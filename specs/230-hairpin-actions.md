# Hairpin Action Rework

Rework crescendo/diminuendo (hairpin) menu actions and introduce direct hairpin
selection for deletion. Replaces the three-item Dynamic submenu entries with two
context-sensitive items and adds click-to-select hairpin interaction.

**Issue:** vasudeva-server/SongScribe#230

---

## Prerequisite

This spec assumes the **hit-test and selection-color refactor has already
landed**. That refactor is tracked separately; its handoff is
`specs/hit-test-selection-refactor.md`.

Two things it delivers that this spec depends on:

1. `songscribe.ui.hit.HitTester` — a single hit-test interface, with
   `LineSelectionHandler` iterating an ordered list of testers instead of an
   imperative cascade. `HitResult` moves to `songscribe.ui.hit` and becomes
   public.
2. `LineComponent.SelectionProvider.isSelected(LineElement, int lineIndex)` —
   one method replacing the per-decoration `isEndingSelected`, plus
   `RenderingUtils.decorationSelectionColor(LineElement, LineInvariants)` as the
   shared implementation of the decoration-selection color rule.

With both in place, hairpin hit testing is one list entry and hairpin
highlighting is one call — no new interface methods, no new `determine*Color`
method.

---

## Goals

1. **Context-sensitive menu items** whose labels and enabled state adapt to the
   current note selection and the hairpins around it. **Nothing is ever hidden.**
2. **Direct hairpin selection** by clicking the rendered hairpin, enabling
   deletion with the Delete key
3. **Mutual exclusivity** between hairpin selection and note/line/slide/ending
   selection
4. **Point dynamics and hairpins cannot coexist** on the same note
5. **Extend by a single note** — selecting one note adjacent to a hairpin
   extends that hairpin
6. **Clean removal** of `RemoveDynamicsAction` and `RemoveDynamicsCommand`

---

## Data Model (as it actually exists)

```
StaffElement ──owns──> Attachment
                          └─ DynamicAttachment   (point dynamic, renders BELOW staff)
     ▲
     │ anchorElement / endElement   ← object references, NOT int indices
     │
RangeElement ──> Hairpin (sealed) ──> Crescendo | Diminuendo   (renders ABOVE staff)
     │              └─ x1ShiftSs, x2ShiftSs, yShiftSs
     │
Line.rangeElements : List<RangeElement>          ← one flat list; there is no IntervalSet
     ├─ getCrescendos() / getDiminuendos()  → findRangeElements(T.class) : List<T>
     │                                         (full scan + fresh ArrayList per call)
     ├─ addCrescendo(c) / addDiminuendo(d)  → addHairpin(…, absorbAdjacent = true)
     │                                         └─ mergeOverlappingSpans (Line.java:1681)
     ├─ removeCrescendo(c) / removeDiminuendo(d)
     ├─ isInHairpinRange(int)                     [exists]
     ├─ precedingGraceNoteIndex(int)              [exists — grace host is index + 1]
     └─ withModification(opName, Runnable)        ← all mutations go through this

RangeElement.overlaps(int begin, int end)               [exists]
RangeElement.getAnchorElementIndex() / getEndElementIndex()   [exist]
ElementType.isPitchedNote() / isGraceNote() / isBarLine() / isRepeat()   [all exist]
```

`Line.addCrescendo` routes through `mergeOverlappingSpans` with
`absorbAdjacent = true`, which widens the new span to absorb any same-type
hairpin that overlaps *or merely touches* it. Extend relies on that merge, but
does **not** rely on it to fix a degenerate span — see §7.

---

## Design

### 1. Menu structure

`NotationMenu.createDynamicsMenu()` keeps its current shape: the hairpin items,
then a separator, then `DYNAMIC_MARKING_ACTION_GROUP` (the point pp/mf/ff radio
items). The menu title string `menu.notation.dynamics` ("Dynamic") is unchanged.

Only the hairpin portion changes — from three items to two:

| Item | Default text | Default state |
|------|-------------|---------------|
| Crescendo item | "Add Crescendo" | Disabled |
| Diminuendo item | "Add Diminuendo" | Disabled |

**Items are never hidden.** When only one of the two is relevant (the extend
case), the other stays visible, reverts to its default "Add …" label, and is
disabled.

