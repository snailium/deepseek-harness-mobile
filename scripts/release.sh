#!/usr/bin/env bash
#
# Build and publish a signed release APK for the snailium fork.
#
# Usage:  scripts/release.sh <version>        e.g. scripts/release.sh 0.2.0-dsh.0.1.6
#
# The version is `<fork>-dsh.<harness>`: our own release count, then the harness line the app
# speaks. The harness half is a **compatibility claim, not a counter** — `0.1.7` means "for DSH
# 0.1.7", and it must match the upstream baseline the tree is actually built on. This script checks
# that, because the failure is silent: a wrong harness half produces an APK that installs fine and
# misinforms everyone who reads the version.
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
  echo "usage: $(basename "$0") <version>   e.g. 0.2.0-dsh.0.1.6" >&2
  exit 2
fi
if [[ ! "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+-dsh\.[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "version must look like 0.2.0-dsh.0.1.6 (fork line -dsh. harness line)" >&2
  exit 2
fi

# `version.properties` is the fork's own record of the scheme — see its header for why the values
# live outside `app/build.gradle.kts`. The argument's harness half must match it, and that file in
# turn must match upstream's compatibility table below.
cd "$REPO_ROOT"
if [[ ! -f version.properties ]]; then
  echo "version.properties is missing — see FORK-NOTES for the scheme" >&2
  exit 2
fi
FILE_HARNESS="$(sed -n 's/^harnessVersion=//p' version.properties | tr -d '[:space:]')"
VERSION_HARNESS="${VERSION##*-dsh.}"
if [[ -n "$FILE_HARNESS" && "$VERSION_HARNESS" != "$FILE_HARNESS" ]]; then
  echo "harness half is $VERSION_HARNESS but version.properties says $FILE_HARNESS" >&2
  exit 2
fi

# The harness half must name the harness baseline this tree is built on. Read from
# `docs/COMPATIBILITY.md`, which is upstream's own record of "this app version targets this harness
# version" — not from the app's version number, which is a different thing entirely (0.11.7 is the
# app; the harness it targets is 0.1.6).
#
# The *latest* row is used rather than a row keyed on the current app version, because upstream does
# not add a row for every release — 0.11.6 and 0.11.7 have none, and their harness target is
# unchanged from 0.11.5's.
HARNESS_BASELINE="$(git show upstream/main:docs/COMPATIBILITY.md 2>/dev/null \
  | grep -E '^\| [0-9]+\.[0-9]+\.[0-9]+ ' | head -1 | awk -F'|' '{ print $3 }')"
# `0.1.6-alpha.1 + master …` -> `0.1.6`: the name carries the release line, not the prerelease tag.
HARNESS_LINE="$(echo "$HARNESS_BASELINE" | grep -oE '[0-9]+\.[0-9]+\.[0-9]+' | head -1)"
if [[ -n "$HARNESS_LINE" && "$VERSION_HARNESS" != "$HARNESS_LINE" ]]; then
  echo "harness half is $VERSION_HARNESS but upstream targets harness $HARNESS_LINE" >&2
  echo "the harness half is a compatibility claim — it names the harness this build is for," >&2
  echo "not a counter that advances with our own releases" >&2
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

# Record what was shipped, so the weekly CI job can tell whether `master` has moved since. Without
# this the workflow would either rebuild an identical APK every Sunday or need a tag lookup it
# cannot do before it has decided whether to run.
FORK_PART="${VERSION%%-dsh.*}"
sed -i "s/^forkVersion=.*/forkVersion=$FORK_PART/" version.properties
sed -i "s/^releasedFrom=.*/releasedFrom=$(git rev-parse HEAD)/" version.properties
echo "recorded: forkVersion=$FORK_PART releasedFrom=$(git rev-parse --short HEAD)"

echo
echo "published: $NAME"
echo "url:       http://192.168.111.90:7777/$NAME"
echo
echo "The APK server serves from inside apk/, so the URL carries no /apk/ segment."
