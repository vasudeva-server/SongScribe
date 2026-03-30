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

import java.util.ArrayList;

import module java.desktop;

import org.assertj.swing.edt.GuiActionRunner;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.ClassOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestClassOrder;
import org.junit.jupiter.api.TestInstance;

import songscribe.music.Composition;
import songscribe.music.StaffElement;
import songscribe.ui.action.Actions;
import songscribe.ui.action.UIAction;
import songscribe.ui.layout.DynamicAttachment;
import songscribe.ui.layout.DynamicAttachment.DynamicType;

/**
 * Integration tests for the dynamics markings feature.
 * Covers menu structure (T28, T29), apply/toggle/replace (E1–E3),
 * reflection and hairpin gating (E4–E5), serialization (E6),
 * and regression checks for coexisting features.
 *
 * <p>All tests share the selection1.mssw fixture loaded once in {@code @BeforeAll}.
 * Each test operates on unique note indices to avoid cross-test interference.
 */
@TestClassOrder(ClassOrderer.OrderAnnotation.class)
class DynamicsMarkingTest extends E2ETest {

    // Element indices for selection1.mssw — each test uses a unique set
    private enum Note {
        POINT_DYNAMIC(0),
        DESELECT_TARGET(1),
        HAIRPIN_START(2),
        HAIRPIN_END(3),
        ROUND_TRIP_FORTE(7),
        ROUND_TRIP_PP(8),
        ROUND_TRIP_NONE(9),
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
    void loadSelection1Fixture() throws Exception {
        resetComposition();
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
            requireAttachment(composition().getLine(0).getElement(noteIndex))
        );

        if (attachment == null) {
            throw new AssertionError("GuiActionRunner returned null unexpectedly");
        }

        return attachment;
    }

    /**
     * Fetches the DynamicAttachment from a note at the given index (line 0) of the
     * given composition, or throws AssertionError if absent.
     */
    private static DynamicAttachment requireAttachmentOnNote(Composition comp, int noteIndex) {
        return requireAttachment(comp.getLine(0).getElement(noteIndex));
    }


    // =====================================================================
    // T28, T29 — Menu structure verification
    // =====================================================================

    @Nested
    @Order(1)
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class MenuStructure {

        private JMenu dynamicsMenu;

        @BeforeAll
        void findDynamicsMenu() {
            var menu = GuiActionRunner.execute(() -> {
                var found = DynamicsMarkingTest.this.findMenuContaining(Actions.ADD_CRESCENDO_ACTION);

                if (found == null) {
                    throw new AssertionError("Could not find Dynamics menu in menu bar");
                }

                return found;
            });

            if (menu == null) {
                throw new AssertionError("GuiActionRunner returned null unexpectedly");
            }

            dynamicsMenu = menu;
        }

        @Test
        void testSeparatorBetweenHairpinsAndPointDynamics() {
            // [T28] Index 3 (after crescendo=0, diminuendo=1, remove=2) must be a separator
            var components = GuiActionRunner.execute(() -> dynamicsMenu.getMenuComponents());

            if (components == null) {
                throw new AssertionError("GuiActionRunner returned null unexpectedly");
            }

            assertThat(components[3])
                .as("separator after hairpin items (index 3)")
                .isInstanceOf(JSeparator.class);
        }

        @Test
        void testSixRadioButtonsInCorrectOrder() {
            // [T29] 6 JRadioButtonMenuItems bound to pp→ff in order
            var radioItems = GuiActionRunner.execute(() -> {
                var result = new ArrayList<JRadioButtonMenuItem>();

                for (var comp : dynamicsMenu.getMenuComponents()) {
                    if (comp instanceof JRadioButtonMenuItem item) {
                        result.add(item);
                    }
                }

                return result;
            });

            if (radioItems == null) {
                throw new AssertionError("GuiActionRunner returned null unexpectedly");
            }

            var expectedActions = new UIAction[]{
                Actions.DYNAMIC_PP_ACTION, Actions.DYNAMIC_P_ACTION,
                Actions.DYNAMIC_MP_ACTION, Actions.DYNAMIC_MF_ACTION,
                Actions.DYNAMIC_F_ACTION, Actions.DYNAMIC_FF_ACTION
            };

            assertThat(radioItems).as("six radio button items").hasSize(6);

            for (int i = 0; i < expectedActions.length; i++) {
                final int idx = i;
                assertThat(radioItems.get(i).getAction())
                    .as("radio item %d bound to expected action", idx)
                    .isSameAs(expectedActions[idx]);
            }
        }
    }


