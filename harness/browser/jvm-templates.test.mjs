import test from "node:test";
import assert from "node:assert/strict";
import { jvmSummaryRecords } from "./summary-data.mjs";
import { streamingComparison } from "./streaming-report.mjs";
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
