# Hit Testing Unification — Findings and Proposal
Status: decisions recorded, nothing implemented. Written 2026-07-31.

Goal: make almost every `LineElement` that isn't a note selectable, which requires unifying (a) how hit geometry is produced and (b) how a hit item is drawn as selected.

Targets to add, on top of the five kinds that are already hit-testable: articulations (accent, staccato, …), attached decorations (fermata), accidentals, and beams.

* * *
## 1. What exists today
### 1.1 Dispatch is already unified; geometry is not
`songscribe.ui.hit` has `HitTester` (functional interface), `HitTestContext` (record), and a sealed `HitResult` with eight variants. `LineSelectionHandler` holds a `List<HitTester>` in deliberate priority order — the first tester to report a hit wins:

```
lyric → note head → slide → hairpin → ending → staff line
```

So adding a selectable kind already means "add a tester", not "add a branch". What is scattered is where each tester's **geometry** comes from.
### 1.2 Five separate geometry mechanisms
| Target | Geometry source | Produced during |
| --- | --- | --- |
| Note heads | `ElementHitTest.buildElementHitRect` recomputes from `ElementType` natural width/height + layout X + staff position, expanded to `MIN_HIT_SIZE_PX = 8` | on demand |
| Lyrics | `LayoutResult.hitTestLyric` reads lyric boxes | layout |
| Hairpins, endings | `RenderingUtils.hitTestDecoration` reads `Map<LineElement, DecorationLayout>` | layout (stacking) |
| Slides | `SlideRenderer.hitTestSlide` reads `cachedHitBounds` / `hasCachedGeometry` / `cachedStartX…` **fields on the DOM model** | render |
| Staff line | ad-hoc test in `hitTestStaffLine`: within `STAFF_HIT_RADIUS_SS = 2.0` of the middle line **and** `isWithinHeaderXSs` — so the line is selectable only from the header at the left, never by clicking a staff line under the music | on demand |

Two of these are already registries, in two incompatible forms: one keyed by element in `LayoutResult`, one written back onto `StaffElement.Fall` / `StaffElement.Glissando` by the renderer.

Preview/insertion targeting is a sixth mechanism — see §4.

**The render-time slide cache leaks past hit testing.** `SlideRenderer.render` writes `cachedStartX`, `cachedStartY`, `cachedAngle`, `cachedCos`, `cachedSin`, `cachedLength`, and `hasCachedGeometry` onto the `Glissando`, from `computeEndpoints(src, tgt)`. `MusicXmlNotationsWriter.writeSlide` then **reads those fields when saving**, falling back to a slide element with no coordinates when `hasCachedGeometry` is false. So a save that happens before the score has been painted silently drops glissando coordinates. This is an independent reason to move that endpoint math to layout time, beyond hit testing.
### 1.3 A dormant bounds layer
`LayoutResult` carries a four-layer CSS box model (`ElementBoundsSs`: content / padding / margin / visual) whose padding layer is documented as "used for hit testing".

`putElementBounds`, `getElementBounds`, `getBounds(Object)`, `findRangeElementBounds`, and `findAttachmentBounds` are called from **nothing but** `LayoutResultTest` — verified by text search over all of `src`. No production code populates or reads them. `ElementBoundsSs` is live only in `SectionLayout` (title/attribution text).

`LineElement` also has an empty `// Hit Testing` section header, suggesting an element-owns-its-hit-test approach was tried and removed.
### 1.4 Selection drawing
Selection is "recolor the ink", decided independently inside each renderer:

- `LineInvariants.colorFor(...)` — the cascade is playing → extra selection check → element selected → hovered (`REPLACED_ELEMENT_COLOR`) → black. Wrapped by `getElementColor`, `getLyricColor`, `getLyricConnectorColor`.
  
- `RenderingUtils.decorationSelectionColor(LineElement, LineInvariants)` for decorations.
  
- Both consult `LineComponent.SelectionProvider`, which has one method per kind: `isElementSelected`, `isLineSelected`, `isSlideSelected`, `isDecorationSelected`, `isLyricSelected`.
  

