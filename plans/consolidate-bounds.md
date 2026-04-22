## Background

`Bounds` and `ElementBounds` are parallel box-model classes with significant overlap. Consolidating them removes duplication, and renaming `ElementBounds` → `ElementBoundsSs` enforces the unit convention throughout the layout layer. Several unit-labelling and unit-mixing bugs should be fixed at the same time.

## Tasks

### 1. Consolidate `Bounds` into `ElementBounds`

Add the three methods from `Bounds` that `ElementBounds` currently lacks:

- `collapsedMarginWith(ElementBounds below)` — CSS-style bottom/top margin collapsing
- `toArea()` — converts margin bounds to `java.awt.geom.Area` for irregular collision shapes
- `withMargin(Rectangle2D contentBounds, Margin margin)` — factory overload that accepts a `Margin` record (staff-space values)

Update callers:

- `LayoutResult` — replace `Bounds` with `ElementBounds`
- `BaseElementRenderer` — same

The hit-test difference dissolves automatically: notes where padding equals content behave exactly like `Bounds.containsPoint()` with zero expansion; cases that want hit expansion pass content-expanded padding bounds at construction time.

Once callers are updated, **delete `Bounds.java`**.

### 2. Rename `ElementBounds` → `ElementBoundsSs`

Use JetBrains rename refactoring so all references are updated automatically.

### 3. Add `Ss` suffix throughout `ElementBoundsSs`

All fields and parameters that carry staff-space values but currently lack the `Ss` suffix should be renamed:

- Fields: `contentBounds`, `paddingBounds`, `marginBounds`, `visualBounds`
- Factory parameters: `padding`, `margin` in `uniform(…)`; `dxSs`/`dySs` in `translate` (already correct); any others missing the suffix
- Getters: `getTop`, `getBottom`, `getLeft`, `getRight`, `getWidth`, `getHeight`, `getMarginTop`, `getMarginBottom`, `getMarginLeft`, `getMarginRight` → suffix each with `Ss`

### 4. Fix `Bounds.DEFAULT_HIT_EXPANSION` unit bug

`DEFAULT_HIT_EXPANSION = 3.0` is documented as pixels but is used where a staff-space expansion is expected in `containsPoint`. 3 ss ≈ 18 px — far too large.

- Rename to `DEFAULT_HIT_EXPANSION_PX` (it is genuinely a pixel value)
- Fix the `containsPoint` call site: convert to staff spaces before use, or restructure so the expansion is applied correctly

### 5. Fix `Bounds` / `ElementBounds` Javadoc unit labels

The following factory methods document their spacing parameters as "in pixels" but the class operates in staff spaces:

- `Bounds.withUniformMargin(…, double margin)` — param says "pixels"
- `Bounds.withMargin(…, top, right, bottom, left)` — all four params say "pixels"
- `ElementBounds.uniform(…, double padding, double margin)` — both params say "pixels"

Update all three to say "in staff spaces".

The `getMarginCss()` / `getPaddingCss()` debug formatters output `"px"` suffixes on staff-space values — update labels to `"ss"`.

### 6. Fix `Attribution` constructor unit bug

```java
// BUG: toRoundedPixels converts ss → px, then passes px where ss is expected
setMarginSs(0, 0, ScaleContext.getInstance().toRoundedPixels(LayoutStylesheet.ATTRIBUTION_MARGIN_BOTTOM_SS), 0);
```

`setMarginSs` stores values in the `marginBottomSs` field. `toRoundedPixels(2.0 ss)` produces ≈ 16 — an 8× inflated margin. Fix:

```java
setMarginSs(0, 0, LayoutStylesheet.ATTRIBUTION_MARGIN_BOTTOM_SS, 0);
```

### 7. Clarify `AttachmentLayout` mixed-unit design

`AttachmentLayout` holds `Point positionPx` (pixels, for rendering) alongside `ElementBounds bounds` (staff spaces, for hit testing). The mix is intentional but undocumented. Add a class-level comment explaining that `positionPx` is the draw-time coordinate and `bounds` is the hit-test region in staff spaces, so that future callers construct `ElementBounds` consistently in staff spaces.

## Scope

- `src/main/java/songscribe/ui/layout/Bounds.java` — add methods, then delete
- `src/main/java/songscribe/ui/layout/ElementBounds.java` → `ElementBoundsSs.java`
- `src/main/java/songscribe/ui/layout/Attribution.java` — fix constructor
- `src/main/java/songscribe/ui/layout/AttachmentLayout.java` — add clarifying comment
- `src/main/java/songscribe/ui/renderer/LayoutResult.java` — update type references
- `src/main/java/songscribe/ui/renderer/BaseElementRenderer.java` — update type references
