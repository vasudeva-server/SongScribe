# Design Pass — `keys`

Run by `/design-pass keys`.

**Status:** 🔄 in progress

Branch `776-key-signature`. Start commit `3c452cea`; last commit `24febe64`.

This record was backfilled at `24febe64`, after the `Key` enum work was already
committed, so its *Before* numbers are reconstructed from the start commit and
the per-step commit discipline begins with the remaining work.

**Only `Key` itself has been through the regime.** The first version of this
record marked steps 1 and 2 complete; they were not. The types it names were
reached by following `Key`'s call sites during the enum conversion, which is not
an inventory, and the unrepresentable-states step was credited to the whole
system on the strength of one type. Read *Remaining work* as the real state.

The steps below are the regime's checklist, not a running order. They are worked
in whatever order the session's target needs, and several are open at once —
which is why most of them sit at 🔄 rather than moving one at a time.

| Step | Status | Notes |
|---|---|---|
| 1 Inventory | ⏳ | **Never run.** The types below were reached through `Key`, not enumerated by a read of the key system. |
| 2 Unrepresentable states | 🔄 | `Key`, and `Line`/`Song` key resolution (group C item 1). `KeyChangeElement.previousKey()`'s three cases are still this step's subject and untouched. |
| 3 Extraction | 🔄 | Done where the enum work reached: `KeyMapping` dissolved, `LegacyKeyType` extracted, `signatureHeightSs()` pulled into `Key`. Not swept. |
| 4 Contracts | 🔄 | `Key` and `LegacyKeyType` done. Everything else outstanding. |
| 5 Test triage | ⏳ | No suite exists (see the register). Proposal owed before anything is written. |
| 6 Test-only surface | ⏳ | |
| 7 Compile and run | 🔄 | Both source sets compile. **The app has not been run**; nothing here is verified on screen. |
| 8 Diagrams | ⏳ | |
| 9 Coverage | ⏳ | Not reachable without a suite. |
| 10 Mutation | ⏳ | opportunistic |

## Numbers

| | Before | After |
|---|---:|---:|
| Test cases | 0 | 0 |
| Main LOC | 125072 | 125119 |
| Test LOC | 3451 | 3278 |
| Ratio | — | — |

Main files changed: 18 · Guards retired: 3 · Contracts written: 9 · Elapsed: in
progress

*Before* is `3c452cea`. Test cases are 0 at both ends because the suite was
removed wholesale before this pass; the net test LOC change is three base classes
restored less thirteen support classes archived, not deleted tests. The three
guards retired are `Key`'s `keyType`-iff-`accidentalCount` invariant, which is
now unrepresentable, `altersPitchClass`'s `NONE` early return, which a zero-trip
loop answers, and `KeyType.glyph()`'s `NONE` throw, which went with the type.

## Domain contracts confirmed

One line per proposal that went to a checkpoint, and what was decided.

- **`Key`'s identity is one signed number, not a `(KeyType, count)` pair.** The
  circle-of-fifths position carries type and count together, so no pair has to be
  held consistent. Confirmed against the objection that a two-argument `of()`
  factory is not materially different from the constructor it replaces.
- **`KeyType` is deleted, not kept alongside.** Each of its ten consumers was a
  two-way branch on the sign.
- **Constants are named by accidental count, declared in fifths order.**
  `SEVEN_FLATS … NO_ACCIDENTALS … SEVEN_SHARPS`, so `ordinal()` tracks the value.
  Accepted that this reorders the key combo the user sees; tonic naming stays in
  `KeyDisplay`, never on a domain constant.
- **`DEFAULT` survives as the one alias**, because it states a policy — the key a
  song starts in — rather than a signature.
- **`Key.ofFifths` throws `IllegalArgumentException`; it does not return
  `@Nullable`.** The range is stated once by the type that owns it, and each
  document reader converts the refusal into its own corruption error. The first
  attempt returned null, which is silent failure and skipped the guard and
  `@Nullable` questions `~/.claude/guides/design.md` requires — corrected.
- **A negative `.mssw` `<keys>` count is checked in `LegacyKeyType`, not in
  `Key`.** Not the same range twice: `FLATS × -3` is `+3`, a valid key, so
  `ofFifths` cannot see the error. Count-is-a-magnitude is a format fact; the
  magnitude's limit is a domain fact.
