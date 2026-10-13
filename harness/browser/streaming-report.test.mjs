import test from "node:test";
import assert from "node:assert/strict";
import { jvmSummaryRecords } from "./summary-data.mjs";
import { mkdtemp, writeFile, readFile, readdir, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { streamingComparison, writeStreamingReport } from "./streaming-report.mjs";
import { streamingMilestones } from "./streaming-milestones.mjs";

const processResult = {
  initializationAndFirst: { micros: 2000 },
  subsequent: [{ micros: 1000, allocatedBytes: 1048576 }, { micros: 3000, allocatedBytes: 3145728 }],
};

test("JVM summary includes both compiled engines when Compose is deselected", () => {
  const records = jvmSummaryRecords({
    jstachioProcesses: { "data-table": [processResult] },
    jteProcesses: { "data-table": [processResult] },
  });
  assert.deepEqual(records.map(record => record.target), ["JStachio", "jte"]);
  for (const record of records) assert.deepEqual(record.values, { first: 2, warm: 2, allocation: 2 });
  assert.equal(jvmSummaryRecords({}).length, 0);
});

test("streaming comparison includes selected compiled engines and excludes deselected engines", () => {
  const scenario = "data-table";
  const common = { outputBytes: 9000, contentBytes: 100,
    contentMilestone: streamingMilestones[scenario].description };
  const jvm = { ...common, samples: [{ firstChunkMicros: 100, firstContentMicros: 200,
    first8KiBMicros: 300, completionMicros: 400, chunks: 10, allocatedBytes: 1024 }] };
  const react = { ...common, samples: [{ firstChunkMs: 0.1, firstContentMs: 0.2,
    first8KiBMs: 0.3, completionMs: 0.4, chunks: 10, allocatedBytes: null }] };
  const comparison = streamingComparison({ targets: ["jstachio", "jte"], results: {
    [scenario]: { jstachio: [jvm], jte: [jvm] },
  } }, { results: { [scenario]: [react] } });
  assert.deepEqual(Object.keys(comparison.results[scenario]), ["react", "jstachio", "jte"]);
  assert.equal(comparison.results[scenario].jte.completionMs.median, 0.4);
  assert.equal(comparison.results[scenario].jstachio.allocatedBytes.median, 1024);
});

test("Compose-specific streaming scenario has no React row", () => {
  const scenario = "fractional-style";
  const run = { outputBytes: 9000, contentBytes: 100,
    contentMilestone: streamingMilestones[scenario].description, samples: [{
      firstChunkMicros: 100, firstContentMicros: 200, first8KiBMicros: 300,
      completionMicros: 400, chunks: 2, allocatedBytes: 1024,
    }] };
  const comparison = streamingComparison({ targets: ["compose"], results: {
    [scenario]: { compose: [run] },
  } }, { results: {} });
  assert.deepEqual(Object.keys(comparison.results[scenario]), ["compose"]);
  assert.equal(comparison.groups[0].length, 1);
});

test("streaming reports reject mismatched delivery policies and byte buffer sizes", async () => {
  const directory = await mkdtemp(join(tmpdir(), "streaming-settings-"));
  const settings = { repetitions: 1, warmups: 0, trials: 1, iterations: 1,
    deliverySink: "buffered-utf8", bufferBytes: 8192 };
  try {
    await writeFile(join(directory, "jvm-streaming-results.json"), JSON.stringify({ settings }));
    for (const [key, value] of [["bufferBytes", 4096], ["deliverySink", "raw"]]) {
      await writeFile(join(directory, "react-streaming-results.json"),
        JSON.stringify({ settings: { ...settings, [key]: value } }));
      await assert.rejects(writeStreamingReport(directory), new RegExp(`Streaming settings differ: ${key}`));
    }
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
});

const singleTargetSettings = { repetitions: 1, warmups: 0, trials: 1, iterations: 1,
  chunkSize: 2048, composeOutput: "writer", reactMode: "production", jvmCharacterBufferChars: 8192 };
const jvmSample = { firstChunkMicros: 100, firstContentMicros: 200, first8KiBMicros: 300,
  completionMicros: 400, chunks: 2, allocatedBytes: 1024 };
const reactSample = { firstChunkMs: 0.1, firstContentMs: 0.2, first8KiBMs: 0.3,
  completionMs: 0.4, chunks: 2, allocatedBytes: null };

async function checkSingleTargetReport(target, deliverySink, sample) {
  const directory = await mkdtemp(join(tmpdir(), "streaming-targets-"));
  const includeJvm = target === "compose";
  const kind = includeJvm ? "jvm" : "react";
  const scenario = "data-table";
  const runs = [{ outputBytes: 9000, contentBytes: 100,
    contentMilestone: streamingMilestones[scenario].description, samples: [sample] }];
  try {
    await writeFile(join(directory, `${kind}-streaming-results.json`), JSON.stringify({
      settings: { ...singleTargetSettings, deliverySink, bufferBytes: deliverySink === "renderer" ? null : 8192 },
      targets: includeJvm ? [target] : undefined,
      results: { [scenario]: includeJvm ? { [target]: runs } : runs },
    }));
    await writeStreamingReport(directory, { includeJvm, includeReact: !includeJvm });
    const report = JSON.parse(await readFile(join(directory, "streaming-results.json"), "utf8"));
    assert.deepEqual(Object.keys(report.results[scenario]), [target]);
    assert.deepEqual(Object.keys(report.rawResults), [kind]);
    assert.equal(report.results[scenario][target].completionMs.median, 0.4);
    const markdown = await readFile(join(directory, "streaming-results.md"), "utf8");
    const header = markdown.split("\n").find(line => line.startsWith("| Scenario"));
    for (const label of ["First chunk", "First content", "Chunks"]) {
      assert.equal(header.includes(label), deliverySink === "renderer", `${deliverySink}: ${label}`);
    }
    assert(header.includes("Output bytes"));
    if (includeJvm) assert(!markdown.includes("React"));
    const files = await readdir(directory);
    assert(files.includes(`${kind}-streaming-results.md`));
    assert(!files.includes(`${includeJvm ? "react" : "jvm"}-streaming-results.md`));
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
}

test("streaming report supports Compose-only runs in both delivery modes", async () => {
  await checkSingleTargetReport("compose", "buffered-utf8", jvmSample);
  await checkSingleTargetReport("compose", "renderer", jvmSample);
});

test("streaming report supports React-only runs in both delivery modes", async () => {
  await checkSingleTargetReport("react", "buffered-utf8", reactSample);
  await checkSingleTargetReport("react", "renderer", reactSample);
});
