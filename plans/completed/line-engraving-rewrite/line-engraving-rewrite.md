# Engraving Rewrite: Fresh Architecture Design

## Status Dashboard

| Phase | Status | Sub-plans |
|-------|--------|-----------|
| 1     | ✅ Complete | — |
| 2     | ✅ Complete | [phase-2-horizontal-spacing.md](phase-2-horizontal-spacing.md) |
| 3     | ✅ Complete | [phase-3-vertical-layout.md](phase-3-vertical-layout.md) |
| 4     | ✅ Complete | — |
| 5     | ✅ Complete | [phase-5-integration-status.md](phase-5-integration-status.md) |
| 6     | ✅ Complete | — |
| 7     | ✅ Complete | — |

## Overview

Complete rewrite of SongScribe's layout system from scratch, replacing the existing renderer/strategy architecture with a new design built around:
- **Note columns** as the fundamental spacing unit
- **Lyric-driven horizontal spacing** (non-proportional, Gould/Ross principles)
- **Bounding areas** for collision detection (not bounding boxes)
- **Clean, unified pipeline** for both horizontal and vertical layout

## Design Principles

### From Gould/Ross Vocal Engraving Rules
1. Spacing is **non-proportional** - rhythmic value does not determine horizontal distance
2. **Lyrics dominate spacing decisions** - optical clarity outweighs rhythmic proportionality
3. Uneven spacing is expected and correct
4. Accidentals only increase spacing when minimum clearance would be violated
5. Hyphens show syllable division only; extenders show duration

### From line-layout.md
1. All attachments are **above the staff** (no placement property)
2. Vertical stacking order: Note → Articulations → Trill → Fermata → Dynamics → Endings → Tempo → Annotations → Attribution
3. Lyrics are **below the staff** (2.5 MU below lowest note bounding area)
4. Each layer builds on the previous layer's bounding area
5. First note: 11.5 MU from right extent of clef/key signature

### Core Concepts
1. **Note column**: The fundamental horizontal spacing unit (note head + accidentals + dots + stem + flag)
2. **Bounding area**: The space an element actually occupies (elements can nest/overlap in defined ways)
3. **Spring constraints**: Flexible spacing with minimum requirements (not rigid boxes)
4. **No X-range filtering**: Proper bounding area collision detection makes it unnecessary

---

## Architecture Design

### Layer 1: Note Column Builder

**Purpose**: Construct note columns as the fundamental spacing unit

**Input**: `Line` with `List<Note>`

**Output**: `List<NoteColumn>` where each column contains:
```
NoteColumn {
    Note note                    // The note (or rest, barline, etc.)
    List<Note> graceNotes        // Grace notes anchored to this note (borrow space from left)
    double leftExtent            // Left edge (includes accidental + grace notes if present)
    double rightExtent           // Right edge (includes dots if present)
    double stemTop               // Top of stem (if stem up)
    double stemBottom            // Bottom of stem (if stem down)
    Syllable syllable            // Associated lyric syllable (if any)
    double syllableWidth         // Measured width of syllable text
    BeamGroup beamGroup          // Beam group this note belongs to (if any)
}
```

