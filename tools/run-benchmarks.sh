#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
CHECKOUT=""
REVISION=""
ACTION="all"
ENV_FILE="${BENCHMARK_ENV_FILE:-}"
if [[ -z "$ENV_FILE" ]]; then
  if [[ -f "$SCRIPT_DIR/benchmarks.env" ]]; then
    ENV_FILE="$SCRIPT_DIR/benchmarks.env"
  elif [[ -f "$REPO_ROOT/benchmarks.env" ]]; then
    ENV_FILE="$REPO_ROOT/benchmarks.env"
  else
    ENV_FILE="$SCRIPT_DIR/benchmarks.env"
  fi
fi

show_help() {
  cat << 'EOF'
Usage: ./tools/run-benchmarks.sh [ACTION] [OPTIONS]

Runs the complete benchmark suite: SSR reports and browser hydration. The command builds nothing.
It requires a current build/verification.json and aborts if the selected Compose checkout or the
benchmark sources changed since it was written. Use compose-version.sh to select and prepare a
Compose checkout before measuring.

SSR renders each workload to an HTML string in fresh JVM and Node processes; it does not involve a
browser or network. Browser hydration/startup loads the prepared server-rendered HTML in a real
browser, then measures client startup, DOM adoption or mounting, interaction, transfer, and memory.

Actions (default: --all):
  --all                     Run complete benchmark suite (SSR reports + browser hydration)
  --ssr                     Run SSR companion reports (Compose JVM vs React SSR)
  --hydration, --browser    Run Playwright browser hydration benchmarks (Compose Wasm/JS vs React 19)

Shared configuration:
  --scenarios=LIST          Comma-separated scenarios to run (default: all)
                            Available: tailwind-catalog, form-app, data-table, svg-dashboard,
                                       content-article, hydrate1k, update10th1k, reorder1k, filter-list
  --repetitions=N           Fresh process repetitions for both SSR and browser runs (default: 3)
  --warmups=N               Discarded renders/loads before sampling in each process
                            (defaults: 3 for SSR, 1 for browser; this option overrides both)

Browser hydration/startup:
  --targets=LIST            Comma-separated browser targets (default: react,compose-wasm,compose-js)
                            Available: react, compose-wasm, compose-js
  --runs=N                  Measured page loads per browser process after warmup (default: 3)

Server-side rendering (SSR):
  --trials=N                Timed sample groups per server process after warmup (default: 15)
  --iterations=N            HTML renders performed and averaged in each trial (default: 100)

Help:
  -h, --help                Display this help message and exit

Examples:
  # Prepare and verify once, then measure as often as you like:
  ./tools/compose-version.sh ../compose-html-ssr
  ./tools/run-benchmarks.sh

  # Run both SSR and hydration measurements for data-table:
  ./tools/run-benchmarks.sh --all --scenarios=data-table

  # Run only SSR reports:
  ./tools/run-benchmarks.sh --ssr

  # Run only browser hydration checks:
  ./tools/run-benchmarks.sh --hydration
EOF
}

if [[ -f "$ENV_FILE" ]]; then
  # This file is intentionally ignored by Git; see the tracked .example file.
  # Sourced env file provides defaults without overwriting variables already set in caller environment.
  pre_env=$(mktemp)
  export -p > "$pre_env"
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  # shellcheck disable=SC1090
  source "$pre_env"
  rm -f "$pre_env"
  set +a
fi

while [[ $# -gt 0 ]]; do
  case "$1" in
    -h|--help)
      show_help
      exit 0
      ;;
    --all)
      ACTION="all"
      shift
      ;;
    --ssr)
      ACTION="ssr"
      shift
      ;;
    --hydration|--browser|--client)
      ACTION="hydration"
      shift
      ;;
    --repetitions=*) export BENCHMARK_REPETITIONS="${1#*=}"; shift ;;
    --runs=*)
      export BENCHMARK_RUNS="${1#*=}"
      shift
      ;;
    --warmups=*)
      export BENCHMARK_WARMUPS="${1#*=}"
      shift
      ;;
    --trials=*)
      export BENCHMARK_TRIALS="${1#*=}"
      shift
      ;;
    --iterations=*)
      export BENCHMARK_ITERATIONS="${1#*=}"
      shift
      ;;
    --scenarios=*|--scenario=*)
      export BENCHMARK_SCENARIOS="${1#*=}"
      shift
      ;;
    --targets=*|--target=*)
      export BENCHMARK_TARGETS="${1#*=}"
      shift
      ;;
    *)
      echo "Unknown option: $1" >&2
      echo "" >&2
      show_help >&2
      exit 1
      ;;
  esac
done

read_local_property() {
  [[ -f "$REPO_ROOT/local.properties" ]] || return 0
  awk -v key="$1" 'index($0, key "=") == 1 { print substr($0, length(key) + 2); exit }' \
    "$REPO_ROOT/local.properties"
}

