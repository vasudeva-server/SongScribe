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

package songscribe.ui.selection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import module java.desktop;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.music.Composition;
import songscribe.music.DurationArticulation;
import songscribe.music.ElementType;
import songscribe.music.StaffElement;
import songscribe.ui.action.AccidentalAction;
import songscribe.ui.action.DotAction;
import songscribe.ui.action.DurationArticulationAction;
import songscribe.ui.action.ElementTypeAction;
import songscribe.ui.action.ElementTypeAction.Kind;
import songscribe.ui.action.FermataAction;
import songscribe.ui.action.UIAction;

/**
 * Integration tests for the selection-apply feature (Phase 11).
 * These tests exercise the full flow: select → reflect → apply → verify,
 * combining multiple subsystems (coordinator, actions, content queries).
 */
class SelectionApplyIntegrationTest extends UnitTest {

    // -- Shared action instances --

    private static final ElementTypeAction QUARTER_ACTION = new ElementTypeAction(
        Kind.DURATION, ElementType.CROTCHET, "Quarter", null, 0, "quarter", "Quarter note", 0, 0
    );

    private static final ElementTypeAction HALF_ACTION = new ElementTypeAction(
        Kind.DURATION, ElementType.MINIM, "Half", null, 0, "half", "Half note", 0, 0
    );

    private static final ElementTypeAction BARLINE_ACTION = new ElementTypeAction(
        Kind.NON_DURATION, ElementType.SINGLE_BARLINE, "Barline", null, 0, "barline", "Single barline", 0, 0
    );

    private static final ElementTypeAction DOUBLE_BARLINE_ACTION = new ElementTypeAction(
        Kind.NON_DURATION, ElementType.DOUBLE_BARLINE, "Double Barline", null, 0, "double-barline", "Double barline", 0, 0
    );

    private static final AccidentalAction SHARP_ACTION =
        new AccidentalAction(StaffElement.Accidental.SHARP, "Sharp", null, 0, "sharp", "Sharp");

    private static final AccidentalAction FLAT_ACTION =
        new AccidentalAction(StaffElement.Accidental.FLAT, "Flat", null, 0, "flat", "Flat");

    private static final DotAction DOT_ACTION =
        new DotAction(DotAction.DotLevel.SINGLE, "Dot", null, 0, "dot", "Dot", 0, 0);

    private static final FermataAction FERMATA_ACTION = new FermataAction();

    private static final DurationArticulationAction STACCATO_ACTION =
        new DurationArticulationAction(DurationArticulation.STACCATO, "Staccato", null, 0, "staccato", "Staccato");

    private SelectionCoordinator createCoordinator(
        List<StaffElement> notes,
        List<UIAction.Reflectable> actions
    ) {
        var coordinator = ReflectionTestHelper.createCoordinator(notes, actions);
        var line = coordinator.getActiveSelection().getLine();
        line.setComposition(mock(Composition.class));
        return coordinator;
    }

    private SelectionCoordinator createCoordinator(
        List<StaffElement> notes,
        List<UIAction.Reflectable> actions,
        List<UIAction> managedActions
    ) {
        var coordinator = ReflectionTestHelper.createCoordinator(notes, actions, managedActions);
        var line = coordinator.getActiveSelection().getLine();
        line.setComposition(mock(Composition.class));
        return coordinator;
    }

    // ---- Edge Cases ----

    @Nested
    class EdgeCases {

        @Test
        void testAccidentalAppliedToNotesOnlyInMixedSelection() {
            var note = ElementType.CROTCHET.newInstance();
            var rest = ElementType.CROTCHET_REST.newInstance();
            var barline = ElementType.SINGLE_BARLINE.newInstance();

            var coordinator = createCoordinator(
                List.of(note, rest, barline),
                List.of(SHARP_ACTION)
            );
            var line = coordinator.getActiveSelection().getLine();

            ReflectionTestHelper.selectRange(coordinator, 0, 2);
            coordinator.applyActionToSelection(SHARP_ACTION, true);

            // Only the actual note gets the accidental
            assertThat(line.getElement(0).getAccidental()).isEqualTo(StaffElement.Accidental.SHARP);
            assertThat(line.getElement(1).getAccidental()).isEqualTo(StaffElement.Accidental.NONE);
            assertThat(line.getElement(2).getAccidental()).isEqualTo(StaffElement.Accidental.NONE);
        }

