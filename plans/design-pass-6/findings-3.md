# Findings — step 3: make illegal states unrepresentable

Target, over the type set step 2 settled: `dom/Line.java`, `dom/StaffElementRun.java` (with `ElementRange`), `dom/EditableRun.java`, `dom/LineSpans.java`, `dom/ElementChange.java`, `dom/ProjectedElements.java`. Line numbers are against `f7309389` plus the uncommitted removal of the dead `@Nullable` from `Line.getElementIndex` and `LineSpans.boundOf`. Paths below are relative to `src/main/java/songscribe/` unless they start with `src/`.

The work reaches past the target:

- the `-1` sweep in 3.13 spans `dom`, `ui`, `layout`, `midi`, `io` and `util`
- `SpanOutcome` and `Hairpin` belong to row 7 (3.8)
- `Song` to row 12 (3.11)
- `MeasureMapper` to row 16 (3.12)
- `LineComponent` to row 25 (3.4)

**An index that may be absent is `@Nullable Integer`** everywhere below, unless 3.13 names a structure for that owner. NullAway then forces the check at every reader. `ScoreViewController.resolveOwnerIndex` (`:1112`) already has this form.

## 3.1 `getElementIndex` is total over the line's elements

- `Line.getElementIndex(StaffElement)` (`Line.java:648`) throws `IllegalArgumentException` when `element.getParentLine() != this`, and never returns `-1`.
- `Line.contains(StaffElement)` answers membership by reading the parent pointer. That is exact:
  - A staff element's parent line is written only by `Line.attach` (`:373`) and `Line.detach` (`:381`).
  - `LineElement.propagateParentLine` (`:107`), `addChild` and `removeChild` reach only children.
  - `addChild` is called only from `StaffElement.addAttachment` and `NoteElement.addArticulation`.
  - No `Attachment` or `Articulation` is a `StaffElement`.
- The Javadoc on `LineElement.parentLine` (`LineElement.java:64–68`) credits "`Line.detach`'s `!= this` guard" with keeping the pointer and the list in step. No such guard exists. Delete that sentence.
- `ElementColumnBuilder.buildColumn(StaffElement, Line)` (`:121`) has no callers. Delete it.
- The class Javadoc of `AttachmentTarget` (`:32–34`) says binding the element and line separately "is what allowed a stale element to reach `Line.modifyElement(-1, …)`". Replace that sentence with the present-tense reason: a target whose element is not on its line cannot be built.

**Sites that assume the element is present do not change.** This covers:

- `PitchShifter:247–250`, `EndingConfirms:85–87`, `KeyChangeElement:215`, `NoteElement:196`, `StaffElementRenderer:245`
- the twelve `LyricEditor` sites
- `Song:1422`, `MeasureMapper:709–710`, `Ending:162` and `:344`
- `LineComponent.elementIndexOf`
- the readers that go through `getParentLine()` first: `EditLyricAction` and `Span.indexInLine`
- the three test sites: `EndingBracketGeometryTest:166`, `InheritanceChainTest:195` and `:204`, `SongAccidentalWalkTest:84` and `:121`

Those that would fail later today now fail at the lookup.

**Sites that change:**

| Site | Why an element from another line can reach it | Change |
|---|---|---|
| `RenderingUtils.getDecorationColor` (`:118–139`) | the insertion preview, which is on no line | asks `contains`; its first branch tests `frame.currentElementIndex()` for null (3.13) |
| `StaffElementRenderer:439` (`isNoteBeamed`) | the insertion preview, reached through `renderNote` before `renderAccidental` | asks `contains` |
| `LineSelectionHandler:612` | an element hit on another line's component | asks `contains` |
| `SelectionCoordinator:478` (`isSelected`) | a clicked element on a different line than `range.line()` | asks `contains` |
| `ScoreViewController:1120` | the selected target's owner, which may be on another line | asks `contains` |
| `AttachmentTarget:67` | `forElement` accepts any element | asks `contains` |
| `ElementChange:117`, `:223` | a span endpoint on the neighboring line | asks `contains`, then takes 3.2's form |
| `HorizontalSpacingCalculator:1242–1248` (`hairpinReservationFloorSs`) | an insertion preview's column, which is on no line | returns `0` unless the line `contains` both columns' elements, then asks `hasSpan` as now; the reversed-pair check and its comment go, and the Javadoc sentence at `:1219–1220` says a pair with an element off the line reserves nothing |
| `ScoreViewController:935` | nothing: the line is `element.getParentLine()` (`:928`) | check deleted |
| `ScoreViewController:1035–1036` (slide delete) | nothing: the element is still on this line | the `elementIndex >= 0` test and the comment's `-1` deleted |
| `ModificationSession:492` | nothing: the line is `owner.getParentLine()` (`:482`) | the `elementIndex < 0` half deleted; the song-membership half becomes 3.11's lookup |
| `AttachmentTarget:49` | nothing: the constructor throws the same exception itself | check deleted |
| `KeyChangeDialogController:122` | nothing: the key change and the line come from the call that opened the dialog | check and its error log deleted |
| `Tie:229–231` | nothing: `Line.setElement` builds the replacement from its own line | the `replacedIndex < 0` half and its comment deleted |
| `Beam:134–139` | as `Tie` | the comment about another line's `-1` deleted; the bounds test stays |
| `HairpinEndpoints:107` | — | the comment's `-1` rewritten |
| `ScoreViewController:1466` | nothing, once 3.10 lands: the successor is the element after the range actually deleted | unchanged |
| `GraceModeManager:764` | nothing, once 3.1a is fixed | unchanged |

