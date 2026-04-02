# First-Second Ending Implementation Plan

**Created:** 2026-03-31
**Spec:** [first-second-ending.md](./first-second-ending.md)

## Status Dashboard

| Phase | Description | Status | Sub-plan |
|-------|-------------|--------|----------|
| 1 | [Validation Engine](#-phase-1-validation-engine) | ✅ Done | — |
| 2 | [Action and Menu Refactoring](#-phase-2-action-and-menu-refactoring) | ✅ Done | — |
| 3 | [Create Logic](#-phase-3-create-logic) | ✅ Done | — |
| 4 | [Modification Bracket](#-phase-4-modification-bracket) | ✅ Done | — |
| 5 | [Auto-Removal](#-phase-5-auto-removal) | ✅ Done | — |
| 6 | [Dead Code Cleanup](#-phase-6-dead-code-cleanup) | ✅ Done | — |

---

## ✅ Phase 1: Validation Engine

**Status:** Done  <br>
**BlockedBy:** —

### Context

`MusicEditOperations.canMakeFirstSecondEnding()` is currently a stub that returns `true`. The spec requires full structural validation, overlap checking, backward search for enclosing repeated sections, and preceding element analysis — all before the menu item becomes visible.

### Tasks

1. Create `EndingValidationResult` class in `songscribe.music`:
   - `boolean valid`
   - Enum for preceding element action: `INSERT_BARLINE`, `EXTEND_INTERVAL`, `NONE`
   - Final interval bounds (start, end) after any extension
   - Factory methods: `EndingValidationResult.invalid()`, `EndingValidationResult.valid(action, start, end)`

2. Add helper predicates to `ElementType` (if not already present):
   - `isContentElement()` — note, rest, or breath mark (not grace note, not glissando)
   - `isTransparent()` — grace note or glissando (skipped during validation but allowed)
   - `isTerminal()` — single barline, double barline, final double barline, or left repeat

3. Rewrite `MusicEditOperations.canMakeFirstSecondEnding()` to accept selection state and return `EndingValidationResult`. Implement the four validation stages in order:

   **a. Structural validation:**
   - Selection has >= 4 elements (excluding glissandos)
   - Exactly one `REPEAT_RIGHT` in selection
   - Between optional leading element and right repeat: one or more content elements, no barlines or repeats
   - Between right repeat and terminal: one or more content elements, no barlines or repeats
   - Selection ends with a terminal element

   **b. Overlap check:**
   - No element index in selection range overlaps any existing `EndingInterval` on the line

   **c. Backward search:**
   - Starting point depends on whether selection starts with left repeat, single barline, or content element
   - Walk backward across lines looking for any repeat or beginning of composition
   - `REPEAT_LEFT` or `REPEAT_LEFT_RIGHT` found: eligible
   - Beginning of composition (line 0, element 0) reached: eligible
   - `REPEAT_RIGHT` found: ineligible
   - Special case: selection starts at line 0, element 0: ineligible

   **d. Preceding element check:**
   - Note/rest/breath mark: action = `INSERT_BARLINE`, extend interval start
   - Single barline or left repeat: action = `EXTEND_INTERVAL`, extend interval start backward
   - Right repeat, left/right repeat, double barline, or final double barline: invalid
   - Beginning of composition: invalid (covered by special case)

4. Update `Score.canMakeFirstSecondEnding()` to pass selection state through and return `EndingValidationResult`

5. Compile and run unit tests

### Files to Modify

| File | Changes |
|------|---------|
| `songscribe/music/EndingValidationResult.java` | **New.** Result object with validity, action, and bounds |
| `songscribe/music/ElementType.java` | Add `isContentElement()`, `isTransparent()`, `isTerminal()` if needed |
| `songscribe/music/MusicEditOperations.java` | Rewrite `canMakeFirstSecondEnding()` with full validation |
| `songscribe/ui/component/Score.java` | Update delegation signature |

---

## ✅ Phase 2: Action and Menu Refactoring

**Status:** Done  <br>
**BlockedBy:** Phase 1

### Context

`FirstSecondEndingAction` currently uses factory methods for make/remove and carries a `makeEnding` boolean. `NotationMenu` adds both actions. The spec eliminates the remove action entirely and changes the make action to a static singleton validated on menu open.

Current state:
- `FirstSecondEndingAction` has `createMakeEndingAction()` and `createRemoveEndingAction()` factories
- Flags include `REQUIRES_MULTIPLE_SELECTION` and `DISABLE_IN_REST_MODE` (to be removed)
- `NotationMenu` adds both make and remove menu items without a `MenuListener`
- `FirstSecondEndingCommand` carries `makeEnding` boolean

### Tasks

1. Refactor `FirstSecondEndingAction`:
   - Replace factory methods with a static singleton: `public static final FirstSecondEndingAction MAKE_ENDING_ACTION`
   - Remove `makeEnding` field
   - Remove `REQUIRES_MULTIPLE_SELECTION` and `DISABLE_IN_REST_MODE` flags
   - Keep `DISABLE_WHEN_PLAYING`, `DISABLE_WHEN_EDITING_TEXT`, `DISABLE_IN_GRACE_MODE`
   - Simplify `actionPerformed()` to post `new FirstSecondEndingCommand()` (no boolean)
   - Add a field to hold the cached `EndingValidationResult` for use by the command handler
   - Add a public method (e.g., `validate(Score)`) called from the menu listener that runs `Score.canMakeFirstSecondEnding()` and updates enabled state + caches the result

2. Simplify `FirstSecondEndingCommand`:
   - Remove `makeEnding` field and `isMakeEnding()` method
   - Becomes a no-arg command

3. Update `NotationMenu`:
   - Remove the remove ending menu item (`FirstSecondEndingAction.createRemoveEndingAction()`)
   - Reference `FirstSecondEndingAction.MAKE_ENDING_ACTION` for the make menu item
   - Add a `MenuListener` (via `addMenuListener`) that calls `MAKE_ENDING_ACTION.validate(score)` in `menuSelected()` to run full validation and enable/disable the action before the menu becomes visible

4. Update `ScoreMessageCoordinator.handleFirstSecondEnding()`:
   - Remove the `isMakeEnding()` branch — only call `operations.makeFirstSecondEnding()`
   - Remove `operations.removeFirstSecondEnding()` call

5. Compile and run unit tests

### Files to Modify

| File | Changes |
|------|---------|
| `songscribe/ui/action/FirstSecondEndingAction.java` | Static singleton, remove flags, validation method |
| `songscribe/message/command/FirstSecondEndingCommand.java` | Remove `makeEnding` boolean |
| `songscribe/ui/menu/NotationMenu.java` | Remove remove item, add `MenuListener` |
| `songscribe/ui/component/ScoreMessageCoordinator.java` | Remove `isMakeEnding()` branch |

---

## ✅ Phase 3: Create Logic

**Status:** Done  <br>
**BlockedBy:** Phases 1, 2

### Context

`MusicEditOperations.makeFirstSecondEnding()` currently has minimal logic: it checks for a right repeat (with a confirm dialog fallback), then blindly creates an `EndingInterval` from selection bounds. The spec requires it to use the `EndingValidationResult` from Phase 1 to handle barline insertion, interval extension, and proper interval creation.

### Tasks

1. Rewrite `MusicEditOperations.makeFirstSecondEnding()` to accept `EndingValidationResult`:
   - If result action is `INSERT_BARLINE`:
     - Insert a single barline at the appropriate position
     - Verify whether the element insertion path calls `IntervalSet.shiftValues()` on the line's interval sets; if not, call it explicitly after insertion
     - Adjust interval bounds for the +1 index shift
   - If result action is `EXTEND_INTERVAL`:
     - Use the extended start from the result (the preceding barline/repeat is included)
   - Create `EndingInterval(start, end, 1)` with the final bounds
   - Add to the line's `firstSecondEndings` IntervalSet
   - Mark composition as modified

2. Remove the old confirm dialog logic (`CONFIRM_ENDING_NO_REPEAT`, `CONFIRM_TITLE_FIRST_SECOND_ENDING`)

3. Compile and run unit tests

### Files to Modify

| File | Changes |
|------|---------|
| `songscribe/music/MusicEditOperations.java` | Rewrite `makeFirstSecondEnding()` |

---

## ✅ Phase 4: Modification Bracket

**Status:** Done  <br>
**BlockedBy:** —

### Context

The auto-removal cleanup (Phase 5) performs multiple structural edits that would each individually trigger a `CompositionDidChangeNotification`. The spec calls for a `beginModification()`/`endModification()` bracket on `Composition` to suppress intermediate notifications and coalesce them into a single post.

### Tasks

1. Add modification bracket API to `Composition`:
   - `private int modificationDepth` field
   - `private EnumSet<ChangeType> accumulatedChangeTypes` field
   - `private Line accumulatedLine` field (for single-line changes; null if multi-line)
   - `beginModification()` — increments depth counter
   - `endModification()` — decrements depth; when it reaches zero, calls `setModified(true)` and posts a single `CompositionDidChangeNotification` with the union of accumulated change types
   - Modify `setModified()` to defer when depth > 0
   - Modify `postChanged()` (or `mutateAndPost`) to accumulate change types and suppress notification posting when depth > 0

2. Add a stub comment for future undo grouping: `// TODO: snapshot composition state here for undo grouping`

3. Compile and run unit tests

### Files to Modify

| File | Changes |
|------|---------|
| `songscribe/music/Composition.java` | Add `beginModification()`, `endModification()`, depth counter, accumulated change types, deferred posting |

---

## ✅ Phase 5: Auto-Removal

**Status:** Done  <br>
**BlockedBy:** Phases 1, 4

### Context

When a structural change occurs (element insertion, deletion, or type change), all `EndingInterval`s on the affected line(s) must be re-validated. Invalid endings are auto-removed with cleanup logic that includes barline removal, second ending content removal, and interval removal.

### Tasks

1. Add structural re-validation method to `MusicEditOperations` (e.g., `revalidateEndings(Line)`):
   - Iterate all `EndingInterval`s on the line
   - Re-run the structural validation rules from Phase 1 against each interval's current elements
   - If the entire interval has been removed (all elements deleted): remove silently
   - If structurally invalid, add to a list of intervals to remove

2. Add cleanup method to `MusicEditOperations` (e.g., `removeInvalidEnding(Line, EndingInterval)`):
   - If interval starts with `SINGLE_BARLINE`, remove it
   - Remove everything from the right repeat onward through the terminal element
   - **Exception:** If terminal is `REPEAT_LEFT`, change the right repeat into `REPEAT_LEFT_RIGHT` instead
   - Remove the `EndingInterval` from the line's IntervalSet

3. Add auto-removal handler in `ScoreMessageCoordinator`:
   - Listen for `CompositionDidChangeNotification` with `ChangeType.CONTENT` or `ChangeType.STRUCTURE`
   - Call revalidation on affected line(s)
   - If any invalid endings found:
     - Show alert (at most once per event): "One or more first-second endings are no longer valid and will be removed."
     - Wrap all cleanup in `composition.beginModification()` / `endModification()` bracket
     - Perform cleanup for each invalid ending

4. Add file load guard:
   - Ensure auto-removal is not triggered during file deserialization
   - Either gate by a "loading" flag on `Composition`, or subscribe after load completes

5. Compile and run unit tests

### Files to Modify

| File | Changes |
|------|---------|
| `songscribe/music/MusicEditOperations.java` | Add `revalidateEndings()`, `removeInvalidEnding()` |
| `songscribe/ui/component/ScoreMessageCoordinator.java` | Add auto-removal handler on structural changes |
| `songscribe/music/Composition.java` | Add loading flag if needed |
| `strings.properties` | Add alert string for auto-removal notification |

---

## ✅ Phase 6: Dead Code Cleanup

**Status:** Done  <br>
**BlockedBy:** Phases 2, 3, 5

### Context

After all phases are complete, several methods, fields, and strings are dead and should be removed.

### Tasks

1. Remove `MusicEditOperations.removeFirstSecondEnding()`

2. Remove dead strings from `strings.properties`:
   - `action.ending.remove`
   - `confirm.ending.no.repeat`
   - `confirm.title.first.second.ending`

3. Verify no remaining references to removed code:
   - `FirstSecondEndingAction.createMakeEndingAction()` / `createRemoveEndingAction()`
   - `FirstSecondEndingCommand.isMakeEnding()`
   - `Score.removeFirstSecondEnding()` (if it existed — it does not currently)

4. Compile and run full unit tests

### Files to Modify

| File | Changes |
|------|---------|
| `songscribe/music/MusicEditOperations.java` | Remove `removeFirstSecondEnding()` |
| `strings.properties` | Remove 3 dead keys |
