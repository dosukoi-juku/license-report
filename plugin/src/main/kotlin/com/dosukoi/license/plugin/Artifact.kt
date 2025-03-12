package com.dosukoi.license.plugin

import kotlinx.serialization.Serializable

@Serializable
data class Artifact(
    val group: String,
    val name: String,
    val version: String,
)