`NotationMenu` stays exactly as it is structurally — plain
`menu.add(new JMenuItem(HAIRPIN_CRESCENDO_ACTION))`. It registers nothing and
holds no state.

### 2. Ownership

There is **no controller class**. Label and enabled state both live on the
`Action`, and a `JMenuItem` built from an `Action` mirrors both automatically —
that is the point of using actions.

`HairpinAction` (the renamed `AddDynamicsAction`) follows the pattern already
used by `ToggleNotationAction` (`:109-133`): override the notification handlers,
gate on `updateEnabledState()`, then defer the decision to a `ScoreViewController`
query. Do **not** override `updateEnabledState()` itself.

```java
@Override
@Handler
public void musicSelectionDidChange(MusicSelectionDidChangeNotification message) {
    handleChange();
}

@Override
@Handler
public void songDidChange(SongDidChangeNotification message) {
    handleChange();
}

@Override
@Handler
public void documentDidLoad(DocumentDidLoadNotification message) {
    handleChange();
}

private void handleChange() {
    var ctrl = getScoreViewController();

    if (ctrl != null && updateEnabledState()) {
        applyHairpinState(ctrl.getHairpinActionState());
    }
}
```

`applyHairpinState` sets both the enabled flag and `Action.NAME`
(`putValue(Action.NAME, …)`) from the state, per the table in §3.

**Flags.** `HairpinAction` keeps every mode flag it has today
(`DISABLE_IN_REST_MODE`, `DISABLE_WHEN_BAR_SELECTED`, `DISABLE_WHEN_PLAYING`,
`DISABLE_WHEN_EDITING_TEXT`, `DISABLE_IN_ADJUSTMENT_MODE`,
`DISABLE_IN_GRACE_MODE`) and swaps `REQUIRES_MULTIPLE_SELECTION` for
**`REQUIRES_SELECTION`**. `REQUIRES_SELECTION` is `size > 0`
(`UIAction.java:524-526`); `REQUIRES_SINGLE_SELECTION` is `size == 1` and would
disable the action on any multi-note selection, making Add impossible. The
single-note extend case needs `size == 1` to be admissible, and Add needs
`size > 1`, so `REQUIRES_SELECTION` is the only flag that covers both.

**Known residual.** `updateEnabledState()` is also called from roughly ten other
`UIAction` handlers — mode change, playback, MIDI state, dialog visibility, text
editing, song state — each ending in `setEnabled(enable)` computed purely from
flags (`UIAction.java:464-491`). On those paths the action can end up enabled
whenever any selection exists, even when the real state is `INELIGIBLE`. This is
the same residual `ToggleNotationAction` already carries for beam and tie. Do
not add extra subscriptions to chase it.

`setUndoOpNameKey` is dropped from the constructor — see §7.

### 3. State algorithm

`MusicEditOperations.getHairpinActionState()` returns an enum. The model layer
does not know about string keys; `HairpinAction` maps outcome to UI.

```java
public enum HairpinActionState {
    INELIGIBLE,         // both "Add …", disabled — selection cannot host a hairpin
    CAN_ADD,            // both "Add …", enabled
    EXTEND_CRESCENDO,   // crescendo item "Extend Crescendo" enabled;
                        //   diminuendo item "Add Diminuendo", disabled
    EXTEND_DIMINUENDO,  // diminuendo item "Extend Diminuendo" enabled;
                        //   crescendo item "Add Crescendo", disabled
    BLOCKED             // both "Add …", disabled — hairpins present, no extension possible
}
```

Resulting UI state per outcome:

| State | Crescendo item | Diminuendo item |
|-------|----------------|-----------------|
| `INELIGIBLE` | "Add Crescendo", disabled | "Add Diminuendo", disabled |
| `CAN_ADD` | "Add Crescendo", enabled | "Add Diminuendo", enabled |
| `EXTEND_CRESCENDO` | "Extend Crescendo", enabled | "Add Diminuendo", disabled |
| `EXTEND_DIMINUENDO` | "Add Crescendo", disabled | "Extend Diminuendo", enabled |
| `BLOCKED` | "Add Crescendo", disabled | "Add Diminuendo", disabled |

