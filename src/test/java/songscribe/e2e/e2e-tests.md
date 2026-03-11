# E2E Test Guide

Each test resets the composition to a blank state before running.
The status overlay shows the test counter and the test name.

## ElementInsertionTest

### EditOperations

#### testDragNoteToNewStaffPosition
**Goal:** Verify that dragging a note in edit mode changes its pitch.
**Steps:**
1. Insert a quarter note at staff position 0.
2. Click the note head in edit mode.
3. Drag the note to staff position -4.
4. Verify the note moved to staff position -4.

#### testReplaceNoteAtSameXDifferentPitch
**Goal:** Verify that clicking at the same X position as an existing note replaces it.
**Steps:**
1. Insert a quarter note at staff position 0.
2. Switch to half note duration.
3. Click at the same X as the existing note but at a different staff position (-4).
4. Verify there is still only 1 note, now a half note at staff position -4.

### NoteInsertion

#### testInsertEighthNote
**Goal:** Verify that clicking on the staff inserts an eighth note.
**Steps:**
1. Select eighth note duration from the toolbar.
2. Click on the staff below the middle line (staff position -2).
3. Verify 1 note exists, is an eighth note, and is at staff position -2.

#### testInsertHalfNote
**Goal:** Verify that clicking on the staff inserts a half note.
**Steps:**
1. Select half note duration from the toolbar.
2. Click on the staff above the middle line (staff position 2).
3. Verify 1 note exists, is a half note, and is at staff position 2.

#### testInsertNoteAtDifferentStaffPositions
**Goal:** Verify that multiple notes can be inserted at various staff positions.
**Steps:**
1. Select quarter note duration.
2. Click at four different staff positions (0, -4, 4, -8) in sequence.
3. Verify 4 notes exist, each at the expected staff position.

#### testInsertQuarterNote
**Goal:** Verify that clicking on the staff inserts a quarter note.
**Steps:**
1. Select quarter note duration from the toolbar.
2. Click on the staff at the middle line (staff position 0).
3. Verify 1 note exists, is a quarter note, and is at staff position 0.

#### testInsertRest
**Goal:** Verify that enabling rest mode inserts a rest instead of a note.
**Steps:**
1. Select quarter note duration.
2. Click the rest mode toggle button.
3. Click on the staff.
4. Verify 1 note exists and it is a rest.


## SelectionTest (selection/)

### BasicSelection

#### testClickEmptySpaceDeselects
**Goal:** Verify that clicking empty space deselects all notes.
**Steps:**
1. Build a 3-note composition.
2. Switch to select mode and click a note to select it.
3. Click on empty space far to the right of all notes.
4. Verify nothing is selected.

#### testClickToSelectNote
**Goal:** Verify that clicking a note in select mode selects it.
**Steps:**
1. Build a composition with 3 quarter notes at positions 0, -2, -4.
2. Switch to select mode.
3. Click the second note.
4. Verify only the second note is selected.

#### testModeToggle
**Goal:** Verify that the mode cycle button toggles between edit and select mode.
**Steps:**
1. Ensure edit mode is active, verify `score().getMode()` is `NOTE_EDIT`.
2. Click the mode cycle button to switch to select mode, verify mode is `SELECT`.
3. Click again to switch back to edit mode, verify mode is `NOTE_EDIT`.

### DeselectAndLineSelection

#### testClickInStaffSelectsLine
**Goal:** Verify that clicking within the staff area (but not on a note) selects the line.
**Steps:**
1. Build a 3-note composition.
2. Switch to select mode.
3. Click within the staff to the right of all notes.
4. Verify the line is selected.

#### testMetaDDeselectsAll
**Goal:** Verify that Cmd+D deselects all notes.
**Steps:**
1. Build a 3-note composition.
2. Select all 3 notes via click + shift-click.
3. Press Cmd+D.
4. Verify nothing is selected.

### RangeSelection

#### testShiftClickExtendsSelection
**Goal:** Verify that shift-clicking extends the selection range.
**Steps:**
1. Build a 3-note composition.
2. Switch to select mode.
3. Click the first note to select it.
4. Shift-click the third note.
5. Verify all 3 notes are selected.

#### testShiftClickShrinksSelection
**Goal:** Verify that shift-clicking a closer note shrinks the selection range.
**Steps:**
1. Build a 3-note composition.
2. Select notes 1-3 via click + shift-click.
3. Shift-click the second note.
4. Verify only notes 1-2 are selected, note 3 is deselected.