### 3.1a Defect: grace mode leaves structural edits reachable

Between entering grace mode and pairing, `GraceModeManager` holds these across user input:

- `graceNoteIndex`, and the host index from `getHostInsertionIndex`
- the notes in `savedAccidentalStates`, which `enterGraceNotePaired` looks up at `:764`

The grace note itself sits on the line untracked (`:562–563`). Any input that inserts, removes or replaces an element, replays undo, or replaces the document invalidates what it holds. `GraceModeManager.keyPressed` consumes only Escape. `mousePressed` consumes presses only in `GRACE_NOTE_INSERT`.

Two paths need no selection to act, and are open today:

| Path | Guard today | What it does |
|---|---|---|
| `UndoRedoAction` (`UndoAction`, `RedoAction`) | `DISABLE_WHEN_PLAYING` only | replays a recorded step against a line holding an unrecorded element |
| `OpenAction`, `NewAction`, `RevertToSavedAction` | none for grace mode | replace the document; `OpenRecentAction` already carries `DISABLE_IN_GRACE_MODE` |

All five take `Flag.DISABLE_IN_GRACE_MODE`, which `UIAction.enableFromGraceModeState` already honors.

**Every other structural path acts on a selection, and needs no guard.** Grace mode runs inside EDIT mode, where a lyric is the only selectable thing (`LineSelectionHandler.handleEditModePress`) — a staff-line hit or an alt+click switches to SELECT mode first (`LineComponent:987–990`). `SelectionCoordinator.getSelection` answers null for a lyric selection, and `getSelectionSize` counts only a range. So:

- `ElementTypeAction`'s six `Role.DURATION` notes reach neither `applyToSelectionIfActive` nor `replaceTerminalIfSelected`, and only re-arm the preview duration. They must **not** take the flag: `enterGraceNote` picks the host's duration through `DURATION_ACTION_GROUP.select`, which runs `perform`.
- `CutAction` is disabled.
- `DeleteAction` is enabled for a selected lyric and deletes the syllable, which leaves every index in place. `canDeleteLine` is false without a staff-line selection.
- `ScoreInputHandler.KeyAction`'s Up/Down returns before `PitchShifter.shiftPitch`.

No selection can appear mid-mode either: `ModeAction`, `CycleModeAction` and `RestModeAction` carry the flag, `ModeCycleButton` disables itself on `GraceModeStateDidChangeNotification`, `SelectAllElementsAction` carries the flag, a press in `GRACE_NOTE_INSERT` is consumed, and Shift+arrow only extends an existing selection.

## 3.2 `ElementChange.projectedIndexOf` returns `@Nullable Integer`

- `projectedIndexOf` (`ElementChange.java:65`) returns null when the element is not in the projection (`:117`, `:158`, `:223`).
- `Hairpin.projectedEndpointIndexOf` (`:202–207`) and `outcomeForDeletion` (`:228`) test for null instead of `< 0`.

## 3.3 `previousNonGraceIndex` and `nextNonGraceIndex`

