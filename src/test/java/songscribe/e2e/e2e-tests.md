# E2E Test Guide

Each test resets the composition to a blank state before running.
The status overlay shows the test counter and the test name.

## NoteInsertionTest

### testInsertQuarterNote
**Goal:** Verify that clicking on the staff inserts a quarter note.
**Steps:**
1. Select quarter note duration from the toolbar.
2. Click on the staff at the middle line (staff position 0).
3. Verify 1 note exists, is a quarter note, and is at staff position 0.

### testInsertEighthNote
**Goal:** Verify that clicking on the staff inserts an eighth note.
**Steps:**
1. Select eighth note duration from the toolbar.
2. Click on the staff below the middle line (staff position -2).
3. Verify 1 note exists, is an eighth note, and is at staff position -2.

### testInsertHalfNote
**Goal:** Verify that clicking on the staff inserts a half note.
**Steps:**
1. Select half note duration from the toolbar.
2. Click on the staff above the middle line (staff position 2).
3. Verify 1 note exists, is a half note, and is at staff position 2.

### testInsertNoteAtDifferentStaffPositions
**Goal:** Verify that multiple notes can be inserted at various staff positions.
**Steps:**
1. Select quarter note duration.
2. Click at four different staff positions (0, -4, 4, -8) in sequence.
3. Verify 4 notes exist, each at the expected staff position.

### testInsertRest
**Goal:** Verify that enabling rest mode inserts a rest instead of a note.
**Steps:**
1. Select quarter note duration.
2. Click the rest mode toggle button.
3. Click on the staff.
4. Verify 1 note exists and it is a rest.

### testReplaceNoteAtSameXDifferentPitch
**Goal:** Verify that clicking at the same X position as an existing note replaces it.
**Steps:**
1. Insert a quarter note at staff position 0.
2. Switch to half note duration.
3. Click at the same X as the existing note but at a different staff position (-4).
4. Verify there is still only 1 note, now a half note at staff position -4.

### testDragNoteToNewStaffPosition
**Goal:** Verify that dragging a note in select mode changes its pitch.
**Steps:**
1. Insert a quarter note at staff position 0.
2. Switch to select mode and click the note to select it.
3. Drag the note to staff position -4.
4. Verify the note moved to staff position -4.


## SelectionTest

### testClickToSelectNote
**Goal:** Verify that clicking a note in select mode selects it.
**Steps:**
1. Build a composition with 3 quarter notes at positions 0, -2, -4.
2. Switch to select mode.
3. Click the second note.
4. Verify only the second note is selected.

### testClickEmptySpaceDeselects
**Goal:** Verify that clicking empty space deselects all notes.
**Steps:**
1. Build a 3-note composition.
2. Switch to select mode and click a note to select it.
3. Click on empty space far to the right of all notes.
4. Verify nothing is selected.

### testModeToggle
**Goal:** Verify that the mode cycle button toggles between edit and select mode.
**Steps:**
1. Ensure edit mode is active, verify `score().getMode()` is `NOTE_EDIT`.
2. Click the mode cycle button to switch to select mode, verify mode is `SELECT`.
3. Click again to switch back to edit mode, verify mode is `NOTE_EDIT`.

### testShiftClickExtendsSelection
**Goal:** Verify that shift-clicking extends the selection range.
**Steps:**
1. Build a 3-note composition.
2. Switch to select mode.
3. Click the first note to select it.
4. Shift-click the third note.
5. Verify all 3 notes are selected.

### testShiftClickShrinksSelection
**Goal:** Verify that shift-clicking a closer note shrinks the selection range.
**Steps:**
1. Build a 3-note composition.
2. Select notes 1-3 via click + shift-click.
3. Shift-click the second note.
4. Verify only notes 1-2 are selected, note 3 is deselected.

### testMetaDDeselectsAll
**Goal:** Verify that Cmd+D deselects all notes.
**Steps:**
1. Build a 3-note composition.
2. Select all 3 notes via click + shift-click.
3. Press Cmd+D.
4. Verify nothing is selected.