## SelectionApplyTest (selection/)

### AccidentalChanges

#### testSelectNotesClickAccidentalVerifyApplied
**Goal:** Verify that clicking an accidental toolbar button applies it to selected notes.
**Steps:**
1. Build a composition with 2 quarter notes at positions 0 and -2.
2. Switch to select mode, select both notes.
3. Click the flat toolbar button.
4. Verify both notes have a FLAT accidental.
5. Verify the selection remains active.

#### testSelectNotesClickNaturalToolbarVerifyApplied
**Goal:** Verify that clicking the natural toolbar button applies a natural accidental to selected notes.
**Steps:**
1. Build a composition with 2 quarter notes.
2. Switch to select mode, select both notes.
3. Click the natural toolbar button.
4. Verify both notes have a NATURAL accidental.
5. Verify the selection remains active.

### ArticulationChanges

#### testApplyFermataThenRemove
**Goal:** Verify that fermata can be applied and toggled off via the menu.
**Steps:**
1. Build a composition with 2 quarter notes.
2. Switch to select mode, select both notes.
3. Click the "Fermata" menu item. Verify both notes have fermata.
4. Click "Fermata" again. Verify fermata is removed from both notes.

#### testSelectNotesAndRestsClickDotVerifyBothGetDots
**Goal:** Verify that clicking the dot button applies dots to both notes and rests.
**Steps:**
1. Build a composition with a quarter note, a quarter rest, and a quarter note.
2. Switch to select mode, select all 3 elements.
3. Click the dot toolbar button.
4. Verify all 3 elements have a dot count of 1.

### DurationChanges

#### testDurationChangePreservesNoteRestKind
**Goal:** Verify that changing duration preserves note/rest distinction.
**Steps:**
1. Build a composition with an eighth note, an eighth rest, and an eighth note.
2. Switch to select mode, select all 3 elements.
3. Click the half note toolbar button.
4. Verify the notes became minims and the rest became a minim rest.

#### testSelectNotesClickDurationVerifyChanged
**Goal:** Verify that clicking a duration toolbar button in select mode changes the selected notes' duration.
**Steps:**
1. Build a composition with 3 eighth notes at positions 0, -2, -4.
2. Switch to select mode, select all 3 notes.
3. Click the quarter note toolbar button.
4. Verify all 3 notes are now crotchets.
5. Verify the selection remains active.

### MixedElementSelection

#### testSelectNotesAndBarlineVerifyMutualExclusivity
**Goal:** Verify that selecting notes and barlines enables actions for both types.
**Steps:**
1. Build a composition with a note, a barline, and a note.
2. Switch to select mode, select the first two elements.
3. Verify duration actions are enabled (notes exist in selection).
4. Verify barline actions are also enabled (barlines exist in selection).

#### testSelectOnlyBarlinesDisablesDurationActions
**Goal:** Verify that selecting only barlines disables duration and dot actions.
**Steps:**
1. Build a composition with two barlines followed by a note.
2. Switch to select mode, select the two barlines.
3. Verify duration actions are disabled.
4. Verify dot action is disabled.
5. Verify barline actions are enabled.


## BeamingTest

### testAutoBeamingOnInsertion
**Goal:** Verify that consecutive eighth notes are automatically beamed.
**Steps:**
1. Switch to edit mode and select eighth note duration.
2. Insert two eighth notes at different staff positions.
3. Verify both notes are beamed together in one beam group (interval 0-1).

### ManualBeamToggle

#### testToggleBeamingOnSelection
**Goal:** Verify that selecting two unbeamed eighth notes and toggling beam adds a beam.
**Steps:**
1. Build a composition with 2 unbeamed eighth notes.
2. Switch to select mode, select both notes.
3. Click the toggle beam button.
4. Verify both notes are now beamed.

#### testToggleBeamingRemovesExistingBeam
**Goal:** Verify that toggling beam on already-beamed notes removes the beam.
**Steps:**
1. Build a composition with 2 beamed eighth notes.
2. Switch to select mode, select both notes.
3. Click the toggle beam button.
4. Verify both notes are now unbeamed.

### StemDirection

#### testFlipStemDirection
**Goal:** Verify that flipping the stem of a beamed note changes its direction.
**Steps:**
1. Build a composition with 2 beamed eighth notes.
2. Switch to select mode, select the first note.
3. Click the flip stem direction button.
4. Verify the note's stem direction is no longer automatic and is flipped.