- **The song's opening key stays on line 0; `Song` holds no key.** Proposed and
  rejected: a non-null `Song.startingKey` seeding resolution. The argument for it
  was making resolution total by construction, but that is reachable with the key
  on line 0 by answering `Key.DEFAULT` when nothing declares one, so the move
  bought nothing and cost seven call sites. **`Line.inheritedKey` still goes**,
  replaced by an `IdentityHashMap<Line, Key>` on `Song`: that half of the item
  was never about where the opening key lives. Implemented — group C item 1.
- **Both legacy readers normalize `(NONE, n)` and `(FLATS, 0)` to C major.**
  `LineIO` used to refuse the load while `SongIO` normalized. Refusing a file the
  old program opened is wrong for a read-only migration path.

## Triage outcome

Kept: — · Rewritten: — · Discarded: — · Added: —

No tests exist to triage. **Step 5 is a gate, not a formality:** the test list is
proposed and approved before anything is written.

## Coverage

Not reachable without a suite. Two things are unverified by any mechanism and
must not be reported as done:

- The **key combo's new fifths ordering** and its glyphs have not been seen on
  screen. The compiler cannot check that `KeyCellRenderer`'s Private Use Area
  codepoints are the right glyphs, only that they are valid characters.
  `plans/ui-dialog-interface.md` Phase 10 task 4 is where this gets looked at,
  along with all four key-change gestures. Do not schedule a second manual run
  for it.
- **`MusicXmlCorpusGenerator`'s four updated call sites** compile but have not
  been run; `scripts/generate-corpus.sh` cannot verify itself because its gate,
  `MusicXmlCorpusLosslessnessTest`, is archived until pass 16.

## Findings raised

Anything surfaced that was not this target's to fix. Findings belonging to a
later pass are in the register's *Carry-forward findings*; these are the ones
noted in passing and left alone.

- **`.mssw` has a live writer**, `SongIO.writeSong` → `LineIO.writeLine`, while
  `CLAUDE.md` describes the format as legacy read-only. It exists for the
  `generateCorpus` build tool. Pass 17 owns the contradiction.
- **`StaffHeaderMetrics.accidentalInkBboxSs` is named for a bbox but returns only
  its width.** Pass 3 (staff geometry).

## Remaining work

`Key` itself is settled; most of the rest of the key system has not been read.

**This is a work list, not a sequence.** The groups below sort the work by where
it came from, not by when to do it — redesign and bug fixes interleave, and a
session takes whatever is in front of it. Where one item genuinely cannot start
until another lands, the item says so and names what blocks it; those are the
only orderings that hold. Everything else is open, and finding that one item
reframes another is the normal outcome of doing the work, not a planning failure.

**Ask the user what they want to look at when a session starts.** Their bugs are
behaviour watched going wrong rather than shape visible in the code, so no amount
of reading surfaces them.

### Group A — bugs the user names

Record each one here as it is named, with what it turned out to be.

1. **Cautionary key changes at the end of a line render incorrectly.** A real
   bug, seen by the user. Two symptoms:
   - **The leading barline is missing.** A cautionary should be preceded by a
     barline, as a mid-line key change is — see *A mid-line key change is always
     preceded by a barline* in `docs/key-signatures.md`.
   - **The spacing is wrong.**
   Whether these are one fault or two is unknown: an unreserved barline would
   also throw off the run's width, so the spacing may be a consequence rather
   than a separate defect.
   The path is `LayoutHitTester.hitTestCautionaryKeyEdit`,
   `KeySignatureRenderer.renderKeyChange`, and the layout reservation that pairs
   with them — the same three the group B cautionary bullet names, so whichever
   is done first covers the other.
   Related: group C item 3, the doubled accidental run, sits in two of those
   three files.

### Group B — the architectural review of the key framework

Steps 1 and 2 done over the whole system rather than over `Key`. Known starting
points, not a complete list — the point of the read is to find what is not on it,
and it does not have to happen in one pass or before anything else:

- ~~**`Line`'s key state.**~~ Settled by group C item 1: `getRunningKey()` no
  longer exits, and `inheritedKey` is gone. `getKey()` is still nullable, which
  is the representation the design keeps deliberately.
