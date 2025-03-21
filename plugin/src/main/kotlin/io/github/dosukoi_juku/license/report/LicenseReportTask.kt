package io.github.dosukoi_juku.license.report

import com.android.build.api.variant.Variant
import java.io.File
import kotlinx.serialization.json.Json
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

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
            val dependenciesJson = Json.encodeToString(dependencies)
            val dependenciesJsonFile = File(outputDir.get().asFile, "dependencies.json")
            dependenciesJsonFile.writeText(dependenciesJson)
        }
    }

    fun loadTransitiveDependencies(
        configuration: Configuration,
        root: ResolvedComponentResult,
        seen: Set<ComponentIdentifier> = emptySet(),
    ): Set<ComponentIdentifier> {
        if (seen.contains(root.id)) return seen

        val updatedSeen = when(root.id) {
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
}