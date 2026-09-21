#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
CHECKOUT=""
DEPENDENCIES=""
PREPARE=0
STATUS=0
SELECTING_CHECKOUT=0
SELECTING_DEPENDENCIES=0
CLEARING_DEPENDENCIES=0
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
Usage: ./tools/compose-version.sh <compose-checkout> [OPTIONS]
       ./tools/compose-version.sh --prepare
       ./tools/compose-version.sh --dependencies <path>
       ./tools/compose-version.sh --clear-dependencies
       ./tools/compose-version.sh --status

Options:
  <compose-checkout>        Compose multiplatform repository or html directory.
                            If an optimization directory (e.g. compose-wasm-optimized-v3)
                            is provided, both the html checkout and modified dependencies
                            are automatically detected.
  --dependencies <path>, --with-dependencies <path>
                            Attach modified dependencies from <path>. <path> may be an
                            optimization directory (with m2/ or runtime/ subprojects) or
                            a local Maven repository directory.
  --clear-dependencies, --without-dependencies, --reset-dependencies
                            Clear custom dependencies and revert to upstream dependencies.
  --prepare                 Re-prepare the currently selected Compose HTML checkout and
                            active modified dependencies.
  --status, --info          Display the currently selected checkout, revision, and dependencies.
  -h, --help                Show this help message.
EOF
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

read_local_property() {
  [[ -f "$REPO_ROOT/local.properties" ]] || return 0
  awk -v key="$1" 'index($0, key "=") == 1 { print substr($0, length(key) + 2); exit }' \
    "$REPO_ROOT/local.properties"
}

