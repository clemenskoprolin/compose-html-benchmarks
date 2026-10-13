plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":workloads:jvm"))
    implementation("org.thymeleaf:thymeleaf:${providers.gradleProperty("thymeleaf.version").get()}")
}