- **`KeyChangeElement.previousKey()`** resolves through three cases, one of which
  is a null parent line falling back to `NO_ACCIDENTALS`.
- **`KeySignature`** is a layout box in `dom` holding a `Key`, measured by `Key`.
  Whether it should exist at all is a question for this read.
- **The cautionary path** — `LayoutHitTester.hitTestCautionaryKeyEdit`,
  `KeySignatureRenderer.renderKeyChange`, and the layout reservation that pairs
  with them.
- **How a key edit reaches the model** — but not the routing, which group C item
  4 settles: after it, all four gestures resolve on `KeyChangeDialogController`
  and `changeLineKey`/`insertKeyChange` sit there rather than on
  `ScoreViewController`. What is left for this read is what those two methods
  *do* — the fit calculation, the accidental reconciliation, the restatement
  prompt and the implicit barline — and whether two commit routes plus item 6's
  third one are the right decomposition.

### Group C — items this pass surfaced along the way

These came out of the enum conversion incidentally, except where an item names
another source. Group B's read may still
dissolve or reframe any of them, which is a reason to re-read an item before
starting it, not a reason to hold it back.

1. ✅ **`Line`/`Song` key resolution.** *Done.* `Line.getRunningKey()` called
   `RuntimeError.exit()` — a model getter that terminated the application. It was
   reachable only when line 0 established no key, which the class invariant
   forbids; the getter had nothing to return and so killed the process.

   Implemented as described below. Not verified: there is no test suite, and the
   app has not been run.

   An earlier version of this item moved the song's opening key onto
   `Song.startingKey` and left line 0 inheriting like any other line, to make
   resolution total *by construction* rather than by invariant. **That was
   dropped.** The exit and the where-does-the-key-live question turned out to be
   independent: resolution can be made total with the key still on line 0, by
   answering `Key.DEFAULT` when no line declares one — which is not masking a
   broken invariant but the real answer for a document that names no key, and is
   already what `rebuildInheritedKeysAfterParsing` does. Moving the key to `Song`
   would have rewritten working machinery (`repairLineZeroKey`,
   `getStartingKey`) for no behavioural gain, at the cost of seven call sites
   that write a line's key and two live bugs it would have introduced — MIDI
   export stops emitting the opening key signature, and `KeyChangeDialog` opens
   the first line's key as an *add* rather than an *edit*.

   **State.** Line 0 owns its key, as it does today. `Line.key`, `@Nullable`, is
   the only per-line key state: non-null means the line establishes its own key,
   null means it inherits. `Song` holds no key of its own;
   `Song.getStartingKey()` stays the query it already is.

   **Resolution.** `Song` owns an `IdentityHashMap<Line, Key>` holding the
   resolved key **at each line's start**, replacing `Line.inheritedKey`. Patched
   incrementally: a mutation walks forward from the line it touched and stops at
   the first line whose own key is non-null, since that line's entry cannot have
   moved and so nothing past it can either. `Song.runningKeyAt(line)` is the
   total query, answering `Key.DEFAULT` when nothing declares a key rather than
   exiting. `Line.getRunningKey()` stays as the per-line delegate — eight callers
   read it as a line property — and `Line.keyAt` bottoms out there.

   The map costs a hash lookup where the field cost a field read. That is noise:
   the caller is `StaffElement.findEffectiveAccidental`'s fallback, which reaches
   it through `keyAt`'s own backward scan over the line, so the path is already
   an O(n) walk per note.

   **Line 0.** `repairLineZeroKey` stays and keeps its current behaviour: a line
   inserted at the top takes the key the song was already in, so the song sounds
   identical across the insert. Confirmed explicitly — `Key.DEFAULT` there would
   silently transpose the whole song.

   **Deleted** `inheritedKey`, both its accessors, and the exit.
   `propagateInheritedKeysFrom` and `rebuildInheritedKeysAfterParsing` survive in
   shape, rewritten to fill the map instead of per-line fields. `LineKeyChange`
   kept its shape, and `KeyChangeDialog`, `LineIO`, `MeasureMapper`, `SongIO` and
   `LineTrackBuilder` were untouched.

   **Two reachability leaks found and fixed while wiring it up.** A deleted
   line's entry is dropped in `maintainKeyInvariant`, so a line out of the song
   cannot report what it inherited from a position it no longer occupies, and the
   map cannot outlive the line's `LineDeletion` record.
   `rebuildInheritedKeysAfterParsing` clears the map first, because
   `Song.loadFrom` replaces the line list with a bare `lines.clear()` and nothing
   else would have removed the discarded lines' entries.

   **`docs/key-signatures.md` updated** with a "Where the inherited key is
   stored" section; the stopping rule and "There is no song-wide key" both stay
   true.
