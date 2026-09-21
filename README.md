# Compose HTML benchmark suite

Reproducible benchmarks for Compose HTML (JVM SSR, browser JS and Wasm), React 19,
and a Thymeleaf SSR reference. The suite uses fixtures across nine
workloads covering markup-heavy pages, hydration and DOM updates.

## Quick start

Requires JDK 21+ and Node.js 22+.

```bash
npm ci
npx playwright install chromium

# Compose HTML SSR/hydration currently requires an external EAP source checkout.
git clone https://github.com/JetBrains/compose-multiplatform.git ../compose-html-ssr

# Select that checkout, build all artifacts and verify equivalent output.
./tools/compose-version.sh ../compose-html-ssr

# Run the complete SSR and browser suite without rebuilding.
./tools/run-benchmarks.sh
```

Re-run `./tools/compose-version.sh --prepare` after a relevant change, or pass a
different checkout to compare another Compose revision.

For a fast correctness smoke test:

```bash
BENCHMARK_MEMORY=false ./tools/run-benchmarks.sh \
  --repetitions=1 --warmups=0 --runs=1 --trials=1 --iterations=1
```

## Common commands

```bash
# Preview prepared output.
./preview react tailwind-catalog
./preview compose-wasm data-table
./preview thymeleaf form-app

# Select a subset. See --help for every option.
./tools/run-benchmarks.sh --scenarios=data-table --targets=react,compose-wasm
./tools/run-benchmarks.sh --help

# Tests and individual harnesses.
npm test
npm run verify:ssr
npm run benchmark:ssr
npm run benchmark:browser
```

Copy `tools/benchmarks.env.example` to the ignored `tools/benchmarks.env` to keep
machine-specific defaults. CLI options and environment variables can override it.

## What is measured

The main runner produces two complementary comparisons:

| Suite | Targets | Measures |
| --- | --- | --- |
| Browser | React, Compose JS, Compose Wasm | Fresh-context startup, FCP, app readiness, interaction, transfer size and memory |
| SSR | React and Compose JVM | Renderer initialization/first render and repeated string rendering |

Browser runs use independent browser processes, disabled network caches, ac 4× CPU / 40 ms network-latency profile by
default. Both frameworks receive pre-exported HTML from the same server, so browser
TTFB is not an SSR comparison. Interaction timing includes DOM completion plus two animation frames.

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
| `preact-text` | SSR | Rendering 1,000 repeated text-heavy components |
| `preact-search-results` | SSR | Rendering product results and a link-heavy footer |
| `preact-stack` | SSR | Rendering ten component branches nested 1,000 levels deep |

The Thymeleaf `preact-stack` variant is globally reported as `SKIPPED` because its
renderer does not finish in a reasonable time.

## Repository layout

```text
implementations/   Compose, React and Thymeleaf implementations
workloads/         Shared models and fixtures
harness/           Browser and JVM runners plus correctness checks
tools/             Build, selection and benchmark orchestration
results/           Run archives and result documentation
```

Third-party attributions are listed in
[`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).
