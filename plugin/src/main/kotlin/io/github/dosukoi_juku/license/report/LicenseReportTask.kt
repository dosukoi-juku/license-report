package io.github.dosukoi_juku.license.report

import com.android.build.api.variant.Variant
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import nl.adaptivity.xmlutil.serialization.XML
import nl.adaptivity.xmlutil.serialization.XmlChildrenName
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.artifacts.result.ResolvedVariantResult
import org.gradle.api.attributes.Attribute
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

private val json = Json { prettyPrint = true }
private val xml = XML {
    defaultPolicy {
        ignoreUnknownChildren()
    }
}

abstract class LicenseReportTask : DefaultTask() {

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Internal
    var variant: Variant? = null

    fun variantSet(variant: Variant) {
        this.variant = variant
    }

    @TaskAction
    fun action() {
        outputDir.get().asFile.mkdir()

        variant?.runtimeConfiguration?.also {
            val root = it.incoming.resolutionResult.rootComponent.get()

            val transitiveDependencies = loadTransitiveDependencies(it, root, setOf())
            val dependencies = transitiveDependencies.map {
                Dependency.create(it.displayName)
            }
            val dependenciesJson = json.encodeToString(dependencies)
            val dependenciesJsonFile = File(outputDir.get().asFile, "dependencies.json")
            dependenciesJsonFile.writeText(dependenciesJson)

            // TODO: ここでFirebaseやGoogle Play Servicesのpomを取得するために、AARを解凍し、推移的に依存しているpomを取得する必要がある。unzipするとthird_party_licenses.txtとthird_party_licenses.jsonが取得できるので、これを使う。
            // TODO: third_party_licenses.jsonはkeyがライセンス名、valueとしてlengthとstartがあり、これはthird_party_licenses.txtのindexを指している。これを使ってライセンス名とライセンス文を取得する必要がある。
            // TODO: FirebaseのgroupIdはcom.google.firebase、Google Play ServicesのgroupIdはcom.google.android.gmsなので、これを使って判別する。
            val (firebaseOrGooglePlayServiceDependencies, otherDependencies) = dependencies.partition {
                isFirebase(it.group) || isGooglePlayServices(it.group)
            }
            val dependenciesWithPomFile = fetchPomFile(otherDependencies, root)
            val reportLicenseOutputModels = dependenciesWithPomFile.map { dependencyWithPom ->
                val fileText = dependencyWithPom.second.file.readText(Charsets.UTF_8)
                val serializer = serializer<PomInputModel>()
                val pom = xml.decodeFromString(serializer, fileText)
                ReportOutputModel(
                    name = pom.name,
                    dependency = ReportDependencyOutputModel(
                        group = dependencyWithPom.first.group,
                        name = dependencyWithPom.first.name,
                        version = dependencyWithPom.first.version
                    ),
                    licenses = pom.licenses.map {
                        ReportLicenseOutputModel(
                            license = it.name,
                            url = it.url
                        )
                    }
                )
            }

            val firebaseOrGooglePlayServiceDependenciesWithAarFile =
                getTransitiveDependenciesFromFirebaseOrGooglePlayService(
                    firebaseOrGooglePlayServiceDependencies,
                    root
                )

            val thirdPartyLicenseModels = firebaseOrGooglePlayServiceDependenciesWithAarFile.flatMap {
                it.second.extractThirdPartyLicenses()
            }

            // 通常のライセンス情報とサードパーティライセンス情報を結合
            val allLicenseModels = (reportLicenseOutputModels + thirdPartyLicenseModels).sortedBy { it.name }

            val dependenciesWithPomInfoJson = json.encodeToString(allLicenseModels)
            val dependenciesWithPomInfoFile = File(outputDir.get().asFile, "license_report.json")
            dependenciesWithPomInfoFile.writeText(dependenciesWithPomInfoJson)
        }
    }

    private fun Configuration.artifacts() =
        resolvedConfiguration.lenientConfiguration.allModuleDependencies.flatMap { it.allModuleArtifacts }