```
Step 1 — Structural eligibility  (begin, end)
─────────────────────────────────────────────
Applies to BOTH add and extend.

  end    : isPitchedNote(end)                       ── never a grace note, never a rest
  begin  : isPitchedNote(begin)
           OR ( isGraceNote(begin) AND isPitchedNote(begin + 1) )   ── grace + its host
  span   : !line.spansStructuralBoundary(begin, end)

  Any check fails → INELIGIBLE

  A grace note may be the START endpoint only. It is an integral part of its
  host note (host = graceIndex + 1). When begin is a grace note, the hairpin
  anchors to the GRACE NOTE ITSELF, not the host, so it covers what the user
  selected. A lone grace note fails: begin == end, and end must be pitched.

  NOTE: there is NO pitched-note-count check here. Count is an ADD-only gate
  and lives in Step 2, because a single note adjacent to a hairpin extends it.


Step 2 — Hairpin relation analysis
──────────────────────────────────
Find every Hairpin that OVERLAPS or is ADJACENT to [begin, end], via the
shared scan in getDynamicsFromSelection (see §4).

ADJACENCY MATTERS: addHairpin uses absorbAdjacent = true, so a same-type
hairpin merely touching an endpoint WILL be absorbed on add. Treating
adjacency as extension is what keeps the menu label honest about what the
model is about to do.

  ┌── no hairpin overlaps or is adjacent ───────────────┬─► count >= 2 ──► CAN_ADD
  │                                                     └─► count <  2 ──► INELIGIBLE
  │       count = #{ i in [begin, end] : isPitchedNote(i) }
  │
  ├── exactly one type present (crescendo), and the ────────────────────► EXTEND_CRESCENDO
  │   selection reaches outside its span (left, right,
  │   or both), and the outside notes touch no other
  │   hairpin                        ── NO count requirement
  │
  ├── same, diminuendo ─────────────────────────────────────────────────► EXTEND_DIMINUENDO
  │
  ├── both a crescendo and a diminuendo are present ────────────────────► BLOCKED
  │
  └── selection lies entirely inside one hairpin ───────────────────────► BLOCKED
      (no extension possible)


Worked examples (no hairpins anywhere near the selection)
─────────────────────────────────────────────────────────
  ┌────────────────────────────────────────────────────────────────┐
  │  ♪gr ♩  ♩  𝄽  ♩       count = 3  ✓   begin = grace ✓  CAN_ADD  │
  │  ♪gr ♩                count = 1  ✗   grace + host is one note  │
  │  ♩                    count = 1  ✗   single note, nothing to   │
  │                                      extend → INELIGIBLE       │
  │  ♩  𝄽  ♪gr            end is a grace note        ✗  Step 1     │
  │  ♩  𝄽  𝄽              end is a rest              ✗  Step 1     │
  │  ♩  ♩  ┃┃ ♩           spansStructuralBoundary    ✗  Step 1     │
  │  ♩  ♩  │  ♩           single barline is fine     ✓  CAN_ADD    │
  └────────────────────────────────────────────────────────────────┘

Single-note extend
──────────────────
  ┌────────────────────────────────────────────────────────────────┐
  │  ♩  ♩  ♩  ♩          selection = the 4th note only            │
  │  └─cresc─┘  ▲                                                  │
  │             └─ adjacent to the crescendo → EXTEND_CRESCENDO    │
  │                                                                │
  │  ♩  ♩  ♩  ♩          same note, no hairpin → INELIGIBLE        │
  └────────────────────────────────────────────────────────────────┘
```

**Extend eligibility** means all three: the selection includes notes inside or
touching the hairpin; it includes notes outside its span; and those outside
notes intersect no other hairpin. Extension may run left, right, or both
directions at once.

### 4. Shared hairpin relation scan

`MusicEditOperations.getDynamicsFromSelection` (`:302-318`) already does a single
pass over `line.getRangeElements()` using `RangeElement.overlaps`. Extend it to
report both overlapping **and adjacent** hairpins in one pass, and have both
`getHairpinActionState()` and `addDynamicsToSelection()` consume it.

Do not call `line.getCrescendos()` / `getDiminuendos()` here — each is a full
scan plus a fresh `ArrayList`, and it would become a third copy of the same loop.

### 5. `Line.spansStructuralBoundary(int begin, int end)`

Returns `true` if any element in `[begin, end]` is a repeat or a barline other
than `SINGLE_BARLINE`. General utility — no range element should span these.

```java
for (var i = begin; i <= end; i++) {
    var type = getElement(i).getType();

    if (type.isRepeat() || (type.isBarLine() && type != ElementType.SINGLE_BARLINE)) {
        return true;
    }
}

return false;
```

