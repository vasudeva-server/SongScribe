# TODOs

## Follow-up in-place lyric editor must cover UNDER / BANGLA / TRANSLATED

**What:** The new per-note in-place lyric editor (planned as a follow-up to Issue
#289) must support not only MAIN lyrics but also `underLyrics`, `banglaLyrics`,
and `translatedLyrics`.

**Why:** Issue #289 deletes `LyricsDialog`, which was the only editor for UNDER
and TRANSLATED as well as MAIN. After Issue #289 ships, those three fields
become permanently read-only unless the follow-up editor covers them. (Bangla
already had no editor — `LyricsDialog` always passed `null` for it — so the
follow-up is the first real editing surface for Bangla.)

**Context:** `LyricsDialog.getData()` loaded all three text areas:
`lyricsArea`, `underSongArea`, `translatedArea`. These correspond to the
`LyricsField.UNDER`, `LyricsField.TRANSLATED`, `LyricsField.BANGLA` mutation
paths (`MAIN` is removed). After Issue #289, UNDER/BANGLA/TRANSLATED remain on
`Composition` as line-block strings but have no write path. Release notes for
#289 should flag the temporary read-only state.

**Depends on:** Issue #289 (per-note lyrics migration).

## Add `Song.lyricLanguages` and wire it into SongIO

**What:** Add a `lyricLanguages` field (`Map<Integer, Locale>`) to `Song`, then
serialize and deserialize it in `SongIO`, mirroring MusicXML
`<lyric-language number="N">`.

**Why:** The field does not exist yet. When multi-verse or multi-language lyric
support lands, the per-verse locale needs to round-trip through the `.mssw` (and
eventually MusicXML) file format.

**Context:** Field was originally in the Issue #289 plan but deferred during
scope review. Signature: `Map<Integer, Locale>` keyed by verse number. MusicXML
attaches `<lyric-language>` to `<score-part>`; importer/exporter needs to bridge
that to the per-verse map. Note: the domain model class is `Song` (not
`Composition`) and its IO class is `SongIO` (not `CompositionIO`) — the original
TODO predated those names.

**Depends on:** Multi-verse lyric editing (follow-up to Issue #289) needing
language metadata.

## MusicXML export/import implementation

**What:** Implement `.musicxml` export/import for lyrics using the model pinned
by Issue #289: `Lyric(verse, SyllableRelation, text, extend)` with
`SyllableRelation ∈ {NONE, SYLLABLE, COMPOUND_WORD}`.

**Why:** The long-term goal per Issue #289's context is for MusicXML to become
the native document format. The internal model was specifically chosen to
round-trip cleanly to/from MusicXML. Without the export/import layer, the
design choice sits unused.

**Context:** Derivation logic (pinned during #289 plan review):

- `<syllabic>` value at export: look at current + previous Lyric relation
  - prev.relation == NONE (or no prev) && L.relation != NONE → `begin`
  - prev.relation == NONE (or no prev) && L.relation == NONE → `single`
  - prev.relation != NONE && L.relation != NONE → `middle`
  - prev.relation != NONE && L.relation == NONE → `end`
- Add `type="compound"` attribute to `<syllabic>` when `L.relation == COMPOUND_WORD`
- Emit `<extend/>` (or `<extend type="start/continue/stop"/>` for multi-note
  melismas) when `L.extend == true`
- Keep `<text>` content clean — do NOT embed hyphen characters (causes double
  hyphens in other engravers)

Import inverts: strip trailing hyphen (if any) defensively; parse
`<syllabic type="compound">` → `COMPOUND_WORD`; reconstruct relation from
`<syllabic>` value + neighbor context. Validated during plan review: the
`type="compound"` attribute is tolerated by MusicXML validators and MuseScore.

**Depends on:** Issue #289 (model migration) complete.

## `.mxl` compressed save format

**What:** Add a save option for MusicXML's standard compressed format (`.mxl`
zip envelope) once MusicXML export is the native path.

**Why:** Per-note `<lyric>` serialization grows the raw XML substantially for
lyrics-heavy compositions (~5-8x over the old single-block format). The `.mxl`
zip envelope compresses repetitive XML tags efficiently, absorbing the growth
with no model changes.

**Context:** MusicXML 4.0 specification for compressed format:
https://www.w3.org/2021/06/musicxml40/tutorial/compressed-mxl-files/. The
`.mxl` format is a zip containing the `.musicxml` document plus a manifest.
Other MusicXML consumers (MuseScore, Finale, Sibelius) open `.mxl` directly.

**Depends on:** MusicXML export/import implementation (previous TODO).

## Full EDT-quiesce between shutdown cleanup and `System.exit`

**What:** Eliminate the window between the EDT cleanup phase running and
`System.exit(0)` being called, during which pending EDT events (Swing timers,
queued runnables, repaint requests) may still execute and touch resources that
cleanup tasks just released.

**Why:** The shutdown registry (`specs/shutdown-registry.md`) runs EDT cleanup
on the EDT, then immediately calls `System.exit(0)`. Any work already in the
EDT queue at that point — Swing timers firing one last time, a queued
`SwingUtilities.invokeLater` runnable, a deferred repaint — can run between
"cleanup ran" and "JVM exits." If that work touches MIDI (closed by the JVM
cleanup phase), reads a now-disabled main frame, or otherwise depends on
resources released during cleanup, the result is a silent error on the way out
or, worse, a crash that shadows the user's clean-quit intent. The v1 partial
mitigation is registering `mainFrame.setEnabled(false)` as the first EDT
cleanup task (runs first via LIFO), which prevents new user-initiated events
but does not stop timers or already-queued runnables.

**Context:** Full quiesce would stop all `javax.swing.Timer` instances,
drain the EDT event queue, and only then proceed to JVM cleanup. The hard
parts: discovering all live timers (no global registry today), deciding what
"drain" means (events scheduled by other in-flight cleanup tasks would loop),
and bounding how long quiesce can take before just exiting anyway. Reasonable
starting point: keep a weak registry of `javax.swing.Timer`s created by the
app and stop them as the first JVM cleanup task, accept that event-queue
events may still slip through, and revisit if any concrete crash-on-quit is
observed.

**Depends on:** Shutdown registry shipped (`specs/shutdown-registry.md`).

## Finish decoupling actions from the MainFrame singleton — RESOLVED (2026-06-19)

**Resolved by** `plans/finish-mainframe-decoupling.md`. The four transitive
`MainFrame.getInstance()` routes reachable from action `doActionPerformed` paths
were cut:

- **Route C** — `Actions` no longer resolves `MainFrame.getInstance()` at class
  load; constants are populated by `Actions.initialize(MainFrame)`, called at the
  top of `MainFrame.initFrame()`.
- **Route D** — `PlaybackController` mirrors the same `initialize(MainFrame)`
  holder pattern.
- **Route A** — `BaseDialog`/`StandardDialog`/`AttachmentDialog` constructors take
  a `MainFrame`; `DialogOpenAction` builds via a `Function<MainFrame, T>` factory
  instead of reflection.
- **Route B** — `EndingConfirms` takes a `Component parent` instead of fetching the
  singleton.

**Deliberately out of scope (remaining `getInstance()` callers, non-action paths):**
- `PreviewElementManager` (preview-element insert/modify mouse handlers).
- `uiconverter.ConvertAction` (standalone converter utility, `extends AbstractAction`).

Both pass `MainFrame.getInstance()` at the call site into the new required
dialog constructors (decision 4A). Because these paths remain, the shared
`MainFrameMockTest` `mockStatic(MainFrame.class)` is retained (and documented in
that class); `SongScribeTest` and `MainFrameTest` keep their two legitimate
bootstrap mocks. Action-level tests no longer require the static mock.

**Note (NullAway deviation from plan decision 3A):** the `Actions.*` /
`PlaybackController.*` constants are `@NonNull` with class-level
`@SuppressWarnings("NullAway.Init")`, not `@Nullable`. Making them `@Nullable`
(as 3A's "null all constants in teardown" implied) would poison ~70 call sites
under NullAway. Teardown therefore clears only the injected frame holder; the
per-test `initialize()` call guarantees fresh constants.

See the "Automated guard" TODO below for turning the manual verification greps into
an enforced test.

## Undo/redo (#14) must replay the new attribution mutation

**What:** When undo/redo (#14) is implemented, its replay engine must handle the
coarse `MetadataChange(MetadataField.ATTRIBUTION, oldSongMetadata,
newSongMetadata)` record-swap introduced by the attribution refactor.

**Why:** The mutation is pure groundwork — it is emitted (from
`Song.setMetadata()`) and validated today but nothing consumes it, because no
replay engine exists yet. It is easy to overlook when #14 lands: the metadata
change collapses 11 former per-field changes into a single record swap, so a
naive replay that diffs scalar fields will mishandle it.

**Context:** `MetadataChange` lives in `songscribe.message.mutation`; its
canonical constructor validates `oldValue`/`newValue` against
`MetadataField.getExpectedType()` (= `SongMetadata.class` for `ATTRIBUTION`).

NOTE (corrected 2026-06-19): An earlier version of this TODO also described a
"cross-line attribution-migration mutation emitted by `Song.addLine(0, …)` /
`Song.removeLine(0)`". That mutation does not exist in the code — `addLine` /
`removeLine` emit only `LineInsertion` / `LineDeletion`, and the only ATTRIBUTION
mutation is the `MetadataChange` from `setMetadata()`. If first-line attribution
ownership needs to survive line insert/delete under undo, modeling that as a
recorded mutation is unfinished design work, not existing groundwork.

**Depends on / blocked by:** Issue #14 (undo/redo) — not started; this refactor
is the groundwork.

## MusicXML attribution export via `AttributionFormatter`

**What:** Wire attribution text into MusicXML export using `AttributionFormatter`
+ `SongMetadata`, mirroring how `SongIO.writeSong` and `ExportABCAction.writeABC`
already format attribution.

**Why:** The attribution refactor deliberately moved formatting into a
UI-free `AttributionFormatter` in `songscribe.dom` so IO, ABC, and MusicXML
could all format from `SongMetadata` without a Swing/pane dependency. ABC and
`.mssw` IO are wired; MusicXML is the one consumer the design anticipated but
left unbuilt (target architecture note: "(later) MusicXML"). Until it is wired,
the no-pane-dependency design sits partially unused for the MusicXML path.

**Context:** `AttributionFormatter` entry points to call:
`text(SongMetadata, boolean showTranslation)` for multi-line attribution and
`singleLineText(SongMetadata, boolean)` for a single-line rendering; derive the
`showTranslation` flag from `Song.showTranslation()`. This is separate from the
existing "MusicXML export/import implementation" TODO above, which covers
lyrics; this entry is specifically the title/composer/lyricist/date attribution
block. Place the credit text in the MusicXML `<credit>` / `<identification>`
elements as appropriate.

**Depends on / blocked by:** MusicXML export path existing (see the lyrics
MusicXML TODO above); `AttributionFormatter` + `SongMetadata` shipped by this
refactor.

## Automated guard: no action path may reach `MainFrame.getInstance()`

**What:** Add a test (ArchUnit-style, or a focused reflection/source scan test)
that fails the build if any `doActionPerformed` path — directly or transitively
through `Actions`, `PlaybackController`, `BaseDialog`, or `EndingConfirms` —
reaches `MainFrame.getInstance()`. The only permitted callers are the
`MainFrame` definition itself and the deliberately out-of-scope non-action
click-handler paths.

**Why:** The "finish decoupling actions from the MainFrame singleton" work
(plan `plans/finish-mainframe-decoupling.md`) enforces this invariant today only
via two manual `rg` checks in the plan's verification checklist
(`rg "MainFrame.getInstance\(\)"` and `rg "mockStatic\(MainFrame.class\)"`). A
manual grep is not run on CI and silently rots: the moment someone reintroduces
a transitive `getInstance()` in an action path, every unit test still passes
(they self-inject via `Actions.initialize(mockFrame)`), so the regression is
invisible until it surfaces as a hard-to-mock test or a startup NPE. An
automated guard makes the decoupling a permanent, enforced property instead of a
point-in-time cleanup.

**Context:** The decoupling reduces `mockStatic(MainFrame.class)` to exactly two
legitimate bootstrap mocks (`SongScribeTest`, `MainFrameTest`); everything else
was cut by Routes A–D. The guard should encode both invariants: (1) no
action-reachable `getInstance()`, and (2) no `mockStatic(MainFrame.class)`
outside the two bootstrap tests. ArchUnit can express (1) as a "no classes in
`songscribe.ui.action..` should call method `MainFrame.getInstance()`" rule plus
the transitive-dependency variant; (2) is simpler as a source-scan test over
`src/test`. Start from the plan's two `rg` patterns — they already define the
exact allow-list. Check whether the project already has an ArchUnit dependency
before adding one; if not, a lightweight source-scan unit test may be the
lower-friction first cut.

**Depends on / blocked by:** Completion of
`plans/finish-mainframe-decoupling.md` (the invariant must actually hold before a
guard can be made green).
