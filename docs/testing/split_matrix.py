#!/usr/bin/env python3
"""Split each matrix-<package>.md into a folder with per-section files.

Only ### headers whose label matches \\d+[A-Z] (e.g. 5A, 10A) are split out
into separate files. All other ### sections (summaries, production
observations, etc.) stay inline in the main file.
"""

import re
import os
import shutil

TESTING_DIR = os.path.dirname(os.path.abspath(__file__))

LETTERED_SECTION = re.compile(r'^### \s*\d+[A-Z]\b')


def slugify_section(header_line):
    """Slug for a lettered section header.

    If the header matches '<id> — <description>', combine id and description.
    Otherwise take everything up to the first '(' or em-dash, strip it.
    Then replace runs of non-word characters with hyphens.
    """
    text = header_line.lstrip('#').strip()
    m = re.match(r'(\d+[A-Z] )— (.+)', text)
    if m:
        text = m.group(1) + m.group(2)
    else:
        text = re.split(r'[(—]', text)[0].strip()
    return re.sub(r'\W+', '-', text).strip('-').lower()


def split_matrix_file(md_path):
    package = re.sub(r'matrix-(.+)\.md', r'\1', os.path.basename(md_path))
    folder = os.path.join(TESTING_DIR, f"matrix-{package}")
    os.makedirs(folder, exist_ok=True)

    with open(md_path) as f:
        content = f.read()

    # Split into runs at every ### boundary
    parts = re.split(r'(?=^### )', content, flags=re.MULTILINE)

    lettered = []    # (header_line, body, slug) — split out
    remaining = []   # raw section text — kept inline

    for part in parts:
        header_line = part.split('\n', 1)[0]
        if LETTERED_SECTION.match(header_line):
            slug = slugify_section(header_line)
            lettered.append((header_line, part, slug))
        else:
            remaining.append(part)

    if not lettered:
        shutil.move(md_path, os.path.join(folder, f"matrix-{package}.md"))
        print(f"{package}: no lettered sections, moved file only")
        return

    # Write each lettered section to its own file
    section_links = []
    for header_line, body, slug in lettered:
        filename = f"{slug}.md"
        with open(os.path.join(folder, filename), 'w') as f:
            f.write(body)
        link_text = header_line.lstrip('#').strip()
        section_links.append((link_text, filename))
        print(f"  {package}/{filename}")

    # Build replacement main file:
    # preamble (remaining[0]) + section links + remaining non-lettered sections
    preamble = remaining[0].rstrip('\n') if remaining else ''
    tail_sections = remaining[1:] if len(remaining) > 1 else []

    lines = [preamble, '']
    for link_text, filename in section_links:
        lines.append(f"- [{link_text}]({filename})")
    for sec in tail_sections:
        lines.append('')
        lines.append(sec.rstrip('\n'))
    lines.append('')

    main_out = os.path.join(folder, f"matrix-{package}.md")
    with open(main_out, 'w') as f:
        f.write('\n'.join(lines))

    os.remove(md_path)
    print(f"{package}: done")


for fname in sorted(os.listdir(TESTING_DIR)):
    if fname.startswith('matrix-') and fname.endswith('.md'):
        path = os.path.join(TESTING_DIR, fname)
        print(f"\n=== {fname} ===")
        split_matrix_file(path)
