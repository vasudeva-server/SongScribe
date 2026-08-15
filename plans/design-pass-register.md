# Design pass register

**Type:** Register · long-lived  <br>
**Created:** 2026-08-14  <br>
**Status:** In progress

Which systems have been through the design regime in `~/.claude/guides/design.md`,
in what order the rest are taken, and what a finished pass found that a later
pass owns.

## What this file is not

**It does not name findings in advance.** A design pass is not plannable in
detail, because each decision determines whether the next item exists at all —
dissolving `KeyChange` into `Key` deleted a review target that a prescriptive
plan had listed. The predecessor of this file tried to name findings per phase;
ten of its specifics were wrong, stale or superseded within one pass, while the
only part that held up was the ordering, because ordering is a dependency fact
rather than a prediction.

It carries no file lists and no task lists. `/design-pass <target>` owns the
procedure; the record it keeps at `plans/design-pass/<target>.md` owns the
detail of one system.

## The order, and why

**Each system is reviewed after whatever it takes its types from, and before
whatever consumes it.** A pass changes the types its consumers use, so a consumer
reviewed first is reviewed against types that are about to move.

Two consequences worth stating once:

- **Test rebuild is per system, as that system settles.** The unit suite was
  removed wholesale (tag `pre-test-reset`, archive worktree
  `../SongScribe-tests-archive`); tests come back only where the design cannot
  carry the promise, proposed before they are written.

  **What stays in `src/test` is the base classes and only what they need.**
  `UnitTest`, `E2ETest` and `MainFrameMockTest`, plus the four helpers they
  import — `dom/StaffElementFactory`, `error/RuntimeErrorTestHelper`,
  `message/MessageCenterTestHelper`, `ui/action/MockEnvHelper` — which cannot
  move up beside the bases because they reach package-private internals of the
  packages they sit in. Separately, `io/musicxml/MusicXmlCorpusGenerator` and its
  two supports (`MusicXmlRoundTripSupport`, `io/XmlFixtures`) stay because they
  are the `generateCorpus` build tool (`build.gradle.kts:258`,
  `scripts/generate-corpus.sh`), not tests. Everything else — 13 per-package
  support classes, and 19 `package-info.java` files left carrying `@NullMarked`
  for packages with no code — went to the archive. Every remaining package has a
  `package-info.java`; `error`, `message` and `io/musicxml` had never had one, so
  NullAway was not enforcing non-null defaults over their helpers until now.

  **Restore by role, never by filename.** The reset in `2ea1c471` deleted every
  `*Test.java`, and all three base classes are named that way, so it took them
  along with the 486 real tests and left their dependants behind uncompilable.
  Note also that `scripts/generate-corpus.sh` verifies through
  `MusicXmlCorpusLosslessnessTest`, which is archived: the script's generate half
  works, its verify half does not, until pass 16 rebuilds that test.
- **Docs are corrected inside the pass that invalidates them**, never in a
  trailing phase. A doc left wrong while the code moves is how it got wrong.

## The unit is a system, not a package

**A pass covers one system — a set of types that only make sense together — not
whatever a package directory happens to contain.** `dom` is one directory and
about ten systems; a single pass over it would be too large to hold, and its
findings would be too tangled to attribute.

The tell that a row is really several: its types have separate invariants,
separate documents in `docs/`, and separate reasons to change. `Song`, `Line`,
spans and ties share a package and share nothing else.

**Systems are named from a read, not from file names.** The rows below are
decomposed only where a pass has already read the code. A row still marked
*(undecomposed)* is a directory awaiting that read — splitting it now from
directory listings would be predicting boundaries before seeing them, which is
the error this file exists to avoid.

## Register

