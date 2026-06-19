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

## Finish decoupling actions from the MainFrame singleton

**What:** Thread `MainFrame` (or narrower collaborators) through the call sites that
still reach `MainFrame.getInstance()` *transitively* from action code paths, so
action-level unit tests no longer need `mockStatic(MainFrame.class)`.

**Why:** Commit f59f21f3 (#375) injected `MainFrame` into `UIAction` constructors,
but `getInstance()` is still reached indirectly (e.g. via `doActionPerformed`
touching the score view / selection coordinator). Evidence (as of 2026-06-19):
`mockStatic(MainFrame.class)` still appears in 5 test files (10 occurrences) —
`MainFrameMockTest` (base-class `@BeforeEach`), `SongScribeTest`, `MainFrameTest`,
`MenuControllerTest`, and `GraceModeManagerTest` (6 of the 10). The constructor
injection is therefore only a partial decoupling — construction is clean, behavior
paths are not. Main-source `getInstance()` calls also persist transitively via the
`BaseDialog` constructor and `EndingConfirms.showEndingConfirm()`.

**Context:** Start by tracing which collaborators action `doActionPerformed`
implementations pull from `MainFrame.getInstance()` (score view, selection
coordinator, controller). These are the same objects `MockEnvHelper.setupMockEnv`
stubs, so that helper is a map of the transitive surface. Inject those collaborators
rather than fetching the singleton. Once a code path no longer calls `getInstance()`,
its test can drop to a plain injected mock (no static mock).

**Depends on / blocked by:** The centralized `MainFrameMockTest` base class has
landed (Phase 2 of plans/issue-375-review-followups.md). Larger effort remaining;
do incrementally, one action family at a time.

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
