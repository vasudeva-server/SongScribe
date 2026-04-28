# Pixel/Unit Mixing — Implementation Plan

**Branch:** `293-update-annotations`
**Created:** 2026-04-21
**Research:** [pixel-unit-mixing-findings.md](./pixel-unit-mixing-findings.md) · [pixel-unit-mixing-investigation.md](./pixel-unit-mixing-investigation.md)
**Status:** In Progress

---

## Purpose

Eliminate pixel/staff-space unit mixing in the `LineElement` hierarchy and
adjacent layout/renderer code, then extend the codebase to be zoom-safe. The
findings document identifies one live bug path, three kinds of latent bugs
(span-formula width mixing in `RangeElement` subclasses, bare-pixel magic
numbers in `Attribution` / `KeySignature` / `Tuplet` / `Tie`, and the fixed
`DEFAULT_HEIGHT_SS` on `AnnotationAttachment`), one layout-correctness bug
(`BeatChangeAttachment` under-reports its descender region), a pervasive
naming hygiene debt in `containsPoint` / renderer draw methods / record
fields, and the infrastructure gap that prevents zoom from working at all
(mutable `ScaleContext` with no change notification, plus `static final`
pixel constants baked at class load).

The plan sequences these so each phase has the smallest possible blast radius
and leaves the codebase in a consistent state.

## Success Criteria

1. `LineElement.getContentBounds()` and `getMarginBounds()` return pure
   staff-space rectangles with no live unit-mixing consumers.
2. Every `LineElement` subclass width/height reflects its actual rendered
   geometry (no magic-number stubs that only "work" at scale = 8).
3. `containsPoint`, draw-method, and record coordinate parameters across
   `ui/layout/` and `ui/renderer/` carry explicit `Ss` or `Px` suffixes.
4. `BeatChangeAttachment` reserves vertical space for the "=" sign descender
   during stacking.
5. `parentLine` is set on every attachment that needs it, enforced at the
   structural entry points.
6. `ScaleContext` emits a change notification; every `static final` /
   constructor-cached pixel value recomputes or invalidates on that signal.
7. A scale-varying unit test harness exists and catches regressions of the
   bugs above.

## Global Conventions

- All exploration uses Serena semantic tools first (`jet_brains_get_symbols_overview`,
  `jet_brains_find_symbol`, `jet_brains_find_referencing_symbols`,
  `jet_brains_type_hierarchy`). Grep / Glob / Read are fallbacks for non-code
  files or when Serena returns no match. See `.agent/rules/serena.md`.
- Any Explore subagent spawned during this plan uses the **Sonnet** model and
  is briefed with the Serena-first instruction from `CLAUDE.md`.
- Renames go through `jet_brains_rename`; moves through `jet_brains_move`;
  deletions through `jet_brains_safe_delete`. Never rename / delete via
  `Edit`.
- Every phase ends with `./scripts/compile.sh` + `./scripts/test.sh unit`
  passing before the next phase starts.
- No magic numbers in new code. Extract named constants per
  `.agent/rules/development.md`.
- Null `parentLine` dereferences use `RuntimeError.exit("<message>")`, not
  silent fallback or `Objects.requireNonNull`.

---

## Status Dashboard

