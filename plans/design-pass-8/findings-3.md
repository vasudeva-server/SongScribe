# Design pass 8 — step 3: make illegal states unrepresentable

Target: `dom`: `Tie`, `TieLoadPass`, `TieRule`, as step 2 left them. Paths are under `src/main/java/songscribe/`.

**If every item is accepted, the proposal retires 5 runtime checks and introduces 1 type.**

- The 5 checks: one `@Nullable` (item 1), two guards (items 2 and 3), and two catch-all arms over the sealed `Mutation` (items 5 and 6). Item 4 also retires the `noteAt` type check that `judge` reaches, and replaces it with a `requireParentLine` check, so it counts as zero.
- The 1 type: the reason enum in item 7. It closes a `String` and retires no check.

No candidate fits steps 1 to 3 of the order (a closed enum over a validated constructor, a wrapper over transposable primitives, an enum over a mode-selecting boolean or int). The target has no validating constructor, no two adjacent parameters of one primitive type, and no mode flag. The one boundary, `TieLoadPass`'s check of a file's anchor (`dom/TieLoadPass.java:61`), already converts: it gives a `NoteElement` that passes the pitched-note check, or reports the anchor as damaged.

## What it found

- `Tie.between` (`dom/Tie.java:74`) is private after step 2. Its two callers are `startingOn` (`:86`), which passes the element the walk reached after a null check, and `judge(NoteElement, StaffElement)` (`:239`), whose `end` is not nullable. Its parameter `end` is still `@Nullable`.
- `Tie.bothNotesIn` (`:133`) returns `false` when `range` does not contain `anchorIndex`. Both callers pass a range that contains it: `beamHoldingBoth` (`:147`) passes the range of `findBeamAt(anchorIndex)`, and `Beam.holdsTiedPair` (`dom/Beam.java:144`) passes an index from inside its range.
- `Line.addTie` (`dom/Line.java:523–524`) throws when the anchor fails `Tie.canAnchor`. Every production caller passes the check first: `TieLoadPass` through `tieableAnchor`, the toggle through `RangeQueries.canToggleTie` (the enablement predicate of the tie action, `ui/action/ToggleNotationAction.java:88`) or `tieCandidateWithPredecessor`, and `MutationReplayer` with a note that was tied. Step 2 kept the kind check itself (item 6b); this throw is one copy of it.
- `Tie.judge(Line, int anchorIndex)` (`:219`) has the precondition "the index of a note tied to the next". It reaches the note through `Line.noteAt`, which throws when the element is not a note (`dom/StaffElementRun.java`, `noteAt`). Both callers hold the note already: `TieRule.judge` as the pattern variable `anchor` (`dom/TieRule.java:87`), and `TieLoadPass.run` as `note`, from which it computes the index (`dom/TieLoadPass.java:66`).
- `Tie.judge(NoteElement, StaffElement)` (`:239`) accepts any two elements. Its Javadoc states two preconditions that no type carries: both elements are in lines, and `end` is at or after `anchor`'s line. `ChangeLines` does not check the second either (`dom/ChangeLines.java`). Its only caller outside `Tie` is `RangeQueries.canBeTied` (`ui/selection/RangeQueries.java:88`). `boundaryTieAt` passes a walk pair. `canToggleTie` passes the selection's first and last notes, with an index check on the selection size, and checks the separator after it asks the rule (`:153`, `:158`).
- `Tie.reachOrigin` (`:365–377`) switches over the sealed `Mutation` with `default -> null` (`:375`).
- `TieRule.touchedLine` (`dom/TieRule.java:66–80`) tests `LineScopedMutation`, `LineInsertion` and `LineDeletion` with `instanceof`, and returns `null` for every other kind.
- `Tie.Judgment.Broken.reasonKey` (`:199`) is a `String`. `judge` gives one of two keys: `REPORT_REASON_TIE_NO_FOLLOWING_NOTE` or `REPORT_REASON_TIE_NOT_HELD`. `TieLoadPass.run` builds its damaged-anchor entry with `REPORT_REASON_DAMAGED_WRONG_ELEMENT` outside `Judgment` (`dom/TieLoadPass.java:62–65`).

## What needs deciding

### 1. <span id="rd-c1">`Tie.between`'s parameter `end` becomes non-null</span>

`private static @Nullable Tie between(NoteElement anchor, StaffElement end)`. The `@param end` clause about `null` goes. The return stays nullable: a pair can fail the pitched-note check. No real downside.

