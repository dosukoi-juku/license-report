package io.github.dosukoi_juku.license.report

import kotlinx.serialization.Serializable

@Serializable
data class Library(
    /**
     * The name of the library. ex. "AndroidX AppCompat"
     */
    val name: String,
    /**
     * The version of the library. ex. "1.3.0"
     */
    val version: String,
    /**
     * The URL of the library. ex. "https://developer.android.com/jetpack/androidx/releases/appcompat"
     */
    val url: String,
    /**
     * The licenses of the library.
     */
    val licenses: List<License>,
)

@Serializable
data class License(
    /**
     * The name of the license. ex. "Apache License 2.0"
     */
    val name: String,
    /**
     * The URL of the license. ex. "https://www.apache.org/licenses/LICENSE-2.0.txt"
     */
    val url: String,
    /**
     * The text of the license.
     */
    val text: String,
)