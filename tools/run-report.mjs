// Archive JVM-only reports and combined JVM/React streaming reports.
import { execFileSync } from "node:child_process";
import { resolve } from "node:path";
import { createRunRecord, rootDirectory } from "../harness/browser/run-record.mjs";
import { writeStreamingReport } from "../harness/browser/streaming-report.mjs";
import { benchmarkCount, loadBenchmarkEnv, reactRuntimeMode } from "../harness/browser/runtime.mjs";
import { streamingMilestones } from "../harness/browser/streaming-milestones.mjs";

const kind = process.argv[2];
if (!["jvm", "streaming"].includes(kind)) throw new Error("Usage: node tools/run-report.mjs jvm|streaming [GRADLE_ARGS]");
const streaming = kind === "streaming";
if (streaming) loadBenchmarkEnv();
const gradleArguments = process.argv.slice(3);
const settings = streaming ? {
  composeOutput: process.env.BENCHMARK_STREAM_OUTPUT ?? "string",
  chunkSize: benchmarkCount("BENCHMARK_STREAM_CHUNK_SIZE", 2048),
  contentMilestones: streamingMilestones,
} : {
  composeValidation: process.env.BENCHMARK_COMPOSE_VALIDATION ??
    (["true", "1"].includes(process.env.COMPOSE_HTML_VALIDATE_STRICTLY?.trim().toLowerCase()) ? "strict" : "fast"),
};
const record = await createRunRecord(kind, { gradleArguments, ...settings });
const env = { ...process.env, BENCHMARK_RESULTS_DIRECTORY: record.directory,
  ...(streaming ? { NODE_ENV: reactRuntimeMode() } : { BENCHMARK_NODE: process.execPath }) };
execFileSync(resolve(rootDirectory, "gradlew"), [
  streaming ? ":harness:jvm:reportSsrStreaming" : ":harness:jvm:reportSsrColdWarm", ...gradleArguments,
], { cwd: rootDirectory, stdio: "inherit", env });
if (streaming) {
  execFileSync(process.execPath, ["--stack-size=8192", resolve(rootDirectory, "harness/browser/streaming-ssr.mjs")],
    { cwd: rootDirectory, stdio: "inherit", env });
  await writeStreamingReport(record.directory);
}
await record.complete();
