#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
CHECKOUT=""
PREPARE=0
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
  echo "Usage: ./tools/compose-version.sh <compose-checkout>"
  echo "       ./tools/compose-version.sh --prepare"
  echo ""
  echo "The checkout may be the compose-multiplatform repository or its html directory."
  echo "Selecting a checkout prepares both SSR and browser hydration artifacts by default."
  echo "Use --prepare without a checkout to prepare the currently selected version again."
}

if [[ -f "$ENV_FILE" ]]; then
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
    --prepare)
      PREPARE=1
      shift
      ;;
    --*)
      echo "Unknown option: $1" >&2
      show_help >&2
      exit 1
      ;;
    *)
      if [[ -n "$CHECKOUT" ]]; then
        echo "Only one Compose checkout may be selected." >&2
        show_help >&2
        exit 1
      fi
      CHECKOUT="$1"
      shift
      ;;
  esac
done

read_local_property() {
  [[ -f "$REPO_ROOT/local.properties" ]] || return 0
  awk -v key="$1" 'index($0, key "=") == 1 { print substr($0, length(key) + 2); exit }' \
    "$REPO_ROOT/local.properties"
}

SELECTING_CHECKOUT=0
if [[ -n "$CHECKOUT" ]]; then
  SELECTING_CHECKOUT=1
elif [[ $PREPARE -eq 1 ]]; then
  CHECKOUT="${COMPOSE_HTML_CHECKOUT:-}"
  if [[ -z "$CHECKOUT" ]]; then CHECKOUT="$(read_local_property compose.html.checkout)"; fi
  if [[ -z "$CHECKOUT" ]]; then CHECKOUT="$REPO_ROOT/../compose-html-ssr"; fi
else
  show_help >&2
  exit 1
fi

if [[ -f "$CHECKOUT/html/settings.gradle.kts" ]]; then CHECKOUT="$CHECKOUT/html"; fi
if [[ ! -f "$CHECKOUT/settings.gradle.kts" ]]; then
  echo "Compose HTML checkout is unavailable: $CHECKOUT" >&2
  exit 1
fi
CHECKOUT="$(cd "$CHECKOUT" && pwd -P)"
ACTUAL_REVISION="$(git -C "$CHECKOUT" rev-parse HEAD)"

if [[ $SELECTING_CHECKOUT -eq 1 ]]; then
  REVISION="$ACTUAL_REVISION"
else
  REVISION="${COMPOSE_HTML_REVISION:-}"
  if [[ -z "$REVISION" ]]; then REVISION="$(read_local_property compose.html.revision)"; fi
  if [[ -z "$REVISION" ]]; then
    REVISION="$(node -e 'const fs = require("fs"); console.log(JSON.parse(fs.readFileSync(process.argv[1])).revision)' \
      "$REPO_ROOT/tools/compose-source.lock.json")"
  fi
  if [[ "$ACTUAL_REVISION" != "$REVISION" ]]; then
    echo "Compose revision mismatch: expected $REVISION, found $ACTUAL_REVISION." >&2
    echo "Select this checkout again to prepare its current revision:" >&2
    echo "  ./tools/compose-version.sh $CHECKOUT" >&2
    exit 1
  fi
fi

if [[ $SELECTING_CHECKOUT -eq 1 ]]; then
  LOCAL_PROPERTIES="$REPO_ROOT/local.properties"
  TEMP_PROPERTIES="$(mktemp)"
  trap 'rm -f "$TEMP_PROPERTIES"' EXIT
  if [[ -f "$LOCAL_PROPERTIES" ]]; then
    awk '!/^compose\.html\.(checkout|revision)=/' "$LOCAL_PROPERTIES" > "$TEMP_PROPERTIES"
  fi
  printf 'compose.html.checkout=%s\ncompose.html.revision=%s\n' "$CHECKOUT" "$REVISION" >> "$TEMP_PROPERTIES"
  mv "$TEMP_PROPERTIES" "$LOCAL_PROPERTIES"
  trap - EXIT
fi

echo ""
echo "Selected Compose HTML checkout: $CHECKOUT"
echo "Selected Compose HTML revision: $REVISION"
echo "Preparing SSR and browser hydration artifacts..."

cd "$REPO_ROOT"
export COMPOSE_HTML_CHECKOUT="$CHECKOUT"
GRADLE_COMPOSE_ARGS=("-Pcompose.html.checkout=$CHECKOUT"
  -Pcompose.html.use.included.build=false
  -Pcompose.html.eap.kotlinx-browser-common-subset.version=0.0.1-SNAPSHOT
  --no-configuration-cache --console=plain)
ALL_SCENARIOS=(tailwind-catalog form-app data-table svg-dashboard content-article hydrate1k update10th1k reorder1k filter-list)

node tools/check-dependencies.mjs

# Build external sources once, then use exactly those local artifacts everywhere.
"$SCRIPT_DIR/refresh-compose-html-artifacts.sh" "$CHECKOUT"
"$REPO_ROOT/gradlew" :harness:jvm:exportFixtures :harness:jvm:exportSsrHtml "${GRADLE_COMPOSE_ARGS[@]}" -q
node "$REPO_ROOT/implementations/react/scripts/build.mjs"

CLIENT_TASKS=()
for SCENARIO in "${ALL_SCENARIOS[@]}"; do
  CLIENT_TASKS+=(":implementations:compose:browser:$SCENARIO:jsBrowserDistribution"
    ":implementations:compose:browser:$SCENARIO:wasmJsBrowserDistribution")
done
"$REPO_ROOT/gradlew" "${CLIENT_TASKS[@]}" "${GRADLE_COMPOSE_ARGS[@]}" -q
COMPOSE_CLIENT_SCENARIOS="$(IFS=,; echo "${ALL_SCENARIOS[*]}")" node "$SCRIPT_DIR/package-compose-clients.mjs"

node "$REPO_ROOT/harness/browser/verify-ssr-workloads.mjs"
node "$SCRIPT_DIR/verification.mjs" write "--checkout=$CHECKOUT" "--revision=$REVISION"

echo ""
if [[ $SELECTING_CHECKOUT -eq 1 ]]; then
  echo "Compose HTML version switched and prepared successfully."
else
  echo "Compose HTML version prepared successfully."
fi
