# Compose HTML benchmark suite

Reproducible benchmarks for Compose HTML (JVM SSR, browser JS and Wasm), React 19,
and JVM SSR references using Thymeleaf, JStachio and jte. The suite uses shared fixtures for
markup-heavy pages, hydration and DOM updates.

## Quick start

Requires JDK 21+ and Node.js 22+.

```bash
npm ci
npx playwright install chromium

# Compose HTML SSR/hydration currently requires an external EAP source checkout.
git clone https://github.com/JetBrains/compose-multiplatform.git ../compose-html-ssr

# Build all artifacts and verify equivalent output once.
./tools/rebuild-benchmark.sh ../compose-html-ssr

# Run the standard Compose vs React SSR and browser suite without rebuilding.
./tools/run-benchmarks.sh
```

Use `./tools/compose-version.sh <checkout>` to switch revisions quickly. It rebuilds
Compose artifacts but reuses fixtures and React and skips SSR equivalence checks.
Re-selecting the same unchanged checkout reuses its prepared SSR and browser output.
Unchanged browser bundles also keep their packaged and compressed files.
It runs the full rebuild automatically when benchmark inputs change or are missing;
use `./tools/rebuild-benchmark.sh` to force one.

## Custom dependencies

Attach or replace the custom dependency bundle:

```bash
./tools/compose-version.sh --dependencies /path/to/dependency-bundle
```

Passing a bundle root containing `html/` and `m2/`, `runtime/`, or `collection/`
selects the dependencies automatically.

Use `./tools/compose-version.sh --status` to inspect the selection and
`./tools/compose-version.sh --clear-dependencies` to return to upstream dependencies.

For a fast correctness smoke test:

```bash
BENCHMARK_MEMORY=false ./tools/run-benchmarks.sh \
  --repetitions=1 --warmups=0 --runs=1 --trials=1 --iterations=1
```

## Common commands

```bash
# Preview prepared output.
./tools/preview react tailwind-catalog
./tools/preview compose-wasm data-table
./tools/preview thymeleaf form-app
./tools/preview jstachio data-table
./tools/preview jte content-article

# Select a subset. See --help for every option.
./tools/run-benchmarks.sh --scenarios=data-table --targets=react,compose-wasm
./tools/run-benchmarks.sh --help

# Run only Compose JVM SSR, skipping React and the JVM template engines.
./tools/run-benchmarks.sh --ssr --targets=compose

# Run only Compose JVM streaming SSR.
./tools/run-benchmarks.sh --streaming --targets=compose

# Tests and individual harnesses.
npm test
npm run verify:ssr
npm run benchmark:ssr
npm run benchmark:streaming
npm run benchmark:browser
```

To profile JVM SSR, select targets and scenarios in `SsrProfiler.kt`, then use the
**Profile JVM SSR** run configuration.

Copy `tools/benchmarks.env.example` to the ignored `tools/benchmarks.env` to keep
machine-specific defaults. CLI options and environment variables can override it.

## What is measured

The default (`--standard`) runs string SSR for Compose JVM and React, plus browser
benchmarks for React, Compose JS and Compose Wasm.
Use `--ssr` or `--streaming` to include all JVM template engines.

`--targets` restricts the selected suite further.

| Suite | Targets | Measures |
| --- | --- | --- |
| Browser | React, Compose JS, Compose Wasm | Fresh-context startup, FCP, app readiness, interaction, transfer size and memory |
| SSR (in `--standard`) | React and Compose JVM | Renderer initialization/first render and repeated string rendering; allocation where supported |
| SSR (`--ssr`) | React, Compose JVM, Thymeleaf, JStachio and jte | Same string rendering and allocation measurements |
| Wasm SSR (`--wasm`) | Compose Wasm on Node | First render and repeated string rendering |
| Streaming SSR (`--streaming`) | React, Compose JVM, Thymeleaf, JStachio and jte | First 8 KiB, completion, output bytes and JVM allocation; `--stream-delivery=renderer` adds first chunk, content and chunk count. |

## Results

Each run is archived under `results/runs/`; `results/runs/latest/` mirrors the most
recent completed report of each kind. Metadata is included per run.

## Workloads

| Workload | Suite | What it does |
| --- | --- | --- |
| `tailwind-catalog` | Browser + SSR | Class-heavy catalog with filters and project cards |
| `form-app` | Browser + SSR | Form controls, validation state and accessibility attributes |
| `data-table` | Browser + SSR | Structured rows, sorting metadata, selection and pagination markup |
| `svg-dashboard` | Browser + SSR | SVG charts, paths, gradients and namespaced attributes |
| `content-article` | Browser + SSR | Long-form semantic content with mixed block types |
| `hydrate1k` | Browser | Hydration and identity preservation of 1,000 keyed table rows |
| `update10th1k` | Browser | Updating every tenth row in a 1,000-row table |
| `reorder1k` | Browser | Moving keyed rows within a 1,000-row table |
| `filter-list` | Browser | Removing a large contiguous range from a 1,000-item list |
| `preact-text` | SSR | Rendering 1,000 repeated components with constant prose |
| `dynamic-text` | SSR | Same 1,000-block layout with distinct prose supplied by the fixture |
| `dynamic-text-escaped` | SSR | Runtime prose containing HTML-sensitive characters and Unicode |
| `preact-search-results` | SSR | Rendering product results and a link-heavy footer |
| `preact-stack` | SSR | Rendering ten component branches nested 1,000 levels deep |
| `fractional-style` | Compose only | 256 elements with fractional units, opacity and custom properties; alternating elements reset priority and remove a property |

`fractional-style` measures Compose's style builder. It has no React result or Compose-vs-React comparison.

Some engines might skip some work loads, if they are not suitable.

## Repository layout

```text
implementations/   Compose, React, Thymeleaf, JStachio and jte implementations
workloads/         Shared models and fixtures
harness/           Browser and JVM runners plus correctness checks
tools/             Build, selection and benchmark orchestration
results/           Run archives and result documentation
```

Buffered JVM streaming sends small writes through a shared 8,192-character buffer before
UTF-8 encoding and the configurable byte delivery buffer. Template presentation views are
built once per render, inside timing; Thymeleaf binds the fixture directly to its context.
Literal template markup stays compact so formatting does not add output whitespace.

The harness runners collect measurements and generate reports afterward.
They use the following shared components:

| File | Responsibility |
| --- | --- |
| `harness/browser/runtime.mjs` | Workload groups, environment defaults, settings and fresh Node worker ordering |
| `harness/browser/*-report.mjs` | Result aggregation and console/Markdown/JSON presentation |
| `harness/browser/run-record.mjs` | Provenance and completed-run archives |
| `SsrWorkloads.kt` (JVM analysis) | JVM scenarios, fixtures and renderers shared by measurement, export, preview and analysis |
| `BenchmarkSupport.kt` (JVM analysis) | Settings, statistics, allocation counters and fresh JVM workers |
| `tools/common.sh` | Environment precedence, local selection and Gradle arguments |
| `tools/run-report.mjs` | JVM and combined streaming report coordination |

Third-party attributions are listed in
[`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).
