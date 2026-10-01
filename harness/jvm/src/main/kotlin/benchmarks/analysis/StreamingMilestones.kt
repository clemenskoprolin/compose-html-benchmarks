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

// Count UTF-8 without encoding/allocating each write. Carry a high surrogate across writes,
// as a UTF-8 Writer would, so a split supplementary character still contributes four bytes.
internal class StreamingCounter(
    private val start: Long,
    private val contentBytes: Int? = null,
    collectHtml: Boolean = false,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    private val html = if (collectHtml) StringBuilder() else null
    private val writes = if (collectHtml) mutableListOf<Pair<Int, Long>>() else null
    private var pendingHighSurrogate = false
    var firstChunkNanos: Long? = null
        private set
    var firstContentNanos: Long? = null
        private set
    var first8KiBNanos: Long? = null
        private set
    var chunks = 0
        private set
    var chars = 0
        private set
    var bytes = 0
        private set

    private fun countChar(char: Char) {
        if (pendingHighSurrogate) {
            pendingHighSurrogate = false
            if (char.isLowSurrogate()) {
                bytes += 4
                return
            }
            bytes++ // Java's UTF-8 encoder replaces an unpaired surrogate with '?'.
        }
        when {
            char.isHighSurrogate() -> pendingHighSurrogate = true
            char.isLowSurrogate() -> bytes++
            char.code < 0x80 -> bytes++
            char.code < 0x800 -> bytes += 2
            else -> bytes += 3
        }
    }

    private inline fun count(length: Int, charAt: (Int) -> Char) {
        if (length == 0) return
        val elapsed = nanoTime() - start
        firstChunkNanos = firstChunkNanos ?: elapsed
        chunks++
        chars += length
        repeat(length) { countChar(charAt(it)) }
        if (bytes >= FIRST_8_KIB_BYTES) first8KiBNanos = first8KiBNanos ?: elapsed
        if (contentBytes != null && bytes >= contentBytes) firstContentNanos = firstContentNanos ?: elapsed
        writes?.add(bytes to elapsed)
    }

    fun accept(chunk: String) {
        count(chunk.length, chunk::get)
        html?.append(chunk)
    }

    fun writer(): Writer = object : Writer() {
        override fun write(chars: CharArray, offset: Int, length: Int) {
            count(length) { chars[offset + it] }
            html?.append(chars, offset, length)
        }

        override fun write(str: String, offset: Int, length: Int) {
            count(length) { str[offset + it] }
            html?.append(str, offset, offset + length)
        }

        override fun flush() = Unit
        override fun close() = Unit
    }

    fun finish() {
        // All benchmark fixtures must be valid UTF-16; an incomplete code point cannot be
        // assigned an arrival time until another write or an encoding flush completes it.
        check(!pendingHighSurrogate) { "Streaming output ends with an unpaired high surrogate" }
    }

    fun resolveContent(byteOffset: Int) {
        firstContentNanos = checkNotNull(writes?.firstOrNull { it.first >= byteOffset }?.second) {
            "Content milestone never reached the sink"
        }
    }

    fun html(): String = checkNotNull(html).toString()
}
