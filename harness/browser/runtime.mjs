// Shared workload groups, environment defaults, numeric settings and fresh Node workers.
import { execFileSync, spawnSync } from "node:child_process";
import { existsSync } from "node:fs";
import { readFile, unlink } from "node:fs/promises";
import { positiveInteger, rotated } from "./metrics.mjs";
import { resolve } from "node:path";
import { rootDirectory } from "./run-record.mjs";
export { rootDirectory } from "./run-record.mjs";

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

export function reactRuntimeMode() {
  const mode = process.env.BENCHMARK_REACT_MODE || "production";
  if (mode !== "production" && mode !== "development") {
    throw new Error(`BENCHMARK_REACT_MODE must be "production" or "development", got: ${mode}`);
  }
  return mode;
}

// React's synchronous deep-tree fallback loses wrappers with Node's default stack.
// Apply the same fixed stack in exports, verification, and every SSR worker.
export function ensureSsrRuntime() {
  loadBenchmarkEnv();
  const mode = reactRuntimeMode();
  if (process.env.NODE_ENV === mode && process.execArgv.includes("--stack-size=8192")) return;
  const result = spawnSync(process.execPath, ["--stack-size=8192", ...process.argv.slice(1)], {
    stdio: "inherit", env: { ...process.env, NODE_ENV: mode },
  });
  if (result.error) throw result.error;
  process.exit(result.status ?? 1);
}

export const documentScenarios = [
  "tailwind-catalog", "form-app", "data-table", "svg-dashboard", "content-article",
];
export const browserScenarios = [...documentScenarios, "hydrate1k", "update10th1k", "reorder1k", "filter-list"];
export const ssrScenarios = [...documentScenarios, "preact-text", "preact-search-results", "preact-stack"];
export const jvmScenarios = [...ssrScenarios, "raw-text-style", "raw-text-elements", "style-heavy-catalog"];

export function benchmarkCount(name, fallback, minimum = 1) {
  return positiveInteger(process.env[name] ?? fallback, name, minimum);
}

export function ssrSettings({ warmups = process.env.BENCHMARK_WARMUPS ?? 3 } = {}) {
  return {
    iterations: benchmarkCount("BENCHMARK_ITERATIONS", 100),
    trials: benchmarkCount("BENCHMARK_TRIALS", 15),
    warmups: positiveInteger(warmups, "BENCHMARK_WARMUPS", 0),
    repetitions: benchmarkCount("BENCHMARK_REPETITIONS", 3),
  };
}

// String SSR historically falls back to all workloads if none of the requested names match.
export function selectStringSsrScenarios(available) {
  const requested = process.env.BENCHMARK_SCENARIOS?.split(",").map(name => name.trim()).filter(Boolean);
  const matched = available.filter(name => requested?.includes(name));
  return matched.length ? matched : available;
}

// Repetition-major order is shared by string SSR runners. Streaming has its own order.
export function collectSsrProcesses({ scenarios, repetitions, executionOrder }, runWorker) {
  const results = Object.fromEntries(scenarios.map(scenario => [scenario, []]));
  for (let repetition = 0; repetition < repetitions; repetition++) {
    for (const scenario of rotated(scenarios, repetition)) {
      executionOrder.push({ repetition, scenario });
      results[scenario].push({ repetition, ...runWorker(scenario, repetition) });
    }
  }
  return results;
}

// Worker execution and cleanup happen in the parent, outside measurement code.
export function runNodeWorker(module, args, options = {}) {
  return JSON.parse(execFileSync(process.execPath, ["--stack-size=8192", module, ...args], {
    encoding: "utf8", maxBuffer: 10 * 1024 * 1024, ...options,
  }));
}

export async function runNodeFileWorker(module, args, output, options = {}) {
  execFileSync(process.execPath, ["--stack-size=8192", module, ...args, output], {
    stdio: "inherit", ...options,
  });
  const result = JSON.parse(await readFile(output));
  await unlink(output);
  return result;
}
