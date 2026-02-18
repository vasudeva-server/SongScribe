# Gould / Ross Engraving Standard

## Vocal-Only, Single-Staff, Lyrics Below

### (Professional Consolidation)

This document consolidates engraving rules derived from:

- Elaine Gould -- *Behind Bars*
- Ted Ross -- *The Art of Music Engraving and Processing*

Adapted specifically for:

- Single melodic staff
- Vocal music
- Lyrics below the staff
- Meterless or lightly structured rhythm
- Occasional articulations, tuplets, and expressive markings

Clarity, proportional balance, and lyric readability govern all
decisions.

------------------------------------------------------------------------

# 1. Hierarchy of Visual Priority

1. Lyrics
2. Fermatas / expressive markings
3. Dynamics and hairpins
4. Articulations
5. Beams and stems
6. Note spacing refinement

If conflicts arise, preserve higher-priority elements.

------------------------------------------------------------------------

# 2. Horizontal Spacing

## 2.1 Note Columns

Each note or rest creates a rhythmic column.

Columns must:

- Align vertically through the system.
- Expand to accommodate accidentals.
- Expand to accommodate lyrics.

Lyrics may override rhythmic proportional spacing when necessary.

## 2.2 Notes and Rests

- Default spacing is non-proportional.
- Rhythmic value does not determine horizontal distance.
- Spacing is governed by glyph extents, collision avoidance, and lyric
  needs.
- Longer durations increase stretch potential, not guaranteed width.
- Uneven spacing is expected and correct.
- Rests create columns equivalent to notes of equal duration.

## 2.3 Accidentals and Augmentation Dots

**Accidentals**

- Project leftward from the notehead.
- Increase spacing only when minimum clearance would be violated.
- No fixed reserved space.

**Dots**

- Project rightward.
- Must not collide with following notes or flags.
- Do not justify widening a bar on their own.

## 2.4 Bars and Measures

- Bars do not enforce equal widths.
- Lyrics may legitimately widen or compress measures.
- Never tighten lyrics to preserve visual symmetry.

## 2.5 Lyrics and Syllables

- One syllable aligns with one notehead.
- Syllables must never crowd.
- Optical clarity outweighs rhythmic proportionality.

**Hyphens vs. Extenders**

- Hyphen = syllable division only.
- Extender = duration only.
- Never repeat hyphens to show duration.
- Two-syllable words always use exactly one hyphen.

Example: ka____-re

## 2.6 Beamed Groups

- Treated as rhythmic units.
- Internal spacing proportional but compact.
- Beams define rhythmic *grouping* and are treated as a single visual
  unit for spacing.
- Internal spacing within a beam group should remain as tight and
  regular as possible, **unless lyrics force expansion**.
- In vocal music, **lyric readability takes precedence**: if syllable
  width requires it, the beam group may widen by increasing the
  spacing between note columns inside the beam.
- Avoid "fracturing" the beam group visually:
    - do not let one note appear detached from its beam group,
    - do not create extreme internal gaps unless required by long
      text.
- Beaming typically removes flags, which reduces protruding glyph
  geometry; therefore beamed passages may space more efficiently than
  isolated flagged notes.
- Provide clear separation between adjacent beam groups (or between a
  beam group and a rest) so rhythmic grouping remains unambiguous.
- Under lyrics:
    - keep syllables centered under their noteheads,
    - maintain clear minimum gaps between syllables,
    - prefer widening the beam group over compressing text.

## 2.7 Grace Notes

- Do not create full rhythmic columns.
- Must not disrupt lyric alignment.

------------------------------------------------------------------------

# 3. Stem Direction

In single-voice vocal music:

- Notes on or above middle line: stems down.
- Notes below middle line: stems up.
- Avoid frequent flipping within short spans.

Consistency preferred over strict rule enforcement in ambiguous cases.

------------------------------------------------------------------------

# 4. Beam Rendering Rules

### Stem–Beam Connection