if [[ -z "$CHECKOUT" ]]; then CHECKOUT="${COMPOSE_HTML_CHECKOUT:-}"; fi
if [[ -z "$CHECKOUT" ]]; then CHECKOUT="$(read_local_property compose.html.checkout)"; fi
if [[ -z "$CHECKOUT" ]]; then CHECKOUT="$REPO_ROOT/../compose-html-ssr"; fi

DEPENDENCIES="${COMPOSE_DEPENDENCIES_PATH:-}"
if [[ -z "$DEPENDENCIES" ]]; then DEPENDENCIES="$(read_local_property compose.dependencies.path)"; fi

if [[ -n "${BENCHMARK_REPETITIONS:-}" && ! "${BENCHMARK_REPETITIONS}" =~ ^[1-9][0-9]*$ ]] || \
   [[ -n "${BENCHMARK_RUNS:-}" && ! "${BENCHMARK_RUNS}" =~ ^[1-9][0-9]*$ ]] || \
   [[ -n "${BENCHMARK_WARMUPS:-}" && ! "${BENCHMARK_WARMUPS}" =~ ^[0-9]+$ ]] || \
   [[ -n "${BENCHMARK_TRIALS:-}" && ! "${BENCHMARK_TRIALS}" =~ ^[1-9][0-9]*$ ]] || \
   [[ -n "${BENCHMARK_ITERATIONS:-}" && ! "${BENCHMARK_ITERATIONS}" =~ ^[1-9][0-9]*$ ]]; then
  echo "--runs, --repetitions, --trials, and --iterations must be at least 1; --warmups must be 0 or greater." >&2
  exit 1
fi

cd "$REPO_ROOT"
if [[ -f "$CHECKOUT/html/settings.gradle.kts" ]]; then CHECKOUT="$CHECKOUT/html"; fi
if [[ ! -f "$CHECKOUT/settings.gradle.kts" ]]; then
  echo "Missing Compose checkout: $CHECKOUT. See README.md clean-machine setup." >&2
  exit 1
fi
if [[ -z "$REVISION" ]]; then
  REVISION="${COMPOSE_HTML_REVISION:-}"
fi
if [[ -z "$REVISION" ]]; then
  REVISION="$(read_local_property compose.html.revision)"
fi
if [[ -z "$REVISION" ]]; then
  REVISION="$(node -p 'JSON.parse(require("fs").readFileSync("tools/compose-source.lock.json")).revision')"
fi
export COMPOSE_HTML_CHECKOUT="$CHECKOUT"
export COMPOSE_DEPENDENCIES_PATH="$DEPENDENCIES"
SELECTED_COMPOSE_VERSION="$(awk -F= '$1 == "compose.version" { print $2; exit }' "$CHECKOUT/gradle.properties")"
if [[ -z "$SELECTED_COMPOSE_VERSION" ]]; then
  echo "Could not determine Compose version from: $CHECKOUT/gradle.properties" >&2
  exit 1
fi
GRADLE_COMPOSE_ARGS=("-Pcompose.html.checkout=$CHECKOUT"
  "-Pcompose.version=$SELECTED_COMPOSE_VERSION"
  -Pcompose.html.use.included.build=false
  -Pcompose.html.eap.kotlinx-browser-common-subset.version=0.0.1-SNAPSHOT
  -I "$SCRIPT_DIR/dependencies.init.gradle"
  --no-configuration-cache --console=plain)

VERIFICATION_CHECK_ARGS=("--checkout=$CHECKOUT" "--revision=$REVISION")
if [[ -n "$DEPENDENCIES" ]]; then
  VERIFICATION_CHECK_ARGS+=("--dependencies=$DEPENDENCIES")
fi
node "$SCRIPT_DIR/verification.mjs" check "${VERIFICATION_CHECK_ARGS[@]}"


if [[ "$ACTION" == "ssr" ]]; then
  node "$SCRIPT_DIR/run-jvm-report.mjs" "${GRADLE_COMPOSE_ARGS[@]}" -q
  node "$REPO_ROOT/harness/browser/ssr-comparison.mjs"
elif [[ "$ACTION" == "hydration" ]]; then
  node "$REPO_ROOT/harness/browser/hydration-benchmark.mjs"
elif [[ "$ACTION" == "all" ]]; then
  node "$SCRIPT_DIR/run-jvm-report.mjs" "${GRADLE_COMPOSE_ARGS[@]}" -q
  node "$REPO_ROOT/harness/browser/ssr-comparison.mjs" --defer-table
  node "$REPO_ROOT/harness/browser/hydration-benchmark.mjs"
  node "$REPO_ROOT/harness/browser/ssr-comparison.mjs" --print-table
fi
printf '%s\n' "Benchmark suite complete. Each run is archived in results/runs; latest/ contains the latest reports."