| Phase | Description | Status | Sonnet? | Sub-plan |
|-------|-------------|--------|---------|----------|
| 1 | [Naming hygiene](#-phase-1-naming-hygiene) | ✅ Done | ✅ | — |
| 2 | [Delete dead bounds surface](#-phase-2-delete-dead-bounds-surface) | ✅ Done | ✅ | — |
| 3 | [Fix getMarginBounds unit mixing](#-phase-3-fix-getmarginbounds-unit-mixing) | 🟡 Code complete (pending visual parity check) | ⚠️ | — |
| 4 | [Fix RangeElement span-formula widths](#-phase-4-fix-rangeelement-span-formula-widths) | 🟡 Code complete (pending visual parity check) | ✅ | — |
| 5 | [Fix BeatChange region height](#-phase-5-fix-beatchange-region-height) | 🟡 Code complete (pending visual parity check) | ✅ | — |
| 6 | [Propagate parentLine from Line.addElement](#-phase-6-propagate-parentline-from-lineaddelement) | ✅ Done | ✅ | — |
| 7 | [Font-drive AnnotationAttachment height](#-phase-7-font-drive-annotationattachment-height) | 🟡 Code complete (pending visual parity check) | ✅ | — |
| 8 | [Replace magic pixels in Attribution, KeySignature, Tuplet, Tie](#-phase-8-replace-magic-pixels-in-attribution-keysignature-tuplet-tie) | ✅ Done | ⚠️ | — |
| 9 | [Scale-varying test harness and coverage](#-phase-9-scale-varying-test-harness-and-coverage) | ⏳ Pending | ✅ | — |
| 10 | [Zoom-readiness infrastructure](#-phase-10-zoom-readiness-infrastructure) | ⏳ Pending | ❌ | — |

**Sonnet legend:** ✅ Sonnet handles cleanly · ⚠️ Sonnet can do the mechanical work but has design judgment calls — brief carefully or split · ❌ Use Opus (cross-cutting architectural work).

---

## ✅ Phase 1: Naming hygiene

**Status:** Done  <br>
**BlockedBy:** —  <br>
**Sonnet suitability:** ✅ Easy. Purely mechanical renames via `jet_brains_rename` from a pre-enumerated table. No design judgment required; the only risk is missing a reference, which the tool handles automatically. Ideal Sonnet task.

### Goal

Add `Ss` / `Px` unit suffixes to every layout/renderer coordinate parameter,
record field, and instance field that currently lacks one. This is the
smallest-risk phase and lands first so later phases operate on unambiguous
signatures.

### Scope

All sites catalogued in findings Q3:

**Method parameters** — rename in place via `jet_brains_rename`:

| File | Method | Param renames |
|---|---|---|
| `ui/layout/Bounds.java` | `containsPoint(x, y)`, `containsPoint(x, y, expansion)` | `x`→`xSs`, `y`→`ySs`, `expansion`→`expansionSs`; update javadoc |
| `ui/layout/LineElement.java` | same two `containsPoint` overloads | same |
| `ui/layout/NoteBounds.java` | `translate(dx, dy)`, `translateRect(dx, dy)` | `dx`→`dxSs`, `dy`→`dySs` |
| `ui/layout/ElementBounds.java` | `translate(dx, dy)`, `translateRect(dx, dy)` | same |
| `ui/layout/AttachmentLayout.java` | `containsPoint(x, y)` | `xSs`/`ySs` |
| `ui/layout/LyricsLayout.java` | `containsPoint(x, y)` | `xSs`/`ySs` |
| `ui/layout/SyllableLayout.java` | `containsPoint(x, y)` | `xSs`/`ySs` |
| `ui/layout/RangeLayout.java` | `containsPoint(x, y)` | `xSs`/`ySs` |
| `ui/layout/ElementColumn.java` | `setXSs(x)` | `x`→`xSs` |
| `ui/renderer/ElementRenderContext.java` | `setOverrideElementXSs(x)` | `x`→`xSs` |
| `ui/renderer/BarRenderer.java` | `drawRepeatDots(g2, x)` | `x`→`xSs` |
| `ui/renderer/BaseElementRenderer.java` | `drawLedgerLine(g2, x, y, width, …)` | `x`→`xSs`, `y`→`ySs`, `width`→`widthSs` |
| `ui/renderer/BaseElementRenderer.java` | `drawBravuraGlyph(g2, glyph, x, y)` (both overloads) | `x`→`xSs`, `y`→`ySs` |

**Records** — rename positional components:

| File | Record | Renames |
|---|---|---|
| `ui/layout/Margin.java` | `Margin(left, bottom, right)` | append `Ss`; remove "music units (MU)" javadoc, replace with staff-space |
| `ui/renderer/GlissandoRenderer.java` | `NoteContext(note, cx, cy, …)` | `cx`→`cxSs`, `cy`→`cySs` |
| `ui/renderer/GlissandoRenderer.java` | `Endpoints(startX, startY, endX, endY, angle)` | append `Ss` to the four coordinates; leave `angle` alone |

**Instance fields** — rename with `jet_brains_rename` so accessor call sites
update:

| File | Field | Rename |
|---|---|---|
| `ui/component/score/UnderLyricsComponent.java` | `contentX` | `contentXPx` (field is set by Swing paint pipeline in pixels) |
| `ui/component/score/BanglaLyricsComponent.java` | `contentX` | `contentXPx` |
| `ui/component/score/TranslationComponent.java` | `contentX` | `contentXPx` |
| `ui/dialog/ResolutionDialog.java` | `sheetWidth`, `sheetHeight`, `sheetHeightWithoutLyrics`, `sheetHeightWithoutTitle` | append `Px` |

**`Attribution.calculateRightAlignedX` (`ui/layout/Attribution.java:118`)** — this method
has zero callers (confirmed). Delete via `jet_brains_safe_delete` as part of
Phase 2 instead of renaming.

### Steps

1. ✅ Use `jet_brains_find_referencing_symbols` to sanity-check the reference
   counts on every renamed symbol before starting — rename only proceeds
   when all references are in code that will be rebuilt.
2. ✅ Run `jet_brains_rename` on each parameter / record component / field
   in the tables above, one entry at a time. Compile after every rename
   (`./scripts/compile.sh`).
3. ✅ Where a rename exposes a javadoc comment that still says "pixels" or
   "music units (MU)", fix the wording with `Edit` in the same change.
4. ✅ Remove any now-obsolete `// pixels` / `// px` trailing comments that
   duplicate the new name.
5. ✅ Run `./scripts/compile.sh` → `./scripts/test.sh unit`. All must pass.

### Verification

- `grep -rn 'double x, double y' src/main/java/songscribe/ui/{layout,renderer}/`
  returns no bare-coordinate signatures.
- `grep -rn 'music units (MU)' src/main/java` returns zero matches.
- Unit test suite green.

### Acceptance

All parameters, fields, and record components in `ui/layout/` and
`ui/renderer/` carry either `Ss` or `Px` suffixes. Javadoc agrees with the
suffix. No behavior changes.

---

## ✅ Phase 2: Delete dead bounds surface

**Status:** Done  <br>
**BlockedBy:** —  <br>
**Sonnet suitability:** ✅ Easy. Enumerated deletion list with pre-verified reference counts. `jet_brains_safe_delete` enforces correctness. The only judgment call — whether `containsPoint` layout-package variants have live callers — is explicitly called out in Step 3 as "leave alone if unsure", which Sonnet can follow.

### Goal

Remove all six dead methods flagged in Q2 / Q4 so Phase 3 operates on a
minimal API. Every deletion is verified against the live reference graph.

### Scope

| Method | File | Reference count |
|---|---|---|
| `LineElement.getBounds()` | `LineElement.java:295` | 0 |
| `LineElement.containsPoint(double, double)` | `LineElement.java:309–311` | 0 |
| `LineElement.containsPoint(double, double, double)` | `LineElement.java:320` | 1 (the dead overload above) |
| `ElementRenderer.getBounds` (interface method) | `ui/renderer/ElementRenderer.java` | 0 |
| `BaseElementRenderer.getBounds(T, ElementRenderContext)` | `BaseElementRenderer.java:266–270` | 0 |
| `Attribution.calculateRightAlignedX` | `ui/layout/Attribution.java:118` | 0 |

Also delete the now-orphan `LineElement.getContentBounds()` **IF** Phase 3
leaves it with no live consumers after the `getMarginBounds` fix. Decision
is made in Phase 3; Phase 2 deletes only the six symbols above.

### Steps

1. ✅ For each method, run `jet_brains_find_referencing_symbols` and confirm
   the count matches the table. If any count is non-zero, stop and update
   the findings document.
2. ✅ Delete via `jet_brains_safe_delete` in the order listed so
   `containsPoint[1]`'s self-reference does not block `containsPoint[0]`
   deletion — delete the 0-arg overload first, then the 1-arg.
3. ✅ Update `Bounds.java` / `LineElement.java` / `AttachmentLayout.java` /
   `LyricsLayout.java` / `SyllableLayout.java` / `RangeLayout.java` and their
   sibling classes if the interface / abstract declaration of `containsPoint`
   has to change. Note: if `containsPoint` is an interface method declared on
   `Bounds`, deleting the `LineElement` override changes the public API —
   verify whether those layout-package `containsPoint` methods have live
   callers separately (they may have live callers from interaction / hit-testing
   code not yet audited). If they do, leave them alone in this phase.
4. ✅ Remove any now-unreferenced imports.
5. ✅ `./scripts/compile.sh` → `./scripts/test.sh unit`.

### Verification

- `jet_brains_get_symbols_overview` on `LineElement.java`,
  `BaseElementRenderer.java`, and `ElementRenderer.java` shows the methods
  are gone.
- Compile + unit tests green.

### Acceptance

All six dead symbols deleted. Remaining references to `getContentBounds` /
`getMarginBounds` are the three cataloged in findings Q2 (one live:
`CollisionDetector.calculateNoteExtent`).

---

## 🟡 Phase 3: Fix getMarginBounds unit mixing

**Status:** Code complete — pending visual parity check  <br>
**BlockedBy:** —  <br>
**Sonnet suitability:** ⚠️ Moderate. Edits are small and enumerated, but correctness hinges on understanding the ss/px boundary at each call site — a Sonnet mistake here silently shifts layout. The manual visual-parity check is essential and Sonnet cannot perform it; plan for a human to do the `./scripts/crun.sh` verification step. Code changes themselves are within Sonnet's reach.

### Goal

Convert `LineElement.getMarginBounds()` and `getContentBounds()` to return
pure staff-space `Rectangle2D.Double`. Update the single live consumer
(`CollisionDetector.calculateNoteExtent`) and the two `LineComponent` sites
that wrap it with `scale.toPixels` / `scale.fromPixels` to pass and consume
staff-space directly.

### Scope

Exact edits identified in findings Q2 blast-radius summary:

1. **`LineElement.getMarginBounds()` and `getContentBounds()`** — rebuild
   the `Rectangle2D.Double` from `positionSs` + `getContentWidthSs()` /
   `getContentHeightSs()` + margin fields, all in staff-space. Rename the
   method's local vars to `*Ss` where relevant.
2. **`CollisionDetector.calculateNoteExtent`** (`CollisionDetector.java:55–107`,
   4 call sites: note, attachment, articulation, range) — rename the
   `staffMiddleY` parameter to `staffMiddleYSs`. All internal arithmetic is
   already `double`; no rounding logic changes. Returned `Rectangle2D.Double`
   is now also in staff-space.
3. **`LineComponent.calculateMiddleLineYSs`** (`LineComponent.java:431–437`):
   drop `scale.toPixels(defaultSpaceAbove + 2.0)` — pass the ss value
   directly. Drop `scale.fromPixels(...)` around the consumed `extent`.
   Remove the "pixel-space margin bounds" comment.
4. **`LineComponent.calculateLineHeightSs`** (`LineComponent.java:506–516`):
   same treatment.

### Steps

1. ✅ Edit `LineElement.getMarginBounds()` body to return pure-ss rectangle
   (converting via `ScaleContext.fromPixels(getContentWidth/HeightPx())`
   at the boundary). `getContentBounds()` was deleted in the same step
   since Serena confirmed zero live callers — subclass methods on
   `ElementBounds` / `Bounds` are unrelated types.
2. ✅ Edit `CollisionDetector.calculateNoteExtent`: parameter renamed to
   `staffMiddleYSs`; the only two external call sites are the two
   `LineComponent` methods (step 3), both updated.
3. ✅ Edit the two `LineComponent` sites: removed the `toPixels` argument
   wrap and the `fromPixels` result wraps, deleted the "renderers not yet
   converted" comments.
4. ✅ `getContentBounds()` deleted in step 1 (zero callers).
5. ✅ `./scripts/compile.sh` → `./scripts/test.sh unit`. 956 passed, 1 skipped.
6. ⏳ Run the app via `./scripts/crun.sh`. Load the reference composition
   in `src/test/resources/` most likely to exercise the stacking logic
   (`sample.mssw` or similar). Confirm staff spacing looks identical to
   before the change — visual parity is the acceptance criterion since the
   algorithm is intended to produce the same result in a consistent unit.

### Verification

- Unit tests green.
- Manual visual parity check against pre-fix on a reference composition.
- `grep -rn 'scale\.fromPixels' src/main/java/songscribe/ui/component/LineComponent.java`
  shows no `fromPixels` wraps around margin-bounds consumption.

### Acceptance

`getMarginBounds()` returns staff-space; `CollisionDetector` and
`LineComponent` consume staff-space; no behavior change in rendered output.

---

## 🟡 Phase 4: Fix RangeElement span-formula widths

**Status:** Code complete — pending visual parity check  <br>
**BlockedBy:** —  <br>
**Sonnet suitability:** ✅ Easy. The formerly ambiguous Hairpin-shift decision is resolved: the `Hairpin.x1Shift` / `x2Shift` / `yShift` pixel fields and their accessors are dead (verified during planning — zero writers anywhere in main/test/IO, sole reader path always resolves to zero). This phase deletes them outright, which removes the only unit-mixing in the span formula and collapses the remaining work to mechanical pure-ss rewrites plus verified dead-code removal via `jet_brains_safe_delete`. No design judgment required.

### Goal

Replace the mixed-unit span formula
`abs(endXSs − anchorXSs) + endElement.getContentWidthPx()` in `Tuplet`,
`Tie`, `Ending`, and `Hairpin` with a pure-ss computation. This is finding
Q1's four-class bug. Incidentally deletes the vestigial pixel shift
scaffold on `Hairpin` that was propping up the mixed-unit formula.

### Scope

The abstract contract has to accommodate span measurements that depend on
layout-time positions. The approach:

1. Add a new abstract `double getContentWidthSs()` to `LineElement` (if not
   already present) and have each `RangeElement` subclass implement it as
   `abs(endElement.getXSs() - anchor.getXSs()) + endElement.getContentWidthSs()`.
   All three inputs are now staff-space.
2. Keep `getContentWidthPx()` on `LineElement` but make its default implementation
   `ScaleContext.getInstance().toPixels(getContentWidthSs())`, so subclasses only
   need to override the ss method. This converges the hierarchy.
3. Delete the dead `Hairpin` pixel shift scaffold, which is the only reason
   `Hairpin.getContentWidthPx()` mixes units today. Verified dead during
   planning (see planning note below). Specifically:
   - `Hairpin.x1Shift`, `x2Shift`, `yShift` fields (all `int`, default 0, no
     writers anywhere — confirmed via `jet_brains_find_referencing_symbols`
     on each setter plus full-tree grep for literal field names and
     reflection-style access).
   - `Hairpin.getX1Shift` / `setX1Shift` / `getX2Shift` / `setX2Shift` /
     `getYShift` / `setYShift` accessors.
   - `VerticalStackingCalculator.convertHairpinShifts` helper and its
     `HairpinShifts` record (only caller is the branch being deleted).
   - The `else if (element instanceof Hairpin hairpin)` branch in
     `VerticalStackingCalculator.applyDecorationOffsets` (the branch only
     adds zero to `xOffsetSs` / `yOffsetSs` / `widthAdjustSs` because all
     inputs are always zero — deletion is behavior-preserving).
   - The `+ x1Shift + x2Shift` term in `Hairpin.getContentWidthPx()`.
4. The pixel-magic heights on `Tuplet` (12.0) and `Tie` (8.0) are **not**
   addressed here — they land in Phase 8. This phase fixes widths only.

**Planning note — Hairpin pixel shifts are vestigial.** The layout-layer
`Hairpin` has `x1Shift` / `x2Shift` / `yShift` int-px fields with public
accessors but no writers anywhere (not in main, not in tests, not in IO,
not via reflection). The active user-adjustable shift mechanism lives on
`DynamicsSpan` (`x1ShiftSs` / `x2ShiftSs` / `yShiftSs`, `double`, ss) and
is consumed in `VerticalStackingCalculator.applySpanOffsets`, populated by
`HorizontalAdjustment`, `VerticalAdjustment`, `FormatMigrator`, `LineIO`,
and the manual-offset tests. The `Hairpin` pixel fields appear to be a
parallel scaffold that was never wired up (or was migrated and the
original fields were left behind). Deleting them removes a pixel leak
ahead of Phase 10's zoom work without any behavior change.

### Steps

1. ✅ Removed the Hairpin shift scaffold: deleted the `else if (element instanceof Hairpin hairpin)` branch
   from `VerticalStackingCalculator.applyDecorationOffsets`, then deleted the `HairpinShifts` record and
   `convertHairpinShifts` method. Removed `Hairpin` and `ScaleContext` imports from that file.
2. ✅ Deleted the three `Hairpin` shift fields (`x1Shift`, `x2Shift`, `yShift`) and their six accessors
   from `Hairpin.java`.
3. ✅ Added a non-abstract `getContentWidthSs()` default to `LineElement` that bridges via
   `fromPixels(getContentWidthPx())`. Added concrete `getContentWidthSs()` (pure-ss span formula) and
   `getContentWidthPx()` (delegates to `toPixels(getContentWidthSs())`) to `RangeElement` — eliminating
   duplication across all four subclasses.
4. ✅ Removed the mixed-unit `getContentWidthPx()` overrides from all four range classes (`Hairpin`,
   `Tuplet`, `Tie`, `Ending`); each now inherits the pure-ss implementation from `RangeElement`.
5. ✅ `./scripts/compile.sh` → `./scripts/test.sh unit`. 956 passed, 1 skipped.
6. ⏳ Run the app via `./scripts/crun.sh`. Load a composition containing hairpins, ties, tuplets, and
   endings. Confirm visual parity — stacking positions look identical to before the change.

### Verification

- `grep -n 'getContentWidthPx' src/main/java/songscribe/ui/layout/{Tuplet,Tie,Ending,Hairpin}.java`
  shows only the inherited default (if any), not an override mixing units.
- `grep -rn 'x1Shift\|x2Shift\|yShift' src/main/java/songscribe/ui/layout/Hairpin.java`
  returns zero matches.
- `grep -rn 'convertHairpinShifts\|HairpinShifts' src/` returns zero matches.
- Unit tests green (including `ManualOffsetStackingTest`, which exercises
  the `DynamicsSpan` ss shift path that remains live).
- Visual parity on a test composition containing tuplets, ties, endings,
  and hairpins, including one where the user has dragged a hairpin endpoint
  (exercises `DynamicsSpan.x*ShiftSs`, not the deleted `Hairpin` fields).

### Acceptance

Four classes' widths are computed entirely in staff-space with a single
conversion at the end. The span-formula bug is gone. The dead `Hairpin`
pixel shift scaffold is deleted; the live `DynamicsSpan` ss shift path is
the sole mechanism for user-adjustable hairpin endpoints.

---

## ⏳ Phase 5: Fix BeatChange region height

**Status:** Code complete — pending visual parity check  <br>
**BlockedBy:** —  <br>
**Sonnet suitability:** ✅ Easy. The reference pattern (`TempoChangeAttachment.computeContentMetrics`) is explicitly cited, and the three sub-regions are fully specified with their offsets. Sonnet can mirror the pattern reliably. Unit test requirements are also concrete.

### Goal

Close the layout-correctness gap in findings Q8b: `BeatChangeAttachment` does
not reserve vertical space for the "=" sign's descender because it uses
`stackAbove` with a scalar height instead of `stackAboveWithRegions` with
per-sub-region metrics.

### Scope

1. **`BeatChangeAttachment`** — add `computeContentMetrics(FontMetrics attrFontMetrics)`
   following the shape of `TempoChangeAttachment.computeContentMetrics`
   (`TempoChangeAttachment.java:65–90`). Emit three regions:
   - left note glyph (`xOffsetSs=0`, `yOffsetSs=0`, `widthSs=leftNoteWidthSs`, `heightSs=QUARTER_NOTE_HEIGHT_SS`)
   - "=" text (`xOffsetSs=leftNoteWidthSs + EQUALS_GAP_SS`, `yOffsetSs=QUARTER_NOTE_HEIGHT_SS − equalsAscentSs`, dimensions from `attrFontMetrics`)
   - right note glyph (`xOffsetSs=leftNoteWidthSs + EQUALS_GAP_SS + equalsWidthSs + EQUALS_GAP_SS`, `yOffsetSs=0`, `widthSs=rightNoteWidthSs`, `heightSs=QUARTER_NOTE_HEIGHT_SS`)
2. **`SystemStacker.stackBeatChange`** (`SystemStacker.java:111–138`) — call
   `stackAboveWithRegions(line, attachment, regions)` instead of
   `stackAbove(line, attachment, getContentHeightSs())`.
3. **`MetronomeAttachment`** — if `computeContentMetrics` belongs on the base
   class (decide during implementation based on duplication), pull the
   common logic up.

### Steps

1. ✅ Read `TempoChangeAttachment.computeContentMetrics` via
   `jet_brains_find_symbol` with `include_body=true` as the reference
   pattern.
2. ✅ Added `BeatChangeAttachment.computeContentMetrics(FontMetrics)`. Moved
   the `ContentMetrics` record from `TempoChangeAttachment` to
   `MetronomeAttachment` (shared base) to avoid duplication. Made
   `computeContentWidthSs` delegate to `computeContentMetrics`.
3. ✅ Updated `SystemStacker.stackBeatChange` to call `stackAboveWithRegions`.
4. ✅ Created `BeatChangeAttachmentTest` with 7 tests verifying the three
   regions' positions and that the "=" descender extends below
   `QUARTER_NOTE_HEIGHT_SS` by exactly `equalsDescentSs`.
5. ✅ `./scripts/compile.sh` → `./scripts/test.sh unit`. 963 passed, 1 skipped.
6. ⏳ Visually verify on a composition with a beat change followed
   immediately by an attachment below: nothing should collide with the "="
   descender.

### Acceptance

Beat-change decoration reserves the full vertical span including the
descender. A unit test locks in the region geometry.

---

## ✅ Phase 6: Propagate parentLine from Line.addElement

**Status:** Done  <br>
**BlockedBy:** —  <br>
**Sonnet suitability:** ✅ Easy. Two small, localized edits (`Line.addElement`, `StaffElement.setLine`) plus three enumerated unit tests. The scope is narrow and the correctness criterion is covered by the new tests.

### Goal

Make `parentLine` reliably non-null on every `StaffElement` added to a line
and on every attachment attached to such an element. This is the
prerequisite for Phase 7.

### Scope

Gap identified in findings Q7b:

- `Line.addElement(StaffElement)` (`Line.java:240–241`) sets
  `element.setLine(this)` but not `element.setParentLine(this)`.
- `StaffElement.setLine` (`StaffElement.java:623–625`) does not cascade to
  existing attachments / articulations.

### Steps

1. ✅ Added `element.setParentLine(this)` in both `Line.addElement` overloads
   and also in `Line.setElement` (same structural gap) right after each
   `element.setLine(this)` call.
2. ✅ Changed `StaffElement.setLine` to iterate existing `attachments` and
   `articulations` and call `setParentLine(line)` on each — backfills
   pre-existing children when an element is added to a line.
3. ✅ Updated the `@Nullable` javadoc on `LineElement.parentLine` to document
   that code downstream of `Line.addElement` may treat it as non-null.
   Annotation retained for Phase 7.
4. ✅ All 963 existing unit tests pass with no adjustments needed.
5. ✅ Added `ParentLinePropagationTest` with 4 passing tests covering all
   three plan scenarios plus articulation cascade.

### Verification

- New unit test passes.
- Existing `ManualOffsetStackingTest` and `FormatMigratorTest` still pass.

### Acceptance

Every attachment on an element that has been added to a line has a non-null
`parentLine`. The structural gap between `setLine` and `setParentLine` is
closed.

---

## ⏳ Phase 7: Font-drive AnnotationAttachment height

**Status:** Code complete — pending visual parity check  <br>
**BlockedBy:** —  <br>
**Sonnet suitability:** ✅ Easy. Symmetrical to the existing `computeContentWidthSs(FontMetrics)` pattern, which is explicitly called out as the template. Test cases are enumerated. The only judgment call — that `parentLine` null should `RuntimeError.exit` — is already stated in the Global Conventions.

### Goal

Replace `AnnotationAttachment.DEFAULT_HEIGHT_SS = 1.75` with a real
font-metrics-driven computation, as identified in findings Q1 and Q7.

### Scope

1. Remove `DEFAULT_HEIGHT_SS` from `AnnotationAttachment`.
2. Introduce `computeContentHeightSs(FontMetrics annotationFontMetrics)`
   that returns `ScaleContext.getInstance().fromPixels(fm.getAscent() + fm.getDescent())`
   — symmetrical to the existing `computeContentWidthSs(FontMetrics)`.
3. Change `getContentHeightSs()` to a no-arg convenience that looks up
   `parentLine.getComposition().getAnnotationFontMetrics()` and delegates.
   The `parentLine`-null branch calls `RuntimeError.exit(...)`. Phase 6's
   propagation makes this safe in every live path.
4. Update `MetronomeRenderer` / `SystemStacker` / anywhere else that passes
   the annotation font metrics downstream to use the instance method when
   it has the metrics in hand, or the no-arg when it does not.
5. Add / strengthen tests in `AnnotationAttachmentTest`:
   - `testContentHeightSsComputedFromFontMetrics` — with a controlled
     `FontMetrics` mock whose ascent+descent is known, assert
     `computeContentHeightSs(mock)` returns the expected ss value.
   - `testGetContentHeightSsThrowsWhenParentLineNull` — exercises the
     `RuntimeError.exit` path.
   - `testGetContentHeightSsReadsCompositionFontMetrics` — integration with
     a real `Composition` and annotation font.

### Steps

1. ✅ Read `AnnotationAttachment.java` overview and the existing
   `computeContentWidthSs(FontMetrics)` body.
2. ✅ Edited `AnnotationAttachment` per Scope 1–3: removed `DEFAULT_HEIGHT_SS`,
   added `computeContentHeightSs(FontMetrics)`, updated `getContentHeightSs()` to
   look up `parentLine.getComposition().getAnnotationFontMetrics()` with a
   `RuntimeError.exit` guard on null parentLine.
3. ✅ Confirmed `DEFAULT_HEIGHT_SS` had exactly one reference (internal to `getContentHeightSs`).
4. ✅ Confirmed the `1.75` literal in `DynamicAttachment` is a separate `DEFAULT_HEIGHT_SS`
   constant for the unglyphed fallback — left untouched.
5. ✅ Updated `SystemStacker.stackAnnotations` to extract `fontMetrics` once and pass it
   to both `computeContentWidthSs` and `computeContentHeightSs`, avoiding the parentLine
   lookup for temporary legacy-bridge annotations.
6. ✅ Added three unit tests in `AnnotationAttachmentTest`:
   `testComputeContentHeightSsUsesAscentPlusDescent`,
   `testGetContentHeightSsReadsCompositionFontMetrics`,
   `testGetContentHeightSsThrowsWhenParentLineNull` (uses `mockStatic(RuntimeError.class)`
   to intercept `System.exit` without terminating the JVM).
7. ✅ `./scripts/compile.sh` → `./scripts/test.sh unit`. 970 passed, 1 skipped.
8. ⏳ Visually verify on a composition with annotations of mixed font sizes
   that vertical stacking no longer cuts off tall ascenders or over-reserves
   for short ones.

### Acceptance

Annotation text reserves exactly one (ascent + descent) worth of space in
staff-space. The fixed 1.75 constant is gone. Null `parentLine` fails
loudly via `RuntimeError.exit`.

---

## ✅ Phase 8: Replace magic pixels in Attribution, KeySignature, Tuplet, Tie

**Status:** Done  <br>
**BlockedBy:** —  <br>
**Sonnet suitability:** ⚠️ Moderate. Four classes with four different strategies (font metrics, SMuFL bbox summation, engraving constants). `Attribution` has a design choice (composition reference vs `parentLine`) in Step 1. SMuFL bbox lookup is a known pattern in the codebase but requires Sonnet to find the right helper. Visual verification across three key-signature flavors plus tuplets/ties is human-only. Feasible for Sonnet class-by-class; consider doing Attribution last since its design is least prescribed.

### Goal

Replace the bare pixel literals (`CHAR_WIDTH_PX=8.0`, `TEXT_HEIGHT_PX=16.0`,
`ACCIDENTAL_WIDTH_PX=8.0`, `ACCIDENTAL_HEIGHT_PX=24.0`, `Tuplet` height
`12.0`, `Tie` height `8.0`) with either SMuFL-sourced or font-metrics-sourced
values, as flagged in findings Q1.

### Scope

- **`Attribution`** — width from `FontMetrics.stringWidth(text)` via the
  composition's attribution font metrics, height from `FontMetrics.getAscent()
  + getDescent()`. Mirrors the Phase 7 pattern. Requires a `parentLine` or
  a direct `Composition` reference on `Attribution`; audit which is
  available. Replace both raw pixel constants.
- **`KeySignature`** — width from SMuFL `ACCIDENTAL_SHARP` / `ACCIDENTAL_FLAT`
  bboxes summed according to the signature's accidental count and kind.
  Height from the max bbox height of the active glyphs. Remove the raw
  pixel constants entirely. Kerning is out of scope for this phase; plain
  summation of bbox widths is a strict improvement over `count × 8.0`.
- **`Tuplet` height** — replace `12.0` with a named `LayoutStylesheet`
  constant sized from the tuplet bracket's actual engraving definition. If
  no authoritative source exists, name the constant
  `TUPLET_BRACKET_HEIGHT_SS` and give it the value `12.0 / 8.0 = 1.5`
  (documented as the engraving convention). This is definitional, not
  measured — same category as `STAFF_HEIGHT_SS`.
- **`Tie` height** — same treatment: `TIE_ARC_HEIGHT_SS = 1.0` (already
  matches `getContentHeightSs()`). The `Px` method becomes
  `scale.toPixels(TIE_ARC_HEIGHT_SS)`.

### Steps

1. ✅ For `Attribution`: confirmed zero live callsites. Used `parentLine`
   to match the `AnnotationAttachment` pattern; future callers must set
   `parentLine` before invoking `getContent*Ss()`.
2. ✅ Implemented `Attribution.computeContentWidthSs(FontMetrics)` and
   `computeContentHeightSs(FontMetrics)`; added `getContentWidthSs()` /
   `getContentHeightSs()` no-arg variants that resolve the composition's
   attribution font metrics through `parentLine`, with `RuntimeError.exit`
   on null. Removed `CHAR_WIDTH_PX` and `TEXT_HEIGHT_PX`.
3. ✅ For `KeySignature`: `getContentWidthSs` = `count * requireBBox(glyph).width()`,
   `getContentHeightSs` = `requireBBox(glyph).height()`. All four accidentals in
   a given signature are the same glyph, so the summation reduces to a multiplication.
   Removed `ACCIDENTAL_WIDTH_PX` and `ACCIDENTAL_HEIGHT_PX`.
4. ✅ For `Tuplet` / `Tie`: added `LayoutStylesheet.TUPLET_BRACKET_HEIGHT_SS = 1.5`
   and `TIE_ARC_HEIGHT_SS = 1.0`. `Tuplet.getContentHeightSs` now returns
   `TUPLET_BRACKET_HEIGHT_SS` (replacing the older 0.7 arm-only value with
   the documented engraving-convention 1.5 = 12px, matching what the bracket
   actually occupies when stacking). Promoted the duplicate
   `getContentHeightPx() = toPixels(getContentHeightSs())` implementation
   to `RangeElement` as the default; removed the overrides from
   `Tuplet`, `Tie`, `Hairpin`, and `Ending`.
5. ✅ Added unit tests: `AttributionTest` (6), `KeySignatureTest` (7),
   `TupletTest` (2), `TieTest` (2).
6. ✅ `./scripts/compile.sh` → `./scripts/test.sh unit`. 987 passed, 1 skipped.
7. ⏳ Visual parity: load a composition with attribution strings, all three
   key-signature flavours (sharps, flats, mixed), tuplets, and ties. Confirm
   stacking and spacing look correct. Expect small visible shifts in
   tuplet vertical stacking (1.5 ss reserved vs. 0.7 ss before) and minor
   key-signature width changes (sharp bbox ≈ 1.18 ss, flat bbox ≈ 0.94 ss,
   vs. uniform 1.0 ss before).

### Acceptance

No raw pixel magic numbers remain in `Attribution`, `KeySignature`,
`Tuplet`, or `Tie`. Every dimension is either SMuFL-derived, font-driven,
or a named engraving-convention ss constant.

---

## ⏳ Phase 9: Scale-varying test harness and coverage

**Status:** Pending  <br>
**BlockedBy:** —  <br>
**Sonnet suitability:** ✅ Easy, with a caveat. Writing a JUnit 5 extension that saves/restores a singleton is a well-trodden pattern Sonnet handles cleanly, and the parameterized tests are mechanical once the harness exists. Caveat: Sonnet must restore `ScaleContext` state deterministically — a subtle bug here would only surface as flaky tests. Include an explicit "always restore in @AfterEach via try/finally" instruction when briefing.

### Goal

Create the missing test infrastructure identified in findings Q6: a harness
that varies `pixelsPerStaffSpace` away from the default of 8 so regressions
in unit handling surface automatically.

### Scope

1. **`ScaleContextTestHarness`** (new class in `src/test/java/songscribe/test/support/`):
   - Exposes a JUnit 5 extension that takes a list of scale values and runs
     the annotated test once per scale.
   - Saves and restores the `ScaleContext` singleton state around each
     invocation.
2. **Parameterized conversion tests** — for every class in findings Q1 that
   was touched in Phases 3–8 (`Clef`, `Articulation`, `Staff`, `Trill`,
   `Tuplet`, `Tie`, `Ending`, `Hairpin`, `StaffElement`, `FermataAttachment`,
   `DynamicAttachment`, `MetronomeAttachment`, `TempoChangeAttachment`,
   `BeatChangeAttachment`, `AnnotationAttachment`, `Attribution`,
   `KeySignature`):
   - `testContentWidthScales` — at scales 4, 8, 12, 16, the `Px` value equals
     `scale × Ss` value within a small epsilon.
   - `testContentHeightScales` — same.
3. **Margin-bounds roundtrip test** — exercise a full
   `CollisionDetector.calculateNoteExtent` call at multiple scales; the
   final ss result should be scale-invariant.
4. **`AnnotationAttachment` coverage** filling the gap from findings Q6:
   `computeContentHeightSs`, `getContentHeightSs`, `computeContentWidthSs`
   each tested at multiple scales.

### Steps

1. ⏳ Read the existing test harness conventions from `.agent/testing-unit.md`
   and `.agent/testing-common.md` before writing.
2. ⏳ Write the `ScaleContextTestHarness` extension.
3. ⏳ Add the parameterized tests in the classes listed in Scope 2.
4. ⏳ Add the roundtrip test in `CollisionDetectorTest` (create if absent).
5. ⏳ Add the `AnnotationAttachment` coverage from Scope 4.
6. ⏳ `./scripts/test.sh unit` — confirm every new test passes and no
   existing test regresses.

### Acceptance

At least one unit test per touched class runs at non-default scale and
catches the concrete unit-mixing bugs fixed in Phases 3–8 if they were to
be reintroduced.

---

## ⏳ Phase 10: Zoom-readiness infrastructure

**Status:** Pending  <br>
**BlockedBy:** —  <br>
**Sonnet suitability:** ❌ Not recommended for Sonnet. This is a cross-cutting architectural change: designing a new notification, wiring it through `MessageCenter`, converting `static final` constants in ways that may require restructuring (some can't be made instance without rethinking ownership), and catching subtle invalidation bugs (`Ending.LABEL_*_BOUNDS_SS`, `FormatMigrator` pixel comparisons). Risk 3 in Cross-phase risks — coalescing vs modification brackets — is exactly the kind of invariant Sonnet tends to miss. Use Opus; Sonnet can safely handle Step 6 (the zoom-roundtrip integration test) once the design is settled.

### Goal

Make `ScaleContext` mutations visible to consumers and convert every
scale-derived pixel cache to recompute on scale change. This is findings Q5's
full scope and the final prerequisite for a working zoom feature.

### Scope

1. **`ScaleContext` change notification.** Add a `ScaleDidChangeNotification`
   (see `.agent/rules/messages.md` and `.agent/rules/mutations.md` for the
   notification conventions) posted from `setPixelsPerStaffSpace` whenever the
   value actually changes. Include the old and new scale in the payload.
2. **Fix `static final` pixel constants** identified in findings Q5:
   - `Score.STAFF_POSITION_OFFSET_PX` — convert to a computed accessor or a
     `@Handler`-maintained field.
   - `Annotation.ABOVE`, `Annotation.BELOW` — same; cascade fix once `Score`
     is fixed.
   - `TranslationComponent.TRANSLATION_TOP_MARGIN` — convert to an instance
     field recomputed on `ScaleDidChangeNotification`.
   - `BanglaLyricsComponent.BANGLA_LYRICS_TOP_MARGIN` — same.
3. **Fix constructor-time pixel caches**:
   - `MainPanel.scoreMarginTop`, `StaffPanel.lineMargin` — subscribe to
     `ScaleDidChangeNotification` and recompute.
4. **Fix `Ending.LABEL_1_BOUNDS_SS` / `LABEL_2_BOUNDS_SS`.** These are frozen
   at class-load from `GraphicUtils.LAYOUT_FRC`. Convert to a method that
   recomputes on demand from the current `FontRenderContext`, or cache with
   invalidation on scale change. Prefer per-render recomputation unless
   profiling shows a hot path.
5. **Line.java deprecated fields** (`beatChangeYPosPx`, `firstSecondEndingYPosPx`,
   `trillYPosPx`): add a javadoc note that these are legacy pixel caches
   not safe under zoom, and that `FormatMigrator`'s comparison against
   `ScaleContext.toRoundedPixels(...)` (findings Q5 caveat) will break on
   non-default scale. Fix or document. Low severity — leave for a follow-up
   unless trivial.

### Steps

1. ⏳ Design the notification class and publish via `MessageCenter.post`.
   Read the existing `PrefsDidChangeNotification` as a shape reference.
2. ⏳ Add `setPixelsPerStaffSpace` notification emission.
3. ⏳ Convert the four `static final` pixel constants to computed or
   handler-driven values. Check for other `static final` pixel constants
   not listed in Q5 via a targeted grep
   (`grep -rn 'static final.*Px = ' src/main/java/songscribe/` and
   `grep -rn 'ScaleContext.*toPixels' src/main/java/songscribe/` filtered
   to class-load contexts).
4. ⏳ Convert the two instance-field caches.
5. ⏳ Fix the `Ending` static `Rectangle2D` fields.
6. ⏳ Add a zoom-roundtrip integration test: load a composition, render to
   an off-screen image; change scale; render again; restore scale; render
   again; the first and third renders must be identical.
7. ⏳ `./scripts/test.sh unit` → `./scripts/compile.sh` → manual visual
   verification at multiple scales.

### Acceptance

`ScaleContext.setPixelsPerStaffSpace` at runtime produces a correctly
re-rendered score with no stale cached pixel values. The
`ScaleDidChangeNotification` is consumed by every site that previously held
a pre-computed pixel value. Zoom support can be added on top of this phase
without introducing new unit bugs.

---

## Cross-phase risks

1. **`containsPoint` in Phase 2 interacts with layout-package hit-testing.**
   The `containsPoint` methods on `AttachmentLayout`, `LyricsLayout`,
   `SyllableLayout`, `RangeLayout`, and `Bounds` may have live callers
   that the findings audit did not catch (findings Q3 catalogues them as
   naming hygiene, not as dead code). Phase 2 only deletes the
   `LineElement.containsPoint` overloads; Phase 1 renames the other
   families for clarity. Verify hit-testing still works end-to-end after
   Phase 1 before starting Phase 2.

2. **Phase 6's `Line.addElement` change runs through every test setup that
   populates a line.** Watch for tests that rely on `parentLine` being null
   at a specific moment; Phase 6 adds a unit test exactly for this but the
   existing suite may need light adjustment.

3. **Phase 10's `ScaleContext` notification must not fire inside the
   `Composition` modification bracket** — or if it does, the coalescing
   contract from `.agent/rules/mutations.md` must be verified. `ScaleContext`
   is not a `Composition` state, so the natural choice is to post the
   notification directly on `MessageCenter` without any mutation bracket.
   Confirm this during Phase 10 design.

4. **Visual-parity verification in Phases 3, 4, 5, 7, 8 is not covered by
   the unit test suite.** Run `./scripts/crun.sh` against a rich reference
   composition and eyeball the result before marking the phase complete.
   E2E tests require user approval per `.agent/rules/development.md` — do
   not run them without asking.

---

## Notes on sequencing

- Phases 1, 2, 4, 5, 8 are independent of each other and could run in
  parallel. They are listed sequentially because each touches overlapping
  files and a single-threaded sequence avoids merge conflicts.
- Phase 3 depends on Phases 1 and 2 for clarity (but not correctness);
  running Phase 3 first would still fix the bug but on a more cluttered
  API surface.
- Phase 7 depends on Phase 6 (hard dependency — marked in the dashboard).
- Phase 9 depends on Phases 3–8 in the sense that it writes tests covering
  their output; running it earlier would write tests against the buggy
  current behavior.
- Phase 10 is gated by Phases 1–8 because the infrastructure churn it
  introduces should land on top of a code base whose pixel handling is
  already consistent.