Selection state lives in `LineSelectionState` as **three parallel concepts**: an index range (`selectionBegin`/`selectionEnd`/`selectionAnchor`), a `@Nullable SelectedDecoration` (sealed, 3 variants: slide / ending / hairpin), and a `lineSelected` flag. The class doc notes the range and `isElementSelected` deliberately disagree (trailing breath mark), and that making them agree would change what the tie/beam/tuplet toggles operate on.

Cost per newly selectable type under the current structure: a `SelectedDecoration` variant + a `SelectionProvider` method + a color decision inside that type's renderer.
### 1.5 Two color facts that shape the work
- `LineInvariants.getElementColor(int elementIndex)` is keyed by **element index**. An index cannot name "the accidental of element 5" or "articulation 2 of element 5".
  
- `ArticulationRenderer` and `NoteRenderer.renderAccidental` **set no color at all** — they inherit whatever the caller left on the `Graphics2D`. By contrast `HairpinRenderer`, `TieRenderer`, `SlideRenderer`, and `BeamGroupRenderer` already call `setColor` with a selection-aware color.
  

* * *
## 2. Proposal
### 2.1 One registry, produced at layout time
```java
// songscribe.layout, or a new songscribe.hit
record HitRegion(Shape shapeSs, HitTarget target, int priority) {}
```

- `HitTarget` is the sealed vocabulary — essentially today's `HitResult` payloads, promoted out of the UI layer so layout can produce them.
  
- `Shape` rather than `Rectangle2D`, so a glissando strip or hairpin wedge can be its true outline, tested with `contains` on click.

  The drag-rectangle path in `LineSelectionHandler` does **not** query the registry. The registry stores every element rect expanded to a minimum clickable size, whereas a rubber band should catch exactly what it visually covers, so the two want different geometry from the same element. Both go through `ElementHitGeometry.elementHitRectSs`, which takes an `expandToMinimum` flag — one function, so the two rects cannot drift. Drag selection therefore still walks the line's elements itself, and as before this refactor it catches notes only, not decorations.
  
- **Registration happens at layout, never at render.** Registering at render is what the slide cache does today, and it means a hit test before the first paint returns nothing, the registry must be invalidated on every repaint including repaints caused by selection, and — as §1.2 shows — the render-time values get read by unrelated consumers like the MusicXML writer.
  
- Resolution rule: **highest priority containing the point wins, ties broken by smallest area.** The area tiebreak is what keeps "everything selectable" from degenerating — a note head inside an ending's box wins without anyone hand-ordering the two.
  
- **Priority is explicit data, not insertion order.** Insertion order is paint order (back-to-front), which is not hit priority; iterating a flat registry in registration order silently changes which item wins wherever regions overlap. That gets worse once endings, tuplet brackets, and hairpins are all selectable, since their boxes routinely swallow notes.
  
- One coordinate space fixed at construction (component-space staff spaces), so `RenderingUtils.layoutYToComponentYSs` stops being a per-tester concern.
  
- `LineSelectionHandler.hitTest` collapses to: build point → query the line's hit map → return. The `HitTester` cascade disappears; the six testers become registration sites in layout.
  
- This replaces the dead `ElementBoundsSs` layer (§1.3) rather than becoming a fourth overlapping bounds concept.
  

Initial priorities port the existing six-way order verbatim; let the area tiebreak take over only for newly added types.
### 2.2 Hit shapes are hand-built from layout data
Every hit shape is composed from geometry layout already produces. No recorded ink.

| Kind | Hit shape | Built from |
| --- | --- | --- |
| Note heads, articulations, fermatas, accidentals, clefs, key signatures, barlines | rect | existing layout geometry |
| Hairpin | closed `Path2D` — the wedge itself | `DecorationLayout` |
| Glissando | `Path2D` — the rotated strip | endpoint math, moved to layout (§1.2) |
| Tie / slur | bounding box | layout curve endpoints |
| Beam group | bounding box of every beam in the group | beam group layout |

Two shapes are exact and two are deliberately coarse:

