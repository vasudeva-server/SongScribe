SongScribe is a Java-based music notation application. The current storage format is MusicXML (`.musicxml`); the older `.mssw` (SongWriter) format is legacy read-only, supported only for migrating old files.

GitHub repo: `vasudeva-server/SongScribe`

### Non-obvious Packages

`hit/package-info.java` and `ui/dialog/backend/package-info.java` describe their packages' roles and are worth reading before working in them. Every other `package-info.java` carries only the `@NullMarked` annotation — do not go looking there for a package's purpose. The list below and the design notes in `docs/` are the documentation.

- smufl/ — SMuFL glyph registry: codepoints, names, and font-metric lookups
- io/ — `io/musicxml/` (`MusicXmlWriter`/`MusicXmlReader`) is the **current** storage mechanism; MusicXML I/O goes through a schema-bound `org.audiveris:proxymusic` object graph (`ScorePartwise`) rather than streaming SAX/XMLStreamWriter events — see `docs/musicxml-object-model.md`. `SongIO` and the other legacy-format classes in `io/` are **legacy read-only** (migration of old files). Never add new persisted fields to the legacy path — they go in the MusicXML writer/reader.
- dom/ — the document model (`Song`, `Line`, elements), not a DOM/XML tree
- layout/ vs engraving/ — `layout/` computes positions and spacing; `engraving/` holds staff geometry and engraving constants
- converter/ vs uiconverter/ — `converter/` is the headless batch converter; `uiconverter/` is its Swing front end

### Key Entry Points

- `SongScribe.java` — application bootstrap (`main()`)
- `ui/component/MainFrame.java` — main window (singleton)
- `dom/Song.java` — the document model

### Design Docs

`docs/*.md` holds subsystem design notes (`undo.md`, `clipboard.md`, `line-layout.md`, `tie-rendering-placement.md`, and others) and, since Phase 7 of the contract-driven rollout, the tier-3 layer of the contract hierarchy — architectural and domain rules that span subsystems, which no single class's Javadoc can state because no single class owns them (`unit-conversion.md`, `zoom.md`, `lyrics.md`, `messages.md`, `mutations.md`, and others). Guides in `.claude/guides/` tell you the conventions to follow within that hierarchy — how to write code here, not what the system promises. The two directories are siblings, not nested: `docs/` states promises, `.claude/guides/` states conventions. Check for a matching doc before a non-trivial change to one of these areas.

### Spawning Fresh Subagents

When spawning a fresh subagent (with `subagent_type`) for Java work, include in the prompt: *"Read `.claude/rules/serena.md` and follow it for all Java exploration and refactoring."* Forked subagents inherit this rule and need no reminder.

### Required Reading by Task

**MANDATORY:** If a task touches any area below — even tangentially — read the linked guide first; these subsystems have project-specific conventions that override language and framework defaults.

- **Writing or changing a method, class, or package contract** — any new or changed nontrivial API: [Contracts](.claude/guides/contracts.md).
- **User-facing strings** (new, changed, moved, or referenced): [Strings](.claude/guides/strings.md).
- **MusicXML** — reading, writing, the `ScorePartwise` object graph, a builder or mapper, or any question about what a MusicXML file may contain: [MusicXML Object Model](docs/musicxml-object-model.md). Note especially that **only SongScribe-authored files are read** — foreign input is rejected at the provenance gate, so no design effort goes into supporting it.
- **Lyrics or verses** — syllables, hyphen chains, melismas, the lyric editor, or anything indexed by verse: [Lyrics and Verses](docs/lyrics.md).
- **Pixels, staff-spaces, or conversion between them**: [Unit Conversion](docs/unit-conversion.md).
- **Zoom** — `ViewScale`, `ScoreView`'s zoom-apply path, `ZoomController`, the `Ss`/`DocPx`/`ViewPx` unit types, or the paint-transform scale factor: [Zoom](docs/zoom.md).
- **MBassador message bus** — posting, subscribing, `@Handler` methods, or reading code that uses them: [Message Framework](docs/messages.md).
- **Undo — `Mutation` records**, modification brackets, or `SongDidChangeNotification`: [Mutation Framework](docs/mutations.md).
- **Key signatures** — line keys and inheritance, mid-line key changes, cautionary rendering, or the cancellation policy: [Key Signatures](docs/key-signatures.md).
- **`JOptionPane`-based alerts, confirms, or input prompts**: [OptionDialogs](.claude/guides/option-dialogs.md).
- **Complex dialogs** (`BaseDialog`, `StandardDialog`, tabs, validation/commit lifecycle), **or a dialog back end** (`DialogBackEnd`, `AttachmentBackEnd`, anything in `ui/dialog/backend/`): [Dialogs](.claude/guides/dialogs.md). It states what a dialog may and may not touch, which is the rule a back end exists to keep.
- **User preferences** (`Prefs`, `PrefsKey`, `defaults.json`, `PrefsDidChangeNotification`): [Preferences](.claude/guides/prefs.md).
- **Custom UI constants** (`FlatLafProps`, `FlatLafKeys`, `FlatLaf.properties`): [FlatLaf Properties](.claude/guides/flatlaf-props.md).
- **File-based logging**: [Logging](.claude/guides/logging.md). If the user says, "check the log", read this guide to know where to look.
- **Nullability** — `@Nullable`, `@NullMarked`, NullAway suppressions, deferred-init fields, `requireXxx()` accessors, or reacting to an unexpected null: [Null Handling](.claude/guides/null-handling.md).
- **Creating a new singleton class**: [Singletons](.claude/guides/singletons.md).
- **Disposing an object, or writing a class that registers itself with anything process-global** (`Disposable`, `dispose()`, a constructor-side `MessageCenter.subscribe`): [Application and Object Lifecycle](docs/lifecycle.md).
- **SMuFL glyph names, codepoints, or ranges**: look up at `https://w3c.github.io/smufl/latest/index.html?search=<search terms>`.
- **LilyPond source**: If the user mentions LilyPond source, it is found at ~/Developer/projects/lilypond/lily/.
- **ABC corpus**: If the user mentions the ABC corpus, it is the .abc files in the numbered directories in ~/Documents/Centre/Music/SongScribe\ songs/ABC. 
