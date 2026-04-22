# Pixel/Unit Mixing — Research Findings

**Branch:** `293-update-annotations`
**Date:** 2026-04-21
**Status:** Research complete. Ready for plan authoring.

---

## Q1 — Full LineElement Subclass Inventory

### Type hierarchy

```
LineElement  (abstract)
├── Clef
├── Attribution
├── Articulation
├── Staff
├── KeySignature
├── RangeElement  (abstract)
│   ├── Tuplet
│   ├── Tie
│   ├── Ending
│   ├── Trill
│   └── Hairpin  (abstract)
│       ├── Crescendo   — no override; inherits Hairpin
│       └── Diminuendo  — no override; inherits Hairpin
├── StaffElement
│   └── StructuralElement — no override; inherits StaffElement
└── Attachment  (abstract)
    ├── AnnotationAttachment
    ├── FermataAttachment
    ├── DynamicAttachment
    └── MetronomeAttachment  (abstract)
        ├── TempoChangeAttachment — no override; inherits MetronomeAttachment
        └── BeatChangeAttachment  — no override; inherits MetronomeAttachment
```

### Implementation table

| Class | `getContentWidthPx()` | `getContentHeightPx()` | Unit assessment | Notes |
|---|---|---|---|---|
| **LineElement** | `abstract` | `abstract` | — | Javadoc says "pixels"; name says Px |
| **Clef** | `toPixels(CONTENT_WIDTH_SS)` | `toPixels(CONTENT_HEIGHT_SS)` | ss internally, converted at boundary | Constants from `SMuFLMetadata.requireBBox(G_CLEF)` |
| **Attribution** | `text.length() * CHAR_WIDTH_PX` (8.0) | `TEXT_HEIGHT_PX` (16.0) | Raw pixel stubs — not font-driven | Javadoc flags as placeholder; width is char-count × constant, height is bare literal |
| **Articulation** | `toPixels(isStaccato() ? STACCATO_WIDTH_SS : ACCENT_WIDTH_SS)` | `toPixels(isStaccato() ? STACCATO_HEIGHT_SS : ACCENT_HEIGHT_SS)` | ss internally, converted at boundary | Constants from SMuFL bboxes for staccato/accent glyphs |
| **Staff** | caller-provided `widthPx` | `toPixels(LayoutStylesheet.STAFF_HEIGHT_SS)` | Width: caller px; height: ss converted | Width is the rendered line width set by the layout engine |
| **KeySignature** | `accidentalCount * ACCIDENTAL_WIDTH_PX` (8.0) or 0 | `ACCIDENTAL_HEIGHT_PX` (24.0) or 0 | Raw pixel magic numbers — not SMuFL-derived | `ACCIDENTAL_WIDTH_PX = 8.0`, `ACCIDENTAL_HEIGHT_PX = 24.0` have no explicit physical origin |
| **Tuplet** | `abs(endXSs - anchorXSs) + endElement.getContentWidthPx()` (mixed) | Hard-coded `12.0` (px) | **Unit bug**: width subtracts ss values but returns without `toPixels()`; height is bare pixel literal | No named constant; no SMuFL derivation |
| **Tie** | Same span formula as Tuplet (mixed units) | Hard-coded `8.0` (px) | **Same unit-mixing bug**; height comment says "approximate arc height" | Neither value is SMuFL-sourced |
| **Ending** | Same span formula (mixed units) | `toPixels(getContentHeightSs())` where Ss returns `LayoutStylesheet.VOLTA_TICK_HEIGHT_SS` | Width has same mixing bug; height is properly ss-based | Height better-behaved than Tuplet/Tie |
| **Trill** | `toPixels(TRILL_GLYPH_WIDTH_SS)` | `toPixels(TRILL_GLYPH_HEIGHT_SS)` | ss internally, converted at boundary | |
| **Hairpin** | Same span formula (mixed units) | `toPixels(getContentHeightSs())` where Ss is dynamic (depends on span + NOTE_HEAD_WIDTH_SS) | Width: same mixing bug; height: properly ss-based | Height dynamic: varies with element span |
| **Crescendo** | Inherits Hairpin | Inherits Hairpin | — | Tag class only |
| **Diminuendo** | Inherits Hairpin | Inherits Hairpin | — | Tag class only |
| **StaffElement** | `toPixels(getType().getFullElementWidthSs())` | `toPixels(getType().getElementHeightSs(upper))` | ss internally, converted at boundary | Driven by `ElementType` enum; comment flags for future ss-direct return |
| **StructuralElement** | Inherits StaffElement | Inherits StaffElement | — | |
| **AnnotationAttachment** | `0` (legacy stub; `computeContentWidthSs` is live path) | `toPixels(DEFAULT_HEIGHT_SS)` where `DEFAULT_HEIGHT_SS = 1.75` | Width: intentionally zero; height: ss converted to px | `DEFAULT_HEIGHT_SS = 1.75` is wrong in principle — variable-content text can't have a fixed height |
| **FermataAttachment** | `toPixels(FERMATA_WIDTH_SS)` | `toPixels(FERMATA_HEIGHT_SS)` | ss internally, converted at boundary | Both from `SMuFLMetadata.requireBBox(FERMATA_ABOVE)` |
| **DynamicAttachment** | `toPixels(getContentWidthSs())` where Ss queries `SMuFLMetadata.requireBBox(glyph).width()` or fallback | `toPixels(getContentHeightSs())` where Ss queries SMuFL or fallback | ss internally (SMuFL bbox), converted at boundary | Dynamic type drives glyph selection; `fp` has no glyph and uses `DEFAULT_WIDTH_SS` / `DEFAULT_HEIGHT_SS` |
| **MetronomeAttachment** | `0` | `toPixels(QUARTER_NOTE_HEIGHT_SS)` where Ss = `bbox.height() * NOTE_SCALE` | Width: zero; height: ss (SMuFL bbox post-scaled) | `NOTE_SCALE` shrinks glyph for above-staff display |
| **TempoChangeAttachment** | Inherits MetronomeAttachment (`0`) | Inherits MetronomeAttachment | — | Real width from `computeContentMetrics(FontMetrics)` — font-driven, runtime |
| **BeatChangeAttachment** | Inherits MetronomeAttachment (`0`) | Inherits MetronomeAttachment | — | Real width from `computeContentWidthSs(FontMetrics)` — font-driven |

### Non-constant / font-driven classes needing self-resolution pattern

**AnnotationAttachment** — `DEFAULT_HEIGHT_SS = 1.75` is a wrong constant; must measure actual font `ascent + descent` via `parentLine.getComposition().getAnnotationFontMetrics()`. Width already uses the explicit-`FontMetrics` parameter pattern (`computeContentWidthSs(FontMetrics)`).

**TempoChangeAttachment** and **BeatChangeAttachment** — widths are entirely font-driven; `getContentWidthPx()` returns `0`. Heights are fixed ss × scale factor. Any refactor that calls `getContentWidthPx()` on these will get zero.

**DynamicAttachment** — both dimensions are SMuFL-bbox-driven with a runtime fallback; not a compile-time constant but also not font-driven — no self-resolution needed, SMuFL metadata is available statically.

**StaffElement / StructuralElement** — both dimensions vary by `ElementType` and stem direction; the most parameter-dependent subclasses. Already carry code comment flagging the future ss-direct conversion.

**RangeElements (Tuplet, Tie, Ending, Hairpin)** — widths are inherently span-computed (anchor-to-end-element). This is a different problem from font resolution: it requires anchor/end positions to be resolved, which happens during layout. These classes cannot return a static width; the width is layout-time dependent.

### Per-class: what the numbers represent and where they come from

**Clef** — constants only.
- `CONTENT_WIDTH_SS = bbox.width()` of `SMuFLGlyph.G_CLEF`. Treble clef glyph width from SMuFL metadata (Bravura). Genuinely ss; correctly converted to px at the boundary.
- `CONTENT_HEIGHT_SS = bbox.height()` of the same glyph. Same treatment.
- No unit mislabeling. The `Px` method returns true pixels via `ScaleContext.toPixels`.

**Attribution** — constants only, **not derived from anything physical**.
- `CHAR_WIDTH_PX = 8.0` — a monospace-ish fudge factor; "approximate character width for stub calculations" per the javadoc. At scale=8 it happens to equal 1.0 ss but that is coincidence, not design.
- `TEXT_HEIGHT_PX = 16.0` — a bare literal with no font-metrics basis. At scale=8 equals 2.0 ss; again coincidental.
- Javadoc explicitly flags both as placeholders for a future font-metrics-driven implementation. The values are genuinely px as named, but they are fictional px — they do not correspond to what any actual font will render.

**Articulation** — constants only.
- `STACCATO_WIDTH_SS/HEIGHT_SS = bbox` of `SMuFLGlyph.ARTIC_STACCATO_ABOVE`. SMuFL-derived.
- `ACCENT_WIDTH_SS/HEIGHT_SS = bbox` of `SMuFLGlyph.ARTIC_ACCENT_ABOVE`. SMuFL-derived.
- Return dispatched by `isStaccato()` on `ArticulationType`. Correctly ss→px at the boundary.

