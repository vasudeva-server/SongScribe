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
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import module java.desktop;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import songscribe.UnitTest;
import songscribe.music.DynamicsSpan;
import songscribe.music.ElementType;
import songscribe.music.Line;
import songscribe.ui.component.MainFrame;
import songscribe.ui.layout.DynamicAttachment;
import songscribe.ui.layout.DynamicAttachment.DynamicType;
import songscribe.ui.selection.ElementSelection;

class DynamicMarkingActionTest extends UnitTest {

    private static final DynamicMarkingAction FORTE_ACTION =
        DynamicMarkingAction.createForteAction();

    private static final DynamicMarkingAction PIANO_ACTION =
        DynamicMarkingAction.createPianoAction();

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class ApplyToElement {

        @Test
        @SuppressWarnings("NullAway")
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
        @SuppressWarnings("NullAway")
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

        private MockEnvHelper.MockEnv setupFullMockEnv(MockedStatic<MainFrame> mainFrameMock) {
            var env = MockEnvHelper.setupMockEnv(mainFrameMock);

            var mockRootPane = mock(JRootPane.class);
            when(mockRootPane.getInputMap(anyInt())).thenReturn(new InputMap());
            when(mockRootPane.getActionMap()).thenReturn(new ActionMap());
            when(env.frame().getRootPane()).thenReturn(mockRootPane);

            return env;
        }

        @Test
        void testDisabledWhenNoSelection() {
            try (var mainFrameMock = mockStatic(MainFrame.class)) {
                var env = setupFullMockEnv(mainFrameMock);
                when(env.score().getSelectionSize()).thenReturn(0);

                var action = DynamicMarkingAction.createForteAction();
                action.updateEnabledState();
                assertThat(action.isEnabled()).isFalse();
            }
        }

        @Test
        void testDisabledWhenMultipleNotesSelected() {
            try (var mainFrameMock = mockStatic(MainFrame.class)) {
                var env = setupFullMockEnv(mainFrameMock);
                when(env.score().getSelectionSize()).thenReturn(2);
                when(env.coordinator().hasActiveSelection()).thenReturn(true);

                var action = DynamicMarkingAction.createForteAction();
                action.updateEnabledState();
                assertThat(action.isEnabled()).isFalse();
            }
        }

        @Test
        void testEnabledWhenSingleNoteNotInHairpin() {
            try (var mainFrameMock = mockStatic(MainFrame.class)) {
                var env = setupFullMockEnv(mainFrameMock);
                var line = detachedLine();
                var selection = new ElementSelection(line, 0, 0);

                when(env.score().getSelectionSize()).thenReturn(1);
                when(env.coordinator().hasActiveSelection()).thenReturn(true);
                when(env.coordinator().isApplicableToSelection(any())).thenReturn(true);
                when(env.coordinator().getSelection()).thenReturn(selection);

                var action = DynamicMarkingAction.createForteAction();
                action.updateEnabledState();
                assertThat(action.isEnabled()).isTrue();
            }
        }

        @Test
        void testDisabledWhenNoteInsideCrescendoRange() {
            try (var mainFrameMock = mockStatic(MainFrame.class)) {
                var env = setupFullMockEnv(mainFrameMock);
                var line = detachedLine();
                line.getCrescendos().addSpan(new DynamicsSpan(0, 3));
                var selection = new ElementSelection(line, 1, 1);

                when(env.score().getSelectionSize()).thenReturn(1);
                when(env.coordinator().hasActiveSelection()).thenReturn(true);
                when(env.coordinator().isApplicableToSelection(any())).thenReturn(true);
                when(env.coordinator().getSelection()).thenReturn(selection);

                var action = DynamicMarkingAction.createForteAction();
                action.updateEnabledState();
                assertThat(action.isEnabled()).isFalse();
            }
        }

        @Test
        void testDisabledWhenNoteInsideDiminuendoRange() {
            try (var mainFrameMock = mockStatic(MainFrame.class)) {
                var env = setupFullMockEnv(mainFrameMock);
                var line = detachedLine();
                line.getDiminuendos().addSpan(new DynamicsSpan(0, 3));
                var selection = new ElementSelection(line, 1, 1);

                when(env.score().getSelectionSize()).thenReturn(1);
                when(env.coordinator().hasActiveSelection()).thenReturn(true);
                when(env.coordinator().isApplicableToSelection(any())).thenReturn(true);
                when(env.coordinator().getSelection()).thenReturn(selection);

                var action = DynamicMarkingAction.createForteAction();
                action.updateEnabledState();
                assertThat(action.isEnabled()).isFalse();
            }
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
