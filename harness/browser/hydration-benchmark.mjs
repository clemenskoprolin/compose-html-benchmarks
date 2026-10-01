// Runs browser hydration benchmarks against the prepared React and Compose client bundles.
// It measures readiness, interactions, transfer, memory, and SSR-node adoption with Playwright.
import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { readFile } from "node:fs/promises";
import { resolve } from "node:path";
import { chromium } from "@playwright/test";
import { startupMetrics, rotated } from "./metrics.mjs";
import { createRunRecord, rootDirectory } from "./run-record.mjs";
import { assertEquivalent } from "./html-equivalence.mjs";
import { interactionInPage } from "./interaction.mjs";
import { loadBenchmarkEnv, reactRuntimeMode, benchmarkCount, browserScenarios } from "./runtime.mjs";
import { summarizeBrowserResults, writeBrowserReport } from "./browser-report.mjs";

loadBenchmarkEnv();
const reactMode = reactRuntimeMode();

const benchmarkDirectory = resolve(rootDirectory, "harness/browser");
const runs = benchmarkCount("BENCHMARK_RUNS", 3);
const repetitions = benchmarkCount("BENCHMARK_REPETITIONS", 2);
const warmups = benchmarkCount("BENCHMARK_WARMUPS", 1, 0);
const serverPort = process.env.BENCHMARK_PORT || "4173";
const browserChannel = process.env.BENCHMARK_BROWSER_CHANNEL || "chrome";
const measureMemory = process.env.BENCHMARK_MEMORY !== "false";
const browserArgs = [
  "--disable-background-timer-throttling",
  "--disable-backgrounding-occluded-windows",
  ...(measureMemory ? ["--enable-blink-features=ForceEagerMeasureMemory"] : []),
];

// Standard mobile/desktop throttled profile (as used in compose-wasm-optimized-v3)
const profile = {
  cpuThrottle: Number(process.env.BENCHMARK_CPU_THROTTLE || 4),
  latencyMs: Number(process.env.BENCHMARK_LATENCY_MS || 40),
  downloadMbps: Number(process.env.BENCHMARK_DOWNLOAD_MBPS || 10),
  uploadMbps: Number(process.env.BENCHMARK_UPLOAD_MBPS || 5),
};

const scenarios = (process.env.BENCHMARK_SCENARIOS || browserScenarios.join(",")).split(",");
const targets = (process.env.BENCHMARK_TARGETS || "react,compose-wasm,compose-js").split(",");

const targetLabels = {
  "react": reactMode === "development" ? "React 19 (dev)" : "React 19",
  "compose-wasm": "Compose (Wasm)",
  "compose-js": "Compose (JS)",
  "compose-wasm-table-unchecked": "Compose (Wasm, table unchecked)",
  "compose-js-table-unchecked": "Compose (JS, table unchecked)",
};

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

async function isReachable(url) {
  try {
    const res = await fetch(url, { signal: AbortSignal.timeout(1500) });
    return res.ok;
  } catch {
    return false;
  }
}

async function startServer() {
  const url = `http://127.0.0.1:${serverPort}/health`;
  if (await isReachable(url)) return null;

  console.log(`Starting benchmark server on port ${serverPort}...`);
  const child = spawn("node", ["server.mjs"], {
    cwd: benchmarkDirectory,
    detached: true,
    stdio: ["ignore", "pipe", "pipe"],
    env: { ...process.env, BENCHMARK_PORT: serverPort },
  });
  child.stdout.on("data", (chunk) => process.stdout.write(`[server] ${chunk}`));
  child.stderr.on("data", (chunk) => process.stderr.write(`[server] ${chunk}`));

  const deadline = Date.now() + 30000;
  while (Date.now() < deadline) {
    if (await isReachable(url)) return child;
    await sleep(300);
  }
  throw new Error("Benchmark server failed to start within 30 seconds");
}

const interactionScenarios = {
  update10th1k: { selector: "#benchmark-update", label: "update" },
  reorder1k: { selector: "#benchmark-reorder", label: "reorder" },
  "filter-list": { selector: "#benchmark-filter", label: "filter" },
};

