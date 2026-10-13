// Local HTTP server for previewing and browser-benchmarking the prepared web artifacts.
// It serves framework bundles and the pre-exported SSR HTML.
import { createReadStream } from "node:fs";
import { readFile, readdir, stat } from "node:fs/promises";
import { createServer } from "node:http";
import { fileURLToPath } from "node:url";
import { documentScenarios as documents, browserScenarios } from "./runtime.mjs";

const port = Number(process.env.BENCHMARK_PORT || 4173);
const repositoryRoot = new URL("../../", import.meta.url);
const reactDistribution = new URL("build/web/react/dist/", repositoryRoot);
const staticDirectory = new URL("build/web/static/", repositoryRoot);
const manifest = JSON.parse(await readFile(new URL("asset-manifest.json", reactDistribution), "utf8"));

const documentScenarios = new Set(documents);
const composeClientScenarios = new Set(browserScenarios);
const composeWasmUrls = new Map();

let hydrate1kWasmUrl = "";
try {
  const wasmAssets = await readdir(new URL("hydrate1k/wasm/", staticDirectory));
  const wasmAsset = wasmAssets.find((name) => name.endsWith(".wasm"));
  if (wasmAsset) hydrate1kWasmUrl = `/static/hydrate1k/wasm/${wasmAsset}`;
} catch {
  // The hydrate1k client may not have been built yet; its page will still load without a preload hint.
}
for (const scenario of [...composeClientScenarios, "hydrate1k-table-unchecked"]) {
  try {
    const wasmAssets = await readdir(new URL(`${scenario}/wasm/`, staticDirectory));
    const wasmAsset = wasmAssets.find((name) => name.endsWith(".wasm"));
    if (wasmAsset) composeWasmUrls.set(scenario, `/static/${scenario}/wasm/${wasmAsset}`);
  } catch {
    // The matching client may not have been built yet.
  }
}

// Fixture data loader
const fixturesUrl = new URL("workloads/jvm/src/main/resources/fixtures.json", repositoryRoot);
let fixtures = {};
try {
  fixtures = JSON.parse(await readFile(fixturesUrl, "utf8"));
} catch {
  // Fallback
}

function escapeJson(value) {
  return JSON.stringify(value)
    .replaceAll("<", "\\u003c")
    .replaceAll(">", "\\u003e")
    .replaceAll("&", "\\u0026");
}

function renderReactDocument({ scenario, bodyHtml, data, scriptUrl }) {
  const serializedState = escapeJson(data);
  return `<!doctype html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Benchmark — React — ${scenario}</title>
  <link rel="stylesheet" href="/styles.css">
</head>
<body data-scenario="${scenario}" data-framework="react">
  <script id="initial-state" type="application/json">${serializedState}</script>
  <div id="app-root">${bodyHtml}</div>
  <script>
    window.__initialFirstElement = document.querySelector("#app-root")?.firstElementChild;
    window.__initialTableRows = Array.from(document.querySelectorAll(".test-data tbody > tr"));
  </script>
  <script type="module" src="${scriptUrl}"></script>
</body>
</html>`;
}

function renderComposeDocument({ scenario, bodyHtml, data, target, assetScenario = scenario }) {
  const serializedState = escapeJson(data);
  const isWasm = target === "wasm";
  const scriptUrl = composeClientScenarios.has(scenario)
    ? `/static/${assetScenario}/${target}/${scenario}-compose.js`
    : isWasm
      ? "/static/search-client-wasm.639ea11629a62fc2db3b.js"
      : "/static/search-client-js.149d99f8c64d47535abe.js";
  const wasmUrl = composeClientScenarios.has(scenario)
    ? composeWasmUrls.get(assetScenario) || (scenario === "hydrate1k" ? hydrate1kWasmUrl : "")
    : "/static/9aec764547a59d19de58.wasm";
  const wasmPreload = isWasm && wasmUrl
    ? `<link rel="preload" as="fetch" type="application/wasm" href="${wasmUrl}" crossorigin="anonymous">`
    : "";

  return `<!doctype html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Benchmark — Compose ${target} — ${scenario}</title>
  <link rel="stylesheet" href="/styles.css">
  ${wasmPreload}
</head>
<body data-scenario="${scenario}" data-framework="compose" data-client-target="${target}">
  <div id="search-page" data-compose-hydration-root="">${bodyHtml}</div>
  <script id="search-state" type="text/plain" data-compose-hydration-state="escaped-text-v1">${serializedState}</script>
  <script>
    window.__initialFirstElement = document.querySelector("#search-page")?.firstElementChild;
    window.__initialTableRows = Array.from(document.querySelectorAll(".test-data tbody > tr"));
  </script>
  <script type="module" src="${scriptUrl}"></script>
</body>
</html>`;
}

