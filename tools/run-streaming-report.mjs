import { execFileSync } from "node:child_process";
import { resolve } from "node:path";
import { createRunRecord, rootDirectory } from "../harness/browser/run-record.mjs";
import { writeStreamingReport } from "../harness/browser/streaming-report.mjs";
import { positiveInteger } from "../harness/browser/metrics.mjs";
import { loadBenchmarkEnv, reactRuntimeMode } from "../harness/browser/runtime.mjs";
import { streamingMilestones } from "../harness/browser/streaming-milestones.mjs";

loadBenchmarkEnv();

const record = await createRunRecord("streaming", {
  gradleArguments: process.argv.slice(2),
  composeOutput: process.env.BENCHMARK_STREAM_OUTPUT ?? "string",
  chunkSize: positiveInteger(process.env.BENCHMARK_STREAM_CHUNK_SIZE ?? 4096, "BENCHMARK_STREAM_CHUNK_SIZE"),
  contentMilestones: streamingMilestones,
});
const env = { ...process.env, NODE_ENV: reactRuntimeMode(), BENCHMARK_RESULTS_DIRECTORY: record.directory };
execFileSync(resolve(rootDirectory, "gradlew"), [":harness:jvm:reportSsrStreaming", ...process.argv.slice(2)], {
  cwd: rootDirectory, stdio: "inherit", env,
});
execFileSync(process.execPath, ["--stack-size=8192", resolve(rootDirectory, "harness/browser/streaming-ssr.mjs")], {
  cwd: rootDirectory, stdio: "inherit", env,
});
await writeStreamingReport(record.directory);
await record.complete();
