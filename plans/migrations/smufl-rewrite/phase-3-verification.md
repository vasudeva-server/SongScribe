**Type:** Verification Plan  <br>
**Parent:** [phase-3-engraving-defaults.md](phase-3-engraving-defaults.md) → Phase 3  <br>
**Master:** [smufl-rewrite.md](smufl-rewrite.md)  <br>
**Created:** 2026-02-15  <br>
**Status:** Ready for Testing

---

# Phase 3: Engraving Defaults - Verification Plan

## Overview

Phase 3 replaces hardcoded stroke/thickness constants with SMuFL engraving defaults. This verification plan provides detailed testing instructions to ensure all visual changes are correct and no rendering artifacts are introduced.

## Expected Visual Changes

Based on the implementation, these are the stroke thickness changes:

| Element | Current (px) | Bravura (px) | Delta | Visibility |
|---------|-------------|-------------|-------|------------|
| Ties | 1.0 | 1.76 | +0.76 | **Very noticeable** - 76% thicker |
| Hairpins | 1.0 | 1.28 | +0.28 | Moderately noticeable |
| Ledger lines | 1.0 | 1.28 | +0.28 | Moderately noticeable |
| Tuplet brackets | 1.0 | 1.28 | +0.28 | Moderately noticeable |
| Ending brackets | 1.0 | 1.28 | +0.28 | Moderately noticeable |
| Thin barlines | 1.5 | 1.28 | -0.22 | Subtle (thinner) |
| Double barline gap | 4.167 | 3.2 | -0.97 | **Very noticeable** - tighter spacing |
| Beam thickness | 4.04 | 4.0 | -0.04 | Imperceptible |
| Stems | 1.0 | 0.96 | -0.04 | Imperceptible |
| Staff lines | 1.0 | 1.04 | +0.04 | Imperceptible |
| Thick barlines | 4.0 | 4.0 | 0 | No change |

## 1. Compilation Check ✓

```bash
./scripts/compile.sh
```

**Expected:** Clean compilation with no errors or warnings.

## 2. Visual Verification

### Launch Application

```bash
./scripts/run-debug.sh
```

### Test Each Element Type

Create a new composition or open an existing one. For each element below, add the specified elements and verify the rendering.

---

#### 2a. Staff Lines, Stems, and Ledger Lines

**What changed:**
- Staff lines: 1.0 → 1.04px (+0.04px)
- Stems: 1.0 → 0.96px (-0.04px)
- Ledger lines: 1.0 → 1.28px (+0.28px)

**How to test:**
1. Add notes within the staff: B4, A4, G4, F4, E4
2. Add notes requiring ledger lines above staff: G5, A5, C6
3. Add notes requiring ledger lines below staff: D4, C4, A3

**Verification checklist:**
- [ ] Staff lines render cleanly with no gaps or jaggedness
- [ ] Staff line thickness is consistent across all five lines
- [ ] Stems have consistent width across all notes
- [ ] Stems connect cleanly to note heads
- [ ] Ledger lines are slightly thicker than staff lines
- [ ] Ledger lines extend appropriately beyond note heads
- [ ] No visual artifacts or rendering glitches

---

#### 2b. Single, Double, and Final Bar Lines

**What changed:**
- Thin barlines: 1.5 → 1.28px (-0.22px)
- Thick barlines: 4.0 → 4.0px (no change)
- Barline separation: 4.167 → 3.2px (-0.97px)

**How to test:**
1. Insert single bar lines (|) between measures
2. Insert double bar lines (||) at section boundaries
3. Insert final double bar lines at composition end

**Verification checklist:**
- [ ] Single bar lines are slightly thinner than before
- [ ] Single bar lines extend from bottom to top staff line
- [ ] Double bar lines have noticeably tighter spacing between lines
- [ ] Double bar line spacing looks visually balanced
- [ ] Final bar lines: thin line is thinner, thick line unchanged
- [ ] Final bar line spacing matches double bar lines
- [ ] All bar lines are vertically aligned and straight

---

#### 2c. Repeat Bar Lines

**What changed:**
- Same thickness/spacing changes as regular bar lines
- Repeat dots should still align correctly

**How to test:**
1. Insert left repeat (|:)
2. Insert right repeat (:|)
3. Insert left-right repeat (:|:)

