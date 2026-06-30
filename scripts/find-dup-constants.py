#!/usr/bin/env python3
"""
find-dup-constants.py

Parses src/test/constants.txt (format: file-path header, then "line:NAME"
entries, blank line between files) and searches the production source tree for
each constant name. Reports candidates where the same ALL_CAPS name appears as
a static final in production code, meaning the test should reference it directly
instead of redeclaring it.

Usage:
    python3 scripts/find-dup-constants.py [constants.txt] [src/main/java]
"""

import re
import sys
import subprocess
from pathlib import Path


PROD_PATTERN = re.compile(r'static\s+final\s+\w+\s+({name})\b')


def parse_constants_file(path: Path) -> dict[str, list[tuple[int, str]]]:
    """Returns {test_file_path: [(line, name), ...]}"""
    result: dict[str, list[tuple[int, str]]] = {}
    current_file: str | None = None

    for raw in path.read_text(encoding='utf-8').splitlines():
        line = raw.rstrip()

        if not line:
            current_file = None
            continue

        if ':' in line:
            lineno_str, name = line.split(':', 1)
            if current_file is not None and lineno_str.isdigit():
                result.setdefault(current_file, []).append((int(lineno_str), name))
                continue

        # No colon, or colon but no current file yet — treat as path header
        current_file = line

    return result


def search_production(name: str, src_root: Path) -> list[tuple[str, int, str]]:
    """Return [(file, line, declaration), ...] for matching production declarations."""
    pattern = PROD_PATTERN.pattern.replace('{name}', re.escape(name))
    result = subprocess.run(
        ['grep', '-rn', '--include=*.java', '-E', pattern, str(src_root)],
        capture_output=True, text=True, check=False
    )

    hits = []
    for raw in result.stdout.splitlines():
        parts = raw.split(':', 2)
        if len(parts) == 3:
            hits.append((parts[0], int(parts[1]), parts[2].strip()))

    return hits


def main() -> None:
    constants_path = Path(sys.argv[1]) if len(sys.argv) > 1 \
        else Path('src/test/constants.txt')
    src_root = Path(sys.argv[2]) if len(sys.argv) > 2 \
        else Path('src/main/java')

    if not constants_path.exists():
        sys.exit(f'Not found: {constants_path}')
    if not src_root.exists():
        sys.exit(f'Not found: {src_root}')

    by_file = parse_constants_file(constants_path)
    total = sum(len(v) for v in by_file.values())
    print(f'Parsed {total} constants across {len(by_file)} test files.\n',
          file=sys.stderr)

    found_any = False

    for test_file in sorted(by_file):
        file_hits: list[tuple[int, str, list]] = []

        for lineno, name in by_file[test_file]:
            prod_hits = search_production(name, src_root)
            if prod_hits:
                file_hits.append((lineno, name, prod_hits))

        if file_hits:
            found_any = True
            print(f'{test_file}')
            for lineno, name, prod_hits in file_hits:
                print(f'  line {lineno}: {name}')
                for file, line, decl in prod_hits:
                    try:
                        rel = Path(file).relative_to(src_root)
                    except ValueError:
                        rel = Path(file)
                    print(f'    -> {rel}:{line}  {decl}')
            print()

    if not found_any:
        print('No matching production constants found.')


if __name__ == '__main__':
    main()
