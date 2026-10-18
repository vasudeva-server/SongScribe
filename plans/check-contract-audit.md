# Contract audit: the caller test

Each Javadoc clause in scope is checked against the caller test in `guides/conventions/contracts.md`. Each entry gives the exact clause, the reason it fails, and the action.

## Caller-test audit — model layer (dom, io, layout, hit, midi)

Method: every Javadoc in the in-scope added files; in the other model files, every Javadoc on a
member whose Javadoc the five commits added or changed (matched on the declaration name and on
added Javadoc lines, then judged on the current text); every `@effects` clause. 101 Javadocs
audited. Several members the diffs touched (e.g. `Song.getLinter`, `Song.scripts`,
`Song.verseNumbers`, `Song.getActiveScript`) carry no Javadoc in the current tree, so there was
nothing to judge.

Self-use note: `EditableRun.insertRun` and `deleteRange` are interface default methods whose
`@effects` also promise implementors "one record per step" through the abstract primitives. The
reductions below drop that clause because it names the calls; if the user wants the self-use
promise kept for implementors, it belongs as a type-3 sentence (what breaks: undo granularity in a
recording implementor), not in `@effects`.

### `BreathMarkLoadPass.run` — src/main/java/songscribe/dom/BreathMarkLoadPass.java:41
- Clause: "Each breath mark is judged against the element in front of it once the pass has settled everything before it, so a run of consecutive breath marks keeps at most its first: the second follows a breath mark, never a duration element."
  - Fails because: it names the order of the pass's steps beside the outcome it produces.
  - Action: reduce to: "Of a run of consecutive breath marks, at most the first stays: the second follows a breath mark, never a duration element."
- Clause: "@invariant afterwards every breath mark in {@code song} stands immediately after a pitched note or a rest, the invariant {@link StaffElementRun#canInsertRunAt} states"
  - Fails because: the trailing phrase names a collaborator that states the same rule.
  - Action: reduce to: "@invariant afterwards every breath mark in {@code song} stands immediately after a pitched note or a rest"
- Clause: "@effects each removal goes through {@link Line#removeElement}, which runs the span sweep: a tuplet that holds the removed mark is removed with it, and that removal is not recorded to {@code report}, because the load runs with mutation tracking suspended and the sweep's report is silent then; records to {@code report}, in document order, each breath mark removed, with an entry that names the line the breath mark was on"
  - Fails because: it names the callee, the sweep mechanism and the reason an internal step is silent, beside the state change.
  - Action: reduce to: "@effects a tuplet that holds a removed breath mark is removed with it, and that removal is not recorded to {@code report}" and "@effects records to {@code report}, in document order, each breath mark removed, with an entry that names the line the breath mark was on"

### `ChangeLines` (record) — src/main/java/songscribe/dom/ChangeLines.java:22
- Clause: "The lines a recorded change was made on, named by the {@link Line} objects themselves so the place stays right whatever else the same edit or load does to the song before its report is built."
  - Fails because: "named by the Line objects themselves" states the mechanism (what the record holds) beside the promise.
  - Action: reduce to: "The lines a recorded change was made on. The place stays right whatever else the same edit or load does to the song before its report is built."
- Clause: "@invariant {@code last} is {@code first} or a later line of the song; the record does not check it, because every producer guarantees it"
  - Fails because: "because every producer guarantees it" names the record's producers (collaborators).
  - Action: reduce to: "@invariant {@code last} is {@code first} or a later line of the song; the record does not check it"

### `ChangeReport.Builder` — src/main/java/songscribe/dom/ChangeReport.java:52
- Clause: "{@link #build()} resolves recorded lines to indices only then, because a line added or removed in front of a recorded line moves its index."
  - Fails because: it describes how `build()` gets its result; the outcome is already `build()`'s own clause.
  - Action: delete
- Clause: "@invariant every line a recorded entry names is still a line of the song when {@link #build()} runs: no edit or load records a change on a line and then removes it"
  - Fails because: the part after the colon describes what collaborators (edits, loads) do rather than the obligation.
  - Action: reduce to: "@invariant every line a recorded entry names is still a line of the song when {@link #build()} runs"

### `DetachedLyricRun.isSongStartAt` — src/main/java/songscribe/dom/DetachedLyricRun.java:55
- Clause: "A detached run is in no song; where a fragment lands is the destination line's question."
  - Fails because: it justifies the implementation and names a collaborator (the destination line); the outcome a caller relies on (always `false`) is already stated by `StaffElementRun.isSongStartAt`'s `@return`.
  - Action: delete the whole Javadoc

### `EditableRun.insertRun` — src/main/java/songscribe/dom/EditableRun.java:54
- Clause: "Inserts {@code run} at {@code index} as one run: adds the elements in order and repairs the seams the run breaks on either side."
  - Fails because: "adds the elements in order" is a step.
  - Action: reduce to: "Inserts {@code run} at {@code index} as one run, and repairs the seams the run breaks on either side."
- Clause: "The steps are not independent — {@link LyricRun#repairNeighborsBeforeInsertion} reads pre-insertion indices, so it must run before the first {@link #addElement}, and {@link LyricRun#repairNeighborsAfterInsertion} reads the run's first element where it landed, so it must run after the last. A caller that open-codes the sequence can leave out a step, and leaving one out shows up as a glissando pointing at a barline or a word hyphenated into an empty note, neither of which fails anywhere near the insertion."
  - Fails because: it names callees and the order of the body's steps; only the type-3 part (what breaks when a caller open-codes the insertion) is exempt.
  - Action: reduce to: "The repairs depend on the order of the insertion steps; a caller that open-codes the sequence can leave out a step, and leaving one out shows up as a glissando pointing at a barline or a word hyphenated into an empty note, neither of which fails anywhere near the insertion."
- Clause: "@effects inserts the elements and applies the neighbor repairs to this run, each through the primitives and {@link #modifyElement}, so an implementor that records gets one record per step"
  - Fails because: it names the calls that make the change (see the self-use note above).
  - Action: reduce to: "@effects this run holds the elements of {@code run} from {@code index}, with the neighbor repairs applied"

### `EditableRun.deleteRange` — src/main/java/songscribe/dom/EditableRun.java:86
- Clause: "Nothing past the widened range goes: no path puts a second breath mark behind the first, since a breath mark stands only directly after a duration element."
  - Fails because: the part after the colon explains the outcome by what other code paths do (collaborators).
  - Action: reduce to: "Nothing past the widened range goes."
- Clause: "This is the deletion an edit performs. {@link #removeRange} and {@link #removeElement} are the primitives beneath it, and repair nothing."
  - Fails because: "the primitives beneath it" names the route by which this method works.
  - Action: reduce to: "This is the deletion an edit performs; {@link #removeRange} and {@link #removeElement} repair nothing."
- Clause: "@effects removes the elements through one {@link #removeRange} and applies the repairs through {@link #modifyElement}; an implementor that records gets one record per step"
  - Fails because: it names the calls that make the change (see the self-use note above).
  - Action: reduce to: "@effects removes the elements {@code effectiveRange} covers and applies the repairs the invariants state"

### `EditableRun.deleteRanges` — src/main/java/songscribe/dom/EditableRun.java:137
- Clause: "Deletes every range in {@code ranges} through {@link #deleteRange}, working from the last to the first so that no removal invalidates the indices of one still to come."
  - Fails because: it names the callee and the order of the body's steps beside the promise.
  - Action: reduce to: "Deletes every range in {@code ranges} as {@link #deleteRange} deletes one; no removal invalidates the indices of a range still to come."
- Clause: "The ordering is the obligation {@link #deleteRange} states and {@link StaffElementRun#redundantKeyChangeRanges(Key)} repeats, discharged once here rather than at each caller: a caller that walks the list forward corrupts every range after the first, and nothing in the types says so."
  - Fails because: it names collaborators and records why the method exists; a caller needs only the promise in the first sentence and the precondition on `ranges`.
  - Action: delete
- Clause: "@effects one {@link #deleteRange} per range, last to first"
  - Fails because: it names the call and the order instead of the state that changes.
  - Action: reduce to: "@effects removes every range in {@code ranges}, with the repairs {@link #deleteRange} states for each"