#### testFlipStemDirectionUnbeamed
**Goal:** Verify that flipping the stem works on unbeamed quarter notes too.
**Steps:**
1. Build a composition with 2 quarter notes.
2. Switch to select mode, select the first note.
3. Click the flip stem direction button.
4. Verify the note's stem direction is no longer automatic and is flipped.

#### testStemDirectionPersistsThroughSaveLoad
**Goal:** Verify that a flipped stem direction survives XML save/load.
**Steps:**
1. Build a composition with 2 quarter notes.
2. Select the first note and flip its stem.
3. Save the composition to XML and reload it.
4. Verify the reloaded note still has a non-automatic, flipped stem direction.


## TieTest

### testDragTiedNoteMovesOther
**Goal:** Verify that dragging one tied note moves the other tied note too.
**Steps:**
1. Build a composition with 2 tied quarter notes at the same pitch.
2. In edit mode, drag the first note down by 4 staff positions.
3. Verify both notes moved to the new staff position.

### testTiePersistsThroughSaveLoad
**Goal:** Verify that ties survive XML save/load.
**Steps:**
1. Build a composition with 2 tied quarter notes.
2. Save the composition to XML and reload it.
3. Verify the reloaded composition has the same tie interval.

### BasicOperations

#### testCreateTieViaSelection
**Goal:** Verify that selecting two adjacent notes and toggling tie creates a tie.
**Steps:**
1. Build a composition with 2 quarter notes at the same pitch.
2. Switch to select mode, select both notes.
3. Click the toggle tie button.
4. Verify a tie interval exists covering notes 0-1.

#### testRemoveTieViaToggle
**Goal:** Verify that toggling tie on already-tied notes removes the tie.
**Steps:**
1. Build a composition with 2 tied quarter notes.
2. Switch to select mode, select both notes.
3. Click the toggle tie button.
4. Verify the tie is removed.

#### testSelectTiedNotesEnablesTieToggle
**Goal:** Verify that selecting both tied notes enables the tie toggle and populates tie context.
**Steps:**
1. Build a composition with 2 tied quarter notes.
2. Switch to select mode.
3. Click the first note, then shift-click the second note.
4. Verify 2 notes are selected.
5. Verify `canToggleTie()` returns true and tie context indicates the tie can be toggled.

### PitchValidation

#### testCanTieAfterNaturalFlatResetsToKeySignature
**Goal:** Verify that a natural-flat accidental resets to the key signature pitch.
**Steps:**
1. Build a composition in Db major: G (Gb from key sig), Gbb, G natural-flat (Gb), G (inherits natural-flat = Gb).
2. Switch to select mode, select the last two notes.
3. Verify `canToggleTie()` returns true (both resolve to Gb).

#### testCanTieEnharmonicNotes
**Goal:** Verify that enharmonic notes (same pitch, different staff position) can be tied.
**Steps:**
1. Build a composition with B# (sp=0, pitch 72) and C (sp=-1, pitch 72).
2. Switch to select mode, select both notes.
3. Verify `canToggleTie()` returns true.

#### testCanTieWithInheritedAccidental
**Goal:** Verify that a note inheriting an accidental from a previous note can be tied.
**Steps:**
1. Build a composition with F# (sp=4, explicit sharp) then F (sp=4, NONE — inherits sharp).
2. Switch to select mode, select both notes.
3. Verify `canToggleTie()` returns true.

#### testCanTieWithKeySignatureAccidental
**Goal:** Verify that notes resolving to the same pitch via key signature can be tied.
**Steps:**
1. Build a composition in Db major (5 flats) with two notes at sp=0 with NONE accidental (both resolve to Bb).
2. Switch to select mode, select both notes.
3. Verify `canToggleTie()` returns true.

#### testCannotTieSamePositionDifferentAccidental
**Goal:** Verify that notes at the same staff position but with different accidentals cannot be tied.
**Steps:**
1. Build a composition with B natural (sp=0) and B# (sp=0).
2. Switch to select mode, select both notes.
3. Verify `canToggleTie()` returns false.

