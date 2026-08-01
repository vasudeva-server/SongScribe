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

import module java.desktop;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.MainFrameMockTest;
import songscribe.dom.StaffElementFactory;
import songscribe.dom.Crescendo;
import songscribe.dom.Diminuendo;
import songscribe.dom.ElementType;
import songscribe.dom.DynamicAttachment;
import songscribe.dom.DynamicAttachment.DynamicType;
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
            var note = ElementType.CROTCHET.newInstance();
            FORTE_ACTION.applyToElement(note, true);

            var attachment = note.findAttachment(DynamicAttachment.class);
            assertThat(attachment).isNotNull();
            assertThat(attachment.getType()).isEqualTo(DynamicType.FORTE);
        }

        @Test
        void testNoOpWhenNotSelectedAndNoDynamic() {
            var note = ElementType.CROTCHET.newInstance();
            FORTE_ACTION.applyToElement(note, false);
            assertThat(note.findAttachment(DynamicAttachment.class)).isNull();
        }

        @Test
        void testReplaceDifferentType() {
            var note = ElementType.CROTCHET.newInstance();
            note.addAttachment(new DynamicAttachment(note, DynamicType.PIANO));
            FORTE_ACTION.applyToElement(note, true);

            var attachment = note.findAttachment(DynamicAttachment.class);
            assertThat(attachment).isNotNull();
            assertThat(attachment.getType()).isEqualTo(DynamicType.FORTE);
        }

        @Test
        void testToggleOffSameType() {
            var note = ElementType.CROTCHET.newInstance();
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
            var note = ElementType.CROTCHET.newInstance();
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
            var note = ElementType.CROTCHET.newInstance();
            note.addAttachment(new DynamicAttachment(note, DynamicType.FORTE));
            assertThat(FORTE_ACTION.matchesElement(note)).isTrue();
        }

        @Test
        void testNoMatchWhenNoteHasDifferentType() {
            var note = ElementType.CROTCHET.newInstance();
            note.addAttachment(new DynamicAttachment(note, DynamicType.PIANO));
            assertThat(FORTE_ACTION.matchesElement(note)).isFalse();
        }

        @Test
        void testNoMatchWhenNoteHasNoDynamic() {
            var note = ElementType.CROTCHET.newInstance();
            assertThat(FORTE_ACTION.matchesElement(note)).isFalse();
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class EnabledState {

        private void setupRootPaneStub() {
            var mockRootPane = mock(JRootPane.class);
            when(mockRootPane.getInputMap(anyInt())).thenReturn(new InputMap());
            when(mockRootPane.getActionMap()).thenReturn(new ActionMap());
            when(mockEnv().frame().getRootPane()).thenReturn(mockRootPane);
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
            setupRootPaneStub();
            var line = detachedLine();

            for (var i = 0; i < 4; i++) {
                line.addElement(StaffElementFactory.crotchet());
            }

            line.addRangeElement(new Crescendo(line.getElement(0), line.getElement(3)));
            var selection = new ElementSelection(line, 1, 1);

            when(mockEnv().score().getSelectionSize()).thenReturn(1);
            when(mockEnv().coordinator().hasActiveSelection()).thenReturn(true);
            when(mockEnv().coordinator().isApplicableToSelection(any())).thenReturn(true);
            when(mockEnv().coordinator().getSelection()).thenReturn(selection);

            var action = DynamicMarkingAction.createForteAction(mainFrame());
            action.updateEnabledState();
            assertThat(action.isEnabled()).isFalse();
        }

        @Test
        void testDisabledWhenNoteInsideDiminuendoRange() {
            setupRootPaneStub();
            var line = detachedLine();

            for (var i = 0; i < 4; i++) {
                line.addElement(StaffElementFactory.crotchet());
            }

            line.addRangeElement(new Diminuendo(line.getElement(0), line.getElement(3)));
            var selection = new ElementSelection(line, 1, 1);

            when(mockEnv().score().getSelectionSize()).thenReturn(1);
            when(mockEnv().coordinator().hasActiveSelection()).thenReturn(true);
            when(mockEnv().coordinator().isApplicableToSelection(any())).thenReturn(true);
            when(mockEnv().coordinator().getSelection()).thenReturn(selection);

            var action = DynamicMarkingAction.createForteAction(mainFrame());
            action.updateEnabledState();
            assertThat(action.isEnabled()).isFalse();
        }

        // Row 26: isInHairpinRange uses inclusive bounds — anchor (index 0) and end (index 3)
        // both fall inside the range [0..3] and must disable the action.

        @Test
        void testDisabledWhenNoteIsAtHairpinAnchorBoundary() {
            setupRootPaneStub();
            var line = detachedLine();

            for (var i = 0; i < 4; i++) {
                line.addElement(StaffElementFactory.crotchet());
            }

            line.addRangeElement(new Crescendo(line.getElement(0), line.getElement(3)));
            var selection = new ElementSelection(line, 0, 0);

            when(mockEnv().score().getSelectionSize()).thenReturn(1);
            when(mockEnv().coordinator().hasActiveSelection()).thenReturn(true);
            when(mockEnv().coordinator().isApplicableToSelection(any())).thenReturn(true);
            when(mockEnv().coordinator().getSelection()).thenReturn(selection);

            var action = DynamicMarkingAction.createForteAction(mainFrame());
            action.updateEnabledState();
            assertThat(action.isEnabled()).isFalse();
        }

        @Test
        void testDisabledWhenNoteIsAtHairpinEndBoundary() {
            setupRootPaneStub();
            var line = detachedLine();

            for (var i = 0; i < 4; i++) {
                line.addElement(StaffElementFactory.crotchet());
            }

            line.addRangeElement(new Crescendo(line.getElement(0), line.getElement(3)));
            var selection = new ElementSelection(line, 3, 3);

            when(mockEnv().score().getSelectionSize()).thenReturn(1);
            when(mockEnv().coordinator().hasActiveSelection()).thenReturn(true);
            when(mockEnv().coordinator().isApplicableToSelection(any())).thenReturn(true);
            when(mockEnv().coordinator().getSelection()).thenReturn(selection);

            var action = DynamicMarkingAction.createForteAction(mainFrame());
            action.updateEnabledState();
            assertThat(action.isEnabled()).isFalse();
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
