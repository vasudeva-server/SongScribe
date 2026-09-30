# Pass 7, Spans — step 5: contracts

All paths are under `src/main/java/songscribe/` unless they start with `src/test`. Line numbers are from the tree step 4 left (HEAD `7bf30db7`, clean).

## What it found

### The counts

Counted per non-private method and per type, in the target's classes. A private member is outside the three counts: its comment is left alone. _Update_ covers a missing `@return`, a missing nullable condition, and a comment that describes the code or narrates a decision.

|     | write | update | remove |
| --- | --- | --- | --- |
| Target | 0   | 29  | 6   |

Nothing earns a new contract. Every method that fan-in rewards already has a comment.

| Class | write | update | remove | What the class promises |
|---|---|---|---|---|
| `Span` | 0 | 10 | 0 | A span reaches from an anchor to an end on one line; the kind decides what an edit does to it. Updates: `getAnchorElement`, `getEndElement`, the seven predicate factories (`:475`–`:535`), `createCopy` (`:563`). Each lacks `@return`. |
| `SpanOutcome` | 0 | 1 | 0 | Keep or Remove; only a hairpin can Reshape. `Keep`'s comment (`:37`–`:49`) defends the enum against a record. That is a rejected alternative. |
| `LineSpans` | 0 | 9 | 5 | The registry of one line; the only writer of its list; the sweep. Updates: `getSpans` (item 4), `sweep` (item 8), `findSpans` (both), `findFirstSpan`, `hasSpan`, `findBeamAt` and `findTupletAt` (each needs the condition for `null`), `isInsideHairpin`. Removes: `sameBeamAt`, `findHairpins`, `hasHairpin`, `hasTrillOverlapping`, `findEndings`. Each restates its name, has one caller or none, and has no `@return`. |
| `Beam` | 0 | 0 | 0 | Members are the non-grace notes from anchor to end. Every method with a comment has its tags. |
| `Hairpin` | 0 | 4 | 0 | Where a hairpin may start and end, and how a deletion pulls its ends in. Updates: `canAnchorAt`, `canEndAt`, `hasEnoughColumns`, `Kind.opposite`. Item 9. |
| `Ending` | 0 | 1 | 0 | A well-formed first/second ending; `spanning` is the one way to make one. Update: `getSplitIndex` (`:416`) says the index is "computed live … rather than from a value cached during layout". That is history. Item 3. |
| `EndingValidationResult` | 0 | 0 | 0 | The editor's verdict; the valid case carries the ending. |
| `Trill` | 0 | 1 | 0 | A trill is a span over one or more elements. Update: `intrinsicWidthSs` (`:90`) lacks `@return`. |
| `Tuplet` | 0 | 2 | 0 | Grade and ratio are fixed at construction; `outcomeFor` removes a tuplet whose interior changes. Updates: `isNumberOnly` (`:90`), `getGrade` (`:175`). Each lacks `@return`. |
| `TupletRatio` | 0 | 0 | 0 | M and V; M is positive. |
| `TupletValidator` | 0 | 1 | 1 | Whether a span can carry a tuplet, and which ratio. Update: `validateDerived` (`:267`). Remove: the class comment's ABC section (`:71`–`:82`). Item 7. |
| `TupletLoadPass` | 0 | 0 | 0 | The one place a read tuplet becomes a `Tuplet`. |
| `EndingLoadPass` | 0 | 0 | 0 | The one place a read ending becomes an `Ending`. Not in the target list; step 4 created it. |
| `EndingSectionRule` | 0 | 0 | 0 | Removes an ending whose split closes no open section. Not in the target list; step 4 created it. |

`SpanBound`, `SpanLookup`, `NoteSpan`, `Crescendo` and `Diminuendo` no longer exist. Steps 2 to 4 deleted them; `Hairpin.Kind` replaced the last two.

### Fan-in

The measures below decide the _remove_ count and items 1 and 2.

| Member | Callers outside its class |
|---|---|
| `Span.isIn` | None. The uses in `SelectionCoordinator`, `ScoreViewController` and `LineSpans` that step 1 listed are gone. Three Javadoc links remain (`LineElement.java:33`, `LineSpans.java:53`, `ui/selection/SelectionCoordinator.java:656`). |
| `Span.getEndLine` | None. `isIn` is its only reader. |
| `Span.getAnchorLine` | None. `isIn` and `Span.line()` read it. |
| `Beam.graceNotes` | None. `Beam.graceStemTouchesBeamAbove` is its only reader. |
| `Ending.isValidEnd` | `EndingBracketGeometryTest` only. Production reads it inside `Ending`. Carried to step 7. |
| `LineSpans.sameBeamAt`, `hasHairpin`, `removeOverlapping` | One file each (`RangeQueries`, `MusicEditOperations`, `TrillAction`). |
| `LineSpans.findEndings` | `EndingSectionRule` reads it, and `Ending.spanning` reads it inside `dom`. It is a one-line filter over `findSpans(Ending.class)`. I did not list its callers outside `dom`. |

