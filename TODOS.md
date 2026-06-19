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

## Wire `Composition.lyricLanguages` into CompositionIO

**What:** Serialize and deserialize `Composition.lyricLanguages`
(`Map<Integer, Locale>`) in `CompositionIO`, mirroring MusicXML
`<lyric-language number="N">`.

**Why:** The field is currently reserved but never serialized. When multi-verse
or multi-language lyric support lands, the per-verse locale needs to round-trip
through the `.mssw` (and eventually MusicXML) file format.

**Context:** Field was originally in the Issue #289 plan but deferred during
scope review. Signature: `Map<Integer, Locale>` keyed by verse number. MusicXML
attaches `<lyric-language>` to `<score-part>`; importer/exporter needs to bridge
that to the per-verse map.

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
touching the score view / selection coordinator). Evidence: 18 of 26 migrated test
files still require `mockStatic(MainFrame.class)`. The constructor injection is
therefore only a partial decoupling — construction is clean, behavior paths are not.

**Context:** Start by tracing which collaborators action `doActionPerformed`
implementations pull from `MainFrame.getInstance()` (score view, selection
coordinator, controller). These are the same objects `MockEnvHelper.setupMockEnv`
stubs, so that helper is a map of the transitive surface. Inject those collaborators
rather than fetching the singleton. Once a code path no longer calls `getInstance()`,
its test can drop to a plain injected mock (no static mock).

**Depends on / blocked by:** Easiest after the centralized `MainFrameMockTest` lands
(Phase 2 of plans/issue-375-review-followups.md), so per-test cleanup happens in one
place. Larger effort; do incrementally, one action family at a time.

## Undo/redo (#14) must replay the new attribution mutations

**What:** When undo/redo (#14) is implemented, its replay engine must handle the
two new mutation types introduced by the attribution refactor: the coarse
`MetadataChange(MetadataField.ATTRIBUTION, oldSongMetadata, newSongMetadata)`
record-swap, and the cross-line attribution-migration mutation emitted by
`Song.addLine(0, …)` / `Song.removeLine(0)`.

**Why:** Both mutations are pure groundwork — they are emitted and validated
today but nothing consumes them, because no replay engine exists yet. They are
easy to overlook when #14 lands: the metadata one collapses 11 former per-field
changes into a single record swap (so a naive replay that diffs scalar fields
will mishandle it), and the migration one moves the `Attribution` element
between `Line`s (so replaying a `LineInsertion`/`LineDeletion` alone will leave
the attribution on the wrong line — desyncing its geometry and `userYOffsetSs`
after undo). The whole reason the migration was modeled as a recorded mutation
(review decision 1C) rather than an imperative side effect was to make this
replay correct; that intent is lost if #14 doesn't wire it up.

**Context:** `MetadataChange` lives in `songscribe.message.mutation`; its
canonical constructor validates `oldValue`/`newValue` against
`MetadataField.getExpectedType()` (= `SongMetadata.class` for `ATTRIBUTION`).
The migration mutation is emitted inside the same `withModification` /
`applyChange` brackets that already carry the last-line terminal-invariant
maintenance in `addLine`/`removeLine` — model the replay symmetrically to how
terminal maintenance replays. `Song.getFirstLineAttribution()` is the
invariant-enforcement point (asserts the first line owns the attribution);
replay must keep that invariant true.

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

## De-Swing the Song Settings attribution preview

**What:** Replace the interim `JComponent` wrapper added in the attribution
refactor (`SongSettingsDialog.TextTab`) with a preview that renders the bare
`AttributionPane` directly, once the pane's measure/render API has settled.

**Why:** The refactor made `AttributionPane` a UI-free rendering surface in
`songscribe.dom` (no longer a `JComponent`), so the dialog needs a small Swing
adapter to host the live preview. That wrapper was explicitly scoped as
"interim" — it re-introduces Swing glue the refactor otherwise removed. Once the
pane API is stable, the preview can paint the pane without a bespoke wrapper.

**Context:** The interim wrapper's `getPreferredSize()` returns the pane's
measured size (passing the dialog's fonts) and `paintComponent` calls
`pane.render(g2, 0, 0, getWidth(), attributionFont, subAttributionFont)`;
`refreshPreview()` builds a `SongMetadata` from widget state and feeds the
formatter via `setOverrideLines(...)`. A cleaner end state is a reusable
pane-hosting Swing component (not dialog-private) that any panel can drop in,
or a small canvas that delegates straight to `AttributionPane.render`. Revisit
after the pane's caching (review decision P1A) and font-parameter signatures
have proven stable across the score and dialog call sites.

**Depends on / blocked by:** Attribution refactor shipped; pane measure/render
API stable.

## Consolidate `MusicXmlWriterSchemaTest` into `MusicXmlRoundTripTest`

**What:** `MusicXmlWriterSchemaTest` has become mostly redundant — its only case
(`testEmptyDefaultSongIsSchemaValid`) duplicates
`testEmptySongWriterOutputIsSchemaValid` in `MusicXmlRoundTripTest`, and every
Phase 2+ scenario already pairs its round-trip with an inline
`MusicXmlSchemaValidator` `doesNotThrowAnyException()` check in
`MusicXmlRoundTripTest`. Fold the lone case in and delete the separate class (or
keep it only if a clear write-only-schema scope emerges).

**Why:** Two test classes asserting the same thing drift apart and dilute intent;
a single home for "writer output is schema-valid" keeps the suite DRY and makes
it obvious where to add new schema assertions.

**Context:** `MusicXmlSchemaValidator` (the reusable XSD validator) is unaffected
and stays — only the thin `MusicXmlWriterSchemaTest` wrapper is the redundancy.
The MusicXML Phase 3 plan already routes new note-output schema checks through
`MusicXmlRoundTripTest`'s inline pattern, so this consolidation aligns the
existing empty-song case with that convention.

**Depends on / blocked by:** Best done after MusicXML Phase 3 lands (it adds the
note-output schema cases that make the consolidation worthwhile); no hard
blocker.
