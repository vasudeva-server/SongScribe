# Linter and Line-Only Change Report (#833)

## Status Dashboard

| Phase | Description | Status | Sub-plan |
|-------|-------------|--------|----------|
| 1 | [User-Facing Strings](#-phase-1-user-facing-strings) | ✅ Complete | — |
| 2 | [Change Report Types and Contracts](#-phase-2-change-report-types-and-contracts) | ✅ Complete | — |
| 3 | [Migrate Change Recorders to Lines](#-phase-3-migrate-change-recorders-to-lines) | ✅ Complete | — |
| 4 | [Lint Package, Linter, Repeat and Fine Rules](#-phase-4-lint-package-linter-repeat-and-fine-rules) | ✅ Complete | — |
| 5 | [Lyric Span Extraction and Missing-Lyric Rule](#-phase-5-lyric-span-extraction-and-missing-lyric-rule) | ✅ Complete | — |
| 6 | [Score Drawing](#-phase-6-score-drawing) | ✅ Complete | — |
| 7 | [Lint Wiring, Save Gate, Play Action and Save As Fix](#-phase-7-lint-wiring-save-gate-play-action-and-save-as-fix) | ✅ Complete | — |
| 8 | [Messages Panel](#-phase-8-messages-panel) | ✅ Complete | — |
| 9 | [Design Docs](#-phase-9-design-docs) | ✅ Complete | — |
| 10 | [Rewrite Report Tests to Line Ranges](#-phase-10-rewrite-report-tests-to-line-ranges) | ✅ Complete | — |
| 11 | [New Tests](#-phase-11-new-tests) | ✅ Complete | — |
| 12 | [Compile and Test Gate](#-phase-12-compile-and-test-gate) | ✅ Complete | — |
| 13 | [Manual UI Verification](#-phase-13-manual-ui-verification) | ⏳ Pending | — |

## Terms used in every phase

- **Change entry** — one thing the song changed on its own during an edit or a load: a span or tie removed, a tuplet repaired. It describes something that is gone, so it names lines only, never elements.
- **Lint finding** — one thing wrong with the song as it is now. It names the offending element as a `StaffElement` object, never as an index.
- **Change report** — the change entries of one edit or one load. An event: it is replaced after every edit. An undo or a redo carries an empty change report, because nothing in a replay records.
- **Lint report** — every lint finding for the song as it is now. A state: the `Linter` singleton holds it and replaces it after every change to the song, at once.
- **Slot** — an element where a syllable can go: `StaffElement.canBearSyllable(predecessor)` returns `true`. It covers notes and grace notes, and excludes a note whose paired grace note in front of it takes its syllable.
- Every finding is an error. There is no severity type and no warning. What a finding blocks comes from the flags of its rule (`LintRule.Flag`): every rule carries `BLOCKS_SAVE`, and the balanced-repeats rule also carries `BLOCKS_PLAYBACK`. The score draws a finding in the color of theme key `SongScribe.score.overflow.color` (`FlatLafKey.SCORE_OVERFLOW_COLOR`). The Messages panel prefixes a lint heading with ❗ and a change heading with 💬, and draws neither in a color of its own.

Each phase that writes Java reads `.claude/rules/serena.md` first and follows it. Each phase that adds or changes a nontrivial contract reads `.claude/guides/contracts.md` first. Each phase that adds or changes a user-facing string reads `.claude/guides/strings.md` first.

## ✅ Phase 1: User-Facing Strings

**Status:** Complete  <br>
**BlockedBy:** —  <br>
**Files:** src/main/java/songscribe/Strings.java, src/main/resources/songscribe/strings.properties  <br>
**Recommended model/effort:** Opus, medium — user-facing wording and key naming under the strings guide

### Tasks

1. Read `.claude/guides/strings.md`. Read the existing `report.*` keys in `strings.properties` and their constants in `Strings.java`, in particular the heading keys (each a MessageFormat pattern that takes the entry count as `{0}`) and `REPORT_LOCATION_LINES`, `REPORT_LOCATION_ELEMENT`, `REPORT_LOCATION_ELEMENTS`, `REPORT_CHANGE`. Read `ACTION_PLAY_PLAY_TOOLTIP`.
2. Add these keys, following the guide's naming and the existing heading pattern. Later phases use the constant names given here:
   - `REPORT_LOCATION_LINE` — the location of a change entry on one line, taking the 1-based line number.
   - `REPORT_LOCATION_LINE_RANGE` — the location of a change entry across lines, taking the first and last 1-based line numbers.
   - `LINT_REPEATS_UNBALANCED` — heading for the balanced-repeats rule.
   - `LINT_LYRICS_MISSING` — heading for the missing-lyric rule.
   - `LINT_FINE_UNBALANCED` — heading for the Fine rule.
   - `LINT_REASON_REPEAT_NOT_CLOSED` — a left repeat (`|:` or `:|:`) whose section no right repeat closes.
   - `LINT_REASON_REPEAT_NOT_OPENED` — a right repeat that no left repeat opens and that the song start cannot open.
   - `LINT_REASON_REPEAT_NESTED` — a left repeat inside a section that is already open.
   - `LINT_REASON_LYRIC_MISSING` — a slot with no lyric in one verse, taking the 1-based verse number.
   - `LINT_REASON_FINE_NOT_ON_BAR` — a Fine on an element that is not a barline or a repeat.
   - `LINT_REASON_FINE_ON_LAST_BAR` — a Fine on the last element of the song.
   - `LINT_REASON_FINE_NOT_REFERENCED` — a Fine with no later annotation that refers to it.
   - `LINT_REASON_FINE_REFERENCE_WITHOUT_FINE` — an annotation that refers to a Fine with no Fine before it.
   - `ALERT_TITLE_SAVE_BLOCKED` and `ERROR_SAVE_BLOCKED_BY_LINT` — the alert shown when a save is refused because the song has lint errors. The message tells the user that the Messages panel lists them.
   - `ACTION_PLAY_BLOCKED_TOOLTIP` — the tooltip of the Play button while it is disabled because the song has errors that prevent playback with repeats. It tells the user that the Messages panel lists them.
3. Leave `REPORT_LOCATION_LINES` and `REPORT_LOCATION_ELEMENTS` in place. Phase 8 deletes each one that no code uses after it rewrites `ReportPanel`. `REPORT_LOCATION_ELEMENT` stays: lint rows use it.

## ✅ Phase 2: Change Report Types and Contracts

**Status:** Complete  <br>
**BlockedBy:** —  <br>
**Files:** src/main/java/songscribe/dom/ChangeReport.java, src/main/java/songscribe/dom/ReportedChange.java, src/main/java/songscribe/dom/RecordedChange.java, src/main/java/songscribe/dom/ChangeLines.java, src/main/java/songscribe/dom/LineRange.java, src/main/java/songscribe/dom/Song.java, src/main/java/songscribe/dom/ModificationSession.java, src/main/java/songscribe/dom/BreathMarkLoadPass.java, src/main/java/songscribe/dom/LoadPasses.java, src/main/java/songscribe/undo/UndoController.java, src/main/java/songscribe/undo/MutationReplayer.java  <br>
**Recommended model/effort:** Opus, medium — new types that carry invariants, and the contracts every recorder and the undo engine rely on

### Tasks

1. Read `docs/change-report.md` and `docs/mutations.md`. A change entry today stores element indices from different versions of the line: the span sweep records before the edit lands, the close-of-edit rules record after, a paste records both before and after it moves elements, and in a load the breath-mark pass removes elements after the tuplet, ending and hairpin passes record. The types below remove element indices from change entries, and resolve line numbers only when the edit or load is over.
2. Create `dom/ChangeLines.java`: `public record ChangeLines(Line first, Line last)` with a static factory `on(Line line)` for one line. A tie across a line break is the case with two different lines: the anchor's line and the line after it. Contract: both lines are lines of one song; `last` is `first` or the line after it.
3. Create `dom/LineRange.java`: `public record LineRange(int firstIndex, int lastIndex)`, zero-based line indices. Its contract states that `firstIndex <= lastIndex`, that `ChangeReport.Builder.build()` is the only producer, and that the indices describe the song after the edit or load that produced the report. The compact constructor has no check: `build()` resolves a `ChangeLines`, whose `last` is never before its `first`.
4. Create `dom/RecordedChange.java`: `public record RecordedChange(String headingKey, String description, @Nullable ChangeLines where)`. This is what a recorder hands to a report while an edit or load runs. Contract: `where` is `null` only for a change with no place in the song (the accidental conversion at load, a reader drop of a wedge bound to no note); a recorder records the `Line` objects, never indices; the line numbers are resolved later, so a line added or removed in front of `where` later in the same edit does not make the entry wrong.
5. Rewrite `dom/ReportedChange.java` as `public record ReportedChange(String headingKey, String description, @Nullable LineRange where)`. Rewrite its contract: a finished entry holds line indices only, resolved when the report was built, and holds no model object.
6. Write the new contract of `ChangeReport.Builder` before you change its code:
   - The only constructor is `Builder(Song song)`: a builder belongs to the song its entries name.
   - `record(RecordedChange change)` appends.
   - `build()` returns a `ChangeReport` whose `ReportedChange` entries hold each entry's lines resolved through `song.indexOfLine` at the moment `build()` runs, in record order.
   - State in the class Javadoc why resolution happens at `build()`: a report describes the song as the edit or load left it, and a line added or removed in front of a recorded line later in the same edit does not make the entry wrong.
   - Rewrite the class Javadoc of `ChangeReport` so the holders it names are the notification that carries a report and the view that shows it. The undo engine does not hold a report.
7. Implement the `Builder` changes. Delete the seed constructor `Builder(ChangeReport seed)`. Its only caller is `ModificationSession.OpenEdit`, which seeds a replay with the report of the edit being redone; a replay now starts from an empty builder like any edit. `LoadPasses.run` creates `new ChangeReport.Builder(parsed.song())`.
8. Remove the change report from the undo engine. An undo or a redo posts an empty change report, because nothing in a replay records: no modification rule runs in a replay (`ModificationSession.owesRules`), and companion side-work is suppressed.
   - `ModificationSession`: `OpenEdit` takes no seed and creates `new ChangeReport.Builder(song)`. Remove the seed parameter from `beginModification` and `inBracket`. `replay(Runnable body)` takes no report. Update the class Javadoc item on replay and the comment on `OpenEdit.report`.
   - `Song.replay(Runnable body)` takes no report. Rewrite its contract: the posted notification carries the report of the replay's own edit, which is empty because nothing in a replay records.
   - `MutationReplayer.undoBatch` and `redoBatch` take no report. Update both contracts.
   - `UndoController.UndoStep` becomes `UndoStep(List<Mutation> mutations, @Nullable String opName)`. Update its Javadoc, the empty step, the push in the `SongDidChangeNotification` handler, and the undo and redo calls and their Javadoc.
9. Resolution of a line that is not in the song at `build()`: find every recorder that can record a `Line` which the same edit or load later removes from the song. Deleting a line takes its spans with it unreported, so expect none. If you find none, resolve with `Objects.requireNonNull(song.indexOfLine(line), ...)`, an assertion with no `@throws` clause. If you find one, stop and report it to the user with file and line.
10. Change `Song.report(ReportedChange)` and `ModificationSession.report(ReportedChange)` to take `RecordedChange`. Update both contracts.
11. Rewrite the contract of `BreathMarkLoadPass` (its class Javadoc and `removeUnhostedBreathMarks`) and the wording in `LoadPasses` about where a removed breath mark is reported. Both now say: the entry names the line the breath mark was on. Remove the sentences "the index it stood at before removal" and "its index in the settled line". Rewrite the `LoadPasses` class Javadoc items that order the passes by the indices they report, so each states the reason that holds when entries name lines.
12. In the same file `Song.java`, rewrite the Javadoc of `Song.repeatSectionStart`. Its method behavior does not change. Replace the sentence that calls a `:|` with no open section "an answer about legal music, not about a broken song": a `null` answer means no section is open before `location`; such a `:|` is an error that the song's lint rule for balanced repeats reports. Name the rule in prose or `{@code}` only, never with `{@link}`: `songscribe.dom` does not reference `songscribe.lint`. Keep the rest of the contract.

## ✅ Phase 3: Migrate Change Recorders to Lines

**Status:** Complete  <br>
**BlockedBy:** 2  <br>
**Files:** src/main/java/songscribe/dom/Span.java, src/main/java/songscribe/dom/Spanning.java, src/main/java/songscribe/dom/TieRule.java, src/main/java/songscribe/dom/TupletLoadPass.java, src/main/java/songscribe/dom/BreathMarkLoadPass.java, src/main/java/songscribe/dom/SpanLoadPass.java, src/main/java/songscribe/dom/ParsedSong.java, src/main/java/songscribe/dom/LoadPasses.java, src/main/java/songscribe/dom/ElementLocationRange.java, src/main/java/songscribe/io/musicxml/MeasureMapper.java, src/main/java/songscribe/io/SongIO.java, src/main/java/songscribe/ui/clipboard/FragmentPaste.java, src/main/java/songscribe/ui/clipboard/PasteSpanReconciliation.java  <br>
**Recommended model/effort:** Opus, medium — call-site migration that renames a method and rewrites the contracts of the span and tie recorders

### Tasks

1. Read `dom/RecordedChange.java` (`headingKey`, `description`, `@Nullable ChangeLines where`), `dom/ChangeLines.java` (`first`, `last`, factory `on(Line)`), and the `report` methods of `Song` and `ModificationSession`, which take a `RecordedChange`.
2. Find every producer of `ReportedChange` and every user of `ElementLocationRange` with `jet_brains_find_referencing_symbols` on both types. The production producers are in `Span` (`location`, `reportedAs`, `reportedAt`, `removeReported`), `Spanning.Refused.reportedAt`, `TieRule.judge` and its `location` helper, `TupletLoadPass.place`, `BreathMarkLoadPass.removeUnhostedBreathMarks`, `SpanLoadPass`, `ParsedSong` (`readerEntries`, `accidentalsConvertedEntry`), `LoadPasses`, `MeasureMapper` (`readerEntries`, `drop`, `dropDamaged`, `at`, `through`, `locationOf`), `SongIO.DocumentReader.getSong`, `FragmentPaste`, and `PasteSpanReconciliation` (`Settlement.reports`, `settle`, `settleWith`). Do not touch `ReportPanel`; phase 8 rewrites it.
3. Change each producer to build a `RecordedChange` that holds the `Line` (or the two `Line` objects of a tie across a line break) instead of an `ElementLocationRange`. `ParsedSong.readerEntries` becomes `List<RecordedChange>`. An entry with no location keeps `null`.
4. `Span.location()` becomes the span's `ChangeLines` (its anchor's line; a span never crosses a line). Rename it with `jet_brains_rename` to say what it returns, and rewrite its Javadoc and the Javadoc of `reportedAs`, `reportedAt` and `removeReported` to say that the entry names the span's line. `Spanning.Refused.reportedAt` takes `ChangeLines`.
5. `TieRule`'s `location` helper becomes the tie's `ChangeLines`: the anchor's line, and the line of the note it reaches, when that note is on the next line. Rewrite its Javadoc to say so.
6. Delete `dom/ElementLocationRange.java` when no production code uses it. `ElementLocation` stays: `Song.repeatSectionStart` uses it.

## ✅ Phase 4: Lint Package, Linter, Repeat and Fine Rules

**Status:** Complete  <br>
**BlockedBy:** 1  <br>
**Files:** src/main/java/songscribe/lint/package-info.java, src/main/java/songscribe/lint/LintMark.java, src/main/java/songscribe/lint/Violation.java, src/main/java/songscribe/lint/LintFinding.java, src/main/java/songscribe/lint/LintCheck.java, src/main/java/songscribe/lint/LintRule.java, src/main/java/songscribe/lint/LintReport.java, src/main/java/songscribe/lint/Linter.java, src/main/java/songscribe/lint/BalancedRepeatsCheck.java, src/main/java/songscribe/lint/UnbalancedFineCheck.java, src/main/java/songscribe/message/notification/LintReportDidChangeNotification.java  <br>
**Recommended model/effort:** Opus, medium — new package design, a process-lived singleton, sealed types, and two rule algorithms with their contracts

### Tasks

1. Read `.claude/guides/singletons.md`, `.claude/guides/messages.md`, `docs/messages.md` and `docs/lifecycle.md`.
2. Create the package `songscribe.lint` with a `package-info.java` that carries only `@NullMarked`, like every other package. The package depends on `songscribe.dom` and on the message bus. `songscribe.dom` never imports it. Nothing in it imports `songscribe.ui` or `javax.swing`: the UI knows the linter, and the linter knows nothing of the UI. A lint rule only reads the song and never changes it, which is what separates it from a `dom.ModificationRule` such as `TieRule`, which repairs the song. State that in `LintRule`'s Javadoc.
3. Create `LintMark.java`: `public sealed interface LintMark` that declares `StaffElement element()`, with three records, each naming what the score view draws:
   - `Flagged(StaffElement element)` — the element is drawn in the lint color.
   - `FlaggedAnnotation(StaffElement element)` — a sawtooth squiggle is drawn under the element's annotation text; the text and the element keep their own colors.
   - `MissingLyric(StaffElement element, int verse)` — a "?" is drawn in the lint color in the lyric row of `verse`, where the element's syllable would stand. `verse` is 1-based, as `Lyric.verse()` is.
   Every consumer that draws a mark switches over `LintMark` without a `default` branch, so a new mark fails to compile until each consumer draws it. A consumer that needs only the element calls `element()`.
4. Create `Violation.java`: `public record Violation(String description, LintMark mark)`. A violation has exactly one mark: one finding, one place.
5. Create `LintCheck.java`: `@FunctionalInterface interface LintCheck { List<Violation> check(Song song); }`. Contract: reads `song` only; returns the violations in song order.
6. Create `LintRule.java`: `public enum LintRule { BALANCED_REPEATS, MISSING_LYRIC, UNBALANCED_FINE }`.
   - A nested `public enum Flag { BLOCKS_SAVE, BLOCKS_PLAYBACK }`. Its Javadoc states what each flag blocks: `BLOCKS_SAVE` refuses every save; `BLOCKS_PLAYBACK` disables Play while playback with repeats is on.
   - Each constant holds a `String headingKey` (`Strings.LINT_REPEATS_UNBALANCED`, `Strings.LINT_LYRICS_MISSING`, `Strings.LINT_FINE_UNBALANCED`, added by phase 1), a `LintCheck` (`BalancedRepeatsCheck`, `MissingLyricCheck`, `UnbalancedFineCheck`), and an `EnumSet<Flag>`. `BALANCED_REPEATS` carries `BLOCKS_SAVE` and `BLOCKS_PLAYBACK`; `MISSING_LYRIC` and `UNBALANCED_FINE` carry `BLOCKS_SAVE`. `MissingLyricCheck` is created by phase 5; reference it by that name.
   - `public boolean hasFlag(Flag flag)`.
7. Create `LintFinding.java`: `public record LintFinding(LintRule rule, Violation violation)` with `headingKey()` returning `rule.headingKey()`, and `description()` and `mark()` delegating to `violation`. A finding has no severity of its own.
8. Write `LintReport`'s contract, then implement it as `public final class LintReport`. A record cannot hold the identity maps below, because a record declares no instance field beyond its components. It has `findings()` (unmodifiable, in rule order, then song order), `EMPTY`, `isEmpty()`, `hasFlag(LintRule.Flag flag)` (some finding's rule carries `flag`), and three per-element queries for the score view, each answered from identity maps built in the constructor so a paint does one lookup per element:
   - `isFlagged(StaffElement element)` — some `Flagged` mark names it.
   - `isAnnotationFlagged(StaffElement element)` — some `FlaggedAnnotation` mark names it.
   - `missingLyricVerses(StaffElement element)` — the verses of every `MissingLyric` mark that names it; empty when none.
   `StaffElement` does not override `equals`, so the maps compare by identity; state that the queries are by identity.
9. Create `LintReportDidChangeNotification` in `songscribe.message.notification` as an empty subclass, following `.claude/guides/messages.md`: it is a signal, and a reader asks `Linter.getReport()` for the report.
10. Create `Linter.java`: a `public final class` singleton in the shape `.claude/guides/singletons.md` gives (a private static `INSTANCE`, static public methods, no `getInstance()`). It owns the lint report of the open document and lives for the process (`docs/lifecycle.md`, "Most objects are never torn down"), so its constructor subscribes to the bus and it is never disposed. Contract: runs on the EDT, the thread that owns the song; reads the song only.
    - `public static LintReport lint(Song song)` runs `LintRule.values()` in declaration order, wraps each violation as a `LintFinding` of its rule, stores the result as the current report, posts `LintReportDidChangeNotification`, and returns the report. Driving the loop from `values()` is what makes a new rule run without a change here.
    - A `@Handler` for `SongDidChangeNotification` calls `lint` with the song the notification carries, at once. Every edit, undo and redo posts one, so the report is current after each of them. Only document songs post it: a song being parsed runs with mutation tracking suspended.
    - `public static LintReport getReport()` returns the current report, `LintReport.EMPTY` before the first run.
    - `public static boolean hasFlag(LintRule.Flag flag)` returns `getReport().hasFlag(flag)`.
11. Write the contract of `BalancedRepeatsCheck` (package-private final class), then implement it as one forward pass over every element of every line, in song order. Use `ElementType.opensRepeatSection()`, `ElementType.closesRepeatSection()` and `ElementType.closesRepeat()`; read their Javadoc first. The semantics, which the contract states:
    - The song start opens a section, but only for the first right repeat before any closer. In `A :| B :|` the first `:|` balances with the song start and the second has no opener: flag the second (`LINT_REASON_REPEAT_NOT_OPENED`, mark `Flagged`).
    - A `||` (and any other element whose type closes a repeat section without being a right repeat) closes the open section, including the song-start section. In `A || B :|` the `:|` has no opener: flag it.
    - A left repeat inside an open section is nested: flag that left repeat (`LINT_REASON_REPEAT_NESTED`). In `|: |: :| :|` the second `|:` is flagged.
    - A `:|:` first closes the open section, as a right repeat, then opens a new one. `|: A :|: B :|` has no finding; `|: A :|: B` flags the `:|:` (`LINT_REASON_REPEAT_NOT_CLOSED`).
    - A left repeat whose section is still open at the end of the song is flagged (`LINT_REASON_REPEAT_NOT_CLOSED`).
    - Line breaks neither open nor close a section.
12. Write the contract of `UnbalancedFineCheck` (package-private final class), then implement it as one forward pass. An annotation is read with `element.findAttachment(AnnotationAttachment.class)` and `getAnnotation().text()`. The semantics, which the contract states:
    - A **Fine** is an annotation whose whole text, without surrounding whitespace, is `fine` in any case.
    - A **reference** is any other annotation whose text contains `fine` as a whole word, in any case: match with the regular expression `\bfine\b` compiled with `Pattern.CASE_INSENSITIVE` and held in a named constant. `D.C. al Fine`, `D.C. al fine` and `al Fine.` are references; `Finest` is not.
    - A Fine on an element where `ElementType.isBar()` is `false`: `LINT_REASON_FINE_NOT_ON_BAR`.
    - A Fine on the last element of the song (the last element of the last line that has elements): `LINT_REASON_FINE_ON_LAST_BAR`.
    - A Fine with no reference after it in song order: `LINT_REASON_FINE_NOT_REFERENCED`.
    - A reference with no Fine before it in song order: `LINT_REASON_FINE_REFERENCE_WITHOUT_FINE`.
    - Every Fine violation uses the mark `FlaggedAnnotation` on the annotated element.
13. The code that finds a violation writes its text with `Strings.get`, as change recorders do. The panel never interprets a finding.

## ✅ Phase 5: Lyric Span Extraction and Missing-Lyric Rule

**Status:** Complete  <br>
**BlockedBy:** 4  <br>
**Files:** src/main/java/songscribe/lint/MissingLyricCheck.java, src/main/java/songscribe/dom/LyricRun.java, src/main/java/songscribe/ui/renderer/LineInvariants.java  <br>
**Recommended model/effort:** Opus, medium — extraction of a domain operation from rendering code, and a rule algorithm over lyric chains

### Tasks

1. Read `docs/lyrics.md` and `Lyric`'s Javadoc. A slot satisfies the rule when it holds a lyric for the verse, or when an open lyric chain covers it. Inner notes of a melisma hold no `Lyric` (only `Extend.START`, and the `STOP` and line-break `CONTINUE` carriers, do). Inner notes of a hyphen run, such as the middle note of "one--ness" over three notes, hold no `Lyric` either.
2. `LineInvariants.isLyricSpanPlaying` already computes where the span of a forward-extending lyric (`Extend.START`, `Syllabic.BEGIN`, `Syllabic.MIDDLE`) ends on its line: at the next `STOP` carrier, or just before the next text-bearing lyric, skipping `CONTINUE` carriers, or at the end of the line. Extract that computation as a domain-defined operation on the type that owns lyric chains (`LyricRun` holds the chain walks; place it there unless `docs/lyrics.md` or `LyricRun`'s Javadoc names a better owner). Name it for what it answers, write its contract, and make `isLyricSpanPlaying` call it so the walk exists once.
3. A chain can cross a line break. Before you write any cross-line logic, read `LyricRun` and its helpers (`previousLyricBearingIndex`, `isWordContinuingLyric`, `hasFollowingTextBearingLyric`, `firstLyricBearingIndex`) and use an existing walk where one answers the question. A repeat ends a chain: `docs/lyrics.md` states that nothing runs through a repeat.
4. Write the contract of `MissingLyricCheck` (package-private final class in `songscribe.lint`), then implement it. For every verse the song carries, and for every slot in song order, the slot is flagged when it holds no lyric for that verse, no open chain covers it, and no tie reaches it. A note that a tie from the note before it reaches (`NoteElement.incomingTie()` is not `null`) continues a sung note and needs no syllable. Each flagged slot is one violation with the text `LINT_REASON_LYRIC_MISSING` (taking the verse) and the mark `MissingLyric(element, verse)`. Every verse is checked, not only the active one: a translation verse must have a lyric on every note, and a finding must not depend on which verse is shown.
5. Implement the check as one forward pass for each verse. At each forward-extending lyric, call the operation from task 2 once and continue after the end of its span. A slot outside every span that holds no lyric for the verse and that no tie reaches is flagged. Each element is visited once per verse.
6. Find how the song's verses are enumerated (the verses present in any element's lyrics, or a count on `Song`; `LyricRun.verseNumbers` is one per-line form). If two places compute it, extract one and use it from both.

## ✅ Phase 6: Score Drawing

**Status:** Complete  <br>
**BlockedBy:** 5  <br>
**Files:** src/main/java/songscribe/ui/renderer/, src/main/java/songscribe/ui/component/score/, src/main/java/songscribe/smufl/  <br>
**Recommended model/effort:** Opus, medium — merges two color-precedence chains and adds lint drawing to the paint path

### Tasks

1. `LineInvariants` has two color chains with the same order: the private `colorFor(int, BooleanSupplier, BooleanSupplier)` (playing, an extra selection test, range selection, hover, black), used by `getElementColor`, `getLyricColor` and `getLyricConnectorColor`; and `colorFor(HitTarget.Element, Integer)` (element or tie playing, exact-target selection, hover, black), used by `RenderingUtils.decorationColor` and `StaffElementRenderer.renderAccidental`. Merge them into one chain that holds the order once. Each caller supplies its playing test and its selection test. Read both bodies and every caller before you write the merged chain.
2. The order of the merged chain is: playing, selection, hover, lint, black. The lint step returns the lint color for an element that `LintReport.isFlagged` names. Write this order into the merged chain's Javadoc, with the reason selection wins: the selection is what the next Delete removes (`RenderingUtils.decorationColor` states that reason today).
3. Add a `LintReport` input to `LineInvariants.Builder` (default `LintReport.EMPTY`). `LineRenderer.buildInvariants` sets it from `Linter.getReport()`.
4. An annotation whose element `LintReport.isAnnotationFlagged` names draws its text in its normal color, and gets a squiggle under the text in the lint color whatever the text's color: the SMuFL glyph `wiggleVibratoMediumFaster` (U+EADD) at half scale, as many whole glyphs as fit the text width, centered on it, with the squiggle's ink top 2 document pixels below the text baseline. `ui/renderer/AnnotationRenderer.java` paints annotation text.
5. `LyricLayoutBuilder` gives every note that can bear a syllable but has none in the active verse a placeholder box of "?", placed by the same rule as a syllable of that text (`computeLyricBoxLeftXSs`, grace notes included). The placeholder is not measured into the column, so it takes no space and every fit gate is unaffected; it opens and closes no connector. `LayoutResult.getLyricPlaceholder` returns it. `LyricTextRenderer` draws an element's placeholder in the lint color when the element has a `MissingLyric` mark whose verse equals `Song.getActiveVerse()`. Layout never reads the lint report. Look up the glyph in `smufl/`; add it to the registry if it is missing (`https://w3c.github.io/smufl/latest/index.html?search=wiggleVibratoMediumFaster`). Read `.claude/guides/spatial-units.md` before you compute the width and the baseline. Marks for other verses are not drawn; a verse switch already invalidates layout, so it needs no extra trigger.
6. Every lint drawing site reads the lint color from `ScoreView.getLintColor()` at paint time. Phase 7 adds that static method to `ScoreView`; use that exact name.
7. Each `switch` over `LintMark` has no `default` branch.

## ✅ Phase 7: Lint Wiring, Save Gate, Play Action and Save As Fix

**Status:** Complete  <br>
**BlockedBy:** 1, 4  <br>
**Files:** src/main/java/songscribe/ui/component/ScoreView.java, src/main/java/songscribe/ui/component/MainFrame.java, src/main/java/songscribe/ui/action/UIAction.java, src/main/java/songscribe/ui/playback/PlayStopAction.java, src/main/java/songscribe/ui/playback/PlayWithRepeatsAction.java  <br>
**Recommended model/effort:** Opus, medium — lint triggers at document install, two gates, an action-enabling rule, and a save-path defect fix

### Tasks

1. Read `.claude/guides/messages.md`, `.claude/guides/option-dialogs.md`, `.claude/guides/prefs.md`, and the class Javadoc of `songscribe.lint.Linter` and `songscribe.lint.LintRule`.
2. `ScoreView` lints every song it installs. In `ScoreView.setSong`, call `Linter.lint(song)` before the `MessageCenter.post(new DocumentDidLoadNotification(song, report))` line, so every subscriber to the load sees the new song's lint report. In `ScoreView.init`, call `Linter.lint(song)` after it creates its first `Song`, so the linter exists and is subscribed before the first edit.
3. `ScoreView` repaints the score on `LintReportDidChangeNotification`.
4. Add `public static Color getLintColor()` to `ScoreView`, beside `getSelectionColor()`, returning `FlatLafProps.getColor(FlatLafKey.SCORE_OVERFLOW_COLOR)`. Its Javadoc states that a lint error uses the overflow color on purpose: both mean that the score shows something wrong with the document. Phase 6 calls it.
5. Fix the Save As defect in `MainFrame`. `saveAsNewFile` calls `setCurrentFile(saveFile)` before `saveCurrentFile` writes, so a failed write leaves the window's current file pointing at a file that was never written. Extract the write in `saveCurrentFile` into a method that takes the target file; `saveCurrentFile` writes to `currentFile`, and `saveAsNewFile` writes to the chosen file and calls `setCurrentFile` and `RecentDocumentsManager.add` only when the write succeeds.
6. Add the save gate in `MainFrame`. Every save goes through `save()` (Save, and the "Save changes?" prompt through `showSaveDialog` and `SaveAction`) or `handleSaveAs` (Save As). At the start of both, before any file chooser, call one gate method. When `Linter.hasFlag(LintRule.Flag.BLOCKS_SAVE)` is `true`, it calls `showMessagesPanel()`, shows an error alert with `OptionDialogs` using `Strings.ALERT_TITLE_SAVE_BLOCKED` and `Strings.ERROR_SAVE_BLOCKED_BY_LINT`, and the save returns without writing (`save()` returns `false`, so a close or quit is canceled). The report is current: `Linter` lints after every change.
7. Add `UIAction.Flag.DISABLE_WHEN_PLAYBACK_BLOCKED`. Read `UIAction.updateEnabledState` and its `enableFrom*` methods first, and follow their shape. An action with the flag is disabled when playback is not running, the current playback settings play repeats, and `Linter.hasFlag(LintRule.Flag.BLOCKS_PLAYBACK)` is `true`. While playback runs, the flag never disables the action: Play and Stop are one action, and Stop must stay available. Re-evaluate the enabled state on `LintReportDidChangeNotification` and when the play-with-repeats setting changes (`MainFrame.handleTogglePlayWithRepeats` writes `PrefsKey.PLAY_WITH_REPEATS`; find the notification that change posts).
8. `PlayStopAction` carries `DISABLE_WHEN_PLAYBACK_BLOCKED`. While the flag disables it, its tooltip is `Strings.ACTION_PLAY_BLOCKED_TOOLTIP`; otherwise its tooltips are as today.
9. `PlayWithRepeatsAction` carries `UIAction.Flag.DISABLE_WHEN_PLAYING`. The repeats setting then cannot change while playback runs, so `PlaybackController.applyPrefsDuringPlayback` never rebuilds a sequence with repeats for a song whose findings block playback.

## ✅ Phase 8: Messages Panel

**Status:** Complete  <br>
**BlockedBy:** 1, 2, 4  <br>
**Files:** src/main/java/songscribe/ui/component/ReportPanel.java, src/main/java/songscribe/dom/ChangeReport.java, src/main/java/songscribe/Strings.java, src/main/resources/songscribe/strings.properties  <br>
**Recommended model/effort:** Opus, medium — restructures the panel around two report sources with different lifetimes

### Tasks

1. Read `ReportPanel.java`, `docs/change-report.md`, and the class Javadoc of `songscribe.lint.Linter`. The panel today holds a `JList<ReportedChange>` and replaces it on every change.
2. The panel holds two sources: the last `ChangeReport` (replaced by every `SongDidChangeNotification` and `DocumentDidLoadNotification`) and the lint report, read from `Linter.getReport()` on every `LintReportDidChangeNotification` and `DocumentDidLoadNotification`. A change notification never clears the lint rows, and a lint notification never clears the change rows.
3. The list holds a private row record (heading text, detail text). One method converts a `ReportedChange` to a row and one converts a `LintFinding`. A lint row's heading text starts with ❗ and a change row's with 💬; hold each prefix in a named constant in `ReportPanel`. No heading is drawn in a color of its own; delete the code that colors a heading.
4. Order the rows once, in the panel: every change row before every lint row, and the rows of each source in song order, by line and then by element. A change names lines only, so it sorts as element 0 of its first line; a change with no place sorts before the first line. Rows at one position keep the order of their report. Then delete `ChangeReport.byHeading` if `jet_brains_find_referencing_symbols` shows no other caller.
5. A change row's location is `Strings.REPORT_LOCATION_LINE` for one line or `Strings.REPORT_LOCATION_LINE_RANGE` for two, shown 1-based; a `null` location shows the description only. Delete the three element-index branches of `location()`.
6. A lint row's location is `Strings.REPORT_LOCATION_ELEMENT` for the finding's element (`finding.mark().element()`), resolved when the row is rendered: the line index through `StaffElement.getParentLine()` and `Song.indexOfLine`, the element index through `Line.getElementIndex`. A finding whose element has no parent line is not listed: an edit removed it, and the next lint run replaces the report.
7. On `DocumentDidLoadNotification`, show the panel (`mainFrame.showMessagesPanel()`) when the change report or `Linter.getReport()` is not empty.
8. Update the class Javadoc to describe the two sources and their lifetimes.
9. Delete `REPORT_LOCATION_LINES` and `REPORT_LOCATION_ELEMENTS` from `Strings.java` and `strings.properties` when no code uses them.

## ✅ Phase 9: Design Docs

**Status:** Complete  <br>
**BlockedBy:** 2, 4, 5, 7, 8  <br>
**Files:** docs/change-report.md, docs/mutations.md, docs/lint.md, CLAUDE.md  <br>
**Recommended model/effort:** Opus, medium — subsystem prose about concepts, not API

### Tasks

1. Read the "Design Docs" section of `CLAUDE.md` and `~/.claude/guides/documents.md`. A doc states concepts and the shape, not class or method names, and holds no history.
2. Revise `docs/change-report.md` so it states what is true now: an entry says where by line, never by element; the reporter records the line itself and the line number is fixed when the edit or load is over, so a line added or removed in front of it in the same edit does not make it wrong; an undo and a redo each deliver an empty report, because a replay judges nothing and records nothing; the panel lists changes first, under 💬 headings, and lint findings after, under ❗ headings. Replace the paragraph on undo and redo (today near line 103), which says a redo shows the report of the edit again.
3. Revise `docs/mutations.md` (today near line 134), which says a redo delivers again the report of the edit, so it states that undo and redo each deliver an empty report.
4. Write `docs/lint.md`. Read the contracts of `BalancedRepeatsCheck`, `UnbalancedFineCheck`, `MissingLyricCheck`, `LintRule`, `LintReport` and `Linter`, and state:
   - what a lint rule is and how it differs from a rule at the close of an edit (it reports and never repairs);
   - the three rules and their semantics;
   - who owns the report (one linter for the process, which the UI asks and which knows nothing of the UI) and when a run happens (at once after every edit, undo and redo; when a document is installed);
   - that every finding is an error, and that what it blocks comes from its rule's flags: every rule blocks a save; the repeats rule also disables Play while playback with repeats is on, and the repeats setting cannot change during playback;
   - how the score and the panel show a finding;
   - that a finding names elements, so it stays on its element between an edit and the next run.
5. Add a line to the "Required Reading by Task" list in `CLAUDE.md` for lint rules, the linter, lint flags, or lint drawing, pointing to `docs/lint.md`.

## ✅ Phase 10: Rewrite Report Tests to Line Ranges

**Status:** Complete  <br>
**BlockedBy:** 2, 3  <br>
**Files:** src/test/java/songscribe/io/LoadPassesTest.java, src/test/java/songscribe/dom/BreathMarkLoadPassTest.java, src/test/java/songscribe/dom/TupletLoadPassTest.java, src/test/java/songscribe/dom/HairpinLoadPassTest.java, src/test/java/songscribe/dom/EndingSectionRuleTest.java, src/test/java/songscribe/dom/TieRuleTest.java, src/test/java/songscribe/dom/ChangeReportDeliveryTest.java, src/test/java/songscribe/dom/BatchReplay.java, src/test/java/songscribe/dom/SpanTest.java, src/test/java/songscribe/dom/HairpinTest.java, src/test/java/songscribe/dom/ModificationSessionTest.java, src/test/java/songscribe/undo/UndoControllerTest.java, src/test/java/songscribe/io/musicxml/MusicXmlCrossLineSpanTest.java, src/test/java/songscribe/io/musicxml/MusicXmlTieTest.java, src/test/java/songscribe/ui/clipboard/PasteSpanReconciliationTest.java  <br>
**Recommended model/effort:** Sonnet, medium — mechanical rewrite of existing assertions and calls to the new types and signatures

### Tasks

1. Read `.claude/guides/testing-common.md` and `.claude/guides/testing-unit.md`.
2. A finished change entry is `dom.ReportedChange(headingKey, description, @Nullable LineRange where)`, where `LineRange(firstIndex, lastIndex)` holds zero-based line indices. The type `ElementLocationRange` does not exist. Rewrite every assertion in the listed files that names an element location so it asserts the `LineRange` instead. Keep each test's promise; change only how the location is stated.
3. The hairpin case in `LoadPassesTest` (a hairpin after a breath mark the load removes) asserts the hairpin's line only.
4. `MutationReplayer.undoBatch(song, fonts, mutations)` and `redoBatch(song, fonts, mutations)` take no report, and an undo or a redo posts an empty change report. Remove the `ChangeReport.EMPTY` argument from every call in the listed files. An assertion that a redo shows the report of the edit again, or that an undo or a redo carries a particular non-empty report, maps to no contract: delete it.
5. A test whose only promise was an element index in a report entry no longer maps to a contract: delete it.
6. List each deletion from tasks 4 and 5 with its reason in your final report.

## ✅ Phase 11: New Tests

**Status:** Complete  <br>
**BlockedBy:** 2, 4, 5, 6, 7  <br>
**Files:** src/test/java/songscribe/lint/, src/test/java/songscribe/dom/ChangeReportResolutionTest.java, src/test/java/songscribe/ui/renderer/LineInvariantsColorTest.java, src/test/java/songscribe/PackageDependencyTest.java, src/test/java/songscribe/e2e/  <br>
**Recommended model/effort:** Sonnet, medium — tests derived from contracts that earlier phases wrote

### Tasks

1. Read `.claude/guides/testing-common.md`, `.claude/guides/testing-unit.md` and `.claude/guides/testing-e2e.md`. Derive each test from the contract of the code it covers (`BalancedRepeatsCheck`, `MissingLyricCheck`, `UnbalancedFineCheck`, `ChangeReport.Builder.build`, the merged color chain in `LineInvariants`), not from its implementation.
2. Before you write each test method, check whether it sits beside a test of the same shape. If it does, both are rows of one parameterized table.
3. Write exactly these tests. The user approved this list; do not add others.
   1. Balanced repeats: `|: A :|` gives no finding; a `:|` with nothing before it balances with the song start.
   2. `A || B :|` flags the `:|`.
   3. `A :| B :|` flags the second `:|`.
   4. A `|:` with no `:|` before the end of the song is flagged.
   5. `|: |: :| :|` flags the second `|:` as nested.
   6. `|: A :|: B :|` gives no finding; `|: A :|: B` flags the `:|:`.
   7. Missing lyric: a slot with no lyric and no open chain is flagged with its verse. Not flagged: inner notes of a melisma, inner notes of a hyphen run, a chain across a line break, the host of a paired grace note, the note a tie from the note before it reaches, rests, barlines.
   8. Missing lyric: verse 1 complete and verse 2 missing on one note gives one finding, for verse 2.
   9. Fine: a Fine on a note, a Fine on the last barline of the song, and a Fine with no later reference are each flagged; a Fine followed by `D.C. al Fine` gives no finding; a `FINE` followed by `D.C. al fine` gives no finding.
   10. Fine references: `D.C. al Fine` with no earlier Fine is flagged; `al Fine.` and `D.C. al fine` are references; `Finest` is not.
   11. `ChangeReportResolutionTest`: a change recorded on a line, followed by a line inserted in front of it in the same edit, reports the new line index; a tie across a line break reports both lines.
   12. `LineInvariantsColorTest`: a flagged element that is not selected gets the lint color; the same element selected gets the selection color; playing wins over both.
   13. `PackageDependencyTest`: add a method asserting that `songscribe.dom` never imports `songscribe.lint`, and a method asserting that `songscribe.lint` never imports `songscribe.ui`, each following the existing `domMustNotImportUi` shape.
   14. e2e: Save on a song with a lint error writes no file and leaves the current file unchanged.
4. Rows 1–6 share one table, rows 7–8 one table, and rows 9–10 one table, unless the testing guides require otherwise.

## ✅ Phase 12: Compile and Test Gate

**Status:** Complete  <br>
**BlockedBy:** 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11  <br>
**Files:** —  <br>
**Recommended model/effort:** Sonnet, low — runs the scripts and reports

### Tasks

1. Run `./scripts/compile.sh --test`. It must report SUCCESS. Never use `./gradlew`, `gradle`, `javac` or `java -cp`.
2. Run `./scripts/test.sh` with the unit test classes from phases 10 and 11: `LoadPassesTest BreathMarkLoadPassTest TupletLoadPassTest HairpinLoadPassTest EndingSectionRuleTest TieRuleTest ChangeReportDeliveryTest SpanTest HairpinTest ModificationSessionTest UndoControllerTest MusicXmlCrossLineSpanTest MusicXmlTieTest PasteSpanReconciliationTest ChangeReportResolutionTest LineInvariantsColorTest PackageDependencyTest` and every test class under `src/test/java/songscribe/lint/`. Every test must pass.
3. Ask the user before you run the e2e test from phase 11, task 3, row 14. Run it only with approval, after the unit run is green.
4. Report failures with their output. Do not rerun with extra flags, and do not stash to check whether a failure was there before.

## ⏳ Phase 13: Manual UI Verification

**Status:** Pending  <br>
**BlockedBy:** 12  <br>
**Files:** —  <br>
**Recommended model/effort:** — (the user verifies)

### Tasks

1. The user runs the app (`./scripts/run.sh`) and checks:
   1. A `:|` after a `||` draws in red, and the Messages panel lists it after the change rows, under a heading that starts with ❗.
   2. A note with no lyric shows a red "?" in the lyric font where its syllable would stand, a grace note included; the line's spacing does not change. Switching to another verse shows that verse's marks only.
   3. A Fine with no later reference keeps its normal color and shows the red sawtooth squiggle under its text, the squiggle's top 2 px below the text baseline; the barline keeps its normal color. `Fine` with `D.C. al fine` later shows no error.
   4. A flagged element that is selected draws in the selection color.
   5. During lyric input, the "?" marks update at each keystroke with no visible delay.
   6. Save, Save As, and Save from the "Save changes?" prompt on close are each refused while an error exists: the alert shows, the panel opens, and no file is written. The close is canceled.
   7. Opening a file with a lint error opens the Messages panel and lists the errors.
   8. An edit that removes a span reports it by line only, under a heading that starts with 💬, and an edit in another place keeps the lint rows in the panel.
   9. Undo and redo update the lint rows; after a redo, the panel shows no change rows.
   10. With play-with-repeats on and a repeat error in the song, Play is disabled and its tooltip names the reason; with play-with-repeats off, Play is enabled. A missing lyric alone does not disable Play.
   11. During playback, the play-with-repeats toggle is disabled.
