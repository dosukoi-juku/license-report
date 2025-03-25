package io.github.dosukoi_juku.license_report.core

/**
 * Represents a license.
 * @param identifier The identifier of the license. e.g. `Apache-2.0`
 * @param url The URL of the license. e.g. `https://opensource.org/licenses/Apache-2.0`
 */
class License(
    val identifier: String,
    val url: String?,
)