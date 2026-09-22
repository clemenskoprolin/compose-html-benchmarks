import java.util.Properties

plugins {
    `kotlin-dsl`
}

// Auto-detect Compose version for build logic
val benchmarkRoot = rootDir.parentFile
val localProperties = Properties().apply {
    benchmarkRoot.resolve("local.properties").takeIf { it.isFile }?.inputStream()?.use(::load)
}
val benchmarkProperties = Properties().apply {
    benchmarkRoot.resolve("gradle.properties").inputStream().use(::load)
}
val selectedComposeVersion = providers.gradleProperty("compose.version").orElse(
    providers.provider {
        val configuredCheckout = providers.environmentVariable("COMPOSE_HTML_CHECKOUT").orNull
            ?: localProperties.getProperty("compose.html.checkout")
        configuredCheckout?.let {
            val requestedCheckout = benchmarkRoot.resolve(it)
            val checkout = requestedCheckout.resolve("html")
                .takeIf { candidate -> candidate.resolve("settings.gradle.kts").isFile }
                ?: requestedCheckout
            Properties().apply {
                checkout.resolve("gradle.properties").inputStream().use(::load)
            }.getProperty("compose.version")
                ?: error("compose.version is missing from ${checkout.resolve("gradle.properties")}")
        } ?: benchmarkProperties.getProperty("compose.version")
            ?: error("compose.version is missing from ${benchmarkRoot.resolve("gradle.properties")}")
    },
)

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.20")
    implementation("org.jetbrains.kotlin:compose-compiler-gradle-plugin:2.4.20")
    implementation("org.jetbrains.compose:compose-gradle-plugin:${selectedComposeVersion.get()}")
}
