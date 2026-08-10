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

package songscribe.ui.action;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static songscribe.dom.StaffElementFactory.crotchet;

import module java.desktop;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.MainFrameMockTest;
import songscribe.dom.StaffElementFactory;
import songscribe.dom.Crescendo;
import songscribe.dom.Diminuendo;
import songscribe.dom.DynamicAttachment;
import songscribe.dom.DynamicAttachment.DynamicType;
import songscribe.dom.Line;
import songscribe.ui.selection.ElementSelection;

class DynamicMarkingActionTest extends MainFrameMockTest {

    private DynamicMarkingAction FORTE_ACTION;
    private DynamicMarkingAction PIANO_ACTION;

    @BeforeEach
    void createActions() {
        FORTE_ACTION = DynamicMarkingAction.createForteAction(mainFrame());
        PIANO_ACTION = DynamicMarkingAction.createPianoAction(mainFrame());
    }

    @SuppressWarnings({ "PackageVisibleInnerClass", "DataFlowIssue" })
    @Nested
    class ApplyToElement {

        @Test
        void testAddDynamicToNoteWithNone() {
            var note = crotchet();
            FORTE_ACTION.applyToElement(note, true);

            var attachment = note.findAttachment(DynamicAttachment.class);
            assertThat(attachment).isNotNull();
            assertThat(attachment.getType()).isEqualTo(DynamicType.FORTE);
        }

        @Test
        void testNoOpWhenNotSelectedAndNoDynamic() {
            var note = crotchet();
            FORTE_ACTION.applyToElement(note, false);
            assertThat(note.findAttachment(DynamicAttachment.class)).isNull();
        }

        @Test
        void testReplaceDifferentType() {
            var note = crotchet();
            note.addAttachment(new DynamicAttachment(note, DynamicType.PIANO));
            FORTE_ACTION.applyToElement(note, true);

            var attachment = note.findAttachment(DynamicAttachment.class);
            assertThat(attachment).isNotNull();
            assertThat(attachment.getType()).isEqualTo(DynamicType.FORTE);
        }

        @Test
        void testToggleOffSameType() {
            var note = crotchet();
            note.addAttachment(new DynamicAttachment(note, DynamicType.FORTE));
            FORTE_ACTION.applyToElement(note, true);
            assertThat(note.findAttachment(DynamicAttachment.class)).isNull();
        }

