// Runs the React SSR benchmark in fresh Node workers and writes its comparison report.
// Worker mode times React's server renderer; parent mode coordinates repetitions and summaries.
import { loadFixtures } from "./fixtures.mjs";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { Session } from "node:inspector/promises";
import { resolve } from "node:path";
import { fileURLToPath } from "node:url";
import {
  ensureSsrRuntime, reactRuntimeMode, ssrScenarios, ssrSettings, selectStringSsrScenarios, runNodeWorker, collectSsrProcesses,
} from "./runtime.mjs";
import { summarize } from "./metrics.mjs";
import { assertEquivalent } from "./html-equivalence.mjs";
import { printComparisonTable, writeReactSsrReport } from "./ssr-report.mjs";
export { printComparisonTable } from "./ssr-report.mjs";
import { createRunRecord, rootDirectory } from "./run-record.mjs";
ensureSsrRuntime();
const reactMode = reactRuntimeMode();
const reactLabel = reactMode === "development" ? "React 19 (dev)" : "React 19";
const allocationSamplingInterval = 32768;

function allocationIterations(outputBytes) {
  if (outputBytes < 16 * 1024) return 1000;
  if (outputBytes < 64 * 1024) return 300;
  return 100;
}

function sampledBytes(node) {
  return node.selfSize + node.children.reduce((total, child) => total + sampledBytes(child), 0);
}

async function measureAllocatedHeap(render, outputBytes, expected) {
  const iterations = allocationIterations(outputBytes);
  const session = new Session();
  session.connect();
  try {
    await session.post("HeapProfiler.startSampling", {
      samplingInterval: allocationSamplingInterval,
      includeObjectsCollectedByMajorGC: true,
      includeObjectsCollectedByMinorGC: true,
    });
    let html;
    for (let i = 0; i < iterations; i++) html = render();
    const { profile } = await session.post("HeapProfiler.stopSampling");
    assert.equal(html, expected, "Output changed during allocation sampling");
    const totalBytes = sampledBytes(profile.head);
    assert(Number.isFinite(totalBytes) && totalBytes > 0, "V8 produced no allocation samples");
    return { bytesPerRender: totalBytes / iterations, iterations, sampleCount: profile.samples.length };
  } finally {
    session.disconnect();
  }
}

const { iterations, trials, warmups, repetitions } = ssrSettings({
  warmups: process.env.BENCHMARK_WARMUPS ?? process.env.BENCHMARK_WARMUP ?? 3,
});
const allScenarios = ssrScenarios;
const requestedScenarios = process.env.BENCHMARK_SCENARIOS?.split(",").map(s => s.trim()).filter(Boolean);
const scenarios = selectStringSsrScenarios(allScenarios);

async function worker(scenario) {
  assert(allScenarios.includes(scenario), `Unknown scenario: ${scenario}`);
  const fixtures = await loadFixtures();
  const expected = await readFile(resolve(rootDirectory, `build/ssr-html/${scenario}.html`), "utf8");
  const initializationStart = performance.now();
  const { renderScenario } = await import(`../../build/web/react/dist/server-render-${scenario}.mjs`);
  const render = () => renderScenario(fixtures[scenario]);
  const firstHtml = render();
  const initializationAndFirstMs = performance.now() - initializationStart;
  assertEquivalent(expected, firstHtml, `${scenario} first render`);
  for (let i = 0; i < warmups; i++) render();
  const samples = [];
  let lastHtml;
  for (let trial = 0; trial < trials; trial++) {
    const start = performance.now();
    for (let i = 0; i < iterations; i++) lastHtml = render();
    samples.push((performance.now() - start) / iterations);
    assert.equal(lastHtml, firstHtml, `${scenario}: output changed after warmup`);
  }
  const outputBytes = Buffer.byteLength(firstHtml);
  // Profile after timing: allocation sampling changes execution speed.
  const allocatedHeap = await measureAllocatedHeap(render, outputBytes, firstHtml);
  return { initializationAndFirstMs, samples, outputBytes, allocatedHeap };
}

const deferTable = process.argv.includes("--defer-table") || process.env.BENCHMARK_DEFER_SSR_TABLE === "true";
const printTableOnly = process.argv.includes("--print-table") || process.argv.includes("--table-only");

if (process.argv[2] === "--worker") {
  console.log(JSON.stringify(await worker(process.argv[3])));
} else if (scenarios.length === 0) {
  console.log("No React SSR scenarios selected.");
} else if (printTableOnly) {
  await printComparisonTable(null, requestedScenarios?.length ? scenarios : null);
} else {
  const record = await createRunRecord("ssr", {
    repetitions, iterations, trials, warmups, nodeStackKiB: 8192,
    reactMode, reactMinified: true, nodeEnv: process.env.NODE_ENV, clock: "performance.now",
    allocationProfiler: "V8 HeapProfiler.startSampling", allocationSamplingInterval,
  });
  record.metadata.executionOrder = [];
  console.log("=".repeat(124));
  console.log(`${reactLabel.toUpperCase()} SSR BENCHMARK`);
  console.log("=".repeat(124));
  console.log(`Settings: ${repetitions} Node processes; each: renderer initialization + first render, ${warmups} warmups, ${trials} trials of ${iterations} renders | Stack 8m`);
  console.log(`Scenarios: ${scenarios.join(", ")}`);
  console.log();

  const raw = collectSsrProcesses({ scenarios, repetitions, executionOrder: record.metadata.executionOrder },
    (scenario, repetition) => {
      process.stdout.write(`  React SSR process ${repetition + 1}/${repetitions} (${scenario})... `);
      const parsed = runNodeWorker(fileURLToPath(import.meta.url), ["--worker", scenario], {
        env: { ...process.env, NODE_ENV: reactMode },
      });
      const warmMedian = summarize(parsed.samples).median;
      console.log(`Init + first: ${parsed.initializationAndFirstMs.toFixed(3)} ms | Warm: ${warmMedian.toFixed(3)} ms | Sampled heap: ${(parsed.allocatedHeap.bytesPerRender / 1024 / 1024).toFixed(2)} MiB`);
      return parsed;
    });
  const results = Object.fromEntries(Object.entries(raw).map(([scenario, processes]) => [scenario, {
    initializationAndFirst: summarize(processes.map(p => p.initializationAndFirstMs)),
    postWarmup: summarize(processes.flatMap(p => p.samples)),
    allocatedHeap: summarize(processes.map(p => p.allocatedHeap.bytesPerRender)),
    perProcess: processes.map(p => ({ repetition: p.repetition, postWarmup: summarize(p.samples), allocatedHeap: p.allocatedHeap })),
    outputBytes: processes[0].outputBytes, rawProcesses: processes,
  }]));
  await writeReactSsrReport(record.directory, results, {
    reactMode, reactLabel, repetitions, warmups, trials, iterations, allocationSamplingInterval,
  });
  await record.complete();

  if (!deferTable) {
    await printComparisonTable(results, scenarios);
  }
}
