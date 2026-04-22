# Investigation — Pixel Units Still Present In The Layout API

**Status:** Research only. Do not write a plan or make code changes from this
file. Hand findings back when research is complete; a new plan will be written
in a separate context after review.

---

## Context

Review of PR branch `293-update-annotations` surfaced that
`AnnotationAttachment`'s fixed `DEFAULT_HEIGHT_SS = 1.75` is wrong in principle
(variable-content text cannot have a fixed height constant). While discussing
the fix, we discovered a deeper issue: `LineElement`'s bounds API mixes
staff-space and pixel units inside the same `Rectangle2D`. This is almost
certainly not isolated to `LineElement`. A full audit is needed before any
refactor is proposed.

This document is the brief for that audit. Everything below is evidence and
open questions — not decisions.

---

## The Smoking Gun

`src/main/java/songscribe/ui/layout/LineElement.java:271–289`:

```java
public Rectangle2D getContentBounds() {
    return new Rectangle2D.Double(
        positionSs.getX(),    // staff spaces
        positionSs.getY(),    // staff spaces
        getContentWidthPx(),  // pixels   <-- wrong unit
        getContentHeightPx()  // pixels   <-- wrong unit
    );
}

public Rectangle2D getMarginBounds() {
    return new Rectangle2D.Double(
        positionSs.getX() - marginLeftSs,                         // ss
        positionSs.getY() - marginTopSs,                          // ss
        getContentWidthPx()  + marginLeftSs + marginRightSs,      // px + ss + ss
        getContentHeightPx() + marginTopSs  + marginBottomSs      // px + ss + ss
    );
}
```

Downstream consumers treat these bounds as staff-space:

- `CollisionDetector.calculateNoteExtent()` — subtracts `staffMiddleY` (ss) from
  `bounds.getMinY()`, at four call sites (note, attachment, articulation,
  range).
- `LineElement.containsPoint(double x, double y, double expansion)` —
  unit-ambiguous hit testing.
- `BaseElementRenderer.getBounds(T element, ElementRenderContext)` — returns
  `element.getContentBounds()` as the renderer's reported bounds.

At the current hardcoded `ScaleContext.PIXELS_PER_STAFF_SPACE = 8`, the bug is
**latent** — the numeric relationship is fixed, so collision detection and hit
testing produce consistent (if scaled-by-8) results, and visual output looks
correct. The moment `ScaleContext` gains zoom support (a stated design goal in
`.claude/rules/unit-conversion.md`), the mixed-unit arithmetic becomes
wrong-by-factor rather than wrong-by-offset, and silent regressions appear in
collision, hit testing, and renderer bounds reporting.

---

## Starting Hypothesis (to be validated, not assumed)

The abstract signatures on `LineElement` are pixel-based because they predate
the move to staff-space throughout the layout system. The expected shape of the
refactor is:

```java
// LineElement — abstract becomes Ss
public abstract double getContentWidthSs();
public abstract double getContentHeightSs();

// LineElement — Px becomes concrete delegation
public double getContentWidthPx()  {
    return ScaleContext.getInstance().toPixels(getContentWidthSs());
}
public double getContentHeightPx() {
    return ScaleContext.getInstance().toPixels(getContentHeightSs());
}

// getContentBounds / getMarginBounds return uniform-Ss Rectangle2D.
```

This hypothesis has two wrinkles worth naming up front:

1. **Some subclasses have non-constant heights.** `AnnotationAttachment.getContentHeightSs()`
   must measure the actual annotation font (`ascent + descent`), not return a
   constant. Any refactor that assumes "every override is a constant" breaks
   Annotation.

2. **Some subclasses have legitimate sub-region structure.** `MetronomeAttachment`
   combines a fixed glyph region (quarter-note bbox) with variable text (tempo
   description). Its "height" question is actually "what is the bounding
   region that accommodates both?" — not a single constant.

---

## Known Subclasses With Px Overrides

From a Grep on `getContentHeightPx`, there are at least 15 concrete overrides
(as of 2026-04-21):

