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

import module java.desktop;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalAnswers.answerVoid;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.MainFrameMockTest;
import songscribe.message.mutation.Mutation;
import songscribe.dom.ArticulationType;
import songscribe.dom.Song;
import songscribe.dom.ElementType;
import songscribe.dom.StaffElement;
import songscribe.layout.NoteGeometry;
import songscribe.ui.action.AccidentalAction;
import songscribe.ui.action.AccidentalInParensAction;
import songscribe.ui.action.ArticulationAction;
import songscribe.ui.action.DotAction;
import songscribe.ui.action.ElementTypeAction;
import songscribe.ui.action.FermataAction;
import songscribe.ui.action.UIAction;
import songscribe.dom.Articulation;
import songscribe.dom.FermataAttachment;

/**
 * Integration tests for the selection-apply feature (Phase 11).
 * These tests exercise the full flow: select → reflect → apply → verify,
 * combining multiple subsystems (coordinator, actions, content queries).
 */
class SelectionApplyIntegrationTest extends MainFrameMockTest {

    // -- Shared action instances --

    private ElementTypeAction QUARTER_ACTION;
    private ElementTypeAction HALF_ACTION;
    private ElementTypeAction BARLINE_ACTION;
    private ElementTypeAction DOUBLE_BARLINE_ACTION;
    private AccidentalAction SHARP_ACTION;
    private AccidentalAction FLAT_ACTION;
    private DotAction DOT_ACTION;
    private FermataAction FERMATA_ACTION;
    private ArticulationAction STACCATO_ACTION;
    private AccidentalInParensAction ACCIDENTAL_IN_PARENS_ACTION;

    // Applying a width-changing action now measures the projected line, which reads accidental
    // widths out of a static table that has to be built first. Without this the class passes only
    // when some earlier test class happens to have built it, and fails whenever it runs alone.
    @BeforeAll
    static void initializeNoteGeometry() {
        NoteGeometry.initializeAccidentalWidths();
    }

    @BeforeEach
    void setUp() {
        var mainFrame = mainFrame();
        QUARTER_ACTION = ElementTypeAction.createQuarterNoteAction(mainFrame);
        HALF_ACTION = ElementTypeAction.createHalfNoteAction(mainFrame);
        BARLINE_ACTION = ElementTypeAction.createSingleBarlineAction(mainFrame);
        DOUBLE_BARLINE_ACTION = ElementTypeAction.createDoubleBarlineAction(mainFrame);
        SHARP_ACTION = AccidentalAction.createSharpAction(mainFrame);
        FLAT_ACTION = AccidentalAction.createFlatAction(mainFrame);
        DOT_ACTION = DotAction.createDotAction(mainFrame);
        FERMATA_ACTION = FermataAction.createAction(mainFrame);
        STACCATO_ACTION = ArticulationAction.createStaccatoAction(mainFrame);
        ACCIDENTAL_IN_PARENS_ACTION = AccidentalInParensAction.createAction(mainFrame);
    }

    private SelectionCoordinator createCoordinator(
        List<? extends StaffElement> notes,
        List<UIAction.Reflectable> actions
    ) {
        return ReflectionTestHelper.createCoordinator(notes, actions, createSongMock());
    }

    private SelectionCoordinator createCoordinator(
        List<? extends StaffElement> notes,
        List<UIAction.Reflectable> actions,
        List<UIAction> managedActions
    ) {
        return ReflectionTestHelper.createCoordinator(notes, actions, managedActions, createSongMock());
    }

    @SuppressWarnings("ReturnOfNull")
    private static Song createSongMock() {
        var songMock = mock(Song.class);
        when(songMock.isModifying()).thenReturn(true);
        when(songMock.getLineWidthSs()).thenReturn(UNCONSTRAINED_LINE_WIDTH_SS);
        doAnswer(answerVoid(Runnable::run)).when(songMock).withModification(any());
        doAnswer(answerVoid((Mutation mutation, Runnable mutator) -> mutator.run())).when(songMock).applyChange(any(), any());
        return songMock;
    }

    // ---- Edge Cases ----

    @SuppressWarnings("PackageVisibleInnerClass")
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
            var selection = coordinator.getActiveSelection();
            assertThat(selection).isNotNull();

            var line = selection.getLine();

            ReflectionTestHelper.selectRange(coordinator, 0, 2);
            coordinator.applyActionToSelection(SHARP_ACTION, true, null);