- The **hairpin** wedge is a closed path and nothing can sit inside it, so the closed shape is exactly right — no over-coverage to resolve.
  
- The **glissando** is a rotated strip, expressible as a four-point path. The work is not the shape, it's relocating `computeEndpoints(src, tgt)` out of `SlideRenderer.render` into layout. That deletes the `cached*` fields from `StaffElement`, and fixes the save-before-paint coordinate loss in §1.2 as a side effect.
  
- **Ties and slurs** use the bounding box, which over-covers: the box of an arc contains the notes it spans and a large empty region above them. That over-coverage is wanted, not tolerated — a slur is a thin curve and hard to click precisely, so the box gives it a generous target, and clicking the empty space under an arc selects the slur. The overlaps that would otherwise be wrong are resolved by ordering: articulations and decorations outrank slurs in priority (so corner overlaps go to the articulation), and the area tiebreak hands note heads to the note.
  
- **Beams** use the bounding box of all beams in a single beam group, not per-beam shapes.
### 2.2a A same-pitch glissando is no longer a state the document may hold
A decision taken alongside the work above, recorded here because it changes editing behavior and is *not* required by hit testing.

Before: a glissando joining two notes at the same pitch stayed in the document and was simply not drawn (`SlideRenderer` returned early). Moving either note off that pitch brought it back.

Now: a pitch shift that lands two glissando-connected notes on one pitch removes the glissando from the model, in the same undo step as the shift. Undo restores it; moving the note back does not.

The reasoning is that a glissando with no distance to traverse is not a thing the document should be able to contain — the slide tool already refuses to create one, so allowing a pitch shift to produce one made the model's rules depend on how the state was reached. `Line.isSamePitchAsFollower` is the single definition of that condition, and every caller — the slide tool, the pitch shift, layout, and render — now asks it.

Hit testing did not force this. Layout withholds geometry for a same-pitch glissando either way, so nothing would have been clickable. The two changes are independent and this one is a product decision.
### 2.3 Draw selection by recoloring at the draw site
Make the selection _be_ a `HitTarget`:

```java
// LineSelectionState
private @Nullable HitTarget selected;   // replaces SelectedDecoration

// SelectionProvider collapses to:
boolean isSelected(HitTarget target);
```

Each renderer asks `invariants.colorFor(target)` — one line, at a point where it already sets a color. The rule lives centrally in `LineInvariants`; only the lookup moves.

Given §1.5, the per-type cost is uneven:

- `HairpinRenderer`, `TieRenderer`, `SlideRenderer`, `BeamGroupRenderer` already call `setColor` with a selection-aware color and need only their argument swapped.
  
- `ArticulationRenderer` and `NoteRenderer.renderAccidental` need a color decision **introduced** where none exists, wrapped in ambient-color save/restore so the rest of the note keeps drawing in its own color.
  
- `colorFor(HitTarget)` is a genuine addition, not a rename: the index-keyed `getElementColor(int)` cannot express a sub-element target, and has to survive alongside it for the range-selection path (§3, risk 1).
  
### 2.4 The new targets, checked against the DOM
Verified against `songscribe.dom`:

| Target | DOM status | Registers as |
| --- | --- | --- |
| Articulations (accent, staccato, …) | `Articulation extends LineElement`, added via `StaffElement.addArticulation` as both an articulation and a `LineElement` child | Element target; rect |
| Fermata and other attached decorations | `FermataAttachment extends Attachment extends LineElement` | Element target; rect |
| Beams | `Beam extends RangeElement extends LineElement` | Element target; group bbox |
| Accidentals | **Not an element.** `accidental` is an enum field on `StaffElement`, alongside `isAccidentalInParentheses` | Sub-element target; rect |

Two consequences:

- Articulations and attachments already sit in the `LineElement` child hierarchy with their own `getPositionSs`, so they are the cheapest additions — exactly the case the registry is for, one registration line each.
  
- Accidentals need `HitTarget` to admit a _part of_ an element, not just an element: something like `AccidentalPart(StaffElement owner)`. The geometry already exists — `NoteGeometry` produces `AccidentalBounds(leftSs, widthSs, topSs, botSs)` at layout time and `VerticalStackingCalculator` consumes it — so registration routes an existing record into the registry rather than computing anything new.
  

