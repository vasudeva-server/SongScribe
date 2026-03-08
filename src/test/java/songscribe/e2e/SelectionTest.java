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
import songscribe.ui.Mode;
import songscribe.ui.component.MainFrame;

/**
 * Milestone 1 E2E tests: click-to-select, shift-click range, deselect.
 */
@Order(2)
class SelectionTest extends E2ETest {

    @Test
    @Order(1)
    void testClickToSelectNote() {
        buildThreeNoteComposition();
        enterSelectMode();

        clickAt(noteScreenPosition(0, 1));

        assertThat(score().getSingleSelectedElement())
            .isEqualTo(composition().getLine(0).getElement(1));
    }

    @Test
    @Order(2)
    void testClickEmptySpaceDeselects() {
        buildThreeNoteComposition();
        enterSelectMode();

        // Select a note first
        clickAt(noteScreenPosition(0, 0));
        assertThat(score().getSingleSelectedElement()).isNotNull();

        // Click below the staff, towards the end of the line
        var emptyPoint = GuiActionRunner.execute(() -> {
            var lc = score().getLineComponent(0);
            var loc = lc.getLocationOnScreen();
            return new Point(loc.x + lc.getWidth() - 10, loc.y + lc.getHeight() - 5);
        });

        clickAt(emptyPoint);
        assertThat(score().getSelectionSize()).isEqualTo(0);
    }

    @Test
    @Order(3)
    void testModeToggle() {
        // Start in edit mode (default after reset)
        enterEditMode();
        assertThat(score().getMode()).isEqualTo(Mode.EDIT);

        // Switch to select mode
        enterSelectMode();
        assertThat(score().getMode()).isEqualTo(Mode.SELECT);

        // Switch back to edit mode
        enterEditMode();
        assertThat(score().getMode()).isEqualTo(Mode.EDIT);
    }

    @Test
    @Order(4)
    void testShiftClickExtendsSelection() {
        buildThreeNoteComposition();
        enterSelectMode();

        // Select first note
        clickAt(noteScreenPosition(0, 0));
        assertThat(score().getSelectionSize()).isEqualTo(1);

        // Shift-click third note to extend selection
        shiftClickAt(noteScreenPosition(0, 2));

        // All 3 notes should be selected
        assertThat(score().getSelectionSize()).isEqualTo(3);
    }

    @Test
    @Order(5)
    void testShiftClickShrinksSelection() {
        buildThreeNoteComposition();
        enterSelectMode();

        // Select first note, then shift-click third to select all 3
        clickAt(noteScreenPosition(0, 0));
        shiftClickAt(noteScreenPosition(0, 2));
        assertThat(score().getSelectionSize()).isEqualTo(3);

        // Shift-click 2nd note to shrink selection (deselects 3rd)
        shiftClickAt(noteScreenPosition(0, 1));

        assertThat(score().getSelectionSize()).isEqualTo(2);
        assertThat(score().isElementSelected(0, 0)).isTrue();
        assertThat(score().isElementSelected(1, 0)).isTrue();
        assertThat(score().isElementSelected(2, 0)).isFalse();
    }

    @Test
    @Order(6)
    void testMetaDDeselectsAll() {
        buildThreeNoteComposition();
        enterSelectMode();

        // Select all 3 notes
        clickAt(noteScreenPosition(0, 0));
        shiftClickAt(noteScreenPosition(0, 2));
        assertThat(score().getSelectionSize()).isEqualTo(3);

        // Press Cmd+D (Meta+D) to deselect all
        robot.pressAndReleaseKey(KeyEvent.VK_D, InputEvent.META_DOWN_MASK);
        assertThat(score().getSelectionSize()).isEqualTo(0);
    }


    @Test
    @Order(7)
    void testClickInStaffSelectsLine() {
        buildThreeNoteComposition();
        enterSelectMode();

        // Click within the staff but to the right of all notes
        var clickPoint = GuiActionRunner.execute(() -> {
            var lc = score().getLineComponent(0);
            var loc = lc.getLocationOnScreen();
            var yPx = lc.staffPositionToYPx(0);
            return new Point(loc.x + lc.getWidth() - 10, loc.y + yPx);
        });

        clickAt(clickPoint);

        assertThat(score().isLineSelected(0)).isTrue();
    }


    // -- Helpers --

    private void buildThreeNoteComposition() {
        GuiActionRunner.execute(() -> {
            var composition = new Composition(MainFrame.getInstance());
            var line = new Line();
            var positions = new int[]{0, -2, -4};

            for (var sp : positions) {
                var note = ElementType.CROTCHET.newInstance();
                note.setStaffPosition(sp);
                line.addElement(note);
            }

            composition.addLine(0, line);
            score().setComposition(composition);
        });

        performLayout(0);
    }

}