**Staff** — mixed: one computed input, one constant.
- Width input: `widthPx` instance field set by the constructor caller. In practice this is the line width produced by the layout engine after justification (`LineJustificationCalculator`), already in pixels from the layout pipeline. Genuinely px but sourced from upstream layout math that itself operates in ss then converts.
- Height constant: `LayoutStylesheet.STAFF_HEIGHT_SS = 4.0` — the semantic height of a 5-line staff (4 inter-line gaps). A staff-topology constant, not SMuFL-sourced; it is a definition, not a measurement. Correctly ss.

**KeySignature** — constants, **neither SMuFL-sourced nor explained**.
- `ACCIDENTAL_WIDTH_PX = 8.0` — a magic number. At scale=8 equals 1.0 ss, which is close to but not the SMuFL bbox width of `ACCIDENTAL_SHARP` (~1.18 ss) or `ACCIDENTAL_FLAT` (~0.94 ss). No javadoc explains the origin.
- `ACCIDENTAL_HEIGHT_PX = 24.0` — a magic number. At scale=8 equals 3.0 ss, close to (but not equal to) the sharp glyph height (~2.68 ss) but well above the flat glyph height (~1.94 ss). No justification.
- Width formula `accidentalCount * ACCIDENTAL_WIDTH_PX` implicitly assumes uniform glyph width and no kerning — wrong in both directions for a mixed sharp/flat key.
- Values are named `Px` but are not px in any principled sense; they are UI fudge.

**Tuplet** — width is computed, height is constant.
- Width inputs: `endElement.getXSs()` and `anchor.getXSs()` (both ss, sourced from layout-resolved positions on `StaffElement`), plus `endElement.getContentWidthPx()` (px, recursively ill-defined because it is the same `getContentWidthPx` contract). **Unit-mixing bug**: an ss difference is added to a px quantity and returned without conversion. Whichever unit a caller treats the result as, one of the two terms is wrong.
- Height constant: `12.0` — bare literal, no named constant, no javadoc, no SMuFL source. Inconsistent with `getContentHeightSs() = 0.7` on the same class (0.7 ss → 5.6 px at scale=8, not 12). Genuinely px but arbitrary.

**Tie** — width is computed, height is constant.
- Width inputs: same span formula as Tuplet, same mixing bug.
- Height constant: `8.0` — bare literal, commented "approximate arc height". Not SMuFL-sourced. Inconsistent with `getContentHeightSs() = 1.0` (= 8 px at scale=8), so the two methods happen to agree only at the default scale.

**Ending** — width is computed, height is ss-backed.
- Width inputs: same span formula as Tuplet/Tie; same mixing bug.
- Height: `toPixels(LayoutStylesheet.VOLTA_TICK_HEIGHT_SS)` where `VOLTA_TICK_HEIGHT_SS = 2.0`. A volta-bracket definition constant (tick leg length), not SMuFL. Correctly ss→px.
- Additional scale-fragile caches: `LABEL_1_BOUNDS_SS` / `LABEL_2_BOUNDS_SS` are `static final Rectangle2D` built from `ENDING_FONT.createGlyphVector(GraphicUtils.LAYOUT_FRC, "1.").getVisualBounds()`. The input is font-driven (AWT glyph-vector metrics against a fixed `FontRenderContext`), baked at class load. Units are ss because the font is sized at `FONT_SIZE * LABEL_FONT_SCALE` and measured against `LAYOUT_FRC`. These participate in Q5's zoom-readiness risk, not in Q1's px/ss mixing.

**Trill** — constants only.
- `TRILL_GLYPH_WIDTH_SS/HEIGHT_SS = bbox` of `SMuFLGlyph.ORNAMENT_TRILL`. SMuFL-derived.
- Correctly ss→px at the boundary.

**Hairpin** — width is computed, height is ss-backed.
- Width inputs: `endElement.getXSs() - anchor.getXSs()` (ss), `endElement.getContentWidthPx()` (px), plus `x1Shift + x2Shift` (user-set integer shifts, documented nowhere but used as pixels — add a third unit ambiguity to the bug). Same mixing failure as Tuplet/Tie/Ending, plus the extra shift terms.
- Height: `toPixels(LayoutStylesheet.HAIRPIN_OPENING_HEIGHT_SS)` where `HAIRPIN_OPENING_HEIGHT_SS = 1.25`. Engraving-convention constant (hairpin opening), not SMuFL. Correctly ss→px. Note that `getSpanWidthSs` uses `max(getContentHeightSs(), …)` — the height constant doubles as a minimum-span floor, a subtle domain coupling.

**Crescendo / Diminuendo** — pure tag classes, zero constants, inherit everything from Hairpin.

**StaffElement** — both dimensions computed, driven by `ElementType`.
- Width input: `getType().getFullElementWidthSs()` → `ElementType.fullWidthSs`, computed once in a static initializer (`computeElementBoundsSs`) from SMuFL `requireBBox(glyph)` of the notehead plus stem-up flag extent (for stemmed types) or bare `bbox.right()` (for unstemmed like semibreve). Genuinely ss.
- Height input: `getType().getElementHeightSs(upper)` → `heightUpSs` or `heightDownSs`, both computed at class-init from SMuFL bbox + stem anchors (`stemUpSE`, `stemDownNW`) plus `LayoutStylesheet.STEM_LENGTH_SS`. Genuinely ss; stem direction flips which value is used.
- Both converted correctly to px. The `upper` flag on the instance drives the height dispatch.
- Grace notes (`GRACE_QUAVER`) additionally multiply by `LayoutStylesheet.GRACE_NOTE_SCALE` at init.

**StructuralElement** — pure tag subclass, inherits StaffElement behavior.

**AnnotationAttachment** — width is stub, height is constant.
- Width: returns `0` as a "legacy pixel API" stub. Real width is the `computeContentWidthSs(FontMetrics)` instance method — inputs `annotation.getAnnotation()` (user text) and `FontMetrics.stringWidth(text)` (in px from the `FontMetrics` supplied by the caller), then `scale.fromPixels(...)` to ss. Font-driven, runtime, caller-supplied.
- Height constant: `DEFAULT_HEIGHT_SS = 1.75` — a fixed assumption that every annotation renders to 14 px tall at scale=8. Not derived from font metrics at all. Wrong in principle: text height depends on the annotation font (which is user-configurable) and specifically on `ascent + descent`. Needs a `computeContentHeightSs(FontMetrics)` peer to the width method.

**FermataAttachment** — constants only.
- `FERMATA_WIDTH_SS/HEIGHT_SS = bbox` of `SMuFLGlyph.FERMATA_ABOVE`. SMuFL-derived.
- Correctly ss→px at the boundary.

**DynamicAttachment** — both dimensions computed at runtime from SMuFL.
- Width input: `type.getGlyph()` (e.g. `DYNAMIC_PIANO`) → `SMuFLMetadata.requireBBox(glyph).width()`. For types without a UI glyph (`SFORZANDO`, `FORTEPIANO`), falls back to `DEFAULT_WIDTH_SS = 2.5`.
- Height input: same pattern, bbox height or `DEFAULT_HEIGHT_SS = 1.75`.
- The two `DEFAULT_*_SS` constants are plausible defaults for un-glyphed types but share AnnotationAttachment's conceptual problem — text-width fallbacks should come from font metrics, not fixed literals. That said, because the entries that fall through are multi-character composites rendered via `ff`+`p`-style composition by the renderer, the fallbacks only participate in collision math and not final visual width.
- Genuinely ss; correctly ss→px at the boundary.

**MetronomeAttachment** — width zero, height constant.
- Width: returns `0`. Real width is computed by subclasses at runtime with a `FontMetrics` parameter.
- Height: `QUARTER_NOTE_HEIGHT_SS = QUARTER_NOTE_BBOX.height() * NOTE_SCALE` where `QUARTER_NOTE_BBOX = requireBBox(MET_NOTE_QUARTER_UP)` and `NOTE_SCALE = FlatLafProps.get(SCORE_TEMPO_NOTE_SCALE)`. SMuFL-derived, then shrunk by a theme-supplied scale factor to fit above the staff. Inputs: SMuFL metadata (static) + FlatLaf property (static at startup). Genuinely ss.
- Also exposes `EQUALS_GAP_SS = FlatLafProps.get(SCORE_EQUALS_GAP)` — a theme-driven ss constant used by both tempo and beat-change subclasses for the "=" gap.

**TempoChangeAttachment** — inherits Metronome's `0` width and quarter-note height.
- Real width is `computeContentMetrics(FontMetrics)`. Inputs:
  - Glyph width from `noteWidthSs(tempo.getTempoType().getNote(), metadata)` — SMuFL `requireAdvanceWidth` × `NOTE_SCALE`, plus two dot-advance widths per augmentation dot.
  - Text width from `attrFontMetrics.stringWidth(tempoText())` where `tempoText()` concatenates `"= " + visibleTempo + " "` (if tempo is visible) with `tempo.getTempoDescription()` (free-form user text).
  - `EQUALS_GAP_SS` gap between glyph and text.
