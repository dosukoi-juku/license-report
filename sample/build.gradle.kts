// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

buildscript {
    repositories {
        google()
        mavenCentral()
    }

    dependencies {
        classpath("com.google.android.gms:oss-licenses-plugin:0.10.6")
//        classpath("io.github.dosukoi-juku:license-report-gradle-plugin")
        classpath("app.cash.licensee:licensee-gradle-plugin:1.13.0")
    }
}