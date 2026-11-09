# Design pass 8 — step 4: extraction and placement

Target: `dom`: `Tie`, `TieLoadPass`, `TieRule`, as step 3 left them (commit `9799654c`). Paths are under `src/main/java/songscribe/`.

The target has no `*Utils` or `*Helper` class. Every static in it states a rule about ties, so no item goes back to step 2. The items below ask which type owns each rule.

## What it found

### The walk's statics on `Tie`

- `Tie.isLegalSeparator(ElementType)` (`dom/Tie.java:174`) is a function of one `ElementType` value. Its readers: `Tie.firstNonSeparatorIndexFrom` and `Tie.lastNonSeparatorIndexBefore` (`:308`, `:316`), and `RangeQueries.canToggleTie` (`ui/selection/RangeQueries.java:155`). Javadoc links name it in the `Tie` class Javadoc (`:50`), in `Tie.reach` (`:338`), and in `NoteElement.tie` and `NoteElement.incomingTie` (`dom/NoteElement.java:178`, `:199`).
- `ElementType.isNonDuration()` is `isBar() || isBreathMark() || isKeyChange()`. `isLegalSeparator` takes `isNonDuration()`, then removes `FINAL_DOUBLE_BARLINE` and every key change. So the rule it applies is "a bar or a breath mark, other than a final double barline".
- `ElementType` already holds a predicate that one subsystem defines: `isTransparentInChain()` (rest or breath mark) is the accidental chain's rule.
- `Tie.firstNonSeparatorIndexFrom(List, int)` and `Tie.lastNonSeparatorIndexBefore(List, int)` (`:308`, `:316`) take an element list. Every caller passes the element list of one `Line`:
  
  - `Tie.endAfter` and `Tie.anchorBefore`, for the anchor's line and for the next or previous line (`:266`, `:279`, `:286`, `:299`);
  - `Tie.reach`, for the origin's line (`:361–362`);
  - `Line.untieEndingIn` and `Line.enteringTie`, for their own `elements` (`dom/Line.java:553`, `:612`);
  - `RangeQueries.boundaryTieAt`, for `line.getElements()` (`ui/selection/RangeQueries.java:74–75`).
- `Line` is a `StaffElementRun` (through `EditableRun` and `LyricRun`). The general index scans that the two helpers wrap, `StaffElementRun.firstIndexFrom` and `StaffElementRun.lastIndexBefore`, are statics over a list there. The other implementors of `StaffElementRun` are `DetachedLyricRun` and `ProjectedEdit.ProjectedRun`. No caller asks either of them for a tie separator.
- `Tie.endAfter(Line, int)` and `Tie.anchorBefore(Line, int)` (`:264`, `:284`) are package-private. All of their callers are in `Tie`: `startingOn`, `judge(NoteElement)` and `bothNotesIn` call `endAfter`; `endingOn` calls `anchorBefore`. Three of the four callers hold the note and compute its index with `line.getElementIndex(...)` only to pass it (`:100`, `:120`, `:245`).

### The beam conflict

- `Tie.bothNotesIn(Line, int, ElementRange)` (`:140`) and `Tie.beamHoldingBoth(Line, int)` (`:151`) take a line and an anchor index. Every caller holds the anchor note and converts it to an index:
  
  - `Beam.holdsTiedPair` (`dom/Beam.java:144`) loops over the indices of a range, and asks for a note that `isTiedToNext()`;
  - `TieRule.judge` (`dom/TieRule.java:91`) asks after `Tie.judge(anchor)` gave `Holds`;
  - `RangeQueries.canToggleTie` (`ui/selection/RangeQueries.java:167`) asks after `canBeTied(anchorNote)`, which is `Tie.judge(anchorNote) instanceof Holds`.
- `bothNotesIn` counts the element that `endAfter` reaches, whatever it is. The `Tie` that `startingOn` gives exists only when that element is a note that passes the pitched-note check (`between`, `:81`). The two agree at `TieRule` and at `canToggleTie`, because both ask only after a `Holds`. They disagree at `Beam.holdsTiedPair` for one case: a tied note whose walk reaches an element that is not a pitched note, such as a rest or a grace note, inside the widened range.
- <span id="rd-c6">That case is a broken tie</span>. `TieRule` removes it, with `REPORT_REASON_TIE_NO_FOLLOWING_NOTE`, when the bracket closes. `Beam.spanning` refuses the beam with `REPORT_REASON_BEAM_TIED_NOTES` before that. So the edit loses the beam and the tie both. Readers cannot give this case: `StampedLoad.place` runs `TieLoadPass` before the beams (`dom/StampedLoad.java`), and `TieLoadPass` drops every broken tie.
- `Tie.Judgment.Holds` (`:196`) carries nothing. `judge(NoteElement, StaffElement)` makes it only where `between` gave a non-null tie (`:255–256`).