- Font-driven and user-data-driven; cannot be a constant or a static method.

**BeatChangeAttachment** — same shape as TempoChangeAttachment.
- `computeContentWidthSs(FontMetrics)` inputs:
  - `noteWidthSs(beatChange.duration().getNote(), metadata)` — left note + any dots (SMuFL).
  - `attrFontMetrics.stringWidth("=")` (font-driven, converted to ss).
  - `noteWidthSs(beatChange.beat().getNote(), metadata)` — right note + any dots (SMuFL).
  - Two `EQUALS_GAP_SS` gaps flanking the "=".
- All inputs are either SMuFL-derived or `FontMetrics`-derived; no hand-picked literals.

### Summary — where the numbers come from

| Category | Classes | Source |
|---|---|---|
| Pure SMuFL bbox constants | Clef, Articulation, Trill, FermataAttachment | `SMuFLMetadata.requireBBox(glyph)` at class init — correct ss, correct conversion |
| SMuFL + theme-scale | MetronomeAttachment (height) | bbox × `FlatLafProps` scale factor — correct ss |
| SMuFL via ElementType | StaffElement, StructuralElement | precomputed in `ElementType.computeElementBoundsSs` — correct ss |
| SMuFL at runtime | DynamicAttachment | `requireBBox(glyph).width/height()` called per call; fallbacks for un-glyphed types — correct ss |
| Engraving-convention ss constants | Staff (height), Ending (height), Hairpin (height) | `LayoutStylesheet` literals (`STAFF_HEIGHT_SS`, `VOLTA_TICK_HEIGHT_SS`, `HAIRPIN_OPENING_HEIGHT_SS`) — definitional, not measured |
| Caller-provided px | Staff (width) | Set from layout engine (already converted upstream) |
| Bare px literals with no physical basis | Attribution (both), KeySignature (both), Tuplet (height), Tie (height) | Hand-picked magic numbers; some coincidentally close to ss-correct values at scale=8; none SMuFL-sourced |
| Span formula with mixed units | Tuplet, Tie, Ending, Hairpin (widths) | `(endXSs − anchorXSs) + endElement.getContentWidthPx()` — literal ss + px sum, the Q2 bug |
| Font-driven, runtime | AnnotationAttachment (width), TempoChangeAttachment (width), BeatChangeAttachment (width) | `FontMetrics.stringWidth(...)` via caller-supplied metrics; converted to ss |
| Wrong-in-principle constant | AnnotationAttachment (height) | `DEFAULT_HEIGHT_SS = 1.75` — should be font-derived like width |
| Intentional stubs | AnnotationAttachment (width Px=0), MetronomeAttachment/TempoChange/BeatChange (width Px=0) | `getContentWidthPx` returns 0; real value lives on a sibling method |

