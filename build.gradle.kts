plugins {
    base
    kotlin("jvm") apply false
    kotlin("multiplatform") apply false
    kotlin("plugin.compose") apply false
    id("org.jetbrains.compose") apply false
}

group = "benchmarks"
version = "1.0.0"

val jvmHarnessTasks = listOf(
    "run",
    "runServer",
    "exportFixtures",
    "exportSsrHtml",
    "reportSsrColdWarm",
    "profileSsrColdWarm",
    "test",
)

jvmHarnessTasks.forEach { taskName ->
    tasks.register(taskName) {
        group = "benchmark"
        dependsOn(":harness:jvm:$taskName")
    }
}
