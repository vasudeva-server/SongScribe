#!/usr/bin/env python3
"""
Find test classes and methods that are NOT listed in tests-to-keep.txt.
Output is sorted and saved to docs/testing/tests-to-remove.txt.
"""

import os
import re
from pathlib import Path

SCRIPT_DIR = Path(__file__).parent
REPO_ROOT = SCRIPT_DIR.parent.parent
TEST_SRC = REPO_ROOT / "src/test/java"
KEEP_FILE = SCRIPT_DIR / "tests-to-keep.txt"
OUTPUT_FILE = SCRIPT_DIR / "tests-to-remove.txt"

CLASS_RE = re.compile(r'(?:^|\s)(?:class|interface)\s+(\w+)')
TEST_METHOD_RE = re.compile(r'@Test\b')
METHOD_NAME_RE = re.compile(r'(?:public|protected|package|private|\s)\s+\w[\w<>,\[\]\s]*\s+(\w+)\s*\(')


def parse_test_file(path: Path) -> dict[str, list[str]]:
    """
    Return {ClassName: [method, ...], ...} for all classes and @Test methods
    in a single Java file. Nested classes are included under their own name.
    """
    source = path.read_text(encoding='utf-8')
    lines = source.splitlines()

    results: dict[str, list[str]] = {}
    # Stack of (class_name, brace_depth_at_open)
    class_stack: list[tuple[str, int]] = []
    brace_depth = 0
    pending_test = False

    i = 0
    while i < len(lines):
        line = lines[i]

        # Track braces
        brace_depth += line.count('{') - line.count('}')

        # Detect class declarations
        class_match = CLASS_RE.search(line)
        if class_match and '{' in line:
            class_name = class_match.group(1)
            # Only care about classes whose name ends in 'Test' or contain 'Test'
            # Actually gather all — filtering is done at output time
            class_stack.append((class_name, brace_depth))
            if class_name not in results:
                results[class_name] = []

        # Pop classes whose brace scope closed
        while class_stack and brace_depth < class_stack[-1][1]:
            class_stack.pop()

        # Detect @Test annotation
        if TEST_METHOD_RE.search(line):
            pending_test = True

        # Detect method after @Test
        if pending_test:
            method_match = METHOD_NAME_RE.search(line)
            if method_match:
                method_name = method_match.group(1)
                if class_stack:
                    current_class = class_stack[-1][0]
                    results.setdefault(current_class, []).append(method_name)
                pending_test = False

        # Reset pending_test if we hit a class declaration or closing brace line
        # without finding a method (e.g. annotation on its own line followed by
        # the method signature on the next line — handled by the loop naturally)

        i += 1

    return results


def load_keep_set(keep_file: Path) -> tuple[set[str], set[str]]:
    """
    Return (keep_classes, keep_methods) where:
      keep_classes — class names listed without a method (entire class kept)
      keep_methods — "ClassName.methodName" entries
    """
    keep_classes: set[str] = set()
    keep_methods: set[str] = set()

    for raw in keep_file.read_text(encoding='utf-8').splitlines():
        entry = raw.strip()
        if not entry:
            continue
        if '.' in entry:
            keep_methods.add(entry)
        else:
            keep_classes.add(entry)

    return keep_classes, keep_methods


ROOT_PACKAGE_DIR = TEST_SRC / "songscribe"


def collect_all_tests(test_src: Path) -> dict[str, list[str]]:
    """Walk all Java files and merge class→methods maps."""
    all_tests: dict[str, list[str]] = {}
    for java_file in test_src.rglob("*.java"):
        if java_file.parent == ROOT_PACKAGE_DIR:
            continue
        file_results = parse_test_file(java_file)
        for class_name, methods in file_results.items():
            existing = all_tests.setdefault(class_name, [])
            for m in methods:
                if m not in existing:
                    existing.append(m)
    return all_tests


def build_removal_list(
    all_tests: dict[str, list[str]],
    keep_classes: set[str],
    keep_methods: set[str],
) -> list[str]:
    """
    Return sorted list of entries that should be removed.

    Rules:
    - If the entire class is in keep_classes, skip it entirely.
    - Otherwise, for each method: if "Class.method" is in keep_methods, skip it.
      If the class has no @Test methods at all, emit just "ClassName".
      Otherwise emit individual "ClassName.method" entries for those not kept.
    """
    to_remove: list[str] = []

    for class_name, methods in all_tests.items():
        if class_name in keep_classes:
            continue

        if not methods:
            # Class with no @Test methods — emit the class itself if not kept
            to_remove.append(class_name)
            continue

        for method in methods:
            entry = f"{class_name}.{method}"
            if entry not in keep_methods:
                to_remove.append(entry)

    return sorted(to_remove)


def main() -> None:
    keep_classes, keep_methods = load_keep_set(KEEP_FILE)
    all_tests = collect_all_tests(TEST_SRC)

    removal_list = build_removal_list(all_tests, keep_classes, keep_methods)

    OUTPUT_FILE.write_text('\n'.join(removal_list) + '\n', encoding='utf-8')
    print(f"Written {len(removal_list)} entries to {OUTPUT_FILE}")


if __name__ == '__main__':
    main()
