import { access, cp, mkdir, readFile, readdir, rm, writeFile } from "node:fs/promises";
import { createHash } from "node:crypto";
import { brotliCompress, constants, gzip } from "node:zlib";
import { promisify } from "node:util";
import { browserScenarios } from "../harness/browser/runtime.mjs";

const brotli = promisify(brotliCompress);
const gzipFile = promisify(gzip);
const root = new URL("../", import.meta.url);
const scenarios = (process.env.COMPOSE_CLIENT_SCENARIOS || browserScenarios.join(",")).split(",");
const outputSuffix = process.env.COMPOSE_CLIENT_OUTPUT_SUFFIX || "";
let reused = 0;

for (const scenario of scenarios) {
  const sourceRoot = new URL(`implementations/compose/browser/${scenario}/build/dist/`, root);
  const outputRoot = new URL(`build/web/static/${scenario}${outputSuffix}/`, root);

  for (const [target, sourceDirectory] of [
    ["js", new URL("js/productionExecutable/", sourceRoot)],
    ["wasm", new URL("wasmJs/productionExecutable/", sourceRoot)],
  ]) {
    const outputDirectory = new URL(`${target}/`, outputRoot);
    const { signature, files } = await sourceSignature(sourceDirectory);
    const cacheFile = new URL(".source-signature", outputDirectory);
    const cachedSignature = await readFile(cacheFile, "utf8").catch(() => null);
    if (cachedSignature === signature && await outputComplete(outputDirectory, files)) {
      reused++;
      continue;
    }
    await rm(outputDirectory, { force: true, recursive: true });
    await mkdir(outputDirectory, { recursive: true });
    await cp(sourceDirectory, outputDirectory, { recursive: true });
    await precompress(outputDirectory, files);
    await writeFile(cacheFile, signature);
  }
}

async function sourceSignature(directory) {
  const files = [];
  async function walk(current, prefix) {
    for (const entry of await readdir(current, { withFileTypes: true })) {
      const relative = `${prefix}${entry.name}`;
      const url = new URL(entry.name, current);
      if (entry.isDirectory()) {
        await walk(new URL(`${entry.name}/`, current), `${relative}/`);
      } else if (entry.isFile()) {
        files.push(relative);
      }
    }
  }
  await walk(directory, "");
  files.sort();
  const digest = createHash("sha256");
  for (const file of files) {
    digest.update(file).update("\0");
    digest.update(createHash("sha256").update(await readFile(new URL(file, directory))).digest());
  }
  return { signature: digest.digest("hex"), files };
}

async function outputComplete(directory, files) {
  for (const file of files) {
    const output = new URL(file, directory);
    try {
      await access(output);
      if (/\.(?:js|mjs|wasm)$/.test(file)) {
        await access(new URL(`${file}.br`, directory));
        await access(new URL(`${file}.gz`, directory));
      }
    } catch { return false; }
  }
  return true;
}

async function precompress(directory, files) {
  for (const file of files) {
    if (!/\.(?:js|mjs|wasm)$/.test(file)) continue;
    const url = new URL(file, directory);

    const source = await readFile(url);
    const [brotliBody, gzipBody] = await Promise.all([
      brotli(source, {
        params: { [constants.BROTLI_PARAM_QUALITY]: 11 },
      }),
      gzipFile(source, { level: 9 }),
    ]);
    await Promise.all([
      writeFile(new URL(`${file}.br`, directory), brotliBody),
      writeFile(new URL(`${file}.gz`, directory), gzipBody),
    ]);
  }
}

console.log(`Packaged Compose browser clients for ${scenarios.join(", ")}${outputSuffix} (${reused} unchanged targets reused)`);