Net: seven classes are fully SMuFL-backed and correct (Clef, Articulation, Trill, FermataAttachment, DynamicAttachment, StaffElement, StructuralElement, plus MetronomeAttachment's height); three classes mix definitional ss constants with correct conversion (Staff, Ending, Hairpin — heights only); four classes (Tuplet, Tie, Ending, Hairpin) have the span-formula width bug; three classes hold bare px magic numbers with no physical basis (Attribution, KeySignature, and Tuplet/Tie heights); three classes have runtime font-driven real widths alongside stub Px=0 returns (AnnotationAttachment, TempoChangeAttachment, BeatChangeAttachment).

---

## Q2 — Caller Audit

### Background: the bug

`getContentBounds()` and `getMarginBounds()` both construct a `Rectangle2D` with mixed units:
- X/Y origin: `positionSs` (staff-space)
- Width/Height: `getContentWidthPx()` / `getContentHeightPx()` (pixels)

### Call sites

Reference graph (resolved via Serena `jet_brains_find_referencing_symbols` on each method):

```
LineElement.getBounds()                            ← 0 callers  (DEAD)
LineElement.containsPoint(x, y)                    ← 0 callers  (DEAD)
LineElement.containsPoint(x, y, expansion)         ← 1 caller: containsPoint(x, y)  (TRANSITIVELY DEAD)
ElementRenderer.getBounds (interface)              ← 0 callers  (DEAD)
BaseElementRenderer.getBounds (only override)      ← 0 callers  (DEAD — no subclass override either)
LineElement.getContentBounds()                     ← 3 callers, all dead:
                                                     • LineElement.getBounds() (dead)
                                                     • LineElement.containsPoint[1] (dead)
                                                     • BaseElementRenderer.getBounds (dead)
LineElement.getMarginBounds()                      ← 5 callers:
                                                     • LineElement.getBounds() (dead)
                                                     • CollisionDetector.calculateNoteExtent × 4 (LIVE)
```

**`LineElement.getBounds()` — `LineElement.java:295`**
- **Zero callers.** Effectively dead code. The "accidentally correct?" question is moot — the method is not invoked in production.
- Only self-references: it is the sole caller of `getContentBounds()` (outside the also-dead paths).

**`LineElement.containsPoint(double x, double y)` — `LineElement.java:309–311`**
- **Zero callers.** Dead code.

**`LineElement.containsPoint(double x, double y, double expansion)` — `LineElement.java:320`**
- **One caller: the 0-arg overload above, itself dead.** Transitively dead.
- Previously suspected live callers (`DebugInspector.updateInspectorHoverOLD`) are all block-commented out.

**`BaseElementRenderer.getBounds(T element, ElementRenderContext)` — `BaseElementRenderer.java:266–270`**
- **Zero callers.** The interface method `ElementRenderer.getBounds` has zero callers, and `BaseElementRenderer` is the only implementation — no renderer subclass overrides it.
- Dead code.

**`CollisionDetector.calculateNoteExtent` — `CollisionDetector.java:63–93` (4 call sites: note, attachment, articulation, range)**
- **The sole live consumer of `getMarginBounds()` and therefore the sole live manifestation of the mixed-unit bug.**
- **Intentionally px-aware** — `staffMiddleY` is passed as pixels (`scale.toPixels(defaultSpaceAbove + 2.0)`).
- `LineComponent.java:433,508` both carry the explicit comment: *"CollisionDetector still uses pixel-space margin bounds (renderers not yet converted)"*.
- `LineComponent` calls `scale.fromPixels()` on the result, expecting a px-space `Rectangle2D`.
- The mixed-unit arithmetic (ss-origin minus px-staffMiddleY) produces a wrong subtraction but the authors account for it via the final `fromPixels()` conversion.
- **This is a known workaround, not accidental correctness.** It will break when `getMarginBounds()` is fixed to return pure ss.

### Blast-radius summary

**The blast radius is much smaller than the API surface suggests.** Of the six methods that touch the mixed-unit rectangles (`getBounds`, `getContentBounds`, `getMarginBounds`, `containsPoint` ×2, `BaseElementRenderer.getBounds`), only one path is live: `CollisionDetector.calculateNoteExtent` → `getMarginBounds()` → consumed by `LineComponent.calculateMiddleLineYSs` / `calculateLineHeightSs`.

Minimum co-changes to fix the unit-mixing bug:
1. Convert `getMarginBounds()` (and for symmetry `getContentBounds()`) to return pure staff-space rectangles.
2. Update `CollisionDetector.calculateNoteExtent` to accept `staffMiddleYSs` instead of `staffMiddleYPx`.
3. Update the two `LineComponent` call sites to pass `defaultSpaceAbove + 2.0` directly (no `toPixels`) and drop the `scale.fromPixels(...)` wrap around the returned extent.
4. Delete the dead methods — `LineElement.getBounds`, both `containsPoint` overloads, `ElementRenderer.getBounds` / `BaseElementRenderer.getBounds` — or rebuild them to take ss if a future need is identified. They currently contribute only ambiguity and test-surface without any live consumer.

No renderer subclasses, no debug inspector, no hit-testing path depends on the mixed-unit rectangles today.

---

## Q3 — Unit-Mixing Hotspots Outside LineElement

### StaffSpaces call sites

`StaffSpaces.toPixels` and `StaffSpaces.fromPixels` have **zero call sites** in `src/main/java/` (verified via Grep for both the method names and the `StaffSpaces` type). The class definition itself is the only file containing those method names. The migration to `ScaleContext` is already complete — no action needed here.

### Fields missing unit suffixes (`ui/layout/` and `ui/renderer/`)

No violations in `ui/layout/` or `ui/renderer/` proper. Violations exist in adjacent UI directories:

| File | Line | Declaration | Issue |
|---|---|---|---|
| `ui/component/score/UnderLyricsComponent.java` | 36 | `private float contentX = -1` | Should be `contentXPx` or `contentXSs` |
| `ui/component/score/BanglaLyricsComponent.java` | 40 | `private float contentX = -1` | Same |
| `ui/component/score/TranslationComponent.java` | 46 | `private float contentX = -1` | Same |
| `ui/dialog/ResolutionDialog.java` | 50–53 | `sheetWidth`, `sheetHeight`, `sheetHeightWithoutLyrics`, `sheetHeightWithoutTitle` | All pixel dimensions, missing `Px` suffix |

### Method parameters missing unit suffixes

| File | Lines | Declaration | Issue |
|---|---|---|---|
| `ui/layout/Bounds.java` | 211, 222 | `containsPoint(double x, double y)` / `…(double x, double y, double expansion)` | `expansion` javadoc says "pixels"; `x`/`y` are layout-space |
| `ui/layout/LineElement.java` | 310, 321 | Same signatures | Same issue |
| `ui/layout/NoteBounds.java` | 210, 219 | `translate(double dx, double dy)` / `translateRect(double dx, double dy)` | `dx`/`dy` unsuffixed |
| `ui/layout/ElementBounds.java` | 221, 230 | Same | Same |
| `ui/layout/AttachmentLayout.java` | 193 | `containsPoint(double x, double y)` | Bare `x`/`y` |
| `ui/layout/LyricsLayout.java` | 128 | `containsPoint(double x, double y)` | Bare `x`/`y` |
| `ui/layout/SyllableLayout.java` | 111 | `containsPoint(double x, double y)` | Bare `x`/`y` |
| `ui/layout/RangeLayout.java` | 201 | `containsPoint(double x, double y)` | Bare `x`/`y` |
| `ui/layout/Attribution.java` | 118 | `calculateRightAlignedX(double staffWidth, double rightMargin)` | Units unclear; mixes result with `getContentWidthPx()`. Method has zero callers (verified) — dead code, but still a documented unit ambiguity |
| `ui/layout/ElementColumn.java` | 268 | `setXSs(double x)` | Method name has `Ss`, parameter does not |
| `ui/renderer/ElementRenderContext.java` | 370 | `setOverrideElementXSs(double x)` | Same |
| `ui/renderer/BarRenderer.java` | 208 | `drawRepeatDots(Graphics2D g2, double x)` | Context implies `xSs` |
| `ui/renderer/BaseElementRenderer.java` | 290 | `drawLedgerLine(Graphics2D g2, double x, double y, double width, …)` | All three positional params unsuffixed |
| `ui/renderer/BaseElementRenderer.java` | 306, ~316 | `drawBravuraGlyph(Graphics2D, SMuFLGlyph, double x, double y)` (two overloads) | `x`/`y` unsuffixed — javadoc says only "X position" / "Y position" |

### Records with unsuffixed positional fields

| File | Line | Record | Issue |
|---|---|---|---|
| `ui/layout/Margin.java` | 29 | `public record Margin(double left, double bottom, double right)` | Three positional fields unsuffixed. Javadoc claims "music units (MU)" but the project convention requires `Ss` suffix; no MU token appears anywhere else in the codebase |
| `ui/renderer/GlissandoRenderer.java` | 401–406 | `record NoteContext(StaffElement note, double cx, double cy, …)` | `cx`/`cy` unsuffixed (coordinate center) |
| `ui/renderer/GlissandoRenderer.java` | 411 | `record Endpoints(double startX, double startY, double endX, double endY, double angle)` | Four positional fields missing unit suffix |

### Mixed-unit arithmetic

The only candidate surfaced by a pattern search outside the `LineElement` hierarchy is:

- `VerticalStackingCalculator.java:204` — `new HairpinShifts(x1Ss, x2Ss - x1Ss, sc.fromPixels(yPx))` — **not a bug**: `x2Ss - x1Ss` is pure ss; `sc.fromPixels(yPx)` explicitly converts before passing.

Note: the span-formula bugs in `Tuplet` / `Tie` / `Ending` / `Hairpin` (ss subtraction + `getContentWidthPx()`) are true mixed-unit arithmetic but live inside `LineElement` subclasses and are already catalogued under Q1 / Q2. Q3 scope is deliberately outside the `LineElement` hierarchy.

### Summary

`StaffSpaces` migration is complete — no work needed there. The hygiene debt outside `LineElement` is concentrated in three areas: (1) the `containsPoint` family spreads across at least seven layout classes with bare `x`/`y`/`expansion` parameters and conflicting unit documentation; (2) renderer draw-method parameters (`drawLedgerLine`, `drawBravuraGlyph` ×2, `drawRepeatDots`, `GlissandoRenderer.Endpoints` / `NoteContext`) and two setter methods where the method name encodes the unit but the parameter does not; and (3) the `Margin` record, which labels its fields as "music units (MU)" — a token used nowhere else in the codebase — rather than the project-standard `Ss` suffix. The score-component and dialog field renames are lower priority.

---

## Q4 — Rectangle2D Contract

### Scope

Q4 asks whether any consumer of `LineElement.getContentBounds()` /
`LineElement.getMarginBounds()` treats the returned `Rectangle2D` as pixel-
precision geometry — i.e. rasterizes its coordinates for drawing, rounds them
to the integer pixel grid, or compares them directly against integer mouse
coordinates.

Note: the same method names exist on the `Bounds` (`ui/layout/Bounds.java`)
and `ElementBounds` (`ui/layout/ElementBounds.java`) value objects. Those are
separate symbols with pre-populated rectangles and are out of scope for Q4,
which concerns the live-computed rectangles on `LineElement`. `SectionLayout`
and `DebugState` consume only the value-object variants.

### Reference tree for the LineElement methods

`getContentBounds()`
- `LineElement.getBounds()` — dead (zero callers)
- `LineElement.containsPoint[1]` — dead (the 0-arg overload that calls it has zero callers)
- `BaseElementRenderer.getBounds(T, ElementRenderContext)` — dead (interface method has zero callers; no subclass override)

`getMarginBounds()`
- `LineElement.getBounds()` — dead
- `CollisionDetector.calculateNoteExtent` — live, 4 call sites (note, attachment, articulation, range)

The only live chain is `CollisionDetector.calculateNoteExtent` → consumed by
`LineComponent.calculateMiddleLineYSs` / `calculateLineHeightSs`.

### What the live consumer actually does

`CollisionDetector.calculateNoteExtent` (`CollisionDetector.java:55–93`):

```java
var noteBounds = note.getMarginBounds();
double noteTop    = noteBounds.getMinY() - staffMiddleY;
double noteBottom = noteBounds.getMaxY() - staffMiddleY;
// …
return new Rectangle2D.Double(0, minY, 0, maxY - minY);
```

All arithmetic is `double`. No `(int)` cast. No `Math.round` / `Math.ceil` /
`Math.floor`. No `SwingUtilities.computeIntersection`. The `Rectangle2D.Double`
constructor takes doubles directly.

`LineComponent.calculateMiddleLineYSs` / `calculateLineHeightSs`
(`LineComponent.java:431–437`, `506–516`):

```java
double tempMiddleLineYPx = scale.toPixels(defaultSpaceAbove + 2.0);
var extent = CollisionDetector.calculateNoteExtent(line, tempMiddleLineYPx);
spaceAbove = Math.max(MIN_SPACE_ABOVE_SS, scale.fromPixels(Math.abs(extent.getMinY())));
spaceBelow = Math.max(defaultSpaceBelow,   scale.fromPixels(extent.getMaxY()) - (staffHeight / 2.0));
```

Again pure `double` throughout — no rounding, no integer coordinate grid.

### Q4 answer

**No live consumer assumes pixel-precision (integer-rasterized) geometry from
`getContentBounds()` / `getMarginBounds()`.** No path rounds the rectangle to
the int grid, rasterizes it through `Graphics2D`, or compares it to an
integer mouse coordinate. Every read is `getMinY()` / `getMaxY()` consumed as
`double`.

The contract the live consumer *does* assume is **pixel unit** (not pixel
precision): `LineComponent` wraps the returned extent in `scale.fromPixels(...)`,
which is only correct if the numeric values in the rectangle are `double`-valued
pixels. That assumption is exactly the Q2 workaround noted in the two in-code
comments at `LineComponent.java:433` and `:508`:
*"CollisionDetector still uses pixel-space margin bounds (renderers not yet
converted)"*. When `getMarginBounds()` is converted to return pure staff-space,
those two `scale.fromPixels(...)` wraps must come off in the same change.

**Dead paths.** `BaseElementRenderer.getBounds` — the one renderer-side
consumer that could plausibly have rasterized — has zero callers, and no
renderer subclass overrides it. `LineElement.containsPoint` — the one
mouse-hit-test consumer that could plausibly have compared the rectangle to
integer mouse coordinates — also has zero callers; the previously-suspected
`DebugInspector.updateInspectorHoverOLD` references are all block-commented
out. Both paths are latent hazards for a future caller but are not currently
live.

**No `Rectangle2D.contains(Point)` / `Rectangle2D.intersects(Rectangle2D)`
calls anywhere in the tree.** Grep confirms `containsPoint` is the only
hit-test variant in the layout package, and its one transitively-reachable
caller (`LineElement.containsPoint[0]`) is itself dead.

### Implications for the refactor

1. Converting `getMarginBounds()` to return pure staff-space requires
   exactly three edits: the method body itself, the four `noteTop`/`noteBottom`
   calculations in `CollisionDetector.calculateNoteExtent` (and the
   `staffMiddleY` parameter name / unit), and the two `LineComponent` call
   sites that drop the `scale.toPixels` / `scale.fromPixels` wraps.
