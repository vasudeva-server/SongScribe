# Migrate First-Second Endings from EndingInterval to Ending

**Created:** 2026-03-31

## Context

The first-second ending feature currently has two parallel data models:

1. **Legacy: `EndingInterval`** -- stored in `Line.firstSecondEndings` (`IntervalSet<EndingInterval>`), index-based (`int start`, `int end`, `int endingNumber`). One interval covers the entire first+second ending region.
2. **New: `Ending`** -- stored in `Line.rangeElements` (`List<RangeElement>`), reference-based (`StaffElement anchorNote`, `StaffElement endNote`, `Type type`, `int yPositionSs`).

All runtime code (rendering, MIDI, export, editing, validation) uses `EndingInterval`. The `Ending` range element only exists for v1 files after `FormatMigrator` runs, and the layout calculator bridges between the two via `bridgeLegacyEndingIntervals()`. This dual representation causes confusion and maintenance burden. The goal is to make `Ending` the sole runtime model and remove `EndingInterval` entirely.

### Key Design Decisions

1. **One Ending per pair, type = FIRST.** The renderer already splits the visual bracket into "1." and "2." sections by finding the right repeat within the range. No need for two Ending objects.
2. **Keep `fsendings` XML format.** Derive write data from Ending objects; keep read path for backward compat. No new XML tag needed since `yPositionSs` is not yet persisted (it is derived from the deprecated line-level offset during v1 migration only).
3. **Index computation via `getAnchorElementIndex()`/`getEndElementIndex()` is fine.** These call `List.indexOf()` on small element lists (10-40 elements).
4. **No `shiftValues` needed.** `EndingInterval` requires `shiftValues` when elements are inserted/removed to keep indices in sync. `Ending` stores `StaffElement` references that remain valid across insertions/deletions -- `getAnchorElementIndex()` computes the index live.

## Status Dashboard

