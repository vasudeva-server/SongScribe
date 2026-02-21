# Fix Milestone 1-2 Migration Issues

## Status Dashboard

| Phase | Description | Status | Sub-plan |
|-------|-------------|--------|----------|
| 1 | [Fix IO Critical Bugs](#-phase-1-fix-io-critical-bugs) | ✅ Done | — |
| 2 | [Migrate NoteColumn/NoteColumnBuilder from BeamGroup to Beamings](#-phase-2-migrate-notecolumnnotecolumnbuilder-from-beamgroup-to-beamings) | ✅ Done | — |
| 3 | [Migrate HorizontalSpacingCalculator from BeamGroup to Beamings](#-phase-3-migrate-horizontalspacingcalculator-from-beamgroup-to-beamings) | ✅ Done | — |
| 4 | [Fix calculateUnbeamedStems Guard](#-phase-4-fix-calculateunbeamedstems-guard) | ✅ Done | — |
| 5 | [Fix calculateBeams Stem Direction for Manual Overrides](#-phase-5-fix-calculatebeams-stem-direction-for-manual-overrides) | ✅ Done | — |
| 6 | [Fix TupletRenderer (M2 Phase 7 completion)](#-phase-6-fix-tupletrenderer-m2-phase-7-completion) | ✅ Done | — |
| 7 | [Dead Code Cleanup](#-phase-7-dead-code-cleanup) | ✅ Done | — |

## Context

The Milestone 1 (coordinate system) and Milestone 2 (beams + stems) migrations left several bugs:
- v2.2 files cannot be loaded (crash on open)
- v2.x files accumulate duplicate RangeElements on every load
- Beam group spacing in HorizontalSpacingCalculator is dead code (uses always-empty `Line.beamGroups` instead of `line.getBeamings()`)
- `calculateUnbeamedStems` runs on beamed notes because `NoteColumn.isBeamed()` always returns false
- `calculateBeams` overwrites manual stem direction overrides unconditionally
- Orphaned fields and dead code remain

## Phases

### ✅ Phase 1: Fix IO Critical Bugs

**1a. Add v2.2 case to CompositionIO version dispatch**

`src/main/java/songscribe/io/CompositionIO.java`

- `startElement()` (~line 271): Add `else if ((majorVersion == 2) && (minorVersion == 2))` before the `else` throw, identical body to the v2.1 case (creates `LineReader` and `ViewReader`)
- `endElement()` (~line 430): Add `else if ((majorVersion == 2) && (minorVersion == 2))` delegating to `endElement21(qName)` (v2.2 parsing is identical to v2.1)

**1b. Set `composition.formatVersion` from file version before migration**

`src/main/java/songscribe/io/CompositionIO.java` — in `getComposition()` (~line 885):

- Before `FormatMigrator.migrate(composition)`, add:
  ```java
  if (majorVersion >= 2) {
      composition.setFormatVersion(2);
  }
  ```
- This makes `FormatMigrator.migrate()` correctly skip the v1→v2 IntervalSet migration for v2.x files (the guard at FormatMigrator line 87 checks `composition.getFormatVersion() >= 2`)

### ✅ Phase 2: Migrate NoteColumn/NoteColumnBuilder from BeamGroup to Beamings

**Goal:** Replace the dead `BeamGroup` reference in `NoteColumn` with an `isBeamed` boolean derived from `line.getBeamings()`.

**2a. NoteColumn.java** (`src/main/java/songscribe/ui/layout2/NoteColumn.java`)

- Replace `private final @Nullable BeamGroup beamGroup` field with `private final boolean beamed`
- Update constructor: replace `@Nullable BeamGroup beamGroup` parameter with `boolean beamed`
- `isBeamed()` → return `beamed` (instead of `beamGroup != null`)
- Delete `getBeamGroup()` method

**2b. NoteColumnBuilder.java** (`src/main/java/songscribe/ui/layout2/NoteColumnBuilder.java`)

- Delete `findBeamGroup()` method
- In `buildColumn()`: replace `BeamGroup beamGroup = findBeamGroup(note, line)` with:
  ```java
  int noteIndex = line.getNoteIndex(note);
  boolean beamed = line.getBeamings().findInterval(noteIndex) != null;
  ```
- Pass `beamed` instead of `beamGroup` to `NoteColumn` constructor
- Remove `BeamGroup` import

### ✅ Phase 3: Migrate HorizontalSpacingCalculator from BeamGroup to Beamings

**Goal:** The beam-group spacing algorithm (tight packing with lyric-aware expansion) is valuable and must be preserved. Rewrite it to use `line.getBeamings()` intervals mapped to column indices.

`src/main/java/songscribe/ui/layout2/HorizontalSpacingCalculator.java`

**3a. Replace `BeamGroupRange` inner class**

- Remove the `BeamGroup group` field from `BeamGroupRange` — it only needs `start` and `end` column indices

**3b. Rewrite `identifyBeamGroupRanges()`**

- Change signature to accept both `List<NoteColumn> columns` and `Line line`
- Iterate `line.getBeamings()` intervals
- For each `BeamInterval`, map its note-index range (`getStart()`..`getEnd()`) to column indices (columns are 1:1 with notes, same ordering, so column index == note index)
- Build `BeamGroupRange(startColIdx, endColIdx)` for each interval
- No more `BeamGroup` object identity checks

**3c. Update `handleBeamGroup()`**

- Remove the `BeamGroup group` parameter from `BeamGroupRange` (already done in 3a)
- The three-phase algorithm (tight → lyric → distribute) is unchanged — it operates on column indices, not BeamGroup objects

**3d. Update `calculatePositions()` call site**

- Pass `line` to `identifyBeamGroupRanges(columns, line)`

### ✅ Phase 4: Fix calculateUnbeamedStems Guard

`src/main/java/songscribe/ui/layout2/LayoutEngine.java` — `calculateUnbeamedStems()`

- The `col.isBeamed()` guard now works correctly because Phase 2 made `NoteColumn.isBeamed()` return the correct value from `line.getBeamings()`
- No additional code changes needed — this fix falls out of Phase 2 automatically

### ✅ Phase 5: Fix calculateBeams Stem Direction for Manual Overrides

`src/main/java/songscribe/ui/layout2/LayoutEngine.java` — `calculateBeams()`

**5a. Determine group stem direction respecting all manual overrides**

Replace the current logic (which only checks the first note) with:

```java
// Scan for any manual override in the group
Boolean manualDirection = null;
for (int i = interval.getStart(); i <= interval.getEnd(); i++) {
    var n = line.getNote(i);
    if (!n.isStemDirectionAuto()) {
        manualDirection = n.isUpper();
        break;  // first manual override wins
    }
}

boolean stemsUp = (manualDirection != null)
    ? manualDirection
    : (minStaffPos + maxStaffPos) < 0;
```

**5b. Only overwrite auto-direction notes**

Replace the unconditional `n.setUpper(stemsUp)` loop with:

```java
for (int i = interval.getStart(); i <= interval.getEnd(); i++) {
    var n = line.getNote(i);
    if (n.isStemDirectionAuto()) {
        n.setUpper(stemsUp);
    }
}
```

This preserves manual overrides while still normalizing auto notes to the group direction.

**5c. Remove debug `System.out.println()` statements**

- Remove from `calculateBeams()` and `calculateUnbeamedStems()`

### ✅ Phase 6: Fix TupletRenderer (M2 Phase 7 completion)

`src/main/java/songscribe/ui/renderer/TupletRenderer.java`

**6a. Replace `properties.stem.y2` (line 239)**

- Use `layoutResult.getStemLayout(note)` to get `topYSs`/`bottomYSs`
- For stems up: bracket needs the stem tip Y, which is `stemLayout.topYSs()`
- For stems down: bracket needs the stem tip Y, which is `stemLayout.bottomYSs()`
- Null-guard: fall back to `noteY +/- 3.5` (MIN_STEM_SS)

**6b. Replace `properties.stem.x1` (lines 264-265, 269, 273-274)**

- Stem X is not stored in `StemLayout` — it is a rendering concern
- Compute from `layoutResult.getNoteXSs(note)` + notehead anchor offset (the stem sits at the notehead's stem anchor point)
- Extract a helper: `private double stemXSs(Note note, LayoutResult layoutResult)`
- Use SMuFL `GlyphAnchors.getStemUpSE()` / `getStemDownNW()` for the offset, same as `NoteRenderer.renderStem()`

### ✅ Phase 7: Dead Code Cleanup

**7a.** Delete `Note.Properties.lengthening` and `Note.Properties.beamThickening` fields (`src/main/java/songscribe/music/Note.java` ~lines 730-731). Update the stale "written by BeamCalculator" comment.

**7b.** Remove `RendererRegistry` mapping for `BeamGroup.class` (`src/main/java/songscribe/ui/renderer/RendererRegistry.java` ~line 82). Remove `BeamGroup` import.

**7c.** Delete `Line.beamGroups` field and its accessor methods (`getBeamGroups()`, `addBeamGroup()`, `findBeamGroupFor()`) from `src/main/java/songscribe/music/Line.java`. Remove `BeamGroup` import.

**7d.** Delete `BeamGroup.java` (`src/main/java/songscribe/ui/layout/BeamGroup.java`).

**7e.** Verify no remaining references to `BeamGroup` in production code (test files may still reference it — update those separately if they break compilation).

## Files Modified

| File | Phases |
|------|--------|
| `src/main/java/songscribe/io/CompositionIO.java` | 1a, 1b |
| `src/main/java/songscribe/ui/layout2/NoteColumn.java` | 2a |
| `src/main/java/songscribe/ui/layout2/NoteColumnBuilder.java` | 2b |
| `src/main/java/songscribe/ui/layout2/HorizontalSpacingCalculator.java` | 3 |
| `src/main/java/songscribe/ui/layout2/LayoutEngine.java` | 4, 5 |
| `src/main/java/songscribe/ui/renderer/TupletRenderer.java` | 6 |
| `src/main/java/songscribe/music/Note.java` | 7a |
| `src/main/java/songscribe/ui/renderer/RendererRegistry.java` | 7b |
| `src/main/java/songscribe/music/Line.java` | 7c |

## Files Deleted

| File | Phase |
|------|-------|
| `src/main/java/songscribe/ui/layout/BeamGroup.java` | 7d |

## Verification

- `./scripts/compile.sh` — clean compilation
- `./scripts/run.sh` — open a composition:
  - Beamed notes render with correct slopes and stem directions
  - Manually flipped stems persist across layout cycles
  - Tuplet brackets position correctly over/under beamed and unbeamed notes
  - Save, close, reopen — no data corruption, no duplicate ties/tuplets/endings
  - Verify beam group spacing: beamed 8th notes should be tightly packed (not spaced like individual notes)
