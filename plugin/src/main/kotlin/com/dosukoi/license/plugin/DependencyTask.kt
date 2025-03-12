package com.dosukoi.license.plugin

import com.android.tools.build.libraries.metadata.AppDependencies
import com.android.tools.build.libraries.metadata.Library.LibraryOneofCase.MAVEN_LIBRARY
import groovy.json.JsonBuilder
import java.io.File
import kotlinx.serialization.json.Json
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

abstract class DependencyTask : DefaultTask() {

    @get:OutputFile
    abstract val dependenciesJson : RegularFileProperty

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

    private fun loadArtifact(): Set<Artifact> {
        if (!libraryDependenciesReport.isPresent) {
            logger.info("$name not provided with AppDependencies proto file")
            return setOf(DependencyUtil.ABSENT_ARTIFACT)
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
        private fun convertDependenciesToArtifact(appDependencies: AppDependencies): Set<Artifact> {
            return appDependencies.libraryList
                .filter { it.libraryOneofCase == MAVEN_LIBRARY }
                .map {
                    Artifact(
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