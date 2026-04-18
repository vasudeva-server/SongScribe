# Final Barline Invariant and Rendering

Enforce a structural invariant that every `Composition` always ends with a
`FINAL_DOUBLE_BARLINE` on the last element of the last line. The final barline
becomes a composition-owned, auto-maintained element — it is no longer a tool
the user places, and it cannot be selected, edited, or deleted. `BarRenderer`
draws it flush with the right edge of the line width.

**Issue:** vasudeva-server/SongScribe#290

---

## Goals

1. **Invariant ownership** — `Composition` guarantees that the last element of
   the last line is always a `FINAL_DOUBLE_BARLINE`, from construction through
   every mutation.
2. **Automatic maintenance** — line add/remove and composition-load paths
   transfer or install the final barline transparently, coalesced into the same
   mutation bracket as the triggering operation (one undo step once undo
   grouping exists — see §11).
3. **Unselectable / unreachable** — the final barline is invisible to
   selection, caret, click, drag, and hover. It exists but the user cannot
   interact with it. Enforcement goes through a single shared predicate so
   every skip site reads the same rule.
4. **End-aligned rendering** — layout sets the element's stored x-position
   flush to the right edge of the line; `BarRenderer` reads that x unchanged.
5. **Legacy compatibility** — load-time migration normalizes compositions that
   were authored before this invariant existed.
6. **Remove redundant UI** — the toolbar button, menu item, keybinding, action
   factory, and string keys for "insert final double barline" are all removed.

---

## Non-Goals

- Redesign of any other barline type (`SINGLE_BARLINE`, `DOUBLE_BARLINE`,
  `REPEAT_LEFT`, `REPEAT_RIGHT`, `REPEAT_LEFT_RIGHT`).
- Changes to SMuFL glyph selection — `BarRenderer` continues to draw the final
  barline via `drawBar` primitives. The SMuFL migration for barlines is tracked
  separately under the SMuFL rewrite plan.
- Multi-movement compositions with internal final barlines. Only the final
  barline at end-of-composition is modeled.
- MusicXML / ABC import normalization — no importer for either format exists
  in the codebase today. Export paths are unaffected.
- Arrow-key caret navigation integration — `ScoreInputHandler.java:251-254`
  has arrow navigation stubbed out with a TODO. A one-line reminder pointing at
  the selectability predicate goes at that TODO so whoever wires up navigation
  respects the invariant.
- Undo grouping of the coalesced trigger + auto-maintenance mutations into a
  single undo step — depends on issue #14 (undo grouping), currently tracked
  via the TODO at `Composition.java:849`. The coalescing itself (one
  `CompositionDidChangeNotification` per user action) is delivered by this
  plan; single-step undo lands when #14 does.

---

## Current State

### Data model

- `ElementType.FINAL_DOUBLE_BARLINE` is declared in
  `src/main/java/songscribe/music/ElementType.java:68`, adjacent to
  `SINGLE_BARLINE` and `DOUBLE_BARLINE`.
- `ElementType.isBarLine()` includes it; `ElementType.snapToEnd()` returns
  `true` for it, `SINGLE_BARLINE`, `DOUBLE_BARLINE`, and `REPEAT_RIGHT`.
- Bounds formula in `computeBarlineBoundsSs()` (line 643): width =
  `thin + thick + sep`; height = full staff height.

### Mutation + modification infrastructure (reused)

- `Composition.withModification(Runnable)` (line 878) opens a bracket;
  `applyChange(Mutation, Runnable)` (line 924) emits one mutation.
- `Composition.withoutMutationTracking(Runnable)` (line 834) suspends posting
  and dirty-flagging via a `suspensionDepth` counter. This plan adds a sibling
  depth counter `autoMaintenanceDepth` (see §3).
- `Line.addElement(int, StaffElement)` (line 252), `Line.setElement(int,
  StaffElement)` (line 276), `Line.removeElement(int)`, `Line.removeRange(int,
  int)` are the element-level helpers the guards attach to. **Note:
  `Line.replaceElement` does not exist; the plan uses
  `ElementReplacement` via `applyChange` (or `Line.setElement`) for
  in-place replacement.**

