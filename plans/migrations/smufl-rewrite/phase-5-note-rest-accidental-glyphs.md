**Type:** Sub-plan  <br>
**Parent:** plans/migrations/smufl-rewrite/smufl-rewrite.md → Phase 5  <br>
**Captured:** 2026-02-16  <br>
**Pre-planned:** Yes  <br>
**Status:** Completed

---

# Phase 5: Note Head, Rest, and Accidental Glyph Rendering

## Status Dashboard

| Step | Description | Status |
|------|-------------|--------|
| 1 | Add color-preserving drawBravuraGlyph variant | ✅ Complete |
| 2 | Switch note head rendering in NoteRenderer | ✅ Complete |
| 3 | Switch dot rendering to augmentation dot glyph | ✅ Complete |
| 4 | Switch accidental rendering in NoteRenderer | ✅ Complete |
| 5 | Switch accidental width computation to SMuFL metadata | ✅ Complete |
| 6 | Switch rest rendering in RestRenderer | ✅ Complete |
| 7 | Switch key signature rendering in KeySignatureRenderer | ✅ Complete |
| 8 | Update NoteRenderer.renderRestGlyph() | ✅ Complete |
| 9 | Update TempoRenderer and BeatChangeRenderer callers | ✅ Complete |
| 10 | Clean up Fughetta glyph constants | ✅ Complete |
| 11 | Visual verification | ✅ Complete |

**Progress: 11/11 steps complete**

---

## Context

After Phase 4 replaced bounding rectangles with SMuFL metadata-driven bounds, the visual rendering still draws glyphs from the Fughetta font using Private Use Area (PUA) codepoints. Phase 5 switches all core notation glyph rendering -- note heads, rests, accidentals, dots, and key signature accidentals -- from Fughetta PUA codepoints to Bravura/SMuFL codepoints.

The `drawBravuraGlyph()` method in `BaseElementRenderer` is already available (used by `ClefRenderer` since Phase 1). The `SMuFLGlyph` enum already contains all needed entries for note heads, rests, accidentals, and the augmentation dot. No new enum entries are required.

## Glyph Mapping Reference

### Note Heads

| Purpose | Fughetta | SMuFL Glyph | Codepoint |
|---|---|---|---|
| Whole note (semibreve) | `\uf077` | `NOTEHEAD_WHOLE` | U+E0A2 |
| Half note (minim) | `\uf0cd` | `NOTEHEAD_HALF` | U+E0A3 |
| Filled note head | `\uf0cf` | `NOTEHEAD_BLACK` | U+E0A4 |

### Rests

| Purpose | Fughetta | SMuFL Glyph | Codepoint |
|---|---|---|---|
| Whole rest | `\uf0ee` | `REST_WHOLE` | U+E4E3 |
| Half rest | `\uf0ee` | `REST_HALF` | U+E4E4 |
| Quarter rest | `\uf0ce` | `REST_QUARTER` | U+E4E5 |
| Eighth rest | `\uf0e4` | `REST_8TH` | U+E4E6 |
| Sixteenth rest | `\uf0c5` | `REST_16TH` | U+E4E7 |
| Thirty-second rest | `\uf0a8` | `REST_32ND` | U+E4E8 |

Note: Fughetta used the same glyph (`\uf0ee`) for both whole and half rests with Y-offset positioning. SMuFL provides distinct glyphs.

### Accidentals (simple, non-parenthesized)

| Purpose | Fughetta | SMuFL Glyph | Codepoint |
|---|---|---|---|
| Natural | `\uf06e` | `ACCIDENTAL_NATURAL` | U+E261 |
| Flat | `\uf062` | `ACCIDENTAL_FLAT` | U+E260 |
| Sharp | `\uf023` | `ACCIDENTAL_SHARP` | U+E262 |
| Double natural | `\uf06e\uf06e` | 2x `ACCIDENTAL_NATURAL` | 2x U+E261 |
| Double flat | `\uf0ba` | `ACCIDENTAL_DOUBLE_FLAT` | U+E264 |
| Double sharp | `\uf0dc` | `ACCIDENTAL_DOUBLE_SHARP` | U+E263 |
| Natural + flat | `\uf06e\uf062` | `ACCIDENTAL_NATURAL` + `ACCIDENTAL_FLAT` | U+E261 + U+E260 |
| Natural + sharp | `\uf06e\uf023` | `ACCIDENTAL_NATURAL` + `ACCIDENTAL_SHARP` | U+E261 + U+E262 |

