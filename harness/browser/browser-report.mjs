// Format browser results after all measurements have finished.
import { summarize } from "./metrics.mjs";
import { writeFile } from "node:fs/promises";
import { resolve } from "node:path";
import { scenarioSummary } from "./benchmark-summary.mjs";
import { browserMetrics, paintMetrics, browserSummaryRecords } from "./summary-data.mjs";
import { consoleTable } from "./report-table.mjs";

const loadColumns = [
  ["Scenario", 20], ["Target", 20], ["First ready", 12], ["Later ready", 12],
  ["First action", 13], ["Warm action", 13], ["RAM warm", 11], ["JS heap", 10],
].map(([label, width], index) => ({ label, width, align: index > 1 ? "right" : "left" }));

const startupFields = {
  ttfb: "ttfbMs", fcp: "fcpMs", navigationReady: "navigationReadyMs",
  postTtfbReady: "postTtfbReadyMs", startupDuration: "hydrationDurationMs",
};

function summarizeBrowserTarget({ firstLoads, samples }, { measureMemory, hasInteraction }) {
  const summary = (field, divisor = 1) => summarize(samples.map(sample => sample[field])
    .filter(value => value != null).map(value => value / divisor));
  const cold = Object.fromEntries(Object.keys(firstLoads[0]).map(key => [key,
    firstLoads.some(sample => typeof sample[key] === "number")
      ? summarize(firstLoads.map(sample => sample[key])).median : firstLoads[0][key],
  ]));
  return {
    rawSamples: { firstLoads, subsequentLoads: samples },
    coldStart: {
      ttfbMs: cold.ttfbMs, fcpMs: cold.fcpMs, navigationReadyMs: cold.navigationReadyMs,
      postTtfbReadyMs: cold.postTtfbReadyMs, startupDurationMs: cold.hydrationDurationMs,
    },
    postWarmup: Object.fromEntries(Object.entries(startupFields).map(([key, field]) => [key, summary(field)])),
    interaction: hasInteraction ? {
      cold: summary("coldInteractionDurationMs"), postWarmup: summary("warmInteractionDurationMs"),
    } : null,
    memory: measureMemory ? {
      cold: {
        userAgentMiB: cold.userAgentMemoryBytes == null ? null : cold.userAgentMemoryBytes / 1024 / 1024,
        jsHeapMiB: cold.jsHeapUsedBytes / 1024 / 1024,
      },
      postWarmup: {
        userAgentMiB: summary("userAgentMemoryBytes", 1024 * 1024),
        jsHeapMiB: summary("jsHeapUsedBytes", 1024 * 1024),
      },
    } : null,
    scriptTransfer: summarize(samples.map(sample => sample.scriptTransferBytes / 1024)),
    scriptDecoded: summarize(samples.map(sample => sample.scriptDecodedBytes / 1024)),
    domElements: samples[0]?.domElementCount ?? cold.domElementCount,
  };
}

export function summarizeBrowserResults(observations, { measureMemory, interactionScenarios }) {
  const results = {};
  for (const [scenario, targets] of Object.entries(observations)) {
    results[scenario] = Object.fromEntries(Object.entries(targets).map(([target, loads]) =>
      [target, summarizeBrowserTarget(loads, { measureMemory, hasInteraction: interactionScenarios[scenario] })]));
  }
  return results;
}