* * *
## 3. Risks, ranked
1. **Range selection doesn't fit a single** `HitTarget`**.** The `selectionBegin..selectionEnd` range is what ties, beams, and tuplets are built from. It must survive alongside the target, not be absorbed into it — and with sub-element targets the gap widens, because an index range has no way to name an accidental or a single articulation. The index-keyed `getElementColor(int)` stays for the range path; `colorFor(HitTarget)` is added beside it.
  
2. **Sub-element targets are a one-way door.** Whether `HitTarget` admits "part of an element" has to be decided before the first variant is written; retrofitting touches every variant, every `isSelected` call site, and the `SelectionProvider` collapse in §2.3.
  
3. **Moving glissando endpoints to layout has a consumer outside hit testing.** `MusicXmlNotationsWriter` reads the `cached*` fields. Changing where they come from has to keep the writer working, and its current "no cached geometry → emit slide without coordinates" fallback should become unreachable rather than being left in place.
  
4. **Priority as data is easy to get subtly wrong on the first pass.** Port the existing order verbatim; change nothing about it in the same step. The one new ordering constraint is that articulations and decorations must outrank ties/slurs (§2.2).
  

* * *
## 4. The preview/insertion target path (separate, and stays separate)
A confirmed-separate mechanism. Preview and insertion elements aren't in the line and render with `overrideElementXSs` / preview shift (see `LineRenderer.computeOverrideXSs`). They don't register with the hit registry, because they aren't selectable.

How the preview element decides which note it "hits" — `PreviewElementManager.trackMouse(LineComponent, MouseEvent)`:

1. View px → doc px (`ViewScale.toDocPx`) → staff spaces. In grace mode the X is overridden by `graceModeManager.getLockedInsertionXSs()`.
  
2. Mouse Y → `calculateStaffPositionFromMouse` — a quantized staff step used to position the ghost, **not** to pick a target.
  
3. Two X-only queries against `LayoutResult`:
  

```java
var xIndex     = layoutResult.findInsertionIndex(mouseXss, line);  // which slot
var elementAtX = layoutResult.findElementAtXSs(mouseXss, line);    // which head, or -1
```

`findElementAtXSs` walks `elementColumns` and returns the first element where `mouseXSs ∈ [column.getXSs(), column.getXSs() + column.getRightExtentSs()]`. Its javadoc: _"Only the horizontal (X) dimension is checked; Y position is ignored."_ `findInsertionIndex` runs that same test first and falls through to gap-slot logic only on a miss.

So the preview's target is **the element whose layout column horizontally spans the mouse X** — not a hit rect, not `ElementHitTest`, not the `HitTester` cascade.

Y participates only as a staff-position comparison used for change detection:

```java
var newXMatch = elementAtX >= 0;
var newYMatch = newXMatch
    && Math.abs(staffPosition - line.getElement(elementAtX).getStaffPosition()) <= 1;
```

`newYMatch` feeds `yPosSpMatchesElement`, which decides whether the overlay's display list needs rebuilding. The replacement target is `newXMatch` alone.

Policy filters then apply in `getHoveredElementLocation()`: null for a breath mark (never replaces), for a slide tool (highlights connected notes via `isSlidePreviewNote` instead), and for a grace note under the cursor (never replaceable). Separately `isPositionBlockedByTerminal` and `isBreathMarkInsertionBlocked` suppress the ghost. The consumer is `LineInvariants.isElementHovered` → `REPLACED_ELEMENT_COLOR`.

**Why it should not fold into the selection registry:**

- It's deliberately X-only. Replacement targeting wants a full-height vertical band over the column so the user can approach a note from any pitch — exactly what a tight hit shape does _not_ give.
  
- It resolves gaps, not just elements. `findInsertionIndex` returns a slot between elements — a target with no geometry at all.
  

The only part worth sharing between the two paths is the coordinate conversion at the front (view px → doc px → staff spaces), which `trackMouse` and `LineSelectionHandler.hitTestViewPoint` currently each do on their own.

