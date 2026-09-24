import { execFileSync } from "node:child_process";
import { resolve } from "node:path";
import { createRunRecord, rootDirectory } from "../harness/browser/run-record.mjs";
const record = await createRunRecord("jvm", {
  gradleArguments: process.argv.slice(2),
  composeValidation: process.env.BENCHMARK_COMPOSE_VALIDATION ??
    (["true", "1"].includes(process.env.COMPOSE_HTML_VALIDATE_STRICTLY?.trim().toLowerCase()) ? "strict" : "fast"),
});
execFileSync(resolve(rootDirectory, "gradlew"), [":harness:jvm:reportSsrColdWarm", ...process.argv.slice(2)], {
  cwd: rootDirectory, stdio: "inherit", env: { ...process.env, BENCHMARK_RESULTS_DIRECTORY: record.directory },
});
await record.complete();
