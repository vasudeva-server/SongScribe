#!/usr/bin/env python3
"""
Replaces all java.desktop package imports with 'import module java.desktop;'.

Covers: java.awt.**, java.beans.**, javax.swing.**, javax.sound.**,
        javax.imageio.**, javax.print.**, javax.accessibility.**, java.applet.**
"""

import sys
from pathlib import Path

DESKTOP_PREFIXES = (
    'import java.awt.',
    'import java.beans.',
    'import javax.swing.',
    'import javax.sound.',
    'import javax.imageio.',
    'import javax.print.',
    'import javax.accessibility.',
    'import java.applet.',
)

MODULE_IMPORT = 'import module java.desktop;\n'


def is_desktop_import(line):
    stripped = line.strip()
    return any(stripped.startswith(p) for p in DESKTOP_PREFIXES)


def collapse_blank_lines(lines):
    """Collapse consecutive blank lines to at most one."""
    result = []
    prev_blank = False
    for line in lines:
        is_blank = line.strip() == ''
        if is_blank and prev_blank:
            continue
        result.append(line)
        prev_blank = is_blank
    return result


def process_file(path):
    text = Path(path).read_text(encoding='utf-8')
    lines = text.splitlines(keepends=True)

    # Skip if no desktop imports
    if not any(is_desktop_import(l) for l in lines):
        return False

    # Skip if already migrated
    if any('import module java.desktop' in l for l in lines):
        return False

    # Remove desktop imports
    filtered = [l for l in lines if not is_desktop_import(l)]

    # Collapse consecutive blank lines left behind
    collapsed = collapse_blank_lines(filtered)

    # Find first remaining non-static import line (static imports stay in place)
    first_import_idx = None
    for i, line in enumerate(collapsed):
        stripped = line.strip()
        if stripped.startswith('import ') and not stripped.startswith('import static '):
            first_import_idx = i
            break

    if first_import_idx is not None:
        # Insert module import before first remaining non-static import
        collapsed.insert(first_import_idx, MODULE_IMPORT)
        # Ensure exactly one blank line after module import if next line is
        # a non-module import (different group)
        next_idx = first_import_idx + 1
        if next_idx < len(collapsed):
            next_line = collapsed[next_idx].strip()
            if next_line.startswith('import ') and not next_line.startswith('import module'):
                collapsed.insert(next_idx, '\n')
    else:
        # No other imports remain — insert after package line + blank
        pkg_idx = None
        for i, line in enumerate(collapsed):
            if line.strip().startswith('package '):
                pkg_idx = i
                break

        if pkg_idx is None:
            return False  # Malformed file, skip

        insert_idx = pkg_idx + 1
        # Skip any blank lines that already follow the package declaration
        while insert_idx < len(collapsed) and collapsed[insert_idx].strip() == '':
            insert_idx += 1

        has_blank_before = (insert_idx > 0 and collapsed[insert_idx - 1].strip() == '')

        # Insert blank line after module import (before next content)
        collapsed.insert(insert_idx, '\n')
        # Insert module import
        collapsed.insert(insert_idx, MODULE_IMPORT)
        # Insert blank line before module import if not already there
        if not has_blank_before:
            collapsed.insert(insert_idx, '\n')

    Path(path).write_text(''.join(collapsed), encoding='utf-8')
    return True


if __name__ == '__main__':
    changed = 0
    for path in sys.argv[1:]:
        try:
            if process_file(path):
                print(f'Updated: {path}')
                changed += 1
        except Exception as e:
            print(f'ERROR {path}: {e}', file=sys.stderr)
    print(f'\nTotal: {changed} files updated')
