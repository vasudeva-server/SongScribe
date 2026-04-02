# First-Second Ending: Create, Validate, and Auto-Remove

## Overview

Rework the first-second ending feature to enforce structural validity at creation time, auto-remove endings that become invalid after composition edits, and eliminate the manual remove action.

## Terminology

- **Content element**: A note, rest, or breath mark. Grace notes and glissandos are transparent (skipped during validation but allowed to be present).
- **Terminal element**: A single barline, double barline, final double barline, or left repeat at the end of the selection.
- **Enclosing repeated section**: The musical region that contains the first-second ending. Bounded by a left repeat, left/right repeat, or beginning of composition on the left side.

## Data Model

A first-second ending is stored as a single `EndingInterval` on the line's `IntervalSet<EndingInterval>`. The interval spans from the first element (leading barline/repeat or first content element) through and including the terminal element.

The right repeat within the interval implicitly divides the first ending (before the right repeat) from the second ending (after the right repeat). No separate intervals are needed for first vs. second.

## Create First-Second Ending

### Structural Validation

Run as the first phase of full validation in `menuSelected()`. Apply these checks:

1. The selection contains at least 4 elements (excluding glissandos)
2. The selection contains exactly one `REPEAT_RIGHT`
3. Between the optional leading element and the right repeat: one or more content elements (no barlines or repeats)
4. Between the right repeat and the terminal element: one or more content elements (no barlines or repeats)
5. The selection ends with a terminal element (single barline, double barline, final double barline, or left repeat)

If any check fails, the action is disabled.

### Full Validation

All validation runs in `NotationMenu`'s `MenuListener.menuSelected()` before the menu becomes visible. After the structural validation passes, apply these additional checks:

#### Overlap Check

No element index in the selection range may overlap with any existing `EndingInterval` on the current line. Boundary sharing (same index) counts as overlap.

#### Backward Search

Determines whether the selection is inside a valid enclosing repeated section.

**Starting point:**
- If the selection starts with a left repeat, begin the backward search from the element before that left repeat.
- If the selection starts with a single barline or content element, begin from the element before the selection start.

**Walk:** Iterate backward through elements on the current line. If index 0 is reached, continue from the last element of the previous line, and so on.

**Stop conditions:** The search stops when it encounters any repeat (`REPEAT_LEFT`, `REPEAT_RIGHT`, `REPEAT_LEFT_RIGHT`) or the beginning of the composition (line 0, element 0).

**All other element types are skipped** (notes, rests, breath marks, grace notes, glissandos, barlines of any kind).

**Eligibility:**
- `REPEAT_LEFT` or `REPEAT_LEFT_RIGHT` found: **eligible** (enclosing repeated section exists)
- Beginning of composition reached: **eligible** (implicit repeat from the start)
- `REPEAT_RIGHT` found: **ineligible** (a prior repeated section ended here; there is no enclosing repeat)

**Special case:** If the selection starts at line 0, element 0 (the very beginning of the composition), the selection is **ineligible** because there is no preceding content to form the repeated section body.

### Preceding Element Check

After full validation passes, examine the element immediately before the selection start (which may be on the previous line):

| Preceding element | Action |
|---|---|
| Note, rest, or breath mark | Auto-insert a single barline at the selection start (index 0 of the current line if cross-line, otherwise at the selection start index). The barline becomes part of the ending interval. |
| Single barline or left repeat | Extend the interval start backward to include that element. No insertion needed. |
| Right repeat, left/right repeat, double barline, or final double barline | **Selection is invalid.** Do not create the ending. |
| Beginning of composition (line 0, element 0) | Invalid (covered by the special case above). |

### Interval Creation

After all checks pass and any barline insertion/interval extension is done:

1. Create an `EndingInterval(start, end, 1)` where `start` and `end` are the final interval bounds (inclusive of leading barline/repeat and terminal element).
2. Add it to the line's `firstSecondEndings` IntervalSet.
3. Mark the composition as modified.

**Index shifting on barline insertion:** If a barline is auto-inserted (preceding element is a content element), the insertion shifts all subsequent element indices by +1. Verify whether the element insertion path already calls `IntervalSet.shiftValues()` on the line's interval sets. If not, call it explicitly after the insertion and before creating the new `EndingInterval`.

## Action Architecture

### Static Instances

`FirstSecondEndingAction` stores a static singleton for the create action:

```java
public static final FirstSecondEndingAction MAKE_ENDING_ACTION = new FirstSecondEndingAction();
```

`NotationMenu` references this static field when building the menu.

### Flags

Remove `REQUIRES_MULTIPLE_SELECTION` and `DISABLE_IN_REST_MODE`. Keep:
- `DISABLE_WHEN_PLAYING`
- `DISABLE_WHEN_EDITING_TEXT`
- `DISABLE_IN_GRACE_MODE`

### Enable/Disable Flow

All validation runs in a single pass when `NotationMenu`'s `MenuListener.menuSelected()` fires, before the menu becomes visible:

1. Call `updateEnabledState()` for flag checks. If flags fail, the action is disabled — done.
2. If flags pass, run full validation (structural check + overlap check + backward search + preceding element check). Update the action's enabled state.

No override of `musicSelectionDidChange()` is needed. The base `UIAction` flag handling is sufficient between menu opens.

### Validation Location

All validation logic lives in `MusicEditOperations.canMakeFirstSecondEnding()` (rewritten from the current stub). Called via `Score.canMakeFirstSecondEnding()` from the menu listener.