show_status() {
  local cur_checkout="${COMPOSE_HTML_CHECKOUT:-}"
  if [[ -z "$cur_checkout" ]]; then cur_checkout="$(read_local_property compose.html.checkout)"; fi
  local cur_rev="${COMPOSE_HTML_REVISION:-}"
  if [[ -z "$cur_rev" ]]; then cur_rev="$(read_local_property compose.html.revision)"; fi
  local cur_deps="${COMPOSE_DEPENDENCIES_PATH:-}"
  if [[ -z "$cur_deps" ]]; then cur_deps="$(read_local_property compose.dependencies.path)"; fi

  echo "Current Compose Configuration:"
  echo "  Compose checkout:    ${cur_checkout:-not set}"
  echo "  Compose revision:    ${cur_rev:-not set}"
  if [[ -n "$cur_deps" ]]; then
    echo "  Custom dependencies: $cur_deps"
    if [[ -d "$cur_deps/m2" ]]; then
      echo "  Artifacts found in:  $cur_deps/m2"
    fi
  else
    echo "  Custom dependencies: none (using upstream dependencies)"
  fi
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    -h|--help)
      show_help
      exit 0
      ;;
    --status|--info)
      STATUS=1
      shift
      ;;
    --prepare)
      PREPARE=1
      shift
      ;;
    --dependencies=*|--with-dependencies=*)
      DEPENDENCIES="${1#*=}"
      SELECTING_DEPENDENCIES=1
      shift
      ;;
    --dependencies|--with-dependencies)
      if [[ $# -lt 2 ]]; then
        echo "Error: $1 requires a path argument." >&2
        exit 1
      fi
      DEPENDENCIES="$2"
      SELECTING_DEPENDENCIES=1
      shift 2
      ;;
    --clear-dependencies|--without-dependencies|--reset-dependencies)
      CLEARING_DEPENDENCIES=1
      DEPENDENCIES=""
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
      SELECTING_CHECKOUT=1
      shift
      ;;
  esac
done

if [[ $STATUS -eq 1 ]]; then
  show_status
  exit 0
fi

# Auto-detect optimization bundle layout if a single checkout directory was passed
if [[ $SELECTING_CHECKOUT -eq 1 && $SELECTING_DEPENDENCIES -eq 0 && $CLEARING_DEPENDENCIES -eq 0 ]]; then
  candidate_deps="$(cd "$CHECKOUT" 2>/dev/null && pwd -P || echo "$CHECKOUT")"
  if [[ -d "$candidate_deps/html" && (-d "$candidate_deps/m2" || -d "$candidate_deps/runtime" || -d "$candidate_deps/collection") ]]; then
    DEPENDENCIES="$candidate_deps"
    SELECTING_DEPENDENCIES=1
    echo "Auto-detected modified dependencies bundle in: $candidate_deps"
  fi
fi

if [[ -z "$DEPENDENCIES" && $CLEARING_DEPENDENCIES -eq 0 ]]; then
  DEPENDENCIES="${COMPOSE_DEPENDENCIES_PATH:-}"
  if [[ -z "$DEPENDENCIES" ]]; then DEPENDENCIES="$(read_local_property compose.dependencies.path)"; fi
fi

if [[ -n "$DEPENDENCIES" ]]; then
  if [[ ! -d "$DEPENDENCIES" ]]; then
    echo "Error: Dependencies path does not exist: $DEPENDENCIES" >&2
    exit 1
  fi
  DEPENDENCIES="$(cd "$DEPENDENCIES" && pwd -P)"
fi

if [[ -z "$CHECKOUT" ]]; then
  CHECKOUT="${COMPOSE_HTML_CHECKOUT:-}"
  if [[ -z "$CHECKOUT" ]]; then CHECKOUT="$(read_local_property compose.html.checkout)"; fi
  if [[ -z "$CHECKOUT" ]]; then CHECKOUT="$REPO_ROOT/../compose-html-ssr"; fi
fi

if [[ $SELECTING_CHECKOUT -eq 0 && $SELECTING_DEPENDENCIES -eq 0 && $CLEARING_DEPENDENCIES -eq 0 && $PREPARE -eq 0 ]]; then
  show_help >&2
  exit 1
fi

if [[ -f "$CHECKOUT/html/settings.gradle.kts" ]]; then CHECKOUT="$CHECKOUT/html"; fi
if [[ ! -f "$CHECKOUT/settings.gradle.kts" ]]; then
  echo "Compose HTML checkout is unavailable: $CHECKOUT" >&2
  exit 1
fi
CHECKOUT="$(cd "$CHECKOUT" && pwd -P)"

ACTUAL_REVISION="$(git -C "$CHECKOUT" rev-parse HEAD 2>/dev/null || true)"
if [[ -z "$ACTUAL_REVISION" ]]; then
  if [[ -f "$CHECKOUT/../REFERENCE.json" ]]; then
    ACTUAL_REVISION="$(node -e 'try { console.log(JSON.parse(fs.readFileSync(process.argv[1])).commit || "") } catch {}' "$CHECKOUT/../REFERENCE.json" 2>/dev/null || true)"
  fi
fi
if [[ -z "$ACTUAL_REVISION" ]]; then
  ACTUAL_REVISION="custom-$(node -e 'const crypto = require("crypto"), fs = require("fs"); const s = fs.readFileSync(process.argv[1]); console.log(crypto.createHash("sha256").update(s).digest("hex").slice(0, 12))' "$CHECKOUT/settings.gradle.kts")"
fi

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

if [[ $SELECTING_CHECKOUT -eq 1 || $SELECTING_DEPENDENCIES -eq 1 || $CLEARING_DEPENDENCIES -eq 1 ]]; then
  LOCAL_PROPERTIES="$REPO_ROOT/local.properties"
  TEMP_PROPERTIES="$(mktemp)"
  trap 'rm -f "$TEMP_PROPERTIES"' EXIT
  if [[ -f "$LOCAL_PROPERTIES" ]]; then
    awk '!/^compose\.(html\.(checkout|revision)|dependencies\.path)=/' "$LOCAL_PROPERTIES" > "$TEMP_PROPERTIES"
  fi
  printf 'compose.html.checkout=%s\ncompose.html.revision=%s\n' "$CHECKOUT" "$REVISION" >> "$TEMP_PROPERTIES"
  if [[ -n "$DEPENDENCIES" ]]; then
    printf 'compose.dependencies.path=%s\n' "$DEPENDENCIES" >> "$TEMP_PROPERTIES"
  fi
  mv "$TEMP_PROPERTIES" "$LOCAL_PROPERTIES"
  trap - EXIT
fi

echo ""
echo "Selected Compose HTML checkout: $CHECKOUT"
echo "Selected Compose HTML revision: $REVISION"
if [[ -n "$DEPENDENCIES" ]]; then
  echo "Selected modified dependencies: $DEPENDENCIES"
else
  echo "Selected modified dependencies: none (using upstream dependencies)"
fi
echo "Preparing SSR and browser hydration artifacts..."

cd "$REPO_ROOT"
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
ALL_SCENARIOS=(tailwind-catalog form-app data-table svg-dashboard content-article hydrate1k update10th1k reorder1k filter-list)

node tools/check-dependencies.mjs

# Build external sources once, then use exactly those local artifacts everywhere.
REFRESH_LOG="$(mktemp)"
trap 'rm -f "$REFRESH_LOG"' EXIT
"$SCRIPT_DIR/refresh-compose-html-artifacts.sh" "$CHECKOUT" "${DEPENDENCIES:-}" | tee "$REFRESH_LOG"
CUSTOM_DEPENDENCY_COUNT="$(sed -n 's/^\([0-9][0-9]*\) custom dependencies found$/\1/p' "$REFRESH_LOG" | tail -n 1)"
CUSTOM_DEPENDENCY_COUNT="${CUSTOM_DEPENDENCY_COUNT:-0}"
rm -f "$REFRESH_LOG"
trap - EXIT
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

VERIFICATION_ARGS=("--checkout=$CHECKOUT" "--revision=$REVISION")
if [[ -n "$DEPENDENCIES" ]]; then
  VERIFICATION_ARGS+=("--dependencies=$DEPENDENCIES")
fi
node "$SCRIPT_DIR/verification.mjs" write "${VERIFICATION_ARGS[@]}"

echo ""
if [[ $SELECTING_CHECKOUT -eq 1 ]]; then
  echo "Compose HTML version switched and prepared successfully."
else
  echo "Compose HTML artifacts prepared successfully."
fi
if [[ -n "$DEPENDENCIES" ]]; then
  echo "Successfully imported $CUSTOM_DEPENDENCY_COUNT custom dependencies."
elif [[ $CLEARING_DEPENDENCIES -eq 1 ]]; then
  echo "Custom dependencies cleared successfully."
fi
