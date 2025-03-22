plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "io.github.dosukoi_juku.license_report.core"

    compileSdk = 35

    defaultConfig {
        minSdk = 24
    }
}