`MusicEditOperations.validateEndingRegionContent` (`:420-442`) walks a range with
a similar test but has different semantics — it also requires content, skips
non-content elements, and rejects even single barlines. Leave it alone; the four
overlapping lines do not justify a two-flag shared helper.

### 6. Commands

`AddDynamicsCommand` is kept unchanged — it is already exactly
`{ boolean isCrescendo }`. Do not create an `AddHairpinCommand`.
`RemoveDynamicsCommand` is deleted; deletion is handled by the Delete key path.

### 7. Add / Extend execution

```
HairpinAction.performAction ──post──> AddDynamicsCommand(isCrescendo)
        ▼
ScoreViewController.handleAddDynamics ──► MusicEditOperations.addDynamicsToSelection
        ▼
line.withModification(opName, () -> {                    ← op name per invocation
    line.addCrescendo(new Crescendo(spanAnchor, spanEnd))   ← EXPLICIT span, see below
        └─► addHairpin(absorbAdjacent = true)
              └─► mergeOverlappingSpans — may widen further
    stripPointDynamics(resulting merged range)           ← NEW
        └─► element.removeAttachment(DynamicAttachment)
})
```

**The span is computed explicitly, never degenerate.** For Add, the span is
exactly `[begin, end]`. For Extend, take the union of the selection with every
same-type hairpin the scan reported as overlapping or adjacent:

```java
var spanBegin = begin;
var spanEnd = end;

for (var hairpin : sameTypeHairpins) {
    spanBegin = Math.min(spanBegin, hairpin.getAnchorElementIndex());
    spanEnd = Math.max(spanEnd, hairpin.getEndElementIndex());
}
```

Then `new Crescendo(line.getElement(spanBegin), line.getElement(spanEnd))`.

The naive alternative — construct `new Crescendo(selected, selected)` for a
single-note extend and let `mergeOverlappingSpans` absorb it — would momentarily
create a one-element hairpin, a shape the model never otherwise produces. If the
merge failed to absorb for any reason, that stray hairpin would persist. The
explicit union reaches the same result and is never degenerate at any point.

**Undo op name is per-invocation, not per-action.** One action performs both Add
and Extend, so `setUndoOpNameKey` at construction cannot label it correctly. Pass
the op name into `line.withModification(opName, …)` based on the current
`HairpinActionState`.

**Point dynamics are stripped across the resulting merged range**, not the
selection — a merge can widen the hairpin past what the user selected, and a
point dynamic stranded inside a hairpin breaks the invariant that
`DynamicMarkingAction.updateEnabledState` (`:143`) already relies on. The strip
happens silently inside the same modification bracket, so one undo reverses both.

The reverse direction already works and needs no change: `DynamicMarkingAction`
disables itself when `line.isInHairpinRange(noteIndex)`.

### 8. Hairpin selection

#### Selection state

Add to `LineSelectionState`:

```java
private @Nullable Hairpin selectedHairpin;
```

`Hairpin` is sealed, so `instanceof Crescendo` distinguishes the type — no
companion boolean.

- `selectHairpin(Hairpin hairpin)` — mirrors `selectSlide` (`:120-122`): calls
  `clearDecorationSelections()`, zeroes `selectionBegin/End/Anchor`, sets
  `lineSelected = false`
- `hasHairpinSelection()`
- `getSelectedHairpin()`
- `clearDecorationSelections()` (`:87-89`) — must also null `selectedHairpin`
- `revalidateDecorationSelection()` (`:180-190`) — must clear the selection when
  the selected hairpin is no longer in `line.getRangeElements()`
- `isSelected(LineElement)` — the refactor's unified predicate must return true
  for `selectedHairpin`

`SelectionCoordinator` gains `hasHairpinSelection()` mirroring
`hasSlideSelection()` (`:345-348`), and its
`musicSelectionDidChangeSaveRestoreActionStates` handler (`:971-987`) adds
`hasHairpinSelection()` to the `saveActionStates()` condition. Its
`revalidateDecorationSelection` guard (`:1015`) adds it too.

**No change to `MusicSelectionDidChangeNotification`.** It carries no
per-decoration flags — slide and ending consumers query the live coordinator
(`DeleteAction.java:53-69` calls `selection.hasSlideSelection()` directly).
Hairpins follow the same pattern.

