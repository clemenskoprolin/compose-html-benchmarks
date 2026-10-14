// Coordinates fresh JVM workers that measure JVM renderers first and subsequent SSR renders.
package benchmarks.analysis

import benchmarks.compose.*
import java.io.File
import java.time.Instant
import kotlin.time.DurationUnit
import kotlin.time.measureTime

internal data class JvmSsrWorkerSettings(
    val warmups: Int,
    val samples: Int,
    val iterations: Int,
)

internal data class JvmSsrMeasurement(
    val micros: Double,
    val allocatedBytes: Double?,
)

internal data class JvmSsrWorkerResult(
    val initializationAndFirst: JvmSsrMeasurement,
    val subsequent: List<JvmSsrMeasurement>,
    val outputBytes: Int,
)

private fun composeHtmlValidationMode(): String =
    when (System.getenv("COMPOSE_HTML_VALIDATE_STRICTLY")?.trim()?.lowercase()) {
        "true", "1" -> "strict"
        else -> "fast"
    }

internal fun runJvmSsrWorker(
    target: JvmSsrTarget,
    scenario: JvmSsrScenario,
    settings: JvmSsrWorkerSettings,
): JvmSsrWorkerResult {
    require(settings.warmups >= 0) { "warmups must be >= 0" }
    require(settings.samples >= 1) { "samples must be >= 1" }
    require(settings.iterations >= 1) { "iterations must be >= 1" }

    val name = scenario.argument
    val fixture = workloadFixture(name)
    val allocation = ThreadAllocation()
    fun allocated() = allocation.read()
    val initializationBefore = allocated()
    lateinit var render: () -> String
    lateinit var expected: String
    val initializationDuration = measureTime {
        render = jvmSsrWorkload(target, name, fixture)
        expected = render()
    }
    val initializationAfter = allocated()
    val initializationAndFirst = JvmSsrMeasurement(
        micros = initializationDuration.toDouble(DurationUnit.MICROSECONDS),
        allocatedBytes = if (initializationBefore >= 0 && initializationAfter >= initializationBefore)
            (initializationAfter - initializationBefore).toDouble() else null,
    )

    fun measure(iterationCount: Int = 1): JvmSsrMeasurement {
        val before = allocated()
        var html = ""
        val duration = measureTime {
            repeat(iterationCount) { html = render() }
        }
        val after = allocated()
        check(html == expected) { "Output changed after first render" }
        return JvmSsrMeasurement(
            micros = duration.toDouble(DurationUnit.MICROSECONDS) / iterationCount,
            allocatedBytes = if (before >= 0 && after >= before) (after - before).toDouble() / iterationCount else null,
        )
    }

    repeat(settings.warmups) { check(render() == expected) }
    val subsequent = List(settings.samples) { measure(settings.iterations) }
    return JvmSsrWorkerResult(
        initializationAndFirst = initializationAndFirst,
        subsequent = subsequent,
        outputBytes = expected.toByteArray(Charsets.UTF_8).size,
    )
}