Derived-test coverage: the ascending-ranges case stays covered by "no removal invalidates the indices of a range still to come".

### `ElementType.closesRepeatSection` — src/main/java/songscribe/dom/ElementType.java:425
- Clause: "Wider than {@link #closesRepeat()}: a {@code ||} and a final barline end every open section without sending playback back, so a {@code :|} after them finds no section to play back to unless another bar opens one."
  - Fails because: the "so a :| after them…" consequence describes what a reader of the answer (the repeat-section walk) does.
  - Action: reduce to: "Wider than {@link #closesRepeat()}: also true for a {@code ||} and a final barline, which end every open section without sending playback back."

### `Ending.closesOpenSection` — src/main/java/songscribe/dom/Ending.java:132
- Clause: "The one statement of the section rule; {@link #spanning} and {@link EndingSectionRule} both apply it."
  - Fails because: it names the method's callers (collaborators).
  - Action: delete

### `EndingSectionRule.apply` — src/main/java/songscribe/dom/EndingSectionRule.java:53
- Clause: "@effects when {@code batch} holds a mutation that can change a repeat section, removes, through {@link Span#removeReported} with {@link Strings#REPORT_REASON_ENDING_NO_OPEN_SECTION}, every ending in the song whose split closes no open repeat section"
  - Fails because: it names the call that makes the change.
  - Action: reduce to: "@effects when {@code batch} holds a mutation that can change a repeat section, removes every ending in the song whose split closes no open repeat section, and reports each removal with {@link Strings#REPORT_REASON_ENDING_NO_OPEN_SECTION}"

### `LeadingBarLoadPass` (class) — src/main/java/songscribe/dom/LeadingBarLoadPass.java:27
- Clause: "No bar line stands first in the song — the rule {@link SongStart} states, which every edit that writes an element keeps. The pass removes each such bar line and reports the line it was on. A key change behind the bar line goes with it, and the key it set becomes the line's own key first, so the song sounds as the file wrote it. Nothing else is moved or repaired into a place the file did not say."
  - Fails because: "which every edit that writes an element keeps" names collaborators, "first" states the order of steps, and the removal-and-report sentence restates `run`'s contract.
  - Action: reduce to: "The rule is the one {@link SongStart} states. A key change behind a removed bar line goes with it, and the song still sounds as the file wrote it; nothing else is moved or repaired into a place the file did not say."

### `LeadingBarLoadPass.run` — src/main/java/songscribe/dom/LeadingBarLoadPass.java:43
- Clause: "@effects each removal takes the bar line's effective range ({@link StaffElementRun#effectiveRange}) through {@link Line#removeRange}, so a key change that sits behind the bar line goes with it, as a deletion of the bar line would take it. When it does, the pass first sets that key change's key as the line's own key ({@link Line#setKey}), so where several bar lines stand first the line ends in the key of the last key change removed. The line leaves off in the key it left off in before, so no other line's key moves and no key change is stranded. The removal runs the span sweep: a span with an endpoint on a removed element, such as an ending that starts on the bar line, is removed with it, and that removal is not recorded to {@code report}, because the load runs with mutation tracking suspended and the sweep's report is silent then. Records to {@code report} one {@link SongStart#removedEntry} per bar line removed, naming the line it was on and whether the removal moved a key, in the order of removal"
  - Fails because: it names callees (`effectiveRange`, `removeRange`, `setKey`), the order of steps ("first sets"), the sweep mechanism and the reason an internal step is silent.
  - Action: reduce to: "@effects a key change that sits behind a removed bar line is removed with it, and its key becomes the line's own key; where several bar lines stand first, the line's own key is that of the last key change removed. No other line's key moves and no key change is stranded" and "@effects a span with an endpoint on a removed element, such as an ending that starts on the bar line, is removed with it, and that removal is not recorded to {@code report}" and "@effects records to {@code report} one {@link SongStart#removedEntry} per bar line removed, naming the line it was on and whether the removal moved a key, in the order of removal"

### `Line.setKey` — src/main/java/songscribe/dom/Line.java:146
- Clause: "The key in effect on the lines that follow is {@link Song}'s to bring up to date, driven by that record."
  - Fails because: it names a collaborator and the mechanism instead of the outcome.
  - Action: reduce to: "The running key of the lines that follow is brought up to date with it."
- Clause: "@effects posts the {@link LineKeyChange} through {@link #applyChange}, under {@link Song#applyChange}'s bracket rule"
  - Fails because: it names the call that makes the change, not the state afterwards.
  - Action: reduce to: "@effects this line's own key is the normalized value; what is recorded, and what is thrown outside a bracket, is {@link Song#applyChange}'s promise"

### `LineRange` (record) — src/main/java/songscribe/dom/LineRange.java:22
- Clause: "{@link ChangeReport.Builder#build()} is the only producer."
  - Fails because: it names the type's producer (a collaborator).
  - Action: delete
- Clause: "@invariant {@code firstIndex <= lastIndex}; the record does not check it, because its only producer guarantees it"
  - Fails because: "because its only producer guarantees it" names a collaborator.
  - Action: reduce to: "@invariant {@code firstIndex <= lastIndex}; the record does not check it"

### `LyricRun.scriptsWithWordEndIn` — src/main/java/songscribe/dom/LyricRun.java:205
- Clause: "Across a range in which a word ends, what stands before it and what stands after it are different words, so no word may close up over it once it is gone."
  - Fails because: it describes what a reader (the deletion repair) does with the answer, not an obligation or outcome of this method.
  - Action: delete the whole Javadoc

### `LyricRun.backfillSyllabicAt` — src/main/java/songscribe/dom/LyricRun.java:288
- Clause: "Writes the element directly, outside any bracket — the caller is responsible for any required modification bracket."
  - Fails because: "Writes the element directly, outside any bracket" names the mechanism beside the caller's obligation.
  - Action: reduce to: "The caller supplies any modification bracket the write requires."

### `LyricRun.storesSameLyric` — src/main/java/songscribe/dom/LyricRun.java:421
- Clause: "{@code text} is never blank here: {@link #writeLyricForScript} routes a blank non-carrier syllable to {@link #removeLyricForScript} first."
  - Fails because: the part after the colon names the caller and its route.
  - Action: reduce to: "{@code text} is not blank."

### `LyricRun.LyricSpanEnd` — src/main/java/songscribe/dom/LyricRun.java:631
- Clause: "The end of a lyric span on one run, as {@link #lyricSpanEnd} finds it."
  - Fails because: "as lyricSpanEnd finds it" names the type's producer.
  - Action: reduce to: "The end of a lyric span on one run."

### `ModificationSession.inBracket` — src/main/java/songscribe/dom/ModificationSession.java:275
- Clause: "A body that throws closes through {@link #closeBracket}, so no rule can replace its exception with one of its own."
  - Fails because: "through closeBracket" names the callee that keeps the promise.
  - Action: reduce to: "A body that throws closes the bracket, and no rule can replace its exception with one of its own."

### `ParsedSong.settle` — src/main/java/songscribe/dom/ParsedSong.java:75
- Clause: "Runs every load pass over {@link #song}, and reports what the load changed. This is the only way to run the load passes, and it runs them once, immediately after the read and before the song is installed."
  - Fails because: "Runs every load pass" and "the only way to run the load passes" name the route and the collaborators; only the timing obligation and the report are for the caller.
  - Action: reduce to: "Settles {@link #song} and reports what the load changed. Call it once, immediately after the read and before the song is installed."
- Clause: "The passes run in this order, with mutation tracking suspended throughout: tuplets, endings, hairpins, breath marks, then the accidental stamp. The reader's entries are recorded after every pass, so the heading the reader's last entry stands under comes last in the report."
  - Fails because: it states the order of internal steps and a mechanism (tracking suspended, already covered as outcome by `@effects`); the caller relies only on the order of report entries.
  - Action: reduce to: "The report lists the tuplet entries first, then the ending, hairpin and breath-mark entries, then the reader's entries."
  - Derived-test coverage: `LoadPassesTest.testTheReportHoldsOneEntryForEachChangeTheLoadMade` asserts this exact entry order (tuplets updated, tuplets removed, endings, hairpins, breath marks, reader entry), so the reduced sentence must stay; deleting the paragraph would leave that case on `@return`'s "every entry the passes made, then readerEntries" alone, which does not order the pass entries.

