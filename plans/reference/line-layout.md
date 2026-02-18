# Line Layout Rules

This document captures layout rules for elements above and below staff lines, built up from concrete examples.

## Terminology

- **Note**: A pitched musical symbol on the staff
- **Articulation**: Performance instruction attached to a note (staccato, accent, tenuto, etc.)
- **Range Element**: Spans multiple notes (beam, tie, slur, tuplet, trill, glissando, dynamics hairpin)
- **Attachment**: Standalone element associated with a note or position (tempo, fermata, annotation)

## Full Note Bounding Box

The **full bounding box** of a note includes:
- Note head
- Stem
- Flag (for eighth notes and shorter)
- Ledger lines
- Articulations

Attachments use the full bounding box when detecting collisions with notes.

## Layout Order

Elements are laid out in a specific order. Each layer builds on the previous layer's bounding box:

**Above staff** (bottom to top):
1. **Note** (head, stem, flag, ledger lines)
2. **Articulations** (staccato, then accent) — stacked opposite the stem
3. **Trill** — range element, above full note bounding box
4. **Fermata**
5. **Dynamics** (non-range and range)
6. **First/second endings** — bracket can contain elements nested inside
7. **Tempo/beat change**
8. **Text annotations**
9. **Attribution** — topmost layer, attached to line end

**Below staff**:
10. **Lyrics** — ascent 2.5 MU below lowest note bounding box

**Note on annotations**: All annotations must be above the staff. When reading a document, any annotation placed below the staff should be migrated above the staff according to the stacking order above.

Example stacking (stem up, all elements present):
```
                        — J. Smith   ← 9. Attribution (topmost, at line end)
    rit.                             ← 8. Text annotation
    ♩ = 80                           ← 7. Tempo
    ┌─1.────                         ← 6. First/second ending bracket
    │  p────<                        ← 5. Dynamics (inside bracket)
    │  ⌢                             ← 4. Fermata
    │  tr                            ← 3. Trill
    │  >                             ← 2. Accent (articulation)
    │  ·                             ← 2. Staccato (articulation)
    │  ●                             ← 1. Note head
    │  |                                Stem
```

Each element's position depends on the cumulative bounding box of everything below it.

## Examples

### Example 1: First note horizontal position

**Scenario**: First note on a line, with or without key signature

**Rule**: The first note is positioned 11.5 MU from the right extent of the clef (if no key signature) or key signature (if present).

```
[Clef] or [Clef][KeySig] ---> 11.5 MU ---> [First Note]
```

### Example 2: Note Y position coordinate system

**Scenario**: Vertical positioning of notes on the staff

**Rule**: The middle staff line (B4 in treble clef) is Y position 0. Negative values are above (higher pitches), positive values are below (lower pitches). Each integer step is one staff position (line or space).

```
D5 = -2  (space above staff)
C5 = -1  (ledger line above staff)
B4 =  0  (middle line) ← ORIGIN
A4 = +1  (space below middle)
G4 = +2  (second line from top)
F4 = +3  (space)
E4 = +4  (second line from bottom)
D4 = +5  (space)
C4 = +6  (bottom line)
```

Note: Even yPos values (0, ±2, ±4, ±6...) are on staff **lines**. Odd values (±1, ±3, ±5...) are on **spaces**.

### Example 3: Staccato placement

**Scenario**: Note with staccato articulation

**Rules**:
1. Staccato is placed on the **opposite side** of the note head from the stem
2. Distance from note head center depends on note position:

```
if (|yPos| % 2 == 0) AND (|yPos| <= 2):
    # Note is on a line within inner staff (yPos ∈ {-2, 0, +2})
    # Extra clearance needed to avoid visual merge with staff line
    distance = 1.5 × staffLineYOffset
else:
    # Note is on a space, or on outer staff lines
    distance = 1.0 × staffLineYOffset
```

**Rationale**: Notes on the three inner staff lines (G4, B4, D5) need extra clearance so the staccato dot doesn't visually merge with the staff line. Notes on spaces or outer lines have more visual room.

**Visual reference** (stem-down notes, staccato below):
```
Line D5 (yPos=-2): ●     ← 1.5× offset (on inner line)
                    ·
Space C5 (yPos=-1): ●·   ← 1.0× offset (on space)
Line B4 (yPos=0):   ●    ← 1.5× offset (middle line)
                    ·
Space A4 (yPos=+1): ●·   ← 1.0× offset (on space)
Line G4 (yPos=+2):  ●    ← 1.5× offset (on inner line)
                    ·
```

### Example 4: Accent placement

**Scenario**: Note with accent articulation, with or without staccato

**Rules**:

**Accent alone (no staccato):**
1. Accent is placed on the **opposite side** of the note head from the stem
2. Reference point depends on note position:

```
if |yPos| <= 4:
    # Note is within the staff (lines at ±4, ±2, 0)
    # Accent anchors to staff edge, not note
    accentY = staffEdge + 0.5 MU (outward)
else:
    # Note is outside the staff (ledger lines)
    # Accent anchors to note head
    accentY = noteHeadEdge + 0.5 MU (outward)
```

**Accent with staccato:**
- Accent is placed 0.5 MU from the staccato (outward, away from stem)

