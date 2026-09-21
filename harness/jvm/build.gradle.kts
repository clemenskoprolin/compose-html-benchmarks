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

tasks.register<JavaExec>("exportFixtures") {
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("benchmarks.analysis.ExportFixturesKt")
}

tasks.register<JavaExec>("runServer") {
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("benchmarks.server.BenchmarkServerKt")
}

tasks.register<JavaExec>("exportSsrHtml") {
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("benchmarks.analysis.ExportSsrHtmlKt")
    jvmArgs("-Xss16m")
}

tasks.register<JavaExec>("reportSsrColdWarm") {
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("benchmarks.analysis.SsrColdWarmReportKt")
    jvmArgs("-Xss16m")
}

tasks.register<JavaExec>("profileSsrColdWarm") {
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("benchmarks.analysis.SsrColdWarmReportKt")
    jvmArgs("-Xss16m", "-Dbenchmarks.profile.directory=${rootProject.file("results/profiles").absolutePath}")
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
}
