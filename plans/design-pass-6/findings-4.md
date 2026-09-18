# Findings — step 4: extraction and placement

Target: `dom/Line.java`, `dom/StaffElementRun.java` (with `ElementRange` and `EffectiveRange`), `dom/EditableRun.java`, `dom/LineSpans.java`, `dom/ElementChange.java`, `dom/ProjectedElements.java`. Line numbers are against `bb61745b` with a clean tree. Paths are relative to `src/main/java/songscribe/`.

The target holds no `*Utils` or `*Helper` bag. What this step found is one rule spelled at several sites — inside the target, and at callers that spell out a query the run already answers or should. Each site below is a fact; each numbered item under _What needs deciding_ is one move.

The work reaches past the target. The files it touches outside it, and the register row that owns each:

- `dom/LyricRun.java` — row 9 (4.1)
- `dom/KeyChangeElement.java` — key changes, settled by pass 0 (4.4)
- `dom/Beam.java`, `dom/Tuplet.java`, `dom/Trill.java`, `dom/Span.java` — row 7 (4.5, 4.7)
- `ui/selection/RangeQueries.java` — row 20 (4.5)
- `ui/MusicEditOperations.java`, `ui/action/TrillAction.java`, `ui/component/score/PreviewElementInserter.java`, `ui/component/score/PreviewElementManager.java` — their own rows (4.2, 4.3, 4.5, 4.7, 4.10)
- `io/musicxml/NoteBuilder.java`, `layout/AccidentalFitGate.java`, `layout/KeyEditFitCalculator.java`, `ui/dialog/KeyChangeDialogController.java` — their own rows (4.4, 4.9)

## What it found

### 4.1 The glissando strip is written three times

The rule: a glissando on a note connects it to the element immediately after it. It cannot survive that element leaving, or something non-pitched landing in front of it. The strip — _the element at_ `i - 1` _is a_ `NoteElement` _with_ `hasGlissando()`_, so_ `modifyElement(i - 1, SLIDE, note::removeSlide)` — is spelled at:

- `EditableRun.deleteRange` `:153–156`
- `EditableRun.deleteElement` `:226–229`
- `LyricRun.repairNeighborsAfterInsertion` `:348–351`

The query half alone, without the mutation, is spelled a fourth time at `PreviewElementManager.isBreathMarkInsertionBlocked` `:817`.

`modifyElement` is declared on `LyricRun`, so anything that records through it sits on `LyricRun` or below.

The two deletion sites do not agree. `deleteElement` skips the strip when a paired grace note precedes the deleted note (`!hasPrecedingPairedGraceNote`, `:226`): the grace note at `index - 1` is itself removed, and the note at `index - 2` is not examined. `deleteRange`, handed a range whose `effectiveBegin` reached back over that same grace note, examines `rangeBegin - 1`, which is that `index - 2`. So a glissando from the note before a paired grace note is stripped by one path and kept by the other.

### 4.2 "Is the element at `index` the auto-maintained terminal" is computed four times

Each site does a bounds test and then `song.isAutoMaintainedTerminal(line.getElement(i), line)`:

- `Line.addElement(StaffElement)` `:404–406`
- `Line.effectiveElementCount` `:638–639`
- `PreviewElementInserter.isDirectClickOnTerminal` `:242–243`
- `MusicEditOperations.canMakeFirstSecondEnding` `:826–827`

The first two ask it of the last index. `addElement(StaffElement)` `:403–409` then computes the insertion index — `lastIdx` when the terminal is there, `elements.size()` when it is not — which is the value `effectiveElementCount()` returns.

The terminal policy itself — which element counts as the terminal — is `Song`'s (row 12) and stays there.

### 4.3 "Is there a breath mark at `index`" is computed three times

Each site spells `i < effectiveElementCount() && getElement(i).getType().isBreathMark()`:

- `StaffElementRun.effectiveEnd` `:235`
- `EditableRun.deleteElement` `:269–270`
- `PreviewElementManager.isBreathMarkInsertionBlocked` `:818`

The sibling predicates do not bound alike: `isKeyChangeAt` `:305` bounds with `hasIndex` (`elementCount()`), `isKeyChangeBehindBarline` `:159` with `effectiveElementCount()`. The terminal is a `FINAL_DOUBLE_BARLINE` or a `REPEAT_RIGHT` (`Line.effectiveElementCount` Javadoc `:630–635`), so neither a breath mark nor a key change stands at it, and the two bounds give the same answer everywhere.

