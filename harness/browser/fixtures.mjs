// Expand compact text specs once, outside renderer initialization and timing.
import { readFile } from "node:fs/promises";
import { BAVARIA_FOO, BAVARIA_BAR } from "../../workloads/text-prose.mjs";

export async function loadFixtures() {
  const fixtures = JSON.parse(await readFile(new URL("../../workloads/jvm/src/main/resources/fixtures.json", import.meta.url)));
  for (const name of ["dynamic-text", "dynamic-text-escaped"]) {
    const { count, escaped } = fixtures[name];
    fixtures[name] = { items: Array.from({ length: count }, (_, index) => {
      const prefix = escaped ? `<item ${index + 1}> & "quoted" 'single' 😀 café α: ` : `Item ${index + 1}: `;
      return { foo: prefix + BAVARIA_FOO, bar: prefix + BAVARIA_BAR };
    }) };
  }
  return fixtures;
}
