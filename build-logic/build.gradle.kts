plugins {
    `kotlin-dsl`
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.20")
    implementation("org.jetbrains.kotlin:compose-compiler-gradle-plugin:2.4.20")
    implementation("org.jetbrains.compose:compose-gradle-plugin:${providers.gradleProperty("compose.version").get()}")
}
