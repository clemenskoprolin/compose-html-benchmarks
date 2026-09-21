plugins {
    kotlin("jvm")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
}

kotlin {
    jvmToolchain(21)
    sourceSets {
        main {
            kotlin.setSrcDirs(listOf("src"))
        }
    }
}

val composeVersion: String = providers.gradleProperty("compose.version").get()
val composeHtmlEapVersion: String = providers.gradleProperty("compose.html.eap.version").get()
val browserCommonSubsetVersion: String =
    providers.gradleProperty("compose.html.eap.kotlinx-browser-common-subset.version").get()

dependencies {
    implementation(project(":implementations:compose:shared"))
    implementation(project(":workloads:jvm"))
    implementation("org.jetbrains.compose.runtime:runtime:$composeVersion")
    implementation("org.jetbrains.compose.html.eap:html-core-eap:$composeHtmlEapVersion")
    implementation("org.jetbrains.compose.html.eap:html-svg-eap:$composeHtmlEapVersion")
    implementation("org.jetbrains.compose.html:kotlinx-browser-common-subset:$browserCommonSubsetVersion")
}
