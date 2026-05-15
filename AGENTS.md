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

### References

User-facing strings: [Strings](.agents/guides/strings.md)

Pixel/staff-space conversion: [Unit Conversion](.agents/guides/unit-conversion.md)

MBassador message bus (posting, subscribing, `@Handler` methods): [Message System](.agents/guides/messages.md)

Typed `Mutation` records, modification brackets, and `SongDidChangeNotification` filtering: [Mutation System](.agents/guides/mutations.md)

`JOptionPane`-based alerts, confirms, and input prompts: [OptionDialogs](.agents/guides/option-dialogs.md).

Complex dialogs (`BaseDialog`, `StandardDialog`, tabs, validation/commit lifecycle): [Dialogs](.agents/guides/dialogs.md).

User preferences (`Prefs` singleton, `PrefsKey` enum, `defaults.json`, and `PrefsDidChangeNotification`): [Preferences](.agents/guides/prefs.md).

Custom UI constants (`FlatLafProps`, `FlatLafKeys`, `FlatLaf.properties`): [FlatLaf Properties](.agents/guides/flatlaf-props.md).

File-based logging: [Logging](.agents/guides/logging.md)

Third party API documentation lookup via context7: [Context7](.agents/guides/context7.md)

Look up SMuFL glyph names, codepoints, or ranges: `https://w3c.github.io/smufl/latest/index.html?search=<search terms>`
