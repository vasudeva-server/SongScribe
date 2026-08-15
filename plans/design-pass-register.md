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
| 0 | **Keys** | `dom`: `Key`, `KeyType`, `KeySignature`, `KeyChangeElement`; plus `layout`, `io/musicxml`, `ui` | 🔄 | Branch `776-key-signature`. **Settles key state wherever it lives, including in `Song` and `Line` — a pass fixes what it reaches, it does not hand its own findings forward.** |
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

**Next: `Key` becomes a closed enum.** Decided, not started. It is a record whose
domain is exactly 15 values, enumerated twice — once in the compact
constructor's two guards and once in `buildAllSignatures()`. As an enum an
8-sharp key is unwriteable rather than rejected, both guards delete, and
`allSignatures()` becomes a view over `values()` in declaration order.
`keyType()` and `accidentalCount()` stay as accessors, so no call site changes
shape.

**The constants are named by accidental count** — decided:

```
NO_ACCIDENTALS
ONE_FLAT … SEVEN_FLATS
ONE_SHARP … SEVEN_SHARPS
```

with `DEFAULT` becoming `FIVE_FLATS`, and the `C_MAJOR` constant added in
`2ea1c471` renamed to `NO_ACCIDENTALS`.

This is the naming rule in `docs/key-signatures.md` applied, not an exception to
it. The record's own components are `keyType` and `accidentalCount`; there is no
mode and no tonic in the type, so "five flats" *is* the value's identity, stated
exactly as the type defines it. A tonic name like `D_FLAT_MAJOR` would put a
display interpretation on a domain constant, and tonic naming already belongs to
`KeyDisplay`, which owns the `FLAT_TONICS` / `SHARP_TONICS` tables. Prose may
still say "C major" where it explains what a value means musically — that is
explanation, not identity.

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
- **→ Test rebuild.** `ui/dialog/SongSettingsDialogFixture` survived the reset as
  infrastructure but all three of its consumers are gone. Kept, not justified.

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