2. Converting `getContentBounds()` to staff-space requires only the method
   body — all three call sites (`getBounds`, `containsPoint`, `BaseElementRenderer.getBounds`)
   are dead. The refactor can either delete those call sites as a separate
   cleanup or leave them as uniform-ss consumers once the method is fixed.
3. No drawing, rasterization, or rounded hit-test path has to be revisited.
   The `Rectangle2D.Double` return type is a safe unit-agnostic carrier; the
   unit bug lives entirely in how the values inside it are computed.

---

## Q5 — Caching and Zoom Readiness

### Cached Rectangle2D fields

| Class | Field | Invalidation? | Zoom risk |
|---|---|---|---|
| `NoteBounds` | `noteHeadBounds`, `noteWithStemBounds`, `noteWithArticulationsBounds` | None — immutable value object, reconstructed each layout pass | Low |
| `ElementBounds` | `contentBounds`, `paddingBounds`, `marginBounds`, `visualBounds` | None — immutable value object | Low |
| `Bounds` | `contentBounds`, `marginBounds` | None — immutable value object | Low |
| `Ending` | `LABEL_1_BOUNDS_SS` (`static final`) | None — frozen at class-load | **High** — computed from `GraphicUtils.LAYOUT_FRC` at JVM init; stale if font rendering context changes under zoom |
| `Ending` | `LABEL_2_BOUNDS_SS` (`static final`) | None — frozen at class-load | **High** — same |

### ScaleContext API

**Mutable?** Yes. `pixelsPerStaffSpace` is a non-final instance field; `setPixelsPerStaffSpace(double)` is public and unrestricted beyond a positivity check.

**Change events?** No. `ScaleContext` exposes no listener, no `PropertyChangeSupport`, no event bus hook. A call to `setPixelsPerStaffSpace()` is invisible to all consumers.

### Stale-cache candidates

All of the following capture a scale-derived pixel value at construction/class-load and have no mechanism to recompute on scale change:

| Site | Type | Field | Risk |
|---|---|---|---|
| `Score.java:111` | `public static final float` | `STAFF_POSITION_OFFSET_PX` | **Critical** — baked at class load; cascades into `Annotation.ABOVE`/`BELOW` |
| `Annotation.java:28,30` | `public static final int` | `ABOVE`, `BELOW` | **Critical** — derived from `Score.STAFF_POSITION_OFFSET_PX` at `Annotation` class-load; every default annotation Y is permanently fixed |
| `TranslationComponent.java:37` | `private static final int` | `TRANSLATION_TOP_MARGIN` | High — `ScaleContext.toRoundedPixels(2.0)` baked at class load |
| `BanglaLyricsComponent.java:37` | `private static final int` | `BANGLA_LYRICS_TOP_MARGIN` | High — same pattern |
| `MainPanel.java:77` | instance field | `scoreMarginTop` | Medium — set once in constructor, never updated |
| `StaffPanel.java:63` | instance field | `lineMargin` | Medium — set once in constructor, never updated |
| `Line.java:129,146,155` | instance fields (`@Deprecated`) | `beatChangeYPosPx`, `firstSecondEndingYPosPx`, `trillYPosPx` | Low (deprecated) — but `FormatMigrator` still compares live values against `ScaleContext.toRoundedPixels(...)`, so scale change would break migration logic |

### Summary

The codebase is not zoom-safe today. `ScaleContext` is mutable but silent — no consumer is notified when the scale changes. The minimum infrastructure before zoom can be added: (1) add change notification to `ScaleContext` (PropertyChange or MBassador message); (2) convert all `static final` pixel constants derived from `ScaleContext` into either lazily-recomputed values or `@Handler`-driven fields; (3) convert per-object constructor-time pixel caches into handler-driven recomputations; (4) convert the two `static final Rectangle2D` fields in `Ending` to be recomputed per-render or per-scale-change.

---

## Q6 — Test Coverage

### Bounds/dimension assertions

| Test class | Test method | Assertion | Locks in px value? |
|---|---|---|---|
| `ElementTypeTest` | `testElementGetContentHeightReturnsPx` | `getContentHeightPx()` close to `sc.toPixels(getElementHeightSs(...))` | No — derives expected value from `ScaleContext` at runtime |
| `ElementTypeTest` | `testElementGetContentWidthReturnsPx` | `getContentWidthPx()` close to `sc.toPixels(getFullElementWidthSs())` | No — same pattern |
| `DynamicAttachmentTest` | `testNullGlyphTypeFallsBackToDefaultHeight` | `getContentHeightSs()` equals `1.75` | No — staff-space, scale-independent |
| `DynamicAttachmentTest` | `testNullGlyphTypeFallsBackToDefaultWidth` | `getContentWidthSs()` equals `2.5` | No — staff-space |
| `DynamicAttachmentTest` | `testContentHeightSsDelegatesToSmuflBBox` | `getContentHeightSs()` equals `bbox.height()` | No — defers to metadata |
| `DynamicAttachmentTest` | `testContentWidthSsDelegatesToSmuflBBox` | `getContentWidthSs()` equals `bbox.width()` | No — defers to metadata |
| `NoteAreaBuilderTest` | various | `getBounds2D()` relational comparisons (min/max X) | No — relational only |
| `StaffExtentsTest` | many | `yGet(...)` equals `-3.0`, `-5.0`, etc. | No — all staff-space units |
| `PageModelTest` | `testDefaultLineWidth*` | `getContentAreaWidthPx()` converted via `fromPixels(...)` | No — result compared in ss |

No test asserts a bare pixel integer (`assertEquals(8, ...)`, `assertEquals(16, ...)`). The two `getContentHeightPx`/`getContentWidthPx` delegation tests validate the chain, not a hardcoded number. They would survive a method rename but would not detect a unit-mixing bug.

### E2e tests with pixel-coordinate assumptions

The `E2ETest` base class computes most coordinates via `ScaleContext.toPixels(...)` at runtime and would adapt to a scale change. However, these hardcoded pixel offsets are not scaled:

- `E2ETest.java:437` — `xPx = 80` (empty-line insertion fallback)
- `E2ETest.java:444` — `+ 30` (past-last-note offset)
- `E2ETest.java:470` — `- 10` (before-element offset)
- `SelectionTest.java:240,191–192` — `+ 40`, `- 20`, `+ 20` drag-select margins

Tests at risk from these offsets: `ElementInsertionTest`, `SelectionTest`, `DynamicsMarkingTest`, `NoteConnectionTest`. All currently pass because the default scale=8 makes the magic numbers land within element hit-rects.

### Scale-varying test harness

**Does not exist.** Every test runs at the default `pixelsPerStaffSpace = 8`. `NoteDragHandlerTest` mocks `ScaleContext` but only stubs `fromPixels(anyDouble()) → 5.0` — it does not vary the scale to probe zoom behavior. No parameterized test cycles over multiple scale values.

### AnnotationAttachment coverage

**Exists:**
- `ManualOffsetStackingTest` — four tests verify `userYOffsetSs`/`userXOffsetSs` shift the stacking position correctly; all assertions in staff-space units
- `FormatMigratorTest` — two tests check presence/absence of `AnnotationAttachment` after migration

**Missing:**
- No test asserts `getContentHeightSs()` (or `getContentHeightPx()`) on `AnnotationAttachment`
- No test calls `computeContentWidthSs(FontMetrics)` — the actual text-width calculation is entirely untested
- No test exercises `AnnotationAttachment` bounds at a non-default scale

### Summary

The test suite contains no hardcoded pixel-integer assertions and the two `*Px` delegation tests derive expected values from `ScaleContext` rather than encoding a fixed number, so they would survive a rename but would not detect a unit-mixing bug. No test anywhere varies `pixelsPerStaffSpace` away from scale=8, meaning zoom regressions — including the core Px/Ss mixing bug — are completely invisible to the automated test suite.

---

## Q7 — AnnotationAttachment Specifics

### 7a. FontMetrics source for `Composition.getAnnotationFontMetrics()`

**Where the method lives and its body**

`Composition.getAnnotationFontMetrics()` is at `src/main/java/songscribe/music/Composition.java:543`. Its body is a one-liner:

```java
public FontMetrics getAnnotationFontMetrics() {
    return annotationFontMetrics;
}
```

It returns the `annotationFontMetrics` instance field declared at line 149.

**FontRenderContext used — not `GraphicUtils.LAYOUT_FRC`**

The `FontMetrics` value is produced by two separate code paths, neither of which uses `GraphicUtils.LAYOUT_FRC`.

1. **At construction** (`initFontsFromPrefs`, `Composition.java:257–283`): a 1×1 `BufferedImage` is created, its `Graphics` object is extracted, and `g.getFontMetrics(font)` is called for each font — annotation included (`Composition.java:277`). The `FontRenderContext` embedded in that `FontMetrics` object is whichever AWT provides for a headless `BufferedImage` `Graphics`.