function acceptedEncoding(header, supported) {
  if (!header) return null;
  const qualities = new Map(header.split(",").map((value) => {
    const [token, ...parameters] = value.trim().toLowerCase().split(";");
    const quality = Number(parameters.find((parameter) => parameter.trim().startsWith("q="))?.split("=")[1] ?? 1);
    return [token.trim(), Number.isFinite(quality) ? quality : 0];
  }));
  return supported
    .map((encoding, preference) => ({
      encoding,
      preference: supported.length - preference,
      quality: qualities.get(encoding) ?? qualities.get("*") ?? 0,
    }))
    .filter(({ quality }) => quality > 0)
    .sort((left, right) => right.quality - left.quality || right.preference - left.preference)[0]?.encoding ?? null;
}

async function sendStatic(request, response, sourceUrl, contentType) {
  const sourcePath = fileURLToPath(sourceUrl);
  const encoding = acceptedEncoding(request.headers["accept-encoding"], ["br", "gzip"]);
  const suffix = encoding === "br" ? ".br" : encoding === "gzip" ? ".gz" : "";
  let selectedPath = `${sourcePath}${suffix}`;
  let selectedEncoding = encoding;
  let metadata;
  try {
    metadata = await stat(selectedPath);
  } catch {
    selectedPath = sourcePath;
    selectedEncoding = null;
    try {
      metadata = await stat(selectedPath);
    } catch {
      response.writeHead(404, { "Content-Type": "text/plain" });
      response.end("Not found");
      return;
    }
  }

  const headers = {
    "Content-Type": contentType,
    "Content-Length": metadata.size,
    "Cache-Control": "public, max-age=31536000, immutable",
  };
  if (selectedEncoding) headers["Content-Encoding"] = selectedEncoding;
  response.writeHead(200, headers);
  createReadStream(selectedPath).pipe(response);
}

const pageRoutes = [
  { prefix: "/react/", label: "React", renderer: "react", stack: true },
  { prefix: "/jstachio/", label: "JStachio", renderer: "jstachio", exportHint: true },
  { prefix: "/jte/", label: "jte", renderer: "jte", exportHint: true },
  { prefix: "/thymeleaf/", label: "Thymeleaf", renderer: "thymeleaf", exportHint: true },
  { prefix: "/compose-wasm/", label: "Compose Wasm", target: "wasm", exportHint: true },
  { prefix: "/compose-js/", label: "Compose JS", target: "js", exportHint: true },
  { prefix: "/compose-wasm-table-unchecked/", label: "table-unchecked Compose Wasm", target: "wasm", unchecked: true },
  { prefix: "/compose-js-table-unchecked/", label: "table-unchecked Compose JS", target: "js", unchecked: true },
];

