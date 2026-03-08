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

import songscribe.music.Composition;
import songscribe.music.ElementType;
import songscribe.music.Line;
import songscribe.ui.component.MainFrame;

/**
 * E2E tests verifying that barlines and repeats are clickable (hit-testable)
 * despite their narrow visual width. AD-10 guarantees a 4px minimum hit rect.
 */
class BarlineHitTest extends E2ETest {

    @Nested
    class StandardBarlines {

        @Test
        void testClickSingleBarline() {
            buildLineWithElement(ElementType.SINGLE_BARLINE);
            enterSelectMode();

            clickAt(noteScreenPosition(0, 1));

            assertThat(score().getSingleSelectedElement())
                .isEqualTo(composition().getLine(0).getElement(1));
        }

        @Test
        void testClickDoubleBarline() {
            buildLineWithElement(ElementType.DOUBLE_BARLINE);
            enterSelectMode();

            clickAt(noteScreenPosition(0, 1));

            assertThat(score().getSingleSelectedElement())
                .isEqualTo(composition().getLine(0).getElement(1));
        }

        @Test
        void testClickFinalDoubleBarline() {
            buildLineWithElement(ElementType.FINAL_DOUBLE_BARLINE);
            enterSelectMode();

            clickAt(noteScreenPosition(0, 1));

            assertThat(score().getSingleSelectedElement())
                .isEqualTo(composition().getLine(0).getElement(1));
        }
    }

    @Nested
    class RepeatBarlines {

        @Test
        void testClickRepeatLeft() {
            buildLineWithElement(ElementType.REPEAT_LEFT);
            enterSelectMode();

            clickAt(noteScreenPosition(0, 1));

            assertThat(score().getSingleSelectedElement())
                .isEqualTo(composition().getLine(0).getElement(1));
        }

        @Test
        void testClickRepeatRight() {
            buildLineWithElement(ElementType.REPEAT_RIGHT);
            enterSelectMode();

            clickAt(noteScreenPosition(0, 1));

            assertThat(score().getSingleSelectedElement())
                .isEqualTo(composition().getLine(0).getElement(1));
        }

        @Test
        void testClickRepeatLeftRight() {
            buildLineWithElement(ElementType.REPEAT_LEFT_RIGHT);
            enterSelectMode();

            clickAt(noteScreenPosition(0, 1));

            assertThat(score().getSingleSelectedElement())
                .isEqualTo(composition().getLine(0).getElement(1));
        }
    }


    // -- Helpers --

    /**
     * Builds a composition with a crotchet followed by the given element type.
     * The crotchet ensures the element is not at x=0 and has proper spacing.
     */
    private void buildLineWithElement(ElementType elementType) {
        GuiActionRunner.execute(() -> {
            var composition = new Composition(MainFrame.getInstance());
            var line = new Line();

            var crotchet = ElementType.CROTCHET.newInstance();
            crotchet.setStaffPosition(0);
            line.addElement(crotchet);

            var element = elementType.newInstance();
            element.setStaffPosition(0);
            line.addElement(element);

            composition.addLine(0, line);
            score().setComposition(composition);
        });

        performLayout(0);
    }
}
