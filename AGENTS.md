# SongScribe Project Configuration

## Agent skills

### Issue tracker

Issues live in GitHub Issues (`vasudeva-server/SongScribe`). See `docs/agents/issue-tracker.md`.

### Triage labels

Default label vocabulary (`needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`). See `docs/agents/triage-labels.md`.

### Domain docs

Single-context layout — one `CONTEXT.md` + `docs/adr/` at the repo root. See `docs/agents/domain.md`.

## Global Rules

### GitHub Repository

The remote is `vasudeva-server/SongScribe`. Always use this for `gh` commands (issues, PRs, etc.).

### Overview

SongScribe is a Java-based music notation application. SongScribe files use the `.mssw` extension.

### Quick Commands

```bash
./scripts/compile.sh              # Compile (required before run)
./scripts/crun.sh                 # Compile + run
./scripts/run.sh                  # Run (after compile)
./scripts/test.sh unit            # Run unit tests
source ./scripts/set-java-home.sh # Set JAVA_HOME (requires Java 25+)
```

You MUST read [development rules](.agents/rules/development.md) for comprehensive information on the development workflow and harness.

### Package Overview

Key packages under `src/main/java/songscribe/`:

| Package | Purpose |
|---------|---------|
| `converter/` | Legacy format converters |
| `export/` | Export to PDF, SVG, PNG, ABC, MusicXML |
| `file/` | File I/O, serialization |
| `message/` | MBassador event bus — `MessageCenter`, `Message`, commands, notifications |
| `midi/` | MIDI playback |
| `music/` | Data model — `Song`, `Line`, notes, decorations |
| `prefs/` | User preferences |
| `smufl/` | SMuFL music font handling, glyph metadata |
| `ui/` | Swing UI — `MainFrame`, renderers, actions, panels |
| `util/` | Shared utilities |

### Key Entry Points

- `SongScribe.java` — application bootstrap (`main()`)
- `ui/component/MainFrame.java` — main window (singleton)
- `music/Song.java` — the document model

### Key Gotchas

- `Strings.java` and `Version.java` are generated — never edit them directly
- `StaffSpaces` utility is deprecated — use `ScaleContext` for new code
- When creating plans, use `/plan-manager:examples:templates` as the format

### Tool Usage

For semantic code exploration and refactoring, see [Serena Tool Usage](.agents/rules/serena.md).

When you need API documentation for Java or any third-party library (FlatLaf, Jackson, etc.), use context7 MCP tools instead of web search. Example: `resolve-library-id` for "flatlaf", then `query-docs` with the resolved ID.

### Spawning Explore Agents

When spawning an `Explore` subagent for Java code, always include this instruction in the prompt:

> **Use Serena semantic tools first for all Java code exploration** (`jet_brains_get_symbols_overview`, `jet_brains_find_symbol`, `jet_brains_find_referencing_symbols`). Fall back to Grep/Glob/Read only for non-code files or when Serena returns no results.

### References

For pixel/staff-space conversion and the deprecated `StaffSpaces` class, see [Unit Conversion](.agents/rules/unit-conversion.md).

For the bootstrap logging constraint in `SongScribe.java`, see [Logging](.agents/rules/logging.md).

For the MBassador message bus (posting, subscribing, `@Handler` methods), see [Message System](.agents/rules/messages.md).

For the typed `Mutation` records, modification brackets, and `SongDidChangeNotification` filtering, see [Mutation System](.agents/rules/mutations.md).

For `JOptionPane`-based alerts, confirms, and input prompts, see [OptionDialogs](.agents/option-dialogs.md).

For complex dialogs (`BaseDialog`, `StandardDialog`, tabs, validation/commit lifecycle), see [Dialogs](.agents/dialogs.md).

For the `Prefs` singleton, `PrefsKey` enum, `defaults.json`, and `PrefsDidChangeNotification`, see [Preferences](.agents/prefs.md).

For custom UI constants (`FlatLafProps`, `FlatLafKeys`, `FlatLaf.properties`), see [FlatLaf Properties](.agents/flatlaf-props.md).

To look up SMuFL glyph names, codepoints, or ranges: `https://w3c.github.io/smufl/latest/index.html?search=<search terms>`