export function createBenchmarkServer() {
  const server = createServer(async (request, response) => {
    response.setHeader("Cross-Origin-Opener-Policy", "same-origin");
    response.setHeader("Cross-Origin-Embedder-Policy", "require-corp");
    response.setHeader("Cross-Origin-Resource-Policy", "same-origin");
    const url = new URL(request.url, `http://${request.headers.host}`);
    const pathname = url.pathname;

    if (pathname.startsWith("/dist/")) {
      const isJs = pathname.endsWith(".js") || pathname.endsWith(".mjs");
      await sendStatic(request, response, new URL(pathname.slice("/dist/".length), reactDistribution), isJs ? "text/javascript" : "application/octet-stream");
      return;
    }

    if (pathname.startsWith("/static/")) {
      const isJs = pathname.endsWith(".js") || pathname.endsWith(".mjs");
      const isWasm = pathname.endsWith(".wasm");
      const relative = pathname.slice("/static/".length);
      await sendStatic(request, response, new URL(relative, staticDirectory), isJs ? "text/javascript" : isWasm ? "application/wasm" : "application/octet-stream");
      return;
    }

    if (pathname === "/styles.css") {
      response.writeHead(200, { "Content-Type": "text/css" });
      response.end(`
        /* Minimal benchmark styling; matches the upstream table affordance. */
        .preloadicon { display: none; }
        .glyphicon-remove::before { content: "⨯"; }
        .items {
          margin: 1em 0;
          padding: 0;
          display: flex;
          flex-wrap: wrap;
          gap: 2px;
        }
        .items > * {
          display: flex;
          align-items: center;
          justify-content: center;
          width: 3em;
          height: 2em;
          margin: 0;
          padding: 0;
          background: #eee;
        }
      `);
      return;
    }

    if (pathname === "/app.css" || pathname === "/theme.js" || pathname === "/assets/klibs-logo.svg") {
      const contentType = pathname.endsWith(".css")
        ? "text/css"
        : pathname.endsWith(".js") ? "text/javascript" : "image/svg+xml";
      await sendStatic(request, response, new URL(pathname.slice(1), staticDirectory), contentType);
      return;
    }

    const route = pageRoutes.find(route => pathname.startsWith(route.prefix));
    if (route) {
      const scenario = pathname.replace(route.prefix, "");
      try {
        let html;
        if (["thymeleaf", "jstachio", "jte"].includes(route.renderer)) {
          html = await readFile(new URL(`build/ssr-html/${scenario}.${route.renderer}.html`, repositoryRoot), "utf8");
        } else {
          const file = route.renderer === "react"
            ? `build/react-html/${scenario}.body.html`
            : `build/ssr-html/${scenario}${documentScenarios.has(scenario) ? ".body" : ""}.html`;
          const bodyHtml = await readFile(new URL(file, repositoryRoot), "utf8");
          const data = fixtures[scenario] || {};
          html = route.renderer === "react"
            ? renderReactDocument({ scenario, bodyHtml, data, scriptUrl: manifest.reactHydrated[scenario] })
            : renderComposeDocument({ scenario, bodyHtml, data, target: route.target,
                assetScenario: route.unchecked ? `${scenario}-table-unchecked` : scenario });
        }
        response.writeHead(200, { "Content-Type": "text/html; charset=UTF-8", "Cache-Control": "no-store" });
        response.end(html);
      } catch (error) {
        const detail = route.stack ? `\n${error.stack}`
          : route.exportHint ? "\nRun ./gradlew exportSsrHtml first." : "";
        response.writeHead(500, { "Content-Type": "text/plain" });
        response.end(`Error rendering ${route.label} scenario: ${error.message}${detail}`);
      }
      return;
    }

    // Fixture API
    if (pathname.startsWith("/api/fixtures/")) {
      const scenario = pathname.replace("/api/fixtures/", "");
      response.writeHead(200, { "Content-Type": "application/json" });
      response.end(JSON.stringify(fixtures[scenario] || {}));
      return;
    }

    // Health check
    if (pathname === "/health") {
      response.writeHead(200, { "Content-Type": "text/plain" });
      response.end("OK");
      return;
    }

    response.writeHead(404, { "Content-Type": "text/plain" });
    response.end("Not found");
  });

  return server;
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const server = createBenchmarkServer();
  server.listen(port, "127.0.0.1", () => {
    console.log(`Benchmark server listening on http://127.0.0.1:${port}`);
  });
}
