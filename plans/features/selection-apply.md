# Apply Toolbar Actions to Selection

**Issue:** #81
**Branch:** `feat/reflect-toolbar`
**Depends on:** #80 (toolbar reflection from selection)
**Spec:** `specs/issue-81-selection-apply.md`

## Status Dashboard

| Phase | Description | Status | Sub-plan |
|-------|-------------|--------|----------|
| 1 | [Model Foundations](#-phase-1-model-foundations) | ✅ Done | — |
| 2 | [Reflectable.applyToNote](#-phase-2-reflectableapplytonote) | ✅ Done | — |
| 3 | [NoteTypeAction Fixes](#-phase-3-notetypeaction-fixes) | ✅ Done | — |
| 4 | [Selection Content Detection](#-phase-4-selection-content-detection) | ✅ Done | — |
| 5 | [Enable/Disable Chain](#-phase-5-enabledisable-chain) | ✅ Done | — |
| 6 | [Expanded Saved State](#-phase-6-expanded-saved-state) | ✅ Done | — |
| 7 | [reflectSelection Simplification](#-phase-7-reflectselection-simplification) | ✅ Done | — |
| 8 | [Action Intercept Flow](#-phase-8-action-intercept-flow) | ✅ Done | — |
| 9 | [Batch Mutation](#-phase-9-batch-mutation) | ✅ Done | — |
| 10 | [Post-Mutation Interval Validation](#-phase-10-post-mutation-interval-validation) | ✅ Done | — |
| 11 | [Integration and E2E Tests](#-phase-11-integration-and-e2e-tests) | ✅ Done | — |

## ✅ Phase 1: Model Foundations

Data model changes that have no behavioral impact until wired in later phases.

### 1.1 `NoteType.toRest()` and `NoteType.toNote()`

Add two switch-expression methods to `NoteType` enum:

- `toRest()` — returns the rest equivalent for the 6 note types (SEMIBREVE through DEMI_SEMIQUAVER), `this` for all others (grace notes, glissando, barlines, etc.)
- `toNote()` — returns the note equivalent for the 6 rest types, `this` for all others

### 1.2 `Note` Copy Constructor

Add `Note(NoteType targetType, Note source)` — creates a note of `targetType`, copying only applicable attributes from `source`:

- **Always copy:** `dotCount`, `fermata`, `tempoChange`, `beatChange`, `annotation`, `syllableMovement`, `syllableRelationMovement`, `forceSyllable`, `line`
- **Copy only if `targetType.isNote()`:** `accidental`, `isAccidentalInParentheses`, `glissando`, `forceArticulation`, `durationArticulation`, `trill`, `upper`, `stemDirectionAuto`, `staffPosition`, `articulations` (deep-copied)
- **Rest target:** set `staffPosition` to `targetType.getDefaultStaffPosition()`
- Call `setParentLine(source.getParentLine())` at the end

This uses a whitelist strategy — new attributes added to `Note` in the future default to missing (visible, safe) rather than stale (invisible, potentially corrupt).

### 1.3 `Line.replaceNoteQuietly(int, Note)`

Add a package-private method that replaces a note at the given index without posting `LayoutChangeMessage`. Sets the note's line reference and updates the `notes` list. The caller is responsible for posting a single `LayoutChangeMessage` after batch operations.

### 1.4 Tests

- `NoteType`: `toRest()`/`toNote()` for all 6 note/rest pairs, plus grace notes, glissando, barlines returning `this`
- `Note` copy constructor: note-to-note (different duration), note-to-rest (whitelist check, staff position reset), rest-to-note, rest-to-rest


## ✅ Phase 2: `Reflectable.applyToNote`

Add `applyToNote(Note note, boolean selected)` to the `UIAction.Reflectable` interface and implement it in all reflectable action classes.

### 2.1 Interface Change

Add to `UIAction.Reflectable`:
```java
void applyToNote(Note note, boolean selected);
```

`selected == true` means apply the attribute; `false` means remove it.

### 2.2 Implementations

| Action Class | `applyToNote(note, selected)` |
|---|---|
| `DotAction` | `note.setDotCount(selected ? dotLevel.ordinal() + 1 : 0)` |
| `AccidentalAction` | `note.setAccidental(selected ? accidental : Note.Accidental.NONE)` |
| `AccidentalInParensAction` | `note.setAccidentalInParentheses(selected)` |
| `ForceArticulationAction` | `note.setForceArticulation(selected ? articulation : null)` |
| `DurationArticulationAction` | `note.setDurationArticulation(selected ? articulation : null)` |
| `FermataAction` | `note.setFermata(selected)` |
| `NoteTypeAction` | `if (!selected) return;` — duration buttons don't un-apply. Compute target type but actual replacement is handled by the coordinator in Phase 7 |

### 2.3 Tests

Each action class: `applyToNote` with `selected == true` (apply) and `selected == false` (remove).


## ✅ Phase 3: `NoteTypeAction` Fixes

### 3.1 Fix `appliesTo` for `DURATION` Kind

Current implementation returns `true` for ALL note types including barlines when `kind == DURATION`. Fix to return `note.getNoteType().isDuration()`.

For `NON_DURATION` kind, return `note.getNoteType().isNonDuration()`.

### 3.2 Add `createReplacement(Note, boolean)`

Creates a replacement note preserving note/rest kind:
- If `!selected`, return `null` (duration buttons don't un-apply)
- Compute `targetType`: if original is rest, use `type.toRest()`; otherwise `type.toNote()`
- Return `new Note(targetType, note)` using the Phase 1 copy constructor

### 3.3 Tests

- `appliesTo` returns `false` for barlines when `kind == DURATION`
- `createReplacement` preserves note/rest kind
- `createReplacement` returns `null` when `selected == false`
- `createReplacement` with grace note (returns note with same type since `toNote()`/`toRest()` returns `this`)


## ✅ Phase 4: Selection Content Detection

Add lazy-cached content queries to `SelectionCoordinator` so the enable/disable chain can ask what the selection contains.

### 4.1 Content Cache

Add fields:
- `NoteSelection contentCacheSelection` — the selection the cache was computed for
- `boolean cachedHasDurations`
- `boolean cachedHasNonDurations`

Add `ensureContentComputed()` — iterates the selection, sets the two flags. Short-circuits when both are `true`. Cache is invalidated when the selection object changes (identity/equality check).

### 4.2 Public Query Methods

- `selectionHasDurations()` — calls `ensureContentComputed()`, returns `cachedHasDurations`
- `isApplicableToSelection(UIAction.Reflectable action)` — iterates selection, returns `true` if `action.appliesTo(note)` for any note

### 4.3 Tests

- No active selection: queries return appropriate defaults
- Selection with only durations: `selectionHasDurations() == true`
- Selection with only non-durations: `selectionHasDurations() == false`
- `isApplicableToSelection` with applicable action returns `true`
- `isApplicableToSelection` with inapplicable action returns `false` (e.g., `AccidentalAction` with rest-only selection)


## ✅ Phase 5: Enable/Disable Chain

Update `UIAction.updateEnabledState()` to handle selection content correctly, including mutual exclusivity between note actions and non-duration actions.

### 5.1 Rename `enableFromSelection(Score)` to `enableFromSelectionSize(Score)`

Use Serena `rename_symbol` to rename the existing method. It checks selection size requirements — the new name clarifies its purpose.

### 5.2 New `enableFromSelection()`

A new method (no parameters) that handles selection content:
- If no active selection, return `true`
- If `this instanceof Reflectable`, delegate to `coordinator.isApplicableToSelection(reflectable)`
- If `hasFlag(DISABLE_WHEN_BAR_SELECTED)`, delegate to `coordinator.selectionHasDurations()`
- Otherwise return `true`

### 5.3 Defer Guards

During active selection, `enableFromBarSelection()` and `enableFromDurationSelection()` must return `true` (defer). Their existing logic is based on toolbar state, which reflects the selection during selection mode — making them stale/wrong. The new `enableFromSelection()` handles content correctly.

Add `if (hasActiveSelection()) return true;` at the top of both methods.

### 5.4 Update `updateEnabledState` Chain

Insert `enableFromSelection()` into the chain after `enableFromBarSelection()` and before `enableFromDurationSelection()`:

```
enableInAdjustmentMode && enableFromTextEditingState && enableFromPlaybackState
    && enableInRestMode && enableFromSelectionSize && enableFromBarSelection
    && enableFromSelection && enableFromDurationSelection && enableFromCompositionState
```

### 5.5 Add `DISABLE_WHEN_BAR_SELECTED` to Non-Reflectable Note Actions

In `Actions.java`, add the flag to: `TOGGLE_BEAM_ACTION`, `TOGGLE_TIE_ACTION`, tuplet actions, `FLIP_STEM_DIRECTION_ACTION`, `ADD_CRESCENDO_ACTION`, `ADD_DIMINUENDO_ACTION`, `TEMPO_CHANGE_ACTION`, `KEY_SIGNATURE_CHANGE_ACTION`.

This flag serves double duty: its existing purpose (disable when a barline is the insertion point) and as a marker for `enableFromSelection()` to identify note-only actions during selection.

### 5.6 Tests

- `enableFromSelection`: no selection returns `true`; reflectable + applicable returns `true`; reflectable + inapplicable returns `false`; non-reflectable with flag + durations returns `true`; non-reflectable with flag + non-durations returns `false`
- `enableFromBarSelection`/`enableFromDurationSelection`: active selection returns `true` (defer); no selection preserves original behavior


## ✅ Phase 6: Expanded Saved State

The saved pre-selection state must capture both `isSelected()` and `isEnabled()` since mutual exclusivity may disable actions during selection.

### 6.1 `ActionState` Record

Add to `SelectionCoordinator`:
```java
record ActionState(boolean selected, boolean enabled) {}
```

Replace the existing `Map<UIAction, Boolean> savedActionStates` (or equivalent) with `Map<UIAction, ActionState>`.

### 6.2 `getManagedActions()`

Discover all actions that need save/restore: reflectable actions plus non-reflectable actions that have `DISABLE_WHEN_BAR_SELECTED`. These are the actions whose enabled state may change during selection.

### 6.3 Save/Restore

- **Save** (selection becomes non-empty): capture `ActionState(action.isSelected(), action.isEnabled())` for all managed actions
- **Restore** (selection cleared): restore both `setSelected` and `setEnabled` from saved state, then clear the map

### 6.4 Tests

- Save/restore round-trip preserves both selected and enabled states


## ✅ Phase 7: `reflectSelection` Simplification

With `enableFromSelection()` handling all enable/disable decisions in the flag chain, simplify `reflectSelection` to only set selected state on reflectable actions. It no longer touches enabled state.

### 7.1 Changes

- Remove any enable/disable logic from `reflectSelection`
- The handler still: saves state on first selection, restores on clear, skips unchanged ranges, and iterates reflectable actions to set `action.setSelected(applicable && matched)`
- Enable/disable is now entirely driven by `updateEnabledState()` which is called by the existing flag-chain mechanism

### 7.2 Tests

- Verify reflectSelection only affects selected state, not enabled state
- Verify enable/disable is handled by the flag chain


## ✅ Phase 8: Action Intercept Flow

Wire the selection apply behavior into action handlers so clicking a toolbar button during active selection applies to all selected notes.

### 8.1 `UIAction.applyToSelectionIfActive()`

Add a protected method to `UIAction`:
- If `!(this instanceof Reflectable)`, return `false`
- Get the selection from the coordinator; if null, return `false`
- Call `coordinator.applyActionToSelection(reflectable, isSelected())`
- Return `true`

The caller checks the return value — if `true`, skip normal insertion-mode flow.

### 8.2 `NoteTypeAction.actionPerformed`

After `doActionPerformed(e)` returns `true` (state changed), add: `if (applyToSelectionIfActive()) return;`

If `doActionPerformed` returns `false` (button already selected, no state change), the mutation is correctly skipped — the selection already matches.

### 8.3 `InsertionNoteAction.actionPerformed`

After `super.actionPerformed(e)` (which toggles selected state), add: `if (applyToSelectionIfActive()) return;`

This skips `UpdateInsertionNoteMessage` during selection — the existing save/restore mechanism handles toolbar state when the selection is cleared.

### 8.4 Tests

- Selection active + reflectable action: intercept returns `true`, normal flow skipped
- No selection: intercept returns `false`, normal flow proceeds
- Non-reflectable action: intercept returns `false`


## ✅ Phase 9: Batch Mutation

Implement `SelectionCoordinator.applyActionToSelection()` — the method that performs the actual note modifications.

### 9.1 `applyActionToSelection(UIAction.Reflectable, boolean)`

- Iterate from `selection.begin()` to `selection.end()`
- For each note where `action.appliesTo(note)`:
  - If `action instanceof NoteTypeAction`: call `createReplacement()`, then `line.replaceNoteQuietly(i, replacement)`; set `needsIntervalCleanup = true`
  - Otherwise: call `action.applyToNote(note, selected)` (mutates in place)
- After the loop, if `needsIntervalCleanup`, call `validateIntervals()` (Phase 10)
- Mark composition modified: `line.getComposition().setModified(true)`
- Post a single `LayoutChangeMessage.scoreContent(line)`

### 9.2 Tests

- Apply duration change to mixed notes/rests — verify kind preservation and note replacement
- Apply accidental to selection with rests — verify rests skipped
- Apply fermata ON then OFF — verify toggle semantics
- Verify single `LayoutChangeMessage` posted (not N messages)
- Selection remains active after applying changes


## ✅ Phase 10: Post-Mutation Interval Validation

After batch note replacement, beam, tie, and tuplet intervals may reference incompatible notes. Validate and repair them.

### 10.1 `validateIntervals(Line, int, int)`

Delegates to three sub-methods scanning the affected range.

### 10.2 Beam Interval Validation

A note that changes from beamable (quaver, semiquaver, demisemiquaver) to non-beamable invalidates any beam interval containing it. Strategy:
- Find overlapping beam intervals in the affected range
- For each, check if all notes are still beamable
- If a non-beamable note is found: split the beam around it
- If a resulting sub-group has < 2 beamable notes, dissolve it

### 10.3 Tie Interval Validation

A note that becomes a rest cannot be tied. Remove it from any tie interval. If the tie has < 2 notes remaining, dissolve it.

### 10.4 Tuplet Interval Validation

A note that becomes a non-duration cannot be in a tuplet. Remove it from any tuplet interval. If the tuplet has < 2 notes remaining, dissolve it.

### 10.5 Tests

- **Beam:** non-beamable at start/end/middle of group (shrink, split, dissolve); all non-beamable (dissolve); group of 2 with one non-beamable (dissolve); group of 5 with middle non-beamable (split into [0,1] and [3,4])
- **Tie:** note becomes rest, remove from tie; tie of 2, one becomes rest, dissolve
- **Tuplet:** note becomes non-duration, remove; tuplet of 2, one becomes non-duration, dissolve


## ✅ Phase 11: Integration and E2E Tests

### 11.1 Integration Tests

- Mutual exclusivity: select only notes -> barline actions disabled; select only barlines -> note actions disabled; select mixed -> both disabled
- Selection cleared: all actions restored to pre-selection state (both selected and enabled)
- Apply action -> selection remains active
- Beamed eighths changed to quarters -> beam intervals repaired
- Playback starts during selection -> actions disabled; stops -> re-enabled per selection content

### 11.2 E2E Tests

- Select notes, click duration button, verify notes changed
- Select notes, click accidental, verify applied
- Select notes + rests, click dot, verify both get dots
- Select notes + barlines, verify mutual exclusivity disabling
