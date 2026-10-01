// Called after the JVM report, so JVM and Node reports share summary mathematics and formatting.
import { readFile, writeFile } from "node:fs/promises";
import { resolve } from "node:path";
import { scenarioSummary } from "../harness/browser/benchmark-summary.mjs";
import { jvmSummaryRecords, ssrMetrics } from "../harness/browser/summary-data.mjs";

const directory = process.argv[2];
if (!directory) throw new Error("Usage: node tools/append-jvm-summary.mjs RESULT_DIRECTORY");
const data = JSON.parse(await readFile(resolve(directory, "jvm-results.json"), "utf8"));
const records = jvmSummaryRecords(data);
const comparison = scenarioSummary(records, ssrMetrics);
const compose = scenarioSummary(records.filter(record => record.target === "Compose JVM"), ssrMetrics);
const markdownPath = resolve(directory, "jvm-results.md");
await writeFile(markdownPath, (await readFile(markdownPath, "utf8")) + compose.markdown);
await writeFile(resolve(directory, "jvm-summary.md"), `# JVM SSR overall comparison\n${comparison.markdown}`);
console.log(comparison.console);
