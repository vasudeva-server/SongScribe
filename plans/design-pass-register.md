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

**Every package under `src/main/java/songscribe` is named by some row's *Where*
column.** A package no row names is a gap in the register, not a package exempt
from review; adding a package to the tree means adding a row or extending one in
the same change. `export` and `converter` carry no row because they no longer
exist — a rewritten export or converter subsystem gets a row when it lands.

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

A row added after the table was written takes the next free number, wherever in
the order it belongs — so a row numbered 29 can sit between 22 and 23. **Table
position is the order; the number is only identity.** Read the order down the
table, never up the numbers.

## Register

| #  | System | Where | Status | Notes |
|----|---|---|---|---|
| 0  | **Keys** | `dom`: `Key`, `KeySignature`, `KeyChangeElement`, `KeyChangeSite`, `KeySignatureExtent`; plus `layout`, `io`, `io/musicxml`, `midi`, `ui` | ✅ | On `develop`. Settled key state wherever it lived, including in `Song` and `Line`. The design it arrived at is `docs/key-changes.md`. |
| 1  | Units and scale | `dom`: `Ss`, `DocPx`, `ViewPx`, `DocumentScale`, `PixelDistance`; plus `font`, `util` | ✅ | On `develop`. The document scale is a compile-time constant, so staff spaces and document pixels cannot diverge. The size-rounds-up / position-rounds-nearest rule is carried by the sealed `PixelDistance` over `DocPx` and `ViewPx`, with `Ss` outside it by construction. Text measurement is collected into `font.TextMeasurement`, the one place toolkit pixels cross into staff spaces and the one measuring instrument; `MyFontUtils` dissolved into four `font` classes and was deleted. The design it arrived at is `.claude/guides/spatial-units.md` and `docs/zoom.md`. |
| 2  | **Glyph registry** | `smufl` | ✅ | Every lookup is total. A font that cannot answer fails the application, so no caller holds a glyph whose measurements are unknown. Stem anchors are asked for by `StemmedNotehead` rather than by glyph, which is what makes the one genuinely partial measurement total. The registry now holds only glyphs the application draws. Outside `smufl`, an element type declares its own appearance, so which glyph an element is drawn with cannot be asked of a type that has no answer, and the barline and repeat stroke sequence is stated once instead of in six places. The design it arrived at is `smufl/package-info.java`. |
| 3  | Staff geometry | `engraving` | ✅ | Every stroke width is base × named multiplier, with no bare literal standing in for one. `EngravingConstants` collects the LilyPond base thickness and everything derived from it that has no single owner; `StemMetrics`, `BeamMetrics`, `LedgerLine`, `StaffPosition` and `BarStroke` are new types that each own one measurement, replacing `LineThickness` and `SMuFLConstants`, both dissolved. `StaffPosition` carries its own `MIN`/`MAX` range and clamps into it, so the two callers that used to check a raw `int` cannot skip the check. The accidental-advance rule — ink width plus natural kerning — moved off `StaffHeaderMetrics` onto `Key.DrawnAccidental` in `dom`, which is total over its own value and its neighbour's rather than partial in its contract. The ledger-line threshold, stated twice before, is now `LedgerLine.forEachOffsetSs`, read by both its callers. The design it arrived at is `engraving/package-info.java`. |
| 4  | Durations and element types | `dom`: `BeatUnit`, `NoteValue`, `ElementType`, `ElementLocation`, `ElementAppearance`, `NoteheadAppearance`, `GlyphAppearance`, `BarAppearance`, `KeySignatureAppearance` | ✅ | The vocabulary every element is built from. `Duration` is now `BeatUnit`, an enum over `(NoteValue, dotted)`; `ElementType` carries a `Kind` and an `ElementAppearance` — sealed over `NoteheadAppearance`, `GlyphAppearance`, `BarAppearance` and `KeySignatureAppearance` — supplied through the constructor, so the appearance mapping is total and the bounds computation is one exhaustive switch with no lookup that can miss. Every appearance arm has its own element leaf (`NoteElement`, `GlyphElement`, `BarElement`, `KeyChangeElement`), sealed under `StaffElement`/`StructuralElement`; the notehead and volta accessors that used to live on `ElementType` moved to the appearance types that actually carry them. `ElementLocation` stays a plain address record — its guard and its transposable `matches` method are both gone, since nothing produces the out-of-range value they existed to catch. Seven runtime guards, six `@Nullable`s and two sentinel values retired; four types introduced (`Kind`, `NoteValue`, `ElementBounds`, `PreviewHover`), each replacing a set of parallel fields or a partial lookup with a total one. |
| 5  | Elements | `dom`: `StaffElement`, `LineElement`, `StructuralElement`, `NoteElement`, `Articulation`, `ArticulationType`, `AccidentalBounds`, `IntrinsicHeight` | ✅ | Everything except key state, which pass 0 settled. |
| 6  | Line | `dom`: `Line`, `ElementChange`, `ProjectedElements`, `StaffElementRun` | ✅ | Everything except key state, which pass 0 settled. |
| 6b | Accidentals | `dom`: `Accidental`, `AccidentalChain`, `AccidentalScanView`, `AccidentalState`, `ChainEdit`, `ProjectedEdit` | ⏳ | The chain walk: which accidental each note sounds and draws, decided again after every edit. `docs/accidentals.md`. It reads lines and the mutations an edit records, and `Song` runs it, so it comes after row 6 and before row 12. |
| 7  | Spans | `dom`: `Span`, `SpanBound`, `SpanLookup`, `SpanOutcome`, `Beam`, `Crescendo`, `Diminuendo`, `Hairpin`, `Ending`, `EndingValidationResult`, `Trill`, `Tuplet`, `TupletValidator`, `TupletLoadPass`, `SlideZone` | ⏳ | One framework with many members; anchored to elements, owned by `Line`. Pass 6 moved the registry into `LineSpans` and added `addTo`/`removeFrom` to `Span` and every subclass, each carrying its own merge policy and mutation record, so the per-kind add/remove methods no longer exist on `Line`. |
| 8  | Ties | `dom`: `Tie` | ⏳ | Its own beast — cross-line, pitch-validated, invalidated by edits at both ends. |
| 9  | Lyrics | `dom`: `Lyric`, `LyricRun`, `DetachedLyricRun` | ⏳ | `docs/lyrics.md`. |
| 10 | Attachments | `dom`: `Attachment`, `AttachmentRemoval`, `Annotation`, `AnnotationAttachment`, `BeatChangeAttachment`, `DynamicAttachment`, `FermataAttachment`, `MetronomeAttachment`, `TempoChangeAttachment` | ⏳ | |
| 11 | Tempo and beat | `dom`: `Tempo`, `TempoResolver`, `SongTempoMark`, `TempoMarking`, `BeatAt`, `BeatChange` | ⏳ | `docs/song-tempo.md`. |
| 12 | Song | `dom`: `Song`, `SongMetadata`, `Attribution`, `AttributionFormatter`, `AttributionLine`, `SongAttribution`, `ModificationSession`, `TerminalMaintainer`, `PartialDate` | ⏳ | The large model, less key state, which pass 0 settled. Decouple from everything above it as far as it will go. Pass 6 moved the terminal-write policy onto `TerminalMaintainer` as three checks `Song` exposes, and made `Song.maintainKeyInvariant` private. |
| 13 | Mutations | `message/mutation` | ⏳ | Shapes follow the model. |
| 14 | Message bus | `message`, `message/notification`, `message/command` | ⏳ | *(undecomposed)* — `plans/message-subscription-delegate.md` settles part of what a read of `message` would otherwise have to discover, so this pass inherits rather than derives: one bus with no scope stack; a publication-error policy the application entry point installs, defaulting to log-only; `MessageSubscription` as the only route to a registration, with `MessageCenter.subscribe`/`unsubscribe` package-private behind it; and a registration whose end is its owner's `dispose()`. That plan is remediation, not a pass, and it leaves `message/notification`, `message/command` and the 28-plus-28 message types they carry untouched. |
| 15 | Undo and lifecycle | `undo`, `lifecycle` | ⏳ | Two halves: `undo` replays mutations, and `lifecycle` builds `Shutdown` and `Disposable`. `docs/lifecycle.md`. How those two are built is this pass's; what the application does about a fatal error is row 30's, so this pass is not the one expected to answer it. `plans/message-subscription-delegate.md` makes the thing every `Disposable` implementor acquires a `MessageSubscription` field and rewrites the class Javadoc sentence that says so, so this pass reads a `Disposable` whose content is already settled and does not re-derive it. |
| 16 | MusicXML I/O | `io/musicxml` | ⏳ | The live boundary — converts into domain types. |
| 17 | Legacy I/O | `io` | ⏳ | Read-only migration path. |
| 18 | Layout | `dom`: `CollisionRegion`; plus `layout`, `layout/stacking`, `hit` | ⏳ | *(undecomposed)* — visibly several systems: columns, spring spacing, lyric layout, beams, accidentals, hit testing, the layout result, geometry primitives. Split on the read. |
| 19 | Rendering | `ui/renderer` | ⏳ | *(undecomposed)* |
| 20 | Selection | `ui/selection` | ⏳ | |
| 21 | Clipboard | `ui/clipboard` | ⏳ | |
| 22 | Edit modes | `ui/edit` | ⏳ | |
| 29 | Bindings | `binding`, `ui/binding` | ⏳ | Consumers today: `ui/dialog` (13 files), `ui/component` (2: `ScoreView`, which already owns a `Bindings`, and `ZoomStatusBarPanel`), `prefs` (1). `plans/bindings-outside-dialogs.md` makes `Song` a binding owner too, so if that plan's Phase 1 lands first, `dom` becomes a consumer and this row moves above row 12. |
| 23 | Dialogs | `ui/dialog`, `ui/dialog/fontchooser` | ⏳ | *(undecomposed)* — takes the package as the finished `ui-dialog-interface` track left it. That track deleted `ui/dialog/backend`, so this pass never sees it. `ui/dialog/fontchooser` has three subpackages: `listeners`, `model`, `panes`. |
| 24 | Actions | `ui/action` | ⏳ | *(undecomposed)* — 60 files. |
| 25 | Score components | `ui/component`, `ui/component/score`, `ui/component/toolbar` | ⏳ | *(undecomposed)* |
| 26 | UI shell | `ui`, `ui/menu`, `ui/platform/mac`, `ui/playback` | ⏳ | *(undecomposed)* — `ui/platform` itself holds no files; everything is in `ui/platform/mac`, which holds six. |
| 27 | MIDI | `midi` | ⏳ | |
| 28  | Leaf utilities | `songscribe`: `FileExtensions`, and the generated `Strings` and `Version`; plus `util`, `error`, `shape`, `prefs` | ⏳ | Swept as encountered; a pass of their own only if one earns it. `strings.properties` is this row's too. |
| 30 | Application entry point | `songscribe`: `SongScribe` | ⏳ | It starts every system above it and nothing consumes it, so it is reviewed after all of them. It reads a `lifecycle` that row 15 has settled. Two questions are this row's: how a fatal error is reported by an entry point that may have no display, and whether `SongScribe.logBanner` stays `public` for the test bases alone. |

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
- **→ Pass 7.** `Tuplet.getNoteValue()` returns a `@Nullable ElementType`,
  standing in for a pitched note's written value. `NoteBuilder.buildTimeModification`
  (L442) converts through `ElementType.noteValue()`. Now that `dom.NoteValue`
  exists, `Tuplet.getNoteValue()` should hold one directly.
