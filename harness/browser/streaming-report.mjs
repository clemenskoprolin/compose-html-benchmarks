import { jvmSsrTargets } from "../../tools/benchmark-targets.mjs";
import assert from "node:assert/strict";
import { readFile, writeFile } from "node:fs/promises";
import { resolve } from "node:path";
import { summarize } from "./metrics.mjs";
import { consoleTable, markdownTable } from "./report-table.mjs";
import { streamingMilestones } from "./streaming-milestones.mjs";
import { scenarioSummary } from "./benchmark-summary.mjs";
import { streamingMetrics, streamingSummaryRecords } from "./summary-data.mjs";

const targets = [
  { ...jvmSsrTargets.find(target => target.key === "compose"), unit: "Micros", divisor: 1000 },
  { key: "react", label: "React 19", unit: "Ms", divisor: 1 },
  ...jvmSsrTargets.filter(target => target.key !== "compose")
    .map(target => ({ ...target, unit: "Micros", divisor: 1000 })),
];

const timingFields = ["firstChunk", "firstContent", "first8KiB", "completion"];
const visibleMetrics = deliverySink => deliverySink === "buffered-utf8"
  ? streamingMetrics.filter(metric => !["firstChunk", "firstContent", "chunks"].includes(metric.key))
  : streamingMetrics;
const tableMetrics = metrics => metrics.filter(metric => metric.key !== "allocation");

function reportColumns(metrics) {
  return [
    { label: "Scenario", width: 25 }, { label: "Target", width: 16 },
    ...tableMetrics(metrics).map(metric => ({ label: metric.label, width: metric.key === "chunks" ? 8 : 14,
      align: "right", decimals: metric.decimals ?? 3, suffix: metric.unit ? ` ${metric.unit}` : "" })),
    { label: "Output bytes", width: 14, align: "right", decimals: 0 },
    { label: "Allocated KiB", width: 15, align: "right", decimals: 1 },
  ];
}

const metricValue = (metric, result) => metric.key === "chunks"
  ? result.chunks.median : result[metric.key + "Ms"].median;

export function streamingComparison(jvm, react, metrics = visibleMetrics((jvm?.settings ?? react?.settings)?.deliverySink)) {
  const results = {};
  const groups = [];
  for (const scenario of Object.keys(jvm?.results ?? react.results)) {
    const byTarget = jvm?.results[scenario] ?? {};
    results[scenario] = {};
    const rows = [];
    for (const target of targets) {
      if (target.key === "react" ? !react?.results[scenario] : !jvm) continue;
      if (target.key !== "react" && !jvm.targets.includes(target.key)) continue;
      const label = target.key === "react" && react.settings?.reactMode === "development"
        ? "React 19 (dev)" : target.label;
      const runs = target.key === "react" ? react.results[scenario] : byTarget[target.key];
      const skipped = jvm?.skipped?.[`${scenario}/${target.key}`];
      if (!runs?.length) {
        assert(skipped, `Missing streaming results: ${scenario}/${target.key}`);
        results[scenario][target.key] = { skipped };
        rows.push([scenario, label, ...tableMetrics(metrics).map(metric => metric.key === "chunks" ? null : "SKIPPED"), null, null]);
        continue;
      }
      const samples = runs.flatMap(run => run.samples);
      assert(samples.length, `No streaming samples: ${scenario}/${target.key}`);
      assert(runs.every(run => run.contentMilestone === streamingMilestones[scenario].description),
        `Content milestone differs: ${scenario}/${target.key}`);
      const timings = Object.fromEntries(timingFields.map(field => [field + "Ms",
        summarize(samples.map(sample => sample[field + target.unit] == null
          ? null : sample[field + target.unit] / target.divisor))]));
      const outputBytes = runs[0].outputBytes;
      assert(runs.every(run => run.outputBytes === outputBytes), `Output size differs: ${scenario}/${target.key}`);
      assert(samples.every(sample => timingFields.every(field => {
        const value = sample[field + target.unit];
        return field === "first8KiB" && outputBytes < 8192 ? value === null : Number.isFinite(value);
      })), `Missing streaming milestones: ${scenario}/${target.key}`);
      const result = { ...timings, chunks: summarize(samples.map(sample => sample.chunks)),
        allocatedBytes: summarize(samples.map(sample => sample.allocatedBytes)), outputBytes,
        contentBytes: runs[0].contentBytes, contentMilestone: runs[0].contentMilestone };
      results[scenario][target.key] = result;
      rows.push([scenario, label, ...tableMetrics(metrics).map(metric => metricValue(metric, result)),
        outputBytes, result.allocatedBytes.median == null ? null : result.allocatedBytes.median / 1024]);
    }
    groups.push(rows);
  }
  return { results, groups };
}