        @Test
        void testDurationChangeOnRestPreservesRestKind() {
            var notes = List.of(
                ElementType.QUAVER.newInstance(),
                ElementType.QUAVER_REST.newInstance()
            );
            var coordinator = createCoordinator(notes, List.of(HALF_ACTION));
            var line = coordinator.getActiveSelection().getLine();

            ReflectionTestHelper.selectRange(coordinator, 0, 1);
            coordinator.applyActionToSelection(HALF_ACTION, true);

            assertThat(line.getElement(0).getType()).isEqualTo(ElementType.MINIM);
            assertThat(line.getElement(1).getType()).isEqualTo(ElementType.MINIM_REST);
        }

        @Test
        void testDurationChangePreservesExistingAttributes() {
            var note = ElementType.QUAVER.newInstance();
            note.setAccidental(StaffElement.Accidental.SHARP);
            note.setDotCount(1);
            note.setFermata(true);
            note.setDurationArticulation(DurationArticulation.STACCATO);

            var coordinator = createCoordinator(List.of(note), List.of(QUARTER_ACTION));
            var line = coordinator.getActiveSelection().getLine();

            ReflectionTestHelper.selectNote(coordinator, 0);
            coordinator.applyActionToSelection(QUARTER_ACTION, true);

            var replaced = line.getElement(0);
            assertThat(replaced.getType()).isEqualTo(ElementType.CROTCHET);
            assertThat(replaced.getAccidental()).isEqualTo(StaffElement.Accidental.SHARP);
            assertThat(replaced.getDotCount()).isEqualTo(1);
            assertThat(replaced.isFermata()).isTrue();
            assertThat(replaced.getDurationArticulation()).isEqualTo(DurationArticulation.STACCATO);
        }

        @Test
        void testGraceNotesUnaffectedByDurationChange() {
            // Grace notes are not durations — they are intimately tied to the
            // following note and have no standalone duration.
            var notes = List.of(
                ElementType.GRACE_QUAVER.newInstance(),
                ElementType.QUAVER.newInstance()
            );
            var coordinator = createCoordinator(notes, List.of(QUARTER_ACTION));
            var line = coordinator.getActiveSelection().getLine();

            ReflectionTestHelper.selectRange(coordinator, 0, 1);
            coordinator.applyActionToSelection(QUARTER_ACTION, true);

            assertThat(line.getElement(0).getType()).isEqualTo(ElementType.GRACE_QUAVER);
            assertThat(line.getElement(1).getType()).isEqualTo(ElementType.CROTCHET);
        }
    }

    // ---- Full Select → Reflect → Apply → Verify Flow ----

    @Nested
    class FullApplyFlow {

        @Test
        void testSelectBarlinesClickBarlineTypeVerifyChanged() {
            var notes = List.of(
                ElementType.SINGLE_BARLINE.newInstance(),
                ElementType.SINGLE_BARLINE.newInstance()
            );
            var coordinator = createCoordinator(notes, List.of(DOUBLE_BARLINE_ACTION));
            var line = coordinator.getActiveSelection().getLine();

            ReflectionTestHelper.selectRange(coordinator, 0, 1);
            coordinator.applyActionToSelection(DOUBLE_BARLINE_ACTION, true);

            for (int i = 0; i <= 1; i++) {
                assertThat(line.getElement(i).getType())
                    .as("barline %d should be double barline", i)
                    .isEqualTo(ElementType.DOUBLE_BARLINE);
            }
        }

        @Test
        void testSelectNotesAndRestsClickDotVerifyBothGetDots() {
            var notes = List.of(
                ElementType.CROTCHET.newInstance(),
                ElementType.CROTCHET_REST.newInstance(),
                ElementType.QUAVER.newInstance()
            );
            var coordinator = createCoordinator(notes, List.of(DOT_ACTION));
            var line = coordinator.getActiveSelection().getLine();

            ReflectionTestHelper.selectRange(coordinator, 0, 2);

            // Apply dot to selection — DotAction applies to all durations (notes + rests)
            coordinator.applyActionToSelection(DOT_ACTION, true);

            for (int i = 0; i <= 2; i++) {
                assertThat(line.getElement(i).getDotCount())
                    .as("note %d should have 1 dot", i)
                    .isEqualTo(1);
            }
        }

