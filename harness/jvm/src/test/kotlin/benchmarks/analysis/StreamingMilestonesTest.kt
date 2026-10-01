package benchmarks.analysis

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StreamingMilestonesTest {
    @Test
    fun `UTF-8 bytes and milestones span Writer calls and surrogate pairs`() {
        var now = 0L
        val counter = StreamingCounter(0, contentBytes = 8194, nanoTime = { now })
        val writer = counter.writer()
        now = 1
        writer.write("é".repeat(4095)) // 8190 bytes, 4095 UTF-16 units.
        assertNull(counter.first8KiBNanos)
        now = 2
        writer.write(charArrayOf('x', '\uD83D'), 1, 1)
        assertEquals(8190, counter.bytes)
        now = 3
        writer.write(charArrayOf('\uDE00', 'x'), 0, 1)
        assertEquals(8194, counter.bytes)
        assertEquals(1L, counter.firstChunkNanos)
        assertEquals(3L, counter.first8KiBNanos)
        assertEquals(3L, counter.firstContentNanos)
        counter.finish()
    }

    @Test
    fun `first render replays receipt times and small output never reaches 8 KiB`() {
        var now = 1L
        val counter = StreamingCounter(0, collectHtml = true, nanoTime = { now })
        counter.accept("")
        counter.accept("<span>é")
        now = 2
        counter.accept("😀</span>")
        counter.resolveContent(counter.html().toByteArray(Charsets.UTF_8).size)
        assertEquals(2L, counter.firstContentNanos)
        assertNull(counter.first8KiBNanos)
        assertEquals(counter.html().toByteArray(Charsets.UTF_8).size, counter.bytes)
        assertEquals(2, counter.chunks)
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
