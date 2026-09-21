// Unit tests for loading local benchmark environment settings without overwriting caller values.
import assert from "node:assert/strict";
import test from "node:test";
import { writeFileSync, unlinkSync } from "node:fs";
import { resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { loadBenchmarkEnv } from "./runtime.mjs";

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
