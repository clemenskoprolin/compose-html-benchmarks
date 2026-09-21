pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://packages.jetbrains.team/maven/p/cmp/dev")
        google()
    }

    plugins {
        kotlin("jvm").version(extra["kotlin.version"] as String)
        kotlin("multiplatform").version(extra["kotlin.version"] as String)
        kotlin("plugin.compose").version(extra["kotlin.version"] as String)
        id("org.jetbrains.compose").version(extra["compose.version"] as String)
    }
}

val localProperties = java.util.Properties().apply {
    file("local.properties").takeIf { it.isFile }?.inputStream()?.use(::load)
}
val useIncludedComposeBuild = providers.gradleProperty("compose.html.use.included.build")
    .orElse("true").get().toBoolean()

dependencyResolutionManagement {
    repositories {
        if (!useIncludedComposeBuild) {
            maven {
                name = "benchmarkComposeM2"
                url = uri(file("build/compose-m2"))
            }
        }
        mavenCentral()
        maven("https://packages.jetbrains.team/maven/p/cmp/dev")
        google()
    }
}

rootProject.name = "compose-html-benchmarks"

include(":workloads:jvm")
include(":workloads:model")
include(":implementations:compose:shared")
include(":implementations:compose:ssr")
include(":implementations:thymeleaf:ssr")
include(":implementations:compose:browser:hydrate1k")
include(":implementations:compose:browser:update10th1k")
include(":implementations:compose:browser:reorder1k")
include(":implementations:compose:browser:filter-list")
include(":implementations:compose:browser:tailwind-catalog")
include(":implementations:compose:browser:form-app")
include(":implementations:compose:browser:data-table")
include(":implementations:compose:browser:svg-dashboard")
include(":implementations:compose:browser:content-article")
include(":harness:jvm")

// CLI and environment overrides take precedence over the machine-local selection
// written by compose-version.sh. The sibling checkout is the portable default.
val checkoutPath = providers.gradleProperty("compose.html.checkout")
    .orElse(providers.environmentVariable("COMPOSE_HTML_CHECKOUT"))
    .orElse(localProperties.getProperty("compose.html.checkout") ?: "../compose-html-ssr")

// Compose HTML may be supplied either as its build directory or as the
// repository root that contains an `html/` build directory.
val requestedCheckoutDirectory = file(checkoutPath.get())
val checkoutDirectory = requestedCheckoutDirectory.resolve("html")
    .takeIf { it.resolve("settings.gradle.kts").isFile }
    ?: requestedCheckoutDirectory

if (useIncludedComposeBuild) {
    require(checkoutDirectory.resolve("settings.gradle.kts").isFile) {
        "Compose HTML checkout not found at $checkoutDirectory. " +
            "Run ./tools/compose-version.sh <compose-checkout> to select and prepare one."
    }
    includeBuild(checkoutDirectory) {
        dependencySubstitution {
            substitute(module("org.jetbrains.compose.html.eap:html-core-eap"))
                .using(project(":html-core-eap"))
            substitute(module("org.jetbrains.compose.html.eap:html-svg-eap"))
                .using(project(":html-svg-eap"))
            substitute(module("org.jetbrains.compose.html.eap:internal-html-core-runtime-eap"))
                .using(project(":internal-html-core-runtime-eap"))
        }
    }
}
