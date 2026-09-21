// Runs the React SSR benchmark in fresh Node workers and writes its comparison report.
// Worker mode times React's server renderer; parent mode coordinates repetitions and summaries.
import assert from "node:assert/strict";
import { readFile, writeFile } from "node:fs/promises";
import { execFileSync } from "node:child_process";
import { resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { ensureSsrRuntime } from "./runtime.mjs";
import { positiveInteger, rotated, summarize } from "./metrics.mjs";
import { assertEquivalent } from "./html-equivalence.mjs";
import { createRunRecord, rootDirectory } from "./run-record.mjs";
ensureSsrRuntime();

const iterations = positiveInteger(process.env.BENCHMARK_ITERATIONS ?? 100, "BENCHMARK_ITERATIONS");
const trials = positiveInteger(process.env.BENCHMARK_TRIALS ?? 15, "BENCHMARK_TRIALS");
const warmups = positiveInteger(process.env.BENCHMARK_WARMUPS ?? process.env.BENCHMARK_WARMUP ?? 3, "BENCHMARK_WARMUPS", 0);
const repetitions = positiveInteger(process.env.BENCHMARK_REPETITIONS ?? 3, "BENCHMARK_REPETITIONS");
const allScenarios = ["tailwind-catalog", "form-app", "data-table", "svg-dashboard", "content-article", "preact-text", "preact-search-results", "preact-stack"];
const requestedScenarios = process.env.BENCHMARK_SCENARIOS?.split(",").map(s => s.trim()).filter(Boolean);
const scenarios = (requestedScenarios && requestedScenarios.length > 0)
  ? (allScenarios.filter(s => requestedScenarios.includes(s)).length > 0 ? allScenarios.filter(s => requestedScenarios.includes(s)) : allScenarios)
  : allScenarios;

async function worker(scenario) {
  assert(allScenarios.includes(scenario), `Unknown scenario: ${scenario}`);
  const fixtures = JSON.parse(await readFile(resolve(rootDirectory, "workloads/jvm/src/main/resources/fixtures.json")));
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
  return { initializationAndFirstMs, samples, outputBytes: Buffer.byteLength(firstHtml) };
}

const deferTable = process.argv.includes("--defer-table") || process.env.BENCHMARK_DEFER_SSR_TABLE === "true";
const printTableOnly = process.argv.includes("--print-table") || process.argv.includes("--table-only");

export async function printComparisonTable(results = null, customScenarios = null) {
  try {
    const jvmResultsPath = resolve(rootDirectory, "results/runs/latest/jvm-results.json");
    const jvmData = JSON.parse(await readFile(jvmResultsPath, "utf8"));
    const jvmRaw = jvmData.rawProcesses || {};
    const thymeleafRaw = jvmData.thymeleafProcesses || {};
    if (!results) {
      const ssrResultsPath = resolve(rootDirectory, "results/runs/latest/ssr-results.json");
      results = JSON.parse(await readFile(ssrResultsPath, "utf8"));
    }
    const targetScenarios = customScenarios || (requestedScenarios && requestedScenarios.length > 0 ? scenarios : Object.keys(results));
    const commonScenarios = targetScenarios.filter(s => jvmRaw[s] && jvmRaw[s].length > 0);
    if (commonScenarios.length > 0) {
      const hasThymeleaf = Object.values(thymeleafRaw).some(processes => processes.length > 0);
      console.log("\n" + "=".repeat(124));
      console.log("SSR COMPARISON RESULTS — COMPOSE JVM vs REACT 19" + (hasThymeleaf ? " vs THYMELEAF" : ""));
      console.log("=".repeat(124));
      console.log(
        `${"Scenario".padEnd(25)} ${"Target".padEnd(16)} ${"Init + first median".padStart(22)} ${"Subsequent median".padStart(20)} ${"Allocated heap".padStart(18)}`
      );
      console.log("-".repeat(124));
      for (const scenario of commonScenarios) {
        const jvmProcesses = jvmRaw[scenario] || [];
        const jvmFirstMedian = summarize(jvmProcesses.map(p => p.initializationAndFirst.micros)).median;
        const jvmWarmSamples = jvmProcesses.flatMap(p => p.subsequent.map(s => s.micros));
        const jvmWarmMedian = summarize(jvmWarmSamples).median;
        const jvmAllocSamples = jvmProcesses.flatMap(p => p.subsequent.map(s => s.allocatedBytes).filter(b => b != null));
        const jvmAllocMedian = jvmAllocSamples.length ? summarize(jvmAllocSamples).median : null;
        const jvmMemStr = jvmAllocMedian != null ? `${(jvmAllocMedian / 1024 / 1024).toFixed(2)} MiB` : "n/a";
        const jvmFirstStr = `${(jvmFirstMedian / 1000).toFixed(3)} ms`;
        const jvmWarmStr = `${(jvmWarmMedian / 1000).toFixed(3)} ms`;

        console.log(
          `${scenario.padEnd(25)} ${"Compose JVM".padEnd(16)} ${jvmFirstStr.padStart(22)} ${jvmWarmStr.padStart(20)} ${jvmMemStr.padStart(18)}`
        );

        const reactResult = results[scenario];
        if (reactResult) {
          const reactFirstStr = `${reactResult.initializationAndFirst.median.toFixed(3)} ms`;
          const reactWarmStr = `${reactResult.postWarmup.median.toFixed(3)} ms`;
          console.log(
            `${"".padEnd(25)} ${"React 19".padEnd(16)} ${reactFirstStr.padStart(22)} ${reactWarmStr.padStart(20)} ${"—".padStart(18)}`
          );
        }

        const thProcesses = thymeleafRaw[scenario] || [];
        if (thProcesses.length > 0) {
          const thFirstMedian = summarize(thProcesses.map(p => p.initializationAndFirst.micros)).median;
          const thWarmSamples = thProcesses.flatMap(p => p.subsequent.map(s => s.micros));
          const thWarmMedian = summarize(thWarmSamples).median;
          const thAllocSamples = thProcesses.flatMap(p => p.subsequent.map(s => s.allocatedBytes).filter(b => b != null));
          const thAllocMedian = thAllocSamples.length ? summarize(thAllocSamples).median : null;
          const thMemStr = thAllocMedian != null ? `${(thAllocMedian / 1024 / 1024).toFixed(2)} MiB` : "n/a";
          const thFirstStr = `${(thFirstMedian / 1000).toFixed(3)} ms`;
          const thWarmStr = `${(thWarmMedian / 1000).toFixed(3)} ms`;
          console.log(
            `${"".padEnd(25)} ${"Thymeleaf".padEnd(16)} ${thFirstStr.padStart(22)} ${thWarmStr.padStart(20)} ${thMemStr.padStart(18)}`
          );
        }

        console.log("-".repeat(124));
      }
    }
  } catch (error) {
    if (!results) {
      console.error("Failed to read latest SSR results for comparison table:", error.message);
    }
  }
}

if (process.argv[2] === "--worker") {
  console.log(JSON.stringify(await worker(process.argv[3])));
} else if (printTableOnly) {
  await printComparisonTable();
} else {
  const record = await createRunRecord("ssr", { repetitions, iterations, trials, warmups, nodeStackKiB: 8192, nodeEnv: process.env.NODE_ENV, clock: "performance.now" });
  record.metadata.executionOrder = [];
  console.log("=".repeat(124));
  console.log("REACT 19 SSR BENCHMARK");
  console.log("=".repeat(124));
  console.log(`Settings: ${repetitions} Node processes; each: renderer initialization + first render, ${warmups} warmups, ${trials} trials of ${iterations} renders | Stack 8m`);
  console.log(`Scenarios: ${scenarios.join(", ")}`);
  console.log();

  const raw = Object.fromEntries(scenarios.map(scenario => [scenario, []]));
  for (let repetition = 0; repetition < repetitions; repetition++) {
    for (const scenario of rotated(scenarios, repetition)) {
      record.metadata.executionOrder.push({ repetition, scenario });
      process.stdout.write(`  React SSR process ${repetition + 1}/${repetitions} (${scenario})... `);
      const result = execFileSync(process.execPath, ["--stack-size=8192", fileURLToPath(import.meta.url), "--worker", scenario], {
        encoding: "utf8", env: { ...process.env, NODE_ENV: "production" }, maxBuffer: 10 * 1024 * 1024,
      });
      const parsed = JSON.parse(result);
      const warmMedian = summarize(parsed.samples).median;
      console.log(`Init + first: ${parsed.initializationAndFirstMs.toFixed(3)} ms | Warm: ${warmMedian.toFixed(3)} ms`);
      raw[scenario].push({ repetition, ...parsed });
    }
  }
  const results = Object.fromEntries(Object.entries(raw).map(([scenario, processes]) => [scenario, {
    initializationAndFirst: summarize(processes.map(p => p.initializationAndFirstMs)),
    postWarmup: summarize(processes.flatMap(p => p.samples)),
    perProcess: processes.map(p => ({ repetition: p.repetition, postWarmup: summarize(p.samples) })),
    outputBytes: processes[0].outputBytes, rawProcesses: processes,
  }]));
  const rows = Object.entries(results).map(([scenario, result]) => `| ${scenario} | ${result.initializationAndFirst.median.toFixed(3)} ms | ${result.postWarmup.median.toFixed(3)} ms |`);

  console.log("\n" + "=".repeat(124));
  console.log("REACT 19 SSR RESULTS");
  console.log("=".repeat(124));
  console.log(
    `${"Scenario".padEnd(28)} ${"Init + first median".padStart(24)} ${"Subsequent median".padStart(24)}`
  );
  console.log("-".repeat(124));
  for (const [scenario, result] of Object.entries(results)) {
    const firstStr = `${result.initializationAndFirst.median.toFixed(3)} ms`;
    const warmStr = `${result.postWarmup.median.toFixed(3)} ms`;
    console.log(
      `${scenario.padEnd(28)} ${firstStr.padStart(24)} ${warmStr.padStart(24)}`
    );
  }
  console.log("-".repeat(124));

  await writeFile(resolve(record.directory, "ssr-results.json"), JSON.stringify(results, null, 2) + "\n");
  await writeFile(resolve(record.directory, "ssr-results.md"), `# React SSR results\n\nProduction React, fixed 8 MiB Node stack and the monotonic \`performance.now()\` clock. Each workload starts in a fresh process for each of ${repetitions} repetitions. Renderer initialization + first render starts before that workload's isolated production server-render module is loaded and ends when its first HTML string is complete; unrelated workload modules, process startup and fixture initialization are excluded. Each process performs ${warmups} warmups and ${trials} trials of ${iterations} renders; subsequent values are trial means. First output must match Compose's DOM, attributes and text; later output must remain identical.\n\n| Scenario | Renderer initialization + first render median | Subsequent trial median |\n| :--- | ---: | ---: |\n${rows.join("\n")}\n\nBoth React and Compose construct their element and composable structures dynamically per render. The primary SSR performance gap is architectural: Compose HTML executes a full composition lifecycle (Recomposer, ControlledComposition, slot tables, snapshot state), materializes an intermediate StringHtmlElementNode tree in memory, and serializes it in a second pass, whereas React's renderToString streams escaped markup directly into a buffer in a single pass.\n\nRaw trials and per-process summaries are retained in JSON. p95 is suppressed below 20 observations.\n`);
  await record.complete();

  if (!deferTable) {
    await printComparisonTable(results, scenarios);
  }
}
