# Sub-plan: Beams + Stems

**Type:** Sub-plan  <br>
**Parent:** [rendering-rewrite.md](rendering-rewrite.md) → Phase 2  <br>
**Created:** 2026-02-01  <br>
**Status:** In Progress  <br>
**BlockedBy:** —

**Spec:** [docs/specs/rendering-rewrite.md](../../../docs/specs/rendering-rewrite.md) — always read the spec before implementing tasks.

---

## Status Dashboard

| Phase | Description | Status |
|-------|-------------|--------|
| 1 | [Data Model Changes](#-phase-1-data-model-changes) | ✅ Done |
| 2 | [IO + Format Migration](#-phase-2-io--format-migration) | ✅ Done |
| 3 | [BeamInterval + Line Wiring](#-phase-3-beaminterval--line-wiring) | ✅ Done |
| 4 | [Fold Beam Calculation into LayoutEngine](#-phase-4-fold-beam-calculation-into-layoutengine) | ✅ Done |
| 5 | [Remove Eager Call Sites + Delete BeamCalculator](#-phase-5-remove-eager-call-sites--delete-beamcalculator) | ✅ Done |
| 6 | [Update BeamGroupRenderer to Use LayoutResult](#-phase-6-update-beamgrouprenderer-to-use-layoutresult) | ✅ Done |
| 7 | [Update NoteRenderer Stems to Use LayoutResult](#-phase-7-update-noterenderer-stems-to-use-layoutresult) | ✅ Done |
| 8 | [Verification](#-phase-8-verification) | ⏳ Pending |

## Overview

Port the beam algorithm from abc2svg (`calculate_beam()`, `draw_beams()`, `set_beams()`). Replace the mutable `Note.Properties` beam state with immutable `LayoutResult.BeamLayout` and `LayoutResult.StemLayout` records. Fold beam calculation into `LayoutEngine.layout()`. Remove all eager `BeamCalculator.calculateLengthenings()` call sites. Introduce `BeamInterval` as a typed interval subclass. Add `stemDirectionAuto` to `Note` with v2.2 IO format.

## Key Design Decisions

1. **BeamCalculator deleted.** All beam layout logic moves to a private method inside `LayoutEngine`. No standalone class survives.
2. **Beam geometry in LayoutResult.** `BeamLayout` and `StemLayout` records store all computed geometry (slope, startY, stem topY/bottomY, lengthening). `Note.Properties.lengthening`, `beamThickening`, and `stem` (Line2D.Double) are removed.
3. **Eager call sites removed.** All `BeamCalculator.calculateLengthenings()` calls in `MusicEditOperations`, `EditModeManager`, `ScoreMessageCoordinator`, `InsertionNoteManager`, `Score` are deleted. Layout runs on the next render cycle via the normal invalidation path.
4. **`stemDirectionAuto` flag.** `true` = algorithm writes `note.upper` each layout pass. `false` = user override, stored value preserved. Serialized as `<stemDirectionAuto/>` when false. Absence on read = auto. Format version bumped to 2.2.
5. **`invertFractionBeamOrientation` removed.** Field deleted from `Note`. NoteIO silently discards the tag on read. No migration entry needed.
6. **Slope algorithm.** Hyperbolic dampening (`BEAM_SLOPE_MAX * a / (BEAM_SLOPE_MAX + |a|)`) replaces linear clamping. Flat beam snapping applied when slope near zero. All math in staff-space units.
7. **Partial beam stub direction.** Automatic from rhythmic context. Replaces `invertFractionBeamOrientation` toggle entirely.
8. **`BeamInterval` typed subclass.** `Line.beamings` changes from `IntervalSet<Interval>` to `IntervalSet<BeamInterval>`. No additional fields on the interval itself — all computed geometry lives in `LayoutResult.BeamLayout`.

## Constants

All new constants in staff-space units, defined in `LayoutEngine` (or a package-private `BeamConstants` nested class):

| Constant | Value | Notes |
|----------|-------|-------|
| `BEAM_DEPTH` | 0.4 ss | Beam thickness (see also SMuFL `beamThickness` engraving default) |
| `BEAM_SHIFT` | 0.625 ss | Gap between stacked beam levels (see also SMuFL `beamSpacing`) |
| `BEAM_STUB` | 1.0 ss | Partial beam stub length |
| `BEAM_SLOPE_MAX` | 0.4 | Dimensionless; hyperbolic saturation limit |
| `MIN_STEM_SS` | 3.5 ss | Minimum stem length (Gould/Ross 4.2) |

Beam thickening: `1/cos(angle)` clamped to 3.3–8.8% of beam thickness (raster compensation for angled beams, not in abc2svg).

## Phases

### ✅ Phase 1: Data Model Changes

- [x] **`Note.java`: Add `stemDirectionAuto` field**
  - Add `private boolean stemDirectionAuto = true;`
  - Add `isStemDirectionAuto()` / `setStemDirectionAuto(boolean)` accessors
  - Default `true` (all new notes are auto)

- [x] **`Note.java`: Remove `invertFractionBeamOrientation`**
  - Delete the field, getter (`isInvertFractionBeamOrientation()`), and setter
  - Fix compilation errors (callers: `NoteIO`, `MusicEditOperations`, `BeamGroupRenderer`)

- [x] **`Note.Properties`: Remove beam-related mutable state**
  - Delete `lengthening` (int)
  - Delete `beamThickening` (double)
  - Delete `stem` (Line2D.Double)
  - Fix all compilation errors from these removals (callers addressed in Phases 5–7)

- [x] **`LayoutResult.java`: Add `StemLayout` and `BeamLayout` records**

  ```java
  public record StemLayout(
      double topY,
      double bottomY,
      double lengthening,
      boolean stubRight  // only meaningful for partial-beam notes; false for full-beam or unbeamed
  ) {}

  public record BeamLayout(
      double slope,
      double startY,
      boolean stemsUp,
      double thickening,  // extra thickness from 1/cos(angle) correction, in ss
      Map<Note, StemLayout> stems
  ) {}
  ```

  - Add `Map<Interval, BeamLayout> beamLayouts` field to `LayoutResult`
  - Add `getBeamLayout(Interval)` accessor
  - Add `Map<Note, StemLayout> stemLayouts` field for unbeamed notes
  - Add `getStemLayout(Note)` accessor — checks `beamLayouts` stems first, falls back to `stemLayouts`
  - Update `Builder` with `putBeamLayout(Interval, BeamLayout)` and `putStemLayout(Note, StemLayout)`

### ✅ Phase 2: IO + Format Migration

- [x] **Bump format version to 2.2**
  - `CompositionIO`: `IO_MINOR_VERSION = 2`

- [x] **`NoteIO.java`: Add `stemDirectionAuto` serialization**
  - Add constant `private static final String XML_STEM_DIRECTION_AUTO = "stemDirectionAuto";`
  - In `writeNote()`: write `<stemDirectionAuto/>` only when `!note.isStemDirectionAuto()` (manual override). Absence on read = auto.
  - In `NoteReader.endElement11()`: when tag equals `XML_STEM_DIRECTION_AUTO`, call `note.setStemDirectionAuto(false)`

- [x] **`NoteIO.java`: Remove `invertFractionBeamOrientation` write; silently ignore on read**
  - Delete the write block in `writeNote()`
  - In `NoteReader.endElement11()`: when tag equals `XML_INVERT_FRACTION_BEAM_ORIENTATION`, do nothing (keep the constant so the string is recognizable, just take no action)

- [x] **`FormatMigrator.java`: Add v2.1 → v2.2 entry**
  - This is a no-op migration: `stemDirectionAuto` defaults to `true`, so absence of the tag in existing files is handled correctly by the default
  - Document explicitly as intentional no-op; the version bump ensures re-saved files are stamped 2.2

### ✅ Phase 3: BeamInterval + Line Wiring

- [x] **Create `BeamInterval.java` in `songscribe/data/`**
  - Subclass of `Interval`, no additional fields
  - Constructor: `BeamInterval(int start, int end)` — passes `null` data to super
  - Override `copyRange(int, int)` → returns `new BeamInterval(newStart, newEnd)`
  - Follow the exact pattern of `TupletInterval`

- [x] **`Line.java`: Change `beamings` type**
  - `IntervalSet<Interval> beamings` → `IntervalSet<BeamInterval>`
  - Update `getBeamings()` return type accordingly

- [x] **Fix all `getBeamings()` construction sites**
  - `LineIO`: added `stringToBeamIntervalSet(IntervalSet<BeamInterval>, String)` that constructs `BeamInterval` objects; updated `XML_BEAMINGS` case to use it
  - `InsertionNoteManager.applyAutomaticBeaming`: `addInterval(noteIndex-1, noteIndex)` → `addInterval(new BeamInterval(...))`
  - `MusicEditOperations.toggleBeaming`: `beamings.addInterval(begin, end)` → `beamings.addInterval(new BeamInterval(begin, end))`
  - `LineRenderer.renderBeams`: removed redundant `(Interval)` cast (now returns `BeamInterval` directly)

### ✅ Phase 4: Fold Beam Calculation into LayoutEngine

This phase implements the full abc2svg-ported beam algorithm as a private pipeline step in `LayoutEngine`.

- [x] **Add beam calculation step to `LayoutEngine.layout()` pipeline**
  - After horizontal spacing is computed (note X positions are finalized), call `calculateBeams(line, noteColumns, builder)`
  - Then call `calculateUnbeamedStems(line, noteColumns, builder)` for all notes not in a beam group

- [x] **Implement stem direction for beamed groups**
  - For each beam group: sum `staffPosition` values for highest + lowest note; if average < 0 (below staff midpoint) → stems up, else → stems down
  - For each note: if `note.isStemDirectionAuto()`, set `note.setUpper(stemsUp)`; otherwise preserve existing `upper`

- [x] **Implement slope calculation (abc2svg hyperbolic dampening)**
  - Raw slope: `a = (lastNoteStaffPos - firstNoteStaffPos) * 0.5 / (lastX - firstX)` (×0.5 converts staff positions to ss)
  - Hyperbolic dampening: `a = BEAM_SLOPE_MAX * a / (BEAM_SLOPE_MAX + Math.abs(a))`
  - Force-horizontal checks **skipped** — abc2svg does not apply them, and they are not wanted

- [x] **Implement y-intercept and minimum stem length enforcement**
  - Compute y-intercept `b` so beam passes through anchor note's stem tip
  - For each note at x: `beamY = a * (x - firstX) + b`; stem length = `|beamY - noteAnchorY|`
  - If any stem < `MIN_STEM_SS`: iteratively reduce slope (×0.85, max 20 iterations), then shift entire beam vertically by any remaining deficit
  - Final `startY` = beam Y at the first note's X

- [x] **Implement flat beam snapping**
  - If `Math.abs(a) < epsilon` (slope near zero):
    - `startY = Math.round((startY + 1.5) / 0.75) * 0.75 - 1.5`
  - Ensures beam sits clearly on a staff line or in a space

- [x] **Implement beam thickening**
  - `angle = Math.atan(a)`
  - `factor = Math.clamp(1.0 / Math.cos(angle), 1.033, 1.088)`
  - `thickening = BEAM_DEPTH * (factor - 1.0)` — extra thickness in ss
  - Store in `BeamLayout.thickening`

- [x] **Build `StemLayout` per note in beam group**
  - `beamYAtNote = a * (noteX - firstX) + startY`
  - `lengthening = Math.abs(beamYAtNote - noteAnchorY) - MIN_STEM_SS` (clamp to 0.0 in renderer if negative)
  - Stem endpoints: `topY` and `bottomY` in ss (notehead anchor at one end, beam at other)

- [x] **Implement automatic partial beam stub direction**
  - For each note that needs a stub (sub-beam level has only this note):
    1. First note in group → `stubRight = true`
    2. Last note in group → `stubRight = false`
    3. Note at a beam break → `stubRight = true`
    4. Note before a beam break → `stubRight = false`
    5. Otherwise → `stubRight` points toward the adjacent note with more flags (shorter duration)
  - Store `stubRight` in `StemLayout`

- [x] **Implement `StemLayout` for unbeamed notes**
  - For each note not in any beam group:
    - If `note.isStemDirectionAuto()`: set `note.setUpper(note.getStaffPosition() <= 0)`
    - Compute standard stem endpoints (notehead anchor ± `MIN_STEM_SS`)
    - `lengthening = 0.0`, `stubRight = false`
  - Add to builder via `putStemLayout(note, stemLayout)`

### ✅ Phase 5: Remove Eager Call Sites + Delete BeamCalculator

- [x] **Remove `BeamCalculator.calculateLengthenings()` from `MusicEditOperations`**
  - Delete all calls (approximately lines 82, 85, 86, 414, 448)

- [x] **Remove from `EditModeManager`**
  - Delete call (approximately line 391)

- [x] **Remove from `ScoreMessageCoordinator`**
  - Delete calls (approximately lines 370–371)

- [x] **Remove from `InsertionNoteManager`**
  - Delete calls in `applyAutomaticBeaming()` (approximately lines 580, 620)

- [x] **Remove from `Score.setComposition()`**
  - Delete the beaming loop that calls `calculateLengthenings` (approximately line 735)

- [x] **Delete `BeamCalculator.java`**
  - Verify with Grep that no references remain, then delete the file

- [x] **Remove flip-partial-beam feature entirely**
  - Delete `FlipPartialBeamAction.java`
  - Remove the `flipPartialBeamOrientation()` dispatch in `ScoreMessageCoordinator` (~line 228)
  - Delete `Score.canFlipPartialBeamOrientation()` and its delegate call
  - Delete `LineSelectionState.canFlipPartialBeamOrientation()`
  - Delete `MusicEditOperations.canFlipPartialBeamOrientation()` and `flipPartialBeamOrientation()` (now a no-op)
  - Remove any menu/toolbar registration that references `FlipPartialBeamAction`
  - Verify with Grep that no references to `flipPartialBeamOrientation` or `canFlipPartialBeamOrientation` remain

### ✅ Phase 6: Update BeamGroupRenderer to Use LayoutResult

- [x] **Replace `Note.Properties.stem` reads with `LayoutResult.StemLayout`**
  - `drawBeam()` reads `note.properties.stem` for stem X, anchor Y, and tip Y
  - Replace with: `ctx.getLayoutResult().getStemLayout(note)` → `.topY()` / `.bottomY()`
  - Stem center X from `ctx.getLayoutResult().getNoteX(note)` + notehead anchor offset

- [x] **Replace `Note.Properties.beamThickening` with `LayoutResult.BeamLayout.thickening()`**
  - `drawBeam()` reads `beginNote.properties.beamThickening`
  - Replace with `ctx.getLayoutResult().getBeamLayout(interval).thickening()`

- [x] **Convert pixel constants to staff-space units**
  - `BEAM_THICKNESS_PX` → use `BEAM_DEPTH` ss directly (scale transform handles pixels)
  - `INNER_BEAM_LENGTH` → `BEAM_STUB = 1.0` ss
  - `INNER_BEAM_OFFSET` → `BEAM_SHIFT = 0.625` ss
  - Remove all `StaffSpaces.toPixels()` calls from constant initializers

- [x] **Update partial beam stub logic in `doDrawBeams()`**
  - Remove all `note.isInvertFractionBeamOrientation()` references (field is gone)
  - Read `StemLayout.stubRight()` instead

### ✅ Phase 7: Update NoteRenderer Stems to Use LayoutResult

- [x] **Replace `Note.Properties.lengthening` / `beamThickening` reads in `renderStem()`**
  - Read `ctx.getLayoutResult().getStemLayout(note)` for `lengthening()` and beam thickening
  - All values in ss — no pixel conversion needed

- [x] **Remove `Note.Properties.stem.setLine(...)` writes from `renderStem()`**
  - The renderer no longer writes back to `Note.Properties`
  - Geometry is pre-computed in `LayoutResult`

- [x] **Replace `Note.Properties.stem` reads in flags rendering**
  - Flags need the stem tip Y position; read from `StemLayout.topY()` / `bottomY()`

- [x] **Fix `TupletRenderer`**
  - Reads `note.properties.stem.y2` for bracket anchor Y
  - Replace with stem tip Y from `LayoutResult.getStemLayout(note)`

### ⏳ Phase 8: Verification

- [ ] Compile with `./scripts/compile.sh`
- [ ] Run with `./scripts/run.sh`
- [ ] Open a composition with beamed 8th notes — beams render with correct slopes
- [ ] Open a composition with 16th and 32nd notes — multi-level beams render correctly
- [ ] Partial beams (dotted rhythms) — stub direction is automatic and correct
- [ ] Manually flip stem direction on a beamed note — `stemDirectionAuto` becomes false, direction persists across layout
- [ ] Manually flip stem direction on an unbeamed note — same override behavior
- [ ] Save and reload — `stemDirectionAuto = false` notes retain their manual direction
- [ ] Open an old v2.1 file — loads correctly, all notes default to auto stem direction
- [ ] Confirm `invertFractionBeamOrientation` tag in old files is silently ignored on load
- [ ] No regressions: clef, key signature, notes, rests, barlines, grace notes render correctly
