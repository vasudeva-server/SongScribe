# Line Layout Rules

This document captures layout rules for elements above and below staff lines, built up from concrete examples and verified against the current implementation (`songscribe.layout.stacking` package: `VerticalStackingCalculator`, `NoteAttachedStacker`, `StructuralStacker`, `SystemStacker`, `StackingUtils`).

## Units

All distances below are in **staff-spaces** (`ss` — the `Ss` suffix in code), where `1.0 ss` is the distance between two adjacent staff lines. This is the project's standard spatial unit; see [Unit Conversion](unit-conversion.md). (An earlier draft of this document used "MU" for this unit — the codebase has no such term, so all values have been re-expressed in `ss` and, where a named constant exists, cross-referenced to it.)

## Terminology

- **Note**: A pitched musical symbol on the staff
- **Articulation**: Performance instruction attached to a note (staccato, accent, tenuto, etc.)
- **Range Element**: Spans multiple notes (beam, tie, slur, tuplet, trill, glissando, dynamics hairpin)
- **Attachment**: Standalone element associated with a note or position (tempo, fermata, annotation)

## Full Note Bounding Box

There is no single precomputed "full bounding box" object that later elements collide against. Instead, `NoteAttachedStacker.seedNoteBounds()` seeds each note's head+stem extent (from the beam/stem pass's `StemLayout` when available, otherwise from `ElementType` metrics) into a shared `StaffExtents` skyline, and `seedTieBounds()` seeds the tie arc next. Every element that stacks afterward — articulations, tuplet brackets, fermata, trill, dynamics, endings, tempo, annotations, attribution — reads and writes that same cumulative skyline, so each successive layer automatically clears everything reserved by earlier layers within its horizontal footprint. This is a **cumulative skyline** model, not a fixed union box: ledger lines do not add extra vertical extent beyond the note's own bounds, and articulations are folded in only because they wrote their own reservation into the same skyline before later elements queried it.

Attachments therefore collide against "whatever is currently reserved in this column," which in practice behaves like a full bounding box once articulations have been stacked — but nothing computes that box explicitly.

## Layout Order

Elements are laid out in tiers. Each tier's stacking pass reads and writes into a `StaffExtents` skyline that already reflects every earlier tier, so later tiers automatically clear earlier ones. This is the order implemented by `VerticalStackingCalculator.calculate()`:

**Above staff** (nearest-staff tier first):
1. **Note** (head, stem) — seeded by `seedNoteBounds()`; tie arcs seeded next by `seedTieBounds()`
2. **Articulations** (staccato, then accent) — stacked opposite the stem
3. **Tuplet brackets** (if present) — derive their ceiling from note tips and the articulations above, rather than querying the skyline; other tiers clear the bracket's reserved footprint
4. **Fermata** — stacked per note column
5. **Trill** — a span, stacked per line *after* fermata, so it clears whatever fermata already reserved
6. **Dynamics**: hairpins (crescendo/diminuendo) first, then text dynamics (p, f, mf, …)
7. **First/second endings** — bracket can contain elements nested inside
8. **Tempo**, then **beat change** — same mechanism, separate margin constants
9. **Text annotations**
10. **Attribution** — topmost layer, attached to the line end (first line only)

**Below staff**:
11. **Lyrics** — baseline computed independently of the above-staff skyline (see Example 11)

**Note on annotations**: All annotations must be above the staff — `SystemStacker.stackAnnotations()` always calls the above-staff stacking path; there is no below-staff branch. When reading a document, any annotation placed below the staff should be migrated above the staff according to the stacking order above.

Example stacking (stem up, all elements present):
```
                        — J. Smith   ← 10. Attribution (topmost, at line end)
    rit.                             ← 9. Text annotation
    ♩ = 80                           ← 8. Tempo
    ┌─1.────                         ← 7. First/second ending bracket
    │  p────<                        ← 6. Dynamics (inside bracket)
    │  tr                            ← 5. Trill
    │  ⌢                             ← 4. Fermata
    │  >                             ← 2. Accent (articulation)
    │  ·                             ← 2. Staccato (articulation)
    │  ●                             ← 1. Note head
    │  |                                Stem
```

Each element's position depends on the cumulative skyline of everything stacked before it.

## Examples

### Example 1: First note horizontal position

**Scenario**: First note on a line, with or without a key signature in the header