### The pitched-note check

- `Tie.canAnchor(StaffElement)` (`:130`) is `element instanceof NoteElement && element.getType().isPitchedNote()`. Every caller passes a `NoteElement`:
  
  - `Tie.between`, for `anchor` and for `endNote` after its pattern test (`:82`);
  - `NoteElement.copySubtypeStateFrom`, for `this` (`dom/NoteElement.java:420`);
  - `TieLoadPass.tieableAnchor`, after its own `instanceof NoteElement` (`dom/TieLoadPass.java:74`).
- `NoteElement` owns the field `tiedToNext`, and `NoteElement.isTiedToNext()` states the invariant the check keeps: true only on a note that passes it. Javadoc links name `Tie#canAnchor` in `NoteElement` (class Javadoc, `isTiedToNext`, `tie`, `copySubtypeStateFrom`, `applySubtypeConversionFrom`), `Line.addTie`, `TieLoadPass.run` and the `Tie` class Javadoc.

### `TieLoadPass.run`

- `run` (`dom/TieLoadPass.java:51–68`) adds the tie of every pending tie whose anchor passes `tieableAnchor`, in a first loop. A second loop calls `tieableAnchor` again for each pending tie, judges it, and removes the broken ones.
- The judgment reads no tie state. `Tie.judge` reads `endAfter`, `between` and `holds`. `holds` reads the staff positions and the sounding alterations. Whether a note is tied changes none of these.
- During a load, `Song.applyChange` records nothing and runs no chain walk (its Javadoc, `dom/Song.java`). So `Line.addTie` and `Line.removeTie` change only `tiedToNext`, and the order of add and judge changes no other note's state.
- So one loop that judges first and adds only a tie that holds leaves the same ties and records the same entries in the same order. That includes two pending ties on one anchor: the second `addTie` returns early in both forms.

### What stays where it is

- `Tie.reach` and its private origins stay on `Tie`; step 2 confirmed that (it matches `KeyInheritance.keyMoveReach`).
- `Tie.judge`, `Tie.Judgment`, `Tie.isAbove` and `Tie.arcSign` are functions of a tie, and are on it.
- `TieRule.touchedLine` (`dom/TieRule.java:70–77`) answers which line of a mutation the tie rule judges, including the line that moves up after a deletion. `KeyInheritance.keyMoveOf` and `ChainEdit.of` switch over the same mutation kinds for their own questions; they are not a copy of it.

## What needs deciding

### 1. <span id="rd-c1">`Tie.isLegalSeparator(ElementType)` becomes `ElementType.isTieSeparator()`</span>

The rule is a function of one `ElementType` value, so it goes on `ElementType`, as `isTransparentInChain()` does for the accidental chain. Its Javadoc moves with it: the reason that a final double barline and a key change are not separators, and the warning that the walk and the toggle must not disagree. `RangeQueries.canToggleTie` asks `type.isTieSeparator()`. The Javadoc links in `Tie`, `Tie.reach`, `NoteElement.tie` and `NoteElement.incomingTie` point to the new member. The name `isTieSeparator` is a proposal; give another if you want one.

The body has two forms:

- **a.** <span id="rd-c2">**Keep the present form**</span>, `isNonDuration() && this != FINAL_DOUBLE_BARLINE && !isKeyChange()`. A type that is later added to `isNonDuration()` becomes a tie separator automatically.
  
  - No real downside.
- **b. State the result directly**, `(isBar() || isBreathMark()) && this != FINAL_DOUBLE_BARLINE`.
  
  - Con: a type that is later added to `isNonDuration()` is not a tie separator until someone adds it here, and nothing tells them to.

### 2. <span id="rd-c3">The two index helpers become instance queries of the line</span>

`firstNonSeparatorIndexFrom(int from)` and `lastNonSeparatorIndexBefore(int before)`, with the same preconditions and the same `null` meaning. Every caller passes one line's elements, so the list parameter goes. `endAfter`, `anchorBefore`, `reach`, `Line.untieEndingIn`, `Line.enteringTie` and `RangeQueries.boundaryTieAt` call them on the line. They must be public, because `RangeQueries` is in `ui/selection`.

The owner is a choice:

- **a.** `Line`**.** The walk crosses from a line to its next and previous lines, and only a line is ever asked.
  
  - No real downside.
- **b.** `StaffElementRun`**, as default methods** next to `firstIndexFrom` and `lastIndexBefore`, which they wrap.
  
  - Con: `DetachedLyricRun` and `ProjectedEdit.ProjectedRun` get the two queries, and no caller asks them.

