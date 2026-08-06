SongScribe is a Java-based music notation application. The current storage format is MusicXML (`.musicxml`); the older `.mssw` (SongWriter) format is legacy read-only, supported only for migrating old files.

GitHub repo: `vasudeva-server/SongScribe`

### Non-obvious Packages

Every top-level package under `songscribe/` has a `package-info.java` describing its role — read that before inferring a package's purpose from its name.

- smufl/ — SMuFL glyph registry: codepoints, names, and font-metric lookups
- io/ — `io/musicxml/` (`MusicXmlWriter`/`MusicXmlReader`) is the **current** storage mechanism; `SongIO` and the other legacy-format classes in `io/` are **legacy read-only** (migration of old files). Never add new persisted fields to the legacy path — they go in the MusicXML writer/reader.
- dom/ — the document model (`Song`, `Line`, elements), not a DOM/XML tree
- layout/ vs engraving/ — `layout/` computes positions and spacing; `engraving/` holds staff geometry and engraving constants
- converter/ vs uiconverter/ — `converter/` is the headless batch converter; `uiconverter/` is its Swing front end

### Key Entry Points

- `SongScribe.java` — application bootstrap (`main()`)
- `ui/component/MainFrame.java` — main window (singleton)
- `dom/Song.java` — the document model

### Design Docs

`docs/*.md` holds subsystem design notes (`undo.md`, `clipboard.md`, `line-layout.md`, `tie-rendering-placement.md`, and others). Guides in `.agents/guides/` tell you the conventions to follow; `docs/` explains why a subsystem is built the way it is. Check for a matching doc before a non-trivial change to one of these areas.

### Spawning Fresh Subagents

When spawning a fresh subagent (with `subagent_type`) for Java work, include in the prompt: *"Read `.agents/rules/serena.md` and follow it for all Java exploration and refactoring."* Forked subagents inherit this rule and need no reminder.

### Required Reading by Task

**MANDATORY:** If a task touches anything involving any of the areas below — even tangentially — read the linked guide first. Default to reading: a defensive read of a short guide is far cheaper than scanning the codebase to infer patterns, and inferred patterns are often wrong because the conventions are not always visible at the call site. Do not rely on prior knowledge; these subsystems have project-specific conventions that override language and framework defaults.

- **User-facing strings** (new, changed, moved, or referenced): [Strings](.agents/guides/strings.md).
- **Lyrics or verses** — syllables, hyphen chains, melismas, the lyric editor, or anything indexed by verse: [Lyrics and Verses](.agents/guides/lyrics.md).
- **Pixels, staff-spaces, or conversion between them**: [Unit Conversion](.agents/guides/unit-conversion.md).
- **Zoom** — `ViewScale`, `ScoreView`'s zoom-apply path, `ZoomController`, the `Ss`/`DocPx`/`ViewPx` unit types, or the paint-transform scale factor: [Zoom](.agents/guides/zoom.md).
- **MBassador message bus** — posting, subscribing, `@Handler` methods, or reading code that uses them: [Message System](.agents/guides/messages.md).
- **Undo — `Mutation` records**, modification brackets, or `SongDidChangeNotification`: [Mutation System](.agents/guides/mutations.md).
- **`JOptionPane`-based alerts, confirms, or input prompts**: [OptionDialogs](.agents/guides/option-dialogs.md).
- **Complex dialogs** (`BaseDialog`, `StandardDialog`, tabs, validation/commit lifecycle): [Dialogs](.agents/guides/dialogs.md).
- **User preferences** (`Prefs`, `PrefsKey`, `defaults.json`, `PrefsDidChangeNotification`): [Preferences](.agents/guides/prefs.md).
- **Custom UI constants** (`FlatLafProps`, `FlatLafKeys`, `FlatLaf.properties`): [FlatLaf Properties](.agents/guides/flatlaf-props.md).
- **File-based logging**: [Logging](.agents/guides/logging.md). If the user says, "check the log", read this guide to know where to look.
- **Nullability** — `@Nullable`, `@NullMarked`, NullAway suppressions, deferred-init fields, `requireXxx()` accessors, or reacting to an unexpected null: [Null Handling](.agents/guides/null-handling.md).
- **Creating a new singleton class**: [Singletons](.agents/guides/singletons.md).
- **Third-party API documentation lookup**: [Context7](.agents/guides/context7.md) — use context7 rather than web search.
- **SMuFL glyph names, codepoints, or ranges**: look up at `https://w3c.github.io/smufl/latest/index.html?search=<search terms>`.
- **LilyPond source**: If the user mentions LilyPond source, it is found at ~/Developer/projects/lilypond/lily/.

The guides above trigger on *what a task touches*. One guide triggers on *how hard a task is*, independent of subsystem, iff the current model is NOT fable:

- **Before any ambiguous, multi-phase, or irreversible task** — design work, migrations, debugging with no obvious cause, or anything touching deletes, pushes, or external systems — read [Fable Reasoning Manual](.agents/guides/fable-reasoning-manual.md) first. It is procedure, not philosophy: request decomposition, risk localization, verification by re-derivation, and a pre-send checklist. Skip it for routine, single-step, reversible work.