- Conceptually (engraving geometry), **stem length is measured to the beam**: the end of the stem defines where the primary beam sits.
- Visually, this reads as the beam being **nested into the stem**: the beam overlays the terminal portion of the stem so the join is clean and the stem does not appear to “poke through” above the outside edge of the beam stack.
- Implementation model that matches Gould/Ross practice:
  - Compute the **beam line** (slope + vertical position).
  - Set stem lengths so stems reach the **beam attachment boundary** (for a multi-beam group, this effectively means reaching to where the outermost beam must attach).
  - Render with a small intentional **overlap** (beam drawn over stem) so the connection is solid under rasterization/anti-aliasing.
- Do **not** treat the beam as something that sits “above” an already-final stem length; and do **not** leave visible stem beyond the outside edge of the beam stack.

### Beam Stack Geometry

- The **primary beam** defines the reference line (its slope and position). Secondary beams are placed at a fixed spacing from the primary beam.
- Secondary beams should be **parallel** to the primary beam (same slope).
- Beam corners are **square/flat** (not tapered) unless your house style explicitly calls for tapering.

### Beam Thickness and Inter-beam Spacing

- Use the font’s **SMuFL `engravingDefaults`** where available:
  - `beamThickness` (beam stroke/rectangle thickness)
  - `beamSpacing` (distance between beams in a stack)
- These SMuFL defaults are the recommended source of truth for beam widths in a font-aware system; if you already have SMuFL values, use those directly.

### Practical Checks

- Ensure the stem/beam join does **not** leave a hairline gap at common rasterization scales (100%–200%).
- Keep beam thickness visually balanced relative to staff-line and stem thickness; avoid “chunky” beams in lyric-dense vocal layouts.

------------------------------------------------------------------------

## 4.1 Minimum Stem Length in Beamed Groups (Vocal Standard)

- Absolute minimum stem length: 3.5 staff spaces.
- Shortest stem within a beamed group must not fall below this threshold.
- If slope causes undersized inner stems:
  - Reduce beam slope.
  - Or shift beam vertically.
  - Prefer horizontal beam before violating minimum stem rule.

In lyric-centered engraving, avoid visually stubby inner stems.
Beam moderation overrides literal contour-following.

------------------------------------------------------------------------

## 4.2 Numeric Beam Geometry Specification (Renderer Implementation)

All measurements expressed in staff spaces.

### Constants

MIN_STEM = 3.5
PREFERRED_STEM = 3.75
MAX_STEM = 4.5

SLOPE_SCALE = 0.6
MAX_SLOPE = 0.75
HARD_MAX_SLOPE = 1.0

### Beam Slope Calculation

rawSlope = (Y2 - Y1)
scaledSlope = rawSlope * SLOPE_SCALE

Clamp to MAX_SLOPE.
Force horizontal if contour reverses or span > sixth.

### Beam Anchoring

Stem-up:
beamY1 = noteTop(Y1) + PREFERRED_STEM
beamY2 = beamY1 + scaledSlope

Stem-down:
beamY1 = noteBottom(Y1) - PREFERRED_STEM
beamY2 = beamY1 + scaledSlope

### Inner Stem Validation

If any required stem < MIN_STEM:

- Reduce slope by 15%.
- Recalculate.
- Repeat until valid or slope = 0.

If violation remains at slope = 0:
Shift entire beam vertically.

Never extend stems artificially to chase slope.

### Maximum Stem Moderation

If any stem > MAX_STEM:

- Reduce slope slightly, or
- Shift beam toward noteheads.

Avoid stems > 4.75 staff spaces in normal vocal context.

### Automatic Horizontal Override

Force horizontal if:

- Dense lyrics within group
- Articulation cluster above
- Hairpin begins or ends inside group
- Repeated inner-stem violation
- Group ≥ 5 notes in vocal context

### Stability Bias

If slope < 0:
    slope *= 0.85

### Visual Targets

- Shortest stem ≈ 3.6–3.8
- Longest stem ≈ 4.2–4.4
- Beam deviation rarely > 0.6
- Horizontal beams common in calm vocal engraving