**Verification checklist:**
- [ ] Repeat dots align correctly with bar lines
- [ ] Dot vertical positioning is centered on staff
- [ ] Thin line thickness matches other thin barlines
- [ ] Thick line thickness matches other thick barlines
- [ ] Line spacing matches double barline spacing
- [ ] Left and right repeats render symmetrically
- [ ] Left-right repeat shows both sets of dots correctly

---

#### 2d. Beamed Note Groups

**What changed:**
- Beam thickness: 4.04 → 4.0px (-0.04px)
- Stem thickness: 1.0 → 0.96px (-0.04px)
- Beam spacing: 6.0 → 6.0px (no net change, now `beamThickness + beamSpacing`)

**How to test:**
1. Create group of 8th notes (quavers) - single beam
2. Create group of 16th notes (semiquavers) - double beam
3. Create group of 32nd notes (demisemiquavers) - triple beam
4. Create mixed group with different note values

**Verification checklist:**
- [ ] Primary beams render with clean, straight edges
- [ ] Secondary/tertiary beams maintain consistent spacing
- [ ] All beam levels have uniform thickness
- [ ] Stems connect cleanly to beams with no gaps
- [ ] Beam angle follows note pitch contour smoothly
- [ ] Multiple beam levels stack evenly
- [ ] Partial beams (for isolated shorter notes) render correctly
- [ ] Stem thickness is consistent across beamed and unbeamed notes

---

#### 2e. Dynamics (Crescendo/Diminuendo)

**What changed:**
- Hairpin thickness: 1.0 → 1.28px (+0.28px)

**How to test:**
1. Add crescendo (<) spanning 3-4 notes
2. Add diminuendo (>) spanning 3-4 notes
3. Test at different vertical positions

**Verification checklist:**
- [ ] Hairpin lines are noticeably thicker than before
- [ ] Lines are clean and sharp with no jagged edges
- [ ] Point (apex) is sharp and well-defined
- [ ] Opening is even on both sides (symmetrical)
- [ ] Crescendo opens to the right
- [ ] Diminuendo opens to the left
- [ ] Vertical positioning is appropriate below staff

---

#### 2f. Ties

**What changed:**
- Tie thickness: 1.0 → 1.76px (+0.76px) - **largest visual change**

**How to test:**
1. Add ties between notes of the same pitch
2. Try ties on different pitches: high (G5), middle (B4), low (E4)
3. Try ties with upward stems
4. Try ties with downward stems
5. Try ties spanning multiple measures

**Verification checklist:**
- [ ] Tie curves are **noticeably thicker** than before (76% increase)
- [ ] Curves are smooth with no jagged edges
- [ ] Curve shape is visually pleasing (not too flat, not too arched)
- [ ] Start point connects cleanly to first note head
- [ ] End point connects cleanly to second note head
- [ ] Ties with upward stems curve above notes
- [ ] Ties with downward stems curve below notes
- [ ] Ties don't overlap with stems or other notation
- [ ] Multi-measure ties render correctly

---

#### 2g. Tuplet Brackets

**What changed:**
- Bracket thickness: 1.0 → 1.28px (+0.28px)

**How to test:**
1. Add triplet (3 notes in space of 2)
2. Test with notes above staff center (stems down)
3. Test with notes below staff center (stems up)
4. If available, test other tuplets (quintuplets, sextuplets)

**Verification checklist:**
- [ ] Bracket lines are slightly thicker than before
- [ ] Horizontal line segment has consistent thickness
- [ ] Vertical end segments have consistent thickness
- [ ] Curved brackets render smoothly (if used)
- [ ] Tuplet number is positioned correctly in bracket gap
- [ ] Tuplet number is readable and properly sized
- [ ] Bracket doesn't overlap with note heads or stems
- [ ] Bracket positioning adjusts correctly for stem direction

---

#### 2h. First/Second Ending Brackets

**What changed:**
- Ending line thickness: 1.0 → 1.28px (+0.28px)

**How to test:**
1. Add first ending bracket (1.)
2. Add second ending bracket (2.)
3. Test with repeat signs
4. Test spanning multiple measures

**Verification checklist:**
- [ ] Bracket lines are slightly thicker than before
- [ ] Vertical segment has consistent thickness
- [ ] Horizontal segment has consistent thickness
- [ ] First ending has closing vertical line
- [ ] Second ending has no closing vertical line
- [ ] Numbers (1, 2) are positioned correctly
- [ ] Numbers are readable and properly sized
- [ ] Bracket aligns correctly with bar lines
- [ ] Multi-measure endings span correctly

