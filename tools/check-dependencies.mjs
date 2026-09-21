import { readFile } from "node:fs/promises";
const root = new URL("../", import.meta.url);
const manifest = JSON.parse(await readFile(new URL("package.json", root)));
for (const [name, version] of Object.entries({ ...manifest.dependencies, ...manifest.devDependencies })) {
  let actual;
  try { actual = JSON.parse(await readFile(new URL(`node_modules/${name}/package.json`, root))).version; } catch {}
  if (actual !== version) throw new Error(`${name}: expected ${version}, found ${actual ?? 'missing'}. Run npm ci in the repository root.`);
}