2. **Root A — a position in a line is a type, not a `(Line, int)` pair.**
   `Line.keyAt`, `Line.getElement`, `KeyChangeElement.needsBarlineBefore`,
   `InsertionPointMode.Client`, `LayoutResult.findInsertionIndex`, and
   `KeyChangeDialogController`'s mid-line entry point, which carries the line,
   the index and whether that index names an existing signature. (Item 4 deletes
   `KeyChangeDialog.KeyChangeInput`, which is where this pair used to sit; the
   pair does not go away, it moves onto the controller.)
   **Open question, decide with the user:** whether it extends or displaces
   `ElementLocation`, which already exists as `(int lineIndex, int elementIndex)`
   bound to no `Song`, with a `matches(int, int)` whose arguments transpose
   silently. `ElementLocation`'s own consumers are hover highlighting, so that
   half reaches pass 4.
3. **The doubled accidental run.** `LayoutHitTester.hitTestCautionaryKeyEdit` and
   `KeySignatureRenderer.renderKeyChange` each build the run twice — once for the
   list, once inside `Key.widthSsFrom`. Introduced when `totalWidthSs(List)` was
   removed; negligible cost, real duplication.
4. **`KeyChangeDialog` onto a controller and `DialogOps`.** **Owned by
   `plans/ui-dialog-interface.md` Phase 5 — read it there, not here.** That
   section carries the shape and the task list, written against the tree as it
   stands. Nothing about this item is restated in this file, because two copies
   drift and this one already had.

   Two things this file adds. **Item 5 waits on this one, and Phase 5 answers
   what item 5 was waiting to learn:** the controller resolves the opening key as
   a value before the dialog exists, reading the clicked `KeyChangeElement`'s own
   key directly for a mid-line binding rather than asking `Line.keyAt` at the
   bound index. `currentChoiceFor` goes, and the inclusive bound loses its last
   caller.

   **Item 6 is the defect Phase 5 leaves standing**, and Phase 5's ⚠ is where the
   evidence for it is.
