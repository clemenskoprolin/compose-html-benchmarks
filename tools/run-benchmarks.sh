#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
CHECKOUT=""
REVISION=""
ACTION="standard"
TARGET_SELECTION_MODE=""
# shellcheck source=common.sh
source "$SCRIPT_DIR/common.sh"

show_help() {
  cat << 'EOF'
Usage: ./tools/run-benchmarks.sh [ACTION] [OPTIONS]

Runs the standard SSR and browser suites, or the separate streaming SSR comparison. The command builds nothing.
It requires a current build/verification.json and aborts if the selected Compose checkout or the
benchmark sources changed since it was written. Use compose-version.sh to switch a
Compose checkout, or rebuild-benchmark.sh to rebuild and verify all artifacts.

SSR renders each workload to an HTML string in fresh JVM and Node processes; it does not involve a
browser or network. Browser hydration/startup loads the prepared server-rendered HTML in a real
browser, then measures client startup, DOM adoption or mounting, interaction, transfer, and memory.

Actions (default: --standard):
  --standard                Compare Compose with React in string SSR and browser hydration
  --ssr                     Run SSR companion reports (Compose JVM, React SSR, Thymeleaf, JStachio, jte)
  --streaming               Compare Compose, React and JVM templates streaming SSR (requires the new Compose API)
  --jvm                     Run JVM SSR report (includes Compose-only scenarios)
  --wasm                    Run Kotlin/Wasm SSR in Node (opt-in)
  --hydration, --browser    Run Playwright browser hydration benchmarks (Compose Wasm/JS vs React 19)

Shared configuration:
  --targets=LIST            Run only the selected targets (comma-separated; defaults depend on suite).
                            SSR/streaming: compose, react, thymeleaf, jstachio, jte
                            Browser: react, compose-wasm, compose-js
                            --ssr --targets=compose runs Compose only; skips React and templates.
                            --standard filters both SSR and browser runs to the selected targets.
  --scenarios=LIST          Comma-separated scenarios to run (default: all)
                            Available: tailwind-catalog, form-app, data-table, svg-dashboard,
                                       content-article, hydrate1k, update10th1k, reorder1k,
                                       filter-list
                            SSR only: preact-text, dynamic-text, dynamic-text-escaped,
                                      preact-search-results, preact-stack
                            Compose JVM/Wasm Node only: fractional-style, raw-text-style, raw-text-elements,
                                                style-heavy-catalog (--jvm or --wasm)
  --repetitions=N           Fresh process repetitions for both SSR and browser runs
                            (defaults: 3 for SSR, 2 for browser; this option overrides both)
  --warmups=N               Discarded renders/loads before sampling in each process
                            (defaults: 3 for SSR, 1 for browser; this option overrides both)
  --react-mode=MODE         React runtime: production (default) or development.
                            Both modes stay minified; development retains React diagnostics.
                            Prepared artifacts must match the selected mode.
  --compose-validation=MODE Compose JVM HTML validation: fast (default) or strict.
                            Strict enables COMPOSE_HTML_VALIDATE_STRICTLY; browser rendering stays fast.

Browser hydration/startup:
  --runs=N                  Measured page loads per browser process after warmup (default: 3)

Server-side rendering (SSR):
  --trials=N                Timed sample groups per server process after warmup (default: 15)
  --iterations=N            HTML renders performed and averaged in each trial (default: 100)
  --compose-string-rendering=MODE
                            Compose JVM renderer: keyed, unkeyed, direct, single-pass or streaming.
                            Wasm Node supports keyed and unkeyed. Default: keyed when supported.
  --stream-chunk-size=N     Compose streaming target size in UTF-16 units (default: 2048)
  --stream-delivery=MODE    buffered-utf8 (default): shared byte buffer; renderer: observe renderer writes
  --stream-buffer-size=N    Shared UTF-8 delivery buffer in bytes (default: 8192)

Help:
  -h, --help                Display this help message and exit

Examples:
  # Prepare and verify once, then measure as often as you like:
  ./tools/rebuild-benchmark.sh ../compose-html-ssr
  ./tools/run-benchmarks.sh

  # Run both SSR and hydration measurements for data-table:
  ./tools/run-benchmarks.sh --standard --scenarios=data-table

  # Run only SSR reports:
  ./tools/run-benchmarks.sh --ssr

  # Run only Compose JVM SSR (no React or template engines):
  ./tools/run-benchmarks.sh --ssr --targets=compose

  # Run the optional Kotlin/Wasm Node SSR report:
  ./tools/run-benchmarks.sh --wasm

  # Compare useful streamed output and include overall differences:
  ./tools/run-benchmarks.sh --streaming

  # Run only browser hydration checks:
  ./tools/run-benchmarks.sh --hydration
EOF
}