```
src/main/java/songscribe/music/StaffElement.java:248
src/main/java/songscribe/ui/layout/AnnotationAttachment.java:134
src/main/java/songscribe/ui/layout/Articulation.java:153
src/main/java/songscribe/ui/layout/Attribution.java:107
src/main/java/songscribe/ui/layout/Clef.java:62
src/main/java/songscribe/ui/layout/DynamicAttachment.java:171
src/main/java/songscribe/ui/layout/Ending.java:638
src/main/java/songscribe/ui/layout/FermataAttachment.java:89
src/main/java/songscribe/ui/layout/Hairpin.java:125
src/main/java/songscribe/ui/layout/KeySignature.java:116
src/main/java/songscribe/ui/layout/MetronomeAttachment.java:125
src/main/java/songscribe/ui/layout/Staff.java:66
src/main/java/songscribe/ui/layout/Tie.java:75
src/main/java/songscribe/ui/layout/Trill.java:115
src/main/java/songscribe/ui/layout/Tuplet.java:112
```

Many return hardcoded constants. Whether the constant is *actually* a pixel
value (needing conversion) or was always a staff-space value mislabeled (just
needing a rename) is per-subclass and requires inspection.

---

## Research Questions

Answer these before drafting a plan.

### 1. Full type hierarchy

Enumerate every concrete and abstract subtype of `LineElement`, including
transitive subtypes through `Attachment`, `RangeElement`, `StaffElement`, and
any others. Record:

- Current `getContentWidthPx()` / `getContentHeightPx()` implementation
  (body, not just signature).
- For each returned constant: what does the number represent? Is it derived
  from a SMuFL bbox, a UI convention, or something else? Is it actually px,
  or ss-mislabeled?
- For each non-constant computation: what inputs drive the result? Where do
  those inputs come from?

Use `jet_brains_type_hierarchy(name_path="LineElement",
relative_path="src/main/java/songscribe/ui/layout/LineElement.java",
hierarchy_type="sub")` as the entry point. Do NOT Grep for `extends LineElement`
— Serena hierarchy is canonical.

### 2. Caller audit

For every call site of `getContentBounds`, `getMarginBounds`, `getBounds`, and
`containsPoint` on `LineElement` or its subtypes:

- What coordinate space is the caller operating in (ss or px)?
- What space does the caller assume the returned bounds are in?
- Is the current behavior accidentally correct (because at scale=8 the numbers
  happen to work), or intentionally px-aware (deliberately mixing)?

Known call sites to start from:

- `CollisionDetector.calculateNoteExtent` (SystemStacker package).
- `LineElement.containsPoint`.
- `BaseElementRenderer.getBounds`.

### 3. Other unit-mixing hotspots

Beyond `LineElement`, the codebase likely has other places where px and ss
leak across each other. Search for:

- Fields with names like `*X`, `*Y`, `*Width`, `*Height` that lack the
  required `Ss` / `Px` / `Sp` suffix (see `.claude/rules/code-styles/java-kotlin.md`).
- Method parameters and return types with raw `double` positional values and
  no suffix.
- Arithmetic that combines variables of different units (hard to detect by
  pattern; best found by looking at classes that deal with geometry).
- Remaining `StaffSpaces.toPixels()` / `StaffSpaces.fromPixels()` calls (the
  class is deprecated — see `.claude/rules/unit-conversion.md` — and any that
  block the refactor should be noted).

### 4. Rectangle2D contract

Does any downstream consumer of `getContentBounds()` / `getMarginBounds()`
assume pixel precision from the `Rectangle2D`? For example, any code that
rasterizes the bounds for drawing or hit-tests against pixel coordinates from
mouse events? `Rectangle2D.Double` is unit-agnostic geometry — any consumer
that treats it as pixels is itself a bug to flag.

### 5. Caching and zoom

- Does any subclass cache its bounds? (Search for fields of type `Rectangle2D`
  on `LineElement` subtypes.)
- If so, is the cache invalidated on any state change that would affect
  bounds? Would zoom changes need to invalidate it?
- Does `ScaleContext` currently expose change events? If not, are any callers
  assuming the scale is immutable?

### 6. Test coverage

- Which unit tests assert numeric widths/heights or bounds coordinates? Are
  any of them locking in the current px values (and therefore will need updating
  alongside the refactor)?
- Do any e2e tests depend on pixel-precise layout for mouse-coordinate
  assertions? If so, they're insulated from the unit mixup by virtue of running
  at the fixed default scale — which means they won't catch the zoom regression
  either.
- Is there a test that varies `ScaleContext.pixelsPerStaffSpace` to catch
  zoom-time regressions? If not, that's itself a gap worth noting.