2. **On font change** (`applyAnnotationFont`, `Composition.java:1512–1515`): `MyFontUtils.getFontMetrics(annotationFont)` is called. `MyFontUtils.getFontMetrics` (at `songscribe/util/MyFontUtils.java:369–378`) is identical in mechanism — it allocates its own 1×1 `BufferedImage`, calls `getGraphics()`, and returns `g.getFontMetrics(font)`. No `FontRenderContext` is threaded in at all; the FRC is whatever AWT defaults to for an off-screen image.

`GraphicUtils.LAYOUT_FRC` (`src/main/java/songscribe/util/GraphicUtils.java:51`) is used only in `Ending`'s static `LABEL_1_BOUNDS_SS`/`LABEL_2_BOUNDS_SS` fields and by `TextLayout` measurement in rendering; it has no involvement in the `Composition` font metrics chain.

**Caching — yes, the field is cached on `Composition`, and it IS invalidated on font change**

`annotationFontMetrics` is a mutable instance field. It is written exactly twice: at construction in `initFontsFromPrefs` and in `applyAnnotationFont`. Between writes, `getAnnotationFontMetrics()` returns the same object — it is not recomputed per call. This is deliberate: font metrics creation involves off-screen `BufferedImage` allocation and should not be done in a hot loop.

**Invalidation chain**

The cache is replaced (not cleared) every time the annotation font changes. The full chain:

1. `CompositionSettingsDialog.FontTab.setData` posts a `FontDidChangeNotification` with the new annotation `Font` via `composition.postWithModification(new FontDidChangeNotification(...))`.
2. `Composition.fontDidChange(@Handler, Composition.java:1317)` receives the notification. If `update.getAnnotationFont() != null`, it calls `setAnnotationFont(update.getAnnotationFont())` (`Composition.java:1332`).
3. `setAnnotationFont` (`Composition.java:694–695`) calls `mutateFont(FontField.ANNOTATION, annotationFont, font, () -> applyAnnotationFont(font))`.
4. `mutateFont` records a `FontChange(FontField.ANNOTATION, oldFont, newFont)` mutation and invokes the `apply` runnable.
5. `applyAnnotationFont` (`Composition.java:1512–1514`) writes both `annotationFont = font` and `annotationFontMetrics = MyFontUtils.getFontMetrics(annotationFont)` atomically.

The `FontChange` mutation uses `FontField.ANNOTATION` (defined at `src/main/java/songscribe/message/mutation/FontField.java:31`). Undo/redo replay re-invokes `applyAnnotationFont`, so the cache is refreshed on undo as well.

There is no separate `PrefsDidChangeNotification` handler in `Composition` for annotation font; the preference path goes entirely through `CompositionSettingsDialog` → `FontDidChangeNotification` → `fontDidChange` handler. `initFontsFromPrefs` is only called from the two constructors (`Composition()` at line 222 and `Composition(CompositionData)` at line 252), not from any preference-change event.

**Sibling `getXxxFontMetrics` methods — uniform pattern**

All six font-metrics getters on `Composition` are identical one-liners returning private fields:

| Method | Line | Backing field | Invalidation | Note |
|---|---|---|---|---|
| `getTitleFontMetrics()` | 519 | `titleFontMetrics` | `applyTitleFont` via `FontField.TITLE` | |
| `getLyricsFontMetrics()` | 527 | `lyricsFontMetrics` | `applyLyricsFont` via `FontField.LYRICS` | |
| `getAttributionFontMetrics()` | 535 | `attributionFontMetrics` | `applyAttributionFont` via `FontField.ATTRIBUTION` | |
| `getAnnotationFontMetrics()` | 543 | `annotationFontMetrics` | `applyAnnotationFont` via `FontField.ANNOTATION` | |
| `getBanglaFontMetrics()` | 551 | `banglaFontMetrics` | `applyBanglaFont` (no `FontField` — not user-changeable) | Bangla font is fixed at load (`TiroBangla-Regular.ttf` size 17) |
| `getFootnoteFontMetrics()` | 559 | `footnoteFontMetrics` | `applyFootnoteFont` (no `FontField` — not user-changeable) | Footnote font is fixed at load (`LatoPlus-Italic.otf` size 15) |

Every `applyXxxFont` method body follows the same `annotationFont = font; annotationFontMetrics = MyFontUtils.getFontMetrics(annotationFont)` pattern (`Composition.java:1497–1524`). All use `MyFontUtils.getFontMetrics` (the off-screen `BufferedImage` path), not `GraphicUtils.LAYOUT_FRC`. The FRC difference between the two initialization paths (`initFontsFromPrefs` uses `img.getGraphics().getFontMetrics(font)` directly; `applyAnnotationFont` uses `MyFontUtils.getFontMetrics`) is technically distinct rendering contexts but both are headless 1×1 off-screen images — in practice the same AWT defaults apply.

### 7b. Call ordering: `setParentLine` vs first bounds query

**`setParentLine` on `LineElement` and its field**

`LineElement.parentLine` is declared `@Nullable` at `LineElement.java:49`. `setParentLine(@Nullable Line)` is at `LineElement.java:114`. `getParentLine()` returns it nullable at `LineElement.java:107`. `StaffElement` does NOT override `getParentLine()` — the field is the `LineElement.parentLine` field, which is distinct from the `StaffElement.line` field (set by `setLine()`/`Line.addElement`).

**Who calls `setParentLine` on attachments**

There are five call sites:

1. **`AnnotationAttachment(StaffElement parent, Annotation)` — `AnnotationAttachment.java:77`**: calls `setParentLine(parent.getParentLine())` inside an `if (parent != null)` guard.
2. **`StaffElement.addAttachment(Attachment)` — `StaffElement.java:344`**: calls `attachment.setParentLine(getParentLine())` unconditionally. This is the gate that transfers the note's `parentLine` to the attachment at the moment of attachment.
3. **`LineElement.addChild(LineElement)` — `LineElement.java:386`**: calls `child.setParentLine(this.parentLine)` — propagates through the child-element tree.
4. Other attachment constructors (`FermataAttachment`, `DynamicAttachment`, `MetronomeAttachment`, `Articulation`) follow the same `if (parent != null) { setParentLine(parent.getParentLine()); }` pattern.
5. `Line.addRangeElement` (`Line.java:823`) sets `parentLine` only on `RangeElement` instances, not on `StaffElement` notes.

**Critical gap: `Line.addElement` does not call `setParentLine`**

`Line.addElement(StaffElement)` (`Line.java:240–241`) calls only `element.setLine(this)`. This sets `StaffElement.line` (a separate field) but does NOT call `element.setParentLine(this)`. Consequently, unless `setParentLine` is called by some other route, `note.getParentLine()` remains `null` for notes added to a `Line` through the normal `addElement` path.

This has a downstream consequence for `addAttachment`: when `note.addAttachment(attachment)` is called, it invokes `attachment.setParentLine(note.getParentLine())` (`StaffElement.java:344`). If the note does not yet have `parentLine` set, the attachment inherits `null`.

**The window where `parentLine` is `null`**

The window is: after `new AnnotationAttachment("text")` or `new AnnotationAttachment(annotation)` (1-arg constructors) and before `note.addAttachment(attachment)` is called on a note that itself has a non-null `parentLine`. In practice, because `Line.addElement` never sets `parentLine`, a note's `parentLine` is almost always `null` unless it was set explicitly via the 3-arg `AnnotationAttachment(note, annotation)` constructor (which calls `setParentLine(parent.getParentLine())` only if `parent.getParentLine()` was already set).

**Does `AnnotationAttachment.getContentHeightSs()` or `computeContentWidthSs()` need `parentLine`?**

No. `getContentHeightSs()` (`AnnotationAttachment.java:123–125`) returns `DEFAULT_HEIGHT_SS = 1.75` — a constant. `computeContentWidthSs(FontMetrics)` (`AnnotationAttachment.java:115–118`) takes the `FontMetrics` as a parameter and uses `annotation.getAnnotation()` (the text string) — no `parentLine` access. Neither method accesses `parentLine` at all.

**`CollisionDetector.calculateNoteExtent` — is `parentLine` guaranteed for attachments?**

`CollisionDetector.calculateNoteExtent` (`CollisionDetector.java:55–107`) calls `attachment.getMarginBounds()` at line 73 for every attachment on every note. `getMarginBounds()` (inherited from `LineElement`) uses only `positionSs`, `marginTopSs`, etc. — all `LineElement` fields set at construction. It does not read `parentLine`. So `CollisionDetector` does not require `parentLine` to be set and will not NPE if `parentLine` is null.

**`SystemStacker.stackAnnotations` — is `parentLine` used?**

`stackAnnotations` (`SystemStacker.java:147–179`) receives `line` as a parameter and passes it directly to `line.getComposition().getAnnotationFontMetrics()` at line 169. It never calls `annotation.getParentLine()`. So the stacking path is also safe when `parentLine` is null on the attachment.

**When `setParentLine` is actually called — the reliable path**