load_benchmark_env

while [[ $# -gt 0 ]]; do
  case "$1" in
    -h|--help) show_help; exit 0 ;;
    --standard|--ssr|--streaming|--jvm|--wasm) ACTION="${1#--}" ;;
    --hydration|--browser|--client) ACTION="hydration" ;;
    --repetitions=*) export BENCHMARK_REPETITIONS="${1#*=}" ;;
    --runs=*) export BENCHMARK_RUNS="${1#*=}" ;;
    --warmups=*) export BENCHMARK_WARMUPS="${1#*=}" ;;
    --trials=*) export BENCHMARK_TRIALS="${1#*=}" ;;
    --iterations=*) export BENCHMARK_ITERATIONS="${1#*=}" ;;
    --scenarios=*|--scenario=*) export BENCHMARK_SCENARIOS="${1#*=}" ;;
    --targets=*|--target=*) export BENCHMARK_TARGETS="${1#*=}"; TARGET_SELECTION_MODE="--strict" ;;
    --react-mode=*) export BENCHMARK_REACT_MODE="${1#*=}" ;;
    --compose-validation=*) export BENCHMARK_COMPOSE_VALIDATION="${1#*=}" ;;
    --compose-string-rendering=*) export BENCHMARK_COMPOSE_STRING_RENDERING="${1#*=}" ;;
    --stream-chunk-size=*) export BENCHMARK_STREAM_CHUNK_SIZE="${1#*=}" ;;
    --stream-delivery=*) export BENCHMARK_STREAM_DELIVERY="${1#*=}" ;;
    --stream-buffer-size=*) export BENCHMARK_STREAM_BUFFER_SIZE="${1#*=}" ;;
    *)
      echo "Unknown option: $1" >&2
      echo "" >&2
      show_help >&2
      exit 1
      ;;
  esac
  shift
done

target_selection="$(node "$SCRIPT_DIR/benchmark-targets.mjs" "$ACTION" "$TARGET_SELECTION_MODE")"
IFS='|' read -r JVM_TARGETS RUN_REACT BROWSER_TARGETS SELECTED_TARGETS <<< "$target_selection"
export BENCHMARK_TARGETS="$SELECTED_TARGETS"

validate_react_mode --react-mode

compose_validation_setting="${BENCHMARK_COMPOSE_VALIDATION-${COMPOSE_HTML_VALIDATE_STRICTLY:-false}}"
compose_validation_setting="${compose_validation_setting#"${compose_validation_setting%%[![:space:]]*}"}"
compose_validation_setting="${compose_validation_setting%"${compose_validation_setting##*[![:space:]]}"}"
compose_validation_setting="$(printf '%s' "$compose_validation_setting" | tr '[:upper:]' '[:lower:]')"
case "$compose_validation_setting" in
  ""|fast|false|0)
    export BENCHMARK_COMPOSE_VALIDATION=fast
    export COMPOSE_HTML_VALIDATE_STRICTLY=false
    ;;
  strict|true|1)
    export BENCHMARK_COMPOSE_VALIDATION=strict
    export COMPOSE_HTML_VALIDATE_STRICTLY=true
    ;;
  *)
    echo "--compose-validation must be fast or strict (COMPOSE_HTML_VALIDATE_STRICTLY must be true or false)." >&2
    exit 1
    ;;
esac

if [[ -n "${BENCHMARK_COMPOSE_STRING_RENDERING+x}" ]]; then
  case "$BENCHMARK_COMPOSE_STRING_RENDERING" in
    keyed|unkeyed|direct|single-pass|streaming) ;;
    *)
      echo "--compose-string-rendering must be keyed, unkeyed, direct, single-pass or streaming." >&2
      exit 1
      ;;
  esac
fi

if [[ "$ACTION" == "wasm" && "${BENCHMARK_COMPOSE_STRING_RENDERING:-keyed}" != "keyed" &&
      "${BENCHMARK_COMPOSE_STRING_RENDERING:-keyed}" != "unkeyed" ]]; then
  echo "Wasm SSR supports keyed or unkeyed string rendering." >&2
  exit 1
fi

