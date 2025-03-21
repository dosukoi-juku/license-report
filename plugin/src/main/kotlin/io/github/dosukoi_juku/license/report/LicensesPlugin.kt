package io.github.dosukoi_juku.license.report

import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.Variant
import org.slf4j.LoggerFactory
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider

/**
 * A simple 'hello world' plugin.
 */


class LicensesPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val androidComponents = project.extensions.getByType(AndroidComponentsExtension::class.java)
        androidComponents.onVariants { variant: Variant ->
            val licenseReportTaskProvider = project.tasks.createLicenseReportTask(variant) {
                it.variantSet(variant)
            }

            variant.sources.assets?.addGeneratedSourceDirectory(licenseReportTaskProvider) {
                it.outputDir
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(LicensesPlugin::class.java)

    }
}

fun TaskContainer.createLicenseReportTask(
    variant: Variant,
    configuration: (LicenseReportTask) -> Unit,
): TaskProvider<LicenseReportTask> {
    val taskName = variant.name + "LicenseReport"
    val licenseReportTask = if (taskName in names) {
        named(taskName, LicenseReportTask::class.java, configuration)
    } else {
        register(taskName, LicenseReportTask::class.java, configuration)
    }
    return licenseReportTask
}