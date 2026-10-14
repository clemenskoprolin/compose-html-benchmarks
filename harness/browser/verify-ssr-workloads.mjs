// Checks that the prepared Compose, React, and JVM template engine SSR outputs describe the same DOM.
// Preparation runs this before allowing benchmark measurements.
import { templateTargets } from "../../tools/benchmark-targets.mjs";
import { loadFixtures } from "./fixtures.mjs";
import { readFile } from "node:fs/promises";
import { ensureSsrRuntime, documentScenarios, ssrScenarios } from "./runtime.mjs";
import { assertEquivalent } from "./html-equivalence.mjs";
ensureSsrRuntime();
const { renderScenario, renderBodyScenario } = await import("../../build/web/react/dist/server-render.mjs");
const fixtures = await loadFixtures();
const scenarios = [...ssrScenarios, "hydrate1k"];
const skipped = JSON.parse(await readFile(new URL("../../build/ssr-html/skipped.json", import.meta.url)));
const bodyScenarios = new Set(documentScenarios);
const isolatedBenchmarkScenarios = new Set(scenarios.filter((scenario) => scenario !== "hydrate1k"));
for (const scenario of scenarios) {
  const compose = await readFile(new URL(`../../build/ssr-html/${scenario}.html`, import.meta.url), "utf8");
  const benchmarkRenderer = isolatedBenchmarkScenarios.has(scenario)
    ? await import(`../../build/web/react/dist/server-render-${scenario}.mjs`)
    : null;
  for (const phase of ["first", "subsequent"]) {
    const aggregateHtml = renderScenario(scenario, fixtures[scenario]);
    assertEquivalent(compose, aggregateHtml, `${scenario} ${phase} render`);
    if (benchmarkRenderer) {
      assertEquivalent(aggregateHtml, benchmarkRenderer.renderScenario(fixtures[scenario]), `${scenario} ${phase} isolated benchmark render`);
    }
  }
  if (ssrScenarios.includes(scenario)) {
    for (const { key: target } of templateTargets) {
      if (skipped[`${scenario}/${target}`]) continue;
      const html = await readFile(new URL(`../../build/ssr-html/${scenario}.${target}.html`, import.meta.url), "utf8");
      assertEquivalent(compose, html, `${scenario} ${target}`);
    }
  }
  if (bodyScenarios.has(scenario)) {
    const body = await readFile(new URL(`../../build/ssr-html/${scenario}.body.html`, import.meta.url), "utf8");
    assertEquivalent(body, renderBodyScenario(scenario, fixtures[scenario]), `${scenario} browser body`, true);
  }
  console.log(`${scenario}: first/subsequent DOM, attributes and text verified`);
}