#### Hit testing

The refactor makes `DynamicsRenderer` a `HitTester`. Implement:

```java
@Override
public @Nullable HitResult hitTest(HitTestContext context)
```

- Return `null` immediately when `context.layoutResult() == null` — the click can
  arrive before the first paint, and omitting this guard is an NPE
- For each `Hairpin` in `context.line().getRangeElements()`, take
  `layoutResult.getDecorationLayout(hairpin)`, build
  `Rectangle2D.Double(xSs, layoutYToComponentYSs(ySs, context.middleLineYSs()),
  widthSs, heightSs + marginSs)`, and test `contains`
- Return `new HitResult.Hairpin(hairpin)` on a hit, `null` otherwise
- No tolerance constant, no cached trig, no segment math. The whole hairpin is
  `Hairpin.HAIRPIN_OPENING_HEIGHT_SS` tall, so the bounding box *is* the
  tolerance band

Add a `HitResult.Hairpin(Hairpin hairpin)` variant to
`songscribe.ui.hit.HitResult`, and register the renderer in
`LineSelectionHandler`'s tester list between the slide and ending entries:

```java
hitTesters = List.of(
    context -> ElementHitTest.hitTest(lc, context),   // note heads
    SlideRenderer.getInstance()::hitTest,             // slides
    DynamicsRenderer.getInstance()::hitTest,          // ◄── NEW
    EndingRenderer.getInstance()::hitTest,            // endings
    this::hitTestStaffLine                            // staff-line proximity
);
```

`handlePress` gains a `HitResult.Hairpin` case mirroring the `Slide` case
(`:191-198`): `prepareSelection()` → `lineSelectionState.selectHairpin(h)` →
`scoreView.selectionChanged()`.

#### Action disabling while a hairpin is selected

No new `UIAction` flags. Element-modifying actions carry `REQUIRES_SELECTION` or
`REQUIRES_MULTIPLE_SELECTION`; with only a hairpin selected the element selection
is empty, so they self-disable. `SelectionCoordinator` saves action states on
hairpin selection and restores them when it clears, exactly as it does for slides
and endings.

#### Deletion

`DeleteAction.updateEnabledState()` (`:53-69`) adds `hasHairpinSelection()` to
its disjunction. `ScoreViewController.handleDelete()` gains a hairpin branch
mirroring the slide branch (`:591-613`):

```java
line.withModification(OpNames.deleteHairpinLabel(hairpin), () -> {
    switch (hairpin) {
        case Crescendo c -> line.removeCrescendo(c);
        case Diminuendo d -> line.removeDiminuendo(d);
    }
});
```

Going through `withModification` and the type-specific `remove*` methods is what
makes the deletion undoable — a direct list removal would silently bypass
mutation recording. After deletion the selection is cleared; no note selection is
restored.

### 9. Selection highlight

`DynamicsRenderer.renderSingleHairpin` (`:90-110`) currently hardcodes
`RenderingUtils.ELEMENT_COLOR`. After the refactor there is **no new
`determine*Color` method** — call the shared helper the refactor introduces:

```java
g2.setColor(RenderingUtils.decorationSelectionColor(hairpin, invariants));
```

`renderHairpinsFromLine` (`:119-138`) already iterates
`layoutResult.getDecorationLayoutsByType(Crescendo.class)`, so the hairpin is
`entry.getKey()` — thread it into `renderSingleHairpin` alongside the layout.

No handles, no thickening.

---

## String key ledger

The build runs a dead-key audit — a key with no `Strings.<CONSTANT>` reference
under `src/` fails `./scripts/compile.sh`. Follow this ledger exactly.

| Key | Action |
|---|---|
| `action.dynamics.remove` | **Delete** |
| `action.dynamics.remove.tooltip` | **Delete** |
| `action.edit.op.remove.dynamics` | **Delete** |
| `action.dynamics.crescendo` / `.diminuendo` (+ `.tooltip`) | Keep — the "Add …" labels |
| `action.edit.op.add.crescendo` / `.add.diminuendo` | Keep — undo label for Add |
| `action.edit.op.crescendo` / `.diminuendo` | **Do not delete** — used by `UndoController.java:485-488` and `MutationLabelTest` |
| `menu.notation.dynamics` | Keep unchanged |
| `action.dynamics.crescendo.extend` (+ `.tooltip`) | **Add** |
| `action.dynamics.diminuendo.extend` (+ `.tooltip`) | **Add** |
| `action.edit.op.extend.crescendo` / `.extend.diminuendo` | **Add** |
| `action.edit.op.delete.crescendo` / `.delete.diminuendo` | **Add** |