### A universal claim that is false

`Ending`'s class comment says every attached ending meets every invariant: `spanning` is the one way to create an ending, and `outcomeFor` keeps the rest. A paste bypasses it.

`ScoreViewController.tryInsertFragment` (`ui/component/ScoreViewController.java:1465`) calls `span.addTo(line)` on every fragment span that `PasteSpanReconciliation.reconcile` keeps. `Fragment.cloneOnto` (`ui/clipboard/Fragment.java:207`) made each fragment ending through `Span.copy`, which is `public final` (`dom/Span.java:549`). The ending never reaches `Ending.spanning`. `reconcile` drops a fragment ending only when a destination ending straddles the paste (`PasteSpanReconciliation.java`, `case Ending ignored`). Two rules stay unjudged:

1. The rule for the element in front of a note or rest anchor. Copy an ending that anchors on a note at the start of its line, and paste it after a `||`. The pasted ending anchors on a note whose predecessor is a `||`. `hasOpeningPredecessor` refuses that anchor.
2. The rule that no bar follows the end bar. Paste an ending in front of a barline. Its end bar is followed by a bar.

`EndingSectionRule` judges the section rule at the close of the paste bracket, so that rule holds. `Ending.outcomeFor` judges only endings that were on the line before the paste. I read this from the code and did not run a paste.

### Statements that stay true

- `LineSpans.sweep` returns at once while the song replays (`:300`). The check is live (claimed finding 4). Item 8 gives the case.
- `LineSpans` class comment says every span query exists in "exactly one place". I did not falsify or confirm it. I check it when I write that sentence, and I write the weaker sentence if it fails.
- Signatures: the public and package-private methods hold no transposable adjacent parameters except `LineSpans.sameBeamAt(int, int)`, which is symmetric. Step 3 settled the rest. Four private helpers hold adjacent ints. Item 6.
- `docs/span-invalidation.md` names none of the deleted types or methods. `docs/hairpin-editing.md` I did not read.

## Decisions

Every correction below is accepted. The fix for each defect is made in this pass.

### Code changes

**1.** `Span.isIn` **and** `getEndLine` **have no reader.** Delete both. `getAnchorLine` becomes private, because `line()` is its only reader. The three Javadoc links that name `isIn` change: `LineSpans`'s class comment says "`Span#isIn` derives parentage from where its endpoints sit", and that sentence becomes "derived from where its endpoints sit". `SelectionCoordinator.isOnLine`'s comment says the same. The `Span.getAnchorLine` comment that argues for a null-safe form goes with the public form.

**2.** `Beam.graceNotes` **has no reader outside** `Beam`**.** Make it private. The class comment keeps its description of the query, without a link to a public member.

**3. A pasted ending is never judged by** `Ending.spanning`**.** This is the defect in _What it found_. A paste never cancels because of an ending. It removes what it invalidates and reports it in the change report, as it does for the other span kinds.

- `Fragment.cloneOnto` still clones the ending. After `insertRun` and before any span is added, the paste calls `Ending.spanning(line, range of the clones)` for each fragment ending that `reconcile` keeps. `spanning` reads the live line, so it judges the well-formed range, the overlap and the section rule.
- `Spanned` gives the ending that the paste adds, with the clone's `getUserYOffsetSs` copied to it. The clone is never added.
- `Refused` goes into the change report with the refusal's reason, as `tupletsRejectedByTarget` does.
- The ending rule and `dropTupletsRejectedByTarget` have one shape: keep or reject each fragment span against the line after the insertion, and report each rejected span. One method in `PasteSpanReconciliation` does it for both kinds.
- A destination ending that the paste breaks stays as it is today: the `insertRun` sweep removes it and reports it.

`Ending`'s class comment states the invariant, and says that every ending on a line was made by `spanning`. That sentence is true after the fix: the paste adds only what `spanning` returns, and undo of a `SpanRemoval` puts back an ending that `spanning` made. `Span.copy` still makes an `Ending` for the fragment, and that object is never added to a line.

**4.** `LineSpans.getSpans` **promises "the order they were added" (claimed finding 3).** The registry promises no order. The `getSpans` contract says a caller reads the spans in no defined order. `findFirstSpan` says it returns one match when several match. `SpanRemoval` and `MutationReplayer` do not change.

The MusicXML file does not depend on the registry order:

- The order of the writer's passes sets the order across kinds (`applyBeams`, `applyTuplets`, `applyTrills`, `applyHairpins` in `ScorePartwiseBuilder`, and `MeasureBuilder.attachEndings`).
- In one kind, an element carries at most one start and one stop. Beams, tuplets and endings cannot overlap, and a trill replaces the trills it overlaps. Two hairpins of opposite kinds share at most one element, where one ends and the other starts, and `applyHairpins` writes the stop before the start.
- Span numbers are constants (`SINGLE_SPAN_NUMBER`, `WEDGE_NUMBER`, the beam level), not a counter.

