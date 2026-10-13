package benchmarks.analysis

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.jsoup.parser.Parser
import java.io.File
import java.io.Writer

internal const val FIRST_8_KIB_BYTES = 8192

internal data class ContentElement(
    val tag: String,
    val classes: List<String> = emptyList(),
    val attributes: Map<String, String> = emptyMap(),
    val ancestor: String? = null,
)

internal data class ContentMilestone(val description: String, val elements: List<ContentElement>) {
    fun byteOffset(html: String): Int {
        val document = Parser.htmlParser().setTrackPosition(true).parseInput(html, "")
        val ends = elements.map { rule ->
            val element = checkNotNull(document.getAllElements().firstOrNull { element ->
                element.normalName() == rule.tag && rule.classes.all(element::hasClass) &&
                    rule.attributes.all { (name, value) -> element.attr(name) == value } &&
                    (rule.ancestor == null || element.parents().any { it.normalName() == rule.ancestor })
            }) { "Missing streaming milestone <${rule.tag}>" }
            val range = if (rule.tag == "input") element.sourceRange() else element.endSourceRange()
            check(range.isTracked) { "Streaming milestone <${rule.tag}> has no complete source range" }
            element to range.end().pos()
        }
        if (elements.map { it.tag } == listOf("label", "input")) {
            check(ends[1].first.id().isNotEmpty()) { "First input must have an id" }
            check(ends[0].first.attr("for") == ends[1].first.id()) { "First input must match its label" }
        }
        return html.substring(0, ends.maxOf { it.second }).toByteArray(Charsets.UTF_8).size
    }
}

internal fun contentMilestones(): Map<String, ContentMilestone> = jacksonObjectMapper()
    .readValue(File("harness/streaming-milestones.json"))

// Observe actual encoded bytes after the shared delivery buffer, with no per-character scan.
internal class StreamingCounter(
    private val start: Long,
    private val contentBytes: Int? = null,
    collectHtml: Boolean = false,
    private val nanoTime: () -> Long = System::nanoTime,
    collectWrites: Boolean = collectHtml,
) {
    private val html = if (collectHtml) java.io.ByteArrayOutputStream() else null
    private val writes = if (collectWrites) mutableListOf<Pair<Int, Long>>() else null
    var firstChunkNanos: Long? = null
        private set
    var firstContentNanos: Long? = null
        private set
    var first8KiBNanos: Long? = null
        private set
    var chunks = 0
        private set
    var bytes = 0
        private set

    internal fun elapsed(): Long = nanoTime() - start

    internal fun observe(length: Int, elapsed: Long = elapsed()) {
        firstChunkNanos = firstChunkNanos ?: elapsed
        chunks++
        bytes += length
        if (bytes >= FIRST_8_KIB_BYTES) first8KiBNanos = first8KiBNanos ?: elapsed
        if (contentBytes != null && bytes >= contentBytes) firstContentNanos = firstContentNanos ?: elapsed
        writes?.add(bytes to elapsed)
    }

    fun accept(data: ByteArray, offset: Int, length: Int) {
        if (length == 0) return
        observe(length)
        html?.write(data, offset, length)
    }

    fun resolveContent(byteOffset: Int) {
        firstContentNanos = checkNotNull(writes?.firstOrNull { it.first >= byteOffset }?.second) {
            "Content milestone never reached the sink"
        }
    }

    fun html(): String = checkNotNull(html).toString(Charsets.UTF_8)
}

/** Renderer mode preserves character-write boundaries without allocating an encoded byte buffer. */
internal class CountingRendererWriter(private val counter: StreamingCounter, collectHtml: Boolean) : Writer() {
    private val html = if (collectHtml) StringBuilder() else null
    private var pendingHighSurrogate = false

    private inline fun accept(length: Int, charAt: (Int) -> Char) {
        if (length == 0) return
        val elapsed = counter.elapsed()
        var size = 0
        repeat(length) {
            val char = charAt(it)
            html?.append(char)
            if (pendingHighSurrogate) {
                check(char.isLowSurrogate()) { "Unpaired high surrogate" }
                pendingHighSurrogate = false
                size += 4
            } else when {
                char.isHighSurrogate() -> pendingHighSurrogate = true
                char.isLowSurrogate() -> error("Unpaired low surrogate")
                char.code < 0x80 -> size++
                char.code < 0x800 -> size += 2
                else -> size += 3
            }
        }
        counter.observe(size, elapsed)
    }

    override fun write(chars: CharArray, offset: Int, length: Int) = accept(length) { chars[offset + it] }
    override fun write(str: String, offset: Int, length: Int) = accept(length) { str[offset + it] }
    override fun flush() = Unit
    override fun close() { check(!pendingHighSurrogate) { "Unpaired high surrogate" } }
    fun html(): String = checkNotNull(html).toString()
}