        // Row 17: selected=false with an existing same-type dynamic — the deselect path removes it.
        // This is a third logical branch: existing!=null && isSameType && selected==false.
        // The code removes the existing attachment and then skips the re-add (because selected=false),
        // so the note ends up with no dynamic.
        @Test
        void testRemovesDynamicWhenNotSelectedAndSameTypeExists() {
            var note = crotchet();
            note.addAttachment(new DynamicAttachment(note, DynamicType.FORTE));
            FORTE_ACTION.applyToElement(note, false);
            assertThat(note.findAttachment(DynamicAttachment.class)).isNull();
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class MatchesElement {

        @Test
        void testMatchesWhenNoteHasMatchingType() {
            var note = crotchet();
            note.addAttachment(new DynamicAttachment(note, DynamicType.FORTE));
            assertThat(FORTE_ACTION.matchesElement(note)).isTrue();
        }

        @Test
        void testNoMatchWhenNoteHasDifferentType() {
            var note = crotchet();
            note.addAttachment(new DynamicAttachment(note, DynamicType.PIANO));
            assertThat(FORTE_ACTION.matchesElement(note)).isFalse();
        }

        @Test
        void testNoMatchWhenNoteHasNoDynamic() {
            var note = crotchet();
            assertThat(FORTE_ACTION.matchesElement(note)).isFalse();
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class EnabledState {

        /** Enough notes for a hairpin plus a note on either side of it. */
        private static final int HAIRPIN_LINE_NOTE_COUNT = 5;

        /** The span of the hairpin the tests below build: notes 1 through 3 of that line. */
        private static final int HAIRPIN_ANCHOR_INDEX = 1;
        private static final int HAIRPIN_INTERIOR_INDEX = 2;
        private static final int HAIRPIN_END_INDEX = 3;

        private void setupRootPaneStub() {
            var mockRootPane = mock(JRootPane.class);
            when(mockRootPane.getInputMap(anyInt())).thenReturn(new InputMap());
            when(mockRootPane.getActionMap()).thenReturn(new ActionMap());
            when(mockEnv().frame().getRootPane()).thenReturn(mockRootPane);
        }

        private Line hairpinLine() {
            var line = detachedLine();

            for (var i = 0; i < HAIRPIN_LINE_NOTE_COUNT; i++) {
                line.addElement(StaffElementFactory.crotchet());
            }

            return line;
        }

        /**
         * Points the mocked selection at the single note {@code noteIndex} of {@code line} and
         * returns a forte action that has just recomputed its enabled state for it.
         */
        private DynamicMarkingAction forteActionForSingleNote(Line line, int noteIndex) {
            setupRootPaneStub();

            when(mockEnv().score().getSelectionSize()).thenReturn(1);
            when(mockEnv().coordinator().hasActiveSelection()).thenReturn(true);
            when(mockEnv().coordinator().isApplicableToSelection(any())).thenReturn(true);
            when(mockEnv().coordinator().getSelection())
                .thenReturn(new ElementSelection(line, noteIndex, noteIndex));

            var action = DynamicMarkingAction.createForteAction(mainFrame());
            action.updateEnabledState();

            return action;
        }

        @Test
        void testDisabledWhenNoSelection() {
            setupRootPaneStub();
            when(mockEnv().score().getSelectionSize()).thenReturn(0);

            var action = DynamicMarkingAction.createForteAction(mainFrame());
            action.updateEnabledState();
            assertThat(action.isEnabled()).isFalse();
        }

        @Test
        void testDisabledWhenMultipleNotesSelected() {
            setupRootPaneStub();
            when(mockEnv().score().getSelectionSize()).thenReturn(2);
            when(mockEnv().coordinator().hasActiveSelection()).thenReturn(true);

            var action = DynamicMarkingAction.createForteAction(mainFrame());
            action.updateEnabledState();
            assertThat(action.isEnabled()).isFalse();
        }

        @Test
        void testEnabledWhenSingleNoteNotInHairpin() {
            setupRootPaneStub();
            var line = detachedLine();
            var selection = new ElementSelection(line, 0, 0);

            when(mockEnv().score().getSelectionSize()).thenReturn(1);
            when(mockEnv().coordinator().hasActiveSelection()).thenReturn(true);
            when(mockEnv().coordinator().isApplicableToSelection(any())).thenReturn(true);
            when(mockEnv().coordinator().getSelection()).thenReturn(selection);

            var action = DynamicMarkingAction.createForteAction(mainFrame());
            action.updateEnabledState();
            assertThat(action.isEnabled()).isTrue();
        }

        @Test
        void testDisabledWhenNoteInsideCrescendoRange() {
            var line = hairpinLine();
            line.addSpan(new Crescendo(
                line.getElement(HAIRPIN_ANCHOR_INDEX), line.getElement(HAIRPIN_END_INDEX)));

            var action = forteActionForSingleNote(line, HAIRPIN_INTERIOR_INDEX);

            assertThat(action.isEnabled())
                .as("a note under the wedge cannot take a dynamic")
                .isFalse();
        }

        @Test
        void testDisabledWhenNoteInsideDiminuendoRange() {
            var line = hairpinLine();
            line.addSpan(new Diminuendo(
                line.getElement(HAIRPIN_ANCHOR_INDEX), line.getElement(HAIRPIN_END_INDEX)));

            var action = forteActionForSingleNote(line, HAIRPIN_INTERIOR_INDEX);

            assertThat(action.isEnabled())
                .as("the rule is about the wedge, not its direction, so a diminuendo bars it too")
                .isFalse();
        }

        // A text dynamic may sit on any hairpin bound — anchor or end — because the wedge pads
        // away from it. Only the strict interior of the range is off limits.

        @Test
        void testEnabledWhenNoteIsAtHairpinAnchorBoundary() {
            var line = hairpinLine();
            line.addSpan(new Crescendo(
                line.getElement(HAIRPIN_ANCHOR_INDEX), line.getElement(HAIRPIN_END_INDEX)));

            var action = forteActionForSingleNote(line, HAIRPIN_ANCHOR_INDEX);

            assertThat(action.isEnabled())
                .as("the anchor is a bound the wedge pads away from, so f< is offerable")
                .isTrue();
        }

        @Test
        void testEnabledWhenNoteIsAtHairpinEndBoundary() {
            var line = hairpinLine();
            line.addSpan(new Crescendo(
                line.getElement(HAIRPIN_ANCHOR_INDEX), line.getElement(HAIRPIN_END_INDEX)));

            var action = forteActionForSingleNote(line, HAIRPIN_END_INDEX);

            assertThat(action.isEnabled())
                .as("the end is a bound the wedge pads away from, so <f is offerable")
                .isTrue();
        }

        @Test
        void testEnabledWhenNoteIsSharedByBackToBackHairpins() {
            var line = hairpinLine();
            // Two opposite hairpins meeting on the interior note, which is a bound of each — the
            // <f> shape. Belonging to two hairpins at once must not make it interior to either.
            line.addSpan(new Crescendo(
                line.getElement(HAIRPIN_ANCHOR_INDEX), line.getElement(HAIRPIN_INTERIOR_INDEX)));
            line.addSpan(new Diminuendo(
                line.getElement(HAIRPIN_INTERIOR_INDEX), line.getElement(HAIRPIN_END_INDEX)));

            var action = forteActionForSingleNote(line, HAIRPIN_INTERIOR_INDEX);

            assertThat(action.isEnabled())
                .as("the note two hairpins share is a bound of both, so <f> is offerable")
                .isTrue();
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class ActionGroupBehavior {

        @Test
        void testSelectingOneActionDeselectsPrevious() {
            var group = new ActionGroup<>(FORTE_ACTION, PIANO_ACTION);
            group.setSelected(FORTE_ACTION, true);
            assertThat(group.getSelected()).isEqualTo(FORTE_ACTION);

            group.setSelected(PIANO_ACTION, true);
            assertThat(group.getSelected()).isEqualTo(PIANO_ACTION);
            assertThat(group.isSelected(FORTE_ACTION)).isFalse();
        }

        @Test
        void testClearSelectionClearsAll() {
            var group = new ActionGroup<>(FORTE_ACTION, PIANO_ACTION);
            group.setSelected(FORTE_ACTION, true);
            group.clearSelection();
            assertThat(group.getSelected()).isNull();
            assertThat(group.isSelected(FORTE_ACTION)).isFalse();
        }
    }
}