5. **The doubled backward walk in accidental resolution.** *Read done, gated on
   the user, nothing implemented.* The read changed what this item is: most of
   the optimization is already in the tree, and what remains is a defect in
   `Line.keyAt`'s bound rather than a duplicated walk.

   **Already done, not by this item.** The barrier-is-a-`KeyChangeElement` case
   short-circuits today — `StaffElement.findEffectiveAccidental` reads the key
   off the barrier instead of asking `keyAt`. Two of the item's three bullets
   were written as though it did not.

   **The real finding: `keyAt`'s inclusive bound is the wrong shape.** `keyAt(i)`
   answers "the key in effect at `i`, counting a key signature sitting *on* `i`".
   Of its five callers, four want the key in effect *before* `i`, and three write
   `i - 1` to get it — `KeyChangeElement.previousKey`,
   `KeyEditFitCalculator.appendInsertedColumns`, `MeasureBuilder.buildLine`. The
   fourth, `StaffElement.keyInEffectAt`, passes `index` raw.

   **That fourth one is a live bug.** On the status-bar preview path the index is
   an *insertion* index posted by `PreviewElementManager.trackMouse`, so hovering
   at the position of a mid-line key change reports the previewed note's
   accidental from the key it is about to precede rather than the key it would be
   in. (The earlier note here blamed a projected index from
   `AccidentalReconciliation`; that resolver has its own scan and never calls
   `findEffectiveAccidental` with anything but an element's own index. The
   preview path is the one that does.)

   **Proposed, not yet decided — make `keyAt` exclusive:** *the key in effect
   immediately before `elementIndex`*. Domain stays `0..elementCount()`.
   `keyAt(0)` is `getRunningKey()` by definition rather than by appeal to the
   position invariant. Three `- 1`s disappear, `keyInEffectAt` becomes correct
   unchanged, and the exhausted-scan shortcut becomes provably equivalent instead
   of carrying a caveat.

   **The new `Line` query is not needed.** With an exclusive bound,
   `keyAt(scanIndex)` equals `keyAt(index)` on the barline-barrier path, because
   the scan has already proved no key change lies between them. Resolve at
   `scanIndex`; add nothing.

   **The tie escape still needs `keyAt`, behind a flag.** After an escape the
   span `(anchorIndex, scanIndex)` is never visited, so exhaustion no longer
   proves the line holds no key change. A local `escaped` boolean gates the
   `getRunningKey()` shortcut.

   **Blocked on C4.** `KeyChangeDialog.currentChoiceFor` is the only caller
   relying on the inclusive bound, and it relies on it to serve
   edit-this-signature and insert-one-here *without telling them apart*. Nothing
   the dialog holds can stand in — `showFor` derives `op` from
   `line.getKey() != null`, which is about the line's own key — so under an
   exclusive bound the dialog would have to ask the line whether a
   `KeyChangeElement` sits at the bound index, reaching into the model to recover
   what its caller already knew. C4 removes the question rather than answering
   it: the controller takes a separate entry point per gesture, because only the
   caller can tell an existing signature from a new one, and hands the dialog a
   `Key`. Start this item once C4 has landed; `keyAt` then has no caller wanting
   the inclusive bound.

   **This item does not fix item 6.** `keyAt`'s bound decides what the dialog
   opens on; `insertKeyChange` decides what OK writes.

   **Also found:** `LyricRun.getElement` is contracted as `/** The element at
   {@code index}. */` with no range, no `@return` and no `@throws`. `Line`'s
   implementation is a bare `elements.get(index)`, so out-of-bounds throws by
   inheritance from the field's type rather than by promise. Any caller guarding
   `elementCount()` is guessing. Fix alongside `keyAt`'s contract; the two are
   read together.
6. **Nothing can change an existing mid-line key signature's key.** Surfaced by
   the `ui/dialog` read, not by the enum conversion. Double-clicking a mid-line
   key signature opens the dialog on that signature's own key and then *inserts a
   second signature in front of it*. The score reads `♯♯♯ ♭♭`; the second
   signature has the last word, so the music from there on stays in the old key
   and the edit reads as clutter that did nothing. No stray barline appears —
   `insertKeyChange` adds one only where the position does not already follow
   a barline, and an existing signature always does. `KeyChangeElement.setKey`
   has no caller outside its own class.

   It silently damages documents, so it is a fix rather than a cleanup. It needs
   a third commit route, which is why item 4 cannot absorb it:

   - `changeMidLineKey(Line, int elementIndex, Key)` on
     `KeyChangeDialogController`, alongside `changeLineKey` and
     `insertKeyChange`, which item 4 moved there. It reconciles the
     accidentals the key move affects, raises the one restatement prompt, changes
     the element's key in place inside one modification bracket, and **re-spaces
     the line**, because the new signature may be wider or narrower than the old.
   - a `KeyEditFitCalculator.midLineKeyChangeSwapFits(…)` variant. The existing
     `midLineKeyChangeFits` measures a line with a column *added*, which is the wrong
     measurement for a swap.
   - `KeyChangeDialogController.editKeyChange`, item 4's entry point for this
     gesture, routed to the new commit instead of to `insertKeyChange`.

   Call it ~200 lines of new domain code, none of it a variant of what item 4
   writes. Item 5 does not fix it, and no test covers it — what guards the
   current behaviour is `insertKeyChange`'s contract, which states that it
   inserts.

## Commits

| Commit | What |
|---|---|
| `3c452cea` | Start of pass (naming rule stated, register rebuilt). |
| `aca3e1be` | Test base classes restored; `src/test` reduced to infrastructure. Does not compile alone — carries `Key` enum call sites. |
| `24febe64` | `Key` becomes a fifths-keyed enum; `KeyType` and `KeyMapping` deleted; `LegacyKeyType` added. |