        @Test
        void testSelectNotesClickAccidentalVerifyApplied() {
            var note1 = ElementType.CROTCHET.newInstance();
            var note2 = ElementType.CROTCHET.newInstance();
            note2.setAccidental(StaffElement.Accidental.FLAT);
            var notes = List.of(note1, note2);

            var coordinator = createCoordinator(notes, List.of(SHARP_ACTION, FLAT_ACTION));
            var line = coordinator.getActiveSelection().getLine();

            ReflectionTestHelper.selectRange(coordinator, 0, 1);

            // Reflect — sharp not selected (not all match), flat not selected (not all match)
            coordinator.reflectSelection(null);
            assertThat(SHARP_ACTION.isSelected()).isFalse();
            assertThat(FLAT_ACTION.isSelected()).isFalse();

            // Apply sharp to selection
            coordinator.applyActionToSelection(SHARP_ACTION, true);

            // Verify both notes are now sharp
            assertThat(line.getElement(0).getAccidental()).isEqualTo(StaffElement.Accidental.SHARP);
            assertThat(line.getElement(1).getAccidental()).isEqualTo(StaffElement.Accidental.SHARP);
        }

        @Test
        void testSelectNotesClickDurationVerifyChanged() {
            var notes = List.of(
                ElementType.QUAVER.newInstance(),
                ElementType.SEMIQUAVER.newInstance(),
                ElementType.QUAVER.newInstance()
            );
            var actions = List.<UIAction.Reflectable>of(QUARTER_ACTION, HALF_ACTION);
            var coordinator = createCoordinator(notes, actions);
            var line = coordinator.getActiveSelection().getLine();

            // Select all notes
            ReflectionTestHelper.selectRange(coordinator, 0, 2);

            // Reflect — should show no duration selected (mixed quaver/semiquaver)
            coordinator.reflectSelection(null);
            assertThat(QUARTER_ACTION.isSelected()).isFalse();
            assertThat(HALF_ACTION.isSelected()).isFalse();

            // Apply quarter note to selection
            coordinator.applyActionToSelection(QUARTER_ACTION, true);

            // Verify all notes changed to crotchet
            for (int i = 0; i <= 2; i++) {
                assertThat(line.getElement(i).getType())
                    .as("note %d should be crotchet", i)
                    .isEqualTo(ElementType.CROTCHET);
            }

            // Selection should still be active
            assertThat(coordinator.getSelection()).isNotNull();
        }
    }

    // ---- Mutual Exclusivity ----

    @Nested
    class MutualExclusivity {

        @Test
        void testBarlineOnlySelectionDisablesDurationActions() {
            var coordinator = createCoordinator(
                List.of(ElementType.SINGLE_BARLINE.newInstance(), ElementType.DOUBLE_BARLINE.newInstance()),
                List.of(QUARTER_ACTION, BARLINE_ACTION)
            );

            ReflectionTestHelper.selectRange(coordinator, 0, 1);

            assertThat(coordinator.isApplicableToSelection(QUARTER_ACTION))
                .as("duration action not applicable to barline-only selection")
                .isFalse();
            assertThat(coordinator.selectionHasDurations())
                .as("barline-only selection has no durations")
                .isFalse();
        }

        @Test
        void testBarlineOnlySelectionEnablesBarlineActions() {
            var coordinator = createCoordinator(
                List.of(ElementType.SINGLE_BARLINE.newInstance(), ElementType.DOUBLE_BARLINE.newInstance()),
                List.of(QUARTER_ACTION, BARLINE_ACTION)
            );

            ReflectionTestHelper.selectRange(coordinator, 0, 1);

            assertThat(coordinator.isApplicableToSelection(BARLINE_ACTION))
                .as("barline action applicable to barline-only selection")
                .isTrue();
        }

        @Test
        void testMixedSelectionDisablesNonReflectableNoteActions() {
            // Non-reflectable actions with DISABLE_WHEN_BAR_SELECTED check selectionHasDurations.
            // Mixed selection has durations, so they are enabled (the action applies to the notes).
            // The mutual exclusivity only fully disables when ALL elements are inapplicable.
            var coordinator = createCoordinator(
                List.of(ElementType.CROTCHET.newInstance(), ElementType.SINGLE_BARLINE.newInstance()),
                List.of(QUARTER_ACTION, BARLINE_ACTION)
            );

            ReflectionTestHelper.selectRange(coordinator, 0, 1);

            // Duration actions are applicable because notes exist
            assertThat(coordinator.isApplicableToSelection(QUARTER_ACTION)).isTrue();
            // Barline actions are applicable because barlines exist
            assertThat(coordinator.isApplicableToSelection(BARLINE_ACTION)).isTrue();
            // selectionHasDurations is true because notes exist
            assertThat(coordinator.selectionHasDurations()).isTrue();
        }

