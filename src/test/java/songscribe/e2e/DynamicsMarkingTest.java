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

import org.assertj.swing.edt.GuiActionRunner;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.ClassOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestClassOrder;
import org.junit.jupiter.api.TestInstance;

import songscribe.dom.StaffElement;
import songscribe.ui.action.Actions;
import songscribe.dom.DynamicAttachment;
import songscribe.dom.DynamicAttachment.DynamicType;

/**
 * Integration tests for the dynamics markings feature.
 * Covers serialization (E6) and regression checks for coexisting features.
 *
 * <p>All tests share the selection1.musicxml fixture loaded once in {@code @BeforeAll}.
 * Each test operates on unique note indices to avoid cross-test interference.
 */
@TestClassOrder(ClassOrderer.OrderAnnotation.class)
class DynamicsMarkingTest extends E2ETest {

    // Element indices for selection1.musicxml — each test uses a unique set
    private enum Note {
        FERMATA_COEXIST(10),
        CRESCENDO_START(11),
        CRESCENDO_END(12),
        DIMINUENDO_START(13),
        DIMINUENDO_END(14),
        ;

        final int index;

        Note(int index) {
            this.index = index;
        }
    }

    @BeforeAll
    void loadSelection1Fixture() {
        resetSong();
        loadFixture("selection1");
    }

    // -- Helper: require a DynamicAttachment or fail the test --

    /**
     * Returns the DynamicAttachment on the given element, or throws AssertionError
     * if none is present. Avoids {@code @SuppressWarnings("NullAway")} by using
     * a null guard that throws.
     */
    private static DynamicAttachment requireAttachment(StaffElement element) {
        var attachment = element.findAttachment(DynamicAttachment.class);

        if (attachment == null) {
            throw new AssertionError("Expected DynamicAttachment on element but found none");
        }

        return attachment;
    }

    /**
     * Fetches the DynamicAttachment from a note at the given index (line 0) on the EDT,
     * or throws AssertionError if absent.
     */
    private DynamicAttachment requireAttachmentOnNote(int noteIndex) {
        var attachment = GuiActionRunner.execute(() ->
            requireAttachment(song().getLine(0).getElement(noteIndex))
        );

        //noinspection ConstantValue
        if (attachment == null) {
            throw new AssertionError("GuiActionRunner returned null unexpectedly");
        }

        return attachment;
    }

    // =====================================================================
    // Regression checks
    // =====================================================================

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    @Order(5)
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class Regression {

        @Test
        void testCrescendoStillWorks() {
            // Crescendo (hairpin) still works after dynamics refactor
            enterSelectMode();
            deselectSelection();
            clickAt(noteScreenPosition(0, Note.CRESCENDO_START.index));
            shiftClickAt(noteScreenPosition(0, Note.CRESCENDO_END.index));
            clickAction(Actions.HAIRPIN_CRESCENDO_ACTION);
            performLayout(0);

            assertThat(GuiActionRunner.execute(() ->
                song().getLine(0).getCrescendos().stream()
                    .anyMatch(c -> c.getAnchorElementIndex() <= Note.CRESCENDO_START.index
                        && c.getEndElementIndex() >= Note.CRESCENDO_START.index)
            )).as("crescendo span added").isTrue();
        }

        @Test
        void testDiminuendoStillWorks() {
            // Diminuendo (hairpin) still works after dynamics refactor
            enterSelectMode();
            deselectSelection();
            clickAt(noteScreenPosition(0, Note.DIMINUENDO_START.index));
            shiftClickAt(noteScreenPosition(0, Note.DIMINUENDO_END.index));
            clickAction(Actions.HAIRPIN_DIMINUENDO_ACTION);
            performLayout(0);

            assertThat(GuiActionRunner.execute(() ->
                song().getLine(0).getDiminuendos().stream()
                    .anyMatch(d -> d.getAnchorElementIndex() <= Note.DIMINUENDO_START.index
                        && d.getEndElementIndex() >= Note.DIMINUENDO_START.index)
            )).as("diminuendo span added").isTrue();
        }

        @Test
        void testFermataAndDynamicCoexistOnSameNote() {
            // Fermata still works alongside dynamics
            enterSelectMode();
            clickAt(noteScreenPosition(0, Note.FERMATA_COEXIST.index));
            clickAction(Actions.FERMATA_ACTION);
            clickMenuItem(Actions.DYNAMIC_F_ACTION);

            assertAll(
                () -> assertThat(GuiActionRunner.execute(Actions.FERMATA_ACTION::isSelected))
                    .as("fermata still applied").isTrue(),
                () -> assertThat(requireAttachmentOnNote(Note.FERMATA_COEXIST.index).getType())
                    .as("forte dynamic coexists with fermata").isEqualTo(DynamicType.FORTE)
            );
        }
    }

}
