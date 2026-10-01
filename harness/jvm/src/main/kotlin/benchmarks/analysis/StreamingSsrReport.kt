package benchmarks.analysis

import benchmarks.compose.streamComposeScenario
import benchmarks.thymeleaf.ThymeleafRenderer
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.io.File
import java.lang.management.ManagementFactory

private val composeStreamOutput = System.getenv("BENCHMARK_STREAM_OUTPUT") ?: "string"
private val milestones = contentMilestones()
private val streamingScenarios = JvmSsrScenario.entries.filter { it.argument in milestones }

private data class Reading(
    val firstChunkMicros: Double,
    val firstContentMicros: Double?,
    val first8KiBMicros: Double?,
    val completionMicros: Double,
    val chunks: Int,
    val chars: Int,
    val bytes: Int,
    val allocatedBytes: Long?,
)

private data class StreamingSample(
    val firstChunkMicros: Double,
    val firstContentMicros: Double,
    val first8KiBMicros: Double?,
    val completionMicros: Double,
    val chunks: Double,
    val allocatedBytes: Double?,
)

private data class StreamingWorkerResult(
    val first: Reading,
    val samples: List<StreamingSample>,
    val outputBytes: Int,
    val contentBytes: Int,
    val contentMilestone: String,
)

private fun worker(
    target: JvmSsrTarget,
    scenario: JvmSsrScenario,
    warmups: Int,
    trials: Int,
    iterations: Int,
    chunkSize: Int,
): StreamingWorkerResult {
    val name = scenario.argument
    val fixture = workloadFixture(name)
    val variables = mapOf("data" to fixture)
    val allocationBean = ManagementFactory.getThreadMXBean() as? com.sun.management.ThreadMXBean
    if (allocationBean?.isThreadAllocatedMemorySupported == true && !allocationBean.isThreadAllocatedMemoryEnabled) {
        allocationBean.isThreadAllocatedMemoryEnabled = true
    }
    fun allocated(): Long? = if (allocationBean?.isThreadAllocatedMemorySupported == true)
        allocationBean.getThreadAllocatedBytes(Thread.currentThread().threadId()) else null

    val milestone = milestones.getValue(name)
    var contentBytes: Int? = null

    fun measure(collectHtml: Boolean = false): Pair<Reading, String?> {
        val before = allocated()
        val start = System.nanoTime()
        val counter = StreamingCounter(start, contentBytes, collectHtml)
        when (target) {
            JvmSsrTarget.COMPOSE_JVM -> if (composeStreamOutput == "writer")
                streamComposeScenario(name, fixture, chunkSize, counter.writer())
            else streamComposeScenario(name, fixture, chunkSize, counter::accept)
            JvmSsrTarget.THYMELEAF -> ThymeleafRenderer.stream(name, variables, counter.writer())
        }
        counter.finish()
        val completion = System.nanoTime() - start
        val after = allocated()
        if (collectHtml && contentBytes == null) {
            contentBytes = milestone.byteOffset(counter.html())
            counter.resolveContent(checkNotNull(contentBytes))
        }
        check(counter.firstContentNanos != null) { "$name emitted no useful content" }
        val firstChunk = checkNotNull(counter.firstChunkNanos) { "$name emitted no chunks" }
        val reading = Reading(firstChunk / 1_000.0, counter.firstContentNanos?.div(1_000.0),
            counter.first8KiBNanos?.div(1_000.0), completion / 1_000.0, counter.chunks,
            counter.chars, counter.bytes, if (before != null && after != null) after - before else null)
        return reading to if (collectHtml) counter.html() else null
    }

    val (first, html) = measure(collectHtml = true)
    val expected = prepareJvmSsrWorkload(target, scenario)()
    check(html == expected) { "$target $name streaming output differs from string output" }
    val expectedBytes = expected.toByteArray(Charsets.UTF_8).size
    check(first.bytes == expectedBytes) { "UTF-8 sink byte count differs from output" }
    fun validate(reading: Reading) {
        check(reading.chars == expected.length && reading.bytes == expectedBytes) { "Output size changed" }
        check((reading.first8KiBMicros != null) == (reading.bytes >= FIRST_8_KIB_BYTES))
    }
    repeat(warmups) { validate(measure().first) }
    val samples = List(trials) {
        val readings = List(iterations) { measure().first.also(::validate) }
        StreamingSample(
            firstChunkMicros = readings.map { it.firstChunkMicros }.average(),
            firstContentMicros = readings.map { checkNotNull(it.firstContentMicros) }.average(),
            first8KiBMicros = if (expectedBytes >= FIRST_8_KIB_BYTES)
                readings.map { checkNotNull(it.first8KiBMicros) }.average() else null,
            completionMicros = readings.map { it.completionMicros }.average(),
            chunks = readings.map { it.chunks }.average(),
            allocatedBytes = readings.mapNotNull { it.allocatedBytes }.takeIf { it.isNotEmpty() }?.average(),
        )
    }
    check(measure(collectHtml = true).second == expected) { "$target $name output changed after trials" }
    return StreamingWorkerResult(first, samples, expectedBytes, checkNotNull(contentBytes), milestone.description)
}