export async function writeStreamingReport(directory, { includeJvm = true, includeReact = true } = {}) {
  assert(includeJvm || includeReact, "No streaming targets selected");
  const [jvm, react] = await Promise.all([["jvm", includeJvm], ["react", includeReact]].map(async ([target, included]) =>
    included ? JSON.parse(await readFile(resolve(directory, `${target}-streaming-results.json`), "utf8")) : null));
  if (jvm && react) {
    for (const key of ["repetitions", "warmups", "trials", "iterations", "deliverySink", "bufferBytes"]) {
      assert.equal(jvm.settings[key], react.settings[key], `Streaming settings differ: ${key}`);
    }
    assert(Object.keys(react.results).every(scenario => scenario in jvm.results),
      "React streaming scenarios must be included in JVM results");
  }
  const settings = jvm?.settings ?? react.settings;
  const buffered = settings.deliverySink === "buffered-utf8";
  assert(buffered || settings.deliverySink === "renderer", "Unknown streaming delivery policy");
  const metrics = visibleMetrics(settings.deliverySink);
  const { results, groups } = streamingComparison(jvm, react, metrics);
  const columns = reportColumns(metrics);
  const sinkDescription = buffered
    ? `All renderers deliver through the same ${settings.bufferBytes}-byte UTF-8 buffer. Full batches are delivered immediately; the final partial batch is delivered at completion. First chunk, first content and chunk count are omitted because delivery is determined by the buffer.${jvm?.settings.jvmCharacterBufferChars ? ` JVM output batches small writes through a ${jvm.settings.jvmCharacterBufferChars}-character buffer before the UTF-8 encoder and byte delivery buffer; these buffers are reused across renders.` : ""}`
    : `Renderer mode observes each renderer's writes without a shared delivery buffer. Character sinks count UTF-8 bytes without encoding; byte sinks observe encoded output. First chunk and chunk count describe renderer writes; first content is the write completing the useful element. These timings include counting overhead.`;
  const composeDescription = jvm?.targets.includes("compose")
    ? `Compose's chunk target is ${settings.chunkSize} UTF-16 code units, using ${settings.composeOutput === "writer" ? "the JVM Writer overload" : "a String callback"}.` : "";
  const validationDescription = [
    jvm ? "JVM output is checked byte-for-byte against string rendering. Thread allocation counts include per-render observing work. fractional-style is Compose-specific." : "",
    react ? "React output is checked for DOM equivalence against prepared Compose HTML before and after trials. React allocations are not measured." : "",
  ].filter(Boolean).join(" ");
  const description = `Each workload runs in ${settings.repetitions} fresh processes per renderer, with ${settings.warmups} warmups and ${settings.trials} trials of ${settings.iterations} renders. Values are medians of per-render trial means; raw JSON retains first-render readings.

${sinkDescription} First 8 KiB is the delivery reaching 8,192 cumulative UTF-8 bytes (n/a for smaller output). Complete includes finishing rendering and draining buffers. The first validated HTML locates the content byte offset outside timing. Output bytes are shown per target because equivalent text can use different escaping.

${composeDescription} Delivery is synchronous, with no network backpressure or browser paint measurement. ${validationDescription} Results from different delivery policies must not be compared.
`;
  const milestoneTable = markdownTable([{ label: "Scenario" }, { label: "First content milestone" }],
    Object.keys(results).map(scenario => [scenario, streamingMilestones[scenario].description]));
  const skips = Object.entries(jvm?.skipped ?? {}).filter(([key]) => key.split("/")[0] in results)
    .map(([key, reason]) => `- ${key}: ${reason}.`).join("\n");
  const labels = Object.fromEntries(targets.map(target => [target.key,
    target.key === "react" && react?.settings.reactMode === "development" ? "React 19 (dev)" : target.label]));
  const records = streamingSummaryRecords(results, labels);
  const summary = scenarioSummary(records, metrics);
  const report = (title, rows, summaryRecords) => `# ${title}\n\n${description}\n${markdownTable(columns, rows)}\n` +
    scenarioSummary(summaryRecords, metrics).markdown + `\n` +
    `## Content milestones\n\n${milestoneTable}\n${skips ? `\n## Skipped\n\n${skips}\n` : ""}`;
  const rows = groups.flat();
  const isReact = row => row[1].startsWith("React 19");
  // All three reports use the same summaries, columns, units and formatting.
  await writeFile(resolve(directory, "streaming-results.json"), JSON.stringify({
    settings: { ...settings, ...(react ? { reactMode: react.settings.reactMode } : {}) }, targetVersions: jvm?.targetVersions, results,
    rawResults: { ...(jvm ? { jvm: "jvm-streaming-results.json" } : {}), ...(react ? { react: "react-streaming-results.json" } : {}) },
  }, null, 2) + "\n");
  await writeFile(resolve(directory, "streaming-results.md"), report("Streaming SSR comparison", rows, records));
  if (jvm) await writeFile(resolve(directory, "jvm-streaming-results.md"), report("JVM streaming SSR",
    rows.filter(row => !isReact(row)), records.filter(record => record.target !== labels.react)));
  if (react) await writeFile(resolve(directory, "react-streaming-results.md"), report("React streaming SSR",
    rows.filter(isReact), records.filter(record => record.target === labels.react)));
  const activeLabels = [...new Set(rows.map(row => row[1]))];
  console.log("\n" + consoleTable({ title: `STREAMING SSR — ${activeLabels.join(" vs ")}`,
    columns, groups, width: columns.reduce((sum, column) => sum + column.width, columns.length - 1) }));
  if (skips) console.log(skips);
  console.log(summary.console);
}
