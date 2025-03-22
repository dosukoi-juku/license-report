pluginManagement {
//    includeBuild("../plugin")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven {
            url = uri("../../repo")
        }
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
        maven { url = uri("https://www.jitpack.io") }
    }
}

rootProject.name = "sample"
include(":app")
 