### testClickInStaffSelectsLine
**Goal:** Verify that clicking within the staff area (but not on a note) selects the line.
**Steps:**
1. Build a 3-note composition.
2. Switch to select mode.
3. Click within the staff to the right of all notes.
4. Verify the line is selected.


## BeamingTest

### testAutoBeamingOnInsertion
**Goal:** Verify that consecutive eighth notes are automatically beamed.
**Steps:**
1. Switch to edit mode and select eighth note duration.
2. Insert two eighth notes at different staff positions.
3. Verify both notes are beamed together in one beam group (interval 0-1).

### testToggleBeamingOnSelection
**Goal:** Verify that selecting two unbeamed eighth notes and toggling beam adds a beam.
**Steps:**
1. Build a composition with 2 unbeamed eighth notes.
2. Switch to select mode, select both notes.
3. Click the toggle beam button.
4. Verify both notes are now beamed.

### testToggleBeamingRemovesExistingBeam
**Goal:** Verify that toggling beam on already-beamed notes removes the beam.
**Steps:**
1. Build a composition with 2 beamed eighth notes.
2. Switch to select mode, select both notes.
3. Click the toggle beam button.
4. Verify both notes are now unbeamed.

### testFlipStemDirection
**Goal:** Verify that flipping the stem of a beamed note changes its direction.
**Steps:**
1. Build a composition with 2 beamed eighth notes.
2. Switch to select mode, select the first note.
3. Click the flip stem direction button.
4. Verify the note's stem direction is no longer automatic and is flipped.

### testFlipStemDirectionUnbeamed
**Goal:** Verify that flipping the stem works on unbeamed quarter notes too.
**Steps:**
1. Build a composition with 2 quarter notes.
2. Switch to select mode, select the first note.
3. Click the flip stem direction button.
4. Verify the note's stem direction is no longer automatic and is flipped.

### testStemDirectionPersistsThroughSaveLoad
**Goal:** Verify that a flipped stem direction survives XML save/load.
**Steps:**
1. Build a composition with 2 quarter notes.
2. Select the first note and flip its stem.
3. Save the composition to XML and reload it.
4. Verify the reloaded note still has a non-automatic, flipped stem direction.


## TieTest

### testCreateTieViaSelection
**Goal:** Verify that selecting two adjacent notes and toggling tie creates a tie.
**Steps:**
1. Build a composition with 2 quarter notes at the same pitch.
2. Switch to select mode, select both notes.
3. Click the toggle tie button.
4. Verify a tie interval exists covering notes 0-1.

### testRemoveTieViaToggle
**Goal:** Verify that toggling tie on already-tied notes removes the tie.
**Steps:**
1. Build a composition with 2 tied quarter notes.
2. Switch to select mode, select both notes.
3. Click the toggle tie button.
4. Verify the tie is removed.

### testTiePersistsThroughSaveLoad
**Goal:** Verify that ties survive XML save/load.
**Steps:**
1. Build a composition with 2 tied quarter notes.
2. Save the composition to XML and reload it.
3. Verify the reloaded composition has the same tie interval.

### testSelectTiedNotesEnablesTieToggle
**Goal:** Verify that selecting both tied notes enables the tie toggle and populates tie context.
**Steps:**
1. Build a composition with 2 tied quarter notes.
2. Switch to select mode.
3. Click the first note, then shift-click the second note.
4. Verify 2 notes are selected.
5. Verify `canToggleTie()` returns true and tie context indicates the tie can be toggled.

### testDragTiedNoteMovesOther
**Goal:** Verify that dragging one tied note moves the other tied note too.
**Steps:**
1. Build a composition with 2 tied quarter notes at the same pitch.
2. In edit mode, drag the first note down by 4 staff positions.
3. Verify both notes moved to the new staff position.


## GlissandoTest

