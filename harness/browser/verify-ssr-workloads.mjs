// Checks that the prepared Compose, React, and Thymeleaf SSR outputs describe the same DOM.
// Preparation runs this before allowing benchmark measurements.
import { readFile } from "node:fs/promises";
import { ensureSsrRuntime } from "./runtime.mjs";
import { assertEquivalent } from "./html-equivalence.mjs";
ensureSsrRuntime();
const { renderScenario, renderBodyScenario } = await import("../../build/web/react/dist/server-render.mjs");
const fixtures = JSON.parse(await readFile(new URL("../../workloads/jvm/src/main/resources/fixtures.json", import.meta.url)));
const scenarios = ["tailwind-catalog", "form-app", "data-table", "svg-dashboard", "content-article", "preact-text", "preact-search-results", "preact-stack", "hydrate1k"];
const thymeleafScenarios = new Set(["tailwind-catalog", "form-app", "data-table", "svg-dashboard", "content-article", "preact-text", "preact-search-results", "preact-stack"]);
const bodyScenarios = new Set(["tailwind-catalog", "form-app", "data-table", "svg-dashboard", "content-article"]);
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
  if (thymeleafScenarios.has(scenario)) {
    const thymeleaf = await readFile(new URL(`../../build/ssr-html/${scenario}.thymeleaf.html`, import.meta.url), "utf8");
    assertEquivalent(compose, thymeleaf, `${scenario} Thymeleaf`);
  }
  if (bodyScenarios.has(scenario)) {
    const body = await readFile(new URL(`../../build/ssr-html/${scenario}.body.html`, import.meta.url), "utf8");
    assertEquivalent(body, renderBodyScenario(scenario, fixtures[scenario]), `${scenario} browser body`, true);
  }
  console.log(`${scenario}: first/subsequent DOM, attributes and text verified`);
}