### `RecordedChange` (record) — src/main/java/songscribe/dom/RecordedChange.java:24
- Clause: "One change the song made on its own, as a recorder hands it to a {@link ChangeReport.Builder} while an edit or a load runs."
  - Fails because: "as a recorder hands it to a ChangeReport.Builder" names collaborators.
  - Action: reduce to: "One change the song made on its own during an edit or a load, before its report is built."
- Clause: "A change names lines, never elements, and by {@link Line} object rather than index: line numbers are resolved only when the report is built ({@link ChangeReport.Builder#build()}), so a line added or removed later in the same edit does not make the entry wrong."
  - Fails because: it states structure and the resolution mechanism; the promise belongs to (and is stated on) `ChangeLines`.
  - Action: delete

### `RepeatSections` (class) — src/main/java/songscribe/dom/RepeatSections.java:32
- Clause: "Playback, the section rule of {@link Ending} and the lint rule for balanced repeats all read it, so they agree by construction."
  - Fails because: it names the type's readers.
  - Action: delete
- Clause: "A song whose first element is a {@code :|} or {@code :|:} (flagged by the {@code LEADING_BAR} lint rule, {@link SongStart}) closes the song-start section: a {@link Section} with a {@code null} opener, not unmatched, whose {@link #sectionStart} is the bar itself."
  - Fails because: the parenthesis names a collaborator (the lint rule and `SongStart`).
  - Action: reduce to: "A song whose first element is a {@code :|} or {@code :|:} closes the song-start section: a {@link Section} with a {@code null} opener, not unmatched, whose {@link #sectionStart} is the bar itself."

### `ReportedChange` (record) — src/main/java/songscribe/dom/ReportedChange.java:24
- Clause: "It holds no model object, so it can outlive any change to the song."
  - Fails because: "holds no model object" states structure as the mechanism behind the promise.
  - Action: reduce to: "It can outlive any change to the song."

### `Song.Script` — src/main/java/songscribe/dom/Song.java:110
- Clause: "The application holds a {@code Script}, never a lyric number, which exists only in a file; only {@code songscribe.io.LyricNumbers} converts between the two."
  - Fails because: "only songscribe.io.LyricNumbers converts between the two" names a collaborator.
  - Action: reduce to: "The application holds a {@code Script}, never a lyric number, which exists only in a file."

### `SongStart` (class) — src/main/java/songscribe/dom/SongStart.java:26
- Clause: "The rule that no bar line stands first in the song, and the one place it is stated."
  - Fails because: "the one place it is stated" is a fact about code organization, not about the rule a caller relies on.
  - Action: reduce to: "The rule that no bar line stands first in the song."
- Clause: "A removed bar line's key change moves onto the line: a bar line removed from the song start takes the mid-line key change behind it with it, and that key becomes the own key of the line ({@link Line#setKey}), so every element after the pair sounds in the key it did before."
  - Fails because: "({@link Line#setKey})" names the route.
  - Action: reduce to: "A removed bar line's key change moves onto the line: a bar line removed from the song start takes the mid-line key change behind it with it, and that key becomes the own key of the line, so every element after the pair sounds in the key it did before."
- Clause: "An edit cannot put a bar line first, and a load or a paste removes one; a deletion can still leave one, which the model accepts and the lint rule that reads {@link #leadingBar} flags."
  - Fails because: it names collaborators (edits, loads, pastes, the lint rule) and what they do.
  - Action: reduce to: "The rule is not an invariant of the model: a deletion can leave a bar line first."

### `Span.addTo` — src/main/java/songscribe/dom/Span.java:100
- Clause: "Adds this span to its line's span registry, first displacing whatever same-kind spans this kind's {@link #displacement()} says it may not coexist with."
  - Fails because: "first" states step order and "span registry" names the holder.
  - Action: reduce to: "Adds this span to its line, removing whatever same-kind spans this kind's {@link #displacement()} says it may not coexist with."
- Clause: "Each displacement is its own recorded mutation, emitted before this span's addition so undo restores the displaced spans after removing this one. Displacement is skipped while {@link Song#isReplaying()}: the recorded batch already carries the companion mutations, and re-deriving them would apply them twice."
  - Fails because: the first sentence states the emission order as the mechanism behind the undo promise (the record order is stated as outcome in `@effects`).
  - Action: reduce to: "Undo of the addition restores the displaced spans. While {@link Song#isReplaying()}, nothing is displaced: the recorded batch already carries the companion mutations, and re-deriving them would apply them twice."
- Clause: "An absorbing displacement widens this span's endpoints over the same-kind spans it absorbs before it is added, which is what a merge is."
  - Fails because: "before it is added" states step order.
  - Action: reduce to: "An absorbing displacement widens this span's endpoints over the same-kind spans it absorbs, which is what a merge is."
- Clause: "@effects posts one {@link SpanRemoval} per displaced span, then a {@link SpanAddition} for this span, through the line's song, and appends this span to the registry; under {@link Displacement#REPLACE_OVERLAPPING}, each displaced span is also reported, through {@link #removeReported} with {@link Strings#REPORT_REASON_REPLACED}; what is recorded, and what is thrown outside a bracket, is {@link Song#applyChange}'s promise"
  - Fails because: "through the line's song", "appends this span to the registry" and "through removeReported" name the route and the holder rather than the state afterwards.
  - Action: reduce to: "@effects records one {@link SpanRemoval} per displaced span, then a {@link SpanAddition} for this span; afterwards the line holds this span and none of the displaced ones; under {@link Displacement#REPLACE_OVERLAPPING}, each displaced span is also reported with {@link Strings#REPORT_REASON_REPLACED}; what is recorded, and what is thrown outside a bracket, is {@link Song#applyChange}'s promise"

Derived-test coverage: removal-before-addition record order, the replay skip and the merge widening all remain stated as outcomes.

### `Span.removeFrom` — src/main/java/songscribe/dom/Span.java:149
- Clause: "A caller that cannot cheaply tell whether an earlier step already removed the span — a sweep that doomed it, a merge that subsumed it, the paste reconciliation — may therefore call this unconditionally."
  - Fails because: the dashed list names collaborators.
  - Action: reduce to: "A caller that cannot cheaply tell whether an earlier step already removed the span may therefore call this unconditionally."
- Clause: "A removal the user did not directly request goes through {@link #removeReported}, which reports it and then calls this."
  - Fails because: "and then calls this" states the route of another method.
  - Action: reduce to: "A removal the user did not directly request goes through {@link #removeReported}, which also reports it."
- Clause: "@effects when this span is in the registry, posts a {@link SpanRemoval} through the line's song and removes the span; otherwise nothing. What is recorded, and what is thrown outside a bracket, is {@link Song#applyChange}'s promise"
  - Fails because: "through the line's song" names the route and "the registry" the holder.
  - Action: reduce to: "@effects when this span is on its line, records a {@link SpanRemoval} and removes the span from the line; otherwise nothing. What is recorded, and what is thrown outside a bracket, is {@link Song#applyChange}'s promise"

### `SpanLoadPass.run` — src/main/java/songscribe/dom/SpanLoadPass.java:44
- Clause: "Each pending span is judged in order by its kind's factory ({@link PendingSpan#spanning}) over the range from its anchor's index to its end's index in the anchor's line. A span the factory returns takes the pending span's two offsets as its {@link LineElement#getUserXOffsetSs()} and {@link LineElement#getUserYOffsetSs()}, and is added to that line with {@link Span#addTo}, which merges a hairpin with each hairpin of its kind already there that it overlaps or touches. One the factory refuses is dropped. Adding a span changes no element, so every later pending span's indices are the ones the reader built. Because the spans are judged in order and the factory sees the spans already added, of two pending endings that overlap or sit back to back, and of two pending hairpins of opposite kinds that share more than one element, the pass drops the later."
  - Fails because: it walks the body's steps and names the callee `Span#addTo` and its mechanism; the outcomes (well-formedness criterion, offsets kept, merge, which of two conflicting spans is dropped, indices as read) can be stated alone.
  - Action: reduce to: "A pending span is well formed when its kind's factory ({@link PendingSpan#spanning}) accepts its range, judged against the spans already added. An added span takes the pending span's two offsets as its {@link LineElement#getUserXOffsetSs()} and {@link LineElement#getUserYOffsetSs()}, and a hairpin merges with each hairpin of its kind already there that it overlaps or touches. Of two pending endings that overlap or sit back to back, and of two pending hairpins of opposite kinds that share more than one element, the pass drops the later. Every pending span's indices are the ones the reader built."