    // =====================================================================
    // E1–E4 — Apply, reflection, replace, and toggle
    // Tests chain on POINT_DYNAMIC in alphabetical order:
    //   Add forte → Reflection check → Replace with piano → Toggle piano off
    // =====================================================================

    @Nested
    @Order(2)
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class PointDynamicOperations {

        @Test
        void testAddDynamicToNote() {
            // [E1] Add dynamic to note → DynamicAttachment is present with correct type
            enterSelectMode();
            clickAt(noteScreenPosition(0, Note.POINT_DYNAMIC.index));
            clickMenuItem(Actions.DYNAMIC_F_ACTION);

            var attachment = requireAttachmentOnNote(Note.POINT_DYNAMIC.index);
            assertThat(attachment.getType()).as("forte attachment added").isEqualTo(DynamicType.FORTE);
        }

        @Test
        void testReflectionShowsCurrentDynamic() {
            // [E4] Select note with dynamic → the matching action reports isSelected() == true
            // Note already has forte from testAddDynamicToNote; deselect and re-select to
            // trigger action reflection.
            clickAt(noteScreenPosition(0, Note.DESELECT_TARGET.index));
            clickAt(noteScreenPosition(0, Note.POINT_DYNAMIC.index));

            assertAll(
                () -> assertThat(GuiActionRunner.execute(() -> Actions.DYNAMIC_F_ACTION.isSelected()))
                    .as("forte action selected for note with forte dynamic").isTrue(),
                () -> assertThat(GuiActionRunner.execute(() -> Actions.DYNAMIC_FF_ACTION.isSelected()))
                    .as("fortissimo action not selected").isFalse(),
                () -> assertThat(GuiActionRunner.execute(() -> Actions.DYNAMIC_P_ACTION.isSelected()))
                    .as("piano action not selected").isFalse()
            );
        }

        @Test
        void testReplaceDynamicWithDifferent() {
            // [E3] Replace dynamic → old type replaced by new type
            // Note already has forte from testAddDynamicToNote; replace with piano.
            clickMenuItem(Actions.DYNAMIC_P_ACTION);

            var attachment = requireAttachmentOnNote(Note.POINT_DYNAMIC.index);
            assertThat(attachment.getType()).as("type replaced to PIANO").isEqualTo(DynamicType.PIANO);
        }

        @Test
        void testToggleSameDynamicRemovesIt() {
            // [E2] Clicking the same dynamic again removes it
            // Note already has piano from testReplaceDynamicWithDifferent; toggle it off.
            clickMenuItem(Actions.DYNAMIC_P_ACTION);

            var attachment = GuiActionRunner.execute(() ->
                composition().getLine(0).getElement(Note.POINT_DYNAMIC.index)
                    .findAttachment(DynamicAttachment.class)
            );
            assertThat(attachment).as("piano attachment removed after toggle").isNull();
        }
    }


    // =====================================================================
    // E5 — Hairpin gate
    // =====================================================================

    @Nested
    @Order(3)
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class HairpinGating {

        @Test
        void testNoteInHairpinRangeDisablesDynamicActions() {
            // [E5] Note inside a crescendo range → all point dynamic actions are disabled
            enterSelectMode();
            deselectSelection();
            clickAt(noteScreenPosition(0, Note.HAIRPIN_START.index));
            shiftClickAt(noteScreenPosition(0, Note.HAIRPIN_END.index));
            clickAction(Actions.ADD_CRESCENDO_ACTION);
            performLayout(0);

            // Verify crescendo was added (confirm precondition)
            assertThat(GuiActionRunner.execute(() ->
                composition().getLine(0).getCrescendos()
                    .findInterval(Note.HAIRPIN_START.index) != null
            )).as("crescendo exists on line").isTrue();

            // Select the start note, which is inside the crescendo range
            enterSelectMode();
            clickAt(noteScreenPosition(0, Note.HAIRPIN_START.index));

            assertAll(
                () -> assertThat(GuiActionRunner.execute(() -> Actions.DYNAMIC_F_ACTION.isEnabled()))
                    .as("forte disabled for note in crescendo").isFalse(),
                () -> assertThat(GuiActionRunner.execute(() -> Actions.DYNAMIC_P_ACTION.isEnabled()))
                    .as("piano disabled for note in crescendo").isFalse(),
                () -> assertThat(GuiActionRunner.execute(() -> Actions.DYNAMIC_PP_ACTION.isEnabled()))
                    .as("pianissimo disabled for note in crescendo").isFalse()
            );
        }
    }


