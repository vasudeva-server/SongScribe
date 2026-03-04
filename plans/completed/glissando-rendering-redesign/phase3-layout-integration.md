**Type:** Sub-plan  <br>
**Parent:** plans/glissando-rendering-redesign/glissando-rendering-redesign.md → Phase 3  <br>
**Captured:** 2026-03-01  <br>
**Status:** In Progress  <br>
**BlockedBy:** —

---

# Phase 3: Layout Integration — Implementation Plan

## Context

Phases 0-2 of the glissando redesign are complete. The pill-shaped glissando renders correctly using Area-based gap calculation, but the **layout system is unaware of glissandos**. Notes with CONNECTED glissandos can be placed so close together that the pill has insufficient horizontal room to render (shorter than `MIN_RECT_LENGTH_SS = 1.34 ss`), causing it to be silently skipped. Phase 3 adds a minimum spacing constraint so the layout always reserves enough horizontal space for the glissando pill between adjacent notes.

Additionally, the HorizontalAdjustment drag handles are positioned at notehead centers rather than at the actual pill endpoints, making the handles visually misaligned with the pill.

### Related issues

- **#109** — Re-layout legacy songs on load to apply glissando spacing constraint
- **#110** — Tighten spacing for SLIDE_OUT glissando on last note of a line

---

## Sub-phase 3.1 — Glissando Flag on NoteColumn + Shared Ledger Overhang Helper

**Goal:** Add a `hasGlissando` flag to `NoteColumn` and extract a shared helper for computing ledger line overhang. These are used by the spacing calculator in sub-phase 3.2 to enforce minimum glissando spacing.

### Why ledger line overhang matters

The existing `leftExtentSs` / `rightExtentSs` cover notehead, dots, accidentals, and flags — but NOT ledger line overhang. Ledger lines extend `legerLineExtension() = 0.4 ss` beyond the notehead on each side. When the glissando tangent passes through the ledger line region, the Area exit point is further out, eating into the horizontal space available for the pill. The spacing constraint must account for this.

### Changes

**`GlissandoRenderer.java`** — Extract a shared helper for ledger line overhang:

```java
/**
 * Returns the ledger line overhang for a note, or 0 if the note has no ledger lines.
 * This is the distance the ledger lines extend beyond the notehead on each side.
 * Used by both addLedgerLinesToArea() and the spacing constraint in
 * HorizontalSpacingCalculator.
 */
public static double getLedgerLineOverhangSs(@NotNull Note note) {
    if (Math.abs(note.getStaffPosition()) <= 5 || !note.getNoteType().drawStaveLongitude()) {
        return 0.0;
    }

    double extensionSs = ENGRAVING.legerLineExtension();

    if (note.getNoteType().isGraceNote()) {
        extensionSs *= GraceNoteRenderer.GRACE_NOTE_SCALE;
    }

    return extensionSs;
}
```

Update `addLedgerLinesToArea()` to delegate to this helper instead of computing the extension inline.

**`NoteColumn.java`** — Add 1 new field:
- `private final boolean hasGlissando` — true when `note.getGlissando() != Note.NO_GLISSANDO`
- Add constructor parameter and getter: `hasGlissando()`

No separate glissando extent fields. Ledger-line-inclusive extents are computed on-the-fly in the spacing calculator (sub-phase 3.2) using the shared helper, keeping `NoteColumn` lean.

**`NoteColumnBuilder.java`** — Compute the flag in `buildColumn()`:

```java
boolean hasGlissando = note.getGlissando() != Note.NO_GLISSANDO;
```

Pass it to the `NoteColumn` constructor.

