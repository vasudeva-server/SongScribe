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
| 23 | Dialogs | `ui/dialog`, `ui/dialog/backend`, `ui/dialog/fontchooser` | ⏳ | *(undecomposed)* — was blocked on `dialog-redesign`, which has landed. |
| 24 | Actions | `ui/action` | ⏳ | *(undecomposed)* — 60 files. |
| 25 | Score components | `ui/component`, `ui/component/score`, `ui/component/toolbar` | ⏳ | *(undecomposed)* |
| 26 | UI shell | `ui`, `ui/menu`, `ui/platform`, `ui/playback` | ⏳ | *(undecomposed)* |
| 27 | MIDI | `midi` | ⏳ | |
| 28 | Export | `export` | ⏳ | |
| 29 | Converter | `converter`, `uiconverter` | ⏳ | Headless; a producer that reaches the model without the UI. |
| 30 | Leaf utilities | `util`, `error`, `shape`, `font`, `prefs` | ⏳ | Swept as encountered; a pass of their own only if one earns it. |

Legend: ⏳ not started · 🔄 in progress · ✅ complete · ⛔ blocked

## In flight

**Pass 0 — the key system**, on branch `776-key-signature`, last commit
`24febe64`. Its record is `plans/design-pass/keys.md`: what has been decided,
what each decision rests on, what is left in what order, and the two things no
mechanism has verified. Start there, not here.

There is no test suite to run (see *The order, and why*), and a passing compile
proves integration rather than correctness.

**Only `Key` itself has been through the regime**, and the pass is early rather
than nearly done. `Key` is now a 15-constant enum identified by its position on
the circle of fifths, `KeyType` and `KeyMapping` are deleted, and
`songscribe.io.LegacyKeyType` owns the `.mssw` tag pair. Everything else in the
key system — `Line`'s key state, `KeyChangeElement`, `KeySignature`, the
cautionary path, the edit path — is unread.

The next session starts by asking the user for the bugs they have in hand and
fixing those, then runs the architectural review the record's phase B describes.

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
- **→ Pass 23.** `ui/dialog/ResolutionDialog.form` is orphaned: no
  `ResolutionDialog` exists in Java anywhere in `src`, and the 9KB GUI-designer
  file was last touched by `6c849acd`, the pre-2.0 legacy import. Dead through
  the entire rewrite.

## Blockers

**None.**

`dialog-redesign` landed on `develop` and this branch already carries it: the
merge at `c91c9604` brought in `develop`'s tip `a558c9f7`, so no rebase is owed
and the `CLAUDE.md` conflict this section used to predict never arises. Verified
in the code rather than the log — `BaseDialog` exposes `getMainFrame()` and
`getData()` with no `getScoreView()`/`requireScoreView()`, and no legacy export
dialog remains in `ui/dialog/`.

Pass 23 and pass 0's `KeyChangeDialog` item are therefore both open.
