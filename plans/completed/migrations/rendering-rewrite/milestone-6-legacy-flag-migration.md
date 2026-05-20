# Sub-plan: Legacy Decoration Flag + SpanSet Migration

**Type:** Sub-plan  <br>
**Parent:** [rendering-rewrite.md](rendering-rewrite.md) → Phase 6  <br>
**Created:** 2026-05-19  <br>
**Status:** Complete  <br>
**BlockedBy:** —

**Spec:** [specs/rendering-rewrite.md](../../../specs/rendering-rewrite.md) -- always read the spec before implementing tasks.

---

## Status Dashboard

| Phase | Description | Status |
|-------|-------------|--------|
| 1 | [Fermata flag → FermataAttachment](#-phase-1-fermata-flag--fermataattachment) | ✅ Complete |
| 2 | [Trill flag → Trill RangeElement](#-phase-2-trill-flag--trill-rangeelement) | ✅ Complete |
| 3 | [TempoChange → TempoChangeAttachment](#-phase-3-tempochange--tempochangeattachment) | ✅ Complete |
| 4 | [BeatChange → BeatChangeAttachment](#-phase-4-beatchange--beatchangeattachment) | ✅ Complete |
| 5 | [Annotation → AnnotationAttachment](#-phase-5-annotation--annotationattachment) | ✅ Complete |
| 6 | [TieSpan → Tie RangeElement](#-phase-6-tiespan--tie-rangeelement) | ✅ Complete |
| 7 | [DynamicsSpan → Crescendo/Diminuendo RangeElements](#-phase-7-dynamicsspan--crescendodiminuendo-rangeelements) | ✅ Complete |
| 8a | [Tuplet core migration (mutations, actions, IO, clipboard)](#-phase-8a-tuplet-core-migration) | ✅ Complete |
| 8b | [Tuplet renderer + stacker bridges + delete TupletSpan](#-phase-8b-tuplet-renderer--delete-tupletspan) | ✅ Complete |
| 8c | [Tuplet test sweep](#-phase-8c-tuplet-test-sweep) | ✅ Complete |
| 9a | [Create Beam class + rewrite mutations + migrate toggleBeaming](#-phase-9a-create-beam--mutations--actions) | ✅ Complete |
| 9b | [Beam IO, FormatMigrator, clipboard, beam algorithm entry, renderer + delete BeamSpan](#-phase-9b-beam-io--clipboard--renderer--delete-beamspan) | ✅ Complete |
| 9c | [Beam test sweep](#-phase-9c-beam-test-sweep) | ✅ Complete |
| 10 | [Delete SpanSet + Span types, remove bridging](#-phase-10-delete-spanset--span-types-remove-bridging) | ✅ Complete |

## Overview

Two parallel legacy representations coexist with the new layout types:

1. **Boolean / object decoration flags on `StaffElement`** — `isFermata()`, `isTrill()`, `getTempoChange()`, `getBeatChange()`, `getAnnotation()`. User actions, file I/O, export, copy/paste, and MIDI use these flags; layout/rendering uses the new `Attachment` / `RangeElement` types with bridging code in `VerticalStackingCalculator` and the stackers.
2. **`SpanSet<Span>` containers on `Line`** — `BeamSpan`, `TieSpan`, `TupletSpan`, two `DynamicsSpan` sets (crescendo, diminuendo). The new equivalents (`Tie`, `Tuplet`, `Crescendo`, `Diminuendo`, `Trill`, `Ending`) extend `RangeElement` and live in `Line.rangeElements`.

This milestone collapses both into the new representation. When the milestone is done:

- All five legacy decoration flag pairs are removed from `StaffElement`.
- `SpanSet`, `Span`, `BeamSpan`, `TieSpan`, `TupletSpan`, `DynamicsSpan` no longer exist; `Line.rangeElements` is the only span container.
- A new `Beam` `RangeElement` exists alongside `Tie`, `Tuplet`, etc.
- Bridging code in `VerticalStackingCalculator`, the stackers, and renderers reads only the new types.
- `FormatMigrator` retains its v2.0 / v2.1 paths but loads into the new types directly.

## Key Design Decisions

1. **One subsystem per phase.** Each phase migrates a single legacy field or `SpanSet` end-to-end (model → user actions → I/O → copy ctor → export → MIDI → UI → tests). This keeps each phase compileable and shippable.
2. **Migration order: leaves first.** Boolean flags (`fermata`, `trill`) and attachments come before `SpanSet` migrations because they have fewer callers and smaller blast radius. `SpanSet` migrations follow in order of complexity: `Tie` → `Dynamics` → `Tuplet` → `Beam`.
3. **Mutations migrate with their data.** `TieAddition`, `BeamingAddition`, `CrescendoAddition`, `DiminuendoAddition`, `TupletAddition` (and their `*Removal` counterparts) are rewritten to carry the new `RangeElement` instead of the old `Span`. Equivalent semantics are preserved so the undo/redo system is unaffected.
4. **`FormatMigrator` reads legacy first, writes new.** Each phase teaches `FormatMigrator` to map the legacy XML element to a new `Attachment` / `RangeElement` while removing the old write path from `StaffElementIO` / `LineIO`. The v2.1 file format already stores positions in the new types; the on-disk decoration tags change name only where the legacy name was tied to the legacy flag.
5. **Beam needs a new class.** Phase 9 introduces `songscribe.ui.layout.Beam extends RangeElement`. The existing `BeamGroupRenderer` / `BeamLayout` already operate on layout-time data; the model only needs to carry the range, so `Beam` stays thin.
6. **Bridging code removed at the end.** Phases 1–9 each delete the legacy field they migrated, but cross-cutting bridges in `VerticalStackingCalculator` and stackers (which currently translate flags to attachments at layout time) are removed in Phase 10 once all phases have landed.
7. **Snapshot per phase.** At the end of each phase, run `git stash push --include-untracked -m "Finished phase N" && git stash apply` as a single Bash invocation to leave a rollback point.

## Per-phase migration template

Each of phases 1–9 follows this template. Phases vary only by which flag / `SpanSet` they target and which callers exist.

1. Model: add/confirm the new type's storage on `Line` or `StaffElement` (most already exist); ensure mutations exist for add/remove.
2. User actions and edit operations: toggle/create/remove the new type instead of the legacy flag/Span.
3. File I/O: `StaffElementIO` / `LineIO` read and write the new type; remove the legacy write path.
4. `FormatMigrator`: map the legacy element on v2.0 / v2.1 load to the new type so old files still open.
5. Copy constructor (`StaffElement`) and clipboard: copy via the new type.
6. Export (ABC, MIDI, etc.): read from the new type.
7. UI (dialogs, menus, actions): operate on the new type end-to-end.
8. Delete the legacy field, getter, setter, and any associated `Span` subclass; update tests.
9. `./scripts/compile.sh` + targeted `./scripts/test.sh` for the affected subsystem.

---

## ✅ Phase 1: Fermata flag → FermataAttachment

**Status:** Complete  <br>
**BlockedBy:** —  <br>
**Recommended model/effort:** Sonnet 4.6, low effort — mechanical end-to-end migration of a boolean flag with a small caller set; existing tests gate correctness.

### Tasks

1. Replace `setFermata`/`isFermata` with `FermataAttachment` create/remove in `FermataAction`, `EditModeManager`, `FermataMenuItem`, `MusicEditOperations`.
2. Update `StaffElementIO` read/write paths: load legacy `fermata="true"` into a `FermataAttachment`; serialize only the new attachment going forward.
3. Update `FormatMigrator` v2.0 / v2.1 paths to produce `FermataAttachment` instead of setting the boolean.
4. Update `ExportABCAction` and any MIDI path to read `FermataAttachment` from the element.
5. Replace `StaffElement` copy constructor's boolean copy with attachment copy.
6. Remove `fermata` field, `isFermata`, `setFermata` from `StaffElement`; remove fermata-flag bridging in `NoteAttachedStacker` and `FermataRenderer`.
7. Update fermata tests (`FermataActionTest`, `FermataTrillStackingTest`, copy-constructor / batch mutation tests) and run `./scripts/compile.sh && ./scripts/test.sh`.

---

## ✅ Phase 2: Trill flag → Trill RangeElement

**Status:** Complete  <br>
**BlockedBy:** —  <br>
**Recommended model/effort:** Sonnet 4.6, low effort — same shape as Phase 1; `Trill` `RangeElement` already exists.

### Tasks

1. Replace `setTrill(true/false)` calls in `MusicEditOperations` with `Trill` creation/removal via `Line.addRangeElement` / `removeRangeElement`.
2. Update `StaffElementIO` / `LineIO` read/write paths: load legacy `trill="true"` runs into `Trill` instances; emit `Trill` going forward.
3. Update `FormatMigrator` v2.0 / v2.1 paths to coalesce contiguous trill flags into `Trill` range elements.
4. Update `ExportABCAction` and any export path to read `Trill` `RangeElement` instead of the boolean.
5. Replace `StaffElement` copy constructor's `trill` copy with no-op (range elements copy via clipboard at line scope).
6. Remove `trill` field, `isTrill`, `setTrill` from `StaffElement`; remove `Line.getFirstTrill()` (or rewrite over `findRangeElements(Trill.class)`); remove trill-flag bridging in `NoteAttachedStacker`.
7. Update trill tests (`FermataTrillStackingTest`, `StaffElementCopyConstructorTest`, preview-attachment tests) and run `./scripts/compile.sh && ./scripts/test.sh`.

---

## ✅ Phase 3: TempoChange → TempoChangeAttachment

**Status:** Complete  <br>
**BlockedBy:** —  <br>
**Recommended model/effort:** Sonnet 4.6, medium effort — wider caller surface (MIDI, playback, ABC export, converter) but mechanically uniform.

### Tasks

1. Migrate `TempoChangeDialog` and any tempo-change actions to read/write `TempoChangeAttachment` via the attachment API on `StaffElement`.
2. Update `StaffElementIO` / `LineIO` / `SongIO` to serialize the attachment; load legacy `Tempo` element into the attachment.
3. Update `FormatMigrator` v2.0 / v2.1 to produce `TempoChangeAttachment` on load.
4. Update playback / MIDI / converter callers (`PlaybackController`, `LineTrackBuilder`, `MidiConverter`, `ConvertAction`, `ExportMidiDialog`, `ExportABCAction`) to read `TempoChangeAttachment`.
5. Update copy constructor and clipboard to copy the attachment.
6. Remove `tempoChange` field, `getTempoChange`, `setTempoChange` from `StaffElement`; remove tempo-flag bridging in `SystemStacker`, `TempoChangeRenderer`, `VerticalAdjustment`.
7. Update tempo tests (`SystemTierStackingTest`, `GlissandoMidiIntegrationTest`) and run `./scripts/compile.sh && ./scripts/test.sh`.

---

## ✅ Phase 4: BeatChange → BeatChangeAttachment

**Status:** Complete  <br>
**BlockedBy:** —  <br>
**Recommended model/effort:** Sonnet 4.6, low effort — narrower than tempo (no MIDI/playback); same shape as Phase 3.

### Tasks

1. Migrate `BeatChangeDialog` and any beat-change action to operate on `BeatChangeAttachment`.
2. Update `StaffElementIO` / `LineIO` to read legacy beat change into the attachment and emit the attachment.
3. Update `FormatMigrator` v2.0 / v2.1 paths.
4. Update `ExportABCAction` and any other readers to use the attachment.
5. Update copy constructor and clipboard to copy the attachment.
6. Remove `beatChange` field, `getBeatChange`, `setBeatChange`, and `Line.getFirstBeatChange` (rewrite over attachments); remove beat-change bridging in `SystemStacker`, `BeatChangeRenderer`, `VerticalAdjustment`.
7. Update beat-change tests (`SystemTierStackingTest`) and run `./scripts/compile.sh && ./scripts/test.sh`.

---

## ✅ Phase 5: Annotation → AnnotationAttachment

**Status:** Complete  <br>
**BlockedBy:** —  <br>
**Recommended model/effort:** Sonnet 4.6, medium effort — annotation has its own font/IO path (`AnnotationIO`, `DocumentFontsHolder`), but the pattern is mechanical.

### Tasks

1. Migrate `AnnotationDialog` to read/write `AnnotationAttachment` directly on the element.
2. Update `StaffElementIO` and `AnnotationIO` to load legacy annotation into the attachment and emit the attachment only.
3. Update `FormatMigrator` v2.0 / v2.1 paths to produce `AnnotationAttachment`.
4. Update `ExportABCAction`, `ArgumentReader`, `DocumentFontsHolder`, and `AnnotationRenderer` / `MetronomeRenderer` / `ElementRenderContext` to read from the attachment.
5. Update copy constructor and clipboard to copy the attachment.
6. Remove `annotation` field, `getAnnotation`, `setAnnotation` from `StaffElement`; remove annotation-flag bridging in `SystemStacker`, `VerticalStackingCalculator`, `VerticalAdjustment`.
7. Update annotation tests (`AnnotationAttachmentTest`, `SystemTierStackingTest`, `ManualOffsetStackingTest`, `FormatMigratorTest`) and run `./scripts/compile.sh && ./scripts/test.sh`.

---

## ✅ Phase 6: TieSpan → Tie RangeElement

**Status:** Complete  <br>
**BlockedBy:** 2  <br>
**Recommended model/effort:** Opus 4.7, medium effort — requires rewriting the `TieAddition` / `TieRemoval` mutations and the index-shift / clamp semantics that `SpanSet.shiftValues` provides, while keeping undo/redo intact.

### Tasks

1. Rewrite `TieAddition` / `TieRemoval` mutations to carry a `Tie` (not `TieSpan`); update `Line.addTie` / `removeTie` to operate on `Tie` and the `rangeElements` list.
2. Port `SpanSet` overlap-merge and insert/delete index-shift behavior into the `Tie`-specific code path (anchor/end clamp on element deletion already partially handled in `Line` for endings; mirror it for ties).
3. Migrate `MusicEditOperations.toggleTie` (and any tie-toggle UI) to construct `Tie` instead of `TieSpan`.
4. Update `LineIO` / `StaffElementIO` and `FormatMigrator` to read legacy `<tie>` spans into `Tie` range elements; emit `Tie` only.
5. Update `ClipboardManager`, `Line.copySpans` / `pasteSpans`, and selection coordinators (`LineSelectionState`, `SelectionCoordinator`, `ScoreViewController`) to use `Tie`.
6. Remove `Line.ties` (the `SpanSet<TieSpan>`), `getTies`, `addTie(TieSpan)`, `removeTie(TieSpan)`; delete `TieSpan`. Remove tie-bridging in `VerticalStackingCalculator` and the tie renderer if any remains.
7. Update tie tests (`ToggleConflictTest`, `MusicEditOperationsMutationTest`, `SpanSetTest`'s tie cases) and run `./scripts/compile.sh && ./scripts/test.sh`.

---

## ✅ Phase 7: DynamicsSpan → Crescendo/Diminuendo RangeElements

**Status:** Complete  <br>
**BlockedBy:** 6  <br>
**Recommended model/effort:** Sonnet 4.6, medium effort — two parallel sets, but `Crescendo` and `Diminuendo` already extend `Hairpin`/`RangeElement`; pattern mirrors Phase 6.

### Tasks

1. Rewrite `CrescendoAddition` / `CrescendoRemoval` / `DiminuendoAddition` / `DiminuendoRemoval` mutations to carry `Crescendo` / `Diminuendo` instances.
2. Update any add/remove user actions and `Line.addCrescendo` / `addDiminuendo` to construct the `RangeElement` and add via `rangeElements`.
3. Update `LineIO` / `StaffElementIO` and `FormatMigrator` to load legacy `<crescendo>` / `<diminuendo>` spans into `Crescendo` / `Diminuendo`; emit only the new form.
4. Update `ClipboardManager`, `Line.copySpans` / `pasteSpans`, and `StructuralStacker` to read `Crescendo` / `Diminuendo` from `rangeElements`.
5. Remove `Line.crescendo` and `Line.diminuendo` (`SpanSet<DynamicsSpan>`), their getters/setters, and `DynamicsSpan`.
6. Remove crescendo/diminuendo bridging in `StructuralStacker` and any renderer; ensure horizontal-adjustment paths (`HorizontalAdjustment`, `VerticalAdjustment`) read from the new range elements.
7. Update dynamics tests (`StructuralTierStackingTest`, hairpin layout tests) and run `./scripts/compile.sh && ./scripts/test.sh`.

---

## ✅ Phase 8a: Tuplet core migration

**Status:** Complete  <br>
**BlockedBy:** 6  <br>
**Recommended model/effort:** Sonnet 4.6, medium effort — production-code migration only; legacy `TupletSpan` and `Line.tuplets` allowed to remain as orphans until 8b.

### Tasks

1. Rewrite `TupletAddition` / `TupletRemoval` to carry `Tuplet` and operate over `rangeElements`.
2. Migrate `MusicEditOperations.toggleTuplet` (same-grade remove and different-grade replace branches) to construct/remove `Tuplet`.
3. Update `LineIO` / `StaffElementIO` and `FormatMigrator` to load legacy `<tuplet>` spans into `Tuplet`; emit only `Tuplet`.
4. Update `ClipboardManager`, `Line.copySpans` / `pasteSpans`, and `TupletPopupButton` / `TupletAction` to use `Tuplet`.
5. Add new `Line.addTuplet(Tuplet)` / `removeTuplet(Tuplet)` overloads next to the existing `TupletSpan` ones; do NOT delete the legacy overloads yet (8b).
6. Update tests only as needed to keep the project compiling. Defer the substantive test sweep to 8c.
7. `./scripts/compile.sh` must succeed at the end.

---

## ✅ Phase 8b: Tuplet renderer + delete TupletSpan

**Status:** Complete  <br>
**BlockedBy:** 8a  <br>
**Recommended model/effort:** Sonnet 4.6, low effort — renderer swap plus dead-code deletion now that all callers use `Tuplet`.

### Tasks

1. Update `TupletRenderer` to read `Tuplet` from `rangeElements`; remove any `TupletSpan` fallback.
2. Remove `StructuralStacker.bridgeLegacyTupletSpans` (and any caller) so the stacker reads `Tuplet` from `rangeElements` only.
3. Update `VerticalStackingCalculator` tuplet vertical-position path to use `Tuplet` from `rangeElements`.
4. Delete the legacy overloads: `Line.tuplets` field, `getTuplets()`, `addTuplet(TupletSpan)`, `removeTuplet(TupletSpan)`. Remove `tuplets` from `Line.spanSets`.
5. Delete `TupletSpan.java`.
6. `./scripts/compile.sh` must succeed. Do NOT run the full unit suite here — 8c does that.

---

## ✅ Phase 8c: Tuplet test sweep

**Status:** Complete  <br>
**BlockedBy:** 8b  <br>
**Recommended model/effort:** Sonnet 4.6, low effort — review and update tests that exercise tuplet semantics, then run unit suite.

### Tasks

1. Review and update `TupletActionTest`, `TupletTest`, `StructuralTierStackingTest` (any "legacy tuplet span" test), plus any other test that touches tuplet add/remove or layout.
2. Mechanical e2e signature fixes only if compilation requires it (no logic changes); stop and ask if more is needed.
3. Run `./scripts/test.sh unit` — must pass.
4. Snapshot at end: `git stash push --include-untracked -m "Finished phase 8" && git stash apply`.

---

## ✅ Phase 9a: Create Beam + mutations + actions

**Status:** Complete  <br>
**BlockedBy:** 6  <br>
**Recommended model/effort:** Opus 4.7, medium effort — introduces a new `RangeElement` subclass and rewrites the beaming mutation path; toggle semantics require care.

### Tasks

1. Create `songscribe.ui.layout.Beam extends RangeElement`. Keep it thin — it carries only the anchor/end pair; `BeamLayout` continues to hold computed beam geometry.
2. Rewrite `BeamingAddition` / `BeamingRemoval` mutations to carry `Beam`.
3. Add `Line.addBeaming(Beam)` / `removeBeaming(Beam)` overloads operating over `rangeElements`; mirror the overlap-merge / index-shift behavior used in `addTie` / `addHairpin`. Do NOT delete the legacy `BeamSpan` overloads yet (9b).
4. Migrate `MusicEditOperations.toggleBeaming` to construct/remove `Beam`.
5. Update tests only as needed to keep the project compiling. Defer the substantive test sweep to 9c.
6. `./scripts/compile.sh` must succeed at the end.

---

## ✅ Phase 9b: Beam IO + clipboard + renderer + delete BeamSpan

**Status:** Complete  <br>
**BlockedBy:** 9a  <br>
**Recommended model/effort:** Opus 4.7, medium effort — IO/migrator + the beam-algorithm wiring through `BeamGroupRenderer`; touches hot paths.

### Tasks

1. Update `LineIO` / `StaffElementIO` and `FormatMigrator` to load legacy `<beam>` spans into `Beam`; emit only `Beam`.
2. Update `ClipboardManager`, `Line.copySpans` / `pasteSpans`, and the beam algorithm entry (the code feeding `calculate_beam()` in milestone 2) to iterate `findRangeElements(Beam.class)`.
3. Update `BeamGroupRenderer` to read `Beam` from `rangeElements`; remove any `BeamSpan` fallback.
4. Delete the legacy overloads: `Line.beamings` field, `getBeamings()`, `addBeaming(BeamSpan)`, `removeBeaming(BeamSpan)`. Remove `beamings` from `Line.spanSets`.
5. Delete `BeamSpan.java`.
6. `./scripts/compile.sh` must succeed. Do NOT run the full unit suite — 9c does that.

---

## ✅ Phase 9c: Beam test sweep

**Status:** Complete  <br>
**BlockedBy:** 9b  <br>
**Recommended model/effort:** Sonnet 4.6, low effort — update tests that exercise beam semantics, then run unit suite.

### Tasks

1. Review and update `BeamToggleTest`, beam-layout tests, and any other test that touches beam add/remove or layout (including any "legacy beam span" test in stacking tests).
2. Mechanical e2e signature fixes only if compilation requires it (no logic changes); stop and ask if more is needed.
3. Run `./scripts/test.sh unit` — must pass.
4. Snapshot at end: `git stash push --include-untracked -m "Finished phase 9" && git stash apply`.

---

## ✅ Phase 10: Delete SpanSet + Span types, remove bridging

**Status:** Complete  <br>
**BlockedBy:** 1, 2, 3, 4, 5, 6, 7, 8c, 9c  <br>
**Recommended model/effort:** Sonnet 4.6, low effort — purely deletion and cleanup once nothing references the legacy types.

### Tasks

1. Delete `songscribe.model.SpanSet` and `songscribe.model.Span`; delete `SpanSetTest`.
2. Remove `Line.spanSets`, `Line.copySpans`, `Line.pasteSpans`, `Line.shiftSpans` and any remaining `SpanSet`-typed plumbing; rewrite `ClipboardManager` and copy/paste tests over `rangeElements` directly.
3. Sweep `VerticalStackingCalculator`, `NoteAttachedStacker`, `StructuralStacker`, `SystemStacker`, `VerticalAdjustment`, and `HorizontalAdjustment` for any remaining flag/Span bridging comments and dead branches; remove.
4. Sweep renderers (`FermataRenderer`, `TrillRenderer`, `TempoChangeRenderer`, `BeatChangeRenderer`, `AnnotationRenderer`, `MetronomeRenderer`, `BeamGroupRenderer`, `TupletRenderer`) for legacy lookups; assert each reads only from `LayoutResult` or the new types.
5. Verify `FormatMigrator` only contains read-side mappings for legacy fields/spans; no write-side legacy emission remains in `StaffElementIO` / `LineIO` / `SongIO`.
6. Run full `./scripts/compile.sh && ./scripts/test.sh` (unit). Open a representative v2.0 song and a representative v2.1 song; verify all decorations render and round-trip.
7. Update `rendering-rewrite.md` Status Dashboard and Phase 6 header to ✅; capture any deferred items into Phase 7 (Cleanup + Polish).

## Verification

- All user actions, file I/O, export, MIDI, and copy/paste work through `Attachment` / `RangeElement` only.
- No `isFermata` / `setFermata`, `isTrill` / `setTrill`, `getTempoChange` / `setTempoChange`, `getBeatChange` / `setBeatChange`, `getAnnotation` / `setAnnotation` remain on `StaffElement`.
- `SpanSet`, `Span`, `BeamSpan`, `TieSpan`, `TupletSpan`, `DynamicsSpan` are deleted; only `Line.rangeElements` carries range data.
- Bridging code in `VerticalStackingCalculator` and the stackers is gone.
- v2.0 and v2.1 files load correctly via `FormatMigrator`; new saves contain only the new representation.