Keys are alphabetized within their blank-line-separated group in
`src/main/resources/songscribe/strings.properties`. Never edit the generated
`Strings.java`.

---

## What is removed

- `ui/action/RemoveDynamicsAction.java`
- `message/command/RemoveDynamicsCommand.java`
- `ScoreViewController.handleRemoveDynamics` and
  `ScoreViewController.canRemoveDynamicsFromSelection`
- `MusicEditOperations.removeDynamicsFromSelection` and
  `canRemoveDynamicsFromSelection`
- `MusicEditOperations.canAddDynamicsToSelection` and
  `ScoreViewController.canAddDynamicsToSelection` — subsumed by
  `getHairpinActionState()`
- `Actions.REMOVE_DYNAMICS_ACTION`
- `src/test/java/songscribe/ui/action/RemoveDynamicsActionTest.java`

No `HairpinActionController` is created. No `register(...)` call is added to
`NotationMenu`.

---

## New / modified files

| File | Change |
|------|--------|
| `ui/action/AddDynamicsAction.java` → `HairpinAction.java` | Rename; `REQUIRES_MULTIPLE_SELECTION` → `REQUIRES_SELECTION`; drop `setUndoOpNameKey`; override the three handlers and defer to `getHairpinActionState()`; set `Action.NAME` per state |
| `ui/action/Actions.java` | `ADD_*_ACTION` → `HAIRPIN_CRESCENDO_ACTION` / `HAIRPIN_DIMINUENDO_ACTION`; remove `REMOVE_DYNAMICS_ACTION` |
| `ui/action/DeleteAction.java` | Add `hasHairpinSelection()` to the enabled disjunction |
| `ui/MusicEditOperations.java` | `HairpinActionState` + `getHairpinActionState()`; adjacency in `getDynamicsFromSelection`; explicit-span extend; point-dynamics strip; remove the `*RemoveDynamics*` and `canAddDynamicsToSelection` methods |
| `dom/Line.java` | Add `spansStructuralBoundary(begin, end)` |
| `undo/OpNames.java` | Add `deleteHairpinLabel(Hairpin)` |
| `ui/selection/LineSelectionState.java` | `selectedHairpin` field + methods; extend `clearDecorationSelections`, `revalidateDecorationSelection`, and `isSelected` |
| `ui/selection/SelectionCoordinator.java` | `hasHairpinSelection()`; save/restore and revalidate branches |
| `ui/hit/HitResult.java` | Add `Hairpin` variant |
| `ui/component/score/LineSelectionHandler.java` | `DynamicsRenderer` entry in the tester list; `handlePress` case |
| `ui/renderer/DynamicsRenderer.java` | Implement `HitTester`; use `decorationSelectionColor` in `renderSingleHairpin` |
| `ui/component/ScoreViewController.java` | Hairpin branch in `handleDelete`; `getHairpinActionState()` passthrough; remove `handleRemoveDynamics` and both `can*DynamicsFromSelection` |
| `strings.properties` | Per the ledger above |
| `ui/menu/NotationMenu.java` | Three hairpin items → two; drop the `REMOVE_DYNAMICS_ACTION` import and line |

---

## Phases

Each phase ends with `./scripts/compile.sh`. Fix all failures before starting the
next phase. At the end of each phase run
`git stash push --include-untracked -m "Finished phase N" && git stash apply`.

**No new tests are written until Phase 10 has been signed off.** Behavior
changes discovered during manual verification would otherwise force the tests to
be rewritten. The only test edits allowed in Phases 1–9 are deletions and
reference fixes the compiler forces.

### Phase 1 — `Line.spansStructuralBoundary`

1. Add `spansStructuralBoundary(int begin, int end)` to `dom/Line.java` per §5

### Phase 2 — Hairpin relation scan and Step 1

1. Extend `MusicEditOperations.getDynamicsFromSelection` to report both
   overlapping and adjacent hairpins in one pass per §4
2. Add the `HairpinActionState` enum per §3
3. Add `MusicEditOperations.getHairpinActionState()` implementing Step 1 only
   (structural eligibility), returning `INELIGIBLE` or `CAN_ADD`
