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

import org.assertj.swing.edt.GuiActionRunner;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import songscribe.music.Composition;
import songscribe.music.KeyType;
import songscribe.music.Line;
import songscribe.music.Note.Accidental;
import songscribe.music.NoteType;
import songscribe.data.TieInterval;
import songscribe.ui.action.Actions;
import songscribe.ui.component.MainFrame;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Milestone 3 E2E tests: tie creation, removal, selection semantics, drag.
 */
@Order(4)
class TieTest extends E2ETest {

    @Test @Order(1)
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

    @Test @Order(2)
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

    @Test @Order(3)
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

    @Test @Order(4)
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

    @Test @Order(5)
    void testDragTiedNoteMovesOther() {
        buildTiedNotes();

        var line = composition().getLine(0);
        var originalSp = line.getNote(0).getStaffPosition();

        // Enter edit mode (drag works in edit mode)
        enterEditMode();

        // Drag the first note to a different staff position
        var targetSp = originalSp - 4;
        dragNote(0, 0, targetSp);
        performLayout(0);

        // Both tied notes should have moved to the new staff position
        assertThat(line.getNote(0).getStaffPosition()).isEqualTo(targetSp);
        assertThat(line.getNote(1).getStaffPosition()).isEqualTo(targetSp);
    }


    @Test @Order(6)
    void testCannotTieSamePositionDifferentAccidental() {
        // B natural (sp=0) and B# (sp=0) — same staff position, different pitch
        buildNotes(0, Accidental.NATURAL, 0, Accidental.SHARP);

        enterSelectMode();
        clickAt(noteScreenPosition(0, 0));
        shiftClickAt(noteScreenPosition(0, 1));

        var lss = score().getLineComponent(0).getLineSelectionState();
        assertThat(lss.canToggleTie()).isFalse();
    }

    @Test @Order(7)
    void testCanTieEnharmonicNotes() {
        // B# (sp=0, pitch 72) and C (sp=-1, pitch 72) — different position, same pitch
        buildNotes(0, Accidental.SHARP, -1, Accidental.NONE);

        enterSelectMode();
        clickAt(noteScreenPosition(0, 0));
        shiftClickAt(noteScreenPosition(0, 1));

        var lss = score().getLineComponent(0).getLineSelectionState();
        assertThat(lss.canToggleTie()).isTrue();
    }

    @Test @Order(8)
    void testCanTieWithInheritedAccidental() {
        // F# (sp=4, explicit sharp) then F (sp=4, NONE) — inherits sharp, same pitch
        buildNotes(4, Accidental.SHARP, 4, Accidental.NONE);

        enterSelectMode();
        clickAt(noteScreenPosition(0, 0));
        shiftClickAt(noteScreenPosition(0, 1));

        var lss = score().getLineComponent(0).getLineSelectionState();
        assertThat(lss.canToggleTie()).isTrue();
    }

    @Test @Order(9)
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

    @Test @Order(10)
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

    @Test @Order(11)
    void testCanTieAfterNaturalFlatResetsToKeySignature() {
        // Db major: G at sp=-5 is Gb from key signature.
        // G (Gb from key sig), Gbb, G natural-flat (Gb), G (inherits natural-flat = Gb).
        // Tying last two: both resolve to Gb.
        GuiActionRunner.execute(() -> {
            var composition = new Composition(MainFrame.getInstance());
            var line = new Line();
            line.setKeyType(KeyType.FLATS);
            line.setKeyAccidentalCount(5);

            var note1 = NoteType.CROTCHET.newInstance();
            note1.setStaffPosition(-5);
            line.addNote(note1);

            var note2 = NoteType.CROTCHET.newInstance();
            note2.setStaffPosition(-5);
            note2.setAccidental(Accidental.DOUBLE_FLAT);
            line.addNote(note2);

            var note3 = NoteType.CROTCHET.newInstance();
            note3.setStaffPosition(-5);
            note3.setAccidental(Accidental.NATURAL_FLAT);
            line.addNote(note3);

            var note4 = NoteType.CROTCHET.newInstance();
            note4.setStaffPosition(-5);
            line.addNote(note4);

            composition.addLine(0, line);
            score().setComposition(composition);
        });

        performLayout(0);

        enterSelectMode();
        clickAt(noteScreenPosition(0, 2));
        shiftClickAt(noteScreenPosition(0, 3));

        var lss = score().getLineComponent(0).getLineSelectionState();
        assertThat(lss.canToggleTie()).isTrue();
    }

