// Unit tests for loading local benchmark environment settings without overwriting caller values.
import assert from "node:assert/strict";
import test from "node:test";
import { writeFileSync, unlinkSync, mkdtempSync, rmSync } from "node:fs";
import { execFileSync } from "node:child_process";
import { tmpdir } from "node:os";
import { resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { loadBenchmarkEnv, ssrSettings, ssrScenarios, selectStringSsrScenarios, collectSsrProcesses } from "./runtime.mjs";

test("string SSR keeps fresh processes, balanced workload order and repetition provenance", () => {
  const executionOrder = [];
  const calls = [];
  const results = collectSsrProcesses({ scenarios: ["a", "b", "c"], repetitions: 3, executionOrder },
    (scenario, repetition) => {
      calls.push(`${repetition}/${scenario}`);
      assert.deepEqual(executionOrder.at(-1), { repetition, scenario });
      return { samples: [repetition + scenario.charCodeAt(0)], outputBytes: 42 };
    });
  assert.deepEqual(calls, ["0/a", "0/b", "0/c", "1/b", "1/c", "1/a", "2/c", "2/a", "2/b"]);
  assert.deepEqual(Object.keys(results), ["a", "b", "c"]);
  assert.deepEqual(results.b, [
    { repetition: 0, samples: [98], outputBytes: 42 },
    { repetition: 1, samples: [99], outputBytes: 42 },
    { repetition: 2, samples: [100], outputBytes: 42 },
  ]);
});

test("loadBenchmarkEnv loads environment file without overwriting existing variables", () => {
  const root = fileURLToPath(new URL("../../", import.meta.url));
  const tempEnv = resolve(root, "tools/test-temp.env");

  writeFileSync(tempEnv, "TEST_BENCHMARK_UNSET_VAR=from_file\nTEST_BENCHMARK_PRESET_VAR=from_file\n");
  process.env.BENCHMARK_ENV_FILE = tempEnv;
  process.env.TEST_BENCHMARK_PRESET_VAR = "pre_set_value";

  try {
    loadBenchmarkEnv();
    assert.equal(process.env.TEST_BENCHMARK_UNSET_VAR, "from_file");
    assert.equal(process.env.TEST_BENCHMARK_PRESET_VAR, "pre_set_value");
  } finally {
    unlinkSync(tempEnv);
    delete process.env.BENCHMARK_ENV_FILE;
    delete process.env.TEST_BENCHMARK_UNSET_VAR;
    delete process.env.TEST_BENCHMARK_PRESET_VAR;
  }
});

test("SSR defaults, zero warmups and scenario selection remain stable", () => {
  const keys = ["BENCHMARK_ITERATIONS", "BENCHMARK_TRIALS", "BENCHMARK_WARMUPS", "BENCHMARK_REPETITIONS", "BENCHMARK_SCENARIOS"];
  const saved = keys.map(key => process.env[key]);
  try {
    keys.forEach(key => delete process.env[key]);
    assert.deepEqual(ssrSettings(), { iterations: 100, trials: 15, warmups: 3, repetitions: 3 });
    process.env.BENCHMARK_WARMUPS = "0";
    assert.equal(ssrSettings().warmups, 0);
    process.env.BENCHMARK_ITERATIONS = "0";
    assert.throws(() => ssrSettings(), /BENCHMARK_ITERATIONS/);
    process.env.BENCHMARK_SCENARIOS = " preact-text, unknown, data-table, preact-text ";
    assert.deepEqual(selectStringSsrScenarios(ssrScenarios), ["data-table", "preact-text"]);
    process.env.BENCHMARK_SCENARIOS = "unknown";
    assert.deepEqual(selectStringSsrScenarios(ssrScenarios), []);
    process.env.BENCHMARK_SCENARIOS = "fractional-style";
    assert.deepEqual(selectStringSsrScenarios(ssrScenarios), []);
    process.env.BENCHMARK_SCENARIOS = " , ";
    assert.deepEqual(selectStringSsrScenarios(ssrScenarios), ssrScenarios);
  } finally {
    keys.forEach((key, index) => saved[index] == null ? delete process.env[key] : process.env[key] = saved[index]);
  }
});

test("shared shell setup preserves exported caller values after loading defaults", () => {
  const root = fileURLToPath(new URL("../../", import.meta.url));
  const directory = mkdtempSync(resolve(tmpdir(), "benchmark-env-"));
  const envFile = resolve(directory, "defaults.env");
  writeFileSync(envFile, "BENCHMARK_WARMUPS=9\nBENCHMARK_TEST_DEFAULT=from_file\n");
  try {
    const output = execFileSync("bash", ["-c",
      'SCRIPT_DIR="$1/tools"; REPO_ROOT="$1"; source "$SCRIPT_DIR/common.sh"; load_benchmark_env; printf "%s,%s" "$BENCHMARK_WARMUPS" "$BENCHMARK_TEST_DEFAULT"',
      "bash", root,
    ], { encoding: "utf8", env: { ...process.env, BENCHMARK_ENV_FILE: envFile, BENCHMARK_WARMUPS: "7" } });
    assert.equal(output, "7,from_file");
  } finally {
    rmSync(directory, { recursive: true, force: true });
  }
});

test("shared target defaults intersect the suite while CLI selections stay strict", async () => {
  const { selectBenchmarkTargets } = await import("../../tools/benchmark-targets.mjs");
  assert.deepEqual(selectBenchmarkTargets("standard", "").jvm, ["compose"]);
  const shared = "react,compose-wasm,compose-js";
  for (const suite of ["ssr", "streaming"]) {
    assert.deepEqual(selectBenchmarkTargets(suite, shared).jvm, []);
    assert.equal(selectBenchmarkTargets(suite, shared).react, true);
    assert.throws(() => selectBenchmarkTargets(suite, shared, { strict: true }), /not supported/);
  }
  assert.deepEqual(selectBenchmarkTargets("wasm", shared).browser, ["compose-wasm"]);
  assert.throws(() => selectBenchmarkTargets("jvm", shared), /No selected targets/);
  assert.deepEqual(selectBenchmarkTargets("jvm", "compose").jvm, ["compose"]);
  assert.throws(() => selectBenchmarkTargets("jvm", "compose-jvm", { strict: true }), /not supported/);
});

test("shell accepts shared environment targets and rejects unsupported CLI targets", () => {
  const script = fileURLToPath(new URL("../../tools/run-benchmarks.sh", import.meta.url));
  const env = { ...process.env, BENCHMARK_TARGETS: "react,compose-wasm,compose-js" };
  const options = { env, encoding: "utf8", stdio: "pipe" };
  // Both errors occur before builds or measurements; the first proves target selection succeeded.
  assert.throws(() => execFileSync("bash", [script, "--ssr", "--scenarios=raw-text-style"], options), /JVM-only/);
  assert.throws(() => execFileSync("bash", [script, "--ssr", "--targets=compose-js"], options), /not supported by --ssr/);
});
