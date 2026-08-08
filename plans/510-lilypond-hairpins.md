# LilyPond Hairpins (refs #510)

**Type:** Master plan  <br>
**Created:** 2026-08-07  <br>
**Status:** Pending

## Context

Issue #510 asked how LilyPond renders hairpins versus SongScribe. The comparison
(LilyPond `lily/hairpin.cc`, `lily/dynamic-align-engraver.cc`,
`scm/define-grobs.scm` `Hairpin` / `DynamicText` / `DynamicLineSpanner` /
`NoteColumn`) found four engraving gaps this plan closes:

1. **Mouth height.** LilyPond's `Hairpin.height` (0.6666) is a *half*-height —
   `Hairpin::print` (`lily/hairpin.cc:335-336`) draws the two segments at
   `Offset(x, starth)` and `Offset(x, -starth)` where `starth == height`, so the
   full opening is 1.3332 ss. `Hairpin.HAIRPIN_OPENING_HEIGHT_SS` is 1.25 ss,
   inherited from an old 10px constant.
2. **No shared dynamics baseline.** LilyPond puts every hairpin and text dynamic
   in one `DynamicLineSpanner` and gives them a common Y, so `p ———< f` reads as
   one horizontal line. SongScribe stacks hairpins first and then text dynamics
   *on top of them* in the same skyline, so they land at different heights.
3. **No horizontal padding against neighbours.** LilyPond's `bound-padding`
   (1.0 ss) pulls the wedge end back from an adjacent text dynamic and from a
   barline, meets back-to-back hairpins at the shared column centre, and stops a
   hairpin at a rest's left edge. SongScribe always lands on the notehead edge.
   Only the text-dynamic case survived implementation — see the corpus section
   below for why the other three are unreachable here.
4. **No minimum length.** LilyPond enforces `minimum-length 2.0` as a spacing rod
   that pushes the notes apart. SongScribe clamps the drawn width after the fact
   in `Hairpin.getSpanWidthSs`, which has no effect on spacing.

### The editor invariant this plan is built on

**No element from a hairpin's anchor through its end, inclusive, may carry a
`DynamicAttachment`.** Enforced in both directions:

- `ui/action/DynamicMarkingAction.java:143` disables the dynamic action when
  `line.isInHairpinRange(noteIndex)`, and `SpanLookup.isInHairpinRange:269`
  resolves through `Span.containing`, which is inclusive of both endpoints.
- `ui/MusicEditOperations.java:528` calls
  `stripPointDynamics(line, anchorIndex, endIndex)` when a hairpin is added,
  deleting the dynamic from every element in that inclusive range.

So the only hairpin/dynamic adjacency SongScribe can produce is a dynamic on the
element **immediately before the anchor** or **immediately after the end** — `p`
on note 1, crescendo anchored at note 2. Every rule below targets those
neighbours. There is deliberately **no** handling for a dynamic on or inside
`[anchorIndex, endIndex]`: the state is unreachable, and foreign MusicXML (the
only import that could produce it) is not supported.

### Baseline endpoints are already correct — do not change them

`x1Ss = anchorColumn.getXSs()` and
`x2Ss = endColumn.getXSs() + endColumn.getNoteheadWidthSs()` stay as the
no-neighbour default. This is not a deferred divergence: `NoteColumn`'s
`bound-alignment-interfaces` is `(rhythmic-head-interface stem-interface)`
(`scm/define-grobs.scm:2576`), so `Axis_group_interface::generic_bound_extent`
filters the column down to noteheads and stems, excluding accidentals and
augmentation dots. LilyPond's endpoints anchor on notehead edges, and
SongScribe's baseline already matches. This phase only *adds* the
neighbour-driven rules on top.

### Corpus evidence (22,818 `.abc` files, 121 resolved hairpins)

Re-measured 2026-08-07 after the first reading conflated "the bound element *is*
X" with "the element *adjacent to* the bound is X". Only the former is what an
endpoint rule can test, and that distinction killed three rules.

| Rule | Corpus instances | Note |
|---|---|---|
| Minimum length | **67/121 (55%)** span exactly two adjacent notes | `MIN_HAIRPIN_NOTES` is 2, so this is also the smallest hairpin the editor can make |
| Adjacent text dynamic | 8/121 (7%) — 6 after the end, 2 before the anchor | A further 28 carry a dynamic *on* a bound element, which the editor strips; those are not adjacency |
| Back-to-back hairpins | 57 hairpins (47%) across 25 files | Kept — real practice; editor support tracked separately |
| Rest as end element | 2/121 (2%) | Kept for the same reason |
| Bound element *is* a barline | **0/121** | **Removed.** The earlier "14 after, 4 before" counted barlines *next to* the bound, which no endpoint rule tests |
| Dynamic strictly inside a span | **0** | Confirms the invariant above |

**The editor cannot produce the back-to-back or rest configurations today**, and
there is no ABC importer, so nothing in the corpus reaches the layout code
directly:

- `isHairpinEligibleSpan` requires `getElement(end).getType().isPitchedNote()`,
  so an endpoint is never a rest or a barline.
- `Line.canAnchorHairpin` requires a pitched or grace note to start one.
- `resolveHairpinAction` returns `BLOCKED` when an opposite-type hairpin is a
  candidate, and `Line.addHairpin` absorbs adjacent same-type spans into one, so
  two hairpins can never share an element.

The back-to-back and rest geometry is nonetheless **kept**: both are ordinary
engraving practice, the corpus proves it, and the engine should be ready for the
editor work that will create them — tracked as issue #743.
`HairpinEndpointsTest` drives both directly.

Only the barline rule was removed. LilyPond needs it because it bounds spanners
on `NoteColumn`s that may be non-musical items; a SongScribe bound is always a
note, and the corpus shows no barline-bounded hairpin at all.

