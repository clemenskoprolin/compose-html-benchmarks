package benchmarks.analysis

import java.io.File

private val deliveryMode = (System.getenv("BENCHMARK_STREAM_DELIVERY") ?: "buffered-utf8").also {
    require(it in setOf("buffered-utf8", "renderer")) { "BENCHMARK_STREAM_DELIVERY must be buffered-utf8 or renderer" }
}
private val composeStreamOutput = System.getenv("BENCHMARK_STREAM_OUTPUT") ?: "writer"
private val milestones = contentMilestones()
private val streamingScenarios = JvmSsrScenario.entries.filter { it.argument in milestones }

private data class Reading(
    val firstChunkMicros: Double,
    val firstContentMicros: Double?,
    val first8KiBMicros: Double?,
    val completionMicros: Double,
    val chunks: Int,
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
    bufferBytes: Int,
): StreamingWorkerResult {
    val name = scenario.argument
    val fixture = workloadFixture(name)
    val allocation = ThreadAllocation()
    fun allocated(): Long? = if (allocation.supported) allocation.read() else null

    val milestone = milestones.getValue(name)
    var contentBytes: Int? = null
    val bufferedSink = if (deliveryMode == "buffered-utf8") BufferedUtf8Sink(StreamingCounter(0), bufferBytes) else null

    fun measure(collectHtml: Boolean = false): Pair<Reading, String?> {
        val before = allocated()
        val start = System.nanoTime()
        val counter = StreamingCounter(start, contentBytes, collectHtml && bufferedSink != null,
            collectWrites = collectHtml)
        bufferedSink?.reset(counter)
        val rendererWriter = if (bufferedSink == null) CountingRendererWriter(counter, collectHtml) else null
        val writer = bufferedSink?.writer ?: checkNotNull(rendererWriter)
        streamJvmSsrWorkload(target, name, fixture, writer, chunkSize, composeStreamOutput)
        if (bufferedSink != null) bufferedSink.finish() else writer.close()
        val completion = System.nanoTime() - start
        val after = allocated()
        val html = if (collectHtml) bufferedSink?.let { counter.html() } ?: rendererWriter?.html() else null
        if (collectHtml && contentBytes == null) {
            contentBytes = milestone.byteOffset(checkNotNull(html))
            counter.resolveContent(checkNotNull(contentBytes))
        }
        check(counter.firstContentNanos != null) { "$name emitted no useful content" }
        val firstChunk = checkNotNull(counter.firstChunkNanos) { "$name emitted no chunks" }
        val reading = Reading(firstChunk / 1_000.0, counter.firstContentNanos?.div(1_000.0),
            counter.first8KiBNanos?.div(1_000.0), completion / 1_000.0, counter.chunks,
            counter.bytes, if (before != null && after != null) after - before else null)
        return reading to html
    }

    val (first, html) = measure(collectHtml = true)
    val expected = jvmSsrWorkload(target, name, fixture)()
    check(html == expected) { "$target $name streaming output differs from string output" }
    val expectedBytes = expected.toByteArray(Charsets.UTF_8).size
    check(first.bytes == expectedBytes) { "UTF-8 sink byte count differs from output" }
    fun validate(reading: Reading) {
        check(reading.bytes == expectedBytes) { "Output size changed" }
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

fun main(args: Array<String>) {
    require(composeStreamOutput in setOf("string", "writer")) { "BENCHMARK_STREAM_OUTPUT must be string or writer" }
    val warmups = setting("BENCHMARK_WARMUPS", 3, 0)
    val trials = setting("BENCHMARK_TRIALS", 15)
    val iterations = setting("BENCHMARK_ITERATIONS", 100)
    val repetitions = setting("BENCHMARK_REPETITIONS", 3)
    val chunkSize = setting("BENCHMARK_STREAM_CHUNK_SIZE", 2048)
    val bufferBytes = setting("BENCHMARK_STREAM_BUFFER_SIZE", DEFAULT_STREAM_BUFFER_BYTES)
    val mapper = benchmarkJson

    if (args.firstOrNull() == "--worker") {
        val target = JvmSsrTarget.entries.single { it.argument == args[1] }
        val scenario = JvmSsrScenario.entries.single { it.argument == args[2] }
        File(args[3]).writeText(mapper.writeValueAsString(worker(target, scenario, warmups, trials, iterations, chunkSize, bufferBytes)))
        return
    }

    val requested = System.getenv("BENCHMARK_SCENARIOS")?.split(",")?.map(String::trim)
    val scenarios = streamingScenarios.filter { requested.isNullOrEmpty() || it.argument in requested }
    require(scenarios.isNotEmpty()) { "No streaming SSR scenarios selected" }
    val directory = File(checkNotNull(System.getenv("BENCHMARK_RESULTS_DIRECTORY")))
    directory.mkdirs()
    val results = linkedMapOf<String, MutableMap<String, List<StreamingWorkerResult>>>()
    val selectedTargets = selectedJvmSsrTargets()

    for (scenario in scenarios) {
        val targets = selectedTargets.filter { skipReason(it, scenario.argument) == null }
        val byTarget = linkedMapOf<String, List<StreamingWorkerResult>>()
        for (target in targets) {
            val runs = List(repetitions) { repetition ->
                val output = File(directory, "stream-${target.argument}-${scenario.argument}-$repetition.worker.json")
                runJvmWorker<StreamingWorkerResult>(
                    "benchmarks.analysis.StreamingSsrReportKt", listOf(target.argument, scenario.argument), output,
                    "Streaming worker failed: $target ${scenario.argument}",
                )
            }
            byTarget[target.argument] = runs
            val samples = runs.flatMap { it.samples }
            val first = median(samples.map { it.firstChunkMicros })!! / 1000
            val complete = median(samples.map { it.completionMicros })!! / 1000
            println("Streaming ${scenario.argument} ${target.argument}: first %.3f ms, complete %.3f ms".format(first, complete))
        }
        results[scenario.argument] = byTarget
    }
    File(directory, "jvm-streaming-results.json").writeText(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(mapOf(
        "targetVersions" to selectedTargets.associate { it.argument to it.version },
        "settings" to mapOf("warmups" to warmups, "trials" to trials, "iterations" to iterations,
            "repetitions" to repetitions, "chunkSize" to chunkSize, "composeOutput" to composeStreamOutput,
            "deliverySink" to deliveryMode,
            "bufferBytes" to if (deliveryMode == "buffered-utf8") bufferBytes else null,
            "jvmCharacterBufferChars" to if (deliveryMode == "buffered-utf8") STREAM_CHAR_BUFFER_CHARS else null),
        "results" to results,
        "targets" to selectedTargets.map { it.argument },
        "skipped" to scenarios.flatMap { scenario ->
            selectedTargets.mapNotNull { target ->
                skipReason(target, scenario.argument)?.let { "${scenario.argument}/${target.argument}" to it }
            }
        }.toMap(),
    )))
}
