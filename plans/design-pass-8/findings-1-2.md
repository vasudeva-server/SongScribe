# Design pass 8 — steps 1 and 2: inventory and class design

Target: `dom`: `Tie`, `TieLoadPass`, `TieRule`. Paths are under `src/main/java/songscribe/` unless they start with `src/test/`.

## What it found

### The types

`Tie` (`dom/Tie.java`, 435 lines). A final class with two fields, `anchor` and `end`, both `NoteElement`. The private constructor is reached only from `between` (`:73`). The file holds five groups of members:

1. **The value.** `anchor()`, `end()`, `isAbove()` (`:362`), `arcSign()` (`:377`), the private `tieArcDirection()` (`:381–419`), `equals`, `hashCode`, `toString`.
2. **The walk.** `isLegalSeparator(ElementType)` (`:126`), `firstNonSeparatorIndexFrom` (`:240`), `lastNonSeparatorIndexBefore` (`:248`), `endAfter(Line, int)` (`:198`), `anchorBefore(Line, int)` (`:216`), the record `Reached(Line, int)` (`:142`), and the pitched-note check `canAnchor(StaffElement)` (`:79`) with the factory `between` (`:73`).
3. **The judgment.** `holds()` (`:137`), `judge(Line, int)` (`:182`), and the sealed `Judgment` with `Holds` and `Broken(String reasonKey, ChangeLines where)` (`:153–168`).
4. **The beam conflict.** `bothNotesIn(Line, int, ElementRange)` (`:88`) and `beamHoldingBoth(Line, int)` (`:103`).
5. **The redraw reach.** `reach(Mutation)` (`:284`), with the private `reachOrigin`, `elementOrigin`, `tieAnchorOrigin`, `beamOrigin` and the record `ReachOrigin` (`:308–360`).

Who calls each group:

| Group | Production callers |
| --- | --- |
| Value | `ScorePartwiseBuilder.addTie` (`isAbove`), `LayoutEngine.tieLayout` and `NoteAttachedStacker.seedTieBounds` (`arcSign`), `Line.placement`, `TieRenderer`, `LayoutResultBuilder`, `SelectionCoordinator`, `HitTarget.Tie`, `Fragment` |
| Walk | `NoteElement.tie` (`endAfter`, `between`), `NoteElement.incomingTie` (`anchorBefore`), `NoteElement.copySubtypeStateFrom` (`canAnchor`), `Line.addTie` (`canAnchor`), `Line.untieEndingIn` and `Line.enteringTie` (`firstNonSeparatorIndexFrom`), `RangeQueries.boundaryTieAt` (both index helpers), `RangeQueries.canBeTied` (`between`), `RangeQueries.canToggleTie` (`isLegalSeparator`), `TieLoadPass.tieableAnchor` (`canAnchor`) |
| Judgment | `TieRule.judge`, `TieLoadPass.run` (`judge`) |
| Beam conflict | `Beam.holdsTiedPair` (`bothNotesIn`), `TieRule.judge` and `RangeQueries.canToggleTie` (`beamHoldingBoth`) |
| Redraw reach | `ScoreViewController.songDidChange` (`ui/component/ScoreViewController.java:484`) only |

Invariants that no type carries:

- **U1. A** `Tie` **need not be a tie the walk makes.** The class Javadoc says a tie is "as `NoteElement.tie()` and `NoteElement.incomingTie()` derive it". But `between` is public and accepts any two notes that pass the pitched-note check. `RangeQueries.canBeTied` (`ui/selection/RangeQueries.java:109`) makes a `Tie` for a selected pair only to test it against `null`.
- **U2. The tie state sits on notes that cannot carry it.** `NoteElement` is final and covers at least the kinds `NOTE` and `GRACE_NOTE`. Its field `tiedToNext` exists on both, and the kind check `Tie.canAnchor` keeps it `false` on a grace note. That check runs in `Tie.between` (`:74`), `Line.addTie` (`dom/Line.java:523`), `NoteElement.copySubtypeStateFrom` (`dom/NoteElement.java:441`), `NoteElement.applySubtypeConversionFrom` (per its Javadoc) and `TieLoadPass.tieableAnchor` (`dom/TieLoadPass.java:77`).
- **U3.** `Judgment.Broken.reasonKey` **is a** `String`**.** `judge` produces one of exactly two keys, `REPORT_REASON_TIE_NO_FOLLOWING_NOTE` and `REPORT_REASON_TIE_NOT_HELD`. `TieLoadPass` builds a third entry, `REPORT_REASON_DAMAGED_WRONG_ELEMENT`, outside `Judgment` (`dom/TieLoadPass.java:62–65`).
- **U4.** `holds()` **has a time precondition.** It must be asked only after every restamp of the edit has landed (`:134–135`, `:170–171`). Nothing in a signature carries this.
- **U5.** `reachOrigin` **ends in** `default -> null` (`:318`) over `Mutation`, which is sealed. A new mutation kind reaches no line, and the compiler does not ask.
- **U6.** `Reached(Line, int)` **holds what the element already holds.** An element in a line knows its parent line, and `Line.getElementIndex` answers its index from a cached identity map (`dom/Line.java`, `getElementIndex`).