Out of scope by explicit decision: broken-hairpin line-break continuation
(SongScribe spans never cross a `Line`), `to-barline`, and `circled-tip` /
niente.

### Span index resolution — mandatory

Never call `Span.getAnchorElementIndex()` / `getEndElementIndex()` in this work.
`SpanLookup.anchorIndexOf`'s Javadoc forbids it (they answer from whichever line
the endpoint belongs to, and return `-1` when unpositioned, which turns into
silently wrong arithmetic rather than a failure). Use the existing predicates:

```
line.hasSpan(Hairpin.class, Span.exactly(i, i + 1))    // adjacency test, allocates nothing
line.findSpans(Hairpin.class, Span.containing(i))      // spans covering element i
line.findSpans(Hairpin.class, (anchor, end) -> …)      // SpanBound-typed custom predicate
line.anchorIndexOf(span) / line.endIndexOf(span)       // returns SpanBound; narrow to SpanBound.At
```

## Pipeline

```
                       ┌──────────────────────────────────────────┐
                       │ INPUT: Line with Hairpin span(s), and    │
                       │ DynamicAttachment only on elements       │
                       │ OUTSIDE [anchorIdx, endIdx]  (invariant) │
                       └────────────────────┬─────────────────────┘
 ══ PHASE 1 ══════════════════════════════  ▼  ══════════════════════════════
  ElementColumnBuilder.buildColumn(el, line, i)
     └─ line.hasSpan(Hairpin, Span.exactly(i, i+1)) ──► setHairpinEndsAtNextElement

  HorizontalSpacingCalculator.buildSpring(prev, curr)
     └─ strut = max( …4 existing floors…, hairpinReservationFloorSs )   ◄── NEW 5th
            └─ prev.hasHairpinEndingAtNextElement()
                   ? MINIMUM_LENGTH_SS − curr.getNoteheadWidthSs() : 0
 ══ PHASE 2 ══════════════════════════════  ▼  ══════════════════════════════
  HairpinEndpoints.compute(hairpin, line, columnsByElement)          [NEW FILE]
     ├─ resolveSpan == null ─────────────────────────────────► return null
     │
     ├─ x1 ┌ hairpin ends at anchorIdx ──► anchorCenterX + BACK_TO_BACK_PADDING_SS
     │     ├ dynamic at anchorIdx−1 ────► dynamicRightEdgeSs + BOUND_PADDING_SS
     │     └ default ───────────────────► anchorX
     │
     ├─ x2 ┌ hairpin starts at endIdx ──► endCenterX − BACK_TO_BACK_PADDING_SS
     │     ├ dynamic at endIdx+1 ───────► dynamicLeftEdgeSs − BOUND_PADDING_SS
     │     ├ endColumn.isRest() ────────► endColumn.getLeftEdgeXSs()
     │     └ default ───────────────────► endX + noteheadW
     │
     └─ x2 − x1 < MINIMUM_LENGTH_SS ────────────────► x2 = x1 + MINIMUM_LENGTH_SS
 ══ PHASE 3 ══════════════════════════════  ▼  ══════════════════════════════
  DynamicGrouper.group(line)                                          [NEW FILE]
     ├─ hairpins sharing an element ──────► merged into one group
     ├─ dynamic at anchorIdx−1 / endIdx+1 ► joins that hairpin's group
     └─ dynamic touching no hairpin ──────► singleton group
                                          │
  groups sorted by leftmost X ───────────►│
     ├─ singleton ──► existing placeAndReserveClamped path (unchanged)
     └─ 2+ members ─► PASS 1  (compute only, reserve nothing)
                        hairpin  refY = anchoredInnerEdgeYSs − height/2
                        dynamic  refY = clampedInnerEdgeYSs − bboxBottom
                                             − DYNAMIC_TEXT_BASELINE_OFFSET_SS
                        groupRefY = min(refY over members)   ← most outward wins
                      PASS 2  (place + reserve, left → right)
                        placeAtInnerEdge(…, innerEdge derived from groupRefY, …)
```

## Status Dashboard

