@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
plugins { kotlin("multiplatform") }
kotlin {
    jvm()
    js(IR) { browser() }
    wasmJs { browser() }
    jvmToolchain(21)
    sourceSets.commonMain.dependencies {
        implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    }
}