- **→ Pass 7.** `Beam` names its members by index, so `BeamGroupRenderer.beamedNoteAt`
  narrows each end to a `NoteElement` with a throw nothing can reach. A beam
  that hands out its notes retires it.
- **→ Pass 18.** `NoteGeometry.getLedgerLineBaseExtentSs` and `getLedgerLineExtentSs`
  (L639, L649) have no caller in either tree; only a Javadoc `{@link}` at L811
  names them. Dead.
- **→ Pass 18.** `layout/stacking/StackingUtils` is a `final` class of 13
  constants and 18 static methods over stacking geometry — the `*Utils` shape
  `design.md` names wherever it's found.
- **→ Pass 24.** `ui/action`'s toggling-action framework hands every state-
  selecting action a bare `boolean selected` through
  `NoteOnlyAction.applyToElement(Line, int, NoteElement, boolean)` —
  `FermataAction`, `DotAction`, `AccidentalInParensAction`, `ArticulationAction`,
  `DynamicMarkingAction` all override it with that signature. `FermataAction`
  already branches on the boolean into `FermataAttachment.addFermata`/
  `removeFermata` at its own boundary; the boolean is the framework's parameter.
- **→ Pass 9.** `Lyric`'s constructor now carries the whole content model — it
  refuses text on a carrier and blank text with `Extend.START`, both by
  `IllegalArgumentException`, where `StaffElement.setLyricForVerse` used to
  check these in front of it and threw `IllegalStateException`.
  `Lyric.carrier(verse, extend)` and `Lyric.withExtend(extend)` are the two
  factories the chain repairs share. Row 9 judges whether the record's
  remaining surface still fits this content model.