### Accidentals (parenthesized -- Fughetta special glyphs)

Fughetta had special single-glyph parenthesized accidentals. In SMuFL, parenthesized accidentals are constructed from `ACCIDENTAL_PARENS_LEFT` (U+E26A) + accidental + `ACCIDENTAL_PARENS_RIGHT` (U+E26B).

| Purpose | Fughetta | SMuFL Replacement |
|---|---|---|
| (natural) | `\uf04e` | `ACCIDENTAL_PARENS_LEFT` + `ACCIDENTAL_NATURAL` + `ACCIDENTAL_PARENS_RIGHT` |
| (flat) | `\uf041` | `ACCIDENTAL_PARENS_LEFT` + `ACCIDENTAL_FLAT` + `ACCIDENTAL_PARENS_RIGHT` |
| (sharp) | `\uf061` | `ACCIDENTAL_PARENS_LEFT` + `ACCIDENTAL_SHARP` + `ACCIDENTAL_PARENS_RIGHT` |
| (double flat) | `\uf08c` | `ACCIDENTAL_PARENS_LEFT` + `ACCIDENTAL_DOUBLE_FLAT` + `ACCIDENTAL_PARENS_RIGHT` |
| (double sharp) | `\uf081` | `ACCIDENTAL_PARENS_LEFT` + `ACCIDENTAL_DOUBLE_SHARP` + `ACCIDENTAL_PARENS_RIGHT` |

For compound accidentals (double-natural, natural-flat, natural-sharp), the parenthesized form already falls through to the `else` branch in `renderAccidental()` (since `ACCIDENTALS[i].equals(ACCIDENTAL_PARENTHESIS[i])` is true for those entries), which manually draws `BEGIN_PARENTHESIS` + accidental + `END_PARENTHESIS`.

### Parentheses

| Purpose | Fughetta | SMuFL Glyph | Codepoint |
|---|---|---|---|
| Left parenthesis | `\uf028` | `ACCIDENTAL_PARENS_LEFT` | U+E26A |
| Right parenthesis | `\uf029` | `ACCIDENTAL_PARENS_RIGHT` | U+E26B |

### Augmentation Dot

| Purpose | Current | SMuFL Glyph | Codepoint |
|---|---|---|---|
| Dot | `Ellipse2D.Double` (filled shape) | `AUGMENTATION_DOT` | U+E1E7 |

### Key Signature Accidentals

| Purpose | Fughetta | SMuFL Glyph |
|---|---|---|
| Flat | `\uf062` | `ACCIDENTAL_FLAT` |
| Sharp | `\uf023` | `ACCIDENTAL_SHARP` |
| Natural (key changes) | `\uf06e` | `ACCIDENTAL_NATURAL` |

## SMuFL Origin Convention Differences

Fughetta glyphs have their baseline/origin at font-specific positions. SMuFL/Bravura defines glyph origins consistently:
- **Note heads**: Origin at the left edge, vertically centered on the staff line (y=0 is the staff line).
- **Rests**: Origin varies per rest type. Whole rest hangs below a line; half rest sits on a line. Quarter and smaller rests are centered on the middle of the staff.
- **Accidentals**: Origin at the left edge, vertically aligned with the staff line.

Key Y-offset considerations:
1. **Rests**: Fughetta used the same glyph for whole and half rests. SMuFL uses distinct glyphs with different origins. The `calculateRestY()` method uses `SEMIBREVE_REST_Y_OFFSET = -2` and `MINIM_REST_Y_OFFSET = 0` which position correctly for Fughetta. These offsets will need validation with Bravura's rest glyph origins.
2. **Note heads**: Bravura note heads are origin-centered on the staff line, same as Fughetta's note head baseline. No Y adjustment needed.
3. **Accidentals**: Bravura accidentals are centered vertically on the staff line. No Y adjustment expected.

