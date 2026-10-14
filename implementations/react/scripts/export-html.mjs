import { loadFixtures } from "../../../harness/browser/fixtures.mjs";
import { mkdir, writeFile } from "node:fs/promises";
import { renderBodyScenario, renderScenario } from "../../../build/web/react/dist/server-render.mjs";

const root = new URL("../../../", import.meta.url);
const fixtures = await loadFixtures();
const output = new URL("build/react-html/", root);
await mkdir(output, { recursive: true });
for (const [scenario, data] of Object.entries(fixtures)) {
  const mounted = ["update10th1k", "reorder1k", "filter-list"].includes(scenario);
  await writeFile(new URL(`${scenario}.body.html`, output), mounted ? "" : renderBodyScenario(scenario, data));
  await writeFile(new URL(`${scenario}.html`, output), renderScenario(scenario, data));
}