| # | System | Where | Status | Notes |
|---|---|---|---|---|
| 0 | **Keys** | `dom`: `Key`, `KeySignature`, `KeyChangeElement`; plus `layout`, `io`, `io/musicxml`, `midi`, `ui` | 🔄 | Branch `776-key-signature`. **Settles key state wherever it lives, including in `Song` and `Line` — a pass fixes what it reaches, it does not hand its own findings forward.** |
| 1 | Units and scale | `dom`: `Ss`, `DocPx`, `ViewPx`, `ScaleContext` | ⏳ | `docs/unit-conversion.md`, `docs/zoom.md`. Leaf; nothing in the model depends on more than these. |
| 2 | Glyph registry | `smufl` | ⏳ | Leaf. |
| 3 | Staff geometry | `engraving` | ⏳ | Depends on `smufl` and units. |
| 4 | Durations and element types | `dom`: `Duration`, `ElementType`, `ElementLocation` | ⏳ | The vocabulary every element is built from. |
| 5 | Elements | `dom`: `StaffElement`, `LineElement`, `StructuralElement`, `Clef`, `Articulation`, `ArticulationType`, `AccidentalBounds` | ⏳ | |
| 6 | Line | `dom`: `Line`, `ElementChange`, `ProjectedElements` | ⏳ | Everything except key state, which pass 0 settles. |
| 7 | Spans | `dom`: `Span`, `SpanBound`, `SpanLookup`, `SpanOutcome`, `Beam`, `Crescendo`, `Diminuendo`, `Hairpin`, `Ending`, `EndingValidationResult`, `Trill`, `Tuplet`, `TupletValidator`, `TupletLoadPass`, `SlideZone` | ⏳ | One framework with many members; anchored to elements, owned by `Line`. |
| 8 | Ties | `dom`: `Tie` | ⏳ | Its own beast — cross-line, pitch-validated, invalidated by edits at both ends. |
| 9 | Lyrics | `dom`: `Lyric`, `LyricRun`, `DetachedLyricRun` | ⏳ | `docs/lyrics.md`. |
| 10 | Attachments | `dom`: `Attachment`, `AttachmentRemoval`, `Annotation`, and the five `*Attachment` types | ⏳ | |
| 11 | Tempo and beat | `dom`: `Tempo`, `TempoResolver`, `SongTempoMark`, `BeatAt`, `BeatChange` | ⏳ | |
| 12 | Song | `dom`: `Song`, `SongMetadata`, `Attribution*`, `ModificationSession`, `TerminalMaintainer` | ⏳ | The large model. Decouple from everything above it as far as it will go. |
| 13 | Mutations | `message/mutation` | ⏳ | Shapes follow the model. |
| 14 | Message bus | `message`, `message/notification`, `message/command` | ⏳ | *(undecomposed)* |
| 15 | Undo | `undo`, `lifecycle` | ⏳ | Replays mutations. |
| 16 | MusicXML I/O | `io/musicxml` | ⏳ | The live boundary — converts into domain types. |
| 17 | Legacy I/O | `io` | ⏳ | Read-only migration path. |
| 18 | Layout | `layout`, `layout/stacking`, `hit` | ⏳ | *(undecomposed)* — visibly several systems: columns, spring spacing, lyric layout, beams, accidentals, hit testing, the layout result, geometry primitives. Split on the read. |
| 19 | Rendering | `ui/renderer` | ⏳ | *(undecomposed)* |
| 20 | Selection | `ui/selection` | ⏳ | |
| 21 | Clipboard | `ui/clipboard` | ⏳ | |
| 22 | Edit modes | `ui/edit` | ⏳ | |
| 23 | Dialogs | `ui/dialog`, `ui/dialog/backend`, `ui/dialog/fontchooser` | ⛔ | *(undecomposed)* — blocked, see *Blockers*. |
| 24 | Actions | `ui/action` | ⏳ | *(undecomposed)* — 60 files. |
| 25 | Score components | `ui/component`, `ui/component/score`, `ui/component/toolbar` | ⏳ | *(undecomposed)* |
| 26 | UI shell | `ui`, `ui/menu`, `ui/platform`, `ui/playback` | ⏳ | *(undecomposed)* |
| 27 | MIDI | `midi` | ⏳ | |
| 28 | Export | `export` | ⏳ | |
| 29 | Converter | `converter`, `uiconverter` | ⏳ | Headless; a producer that reaches the model without the UI. |
| 30 | Leaf utilities | `util`, `error`, `shape`, `font`, `prefs` | ⏳ | Swept as encountered; a pass of their own only if one earns it. |

Legend: ⏳ not started · 🔄 in progress · ✅ complete · ⛔ blocked

## In flight

Pass 0 — the key system — on branch `776-key-signature`, last commit `2ea1c471`.
There is no test suite to run (see *The order, and why*), and a passing compile
proves integration rather than correctness.

**Done: `Key` is a closed enum, and `KeyType` is gone.** Not merely a record
turned into an enum — the pass found that `Key`'s identity is *one signed
number*, its position on the circle of fifths, so the `(KeyType, count)` pair had
no reason to exist. A two-argument lookup would have deleted the record's guards
and re-added them in a factory; a one-argument one has no inconsistent pair to
reject. `KeyType`'s ten consumers were each a two-way branch on the sign.

