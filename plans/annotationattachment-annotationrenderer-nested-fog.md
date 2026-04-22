# Update AnnotationAttachment & AnnotationRenderer to Modern Layout/Rendering System

## Status

Revised 2026-04-21 after zoom-preparation phases 1–8 landed. The height/stacker
portions of the original plan (problem #4, Step 1 body, Step 2) were completed
as zoom-preparation **Phase 7** (font-driven `AnnotationAttachment` height) and
**Phase 6** (`parentLine` propagation). This plan now narrows to the remaining
work: one constructor bug, the `AnnotationRenderer` modernization, and the
`scaleFont` consolidation on `BaseElementRenderer`.

OQ-1 and OQ-2 are resolved (see notes under **Open Questions**). OQ-3 remains
open but has a clear recommendation backed by `BaseElementRenderer`'s existing
helper conventions.

---

## Context

`AnnotationAttachment` and `AnnotationRenderer` predate the modern
layout/rendering system established by the `TempoChange*` pair. The original
audit found four concrete problems. Three are now fixed:

1. ~~X positioning is legacy/incorrect.~~ — still present in
   `AnnotationRenderer.getAnnotationXPosPx`. `CROTCHET_WIDTH_PX` centering
   ignores `DecorationLayout.xSs()` that the stacker already computed.
2. ~~Y baseline is drawn above the allocated region.~~ — still present.
   `AnnotationRenderer.getAnnotationYPosPx` returns the raw
   `decorationLayout.ySs()` (region top) as the baseline, so letter bodies
   draw above the reserved region.
3. ~~Font is not scaled for staff-space coordinates.~~ — still present.
   `AnnotationRenderer.renderElement` sets `composition.getAnnotationFont()`
   directly; the ss→px scale transform on `Graphics2D` makes the text huge.
   `MetronomeRenderer.scaleAttrFont` has the correct conversion but is not
   reusable from `AnnotationRenderer`.
4. ~~`DEFAULT_HEIGHT_SS = 1.75` is wrong.~~ **FIXED in zoom-preparation
   Phase 7.** `AnnotationAttachment` now has `computeContentHeightSs(FontMetrics)`
   and a no-arg `getContentHeightSs()` that resolves
   `parentLine.getComposition().getAnnotationFontMetrics()`, calling
   `RuntimeError.exit` if `parentLine` is null. `SystemStacker.stackAnnotations`
   already passes the metrics to both width and height calls.

Additionally, `AnnotationAttachment`'s 3-arg constructor still contains a
duplicate `setOwnerElement(parent)` call — once on the unconditional line and
again inside the `if (parent != null)` block.

**Note on `TempoChangeRenderer`:** Earlier drafts compared Annotation's
baseline problem to Tempo's `ySs + QUARTER_NOTE_HEIGHT_SS`. These are not the
same bug. The tempo formula aligns the text baseline with the quarter-note
glyph baseline — deliberate typography, not a latent error. Read-only
reference here.

---

## Critical Files

| File | Change |
|------|--------|
| `src/main/java/songscribe/ui/layout/AnnotationAttachment.java` | Fix duplicate `setOwnerElement` in 3-arg constructor |
| `src/main/java/songscribe/ui/renderer/AnnotationRenderer.java` | Full modernization: delete `CROTCHET_WIDTH_PX`, delete `getAnnotationXPosPx`, delete `getAnnotationYPosPx`, delete dead `renderAnnotation` wrapper, rewrite `renderElement` |
| `src/main/java/songscribe/ui/renderer/BaseElementRenderer.java` | Add `protected static Font scaleFont(Font)` |
| `src/main/java/songscribe/ui/renderer/MetronomeRenderer.java` | Make `scaleAttrFont` `protected static`; rename → `scaleFont`; delete the now-redundant override |

**Read-only reference:**
- `MetronomeAttachment.java` — constructor pattern
- `TempoChangeRenderer.renderTempoChange` — template for
  decoration-layout-driven X and scaled-font usage. **NOT** a template for
  baseline calculation (see note above).

---

## Implementation Steps

### Step 1 — `AnnotationAttachment`: fix duplicate `setOwnerElement`

**Sonnet suitability:** ✅ Easy. Single-line deletion inside one constructor.
Zero design judgment. The surrounding contract (when `parent != null`) is
already clear from the existing code.

The 3-arg constructor calls `setOwnerElement(parent)` unconditionally, then
calls it again inside `if (parent != null)`. Remove the inner duplicate:

```java
public AnnotationAttachment(@Nullable StaffElement parent, Annotation annotation) {
    this.annotation = annotation;
    setOwnerElement(parent);
    setAlignment(Alignment.LEFT);

    if (parent != null) {
        setParentLine(parent.getParentLine());
    }
}
```

Nothing else in this file needs to change — `computeContentHeightSs`,
`getContentHeightSs`, and `getContentHeightPx` are already correct after
zoom-preparation Phase 7.

### Step 2 — `BaseElementRenderer`: add `scaleFont(Font)`

**Sonnet suitability:** ✅ Easy. One new static method with a verbatim body
copy from `MetronomeRenderer.scaleAttrFont`. Insertion location is named
(near the other static helpers). No references to update in this step.

Add next to the other static helpers (`layoutYToComponentYSs`,
`centeredGlyphX`, `glyphOriginYFromLayoutTop`):

```java
/** Returns {@code font} scaled from pixel units to staff-space units. */
protected static Font scaleFont(Font font) {
    return font.deriveFont(
        (float) ScaleContext.getInstance().fromPixels(font.getSize()));
}
```

Formula is identical to `MetronomeRenderer.scaleAttrFont`. Static matches the
rest of the helper surface on this class. See **OQ-3**.

### Step 3 — `MetronomeRenderer`: consolidate into `BaseElementRenderer.scaleFont`

**Sonnet suitability:** ✅ Easy, with one ordering caveat. The four-step
sequence below is fully mechanical and uses `jet_brains_rename` /
`jet_brains_safe_delete` which handle references automatically. The only
risk — attempting the rename before making the subclass method static —
would produce a compile error, not silent breakage. An explicit "compile
after each step" instruction when briefing Sonnet eliminates even that risk.

Order matters: static and instance methods with the same name cannot coexist
across a superclass / subclass boundary. Sequence:

1. Make `MetronomeRenderer.scaleAttrFont` `protected static` (body doesn't
   use instance state — trivial). Compile.
2. Add `BaseElementRenderer.scaleFont` from Step 2. Compile.
3. `jet_brains_rename` `MetronomeRenderer.scaleAttrFont` → `scaleFont`. Two
   call sites (`MetronomeRenderer.drawDurationEquals:174`,
   `TempoChangeRenderer.renderTempoChange:89`) update automatically. Static
   methods with the same signature in super and subclass "hide" rather than
   override, so this compiles with the subclass method still present.
4. `jet_brains_safe_delete` the subclass `scaleFont`. The two callers above
   inherit from `BaseElementRenderer`.
5. Compile → `./scripts/test.sh unit`.

### Step 4 — `AnnotationRenderer`: full modernization

**Sonnet suitability:** ⚠️ Moderate. The deletions are mechanical
(`jet_brains_safe_delete` per symbol; zero-caller status confirmed during
planning). The rewrite requires understanding three things simultaneously:
(a) the ss-space `Graphics2D` transform means font metrics must pass through
`ScaleContext.fromPixels` after the font is already scaled; (b) the baseline
lives at `regionTop + ascentSs`, not at the bottom of the region;
(c) `decorationLayout.xSs()` is authoritative — do not re-center. Sonnet
can execute from the provided body verbatim, but if asked to "recompute the
baseline" or "re-center" on its own it is likely to reintroduce one of the
three bugs. Brief with the exact method body and an instruction not to
adjust the constants. Visual verification is human-only.

Delete:
- `CROTCHET_WIDTH_PX` constant — legacy positioning.
- `getAnnotationXPosPx(Graphics2D, StaffElement)` — replaced by
  `decorationLayout.xSs()`.
- `getAnnotationYPosPx(StaffElement, ElementRenderContext)` — replaced by
  the inlined baseline calculation below.
- `renderAnnotation(Graphics2D, StaffElement, ElementRenderContext)` — dead
  (confirmed zero callers via `jet_brains_find_referencing_symbols` on
  2026-04-21). `LineRenderer` calls `.render(...)` directly.

Rewrite `renderElement`:

```java
@Override
protected void renderElement(
    StaffElement element,
    Graphics2D g2,
    ElementRenderContext ctx
) {
    var annotation = element.getAnnotation();

    if (annotation == null) {
        return;
    }

    var decorationLayout = ctx.getLayoutResult().findAttachmentDecorationLayout(
        element, AnnotationAttachment.class);

    if (decorationLayout == null) {
        throw new IllegalStateException(
            "No DecorationLayout found for AnnotationAttachment on note");
    }

    try (var ignored = GraphicsState.save(g2, FONT, COLOR)) {
        g2.setFont(scaleFont(ctx.getComposition().getAnnotationFont()));
        applyDecorationColor(g2, element, ctx);

        var metrics = g2.getFontMetrics();
        double ascentSs = ScaleContext.getInstance().fromPixels(metrics.getAscent());
        double xSs = decorationLayout.xSs();
        double baselineYSs = layoutYToComponentYSs(decorationLayout.ySs(), ctx) + ascentSs;

        g2.drawString(annotation.getAnnotation(), (float) xSs, (float) baselineYSs);
    }
}
```

Key corrections vs. the current renderer:

- **X is `decorationLayout.xSs()`**, not a hand-rolled centering off
  `note.getXOffsetPx()`. The stacker already centered it.
- **Baseline is `regionTop + ascentSs`**, NOT `regionTop`. The text baseline
  sits between ascent and descent; without `ascentSs` letter bodies draw
  above the allocated region.
- **Font is scaled.** `scaleFont` converts pixel font size to ss so the
  `Graphics2D` ss→px transform produces the right on-screen size.
- **`ascentSs` comes from the scaled font's metrics.** Metrics read from the
  already-set scaled font are in px at the ss-equivalent pixel value; the
  `fromPixels` call completes the conversion.

---

## Verification

1. **Compile:** `./scripts/compile.sh`.
2. **Unit tests:** `./scripts/test.sh unit`. Expect
   `AnnotationAttachmentTest` (post-Phase-7) and `ManualOffsetStackingTest`
   to pass with no adjustments.
3. **Visual check:** `./scripts/run.sh`. Open a file with annotations and
   confirm:
   - Annotations sit inside the region allocated by the stacker — no letter
     bodies above the top of the region.
   - Annotation text aligns with note columns (centered by the layout
     system, not drifted).
   - Changing the annotation font size via Composition → Fonts produces a
     proportional change in vertical space. This is the real regression test
     for the measurement path — fixed-height code fails it silently. (Phase 7
     already wired the measurement; this visual check confirms the renderer
     respects it.)

If annotations appear in the wrong vertical position, do NOT tune a constant.
Investigate the measurement chain: `Composition.getAnnotationFontMetrics()`
origin → `FontMetrics.getAscent()` / `getDescent()` relationship to visible
glyph bbox.

---

## Decisions Made During Review

| # | Decision | Status |
|---|----------|--------|
| D1 | Delete `DEFAULT_HEIGHT_SS` entirely. | ✅ Done (zoom-prep Phase 7) |
| D2 | Baseline = `regionTop + ascentSs` measured from the scaled font. | Pending (Step 4) |
| D3 | `computeContentHeightSs` takes `FontMetrics`, mirroring `computeContentWidthSs`. | ✅ Done (zoom-prep Phase 7) |
| D4 | No new stacker plumbing — `SystemStacker.stackAnnotations` already retrieves `FontMetrics`. | ✅ Done (zoom-prep Phase 7) |
| D5 | `TempoChangeRenderer` is NOT the same class of bug. Do not "fix" its `ySs + QUARTER_NOTE_HEIGHT_SS`. | Honored |
| D6 | `scaleFont` consolidation into `BaseElementRenderer`. | Pending (Steps 2–3) |
| D7 | `renderAnnotation` wrapper is dead code. | Pending (Step 4) |
| D8 | `getAnnotationYPosPx` becomes unreachable after the rewrite. | Pending (Step 4) |
| D9 | `LineElement.getContentBounds`/`getMarginBounds` unit-mixing is out of scope. | ✅ Resolved (zoom-prep Phases 3, 4, 8) |

---

## Open Questions

### OQ-1 — `AnnotationAttachment.getContentHeightPx()` — ~~RESOLVED~~

Resolved by zoom-preparation Phase 7 choosing option A: `getContentHeightSs()`
resolves `parentLine.getComposition().getAnnotationFontMetrics()` and fails
loud via `RuntimeError.exit` if `parentLine` is null. Phase 6 makes the
propagation reliable.

### OQ-2 — Sequencing relative to the pixel-unit-mixing refactor — ~~RESOLVED~~

Resolved. The pixel-unit-mixing refactor landed as zoom-preparation phases
1–8. This PR is the follow-up that works on top of the cleaned surface.

### OQ-3 — `BaseElementRenderer.scaleFont` — static or instance?

`MetronomeRenderer.scaleAttrFont` is currently `protected` (instance). The
method body uses no instance state.

- **Static** matches every other helper on `BaseElementRenderer` that was
  already audited for this PR: `layoutYToComponentYSs` (both overloads),
  `layoutXToComponentXSs`, `centeredGlyphX`, `glyphOriginYFromLayoutTop`,
  `staffLineToYSs`, `noteStaffPositionToCoordinateSs`, `drawLedgerLine`.
- **Instance** would force every subclass helper in the class to justify
  why it *isn't* static — inconsistent.

**Recommendation: static.** Also simplifies the Step 3 sequence — making the
subclass method static first lets it "hide" cleanly until the delete.

### OQ-4 — Shadow-vs-override interaction — mechanical consequence of OQ-3

Once OQ-3 resolves to static, both methods are static and hiding is legal.
Step 3's ordering (make subclass static → add superclass → rename →
safe-delete) avoids any transient illegal state. No real open question.

---

## Cross-References

- `plans/zoom-preparation.md` — the umbrella plan that absorbed the height
  and stacker portions (Phases 6, 7).
- `.claude/rules/unit-conversion.md` — `ScaleContext` authoritative converter.
- `.claude/rules/code-styles/java-kotlin.md` — unit suffix rules,
  no-logic-duplication rule.
- `.claude/rules/serena.md` — semantic tool usage for the refactor steps
  (`jet_brains_rename`, `jet_brains_safe_delete`).