### `StaffElement.copyStateFrom` — src/main/java/songscribe/dom/StaffElement.java:105
- Clause: "This is the field list for the state a staff element holds itself; the base state travels through {@link #copyBaseStateFrom} and the subtype's through the {@link #copySubtypeStateFrom} this ends by calling, so each class owns exactly one list and none of the three can be reached without the other two. {@link #clone()} builds a bare element and copies through here rather than through a copy constructor of its own, so there is one field list per class and no second one to drift from it. Undo replay of an {@code ElementModification} restores in place through this method — a swap via {@code setElement} would leave stale references inside span records still sitting on the undo/redo stacks."
  - Fails because: it names callees, the order of calls and collaborators (`clone`, undo replay); only the reason identity is preserved (what breaks on the undo stacks) is type 3.
  - Action: reduce to: "Identity is preserved because undo replay restores an element in place through this method: a swap would leave stale references inside span records still sitting on the undo and redo stacks."

### `StaffElementRun.canReplaceElementAt` — src/main/java/songscribe/dom/StaffElementRun.java:237
- Clause: "Whether the element at {@code index} may be replaced in place by an element of type {@code incoming} — clicked through with another element in note entry, which swaps the new element in and keeps the index."
  - Fails because: the dashed part names a collaborator (note entry) and what it does.
  - Action: reduce to: "Whether the element at {@code index} may be replaced in place, keeping its index, by an element of type {@code incoming}."
- Clause: "Two things are refused whatever {@code incoming} is, both because they are half of a pair that only means anything whole: a <b>grace note</b>, which belongs to the host that follows it, and either half of a <b>key change and the barline it sits behind</b>, which {@link #effectiveBegin} deletes whole for the same reason."
  - Fails because: "which effectiveBegin deletes whole for the same reason" names a collaborator.
  - Action: reduce to: "Two things are refused whatever {@code incoming} is, both because they are half of a pair that only means anything whole: a <b>grace note</b>, which belongs to the host that follows it, and either half of a <b>key change and the barline it sits behind</b>."
- Clause: "A replacement by anything but a pitched note takes the paired grace note in front of it along, so the bar line would then stand where the grace note stood; {@link #effectiveBegin} is that reach."
  - Fails because: "effectiveBegin is that reach" names a collaborator.
  - Action: reduce to: "A replacement by anything but a pitched note takes the paired grace note in front of it along, so the bar line would then stand where the grace note stood."
- Clause: "@return {@code true} when note entry may write an element of type {@code incoming} over the element at {@code index}"
  - Fails because: "note entry" names a collaborator instead of the answer's meaning.
  - Action: reduce to: "@return {@code true} when an element of type {@code incoming} may replace the element at {@code index}"

### `TupletLoadPass.run` — src/main/java/songscribe/dom/TupletLoadPass.java:58
- Clause: "Each pending tuplet is judged leniently, under {@link TupletValidator.Strictness#LENIENT}: the beat barrier and the structural boundary constrain creation, not a document that already exists. A stated ratio is judged by {@link Tuplet#spanningStated}, and an absent one is derived by {@link Tuplet#spanning}, each at the beat in effect at the tuplet's anchor. A tuplet that passes takes the pending tuplet's offset as its {@link LineElement#getUserYOffsetSs()} and is added to its anchor's line. One that is refused is dropped."
  - Fails because: it walks the body's steps ("is added", "is dropped", both restated by `@effects`) and phrases the acceptance criterion as calls rather than as the outcome.
  - Action: reduce to: "Each pending tuplet is judged under {@link TupletValidator.Strictness#LENIENT}: the beat barrier and the structural boundary constrain creation, not a document that already exists. A stated ratio stands when {@link Tuplet#spanningStated} accepts it, and an absent one is the ratio {@link Tuplet#spanning} derives, each at the beat in effect at the tuplet's anchor. An added tuplet takes the pending tuplet's offset as its {@link LineElement#getUserYOffsetSs()}."

### `MeasureMapper.through` — src/main/java/songscribe/io/musicxml/MeasureMapper.java:1316
- Clause: "Every element read here and in {@link #at} sits on a line of the song."
  - Fails because: "and in {@link #at}" states a precondition of another method; this Javadoc should state only its own.
  - Action: reduce to: "Every element this reads sits on a line of the song."

### `ElementColumn.getLyric` — src/main/java/songscribe/layout/ElementColumn.java:315
- Clause: "Every later pass reads the lyric from the column, so it stays on the script the width was measured for."
  - Fails because: it describes what the type's readers do rather than the obligation it implies.
  - Action: reduce to: "Read the lyric from here rather than from the element, so it stays on the script the width was measured for."

### `HorizontalSpacingCalculator.graceLyricLeftOffsetSs` — src/main/java/songscribe/layout/HorizontalSpacingCalculator.java:545
- Clause: "Single source of truth for where a grace lyric sits: {@link LyricLayoutBuilder} adds it to the grace's X to place the box, and the extent methods below derive the neighbour reservations from the same formula."
  - Fails because: it names the method's readers and what they do with the result.
  - Action: delete
- Clause: "The reservation passes the ideal {@link #idealGraceHostUnionWidthSs}; the placement runs after the solve and passes the union actually produced."
  - Fails because: it describes what the callers pass and when they run.
  - Action: delete

### `HorizontalSpacingCalculator.GraceSyllable` — src/main/java/songscribe/layout/HorizontalSpacingCalculator.java:643
- Clause: "The widths are given rather than read off the grace so a caller can supply a per-script box width."
  - Fails because: it records a design decision against a rejected alternative ("rather than read off the grace").
  - Action: delete

### `InsertionSpacingCalculator.calculateFragmentInsertion` — src/main/java/songscribe/layout/InsertionSpacingCalculator.java:254
- Clause: "The solve reads the line in the keys the edit leaves it in."
  - Fails because: "the solve reads" names the internal step instead of the outcome.
  - Action: reduce to: "The line is measured in the keys the edit leaves it in."

### `LayoutResult.getLyricPlaceholder` — src/main/java/songscribe/layout/LayoutResult.java:337
- Clause: "The placeholder takes no space, and a renderer decides whether to draw it."
  - Fails because: "a renderer decides whether to draw it" names a reader of the result.
  - Action: reduce to: "The placeholder takes no space."

### `LayoutResult.Decoration.inkRectSs` — src/main/java/songscribe/layout/LayoutResult.java:571
- Clause: "For a {@link Sloped} tuplet bracket the box does not follow the tilt; the hit registry gives a tuplet its own shape."
  - Fails because: "the hit registry gives a tuplet its own shape" names a collaborator.
  - Action: reduce to: "For a {@link Sloped} tuplet bracket the box does not follow the tilt."

### `LyricLayoutBuilder.build` — src/main/java/songscribe/layout/LyricLayoutBuilder.java:107
- Clause: "The lyrics themselves come off the columns, which {@link ElementColumnBuilder} already resolved for that script and measured the cached syllable widths against, so text and widths cannot come from different scripts."
  - Fails because: it names the route and a collaborator (`ElementColumnBuilder`) where the caller needs only the precondition on `columns`.
  - Action: reduce to: "The lyrics come off {@code columns}, which must be built for {@code activeScript}."

