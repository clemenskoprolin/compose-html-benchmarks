package benchmarks.analysis

import benchmarks.compose.streamComposeScenario
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.Writer
import java.lang.management.ManagementFactory

private class ThroughputSink(private val start: Long) : Writer() {
    var chars = 0
    var chunks = 0
    var firstChunkNanos: Long? = null
    private fun acceptLength(length: Int) {
        if (length == 0) return
        if (firstChunkNanos == null) firstChunkNanos = System.nanoTime() - start
        chunks++
        chars += length
    }
    fun accept(text: String) = acceptLength(text.length)
    override fun write(chars: CharArray, offset: Int, length: Int) = acceptLength(length)
    override fun flush() = Unit
    override fun close() = Unit
}

private class ByteSink : OutputStream() {
    var bytes = 0
    override fun write(value: Int) { bytes++ }
    override fun write(values: ByteArray, offset: Int, length: Int) { bytes += length }
}

/** Renderer throughput without the milestone sink's character-by-character UTF-8 counting. */
fun main(args: Array<String>) {
    require(args.size == 3) { "Usage: StreamingThroughputKt string|writer|utf8|string-utf8 SCENARIO OUTPUT.json" }
    val mode = args[0]
    require(mode in setOf("string", "writer", "utf8", "string-utf8"))
    val name = args[1]
    val stress = name in setOf("stream-pre", "stream-textarea")
    val scenario = if (stress) null else JvmSsrScenario.entries.single { it.argument == name }
    val fixture = if (stress) null else workloadFixture(name)
    val chunkSize = System.getenv("BENCHMARK_STREAM_CHUNK_SIZE")?.toInt() ?: 4096
    val warmups = System.getenv("BENCHMARK_WARMUPS")?.toInt() ?: 200
    val trials = System.getenv("BENCHMARK_TRIALS")?.toInt() ?: 15
    val iterations = System.getenv("BENCHMARK_ITERATIONS")?.toInt() ?: 100
    require(chunkSize > 0 && warmups >= 0 && trials > 0 && iterations > 0)
    val html = StringBuilder()
    streamComposeScenario(name, fixture, chunkSize, html::append)
    val expected = if (stress) {
        val tag = if (name == "stream-pre") "pre" else "textarea"
        "<$tag>\n\n" + ((if (tag == "pre") "<!--c-->" else "") + "a&amp;😀α".repeat(512)).repeat(256) + "</$tag>"
    } else prepareJvmSsrWorkload(JvmSsrTarget.COMPOSE_JVM, checkNotNull(scenario))()
    check(html.toString() == expected) { "$name string stream differs from expected HTML" }
    fun verifyWriter() {
        if (mode == "string") return
        if (mode == "writer") {
            val output = java.io.StringWriter()
            streamComposeScenario(name, fixture, chunkSize, output)
            check(output.toString() == expected)
        } else {
            val output = ByteArrayOutputStream()
            val writer = OutputStreamWriter(output, Charsets.UTF_8)
            if (mode == "string-utf8") streamComposeScenario(name, fixture, chunkSize) { writer.write(it) }
            else streamComposeScenario(name, fixture, chunkSize, writer)
            writer.flush()
            check(output.toByteArray().contentEquals(expected.toByteArray(Charsets.UTF_8)))
        }
    }
    verifyWriter()
    val expectedBytes = expected.toByteArray(Charsets.UTF_8).size
    val bean = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
    bean.isThreadAllocatedMemoryEnabled = true
    val thread = Thread.currentThread().threadId()
    fun measure(): Map<String, Any> {
        val before = bean.getThreadAllocatedBytes(thread)
        val start = System.nanoTime()
        val sink = ThroughputSink(start)
        val bytes = if (mode.endsWith("utf8")) ByteSink() else null
        when (mode) {
            "string" -> streamComposeScenario(name, fixture, chunkSize, sink::accept)
            "writer" -> streamComposeScenario(name, fixture, chunkSize, sink)
            "utf8", "string-utf8" -> {
                val writer = OutputStreamWriter(checkNotNull(bytes), Charsets.UTF_8)
                if (mode == "string-utf8") streamComposeScenario(name, fixture, chunkSize) { writer.write(it) }
                else streamComposeScenario(name, fixture, chunkSize, writer)
                writer.flush()
            }
        }
        val completion = System.nanoTime() - start
        val allocated = bean.getThreadAllocatedBytes(thread) - before
        if (bytes == null) check(sink.chars == expected.length) else check(bytes.bytes == expectedBytes)
        return mapOf("completionMicros" to completion / 1000.0, "allocatedBytes" to allocated,
            "firstChunkMicros" to (sink.firstChunkNanos ?: 0) / 1000.0, "chunks" to sink.chunks)
    }
    repeat(warmups) { measure() }
    val samples = List(trials) {
        val readings = List(iterations) { measure() }
        readings.first().keys.associateWith { key -> readings.map { (it.getValue(key) as Number).toDouble() }.average() }
    }
    html.setLength(0)
    streamComposeScenario(name, fixture, chunkSize, html::append)
    check(html.toString() == expected) { "$name output changed after trials" }
    verifyWriter()
    File(args[2]).apply { parentFile?.mkdirs() }.writeText(jacksonObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(mapOf(
        "settings" to mapOf("mode" to mode, "scenario" to name, "chunkSize" to chunkSize,
            "warmups" to warmups, "trials" to trials, "iterations" to iterations),
        "outputChars" to expected.length, "outputBytes" to expectedBytes, "samples" to samples,
    )))
}