4. Add a `getHairpinActionState()` passthrough to `ScoreViewController`

### Phase 3 — State algorithm Step 2

1. Implement Step 2 in `getHairpinActionState()` per §3, treating adjacency as
   extension and applying the `count >= 2` gate **only** on the add branch
2. Copy the Step 1 / Step 2 ASCII decision tree from §3 into a Javadoc comment on
   `getHairpinActionState()` — it is the least obvious logic in the change

### Phase 4 — Add / Extend execution

1. Add `stripPointDynamics(Line, int begin, int end)` to `MusicEditOperations`
   using `findAttachment(DynamicAttachment.class)` / `removeAttachment`
2. Change `addDynamicsToSelection` to compute the explicit union span per §7,
   pass a per-invocation op name into `line.withModification(opName, …)`, and
   call `stripPointDynamics` across the **resulting merged range** inside the
   same bracket
3. Add the extend and delete op-name keys to `strings.properties` per the ledger

### Phase 5 — `HairpinAction` and menu

1. Rename `AddDynamicsAction` → `HairpinAction` with `jet_brains_rename`
2. Swap `REQUIRES_MULTIPLE_SELECTION` for `REQUIRES_SELECTION`; drop the
   constructor `setUndoOpNameKey`
3. Override `musicSelectionDidChange` / `songDidChange` / `documentDidLoad` per
   §2, deferring to `getHairpinActionState()` and setting enabled + `Action.NAME`
4. Rename `Actions.ADD_CRESCENDO_ACTION` / `ADD_DIMINUENDO_ACTION` →
   `HAIRPIN_CRESCENDO_ACTION` / `HAIRPIN_DIMINUENDO_ACTION` with
   `jet_brains_rename` (updates the e2e call sites atomically)
5. Add the extend label and tooltip keys per the ledger
6. Update `NotationMenu.createDynamicsMenu()` — two hairpin items; keep the
   separator and `DYNAMIC_MARKING_ACTION_GROUP`

### Phase 6 — Remove the old remove path

1. Delete `RemoveDynamicsAction.java`, `RemoveDynamicsCommand.java`,
   `RemoveDynamicsActionTest.java`, and `Actions.REMOVE_DYNAMICS_ACTION` using
   `jet_brains_safe_delete`
2. Remove `ScoreViewController.handleRemoveDynamics` and
   `canRemoveDynamicsFromSelection`
3. Remove `MusicEditOperations.removeDynamicsFromSelection`,
   `canRemoveDynamicsFromSelection`, and `canAddDynamicsToSelection`
4. Compiler-forced test fixes only: drop `testHandleRemoveDynamicsEmitsRemovals`
   from `ScoreViewControllerCommandHandlerTest` (`:318`); fix the dead references
   in `MusicEditOperationsMutationTest` (`:510, 532, 542, 554, 566, 582`) and
   `MusicEditOperationsNullStateTest` (`:227, 238`); retarget
   `AddDynamicsActionTest` → `HairpinActionTest` only far enough to compile
5. Delete the three dead string keys per the ledger, keeping
   `action.edit.op.crescendo` / `.diminuendo`
6. Compile (the dead-key audit must pass) and run the full unit suite green

### Phase 7 — Hairpin selection state

1. Add `selectedHairpin` plus `selectHairpin` / `hasHairpinSelection` /
   `getSelectedHairpin` to `LineSelectionState` per §8
2. Add `selectedHairpin` to `clearDecorationSelections()`
3. Extend `revalidateDecorationSelection()` to clear when the hairpin is no
   longer in `line.getRangeElements()`
4. Extend `LineSelectionState.isSelected(LineElement)` to cover `selectedHairpin`
5. Add `SelectionCoordinator.hasHairpinSelection()` and add it to the
   `saveActionStates()` and revalidate conditions

### Phase 8 — Hit testing and click handling

1. Make `DynamicsRenderer` implement `HitTester` per §8
2. Add the `HitResult.Hairpin(Hairpin hairpin)` variant
3. Add the `DynamicsRenderer` entry to `LineSelectionHandler`'s tester list,
   between slides and endings
4. Add the `HitResult.Hairpin` case to `handlePress`

### Phase 9 — Delete and selection rendering

1. Add `OpNames.deleteHairpinLabel(Hairpin)` mirroring `deleteSlideLabel`
   (`:164`)
