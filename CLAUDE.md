# SongScribe Project Configuration

## GitHub Repository

The remote is `vasudeva-server/SongScribe`. Always use this for `gh` commands (issues, PRs, etc.).

## Overview

SongScribe is a Java/Kotlin-based music notation application. Code style should emphasize clarity, maintainability, and consistency with the existing codebase patterns.

## Quick Commands

```bash
./scripts/compile.sh              # Compile (required before run)
./scripts/crun.sh                 # Compile + run
./scripts/run.sh                  # Run (after compile)
./scripts/test.sh unit            # Run unit tests
./scripts/check-style.sh <file>   # Check a file for style violations
source ./scripts/set-java-home.sh # Set JAVA_HOME (requires Java 25+)
```

See [development rules](./.claude/rules/development.md) for full script options and testing examples.

## Package Overview

Key packages under `src/main/java/songscribe/`:

| Package | Purpose |
|---------|---------|
| `music/` | Data model — `Composition`, `Line`, notes, decorations |
| `ui/` | Swing UI — `MainFrame`, renderers, actions, panels |
| `smufl/` | SMuFL music font handling, glyph metadata |
| `export/` | Export to PDF, SVG, PNG, ABC, MusicXML |
| `midi/` | MIDI playback |
| `file/` | File I/O, serialization |
| `converter/` | Legacy format converters |
| `prefs/` | User preferences |
| `util/` | Shared utilities |

### Key Entry Points

- `SongScribe.java` — application bootstrap (`main()`)
- `ui/component/MainFrame.java` — main window (singleton)
- `music/Composition.java` — the document model

## Key Gotchas

- `Strings.java` and `Version.java` are generated — never edit them directly
- `StaffSpaces` utility is deprecated — use `ScaleContext` for new code (see [unit-conversion rules](./.claude/rules/unit-conversion.md))
- Logger in `SongScribe.java` would initialize before config — see [logging rules](./.claude/rules/logging.md)

## Tool Usage

For semantic code exploration and refactoring, see [Serena Tool Usage](./.claude/rules/serena.md).

When you need API documentation for Java, Kotlin, or any third-party library (FlatLaf, Jackson, etc.), use context7 MCP tools instead of web search. Example: `resolve-library-id` for "flatlaf", then `query-docs` with the resolved ID.

## Phased Plans

When creating phased plans, use the templates from the `/plan-manager:examples:templates` skill for formatting (status dashboard, phase sections, status icons, etc.).

## SMuFL Reference

To look up SMuFL glyph names, codepoints, or ranges, use:
`https://w3c.github.io/smufl/latest/index.html?search=<search terms>`
