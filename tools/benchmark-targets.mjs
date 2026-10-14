// Select only runners supported by the requested suite and target list.
import { fileURLToPath } from "node:url";

export const jvmSsrTargets = [
  { key: "compose", label: "Compose JVM", resultKey: "rawProcesses" },
  { key: "thymeleaf", label: "Thymeleaf", resultKey: "thymeleafProcesses" },
  { key: "jstachio", label: "JStachio", resultKey: "jstachioProcesses" },
  { key: "jte", label: "jte", resultKey: "jteProcesses" },
];
export const templateTargets = jvmSsrTargets.filter(target => target.key !== "compose");
const jvmTargets = jvmSsrTargets.map(target => target.key);
const browserTargets = ["react", "compose-wasm", "compose-js",
  "compose-wasm-table-unchecked", "compose-js-table-unchecked"];
const suiteTargets = {
  standard: [...jvmTargets, ...browserTargets],
  ssr: [...jvmTargets, "react"],
  streaming: [...jvmTargets, "react"],
  jvm: jvmTargets,
  hydration: browserTargets,
  wasm: ["compose-wasm"],
};
const defaults = {
  standard: ["compose", "react", "compose-wasm", "compose-js"],
  hydration: ["react", "compose-wasm", "compose-js"],
};

export function selectBenchmarkTargets(action, value = process.env.BENCHMARK_TARGETS, { strict = false } = {}) {
  const available = suiteTargets[action];
  if (!available) throw new Error(`Unknown benchmark suite: ${action}`);
  const requested = value?.split(",").map(target => target.trim()).filter(Boolean);
  const candidates = [...new Set(requested?.length ? requested : defaults[action] ?? available)];
  for (const target of candidates) {
    if (strict && !available.includes(target)) {
      throw new Error(`Target '${target}' is not supported by --${action}. Available: ${available.join(", ")}`);
    }
  }
  const selected = candidates.filter(target => available.includes(target));
  if (!selected.length) throw new Error(`No selected targets are supported by --${action}. Available: ${available.join(", ")}`);
  return {
    jvm: selected.filter(target => jvmTargets.includes(target)),
    react: selected.includes("react"),
    browser: selected.filter(target => browserTargets.includes(target)),
  };
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  try {
    const { jvm, react, browser } = selectBenchmarkTargets(process.argv[2], process.env.BENCHMARK_TARGETS, { strict: process.argv[3] === "--strict" });
    const selected = [...new Set([...jvm, ...browser])];
    console.log(`${jvm.join(",")}|${react}|${browser.join(",")}|${selected.join(",")}`);
  } catch (error) {
    console.error(error.message);
    process.exitCode = 1;
  }
}