async function verifyInteraction(page, scenario, operationCount) {
  const { actual, expected } = await page.evaluate(({ scenario, operationCount }) => {
    const data = JSON.parse(document.querySelector("#initial-state, #search-state").textContent);
    if (scenario === "filter-list") {
      return {
        actual: Array.from(document.querySelectorAll(".items > article"), (item) => Number(item.textContent)),
        expected: data.items.filter((id) => operationCount % 2 === 0 || id < 20 || id > 600),
      };
    }
    const actual = Array.from(document.querySelectorAll(".test-data tbody > tr"), (row) => ({
      id: Number(row.children[0].textContent), label: row.children[1].textContent,
    }));
    const offset = (3 * operationCount) % data.rows.length;
    const expected = scenario === "reorder1k"
      ? data.rows.slice(offset).concat(data.rows.slice(0, offset))
      : data.rows.map((row, index) => ({
        ...row, label: row.label + (index % 10 === 0 ? " !!!".repeat(operationCount) : ""),
      }));
    return { actual, expected };
  }, { scenario, operationCount });
  assert.deepEqual(actual, expected, `${scenario}: incorrect DOM after operation ${operationCount}`);
}

async function measureInteraction(page, scenario, operationCount) {
  const config = interactionScenarios[scenario];
  const duration = await page.evaluate(interactionInPage, { ...config, scenario, operationCount });
  await verifyInteraction(page, scenario, operationCount);
  return duration;
}