    // =====================================================================
    // E6 — Save/load round-trip
    // =====================================================================

    @Nested
    @Order(4)
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class Serialization {

        @Test
        void testRoundTripPreservesDynamics() throws Exception {
            // [E6] Dynamics survive a serialize/deserialize cycle
            enterSelectMode();
            deselectSelection();
            clickAt(noteScreenPosition(0, Note.ROUND_TRIP_FORTE.index));
            clickMenuItem(Actions.DYNAMIC_F_ACTION);
            performLayout(0);

            clickAt(noteScreenPosition(0, Note.ROUND_TRIP_PP.index));
            clickMenuItem(Actions.DYNAMIC_PP_ACTION);

            var reloaded = roundTrip(composition());

            assertAll(
                () -> assertThat(requireAttachmentOnNote(reloaded, Note.ROUND_TRIP_FORTE.index).getType())
                    .as("forte preserved after round-trip").isEqualTo(DynamicType.FORTE),
                () -> assertThat(requireAttachmentOnNote(reloaded, Note.ROUND_TRIP_PP.index).getType())
                    .as("pianissimo preserved after round-trip").isEqualTo(DynamicType.PIANISSIMO),
                () -> assertThat(reloaded.getLine(0).getElement(Note.ROUND_TRIP_NONE.index)
                        .findAttachment(DynamicAttachment.class))
                    .as("untouched note has no dynamic after round-trip").isNull()
            );
        }
    }


    // =====================================================================
    // Regression checks
    // =====================================================================

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
            clickAction(Actions.ADD_CRESCENDO_ACTION);
            performLayout(0);

            assertThat(GuiActionRunner.execute(() ->
                composition().getLine(0).getCrescendos()
                    .findInterval(Note.CRESCENDO_START.index) != null
            )).as("crescendo interval added").isTrue();
        }

        @Test
        void testDiminuendoStillWorks() {
            // Diminuendo (hairpin) still works after dynamics refactor
            enterSelectMode();
            deselectSelection();
            clickAt(noteScreenPosition(0, Note.DIMINUENDO_START.index));
            shiftClickAt(noteScreenPosition(0, Note.DIMINUENDO_END.index));
            clickAction(Actions.ADD_DIMINUENDO_ACTION);
            performLayout(0);

            assertThat(GuiActionRunner.execute(() ->
                composition().getLine(0).getDiminuendos()
                    .findInterval(Note.DIMINUENDO_START.index) != null
            )).as("diminuendo interval added").isTrue();
        }

        @Test
        void testFermataAndDynamicCoexistOnSameNote() {
            // Fermata still works alongside dynamics
            enterSelectMode();
            clickAt(noteScreenPosition(0, Note.FERMATA_COEXIST.index));
            clickAction(Actions.FERMATA_ACTION);
            clickMenuItem(Actions.DYNAMIC_F_ACTION);

            assertAll(
                () -> assertThat(GuiActionRunner.execute(() -> Actions.FERMATA_ACTION.isSelected()))
                    .as("fermata still applied").isTrue(),
                () -> assertThat(requireAttachmentOnNote(Note.FERMATA_COEXIST.index).getType())
                    .as("forte dynamic coexists with fermata").isEqualTo(DynamicType.FORTE)
            );
        }

        @Test
        void testFixtureWithoutDynamicsLoadsWithoutError() throws Exception {
            // Files without dynamics load without errors
            var composition = loadFixture("selection2");
            // selection2.mssw has 19 elements (indices 0–18)
            assertThat(composition.getLine(0).elementCount())
                .as("selection2 fixture element count").isEqualTo(19);
        }
    }

}