### Validation Result Object

`canMakeFirstSecondEnding()` returns a result object (e.g., `EndingValidationResult`) rather than a boolean. The result encodes:

- Whether validation passed
- The preceding element action: `INSERT_BARLINE`, `EXTEND_INTERVAL`, or `NONE`
- The final interval bounds (after any extension)

`makeFirstSecondEnding()` accepts this result to avoid re-deriving the preceding element logic.

## Remove First-Second Ending

### Manual Remove Action: Eliminated

Remove `createRemoveEndingAction()` and its menu item from `NotationMenu`. The remove action factory and command handling for removal can be deleted. Simplify `FirstSecondEndingCommand` to a no-arg command (remove the `makeEnding` boolean).

### Auto-Removal on Structural Changes

When a `CompositionDidChangeNotification` is received with a structural change type (element insertion, deletion, or type change), iterate all `EndingInterval`s on the affected line(s) and re-validate each one using the structural validation rules.

**If the entire interval has been removed** (e.g., all elements deleted): remove the interval silently.

**If the interval still exists but is structurally invalid:**

1. Show an alert: *"One or more first-second endings are no longer valid and will be removed."* Show this alert at most once per modification event, even if multiple endings are invalidated.

2. For each invalid ending, perform cleanup:
   a. If the interval starts with a `SINGLE_BARLINE`, remove it.
   b. Remove everything from the right repeat onward through the terminal element (the right repeat itself, all second ending content, and the terminal element).
   c. **Exception:** If the terminal element was a `REPEAT_LEFT`, change the right repeat into a `REPEAT_LEFT_RIGHT` instead of removing it. This preserves the repeat structure for the next section.
   d. Remove the `EndingInterval` from the line's IntervalSet.

3. Mark the composition as modified.

### Modification Bracket

The cleanup performs multiple structural edits (element removal, barline insertion, interval removal) that would each individually trigger a `CompositionDidChangeNotification`. Instead of a simple re-entrancy guard, use a `beginModification()`/`endModification()` bracket on `Composition`:

```
composition.beginModification()
    // ... all cleanup edits happen here ...
    // intermediate setModified() and notification posts are suppressed
composition.endModification()
    // fires a single coalesced CompositionDidChangeNotification
```

**API:**

- `beginModification()` — increments a depth counter. Stub: creates a checkpoint comment (`// TODO: snapshot composition state here for undo grouping`).
- `endModification()` — decrements the depth counter. When it reaches zero, calls `setModified(true)` and posts a single `CompositionDidChangeNotification` with the union of all change types accumulated during the bracket.
- While inside a bracket (depth > 0), `setModified()` defers and notification posting is suppressed. Change types are accumulated into an `EnumSet`.

This pattern:
1. Solves re-entrancy (intermediate notifications are suppressed, so the auto-removal handler cannot re-trigger itself).
2. Provides the natural hook for atomic undo grouping when an undo system is implemented.
3. Is reusable by any multi-step edit operation, not just auto-removal.

### File Load Guard

Auto-removal listens for `CompositionDidChangeNotification` with structural change types. During file deserialization, loading a composition with pre-existing (potentially stale) endings could trigger the auto-removal handler before the composition is fully loaded. Ensure auto-removal is either:
- Not subscribed until after the composition is fully loaded, or
- Gated by a "loading" flag on the composition that suppresses auto-removal during deserialization.

## Summary of Changes

### Files to Modify

| File | Changes |
|---|---|
| `FirstSecondEndingAction.java` | Add static singleton, remove `REQUIRES_MULTIPLE_SELECTION` and `DISABLE_IN_REST_MODE` flags, remove `createRemoveEndingAction()` |
| `MusicEditOperations.java` | Rewrite `canMakeFirstSecondEnding()` with full validation (structural + overlap + backward search + preceding element checks), rewrite `makeFirstSecondEnding()` to include barline insertion and interval extension, remove `removeFirstSecondEnding()`, add auto-removal cleanup logic |
| `Score.java` | Update delegation methods to match new `MusicEditOperations` API |
| `NotationMenu.java` | Remove the remove ending menu item, add `MenuListener` for full validation on menu open, reference static action instance |
| `Composition.java` | Add `beginModification()`, `endModification()`, depth counter, accumulated change types, and deferred notification posting |
| `ScoreMessageCoordinator.java` | Remove handling for `makeEnding=false`, add `CompositionDidChangeNotification` handler for auto-removal (using modification bracket) |
| `FirstSecondEndingCommand.java` | Remove `makeEnding` boolean — becomes a no-arg command |
| `strings.properties` | Add alert string for auto-removal notification, remove dead strings: `action.ending.remove`, `confirm.ending.no.repeat`, `confirm.title.first.second.ending` |

### Files to Delete (or Dead Code to Remove)

| Item | Reason |
|---|---|
| `createRemoveEndingAction()` factory method | Manual remove action eliminated |
| `removeFirstSecondEnding()` in `MusicEditOperations` | Replaced by auto-removal |
| `FirstSecondEndingCommand.makeEnding` field + `isMakeEnding()` | Only create action remains; boolean is dead |
| Remove ending menu item in `NotationMenu` | No longer needed |
| `action.ending.remove`, `confirm.ending.no.repeat`, `confirm.title.first.second.ending` in `strings.properties` | Dead strings after removal of remove action and old confirmation dialog |
