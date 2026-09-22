# Findings — step 4, pass 6b, _Accidentals_

Paths are under `src/main/java/songscribe/` unless stated.

## What it found

| Where | Operation | Statable without a caller? |
|---|---|---|
| `Song.java:1428`, `Song.java:1923`, `layout/AccidentalFitGate.java:147`, `ui/edit/GraceModeManager.java:571`, `:778` | `restamp.note().setAccidentalState(restamp.state())` — apply a restamp to its note | Yes: "stamp the note with the state the walk decided". Written five times. |
| `dom/AccidentalChain.java:396–409`, `Walk.keyBefore` and `lastKeyChangeBefore` | The key in force just before a place on a line: the last key change strictly before it, else the running key | Yes. `Line.keyAt(int)` (`Line.java`) answers the same question with an inclusive bound, and treats `elementCount()` as "just past the last element", which is this exclusive reading. |
| `dom/AccidentalChain.java:295`, `:392`, and `ui/action/AccidentalAction.java:252–253` | `key.alterationFor(PitchClass.at(position.value()))` — the key's alteration at a staff position | Yes. Written in both classes; `Walk.pitchClassOf` (`:442`) exists only to feed it. |
| `dom/AccidentalChain.java:437`, `Walk.skipped` | `type.isRest() \|\| type.isBreathMark()` — an element the chain walk looks through when deciding what intervenes | Yes. `ElementType` already holds the two sibling predicates `intervenesInChain()` and `endsAccidentalsInForce()`. |
| `dom/ChainEdit.java`, `positionsOf`, `anyRekeys`, `endsInForce`, `reachOf` | Private helpers over element lists | Each names its argument, not a caller. They stay private on the type that builds edits. |
| `dom/AccidentalChain` | A `final` class of two public static methods and one private nested class | Not a `*Utils` bag: it holds one algorithm, the chain walk, and the class is named for the concept. It stays. |

## What needs deciding

### 1. `Restamp.stamp()`

<span id="rd-c1">`AccidentalChain.Restamp` gains `void stamp()`</span>: sets the note's accidental state to the decided state, plainly, recording nothing. The five call sites become `restamp.stamp()`, and the two loops that do nothing else (`Song.stampAccidentalsAfterParsing`, `GraceModeManager.abort`) become `forEach(Restamp::stamp)`. `Song.applyChange` passes `restamp::stamp` as the mutator to `Line.modifyElement`, so recording stays where it is.

The invariant `Restamp` owns: a restamp names the note and the state together, so nothing can stamp one restamp's note with another's state.

- Pro: the sequence "walk, then stamp plainly" is one method call at every consumer, and a consumer cannot pair a note with the wrong state.
- Con: none real.

**Option B. Leave the five sites.**

- Con: the duplication stays.

### 2. `Walk.keyBefore` reads `Line`, and `Line` gains the question every caller asks

`Line.keyAt(int)` is inclusive: a key change at the index is in effect at it. The walk needs the key strictly before its start, because the element there may be the key change the edit inserted, which the walk applies itself.

Of `keyAt`'s ten production callers, eight ask the exclusive question by subtracting one first, three of them with a guard for index 0:

- `Line.keyAtEndOfLineAfterRemoving` `:340` — `begin > 0 ? keyAt(begin - 1) : getRunningKey()`
- `KeyChangeElement.previousKey` `:192` — `keyAt(getElementIndex(this) - 1)`
- `layout/KeyEditFitCalculator.keySignatureColumn` `:375` — `keyAt(index - 1)`
- `io/musicxml/MeasureBuilder.buildLine` `:340` — `keyAt(i - 1)`
- `ui/dialog/KeyChangeDialogController.changeMidLineKey` `:289` — `keyAt(elementIndex - 1)`
- `layout/InsertionSpacingCalculator.calculateFragmentInsertion` `:418` — `insertIndex >= 1 ? keyAt(insertIndex - 1) : getRunningKey()`
- `ui/component/ScoreViewController.deletionStranding` `:1213` — `keyAt(Math.max(range.begin() - 1, 0))`
- `ui/component/ScoreViewController.tryInsertFragment` `:1367` — `keyAt(Math.max(insertIndex - 1, 0))`

Two ask the inclusive one: `KeyChangeSite.keyInEffect` `:115` and `AccidentalAction.keyAlterationAt` `:252`.

<span id="rd-c2">**Option A.** `Line.keyBefore(int index)`</span>: the key in force just before `index` — the running key, overridden by the last key change at an index below it; `0..elementCount()`, so an insertion index is valid. The eight callers above call it and drop their `- 1` and their guards; `keyAt` keeps its two callers and its inclusive contract. `Walk.keyBefore` becomes `view.line(lineIndex).keyBefore(index)` and `lastKeyChangeBefore` is deleted.

`Line` is row 6's, marked ✅; `KeyEditFitCalculator`, `InsertionSpacingCalculator`, `MeasureBuilder`, `KeyChangeDialogController` and `ScoreViewController` belong to rows 18, 16, 23 and 25. The review decides whether this pass makes the eight call-site changes or the finding goes to the register with them listed.

- Pro: the walk stops re-deriving what `Line` owns, and the `- 1` with its three hand-written zero guards is written once, on the type.
- Con: none real.

**Option B.** `Walk.keyBefore` **stays as it is.**

- Con: two walks over a line's key changes exist, one on `Line` and one in the chain walk, and they agree by coincidence. The eight `- 1` callers stay.

### 3. `Key.alterationAt(StaffPosition)`

<span id="rd-c3">`Key` gains `Accidental alterationAt(StaffPosition position)`:</span> the key's alteration for the pitch class written at `position`. `Walk.keyAlterationFor` and `Walk.decide` call it; `Walk.pitchClassOf` is deleted; `AccidentalAction.keyAlterationAt` calls `line.keyAt(index).alterationAt(…)`.

The invariant `Key` owns: which pitch classes it alters. `alterationFor` is already there; this states the same rule for a staff position.

- Pro: the two sites that convert a position to a pitch class by hand share one method on the type that owns the rule.
- Con: none real.

**Option B. Keep the inline conversion.**

- Con: the expression is written in two classes.

### 4. <span id="rd-c4">`ElementType.isTransparentInChain()`</span>

`ElementType` gains a third chain predicate beside `intervenesInChain()` and `endsAccidentalsInForce()`: whether the walk looks through this element when deciding what intervenes — a rest or a breath mark. `Walk.skipped` calls it.

- Pro: the three facts the walk reads about an element type sit together on the type, and `docs/accidentals.md`'s "skipping rests and breath marks" has one home in code.
- Con: none real.

**Option B. Keep** `Walk.skipped`**.**

- Con: two of the walk's three type predicates live on `ElementType` and the third in the walk.

---
roughdraft: "1.0"
comments:
  rd-c1:
    body: ok
    by: user
    at: "2026-09-22T01:08:25.492Z"
  rd-c2:
    body: ok
    by: user
    at: "2026-09-22T01:09:36.278Z"
  rd-c3:
    body: ok
    by: user
    at: "2026-09-22T01:09:54.232Z"
  rd-c4:
    body: ok
    by: user
    at: "2026-09-22T01:10:20.167Z"
