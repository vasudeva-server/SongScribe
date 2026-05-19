# Trill Action Rework

Rework the trill menu action so the menu reads "Trill" (not "Toggle Trill") and
behaves as a checked menu item that reflects whether the current selection
overlaps any trill range. Convert the action to the `Reflectable` pattern used
by `FermataAction`, with semantics suited to range-based storage.

**Issue:** vasudeva-server/SongScribe#355

**Precondition:** This spec assumes `rendering-rewrite.md` is complete. By the
time this work begins:

- `StaffElement.trill` boolean, `isTrill()`, `setTrill(...)`, and
  `ElementField.TRILL` no longer exist.
- `NoteAttachedStacker.bridgeLegacyTrillFlags` no longer exists.
- The per-note `<trill/>` tag in `StaffElementIO` is gone; trill serialization
  lives in `LineIO` alongside other `RangeElement`s.
- `Trill` `RangeElement`s on `line.rangeElements` are the sole representation
  of a trill.
- `MusicEditOperations.toggleTrill` / `canToggleTrill`,
  `ScoreViewController.handleToggleTrill` / `canToggleTrill`, and
  `ToggleTrillCommand` either survive the rewrite or have already been
  reworked; this spec deletes them outright as part of the action conversion.

---

## Goals

1. Rename the menu item from "Toggle Trill" to "Trill".
2. The menu item is a `JCheckBoxMenuItem` whose checked state reflects whether
   the current selection overlaps any `Trill` range on the line.
3. In the preview-note flow (no active selection), the action behaves exactly
   like `FermataAction`: checking it decorates the next entered note with a
   trill, and the action auto-resets to unchecked after note insertion.
4. With an active selection:
   - Checking: create one `Trill` `RangeElement` spanning the full selection
     (anchor = first selected element, end = last selected element).
   - Unchecking: remove every `Trill` range that overlaps the selection.
5. Delete the now-redundant `ToggleTrillCommand` / `toggleTrill` plumbing in
   `MusicEditOperations` and `ScoreViewController`.

---

## Current State (post-rendering-rewrite)

### Action and Plumbing

- `ToggleTrillAction extends UIAction` (not `Reflectable`, not
  `SelectableUIAction`). `actionPerformed` posts `ToggleTrillCommand` to
  `MessageCenter`. The command is handled by
  `ScoreViewController.handleToggleTrill` → `MusicEditOperations.toggleTrill`.
- `MusicEditOperations.toggleTrill` iterates the selection and flips each note
  independently. After the rendering rewrite this method either operates on
  `Trill` `RangeElement`s (per-note add/remove) or has already been deleted —
  either way this spec removes it.
- `musicSelectionDidChange` in `ToggleTrillAction` drives only the enabled
  state via `canToggleTrill`. There is no `setSelected` call anywhere.
- Registered as `Actions.TOGGLE_TRILL_ACTION` (`Actions.java:244`).
- No keyboard shortcut bound; no tooltip set.
- Menu item: `new JCheckBoxMenuItem(TOGGLE_TRILL_ACTION)` in
  `NotationMenu.java:70`. No toolbar button.

### Data Model

- `Trill extends RangeElement` (`src/main/java/songscribe/ui/layout/Trill.java`).
- Stored in `line.rangeElements`. Added/removed via `Line.addRangeElement` /
  `Line.removeRangeElement`, which emit generic `RangeElementAddition` /
  `RangeElementRemoval` mutations.
- Single-note trill is represented as `new Trill(note, note)`.
- The "tr" glyph renders at `anchorElement`; a wavy line extends to
  `endElement`. Adjacent trills are not auto-merged by the layout.

### Strings

- `action.trill.toggle = Toggle Trill` (`strings.properties:145`).
- Constant: `Strings.ACTION_TRILL_TOGGLE` in generated `Strings.java`.
- No existing `action.trill.tooltip`.

### Reflectable Pattern Reference

`FermataAction` (`src/main/java/songscribe/ui/action/FermataAction.java`):

- `extends NoteOnlyAction extends PreviewElementAction extends SelectableUIAction extends UIAction`
- `implements UIAction.ElementModifiable` (which extends `Reflectable`, which
  extends `Selectable`).
