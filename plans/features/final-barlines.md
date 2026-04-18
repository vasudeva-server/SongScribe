# Final Barline Invariant and Rendering

**Issue:** vasudeva-server/SongScribe#290  <br>
**Spec:** [specs/290-final-barlines.md](../../specs/290-final-barlines.md)  <br>
**Branch:** `290-final-barlines`

---

## Overview

Enforce a structural invariant that every `Composition` always ends with a
`FINAL_DOUBLE_BARLINE` on the last element of the last line. The final barline
becomes a composition-owned, auto-maintained element — no longer a tool the
user places. `BarRenderer` draws it flush with the right edge of the line
width.

The plan lands in seven phases, ordered so each phase produces a tree that
still compiles and passes tests. Each phase has its own unit tests alongside
production code — no "tests at the end" phase.

---

## Status Dashboard

| Phase | Description | Status | Sub-plan |
|-------|-------------|--------|----------|
| 1 | [Data-model invariant foundation](#-phase-1-data-model-invariant-foundation) | ✅ Complete | — |
| 2 | [Auto-maintenance in addLine/removeLine](#-phase-2-auto-maintenance-in-addlineremoveline) | ✅ Complete | — |
| 3 | [New-composition seeding and load-time migration](#-phase-3-new-composition-seeding-and-load-time-migration) | ✅ Complete | — |
| 4 | [End-aligned layout](#-phase-4-end-aligned-layout) | ✅ Complete | — |
| 5 | [Selection and interaction suppression](#-phase-5-selection-and-interaction-suppression) | ✅ Complete | — |
| 6 | [Paste normalization](#-phase-6-paste-normalization) | ✅ Complete | — |
| 7 | [Remove redundant UI and strings](#-phase-7-remove-redundant-ui-and-strings) | ✅ Complete | — |

---

## ✅ Phase 1: Data-model invariant foundation

**Status:** Complete  <br>
**BlockedBy:** —  <br>
**Sonnet-suitable:** Yes — mechanical plumbing. Sibling counter mirrors an existing pattern; guard conditions are straightforward.

### Purpose

Establish the data-model scaffolding that every later phase depends on:
`autoMaintenanceDepth` counter on `Composition`, a shared
`createFinalBarlineElement()` helper, and symmetric `Line` guards on
`addElement` / `setElement` / `removeElement` / `removeRange` that reject
invariant-breaking operations (bypassed during suspension or
auto-maintenance).

At the end of this phase the invariant is not yet enforced on new or loaded
compositions — guards only reject misuse. Phase 3 seeds the invariant for
new/loaded compositions; Phase 2 wires auto-maintenance so the guards can be
safely bypassed.

### Tasks

1. `music/Composition.java` — add `private int autoMaintenanceDepth` sibling
   to `suspensionDepth`. Add `isInAutoMaintenance()` returning
   `autoMaintenanceDepth > 0`. Add a private
   `incrementAutoMaintenance(Runnable)` helper that increments around the
   body and decrements in `finally`, mirroring `withoutMutationTracking`.
2. `music/Composition.java` — add
   `public static StaffElement createFinalBarlineElement()` returning a
   fresh `FINAL_DOUBLE_BARLINE` `Note` (or whatever `StaffElement` subtype
   `DOUBLE_BARLINE` constructs today — copy that call shape).
3. `music/Line.java` — in `addElement(int, StaffElement)`,
   `setElement(int, StaffElement)`, `removeElement(int)`, and
   `removeRange(int, int)`, add a guard helper that throws
   `IllegalStateException` when the call would:
   - insert/replace a `FINAL_DOUBLE_BARLINE` at any position other than
     `elementCount()` on the last line, or
   - remove (singly or within a range) the `FINAL_DOUBLE_BARLINE` on the last
     line.
   The guard is bypassed when
   `composition.isMutationTrackingSuspended() ||
   composition.isInAutoMaintenance()`.
4. Decide predicate naming and placement (spec §2 / §D leaves this open —
   candidates: `Composition.isFinalBarline(StaffElement, Line)`,
   `Line.isInteractable(StaffElement)`, static on `StaffElement`). Add the
   predicate now so later phases (selection, layout) can call it. Pick the
   site that reads cleanest at the call sites.

### Tests

- `LineMutationTest` — `Line.addElement` throws when inserting
  `FINAL_DOUBLE_BARLINE` at any index other than `elementCount()` on the
  last line, and when inserting on a non-last line at any index.
- `LineMutationTest` — `Line.setElement` throws when replacing with
  `FINAL_DOUBLE_BARLINE` at any index other than `elementCount() - 1` on the
  last line.
- `LineMutationTest` (new) — `Line.removeElement` throws when the target is
  the final barline on the last line.
- `LineMutationTest` (new) — `Line.removeRange` throws when the range
  includes the final barline on the last line.
- `LineMutationTest` — all four guards are bypassed when
  `composition.isMutationTrackingSuspended()` is true.
- Selectability-predicate unit test (new class, or added to
  `CompositionTest`):
  - Final barline on last line → `false`.
  - Non-final barline on last line → `true`.
  - A `FINAL_DOUBLE_BARLINE` on a non-last line → `true` (programmatic edge
    case).
  - Note / rest / other element → `true`.

### Exit criteria

- All existing unit tests green.
- New guard and predicate tests green.
- No behavior change visible from the UI.

---

## ✅ Phase 2: Auto-maintenance in addLine/removeLine

**Status:** Complete  <br>
**BlockedBy:** —  <br>
**Sonnet-suitable:** Yes with care — the branch tables in §4 of the spec are explicit. Sonnet should paste the spec's ASCII diagrams as javadoc verbatim and write one `switch` per diagram. Review for branch-coverage correctness.

### Purpose

Make `Composition.addLine(int, Line)` and `Composition.removeLine(int)`
auto-maintain the invariant: when the "last line" changes, the final barline
is transferred / replaced / inserted as required, all inside the same
`withModification` bracket as the triggering `LineInsertion` /
`LineDeletion` so one user action = one `CompositionDidChangeNotification`.

### Tasks

1. `music/Composition.java` — rewrite `addLine(int, Line)`:
   - Open `withModification`.
   - Inside, call `incrementAutoMaintenance` around the body so guards from
     Phase 1 are bypassed.
   - `applyChange(LineInsertion, …)` for the line itself.
   - If `index` makes `line` the new last line:
     - If the previous last line's last element was `FINAL_DOUBLE_BARLINE`,
       emit `ElementDeletion` on that element.
     - Branch on the new last line's last element
       (`FINAL_DOUBLE_BARLINE` → no-op; other barline → `ElementReplacement`
       via `applyChange` or `Line.setElement`; non-barline / empty →
       `ElementInsertion` via `Line.addElement`).
   - Embed the spec's ASCII diagram as javadoc on the method.
2. `music/Composition.java` — rewrite `removeLine(int)`:
   - Open `withModification`, `incrementAutoMaintenance`.
   - `applyChange(LineDeletion, …)` for the line itself.
   - Preserve the existing empty-composition guard (`lines.size() > 1`).
   - If the removed line was the last line, branch on the new last line's
     last element (same table: FINAL → no-op, barline → replacement,
     non-bar / empty → insertion).
   - Embed the spec's ASCII diagram as javadoc on the method.
3. Confirm that the internally-emitted element mutations set the modified
   flag and participate in undo the normal way (they do, because they go
   through `applyChange`).

### Tests

New tests on `Composition` (or add to `CompositionTest`):

- **Append as new last (transfer path):** former last line had a final
  barline → `ElementDeletion` fires for it; new last line gets the final
  barline through the correct branch (`ElementReplacement` when its last
  element was a different barline; `ElementInsertion` when it was a note or
  empty).
- **Insert before current last:** no transfer runs; current last line
  unchanged.
- **Append empty line:** final barline is `ElementInsertion`ed.
- **Remove last line:** penultimate becomes new last and ends up with a
  final barline — exercises all three sub-branches (no-op /
  `ElementReplacement` / `ElementInsertion`).
- **Remove non-last line:** no maintenance runs.
- **Coalescing:** subscribing a test listener sees exactly **one**
  `CompositionDidChangeNotification` per call, carrying both the trigger
  mutation and any maintenance mutations.
- **Guard bypass assertion:** an `addLine` call that requires replacement
  completes without `IllegalStateException` — proves
  `isInAutoMaintenance` gates the Phase 1 guards. One-line assertion.
- **Modified flag:** user-driven `addLine` / `removeLine` dirties the
  document.
- **Pending #14 (single-step undo):** marked `@Disabled("pending #14")` or
  commented out with a TODO referencing the Composition.java:849 TODO.

### Exit criteria

- All tests above green.
- Existing `LineMutationTest` / `MusicEditOperationsMutationTest` green
  (notably
  `testSetElementEndWithFinalDoubleBarlineRetainsEnding` and the
  repeat-scan test at `MusicEditOperationsMutationTest:541-544`).

---

## ✅ Phase 3: New-composition seeding and load-time migration

**Status:** Complete  <br>
**BlockedBy:** —  <br>
**Sonnet-suitable:** Yes — each migration branch is an explicit rule. Sonnet will need to locate `DocumentReader`'s post-parse block (lines 558-633) and insert the migration step alongside the other `FormatMigrator.migrate*` calls.

### Purpose

Make the invariant true for (a) every newly-constructed `Composition` and
(b) every `Composition` loaded from disk, using the shared helper from
Phase 1.

### Tasks

1. `music/Composition.java` — in the constructor path that builds a
   pristine composition (or `setupNewComposition`, whichever runs for
   "File → New"), append
   `createFinalBarlineElement()` to the first line under
   `withoutMutationTracking`. Brand-new compositions have nothing to undo,
   so suspension is correct.
2. `file/FormatMigrator.java` — add `migrateFinalBarline(Composition)`
   implementing the decision tree from spec §6:
   - For each non-last line, strip every `FINAL_DOUBLE_BARLINE` at any
     position.
   - For the last line, strip any `FINAL_DOUBLE_BARLINE` that is not the
     last element.
   - Then on the last line's last element: FINAL → no-op;
     SINGLE_BARLINE / DOUBLE_BARLINE / REPEAT_RIGHT / REPEAT_LEFT_RIGHT →
     replace via `setElement`; non-barline → append via `addElement`; empty
     line → append via `addElement`.
3. `io/CompositionIO.java` (`DocumentReader` completion, lines 558-633) —
   wire `FormatMigrator.migrateFinalBarline` into the migration chain
   inside the existing `withoutMutationTracking` block so the load does
   not dirty the document.

### Tests

- **New-composition test** (`CompositionTest` or new) — default-constructed
  `Composition` has exactly one line with one element, which is
  `FINAL_DOUBLE_BARLINE`. `LayoutEngine.layout(line)` of that line
  completes without error (smoke check).
- **`FormatMigratorTest` (new or extended)** — hand-built `Composition`
  fixtures exercising every branch:
  - Misplaced final barline on a non-last line → removed; last line's final
    barline preserved.
  - Multiple misplaced final barlines → all removed.
  - Last line ends in `SINGLE_BARLINE` → replaced with
    `FINAL_DOUBLE_BARLINE`.
  - Last line ends in `DOUBLE_BARLINE` → replaced.
  - Last line ends in `REPEAT_RIGHT` → replaced.
  - Last line ends in `REPEAT_LEFT_RIGHT` → replaced.
  - Last line ends with a note → final barline appended.
  - Last line already ends in `FINAL_DOUBLE_BARLINE` → no-op.
  - Last line is empty → final barline appended.
- **Migration does not dirty the document** — modified flag is still false
  after load.

### Exit criteria

- New-composition and migration tests green.
- Manual smoke test: open an existing legacy `.ss` file from the project's
  sample set and confirm it loads without errors and draws correctly.

---

## ✅ Phase 4: End-aligned layout

**Status:** Complete  <br>
**BlockedBy:** —  <br>
**Sonnet-suitable:** Yes — one shared helper, one layout edit, one `HorizontalAdjustment` skip. All three sites are pinpointed in the spec.

### Purpose

Layout becomes the sole writer of the final barline's `xPosSs`; the x is
flush with the right edge of the line width. `BarRenderer` is unchanged.

### Tasks

1. Add the shared flush-right helper (name TBD; candidates:
   `BarlineGeometry.flushRightXSs(double lineWidthSs)` or a static on
   `ElementType`). The helper returns `lineWidthSs - (thin + sep + thick)`,
   matching the formula already in `computeBarlineBoundsSs` at
   `ElementType.java:643`.
2. `ui/layout/HorizontalSpacingCalculator.java` — after
   `calculatePositions` has positioned every column on the last line,
   locate the final barline and set its `xPosSs` via the helper. Gate this
   on "line is the last line of the composition" — non-last-line barlines
   that happen to be `FINAL_DOUBLE_BARLINE` (shouldn't exist post-Phase-3,
   but defensive) are not end-aligned.
3. `ui/layout/HorizontalAdjustment.java:192-196` — in the `snapToEnd`
   drag-snap loop, explicitly skip the final barline (use the Phase-1
   predicate or check element type + last-line). Add a short comment
   pointing at layout as the sole writer.
4. `ui/renderer/BarRenderer.java` — no change; confirm via compile + a
   quick visual test that the rendered position matches the right edge.

### Tests

New `HorizontalSpacingCalculatorTest` (or extend `LayoutEngineTest`):

- On the last line, after `LayoutEngine.layout(line)` the final barline's
  `xPosSs` equals `flushRightXSs(lineWidthSs)`.
- On a non-last line, barlines are **not** end-aligned — confirms the
  last-line gate.

Manual check: open a multi-line composition, resize the window, confirm
the final barline stays flush to the right margin without user
interaction.

### Exit criteria

- Layout test green.
- Manual smoke shows final barline rendering flush-right at multiple
  widths.

---

## ✅ Phase 5: Selection and interaction suppression

**Status:** Complete  <br>
**BlockedBy:** 3 (so the invariant actually holds at runtime)  <br>
**Sonnet-suitable:** Mostly — each skip site is a one-liner consulting the shared predicate, but there are several sites to touch and careful inspection of each is needed. Can be Sonnet with a test-first pass.

### Purpose

Make the final barline invisible to click, drag, select-all, hover, and
preview — all through the one predicate from Phase 1. Delete /
Backspace / Cut do not need explicit guards because selection is
impossible.

### Tasks

1. `ui/selection/SelectionCoordinator.java` — in the click path, the
   drag-rectangle path, and the hover path, consult the shared predicate
   and skip the final barline. Range selections clip at its left edge.
2. `ui/action/SelectAllAction.java` (or the range builder it delegates
   to) — exclude the final barline from the selected range via the
   predicate.
3. `ui/component/score/PreviewElementManager.java:515-523` — suppress
   preview rendering and click routing at the final-barline cell when any
   insert tool is active. Belt-and-braces for future internal callers.
4. `ui/component/score/ScoreInputHandler.java:251-254` — add a one-line
   comment at the arrow-nav TODO pointing at the predicate so whoever
   wires up caret navigation respects the invariant. No logic change.

### Tests

- Selection / click unit tests on `SelectionCoordinator` (if test harness
  allows) — clicking the final barline cell leaves selection and caret
  unchanged; hover returns empty; drag rectangle spanning the final
  barline excludes it.
- `SelectAllAction` test — the resulting range excludes the final
  barline.

Manual smoke: try to click, shift-click, drag-select, and hover the final
barline in the running app — nothing happens.

### Exit criteria

- Predicate is the single source of truth; no open-coded
  `ElementType.FINAL_DOUBLE_BARLINE` checks remain in selection code
  (grep verifies).
- Manual smoke passes.

---

## ✅ Phase 6: Paste normalization

**Status:** Complete  <br>
**BlockedBy:** 3  <br>
**Sonnet-suitable:** Yes — narrowly scoped, single file, single rule.

### Purpose

Pasted clipboard content containing `FINAL_DOUBLE_BARLINE` is normalized
to `DOUBLE_BARLINE` before insertion. The composition's own final barline
is untouched.

### Tasks

1. `ui/action/PasteAction.java` — pre-process incoming elements: any
   `FINAL_DOUBLE_BARLINE` becomes `DOUBLE_BARLINE`. Apply before the
   existing insertion path. No change to export, since MusicXML / ABC
   importers do not exist.

### Tests

- `PasteActionTest` (new or extended) — a paste source containing
  `FINAL_DOUBLE_BARLINE` inserts `DOUBLE_BARLINE`; the composition's own
  final barline on the last line is untouched.

### Exit criteria

- Test green.

---

## ✅ Phase 7: Remove redundant UI and strings

**Status:** Complete  <br>
**BlockedBy:** 5 (so the invariant is maintained by code, not user action, before removing the user path)  <br>
**Sonnet-suitable:** Yes — pure deletion with a grep-before-delete sweep. Straightforward.

### Purpose

Remove the toolbar button, menu item, keybinding, action factory, and
string keys for "insert final double barline".

### Tasks

1. `ui/action/Actions.java:202-203` — remove the final-double-barline
   entry from `BARLINE_ACTIONS`. `BarToolbar` and `BarlineMenu` iterate
   the array and pick up the change automatically.
2. `ui/action/ElementTypeAction.java:176` — delete
   `createFinalDoubleBarlineAction()`. Confirm zero remaining callers
   (grep for the method name).
3. **Strings residual-reference sweep:** grep `src/` and `resources/` for
   `Strings.ACTION_BARLINE_FINAL_DOUBLE` and
   `Strings.ACTION_BARLINE_FINAL_DOUBLE_TOOLTIP`. Both must have zero
   references after step 1.
4. `resources/songscribe/strings.properties` — remove
   `action.barline.final.double` and
   `action.barline.final.double.tooltip`. Run `./scripts/compile.sh` to
   regenerate `Strings.java`.
5. Confirm Shift+F is no longer bound (it was attached to the removed
   action).

### Tests

- Existing unit tests remain green (they construct
  `FINAL_DOUBLE_BARLINE` elements directly, not via the action factory —
  so nothing to update).
- Manual smoke: "Insert Final Double Barline" is absent from toolbar and
  Barline menu; Shift+F does nothing.

### Exit criteria

- Grep for `createFinalDoubleBarlineAction` returns zero hits.
- Grep for `ACTION_BARLINE_FINAL_DOUBLE` returns zero hits.
- All unit tests green.

---

## Implementation Checklist (from spec §11)

- [x] `autoMaintenanceDepth` + `isInAutoMaintenance()` sibling to
  `suspensionDepth` (Phase 1).
- [x] Symmetric guards on `removeElement` / `removeRange` (Phase 1).
- [x] Shared `flushRightXSs` helper; `HorizontalAdjustment` skips the
  final barline; layout owns its x (Phase 4).
- [x] ASCII diagrams embedded in `addLine` / `removeLine` javadoc
  (Phase 2).
- [x] Delete `createFinalDoubleBarlineAction()` (Phase 7).
- [x] Single shared selectability predicate used by every skip site
  (Phase 1 + Phase 5).
- [x] Single `createFinalBarlineElement()` helper for default + migration
  paths (Phase 1 + Phase 3).
- [x] Residual-reference sweep before removing `Strings` keys (Phase 7).
- [x] Layout end-aligned x unit test (Phase 4).
- [x] Selectability predicate unit test (Phase 1).
- [x] Removal guard unit tests (Phase 1).
- [x] Auto-maintenance test includes explicit "no guard exception"
  assertion (Phase 2).

---

## Deferred / Pending

- **Single-step undo** for coalesced trigger + auto-maintenance mutations
  depends on issue #14 (`Composition.java:849` TODO). Tests for
  single-step undo are `@Disabled("pending #14")` in Phase 2. Mutation
  coalescing into one `CompositionDidChangeNotification` is delivered by
  this plan.
- **Arrow-key caret navigation** is stubbed at
  `ScoreInputHandler.java:251-254`. A comment pointing at the predicate is
  added in Phase 5; wiring the actual navigation is out of scope.
- **MusicXML / ABC import normalization** is out of scope — no importer
  for either format exists today.

---

## Sonnet-vs-Opus summary

| Phase | Sonnet-suitable | Note |
|-------|-----------------|------|
| 1 | Yes | Mechanical; mirrors `suspensionDepth` pattern. |
| 2 | Yes, with review | Branch tables are explicit; ASCII diagrams come straight from the spec. Review for branch-coverage correctness. |
| 3 | Yes | Each migration branch is an explicit rule. |
| 4 | Yes | Three small, pinpointed edits. |
| 5 | Mostly | Several skip sites; test-first is recommended. |
| 6 | Yes | Narrow, single-file change. |
| 7 | Yes | Deletion + grep sweep. |

No phase requires Opus-level reasoning provided the spec is kept open
alongside the implementation.