2. Add the hairpin branch to `ScoreViewController.handleDelete()` per §8, inside
   `line.withModification`
3. Add `hasHairpinSelection()` to `DeleteAction.updateEnabledState()`
4. Thread the hairpin into `renderSingleHairpin` and colour it via
   `RenderingUtils.decorationSelectionColor` per §9
5. Compile and run the full unit suite green

### Phase 10 — Manual verification (user)

Blocks all test writing. Walk through, in the running app:

1. Menu labels and enabled state across all five `HairpinActionState` outcomes,
   confirming nothing is ever hidden
2. Add a hairpin over a multi-note selection
3. Extend a hairpin left, right, both, and **by a single note** in each direction
4. Point dynamics disappear across the whole merged range, and one undo reverses
   both the hairpin and the strip
5. Click a rendered hairpin — it highlights; note/line/slide/ending selections
   and the hairpin selection are mutually exclusive
6. Delete removes the selected hairpin and the toolbar returns to normal
   afterwards
7. Undo/redo of add, extend, and delete, with correct undo labels

### Phase 11 — Unit tests (blocked by Phase 10)

1. `LineSpansStructuralBoundaryTest`: single barline allowed; double barline,
   final double barline, `REPEAT_LEFT`, `REPEAT_RIGHT`, `REPEAT_LEFT_RIGHT` each
   rejected; `begin == end`; boundary element at exactly `begin` and exactly
   `end` (guards the off-by-one that would let a hairpin cross a final barline)
2. `HairpinActionStateTest` Step 1: each structural failure cause; the pass case;
   grace note at begin with pitched host; grace note at begin whose host is not
   pitched; grace note at end rejected; lone grace note rejected; rests between
   two pitched notes accepted
3. `HairpinActionStateTest` Step 2: `CAN_ADD`; single note with no hairpin →
   `INELIGIBLE`; single note adjacent to a same-type hairpin → `EXTEND_*` on each
   side; `EXTEND_CRESCENDO` extending left, right, and both; `EXTEND_DIMINUENDO`
   likewise; extension blocked because the outside notes touch another hairpin;
   `BLOCKED` when both types overlap; `BLOCKED` when the selection is entirely
   inside one hairpin
4. `MusicEditOperationsMutationTest`: point dynamics stripped across a merged
   range wider than the selection; one undo reverses both the add and the strip;
   a single-note extend never produces a one-element hairpin
5. `MutationLabelTest`: Add and Extend produce different undo labels
6. `HairpinActionTest` (rewritten from `AddDynamicsActionTest`): the four former
   `canAddDynamicsToSelection` tests rebound to `getHairpinActionState`, plus
   label and enabled state per state
7. `LineSelectionStateTest`: selecting a hairpin clears note, line, slide, and
   ending selection, **and** each of those clears the hairpin selection (both
   directions — missing one lets a note and a hairpin be selected at once, and
   Delete would remove both); revalidation clears a hairpin removed from the line
8. `DynamicsRendererTest`: click inside the wedge bounding box hits; click
   outside misses; `layoutResult == null` returns `null` rather than throwing;
   crescendo and diminuendo both hit; a selected hairpin renders in the selection
   color
9. `DeleteActionTest`: Delete enabled with only a hairpin selected
10. `MutationReplayerRoundTripTest`: hairpin deletion survives an undo/redo round
    trip

Run `./scripts/test.sh unit` green.

### Phase 12 — e2e (blocked by Phase 11; requires user approval before running)

Four scenarios, covering only what unit tests cannot reach:

1. **Click to select** — click a rendered hairpin, assert it is selected and
   drawn in the selection color
2. **Delete** — with a hairpin selected, press Delete; assert the hairpin is gone
   and the selection is cleared
3. **Action save/restore** — assert note-modifying actions are disabled while the
   hairpin is selected and **restored** after the selection clears (a
   save-without-restore leaves the toolbar permanently stuck and is invisible to
   unit tests)
4. **Menu round trip** — select notes, add a crescendo, extend it via the
   relabelled item, assert the span widened and the diminuendo item was still
   visible but disabled during the extend state

Then update `LineHairpinMergeTest`'s class Javadoc, which currently claims the
e2e `DynamicsMarkingTest` "must not be touched" — no longer true after the
`Actions` rename in Phase 5.
