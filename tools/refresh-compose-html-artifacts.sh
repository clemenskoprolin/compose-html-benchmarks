#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

ENV_FILE="${BENCHMARK_ENV_FILE:-}"
if [[ -z "$ENV_FILE" ]]; then
  if [[ -f "$REPO_ROOT/tools/benchmarks.env" ]]; then
    ENV_FILE="$REPO_ROOT/tools/benchmarks.env"
  elif [[ -f "$REPO_ROOT/benchmarks.env" ]]; then
    ENV_FILE="$REPO_ROOT/benchmarks.env"
  fi
fi
if [[ -n "$ENV_FILE" && -f "$ENV_FILE" ]]; then
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

CHECKOUT="${1:-${COMPOSE_HTML_CHECKOUT:-}}"
if [[ -z "$CHECKOUT" ]]; then CHECKOUT="$(read_local_property compose.html.checkout)"; fi
if [[ -z "$CHECKOUT" ]]; then CHECKOUT="$REPO_ROOT/../compose-html-ssr"; fi

if [[ -f "$CHECKOUT/html/settings.gradle.kts" ]]; then
  CHECKOUT="$CHECKOUT/html"
fi

if [[ ! -x "$CHECKOUT/gradlew" ]]; then
  echo "Compose HTML checkout is unavailable: $CHECKOUT" >&2
  exit 1
fi

rm -rf "$REPO_ROOT/build/compose-m2"
mkdir -p "$REPO_ROOT/build/compose-m2"

"$CHECKOUT/gradlew" -p "$CHECKOUT" \
  :kotlinx-browser-common-subset:publishToMavenLocal \
  :internal-html-core-runtime-eap:publishToMavenLocal \
  :html-core-eap:publishToMavenLocal \
  :html-svg-eap:publishToMavenLocal \
  -Dmaven.repo.local="$REPO_ROOT/build/compose-m2" \
  -Pcompose.html.eap.enabled=true \
  --console=plain

node "$REPO_ROOT/tools/record-compose-build.mjs" "$CHECKOUT"
