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

It carries no file lists and no task lists. `/design-pass <row number>` owns the
procedure; what it keeps in `plans/design-pass-<pass>/` — a record and one
findings document per reviewed step, `<pass>` being the row number — owns the
detail of one system.

**A row number is the only way to start a pass.** `/design-pass 1` takes row 1,
resolving its *Where* column into the target. Work that is not on this table gets
a row before it gets a pass, which is what keeps the ordering above true rather
than aspirational.

## The record is working memory

**A pass's record is deleted when the pass completes.** It exists to survive a
context clear while the work is open, and nothing else. Once the pass is done,
git holds what it said, `docs/` holds the design it produced, and a record left
behind is a second account of the system that drifts from the first.

Two things must leave the record before it goes: anything a **later** pass owns,
which moves to *Carry-forward findings* below, and anything about the system's
**shape**, which belongs in `docs/` and is written there inside the pass that
learned it. A finding that is neither — resolved, or this pass's own — leaves
with the file.

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
  packages they sit in. Separately, `io/musicxml/MusicXmlRoundTripSupport` and
  `io/XmlFixtures` stay because `MusicXmlTempoReadTest` imports them and
  `specs/184b-page-setup.md` plans further use. Everything else — 13 per-package
  support classes, and 19 `package-info.java` files left carrying `@NullMarked`
  for packages with no code — went to the archive. Every remaining package has a
  `package-info.java`; `error`, `message` and `io/musicxml` had never had one, so
  NullAway was not enforcing non-null defaults over their helpers until now.

  **Restore by role, never by filename.** The reset in `2ea1c471` deleted every
  `*Test.java`, and all three base classes are named that way, so it took them
  along with the 486 real tests and left their dependants behind uncompilable.

  **There is no MusicXML losslessness gate, and no pass restores one.** The
  `.mssw` corpus it consumed, and the generator that built that corpus, went with
  the legacy write path. Both are reachable only from git history now. A pass that
  wants such a gate builds its songs in memory and round-trips them through
  MusicXML; it does not restore `MusicXmlCorpusLosslessnessTest`.
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

**A split suffixes; it never renumbers.** When a read decomposes row 14, it
becomes 14a, 14b, 14c, and every row below keeps the number it had. **A row
number is permanent identity.** *Carry-forward findings* are tagged with it by
passes that are finished and cannot be asked what they meant, so renumbering
would re-point every one of those tags at the wrong system. A suffixed row that
splits again suffixes again: 14b1, 14b2.

## Register

