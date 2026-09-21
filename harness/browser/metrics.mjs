// Shared validation, order rotation, and statistical-summary helpers for benchmark runners.
import assert from "node:assert/strict";

export function positiveInteger(value, name, minimum = 1) {
  const number = Number(value);
  assert(Number.isSafeInteger(number) && number >= minimum, `${name} must be an integer >= ${minimum}`);
  return number;
}

export function summarize(values) {
  const sorted = values.filter((value) => value != null).sort((a, b) => a - b);
  assert(sorted.every(Number.isFinite), "Non-finite benchmark sample");
  if (!sorted.length) return { n: 0, median: null, min: null, max: null, p95: null };
  const mid = Math.floor(sorted.length / 2);
  const median = sorted.length % 2 ? sorted[mid] : (sorted[mid - 1] + sorted[mid]) / 2;
  return {
    n: sorted.length, median, min: sorted[0], max: sorted.at(-1),
    p95: sorted.length >= 20 ? sorted[Math.ceil(sorted.length * 0.95) - 1] : null,
  };
}

export function startupMetrics({ navigation, marks, paints }, target) {
  const mark = (name) => marks.find((entry) => entry.name === name)?.startTime;
  const ready = mark("app-ready");
  assert(Number.isFinite(navigation?.responseStart), "Missing navigation responseStart");
  assert(Number.isFinite(ready) && ready >= navigation.responseStart, "Missing or invalid app-ready mark");
  const start = target === "react"
    ? mark("react-hydrate-start") ?? mark("react-mount-start")
    : mark("kt-hydrate-start") ?? mark("kt-mount-start") ?? mark("kt-main-start");
  assert(Number.isFinite(start) && start <= ready, "Missing or invalid framework startup mark");
  return {
    ttfbMs: navigation.responseStart,
    navigationReadyMs: ready,
    postTtfbReadyMs: ready - navigation.responseStart,
    fcpMs: paints.find((entry) => entry.name === "first-contentful-paint")?.startTime ?? null,
    // React's render()/hydrateRoot() return before commit. Always use explicit readiness.
    hydrationDurationMs: ready - start,
  };
}

export function rotated(values, offset) {
  const start = offset % values.length;
  return values.slice(start).concat(values.slice(0, start));
}