        @Test
        void testNoteOnlySelectionDisablesBarlineActions() {
            var coordinator = createCoordinator(
                List.of(ElementType.CROTCHET.newInstance(), ElementType.QUAVER.newInstance()),
                List.of(QUARTER_ACTION, BARLINE_ACTION)
            );

            ReflectionTestHelper.selectRange(coordinator, 0, 1);

            assertThat(coordinator.isApplicableToSelection(BARLINE_ACTION))
                .as("barline action not applicable to note-only selection")
                .isFalse();
        }

        @Test
        void testNoteOnlySelectionEnablesDurationActions() {
            var coordinator = createCoordinator(
                List.of(ElementType.CROTCHET.newInstance(), ElementType.QUAVER.newInstance()),
                List.of(QUARTER_ACTION, BARLINE_ACTION)
            );

            ReflectionTestHelper.selectRange(coordinator, 0, 1);

            assertThat(coordinator.isApplicableToSelection(QUARTER_ACTION))
                .as("duration action applicable to note-only selection")
                .isTrue();
            assertThat(coordinator.selectionHasDurations())
                .as("note-only selection has durations")
                .isTrue();
        }

        @Test
        void testRestOnlySelectionDisablesNoteOnlyActions() {
            // AccidentalAction applies only to notes, not rests
            var coordinator = createCoordinator(
                List.of(ElementType.CROTCHET_REST.newInstance(), ElementType.QUAVER_REST.newInstance()),
                List.of(SHARP_ACTION, DOT_ACTION)
            );

            ReflectionTestHelper.selectRange(coordinator, 0, 1);

            assertThat(coordinator.isApplicableToSelection(SHARP_ACTION))
                .as("accidental not applicable to rest-only selection")
                .isFalse();
            assertThat(coordinator.isApplicableToSelection(DOT_ACTION))
                .as("dot applicable to rest-only selection (durations)")
                .isTrue();
        }
    }

    // ---- Multi-Action Sequential Application ----

    @Nested
    class SequentialApply {

        @Test
        void testApplyDotThenChangeDurationPreservesDots() {
            var notes = List.of(
                ElementType.QUAVER.newInstance(),
                ElementType.QUAVER.newInstance()
            );
            var coordinator = createCoordinator(notes, List.of(DOT_ACTION, QUARTER_ACTION));
            var line = coordinator.getActiveSelection().getLine();

            ReflectionTestHelper.selectRange(coordinator, 0, 1);

            // Add dots first
            coordinator.applyActionToSelection(DOT_ACTION, true);

            // Change duration — dots should be preserved by copy constructor
            coordinator.applyActionToSelection(QUARTER_ACTION, true);

            for (int i = 0; i <= 1; i++) {
                var note = line.getElement(i);
                assertThat(note.getType())
                    .as("note %d type", i).isEqualTo(ElementType.CROTCHET);
                assertThat(note.getDotCount())
                    .as("note %d dots", i).isEqualTo(1);
            }
        }

        @Test
        void testApplyMultipleAttributesInSequence() {
            var notes = List.of(
                ElementType.QUAVER.newInstance(),
                ElementType.QUAVER.newInstance()
            );
            var actions = List.<UIAction.Reflectable>of(
                QUARTER_ACTION, SHARP_ACTION, FERMATA_ACTION, STACCATO_ACTION
            );
            var coordinator = createCoordinator(notes, actions);
            var line = coordinator.getActiveSelection().getLine();

            ReflectionTestHelper.selectRange(coordinator, 0, 1);

            // Step 1: Change duration to quarter
            coordinator.applyActionToSelection(QUARTER_ACTION, true);

            // Step 2: Add sharp
            coordinator.applyActionToSelection(SHARP_ACTION, true);

            // Step 3: Add fermata
            coordinator.applyActionToSelection(FERMATA_ACTION, true);

            // Step 4: Add staccato
            coordinator.applyActionToSelection(STACCATO_ACTION, true);

            // Verify all attributes applied to both notes
            for (int i = 0; i <= 1; i++) {
                var note = line.getElement(i);
                assertThat(note.getType())
                    .as("note %d type", i).isEqualTo(ElementType.CROTCHET);
                assertThat(note.getAccidental())
                    .as("note %d accidental", i).isEqualTo(StaffElement.Accidental.SHARP);
                assertThat(note.isFermata())
                    .as("note %d fermata", i).isTrue();
                assertThat(note.getDurationArticulation())
                    .as("note %d staccato", i).isEqualTo(DurationArticulation.STACCATO);
            }

            // Selection remains active throughout
            assertThat(coordinator.getSelection()).isNotNull();
        }

