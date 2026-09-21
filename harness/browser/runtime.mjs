// Loads benchmark environment defaults and enforces the Node settings required by SSR workers.
import { spawnSync } from "node:child_process";
import { existsSync } from "node:fs";
import { resolve } from "node:path";
import { fileURLToPath } from "node:url";

const rootDirectory = fileURLToPath(new URL("../../", import.meta.url));

export function loadBenchmarkEnv() {
  if (typeof process.loadEnvFile !== "function") return;
  const explicit = process.env.BENCHMARK_ENV_FILE;
  if (explicit && existsSync(explicit)) {
    try { process.loadEnvFile(explicit); return; } catch {}
  }
  const candidates = [
    resolve(rootDirectory, "tools/benchmarks.env"),
    resolve(rootDirectory, "benchmarks.env"),
  ];
  for (const candidate of candidates) {
    if (existsSync(candidate)) {
      try { process.loadEnvFile(candidate); return; } catch {}
    }
  }
}

// React's synchronous deep-tree fallback loses wrappers with Node's default stack.
// Apply the same fixed stack in exports, verification, and every SSR worker.
export function ensureSsrRuntime() {
  loadBenchmarkEnv();
  if (process.env.NODE_ENV === "production" && process.execArgv.includes("--stack-size=8192")) return;
  const result = spawnSync(process.execPath, ["--stack-size=8192", ...process.argv.slice(1)], {
    stdio: "inherit", env: { ...process.env, NODE_ENV: "production" },
  });
  if (result.error) throw result.error;
  process.exit(result.status ?? 1);
}
