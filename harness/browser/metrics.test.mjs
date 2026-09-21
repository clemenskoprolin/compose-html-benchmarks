// Unit tests for the shared metric validation and summary helpers.
import assert from "node:assert/strict";
import test from "node:test";
import { positiveInteger, startupMetrics, summarize } from "./metrics.mjs";

test("summaries average both middle samples and preserve missing measurements", () => {
  assert.equal(summarize([10, 2, 8, 4]).median, 6);
  assert.equal(summarize([null, 0, 10]).median, 5);
  assert.deepEqual(summarize([null]), { n: 0, median: null, min: null, max: null, p95: null });
  assert.throws(() => summarize([NaN]));
});

test("startup measures readiness, not React's asynchronous API return", () => {
  const metrics = startupMetrics({
    navigation: { responseStart: 40 },
    marks: [
      { name: "react-mount-start", startTime: 50 },
      { name: "react-mount-end", startTime: 51 },
      { name: "app-ready", startTime: 150 },
    ],
    paints: [{ name: "first-contentful-paint", startTime: 80 }],
  }, "react");
  assert.deepEqual(metrics, {
    ttfbMs: 40, navigationReadyMs: 150, postTtfbReadyMs: 110,
    fcpMs: 80, hydrationDurationMs: 100,
  });
});

test("zero timestamps are valid, absent paint stays null, missing readiness fails", () => {
  const timing = {
    navigation: { responseStart: 0 },
    marks: [{ name: "kt-mount-start", startTime: 0 }, { name: "app-ready", startTime: 10 }],
    paints: [],
  };
  assert.equal(startupMetrics(timing, "compose-js").hydrationDurationMs, 10);
  assert.equal(startupMetrics(timing, "compose-js").fcpMs, null);
  assert.throws(() => startupMetrics({ ...timing, marks: [] }, "compose-js"));
});

test("invalid sample counts fail early, while zero warmups are supported", () => {
  assert.equal(positiveInteger("0", "warmups", 0), 0);
  for (const value of [0, -1, 1.5, "bad", Infinity]) {
    assert.throws(() => positiveInteger(value, "runs"));
  }
});