**Key calculations**:
- Accidental width (projects left from note head)
- Grace note extent (projects left, borrowed from main note's space)
- Note head width
- Dot width (projects right from note head)
- Stem length and direction
- Flag extent (if unbeamed) or beam connection point (if beamed)

**What creates columns**:
- Notes → YES (full columns)
- Rests → YES (full columns)
- Barlines → YES (columns with minimal extent)
- Grace notes → NO (part of main note's column, extend leftward)
- Glissandi → NO (connective graphics, adapt to existing spacing)
- Breath marks → Minimal (attached to note, slight space after)

### Layer 2: Horizontal Spacing Calculator

**Purpose**: Calculate X positions for all note columns based on visual constraints

**Algorithm**:
```
1. First note column X = clefRightExtent + keySignatureWidth + 11.5 MU

2. For each subsequent column:
   a. Calculate minimum spacing:
      - Previous column rightExtent + MIN_COLUMN_GAP → current column leftExtent

   b. Calculate lyric spacing requirement:
      - Previous syllable width / 2 + MIN_SYLLABLE_GAP + current syllable width / 2

   c. Final spacing = MAX(minimum spacing, lyric spacing requirement)

   d. If current note has accidental:
      - Check: previousRightExtent + MIN_COLUMN_GAP ≤ accidentalLeftEdge
      - If not, push column right to maintain clearance

   e. If current note has grace notes:
      - Grace notes borrow space from main note's left
      - Must not push previous note leftward
      - If insufficient space, grace notes compress (they are subordinate)

3. For beam groups:
   a. First pass: calculate tight internal spacing (ignoring lyrics)
   b. Check if lyrics under beam group require expansion
   c. If yes, distribute expansion across beam group columns evenly
   d. Maintain clear separation from adjacent beam groups/rests
```

**Constants** (all in MU, defined in one place):
```
FIRST_NOTE_OFFSET = 11.5         // From clef/key signature
MIN_COLUMN_GAP = 0.25            // Minimum between columns
MIN_SYLLABLE_GAP = 0.5           // Minimum between syllables (TBD)
```

### Layer 3: Vertical Stacking Calculator

**Purpose**: Calculate Y positions for all elements above/below staff using bounding areas

**Stacking order** (bottom to top, above staff):
1. Note (head, stem, flag, ledger lines)
2. Articulations (staccato, accent)
3. Trill
4. Fermata
5. Dynamics (point and range)
6. First/second endings
7. Tempo/beat change
8. Text annotations
9. Attribution

**Below staff**:
- Lyrics (ascent 2.5 MU below lowest note bounding area)

**Algorithm**:
```
For each note column:
    1. Initialize bounding area from note column bounds

    2. For each layer (articulations → attribution):
       a. Get elements attached to this note at this layer
       b. For each element:
          - Calculate element's content area
          - Find Y position that clears accumulated bounding area + margin
          - Update accumulated bounding area

    3. Track maximum height reached for line height calculation
```

**Bounding area collision detection**:
- Use `java.awt.geom.Area` for non-rectangular bounds
- Elements can nest inside others (e.g., notes inside ending brackets)
- Collision = actual intersection, not bounding box overlap

### Layer 4: Line Justification Calculator

**Purpose**: Adjust spacing to fit notes within the staff right margin (like justified text)

**Algorithm**:
```
1. After calculating all column positions, check if rightmost column exceeds staff right margin

2. If line exceeds margin:
   a. Calculate required compression ratio:
      - compressionRatio = availableWidth / calculatedWidth

   b. Check if compression is possible:
      - Apply compression ratio to all inter-column gaps
      - Verify no gap falls below MIN_COLUMN_GAP
      - Verify no syllable gap falls below MIN_SYLLABLE_GAP

   c. If compression is possible:
      - Apply compression ratio uniformly
      - Redistribute column positions

   d. If compression would violate minimum gaps:
      - REJECT the note addition
      - Alert user: "Note cannot fit on line while maintaining minimum spacing"

3. If line is under margin (normal case):
   - No adjustment needed (uneven spacing is correct per Gould/Ross)
   - Do NOT expand to fill margin (that would be proportional spacing)
```

**Key principle**: Compression is allowed (with limits), expansion for justification is NOT done (preserves Gould/Ross non-proportional spacing).

### Layer 5: Line Height Calculator

**Purpose**: Determine total line height based on stacked elements

**Calculation**:
```
lineHeight = staffHeight
           + maxAboveStaffHeight
           + lyricHeight (if any)
           + interLineMargin
```

### Layer 6: Rendering Interface

**Purpose**: Provide positioned elements to rendering code

**Output**: `LayoutResult` containing:
```
LayoutResult {
    Map<Note, NoteColumn> columns
    Map<LineElement, Position> positions
    double lineHeight
    double staffTopY
    double staffBottomY
    double lyricBaselineY
}
```

**Key principle**: Rendering code receives final positions; it does not calculate any positions.

---

## Data Model Changes

### Remove from Attachment
- `placement` property (always above staff)

### Keep/Preserve
- Note, Articulation, Attachment class hierarchy
- RangeElement relationships (anchorNote, endNote)
- Line → Note → Articulation/Attachment ownership
- LineElement base with position and margins

### New/Modified
- Lyric rendering: extenders for duration, single hyphen for syllable division

---

## Implementation Plan

### Phase 1: Foundation
1. Create `NoteColumn` data class
2. Create `LayoutConstants` with all spacing values
3. Create `NoteColumnBuilder` to construct columns from notes

### Phase 2: Horizontal Layout
4. Create `HorizontalSpacingCalculator`
5. Implement clef/key signature width measurement
6. Implement first note positioning rule
7. Implement column-to-column spacing with lyric dominance
8. Implement accidental clearance rule

### Phase 3: Vertical Layout
9. Create `VerticalStackingCalculator`
10. Implement layer-by-layer stacking with bounding areas
11. Implement margin rules per element type
12. Calculate line height

### Phase 4: Line Justification
13. Create `LineJustificationCalculator`
14. Implement compression ratio calculation
15. Implement minimum gap validation
16. Implement note rejection with user alert

### Phase 5: Integration
17. Create `LayoutEngine` that orchestrates all calculators
18. Create `LayoutResult` output structure
19. Update rendering code to consume `LayoutResult`

### Phase 6: Lyric Rendering
20. Update lyric rendering for extenders vs hyphens (Gould/Ross)

### Phase 7: Cleanup
21. Remove old layout code (XPositionCalculator, layout strategies, etc.)
22. Remove X-range filtering from LayoutAccumulator
23. Remove placement property from Attachment

---

## Files to Create

```
src/main/java/songscribe/ui/layout2/
├── NoteColumn.java              // Data class for column
├── NoteColumnBuilder.java       // Builds columns from notes
├── HorizontalSpacingCalculator.java
├── VerticalStackingCalculator.java
├── LineJustificationCalculator.java  // Compression to fit margin
├── LayoutEngine.java            // Orchestrator
├── LayoutResult.java            // Output structure
└── LayoutConstants.java         // All spacing values
```

## Files to Modify

- Rendering code: consume LayoutResult instead of old layout
- Lyric rendering: extenders vs hyphens
- Attachment: remove placement property

## Files to Delete (after migration)

- Old layout strategies
- XPositionCalculator
- LayoutAccumulator X-range filtering methods
- Old layout engine

---

## Verification Plan

1. **Visual comparison**: Render same scores before/after, verify improvements
2. **Lyric spacing**: Verify lyrics drive horizontal spacing (uneven is correct)
3. **Accidental clearance**: Verify 0.25 MU minimum gap
4. **Vertical stacking**: Verify correct layer order and margins
5. **Attachment positioning**: Verify tempo, fermata, etc. positioned above notes
6. **Lyric rendering**: Verify extenders + single hyphen (not multiple hyphens)
7. **Line justification**: Verify notes compress when approaching margin
8. **Note rejection**: Verify alert when note cannot fit while maintaining minimum spacing

---

## Resolved Design Decisions

### Beamed Notes (from Gould/Ross)
- Beams define rhythmic grouping, treated as single visual unit for spacing
- Internal spacing within beam group: tight and regular **unless lyrics force expansion**
- Lyric readability takes precedence: beam group may widen for syllable width
- Avoid "fracturing" beam group visually (no extreme internal gaps unless required by text)
- Beaming removes flags → more efficient spacing than isolated flagged notes
- Clear separation between adjacent beam groups

### Rests
- **Create full spacing columns** (same as notes)
- Participate fully in horizontal spacing
- Should not be compressed to tighten lyrics

### Grace Notes (from Gould/Ross)
- **Non-metrical**: do NOT create independent spacing columns
- Anchored to the following main note, borrow space from its left
- Must never push earlier notes leftward
- Visually subordinate and close to main note
- Lyrics align only to main note, never to grace notes
- Implementation: Grace notes are part of the main note's column, extending leftward

### Glissandi (from Gould/Ross)
- Connective graphics, not rhythmic events
- Do NOT create spacing columns
- Adapt to existing spacing; spacing should not be altered for them
- Vertical repositioning or shortening preferred over widening note spacing

### Breath Marks (from Gould/Ross)
- Non-metrical phrasing symbols attached to rhythmic position
- Participate in spacing lightly (minimal horizontal extent)
- May justify slight space *after* the note but must never compress lyrics
- Prefer vertical clearance over horizontal distortion

### Line Justification
- When notes would exceed staff right margin, **compress** spacing uniformly
- Compression must respect minimum gaps (MIN_COLUMN_GAP, MIN_SYLLABLE_GAP)
- If compression cannot achieve valid spacing, **reject the note** with alert
- Do NOT expand to fill margin (that would violate Gould/Ross non-proportional rule)
- Only compress, never expand for justification

---

## Open Questions

None - all design decisions resolved.

**Note**: `MIN_SYLLABLE_GAP` will be determined empirically during implementation, starting with 0.5 MU and adjusting based on visual testing.