For attachments created via the 3-arg constructor (e.g. `FormatMigrator`, `SystemStacker` legacy bridge), `setParentLine(parent.getParentLine())` is called in the constructor body (`AnnotationAttachment.java:77`) — but only if `parent.getParentLine()` is non-null at the time. For attachments added via `note.addAttachment(attachment)`, the `parentLine` is set to whatever `note.getParentLine()` returns at call time — which may be null if the note's `parentLine` was never set (the common case, since `Line.addElement` uses `setLine`, not `setParentLine`).

**Summary for 7b**

A `parentLine`-null attachment exists for the entire lifetime of any `AnnotationAttachment` constructed via the 1-arg or 2-arg constructors and attached to a note that was added to a line through `Line.addElement` (i.e., the normal path). The null is structurally harmless today: `getContentHeightSs()`, `computeContentWidthSs(FontMetrics)`, `getMarginBounds()`, and the stacking path none of them read `parentLine`. The future refactor that makes `getContentHeightSs()` resolve metrics via `parentLine.getComposition().getAnnotationFontMetrics()` is where this null will become load-bearing.

### 7c. 1-arg construction paths

**The three constructors of `AnnotationAttachment`**

```java
// Constructor 1 (line 49) — 1-arg: String text
public AnnotationAttachment(String text) {
    this.annotation = new Annotation(text);
    setAlignment(Alignment.LEFT);
}

// Constructor 2 (line 59) — 1-arg: Annotation annotation
public AnnotationAttachment(Annotation annotation) {
    this.annotation = annotation;
    setAlignment(Alignment.LEFT);
}

// Constructor 3 (line 70) — 2-arg: parent + annotation
public AnnotationAttachment(@Nullable StaffElement parent, Annotation annotation) {
    this.annotation = annotation;
    setOwnerElement(parent);
    setAlignment(Alignment.LEFT);
    if (parent != null) {
        setOwnerElement(parent);
        setParentLine(parent.getParentLine());
    }
}
```

Neither 1-arg constructor calls `setParentLine` — `parentLine` is left `null` (the `LineElement` field default).

**All call sites**

| Constructor | Call site | Production? | `setParentLine` before bounds query? |
|---|---|---|---|
| `(String text)` | `ManualOffsetStackingTest.java:120` | No — test only | Attachment is added via `note.addAttachment()` after `createNote(0, false)`, before the note is added to a line. `note.getParentLine()` is null at call time; attachment inherits null. Bounds are only queried inside `VerticalStackingCalculator.calculate`, by which point `stackAnnotations` uses the locally-passed `line` param — not `parentLine`. |
| `(String text)` | `ManualOffsetStackingTest.java:135, 153, 347, 365` | No — test only | Same pattern in all four uses. |
| `(Annotation annotation)` | (unused — zero production or test call sites) | — | Not applicable |
| `(StaffElement parent, Annotation)` | `FormatMigrator.java:431` | Yes | `parent` is an element already in a `Line` (iterated via `line.getElement(i)`); `parent.getParentLine()` may still be null (see 7b) since `Line.addElement` sets `parent.line` via `setLine`, not `setParentLine`. The constructor sets `parentLine = parent.getParentLine()` — which is null unless someone else called `setParentLine` on the note first. |
| `(StaffElement parent, Annotation)` | `SystemStacker.java:159` | Yes | Used for the legacy-bridge path (temporary attachment). After construction, the attachment is used immediately in `stackAnnotations` via `annotation.computeContentWidthSs(line.getComposition().getAnnotationFontMetrics())` — which does not read `parentLine`. The temporary attachment is never stored. |
| `(StaffElement parent, Annotation)` | `FormatMigratorTest.java:126` | No — test only | Same as `FormatMigrator.java:431`. |

**Is there a path where an existing attachment can be queried for bounds without a parent?**

Yes, structurally — but it is safe in today's implementation:

In `ManualOffsetStackingTest`, the pattern is:
1. `var attachment = new AnnotationAttachment("test");` → `parentLine` is null.
2. `note.addAttachment(attachment)` → `attachment.setParentLine(note.getParentLine())` which is null (note not yet in any line).
3. `populate(line, note)` → `line.addElement(note)` → `note.setLine(line)` — does NOT propagate to attachment.
4. `stackColumns(...)` → `VerticalStackingCalculator.calculate` → `SystemStacker.stackAnnotations` — uses `line` param, reads `annotation.computeContentWidthSs(line.getComposition().getAnnotationFontMetrics())` and `annotation.getContentHeightSs()`. Both methods are `parentLine`-free.

At step 4 the attachment has `parentLine == null` throughout. The test passes because the current code never dereferences `parentLine`. If `getContentHeightSs()` were changed to `parentLine.getComposition().getAnnotationFontMetrics().getHeight()` (the proposed fix for the fixed-constant bug), step 4 would throw a `NullPointerException` on the `parentLine` dereference.

**Production path analysis**

In the two production call sites (`FormatMigrator` and `SystemStacker`):

- **`FormatMigrator.java:431`**: `AnnotationAttachment(note, note.getAnnotation())` then `note.addAttachment(attachment)`. The 3-arg constructor sets `parentLine = note.getParentLine()`. At migration time, notes are iterated from `line.getElement(i)` — the note's `line` field is set, but `parentLine` on the note is not (because `Line.addElement` uses `setLine`, not `setParentLine`). So the migrated attachment gets `parentLine == null`. `addAttachment` then calls `attachment.setParentLine(note.getParentLine())` which is also null — no change. The attachment lives with `parentLine == null` indefinitely.

- **`SystemStacker.java:159`**: `AnnotationAttachment(note, note.getAnnotation())` — temporary, not stored. Same null `parentLine` on construction. `computeContentWidthSs(line.getComposition().getAnnotationFontMetrics())` is called immediately (line 168–169) — safe.

**Current failure mode if bounds are queried with null parent**

With the current code, there is no failure: no method reachable from `getMarginBounds()`, `getContentHeightSs()`, or `computeContentWidthSs(FontMetrics)` reads `parentLine`. The null persists silently. `getContentHeightSs()` returns `DEFAULT_HEIGHT_SS = 1.75` regardless.

**Defined failure mode once `parentLine` becomes load-bearing**

Per the investigation file (`plans/pixel-unit-mixing-investigation.md`, Constraints section) and `.claude/rules/development.md`: any code path that reads `parentLine` must call `RuntimeError.exit` when the value is null, rather than silently degrading or falling back. The pattern for a future no-arg `getContentHeightSs()` that resolves metrics would be:

```java
// Proposed; not yet implemented
public double getContentHeightSs() {
    var line = getParentLine();
    if (line == null) {
        RuntimeError.exit("AnnotationAttachment.getContentHeightSs() called without a parent line");
    }
    var fm = line.getComposition().getAnnotationFontMetrics();
    return ScaleContext.getInstance().fromPixels(fm.getAscent() + fm.getDescent());
}
```

Before this change is safe in tests, `ManualOffsetStackingTest` would need to wire up the parent line on each attachment (either by adding the note to the line first, or by calling `attachment.setParentLine(line)` after construction). The `StaffElement.setLine` method would also need to be extended to retroactively call `setParentLine(line)` on all existing attachments and articulations — currently it does not (`StaffElement.java:623–625`).

---

## Q8 — MetronomeAttachment / TempoChangeRenderer

### 8a. Baseline interpretation confirmed

`ySs + QUARTER_NOTE_HEIGHT_SS` is the bottom visual edge of the quarter-note glyph, not an independent text-placement offset. Evidence:

- `MetronomeRenderer.buildRenderSetup` at `src/main/java/songscribe/ui/renderer/MetronomeRenderer.java:75–101` documents `ySs` as "top Y of the decoration in component staff-space coordinates" and populates it from `DecorationLayout.ySs()` (which is the layout top, in the component coordinate system after `layoutYToComponentYSs`). `DecorationLayout.ySs` is explicitly the decoration's top edge — the same value `stackAbove` / `stackAboveWithRegions` writes as `elementYSs`.
- `MetronomeRenderer.drawDurationEquals` at `MetronomeRenderer.java:152–159` draws the note glyph at `glyphOriginYSs = ySs - metadata.requireBBox(metGlyph).top() * NOTE_SCALE`. Because SMuFL `BBox.top()` (post Y-flip) is negative for glyphs that sit above their font origin, subtracting `bbox.top() * NOTE_SCALE` shifts the glyph's font origin downward so its **top visual edge** lands at `ySs`.
- `MetronomeAttachment.QUARTER_NOTE_HEIGHT_SS` at `src/main/java/songscribe/ui/layout/MetronomeAttachment.java:54` is defined as `QUARTER_NOTE_BBOX.height() * NOTE_SCALE`. Since the glyph's top edge is at `ySs`, its bottom edge is at `ySs + QUARTER_NOTE_HEIGHT_SS`.
- `textBaselineYSs = ySs + MetronomeAttachment.QUARTER_NOTE_HEIGHT_SS` is then used as the `y` argument to `g2.drawString` for the tempo text (`TempoChangeRenderer.java:75,92`) and for the "=" sign (`MetronomeRenderer.java:153,176`). `Graphics2D.drawString` treats `y` as the **baseline**.

