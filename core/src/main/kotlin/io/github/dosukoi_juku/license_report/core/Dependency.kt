package io.github.dosukoi_juku.license_report.core

/**
 * Represents a dependency.
 * @param group The group of the dependency. e.g. `io.github.dosukoi_juku.license_report`
 * @param name The name of the dependency. e.g. `core`
 * @param version The version of the dependency. e.g. `1.0.0`
 */
class Dependency(
    val group: String,
    val name: String,
    val version: String,
) {
    override fun toString(): String {
        return "$group:$name:$version"
    }
}