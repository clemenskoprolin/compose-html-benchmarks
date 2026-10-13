import assert from "node:assert/strict";

export const defaultStreamBufferBytes = 8192;

export function streamDeliverySettings() {
  const mode = process.env.BENCHMARK_STREAM_DELIVERY ?? "buffered-utf8";
  assert(["buffered-utf8", "renderer"].includes(mode), "BENCHMARK_STREAM_DELIVERY must be buffered-utf8 or renderer");
  const bufferBytes = Number(process.env.BENCHMARK_STREAM_BUFFER_SIZE ?? defaultStreamBufferBytes);
  assert(Number.isSafeInteger(bufferBytes) && bufferBytes > 0, "BENCHMARK_STREAM_BUFFER_SIZE must be positive");
  return { deliverySink: mode, bufferBytes: mode === "buffered-utf8" ? bufferBytes : null };
}

// Same byte delivery policy as the JVM sink, including large writes and final partial buffers.
export class BufferedUtf8Sink {
  constructor(deliver, bufferBytes = defaultStreamBufferBytes) {
    assert(Number.isSafeInteger(bufferBytes) && bufferBytes > 0, "bufferBytes must be positive");
    this.buffer = Buffer.allocUnsafe(bufferBytes);
    this.deliver = deliver;
    this.used = 0;
    this.finished = false;
  }

  write(chunk) {
    assert(!this.finished, "Delivery sink is closed");
    for (let offset = 0; offset < chunk.length;) {
      const count = Math.min(this.buffer.length - this.used, chunk.length - offset);
      chunk.copy(this.buffer, this.used, offset, offset + count);
      this.used += count;
      offset += count;
      if (this.used === this.buffer.length) this.flushBuffer();
    }
  }

  flushBuffer() {
    this.deliver(this.buffer.subarray(0, this.used));
    this.used = 0;
  }

  finish() {
    if (this.finished) return;
    if (this.used) this.flushBuffer();
    this.finished = true;
  }
}