- **→ Pass 9.** Hyphen chains and melismas must be able to cross a line break,
  and the lyric editor must be able to enter both. Today neither can be entered:
  `LyricTargetResolver.findNextEligibleIndex`/`findPreviousEligibleIndex` stop at
  the line's ends, so `-` and `_` on a line's last note find no target, and `_`
  on a line's first note finds no preceding syllable. Hyphens are not modeled
  across a break at all: `LyricLayoutBuilder.emitDanglingHyphen` looks for the
  next syllable only on its own line and logs an error when there is none, no
  pending hyphen is carried into the next line, and `LyricRun.endDanglingWord`
  ends a word whose next syllable is not in the run. Melismas are laid out
  across a break (`Result.hasTrailingContinuation` feeds the next line's
  leading stub).

  **Pass 6 checked the chain repairs, and they do not keep a crossing chain
  intact.** The user settled the rule: **a line break is a gap, and words and
  melismas both cross it.** An edit that interrupts a chain at a line edge
  settles it on both lines, exactly as it would mid-line. The dom lyric model
  cannot do that, because `LyricRun` has no reach past its own run. Every walk
  along a chain stops at the line edge:

  - `settleChainsAcross` (`LyricRun.java:114`), which all three edit repairs go
    through: the insertion repair (`:188`), the untracked insertion repair
    (`:282`) and the deletion repair (`:331`). At index 0, `fixSuccessorSyllabic`
    makes the line's first syllable begin a word while the previous line's last
    syllable still hyphenates into it. On an append, the last syllable's word
    ends or its melisma closes, and the next line's first syllable or carriers
    are left pointing back at a chain that is gone.
  - `setSyllableBoundary`'s successor propagation, the lyric-deletion
    adjustments, and `backfillSyllabic` (`:392`), which makes every line's first
    syllable begin a word on a legacy load.
  - The lyric editors, `LyricEditor` and `LyricChainEditor`, which never reach
    another line.

  The correct structure is a chain that runs the length of the song. `LyricRun`
  gets its neighboring runs: a `Line` reaches the lines before and after it
  through its song, and a detached run has none. Every walk along a chain then
  continues into the neighboring run when it reaches an edge. Making only the
  three edit repairs cross lines is not a fix, because lyric entry at a line
  edge would still stop there. Pass 12's `Line`-to-concrete-`Song` finding
  bears on how a line reaches its neighbors.

  `AcceptedInsertion.land` (`ui/component/score/AcceptedInsertion.java:70–82`)
  assembles `EditableRun.insertRun`'s repair sequence by hand and skips both
  repairs on an append. The skip keeps a crossing chain whole today, where
  `insertRun` would break one side of it. Once the repairs cross lines, `land`
  goes through `insertRun` unchanged and its skip is deleted. `appends()` has a
  second reader under the same rule: `GraceModeManager.enterGraceNote`
  (`ui/edit/GraceModeManager.java:539`) owes the deferred repairs only when the
  grace note was not an append, so that condition goes too, and with it
  `appends()`.
