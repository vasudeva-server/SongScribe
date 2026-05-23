#!/usr/bin/env python3
"""Regenerate remediation-ledger.md from the section files' done markers.

The matrix section files are the single source of truth: each actionable row
carries ⬜ (todo) or ✅ (done) in its `done` column. This script counts those
markers per section, groups by package, and writes the ledger. Re-run after each
work unit to refresh progress; never hand-edit the generated counts.
"""
import glob
import os
import re

PKG_NAMES = {
    "matrix-dom": "1 · dom",
    "matrix-io": "2 · io",
    "matrix-layout": "3 · layout",
    "matrix-util": "4 · midi/converter/util/smufl/prefs/font/export/uiconverter",
    "matrix-action": "5 · ui/action",
    "matrix-selection": "6 · ui/selection+edit+adjustment+clipboard",
    "matrix-component": "7 · ui/component",
    "matrix-message": "8 · message",
    "matrix-renderer": "9 · ui/renderer",
    "matrix-dialog": "10 · ui/dialog",
    "matrix-menu": "11 · ui/menu+playback+platform+ui",
    "matrix-lifecycle": "12 · lifecycle+error+top-level",
    "matrix-e2e": "13 · e2e reconciliation",
}
PKG_ORDER = list(PKG_NAMES.keys())
DONE_CELL = re.compile(r"\|\s*(⬜|✅)\s*\|\s*$")


def count_file(path):
    todo = done = 0
    with open(path, encoding="utf-8") as f:
        for line in f:
            m = DONE_CELL.search(line.rstrip("\n"))
            if m:
                if m.group(1) == "✅":
                    done += 1
                else:
                    todo += 1
    return done, todo


def main():
    base = os.path.dirname(os.path.abspath(__file__))
    rows = []
    grand_done = grand_total = 0
    for pkg in PKG_ORDER:
        files = sorted(glob.glob(os.path.join(base, pkg, "[0-9]*.md")))
        for path in files:
            done, todo = count_file(path)
            total = done + todo
            if total == 0:
                continue
            rel = os.path.relpath(path, base)
            section = os.path.splitext(os.path.basename(path))[0]
            rows.append((PKG_NAMES[pkg], section, rel, done, total))
            grand_done += done
            grand_total += total

    pct = (100 * grand_done // grand_total) if grand_total else 0
    out = []
    out.append("# Remediation Ledger (generated — do not hand-edit counts)\n")
    out.append(
        "> Regenerate with `python3 gen_ledger.py`. The section files' `done`\n"
        "> column (⬜/✅) is the source of truth; this is a derived view.\n"
    )
    out.append(f"\n**Overall: {grand_done} / {grand_total} actionable rows done ({pct}%).**\n")
    out.append("\n| Package | Section | Done | Total | Status |")
    out.append("\n|---|---|---:|---:|---|")
    cur_pkg = None
    for pkg, section, rel, done, total in rows:
        pkgcell = pkg if pkg != cur_pkg else ""
        cur_pkg = pkg
        if done == 0:
            status = "not started"
        elif done < total:
            status = "in progress"
        else:
            status = "✅ complete"
        out.append(f"\n| {pkgcell} | [{section}]({rel}) | {done} | {total} | {status} |")
    out.append("\n")

    with open(os.path.join(base, "remediation-ledger.md"), "w", encoding="utf-8") as f:
        f.writelines(out)
    print(f"Wrote remediation-ledger.md — {grand_done}/{grand_total} done ({pct}%).")


if __name__ == "__main__":
    main()