### Rendering + layout

- `BarRenderer` (`src/main/java/songscribe/ui/renderer/BarRenderer.java:132`)
  draws the final barline as two filled rectangles at the element's resolved x.
- `HorizontalSpacingCalculator.calculatePositions` (line 88) sets per-column
  x during layout via `ElementColumn.setXSs()`.
- `LayoutEngine.layout(Line)` (line 131) is the per-line layout entry point.
- `HorizontalAdjustment.java:192-196` implements drag-snap for `snapToEnd`
  elements. This plan explicitly skips the final barline there — the layout
  stage is the sole writer of the final barline's x.

### Insertion / click / selection paths

- **Toolbar / menu / keybinding:**
  `ElementTypeAction.createFinalDoubleBarlineAction()`
  (`ui/action/ElementTypeAction.java:176`, shortcut Shift+F) is registered in
  `Actions.BARLINE_ACTIONS` (`ui/action/Actions.java:202-203`). Both
  `BarToolbar.java` and `BarlineMenu.java` iterate that array, so removing the
  entry removes both UI surfaces in one edit. The factory method itself is
  **deleted** (it has zero remaining callers — see §10).
- **Click handling:** `PreviewElementManager.java:515-523` routes to
  `addPreviewElement` / `modifyExistingElement` / `insertElement`. The preview
  suppression hook attaches here.
- **Selection hub:** `ui/selection/SelectionCoordinator.java` is the single
  click/drag router (there is no `SelectionHandler.java`). `LineSelectionState`
  holds per-line state. Per-component mouse routing is in
  `ui/component/score/LineSelectionHandler.java`.
- **Arrow-key caret:** stubbed in `ScoreInputHandler.java:251-254`. No live
  code path to modify today — see Non-Goals.

### Load pipeline

- Single entry: `CompositionLoader.load(File)` (line 54) invokes
  `CompositionIO.DocumentReader` (SAX parser). On completion (lines 558-633)
  it runs `FormatMigrator.migrate*` steps in sequence. The final-barline
  migration becomes a new step in that chain — no multi-loader threading.

### Paste / import

- `PasteAction.java` (extends `PasteboardAction`) is the only paste surface.
  There is no `PasteOperation` class, no MusicXML importer, and no ABC
  importer — so paste is the sole normalization site.

### Tests

- `src/test/java/songscribe/music/ElementTypeTest.java:200-266` — barline
  bounds and snap behavior.
- `src/test/java/songscribe/music/LineMutationTest.java:423-430` —
  `testSetElementEndWithFinalDoubleBarlineRetainsEnding`. Must remain green.
- `src/test/java/songscribe/music/MusicEditOperationsMutationTest.java:541-544`
  — repeat-scan stops at final barline.

### Strings

- `action.barline.final.double` and `action.barline.final.double.tooltip` in
  `strings.properties:36-37`. Both are removed.

---

## Design

### 1. The Invariant

**Invariant:** at all times, after any mutation bracket closes, the last line
of the composition has at least one element, and its last element is a
`FINAL_DOUBLE_BARLINE`.

Consequences:

- Every `Composition` has at least one line (already true).
- Every line designated as "last" has at least one element: the final barline.
- A brand-new empty composition already holds a single line whose only
  element is the final barline.

### 2. Selection and Interaction Suppression — single shared predicate

All skip sites consult one predicate. The predicate (naming TBD during
implementation; suggestion `Composition.isFinalBarline(StaffElement,
Line)` or a method on `Line` like `line.isInteractable(element)`) returns
`false` for the final barline on the last line, `true` for every other
element.

