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

import songscribe.MainFrameMockTest;
import songscribe.dom.ElementType;
import songscribe.ui.component.ScoreView;
import songscribe.ui.selection.Selection;
import songscribe.ui.selection.SelectionCoordinator;

class EditLyricActionGraceNoteTest extends MainFrameMockTest {

    private ScoreView mockScore;
    private SelectionCoordinator mockCoordinator;
    private EditLyricAction action;

    @BeforeEach
    void setUp() {
        mockScore = mock(ScoreView.class);
        mockCoordinator = mock(SelectionCoordinator.class);

        when(mockScore.getSelectionCoordinator()).thenReturn(mockCoordinator);

        action = EditLyricAction.createAction(mainFrame());
    }

    private void pairedGraceAt(int graceIndex) {
        var line = detachedLine();

        for (var i = 0; i < graceIndex; i++) {
            line.addElement(ElementType.CROTCHET.newInstance());
        }

        var grace = ElementType.GRACE_QUAVER.newInstance();
        grace.setGlissando();
        line.addElement(grace);
        line.addElement(ElementType.CROTCHET.newInstance());

        when(mockCoordinator.getRange())
            .thenReturn(Selection.Range.single(line, graceIndex));
    }

    private void hostOfPairedGraceAt(int hostIndex) {
        var line = detachedLine();

        for (var i = 0; i < hostIndex - 1; i++) {
            line.addElement(ElementType.CROTCHET.newInstance());
        }

        var grace = ElementType.GRACE_QUAVER.newInstance();
        grace.setGlissando();
        line.addElement(grace);
        var host = ElementType.CROTCHET.newInstance();
        line.addElement(host);

        when(mockCoordinator.getRange())
            .thenReturn(Selection.Range.single(line, hostIndex));
    }

    @Test
    void testEnabledForPairedGraceNote() {
        pairedGraceAt(0);
        assertThat(action.enableFromSelection(true, mockScore)).isTrue();
    }

    @Test
    void testEnabledForHostOfPairedGraceNote() {
        // The host carries no lyric of its own, but the command is still offered: it
        // redirects to the grace note that holds the pair's lyric, matching what a
        // double-click or Return on the same host does.
        hostOfPairedGraceAt(1);
        assertThat(action.enableFromSelection(true, mockScore)).isTrue();
    }

@Test
    void testEnabledForUnpairedGraceNote() {
        var line = detachedLine();
        var grace = ElementType.GRACE_QUAVER.newInstance();
        line.addElement(grace);
        line.addElement(ElementType.CROTCHET.newInstance());

        when(mockCoordinator.getRange())
            .thenReturn(Selection.Range.single(line, 0));

        assertThat(action.enableFromSelection(true, mockScore)).isTrue();
    }

    @Test
    void testDisabledWhenNoElementSelected() {
        when(mockCoordinator.getRange()).thenReturn(null);
        assertThat(action.enableFromSelection(true, mockScore)).isFalse();
    }

    @Test
    void testDisabledWhenMoreThanOneElementSelected() {
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());

        when(mockCoordinator.getRange()).thenReturn(new Selection.Range(line, 0, 1, 0));

        assertThat(action.enableFromSelection(true, mockScore)).isFalse();
    }
}