Pass: `AccidentalChain.keyBefore` (type 3), `AccidentalScanView.keyBefore`, `ChainEdit.lineRekeyedAndReplaced`, `ChangeReport.Builder.record`, `ChangeReport.Builder.build`, `Line.lyricsYPosSs`, `Line.isSongStartAt`, `LyricRun.settleChainsAcross`, `LyricRun.replaceSyllabicAndCompound`, `LyricRun.firstLyricBearingIndex`, `LyricRun.previousLyricBearingIndex`, `LyricRun.previousTextBearingIndex`, `LyricRun.textBearingIndexAtOrAfter`, `LyricRun.hasFollowingTextBearingLyric`, `LyricRun.opensLyricSpan`, `LyricRun.lyricSpanEnd`, `LyricRun.adjustNeighborsForLyricDeletion`, `LyricRun.endDanglingWord`, `LyricRun.removeLyricForScript`, `LyricRun.moveHostSyllablesToGrace`, `LyricRun.setExtendForScript`, `LyricRun.scripts`, `NoteElement.copyArticulationsFrom`, `ParsedSong.lyricDroppedEntry`, `ProjectedEdit.inserting`, `ProjectedEdit.replacing`, `ProjectedEdit.walk`, `RepeatSections.Section`, `RepeatSections.of`, `RepeatSections.unclosedOpeners`, `RepeatSections.nestedOpeners`, `RepeatSections.sectionStart`, `Song.setActiveScript`, `SongStart.leadingBar`, `SongStart.keyBehind`, `Span.reportedAt`, `StaffElement.requireParentLine`, `StaffElement.getLyricForScript`, `StaffElement.getLyrics`, `StaffElement.removeLyricForScript`, `StaffElementRun.isSongStartAt`, `HitRegistry.hitRegion`, `LineIO.linesThatDroppedLyrics`, `LyricNumbers` (class), `LyricNumbers.toScript`, `MeasureMapper.dropOpenEnding`, `MeasureMapper.at`, `NoteMapper.MappedNote`, `NoteMapper.putLyrics`, `StaffElementIO.droppedLyric`, `ElementHitGeometry.elementHitRectSs`, `HorizontalSpacingCalculator.graceHostPairMelisma` (type 3), `InsertionSpacingCalculator.FragmentInsertionResult`, `InsertionSpacingCalculator.nextRunningKey`, `LayoutResult.elementHitRectSs`, `LayoutResult.getLyricBoxes`, `LayoutResult.lyricBoxRectSs`, `LyricLayoutBuilder.Result`, `LyricLayoutBuilder.graceHostColumn`, `LyricLayoutBuilder.graceUnionWidthSs`, `LyricLayoutBuilder.BoxPlacement`, `LyricLayoutBuilder.ExtenderState`. Remaining clauses of the members listed above with failures also pass.

## Caller-test audit: UI and lint layer

Scope: every Javadoc in the `added.txt` files of `prod-ui.txt`; in the other `prod-ui.txt`
files, every Javadoc the five commits added or changed (found by blame, by the declaration
after each changed Javadoc in the diffs, and by fuzzy match for text reflowed later); every
`@effects` clause. Current text judged throughout.

## lint

### `BalancedRepeatsCheck` (class) — src/main/java/songscribe/lint/BalancedRepeatsCheck.java:32
- Clause: "What opens, closes and nests a section is {@link RepeatSections}' rule, which playback reads too."
  - Fails because: "which playback reads too" names another reader of `RepeatSections`.
  - Action: reduce to: "What opens, closes and nests a section is {@link RepeatSections}' rule."
- Clause: "{@link Strings#LINT_REASON_REPEAT_NOT_OPENED} on each of {@link RepeatSections#unopenedClosers()}."
  - Fails because: it names the callee the check reads, not the bar it flags.
  - Action: reduce to: "{@link Strings#LINT_REASON_REPEAT_NOT_OPENED} on each closer with no open section."
- Clause: "{@link Strings#LINT_REASON_REPEAT_NESTED} on each of {@link RepeatSections#nestedOpeners()}."
  - Fails because: same: it names the callee instead of the bar.
  - Action: reduce to: "{@link Strings#LINT_REASON_REPEAT_NESTED} on each opener inside an open section."
- Clause: "{@link Strings#LINT_REASON_REPEAT_NOT_CLOSED} on each of {@link RepeatSections#unclosedOpeners()}."
  - Fails because: same: it names the callee instead of the bar.
  - Action: reduce to: "{@link Strings#LINT_REASON_REPEAT_NOT_CLOSED} on each opener never closed."
- Coverage: `BalancedRepeatsCheckTest`'s three rows (closer after its section closed; opener with no closer; opener inside an open section) are each stated by one reduced item.

### `LeadingBarCheck.check` — src/main/java/songscribe/lint/LeadingBarCheck.java:35
- Clause: "@return no violation when {@link SongStart#leadingBar} answers {@code null}; otherwise one violation with the heading {@link Strings#LINT_BAR_LEADING}, the description {@link Strings#REPORT_REASON_LEADING_BAR}, and a {@link LintMark.Flagged} mark on that bar line"
  - Fails because: the no-violation case is stated as what a callee returns, the route to the result.
  - Action: reduce to: "@return no violation when no bar line stands first in the song; otherwise one violation with the heading {@link Strings#LINT_BAR_LEADING}, the description {@link Strings#REPORT_REASON_LEADING_BAR}, and a {@link LintMark.Flagged} mark on that bar line"
- Coverage: the class sentence ("no bar line stands first in the song, as {@link SongStart#leadingBar} defines it") still supplies the definition behind `LeadingBarCheckTest`'s rows (six bar types, terminal only, bar after a note, empty first line).

### `Linter` (class) — src/main/java/songscribe/lint/Linter.java:28
- Clause: "The song lints when a {@link LintingNotification} for it is built; that class states the order this gives every reader of the report."
  - Fails because: it names the collaborator that triggers the run and the readers of the report.
  - Action: delete

### `LintMark` (class) — src/main/java/songscribe/lint/LintMark.java:26
- Clause: "Two consumers switch over this type, drawing ({@code LineInvariants}) and geometry ({@code LineSelectionHandler}), neither with a {@code default} branch, so a new kind fails to compile in both until it is drawn and placed."
  - Fails because: it names the readers of the type and how they are written.
  - Action: delete
- Clause: "Only a {@link Flagged} mark competes for its element's color, where playing, selection and hover each win over the lint color ({@code ColorPriority})."
  - Fails because: the parenthetical names the collaborator that keeps the promise.
  - Action: reduce to: "Only a {@link Flagged} mark competes for its element's color, where playing, selection and hover each win over the lint color."

### `LintMark.element` — src/main/java/songscribe/lint/LintMark.java:38
- Clause: "Held as the object, not an index, so the mark stays on its element when an edit moves it."
  - Fails because: "held as the object, not an index" is the mechanism beside the promise.
  - Action: reduce to: "The mark stays on this element when an edit moves it."

### `LintReport` (class) — src/main/java/songscribe/lint/LintReport.java:30
- Clause: "A state, not an event: {@link Linter} holds the current one and replaces it after every change to the song."
  - Fails because: it names the holder of the type; when the report is current is already given by the identity test in the next paragraph.
  - Action: reduce to: "A state, not an event."
- Clause: "A finding names an element, not an index, and may name one that an edit has since removed; a reader that resolves a position checks that the element is still in the song."
  - Fails because: "names an element, not an index" states structure; the obligation stands without it.
  - Action: reduce to: "A finding may name an element that an edit has since removed; a reader that resolves a position checks that the element is still in the song."

### `LintRule` (class) — src/main/java/songscribe/lint/LintRule.java:29
- Clause: "A lint rule only reads the song; a modification rule repairs it."
  - Fails because: the second half names another type, which no caller of `LintRule` needs.
  - Action: reduce to: "A lint rule only reads the song."

### `LintRule.Flag` — src/main/java/songscribe/lint/LintRule.java:54
- Clause: "The one statement of each flag; enforcing code links here."
  - Fails because: it names the readers of the type.
  - Action: delete

### `MissingLyricCheck` (class) — src/main/java/songscribe/lint/MissingLyricCheck.java:31
- Clause: "A <em>slot</em> is an element a syllable can go on ({@code StaffElement.canBearSyllable}): a note or a grace note, but not the host of a paired grace note, whose syllable is the grace note's."
  - Fails because: the parenthetical names the callee; the prose after the colon is the definition.
  - Action: reduce to: "A <em>slot</em> is an element a syllable can go on: a note or a grace note, but not the host of a paired grace note, whose syllable is the grace note's."
