#!/usr/bin/env python3
"""Union-merge conflicted `strings.xml` files: keep every `<string>` from both sides.

String catalogues conflict on almost every upstream merge, and the answer is always the same —
upstream's new keys and ours are disjoint, so the correct resolution is the union, never a side.
Picking a side silently deletes translations; leaving it to hand-editing across eleven locales is
how one locale ends up missing a key and the build fails on `MissingTranslation`.

Usage (from the repo root, with conflicts staged by git but unresolved):

    python3 scripts/union-strings.py

It resolves every conflicted `strings.xml`, preserving each side's ordering, and stages them.
Anchoring on the base version's line order means a key that both sides added is deduplicated
deterministically rather than appearing twice.
"""
import re
import subprocess
import sys

CONFLICT = "<<<<<<<"
MARK_END = ">>>>>>>"


def conflicted_string_files() -> list[str]:
    out = subprocess.run(
        ["git", "diff", "--name-only", "--diff-filter=U"],
        capture_output=True, text=True, check=True,
    ).stdout.split()
    files = [f for f in out if f.endswith("strings.xml")]
    if not files:
        print("no conflicted strings.xml files", file=sys.stderr)
    return files


def parse_conflicts(text: str) -> tuple[list[str], list[tuple[list[str], list[str]]]]:
    """Split a conflicted file into (clean_lines, [(ours, theirs), ...])."""
    clean: list[str] = []
    blocks: list[tuple[list[str], list[str]]] = []
    i = 0
    lines = text.splitlines()
    while i < len(lines):
        line = lines[i]
        if line.startswith(CONFLICT):
            ours: list[str] = []
            theirs: list[str] = []
            i += 1
            while i < len(lines) and not lines[i].startswith("======="):
                ours.append(lines[i])
                i += 1
            i += 1  # the ======= marker
            while i < len(lines) and not lines[i].startswith(MARK_END):
                theirs.append(lines[i])
                i += 1
            i += 1  # the >>>>>>> marker
            blocks.append((ours, theirs))
        else:
            clean.append(line)
            i += 1
    return clean, blocks


def key_of(line: str) -> str | None:
    """The `name="…"` of a `<string>` line, or None for anything else."""
    m = re.search(r'<string\s+name="([^"]+)"', line)
    return m.group(1) if m else None


def merge_block(ours: list[str], theirs: list[str]) -> list[str]:
    """Ours first, then their lines we do not already have.

    Deduplication is by **resource key**, not by line text. Both sides frequently add the same key
    at different positions in the file, and two identical lines are then not adjacent — comparing
    whole lines misses it and the merge produces a duplicate `<string name="…">`, which the
    resource merger rejects outright ("Found item String/x more than one time"). That is a build
    failure, not a warning, so it has to be caught here rather than by compiling.

    Non-`<string>` lines (comments, blank lines) are compared as-is, since they carry no key.
    """
    seen = {key_of(line) for line in ours if key_of(line)}
    merged = list(ours)
    for line in theirs:
        k = key_of(line)
        if k:
            if k in seen:
                continue
            seen.add(k)
            merged.append(line)
        elif line.strip() and line.strip() not in {l.strip() for l in merged}:
            merged.append(line)
    return merged


def main() -> int:
    files = conflicted_string_files()
    for path in files:
        raw = open(path, encoding="utf-8").read()
        clean, blocks = parse_conflicts(raw)
        # Rebuild: walk the original line list again, substituting merged blocks in place so the
        # clean lines stay in their original positions relative to the conflicts.
        result: list[str] = []
        bi = 0
        i = 0
        lines = raw.splitlines()
        while i < len(lines):
            if lines[i].startswith(CONFLICT):
                ours: list[str] = []
                theirs: list[str] = []
                i += 1
                while i < len(lines) and not lines[i].startswith("======="):
                    ours.append(lines[i]); i += 1
                i += 1
                while i < len(lines) and not lines[i].startswith(MARK_END):
                    theirs.append(lines[i]); i += 1
                i += 1
                result.extend(merge_block(ours, theirs))
                bi += 1
            else:
                result.append(lines[i]); i += 1
        open(path, "w", encoding="utf-8").write("\n".join(result) + "\n")
        print(f"union-merged {path} ({bi} conflict block(s))")
    if files:
        subprocess.run(["git", "add"] + files, check=True)
        print(f"staged {len(files)} file(s)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
