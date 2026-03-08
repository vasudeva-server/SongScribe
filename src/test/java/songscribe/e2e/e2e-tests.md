# E2E Test Guide

Each test resets the composition to a blank state before running.
The status overlay shows the test counter and the test name.

## ElementInsertionTest

### NoteInsertion

#### testInsertQuarterNote
**Goal:** Verify that clicking on the staff inserts a quarter note.
**Steps:**
1. Select quarter note duration from the toolbar.
2. Click on the staff at the middle line (staff position 0).
3. Verify 1 note exists, is a quarter note, and is at staff position 0.

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

#### testInsertRest
**Goal:** Verify that enabling rest mode inserts a rest instead of a note.
**Steps:**
1. Select quarter note duration.
2. Click the rest mode toggle button.
3. Click on the staff.
4. Verify 1 note exists and it is a rest.

### EditOperations

#### testReplaceNoteAtSameXDifferentPitch
**Goal:** Verify that clicking at the same X position as an existing note replaces it.
**Steps:**
1. Insert a quarter note at staff position 0.
2. Switch to half note duration.
3. Click at the same X as the existing note but at a different staff position (-4).
4. Verify there is still only 1 note, now a half note at staff position -4.

#### testDragNoteToNewStaffPosition
**Goal:** Verify that dragging a note in edit mode changes its pitch.
**Steps:**
1. Insert a quarter note at staff position 0.
2. Click the note head in edit mode.
3. Drag the note to staff position -4.
4. Verify the note moved to staff position -4.


## SelectionTest (selection/)

### BasicSelection

#### testClickToSelectNote
**Goal:** Verify that clicking a note in select mode selects it.
**Steps:**
1. Build a composition with 3 quarter notes at positions 0, -2, -4.
2. Switch to select mode.
3. Click the second note.
4. Verify only the second note is selected.

#### testClickEmptySpaceDeselects
**Goal:** Verify that clicking empty space deselects all notes.
**Steps:**
1. Build a 3-note composition.
2. Switch to select mode and click a note to select it.
3. Click on empty space far to the right of all notes.
4. Verify nothing is selected.

#### testModeToggle
**Goal:** Verify that the mode cycle button toggles between edit and select mode.
**Steps:**
1. Ensure edit mode is active, verify `score().getMode()` is `NOTE_EDIT`.
2. Click the mode cycle button to switch to select mode, verify mode is `SELECT`.
3. Click again to switch back to edit mode, verify mode is `NOTE_EDIT`.

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

### DeselectAndLineSelection

#### testMetaDDeselectsAll
**Goal:** Verify that Cmd+D deselects all notes.
**Steps:**
1. Build a 3-note composition.
2. Select all 3 notes via click + shift-click.
3. Press Cmd+D.
4. Verify nothing is selected.

#### testClickInStaffSelectsLine
**Goal:** Verify that clicking within the staff area (but not on a note) selects the line.
**Steps:**
1. Build a 3-note composition.
2. Switch to select mode.
3. Click within the staff to the right of all notes.
4. Verify the line is selected.


## SelectionApplyTest (selection/)

### DurationChanges

#### testSelectNotesClickDurationVerifyChanged
**Goal:** Verify that clicking a duration toolbar button in select mode changes the selected notes' duration.
**Steps:**
1. Build a composition with 3 eighth notes at positions 0, -2, -4.
2. Switch to select mode, select all 3 notes.
3. Click the quarter note toolbar button.
4. Verify all 3 notes are now crotchets.
5. Verify the selection remains active.

#### testDurationChangePreservesNoteRestKind
**Goal:** Verify that changing duration preserves note/rest distinction.
**Steps:**
1. Build a composition with an eighth note, an eighth rest, and an eighth note.
2. Switch to select mode, select all 3 elements.
3. Click the half note toolbar button.
4. Verify the notes became minims and the rest became a minim rest.

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

#### testSelectNotesAndRestsClickDotVerifyBothGetDots
**Goal:** Verify that clicking the dot button applies dots to both notes and rests.
**Steps:**
1. Build a composition with a quarter note, a quarter rest, and a quarter note.
2. Switch to select mode, select all 3 elements.
3. Click the dot toolbar button.
4. Verify all 3 elements have a dot count of 1.

#### testApplyFermataThenRemove
**Goal:** Verify that fermata can be applied and toggled off via the menu.
**Steps:**
1. Build a composition with 2 quarter notes.
2. Switch to select mode, select both notes.
3. Click the "Fermata" menu item. Verify both notes have fermata.
4. Click "Fermata" again. Verify fermata is removed from both notes.

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

### testTiePersistsThroughSaveLoad
**Goal:** Verify that ties survive XML save/load.
**Steps:**
1. Build a composition with 2 tied quarter notes.
2. Save the composition to XML and reload it.
3. Verify the reloaded composition has the same tie interval.

### testDragTiedNoteMovesOther
**Goal:** Verify that dragging one tied note moves the other tied note too.
**Steps:**
1. Build a composition with 2 tied quarter notes at the same pitch.
2. In edit mode, drag the first note down by 4 staff positions.
3. Verify both notes moved to the new staff position.