    fun loadTransitiveDependencies(
        configuration: Configuration,
        root: ResolvedComponentResult,
        seen: Set<ComponentIdentifier> = emptySet(),
    ): Set<ComponentIdentifier> {
        if (seen.contains(root.id)) return seen

        val updatedSeen = when (root.id) {
            is ProjectComponentIdentifier -> seen
            else -> seen + root.id
        }

        return root.dependencies.fold(updatedSeen) { acc, dependency ->
            when (dependency) {
                is ProjectComponentIdentifier -> acc
                is ResolvedDependencyResult ->
                    loadTransitiveDependencies(configuration, dependency.selected, acc)

                else -> acc
            }
        }
    }

    fun applyVariantAttributes(
        configuration: Configuration,
        variants: List<ResolvedVariantResult>
    ): Configuration =
        variants.fold(configuration) { config, variant ->
            config.attributes.also { attrs ->
                variant.attributes.keySet().forEach { key ->
                    @Suppress("UNCHECKED_CAST")
                    attrs.attribute(key as Attribute<Any?>, variant.attributes.getAttribute(key)!!)
                }
            }
            config
        }

    fun fetchPomFile(dependencies: List<Dependency>, root: ResolvedComponentResult): List<Pair<Dependency, PomFile>> {

        val pomDependencies = dependencies.map {
            project.dependencies.create("${it.group}:${it.name}:${it.version}@pom")
        }.toTypedArray()

        val pomDetachedConfiguration: (Array<org.gradle.api.artifacts.Dependency>) -> Configuration = { deps ->
            project.configurations.detachedConfiguration(*deps)
        }

        val withVariants = applyVariantAttributes(
            pomDetachedConfiguration(pomDependencies),
            root.variants
        ).artifacts()

        val withoutVariants = pomDetachedConfiguration(pomDependencies).artifacts()

        return (withVariants + withoutVariants).map {
            val moduleComponentIdentifier = (it.id.componentIdentifier as ModuleComponentIdentifier)
            val dependency = Dependency(
                moduleComponentIdentifier.group,
                moduleComponentIdentifier.module,
                moduleComponentIdentifier.version
            )
            Pair(dependency, PomFile(it.file))
        }.distinctBy { it.first }
    }

    fun isFirebase(groupId: String): Boolean {
        return groupId.startsWith("com.google.firebase")
    }

    fun isGooglePlayServices(groupId: String): Boolean {
        return groupId.startsWith("com.google.android.gms")
    }

    fun getTransitiveDependenciesFromFirebaseOrGooglePlayService(
        dependencies: List<Dependency>,
        root: ResolvedComponentResult,
    ): List<Pair<Dependency, AarFile>> {
//        val pomDependencies = dependencies.map {
//            project.dependencies.create("${it.group}:${it.name}:${it.version}@aar")
//        }.toTypedArray()
//
//        val pomDetachedConfiguration: (Array<org.gradle.api.artifacts.Dependency>) -> Configuration = { deps ->
//            project.configurations.detachedConfiguration(*deps)
//        }
//
//        val withVariants = applyVariantAttributes(
//            pomDetachedConfiguration(pomDependencies),
//            root.variants
//        ).artifacts()
//
//        val withoutVariants = pomDetachedConfiguration(pomDependencies).artifacts()
//
//        return (withVariants + withoutVariants).map {
//            val moduleComponentIdentifier = (it.id.componentIdentifier as ModuleComponentIdentifier)
//            val dependency = Dependency(
//                moduleComponentIdentifier.group,
//                moduleComponentIdentifier.module,
//                moduleComponentIdentifier.version
//            )
//            Pair(dependency, AarFile(it.file))
//        }.distinctBy { it.first }

        // 各依存関係について、@aar 指定で解決を試み、artifacts() 呼び出し時に例外が発生したらフォールバックする関数
        fun resolveArtifactsWithFallback(dep: Dependency): List<ResolvedArtifact> {
            val configAar = project.configurations.detachedConfiguration(
                project.dependencies.create("${dep.group}:${dep.name}:${dep.version}@aar")
            )
            val artifactsAar = try {
                applyVariantAttributes(configAar, root.variants).artifacts() +
                        configAar.artifacts()
            } catch (e: Exception) {
                emptyList<ResolvedArtifact>()
            }
            // 結果の中から、実際に aar ファイルのみを抽出する
            return artifactsAar.filter { it.file.extension.equals("aar", ignoreCase = true) }
        }

        // 各依存関係ごとに解決されたアーティファクトを取得し、Dependency と AarFile のペアに変換する
        return dependencies.flatMap { dep ->
            // 個々の依存解決は artifacts() 呼び出し時に行われ、上記関数内で try/catch している
            try {
                resolveArtifactsWithFallback(dep)
            } catch (e: Exception) {
                // 万一、エラーが発生した場合はその依存関係はスキップ
                emptyList()
            }
        }.map { artifact ->
            // ModuleComponentIdentifier を使って依存関係オブジェクトを再構築
            val moduleComponentIdentifier = artifact.id.componentIdentifier as ModuleComponentIdentifier
            val resolvedDependency = Dependency(
                moduleComponentIdentifier.group,
                moduleComponentIdentifier.module,
                moduleComponentIdentifier.version
            )
            Pair(resolvedDependency, AarFile(artifact.file))
        }.distinctBy { it.first }
    }
}

