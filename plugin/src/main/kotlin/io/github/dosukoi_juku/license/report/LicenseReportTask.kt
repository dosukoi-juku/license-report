package io.github.dosukoi_juku.license.report

import com.android.build.api.variant.Variant
import java.io.File
import kotlin.collections.associate
import kotlin.collections.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import org.apache.maven.model.Model
import org.apache.maven.model.Parent
import org.apache.maven.model.Repository
import org.apache.maven.model.Scm
import org.apache.maven.model.building.DefaultModelBuilderFactory
import org.apache.maven.model.building.DefaultModelBuildingRequest
import org.apache.maven.model.building.FileModelSource
import org.apache.maven.model.building.ModelBuildingRequest
import org.apache.maven.model.building.ModelSource
import org.apache.maven.model.building.ModelSource2
import org.apache.maven.model.resolution.ModelResolver
import org.apache.tools.ant.taskdefs.optional.depend.Depend
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.artifacts.result.ResolvedVariantResult
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

private val json = Json { prettyPrint = true }

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
        println(outputDir.get().asFile.absolutePath)
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

            val builder = DefaultModelBuilderFactory().newInstance()
            val resolver = object : ModelResolver {
                fun resolve(dependency: Dependency): FileModelSource {
                    val pomFile =
                        fetchPomFile(
                            listOf(dependency),
                            root,
                        ).single().second
                    return FileModelSource(pomFile.file)
                }

                override fun resolveModel(groupId: String, artifactId: String, version: String): ModelSource2 =
                    resolve(Dependency(groupId, artifactId, version))

                override fun resolveModel(parent: Parent): ModelSource2 =
                    resolve(Dependency(parent.groupId, parent.artifactId, parent.version))

                override fun resolveModel(dependency: org.apache.maven.model.Dependency): ModelSource2? =
                    resolve(Dependency(dependency.groupId, dependency.artifactId, dependency.version))

                override fun addRepository(repository: Repository) {}
                override fun addRepository(repository: Repository, replace: Boolean) {}
                override fun newCopy(): ModelResolver = this
            }

            val dependenciesWithPomInfo = dependenciesWithPomFile.map { (dependency, pomFile) ->
                val req = DefaultModelBuildingRequest().also {
                    it.isProcessPlugins = false
                    it.pomFile = pomFile.file
                    it.isTwoPhaseBuilding = true
                    it.modelResolver = resolver
                    it.validationLevel = ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL
                }
                val result = builder.build(req)
                DependencyWithPomInfo(
                    group = dependency.group,
                    name = dependency.name,
                    version = dependency.version,
                    loadPomInfo(result.effectiveModel) { modelId ->
                        result.getRawModel(modelId)
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

        pomDetachedConfiguration(pomDependencies).forEach {
            println(it)
        }

        val withVariants = applyVariantAttributes(
            pomDetachedConfiguration(pomDependencies),
            root.variants
        ).artifacts()

        val withoutVariants = pomDetachedConfiguration(pomDependencies).artifacts()
        withoutVariants.forEach {
            println(it)
        }

        println(withVariants)
        println(withoutVariants)

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

    internal fun loadPomInfo(
        pom: Model,
        getRawModel: (String) -> Model?,
    ): PomInfo {
        val parentRawModel = pom.parent?.let {
            getRawModel("${it.groupId}:${it.artifactId}:${it.version}")
        }
        val pomScm: Scm? = pom.scm
        val url = if (parentRawModel != null) {
            // https://maven.apache.org/ref/3.6.1/maven-model-builder/
            // When depending on a parent, Maven adds a /artifactId to the url of the child,
            // if the pom file does not opt out of this behavior:
            // <scm child.scm.url.inherit.append.path="false">
            // We don't want to handle the /artifactId, so we use the parent clean url instead.
            val parentScm: Scm? = parentRawModel.scm
            when (parentScm?.childScmUrlInheritAppendPath?.toBoolean()) {
                // No opt-out, use pom first, then parent pom because we don't want to use the /artifactId.
                null -> pomScm?.url?.removeSuffix("/${pom.artifactId}") ?: parentScm?.url
                // Explicit opt-out, so use the parent url.
                false -> parentScm.url
                // Explicit opt-in, the model already appends the path.
                true -> pomScm?.url
            }
        } else {
            pomScm?.url
        }

        return PomInfo(
            name = pom.name ?: parentRawModel?.name,
            licenses = (pom.licenses.takeUnless { it.isEmpty() } ?: parentRawModel?.licenses)?.mapTo(mutableSetOf()) {
                PomLicense(it.name, it.url)
            } ?: emptySet(),
            scm = PomScm(url),
        )
    }

}

data class PomFile(
    val file: File
)

@Serializable
data class PomInfo(
    val name: String?,
    val licenses: Set<PomLicense>,
    val scm: PomScm
)

@Serializable
data class PomLicense(
    val name: String?,
    val url: String?
)

@Serializable
data class PomScm(
    val url: String?
)

@Serializable
data class DependencyWithPomInfo(
    override val group: String,
    override val name: String,
    override val version: String,
    @SerialName("license")
    val pomInfo: PomInfo
): DependencyInfo