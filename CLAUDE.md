SongScribe is a Java-based music notation application. The current storage format is MusicXML (`.musicxml`); the older `.mssw` (SongWriter) format is legacy read-only, supported only for migrating old files.

GitHub repo: `vasudeva-server/SongScribe`

### Non-obvious Packages

`hit/package-info.java` describes its package's role and is worth reading before working in it. Every other `package-info.java` carries only the `@NullMarked` annotation — do not go looking there for a package's purpose. The list below and the files in `guides/` are the documentation.

- smufl/ — SMuFL glyph registry: codepoints, names, and font-metric lookups
- io/ — `io/musicxml/` (`MusicXmlWriter`/`MusicXmlReader`) is the **current** storage mechanism; MusicXML I/O goes through a schema-bound `org.audiveris:proxymusic` object graph (`ScorePartwise`) rather than streaming SAX/XMLStreamWriter events — see `guides/subsystems/musicxml.md`. `SongIO` and the other legacy-format classes in `io/` are **legacy read-only** (migration of old files). Never add new persisted fields to the legacy path — they go in the MusicXML writer/reader.
- dom/ — the document model (`Song`, `Line`, elements), not a DOM/XML tree
- layout/ vs engraving/ — `layout/` computes positions and spacing; `engraving/` holds staff geometry and engraving constants
- binding/ vs ui/binding/ — `binding/` is the Swing-free property graph (`Bindings`, `Property`, `ViewProperty`); `ui/binding/` holds only the views over Swing controls (`Controls`, `Widgets`). A view over non-Swing storage is a `ViewProperty` and belongs with that storage, not in either package

### Key Entry Points

- `SongScribe.java` — application bootstrap (`main()`)
- `ui/component/MainFrame.java` — main window (singleton)
- `dom/Song.java` — the document model

### Guides

`guides/` holds only what the code, the Javadoc and the Serena tools do not give. Each subfolder holds one kind of file, and each kind has its own rules for what a file may contain:

- `guides/subsystems/` — the entry point, the member types and the cross-type rules of one subsystem.
- `guides/conventions/` — rules for one kind of code or one activity, in any subsystem.
- `guides/concepts/` — domain terms that two or more types use, each with the type that models it.
- `guides/tool-references/` — the defects and hidden capabilities of an external tool.

Two files for the same area have the same name in different subfolders. Get structure from the Serena tools, and purpose, contract and rationale from the Javadoc. Read [Guides](guides/conventions/guides.md) before you add or change a guide. Check for a matching guide before a non-trivial change to one of these areas.

### Spawning Fresh Subagents

When spawning a fresh subagent (with `subagent_type`) for Java work, include in the prompt: *"Read `.claude/rules/serena.md` and follow it for all Java exploration and refactoring."* Forked subagents inherit this rule and need no reminder.

### Required Reading by Task

**MANDATORY:** If a task touches any area below — even tangentially — read the linked guide first; these subsystems have project-specific conventions that override language and framework defaults.

