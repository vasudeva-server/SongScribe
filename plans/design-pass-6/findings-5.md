# Design Pass 6 — Step 5: Contracts

Counts are in methods. **Write**: no Javadoc, and fan-in earns one. **Update**: a Javadoc exists, but not in the form `.claude/guides/contracts.md` states. **Remove**: a Javadoc exists that fan-in does not earn, or that describes the code instead of a promise.

|     | Write | Update | Remove |
| --- | --- | --- | --- |
| **Total** | 2   | 35  | 1   |

Fan-in is from step 1's table (`findings-1-2.md` §_Fan-in per public member_), re-read for every member that steps 2–4 added, moved or renamed.

## What it found

### Per class

| Class | Write | Update | Remove | What the class promises that makes the counts come out this way |
|---|---|---|---|---|
| `Line` | 2 | 8 | 0 | The key queries, the removal primitives and `getElements(ElementRange)` already carry full contracts. The gaps are the high-fan-in accessors that promise a live view or a throw and do not say so, and two docs that narrate or describe code. |
| `StaffElementRun` | 0 | 17 | 1 | The pairing and key queries carry full contracts. Most updates are one-line queries with no `@return` — the tag the IDE shows at the call site. Two are rule violations in prose, one is a domain question, and one is the carried `getElement` item. |
| `EditableRun` | 0 | 2 | 0 | The interface promises the repair sequence around an edit. `deleteRange` promises a repair that no code performs, and it does not say what happens to a breath mark past the range. |
| `LineSpans` | 0 | 2 | 0 | The registry's package-private members carry accurate contracts. Only the two public members that callers outside the class use lack tags. |
| `ElementChange` | 0 | 6 | 0 | Step 3 made `Replacement.oldElement()` read the line lazily. The throw moved with it, but the contract stayed on the factory. |
| `ProjectedElements` | 0 | 0 | 0 | Package-private. Every method is a `List` override, so nothing earns a method contract. Its class Javadoc makes two false claims (below). |

### `Line`

- **Write** `getElements()` (`Line.java:525`). Fan-in 11 files. Callers iterate the result, and it is the live `elementsView`. A caller that iterates it across an edit gets `ConcurrentModificationException`. `effectiveElements` (`:531`) says "like `getElements()` a live unmodifiable view", so it cites a promise that `getElements()` does not state.
- **Write** `setLyricsYPosSs` (`:717`). Fan-in 3 (`FormatMigrator`, `MutationReplayer`, `LineIO`). It posts a `LineLayoutChange` through the bracket. Every other `Line` mutator states the bracket rule. If this one stays silent, a reader can think that the setter records nothing.
- **Update** `spanRegistry()` (`:146`, 88 refs): no `@return`.
- **Update** `isFirst()`, `isLast()` (`:167`, `:172`): no `@return`. Both call `index()`, which throws `IllegalStateException` for a line that is not in its song. Neither states that throw.
- **Update** `addElement(StaffElement)` (`:390–402`), fan-in about 22 files. The Javadoc narrates history: "It looked as though it did not while every span ended somewhere inside its own line … (#493)". The promise is that the element goes in front of the terminal and passes through the insertion primitive, so the insertion's span sweep and its terminal check apply. It does not state what it throws.
- **Update** `addElement(int, StaffElement)` (`:407–421`). Its own Javadoc replaces the interface Javadoc, so it loses the `@throws IndexOutOfBoundsException` that `EditableRun.addElement` states.
- **Update** `modifyElement(int, ElementField, Runnable)` (`:479–485`), fan-in 12. The Javadoc describes the implementation ("Clones the element before running `mutator` … centralizing the clone-before-mutate contract that would otherwise have to be repeated at every emission site"). The promise is what the record carries: the element's state before and after `mutator`, under the bracket rule.
- **Update** `effectiveElements()` (`:529`): no `@return`.
- **Update** `effectiveElementCount()` (`:633–642`, fan-in 26 files): no `@return`.
- **Class Javadoc** (`:50–55`) names four primitives: insertion, replacement, single removal and range removal. `modifyElement(int, EnumSet, Runnable)` also sweeps and posts. `ElementChange`'s class Javadoc names four shapes that include the modification. The two lists disagree. The fix is five primitives on `Line`, one sweep shape for each kind of change.