        @Test
        void testApplyThenRemoveAttribute() {
            var notes = List.of(
                ElementType.CROTCHET.newInstance(),
                ElementType.CROTCHET.newInstance()
            );
            var coordinator = createCoordinator(notes, List.of(FERMATA_ACTION));
            var line = coordinator.getActiveSelection().getLine();

            ReflectionTestHelper.selectRange(coordinator, 0, 1);

            // Apply fermata
            coordinator.applyActionToSelection(FERMATA_ACTION, true);
            assertThat(line.getElement(0).isFermata()).isTrue();
            assertThat(line.getElement(1).isFermata()).isTrue();

            // Remove fermata
            coordinator.applyActionToSelection(FERMATA_ACTION, false);
            assertThat(line.getElement(0).isFermata()).isFalse();
            assertThat(line.getElement(1).isFermata()).isFalse();
        }
    }

    // ---- State Management Integration ----

    @Nested
    class StateManagement {

        @Test
        void testManagedActionsIncludeNonReflectableWithFlag() {
            var note = ElementType.CROTCHET.newInstance();

            var fermataAction = new FermataAction();
            var flaggedAction = new UIAction("Beam", null, 0, "beam", "Toggle beam") {
                @Override
                public void actionPerformed(ActionEvent e) {
                }
            };
            flaggedAction.setFlags(UIAction.Flag.DISABLE_WHEN_BAR_SELECTED);

            var reflectableActions = List.<UIAction.Reflectable>of(fermataAction);
            var managedActions = new ArrayList<UIAction>();
            managedActions.add(fermataAction);
            managedActions.add(flaggedAction);

            var coordinator = createCoordinator(
                List.of(note),
                reflectableActions,
                managedActions
            );

            // Set distinct initial states
            fermataAction.setSelected(false);
            fermataAction.setEnabled(true);
            flaggedAction.setEnabled(true);

            // Select and reflect — saves state for both managed actions
            ReflectionTestHelper.selectNote(coordinator, 0);
            coordinator.reflectSelection(null);

            // Simulate flag chain disabling the non-reflectable action
            flaggedAction.setEnabled(false);

            // Clear and reflect — both actions should be restored
            ReflectionTestHelper.clearSelection(coordinator);
            coordinator.reflectSelection(null);

            assertThat(flaggedAction.isEnabled())
                .as("non-reflectable action enabled state restored")
                .isTrue();
        }

        @Test
        void testSaveReflectApplyClearRestoresState() {
            var note = ElementType.CROTCHET.newInstance();
            note.setAccidental(StaffElement.Accidental.FLAT);

            var sharpAction = new AccidentalAction(
                StaffElement.Accidental.SHARP, "Sharp", null, 0, "sharp", "Sharp"
            );
            var flatAction = new AccidentalAction(
                StaffElement.Accidental.FLAT, "Flat", null, 0, "flat", "Flat"
            );
            // Pre-selection state
            sharpAction.setSelected(false);
            sharpAction.setEnabled(true);
            flatAction.setSelected(true);
            flatAction.setEnabled(true);

            var coordinator = createCoordinator(
                List.of(note),
                List.of(sharpAction, flatAction)
            );

            // Select and reflect — saves state, then reflects
            ReflectionTestHelper.selectNote(coordinator, 0);
            coordinator.reflectSelection(null);

            // Note has FLAT, so SHARP=false, FLAT=true
            assertThat(sharpAction.isSelected()).isFalse();
            assertThat(flatAction.isSelected()).isTrue();

            // Apply sharp — mutates the note
            coordinator.applyActionToSelection(sharpAction, true);
            var line = coordinator.getActiveSelection().getLine();
            assertThat(line.getElement(0).getAccidental()).isEqualTo(StaffElement.Accidental.SHARP);

            // Simulate disabled state during selection (flag chain would do this)
            sharpAction.setEnabled(false);

            // Clear selection and reflect — restores pre-selection state
            ReflectionTestHelper.clearSelection(coordinator);
            coordinator.reflectSelection(null);

            assertThat(sharpAction.isSelected()).as("sharp restored to pre-selection selected").isFalse();
            assertThat(sharpAction.isEnabled()).as("sharp restored to pre-selection enabled").isTrue();
            assertThat(flatAction.isSelected()).as("flat restored to pre-selection selected").isTrue();
            assertThat(flatAction.isEnabled()).as("flat restored to pre-selection enabled").isTrue();
        }
    }
}