------------------------------------------------------------------------

# 5. Beam Angle Rules

## 5.1 Governing Philosophy

Beam angle should:

- Suggest contour
- Remain moderate
- Avoid steep slopes
- Preserve stem balance
- Protect lyric clarity

## 5.2 Default Beam Behavior

- Scale melodic slope to 60% of actual contour.
- Clamp deviation to ≤ 0.75 staff spaces.
- Absolute maximum: 1 staff space (rare).

If uncertain, flatten.

## 5.3 When to Use Horizontal Beams

Force horizontal beams when:

- Contour reverses
- Pitch span exceeds a sixth
- Lyrics are dense
- Articulations cluster above
- Slope exceeds 0.75 staff spaces
- Music is declamatory or calm

Horizontal beams are often preferable in vocal engraving.

## 5.4 Stem Length Balance

Beam placement must avoid:

- Extremely short stems
- Excessively long stems

Balanced stems override literal pitch slope.

------------------------------------------------------------------------

# 6. Vertical Spacing

## 6.1 Articulations

**Placement**

For vocal-only engraving:

- Accents: Above staff
- Staccato alone: Above staff
- Accent + staccato: Cluster above
- Fermatas: Above staff

Must clear staff lines, stems, beams, and text.

**Interaction with Beams**

- Beam angle must not compress articulation space.
- In articulated passages, prefer flatter beams.

## 6.2 Fermatas

- Placed above the staff.
- Must clear dynamics, tempo text, and endings.
- Prefer vertical expansion to horizontal distortion.

## 6.3 Dynamics and Hairpins

**Placement**

- Place dynamics above staff.
- Place cresc./dim. above staff.

**Range Elements (Hairpins)**

- Hairpins are **range indicators**, not rhythmic events.
- They do not create spacing columns and do not advance time.
- In vocal-only music, hairpins are placed **above the staff**.
- Hairpins should:
    - begin and end clearly under the notes they affect,
    - avoid touching noteheads, stems, beams, or lyrics,
    - remain visually subordinate to tempo and fermata marks.
- Hairpins must **never intrude into lyric space**.
- Maintain steady vertical alignment.
- If space is tight:
    - shorten the hairpin,
    - adjust vertical position,
    - widen spacing only as a last resort.
- Hairpins communicate **dynamic change**, not duration.

**Text Dynamics Across a Range**

- Dynamic text (e.g., *cresc.*, *dim.*) functions like a hairpin.
- Place above the staff in vocal music.
- Do not stretch musical spacing solely to center the text.
- Ensure text remains visually associated with its range.

## 6.4 Breath Marks

- Placed above the staff, after the note.
- Must be visually clear and unambiguous.
- May require slight horizontal space after the note.
- Never compress lyrics to accommodate a breath mark.

## 6.5 First and Second Endings (Volta Brackets)

- Endings are **structural range elements** spanning one or more
  measures.
- They do not participate in rhythmic spacing.
- Use straight horizontal brackets.
- Vertical hooks at both ends.
- Second ending usually closed.
- Align with barline or repeat sign.
- Must clear tempo marks, dynamics, and fermatas.
- Maintain consistent bracket height.
- Vertical expansion is preferred over horizontal distortion.
- Open or closed final brackets are acceptable; clarity overrides
  symmetry.

## 6.6 Tempo and Beat Changes

- Placed above the staff.
- Aligned with the governing musical event.
- Must remain visually distinct from expressive text.

------------------------------------------------------------------------

# 7. Tuplets

## 7.1 Beamed Tuplets

- Follow standard beam-angle rules.
- No brackets when beam is present (unless clarity demands).

## 7.2 Unbeamed Tuplets

- Use straight brackets.
- Bracket does not dictate beam angle.

Tuplet numbers must remain legible and centered.

------------------------------------------------------------------------

# 8. Trills and Multi-Note Ornaments

