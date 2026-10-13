// Adapt each runner's raw schema to the same summary records and units.
import { jvmSsrTargets } from "../../tools/benchmark-targets.mjs";
import { summarize } from "./metrics.mjs";

export const ssrMetrics = [
  { key: "first", label: "Init + first", unit: "ms" },
  { key: "warm", label: "Subsequent render", unit: "ms" },
  { key: "allocation", label: "Allocated heap", unit: "MiB", decimals: 2 },
];

export function ssrSummaryRecords(results, target) {
  return Object.entries(results).map(([scenario, result]) => ({ scenario, target, values: {
    first: result.initializationAndFirst.median, warm: result.postWarmup.median,
    allocation: result.allocatedHeap?.median == null ? null : result.allocatedHeap.median / 1024 / 1024,
  } }));
}

export function jvmSummaryRecords(data) {
  return jvmSsrTargets.flatMap(({ label: target, resultKey }) =>
    Object.entries(data[resultKey] ?? {}).filter(([, processes]) => processes.length).map(([scenario, processes]) => {
      const samples = processes.flatMap(process => process.subsequent);
      const median = (values, divisor) => {
        const value = summarize(values).median;
        return value == null ? null : value / divisor;
      };
      return { scenario, target, values: {
        first: median(processes.map(process => process.initializationAndFirst.micros), 1000),
        warm: median(samples.map(sample => sample.micros), 1000),
        allocation: median(samples.map(sample => sample.allocatedBytes), 1024 * 1024),
      } };
    }));
}

export const streamingMetrics = [
  { key: "firstChunk", label: "First chunk", unit: "ms" },
  { key: "firstContent", label: "First content", unit: "ms" },
  { key: "first8KiB", label: "First 8 KiB", unit: "ms" },
  { key: "completion", label: "Complete", unit: "ms" },
  { key: "chunks", label: "Chunks", decimals: 0, rank: false },
  { key: "allocation", label: "Allocated heap", unit: "KiB", decimals: 1 },
];

export function streamingSummaryRecords(results, labels) {
  return Object.entries(results).flatMap(([scenario, targets]) => Object.entries(targets)
    .filter(([, result]) => !result.skipped).map(([target, result]) => ({ scenario, target: labels[target], values: {
      ...Object.fromEntries(["firstChunk", "firstContent", "first8KiB", "completion"].map(key => [key, result[key + "Ms"].median])),
      chunks: result.chunks.median,
      allocation: result.allocatedBytes.median == null ? null : result.allocatedBytes.median / 1024,
    } })));
}

export const browserMetrics = [
  { key: "firstReady", label: "First ready", path: "coldStart.postTtfbReadyMs", unit: "ms" },
  { key: "laterReady", label: "Later ready", path: "postWarmup.postTtfbReady.median", unit: "ms" },
  { key: "firstAction", label: "First interaction", path: "interaction.cold.median", unit: "ms" },
  { key: "warmAction", label: "Warm interaction", path: "interaction.postWarmup.median", unit: "ms" },
  { key: "firstRam", label: "App RAM first", path: "memory.cold.userAgentMiB", unit: "MiB" },
  { key: "laterRam", label: "App RAM subsequent", path: "memory.postWarmup.userAgentMiB.median", unit: "MiB" },
  { key: "firstHeap", label: "JS heap first", path: "memory.cold.jsHeapMiB", unit: "MiB" },
  { key: "laterHeap", label: "JS heap subsequent", path: "memory.postWarmup.jsHeapMiB.median", unit: "MiB" },
  { key: "transfer", label: "Script transfer", path: "scriptTransfer.median", unit: "KiB" },
  { key: "dom", label: "DOM elements", path: "domElements", decimals: 0, rank: false },
];
export const paintMetrics = [
  { key: "firstFcp", label: "First FCP", path: "coldStart.fcpMs", unit: "ms" },
  { key: "laterFcp", label: "Later FCP", path: "postWarmup.fcp.median", unit: "ms" },
  { key: "laterTtfb", label: "Later TTFB", path: "postWarmup.ttfb.median", unit: "ms", rank: false },
  { key: "firstNavigation", label: "First navigation → ready", path: "coldStart.navigationReadyMs", unit: "ms" },
  { key: "laterNavigation", label: "Later navigation → ready", path: "postWarmup.navigationReady.median", unit: "ms" },
];

export function browserSummaryRecords(results, labels) {
  return Object.entries(results).flatMap(([scenario, targets]) => Object.entries(targets).map(([target, result]) => ({
    scenario, target: labels[target] ?? target,
    values: Object.fromEntries([...browserMetrics, ...paintMetrics].map(metric => [metric.key,
      metric.path.split(".").reduce((value, name) => value?.[name], result) ?? null])),
  })));
}
