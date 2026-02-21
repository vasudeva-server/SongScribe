# Plan: Phase 7 — Update NoteRenderer Stems + Fix TupletRenderer

## Context

Phase 6 wired `BeamGroupRenderer` to read pre-computed geometry from `LayoutResult` (via `StemLayout` and `BeamLayout`). `NoteRenderer.renderStem` and `renderFlags` still read from `note.properties.lengthening`, `note.properties.beamThickening`, and `note.properties.stem`. `TupletRenderer` still reads `note.properties.stem.x1/.y2`. After this phase, those three `Note.Properties` fields (`lengthening`, `beamThickening`, `stem`) can be deleted.

## Coordinate System Facts (from research)

- All rendering uses **Y-down, staff-space units**. `middleLineYSs` is the absolute Y of the middle staff line in that space.
- `StemLayout.topYSs` / `bottomYSs` are **offsets from the middle line**.
  - `upper=true` (stem up): stem tip = `stemLayout.bottomYSs()` (smaller Y = higher = upward)
  - `upper=false` (stem down): stem tip = `stemLayout.topYSs()`
  - This matches `stemTipYSsOffset()` in `BeamGroupRenderer`.
- After `g2.translate(noteX, noteY)` (where `noteY = middleLineYSs + staffPos * 0.5`), the **local coordinate** of the stem tip = `tipYSs - staffPos * 0.5`.
- `stemCenterXOffsetSs(noteType, upper)` in `BaseElementRenderer` gives the X offset from the note reference point to the stem center — already used by `BeamGroupRenderer`.

## Files to Change

| File | Changes |
|------|---------|
| `src/main/java/songscribe/ui/renderer/NoteRenderer.java` | Update `renderStem`, `renderFlags`, call sites |
| `src/main/java/songscribe/ui/renderer/TupletRenderer.java` | Replace `stem.x1` / `stem.y2` reads |
| `src/main/java/songscribe/music/Note.java` | Delete `Properties.lengthening`, `beamThickening`, `stem` |

## Step-by-Step Changes

### 1. `NoteRenderer.renderStem` — add `ctx`, replace legacy fields, remove writes

**Signature change:** add `@NotNull ElementRenderContext ctx` as last parameter.

**Replace** the old pixel-conversion block:
```java
double pxPerSs = ScaleContext.getInstance().getPixelsPerStaffSpace();
double lengtheningInSs = note.properties.lengthening / pxPerSs;
double beamThickeningInSs = note.properties.beamThickening / pxPerSs;
```
**With** LayoutResult reads:
```java
var layoutResult = ctx.getLayoutResult();
var stemLayout = (layoutResult != null) ? layoutResult.getStemLayout(note) : null;
double lengtheningSs = (stemLayout != null) ? stemLayout.lengtheningSs() : 0.0;

double beamThickeningSs = 0.0;
if (beamed && layoutResult != null) {
    var line = ctx.getCurrentLine();
    if (line != null) {
        var interval = line.getBeamings().findInterval(line.getNoteIndex(note));
        if (interval != null) {
            var beamLayout = layoutResult.getBeamLayout(interval);
            if (beamLayout != null) {
                beamThickeningSs = beamLayout.thickeningSs();
            }
        }
    }
}
```

**Update** downstream variable names (rename `lengtheningInSs` → `lengtheningSs`, `beamThickeningInSs` → `beamThickeningSs`).

**Remove** both `note.properties.stem.setLine(...)` calls (they are the only writes).

**Remove** the now-unused `ScaleContext` import.

**Update call site** in `renderNoteHead`:
```java
renderStem(g2, note, note.isUpper(), beamed, noteType, ctx);
```
(`ctx` is already in scope there — `renderNoteHead` receives it.)

### 2. `NoteRenderer.renderFlags` — add `ctx`, derive position from StemLayout

**Signature change:** add `@NotNull ElementRenderContext ctx` as last parameter.

**Replace** the `note.properties.stem` reads:
```java
var stem = note.properties.stem;
float flagX = (float) (stem.getX1() - STEM_WIDTH_SS / 2);
float flagY = (float) stem.getY2();
```
**With:**
```java
// Flag X: same anchor formula as renderStem
boolean isMinim = noteType == NoteType.MINIM;
GlyphAnchors.Anchor anchor = upper
    ? (isMinim ? STEM_UP_SE_HALF : STEM_UP_SE_BLACK)
    : (isMinim ? STEM_DOWN_NW_HALF : STEM_DOWN_NW_BLACK);
double stemLeftRaw = upper ? anchor.x() - STEM_WIDTH_SS : anchor.x() - STEM_WIDTH_SS / 2;
float flagX = (float) GraphicUtils.snapXToDevicePixel(g2, stemLeftRaw);

// Flag Y: stem tip in local note coordinates
var layoutResult = ctx.getLayoutResult();
var stemLayout = (layoutResult != null) ? layoutResult.getStemLayout(note) : null;
float flagY;
if (stemLayout != null) {
    double tipYSs = upper ? stemLayout.bottomYSs() : stemLayout.topYSs();
    flagY = (float) (tipYSs - note.getStaffPosition() * 0.5);
} else {
    flagY = (float) (anchor.y() + (upper ? -STEM_LENGTH_SS : STEM_LENGTH_SS));
}
```

**Update call site** in `renderNoteHead`:
```java
renderFlags(g2, note, note.isUpper(), noteType, ctx);
```

### 3. `TupletRenderer.renderTuplet` — replace `stem.x1` / `stem.y2`

This renderer has pre-existing mixed-unit issues; changes here are **minimal** — just remove the two `note.properties.stem` dependencies without fixing anything else.

**Replace `note.properties.stem.y2`** (used to compute `refY` for isUpper=true):
```java
// OLD:
refY = noteY + note.properties.stem.y2;

// NEW:
var layoutResult = ctx.getLayoutResult();
var stemLayout = (layoutResult != null) ? layoutResult.getStemLayout(note) : null;
double stemTipLocalY = (stemLayout != null)
    ? (isUpper ? stemLayout.bottomYSs() : stemLayout.topYSs()) - note.getStaffPosition() * 0.5
    : (isUpper ? -3.5 : 3.5);  // 3.5 = MIN_STEM_SS fallback
refY = noteY + stemTipLocalY;
```

**Replace `note.properties.stem.x1`** (5 occurrences, all of the form `note.getXPos() + note.properties.stem.x1`):
- `stem.x1` was set to `stemCenterX = stemLeftX + STEM_WIDTH_SS / 2`
- Equivalent: `stemCenterXOffsetSs(note.getNoteType(), isUpper)` (inherited from `BaseElementRenderer`)
- Each `note.properties.stem.x1` → `stemCenterXOffsetSs(note.getNoteType(), isUpper)`

### 4. Delete legacy `Note.Properties` fields

After all callers are fixed, remove from `Note.Properties`:
```java
// DELETE these three lines:
public int lengthening = 0;
public double beamThickening = 0.0;
public final Line2D.Double stem = new Line2D.Double();
```

## Verification

```bash
./scripts/compile.sh   # Must succeed with no errors
./scripts/run.sh       # Open a composition with beams, check rendering
```

Visual checks:
- Beamed 8th notes: stems connect to beams correctly
- 16th/32nd notes: flags appear at correct stem tip position
- Unbeamed notes with flags: flags positioned correctly
- Tuplet brackets: rendered at correct vertical position relative to stems
