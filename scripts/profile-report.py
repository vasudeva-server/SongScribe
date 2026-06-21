#!/usr/bin/env python3
"""
Parse an async-profiler flame-graph HTML file and report inclusive wall-time.

`scripts/run.sh --profile` writes build/songscribe-profile.html on quit. That
file embeds the profile as a delta-encoded JS model (front-coded `cpool` string
table + `f`/`u`/`n` frame calls). This script decodes that model back into a
call tree so you can query inclusive sample counts per method without opening a
browser.

At the 1 ms wall-clock sampling interval used by run.sh, 1 sample on a working
thread is ~1 ms of wall time on that thread.

Usage:
  scripts/profile-report.py [HTML] [SUBSTR ...]
    HTML     path to the flame-graph HTML (default: build/songscribe-profile.html)
    SUBSTR   one or more case-insensitive substrings to match frame titles
             against; for each, the matching frames are listed by inclusive
             width with their call path. With no SUBSTR, prints the hottest
             leaf-ward frames overall.

  scripts/profile-report.py --collapsed [HTML] > out.collapsed
             emit folded (collapsed) stacks: "frame;frame;... count", one per
             line, the format flame-graph tooling and `grep` both like.

Examples:
  scripts/profile-report.py                       # default file, overview
  scripts/profile-report.py build/x.html createOffsetArea openMidi
  scripts/profile-report.py --collapsed | grep paintComponent
"""

import html as html_mod
import re
import sys

DEFAULT_HTML = "build/songscribe-profile.html"
OVERVIEW_LIMIT = 30
MATCH_LIMIT = 12


class Frame:
    __slots__ = ("title", "level", "left", "width", "children")

    def __init__(self, title, level, left, width):
        self.title = title
        self.level = level
        self.left = left
        self.width = width
        self.children = []


def parse_cpool(text):
    """Extract and front-code-decode the cpool string table."""
    start = text.index("const cpool = [") + len("const cpool = [")
    end = text.index("\n];", start)
    body = text[start:end]

    # Each entry is a single-quoted JS string literal on its own line.
    raw = re.findall(r"'((?:[^'\\]|\\.)*)'", body)

    def unescape(s):
        return s.replace("\\\\", "\\").replace("\\'", "'")

    pool = [unescape(s) for s in raw]

    # unpack(): cpool[i] = cpool[i-1][:charCode(0)-32] + cpool[i][1:]
    for i in range(1, len(pool)):
        keep = ord(pool[i][0]) - 32
        pool[i] = pool[i - 1][:keep] + pool[i][1:]
    return pool


def parse_tree(text, cpool):
    """Replay the f/u/n frame stream into a call tree; return the root frame."""
    data_start = text.index("unpack(cpool);")
    # Frame calls run from there to the closing search() call.
    data = text[data_start:]

    state = {"level0": 0, "left0": 0, "width0": 0}
    current = {}  # level -> most recent Frame at that level
    root = [None]

    def emit(key, level, left_inc, width):
        state["level0"] = level
        state["left0"] += left_inc
        if width is not None:
            state["width0"] = width
        title = cpool[key >> 3]
        fr = Frame(title, level, state["left0"], state["width0"])
        current[level] = fr
        parent = current.get(level - 1)
        if parent is None:
            root[0] = fr
        else:
            parent.children.append(fr)

    call_re = re.compile(r"\b([fun])\((\d+)(?:,(-?\d*))?(?:,(-?\d*))?", )
    for m in call_re.finditer(data):
        kind, key = m.group(1), int(m.group(2))
        a = m.group(3)
        # arg layout: f(key,level,left,width,...) u(key,width,...) n(key,width,...)
        if kind == "f":
            level = int(a) if a else 0
            left = m.group(4)
            left_inc = int(left) if left else 0
            # width is the 4th positional arg (after key,level,left)
            wmatch = re.match(r"f\(\d+,-?\d*,-?\d*,(-?\d+)", m.string[m.start():m.start() + 60])
            width = int(wmatch.group(1)) if wmatch else None
            emit(key, level, left_inc, width)
        elif kind == "u":
            width = int(a) if a else None
            emit(key, state["level0"] + 1, 0, width)
        else:  # n
            width = int(a) if a else None
            emit(key, state["level0"], state["width0"], width)
    return root[0]


def walk(frame, path, out):
    out.append((frame, list(path)))
    path.append(frame.title)
    for ch in frame.children:
        walk(ch, path, out)
    path.pop()


def short(title):
    return html_mod.unescape(title)


def main():
    args = sys.argv[1:]
    collapsed = False
    if args and args[0] == "--collapsed":
        collapsed = True
        args = args[1:]

    html_path = DEFAULT_HTML
    if args and (args[0].endswith(".html") or "/" in args[0]):
        html_path = args.pop(0)

    with open(html_path, encoding="utf-8") as fh:
        text = fh.read()

    cpool = parse_cpool(text)
    root = parse_tree(text, cpool)

    flat = []
    walk(root, [], flat)
    total = root.width

    if collapsed:
        # Emit a folded line per leaf, with self-count = width minus children widths.
        for fr, path in flat:
            child_w = sum(c.width for c in fr.children)
            self_w = fr.width - child_w
            if self_w > 0:
                stack = ";".join(short(t) for t in path + [fr.title])
                print(f"{stack} {self_w}")
        return

    print(f"file:  {html_path}")
    print(f"total: {total:,} samples (~1 ms/sample/thread, wall-clock)")
    print()

    if not args:
        # Overview: hottest frames by self time (excl. the all-idle parks).
        scored = []
        for fr, path in flat:
            child_w = sum(c.width for c in fr.children)
            self_w = fr.width - child_w
            scored.append((self_w, fr, path))
        scored.sort(key=lambda x: -x[0])
        print(f"Top {OVERVIEW_LIMIT} frames by SELF samples:")
        for self_w, fr, path in scored[:OVERVIEW_LIMIT]:
            print(f"  {self_w:>8,}  {short(fr.title)}")
        print()
        print("Pass method-name substrings as args for inclusive call paths.")
        return

    for needle in args:
        nl = needle.lower()
        matches = [(fr, path) for fr, path in flat if nl in fr.title.lower()]
        matches.sort(key=lambda x: -x[0].width)
        print(f"== '{needle}': {len(matches)} frame(s) ==")
        for fr, path in matches[:MATCH_LIMIT]:
            pct = 100.0 * fr.width / total if total else 0
            print(f"  {fr.width:>8,} samples ({pct:5.2f}%)  {short(fr.title)}")
            # show the immediate call path (last few ancestors)
            tail = [short(t).split("(")[0] for t in path[-4:]]
            if tail:
                print(f"           via … {' → '.join(tail)}")
        print()


if __name__ == "__main__":
    main()