**Which key the header draws**: the line's **running** key (`Line.getRunningKey()`) — its own key when it establishes one, otherwise the key it inherits from the line before it. Every line draws its key signature, whether it established that key or inherited it, so "no key of its own" is not "no key signature". A line whose running key is C major draws no accidentals and is the no-key-signature case below.

**Rule**: LilyPond measures the first note from a different edge depending on what it follows (`HorizontalSpacingCalculator.calculateFirstElementXSs`):

- **With a key signature** — the note sits `StaffHeaderMetrics.KEY_SIGNATURE_FIRST_NOTE_GAP_SS` past the key signature's right edge.
- **Without one** — the span from the clef's *left* edge has a floor of `StaffHeaderMetrics.CLEF_FIRST_NOTE_SPAN_SS`, which the clef's own width does not fill, so the clef's width drops out of the answer.

```
[Clef] ------ CLEF_FIRST_NOTE_SPAN_SS ------> [First Note]
[Clef][KeySig] -- KEY_SIGNATURE_FIRST_NOTE_GAP_SS --> [First Note]
```

### Example 1a: Mid-line key signature

**Scenario**: A key change written into the middle of a line (`KeySignatureElement`)

A key signature is not only a header fixture. A `KeySignatureElement` is an ordinary element in the line's element list and is placed by the same spring chain as every other column, subject to its position invariant: never at index 0, always immediately after a barline or repeat.

**Rules**:

1. **Width**: the column's right extent is the change's *drawn* width — the accidentals `KeyChange` lays out between the key in effect immediately before the element and the element's own key — not a per-type constant. A change that cancels the previous signature is wider than one that does not, and the spacing reflects that.
2. **Minimum spacing**: `HorizontalSpacingCalculator.calculateMinimumColumnSpacingSs` gives it the same promise it gives every column — `MIN_COLUMN_GAP_SS` of clear space between facing ink on each side. The barline before it clears the first accidental by that gap; the last accidental clears the following note's leftmost ink (its accidental when it has one) by the same.

```
[Barline] -- MIN_COLUMN_GAP_SS --> [♮♮♮ ♯♯] -- MIN_COLUMN_GAP_SS --> [Note]
             cancellation naturals ─┘   └─ new signature
```

### Example 1b: Cautionary key signature at the end of a line

**Scenario**: The next line begins in a different key

**Rule**: A cautionary key signature is drawn in the trailing space at the end of the line, warning the performer what the next line starts in. Layout reserves room for it in `HorizontalSpacingCalculator.trailingReservationSs`: the trailing gap past the last column becomes the larger of the line rest and the cautionary's width plus `KeyChange.RIGHT_MARGIN_SS`. The larger, not the sum — the cautionary is drawn *into* the trailing gap, not after it.

The keys compared are the **running** keys on each side of the boundary: `Line.keyAtEndOfLine()` (which accounts for a mid-line change) against `Line.nextLineRunningKey()`. A null answer from the latter — the song's last line — means there is nothing to warn about, so nothing is drawn and nothing is reserved.

```
[... notes ...] --- max(line rest, cautionary + RIGHT_MARGIN_SS) --> | staff right margin
                                   [♭♭♭] ─┘
```

### Example 1c: What a key change costs, and where it is checked

A key change is not local to the line it is made on. It claims horizontal space in four places, and `songscribe.layout.KeyEditFitCalculator` measures all four before the editor accepts the edit:

1. the **cautionary** at the end of the line before it (Example 1b);
2. the **header** of every line the change re-keys (Example 1);
3. the **cautionary at the end of each of those lines**, since the key they leave off in moves with them;
4. for a mid-line change, the **key signature column** itself (Example 1a), plus the barline the editor inserts when the chosen position does not already open a measure.

"Every line the change re-keys" is the inheritance chain: a line with no key of its own inherits from the line before it, so a change propagates forward and stops at the first line that establishes its own key — the same rule `Song`'s inherited-key propagation follows.

Each of those lines is measured by the identical `HorizontalSpacingCalculator.solveLine` the committed layout runs, over columns built by the same `ElementColumnBuilder`. The keys a solve reads off a line travel together in `songscribe.layout.LineKeys` — header key, key at end of line, next line's running key — so a pre-check states the keys the edit would produce rather than reading some from the edit and the rest from the unedited document.

### Example 2: Note Y position coordinate system