### testInsertConnectedGlissando
**Goal:** Verify that inserting a connected glissando between two notes works.
**Steps:**
1. Build a composition with 2 notes at different pitches (0 and -4).
2. Switch to edit mode, select the glissando tool.
3. Click between the two notes.
4. Verify note 0 has a connected glissando.

### testInsertSlideOutGlissando
**Goal:** Verify that inserting a slide-out glissando on a note works.
**Steps:**
1. Build a composition with 2 notes at different pitches.
2. Switch to edit mode, select the slide-out tool.
3. Click between the notes.
4. Verify note 0 has a slide-out glissando.

### testSelectGlissandoByClick
**Goal:** Verify that clicking on a glissando line selects it.
**Steps:**
1. Build a composition with a connected glissando between 2 notes.
2. Switch to select mode.
3. Click the midpoint of the glissando line.
4. Verify the glissando is selected (not a note).

### testSelectSourceNoteHighlightsGlissando
**Goal:** Verify that selecting the source note of a glissando highlights it.
**Steps:**
1. Build a composition with a connected glissando.
2. Switch to select mode.
3. Click the source note (note 0).
4. Verify note 0 is selected and has a glissando.

### testSelectTargetNoteHighlightsGlissando
**Goal:** Verify that selecting the target note of a glissando highlights it.
**Steps:**
1. Build a composition with a connected glissando.
2. Switch to select mode.
3. Click the target note (note 1).
4. Verify note 1 is selected and the previous note has a connected glissando.

### testDeleteSelectedGlissando
**Goal:** Verify that pressing Delete with a glissando selected removes only the glissando.
**Steps:**
1. Build a composition with a connected glissando.
2. Switch to select mode, click the glissando midpoint to select it.
3. Press Delete.
4. Verify the glissando is removed but both notes still exist.

### testGlissandoPersistsThroughSaveLoad
**Goal:** Verify that glissandos survive XML save/load.
**Steps:**
1. Build a composition with a connected glissando.
2. Save the composition to XML and reload it.
3. Verify the reloaded note has the same glissando type.

### testDeleteSourceNoteRemovesConnectedGlissando
**Goal:** Verify that deleting the source note of a connected glissando removes the glissando.
**Steps:**
1. Build a composition with a connected glissando.
2. Switch to select mode, select the source note (note 0).
3. Press Delete.
4. Verify only 1 note remains and it has no glissando.

### testDeleteSourceNoteRemovesSlideOut
**Goal:** Verify that deleting a note with a slide-out removes the note entirely.
**Steps:**
1. Build a composition with 1 note that has a slide-out glissando.
2. Switch to select mode, select the note.
3. Press Delete.
4. Verify the line has 0 notes.

### testDeleteTargetNoteRemovesConnectedGlissando
**Goal:** Verify that deleting the target note of a connected glissando cleans up the source.
**Steps:**
1. Build a composition with a connected glissando.
2. Switch to select mode, select the target note (note 1).
3. Press Delete.
4. Verify only 1 note remains and it has no glissando.

### testDragToUnisonRemovesConnectedGlissando
**Goal:** Verify that dragging notes to the same pitch removes a connected glissando.
**Steps:**
1. Build a composition with a connected glissando between notes at different pitches.
2. In edit mode, drag the source note to the same pitch as the target.
3. Verify the glissando is removed (unison glissando is meaningless).


## SaveLoadRoundTripTest

### testSaveAndReloadPreservesNotes
**Goal:** Verify that notes of different types and positions survive XML save/load.
**Steps:**
1. Build a composition with 4 notes: quarter (pos 0), eighth (pos -4), half (pos 4), sixteenth (pos -8).
2. Save to XML and reload.
3. Verify all note types and staff positions match.

### testSaveAndReloadPreservesKeySignature
**Goal:** Verify that the key signature survives XML save/load.
**Steps:**
1. Create a composition with key signature set to 3 sharps.
2. Save to XML and reload.
3. Verify the reloaded composition has key type SHARPS and accidental count 3.
