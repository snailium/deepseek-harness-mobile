#!/usr/bin/env bash
#
# Build and publish a signed APK to the workspace's apk/ directory, shared over dsh-share-plus.
#
# Usage:  scripts/release.sh              build, stamp, publish
#         scripts/release.sh --dry-run    show the version it would use, build nothing
#
# **This path does not version.** It is the everyday channel: every change worth putting on a phone
# gets a build, and most of those changes are not releases. The version is taken as-is from
# `version.properties` and the timestamp goes into the *filename*, so a phone sees a build number
# that never lies about where the code came from, and the release history stays a list of releases
# rather than a list of afternoons.
#
# The versioned channel is the weekly GitHub release — see RELEASE-POLICY.md. It is the only thing
# that writes a new `forkVersion`, and it does so once a week.
#
# What it does: runs the test suite, builds a signed APK, verifies the signature, copies it into the
# served `apk/` directory as `<version>-<timestamp>.apk`, and prints the download URL. It refuses to
# build if the keystore is missing rather than silently producing an unsigned APK that Android will
# not upgrade over — upstream's build script falls back to unsigned, which is the right default for
# them and the wrong one for us.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORKSPACE_ROOT="$(cd "$REPO_ROOT/.." && pwd)"

DRY_RUN=false
for arg in "$@"; do
  case "$arg" in
    --dry-run) DRY_RUN=true ;;
    -h|--help) sed -n '3,20p' "${BASH_SOURCE[0]}" | sed 's/^# \?//'; exit 0 ;;
    *) echo "unknown argument: $arg (try --help)" >&2; exit 2 ;;
  esac
done

# The version is read, never written. This path publishes builds, not releases — bumping here is
# what produced six "releases" in one afternoon, five of them a few lines apart.
cd "$REPO_ROOT"
if [[ ! -f version.properties ]]; then
  echo "version.properties is missing — see FORK-NOTES for the scheme" >&2
  exit 2
fi
FORK_HALF="$(sed -n 's/^forkVersion=//p' version.properties | tr -d '[:space:]')"
VERSION_HARNESS="$(sed -n 's/^harnessVersion=//p' version.properties | tr -d '[:space:]')"
if [[ -z "$FORK_HALF" || -z "$VERSION_HARNESS" ]]; then
  echo "version.properties must define forkVersion and harnessVersion" >&2
  exit 2
fi
VERSION="$FORK_HALF-dsh.$VERSION_HARNESS"

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

if $DRY_RUN; then
  echo "would publish: dsh-mobile-$VERSION+$(date +%Y%m%d-%H%M%S).apk"
  echo "version:       $VERSION (from version.properties)"
  echo "commit:        $(git rev-parse --short HEAD)"
  exit 0
fi

# Every step runs here, on this machine.
#
# These used to be split: the secret-free steps (tests, lint) ran on the .101 build box through an
# isolated container, because four cores made them the slow part of a release. That machine is now
# this one — twelve threads — and the same suite takes about a minute and a half, which is faster
# than syncing the tree to another host ever was. The remote wrapper and its image are gone.

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

# The timestamp is the only thing that distinguishes one build from the next, so it goes in the
# filename. `apk/` is a flat directory served as-is, and sorting it by name sorts it by time.
STAMP="$(date +%Y%m%d-%H%M%S)"
SHORT_SHA="$(git rev-parse --short HEAD)"
NAME="dsh-mobile-$VERSION+$STAMP.apk"
mkdir -p "$WORKSPACE_ROOT/apk"
cp "$APK" "$WORKSPACE_ROOT/apk/$NAME"

# Copying into `apk/` *is* publishing. `dsh-share-plus` holds that directory as a read-only
# mount, so the file is downloadable the moment it lands here — nothing is uploaded, registered,
# or restarted, and the listing picks it up on the next request.
#
# The mount also does the two things a plain `python3 -m http.server` could not: it types `.apk`
# as `application/vnd.android.package-archive` with `Content-Disposition: attachment`, so a phone
# offers to install instead of downloading a blob, and it rebinds at boot, so a link survives a
# dsh restart. The mount itself is configured once on the Plugins page and never by this script.
#
# The `+` in the filename must be escaped for a URL; the server decodes it back to `+`.
SHARE_HOST="${DSH_SHARE_HOST:-192.168.111.90}"
SHARE_PORT="${DSH_SHARE_PORT:-3082}"
URL="http://$SHARE_HOST:$SHARE_PORT/apk/${NAME//+/%2B}"

echo
echo "published: $NAME"
echo "url:       $URL"
echo "version:   $VERSION  (unchanged — this path does not version; only the weekly release advances it)"
echo "commit:    $SHORT_SHA"

# The URL *is* the deliverable, so it gets checked the way the signature does. This is not
# confirming the copy — the copy is already done — it is catching the two ways the link can be
# dead anyway, which need different advice: the dsh-web process being down (nothing listening)
# versus the mount being absent (server up, path 404). A build is not handed over with a link
# that does not answer.
CODE="$(curl -s -o /dev/null -w '%{http_code}' --max-time 15 "$URL" 2>/dev/null || true)"
if [ "$CODE" = "200" ]; then
  echo "served:    200 (dsh-share-plus :$SHARE_PORT)"
else
  echo "served:    ${CODE:-no response}  <- WARNING: the URL does not answer, so nobody can download this"
  echo "           the file is on disk at $WORKSPACE_ROOT/apk/$NAME"
  echo "           if dsh-web is down, start it; if it is up, dsh-share-plus is missing an 'apk'"
  echo "           mount for $WORKSPACE_ROOT/apk/ (Plugins -> dsh-share-plus -> Configure)"
fi
echo
echo "The APK server serves the workspace root, so the /apk/ segment is part of the URL."
echo "The versioned channel is the weekly GitHub release; see RELEASE-POLICY.md."
