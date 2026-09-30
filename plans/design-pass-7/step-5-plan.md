# Pass 7, Spans — step 5: contracts

## Status Dashboard

| Phase | Description | Status | Sub-plan |
|-------|-------------|--------|----------|
| 1 | [Spanning and the hairpin invariant](#-phase-1-spanning-and-the-hairpin-invariant) | ✅ Complete | — |
| 2 | [The paste judges every fragment span](#-phase-2-the-paste-judges-every-fragment-span) | ✅ Complete | — |
| 3 | [The hairpin menu asks the factory](#-phase-3-the-hairpin-menu-asks-the-factory) | ✅ Complete | — |
| 4 | [The hairpin load pass](#-phase-4-the-hairpin-load-pass) | ✅ Complete | — |
| 5 | [Registry and ending contracts](#-phase-5-registry-and-ending-contracts) | ✅ Complete | — |
| 6 | [Mechanical sweep in dom](#-phase-6-mechanical-sweep-in-dom) | ✅ Complete | — |
| 7 | [Reader wiring and the ending editor](#-phase-7-reader-wiring-and-the-ending-editor) | ✅ Complete | — |
| 8 | [Design docs](#-phase-8-design-docs) | ✅ Complete | — |
| 9 | [Gate](#-phase-9-gate) | ✅ Complete | — |
| 10 | [Manual UI verification](#-phase-10-manual-ui-verification) | ✅ Complete | — |
| 11 | [Legacy ending offsets](#-phase-11-legacy-ending-offsets) | ✅ Complete | — |

## Rules for every phase

- A path that starts with `dom/`, `io/` or `ui/` is under `src/main/java/songscribe/`.
- Read `.claude/rules/serena.md` and follow it for all Java exploration and refactoring.
- Line numbers in this plan are from commit `7bf30db7`. Earlier phases move lines. Find each
  symbol by name with serena, not by line number.
- Do not compile and do not run tests. Phase 9 is the only phase that does.
- Do not edit a file under `src/test/` unless a task names that file.
- Read `.claude/guides/contracts.md` before you write or change a Javadoc contract.
- Read `.claude/guides/strings.md` before you add a key to
  `src/main/resources/songscribe/strings.properties`.
- A comment describes the code as it is. Do not write history, rejected alternatives or change
  narration into a comment.
- Do not commit.

## ✅ Phase 1: Spanning and the hairpin invariant

**Status:** Complete  <br>
**BlockedBy:** —  <br>
**Files:** src/main/java/songscribe/dom/Spanning.java, src/main/java/songscribe/dom/Hairpin.java, src/main/java/songscribe/dom/Ending.java, src/main/java/songscribe/dom/LineSpans.java, src/main/java/songscribe/dom/StaffElementRun.java, src/main/resources/songscribe/strings.properties  <br>
**Recommended model/effort:** Opus, medium — a new generic result type, a factory that owns the hairpin invariant, and the contracts that state it

### Tasks

1. Read `docs/hairpin-editing.md`, `docs/span-invalidation.md` and `docs/change-report.md`.
   Read `Ending.spanning` and `Ending.Spanning` in `dom/Ending.java`. Read
   `MusicEditOperations.resolveHairpinAction` in `ui/MusicEditOperations.java`. Its
   rules are the rules this phase moves into `Hairpin`. Do not edit `MusicEditOperations`;
   phase 3 does.

2. Write the contract of a new top-level type `songscribe.dom.Spanning<S extends Span<?>>`,
   then create it in `dom/Spanning.java`:
   - A sealed interface that permits two records.
   - `record Spanned<S extends Span<?>>(S span)`: the range holds the span. The span is built
     and not added to its line.
   - `record Refused<S extends Span<?>>(String reasonKey, Cause cause)`: the range holds no
     span. `reasonKey` is the `Strings` key of the reason, as the user reads it.
   - `enum Cause { RANGE, OTHER_SPAN }`. `RANGE`: the requested range cannot hold the span,
     whatever else is on the line. `OTHER_SPAN`: a span already on the line is the obstacle.
   - State in the type's Javadoc why a refusal names its cause: the hairpin menu shows
     "blocked" for `OTHER_SPAN` and "ineligible" for `RANGE`.

3. Delete the nested `Ending.Spanning`. `Ending.spanning` returns `Spanning<Ending>`.
   An ill-formed range is refused with `Cause.RANGE`. An overlap with an ending already on the
   line is refused with `Cause.OTHER_SPAN`. A split that closes no open section is refused
   with `Cause.RANGE`. Update the `@return` of `Ending.spanning` to name the cause of each
   refusal. Do not edit the callers outside `Ending`: phases 4, 6 and 7 update
   `EndingLoadPass`, `SpanFactory` and `MusicEditOperations.canMakeFirstSecondEnding`.

4. Write `Hairpin`'s class invariant as `@invariant` tags in its class Javadoc, in the form
   that `Ending`'s class Javadoc uses. The five rules:
   1. A hairpin starts on a pitched note, or on a grace note whose host is a pitched note.
   2. It ends on a pitched note. It never ends on a rest.
   3. It holds at least `MIN_COLUMNS` columns. A grace note and its host are one column. A
      rest is not a column.
   4. Its range holds no repeat and no bar other than `ElementType.SINGLE_BARLINE`.
   5. It shares at most one element with a hairpin of the opposite kind, where one ends and
      the other starts.

   Under the tags, state in prose what keeps each rule on every path that puts a hairpin on a
   line:
   - `Hairpin.spanning` (task 7) judges every new hairpin. The editor
     (`MusicEditOperations.addHairpinToSelection`), the paste
     (`ScoreViewController.tryInsertFragment`) and the load (`HairpinLoadPass`) each add
     only a hairpin that `spanning` returned. Phases 2, 3 and 4 make that true.
   - `outcomeFor` judges every element change (task 9).
   - Undo of a span removal puts back a hairpin that `spanning` made.

   Before you write that list, run `jet_brains_find_referencing_symbols` on `Span/addTo` and
   on the `Hairpin` constructor. Every production path that adds a `Hairpin` must be in the
   list. After phases 2, 3, 4 and 7, the only production constructor call is inside
   `Hairpin`. If you find a path the list does not name, add it and report it.

5. Make the `Hairpin` constructor `private`, as `Ending`'s is. Then `spanning` and
   `createCopy` are the only ways to make a hairpin. Five test files call the constructor
   (`UnitTest`, `LineSpansTest`, `LineTest`, `SpanTest`, `MusicXmlOffsetRoundTripTest`).
   Do not edit them; phase 9 reports them.

6. Move the convergence loop out of `resolveHairpinAction` (its "Step 1") into
   `Hairpin`. Write the contract first:
   - `public static Convergence converge(Line line, Kind kind, ElementRange span)`.
   - `public record Convergence(ElementRange range, List<Hairpin> absorbed)`, nested in
     `Hairpin`.
   - `range` widens `span` over every hairpin of `kind` within
     `LineSpans.SPAN_ADJACENCY_REACH` of it, and repeats until the range stops growing.
     `absorbed` is every hairpin the widening took in. It is empty when nothing is in reach.
   - The contract states that `range` is the range a hairpin over `span` holds after
     `Span.addTo` applies `Displacement.ABSORB_ADJACENT`.

   Read `LineSpans`'s absorb merge (the method that the Javadoc at `LineSpans.java:400`
   describes). Make sure that `converge` and the merge use the same reach and the same
   same-kind test. If the merge computes the same widening, make both call one method, so
   that the two cannot drift.

7. Write the contract of `public static Spanning<Hairpin> spanning(Line line, Kind kind,
   ElementRange span)`, then implement it. It is the counterpart of `Ending.spanning`.
   - It judges the range `converge(line, kind, span).range()`, not `span`. Reason: absorption
     can cover an opposite-kind hairpin. Example: a crescendo on `[0,3]`, a diminuendo on
     `[3,4]`, a new crescendo on `[4,6]`. The new one absorbs `[0,3]`, becomes `[0,6]` and
     covers the whole diminuendo.
   - It refuses, with these causes:
     - anchor breaks rule 1: `Cause.RANGE`;
     - end breaks rule 2: `Cause.RANGE`;
     - fewer than `MIN_COLUMNS` columns: `Cause.RANGE`;
     - a structural boundary inside `span`: `Cause.RANGE`;
     - a structural boundary only in the part that convergence added: `Cause.OTHER_SPAN`,
       because an absorbed hairpin brought the range to the boundary;
     - more than one shared element with an opposite-kind hairpin: `Cause.OTHER_SPAN`. Test
       this with `Span.overlappingBeyondEndpoint`, as `resolveHairpinAction` does.
   - An absorbed hairpin's endpoints already meet rules 1 and 2, so an endpoint failure can
     come only from `span`. State this in the contract.
   - `Spanned` carries a new hairpin of `kind` over `span`, not over the converged range, with
     zero shifts. `Span.addTo` widens it to the converged range. This keeps the merge's
     behavior for the shifts of absorbed hairpins.
   - The hairpin is built, not added.
   - One implementation of each rule exists. `spanning`, `outcomeFor` and the deletion
     reshape read the same predicates.

8. `canEndAt` accepts a pitched note only. Delete the rest branch and its LilyPond comment.
   Delete `precedingDurationIsPitchedNote` when nothing else reads it (check with
   `jet_brains_find_referencing_symbols`). `countColumns` stops counting a rest at the end.
   Remove the rest sentence from the `MIN_COLUMNS` comment. `resolveEndIndex` reads
   `canEndAt`, so a deletion never pulls an end onto a rest. Its comment says that a deleted
   end moves in to the last surviving pitched note. In `resolveBeginIndex`, replace the
   sentence about "a hairpin an older build left anchored on a rest" with a present-tense
   statement: a surviving anchor stays where it is.

9. Rule 4 joins `outcomeFor`. In `outcomeForRepointing`, an insertion or a replacement
   removes a hairpin whose projected range holds a repeat or a non-single bar. The test today
   is `StaffElementRun.spansStructuralBoundary(ElementRange)`, a default method over the run.
   `outcomeForRepointing` reads `change.projectedElements()`, a list. Give the boundary test
   one implementation that both a run and a list of elements can reach. Do not copy the loop.
   A deletion cannot put a bar inside a range, so the deletion path gets no boundary check.
   State that in `outcomeForDeletion`'s comment.

10. Add the refusal and removal reasons to `strings.properties`, in sorted position in the
    `dialog.report.reason.*` group:
    - an endpoint that cannot hold the end of a hairpin, worded for a load and a paste as well
      as an edit (the existing `dialog.report.reason.hairpin.endpoint.invalid` says "can no
      longer", which fits only an edit; keep it for `outcomeFor`);
    - too few columns;
    - more than one shared element with an opposite-kind hairpin;
    - a repeat or non-single bar inside the hairpin (used by `spanning` and by the removal in
      task 9).

    Each `Refused` from `Hairpin.spanning` carries one of these keys.

11. Rewrite the contracts of `canAnchorAt`, `canEndAt` and `hasEnoughColumns`. Each gets
    `@return`. Each names its real readers after this phase: `spanning`, `outcomeFor` and the
    deletion reshape. The menu no longer reads them directly, so do not say that it does.

## ✅ Phase 2: The paste judges every fragment span

**Status:** Complete  <br>
**BlockedBy:** 1  <br>
**Files:** src/main/java/songscribe/ui/clipboard/PasteSpanReconciliation.java, src/main/java/songscribe/ui/component/ScoreViewController.java, src/main/java/songscribe/dom/Span.java, src/main/java/songscribe/dom/LineElement.java, src/main/java/songscribe/dom/Hairpin.java, src/main/java/songscribe/dom/Ending.java, src/main/java/songscribe/dom/Beam.java, src/main/java/songscribe/dom/Trill.java, src/main/java/songscribe/dom/Tuplet.java  <br>
**Recommended model/effort:** Opus, medium — one rejection rule for three span kinds, with ordering constraints inside the paste

### Tasks

1. Read `docs/clipboard.md`, `docs/change-report.md`, `PasteSpanReconciliation` and
   `ScoreViewController.tryInsertFragment`. Read `Fragment.cloneOnto`
   (`ui/clipboard/Fragment.java`) and `Span.copy` (`dom/Span.java`). Read the new
   `dom/Spanning.java`, `Ending.spanning` and `Hairpin.spanning(Line, Hairpin.Kind,
   ElementRange)` that phase 1 wrote.

2. The defect: `tryInsertFragment` calls `span.addTo(line)` on every fragment span that
   `reconcile` keeps. A fragment ending and a fragment hairpin are clones made by `Span.copy`,
   so they never pass through `Ending.spanning` or `Hairpin.spanning`. Two cases break the
   ending invariant today:
   - Copy an ending that anchors on a note at the start of its line. Paste it after a `||`.
     The pasted ending anchors on a note whose predecessor is a `||`.
   - Paste an ending in front of a barline. Its end bar is followed by a bar.

   After this phase, the paste adds only spans that the kind's own rule accepted. A paste
   never cancels because of a span. It leaves out what the destination refuses and reports it.

3. Replace `dropTupletsRejectedByTarget` with one method,
   `settleAgainstTarget(Line line)`, that judges every fragment span against the line after
   the insertion. Write its contract first. Rules:
   - Dispatch with an exhaustive `switch` over the sealed `Span` hierarchy, with no
     `default`, so that a new span kind fails to compile here.
   - A `Tuplet` is judged by `TupletValidator` in `STRICT` mode, as `isRejectedByTarget`
     does now. A kept tuplet is the clone. A rejected one has the reason
     `Strings.DIALOG_REPORT_REASON_PASTE_TUPLET_REJECTED`.
   - An `Ending` is judged by `Ending.spanning(line, range of the clone)`. A kept ending is
     the `Spanned` ending, which takes the clone's user state through `copyUserStateFrom`
     (task 8). The clone is never added.
   - A `Hairpin` is judged by `Hairpin.spanning(line, clone's kind, range of the clone)`. A
     kept hairpin is the `Spanned` hairpin, which takes the clone's user state through
     `copyUserStateFrom` (task 8). The clone is never added.
   - A `Beam` and a `Trill` are kept as they are.
   - A refused ending or hairpin has the reason key of its `Refused`.
   - Judge every fragment span before any fragment span is added.

4. Replace the record component `tupletsRejectedByTarget` with
   `List<RejectedSpan> fragmentSpansRejected`, and add
   `record RejectedSpan(Span<?> span, String reasonKey)` nested in
   `PasteSpanReconciliation`. `reconcile` returns an empty list for it.
   `settleAgainstTarget` fills it and replaces `fragmentSpans` with the kept spans.

5. In `tryInsertFragment`, call `settleAgainstTarget(line)` where
   `dropTupletsRejectedByTarget(line)` is called now: after `line.insertRun`, before any span
   is added. Report every `fragmentSpansRejected` entry with `span.reportedAs(reasonKey)`.
   Delete the tuplet-only report loop. Add every span in `fragmentSpans`, as now.

6. A destination ending that the paste breaks stays as it is: the `insertRun` sweep removes
   it and reports it. Do not change that path.

7. Rewrite the class Javadoc and the method comments of `PasteSpanReconciliation`, and the
   comments in `tryInsertFragment` that describe the drop rules. They must describe the
   straddle rule and the target rule for all three judged kinds. Delete the sentence
   "nothing else in the codebase rejects a nested ending". `Ending.spanning` refuses an
   overlap.

8. Give the user state of a span one copy route. The user state is the base state that
   `LineElement.copyBaseStateFrom` copies (both user offsets) plus a span kind's own user
   state (a hairpin's `x1ShiftSs` and `x2ShiftSs`). Write the contracts first.
   - Add `public final void copyUserStateFrom(Span<E> source)` to `Span`. It calls
     `copyBaseStateFrom(source)`, then `copyOwnStateFrom(source)`. A `source` of another
     concrete class is an `IllegalArgumentException`.
   - Add `protected abstract void copyOwnStateFrom(Span<E> source)` to `Span`. Each span kind
     implements it with the state that its `createCopy` copies now.
   - `createCopy` constructs the span over the new endpoints and copies no state.
     `Span.copy` calls `createCopy`, then `copyUserStateFrom(this)`.
   - Add `copyUserStateFrom` to the routes that `copyBaseStateFrom`'s Javadoc names.
   - `settleAgainstTarget` calls `copyUserStateFrom` on each `Spanned` ending and hairpin,
     and calls no setter of a user offset or a shift.

   `LineSpans.applySpanRun` is the other caller of `Span.copy`. It reshapes a run of hairpins
   after a deletion, and phase 5 task 5 gives it rule 5. Do not edit `LineSpans`.

## ✅ Phase 3: The hairpin menu asks the factory

**Status:** Complete  <br>
**BlockedBy:** 1  <br>
**Files:** src/main/java/songscribe/ui/MusicEditOperations.java, src/main/java/songscribe/ui/action/HairpinAction.java  <br>
**Recommended model/effort:** Opus, medium — rewrites a multi-state resolution onto the new factory and rewrites its contract

### Tasks

1. Read `docs/hairpin-editing.md`. Read the `Hairpin` class that phase 1 wrote: `Hairpin.converge`,
   `Hairpin.Convergence`, `Hairpin.spanning` and `dom/Spanning.java`.

2. Rewrite `MusicEditOperations.resolveHairpinAction(Hairpin.Kind kind)` so that it keeps
   no copy of a hairpin rule:
   1. No selection range → `INELIGIBLE`, as now.
   2. `var convergence = Hairpin.converge(line, kind, selection)`.
   3. An absorbed hairpin whose range covers the whole selection → `BLOCKED`. There is
      nothing to extend.
   4. `switch (Hairpin.spanning(line, kind, selection))`:
      - `Spanned` → `EXTEND` when `convergence.absorbed()` is not empty, else `CAN_ADD`. The
        resolution's span is `convergence.range()`.
      - `Refused` → switch on its `cause()`, exhaustively and with no `default`:
        `RANGE` → `INELIGIBLE`, `OTHER_SPAN` → `BLOCKED`.

   The calls to `Hairpin.canAnchorAt`, `Hairpin.canEndAt`, `Hairpin.hasEnoughColumns`,
   `line.spansStructuralBoundary`, `LineSpans.findHairpins` and `LineSpans.hasHairpin` go
   from this method.

3. `addHairpinToSelection` adds the hairpin that `Hairpin.spanning` returns for the
   selection. It constructs no `Hairpin` (the constructor is private after phase 1). Keep
   `stripInteriorPointDynamics(line, added.range())` after `addTo`. `addTo` widens the added
   hairpin to the converged range, so `added.range()` is the range to strip.

4. Rewrite the Javadoc of `resolveHairpinAction`:
   - The steps above, with `Hairpin.spanning` as the one judge.
   - The worked example "One note plus the rest after it … `CAN_ADD`" becomes `INELIGIBLE`:
     a hairpin never ends on a rest.
   - Delete the exception for "a rest anchor an older build left behind", in the Javadoc and
     in the method comments.
   - Keep the "Do not cache this" paragraph.

5. Rewrite the constructor comment of `HairpinAction` (`ui/action/HairpinAction.java`). It
   says a "note, rest" selection is one the action must support, that a trailing rest is a
   valid endpoint, and that `resolveHairpinAction()` "is now" the authority. After this plan:
   a selection may hold rests inside it; a rest at either end makes the selection
   `INELIGIBLE`; `resolveHairpinAction` answers through `Hairpin.spanning`. Keep the reason
   that `DISABLE_IN_REST_MODE` is absent, if it is still true: a selection with an interior
   rest must stay enabled.

6. Do not edit `canMakeFirstSecondEnding`. Phase 7 updates it.

## ✅ Phase 4: The hairpin load pass

**Status:** Complete  <br>
**BlockedBy:** 1  <br>
**Files:** src/main/java/songscribe/dom/PendingHairpin.java, src/main/java/songscribe/dom/HairpinLoadPass.java, src/main/java/songscribe/dom/EndingLoadPass.java, src/main/java/songscribe/io/LoadPasses.java, src/main/java/songscribe/io/FormatMigrator.java, src/main/java/songscribe/io/MigrationContext.java, src/main/java/songscribe/io/MigrationPipeline.java  <br>
**Recommended model/effort:** Opus, medium — a new load pass, a shared shape with the ending pass, and the contracts of both

### Tasks

1. Read `docs/change-report.md` and `docs/musicxml-object-model.md`. Read
   `dom/EndingLoadPass.java`, `dom/PendingEnding.java`, `io/LoadPasses.java`,
   `FormatMigrator.migratePixelsToStaffSpace`, `io/MigrationContext.java` and
   `io/MigrationPipeline.java`. Read the wedge handling in `io/musicxml/MeasureMapper.java`
   (`buildHairpin`, `closeHairpin`, `applyHairpinShifts`, and the code that carries an
   `OpenHairpin`), `LineIO.createCrescendosFromPending` and
   `LineIO.createDiminuendosFromPending`, and
   `src/test/java/songscribe/io/musicxml/MusicXmlCrossLineSpanTest.java`. Do not edit the
   readers. Phase 7 does.

2. The defect: the MusicXML reader and the legacy reader build each `Hairpin` and add it with
   no judgment. A file that an older build saved can hold a hairpin that ends on a rest. After
   this phase, a reader records a pending hairpin, and `HairpinLoadPass` makes each hairpin
   through `Hairpin.spanning`. It drops each hairpin that `spanning` refuses and reports it.

3. Create `record PendingHairpin(Hairpin.Kind kind, StaffElement anchor, StaffElement end,
   double x1ShiftSs, double x2ShiftSs, double yOffsetSs)` in `dom/PendingHairpin.java`.
   Write its Javadoc in the form of `PendingEnding`'s.

4. Decide the precondition on the anchor's and the end's line. Find out from `MeasureMapper`
   and `MusicXmlCrossLineSpanTest` whether a reader can record a hairpin whose anchor and end
   are on different lines. If a reader can, file input is a real producer: the pass drops and
   reports that hairpin. If no reader can, the pass throws `IllegalArgumentException`, as
   `EndingLoadPass` does. State the result in the contract of `HairpinLoadPass.run`.

5. Write the contract of `public static void run(Song song, List<PendingHairpin>
   pendingHairpins, ChangeReport report)` in `dom/HairpinLoadPass.java`, then implement it.
   It mirrors `EndingLoadPass.run`:
   - It runs inside `song.withoutMutationTracking`.
   - It judges each pending hairpin in order with `Hairpin.spanning(line, kind, range)`. The
     range runs from the anchor's index to the end's index in the anchor's line.
   - A `Spanned` hairpin takes the pending shifts and offset (`setX1ShiftSs`,
     `setX2ShiftSs`, `setUserYOffsetSs`) and is added with `addTo`. `addTo` merges two
     same-kind hairpins that overlap or touch, as the readers' direct `addTo` does now.
   - A `Refused` hairpin is logged at WARN and recorded as a `ReportedChange` with heading
     `Strings.DIALOG_REPORT_HAIRPINS_REMOVED`, detail `Strings.get(reasonKey)`, and the
     location `ElementLocationRange.onLine(lineIndex, range)`. Phase 6 creates
     `ElementLocationRange.onLine(int lineIndex, ElementRange range)`; call it by that name.

6. `EndingLoadPass.place` and the new hairpin `place` have one shape: resolve the anchor's
   line and line index, build the range, switch on a `Spanning`, add the spanned span or
   report the refusal. Extract that shape once and make both passes call it. Refactor
   `EndingLoadPass` first. In `EndingLoadPass`, the patterns become `Spanning.Spanned` and
   `Spanning.Refused`, and `TupletLoadPass.anchorToEnd(lineIndex, span)` becomes
   `ElementLocationRange.onLine(lineIndex, span)`.

7. Change `LoadPasses.run` to `run(Song song, Input input)`, with
   `public record Input(List<PendingTuplet> pendingTuplets, List<PendingEnding>
   pendingEndings, List<PendingHairpin> pendingHairpins, boolean accidentalsConverted)`
   nested in `LoadPasses`. Five parameters exceed the four-parameter limit in
   `.claude/rules/java.md`, so the record is required. The pass order becomes: tuplets,
   endings, hairpins, breath marks, accidental stamp. In the Javadoc, state why the hairpin
   pass runs before the breath-mark pass: the breath-mark pass removes elements, each pass
   reports the indices of the line as it saw it, and no hairpin endpoint is a breath mark.
   Update the `@effects` clause.

8. The legacy migration converts hairpin offsets from pixels in
   `FormatMigrator.migratePixelsToStaffSpace`, by a loop over the registry's hairpins. After
   phase 7, a legacy line holds no hairpin at migration time: the hairpins are pending. Delete
   that loop. Convert the three offsets of each pending hairpin instead, as the method does
   for pending tuplets. Add `List<PendingHairpin> pendingHairpins = List.of()` to
   `MigrationContext`. Update the stage in `MigrationPipeline` that calls
   `migratePixelsToStaffSpace`. Choose the signature. The result must not hold two adjacent
   parameters of one type that a call can transpose.

## ✅ Phase 5: Registry and ending contracts

**Status:** Complete  <br>
**BlockedBy:** 1, 2  <br>
**Files:** src/main/java/songscribe/dom/LineSpans.java, src/main/java/songscribe/dom/Ending.java, src/main/java/songscribe/dom/Hairpin.java  <br>
**Recommended model/effort:** Opus, medium — contracts whose content was decided, plus two universal claims to falsify before they are written

### Tasks

1. `LineSpans.getSpans` promises "the order they were added". The registry promises no
   order. Rewrite the contract: a caller reads the spans in no defined order.
   `findFirstSpan`'s contract says it returns one match when several match, and that which
   one is not defined. Read `findTieAt` and every other caller of `findFirstSpan` with
   `jet_brains_find_referencing_symbols`. If a caller depends on which match it gets, report
   it and stop this task.

   The MusicXML file does not depend on the registry order. The writer's pass order sets the
   order across kinds. In one kind, an element carries at most one start and one stop. Span
   numbers are constants, not a counter. Do not write this reasoning into the contract; it is
   the reason the contract can promise no order.

2. `LineSpans.sweep` returns at once while the song replays. The check is live. Rewrite its
   comment to name two reasons:
   - The recorded batch already carries the companion span mutations.
   - Undo of a deletion of more than one element puts the elements back one at a time through
     `Line.addElement`, and each call sweeps. The line passes through states that no edit
     made, and the rules would judge them. Example: the line is `[Y, ||, X, N …]` and an
     ending anchors on note `N`. The user deletes `||` and `X`, and the ending stays. Undo
     puts `||` back first: `[Y, ||, N]`. `hasOpeningPredecessor` refuses a `||` in front of a
     note anchor, so a sweep would remove the ending in the middle of the undo.

3. `LineSpans`'s class comment says every span query exists in "exactly one place". Before
   you keep that sentence, try to falsify it: look for a span query that is restated outside
   `LineSpans`, in `dom/`, `ui/`, `layout/` and `io/`. If you find one, write the weaker
   sentence that is true, and list each restatement in your report.

4. Rewrite `Ending`'s class comment so that it states: every ending on a line was made by
   `Ending.spanning`. Name the paths: the editor (`MusicEditOperations.canMakeFirstSecondEnding`),
   the load (`EndingLoadPass`), the paste (`PasteSpanReconciliation.settleAgainstTarget`), and
   undo of a span removal, which puts back an ending that `spanning` made. `Span.copy` still
   makes an `Ending` for a clipboard fragment, and that object is never added to a line.
   Before you write the sentence, run `jet_brains_find_referencing_symbols` on `Span/addTo`,
   `Span/copy` and `Ending/createCopy`. Every path that adds an `Ending` must be one of those
   named. If one is not, report it.

   Update the prose of `Ending.spanning`'s Javadoc to name the paste as a caller.

5. **The deletion merge keeps rule 5.** `LineSpans.mergeAdjacentSpans` groups the reshaped
   hairpins of one kind into runs, and `applySpanRun` joins each run into one hairpin. The
   runs look at one kind only, so a merge can cover an opposite-kind hairpin. Example: a
   crescendo on `[0,2]`, a diminuendo on `[2,5]`, a crescendo on `[5,7]`. The user deletes
   elements 3 and 4. The diminuendo becomes `[2,3]` and the second crescendo becomes `[3,5]`.
   The two crescendos merge into `[0,5]`, which holds the whole diminuendo.

   A run ends before a same-kind hairpin when the joined range would share more than one
   element with an opposite-kind hairpin. Both hairpins then stay, each reshaped on its own.
   The example gives a crescendo on `[0,2]`, a diminuendo on `[2,3]` and a crescendo on
   `[3,5]`.
   - Test the joined range against the projected range of every opposite-kind hairpin that
     survives the change, reshaped or not.
   - Use `Span.overlappingBeyondEndpoint`, the predicate that `Hairpin.spanning` uses for
     rule 5. Do not write a second test of rule 5.
   - Update `mergeAdjacentSpans`'s Javadoc and the reshape paragraph of `sweep`'s Javadoc.

   `Hairpin`'s class Javadoc says that the deletion merge keeps rules 1 to 4. It now keeps all
   five. Rewrite that part of the prose. State why the merge keeps rules 1 to 4 without
   `spanning`: its anchor and end are endpoints of hairpins that the deletion reshaped, a
   joined range holds at least the columns of its parts, and a deletion cannot put a bar inside
   a range.

## ✅ Phase 6: Mechanical sweep in dom

**Status:** Complete  <br>
**BlockedBy:** 1, 4, 5  <br>
**Files:** src/main/java/songscribe/dom/Span.java, src/main/java/songscribe/dom/Beam.java, src/main/java/songscribe/dom/LineElement.java, src/main/java/songscribe/dom/LineSpans.java, src/main/java/songscribe/dom/SpanOutcome.java, src/main/java/songscribe/dom/Trill.java, src/main/java/songscribe/dom/Tuplet.java, src/main/java/songscribe/dom/TupletValidator.java, src/main/java/songscribe/dom/TupletLoadPass.java, src/main/java/songscribe/dom/Hairpin.java, src/main/java/songscribe/dom/Ending.java, src/main/java/songscribe/dom/ElementLocationRange.java, src/main/java/songscribe/ui/selection/SelectionCoordinator.java, src/test/java/songscribe/dom/SpanFactory.java  <br>
**Recommended model/effort:** Sonnet, medium — deletions, visibility changes, one extracted factory, private-signature changes and Javadoc tags whose content is stated here

### Tasks

1. **Delete `Span.isIn` and `Span.getEndLine`.** Neither has a reader. Make
   `Span.getAnchorLine` private; `Span.line()` is its only reader. Delete the part of
   `getAnchorLine`'s comment that argues for a null-safe form. Three Javadoc links name
   `isIn`:
   - `dom/LineElement.java` (at `:33`): remove the link.
   - `LineSpans`'s class comment (at `:53`): the sentence "`Span#isIn` derives parentage from
     where its endpoints sit" becomes "derived from where its endpoints sit".
   - `SelectionCoordinator.isOnLine`'s comment in `ui/selection/SelectionCoordinator.java`
     (at `:656`): the same change.

2. **Make `Beam.graceNotes` private.** `Beam.graceStemTouchesBeamAbove` is its only reader.
   `Beam`'s class comment keeps its description of the query, without a link to a public
   member.

3. **Extract the span location factory.** `Span.location()` and `TupletLoadPass.anchorToEnd`
   each build `new ElementLocationRange(new ElementLocation(lineIndex, begin), new
   ElementLocation(lineIndex, end))` from a line index and an `ElementRange`. Add
   `public static ElementLocationRange onLine(int lineIndex, ElementRange range)` to
   `dom/ElementLocationRange.java`, with a one-line contract. Make `Span.location()` and
   `TupletLoadPass` call it. Delete `TupletLoadPass.anchorToEnd`. `SpanLoadPass.place`, which
   `EndingLoadPass` and `HairpinLoadPass` share, already calls `onLine`; do not edit those
   three files.

4. **Change four private signatures that a call can transpose.**
   - `TupletValidator.addSpan(spans, int spanTicks, int noteValueTicks)`: `conventionalSpans`
     and `addSpan` take the `NotatableValue`, not its ticks. `validate` already holds one.
   - `TupletValidator.isStructuralBoundary(type, int index, int endIndex)`: make it a method
     of the nested `SpanMeasure`, which holds the span. Its only parameter is the element's
     type.
   - `Ending.firstBracketOf(type, int anchorIndex, int splitIndex)` and
     `Ending.secondBracketOf`: each takes the ending's `ElementRange` and the split index, and
     reads the types from the element list. `elementsBetween(int before, int after)` keeps two
     ints: a bracket bound of `-1` (a note anchor at index 0) is not an `ElementRange`. Say
     that in one line on `elementsBetween`.

5. **`TupletValidator`'s class comment.** Delete the section about an ABC importer
   (`:71`–`:82`). No ABC importer exists. Rewrite the reference to "constraint 4" (`:52`) so
   that it names the fermata rule.

6. **Add `@return` where it is missing.** Read each method first. Write what it returns, and
   for a `@Nullable` return, the condition for `null`.
   - `Span`: `getAnchorElement`, `getEndElement`, the seven predicate factories (`:475`–`:535`),
     `createCopy` (`:563`).
   - `LineSpans`: both `findSpans` overloads, `hasSpan`, `findBeamAt`, `findTupletAt`,
     `isInsideHairpin`.
   - `Trill.intrinsicWidthSs`, `Tuplet.isNumberOnly`, `Tuplet.getGrade`,
     `Hairpin.Kind.opposite`.
   - `TupletValidator.validateDerived`: complete its tags against
     `.claude/guides/contracts.md`.

7. **Delete comments that restate the name.** In `LineSpans`, delete the Javadoc of
   `sameBeamAt`, `findHairpins`, `hasHairpin`, `hasTrillOverlapping` and `findEndings`. Before
   you do, run `jet_brains_find_referencing_symbols` on each. If a method has no caller, delete
   the method, not only its comment. Report each deletion.

8. **Delete history and rejected alternatives.**
   - `SpanOutcome.Keep`'s comment (`:37`–`:49`) defends the enum against a record. Delete that
     paragraph.
   - `Ending.getSplitIndex`'s comment (`:416`) says the index is "computed live … rather than
     from a value cached during layout". Delete that clause.

9. **`SpanFactory`** (`src/test/java/songscribe/dom/SpanFactory.java`): `endingOver`'s
   switch uses `Spanning.Spanned` and `Spanning.Refused`, and `spanned.span()` in place of
   `spanned.ending()`. Change nothing else in the file.

## ✅ Phase 7: Reader wiring and the ending editor

**Status:** Complete  <br>
**BlockedBy:** 3, 4  <br>
**Files:** src/main/java/songscribe/io/musicxml/MeasureMapper.java, src/main/java/songscribe/io/musicxml/SongMapper.java, src/main/java/songscribe/io/LineIO.java, src/main/java/songscribe/io/SongIO.java, src/main/java/songscribe/io/SongLoader.java, src/main/java/songscribe/ui/MusicEditOperations.java, src/test/java/songscribe/io/LoadPassesTest.java  <br>
**Recommended model/effort:** Sonnet, medium — call-site updates to the signatures phases 1 and 4 define, and one merge of two duplicate methods

### Tasks

1. Read `dom/PendingHairpin.java` and `io/LoadPasses.java` (`LoadPasses.run(Song,
   LoadPasses.Input)` and `LoadPasses.Input`), which phase 4 wrote. Read
   `MigrationContext.pendingHairpins`, which phase 4 added.

2. **`MeasureMapper`.** `buildHairpin` records a `PendingHairpin` in a new
   `pendingHairpins` list. It does not build or add a `Hairpin`. Carry the shifts that
   `applyHairpinShifts` sets now into the record's `x1ShiftSs`, `x2ShiftSs` and `yOffsetSs`.
   Delete `applyHairpinShifts` if nothing else reads it. Delete the record
   `MeasureMapper.Result`. `map` returns `LoadPasses.Input`, which holds the same three
   values plus the pending hairpins. Rewrite `buildHairpin`'s Javadoc: the merge of two
   same-kind wedges happens in the load pass. Keep the drop-and-warn logic for unpaired and
   overlapping wedges.

3. **`SongMapper`.** Call `LoadPasses.run(song, mapped)`.

4. **`LineIO`.** `createCrescendosFromPending` and `createDiminuendosFromPending` are one
   algorithm that differs by kind and by list. Merge them into one method that takes the
   list and the `Hairpin.Kind`. It records a `PendingHairpin` per entry (offsets `data[2]`,
   `data[3]`, `data[4]`) and does not build a `Hairpin`. Keep the
   `DocumentValidation.requireIndexInRange` call. Add `pendingHairpins()`, in the form of
   `pendingEndings()`.

5. **`SongIO`.** In `DocumentReader.getSong`, set `ctx.pendingHairpins` from
   `lineReader.pendingHairpins()` before `MigrationPipeline.runPreAssembly(ctx)`, as
   `ctx.pendingTuplets` is set. Keep the migrated list in a `resolvedPendingHairpins` field.
   Add `pendingHairpins()`, in the form of `pendingTuplets()`.

6. **`SongLoader`.** Call `LoadPasses.run(song, new LoadPasses.Input(reader.pendingTuplets(),
   reader.pendingEndings(), reader.pendingHairpins(), reader.accidentalsConverted()))`.

7. **`MusicEditOperations.canMakeFirstSecondEnding`.** The switch uses `Spanning.Spanned`
   and `Spanning.Refused`, and `spanned.span()` in place of `spanned.ending()`.

8. **`LoadPassesTest`** (`src/test/java/songscribe/io/LoadPassesTest.java`, `:99`): call
   `LoadPasses.run(song, new LoadPasses.Input(List.of(derivedTuplet, droppedTuplet),
   List.of(droppedEnding), List.of(), true))`. Change nothing else in the file.

## ✅ Phase 8: Design docs

**Status:** Complete  <br>
**BlockedBy:** 1, 2, 3, 4, 5  <br>
**Files:** docs/hairpin-editing.md, docs/span-invalidation.md, docs/clipboard.md, docs/change-report.md, docs/musicxml-object-model.md  <br>
**Recommended model/effort:** Sonnet, medium — doc edits whose content is stated here

### Tasks

1. Read `CLAUDE.md` under *Design Docs*. A doc states concepts and how work flows, not class
   or method names. Read `Hairpin`'s class Javadoc and `Hairpin.spanning`, which phase 1
   wrote, and `PasteSpanReconciliation`'s class Javadoc, which phase 2 wrote. The docs must
   agree with them.

2. **`docs/hairpin-editing.md`.**
   - Replace the section "Ending on a rest, but never starting from one": a hairpin never
     starts or ends on a rest. Rests inside a hairpin are unaffected.
   - The two-column paragraph: a rest is not a column.
   - "The span converges before anything is judged": the convergence and every check belong
     to the hairpin's own rule, which the menu, the paste and the load all ask. The menu keeps
     no rule of its own.
   - "Ineligible versus blocked": the rule's refusal says whether the range itself is at
     fault or another hairpin is the obstacle, and the menu maps that directly. A boundary
     that only the absorbed part of the range crosses is the absorbed hairpin's fault.
   - "The model can never hold a shape the menu would refuse": list the five rules. An
     insertion or a replacement that puts a repeat or a non-single barline inside a hairpin
     removes it and reports it. A deletion that leaves two same-kind hairpins touching joins
     them, except when the joined hairpin would cover more than one element of an
     opposite-kind hairpin; then both stay. A file that holds a hairpin that breaks a rule loses that
     hairpin on load, and the load report lists it.
   - "Corpus figures": delete the clause about hairpins bounded by a trailing rest.

3. **`docs/span-invalidation.md`.** Where it describes what an insertion or a replacement does
   to a hairpin, add the structural-boundary removal.

4. **`docs/clipboard.md`.** After the fragment's elements are in, every pasted tuplet,
   ending and hairpin is judged by its kind's own rule against the line as the paste leaves
   it. A refused one is not pasted and is reported. Update the table at `:54`–`:55` if it no
   longer states the result.

5. **`docs/change-report.md`.** In the *Paste* bullet (`:57`), a pasted tuplet, ending or
   hairpin that is not valid where it lands is not pasted. In *The load passes* bullet
   (`:59`), add: a hairpin that is not valid where the file puts it is dropped.

6. **`docs/musicxml-object-model.md`.** The paragraph "A reader never builds a tuplet"
   (`:109`): add that a reader builds no ending and no hairpin either. Each is pending until
   its load pass judges it by the same rule the editor uses.

## ✅ Phase 9: Gate

**Status:** Complete  <br>
**BlockedBy:** 1, 2, 3, 4, 5, 6, 7, 8, 11  <br>
**Files:** —  <br>
**Recommended model/effort:** Sonnet, low — runs the build and the named tests, and reports

### Tasks

1. Run `./scripts/compile.sh --test`.

2. A production compile error that a call-site update to a signature in this plan fixes: fix
   it, and name it in the report. Any other production compile error: stop and report it
   with file and line.

3. Test-tree compile errors are expected in these places. Do not edit a test:
   - `src/test/java/songscribe/dom/EndingTest.java` (`:77`, `:79`): `Spanning.Refused` takes a
     cause, so each refusal row needs an expected cause.
   - `new Hairpin(…)` in `UnitTest` (`:359`), `LineSpansTest` (`:491`, `:495`), `LineTest`
     (`:369`, `:370`), `SpanTest` (`:61`) and `MusicXmlOffsetRoundTripTest` (`:157`): the
     constructor is private.

4. When the test tree compiles, run:
   `./scripts/test.sh EndingTest SpanTest SpanPredicateTest LineSpansTest LineTest ElementChangeTest EndingSectionRuleTest TupletLoadPassTest TupletBeatRuleTest BreathMarkLoadPassTest LoadPassesTest MusicXmlOffsetRoundTripTest MusicXmlCrossLineSpanTest MusicEditOperationsTest FragmentTest`.
   Expected failures: a case that adds or keeps a hairpin that ends on a rest, including the
   `MusicEditOperationsTest` case for one note and the rest after it.

5. For every test-tree compile error and every failing test, give the user one table, and
   stop. Do not change a test until the user approves the table.

   | Test | Kind | Proposal | Reason |
   |---|---|---|---|
   | the test method or fixture | real algorithm / invariant over several calls / known-correct corpus, or none | keep / rewrite to the new contract / delete | the contract it pins, and what changed |

   A test whose kind is "none" is proposed for deletion. After approval, apply the approved
   changes, then run task 4 again and report SUCCESS or the failures.

## ✅ Phase 10: Manual UI verification

**Status:** Complete  <br>
**BlockedBy:** 9  <br>
**Files:** —  <br>
**Recommended model/effort:** — (the user does this phase)

### Tasks

The user runs `./scripts/run.sh` and checks each item:

1. Select one note and the rest after it. Both hairpin menu items are disabled.
2. Add a crescendo over two notes. Select the next two notes. The crescendo item reads
   "Extend Crescendo", and the diminuendo item adds a back-to-back diminuendo.
3. Add a crescendo on notes, then select from its middle into later notes. The diminuendo
   item is disabled (blocked).
4. Insert a `||` or a `:|` inside a hairpin. The hairpin goes, and the change report lists
   it. Undo puts it back.
5. Copy an ending that anchors on a note at the start of its line. Paste it after a `||`.
   The notes paste, the ending does not, and the change report lists the ending.
6. Paste an ending in front of a barline. The ending does not paste, and the report lists it.
7. Copy a hairpin and paste it onto plain notes. It pastes with its shifts.
8. Open a file an older build saved with a hairpin that ends on a rest, if one is at hand.
   The load report lists the hairpin as removed, and the song shows as modified.

## ✅ Phase 11: Legacy ending offsets

**Status:** Complete  <br>
**BlockedBy:** —  <br>
**Files:** src/main/java/songscribe/io/FormatMigrator.java, src/main/java/songscribe/io/LegacyLineOffsets.java, src/main/java/songscribe/io/LineIO.java  <br>
**Recommended model/effort:** Sonnet, low — deletions whose content is stated here

### Tasks

A legacy ending loads with a vertical offset of zero. `LineIO.LineReader.createEndingsFromPendingPairs`
records each `PendingEnding` with offset `0`, and no migration changes it. The legacy
line-level ending offset is not migrated.

1. In `FormatMigrator.migrateLineLevelOffsets`, delete the block that migrates the first/second
   ending offset. Update the method's Javadoc: it converts three legacy offsets, not four.
2. In `FormatMigrator.migratePixelsToStaffSpace`, delete the loop over the registry's
   endings. Update the comment "Convert per-instance Span offsets (Ending, Trill)".
3. Delete the component `firstSecondEndingYPosPx` from `LegacyLineOffsets`. Delete
   `LineIO.LineReader.legacyFsEndingYPosPx` and every line that reads or writes it. Run
   `jet_brains_find_referencing_symbols` on each before you delete it. A legacy file that
   holds the attribute must still load. Read how the reader treats an attribute it does not
   read. If it rejects one, keep the read of this attribute and discard its value.
