// Coordinates fresh JVM workers that measure Compose and Thymeleaf first and subsequent SSR renders.
package benchmarks.analysis

import benchmarks.compose.*
import benchmarks.model.*
import benchmarks.thymeleaf.ThymeleafRenderer
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.io.File
import java.lang.management.ManagementFactory
import java.time.Instant
import kotlin.time.DurationUnit
import kotlin.time.measureTime

private val workloadNames = listOf(
    "tailwind-catalog", "form-app", "data-table", "svg-dashboard", "content-article",
    "preact-text", "preact-search-results", "preact-stack",
)

private val thymeleafWorkloads = setOf(
    "tailwind-catalog", "form-app", "data-table", "svg-dashboard", "content-article",
    "preact-text", "preact-search-results", "preact-stack",
)

private val skippedThymeleafWorkloads = mapOf(
    "preact-stack" to "renderer does not finish",
)

internal enum class JvmSsrTarget(val argument: String) {
    COMPOSE_JVM("compose"),
    THYMELEAF("thymeleaf"),
}

internal enum class JvmSsrScenario(val argument: String) {
    TAILWIND_CATALOG("tailwind-catalog"),
    FORM_APP("form-app"),
    DATA_TABLE("data-table"),
    SVG_DASHBOARD("svg-dashboard"),
    CONTENT_ARTICLE("content-article"),
    PREACT_TEXT("preact-text"),
    PREACT_SEARCH_RESULTS("preact-search-results"),
    PREACT_STACK("preact-stack"),
}

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

private fun count(name: String, default: Int, minimum: Int = 1): Int =
    (System.getenv(name)?.toIntOrNull() ?: if (System.getenv(name) == null) default else error("Invalid $name"))
        .also { require(it >= minimum) { "$name must be >= $minimum" } }

private fun median(values: List<Double>): Double? {
    if (values.isEmpty()) return null
    val sorted = values.sorted()
    return (sorted[(sorted.size - 1) / 2] + sorted[sorted.size / 2]) / 2
}

private fun workloadFixture(name: String): Any? = when (name) {
    "tailwind-catalog" -> catalogFixture()
    "form-app" -> formFixture()
    "data-table" -> dataTableFixture()
    "svg-dashboard" -> svgDashboardFixture()
    "content-article" -> articleFixture()
    "preact-search-results" -> preactSearchResultsFixture()
    "preact-text", "preact-stack" -> null
    else -> error("Unknown workload: $name")
}

private fun composeWorkload(name: String, fixture: Any?): () -> String = when (name) {
    "tailwind-catalog" -> { { renderTailwindCatalog(fixture as CatalogData) } }
    "form-app" -> { { renderFormApp(fixture as FormData) } }
    "data-table" -> { { renderDataTable(fixture as DataTableData) } }
    "svg-dashboard" -> { { renderSvgDashboard(fixture as SvgDashboardData) } }
    "content-article" -> { { renderContentArticle(fixture as ArticleData) } }
    "preact-text" -> ::renderPreactText
    "preact-search-results" -> { { renderPreactSearchResults(fixture as PreactSearchResultsData) } }
    "preact-stack" -> ::renderPreactStack
    else -> error("Unknown workload: $name")
}

