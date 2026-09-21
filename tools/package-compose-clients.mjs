import { cp, mkdir, readFile, readdir, rm, writeFile } from "node:fs/promises";
import { brotliCompress, constants, gzip } from "node:zlib";
import { promisify } from "node:util";

const brotli = promisify(brotliCompress);
const gzipFile = promisify(gzip);
const root = new URL("../", import.meta.url);
const scenarios = (process.env.COMPOSE_CLIENT_SCENARIOS || "tailwind-catalog,form-app,data-table,svg-dashboard,content-article,hydrate1k,update10th1k,reorder1k,filter-list").split(",");
const outputSuffix = process.env.COMPOSE_CLIENT_OUTPUT_SUFFIX || "";

for (const scenario of scenarios) {
  const sourceRoot = new URL(`implementations/compose/browser/${scenario}/build/dist/`, root);
  const outputRoot = new URL(`build/web/static/${scenario}${outputSuffix}/`, root);
  await rm(outputRoot, { force: true, recursive: true });

  for (const [target, sourceDirectory] of [
    ["js", new URL("js/productionExecutable/", sourceRoot)],
    ["wasm", new URL("wasmJs/productionExecutable/", sourceRoot)],
  ]) {
    const outputDirectory = new URL(`${target}/`, outputRoot);
    await mkdir(outputDirectory, { recursive: true });
    await cp(sourceDirectory, outputDirectory, { recursive: true });
    await precompress(outputDirectory);
  }
}

async function precompress(directory) {
  for (const entry of await readdir(directory, { withFileTypes: true })) {
    const url = new URL(entry.name, directory);
    if (entry.isDirectory()) {
      await precompress(new URL(`${entry.name}/`, directory));
      continue;
    }
    if (!/\.(?:js|mjs|wasm)$/.test(entry.name)) continue;

    const source = await readFile(url);
    const [brotliBody, gzipBody] = await Promise.all([
      brotli(source, {
        params: { [constants.BROTLI_PARAM_QUALITY]: 11 },
      }),
      gzipFile(source, { level: 9 }),
    ]);
    await Promise.all([
      writeFile(new URL(`${entry.name}.br`, directory), brotliBody),
      writeFile(new URL(`${entry.name}.gz`, directory), gzipBody),
    ]);
  }
}

console.log(`Packaged Compose browser clients for ${scenarios.join(", ")}${outputSuffix}`);