#### testCannotTieWhenNaturalCancelsInheritedAccidental
**Goal:** Verify that a natural accidental cancels an inherited accidental, preventing a tie.
**Steps:**
1. Build a composition with F# (sp=4), F (sp=4, inherits sharp), F natural (sp=4, explicit natural).
2. Switch to select mode, select the last two notes (F# inherited vs F natural).
3. Verify `canToggleTie()` returns false.


## GlissandoTest

### testDragToUnisonRemovesConnectedGlissando
**Goal:** Verify that dragging notes to the same pitch removes a connected glissando.
**Steps:**
1. Build a composition with a connected glissando between notes at different pitches.
2. In edit mode, drag the source note to the same pitch as the target.
3. Verify the glissando is removed (unison glissando is meaningless).

### testGlissandoPersistsThroughSaveLoad
**Goal:** Verify that glissandos survive XML save/load.
**Steps:**
1. Build a composition with a connected glissando.
2. Save the composition to XML and reload it.
3. Verify the reloaded note has the same glissando type.

### Deletion

#### testDeleteSelectedGlissando
**Goal:** Verify that pressing Delete with a glissando selected removes only the glissando.
**Steps:**
1. Build a composition with a connected glissando.
2. Switch to select mode, click the glissando midpoint to select it.
3. Press Delete.
4. Verify the glissando is removed but both notes still exist.

#### testDeleteSourceNoteRemovesConnectedGlissando
**Goal:** Verify that deleting the source note of a connected glissando removes the glissando.
**Steps:**
1. Build a composition with a connected glissando.
2. Switch to select mode, select the source note (note 0).
3. Press Delete.
4. Verify only 1 note remains and it has no glissando.

#### testDeleteSourceNoteRemovesSlideOut
**Goal:** Verify that deleting a note with a slide-out removes the note entirely.
**Steps:**
1. Build a composition with 1 note that has a slide-out glissando.
2. Switch to select mode, select the note.
3. Press Delete.
4. Verify the line has 0 notes.

#### testDeleteTargetNoteRemovesConnectedGlissando
**Goal:** Verify that deleting the target note of a connected glissando cleans up the source.
**Steps:**
1. Build a composition with a connected glissando.
2. Switch to select mode, select the target note (note 1).
3. Press Delete.
4. Verify only 1 note remains and it has no glissando.

### Insertion

#### testInsertConnectedGlissando
**Goal:** Verify that inserting a connected glissando between two notes works.
**Steps:**
1. Build a composition with 2 notes at different pitches (0 and -4).
2. Switch to edit mode, select the glissando tool.
3. Click between the two notes.
4. Verify note 0 has a connected glissando.

#### testInsertSlideOutGlissando
**Goal:** Verify that inserting a slide-out glissando on a note works.
**Steps:**
1. Build a composition with 2 notes at different pitches.
2. Switch to edit mode, select the slide-out tool.
3. Click between the notes.
4. Verify note 0 has a slide-out glissando.

### Selection

#### testSelectGlissandoByClick
**Goal:** Verify that clicking on a glissando line selects it.
**Steps:**
1. Build a composition with a connected glissando between 2 notes.
2. Switch to select mode.
3. Click the midpoint of the glissando line.
4. Verify the glissando is selected (not a note).

#### testSelectSourceNoteHighlightsGlissando
**Goal:** Verify that selecting the source note of a glissando highlights it.
**Steps:**
1. Build a composition with a connected glissando.
2. Switch to select mode.
3. Click the source note (note 0).
4. Verify note 0 is selected and has a glissando.

#### testSelectTargetNoteHighlightsGlissando
**Goal:** Verify that selecting the target note of a glissando highlights it.
**Steps:**
1. Build a composition with a connected glissando.
2. Switch to select mode.
3. Click the target note (note 1).
4. Verify note 1 is selected and the previous note has a connected glissando.


## GraceNoteTest

### testClickClickInsertsGraceNoteAndHostWithGlissando
**Goal:** Verify that clicking once to insert a grace note then clicking again to insert the host note creates a paired grace note with a connected glissando.
**Steps:**
1. Build an empty line and select grace eighth note duration.
2. Click at staff position 0 to insert the grace note. Verify 1 GRACE_QUAVER exists.
3. Click at staff position -2 to insert the host note.
4. Verify the line has 2 elements: a GRACE_QUAVER and a pitched note.
5. Verify the grace note has a CONNECTED glissando to the host.
6. Verify grace mode is inactive after completion.

### testDeleteHostNoteAlsoDeletesGraceNote
**Goal:** Verify that deleting the host note also removes the orphaned grace note.
**Steps:**
1. Build a grace note pair (grace note + host note with connected glissando).
2. Switch to select mode, select the host note (index 1).
3. Press Delete.
4. Verify the line has 0 elements (both grace note and host removed).

### testDragLeftCancelsAndRemovesGraceNote
**Goal:** Verify that dragging left past the cancel threshold during grace note insertion cancels the insertion and removes the grace note.
**Steps:**
1. Build an empty line and select grace eighth note duration.
2. Press and hold the mouse at the insertion point, wait for the drag threshold.
3. Move the mouse left past the cancel threshold.
4. Release the mouse.
5. Verify no grace note was inserted.
6. Verify grace mode is inactive.

### testDragRightConnectsToExistingNote
**Goal:** Verify that dragging right past the connect threshold connects the grace note to an existing note.
**Steps:**
1. Build a line with one existing note and select grace eighth note duration.
2. Press and hold at the insertion point before the existing note.
3. Wait for the drag threshold, then move right past the connect threshold.
4. Release the mouse.
5. Verify 2 elements exist: a GRACE_QUAVER followed by the original note.
6. Verify the grace note has a CONNECTED glissando.
7. Verify grace mode is inactive.

### testEscapeDuringGraceNoteCancels
**Goal:** Verify that pressing Escape after inserting a grace note cancels the pairing flow and removes the grace note.
**Steps:**
1. Build an empty line and select grace eighth note duration.
2. Click to insert a grace note. Verify 1 element exists.
3. Press Escape to cancel.
4. Verify the grace note is removed and the line is empty.
5. Verify grace mode is inactive.

### testKeyChangeDurationDuringFlow
**Goal:** Verify that selecting a different duration after inserting a grace note applies it to the host note.
**Steps:**
1. Build an empty line and select grace eighth note duration.
2. Click to insert a grace note.
3. Select half note duration.
4. Click to insert the host note.
5. Verify the host note is a MINIM and the grace note is still a GRACE_QUAVER.
6. Verify the grace note has a CONNECTED glissando.

### EdgeCases

#### testNoRoomShowsAlertAndNoInsertion
**Goal:** Verify that clicking when there is no room for a grace note and host shows an error dialog and inserts nothing.
**Steps:**
1. Build a full line of notes and select grace eighth note duration.
2. Click near the right edge of the line where there is no room.
3. Dismiss the error dialog.
4. Verify the note count is unchanged.
5. Verify grace mode is inactive.

#### testSamePitchShowsAlertAndCancels
**Goal:** Verify that clicking a host note at the same pitch as the grace note shows an error dialog and cancels the flow.
**Steps:**
1. Build an empty line and select grace eighth note duration.
2. Click at staff position 0 to insert a grace note.
3. Click at the same staff position 0 to insert the host note.
4. Dismiss the error dialog.
5. Verify the grace note is removed and the line is empty.
6. Verify grace mode is inactive.

### ToolbarState

#### testNonApplicableActionsDisabledDuringFlow
**Goal:** Verify that non-applicable actions are disabled while the grace note pairing flow is active and re-enabled on exit.
**Steps:**
1. Build an empty line. Verify glissando and rest actions are enabled.
2. Select grace eighth note duration and click to insert a grace note (enters grace mode).
3. Verify glissando, rest, and grace note actions are disabled.
4. Verify duration actions (quarter note, half note) remain enabled.
5. Press Escape to cancel grace mode.
6. Verify the disabled actions are re-enabled.

#### testToolbarReflectsHostNoteAfterPairing
**Goal:** Verify that after pairing a grace note and host note, the toolbar reflects the host note's duration.
**Steps:**
1. Build an empty line and select grace eighth note duration.
2. Click to insert the grace note.
3. Switch to half note duration.
4. Click to insert the host note.
5. Verify the half note toolbar button is selected and quarter note is deselected.


## SaveLoadRoundTripTest

### testSaveAndReloadPreservesKeySignature
**Goal:** Verify that the key signature survives XML save/load.
**Steps:**
1. Create a composition with key signature set to 3 sharps.
2. Save to XML and reload.
3. Verify the reloaded composition has key type SHARPS and accidental count 3.

### testSaveAndReloadPreservesNotes
**Goal:** Verify that notes of different types and positions survive XML save/load.
**Steps:**
1. Build a composition with 4 notes: quarter (pos 0), eighth (pos -4), half (pos 4), sixteenth (pos -8).
2. Save to XML and reload.
3. Verify all note types and staff positions match.


## ToolbarReflectionTest

### testSelectionClearedRestoresState
**Goal:** Verify that clearing a selection restores toolbar buttons to their pre-selection state.
**Steps:**
1. Build a composition with a minim note that has a FLAT accidental.
2. Switch to select mode. Verify FLAT action is deselected.
3. Click the note. Verify FLAT action is selected (reflection).
4. Press Cmd+D to deselect all.
5. Verify FLAT action is restored to deselected.

### MixedTypeReflection

#### testDifferentDurationsAndRest
**Goal:** Verify toolbar state when selecting notes of different durations plus a rest.
**Steps:**
1. Build a composition with 1 quarter note + 1 half note + 1 quarter rest.
2. Switch to select mode, select all 3.
3. Verify QUARTER is deselected, HALF is deselected, all accidentals deselected.

#### testNoteAndRestSelection
**Goal:** Verify toolbar state when selecting a note and a rest together.
**Steps:**
1. Build a composition with 1 quarter note + 1 quarter rest.
2. Switch to select mode, select both.
3. Verify QUARTER is deselected (CROTCHET != CROTCHET_REST).
4. Verify all accidental buttons are deselected.

### MultiElementReflection

#### testMultipleDifferentAccidentals
**Goal:** Verify that selecting notes with different accidentals deselects accidental buttons.
**Steps:**
1. Build a composition with 2 quarter notes, first with FLAT, second with DOUBLE_FLAT.
2. Switch to select mode, select both notes.
3. Verify FLAT is deselected, DOUBLE_FLAT is deselected (accidentals differ).
4. Verify QUARTER is selected (both are quarter notes).

#### testMultipleDifferentDurations
**Goal:** Verify that selecting notes with different durations deselects all duration buttons.
**Steps:**
1. Build a composition with 1 quarter note + 1 half note.
2. Switch to select mode, select both notes.
3. Verify QUARTER is deselected, HALF is deselected (types differ).

#### testMultipleIdenticalNotes
**Goal:** Verify that selecting multiple identical notes reflects their shared properties.
**Steps:**
1. Build a composition with 2 quarter notes at different staff positions.
2. Switch to select mode, click first note, shift-click second note.
3. Verify QUARTER button is selected (both are quarter notes).
4. Verify accidental buttons are deselected (both have no accidental).

### SingleElementReflection

#### testSingleNoteSelection
**Goal:** Verify that selecting a single note reflects its properties onto toolbar buttons.
**Steps:**
1. Insert 1 quarter note at staff position 0.
2. Switch to select mode, click the note.
3. Verify QUARTER button is selected, HALF/EIGHTH are deselected.
4. Verify all accidental buttons are deselected (note has no accidental).
5. Verify DOT button is deselected.

#### testSingleRestSelection
**Goal:** Verify that selecting a single rest deselects all toolbar buttons.
**Steps:**
1. Build a composition with 1 crotchet rest.
2. Switch to select mode, click the rest.
3. Verify all duration buttons are deselected (CROTCHET_REST != CROTCHET).
4. Verify all accidental/articulation buttons are deselected (not applicable to rests).


## BarlineHitTest

### RepeatBarlines

#### testClickRepeatLeft
**Goal:** Verify that a left repeat barline can be selected by clicking on it.
**Steps:**
1. Build a composition with a crotchet followed by a left repeat barline.
2. Switch to select mode.
3. Click at the barline's position.
4. Verify the barline is the selected element.

#### testClickRepeatLeftRight
**Goal:** Verify that a left-right repeat barline can be selected by clicking on it.
**Steps:**
1. Build a composition with a crotchet followed by a left-right repeat barline.
2. Switch to select mode.
3. Click at the barline's position.
4. Verify the barline is the selected element.

#### testClickRepeatRight
**Goal:** Verify that a right repeat barline can be selected by clicking on it.
**Steps:**
1. Build a composition with a crotchet followed by a right repeat barline.
2. Switch to select mode.
3. Click at the barline's position.
4. Verify the barline is the selected element.

### StandardBarlines

#### testClickDoubleBarline
**Goal:** Verify that a double barline can be selected by clicking on it.
**Steps:**
1. Build a composition with a crotchet followed by a double barline.
2. Switch to select mode.
3. Click at the barline's position.
4. Verify the barline is the selected element.

#### testClickFinalDoubleBarline
**Goal:** Verify that a final double barline can be selected by clicking on it.
**Steps:**
1. Build a composition with a crotchet followed by a final double barline.
2. Switch to select mode.
3. Click at the barline's position.
4. Verify the barline is the selected element.

#### testClickSingleBarline
**Goal:** Verify that a single barline can be selected by clicking on it.
**Steps:**
1. Build a composition with a crotchet followed by a single barline.
2. Switch to select mode.
3. Click at the barline's position.
4. Verify the barline is the selected element.