if [[ "$ACTION" != "jvm" && "$ACTION" != "wasm" && -n "${BENCHMARK_SCENARIOS:-}" ]]; then
  IFS=',' read -r -a requested_scenarios <<< "$BENCHMARK_SCENARIOS"
  for scenario in "${requested_scenarios[@]}"; do
    scenario="${scenario#"${scenario%%[![:space:]]*}"}"
    scenario="${scenario%"${scenario##*[![:space:]]}"}"
    case "$scenario" in
      raw-text-style|raw-text-elements|style-heavy-catalog)
        echo "Scenario '$scenario' is JVM-only; use --jvm." >&2
        exit 1
        ;;
    esac
  done
fi


if [[ -z "$CHECKOUT" ]]; then CHECKOUT="${COMPOSE_HTML_CHECKOUT:-}"; fi
if [[ -z "$CHECKOUT" ]]; then CHECKOUT="$(read_local_property compose.html.checkout)"; fi
if [[ -z "$CHECKOUT" ]]; then CHECKOUT="$REPO_ROOT/../compose-html-ssr"; fi

DEPENDENCIES="${COMPOSE_DEPENDENCIES_PATH:-}"
if [[ -z "$DEPENDENCIES" ]]; then DEPENDENCIES="$(read_local_property compose.dependencies.path)"; fi

for setting in BENCHMARK_REPETITIONS BENCHMARK_RUNS BENCHMARK_WARMUPS BENCHMARK_TRIALS BENCHMARK_ITERATIONS BENCHMARK_STREAM_CHUNK_SIZE BENCHMARK_STREAM_BUFFER_SIZE; do
  value="${!setting:-}"
  pattern='^[1-9][0-9]*$'
  if [[ "$setting" == BENCHMARK_WARMUPS ]]; then pattern='^[0-9]+$'; fi
  if [[ -n "$value" && ! "$value" =~ $pattern ]]; then
    echo "--runs, --repetitions, --trials, --iterations, --stream-chunk-size, and --stream-buffer-size must be at least 1; --warmups must be 0 or greater." >&2
    exit 1
  fi
done

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
configure_compose_gradle

VERIFICATION_CHECK_ARGS=("--checkout=$CHECKOUT" "--revision=$REVISION" "--react-mode=$BENCHMARK_REACT_MODE")
if [[ -n "$DEPENDENCIES" ]]; then
  VERIFICATION_CHECK_ARGS+=("--dependencies=$DEPENDENCIES")
fi
node "$SCRIPT_DIR/verification.mjs" check "${VERIFICATION_CHECK_ARGS[@]}"

if [[ "$ACTION" == "jvm" ]]; then
  node "$SCRIPT_DIR/run-report.mjs" jvm "${GRADLE_COMPOSE_ARGS[@]}" -q
elif [[ "$ACTION" == "streaming" ]]; then
  node "$SCRIPT_DIR/run-report.mjs" streaming "${GRADLE_COMPOSE_ARGS[@]}" -q
elif [[ "$ACTION" == "wasm" ]]; then
  node "$REPO_ROOT/harness/browser/wasm-ssr-comparison.mjs"
elif [[ "$ACTION" == "ssr" ]]; then
  if [[ -n "$JVM_TARGETS" ]]; then
    node "$SCRIPT_DIR/run-report.mjs" jvm "${GRADLE_COMPOSE_ARGS[@]}" -q
  fi
  if [[ "$RUN_REACT" == "true" ]]; then
    node "$REPO_ROOT/harness/browser/ssr-comparison.mjs"
  fi
elif [[ "$ACTION" == "hydration" ]]; then
  BENCHMARK_TARGETS="$BROWSER_TARGETS" node "$REPO_ROOT/harness/browser/hydration-benchmark.mjs"
elif [[ "$ACTION" == "standard" ]]; then
  if [[ -n "$JVM_TARGETS" ]]; then
    node "$SCRIPT_DIR/run-report.mjs" jvm "${GRADLE_COMPOSE_ARGS[@]}" -q
  fi
  if [[ "$RUN_REACT" == "true" ]]; then
    node "$REPO_ROOT/harness/browser/ssr-comparison.mjs" --defer-table
  fi
  if [[ -n "$BROWSER_TARGETS" ]]; then
    BENCHMARK_TARGETS="$BROWSER_TARGETS" node "$REPO_ROOT/harness/browser/hydration-benchmark.mjs"
  fi
  if [[ "$RUN_REACT" == "true" && -n "$JVM_TARGETS" ]]; then
    node "$REPO_ROOT/harness/browser/ssr-comparison.mjs" --print-table
  fi
fi
printf '%s\n' "Benchmark suite complete. Each run is archived in results/runs; latest/ contains the latest reports."
