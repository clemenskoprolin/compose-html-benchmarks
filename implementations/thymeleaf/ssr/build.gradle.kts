plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":workloads:jvm"))
    implementation("org.thymeleaf:thymeleaf:3.1.3.RELEASE")
}
