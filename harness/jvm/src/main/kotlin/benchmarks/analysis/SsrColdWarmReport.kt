// Coordinates fresh JVM workers that measure Compose and Thymeleaf first and subsequent SSR renders.
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
    if (target == JvmSsrTarget.THYMELEAF) {
        skippedThymeleafWorkloads[name]?.let { reason ->
            error("Thymeleaf $name is not supported: $reason")
        }
    }

    val fixture = workloadFixture(name)
    val allocation = ThreadAllocation()
    fun allocated() = allocation.read()
    val initializationBefore = allocated()
    lateinit var render: () -> String
    lateinit var expected: String
    val initializationDuration = measureTime {
        render = if (target == JvmSsrTarget.THYMELEAF) {
            thymeleafWorkload(name, fixture) ?: error("Unknown Thymeleaf workload: $name")
        } else {
            composeWorkload(name, fixture)
        }
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
    val activeWorkloadNames = if (!requestedScenarios.isNullOrEmpty()) {
        val matched = workloadNames.filter { it in requestedScenarios }
        if (matched.isNotEmpty()) matched else workloadNames
    } else {
        workloadNames
    }

    val targetsEnv = System.getenv("BENCHMARK_TARGETS")?.split(",")?.map { it.trim() }
    val includeThymeleaf = targetsEnv == null || "thymeleaf" in targetsEnv || (!targetsEnv.contains("compose") && !targetsEnv.contains("compose-jvm") && !targetsEnv.contains("no-thymeleaf"))

    val directory = File(System.getenv("BENCHMARK_RESULTS_DIRECTORY")
        ?: "results/runs/${Instant.now().toString().replace(':', '-')}-jvm-${ProcessHandle.current().pid()}")
    directory.mkdirs()
    val composeResults = activeWorkloadNames.associateWith { mutableListOf<JvmSsrWorkerResult>() }
    val thymeleafResults = activeWorkloadNames
        .filter { it in thymeleafWorkloads && it !in skippedThymeleafWorkloads }
        .associateWith { mutableListOf<JvmSsrWorkerResult>() }
    val hasThymeleafWorkloads = includeThymeleaf && activeWorkloadNames.any { it in thymeleafWorkloads }
    val markdown = mutableListOf<String>()

    println("=".repeat(124))
    println(if (hasThymeleafWorkloads) "JVM SSR BENCHMARK — COMPOSE JVM & THYMELEAF" else "COMPOSE JVM SSR BENCHMARK")
    println("=".repeat(124))
    println("Settings: $repetitions JVM processes; each: renderer initialization + first render, $warmups warmups, $samples trials of $iterations renders | Stack 16m")
    println("Compose HTML string rendering: ${composeHtmlStringRenderingMode()}")
    println("Compose HTML validation: ${composeHtmlValidationMode()}")
    println("Workloads: ${activeWorkloadNames.joinToString(", ")}")
    println()

    fun runTarget(target: JvmSsrTarget, name: String, repetition: Int) {
        val isCompose = target == JvmSsrTarget.COMPOSE_JVM
        val label = if (isCompose) "Compose JVM" else "Thymeleaf  "
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
            "${if (isCompose) "Compose JVM" else "Thymeleaf"} worker failed: $name", jvmArguments,
        )
        (if (isCompose) composeResults else thymeleafResults).getValue(name).add(result)
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
            runTarget(JvmSsrTarget.COMPOSE_JVM, name, repetition)
            val skipReason = skippedThymeleafWorkloads[name]
            if (includeThymeleaf && skipReason != null) {
                println("  Thymeleaf   process ${repetition + 1}/$repetitions ($name)... SKIPPED ($skipReason)")
            } else if (includeThymeleaf && name in thymeleafWorkloads) {
                runTarget(JvmSsrTarget.THYMELEAF, name, repetition)
            }
        }
    }

    println("\n" + "=".repeat(124))
    println(if (hasThymeleafWorkloads) "JVM SSR RESULTS — COMPOSE JVM & THYMELEAF" else "COMPOSE JVM SSR RESULTS")
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
        val cells = resultCells(composeResults.getValue(name))
        markdown += "| $name | ${cells.joinToString(" | ")} |"
        printRow(name, "Compose JVM", cells)
        val thymeleaf = thymeleafResults[name]
        if (!thymeleaf.isNullOrEmpty()) {
            printRow("", "Thymeleaf", resultCells(thymeleaf))
        } else if (includeThymeleaf && name in skippedThymeleafWorkloads) {
            printRow("", "Thymeleaf", listOf("SKIPPED", "SKIPPED", "n/a"))
        }
        println("-".repeat(124))
    }

    File(directory, "jvm-results.json").writeText(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(mapOf(
        "settings" to mapOf("repetitions" to repetitions, "warmups" to warmups, "trials" to samples, "iterations" to iterations, "stack" to "16m", "clock" to "kotlin.time.TimeSource.Monotonic", "composeStringRendering" to composeHtmlStringRenderingMode(), "composeValidation" to composeHtmlValidationMode()),
        "runtime" to mapOf("java" to System.getProperty("java.version"), "vm" to System.getProperty("java.vm.name"),
            "os" to System.getProperty("os.name"), "osVersion" to System.getProperty("os.version"), "arch" to System.getProperty("os.arch")),
        "rawProcesses" to composeResults,
        "thymeleafProcesses" to thymeleafResults,
        "skippedThymeleafWorkloads" to skippedThymeleafWorkloads,
    )))
    File(directory, "jvm-results.md").writeText("""# Compose JVM SSR results

Each workload runs in $repetitions fresh JVMs with a 16 MiB stack and Kotlin's monotonic `measureTime` clock. Compose HTML string rendering mode: ${composeHtmlStringRenderingMode()}; HTML validation: ${composeHtmlValidationMode()}. Renderer initialization + first render starts before the workload renderer is resolved and ends when its first HTML string is complete; it excludes process and fixture initialization. Each process performs $warmups warmups, then $samples trials of $iterations renders; subsequent timings and allocations are per-render trial means. Raw timings and allocations are retained in JSON. Unavailable allocation counters are null.

| Scenario | Renderer initialization + first render median | Subsequent median | Subsequent allocation median |
| :--- | ---: | ---: | ---: |
${markdown.joinToString("\n")}
""")
    val summary = ProcessBuilder(
        System.getenv("BENCHMARK_NODE") ?: "node", "harness/browser/ssr-report.mjs", directory.absolutePath,
    ).inheritIO().start()
    check(summary.waitFor() == 0) { "JVM report summary failed" }
    println("JVM results: ${directory.absolutePath}")
}