## Rendering Approach Change

Currently, note heads, accidentals, and rests are rendered using `g2.drawString(fughettaString, x, y)` with the Fughetta `MUSIC_FONT`. The migration will switch to using `drawBravuraGlyph()` which internally:
1. Saves graphics state (color, font)
2. Sets font to `BRAVURA_FONT`
3. Sets color to `NOTE_COLOR`
4. Draws the `SMuFLGlyph.asString()` at the specified position
5. Restores graphics state

However, `drawBravuraGlyph()` always sets `NOTE_COLOR`, which would override the caller's color (e.g., blue for insertion notes). This needs to be addressed. The rendering methods currently follow the pattern "don't set color here - respect the color set by the caller." A new overload or variant of `drawBravuraGlyph()` that preserves the current color is needed.

## Accidental Width Computation

Currently, `initializeAccidentalWidths()` uses `g2.getFontMetrics(MUSIC_FONT)` to measure Fughetta glyph string widths in pixels. With SMuFL, widths should come from `SMuFLMetadata.getAdvanceWidth()` which returns values in staff spaces. These are converted to pixels using `StaffSpaces.toPixels()` (8.0 pixels per staff space).

---

## Implementation Steps

### ✅ Step 1: Add color-preserving drawBravuraGlyph variant to BaseElementRenderer

Add a new protected method `drawBravuraGlyphPreserveColor()` (or modify `drawBravuraGlyph` to accept a boolean parameter) that sets the Bravura font but does NOT override the current color. This is needed because note head, accidental, and rest rendering relies on the caller having set the color (black for composition notes, blue for insertion notes, etc.).

Suggested signature:
```java
protected void drawBravuraGlyph(
    @NotNull Graphics2D g2,
    @NotNull SMuFLGlyph glyph,
    double x,
    double y,
    boolean preserveColor
)
```

When `preserveColor` is true, skip the `g2.setColor(NOTE_COLOR)` call. The existing 4-parameter overload remains unchanged for backward compatibility (ClefRenderer etc.).

**Compile and verify.**

### ✅ Step 2: Switch note head rendering in NoteRenderer

**2a.** Change the `NOTE_HEAD` map from `EnumMap<NoteType, String>` to `EnumMap<NoteType, SMuFLGlyph>`:

```java
private static final EnumMap<NoteType, SMuFLGlyph> NOTE_HEAD = new EnumMap<>(NoteType.class);

static {
    NOTE_HEAD.put(NoteType.SEMIBREVE, SMuFLGlyph.NOTEHEAD_WHOLE);
    NOTE_HEAD.put(NoteType.MINIM, SMuFLGlyph.NOTEHEAD_HALF);
    NOTE_HEAD.put(NoteType.CROTCHET, SMuFLGlyph.NOTEHEAD_BLACK);
    NOTE_HEAD.put(NoteType.QUAVER, SMuFLGlyph.NOTEHEAD_BLACK);
    NOTE_HEAD.put(NoteType.SEMIQUAVER, SMuFLGlyph.NOTEHEAD_BLACK);
    NOTE_HEAD.put(NoteType.DEMI_SEMIQUAVER, SMuFLGlyph.NOTEHEAD_BLACK);
}
```

**2b.** Add `getNoteHeadGlyph()` returning `SMuFLGlyph`, update NoteRenderer's internal methods to use it. Update `getNoteHeadChar()` to return `glyph.asString()` using the Bravura codepoint. TempoRenderer and BeatChangeRenderer will be updated in Step 9.

**2c.** Update `renderNoteHead()` and `renderNoteHeadSimple()` to use Bravura font locally for the note head draw:

```java
// In renderNoteHead / renderNoteHeadSimple:
var glyph = NOTE_HEAD.get(noteType);
if (glyph == null) return;

try (var ignored2 = GraphicsState.save(g2, FONT)) {
    g2.setFont(BRAVURA_FONT);
    g2.drawString(glyph.asString(), noteHeadXPos, 0f);
}
```

