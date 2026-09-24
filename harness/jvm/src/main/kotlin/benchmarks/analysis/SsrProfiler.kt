// Run this main directly with IntelliJ's JVM profiler.
package benchmarks.analysis

import benchmarks.compose.COMPOSE_HTML_STRING_RENDERING_PROPERTY
import benchmarks.compose.composeHtmlStringRenderingMode

// Toggle this when profiling Compose: true reuses the renderer; false creates one per render.
private const val useReusableComposeHtmlRendering = false

/*
 * Edit this block to choose what IntelliJ profiles, then use the gutter action
 * "Profile 'SsrProfilerKt' with IntelliJ Profiler". A single target and scenario give
 * the clearest flame graph.
 */
private val profileConfiguration = JvmSsrProfileConfiguration(
    targets = listOf(
        JvmSsrTarget.COMPOSE_JVM,
        // JvmSsrTarget.THYMELEAF,
    ),
    scenarios = listOf(
        // JvmSsrScenario.DATA_TABLE,
        JvmSsrScenario.TAILWIND_CATALOG,
        // JvmSsrScenario.FORM_APP,
        // JvmSsrScenario.SVG_DASHBOARD,
        // JvmSsrScenario.CONTENT_ARTICLE,
        // JvmSsrScenario.PREACT_TEXT,
        // JvmSsrScenario.PREACT_SEARCH_RESULTS,
        // JvmSsrScenario.PREACT_STACK,
    ),
)

private data class JvmSsrProfileConfiguration(
    val targets: List<JvmSsrTarget>,
    val scenarios: List<JvmSsrScenario>,
)

// Give the first and subsequent renders distinct profiler stack frames.
private fun initialRender(render: () -> String): String = render()

private fun repeatedRender(render: () -> String): String = render()

fun main() {
    require(profileConfiguration.targets.isNotEmpty()) { "Select at least one JVM SSR target" }
    require(profileConfiguration.scenarios.isNotEmpty()) { "Select at least one JVM SSR scenario" }

    System.setProperty(
        COMPOSE_HTML_STRING_RENDERING_PROPERTY,
        if (useReusableComposeHtmlRendering) "keyed" else "unkeyed",
    )
    if (JvmSsrTarget.COMPOSE_JVM in profileConfiguration.targets) {
        println("Compose HTML string rendering: ${composeHtmlStringRenderingMode()}")
    }

    for (target in profileConfiguration.targets) {
        for (scenario in profileConfiguration.scenarios) {
            println("Profiling ${target.argument} / ${scenario.argument}...")
            val render = prepareJvmSsrWorkload(target, scenario)
            val first = initialRender(render)
            val second = repeatedRender(render)
            val third = repeatedRender(render)
            check(first == second && second == third) { "Output changed between renders" }
            println(
                "Finished ${target.argument} / ${scenario.argument}: " +
                    "3 renders, ${third.toByteArray(Charsets.UTF_8).size} output bytes",
            )
        }
    }
}