| Interaction | Site | Behavior when predicate returns `false` |
|-------------|------|------------------------------------------|
| Arrow-key caret navigation | (stub — see Non-Goals) | Skip over it — reminder comment at `ScoreInputHandler.java:251-254` points at the predicate. |
| Mouse click on the glyph | `SelectionCoordinator` click path | Ignored. Selection and caret position unchanged. |
| Drag-select rectangle | `SelectionCoordinator` rectangle path | Exclude; range selections clip at its left edge. |
| Select All | `SelectAllAction` / selection builder | Does not include it. |
| Hover preview / highlight | `SelectionCoordinator` hover path | Suppressed. |
| Preview element (when a tool is active) | `PreviewElementManager` | Suppressed when hovering where the final barline sits. |
| Delete / Backspace / Cut | — | Not reachable because selection is impossible; no explicit guard is required beyond the selection block. |

The action class `createFinalDoubleBarlineAction()` is **deleted**, not
retained. It has no remaining callers once removed from
`Actions.BARLINE_ACTIONS`, and tests construct `FINAL_DOUBLE_BARLINE` elements
directly rather than through the action factory.

### 3. Guard Layer (Defense in Depth) — `autoMaintenanceDepth` counter

Insertion or removal of a `FINAL_DOUBLE_BARLINE` at a position that would break
the invariant is rejected at two layers:

1. **UI / preview gating** — `PreviewElementManager` hides the preview and
   ignores clicks at the final-barline cell. With the toolbar/menu/keybinding
   gone, this is primarily belt-and-braces for any future internal code path.

2. **API-level validation on `Line`** — guards on `addElement`, `setElement`,
   `removeElement`, and `removeRange` throw `IllegalStateException` when the
   caller would either:
   - insert/replace a `FINAL_DOUBLE_BARLINE` at any position other than "last
     element of last line", **or**
   - remove (singly or within a range) the `FINAL_DOUBLE_BARLINE` on the last
     line.

   The guards are **bypassed** in exactly two cases:
   - `composition.isMutationTrackingSuspended()` is true (load-time migration,
     test fixture setup), **or**
   - `composition.isInAutoMaintenance()` is true (the maintenance block in §4
     is executing).

   `autoMaintenanceDepth` is a sibling counter to `suspensionDepth`, mirroring
   its increment/decrement pattern. It is incremented around the element
   mutations emitted by `Composition.addLine` / `removeLine` so their
   internally-driven calls into `Line.addElement` / `setElement` /
   `removeElement` don't trip the guards.

   Symmetric coverage on the remove side matters: without it, any
   programmatic path (future code, a buggy refactor, or a paste path that
   regresses) could silently break the invariant. The selection block alone
   isn't defense-in-depth.

### 4. Automatic Maintenance in Composition