**Scenario**: Vertical positioning of notes on the staff

**Rule**: `StaffElement.staffPosition` is an integer diatonic-step index. The middle staff line (B4 in treble clef) is position 0. Negative values are above (higher pitches), positive values are below (lower pitches). Each integer step is one staff position (line or space) and equals `Staff.STAFF_POSITION_OFFSET_SS` = `0.5 ss`; a note's actual Y in staff-spaces is `staffPosition × 0.5`. The top staff line sits at position `-4` (`StackingUtils.TOP_STAFF_LINE_POSITION`), the bottom staff line at position `+4` (`StackingUtils.BOTTOM_STAFF_LINE_POSITION`).

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

Note: Even `staffPosition` values (0, ±2, ±4, ±6...) are on staff **lines** (`StaffElement.isLinePosition()`). Odd values (±1, ±3, ±5...) are on **spaces**.

### Example 3: Staccato placement

**Scenario**: Note with staccato articulation

**Rules**:
1. Staccato is placed on the **opposite side** of the note head from the stem
2. Distance from note head center depends on note position:

```
if strictly inside the staff (top staff line < staffPosition < bottom staff line, i.e. between -4 and +4):
    if staffPosition is a line position (even):
        distance = STACCATO_ON_LINE_DISTANCE_SS       # 1.5 ss
    else (a space position, odd):
        distance = STACCATO_BETWEEN_LINES_DISTANCE_SS  # 1.0 ss
else:
    # Note is on or beyond the top/bottom staff line (including ledger lines)
    # Uses the same edge/margin-anchored formula as fermata/trill/etc.,
    # not the 1.0/1.5 distances above
```

**Rationale**: Any note on an interior staff line (not just the three closest to the middle line) needs extra clearance so the staccato dot doesn't visually merge with the staff line; notes on interior spaces have more visual room. Both constants live in `StackingUtils` (`STACCATO_ON_LINE_DISTANCE_SS = 1.5`, `STACCATO_BETWEEN_LINES_DISTANCE_SS = 1.0`). Notes at or beyond the staff edge are edge-anchored with a margin instead — the dot's outer *outline* (not its bounding box) is what gets reserved and cleared, via `ShapeProfile.outerEdge`.

**Visual reference** (stem-down notes, staccato below):
```
Line D5 (yPos=-2): ●     ← 1.5 ss (interior line)
                    ·
Space C5 (yPos=-1): ●·   ← 1.0 ss (interior space)
Line B4 (yPos=0):   ●    ← 1.5 ss (middle line)
                    ·
Space A4 (yPos=+1): ●·   ← 1.0 ss (interior space)
Line G4 (yPos=+2):  ●    ← 1.5 ss (interior line)
                    ·
```

### Example 4: Accent placement

**Scenario**: Note with accent articulation, with or without staccato

**Rules**:

1. Accent is placed on the **opposite side** of the note head from the stem (`NoteAttachedStacker.articulationDirection()` — literally `note.getDirection().opposite()`).
2. The accent is placed via generic collision stacking (`StackingUtils.placeAndReserveClamped`), not a hardcoded `|yPos|` branch. Its inner (staff-facing) edge is the **more outward** of:
   - whatever is already reserved directly beneath it in that column (the staccato dot if present, else the tie arc, else the notehead — each queried through its actual outline, not a flat box), padded by `ACCENT_PADDING_SS` = `0.20 ss`; or
   - the staff line's ink edge, padded by a floor of `max(ACCENT_PADDING_SS, SCRIPT_STAFF_PADDING_SS)` = `max(0.20, 0.25)` = `0.25 ss` (`NoteAttachedStacker.scriptStaffPaddingSs()`).

   In practice this produces staff-edge anchoring for notes within or near the staff, and notehead/staccato anchoring for notes far out on ledger lines — the same effective behavior as an explicit `|yPos|` threshold, but derived from the skyline rather than special-cased.
3. **Accent with staccato**: the accent clears the staccato's outline by `ACCENT_PADDING_SS` = `0.20 ss` (not a separate rule — this falls out of rule 2, since the staccato is the nearest reserved neighbor).
4. There is no isotropic "accent margin all around" — the accent has no bottom/left/right margin constant; only the outward-side padding/clamp above applies.