### PitchValidation

#### testCannotTieSamePositionDifferentAccidental
**Goal:** Verify that notes at the same staff position but with different accidentals cannot be tied.
**Steps:**
1. Build a composition with B natural (sp=0) and B# (sp=0).
2. Switch to select mode, select both notes.
3. Verify `canToggleTie()` returns false.

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

#### testCannotTieWhenNaturalCancelsInheritedAccidental
**Goal:** Verify that a natural accidental cancels an inherited accidental, preventing a tie.
**Steps:**
1. Build a composition with F# (sp=4), F (sp=4, inherits sharp), F natural (sp=4, explicit natural).
2. Switch to select mode, select the last two notes (F# inherited vs F natural).
3. Verify `canToggleTie()` returns false.

#### testCanTieWithKeySignatureAccidental
**Goal:** Verify that notes resolving to the same pitch via key signature can be tied.
**Steps:**
1. Build a composition in Db major (5 flats) with two notes at sp=0 with NONE accidental (both resolve to Bb).
2. Switch to select mode, select both notes.
3. Verify `canToggleTie()` returns true.

#### testCanTieAfterNaturalFlatResetsToKeySignature
**Goal:** Verify that a natural-flat accidental resets to the key signature pitch.
**Steps:**
1. Build a composition in Db major: G (Gb from key sig), Gbb, G natural-flat (Gb), G (inherits natural-flat = Gb).
2. Switch to select mode, select the last two notes.
3. Verify `canToggleTie()` returns true (both resolve to Gb).


## GlissandoTest

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

### testGlissandoPersistsThroughSaveLoad
**Goal:** Verify that glissandos survive XML save/load.
**Steps:**
1. Build a composition with a connected glissando.
2. Save the composition to XML and reload it.
3. Verify the reloaded note has the same glissando type.

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


## ToolbarReflectionTest

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

### MultiElementReflection

#### testMultipleIdenticalNotes
**Goal:** Verify that selecting multiple identical notes reflects their shared properties.
**Steps:**
1. Build a composition with 2 quarter notes at different staff positions.
2. Switch to select mode, click first note, shift-click second note.
3. Verify QUARTER button is selected (both are quarter notes).
4. Verify accidental buttons are deselected (both have no accidental).

#### testMultipleDifferentDurations
**Goal:** Verify that selecting notes with different durations deselects all duration buttons.
**Steps:**
1. Build a composition with 1 quarter note + 1 half note.
2. Switch to select mode, select both notes.
3. Verify QUARTER is deselected, HALF is deselected (types differ).

#### testMultipleDifferentAccidentals
**Goal:** Verify that selecting notes with different accidentals deselects accidental buttons.
**Steps:**
1. Build a composition with 2 quarter notes, first with FLAT, second with DOUBLE_FLAT.
2. Switch to select mode, select both notes.
3. Verify FLAT is deselected, DOUBLE_FLAT is deselected (accidentals differ).
4. Verify QUARTER is selected (both are quarter notes).

### MixedTypeReflection

#### testNoteAndRestSelection
**Goal:** Verify toolbar state when selecting a note and a rest together.
**Steps:**
1. Build a composition with 1 quarter note + 1 quarter rest.
2. Switch to select mode, select both.
3. Verify QUARTER is deselected (CROTCHET != CROTCHET_REST).
4. Verify all accidental buttons are deselected.

#### testDifferentDurationsAndRest
**Goal:** Verify toolbar state when selecting notes of different durations plus a rest.
**Steps:**
1. Build a composition with 1 quarter note + 1 half note + 1 quarter rest.
2. Switch to select mode, select all 3.
3. Verify QUARTER is deselected, HALF is deselected, all accidentals deselected.

### testSelectionClearedRestoresState
**Goal:** Verify that clearing a selection restores toolbar buttons to their pre-selection state.
**Steps:**
1. Build a composition with a minim note that has a FLAT accidental.
2. Switch to select mode. Verify FLAT action is deselected.
3. Click the note. Verify FLAT action is selected (reflection).
4. Press Cmd+D to deselect all.
5. Verify FLAT action is restored to deselected.


## BarlineHitTest

### StandardBarlines

#### testClickSingleBarline
**Goal:** Verify that a single barline can be selected by clicking on it.
**Steps:**
1. Build a composition with a crotchet followed by a single barline.
2. Switch to select mode.
3. Click at the barline's position.
4. Verify the barline is the selected element.

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

### RepeatBarlines

#### testClickRepeatLeft
**Goal:** Verify that a left repeat barline can be selected by clicking on it.
**Steps:**
1. Build a composition with a crotchet followed by a left repeat barline.
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

#### testClickRepeatLeftRight
**Goal:** Verify that a left-right repeat barline can be selected by clicking on it.
**Steps:**
1. Build a composition with a crotchet followed by a left-right repeat barline.
2. Switch to select mode.
3. Click at the barline's position.
4. Verify the barline is the selected element.
