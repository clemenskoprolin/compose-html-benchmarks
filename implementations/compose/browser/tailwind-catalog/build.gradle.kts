plugins { id("compose-browser-workload") }

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":implementations:compose:shared"))
            implementation(project(":workloads:model"))
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
        }
    }
}