| #  | System | Where | Status | Notes |
|----|---|---|---|---|
| 0  | **Keys** | `dom`: `Key`, `KeySignature`, `KeyChangeElement`; plus `layout`, `io`, `io/musicxml`, `midi`, `ui` | ✅ | On `develop`. Settled key state wherever it lived, including in `Song` and `Line`. The design it arrived at is `docs/key-changes.md`. |
| 1  | Units and scale | `dom`: `Ss`, `DocPx`, `ViewPx`, `DocumentScale`; plus `font`, `util` | ✅ | On `develop`. The document scale is a compile-time constant, so staff spaces and document pixels cannot diverge. The size-rounds-up / position-rounds-nearest rule is carried by the sealed `PixelDistance` over `DocPx` and `ViewPx`, with `Ss` outside it by construction. Text measurement is collected into `font.TextMeasurement`, the one place toolkit pixels cross into staff spaces and the one measuring instrument; `MyFontUtils` dissolved into four `font` classes and was deleted. The design it arrived at is `.claude/guides/spatial-units.md` and `docs/zoom.md`. |
| 2  | **Glyph registry** | `smufl` | ✅ | Every lookup is total. A font that cannot answer fails the application, so no caller holds a glyph whose measurements are unknown. Stem anchors are asked for by `StemmedNotehead` rather than by glyph, which is what makes the one genuinely partial measurement total. The registry now holds only glyphs the application draws. Outside `smufl`, an element type declares its own appearance, so which glyph an element is drawn with cannot be asked of a type that has no answer, and the barline and repeat stroke sequence is stated once instead of in six places. The design it arrived at is `smufl/package-info.java`. |
| 3  | Staff geometry | `engraving` | ✅ | Every stroke width is base × named multiplier, with no bare literal standing in for one. `EngravingConstants` collects the LilyPond base thickness and everything derived from it that has no single owner; `StemMetrics`, `BeamMetrics`, `LedgerLine`, `StaffPosition` and `BarStroke` are new types that each own one measurement, replacing `LineThickness` and `SMuFLConstants`, both dissolved. `StaffPosition` carries its own `MIN`/`MAX` range and clamps into it, so the two callers that used to check a raw `int` cannot skip the check. The accidental-advance rule — ink width plus natural kerning — moved off `StaffHeaderMetrics` onto `Key.DrawnAccidental` in `dom`, which is total over its own value and its neighbour's rather than partial in its contract. The ledger-line threshold, stated twice before, is now `LedgerLine.forEachOffsetSs`, read by both its callers. The design it arrived at is `engraving/package-info.java`. |
| 4  | Durations and element types | `dom`: `Duration`, `ElementType`, `ElementLocation` | ⏳ | The vocabulary every element is built from. |
| 5  | Elements | `dom`: `StaffElement`, `LineElement`, `StructuralElement`, `Clef`, `Articulation`, `ArticulationType`, `AccidentalBounds` | ⏳ | Everything except key state, which pass 0 settled. |
| 6  | Line | `dom`: `Line`, `ElementChange`, `ProjectedElements` | ⏳ | Everything except key state, which pass 0 settled. |
| 7  | Spans | `dom`: `Span`, `SpanBound`, `SpanLookup`, `SpanOutcome`, `Beam`, `Crescendo`, `Diminuendo`, `Hairpin`, `Ending`, `EndingValidationResult`, `Trill`, `Tuplet`, `TupletValidator`, `TupletLoadPass`, `SlideZone` | ⏳ | One framework with many members; anchored to elements, owned by `Line`. |
| 8  | Ties | `dom`: `Tie` | ⏳ | Its own beast — cross-line, pitch-validated, invalidated by edits at both ends. |
| 9  | Lyrics | `dom`: `Lyric`, `LyricRun`, `DetachedLyricRun` | ⏳ | `docs/lyrics.md`. |
| 10 | Attachments | `dom`: `Attachment`, `AttachmentRemoval`, `Annotation`, and the five `*Attachment` types | ⏳ | |
| 11 | Tempo and beat | `dom`: `Tempo`, `TempoResolver`, `SongTempoMark`, `BeatAt`, `BeatChange` | ⏳ | |
| 12 | Song | `dom`: `Song`, `SongMetadata`, `Attribution*`, `ModificationSession`, `TerminalMaintainer` | ⏳ | The large model, less key state, which pass 0 settled. Decouple from everything above it as far as it will go. |
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
| 23 | Dialogs | `ui/dialog`, `ui/dialog/fontchooser` | ⏳ | *(undecomposed)* — takes the package as the finished `ui-dialog-interface` track left it. That track deleted `ui/dialog/backend`, so this pass never sees it. |
| 24 | Actions | `ui/action` | ⏳ | *(undecomposed)* — 60 files. |
| 25 | Score components | `ui/component`, `ui/component/score`, `ui/component/toolbar` | ⏳ | *(undecomposed)* |
| 26 | UI shell | `ui`, `ui/menu`, `ui/platform`, `ui/playback` | ⏳ | *(undecomposed)* |
| 27 | MIDI | `midi` | ⏳ | |
| 28  | Leaf utilities | `util`, `error`, `shape`, `font`, `prefs` | ⏳ | Swept as encountered; a pass of their own only if one earns it. |

Legend: ⏳ not started · 🔄 in progress · ✅ complete · ⛔ blocked

## Carry-forward findings

Facts a completed or in-flight pass established that a later pass owns. Nothing
speculative belongs here, and nothing that the *current* pass could fix belongs
here either — a finding inside a pass's own reach is fixed in that pass, while
the reason for it is still in hand.

- **→ Pass 18.** What is drawn for a grace note today is an ordinary
  `noteheadBlack` at a reduced font size, not `noteheadBlackSmall` — so
  Bravura's `noteheadBlackSmall` stem anchors do not belong to the glyph the
  notehead actually uses. The open question is whether grace notes should be
  drawn with `noteheadBlackSmall` at all, which is a change to the notehead,
  its flag and its accidentals together, not a one-line fix. The grace stem
  anchor is derived by scaling in two places, `NoteGeometry.GRACE_STEM_UP_SE`
  and `ElementType.computeGraceNoteBoundsSs`, with nothing making them agree.
  `StemmedNotehead.BLACK_SMALL` and `SMuFLGlyph.NOTEHEAD_BLACK_SMALL` are
  deleted, so re-adding them is part of that future change rather than a
  prerequisite for it.
- **→ Pass 4.** `ElementLocation.matches(int lineIndex, int elementIndex)` takes
  two same-typed parameters that transpose silently. Pass 0 built `KeyChangeSite`
  for the same shape — a place in a line, stated by whoever resolved it — but only
  for key changes. Whether the general type extends or displaces `ElementLocation`
  is the open question; `ElementLocation`'s own consumers are hover highlighting.
- **→ Pass 4.** Inside `ElementType`, each constant now declares an
  `ElementAppearance` — a sealed type over `NoteheadAppearance`,
  `GlyphAppearance`, `BarAppearance` and `KeySignatureAppearance` — supplied
  through the constructor, so the mapping is total by construction and the
  bounds computation is one exhaustive switch with no lookup that can miss.
  Open: whether `voltaOpeningXOffsetSs` and `voltaClosingXOffsetSs` belong on
  `ElementType` at all, or whether an element type owing layout the position
  of a volta bracket's tick is a dependency that should point the other way.
