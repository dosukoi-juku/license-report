package io.github.dosukoi_juku.license.report

import kotlinx.serialization.Serializable

@Serializable
data class Dependency(
    val group: String,
    val name: String,
    val version: String,
) {
    companion object {
        fun create(transitiveDependency: String): Dependency {
            val parts = transitiveDependency.split(":")
            return Dependency(parts[0], parts[1], parts[2])
        }
    }
}