- **→ Pass 12.** Two diagrams sit outside pass 4's target and were not judged at
  its step 9: `Song.java` L909 and L977 (pseudocode for the line-insertion and
  line-deletion mutation sequences), and `SongMetadata.java` L38 (the per-field
  normalization pipeline). Each is a sequence its contracts also state, which is
  the shape a design pass's step 9 exists to question.
- **→ Pass 30.** The fatal-error path assumes a display, and the open question is
  **how does an entry point without a display report and terminate a fatal
  error?** `RuntimeError.exit` routes unconditionally through
  `OptionDialogs.showErrorMessageWithString`, i.e. a modal `JOptionPane`, and it
  is called from far more than a throwing `@Handler`. Answered once at the
  boundary, four production members stop being back doors:
  `OptionDialogs.setSuppressDialogs` — `public`, documented "for testing" —
  `RuntimeError.setExitHandlerForTesting`, `RuntimeError.resetAlertShownForTesting`
  and `SongScribe.logBanner`. None is dead: `UnitTest`, `E2ETest` and
  `RuntimeErrorTestHelper` call the first three, and `logBanner` is `public` for
  `UnitTest` and `E2ETest` alone — no caller outside its own class is production.
  They are test-only production surface rather than removable code, and the
  boundary this pass installs is what the test bases should be using instead.

  The message bus is not an instance of this finding.
  `plans/message-subscription-delegate.md` makes the publication-error policy
  log-only by default and has `SongScribe.main` install the dialog policy, and it
  deletes `MessageBusScope`, `MessageCenter.pushBus`, `popBus` and `SCOPE_STACK`
  outright, so none of them is a member this row inherits.
