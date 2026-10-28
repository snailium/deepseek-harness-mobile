#!/usr/bin/env python3
"""Refresh the pinned copy of the harness's session-event vocabulary.

The harness generates its own vocabulary — `KNOWN_SESSION_EVENT_TYPES` in
`packages/core/session/src/known-event-types.ts` — and verifies it fresh in its own CI. This
script copies that list into the test resources, so the client can be checked against it without
a harness checkout being present.

Usage:
    scripts/refresh-event-vocabulary.py                       # the installed dsh-session package
    scripts/refresh-event-vocabulary.py <known-event-types.ts> # a checkout's source file
    scripts/refresh-event-vocabulary.py --harness <dir>      # <dir>/packages/core/session/src/...

The file it writes is `core/src/test/resources/harness-event-vocabulary.txt`. The harness
version and commit in its header are taken from the file's own path and, for a checkout, from
`git -C <dir> rev-parse HEAD`; for an installed package, from that package's `package.json`.
"""

from __future__ import annotations

import argparse
import json
import pathlib
import re
import subprocess
import sys

REPO_ROOT = pathlib.Path(__file__).resolve().parent.parent
TARGET = REPO_ROOT / "core" / "src" / "test" / "resources" / "harness-event-vocabulary.txt"

HEADER = """\
# The harness's durable session-event vocabulary.
#
# A copy of `KNOWN_SESSION_EVENT_TYPES` from
# `packages/core/session/src/known-event-types.ts`, pinned to the harness release named below.
# The harness generates that list (`pnpm run gen-persistence-catalog`) and verifies it fresh in
# its own CI, so the authoritative list is a file, not a guess about what a type might mean.
#
# harness: {version}
# commit:  {commit} ({branch})
#
# `EventTypeClassificationTest` compares the client's classification against this list in both
# directions, and compares this list against a live harness checkout or install when it can find
# one — a harness that has grown since this copy was taken fails the test with a note to refresh
# it, which is how a new event type announces itself before it reaches a phone.
#
# Refresh with: scripts/refresh-event-vocabulary.py [path/to/known-event-types.ts]
"""


def declared_types(source: pathlib.Path) -> list[str]:
    """The `KNOWN_SESSION_EVENT_TYPES` members, read out of the generated file.

    Read lexically rather than by parsing TypeScript: the file is generated, so its shape is
    stable, and every member sits alone on its line in quotes. Anything looser would match the
    `MESSAGE_PROJECTION_EVENT_TYPES` set further down the same file, so the scan stops at the
    end of the first `new Set([...])`.
    """
    text = source.read_text(encoding="utf-8")
    start = text.index("KNOWN_SESSION_EVENT_TYPES")
    open_at = text.index("new Set([", start)
    close = text.index("])", open_at)
    return re.findall(r"'([^']+)'", text[open_at:close])


def installed_package() -> tuple[pathlib.Path, str, str]:
    """The `known-event-types.js` of the newest installed dsh-session package, with its version.

    A machine often carries several harness versions at once — an old one under another node
    version, the current one nested inside `dsh` — so the candidates are ranked by the package's
    own version rather than by where they sit on disk. Picking the wrong one would pin the
    vocabulary to a harness the phone is not talking to.
    """
    roots = [
        pathlib.Path.home() / ".nvm" / "versions" / "node",
        pathlib.Path("/usr/lib/node_modules"),
        pathlib.Path("/usr/local/lib/node_modules"),
    ]
    candidates: list[pathlib.Path] = []
    for root in roots:
        if not root.is_dir():
            continue
        if root.name == "node":  # the nvm layout holds one install per node version
            candidates.extend(sorted(root.glob("*/lib/node_modules/@deepseek-ai/dsh-session")))
            # `dsh-session` is a dependency of `dsh`, so npm usually nests it one level deeper.
            candidates.extend(sorted(root.glob("*/lib/node_modules/@deepseek-ai/dsh/node_modules/@deepseek-ai/dsh-session")))
        else:
            candidates.append(root / "@deepseek-ai" / "dsh-session")
            candidates.append(root / "@deepseek-ai" / "dsh" / "node_modules" / "@deepseek-ai" / "dsh-session")

    found = []
    for package in candidates:
        source = package / "lib" / "types" / "known-event-types.js"
        if not source.is_file():
            continue
        manifest = json.loads((package / "package.json").read_text(encoding="utf-8"))
        version = manifest.get("version", "unknown")
        found.append((version_key(version), version, source))
    if not found:
        raise SystemExit(
            "no installed @deepseek-ai/dsh-session found; pass the file or a harness checkout"
        )
    _, version, source = max(found, key=lambda entry: entry[0])
    return source, version, "release tag"


def version_key(version: str) -> tuple:
    """A comparable key for a semver-ish string; a prerelease sorts below its release."""
    core, _, prerelease = version.partition("-")
    numbers = []
    for part in core.split("."):
        try:
            numbers.append(int(part))
        except ValueError:
            numbers.append(0)
    while len(numbers) < 3:
        numbers.append(0)
    # A prerelease ("0.2.1-rc.2") is older than the same release without one.
    return (numbers[0], numbers[1], numbers[2], 0 if prerelease else 1, prerelease)


def checkout_version(harness: pathlib.Path) -> str:
    try:
        out = subprocess.run(
            ["git", "-C", str(harness), "describe", "--tags", "--abbrev=0"],
            capture_output=True, text=True, check=True,
        )
        return out.stdout.strip().lstrip("v")
    except Exception:
        return "unknown"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", nargs="?", help="the generated known-event-types file")
    parser.add_argument("--harness", help="a harness checkout (its packages/core/session/src is used)")
    args = parser.parse_args()

    if args.harness:
        harness = pathlib.Path(args.harness).resolve()
        source = harness / "packages" / "core" / "session" / "src" / "known-event-types.ts"
        if not source.is_file():
            raise SystemExit(f"no known-event-types.ts under {harness}")
        version = checkout_version(harness)
        commit = subprocess.run(
            ["git", "-C", str(harness), "rev-parse", "HEAD"], capture_output=True, text=True, check=True,
        ).stdout.strip()
        branch = subprocess.run(
            ["git", "-C", str(harness), "rev-parse", "--abbrev-ref", "HEAD"],
            capture_output=True, text=True, check=True,
        ).stdout.strip()
    elif args.source:
        source = pathlib.Path(args.source).resolve()
        version, commit, branch = "unknown", "unknown", "unknown"
    else:
        source, version, branch = installed_package()
        commit = f"dsh-v{version}"

    types = declared_types(source)
    if len(types) < 40:
        raise SystemExit(f"read only {len(types)} types from {source}; the scan is probably wrong")

    TARGET.parent.mkdir(parents=True, exist_ok=True)
    TARGET.write_text(HEADER.format(version=version, commit=commit, branch=branch) + "\n".join(types) + "\n", encoding="utf-8")
    print(f"wrote {len(types)} types to {TARGET.relative_to(REPO_ROOT)}")
    print(f"  from:  {source}")
    print(f"  harness: {version}  commit: {commit}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
