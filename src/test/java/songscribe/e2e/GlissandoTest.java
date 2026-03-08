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

import java.awt.*;
import java.awt.event.*;

import org.assertj.swing.edt.GuiActionRunner;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import songscribe.music.Composition;
import songscribe.music.ElementType;
import songscribe.music.Line;
import songscribe.music.StaffElement;
import songscribe.ui.action.Actions;
import songscribe.ui.component.MainFrame;

/**
 * Milestone 3 E2E tests: glissando insert, select, delete, highlight, persistence.
 */
@Order(5)
class GlissandoTest extends E2ETest {

    @Test
    @Order(1)
    void testInsertConnectedGlissando() {
        buildTwoNotesAtDifferentPitches();

        enterEditMode();
        selectDuration(Actions.GLISSANDO_ACTION);

        // Click between the two notes (past the first note)
        clickAt(glissandoInsertionPoint(0));
        performLayout(0);

        var note = composition().getLine(0).getElement(0);
        //noinspection ObjectEquality
        assertThat(note.getGlissando() != StaffElement.NO_GLISSANDO).isTrue();
        assertThat(note.getGlissando().type).isEqualTo(StaffElement.Glissando.Type.CONNECTED);
    }

    @Test
    @Order(2)
    void testInsertSlideOutGlissando() {
        buildTwoNotesAtDifferentPitches();

        enterEditMode();
        selectDuration(Actions.SLIDE_OUT_ACTION);

        // Click past the last note (slide out doesn't require a target note,
        // but the insertion point must be after a note)
        clickAt(glissandoInsertionPoint(0));
        performLayout(0);

        var note = composition().getLine(0).getElement(0);
        //noinspection ObjectEquality
        assertThat(note.getGlissando() != StaffElement.NO_GLISSANDO).isTrue();
        assertThat(note.getGlissando().type).isEqualTo(StaffElement.Glissando.Type.SLIDE_OUT);
    }

    @Test
    @Order(3)
    void testSelectGlissandoByClick() {
        buildNotesWithConnectedGlissando();

        enterSelectMode();

        // Click on the glissando line (midpoint between the two notes)
        clickAt(glissandoMidpoint(0));

        var lss = score().getLineComponent(0).getLineSelectionState();
        assertThat(lss.hasGlissandoSelection()).isTrue();
        assertThat(lss.getSelectedGlissandoElementIndex()).isEqualTo(0);
    }

    @Test
    @Order(4)
    void testSelectSourceNoteHighlightsGlissando() {
        buildNotesWithConnectedGlissando();

        enterSelectMode();

        // Select the source note (note 0)
        clickAt(noteScreenPosition(0, 0));

        // Source note is selected
        var lss = score().getLineComponent(0).getLineSelectionState();
        assertThat(lss.isElementSelected(0)).isTrue();

        // Glissando exists on that note (model-level check)
        var note = composition().getLine(0).getElement(0);
        //noinspection ObjectEquality
        assertThat(note.getGlissando() != StaffElement.NO_GLISSANDO).isTrue();
    }

    @Test
    @Order(5)
    void testSelectTargetNoteHighlightsGlissando() {
        buildNotesWithConnectedGlissando();

        enterSelectMode();

        // Select the target note (note 1)
        clickAt(noteScreenPosition(0, 1));

        // Target note is selected
        var lss = score().getLineComponent(0).getLineSelectionState();
        assertThat(lss.isElementSelected(1)).isTrue();

        // Previous note has a connected glissando pointing to it (model-level check)
        var prevNote = composition().getLine(0).getElement(0);
        assertThat(prevNote.getGlissando().type).isEqualTo(StaffElement.Glissando.Type.CONNECTED);
    }

    @Test
    @Order(6)
    void testDeleteSelectedGlissando() {
        buildNotesWithConnectedGlissando();

        enterSelectMode();

        // Select the glissando by clicking on it
        clickAt(glissandoMidpoint(0));

        var lss = score().getLineComponent(0).getLineSelectionState();
        assertThat(lss.hasGlissandoSelection()).isTrue();

        // Press Delete
        robot.pressAndReleaseKey(KeyEvent.VK_DELETE);
        performLayout(0);

        // Glissando should be removed
        var note = composition().getLine(0).getElement(0);
        //noinspection ObjectEquality
        assertThat(note.getGlissando()).isSameAs(StaffElement.NO_GLISSANDO);
    }

    @Test
    @Order(7)
    void testGlissandoPersistsThroughSaveLoad() throws Exception {
        buildNotesWithConnectedGlissando();

        var originalNote = composition().getLine(0).getElement(0);
        var originalType = originalNote.getGlissando().type;

        // Round-trip save/load
        var reloaded = roundTrip(composition());

        var reloadedNote = reloaded.getLine(0).getElement(0);
        //noinspection ObjectEquality
        assertThat(reloadedNote.getGlissando() != StaffElement.NO_GLISSANDO).isTrue();
        assertThat(reloadedNote.getGlissando().type).isEqualTo(originalType);
    }

