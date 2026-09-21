import { readFile, mkdir, writeFile } from "node:fs/promises";
import { renderBodyScenario, renderScenario } from "../../../build/web/react/dist/server-render.mjs";

const root = new URL("../../../", import.meta.url);
const fixtures = JSON.parse(await readFile(new URL("workloads/jvm/src/main/resources/fixtures.json", root)));
const output = new URL("build/react-html/", root);
await mkdir(output, { recursive: true });
for (const [scenario, data] of Object.entries(fixtures)) {
  const mounted = ["update10th1k", "reorder1k", "filter-list"].includes(scenario);
  await writeFile(new URL(`${scenario}.body.html`, output), mounted ? "" : renderBodyScenario(scenario, data));
  await writeFile(new URL(`${scenario}.html`, output), renderScenario(scenario, data));
}
