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
import songscribe.music.Line;
import songscribe.music.NoteType;
import songscribe.ui.component.MainFrame;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E tests verifying that barlines and repeats are clickable (hit-testable)
 * despite their narrow visual width. AD-10 guarantees a 4px minimum hit rect.
 */
@Order(5)
class BarlineHitTest extends E2ETest {

    @Test @Order(1)
    void testClickSingleBarline() {
        buildLineWithElement(NoteType.SINGLE_BARLINE);
        enterSelectMode();

        clickAt(noteScreenPosition(0, 1));

        assertThat(score().getSingleSelectedNote())
            .isEqualTo(composition().getLine(0).getNote(1));
    }

    @Test @Order(2)
    void testClickDoubleBarline() {
        buildLineWithElement(NoteType.DOUBLE_BARLINE);
        enterSelectMode();

        clickAt(noteScreenPosition(0, 1));

        assertThat(score().getSingleSelectedNote())
            .isEqualTo(composition().getLine(0).getNote(1));
    }

    @Test @Order(3)
    void testClickFinalDoubleBarline() {
        buildLineWithElement(NoteType.FINAL_DOUBLE_BARLINE);
        enterSelectMode();

        clickAt(noteScreenPosition(0, 1));

        assertThat(score().getSingleSelectedNote())
            .isEqualTo(composition().getLine(0).getNote(1));
    }

    @Test @Order(4)
    void testClickRepeatLeft() {
        buildLineWithElement(NoteType.REPEAT_LEFT);
        enterSelectMode();

        clickAt(noteScreenPosition(0, 1));

        assertThat(score().getSingleSelectedNote())
            .isEqualTo(composition().getLine(0).getNote(1));
    }

    @Test @Order(5)
    void testClickRepeatRight() {
        buildLineWithElement(NoteType.REPEAT_RIGHT);
        enterSelectMode();

        clickAt(noteScreenPosition(0, 1));

        assertThat(score().getSingleSelectedNote())
            .isEqualTo(composition().getLine(0).getNote(1));
    }

    @Test @Order(6)
    void testClickRepeatLeftRight() {
        buildLineWithElement(NoteType.REPEAT_LEFT_RIGHT);
        enterSelectMode();

        clickAt(noteScreenPosition(0, 1));

        assertThat(score().getSingleSelectedNote())
            .isEqualTo(composition().getLine(0).getNote(1));
    }


    // -- Helpers --

    /**
     * Builds a composition with a crotchet followed by the given element type.
     * The crotchet ensures the element is not at x=0 and has proper spacing.
     */
    private void buildLineWithElement(NoteType elementType) {
        GuiActionRunner.execute(() -> {
            var composition = new Composition(MainFrame.getInstance());
            var line = new Line();

            var crotchet = NoteType.CROTCHET.newInstance();
            crotchet.setStaffPosition(0);
            line.addNote(crotchet);

            var element = elementType.newInstance();
            element.setStaffPosition(0);
            line.addNote(element);

            composition.addLine(0, line);
            score().setComposition(composition);
        });

        performLayout(0);
    }
}
