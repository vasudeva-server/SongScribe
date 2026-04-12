# ChangeType Elimination & Interval-Operation Mutation Migration

**Type:** Master  <br>
**Created:** 2026-04-12  <br>
**Status:** Planned  <br>
**BlockedBy:** --  <br>
**Depends on:** `mutation-hierarchy-refactor.md` (done)

---

## Context

The mutation-hierarchy refactor (issue #280) migrated all composition-level
setters and most line-level mutations off the legacy `ChangeType` enum onto
the sealed `Mutation` hierarchy. One class of operations is **still unmigrated**:
the interval-set and per-element-field edits driven by
`songscribe.music.MusicEditOperations`. These sites call
`composition.setModified(true)` directly, and `ScoreMessageCoordinator` follows
up by posting a `CompositionDidChangeNotification(ChangeType.CONTENT, ...)` to
trigger layout invalidation.

The original plan's "Deferred Work" section explicitly parked the range /
interval mutation types until a subscriber needed them (plans/mutation-hierarchy-refactor.md:899).
That deferral leaves three pieces of technical debt in the codebase:

1. **`ChangeType` enum, three deprecated constructors, and `legacyChangeTypes`
   field** on `CompositionDidChangeNotification.java` — a half-migrated API
   where `hasChangeType()` returns `false` for any new-style notification and
   `getMutations()` returns `[]` for any legacy-style notification. No single
   caller currently hits a false-negative hazard, but the split API is fragile:
   any new mutation type that affects line layout must remember to implement
   `LineScopedMutation` or be added to `hasLineLayoutMutation`'s `instanceof`
   list, otherwise it silently stops invalidating layout.
2. **`ScoreMessageCoordinator.hasChangeType(ChangeType.CONTENT) || hasLineLayoutMutation(message)`
   compound check** in `compositionDidChange` — the `CONTENT` branch exists
   solely to cover the unmigrated operations below. It is the only remaining
   reader of `hasChangeType`.
3. **`SelectionCoordinator.applyActionToSelection` and `MusicEditOperations.*`**
   bypass the mutation system entirely: they mutate state, call
   `composition.setModified(true)`, and (via `ScoreMessageCoordinator`) post a
   raw `CompositionDidChangeNotification(ChangeType.CONTENT, ...)`.

This plan eliminates all three by introducing fine-grained per-kind mutation
records for the nine remaining operations, migrating each emitter to use
`applyChange()`, and deleting the legacy `ChangeType` surface in full.

---

## Status Dashboard

| Phase | Description | Model | Status |
|-------|-------------|-------|--------|
| 1 | [Mutation records for interval operations](#-phase-1-mutation-records-for-interval-operations) | Sonnet | ⏳ Pending |
| 2 | [Mutation records for element-field operations](#-phase-2-mutation-records-for-element-field-operations) | Sonnet | ⏳ Pending |
| 3 | [Migrate `MusicEditOperations` emitters](#-phase-3-migrate-musiceditoperations-emitters) | Opus | ⏳ Pending |
| 4 | [Migrate `SelectionCoordinator.applyActionToSelection`](#-phase-4-migrate-selectioncoordinatorapplyactiontoselection) | Sonnet | ⏳ Pending |
| 5 | [Migrate `ScoreMessageCoordinator` command handlers](#-phase-5-migrate-scoremessagecoordinator-command-handlers) | Sonnet | ⏳ Pending |
| 6 | [Delete `ChangeType` API](#-phase-6-delete-changetype-api) | Sonnet | ⏳ Pending |
| 7 | [Tests](#-phase-7-tests) | Sonnet | ⏳ Pending |

---

## Design Decisions

### Granularity: per-kind, not per-interval-type

The nine unmigrated operations fall into two groups:

**Interval-set operations** (add or remove an entry in a `Line.getXxx()` interval set):
- `toggleBeaming` — `BeamInterval` add/remove in `line.getBeamings()`
- `toggleTie` — `TieInterval` add/remove in `line.getTies()`
- `toggleTuplet` — `TupletInterval` add/remove/grade-change in `line.getTuplets()`
- `addDynamicsToSelection` — `DynamicsInterval` add in `line.getCrescendos()` or `line.getDiminuendos()`
- `removeDynamicsFromSelection` — `DynamicsInterval` remove(s) in the same two sets

**Element-field operations** (mutate one or more existing elements in place):
- `toggleTrill` — flips `element.isTrill()` for every note in the selection
- `toggleLyricsUnderRests` — flips `element.isForceSyllable()` on one rest element
- `flipStemDirection` — flips `element.isUpper()` and `element.setStemDirectionAuto(false)` across a selection plus tie partners
- `makeFirstSecondEnding` — inserts a barline (`addElement`) plus adds a `RangeElement` (`Ending`) plus optionally extends an interval

**Element-replace operation**:
- `SelectionCoordinator.applyActionToSelection` — calls `Line.replaceElementQuietly(i, replacement)` in a loop (ElementReplaceable actions like half-note → quarter-note) plus mutates element fields in place (ElementModifiable actions)

The granularity decision: **one record per operation**, not one record per
interval-set or per element-field. Rationale:

- **Subscribers today only care "line X's layout is dirty."** No current
  subscriber needs to distinguish "a beam was added" from "a tuplet grade
  changed." Fine-grained types would add noise without a reader.
- **Undo (#14) will need to know what changed**, but not at the type level —
  it needs the *before* state (interval set snapshot, element field snapshot)
  so it can re-apply. Records carry that snapshot; undo reads it generically.
- **Per-operation types mirror existing `ElementInsertion` / `LineInsertion`
  granularity** — one sealed-hierarchy entry per user-level action. This is
  the pattern the existing refactor established.

### `Line` interval-set accessors remain mutable

`Line.getBeamings() / getTies() / getTuplets() / getCrescendos() /
getDiminuendos()` return live `IntervalSet` references. Callers add/remove
intervals directly. The migration wraps each mutation site in
`line.withModification(() -> line.applyChange(new XxxChange(...), () -> { ... }))`
rather than introducing `Line.addBeaming(interval)` accessors that internally
route through `applyChange`. Rationale:

- The interval sets have rich operations (add, remove, findInterval, grade
  change) and multiple call sites already use them directly. Wrapping every
  interval-set access with a mutation-emitting method would force a large
  API surface just to preserve the existing call shape.
- The `applyChange` + lambda pattern the setters use already embeds the
  "capture before-state, run mutator, record mutation" contract cleanly.

### `makeFirstSecondEnding` decomposes into multiple mutations

`makeFirstSecondEnding` currently performs three distinct operations:

1. (Optional) Inserts a `SINGLEBARLINE` element via `line.addElement(start, barline)`
2. Adds an `Ending` to `line.getRangeElements()` via `line.addRangeElement(ending)`
3. Shifts interval indices internally (handled by `line.addElement` already)

Steps 1 and 2 **already emit mutations** (`ElementInsertion` and
`RangeElementAddition` respectively) because `Line.addElement` and
`Line.addRangeElement` route through `applyChange`. The caller just needs to
wrap the whole operation in a single `line.withModification(...)` bracket so
both mutations coalesce into one `CompositionDidChangeNotification`. No new
mutation record is required — this operation is already internally consistent
once the outer bracket is opened.

### Interval-set mutations carry a `before` snapshot

Each interval-set mutation record carries an immutable snapshot of the
affected set *before* the mutation ran. Undo (#14) replays by restoring the
snapshot; subscribers that want "what changed" can diff old and new.

```java
public record BeamingChange(
    Line line,
    IntervalSet<BeamInterval> oldBeamings,
    IntervalSet<BeamInterval> newBeamings
) implements Mutation, LineScopedMutation { ... }
```

`IntervalSet` does not currently implement `deepCopy()`. Phase 1 adds it.

### Element-field mutations reuse `ElementModification`

`toggleTrill`, `toggleLyricsUnderRests`, and `flipStemDirection` all modify
existing elements in place without adding or removing list entries. They
fit the existing `ElementModification(line, index, fields, beforeElement)`
pattern exactly. Phase 2 just extends `ElementField` with the necessary
values (`TRILL`, `FORCE_SYLLABLE`, `STEM_DIRECTION`) and each operation
emits one `ElementModification` per affected element via
`Line.modifyElement(index, field, mutator)`.

`flipStemDirection` affects multiple elements and multiple fields per element
(both `UPPER` and `STEM_DIRECTION_AUTO`). It emits one
`ElementModification(EnumSet.of(UPPER, STEM_DIRECTION_AUTO))` per affected
index. Beam partners and tie partners each get their own `ElementModification`
within the single bracket.

### `SelectionCoordinator.applyActionToSelection` — mixed mutation kinds

This method handles two action kinds:
- **`ElementReplaceable`** — calls `line.replaceElementQuietly(i, replacement)`,
  which is the only remaining caller of the quiet API. Migrate to
  `line.setElement(i, replacement)` (which emits `ElementReplacement`) and
  delete `replaceElementQuietly` after this refactor.
- **`ElementModifiable`** — calls `modifiable.applyToElement(element, selected)`,
  mutating the element in place. Wrap each iteration in
  `line.modifyElement(i, fields, () -> modifiable.applyToElement(...))`. The
  `fields` EnumSet depends on which action is being applied; extend
  `ElementField` with new values as needed (e.g. `DURATION`, `DOT`,
  `ACCIDENTAL`, `ARTICULATION`, `FERMATA`). Phase 2 enumerates the exact set
  by cross-referencing `UIAction.ElementModifiable` implementations.

### `setModified(true)` in `MusicEditOperations` disappears

Every `MusicEditOperations` method currently ends with
`composition.setModified(true)`. After migration, each method runs inside a
`line.withModification(...)` bracket and the outermost `endModification`
sets `modified = true` automatically. Remove the explicit calls.

### `ScoreMessageCoordinator.postSelectionContentChanged` disappears

After migration, every command handler emits its own mutations via
`applyChange`, and the bracket closure fires one
`CompositionDidChangeNotification`. The post-operation manual
`MessageCenter.post(new CompositionDidChangeNotification(CONTENT, ...))` is
dead code. Delete `postSelectionContentChanged` and the inline posts in
`handleToggleBeam` and `handleToggleLyricsUnderRests`.

### `compositionDidChange` filter collapses to one check

After migration, `hasChangeType(CONTENT)` always returns `false`, and every
operation that used to fire `CONTENT` now emits a `LineScopedMutation`
(either directly or via `ElementModification`). The filter collapses to:

```java
@Handler
public void compositionDidChange(CompositionDidChangeNotification message) {
    var mainPanel = score.getMainPanel();
    if (mainPanel == null) return;

    if (hasLineLayoutMutation(message)) {
        // invalidate affected line layouts
    }

    if (hasFullRelayoutMutation(message)) {
        score.viewChanged();
    }

    // repaint debounce
}
```

`hasLineLayoutMutation` already covers `LineScopedMutation`, `LineInsertion`,
`LineDeletion`, and `LyricsChange`. The new interval-set mutations implement
`LineScopedMutation`, so they're covered automatically.

### `LyricsProcessor.spellLyrics` side effect

`toggleLyricsUnderRests` calls `LyricsProcessor.spellLyrics(line)` after
mutating the element. This runs *outside* the mutation system. Keep it
outside the bracket, matching the precedent in `Composition.lyricsDidChange`
which also runs `spellLyrics` after the bracket closes. It produces no
notification of its own — it just adjusts the in-memory lyrics state.

---

## ✅ Phase 1: Mutation records for interval operations

**Model:** Sonnet  <br>
**Status:** Pending  <br>
**BlockedBy:** --

### Purpose

Add the five sealed-hierarchy entries for interval-set operations and the
`IntervalSet.deepCopy()` method they rely on.

### Tasks

1. Add `deepCopy()` to `IntervalSet<T>` that returns a new `IntervalSet`
   containing copies of every interval. Each interval subclass
   (`BeamInterval`, `TieInterval`, `TupletInterval`, `DynamicsInterval`,
   `Interval`) gets its own `copy()` method returning a new instance with
   the same fields. Interval records can use their record copy constructor.
2. Create mutation records in `songscribe.message.mutation`:
   - `BeamingChange(Line line, IntervalSet<BeamInterval> oldBeamings, IntervalSet<BeamInterval> newBeamings)`
   - `TieChange(Line line, IntervalSet<TieInterval> oldTies, IntervalSet<TieInterval> newTies)`
   - `TupletChange(Line line, IntervalSet<TupletInterval> oldTuplets, IntervalSet<TupletInterval> newTuplets)`
   - `CrescendoChange(Line line, IntervalSet<DynamicsInterval> oldCrescendos, IntervalSet<DynamicsInterval> newCrescendos)`
   - `DiminuendoChange(Line line, IntervalSet<DynamicsInterval> oldDiminuendos, IntervalSet<DynamicsInterval> newDiminuendos)`
3. Each record implements `Mutation` and `LineScopedMutation`. `getLine()`
   returns the stored `line` field.
4. Add all five to the `Mutation.permits` clause.

### Key files

- `src/main/java/songscribe/music/IntervalSet.java` (add `deepCopy`)
- `src/main/java/songscribe/music/BeamInterval.java`, `TieInterval.java`,
  `TupletInterval.java`, `DynamicsInterval.java`, `Interval.java`
  (add `copy()`)
- `src/main/java/songscribe/message/mutation/BeamingChange.java` (new)
- `src/main/java/songscribe/message/mutation/TieChange.java` (new)
- `src/main/java/songscribe/message/mutation/TupletChange.java` (new)
- `src/main/java/songscribe/message/mutation/CrescendoChange.java` (new)
- `src/main/java/songscribe/message/mutation/DiminuendoChange.java` (new)
- `src/main/java/songscribe/message/mutation/Mutation.java` (extend `permits`)

### Open questions

- `Crescendo` and `Diminuendo` are currently listed in the `mutation-hierarchy-refactor`
  deferred work as `CrescendoAddition/Removal` / `DiminuendoAddition/Removal`
  pairs. This plan collapses each pair into a single `CrescendoChange` /
  `DiminuendoChange` that carries old+new snapshots. Confirm the collapse is
  preferred over add/remove pairs.

---

## ✅ Phase 2: Mutation records for element-field operations

**Model:** Sonnet  <br>
**Status:** Pending  <br>
**BlockedBy:** --

### Purpose

Extend `ElementField` with the enum values the remaining element-field
operations need. No new mutation records are required — all uses go through
the existing `ElementModification` record and `Line.modifyElement` helper.

### Tasks

1. Extend `ElementField` enum with these values (add to the existing enum;
   alphabetize if the existing file is sorted):
   - `TRILL` — for `toggleTrill`
   - `FORCE_SYLLABLE` — for `toggleLyricsUnderRests`
   - `UPPER` — for `flipStemDirection`'s stem direction (and grace-note drag)
   - `STEM_DIRECTION_AUTO` — paired with `UPPER` in `flipStemDirection`
2. Cross-reference every `UIAction.ElementModifiable` implementation and
   every `UIAction.ElementReplaceable` implementation to enumerate the fields
   each one touches. Add any missing `ElementField` values. Candidates to
   verify during the audit:
   - `DURATION` (quarter/eighth/etc.) — replaceable; a replacement produces
     `ElementReplacement`, no `ElementField` needed
   - `DOT` — modifiable, likely needs `DOT`
   - `ACCIDENTAL` — modifiable, likely needs `ACCIDENTAL`
   - `ARTICULATION` — modifiable, likely needs `ARTICULATION`
   - `FERMATA` — modifiable, likely needs `FERMATA`
3. Add unit tests for each new `ElementField` constant — just verify the
   constant exists and is in `values()`.

### Key files

- `src/main/java/songscribe/message/mutation/ElementField.java` (extend)
- `src/main/java/songscribe/ui/action/` (audit ElementModifiable /
  ElementReplaceable implementations)

### Deliverable

The extended `ElementField` enum plus a short sub-section appended to this
plan listing which `UIAction` each new value corresponds to, so Phase 4
(applyActionToSelection migration) can reference it.

---

## ✅ Phase 3: Migrate `MusicEditOperations` emitters

**Model:** Opus  <br>
**Status:** Pending  <br>
**BlockedBy:** Phases 1, 2

### Purpose

Rewrite each `MusicEditOperations` method to emit a mutation via
`applyChange` inside a `withModification` bracket. After this phase, no
`MusicEditOperations` method calls `composition.setModified(true)` directly.

### Tasks

1. **`toggleBeaming`** (`MusicEditOperations.java:61`):
   - Capture `var old = line.getBeamings().deepCopy()` before the mutation.
   - Wrap the `if shouldConnect ... addInterval else removeInterval` block
     in `line.applyChange(new BeamingChange(line, old, line.getBeamings()), () -> { ... })`.
   - `newBeamings` in the record is the **post-mutation** live set. Since
     the mutation records are immutable, capture `line.getBeamings().deepCopy()`
     *after* the mutator runs if the record should hold a frozen snapshot.
     *Alternative:* the record can hold the live reference if we accept that
     the "new" state is mutable; this is fine for layout-invalidation readers
     but not for undo. **Decision:** hold a frozen snapshot post-mutation,
     to match the "beforeElement is a clone" pattern from `ElementModification`.
   - Remove `composition.setModified(true)` from the method body.
   - Caller (`ScoreMessageCoordinator.handleToggleBeam`) must open the
     bracket, because `MusicEditOperations` methods no longer open their own.
     See Phase 5.
2. **`toggleTie`** (`MusicEditOperations.java:87`): same pattern with `TieChange`.
3. **`toggleTuplet`** (`MusicEditOperations.java:119`): same pattern with `TupletChange`.
4. **`addDynamicsToSelection`** (`MusicEditOperations.java:146`): same
   pattern with `CrescendoChange` or `DiminuendoChange` depending on the
   `crescendo` parameter.
5. **`removeDynamicsFromSelection`** (`MusicEditOperations.java:175`):
   emits *both* `CrescendoChange` and `DiminuendoChange` inside a single
   bracket, since the method removes intervals from both sets. Skip the
   emission for a set whose interval list is empty.
6. **`toggleTrill`** (`MusicEditOperations.java:510`): iterate the selection
   and call `line.modifyElement(i, ElementField.TRILL, () -> note.setTrill(!note.isTrill()))`.
   No new mutation record.
7. **`toggleLyricsUnderRests`** (`MusicEditOperations.java:533`): single
   `line.modifyElement(selectionBegin, ElementField.FORCE_SYLLABLE, ...)` call.
   Keep `LyricsProcessor.spellLyrics(line)` *after* the bracket closes
   (outside `modifyElement`'s mutator).
8. **`flipStemDirection`** (`MusicEditOperations.java:556`): most complex
   site. Wrap the whole thing in `line.withModification(() -> { ... })`
   and replace each direct `note.setUpper(...)` / `note.setStemDirectionAuto(...)`
   pair with
   `line.modifyElement(idx, EnumSet.of(ElementField.UPPER, ElementField.STEM_DIRECTION_AUTO), () -> { note.setStemDirectionAuto(false); note.setUpper(newUpper); })`.
   Emit one `ElementModification` per affected index (beam group loop, tie
   partner loop, and the outer selection loop's non-beam branch). The
   helper clones each element before its mutator runs, so the before-state
   is captured per-element correctly.
9. **`makeFirstSecondEnding`** (`MusicEditOperations.java:466`): no new
   mutation record. The method's internal `line.addElement(start, barline)`
   and `line.addRangeElement(new Ending(...))` calls already emit mutations.
   Wrap the whole method body in `line.withModification(() -> { ... })` so
   both are coalesced into a single notification. Remove the explicit
   `composition.setModified(true)` at the end.

10. **Remove `composition.setModified(true)`** from every method in
    `MusicEditOperations`. The bracket closure handles dirty-marking.

### Key files

- `src/main/java/songscribe/music/MusicEditOperations.java`

### Migration risk

- The migration is mechanical but touches every method in
  `MusicEditOperations`. Run `./scripts/test.sh unit` after each method
  (not just at the end) to catch missed brackets via the strict
  `applyChange` `IllegalStateException`. Production code paths for these
  operations are exercised by e2e tests; verify those separately.

---

## ✅ Phase 4: Migrate `SelectionCoordinator.applyActionToSelection`

**Model:** Sonnet  <br>
**Status:** Pending  <br>
**BlockedBy:** Phase 2

### Purpose

Route `applyActionToSelection` through `applyChange`. This is the last
caller of `Line.replaceElementQuietly`; remove that method after the
migration.

### Tasks

1. In `SelectionCoordinator.applyActionToSelection` (line 569):
   - Wrap the whole body in `composition.withModification(() -> { ... })`
     after the early return.
   - For the `ElementReplaceable` branch: replace
     `line.replaceElementQuietly(i, replacement)` with
     `line.setElement(i, replacement)`. This emits `ElementReplacement`
     inside the open bracket.
   - For the `ElementModifiable` branch: wrap in
     `line.modifyElement(i, fieldsFor(action), () -> modifiable.applyToElement(element, selected))`.
     `fieldsFor(action)` is a new helper on `UIAction` or a switch on action
     type that returns the appropriate `EnumSet<ElementField>`.
     See Phase 2 for the field enumeration.
2. Delete the trailing
   `composition.setModified(true); MessageCenter.post(new CompositionDidChangeNotification(CONTENT, ...))`
   lines. `withModification` handles both.
3. Delete `Line.replaceElementQuietly` (`Line.java:~280`). Confirm no other
   callers via `search_for_pattern` / `jet_brains_find_referencing_symbols`.
4. `validateIntervals` still runs inside the bracket — it mutates interval
   sets (tie/beam repair) via direct access. This is a second-order
   mutation that should ideally emit its own interval-change records. For
   **this phase only**, leave `validateIntervals` as a raw mutation inside
   the bracket and rely on one or more of the Phase 3 interval-change
   mutations being emitted by whatever operations run after. **Follow-up:**
   track `validateIntervals` as deferred work until a Phase 8 migrates it
   properly.

### Key files

- `src/main/java/songscribe/ui/selection/SelectionCoordinator.java`
- `src/main/java/songscribe/music/Line.java` (delete `replaceElementQuietly`)
- `src/main/java/songscribe/ui/action/UIAction.java` (optional: add
  `fieldsFor` helper or similar field-discovery mechanism)

---

## ✅ Phase 5: Migrate `ScoreMessageCoordinator` command handlers

**Model:** Sonnet  <br>
**Status:** Pending  <br>
**BlockedBy:** Phases 3, 4

### Purpose

Open the modification bracket at the command-handler level (where each
user action is a single command) rather than inside `MusicEditOperations`.
Delete the manual `CompositionDidChangeNotification(CONTENT, ...)` posts.

### Tasks

1. **`handleToggleBeam`** (`ScoreMessageCoordinator.java:175`):
   ```java
   @Handler
   public void handleToggleBeam(ToggleBeamCommand message) {
       var selection = selectionCoordinator.getActiveSelection();
       if (selection == null) return;
       selection.getLine().withModification(() -> operations.toggleBeaming());
   }
   ```
   Remove the `MessageCenter.post(new CompositionDidChangeNotification(...))`
   line.
2. **`handleToggleTie`** (line 186): wrap `operations.toggleTie()` in the
   active selection's `line.withModification(...)`. Remove `postSelectionContentChanged()`.
3. **`handleToggleTuplet`** (line 191): same. Keep `score.selectionChanged()`
   call; it's not a mutation.
4. **`handleAddDynamics`** (line 198): same pattern with
   `operations.addDynamicsToSelection(message.isCrescendo())`.
5. **`handleRemoveDynamics`** (line 204): same.
6. **`handleFirstSecondEnding`** (line 210): same; the `MessageCenter.post(new DeselectCommand())`
   call stays outside the bracket.
7. **`handleToggleTrill`** (line 221): same.
8. **`handleToggleLyricsUnderRests`** (line 228): same. Remove the
   `MessageCenter.post(new CompositionDidChangeNotification(CONTENT, ...))`
   line.
9. **`handleFlipStemDirection`** (line 235): same.
10. **Delete `ScoreMessageCoordinator.postSelectionContentChanged`** after
    all handlers are migrated.

### Notes

- Every command handler now opens its own bracket. Nested brackets (e.g.
  `operations.toggleBeaming` internally calls `line.withModification` — NO,
  after Phase 3 it does NOT; it just calls `line.applyChange` inside the
  caller's already-open bracket) handle depth correctly.

  **Clarification:** Phase 3 migrates each `MusicEditOperations` method to
  call `line.applyChange(...)` directly without opening its own bracket.
  The coordinator's bracket is the outermost. This is deliberate: the
  coordinator knows the scope of the user action, `MusicEditOperations`
  knows only what to mutate.

### Key files

- `src/main/java/songscribe/ui/component/ScoreMessageCoordinator.java`

---

## ✅ Phase 6: Delete `ChangeType` API

**Model:** Sonnet  <br>
**Status:** Pending  <br>
**BlockedBy:** Phases 3, 4, 5

### Purpose

Remove the deprecated `ChangeType` enum, the three legacy constructors,
`legacyChangeTypes` field, and the `hasChangeType` / `getChangeTypes`
methods from `CompositionDidChangeNotification`. Collapse
`ScoreMessageCoordinator.compositionDidChange` to the mutation-only check.

### Tasks

1. Delete `CompositionDidChangeNotification.ChangeType` enum.
2. Delete the three deprecated constructors:
   - `CompositionDidChangeNotification(ChangeType, Composition)`
   - `CompositionDidChangeNotification(ChangeType, Composition, Line)`
   - `CompositionDidChangeNotification(EnumSet<ChangeType>, Composition, Line)`
3. Delete the `legacyChangeTypes` field and the `lineIsCached` / `cachedLine`
   pre-caching branch that exists only for legacy constructors. The
   remaining `getLine()` is purely mutation-driven.
4. Delete `hasChangeType(ChangeType)` and `getChangeTypes()` methods.
5. Update `CompositionDidChangeNotification` javadoc to remove the
   "deprecated will be removed" language.
6. In `ScoreMessageCoordinator.compositionDidChange`:
   - Remove the `import ...ChangeType` import.
   - Remove the `message.hasChangeType(ChangeType.CONTENT) ||` branch.
   - The remaining `if (hasLineLayoutMutation(message))` check is
     sufficient.
7. Remove the `ChangeType` reference in `DocumentDidLoadNotification.java`
   javadoc (currently: `"Replaces ChangeType.FULL from the old notification"`).
8. Update `plans/mutation-hierarchy-refactor.md` "Deferred Work" section to
   mark range/interval mutation types as ✅ done (now covered by this plan).
9. Update `.claude/rules/messages.md` example that uses
   `CompositionDidChangeNotification(ChangeType.LAYOUT, ...)` to use the
   new constructor form.

### Key files

- `src/main/java/songscribe/message/notification/CompositionDidChangeNotification.java`
- `src/main/java/songscribe/message/notification/DocumentDidLoadNotification.java`
- `src/main/java/songscribe/ui/component/ScoreMessageCoordinator.java`
- `plans/mutation-hierarchy-refactor.md`
- `.claude/rules/messages.md`

### Grep verification

After this phase, `grep -r ChangeType src/main` returns no matches.

---

## ✅ Phase 7: Tests

**Model:** Sonnet  <br>
**Status:** Pending  <br>
**BlockedBy:** Phases 1–6

### Purpose

Unit-test every new mutation emission path and update any existing test
that asserts on `ChangeType`-based APIs.

### Tasks

1. **Mutation record tests** in `src/test/java/songscribe/message/mutation/`:
   - `BeamingChangeTest` — construct a record, verify `getLine()`, verify
     the oldBeamings / newBeamings snapshots are distinct instances.
   - Same for `TieChange`, `TupletChange`, `CrescendoChange`,
     `DiminuendoChange`.
   - `IntervalSetDeepCopyTest` — verify `deepCopy()` produces an independent
     instance whose contents are equal but whose reference is different.
     Verify that mutating the copy does not affect the original.
2. **Operation emission tests** in a new `MusicEditOperationsMutationTest`:
   - `testToggleBeamingEmitsBeamingChange` — create a composition with a
     line, select a few notes, call `operations.toggleBeaming()` inside a
     real (not mocked) message-center capture, assert one notification with
     one `BeamingChange` whose old set is empty and new set contains the
     beam.
   - `testToggleTieEmitsTieChange`
   - `testToggleTupletEmitsTupletChange`
   - `testAddDynamicsEmitsCrescendoChangeOrDiminuendoChange` (parameterized)
   - `testRemoveDynamicsEmitsBothCrescendoAndDiminuendoChange`
   - `testToggleTrillEmitsOneElementModificationPerNote`
   - `testToggleLyricsUnderRestsEmitsOneElementModification`
   - `testFlipStemDirectionEmitsElementModificationPerAffectedIndex`
   - `testMakeFirstSecondEndingEmitsElementInsertionAndRangeElementAddition`
3. **Coordinator integration test** in
   `ScoreMessageCoordinatorTest` (or new file):
   - Verify that each command handler opens exactly one bracket and the
     resulting notification has the expected mutations.
4. **`applyActionToSelection` test**:
   - For an `ElementReplaceable` action (e.g. duration change), verify
     one `ElementReplacement` per selected element plus at most one
     interval-cleanup mutation.
   - For an `ElementModifiable` action (e.g. accidental), verify one
     `ElementModification` per affected element with the correct
     `ElementField` set.
5. **Update existing tests** in
   `CompositionDidChangeNotificationTest` that construct notifications via
   the deprecated constructors. All legacy-constructor calls should be
   replaced with the new `(List<Mutation>, Composition)` form.
6. **Regression: verify layout invalidation fires** — add an integration
   test that installs a spy `LinePanel` (or similar) and confirms that
   beam/tie/tuplet operations still trigger `invalidateLayout()` after the
   migration. This is the main user-visible correctness concern of the
   whole plan.

### Key files

- `src/test/java/songscribe/message/mutation/` (new record tests)
- `src/test/java/songscribe/music/MusicEditOperationsMutationTest.java` (new)
- `src/test/java/songscribe/ui/component/ScoreMessageCoordinatorTest.java` (extend)
- `src/test/java/songscribe/message/notification/CompositionDidChangeNotificationTest.java` (update)

---

## Deferred Work (out of scope; revisit when needed)

1. **`validateIntervals` in `SelectionCoordinator`** — currently mutates
   tie/beam/tuplet interval sets directly inside `applyActionToSelection`.
   Phase 4 leaves it as a raw mutation; a follow-up should emit proper
   `BeamingChange` / `TieChange` / `TupletChange` mutations so undo can
   restore the pre-repair state.
2. **Interval set snapshot cost** — `deepCopy()` copies every interval on
   every interval-set mutation. For a selection spanning hundreds of notes
   this is O(n). If profiling shows it matters, consider persistent
   (copy-on-write) interval sets. Not needed today.
3. **`ElementField` enum completeness** — Phase 2 adds the fields actually
   used by current operations. New fields get added as new emitters arrive.
4. **`applyActionToSelection` two-pass structure** — the method first
   mutates every element then calls `validateIntervals`. A more faithful
   mutation decomposition would emit per-element mutations followed by
   interval-change mutations in the same bracket. The structure is already
   correct; the deferred work is making `validateIntervals` explicit (item 1).

---

## Risk assessment

**Scope estimate:** ~600 lines of production code across 6 files, ~300 lines
of test code across 5–6 files, 5 new mutation record files.

**Risk areas:**

1. **`flipStemDirection` correctness** — the method has three interleaved
   loops (beam groups, selection, tie partners) with shared state (the
   `processedBeamIntervals` set and `tiePartnersToFlip` tree). Emitting a
   mutation per affected index is straightforward; the risk is that
   `line.modifyElement` clones the element *before* the mutator runs, so
   the beam-group branch (which reads `firstElement.isUpper()` before
   writing `newUpper`) needs careful ordering to avoid capturing a
   half-mutated clone. Read-then-emit-then-write ordering is the safe
   pattern: compute `newUpper` first, then issue the `modifyElement` call.
2. **Interval set snapshot ordering** — `BeamingChange`'s `newBeamings`
   should be captured *after* the mutator runs. If captured inside the
   mutator lambda it's pre-mutation. Use a local variable captured after
   `applyChange` returns and re-wrap in a second `applyChange`... or, more
   simply, capture the new snapshot via a post-mutator hook. The cleanest
   implementation is to construct the record with a deferred-capture
   pattern, e.g.:
   ```java
   var oldBeamings = line.getBeamings().deepCopy();
   line.withModification(() -> {
       // mutate the live set
       beamings.addInterval(...);
       var newBeamings = line.getBeamings().deepCopy();
       line.applyChange(new BeamingChange(line, oldBeamings, newBeamings), () -> {});
   });
   ```
   The empty-mutator `applyChange` is the same pattern `NoteDragHandler`
   already uses for the pitch-drag record: "the mutation already happened;
   emit a record for it." This is acceptable but slightly ugly. An
   alternative is to make the mutation record carry only the *before*
   snapshot and have subscribers derive *after* from the current live set
   state, but that breaks encapsulation for undo.
3. **`makeFirstSecondEnding` and `toggleTuplet` interaction with existing
   mutations** — these methods call `line.addElement` / `line.addRangeElement`
   which already emit mutations. Wrapping in a bracket produces *multiple*
   mutations in one notification. Verify that
   `ScoreMessageCoordinator.hasLineLayoutMutation` still fires for the
   compound case (it should, because each component mutation implements
   `LineScopedMutation`).
4. **E2E test coverage** — the interval-set operations are exercised by
   existing e2e tests. Run the full e2e suite after Phases 3–5 to catch
   any user-visible regressions.

---

## Success criteria

1. `grep -r ChangeType src/main/java` returns no matches.
2. `./scripts/test.sh unit` passes 100%.
3. Manual smoke test in the running app: toggle beams, ties, tuplets,
   dynamics, trills, stem direction, lyrics-under-rests, and first/second
   ending all still trigger correct layout updates.
4. `CompositionDidChangeNotification.java` has a single constructor and
   no `@Deprecated` members.
5. `MusicEditOperations.java` contains no calls to
   `composition.setModified(true)`.
6. `ScoreMessageCoordinator.postSelectionContentChanged` is deleted.
7. `Line.replaceElementQuietly` is deleted.