### 3. `Tie.endAfter` and `Tie.anchorBefore` become private, and take the note

`private static @Nullable StaffElement endAfter(Line line, NoteElement anchor)` and `anchorBefore(Line line, NoteElement end)`. All callers are in `Tie`, so package visibility exposes them for nothing. After item 4, every caller holds the note, so the index lookup moves inside and the repeated `line.getElementIndex(...)` at `:100`, `:120` and `:245` goes. The line stays a parameter because `startingOn` and `endingOn` return `null` for a note in no line, and `judge` requires a line. No real downside.

### 4. The beam conflict becomes two instance members of `Tie`, and `Holds` carries the tie

- `boolean bothNotesIn(ElementRange range)`: the end is on the anchor's line and `range` contains its index. `range` is a range of the anchor's line.
- `@Nullable Beam beamHoldingBoth()`: the beam at the anchor's index, when it holds both notes.
- `Tie.Judgment.Holds` becomes `record Holds(Tie tie)`. `judge` already has the tie where it makes `Holds`.

The callers change as follows:

- `TieRule.judge` asks `case Holds(var tie) -> tie.beamHoldingBoth()`.
- `RangeQueries.canToggleTie` asks `Tie.judge(anchorNote) instanceof Holds(var tie)`, then `tie.beamHoldingBoth()`. `canBeTied` stays for `boundaryTieAt`.
- `Beam.holdsTiedPair` asks `note.tie()`, and `tie.bothNotesIn(range)` when the tie is not `null`.

At `TieRule` and `canToggleTie` the result is unchanged. At `Beam.holdsTiedPair` it changes for the broken-tie case in _What it found_: a tied note whose walk reaches a rest or a grace note inside the widened range. Today `Beam.spanning` refuses the beam, and `TieRule` then removes the tie when the bracket closes, so the edit keeps neither. With this item, `note.tie()` is `null`, the beam is made, and `TieRule` removes the broken tie, so the edit keeps the beam.

Is that the outcome you want for the broken-tie case?

- **Yes:** the item is correct as stated, and the change is a defect fix. `Beam.spanning`'s Javadoc for `REPORT_REASON_BEAM_TIED_NOTES` then reads "the widened range holds both notes of a tie" with a link to the instance member. Step 6 proposes the test.
- **No, refuse the beam over any tied note whose walk ends in the range:** then the beam conflict is not a question about a `Tie`, because no `Tie` exists for that pair. `bothNotesIn` stays a static over a line and the anchor note, `(Line line, NoteElement anchor, ElementRange range)`, and only `beamHoldingBoth` becomes the instance member.

### 5. `Tie.canAnchor(StaffElement)` becomes `NoteElement.canCarryTie()`

`public boolean canCarryTie() { return getType().isPitchedNote(); }`. Every caller holds a `NoteElement`, so the `instanceof` test in `canAnchor` is dead at every call site. The owner is `NoteElement`, because it holds `tiedToNext` and states the invariant that the check keeps. `between` asks `anchor.canCarryTie() && end instanceof NoteElement endNote && endNote.canCarryTie()`. `TieLoadPass.tieableAnchor` keeps its own `instanceof NoteElement`, which is the boundary's conversion of a file's element. The Javadoc links listed in _What it found_ point to the new member, and the term "pitched-note check" in the `Tie` class Javadoc names it. The name `canCarryTie` is a proposal; give another if you want one. No real downside.

### 6. `TieLoadPass.run` judges each pending tie once, and adds only a tie that holds

One loop over `ties`, in order:

1. When the anchor fails `tieableAnchor`, report the `DAMAGED_ANCHOR` entry on the anchor's line.
2. Otherwise, when `Tie.judge(note)` is `Broken`, report its entry.
3. Otherwise, call `line.addTie(note)`.

`tieableAnchor` then has one caller. `Line.removeTie` is no longer called from the load. The `@effects` clauses of `run` stay true without change. No real downside.

---
roughdraft: "1.0"
comments:
  rd-c1:
    body: Ok, but ElementType.isLegalTieSeparator()
    by: user
    at: "2026-11-09T02:51:42.986Z"
  rd-c2:
    body: this
    by: user
    at: "2026-11-09T02:52:05.707Z"
  rd-c3:
    body: ok, option a
    by: user
    at: "2026-11-09T02:52:52.271Z"
  rd-c6:
    body: If the only case where they disagree is a broken tie, then what's the problem?
    by: user
    at: "2026-11-09T02:57:34.813Z"
  rd-c7:
    body: unless otherwise noted, proposal is accepted
    by: user
    at: "2026-11-09T02:59:39.593Z"
    scope: document