### 4.4 "Is the element at `index` a barline or repeat" is a static on `KeyChangeElement`

- `StaffElementRun.isKeyChangeBehindBarline` `:167–169` reads `getElement(index - 1).getType().isBar()`.
- `KeyChangeElement.needsBarlineBefore(Line line, int insertionIndex)` `:111–112` reads `!line.getElement(insertionIndex - 1).getType().isBar()`. Callers: `KeyEditFitCalculator` `:323`, `KeyChangeDialogController` `:380`.

`needsBarlineBefore` is a function of the run at an index — the same shape as `isKeyChangeBehindBarline`, which lives on the run and cites `KeyChangeElement`'s position invariant the same way. Its Javadoc says the two callers must agree, which a named method on the run gives them just as a static does.

### 4.5 Nearest non-grace neighbours are scanned outside the run

`StaffElementRun.previousNonGraceIndex` `:467–475` and `nextNonGraceIndex` `:486–494` answer the nearest non-grace index below and above a position, `null` when there is none.

- `Beam.noteTypeInLevel` `:303–316` finds the same two neighbours with two `while` loops (`:307–313`) and then tests `begin >= 0` and `end < line.elementCount()`. The loop below stops at index 0 without testing it; a grace note at 0 then recurses into `noteTypeInLevel(0, level)`, whose own scan yields `begin = -1` and returns `false` — the same answer `null` gives. Since both neighbours are non-grace, the recursion on each reduces to `type.beamCount() > level`.
- `RangeQueries.nonGraceBegin` `:189–203` and `nonGraceEnd` `:205–221` scan a selection's range for its first and last non-grace index, `null` when the range holds only grace notes. Every reader takes the pair together and tests `beginIndex == null || endIndex == null || beginIndex.equals(endIndex)` before building an `ElementRange` of the two: `RangeQueries.canToggleBeaming` `:228–229`, `canToggleTuplet` `:497–498`, `MusicEditOperations.toggleBeaming` `:114–115`, `toggleTuplet` `:426–427`. The concept every reader wants is _the range narrowed to its non-grace endpoints_.

### 4.6 `isInsideGraceHostPair` spells its sibling inline

`StaffElementRun.isInsideGraceHostPair` `:273` reads `isPairedGraceNote(index) || isPairedGraceNote(index - 1)`. The second disjunct is `isHostOfPairedGraceNote(index)` `:141` by definition (`index >= 1 && isPairedGraceNote(index - 1)`; `isPairedGraceNote(-1)` is `false` through `hasIndex`).

### 4.7 `LineSpans` offers one removal method per span kind

`removeOverlappingTuplets` `:545` and `removeTrillsOverlapping` `:557` are public one-line wrappers over the private `removeOverlapping(Class<? extends Span> type, ElementRange range)` `:565`. Callers: `Tuplet.applyDisplacementPolicy` `:415`, `Trill.applyDisplacementPolicy` `:73`, `TrillAction.performAction` `:138`. Each caller already knows its kind, and `mergeOverlapping` `:487` — the other displacement primitive the kinds call — already takes the kind as `type`. The two names disagree in word order.

The trill wrapper's Javadoc (`:552–553`) states that a trill whose anchor is detached is still found. That is a property of `Span.overlapping` (`Span.java:513–516`) and holds for every kind the generic serves.

### 4.8 The four `ElementChange` shapes disagree on `projectedIndexOf`

- `Insertion.projectedIndexOf` `:122–126` and `Deletion.projectedIndexOf` `:236–240`: `if (!line.contains(element)) return null; var current = line.getElementIndex(element);`.
- `Replacement.projectedIndexOf` `:168` and `Modification.projectedIndexOf` `:269` call `line.getElementIndex(element)` directly, which throws `IllegalArgumentException` for an element on another line.

The contract (`:66–72`) promises `null` when the element "sits on another line". Two shapes keep it and two break it. The only reader is `Hairpin.projectedEndpointIndexOf` (`Hairpin.java:226`, `:229`), which passes the hairpin's own endpoints; only ties are ever cross-line (`LineSpans.boundOf` Javadoc `:186–187`). No caller can supply an off-line element today.

### 4.9 Call sites that reimplement a `Line` accessor