- Clause: "a lyric span covers it: a melisma, or a word whose next syllable stands on a later note, runs over it ({@link LyricRun#lyricSpanEnd}). A span crosses a line break and stops at a repeat;"
  - Fails because: the parenthetical names the callee that computes the span.
  - Action: reduce to: "a lyric span covers it: a melisma, or a word whose next syllable stands on a later note, runs over it. A span crosses a line break and stops at a repeat;"
- Clause: "a tie from the note before it reaches it ({@code NoteElement.incomingTie()}), so it continues a note already sung."
  - Fails because: the parenthetical names the callee.
  - Action: reduce to: "a tie from the note before it reaches it, so it continues a note already sung."
- Coverage: every case `MissingLyricCheckTest` can draw from the three conditions is still stated in the prose of each item.

### `UnbalancedFineCheck` (class) — src/main/java/songscribe/lint/UnbalancedFineCheck.java:34
- Clause: "{@link Strings#LINT_REASON_FINE_NOT_ON_BAR} on a Fine on an element that is not a barline or a repeat ({@code ElementType.isBar()} is {@code false})."
  - Fails because: the parenthetical restates "not a barline or a repeat" as the callee that decides it.
  - Action: reduce to: "{@link Strings#LINT_REASON_FINE_NOT_ON_BAR} on a Fine on an element that is not a barline or a repeat."

### `Violation` (record) — src/main/java/songscribe/lint/Violation.java:23
- Clause: "A violation has exactly one mark."
  - Fails because: it states what the record holds, which the record header shows.
  - Action: delete
- Clause: "The view shows the heading and description as they are and never interprets them."
  - Fails because: it names the reader; what the constructing caller needs is that the strings are final display text.
  - Action: reduce to: "The heading and description are display text, shown as given."

## message/notification

### `ActiveScriptDidChangeNotification` (class) — src/main/java/songscribe/message/notification/ActiveScriptDidChangeNotification.java:25
- Clause: "Posted by {@link Song#setActiveScript} when the song's active script changes."
  - Fails because: it names the poster; a subscriber needs only the event.
  - Action: reduce to: "The notification that the song's active script changed."

### `LintingNotification` (class) — src/main/java/songscribe/message/notification/LintingNotification.java:26
- Clause: "Its constructor lints the song, so every subscriber reads the current report whatever its priority."
  - Fails because: "its constructor lints the song" is the mechanism beside the promise.
  - Action: reduce to: "Every subscriber reads the current report, whatever its priority."

### `ZoomDidChangeNotification` (class) — src/main/java/songscribe/message/notification/ZoomDidChangeNotification.java:29
- Clause: "Posted by {@link songscribe.ui.ZoomController} to both request and report a zoom change."
  - Fails because: it names the poster.
  - Action: reduce to: "Both requests and reports a zoom change."

## ui

### `LintCallout` (class) — src/main/java/songscribe/ui/component/score/LintCallout.java:33
- Clause: "The tooltip manager shows a tooltip only for a mouse event, so this owns its own popup; {@link LintCalloutController} decides when it shows."
  - Fails because: the last clause names a collaborator.
  - Action: reduce to: "The tooltip manager shows a tooltip only for a mouse event, so this owns its own popup."

### `LintCalloutController` (class) — src/main/java/songscribe/ui/component/score/LintCalloutController.java:43
- Clause: "A callout hidden for any reason but a change of the selection is dismissed: this posts a {@link LintCalloutWasDismissedNotification}, so the panel clears the selection that showed it."
  - Fails because: "so the panel clears the selection" names the subscriber.
  - Action: reduce to: "A callout hidden for any reason but a change of the selection is dismissed: this posts a {@link LintCalloutWasDismissedNotification}."
- Clause: "Process-lifetime, as the {@link ScoreView} that owns it is, so it does not implement {@code songscribe.lifecycle.Disposable}."
  - Fails because: it names the holder and states the absent interface (structure); the lifecycle fact is only that it is never disposed.
  - Action: reduce to: "Process-lifetime; never disposed."

### `LintCalloutController.zoomDidChange` — src/main/java/songscribe/ui/component/score/LintCalloutController.java:89
- Clause: "Hides both tooltips before the zoom moves the items they point at."
  - Fails because: "hides both tooltips" restates the body; the fact a reader lacks is why the handler runs at `BEFORE_APPLY_PRIORITY`.
  - Action: reduce to: "Runs at {@link ZoomDidChangeNotification#BEFORE_APPLY_PRIORITY} so that both tooltips hide before the zoom moves the items they point at."

### `LintCalloutController.linting` — src/main/java/songscribe/ui/component/score/LintCalloutController.java:95
- Clause: "The tooltip manager asks for a new hover tooltip only when the pointer moves."
  - Fails because: it states a collaborator's behavior without the constraint it justifies.
  - Action: reduce to: "Both tooltips hide on a lint, because the tooltip manager asks for a new hover tooltip only when the pointer moves."

### `LintToolTipPlacement` (class) — src/main/java/songscribe/ui/component/score/LintToolTipPlacement.java:32
- Clause: "The hover tooltip and the callout are both placed here, so a mark's tooltip shows at the same place whichever of the two shows it."
  - Fails because: it names the two readers of the type.
  - Action: reduce to: "A mark's tooltip shows at the same place whether the hover tooltip or the callout shows it."
- Clause: "{@link #arrange} is the rule as arithmetic, with no Swing; {@link #place} reads the screen, builds the tooltip and measures it."
  - Fails because: it lists the steps of `place` and the internal split of the type.
  - Action: delete

### `ColorPriority` (enum) — src/main/java/songscribe/ui/renderer/ColorPriority.java:25
- Clause: "Do not reorder the constants unless a new priority is intended; {@link #decide} reads nothing but that order."
  - Fails because: "{@link #decide} reads nothing but that order" names the method that keeps the promise.
  - Action: reduce to: "Do not reorder the constants unless a new priority is intended."

### `GlyphRun` (record) — src/main/java/songscribe/ui/renderer/GlyphRun.java:31
- Clause: "A SMuFL line segment glyph tiled {@code count} times along one baseline, as a trill's wavy extension or the lint squiggle under a flagged annotation's text is drawn."
  - Fails because: the "as ..." tail names the readers of the type.
  - Action: reduce to: "A SMuFL line segment glyph tiled {@code count} times along one baseline."

### `ScreenScaleToolTipUI` (class) — src/main/java/songscribe/ui/ScreenScaleToolTipUI.java:33
- Clause: "Until a tooltip is in a window, this UI measures it under the graphics configuration of the component it belongs to."
  - Fails because: it is the mechanism that keeps the promise already stated in the purpose ("measured at the scale of the screen it appears on").
  - Action: delete

### `ToggleMessagesAction` (class) — src/main/java/songscribe/ui/action/ToggleMessagesAction.java:32
- Clause: "{@link MainFrame} must call {@link #setPanelShown} each time the panel is shown or hidden, and {@link #setStatus} each time the panel's status changes, which keeps the selected state, the tooltip and the icon current."
  - Fails because: it names the collaborator that meets the obligation; the obligation binds any caller.
  - Action: reduce to: "{@link #setPanelShown} must be called each time the panel is shown or hidden, and {@link #setStatus} each time the panel's status changes; this keeps the selected state, the tooltip and the icon current."

### `UIAction.linting` — src/main/java/songscribe/ui/action/UIAction.java:816
- Clause: "The notification's construction has already linted the song."
  - Fails because: it states a collaborator's behavior; the promise belongs to `LintingNotification`, which states it.
  - Action: delete the whole Javadoc

### `Fragment.withoutRedundantKeyChanges` — src/main/java/songscribe/ui/clipboard/Fragment.java:223
- Clause: "What goes with the key change is {@link StaffElementRun}'s to say, asked of this fragment as the run it is — so a pasted pair is resolved by the rule that resolves a deleted one, and neither has to be kept in step with the other by hand. In practice that is the barline in front of it, which {@link #capture} widens a captured range back over precisely so this fragment holds both."
  - Fails because: it names the callee that decides the pairing and the mechanism in `capture` that supplies it; the promise is only which element goes with the key change.
  - Action: reduce to: "A key change goes together with the element that deleting it would also remove, in practice the barline in front of it, so a pasted pair is resolved by the rule that resolves a deleted one."
