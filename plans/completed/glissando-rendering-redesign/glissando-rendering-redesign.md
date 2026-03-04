# Glissando Rendering Redesign — Implementation Plan

## Status Dashboard

| Phase | Description | Status | Sub-plan |
|-------|-------------|--------|----------|
| 0 | [DRY Prerequisites](#-phase-0-dry-prerequisites-pure-refactoring-no-behavioral-changes) | ✅ Complete | — |
| 1 | [Note Area Construction](#-phase-1-note-area-construction-new-code-no-behavioral-change) | ✅ Complete | — |
| 2 | [Rounded Rect Rendering](#-phase-2-rounded-rect-rendering-visual-change) | ✅ Complete | 📋 [phase2-pill-rendering.md](./phase2-pill-rendering.md) |
| 3 | [Layout Integration](#-phase-3-layout-integration-spacing-change) | ✅ Complete | 📋 [phase3-layout-integration.md](./phase3-layout-integration.md) |
| 4 | [Behavioral Changes](#-phase-4-behavioral-changes) | ✅ Complete | — |

## Context

The current glissando renderer tiles wiggle-trill glyphs along the tangent line between notes, with gap calculation based on `NoteColumn` bounding box extents. This produces visually imprecise gaps (the glissando doesn't tuck close to the notehead when the tangent misses distant elements like dots or accidentals).

The redesign replaces this with a filled rounded rectangle (pill shape) and uses `java.awt.geom.Area`-based gap calculation that models each note's actual geometric footprint — notehead glyph outline, dots, accidentals, ledger lines, stem, and flags. This lets the glissando hug the notehead closely when the tangent doesn't pass through other elements.

See full spec: `docs/specs/glissando-rendering-redesign.md`

---

## ✅ Phase 0: DRY Prerequisites (pure refactoring, no behavioral changes)

Each sub-phase compiles independently. Commit after each.

### 0A — Consolidate `STEM_LENGTH_SS = 3.5`

Add `public static final double STEM_LENGTH_SS = 3.5` to `LayoutConstants.java`, then remove the duplicate from each location and replace with `LayoutConstants.STEM_LENGTH_SS`:

| File | Current form | Action |
|---|---|---|
| `ui/layout2/LayoutConstants.java` | (missing) | Add constant |
| `ui/layout2/NoteColumnBuilder.java` | `private static final double STEM_LENGTH_SS = 3.5` | Remove, use `LayoutConstants.STEM_LENGTH_SS` |
| `ui/renderer/NoteRenderer.java` | `private static final double STEM_LENGTH_SS = 3.5` | Remove, use `LayoutConstants.STEM_LENGTH_SS` |
| `music/NoteType.java` | `private static final double STEM_LENGTH_SS = 3.5` | Remove, use `LayoutConstants.STEM_LENGTH_SS` |
| `ui/renderer/BeamGroupRenderer.java` | literal `3.5` in `stemTipYSsOffset()` | Replace with `LayoutConstants.STEM_LENGTH_SS` |
| `ui/layout/BravuraFontBoundsProvider.java` | `private static final double STEM_LENGTH_STAFF_SPACES = 3.5` | Remove, use `LayoutConstants.STEM_LENGTH_SS` |

### 0B — Consolidate `noteStaffPositionToCoordinateSs`

The formula `middleLineYSs + staffPosition * 0.5` exists in three places:

| File | Current form | Action |
|---|---|---|
| `ui/renderer/BaseElementRenderer.java` | `protected` instance method | Change to `public static` |
| `ui/renderer/GlissandoRenderer.java` | `private static` method | Delete, call `BaseElementRenderer.noteStaffPositionToCoordinateSs()` |
| `ui/renderer/NoteRenderer.java` | `private calculateNoteYSs()` | Delete, call inherited static method |

### 0C — Expose private constants and unify accidental width

Move these from `NoteColumnBuilder` (private) to `LayoutConstants` (public):
- `DOT_WIDTH_SS = 0.5`
- `DOT_GAP_SS = 0.25`
- `ACCIDENTAL_GAP_SS = 0.25`

Widen visibility in `NoteRenderer` (same package as `GlissandoRenderer`):
- `FIRST_DOT_X_SS` → package-private (computed from SMuFL metadata at init; cannot be a compile-time constant)
- `DOT_SPACING_SS` → package-private (computed from SMuFL metadata at init; cannot be a compile-time constant)

**Unify accidental width computation:** `NoteColumnBuilder.getAccidentalWidthSs(Note.Accidental)` and `NoteRenderer.getAccidentalWidthSs(Note)` are two independent implementations that disagree for parenthesized accidentals. `NoteRenderer`'s version is correct (handles parentheses via `baseAccidentalParenthesisWidthsSs[]`). Make `NoteColumnBuilder.calculateLeftExtentSs()` delegate to `NoteRenderer.getAccidentalWidthSs(note)` instead of its own `getAccidentalWidthSs(accidental)`. Remove the now-unused `NoteColumnBuilder.getAccidentalWidthSs()` and `ACCIDENTAL_WIDTHS` map.

---

## ✅ Phase 1: Note Area Construction (new code, no behavioral change)

### 1.1 — Notehead glyph shape precomputation

In `GlissandoRenderer`, pre-compute 3 cached `Shape` templates using `Font.createGlyphVector()` → `GlyphVector.getOutline()` to obtain the actual glyph outline:
- **Quarter/filled**: outline of `noteheadBlack` glyph
- **Whole**: outline of `noteheadWhole` glyph
- **Grace**: quarter outline scaled by `GraceNoteRenderer.GRACE_NOTE_SCALE`

This uses the real glyph path rather than an ellipse approximation, so the Area matches the rendered notehead exactly.

### 1.2 — Area component construction

Add `buildNoteArea(Note, boolean beamed)` → `java.awt.geom.Area` in `GlissandoRenderer`.

The top-level method is a short orchestrator that delegates to per-component private helpers:

| Component | Helper method | Shape | Source |
|---|---|---|---|
| Notehead | `addNoteheadToArea()` | Glyph `Shape` | Step 1.1 templates |
| Dots | `addDotsToArea()` | `Ellipse2D` circles | `NoteRenderer.FIRST_DOT_X_SS`, `DOT_SPACING_SS`; Y offset from `staffPosition % 2` |
| Accidentals | `addAccidentalToArea()` | `Rectangle2D` | `NoteRenderer.getAccidentalWidthSs()`, `LayoutConstants.ACCIDENTAL_GAP_SS`, SMuFL bbox height |
| Ledger lines | `addLedgerLinesToArea()` | `Rectangle2D` | `getNoteheadRightEdgeSs() + 2 * legerLineExtension()`, `legerLineThickness()` |
| Stem | `addStemToArea()` | `Rectangle2D` | `LayoutConstants.STEM_WIDTH_SS`, `STEM_LENGTH_SS`, anchor offsets from `LayoutConstants` |
| Flags | `addFlagsToArea()` | `Rectangle2D` | `SMuFLMetadata.getBBox(flagGlyph)` at stem tip |

All positioned relative to glyph origin, unioned via `Area.add()`.

### 1.3 — Area cache on Note

In `Note.java`:
- Add `private transient Area glissandoArea` field + getter/setter/invalidator

Invalidation triggers — call `invalidateGlissandoArea()` from:
- `setAccidental()` — accidental changes width/presence
- `setDotCount()` — dots change right extent
- `setUpper()` — stem direction changes stem/flag position
- `setStaffPosition()` — **conditionally**, only when the new position crosses the C4 or G5 boundary (entering or leaving the ledger-line range). Most pitch-drag movements stay within the same ledger-line status and do not require invalidation.

Area is constructed lazily by `GlissandoRenderer` only for notes with glissandos.

### 1.4 — Binary search for tangent-area intersection

Add `findAreaExitPoint(Area, cx, cy, dx, dy, maxDist, tolerance)` — walks from note center outward along tangent direction, binary-searches for the last point inside the area. Tolerance: `SEARCH_TOLERANCE_SS = 0.05` (~0.4px).

**Verify**: Compile. Write unit tests:

| Test | What it verifies |
|---|---|
| `findAreaExitPoint` with circle Area | Basic binary search convergence |
| `findAreaExitPoint` with composite Area (rect + circle) | Handles unioned shapes |
| `findAreaExitPoint` with axis-aligned tangent (horizontal, vertical) | Degenerate angle edge cases |
| `findAreaExitPoint` with center outside Area | Defensive edge case |
| `buildNoteArea` for quarter note, no extras | Smoke test — produces non-empty Area |
| `buildNoteArea` with 1-2 dots | Area wider than without dots |
| `buildNoteArea` with accidental | Area extends left of notehead |
| `buildNoteArea` with ledger lines (above/below staff) | Ledger line rects included |

---

## ✅ Phase 2: Rounded Rect Rendering (visual change)

> Sub-plan: [phase2-pill-rendering.md](./phase2-pill-rendering.md)

### 2.1 — New constants in `GlissandoRenderer`

| Constant | Value |
|---|---|
| `MIN_GAP_SS` | `0.3` |
| `MIN_RECT_LENGTH_SS` | `1.34` |
| `RECT_THICKNESS_SS` | `engravingDefaults.legerLineThickness()` |
| `CORNER_RADIUS_SS` | `RECT_THICKNESS_SS / 2.0` |
| `SEARCH_TOLERANCE_SS` | `0.05` |
| `MIN_HORIZONTAL_RESERVATION_SS` | `MIN_RECT_LENGTH_SS + 2 * MIN_GAP_SS` |

Remove old constants: `MIN_SEGMENTS`, `SLIDE_OUT_SEGMENTS`, `DOTTED_SLIDE_OUT_GAP_SS`, `SLIDE_OUT_TANGENT_LENGTH_SS`, `GLYPH_Y_CENTER_SS`.

### 2.2 — Change anchor points to notehead center

- **x1**: `noteX + getNoteheadRightEdgeSs(note) / 2.0` (center of notehead)
- **x2 (CONNECTED)**: same formula for target note
- **x2 (SLIDE_OUT)**: `x1 + tangentLen / sqrt(2)` at fixed 45 degrees

### 2.3 — Rewrite rendering

1. Build/retrieve `Area` for source (and target if CONNECTED)
2. Tangent angle = `atan2(y2 - y1, x2 - x1)`
3. `findAreaExitPoint()` from each note center outward → add `MIN_GAP_SS` → glissando endpoints
4. Apply user `x1Translate`/`x2Translate` (already stored in staff spaces) with clamping: `effectiveTranslate = clamp(userTranslate, -maxRetract, +∞)` where `maxRetract = distFromExitPointToAreaEdge`. The user can shrink the gap to zero but cannot cause the pill endpoint to enter the note Area.
5. `Graphics2D.rotate()` to tangent, fill `RoundRectangle2D`, save/restore transform via try/finally
6. Skip rendering if rect length < `MIN_RECT_LENGTH_SS`

### 2.4 — Update preview rendering

Same path as committed glissandos. CONNECTED previews use both note areas; SLIDE_OUT only source area.

**Verify**: Compile and run. Visually confirm pill-shape rendering, proper gaps, SLIDE_OUT at 45 degrees, preview behavior.

---

## ✅ Phase 3: Layout Integration (spacing change)

> Sub-plan: [phase3-layout-integration.md](./phase3-layout-integration.md)

### 3.1 — Glissando-aware extents on `NoteColumn`

Add `glissandoLeftExtentSs` / `glissandoRightExtentSs` fields to `NoteColumn` that include ledger line overhang. Computed by `NoteColumnBuilder` only for notes with glissandos. Add `hasGlissando` boolean flag to `NoteColumn`.

### 3.2 — Spacing constraint in `HorizontalSpacingCalculator`

In the left-to-right pass: when `prevColumn.hasGlissando()`, compute horizontal distance between glissando extents. If < `MIN_HORIZONTAL_RESERVATION_SS` (1.94 ss), push next column right.

### 3.3 — Update `HorizontalAdjustment`

Add public `getGlissandoEndpoint1Ss()` / `getGlissandoEndpoint2Ss()` methods that return post-gap drawn endpoints. Update `HorizontalAdjustment` to use these for drag handle positioning (instead of the old column-edge anchor points).

**Verify**: Compile and run. Notes with glissandos maintain minimum spacing. Drag handles appear at actual rect endpoints.

---

## ✅ Phase 4: Behavioral Changes

### 4.1 — Unison prevention on add

In `InsertionNoteManager`: before adding a CONNECTED glissando, check if source and target have the same `staffPosition`. If so, skip the add.

### 4.2 — Unison prevention on pitch drag

In the pitch drag handler: when drag stops, check if any glissando between the dragged note and its neighbors creates a unison. If so, remove the glissando and show a `JOptionPane` alert with "Don't show this again" checkbox, persisted via `java.util.prefs.Preferences`.

### 4.3 — Verify preview rendering

Confirm existing zone computation (`InsertionNoteManager.computeGlissandoZone()`) works correctly with the new rendering. Adjust if needed.

**Verify**: Cannot add unison glissando. Pitch drag to unison removes glissando with alert. Preview rendering matches committed style.

Write unit tests for unison prevention:

| Test | What it verifies |
|---|---|
| Add CONNECTED glissando with same staffPosition | Glissando is not added |
| Add CONNECTED glissando with different staffPosition | Glissando is added |
| Pitch drag creating unison | Glissando is removed |

---

## Phase Dependencies

```
0A → 0B → 0C → 1 → 2 → 3 → 4
                         ↗
                    (2 also needed)
```

Each phase compiles independently after its prerequisites. Phase 0 sub-phases are safe to commit individually since they are pure refactoring.

## Key Risks

1. **SMuFL flag glyph bbox missing**: If `SMuFLMetadata.getBBox()` returns null for a flag glyph, the Area would omit the flag component. The pill could overlap the flag visually. Add a null guard that skips the flag component (acceptable degradation — flags are narrow and rarely intersected by tangents).
2. **Area cache invalidation during drag**: `setStaffPosition()` is called repeatedly during drag, but the cache is only invalidated when the position crosses the C4/G5 boundary (ledger line status change). Most drag movements do not cross this boundary, so the cached Area remains valid throughout the drag.
