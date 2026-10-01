import assert from "node:assert/strict";
import test from "node:test";
import { contentByteOffset, StreamingCounter } from "./streaming-milestones.mjs";

test("content waits for the whole result, including nested elements and multibyte text", () => {
  const prefix = '<div class="search-results"><div class="search-results-item"><div>é😀</div><button>Buy</button></div>';
  const html = prefix + '<div class="search-results-item">Later</div></div>';
  const offset = contentByteOffset(html, "preact-search-results");
  assert.equal(offset, Buffer.byteLength(prefix));
  const counter = new StreamingCounter(offset);
  const bytes = Buffer.from(html);
  counter.accept(Buffer.alloc(0), 0);
  counter.accept(bytes.subarray(0, offset - 2), 1);
  assert.equal(counter.firstContentMs, null);
  counter.accept(bytes.subarray(offset - 2, offset - 1), 2);
  assert.equal(counter.firstContentMs, null);
  counter.accept(bytes.subarray(offset - 1), 3);
  assert.equal(counter.firstChunkMs, 1);
  assert.equal(counter.firstContentMs, 3);
  assert.equal(counter.first8KiBMs, null);
});

test("table content requires the header and first data row", () => {
  const prefix = '<table><thead><tr><th>A</th></tr></thead><tbody><tr><td>B</td></tr>';
  assert.equal(contentByteOffset(prefix + '<tr><td>C</td></tr></tbody></table>', "data-table"),
    Buffer.byteLength(prefix));
});

test("labeled input finishes at the void tag, with quoted greater-than signs", () => {
  const prefix = '<form><label for="first">Name</label><input placeholder="a > b" id="first">';
  assert.equal(contentByteOffset(prefix + '<input id="later"></form>', "form-app"), Buffer.byteLength(prefix));
  assert.throws(() => contentByteOffset('<label for="a">A</label><input id="b">', "form-app"), /match its label/);
});

test("article paragraph is inside the article, after its heading", () => {
  const prefix = '<p>Unrelated</p><article><h1>Heading</h1><section><p>First</p>';
  assert.equal(contentByteOffset(prefix + '<p>Later</p></section></article>', "content-article"), Buffer.byteLength(prefix));
});

test("missing or incomplete content fails rather than silently measuring a different milestone", () => {
  assert.throws(() => contentByteOffset('<article><h1>A</h1></article>', "content-article"), /missing milestone/);
  assert.throws(() => contentByteOffset('<svg><text>A</text>', "svg-dashboard"), /complete source range/);
});

test("8 KiB counts bytes, crosses writes, and latches the first receipt", () => {
  const counter = new StreamingCounter(2);
  counter.accept(Buffer.from("é".repeat(4095)), 1);
  assert.equal(counter.bytes, 8190);
  assert.equal(counter.first8KiBMs, null);
  counter.accept(Buffer.from("é"), 2);
  counter.accept(Buffer.from("later"), 3);
  assert.equal(counter.first8KiBMs, 2);
  assert.equal(counter.firstContentMs, 1);
});

test("first-render content uses captured receipt times, with split UTF-8 code points", () => {
  const counter = new StreamingCounter(null, true);
  const bytes = Buffer.from("😀");
  counter.accept(bytes.subarray(0, 2), 5);
  counter.accept(bytes.subarray(2), 7);
  counter.resolveContent(4);
  assert.equal(counter.bytes, 4);
  assert.equal(counter.firstContentMs, 7);
});
