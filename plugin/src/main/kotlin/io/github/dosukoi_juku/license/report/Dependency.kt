package io.github.dosukoi_juku.license.report

import kotlinx.serialization.Serializable

interface DependencyInfo {
    val group: String
    val name: String
    val version: String
}

@Serializable
data class Dependency(
    override val group: String,
    override val name: String,
    override val version: String,
): DependencyInfo {

    override fun toString(): String {
        return "$group:$name:$version"
    }

    companion object {
        fun create(transitiveDependency: String): Dependency {
            val parts = transitiveDependency.split(":")
            return Dependency(parts[0], parts[1], parts[2])
        }
    }
}