- Clause: "The user did not ask to lose it, so the reduction names it in {@link Reduction#strandedSpans}, for the paste to report."
  - Fails because: "for the paste to report" names the reader of the result.
  - Action: reduce to: "The user did not ask to lose it, so the reduction names it in {@link Reduction#strandedSpans}."

### `Fragment.Reduction.withoutLeadingBars` — src/main/java/songscribe/ui/clipboard/Fragment.java:328
- Clause: "The drop names the key of the last key change it left out, for the paste to move onto the destination line ({@link SongStart#keyBehind})."
  - Fails because: it names the reader of the key and the callee that finds it.
  - Action: reduce to: "The drop names the key of the last key change it left out."

### `Fragment.LeadingBarDrop` (record) — src/main/java/songscribe/ui/clipboard/Fragment.java:359
- Clause: "@param movedKey the key the paste makes the destination line's own; {@code null} when the drop left out no key change"
  - Fails because: the first half states what a reader does with the key, not what the key is.
  - Action: reduce to: "@param movedKey the key of the last key change the drop left out; {@code null} when it left out none"

### `FragmentPaste.paste` — src/main/java/songscribe/ui/clipboard/FragmentPaste.java:51
- Clause: "{@link PasteSpanReconciliation#settle} states the rule."
  - Fails because: it names the callee that applies the rule.
  - Action: delete
- Clause: "@effects within the caller's bracket: removes the stranded key changes on every line the fragment's key reaches; removes each destination span the paste straddles, reported with {@link PasteSpanReconciliation.LostSpan#reasonKey}; deletes {@code target}'s range when it has one; inserts the fragment's elements at {@code target}'s insert index; records through {@link songscribe.dom.Song#report} one entry per span of {@link Fragment.Reduction#strandedSpans} and one per entry of {@link PasteSpanReconciliation.Settlement#reports}; adds every span of {@link PasteSpanReconciliation.Settlement#spansToAdd}"
  - Fails because: it names the call that records (`Song#report`) and the internal collaborator's outputs (`LostSpan`, `Settlement`), lists the changes in step order, and packs six clauses into one tag.
  - Action: reduce to:
    "@effects the stranded key changes are gone from every line the fragment's key reaches
    @effects each destination span the paste straddles is removed
    @effects {@code target}'s range, when it has one, is deleted, and the fragment's elements stand at {@code target}'s insert index
    @effects the open bracket's change report holds one entry per span of {@link Fragment.Reduction#strandedSpans}, one per destination span removed, and one per fragment span the paste leaves out
    @effects every fragment span the paste keeps is on {@code line}, as its kind's factory built it"
- Coverage: `PasteSpanReconciliationTest` reaches this method and asserts that kept spans are as their factory built them, carrying user state, and that refused spans are absent and reported with their reason. The last two reduced `@effects` state the presence and the entries; the reasons and the user state stay stated by the `@invariant`s of `PasteSpanReconciliation.settle` and `reconcile`, and the first sentence keeps "as one edit of the caller's open modification bracket".

### `PasteSpanReconciliation.settle` — src/main/java/songscribe/ui/clipboard/PasteSpanReconciliation.java:201
- Clause: "Refusing a span that was never added is also what keeps this rule free of any mutation of its own: there is nothing for the paste's bracket to record, so the paste's single undo step covers the refusal by construction."
  - Fails because: it is the mechanism behind "@effects none", and names the caller's bracket.
  - Action: delete

### `MainFrame.writeSong` — src/main/java/songscribe/ui/component/MainFrame.java:1094
- Clause: "Every save goes through here, so this is the one place a save is gated:"
  - Fails because: it names the callers and the structure around the method.
  - Action: reduce the sentence to: "When a finding carries {@link LintRule.Flag#BLOCKS_OUTPUT}, the user is first asked to confirm, Yes by default; where dialogs are suppressed the default is taken and the file is written."

### `ReportPanel` (class) — src/main/java/songscribe/ui/component/ReportPanel.java:81
- Clause: "the lint report is a state, the last {@link ChangeReport} an event that every {@link SongDidChangeNotification} and load replaces (an undo or redo carries an empty one)."
  - Fails because: it names the notification by which the rows update, the route, rather than when they change.
  - Action: reduce to: "the lint report is a state, the last {@link ChangeReport} an event that every edit, undo, redo and load replaces (an undo or redo leaves none)."
- Clause: "It lives for the process, so it registers with the bus for the process and is never disposed."
  - Fails because: "registers with the bus" is mechanism; the lifecycle fact is the lifetime.
  - Action: reduce to: "It lives for the process and is never disposed."

### `ReportPanel.lintRow` — src/main/java/songscribe/ui/component/ReportPanel.java:281
- Clause: "The finding is from the current lint report: every {@link LintingNotification} lints the song as it is built, and no handler changes the song before this panel reads the report."
  - Fails because: after the colon it names collaborators and how they make the precondition hold; the caller needs only the precondition.
  - Action: reduce to: "{@code finding} is from the current lint report."

### `LineSelectionHandler.hitTestMissingLyricViewPoint` — src/main/java/songscribe/ui/component/score/LineSelectionHandler.java:174
- Clause: "A \"?\" has no region in the hit registry: layout places a placeholder under every slot lacking a syllable, but the lint report decides which are drawn, and layout never reads it."
  - Fails because: it explains the route (why the registry is bypassed) by naming collaborators; no caller acts on it.
  - Action: delete

### `LineSelectionHandler.lintMarkAt` — src/main/java/songscribe/ui/component/score/LineSelectionHandler.java:211
- Clause: "The \"?\" comes first because it stands in the lyric row, and a lyric outranks every other target."
  - Fails because: "comes first" is the order of steps; the caller relies on which mark wins.
  - Action: reduce to: "When the point is on both a \"?\" and a region, the \"?\" wins, because it stands in the lyric row and a lyric outranks every other target."

### `ScoreView` (class) — src/main/java/songscribe/ui/component/ScoreView.java:116
- Clause: "Process-lifetime: {@link #init()} subscribes the view to the message bus and nothing ever unsubscribes it. The class is deliberately not {@code songscribe.lifecycle.Disposable}; there is nothing to release."
  - Fails because: it describes the subscription mechanism and the absent interface (structure) beside the lifecycle fact.
  - Action: reduce to: "Process-lifetime; never disposed."

### `ScoreView.zoomDidChangeApplyZoom` — src/main/java/songscribe/ui/component/ScoreView.java:1012
- Clause: "The sole handler that performs a zoom change."
  - Fails because: it states what the method does and a structural fact about the other handlers.
  - Action: delete

### `LineInvariants.drawnLyricPlaceholder` — src/main/java/songscribe/ui/renderer/LineInvariants.java:267
- Clause: "The one statement of where a \"?\" is drawn: the renderer draws by it, through {@link #lintOn}, and the pointer code hit-tests by it."
  - Fails because: it names the readers of the method.
  - Action: delete
- Clause: "Layout lays out the active script only, so a \"?\" stands only for a mark in the script the lyric row shows."
  - Fails because: "layout lays out the active script only" names a collaborator's behavior; the outcome is the null case.
  - Action: reduce to: "A \"?\" is drawn only for a mark in the script the lyric row shows."

### `LineInvariants.getElementColor` — src/main/java/songscribe/ui/renderer/LineInvariants.java:293
- Clause: "Does not include grace-cancel coloring (handled in LineRenderer)."
  - Fails because: the parenthetical names a collaborator.
  - Action: reduce to: "Does not include grace-cancel coloring."

### `LineInvariants.positionFacts` — src/main/java/songscribe/ui/renderer/LineInvariants.java:405
- Clause: "Returns a new, mutable set, for the caller to add its own playing and selection facts to."
  - Fails because: the tail names what one caller does with the set.
  - Action: reduce to: "Returns a new, mutable set."

### `AnnotationRenderer` (class) — src/main/java/songscribe/ui/renderer/AnnotationRenderer.java:37
- Clause: "An annotation that a lint finding names ({@link LineInvariants.ElementLint#annotationFlagged()}) keeps its own color and gets a squiggle in {@link ScoreView#getLintColor()}, its ink top {@value #SQUIGGLE_GAP_DOC_PX} document pixels below the text baseline."
  - Fails because: the parenthetical names the callee the renderer reads.
  - Action: reduce to: "An annotation that a lint finding names keeps its own color and gets a squiggle in {@link ScoreView#getLintColor()}, its ink top {@value #SQUIGGLE_GAP_DOC_PX} document pixels below the text baseline."

