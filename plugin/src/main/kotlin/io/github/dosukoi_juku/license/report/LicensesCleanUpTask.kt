package io.github.dosukoi_juku.license.report

import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

abstract class LicensesCleanUpTask : DefaultTask() {
    @get:Internal
    lateinit var dependencyDir: File
    @get:Internal
    lateinit var dependenciesJson: File
    @get:Internal
    lateinit var licensesDir: File
    @get:Internal
    lateinit var licensesFile: File
    @get:Internal
    lateinit var licensesMetadataFile: File

    @TaskAction
    fun action() {
        if (dependenciesJson.exists()) {
            dependenciesJson.delete()
        }

        if (dependencyDir.isDirectory && dependencyDir.list()?.size == 0) {
            dependencyDir.delete()
        }

        if (licensesFile.exists()) {
            licensesFile.delete()
        }

        if (licensesMetadataFile.exists()) {
            licensesMetadataFile.delete()
        }

        if (licensesDir.isDirectory && licensesDir.list()?.size == 0) {
            licensesDir.delete()
        }
    }
}