**`InsertionSpacingCalculator.java`** — Update `createLightweightColumn()` to pass `hasGlissando=false` (insertion operations don't need glissando spacing).

**`LayoutResult.java`** — Update the inline `NoteColumn` construction in `calculateInsertionXSs()` to pass `hasGlissando=false`.

### Files modified
- `src/main/java/songscribe/ui/renderer/GlissandoRenderer.java`
- `src/main/java/songscribe/ui/layout2/NoteColumn.java`
- `src/main/java/songscribe/ui/layout2/NoteColumnBuilder.java`
- `src/main/java/songscribe/ui/layout2/InsertionSpacingCalculator.java`
- `src/main/java/songscribe/ui/layout2/LayoutResult.java`

---

## Sub-phase 3.2 — Spacing Constraint in HorizontalSpacingCalculator

**Goal:** When two adjacent columns are connected by a glissando, ensure minimum horizontal space for the pill to render.

### Constant

Add to `GlissandoRenderer` (if not already present):
```java
public static final double MIN_HORIZONTAL_RESERVATION_SS =
    MIN_RECT_LENGTH_SS + 2 * MIN_GAP_SS;  // 1.34 + 0.6 = 1.94 ss
```

### Helper method

Extract a helper `ensureGlissandoSpacing(prev, curr, spacingSs)` that computes ledger-line-inclusive extents on-the-fly and returns the adjusted spacing:

```java
/**
 * Ensures minimum horizontal spacing for a glissando pill between two columns.
 * Computes ledger-line-inclusive extents on-the-fly via the shared helper
 * on GlissandoRenderer. Returns the input spacing unchanged if no glissando
 * or if there is already enough room.
 *
 * Geometry:
 *
 *   prev note origin          curr note origin
 *       |                         |
 *       |<-- rightExtent --->|    |
 *       |              overhang-->|    |<--overhang
 *       |                   |<-->|    |
 *       |                   gap  |    |
 *       |<------- spacingSs ---------->|
 *
 *   glissRightExtent = max(rightExtent, noteheadWidth + overhang)
 *   glissLeftExtent  = min(leftExtent, -overhang)
 *   gap = spacingSs + glissLeftExtent(curr) - glissRightExtent(prev)
 *   The pill must fit within "gap".
 */
private static double ensureGlissandoSpacing(
    @NotNull NoteColumn prev, @NotNull NoteColumn curr, double spacingSs
) {
    if (!prev.hasGlissando()) return spacingSs;

    // Compute ledger-line-inclusive extents on-the-fly
    double prevOverhang = GlissandoRenderer.getLedgerLineOverhangSs(prev.getNote());
    double prevGlissRight = prev.getRightExtentSs();
    if (prevOverhang > 0) {
        double noteheadWidthSs = NoteRenderer.getNoteheadRightEdgeSs(prev.getNote());
        prevGlissRight = Math.max(prevGlissRight, noteheadWidthSs + prevOverhang);
    }

    double currOverhang = GlissandoRenderer.getLedgerLineOverhangSs(curr.getNote());
    double currGlissLeft = curr.getLeftExtentSs();
    if (currOverhang > 0) {
        currGlissLeft = Math.min(currGlissLeft, -currOverhang);
    }

    double gap = spacingSs + currGlissLeft - prevGlissRight;
    double needed = GlissandoRenderer.MIN_HORIZONTAL_RESERVATION_SS;

    if (gap < needed) {
        spacingSs += (needed - gap);
    }

    return spacingSs;
}
```

### Where to add the constraint

**In `calculateNextColumnXSs()`** — after computing the tentative `nextXSs` and the accidental push, apply the glissando spacing check:

```java
// Check glissando spacing: ensure enough horizontal room for the pill
double spacingSs = nextXSs - prevColumn.getXSs();
spacingSs = ensureGlissandoSpacing(prevColumn, currColumn, spacingSs);
nextXSs = prevColumn.getXSs() + spacingSs;
```

This follows the existing accidental-push pattern: compute tentative position, check constraint, push right if violated.

**In `handleBeamGroup()`** — in the tight spacing loop, apply after computing the base spacing:

```java
double spacingSs = prev.getRightExtentSs() + tightGapSs + Math.abs(curr.getLeftExtentSs());
spacingSs = ensureGlissandoSpacing(prev, curr, spacingSs);
currentXSs += spacingSs;
```

### Files modified
- `src/main/java/songscribe/ui/renderer/GlissandoRenderer.java` (add `MIN_HORIZONTAL_RESERVATION_SS` constant)
- `src/main/java/songscribe/ui/layout2/HorizontalSpacingCalculator.java` (add `ensureGlissandoSpacing` helper, apply in both methods)

---

## Sub-phase 3.3 — Update HorizontalAdjustment Handle Positioning

**Goal:** Position drag handles at the actual pill endpoints (after area exit + gap) rather than at notehead centers.

### Extract `computePillEndpoints()` from `renderPill()`

Extract the endpoint computation (everything before the `Graphics2D` drawing calls) into a shared helper that returns a record:

```java
/**
 * Immutable record holding the computed pill endpoint positions in layout space.
 */
record PillEndpoints(double startX, double startY, double endX, double endY) {}

/**
 * Computes the pill start/end positions in layout space (staff-space coordinates).
 * Returns null if the pill is too short to render (endpoints crossed or
 * length < MIN_RECT_LENGTH_SS).
 *
 * Both renderPill() and the public endpoint methods delegate to this.
 */
@Nullable
private PillEndpoints computePillEndpoints(
    @NotNull Note note1, double cx1, double cy1, @NotNull Area area1,
    @Nullable Note note2, double cx2, double cy2, @Nullable Area area2,
    double x1Translate, double x2Translate
) {
    // ... extracted from renderPill() lines 381-445:
    // 1. Compute tangent direction (dx, dy), normalize
    // 2. Find exit points via findAreaExitPoint() in local coordinates
    // 3. Translate to layout space with gap + clamped user offset
    // 4. Check pill length >= MIN_RECT_LENGTH_SS and endpoints not crossed
    // 5. Return PillEndpoints or null
}
```

Update `renderPill()` to delegate:

```java
private void renderPill(
    @NotNull Graphics2D g2,
    @NotNull Note note1, double cx1, double cy1, @NotNull Area area1,
    @Nullable Note note2, double cx2, double cy2, @Nullable Area area2,
    double x1Translate, double x2Translate
) {
    var endpoints = computePillEndpoints(
        note1, cx1, cy1, area1, note2, cx2, cy2, area2, x1Translate, x2Translate);

    if (endpoints == null) return;

    // Draw the pill using endpoints.startX/Y, endpoints.endX/Y
    // (existing Graphics2D rotation + fill logic)
}
```

### Update public endpoint methods

Replace the current `getGlissandoX1Ss`/`getGlissandoX2Ss` implementations (which return notehead centers) to return actual pill endpoints:

```java
public static double getGlissandoX1Ss(
    int xIndex, @NotNull Note.Glissando glissando, int lineIndex,
    @NotNull Composition composition, @NotNull LayoutResult layoutResult,
    double middleLineYSs
) {
    // Build areas, compute centers, call computePillEndpoints()
    // Return endpoints.startX
    // TODO: refs #109 — after Phase 3 spacing constraint, endpoints should
    // never be null here. Fail fast with assertion if they are.
}

public static double getGlissandoX2Ss(
    int xIndex, @NotNull Note.Glissando glissando, int lineIndex,
    @NotNull Composition composition, @NotNull LayoutResult layoutResult,
    double middleLineYSs
) {
    // Same pattern, return endpoints.endX
}
```

The new `middleLineYSs` parameter is needed to compute Y positions from staff positions. The existing signature changes — callers must be updated.

### Update HorizontalAdjustment

In `getAdjustRect()`, change the `GLISSANDO_START` / `GLISSANDO_END` cases to pass `middleLineYSs` to the updated endpoint methods. This value is available from the LineComponent's layout result.

### Files modified
- `src/main/java/songscribe/ui/renderer/GlissandoRenderer.java` (add `PillEndpoints` record, extract `computePillEndpoints`, update endpoint methods)
- `src/main/java/songscribe/ui/adjustment/HorizontalAdjustment.java` (pass `middleLineYSs` to updated endpoint methods)

---

## Tests

### New test file: `HorizontalSpacingCalculatorTest.java`

| ID | Test | Description |
|---|---|---|
| T5 | `testEnsureGlissandoSpacing_noGlissando` | `prev.hasGlissando()=false` returns input unchanged |
| T6 | `testEnsureGlissandoSpacing_sufficientGap` | Gap >= `MIN_HORIZONTAL_RESERVATION_SS` returns input unchanged |
| T7 | `testEnsureGlissandoSpacing_insufficientGap` | Gap < minimum returns `spacingSs + deficit` |
| T8 | `testEnsureGlissandoSpacing_bothOnLedgerLines` | Both notes on ledger lines produce larger adjustment than T7 |
| T9 | `testEnsureGlissandoSpacing_sourceOnlyOnLedgerLines` | Only source note on ledger lines produces moderate adjustment |
| T10 | `testBeamGroupWithInternalGlissando` | Beam group tight spacing enforces glissando minimum |

### Additions to `GlissandoRendererTest.java`

| ID | Test | Description |
|---|---|---|
| T1 | `testGetLedgerLineOverhangSs_noteOnStaff` | Staff position <= 5 returns 0.0 |
| T2 | `testGetLedgerLineOverhangSs_noteOnLedgerLine` | Staff position > 5 returns `legerLineExtension()` |
| T3 | `testGetLedgerLineOverhangSs_graceNoteOnLedgerLine` | Returns extension scaled by `GRACE_NOTE_SCALE` |
| T4 | `testGetLedgerLineOverhangSs_rest` | `drawStaveLongitude()=false` returns 0.0 |
| T12 | `testComputePillEndpoints_connectedValid` | Returns non-null with `startX < endX` |
| T13 | `testComputePillEndpoints_tooShort` | Returns null when notes too close |
| T14 | `testComputePillEndpoints_slideOut` | Returns valid fixed-angle endpoints |

### Addition to `NoteColumnBuilderTest.java`

| ID | Test | Description |
|---|---|---|
| T11 | `testHasGlissandoFlag` | True when glissando set, false when `NO_GLISSANDO` |

---

## Implementation Order

```
3.1 (NoteColumn flag + GlissandoRenderer shared helper)
  |
  v
3.2 (HorizontalSpacingCalculator constraint + tests T1-T11)
  |
  v
3.3 (GlissandoRenderer endpoint extraction + HorizontalAdjustment + tests T12-T14)
```

Each sub-phase compiles independently. Commit after each.

---

## Verification

After all sub-phases:
1. `./scripts/compile.sh` — verify compilation
2. `./scripts/run.sh` — launch the application
3. **Visual checks:**
   - Create two adjacent notes with a CONNECTED glissando — pill should render with visible length even when notes are close
   - Create notes with ledger lines (above/below staff) and glissandos — spacing should accommodate ledger line overhang
   - Verify glissandos within beam groups still render correctly
   - Verify drag handles appear at the pill endpoints (not notehead centers)
   - Drag handles to adjust gap — pill should respond correctly
   - Verify SLIDE_OUT glissandos are unaffected by spacing changes