private fun setting(name: String, default: Int, min: Int = 1): Int {
    val raw = System.getenv(name)
    val value = raw?.toIntOrNull() ?: if (raw == null) default else error("Invalid $name: $raw")
    require(value >= min) { "$name must be >= $min" }
    return value
}

private fun median(values: List<Double>): Double {
    val sorted = values.sorted()
    return (sorted[(sorted.size - 1) / 2] + sorted[sorted.size / 2]) / 2
}

fun main(args: Array<String>) {
    require(composeStreamOutput in setOf("string", "writer")) { "BENCHMARK_STREAM_OUTPUT must be string or writer" }
    val warmups = setting("BENCHMARK_WARMUPS", 3, 0)
    val trials = setting("BENCHMARK_TRIALS", 15)
    val iterations = setting("BENCHMARK_ITERATIONS", 100)
    val repetitions = setting("BENCHMARK_REPETITIONS", 3)
    val chunkSize = setting("BENCHMARK_STREAM_CHUNK_SIZE", 4096)
    val mapper = jacksonObjectMapper()

    if (args.firstOrNull() == "--worker") {
        val target = JvmSsrTarget.entries.single { it.argument == args[1] }
        val scenario = JvmSsrScenario.entries.single { it.argument == args[2] }
        File(args[3]).writeText(mapper.writeValueAsString(worker(target, scenario, warmups, trials, iterations, chunkSize)))
        return
    }

    val requested = System.getenv("BENCHMARK_SCENARIOS")?.split(",")?.map(String::trim)
    val scenarios = streamingScenarios.filter { requested.isNullOrEmpty() || it.argument in requested }
    require(scenarios.isNotEmpty()) { "No streaming SSR scenarios selected" }
    val directory = File(checkNotNull(System.getenv("BENCHMARK_RESULTS_DIRECTORY")))
    directory.mkdirs()
    val java = File(System.getProperty("java.home"), "bin/java").absolutePath
    val results = linkedMapOf<String, MutableMap<String, List<StreamingWorkerResult>>>()

    for (scenario in scenarios) {
        val targets = if (scenario.argument == "preact-stack") listOf(JvmSsrTarget.COMPOSE_JVM)
            else JvmSsrTarget.entries
        val byTarget = linkedMapOf<String, List<StreamingWorkerResult>>()
        for (target in targets) {
            val runs = List(repetitions) { repetition ->
                val output = File(directory, "stream-${target.argument}-${scenario.argument}-$repetition.worker.json")
                val command = listOf(java, "-Xss16m", "-cp", System.getProperty("java.class.path"),
                    "benchmarks.analysis.StreamingSsrReportKt", "--worker", target.argument,
                    scenario.argument, output.absolutePath)
                val process = ProcessBuilder(command).inheritIO().start()
                check(process.waitFor() == 0) { "Streaming worker failed: $target ${scenario.argument}" }
                val result = mapper.readValue<StreamingWorkerResult>(output)
                output.delete()
                result
            }
            byTarget[target.argument] = runs
            val samples = runs.flatMap { it.samples }
            val first = median(samples.map { it.firstChunkMicros }) / 1000
            val complete = median(samples.map { it.completionMicros }) / 1000
            println("Streaming ${scenario.argument} ${target.argument}: first %.3f ms, complete %.3f ms".format(first, complete))
        }
        results[scenario.argument] = byTarget
    }
    File(directory, "jvm-streaming-results.json").writeText(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(mapOf(
        "settings" to mapOf("warmups" to warmups, "trials" to trials, "iterations" to iterations,
            "repetitions" to repetitions, "chunkSize" to chunkSize, "composeOutput" to composeStreamOutput),
        "results" to results,
        "skipped" to mapOf("preact-stack/thymeleaf" to "renderer does not finish"),
    )))
}
