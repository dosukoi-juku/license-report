package com.dosukoi.license.plugin

import java.io.File
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.maven.MavenModule
import org.gradle.maven.MavenPomArtifact

object DependencyUtil {
    val ABSENT_ARTIFACT = Artifact("absent", "absent", "absent")

    fun resolvePomFileArtifact(project: Project, artifact: Artifact): File? {
        val moduleComponentIdentifier = createModuleComponentIdentifier(artifact)
        println("Resolving POM file for $moduleComponentIdentifier licenses.")

        val components = project.dependencies
            .createArtifactResolutionQuery()
            .forComponents(moduleComponentIdentifier)
            .withArtifacts(MavenModule::class.java, MavenPomArtifact::class.java)
            .execute()

        if (components.resolvedComponents.isEmpty()) {
            println("$moduleComponentIdentifier has no POM file.")
            return null
        }

        val artifacts = components.resolvedComponents.first().getArtifacts(MavenPomArtifact::class.java)
        if (artifacts.isEmpty()) {
            println("$moduleComponentIdentifier empty POM artifact list.")
            return null
        }

        if (artifacts.first() !is ResolvedArtifactResult) {
            println("$moduleComponentIdentifier unexpected type ${artifacts.first().javaClass.simpleName}.")
            return null
        }

        return (artifacts.first() as ResolvedArtifactResult).file
    }

    fun createModuleComponentIdentifier(artifact: Artifact): ModuleComponentIdentifier {
        return DefaultModuleComponentIdentifier(
            DefaultModuleIdentifier.newId(
                artifact.group,
                artifact.name
            ),
            artifact.version
        )
    }
}