### 7. AnnotationAttachment specifics

- Where does `Composition.getAnnotationFontMetrics()` get its `FontMetrics`?
  Does it use `GraphicUtils.LAYOUT_FRC`? Is it cached? When is it invalidated
  on font change?
- What is the call ordering between `setParentLine` and the first
  `getContentHeightSs` / `getContentBounds` query for an attachment? Is there
  a window where an attachment can exist without a parent and still be queried
  for bounds?
- Are there any paths that construct an `AnnotationAttachment` via the 1-arg
  constructors (`String text` or `Annotation annotation`) and then immediately
  query bounds without setting a parent? If yes, that path needs a defined
  failure mode.

### 8. MetronomeAttachment / TempoChangeRenderer

The tempo path uses `ySs + QUARTER_NOTE_HEIGHT_SS` as a text baseline
(`TempoChangeRenderer.java:75`). This is a deliberate typographic choice
(align text baseline with glyph baseline), not the same class of bug as
Annotation. Confirm this is the correct interpretation, and whether the
overall attachment region height adequately accommodates tall attribution
fonts.

---

## Tooling

- Use Serena semantic tools first for all exploration. See
  `.claude/rules/serena.md`.
- `jet_brains_type_hierarchy` for subtype enumeration.
- `jet_brains_find_referencing_symbols` for call-site audits.
- `jet_brains_find_symbol` with `include_body=true` for reading specific
  overrides.
- Grep is appropriate only for unit-suffix pattern searches (step 3) and for
  finding raw numeric literals that should have been named constants.

---

## Constraints And Landmines

- **Do not opportunistically migrate `StaffSpaces` call sites** as part of
  this refactor. Deprecation migration is a separate track — see
  `.claude/rules/unit-conversion.md`. Only touch them if they actively block
  the unit-fix work.
- **`positionSs` on `LineElement` is already staff-space** and is not part of
  this refactor. The issue is the width/height fields, not the position.
- **`AnnotationAttachment.getContentHeightSs` cannot be a constant.** Any
  refactor that assumes every subclass override is trivial will regress
  Annotation. The explicit-`FontMetrics` parameter pattern already established
  by `computeContentWidthSs(FontMetrics)` at
  `AnnotationAttachment.java:115` is the reference pattern to preserve; a
  no-arg abstract override resolves metrics via
  `parentLine.getComposition().getAnnotationFontMetrics()` and fails loud
  (`RuntimeError.exit`) when parent is unset.
- **`MetronomeAttachment` has sub-region structure**, not a single
  height. Whatever the refactor does, it must not flatten that structure.
- **Zoom is the forcing function.** The refactor is not motivated by current
  visible bugs. It is motivated by the known-upcoming zoom feature turning
  latent bugs into visible ones. Keep that framing in the plan that emerges
  from this research.

---

## Starting Points For A Fresh Context

Read in this order:

1. `plans/annotationattachment-annotationrenderer-nested-fog.md` — the PR that
   surfaced this.
2. `.claude/rules/unit-conversion.md` — the `ScaleContext` / `StaffSpaces`
   rules.
3. `.claude/rules/code-styles/java-kotlin.md` — the unit-suffix convention.
4. `src/main/java/songscribe/ui/layout/LineElement.java` — especially lines
   90–120 (abstract signatures) and 265–320 (bounds and hit testing).
5. `src/main/java/songscribe/ui/layout/stacking/SystemStacker.java` — around
   `stackAnnotations` for the reference `FontMetrics`-plumbed pattern.
6. `src/main/java/songscribe/ui/layout/AnnotationAttachment.java` — the
   motivating non-constant case.
7. `src/main/java/songscribe/ui/layout/MetronomeAttachment.java` — the
   sub-region case.

---

## What Success Looks Like

The research phase produces:

1. A complete subclass inventory with current implementations and proposed Ss
   values for each.
2. A caller audit identifying any consumer that is currently accidentally
   correct.
3. A list of non-constant subclasses needing the `AnnotationAttachment`-style
   self-resolution pattern.
4. Any newly discovered unit-mixing hotspots outside `LineElement`.
5. A statement of test gaps — especially whether the codebase needs a
   scale-varying test harness to catch zoom regressions.

From that inventory, a plan can be written. Until the inventory is complete,
the plan would be speculative.
