package io.github.dosukoi_juku.license.report

import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.AndroidComponentsExtension
import java.io.File
import org.slf4j.LoggerFactory
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * A simple 'hello world' plugin.
 */
class LicensesPlugin: Plugin<Project> {
    override fun apply(project: Project) {
        val variantToLicenseTaskMap = mutableMapOf<String, LicensesTask>()
        project.extensions.configure(AndroidComponentsExtension::class.java) { androidComponents ->
            androidComponents.onVariants(androidComponents.selector().all()) { variant ->
                println("------------------------------------------------------------------------")
                variant.runtimeConfiguration.incoming.resolutionResult.rootComponent
                val baseDir = File(project.layout.buildDirectory.asFile.get(), "generated/third_party_licenses_2/${variant.name}")
                val dependenciesJson = File(baseDir, "dependencies_2.json")

                val dependencyTask = project.tasks.register<DependencyTask>(
                    "${variant.name}DependencyTask",
                    DependencyTask::class.java
                ) {
                    it.dependenciesJson.set(dependenciesJson)
                    it.libraryDependenciesReport.set(
                        variant.artifacts.get(SingleArtifact.METADATA_LIBRARY_DEPENDENCIES_REPORT)
                    )
                }.get()
                logger.debug("Created task ${dependencyTask.name}")

                val resourceBaseDir = File(baseDir, "/res")
                val rawResourceDir = File(resourceBaseDir, "/raw")
                val licensesFile = File(rawResourceDir, "third_party_licenses")
                val licensesMetadataFile = File(
                    rawResourceDir,
                    "third_party_license_metadata+2"
                )

                val licensesTask = project.tasks.register<LicensesTask>(
                    "${variant.name}LicensesTask",
                    LicensesTask::class.java
                ) {
                    it.dependenciesJson.set(dependenciesJson)
                    it.rawResourceDir.set(rawResourceDir)
                    it.licenses.set(licensesFile)
                    it.licensesMetadata.set(licensesMetadataFile)
                }.get()
                logger.debug("Created task ${licensesTask.name}")

                variantToLicenseTaskMap[licensesTask.name] = licensesTask

                val cleanUpTask = project.tasks.register<LicensesCleanUpTask>(
                    "${variant.name}LicensesCleanUpTask",
                    LicensesCleanUpTask::class.java
                ) {
                    it.dependencyDir = baseDir
                    it.dependenciesJson = dependenciesJson
                    it.licensesDir = rawResourceDir
                    it.licensesFile = licensesFile
                    it.licensesMetadataFile = licensesMetadataFile
                }.get()
                logger.debug("Created task ${cleanUpTask.name}")

                project.tasks.findByName("clean")?.dependsOn(cleanUpTask)
            }
        }
    }
    companion object {
        private val logger = LoggerFactory.getLogger(LicensesPlugin::class.java)

    }
}