- **→ Pass 6.** `StaffElementRun.getElement` is contracted as *"The element at
  `index`."* — no range, no `@return`, no `@throws`. `Line`'s implementation is a
  bare `elements.get(index)`, so out of bounds throws by inheritance from the
  field's type rather than by promise, and any caller guarding on `elementCount()`
  is guessing.
- **→ Pass 28.** `strings.properties` has `dialog.song.settings.year` above
  `dialog.converter.converting`, breaking the within-group alphabetical order the
  strings guide requires.
- **→ Pass 23.** `ui/dialog/ResolutionDialog.form` is orphaned: no
  `ResolutionDialog` exists in Java anywhere in `src`, and the 9KB GUI-designer
  file was last touched by `6c849acd`, the pre-2.0 legacy import. Dead through
  the entire rewrite.
- **→ Pass 28.** `Prefs.resetAll()` has no production caller and is the only
  producer of `PrefsKey.ALL`; delete both, and the `key == PrefsKey.ALL`
  branches in `BaseDialog.GeometryResetSubscriber.prefsDidChange` and
  `ScoreViewController.prefsDidChange` that exist only to catch it.
  `removeObsoleteKeysForTest`/`removeSystemDefaultKeysFromStoreForTest`/
  `writeTypedForTest`/`migrateForTest` and the unflagged `getRawStored`/
  `putRawStored` are visibility relaxations over a load pipeline welded to
  `Prefs`'s constructor — extract a `PrefsStore`, constructed from a path, that
  the pipeline runs against, so a test can build one directly.
- **→ Pass 28.** `RecentDocumentsManager.resetForTest`/`reloadForTest` are
  deleted — both had zero callers once the test vault was retired. What remains
  is the shaping: `loadFromPrefs`'s logic (stored strings in, existing paths
  out) wants to be a static `readRecents` function, not a constructor step. The
  extraction is still there, now with its test justification removed from the
  Javadoc rather than acted on.
- **→ Pass 25.** `MainFrame.clearStartupErrorsForTest` and
  `PreviewElementManager.resetOverlaysForTest` are deleted, along with seven
  more dead members on `PreviewElementManager` and two on
  `PreviewOverlayRegistry`. The state they reached is untouched and still
  process-global: extract a `StartupErrorQueue` that `MainFrame` owns an
  instance of; move overlay ownership onto `OverlayHost`/`ScoreView` so a
  discarded view takes its overlays with it.
- **→ Pass 25.** `PreviewElementManager` subscribes through
  `PreviewElementManager.initialize()`, called from `MainFrame.initFrame()`
  beside `Actions.initialize` and `PlaybackController.initialize`, rather than
  from a `static {}` block. Bus wiring no longer depends on class-load order,
  and a headless conversion — which builds a score view but never hovers — does
  not subscribe a preview handler at all.
- **→ Pass 26, then Pass 23.** `PreferencesDialog`'s instrument cache
  (`ensureInstrumentsLoaded`, `getInstrumentStrings`, `getInstrumentPrograms`,
  `resetInstrumentsForTesting`) is the open synthesizer's state, living on a
  dialog instead of on `MidiController` — move it there as `getInstruments():
  List<Instrument>` over `record Instrument(String name, int program)`,
  populated when the synthesizer opens and cleared when it closes;
  `PreferencesDialog` becomes the caller. `programToIndex`'s `0` fallback for
  an unrecognized program is an arbitrary default that is also a legitimate
  index — a guard, not a value — worth fixing in the same move.
  `MidiController.failForTesting` and the `openMidi()` branch reading it are
  deleted; `MidiController.synthesizer` (`public static volatile`) is over-visible
  on the same class — written only inside `MidiController` and read by
  `PreferencesDialog.ensureInstrumentsLoaded`, which this pass retires anyway.
  `PreferencesDialog.resetInstrumentsForTesting` is deleted, but the cache it
  reset is untouched and still lives on the dialog.
- **→ The `converter` rewrite.** The fatal-error path assumes a display.
  `RuntimeError.exit` routes unconditionally through
  `OptionDialogs.showErrorMessageWithString`, i.e. a modal `JOptionPane`, so any
  entry point without a screen attempts a dialog on any fatal error. The
  `songscribe.converter` classes that made this visible are being rewritten and
  are not themselves a finding; what the rewrite owes is the answer to **how does
  a non-interactive entry point report and terminate a fatal error?**
  Answered once at the boundary, three production members stop being back doors:
  `OptionDialogs.setSuppressDialogs` — `public`, documented "for testing" — and
  `RuntimeError.setExitHandlerForTesting`/`resetAlertShownForTesting`. None is
  dead: `UnitTest`, `E2ETest` and `RuntimeErrorTestHelper` call all three. They
  are test-only production surface rather than removable code, and the boundary
  the rewrite installs is what the test bases should be using instead.

## Blockers

**None.** Every track that held a pass back has landed on `develop`: the dialog
redesign, and the `ui-dialog-interface` track that owned `ui/dialog`. Pass 23
begins on the package that track left behind.
