# Articulation Placement by Stem Direction (issue #499)

Place accent and staccato **above the staff for down-stems** and **below the staff for up-stems**
(opposite the stem — matches LilyPond and standard engraving). Extend the vertical stacking
calculator to stack below the staff as well as above. Below the staff, only staccato and accent
are ever stacked (staccato closest to the note, accent beyond). Below-staff articulations must
push lyrics down.

## Status Dashboard

| Phase | Description | Status | Sub-plan |
|-------|-------------|--------|----------|
| 1 | [Below-staff stacking primitives](#-phase-1-below-staff-stacking-primitives) | ✅ Complete | — |
| 2 | [Direction-aware routing](#-phase-2-direction-aware-routing) | ✅ Complete | — |
| 3 | [Glyphs + rendering](#-phase-3-glyphs--rendering) | ✅ Done (already in working tree) | — |
| 4 | [Doc/comment corrections + dead-code removal](#-phase-4-doccomment-corrections--dead-code-removal) | ✅ Complete | — |
| 5 | [Stacking/layout tests](#-phase-5-stackinglayout-tests) | ✅ Complete | — |
| 6 | [Renderer + parity tests](#-phase-6-renderer--parity-tests) | ✅ Complete | — |

> **Note (plan review, 2026-06-30):** Phase 3 and most of Phase 4 were already implemented
> in the working tree before this review (uncommitted). This dashboard reflects actual state,
> not the original phase order. Phase 3 was correctly designed to be independent-for-compilation
> per its own text; the current working tree is mid-flight, not broken — for an up-stem note
> today, the renderer picks the *below* glyph/bbox but positions it at the *above*-staff Y
> (Phase 1/2 haven't landed), which is expected and resolved once Phase 1/2 land.

## Settled decisions

- **Direction:** `note.getDirection().isDown()` → above staff (existing path); `isUp()` → below staff (new path).
- **Margin:** add `ARTICULATION_MARGIN_SS = 0.20` (staff-spaces) used only by the articulation paths.
  Leave `NOTE_DECORATION_MARGIN_SS = 0.5` for fermata, trill, and text dynamics.
- **Below combo glyph:** use precomposed `articAccentStaccatoBelow` (U+E4B1), mirroring the above
  combo (`ARTIC_ACCENT_STACCATO_ABOVE`). Its bbox is already in `bravura_metadata.json` and the
  glyph is already registered in `SMuFLGlyph.java` (Phase 3, done).
- **Below-Y → lyric-extent threading (plan review decision):** `dispatchArticulationStacking`
  returns the element's bottom Y (meaningful when placing below; the above path's callers simply
  ignore it). `stackArticulations` forwards it to `context.updateBotContentExtentSs()` only when
  placing below. This keeps `StackingUtils`/dispatch free of a `StackingContext` dependency and
  avoids a null/no-op special case for the context-less preview path. (Architecture Issue 1 → 1A.)

## Coordinate model (Y-down, middle line = 0)

- Staff top line `y = -2.0` ss, bottom line `y = +2.0` ss (`STAFF_HALF_SS = 2.0`).
- `DecorationLayout.ySs()` is the **top** Y of the glyph box. Negative = above staff, positive = below.
- Above stacking reserves via `StaffExtents.ySet(true, …)` (keeps the min/highest); below stacking
  reserves via `ySet(false, …)` (keeps the max/lowest).

```
                    Y-down axis (0 = middle staff line)

   more negative Y                                         more positive Y
   (higher on page)                                        (lower on page)
        ▲                                                        ▲
        │                                                        │
        │   accent   ─┐                                          │
        │              │  stacked outward as more                │
        │   staccato  ─┘  articulations are added                 │
        │        ▲                                                │
        │        │ marginSs (ARTICULATION_MARGIN_SS = 0.20)       │
        │  ┌─────────────────────┐                                │
   -2.0 │  │ anchorCeilingSs      │  STAFF_TOP_Y_SS                │
        │  ├─────────────────────┤  ── top staff line ──          │
        │  │                     │                                │
    0.0 │  │   staff (5 lines)   │  ── middle line ──              │
        │  │                     │                                │
   +2.0 │  ├─────────────────────┤  ── bottom staff line ──       │
        │  │ anchorFloorSs        │  STAFF_BOT_Y_SS                │
        │  └─────────────────────┘                                │
        │        │ marginSs (ARTICULATION_MARGIN_SS = 0.20)       │
        │        ▼                                                │
        │   staccato  ─┐                                          │
        │              │  stacked outward as more                │
        │   accent    ─┘  articulations are added                 │
        │                                                          ▼

   Notes AT or ABOVE the top staff line anchor at the notehead (not the staff
   line) — anchorCeilingSs(sp) = noteHeadYSs − NOTE_HEAD_RADIUS_SS when
   sp <= TOP_STAFF_LINE_POSITION. anchorFloorSs mirrors this for notes AT or
   BELOW the bottom staff line: anchorFloorSs(sp) = noteHeadYSs + NOTE_HEAD_RADIUS_SS
   when sp >= BOTTOM_STAFF_LINE_POSITION.

   stackAbove:  ceilingSs = min(currentTopSs, anchorCeilingSs) → elementY = ceiling − margin − height
   stackBelow:  floorSs   = max(currentBotSs, anchorFloorSs)   → elementY = floor + margin
                                                                  (returns elementY + height, the bottom)
```

## Key touchpoints

- `layout/stacking/StackingUtils.java` — `anchorCeilingSs()` (65-79), `stackAbove()` (90-115). Mirror these.
- `layout/stacking/NoteAttachedStacker.java` — `stackArticulations` (398-431), `dispatchArticulationStacking`
  (438-460), `stackSingleArticulation` (465-474), `computePreviewDecorationLayouts` (124-163), margin
  constants (58, 64). Reduced tie margin (`TIE_DECORATION_MARGIN_SS`, 425-427) applies to the above path only.
- `layout/StaffExtents.java` — `ySet/yGet(above, …)` (119-170); `STAFF_HALF_SS` (52), `STAFF_POSITION_OFFSET_SS` (57).
  Already fully generic over above/below — **no changes needed here.**
- `layout/stacking/StackingContext.java` — `updateBotContentExtentSs` (109-112) / `getBotContentExtentSs` (101);
  drives lyric baseline via `VerticalStackingCalculator.java:178-180` → `LayoutResult.setBelowContentSs` →
  `SongLayoutMetrics.lyricsBaselineYSs` (`SongLayoutMetrics.java:70`). Line-height growth below the staff is
  automatic through the `bot` extents (`VerticalStackingCalculator.java:164-173`). Already used by
  `NoteAttachedStacker.seedTieArcIntoExtents` with the exact above/below-threading pattern this plan mirrors —
  **follow that precedent**, don't reinvent it.
- `ui/renderer/ArticulationRenderer.java` — `render` (88-154), above/below-glyph bbox constants (50-84).
  **Already implemented** — direction-aware glyph + bbox selection for combo/staccato/accent branches is done.
- `ui/renderer/RenderingUtils.java` — `layoutYToComponentYSs` (178), `glyphOriginYFromLayoutTop` (282),
  `centeredGlyphX`.
- `smufl/SMuFLGlyph.java` — articulation block (93-101); `ARTIC_ACCENT_BELOW` (E4A1), `ARTIC_STACCATO_BELOW`
  (E4A3), and `ARTIC_ACCENT_STACCATO_BELOW` (E4B1) all **already present**.
- `smufl/SMuFLMetadata.java` — add shared accessors for the combo-glyph width (see Phase 2a below); follows
  the existing `noteHeadWidthSs()`/`noteHeadHeightSs()` convention (lines 62-68).
- `dom/Articulation.java` (28-36) — class Javadoc **already corrected**. `getContentWidthSs`/
  `getContentHeightSs` (130-143) remain direction-unaware; safe today (verified via
  `bravura_metadata.json` — single staccato/accent above/below bboxes are numerically identical) and
  guarded by a new parity test (Phase 5). See `TODOS.md` for the deferred direction-aware follow-up.
- `dom/ArticulationType.java` (23-31) — class Javadoc **already corrected**. `getDrawingOrder(boolean)`
  (76-92) still needs removal (Phase 4 task remaining) — confirmed via
  `jet_brains_find_referencing_symbols` to have zero references outside its own test file.

---

## ✅ Phase 1: Below-staff stacking primitives

**Status:** Complete  <br>
**BlockedBy:** —  <br>
**Recommended model/effort:** Opus 4.8, high — new Y-down geometry mirroring existing above-staff logic; sign/direction errors are easy and load-bearing.

Add the below-staff placement primitives to `StackingUtils`, mirroring the above-staff pair. No callers change yet.

### Tasks
1. Add `BOTTOM_STAFF_LINE_POSITION = 4` and `STAFF_BOT_Y_SS = BOTTOM_STAFF_LINE_POSITION * StaffExtents.STAFF_POSITION_OFFSET_SS` (= +2.0), mirroring `TOP_STAFF_LINE_POSITION` / `STAFF_TOP_Y_SS` (47-51).
2. Add `anchorFloorSs(int staffPosition)` + `anchorFloorSs(StaffElement)` mirroring `anchorCeilingSs` (65-79): if `staffPosition < BOTTOM_STAFF_LINE_POSITION` return `STAFF_BOT_Y_SS`; else return `noteHeadYSs + NOTE_HEAD_RADIUS_SS` (below the notehead).
3. Add `stackBelow(StaffExtents, LineElement, xSs, widthSs, heightSs, marginSs, staffPosition, builder)` mirroring `stackAbove` (90-115): `currentBotSs = extents.yGet(false, queryXSs, queryWidthSs)`; `floorSs = Math.max(currentBotSs, anchorFloorSs(staffPosition))`; `elementTopYSs = floorSs + marginSs`; reserve the element **bottom** via `extents.ySet(false, xSs, widthSs, elementTopYSs + heightSs)`; write `DecorationLayout(xSs, elementTopYSs, widthSs, heightSs, marginSs)`. **Return the element bottom Y** (`elementTopYSs + heightSs`) so callers can feed it to the below-staff content extent.
4. Add a direct unit test for `anchorFloorSs` in `StackingUtilsTest.java`, mirroring the existing `anchorCeilingSs` test — assert both branches (within-staff vs. beyond-bottom-line) in isolation, independent of any full-column integration test. (Plan review Architecture Issue 3 → 3A: `anchorCeilingSs` already has this coverage; `anchorFloorSs` is the single highest-risk new function in this plan and deserves the same.)
5. Run `./scripts/compile.sh` — must report SUCCESS.

---

## ✅ Phase 2: Direction-aware routing

**Status:** Complete  <br>
**BlockedBy:** 1  <br>
**Recommended model/effort:** Opus 4.8, high — branching on stem direction across two code paths plus the lyric-extent side effect and tie-margin interaction.

Route articulation stacking to above vs below by stem direction in both the full-layout and preview paths, and push lyrics down for below-staff placement.

### Tasks
1. Add `public static final double ARTICULATION_MARGIN_SS = 0.20;` to `NoteAttachedStacker` (near line 58). Use it as the base articulation margin in place of `NOTE_DECORATION_MARGIN_SS` for articulations only. The tie-reduction branch (425-427) applies **only when placing above** (down-stems); for below placement use `ARTICULATION_MARGIN_SS` directly (upward ties never arc into below-staff articulations). Do not change fermata/trill margins.
2. **(New — plan review Code Quality Issue 2 → 2B)** Add two shared accessors to `SMuFLMetadata.java`, following the existing `noteHeadWidthSs()`/`noteHeadHeightSs()` convention: `accentStaccatoAboveWidthSs()` and `accentStaccatoBelowWidthSs()` (each `requireBBox(...).width()` for the respective combo glyph). Update `NoteAttachedStacker`'s `ACCENT_STACCATO_WIDTH_SS` and `ArticulationRenderer`'s `ACCENT_STACCATO_WIDTH_SS`/`ACCENT_STACCATO_BELOW_WIDTH_SS` to call these instead of independently deriving the same value from `requireBBox(...).width()`. (Precise scope: only the combo-glyph *width* is duplicated across the two classes today — height and left-bearing values are each used by only one class and are not duplicated; do not over-consolidate beyond this.)
3. Thread `StaffElement.Direction` (or a `boolean above`) into `dispatchArticulationStacking` and `stackSingleArticulation`. When above → call `stackAbove` (unchanged); when below → call `stackBelow`. Combo case: above → `ARTIC_ACCENT_STACCATO_ABOVE` dims; below → `articAccentStaccatoBelow` dims (already registered — no fallback/temporary branch needed, Phase 3 is done). Keep keying the combo layout on the staccato articulation (accent gets no layout entry, matching the above path). Make `dispatchArticulationStacking` return the element's bottom Y (see "Settled decisions" above).
4. In `stackArticulations` (398-431), compute `above = note.getDirection().isDown()` and pass it down. When below, take the returned bottom Y from `dispatchArticulationStacking` and call `context.updateBotContentExtentSs(bottomYSs)` so lyrics clear the articulations.
5. Apply the same direction routing in `computePreviewDecorationLayouts` (124-163) so the insertion-note preview matches the rendered layout. (No `StackingContext` here — the preview does not need the lyric-extent update; discard the returned bottom Y.)
6. Run `./scripts/compile.sh` — must report SUCCESS.

---

## ✅ Phase 3: Glyphs + rendering (already done)

**Status:** Done — confirmed via `git diff` during plan review (2026-06-30).

Registered the below combo glyph and made the renderer pick above vs below glyph sets by stem direction:
1. `ARTIC_ACCENT_STACCATO_BELOW("articAccentStaccatoBelow", '')` added to `SMuFLGlyph.java` after `ARTIC_ACCENT_STACCATO_ABOVE`.
2. `ArticulationRenderer` has below-glyph bbox constants (`ACCENT_BELOW_*`, `STACCATO_BELOW_*`, `ACCENT_STACCATO_BELOW_*`) mirroring the above constants, from `SMuFLMetadata.requireBBox`.
3. `render()` computes `above = element.getDirection().isDown()` once and selects the above- or below- glyph (and its bbox constants) in each of the combo / staccato-only / accent-only branches.

**Remaining follow-up from this phase:** apply the Phase 2 task 2 `SMuFLMetadata` accessor consolidation to the combo-width constants here too (small edit, not a re-implementation).

---

## ✅ Phase 4: Doc/comment corrections + dead-code removal

**Status:** Complete  <br>
**BlockedBy:** —  <br>
**Recommended model/effort:** Sonnet 4.6, low — one safe delete of unused code; doc fixes already landed.

Fix the backwards doc comments and remove the unused, contradictory ordering helper.

### Tasks
1. ~~Correct the class Javadoc in `Articulation.java` (33-36)~~ — **already done.**
2. ~~Correct the enum Javadoc in `ArticulationType.java` (26-32) and the `stackArticulations` Javadoc in `NoteAttachedStacker.java` (390-397)~~ — **already done.**
3. Remove `ArticulationType.getDrawingOrder(boolean)` (78-94) — **still pending.** Confirmed via `jet_brains_find_referencing_symbols`: zero references in `src/main`, referenced only by its own test file (`ArticulationTypeTest.java`, 4 test methods). Safe to delete.
4. Run `./scripts/compile.sh` — must report SUCCESS. (Compilation of `getDrawingOrder`'s tests will fail until Phase 5 removes them; note it and let Phase 5 gate.)

---

## ✅ Phase 5: Stacking/layout tests

**Status:** Complete  <br>
**BlockedBy:** 1, 2, 4  <br>
**Recommended model/effort:** Opus 4.8, medium — expected below-staff Y coordinates must be derived from the Y-down geometry; wiring is mechanical but the assertions are not.

Update and extend the layout-level tests to cover below-staff placement, stacking order, collision, lyric push-down, and preview parity, per the plan review's test-coverage gap analysis. Also removes the tests left dangling by Phase 4's `getDrawingOrder` deletion, since that deletion is a prerequisite for this phase to compile.

### Tasks
1. In `ArticulationStackingTest`, keep `StemDownArticulationsAbove` (down-stem → `ySs() < 0`) and rename/rewrite `StemUpArticulationsAbove` → `StemUpArticulationsBelow` asserting up-stem articulations get `ySs() > 0` (below staff), with correct expected top-Y from `anchorFloorSs + ARTICULATION_MARGIN_SS`. Include all three sub-cases to mirror the above-side class exactly: staccato alone, **accent alone** (gap identified in review — the above-side class has this, the naive below rewrite would not), and the combo.
2. Add a below-staff **stacking-order** test: up-stem note with both staccato + accent produces a single combo layout keyed on staccato (accent has no layout entry), placed below the staff.
3. **(New — plan review Test Gap E)** Add a below-staff **collision/reservation** test mirroring `testAboveStaffArticulationsReserveSpaceInExtents`: two up-stem notes at the same X position; the second staccato's `ySs()` must be *greater* (further below) than the first's, proving `stackBelow`'s `Math.max`/`ySet(false, …)` reservation logic works under real collision — this is the plan's own highest-risk new code path and was otherwise only covered indirectly.
4. Add a **lyric push-down** test: an up-stem note with articulations increases the line's below-content extent (`getBelowContentSs()` / `SongLayoutMetrics.lyricsBaselineYSs`) versus the same note without articulations.
5. **(New — plan review Test Gap C)** Add a below-direction **preview-path parity** test: `computePreviewDecorationLayouts` for an up-stem note produces `ySs() > 0` and matches the full-layout path's dimensions — proves the two independently-branching call sites (Phase 2 tasks 4 and 5) stayed in sync.
6. Update `NoteAttachedStackerTest` (410): fermata/trill assertions still use `NOTE_DECORATION_MARGIN_SS` (0.5) — confirm unchanged. (Note: line 381 is a comment, not a live assertion; no test currently asserts articulation Y against a literal margin constant outside the tie-reduced-margin path, so no update is needed there beyond what task 1 covers via the rewritten below-staff tests.)
7. Remove the `getDrawingOrder` tests (`ArticulationTypeTest`'s `GetDrawingOrderStemDown` and `GetDrawingOrderStemUp` nested classes) left dangling by Phase 4. Keep `GetMidiDurationPercent` and `HasMidiDurationOverride`. Needed for a clean compile — Phase 4 already removed the method these tests call.
8. Run `./scripts/compile.sh` — must report SUCCESS. (Phase 6 runs the full `test.sh unit` gate once its own tests are in place.)

---

## ✅ Phase 6: Renderer + parity tests

**Status:** Complete  <br>
**BlockedBy:** 5  <br>
**Recommended model/effort:** Sonnet 4.6, low — mechanical render-level assertions and one bbox parity check; no new geometry to derive.

Add the render-level glyph-selection tests and the bbox-parity guard test, then run the full test suite as the plan's final gate.

### Tasks
1. **(plan review Test Gap D)** In `ArticulationRendererTest.java`, add 3 below-direction tests mirroring the existing 3 (solo staccato, solo accent, combo) asserting `ARTIC_STACCATO_BELOW`/`ARTIC_ACCENT_BELOW`/`ARTIC_ACCENT_STACCATO_BELOW` glyphs are drawn for an up-stem note. (Layout-level Y-sign tests don't catch an inverted `above ? X : Y` glyph-selection branch — only a render-level test that inspects the drawn glyph string does.)
2. **(plan review Code Quality Issue 1 → 1A)** Add a parity unit test (e.g. in `ArticulationTest` or `SMuFLMetadataTest`) asserting `ARTIC_STACCATO_ABOVE`/`ARTIC_STACCATO_BELOW` and `ARTIC_ACCENT_ABOVE`/`ARTIC_ACCENT_BELOW` bboxes have identical width/height — documents and guards the assumption `Articulation.getContentWidthSs`/`getContentHeightSs` silently rely on (see `TODOS.md` for the deferred direction-aware follow-up if this ever fails).
3. Run `./scripts/compile.sh`, then `./scripts/test.sh unit` — must be green.

## Verification (whole plan)

- Down-stem notes: accent/staccato render above the staff exactly as before (visual parity except the new 0.20 gap).
- Up-stem notes: accent/staccato render below the staff; combo uses `articAccentStaccatoBelow`; staccato closest to the note.
- Lyrics shift down to clear below-staff articulations.
- Insertion-note preview matches final rendering for both directions.
- `./scripts/compile.sh` SUCCESS and `./scripts/test.sh unit` green.

---

## Post-implementation corrections (2026-07-01)

Live app testing after all phases landed surfaced three corrections to the plan above. The
precomposed combo glyph (`articAccentStaccato{Above,Below}`) described throughout this plan
**is no longer used anywhere** — superseded by the third correction below. Sections above
describing combo-glyph selection, `dispatchArticulationStacking`'s combo branch, and the
`ACCENT_STACCATO_*`/`accentStaccato{Above,Below}WidthSs()` constants/accessors are historical;
that code has been removed from the working tree.

1. **Staccato distance from the note is center-based, not edge-based.** `StackingUtils.stackStaccatoAbove`/`stackStaccatoBelow` originally fed the note-relative distance into the same edge+margin math used by every other decoration (`stackAboveAtAnchor`/`stackBelowAtAnchor`), which silently added an extra margin and positioned the dot's *edge* at the distance instead of its *center*. Fixed via new `stackAboveAtCenter`/`stackBelowAtCenter` helpers: within the staff, the dot's center sits exactly at `staccatoAnchorCeilingSs`/`staccatoAnchorFloorSs` (no margin added to the ideal position; margin only applies when colliding with already-reserved content, e.g. a stem tip). At or beyond the top/bottom staff line, staccato still uses the original edge+margin path unchanged.
2. **`StaffExtents` had a pre-existing coordinate-system bug**, unrelated to this plan but blocking correct below-staff placement: the constructor defaulted the `bot[]` array to `STAFF_HEIGHT_SS` (4.0), a leftover from before the codebase adopted the Y-down, middle-line-relative convention (where the correct default is 0.0, mirroring `top[]`). Fixed in `StaffExtents`'s constructor.
3. **The precomposed combo glyph was replaced entirely with two separately-stacked simple glyphs.** Originally: combo used whenever both staccato and accent were present, gated by staff position (first `|staffPosition| >= 3`, then tightened to `>= 2` — see `NoteAttachedStacker.NARROW_ACCENT_STACCATO_GAP_THRESHOLD_POSITIONS`). Final design: no combo glyph at all — accent always stacks directly beyond staccato (via new `StackingUtils.stackBeyondAbove`/`stackBeyondBelow`, which ignore accent's own generic staff-line anchor and instead follow whatever staccato actually reserved, since staccato's note-relative position can sit closer to the staff than accent's generic anchor). The gap between them is `NoteAttachedStacker.ARTICULATION_MARGIN_SS` (0.20) within `NARROW_ACCENT_STACCATO_GAP_THRESHOLD_POSITIONS` (2) positions of the middle line, and the narrower `NARROW_ACCENT_STACCATO_GAP_SS` (0.125) at or beyond that threshold. `ArticulationRenderer` no longer has any combo-glyph branch — it always draws each present articulation's individual glyph.
