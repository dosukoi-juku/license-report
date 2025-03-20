plugins {
    id("com.android.application") version "8.0.2"
    id("org.jetbrains.kotlin.android") version "2.1.10"
//    id("io.github.dosukoi-juku.licenses-plugin") version "0.0.1-SNAPSHOT"
}

android {
    namespace = "io.github.dosukoi_juku.license_report.sample"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.github.dosukoi_juku.license_report.sample"
        minSdk = 24
        targetSdk = 34
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

    implementation(libs.appcompat.v7)
    testImplementation(libs.junit)
    androidTestImplementation(libs.runner)
    androidTestImplementation(libs.espresso.core)
}