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

### Granularity: Addition/Removal pairs for interval sets

The nine unmigrated operations fall into two groups:

**Interval-set operations** (add or remove an entry in a `Line.getXxx()` interval set):
- `toggleBeaming` — `BeamInterval` add or remove in `line.getBeamings()`
- `toggleTie` — `TieInterval` add or remove in `line.getTies()`
- `toggleTuplet` — `TupletInterval` add or remove in `line.getTuplets()` (no in-place grade change; see Phase 3)
- `addDynamicsToSelection` — single `DynamicsInterval` add in `line.getCrescendos()` xor `line.getDiminuendos()`
- `removeDynamicsFromSelection` — one or more `DynamicsInterval` removes across either or both sets

**Element-field operations** (mutate one or more existing elements in place):
- `toggleTrill` — flips `element.isTrill()` for every note in the selection
- `toggleLyricsUnderRests` — flips `element.isForceSyllable()` on one rest element
- `flipStemDirection` — flips `element.isUpper()` and `element.setStemDirectionAuto(false)` across a selection plus tie partners
- `makeFirstSecondEnding` — inserts a barline (`addElement`) plus adds a `RangeElement` (`Ending`) plus optionally extends an interval

**Element-replace operation**:
- `SelectionCoordinator.applyActionToSelection` — calls `Line.replaceElementQuietly(i, replacement)` in a loop (ElementReplaceable actions like half-note → quarter-note) plus mutates element fields in place (ElementModifiable actions)

Every interval-set operation follows the same shape: **single add xor
one-or-more removes, never mixed**. Given that, the granularity decision
for interval sets is **one `Addition` record and one `Removal` record per
interval type**, each carrying a single interval instance. Rationale:

- **Each mutation is a discrete, self-describing fact.** An `Addition` holds
  the added interval; a `Removal` holds the removed interval. No before/after
  set snapshots are required.
