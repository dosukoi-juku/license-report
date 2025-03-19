package io.github.dosukoi_juku.license.report

import kotlinx.serialization.Serializable

@Serializable
data class Dependency(
    val group: String,
    val name: String,
    val version: String,
)