So the interpretation is a deliberate typographic choice: the text baseline is pinned to the bottom visual edge of the quarter-note glyph. The description in the investigation file is correct; the name "`textBaselineYSs`" in the renderer matches the semantics.

One caveat: `QUARTER_NOTE_HEIGHT_SS` is hardcoded to the quarter-note bbox even when the actual glyph is a different duration (e.g. half or eighth). For glyphs shorter than a quarter note, the text baseline sits slightly below the glyph's true bottom edge. In practice this is benign because the quarter-note is the tallest common metronome glyph and the offset is used as a conservative alignment anchor across all durations — but the constant name is prescriptive ("QUARTER_NOTE") even though it functions as a universal baseline offset.

### 8b. Region-height adequacy — tempo path correct, beat-change path under-reports

**Tempo path (correct).** `SystemStacker.stackTempo` at `src/main/java/songscribe/ui/layout/stacking/SystemStacker.java:75–102` calls `tempo.computeContentMetrics(attrFontMetrics)` and passes the resulting `regions` list to `stackAboveWithRegions`. `TempoChangeAttachment.computeContentMetrics` at `src/main/java/songscribe/ui/layout/TempoChangeAttachment.java:65–90` builds two `CollisionRegion` entries:

| Region | `xOffsetSs` | `yOffsetSs` | `widthSs` | `heightSs` |
|---|---|---|---|---|
| Glyph | `0` | `0` | `glyphWidth` | `QUARTER_NOTE_HEIGHT_SS` |
| Text | `glyphWidth + EQUALS_GAP_SS` (or `0` if no glyph) | `QUARTER_NOTE_HEIGHT_SS - textAscentSs` | `textWidth` | `textAscentSs + textDescentSs` |

`StackingUtils.stackAboveWithRegions` at `src/main/java/songscribe/ui/layout/stacking/StackingUtils.java:168–178` computes `overallHeightSs = max(region.yOffsetSs() + region.heightSs())` and writes that into `DecorationLayout.heightSs`. For the text region, `yOffsetSs + heightSs = (QUARTER_NOTE_HEIGHT_SS - textAscentSs) + (textAscentSs + textDescentSs) = QUARTER_NOTE_HEIGHT_SS + textDescentSs`. So the reported overall height correctly includes the text descender below the glyph's bottom.

**Beat-change path (layout-correctness bug).** `SystemStacker.stackBeatChange` at `SystemStacker.java:111–138` calls the plain `stackAbove`, passing `beatChange.getContentHeightSs()` — which resolves to the inherited `MetronomeAttachment.getContentHeightSs()` at `MetronomeAttachment.java:115–117` returning `QUARTER_NOTE_HEIGHT_SS`. `BeatChangeAttachment` has no `computeContentMetrics` method; it only has `computeContentWidthSs(FontMetrics)` at `src/main/java/songscribe/ui/layout/BeatChangeAttachment.java:84–95`, which computes width but nothing about height or sub-regions.

However, `BeatChangeRenderer.drawBeatChange` at `src/main/java/songscribe/ui/renderer/BeatChangeRenderer.java:65–75` calls `drawDurationEquals` — the same method used by the tempo path — which draws the "=" sign at `textBaselineYSs = ySs + QUARTER_NOTE_HEIGHT_SS` (`MetronomeRenderer.java:176`). The "=" character has a small descent but no ascent above the glyph top, so the descender extends below `ySs + QUARTER_NOTE_HEIGHT_SS`. That region is not reserved in `StaffExtents` and not reflected in `DecorationLayout.heightSs`. Any element stacked immediately below a beat-change attachment can visually collide with the "=" descender.

The tempo path gets this right because `computeContentMetrics` includes the text region. The beat-change path is a straightforward port away from the per-region pattern: it needs a `computeContentMetrics(FontMetrics)` method on `BeatChangeAttachment` (or the shared base class) that emits a text region for the "=" sign — and `stackBeatChange` needs to call `stackAboveWithRegions` instead of `stackAbove`.

### 8c. Which font drives the text

Both the tempo and beat-change text use the **attribution font**, not the annotation font:

- `MetronomeRenderer.buildRenderSetup` at `MetronomeRenderer.java:98` fetches `ctx.getComposition().getAttributionFont()` and stores it in `RenderSetup.attrFont`.
- `SystemStacker.stackTempo` at `SystemStacker.java:96` and `stackBeatChange` at `SystemStacker.java:133` both read `line.getComposition().getAttributionFontMetrics()` for layout-time measurement.
- `TempoChangeAttachment.computeContentMetrics` takes the `attrFontMetrics` parameter by name (`TempoChangeAttachment.java:65`).

The investigation file's reference to "tall attribution fonts" is correct — not a typo for "annotation fonts". This is the **same** attribution font used for title/composer/arranger attribution strings; it is tracked by `Composition.attributionFont` / `attributionFontMetrics` with the same invalidation infrastructure documented in Q7a for the annotation font (via `FontField.ATTRIBUTION` and `applyAttributionFont` — see the font-metrics sibling table in Q7a). The note glyph itself uses the Bravura music font scaled by `NOTE_SCALE` (`MetronomeRenderer.java:50`), not the attribution font.

### 8d. Additional observations

1. **`scaleAttrFont` deriveFont precision** — `MetronomeRenderer.scaleAttrFont` at `MetronomeRenderer.java:124–126` converts `attrFont.getSize()` (an `int` in px) to staff spaces via `ScaleContext.fromPixels`. At the default scale of 8, a 16px font becomes a 2ss font; this is exact. At non-integer scale factors under future zoom, the conversion is still exact because `fromPixels` divides by a double. Not a unit-mixing bug, just noted because `getSize()` returns `int`: fractional pixel sizes from `deriveFont(float)` round-trip through `(int) getSize()` as a truncation. If rendering ever consumes a non-integer font size, this will lose a fraction. Low severity.

2. **`MetronomeAttachment.getContentWidthPx()` returns `0`** at `MetronomeAttachment.java:120–122`. Width is resolved elsewhere: `TempoChangeAttachment.computeContentMetrics(...).widthSs()` and `BeatChangeAttachment.computeContentWidthSs(FontMetrics)`. Any caller that goes through the `LineElement.getContentWidthPx()` polymorphic API (e.g. `BaseElementRenderer.getBounds`, `CollisionDetector.calculateNoteExtent`) will report zero width for metronome attachments. This is a contract inconsistency with the rest of the hierarchy and a concrete example of the Q1 finding that the `Px`-suffixed API cannot represent font-driven dimensions.

3. **`QUARTER_NOTE_HEIGHT_SS` is a misnomer when used as a baseline offset** — the constant name says "height" but the usage in `drawDurationEquals` is "distance from decoration top to text baseline" (which happens to equal the quarter-note glyph height). Any future refactor that changes the glyph sizing needs to preserve this dual meaning or split the constant into two (e.g. `QUARTER_NOTE_HEIGHT_SS` for the height role, `TEXT_BASELINE_OFFSET_SS` for the typographic role).

4. **The tempo path's "=" is drawn twice in different places** — once by `drawDurationEquals` (inside `renderTempoChange` when `showTempo` is true, via `TempoChangeRenderer.java:87`) and once implicitly as part of beat change (`BeatChangeRenderer.drawBeatChange` → `drawDurationEquals`). Both paths reach the same glyph-placement and text-baseline code, so the region-height bug described in 8b applies to both renderings of the "=" sign when `showTempo` is true — but the tempo path's `computeContentMetrics` reserves space for the text portion including the "=" character (the `tempoText()` method includes a leading "= " when `shouldShowTempo()`), so the text-region bounds do cover it. The bug is specific to beat change where no `computeContentMetrics` exists.

---

## Cross-cutting observations

1. **RangeElement width is a layout-time computation, not a measurement** — `Tuplet`, `Tie`, `Ending`, `Hairpin` all compute width from anchor/end-element positions. Their `getContentWidthPx()` bodies already mix ss and px in the span formula (`abs(endXSs - anchorXSs) + endElement.getContentWidthPx()`). The fix for these is different from the fix for constant-returning subclasses: the abstract `getContentWidthSs()` would need to be called during layout after positions are resolved, or the abstract method needs to be split into a measurement method and a layout-time query.

2. **`Attribution` is an acknowledged stub** — both dimensions are placeholder integers with no physical basis. This class needs a proper font-metrics-driven implementation before the refactor can apply to it.

3. **`KeySignature` has magic pixel numbers** — `ACCIDENTAL_WIDTH_PX = 8.0` and `ACCIDENTAL_HEIGHT_PX = 24.0` have no SMuFL derivation and no named physical origin. At scale=8 they happen to equal 1.0 ss and 3.0 ss respectively, but this is not stated anywhere in the code.

4. **The `containsPoint` unit ambiguity is pervasive** — seven layout classes expose `containsPoint(double x, double y)` with no unit suffix and conflicting javadoc. Before the Px→Ss refactor, these signatures need a unit decision enforced by naming.
