package io.github.dosukoi_juku.license.report

import java.io.File
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.internal.component.AmbiguousVariantSelectionException
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.maven.MavenModule
import org.gradle.maven.MavenPomArtifact
import org.slf4j.LoggerFactory

object DependencyUtil {
    val ABSENT_Dependency = Dependency("absent", "absent", "absent")
    private const val TEST_PREFIX = "test"
    private const val ANDROID_TEST_PREFIX = "androidTest"
    private const val LOCAL_LIBRARY_VERSION = "unspecified"
    private val TEST_COMPILE = setOf("testCompile", "androidTestCompile")
    private val PACKAGED_DEPENDENCIES_PREFIXES = setOf("compile", "implementation", "api")

    private val logger = LoggerFactory.getLogger(DependencyUtil::class.java)

    fun resolvePomFileArtifact(project: Project, dependency: Dependency): File? {
        val moduleComponentIdentifier = createModuleComponentIdentifier(dependency)

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

    fun createModuleComponentIdentifier(dependency: Dependency): ModuleComponentIdentifier {
        return DefaultModuleComponentIdentifier(
            DefaultModuleIdentifier.newId(
                dependency.group,
                dependency.name
            ),
            dependency.version
        )
    }

    fun getLibraryFile(project: Project, dependencyInfo: Dependency): File? {
        for (configuration in project.configurations) {
            println("${configuration.name} ${shouldSkipConfiguration(configuration)} ${isNotResolved(configuration)} ${configuration.state}")
            if (shouldSkipConfiguration(configuration)) {
                continue
            }
            if (isNotResolved(configuration)) {
                project.logger.info("Configuration ${configuration.name} is not resolved, skipping.")
                continue
            }
            val resolvedDependencies = configuration.resolvedConfiguration.lenientConfiguration.allModuleDependencies
            return findLibraryFileInResolvedDependencies(resolvedDependencies, dependencyInfo, emptySet())
        }
        logger.warn("No resolved configurations contained $dependencyInfo")
        return null
    }

    fun findLibraryFileInResolvedDependencies(
        resolvedDependencies: Set<ResolvedDependency>,
        dependency: Dependency,
        visitedDependencies: Set<ResolvedDependency>
    ): File? {
        for (resolvedDependency in resolvedDependencies) {
            try {
                if (resolvedDependency.moduleVersion == LOCAL_LIBRARY_VERSION) {
                    if (resolvedDependency in visitedDependencies) {
                        logger.warn("Cyclic dependency detected for ${resolvedDependency.name}")
                        continue
                    }
                    val childResult = findLibraryFileInResolvedDependencies(
                        resolvedDependency.children,
                        dependency,
                        visitedDependencies + resolvedDependency
                    )
                    if (childResult != null) {
                        return childResult
                    }
                } else {
                    for (resolvedArtifact in resolvedDependency.allModuleArtifacts) {
                        if (isMatchingArtifact(resolvedArtifact, dependency)) {
                            return resolvedArtifact.file
                        }
                    }
                }
            } catch (e: AmbiguousVariantSelectionException) {
                logger.info("Failed to process ${resolvedDependency.name}", e)
            }
        }
        return  null
    }

    fun shouldSkipConfiguration(configuration: Configuration): Boolean {
        return (!canBeResolved(configuration) || isTest(configuration) || !isPackagedDependency(configuration))
    }

    fun canBeResolved(configuration: Configuration): Boolean {
        return configuration.isCanBeResolved
    }

    fun isTest(configuration: Configuration): Boolean {
        var isTestConfiguration =
            configuration.name.startsWith(TEST_PREFIX) || configuration.name.startsWith(ANDROID_TEST_PREFIX)
        configuration.hierarchy.forEach {
            isTestConfiguration = isTestConfiguration or TEST_COMPILE.contains(it.name)
        }
        return isTestConfiguration
    }

    fun isPackagedDependency(configuration: Configuration): Boolean {
        var isPackagedDependency = PACKAGED_DEPENDENCIES_PREFIXES.any {
            configuration.name.startsWith(it)
        }
        configuration.hierarchy.forEach {
            val configurationHierarchyName = it.name
            isPackagedDependency = isPackagedDependency or PACKAGED_DEPENDENCIES_PREFIXES.any {
                configurationHierarchyName.startsWith(it)
            }
        }
        return isPackagedDependency
    }

    fun isNotResolved(configuration: Configuration): Boolean {
        println("${configuration.name} ${configuration.state}")
        return configuration.state != Configuration.State.RESOLVED
    }

    fun isMatchingArtifact(resolvedArtifact: ResolvedArtifact, dependency: Dependency): Boolean {
        return (resolvedArtifact.moduleVersion.id.group == dependency.group &&
                resolvedArtifact.name == dependency.name &&
                resolvedArtifact.moduleVersion.id.version == dependency.version)
    }
}