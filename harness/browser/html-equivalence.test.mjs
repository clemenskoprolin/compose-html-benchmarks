// Unit tests for structural HTML equivalence and shared statistical helpers.
import assert from "node:assert/strict";
import test from "node:test";
import { assertEquivalent } from "./html-equivalence.mjs";
import { rotated, summarize } from "./metrics.mjs";

test("equivalence checks content and attributes, not just tag counts", () => {
  assert.throws(() => assertEquivalent('<input value="one">', '<input value="two">', "value", true));
  assert.throws(() => assertEquivalent('<p>one</p>', '<p>two</p>', "text", true));
  assert.throws(() => assertEquivalent('<p aria-label="one"></p>', '<p></p>', "aria", true));
  assert.throws(() => assertEquivalent('<input checked>', '<input>', "checked", true));
});

test("equivalence permits serialization differences without losing text", () => {
  assertEquivalent('<input checked="checked" id="a">', '<input id="a" checked>', "boolean", true);
  assertEquivalent('<p>A &amp; B</p>', '<p>A <!-- -->&amp; B</p>', "text boundaries", true);
  assertEquivalent('<svg><rect width="2.0"/></svg>', '<svg><rect width="2"/></svg>', "SVG", true);
});

test("target rotation balances positions and sparse samples do not claim p95", () => {
  const values = ["react", "js", "wasm"];
  for (const value of values) assert.deepEqual([0, 1, 2].map(i => rotated(values, i).indexOf(value)).sort(), [0, 1, 2]);
  assert.equal(summarize([1, 2, 3, 4, 5, 6]).p95, null);
  assert.equal(summarize(Array.from({ length: 20 }, (_, i) => i + 1)).p95, 19);
});