| Phase | Description | Status | Sub-plan |
|-------|-------------|--------|----------|
| 1 | [Constants and Minimum-Length Spacing Floor](#-phase-1-constants-and-minimum-length-spacing-floor) | ✅ Complete | — |
| 2 | [Endpoint Bound Padding](#-phase-2-endpoint-bound-padding) | ✅ Complete | — |
| 3 | [Shared Dynamics Baseline](#-phase-3-shared-dynamics-baseline) | ✅ Complete | — |
| 4 | [Endpoint and Spacing Tests](#-phase-4-endpoint-and-spacing-tests) | ✅ Complete | — |
| 5 | [Grouping and Baseline Tests](#-phase-5-grouping-and-baseline-tests) | ✅ Complete | — |
| 6 | [Docs](#-phase-6-docs) | ✅ Complete | — |
| 7 | [Manual UI Verification](#-phase-7-manual-ui-verification) | ⏸️ Blocked by 5, 6 | — |

Phases 2 and 3 both rewrite `StructuralStacker.stackHairpins`, and phase 3
consumes the endpoint geometry phase 2 introduces, so 1 → 2 → 3 is genuinely
sequential. Phase 4 can run as soon as 2 lands; phases 5 and 6 can run
concurrently once 3 lands.

---

## ✅ Phase 1: Constants and Minimum-Length Spacing Floor

**Status:** Complete  <br>
**BlockedBy:** —  <br>
**Files:** src/main/java/songscribe/dom/Hairpin.java, src/main/java/songscribe/layout/ElementColumn.java, src/main/java/songscribe/layout/ElementColumnBuilder.java, src/main/java/songscribe/layout/HorizontalSpacingCalculator.java  <br>
**Recommended model/effort:** Sonnet 4.6, medium effort — constant changes plus one new spacing floor that mirrors an existing one line-for-line

### Tasks

1. In `src/main/java/songscribe/dom/Hairpin.java`, change
   `HAIRPIN_OPENING_HEIGHT_SS` from `1.25` to `1.3332` and replace the trailing
   `// 10px` comment with Javadoc explaining the derivation: LilyPond's
   `Hairpin.height` grob property (`scm/define-grobs.scm`) is `0.6666`, and
   `Hairpin::print` (`lily/hairpin.cc:335-336`) draws the two wedge segments at
   `+starth` and `-starth` where `starth == height`, so the full mouth opening
   is twice the property value.

   Add three more constants in the same file — all four are properties of the
   same LilyPond `Hairpin` grob and belong in one place, so phase 2 declares no
   constants of its own:

   ```java
   /**
    * Minimum drawn length of a hairpin in staff spaces, from LilyPond's
    * {@code Hairpin.minimum-length} (scm/define-grobs.scm). LilyPond applies this
    * as a spacing rod via {@code ly:spanner::set-spacing-rods}, so the notes are
    * pushed apart rather than the wedge being squashed.
    */
   public static final double MINIMUM_LENGTH_SS = 2.0;

   /**
    * The gap a hairpin endpoint keeps from an adjacent text dynamic,
    * from LilyPond's {@code Hairpin.bound-padding} (scm/define-grobs.scm).
    */
   public static final double BOUND_PADDING_SS = 1.0;

   /**
    * The gap each of two back-to-back hairpins keeps from their shared column
    * centre. {@code Hairpin::print} uses {@code padding / 3} for this case
    * ("we're not adjacent to a text-dynamic, and we may move closer"), so it is
    * derived from {@link #BOUND_PADDING_SS} rather than written as a literal.
    */
   public static final double BACK_TO_BACK_PADDING_SS = BOUND_PADDING_SS / 3;
   ```

   Do **not** touch `getSpanWidthSs` in this phase — phase 2 removes it, and
   removing it here would break the tree until phase 2 lands.

2. In `src/main/java/songscribe/layout/ElementColumn.java`, add a private
   `boolean hairpinEndsAtNextElement = false;` field with a getter
   `hasHairpinEndingAtNextElement()` and a setter
   `setHairpinEndsAtNextElement(boolean)`. Follow the existing mutable-field
   pattern used by `minGapToNextSyllableSs` / `setMinGapToNextSyllableSs` —
   a setter, **not** a new constructor parameter, so the several `ElementColumn`
   construction sites (`ElementColumnBuilder.buildColumn`, `buildDetachedColumn`,
   `buildColumnForBeam`, and `src/test/java/songscribe/layout/ElementColumnTestHelper.java`)
   need no changes.

   Javadoc it as: true when a `Hairpin` on the line has this element as its
   anchor and the immediately following element as its end. State explicitly
   that it stays `false` on columns built by `buildDetachedColumn` and
   `buildColumnForBeam`, which have no `Line` to query and are never fed to the
   spacing solver — that is correct, not an oversight, and phase 4 pins it with
   a test.

3. In `src/main/java/songscribe/layout/ElementColumnBuilder.buildColumn` (the
   overload that receives `(StaffElement element, Line line, int index)` — see
   `buildColumns`, which calls `buildColumn(line.getElement(i), line, i)`), set
   the new flag:

   ```java
   column.setHairpinEndsAtNextElement(
       line.hasSpan(Hairpin.class, Span.exactly(index, index + 1)));
   ```

   Use `hasSpan`, not `findSpans(...).isEmpty()`: this runs once per element on
   every relayout, and `findSpans` allocates a fresh `ArrayList` per call while
   `hasSpan` short-circuits. `SpanLookup.isInHairpinRange` uses the same shape.

4. In `src/main/java/songscribe/layout/HorizontalSpacingCalculator.java`, add a
   private static method modelled directly on the existing
   `glissandoReservationFloorSs(ElementColumn prev, ElementColumn curr)` (same
   file) — read that method first and mirror its shape and Javadoc style:

   ```java
   private static double hairpinReservationFloorSs(ElementColumn prev, ElementColumn curr) {
       if (!prev.hasHairpinEndingAtNextElement()) {
           return 0;
       }

       return Hairpin.MINIMUM_LENGTH_SS - curr.getNoteheadWidthSs();
   }
   ```

   The floor is a delta-X between the two column origins. The hairpin is drawn
   from `prev`'s origin to `curr`'s origin plus `curr`'s notehead width, so a
   drawn length of at least `MINIMUM_LENGTH_SS` requires an origin-to-origin
   delta of at least `MINIMUM_LENGTH_SS - noteheadWidth`. Fold the result into
   the `strutSs` maximum in `buildSpring(ElementColumn, ElementColumn, double,
   ElementColumn, ElementColumn)` alongside `calculateMinimumColumnSpacingSs`,
   `syllableCollisionFloorSs`, `glissandoReservationFloorSs` and
   `graceCompressionFloorSs`.

   State in the Javadoc that this only constrains a hairpin whose anchor and end
   are adjacent columns. LilyPond's rod spans the whole interval; SongScribe's
   `Spring` model has only per-pair struts. This is not a meaningful gap — 69%
   of corpus hairpins span exactly two adjacent notes, and every longer one is
   already wider than `MINIMUM_LENGTH_SS`.

   Note that phase 2's adjacent-dynamic rule can only make the wedge *longer*
   (the endpoint is taken from the dynamic's extent, which lies outside the
   span), so no dynamic-overhang term is needed here.

5. Run `./scripts/compile.sh` and `./scripts/test.sh` and report SUCCESS /
   green. Existing assertions that hard-code the old 1.25 mouth height (look in
   `src/test/java/songscribe/layout/StructuralTierStackingTest.java` and
   `src/test/java/songscribe/layout/stacking/StructuralStackerTest.java`) may
   need their expected values updated; update them to the new geometry rather
   than reverting the constant.

---

## ✅ Phase 2: Endpoint Bound Padding

**Status:** Complete  <br>
**BlockedBy:** 1  <br>
**Files:** src/main/java/songscribe/layout/HairpinEndpoints.java, src/main/java/songscribe/layout/stacking/StructuralStacker.java, src/main/java/songscribe/dom/Hairpin.java, src/test/java/songscribe/dom/HairpinTest.java  <br>
**Recommended model/effort:** Opus 4.8, high effort — four interacting placement rules with an explicit precedence order ported from C++

### Context this phase needs

Today `StructuralStacker.stackHairpins` (in
`src/main/java/songscribe/layout/stacking/StructuralStacker.java`) routes both
hairpin types through the generic `stackSpanElement(...)` helper, which computes
`anchorXSs = anchorColumn.getXSs()` and
`widthSs = element.getSpanWidthSs(anchorXSs, endXSs)`. `stackSpanElement` is
shared with tuplets and must keep working for them.

LilyPond's endpoint logic is `Hairpin::print` in
`~/Developer/projects/lilypond/lily/hairpin.cc`, lines 184–290 (the
`for (const auto d : {LEFT, RIGHT})` loop). Read it before implementing. Note
that `d == LEFT == -1` and `d == RIGHT == +1`, so a formula written `- d * p`
means `+ p` on the left and `- p` on the right.

The endpoint rules are **assignments, not clamps.** LilyPond sets
`x_points[d] = e[-d] - d * padding` straight from the *neighbour's* extent with
no clamp back to the note column (`hairpin.cc:218-220`), so a wedge whose anchor
is preceded by a text dynamic legitimately extends left past the anchor column's
origin. That is the intended appearance: the wedge runs from just clear of the
`p` to the end note.

Phase 3 replaces the `stackHairpins` call site this phase rewires. That rework
is deliberate — it leaves a compiling, testable intermediate state. Do not try
to anticipate phase 3's grouped pass here.

### Tasks

1. Create `src/main/java/songscribe/layout/HairpinEndpoints.java`, a final class
   with a private constructor (match the file-header licence block and Javadoc
   style of `src/main/java/songscribe/layout/EndingBracketGeometry.java`).
   Declare no constants of its own — the padding values live on
   `songscribe.dom.Hairpin` (phase 1). Expose one entry point returning a record
   of the two resolved X positions:

   ```java
   public record Endpoints(double x1Ss, double x2Ss) {}

   public static @Nullable Endpoints compute(
       Hairpin hairpin, Line line, Map<StaffElement, ElementColumn> columnsByElement)
   ```

   Return `null` when `ElementColumn.resolveSpan(hairpin, columnsByElement)`
   returns `null`, matching the existing early-out in `stackSpanElement`.

   Resolve the hairpin's own indices with `line.anchorIndexOf(hairpin)` and
   `line.endIndexOf(hairpin)`, narrowing each to `SpanBound.At`; return `null`
   if either is not `At`. Never call `hairpin.getAnchorElementIndex()`.

2. Add package-visible static helpers to `HairpinEndpoints` that return a text
   dynamic's X extent, so the endpoint rules and
   `StructuralStacker.stackTextDynamics` share one formula (phase 3 reuses them —
   do not duplicate the arithmetic):

   ```java
   static double dynamicLeftEdgeSs(ElementColumn column, DynamicAttachment dynamic)
   static double dynamicWidthSs(DynamicAttachment dynamic)
   ```

   `dynamicLeftEdgeSs` must reproduce exactly what
   `StructuralStacker.stackTextDynamics` computes today:
   `column.getXSs() + NoteGeometry.getNoteheadCenterXSs(column.getElement()) - dynamic.getContentWidthSs() / 2.0`.
   `dynamicWidthSs` returns `dynamic.getContentWidthSs()`. Then change
   `stackTextDynamics` to call these two helpers instead of computing
   `centeredXSs` inline.

3. Implement the LEFT endpoint (`x1Ss`) in `compute`, applying the first rule
   that matches, in this order — this is the precedence in `Hairpin::print`:

   - **Back-to-back hairpin.** Another `Hairpin` on the line has its end at
     `anchorIndex`. Query it as
     `line.findSpans(Hairpin.class, (anchorBound, endBound) -> endBound.isAt(anchorIndex))`,
     excluding `hairpin` itself. LilyPond: `x_points[d] = e.center() - d * padding / 3`
     with `d == LEFT`, so
     `x1Ss = anchorColumn.getNoteheadCenterXSs() + Hairpin.BACK_TO_BACK_PADDING_SS`.
   - **Adjacent text dynamic.** The element at `anchorIndex - 1` (guard the line
     start) carries a `DynamicAttachment`. LilyPond: `x_points[d] = e[-d] - d * padding`,
     i.e. the dynamic's right edge plus padding:
     `x1Ss = dynamicLeftEdgeSs(prevColumn, dynamic) + dynamicWidthSs(dynamic) + Hairpin.BOUND_PADDING_SS`.
     No clamp — this may fall left of `anchorColumn.getXSs()`, which is correct.
   - **Default.** `x1Ss = anchorColumn.getXSs()`.

4. Implement the RIGHT endpoint (`x2Ss`), same precedence:

   - **Back-to-back hairpin.** Another hairpin has its anchor at `endIndex`
     (`(anchorBound, endBound) -> anchorBound.isAt(endIndex)`). With `d == RIGHT`:
     `x2Ss = endColumn.getNoteheadCenterXSs() - Hairpin.BACK_TO_BACK_PADDING_SS`.
   - **Adjacent text dynamic.** The element at `endIndex + 1` (guard the line
     end) carries a `DynamicAttachment`:
     `x2Ss = dynamicLeftEdgeSs(nextColumn, dynamic) - Hairpin.BOUND_PADDING_SS`.
     No clamp.
   - **Rest.** `endColumn.isRest()` — LilyPond ends the hairpin at the rest's
     left edge (`hairpin.cc:268-271`, `x_points[RIGHT] = e[LEFT]`):
     `x2Ss = endColumn.getLeftEdgeXSs()`.
   - **Default.** `x2Ss = endColumn.getXSs() + endColumn.getNoteheadWidthSs()`.

   The `isBarline()` adjustment on each side was implemented here and then
   removed — see the corpus section. Do not reintroduce it.

   Finally, guard the degenerate case: if
   `x2Ss - x1Ss < Hairpin.MINIMUM_LENGTH_SS`, set
   `x2Ss = x1Ss + Hairpin.MINIMUM_LENGTH_SS`. LilyPond instead warns
   "crescendo too small" and collapses the width to 0 (`hairpin.cc:293-299`);
   extending rightward keeps the wedge visible. Phase 1's spacing floor makes
   this rare — it is a last-resort guard, not the primary mechanism.

5. Rewire `StructuralStacker.stackHairpins` to use the new geometry. For each
   `Crescendo` and each `Diminuendo` from `line.findSpans(...)`:

   ```java
   var endpoints = HairpinEndpoints.compute(hairpin, line, columnsByElement);

   if (endpoints == null) {
       continue;
   }

   var spanColumns = ElementColumn.resolveSpan(hairpin, columnsByElement);
   StackingUtils.stackAbove(structuralExtents, hairpin,
       endpoints.x1Ss(), endpoints.x2Ss() - endpoints.x1Ss(),
       hairpin.getContentHeightSs(), HAIRPIN_MARGIN_SS,
       spanColumns.anchor().getStaffPosition(), builder);
   ```

   Leave `stackSpanElement` in place — tuplets still use it.

6. `Hairpin.getSpanWidthSs` in `src/main/java/songscribe/dom/Hairpin.java` is now
   dead (its only remaining callers are tests). Delete the override and the
   `GetSpanWidthSs` nested test class in
   `src/test/java/songscribe/dom/HairpinTest.java`. Keep
   `testGetContentHeightSsEqualsHairpinOpeningHeightSs`. Verify with
   `jet_brains_find_referencing_symbols` on `Span/getSpanWidthSs` that no
   production caller remains for hairpins before deleting.

7. Run `./scripts/compile.sh` and `./scripts/test.sh` and report SUCCESS /
   green.

---

## ✅ Phase 3: Shared Dynamics Baseline

**Status:** Complete  <br>
**BlockedBy:** 2  <br>
**Files:** src/main/java/songscribe/layout/stacking/DynamicGrouper.java, src/main/java/songscribe/layout/stacking/StructuralStacker.java, src/main/java/songscribe/layout/stacking/StackingUtils.java, src/main/java/songscribe/dom/DynamicAttachment.java  <br>
**Recommended model/effort:** Opus 4.8, high effort — a new two-pass placement model that must interleave correctly with the existing single-pass skyline stacker

### Context this phase needs

`StructuralStacker.stackRemaining()` currently runs, in order: `stackHairpins`
(all hairpins), then `stackTextDynamics` per column, then `stackEndings`. Each
placement both *queries* and *reserves* against `structuralExtents` in one shot,
so a text dynamic placed after a hairpin is pushed outside it.

LilyPond instead collects hairpins and text dynamics into a `DynamicLineSpanner`
(`lily/dynamic-align-engraver.cc`) whose Y is computed once for the whole group.
Critically, the two member types are **not** aligned the same way on that line:

```
Hairpin      (self-alignment-Y . CENTER)
             (Y-offset . self-alignment-interface::y-aligned-on-self)
                 → the wedge's full Y-extent is centered on the reference line

DynamicText  (Y-offset . ,(scale-by-font-size -0.6))   ; "center on an 'm'"
                 → the glyph's BASELINE sits 0.6 ss below the reference line,
                   so its x-height centre — not its bounding-box centre —
                   lands on the line
```

That distinction is the point of the change. `p` has a descender, `f` has an
ascender and a descender, `mf` is nearly all x-height; centring each on its own
bounding box leaves them at visibly different heights, which is the defect #510
reports. Centring on the x-height is stable across every dynamic glyph.

Read these before implementing, in
`src/main/java/songscribe/layout/stacking/StackingUtils.java`:
`stackAbove`, `stackAtAnchor`, `placeAndReserve`, `placeAndReserveClamped`,
`placeAtInnerEdge`, `yGetExpanded`, `anchorCeilingSs`, `STAFF_TOP_INK_Y_SS`,
`STRUCTURAL_HORIZONTAL_MARGIN_SS`.

### Tasks

1. In `src/main/java/songscribe/layout/stacking/StackingUtils.java`, split the
   inner-edge computation out of the two placement methods so the grouper can
   reuse it rather than copying the arithmetic. Add two package-private methods
   and have the existing methods call them — this is a pure extraction, no
   behaviour change:

   ```java
   /** The inner edge {@link #stackAtAnchor} would compute for this footprint. */
   static double anchoredInnerEdgeYSs(
       Direction direction, StaffExtents extents,
       double xSs, double widthSs, double marginSs, int staffPosition)

   /** The inner edge {@link #placeAndReserveClamped} would compute for this footprint. */
   static double clampedInnerEdgeYSs(
       Direction direction, StaffExtents extents,
       double xSs, StaffExtents.Profile innerProfile,
       double paddingSs, double staffPaddingSs, double horizonPaddingSs)
   ```

   `stackAtAnchor` becomes `placeAndReserve(…, anchoredInnerEdgeYSs(…) …)` — note
   it currently passes `boundSs` and lets `placeAndReserve` subtract `marginSs`,
   so fold that subtraction into the new method and keep `placeAndReserve`'s
   existing signature for its other callers. `placeAndReserveClamped` becomes
   `placeAtInnerEdge(…, clampedInnerEdgeYSs(…) …)`.

   Also change `placeAtInnerEdge` from `private static` to package-private
   `static` and extend its Javadoc to note that a caller may pass a Y it
   computed for a group of elements rather than for this element alone.

2. In `src/main/java/songscribe/dom/DynamicAttachment.java`, add two accessors
   exposing the glyph's vertical extent relative to its baseline, mirroring the
   structure of the existing `getContentHeightSs()` (which reads
   `SMuFLMetadata.requireBBox(glyph).height()` and falls back to
   `DEFAULT_HEIGHT_SS` when `type.getGlyph()` is null):

   ```java
   /** Top edge of the glyph relative to its text baseline; negative (above it). */
   public double getContentTopSs()      // bbox.top(),    fallback -DEFAULT_HEIGHT_SS / 2
   /** Bottom edge of the glyph relative to its text baseline; positive (below it). */
   public double getContentBottomSs()   // bbox.bottom(), fallback +DEFAULT_HEIGHT_SS / 2
   ```

   The null-glyph branch exists only to satisfy nullability; every
   `DynamicType` has a glyph today, so it needs no test.

3. In `StructuralStacker`, add the LilyPond `DynamicText` Y-offset as a constant:

   ```java
   /**
    * How far below a dynamics group's shared reference line a text dynamic's glyph
    * baseline sits, from LilyPond's {@code DynamicText.Y-offset}
    * ({@code scale-by-font-size -0.6}, commented "center on an 'm'"). Placing the
    * baseline here puts the glyph's x-height centre on the line, which is stable
    * across glyphs whose ascenders and descenders differ.
    * <p>
    * Not to be confused with {@link NoteAttachedStacker#DYNAMIC_PADDING_SS}, which
    * happens to share this value but comes from {@code DynamicLineSpanner.padding}
    * and means something else entirely. Do not collapse the two.
    */
   static final double DYNAMIC_TEXT_BASELINE_OFFSET_SS = 0.6;
   ```

4. Create `src/main/java/songscribe/layout/stacking/DynamicGrouper.java`
   implementing the grouping rule. Members are every `Hairpin` on the line
   (`line.findSpans(Hairpin.class)`) and every `DynamicAttachment` found via
   `element.findAttachment(DynamicAttachment.class)` on each element.

   - Two hairpins whose inclusive index ranges overlap or touch are in the same
     group. Resolve those ranges through `line.anchorIndexOf` /
     `line.endIndexOf`, narrowing to `SpanBound.At`; skip any hairpin whose
     bounds do not both resolve.
   - A text dynamic on element index `i` joins a hairpin's group when
     `i == anchorIndex - 1` or `i == endIndex + 1` — the elements immediately
     outside the span. A dynamic can never sit on or inside `[anchor, end]`
     (see the editor invariant in Context), so there is no interior case to
     handle and none should be written.
   - A text dynamic adjacent to no hairpin forms a singleton group.

   Return groups in ascending X order of their leftmost member.

5. In `StructuralStacker`, replace the `stackHairpins(...)` + per-column
   `stackTextDynamics(...)` sequence inside `stackRemaining()` with a single
   grouped pass over `DynamicGrouper`'s output, keeping `stackEndings` last. For
   each group:

   - **Singleton text-dynamic group:** call the existing `stackTextDynamics`
     path unchanged (`StackingUtils.placeAndReserveClamped` with
     `NoteAttachedStacker.DYNAMIC_PADDING_SS`,
     `NoteAttachedStacker.DYNAMIC_STAFF_PADDING_SS`,
     `StackingUtils.STRUCTURAL_HORIZONTAL_MARGIN_SS`).
   - **Any group with two or more members:** run the two-pass placement in task 6.

   Grouped X order is **not** the same reservation order as today's
   all-hairpins-then-all-dynamics sweep, so singleton output is not guaranteed
   bit-identical: `STRUCTURAL_HORIZONTAL_MARGIN_SS` is a horizon padding, so each
   element looks beyond its own footprint for support and the order in which
   neighbours reserve can shift a result. Expect and accept small diffs in
   existing assertions for a singleton dynamic sitting near a hairpin; phase 5
   adds a test pinning the new expectation.

6. Implement the two-pass group placement. All dynamics sit above the staff, so
   "more outward" means a smaller Y, and `placeAtInnerEdge` treats the inner edge
   as the element's *bottom*.

   **Pass 1 — compute, reserving nothing.** For each member, resolve its
   footprint and the reference line it would demand on its own:

   - *Hairpin:* footprint from `HairpinEndpoints.compute(...)` — `xSs = x1Ss`,
     `widthSs = x2Ss - x1Ss`, `heightSs = hairpin.getContentHeightSs()`. Then
     ```java
     var innerEdgeYSs = StackingUtils.anchoredInnerEdgeYSs(Direction.UP,
         structuralExtents, xSs, widthSs, HAIRPIN_MARGIN_SS, anchor.getStaffPosition());
     var referenceYSs = innerEdgeYSs - heightSs / 2;
     ```
   - *Text dynamic:* footprint from `HairpinEndpoints.dynamicLeftEdgeSs` /
     `dynamicWidthSs`, `heightSs = dynamic.getContentHeightSs()`. Then
     ```java
     var innerEdgeYSs = StackingUtils.clampedInnerEdgeYSs(Direction.UP,
         structuralExtents, xSs, StaffExtents.Profiles.flat(widthSs).inner(),
         NoteAttachedStacker.DYNAMIC_PADDING_SS,
         NoteAttachedStacker.DYNAMIC_STAFF_PADDING_SS,
         StackingUtils.STRUCTURAL_HORIZONTAL_MARGIN_SS);
     var referenceYSs = innerEdgeYSs
         - dynamic.getContentBottomSs() - DYNAMIC_TEXT_BASELINE_OFFSET_SS;
     ```

   Keep each member's footprint from this pass — pass 2 must not recompute
   `HairpinEndpoints.compute`.

   The group's shared line is the most outward demand:
   `groupReferenceYSs = min over members of referenceYSs`.

   **Pass 2 — place and reserve.** For each member, in left-to-right X order,
   invert the same relation to get the inner edge to place at:

   ```java
   // hairpin
   var innerEdgeYSs = groupReferenceYSs + heightSs / 2;
   // text dynamic
   var innerEdgeYSs = groupReferenceYSs
       + DYNAMIC_TEXT_BASELINE_OFFSET_SS + dynamic.getContentBottomSs();

   StackingUtils.placeAtInnerEdge(Direction.UP, structuralExtents, member,
       xSs, widthSs, heightSs, innerEdgeYSs,
       StaffExtents.Profile.flat(widthSs),
       marginSs,   // HAIRPIN_MARGIN_SS for a hairpin, DYNAMIC_PADDING_SS for a text dynamic
       builder);
   ```

   The whole point of the two passes is that no member's reservation influences
   another member's computed reference line — every `referenceYSs` must be
   computed before any `placeAtInnerEdge` call for that group.

7. Run `./scripts/compile.sh` and `./scripts/test.sh` and report SUCCESS /
   green. Assertions in
   `src/test/java/songscribe/layout/StructuralTierStackingTest.java` and
   `src/test/java/songscribe/layout/stacking/StructuralStackerTest.java` that
   assume a text dynamic sits outside an adjacent hairpin now describe the
   old behaviour; update their expected values to the shared baseline rather
   than working around the change.

---

## ✅ Phase 4: Endpoint and Spacing Tests

**Status:** Complete  <br>
**BlockedBy:** 2  <br>
**Files:** src/test/java/songscribe/layout/HairpinEndpointsTest.java, src/test/java/songscribe/layout/HorizontalSpacingCalculatorSpringTest.java, src/test/java/songscribe/layout/ElementColumnBuilderTest.java  <br>
**Recommended model/effort:** Sonnet 4.6, medium effort — mechanical test authoring against two already-implemented, well-specified units

### Tasks

1. Read `.agents/guides/testing-common.md` and `.agents/guides/testing-unit.md`
   before writing anything; they carry conventions that override JUnit defaults.
   Use `src/test/java/songscribe/layout/ElementColumnTestHelper.java` and the
   fixtures in `src/test/java/songscribe/layout/EndingLineFixture.java` as models
   for building detached lines and columns.

2. New `src/test/java/songscribe/layout/HairpinEndpointsTest.java`, one nested
   class per rule: default endpoints (no neighbours); rest as the end element;
   back-to-back hairpins sharing an element (assert both sides land on
   `getNoteheadCenterXSs() ∓ Hairpin.BACK_TO_BACK_PADDING_SS`, and that the two
   tips do not meet); and the `Hairpin.MINIMUM_LENGTH_SS` degenerate-case guard.
   The rest and back-to-back cases are unreachable from the editor, so these
   tests are the only thing exercising that geometry — build the configuration
   on the model directly. (A barline-bound class was written here and then
   deleted with the rule it covered.)

3. In the same file, cover the adjacent-text-dynamic rules on both sides, and
   assert the **direction** explicitly: a dynamic on the element before the
   anchor puts `x1Ss` **left of** `anchorColumn.getXSs()`, and a dynamic on the
   element after the end puts `x2Ss` **right of**
   `endColumn.getXSs() + getNoteheadWidthSs()`. The rule is an assignment from
   the dynamic's extent, not a clamp to the note column — a test that only
   asserts "the pullback happened" would pass a clamped implementation too.

   Also assert that a dynamic two elements away from either bound changes
   nothing.

4. Extend `src/test/java/songscribe/layout/HorizontalSpacingCalculatorSpringTest.java`
   with a case asserting that a two-note line whose notes carry a hairpin gets a
   spring strut of at least
   `Hairpin.MINIMUM_LENGTH_SS - curr.getNoteheadWidthSs()`, and that the same
   line without a hairpin does not.

5. Add a case (in `ElementColumnBuilderTest` if it exists, otherwise beside the
   spring test) asserting `hasHairpinEndingAtNextElement()` is false for a
   column built by `buildDetachedColumn` and by `buildColumnForBeam`, even on a
   line that has such a hairpin. Those paths have no `Line` and are never fed to
   the spacing solver; the test exists so nobody "fixes" the flag into them.

6. Run `./scripts/compile.sh` then `./scripts/test.sh` and report green.

---

## ✅ Phase 5: Grouping and Baseline Tests

**Status:** Complete  <br>
**BlockedBy:** 3  <br>
**Files:** src/test/java/songscribe/layout/stacking/DynamicGrouperTest.java, src/test/java/songscribe/layout/StructuralTierStackingTest.java  <br>
**Recommended model/effort:** Sonnet 4.6, medium effort — the baseline assertion needs care; see task 3

### Tasks

1. New `src/test/java/songscribe/layout/stacking/DynamicGrouperTest.java`
   covering the grouping rule only (no Y arithmetic): a lone text dynamic is its
   own group; a dynamic on the element immediately before a hairpin's anchor
   joins that hairpin's group; so does one immediately after its end; a dynamic
   two elements outside either bound does not; two hairpins sharing an element
   merge into one group; two disjoint hairpins stay separate.

2. In the same file, assert groups come back in ascending X order of their
   leftmost member.

3. Extend `src/test/java/songscribe/layout/StructuralTierStackingTest.java` with
   a baseline-alignment case: a note bearing a text dynamic on the element
   immediately before a hairpin's anchor, asserting the two decoration layouts
   resolve to the **same reference line** — not the same bounding-box centre.
   Derive each side the way phase 3 does and compare those, within the file's
   existing double epsilon:

   ```
   hairpin   refY = layout.ySs() + layout.heightSs() / 2
   dynamic   refY = layout.ySs() + layout.heightSs()
                        - dynamic.getContentBottomSs()
                        - StructuralStacker.DYNAMIC_TEXT_BASELINE_OFFSET_SS
   ```

   An assertion comparing `ySs() + heightSs() / 2` on both sides would reject a
   correct implementation, because the two members deliberately do not share a
   bounding-box centre.

4. Add a case asserting a text dynamic adjacent to no hairpin is placed by the
   unchanged singleton path, and one for a singleton dynamic sitting near — but
   not adjacent to — a hairpin, pinning whatever the new grouped X ordering
   produces (see phase 3 task 5).

5. Run `./scripts/compile.sh` then `./scripts/test.sh` and report green.

---

## ✅ Phase 6: Docs

**Status:** Complete  <br>
**BlockedBy:** 3  <br>
**Files:** docs/line-layout.md, docs/layout-geometry.md  <br>
**Recommended model/effort:** Haiku 4.5, low effort — prose edits whose replacement content is fully specified below

### Tasks

1. In `docs/line-layout.md`, rewrite "Example 8: Dynamics placement". The
   current text (around line 185–200) says hairpins are stacked before text
   dynamics so text dynamics end up outside hairpins, and that item 6,
   "Coordination with non-range dynamics", is "Not automatically implemented".
   Both statements are now false. Replace them with:

   - Hairpins and text dynamics are grouped and share one reference line. A text
     dynamic on the element immediately before a hairpin's anchor, or
     immediately after its end, joins that hairpin's group; a dynamic can never
     sit on or inside the span itself, because the editor strips point dynamics
     from `[anchor, end]` when a hairpin is added
     (`MusicEditOperations.stripPointDynamics`) and disables the dynamic action
     inside an existing one (`DynamicMarkingAction`). A text dynamic adjacent to
     no hairpin keeps its own independent placement. This follows LilyPond's
     `DynamicLineSpanner` (`lily/dynamic-align-engraver.cc`).
   - Within a group the two member types align differently on that line, as
     LilyPond does: a hairpin's full height is centred on it
     (`Hairpin.self-alignment-Y = CENTER`), while a text dynamic's glyph
     baseline sits `DYNAMIC_TEXT_BASELINE_OFFSET_SS` below it
     (`DynamicText.Y-offset`, "center on an 'm'") so its x-height centre lands
     on the line. Centring the glyph's bounding box instead would leave `p`,
     `f` and `mf` at visibly different heights.
   - A hairpin endpoint pulls back by `Hairpin.BOUND_PADDING_SS` from an
     adjacent text dynamic — LilyPond's `Hairpin.bound-padding` rule from
     `lily/hairpin.cc`. It is an assignment from the dynamic's extent, not a
     clamp, so a wedge preceded by a `p` legitimately extends past its anchor
     column's origin. Back-to-back hairpins meet at the shared column centre ∓
     `Hairpin.BACK_TO_BACK_PADDING_SS`, and a hairpin ending on a rest stops at
     the rest's left edge; note that the editor cannot create either yet, so the
     geometry is ahead of the UI. LilyPond's barline-bound rule is absent.
   - Default endpoints are unchanged and match LilyPond: `NoteColumn`'s
     `bound-alignment-interfaces` is `(rhythmic-head-interface stem-interface)`,
     so LilyPond anchors on notehead edges too, excluding accidentals and
     augmentation dots.
   - The `x1ShiftSs` / `x2ShiftSs` / `yShiftSs` manual offsets still exist and
     are still applied post-layout by
     `VerticalStackingCalculator.applyDecorationOffsets` with no collision
     re-run; they are now a manual override on top of automatic coordination
     rather than the only mechanism.
   - Update the ASCII example so it no longer describes the shift as required.

2. Update the hairpin margin note in the same section: `HAIRPIN_MARGIN_SS` =
   1.0 ss still governs a group's clearance, and the mouth height is now
   `Hairpin.HAIRPIN_OPENING_HEIGHT_SS`, matching LilyPond's `Hairpin.height`
   doubled.

3. In `docs/layout-geometry.md`, update the strut recipe around lines 122–129.
   The ASCII `max(...)` block enumerates exactly four floors; add the fifth:

   ```
             , hairpin reservation   MINIMUM_LENGTH_SS − curr.noteheadWidth
                                     (only when prev has a hairpin ending at curr)
   ```

   Update the surrounding prose, which also says "the largest of four floors" —
   `HorizontalSpacingCalculator.buildSpring`'s Javadoc points at this file as the
   authority, so it must stay true.

---

## ⏸️ Phase 7: Manual UI Verification

**Status:** Pending  <br>
**BlockedBy:** 5, 6  <br>
**Files:** —  <br>
**Recommended model/effort:** — user-driven

### Tasks

Ask the user to run `./scripts/run.sh` (do **not** run it without permission)
and confirm, on a score with hairpins:

1. A crescendo and a diminuendo look right — mouth slightly taller than before,
   tips still closing solidly.
2. `p` on the note before a crescendo: the `p` and the wedge read as one
   horizontal line, and the wedge starts just clear of the `p`, extending left
   of its anchor notehead.
3. The same with `f` and with `mf` in place of `p` — all three should sit at the
   same apparent height, which is what the x-height alignment buys.
4. A hairpin *spanning* two adjacent notes — anchor on the first, end on the
   second, which is the smallest the editor allows (`MIN_HAIRPIN_NOTES` = 2) and
   55% of the corpus: the two notes are pushed apart enough for the wedge to
   reach `MINIMUM_LENGTH_SS`.
5. Existing manual `x1`/`x2`/`y` shifts on hairpins in an old file still apply on
   top of the new placement.

Back-to-back hairpins and a hairpin ending on a rest cannot be checked here: the
engine renders both, but the editor cannot yet create either, so they are
covered by unit tests instead and the UI work is tracked as issue #743. The
barline-bound item was dropped outright — the code that handled it is gone.
