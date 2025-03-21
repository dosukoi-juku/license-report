package io.github.dosukoi_juku.license.report

import com.android.tools.build.libraries.metadata.AppDependencies
import com.android.tools.build.libraries.metadata.Library.LibraryOneofCase.MAVEN_LIBRARY
import java.io.File
import kotlinx.serialization.json.Json
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

abstract class DependencyTask : DefaultTask() {

    @get:Input
    abstract val outputDir: RegularFileProperty

    @get:OutputFile
    abstract val dependenciesJson : RegularFileProperty

    @get:Optional
    @get:InputFile
    abstract val libraryDependenciesReport: RegularFileProperty

    @TaskAction
    fun action() {
        val artifactSet = loadArtifact()

        val outputFile = dependenciesJson.asFile.get()

        initOutput(outputFile.parentFile)
        outputFile.writer().buffered().use {
            it.write(Json.encodeToString(artifactSet))
        }
    }

    private fun loadArtifact(): Set<Dependency> {
        if (!libraryDependenciesReport.isPresent) {
            logger.info("$name not provided with AppDependencies proto file")
            return setOf(DependencyUtil.ABSENT_Dependency)
        }

        val appDependencies = loadDependenciesFile()
        return convertDependenciesToArtifact(appDependencies)
    }

    private fun loadDependenciesFile(): AppDependencies {
        val dependenciesFile = libraryDependenciesReport.asFile.get()
        return dependenciesFile.inputStream().buffered().use {
            AppDependencies.parseFrom(it)
        }
    }

    companion object {
        private fun convertDependenciesToArtifact(appDependencies: AppDependencies): Set<Dependency> {
            return appDependencies.libraryList
                .filter { it.libraryOneofCase == MAVEN_LIBRARY }
                .map {
                    Dependency(
                        it.mavenLibrary.groupId,
                        it.mavenLibrary.artifactId,
                        it.mavenLibrary.version,
                    )
                }
                .toSet()
        }

        private fun initOutput(outputDir: File) {
            if (!outputDir.exists())
                outputDir.mkdirs()
        }
    }
}