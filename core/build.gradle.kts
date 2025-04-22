plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.dosukoi_juku.license_report.core"

    compileSdk = 35

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    sourceSets {
        getByName("test").assets.srcDir(files("src/test/assets"))
    }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutine.core)
    implementation(libs.okhttp.okio)

    testImplementation(libs.kotlinx.coroutine.test)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}