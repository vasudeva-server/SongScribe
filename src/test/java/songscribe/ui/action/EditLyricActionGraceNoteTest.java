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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.music.ElementType;
import songscribe.music.StaffElement;
import songscribe.ui.component.Score;
import songscribe.ui.selection.LineSelectionState;
import songscribe.ui.selection.SelectionCoordinator;

class EditLyricActionGraceNoteTest extends UnitTest {

    private Score mockScore;
    private SelectionCoordinator mockCoordinator;
    private LineSelectionState mockLineState;
    private EditLyricAction action;

    @BeforeEach
    void setUp() {
        mockScore = mock(Score.class);
        mockCoordinator = mock(SelectionCoordinator.class);
        mockLineState = mock(LineSelectionState.class);

        when(mockScore.getSelectionCoordinator()).thenReturn(mockCoordinator);
        when(mockCoordinator.getActiveSelection()).thenReturn(mockLineState);

        action = EditLyricAction.createAction();
    }

    private StaffElement pairedGraceAt(int graceIndex) {
        var line = detachedLine();

        for (var i = 0; i < graceIndex; i++) {
            line.addElement(ElementType.CROTCHET.newInstance());
        }

        var grace = ElementType.GRACE_QUAVER.newInstance();
        grace.setGlissando(StaffElement.Glissando.Type.CONNECTED);
        line.addElement(grace);
        line.addElement(ElementType.CROTCHET.newInstance());

        when(mockCoordinator.getSingleSelectedElement()).thenReturn(grace);
        when(mockLineState.getLine()).thenReturn(line);
        when(mockLineState.getSelectionBegin()).thenReturn(graceIndex);
        return grace;
    }

    private StaffElement hostOfPairedGraceAt(int hostIndex) {
        var line = detachedLine();

        for (var i = 0; i < hostIndex - 1; i++) {
            line.addElement(ElementType.CROTCHET.newInstance());
        }

        var grace = ElementType.GRACE_QUAVER.newInstance();
        grace.setGlissando(StaffElement.Glissando.Type.CONNECTED);
        line.addElement(grace);
        var host = ElementType.CROTCHET.newInstance();
        line.addElement(host);

        when(mockCoordinator.getSingleSelectedElement()).thenReturn(host);
        when(mockLineState.getLine()).thenReturn(line);
        when(mockLineState.getSelectionBegin()).thenReturn(hostIndex);
        return host;
    }

    @Test
    void testEnabledForPairedGraceNote() {
        pairedGraceAt(0);
        assertThat(action.enableFromSelection(true, mockScore)).isTrue();
    }

    @Test
    void testDisabledForHostOfPairedGraceNote() {
        hostOfPairedGraceAt(1);
        assertThat(action.enableFromSelection(true, mockScore)).isFalse();
    }

@Test
    void testEnabledForUnpairedGraceNote() {
        var line = detachedLine();
        var grace = ElementType.GRACE_QUAVER.newInstance();
        line.addElement(grace);
        line.addElement(ElementType.CROTCHET.newInstance());

        when(mockCoordinator.getSingleSelectedElement()).thenReturn(grace);
        when(mockLineState.getLine()).thenReturn(line);
        when(mockLineState.getSelectionBegin()).thenReturn(0);

        assertThat(action.enableFromSelection(true, mockScore)).isTrue();
    }

    @Test
    void testDisabledWhenNoElementSelected() {
        when(mockCoordinator.getSingleSelectedElement()).thenReturn(null);
        assertThat(action.enableFromSelection(true, mockScore)).isFalse();
    }
}