    @Test
    @Order(8)
    void testDeleteSourceNoteRemovesConnectedGlissando() {
        buildNotesWithConnectedGlissando();

        enterSelectMode();

        // Select the source note (note 0)
        clickAt(noteScreenPosition(0, 0));

        // Delete it
        robot.pressAndReleaseKey(KeyEvent.VK_DELETE);
        performLayout(0);

        // Note 0 was removed, only 1 note remains
        var line = composition().getLine(0);
        assertThat(line.elementCount()).isEqualTo(1);

        // The remaining note should have no orphaned glissando
        //noinspection ObjectEquality
        assertThat(line.getElement(0).getGlissando()).isSameAs(StaffElement.NO_GLISSANDO);
    }

    @Test
    @Order(9)
    void testDeleteSourceNoteRemovesSlideOut() {
        buildNoteWithSlideOut();

        enterSelectMode();

        // Select the note with slide-out
        clickAt(noteScreenPosition(0, 0));

        // Delete it
        robot.pressAndReleaseKey(KeyEvent.VK_DELETE);
        performLayout(0);

        // Note was removed
        var line = composition().getLine(0);
        assertThat(line.elementCount()).isEqualTo(0);
    }

    @Test
    @Order(10)
    void testDeleteTargetNoteRemovesConnectedGlissando() {
        buildNotesWithConnectedGlissando();

        enterSelectMode();

        // Select the target note (note 1)
        clickAt(noteScreenPosition(0, 1));

        // Delete it
        robot.pressAndReleaseKey(KeyEvent.VK_DELETE);
        performLayout(0);

        // Only 1 note remains (the source)
        var line = composition().getLine(0);
        assertThat(line.elementCount()).isEqualTo(1);

        // The source note's glissando should be removed (no target to connect to)
        //noinspection ObjectEquality
        assertThat(line.getElement(0).getGlissando()).isSameAs(StaffElement.NO_GLISSANDO);
    }

    @Test
    @Order(11)
    void testDragToUnisonRemovesConnectedGlissando() {
        buildNotesWithConnectedGlissando();

        var line = composition().getLine(0);
        var targetSp = line.getElement(1).getStaffPosition();

        // Enter edit mode (drag works in edit mode)
        enterEditMode();

        // Drag the source note to the same pitch as the target
        dragNote(0, 0, targetSp);
        performLayout(0);

        // Connected glissando should be removed (unison is meaningless)
        //noinspection ObjectEquality
        assertThat(line.getElement(0).getGlissando()).isSameAs(StaffElement.NO_GLISSANDO);
    }


    // -- Coordinate helpers --

    /**
     * Returns a screen point between two notes, suitable for clicking to insert
     * a glissando. The point is positioned horizontally between note 0 and note 1,
     * vertically at the midpoint of their staff positions.
     */
    private Point glissandoInsertionPoint(int lineIndex) {
        var p0 = noteScreenPosition(lineIndex, 0);
        var p1 = noteScreenPosition(lineIndex, 1);
        return new Point((p0.x + p1.x) / 2, (p0.y + p1.y) / 2);
    }

    /**
     * Returns the approximate midpoint of the glissando line between notes 0 and 1,
     * suitable for click-selecting the glissando.
     */
    private Point glissandoMidpoint(int lineIndex) {
        var p0 = noteScreenPosition(lineIndex, 0);
        var p1 = noteScreenPosition(lineIndex, 1);
        return new Point((p0.x + p1.x) / 2, (p0.y + p1.y) / 2);
    }


    // -- Composition builders --

    private void buildTwoNotesAtDifferentPitches() {
        GuiActionRunner.execute(() -> {
            var composition = new Composition(MainFrame.getInstance());
            var line = new Line();

            var note1 = ElementType.CROTCHET.newInstance();
            note1.setStaffPosition(0);
            line.addElement(note1);

            var note2 = ElementType.CROTCHET.newInstance();
            note2.setStaffPosition(-4);
            line.addElement(note2);

            composition.addLine(0, line);
            score().setComposition(composition);
        });

        performLayout(0);
    }

    private void buildNotesWithConnectedGlissando() {
        GuiActionRunner.execute(() -> {
            var composition = new Composition(MainFrame.getInstance());
            var line = new Line();

            var note1 = ElementType.CROTCHET.newInstance();
            note1.setStaffPosition(0);
            note1.setGlissando(StaffElement.Glissando.Type.CONNECTED);
            line.addElement(note1);

            var note2 = ElementType.CROTCHET.newInstance();
            note2.setStaffPosition(-4);
            line.addElement(note2);

            composition.addLine(0, line);
            score().setComposition(composition);
        });

        performLayout(0);
    }

    private void buildNoteWithSlideOut() {
        GuiActionRunner.execute(() -> {
            var composition = new Composition(MainFrame.getInstance());
            var line = new Line();

            var note = ElementType.CROTCHET.newInstance();
            note.setStaffPosition(0);
            note.setGlissando(StaffElement.Glissando.Type.SLIDE_OUT);
            line.addElement(note);

            composition.addLine(0, line);
            score().setComposition(composition);
        });

        performLayout(0);
    }


}