**2d.** Defer stem X position recalibration. Current Fughetta-specific `UPPER_CROTCHET_STEM_X` and `UPPER_MINIM_STEM_X` are close to SMuFL anchor values (8.88px vs 9.44px). Keep current values; recalibrate in Phase 6 (stems/flags).

**Compile and verify visually.**

### ✅ Step 3: Switch dot rendering from Ellipse2D to augmentation dot glyph

**3a.** In `NoteRenderer`, replace the `NOTE_DOTS` `Ellipse2D.Double[]` array and `DOT_WIDTH` constant with SMuFL augmentation dot positioning:

```java
try (var ignored2 = GraphicsState.save(g2, FONT)) {
    g2.setFont(BRAVURA_FONT);
    float dotX = FIRST_DOT_X; // calibrate from Bravura metrics
    for (int i = 0; i < note.getDotCount(); i++) {
        g2.drawString(SMuFLGlyph.AUGMENTATION_DOT.asString(), dotX, 0f);
        dotX += DOT_SPACING; // from advance width metadata
    }
}
```

Dot X positions and spacing should use `SMuFLMetadata.getInstance().getAdvanceWidth(SMuFLGlyph.AUGMENTATION_DOT)` converted to pixels via `StaffSpaces.toPixels()`. The initial X offset (distance from note head to first dot) should be calibrated from the current value of 13.1px.

**3b.** Apply the same change to `RestRenderer.renderDots()`, replacing `g2.fill(NOTE_DOTS[i])` with Bravura augmentation dot glyph rendering.

**3c.** Remove the `NOTE_DOTS` Ellipse2D array and `DOT_WIDTH` constant from both NoteRenderer and RestRenderer (and the `java.awt.geom.Ellipse2D` import from RestRenderer).

**Compile and verify visually.**

### ✅ Step 4: Switch accidental rendering in NoteRenderer

**4a.** Replace the `ACCIDENTALS` string array with a `SMuFLGlyph[][]` components array:

```java
private static final SMuFLGlyph[][] ACCIDENTAL_COMPONENTS = {
    {},                                                              // NONE
    {SMuFLGlyph.ACCIDENTAL_NATURAL},                                // NATURAL
    {SMuFLGlyph.ACCIDENTAL_FLAT},                                   // FLAT
    {SMuFLGlyph.ACCIDENTAL_SHARP},                                  // SHARP
    {SMuFLGlyph.ACCIDENTAL_NATURAL, SMuFLGlyph.ACCIDENTAL_NATURAL}, // DOUBLE_NATURAL
    {SMuFLGlyph.ACCIDENTAL_DOUBLE_FLAT},                            // DOUBLE_FLAT
    {SMuFLGlyph.ACCIDENTAL_DOUBLE_SHARP},                           // DOUBLE_SHARP
    {SMuFLGlyph.ACCIDENTAL_NATURAL, SMuFLGlyph.ACCIDENTAL_FLAT},    // NATURAL_FLAT
    {SMuFLGlyph.ACCIDENTAL_NATURAL, SMuFLGlyph.ACCIDENTAL_SHARP},   // NATURAL_SHARP
};
```

**4b.** Remove the `ACCIDENTAL_PARENTHESIS` array. In SMuFL, parenthesized accidentals are always composed from `ACCIDENTAL_PARENS_LEFT` + accidental components + `ACCIDENTAL_PARENS_RIGHT`.

**4c.** Rewrite `renderAccidental()` to use Bravura font and SMuFLGlyph. The logic simplifies to:
1. Non-parenthesized: draw accidental component(s) from `ACCIDENTAL_COMPONENTS`
2. Parenthesized: draw `ACCIDENTAL_PARENS_LEFT` + accidental component(s) + `ACCIDENTAL_PARENS_RIGHT`

The method needs to:
- Set font to `BRAVURA_FONT` (instead of `ctx.getMusicFont()`)
- For grace notes, apply scale transform instead of switching to a grace font
- Draw each glyph component at the correct X position using advance widths from metadata