- Overrides: `matchesElement`, `applyToElement`, `modifiedFields`, `appliesTo`
  (from `NoteOnlyAction`).
- `SelectionCoordinator.triggerReflection()` walks the selection and calls
  `setSelected(applicable && matched)` where `matched` requires *every*
  applicable element to match (early-break on first mismatch).
- `PreviewElementAction.actionPerformed` → `applyToSelectionIfActive` →
  `SelectionCoordinator.applyActionToSelection(reflectable, isSelected())`.
  Posts `UpdatePreviewElementCommand` when no selection.
- `EditModeManager.previewElementDidChange` explicitly resets
  `FERMATA_ACTION.setSelected(false)` after each preview-note commit.

---

## Design

### Semantics summary

- **Checked state**: true iff any element in the current selection (or the
  preview element) is covered by a `Trill` `RangeElement` on the line.
- **Click while unchecked → check**: add `new Trill(elements[begin], elements[end])`
  to `line.rangeElements`. Preview path: decorate next note with a single-note
  trill; auto-reset.
- **Click while checked → uncheck**: remove every `Trill` `RangeElement` that
  overlaps the selection. (One element's selection always overlaps at most one
  trill range, but multi-note selections may overlap several — remove them all.)
- **No mixed/tri-state**: any overlap counts as checked. There is no
  indeterminate visual.
- **Applicability**: pitched notes only (matches existing `canToggleTrill`
  semantics via `LineSelectionState.canToggleTrill`).

### `matchesElement` semantics

`matchesElement(element)` returns true iff some `Trill` on
`element.getLine().findRangeElements(Trill.class)` covers `element` (i.e.
`anchor.getIndex() <= element.getIndex() <= end.getIndex()`).

Since `SelectionCoordinator.triggerReflection` early-breaks on the first
non-match, this gives the desired "any overlap → checked" behavior **only** if
the loop is inverted to early-break on the first *match*. Two options:

1. **Custom reflection override.** `TrillAction` overrides the default
   `Reflectable` reflection path with a class-specific `reflectSelection` that
   returns true on first match instead of requiring all-match. Requires either
   adding a `Reflectable` hook to `SelectionCoordinator` or having
   `TrillAction` subscribe directly to `MusicSelectionDidChangeNotification`
   (in the manner of the current `ToggleTrillAction`, but updating
   `setSelected` rather than `setEnabled`).

2. **Tweak `triggerReflection`** to read a flag on the `Reflectable`
   indicating "any-match" vs "all-match" semantics, defaulting to all-match.

This spec uses **option 1, subscribe-directly**: `TrillAction` keeps its own
`@Handler musicSelectionDidChange` that computes the any-overlap check and
calls `setSelected` itself. This avoids touching `SelectionCoordinator`
semantics that other actions depend on.

### Apply semantics

`TrillAction` does **not** implement `ElementModifiable`'s per-element
`applyToElement` cleanly — its apply step operates on the line, not on a
single element. So the action overrides `actionPerformed` directly rather
than relying on `applyToSelectionIfActive`:

```java
@Override
public void actionPerformed(ActionEvent e) {
    toggleOnKeyboardShortcut(e);  // keyboard path flips isSelected manually

    var line = coordinator.getActiveLine();
    if (line == null) {
        MessageCenter.post(new UpdatePreviewElementCommand());
        return;
    }

    var selection = coordinator.getActiveSelection();
    if (selection == null) {
        MessageCenter.post(new UpdatePreviewElementCommand());
        return;
    }

    line.withModification(() -> {
        if (isSelected()) {
            addTrillSpanningSelection(line, selection);
        } else {
            removeOverlappingTrills(line, selection);
        }
    });
}
```

`addTrillSpanningSelection`:

1. Clamp anchor / end to the first / last applicable (pitched) element in the
   selection range.
2. If no applicable elements, no-op.
3. Otherwise add `new Trill(anchor, end)` via `line.addRangeElement(...)`.

`removeOverlappingTrills`:

1. Collect every `Trill` in `line.findRangeElements(Trill.class)` whose range
   `[anchor.index, end.index]` overlaps the selection range
   `[selection.begin, selection.end]`.
2. Call `line.removeRangeElement(trill)` for each. Each call emits its own
   `RangeElementRemoval` mutation inside the surrounding `withModification`.