### `StaffElementRun`

- **Update** `getElement` (`StaffElementRun.java:83`). This is the claimed carry-forward item: _"The element at_ `index`_."_ It has no range, no `@return` and no `@throws`. Both implementors (`Line:521`, `DetachedLyricRun`) are `List.get`, so the contract is `0 <= index < elementCount()`, and `IndexOutOfBoundsException` outside that range. The contract holds for both implementors.
- **Update** — no `@return` on a documented non-void method: `elementCount` (`:86`), `effectiveElementCount` (`:89`), `hasIndex` (`:96`), `isPairedGraceNote` (`:127`), `isHostOfPairedGraceNote` (`:139`), `isBreathMarkAt` (`:298`), `isBarAt` (`:305`), `isDurationAt` (`:312`), `canLeadRunAt` (`:505`), `canTrailRunAt` (`:513`), `isEmpty` (`:525`), `isSamePitchAsFollower` (`:553`), `EffectiveRange.range()` (`:920`).
- **Update** `isSamePitchAsFollower` also lists its callers ("The slide tool asks it …, a pitch shift asks it …, and the layout and render passes ask it"). This list becomes wrong when a caller changes. The reason it exists — one definition of the condition — stays.
- **Update** `spansStructuralBoundary` (`:635–640`): "No bounds check is performed; callers own their indices" describes the code. The promise is a precondition: every index in `range` satisfies `hasIndex`. It has no `@param` and no `@return`.
- **Update** `effectiveBegin` (`:198–200`): "A separate index-0 guard would be dead code" explains why an absent thing is absent, which the comment rules forbid. The sentence before it — no deletion can leave a key change at index 0 — is a real invariant and becomes an `@invariant`.
- **Update** `isInsideGraceHostPair` (`:251–264`). The Javadoc speaks UI ("clicking on it", "clicking between it and its host"), and it has no `@return`. What it promises is a domain question — item 5.
- **Remove** `isKeyChangeBarlineAt` (`:393–404`). It is a private one-line helper with one caller, and it has a full `@param`/`@return` contract. It keeps a one-line summary.
- **Class Javadoc** (`:52–58`): "These rules were `Line`'s, and a line is still the run they mostly operate on" narrates history. The paragraph keeps its present-tense reason: a clipboard fragment holds the same pairs and needs the same answer.

### `EditableRun`

- **Update** `deleteRange` (`EditableRun.java:111–131`). The summary promises a repair of "the x-offsets of the elements that close the gap". No statement in `deleteRange`, `deleteRangeElementwise` or `deleteElement` touches an x-offset, and `LyricRun` has no member that does (checked by symbol overview). The clause is false. Also, the contract does not say what happens to a breath mark past the range — item 4.
- **Update** `deleteElement` (private, `:201–209`, and the comment at `:254–260`). Both name "gap-fill", which no code performs.

### `LineSpans`

- **Update** `getSpans()` (`LineSpans.java:106–112`). Its own Javadoc replaces `SpanLookup.getSpans`'s ("in the order they were added"), so it loses the order promise. It also has no `@return`, and it does not say that the view is live. `Song.removeSpansBetweenNonAdjacentLines` (`Song.java:1058`) copies the list before it removes spans, because the view is live.
- **Update** `removeOverlapping` (`:538–548`). Callers are `Tuplet`, `Trill` and `TrillAction`. It has no `@param`, and it has no `@effects` link to `Span#removeFrom`, which states the recording and bracket rule. Step 4 moved the detached-anchor sentence here, as `record.md` asked.