**4d.** Rewrite `renderSimpleAccidental()` to draw SMuFL glyphs from the `ACCIDENTAL_COMPONENTS` array, advancing X by the advance width of each glyph.

**4e.** Remove the `BEGIN_PARENTHESIS`, `END_PARENTHESIS` constants from `NoteRenderer`. Use `SMuFLGlyph.ACCIDENTAL_PARENS_LEFT` and `SMuFLGlyph.ACCIDENTAL_PARENS_RIGHT` directly.

**4f.** Remove `MANUAL_PARENTHESIS_Y` constant. SMuFL accidental parentheses are properly baseline-aligned; no manual Y offset should be needed (verify visually).

**Compile and verify visually.**

### ✅ Step 5: Switch accidental width computation to SMuFL metadata

**5a.** Rewrite `initializeAccidentalWidths()` to compute widths from `SMuFLMetadata.getAdvanceWidth()` instead of `FontMetrics.stringWidth()`:

```java
public static void initializeAccidentalWidths(@NotNull Graphics2D g2) {
    if (baseAccidentalWidths != null) return;

    var metadata = SMuFLMetadata.getInstance();
    baseAccidentalWidths = new float[ACCIDENTAL_COMPONENTS.length];

    for (int i = 0; i < ACCIDENTAL_COMPONENTS.length; i++) {
        var components = ACCIDENTAL_COMPONENTS[i];
        float width = 0f;
        for (int c = 0; c < components.length; c++) {
            if (c > 0) width += SPACE_BETWEEN_TWO_ACCIDENTALS;
            Double aw = metadata.getAdvanceWidth(components[c]);
            width += (aw != null) ? (float) StaffSpaces.toPixels(aw) : 0f;
        }
        baseAccidentalWidths[i] = width;
    }

    Double parensLeftWidth = metadata.getAdvanceWidth(SMuFLGlyph.ACCIDENTAL_PARENS_LEFT);
    Double parensRightWidth = metadata.getAdvanceWidth(SMuFLGlyph.ACCIDENTAL_PARENS_RIGHT);
    beginParenthesisWidth = (parensLeftWidth != null) ? (float) StaffSpaces.toPixels(parensLeftWidth) : 0f;
    endParenthesisWidth = (parensRightWidth != null) ? (float) StaffSpaces.toPixels(parensRightWidth) : 0f;

    baseAccidentalParenthesisWidths = new float[ACCIDENTAL_COMPONENTS.length];
    for (int i = 0; i < baseAccidentalParenthesisWidths.length; i++) {
        baseAccidentalParenthesisWidths[i] = baseAccidentalWidths[i]
            + beginParenthesisWidth + endParenthesisWidth;
    }
}
```

**5b.** The `Graphics2D` parameter can potentially be dropped since `FontMetrics` is no longer needed. Evaluate whether to keep for API stability or remove and update callers.

**5c.** `getAccidentalWidth()` and `getAccidentalComponentWidth()` remain largely unchanged since they index into the precomputed arrays.

**Compile and verify.**

### ✅ Step 6: Switch rest rendering in RestRenderer

**6a.** Change `REST_GLYPHS` from `EnumMap<NoteType, String>` to `EnumMap<NoteType, SMuFLGlyph>`:

```java
private static final EnumMap<NoteType, SMuFLGlyph> REST_GLYPHS = new EnumMap<>(NoteType.class);

static {
    REST_GLYPHS.put(NoteType.SEMIBREVE_REST, SMuFLGlyph.REST_WHOLE);
    REST_GLYPHS.put(NoteType.MINIM_REST, SMuFLGlyph.REST_HALF);
    REST_GLYPHS.put(NoteType.CROTCHET_REST, SMuFLGlyph.REST_QUARTER);
    REST_GLYPHS.put(NoteType.QUAVER_REST, SMuFLGlyph.REST_8TH);
    REST_GLYPHS.put(NoteType.SEMIQUAVER_REST, SMuFLGlyph.REST_16TH);
    REST_GLYPHS.put(NoteType.DEMI_SEMIQUAVER_REST, SMuFLGlyph.REST_32ND);
}
```