Constants are named by accidental count, in fifths order —
`SEVEN_FLATS … ONE_FLAT, NO_ACCIDENTALS, ONE_SHARP … SEVEN_SHARPS` — so
`ordinal()` tracks the value and the key combo reads in circle-of-fifths order,
the change one place the user sees. `DEFAULT` survives as the single alias,
because it states a policy rather than a signature.

What fell out, all of it recorded in `docs/key-signatures.md`:

- **`KeyMapping` dissolved.** Its stated reason to exist was owning the sign
  convention in one place; the convention is now `Key.fifths()`. `toFifths`
  became an accessor call and `toKey` became `Key.ofFifths` plus the reader's own
  range check. `midi` no longer depends on `io/musicxml`.
- **`songscribe.io.LegacyKeyType`** now owns the three `.mssw` `<keytype>` names
  and converts both ways. They were persisted `dom` enum constant names: renaming
  one would have stopped old files loading, with nothing in the build to catch
  it.
- **`Key.altersPitchClass` lost its `keyType.ordinal() - 1` table indexing** and
  the comment warning that reordering `KeyType` would silently corrupt it.
- **`Key.signatureHeightSs()`** joins `signatureWidthSs()`, so `KeySignature` no
  longer reaches for the glyph itself and header and cautionary cannot disagree
  on height either.

Behaviour deliberately changed, both in the legacy read path:

- **`LineIO` and `SongIO` disagreed about the same corrupt pair** — `(NONE, 3)`
  and `(FLATS, 0)`. `SongIO` normalized both to C major; `LineIO` refused the
  load. Now both normalize, which is what the pair always sounded and drew as,
  and refusing it in a read-only migration path was the wrong half of the
  disagreement.
- **`SongIO.endElement10` parsed `<keys>` with a bare `Integer.parseInt`**, so a
  corrupt v1.0 count threw `NumberFormatException` out of the SAX handler instead
  of becoming a `ParseError`. It now goes through `parseIntOrThrow` like every
  other scalar, which cost `getSong()` a `throws SAXException` that `SongLoader`
  already catches.

**Then, in order, the rest of pass 0.** All of it is key-system work, so none of
it defers to a later pass — the key system reaches into `Line`, `Song`, `layout`
and `io/musicxml`, and leaving any of it behind is tech debt that outlives the
reason it was understood.

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
2. **Root A — a position in a line is a type, not a `(Line, int)` pair.**
   `Line.keyAt`, `Line.getElement`, `KeyChangeElement.needsBarlineBefore`,
   `InsertionPointMode.Client`, `LayoutResult.findInsertionIndex`,
   `KeyChangeDialog.KeyChangeInput`. Undecided: whether it extends or displaces
   `ElementLocation`, which already exists as `(int lineIndex, int elementIndex)`
   bound to no `Song`, with a `matches(int, int)` whose arguments transpose
   silently. `ElementLocation`'s own consumers are hover highlighting, so that
   half of the question reaches pass 4 (durations and element types).
3. **The doubled accidental run.** `LayoutHitTester.hitTestCautionaryKeyEdit` and
   `KeySignatureRenderer.renderKeyChange` each build the run twice — once for the
   list, once inside `Key.widthSsFrom`. Introduced when `totalWidthSs(List)` was
   removed; negligible cost, real duplication.
4. **`KeyChangeDialog` to the back-end pattern** — blocked, see *Blockers*.

Passes 5, 6 and 12 then cover what is left of the elements, `Line` and `Song`:
everything that is not key state.

## Carry-forward findings

Facts a completed or in-flight pass established that a later pass owns. Nothing
speculative belongs here, and nothing that the *current* pass could fix belongs
here either — a finding inside a pass's own reach is fixed in that pass, while
the reason for it is still in hand.

- **→ Pass 30.** `strings.properties` has `dialog.song.settings.year` above
  `dialog.converter.converting`, breaking the within-group alphabetical order the
  strings guide requires.

## Blockers

**Pass 23 (dialogs) blocks on `dialog-redesign` landing on `develop`.**
`BaseDialog` lost `getScoreView()` and `requireScoreView()` there before five
dialogs were migrated off them, and another session is deleting three legacy
export dialogs with their actions and menu entries and migrating
`PreferencesDialog`. Reviewing dialogs here first means doing it twice, against a
`BaseDialog` that is about to change.

Order: `dialog-redesign` lands on `develop`, this branch rebases onto it
(`/update-branch`), then pass 23 runs. **Expect one conflict, in `CLAUDE.md`:**
keep this branch's *Key signatures* required-reading row and take the `.claude/`
paths, which both sides now agree on. `d67f064a` will not drop out silently
despite already being on `develop`, because develop's copy of that row was
removed when it was cherry-picked.
