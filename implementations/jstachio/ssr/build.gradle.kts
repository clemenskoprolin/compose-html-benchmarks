plugins { java }

java { toolchain { languageVersion.set(JavaLanguageVersion.of(21)) } }

val jstachioVersion = providers.gradleProperty("jstachio.version").get()
dependencies {
    implementation(project(":workloads:jvm"))
    implementation("io.jstach:jstachio:$jstachioVersion")
    annotationProcessor("io.jstach:jstachio-apt:$jstachioVersion")
}

// JStachio reads classpath templates while javac runs its annotation processor.
tasks.compileJava {
    options.compilerArgs.add("-Ajstache.resourcesPath=${layout.projectDirectory.dir("src/main/resources").asFile}")
    inputs.dir("src/main/resources")
}
