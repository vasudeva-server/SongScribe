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

import module java.desktop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.MainFrameMockTest;
import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.StaffElement;
import songscribe.prefs.Prefs;
import songscribe.prefs.PrefsKey;
import songscribe.ui.playback.PlayThread;
import songscribe.ui.selection.ElementSelection;
import songscribe.ui.selection.SelectionActionApplier;

class AccidentalActionTest extends MainFrameMockTest {

    private AccidentalAction action;

    @BeforeEach
    void createAction() {
        action = AccidentalAction.createSharpAction(mainFrame());
    }

    @Test
    void testApplyToNoteAppliesAccidental() {
        var note = ElementType.CROTCHET.newInstance();
        action.applyToElement(note, true);
        assertThat(note.getAccidental()).isEqualTo(StaffElement.Accidental.SHARP);
    }

    @Test
    void testApplyToNoteRemovesAccidental() {
        var note = ElementType.CROTCHET.newInstance();
        note.setAccidental(StaffElement.Accidental.SHARP);
        action.applyToElement(note, false);
        assertThat(note.getAccidental()).isNull();
    }

    @Test
    void testDoesNotMatchWhenAccidentalDiffers() {
        var note = ElementType.CROTCHET.newInstance();
        note.setAccidental(StaffElement.Accidental.FLAT);
        assertThat(action.matchesElement(note)).isFalse();
    }

    @Test
    void testMatchesWhenAccidentalMatches() {
        var note = ElementType.CROTCHET.newInstance();
        note.setAccidental(StaffElement.Accidental.SHARP);
        assertThat(action.matchesElement(note)).isTrue();
    }

    @Nested
    class PlaySelectedNote {

        @BeforeEach
        void setUpActiveSelection() {
            when(mockEnv().coordinator().getSelection()).thenReturn(
                new ElementSelection(mock(Line.class), 0, 2)
            );
        }

        /**
         * The selection above exists only to send the action down its
         * apply-to-selection branch. The applier is stubbed out because it would reach
         * through the mocked line the selection names, and what these tests observe is
         * the note-playing decision that happens before it.
         */
        @Test
        void testPlayThreadNotStartedWhenPrefIsDisabled() {
            try (var prefsMock = mockStatic(Prefs.class);
                 var _ = mockStatic(SelectionActionApplier.class);
                 var playMock = mockConstruction(PlayThread.class)) {
                prefsMock.when(() -> Prefs.getBoolean(PrefsKey.PLAY_SELECTED_NOTE)).thenReturn(false);
                action.actionPerformed(new ActionEvent(new JButton(), ActionEvent.ACTION_PERFORMED, ""));
                assertThat(playMock.constructed()).isEmpty();
            }
        }

        @Test
        void testPlayThreadNotStartedWhenSelectedElementIsNull() {
            try (var prefsMock = mockStatic(Prefs.class);
                 var _ = mockStatic(SelectionActionApplier.class);
                 var playMock = mockConstruction(PlayThread.class)) {
                prefsMock.when(() -> Prefs.getBoolean(PrefsKey.PLAY_SELECTED_NOTE)).thenReturn(true);
                when(mockEnv().score().getSingleSelectedElement()).thenReturn(null);
                action.actionPerformed(new ActionEvent(new JButton(), ActionEvent.ACTION_PERFORMED, ""));
                assertThat(playMock.constructed()).isEmpty();
            }
        }
    }
}
