@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
plugins {
    kotlin("multiplatform")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
}
kotlin {
    jvm()
    js(IR) { browser() }
    wasmJs { browser() }
    jvmToolchain(21)
    sourceSets.commonMain.dependencies {
        api(project(":workloads:model"))
        implementation("org.jetbrains.compose.runtime:runtime:${providers.gradleProperty("compose.version").get()}")
        implementation("org.jetbrains.compose.html.eap:html-core-eap:${providers.gradleProperty("compose.html.eap.version").get()}")
        implementation("org.jetbrains.compose.html.eap:html-svg-eap:${providers.gradleProperty("compose.html.eap.version").get()}")
        implementation("org.jetbrains.compose.html:kotlinx-browser-common-subset:${providers.gradleProperty("compose.html.eap.kotlinx-browser-common-subset.version").get()}")
    }
}
