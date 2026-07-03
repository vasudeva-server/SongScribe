SongScribe is a Java-based music notation application. SongScribe files use the `.mssw` extension.

GitHub repo: `vasudeva-server/SongScribe`

### Non-obvious Packages

- smufl/ — SMuFL glyph registry: codepoints, names, and font-metric lookups

### Key Entry Points

- `SongScribe.java` — application bootstrap (`main()`)
- `ui/component/MainFrame.java` — main window (singleton)
- `music/Song.java` — the document model

### Spawning Fresh Subagents

When spawning a fresh subagent (with `subagent_type`) for Java work, include in the prompt: *"Read `.agents/rules/serena.md` and follow it for all Java exploration and refactoring."* Forked subagents inherit this rule and need no reminder.

### Required Reading by Task

**MANDATORY:** If a task touches anything involving any of the areas below — even tangentially — read the linked guide first. Default to reading: a defensive read of a short guide is far cheaper than scanning the codebase to infer patterns, and inferred patterns are often wrong because the conventions are not always visible at the call site. Do not rely on prior knowledge; these subsystems have project-specific conventions that override language and framework defaults.

- **User-facing strings** (new, changed, moved, or referenced): [Strings](.agents/guides/strings.md).
- **Pixels, staff-spaces, or conversion between them**: [Unit Conversion](.agents/guides/unit-conversion.md).
- **MBassador message bus** — posting, subscribing, `@Handler` methods, or reading code that uses them: [Message System](.agents/guides/messages.md).
- **Undo — `Mutation` records**, modification brackets, or `SongDidChangeNotification`: [Mutation System](.agents/guides/mutations.md).
- **`JOptionPane`-based alerts, confirms, or input prompts**: [OptionDialogs](.agents/guides/option-dialogs.md).
- **Complex dialogs** (`BaseDialog`, `StandardDialog`, tabs, validation/commit lifecycle): [Dialogs](.agents/guides/dialogs.md).
- **User preferences** (`Prefs`, `PrefsKey`, `defaults.json`, `PrefsDidChangeNotification`): [Preferences](.agents/guides/prefs.md).
- **Custom UI constants** (`FlatLafProps`, `FlatLafKeys`, `FlatLaf.properties`): [FlatLaf Properties](.agents/guides/flatlaf-props.md).
- **File-based logging**: [Logging](.agents/guides/logging.md). If the user says, "check the log", read this guide to know where to look.
- **Creating a new singleton class**: [Singletons](.agents/guides/singletons.md).
- **Third-party API documentation lookup**: [Context7](.agents/guides/context7.md) — use context7 rather than web search.
- **SMuFL glyph names, codepoints, or ranges**: look up at `https://w3c.github.io/smufl/latest/index.html?search=<search terms>`.