- `StaffElementRun.nearestNonGraceIndex(int fromIndex, int step)` (`:461`) is replaced by `previousNonGraceIndex(int)` and `nextNonGraceIndex(int)`. Each returns `@Nullable Integer`. No step exists to pass `0`.
- `Beam.neighborBeamCount` (`Beam.java:439`) takes the `@Nullable Integer` neighbor index already found, and answers for its absence in one place. Its callers at `Beam:349`, `:418` and `:420` each call the query they need and pass the result in. `frenchBeamShortening` stops passing literals.
- `MusicEditOperations:181–183` calls `previousNonGraceIndex`.
- `RangeQueries.tieAttachmentIndex` also takes a step of `-1` (`RangeQueries.java:138`). It takes the same split.

## 3.4 `precedingGraceNoteIndex` and the playing indices

- `precedingGraceNoteIndex` (`StaffElementRun.java:276`) returns `@Nullable Integer`.
- `PitchShifter:384–386` tests for null before `isSamePitchAsFollower`. Its comment about a negative index goes.
- `PlaybackController:221`, `:229` and `:276` pass null where they pass `-1` today. `PlaybackController.previousPlayingLine` (`:106`, `:218`, `:273`, `:279`) becomes `@Nullable Integer`.
- `LineComponent.setPlayingIndices` (`:321`) takes two `@Nullable Integer`. Its two playing fields (`:140–144`, `:318–337`) hold `@Nullable Integer`, null when nothing is playing.
- `LineComponent.pressHitIndex` (`:174`, `:179`, `:1017`) and `LineComponent.elementIndexOf` (`:782`, `:797`, `:818`) take the same form.
- `LineInvariants.playingNoteIndex` and `playingGraceNoteIndex` (`:589–590`, and the `Builder`'s copies) become `@Nullable Integer`, and so do the returns of `getPlayingNoteIndex` and `getPlayingGraceNoteIndex` (Javadoc `:458`, `:463`). `isElementPlaying` and `isLyricSpanPlaying` test for null instead of `< 0`.
- Delete `RenderContext.getPlayingLine` and `getPlayingNote` (Javadoc `RenderContext.java:81`, `:86`), with their `ScoreView` implementations (`:1414–1426`). Nothing calls either, `ScoreView` is the only implementation, and both return a hard-coded `-1`.

## 3.5 `lastNoteIndex` returns `@Nullable Integer`

- `lastNoteIndex()` (`StaffElementRun.java:397`) returns null when the line holds no note.
- `KeyChangeAction:132` tests for null, then tests `index > 0 && index <= last`. The Javadoc clause "a line holding no note offers no position" (`:110–111`) then reads straight off that test.

## 3.6 `ElementRange` rejects a negative `begin`

- The compact constructor (`StaffElementRun.java:668`) also throws when `begin < 0`.
- The `@invariant` becomes `0 <= begin <= end`.

This lands after 3.1: `HorizontalSpacingCalculator:1248` builds `(-1, -1)` until then. No test constructs a negative `begin`.

## 3.7 `mergeOverlapping` takes an absorb mode

- `LineSpans.mergeOverlapping(R span, Class<? extends R> type, Absorb absorb)` (`:463`), with `enum Absorb { OVERLAPPING, ADJACENT }` on `LineSpans`.
- Each value carries the reach it implies. `ADJACENT` reads `SPAN_ADJACENCY_REACH`, which stays the one number that it and `MusicEditOperations` share.
- `Beam.java:453` passes `OVERLAPPING` and `Hairpin.java:118` passes `ADJACENT`.
- The Javadoc at `:458–461` describes the two values instead of the two numbers.

## 3.8 Only a hairpin can reshape

- `Span.outcomeFor` returns `SpanOutcome.Simple`.
- `Hairpin` answers a separate method returning a sealed `HairpinOutcome` over `Simple | Reshape`. `Hairpin.java:259` stays the only construction of `Reshape`.
- `LineSpans.sweep` asks hairpins through that method. `collectReshape`'s `instanceof` and throw (`LineSpans.java:374–376`) are deleted.
- `Hairpin` overrides `outcomeFor` as a `final` projection of that method, reading `Reshape` as `KEEP`, so no caller of `outcomeFor` gets the default bridge's answer for a hairpin, which removes a hairpin that a deleted endpoint only reshapes.
- `ElementChange.removesConfirmableSpan` (`:79–84`) calls `outcomeFor` on every span, with no `instanceof Hairpin` test; the override gives it the answer the sweep acts on.

## 3.9 `Replacement` derives `oldElement`

- `record Replacement(Line line, int index, StaffElement newElement)` (`ElementChange.java:136`). `oldElement()` is a method that reads `line.getElement(index)`, the shape `Deletion.deletedElements()` (`:198`) already has.
- `forReplacement` (`:293`) stops passing the old element.
- Its readers call the method and do not change: `Hairpin:203`, `Tuplet`'s replacement arm, `Span`'s default bridge, and `endingEffect`.
- A `Replacement` is ephemeral: it is valid only until its line changes. The class Javadoc keeps that statement.

## 3.10 A widened range is its own type

- `final class StaffElementRun.EffectiveRange`, with a private constructor, so only `effectiveRange` creates one. It exposes its `ElementRange`.
- These take or return it:
  - `EditableRun.deleteRange` and `deleteRanges`
  - `Line.keyAtEndOfLineAfterRemoving` (`Line.java:314`)
  - the return of `redundantKeyChangeRanges`
  - `KeyChangeStranding.Stranded`'s ranges
- The "must be widened" clause at `EditableRun.java:118–122` is deleted.
- `ScoreViewController.tryInsertFragment` takes `@Nullable EffectiveRange`. Its only caller passing a range, `handlePaste` (`:1557`), already passes the widened one. The method widens that range again twice today, and both re-widenings are deleted:
  - `pasteDisplacementIndex` with `widenedDeleteRange` (`:1308–1316`)
  - `line.effectiveRange(deleteRange)` at `:1462`

  `successorIndex` is then the index after the range actually deleted, so the lookup at `:1466` cannot miss.
- Tests that build their ranges through `effectiveRange`:
  - `src/test/java/songscribe/dom/LineDeletionTest.java:154–156`, the raw ranges handed to `deleteRanges`
  - `StrandedKeyChangeTest.java:322–326`, `:339–343`, `:353–357`, the expected lists compared with `redundantKeyChangeRanges`
  - `KeyChangeStrandingTest.java:70–71`, the `KEY_CHANGE_PAIR` field and its uses at `:118` and `:135`
- `LineDeletionTest:142–143` and `InheritedKeyPropagationTest:198–199` already go through `effectiveRange`.

## 3.11 `Song.indexOfLine` returns `@Nullable Integer`

- `Song.indexOfLine` returns null for a line not in the song.
- `Line.index()` (`Line.java:156–160`) keeps its throw, for callers holding a line known to be in its song.
- Three sites call `contains` and then `index()`. Each calls `indexOfLine` once and tests for null instead:
  - `Line.nextLineRunningKey` (`Line.java:340`)
  - `LineSpans.boundOf` (`:180–185`)
  - `ModificationSession:492–496`

## 3.12 The reader drops a span whose endpoints are on two lines

`MeasureMapper.mapPart` collects every note of the part, across all lines, into one list (`:260–268`). `resolveBeams` (`:1238`), `resolveTrills` (`:1407`) and `resolveTuplets` (`:1314`) / `buildTuplet` check the start note's line against the end note's before building a span. When the lines differ, each drops the span with a warning.

For beams and trills, this matches how each already drops an unclosed start or an orphan stop. A tuplet's other malformations still throw `DocumentValidation.corrupt`. `Tuplet.hasValidSpan(line)` does not catch the cross-line case, because it reads each endpoint's index on that endpoint's own line.

`resolveHairpins` (`:1044`) and `resolveEndings` (`:638`) start over at every line and need no check. The throw in `LineSpans.requireRangeOf` (`:133`) stays: it states that such a span cannot be represented.

## 3.13 No raw `-1`

The inventory is every standalone `-1` in `src/main/java`: 272 lines, plus `-1.0` at `LyricLayoutBuilder.java:506`, and the `-1` markers in `ui/dialog/ResolutionDialog.form`. Each belongs to exactly one group below. Sites already assigned to 3.1–3.11 are not repeated.

### Group 1: not a sentinel. Stays.

- **Signs and directions:**
  - `dom/Direction.java:46`, `:49`
  - `dom/Tie.java:266`, `:291`, `:294`
  - `engraving/LedgerLine.java:67`
  - `layout/LayoutEngine.java:539`, `:854`, `:885`, `:923`, `:1098`, `:1133`, `:1193`, `:1224`
  - `layout/BeamScoring.java:220`, `:596`, `:645`, `:1163`
  - `layout/stacking/StackingUtils.java:605`
  - `ui/renderer/BeamGroupRenderer.java:285`
- **Signed deltas:** `ui/component/ScoreInputHandler.java:302` (`RAISE_PITCH_DELTA_SP`), `:308` (`MOVE_LEFT`)
- **Line distance:** `dom/LineSpans.java:188` (`case -1 -> SpanBound.BEFORE_LINE`)
- **Enum values and tables:**
  - `dom/Accidental.java:13`
  - `dom/Key.java:61`, `:98`, `:99`
  - `io/LegacyKeyType.java:41`
  - `dom/NoteElement.java:50`
- **A restart one before index 0:** `midi/MidiSequenceBuilder.java:292`
- **`split(…, -1)` limits:**
  - `io/LegacyLyricsImporter.java:76`
  - `io/SongIO.java:785`
  - `util/StringUtils.java:166`, `:288`
  - `dom/AttributionFormatter.java:158`
- **Exit status:** `error/RuntimeError.java:46`, `:51`
- **A comment quoting an outside API whose code tests `<= 0`:** `ui/renderer/RecordingGraphics2D.java:564`
- **GUI-designer markers in a generated form:** `ui/dialog/ResolutionDialog.form:3`, `:13`, `:65`, `:85`, `:93`, `:120`, `:130`

The steps passed to `nearestNonGraceIndex` and `tieAttachmentIndex` (`dom/Beam.java:349`, `:418`, `:428`, `:436`; `dom/StaffElementRun.java:451`; `ui/MusicEditOperations.java:181`; `ui/selection/RangeQueries.java:138`) go with 3.3's split.

### Group 2: a `-1` defined by an outside API

Each API gets one named constant, distinct from every other API's.

| API and meaning | Sites | Change |
|---|---|---|
| `String.indexOf` / `lastIndexOf`, not found | `util/FileUtils.java:47`, `:61`; `io/LineIO.java:78` | `StringUtils.INDEX_NOT_FOUND`, in `util/StringUtils` |
| Java Sound `Receiver.send`, no timestamp | `ui/playback/MidiController.java:239`, `:268`, `:318`; `ui/playback/PlayThread.java:89`, `:93`, `:98`, `:114`, `:132` | `MidiController.NO_TIMESTAMP`, package-private |
| `ListCellRenderer` index for a closed combo's display cell | `ui/component/BaseLabel.java:37`; `ui/component/DurationListCellRenderer.java:123`; Javadoc `ui/dialog/OtherValueComboBox.java:370` | `UIUtils.COMBO_DISPLAY_INDEX`, in `util/UIUtils` |
| `JComboBox`/`JList` selected index, no selection | Javadoc `ui/dialog/OtherValueComboBox.java:175`, `:207`; inside `ui/binding/Controls.java` | `UIUtils.NO_SELECTED_INDEX`, in `util/UIUtils`, read only where `Controls` converts (group 3) |
| `DefaultListModel.indexOf`, not found | `ui/dialog/fontchooser/panes/SizePane.java:93` | the index is only tested, so `SizePane` asks `contains` and no constant is needed |
| `List.indexOf`, not found | `ui/action/ActionGroup.java:147` | see *Defects* below: once `ActionGroup.remove` clears a removed selection, `selected` is always in `actions`, and the check is deleted |

**`Controls` views convert at the Swing boundary.** `Controls.itemIndex` and `Controls.selectedIndex` (Javadoc `:241`, `:245`, `:247`, `:357`) become `Property<@Nullable Integer>`, reading null for "nothing selected" and writing `UIUtils.NO_SELECTED_INDEX` back to Swing. Their Javadoc stops describing `-1` as the view's answer. Every binding over them, found with `jet_brains_find_referencing_symbols`, tests for null.

### Group 3: SongScribe's own "absent"

Each owner below becomes `@Nullable Integer` unless its row names another structure. Every reader found with `jet_brains_find_referencing_symbols` tests for null, and its Javadoc stops naming `-1`.

| Owner | Sites |
|---|---|
| `dom/Span.indexInLine`, with `getAnchorElementIndex` / `getEndElementIndex` | `Span.java:388`, `:395`, `:403`, `:411`; comment `layout/stacking/NoteAttachedStacker.java:828` |
| `dom/LyricRun.nextLyricBearingIndex`, `previousLyricBearingIndex`, `previousTextBearingIndex`, and the "no predecessor" arguments | `LyricRun.java:755`, `:760`, `:769`, `:774`, `:787`, `:1204`, `:1222`, `:1224` |
| `dom/Hairpin.resolveBeginIndex`, `resolveEndIndex`, and `outcomeForDeletion`'s `first`/`last` | `Hairpin.java:221`, `:222`, `:377`, `:398`, `:403`, `:420` |
| `dom/StaffElement.indexOfLyricForVerse` | `StaffElement.java:343`, `:354` |
| `dom/Song.firstNonEmptyLineIndex` | `Song.java:730`, `:741` |
| `dom/StaffElementRun.precedingGraceNoteIndex`, `lastNoteIndex`, `nearestNonGraceIndex` | 3.3–3.5; `StaffElementRun.java:275`, `:281`, `:284`, `:395`, `:405`, `:452`, `:460`, `:469` |
| `dom/Line.getElementIndex` | 3.1; `Line.java:645`, `:668` |
| `dom/LineSpans.mergeAdjacentSpans` local `runEnd` | `LineSpans.java:396`, `:405`: null exactly when `run` is empty, replacing the `!run.isEmpty()` test; the trailing `applySpanRun` runs only when it is not null |
| `io/musicxml/MeasureMapper.lineStartMeasureIndex` | `MeasureMapper.java:211`, `:215` |
| `io/musicxml/MusicXmlSerializer.indexOfChild`, `lastIndexOfChild` | `MusicXmlSerializer.java:863`, `:873`; `orderedCalls` (`:849`) tests both for null before `add`/`remove` |
| `io/musicxml/MeasureBuilder.positionOf` | `MeasureBuilder.java:815`, `:829` |
| `io/SongIO.parseLegacyAttributionBlob` local `byLineIndex` | `SongIO.java:786` |
| `layout/LayoutHitTester.findElementAtXSs`, and `LayoutResult.findElementAtXSs`, which delegates | `LayoutHitTester.java:175`, `:192`, `:212`; Javadoc `LayoutResult.java:662`; `PreviewElementManager.java:609` assigns null in grace mode, and `:615`/`:624` test for null |
| `layout/SweepRange.overlapping` locals `begin`/`end` | `SweepRange.java:154`, `:155`, `:161`, `:169` |
| `layout/AttributionContent.firstSubAttributionIndex` | `AttributionContent.java:216`, `:226` |
| `midi/SlideMidiHelper.currentSensitivity` | `SlideMidiHelper.java:75–76`, `:152–153`; compared at `:275` |
| `midi/SlideMidiHelper.pendingGracePitch` | `SlideMidiHelper.java:78–79`, `:210–214`: `consumePendingGracePitch` returns `@Nullable Integer`, and `hasPendingGracePitch` (`:203`) reads the field for null |
| `ui/edit/GraceModeManager.graceNoteIndex`, `hostPreviewGraceIndexOn`, `internalGetCancelThresholdPx`, `internalGetConnectThresholdPx` | `GraceModeManager.java:98`, `:214`, `:218`, `:223`, `:227`, `:285`, `:294`, `:820`, `:874`, `:933`, `:939`, `:948`, `:954`, `:960` |
| `ui/edit/InsertionPointMode.targetIndex` / `getTargetIndex` | `InsertionPointMode.java:216–217`, `:278–280`, `:511–512`, `:560–562`, `:576`, `:586` |
| `ui/MusicEditOperations.validateEndingStructure` | `MusicEditOperations.java:855`, `:858`, `:871`, `:879`, `:888`, `:901`, `:907` |
| `ui/selection/SelectionCoordinator.activeLineIndex`, `findLineIndex`, `getSelectedLine` | `SelectionCoordinator.java:105`, `:106`, `:173`, `:190`, `:255`, `:657`, `:670`, `:743`, `:746`, `:753`; `RenderContext.getSelectedLine` (Javadoc `RenderContext.java:72`) |
| `ui/selection/RangeQueries.tieAttachmentIndex`, `nonGraceBegin`, `nonGraceEnd`, `glissandoSourceIndex` | `RangeQueries.java:144`, `:168`, `:173`, `:189`, `:194`, `:208`, `:366`, `:405`, `:409`, `:420`, `:425`, `:428` |
| `ui/component/LyricTargetResolver.resolveLyricTarget`, `findNextEligibleIndex`, `findPreviousEligibleIndex` | `LyricTargetResolver.java:42`, `:47`, `:52`, `:65`, `:70`, `:80` |
| `ui/component/score/NoteDragHandler.dragElementIndex` | `NoteDragHandler.java:61`, `:319` |
| `ui/component/score/InsertionMarkerOverlay.targetIndex` | `InsertionMarkerOverlay.java:56`, `:59` |
| `ui/component/score/PreviewElementManager.graceHostPreviewSourceIndexOn` | `PreviewElementManager.java:370`, `:378` |
| `ui/renderer/BeamGroupRenderer.doDrawBeams` local `startSubBeam` | `BeamGroupRenderer.java:243`, `:247`, `:251`, `:255` |
| `ui/renderer/ElementFrame.currentElementIndex` | `ElementFrame.java:46`, `:48`, `:88`, `:96–101`; readers `StaffElementRenderer:416`, `LyricTextRenderer:91`, `RenderingUtils:126`, `:167`, `PreviewElementOverlay:193` |
| `ui/binding/Controls.itemIndex`, `selectedIndex` | Javadoc `Controls.java:241`, `:245`, `:247`, `:357`; each becomes `Property<@Nullable Integer>`, writing `UIUtils.NO_SELECTED_INDEX` back to Swing |

**Owners that take a structure rather than a bare `@Nullable Integer`:**

- **`ScoreComponent.contentXPx` (`ScoreComponent.java:143`, Javadoc `:271`) is a `float`, and `-1` means "center on own width".** `resolveContentX` tests `>= 0`, so a shared X that comes out negative — a lyric union wider than the line, from `TextPanel:164` — is taken for "unset", and each component centers on its own width. That is a defect. The field becomes `@Nullable Float`, `resolveContentX` tests for null, and `TextPanel:170–172` pass null.
- **`ElementFrame`'s preview shift is a pair.** `previewShiftFromIndex` (`-1` for none) and `previewShiftSs` become one `@Nullable PreviewShift(int fromIndex, double shiftSs)` record, created once per paint at line level and shared by reference into every per-element frame. `hasPreviewShift()` goes. `PreviewShift.displaces(int index)` replaces the `hasPreviewShift() && index >= previewShiftFromIndex()` expression repeated at `SlideRenderer:344`, `LineRenderer:324`, `:460` and `:558`.
- **`LineInvariants.colorFor(HitTarget, int)` takes an index only `HitTarget.Element` reads.** `TieRenderer:159` and `BeamGroupRenderer:139–140` pass `NO_ELEMENT_INDEX`; `SlideRenderer:202` passes an index that is ignored. Split it into `colorFor(HitTarget)` for every variant and `colorFor(HitTarget.Element, int elementIndex)` for the element variant. `RenderingUtils.decorationColor` (`:167`) branches on the variant, and `NO_ELEMENT_INDEX` is deleted (group 4).
- **`LyricLayoutBuilder.ExtenderState` (`:499–570`) carries two pairs of fields that are set and reset together.**
  - `extenderActive`, `extenderStartXSs`, `extenderColumnIndex` and `syllableSpansGraceHostUnion` become one `@Nullable ActiveExtender(double startXSs, @Nullable Integer sourceColumnIndex, boolean syllableSpansGraceHostUnion)`. A leading continuation is `new ActiveExtender(0.0, null, false)`.
  - `pendingHyphenStartXSs` (`-1` at `:197`, `-1.0` at `:506`) and `pendingHyphenColumnIndex` become one `@Nullable PendingHyphen(double startXSs, int columnIndex)`.
  - The readers at `:196–215`, `:428–429`, `:460`, `:479`, `:488`, `:494`, `:521` and `:563` read the record. `emitDanglingHyphen`'s loop start (`:479`) is then only ever `columnIndex + 1` of a present hyphen.
  - `LyricConnectorLayout`'s `sourceElementIndex` becomes `@Nullable Integer`, and `NO_SOURCE_ELEMENT_INDEX` is deleted (group 4).
- **`SpringSpacer.compress`'s `worstGap` (`:157`) travels with `worstViolationSs` and `worstAtStrut`.** The inner scan (`:161–185`) becomes a method returning `@Nullable Clamp(int gap, boolean atStrut)`, null when no free gap violates a bound. The `worstGap < 0` test at `:189` becomes a null test.
- **`Beam.beamLevel` returns `maxBeamCount - 1`, which is `-1` when no member carries a beam.** Its readers either add 1 back (`Beam.estimatedBeamBottomAboveYSs:252`, `HitRegionBuilder:722`) or test for `-1` (`BeamGroupRenderer:199`). It becomes `beamStackCount()`, returning the count. `BeamGroupRenderer.renderBeams` (`:114`) returns early when the count is `0`, and `doDrawBeams` loses its `level` parameter, which it reads only for that test.
- **`LineIO.LineReader.SegmentParts.secondComma` (`:199–214`) turns `indexOf`'s answer into SongScribe's own "no data portion".** Its readers use it only to cut out the text after it (`:366–368`, `:432–434`). `SegmentParts` carries `@Nullable String data` instead, the text after the second comma or null. `parseTrillPairs` (`:630–646`) repeats `parseSegmentAB`'s parsing inline; it calls `parseSegmentAB` and reads `data`.

### Group 4: named "none" constants

| Constant | Change |
|---|---|
| `InsertionPointMode.NO_INDEX` (`:216`) | deleted with `targetIndex`'s sentinel (group 3) |
| `LineInvariants.NO_VERSE` (`:72–73`) | deleted; `activelyEditedVerse` (`:135`, `:262–267`, `:585`, `:628–630`) becomes `@Nullable Integer`, and so do `LineRenderer:177` and `LyricConnectorRenderer:103` |
| `LineInvariants.NO_ELEMENT_INDEX` (`:75–80`) | deleted by the `colorFor` split (group 3) |
| `LyricConnectorLayout.NO_SOURCE_ELEMENT_INDEX` (`:43–50`) | deleted by the `ExtenderState` records (group 3) |
| `ElementColumn.NO_BEAM_GROUP` (`:61`) | deleted; `beamGroupId` (`:92`) becomes `@Nullable Integer`, `sharesBeamGroupWith` (`:603`) tests for null, and `ElementColumnBuilder:229` passes null |
| `BeatAt.NO_DEFINING_EVENT` | deleted with `BeatAt` — see *Dead code* |
| `TupletLoadPass.NO_POPULATED_LINE` (`:64`) | deleted; `firstPopulatedLineIndex` (`:219–231`) returns `@Nullable Integer`, and `settleTuplets` (`:172`) tests for null |
| `TupletMenuItems.NO_GRADE` (`:45`) | deleted; `existingGrade` (`:81`, `:85`) returns `@Nullable Integer`, and `rebuild` (`:63`) compares with `Objects.equals` |

### Dead code

- `Song.APPEND` (`Song.java:147`). Its only use is `addLine(Line)` → `addLine(APPEND, line)` (`:888`), and `addLine(int, Line)` maps it to `lines.size()` (`:937`). `addLine(Line)` passes `lines.size()`, and the constant and the mapping go.
- `Utils.arrayIndexOf` (`util/Utils.java:51`) has no callers.
- `BeatAt`'s `lineIndex` and `elementIndex` have no readers. The record's Javadoc claims callers use them to recognize a beat barrier, and none does. `TempoResolver.resolveBeatAt` and `Song.resolveBeatAt` (`Song.java:441`) return the `BeatUnit`, and `BeatAt` is deleted. `TupletValidator:184` and `:372` stop calling `.beat()`.
- `MidiSequenceBuilder.buildSequence`'s `endLine` and `endNote`, and `buildSequenceWithRepeats`'s `endLine`. Both callers (`buildFullSequence:82`, `buildFromNoteToEnd:96`) pass `-1`, so every branch reading them is dead. The parameters, their branches and their Javadoc (`:105–106`, `:212`) go.
- `MusicXmlRoundTripSupport.assertSpanEquals` (`src/test/java/songscribe/io/musicxml/MusicXmlRoundTripSupport.java:239–272`), all four overloads. None has a caller.

### Defects

- **`BaseLabel` and `DurationListCellRenderer` paint selection with the same code.** `BaseLabel`'s constructor (`:37–43`) and `DurationListCellRenderer`'s `configure` (`:123–129`) both hold it. It moves to one `BaseLabel` method, which `configure` calls.
- **`ActionGroup.remove` (`remove(T)`) leaves `selected` and `previousSelected` pointing at a removed action.** `remove` clears whichever of them is the removed action. `selectNext`'s `index == -1` test (`:145–149`) is then dead and is deleted.

### Stale documentation

- The comment at `ui/component/score/PreviewOverlayRegistry.java:153` says restoring the preview "resets the insertion index to -1". `restorePreviewElement` drops the hover (`hover = null`). The comment says that.
- `ui/edit/InsertionPointMode.java:278` names `-1` for `getTargetIndex`. It goes with group 3.

### Tests

- No existing test asserts a `-1`, so none changes behavior.
- The tests the conversion reaches at compile time are listed in 3.1 and 3.10.
- None exercises an absent path of an owner above.
