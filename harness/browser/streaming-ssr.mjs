// Measures React's pipeable SSR stream against the same fixtures as JVM streaming.
import assert from "node:assert/strict";
import { readFile, writeFile } from "node:fs/promises";
import { Writable } from "node:stream";
import { resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { assertEquivalent } from "./html-equivalence.mjs";
import { summarize } from "./metrics.mjs";
import { ensureSsrRuntime, benchmarkCount as setting, runNodeFileWorker } from "./runtime.mjs";
import { contentByteOffset, StreamingCounter, streamingMilestones } from "./streaming-milestones.mjs";
import { rootDirectory } from "./run-record.mjs";


ensureSsrRuntime();
const allScenarios = Object.keys(streamingMilestones);

async function renderStream(renderScenario, data, contentBytes = null, collect = false) {
  const start = performance.now();
  const pieces = collect ? [] : null;
  const counter = new StreamingCounter(contentBytes, collect);
  await new Promise((resolve, reject) => {
    const sink = new Writable({ write(chunk, _encoding, done) {
      if (chunk.length) {
        counter.accept(chunk, performance.now() - start);
        if (pieces) pieces.push(Buffer.from(chunk));
      }
      done();
    } });
    sink.once("finish", resolve);
    sink.once("error", reject);
    let stream;
    stream = renderScenario(data, {
      onShellReady() { queueMicrotask(() => stream.pipe(sink)); },
      onShellError: reject,
      onError: reject,
    });
  });
  const completionMs = performance.now() - start;
  assert(counter.firstChunkMs !== null, "React emitted no chunks");
  if (contentBytes !== null) assert(counter.firstContentMs !== null, "React emitted no useful content");
  assert.equal(counter.first8KiBMs !== null, counter.bytes >= 8192, "8 KiB milestone disagrees with output size");
  return { firstChunkMs: counter.firstChunkMs, firstContentMs: counter.firstContentMs,
    first8KiBMs: counter.first8KiBMs, completionMs, chunks: counter.chunks, bytes: counter.bytes,
    ...(collect ? { counter } : {}),
    ...(collect ? { html: Buffer.concat(pieces).toString("utf8") } : {}) };
}

async function worker(scenario) {
  const fixtures = JSON.parse(await readFile(resolve(rootDirectory, "workloads/jvm/src/main/resources/fixtures.json")));
  const data = fixtures[scenario];
  const initializationStart = performance.now();
  const { renderScenario } = await import(`../../build/web/react/dist/server-stream-${scenario}.mjs`);
  const first = await renderStream(renderScenario, data, null, true);
  const initializationAndFirstMs = performance.now() - initializationStart;
  const expected = await readFile(resolve(rootDirectory, `build/ssr-html/${scenario}.html`), "utf8");
  assertEquivalent(expected, first.html, `${scenario} React stream`);
  const contentBytes = contentByteOffset(first.html, scenario);
  first.counter.resolveContent(contentBytes);
  first.firstContentMs = first.counter.firstContentMs;
  delete first.counter;
  const warmups = setting("BENCHMARK_WARMUPS", 3, 0);
  const trials = setting("BENCHMARK_TRIALS", 15);
  const iterations = setting("BENCHMARK_ITERATIONS", 100);
  for (let i = 0; i < warmups; i++) assert.equal((await renderStream(renderScenario, data, contentBytes)).bytes, first.bytes);
  const samples = [];
  for (let trial = 0; trial < trials; trial++) {
    const readings = [];
    for (let i = 0; i < iterations; i++) {
      const reading = await renderStream(renderScenario, data, contentBytes);
      assert.equal(reading.bytes, first.bytes, `${scenario}: output size changed`);
      readings.push(reading);
    }
    samples.push({
      firstChunkMs: readings.reduce((sum, item) => sum + item.firstChunkMs, 0) / iterations,
      firstContentMs: readings.reduce((sum, item) => sum + item.firstContentMs, 0) / iterations,
      first8KiBMs: first.first8KiBMs === null ? null
        : readings.reduce((sum, item) => sum + item.first8KiBMs, 0) / iterations,
      completionMs: readings.reduce((sum, item) => sum + item.completionMs, 0) / iterations,
      chunks: readings.reduce((sum, item) => sum + item.chunks, 0) / iterations,
    });
  }
  const last = await renderStream(renderScenario, data, contentBytes, true);
  assertEquivalent(expected, last.html, `${scenario} React stream after trials`);
  assert.equal(last.html, first.html, `${scenario}: streaming output changed after trials`);
  delete first.html;
  return { initializationAndFirstMs, first, samples, outputBytes: first.bytes, contentBytes,
    contentMilestone: streamingMilestones[scenario].description };
}

if (process.argv[2] === "--worker") {
  const scenario = process.argv[3];
  assert(allScenarios.includes(scenario), `Unknown streaming scenario: ${scenario}`);
  await writeFile(process.argv[4], JSON.stringify(await worker(scenario)));
} else {
  const requested = process.env.BENCHMARK_SCENARIOS?.split(",").map(value => value.trim());
  const scenarios = allScenarios.filter(name => !requested?.length || requested.includes(name));
  assert(scenarios.length > 0, "No streaming SSR scenarios selected");
  const repetitions = setting("BENCHMARK_REPETITIONS", 3);
  const directory = process.env.BENCHMARK_RESULTS_DIRECTORY;
  assert(directory, "BENCHMARK_RESULTS_DIRECTORY is required");
  const results = {};
  for (const scenario of scenarios) {
    const runs = [];
    for (let repetition = 0; repetition < repetitions; repetition++) {
      const output = resolve(directory, `react-stream-${scenario}-${repetition}.worker.json`);
      runs.push(await runNodeFileWorker(fileURLToPath(import.meta.url), ["--worker", scenario], output,
        { cwd: rootDirectory, env: process.env }));
    }
    results[scenario] = runs;
    const samples = runs.flatMap(run => run.samples);
    const first = summarize(samples.map(sample => sample.firstChunkMs)).median;
    const complete = summarize(samples.map(sample => sample.completionMs)).median;
    console.log(`Streaming ${scenario} React: first ${first.toFixed(3)} ms, complete ${complete.toFixed(3)} ms`);
  }
  await writeFile(resolve(directory, "react-streaming-results.json"), JSON.stringify({
    settings: { repetitions, reactMode: process.env.NODE_ENV, warmups: setting("BENCHMARK_WARMUPS", 3, 0),
      trials: setting("BENCHMARK_TRIALS", 15), iterations: setting("BENCHMARK_ITERATIONS", 100) },
    results,
  }, null, 2));
}