data class PomFile(
    val file: File
)

data class AarFile(
    val file: File
) {

    @Serializable
    data class ThirdPartyLicense(
        val length: Int,
        val start: Int
    )

    fun extractThirdPartyLicenses(): List<ReportOutputModel> {
        val zipFile = ZipFile(file)
        val thirdPartyLicensesJson: ZipEntry? = zipFile.getEntry("third_party_licenses.json")
        val thirdPartyLicensesTxt: ZipEntry? = zipFile.getEntry("third_party_licenses.txt")

        if (thirdPartyLicensesTxt == null || thirdPartyLicensesJson == null) {
            return emptyList()
        }

        val licensesJson = zipFile.getInputStream(thirdPartyLicensesJson).bufferedReader().use { it.readText() }.run {
            json.decodeFromString<Map<String, ThirdPartyLicense>>(this)
        }

        val licensesTxt = zipFile.getInputStream(thirdPartyLicensesTxt).bufferedReader().use { it.readText() }
        return licensesJson.mapNotNull { (licenseName, thirdPartyLicense) ->
            println("licenseName: $licenseName, thirdPartyLicense: $thirdPartyLicense")
            // ライセンステキストファイルの長さを超えないようにする
            if (thirdPartyLicense.start >= licensesTxt.length) {
                // 開始位置が範囲外の場合はスキップ
                null
            } else {
                // 終了位置が範囲外にならないように調整
                val endPos = minOf(licensesTxt.length, thirdPartyLicense.start + thirdPartyLicense.length)
                val licenseText = licensesTxt.substring(thirdPartyLicense.start, endPos)

                ReportOutputModel(
                    name = licenseName,
                    dependency = null,
                    licenses = listOf(
                        ReportLicenseOutputModel(
                            license = licenseText,
                            url = null // ライセンスURLは third_party_licenses から取得できないため
                        )
                    )
                )
            }
        }
    }
}

@Serializable
data class PomInputModel(
    @XmlElement(true)
    val name: String,
    @XmlChildrenName("license")
    val licenses: List<PomLicenseInputModel>,
)

@Serializable
@XmlSerialName("license", "", "")
@SerialName("license")
data class PomLicenseInputModel(
    @XmlElement(true)
    val name: String?,
    @XmlElement(true)
    val url: String?
)

@Serializable
data class ReportOutputModel(
    val name: String?, // Library Name. e.g. "AndroidX Core"
    val dependency: ReportDependencyOutputModel?,
    val licenses: List<ReportLicenseOutputModel>,
)

@Serializable
data class ReportDependencyOutputModel(
    val group: String, // Group ID. e.g. "androidx.core"
    val name: String, // Artifact ID. e.g. "core"
    val version: String, // Version. e.g. "1.0.0"
)

@Serializable
data class ReportLicenseOutputModel(
    val license: String?, // License Name. e.g. "Apache-2.0"
    val url: String? // License URL. e.g. "https://opensource.org/licenses/Apache-2.0"
)