**6b.** Update `renderElement()` in RestRenderer:
- Replace `g2.setFont(ctx.getMusicFont())` with `g2.setFont(BRAVURA_FONT)`
- Replace `g2.drawString(glyph, 0f, 0f)` with `g2.drawString(glyph.asString(), 0f, 0f)`

**6c.** Update `getRestGlyph()` return type from `String` to `SMuFLGlyph` (or add a new method). The only external caller is `NoteRenderer.renderRestGlyph()`.

**6d.** Validate Y-offsets for rest positioning. Key concern: SMuFL uses distinct glyphs for whole and half rests:
- `REST_WHOLE`: Origin is on the line from which it hangs (4th line = yPos -2 from middle). Current `SEMIBREVE_REST_Y_OFFSET = -2` should be correct.
- `REST_HALF`: Origin is on the line on which it sits (middle line). Current `MINIM_REST_Y_OFFSET = 0` should be correct.

Visual verification is essential.

**6e.** Update `RestRenderer.renderDots()` to use the augmentation dot glyph (as in Step 3b).

**Compile and verify visually.**

### ✅ Step 7: Switch key signature rendering in KeySignatureRenderer

**7a.** Change glyph constants from Fughetta strings to SMuFLGlyph:

```java
private static final SMuFLGlyph FLAT_GLYPH = SMuFLGlyph.ACCIDENTAL_FLAT;
private static final SMuFLGlyph SHARP_GLYPH = SMuFLGlyph.ACCIDENTAL_SHARP;
private static final SMuFLGlyph NATURAL_GLYPH = SMuFLGlyph.ACCIDENTAL_NATURAL;
```

**7b.** Update `renderElement()`, `renderKeySignature()`, and `renderKeySignatureChange()`:
- Replace `g2.setFont(ctx.getMusicFont())` with `g2.setFont(BRAVURA_FONT)`
- Replace `g2.drawString(glyph, ...)` with `g2.drawString(glyph.asString(), ...)`

**7c.** Update `ACCIDENTAL_SPACING` to use SMuFL advance width from metadata:
```java
private static final int ACCIDENTAL_SPACING = (int) Math.ceil(
    StaffSpaces.toPixels(
        SMuFLMetadata.getInstance().getAdvanceWidth(SMuFLGlyph.ACCIDENTAL_SHARP)
    )
) + 1;
```

Or keep as constant if the metadata-derived value is close to 9. Calculate and compare first.

**7d.** Similarly evaluate `KEY_CHANGE_SPACING = 8` against metadata advance widths.

**7e.** Update `getGlyphForKeyType()` to return `SMuFLGlyph` instead of `String`.

**Compile and verify visually.**

### ✅ Step 8: Update NoteRenderer.renderRestGlyph() for backward-compat path

The `render(Graphics2D, Note, int)` backward-compatibility method calls `renderRestGlyph()` which currently uses `MUSIC_FONT`. Update to use `BRAVURA_FONT` and `SMuFLGlyph`:

```java
private void renderRestGlyph(@NotNull Graphics2D g2, @NotNull NoteType noteType) {
    try (var ignored = GraphicsState.save(g2, FONT)) {
        g2.setFont(BRAVURA_FONT);
        SMuFLGlyph glyph = RestRenderer.getRestGlyph(noteType);
        if (glyph != null) {
            g2.drawString(glyph.asString(), 0f, 0f);
        }
    }
}
```

**Compile and verify.**

### ✅ Step 9: Update TempoRenderer and BeatChangeRenderer callers

These renderers call `NoteRenderer.getNoteHeadChar()` and use the returned string with `g2.drawString()` using the Fughetta font. Since `getNoteHeadChar()` will now return Bravura codepoints, these renderers need to set their font to `BRAVURA_FONT` when drawing note heads.

**9a.** In `TempoRenderer.paintSimpleTempoNote()`: change the font from Fughetta to Bravura when drawing the note head. Flag drawing (`"\uf06a"`) remains Fughetta for now (flags are Phase 6 scope).

**9b.** In `TempoRenderer.getTempoNoteBounds()`: the glyph vector creation uses `font.createGlyphVector(frc, noteHeadChar)`. Update callers to pass `BRAVURA_FONT`.