| Phase | Description | Status  |
|-------|-------------|---------|
| 1 | [Foundation: Line Helpers and Ending Availability](#phase-1-foundation) | Done    |
| 2 | [Consumer Migration](#phase-2-consumer-migration) | Done    |
| 3 | [Persistence and Creation Cutover](#phase-3-persistence-and-creation-cutover) | Done    |
| 4 | [Cleanup](#phase-4-cleanup) | Done    |

---

## Phase 1: Foundation

**Status:** Done
**BlockedBy:** --

### Context

Before consumers can switch to reading `Ending` objects, two prerequisites must be met: (a) `Line` needs convenience query methods that mirror the `IntervalSet` API, and (b) `Ending` objects must exist at runtime for all code paths -- both file loading and user creation.

Currently, v2 files (IO version >= 2.0) do NOT get `Ending` objects because `FormatMigrator.migrate()` skips them. And `makeFirstSecondEnding()` only creates `EndingInterval`. This phase fixes both gaps.

### Tasks

1. Add convenience methods to `Line`:
   - `findEndings()` -- returns `List<Ending>` (delegates to `findRangeElements(Ending.class)`)
   - `findEndingAt(int elementIndex)` -- returns `@Nullable Ending` by checking anchor/end indices
   - `isInsideAnyEnding(int elementIndex)` -- returns `boolean`
   - `isStartOfAnyEnding(int elementIndex)` / `isEndOfAnyEnding(int elementIndex)` -- for ABC export

2. Ensure `Ending` objects exist on file load for ALL format versions:
   - In `LineIO.LineReader`, after all elements are parsed (at end-of-line processing), if `line.getFirstSecondEndings()` is populated but `line.findEndings()` is empty, create `Ending` objects from the IntervalSet entries and add to `line.rangeElements`
   - This handles v2 files that have `fsendings` but skip `FormatMigrator`
   - Add guard in `FormatMigrator.migrateRangeElements()`: skip ending migration if `line.findEndings()` is already populated (prevents double-creation for v1 files)

3. Dual-write in `MusicEditOperations.makeFirstSecondEnding()`:
   - After creating `EndingInterval` and adding to IntervalSet, also create `Ending(startElement, endElement, Ending.Type.FIRST)` and add via `line.addRangeElement()`
   - Also dual-write the removal path: in `removeInvalidEnding()`, after removing the `EndingInterval`, also remove the matching `Ending` from `rangeElements`

4. Compile and run unit tests

### Files to Modify

| File | Changes |
|------|---------|
| `songscribe/music/Line.java` | Add 5 convenience methods |
| `songscribe/io/LineIO.java` | Create Ending objects from IntervalSet at end-of-line parsing |
| `songscribe/io/FormatMigrator.java` | Guard against double Ending creation |
| `songscribe/music/MusicEditOperations.java` | Dual-write Ending + EndingInterval in create and remove paths |

---

## Phase 2: Consumer Migration

**Status:** Done
**BlockedBy:** Phase 1

### Context

With both data stores populated (Phase 1), all consumers can safely switch from reading `IntervalSet<EndingInterval>` to reading `Ending` range elements. After this phase, `getFirstSecondEndings()` is no longer read by any runtime code.

### Tasks

1. Migrate `EndingRenderer`:
   - `renderEndings()`: iterate `line.findEndings()` instead of `line.getFirstSecondEndings().listIterator()`. Replace `interval.getStart()`/`interval.getEnd()` with `ending.getAnchorElementIndex()`/`ending.getEndElementIndex()`.
   - `drawEnding()`: change parameter from `EndingInterval interval` to `Ending ending`
   - `getEffectiveEndingYPosPx()`: change parameter from `EndingInterval interval` to `Ending ending`. Remove the SpanLayout lookup path; only use DecorationLayout keyed by `Ending.class`.

2. Migrate `VerticalStackingCalculator`:
   - Remove `bridgeLegacyEndingIntervals()` method entirely
   - In `stackEndings()`, remove the call to `bridgeLegacyEndingIntervals()`

3. Migrate `MidiSequenceBuilder`:
   - Replace `line.getFirstSecondEndings().findInterval(noteIndex)` with `line.findEndingAt(noteIndex)`
   - Replace `firstSecondInterval.getEnd()` with `ending.getEndElementIndex()`

4. Migrate `ExportABCAction`:
   - Replace `line.getFirstSecondEndings().isStartOfAnyInterval(i)` with `line.isStartOfAnyEnding(i)`
   - Replace `line.getFirstSecondEndings().isInsideAnyInterval(i)` with `line.isInsideAnyEnding(i)`
   - Replace `line.getFirstSecondEndings().isEndOfAnyInterval(i)` with `line.isEndOfAnyEnding(i)`

5. Migrate `VerticalAdjustment`:
   - Replace `line.getFirstSecondEndings().isEmpty()` with `line.findEndings().isEmpty()`
   - Replace `line.getFirstSecondEndings().findInterval()` with `line.findEndingAt()`
   - Adapt `getStart()`/`getEnd()` to `getAnchorElementIndex()`/`getEndElementIndex()`

6. Migrate `MusicEditOperations` read paths:
   - `hasOverlap()`: replace `line.getFirstSecondEndings().isInsideAnyInterval(i)` with `line.isInsideAnyEnding(i)`
   - `findInvalidEndings()`: change return type to `ArrayList<Ending>`, iterate `line.findEndings()`, use `getAnchorElementIndex()`/`getEndElementIndex()` for bounds
   - `removeInvalidEnding()`: change parameter from `EndingInterval` to `Ending`. Remove `Ending` from `rangeElements` instead of (in addition to) removing from IntervalSet. Element cleanup logic stays the same but uses Ending indices.

7. Migrate `ScoreMessageCoordinator`:
   - `autoRemoveInvalidEndings()` and `collectInvalidEndings()`: change `EndingInterval` types to `Ending`
   - Remove `EndingInterval` import

8. Update tests:
   - `StructuralTierStackingTest`: update legacy ending interval test to use Ending range elements
   - Any other tests referencing `EndingInterval`

9. Compile and run unit tests

### Files to Modify

| File | Changes |
|------|---------|
| `songscribe/ui/renderer/EndingRenderer.java` | Rewrite to iterate Ending, remove SpanLayout path |
| `songscribe/ui/layout/VerticalStackingCalculator.java` | Remove `bridgeLegacyEndingIntervals()` |
| `songscribe/midi/MidiSequenceBuilder.java` | Replace IntervalSet query with Ending query |
| `songscribe/ui/action/ExportABCAction.java` | Replace 3 IntervalSet boundary checks |
| `songscribe/ui/adjustment/VerticalAdjustment.java` | Replace IntervalSet checks with Ending queries |
| `songscribe/music/MusicEditOperations.java` | Change read paths, return types, parameter types |
| `songscribe/ui/component/ScoreMessageCoordinator.java` | Change EndingInterval to Ending |
| Test files | Update ending-related tests |

---

## Phase 3: Persistence and Creation Cutover

**Status:** Done
**BlockedBy:** Phase 2

### Context

After Phase 2, no runtime code reads from `IntervalSet<EndingInterval>`. The IntervalSet is still written to (dual-write) and still read from disk by LineIO. This phase cuts over persistence to derive from Ending objects, and stops the dual-write.

### Tasks

1. Change `LineIO.writeLine()` fsendings block:
   - Instead of `intervalToString(l.getFirstSecondEndings())`, derive the interval string from `l.findEndings()` using `getAnchorElementIndex()`/`getEndElementIndex()`
   - Keep the same `"start,end;"` format for backward compatibility

2. Change `LineIO.LineReader` fsendings read path:
   - Parse the `fsendings` string into index pairs (no longer create `EndingInterval` objects)
   - Store parsed index pairs temporarily
   - At end-of-line (after elements are loaded), create `Ending` objects directly from the stored pairs
   - Do NOT populate `line.getFirstSecondEndings()` at all

3. Remove dual-write from `MusicEditOperations.makeFirstSecondEnding()`:
   - Remove `EndingInterval` creation and `line.getFirstSecondEndings().addInterval()` call
   - Keep only the `Ending` creation and `line.addRangeElement()` call

4. Remove dual-write from `MusicEditOperations.removeInvalidEnding()`:
   - Remove `line.getFirstSecondEndings().removeInterval()` call
   - Keep only `line.removeRangeElement(ending)`

5. Compile and run unit tests

### Files to Modify

| File | Changes |
|------|---------|
| `songscribe/io/LineIO.java` | Rewrite fsendings write to derive from Ending; rewrite read to create Ending directly |
| `songscribe/music/MusicEditOperations.java` | Remove dual-write in create and remove paths |

---

## Phase 4: Cleanup

**Status:** Done
**BlockedBy:** Phase 3

### Context

`EndingInterval` and `Line.firstSecondEndings` are now completely unused at runtime and in persistence. Remove them along with all related dead code.

### Tasks

1. Remove from `Line`:
   - `firstSecondEndings` field
   - Remove `firstSecondEndings` from the `intervalSets` array
   - `getFirstSecondEndings()` method
   - Note: keep `firstSecondEndingYPosPx` field and getter/setter (still needed for v1 file migration in `FormatMigrator.migrateLineLevelOffsets()`) -- ensure they remain `@Deprecated`

2. Remove `FormatMigrator` ending-specific migration:
   - Remove the ending conversion block from `migrateRangeElements()` (LineIO now creates Ending objects directly when reading `fsendings`)
   - Remove `extractEndingType()` helper
   - Keep `migrateLineLevelOffsets()` ending section (still needed for v1 files: reads `firstSecondEndingYPosPx`, applies delta to Ending objects from `line.findEndings()`)

3. Remove `LineIO.stringToEndingIntervalSet()` (now dead)

4. Delete `EndingInterval.java`

5. Remove `EndingInterval` imports from all files

6. Remove SpanLayout keyed by EndingInterval from `LayoutResult` if no longer used by any code

7. Verify no remaining references to removed code:
   - `EndingInterval`
   - `getFirstSecondEndings()`
   - `bridgeLegacyEndingIntervals`
   - `stringToEndingIntervalSet`

8. Compile and run full unit tests

### Files to Modify

| File | Changes |
|------|---------|
| `songscribe/music/Line.java` | Remove IntervalSet field, remove from intervalSets array, remove getter |
| `songscribe/music/EndingInterval.java` | **Delete** |
| `songscribe/io/LineIO.java` | Remove `stringToEndingIntervalSet()` |
| `songscribe/io/FormatMigrator.java` | Remove ending IntervalSet migration, keep offset migration |
| All files that imported EndingInterval | Remove imports |
