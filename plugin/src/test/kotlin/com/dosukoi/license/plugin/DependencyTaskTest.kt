package com.dosukoi.license.plugin

import com.android.tools.build.libraries.metadata.AppDependencies
import com.android.tools.build.libraries.metadata.Library
import com.android.tools.build.libraries.metadata.MavenLibrary
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class DependencyTaskTest {

    @TempDir
    lateinit var tempFolder: File

    lateinit var project: Project

    lateinit var dependencyTask: DependencyTask

    @BeforeEach
    fun setup() {
        project = ProjectBuilder.builder().build()
        dependencyTask = project.tasks.create("dependencyTask", DependencyTask::class.java)
    }

    @Test
    fun testAction_valuesConvertedToJson() {
        val outputJson = File(tempFolder, "output.json")
        dependencyTask.dependenciesJson.set(outputJson)
        val expectedArtifacts = setOf(
            Artifact(group = "org.group.id", name = "artifactId", version = "1.0.0"),
            Artifact(group = "org.group.id", name = "artifactId2", version = "2.0.0")
        )
        val appDependencies = createAppDependencies(expectedArtifacts)
        val protoFile = writeAppDependencies(appDependencies, File(tempFolder, "dependencies.pb"))
        dependencyTask.libraryDependenciesReport.set(protoFile)

        dependencyTask.action()

        val actualArtifacts = Json.decodeFromString<Set<Artifact>>(outputJson.readText())
        assertEquals(expectedArtifacts, actualArtifacts)
    }

    @Test
    fun testAction_withNonMavenDeps_nonMavenDepsIgnored() {
        val outputJson = File(tempFolder, "output.json")
        dependencyTask.dependenciesJson.set(outputJson)
        val expectedArtifacts = setOf(
            Artifact(group = "org.group.id", name = "artifactId", version = "1.0.0"),
            Artifact(group = "org.group.id", name = "artifactId2", version = "2.0.0"),
            Artifact(group = "org.group.id", name = "artifactId3", version = "3.0.0")
        )
        val appDependencies = createAppDependencies(expectedArtifacts).toBuilder()
            .addLibrary(Library.getDefaultInstance())
            .build()

        val protoFile = writeAppDependencies(appDependencies, File(tempFolder, "dependencies.pb"))
        dependencyTask.libraryDependenciesReport.set(protoFile)

        dependencyTask.action()

        val actualArtifacts = Json.decodeFromString<Set<Artifact>>(outputJson.readText())
        assertEquals(expectedArtifacts, actualArtifacts)
    }

    @Test
    fun testAction_depFileAbsent_writesAbsentDep() {
        val outputJson = File(tempFolder, "output.json")
        dependencyTask.dependenciesJson.set(outputJson)

        dependencyTask.action()

        val actualArtifacts = Json.decodeFromString<Set<Artifact>>(outputJson.readText())
        assertEquals(setOf(DependencyUtil.ABSENT_ARTIFACT), actualArtifacts)
    }


    companion object {
        private fun createAppDependencies(artifactSet: Set<Artifact>): AppDependencies {
            val appDependenciesBuilder = AppDependencies.newBuilder()
            artifactSet.forEach {
                appDependenciesBuilder.addLibrary(
                    Library.newBuilder()
                        .setMavenLibrary(
                            MavenLibrary.newBuilder()
                                .setGroupId(it.group)
                                .setArtifactId(it.name)
                                .setVersion(it.version)
                        )

                )
            }
            return appDependenciesBuilder.build()
        }

        @Throws(IOException::class)
        private fun writeAppDependencies(appDependencies: AppDependencies, protoFile: File): File {
            val outputStream = FileOutputStream(protoFile)
            appDependencies.writeTo(outputStream)
            return protoFile
        }
    }
}