**Key insight**: Accent placement is one instance of the same generic skyline-collision algorithm used everywhere else in this document, not a position-dependent special case. The staff-edge-anchoring behavior for notes within the staff is an emergent property of clamping against the staff's ink edge.

### Example 5: Tempo marking placement

**Scenario**: Tempo marking (e.g., ♩ = 80) above the staff

**Rules**:

1. **Margin**: Tempo keeps `TEMPO_MARGIN_SS` = `1.0 ss` above whatever is already reserved in its column (`SystemStacker.stackTempo` → `stackMetronomeAttachment` → `StackingUtils.stackAboveWithRegions`). This is a single ceiling-anchored margin, not a margin box on multiple sides.
2. **Vertical collision**: Any element already stacked (in an earlier tier) within the tempo's X range pushes the tempo — and therefore the line's overall height — outward to maintain the margin.
3. **Horizontal independence**: Elements at other X positions do not collide with the tempo at all — this is a natural consequence of the skyline being indexed by X step, not a tempo-specific "allowed overlap within margin bounds" rule.
4. **Collision detection**: Uses the cumulative skyline described in [Full Note Bounding Box](#full-note-bounding-box).

**Note**: Beat change (time signature change) follows the same mechanism, with its own constant `BEAT_CHANGE_MARGIN_SS` = `1.0 ss`, and stacks immediately after tempo in the same tier (so beat change clears tempo where they overlap).

### Example 6: Fermata placement

**Scenario**: Note with fermata

**Rules**:

1. **Position**: Always above the staff (never below)
2. **Margin**: `FERMATA_PADDING_SS` = `0.466 ss` (`NoteAttachedStacker`), with a staff-edge clamp floor of `max(FERMATA_PADDING_SS, SCRIPT_STAFF_PADDING_SS)` = `0.466 ss`
3. **Collision detection**: Uses the cumulative skyline (a flat box for the fermata's own footprint, even though the glyph is round)

The fermata is placed `FERMATA_PADDING_SS` above whatever is already reserved in its column.

### Example 7: Trill placement

**Scenario**: Note or range of notes with trill

**Rules**:

1. **Position**: Always above the staff (same as fermata)
2. **Margin**: `TRILL_SCRIPT_PADDING_SS` = `0.40 ss` at the anchor (first) note, or `TRILL_SPANNER_PADDING_SS` = `0.70 ss` at subsequent notes in a multi-note range — not a single uniform margin
3. **Collision detection**: Uses the cumulative skyline, with the glyph's actual outline (not a flat box) via `ShapeProfile`
4. **Span**: Each note in the trill's range contributes its own required clearance; the whole span seats at the most outward requirement across the range — "positioning itself above the highest bounding box in the span," per the original rule

**Note**: Trills stack *above* (further out than) fermatas — `NoteAttachedStacker.stackOuterScripts()` stacks fermata for every column first, then trills for the line, so a trill always clears whatever fermata already reserved in its columns.

### Example 8: Dynamics placement

**Grouping and shared baseline**:
- Hairpins and text dynamics are grouped together and share one reference line. A text dynamic may sit on any hairpin bound — its anchor element or its end element — or immediately outside it (the element before the anchor, or after the end); either way it joins that hairpin's group. Only the *strict interior* of a hairpin's span is forbidden: the editor enforces this at the UI level via `SpanLookup.isInsideHairpin`, read by `DynamicMarkingAction.updateEnabledState` to disable the dynamic actions inside an existing hairpin's range, and at the mutation level via `MusicEditOperations.stripInteriorPointDynamics`, which strips point dynamics from the strict interior `(anchor, end)` — not the bounds themselves — when a hairpin is added or extended over them. This is an editor invariant, not a layout one: an imported dynamic that is strictly inside a wedge falls through to its own independent group rather than joining the hairpin's. A text dynamic adjacent to no hairpin also keeps its own independent placement. This follows LilyPond's `DynamicLineSpanner` (`lily/dynamic-align-engraver.cc`).

**Alignment within a group**:
- Within a group, the two member types align differently on the shared reference line, as LilyPond does: a hairpin's full height is centred on it (`Hairpin.self-alignment-Y = CENTER`), while a text dynamic's glyph baseline sits `DYNAMIC_TEXT_BASELINE_OFFSET_SS` below it (`DynamicText.Y-offset`, "center on an 'm'") so its x-height centre lands on the line. Centring the glyph's bounding box instead would leave `p`, `f` and `mf` at visibly different heights.

**Endpoint bound padding**:
- The rules below are assignments from the neighboring element's extent, not clamps, so a wedge preceded by a `p` legitimately extends past its anchor column's origin.

```
Hairpin tip placement — first matching rule wins
(HairpinEndpoints.leftEndpointSs / rightEndpointSs; LilyPond hairpin.cc:184-290)

  LEFT tip (anchor side)                  RIGHT tip (end side)
  ────────────────────────────────        ────────────────────────────────
  1. dynamic on the anchor element?       1. dynamic on the end element?
     → advance box right + BOUND_PADDING     → advance box left − BOUND_PADDING
       (hairpin.cc:216-220)                    (hairpin.cc:216-220)
       │ no                                    │ no
  2. a hairpin ends on the anchor?        2. a hairpin starts on the end?
     → notehead center + B2B_PADDING         → notehead center − B2B_PADDING
       (hairpin.cc:222-257)                    (hairpin.cc:222-257)
       │ no                                    │ no
  3. dynamic at anchorIndex − 1?          3. dynamic at endIndex + 1?
     → advance box right + BOUND_PADDING     → advance box left − BOUND_PADDING
       │ no                                    │ no
  4. anchor column origin                 4. end element is a rest?
                                             → rest left edge (hairpin.cc:268-271)
                                               │ no
                                          5. end notehead right edge

Minimum length: widening moves the RIGHT tip and nothing else, so
  · right tip placed by rule 1 or 3 → do not widen; clamp only a negative
    width to zero, as LilyPond does (hairpin.cc:292-298). Widening here
    would drive the wedge back under the glyph the padding just cleared.
  · otherwise → widen to Hairpin.MINIMUM_LENGTH_SS. Safe even when a
    dynamic placed the LEFT tip: widening carries the right tip away from
    that glyph, and a dynamic on the right would have taken the branch
    above.
```

- "Advance box" in rules 1 and 3 is the glyph's **advance width**, not its ink bounding box, and the distinction is worth about half a staff space. The italic dynamic glyphs paint well outside the box the font declares for them — Bravura's `dynamicForte` has an ink box of `[-0.564, +1.456]` against an advance width of `1.456` — so padding from the ink puts the wedge roughly twice as far from the glyph as LilyPond does. LilyPond's `e` is the `DynamicText` grob extent, which is the advance width (Emmentaler's `f` declares `1.468`, near-identical to Bravura's `1.456`), so measuring from the advance box is what reproduces its spacing. `HairpinEndpoints.dynamicAdvanceLeftEdgeSs` recovers that box from the ink left edge by removing the glyph's left side bearing. Note the asymmetry this leaves: the gap tightens only on the side where a given glyph's ink overhangs its box, which for Bravura's dynamics is the left.
- The dynamic **glyph itself** is still centered over the notehead by its **ink** box (`HairpinEndpoints.dynamicLeftEdgeSs`, read by `StructuralStacker`), because that is what looks centered to the eye. Only the wedge's stopping point uses the advance box.
- Rule 2 is the back-to-back case, and it is now **reachable from the editor**: with no dynamic on the shared element, two back-to-back wedges meet at the shared column centre ∓ `Hairpin.BACK_TO_BACK_PADDING_SS`, so their tips do not touch. Rule 1 outranks it — a dynamic on the shared element (as in `<f>`) pulls both wedges back to the glyph's edges instead of stopping at the notehead center. A hairpin ending on a rest stops at the rest's left edge (rule 4). Both are ordinary engraving practice — back-to-back hairpins appear in 25 files of the ABC corpus and rest-bounded ones in two (issue #743) — see [Hairpin Editing Rules](hairpin-editing.md) for the editor decision tree that produces them and for the dynamic-on-a-bound rule; this document covers only their layout geometry.
- LilyPond's remaining bound rule, padding away from a non-musical bound (`Item::is_non_musical`), is **not** implemented. It exists because LilyPond bounds spanners on `NoteColumn`s that may be barlines; a SongScribe bound is always a note, and no hairpin in the corpus is bounded by a barline.

**Reserving room for the pullback**:
- A dynamic on a bound moves that bound's tip inward by roughly two staff spaces, so a hairpin spanning two adjacent notes needs a wider gap between them than the plain `MINIMUM_LENGTH_SS - noteheadWidth`. `HorizontalSpacingCalculator.hairpinReservationFloorSs` reserves that extra distance, which is why an `f<` on a compressed line pushes its two notes apart instead of collapsing to an invisible wedge — the same trade LilyPond makes with its spacing rod. `HairpinEndpoints.dynamicLeftTipOffsetSs` / `dynamicRightTipOffsetSs` state the pullback once, as an offset from the column origin, so the calculator can ask for it before any column has a resolved X; both classes read the same number, and `HorizontalSpacingCalculatorSpringTest` pins that they agree by placing a pair at the reserved gap and measuring the wedge that comes out.
- Three tip rules are deliberately **not** reserved for. A dynamic on the element *outside* the span (rule 3) pulls a tip by an amount that depends on a neighbouring pair's spring, which SongScribe's per-pair strut model cannot see. The back-to-back rule (2) and the rest rule (4) also shorten the wedge, and both went unreserved before dynamics on bounds existed; they stay that way.

**Default endpoints and exclusions**:
- Default endpoints are unchanged and match LilyPond: `NoteColumn`'s `bound-alignment-interfaces` is `(rhythmic-head-interface stem-interface)`, so LilyPond anchors on notehead edges too, excluding accidentals and augmentation dots.

**Manual offsets**:
- The `x1ShiftSs` / `x2ShiftSs` / `yShiftSs` manual offsets still exist and are still applied post-layout by `VerticalStackingCalculator.applyDecorationOffsets` with no collision re-run; they are now a manual override on top of automatic coordination rather than the only mechanism.

**Stacking**:
- Stacked in the structural tier. Hairpins and text dynamics are both subject to the same cumulative skyline stacking; they also clear fermata and trill where those elements have already reserved space. Hairpins and dynamics themselves are not strictly pre/post-ordered by the grouping — the grouping applies at the *reference line* level, not the stacking pass.

**Margins and dimensions**:
- `HAIRPIN_MARGIN_SS` = `1.0 ss` still governs a group's clearance, and the mouth height is now `Hairpin.HAIRPIN_OPENING_HEIGHT_SS`, matching LilyPond's `Hairpin.height` doubled.

```
Example: "p" with diminuendo starting at the next note (automatic coordination)

    p────<           ← Hairpin anchored at next note, end pullback applied
    tr                ← Trill
    ⌢                ← Fermata
    >                ← Accent
    ·                ← Staccato
    ●                ← Note
```

### Example 9: First/second endings

**Scenario**: Repeat brackets (1st/2nd endings)

**Rules**:

1. **Margin**: `ENDING_MARGIN_SS` = `1.0 ss`
2. **Stacking**: Below tempo/beat changes and text annotations (rit., Fine, etc.) — the structural tier (which includes endings) fully completes before the system tier (tempo/beat change/annotations) begins
3. **Bounding area, not bounding box**: `StructuralStacker.stackEndings()` builds a `CollisionRegion` per bracket (via `Ending.computeCollisionRegions()`) and stacks them together with `stackAboveWithRegions` — only the bracket's top line needs clearance; content nested inside the bracket does not collide with it
4. **Treated as single element**: All of an ending's bracket regions are combined and stacked as one unit, using one anchor X and one overall width

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

1. **Position**: Above tempo/beat change — `SystemStacker.stack()` runs `stackTempo()`, then `stackBeatChange()`, then `stackAnnotations()` per column, each against the same cumulative skyline, so annotations end up outside tempo/beat change where they overlap
2. **Margin**: `ANNOTATION_MARGIN_SS` = `1.0 ss`

### Example 12: Attribution

**Scenario**: Composer/lyricist attribution at end of line

**Rules**:

1. **Position**: Top of the stack — `VerticalStackingCalculator.calculate()` stacks attribution as its final tier, over the system-tier skyline (which already includes tempo/beat change/annotations)
2. **Participates in the same collision-stacking system**: attribution is *not* laid out independently — it uses `StackingUtils.stackAbove()` against the system extents like everything else, and genuinely gets pushed further out if something below it demands more room
3. **Horizontal placement is fixed, not collision-based**: always right-aligned to the staff's right edge, inset by `ATTRIBUTION_RIGHT_MARGIN_SS` = `0.5 ss`. There is no left-margin constant — the block's left edge simply follows from its width.
4. **Margin**: Bottom `ATTRIBUTION_MARGIN_BOTTOM_SS` = `2.0 ss`, Right `ATTRIBUTION_RIGHT_MARGIN_SS` = `0.5 ss`
5. **Only on the first line**: `calculate()` accepts an `@Nullable Attribution` parameter that is non-null only for the song's first line

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
2. **Spacing**: The lyric baseline is computed independently of the above-staff skyline. `SongLayoutMetricsBuilder.build()` computes `staffToLyricsGapSs = LYRICS_ROW_MARGIN_SS + lyricAscentSs`, where `LYRICS_ROW_MARGIN_SS` = `1.0 ss` is the margin from the bottom of below-staff content (notes, downward ties, downward-stem articulations) to the top (ascent) of the lyric text, and `lyricAscentSs` is the measured ascent of the lyric font. (`LYRICS_HEIGHT_SS` = `2.5 ss` is a separate constant — the height of one lyric row — not the gap above it; do not conflate the two.)

```
    ●     ●     ●     ●      ← Notes
    |     |     |     |
═══════════════════════════  ← Staff
    |                        ← Stem (if down)
    ↓ 1.0 ss margin + lyric font ascent
   This   is   the   text    ← Lyrics (ascent aligned)
```

---

## Derived Rules

### Above Staff

All above-staff decorations share one algorithm: stack against a cumulative `StaffExtents` skyline, anchored to either the top staff line or the note's own reserved extent — whichever is more outward — with each element type owning its own padding/margin constant (see the per-example rules above; there is no single margin value shared by everything). Two collision styles are used:

- **Flat-box stacking** (`StackingUtils.stackAbove` / `placeAndReserveClamped` with `StaffExtents.Profiles.flat`) — used by tempo, beat change, annotations, hairpins, text dynamics, and fermata (fermata's glyph is round, but its collision footprint is a flat rectangle).
- **Outline-aware skyline stacking** (`ShapeProfile.outerEdge`/`innerEdge`) — used by staccato, accent, and trill, whose glyphs (dot, wedge, "tr" flourish) are reserved and cleared by their actual silhouette rather than a bounding rectangle.

Tiers, nearest-to-staff first: note-attached articulations → tuplet brackets → outer scripts (fermata, then trill) → structural decorations (hairpins and text dynamics together, then endings) → system decorations (tempo, then beat change, then annotations) → attribution (first line only, topmost).

Hairpins and text dynamics are one tier, not two: they are placed group by group so a hairpin and the dynamic beside it share a reference line (see Example 8), which is impossible if a second sweep stacks outside whatever the first reserved.

### Below Staff

Only lyrics are positioned below the staff — annotations are unconditionally forced above (see [Layout Order](#layout-order)). The lyric baseline is `1.0 ss` (`LYRICS_ROW_MARGIN_SS`) below the bottom of below-staff content, plus the lyric font's ascent.

### Collision Handling

No element collides against a single precomputed "full note bounding box." Every tier reads and writes the same `StaffExtents` skyline object (or a copy seeded from it — see `copyTopFrom()`), so each layer automatically clears everything reserved by earlier tiers within its own horizontal footprint. Endings are the one exception to whole-element collision: they reserve multiple independent `CollisionRegion`s per bracket, since only the bracket's top line needs clearance from what's above it — content nested *inside* the bracket is exempt.

### Stacking Order

The definitive tier/pass order, from `VerticalStackingCalculator.calculate()`:

1. `NoteAttachedStacker.stackInner()` — seed note bounds, seed tie bounds, stack staccato (all columns), then accent (all columns)
2. `StructuralStacker.stackTuplets()` — tuplet brackets
3. `NoteAttachedStacker.stackOuterScripts()` — fermata (all columns), then trills (per line)
4. `seedAccidentalsIntoStructural()` — accidental bounds seeded so structural elements clear them (scripts above ignore them)
5. `StructuralStacker.stackRemaining()` — hairpin/text-dynamic groups in left-to-right order (`DynamicGrouper`), then endings
6. `SystemStacker.stack()` — per column: tempo, then beat change, then annotations
7. `stackAttribution()` — first line only, topmost
8. `applyManualOffsets()` — post-layout user offsets (no collision re-run)

---

## Open Questions

- None outstanding. (The former question — whether hairpin/text-dynamic coordination should be automatic rather than manual-only — was answered by implementing it; see Example 8, refs #510.)