    // -- Helpers --

    private void buildTwoAdjacentNotes() {
        GuiActionRunner.execute(() -> {
            var composition = new Composition(MainFrame.getInstance());
            var line = new Line();

            var note1 = NoteType.CROTCHET.newInstance();
            note1.setStaffPosition(0);
            line.addNote(note1);

            var note2 = NoteType.CROTCHET.newInstance();
            note2.setStaffPosition(0);
            line.addNote(note2);

            composition.addLine(0, line);
            score().setComposition(composition);
        });

        performLayout(0);
    }

    private void buildTiedNotes() {
        GuiActionRunner.execute(() -> {
            var composition = new Composition(MainFrame.getInstance());
            var line = new Line();

            var note1 = NoteType.CROTCHET.newInstance();
            note1.setStaffPosition(0);
            line.addNote(note1);

            var note2 = NoteType.CROTCHET.newInstance();
            note2.setStaffPosition(0);
            line.addNote(note2);

            line.getTies().addInterval(new TieInterval(0, 1));

            composition.addLine(0, line);
            score().setComposition(composition);
        });

        performLayout(0);
    }

    private void buildNotes(int sp1, Accidental acc1, int sp2, Accidental acc2) {
        GuiActionRunner.execute(() -> {
            var composition = new Composition(MainFrame.getInstance());
            var line = new Line();

            var note1 = NoteType.CROTCHET.newInstance();
            note1.setStaffPosition(sp1);
            note1.setAccidental(acc1);
            line.addNote(note1);

            var note2 = NoteType.CROTCHET.newInstance();
            note2.setStaffPosition(sp2);
            note2.setAccidental(acc2);
            line.addNote(note2);

            composition.addLine(0, line);
            score().setComposition(composition);
        });

        performLayout(0);
    }

    private void buildThreeNotes() {
        GuiActionRunner.execute(() -> {
            var composition = new Composition(MainFrame.getInstance());
            var line = new Line();

            var note1 = NoteType.CROTCHET.newInstance();
            note1.setStaffPosition(4);
            note1.setAccidental(Accidental.SHARP);
            line.addNote(note1);

            var note2 = NoteType.CROTCHET.newInstance();
            note2.setStaffPosition(4);
            line.addNote(note2);

            var note3 = NoteType.CROTCHET.newInstance();
            note3.setStaffPosition(4);
            note3.setAccidental(Accidental.NATURAL);
            line.addNote(note3);

            composition.addLine(0, line);
            score().setComposition(composition);
        });

        performLayout(0);
    }

    private void buildNotesWithKeySignature(
        KeyType keyType, int keyCount,
        int sp1, Accidental acc1, int sp2, Accidental acc2
    ) {
        GuiActionRunner.execute(() -> {
            var composition = new Composition(MainFrame.getInstance());
            var line = new Line();
            line.setKeyType(keyType);
            line.setKeyAccidentalCount(keyCount);

            var note1 = NoteType.CROTCHET.newInstance();
            note1.setStaffPosition(sp1);
            note1.setAccidental(acc1);
            line.addNote(note1);

            var note2 = NoteType.CROTCHET.newInstance();
            note2.setStaffPosition(sp2);
            note2.setAccidental(acc2);
            line.addNote(note2);

            composition.addLine(0, line);
            score().setComposition(composition);
        });

        performLayout(0);
    }

}