`TieLoadPass` (`dom/TieLoadPass.java`, 79 lines). A final class with a private constructor and one static `run(Song, List<PendingTie>)`, which `StampedLoad.place` calls before the beam and trill load passes. `run` makes two loops over the pending ties. The first adds every tie whose anchor passes `canAnchor`. The second reports each anchor that fails it, and judges and removes each added tie that `Tie.judge` finds broken. `tieableAnchor` is called twice for each pending tie. `run` has a full contract (`@effects`, `:40–50`).

`TieRule` (`dom/TieRule.java`, 107 lines). One of the three `ModificationRule` implementations, with `EndingSectionRule` and `SongStartRule`. `apply` collects the line each mutation touches and the line before it, then judges every tied note of those lines. A tie that holds removes any beam that holds both of its notes. `apply` has no Javadoc. The rule is stated only in the class Javadoc.

### Every `@Nullable`

| Where | Which answer |
|---|---|
| `Tie.between` return (`:73`) | Honestly partial: the pair may fail the pitched-note check. |
| `Tie.between` parameter `end` (`:73`) | **No caller passes `null`.** `NoteElement.tie` and `judge` pass `reached.element()` after a null check; `RangeQueries.canBeTied` passes a `NoteElement`. |
| `Tie.beamHoldingBoth` return (`:103`) | Honestly partial. |
| `Tie.endAfter`, `Tie.anchorBefore` returns (`:198`, `:216`) | Honestly partial: the walk can reach nothing. |
| `firstNonSeparatorIndexFrom`, `lastNonSeparatorIndexBefore` returns (`:240`, `:248`) | Honestly partial. |
| `reachOrigin`, `tieAnchorOrigin`, `beamOrigin` returns (`:308–357`) | Partial: a later record of the same batch can remove the anchor or the beam end. `reachOrigin`'s `default` arm is U5. |
| `TieLoadPass.tieableAnchor` return (`:76`) | Honestly partial: a damaged file can tie a rest. |
| `TieRule.touchedLine` return (`:66`) and the `Song.indexOfLine` result (`:48`) | Honestly partial: a mutation with no line, or a line a later record deleted. |

### Every guard

| Guard | Callers that can produce the rejected value |
|---|---|
| `Tie.bothNotesIn`: `!range.contains(anchorIndex)` returns `false` (`:89`) | **None.** `beamHoldingBoth` passes the range of `findBeamAt(anchorIndex)`, which contains `anchorIndex` by its contract. `Beam.holdsTiedPair` (`dom/Beam.java:144`) passes an index from inside the range. |
| `Tie.between`: `canAnchor(anchor)` (`:74`) | Only `RangeQueries.canBeTied`, from a user selection. `NoteElement.tie` and `judge` pass a tied note, which passes by `isTiedToNext`'s invariant. |
| `Line.addTie`: throws on `!canAnchor` (`dom/Line.java:523`) | **None in production.** `TieLoadPass` filters with `tieableAnchor`. The toggle reaches `addTie` through `MusicEditOperations.toggleTieBetween` (`ui/MusicEditOperations.java:185`) after `RangeQueries.canBeTied` or `tieCandidateWithPredecessor`. `MusicEditOperations.toggleTie` applies no check of its own, and its Javadoc (`:163`) says its caller consulted `canToggleTie`. `MutationReplayer` re-adds a tie that existed. |
| `Line.addTie` returns early when already tied (`:527`); `Line.removeTie` returns early when not tied (`:539`) | No edit-time caller. The load reaches it only if the reader gives two pending ties on one anchor. I did not check whether the MusicXML reader can do that; the reader is row 16's. |
| `TieLoadPass`: `canAnchor` refusal (`:61`) | A damaged file. This is a boundary, and the check is correct there. |
| `TieRule.apply`: `touched == null \|\| touchedIndex == null` skips (`:50`) | Real: see the `@Nullable` table. |

### The methods a test derives from

| Method | Test | Contract today |
|---|---|---|
| `NoteElement.tie`, `NoteElement.incomingTie` | `TieWalkTest` walk and separator cases | `@return` and `@invariant` clauses |
| `Line.ownedTies` | `TieWalkTest` ownership case | prose contract |
| `Tie.reach` | `TieWalkTest` reach cases | full prose contract |
| `TieRule.apply` | all of `TieRuleTest` | **none on the method**; the rule is in the class Javadoc |
| `TieLoadPass.run` | `TieLoadPassTest` | full contract |