### Preview-note flow

When no selection is active and the menu is clicked, `actionPerformed` posts
`UpdatePreviewElementCommand`. The handler chain:

1. `ScoreViewController.handleUpdatePreviewElement` →
   `EditModeManager.decorateElement(previewElement)`.
2. `decorateElement` is extended to handle the trill action: if
   `TRILL_ACTION.isSelected()`, mark the preview element with a transient
   "trill" decoration. Because `StaffElement.trill` no longer exists,
   `decorateElement` instead writes a transient flag on the preview element
   (new `previewTrill` boolean on `StaffElement`, or a transient side-channel
   on `EditModeManager`).
3. On commit, `PreviewElementManager.insertElement` → `line.addElement(...)`
   places the bare element. Immediately after, `previewElementDidChange` (or
   a new hook) consults whether the preview was trill-decorated and, if so,
   calls `line.addRangeElement(new Trill(committed, committed))`.
4. `previewElementDidChange` resets `Actions.TRILL_ACTION.setSelected(false)`
   alongside the existing fermata / accidental-parens resets.

The transient `previewTrill` boolean is **not** persisted by `LineIO` — it
exists only on the in-memory preview element until commit promotes it.

### Class structure

`TrillAction` replaces `ToggleTrillAction`:

```
TrillAction extends SelectableUIAction
  implements Listener
  // Not ElementModifiable — its apply step is line-scoped, not element-scoped.
  // Not NoteOnlyAction — the applicability check runs inside actionPerformed
  // because we clamp the span to pitched elements rather than skipping them.
```

Flags: `REQUIRES_SELECTION` is **dropped** — the action is now valid in
preview-note flow. Retain `DISABLE_WHEN_PLAYING`,
`DISABLE_WHEN_EDITING_TEXT`, `DISABLE_IN_GRACE_MODE`. The enabled state
otherwise mirrors fermata: enabled whenever the selection contains a pitched
note, or in preview mode whenever the preview type is a pitched note.

`@Handler` methods:

- `musicSelectionDidChange(MusicSelectionDidChangeNotification msg)` —
  recompute checked state by scanning the selection for trill overlap,
  recompute enabled state from `canApply` (pitched-note presence).
