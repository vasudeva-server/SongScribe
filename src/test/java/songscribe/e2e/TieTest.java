/*
    SongScribe song notation program
    Copyright (C) Sri Chinmoy Centres International

    This file is part of SongScribe.

    SongScribe is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 3 of the License, or
    (at your option) any later version.

    SongScribe is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package songscribe.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import org.assertj.swing.edt.GuiActionRunner;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.music.KeyType;
import songscribe.music.StaffElement.Accidental;
import songscribe.ui.action.Actions;

/**
 * Milestone 3 E2E tests: tie creation, removal, selection semantics, drag.
 */
class TieTest extends E2ETest {

    @Test
    void testDragTiedNoteMovesOther() {
        buildTiedNotes();

        var line = composition().getLine(0);
        var originalSp = line.getElement(0).getStaffPosition();

        // Enter edit mode (drag works in edit mode)
        enterEditMode();

        // Drag the first note to a different staff position
        var targetSp = originalSp - 4;
        dragNote(0, 0, targetSp);
        performLayout(0);

        // Both tied notes should have moved to the new staff position
        assertThat(line.getElement(0).getStaffPosition()).isEqualTo(targetSp);
        assertThat(line.getElement(1).getStaffPosition()).isEqualTo(targetSp);
    }

    @Test
    void testTiePersistsThroughSaveLoad() throws Exception {
        buildTiedNotes();

        var line = composition().getLine(0);
        var tie = line.getTies().findInterval(0);
        assertThat(tie).isNotNull();

        // Round-trip save/load
        var reloaded = roundTrip(composition());

        var reloadedLine = reloaded.getLine(0);
        var reloadedTie = reloadedLine.getTies().findInterval(0);
        assertThat(reloadedTie).isNotNull();
        assertThat(reloadedTie.getStart()).isEqualTo(tie.getStart());
        assertThat(reloadedTie.getEnd()).isEqualTo(tie.getEnd());
    }

    @Nested
    class BasicOperations {

        @Test
        void testCreateTieViaSelection() {
            buildTwoAdjacentNotes();

            var line = composition().getLine(0);
            assertThat(line.getTies().isEmpty()).isTrue();

            // Select both notes
            enterSelectMode();
            clickAt(noteScreenPosition(0, 0));
            shiftClickAt(noteScreenPosition(0, 1));
            assertThat(score().getSelectionSize()).isEqualTo(2);

            // Toggle tie on
            triggerAction(Actions.TOGGLE_TIE_ACTION);
            performLayout(0);

            // Tie interval should now exist covering both notes
            assertThat(isTied(0, 0)).isTrue();
            assertThat(isTied(0, 1)).isTrue();

            var tie = line.getTies().findInterval(0);
            assertThat(tie).isNotNull();
            assertThat(tie.getStart()).isEqualTo(0);
            assertThat(tie.getEnd()).isEqualTo(1);
        }

        @Test
        void testRemoveTieViaToggle() {
            buildTiedNotes();

            var line = composition().getLine(0);
            assertThat(line.getTies().isEmpty()).isFalse();

            // Select the tied notes
            enterSelectMode();
            clickAt(noteScreenPosition(0, 0));
            shiftClickAt(noteScreenPosition(0, 1));
            assertThat(score().getSelectionSize()).isEqualTo(2);

            // Toggle tie off
            triggerAction(Actions.TOGGLE_TIE_ACTION);
            performLayout(0);

            // Tie interval should be removed
            assertThat(isTied(0, 0)).isFalse();
            assertThat(isTied(0, 1)).isFalse();
        }

        @Test
        void testSelectTiedNotesEnablesTieToggle() {
            buildTiedNotes();

            enterSelectMode();

            // Select both tied notes
            clickAt(noteScreenPosition(0, 0));
            shiftClickAt(noteScreenPosition(0, 1));

            var lss = score().getLineComponent(0).getLineSelectionState();
            assertThat(lss.getSelectionSize()).isEqualTo(2);

            // canToggleTie populates canTie and should return true for tied notes
            assertThat(lss.canToggleTie()).isTrue();
            assertThat(lss.getCanTie()).isTrue();
            assertThat(lss.getTieInterval()).isNotNull();
        }
    }

    @Nested
    class PitchValidation {

        @Test
        void testCanTieAfterNaturalFlatResetsToKeySignature() {
            // Db major: G at sp=-5 is Gb from key signature.
            // G (Gb from key sig), Gbb, G natural-flat (Gb), G (inherits natural-flat = Gb).
            // Tying last two: both resolve to Gb.
            setKeySignature(KeyType.FLATS, 5);

            selectDuration(Actions.QUARTER_NOTE_ACTION);

            // Note 1: sp=-5, NONE (gets Gb from key sig)
            clickAt(insertionPoint(0, -5));
            performLayout(0);

            // Note 2: sp=-5, DOUBLE_FLAT
            clickToolbarButton(Actions.DOUBLE_FLAT_ACTION);
            clickAt(insertionPoint(0, -5));
            performLayout(0);

            // Note 3: sp=-5, NATURAL_FLAT
            clickToolbarButton(Actions.NATURAL_FLAT_ACTION);
            clickAt(insertionPoint(0, -5));
            performLayout(0);

            // Note 4: sp=-5, NONE (inherits natural-flat = Gb)
            clickToolbarButton(Actions.NATURAL_FLAT_ACTION);
            clickAt(insertionPoint(0, -5));
            performLayout(0);

            enterSelectMode();
            clickAt(noteScreenPosition(0, 2));
            shiftClickAt(noteScreenPosition(0, 3));

            var lss = score().getLineComponent(0).getLineSelectionState();
            assertThat(lss.canToggleTie()).isTrue();
        }

