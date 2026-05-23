#!/usr/bin/env python3
"""Add a `done` marker column to every behavior table in the section matrix files.

Each section file (matrix-*/<N><letter>-*.md) holds one or more 6-column tables:
    class | behavior | required level | existing test | verdict | action
The e2e variant differs in labels but keeps verdict at column 5, action at 6.

This appends a 7th column `done`:
  - actionable rows (verdict implies work) -> ⬜
  - non-actionable rows (adequate/keep/none) -> —

Actionability is keyed off the verdict cell. Idempotent: a file whose first table
header already has a `done` column is skipped.

Run with --dry-run to print a per-file summary without writing.
"""
import glob
import os
import re
import sys

ACTIONABLE_KEYWORDS = ("missing", "inadequate", "wrong-level", "redundant", "orphan")
SEP_RE = re.compile(r"^\|[\s:|-]+\|\s*$")
TODO = "⬜"
NA = "—"


def content_cells(line):
    # "| a | b |" -> [" a ", " b "]
    parts = line.rstrip().split("|")
    return parts[1:-1]


def is_table_header(line):
    cells = content_cells(line)
    return len(cells) >= 6 and any("verdict" in c.lower() for c in cells)


def is_actionable(verdict):
    v = verdict.strip().lower()
    return any(k in v for k in ACTIONABLE_KEYWORDS)


def process_file(path, dry_run):
    with open(path, encoding="utf-8") as f:
        lines = f.readlines()

    out = []
    todo = na = 0
    skipped_already = False
    i = 0
    n = len(lines)
    while i < n:
        line = lines[i]
        stripped = line.rstrip("\n")
        if stripped.lstrip().startswith("|") and is_table_header(stripped):
            # already has a done column?
            cells = content_cells(stripped)
            if cells and cells[-1].strip().lower() == "done":
                skipped_already = True
                out.append(line)
                i += 1
                continue
            # header
            out.append(stripped.rstrip() + " done |\n")
            i += 1
            # separator
            if i < n and SEP_RE.match(lines[i].rstrip("\n")):
                out.append(lines[i].rstrip("\n").rstrip() + "---|\n")
                i += 1
            # data rows until table ends
            while i < n and lines[i].lstrip().startswith("|"):
                row = lines[i].rstrip("\n")
                cells = content_cells(row)
                if len(cells) >= 6:
                    verdict = cells[4]
                    if is_actionable(verdict):
                        marker = TODO
                        todo += 1
                    else:
                        marker = NA
                        na += 1
                    out.append(row.rstrip() + f" {marker} |\n")
                else:
                    out.append(lines[i])
                i += 1
        else:
            out.append(line)
            i += 1

    if skipped_already:
        return ("skip", 0, 0)

    if not dry_run:
        with open(path, "w", encoding="utf-8") as f:
            f.writelines(out)
    return ("done", todo, na)


def main():
    dry_run = "--dry-run" in sys.argv
    base = os.path.dirname(os.path.abspath(__file__))
    files = sorted(glob.glob(os.path.join(base, "matrix-*", "[0-9]*.md")))
    total_todo = total_na = 0
    for path in files:
        status, todo, na = process_file(path, dry_run)
        rel = os.path.relpath(path, base)
        if status == "skip":
            print(f"  SKIP (already marked)  {rel}")
        else:
            total_todo += todo
            total_na += na
            print(f"  {todo:4d} ⬜  {na:4d} —   {rel}")
    print(f"\nTOTAL: {total_todo} actionable (⬜), {total_na} non-actionable (—)")
    if dry_run:
        print("(dry run — no files written)")


if __name__ == "__main__":
    main()