private fun thymeleafWorkload(name: String, fixture: Any?): (() -> String)? = when (name) {
    in thymeleafWorkloads -> { { ThymeleafRenderer.render(name, mapOf("data" to fixture)) } }
    else -> null
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
    val allocationBean = ManagementFactory.getThreadMXBean() as? com.sun.management.ThreadMXBean
    if (allocationBean?.isThreadAllocatedMemorySupported == true && !allocationBean.isThreadAllocatedMemoryEnabled) {
        allocationBean.isThreadAllocatedMemoryEnabled = true
    }
    fun allocated() = if (allocationBean?.isThreadAllocatedMemorySupported == true)
        allocationBean.getThreadAllocatedBytes(Thread.currentThread().threadId()) else -1L
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
    val mapper = jacksonObjectMapper()
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
    val javaExecutable = File(System.getProperty("java.home"), "bin/java").absolutePath
    val composeResults = activeWorkloadNames.associateWith { mutableListOf<Any>() }
    val thymeleafResults = activeWorkloadNames
        .filter { it in thymeleafWorkloads && it !in skippedThymeleafWorkloads }
        .associateWith { mutableListOf<Any>() }
    val hasThymeleafWorkloads = includeThymeleaf && activeWorkloadNames.any { it in thymeleafWorkloads }
    val markdown = mutableListOf<String>()

    println("=".repeat(124))
    println(if (hasThymeleafWorkloads) "JVM SSR BENCHMARK — COMPOSE JVM & THYMELEAF" else "COMPOSE JVM SSR BENCHMARK")
    println("=".repeat(124))
    println("Settings: $repetitions JVM processes; each: renderer initialization + first render, $warmups warmups, $samples trials of $iterations renders | Stack 16m")
    println("Workloads: ${activeWorkloadNames.joinToString(", ")}")
    println()

    for (repetition in 0 until repetitions) {
        val order = activeWorkloadNames.drop(repetition % activeWorkloadNames.size) + activeWorkloadNames.take(repetition % activeWorkloadNames.size)
        for (name in order) {
            print("  Compose JVM process ${repetition + 1}/$repetitions ($name)... ")
            System.out.flush()
            val resultFile = File(directory, "compose-$name-$repetition.worker.json")
            val command = mutableListOf(javaExecutable, "-Xss16m")
            System.getProperty("benchmarks.profile.directory")?.let { path ->
                val profile = File(path, "compose-$name-$repetition.jfr")
                profile.parentFile.mkdirs()
                command += "-XX:StartFlightRecording=filename=${profile.absolutePath},settings=profile,dumponexit=true"
            }
            command += listOf("-cp", System.getProperty("java.class.path"),
                "benchmarks.analysis.SsrColdWarmReportKt", "--worker", "compose", name, resultFile.absolutePath)
            val process = ProcessBuilder(command).inheritIO().start()
            check(process.waitFor() == 0) { "Compose JVM worker failed: $name" }
            val workerNode = mapper.readTree(resultFile)
            composeResults.getValue(name).add(workerNode)
            val workerFirst = workerNode["initializationAndFirst"]["micros"].asDouble()
            val workerWarm = median(workerNode["subsequent"].map { it["micros"].asDouble() })!!
            val workerAllocations = workerNode["subsequent"].mapNotNull { it["allocatedBytes"].takeUnless { n -> n.isNull }?.asDouble() }
            val workerMemory = median(workerAllocations)?.let { " | Heap alloc: %.2f MiB".format(it / 1024 / 1024) } ?: ""
            println("Init + first: %.3f ms | Warm: %.3f ms%s".format(workerFirst / 1000.0, workerWarm / 1000.0, workerMemory))
            resultFile.delete()

            val thymeleafSkipReason = skippedThymeleafWorkloads[name]
            if (includeThymeleaf && thymeleafSkipReason != null) {
                println("  Thymeleaf   process ${repetition + 1}/$repetitions ($name)... SKIPPED ($thymeleafSkipReason)")
            } else if (includeThymeleaf && name in thymeleafWorkloads) {
                print("  Thymeleaf   process ${repetition + 1}/$repetitions ($name)... ")
                System.out.flush()
                val thResultFile = File(directory, "thymeleaf-$name-$repetition.worker.json")
                val thCommand = mutableListOf(javaExecutable, "-Xss16m")
                System.getProperty("benchmarks.profile.directory")?.let { path ->
                    val profile = File(path, "thymeleaf-$name-$repetition.jfr")
                    profile.parentFile.mkdirs()
                    thCommand += "-XX:StartFlightRecording=filename=${profile.absolutePath},settings=profile,dumponexit=true"
                }
                thCommand += listOf("-cp", System.getProperty("java.class.path"),
                    "benchmarks.analysis.SsrColdWarmReportKt", "--worker", "thymeleaf", name, thResultFile.absolutePath)
                val thProcess = ProcessBuilder(thCommand).inheritIO().start()
                check(thProcess.waitFor() == 0) { "Thymeleaf worker failed: $name" }
                val thNode = mapper.readTree(thResultFile)
                thymeleafResults.getValue(name).add(thNode)
                val thFirst = thNode["initializationAndFirst"]["micros"].asDouble()
                val thWarm = median(thNode["subsequent"].map { it["micros"].asDouble() })!!
                val thAllocations = thNode["subsequent"].mapNotNull { it["allocatedBytes"].takeUnless { n -> n.isNull }?.asDouble() }
                val thMemory = median(thAllocations)?.let { " | Heap alloc: %.2f MiB".format(it / 1024 / 1024) } ?: ""
                println("Init + first: %.3f ms | Warm: %.3f ms%s".format(thFirst / 1000.0, thWarm / 1000.0, thMemory))
                thResultFile.delete()
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
    for (name in activeWorkloadNames) {
        val processes = composeResults.getValue(name)
        val nodes = processes.map { mapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(it) }
        val first = median(nodes.map { it["initializationAndFirst"]["micros"].asDouble() })!!
        val warm = median(nodes.flatMap { it["subsequent"].map { sample -> sample["micros"].asDouble() } })!!
        val allocations = nodes.flatMap { it["subsequent"].mapNotNull { sample ->
            sample["allocatedBytes"].takeUnless { it.isNull }?.asDouble()
        } }
        val memory = median(allocations)?.let { "%.2f MiB".format(it / 1024 / 1024) } ?: "n/a"
        markdown += "| $name | %.3f ms | %.3f ms | $memory |".format(first / 1000.0, warm / 1000.0)
        val firstFormatted = "%.3f ms".format(first / 1000.0)
        val warmFormatted = "%.3f ms".format(warm / 1000.0)
        println(
            "%-25s %-16s %22s %20s %18s".format(
                name, "Compose JVM", firstFormatted, warmFormatted, memory
            )
        )

        val thProcesses = thymeleafResults[name]
        if (!thProcesses.isNullOrEmpty()) {
            val thNodes = thProcesses.map { mapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(it) }
            val thFirst = median(thNodes.map { it["initializationAndFirst"]["micros"].asDouble() })!!
            val thWarm = median(thNodes.flatMap { it["subsequent"].map { sample -> sample["micros"].asDouble() } })!!
            val thAllocations = thNodes.flatMap { it["subsequent"].mapNotNull { sample ->
                sample["allocatedBytes"].takeUnless { it.isNull }?.asDouble()
            } }
            val thMemory = median(thAllocations)?.let { "%.2f MiB".format(it / 1024 / 1024) } ?: "n/a"
            println(
                "%-25s %-16s %22s %20s %18s".format(
                    "", "Thymeleaf", "%.3f ms".format(thFirst / 1000.0), "%.3f ms".format(thWarm / 1000.0), thMemory
                )
            )
        } else if (includeThymeleaf && name in skippedThymeleafWorkloads) {
            println(
                "%-25s %-16s %22s %20s %18s".format(
                    "", "Thymeleaf", "SKIPPED", "SKIPPED", "n/a"
                )
            )
        }
        println("-".repeat(124))
    }

    File(directory, "jvm-results.json").writeText(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(mapOf(
        "settings" to mapOf("repetitions" to repetitions, "warmups" to warmups, "trials" to samples, "iterations" to iterations, "stack" to "16m", "clock" to "kotlin.time.TimeSource.Monotonic"),
        "runtime" to mapOf("java" to System.getProperty("java.version"), "vm" to System.getProperty("java.vm.name"),
            "os" to System.getProperty("os.name"), "osVersion" to System.getProperty("os.version"), "arch" to System.getProperty("os.arch")),
        "rawProcesses" to composeResults,
        "thymeleafProcesses" to thymeleafResults,
        "skippedThymeleafWorkloads" to skippedThymeleafWorkloads,
    )))
    File(directory, "jvm-results.md").writeText("""# Compose JVM SSR results

Each workload runs in $repetitions fresh JVMs with a 16 MiB stack and Kotlin's monotonic `measureTime` clock. Renderer initialization + first render starts before the workload renderer is resolved and ends when its first HTML string is complete; it excludes process and fixture initialization. Each process performs $warmups warmups, then $samples trials of $iterations renders; subsequent timings and allocations are per-render trial means. Raw timings and allocations are retained in JSON. Unavailable allocation counters are null.

| Scenario | Renderer initialization + first render median | Subsequent median | Subsequent allocation median |
| :--- | ---: | ---: | ---: |
${markdown.joinToString("\n")}
""")
    println("JVM results: ${directory.absolutePath}")
}
