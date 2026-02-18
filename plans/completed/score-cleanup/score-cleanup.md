# Plan: Score.java Legacy Code Cleanup

**Type:** Master Plan  <br>
**Created:** 2026-02-02  <br>
**Status:** Complete

---

## Status Dashboard

| Phase | Description | Status | Sub-plan |
|-------|-------------|--------|----------|
| 1 | [Extract MIDI Sequence Building](#phase-1-extract-midi-sequence-building) | ✅ Complete | [phase-1-midi-sequence-builder.md](phase-1-midi-sequence-builder.md) |
| 2 | [Extract Playback Notification](#phase-2-extract-playback-notification-) | ✅ Complete | — |
| 3 | [Extract Beam Calculations](#phase-3-extract-beam-calculations-) | ✅ Complete | — |
| 4 | [Extract Lyrics Processing](#phase-4-extract-lyrics-processing-) | ✅ Complete | [phase-4-lyrics-processing.md](phase-4-lyrics-processing.md) |
| 5 | [Extract Music Editing Operations](#phase-5-extract-music-editing-operations) | ✅ Complete | [phase-5-music-edit-operations.md](phase-5-music-edit-operations.md) |
| 6 | [Move Edit Note State to EditModeManager](#phase-6-move-edit-note-state-to-editmodemanager) | ✅ Complete | — |
| 7 | [Extract Debug Inspector](#phase-7-extract-debug-inspector) | ✅ Complete | — |
| 8 | [Extract Export Operations](#phase-8-extract-export-operations) | ✅ Complete | — |

---

## Goal

Eliminate legacy code from Score.java by extracting domain logic into appropriate classes. This will reduce Score.java from ~3,300 lines to ~900-1,000 lines, leaving only UI coordination responsibilities.

## Current State

Score.java is a 3,346-line monolithic class that mixes:
- UI coordination (appropriate)
- MIDI sequencing (should be extracted)
- Mathematical calculations (should be extracted)
- Text parsing (should be extracted)
- Domain operations (should be extracted)

## Target Architecture

After cleanup, Score.java should contain only:
- Component initialization and lifecycle
- View/layout coordination
- RenderContext implementation
- Painting coordination (delegation)
- Composition management
- Message handlers (thin delegation)
- Playback state coordination
- Focus management
- Mouse/keyboard event routing

All domain logic should be extracted to dedicated classes.

---

## Phase 1: Extract MIDI Sequence Building

**Status:** Complete  <br>
**Recommended Model:** Sonnet  <br>
**Testing:** Integration tests after completion (test full sequence building)  <br>
**Priority:** High (completely self-contained, easy extraction)  <br>
**Estimated Lines:** ~350

### Current Location
Lines 2163-2549 in Score.java

### Methods to Extract
- `getSequence()` - **removed entirely, moved to PlaybackController**
- `getSelectedSequence()` - **removed entirely, moved to PlaybackController**
- `addLineToTrack()`
- `addNoteToTrack()`
- `addColorizeNoteToTrack()`
- `addTempoChangeToTrack()`
- `addNoteOnOffMessagesToTrack()`
- `getNoteDurationWithTuplet()`
- `getTupletFactor()`
- `getMidiTempoMessage()`
- `createSequence()`

### Fields to Move
- `instrument`, `manualTempoChange`, `playbackNoteDuration`, `colorizeNote` - **moved to PlaybackController**

### Target
- New class: `songscribe.midi.MidiSequenceBuilder` - coordinates sequence building
- New record: `songscribe.midi.PlaybackSettings` - encapsulates playback configuration
- Updated class: `songscribe.ui.playback.PlaybackController` - owns playback settings, coordinates sequence building

### Dependencies
- Composition (read-only)
- Playback settings (now owned by PlaybackController)
- Constants (PPQ, velocities - remain in Score as they're used by Note classes)

### Architecture Change
**Before:** Score owns playback settings and coordinates MIDI building
**After:** PlaybackController owns playback settings and coordinates MIDI building
- Score is a UI component and shouldn't be involved in playback
- PlaybackController is responsible for all playback concerns
- Callers use `PlaybackController.buildSequence()` instead of `Score.getSequence()`

---

## Phase 2: Extract Playback Notification ✅

**Status:** Complete  <br>
**Recommended Model:** Sonnet  <br>
**Testing:** Integration tests after Phase 1+2 complete (test full playback with highlighting end-to-end)  <br>
**Priority:** High (decouples playback from Score)  <br>
**Estimated Lines:** ~50

### Implementation Summary

Successfully moved playback notification handling from Score to PlaybackController:

1. **PlaybackController Changes:**
   - Added `MetaEventListener` functionality via method reference
   - Added `previousPlayingLine` and `previousPlayingNote` fields for efficient updates
   - Implemented `handleMetaMessage()` to process SEQUENCE_NUMBER and END_OF_TRACK events
   - Implemented `updatePlayingNote()` for efficient LineComponent highlighting (only clears previous line if different)
   - Added `getLineComponent()` helper to access LineComponents via MainFrame → Score
   - Added `clearPlayingHighlight()` to reset state on stop
   - Added `pausedSelection` tracking for proper pause/resume behavior

2. **Score Changes:**
   - Removed `MetaEventListener` implementation
   - Removed `meta()`, `updateLineComponentPlaybackState()`, and `resetPlayback()` methods
   - Simplified `playbackStateDidChange()` to only reset sequencer position
   - Added `getLineComponent(int lineIndex)` helper method
   - Removed unused `MidiMetaMessageTypes` import

3. **Removed Classes:**
   - Deleted `PlaybackStateManager.java` (dead code - getters never called)
   - Removed `getPlayingLine()` and `getPlayingNote()` from RenderContext interface

4. **Bug Fixes:**
   - Fixed pause/resume regression where playback would restart from beginning instead of continuing
   - Fixed repeats not being honored by passing `playWithRepeats` setting through PlaybackSettings to MidiSequenceBuilder
   - Restored repeat handling logic in MidiSequenceBuilder with proper REPEAT_RIGHT/REPEAT_LEFT_RIGHT detection

### Results

- **Separation of Concerns:** Playback logic now entirely in playback package
- **Simpler Score:** Score no longer handles MIDI internals or playback state
- **Efficient Updates:** Only updates affected LineComponents (previous and current)
- **Code Reduction:** 124+ lines of code removed
- **Proper Pause/Resume:** Playback correctly continues from paused position
- **Working Repeats:** Repeats are properly honored during playback

---

## Phase 3: Extract Beam Calculations ✅

**Status:** Complete  <br>
**Recommended Model:** Haiku  <br>
**Testing:** Unit tests recommended (pure math functions with clear inputs/outputs)  <br>
**Priority:** Medium (already static, pure math)  <br>
**Estimated Lines:** ~140

### Implementation Summary

Successfully extracted beam calculation methods from Score.java into a dedicated BeamCalculator class:

1. **Created BeamCalculator class** (`src/main/java/songscribe/music/BeamCalculator.java`):
   - Extracted `calculateLengthenings()` - main entry point for beam calculations
   - Extracted `isGoodNote()` - checks if a note can serve as anchor point
   - Extracted `isGoodNotePosition()` - validates note position relative to beam line
   - Extracted `calculateNoteLengthening()` - calculates lengthening for individual notes
   - Moved `MAX_BEAM_ANGLE` constant to BeamCalculator

2. **Updated Score.java**:
   - Removed all beam calculation methods and MAX_BEAM_ANGLE constant
   - Added import for BeamCalculator
   - Updated all 11 calls to use `BeamCalculator.calculateLengthenings()`

3. **Updated LineComponent.java**:
   - Added import for BeamCalculator
   - Updated call to use `BeamCalculator.calculateLengthenings()`

### Results

- **Separation of Concerns:** All beam calculations now isolated in dedicated class
- **Score Simplified:** Removed ~130 lines of mathematical code
- **Pure Math Utility:** BeamCalculator is a stateless utility class with static methods
- **No Breaking Changes:** All functionality preserved, just relocated

---

## Phase 4: Extract Lyrics Processing ✅

**Status:** Complete  <br>
**Recommended Model:** Haiku  <br>
**Testing:** Unit tests recommended (text parsing with clear inputs/outputs - lyrics string → note syllables)  <br>
**Priority:** Medium (self-contained text parsing)  <br>
**Estimated Lines:** ~120

### Implementation Summary

Successfully extracted lyrics processing methods from Score.java into a dedicated LyricsProcessor class:

1. **Created LyricsProcessor class** (`src/main/java/songscribe/music/LyricsProcessor.java`):
   - Extracted `spellLyrics(Composition)` - processes all lines (updated signature to accept composition parameter)
   - Extracted `spellLyrics(Line)` - processes a single line
   - Extracted `setSyllableForNextNote()` - private helper for finding next valid note
   - Added JavaDoc comments for public methods
   - Utility class pattern with private constructor

2. **Updated Score.java**:
   - Removed all three lyrics processing methods
   - Added import for LyricsProcessor
   - Updated 5 call sites to use `LyricsProcessor.spellLyrics()`
   - Changed no-argument `spellLyrics()` calls to pass composition explicitly

3. **Updated LyricsDialog.java**:
   - Added import for LyricsProcessor
   - Updated call site to use `LyricsProcessor.spellLyrics(composition)`

4. **Updated LyricsPanel.java**:
   - Added import for LyricsProcessor
   - Updated call site to use `LyricsProcessor.spellLyrics(score.getComposition())`

### Results

- **Separation of Concerns:** All lyrics parsing logic isolated in dedicated class
- **Score Simplified:** Removed ~162 lines of text processing code
- **Pure Text Processing Utility:** LyricsProcessor is a stateless utility class with static methods
- **No Breaking Changes:** All functionality preserved, just relocated
- **Signature Change:** `spellLyrics()` no-argument version now requires Composition parameter for static context

---

## Phase 5: Extract Music Editing Operations ✅

**Status:** Complete  <br>
**Sub-plan:** [phase-5-music-edit-operations.md](phase-5-music-edit-operations.md)  <br>
**Recommended Model:** Sonnet  <br>
**Testing:** Deferred until selection is fixed  <br>
**Priority:** Medium (high LOC impact, clear domain)  <br>
**Estimated Lines:** ~400

### Implementation Summary

Successfully extracted 22 music editing operation methods from Score.java into a dedicated MusicEditOperations class:

1. **Created MusicEditOperations class** (`src/main/java/songscribe/music/MusicEditOperations.java`):
   - Constructor injection pattern (composition, selectionManager, mainFrame)
   - Extracted all 22 public operation methods (beaming, tie, tuplet, dynamics, endings, trill, lyrics under rests, partial beam, stem direction, tempo)
   - Extracted 1 private helper method (getDynamicsIntervalsFromSelection)
   - Delegates to SelectionManager for helper methods (shouldConnectSelection, getSelectionSize, getSingleSelectedNote)

2. **Updated Score.java**:
   - Added operations field and initialization in setComposition()
   - Added import for MusicEditOperations
   - Updated all 22 methods to delegate to operations instance
   - Updated message handlers to call operations then repaint (toggleTuplet also calls selectionChanged)
   - Removed shouldConnectSelection() helper method (no longer needed)

3. **Code Reduction**:
   - Score.java: 306 lines removed, 30 lines added (net -276 lines)
   - MusicEditOperations.java: 408 lines added
   - Clear separation: Score coordinates UI, MusicEditOperations handles domain logic

### Testing Status

**Testing deferred until selection is fixed.** All operations compile successfully and delegate properly, but comprehensive manual testing of the 22 operations should be performed once selection functionality is working correctly.

### Current Location (Original)
Lines 1162-1552 in Score.java

### Methods to Extract (Completed)

**Beaming Operations:**
- `toggleBeaming()`
- `canToggleBeaming()` (delegate to SelectionManager)

**Tie Operations:**
- `toggleTie()`
- `canToggleTie()` (delegate to SelectionManager)
- `getTieContext()` (delegate to SelectionManager)

**Tuplet Operations:**
- `toggleTuplet()`
- `canToggleTuplet()` (delegate to SelectionManager)

**Dynamics Operations:**
- `addDynamicsToSelection()`
- `removeDynamicsFromSelection()`
- `canRemoveDynamicsFromSelection()`
- `getDynamicsIntervalsFromSelection()`

**Ending Operations:**
- `makeFirstSecondEnding()`
- `removeFirstSecondEnding()`
- `canMakeFirstSecondEnding()`

**Trill Operations:**
- `toggleTrill()`
- `canToggleTrill()`

**Stem/Beam Flip Operations:**
- `flipPartialBeamOrientation()`
- `canFlipPartialBeamOrientation()`
- `flipStemDirection()`
- `canFlipStemDirection()`

**Misc:**
- `toggleLyricsUnderRests()`
- `canToggleLyricsUnderRests()`
- `canChangeTempo()`

### Target Options
1. Single class: `songscribe.music.MusicEditOperations`
2. Split by domain:
   - `songscribe.music.BeamingOperations`
   - `songscribe.music.TieOperations`
   - `songscribe.music.DynamicsOperations`
   - etc.

### Dependencies
- Composition
- SelectionManager (for selection state)
- Line (for interval operations)
- BeamCalculator (for recalculating after changes)

### Notes
- Many methods need access to selection state
- Consider passing SelectionManager or a selection snapshot
- Message handlers in Score will delegate to these classes

---

## Phase 6: Move Edit Note State to EditModeManager ✅

**Status:** Complete  <br>
**Recommended Model:** Haiku  <br>
**Testing:** Integration tests preferred (involves UI state and toolbar interactions)  <br>
**Priority:** Medium  <br>
**Estimated Lines:** ~150

### Implementation Summary

Successfully moved edit note creation, decoration, and modification logic from Score.java to EditModeManager:

1. **Created ScoreActions Interface** (`src/main/java/songscribe/ui/edit/ScoreActions.java`):
   - Callback interface decoupling EditModeManager from Score
   - Methods: clearSelection(), repaint(), setEditNote(), drawWidthIfWiderLine(), getControl(), setControl()
   - Prevents circular dependency while allowing EditModeManager to trigger Score actions

2. **Updated EditModeManager** (`src/main/java/songscribe/ui/edit/EditModeManager.java`):
   - Added constructor injection for dependencies (compositionSupplier, mainFrame, clipboardManager, selectionManager, scoreActions)
   - Moved fields from Score: control, prevPasteControl, playInsertingNote
   - Moved methods: makeEditNote() (both overloads), decorateNote(), noteWasModified(), editNoteDidChange(), modifyEditNote()
   - Added setPrevPasteControl() method for paste operation state management
   - Total addition: ~340 lines

3. **Updated Score** (`src/main/java/songscribe/ui/component/Score.java`):
   - Implemented ScoreActions interface
   - Removed moved fields: playInsertingNote, prevPasteControl
   - Removed moved methods: makeEditNote() (both overloads), decorateNote(), noteWasModified(), editNoteDidChange(), modifyEditNote()
   - Updated EditModeManager construction to pass dependencies and Score as ScoreActions
   - Updated message handlers and updateEditNote() to delegate to EditModeManager
   - Updated handlePaste() to use editModeManager.setPrevPasteControl()
   - Total reduction: ~210 lines

4. **Updated LineComponent** (`src/main/java/songscribe/ui/component/score/LineComponent.java`):
   - Changed calls from score.noteWasModified() to editModeManager.noteWasModified()
   - Changed calls from score.editNoteDidChange() to editModeManager.editNoteDidChange()
   - Uses EditModeManager.getInstance() to access the manager

### Results

- **Separation of Concerns:** All edit note creation, decoration, and modification logic now centralized in EditModeManager
- **Score Simplified:** Removed ~210 lines of edit-mode-specific code
- **Clean Architecture:** ScoreActions interface prevents circular dependencies while enabling necessary callbacks
- **Orphaned Code Handled:** modifyEditNote() moved to EditModeManager (currently unused but preserved for potential future use)
- **No Breaking Changes:** All functionality preserved, just relocated and better organized

### Current Location (Original)
Lines 767-881, 1032-1151 in Score.java

### Methods Moved (Completed)
- `makeEditNote()` (both overloads, static) → EditModeManager
- `decorateNote()` (static) → EditModeManager
- `noteWasModified()` → EditModeManager
- `editNoteDidChange()` → EditModeManager
- `modifyEditNote()` → EditModeManager

### Target
Existing class: `songscribe.ui.edit.EditModeManager`

### Methods Kept in Score
- `setEditNote()` - delegates to EditModeManager (via ScoreActions)
- `getEditNote()` - delegates to EditModeManager
- Message handlers - delegate to EditModeManager (makeEditNote, decorateNote)

### Dependencies
- Actions (toolbar state)
- NoteType
- NoteSpacing
- Line operations

### Notes
- EditModeManager already exists and holds edit note
- Move creation and decoration logic there
- Score becomes a thin coordinator

---

## Phase 7: Extract Debug Inspector ✅

**Status:** Complete  <br>
**Recommended Model:** Haiku  <br>
**Testing:** No tests needed (currently disabled/stubbed - will need tests when re-enabled)  <br>
**Priority:** Low (already disabled, low risk)  <br>
**Estimated Lines:** ~200

### Implementation Summary

Successfully extracted debug inspector methods from Score.java into a dedicated DebugInspector utility class:

1. **Created DebugInspector class** (`src/main/java/songscribe/ui/debug/DebugInspector.java`):
   - Extracted `updateInspectorHover(int x, int y)` - current stub implementation
   - Preserved `updateInspectorHoverOLD(int x, int y)` - commented-out old implementation for reference
   - Extracted `checkSection(SectionLayout, int, int, String, ElementType)` - section hit testing
   - Extracted `logInspectorHover(HoveredElement)` - debug logging
   - Utility class pattern with private constructor and static methods
   - Added comprehensive JavaDoc documentation

2. **Updated Score.java**:
   - Added import for DebugInspector
   - Updated mouseMoved() to call `DebugInspector.updateInspectorHover()` and `DebugInspector.logInspectorHover()`
   - Removed all four inspector-related methods

3. **Code Reduction**:
   - Score.java: ~200 lines removed
   - Clear separation: Score coordinates UI, DebugInspector handles debug inspection logic

### Results

- **Separation of Concerns:** All debug inspector logic isolated in dedicated class
- **Score Simplified:** Removed ~200 lines of debug-specific code
- **Pure Utility:** DebugInspector is a stateless utility class with static methods
- **Preserved Stub Code:** Old implementation preserved for future reference when re-enabling inspector
- **No Breaking Changes:** Functionality unchanged, just relocated

### Current Location (Original)
Lines 1560-1753 in Score.java (before extraction)

### Methods Extracted (Completed)
- `updateInspectorHover()` → DebugInspector
- `updateInspectorHoverOLD()` → DebugInspector (preserved in comments)
- `checkSection()` → DebugInspector
- `logInspectorHover()` → DebugInspector

### Target
New class: `songscribe.ui.debug.DebugInspector`

### Dependencies
- DebugState
- SectionLayout

### Notes
- Currently disabled/stubbed
- Extracted without functional changes
- Old implementation preserved for reference when debug features are re-enabled

---

## Phase 8: Extract Export Operations ✅

**Status:** Complete  <br>
**Recommended Model:** Haiku  <br>
**Testing:** No tests needed (currently stubbed - will need tests when properly implemented)  <br>
**Priority:** Low (currently stubbed)  <br>
**Estimated Lines:** ~70

### Implementation Summary

Successfully extracted export operations from Score.java into dedicated exporter classes:

1. **Created ImageExporter class** (`src/main/java/songscribe/export/ImageExporter.java`):
   - Extracted `createImageForExport()` (both overloads)
   - Static utility class with private constructor
   - Gets score from MainFrame.getInstance()
   - Preserves stub implementation for future component-based rendering

2. **Created SVGExporter class** (`src/main/java/songscribe/export/SVGExporter.java`):
   - Extracted `createSVG()`
   - Static utility class with private constructor
   - Gets score from MainFrame.getInstance()
   - Preserves stub implementation with error messaging

3. **Created PDFExporter class** (`src/main/java/songscribe/export/PDFExporter.java`):
   - Extracted `createPDF()` from ExportPDFAction
   - Static utility class with private constructor
   - Preserves all scale calculation logic
   - Preserves stub implementation with error messaging

4. **Created ABCExporter class** (`src/main/java/songscribe/export/ABCExporter.java`):
   - Created stub `createABC()` method
   - Static utility class with private constructor
   - Not currently supported, reserved for future implementation

5. **Updated Score.java**:
   - Modified `createSVG()` to delegate to SVGExporter
   - Modified `createImageForExport()` (both overloads) to delegate to ImageExporter
   - Removed implementation details, kept only delegation

6. **Updated ExportPDFAction.java**:
   - Modified `createPDF()` to delegate to PDFExporter
   - Removed GraphicUtils import (no longer needed)
   - Added PDFExporter import

### Results

- **Separation of Concerns:** All export logic now isolated in dedicated export package
- **Score Simplified:** Removed ~40 lines of export-specific code
- **Consistent Architecture:** All export formats follow same static utility class pattern
- **Future-Ready:** ABCExporter stub prepared for future implementation
- **No Breaking Changes:** All functionality preserved, just relocated

### Current Location (Original)
Lines 321-331, 1599-1634 in Score.java (before extraction)

### Methods Extracted (Completed)
- `createSVG()` → SVGExporter
- `createImageForExport()` (both overloads) → ImageExporter
- `createPDF()` (from ExportPDFAction) → PDFExporter

### Target
New package: `songscribe.export`
- `ImageExporter` - image export operations
- `SVGExporter` - SVG export operations
- `PDFExporter` - PDF export operations
- `ABCExporter` - ABC export stub

### Dependencies
- MainFrame (for getting score instance)
- Graphics2D
- Component hierarchy (for rendering)
- Export settings (scale, border, background)
- PageLayoutData (for PDF export)

### Notes
- Currently stubbed with "not yet implemented" messages
- Will need proper implementation when component-based export is added
- Each exporter is a self-contained static utility class

---

## Verification

After each phase:
1. Compile: `./scripts/compile.sh`
2. Run: `./scripts/run-debug.sh`
3. Test affected functionality manually
4. Verify no regressions in:
   - Note editing and insertion
   - Selection and clipboard operations
   - Playback
   - All toggle operations (beaming, ties, etc.)

## Notes

- Each phase is independent and can be done in any order
- Priority order reflects risk/reward tradeoff
- High priority items are self-contained with clear boundaries
- Lower priority items have more dependencies or are already disabled