### 2. <span id="rd-c2">Delete the dead guard in `Tie.bothNotesIn`</span>

Delete `if (!range.contains(anchorIndex)) return false;`. State the precondition instead: `@param range contains anchorIndex`. No real downside.

### 3. Delete `Line.addTie`'s throw

The precondition is already in `@param anchor` (`dom/Line.java:518`). After the delete, a caller that passes a grace note ties it, and `NoteElement.isTiedToNext`'s invariant breaks without a sign.

- Con: the only route that does not test the anchor itself is `MusicEditOperations.toggleTie`, which relies on the tie action's enablement. If that enablement is stale when the command arrives, the throw is what stops a tied grace note today.

Question: <span id="rd-c3">is a stale enablement possible in this application</span>? If yes, the check belongs at the UI edge, in `toggleTie`, and not in `Line`. If no, the throw is dead and goes.

### 4. <span id="rd-c4">`Tie.judge` takes the anchor note, not a line and an index</span>

`static Judgment judge(NoteElement anchor)`: it walks from `anchor` and judges the pair. The index precondition and the `noteAt` check go. `TieRule.judge` passes `anchor`, and `TieLoadPass.run` passes `note`. The anchor's line comes from `requireParentLine()`, which throws for a note in no line. Both callers pass notes in lines.

### 5. <span id="rd-c5">`Tie.judge(NoteElement, StaffElement)` becomes private, and the toggle asks item 4's judgment</span>

This revises step 2's item 1b. The pair overload lets a caller judge two elements that the walk does not join, and it carries two preconditions that no type checks. With item 4, every public judgment starts from an anchor, so the pair is always a walk pair:

- `boundaryTieAt` asks `Tie.judge(candidate.anchor())`.
- `canToggleTie` checks the selection's shape first (the size, and a legal separator in the middle of a three-element selection), then asks `Tie.judge(anchorNote)`. The shape check makes `endNote` the anchor's walk end.

The pair overload stays as the private body of item 4. This item depends on item 4.

- Con: `canToggleTie` depends on its shape check to make the walk end the selected end note. If a later change to the shape check lets through a selection whose end is not the walk end, the rule judges the walk end and not the selected note.

### 6. <span id="rd-c6">`Tie.reachOrigin` and `TieRule.touchedLine` become exhaustive switches</span>

Replace `default -> null` (`dom/Tie.java:375`) with one arm for each remaining `Mutation` kind, each `-> null`. Turn `TieRule.touchedLine`'s `instanceof` chain into a switch with a `LineScopedMutation` arm, the `LineInsertion` and `LineDeletion` arms, and one arm for each other kind, `-> null`. The behavior does not change. A new mutation kind then does not compile until someone decides whether it reaches a line and which line it touches. No real downside.

### 7. <span id="rd-c7">A closed reason in `Judgment.Broken`</span>

Replace `String reasonKey` with an enum `Tie.Judgment.Reason`, each value holding its `Strings` key. `entry()` reads the key from the reason.

Question: does the load's damaged anchor belong in the enum?

- **a. Two values**, `NO_FOLLOWING_NOTE` and `NOT_HELD`: the outcomes of `judge`. `TieLoadPass` keeps building its own entry.
  
  - Con: the reasons a tie is dropped stay in two places: the enum, and the literal key in `TieLoadPass.run` (`dom/TieLoadPass.java:64`).
- **b. Three values**, with `DAMAGED_ANCHOR`. `TieLoadPass` reports `new Judgment.Broken(Reason.DAMAGED_ANCHOR, ChangeLines.on(line)).entry()`.
  
  - Con: `judge`'s result type then admits a reason that `judge` never gives. No caller switches on the reason today; one that did would have an arm it can never reach.

---
roughdraft: "1.0"
comments:
  rd-c1:
    body: yes
    by: user
    at: "2026-11-09T01:51:51.691Z"
  rd-c2:
    body: yes
    by: user
    at: "2026-11-09T01:52:00.612Z"
  rd-c3:
    body: shouldn't be, if it is it's a bug, so don't guard against it
    by: user
    at: "2026-11-09T01:52:32.346Z"
  rd-c4:
    body: agreed
    by: user
    at: "2026-11-09T01:52:47.310Z"
  rd-c5:
    body: ok
    by: user
    at: "2026-11-09T01:54:12.033Z"
  rd-c6:
    body: agreed
    by: user
    at: "2026-11-09T01:54:28.040Z"
  rd-c7:
    body: need more context
    by: user
    at: "2026-11-09T01:56:12.576Z"
