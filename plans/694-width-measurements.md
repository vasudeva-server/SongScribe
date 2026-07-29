# Fix and unify notehead width measurements (#694)
**Created:** 2026-07-29  
**Status:** Pending  
**BlockedBy:** —

* * *
## The defect
`SMuFLConstants.NOTE_HEAD_WIDTH_SS` is seeded from the `noteheadBlack` bounding box only (`SMuFLMetadata.noteHeadWidthSs()` → `requireBBox(NOTEHEAD_BLACK).width()` = **1.18**). Bravura's `noteheadWhole` bbox right edge is **1.688**. `NoteRenderer.renderNoteHead` draws `NOTEHEAD_WHOLE` at the column X with zero offset, so a whole note's ink really occupies `[x, x + 1.688]`.

Every site that uses `SMuFLConstants.NOTE_HEAD_WIDTH_SS` to mean _this note's head width_ is therefore short by **0.508 ss (≈ 4.1 px at 100% zoom)** on whole notes. `SEMIBREVE` is the only affected type — there is no breve in `ElementType`, and grace notes scale the black head so they stay consistent.

`ElementType.baseWidthSs` (exposed as `getElementWidthSs()`) is already the correct per-type value for notes: `computeNoteBoundsSs` sets it to `bbox.right()` and the grace path scales it. The fix is mostly routing every consumer to that value and deleting the duplicate paths.

Two related divergences are fixed at the same time:

- Rests set `baseWidthSs` from `bbox.width()` (`computeGlyphBoundsSs`) while `NoteGeometry.getGlyphRightEdgeSs` reads `bbox.right()`. They differ by the glyph's left bearing — 0.004 ss on `restQuarter`, 0 on every other rest. Unifying on `bbox.right()` makes the two measurements byte-identical for every glyph-bearing type so one can be deleted.
  
- `ElementColumnBuilder.HALF_NOTE_HEAD_SS` is half the notehead **width** (0.59) but is used exclusively as a vertical half-**height** (stem top/bottom for stemless columns, and the left-facing collision band). The notehead's real half-height is 0.5, and for rests and barlines the real vertical extent is nothing like either.
  
