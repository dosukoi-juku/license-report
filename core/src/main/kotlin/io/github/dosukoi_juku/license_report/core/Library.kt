package io.github.dosukoi_juku.license_report.core

class Library(
    val libraryName: String,
    val dependency: Dependency,
    val licenses: List<License>,
)