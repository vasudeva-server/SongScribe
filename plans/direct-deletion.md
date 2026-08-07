# Direct Deletion of Selected Notations (#682)
Pressing Backspace / Delete while a single notation object is directly selected removes it, reusing the existing undo-tracked mutation paths so no new undo support is added.

The whole feature converges on one method: `ScoreViewController.deleteSelectedTarget(Line line, HitTarget target)` in `src/main/java/songscribe/ui/component/ScoreViewController.java`. It already handles `HitTarget.Slide`, `HitTarget.Ending` and `HitTarget.Hairpin`; every other variant currently falls into a deliberate silent no-op arm. This plan fills in the remaining arms: `Tie`, `Beam`, `Tuplet`, `Trill`, `Articulation`, `Attachment`, `Accidental`.

`HitTarget.Element` stays a no-op (a note is selected as an index range and deleted by `handleDelete`'s range branch), `HitTarget.GraceGlissando` stays a no-op (not selectable), and `HitTarget.StaffLine` / `HitTarget.Lyric` keep their own branches in `handleDelete`.
## Dispatch Map
Reproduce a condensed form of this as an ASCII comment above `deleteSelectedTarget` in Phase 6, replacing the prose paragraph that currently explains why each kind is a no-op.

```
Backspace / Delete
        │
        ▼
   handleDelete()
        │
        ├─ HitTarget.Lyric ─────────────► modifyElement(LYRIC) ──┐
        │                                                        │
        ├─ range != null ───────────────► deleteElementRange()  ─┤
        │                                                        │
        ├─ selectedTarget != null ──────► deleteSelectedTarget() ─┤
        │        │                                               │
        │        ├── Slide      ─► modifyElement(SLIDE)          │   existing
        │        ├── Ending     ─► removeSpan                    │   existing
        │        ├── Hairpin    ─► removeCrescendo/Diminuendo    │   existing
        │        │ ─────────────────────────────────────────     │
        │        ├── Tie        ─► removeTie      ── both lines  │   NEW
        │        ├── Beam       ─► removeBeaming                 │   NEW
        │        ├── Tuplet     ─► removeTuplet                  │   NEW
        │        ├── Trill      ─► removeSpan  (explicit label)  │   NEW
        │        ├── Articulatn ─► modifyElement(ARTICULATION)   │   NEW
        │        ├── Attachment ─► deleteAttachment() ──┐        │   NEW
        │        │                   ├ Fermata     FERMATA       │
        │        │                   ├ Dynamic     DYNAMIC_ATT   │
        │        │                   ├ Annotation  ANNOTATION    │
        │        │                   ├ BeatChange  BEAT_CHANGE   │
        │        │                   └ TempoChange TEMPO_CHANGE  │  veto
        │        └── Accidental ─► SelectionActionApplier.apply ─┤   NEW
        │                          (restatements, reconciliation,│
        │                           fit gate — can refuse)       │
        ├─ canDeleteLine() ─────────────► song.removeLine()     ─┤
        │                                                        ▼
        └────────────────────────────► restoreSelectedActionStates()
                                        score.deselect()
```
## Facts Established Before Implementation
These were verified against the code. Do not re-derive them, and do not write defenses against the failure modes they rule out.

- `DeleteAction` **needs no change.** `DeleteAction.updateScoreEnabledState` (`src/main/java/songscribe/ui/action/DeleteAction.java`) already returns true whenever `hasDecorationSelection()` is true, so the keystrokes are live for every target below.
  
- `handleDelete`**'s tail is unconditional.** It always runs `selectionCoordinator.getActionReflector().restoreSelectedActionStates()` and `score.deselect()` after `deleteSelectedTarget` returns, and `SelectionCoordinator.revalidateDecorationSelection` handles the stale-selection case. No selection cleanup belongs in any delete arm, including the refused tempo-change case.
  
- **An empty modification bracket costs nothing.** `ModificationSession.endModification` posts no notification and does not set `modified` when no mutation accumulated, so wrapping an internally-guarded span removal in `withModification` cannot leave a phantom undo step. `Line.modifyElement`, by contrast, records **unconditionally** — it clones before, runs the mutator, clones after, and appends the record whether or not anything changed. Every guard that can refuse must therefore sit outside `line.modifyElement`.
  
- `ElementField.DURATION_AFFECTING` **is** `EnumSet.of(DOT_COUNT)`**.** Nesting `Song.withBeatDefiningEditOn` inside `modifyElement(BEAT_CHANGE, …)` does not double-fire tuplet removal; it is exactly what the dialog does today.
  
- `MetronomeAttachment` **is abstract.** The five concrete attachment kinds are `FermataAttachment`, `DynamicAttachment`, `AnnotationAttachment`, `TempoChangeAttachment` and `BeatChangeAttachment`. No bare metronome attachment can exist.
  
- `AccidentalAction.applyToElement(element, false)` **is** `element.setAccidental(null)`**.** The action's own `accidental` field is read only when `selected` is true, and `decideChanges` gates on `appliesTo`, never `matchesElement`. Every action in `Actions.ACCIDENTAL_ACTION_GROUP` behaves identically for a removal.
  
- `action.edit.op.span = Ending`**.** `Line.removeSpan` emits a generic `SpanRemoval` whose unlabeled fallback op-name resolves to the literal text `Ending`, so a trill deleted without an explicit label would make the undo menu read "Undo Ending".
  
## Branch Coordination
This branch lands **before** `717-manual-slides` (#717), which converts the glissando and fall actions into selection-based toggles and deletes the mouse-driven slide tool. The two branches share only two files, and they edit disjoint regions of each:

- `src/main/java/songscribe/ui/component/ScoreViewController.java` — this plan adds arms inside `deleteSelectedTarget`; #717 adds `canToggleGlissando` / `canToggleFall` accessors and four `@Handler` methods elsewhere in the class.
  
- `src/main/resources/songscribe/strings.properties` — different keys in both, both inserted in alphabetized position.
  

Two constraints follow. Both cost nothing, because this plan already respects them — they are written down so a phase does not drift into them by accident:

- Leave the existing `case HitTarget.Slide` arm of `deleteSelectedTarget` exactly as it is. Selecting a slide and deleting it must keep working, and #717 relies on this arm surviving untouched — it removes the slide _tool_, not slide selection or deletion.
  
- Do not touch `src/main/java/songscribe/ui/selection/ActionReflector.java`. #717 deletes `reflectSlideSelection` from it. Nothing here needs that method; `restoreSelectedActionStates()`, which `handleDelete` does rely on, is a different method that neither branch changes.
  
## Status Dashboard
| Phase | Description | Status | Sub-plan |
| --- | --- | --- | --- |
| 1   | [Seal Attachment and Add Op-Name Labels](#-phase-1-seal-attachment-and-add-op-name-labels) | ✅ Complete | —   |
| 2   | [Tempo Orphan Query](#-phase-2-tempo-orphan-query) | ✅ Complete | —   |
| 3   | [Attachment Removal Extraction](#-phase-3-attachment-removal-extraction) | ✅ Complete | —   |
| 4   | [Tempo Removal Veto](#-phase-4-tempo-removal-veto) | ✅ Complete | —   |
| 5   | [Single-Element Action Application](#-phase-5-single-element-action-application) | ✅ Complete | —   |
| 6   | [Delete Arms](#-phase-6-delete-arms) | ✅ Complete | —   |
| 7   | [Test Split and Span Delete Tests](#-phase-7-test-split-and-span-delete-tests) | ✅ Complete | —   |
| 8   | [Articulation and Attachment Delete Tests](#-phase-8-articulation-and-attachment-delete-tests) | ✅ Complete | —   |
| 9   | [Accidental Tests and Op-Name Assertions](#-phase-9-accidental-tests-and-op-name-assertions) | ✅ Complete | —   |
| 10  | [Tempo Orphan Tests](#-phase-10-tempo-orphan-tests) | ✅ Complete | —   |
| 11  | [Manual UI Verification](#-phase-11-manual-ui-verification) | ✅ Complete | —   |
| 12  | [Verification Follow-ups](#-phase-12-verification-follow-ups) | ✅ Complete | —   |

* * *
## ✅ Phase 1: Seal Attachment and Add Op-Name Labels
**Status:** Complete  
**BlockedBy:** —  
**Files:** src/main/java/songscribe/dom/Attachment.java, src/main/java/songscribe/dom/MetronomeAttachment.java, src/main/java/songscribe/dom/FermataAttachment.java, src/main/java/songscribe/dom/DynamicAttachment.java, src/main/java/songscribe/dom/AnnotationAttachment.java, src/main/java/songscribe/dom/TempoChangeAttachment.java, src/main/java/songscribe/dom/BeatChangeAttachment.java, src/main/java/songscribe/hit/HitTarget.java, src/main/resources/songscribe/strings.properties, src/main/java/songscribe/undo/OpNames.java  
**Recommended model/effort:** Sonnet 4.6, low — mechanical sealing plus additive string keys and pure label functions matching an established pattern in the same file
### Context
`src/main/java/songscribe/undo/OpNames.java` is the single home for assembling context-dependent undo op-names. It already has `deleteSlideLabel(StaffElement.Slide)`, `deleteHairpinLabel(Hairpin)`, `deleteEndingLabel()`, `deleteLineLabel()` and `deleteLabel(List<ElementType>)`. Every method is a pure function that resolves a `Strings.*` constant. Add the new label methods in that same style, next to the existing `delete*Label` methods.

Sealing `Attachment` comes first because it is what lets `deleteAttachmentLabel` — and `deleteAttachment` in Phase 6 — be a compile-time exhaustive `switch` with no `default` arm. That is the same guarantee `deleteSelectedTarget` already relies on for `HitTarget`: adding a kind must fail to compile at every site that must answer for it, rather than silently answering "nothing". Every subclass lives in `songscribe.dom` alongside the base, so sealing is a `permits` clause plus one modifier per subclass.

Read `.agents/guides/strings.md` before touching `strings.properties`. Relevant points: keys are validated against `[a-z][a-z0-9]*(\.[a-z][a-z0-9]*)*`, one English word per dot segment, groups are separated by blank lines and keys are **alphabetized within each group** — insert each new key in sorted position, not at the end. The constant name is the key uppercased with dots as underscores (`action.edit.op.delete.tie` → `Strings.ACTION_EDIT_OP_DELETE_TIE`). The build's dead-key audit fails unless the literal text `Strings.<CONSTANT>` appears somewhere under `src/` — the OpNames methods added in this phase satisfy that for every key below.

Do **not** touch the existing `action.edit.op.remove.annotation`, `action.edit.op.remove.beat.change`, `action.edit.op.remove.tempo.change` or `action.edit.op.remove.tuplet` keys. Those name the attachment dialogs' _Remove_ button and stay as they are; the Delete key gets its own uniform `Delete X` wording.
### Tasks
1. Seal the attachment hierarchy in `songscribe.dom`:
  
  - `Attachment` becomes `public abstract sealed class Attachment extends LineElement permits FermataAttachment, DynamicAttachment, AnnotationAttachment, MetronomeAttachment`.
    
  - `MetronomeAttachment` becomes `sealed … permits TempoChangeAttachment, BeatChangeAttachment`.
    
  - The five concrete subclasses become `final` if they are not already.
    
  
  Existing `switch` statements over `Attachment` that carry a `default` arm keep compiling unchanged; do not go hunting for them.
  
2. Correct the now-inaccurate Javadoc on `HitTarget.Attachment` (`src/main/java/songscribe/hit/HitTarget.java`), which lists "metronome mark" as a selectable kind. `MetronomeAttachment` is abstract, so no bare instance exists — the list is fermata, dynamic, tempo change, beat change and annotation.
  
3. In `src/main/resources/songscribe/strings.properties`, add these keys in alphabetized position within the existing `action.edit.op.*` group (which already contains `action.edit.op.delete.barline` … `action.edit.op.delete.rests`). All values are ASCII, so ordinary `Edit` calls work — no python3 workaround needed:
  
  ```properties
  action.edit.op.delete.accent = Delete Accent
  action.edit.op.delete.accidental = Delete Accidental
  action.edit.op.delete.annotation = Delete Annotation
  action.edit.op.delete.beam = Delete Beam
  action.edit.op.delete.beat.change = Delete Beat Change
  action.edit.op.delete.dynamic = Delete Dynamic
  action.edit.op.delete.fermata = Delete Fermata
  action.edit.op.delete.staccato = Delete Staccato
  action.edit.op.delete.tempo.change = Delete Tempo Change
  action.edit.op.delete.tie = Delete Tie
  action.edit.op.delete.trill = Delete Trill
  action.edit.op.delete.tuplet = Delete Tuplet
  ```
  
4. Add these no-argument methods to `OpNames`, each a one-line `Strings.get(...)` mirroring `deleteEndingLabel()`: `deleteTieLabel()`, `deleteBeamLabel()`, `deleteTupletLabel()`, `deleteTrillLabel()`, `deleteAccidentalLabel()`.
  
5. Add `public static String deleteArticulationLabel(ArticulationType type)` (`songscribe.dom.ArticulationType` has exactly two constants, `STACCATO` and `ACCENT`). Use an exhaustive `switch` expression over the enum — no `default` arm — so a new articulation type fails to compile here. Returns `ACTION_EDIT_OP_DELETE_STACCATO` / `ACTION_EDIT_OP_DELETE_ACCENT`.
  
6. Add `public static String deleteAttachmentLabel(Attachment attachment)` (`songscribe.dom.Attachment`). Use a pattern `switch` expression over the now-sealed hierarchy, with **no** `default` **arm**, mapping `FermataAttachment`, `DynamicAttachment`, `AnnotationAttachment`, `TempoChangeAttachment` and `BeatChangeAttachment` to `ACTION_EDIT_OP_DELETE_FERMATA`, `ACTION_EDIT_OP_DELETE_DYNAMIC`, `ACTION_EDIT_OP_DELETE_ANNOTATION`, `ACTION_EDIT_OP_DELETE_TEMPO_CHANGE` and `ACTION_EDIT_OP_DELETE_BEAT_CHANGE`. Javadoc it with a line saying the switch is exhaustive by sealing, so a new attachment kind fails to compile here.
  
7. Run `./scripts/compile.sh` and confirm SUCCESS. The build regenerates `build/generated-sources/songscribe/Strings.java` and runs the key validator and the dead-key audit — both must pass. Never edit the generated file.
  

* * *
## ✅ Phase 2: Tempo Orphan Query
**Status:** Complete  
**BlockedBy:** —  
**Files:** src/main/java/songscribe/dom/TempoResolver.java, src/main/java/songscribe/dom/Song.java  
**Recommended model/effort:** Sonnet 4.6, medium — one new pure query plus a delegating accessor, mirroring an existing method in the same class
### Context
`Song` owns a `TempoResolver` (`src/main/java/songscribe/dom/TempoResolver.java`) that answers "what is in effect here?" by walking backward through the score. `Song.hasAnyTempoChange()` already delegates to `TempoResolver.hasAnyTempoChange()`, which loops every line and every element looking for a `TempoChangeAttachment`. Copy that delegation shape exactly.

This phase adds the query consumed by Phase 4: **removing a tempo change is refused when there is a tempo change later in the song and no tempo change before the one being removed.**

The reason is musical, not mechanical. A tempo change is an instruction to change _from_ an established tempo _to_ a new one. Strip the tempo that established the reference and the surviving mark instructs a change from nothing — it is not notation that means something slightly different, it is notation that does not parse. Playback will still resolve a number for the opening region, because `Song.getEffectiveTempo()` substitutes a default when the song-level tempo is null, but that is the playback engine papering over a score that no longer says anything coherent. Do not let that fallback talk you out of the guard.

The refusal covers the dialog Remove button and the Delete key only; the element-range delete and line-delete paths reach the same state unguarded. That is tracked in issue #731 and is explicitly out of scope here — do not add guards to `deleteElementRange` or the `canDeleteLine` branch.
### Tasks
1. Add a public method to `TempoResolver`: `public boolean removalWouldOrphanLaterTempoChange(int lineIndex, int elementIndex)`. It returns true when **both** hold, scanning the song in document order (line by line, element by element within each line, using `song.getLines()` / `Line.elementCount()` / `Line.getElement(i)` and `StaffElement.findAttachment(TempoChangeAttachment.class)`):
  
  - no element **strictly before** `(lineIndex, elementIndex)` carries a `TempoChangeAttachment`, and
    
  - at least one element **strictly after** `(lineIndex, elementIndex)` carries one.
    
  
  The element at `(lineIndex, elementIndex)` itself is excluded from both scans — it is the one being removed. Do not reuse the private `walkBackFrom` helper; it starts _at_ the given index rather than before it, and folding an off-by-one into it would change what `getTempoAt`/`resolveBeatAt` mean. Write a forward scan with a flag that flips when the walk passes the given position, and exit early in both directions — return false the moment a tempo change is found before the position, and true the moment one is found after it. The two early exits _are_ the two conditions, so they document the method as well as shorten it.
  
2. Javadoc the new method with the musical reason: a tempo change notates a change _from_ an established tempo, so the first one in the song is what gives every later change something to change from. Removing it while a later change survives leaves that change with no reference — a marking that instructs a change from nothing. State it in those terms; do not justify the method by what playback resolves, which is a different layer and would read as though the guard were about a wrong number rather than incoherent notation.
  
3. Add a public delegate on `Song` (`src/main/java/songscribe/dom/Song.java`), placed next to the existing `clearTempoIfOrphaned`:
  
  ```java
  public boolean wouldOrphanLaterTempoChange(StaffElement element)
  ```
  
  It resolves the element's position — `element.getParentLine()`, then `indexOfLine(line)` and `line.getElementIndex(element)` — and returns false when the element is in no line or its index is negative (an element in no line orphans nothing), otherwise delegates to `tempoResolver.removalWouldOrphanLaterTempoChange(lineIndex, elementIndex)`. Match the existing `clearTempoIfOrphaned` null-guard style in the same class.
  
4. Run `./scripts/compile.sh` and confirm SUCCESS.
  

* * *
## ✅ Phase 3: Attachment Removal Extraction
**Status:** Complete  
**BlockedBy:** —  
**Files:** src/main/java/songscribe/dom/AttachmentRemoval.java, src/main/java/songscribe/ui/dialog/TempoChangeDialog.java, src/main/java/songscribe/ui/dialog/BeatChangeDialog.java, src/main/java/songscribe/ui/dialog/AnnotationDialog.java  
**Recommended model/effort:** Sonnet 4.6, medium — three verbatim body moves into a new class plus three one-line delegates
### Context
The three attachment kinds with dialogs each own their removal logic as a `protected void clearChange(StaffElement)` override of `AttachmentDialog` (`src/main/java/songscribe/ui/dialog/AttachmentDialog.java`). Phase 6 needs to invoke that same logic from the Delete key, where no dialog instance exists.

The logic itself is pure document-model behavior — `Song.withBeatDefiningEditOn`, `element.removeAttachment`, `Song.clearTempoIfOrphaned` — with nothing dialog-specific in it, so it moves to `songscribe.dom` rather than becoming statics on the dialog classes. A keystroke handler calling `TempoChangeDialog.removeTempoChange(element)` would read as "the Delete key opens a dialog", and it would point `songscribe.ui.component` at `songscribe.ui.dialog` for behavior that belongs to neither.

`TempoChangeDialog.clearChange` currently reads:

```java
var attachment = element.findAttachment(TempoChangeAttachment.class);

Song.withBeatDefiningEditOn(element, () -> {
    if (attachment != null) {
        element.removeAttachment(attachment);
    }

    var line = element.getParentLine();

    // An element in no line cannot orphan the song-level tempo.
    if (line == null) {
        return;
    }

    line.getSong().clearTempoIfOrphaned(element);
});
```

`BeatChangeDialog.clearChange` wraps `element.removeAttachment(attachment)` in `Song.withBeatDefiningEditOn(element, ...)` after a null guard. `AnnotationDialog.clearChange` is a plain null-guarded `element.removeAttachment(existing)`. The `Song.withBeatDefiningEditOn` chokepoint is load-bearing for tempo and beat changes — it is what invalidates and reports tuplets the new beat forces out — so it must survive the move verbatim.

`TempoChangeDialogTest`, `BeatChangeDialogTest` and `AnnotationDialogTest` all exist and assert on `clearChange`'s effects, including `verify(song).clearTempoIfOrphaned(element)`. A verbatim move plus delegation keeps them green; if any of them fails, the move was not verbatim.
### Tasks
1. Create `src/main/java/songscribe/dom/AttachmentRemoval.java` — a `public final class` with a private constructor, in the style of the other utility classes in the package. Class Javadoc: it holds the tracked-removal bodies for the attachment kinds that have a dialog, so the dialog Remove button and the Delete key run identical logic. Note that none of these methods opens a modification bracket — the caller owns the bracket, because `Line.modifyElement` records unconditionally and only the caller knows whether a refusal is still possible.
  
2. Add `public static void removeTempoChange(StaffElement element)` holding `TempoChangeDialog.clearChange`'s body verbatim, including the `Song.withBeatDefiningEditOn` wrapper, the null guard and the `clearTempoIfOrphaned` call. Move the existing Javadoc from the override onto it.
  
3. Add `public static void removeBeatChange(StaffElement element)` and `public static void removeAnnotation(StaffElement element)` holding their respective `clearChange` bodies verbatim.
  
4. Reduce all three `clearChange` overrides to one-line delegates to the new statics.
  
5. Run `./scripts/compile.sh` and confirm SUCCESS, then run `./scripts/test.sh TempoChangeDialogTest BeatChangeDialogTest AnnotationDialogTest` and confirm green.
  

* * *
## ✅ Phase 4: Tempo Removal Veto
**Status:** Complete  
**BlockedBy:** 2, 3  
**Files:** src/main/resources/songscribe/strings.properties, src/main/java/songscribe/ui/TempoChangeConfirms.java, src/main/java/songscribe/ui/dialog/AttachmentDialog.java, src/main/java/songscribe/ui/dialog/TempoChangeDialog.java, src/test/java/songscribe/ui/dialog/AttachmentDialogTest.java  
**Recommended model/effort:** Opus 4.8, high — introduces a veto hook into a dialog base class's commit lifecycle and must keep the guard outside the modification bracket
### Context
The Remove-button listener in `AttachmentDialog`'s constructor is:

```java
var element = selectedElement;
var line = selectedLine;

if (element == null || line == null) {
    throw new IllegalStateException("no element selected");
}

var elementIndex = line.getElementIndex(element);
line.withModification(opLabel(AttachmentOp.REMOVE), () -> line.modifyElement(
    elementIndex, getElementField(), () -> clearChange(element)));
setVisible(false);
```

`Line.modifyElement` records an `ElementModification` **unconditionally**, so a removal that refuses to proceed must refuse _before_ the bracket opens or it leaves a no-op entry in the undo history. That is the entire reason the guard is a separate veto hook rather than an early return inside `clearChange`, and it is why the hook's placement is worth a test of its own: a veto placed one line too late still refuses, still warns, still leaves the score untouched — and the only symptom is a phantom undo step.

The confirm lives in `songscribe.ui` rather than on a dialog class or in `songscribe.dom`: it shows UI, so it cannot go in the model, and both callers are outside the dialog package. `songscribe.ui.EndingConfirms` is the established precedent for "a static that asks the user about a document edit", including its parent-`Component` parameter. Take the parent as a parameter for the same reason `EndingConfirms.confirmInvalidation(score)` does — on the Remove-button path the warning is raised while the modal `TempoChangeDialog` is on screen, so parenting it on `MainFrame` can render it behind the dialog.

Read `.agents/guides/option-dialogs.md` and `.agents/guides/strings.md` before writing the alert. All `JOptionPane` use goes through `songscribe.ui.OptionDialogs`, and its message-style methods take `Strings` **keys**, not literal text.
### Tasks
1. In `src/main/resources/songscribe/strings.properties`, add two keys in alphabetized position within the existing `alert.*` group (values are ASCII, so ordinary `Edit` calls work):
  
  ```properties
  alert.tempo.change.orphaned = There is a tempo change later in the song which would be orphaned, so this tempo cannot be removed.
  alert.title.tempo.change.orphaned = Cannot Remove Tempo Change
  ```
  
  Note the `alert.title.*` key sorts into the run of `alert.title.…` keys, not next to `alert.tempo.change.orphaned`.
  
2. Create `src/main/java/songscribe/ui/TempoChangeConfirms.java` — a `public final class` with a private constructor, mirroring `EndingConfirms`. Add:
  
  ```java
  public static boolean confirmRemoveTempoChange(@Nullable Component parent, StaffElement element)
  ```
  
  It returns true when `element.getParentLine()` is null (nothing to orphan) or when `line.getSong().wouldOrphanLaterTempoChange(element)` (Phase 2) is false. Otherwise it calls `OptionDialogs.showWarningMessage(parent, Strings.ALERT_TITLE_TEMPO_CHANGE_ORPHANED, Strings.ALERT_TEMPO_CHANGE_ORPHANED)` and returns false. Javadoc that it both decides and explains — the caller never has to show a message of its own.
  
3. In `AttachmentDialog`, add a veto hook:
  
  ```java
  /**
   * Whether the Remove button may proceed. Checked before any modification bracket opens,
   * because {@link Line#modifyElement} records an ElementModification unconditionally and a
   * refusal inside {@link #clearChange} would leave an empty undo step behind. A subclass that
   * refuses is responsible for telling the user why.
   */
  protected boolean canClearChange(StaffElement element) {
      return true;
  }
  ```
  
  Gate the Remove-button listener on it: after resolving `element` and `line` and throwing the existing `IllegalStateException` when either is null, return early — leaving the dialog visible — when `canClearChange(element)` is false, **before** `line.getElementIndex(element)` and before `line.withModification(...)`.
  
4. In `TempoChangeDialog`, override `canClearChange(StaffElement)` as a one-line delegate to `TempoChangeConfirms.confirmRemoveTempoChange(this, element)`.
  
5. Add a test to `src/test/java/songscribe/ui/dialog/AttachmentDialogTest.java`: a stub subclass whose `canClearChange` returns false, clicked Remove. Assert that no `SongDidChangeNotification` was posted, that `song.isModified()` is false, and that the dialog is still visible. This is the assertion that pins the veto's placement — without it a guard moved inside the bracket passes every other test in the suite.
  
6. Run `./scripts/compile.sh` and confirm SUCCESS, then run `./scripts/test.sh AttachmentDialogTest TempoChangeDialogTest BeatChangeDialogTest AnnotationDialogTest` and confirm green.
  

* * *
## ✅ Phase 5: Single-Element Action Application
**Status:** Complete  
**BlockedBy:** —  
**Files:** src/main/java/songscribe/ui/selection/SelectionActionApplier.java, src/main/resources/songscribe/strings.properties  
**Recommended model/effort:** Sonnet 4.6, medium — an extract-parameter refactor of one public entry point; the existing signature must keep behaving identically
### Context
`SelectionActionApplier.apply` (`src/main/java/songscribe/ui/selection/SelectionActionApplier.java`) is the engine every reflectable toolbar action runs through. It is the **only** production path that clears a note's accidental with the full consequence handling — `AccidentalRestatements.confirm` (a user prompt), `AccidentalReconciliation.reconcileModification`, `AccidentalMaterializer.commit` and `AccidentalRestatements.commitOtherLines`. Phase 6 must reach that pipeline for a single note whose accidental was directly selected, and it must not disturb the selection to do it.

Today the method reads the selection off the coordinator:

```java
public static void apply(
    SelectionCoordinator coordinator,
    UIAction.Reflectable action,
    boolean selected,
    @Nullable ScoreView score) {

    var selection = coordinator.getSelection();

    if (selection == null) {
        return;
    }
    ...
```

`coordinator` is otherwise used only for `coordinator.invalidateSelectionCaches()` at the end of the bracket. `ElementSelection` (`src/main/java/songscribe/ui/selection/ElementSelection.java`) is the record `(Line line, int begin, int end)`.

The apply pass currently opens an **unlabeled** bracket, `song.withModification(() -> { … })`, so its undo step falls back to the generic "Edit Note". Phase 6 needs it to be nameable. The label must be passed in rather than applied by wrapping the call in an outer labeled bracket, because `decideChanges` and `AccidentalRestatements.confirm` deliberately run before any bracket opens so a dialog is never on screen while a modification bracket is open.

`Song.withModification(String label, Runnable)` takes a non-null label; `Song.withModification(Runnable)` is the unlabeled overload.

The fit gate in this method also needs correcting, and this is the only phase that touches it. `AccidentalAction` implements `UIAction.WidensColumn`, so the gate runs on accidental _removals_ too — correctly, because `AccidentalReconciliation` can add accidentals to other notes to preserve their pitch, which genuinely can overflow the line. But when it refuses, it currently shows `ALERT_TITLE_INSERT_ERROR` ("Insert Error") with `ERROR_LINE_FULL_ELEMENT` ("There isn't enough room on this line for this {0}"). Once Phase 6 lands, a user who presses Backspace on an accidental can be told the line is too full to _insert_ something.
### Tasks
1. In `src/main/resources/songscribe/strings.properties`, add one key in alphabetized position within the existing `error.line.full.*` run (which already holds `error.line.full.element`, `.fall`, `.lyric`, `.paste`). The value is deliberately contraction-free so it stays ASCII and ordinary `Edit` calls work:
  
  ```properties
  error.line.full.removal = Removing this would force accidentals onto other notes, and there is not enough room on this line.
  ```
  
  Reuse the existing `alert.title.line.too.full` ("Line Too Full") as the title — it is operation-neutral, so no new title key is needed.
  
2. Add a new public overload:
  
  ```java
  public static void apply(
      SelectionCoordinator coordinator,
      ElementSelection selection,
      UIAction.Reflectable action,
      boolean selected,
      @Nullable ScoreView score,
      @Nullable String opLabel)
  ```
  
  It holds the entire current body except the `coordinator.getSelection()` lookup and its null guard — `selection` now arrives as a parameter.
  
3. Reduce the existing four-argument `apply` to a delegate: read `coordinator.getSelection()`, return when null, otherwise call the new overload with `opLabel = null`. Its Javadoc keeps describing the three passes. The new overload's Javadoc needs the full `@param` list — the existing Javadoc uses explicit `@param` tags, so add tags for `selection` (a caller-supplied selection rather than the coordinator's current one) and `opLabel` (names the undo step; null falls back to the generic op-name).
  
4. In the new overload's apply pass, branch the bracket on the label: `opLabel != null` → `song.withModification(opLabel, body)`, otherwise `song.withModification(body)`. Extract the lambda body to a local `Runnable` so it is written once — do not duplicate it across the two branches.
  
5. In the fit-gate refusal, choose the message by direction: when `selected` is false use `Strings.ALERT_TITLE_LINE_TOO_FULL` with `Strings.ERROR_LINE_FULL_REMOVAL` (which takes no argument), otherwise keep the existing `ALERT_TITLE_INSERT_ERROR` / `ERROR_LINE_FULL_ELEMENT` call with its `categoryName()` argument. Comment why: a removal that forces reconciliation can overflow the line, and the insert wording misdescribes it.
  
6. Run `./scripts/compile.sh` and confirm SUCCESS, then run `./scripts/test.sh ApplyActionToSelectionMutationTest SelectionApplyIntegrationTest` and confirm green — these are the existing tests that exercise `apply`, and they are what proves the delegate is behavior-preserving. There is no `SelectionActionApplierTest`; do not create one. The new labeled-bracket branch is covered end-to-end by Phase 9's op-name test.
  

* * *
## ✅ Phase 6: Delete Arms
**Status:** Complete  
**BlockedBy:** 1, 4, 5  
**Files:** src/main/java/songscribe/ui/component/ScoreViewController.java  
**Recommended model/effort:** Opus 4.8, high — seven interacting delete paths, each with its own tracked removal API, guard conditions and undo label
### Context
`ScoreViewController.deleteSelectedTarget(Line line, HitTarget target)` is a pattern `switch` over `HitTarget` (`src/main/java/songscribe/hit/HitTarget.java`) with **no** `default` **arm** — every variant is listed so that adding a selectable kind fails to compile there. Three arms delete (`Slide`, `Ending`, `Hairpin`); the rest currently sit in a shared empty arm with a comment explaining why each is a no-op. This phase moves seven of them out of that arm and rewrites the comment to cover only what remains.

It is called from `handleDelete` as

```java
} else if (selectedTarget != null && targetLine != null) {
    deleteSelectedTarget(targetLine, selectedTarget);
}
```

where `targetLine` is `selectionCoordinator.getActiveLine()` and `selectedTarget` is non-null only when `selectionCoordinator.hasDecorationSelection()` is true — which is every target except `HitTarget.StaffLine` and `HitTarget.Lyric`.

Facts that must shape the implementation, beyond those in "Facts Established Before Implementation" above:

- **Span removals are already undo-tracked and take the object directly.** `Line.removeTie(Tie)`, `Line.removeBeaming(Beam)`, `Line.removeTuplet(Tuplet)` and the generic `Line.removeSpan(Span)` each guard on `spans.indexOf(...) < 0` and route through `applyChange(...)` + `removeChild(...)`. `Line.removeChild` also removes the span from `otherLineOf(element)`, which is what makes a cross-line tie (#493) vanish from **both** lines under a single `TieRemoval` mutation, so one undo restores both halves. Route tie deletion through `Line.removeTie` and nothing else — the `MusicEditOperations.toggleTie` path cannot be used here because it re-derives the tie from an index range and has no entry point taking a `Tie` object.
  
- **Tuplet removal needs no extra bookkeeping.** `MusicEditOperations.toggleTuplet`'s removal branch is exactly `line.removeTuplet(existing)`; the `TupletsWereRemovedNotification` path handled by `ScoreViewController.tupletsWereRemoved` is for tuplets forced out by _other_ edits and is not involved here.
  
- **Articulations are a list on the note.** `StaffElement.articulations` holds several at once; `Articulation.getOwnerElement()` names the note and `StaffElement.removeArticulation(Articulation)` performs the raw removal, which becomes tracked by wrapping it in `Line.modifyElement`.
  
- `Attachment.getOwnerElement()` **is** `@Nullable`**.** Fermata and dynamic are self-contained; annotation, tempo change and beat change go through `songscribe.dom.AttachmentRemoval` from Phase 3.
  
### Tasks
1. Add a private helper used by every arm that has to turn an owner into an index, so the resolve-and-guard is written once rather than three times:
  
  ```java
  private @Nullable Integer resolveOwnerIndex(Line line, @Nullable LineElement owner)
  ```
  
  It returns null when `owner` is null or `line.getElementIndex(owner)` is negative, logging at debug in both cases (see `.agents/guides/logging.md`), and the index otherwise. The log is the point: every caller of this helper swallows a keystroke when it returns null, and a delete that does nothing with no diagnostic is the worst outcome this feature can produce. Javadoc it with that reasoning. `StaffElement` is a `LineElement`, so the accidental arm can use it too.
  
2. Add the span arms to the `switch` in `deleteSelectedTarget`, each following the shape of the existing `HitTarget.Ending` arm (`line.withModification(<label>, () -> …)`):
  
  - `case HitTarget.Tie(var tie) -> line.withModification(OpNames.deleteTieLabel(), () -> line.removeTie(tie));`
    
  - `case HitTarget.Beam(var beam) -> line.withModification(OpNames.deleteBeamLabel(), () -> line.removeBeaming(beam));`
    
  - `case HitTarget.Tuplet(var tuplet) -> line.withModification(OpNames.deleteTupletLabel(), () -> line.removeTuplet(tuplet));`
    
  - `case HitTarget.Trill(var trill) -> line.withModification(OpNames.deleteTrillLabel(), () -> line.removeSpan(trill));`
    
  
  The trill's label is not optional: without it the generic `SpanRemoval` fallback makes the undo menu read "Undo Ending".
  
3. Add the articulation arm. `HitTarget.Articulation(songscribe.dom.Articulation articulation)` — resolve the index with `resolveOwnerIndex(line, articulation.getOwnerElement())` and return when it is null. Otherwise:
  
  ```java
  line.withModification(OpNames.deleteArticulationLabel(articulation.getType()), () ->
      line.modifyElement(elementIndex, ElementField.ARTICULATION,
          () -> owner.removeArticulation(articulation)));
  ```
  
4. Add the attachment arm as `case HitTarget.Attachment(var attachment) -> deleteAttachment(line, attachment);` and write the private helper `deleteAttachment(Line line, Attachment attachment)` beneath `deleteSelectedTarget`. It resolves the index via `resolveOwnerIndex(line, attachment.getOwnerElement())` and returns when null, then decides _what_ to change with a single exhaustive pattern `switch` over the sealed hierarchy — no `default` arm — yielding a small private record:
  
  ```java
  private record AttachmentRemovalPlan(ElementField field, Runnable mutator) {}
  ```
  

   | Attachment | `ElementField` | Mutator |
   |---|---|---|
   | `FermataAttachment` | `FERMATA` | `() -> element.setFermata(false)` |
   | `DynamicAttachment` | `DYNAMIC_ATTACHMENT` | `() -> element.removeAttachment(dynamic)` |
   | `AnnotationAttachment` | `ANNOTATION` | `() -> AttachmentRemoval.removeAnnotation(element)` |
   | `BeatChangeAttachment` | `BEAT_CHANGE` | `() -> AttachmentRemoval.removeBeatChange(element)` |
   | `TempoChangeAttachment` | `TEMPO_CHANGE` | `() -> AttachmentRemoval.removeTempoChange(element)` |

Then write the bracket **once**, below the switch:

```java
line.withModification(OpNames.deleteAttachmentLabel(attachment), () ->
    line.modifyElement(elementIndex, plan.field(), plan.mutator()));
```

Do not repeat the `withModification`/`modifyElement` statement in each arm — the switch decides what changes, the bracket is written once.

5. Guard the tempo-change case. Before building the plan or opening any bracket, when the attachment is a `TempoChangeAttachment`, call `TempoChangeConfirms.confirmRemoveTempoChange(score, element)` (Phase 4) and return without mutating anything when it returns false — it has already shown the user the warning. The guard must sit outside `line.withModification`, because `Line.modifyElement` records an `ElementModification` unconditionally and would otherwise leave an empty undo step. `score` is the parent so the warning is raised on the score view, not the frame behind it.
  
6. Add the accidental arm. `HitTarget.Accidental(StaffElement owner)` names the note, not a `LineElement`. Resolve the index with `resolveOwnerIndex(line, owner)` and return when it is null or `owner.getAccidental()` is null. Otherwise route through the full accidental pipeline — the same one the toolbar toggle uses, so Delete and the toggle can never disagree about restatements and courtesy accidentals:
  
  ```java
  SelectionActionApplier.apply(
      selectionCoordinator,
      new ElementSelection(line, elementIndex, elementIndex),
      accidentalAction,
      false,
      score,
      OpNames.deleteAccidentalLabel());
  ```
  
  using the six-argument overload from Phase 5. For `accidentalAction`, take `Actions.ACCIDENTAL_ACTION_GROUP.getActions().getFirst()` (`src/main/java/songscribe/ui/action/Actions.java`) — do **not** scan the group for the action matching the note's accidental. `AccidentalAction.applyToElement(element, false)` is `element.setAccidental(null)` regardless of which accidental the action represents, and `decideChanges` gates on `appliesTo`, never `matchesElement`; a scan would buy nothing and its no-match branch would silently eat the keystroke. Comment that reasoning at the call site. `StaffElement.setAccidental(null)` already drops the parenthesized-accidental flag, so no separate `ACCIDENTAL_IN_PARENS` handling is needed. Do not add a null guard on `Actions.ACCIDENTAL_ACTION_GROUP` for the benefit of tests — it is a deferred-init field populated at application start, and Phase 9 initializes it in the test environment.
  
7. Rewrite the trailing no-op arm and its explanatory comment, and replace the prose with the condensed dispatch diagram from this plan's "Dispatch Map" section. Only `HitTarget.Element`, `HitTarget.GraceGlissando`, `HitTarget.StaffLine` and `HitTarget.Lyric` remain in the arm. The comment should say: a note is selected as an index range and deleted by `handleDelete`'s range branch; a grace-note glissando is not selectable; and the staff line and a lyric are handled by `handleDelete` before it reaches here. Delete the now-false claims about ties, beams, tuplets, trills, accidentals, articulations and attachments being removed elsewhere. Update `deleteSelectedTarget`'s method Javadoc, which currently says "Only a slide, an ending and a hairpin can be deleted this way."
  
8. Run `./scripts/compile.sh` and confirm SUCCESS, then run `./scripts/test.sh ScoreViewControllerTest DeleteActionTest DeleteLyricTest`. The parameterized test `testHandleDeleteLeavesTheLineIntactForKindsItDoesNotDelete` and its `undeletableTargets()@MethodSource` in `src/test/java/songscribe/ui/component/ScoreViewControllerTest.java` will now fail for the kinds that became deletable — that is expected, and Phase 7 owns that file. Do **not** fix them here. Report the exact list of failing cases so Phase 7 can confirm it addressed each one.
  

* * *
## ✅ Phase 7: Test Split and Span Delete Tests
**Status:** Complete  
**BlockedBy:** 6  
**Files:** src/test/java/songscribe/ui/component/ScoreViewControllerDeleteTargetTest.java, src/test/java/songscribe/ui/component/ScoreViewControllerTest.java  
**Recommended model/effort:** Opus 4.8, medium — a file split plus repetitive per-kind tests, but the cross-line tie fixture needs real judgment
### Context
Read `.agents/guides/testing-common.md` and `.agents/guides/testing-unit.md` first.

`src/test/java/songscribe/ui/component/ScoreViewControllerTest.java` is 3,777 lines. Its nested `HandleDelete` class already covers target deletion, and this plan adds roughly eighteen more tests to it. Move the target-delete cluster into its own file rather than growing that one past 4,000 lines. This mirrors the splits already applied to `SelectionCoordinator`, `PreviewElementManager` and `Song` on this branch's lineage; splitting the production class is tracked separately as issue #733 and is **not** in scope here.

Existing private helpers build a short line, attach the notation, select it through `src/test/java/songscribe/ui/selection/ReflectionTestHelper.java` (`createCoordinatorForLine`, `selectTarget`, `selectGlissando`, `selectEnding`, `selectHairpin`, `selectRange`, `selectNote`), call `controller.handleDelete()`, then assert removal. `deleteSelectedHairpin` (around line 722) is the cleanest example — it builds the fixture, deletes, and asserts both "gone from the line" and `song.isModified()`. Mirror its shape rather than inventing a new one.

`undeletableTargets()` currently asserts that tie, beam, trill, accidental, articulation and attachment leave the line untouched. Those assertions are now wrong. Note that it never included a `HitTarget.Tuplet` case despite the test's Javadoc claiming it did — a pre-existing gap, not a regression.
### Tasks
1. Create `src/test/java/songscribe/ui/component/ScoreViewControllerDeleteTargetTest.java` and move the whole target-delete cluster into it from `ScoreViewControllerTest`: the glissando, slide, ending and hairpin delete tests, `deleteSelectedHairpin`, `undeletableTargets()`, `testHandleDeleteLeavesTheLineIntactForKindsItDoesNotDelete`, and whichever private fixture helpers (`buildController`, `crotchet`, and so on) they need. Leave the range-delete, pasteboard, restatement and message-handler tests where they are. Run `./scripts/test.sh ScoreViewControllerTest ScoreViewControllerDeleteTargetTest` and confirm both are green before changing any assertion — the move must be behavior-neutral first.
  
2. Shrink `undeletableTargets()` to the kinds that are still no-ops — `HitTarget.Element`, `HitTarget.GraceGlissando` — and correct the test's Javadoc so it names only those. Keep the test itself; it is what stops a future kind from silently falling through to the whole-line delete. Confirm the resulting pass list matches the failures Phase 6 reported.
  
3. Add a stale-target regression test. Shrinking `undeletableTargets()` loses the property that a span target _not_ present in `line.getSpans()` still does not fall through to deleting the staff line. Select a `HitTarget.Tie` whose `Tie` was never added to the line, with `canDeleteLine()` stubbed true, call `handleDelete()`, and assert the line count and element count are unchanged.
  
4. Add a delete-and-undo test per newly deletable span kind — tie, beam, tuplet, trill. Each asserts the span is gone from `line.getSpans()` after `controller.handleDelete()` and that `song.isModified()` is true.
  
5. Add a cross-line tie test: build a two-line song with one `Tie` present in both lines' spans (`src/test/java/songscribe/dom/CrossLineTieDeletionTest.java` and `src/test/java/songscribe/ui/selection/CrossLineTieSelectionTest.java` both construct this fixture — reuse whichever construction is simplest to lift). Select the tie as a `HitTarget.Tie`, call `handleDelete()`, and assert it is absent from **both** lines' spans and that a single undo restores it to both. This is the regression the issue explicitly asks for: a removal that reached only the clicked line would leave an unreachable half-tie.
  
6. Run `./scripts/compile.sh`, then `./scripts/test.sh ScoreViewControllerTest ScoreViewControllerDeleteTargetTest`, and confirm green.
  

* * *
## ✅ Phase 8: Articulation and Attachment Delete Tests
**Status:** Complete  
**BlockedBy:** 7  
**Files:** src/test/java/songscribe/ui/component/ScoreViewControllerDeleteTargetTest.java  
**Recommended model/effort:** Opus 4.8, medium — repetitive per-kind tests over fixtures Phase 7 established
### Context
Read `.agents/guides/testing-common.md` and `.agents/guides/testing-unit.md` first. Continue in the file Phase 7 created, following the `deleteSelectedHairpin` shape.

Assert against the document model — `StaffElement.findAttachment(...)`, the articulation list — never against mocks. The point of each test is that the notation is really gone and that a tracked mutation was recorded, not that a particular method was called.
### Tasks
1. Add an articulation test on a note carrying **both** a staccato and an accent, asserting the selected one is removed and the other survives. Two articulations on one note is the case that distinguishes "removed the right one" from "cleared the list".
  
2. Add fermata, dynamic and annotation deletion tests via `HitTarget.Attachment`, each asserting the attachment is gone from the element and `song.isModified()` is true.
  
3. Add a beat change deletion test via `HitTarget.Attachment`.
  
4. Add a tempo change deletion test for the ordinary case: a mid-song tempo change with an earlier tempo change before it, which must delete normally.
  
5. Add the tempo refusal test: a song whose **first** tempo change is selected while a later tempo change exists must leave both attachments in place and produce no modification — assert `song.isModified()` is false, not merely that the attachment survived. Call `OptionDialogs.setSuppressDialogs(true)` so the warning does not block; check whether `UnitTest` already does this before adding it.
  
6. Add guard tests for `resolveOwnerIndex`'s two null outcomes, driven through `handleDelete`: an `Articulation` whose owner element is not on the line, and an `Attachment` with a null owner. Each must leave the line and the line count untouched.
  
7. Run `./scripts/compile.sh`, then `./scripts/test.sh ScoreViewControllerDeleteTargetTest`, and confirm green.
  

* * *
## ✅ Phase 9: Accidental Tests and Op-Name Assertions
**Status:** Complete  
**BlockedBy:** 8  
**Files:** src/test/java/songscribe/ui/component/ScoreViewControllerDeleteTargetTest.java  
**Recommended model/effort:** Opus 4.8, medium — the accidental pipeline fixtures and the notification-capture harness both need real judgment
### Context
Read `.agents/guides/testing-common.md` and `.agents/guides/testing-unit.md` first.

The accidental arm is the only new path that mutates notes the user did not select: removing an explicit accidental changes the sounding pitch of later notes, and `AccidentalReconciliation.reconcileModification` plus `AccidentalRestatements` materialize accidentals on them to compensate. A test that checks only the selected note passes even if the entire reconciliation is skipped — which is the whole reason Phase 5 exists. `ScoreViewControllerTest` already has `testDeleteAcceptedRemovesTheNoteAndTheRestatement`, `testDeleteDeclinedRemovesTheNoteAndLeavesTheRestatement` and `testDeleteCancelledRemovesNothingAtAll` (around lines 1319–1366); lift their fixture and their `OptionDialogs` handling.

The production path resolves its action from `Actions.ACCIDENTAL_ACTION_GROUP`, so these tests must run with `Actions` initialized — follow whatever `MainFrame` mock setup the rest of the suite uses, and if `ACCIDENTAL_ACTION_GROUP` is still unpopulated, initialize it the same way the existing accidental tests in `src/test/java/songscribe/ui/action/` do.

For op-names, `src/test/java/songscribe/ui/action/OpNameThreadingTest.java` shows the pattern: capture the posted `SongDidChangeNotification` and assert `getOpName()`. Every other test in this plan asserts that _a_ mutation was recorded; none asserts it was named correctly, and a wrong undo label is invisible until the user undoes the wrong thing.
### Tasks
1. Add the basic accidental test: select `HitTarget.Accidental` on a note with an accidental, call `handleDelete()`, and assert `element.getAccidental()` is null afterwards and `song.isModified()` is true.
  
2. Add the restatement-accepted test: a fixture where removing the accidental forces a restatement on a later note, with the prompt accepted. Assert the selected note's accidental is null **and** the restatement was materialized on the later note.
  
3. Add the restatement-declined test on the same fixture, asserting the accidental is removed and the later note is left alone — the branch that proves the decision is actually threaded through rather than assumed.
  
4. Add the fit-refusal test: a line full enough that the accidentals reconciliation forces cannot fit. Assert the note keeps its accidental, `song.isModified()` is false, and no undo step was produced. This is the path Phase 5 gave its own error wording.
  
5. Add a parameterized op-name test covering all eleven deletable kinds — tie, beam, tuplet, trill, staccato, accent, fermata, dynamic, annotation, beat change, tempo change — plus the accidental. Each case builds the fixture, deletes, captures the `SongDidChangeNotification`, and asserts `getOpName()` equals the expected `Strings.get(...)` value. This is what catches a label wired to the wrong arm, a swapped staccato/accent mapping, and the trill falling back to "Undo Ending". It also exercises Phase 5's labeled-bracket branch end-to-end through the accidental case, which is why no separate `SelectionActionApplier` test is needed.
  
6. Run `./scripts/compile.sh`, then `./scripts/test.sh ScoreViewControllerTest ScoreViewControllerDeleteTargetTest`, and confirm green.
  

* * *
## ✅ Phase 10: Tempo Orphan Tests
**Status:** Complete  
**BlockedBy:** 2  
**Files:** src/test/java/songscribe/dom/TempoOrphanRemovalTest.java  
**Recommended model/effort:** Sonnet 4.6, low — a handful of table-driven cases over one pure query
### Context
Read `.agents/guides/testing-common.md` and `.agents/guides/testing-unit.md` first. Extend `UnitTest`; `minimalSongMock()` and `detachedLine()` build a `Song`/`Line` without the UI singleton graph, which is all this query needs.

Phase 2 added `Song.wouldOrphanLaterTempoChange(StaffElement)`, delegating to `TempoResolver.removalWouldOrphanLaterTempoChange(int lineIndex, int elementIndex)`. It returns true exactly when no element strictly before the given position carries a `TempoChangeAttachment` and at least one element strictly after it does.

Create the file new — no existing test covers this query. This phase depends only on Phase 2 and can run in parallel with the controller work.
### Tasks
1. Create `src/test/java/songscribe/dom/TempoOrphanRemovalTest.java` extending `UnitTest`.
  
2. Cover the true case: a song whose first tempo change sits at line 0 element 0 and a second tempo change sits later — `wouldOrphanLaterTempoChange` on the first element returns true.
  
3. Cover the false cases, each as its own test with a name stating the condition:
  
  - the only tempo change in the song (nothing later to orphan)
    
  - a tempo change with an earlier tempo change before it (the earlier one still covers the later)
    
  - the last of several tempo changes
    
  - an element carrying no tempo change at all
    
  - an element whose `getParentLine()` is null
    
  - an element whose `getElementIndex` returns −1 (in a line, but not among its elements)
    
4. Cover the self-exclusion rule explicitly: the element under test carries a tempo change and is the **only** one in the song. It must return false — its own attachment must not be counted as "one after", which is the off-by-one the forward scan's flag exists to prevent.
  
5. Cover the multi-line cases: one where the earlier tempo change is on line 0 and the element under test is on line 1, and one where the later tempo change is on line 2.
  
6. Run `./scripts/compile.sh`, then `./scripts/test.sh TempoOrphanRemovalTest`, and confirm green.
  

* * *
## ✅ Phase 11: Manual UI Verification
**Status:** Complete — deletion and undo verified working. Two defects found, fixed in Phase 12: the far half of a cross-line tie did not draw as selected, and the undo labels said "Delete X" where the user wanted "Remove X".  
**BlockedBy:** 9, 10  
**Files:** —  
**Recommended model/effort:** Haiku 4.5, low — presents a checklist to the user and records the outcome; no code changes
### Tasks
1. Confirm the full suite is green before starting — this pass confirms behavior the tests already assert, it does not stand in for them.
  
2. Ask the user for permission before running the app; never execute `./scripts/run.sh` without it. With permission, launch `./scripts/run.sh` and ask the user to work through the checklist below, or hand them the checklist to run themselves.
  
3. Checklist — for each, click the notation directly to select it, press Backspace (and Delete), confirm it disappears, then confirm one Undo restores it and the Undo menu item names the right operation:
  
  - tie; **cross-line tie** — select it from either line's half, confirm _both_ halves vanish and a single Undo restores both
    
  - beam group; tuplet bracket; trill (including one with a wavy-line extension) — the trill's Undo entry must read "Undo Remove Trill", not "Undo Ending"
    
  - staccato and accent on a note that carries both — deleting one must leave the other
    
  - fermata; dynamic marking; annotation
    
  - beat change; tempo change (an ordinary one, mid-song)
    
  - tempo change that is the **first** tempo in the song while a later tempo change exists — expect the warning "There is a tempo change later in the song which would be orphaned, so this tempo cannot be removed.", raised **in front of** the score, with no change to the score or the undo history. The mark deselects afterwards, exactly as a no-op delete does today.
    
  - the same refusal from the Tempo Change dialog's Remove button — the warning must appear in front of the dialog, the dialog must stay open, and no undo step may be added
    
  - accidental on a note — including one whose removal triggers the accidental restatement prompt, to confirm Delete behaves the same as toggling the accidental off from the toolbar
    
  - accidental on a note in a nearly full line, where reconciliation cannot fit the accidentals it forces — expect the "Line Too Full" warning, not an "Insert Error"
    
  - a selected note (index-range selection) still deletes as before; a selected staff line still deletes the line; a selected lyric syllable still deletes the syllable
    
4. Report the user's results verbatim. If anything is wrong, report it and stop rather than patching behavior the user has rejected without discussing it first.

* * *
## ✅ Phase 12: Verification Follow-ups
**Status:** Complete  
**BlockedBy:** 11  
**Files:** src/main/java/songscribe/ui/component/ScoreView.java, src/main/java/songscribe/ui/component/score/LineSelectionHandler.java, src/main/java/songscribe/undo/OpNames.java, src/main/resources/songscribe/strings.properties, src/test/java/songscribe/ui/component/ScoreViewTest.java, src/test/java/songscribe/ui/component/ScoreViewControllerDeleteTargetTest.java
### Cross-line tie selection highlight
Selecting either half of a cross-line tie left the other half drawn unselected. The model was already right — `SelectionCoordinator.isSelected` special-cases `HitTarget.Tie` via `Tie.isIn(line)` so both lines answer "selected", and `TieRenderer.determineTieColor` asks per-line. The gap was repaint dispatch: `LineSelectionHandler.handlePress` ends with `lc.repaint()`, and `ScoreView.clearSelection` repaints only the outgoing active line, so the far half was never asked to redraw.

`ScoreView.repaintTieHalves(HitTarget)` repaints the components of both endpoint lines and no-ops for every other kind. `clearSelection` reads the outgoing target before clearing and calls it; `LineSelectionHandler.selectTarget` calls it after selecting.
### Delete → Remove wording
The twelve op-name keys this branch added were renamed from `action.edit.op.delete.*` to `action.edit.op.remove.*` with "Remove X" values. Four of them — annotation, beat change, tempo change, tuplet — collided with the existing dialog Remove-button keys and were merged into those, so the dialog and the Delete key now name the same edit identically in the undo menu. The pre-existing `delete.*` keys (Delete Note, Delete Line, Delete Barline, …) were deliberately left alone. The `OpNames` methods were renamed to match the text they return (`removeTieLabel`, `removeAttachmentLabel`, …).
