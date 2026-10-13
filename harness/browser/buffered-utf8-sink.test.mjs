import assert from "node:assert/strict";
import test from "node:test";
import { BufferedUtf8Sink } from "./buffered-utf8-sink.mjs";
import { StreamingCounter, contentByteOffset } from "./streaming-milestones.mjs";

test("UTF-8 delivery batches are independent of renderer write sizes and preserve split code points", () => {
  const bytes = Buffer.from("é".repeat(4095) + "😀" + "x".repeat(9000));
  const deliver = pieces => {
    const received = [];
    const sink = new BufferedUtf8Sink(chunk => received.push(Buffer.from(chunk)));
    for (const piece of pieces) sink.write(piece);
    sink.finish();
    sink.finish();
    assert.deepEqual(Buffer.concat(received), bytes);
    assert.deepEqual(received.map(chunk => chunk.length), [8192, 8192, bytes.length - 16384]);
    assert.throws(() => sink.write(Buffer.from("late")), /closed/);
  };
  deliver([bytes]);
  deliver([bytes.subarray(0, 8191), bytes.subarray(8191, 8193), bytes.subarray(8193)]);
  deliver(Array.from(bytes, byte => Buffer.from([byte])));
  assert.throws(() => new BufferedUtf8Sink(() => {}, 0), /positive/);
});

test("milestones record delivered bytes, including the final partial buffer", () => {
  let now = 1;
  const counter = new StreamingCounter(8194);
  const sink = new BufferedUtf8Sink(chunk => counter.accept(chunk, now));
  sink.write(Buffer.from("é".repeat(4095)));
  assert.equal(counter.bytes, 0);
  assert.equal(counter.firstChunkMs, null);
  now = 2;
  sink.write(Buffer.from("😀"));
  assert.equal(counter.bytes, 8192);
  assert.equal(counter.first8KiBMs, 2);
  assert.equal(counter.firstContentMs, null);
  now = 3;
  sink.finish();
  assert.equal(counter.bytes, 8194);
  assert.equal(counter.firstContentMs, 3);
});

test("both runtime-text workloads use the first complete text pair as content", () => {
  const prefix = '<div><div><span class="foo">é &amp; 😀</span><span class="bar">second</span>';
  for (const name of ["dynamic-text", "dynamic-text-escaped"]) {
    assert.equal(contentByteOffset(prefix + '</div><div>later</div></div>', name), Buffer.byteLength(prefix));
  }
});
