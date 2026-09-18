# Step 4 — Extraction and Placement

Source: `plans/design-pass-6/findings-4.md`. Read the finding named in a task before
carrying it out; the tasks below state what changes, the findings document states the
site-by-site evidence. Where a task and `findings-4.md` disagree on a name, a body, or a
site list, the task is right.

Paths below are relative to `src/main/java/songscribe/` unless they start with `src/`,
`docs/` or `plans/`.

**Only Phase 12 writes a new test.** In every other phase, test files change only where a
deleted or renamed member breaks compilation.

**Do not compile to certify a phase.** Phase 13 is the single gate. A phase that leaves
the tree non-compiling is expected. The end state is what gets checked.

**Every Java edit goes through `Edit`**, after reading the exact text with
`jet_brains_find_symbol` and `include_body=true`. Read `.claude/rules/serena.md` first.

**Every new or changed nontrivial API follows `.claude/guides/contracts.md`.** A task that
says "contract" means a Javadoc written to that guide. A task that says "name only" means
a one-line Javadoc that states what the method does and nothing more.

**Comments state the code as it is now.** No task leaves a note that something moved,
was renamed, or was deleted.

## Status Dashboard

| Phase | Description | Status | Sub-plan |
|-------|-------------|--------|----------|
| 1 | [Strings](#-phase-1-strings) | ✅ Complete | — |
| 2 | [Run Queries and Line Accessors](#-phase-2-run-queries-and-line-accessors) | ✅ Complete | — |
| 3 | [Paste Refusal](#-phase-3-paste-refusal) | ✅ Complete | — |
| 4 | [Note Entry Refusal and Cascade](#-phase-4-note-entry-refusal-and-cascade) | ✅ Complete | — |
| 5 | [Breath Mark Load Pass](#-phase-5-breath-mark-load-pass) | ✅ Complete | — |
| 6 | [Breath Mark Fold Invariant](#-phase-6-breath-mark-fold-invariant) | ✅ Complete | — |
| 7 | [Projected Index Contract](#-phase-7-projected-index-contract) | ✅ Complete | — |
| 8 | [Span Removal Primitive](#-phase-8-span-removal-primitive) | ✅ Complete | — |
| 9 | [Dom Call Sites](#-phase-9-dom-call-sites) | ✅ Complete | — |
| 10 | [UI, Layout and IO Call Sites](#-phase-10-ui-layout-and-io-call-sites) | ✅ Complete | — |
| 11 | [Test Compilation Repairs](#-phase-11-test-compilation-repairs) | ✅ Complete | — |
| 12 | [Tests](#-phase-12-tests) | ✅ Complete | — |
| 13 | [Gate](#-phase-13-gate) | ✅ Complete | — |
| 14 | [Manual UI Verification](#-phase-14-manual-ui-verification) | ✅ Complete | — |

## ✅ Phase 1: Strings

**Status:** Complete  <br>
**BlockedBy:** —  <br>
**Files:** src/main/resources/songscribe/strings.properties  <br>
**Recommended model/effort:** Haiku, low effort — adds property rows in sorted position

Read `.claude/guides/strings.md` first. Keys go in sorted position inside their group.
Values use the curly apostrophe. Every constant below is referenced by a later phase, so
the dead-key audit passes once the tree is whole.

### Tasks

1. Add to the `alert` group:
   - `alert.paste.breath.mark.not.here = A breath mark cannot be placed here.`
   - `alert.paste.not.before.breath.mark = These elements may not be inserted before a breath mark.`
   - `alert.title.paste = Paste`
2. Add to the `button` group:
   - `button.remove.and.paste = Remove & paste`
3. Add to the `confirm` group:
   - `confirm.paste.leading.breath.mark = The clipboard begins with a breath mark, which cannot be placed here. Remove it and paste, or cancel?`
   - `confirm.title.paste = Paste`
4. Add to the `dialog.migration` group:
   - `dialog.migration.breath.marks.removed = {0,choice,1#One breath mark was removed|1<{0} breath marks were removed}`
   - `dialog.migration.breath.mark.change = <html>Line {0}, element {1}:<br>{2}</html>`
   - `dialog.migration.reason.breath.mark.host = A breath mark must follow a note or a rest.`

The generated constants are `Strings.ALERT_PASTE_BREATH_MARK_NOT_HERE`,
`Strings.ALERT_PASTE_NOT_BEFORE_BREATH_MARK`, `Strings.ALERT_TITLE_PASTE`,
`Strings.BUTTON_REMOVE_AND_PASTE`, `Strings.CONFIRM_PASTE_LEADING_BREATH_MARK`,
`Strings.CONFIRM_TITLE_PASTE`, `Strings.DIALOG_MIGRATION_BREATH_MARKS_REMOVED`,
`Strings.DIALOG_MIGRATION_BREATH_MARK_CHANGE` and
`Strings.DIALOG_MIGRATION_REASON_BREATH_MARK_HOST`.

## ✅ Phase 2: Run Queries and Line Accessors

**Status:** Complete  <br>
**BlockedBy:** —  <br>
**Files:** dom/StaffElementRun.java, dom/Line.java, dom/LyricRun.java  <br>
**Recommended model/effort:** Fable, low effort — new domain queries with contracts that several callers rely on

This phase adds members only. It changes no existing body. Phase 9 rewrites the bodies in
these three files that the new members replace.

`hasIndex(i)` on `StaffElementRun` is `0 <= i < elementCount()`. The `*At` predicates below
bound with `hasIndex`, like `isKeyChangeAt`. The terminal is a `FINAL_DOUBLE_BARLINE` or a
`REPEAT_RIGHT`, so no breath mark, key change or duration element stands at it, and
`hasIndex` and `effectiveElementCount()` give the same answer for those predicates.

The invariant the breath-mark queries carry: **a breath mark stands only immediately after
a duration element**, where a duration element is one whose `ElementType.isDuration()` is
true (a pitched note or a rest). Phases 3, 4 and 5 make every path that places content
keep that invariant, and Phase 6 makes the writer assume it.

### Tasks

1. On `StaffElementRun`, beside `isKeyChangeAt`, add three sibling predicates. Name only,
   one line each, stating that out of range yields `false`:
   - `default boolean isBreathMarkAt(int index)` — `hasIndex(index) && getElement(index).getType().isBreathMark()`.
   - `default boolean isBarAt(int index)` — `hasIndex(index) && getElement(index).getType().isBar()`.
   - `default boolean isDurationAt(int index)` — `hasIndex(index) && getElement(index).getType().isDuration()`.
2. On `StaffElementRun`, add `default boolean hasGlissandoInto(int index)`: `true` when
   the element at `index - 1` is a `NoteElement` whose `hasGlissando()` is true, which
   connects it to the element at `index`; `false` when `index - 1` is out of range. Bound
   with `hasIndex(index - 1)`, so `index == elementCount()` is a legal question. Contract:
   four callers read it (`EditableRun.deleteRange`, `EditableRun.deleteElement`,
   `LyricRun.repairNeighborsAfterInsertion`, `PreviewElementManager`).
3. On `LyricRun`, add `default void removeGlissandoInto(int index)`: when
   `hasGlissandoInto(index)`, record
   `modifyElement(index - 1, ElementField.SLIDE, note::removeSlide)` where `note` is the
   `NoteElement` at `index - 1`; otherwise do nothing. Must be called inside a modification
   bracket. Contract, with `@effects` stating the one `modifyElement` record, in the form
   `EditableRun.deleteRange`'s Javadoc uses.
4. On `StaffElementRun`, add
   `default boolean needsBarlineBeforeKeyChangeAt(int insertionIndex)`, body
   `!isBarAt(insertionIndex - 1)`. Move `KeyChangeElement.needsBarlineBefore`'s Javadoc
   onto it, rewritten for a run method: drop the `line` parameter, keep the sentence that
   the fit pre-check and the commit must agree, keep the precondition that
   `insertionIndex` is at least `Line.FIRST_LEGAL_KEY_CHANGE_INDEX`. Phase 9 deletes the
   static on `KeyChangeElement`.
5. On `StaffElementRun`, add
   `default StaffElementRun.@Nullable ElementRange nonGraceRange(ElementRange range)`:
   `range` narrowed to its first and last non-grace elements, `null` when every element
   in `range` is a grace note. Implement with `nextNonGraceIndex` and
   `previousNonGraceIndex` where their bounds allow it; otherwise two scans inward from
   the ends. Precondition: every index of `range` satisfies `hasIndex`. Contract: four
   callers read it (`RangeQueries.canToggleBeaming`, `RangeQueries.canToggleTuplet`,
   `MusicEditOperations.toggleBeaming`, `MusicEditOperations.toggleTuplet`).
6. On `StaffElementRun`, beside `canInsertElementAt`, add the breath-mark placement rule
   as three methods. Write the contract of `canInsertRunAt`; the two halves get a name-only
   Javadoc that points at it.
   - `default boolean canLeadRunAt(int index)` — whether a breath mark may be the first
     element of a run inserted at `index`: `isDurationAt(index - 1) && !isBreathMarkAt(index)`.
   - `default boolean canTrailRunAt(int index, ElementType last)` — whether a run whose
     last element has type `last` may be inserted at `index`:
     `!isBreathMarkAt(index) || last.isDuration()`.
   - `default boolean canInsertRunAt(int index, List<? extends StaffElement> run)` —
     `canInsertElementAt(index)`, and `canLeadRunAt(index)` when the first element of
     `run` is a breath mark, and `canTrailRunAt(index, lastType)` for the last element's
     type. Precondition: `run` is not empty. The contract states the invariant above, that
     `canInsertElementAt` is the pair rule this rule adds to, and that a paste asks the two
     halves separately because each half has its own dialog.
7. On `Line`, add `public boolean isTerminalAt(int index)`:
   `hasIndex(index) && song.isTerminal(getElement(index), this)`. Contract: whether the
   element at `index` is the song's terminal; out of range yields `false`. Four callers
   read it (`addElement(StaffElement)`, `effectiveElementCount`,
   `PreviewElementInserter.isDirectClickOnTerminal`,
   `MusicEditOperations.canMakeFirstSecondEnding`).
8. On `Line`, beside `getElements()`, add `public List<StaffElement> effectiveElements()`:
   an unmodifiable live view of `getElements()` with the terminal excluded, which is
   `getElements().subList(0, effectiveElementCount())`. Name only, plus one line that it
   is the list form of `effectiveElementCount()` and is live.

## ✅ Phase 3: Paste Refusal

**Status:** Complete  <br>
**BlockedBy:** 2  <br>
**Files:** ui/component/ScoreViewController.java, ui/edit/PasteModeManager.java  <br>
**Recommended model/effort:** Fable, low effort — a new refusal path inside a method with ordering constraints and an outcome enum two callers switch on

Read `.claude/guides/option-dialogs.md` first. Phase 1 adds the string constants named
here; use them as `Strings.*` constants. Phase 2 adds `StaffElementRun.canLeadRunAt(int)`
and `StaffElementRun.canTrailRunAt(int, ElementType)`; read their contracts on
`dom/StaffElementRun.java`.

Both paste paths, paste mode and paste-replace, call
`ScoreViewController.tryInsertFragment(Line, int, EffectiveRange)`. The breath-mark tests
go there, on the instantiated clones, before the fit gate and before any mutation. A
paste-replace deletes from the range's begin, so the element at `insertIndex - 1` is the
same before and after the deletion, and the leading test reads only that predecessor. A
paste-replace never meets the trailing test, because `effectiveEnd` takes a breath mark
after the range into the deletion.

### Tasks

1. Add a constant `DECLINED_BY_ALERT` to `ScoreViewController.FragmentInsertOutcome`:
   an error alert was shown, nothing was mutated, and the user may pick another point.
   Update the enum's Javadoc and the `@return` of `tryInsertFragment` for it.
2. In `tryInsertFragment`, after `clones` is computed and after the `clones.isEmpty()`
   branch, and before `restamps`, add the two tests in this order, on the clone list as it
   stands:
   1. The first clone is a breath mark and `!line.canLeadRunAt(insertIndex)`. When the
      fragment is that one breath mark alone, show
      `OptionDialogs.showErrorMessage(null, Strings.ALERT_TITLE_PASTE, Strings.ALERT_PASTE_BREATH_MARK_NOT_HERE)`
      and return `DECLINED_BY_ALERT`. Otherwise show a two-button confirm through
      `OptionDialogs.showOptionDialog` with title `Strings.CONFIRM_TITLE_PASTE`, message
      `Strings.CONFIRM_PASTE_LEADING_BREATH_MARK`, labels
      `Strings.get(Strings.BUTTON_CANCEL)` and `Strings.get(Strings.BUTTON_REMOVE_AND_PASTE)`,
      default `Cancel`, in the form `EndingConfirms.showEndingConfirm` uses. Cancel, or a
      closed dialog, returns `CANCELLED`. "Remove & paste" drops the leading clone from
      the list every later statement reads, so the fit check, the chain walk and the span
      reconciliation see the run that lands. Check that `instantiated.spans()` holds no
      span anchored on the dropped clone; a breath mark anchors no span, so state that in
      a comment where the clone is dropped.
   2. `!line.canTrailRunAt(insertIndex, lastCloneType)` for the last clone's type. Show
      `OptionDialogs.showErrorMessage(null, Strings.ALERT_TITLE_PASTE, Strings.ALERT_PASTE_NOT_BEFORE_BREATH_MARK)`
      and return `DECLINED_BY_ALERT`.
   A lone breath mark in front of a breath mark fails the first test, so it never reaches
   the second.
3. `clones` is read after the drop by `ProjectedEdit`, `AccidentalFitGate.fits`,
   `InsertionSpacingCalculator.calculateFragmentInsertion`, the ending confirm,
   `PasteSpanReconciliation.reconcile` and `line.insertRun`. Make the dropped list the
   one variable every one of those reads. Do not leave two lists live.
4. In `tryInsertFragment`'s Javadoc, state the two refusals beside the `LINE_FULL` and
   `CANCELLED` paragraphs: what each dialog says, which outcome each returns, and that
   both leave the line untouched.
5. In `PasteModeManager.insertionPointChosen`, map `DECLINED_BY_ALERT` to
   `InsertionPointMode.Placement.DECLINED`, beside `LINE_FULL` and `EMPTY`. `CANCELLED`
   stays `COMPLETED`. Update the method's Javadoc: the alert leaves the mode live, the
   confirm's Cancel ends it.
6. `ScoreViewController.handlePaste` compares the outcome to `INSERTED` only, so
   `DECLINED_BY_ALERT` leaves the selection and the clipboard untouched there without
   a change. Confirm that by reading it, and change nothing in it.
7. `PasteModeManager.acceptsInsertionIndex` keeps answering `true`. Its Javadoc says the
   fit is not decided there; add one sentence that the breath-mark placement rule is not
   decided there either, and that `tryInsertFragment` alerts on the click.

## ✅ Phase 4: Note Entry Refusal and Cascade

**Status:** Complete  <br>
**BlockedBy:** 2  <br>
**Files:** ui/component/score/PreviewElementManager.java, ui/component/score/PreviewElementInserter.java  <br>
**Recommended model/effort:** Fable, low effort — the ghost-preview gate and a replacement cascade with undo ordering

Phase 2 adds `StaffElementRun.canInsertRunAt(int, List)`, `canLeadRunAt(int)`,
`hasGlissandoInto(int)` and `isBreathMarkAt(int)`; read their contracts on
`dom/StaffElementRun.java`. Phase 10 rewrites `PreviewElementInserter.isDirectClickOnTerminal`;
leave it alone here.

Two rules land in the preview:

- The ghost preview hides at a gap slot where the preview element may not be inserted
  under `canInsertRunAt` with a one-element run. This covers the pair rule the gap check
  already applies, a breath mark not after a duration element or in front of another
  breath mark, and a non-duration element in front of a breath mark. No alert is shown
  for any of these.
- The glissando refusal stays the preview's own: a breath mark is also hidden where
  `hasGlissandoInto(xIndex)`.
- A replacement of the element before a breath mark by a non-duration element takes the
  breath mark with it, as `EditableRun.deleteElement` does for a deletion.

### Tasks

1. In `PreviewElementManager.trackMouse`, the gap check
   `previewElement != null && elementAtX == null && !line.canInsertElementAt(xIndex)`
   becomes
   `previewElement != null && elementAtX == null && !line.canInsertRunAt(xIndex, List.of(previewElement))`.
   Rewrite the comment above it to state the rule it now applies, without naming what it
   applied before.
2. Rewrite `PreviewElementManager.isBreathMarkInsertionBlocked` so that for a breath-mark
   preview it returns
   `overExistingElement || !line.canLeadRunAt(xIndex) || line.hasGlissandoInto(xIndex)`.
   The `xIndex == 0` test and the local `precedingElement` go; `canLeadRunAt` answers
   `false` at 0. Rewrite its Javadoc: the placement rule is the run's, the glissando
   refusal and the over-element refusal are the preview's.
3. `PreviewElementInserter.handleClick` must refuse every gap click `trackMouse` hides
   the ghost for. Today it calls `isBreathMarkInsertionBlocked` and, for a click over an
   element, `canReplaceElementAt`. Add the gap-insert refusal: when
   `!overElementX && !line.canInsertRunAt(insertionIndex, List.of(previewElement))`,
   return. Place it beside the `isBreathMarkInsertionBlocked` call, after the
   `previewElement == null` guard so the list is non-null.
4. In `PreviewElementInserter.modifyExistingElement`, after `line.setElement(elementIndex, replacement)`
   and before the grace-note cleanup, cascade the breath mark: when
   `!previewType.isDuration() && line.isBreathMarkAt(elementIndex + 1)`, delete it
   through `line.deleteRange(line.effectiveRange(ElementRange.single(elementIndex + 1)))`.
   Read `EditableRun.deleteRange` and `StaffElementRun.effectiveRange` first and confirm
   that `effectiveRange` of a lone breath mark widens nothing; state that in the comment.
   The cascade runs inside the caller's bracket, so undo restores the breath mark before
   the old element, in reverse order. The grace-note cleanup below reads `elementIndex`
   and `elementIndex - 1` only, so the cascade at `elementIndex + 1` does not move them.
5. Update `modifyExistingElement`'s Javadoc with the cascade, in the same sentence shape
   `EditableRun.deleteElement`'s Javadoc uses for its cascade.

## ✅ Phase 5: Breath Mark Load Pass

**Status:** Complete  <br>
**BlockedBy:** 2  <br>
**Files:** dom/BreathMarkLoadPass.java, io/LoadMigrations.java, ui/dialog/MigrationWindow.java  <br>
**Recommended model/effort:** Fable, low effort — a new post-parse pass, a report type, and its place in the migration report

Phase 1 adds `Strings.DIALOG_MIGRATION_BREATH_MARKS_REMOVED`,
`Strings.DIALOG_MIGRATION_BREATH_MARK_CHANGE` and
`Strings.DIALOG_MIGRATION_REASON_BREATH_MARK_HOST`. Phase 2 adds
`StaffElementRun.isDurationAt(int)`.

`LoadMigrations.run(Song, boolean)` is called by `io/SongLoader.load` for the legacy
`.mssw` reader and by `io/musicxml/SongMapper.map` for the MusicXML reader. Only the legacy
reader can produce a breath mark that does not follow a duration element: the MusicXML
writer folds every breath mark into the `<note>` before it and the reader appends it after
that note. The pass runs for both readers, and the MusicXML path finds nothing.

Mirror `dom/TupletLoadPass.java`: a `public static Report run(Song)` that runs under
`song.withoutMutationTracking`, logs one line when it removed anything, and returns a
`Report` record. Read `TupletLoadPass` in full before writing the new class.

### Tasks

1. Create `dom/BreathMarkLoadPass.java`, `public final class BreathMarkLoadPass`, with a
   private constructor. Class Javadoc: what it drops and why, in the present tense. It
   holds:
   - `public record Removed(int lineIndex, int elementIndex)` — one breath mark the pass
     removed, 0-based as the document stores it.
   - `public record Report(List<Removed> removed)` with a defensive-copy compact
     constructor, `static Report empty()`, and `boolean isEmpty()`.
   - `public static Report run(Song song)`. For each line in document order, walk the
     element indices forward. At index `i`, when the element is a breath mark and
     `!line.isDurationAt(i - 1)`, remove it with `line.removeElement(i)` and record it;
     do not advance `i` past the element that closed up. A breath mark that follows a
     removed breath mark is tested against its new predecessor, so a run of consecutive
     breath marks keeps at most its first. Contract: the invariant from
     `StaffElementRun.canInsertRunAt`, that removal is through the primitive with no
     lyric repair because a breath mark carries none, and that the pass emits no
     mutations.
2. In `io/LoadMigrations.java`, add a record component
   `BreathMarkLoadPass.Report breathMarkReport` beside `tupletReport`. `run` calls
   `BreathMarkLoadPass.run(song)` after `TupletLoadPass.run(song)` and before
   `stampAccidentalsAfterParsing()`. `isEmpty()` includes it. Update the record's
   Javadoc `@param` list and the paragraph on pass order: the breath-mark pass reads no
   tuplet and changes no note, so its place in the order is free; state that it runs
   after the tuplet pass so the removed breath mark's index in the report is the index of
   the settled line.
3. In `ui/dialog/MigrationWindow.java`:
   - `buildTree` adds a group titled
     `Strings.get(Strings.DIALOG_MIGRATION_BREATH_MARKS_REMOVED, n)` after the tuplet
     groups, one leaf per `Removed`, described by
     `Strings.get(Strings.DIALOG_MIGRATION_BREATH_MARK_CHANGE, lineIndex + 1, elementIndex + 1, Strings.get(Strings.DIALOG_MIGRATION_REASON_BREATH_MARK_HOST))`.
     `addGroup` takes a `List<? extends TupletLoadPass.Change>`; generalize it to take
     the already-described leaf strings, or add the group inline. Do not duplicate the
     loop.
   - `show`'s suppressed log line reports the breath-mark count beside the tuplet counts.
4. Search `src/test/java/songscribe/io/LoadMigrationsTest.java` and any other constructor
   call of `LoadMigrations` for the record constructor; the new component breaks them.
   Fix each with `BreathMarkLoadPass.Report.empty()`.

## ✅ Phase 6: Breath Mark Fold Invariant

**Status:** Complete  <br>
**BlockedBy:** —  <br>
**Files:** io/musicxml/NoteBuilder.java  <br>
**Recommended model/effort:** Fable, low effort — turns two silent drops into an invariant failure in the form the null guide sets, and rewrites a history-narrating Javadoc

Read `.claude/guides/null-handling.md`, the section on reacting to an unexpected null,
before task 1. Read `.claude/rules/development.md`'s *No history in comments* before task 2.
Phase 10 rewrites the two `line.getElements().get(i)` sites in this file; leave them alone
here.

### Tasks

1. In `NoteBuilder.foldBreathMark`, the two early returns, `elementIndex == 0` and
   `precedingNote == null`, become one invariant failure in the form the null guide sets
   for an impossible null. The invariant: a breath mark stands only immediately after a
   duration element, and every duration element produces a `Note`. Name the line index
   and element index in the failure message.
2. Rewrite `foldBreathMark`'s Javadoc in the present tense. It states: MusicXML has no
   standalone breath mark, so the mark is an articulation of the note before it; the
   lookup is the immediately preceding element of the same line; the invariant above
   makes that lookup total, and its failure is an invariant failure. Every sentence about
   a streaming writer, a peek, or what "is gone" is removed.

## ✅ Phase 7: Projected Index Contract

**Status:** Complete  <br>
**BlockedBy:** —  <br>
**Files:** dom/ElementChange.java  <br>
**Recommended model/effort:** Fable, low effort — narrows a contract and removes the guards it made dead

The contract of `ElementChange.projectedIndexOf(StaffElement)` narrows to elements of the
change's own line. `Line.getElementIndex` throws `IllegalArgumentException` for an element
that is not on the line, and that throw is the promise. The one reader is
`Hairpin.projectedEndpointIndexOf`, whose endpoints are always on the hairpin's line.

### Tasks

1. In the interface Javadoc of `projectedIndexOf`, remove the clause that `null` is
   returned when the element sits on another line. State the precondition that `element`
   is an element of the change's line, and that `null` means the element is not present
   after the change.
2. In `Insertion.projectedIndexOf` and `Deletion.projectedIndexOf`, delete the
   `if (!line.contains(element)) return null;` guard and its comment. All four shapes then
   call `line.getElementIndex(element)` directly.
3. Search for a `{@link}` or `{@code}` reference to the removed clause elsewhere in
   `ElementChange.java`; rewrite any that names it.

## ✅ Phase 8: Span Removal Primitive

**Status:** Complete  <br>
**BlockedBy:** —  <br>
**Files:** dom/LineSpans.java, dom/Tuplet.java, dom/Trill.java, dom/Span.java, ui/action/TrillAction.java  <br>
**Recommended model/effort:** Sonnet, low effort — visibility change, two wrapper deletions, three call-site updates

`LineSpans.removeOverlapping(Class<? extends Span> type, ElementRange range)` is private.
Its public wrappers are `removeOverlappingTuplets(ElementRange)` and
`removeTrillsOverlapping(ElementRange)`. Step 5 of this pass writes its contract; this
phase writes no contract.

### Tasks

1. Make `LineSpans.removeOverlapping` public. Keep its Javadoc; add the sentence from
   `removeTrillsOverlapping`'s Javadoc that a span whose anchor is detached is still
   found, stated for every kind.
2. Delete `removeOverlappingTuplets` and `removeTrillsOverlapping`.
3. `Tuplet.applyDisplacementPolicy` calls `removeOverlapping(Tuplet.class, range)`.
   `Trill.applyDisplacementPolicy` and `TrillAction.performAction` call
   `removeOverlapping(Trill.class, range)`.
4. The `{@link}` in `Span.java` that names `removeTrillsOverlapping` names
   `LineSpans#removeOverlapping`.

## ✅ Phase 9: Dom Call Sites

**Status:** Complete  <br>
**BlockedBy:** 2  <br>
**Files:** dom/Line.java, dom/StaffElementRun.java, dom/EditableRun.java, dom/LyricRun.java, dom/KeyChangeElement.java, dom/Beam.java  <br>
**Recommended model/effort:** Sonnet, medium effort — body rewrites whose shape Phase 2's members fix

Phase 2 added, on `StaffElementRun`: `isBreathMarkAt`, `isBarAt`, `isDurationAt`,
`hasGlissandoInto`, `needsBarlineBeforeKeyChangeAt`, `nonGraceRange`; on `LyricRun`:
`removeGlissandoInto`; on `Line`: `isTerminalAt`, `effectiveElements`. Read each before
using it.

### Tasks

1. `EditableRun.deleteRange`: the glissando strip, the `if` on `rangeBegin > 0 &&
   getElement(rangeBegin - 1) instanceof NoteElement ...` with its `modifyElement`,
   becomes `removeGlissandoInto(rangeBegin)`. Keep the comment that the strip is
   recorded for undo; drop the rest of that comment.
2. `EditableRun.deleteElement`: the strip guarded by `!hasPrecedingPairedGraceNote &&
   index > 0 && ...` becomes `removeGlissandoInto(firstDeletedIndex)`. The
   `!hasPrecedingPairedGraceNote` test is dead: a glissando from the note before a paired
   grace note into that grace note is not a state the model holds. Replace the comment
   above the strip with one sentence on why `firstDeletedIndex` is the index asked. Keep
   `hasPrecedingPairedGraceNote` for the removal below it.
3. `EditableRun.deleteElement`: the cascade test
   `firstDeletedIndex < effectiveElementCount() && getElement(firstDeletedIndex).getType().isBreathMark()`
   becomes `isBreathMarkAt(firstDeletedIndex)`.
4. `LyricRun.repairNeighborsAfterInsertion(int, List)`: the strip becomes
   `if (!getElement(firstInsertedIndex).getType().isPitchedNote()) { removeGlissandoInto(firstInsertedIndex); }`.
   The comment above it stays.
5. `StaffElementRun.effectiveEnd`: the breath-mark test becomes `isBreathMarkAt(end + 1)`.
6. `StaffElementRun.isKeyChangeBehindBarline`: body becomes
   `return isKeyChangeAt(index) && isBarAt(index - 1);`. Its Javadoc's `@param` sentence
   on the bound stays true; check it and leave it.
7. `StaffElementRun.isInsideGraceHostPair`: body becomes
   `isPairedGraceNote(index) || isHostOfPairedGraceNote(index)`.
8. `Line.effectiveElementCount`: body becomes
   `return isTerminalAt(elementCount() - 1) ? elementCount() - 1 : elementCount();`.
   Javadoc stays.
9. `Line.addElement(StaffElement)`: body becomes `addElement(effectiveElementCount(), element);`.
   Javadoc stays.
10. Delete `KeyChangeElement.needsBarlineBefore`. Phase 10 rewrites its two callers.
11. `Beam.noteTypeInLevel`: the two `while` loops and the two bounds tests become
    ```java
    var previous = line.previousNonGraceIndex(noteIndex);
    var next = line.nextNonGraceIndex(noteIndex);

    return previous != null && noteTypeInLevel(previous, level)
        && next != null && noteTypeInLevel(next, level);
    ```
    Read `previousNonGraceIndex` and `nextNonGraceIndex` first and confirm their bounds
    match the loops they replace. Rewrite the method's comment for the new body.
12. In `Line.java`, close the run of blank lines after `effectiveElementCount` and the
    run after `getElementIndex`. One blank line between members.

## ✅ Phase 10: UI, Layout and IO Call Sites

**Status:** Complete  <br>
**BlockedBy:** 2, 4, 6  <br>
**Files:** layout/KeyEditFitCalculator.java, ui/dialog/KeyChangeDialogController.java, ui/selection/RangeQueries.java, ui/MusicEditOperations.java, layout/AccidentalFitGate.java, io/musicxml/NoteBuilder.java, ui/component/score/PreviewElementInserter.java  <br>
**Recommended model/effort:** Sonnet, medium effort — call-site rewrites against members Phase 2 fixed

Phase 2 added, on `StaffElementRun`: `needsBarlineBeforeKeyChangeAt(int)`,
`nonGraceRange(ElementRange)`; on `Line`: `isTerminalAt(int)`, `effectiveElements()`.
Read each before using it. `ElementRange.size()` is the element count of a range.

### Tasks

1. `KeyEditFitCalculator` and `KeyChangeDialogController`: each call of
   `KeyChangeElement.needsBarlineBefore(line, i)` becomes
   `line.needsBarlineBeforeKeyChangeAt(i)`.
2. `RangeQueries`: delete `nonGraceBegin` and `nonGraceEnd`. In `canToggleBeaming` and
   `canToggleTuplet`, the pair of lookups and the test
   `beginIndex == null || endIndex == null || beginIndex.equals(endIndex)` become
   `var grouped = line.nonGraceRange(range.range()); if (grouped == null || grouped.size() < 2) …`,
   and the `ElementRange` built from the pair is `grouped`.
3. `MusicEditOperations.toggleBeaming` and `toggleTuplet`: the same rewrite as task 2.
   Keep the comment that cites #592 where it stands.
4. `MusicEditOperations.canMakeFirstSecondEnding`: the test
   `extendedEnd < line.elementCount() && song.isTerminal(line.getElement(extendedEnd), line)`
   becomes `line.isTerminalAt(extendedEnd)`.
5. `PreviewElementInserter.isDirectClickOnTerminal`: body becomes
   `return line.isTerminalAt(xIndex);`. If `song` is then unused there, drop the parameter
   and update its callers in the same file.
6. `AccidentalFitGate.otherLinesFit`: `line.getElements().subList(0, line.effectiveElementCount())`
   becomes `line.effectiveElements()`.
7. `NoteBuilder`: the two `line.getElements().get(i)` reads become `line.getElement(i)`.

## ✅ Phase 11: Test Compilation Repairs

**Status:** Complete  <br>
**BlockedBy:** 8, 9, 10  <br>
**Files:** src/test/java/songscribe/  <br>
**Recommended model/effort:** Sonnet, low effort — rename or delete test calls that pin deleted members

Members deleted or made non-static in this plan: `LineSpans.removeOverlappingTuplets`,
`LineSpans.removeTrillsOverlapping`, `KeyChangeElement.needsBarlineBefore`,
`RangeQueries.nonGraceBegin`, `RangeQueries.nonGraceEnd`. Members whose signature
changed: none. Phase 5 handles the `LoadMigrations` constructor.

### Tasks

1. With `jet_brains_find_referencing_symbols`, list every test reference to the five
   members above. For each: a call becomes the call of the member that replaced it, as
   Phases 8, 9 and 10 state. A test method that exists only to pin one of those members
   is deleted and named in the phase's report.
2. Do not run the tests. Phase 13 runs them.

## ✅ Phase 12: Tests

**Status:** Complete  <br>
**BlockedBy:** 2, 5, 9  <br>
**Files:** src/test/java/songscribe/dom/StaffElementRunTest.java, src/test/java/songscribe/dom/BreathMarkLoadPassTest.java  <br>
**Recommended model/effort:** Sonnet, medium effort — test wiring against contracts the plan fixes

Read `.claude/guides/testing-common.md` and `.claude/guides/testing-unit.md` first. Read
the existing `StaffElementRunTest` and `SongFactory`/`StaffElementFactory` for the fixture
shape. Before writing each test method, check whether it sits beside a same-shape sibling;
when it does, both are rows in one parameterized table.

The tests below are the whole list. Nothing else is written. No test covers
`isBreathMarkAt`, `isBarAt`, `isDurationAt`, `hasGlissandoInto`, `isTerminalAt`,
`effectiveElements`, `needsBarlineBeforeKeyChangeAt` or the paste dialogs.

| Test | Kind |
|---|---|
| `nonGraceRange` narrows a range to its non-grace endpoints: grace notes at both ends are excluded, a range of only grace notes yields `null`, a range with one non-grace element yields that single | real algorithm with logic worth checking |
| `canInsertRunAt` composes the three rules: a breath mark leading a run after a barline, at index 0, or in front of a breath mark is refused; after a note or a rest it is accepted; a run ending in a barline in front of a breath mark is refused; a run ending in a note there is accepted; a run at the slot inside a grace pair is refused whatever it holds | real algorithm with logic worth checking |
| `BreathMarkLoadPass.run` on a song holding a breath mark at index 0, one after a barline, one after a note, and two consecutive after a note: removes the first, the second and the last of the consecutive pair, keeps the others, reports each removal with its line and element index in document order, and emits no mutation | invariant spanning several calls |

### Tasks

1. Add the first two tests to `StaffElementRunTest`.
2. Create `src/test/java/songscribe/dom/BreathMarkLoadPassTest.java` with the third.
   Build the song through `SongFactory` and `Line.addElement`, not through a reader.

## ✅ Phase 13: Gate

**Status:** Complete  <br>
**BlockedBy:** 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12  <br>
**Files:** —  <br>
**Recommended model/effort:** Sonnet, low effort — runs the build and the named tests, fixes what they report

### Tasks

1. Run `./scripts/compile.sh --test`. It must print SUCCESS. Fix every error in the
   tree; each one is information about a call site a phase did not account for.
2. Run `./scripts/test.sh StaffElementRunTest BreathMarkLoadPassTest LineDeletionTest LoadMigrationsTest`.
   All green. Fix failures in the code or the test as the contract decides; never by
   loosening a contract.
3. Confirm no `Strings.*` constant Phase 1 added is unreferenced under `src/`; the build's
   dead-key audit reports one.
4. Snapshot: `git add -A && git stash store -m "Finished step 4" "$(git stash create)"`.

## ✅ Phase 14: Manual UI Verification

**Status:** Complete  <br>
**BlockedBy:** 13  <br>
**Files:** —  <br>
**Recommended model/effort:** Fable, low effort — the user runs the app; the agent lists the checks and records what the user reports

The user runs `./scripts/run.sh`. Never run it without their permission.

### Tasks

1. Note entry with the breath-mark tool: the ghost hides at index 0, after a barline,
   after a grace note, in front of a breath mark, and where the note before the slot has a
   glissando. It shows after a note and after a rest.
2. Note entry with the barline tool: the ghost hides in the slot between a note and its
   breath mark. Clicking a barline over the note before a breath mark replaces the note
   and removes the breath mark; one undo restores both.
3. Paste mode with a fragment that begins with a breath mark, clicked after a barline:
   the confirm titled "Paste" shows "Cancel" and "Remove & paste". "Remove & paste" pastes
   the rest; "Cancel" ends paste mode.
4. Paste mode with a fragment of one breath mark, clicked after a barline: the alert
   "A breath mark cannot be placed here." shows, and paste mode stays live.
5. Paste mode with a fragment that ends in a barline, clicked between a note and its
   breath mark: the alert "These elements may not be inserted before a breath mark." shows,
   and paste mode stays live.
6. Paste-replace of a selection with a fragment that begins with a breath mark, where the
   element before the selection is a barline: the same confirm as task 3.
7. Open a legacy `.mssw` file that holds a breath mark after a barline: the migration
   window lists it under "One breath mark was removed" with its line and element, and the
   song opens modified.