**Accent margin**: 1 MU all around

**Key insight**: For notes within the staff, accents align to a consistent distance from the staff edge, creating visual uniformity. Only notes on ledger lines anchor the accent to the note itself.

### Example 5: Tempo marking placement

**Scenario**: Tempo marking (e.g., ♩ = 80) above the staff

**Rules**:

1. **Margin box**: Tempo has a 3 MU margin on left, right, and bottom
   ```
   +------------------+
   |     ♩ = 80       |  ← content
   +------------------+
   |    3 MU margin   |  ← bottom margin
   ```

2. **Vertical collision**: Any element below the tempo that would intersect the margin bounds **pushes the staff down** to maintain the margin

3. **Horizontal overlap allowed**: Elements to the left or right of a tempo may occupy the same Y position, as long as they remain outside the tempo's margin bounds (including the 3 MU left/right margins)

4. **Collision detection**: Uses the full note bounding box

**Visual examples**:
```
         +--[tempo margin]--+
         |    ♩ = 80        |
         +------------------+
    ♪                    ♪♪♪   ← High notes can overlap Y level
  __|__                __|__     if outside horizontal margins
═══════════════════════════════  ← Staff pushed down if needed
```

**Key insight**: Tempo uses a margin-box model. Vertical collisions expand the line height; horizontal neighbors can coexist at the same Y level if they respect the left/right margins.

**Note**: Beat change (time signature change) follows the same rules as tempo.

### Example 6: Fermata placement

**Scenario**: Note with fermata

**Rules**:

1. **Position**: Always above the staff (never below)
2. **Margin**: 1 MU all around
3. **Collision detection**: Uses the full note bounding box

The fermata is placed 1 MU above the topmost element of the note's bounding box (or above the trill, if present).

### Example 7: Trill placement

**Scenario**: Note or range of notes with trill

**Rules**:

1. **Position**: Always above the staff (same as fermata)
2. **Margin**: 1 MU all around (same as fermata)
3. **Collision detection**: Uses the full note bounding box
4. **Range element**: Trills can span multiple notes. The trill maintains its margin across **all notes in the range**, positioning itself above the highest bounding box in the span.

**Note**: Fermatas stack above trills in the layout order.

### Example 8: Dynamics placement

**Non-range dynamics** (f, p, mf, etc.):
- Same rules as fermata (1 MU margin all around, above staff)
- Stack above fermatas in the layout order

**Range dynamics** (crescendo, diminuendo hairpins):
1. **Position**: Always above the staff
2. **Margin**: 2 MU top/bottom
3. **Range element**: Like trills, maintains margin across all notes in the span
4. **Collision detection**: Uses the full note bounding box
5. **Stack above fermatas** in the layout order
6. **Coordination with non-range dynamics**: If a range dynamic has a non-range dynamic (e.g., "p") at the beginning or end of the range, the hairpin endpoint is drawn just outside the margin of the non-range dynamic

```
Example: "p" with diminuendo starting at same note

    p─────<          ← Hairpin starts just outside "p" margin
    ⌢                ← Fermata
    >                ← Accent
    ·                ← Staccato
    ●                ← Note
```

### Example 9: First/second endings

**Scenario**: Repeat brackets (1st/2nd endings)

**Rules**:

1. **Margin**: 2 MU (same as range dynamics)
2. **Stacking**: Below tempo/beat changes and text annotations (rit., Fine, etc.)
3. **Bounding area, not bounding box**: Elements can nest inside the bracket — the bracket only needs clearance above its top line
4. **Treated as single element**: First and second endings move together as a unit

```
Example:
    rit.
    ♩ = 80
    ┌─1.───────┬─2.───────┐   ← Endings below tempo/annotations
    │  ♪       │     ♪    │   ← Notes can nest inside bracket
    ══════════════════════════
```

### Example 10: Text annotations

**Scenario**: Performance instructions (rit., Fine, accel., D.C., etc.)

**Rules**:

1. **Position**: Above tempo/beat change
2. **Margin**: 1 MU all around

### Example 12: Attribution

**Scenario**: Composer/lyricist attribution at end of line

**Rules**:

1. **Position**: Top of the stack (above everything else, including text annotations)
2. **Attachment**: Attached to the line itself, drawn at the end
3. **Margin**: Left 3 MU, Bottom 3 MU, Right 0

```
Example:
    rit.                Fine   ← Text annotations (topmost)
    ♩ = 80
    ┌─1.───────┬─2.─────────┐
    ══════════════════════════
```

### Example 11: Lyrics

**Scenario**: Syllables below the staff

**Rules**:

1. **Position**: Below the staff
2. **Spacing**: Ascent (top) of lyrics is 2.5 MU below the lowest full note bounding box on the staff

```
    ●     ●     ●     ●      ← Notes
    |     |     |     |
═══════════════════════════  ← Staff
    |                        ← Stem (if down)
    ↓ 2.5 MU
   This   is   the   text    ← Lyrics (ascent aligned)
```

---

## Derived Rules

<!-- After collecting examples, organize rules here by category -->

### Above Staff

### Below Staff

### Collision Handling

### Stacking Order

---

## Open Questions

<!-- Capture uncertainties and edge cases to resolve -->
