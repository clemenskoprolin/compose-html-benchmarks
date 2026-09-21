pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://packages.jetbrains.team/maven/p/cmp/dev")
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://packages.jetbrains.team/maven/p/cmp/dev")
        google()
    }
}

rootProject.name = "benchmark-build-logic"