`Tie.arcSign` and `tieArcDirection` are an algorithm (stem directions, then staff position), and no test in the target reaches them.

### Guides

- `guides/subsystems/tie-placement.md` lists only `Tie` and `BezierBow`, and holds no rules.
- `guides/subsystems/span-invalidation.md` lists `TieLoadPass` and `TieRule`, and has one tie rule: judge a tie in a `ModificationRule`, never in `Span.outcomeFor`.
- `guides/subsystems/undo.md` lists `TieRule`.
- `guides/subsystems/change-report.md` holds the rules for reported removals that `TieRule` and `TieLoadPass` follow.

### The tests

| Class | Cases | What it covers |
|---|---|---|
| `src/test/java/songscribe/dom/TieWalkTest.java` | 35 | the end of a tie's walk (10), every non-duration type as a separator (8), ownership across a line break (1), `Tie.reach` (16) |
| `src/test/java/songscribe/dom/TieRuleTest.java` | 17 | edits on one line and what stays of the tie (11), a tied note in a beam (1), one undo restores the tie (1), edits on the next line for a crossing tie (4) |
| `src/test/java/songscribe/dom/TieLoadPassTest.java` | 8 | pending ties kept, dropped as damaged, or dropped as broken, with the report order |

All 60 pass at `77e93179`.

### Outside the target

- **The toggle states a second tie rule.** `RangeQueries.canBeTied` (`ui/selection/RangeQueries.java:108–111`) accepts a pair when `between` makes a tie and the two MIDI pitches are equal. `Tie.holds` (`:137–140`) requires one staff position and one sounding alteration. The two disagree on an enharmonic pair, such as E♯ followed by F: equal MIDI pitch, different staff positions. The toggle offers the tie and adds it. When the bracket closes, `TieRule` removes it and reports `REPORT_REASON_TIE_NOT_HELD`. The Javadoc of `canBeTied` calls it "the one statement of the tie rule", which is false. The accidental walk does not read whether a note is tied, so adding the tie cannot change either note's sounding alteration.
- **The toggle has its own copy of the walk.** `RangeQueries.boundaryTieAt` (`:72–106`) finds the note across a line break from a note at a line's edge. At the line's first non-separator it takes the previous line's last non-separator, which is what `Tie.anchorBefore` does. At the line's last non-separator it takes the next line's first non-separator, which is what `Tie.endAfter` does. Its result type, `RangeQueries.BoundaryTie(NoteElement anchor, NoteElement end)` (`:67`), has the same shape as `Tie`.

## What needs deciding

### 1. The toggle's tie rule disagrees with `Tie.holds` — a defect

The defect is in _Outside the target_. The fix: `RangeQueries.canBeTied` asks `Tie`'s rule and no longer compares MIDI pitches. `holds()` is package-private, and `RangeQueries` is in `ui/selection`, so `Tie` must expose the rule. A test goes with the fix. Step 6 proposes it.

The shape of the fix is a choice:

- **a. Make** `holds()` **public.** `canBeTied` becomes "`between` makes a tie and it holds". `judge` does not change.
  
  - Con: `holds()` carries the time precondition U4 as a public member. `RangeQueries` asks outside any bracket, where it holds.
- <span id="rd-c1">**b. Give** `Tie` **one judgment over a pair**</span>, for example `static Judgment judge(NoteElement anchor, StaffElement end)`. `judge(Line, int)` becomes the walk followed by that judgment, and `canBeTied` asks whether it is `Holds`. The two reasons stay in one place.
  
  - No real downside. `Broken.where` is then built from the two elements' parent lines, and every caller passes elements that are in lines: the walk and the selection give no others.

Item 3 changes what `between` is. Under item 3, form b is the only public way to ask the rule.

### 2. `RangeQueries.boundaryTieAt` and `BoundaryTie` duplicate the walk and `Tie`

The correct structure has one walk that answers both a stored tie and a tie that a note could start or end. Concretely:

- The walk gets two queries that do not need the anchor to be tied: the tie a note would start, and the tie that would end on a note. Each gives a `@Nullable Tie`.
- `NoteElement.tie()` becomes "tied to next, then the first query". `NoteElement.incomingTie()` becomes "the second query, when its anchor is tied".
- `boundaryTieAt` becomes: at the line's first non-separator, the second query, when its anchor is in the previous line; at the line's last non-separator, the first query, when its end is in the next line. Then the rule from item 1.
- `BoundaryTie` dissolves. `MusicEditOperations.toggleBoundaryTie` and `tieCandidateWithPredecessor` read `anchor()` and `end()` from the `Tie`.

Which type the two queries go on is step 4's question. `RangeQueries` is row 20's file and `MusicEditOperations` is row 26's. <span id="rd-c2">Do this now</span>, or carry it to row 20 in the register?

