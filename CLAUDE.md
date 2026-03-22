# SongScribe Project Configuration

## GitHub Repository

The remote is `vasudeva-server/SongScribe`. Always use this for `gh` commands (issues, PRs, etc.).

## Overview

SongScribe is a Java/Kotlin-based music notation application.

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
| `converter/` | Legacy format converters |
| `export/` | Export to PDF, SVG, PNG, ABC, MusicXML |
| `file/` | File I/O, serialization |
| `message/` | MBassador event bus — `MessageCenter`, `Message`, commands, notifications |
| `midi/` | MIDI playback |
| `music/` | Data model — `Composition`, `Line`, notes, decorations |
| `prefs/` | User preferences |
| `smufl/` | SMuFL music font handling, glyph metadata |
| `ui/` | Swing UI — `MainFrame`, renderers, actions, panels |
| `util/` | Shared utilities |

### Key Entry Points

- `SongScribe.java` — application bootstrap (`main()`)
- `ui/component/MainFrame.java` — main window (singleton)
- `music/Composition.java` — the document model

## Key Gotchas

- `Strings.java` and `Version.java` are generated — never edit them directly
- `StaffSpaces` utility is deprecated — use `ScaleContext` for new code
- When creating plans, use `/plan-manager:examples:templates` as the format

## Tool Usage

For semantic code exploration and refactoring, see [Serena Tool Usage](./.claude/rules/serena.md).

When you need API documentation for Java, Kotlin, or any third-party library (FlatLaf, Jackson, etc.), use context7 MCP tools instead of web search. Example: `resolve-library-id` for "flatlaf", then `query-docs` with the resolved ID.

## References

For pixel/staff-space conversion and the deprecated `StaffSpaces` class, see [Unit Conversion](./.claude/rules/unit-conversion.md).

For the bootstrap logging constraint in `SongScribe.java`, see [Logging](./.claude/rules/logging.md).

For the MBassador message bus (posting, subscribing, `@Handler` methods), see [Message System](./.claude/rules/messages.md).

For `JOptionPane`-based alerts, confirms, and input prompts, see [OptionDialogs](./.claude/rules/option-dialogs.md).

For complex dialogs (`BaseDialog`, `StandardDialog`, tabs, validation/commit lifecycle), see [Dialogs](./.claude/dialogs.md).

To look up SMuFL glyph names, codepoints, or ranges: `https://w3c.github.io/smufl/latest/index.html?search=<search terms>`
