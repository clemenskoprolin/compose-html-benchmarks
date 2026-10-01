import assert from "node:assert/strict";
import { consoleTable, markdownTable, formatCell } from "./report-table.mjs";

const equal = (a, b) => Math.abs(a - b) <= Math.max(Math.abs(a), Math.abs(b)) * 1e-12;
const geometricMean = values => Math.exp(values.reduce((sum, value) => sum + Math.log(value), 0) / values.length);

// Each record holds one workload's medians in consistent units, never formatted strings.
// Every compared renderer uses the same workloads for a metric. Renderers with no values
// for that metric remain unavailable (e.g. React allocations in the streaming benchmark).
export function summarizeScenarios(records, metrics) {
  const targets = [...new Set(records.map(record => record.target))];
  const scenarios = [...new Set(records.map(record => record.scenario))];
  const byScenario = new Map();
  for (const record of records) {
    const byTarget = byScenario.get(record.scenario) ?? new Map();
    assert(!byTarget.has(record.target), `Duplicate summary record: ${record.scenario}/${record.target}`);
    byTarget.set(record.target, record.values);
    byScenario.set(record.scenario, byTarget);
  }
  return metrics.map(metric => {
    for (const record of records) {
      const value = record.values[metric.key];
      assert(value == null || (Number.isFinite(value) && value >= 0), `Invalid summary value: ${metric.key}`);
    }
    const hasValue = target => records.some(record => record.target === target && record.values[metric.key] > 0);
    const available = targets.filter(hasValue);
    const common = available.length ? scenarios.filter(scenario =>
      available.every(target => byScenario.get(scenario).get(target)?.[metric.key] > 0)) : [];
    const averages = Object.fromEntries(available.map(target => [target, common.length
      ? geometricMean(common.map(scenario => byScenario.get(scenario).get(target)[metric.key])) : null]));
    const ranked = metric.rank !== false;
    const best = common.length && ranked ? Math.min(...Object.values(averages)) : null;
    const winners = best == null ? [] : available.filter(target => equal(averages[target], best));
    const wins = Object.fromEntries(available.map(target => [target, 0]));
    if (ranked) for (const scenario of common) {
      const minimum = Math.min(...available.map(target => byScenario.get(scenario).get(target)[metric.key]));
      for (const target of available) if (equal(byScenario.get(scenario).get(target)[metric.key], minimum)) wins[target]++;
    }
    return { ...metric, scenarios: common, targets: targets.map(target => ({
      target, average: averages[target] ?? null, wins: wins[target] ?? null,
      differencePercent: best == null || averages[target] == null ? null
        : equal(averages[target], best) ? 0 : Math.expm1(Math.log(averages[target]) - Math.log(best)) * 100,
    })), winners };
  });
}

const summaryColumns = [
  { label: "Metric", width: 26 }, { label: "Scenarios", width: 9, align: "right", decimals: 0 },
  { label: "Target", width: 28 }, { label: "Overall average", width: 17, align: "right" },
  { label: "Compared with best", width: 20, align: "right" },
  { label: "Workload wins", width: 14, align: "right" },
];
export const summaryMethod = "Averages: geometric means of shared workload medians, equally weighted. " +
  "BEST = lowest; +% = increase over BEST. Wins include ties.";

export function scenarioSummary(records, metrics) {
  const results = summarizeScenarios(records, metrics.filter(metric => metric.rank !== false))
    .filter(metric => metric.scenarios.length > 0);
  if (!results.length) return { results, console: "", markdown: "" };
  const groups = results.map(metric => metric.targets.map((target, index) => {
    const average = formatCell({ decimals: metric.decimals ?? 3, suffix: metric.unit ? ` ${metric.unit}` : "" }, target.average);
    const comparison = target.average == null ? "n/a"
      : metric.targets.filter(value => value.average != null).length === 1 ? "Only target"
      : metric.winners.includes(target.target) ? "BEST" : `+${target.differencePercent.toFixed(1)}%`;
    return [index ? "" : metric.label, metric.scenarios.length, target.target, average, comparison,
      target.average == null ? "—" : `${target.wins}/${metric.scenarios.length}`];
  }));
  const columns = summaryColumns.map((column, index) => ({ ...column,
    width: Math.max(column.width, ...groups.flat().map(row => formatCell(column, row[index]).length)),
  }));
  return {
    results,
    console: "\n" + consoleTable({ title: "OVERALL ACROSS SCENARIOS", columns, groups,
      blankRepeatedFirstCell: false, width: columns.reduce((sum, column) => sum + column.width, columns.length - 1) }) + `\n${summaryMethod}\n`,
    markdown: `\n\n### Overall across scenarios\n\n${markdownTable(columns, groups.flat())}\n\n${summaryMethod}\n`,
  };
}
