@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

plugins {
    kotlin("multiplatform")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
}

val wasmSsrWorkloadSources by tasks.registering(Sync::class) {
    from("../ssr/src") { exclude("ComposeHtmlStringRenderer.kt") }
    into(layout.buildDirectory.dir("generated/wasmSsrWorkloads"))
}

kotlin {
    wasmJs {
        nodejs()
        binaries.executable()
    }
    sourceSets {
        val wasmJsMain by getting {
            // Run the same Compose SSR workload implementations as the JVM benchmark.
            kotlin.srcDir(wasmSsrWorkloadSources)
            dependencies {
                implementation(project(":implementations:compose:shared"))
                implementation(project(":workloads:model"))
                implementation("org.jetbrains.compose.runtime:runtime:${providers.gradleProperty("compose.version").get()}")
                implementation("org.jetbrains.compose.html.eap:html-core-eap:${providers.gradleProperty("compose.html.eap.version").get()}")
                implementation("org.jetbrains.compose.html.eap:html-svg-eap:${providers.gradleProperty("compose.html.eap.version").get()}")
                implementation("org.jetbrains.compose.html:kotlinx-browser-common-subset:${providers.gradleProperty("compose.html.eap.kotlinx-browser-common-subset.version").get()}")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
            }
        }
    }
}