### `ElementChange`

- **Update** `forReplacement` (`ElementChange.java:295–307`). `@throws IndexOutOfBoundsException if no element stands at index` is false. The factory only calls the record constructor, which reads nothing. Since step 3, the throw comes from `Replacement.oldElement()` (`:145`), and from `endingEffect()` (`:181`) through it. Both UI callers (`SelectionActionApplier:237`, `PreviewElementInserter:456`) call `endingEffect()` on the result at once.
- **Update** `Replacement.oldElement()` and `endingEffect()`: they gain the throw. `oldElement()` also has no `@return`.
- **Update** — no `@return`: `line()` (`:56`), `projectedElements()` (`:64`), `Deletion.deletedElements()` (`:210`).

### `ProjectedElements`

The class Javadoc has two false claims. "O(log d) for a deletion of d elements" (`ProjectedElements.java:30`) is false, because `Deletion.projectedElements` maps a position with one comparison, which is O(1). "what all three share" (`:39`) is false, because four shapes supply a mapping since step 3 added `Modification`. Its paragraph on liveness (`:32–36`) also repeats `ElementChange`'s class Javadoc (`ElementChange.java:46–51`). The fix is a link.

### Items from the record, re-read against the tree

`plans/check-findings.md` items 8–19:

| #   | State in the tree |
| --- | --- |
| 8   | Done. `EditableRun`'s edits carry no bracket clause. `redundantKeyChangeRanges` links `EditableRun#deleteRanges`. |
| 9   | Done. `Song.applyChange`'s `@throws` states the two conditions. `Line.applyChange` and `setKey` link to it. |
| 10, 11, 12, 16, 17 | Done. |
| 13  | Partly done. `removeOverlapping` replaces both methods, and the stale caller list is gone. The `@param` and the bracket link are still absent (above). |
| 14  | Wrong in the tree now. The throw is on `forReplacement`, which cannot throw it (above). |
| 15  | Done. `Song`'s three delegations link `TerminalMaintainer` for the condition. |
| 18  | Open. `getElement` (above). |
| 19  | One stale link is left: `PasteSpanReconciliation.java:113–116` links `Line#addCrescendo`, `Line#addDiminuendo` and `Line#addPastedSpan`. None of the three exists. The merge is now `Hairpin`'s displacement policy, run by `Span.addTo`. The other stale prose in #19 is gone. |

The #19 link is a defect. It is in row 20's file (`ui/clipboard`), and it points a reader at three methods that do not exist. The fix is to name what does the work now: `Span#addTo`, and the policy `Hairpin#applyDisplacementPolicy` runs.

### For step 7, recorded, not argued here

- `lastKeyChangeKey` (`StaffElementRun.java:777`) has no production caller. Its only caller is `MusicXmlStrandedKeyChangeTest`.
- `strandedKeyChangeIndices` (`:671`) has one production caller, `redundantKeyChangeRanges`, in the same interface. `StrandedKeyChangeTest` calls it directly.

### `package-info.java` and `docs/`

No invariant spans the target classes that a class Javadoc or `docs/span-invalidation.md` does not already state. Nothing goes in `package-info.java`. No `docs/` file changes.

## What needs deciding

**1. The carried** `getElement` **item has two owners.** It is in `record.md` _Findings claimed_, and `check-findings.md` #18 put it back in `plans/design-pass-register.md` (_Carry-forward findings_, line 19 of that section). This step writes the contract (above). I propose to delete the register line in the same change, so that no list owns an item that is closed. <span id="rd-c1">Confirm</span>.

**2. Narrow four members that nothing outside their class uses.** A public member with no outside caller tells every reader that outside callers exist. The correct structure is the narrowest visibility that the callers need. Each is separate:

- a. `StaffElementRun.isBarAt` (`:308`): its only callers are `isKeyChangeBehindBarline` and `needsBarlineBeforeKeyChangeAt` in the same interface. <span id="rd-c2">It becomes a `private` interface method</span>.
- b. `StaffElementRun.isKeyChangeBehindBarline` (`:158`): its only callers are `beginIncludingKeyChangeBarline`, `effectiveEnd` and `isKeyChangeBarlineAt` in the same interface. <span id="rd-c3">It becomes `private`</span>. Its full contract then becomes a private helper's contract. I propose to keep the paragraph on why the barline is tested and not assumed, because that is the reason the pairing is safe, and to cut the tags.
- c. `Line.LYRICS_DEFAULT_Y_SS` (`Line.java:130`): the only reader is `Line`'s own field initializer. <span id="rd-c4">It becomes `private`</span>.
- d. `LineSpans.rangeOf` (`LineSpans.java:137`): the callers are `requireRangeOf` and `mergeOverlapping` in the class, `Tuplet.outcomeFor` in `dom`, and now `HairpinEndpoints.compute` in `layout`. It stays public.
  
  `check-findings.md` #4 expected four callers outside `dom` (`RangeQueries`, `HairpinEndpoints`, `LineInvariants`, `ElementColumn`) to move to `rangeOf`. Only `HairpinEndpoints` did. It only asked whether both bounds were `SpanBound.At`, the same test `rangeOf` already performs, so it now calls `rangeOf` and reads `range.begin()`/`range.end()` instead of pattern-matching `anchorIndexOf`/`endIndexOf` by hand.
  
  The other three read the bound itself, not just the range it resolves to, so `rangeOf` would delete information they need:
  
  - `ElementColumn.java:739, 745` tests `endIndexOf(tie) == SpanBound.AFTER_LINE` and `anchorIndexOf(tie) == SpanBound.BEFORE_LINE` — the far endpoint of a cross-line tie, a case `rangeOf` collapses to the same `null` it returns for a detached span.
  - `LineInvariants.java:150–151` stores the anchor and end bounds as two separate `SpanBound` fields, for the same cross-line reason during playback rendering.
  - `RangeQueries.java:481` hands the raw bounds to `Span.exactly(grouped).test(...)`, a predicate written to compare `SpanBound` values, not resolved indices.
  
  `rangeOf`'s own contract says as much: "a caller that has to tell an endpoint in a neighboring line from an absent one asks the two bound queries instead." These three are that caller. <span id="rd-c5">No further move is proposed for them</span>.

If a or b is accepted, its update in the counts above becomes a trim to a one-line summary.

**3.** `ElementChange`**'s four factories alias the canonical constructors.** `forInsertion(Line, int, StaffElement)`, `forReplacement`, `forDeletion` and `forModification` (`ElementChange.java:275–332`) each return `new X(...)` with the same arguments. The nested records are public, so their constructors are public too. Each shape thus has two public construction routes with two contracts. Item 14 shows the cost: the throw stayed on the factory when the record changed. <span id="rd-c6">The correct structure is one route: delete the four aliases, and callers use the constructors</span> (`Line`'s five sweeps, `ScoreViewController` ×2, `SelectionActionApplier`, `PreviewElementInserter`). `forInsertion(Line, int, ElementType)` does more than alias, so it stays, as a static factory on `Insertion` next to the constructor it wraps. If you decline, the four factories keep their contracts, and I correct `forReplacement`'s contract as above.

**4. The breath-mark cascade in** `deleteElement` **appears unreachable, and** `deleteRange`**'s contract must state one answer.** `deleteElement` (`EditableRun.java:261–263`) deletes a breath mark that lands at the deletion's left edge. Only `deleteRangeElementwise` calls `deleteElement`, and it walks a range that `effectiveEnd` already widened over a trailing breath mark. The cascade can therefore run only if a breath mark stands directly after another breath mark. Three paths keep that from happening:

- the load pass drops a breath mark that does not follow a duration element (`BreathMarkLoadPass.java:124`);
- insertion refuses a breath mark in front of a breath mark (`canLeadRunAt`, `canTrailRunAt`);
- replacement refuses a non-duration element in front of a breath mark (`PreviewElementInserter.java:512`).

I did not find a fourth path, but I did not trace every replacement route (the selection-wide replace in `SelectionActionApplier` replaces durations with durations only, as far as I read). The proposal: delete the cascade, and `deleteRange` states that a deletion takes the breath mark directly after the range, through `effectiveRange`, and nothing past it. Two questions:

- a. <span id="rd-c7">Is "a breath mark stands only directly after a duration element" a promise the model keeps on every path?</span> If yes, the cascade is dead and goes. If some path can put two breath marks side by side, that path is the defect, and I need to know which one you have in mind.
- b. If the cascade stays, the contract must say that the elementwise path also takes a breath mark past the range, and the contiguous path does not. That makes the result depend on whether a paired grace note stands at the head of the range. I cannot see a musical reason for that, which is why I propose the deletion.

**5.** `isInsideGraceHostPair` **— domain checkpoint.** Its one caller, `GraceModeManager.mousePressed` (`GraceModeManager.java:382`), refuses to start grace-note entry at the hovered insertion index when this returns true.

`StaffElementRun.isInsideGraceHostPair(int)`

<span id="rd-c8">Proposing: a new grace note may not be entered at insertion index i when the element at i is a paired grace note, or when i falls between a paired grace note and its host.</span> A grace note entered at i decorates the element at i. In the first case, it would decorate a grace note that already has a host. In the second case, it would split a pair.

Today: the code agrees. It returns isPairedGraceNote(i) || isHostOfPairedGraceNote(i). The Javadoc says only "would conflict with a paired grace note", and it explains the two cases as clicks. This proposal comes from reading the body and its one caller, not from the domain.

Also defensible: slot i in front of a paired grace note is not inside the pair. canInsertElementAt allows an ordinary element there. If a grace note in front of a paired grace note is legal notation (two grace notes before one host), then only the second case is a refusal, and the first case is a bug in the grace-mode rule.

The name follows the answer. If the proposal is right, "inside the pair" is wrong for the first case. A name that states the grace-entry question, such as `canEnterGraceNoteAt` with the sense inverted, fits the promise. If the alternative is right, the method becomes `isHostOfPairedGraceNote`, which already exists, and this method goes. Accept, correct or reject.

**6.** <span id="rd-c9">`PasteSpanReconciliation`**'s stale links are a defect, and I fix them in this step**</span>**.** The fix is above. The file is row 20's. This is not a question about whether to fix. I state it here because the change crosses the target's files.

---
roughdraft: "1.0"
comments:
  rd-c1:
    body: confirmed
    by: user
    at: "2026-09-18T18:17:30.022Z"
  rd-c2:
    body: agreed
    by: user
    at: "2026-09-18T18:19:57.614Z"
  rd-c3:
    body: agreed
    by: user
    at: "2026-09-18T18:20:58.622Z"
  rd-c4:
    body: agreed
    by: user
    at: "2026-09-18T18:21:06.662Z"
  rd-c5:
    body: correct
    by: user
    at: "2026-09-18T18:40:09.772Z"
  rd-c6:
    body: agreed
    by: user
    at: "2026-09-18T19:40:21.638Z"
  rd-c7:
    body: don't know, you'll have to investigate
    by: user
    at: "2026-09-18T19:42:07.946Z"
  rd-c8:
    body: |-
      Currently the code *is* allowing a grace note to be inserted before an existing grace-host pair, but is failing to go into grace mode and allow a host to be inserted. This should be allowed.

      The code already prevents a grace note from being between an existing grace note and its host.
    by: user
    at: "2026-09-18T19:51:13.539Z"
  rd-c9:
    body: ok
    by: user
    at: "2026-09-18T19:51:31.602Z"