- Place above staff.
- Span clearly over affected notes.
- Avoid beam collisions.
- Maintain vertical hierarchy with dynamics.
- Trills spanning multiple notes are **ornamental ranges**, not
  rhythmic events.
- They do not create spacing columns.
- Trill lines or extensions should:
    - align clearly with the notes they modify,
    - remain visually continuous across the span,
    - avoid collisions with stems, beams, and lyrics.
- In vocal music:
    - trills should remain light and subordinate,
    - never obscure text or phrasing marks.
- If space is limited:
    - shorten or raise the trill line,
    - avoid widening note spacing unless absolutely necessary.

------------------------------------------------------------------------

## 6.7 Above-Note Vertical Hierarchy (Vocal, Meterless, Melody-Only)

**Scope** - Single staff - Vocal - Lyrics below - Meterless or lightly
structured rhythm - No slurs (ties permitted) - Articulations placed
above - Dynamics placed above

### Governing Logic

Vertical order is determined by:

1. Structural hierarchy
2. Duration hierarchy (spanners vs note-attached symbols)
3. Optical clarity

Clarity overrides rigid stacking when conflicts arise.

------------------------------------------------------------------------

### Vertical Stacking Order (Top → Down Toward Notehead)

1. **Structural Elements**

    - Tempo indications
    - First/second endings
    - D.C., D.S., al Fine, etc.

2. **Tuplet Brackets and Numbers** Highest local rhythmic grouping
   layer.

3. **Range Spanners**

    - Hairpins
    - cresc./dim. text
    - 8va lines
    - Trill extension lines

   If both hairpin and trill extension occur, hairpin sits above the
   trill line.

4. **Instantaneous Dynamics**

    - p, f, mf, sfz, etc.

   Dynamics sit below spanners but above fermatas and articulations.

5. **Note-Attached Fermata**

6. **Articulations (Clustered)** Ordered by visual weight:

    - Accent
    - Tenuto
    - Staccatissimo
    - Staccato

7. **Ornament Glyphs**

    - Trill sign ("tr")

   Ornaments sit closer to the note than articulations in strict
   hierarchy.
   Minor optical adjustment is permitted for clarity.

8. **Tie** Closest element to the notehead.
   Nothing may intervene between tie and note.

9. **Notehead**

------------------------------------------------------------------------

### Compact Reference Stack

Structural
→ Tuplet
→ Spanners
→ Dynamic
→ Fermata
→ Articulations
→ Ornament glyph
→ Tie
→ Notehead

------------------------------------------------------------------------

### Conflict Resolution

1. Preserve tie proximity to notehead.
2. Preserve articulation readability.
3. Preserve dynamic legibility.
4. Spanners must not collide with articulation clusters.
5. Shift ornament glyphs before altering articulation clarity.
6. Structural and tuplet elements remain highest.

# 9. Meterless Engraving Adjustments

In meterless vocal music:

- Favor conservative beam slopes.
- Favor horizontal beams when ambiguous.
- Maintain generous lyric spacing.
- Avoid visual angular drama.

Stability over geometry.

------------------------------------------------------------------------

# 10. Implementation Defaults (Recommended Preset)

Slope scaling: 0.6 Max beam deviation: 0.75 staff spaces Horizontal
threshold: mixed contour Downward slope penalty: -15% Stem balance
priority: High Lyric collision override: Enabled Articulation
clustering: Enabled Dynamic placement: Above staff Tuplet bracket style:
Straight

------------------------------------------------------------------------

# 11. Global Principles

1. Lyrics dominate spacing decisions.
2. Readability beats symmetry.
3. Duration is never encoded in text.
4. Uneven spacing is normal.
5. Everything yields to the singer's eye.
6. Range elements adapt to spacing; spacing does not adapt to range
   elements.

------------------------------------------------------------------------

# 12. Visual Target

Your engraving should appear:

- Calm
- Balanced
- Proportional
- Text-centered
- Structurally deliberate

The notation must serve the sung text first.

------------------------------------------------------------------------

End of Gould / Ross Engraving Standard.