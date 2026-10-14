// String SSR presentation shared by React reports and JVM summary generation.
import { readFile, writeFile } from "node:fs/promises";
import { resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { rootDirectory, reactRuntimeMode } from "./runtime.mjs";
import { consoleTable } from "./report-table.mjs";
import { scenarioSummary } from "./benchmark-summary.mjs";
import { jvmSummaryRecords, ssrMetrics, ssrSummaryRecords } from "./summary-data.mjs";
import { selectBenchmarkTargets, templateTargets } from "../../tools/benchmark-targets.mjs";

const comparisonColumns = [
  { label: "Scenario", width: 25 }, { label: "Target", width: 16 },
  { label: "Init + first median", width: 22, align: "right" },
  { label: "Subsequent median", width: 20, align: "right" },
  { label: "Allocated heap", width: 18, align: "right" },
];
const reactColumns = [
  { label: "Scenario", width: 28 },
  { label: "Init + first median", width: 24, align: "right" },
  { label: "Subsequent median", width: 24, align: "right" },
  { label: "Sampled heap", width: 18, align: "right" },
];

export async function printComparisonTable(results = null, customScenarios = null) {
  if (!selectBenchmarkTargets("ssr").jvm.length) return;
  const reactLabel = reactRuntimeMode() === "development" ? "React 19 (dev)" : "React 19";
  try {
    const jvmResultsPath = resolve(rootDirectory, "results/runs/latest/jvm-results.json");
    const jvmData = JSON.parse(await readFile(jvmResultsPath, "utf8"));
    const templateLabels = templateTargets.map(target => target.label);
    if (!results) {
      const ssrResultsPath = resolve(rootDirectory, "results/runs/latest/ssr-results.json");
      results = JSON.parse(await readFile(ssrResultsPath, "utf8"));
    }
    const targetScenarios = customScenarios || Object.keys(results);
    const jvmRecords = jvmSummaryRecords(jvmData);
    const commonScenarios = targetScenarios.filter(s => jvmRecords.some(record => record.scenario === s));
    if (commonScenarios.length > 0) {
      const presentTemplateLabels = templateLabels.filter(target => jvmRecords.some(record => record.target === target));
      const jvmRow = (scenario, target) => {
        const values = jvmRecords.find(record => record.scenario === scenario && record.target === target)?.values;
        return values && [scenario, target, `${values.first.toFixed(3)} ms`, `${values.warm.toFixed(3)} ms`,
          values.allocation == null ? "n/a" : `${values.allocation.toFixed(2)} MiB`];
      };
      const groups = commonScenarios.map(scenario => {
        const rows = [jvmRow(scenario, "Compose JVM")].filter(Boolean);
        const react = results[scenario];
        if (react) rows.push([scenario, reactLabel, `${react.initializationAndFirst.median.toFixed(3)} ms`,
          `${react.postWarmup.median.toFixed(3)} ms`, react.allocatedHeap?.median == null ? "—"
            : `${(react.allocatedHeap.median / 1024 / 1024).toFixed(2)} MiB*`]);
        for (const target of presentTemplateLabels) {
          const row = jvmRow(scenario, target);
          if (row) rows.push(row);
        }
        return rows;
      });
      console.log("\n" + consoleTable({
        title: "SSR COMPARISON RESULTS — JVM RENDERERS vs " + reactLabel,
        columns: comparisonColumns, groups, width: 124,
      }));
      console.log("* React allocation is a V8 sampling estimate; JVM allocations use a thread counter.");
      const records = [...jvmSummaryRecords(jvmData), ...ssrSummaryRecords(results, reactLabel)]
        .filter(record => commonScenarios.includes(record.scenario));
      const summary = scenarioSummary(records, ssrMetrics);
      console.log(summary.console);
    }
  } catch (error) {
    if (!results) {
      console.error("Failed to read latest SSR results for comparison table:", error.message);
    }
  }
}

export async function writeReactSsrReport(directory, results, {
  reactMode, reactLabel, repetitions, warmups, trials, iterations, allocationSamplingInterval,
}) {
  const rows = Object.entries(results).map(([scenario, result]) => [scenario,
    `${result.initializationAndFirst.median.toFixed(3)} ms`, `${result.postWarmup.median.toFixed(3)} ms`,
    `${(result.allocatedHeap.median / 1024 / 1024).toFixed(2)} MiB`,
  ]);
  const markdownRows = rows.map(row => `| ${row.join(" | ")} |`);
  const overall = scenarioSummary(ssrSummaryRecords(results, reactLabel), ssrMetrics);

  console.log("\n" + consoleTable({ title: "REACT 19 SSR RESULTS", columns: reactColumns,
    groups: [rows], width: 124, blankRepeatedFirstCell: false }));
  console.log(overall.console);
  await writeFile(resolve(directory, "ssr-results.json"), JSON.stringify(results, null, 2) + "\n");
  const modeDescription = reactMode === "development"
    ? "React development diagnostics with esbuild minification enabled"
    : "Production React with esbuild minification enabled";
  await writeFile(resolve(directory, "ssr-results.md"), `# React SSR results\n\n${modeDescription}, fixed 8 MiB Node stack and the monotonic \`performance.now()\` clock. Each workload starts in a fresh process for each of ${repetitions} repetitions. Renderer initialization + first render starts before that workload's isolated ${reactMode} server-render module is loaded and ends when its first HTML string is complete; unrelated workload modules, process startup and fixture initialization are excluded. Each process performs ${warmups} warmups and ${trials} trials of ${iterations} renders; subsequent values are trial means. First output must match Compose's DOM, attributes and text; later output must remain identical.\n\nAfter latency trials, each worker separately samples V8 JS heap allocations with a ${allocationSamplingInterval}-byte average interval. Objects collected by minor and major GC are included. The reported bytes per render divide sampled allocated bytes by the number of profiled renders; this is an estimate, unlike Compose's JVM thread allocation counter. Native allocations and retained heap are not measured. Raw profiles are not retained, but sample counts and profiled render counts are in JSON.\n\n| Scenario | Renderer initialization + first render median | Subsequent trial median | Sampled allocated heap per render |\n| :--- | ---: | ---: | ---: |\n${markdownRows.join("\n")}\n${overall.markdown}\nBoth React and Compose construct their element and composable structures dynamically per render. Compose HTML materializes an intermediate HTML node tree and serializes it in a second pass, whereas React's renderToString writes escaped markup directly into a buffer. Depending on the selected Compose HTML version, repeated renders may reuse the composition and matching HTML nodes; the Compose JVM report records the mode used.\n\nRaw trials and per-process summaries are retained in JSON. p95 is suppressed below 20 observations.\n`);
}

export async function appendJvmSummary(directory) {
  const data = JSON.parse(await readFile(resolve(directory, "jvm-results.json"), "utf8"));
  const records = jvmSummaryRecords(data);
  const comparison = scenarioSummary(records, ssrMetrics);
  const markdownPath = resolve(directory, "jvm-results.md");
  await writeFile(markdownPath, (await readFile(markdownPath, "utf8")) + comparison.markdown);
  console.log(comparison.console);
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  if (!process.argv[2]) throw new Error("Usage: node harness/browser/ssr-report.mjs RESULT_DIRECTORY");
  await appendJvmSummary(process.argv[2]);
}
