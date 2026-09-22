// Run this main directly with IntelliJ's JVM profiler.
package benchmarks.analysis

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
//        JvmSsrScenario.DATA_TABLE,
         JvmSsrScenario.TAILWIND_CATALOG,
//         JvmSsrScenario.FORM_APP,
//         JvmSsrScenario.SVG_DASHBOARD,
//         JvmSsrScenario.CONTENT_ARTICLE,
//         JvmSsrScenario.PREACT_TEXT,
//         JvmSsrScenario.PREACT_SEARCH_RESULTS,
//         JvmSsrScenario.PREACT_STACK,
    ),
    warmups = 3,
    samples = 15,
    iterationsPerSample = 100,
)

private data class JvmSsrProfileConfiguration(
    val targets: List<JvmSsrTarget>,
    val scenarios: List<JvmSsrScenario>,
    val warmups: Int,
    val samples: Int,
    val iterationsPerSample: Int,
)

fun main() {
    require(profileConfiguration.targets.isNotEmpty()) { "Select at least one JVM SSR target" }
    require(profileConfiguration.scenarios.isNotEmpty()) { "Select at least one JVM SSR scenario" }

    val settings = JvmSsrWorkerSettings(
        warmups = profileConfiguration.warmups,
        samples = profileConfiguration.samples,
        iterations = profileConfiguration.iterationsPerSample,
    )
    for (target in profileConfiguration.targets) {
        for (scenario in profileConfiguration.scenarios) {
            println("Profiling ${target.argument} / ${scenario.argument}...")
            val result = runJvmSsrWorker(target, scenario, settings)
            println(
                "Finished ${target.argument} / ${scenario.argument}: " +
                    "${result.subsequent.size} samples, ${result.outputBytes} output bytes",
            )
        }
    }
}