### `LyricTextRenderer` (class) — src/main/java/songscribe/ui/renderer/LyricTextRenderer.java:42
- Clause: "Renders per-element lyric syllable text beneath the staff, and the {@link LintMark.MissingLyric} \"?\" in the placeholder box {@link LineInvariants#lintOn} names, so the mark stands exactly where the missing syllable would."
  - Fails because: "the placeholder box {@link LineInvariants#lintOn} names" names the callee that supplies the box.
  - Action: reduce to: "Renders per-element lyric syllable text beneath the staff, and the {@link LintMark.MissingLyric} \"?\" exactly where the missing syllable would stand."

### `LyricEditor` (class) — src/main/java/songscribe/ui/component/LyricEditor.java:85
- Clause: "In-place lyric editor overlay parented to {@link ScoreView}, editing the active-script lyric of a single {@link StaffElement}."
  - Fails because: "parented to {@link ScoreView}" names the holder.
  - Action: reduce to: "In-place lyric editor overlay, editing the active-script lyric of a single {@link StaffElement}."
- Clause: "This is enforced by {@code DISABLE_WHEN_EDITING_TEXT} on every toolbar {@code UIAction}; {@code LyricEditorActionAuditTest} locks the whitelist."
  - Fails because: it names the mechanism that keeps the invariant and the test that checks it.
  - Action: delete

### `LyricChainEditor` (class) — src/main/java/songscribe/ui/component/LyricChainEditor.java:35
- Clause: "The hyphen-chain and melisma-chain operations an open {@link LyricEditor} session performs on one script of one {@link Line}."
  - Fails because: "an open {@link LyricEditor} session performs" names the collaborator that holds the type.
  - Action: reduce to: "The hyphen-chain and melisma-chain operations on one script of one {@link Line}."
- Clause: "Reads or writes only the model, so it can be exercised without a Swing component."
  - Fails because: the tail names a reader (the tests), not a caller fact.
  - Action: reduce to: "Reads or writes only the model."

### `PasteModeManager.acceptsInsertionIndex` — src/main/java/songscribe/ui/edit/PasteModeManager.java:59
- Clause: "Whether the fragment fits, the breath-mark rule and the song-start rule are decided by {@code tryInsertFragment} once a point is chosen, and their refusals are retryable."
  - Fails because: it names the callee that decides them.
  - Action: reduce to: "Whether the fragment fits, the breath-mark rule and the song-start rule are decided once a point is chosen, not here, and their refusals are retryable."

## undo

### `MutationReplayer.undoBatch` — src/main/java/songscribe/undo/MutationReplayer.java:69
- Clause: "Undoes the edit that recorded {@code mutations}, restoring {@code song} and its fonts to the state they held immediately before it: the records are inverted in reverse order, inside one {@link Song#replay}."
  - Fails because: after the colon it lists the steps and the callee.
  - Action: reduce to: "Undoes the edit that recorded {@code mutations}, restoring {@code song} and its fonts to the state they held immediately before it."
- Clause: "@effects posts the {@link songscribe.message.notification.SongDidChangeNotification} that {@link Song#replay} posts, carrying the inverse changes"
  - Fails because: it names the call that posts.
  - Action: reduce to: "@effects posts one {@link songscribe.message.notification.SongDidChangeNotification}, carrying the inverse changes"

### `MutationReplayer.redoBatch` — src/main/java/songscribe/undo/MutationReplayer.java:98
- Clause: "Redoes the edit that recorded {@code mutations}, restoring {@code song} and its fonts to the state they held immediately after it: the records are re-applied in forward order, inside one {@link Song#replay}."
  - Fails because: after the colon it lists the steps and the callee.
  - Action: reduce to: "Redoes the edit that recorded {@code mutations}, restoring {@code song} and its fonts to the state they held immediately after it."
- Clause: "@effects posts the {@link songscribe.message.notification.SongDidChangeNotification} that {@link Song#replay} posts, carrying the re-applied changes"
  - Fails because: it names the call that posts.
  - Action: reduce to: "@effects posts one {@link songscribe.message.notification.SongDidChangeNotification}, carrying the re-applied changes"

## SongScribe / util

### `SongScribe.buildMainWindow` — src/main/java/songscribe/SongScribe.java:197
- Clause: "Every process that builds the main window builds it here, including the end-to-end suite."
  - Fails because: it names the callers.
  - Action: delete

### `UIUtils.ToolTipSection` (record) — src/main/java/songscribe/util/UIUtils.java:170
- Clause: "All three are plain text: {@link #toolTipHtml} escapes them, so a {@code <}, {@code >} or {@code &} shows as itself."
  - Fails because: "{@link #toolTipHtml} escapes them" names the callee that keeps the promise.
  - Action: reduce to: "All three are plain text: a {@code <}, {@code >} or {@code &} shows as itself."

## Pass

Pass: BalancedRepeatsCheck.check, BalancedRepeatsCheck.Flag, LeadingBarCheck (class), LintCheck (class), LintCheck.SongOrderedViolations, LintFinding, LintMark.FlaggedAnnotation, LintMark.MissingLyric, LintRule.LEADING_BAR, LintRule.BALANCED_REPEATS, LintRule.MISSING_LYRIC, LintRule.UNBALANCED_FINE, LintRule.Flag.BLOCKS_EXPORT, LintRule.Flag.BLOCKS_PLAYBACK, MissingLyricCheck.check, UnbalancedFineCheck.check, LintCalloutWasDismissedNotification, LintMarkSelectionDidChangeNotification (class, constructor, getMark), ReportStatus, LintCallout.popup, LintCalloutController.calloutWindow, LintCalloutController.hideCallout, LintToolTipPlacement.Request, LintToolTipPlacement.arrange, LintToolTipPlacement.pushOnto, ColorPriority.HOVERED, GlyphRun.startingAt, GlyphRun.centeredOn, GlyphRun.draw, ScaledGlyph (record), ScaledGlyph.inkTopYSs, ScreenScaleToolTipUI.createUI, SongScribe.setBus, UIAction.enableFromPlaybackBlock, Fragment.without, Fragment.Reduction, Fragment.StrandedSpan, FragmentPaste.landedLines, PasteSpanReconciliation.reconcile (incl. its @effects), PasteSpanReconciliation.settle @effects, LyricEditor.activeScript, MainFrame.installDividerReleaseListener, MainFrame.placeDividerAtSavedWidth, MainFrame.showMessagesPanel, MainFrame.hideMessagesPanel, ReportPanel.BODY_LINE_SPACING, ReportPanel.showRows, ReportPanel.Row.location, ReportPanel.ChangeRow, ReportPanel.Position, ReportPanel.WrappingText, ReportPanel.WrappingText.setText, ReportPanel.ReportList, ReportPanel.ReportList.recomputeCellHeights, ReportPanel.CellRenderer, ReportPanel.CellRenderer.getPreferredSize, LineComponent.PointerMark, LineComponent.pointerMark, LineComponent.editableLyricElement, LineComponent.getToolTipText, LineComponent.getToolTipLocation, LineComponent.PointerHit, LineComponent.lintMarkAtPointer, LineSelectionHandler.hitTestViewPoint, LineSelectionHandler.hitRegionAtViewPoint, LineSelectionHandler.readyRegistry, LineSelectionHandler.markBoundsSs, LineSelectionHandler.lintMarkOf, LineSelectionHandler.sweepRange, ScoreView.getLintColor, ScoreView.init, ScoreView.requireLineComponent, ScoreViewController.SongChanges, ScoreViewController.landFragment, ScoreViewController.tryInsertFragment, PlayStopAction.updateEnabledState, LineInvariants.getActivelyEditedScript, LineInvariants.ElementLint, LineInvariants.lintOn, LineInvariants.colorFor, SelectionCoordinator.selectLyric, UndoController (class), UndoController.UndoStep, UndoController.redo, UIUtils.setToolTipText.