async function measurePageLoad(browser, target, scenario, collectMemory = measureMemory) {
  const benchmarkOrigin = `http://127.0.0.1:${serverPort}`;
  const url = `${benchmarkOrigin}/${target}/${scenario}`;
  const context = await browser.newContext({
    extraHTTPHeaders: { "Accept-Encoding": "br, gzip" },
    serviceWorkers: "block",
    viewport: { width: 1280, height: 900 },
  });

  try {
    await context.addInitScript(() => {
      performance.setResourceTimingBufferSize(1000);
    });

    const page = await context.newPage();
    const errors = [];
    await context.route("**/*", async (route) => {
      const requestOrigin = new URL(route.request().url()).origin;
      if (requestOrigin === benchmarkOrigin) await route.continue();
      else await route.abort("blockedbyclient");
    });
    page.on("pageerror", (err) => errors.push(err.message));
    page.on("requestfailed", (request) => {
      if (request.url().startsWith(benchmarkOrigin)) {
        errors.push(`Request failed: ${request.url()} (${request.failure()?.errorText || "unknown error"})`);
      }
    });
    page.on("response", (response) => {
      if (response.url().startsWith(benchmarkOrigin) && response.status() >= 400) {
        errors.push(`HTTP ${response.status()}: ${response.url()}`);
      }
    });
    page.on("console", (msg) => {
      if (msg.type() === "error" && !msg.text().startsWith("Failed to load resource:")) {
        errors.push(msg.text());
      }
    });

    // Configure Chrome DevTools Protocol for controlled throttling
    const session = await context.newCDPSession(page);
    await session.send("Network.enable");
    await session.send("Network.setCacheDisabled", { cacheDisabled: true });
    await session.send("Emulation.setCPUThrottlingRate", { rate: profile.cpuThrottle });
    await session.send("Network.emulateNetworkConditions", {
      connectionType: "cellular4g",
      offline: false,
      latency: profile.latencyMs,
      downloadThroughput: (profile.downloadMbps * 1000000) / 8,
      uploadThroughput: (profile.uploadMbps * 1000000) / 8,
    });

    await page.goto(url, { waitUntil: "load" });

    // Wait for explicit application readiness mark or attribute
    await page.locator('body[data-app-ready="true"]').waitFor({ state: "attached", timeout: 45000 }).catch(error => {
      throw new Error(`${url}: readiness failed; ${errors.join("; ") || error.message}`, { cause: error });
    });

    // Allow rendering opportunities before reading paint entries (not a display guarantee).
    await page.evaluate(() => new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve))));

    // Extract performance metrics from inside the page context
    const rawMetrics = await page.evaluate(() => {
      const nav = performance.getEntriesByType("navigation")[0];
      const resources = performance.getEntriesByType("resource");
      const scripts = resources.filter(
        (r) => r.initiatorType === "script" || r.name.endsWith(".js") || r.name.endsWith(".mjs") || r.name.endsWith(".wasm")
      );

      const root = document.querySelector("#app-root") || document.querySelector("#search-page") || document.body;
      const currentFirst = root?.firstElementChild;
      const currentCount = root?.querySelectorAll("*").length ?? 0;
      const adopted = window.__initialFirstElement ? (window.__initialFirstElement === currentFirst) : true;
      const currentRows = document.querySelectorAll(".test-data tbody > tr");
      const tableRowsAdopted = window.__initialTableRows?.length === 1000
        && window.__initialTableRows.every((row, index) => row === currentRows[index]);

      return {
        timing: {
          navigation: nav?.toJSON(),
          marks: performance.getEntriesByType("mark").map((entry) => entry.toJSON()),
          paints: performance.getEntriesByType("paint").map((entry) => entry.toJSON()),
        },
        transferBytes: resources.reduce((sum, r) => sum + (r.transferSize || 0), 0) + (nav ? nav.transferSize || 0 : 0),
        scriptTransferBytes: scripts.reduce((sum, r) => sum + (r.transferSize || 0), 0),
        scriptDecodedBytes: scripts.reduce((sum, r) => sum + (r.decodedBodySize || 0), 0),
        domElementCount: currentCount,
        ssrNodeAdopted: adopted,
        tableRowsAdopted,
      };
    });
    const { timing, ...metrics } = rawMetrics;
    Object.assign(metrics, startupMetrics(timing, target));

    if (!interactionScenarios[scenario]) {
      const expected = await readFile(resolve(rootDirectory, `build/react-html/${scenario}.body.html`), "utf8");
      const actual = await page.locator("#app-root, #search-page").innerHTML();
      assertEquivalent(expected, actual, `${target}/${scenario} hydrated DOM`, true);
    }
    assert(metrics.ssrNodeAdopted, `SSR DOM elements were destroyed/replaced instead of adopted in ${url}`);
    if (interactionScenarios[scenario]) {
      const itemSelector = scenario === "filter-list" ? ".items > article" : ".test-data tbody > tr";
      assert.equal(await page.locator(itemSelector).count(), 1000, `Expected 1,000 mounted items in ${url}`);
      metrics.coldInteractionDurationMs = await measureInteraction(page, scenario, 1);
      for (let operationCount = 2; operationCount <= warmups + 1; operationCount++) {
        await measureInteraction(page, scenario, operationCount);
      }
      let measuredOperation = warmups + 2;
      // Filter additions and removals are different workloads. Always measure removal.
      if (scenario === "filter-list" && measuredOperation % 2 === 0) {
        await measureInteraction(page, scenario, measuredOperation++);
      }
      metrics.warmInteractionDurationMs = await measureInteraction(page, scenario, measuredOperation);
    }
    if (scenario === "hydrate1k") {
      assert(metrics.tableRowsAdopted, `One or more of the 1,000 SSR table rows were replaced during hydration in ${url}`);
      assert.equal(await page.locator(".test-data tbody > tr").count(), 1000, `Expected 1,000 hydrated rows in ${url}`);
      await page.locator(".test-data tbody > tr:nth-child(2) td:nth-child(3) a").click();
      await page.waitForFunction(() => document.querySelectorAll(".test-data tbody > tr").length === 999);
      assert.equal(await page.locator(".test-data tbody > tr").count(), 999, `Hydrated delete did not remove exactly one row in ${url}`);
      assert.equal(
        await page.locator(".test-data tbody > tr:nth-child(2) td:first-child").textContent(),
        "3",
        `Hydrated delete removed the wrong row in ${url}`,
      );
    }
    if (collectMemory) {
      metrics.userAgentMemoryBytes = await page.evaluate(async () => {
        if (typeof performance.measureUserAgentSpecificMemory !== "function") return null;
        try {
          return (await performance.measureUserAgentSpecificMemory()).bytes;
        } catch {
          return null;
        }
      });
      const heap = await session.send("Runtime.getHeapUsage");
      metrics.jsHeapUsedBytes = heap.usedSize;
    }
    assert.deepEqual(errors, [], `Browser errors in ${url}`);
    return { ...metrics, errors };
  } finally {
    await context.close();
  }
}

