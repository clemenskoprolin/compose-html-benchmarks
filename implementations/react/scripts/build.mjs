import { brotliCompress, constants, gzip } from "node:zlib";
import { promisify } from "node:util";
import { mkdir, readFile, readdir, rm, writeFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { spawnSync } from "node:child_process";
import { build } from "esbuild";
import { loadBenchmarkEnv, reactRuntimeMode } from "../../../harness/browser/runtime.mjs";

loadBenchmarkEnv();
const reactMode = reactRuntimeMode();

const brotli = promisify(brotliCompress);
const gzipFile = promisify(gzip);
const repositoryRoot = new URL("../../../", import.meta.url);
const distribution = new URL("build/web/react/dist/", repositoryRoot);

await rm(distribution, { force: true, recursive: true });
await mkdir(distribution, { recursive: true });

const shared = {
  bundle: true,
  define: { "process.env.NODE_ENV": JSON.stringify(reactMode) },
  logLevel: "info",
  // Keep both runtime modes minified so their size and parse costs are comparable.
  minify: true,
  sourcemap: true,
};
const nodeEsmBanner = {
  js: 'import { createRequire as __createRequire } from "node:module"; const require = __createRequire(import.meta.url);',
};

async function browserBundle(scenario, component, directory) {
  const name = `react-${scenario}`;
  const result = await build({
    ...shared,
    entryNames: `${name}.[hash]`,
    stdin: {
      contents: `import { ${component} } from "../${directory}/${component}.jsx"; import { startClient } from "../browser/client.jsx"; startClient(${component}, ${["update10th1k", "reorder1k", "filter-list"].includes(scenario)});`,
      resolveDir: fileURLToPath(new URL("./", import.meta.url)),
      sourcefile: name + ".jsx",
      loader: "jsx",
    },
    format: "esm",
    metafile: true,
    outdir: fileURLToPath(distribution),
    platform: "browser",
  });
  const output = Object.entries(result.metafile.outputs)
    .find(([, metadata]) => metadata.entryPoint)?.[0];
  if (!output) throw new Error(`esbuild did not report an entry output for ${name}`);
  return `/${output.slice(output.lastIndexOf("dist/"))}`;
}

const scenarioComponents = {
  "tailwind-catalog": ["TailwindCatalog", "shared"],
  "form-app": ["FormApp", "shared"],
  "data-table": ["DataTable", "shared"],
  "svg-dashboard": ["SvgDashboard", "shared"],
  "content-article": ["ContentArticle", "shared"],
  "hydrate1k": ["Hydrate1k", "browser"],
  "update10th1k": ["Update10th1k", "browser"],
  "reorder1k": ["Reorder1k", "browser"],
  "filter-list": ["FilterList", "browser"],
};

const ssrScenarioComponents = {
  "tailwind-catalog": ["TailwindCatalog", "../shared/TailwindCatalog.jsx", "renderDocumentComponent", ", true"],
  "form-app": ["FormApp", "../shared/FormApp.jsx", "renderDocumentComponent", ""],
  "data-table": ["DataTable", "../shared/DataTable.jsx", "renderDocumentComponent", ""],
  "svg-dashboard": ["SvgDashboard", "../shared/SvgDashboard.jsx", "renderDocumentComponent", ""],
  "content-article": ["ContentArticle", "../shared/ContentArticle.jsx", "renderDocumentComponent", ", false, true"],
  "preact-text": ["PreactText", "../ssr/PreactText.jsx", "renderBodyComponent", ""],
  "preact-search-results": ["PreactSearchResults", "../ssr/PreactSearchResults.jsx", "renderBodyComponent", ""],
  "preact-stack": ["PreactStack", "../ssr/PreactStack.jsx", "renderBodyComponent", ""],
};
const reactHydrated = Object.fromEntries(await Promise.all(
  Object.entries(scenarioComponents).map(async ([scenario, [component, directory]]) =>
    [scenario, await browserBundle(scenario, component, directory)]),
));
await build({
  ...shared,
  banner: nodeEsmBanner,
  entryPoints: [fileURLToPath(new URL("../ssr/server-render.jsx", import.meta.url))],
  outfile: fileURLToPath(new URL("server-render.mjs", distribution)),
  format: "esm",
  platform: "node",
});
await Promise.all(Object.entries(ssrScenarioComponents).map(async ([scenario, [component, componentPath, renderer, rendererArguments]]) => {
  await build({
    ...shared,
    banner: nodeEsmBanner,
    stdin: {
      contents: `import { ${component} } from "${componentPath}"; import { ${renderer} } from "../ssr/renderer.jsx"; export const renderScenario = (data) => ${renderer}(${component}, data${rendererArguments});`,
      resolveDir: fileURLToPath(new URL("./", import.meta.url)),
      sourcefile: `server-render-${scenario}.jsx`,
      loader: "jsx",
    },
    outfile: fileURLToPath(new URL(`server-render-${scenario}.mjs`, distribution)),
    format: "esm",
    platform: "node",
  });
}));
await writeFile(
  new URL("asset-manifest.json", distribution),
  `${JSON.stringify({ reactMode, minified: true, reactHydrated }, null, 2)}\n`,
);
const exportResult = spawnSync(process.execPath, ["--stack-size=8192", fileURLToPath(new URL("export-html.mjs", import.meta.url))], {
  stdio: "inherit", env: { ...process.env, NODE_ENV: reactMode },
});
if (exportResult.status !== 0) throw new Error("React HTML export failed");

async function precompress(directory) {
  for (const entry of await readdir(directory, { withFileTypes: true })) {
    const url = new URL(entry.name, directory);
    if (entry.isDirectory()) {
      await precompress(new URL(`${entry.name}/`, directory));
    } else if (/\.(?:css|js|json|svg)$/.test(entry.name)) {
      const source = await readFile(url);
      if (source.byteLength < 512) continue;
      const [brotliBody, gzipBody] = await Promise.all([
        brotli(source, {
          params: {
            [constants.BROTLI_PARAM_QUALITY]: 11,
          },
        }),
        gzipFile(source, { level: 9 }),
      ]);
      await Promise.all([
        writeFile(new URL(`${entry.name}.br`, directory), brotliBody),
        writeFile(new URL(`${entry.name}.gz`, directory), gzipBody),
      ]);
    }
  }
}

await precompress(distribution);
console.log(`React ${reactMode} bundles built, minified, and precompressed successfully.`);
