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

DEPENDENCIES="${2:-${COMPOSE_DEPENDENCIES_PATH:-}}"
if [[ -z "$DEPENDENCIES" ]]; then DEPENDENCIES="$(read_local_property compose.dependencies.path)"; fi

if [[ -f "$CHECKOUT/html/settings.gradle.kts" ]]; then
  CHECKOUT="$CHECKOUT/html"
fi

if [[ ! -x "$CHECKOUT/gradlew" ]]; then
  echo "Compose HTML checkout is unavailable: $CHECKOUT" >&2
  exit 1
fi

rm -rf "$REPO_ROOT/build/compose-m2"
mkdir -p "$REPO_ROOT/build/compose-m2"

if [[ -n "$DEPENDENCIES" && -d "$DEPENDENCIES" ]]; then
  DEPENDENCIES="$(cd "$DEPENDENCIES" && pwd -P)"
  echo "Staging modified dependencies from: $DEPENDENCIES"

  # 1. If pre-built m2 artifacts exist, copy them
  if [[ -d "$DEPENDENCIES/m2" ]]; then
    cp -R "$DEPENDENCIES/m2/"* "$REPO_ROOT/build/compose-m2/" 2>/dev/null || true
  elif [[ -d "$DEPENDENCIES/androidx" || -d "$DEPENDENCIES/org" ]]; then
    cp -R "$DEPENDENCIES/"* "$REPO_ROOT/build/compose-m2/" 2>/dev/null || true
  fi

  # Compose HTML artifacts belong to the selected checkout and are published below.
  # A dependency bundle may contain cached copies, but they must not override that checkout.
  rm -rf "$REPO_ROOT/build/compose-m2/org/jetbrains/compose/html"

  # 2. If Gradle subprojects exist (e.g. collection, runtime), build and publish if not already present
  for SUB in "collection" "runtime"; do
    if [[ -d "$DEPENDENCIES/$SUB" && -f "$DEPENDENCIES/$SUB/build.gradle.kts" ]]; then
      if ! find "$REPO_ROOT/build/compose-m2" -name "*$SUB*" 2>/dev/null | grep -q .; then
        echo "Building and publishing dependency: $SUB"
        "$REPO_ROOT/gradlew" -p "$DEPENDENCIES/$SUB" publishToMavenLocal \
          -Dmaven.repo.local="$REPO_ROOT/build/compose-m2" \
          -I "$REPO_ROOT/tools/dependencies.init.gradle" \
          --no-configuration-cache --console=plain -q || true
      fi
    fi
  done

  CUSTOM_DEPENDENCIES_FILE="$(mktemp)"
  find "$REPO_ROOT/build/compose-m2" -type f \( -name '*.pom' -o -name '*.module' \) -print0 |
    while IFS= read -r -d '' metadata; do
      version_dir="${metadata%/*}"
      module_dir="${version_dir%/*}"
      group_dir="${module_dir%/*}"
      group="${group_dir#"$REPO_ROOT/build/compose-m2/"}"
      group="${group//\//.}"
      case "$group" in
        org.jetbrains.compose.html|org.jetbrains.compose.html.*) continue ;;
      esac
      printf '%s:%s:%s\n' "$group" "${module_dir##*/}" "${version_dir##*/}"
    done | LC_ALL=C sort -u > "$CUSTOM_DEPENDENCIES_FILE"
  CUSTOM_DEPENDENCY_COUNT="$(wc -l < "$CUSTOM_DEPENDENCIES_FILE" | tr -d ' ')"
  echo "$CUSTOM_DEPENDENCY_COUNT custom dependencies found"
  sed 's/^/  /' "$CUSTOM_DEPENDENCIES_FILE"
  rm -f "$CUSTOM_DEPENDENCIES_FILE"
fi

SUBSET_BUILD="$CHECKOUT/kotlinx-browser-common-subset"
if [[ ! -f "$SUBSET_BUILD/settings.gradle.kts" ]]; then
  echo "Compose HTML browser subset build is unavailable: $SUBSET_BUILD" >&2
  exit 1
fi

"$CHECKOUT/gradlew" -p "$SUBSET_BUILD" \
  publishToMavenLocal \
  -I "$REPO_ROOT/tools/dependencies.init.gradle" \
  -Dmaven.repo.local="$REPO_ROOT/build/compose-m2" \
  --console=plain

"$CHECKOUT/gradlew" -p "$CHECKOUT" \
  :internal-html-core-runtime-eap:publishToMavenLocal \
  :html-core-eap:publishToMavenLocal \
  :html-svg-eap:publishToMavenLocal \
  -I "$REPO_ROOT/tools/dependencies.init.gradle" \
  -Dmaven.repo.local="$REPO_ROOT/build/compose-m2" \
  -Pcompose.html.eap.enabled=true \
  --console=plain

node "$REPO_ROOT/tools/record-compose-build.mjs" "$CHECKOUT" "${DEPENDENCIES:-}"