Both `addLine(int, Line)` and `removeLine(int)` do their maintenance work
**inside the same `withModification` bracket** as the triggering
`LineInsertion` / `LineDeletion`, so one user action = one
`CompositionDidChangeNotification` = (once #14 lands) one undo step.

The maintenance block runs with `autoMaintenanceDepth` incremented so the
guards in §3 are bypassed.

#### `addLine(int index, Line line)`

```
┌──────────────────────────────────────────────────────────────────┐
│ addLine — mutation sequence                                      │
└──────────────────────────────────────────────────────────────────┘

 withModification {                                    ┐
   incrementAutoMaintenance {                          │
     applyChange(LineInsertion(index, line), …)        │
                                                       │  one
     if (line became the new last line) {              │  bracket
       if (prevLast.lastElement == FINAL_DOUBLE_BARLINE)│  =
         applyChange(ElementDeletion on prevLast, …)    │  one
                                                       │  notification
       switch (line.lastElement) {                     │
         FINAL → no-op                                 │
         barline → applyChange(ElementReplacement …)   │
         non-bar → applyChange(ElementInsertion …)     │
         empty   → applyChange(ElementInsertion …)     │
       }                                               │
     }                                                 │
     // else inserted before the current last — no transfer
   }                                                   │
 }                                                     ┘
```

Branches:

1. If `index` makes `line` the new last line (appended at or past the previous
   last), run the transfer block above.
2. Otherwise (inserted in the middle), no final-barline transfer runs — the
   existing last line still owns the invariant.

#### `removeLine(int index)`

```
┌──────────────────────────────────────────────────────────────────┐
│ removeLine — mutation sequence                                   │
└──────────────────────────────────────────────────────────────────┘

 withModification {                                    ┐
   incrementAutoMaintenance {                          │
     applyChange(LineDeletion(index, removed), …)       │
                                                       │
     if (removed line was the last line) {             │  one
       let penult = lines.last                         │  bracket
       switch (penult.lastElement) {                   │
         FINAL   → no-op                               │
         barline → applyChange(ElementReplacement …)   │
         non-bar → applyChange(ElementInsertion …)     │
         empty   → applyChange(ElementInsertion …)     │
       }                                               │
     }                                                 │
   }                                                   │
 }                                                     ┘
```

These diagrams are embedded as javadoc ASCII on `Composition.addLine` /
`Composition.removeLine` so the mutation order is visible at the modification
site and won't rot silently during future refactors.

**Empty-composition guard:** `removeLine` already refuses to reduce the
composition below one line; preserved.

**Mutation tracking:** the internally-emitted mutations are real mutations —
they set the modified flag, participate in (future) undo, and flow through
`CompositionDidChangeNotification` subscribers normally.

### 5. New-Composition Construction — shared helper

A single helper `Composition.createFinalBarlineElement()` returns a fresh
`FINAL_DOUBLE_BARLINE` `StaffElement`. Two sites call it:

- The constructor path that produces a pristine `Composition` (or its
  equivalent `setupNewComposition`) — invokes it once to seed the first
  line. Runs under `withoutMutationTracking` since there is nothing to undo
  on a brand-new composition.
- The migration pass in §6 — invokes it when appending a missing final
  barline on the last line.

One helper, no duplication.

### 6. Load-Time Migration

The file loader deserializes raw `Line` / `Composition` state, then runs a
single migration step `FormatMigrator.migrateFinalBarline` inside
`withoutMutationTracking` (chained alongside the other `FormatMigrator.migrate*`
calls at `CompositionIO.DocumentReader:558-633`).

```
┌──────────────────────────────────────────────────────────────────┐
│ migrateFinalBarline — decision tree                              │
└──────────────────────────────────────────────────────────────────┘

 for each non-last line:
   strip every FINAL_DOUBLE_BARLINE at any position

 for the last line:
   strip any FINAL_DOUBLE_BARLINE that is NOT the last element

   switch (lastLine.lastElement) {
     FINAL_DOUBLE_BARLINE        → no-op
     SINGLE / DOUBLE / REPEAT_R /
       REPEAT_LEFT_RIGHT         → replace with FINAL (via setElement)
     non-barline                 → append FINAL (via addElement)
     empty line                  → append FINAL (via addElement)
   }

 post-migration: invariant holds; regular §3 guards apply to subsequent ops
```

Because migration runs under `withoutMutationTracking`, it does not dirty the
just-loaded document. User-driven maintenance in §4 does dirty the document.

Note: `REPEAT_LEFT_RIGHT` is included in the replaceable-barline set,
consistent with the recent fix in commit `23cec026` recognizing it as a valid
ending split.

### 7. Paste Handling

Clipboard content containing `FINAL_DOUBLE_BARLINE` (same-app copy/paste or
cross-line paste) is normalized by `PasteAction` before insertion:

- Each incoming `FINAL_DOUBLE_BARLINE` is converted to `DOUBLE_BARLINE`.
- The composition's own final barline is not affected.

MusicXML and ABC importer normalization is out of scope — no importer for
either format exists today.

### 8. Rendering: End-Aligned Final Barline — layout owns the x

The element model holds `xPosSs`. Layout is the sole writer for the final
barline's x:

1. **Shared formula.** Extract the flush-right x into a single helper (name
   TBD; suggestion `BarlineGeometry.flushRightXSs(double lineWidthSs)` or a
   static method on `ElementType`). The helper returns `lineWidthSs -
   (thin + sep + thick)`, matching `computeBarlineBoundsSs`. Both the layout
   stage and any other call site that needs the flush-right x call this
   helper — the formula lives in exactly one place.

2. **Layout pass.** In the line-layout stage driven by
   `HorizontalSpacingCalculator.calculatePositions`, after all other elements
   have been positioned on the last line:
   - Locate the final barline.
   - Compute its target x via the shared helper.
   - Set the element's `xPosSs`.

3. **`BarRenderer`.** No code change. The final barline branch at
   `BarRenderer.java:132-135` continues to draw two rectangles at the resolved
   x; the resolved x is now the layout-set flush-right value.

4. **`HorizontalAdjustment`.** Drag-snap remains in place for other `snapToEnd`
   barlines but **explicitly skips the final barline** — layout is the sole
   writer of its x. Without this skip, the two writers could stamp on each
   other during a future refactor.

### 9. Removed UI

| Item | Action |
|------|--------|
| `Actions.BARLINE_ACTIONS` entry for final double barline | Remove. Both `BarToolbar` and `BarlineMenu` pick up the change automatically. |
| `ElementTypeAction.createFinalDoubleBarlineAction()` factory method | Delete. Zero remaining callers after array edit. |
| Shift+F keybinding | Removed with the action. |
| `action.barline.final.double` string | Remove from `strings.properties`. |
| `action.barline.final.double.tooltip` string | Remove from `strings.properties`. |

**Strings residual-reference sweep:** before deleting the two keys, grep
`src/` and `resources/` for `Strings.ACTION_BARLINE_FINAL_DOUBLE` and
`Strings.ACTION_BARLINE_FINAL_DOUBLE_TOOLTIP`. The generated `Strings.java`
constants must have zero remaining references. This matches the project rule
in `.claude/rules/strings.md`.

### 10. Factory Deletion Rationale

Plan deletes `createFinalDoubleBarlineAction()` rather than retaining it for
"internal/programmatic use." Tests construct `FINAL_DOUBLE_BARLINE` elements
directly (`new Note(ElementType.FINAL_DOUBLE_BARLINE, …)`) — they do not
need the action factory. Retaining a method with zero production callers
invites bitrot.

### 11. Undo Grouping Dependency

The plan's mutation coalescing gives subscribers a single
`CompositionDidChangeNotification` per user action; that is delivered by this
plan. Collapsing the trigger + auto-maintenance mutations into a **single
undo step** depends on issue #14 (the TODO at `Composition.java:849`: "snapshot
composition state here for undo grouping"). The plan's test for single-step
undo is marked as pending on #14.

---

## Files to Modify

| File | Change |
|------|--------|
| `music/Composition.java` | Add `autoMaintenanceDepth` + `isInAutoMaintenance()`. Add `createFinalBarlineElement()` helper. Constructor seeds first line. `addLine` / `removeLine` run maintenance block with ASCII-diagrammed javadoc. |
| `music/Line.java` | Guards on `addElement`, `setElement`, `removeElement`, `removeRange` for `FINAL_DOUBLE_BARLINE`, checking both suspension and auto-maintenance. |
| `music/ElementType.java` | Optional: shared flush-right x helper if that's where it lands. |
| (new) `BarlineGeometry` or `ElementType.flushRightXSs()` | Shared flush-right x helper, single source of truth for `lineWidthSs - contentWidthSs`. |
| `file/FormatMigrator.java` | New `migrateFinalBarline` step. |
| `io/CompositionIO.java` (DocumentReader completion, 558-633) | Wire the new migration step into the chain. |
| `ui/renderer/BarRenderer.java` | No change. |
| `ui/layout/HorizontalSpacingCalculator.java` (or `LayoutEngine.java`) | End-aligned x for the final barline on the last line, using the shared helper. |
| `ui/layout/HorizontalAdjustment.java` | Explicit skip of the final barline in the drag-snap loop. |
| `ui/component/score/PreviewElementManager.java` | Suppress preview / click at the final-barline cell. |
| `ui/selection/SelectionCoordinator.java` | Click / drag / select-all / hover skip the final barline via the shared predicate. (No separate `SelectionHandler.java`.) |
| `ui/action/SelectAllAction.java` (or the range builder it delegates to) | Range excludes the final barline via the shared predicate. |
| `ui/component/score/ScoreInputHandler.java:251-254` | One-line reminder comment at the arrow-nav TODO pointing at the shared predicate. |
| `ui/action/Actions.java` | Remove `createFinalDoubleBarlineAction()` entry from `BARLINE_ACTIONS`. |
| `ui/action/ElementTypeAction.java` | Delete `createFinalDoubleBarlineAction()`. |
| `resources/songscribe/strings.properties` | Remove `action.barline.final.double` and `action.barline.final.double.tooltip` after residual-reference sweep. |
| `ui/action/PasteAction.java` | Convert incoming `FINAL_DOUBLE_BARLINE` to `DOUBLE_BARLINE`. |

The shared selectability predicate lives on whichever type reads cleanest at
the call sites (candidates: a method on `Composition`, on `Line`, or a static
on `StaffElement`). Settle this during implementation — the requirement is
one predicate, not where it lives.

---

## Test Plan

Unit tests (all under `src/test/java/songscribe/` unless otherwise noted).
E2E tests are out of scope.

### Insertion guard tests (`LineMutationTest`)

- `Line.addElement` throws `IllegalStateException` when inserting
  `FINAL_DOUBLE_BARLINE` anywhere but `elementCount()` on the last line.
- `Line.addElement` throws when inserting `FINAL_DOUBLE_BARLINE` on a non-last
  line (any index).
- `Line.setElement` throws when replacing with `FINAL_DOUBLE_BARLINE` at any
  index other than `elementCount() - 1` on the last line.

### Removal guard tests (`LineMutationTest`, new)

- `Line.removeElement` throws when the target is the `FINAL_DOUBLE_BARLINE` on
  the last line.
- `Line.removeRange` throws when the range includes the `FINAL_DOUBLE_BARLINE`
  on the last line.
- All four guards (add/set/remove/removeRange) are bypassed when
  `composition.isMutationTrackingSuspended()` is true.
- All four guards are bypassed when `composition.isInAutoMaintenance()` is
  true — verified indirectly via the auto-maintenance tests below.

### Auto-maintenance tests (on `Composition`)

- Adding a new line after the current last: former last line loses its final
  barline (`ElementDeletion`), new last line gains one (`ElementInsertion` or
  `ElementReplacement` depending on its contents).
- Adding a line before the current last: no transfer — the last line is
  unchanged.
- Adding a line with zero elements as the new last: final barline is
  `ElementInsertion`ed.
- Removing the last line: the penultimate becomes the new last and has a
  final barline as its last element (no-op / `ElementReplacement` /
  `ElementInsertion` branches exercised).
- Removing a non-last line: no maintenance runs.
- All maintenance coalesces with its trigger into **one**
  `CompositionDidChangeNotification` — subscribers see the trigger mutation
  and the maintenance mutations in a single event.
- **Explicit bypass assertion:** `composition.addLine(…)` from a state
  requiring replacement completes without throwing `IllegalStateException` —
  proves `isInAutoMaintenance` gates the guard. One-line assertion.
- The modified flag is set by user-driven maintenance.
- (pending #14) Both trigger and maintenance revert in a single undo step.

### Selectability predicate test (new)

Direct unit test of the shared predicate:

- Final barline on last line → `false`.
- Non-final barline on last line → `true`.
- Same `FINAL_DOUBLE_BARLINE` element on a non-last line → `true`
  (programmatic edge case; real compositions can't reach this, but the
  predicate must return a sensible value).
- A note / rest / other element → `true`.

This is the single locus of regression for every selection skip site.

### Layout end-aligned x test (new)

New test class `HorizontalSpacingCalculatorTest` (or add to `LayoutEngineTest`):

- On the last line, after `LayoutEngine.layout(line)`, the final barline's
  `xPosSs` equals `flushRightXSs(lineWidthSs)` — i.e., `lineWidthSs - (thin +
  sep + thick)` via the shared helper.
- On a non-last line, barlines are **not** end-aligned — confirms the layout
  gate is limited to the last line.

### New-composition test

- A default-constructed `Composition` satisfies the invariant: exactly one
  line, exactly one element on it, element is `FINAL_DOUBLE_BARLINE`.
- Layout of the brand-new composition completes without error (smoke check
  for "line with only a final barline" layout path).

### Load-time migration tests

Fixtures with a hand-built `Composition` run through `migrateFinalBarline`:

- Misplaced final barline on a non-last line → removed; final-position
  barline on last line is inserted/preserved correctly.
- Multiple misplaced final barlines → all removed.
- Last line ends in `SINGLE_BARLINE` → replaced with `FINAL_DOUBLE_BARLINE`.
- Last line ends in `DOUBLE_BARLINE` → replaced.
- Last line ends in `REPEAT_RIGHT` → replaced.
- Last line ends in `REPEAT_LEFT_RIGHT` → replaced.
- Last line ends with a note (no trailing barline) → `FINAL_DOUBLE_BARLINE`
  appended.
- Last line already ends in `FINAL_DOUBLE_BARLINE` → no-op.
- Last line is empty → `FINAL_DOUBLE_BARLINE` appended.
- Migration does not dirty the document.

### Ending-interaction regression

- `testSetElementEndWithFinalDoubleBarlineRetainsEnding` in `LineMutationTest`
  continues to pass unmodified.

### Paste normalization

- Paste source containing `FINAL_DOUBLE_BARLINE` → inserted as
  `DOUBLE_BARLINE`; the composition's own final barline is untouched.

---

## Implementation Checklist

One per accepted review recommendation — useful as a PR checklist:

- [ ] `autoMaintenanceDepth` + `isInAutoMaintenance()` sibling to
  `suspensionDepth` (review Issue 1).
- [ ] Symmetric guards on `removeElement` / `removeRange` (Issue 2).
- [ ] Shared `flushRightXSs` helper; `HorizontalAdjustment` skips the final
  barline; layout owns its x (Issue 3).
- [ ] ASCII diagrams embedded in `addLine` / `removeLine` javadoc (Issue 4).
- [ ] Delete `createFinalDoubleBarlineAction()` (Issue 5).
- [ ] Single shared selectability predicate used by every skip site
  (Issue 6).
- [ ] Single `createFinalBarlineElement()` helper for default + migration
  paths (Issue 7).
- [ ] Residual-reference sweep before removing `Strings` keys (Issue 8).
- [ ] Layout end-aligned x unit test (Issue 9).
- [ ] Selectability predicate unit test (Issue 10).
- [ ] Removal guard unit tests (Issue 11).
- [ ] Auto-maintenance test includes explicit "no guard exception" assertion
  (Issue 12).

---

## Open Questions

None. Decisions captured during review:

- Invariant applies always, including on brand-new empty compositions.
- Auto-maintenance uses one mutation bracket per trigger; one
  `CompositionDidChangeNotification`; single-step undo is pending #14.
- On-load migration runs under `withoutMutationTracking` and does not dirty
  the document.
- Final barline is unselectable via a single shared predicate.
- Toolbar entry, menu entry, keybinding, action factory, and string keys are
  all removed.
- Paste converts final barlines to `DOUBLE_BARLINE`; MusicXML / ABC import
  paths do not exist.
- Guards are symmetric (add / set / remove / removeRange) and bypassed via
  `autoMaintenanceDepth` or `suspensionDepth`.
- Layout is the sole writer of the final barline's x; `HorizontalAdjustment`
  explicitly skips it; the flush-right formula lives in one helper.