---

## 3. General Visual Quality

For each element tested above, verify:

- [ ] **No rendering artifacts** - No gaps, overlaps, or jagged edges
- [ ] **Consistent thickness** - Element thickness is uniform throughout
- [ ] **Proper alignment** - Elements align correctly with adjacent notation
- [ ] **Anti-aliasing** - Smooth rendering on diagonal/curved lines
- [ ] **Color uniformity** - All elements use consistent black color
- [ ] **No performance issues** - Rendering is smooth and responsive

## 4. Automated Tests

```bash
mvn test
```

**Expected:**
- [ ] All tests pass
- [ ] No new test failures introduced
- [ ] No warnings related to stroke calculations

**Note:** Changes are purely visual (stroke thickness) and should not affect layout calculations or test expectations.

## 5. Regression Testing

Test with existing composition files:

1. Open an existing composition with varied notation
2. Verify all elements render correctly
3. Check that element positions haven't changed (only stroke widths)
4. Export composition to file
5. Re-import and verify rendering is identical
6. Test print preview
7. If available, test PDF export

**Regression checklist:**
- [ ] Existing files open without errors
- [ ] All notation elements render correctly
- [ ] No unexpected layout shifts
- [ ] Export/import preserves all information
- [ ] Print preview shows correct stroke thicknesses
- [ ] PDF export (if available) looks correct

## 6. Edge Cases and Error Conditions

Test boundary conditions:

**Very high notes:**
- [ ] Multiple ledger lines above staff render correctly
- [ ] Ledger line thickness is consistent

**Very low notes:**
- [ ] Multiple ledger lines below staff render correctly
- [ ] Ledger line thickness is consistent

**Complex beaming:**
- [ ] Mixed note values (8th + 16th + 32nd) in one beam
- [ ] Grace notes in beamed groups
- [ ] Beams across different pitch levels

**Overlapping elements:**
- [ ] Ties + accidentals
- [ ] Tuplets + dynamics
- [ ] Multiple voice staffs

## 7. Known Issues to Watch For

If you encounter any of these, it indicates a problem:

| Issue | Likely Cause |
|-------|-------------|
| Gaps in staff lines | STAFF_LINE_STROKE initialization error |
| Disconnected stems/beams | STEM_STROKE and BEAM_STROKE misalignment |
| Malformed ties | LINE_STROKE in TieRenderer not using tieMidpointThickness |
| Wrong barline spacing | BAR_LINE_SPACE not using barlineSeparation |
| Uneven beam stacking | INNER_BEAM_OFFSET calculation incorrect |
| Thick elements too thin | Missing StaffSpaces.toPixels() conversion |
| Compilation errors | Missing imports or incorrect metadata access |

## 8. Success Criteria

Phase 3 is successful if:

1. ✓ Compilation completes with no errors
2. ✓ All automated tests pass
3. ✓ Visual inspection shows no rendering artifacts
4. ✓ Ties are noticeably thicker (most dramatic change)
5. ✓ Double barlines have tighter spacing (second most dramatic change)
6. ✓ All other thickness changes are subtle but present
7. ✓ Existing compositions render correctly with new thicknesses
8. ✓ No layout shifts or position changes
9. ✓ Export/import still works correctly
10. ✓ Overall rendering looks clean and professional

## 9. Implementation Summary

Files modified with SMuFL engraving defaults:

- ✓ `BaseElementRenderer.java` - staffLineThickness, stemThickness, legerLineThickness
- ✓ `BarRenderer.java` - thinBarlineThickness, thickBarlineThickness, barlineSeparation
- ✓ `BeamGroupRenderer.java` - beamThickness, stemThickness, beamSpacing
- ✓ `DynamicsRenderer.java` - hairpinThickness
- ✓ `TieRenderer.java` - tieMidpointThickness
- ✓ `TupletRenderer.java` - tupletBracketThickness
- ✓ `EndingRenderer.java` - repeatEndingLineThickness
- ✓ `NoteRenderer.java` - references BaseElementRenderer constants
- ✓ `ArticulationRenderer.java` - no changes (DPI-aware stroke kept as-is)

---

**Next Phase:** After successful verification, proceed to Phase 4 (if defined in master plan).