async function run() {
  const record = await createRunRecord("browser", {
    runs, repetitions, warmups, profile, scenarios, targets, measureMemory,
    forceEagerMemory: measureMemory, reactMode, reactMinified: true,
  });
  const resultsDirectory = record.directory;
  record.metadata.browserVersions = [];
  record.metadata.executionOrder = [];
  console.log("=".repeat(124));
  console.log("CLIENT BENCHMARK — COMPOSE HTML (WASM & JS) vs REACT 19");
  console.log("=".repeat(124));
  console.log(`Settings: ${repetitions} browser processes; each: 1 first load, ${warmups} discarded loads, ${runs} subsequent samples | CPU ${profile.cpuThrottle}x | ${profile.latencyMs}ms CDP latency`);
  console.log(`Scenarios: ${scenarios.join(", ")}`);
  console.log(`Targets:   ${targets.map((target) => targetLabels[target] || target).join(", ")}`);

  const server = await startServer();
  let browser;
  try {
    const observations = Object.fromEntries(scenarios.map(scenario => [scenario, {}]));
    const totalScenarioEvaluations = repetitions * scenarios.length;
    let scenarioEvaluation = 0;
    for (let repetition = 0; repetition < repetitions; repetition++) {
      if (browser) await browser.close();
      try {
        browser = await chromium.launch({
          channel: browserChannel,
          headless: true,
          args: browserArgs,
        });
      } catch {
        console.log("Google Chrome channel not found, using Playwright default browser...");
        browser = await chromium.launch({ headless: true, args: browserArgs });
      }

      record.metadata.browserVersions.push(browser.version());
      for (const scenario of rotated(scenarios, repetition)) {
        console.log(`\nEvaluating scenario: [${scenario}] (${++scenarioEvaluation}/${totalScenarioEvaluations})`);

        const activeTargets = targets.filter(
          (target) => target === "react" || !target.includes("table-unchecked") || scenario === "hydrate1k",
        );

        for (const target of rotated(activeTargets, repetition + scenarios.indexOf(scenario))) {
          record.metadata.executionOrder.push({ repetition, scenario, target });
          const label = targetLabels[target] || target;
          process.stdout.write(`  First load (${label})... `);
          const cold = await measurePageLoad(browser, target, scenario, measureMemory);
          console.log(`Ready ${cold.postTtfbReadyMs.toFixed(1)}ms | Startup ${cold.hydrationDurationMs?.toFixed(2) ?? "n/a"}ms`);

          for (let warmupIndex = 0; warmupIndex < warmups; warmupIndex++) {
            await measurePageLoad(browser, target, scenario, false);
          }

          const samples = [];
          for (let sampleIndex = 0; sampleIndex < runs; sampleIndex++) {
            process.stdout.write(`  Subsequent ${sampleIndex + 1}/${runs} (${label})... `);
            const sample = await measurePageLoad(browser, target, scenario, measureMemory && sampleIndex === runs - 1);
            samples.push(sample);
            console.log(`Ready ${sample.postTtfbReadyMs.toFixed(1)}ms | Startup ${sample.hydrationDurationMs?.toFixed(2) ?? "n/a"}ms`);
          }

          const collected = observations[scenario][target] ??= { firstLoads: [], samples: [] };
          collected.firstLoads.push({ repetition, ...cold });
          collected.samples.push(...samples.map(sample => ({ repetition, ...sample })));
        }
      }
    }
    const allResults = summarizeBrowserResults(observations, { measureMemory, interactionScenarios });
    await writeBrowserReport(resultsDirectory, allResults, {
      scenarios, targets, targetLabels, interactionScenarios, profile, repetitions, warmups, runs, reactMode,
    });
    await record.complete();
  } finally {
    if (browser) await browser.close();
    if (server) server.kill("SIGTERM");
  }
}

run().catch((error) => {
  console.error("Client benchmark error:", error);
  process.exit(1);
});
