plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("io.github.dosukoi-juku.license-report")
    id("com.google.android.gms.oss-licenses-plugin")
}

android {
    namespace = "io.github.dosukoi_juku.sample"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.dosukoi_juku.sample"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "android.support.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(libs.androidx.core)
    implementation(libs.okhttp)
    implementation(libs.retrofit)
    implementation(libs.play.service.auth)
}