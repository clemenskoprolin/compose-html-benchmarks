// Defines JVM harness entry points for fixture export, SSR export, reports, profiling, and preview serving.
plugins {
    kotlin("jvm")
    application
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("benchmarks.analysis.AttributeProfileAnalyzerKt")
}

tasks.withType<JavaExec>().configureEach {
    workingDir(rootProject.projectDir)
}

tasks.withType<Test>().configureEach {
    workingDir(rootProject.projectDir)
}

mapOf(
    "exportFixtures" to "benchmarks.analysis.ExportFixturesKt",
    "runServer" to "benchmarks.server.BenchmarkServerKt",
    "exportSsrHtml" to "benchmarks.analysis.ExportSsrHtmlKt",
    "reportSsrColdWarm" to "benchmarks.analysis.SsrColdWarmReportKt",
    "reportSsrStreaming" to "benchmarks.analysis.StreamingSsrReportKt",
    "reportStreamingThroughput" to "benchmarks.analysis.StreamingThroughputKt",
    "profileSsrColdWarm" to "benchmarks.analysis.SsrColdWarmReportKt",
).forEach { (name, entryPoint) ->
    tasks.register<JavaExec>(name) {
        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set(entryPoint)
        if (name != "exportFixtures" && name != "runServer") jvmArgs("-Xss16m")
        if (name == "profileSsrColdWarm") {
            jvmArgs("-Dbenchmarks.profile.directory=${rootProject.file("results/profiles").absolutePath}")
        }
    }
}

dependencies {
    implementation(project(":workloads:jvm"))
    implementation(project(":implementations:compose:ssr"))
    implementation(project(":implementations:thymeleaf:ssr"))
    implementation(project(":implementations:compose:browser:hydrate1k"))
    implementation(project(":implementations:compose:browser:update10th1k"))
    implementation(project(":implementations:compose:browser:reorder1k"))
    implementation(project(":implementations:compose:browser:filter-list"))
    implementation("org.jsoup:jsoup:1.18.1")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.2")
    implementation("org.slf4j:slf4j-nop:2.0.16")
    testImplementation(kotlin("test"))
}
