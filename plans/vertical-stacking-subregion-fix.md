# Fix Vertical Stacking Subregion Heights and Reservation

## Context

The `VerticalStackingCalculator.stackAboveWithRegions()` method stacks composite elements
(tempo markings, ending brackets) that are decomposed into collision subregions. Two issues
prevent correct nestling between elements:

1. **Tempo subregion heights are identical**: Both glyph and text subregions use
   `DEFAULT_HEIGHT_SS` (quarter note bbox height * NOTE_SCALE). The glyph (with stem) is tall;
   the text is shorter. Since both have the same collision height, elements cannot nestle between
   the glyph stem and text of adjacent tempo marks.

2. **Subregion reservation prevents nestling from above**: All subregions reserve at `elementYSs`
   (the unified element top), so later-stacked elements see a uniform ceiling and cannot nestle
   into gaps between short and tall subregions.

Additionally, the text subregion doesn't start at the element top — it starts partway down
(the text baseline is at `ySs + glyphHeight`), but `CollisionRegion` has no `yOffsetSs` to
model this.

## Approach

### Step 1: Add `yOffsetSs` to `CollisionRegion`

**File:** `src/main/java/songscribe/ui/layout/CollisionRegion.java`

Add `yOffsetSs` as the second field:
```java
public record CollisionRegion(
    double xOffsetSs, double yOffsetSs, double widthSs, double heightSs) {}
```

The subregion occupies `[elementY + yOffsetSs, elementY + yOffsetSs + heightSs]` vertically.

### Step 2: Update `Ending.computeCollisionRegions()`

**File:** `src/main/java/songscribe/ui/layout/Ending.java` (lines 210-229)

All ending subregions start at the element top, so pass `yOffsetSs = 0` for all four regions
(bar, left tick, right tick, label). No behavioral change.

### Step 3: Update `TempoAttachment.computeCollisionRegions()`

**File:** `src/main/java/songscribe/ui/layout/TempoAttachment.java` (lines 147-163)

Compute per-subregion heights using font metrics:

- **Glyph**: `yOffsetSs = 0`, `heightSs = DEFAULT_HEIGHT_SS` (unchanged — full glyph with stem)
- **Text**: `yOffsetSs = DEFAULT_HEIGHT_SS - textAscentSs`,
  `heightSs = textAscentSs + textDescentSs`
  (matches the renderer, which draws text baseline at `ySs + DEFAULT_HEIGHT_SS`)

Text height values come from `ScaleContext.getInstance().fromPixels(attrFontMetrics.getAscent())`
and `.fromPixels(attrFontMetrics.getDescent())`.

### Step 4: Update `stackAboveWithRegions()` algorithm

**File:** `src/main/java/songscribe/ui/layout/VerticalStackingCalculator.java` (lines 1085-1131)

**4a. Query phase** — account for `yOffsetSs` in positioning constraint:

```
regionY = ceiling - margin - region.yOffsetSs - region.heightSs
```

(Old formula was `ceiling - margin - region.heightSs`.)

**4b. Reservation phase** — reserve each subregion at its visual bottom:

```
extents.ySet(true, regionXSs, region.widthSs, elementYSs + region.yOffsetSs + region.heightSs)
```

(Old code reserved all subregions at `elementYSs`.)

**4c. Compute overall height from regions** for `DecorationLayout`:

```
overallHeightSs = max(region.yOffsetSs + region.heightSs) across all regions
```

This replaces the `heightSs` parameter. Remove `heightSs` from the method signature.

### Step 5: Update call sites

- `stackEndings()` (line 834): remove `ending.getContentHeightSs()` argument
- `stackTempo()` (line 947): remove `tempo.getContentHeightSs()` argument

### Step 6: Remove debug prints

Remove all `System.out.printf` calls from `stackAbove()` (lines 1062-1067) and
`stackAboveWithRegions()` (lines 1096-1097, 1112-1115, 1120).

## Edge Cases

| Scenario | Behavior |
|---|---|
| Tempo with glyph only (no text) | Single region, yOffset=0, height=DEFAULT_HEIGHT_SS. Same as before. |
| Tempo with text only (no glyph) | Single text region at yOffset=DEFAULT_HEIGHT_SS−textAscent. Overall height = DEFAULT_HEIGHT_SS + textDescent. |
| Ending without closing stroke | Right tick omitted; all remaining regions have yOffset=0. Same as before. |
| Two tempos stacking (adjacent lines) | Upper text can nestle beside lower glyph stem because text subregion has shorter collision height and text-position reservation is shallower. |

## Files Modified

| File | Change |
|---|---|
| `CollisionRegion.java` | Add `yOffsetSs` field |
| `Ending.java` | Pass `yOffsetSs = 0` to all CollisionRegion constructors |
| `TempoAttachment.java` | Per-subregion heights with yOffsets; text height from font metrics |
| `VerticalStackingCalculator.java` | Algorithm changes (query, reserve, overall height); remove `heightSs` param; remove debug prints |

## Files NOT Modified

- `TempoRenderer.java` — rendering is already correct; fix is purely in collision modeling
- `LineRenderer.java` — debug rect uses `layout.heightSs()` which will now be the correct overall height
- `LayoutResult.java` — `DecorationLayout` record unchanged

## Verification

1. Compile: `./scripts/compile.sh`
2. Run unit tests: `./scripts/test.sh unit`
3. Run app with `DEBUG=1 ./scripts/crun.sh`, enable "Show Layout Boxes" in debug menu
4. Verify: tempo text subregions have shorter collision rects than glyph subregions
5. Verify: two tempo marks on adjacent lines exhibit text nestling (image #14 scenario)
6. Verify: pp dynamic has content gap from tempo text bottom (image #13 fix)
7. Verify: fermata nestles inside ending bracket (image #15 scenario)
