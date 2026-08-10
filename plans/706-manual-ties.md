# Manual ties on the last-placed note — the `t` key in edit mode

Closes #706.

## Context

Commit `d237122` finished the "act on the last element placed" infrastructure: `EditModeManager`
remembers the last placement as an `Insertion(line, elementIndex)`, `ScoreInputHandler` binds plain
`b` on the focused score, and `ScoreViewController.handleLastInsertionCommand` runs the operation and
beeps when it refuses. Shift+G (glissando) and `f` (fall) have since joined it, so the shell is a
settled three-user pattern.

Ties have no such key. Today the only way to tie is to leave edit mode, select the pair and use the
menu action or its `t` accelerator. Issue #706 asks for the analogous edit-mode behavior: after
placing a note, press `t` to tie it to the note before it, and press `t` again to untie.

Ties differ from beams in ways that make a literal copy of `toggleBeamWithPredecessor` wrong:

- **Ties never coalesce.** `Line.addTie` does no span merging (one `Tie` per adjacent pair), so there
  is no widen-or-break branch. The toggle is a plain add/remove of the exact tie over the pair.
- **Grace notes are not transparent.** For beams they are skipped; a tie is invalidated the moment a
  grace note appears between its notes, and a grace note is rejected at either endpoint.
- **A separator may sit between the pair** — a barline, repeat or breath mark (refs #527).
- **Ties may cross a line break** (#493), which beams cannot.

All four rules already exist, stated once each in `RangeQueries.canToggleTie` /
`RangeQueries.boundaryTieAt` / `Tie.isLegalSeparator`. This plan reuses them verbatim rather than
restating any of them, which is possible because `Selection.Range` is a freely constructible record
and needs no live selection.

### Target behavior

| Placed, then `t` pressed | Result |
| --- | --- |
| note, same pitch as the one before it | ties them |
| `t` again | unties them |
| note preceded by a barline, same pitch before that | ties across the barline |
| note of a different pitch | beep |
| rest, or a grace note | beep |
| note preceded by a grace note | beep — a grace note is not a legal separator |
| first note of a line, previous line ends on the same pitch | ties across the line break |
| first note of the first line | beep |
| pair already joined by a beam | beep — the same conflict rule the beam side applies |
| note that is its line's tie-exit element, next line starts on the same pitch | beep — never tie *forward* |

`t` stays armed across its own effect, so repeated presses toggle, exactly as `b` does. Outside edit
mode, or during grace/paste mode, the binding reports itself disabled so the key falls through to
`TOGGLE_TIE_ACTION`'s root-pane accelerator.

### Control flow

```
KEY `t` pressed, ScoreView focused (WHEN_FOCUSED input map)
  │
  ├─ registerLastInsertionBinding(VK_T, ToggleTieWithPreviousCommand::new)
  │    isEnabled = mode==EDIT && !graceInProgress && !pasteInProgress
  │       ├── false ─► key not consumed ─► root pane ─► TOGGLE_TIE_ACTION   (existing)
  │       └── true  ─► MessageCenter.post(ToggleTieWithPreviousCommand)
  │
  └─ ScoreViewController.handleToggleTieWithPrevious
        └── handleLastInsertionCommand(pred)                     (existing shell)
              ├── PlaybackController.isPlaying ──────────────────────► beep
              ├── getLastInsertion() == null ────────────────────────► beep
              └── MusicEditOperations.toggleTieWithPredecessor(line, t)
                    │
                    └── RangeQueries.tieCandidateWithPredecessor(line, t)
                          ├── t < 0 || t >= elementCount ─────────► null
                          ├── cand A  Range(line, t-1, t)     skip if t-1 < 0
                          ├── cand B  Range(line, t-2, t)     skip if t-2 < 0
                          ├── cand C  Range.single(line, t)
                          │      guard: boundaryTieAt(line,t).end() === element(t)
                          │             └─ keeps the backward tie, rejects the forward one
                          │      first candidate canToggleTie accepts wins
                          └── none accepted ──────────────────────► null
                    │
                    ├── null ──────────────────────────────────► false ► beep
                    └── armInsertion(line, t); toggleTieInRange(cand) ─► true
                          ├── size 1  ► toggleBoundaryTie
                          └── size 2/3 ► add or remove the exact tie
```

---

## Status Dashboard

| Phase | Description | Status | Sub-plan |
|-------|-------------|--------|----------|
| 1 | [Make the tie toggle callable without a selection](#-phase-1-make-the-tie-toggle-callable-without-a-selection) | ✅ Complete | — |
| 2 | [Candidate query and predecessor operation](#-phase-2-candidate-query-and-predecessor-operation) | ✅ Complete | — |
| 3 | [Wire the key](#-phase-3-wire-the-key) | ✅ Complete | — |
| 4 | [Candidate-selection tests](#-phase-4-candidate-selection-tests) | ✅ Complete | — |
| 5 | [Toggle, arming and undo tests](#-phase-5-toggle-arming-and-undo-tests) | ✅ Complete | — |
| 6 | [EditModeManager arming tests](#-phase-6-editmodemanager-arming-tests) | ✅ Complete | — |
| 7 | [ScoreInputHandler binding tests](#-phase-7-scoreinputhandler-binding-tests) | ✅ Complete | — |
| 8 | [ScoreViewController handler tests](#-phase-8-scoreviewcontroller-handler-tests) | ✅ Complete | — |
| 9 | [End-to-end](#-phase-9-end-to-end) | ✅ Complete | — |
| 10 | [Manual UI verification](#-phase-10-manual-ui-verification) | ✅ Complete | — |

---

## ✅ Phase 1: Make the tie toggle callable without a selection

**Status:** Complete  <br>
**BlockedBy:** —  <br>
**Files:** src/main/java/songscribe/ui/MusicEditOperations.java  <br>
**Recommended model/effort:** Sonnet, low effort — behavior-preserving extract-method; existing tie tests gate it

`MusicEditOperations` is `public final class MusicEditOperations` with instance fields
`private Song song` and `private final SelectionCoordinator coordinator`.
`public void toggleTie()` (around line 233) reads `coordinator.getRange()`, returns early on null,
and then does work that depends on nothing but the range. Split those two jobs.

### Tasks

1. Extract everything after the `range == null` early return of `toggleTie()` into a new
   `public static void toggleTieInRange(Selection.Range range)` — the whole
   `line.withModification(...)` block, unchanged, including the `range.size() == 1` delegation to
   `toggleBoundaryTie`. Use that distinct name rather than an overload of `toggleTie`: an overload
   differing only in parameters would be confusable at the call site. Place it next to `toggleTie()`.
2. Change `private void toggleBoundaryTie(Line line, int index)` (around line 276) to
   `private static void toggleBoundaryTie(Line line, int index)`. Verified safe: its body touches
   neither `this.song` nor `this.coordinator` — it calls only the static
   `RangeQueries.boundaryTieAt(line, index)` and instance methods on the passed-in `line`
   (`findTieBetween`, `addTie`, `removeTie`).
3. Reduce `toggleTie()` to: read `coordinator.getRange()`, return if null, else
   `toggleTieInRange(range)`. Leave `toggleTie()` an instance method — `ScoreViewController.handleToggleTie`
   and several tests call it on an instance.
4. Run `./scripts/compile.sh` — must report SUCCESS.
5. Run `./scripts/test.sh` — must be green. These suites already exercise `toggleTie()` and gate the
   refactor: `src/test/java/songscribe/dom/TieToggleTest.java`,
   `src/test/java/songscribe/dom/MusicEditOperationsMutationTest.java`,
   `src/test/java/songscribe/ui/MusicEditOperationsNullStateTest.java`,
   `src/test/java/songscribe/ui/selection/CrossLineTieToggleTest.java`.

---

## ✅ Phase 2: Candidate query and predecessor operation

**Status:** Complete  <br>
**BlockedBy:** 1  <br>
**Files:** src/main/java/songscribe/ui/selection/RangeQueries.java, src/main/java/songscribe/ui/MusicEditOperations.java  <br>
**Recommended model/effort:** Opus, high effort — candidate ordering and the tie-direction guard are the conceptual core of the feature

Phase 1 has already added `public static void toggleTieInRange(Selection.Range range)` to
`src/main/java/songscribe/ui/MusicEditOperations.java`; this phase calls it.

The new candidate-selection logic lives in `RangeQueries` alongside the rules it depends on, not in
`MusicEditOperations`: the separator span and the cross-line direction rule are tie knowledge, and
`RangeQueries` already owns every other piece of it (`canBeTied`, `canToggleTie`, `boundaryTieAt`,
`tieAttachmentIndex`, the `TIE_SELECTION_SIZE_*` constants). This mirrors the beam side, where
`toggleBeamWithPredecessor` delegates its walk outward to `Line.nearestNonGraceIndex`.

### Tasks

1. Add to `src/main/java/songscribe/ui/selection/RangeQueries.java`, next to `canToggleTie`:

   ```java
   public static @Nullable Selection.Range tieCandidateWithPredecessor(Line line, int index)
   ```

   Returns the range `t` would toggle, or null if no pair qualifies. **No tie rule is restated
   here** — every one of them is `canToggleTie`'s to enforce. The method only proposes shapes and
   returns the first one `canToggleTie` accepts, in this order:
   - reject outright if `index < 0 || index >= line.elementCount()`
   - candidate A: `new Selection.Range(line, index - 1, index, index)`, skipped when `index - 1 < 0`
   - candidate B: `new Selection.Range(line, index - 2, index, index)`, skipped when `index - 2 < 0`
   - candidate C: `Selection.Range.single(line, index)`, subject to the direction guard in task 3
   - none accepted → `null`

2. **Reuse the existing separator constant** for candidate B's span. `RangeQueries` already declares
   `private static final int TIE_SELECTION_SIZE_WITH_SEPARATOR = 3;`, so the offset is
   `TIE_SELECTION_SIZE_WITH_SEPARATOR - 1`. Do not introduce a second constant — one fact, one
   definition. Candidates with a negative begin index must be skipped **without constructing a
   `Range`**: `Selection.Range`'s compact constructor throws `IllegalArgumentException` when
   `begin < 0`, when `end < begin`, or when `anchor` falls outside `[begin, end]`.

3. **The direction check on candidate C is mandatory, and it is not a first-line-only concern.**
   `RangeQueries.boundaryTieAt(Line, int)` returns a `@Nullable BoundaryTie`, a record
   `BoundaryTie(StaffElement anchor, StaffElement end)`. It has two branches: when `index` is the
   line's tie-**entry** element it returns the *backward* candidate (`end()` is the element at
   `index`); when `index` is the line's tie-**exit** element it returns the *forward* candidate
   (`end()` is the next line's element). Without a guard, placing the last note of line 2 with a
   wrong-pitch in-line predecessor would tie forward into line 3's first note — a note the user has
   not reached. Accept candidate C only when `boundaryTieAt(line, index)` is non-null **and** its
   `end()` is identically `line.getElement(index)`. Use `==` with the
   `//noinspection ObjectEquality` comment the codebase uses for deliberate identity tests (see
   `MusicEditOperations.toggleBeamWithPredecessor` for the existing instance of that comment).
   `tieEntryIndex`/`tieExitIndex`/`tieAttachmentIndex`/`canBeTied` are private and stay private.

4. **Trying the adjacent pair first is what keeps the separator rule unstated here.** A barline at
   `index - 1` fails `canBeTied` inside `canToggleTie` (a barline is not a pitched note), so
   candidate B is reached only when A is genuinely ineligible, and `canToggleTie`'s own size-3 branch
   then decides whether what sits between is a legal separator. A *note* of the wrong pitch at
   `index - 1` likewise fails A and then fails B, because a note is not a legal separator — refusal,
   which is right. Do not add any pre-filter that would short-circuit this.

5. Add to `src/main/java/songscribe/ui/MusicEditOperations.java`, next to `toggleBeamWithPredecessor`
   (the model for the arming discipline and the boolean return):

   ```java
   public static boolean toggleTieWithPredecessor(Line line, int elementIndex) {
       var range = RangeQueries.tieCandidateWithPredecessor(line, elementIndex);

       if (range == null) {
           return false;
       }

       EditModeManager.armInsertion(line, elementIndex);
       toggleTieInRange(range);

       return true;
   }
   ```

   Static and taking `(Line, int)` for the same reason the beam version is: no coordinator,
   unit-testable against a bare `Song`/`Line`. **`EditModeManager.armInsertion` is called on the
   single committing path only,** immediately before the mutation — the invariant
   `toggleBeamWithPredecessor`'s Javadoc records: a refusing path opens no modification bracket, so
   an arm made ahead of the gate would sit unclaimed until an unrelated edit adopted it.

6. Javadoc: on `tieCandidateWithPredecessor`, record why there is no break branch (ties never
   coalesce, unlike beams — `Line.addTie` does no span merging) and why grace notes are *not*
   transparent here although they are for beams. On `toggleTieWithPredecessor`, record the arming
   invariant. No undo wiring and no new string key are needed: `UndoController.opNameKey` already
   maps `TieAddition`/`TieRemoval` to `Strings.ACTION_EDIT_OP_TIE`.

7. Run `./scripts/compile.sh` — must report SUCCESS.

---

## ✅ Phase 3: Wire the key

**Status:** Complete  <br>
**BlockedBy:** 2  <br>
**Files:** src/main/java/songscribe/message/command/ToggleTieWithPreviousCommand.java, src/main/java/songscribe/ui/component/ScoreInputHandler.java, src/main/java/songscribe/ui/component/ScoreViewController.java  <br>
**Recommended model/effort:** Sonnet, low effort — three mechanical additions copied in shape from the beam key

Phase 2 has already added
`public static boolean MusicEditOperations.toggleTieWithPredecessor(Line line, int elementIndex)`.

Read `.agents/guides/messages.md` before adding the handler.

### Tasks

1. Create `src/main/java/songscribe/message/command/ToggleTieWithPreviousCommand.java`, copied in
   shape from `src/main/java/songscribe/message/command/ToggleBeamWithPreviousCommand.java` — the
   project's GPL header, `package songscribe.message.command;`, `import songscribe.message.Message;`,
   then `public class ToggleTieWithPreviousCommand extends Message {}`.

2. In `ScoreInputHandler.installKeyBindings` (around lines 193-214), add a fourth call beside the
   existing three (`VK_B`, Shift+`VK_G`, `VK_F`):

   ```java
   registerLastInsertionBinding(bindings, inputMap, actionMap,
       KeyStroke.getKeyStroke(KeyEvent.VK_T, 0), ToggleTieWithPreviousCommand::new);
   ```

   Nothing else in the file changes. `registerLastInsertionBinding` already carries the `isEnabled`
   mode guard and the `WHEN_FOCUSED`-beats-root-pane precedence its Javadoc explains. `VK_T` is not
   in the file's `KEY_CODES` array (which holds only `VK_UP, VK_DOWN, VK_LEFT, VK_RIGHT, VK_PAGE_UP,
   VK_PAGE_DOWN, VK_ENTER`); plain `t` exists only as `TOGGLE_TIE_ACTION`'s root-pane accelerator
   (`ToggleNotationAction.createTieAction`), which is exactly the fall-through target the disabled
   state is designed to reach.

3. In `ScoreViewController`, beside `handleToggleBeamWithPrevious` (around line 277):

   ```java
   @Handler
   public void handleToggleTieWithPrevious(ToggleTieWithPreviousCommand message) {
       handleLastInsertionCommand(insertion ->
           !MusicEditOperations.toggleTieWithPredecessor(insertion.line(), insertion.elementIndex()));
   }
   ```

   `handleLastInsertionCommand(Predicate<EditModeManager.Insertion> shouldBeep)` already covers the
   playing-back beep, the no-armed-target beep, and the leave-arming-to-the-operation rule. Add the
   import for `ToggleTieWithPreviousCommand`.

4. Run `./scripts/compile.sh` — must report SUCCESS.

---

## ✅ Phase 4: Candidate-selection tests

**Status:** Complete  <br>
**BlockedBy:** 2  <br>
**Files:** src/test/java/songscribe/ui/selection/RangeQueriesTieCandidateTest.java  <br>
**Recommended model/effort:** Sonnet, medium effort — pure-function tests, but the two direction-guard fixtures need care

Read `.agents/guides/testing-common.md` and `.agents/guides/testing-unit.md` first.

Under test:
`RangeQueries.tieCandidateWithPredecessor(Line line, int index)` → `@Nullable Selection.Range`
(added by Phase 2). It is a pure function of `(Line, int)`, so these tests need no `mockStatic`.

Conventions to follow, taken from the neighbouring suites:
`src/test/java/songscribe/ui/selection/RangeQueriesTest.java` and `RangeQueriesSlideTest.java` —
extend `UnitTest`, no `@DisplayName`, build lines with a local
`lineOf(StaffElement...)`-style helper over `detachedLine()`, and group with `@Nested` classes named
as descriptive phrases. Build elements only via `songscribe.dom.StaffElementFactory`
(`crotchet`, `quaver`, `graceQuaver`, `crotchetRest`, `singleBarline`,
`createNote(staffPosition, upper)`) — never `ElementType.X.newInstance()` and never a local `note()`
helper. For multi-line fixtures, copy the `twoLineFixture(...)` shape from
`src/test/java/songscribe/ui/selection/CrossLineTieToggleTest.java` (real `Song`, `new Line(song)`,
element assembly inside `song.withoutMutationTracking(...)`); those tests need no coordinator here,
since `tieCandidateWithPredecessor` takes the line directly.

### Tasks

1. Create `src/test/java/songscribe/ui/selection/RangeQueriesTieCandidateTest.java` covering the
   same-line accept and reject cases. Assert the returned range's `begin()`/`end()`, or null:
   - adjacent same-pitch notes → `[index - 1, index]`
   - different pitch; a rest at either end; a grace note as the target; a grace note between the pair
     → null
   - a barline between the pair → `[index - 2, index]`
   - a beam already spanning the pair → null (the add is blocked)
   - index 0 of the song's first line, and out-of-range indices (negative, `== elementCount`) → null
2. Add the cross-line cases to the same file:
   - previous line ending on the same pitch → `Selection.Range.single(line, index)` (assert
     `begin() == end() == index`)
   - **direction guard, first line of the song:** `index` is the first line's tie-exit element and
     the next line starts on the same pitch → null
   - **direction guard, mid-song:** three lines; `index` is line 2's tie-exit element, its in-line
     predecessor is a different pitch, and line 3 starts on the same pitch → null

   These two are the critical cases: without the guard both would return a range and the toggle would
   silently draw a forward tie.
3. Run `./scripts/compile.sh` — SUCCESS — then `./scripts/test.sh` — green.

---

## ✅ Phase 5: Toggle, arming and undo tests

**Status:** Complete  <br>
**BlockedBy:** 2  <br>
**Files:** src/test/java/songscribe/ui/MusicEditOperationsTieWithPredecessorTest.java  <br>
**Recommended model/effort:** Sonnet, medium effort — mirrors an existing suite closely, but the cross-line and chained-tie fixtures need care

Read `.agents/guides/testing-common.md` and `.agents/guides/testing-unit.md` first.

Under test:
`MusicEditOperations.toggleTieWithPredecessor(Line line, int elementIndex)` → `boolean`
(added by Phase 2).

Create `src/test/java/songscribe/ui/MusicEditOperationsTieWithPredecessorTest.java` on the fixture
pattern of `src/test/java/songscribe/ui/MusicEditOperationsBeamWithPredecessorTest.java`: extend
`UnitTest`; a `lineOf(StaffElement...)` helper that builds a real `Line` inside
`song.withoutMutationTracking(...)` and then opens `mockStatic(MessageCenter.class)`;
`mockStatic(EditModeManager.class)` opened unconditionally in `@BeforeEach`; both closed in
`@AfterEach` through `@Nullable MockedStatic<...>` fields; `@Nested` classes named as behavior
phrases. Add a `tieSpans(Line)` helper mirroring its `beamSpans`:

```java
/** The line's beam spans as [anchor, end] index pairs, in span order. */
private static List<List<Integer>> beamSpans(Line line) {
    return line.getSpans().stream()
        .filter(Beam.class::isInstance)
        .map(Beam.class::cast)
        .map(b -> List.of(b.getAnchorElementIndex(), b.getEndElementIndex()))
        .toList();
}
```

Build elements only via `songscribe.dom.StaffElementFactory`. For the cross-line fixture, copy the
`twoLineFixture(...)` shape from `src/test/java/songscribe/ui/selection/CrossLineTieToggleTest.java`.

Phase 4 (`RangeQueriesTieCandidateTest`) already covers refusal; this phase covers what actually
changes.

### Tasks

1. Adjacent same-pitch notes tie (returns true, one tie span over the pair); a second call unties.
2. A barline between the pair → ties across it, the tie spanning the two notes and not the barline;
   a second call removes it.
3. A beam already spanning the pair blocks the add (returns false), but never blocks the remove.
4. Previous line ending on the same pitch → the cross-line tie is created; **a second call removes
   it**.
5. **Chained tie:** `index - 1` is already tied to `index - 2`; tying `index - 1` → `index` succeeds
   and leaves both ties in place.
6. One undo restores the pre-toggle spans. `UndoTestSupport.captureBatch(song, ...)` is the helper
   `CrossLineTieToggleTest` uses for this.
7. Run `./scripts/compile.sh` — SUCCESS — then `./scripts/test.sh` — green.

---

## ✅ Phase 6: EditModeManager arming tests

**Status:** Complete  <br>
**BlockedBy:** 2  <br>
**Files:** src/test/java/songscribe/ui/edit/EditModeManagerTest.java  <br>
**Recommended model/effort:** Sonnet, low effort — two tests mirroring the two beam tests already in the file

Under test:
`MusicEditOperations.toggleTieWithPredecessor(Line line, int elementIndex)` → `boolean`
(added by Phase 2), as seen through `EditModeManager`'s arming state.

In the `LastInsertion` nested class of `src/test/java/songscribe/ui/edit/EditModeManagerTest.java`
there are two existing tests, `testSuccessfulBeamToggleKeepsTheTargetArmed` and
`testRefusedBeamToggleLeavesNoStaleTargetForTheNextEdit` (around lines 876 and 903). They use the
class's `song`/`line` fields, `song.withoutMutationTracking(...)` to seed the predecessor,
`song.withModification(...)` plus `EditModeManager.previewElementDidChange(line, indexOf(element))`
to place the target, then `requireLastInsertion()`, and assert via `assertArmedElementIs(...)` or
`assertThat(EditModeManager.getLastInsertion()).isNull()`.

### Tasks

1. Add `testSuccessfulTieToggleKeepsTheTargetArmed`, mirroring the beam version: place two same-pitch
   notes, assert `MusicEditOperations.toggleTieWithPredecessor(line, target.elementIndex())` is true
   (with an `.as("pre-condition: ...")` description), then `assertArmedElementIs(second)`. The key
   must stay live so the same pair can be untied by pressing `t` again.
2. Add `testRefusedTieToggleLeavesNoStaleTargetForTheNextEdit`, mirroring the beam version: make the
   predecessor a rest (or a differently pitched note) so the toggle returns false, then perform an
   unrelated `song.withModification(...)` edit and assert `EditModeManager.getLastInsertion()` is
   null — the refused toggle left nothing for that edit to adopt.
3. Run `./scripts/compile.sh` — SUCCESS — then `./scripts/test.sh` — green.

---

## ✅ Phase 7: ScoreInputHandler binding tests

**Status:** Complete  <br>
**BlockedBy:** 3  <br>
**Files:** src/test/java/songscribe/ui/component/ScoreInputHandlerTest.java  <br>
**Recommended model/effort:** Sonnet, low effort — copy the `BeamKeyBinding` nested class and swap the key and command

Phase 3 has already registered plain `VK_T` in `ScoreInputHandler.installKeyBindings` and added
`songscribe.message.command.ToggleTieWithPreviousCommand`.

### Tasks

1. In `src/test/java/songscribe/ui/component/ScoreInputHandlerTest.java`, bump
   `final var expectedBindingCount = 12;` at line 1163 to `13`, and update the comment above it —
   currently "7 plain KEY_CODES bindings + shift-Left/Right extension bindings + the three
   last-insertion bindings: plain B, shift-G and plain F" — to name the fourth, plain T.
2. Add a `TieKeyBinding` nested class mirroring `BeamKeyBinding` (same
   `@SuppressWarnings("PackageVisibleInnerClass")` + `@Nested` header, same
   `new ScoreInputHandler(mock(InputHandlerCallback.class))` / `new JPanel()` /
   `handler.installKeyBindings(component)` setup) covering:
   - plain `VK_T` is registered and the `MENU_SHORTCUT_MASK`, `SHIFT_DOWN_MASK` and `ALT_DOWN_MASK`
     variants are not
   - in edit mode the key is consumed and posts `ToggleTieWithPreviousCommand`
   - in select mode, and during grace mode and paste mode, the binding reports itself disabled and
     leaves the key unconsumed, so it can fall through to `TOGGLE_TIE_ACTION`'s root-pane accelerator
3. Run `./scripts/compile.sh` — SUCCESS — then `./scripts/test.sh` — green.

---

## ✅ Phase 8: ScoreViewController handler tests

**Status:** Complete  <br>
**BlockedBy:** 3  <br>
**Files:** src/test/java/songscribe/ui/component/ScoreViewControllerCommandHandlerTest.java  <br>
**Recommended model/effort:** Sonnet, low effort — four tests copied from the beam handler's, swapping the command and operation

Phase 3 has already added
`ScoreViewController.handleToggleTieWithPrevious(ToggleTieWithPreviousCommand message)`, and Phase 2
added `MusicEditOperations.toggleTieWithPredecessor(Line, int)`.

In `src/test/java/songscribe/ui/component/ScoreViewControllerCommandHandlerTest.java` there are four
existing `handleToggleBeamWithPrevious` tests. Each builds
`new ScoreViewController(mock(ScoreView.class), mock(MusicEditOperations.class),
mock(SelectionCoordinator.class), mock(ClipboardManager.class))` and opens
`mockStatic(PlaybackController.class)`, `mockStatic(EditModeManager.class)`,
`mockStatic(MusicEditOperations.class)` and `mockStatic(UIUtils.class)` in a try-with-resources.

### Tasks

1. Add `testHandleToggleTieWithPreviousBeepsAndDoesNothingWhilePlaying`: `PlaybackController::isPlaying`
   returns true → `UIUtils::beep` verified, `EditModeManager::getLastInsertion` verified `never()`.
2. Add `testHandleToggleTieWithPreviousBeepsWhenNoLastInsertion`: not playing,
   `EditModeManager::getLastInsertion` returns null → `UIUtils::beep` verified.
3. Add `testHandleToggleTieWithPreviousTogglesTheTargetWithoutArmingItself`: stub
   `getLastInsertion()` to a `new EditModeManager.Insertion(line, elementIndex)` and
   `MusicEditOperations.toggleTieWithPredecessor(line, elementIndex)` to true → verify the toggle was
   called, `EditModeManager.armInsertion(any(), anyInt())` verified `never()`, and `UIUtils::beep`
   verified `never()`. Carry over the beam version's Javadoc reasoning: arming belongs to the
   toggle's committing branch, because the handler cannot know in advance whether the toggle will
   change the line.
4. Add `testHandleToggleTieWithPreviousBeepsWhenTheToggleRefuses`: same setup, toggle stubbed to
   false → `UIUtils::beep` verified.
5. Run `./scripts/compile.sh` — SUCCESS — then `./scripts/test.sh` — green.

---

## ✅ Phase 9: End-to-end

**Status:** Complete  <br>
**BlockedBy:** 3  <br>
**Files:** src/test/java/songscribe/e2e/ElementInsertionTest.java  <br>
**Recommended model/effort:** Sonnet, medium effort — mirrors the `BeamKey` nested class; e2e fixture handling needs care

Read `.agents/guides/testing-e2e.md` first.

Add a `TieKey` nested class to `src/test/java/songscribe/e2e/ElementInsertionTest.java` beside
`BeamKey`, with the same header shape (`@SuppressWarnings("PackageVisibleInnerClass")`, `@Nested`,
`@Order(...)` — take the next free order value — and
`@TestInstance(TestInstance.Lifecycle.PER_CLASS)`), the same `@BeforeEach reloadFixture()` that calls
`resetSong()` then `loadFixture("insertion")`, and the same real-key dispatch via
`pressKey(KeyEvent.VK_T, 0)` followed by `performLayout(0)`.

### Tasks

1. Add `testTieKeyTogglesTheTieOnTheLastTwoPlacedNotes`: select a duration action, click
   `insertionPoint(0, <staff position>)` twice to place two notes of the same pitch, press
   `KeyEvent.VK_T`, then assert in one `assertAll(...)` that the tie exists over the pair and that
   `isActionEnabled(Actions.TOGGLE_TIE_ACTION)` is false in edit mode — the reason the WHEN_FOCUSED
   binding on the score view has to carry the key. Press `t` again and assert the tie is gone.
2. Add `testTieKeyWorksAfterTypingInTheLyricEditor`, mirroring
   `testBeamKeyWorksAfterTypingInTheLyricEditor`: place the first note, open the lyric editor with
   `LyricEditor.deselectAndOpenOn(scoreView(), song().getLine(0), elementIndex)` inside
   `GuiActionRunner.execute`, `pause()`, set the editor text to a one-syllable string via
   `editor.setText(...)` (never the robot — it maps characters to physical keys and types the wrong
   letters on a non-QWERTY layout), place the second note with a click (that click is what must hand
   focus back), press `t`, and assert both that the tie was created and that exactly one syllable
   with the original text is committed — i.e. the `t` did not land in the lyric.
3. Run `./scripts/compile.sh` — SUCCESS.
4. **Ask the user for approval before running e2e.** With approval, run `./scripts/test.sh e2e` —
   green. Without approval, report the phase complete except for the e2e run and say so explicitly.

---

## ✅ Phase 10: Manual UI verification

**Status:** Complete — verified by the user directly  <br>
**BlockedBy:** —  <br>
**Files:** —  <br>
**Recommended model/effort:** Opus, low effort — drives the user through a checklist; no code changes

### Tasks

1. Ask the user for permission to run the app, then run `./scripts/run.sh`. Do not run it without
   permission.
2. Walk the user through, in edit mode:
   - place two notes of the same pitch, press `t`, confirm the tie draws; press `t` again, confirm it
     clears
   - undo once — the menu reads "Undo Tie" and the span comes back
   - place a barline then a same-pitch note — `t` ties across it
   - place a note at the start of a wrapped line — `t` ties back to the previous line
   - place the last note of a middle line whose next line starts on the same pitch — `t` beeps rather
     than tying forward
   - switch to select mode — `t` still drives the menu action on a selection
3. Report each result faithfully; any mismatch is a defect to fix, not to note and move past.

---

## Verification

1. `./scripts/compile.sh` — SUCCESS.
2. `./scripts/test.sh` — the unit suite green.
3. `./scripts/test.sh e2e` — only with the user's approval.
4. Phase 10's manual checklist — only with the user's approval to run the app.

---

## Follow-up

✅ **Document the last-insertion key family** — done on this branch as
`docs/last-insertion-keys.md`, rather than filed as an issue. Carries the control-flow diagram, the
arming invariant (arm only on committing paths), the disabled-so-the-key-falls-through trick, and
an add-a-fifth-key checklist.

It also records a fix made on this branch that the plan did not anticipate: these keys bypass the
`UIAction` template that sets the Tier-A undo op-name, so they fell through to
`UndoController.opNameKey`'s mutation-kind fallback and labelled the same edit differently from the
menu action — "Undo Beaming" against the menu's "Undo Toggle Beam". `handleLastInsertionCommand`
now takes the op-name and brackets the operation with `UndoController.setPendingOpName`; `b`'s
pre-existing wrong label is fixed alongside `t`'s.
