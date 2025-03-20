pluginManagement {
    includeBuild("../plugin")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven {
            name = "localPluginRepository"
            url = uri("repo")
        }
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "sample"
include(":app")
 