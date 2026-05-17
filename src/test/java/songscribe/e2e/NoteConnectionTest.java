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
import static org.junit.jupiter.api.Assertions.assertAll;

import module java.desktop;

import java.util.Objects;

import org.assertj.swing.edt.GuiActionRunner;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.ClassOrderer;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestClassOrder;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

import songscribe.music.StaffElement;
import songscribe.ui.action.Actions;

/**
 * E2E tests for glissando interactions that require mouse clicks at pixel
 * coordinates: selection, insertion, and deletion.
 */
@TestClassOrder(ClassOrderer.OrderAnnotation.class)
class NoteConnectionTest extends E2ETest {

    @BeforeAll
    void loadConnectionsFixture() {
        resetSong();
        loadFixture("connections");
    }

    // Element indices for connections.mssw.
    // After PAIR_E_SRC is deleted, subsequent elements shift down by 1;
    // the post-deletion entries capture these shifted positions.
    private enum Element {
        PAIR_A_SRC(5),
        PAIR_A_TGT(6),
        PAIR_B_SRC(7),
        PAIR_B_TGT(8),
        PAIR_D_SRC(11),
        PAIR_D_TGT(12),
        PAIR_E_SRC(13),
        // After PAIR_E_SRC deleted — remaining elements shift down by 1
        PAIR_E_TGT_SHIFTED(13),
        PAIR_F_SRC_SHIFTED(14),
        PAIR_F_TGT_SHIFTED(15),
        ;

        final int index;

        Element(int index) {
            this.index = index;
        }
    }


    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    @Order(1)
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class GlissandoSelection {

        @BeforeEach
        void resetState() {
            deselectSelection();
        }

        @Test
        void testClickSelectGlissando() {
            enterSelectMode();
            clickAt(midpoint(0, Element.PAIR_B_SRC.index, Element.PAIR_B_TGT.index));

            var lss = Objects.requireNonNull(scoreView().getLineComponent(0)).getLineSelectionState();
            assertAll(
                () -> assertThat(Objects.requireNonNull(lss).hasGlissandoSelection())
                    .as("glissando selected by click").isTrue(),
                () -> assertThat(Objects.requireNonNull(lss).getSelectedGlissandoElementIndex())
                    .as("correct element index").isEqualTo(Element.PAIR_B_SRC.index)
            );
        }

        @Test
        void testSelectSourceNote() {
            enterSelectMode();
            clickAt(noteScreenPosition(0, Element.PAIR_B_SRC.index));

            var lss = Objects.requireNonNull(scoreView().getLineComponent(0)).getLineSelectionState();
            assertThat(Objects.requireNonNull(lss).isElementSelected(Element.PAIR_B_SRC.index))
                .as("source note selected").isTrue();

            var note = song().getLine(0).getElement(Element.PAIR_B_SRC.index);
            assertThat(note.getGlissando()).as("source has glissando").isNotNull();
        }

        @Test
        void testSelectTargetNote() {
            enterSelectMode();
            clickAt(noteScreenPosition(0, Element.PAIR_B_TGT.index));

            var lss = Objects.requireNonNull(scoreView().getLineComponent(0)).getLineSelectionState();
            assertThat(Objects.requireNonNull(lss).isElementSelected(Element.PAIR_B_TGT.index))
                .as("target note selected").isTrue();

            var sourceNote = song().getLine(0).getElement(Element.PAIR_B_SRC.index);
            assertThat(Objects.requireNonNull(sourceNote.getGlissando()).type)
                .as("source has connected glissando pointing to target")
                .isEqualTo(StaffElement.Glissando.Type.CONNECTED);
        }

    }


    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    @Order(2)
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class GlissandoInsertion {

        @Order(1)
        @Test
        void testInsertConnectedGlissando() {
            enterEditMode();
            selectDuration(Actions.GLISSANDO_ACTION);
            clickAt(midpoint(0, Element.PAIR_A_SRC.index, Element.PAIR_A_TGT.index));
            performLayout(0);

            var note = song().getLine(0).getElement(Element.PAIR_A_SRC.index);
            var glissando = note.getGlissando();
            assertAll(
                () -> assertThat(glissando).as("has glissando").isNotNull(),
                () -> assertThat(Objects.requireNonNull(glissando).type)
                    .as("type is CONNECTED").isEqualTo(StaffElement.Glissando.Type.CONNECTED)
            );
        }

        @Order(2)
        @Test
        void testInsertSlideOut() {
            selectDuration(Actions.SLIDE_OUT_ACTION);
            clickAt(midpoint(0, Element.PAIR_A_TGT.index, Element.PAIR_B_SRC.index));
            performLayout(0);

            var note = song().getLine(0).getElement(Element.PAIR_A_TGT.index);
            var glissando = note.getGlissando();
            assertAll(
                () -> assertThat(glissando).as("has glissando").isNotNull(),
                () -> assertThat(Objects.requireNonNull(glissando).type)
                    .as("type is SLIDE_OUT").isEqualTo(StaffElement.Glissando.Type.SLIDE_OUT)
            );
        }

    }


    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    @Order(3)
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class GlissandoDeletion {

        @Order(1)
        @Test
        void testDeleteSelectedGlissando() {
            enterSelectMode();
            clickAt(midpoint(0, Element.PAIR_D_SRC.index, Element.PAIR_D_TGT.index));

            var lss = Objects.requireNonNull(scoreView().getLineComponent(0)).getLineSelectionState();
            assertThat(Objects.requireNonNull(lss).hasGlissandoSelection()).as("glissando selected").isTrue();

            robot.pressAndReleaseKey(KeyEvent.VK_DELETE);
            performLayout(0);

            var note = song().getLine(0).getElement(Element.PAIR_D_SRC.index);
            assertThat(note.getGlissando()).as("delete selected glissando").isNull();
        }

        @Order(2)
        @Test
        void testDeleteSourceNoteRemovesGlissando() {
            var countBefore = Objects.requireNonNull(GuiActionRunner.execute(
                () -> song().getLine(0).elementCount()
            ));

            clickAt(noteScreenPosition(0, Element.PAIR_E_SRC.index));
            robot.pressAndReleaseKey(KeyEvent.VK_DELETE);
            performLayout(0);

            var line = song().getLine(0);
            // After deleting PAIR_E_SRC: former pair E target shifts down by 1
            assertAll(
                () -> assertThat(line.elementCount())
                    .as("element count decreased").isEqualTo(countBefore - 1),
                () -> assertThat(line.getElement(Element.PAIR_E_TGT_SHIFTED.index).getGlissando())
                    .as("remaining note has no glissando").isNull()
            );
        }

        @Order(3)
        @Test
        void testDeleteTargetNoteRemovesGlissando() {
            // After previous deletion: pair F source and target each shifted down
            var countBefore = Objects.requireNonNull(GuiActionRunner.execute(
                () -> song().getLine(0).elementCount()
            ));

            clickAt(noteScreenPosition(0, Element.PAIR_F_TGT_SHIFTED.index));
            robot.pressAndReleaseKey(KeyEvent.VK_DELETE);
            performLayout(0);

            var line = song().getLine(0);
            // Pair F source should have glissando removed (target deleted)
            assertAll(
                () -> assertThat(line.elementCount())
                    .as("element count decreased").isEqualTo(countBefore - 1),
                () -> assertThat(line.getElement(Element.PAIR_F_SRC_SHIFTED.index).getGlissando())
                    .as("source glissando removed").isNull()
            );
        }
    }

}
