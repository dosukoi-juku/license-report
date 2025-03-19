package io.github.dosukoi_juku.licenses

import io.github.dosukoi_juku.license.report.LicensesCleanUpTask
import java.io.File
import kotlin.test.assertFalse
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class LicensesCleanUpTaskTest {

    @TempDir
    lateinit var tempFolder: File

    @Test
    fun testAction() {
        val dependencyDir = File(tempFolder, "dependency")
        dependencyDir.mkdirs()

        val dependencyFile = File(tempFolder, "dependency.json")

        val licensesDir = File(tempFolder, "raw")
        licensesDir.mkdirs()

        val licensesFile = File(tempFolder, "third_party_licenses")
        val licensesMetadataFile = File(tempFolder, "third_party_licenses_metadata")

        val project = ProjectBuilder.builder().withProjectDir(tempFolder).build()
        val task = project.tasks.create("licensesCleanUpTask", LicensesCleanUpTask::class.java)
        task.dependencyDir = dependencyDir
        task.dependenciesJson = dependencyFile
        task.licensesDir = licensesDir
        task.licensesFile = licensesFile
        task.licensesMetadataFile = licensesMetadataFile

        task.action()

        assertFalse {  dependencyFile.exists() }
        assertFalse {  dependencyDir.exists() }
        assertFalse {  licensesFile.exists() }
        assertFalse {  licensesMetadataFile.exists() }
        assertFalse {  licensesDir.exists() }
    }
}