- **Undo (#14) is a trivial inverse per record.** `Addition` undo = remove
  that interval; `Removal` undo = re-add that interval. No snapshot machinery
  needs to be replayed.
- **Multi-remove operations emit N `Removal` records in a single bracket**,
  which is exactly what brackets are for.
- **Mirrors the existing `ElementInsertion` / `ElementDeletion` shape** — one
  sealed-hierarchy entry per discrete fact.

Element-field operations continue to route through the existing
`ElementModification` record (see the "Element-field mutations reuse
`ElementModification`" subsection below).

### `Line` interval-set accessors remain mutable

`Line.getBeamings() / getTies() / getTuplets() / getCrescendos() /
getDiminuendos()` return live `IntervalSet` references. Callers add/remove
intervals directly. The migration wraps each mutation site in
`line.applyChange(new XxxAddition(line, interval), () -> intervalSet.addInterval(interval))`
(or the `Removal` counterpart) rather than introducing `Line.addBeaming(interval)`
accessors that internally route through `applyChange`. Rationale:

- The interval sets have rich operations (add, remove, findInterval) and
  multiple call sites already use them directly. Wrapping every interval-set
  access with a mutation-emitting method would force a large API surface
  just to preserve the existing call shape.
- The `applyChange` + lambda pattern the setters use already embeds the
  "record mutation, run mutator" contract cleanly. Because the mutation
  records carry only the interval being added or removed (not before/after
  set snapshots), the emission site is a single line per mutation.

### `makeFirstSecondEnding` decomposes into multiple mutations

`makeFirstSecondEnding` currently performs three distinct operations:

1. (Optional) Inserts a `SINGLEBARLINE` element via `line.addElement(start, barline)`
2. Adds an `Ending` to `line.getRangeElements()` via `line.addRangeElement(ending)`
3. Shifts interval indices internally (handled by `line.addElement` already)

Steps 1 and 2 **already emit mutations** (`ElementInsertion` and
`RangeElementAddition` respectively) because `Line.addElement` and
`Line.addRangeElement` route through `applyChange`. `makeFirstSecondEnding`
wraps its whole body in `line.withModification(...)` so both mutations
coalesce into one `CompositionDidChangeNotification`. No new mutation record
is required — this operation is already internally consistent once the
bracket is opened.

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

### Each `MusicEditOperations` method opens its own bracket

Every migrated `MusicEditOperations` method wraps its whole body in
`line.withModification(() -> { ... })`. The command handler in
`ScoreMessageCoordinator` just calls the method directly — no bracket glue
at the coordinator layer.

This relies on `Composition.withModification` already being reentrant via
`modificationDepth` (`Composition.java:197`): `beginModification` increments
the counter, `endModification` decrements it, and mutations flush / the
notification fires only at depth 0. Nested `withModification` calls collapse
into the outermost one, so a hypothetical future caller that wraps multiple
operations in its own outer bracket still produces exactly one notification.

The method owning its own bracket keeps `MusicEditOperations` self-contained:
- Call sites (command handlers, tests, future programmatic drivers) don't
  need to know about the `Line.applyChange` "must be inside a bracket"
  contract.
- The `Line.applyChange` contract is still satisfied — just at the method
  boundary instead of the call site.
- Removes nine copies of the `line.withModification(() -> operations.xxx())`
  boilerplate from `ScoreMessageCoordinator` command handlers.

### `setModified(true)` in `MusicEditOperations` disappears

Every `MusicEditOperations` method currently ends with
`composition.setModified(true)`. After migration, each method runs inside its
own `line.withModification(...)` bracket and the outermost `endModification`
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

## ⏳ Phase 1: Mutation records for interval operations

**Model:** Sonnet  <br>
**Status:** Pending  <br>
**BlockedBy:** --

### Purpose

Add the ten sealed-hierarchy entries for interval-set operations: one
`Addition` and one `Removal` record per interval type.

### Tasks

1. Create mutation records in `songscribe.message.mutation`:
   - `BeamingAddition(Line line, BeamInterval interval)`
   - `BeamingRemoval(Line line, BeamInterval interval)`
   - `TieAddition(Line line, TieInterval interval)`
   - `TieRemoval(Line line, TieInterval interval)`
   - `TupletAddition(Line line, TupletInterval interval)`
   - `TupletRemoval(Line line, TupletInterval interval)`
   - `CrescendoAddition(Line line, DynamicsInterval interval)`
   - `CrescendoRemoval(Line line, DynamicsInterval interval)`
   - `DiminuendoAddition(Line line, DynamicsInterval interval)`
   - `DiminuendoRemoval(Line line, DynamicsInterval interval)`
2. Each record implements `Mutation` and `LineScopedMutation`. `getLine()`
   returns the stored `line` field.
3. Add all ten to the `Mutation.permits` clause.

### Key files

- `src/main/java/songscribe/message/mutation/BeamingAddition.java` (new)
- `src/main/java/songscribe/message/mutation/BeamingRemoval.java` (new)
- `src/main/java/songscribe/message/mutation/TieAddition.java` (new)
- `src/main/java/songscribe/message/mutation/TieRemoval.java` (new)
- `src/main/java/songscribe/message/mutation/TupletAddition.java` (new)
- `src/main/java/songscribe/message/mutation/TupletRemoval.java` (new)
- `src/main/java/songscribe/message/mutation/CrescendoAddition.java` (new)
- `src/main/java/songscribe/message/mutation/CrescendoRemoval.java` (new)
- `src/main/java/songscribe/message/mutation/DiminuendoAddition.java` (new)
- `src/main/java/songscribe/message/mutation/DiminuendoRemoval.java` (new)
- `src/main/java/songscribe/message/mutation/Mutation.java` (extend `permits`)

---

## ⏳ Phase 2: Mutation records for element-field operations

**Model:** Sonnet  <br>
**Status:** Pending  <br>
**BlockedBy:** --

### Purpose

Extend `ElementField` with the enum values the Phase 3 element-field
operations need. No new mutation records are required — all uses go through
the existing `ElementModification` record and `Line.modifyElement` helper.

`ElementField` is a filter tag, not undo storage: `ElementModification`
already holds a full `beforeElement` clone as the undo source of truth,
so tag values exist purely to let subscribers skip handlers they don't
care about. Values are therefore added only as emitters need them —
Phase 4 and any future migration will extend the enum at the point of
use rather than up-front.

### Tasks

1. Extend `ElementField` enum with the values needed by Phase 3:
   - `TRILL` — for `toggleTrill`
   - `FORCE_SYLLABLE` — for `toggleLyricsUnderRests`
   - `UPPER` — for `flipStemDirection`'s stem direction
   - `STEM_DIRECTION_AUTO` — paired with `UPPER` in `flipStemDirection`

### Key files

- `src/main/java/songscribe/message/mutation/ElementField.java` (extend)

---

## ⏳ Phase 3: Migrate `MusicEditOperations` emitters

**Model:** Opus  <br>
**Status:** Pending  <br>
**BlockedBy:** Phases 1, 2

### Purpose

Rewrite each `MusicEditOperations` method to wrap its body in
`line.withModification(() -> { ... })` and emit one or more mutations via
`applyChange` inside that bracket. Each method owns its own bracket; the
command handler in `ScoreMessageCoordinator` just calls the method. After
this phase, no `MusicEditOperations` method calls
`composition.setModified(true)` directly.

### Tasks

1. **`toggleBeaming`** (`MusicEditOperations.java:61`):
   - Wrap the whole body in `line.withModification(() -> { ... })`.
   - In the `shouldConnect` branch, emit a `BeamingAddition(line, interval)`:
     `line.applyChange(new BeamingAddition(line, interval), () -> line.getBeamings().addInterval(interval));`
   - In the remove branch, emit a `BeamingRemoval(line, interval)` identically.
   - Remove `composition.setModified(true)` from the method body.
2. **`toggleTie`** (`MusicEditOperations.java:87`): same pattern (wrap body
   in `line.withModification(...)`) with `TieAddition` / `TieRemoval`.
3. **`toggleTuplet`** (`MusicEditOperations.java:119`): same pattern with
   `TupletAddition` / `TupletRemoval`. **Behavioral change:** if the current
   implementation has an in-place grade-change branch (changing an existing
   tuplet's grade without replacing it), remove that branch. The method now
   only adds or removes a tuplet. If a grade-change capability is preserved
   at the UI level in a future plan, it must be rewritten as remove-then-add,
   emitted as one `TupletRemoval` followed by one `TupletAddition` in the
   same bracket. This plan does **not** update `TupletAction` enable/disable
   logic; that work is deferred to a separate plan (see Deferred Work).
4. **`addDynamicsToSelection`** (`MusicEditOperations.java:146`): wrap body
   in `line.withModification(...)`. Emits exactly one `CrescendoAddition`
   xor one `DiminuendoAddition` depending on the `crescendo` parameter.
5. **`removeDynamicsFromSelection`** (`MusicEditOperations.java:175`): wrap
   body in `line.withModification(...)`. Emits one `CrescendoRemoval` per
   removed crescendo interval and one `DiminuendoRemoval` per removed
   diminuendo interval, all within the method's single bracket. Zero records
   are emitted for a set whose interval list contains no matches — the
   bracket still closes with mutations from the other set.
6. **`toggleTrill`** (`MusicEditOperations.java:510`): wrap body in
   `line.withModification(...)`, then iterate the selection and call
   `line.modifyElement(i, ElementField.TRILL, () -> note.setTrill(!note.isTrill()))`.
   No new mutation record.
7. **`toggleLyricsUnderRests`** (`MusicEditOperations.java:533`): wrap in
   `line.withModification(...)` around a single
   `line.modifyElement(selectionBegin, ElementField.FORCE_SYLLABLE, ...)` call.
   Keep `LyricsProcessor.spellLyrics(line)` *after* the bracket closes
   (outside the `withModification` body).
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

## ⏳ Phase 4: Migrate `SelectionCoordinator.applyActionToSelection`

**Model:** Sonnet  <br>
**Status:** Pending  <br>
**BlockedBy:** Phases 1, 2

### Purpose

Route `applyActionToSelection` through `applyChange`, migrate
`validateIntervals` to emit proper interval mutations (not defer it),
and remove the last caller of `Line.replaceElementQuietly`.

### Tasks

1. In `SelectionCoordinator.applyActionToSelection` (line 569):
   - Wrap the whole body in `composition.withModification(() -> { ... })`
     after the early return.
   - For the `ElementReplaceable` branch: replace
     `line.replaceElementQuietly(i, replacement)` with
     `line.setElement(i, replacement)`. This emits `ElementReplacement`
     inside the open bracket.
   - For the `ElementModifiable` branch: wrap in
     `line.modifyElement(i, modifiable.modifiedFields(), () -> modifiable.applyToElement(element, selected))`.
     Add a `modifiedFields()` method to the `ElementModifiable` interface
     returning `EnumSet<ElementField>` — each implementation declares the
     tag set corresponding to the fields it mutates. This keeps the field
     declaration orthogonal to action flags and colocated with the action
     that owns the mutation. Extend `ElementField` with whichever tag
     values the migrated actions actually need at the point of use — do
     not pre-enumerate.
2. Delete the trailing
   `composition.setModified(true); MessageCenter.post(new CompositionDidChangeNotification(CONTENT, ...))`
   lines. `withModification` handles both.
3. **Migrate `validateIntervals`.** The method is restructured entirely,
   not just routed through `applyChange`. Under the invariants that hold
   at this call site, the three branches have very different shapes:
   - **Tie branch — delete.** No reachable replacement via
     `applyActionToSelection` can invalidate a tie: pitch is preserved
     by `createReplacement`, rest-ness is preserved, grace notes are
     disabled in select mode (`Flag.DISABLE_IN_SELECT_MODE` on
     `createGraceEighthNoteAction`), and glissando between same-pitch
     notes is forbidden by the musical invariant that makes the tie
     legal in the first place. `ElementModifiable` actions don't touch
     element type. The entire `repairIntervalSet(line.getTies(), ...)`
     call is dead code and is removed along with the `isNote()`
     predicate it passed.
   - **Tuplet branch — flat remove.** Under the tuplet immutability
     policy ("any change other than pitch invalidates a tuplet"), any
     replacement in the modified range invalidates every overlapping
     tuplet; repair-by-splitting is semantically wrong. Rewrite as an
     `invalidateOverlappingTuplets(Line, int, int)` method that iterates
     `line.getTuplets()`, collects overlaps, and for each emits
     `line.applyChange(new TupletRemoval(line, t), () -> line.getTuplets().removeInterval(t))`.
     No sub-interval creation.
   - **Beam branch — trim-and-kill.** Replace
     `repairIntervalSet(line.getBeamings(), ...)` with a concrete
     `repairBeamings(Line, int, int)` method implementing the repair rule:
     1. Trim invalid (non-beamable) elements from the left end.
     2. Trim invalid elements from the right end.
     3. If the trimmed span still contains any invalid element (i.e. an
        invalid element in the interior), kill the beam entirely — one
        `BeamingRemoval`, no replacement.
     4. If the trimmed span is identical to the original, no-op.
     5. If the trimmed span shrank but is all valid, emit one
        `BeamingRemoval` for the original interval followed by one
        `BeamingAddition` for `beam.copyRange(newStart, newEnd)`.
     6. If trimming leaves fewer than 2 elements, degenerate to removal
        without re-add.

   The bidirectional trim handles configurations unreachable under
   contiguous-selection rules (e.g. `[q e e e q]` where both outer
   elements are invalid but the interior is valid) at zero extra cost,
   leaving the logic resilient to a future disjoint-selection capability.
4. Delete `Line.replaceElementQuietly` (`Line.java:~280`). Confirm no
   other callers via `search_for_pattern` /
   `jet_brains_find_referencing_symbols`.
5. Delete the generic `repairIntervalSet<T>` helper. Beams were its
   sole remaining user and now have their own concrete method.

### Design rationale for the `validateIntervals` restructure

Earlier drafts of this plan deferred `validateIntervals` as a "raw
mutation inside the bracket" because its three branches shared a
generic repair-and-split helper and migrating them together looked
scope-heavy. Working through the invariants that actually hold at each
call site shrinks the problem substantially:

1. **Ties are unreachable.** See the tie-branch bullet above.
2. **Tuplets flatten by policy.** Repair-and-split is wrong for
   tuplets under the new immutability policy; the correct behavior is
   a single-pass removal loop.
3. **Beams are the only genuine repair case.** Only beams need to
   handle end invalidation separately from interior invalidation. The
   trim-and-kill rule is simpler than the current repair-and-split: it
   handles end invalidation by shrinking the interval and interior
   invalidation by destroying it, without attempting to synthesize
   multiple sub-intervals. A beam with a non-beamable note in the
   middle isn't a salvageable musical structure — splitting it into
   sub-beams reads as an implementation artifact, not a user intent.

Since beams are the sole user of the repair pass, the generic
`repairIntervalSet<T>` helper with its type-erasure / predicate-parameter
shape disappears in favor of a concrete `repairBeamings` method. No
factory parameters, no type gymnastics.

### Behavior change note

The current `repairIntervalSet` creates sub-intervals wherever
contiguous runs of valid elements exist (≥ 2 elements for beams). The
new beam rule kills a beam entirely if any interior element becomes
non-beamable. Worked example: a five-note beam `[e e q e e]` (after
changing the middle eighth to a quarter) currently splits into two
two-note beams; under the new rule it becomes no beams. This is an
intentional semantic shift and needs:

- A targeted unit test (see Phase 7) asserting the
  kill-on-interior-invalid behavior for beams.
- Commit-message language calling out the change so it's visible in
  history.

### Key files

- `src/main/java/songscribe/ui/selection/SelectionCoordinator.java`
  (rewrite `validateIntervals`, add `repairBeamings` and
  `invalidateOverlappingTuplets`, delete `repairIntervalSet`)
- `src/main/java/songscribe/music/Line.java` (delete `replaceElementQuietly`)
- `src/main/java/songscribe/ui/action/UIAction.java` (add
  `modifiedFields()` to the `ElementModifiable` interface; implement it
  on every `ElementModifiable` action touched by the migration)

---

## ⏳ Phase 5: Clean up `ScoreMessageCoordinator` command handlers

**Model:** Sonnet  <br>
**Status:** Pending  <br>
**BlockedBy:** Phases 3, 4

### Purpose

Delete the manual `CompositionDidChangeNotification(CONTENT, ...)` posts and
`setModified(true)` calls from every migrated command handler. No bracket
glue is added at this layer — each `MusicEditOperations` method now opens
its own bracket (Phase 3), so handlers shrink to a direct call.

### Tasks

1. **`handleToggleBeam`** (`ScoreMessageCoordinator.java:175`):
   ```java
   @Handler
   public void handleToggleBeam(ToggleBeamCommand message) {
       var selection = selectionCoordinator.getActiveSelection();
       if (selection == null) return;
       operations.toggleBeaming();
   }
   ```
   Remove the `MessageCenter.post(new CompositionDidChangeNotification(...))`
   line.
2. **`handleToggleTie`** (line 186): call `operations.toggleTie()` directly.
   Remove `postSelectionContentChanged()`.
3. **`handleToggleTuplet`** (line 191): same. Keep `score.selectionChanged()`
   call; it's not a mutation.
4. **`handleAddDynamics`** (line 198): same pattern with
   `operations.addDynamicsToSelection(message.isCrescendo())`.
5. **`handleRemoveDynamics`** (line 204): same.
6. **`handleFirstSecondEnding`** (line 210): same; the `MessageCenter.post(new DeselectCommand())`
   call stays in the handler (outside the operation).
7. **`handleToggleTrill`** (line 221): same.
8. **`handleToggleLyricsUnderRests`** (line 228): same. Remove the
   `MessageCenter.post(new CompositionDidChangeNotification(CONTENT, ...))`
   line.
9. **`handleFlipStemDirection`** (line 235): same.
10. **Delete `ScoreMessageCoordinator.postSelectionContentChanged`** after
    all handlers are migrated.

### Notes

- Each `MusicEditOperations` method opens its own `line.withModification(...)`
  bracket (Phase 3), so handlers do not need any bracket glue.
- `Composition.withModification` is reentrant via `modificationDepth`
  (`Composition.java:197`): if any future caller wraps multiple operations
  in an outer bracket, the inner per-operation brackets collapse into it and
  exactly one `CompositionDidChangeNotification` fires. No double-notification
  hazard.

### Key files

- `src/main/java/songscribe/ui/component/ScoreMessageCoordinator.java`

---

## ⏳ Phase 6: Delete `ChangeType` API

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

## ⏳ Phase 7: Tests

**Model:** Sonnet  <br>
**Status:** Pending  <br>
**BlockedBy:** Phases 1–6

### Purpose

Unit-test every new mutation emission path and update any existing test
that asserts on `ChangeType`-based APIs.

### Tasks

1. **Mutation record tests** in `src/test/java/songscribe/message/mutation/`:
   - `IntervalMutationTest` (or per-type files) — construct each `Addition`
     and `Removal` record, verify `getLine()` and that the stored interval
     is the same instance that was passed in. Verify the record implements
     `LineScopedMutation`.
2. **Operation emission tests** in a new `MusicEditOperationsMutationTest`:
   - `testToggleBeamingAddEmitsBeamingAddition` — create a composition with
     a line, select a few notes, call `operations.toggleBeaming()` inside a
     real (not mocked) message-center capture, assert one notification
     containing exactly one `BeamingAddition` whose interval matches the
     new beam. No bracket glue in the test — the method opens its own.
   - `testToggleBeamingRemoveEmitsBeamingRemoval`
   - `testToggleTieAddEmitsTieAddition` / `testToggleTieRemoveEmitsTieRemoval`
   - `testToggleTupletAddEmitsTupletAddition` / `testToggleTupletRemoveEmitsTupletRemoval`
   - `testToggleTupletDoesNotPerformInPlaceGradeChange` — assert that
     calling `toggleTuplet` on a selection that matches an existing tuplet
     of a different grade results in a `TupletRemoval` (not a grade-change
     mutation).
   - `testAddDynamicsEmitsOneAddition` (parameterized on crescendo/diminuendo)
   - `testRemoveDynamicsEmitsRemovalPerInterval` — select a range covering
     multiple crescendos and diminuendos, assert N+M removal records.
   - `testToggleTrillEmitsOneElementModificationPerNote`
   - `testToggleLyricsUnderRestsEmitsOneElementModification`
   - `testFlipStemDirectionEmitsElementModificationPerAffectedIndex`
   - `testMakeFirstSecondEndingEmitsElementInsertionAndRangeElementAddition`
3. **Coordinator integration test** in
   `ScoreMessageCoordinatorTest` (or new file):
   - Verify that each command handler produces exactly one
     `CompositionDidChangeNotification` with the expected mutations
     (the single notification comes from the `MusicEditOperations`
     method's own bracket).
4. **`applyActionToSelection` tests**:
   - For an `ElementReplaceable` action (e.g. duration change), verify
     one `ElementReplacement` per selected element, plus zero-or-more
     `BeamingRemoval` / `BeamingAddition` records from the beam repair,
     plus zero-or-more `TupletRemoval` records from the tuplet
     invalidation. Assert that **no** `TieRemoval` / `TieAddition`
     records are ever emitted (guard against accidental revival of the
     dead tie branch).
   - For an `ElementModifiable` action (e.g. accidental), verify one
     `ElementModification` per affected element with the correct
     `ElementField` set, and **no** interval mutations at all (the
     modify path bypasses `validateIntervals` entirely).
5. **`validateIntervals` beam repair tests** (new, in
   `SelectionCoordinatorValidateIntervalsTest` or similar):
   - `testBeamUnchangedWhenAllElementsRemainBeamable` — duration
     change within the beam's range that keeps every element beamable
     (e.g. eighth → sixteenth) emits no beam mutations.
   - `testBeamTrimmedWhenStartElementBecomesNonBeamable` — a
     three-eighth beam with the first element replaced by a quarter
     emits one `BeamingRemoval` + one `BeamingAddition` for the
     truncated two-note beam.
   - `testBeamTrimmedWhenEndElementBecomesNonBeamable` — mirror of the
     above for the last element.
   - `testBeamKilledWhenInteriorElementBecomesNonBeamable` — a
     five-eighth beam with the middle element replaced by a quarter
     emits one `BeamingRemoval` and **no** `BeamingAddition`. This is
     the behavior change described in Phase 4.
   - `testBeamKilledWhenTrimLeavesFewerThanTwoElements` — a two-eighth
     beam with one element replaced by a quarter emits one
     `BeamingRemoval` and no addition.
6. **`validateIntervals` tuplet invalidation tests**:
   - `testOverlappingTupletsRemovedOnReplacement` — any
     `ElementReplaceable` replacement that overlaps a tuplet's range
     emits one `TupletRemoval` per overlapping tuplet.
   - `testNonOverlappingTupletsUntouched` — a tuplet wholly outside
     the modified range emits no mutations.
   - `testElementModifiableLeavesTupletsAlone` — an `ElementModifiable`
     action (accidental, fermata, etc.) on a selection that overlaps
     tuplets emits no `TupletRemoval` records.
7. **Update existing tests** in
   `CompositionDidChangeNotificationTest` that construct notifications via
   the deprecated constructors. All legacy-constructor calls should be
   replaced with the new `(List<Mutation>, Composition)` form.
8. **Regression: verify layout invalidation fires** — add an integration
   test that installs a spy `LinePanel` (or similar) and confirms that
   beam/tie/tuplet operations still trigger `invalidateLayout()` after the
   migration. This is the main user-visible correctness concern of the
   whole plan.

### Key files

- `src/test/java/songscribe/message/mutation/` (new record tests)
- `src/test/java/songscribe/music/MusicEditOperationsMutationTest.java` (new)
- `src/test/java/songscribe/ui/selection/SelectionCoordinatorValidateIntervalsTest.java` (new — beam repair + tuplet invalidation coverage)
- `src/test/java/songscribe/ui/component/ScoreMessageCoordinatorTest.java` (extend)
- `src/test/java/songscribe/message/notification/CompositionDidChangeNotificationTest.java` (update)

---

## Deferred Work (out of scope; revisit when needed)

1. **`TupletAction` enable/disable logic.** This plan removes the in-place
   grade-change branch from `toggleTuplet` (see Phase 3). The corresponding
   UI-level enable/disable rules for tuplet actions — and any related
   adjustments to how grade changes are surfaced to users — are deferred
   to a separate plan and are out of scope here.
2. **Tie-validity predicate audit outside `validateIntervals`.** The
   `validateIntervals` tie branch passed `isNote()` as its validity
   predicate, which technically permits grace notes inside a tie — a state
   that violates the "ties connect pitched notes only" invariant. This
   plan deletes the predicate along with the dead tie branch, but the
   same loose predicate may exist at other tie call sites (creation,
   rendering, playback). A follow-up audit should grep for `isNote()` in
   tie-handling code and tighten it to `isPitchedNote()` where
   appropriate.

---

## Risk assessment

**Scope estimate:** ~550 lines of production code across 6 files, ~400 lines
of test code across 6–7 files, 10 new mutation record files (each trivial:
a line reference and an interval).

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
2. **`makeFirstSecondEnding` compound mutations** — the method calls
   `line.addElement` / `line.addRangeElement` which already emit their own
   mutations. Wrapping in a bracket produces *multiple* mutations in one
   notification. Verify that `ScoreMessageCoordinator.hasLineLayoutMutation`
   still fires for the compound case (it should, because each component
   mutation implements `LineScopedMutation`).
3. **Beam repair semantic change** — the Phase 4 trim-and-kill rule
   produces a different result than the current repair-and-split logic
   when an interior element becomes non-beamable (see Phase 4 "Behavior
   change note"). This is an intentional shift aligned with the musical
   argument that a beam punctured in the middle isn't salvageable, but
   existing scores may render differently after a targeted selection
   edit. Surface the change in the commit message, add the Phase 7
   `testBeamKilledWhenInteriorElementBecomesNonBeamable` test, and scan
   the e2e suite for any snapshot test that depends on the old
   split-into-sub-beams behavior.
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
8. `toggleTuplet` emits only `TupletAddition` or `TupletRemoval` mutations.
   No in-place grade-change code path exists in `MusicEditOperations`.
