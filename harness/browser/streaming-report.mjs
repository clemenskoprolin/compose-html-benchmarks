import assert from "node:assert/strict";
import { readFile, writeFile } from "node:fs/promises";
import { resolve } from "node:path";
import { summarize } from "./metrics.mjs";
import { consoleTable, markdownTable } from "./report-table.mjs";
import { streamingMilestones } from "./streaming-milestones.mjs";
import { scenarioSummary } from "./benchmark-summary.mjs";
import { streamingMetrics, streamingSummaryRecords } from "./summary-data.mjs";

const targets = [
  { key: "compose", label: "Compose JVM", unit: "Micros", divisor: 1000 },
  { key: "react", label: "React 19", unit: "Ms", divisor: 1 },
  { key: "thymeleaf", label: "Thymeleaf", unit: "Micros", divisor: 1000 },
];
const columns = [
  { label: "Scenario", width: 25 }, { label: "Target", width: 16 },
  ...["First content", "First 8 KiB", "Complete"].map(label => ({ label, width: 14, align: "right", decimals: 3, suffix: " ms" })),
  { label: "Chunks", width: 8, align: "right", decimals: 0 },
  { label: "Allocated KiB", width: 15, align: "right", decimals: 1 },
];
const timingFields = ["firstChunk", "firstContent", "first8KiB", "completion"];
const displayedTimings = timingFields.slice(1);

export function streamingComparison(jvm, react) {
  const results = {};
  const groups = [];
  for (const [scenario, byTarget] of Object.entries(jvm.results)) {
    results[scenario] = {};
    const rows = [];
    for (const target of targets) {
      const label = target.key === "react" && react.settings?.reactMode === "development"
        ? "React 19 (dev)" : target.label;
      const runs = target.key === "react" ? react.results[scenario] : byTarget[target.key];
      const skipped = jvm.skipped?.[`${scenario}/${target.key}`];
      if (!runs?.length) {
        assert(skipped, `Missing streaming results: ${scenario}/${target.key}`);
        results[scenario][target.key] = { skipped };
        rows.push([scenario, label, ...displayedTimings.map(() => "SKIPPED"), null, null]);
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
      rows.push([scenario, label, ...displayedTimings.map(field => result[field + "Ms"].median),
        result.chunks.median, result.allocatedBytes.median == null ? null : result.allocatedBytes.median / 1024]);
    }
    groups.push(rows);
  }
  return { results, groups };
}

export async function writeStreamingReport(directory) {
  const [jvm, react] = await Promise.all(["jvm", "react"].map(async target =>
    JSON.parse(await readFile(resolve(directory, `${target}-streaming-results.json`), "utf8"))));
  for (const key of ["repetitions", "warmups", "trials", "iterations"]) {
    assert.equal(jvm.settings[key], react.settings[key], `Streaming settings differ: ${key}`);
  }
  assert.deepEqual(Object.keys(jvm.results), Object.keys(react.results), "Streaming scenario sets differ");
  const { results, groups } = streamingComparison(jvm, react);
  const description = `Each workload runs in ${jvm.settings.repetitions} fresh processes per renderer, with ` +
    `${jvm.settings.warmups} warmups and ${jvm.settings.trials} trials of ${jvm.settings.iterations} renders. ` +
    `Values are medians of per-render trial means after warmup; first-render readings are retained in the raw JSON.

All milestones are observed at the sink, including renderer buffering. ` +
    `First content is the write that completes the workload's useful element. First 8 KiB is the write that brings ` +
    `cumulative UTF-8 output to 8,192 bytes (n/a for smaller output). Complete ends when JVM rendering returns or ` +
    `React's writable finishes. The first validated HTML locates the content byte offset outside timing; timed sinks ` +
    `count bytes across writes without parsing HTML. Timings include the sink's counting overhead.

Compose's chunk target is ${jvm.settings.chunkSize} UTF-16 code units. React and Thymeleaf control their own ` +
    `write sizes. Compose uses ${jvm.settings.composeOutput === "writer" ? "the JVM Writer overload" : "a String callback"}. Sinks accept output immediately; these measure HTML delivery without network backpressure. ` +
    `They do not measure browser paint. JVM output is checked against its string renderer byte-for-byte; React ` +
    `is checked by DOM equivalence against prepared Compose HTML, before and after trials. Allocations use a JVM ` +
    `thread counter and include the observing sink; React allocations are not measured here.
`;
  const milestoneTable = markdownTable([{ label: "Scenario" }, { label: "First content milestone" }],
    Object.keys(results).map(scenario => [scenario, streamingMilestones[scenario].description]));
  const skips = Object.entries(jvm.skipped ?? {}).filter(([key]) => key.split("/")[0] in results)
    .map(([key, reason]) => `- ${key}: ${reason}.`).join("\n");
  const labels = Object.fromEntries(targets.map(target => [target.key,
    target.key === "react" && react.settings.reactMode === "development" ? "React 19 (dev)" : target.label]));
  const records = streamingSummaryRecords(results, labels);
  const summary = scenarioSummary(records, streamingMetrics);
  const report = (title, rows, summaryRecords) => `# ${title}\n\n${description}\n${markdownTable(columns, rows)}\n` +
    scenarioSummary(summaryRecords, streamingMetrics).markdown + `\n` +
    `## Content milestones\n\n${milestoneTable}\n${skips ? `\n## Skipped\n\n${skips}\n` : ""}`;
  const rows = groups.flat();
  const isReact = row => row[1].startsWith("React 19");
  // All three reports use the same summaries, columns, units and formatting.
  await writeFile(resolve(directory, "streaming-results.json"), JSON.stringify({
    settings: { ...jvm.settings, reactMode: react.settings.reactMode }, results,
    rawResults: { jvm: "jvm-streaming-results.json", react: "react-streaming-results.json" },
  }, null, 2) + "\n");
  await writeFile(resolve(directory, "streaming-results.md"), report("Streaming SSR comparison", rows, records));
  await writeFile(resolve(directory, "jvm-streaming-results.md"), report("JVM streaming SSR",
    rows.filter(row => !isReact(row)), records.filter(record => record.target !== labels.react)));
  await writeFile(resolve(directory, "react-streaming-results.md"), report("React streaming SSR",
    rows.filter(isReact), records.filter(record => record.target === labels.react)));
  console.log("\n" + consoleTable({ title: "STREAMING SSR COMPARISON — COMPOSE JVM vs REACT 19 vs THYMELEAF",
    columns, groups, width: columns.reduce((sum, column) => sum + column.width, columns.length - 1) }));
  if (skips) console.log(skips);
  console.log(summary.console);
}