export async function writeBrowserReport(resultsDirectory, allResults, {
  scenarios, targets, targetLabels, interactionScenarios, profile, repetitions, warmups, runs, reactMode,
}) {
  const consoleGroups = [];
  const markdownRows = [];
  const paintRows = [];
  for (const scenario of scenarios) {
    const consoleRows = [];
    for (const target of targets) {
      const result = allResults[scenario]?.[target];
      if (!result) continue;
      const label = targetLabels[target] || target;
      const coldReady = `${result.coldStart.postTtfbReadyMs.toFixed(1)} ms`;
      const warmReady = `${result.postWarmup.postTtfbReady.median.toFixed(1)} ms`;
      const coldAction = result.interaction ? `${result.interaction.cold.median.toFixed(2)} ms` : "—";
      const warmAction = result.interaction ? `${result.interaction.postWarmup.median.toFixed(2)} ms` : "—";
      const formatMs = (value) => value == null ? "n/a" : `${value.toFixed(1)} ms`;
      paintRows.push(`| ${scenario} | ${label} | ${formatMs(result.coldStart.fcpMs)} | ${formatMs(result.postWarmup.fcp.median)} | ${formatMs(result.postWarmup.ttfb.median)} | ${formatMs(result.coldStart.navigationReadyMs)} | ${formatMs(result.postWarmup.navigationReady.median)} |`);
      const payload = `${result.scriptTransfer.median.toFixed(1)} KiB`;
      const userAgentMemory = result.memory?.postWarmup.userAgentMiB.n
        ? `${result.memory.cold.userAgentMiB?.toFixed(1) ?? "n/a"} → ${result.memory.postWarmup.userAgentMiB.median.toFixed(1)} MiB`
        : "n/a";
      const jsHeap = result.memory
        ? `${result.memory.cold.jsHeapMiB.toFixed(1)} → ${result.memory.postWarmup.jsHeapMiB.median.toFixed(1)} MiB`
        : "n/a";
      markdownRows.push(`| ${scenario} | ${label} | ${coldReady} | ${warmReady} | ${coldAction} | ${warmAction} | ${userAgentMemory} | ${jsHeap} | ${payload} | ${result.domElements} |`);
      consoleRows.push([scenario, label, coldReady, warmReady, coldAction, warmAction,
        result.memory?.postWarmup.userAgentMiB.n ? `${result.memory.postWarmup.userAgentMiB.median.toFixed(1)} MiB` : "n/a",
        result.memory ? `${result.memory.postWarmup.jsHeapMiB.median.toFixed(1)} MiB` : "n/a"]);
    }
    consoleGroups.push(consoleRows);
  }
  console.log("\n" + consoleTable({ title: "FIRST AND SUBSEQUENT LOAD RESULTS", columns: loadColumns,
    groups: consoleGroups, width: 124, blankRepeatedFirstCell: false }));

  const summaryRecords = browserSummaryRecords(allResults, targetLabels);
  const overall = scenarioSummary(summaryRecords, browserMetrics);
  const paintOverall = scenarioSummary(summaryRecords, paintMetrics);
  const consoleOverall = scenarioSummary(summaryRecords,
    browserMetrics.filter(metric => ["firstReady", "laterReady", "firstAction", "warmAction", "laterRam", "laterHeap"].includes(metric.key)));
  console.log(consoleOverall.console);

  const interactionVerification = scenarios.some((scenario) => interactionScenarios[scenario])
    ? `\n- **Interaction protocol**: Measure the first operation, perform ${warmups} warmup operations, then measure the subsequent operation. Filter resets to 1,000 items when necessary so both measured operations remove items 20–600.`
    : "";
  const report = `# Client benchmark: Compose HTML vs React 19

Automated with Playwright and Chrome DevTools Protocol.

- **CPU throttling**: ${profile.cpuThrottle}× slowdown
- **Network**: ${profile.latencyMs} ms CDP latency, ${profile.downloadMbps} Mbps download, ${profile.uploadMbps} Mbps upload
- **Cache**: Disabled for every page load
- **Startup protocol**: ${repetitions} independent browser processes, rotating target/scenario order. Per target per process: one first load, ${warmups} discarded loads, then ${runs} subsequent loads; every load uses a fresh context. First-load columns aggregate the per-process first loads; they do not measure browser launch or an isolated cold OS cache.
- **Reported aggregate**: Median of post-warmup samples${interactionVerification}
- **Memory**: User-agent-specific application memory plus CDP JS heap, collected after timing and reported first → subsequent. When enabled, Chrome's eager measurement mode forces the API's garbage collection immediately instead of waiting for its fallback timeout.
- **Scope**: All nine browser scenarios use matched fixture data and markup. Compose uses production bundles; React uses a minified ${reactMode} runtime bundle. Both targets serve pre-exported HTML.
- **DOM verification**: SSR node adoption and workload result assertions run for every sample

| Scenario | Target | First-load TTFB → Ready | Subsequent TTFB → Ready | First interaction | Post-warmup interaction | App RAM first → subsequent | JS heap first → subsequent | Script transfer | DOM elements |
| :--- | :--- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
${markdownRows.join("\n")}${overall.markdown}

| Scenario | Target | First-load FCP | Subsequent FCP | Subsequent TTFB | First-load navigation → Ready | Subsequent navigation → Ready |
| :--- | :--- | ---: | ---: | ---: | ---: | ---: |
${paintRows.join("\n")}${paintOverall.markdown}

FCP is first contentful paint, measured from navigation start. It can precede hydration because SSR HTML is already visible. Ready is the application's post-commit app-ready mark, not a browser paint metric. TTFB → Ready subtracts navigation responseStart; navigation → Ready includes it. Both frameworks serve pre-exported HTML from the same server. TTFB remains diagnostic, not an SSR speed comparison. CDP latency models browser resource delivery; navigation responseStart can precede the artificial delay.

Startup duration in JSON runs from the framework start mark to app-ready (React uses a layout effect after commit; both include fixture decoding). Interaction durations include DOM completion and two animation frames; they are a rendering opportunity proxy, not pure framework execution time or a guaranteed display timestamp. Script transfer includes Resource Timing's estimated response headers and JS/Wasm bodies, not just Brotli body bytes. Memory is sampled after interactions/deletion, on the first and last measured loads of each browser process; unavailable values are null. Raw first/subsequent samples are retained in JSON with process indices. p95 is null for fewer than 20 observations. Settings, runtime versions and bundle hashes are archived beside the results. The legacy coldStart/postWarmup JSON keys refer to first/subsequent fresh-context loads.
`;

  await writeFile(resolve(resultsDirectory, "hydration-results.md"), report);
  await writeFile(resolve(resultsDirectory, "hydration-results.json"), `${JSON.stringify(allResults, null, 2)}\n`);
}