**9c.** In `BeatChangeRenderer.paintSimpleTempoNote()`: same changes as TempoRenderer.

**Compile and verify visually.**

### ✅ Step 10: Clean up Fughetta glyph constants in BaseElementRenderer

Remove or deprecate the Fughetta glyph constants from `BaseElementRenderer` that are no longer referenced:
- `SEMIBREVE_HEAD`, `MINIM_HEAD`, `FILLED_NOTE_HEAD` (note heads)
- `SEMIBREVE_REST`, `MINIM_REST`, `CROTCHET_REST`, `QUAVER_REST`, `SEMIQUAVER_REST`, `DEMISEMIQUAVER_REST` (rests)
- `NATURAL`, `FLAT`, `SHARP`, `DOUBLE_NATURAL`, `DOUBLE_FLAT`, `DOUBLE_SHARP`, `NATURAL_FLAT`, `NATURAL_SHARP` (accidentals)
- `BEGIN_PARENTHESIS`, `END_PARENTHESIS` (parentheses)

Keep `MAIN_UPPER_FLAG`, `SECOND_UPPER_FLAG`, `MAIN_LOWER_FLAG`, `SECOND_LOWER_FLAG`, `GLISSANDO`, `TRILL` for later phases.

Also clean up in NoteRenderer: remove old `ACCIDENTALS` and `ACCIDENTAL_PARENTHESIS` string arrays, `MANUAL_PARENTHESIS_Y`, old `NOTE_DOTS` Ellipse2D array, and `DOT_WIDTH`.

**Compile and verify.**

### ✅ Step 11: Visual verification

Systematically verify every glyph type:

1. **Note heads**: Create/open a score with whole, half, quarter, eighth, sixteenth, thirty-second notes. Verify each note head renders correctly. Check upper and lower stem alignment.
2. **Dots**: Verify single-dotted and double-dotted notes. Check dot positioning for different note types and for notes on lines vs. spaces.
3. **Accidentals**: Test every accidental type (natural, flat, sharp, double-natural, double-flat, double-sharp, natural-flat, natural-sharp). Test parenthesized variants. Test grace note accidentals.
4. **Rests**: Test whole, half, quarter, eighth, sixteenth, thirty-second rests. Verify Y positioning. Test dotted rests.
5. **Key signatures**: Test all key signatures (1-7 sharps, 1-7 flats). Test key changes at end of lines.
6. **Tempo marks**: Verify tempo note rendering (uses getNoteHeadChar). **Note:** Will need to be verified at a later stage because tempo changes are not currently rendering.

---

## Risks and Mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| Bravura glyph metrics differ enough to misalign elements | Medium | Visual verification at each step; keep old constants as comments for comparison |
| Stem X positions wrong with Bravura note heads | Low | Defer stem X recalibration; current values are close to SMuFL anchors |
| Parenthesized accidentals spacing wrong | Medium | Carefully test all parenthesized accidental combinations |
| Rest Y positioning off | Medium | Test all 6 rest types; adjust offsets if needed |
| Color override in drawBravuraGlyph | High | Step 1 addresses this first, before any rendering changes |
| TempoRenderer/BeatChangeRenderer breakage | Medium | Step 9 addresses these callers explicitly |

## Files Modified

| File | Changes |
|---|---|
| `BaseElementRenderer.java` | Add color-preserving drawBravuraGlyph variant; remove unused Fughetta constants |
| `NoteRenderer.java` | Switch NOTE_HEAD to SMuFLGlyph; rewrite accidental rendering; replace dot Ellipse2D with glyph; rewrite accidental width computation; update renderRestGlyph |
| `RestRenderer.java` | Switch REST_GLYPHS to SMuFLGlyph; use Bravura font; replace dot rendering |
| `KeySignatureRenderer.java` | Switch glyph constants to SMuFLGlyph; use Bravura font; metadata-based spacing |
| `TempoRenderer.java` | Update font for note head rendering |
| `BeatChangeRenderer.java` | Update font for note head rendering |
