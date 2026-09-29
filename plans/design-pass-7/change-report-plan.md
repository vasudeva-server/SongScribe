# Modification Rules and Change Report

Branch: `design-pass-7`. Every phase runs as a fresh subagent. Before any Java work, read
`.claude/rules/serena.md` and follow it for all Java exploration and refactoring. Every file
change goes through `Edit` or `Write`; a bulk find-and-replace goes through
`mcp__serena__replace_in_files` with `dry_run: true` first. Do not compile or run tests in any
phase except Phase 10.

## Status Dashboard

| Phase | Description | Status | Sub-plan |
|-------|-------------|--------|----------|
| 1 | [Report Types and Rule Registry](#-phase-1-report-types-and-rule-registry) | ✅ Complete | — |
| 2 | [Close-Time Rules and the Ending Section Rule](#-phase-2-close-time-rules-and-the-ending-section-rule) | ✅ Complete | — |
| 3 | [Funnel Removals Report](#-phase-3-funnel-removals-report) | ✅ Complete | — |
| 4 | [Delete Ending Prompts and Compensation](#-phase-4-delete-ending-prompts-and-compensation) | ✅ Complete | — |
| 5 | [Delete the Beat Chokepoint and Report Paste Drops](#-phase-5-delete-the-beat-chokepoint-and-report-paste-drops) | ✅ Complete | — |
| 6 | [Load Passes Report](#-phase-6-load-passes-report) | ✅ Complete | — |
| 7 | [Report Window and Strings](#-phase-7-report-window-and-strings) | ✅ Complete | — |
| 8 | [Design Docs](#-phase-8-design-docs) | ✅ Complete | — |
| 9 | [Tests](#-phase-9-tests) | ✅ Complete | — |
| 10 | [Compile and Test Gate](#-phase-10-compile-and-test-gate) | ✅ Complete | — |
| 11 | [Manual UI Verification](#-phase-11-manual-ui-verification) | ⏳ Pending | — |

## String keys

Every phase that names a string uses the constants below. The constant name is the key
uppercased with dots replaced by underscores (`dialog.report.beams.removed` →
`Strings.DIALOG_REPORT_BEAMS_REMOVED`). Phase 7 writes all of them to
`src/main/resources/songscribe/strings.properties`. Other phases reference the constants only.

**Group headings** (MessageFormat with the entry count as `{0}`):

| Key | Value |
|---|---|
| `dialog.report.beams.removed` | `{0,choice,1#One beam was removed\|1<{0} beams were removed}` |
| `dialog.report.endings.removed` | `{0,choice,1#One ending was removed\|1<{0} endings were removed}` |
| `dialog.report.hairpins.removed` | `{0,choice,1#One hairpin was removed\|1<{0} hairpins were removed}` |
| `dialog.report.ties.removed` | `{0,choice,1#One tie was removed\|1<{0} ties were removed}` |
| `dialog.report.trills.removed` | `{0,choice,1#One trill was removed\|1<{0} trills were removed}` |
| `dialog.report.tuplets.removed` | `{0,choice,1#One tuplet was removed\|1<{0} tuplets were removed}` |
| `dialog.report.tuplets.updated` | `{0,choice,1#One tuplet was updated\|1<{0} tuplets were updated}` |
| `dialog.report.breath.marks.removed` | `{0,choice,1#One breath mark was removed\|1<{0} breath marks were removed}` |
| `dialog.report.accidentals.converted` | `Accidentals were converted` |

**Descriptions** (plain text, read with `Strings.get(key)` unless a pattern is shown):

| Key | Value |
|---|---|
| `dialog.report.accidentals.detail` | `Natural + accidental was converted to plain accidental.` |
| `dialog.report.updated.detail` | `The file did not record this tuplet’s timing. It plays {0} notes in the time of {1}.` |
| `dialog.report.reason.endpoint.deleted` | `It started or ended on an element that was deleted.` |
| `dialog.report.reason.beam.not.beamable` | `A note it joined was replaced by an element that cannot be beamed.` |
| `dialog.report.reason.hairpin.endpoint.invalid` | `It started or ended on an element that can no longer hold the end of a hairpin.` |
| `dialog.report.reason.tuplet.interior.changed` | `The notes inside it changed, so it is no longer a valid tuplet.` |
| `dialog.report.reason.ending.not.valid` | `The ending is not a valid first or second ending.` |
| `dialog.report.reason.ending.bar.after.end` | `A barline or repeat cannot directly follow the bar that ends the ending.` |
| `dialog.report.reason.ending.split.not.right.repeat` | `The first bracket must end on a right repeat.` |
| `dialog.report.reason.ending.no.open.section` | `The repeat that ends its first bracket has no balanced repeated section to go back to.` |
| `dialog.report.reason.tie.not.held` | `The notes it joined no longer have the same pitch.` |
| `dialog.report.reason.merged` | `It was merged with a neighboring one of the same kind.` |
| `dialog.report.reason.replaced` | `A new one of the same kind was added over it.` |
| `dialog.report.reason.paste.straddled` | `The paste landed inside it.` |
| `dialog.report.reason.paste.fragment.dropped` | `It was not pasted, because the paste landed inside one of the same kind.` |
| `dialog.report.reason.paste.tuplet.rejected` | `It does not fit the beat or the barlines where it was pasted.` |
| `dialog.report.reason.breath.mark.host` | `A breath mark must follow a note or a rest.` |
| `dialog.report.reason.bad.ratio` … `dialog.report.reason.too.few.notes` | the nine tuplet reasons, same values as the `dialog.migration.reason.*` keys they rename |

**Window text:**

| Key | Value |
|---|---|
| `dialog.report.load.title` | `Validity Check` |
| `dialog.report.load.message` | `During load, some modifications were made to the song.` |
| `dialog.report.edit.title` | `Changes Made` |
| `dialog.report.edit.message` | `This edit also made the following changes to the song.` |
| `dialog.report.location.element` | `Line {0}, element {1}` |
| `dialog.report.location.elements` | `Line {0}, elements {1}–{2}` |
| `dialog.report.location.lines` | `Line {0}, element {1} – line {2}, element {3}` |
| `dialog.report.change` | `<html>{0}:<br>{1}</html>` |

---

## ✅ Phase 1: Report Types and Rule Registry

**Status:** Complete  <br>
**BlockedBy:** —  <br>
**Files:** src/main/java/songscribe/dom/ElementLocationRange.java, src/main/java/songscribe/dom/ReportedChange.java, src/main/java/songscribe/dom/ChangeReport.java, src/main/java/songscribe/dom/ModificationRule.java, src/main/java/songscribe/dom/ModificationRules.java, src/main/java/songscribe/dom/SpanOutcome.java, src/main/java/songscribe/dom/Span.java, src/main/java/songscribe/dom/ModificationSession.java, src/main/java/songscribe/dom/Song.java, src/main/java/songscribe/message/notification/ChangesWereReportedNotification.java  <br>
**Recommended model/effort:** Opus, high — new types that carry invariants, their contracts, and the change to the bracket-close sequence that undo ordering depends on

Read first: `.claude/guides/contracts.md`, `.claude/guides/null-handling.md`,
`.claude/guides/messages.md`, `docs/mutations.md`, `docs/span-invalidation.md`.

### Tasks

1. **`ElementLocationRange`** (`dom/ElementLocationRange.java`): write the contract, then the
   record `ElementLocationRange(ElementLocation start, ElementLocation end)`. Both hold
   zero-based model indices. The compact constructor throws `IllegalArgumentException` unless
   `start` is at or before `end` in song order (line index first, then element index). Add
   `static ElementLocationRange single(ElementLocation location)`. A range may cross lines: a
   tie from the last note of line 3 to the first note of line 4 is one range.
2. **`ReportedChange`** (`dom/ReportedChange.java`): the record
   `ReportedChange(String headingKey, String description, @Nullable ElementLocationRange where)`.
   `headingKey` is a `Strings` key whose value is a MessageFormat pattern taking the count of
   entries in its group as `{0}`. `description` is finished user-facing text, built by the
   code that made the change. `where` is `null` only for a change with no location, such as
   the accidental conversion at load. State in its Javadoc that the reporter builds the text
   because the reporter holds the domain knowledge, and the view only groups and shows it.
3. **`ChangeReport`** (`dom/ChangeReport.java`): write the contract, then the class. It holds
   the entries in the order recorded. Methods: `void record(ReportedChange change)`,
   `boolean isEmpty()`, `List<ReportedChange> changes()` (unmodifiable view), and
   `Map<String, List<ReportedChange>> byHeading()` (insertion-ordered by the first entry of each
   heading). It is a plain collector: it does not know about brackets, replay or suspension.
4. **`SpanOutcome`** (`dom/SpanOutcome.java`): replace `enum Simple { KEEP, REMOVE }` with:
   - `enum Keep implements SpanOutcome, HairpinOutcome { INSTANCE }`;
   - `record Remove(String reasonKey) implements SpanOutcome, HairpinOutcome` — `reasonKey` is
     the `Strings` key of the reason the span's rule gives;
   - `sealed interface SpanOutcome permits Keep, Remove`;
   - `sealed interface HairpinOutcome permits Keep, Remove, Reshape`, with `Reshape` unchanged.
   Rewrite the type's Javadoc to the new shape: keep has one value and carries no data; remove
   carries the reason the report shows.
5. **`Span`** (`dom/Span.java`): write the contract, then the code, for each of these:
   - `protected static SpanOutcome removeWhen(boolean invalidated, String reasonKey)` replaces
     `removeWhen(boolean)`.
   - `outcomeFor(ElementChange)` returns `SpanOutcome`. The default rule answers
     `removeWhen(<endpoint deleted>, Strings.DIALOG_REPORT_REASON_ENDPOINT_DELETED)`.
   - `protected abstract String reportHeadingKey()` — the heading key for a removed span of this
     kind. Phase 3 implements it in each subclass.
   - `public ElementLocationRange location()` — the range from the anchor to the end, resolved
     through the anchor's parent line and `Song.indexOfLine`. It works for a span that is not in
     a registry, as long as its endpoints are elements of a line in the song (the paste path
     reports fragment spans after their elements are inserted).
   - `public ReportedChange reportedAs(String reasonKey)` — the entry for this span:
     `new ReportedChange(reportHeadingKey(), Strings.get(reasonKey), location())`.
   - `public void removeReported(Line line, String reasonKey)` — records `reportedAs(reasonKey)`
     through `line.getSong().report(...)`, then calls `removeFrom(line)`. Its contract states
     that every removal the user did not directly request goes through this method, and that
     `removeFrom` is for a removal the user asked for.
6. **`ModificationRule`** (`dom/ModificationRule.java`): write the contract, then the
   package-private interface with one method,
   `void apply(Song song, List<Mutation> batch, ChangeReport report)`. The contract states:
   it runs once for each edit, when the outermost modification bracket closes, while the
   bracket is still open, so each removal it records lands in the batch that is delivered; every
   change in `batch` has landed; it records each removal it makes into `report`; it does not
   read what another rule removed.
7. **`ModificationRules`** (`dom/ModificationRules.java`): write the contract, then the
   package-private final class. It holds the ordered list
   `List.of(new TieRule(), new EndingSectionRule(), new TupletBeatRule())` (Phase 2 creates the
   three classes in `songscribe.dom`). One method: `void apply(Song song, List<Mutation> batch,
   ChangeReport report)`, which calls each rule in order. It has no public `register`: every rule
   is a domain rule. State in its Javadoc that key-invariant maintenance and the accidental chain
   walk are not rules here, because they run after each mutation and not once for each edit.
8. **`ModificationSession`** (`dom/ModificationSession.java`), the bracket close:
   - Add a field `@Nullable ChangeReport report`, created when the outermost bracket opens
     (depth 0 → 1) and dropped in `deliverBatch`.
   - Add `void report(ReportedChange change)`: records nothing while replaying or while
     tracking is suspended; throws `IllegalStateException` when no bracket is open, as
     `applyChange` does; otherwise records into the open report. Write its contract first.
   - Add a field `private final ModificationRules rules = new ModificationRules();`.
   - In `endModification`, replace the `song.applyTieRule(List.copyOf(batch))` call with
     `rules.apply(song, List.copyOf(batch), report)`, under the same condition, renamed from
     `owesTieRule` to `owesRules`. Rewrite the Javadoc of `endModification`, `closeBracket`,
     `inBracket` and `withReplay` so they name the rules, not the tie rule.
   - In `deliverBatch`, after posting `SongDidChangeNotification`, post
     `new ChangesWereReportedNotification(report)` when the report is not empty. Leave the
     `tupletsRemovedNoticeCause` post in place; Phase 5 deletes it.
9. **`Song`** (`dom/Song.java`): add `public void report(ReportedChange change)`, delegating to
   `ModificationSession.report`, with a contract that points at it.
10. **`ChangesWereReportedNotification`**
    (`message/notification/ChangesWereReportedNotification.java`): a `Message` subclass holding
    the `ChangeReport`, with `getReport()`. Its Javadoc states: posted once for each edit, after
    `SongDidChangeNotification`, only when the report is not empty; never during replay or while
    tracking is suspended.

---

## ✅ Phase 2: Close-Time Rules and the Ending Section Rule

**Status:** Complete  <br>
**BlockedBy:** 1  <br>
**Files:** src/main/java/songscribe/dom/TieRule.java, src/main/java/songscribe/dom/EndingSectionRule.java, src/main/java/songscribe/dom/TupletBeatRule.java, src/main/java/songscribe/dom/Ending.java, src/main/java/songscribe/dom/Song.java, src/main/java/songscribe/dom/Line.java  <br>
**Recommended model/effort:** Opus, high — three domain rules, a new invariant on `Ending`, and the one enumeration of beat-defining mutations

Read first: `docs/span-invalidation.md`, `docs/mutations.md`, `.claude/guides/contracts.md`.

These types exist from Phase 1 in `songscribe.dom`: `ModificationRule` with
`void apply(Song song, List<Mutation> batch, ChangeReport report)`; `ChangeReport` with
`record(ReportedChange)`; `ReportedChange(String headingKey, String description,
@Nullable ElementLocationRange where)`; `ElementLocationRange(ElementLocation start,
ElementLocation end)` with `single(ElementLocation)`; `Span.removeReported(Line, String
reasonKey)`; `Span.location()`. The string constants are in the plan's "String keys" section.

### Tasks

1. **The section rule on `Ending`.** Write the contract, then add to `Ending` a package-private
   static method `closesOpenSection(Line line, int splitIndex)`: true when
   `line.getSong().repeatSectionStart(new ElementLocation(line.index(), splitIndex)) != null`.
   This is the one statement of the rule; `spanning` and `EndingSectionRule` both call it. Add
   an `@invariant` line to the `Ending` class Javadoc: the split closes an open repeat section
   (`Song.repeatSectionStart` at the split is not null), which reads across lines.
2. **`Ending.spanning` returns a reason.** Write the contract, then change
   `public static @Nullable Ending spanning(Line, ElementRange)` to return a new sealed type
   nested in `Ending`: `sealed interface Spanning permits Spanned, Refused`,
   `record Spanned(Ending ending)`, `record Refused(String reasonKey)`. The checks, in order:
   `isWellFormed` fails, or the range overlaps the range of an ending already on the line
   (`ElementRange.overlaps`) → `Refused(DIALOG_REPORT_REASON_ENDING_NOT_VALID)`;
   `closesOpenSection` fails at the split → `Refused(DIALOG_REPORT_REASON_ENDING_NO_OPEN_SECTION)`.
   `isWellFormed` does not call `closesOpenSection`: `outcomeFor` judges a one-line projection,
   and the section rule reads the live song across lines. State this split in the Javadoc of
   `outcomeFor` and of `spanning`.
3. **The split is a right repeat, and there is no separation rule.** Write the contract first,
   in the `Ending` class Javadoc.
   - An ending's split is always a `REPEAT_RIGHT`. `splitIndexOf` still finds the first bar
     inside the range whose type `closesRepeat()`, so a `REPEAT_LEFT_RIGHT` there is found and
     makes the ending ill formed; it is not skipped. A split whose type is not `REPEAT_RIGHT`
     has its own reason, `DIALOG_REPORT_REASON_ENDING_SPLIT_NOT_RIGHT_REPEAT`, in every place
     that reports a reason: `spanning` answers `Refused` with it, and `outcomeFor` answers
     `Remove` with it. Every other failure of `isWellFormed` keeps
     `DIALOG_REPORT_REASON_ENDING_NOT_VALID`. So `isWellFormed` answers which rule failed
     (a nullable reason key, `null` when well formed) rather than a `boolean`; rename it to say
     so. The editor, the load and the funnel all reach it, so an edit that turns the split into
     a `:|:` removes the ending and reports the split reason. Phase 3 writes
     `Ending.outcomeFor`: it answers `Remove` with the reason key this method returns.
   - Change `isValidEnd(ElementType splitType, ElementType endType)` to
     `isValidEnd(ElementType endType)`: a note or a rest, or a type that `isEndingTerminal()`.
     Delete its `REPEAT_LEFT_RIGHT` branch and rewrite its Javadoc. Update the class invariant
     that names it.
   - No bar follows the end bar. The end bar is the element `getEndBar` answers: the end when
     it is a bar, else the element right after an end note when that is a bar. Extract that
     rule into a private static method over an element list and an end index, so that
     `getEndBar` and the well-formedness check share it. In the well-formedness check, when
     there is an end bar, the first element after it on the same line that is not
     `isNonContentElement()` must not be a bar (`isBar()`); when it is, the check answers
     `DIALOG_REPORT_REASON_ENDING_BAR_AFTER_END`. The check stops at the end of the line: the
     first element of the next line is not read. Add an `@invariant` line for this rule to the
     `Ending` class Javadoc.
   - Two endings may sit back to back. With a right-repeat split, the walk back from a later
     ending's split crosses the earlier ending and reaches the earlier split, which closes the
     section, so the section rule refuses the later ending. Delete `Ending.isSeparated` and the
     `separatedFromEvery` check in `spanning`. Replace the
   last `@invariant` of the `Ending` class Javadoc with: the ranges of two endings on a line do
   not overlap. Rewrite the first paragraph of the class Javadoc, which names the separation
   rule, to say what is true now. The overlap refusal in `spanning` is for a user selection
   and a file; no edit can make two endings overlap, so the funnel has no overlap rule.
4. **`TieRule`** (`dom/TieRule.java`): implement `ModificationRule`. Move the body of
   `Song.applyTieRule(List<Mutation>)` into `apply`, and delete `Song.applyTieRule`. Change
   `Line.applyTieRule()` to `Line.applyTieRule(ChangeReport report)`: before it calls
   `removeTie(anchor)`, it records
   `new ReportedChange(DIALOG_REPORT_TIES_REMOVED, Strings.get(DIALOG_REPORT_REASON_TIE_NOT_HELD), where)`,
   where `where` runs from the anchor's location to the location of the element
   `Tie.endAfter(elements, i, next)` returns, or is `ElementLocationRange.single(anchor)` when
   that is `null`. `Song.touchedLine` and `Song.indexOfLine` may become package-private for
   `TieRule`. Move the Javadoc of `Song.applyTieRule` onto `TieRule`, rewritten to present tense.
5. **`EndingSectionRule`** (`dom/EndingSectionRule.java`): write the contract, then implement
   `ModificationRule`. For each line of the song, for a copy of
   `line.spanRegistry().findEndings()`, call
   `ending.removeReported(line, DIALOG_REPORT_REASON_ENDING_NO_OPEN_SECTION)` when
   `!Ending.closesOpenSection(line, ending.getSplitIndex())`. It judges every ending in the song,
   not only the lines the batch touched: an edit on one line can close the section of an ending
   any number of lines later, and a line deletion or insertion goes through no funnel.
6. **`Song.beatMoveOf`** (`dom/Song.java`): write the contract, then add
   `@Nullable ElementLocation beatMoveOf(Mutation mutation)`, modeled on `Song.keyMoveOf`. It is
   the one enumeration of the mutations that can redefine the beat. It answers the location in
   the current song from which the beat is redefined, or `null`:
   - `MetadataChange` with field `TEMPO` whose old and new values fail `Tempo.haveSameBeat` →
     element 0 of `firstNonEmptyLineIndex()`, or `null` when there is none;
   - `ElementModification` whose fields contain `TEMPO_CHANGE` or `BEAT_CHANGE` → the current
     location of `afterElement()`, or `null` when it is no longer in the song;
   - `ElementInsertion` of an element that defines a beat → that element's current location;
   - `ElementReplacement` where the old or the new element defines a beat → the new element's
     current location;
   - `ElementDeletion` where a deleted element defines a beat → the line's current index and
     `min(range().begin(), line.elementCount() - 1)`, or `null` when the line is gone or empty;
   - `LineInsertion` whose line holds a beat-defining element → element 0 of that line;
   - `LineDeletion` whose deleted line held one → element 0 of the line now at its index, or
     `null` when there is none.
   An element defines a beat when it carries a tempo-change or a beat-change attachment. Add
   that test as one private predicate, as `changesKey` is for keys.
7. **`TupletBeatRule`** (`dom/TupletBeatRule.java`): write the contract, then implement
   `ModificationRule`. Take the earliest non-null `song.beatMoveOf(m)` over the batch (line
   index first, then element index). When there is one, move the body of
   `ModificationSession.removeTupletsInvalidatedFrom` and `registryTupletAnchoredAt` here: the
   start backs up to the anchor of a tuplet that encloses it, then
   `TupletValidator.validateFrom(song, start, STRICT, ...)`. For each `Invalid` verdict, call
   `tuplet.removeReported(line, reason.messageKey())`. `TupletValidator.Reason.messageKey()` is
   added in Phase 6; use it here by that name. Leave the old methods in `ModificationSession`;
   Phase 5 deletes them.

---

## ✅ Phase 3: Funnel Removals Report

**Status:** Complete  <br>
**BlockedBy:** 1, 2  <br>
**Files:** src/main/java/songscribe/dom/Beam.java, src/main/java/songscribe/dom/Tuplet.java, src/main/java/songscribe/dom/Trill.java, src/main/java/songscribe/dom/Hairpin.java, src/main/java/songscribe/dom/Ending.java, src/main/java/songscribe/dom/LineSpans.java  <br>
**Recommended model/effort:** Sonnet, medium — applies the outcome and reporting shape Phase 1 defined, with the reason keys this phase names

Phase 1 changed `SpanOutcome` to `Keep.INSTANCE`, `Remove(String reasonKey)` and `Reshape`, and
`Span.removeWhen(boolean, String reasonKey)`. It added `Span.removeReported(Line, String
reasonKey)` and `protected abstract String reportHeadingKey()`. The string constants are in the
plan's "String keys" section.

### Tasks

1. Implement `reportHeadingKey()` in each span subclass: `Beam` → `DIALOG_REPORT_BEAMS_REMOVED`,
   `Tuplet` → `DIALOG_REPORT_TUPLETS_REMOVED`, `Trill` → `DIALOG_REPORT_TRILLS_REMOVED`,
   `Hairpin` → `DIALOG_REPORT_HAIRPINS_REMOVED`, `Ending` → `DIALOG_REPORT_ENDINGS_REMOVED`.
2. Give each `outcomeFor` a reason. `Beam`: endpoint deleted → `DIALOG_REPORT_REASON_ENDPOINT_DELETED`;
   `replacementBreaksInvariant` → `DIALOG_REPORT_REASON_BEAM_NOT_BEAMABLE`. `Tuplet` →
   `DIALOG_REPORT_REASON_TUPLET_INTERIOR_CHANGED`. `Ending`: an endpoint absent after the change →
   `DIALOG_REPORT_REASON_ENDPOINT_DELETED`; otherwise the reason key that Phase 2's
   well-formedness method in `Ending` returns for the projected range. `Hairpin.hairpinOutcomeFor` and its helpers
   `outcomeForDeletion` and `outcomeForRepointing`: a removal on deletion →
   `DIALOG_REPORT_REASON_ENDPOINT_DELETED`; a removal on repointing →
   `DIALOG_REPORT_REASON_HAIRPIN_ENDPOINT_INVALID`. Replace every `SpanOutcome.Simple.KEEP` with
   `SpanOutcome.Keep.INSTANCE`, and update each return type from `SpanOutcome.Simple` to
   `SpanOutcome`. Update the `@return` lines to name `Keep` and `Remove`.
3. `LineSpans.decide`: switch on the new outcome types. `SweepDecision.removed()` becomes a list
   of span-and-reason pairs (a private record `Removal(Span<?> span, String reasonKey)`).
   Endings have no separation rule: Phase 2 deleted `Ending.isSeparated`. Delete
   `unseparatedEndings`, the `keptEndings` list in `decide`, and the ending paragraph of the
   Javadoc of `decide` and `sweep`. If `projectedRange` or `SpanAt` then has no caller, delete it.
4. `LineSpans.sweep`: remove each span with `span.removeReported(line, reasonKey)`.
5. `LineSpans.applySpanRun`: the first hairpin of a run is reshaped, not lost, so it keeps
   `removeFrom`. Every other hairpin in a run of more than one is absorbed: call
   `removeReported(line, DIALOG_REPORT_REASON_MERGED)` on it.
6. `LineSpans.mergeOverlapping`: each subsumed span → `removeReported(line, DIALOG_REPORT_REASON_MERGED)`.
7. `LineSpans.removeOverlappingSameKind`: each span → `removeReported(line, DIALOG_REPORT_REASON_REPLACED)`.
8. `LineSpans.removeOverlapping(Class, ElementRange)` stays on `removeFrom`: its callers remove
   the spans the user asked to remove. Before you leave it, run
   `jet_brains_find_referencing_symbols` on it and confirm that every production caller is a user
   command that removes spans. Report any caller that is not.

---

## ✅ Phase 4: Delete Ending Prompts and Compensation

**Status:** Complete  <br>
**BlockedBy:** 3  <br>
**Files:** src/main/java/songscribe/ui/EndingConfirms.java, src/main/java/songscribe/ui/component/ScoreViewController.java, src/main/java/songscribe/ui/component/score/PreviewElementInserter.java, src/main/java/songscribe/ui/selection/SelectionActionApplier.java, src/main/java/songscribe/dom/ElementChange.java, src/main/java/songscribe/dom/Ending.java, src/main/java/songscribe/dom/Span.java, src/main/java/songscribe/dom/LineSpans.java  <br>
**Recommended model/effort:** Sonnet, medium — deletions and call-site simplification; no new design

No edit asks the user to confirm the loss of an ending before the edit. Every automatic removal
is reported after the edit through `ChangesWereReportedNotification`, and undo reverses it.
An edit that breaks an ending never adds a second bar change to keep it.

### Tasks

1. Delete `src/main/java/songscribe/ui/EndingConfirms.java`.
2. `ScoreViewController`: delete `confirmEndingInvalidatedByDeletion` and inline `true` at its
   callers, removing the branches it guarded. In `tryInsertFragment`, delete the
   `deletionAlreadyConfirmed` and `insertionInvalidatesEnding` logic and the
   `FragmentInsertOutcome.CANCELLED` return it produces. If `CANCELLED` then has no producer,
   delete the constant and its handling.
3. `PreviewElementInserter`: in `accept`, delete the `removesConfirmableSpan` confirm. In
   `modifyExistingElement`, replace `EndingConfirms.confirmReplacement(...)` with the replacement
   it was given, and delete the cancel branch.
4. `SelectionActionApplier.decideReplacement`: return
   `new ElementChange.Replacement(line, newElements)` directly. Rename the method to say what it
   now does (for example `replacementFor`), with `jet_brains_rename`, and fix its Javadoc.
5. `ElementChange`: delete `removesConfirmableSpan`, `Replacement.endingEffects`, and any helper
   only they use. Rewrite the class Javadoc: a change has one reader, the span sweep.
6. `Ending`: delete `EndingEffect` and its nested types, `effectOf`, `compensationFor`,
   `matchingEndType`, `matchingSplitType`, and `requiresInvalidationConfirm`.
7. `Span`: delete `requiresInvalidationConfirm`.
8. `LineSpans`: make `decide` private. Rewrite the Javadoc of `decide` and `sweep` so they
   state why the whole decision is made before any of it is done (walking a list while
   removing from it, and one span's removal changing what a later span is judged against), and
   say nothing about prompts.
9. Run `jet_brains_find_referencing_symbols` on each deleted symbol before you delete it, and
   report any production reference outside the files in this phase's `Files:` list.

---

## ✅ Phase 5: Delete the Beat Chokepoint and Report Paste Drops

**Status:** Complete  <br>
**BlockedBy:** 2, 4  <br>
**Files:** src/main/java/songscribe/dom/ModificationSession.java, src/main/java/songscribe/dom/Song.java, src/main/java/songscribe/dom/TempoChangeAttachment.java, src/main/java/songscribe/dom/BeatChangeAttachment.java, src/main/java/songscribe/dom/AttachmentRemoval.java, src/main/java/songscribe/ui/dialog/BeatChangeController.java, src/main/java/songscribe/ui/dialog/TempoChangeController.java, src/main/java/songscribe/message/notification/TupletsWereRemovedNotification.java, src/main/java/songscribe/ui/component/ScoreViewController.java, src/main/java/songscribe/ui/clipboard/PasteSpanReconciliation.java, src/test/java/songscribe/UnitTest.java  <br>
**Recommended model/effort:** Sonnet, medium — deletions and direct writes; the paste reporting uses the entry shape Phase 1 defined

`TupletBeatRule` (Phase 2, `dom/TupletBeatRule.java`) finds every beat-defining mutation in
the batch when the bracket closes, so no writer calls a chokepoint. No production caller reads
the `boolean` that `withBeatDefiningEdit` returns.

### Tasks

1. `ModificationSession`: delete `withBeatDefiningEdit`, `withBeatDefiningEditOn`,
   `noteTupletsWereRemoved`, `removeTupletsInvalidatedFrom`, `registryTupletAnchoredAt`, and
   the fields `beatDefiningEditDepth`, `beatEditRemovedTuplets` and
   `tupletsRemovedNoticeCause`, with the post of `TupletsWereRemovedNotification` in
   `deliverBatch`. Remove the "Beat-defining edit" item from the class Javadoc list.
2. `Song`: delete `withBeatDefiningEdit`, `withBeatDefiningEditOn` and `noteTupletsWereRemoved`.
   In `setTempo` and `tempoDidChange`, assign the tempo directly inside the `applyChange`
   mutator. `tempoDidChange` keeps its `Tempo.haveSameBeat` test only if something still reads
   it; `TupletBeatRule` makes the same test through `Song.beatMoveOf`. Rewrite the Javadoc of
   `setTempo`.
3. `TempoChangeAttachment.setTempo`, `BeatChangeAttachment.setBeatChange`,
   `AttachmentRemoval.removeTempoChange`, `AttachmentRemoval.removeBeatChange`,
   `BeatChangeController.commit`, `TempoChangeController.commit`: replace
   `Song.withBeatDefiningEditOn(owner, edit)` with `edit` itself. Rewrite each Javadoc that
   names the chokepoint, and the `createCopy` Javadoc in both attachment classes.
4. Delete `TupletsWereRemovedNotification.java` and the `tupletsWereRemoved` `@Handler` in
   `ScoreViewController`.
5. `PasteSpanReconciliation`: add the record component `List<Span<?>> fragmentSpansDropped` —
   the fragment spans the straddle rule drops in `reconcile`. `dropTupletsRejectedByTarget`
   carries it through unchanged. Update the class and method Javadoc: the caller reports every
   span in `targetSpansToRemove`, `fragmentSpansDropped` and `tupletsRejectedByTarget`.
6. `ScoreViewController.tryInsertFragment`: remove each span in `targetSpansToRemove` with
   `span.removeReported(line, DIALOG_REPORT_REASON_PASTE_STRADDLED)`. After `line.insertRun`,
   record `span.reportedAs(DIALOG_REPORT_REASON_PASTE_FRAGMENT_DROPPED)` for each span in
   `fragmentSpansDropped`, and `tuplet.reportedAs(DIALOG_REPORT_REASON_PASTE_TUPLET_REJECTED)`
   for each tuplet in `tupletsRejectedByTarget`, each through `line.getSong().report(...)`.
   Delete the `noteTupletsWereRemoved` call. Rewrite the comments above these lines in present
   tense.
7. `UnitTest.minimalSongMock`: delete the `withBeatDefiningEdit` stub.

---

## ✅ Phase 6: Load Passes Report

**Status:** Complete  <br>
**BlockedBy:** 1, 2  <br>
**Files:** src/main/java/songscribe/io/LoadMigrations.java, src/main/java/songscribe/io/LoadPasses.java, src/main/java/songscribe/dom/TupletLoadPass.java, src/main/java/songscribe/dom/EndingLoadPass.java, src/main/java/songscribe/dom/BreathMarkLoadPass.java, src/main/java/songscribe/dom/TupletValidator.java, src/main/java/songscribe/io/SongLoadResult.java, src/main/java/songscribe/io/SongLoader.java, src/main/java/songscribe/io/musicxml/SongMapper.java, src/main/java/songscribe/io/SongIO.java, src/main/java/songscribe/ui/component/ScoreView.java  <br>
**Recommended model/effort:** Sonnet, medium — each pass records into the collector Phase 1 defined, with the entries this phase names

Read first: `docs/musicxml-object-model.md`. Phase 1 created `ChangeReport`, `ReportedChange`
and `ElementLocationRange` in `songscribe.dom`. Phase 2 changed `Ending.spanning` to return
`Ending.Spanning` (`Spanned(Ending)` or `Refused(String reasonKey)`). The load runs with
tracking suspended, so the passes record into the report they are given, not through
`Song.report`.

### Tasks

1. `TupletValidator.Reason`: add `String messageKey()`, a switch over the nine constants to
   `DIALOG_REPORT_REASON_BAD_RATIO` … `DIALOG_REPORT_REASON_TOO_FEW_NOTES`, moved from
   `MigrationWindow.reasonText`. An exhaustive switch makes a new constant fail to compile.
2. `TupletLoadPass.run(Song, List<PendingTuplet>, ChangeReport)`: delete `Change` and `Report`.
   A dropped tuplet records `(DIALOG_REPORT_TUPLETS_REMOVED, Strings.get(reason.messageKey()),
   <anchor to end>)`. A tuplet given a derived ratio records `(DIALOG_REPORT_TUPLETS_UPDATED,
   Strings.get(DIALOG_REPORT_UPDATED_DETAIL, grade.number(), normalNotes), <anchor to end>)`.
   Keep the `LOG.info` counts.
3. `EndingLoadPass.run(Song, List<PendingEnding>, ChangeReport)`: delete `Removed` and
   `Report`. Switch on `Ending.spanning`: `Spanned` adds the ending as today; `Refused` records
   `(DIALOG_REPORT_ENDINGS_REMOVED, Strings.get(refused.reasonKey()), <anchor to end>)` and
   logs as today.
4. `BreathMarkLoadPass.run(Song, ChangeReport)`: delete `Removed` and `Report`. Each removed
   breath mark records `(DIALOG_REPORT_BREATH_MARKS_REMOVED,
   Strings.get(DIALOG_REPORT_REASON_BREATH_MARK_HOST), single(<its location>))`.
5. Replace `io/LoadMigrations.java` with `io/LoadPasses.java`: a final class with
   `public static ChangeReport run(Song song, List<PendingTuplet> pendingTuplets,
   List<PendingEnding> pendingEndings, boolean accidentalsConverted)`. It creates the report,
   runs the passes in the order and for the reasons the Javadoc of `LoadMigrations.run` states
   (move that text), and records `(DIALOG_REPORT_ACCIDENTALS_CONVERTED,
   Strings.get(DIALOG_REPORT_ACCIDENTALS_DETAIL), null)` when `accidentalsConverted`. Delete
   `LoadMigrations.java`.
6. `SongLoadResult.Success`: replace the component `LoadMigrations migrations` with
   `ChangeReport report`. Update `SongLoader.load`, `SongMapper.map`, and the
   `SongIO.DocumentReader.pendingTuplets` Javadoc link to `LoadPasses.run`.
7. `ScoreView.openFile`: read `success.report()`. When it is not empty, set the song modified
   and call `ReportWindow.show(owner, DIALOG_REPORT_LOAD_TITLE, DIALOG_REPORT_LOAD_MESSAGE,
   report)`. Phase 7 creates `ReportWindow.show(@Nullable Window, String titleKey, String
   messageKey, ChangeReport)` by renaming `MigrationWindow`.

---

## ✅ Phase 7: Report Window and Strings

**Status:** Complete  <br>
**BlockedBy:** 3, 4, 5, 6  <br>
**Files:** src/main/java/songscribe/ui/dialog/MigrationWindow.java, src/main/java/songscribe/ui/dialog/ReportWindow.java, src/main/java/songscribe/ui/component/ScoreViewController.java, src/main/java/songscribe/ui/MusicEditOperations.java, src/main/resources/songscribe/strings.properties, .claude/guides/dialogs.md, .claude/guides/strings.md, src/main/java/  <br>
**Recommended model/effort:** Sonnet, medium — a rename, a generic rewrite of one tree builder whose shape is stated here, and string entries this plan lists

Read first: `.claude/guides/strings.md`, `.claude/guides/dialogs.md`,
`.claude/guides/messages.md`. The `src/main/java/` entry in `Files:` covers the bulk rename of
`DIALOG_MIGRATION_*` constant references.

### Tasks

1. Rename `MigrationWindow` to `ReportDialog` with `jet_brains_rename`
   (`rename_in_comments: false`). Then check the Markdown diff as `.claude/rules/serena.md`
   says, and restore any plan file under `plans/` that records history.
2. `strings.properties`: rename every `dialog.migration.*` key to `dialog.report.*` as the
   plan's "String keys" section lists it (`dialog.migration.title` →
   `dialog.report.load.title`, `dialog.migration.message` → `dialog.report.load.message`,
   `dialog.migration.accidentals` → `dialog.report.accidentals.detail`). Then rename the
   constant references with `mcp__serena__replace_in_files`, needle `DIALOG_MIGRATION_`, repl
   `DIALOG_REPORT_`, dry run first; fix the three renamed title, message and accidentals
   constants by hand. Add every other key from the "String keys" section. Delete
   `dialog.migration.change`, `dialog.migration.breath.mark.change`, `alert.title.tuplets.removed`,
   `alert.tuplets.removed.beat`, `alert.tuplets.removed.paste`, and every `confirm.ending.*` key.
   Delete `confirm.title.first.second.ending` only if no Java reference remains. Keep each group
   sorted.
3. `ReportDialog`: `public static void show(@Nullable Window owner, String titleKey, String
   messageKey, ChangeReport report)`. `buildTree` becomes one loop over `report.byHeading()`:
   each group is a node titled `Strings.get(headingKey, entries.size())`, and each entry is a
   leaf. A leaf with a location reads `Strings.get(DIALOG_REPORT_CHANGE, location, description)`;
   a leaf with no location reads the description. Delete `describe` (all overloads), `detail`,
   `reasonText` and `changesOfKind`. Add one private method that formats an
   `ElementLocationRange` one-based: one element → `DIALOG_REPORT_LOCATION_ELEMENT`, one line →
   `DIALOG_REPORT_LOCATION_ELEMENTS`, two lines → `DIALOG_REPORT_LOCATION_LINES`. This is the
   only place that adds one to an index. Rewrite the class Javadoc: the window shows a report
   from a load or from an edit, and knows nothing about the kinds of change.
4. `ScoreViewController`: add a `@Handler` for `ChangesWereReportedNotification` that calls
   `ReportWindow.show(owner, DIALOG_REPORT_EDIT_TITLE, DIALOG_REPORT_EDIT_MESSAGE,
   message.getReport())`, with the owner window the deleted `tupletsWereRemoved` handler used.
5. `MusicEditOperations.canMakeFirstSecondEnding`: delete "Stage 1" (the
   `song.repeatSectionStart` check); `Ending.spanning` now makes it. Switch on
   `Ending.Spanning`: `Spanned` → `new EndingValidationResult.Valid(line, spanned.ending())`;
   `Refused` → `EndingValidationResult.Invalid.INSTANCE`. Rewrite the comments in present tense.
6. `.claude/guides/dialogs.md` and `.claude/guides/strings.md`: replace the names
   `MigrationWindow` and `EndingConfirms` with what is true now (`ReportDialog`; no ending
   confirms exist).

---

## ✅ Phase 8: Design Docs

**Status:** Complete  <br>
**BlockedBy:** 7  <br>
**Files:** docs/span-invalidation.md, docs/mutations.md, docs/change-report.md, CLAUDE.md  <br>
**Recommended model/effort:** Opus, high — subsystem prose that states the shape: timing, ownership and the undo argument

Read first: `~/.claude/guides/documents.md`, the "Design Docs" paragraph of `CLAUDE.md`.
Docs state concepts, not class or method names, and describe the code as it is now.

### Tasks

1. `docs/span-invalidation.md`: remove every mention of confirmation prompts and of
   compensation ("One funnel", "Replacement re-points" last paragraph). State that a removal
   carries the reason the span's rule gives, and that every removal the user did not directly
   request is reported. Add a section on the ending's section rule: it reads the song across
   lines, so it is judged once the edit is over and not in the funnel; the undo argument is the
   tie rule's (the ending's endpoints still exist in the final state). In "What stays with the
   registry", state that an ending's validity reads across lines while the ending still sits in
   one line's registry. Replace the paragraph "No two endings are back to back" with the rule
   that is true now: two endings on a line do not overlap, and there is no separation rule
   between them.
2. `docs/mutations.md`: describe the rules that run when the outermost bracket closes, their
   run conditions, and why their removals may follow the changes that caused them.
3. `docs/change-report.md` (new): the change report — what reports into it (the funnel, merges,
   displacements, the rules at close, paste, the load passes), that the reporter builds the text
   because it holds the domain knowledge, how an edit's report and a load's report reach the one
   window, and that the report is the one way an automatic change reaches the user. State that
   a stranded key change is not reported: it carries no information the document does not
   already hold.
4. `CLAUDE.md`, "Required Reading by Task": add an entry for the change report and the
   modification rules, pointing at `docs/change-report.md`.

---

## ✅ Phase 9: Tests

**Status:** Complete  <br>
**BlockedBy:** 7  <br>
**Files:** src/test/java/songscribe/  <br>
**Recommended model/effort:** Sonnet, medium — test wiring for the approved list below, against contracts the earlier phases wrote

Read first: `.claude/guides/testing-common.md`, `.claude/guides/testing-unit.md`. Before
writing each test method, check whether it will sit beside a same-shape sibling; if so, both
are rows in one parameterized table.

The list below is the approved list. Write no test outside it.

| Test | Kind |
|---|---|
| `EndingSectionRuleTest`: an edit that leaves an ending's split with no open section removes the ending and reports it — rows: delete the `\|:`, insert a `:\|`, insert a `\|\|`, replace the `\|:` with a single barline, delete the line that holds the `\|:`; a `\|:` on an earlier line keeps the ending | Invariant spanning several calls |
| `EndingSectionRuleTest`: undo of such an edit restores the ending, and redo removes it again | Invariant spanning several calls |
| `EndingTest` spanning table: a split with no open section is `Refused` with `DIALOG_REPORT_REASON_ENDING_NO_OPEN_SECTION`; an opener on an earlier line is accepted; a `:\|:` split is `Refused` with `DIALOG_REPORT_REASON_ENDING_SPLIT_NOT_RIGHT_REPEAT`; a bar after the end bar, directly or past a grace note, is `Refused` with `DIALOG_REPORT_REASON_ENDING_BAR_AFTER_END`; an end bar that is the last element of its line is accepted | Real algorithm |
| `TupletBeatRuleTest`: each beat-defining mutation removes the tuplets that `STRICT` rejects from that point on — rows: song tempo with a new beat, a tempo change set, a beat change set, either one removed, deletion of an element that carries one, deletion of a line that holds one; a tempo edit that keeps the beat removes none | Invariant spanning several calls |
| `ChangeReportDeliveryTest`: one edit whose funnel, merge, displacement and close-time rules each remove a span delivers one `ChangesWereReportedNotification` that holds every entry; undo, redo and suspended tracking deliver none | Invariant spanning several calls |
| `LoadPassesTest` (rename of `LoadPassesTest`, keep its accidental cases): the report holds one entry for each dropped tuplet, derived tuplet, dropped ending with its refusal reason, removed breath mark, and the accidental conversion | Invariant spanning several calls |

### Tasks

1. Delete from `EndingTest`: `EndingEffectsCase`, `testEndingEffectsMatchesTheEditRule`,
   `applyEveryCompensation`, and every case that reads `endingEffects`. Delete from
   `ElementChangeTest` every case that reads `removesConfirmableSpan`. Delete every test and
   fixture of the separation rule between endings: `ElementChangeTest.backToBackEndingsFixture`
   and the cases that use it, and every `EndingTest` or `LineSpansTest` case that expects two
   back-to-back endings to be refused or the later one removed. Keep the cases that expect an
   overlapping ending to be refused.
   Run `jet_brains_find_referencing_symbols` on `ElementType.REPEAT_LEFT_RIGHT` and on
   `Ending.isValidEnd`, restricted to `src/test/java/`. A fixture that builds an ending split by a
   `REPEAT_LEFT_RIGHT` changes the split to `REPEAT_RIGHT` with a matching end. A case that
   expects such an ending to be accepted now expects it to be refused. Delete each
   `isValidEnd` case that passes a split type, and keep its end-type cases with the one-argument
   form.
2. Update every test caller of `Ending.spanning` to the `Spanning` result:
   `EndingTest`, `ElementChangeTest`, `LineSpansTest`, `EndingBracketGeometryTest`,
   `VoltaAlignmentFixtureTest`, `MusicXmlOffsetRoundTripTest`. Update every test that reads
   `SpanOutcome.Simple`, `TupletLoadPass.Report`/`Change`, `EndingLoadPass.Report`/`Removed`,
   `BreathMarkLoadPass.Report`/`Removed` or `LoadMigrations` (`TupletLoadPassTest`,
   `BreathMarkLoadPassTest`, `LoadPassesTest`) to the `ChangeReport` shape.
3. Rename `LoadPassesTest` to `LoadPassesTest` with `jet_brains_rename`.
4. Write the tests in the table.

---

## ✅ Phase 10: Compile and Test Gate

**Status:** Complete  <br>
**BlockedBy:** 1, 2, 3, 4, 5, 6, 7, 8, 9  <br>
**Files:** —  <br>
**Recommended model/effort:** Sonnet, low — runs the build and the named test classes and reports the result

### Tasks

1. Run `./scripts/compile.sh --test`. It must print SUCCESS. On FAILURE, fix each error at its
   cause and run it again.
2. Run `./scripts/test.sh EndingSectionRuleTest TupletBeatRuleTest ChangeReportDeliveryTest
   LoadPassesTest EndingTest ElementChangeTest LineSpansTest TupletLoadPassTest
   BreathMarkLoadPassTest TieRuleTest EndingBracketGeometryTest VoltaAlignmentFixtureTest
   MusicXmlOffsetRoundTripTest SongRepeatSectionTest MidiRepeatTest`. It must be green. Report
   each failure with its output.

---

## ✅ Phase 11: Manual UI Verification

**Status:** Complete  <br>
**BlockedBy:** 10  <br>
**Files:** —  <br>
**Recommended model/effort:** — (the user runs this phase)

### Tasks

1. Build `:| 1 2 :| 3 | 4 |: 5 6 :| 7 8 |]` with an ending over the first `:| … 3 |` and one over
   `6 :| 7 8`. Delete the `|:` after 4. The second ending is removed, one "Changes Made" window
   lists it with its reason and location, and undo restores it.
2. Put the `|:` on one line and the ending on a later line. Delete the line that holds the
   `|:`. The ending is removed and reported.
3. Delete a note that a beam, a trill and a hairpin end on in one edit. One window lists all
   three groups.
4. Change the song tempo's beat so that a tuplet no longer fits. The tuplet is removed and
   reported, and no other alert appears.
5. Paste into the middle of a beamed group. The straddled beam is reported.
6. Open a file that the load passes change. The "Validity Check" window shows the same tree as
   before, with each location one-based.
7. Change an ending's split from `:|` to `:|:`. No prompt appears; the ending is removed and
   reported.
8. Select a range whose first repeat inside it is a `:|:` and try to make a first-second
   ending. The editor refuses it.