            // Only the actual note gets the accidental
            assertThat(line.getElement(0).getAccidental()).isEqualTo(StaffElement.Accidental.SHARP);
            assertThat(line.getElement(1).getAccidental()).isNull();
            assertThat(line.getElement(2).getAccidental()).isNull();
        }

        // Pins the wiring between the toolbar accidental action and the accidental reconciliation.
        // Every other accidental test here selects all the notes at the affected staff position,
        // so none leaves a note behind that was inheriting the accidental being changed.
        //
        // Both notes sit at the same staff position; only the first carries a flat, so the second
        // sounds flat by inheriting it. Sharpening the first alone moves the context arriving at
        // the second, which must be given an explicit flat or it would silently change pitch.
        @Test
        void testSharpeningOneNoteWritesTheDisplacedFlatOntoItsUnselectedSibling() {
            var changedNote = ElementType.CROTCHET.newInstance();
            changedNote.setAccidental(StaffElement.Accidental.FLAT);
            var inheritingNote = ElementType.CROTCHET.newInstance();

            var coordinator = createCoordinator(
                List.of(changedNote, inheritingNote),
                List.of(SHARP_ACTION)
            );
            var selection = coordinator.getActiveSelection();
            assertThat(selection).isNotNull();

            var line = selection.getLine();
            var inheritedPitchBefore = line.getElement(1).getPitch();

            // Only the first note is selected — the second must be reconciled on its behalf.
            ReflectionTestHelper.selectRange(coordinator, 0, 0);
            coordinator.applyActionToSelection(SHARP_ACTION, true, null);

            assertThat(line.getElement(0).getAccidental()).isEqualTo(StaffElement.Accidental.SHARP);
            assertThat(line.getElement(1).getAccidental())
                .as("the unselected note was inheriting the flat and must now carry one of its own")
                .isEqualTo(StaffElement.Accidental.FLAT);
            assertThat(line.getElement(1).getPitch())
                .as("a note the user did not select must not change pitch")
                .isEqualTo(inheritedPitchBefore);
        }

        @Test
        void testAccidentalInParensAppliedToAccidentalNotesOnlyInMixedSelection() {
            var accidentalNote = ElementType.CROTCHET.newInstance();
            accidentalNote.setAccidental(StaffElement.Accidental.SHARP);
            var plainNote = ElementType.CROTCHET.newInstance();

            var coordinator = createCoordinator(
                List.of(accidentalNote, plainNote),
                List.of(ACCIDENTAL_IN_PARENS_ACTION)
            );
            var selection = coordinator.getActiveSelection();
            assertThat(selection).isNotNull();

            var line = selection.getLine();

            ReflectionTestHelper.selectRange(coordinator, 0, 1);
            coordinator.applyActionToSelection(ACCIDENTAL_IN_PARENS_ACTION, true, null);

            assertThat(line.getElement(0).isAccidentalInParentheses()).isTrue();
            assertThat(line.getElement(1).isAccidentalInParentheses()).isFalse();
        }

        @Test
        void testDurationChangeOnRestPreservesRestKind() {
            var notes = List.of(
                ElementType.QUAVER.newInstance(),
                ElementType.QUAVER_REST.newInstance()
            );
            var coordinator = createCoordinator(notes, List.of(HALF_ACTION));
            var selection = coordinator.getActiveSelection();
            assertThat(selection).isNotNull();

            var line = selection.getLine();

            ReflectionTestHelper.selectRange(coordinator, 0, 1);
            coordinator.applyActionToSelection(HALF_ACTION, true, null);

            assertThat(line.getElement(0).getType()).isEqualTo(ElementType.MINIM);
            assertThat(line.getElement(1).getType()).isEqualTo(ElementType.MINIM_REST);
        }

        @Test
        void testDurationChangePreservesExistingAttributes() {
            var note = ElementType.QUAVER.newInstance();
            note.setAccidental(StaffElement.Accidental.SHARP);
            note.setDotCount(1);
            note.addAttachment(new FermataAttachment(note));
            note.addArticulation(new Articulation(note, ArticulationType.STACCATO));

            var coordinator = createCoordinator(List.of(note), List.of(QUARTER_ACTION));
            var selection = coordinator.getActiveSelection();
            assertThat(selection).isNotNull();

            var line = selection.getLine();

            ReflectionTestHelper.selectNote(coordinator, 0);
            coordinator.applyActionToSelection(QUARTER_ACTION, true, null);

            var replaced = line.getElement(0);
            assertThat(replaced.getType()).isEqualTo(ElementType.CROTCHET);
            assertThat(replaced.getAccidental()).isEqualTo(StaffElement.Accidental.SHARP);
            assertThat(replaced.getDotCount()).isEqualTo(1);
            assertThat(replaced.findAttachment(FermataAttachment.class)).isNotNull();
            assertThat(replaced.hasArticulation(ArticulationType.STACCATO)).isTrue();
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
            var selection = coordinator.getActiveSelection();
            assertThat(selection).isNotNull();

            var line = selection.getLine();

            ReflectionTestHelper.selectRange(coordinator, 0, 1);
            coordinator.applyActionToSelection(QUARTER_ACTION, true, null);

            assertThat(line.getElement(0).getType()).isEqualTo(ElementType.GRACE_QUAVER);
            assertThat(line.getElement(1).getType()).isEqualTo(ElementType.CROTCHET);
        }
    }

    // ---- Full Select → Reflect → Apply → Verify Flow ----

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class FullApplyFlow {

        @Test
        void testSelectBarlinesClickBarlineTypeVerifyChanged() {
            var notes = List.of(
                ElementType.SINGLE_BARLINE.newInstance(),
                ElementType.SINGLE_BARLINE.newInstance()
            );
            var coordinator = createCoordinator(notes, List.of(DOUBLE_BARLINE_ACTION));
            var selection = coordinator.getActiveSelection();
            assertThat(selection).isNotNull();

            var line = selection.getLine();

            ReflectionTestHelper.selectRange(coordinator, 0, 1);
            coordinator.applyActionToSelection(DOUBLE_BARLINE_ACTION, true, null);

            for (var i = 0; i <= 1; i++) {
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
            var selection = coordinator.getActiveSelection();
            assertThat(selection).isNotNull();

            var line = selection.getLine();

            ReflectionTestHelper.selectRange(coordinator, 0, 2);

            // Apply dot to selection — DotAction applies to all durations (notes + rests)
            coordinator.applyActionToSelection(DOT_ACTION, true, null);

            for (var i = 0; i <= 2; i++) {
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
            var selection = coordinator.getActiveSelection();
            assertThat(selection).isNotNull();

            var line = selection.getLine();

            ReflectionTestHelper.selectRange(coordinator, 0, 1);

            // Reflect — sharp not selected (not all match), flat not selected (not all match)
            coordinator.triggerReflection();
            assertThat(SHARP_ACTION.isSelected()).isFalse();
            assertThat(FLAT_ACTION.isSelected()).isFalse();

            // Apply sharp to selection
            coordinator.applyActionToSelection(SHARP_ACTION, true, null);

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
            var selection = coordinator.getActiveSelection();
            assertThat(selection).isNotNull();

            var line = selection.getLine();

            // Select all notes
            ReflectionTestHelper.selectRange(coordinator, 0, 2);

            // Reflect — should show no duration selected (mixed quaver/semiquaver)
            coordinator.triggerReflection();
            assertThat(QUARTER_ACTION.isSelected()).isFalse();
            assertThat(HALF_ACTION.isSelected()).isFalse();

            // Apply quarter note to selection
            coordinator.applyActionToSelection(QUARTER_ACTION, true, null);

            // Verify all notes changed to crotchet
            for (var i = 0; i <= 2; i++) {
                assertThat(line.getElement(i).getType())
                    .as("note %d should be crotchet", i)
                    .isEqualTo(ElementType.CROTCHET);
            }

            // Selection should still be active
            assertThat(coordinator.getSelection()).isNotNull();
        }
    }

    // ---- Mutual Exclusivity ----

    @SuppressWarnings("PackageVisibleInnerClass")
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

        @Test
        void testAccidentalFreeSelectionDisablesAccidentalInParensAction() {
            var coordinator = createCoordinator(
                List.of(ElementType.CROTCHET.newInstance(), ElementType.CROTCHET_REST.newInstance()),
                List.of(ACCIDENTAL_IN_PARENS_ACTION)
            );

            ReflectionTestHelper.selectRange(coordinator, 0, 1);

            assertThat(coordinator.isApplicableToSelection(ACCIDENTAL_IN_PARENS_ACTION))
                .as("accidental-in-parens not applicable when no selected note has an accidental")
                .isFalse();
        }

        @Test
        void testMixedAccidentalSelectionEnablesAccidentalInParensAction() {
            var accidentalNote = ElementType.CROTCHET.newInstance();
            accidentalNote.setAccidental(StaffElement.Accidental.SHARP);
            var plainNote = ElementType.QUAVER.newInstance();

            var coordinator = createCoordinator(
                List.of(accidentalNote, plainNote),
                List.of(ACCIDENTAL_IN_PARENS_ACTION)
            );

            ReflectionTestHelper.selectRange(coordinator, 0, 1);

            assertThat(coordinator.isApplicableToSelection(ACCIDENTAL_IN_PARENS_ACTION))
                .as("accidental-in-parens applicable when at least one selected note has an accidental")
                .isTrue();
        }
    }

    // ---- Multi-Action Sequential Application ----

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class SequentialApply {

        @Test
        void testApplyDotThenChangeDurationPreservesDots() {
            var notes = List.of(
                ElementType.QUAVER.newInstance(),
                ElementType.QUAVER.newInstance()
            );
            var coordinator = createCoordinator(notes, List.of(DOT_ACTION, QUARTER_ACTION));
            var selection = coordinator.getActiveSelection();
            assertThat(selection).isNotNull();

            var line = selection.getLine();

            ReflectionTestHelper.selectRange(coordinator, 0, 1);

            // Add dots first
            coordinator.applyActionToSelection(DOT_ACTION, true, null);

            // Change duration — dots should be preserved by copy constructor
            coordinator.applyActionToSelection(QUARTER_ACTION, true, null);

            for (var i = 0; i <= 1; i++) {
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
            var selection = coordinator.getActiveSelection();
            assertThat(selection).isNotNull();

            var line = selection.getLine();

            ReflectionTestHelper.selectRange(coordinator, 0, 1);

            // Step 1: Change duration to quarter
            coordinator.applyActionToSelection(QUARTER_ACTION, true, null);

            // Step 2: Add sharp
            coordinator.applyActionToSelection(SHARP_ACTION, true, null);

            // Step 3: Add fermata
            coordinator.applyActionToSelection(FERMATA_ACTION, true, null);

            // Step 4: Add staccato
            coordinator.applyActionToSelection(STACCATO_ACTION, true, null);

            // Verify all attributes applied to both notes
            for (var i = 0; i <= 1; i++) {
                var note = line.getElement(i);
                assertThat(note.getType())
                    .as("note %d type", i).isEqualTo(ElementType.CROTCHET);
                assertThat(note.getAccidental())
                    .as("note %d accidental", i).isEqualTo(StaffElement.Accidental.SHARP);
                assertThat(note.findAttachment(FermataAttachment.class))
                    .as("note %d fermata", i).isNotNull();
                assertThat(note.hasArticulation(ArticulationType.STACCATO))
                    .as("note %d staccato", i).isTrue();
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
            var selection = coordinator.getActiveSelection();
            assertThat(selection).isNotNull();

            var line = selection.getLine();

            ReflectionTestHelper.selectRange(coordinator, 0, 1);

            // Apply fermata
            coordinator.applyActionToSelection(FERMATA_ACTION, true, null);
            assertThat(line.getElement(0).findAttachment(FermataAttachment.class)).isNotNull();
            assertThat(line.getElement(1).findAttachment(FermataAttachment.class)).isNotNull();

            // Remove fermata
            coordinator.applyActionToSelection(FERMATA_ACTION, false, null);
            assertThat(line.getElement(0).findAttachment(FermataAttachment.class)).isNull();
            assertThat(line.getElement(1).findAttachment(FermataAttachment.class)).isNull();
        }
    }

    // ---- State Management Integration ----

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class StateManagement {

        @Test
        void testManagedActionsIncludeNonReflectableWithFlag() {
            var note = ElementType.CROTCHET.newInstance();

            var fermataAction = FermataAction.createAction(mainFrame());
            var flaggedAction = new UIAction(mainFrame(), "Beam", null, 0, "beam", "Toggle beam");
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
            coordinator.triggerReflection();

            // Simulate flag chain disabling the non-reflectable action
            flaggedAction.setEnabled(false);

            // Clear and reflect — both actions should be restored
            ReflectionTestHelper.clearSelection(coordinator);
            coordinator.triggerReflection();

            assertThat(flaggedAction.isEnabled())
                .as("non-reflectable action enabled state restored")
                .isTrue();
        }

        @Test
        void testSaveReflectApplyClearRestoresState() {
            var note = ElementType.CROTCHET.newInstance();
            note.setAccidental(StaffElement.Accidental.FLAT);

            var sharpAction = AccidentalAction.createSharpAction(mainFrame());
            var flatAction = AccidentalAction.createFlatAction(mainFrame());
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
            coordinator.triggerReflection();

            // Note has FLAT, so SHARP=false, FLAT=true
            assertThat(sharpAction.isSelected()).isFalse();
            assertThat(flatAction.isSelected()).isTrue();

            // Apply sharp — mutates the note
            coordinator.applyActionToSelection(sharpAction, true, null);
            var selection = coordinator.getActiveSelection();
            assertThat(selection).isNotNull();

            var line = selection.getLine();
            assertThat(line.getElement(0).getAccidental()).isEqualTo(StaffElement.Accidental.SHARP);

            // Simulate disabled state during selection (flag chain would do this)
            sharpAction.setEnabled(false);

            // Clear selection and reflect — restores pre-selection state
            ReflectionTestHelper.clearSelection(coordinator);
            coordinator.triggerReflection();

            assertThat(sharpAction.isSelected()).as("sharp restored to pre-selection selected").isFalse();
            assertThat(sharpAction.isEnabled()).as("sharp restored to pre-selection enabled").isTrue();
            assertThat(flatAction.isSelected()).as("flat restored to pre-selection selected").isTrue();
            assertThat(flatAction.isEnabled()).as("flat restored to pre-selection enabled").isTrue();
        }
    }
}