        @Test
        void testCanTieEnharmonicNotes() {
            // B# (sp=0, pitch 72) and C (sp=-1, pitch 72) — different position, same pitch
            buildNotes(0, Accidental.SHARP, -1, Accidental.NONE);

            enterSelectMode();
            clickAt(noteScreenPosition(0, 0));
            shiftClickAt(noteScreenPosition(0, 1));

            var lss = score().getLineComponent(0).getLineSelectionState();
            assertThat(lss.canToggleTie()).isTrue();
        }

        @Test
        void testCanTieWithInheritedAccidental() {
            // F# (sp=4, explicit sharp) then F (sp=4, NONE) — inherits sharp, same pitch
            buildNotes(4, Accidental.SHARP, 4, Accidental.NONE);

            enterSelectMode();
            clickAt(noteScreenPosition(0, 0));
            shiftClickAt(noteScreenPosition(0, 1));

            var lss = score().getLineComponent(0).getLineSelectionState();
            assertThat(lss.canToggleTie()).isTrue();
        }

        @Test
        void testCanTieWithKeySignatureAccidental() {
            // Db major (5 flats): B at sp=0 gets Bb from key signature.
            // Two notes at sp=0 with NONE accidental should both resolve to Bb.
            buildNotesWithKeySignature(KeyType.FLATS, 5, 0, Accidental.NONE, 0, Accidental.NONE);

            enterSelectMode();
            clickAt(noteScreenPosition(0, 0));
            shiftClickAt(noteScreenPosition(0, 1));

            var lss = score().getLineComponent(0).getLineSelectionState();
            assertThat(lss.canToggleTie()).isTrue();
        }

        @Test
        void testCannotTieSamePositionDifferentAccidental() {
            // B natural (sp=0) and B# (sp=0) — same staff position, different pitch
            buildNotes(0, Accidental.NATURAL, 0, Accidental.SHARP);

            enterSelectMode();
            clickAt(noteScreenPosition(0, 0));
            shiftClickAt(noteScreenPosition(0, 1));

            var lss = score().getLineComponent(0).getLineSelectionState();
            assertThat(lss.canToggleTie()).isFalse();
        }

        @Test
        void testCannotTieWhenNaturalCancelsInheritedAccidental() {
            // F# (sp=4), F (sp=4, NONE inherits sharp), F natural (sp=4, explicit natural)
            // Tying last two: F# vs F natural — different pitch
            buildThreeNotes();

            enterSelectMode();
            clickAt(noteScreenPosition(0, 1));
            shiftClickAt(noteScreenPosition(0, 2));

            var lss = score().getLineComponent(0).getLineSelectionState();
            assertThat(lss.canToggleTie()).isFalse();
        }
    }

    // -- Helpers --

    private void buildTwoAdjacentNotes() {
        selectDuration(Actions.QUARTER_NOTE_ACTION);
        clickAt(insertionPoint(0, 0));
        performLayout(0);
        clickAt(insertionPoint(0, 0));
        performLayout(0);
    }

    private void buildTiedNotes() {
        buildTwoAdjacentNotes();

        enterSelectMode();
        clickAt(noteScreenPosition(0, 0));
        shiftClickAt(noteScreenPosition(0, 1));
        triggerAction(Actions.TOGGLE_TIE_ACTION);
        performLayout(0);
        enterEditMode();
    }

    private void buildNotes(int sp1, Accidental acc1, int sp2, Accidental acc2) {
        selectDuration(Actions.QUARTER_NOTE_ACTION);

        selectAccidental(acc1);
        clickAt(insertionPoint(0, sp1));
        performLayout(0);

        selectAccidental(acc2);
        clickAt(insertionPoint(0, sp2));
        performLayout(0);

        deselectAccidental();
    }

    private void buildThreeNotes() {
        selectDuration(Actions.QUARTER_NOTE_ACTION);

        // Note 1: F# (sp=4, SHARP)
        clickAccidentalAction(Actions.SHARP_ACTION);
        clickAt(insertionPoint(0, 4));
        performLayout(0);

        // Note 2: F (sp=4, NONE — inherits sharp from note 1)
        clickAccidentalAction(Actions.SHARP_ACTION);
        clickAt(insertionPoint(0, 4));
        performLayout(0);

        // Note 3: F natural (sp=4, NATURAL)
        clickToolbarButton(Actions.NATURAL_ACTION);
        clickAt(insertionPoint(0, 4));
        performLayout(0);

        clickToolbarButton(Actions.NATURAL_ACTION);
    }

    private void buildNotesWithKeySignature(
        KeyType keyType, int keyCount,
        int sp1, Accidental acc1, int sp2, Accidental acc2
    ) {
        setKeySignature(keyType, keyCount);

        selectDuration(Actions.QUARTER_NOTE_ACTION);

        selectAccidental(acc1);
        clickAt(insertionPoint(0, sp1));
        performLayout(0);

        selectAccidental(acc2);
        clickAt(insertionPoint(0, sp2));
        performLayout(0);

        deselectAccidental();
    }

    private void setKeySignature(KeyType keyType, int keyCount) {
        GuiActionRunner.execute(() -> {
            var line = composition().getLine(0);
            line.setKeyType(keyType);
            line.setKeyAccidentalCount(keyCount);
        });
        performLayout(0);
    }


}
