#!/usr/bin/env bash
# Shared setup for the preparation and measurement commands.
# The caller defines SCRIPT_DIR, REPO_ROOT and CHECKOUT.

load_benchmark_env() {
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
  if [[ -f "$ENV_FILE" ]]; then
    # This file is intentionally ignored by Git; see the tracked .example file.
    # Sourced env file provides defaults without overwriting variables already set in caller environment.
    pre_env=$(mktemp)
    # `declare -x` would create function-local variables when sourced here.
    # Restore with `export` so caller values survive after this function returns.
    export -p | sed 's/^declare -x /export /' > "$pre_env"
    set -a
    # shellcheck disable=SC1090
    source "$ENV_FILE"
    # shellcheck disable=SC1090
    source "$pre_env"
    rm -f "$pre_env"
    set +a
  fi
}

read_local_property() {
  [[ -f "$REPO_ROOT/local.properties" ]] || return 0
  awk -v key="$1" 'index($0, key "=") == 1 { print substr($0, length(key) + 2); exit }' \
    "$REPO_ROOT/local.properties"
}

validate_react_mode() {
  export BENCHMARK_REACT_MODE="${BENCHMARK_REACT_MODE:-production}"
  if [[ "$BENCHMARK_REACT_MODE" != "production" && "$BENCHMARK_REACT_MODE" != "development" ]]; then
    echo "$1 must be production or development." >&2
    exit 1
  fi
}

configure_compose_gradle() {
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
}