* * *
## 5. Order of work
**First, settle the** `HitTarget` **vocabulary, specifically whether it admits sub-element targets.** Accidentals cannot be selectable without it (§2.4), and it has to be right before the first variant is written (§3, risk 2). Everything else is additive; this is not.

Then:

1. **Define** `HitTarget` **and** `HitRegion`**, and port the existing six testers to registration sites**, with priorities copied verbatim from the current cascade. No new selectable kinds — a pure refactor with identical behavior, verifiable against current hit behavior.
  
2. **Add** `colorFor(HitTarget)` **to** `LineInvariants` beside the existing `getElementColor(int)`, and collapse `SelectionProvider`'s five methods to `isSelected(HitTarget)`. Still no new kinds.
  
3. **Move glissando endpoint math from** `SlideRenderer.render` **to layout**, register the strip as a `Path2D`, delete the `cached*` fields from `StaffElement`, and repoint `MusicXmlNotationsWriter` (§3, risk 3).
  
4. **Add articulations and fermatas.** Both are already `LineElement` children with their own position; both register a rect and need a new color decision point plus ambient-color save/restore.
  
5. **Add accidentals**, routing the existing `AccidentalBounds` from `NoteGeometry` into the registry. This is the step that exercises sub-element targets end to end.
  
6. **Add beams**, registering the group bounding box.
  

Steps 1–3 are worth doing even if the new selectable kinds are deferred: they delete the `HitTester` cascade, the dead `ElementBoundsSs` layer, and the `cached*` render-scratch fields on `StaffElement`, and they fix the save-before-paint glissando coordinate loss — all without changing what the user sees.

* * *

comments:
  c14:
    body: "Done — §2.2 is now hand-built shapes from layout data, and RecordingGraphics2D is out of the proposal entirely. Your four calls are the shape table: hairpin closed Path2D, glissando Path2D strip, tie/slur bbox, beam-group bbox. Dropping ink also removes the recording-cost risk, which was ranked #1 in the previous draft."
    by: AI
    at: "2026-08-01T02:22:45.000Z"
    re: c9
  c15:
    body: "The glissando Path2D is the one that isn't free. Its endpoint math lives in SlideRenderer.render, which calls computeEndpoints and writes cachedStartX/Y, cachedAngle, cachedCos/Sin, cachedLength onto the Glissando — so building the strip at layout time means relocating that computation. While checking it I found the cache has a consumer outside rendering: MusicXmlNotationsWriter.writeSlide reads those fields when saving and emits a slide with no coordinates when hasCachedGeometry is false, so saving a file that has not been painted silently drops glissando coordinates. Moving the math to layout fixes that, but the writer has to be repointed in the same step — now §3 risk 3 and step 3 of §5."
    by: AI
    at: "2026-08-01T02:22:45.000Z"
    re: c12
  c16:
    body: "Done. Removed the Option 2 rejection, the whole evaluation-of-starting-proposals section, and the C2 overlay-pass rejection with its antialiasing analysis. Sections renumbered: findings (§1), proposal (§2), risks (§3), preview path (§4), order of work (§5). Two things from the removed sections were kept, restated as decisions rather than rejections, because the reasoning is still needed to implement correctly: registration happens at layout and never at render, and priority is explicit data rather than insertion order."
    by: AI
    at: "2026-08-01T02:22:45.000Z"
    re: c8
  c19:
    body: "Removed. The two conditions in hitTestStaffLine are ANDed — within STAFF_HIT_RADIUS_SS of the middle line AND isWithinHeaderXSs — and a slur never overlaps the header, so there is no conflict. I also rewrote the §1.2 table row: it said 'radius test plus a header-X check', vague enough that I misread it as a filter on something broader. It now says outright that the line is selectable only from the header."
    by: AI
    at: "2026-08-01T02:29:00.000Z"
    re: c1
  c20:
    body: "Removed."
    by: AI
    at: "2026-08-01T02:29:00.000Z"
    re: c2
suggestions: {}
