# Design Pass — `keys`

Run by `/design-pass keys`.

**Status:** 🔄 in progress

Branch `776-key-signature`. Start commit `3c452cea`; last commit `24febe64`.

This record was backfilled at `24febe64`, after the `Key` enum work was already
committed. Steps 1–4 below are recorded from what the work actually did rather
than captured live, so their *Before* numbers are reconstructed from the start
commit and the per-step commit discipline begins with the remaining items.

| Step | Status | Notes |
|---|---|---|
| 1 Inventory | ✅ | `Key`, `KeyType`, `KeySignature`, `KeyChangeElement`, plus reach into `layout`, `io`, `io/musicxml`, `midi`, `ui`. |
| 2 Unrepresentable states | ✅ | The pass's centre of gravity — see *Domain contracts confirmed*. |
| 3 Extraction | ✅ | `KeyMapping` dissolved; `LegacyKeyType` extracted; `signatureHeightSs()` pulled into `Key`. |
| 4 Contracts | 🔄 | `Key` and `LegacyKeyType` done. `Line`/`Song` key resolution outstanding — item 1 below. |
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

In order. All of it is key-system work, so none defers to a later pass.

1. **`Line`/`Song` key resolution.** `Line.getRunningKey()` calls
   `RuntimeError.exit()` at `Line.java:346` — a model getter that terminates the
   application. Agreed replacement: `Line.ownKey` as the only key state; `Song`
   owning resolution in an `IdentityHashMap<Line, Key>` rebuilt wholesale rather
   than patched; `Song.runningKeyAt(line)` as the total query; `Line.keyAt`
   staying put and bottoming out there; `detach()` materialising the running key
   so a detached line answers for itself. Deletes `inheritedKey`, both its
   accessors, `propagateInheritedKeysFrom`, `rebuildInheritedKeysAfterParsing`
   and the exit. `LineKeyChange` keeps its shape.
   **`docs/key-signatures.md` is rewritten in the same change** — its "There is
   no song-wide key" and "The inheritance chain and its stopping rule" sections,
   and the ASCII table, all describe `inheritedKey` and go with it.
   **Ask whether this needs an architecture gate before starting**: it moves
   resolution across a seam and changes what `detach()` promises.
2. **Root A — a position in a line is a type, not a `(Line, int)` pair.**
   `Line.keyAt`, `Line.getElement`, `KeyChangeElement.needsBarlineBefore`,
   `InsertionPointMode.Client`, `LayoutResult.findInsertionIndex`,
   `KeyChangeDialog.KeyChangeInput`.
   **Open question, decide with the user:** whether it extends or displaces
   `ElementLocation`, which already exists as `(int lineIndex, int elementIndex)`
   bound to no `Song`, with a `matches(int, int)` whose arguments transpose
   silently. `ElementLocation`'s own consumers are hover highlighting, so that
   half reaches pass 4. Item 1 may settle it, since `Line.keyAt` is on both lists.
3. **The doubled accidental run.** `LayoutHitTester.hitTestCautionaryKeyEdit` and
   `KeySignatureRenderer.renderKeyChange` each build the run twice — once for the
   list, once inside `Key.widthSsFrom`. Introduced when `totalWidthSs(List)` was
   removed; negligible cost, real duplication.
4. **`KeyChangeDialog` to the back-end pattern.** Unblocked — `dialog-redesign`
   has landed and this branch carries it, so `DialogBackEnd` and
   `AttachmentBackEnd` are in their final shape to migrate against.

## Commits

| Commit | What |
|---|---|
| `3c452cea` | Start of pass (naming rule stated, register rebuilt). |
| `aca3e1be` | Test base classes restored; `src/test` reduced to infrastructure. Does not compile alone — carries `Key` enum call sites. |
| `24febe64` | `Key` becomes a fifths-keyed enum; `KeyType` and `KeyMapping` deleted; `LegacyKeyType` added. |