- **→ Pass 16.** `MusicXmlCrossLineSpanTest` was added by pass 6 and is
  untriaged: its subject is the MusicXML reader, which is row 16's.
- **→ Pass 6b.** Pass 6's mutation run over `songscribe.dom`, with the `dom` tests
  only, left 26 mutants in `ChainEdit` that no test detects. `lineRekeyed` (`:171`)
  and `lineRemoved` (`:188`) can return null, and `lineRemoved`'s rekey clause
  (`:189`) can be negated, so no test sends a line key change or a line removal
  through the walk. Also undetected: `replaced`'s range end and rekey clause
  (`:135–136`), `modified`'s vacated-position condition, range end and rekey clause
  (`:155–161`), `of`'s rekey for a line insertion with its own key (`:229`), every
  clause of `isRestamp` (`:265–269`), and `anyRekeys` and `endsInForce` returning
  true (`:285`, `:289`). Each is a promise in `ChainEdit`'s own contract.
- **→ Pass 7.** `LineSpans.getSpans` promises "the order they were added", and every
  span query iterates in that order. Only the tie queries depend on it:
  `findTieAt` takes the first match, and a note can hold a tie in and a tie out.
  Every reader of hairpins either sorts by position or asks only whether one
  exists, and pass 6 deleted the sort in `applySpanRun` that tried to keep hairpin
  order across undo, which re-adds a span at the end of the list. Row 7 decides
  whether the registry promises an order at all, or whether the tie queries state
  the one they need.
- **→ Pass 7.** `LineSpans.sweep` (`dom/LineSpans.java:383`) returns at once when
  `line.getSong().isReplaying()`. A batch replays whole and in order, so the companion
  span changes are already applied at each replayed element change, and the sweep would
  find nothing to do. No test can make the check fail. Row 7 decides whether the check is
  dead, or names the batch that needs it; row 15 owns the replay flag and can answer what
  a batch carries.
- **→ Pass 12.** `Line` holds a concrete `Song` (`Line.java:139`) and calls ten of its
  members: `lineCount`, `applyChange`, `getLine`, `indexOfLine`, `inheritedKeyOf`,
  `checkTerminalPolicyForInsertion`, `checkTerminalPolicyForReplacement`,
  `checkTerminalPolicyForRemoval` and `isTerminal`; `LineSpans` adds `isReplaying`.
  `UnitTest.minimalSongMock()` therefore mocks the whole `Song` class. Row 12 decides the
  narrow interface a line needs from its song. Pass 9's lyric chains need a line to reach
  its neighboring lines through its song, so the two decisions are one.
- **→ Pass 7.** `Ending.hasRepeatSplit(Line, int anchorIndex, int endIndex)` takes two
  indices of one type, in an order the caller must know. `MeasureMapper:710` is the
  reader-side caller. Row 7 judges whether an ending's span should be the argument.

## Blockers

**None.** Every track that held a pass back has landed on `develop`: the dialog
redesign, and the `ui-dialog-interface` track that owned `ui/dialog`. Pass 23
begins on the package that track left behind.
