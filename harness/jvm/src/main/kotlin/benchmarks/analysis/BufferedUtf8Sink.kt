package benchmarks.analysis

import java.io.BufferedWriter
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.Writer
import java.nio.charset.MalformedInputException
import java.nio.charset.CodingErrorAction

internal const val DEFAULT_STREAM_BUFFER_BYTES = 8192
internal const val STREAM_CHAR_BUFFER_CHARS = 8192

/** Batch small character writes before encoding, then deliver fixed-size UTF-8 byte batches. */
internal class BufferedUtf8Sink(counter: StreamingCounter, bufferBytes: Int) {
    private val delivery = BufferedByteDelivery(counter, bufferBytes)
    private val bufferedWriter = BufferedWriter(
        OutputStreamWriter(delivery, Charsets.UTF_8.newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)),
        STREAM_CHAR_BUFFER_CHARS,
    )
    private var pendingHighSurrogate = false
    val writer: Writer = object : Writer() {
        override fun write(chars: CharArray, offset: Int, length: Int) {
            bufferedWriter.write(chars, offset, length)
            if (length > 0) pendingHighSurrogate = chars[offset + length - 1].isHighSurrogate()
        }
        override fun write(str: String, offset: Int, length: Int) {
            bufferedWriter.write(str, offset, length)
            if (length > 0) pendingHighSurrogate = str[offset + length - 1].isHighSurrogate()
        }
        override fun flush() = bufferedWriter.flush()
        override fun close() = finish()
    }

    fun reset(counter: StreamingCounter) {
        delivery.reset(counter)
        pendingHighSurrogate = false
    }

    fun finish() {
        if (pendingHighSurrogate) throw MalformedInputException(1)
        bufferedWriter.flush()
        delivery.finish()
    }
}

/** Large renderer writes cannot bypass the common byte-sized delivery batches. */
private class BufferedByteDelivery(
    private var counter: StreamingCounter,
    bufferBytes: Int,
) : OutputStream() {
    init { require(bufferBytes > 0) { "bufferBytes must be positive" } }
    private val buffer = ByteArray(bufferBytes)
    private var used = 0
    private var closed = false
    private var started = false

    fun reset(nextCounter: StreamingCounter) {
        check(!started || closed) { "Previous render is not finished" }
        counter = nextCounter
        used = 0
        closed = false
        started = true
    }

    fun finish() {
        if (closed) return
        if (used > 0) deliver()
        closed = true
    }

    override fun write(value: Int) {
        check(!closed) { "Delivery sink is closed" }
        buffer[used++] = value.toByte()
        if (used == buffer.size) deliver()
    }

    override fun write(data: ByteArray, offset: Int, length: Int) {
        check(!closed) { "Delivery sink is closed" }
        java.util.Objects.checkFromIndexSize(offset, length, data.size)
        var position = offset
        val end = offset + length
        while (position < end) {
            val count = minOf(buffer.size - used, end - position)
            data.copyInto(buffer, used, position, position + count)
            used += count
            position += count
            if (used == buffer.size) deliver()
        }
    }

    // Renderer flushes may drain character/encoder storage but do not change delivery batches.
    override fun flush() = Unit
    override fun close() = finish()

    private fun deliver() {
        counter.accept(buffer, 0, used)
        used = 0
    }
}