- `NoteBuilder` `:119` and `:358`: `line.getElements().get(i)` — the list view indexed by hand where `getElement(i)` exists.
- `AccidentalFitGate.otherLinesFit` `:171`: `line.getElements().subList(0, line.effectiveElementCount())` — the line's content elements with the terminal excluded, which is the concept `effectiveElementCount`'s Javadoc (`:630–635`) defines. It is the only caller of `InsertionSpacingCalculator.fitsInPlace` that passes the line as it stands; the other three (`InsertionSpacingCalculator:593`, `PreviewElementInserter:222`, `SelectionActionApplier:346`) pass a projection.

### 4.10 The breath-mark placement rule lives in the preview

`PreviewElementManager.isBreathMarkInsertionBlocked` `:802–819` states where a breath mark may land: never at index 0; only after a duration element; not where the preceding note's glissando would be broken (4.1's query); not in front of another breath mark (4.3's query). `overExistingElement` is the preview's own state and stays with it.

`canInsertElementAt` `:378–380` refuses the two slots inside a pair and says a per-operation slot rule "is each operation's own rule, and it is asked separately". This rule is asked by the preview only: `insertRun` (a paste) and the MusicXML reader do not ask it.

### Housekeeping

`Line.java` carries a run of blank lines after `effectiveElementCount` and another after `getElementIndex`, left where members were moved out.

## What needs deciding

Each item is separately decidable.

1. <span id="rd-c1">**Extract the glissando strip and its query**</span>**.** `StaffElementRun` gains `default boolean hasGlissandoInto(int index)`: `true` when `index >= 1` and the element at `index - 1` is a note carrying a glissando, which connects it to the element at `index`; `false` out of range. `LyricRun` gains `default void removeGlissandoInto(int index)`: when `hasGlissandoInto(index)`, records `modifyElement(index - 1, ElementField.SLIDE, noteAt(index - 1)::removeSlide)`. The three strip sites in 4.1 call `removeGlissandoInto`; `PreviewElementManager:817` calls `hasGlissandoInto`. `LyricRun` is row 9's file — execute now, or carry the `LyricRun` half forward tagged to row 9 and leave that third copy until then?

**The two deletion paths disagree** (4.1, last paragraph). <span id="rd-c2">Is a glissando from the note before a paired grace note into that grace note a state the model can hold</span>? If it cannot, the `!hasPrecedingPairedGraceNote` test in `deleteElement` is dead and `removeGlissandoInto(firstDeletedIndex)` is the whole statement. If it can, the two paths must pick one answer, and which is a domain promise: does deleting a grace-host pair strip the glissando that reached the grace note? 2. `Line.isTerminalAt(int index)`**.** `hasIndex(index) && song.isAutoMaintainedTerminal(getElement(index), this)`, documented as _whether the element at_ `index` _is the song's auto-maintained terminal; out of range yields_ `false`. Then `effectiveElementCount()` is `isTerminalAt(elementCount() - 1) ? elementCount() - 1 : elementCount()`, and `addElement(StaffElement)`'s body is `addElement(effectiveElementCount(), element)` with its Javadoc kept. `PreviewElementInserter.isDirectClickOnTerminal`'s body becomes `line.isTerminalAt(xIndex)`, and `MusicEditOperations:826–827` becomes `line.isTerminalAt(extendedEnd)`. <span id="rd-c3"><span id="rd-c18">The name: `isTerminalAt`, or `isAutoMaintainedTerminalAt` to match `Song`'s</span></span>? 3. `StaffElementRun.isBreathMarkAt(int index)`, a sibling of `isKeyChangeAt` bounded by `hasIndex`. `effectiveEnd:235`, `deleteElement:269–270` and `PreviewElementManager:818` read it. `isKeyChangeBehindBarline`'s own bound (`index >= effectiveElementCount()`) goes with item 4, so the three `*At` predicates bound alike; 4.3 states why the answers do not change. 4. `StaffElementRun.isBarAt(int index)`, bounded by `hasIndex`, and `needsBarlineBefore` moves onto the run. `isKeyChangeBehindBarline` becomes `isKeyChangeAt(index) && isBarAt(index - 1)`. `KeyChangeElement.needsBarlineBefore(Line, int)` becomes `StaffElementRun.needsBarlineBeforeKeyChangeAt(int insertionIndex)`, body `!isBarAt(insertionIndex - 1)`, Javadoc moved with it; `KeyEditFitCalculator:323` and `KeyChangeDialogController:380` call it on the line. `KeyChangeElement` is pass 0's area: <span id="rd-c5">execute the move now</span>, or add `isBarAt` alone and leave `needsBarlineBefore` where it is, written as `!line.isBarAt(insertionIndex - 1)`? 5. `Beam.noteTypeInLevel` **asks the run.** The two loops and the two bounds tests become:

```java
var previous = line.previousNonGraceIndex(noteIndex);
var next = line.nextNonGraceIndex(noteIndex);

return previous != null && noteTypeInLevel(previous, level)
    && next != null && noteTypeInLevel(next, level);
```

4.5 states the equivalence. `Beam` is row 7's file: <span id="rd-c6">execute now</span>, or carry forward tagged to row 7? 6. `StaffElementRun.nonGraceRange(ElementRange range)` returning `@Nullable ElementRange`: `range` narrowed to its first and last non-grace elements, `null` when it holds only grace notes. `RangeQueries.nonGraceBegin` and `nonGraceEnd` are deleted; each of the four readers in 4.5 becomes `var grouped = line.nonGraceRange(range.range()); if (grouped == null || grouped.size() < 2) …`. `RangeQueries` is row 20's file: <span id="rd-c7">execute now,</span> or carry forward? 7. `isInsideGraceHostPair`**'s body** becomes `isPairedGraceNote(index) || isHostOfPairedGraceNote(index)`. 8. `LineSpans.removeOverlapping(Class<? extends Span> type, ElementRange range)` **becomes public**; `removeOverlappingTuplets` and `removeTrillsOverlapping` are deleted. `Tuplet:415` passes `Tuplet.class`, `Trill:73` and `TrillAction:138` pass `Trill.class`. The `{@link}` at `Span.java:515` names the generic. Its contract, which step 5 writes, carries the detached-anchor sentence for every kind. 9. `projectedIndexOf`**: one promise for all four shapes.** Two structures keep the sibling sites in step; which one turns on what the contract promises.

- **The contract stands** — `null` for an element on another line. Every shape guards, and the pair `contains` then `getElementIndex` is written once as a private static interface method on `ElementChange`, `indexOnLine(Line line, StaffElement element)`, returning `@Nullable Integer`. `Insertion`, `Replacement`, `Deletion` and `Modification` all read it. Facts: the promise is honoured by every shape; no reader needs it today.
- <span id="rd-c9">**The contract narrows** to elements of the line</span>. The two guards in `Insertion` and `Deletion` go, and the clause "or it sits on another line" leaves the Javadoc. Facts: a future cross-line reader throws from `getElementIndex` at the lookup, which is the form step 3 chose for every other site that assumes presence; the one reader, `Hairpin`, is never cross-line.

10. <span id="rd-c10">`Line.effectiveElements()` — `getElements()` less the auto-maintained terminal</span>, as an unmodifiable live view, the list form of `effectiveElementCount()`. `AccidentalFitGate:171` reads it. One caller; the concept is already named by `effectiveElementCount`'s Javadoc. Add it, or leave the `subList` where it is?
11. `NoteBuilder:119` **and** `:358` read `line.getElement(i)`.
12. **Where does the breath-mark placement rule live?** If it is the run's: `StaffElementRun.canInsertBreathMarkAt(int index)` — `index >= 1 && getElement(index - 1).getType().isDuration() && !hasGlissandoInto(index) && !isBreathMarkAt(index)` — beside `canInsertElementAt`, and `isBreathMarkInsertionBlocked` becomes `overExistingElement || !line.canInsertBreathMarkAt(xIndex)`. If it is the preview's own rule, 4.10 stays where it is and reads items 1 and 3's queries only. The fact that decides it: <span id="rd-c11"><span id="rd-c19">is a paste or a load that places a breath mark after a barline, or after a glissando, a state the model must refuse</span></span>, or one the preview alone avoids?
13. **Housekeeping.** Close the two blank-line runs in `Line.java`.

---
roughdraft: "1.0"
comments:
  rd-c1:
    body: do it all now
    by: user
    at: "2026-09-16T20:52:01.524Z"
  rd-c2:
    body: no
    by: user
    at: "2026-09-16T21:28:08.197Z"
  rd-c3:
    body: Rename isAutoMaintainedTerminalAt -> isTerminalAt
    by: user
    at: "2026-09-16T21:32:05.148Z"
  rd-c5:
    body: do it now
    by: user
    at: "2026-09-16T21:33:48.141Z"
  rd-c6:
    body: now
    by: user
    at: "2026-09-16T21:34:05.047Z"
  rd-c7:
    body: now
    by: user
    at: "2026-09-16T21:34:17.167Z"
  rd-c9:
    body: this
    by: user
    at: "2026-09-16T21:37:56.028Z"
  rd-c10:
    body: yes
    by: user
    at: "2026-09-16T22:35:23.750Z"
  rd-c11:
    body: Yes. For example currently I can paste a breath mark after a barline, which should be rejected.
    by: user
    at: "2026-09-16T22:38:29.757Z"
  rd-c12:
    body: Any item in "What needs deciding" that I did not comment on is accepted as is.
    by: user
    at: "2026-09-16T22:38:55.344Z"
    scope: document
  rd-c13:
    body: Then the `!hasPrecedingPairedGraceNote` test in `deleteElement` is dead. Item 1's execution deletes it, and both deletion paths become one statement, `removeGlissandoInto(firstDeletedIndex)`.
    by: AI
    at: "2026-09-16T22:39:42.000Z"
    re: rd-c2
  rd-c14:
    body: "No `isAutoMaintainedTerminalAt` exists today. I read this as: the new `Line` method is named `isTerminalAt`. Does the rename also cover `Song.isAutoMaintainedTerminal(StaffElement, Line)` → `Song.isTerminal(StaffElement, Line)` and `Song.isAutoMaintainedTerminalOfItsSong` → `Song.isTerminalOfItsSong`? Both are `Song`'s (row 12). I will leave them unless you say so."
    by: AI
    at: "2026-09-16T22:39:43.000Z"
    re: rd-c3
  rd-c15:
    body: |
      Then the rule is the run's: `StaffElementRun.canInsertBreathMarkAt(int index)` as item 12 states, and the preview reads it. Where the paste refuses is a second decision, because the paste asks nothing today: `PasteModeManager.acceptsInsertionIndex` accepts every index, and paste-replace (`ScoreViewController.handlePaste`, which calls `tryInsertFragment(line, effective.range().begin(), effective)`) never passes through `InsertionPointMode`. Two sites, one question each:

      - Paste mode: `PasteModeManager.acceptsInsertionIndex` reads the clipboard fragment and answers `false` when its first element is a breath mark and `!line.canInsertBreathMarkAt(index)`. The marker hides, as it does for `KeyChangeAction`'s refusals. Is that the refusal you want?
      - Paste-replace: the same test against the deleted range's begin, in `handlePaste` before the bracket opens. It has no marker to hide, so it needs an outcome. Refuse with an error dialog (a new string, under the strings guide), or drop the fragment's leading breath mark and paste the rest?

      One more fact. For a paste, "after a glissando" is repaired today rather than refused: `repairNeighborsAfterInsertion` strips the predecessor's glissando when the first inserted element is not pitched. The preview refuses the same placement. Should the paste refuse it too, through the same `canInsertBreathMarkAt` test, or keep the repair?
    by: AI
    at: "2026-09-16T22:39:44.000Z"
    re: rd-c11
  rd-c16:
    body: |
      You answered in chat: rename them now. Done. `Song.isAutoMaintainedTerminal` is now `Song.isTerminal`, and `Song.isAutoMaintainedTerminalOfItsSong` is now `Song.isTerminalOfItsSong`. The `{@code}` reference in `BarlineMenu:38` names the new method. The tree compiles.

      One sibling keeps the old name: `TerminalMaintainer.isAutoMaintainedTerminal(StaffElement, Line)` (`:187`), which `Song.isTerminal` delegates to. Its own callers are `TerminalMaintainer:237` and `:256`, and its class Javadoc (`:36`) links it. Rename it to `isTerminal` too?
    by: AI
    at: "2026-09-17T01:05:12.000Z"
    re: rd-c14
  rd-c17:
    body: |
      You answered in chat. Both paste paths show a confirm dialog, titled "Paste", with the message "The clipboard begins with a breath mark, which cannot be placed here. Remove it and paste, or cancel?" and the buttons "Cancel" and "Remove". "After a glissando" keeps the repair.

      What follows from those answers, stated so you can check it:

      - **One site, not two.** Both paths call `tryInsertFragment`, and it already holds the ending confirm before any mutation. The breath-mark test goes there, on the instantiated clones, before the fit gate. "Remove" drops the leading clone. The fit check, the chain walk and the span reconciliation then see the run that actually lands.
      - **The test reads only the predecessor.** A paste-replace deletes from `begin`, so the element at `begin - 1` is the same before and after the deletion. The test is `insertIndex >= 1` and the element at `insertIndex - 1` is a duration.
      - **Item 12's rule loses its glissando clause.** The paste keeps the repair, so the model does not refuse that placement. `canInsertBreathMarkAt(index)` becomes `index >= 1 && getElement(index - 1).getType().isDuration() && !isBreathMarkAt(index)`. The glissando refusal stays the preview's own: `isBreathMarkInsertionBlocked` becomes `overExistingElement || !line.canInsertBreathMarkAt(xIndex) || line.hasGlissandoInto(xIndex)`.

      Questions:

      1. **Paste mode.** I read your answer as: `acceptsInsertionIndex` keeps accepting every index, so the marker shows at a refused slot too, and the dialog comes on the click. Is that right? Then what does "Cancel" do to the mode? `CANCELLED` completes it today, as the ending confirm does. The other choice is `DECLINED`, which leaves the mode live for a click on another slot.
      2. **A fragment that is only a breath mark.** "Remove" leaves nothing to paste. Choices:
         - The same dialog. "Remove" pastes nothing through the existing empty-clones path. A paste-replace still deletes the selection, and paste mode completes.
         - A different dialog, an error with one button and a new string. Nothing changes.
      3. **The other end of the fragment.** A breath mark is also broken when something that is not a duration lands in *front* of it. Example: paste mode puts a fragment that ends with a barline between a note and its breath mark. The breath mark then follows a barline, which is the state you want refused. A paste-replace never does this: `effectiveEnd` takes a breath mark after the range into the deletion, so the successor is never a breath mark. Note entry does it too: nothing refuses a barline, a key change or a grace note in front of a breath mark (`canInsertElementAt` does not, and `PreviewElementManager` tests the successor only for a breath mark). I did not run the app to confirm that the click geometry offers that slot. The structure that covers every case is one run method, `StaffElementRun.canInsertRunAt(int index, List<? extends StaffElement> run)`: `canInsertElementAt(index)`, the leading test above when the run's first element is a breath mark, and, when `isBreathMarkAt(index)`, the run's last element is a duration. A lone breath mark in front of a breath mark fails the last test, so `canInsertBreathMarkAt` folds into it. The preview asks it with a one-element run. Is that the rule you want? If it is, what does paste mode do at such a slot? Removing the trailing element does not help in general, so the choice is: the marker hides there (`acceptsInsertionIndex` refuses), or an error dialog on the click.
      4. **Files already saved.** A paste can put a breath mark after a barline today, so a saved `.musicxml` can carry that state. The reader does not test it. Should the reader drop such a breath mark on load, or leave it?
    by: AI
    at: "2026-09-17T01:05:13.000Z"
    re: rd-c15
  rd-c18:
    body: Yes, rename that too
    by: user
    at: "2026-09-17T01:06:30.562Z"
    re: rd-c3
  rd-c19:
    body: |-
      1. Change the "Remove" button to "Remove & paste". Cancel cancels paste mode.
      2. If the fragment is only a breath mark, show an error alert titled "Paste" which says "A breath mark cannot be placed here." Don't cancel paste mode.
      3. This is currently allowed. If attempted, show an error alert titled "Paste" which says "These elements may not be inserted before a breath mark." Don't cancel paste mode.
      4. The reader should drop an invalid breath mark on load and warn.
    by: user
    at: "2026-09-17T01:14:43.796Z"
    re: rd-c11
  rd-c20:
    body: |
      Done. `TerminalMaintainer.isAutoMaintainedTerminal` is now `isTerminal`. No `isAutoMaintained` name is left under `src/`.
    by: AI
    at: "2026-09-17T01:16:52.000Z"
    re: rd-c18
  rd-c21:
    body: |
      Answers 1 to 3 as I will execute them:

      - Both dialogs are titled "Paste". The confirm's buttons are "Cancel" and "Remove & paste".
      - The test runs in `tryInsertFragment`, before the fit gate, in this order. The fragment's first element is a breath mark and the slot refuses it: a lone breath mark gets the error "A breath mark cannot be placed here."; a longer fragment gets the confirm. Then, when the slot is in front of a breath mark and the fragment's last element is not a duration, the error "These elements may not be inserted before a breath mark." A lone breath mark in front of a breath mark fails the first test, so it gets the first error only.
      - In paste mode, "Cancel" gives `CANCELLED`, which ends the mode. Both errors give a new outcome that `insertionPointChosen` maps to `DECLINED`, which leaves the mode live, as `LINE_FULL` does. A paste-replace never meets the second test: `effectiveEnd` takes a breath mark after the range into the deletion.
      - The rule is one run method, `StaffElementRun.canInsertRunAt(int index, List<? extends StaffElement> run)`, as rd-c17 item 3 states. `tryInsertFragment` asks its two halves separately, because each half has its own dialog. The halves are two run methods: `canLeadRunAt(int index)`, which is the predecessor test, and `canTrailRunAt(int index, ElementType last)`, which is the successor test.

      Answer 4 rests on a premise of mine that is wrong. A saved `.musicxml` cannot hold a breath mark that does not follow a note or a rest. The writer folds each breath mark into the `<note>` element that the element before it produced (a rest is a `<note>` too) (`NoteBuilder.foldBreathMark`, `:337–367`). When no note is there, the writer drops the breath mark silently at save time. The reader then appends each breath mark directly after the note that carries it (`MeasureMapper.walkNote`, `:617–618`). So the MusicXML load never meets the state. Only the legacy `.mssw` reader (`StaffElementIO`) reads a flat element list that can hold it.

      Questions:

      1. **Note entry in front of a breath mark.** A barline, a key change or a grace note clicked into the slot between a note and its breath mark. Does note entry show the same error alert, or does the ghost preview hide there, as it does for every other refused breath-mark slot? The alert text says "These elements", which reads as the paste's.
      2. **Note entry that replaces the note before a breath mark.** `canReplaceElementAt` does not refuse it, and `PreviewElementInserter.modifyExistingElement` (`:439–527`) does not repair it. A click with a barline over that note leaves the breath mark after a barline. Choices:
         - Refuse: `canReplaceElementAt` refuses a non-duration replacement of an element that a breath mark follows. The ghost preview hides there.
         - Cascade: the replacement takes the breath mark with it, as `deleteElement` does for a deletion (`:262–272`).
      3. **Where the load drop runs.** Choices:
         - A post-parse pass beside `TupletLoadPass`, run by `LoadMigrations.run` for both readers. It reports through `LoadMigrations` into the `MigrationWindow`, which `ScoreView.openFile` already shows and which already marks the song modified. The MusicXML path never finds anything.
         - The legacy reader alone. It reports a new `LoadWarning.Type`, and `ScoreView.openFile` shows a warning dialog with a new string, as it does for `INVALID_LYRICS_DATE` (`:474–483`).
      4. **The writer's silent drop.** Once every edit path refuses the state and the load removes it, `foldBreathMark`'s two early returns (`:354–356`, `:360–362`) are unreachable. They then describe an invariant failure, not a case to handle. The null-handling guide sets the form of that failure. I will read it at execution, unless you want the drop kept.

      One more finding in the same method: `foldBreathMark`'s Javadoc narrates history ("The streaming writer expressed that as a peek… Both halves are gone", "which is what the streaming writer's peek did"). That breaks the no-history rule for comments. I will rewrite it in present tense together with item 11, which already touches `NoteBuilder`.
    by: AI
    at: "2026-09-17T01:16:53.000Z"
    re: rd-c19
  rd-c22:
    body: |
      You answered in chat:

      1. Note entry in front of a breath mark: the ghost preview hides at that slot. No alert.
      2. Note entry that replaces the note before a breath mark with a non-duration: cascade. The replacement takes the breath mark with it, as `deleteElement` does.
      3. The load drop runs as a post-parse pass beside `TupletLoadPass`, called by `LoadMigrations.run` for both readers and reported in the `MigrationWindow`.
      4. `foldBreathMark`'s two early returns become invariant failures, in the form the null-handling guide sets.
    by: user
    at: "2026-09-17T06:09:11.000Z"
    re: rd-c21
