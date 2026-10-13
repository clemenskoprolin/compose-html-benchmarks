package benchmarks.analysis

import java.nio.charset.MalformedInputException
import kotlin.test.*

class StreamingMilestonesTest {
    @Test
    fun `renderer mode observes small writes immediately and carries split surrogates`() {
        var now = 1L
        val counter = StreamingCounter(0, contentBytes = 6, nanoTime = { now })
        val writer = CountingRendererWriter(counter, collectHtml = true)
        writer.write("é")
        assertEquals(1L, counter.firstChunkNanos)
        assertEquals(2, counter.bytes)
        writer.write("\uD83D")
        now = 2
        writer.write("\uDE00")
        writer.close()
        assertEquals(6, counter.bytes)
        assertEquals(3, counter.chunks)
        assertEquals(2L, counter.firstContentNanos)
        assertEquals("é😀", writer.html())
        assertNull(counter.first8KiBNanos)
    }

    @Test
    fun `UTF-8 delivery waits for a full byte buffer and preserves split surrogate pairs`() {
        var now = 0L
        val counter = StreamingCounter(0, contentBytes = 8194, collectHtml = true, nanoTime = { now })
        val sink = BufferedUtf8Sink(counter, 8192)
        val writer = sink.writer
        now = 1
        writer.write("é".repeat(4095))
        writer.flush()
        assertEquals(0, counter.bytes)
        assertNull(counter.firstChunkNanos)
        now = 2
        writer.write(charArrayOf('x', '\uD83D'), 1, 1)
        writer.flush()
        assertEquals(0, counter.bytes)
        now = 3
        writer.write(charArrayOf('\uDE00', 'x'), 0, 1)
        writer.flush()
        assertEquals(8192, counter.bytes)
        assertEquals(3L, counter.firstChunkNanos)
        assertEquals(3L, counter.first8KiBNanos)
        assertNull(counter.firstContentNanos)
        now = 4
        sink.finish()
        assertEquals(8194, counter.bytes)
        assertEquals(4L, counter.firstContentNanos)
        assertEquals("é".repeat(4095) + "😀", counter.html())
    }

    @Test
    fun `large writes use fixed delivery batches and completion flushes only the remainder`() {
        var now = 1L
        val counter = StreamingCounter(0, collectHtml = true, nanoTime = { now })
        val sink = BufferedUtf8Sink(counter, 8192)
        val text = "x".repeat(8192 * 3 + 17)
        sink.writer.write(text)
        sink.writer.flush()
        assertEquals(8192 * 3, counter.bytes)
        assertEquals(3, counter.chunks)
        now = 2
        sink.finish()
        sink.finish()
        counter.resolveContent(text.length)
        assertEquals(2L, counter.firstContentNanos)
        assertEquals(4, counter.chunks)
        assertEquals(text, counter.html())
    }

    @Test
    fun `small output is delivered on completion with no 8 KiB milestone`() {
        var now = 1L
        val counter = StreamingCounter(0, collectHtml = true, nanoTime = { now })
        val sink = BufferedUtf8Sink(counter, 8192)
        val text = "<span>é😀</span>"
        sink.writer.write(text)
        sink.writer.flush()
        assertNull(counter.firstChunkNanos)
        now = 2
        sink.finish()
        counter.resolveContent(text.toByteArray(Charsets.UTF_8).size)
        assertEquals(2L, counter.firstChunkNanos)
        assertEquals(2L, counter.firstContentNanos)
        assertNull(counter.first8KiBNanos)
        assertEquals(text, counter.html())
        assertEquals(1, counter.chunks)
    }

    @Test
    fun `buffered sink reuses its writer and buffers across renders`() {
        val first = StreamingCounter(0, collectHtml = true)
        val sink = BufferedUtf8Sink(first, 7)
        val writer = sink.writer
        writer.write("first 😀")
        sink.finish()
        assertEquals("first 😀", first.html())

        val second = StreamingCounter(0, collectHtml = true)
        sink.reset(second)
        assertTrue(writer === sink.writer)
        writer.write("second é")
        sink.finish()
        assertEquals("second é", second.html())
        assertEquals("first 😀".toByteArray(Charsets.UTF_8).size, first.bytes)
        assertEquals("second é".toByteArray(Charsets.UTF_8).size, second.bytes)
    }

    @Test
    fun `small writes preserve Unicode across character buffer boundaries`() {
        val counter = StreamingCounter(0, collectHtml = true)
        val sink = BufferedUtf8Sink(counter, 37)
        val text = "x".repeat(STREAM_CHAR_BUFFER_CHARS - 1) + "😀éα"
        text.forEachIndexed { index, char ->
            when (index % 3) {
                0 -> sink.writer.write(char.code)
                1 -> sink.writer.write(charArrayOf(char), 0, 1)
                else -> sink.writer.write(char.toString())
            }
        }
        sink.finish()
        assertEquals(text, counter.html())
        val bytes = text.toByteArray(Charsets.UTF_8).size
        assertEquals(bytes, counter.bytes)
        assertEquals((bytes + 36) / 37, counter.chunks)
    }

    @Test
    fun `malformed UTF-16 is rejected when encoding finishes`() {
        val sink = BufferedUtf8Sink(StreamingCounter(0), 8192)
        sink.writer.write("bad\uD800")
        assertFailsWith<MalformedInputException> { sink.finish() }
        assertFailsWith<IllegalArgumentException> { BufferedUtf8Sink(StreamingCounter(0), 0) }
    }

    @Test
    fun `content offsets end at complete source elements`() {
        val milestones = contentMilestones()
        val prefix = "<table><thead><tr><th>é</th></tr></thead><tbody><tr><td>😀</td></tr>"
        assertEquals(prefix.toByteArray(Charsets.UTF_8).size,
            milestones.getValue("data-table").byteOffset(prefix + "<tr><td>Later</td></tr></tbody></table>"))
        val form = "<label for=\"a\">A</label><input id=\"a\" placeholder=\"a > b\">"
        assertEquals(form.toByteArray(Charsets.UTF_8).size,
            milestones.getValue("form-app").byteOffset(form + "<input id=\"b\">"))
    }
}
