import { TailwindCatalog } from "../shared/TailwindCatalog.jsx";
import { FormApp } from "../shared/FormApp.jsx";
import { DataTable } from "../shared/DataTable.jsx";
import { SvgDashboard } from "../shared/SvgDashboard.jsx";
import { ContentArticle } from "../shared/ContentArticle.jsx";
import { Hydrate1k } from "../browser/Hydrate1k.jsx";
import { Update10th1k } from "../browser/Update10th1k.jsx";
import { Reorder1k } from "../browser/Reorder1k.jsx";
import { FilterList } from "../browser/FilterList.jsx";
import { PreactText } from "./PreactText.jsx";
import { PreactSearchResults } from "./PreactSearchResults.jsx";
import { PreactStack } from "./PreactStack.jsx";
import { renderBodyComponent, renderDocumentComponent } from "./renderer.jsx";

const scenarioComponents = {
  "tailwind-catalog": TailwindCatalog,
  "form-app": FormApp,
  "data-table": DataTable,
  "svg-dashboard": SvgDashboard,
  "content-article": ContentArticle,
  "hydrate1k": Hydrate1k,
  "update10th1k": Update10th1k,
  "reorder1k": Reorder1k,
  "filter-list": FilterList,
  "preact-text": PreactText,
  "preact-search-results": PreactSearchResults,
  "preact-stack": PreactStack,
};

const documentScenarios = new Set(["tailwind-catalog", "form-app", "data-table", "svg-dashboard", "content-article"]);

export function renderBodyScenario(scenario, data) {
  const Component = scenarioComponents[scenario];
  if (!Component) {
    throw new Error(`Unknown scenario: ${scenario}`);
  }
  return renderBodyComponent(Component, data);
}

export function renderScenario(scenario, data) {
  const Component = scenarioComponents[scenario];
  if (!Component) {
    throw new Error(`Unknown scenario: ${scenario}`);
  }
  if (!documentScenarios.has(scenario)) return renderBodyComponent(Component, data);
  return renderDocumentComponent(Component, data, scenario === "tailwind-catalog", scenario === "content-article");
}
