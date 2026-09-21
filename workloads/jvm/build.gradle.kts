plugins {
    kotlin("jvm")
    `java-library`
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    api(project(":workloads:model"))
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.2")
}
