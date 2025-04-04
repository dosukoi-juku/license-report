package io.github.dosukoi_juku.license.report

import com.android.build.api.variant.Variant
import java.io.File
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

            val dependenciesWithPomFile = fetchPomFile(dependencies, root)
            val dependenciesWithPomInfo = dependenciesWithPomFile.map { dependencyWithPom ->
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
                            name = it.name,
                            url = it.url
                        )
                    }
                )
            }

            val dependenciesWithPomInfoJson = json.encodeToString(dependenciesWithPomInfo)
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
}

data class PomFile(
    val file: File
)

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
    val dependency: ReportDependencyOutputModel,
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
    val name: String?, // License Name. e.g. "Apache-2.0"
    val url: String? // License URL. e.g. "https://opensource.org/licenses/Apache-2.0"
)