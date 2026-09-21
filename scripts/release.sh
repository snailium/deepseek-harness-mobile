#!/usr/bin/env bash
#
# Build and publish a signed release APK for the snailium fork.
#
# Usage:  scripts/release.sh <version>        e.g. scripts/release.sh 0.1.0-dsh.0.1.5
#
# The version is this fork's own line plus the upstream harness line it speaks, as
# `<fork>-dsh.<upstream>` — see the KDoc in app/build.gradle.kts for why versionCode comes from the
# fork half alone.
#
# What it does: runs the test suite, builds a signed release APK, copies it into the served `apk/`
# directory with a timestamped name, and prints the download URL. It refuses to build if the
# keystore is missing rather than silently producing an unsigned APK that Android will not upgrade
# over — upstream's build script falls back to unsigned, which is the right default for them and
# the wrong one for us.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORKSPACE_ROOT="$(cd "$REPO_ROOT/.." && pwd)"

VERSION="${1:-}"
if [[ -z "$VERSION" ]]; then
  echo "usage: $(basename "$0") <version>   e.g. 0.1.0-dsh.0.1.5" >&2
  exit 2
fi
if [[ ! "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+-dsh\.[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "version must look like 0.1.0-dsh.0.1.5 (fork line -dsh. upstream line)" >&2
  exit 2
fi

# --- toolchain ---------------------------------------------------------------------------------
export JAVA_HOME="${JAVA_HOME:-$WORKSPACE_ROOT/.toolchain/jdk-17.0.20.1+1}"
export ANDROID_HOME="${ANDROID_HOME:-$WORKSPACE_ROOT/.sdk}"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$WORKSPACE_ROOT/.gradle-home}"

# --- signing -----------------------------------------------------------------------------------
KEYSTORE="${DSH_KEYSTORE:-$WORKSPACE_ROOT/.keys/dsh-mobile-release.jks}"
PASSWORD_FILE="$WORKSPACE_ROOT/.keys/keystore.password"
if [[ ! -f "$KEYSTORE" ]]; then
  echo "keystore not found at $KEYSTORE — refusing to build an unsigned release" >&2
  echo "generate one with keytool, or point DSH_KEYSTORE at an existing .jks" >&2
  exit 1
fi
if [[ -z "${DSH_KEYSTORE_PASSWORD:-}" && -f "$PASSWORD_FILE" ]]; then
  DSH_KEYSTORE_PASSWORD="$(cat "$PASSWORD_FILE")"
fi
if [[ -z "${DSH_KEYSTORE_PASSWORD:-}" ]]; then
  echo "no keystore password: set DSH_KEYSTORE_PASSWORD or write $PASSWORD_FILE" >&2
  exit 1
fi
export DSH_KEYSTORE="$KEYSTORE"
export DSH_KEYSTORE_PASSWORD
export DSH_KEY_ALIAS="${DSH_KEY_ALIAS:-dsh-mobile}"
export DSH_KEY_PASSWORD="${DSH_KEY_PASSWORD:-$DSH_KEYSTORE_PASSWORD}"
export DSH_VERSION_NAME="$VERSION"

cd "$REPO_ROOT"

echo "==> tests"
./gradlew :core:test :mock-harness:test :app:testDebugUnitTest --console=plain

echo "==> lint"
./gradlew :app:lintDebug --console=plain

echo "==> signed release APK ($VERSION)"
./gradlew :app:assembleRelease --console=plain

APK="app/build/outputs/apk/release/app-release.apk"
if [[ ! -f "$APK" ]]; then
  echo "expected $APK, which the build did not produce" >&2
  exit 1
fi

# An unsigned APK installs but cannot upgrade a signed one, and the failure only shows up at
# install time on the phone — so check here, where the cause is still visible.
if ! "$ANDROID_HOME"/build-tools/*/apksigner verify --print-certs "$APK" >/dev/null 2>&1; then
  echo "APK is not signed — check DSH_KEYSTORE / DSH_KEYSTORE_PASSWORD / DSH_KEY_ALIAS" >&2
  exit 1
fi

STAMP="$(date +%Y%m%d-%H%M%S)"
NAME="dsh-mobile-$VERSION-$STAMP.apk"
mkdir -p "$WORKSPACE_ROOT/apk"
cp "$APK" "$WORKSPACE_ROOT/apk/$NAME"

echo
echo "published: $NAME"
echo "url:       http://192.168.111.90:7777/$NAME"
echo
echo "The APK server serves from inside apk/, so the URL carries no /apk/ segment."