### 3. What a `Tie` value means

Today a `Tie` means one of two things. It can be the tie the walk derives from a tied note. It can also be any pitched pair, through the public `between`. The class Javadoc states only the first. After items 1 and 2, nothing outside the walk calls `between`, so it can become private. Then every `Tie` is a pair that the walk joins. Making that unbreakable is step 3's work. The question here is which concept the type names:

- <span id="rd-c3">**a. A pair that the walk joins, tied or not**</span>**.** A candidate from item 2 is a `Tie` whose anchor is not yet tied. `NoteElement.tie()` gives only the stored ones. `BoundaryTie` dissolves.
- **b. A stored tie only.** Every `Tie` has a tied anchor. Then item 2's candidate needs a type of its own, which keeps two types with the same two fields and the same walk.

### 4. `Tie` keeps the value and the tie system's rules together — confirm

I found no second concept in `Tie.java` that needs a type of its own, other than `Reached` (item 5). The walk, the judgment, the beam conflict and the redraw reach are each a rule about ties, stated on the tie's type. `reach` matches `KeyInheritance.keyMoveReach`, which the key system owns and `ScoreViewController` asks in the same way. Where each static goes, for example `isLegalSeparator` on `ElementType`, is step 4's question. <span id="rd-c4">Confirm</span>, or name the split you want.

### 5. <span id="rd-c5">Dissolve `Reached` into the element</span>

`endAfter` returns the `StaffElement` it reaches, or `null`. `bothNotesIn` and `judge` get the line from `requireParentLine()` and the index from `getElementIndex`, which is a cached lookup. No real downside. If item 2 replaces `endAfter` with the queries there, `Reached` goes with it.

### 6. A kind check stands in for a type: the tie state on every `NoteElement` (U2)

The correct structure puts `tiedToNext` only on a type that can carry a tie. Then a grace note has no tie state at all, and the five kind checks in U2 become a type test at the load boundary or disappear. That type is in `NoteElement`'s design, which row 5 settled, and `StaffElement.convertedFrom` already makes a new instance when a note becomes a grace note.

- **a. Split** `NoteElement` into a principal-note type, which carries the tie state, and a grace-note type.
  
  - Con: it reopens row 5, which is ✅. Every reader of `NoteElement` that means only principal notes or only grace notes changes its type.
- **b.** <span id="rd-c6">**Keep the kind check**</span>**.**
  
  - Con: the check stays in the five places in U2, and `Line.addTie` keeps its throw.

Do this in this pass, carry it to the register, or keep the kind check?

### 7. What varies, and what is not abstracted — <span id="rd-c7">confirm</span>

- Nothing in the target varies by subtype. `Tie` is final. `Judgment` is a closed outcome with two arms.
- `TieRule` is one member of the closed `ModificationRule` set.
- `TieLoadPass` is a load stage called by name from `StampedLoad.place`. No load-pass interface exists, and `SpanLoadPass` has the same shape.
- No type joins `TieRule` and `TieLoadPass`. They share `Tie.judge`, which is the one judgment of a tie for an edit and for a load.

### 8. Which type owns each uncarried invariant

These are step 3's input. <span id="rd-c8">Confirm the owner of each</span>:

| Invariant | Owner |
|---|---|
| U1, a `Tie` is a walk result | `Tie`, after item 3 |
| U2, tie state only on a note that can carry it | the note type, if item 6 is a |
| U3, the reason a tie is dropped | `Tie.Judgment`, which then also covers the load's damaged-anchor reason |
| U4, `holds()` after every restamp | the order in which a bracket closes; a contract clause, step 5 |
| U5, every mutation kind has a reach answer | `Tie.reach`'s switch |
| U6, where the walk ends | the element, after item 5 |

---
roughdraft: "1.0"
comments:
  rd-c1:
    body: this
    by: user
    at: "2026-11-09T00:49:36.219Z"
  rd-c2:
    body: now
    by: user
    at: "2026-11-09T00:50:03.219Z"
  rd-c3:
    body: I don't understand what you mean by "the walk joins"
    by: user
    at: "2026-11-09T00:53:15.072Z"
  rd-c4:
    body: confirmed
    by: user
    at: "2026-11-09T00:53:55.846Z"
  rd-c5:
    body: ok
    by: user
    at: "2026-11-09T00:54:29.675Z"
  rd-c6:
    body: seems to me keeping the kind check is less messy than splitting the type.
    by: user
    at: "2026-11-09T00:56:52.298Z"
  rd-c7:
    body: confirmed
    by: user
    at: "2026-11-09T00:57:31.945Z"
  rd-c8:
    body: confirmed
    by: user
    at: "2026-11-09T00:58:50.898Z"
