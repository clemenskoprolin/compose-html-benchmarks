plugins {
    java
    id("gg.jte.gradle")
}

java { toolchain { languageVersion.set(JavaLanguageVersion.of(21)) } }

jte {
    sourceDirectory.set(layout.projectDirectory.dir("src/main/jte").asFile.toPath())
    contentType.set(gg.jte.ContentType.Html)
    trimControlStructures.set(true)
    generate()
}

dependencies {
    implementation(project(":workloads:jvm"))
    implementation("gg.jte:jte-runtime:${providers.gradleProperty("jte.version").get()}")
}