The hairpin rule in the second point lives only in the editor today (`MusicEditOperations.resolveHairpinAction`). `Hairpin.isSameKindAs` lets hairpins of opposite kinds overlap without limit. The rule becomes part of `Hairpin`'s class invariant. Item 9 states how it is enforced.

**5. A span's location is built by hand in two places.** `Span.location()` (`dom/Span.java:340`) and `TupletLoadPass.anchorToEnd` (`dom/TupletLoadPass.java:186`) each build `new ElementLocationRange(new ElementLocation(lineIndex, begin), new ElementLocation(lineIndex, end))` from a line index and an `ElementRange`. `EndingLoadPass` calls `TupletLoadPass.anchorToEnd`. Add a factory on `ElementLocationRange` that takes a line index and an `ElementRange`. Both sites call it. Delete `TupletLoadPass.anchorToEnd`.

**6. Four private helpers take adjacent ints a call can transpose.** The signature rule applies to private helpers too.

- `TupletValidator.addSpan(spans, int spanTicks, int noteValueTicks)`: `conventionalSpans` and `addSpan` take the `NotatableValue`, not its ticks. `validate` already holds one.
- `TupletValidator.isStructuralBoundary(type, int index, int endIndex)`: it becomes a method of `SpanMeasure`, which holds the span. The parameter list shrinks to the element.
- `Ending.firstBracketOf(type, int anchorIndex, int splitIndex)`: `firstBracketOf` and `secondBracketOf` take the `ElementRange` of the ending and the split index, and read the types from the element list. `elementsBetween(int before, int after)` keeps two ints, because a bracket bound of `-1` (a note anchor at index 0) is not an `ElementRange`.

**7.** `TupletValidator`**'s class comment holds a section for an importer that does not exist.** There is no ABC importer, and there will be none. Delete the section (`:71`–`:82`); the policy goes nowhere. Rewrite the reference to "constraint 4" (`:52`) so that it names the fermata rule.

**8. The replay check in** `LineSpans.sweep` **is live.** `sweep` has four callers: `Line.addElement`, `replace`, `modifyElement` and `removeRange`. `MutationReplayer` reaches each of them. Undo of an `ElementDeletion` puts the elements back one at a time through `addElement`, and each call sweeps. The line then passes through states that no edit made. Example:

1. The line is `[Y, ||, X, N …]`. An ending anchors on note `N`, whose predecessor is note `X`.
2. The user deletes `||` and `X`. The predecessor of `N` is now `Y`, a note, and the ending stays.
3. Undo puts `||` back first: `[Y, ||, N]`. `hasOpeningPredecessor` refuses a `||` in front of a note anchor, so a sweep would remove the ending in the middle of the undo.

The check stays. Its comment names two reasons: the recorded batch already carries the companion span mutations, and undo of a deletion of more than one element passes through intermediate lines that the rules would judge.

### Hairpins

**9. Where a hairpin may start and end.** This is `Hairpin`'s class invariant:

- A hairpin starts on a pitched note, or on a grace note whose host is a pitched note.
- It ends on a pitched note. A hairpin never ends on a rest.
- It holds at least two columns. A grace note and its host are one column.
- It shares at most one element with a hairpin of the opposite kind, where one ends and the other starts.

A deletion pulls a deleted end in to the nearest element that can hold it. An insertion or a replacement removes a hairpin whose ends stop being legal.

The changes:

- `canEndAt` accepts a pitched note only. The rest branch and its LilyPond comment go. `precedingDurationIsPitchedNote` goes if nothing else reads it. `resolveEndIndex` reads `canEndAt`, so a deletion never pulls an end onto a rest.
- A factory on `Hairpin`, the counterpart of `Ending.spanning`, judges the four rules and returns the hairpin or the reason for the refusal. `Ending.Spanning` becomes a top-level `dom` type over the span kind, so both factories return it.
- The editor, the paste and a new `HairpinLoadPass` make every hairpin through that factory. `resolveHairpinAction` asks the factory and keeps no copy of the rules. Its worked example "one note plus the rest after it: `CAN_ADD`" becomes `INELIGIBLE`, and its exception for "a rest anchor an older build left behind" goes.
- `HairpinLoadPass` does for a read hairpin what `EndingLoadPass` does for a read ending. The MusicXML reader judges no hairpin today, so a file that an older build saved can hold a hairpin that ends on a rest. The load pass removes each hairpin the factory refuses and reports it in the change report.

After these changes, every hairpin on a line meets the invariant.

### Carried forward

**To step 7:** `Ending.isValidEnd` is `public` and read in production only inside `Ending`. `EndingBracketGeometryTest` is the only outside reader.

**To step 6:** `EndingTest` has nine `Ending.spanning` calls. Item 3 sends a pasted ending through `spanning`, so the paste path needs a case. The hairpin factory and `HairpinLoadPass` (item 9) need tests, and so does the one shared rejection method in `PasteSpanReconciliation` (item 3). The list is proposed before any test is written.
