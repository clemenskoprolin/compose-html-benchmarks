@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

plugins {
    kotlin("multiplatform")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
}

val composeVersion: String = providers.gradleProperty("compose.version").get()
val composeHtmlEapVersion: String = providers.gradleProperty("compose.html.eap.version").get()
val browserCommonSubsetVersion: String =
    providers.gradleProperty("compose.html.eap.kotlinx-browser-common-subset.version").get()
val browserOutputFileName = "${project.name}-compose.js"

kotlin {
    jvm()
    js(IR) {
        browser {
            commonWebpackConfig {
                outputFileName = browserOutputFileName
            }
        }
        binaries.executable()
    }
    wasmJs {
        browser {
            commonWebpackConfig {
                outputFileName = browserOutputFileName
            }
        }
        binaries.executable()
    }
    applyDefaultHierarchyTemplate()
    jvmToolchain(21)

    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.compose.runtime:runtime:$composeVersion")
            implementation("org.jetbrains.compose.html.eap:html-core-eap:$composeHtmlEapVersion")
            implementation("org.jetbrains.compose.html:kotlinx-browser-common-subset:$browserCommonSubsetVersion")
        }
        wasmJsMain {
            languageSettings.optIn("kotlin.js.ExperimentalWasmJsInterop")
        }
    }
}