## Status Dashboard
| Phase | Description | Status | Sub-plan |
| --- | --- | --- | --- |
| 1   | [Unified notehead-width source of truth](#-phase-1-unified-notehead-width-source-of-truth) | ✅ Complete | —   |
| 2   | [Tie endpoints and tuplet bracket geometry](#-phase-2-tie-endpoints-and-tuplet-bracket-geometry) | ✅ Complete | —   |
| 3   | [Trill and note-attached extents](#-phase-3-trill-and-note-attached-extents) | ✅ Complete | —   |
| 4   | [Range-element span widths](#-phase-4-range-element-span-widths) | ✅ Complete | —   |
| 5   | [Per-type vertical notehead extents](#-phase-5-per-type-vertical-notehead-extents) | ✅ Complete | —   |
| 6   | [Manual UI verification](#-phase-6-manual-ui-verification) | ✅ Complete | —   |
| 7   | [Tests](#-phase-7-tests) | ✅ Complete | —   |

* * *
## ✅ Phase 1: Unified notehead-width source of truth
**Status:** Complete  
**BlockedBy:** —  
**Recommended model/effort:** Opus 4.8, high effort — establishes the accessor every later phase calls, deletes a duplicate measurement, and changes an enum initializer that feeds all layout.
### Context
`ElementType` stores two width fields per type, both set in the static initializer at `src/main/java/songscribe/dom/ElementType.java`:

- `fullWidthSs` (`getFullElementWidthSs()`) — includes the stem-up flag extent for notes.
  
- `baseWidthSs` (`getElementWidthSs()`) — the notehead alone for notes; excludes the flag.
  

`computeNoteBoundsSs` (`ElementType.java:605`) sets `baseWidthSs = bbox.right()` for notes (both the stemmed and the stemless/semibreve branch). `computeGraceNoteBoundsSs` (`:657`) sets it to `headBBox.right() * GRACE_NOTE_SCALE`. `computeGlyphBoundsSs` (`:716`) — the rest and breath-mark path — sets it to `bbox.width()`, which is the only place the two disagree.

`BBox` is already in the screen-down convention (`BBox.fromSMuFL` flips Y), and `left()` is the SMuFL `bBoxSW` x, so `right()` is the distance from the glyph origin to its right edge — exactly what a column's right extent needs.
### Tasks
1. In `ElementType.computeGlyphBoundsSs` (`src/main/java/songscribe/dom/ElementType.java:716-722`), change the width argument of `setSymmetricBounds` from `bbox.width()` to `bbox.right()`. This makes `baseWidthSs` the glyph's right edge measured from the origin for every glyph-bearing type. The only observable change is `CROTCHET_REST`, which gains 0.004 ss (its `bBoxSW` x is 0.004; every other rest's is 0.0). Update the Javadoc on `getElementWidthSs()` (`:248-253`) and `getElementCenterXSs()` (`:255-261`) to say the value is the glyph's right edge from the element origin, not a bounding-box width.
  
2. Add `public static double noteheadWidthSs(ElementType noteType)` to `src/main/java/songscribe/layout/NoteGeometry.java`, returning `noteType.getElementWidthSs()`. Javadoc it as the single source of truth for "how wide is this element's head glyph, measured from its origin" — per type, grace-scaled, excluding stem, flag, and augmentation dots — and state explicitly that `SMuFLConstants.NOTE_HEAD_WIDTH_SS` is the `noteheadBlack` width and must not be used for this purpose because `noteheadWhole` is 1.688 where `noteheadBlack` is 1.18.
  
3. Delete `NoteGeometry.getGlyphRightEdgeSs(StaffElement)` (`src/main/java/songscribe/layout/NoteGeometry.java:754-759`). It is now identical to `noteType.getElementWidthSs()` for every type it accepts. Its sole caller is `src/main/java/songscribe/layout/stacking/SystemStacker.java:147-150`, where the two branches of the `elementType.getSMuFLGlyph() != null` conditional now produce the same value — collapse them to a single `var anchorWidthSs = elementType.getElementWidthSs();` and drop the now-stale comment about barlines having no glyph to measure.
  
4. In `src/main/java/songscribe/layout/ElementColumnBuilder.java`, change `getNoteheadRightExtent(ElementType type)` (`:347-351`) to `return NoteGeometry.noteheadWidthSs(type);`, deleting the `isGraceNote()` ternary. Keep the `GRACE_NOTE_HEAD_WIDTH_SS` constant and its `#560` Javadoc at `:61-73` — it is referenced by existing tests and still documents why the grace head is the scaled black head rather than `noteheadBlackSmall`; add one sentence noting it is now equal to `GRACE_QUAVER.getElementWidthSs()` and is retained as documentation rather than as the live value. This single change fixes both consumers named in issue #694: `:209` (`column.setNoteheadWidthSs(...)`, which drives lyric centering) and `:308` (`noteheadRightExtentSs`, which seeds the column's right extent).
  
5. Read `ElementColumn.getFlagExtentSs()` (`src/main/java/songscribe/layout/ElementColumn.java:271-273`) and confirm it stays non-negative: it is `rightExtentExcludingAugmentationSs - noteheadWidthSs`, and after task 4 both operands derive from the same per-type width, so a whole note yields exactly 0 rather than −0.508. Do not change the method; this is a correctness check that the two values moved together. Also add a `{@link songscribe.layout.NoteGeometry#getNoteheadCenterXSs}` cross-reference to the Javadoc of `ElementColumn.getNoteheadCenterXSs()` (`:360-367`) noting that the two now agree for every type — before this change they disagreed by 0.254 ss on a whole note.
  
6. Run `./scripts/compile.sh` and confirm SUCCESS.
  

* * *
## ✅ Phase 2: Tie endpoints and tuplet bracket geometry
**Status:** Complete  
**BlockedBy:** 1  
**Recommended model/effort:** Sonnet 4.6, medium effort — mechanical signature threading, but each change has callers in two places that must stay in lockstep.
### Context
Phase 1 added `NoteGeometry.noteheadWidthSs(ElementType noteType)` in `src/main/java/songscribe/layout/NoteGeometry.java`, returning the per-type notehead width from the glyph bounding box (1.688 for a whole note, 1.18 for every other notehead, grace-scaled for grace notes). Use it everywhere below. Do not use `SMuFLConstants.NOTE_HEAD_WIDTH_SS`, which is the `noteheadBlack` width and is wrong for whole notes.

Phase 1 also made `ElementColumn.getNoteheadWidthSs()` per-type for note columns, so a column that is already in hand does not need the type looked up again.
### Tasks
1. In `src/main/java/songscribe/layout/LayoutEngine.java`, change `tieEndpointXSs(double noteLeftXSs, int dir, boolean centerAttach)` (`:942-947`) to take an additional `ElementType noteType` parameter and replace both uses of `SMuFLConstants.NOTE_HEAD_WIDTH_SS` (`:943`, `:944`) with `NoteGeometry.noteheadWidthSs(noteType)` (evaluate it once into a local). Update the two call sites at `:887-888` to pass the type of the element whose head that endpoint attaches to — `startColumn`'s element type for the `startXSs` call and `endColumn`'s element type for the `endXSs` call. Update the `@param` block to document the new parameter. Without this, a tie into or out of a whole note attaches 0.508 ss inside the notehead's ink.
  
2. In `src/main/java/songscribe/dom/Tuplet.java`, change `bracketLeftEdgeXSs(double anchorXSs, boolean anchorStemUp, double stemSs)` (`:151-155`) and `bracketRightEdgeXSs(double endXSs)` (`:162-164`) to each take an additional `double noteheadWidthSs` parameter, replacing every `SMuFLConstants.NOTE_HEAD_WIDTH_SS` in their bodies. In `bracketLeftEdgeXSs` both occurrences refer to the same anchor note's head, so both take the one new parameter. Update the Javadoc on both to say the width is the anchor's / end note's own head width.
  
3. Update both callers of the methods changed in task 2 so the reserved and drawn arm positions stay in lockstep: `TupletRenderer.renderTupletsFromLine` in `src/main/java/songscribe/ui/renderer/TupletRenderer.java` and `StructuralStacker.computeTupletClearanceLeftYSs` in `src/main/java/songscribe/layout/stacking/StructuralStacker.java`. Locate them with `rg -n "bracketLeftEdgeXSs|bracketRightEdgeXSs" src/main`. Each caller has the anchor and end columns or elements in hand — pass `NoteGeometry.noteheadWidthSs(<element>.getType())`, or `<column>.getNoteheadWidthSs()` when an `ElementColumn` is available.
  
4. In `src/main/java/songscribe/layout/stacking/StructuralStacker.java`, method `boundEdgeXSs` (`:499-515`), replace `SMuFLConstants.NOTE_HEAD_WIDTH_SS` at `:501` with `column.getNoteheadWidthSs()`. The method is documented as taking "the outer non-rest column", so the column is always a note column and its notehead width was set per-type by `ElementColumnBuilder` in Phase 1.
  
5. Run `./scripts/compile.sh` and confirm SUCCESS. If `SMuFLConstants` is now an unused import in any file you touched, remove it.
  

* * *
## ✅ Phase 3: Trill and note-attached extents
**Status:** Complete  
**BlockedBy:** 1  
**Recommended model/effort:** Sonnet 4.6, low effort — direct constant substitutions with the element already in scope at every site.
### Context
Phase 1 added `NoteGeometry.noteheadWidthSs(ElementType noteType)` in `src/main/java/songscribe/layout/NoteGeometry.java`, returning the per-type notehead width from the glyph bounding box (1.688 for a whole note, 1.18 for every other notehead, grace-scaled for grace notes). Use it at every site below. Do not use `SMuFLConstants.NOTE_HEAD_WIDTH_SS`, which is the `noteheadBlack` width and is short by 0.508 ss on whole notes.
### Tasks
1. In `src/main/java/songscribe/ui/renderer/TrillRenderer.java:95`, replace `layoutResult.getElementXSs(endNote) + SMuFLConstants.NOTE_HEAD_WIDTH_SS` with the same expression using `NoteGeometry.noteheadWidthSs(endNote.getType())`. `endNote` is already null-checked by the enclosing `if (endNote != null && endNote != anchor)`. Without this the wavy line stops 0.508 ss short of a whole note's right edge.
  
2. In `src/main/java/songscribe/layout/stacking/NoteAttachedStacker.java`, method `computePreviewDecorationLayouts(StaffElement note, double xSs)`, replace `SMuFLConstants.NOTE_HEAD_WIDTH_SS` at `:293` (the `lineWidthSs` computation) and at `:298` and `:299` (the two `extents.ySet` calls) with `NoteGeometry.noteheadWidthSs(note.getType())`, evaluated once into a local before `:293`.
  
3. In the same file, replace `SMuFLConstants.NOTE_HEAD_WIDTH_SS` at `:388` and `:389` (the two `noteAttachedExtents.ySet` calls) with `NoteGeometry.noteheadWidthSs(element.getType())` — the loop variable `element` is in scope there. Also replace the constant at `:856` (`StaffExtents.Profile.flat(...)`, the trill spanner's per-note clearance box) with `NoteGeometry.noteheadWidthSs(note.getType())`.
  
4. Run `./scripts/compile.sh` and confirm SUCCESS. If `SMuFLConstants` is now an unused import in any file you touched, remove it.
  

* * *
## ✅ Phase 4: Range-element span widths
**Status:** Complete  
**BlockedBy:** 1  
**Recommended model/effort:** Sonnet 4.6, medium effort — an abstract-method signature change across six implementations and three call sites; mechanical but wide.
### Context
`RangeElement.getSpanWidthSs(double anchorXSs, double endXSs)` (`src/main/java/songscribe/dom/RangeElement.java:198`) is abstract with six implementations. Two of them extend the span past the end note by a hardcoded `SMuFLConstants.NOTE_HEAD_WIDTH_SS`, which is the `noteheadBlack` width and so is 0.508 ss short when the span ends on a whole note:

- `Hairpin.getSpanWidthSs` (`src/main/java/songscribe/dom/Hairpin.java:115-117`): `Math.max(HAIRPIN_OPENING_HEIGHT_SS, endXSs - anchorXSs + SMuFLConstants.NOTE_HEAD_WIDTH_SS)`
  
- `Ending.getSpanWidthSs` (`src/main/java/songscribe/layout/Ending.java:349-351`): `Math.max(SMuFLConstants.NOTE_HEAD_WIDTH_SS, endXSs - anchorXSs + SMuFLConstants.NOTE_HEAD_WIDTH_SS)` — note the two occurrences mean different things: the first is a generic minimum-span floor and must stay as it is, the second covers the end note's head and must become per-type.
  

The other four (`Trill:122`, `Tuplet:218`, `Beam:56`, `Tie:57`) clamp to their own minimums or glyph widths and never reference a notehead width.

Phase 1 added `NoteGeometry.noteheadWidthSs(ElementType noteType)` in `src/main/java/songscribe/layout/NoteGeometry.java`, and made `ElementColumn.getNoteheadWidthSs()` per-type for note columns.
### Tasks
1. Change the abstract declaration to `public abstract double getSpanWidthSs(double anchorXSs, double endXSs, double endNoteheadWidthSs)` in `src/main/java/songscribe/dom/RangeElement.java:198`. Javadoc the new parameter as the end element's own notehead width, and state that implementations whose span does not extend past the end element's origin ignore it.
  
2. Update the four implementations that ignore the new parameter — `Trill.getSpanWidthSs` (`src/main/java/songscribe/dom/Trill.java:122`), `Tuplet.getSpanWidthSs` (`src/main/java/songscribe/dom/Tuplet.java:218`), `Beam.getSpanWidthSs` (`src/main/java/songscribe/dom/Beam.java:56`), and `Tie.getSpanWidthSs` (`src/main/java/songscribe/dom/Tie.java:57`) — to accept it and note in one line of Javadoc that it is unused because the span ends at the end element's origin.
  
3. Update `Hairpin.getSpanWidthSs` (`src/main/java/songscribe/dom/Hairpin.java:115-117`) to use `endNoteheadWidthSs` in place of `SMuFLConstants.NOTE_HEAD_WIDTH_SS`. Update `Ending.getSpanWidthSs` (`src/main/java/songscribe/layout/Ending.java:349-351`) to use `endNoteheadWidthSs` for the second occurrence only — the first occurrence is the minimum-span floor and stays `SMuFLConstants.NOTE_HEAD_WIDTH_SS`. Add a comment on that line saying it is a generic floor, not the end note's head, so a later reader does not "fix" it.
  
4. Update the three call sites, each of which already has the end column in scope: `StructuralStacker.java:167` (`tuplet.getSpanWidthSs(anchorXSs, endColumn.getXSs())`), `StructuralStacker.java:653` (`element.getSpanWidthSs(anchorXSs, endXSs)`, where `endColumn` is bound just above), and `NoteAttachedStacker.java:873` (`trill.getSpanWidthSs(anchorXSs, endXSs)`). Pass `endColumn.getNoteheadWidthSs()` where an `ElementColumn` is available; otherwise `NoteGeometry.noteheadWidthSs(endNote.getType())`.
  
5. Run `./scripts/compile.sh` and confirm SUCCESS. Existing tests in `HairpinTest`, `EndingTest`, `TrillTest`, `TupletTest`, `BeamTest`, and `TieTest` call `getSpanWidthSs` with two arguments and will fail to compile — update those call sites to pass `SMuFLConstants.NOTE_HEAD_WIDTH_SS` as the third argument so the existing assertions keep their current expected values. Do not add new test cases here; Phase 7 covers new coverage. Then run `./scripts/test.sh unit HairpinTest EndingTest TrillTest TupletTest BeamTest TieTest` and confirm green.
  

* * *
## ✅ Phase 5: Per-type vertical notehead extents
**Status:** Complete  
**BlockedBy:** 1  
**Recommended model/effort:** Opus 4.8, high effort — a cross-axis correction whose blast radius reaches optical spacing and vertical stacking; requires reasoning about which consumers are gated.
### Context
`ElementColumnBuilder.HALF_NOTE_HEAD_SS` (`src/main/java/songscribe/layout/ElementColumnBuilder.java:75-76`) is `SMuFLConstants.NOTE_HEAD_WIDTH_SS / 2.0` = **0.59**, i.e. half the notehead's _width_. Its comment claims it is "for left/right extent calculation", but every use is a **vertical** Y value:

- `calculateStemTopSs` (`:381-400`) returns `-HALF_NOTE_HEAD_SS` at `:386` (stemless elements) and `:399` (stem-down notes, where the top is the notehead top).
  
- `calculateStemBottomSs` (`:414-433`) returns `HALF_NOTE_HEAD_SS` at `:419` (stemless) and `:432` (stem-up notes).
  
- `ElementColumn.getLeftFacingTopYSs()` (`src/main/java/songscribe/layout/ElementColumn.java:429-431`) returns `getPositionSs() - ElementColumnBuilder.HALF_NOTE_HEAD_SS`.
  

The notehead's real half-height is 0.5 (`noteheadBlack` bbox spans −0.5…+0.5 screen-down). For rests and barlines the real vertical extent is nothing like 0.59 — `restQuarter` spans −1.492…+1.5, and a barline column spans the full staff height.

`ElementType` already stores the correct values, set in the same static initializer that sets the widths: `noteheadTopOffsetSs` (exposed as `getNoteheadTopOffsetSs()`, screen-down, negative = above) and `fullElementHeightSs` (exposed as `getFullElementHeightSs()`). `BBox` is already screen-down (`BBox.fromSMuFL` flips Y), so `getNoteheadTopOffsetSs()` is `bbox.top()` directly. Concretely: notes −0.5…+0.5; `restQuarter` −1.492…+1.5; barlines and repeats ∓`Staff.STAFF_HEIGHT_SS / 2` (set by `computeBarlineBoundsSs` / `computeRepeatBoundsSs` via `setSymmetricBounds`); grace notes scaled by `GRACE_NOTE_SCALE`.
### Tasks
1. Add `public double getNoteheadBottomOffsetSs()` to `src/main/java/songscribe/dom/ElementType.java`, next to the existing `getNoteheadTopOffsetSs()` (`:301-308`), returning `noteheadTopOffsetSs + fullElementHeightSs`. Javadoc it as the Y offset from the notehead center to the bottom of the head glyph, positive (below center), mirroring `getNoteheadTopOffsetSs()`.
  
2. In `src/main/java/songscribe/layout/ElementColumnBuilder.java`, replace the four uses of `HALF_NOTE_HEAD_SS`: `:386` and `:399` become `elementType.getNoteheadTopOffsetSs()` (already negative — do not negate it, and remove the leading minus sign), and `:419` and `:432` become `elementType.getNoteheadBottomOffsetSs()`. Delete the `HALF_NOTE_HEAD_SS` constant at `:75-76`. Update the Javadoc on both methods to say the stemless / non-stem-side extent is the element's own glyph bbox edge, not a fixed notehead half-width.
  
3. In `src/main/java/songscribe/layout/ElementColumn.java`, change `getLeftFacingTopYSs()` (`:429-431`) to `return getPositionSs() + element.getType().getNoteheadTopOffsetSs();` (the field `element` is in scope). Update its Javadoc, which currently says "the left-facing band starts at the notehead top in every case" — that stays true, but the value is now the element's own glyph top rather than a fixed offset.
  
4. Before compiling, verify by reading that the blast radius is bounded as expected, and record what you found in the commit-ready state of the code (no new files):
  
  - `OpticalSpacing.oppositeStemCorrectionSs` and `sameDirectionCorrectionSs` (`src/main/java/songscribe/layout/OpticalSpacing.java:~190` and `:225-228`) both early-return when `!prev.hasStem() || !curr.hasStem()`, so rest and barline columns never reach `verticalOverlapSs(columnA, columnB)`. Stemmed notes' spans tighten by 0.09 ss on the non-stem side, which slightly reduces these corrections.
    
  - `OpticalSpacing.downstemAfterBarlineCorrectionSs` (`:~253-266`) hardcodes the barline's span as `-Staff.STAFF_HALF_SS … Staff.STAFF_HALF_SS` rather than reading the column, so widening a barline column's span does not double-count there.
    
  - `StructuralStacker`'s tuplet slope (`:209`, `:222`, `:337`) uses `collectNonRestSpannedColumns`, so rests are excluded; confirm whether a barline column can appear inside a tuplet span and, if it can, that the widened barline span is the intended bound (a barline genuinely does occupy the full staff height).
    
  - `HorizontalSpacingCalculator:253` passes `getLeftFacingTopYSs()` / `getLeftFacingBottomYSs()` to `ElementColumn.getRightExtentFacingSs` for grace-note flag discounting. A rest neighbour's band is now much taller, so a grace's flag is charged more often — strictly more conservative spacing, never tighter.
    
  - `HorizontalSpacingCalculator:636-650` (`describeForLog`) only reads these values for debug output and needs no change.
    
5. Run `./scripts/compile.sh` and confirm SUCCESS. Then run `./scripts/test.sh unit` and report the result. Existing assertions in `ElementColumnBuilderTest` (`:533-535`, `:574-576`, `:600-611`, `:629`) and `ElementColumnTest` (`:245-259`) reference `ElementColumnBuilder.HALF_NOTE_HEAD_SS` and will fail to compile. Update them to use `ElementType.getNoteheadTopOffsetSs()` / `getNoteheadBottomOffsetSs()` for the element type under test, so each assertion keeps testing the same property against the corrected expected value. Do not weaken an assertion to make it pass; if one fails on a value rather than on compilation, stop and report which and why.
  

* * *
## ✅ Phase 6: Manual UI verification
**Status:** Complete  
**BlockedBy:** 2, 3, 4, 5  
**Recommended model/effort:** Sonnet 4.6, low effort — build, launch, and present the checklist; the user makes the calls.
### Tasks
1. Run `./scripts/compile.sh` and confirm SUCCESS.
  
2. Ask the user for permission before launching, then run `./scripts/run.sh`. Never launch without explicit permission.
  
3. Ask the user to build a short test line containing, at minimum: a whole note with a syllable under it, a whole note tied to another note, a whole note under a hairpin or volta bracket, a whole note inside a tuplet, a whole note with a trill spanning to a later note, and a whole note carrying a melisma extender. Then ask them to confirm each of the following, one at a time:
  
  - The syllable is centered on the whole notehead (previously ~2 px left of center).
    
  - The tie's endpoint sits just outside the whole notehead's ink, not 0.5 ss inside it.
    
  - The gap after an undotted whole note is not visibly tighter than after a half note.
    
  - The tuplet bracket arm clears the whole notehead.
    
  - The trill's wavy line reaches the whole notehead's right edge.
    
  - The melisma extender starts at the whole notehead's right edge.
    
  - Rests and barlines are spaced no worse than before (Phase 5 widened their vertical extents, which feeds grace-note flag discounting and can only loosen spacing, never tighten it).
    
4. If the user reports any regression, stop and report it rather than proceeding. Do not start Phase 7 until the user has confirmed the behavior is correct.
  
### Outcome
Every check passed except the tuplet bracket arm: with a whole note at the start of a tuplet, the left arm was flush with the notehead's right edge instead of clearing its left edge.

Cause: `Tuplet.bracketLeftEdgeXSs` chose the arm inset from the anchor's *stored* stem direction. A whole note draws no stem but still holds a direction, and when that direction is UP the formula inset only the stem thickness. Phase 1 did not introduce this — with the old 1.18 constant the arm landed inside the wider whole head; the corrected 1.688 width moved it out to the head's right edge, where it became visible.

Fix: `bracketLeftEdgeXSs` (`src/main/java/songscribe/dom/Tuplet.java:154`) takes a new `anchorHasStem` parameter and insets a full notehead width whenever the anchor is stemless, matching the down-stem geometry. Both callers derive the flag from the same predicate so reserved and drawn arms stay in lockstep: `StructuralStacker.java:178` passes `anchorColumn.hasStem()`, `TupletRenderer.java:109` passes `anchorType.isNoteWithStem()` (`ElementColumn.hasStem()` delegates to it). `./scripts/compile.sh` SUCCESS; `./scripts/test.sh unit` green (6388 passed, 1 skipped).

Phase 7 should add a case pinning a `SEMIBREVE` anchor's left arm to `anchorXSs - Tuplet.ARM_EXTENSION_SS` for both stored stem directions.
  

* * *
## ✅ Phase 7: Tests
**Status:** Complete  
**BlockedBy:** 6  
**Recommended model/effort:** Sonnet 4.6, medium effort — new cases in existing suites following each suite's established patterns.
### Context
Read `.agents/guides/testing-common.md` and `.agents/guides/testing-unit.md` before writing anything. All new tests are unit tests; do not write e2e tests.

The invariant under test throughout: an element's head width is its own glyph's bounding-box right edge — 1.688 ss for `SEMIBREVE` (`noteheadWhole`), 1.18 ss for every other notehead (`noteheadBlack` / `noteheadHalf`), and 1.18 × `ElementType.GRACE_NOTE_SCALE` for `GRACE_QUAVER`. It is reached through `NoteGeometry.noteheadWidthSs(ElementType)`. `SMuFLConstants.NOTE_HEAD_WIDTH_SS` is the `noteheadBlack` width and is no longer the notehead width for whole notes.
### Tasks
1. In `src/test/java/songscribe/layout/NoteGeometryTest.java`, add cases for `NoteGeometry.noteheadWidthSs`: `SEMIBREVE` is strictly greater than `MINIM` and `CROTCHET`; `MINIM` equals `CROTCHET` equals `SMuFLConstants.NOTE_HEAD_WIDTH_SS`; `GRACE_QUAVER` equals `SMuFLConstants.NOTE_HEAD_WIDTH_SS * ElementType.GRACE_NOTE_SCALE`. Assert against the glyph metadata (`SMuFLMetadata.requireBBox(SMuFLGlyph.NOTEHEAD_WHOLE).right()`) rather than the literal 1.688, so the test tracks a font change.
  
2. In `src/test/java/songscribe/layout/ElementColumnBuilderTest.java`, add cases asserting that a `SEMIBREVE` column's `getNoteheadWidthSs()` and its `getRightExtentExcludingAugmentationSs()` both equal `NoteGeometry.noteheadWidthSs(ElementType.SEMIBREVE)`, and that `getFlagExtentSs()` is exactly 0 for it (it was −0.508 if the two measurements ever drift apart again). Also assert `ElementColumnBuilder.GRACE_NOTE_HEAD_WIDTH_SS` equals `ElementType.GRACE_QUAVER.getElementWidthSs()`, pinning the retained constant to the live value.
  
3. In `src/test/java/songscribe/layout/LyricLayoutBuilderTest.java`, add a case that a syllable on a `SEMIBREVE` is centered on the notehead: its box center equals the column X plus `NoteGeometry.noteheadWidthSs(ElementType.SEMIBREVE) / 2`. Add a companion case that the same column's `getNoteheadCenterXSs()` equals `column.getXSs() + NoteGeometry.getNoteheadCenterXSs(element)` — the two same-named accessors that disagreed by 0.254 ss on a whole note before this change.
  
4. In `src/test/java/songscribe/layout/LayoutEngineTest.java`, add a case for `tieEndpointXSs` showing that a `SEMIBREVE` left endpoint lands further right than a `CROTCHET` left endpoint at the same column X, by exactly the difference in their notehead widths.
  
5. In `src/test/java/songscribe/dom/HairpinTest.java` and `src/test/java/songscribe/layout/EndingTest.java`, add a case each passing `NoteGeometry.noteheadWidthSs(ElementType.SEMIBREVE)` as the `endNoteheadWidthSs` argument to `getSpanWidthSs` and asserting the span is wider than the same call with `SMuFLConstants.NOTE_HEAD_WIDTH_SS`. In `EndingTest`, also pin the minimum-span floor case: with a zero-length span the result is `SMuFLConstants.NOTE_HEAD_WIDTH_SS`, unaffected by the end element's type.
  
6. In `src/test/java/songscribe/layout/ElementColumnTest.java`, add cases that a `CROTCHET_REST` column's `getAbsoluteTopYSs()` / `getAbsoluteBottomYSs()` match its own glyph bbox (`ElementType.CROTCHET_REST.getNoteheadTopOffsetSs()` / `getNoteheadBottomOffsetSs()` offset from `getPositionSs()`), and that a stemless `SEMIBREVE` column spans exactly ±0.5 rather than ±0.59.
  
7. Run `./scripts/compile.sh`, then `./scripts/test.sh unit`, and confirm both report SUCCESS / green. Report any failure with its output rather than adjusting an assertion to match observed behavior.
  
### Outcome
14 tests added; `./scripts/compile.sh` SUCCESS and `./scripts/test.sh unit` green (6402 passed, 1 skipped — up from 6388). No assertion was weakened and no production code changed.

Deviations from the tasks as written, all because the coverage was already in place or the stated expected value was wrong:

- **Task 1** — Phase 1 had already added `NoteGeometryTest.NoteheadWidth` with the crotchet-vs-bbox, grace-scaling, and semibreve > crotchet cases. Added the three that were missing: semibreve equals `SMuFLMetadata.requireBBox(NOTEHEAD_WHOLE).right()`, `MINIM` equals `CROTCHET` equals `SMuFLConstants.NOTE_HEAD_WIDTH_SS` (plus semibreve > minim), and `GRACE_QUAVER` equals the constant times `GRACE_NOTE_SCALE`.
  
- **Task 2** — the `GRACE_NOTE_HEAD_WIDTH_SS` pin already existed as `testGraceNoteheadWidthIsTheGraceScaledHeadTheRendererPaints`. Added only the `SEMIBREVE` column case.
  
- **Task 5** — the `EndingTest` floor case as specified does not hold: `getSpanWidthSs` is `max(NOTE_HEAD_WIDTH_SS, span + endNoteheadWidthSs)`, so a zero-length span on a `SEMIBREVE` returns 1.688, not the floor. The floor branch is only reachable with an end head *narrower* than 1.18, so the test pins it with `GRACE_QUAVER` (0.826) and asserts that precondition explicitly.
  
- **Phase 6 follow-up** — added `TupletTest.BracketLeftEdgeXSs` with the stemless-anchor case (arm at `anchorXSs - ARM_EXTENSION_SS` for both stored stem directions) and an up-stem companion proving the direction still matters when the anchor does have a stem.