fun main(args: Array<String>) {
    val mapper = benchmarkJson
    val warmups = count("BENCHMARK_WARMUPS", 3, 0)
    val samples = count("BENCHMARK_JVM_SAMPLES", count("BENCHMARK_TRIALS", 15))
    val iterations = count("BENCHMARK_ITERATIONS", 100)
    val repetitions = count("BENCHMARK_REPETITIONS", 3)

    if (args.firstOrNull() == "--worker") {
        val targetArgument = if (args.size > 3) args[1] else "compose"
        val name = if (args.size > 3) args[2] else args[1]
        val resultFile = if (args.size > 3) args[3] else args.getOrNull(2)
        val target = JvmSsrTarget.entries.singleOrNull { it.argument == targetArgument }
            ?: error("Unknown JVM SSR target: $targetArgument")
        val scenario = JvmSsrScenario.entries.singleOrNull { it.argument == name }
            ?: error("Unknown JVM SSR scenario: $name")
        val result = runJvmSsrWorker(target, scenario, JvmSsrWorkerSettings(warmups, samples, iterations))
        val json = mapper.writeValueAsString(result)
        if (resultFile != null) File(resultFile).writeText(json) else print(json)

        return
    }

    val requestedScenarios = System.getenv("BENCHMARK_SCENARIOS")?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
    val selectedWorkloadNames = if (!requestedScenarios.isNullOrEmpty()) {
        val matched = workloadNames.filter { it in requestedScenarios }
        if (matched.isNotEmpty()) matched else workloadNames
    } else {
        workloadNames
    }

    val targets = selectedJvmSsrTargets()
    val activeWorkloadNames = selectedWorkloadNames
    val directory = File(System.getenv("BENCHMARK_RESULTS_DIRECTORY")
        ?: "results/runs/${Instant.now().toString().replace(':', '-')}-jvm-${ProcessHandle.current().pid()}")
    directory.mkdirs()
    val results = targets.associateWith { target ->
        activeWorkloadNames.filter { skipReason(target, it) == null }
            .associateWith { mutableListOf<JvmSsrWorkerResult>() }
    }
    require(results.values.any { it.isNotEmpty() }) { "No supported JVM SSR scenarios selected" }
    val markdown = mutableListOf<String>()
    val labels = targets.joinToString(" & ") { it.label }
    println("=".repeat(124))
    println("JVM SSR BENCHMARK — $labels")
    println("=".repeat(124))
    println("Settings: $repetitions JVM processes; each: renderer initialization + first render, $warmups warmups, $samples trials of $iterations renders | Stack 16m")
    println("Compose HTML string rendering: ${composeHtmlStringRenderingMode()}")
    println("Compose HTML validation: ${composeHtmlValidationMode()}")
    println("Workloads: ${activeWorkloadNames.joinToString(", ")}")
    println()

    fun runTarget(target: JvmSsrTarget, name: String, repetition: Int) {
        val isCompose = target == JvmSsrTarget.COMPOSE_JVM
        val label = target.label.padEnd(11)
        print("  $label process ${repetition + 1}/$repetitions ($name)... ")
        System.out.flush()
        val jvmArguments = buildList {
            if (isCompose) System.getProperty(COMPOSE_HTML_STRING_RENDERING_PROPERTY)?.let {
                add("-D$COMPOSE_HTML_STRING_RENDERING_PROPERTY=$it")
            }
            System.getProperty("benchmarks.profile.directory")?.let { path ->
                val profile = File(path, "${target.argument}-$name-$repetition.jfr")
                profile.parentFile.mkdirs()
                add("-XX:StartFlightRecording=filename=${profile.absolutePath},settings=profile,dumponexit=true")
            }
        }
        val result = runJvmWorker<JvmSsrWorkerResult>(
            "benchmarks.analysis.SsrColdWarmReportKt", listOf(target.argument, name),
            File(directory, "${target.argument}-$name-$repetition.worker.json"),
            "${target.label} worker failed: $name", jvmArguments,
        )
        results.getValue(target).getValue(name).add(result)
        val first = result.initializationAndFirst.micros / 1000
        val warm = median(result.subsequent.map { it.micros })!! / 1000
        val allocations = result.subsequent.mapNotNull { it.allocatedBytes }
        val memory = median(allocations)?.let { " | Heap alloc: %.2f MiB".format(it / 1024 / 1024) } ?: ""
        println("Init + first: %.3f ms | Warm: %.3f ms%s".format(first, warm, memory))
    }

    for (repetition in 0 until repetitions) {
        val offset = repetition % activeWorkloadNames.size
        val order = activeWorkloadNames.drop(offset) + activeWorkloadNames.take(offset)
        for (name in order) {
            for (target in targets.drop(repetition % targets.size) + targets.take(repetition % targets.size)) {
                val reason = skipReason(target, name)
                if (reason != null) println("  ${target.label} process ${repetition + 1}/$repetitions ($name)... SKIPPED ($reason)")
                else runTarget(target, name, repetition)
            }
        }
    }

    println("\n" + "=".repeat(124))
    println("JVM SSR RESULTS — $labels")
    println("=".repeat(124))
    println(
        "%-25s %-16s %22s %20s %18s".format(
            "Scenario", "Target", "Init + first median", "Subsequent median", "Allocated heap"
        )
    )
    println("-".repeat(124))
    fun resultCells(processes: List<JvmSsrWorkerResult>): List<String> {
        val samples = processes.flatMap { it.subsequent }
        val first = median(processes.map { it.initializationAndFirst.micros })!!
        val warm = median(samples.map { it.micros })!!
        val allocations = samples.mapNotNull { it.allocatedBytes }
        val memory = median(allocations)?.let { "%.2f MiB".format(it / 1024 / 1024) } ?: "n/a"
        return listOf("%.3f ms".format(first / 1000), "%.3f ms".format(warm / 1000), memory)
    }
    fun printRow(name: String, target: String, cells: List<String>) {
        println("%-25s %-16s %22s %20s %18s".format(name, target, *cells.toTypedArray()))
    }
    for (name in activeWorkloadNames) {
        for (target in targets) {
            val processes = results.getValue(target)[name]
            if (!processes.isNullOrEmpty()) {
                val cells = resultCells(processes)
                markdown += "| $name | ${target.label} | ${cells.joinToString(" | ")} |"
                printRow(name, target.label, cells)
            } else if (skipReason(target, name) != null) {
                printRow(name, target.label, listOf("SKIPPED", "SKIPPED", "n/a"))
                markdown += "| $name | ${target.label} | SKIPPED | SKIPPED | n/a |"
            }
        }
        println("-".repeat(124))
    }

    File(directory, "jvm-results.json").writeText(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(mapOf(
        "settings" to mapOf("repetitions" to repetitions, "warmups" to warmups, "trials" to samples, "iterations" to iterations, "stack" to "16m", "clock" to "kotlin.time.TimeSource.Monotonic", "composeStringRendering" to composeHtmlStringRenderingMode(), "composeValidation" to composeHtmlValidationMode()),
        "runtime" to mapOf("java" to System.getProperty("java.version"), "vm" to System.getProperty("java.vm.name"),
            "os" to System.getProperty("os.name"), "osVersion" to System.getProperty("os.version"), "arch" to System.getProperty("os.arch")),
        "targetVersions" to targets.associate { it.argument to it.version },
        *targets.map { it.resultKey to results.getValue(it) }.toTypedArray(),
    )))
    File(directory, "jvm-results.md").writeText("""# JVM SSR results

Each workload runs in $repetitions fresh JVMs with a 16 MiB stack and Kotlin's monotonic `measureTime` clock. Compose HTML string rendering mode: ${composeHtmlStringRenderingMode()}; HTML validation: ${composeHtmlValidationMode()}. Renderer initialization + first render starts before the workload renderer is resolved and ends when its first HTML string is complete; it excludes process and fixture initialization. Each process performs $warmups warmups, then $samples trials of $iterations renders; subsequent timings and allocations are per-render trial means. JStachio ${JvmSsrTarget.JSTACHIO.version} and jte ${JvmSsrTarget.JTE.version} use build-time compiled templates. JStachio presentation views are constructed inside each render and included in timing and allocation; jte reads the shared fixtures directly. Raw timings and allocations are retained in JSON. Unavailable allocation counters are null.

`fractional-style` measures Compose's style builder, including priority reset and property removal. It has no React or template-engine result.

| Scenario | Target | Renderer initialization + first render median | Subsequent median | Subsequent allocation median |
| :--- | :--- | ---: | ---: | ---: |
${markdown.joinToString("\n")}
""")
    val summary = ProcessBuilder(
        System.getenv("BENCHMARK_NODE") ?: "node", "harness/browser/ssr-report.mjs", directory.absolutePath,
    ).inheritIO().start()
    check(summary.waitFor() == 0) { "JVM report summary failed" }
    println("JVM results: ${directory.absolutePath}")
}