- **Accidentals** — the chain walk, the five note states, or the accidental actions: [Accidentals](guides/subsystems/accidentals.md).
- **Writing or changing a method, class, or package contract** — any new or changed nontrivial API: [Contracts](guides/conventions/contracts.md).
- **Writing or changing a code comment** (`//` or `/* */`): [Comments](guides/conventions/comments.md).
- **User-facing strings** (new, changed, moved, or referenced): [Strings](guides/conventions/strings.md).
- **MusicXML** — reading, writing, the `ScorePartwise` object graph, a builder or mapper, or any question about what a MusicXML file may contain: [MusicXML](guides/subsystems/musicxml.md). Note especially that **only SongScribe-authored files are read** — foreign input is rejected at the provenance gate, so no design effort goes into supporting it.
- **Lyrics or verses** — syllables, hyphen chains, melismas, the lyric editor, or anything indexed by verse: [Lyrics](guides/subsystems/lyrics.md) and [Lyrics concepts](guides/concepts/lyrics.md).
- **Pixels, staff-spaces, or conversion between them**: [Spatial Units](guides/conventions/spatial-units.md) and [Spatial Unit concepts](guides/concepts/spatial-units.md).
- **Zoom** — `ViewScale`, `ScoreView`'s zoom-apply path, `ZoomController`, the `Ss`/`DocPx`/`ViewPx` unit types, or the paint-transform scale factor: [Zoom](guides/subsystems/zoom.md).
- **MBassador message bus** — posting, subscribing, `@Handler` methods, or reading code that uses them: [Messages](guides/conventions/messages.md).
- **Undo — `Mutation` records**, modification brackets, or `SongDidChangeNotification`: [Undo](guides/subsystems/undo.md).
- **The change report and the modification rules** — anything the song changes on its own during an edit or a load (a span or tie removed, a tuplet repaired), `ChangeReport`, `ReportedChange`, `ModificationRule`, `Span.removeReported`, or the Messages panel: [The Change Report](guides/subsystems/change-report.md).
- **Lint** — a lint rule, the linter, a rule's flags (what a finding blocks), or how the score and the Messages panel show a finding: [Lint](guides/subsystems/lint.md).
- **Key changes** — line keys and inheritance, mid-line key changes, cautionary rendering, or the cancellation policy: [Key Changes](guides/subsystems/key-changes.md) and [Key Change concepts](guides/concepts/key-changes.md).
- **Spans** — beams, hairpins, endings, trills, tuplets, `LineSpans`, `Span.outcomeFor`, a `spanning` factory, or a `ModificationRule`: [Span Invalidation](guides/subsystems/span-invalidation.md).
- **Hairpins** — `Hairpin`, `PendingHairpin`, `HairpinAction`, or where a hairpin or a dynamic may go: [Hairpin Editing](guides/subsystems/hairpin-editing.md).
- **Cut, copy and paste** — `ClipboardManager`, `Fragment`, `FragmentPaste`, `PasteModeManager`, or `InsertionPointMode`: [Clipboard](guides/subsystems/clipboard.md).
- **Line layout** — `LayoutEngine`, spacing, springs, vertical stacking, `StaffExtents`, or what the renderer may measure: [Line Layout](guides/subsystems/line-layout.md), [Line Layout concepts](guides/concepts/line-layout.md) and [Layout Geometry concepts](guides/concepts/layout-geometry.md).
- **Ties** — `Tie`, `BezierBow`, or where a tie is drawn: [Tie Placement](guides/subsystems/tie-placement.md).
- **The score view** — `ScoreView`, its panels, `ScoreComponent`, `LineComponent`, overlays, or a mouse listener on a score component: [The Score View](guides/subsystems/score-view.md).
- **Selection** — `SelectionCoordinator`, `Selection`, drag selection, `SweepRange`, or hit regions: [Selection](guides/subsystems/selection.md) and [Selection concepts](guides/concepts/selection.md).
- **Song tempo** — `Tempo`, `TempoMarking`, `SongTempoMark`, or a tempo change: [Song Tempo](guides/subsystems/song-tempo.md).
- **`JOptionPane`-based alerts, confirms, or input prompts**: [OptionDialogs](guides/conventions/option-dialogs.md).
- **Complex dialogs** (`BaseDialog`, `StandardDialog`, tabs, validation/commit lifecycle), **or the controller behind one** (`DialogOps`, `DialogController`, any `*Controller` in `ui/dialog/`): [Dialogs](guides/conventions/dialogs.md). It states what a dialog may and may not touch, which is the rule a controller exists to keep, and that the controller rather than the dialog is what carries tests.
- **User preferences** (`Prefs`, `PrefsKey`, `defaults.json`, `PrefsDidChangeNotification`): [Preferences](guides/conventions/prefs.md).
- **Bindings** (`Bindings`, `Property`, `ObservableValue`, `ValueProperty`, `ViewProperty`, `Computed`, `Controls`, `Widgets`), or wiring a Swing listener for state a binding could carry: [Bindings](guides/conventions/bindings.md).
- **Custom UI constants** (`FlatLafProps`, `FlatLafKeys`, `FlatLaf.properties`): [FlatLaf Properties](guides/conventions/flatlaf-props.md).
- **File-based logging**: [Logging](guides/conventions/logging.md). If the user says, "check the log", read this guide to know where to look.
- **Nullability** — `@Nullable`, `@NullMarked`, NullAway suppressions, deferred-init fields, `requireXxx()` accessors, or reacting to an unexpected null: [Null Handling](guides/conventions/null-handling.md).
- **Creating a new singleton class**: [Singletons](guides/conventions/singletons.md).
- **Disposing an object, or writing a class that registers itself with anything process-global** (`Disposable`, `dispose()`, a constructor-side `MessageCenter.subscribe`): [Lifecycle](guides/conventions/lifecycle.md).
- **SMuFL glyph names, codepoints, or ranges**: look up at `https://w3c.github.io/smufl/latest/index.html?search=<search terms>`.
- **LilyPond source**: If the user mentions LilyPond source, it is found at ~/Developer/projects/lilypond/lily/.
- **ABC corpus** — If the user mention the "abc corpus", that refers to the .abc files in the numbered directories in `~/Documents/Centre/Music/SongScribe songs/ABC`.
- **Naming a code boundary, or using "seam" for anything in this codebase**: [Vocabulary](guides/conventions/vocabulary.md).
- **A git operation that references a base branch** — diff, checkout, rebase, or PR creation: [Branch Topology](guides/conventions/branch-topology.md).