- `previewElementDidChange(...)` — recompute checked state from the preview
  element when no selection is active (mirrors fermata's reflection).

### Mutation tracking

This spec keeps the generic `RangeElementAddition` / `RangeElementRemoval`
emissions from `Line.addRangeElement` / `Line.removeRangeElement`. Typed
`TrillAddition` / `TrillRemoval` records are **not** introduced — the generic
records carry the `RangeElement` payload and that is sufficient for undo /
notification consumers. (If undo / observers need to distinguish trill events
specifically, they can `instanceof` the payload.)

The whole action runs inside one `line.withModification(...)` block, so a
multi-range removal produces a single `LineDidChangeNotification` /
`SongDidChangeNotification` (per the mutations guide).

---

## Strings

Rename the resource key to align with fermata's convention.

- **Remove**: `action.trill.toggle = Toggle Trill`
- **Add**: `action.trill = Trill`
- **Add**: `action.trill.tooltip = Add trill`

Update the generated `Strings.java`:

- Remove `ACTION_TRILL_TOGGLE`.
- Add `ACTION_TRILL`, `ACTION_TRILL_TOOLTIP`.

Use the existing string-regeneration tool (whatever produces
`build/generated-sources/songscribe/Strings.java`). Do not hand-edit the
generated file.

---

## Files to Change

### Action layer

- **Delete** `src/main/java/songscribe/ui/action/ToggleTrillAction.java`.
- **Create** `src/main/java/songscribe/ui/action/TrillAction.java` (design
  above).
- **Update** `src/main/java/songscribe/ui/action/Actions.java`:
  - Replace `TOGGLE_TRILL_ACTION = ToggleTrillAction.createAction()` with
    `TRILL_ACTION = TrillAction.createAction()`.

### Command / handler removal

- **Delete** `ToggleTrillCommand` (path TBD — find via `find_symbol`).
- **Update** `src/main/java/songscribe/ui/component/ScoreViewController.java`:
  remove `handleToggleTrill` and `canToggleTrill` along with their `@Handler`
  registration and any `MessageCenter.subscribe` plumbing.
- **Update** `src/main/java/songscribe/ui/MusicEditOperations.java`: remove
  `toggleTrill()` and `canToggleTrill()`. The new action calls
  `line.addRangeElement` / `line.removeRangeElement` directly.
- **Update** `src/main/java/songscribe/ui/selection/LineSelectionState.java`:
  remove `canToggleTrill()` if it has no other callers.

### Menu

- **Update** `src/main/java/songscribe/ui/menu/NotationMenu.java:70`:
  `new JCheckBoxMenuItem(TRILL_ACTION)` (renamed reference).

### Preview-element decoration

- **Update** `src/main/java/songscribe/ui/edit/EditModeManager.java`:
  - In `decorateElement`, set the new transient `previewTrill` flag on the
    preview element when `TRILL_ACTION.isSelected()`.
  - In `previewElementDidChange`, after `line.addElement(committed, ...)`,
    add a `new Trill(committed, committed)` range element if the committed
    preview was trill-decorated.
  - Add `Actions.TRILL_ACTION.setSelected(false)` to the existing reset block
    (alongside fermata, accidental-parens).
- **Update** `src/main/java/songscribe/model/StaffElement.java`: add a
  transient `previewTrill` boolean (not serialized) used only by the preview
  flow. Alternatively, store the flag on `EditModeManager` keyed by the
  preview element identity — pick whichever fits the post-Phase-6 codebase
  better; `previewTrill` is suggested because `previewFermata` may exist by
  then via the same pattern.

### Reflection on grace-note pairing

- **Update** `src/main/java/songscribe/ui/edit/GraceModeManager.java`
  (`enterGraceNotePaired` calls `SelectionCoordinator.reflectElement`):
  ensure `TRILL_ACTION` reflects the host element's trill-coverage state.
  Since `TrillAction` is not `Reflectable`, add an explicit `TRILL_ACTION`
  update or extend `reflectElement` to consult any line-context-aware
  actions. Recommended: drive `TrillAction.reflectElement(element)` from
  `SelectionCoordinator.reflectElement` via a narrow new interface
  (`LineContextReflectable`) so the call site is generic.

### Strings

- `src/main/resources/songscribe/strings.properties` — replace the key.
- Regenerate `Strings.java`.

---

## Out of Scope

- Tri-state / indeterminate menu visual. (Decided no.)
- Typed `TrillAddition` / `TrillRemoval` mutation records. Generic
  `RangeElementAddition` / `RangeElementRemoval` are sufficient.
- Auto-merging adjacent / overlapping `Trill` ranges. Adding a `Trill` over
  a region that overlaps existing trills produces stacked / coexisting trill
  ranges (the renderer handles this gracefully today). If consolidation is
  desired, file a follow-up.
- Splitting a `Trill` range when only part of it is unchecked. Per the
  decided semantics, any overlap deletes the whole range.
- Adding a toolbar button for trill. (None exists; none is requested.)
- Keyboard shortcut binding. (None exists; out of scope unless the user
  asks.)
- Migrating tie / tuplet / hairpin SpanSets — that is rendering-rewrite
  Phase 7+ and unrelated to this issue.

---

## Tests

### Unit tests

Place under `src/test/java/songscribe/ui/action/TrillActionTest.java` and
`src/test/java/songscribe/model/TrillActionMutationTest.java`. Read
`.agents/guides/testing-unit.md` first.

1. **`testCheckedWhenSelectionOverlapsTrill`** — line with `Trill(3,7)`;
   selection 5–10; assert `TRILL_ACTION.isSelected()` after
   `MusicSelectionDidChangeNotification`.
2. **`testUncheckedWhenSelectionDoesNotOverlap`** — line with `Trill(3,7)`;
   selection 8–10; assert `!isSelected()`.
3. **`testUncheckedWhenLineHasNoTrills`**.
4. **`testCheckedForSingleNoteTrill`** — `Trill(5,5)`, selection {5}.
5. **`testCheckActionAddsSpanningTrill`** — unchecked → click; assert one
   `Trill` exists with `anchor=elements[begin]`, `end=elements[end]`.
6. **`testCheckActionClampsToPitchedElements`** — selection includes a rest
   at the boundary; assert anchor/end clamp to the outermost pitched notes.
7. **`testCheckActionNoOpWhenNoPitchedElements`** — selection is all rests;
   no `Trill` added.
8. **`testUncheckActionRemovesAllOverlappingTrills`** — line with
   `Trill(3,5)` and `Trill(7,9)`; selection 4–8; assert both removed.
9. **`testActionFiresSingleLineDidChangeForMultiRemoval`** — assert exactly
   one `LineDidChangeNotification` emitted for a multi-range removal.
10. **`testPreviewFlowAddsSingleNoteTrillOnCommit`** — no selection;
    `TRILL_ACTION.setSelected(true)`; insert a pitched note via the preview
    manager; assert a `Trill(newNote, newNote)` exists and
    `TRILL_ACTION.isSelected()` is now false.
11. **`testPreviewFlowDoesNotAffectExistingTrills`** — preview-add a trill
    on note N+1 while a `Trill(0, N-1)` exists; assert both ranges coexist.
12. **`testGraceModeReflectsHostTrillCoverage`** — paired grace-note entry
    on a host inside a `Trill` range sets `TRILL_ACTION.setSelected(true)`.

### E2E tests

Not required for this issue (action-level behavior is fully exercised by unit
tests). If e2e coverage is desired, file a follow-up.

---

## Build Sequence

1. **Strings**. Update `strings.properties`, regenerate `Strings.java`.
   Compile to confirm `Strings.ACTION_TRILL` / `ACTION_TRILL_TOOLTIP`
   resolve and `ACTION_TRILL_TOGGLE` references are all replaced.
2. **TrillAction**. Create the new class. Leave `ToggleTrillAction`
   in place temporarily so `Actions.java` still compiles.
3. **Actions.java**. Add `TRILL_ACTION`; delete `TOGGLE_TRILL_ACTION` and
   the import for `ToggleTrillAction`.
4. **NotationMenu.java**. Update to reference `TRILL_ACTION`.
5. **EditModeManager preview hook**. Wire `previewTrill` decoration and
   the post-commit `addRangeElement(new Trill(...))` step. Add the
   `setSelected(false)` reset.
6. **GraceModeManager reflection**. Wire the host-element trill check.
7. **Delete legacy**:
   - `ToggleTrillAction.java`
   - `ToggleTrillCommand` (and any subscribe sites in
     `ScoreViewController`)
   - `ScoreViewController.handleToggleTrill` / `canToggleTrill`
   - `MusicEditOperations.toggleTrill` / `canToggleTrill`
   - `LineSelectionState.canToggleTrill` (if unused after the above)
8. **Tests**. Add the unit tests in the order above.
9. **`./scripts/compile.sh`**. Resolve any remaining references.
10. **`./scripts/test.sh unit`**.

---

## Risks

- **Reflection asymmetry.** `TrillAction` does not use
  `SelectionCoordinator.triggerReflection` (since "any match" differs from
  the default "all match"). It maintains its own `@Handler` subscription.
  If `SelectionCoordinator` gains additional lifecycle hooks
  (`musicSelectionDidChangeSaveRestoreActionStates`, action-state
  save/restore for selection-mode), the trill action must participate in
  them explicitly. Audit `SelectionCoordinator` priorities at integration
  time.
- **Preview-element decoration state**. The transient `previewTrill` flag
  must not leak into committed elements (only the `Trill` range element
  should). Verify in tests that committing a preview never sets a non-trill
  attribute on the resulting `StaffElement`.
- **Multi-line selections.** If `LineSelectionState` ever supports a
  selection that spans multiple lines, the add/remove logic must iterate
  per affected line. Check current selection model assumptions; today
  selection is line-scoped, so single-line handling is sufficient.
- **Phase-6 dependency.** This spec assumes the trill boolean is gone. If
  scheduling demands shipping this issue before Phase 6 completes, the spec
  must be expanded with a transitional shim: write through to both `Trill`
  RangeElement and the legacy `isTrill()` boolean on add, clear booleans on
  remove, and the `matchesElement` check OR's the boolean with range
  coverage. Flag for re-spec if scheduling slips.
