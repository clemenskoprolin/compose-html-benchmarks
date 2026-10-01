// Benchmarks Compose HTML SSR in fresh Kotlin/Wasm Node processes.
import assert from "node:assert/strict";
import { writeFile } from "node:fs/promises";
import { resolve } from "node:path";
import { summarize } from "./metrics.mjs";
import { createRunRecord, rootDirectory } from "./run-record.mjs";
import { scenarioSummary } from "./benchmark-summary.mjs";
import { ssrMetrics, ssrSummaryRecords } from "./summary-data.mjs";

import { jvmScenarios, ssrSettings, runNodeWorker, collectSsrProcesses } from "./runtime.mjs";

const composeMode = process.env.BENCHMARK_COMPOSE_STRING_RENDERING ?? "keyed";
assert(["keyed", "unkeyed"].includes(composeMode), "Wasm SSR supports keyed or unkeyed string rendering");
const executable = resolve(rootDirectory,
  "implementations/compose/wasm-ssr/build/compileSync/wasmJs/main/productionExecutable/optimized/compose-html-benchmarks-implementations-compose-wasm-ssr.mjs");
const allScenarios = jvmScenarios;
const requested = process.env.BENCHMARK_SCENARIOS?.split(",").map(s => s.trim()).filter(Boolean);
const scenarios = requested?.length ? allScenarios.filter(s => requested.includes(s)) : allScenarios;
assert(scenarios.length > 0, "No known Wasm SSR scenarios selected");
if (requested) for (const name of requested) assert(allScenarios.includes(name), `Unknown Wasm SSR scenario: ${name}`);
const { repetitions, warmups, trials, iterations } = ssrSettings();

const record = await createRunRecord("wasm-ssr", {
  repetitions, warmups, trials, iterations, nodeStackKiB: 8192,
  runtime: "Kotlin/Wasm on Node", composeMode, clock: "performance.now",
});
record.metadata.executionOrder = [];
console.log("=".repeat(108));
console.log("KOTLIN/WASM NODE SSR BENCHMARK");
console.log("=".repeat(108));
console.log(`Settings: ${repetitions} fresh Node processes per workload; ${warmups} warmups, ${trials} trials of ${iterations} renders; ${composeMode} rendering`);
console.log(`Scenarios: ${scenarios.join(", ")}`);

const raw = collectSsrProcesses({ scenarios, repetitions, executionOrder: record.metadata.executionOrder },
  (scenario, repetition) => {
    process.stdout.write(`  Wasm process ${repetition + 1}/${repetitions} (${scenario})... `);
    const parsed = runNodeWorker(executable, [scenario, String(warmups), String(trials), String(iterations)],
      { cwd: rootDirectory });
    assert.equal(parsed.scenario, scenario);
    assert.equal(parsed.samples.length, trials);
    assert(Number.isFinite(parsed.initializationAndFirstMs));
    assert(parsed.samples.every(Number.isFinite));
    console.log(`Init + first: ${parsed.initializationAndFirstMs.toFixed(3)} ms | Warm: ${summarize(parsed.samples).median.toFixed(3)} ms`);
    return parsed;
  });
const results = Object.fromEntries(Object.entries(raw).map(([scenario, processes]) => [scenario, {
  initializationAndFirst: summarize(processes.map(p => p.initializationAndFirstMs)),
  postWarmup: summarize(processes.flatMap(p => p.samples)),
  outputBytes: processes[0].outputBytes,
  rawProcesses: processes,
}]));
const rows = Object.entries(results).map(([scenario, result]) =>
  `| ${scenario} | ${result.initializationAndFirst.median.toFixed(3)} ms | ${result.postWarmup.median.toFixed(3)} ms |`);
const overall = scenarioSummary(ssrSummaryRecords(results, "Compose Wasm on Node"), ssrMetrics.slice(0, 2));
console.log("\n" + "=".repeat(108));
console.log("KOTLIN/WASM NODE SSR RESULTS");
console.log("=".repeat(108));
for (const [scenario, result] of Object.entries(results)) {
  console.log(`${scenario.padEnd(25)} ${(result.initializationAndFirst.median.toFixed(3) + " ms").padStart(16)} ${(result.postWarmup.median.toFixed(3) + " ms").padStart(16)}`);
}
console.log(overall.console);
await writeFile(resolve(record.directory, "wasm-results.json"), JSON.stringify(results, null, 2) + "\n");
await writeFile(resolve(record.directory, "wasm-results.md"), `# Kotlin/Wasm Node SSR results\n\n` +
  `The optimized production executable uses ${composeMode} rendering in ${repetitions} fresh Node processes per workload. ` +
  `Each process performs ${warmups} warmups and ${trials} trials of ${iterations} renders. ` +
  `Times use Node's monotonic \`performance.now()\`; process startup and fixture parsing are excluded. ` +
  `Each workload's first output must exactly match the prepared Compose JVM HTML, and later output must remain identical. ` +
  `Heap allocation is not reported because Node's V8 heap counters do not cover Wasm linear memory.\n\n` +
  `| Scenario | First render median | Subsequent median |\n| :--- | ---: | ---: |\n${rows.join("\n")}\n${overall.markdown}`);
await record.complete();
