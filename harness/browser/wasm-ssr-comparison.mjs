// Benchmarks Compose HTML SSR in fresh Kotlin/Wasm Node processes.
import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { readFile, writeFile } from "node:fs/promises";
import { resolve } from "node:path";
import { positiveInteger, rotated, summarize } from "./metrics.mjs";
import { createRunRecord, rootDirectory } from "./run-record.mjs";

const composeMode = process.env.BENCHMARK_COMPOSE_STRING_RENDERING ?? "keyed";
assert(["keyed", "unkeyed"].includes(composeMode), "Wasm SSR supports keyed or unkeyed string rendering");
const executable = resolve(rootDirectory,
  "implementations/compose/wasm-ssr/build/compileSync/wasmJs/main/productionExecutable/optimized/compose-html-benchmarks-implementations-compose-wasm-ssr.mjs");
const allScenarios = ["tailwind-catalog", "form-app", "data-table", "svg-dashboard", "content-article",
  "preact-text", "preact-search-results", "preact-stack", "raw-text-style", "raw-text-elements",
  "style-heavy-catalog"];
const requested = process.env.BENCHMARK_SCENARIOS?.split(",").map(s => s.trim()).filter(Boolean);
const scenarios = requested?.length ? allScenarios.filter(s => requested.includes(s)) : allScenarios;
assert(scenarios.length > 0, "No known Wasm SSR scenarios selected");
if (requested) for (const name of requested) assert(allScenarios.includes(name), `Unknown Wasm SSR scenario: ${name}`);
const repetitions = positiveInteger(process.env.BENCHMARK_REPETITIONS ?? 3, "BENCHMARK_REPETITIONS");
const warmups = positiveInteger(process.env.BENCHMARK_WARMUPS ?? 3, "BENCHMARK_WARMUPS", 0);
const trials = positiveInteger(process.env.BENCHMARK_TRIALS ?? 15, "BENCHMARK_TRIALS");
const iterations = positiveInteger(process.env.BENCHMARK_ITERATIONS ?? 100, "BENCHMARK_ITERATIONS");

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

const raw = Object.fromEntries(scenarios.map(name => [name, []]));
for (let repetition = 0; repetition < repetitions; repetition++) {
  for (const scenario of rotated(scenarios, repetition)) {
    record.metadata.executionOrder.push({ repetition, scenario });
    process.stdout.write(`  Wasm process ${repetition + 1}/${repetitions} (${scenario})... `);
    const output = execFileSync(process.execPath,
      ["--stack-size=8192", executable, scenario, String(warmups), String(trials), String(iterations)],
      { encoding: "utf8", cwd: rootDirectory, maxBuffer: 10 * 1024 * 1024 });
    const parsed = JSON.parse(output);
    assert.equal(parsed.scenario, scenario);
    assert.equal(parsed.samples.length, trials);
    assert(Number.isFinite(parsed.initializationAndFirstMs));
    assert(parsed.samples.every(Number.isFinite));
    console.log(`Init + first: ${parsed.initializationAndFirstMs.toFixed(3)} ms | Warm: ${summarize(parsed.samples).median.toFixed(3)} ms`);
    raw[scenario].push({ repetition, ...parsed });
  }
}
const results = Object.fromEntries(Object.entries(raw).map(([scenario, processes]) => [scenario, {
  initializationAndFirst: summarize(processes.map(p => p.initializationAndFirstMs)),
  postWarmup: summarize(processes.flatMap(p => p.samples)),
  outputBytes: processes[0].outputBytes,
  rawProcesses: processes,
}]));
const rows = Object.entries(results).map(([scenario, result]) =>
  `| ${scenario} | ${result.initializationAndFirst.median.toFixed(3)} ms | ${result.postWarmup.median.toFixed(3)} ms |`);
console.log("\n" + "=".repeat(108));
console.log("KOTLIN/WASM NODE SSR RESULTS");
console.log("=".repeat(108));
for (const [scenario, result] of Object.entries(results)) {
  console.log(`${scenario.padEnd(25)} ${(result.initializationAndFirst.median.toFixed(3) + " ms").padStart(16)} ${(result.postWarmup.median.toFixed(3) + " ms").padStart(16)}`);
}
await writeFile(resolve(record.directory, "wasm-results.json"), JSON.stringify(results, null, 2) + "\n");
await writeFile(resolve(record.directory, "wasm-results.md"), `# Kotlin/Wasm Node SSR results\n\n` +
  `The optimized production executable uses ${composeMode} rendering in ${repetitions} fresh Node processes per workload. ` +
  `Each process performs ${warmups} warmups and ${trials} trials of ${iterations} renders. ` +
  `Times use Node's monotonic \`performance.now()\`; process startup and fixture parsing are excluded. ` +
  `Each workload's first output must exactly match the prepared Compose JVM HTML, and later output must remain identical. ` +
  `Heap allocation is not reported because Node's V8 heap counters do not cover Wasm linear memory.\n\n` +
  `| Scenario | First render median | Subsequent median |\n| :--- | ---: | ---: |\n${rows.join("\n")}\n`);
await record.complete();
