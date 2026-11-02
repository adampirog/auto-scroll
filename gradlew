#!/usr/bin/env bash
# Minimal Ubuntu/Linux Gradle bootstrap. Pinned to the version in wrapper.properties.
# No prebuilt Gradle or Android SDK binaries are included in this source archive.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VERSION="9.3.1"
GRADLE_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"
INSTALL_DIR="$GRADLE_HOME/wrapper/dists/autoscroll/gradle-$VERSION"
BINARY="$INSTALL_DIR/gradle-$VERSION/bin/gradle"

if [[ ! -x "$BINARY" ]]; then
  command -v curl >/dev/null || { echo "curl is required" >&2; exit 1; }
  command -v unzip >/dev/null || { echo "unzip is required" >&2; exit 1; }
  command -v sha256sum >/dev/null || { echo "sha256sum is required" >&2; exit 1; }
  tmp="$(mktemp -d)"
  trap 'rm -rf "$tmp"' EXIT
  url="https://services.gradle.org/distributions/gradle-$VERSION-bin.zip"
  echo "Fetching Gradle $VERSION (first run only)..."
  curl --fail --location --silent --show-error --retry 3 "$url" -o "$tmp/gradle.zip"
  curl --fail --location --silent --show-error --retry 3 "$url.sha256" -o "$tmp/checksum"
  printf '%s  %s\n' "$(cat "$tmp/checksum")" "$tmp/gradle.zip" | sha256sum --check --status
  mkdir -p "$INSTALL_DIR"
  unzip -q "$tmp/gradle.zip" -d "$INSTALL_DIR"
  rm -rf "$tmp"
  trap - EXIT
fi
cd "$ROOT"
exec "$BINARY" "$@"
