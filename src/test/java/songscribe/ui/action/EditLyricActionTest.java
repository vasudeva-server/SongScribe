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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import songscribe.MainFrameMockTest;
import songscribe.RequiresDisplay;
import songscribe.dom.ElementType;
import songscribe.ui.component.LyricEditor;
import songscribe.util.UIUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class EditLyricActionTest extends MainFrameMockTest {

    private EditLyricAction action;

    @BeforeEach
    void setUp() {
        action = EditLyricAction.createAction(mainFrame());
    }

    // Row 18: actionPerformed throws ISE when REQUIRES_SINGLE_SELECTION violated (no element selected)

    @Test
    void testActionPerformedThrowsIllegalStateExceptionWhenNoElementSelected() {
        when(mockEnv().coordinator().getSingleSelectedElement()).thenReturn(null);

        assertThatThrownBy(
            () -> action.actionPerformed(
                new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "edit-lyric"))
        ).isInstanceOf(IllegalStateException.class);
    }

    // T27

    @Test
    void testActionPerformedOpensEditorForSelectedElement() {
        var line = detachedLine();
        var note = ElementType.CROTCHET.newInstance();
        line.addElement(note);
        when(mockEnv().coordinator().getSingleSelectedElement()).thenReturn(note);

        try (var lyricEditorMock = mockStatic(LyricEditor.class)) {
            action.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "edit-lyric"));
            var score = mockEnv().score();
            lyricEditorMock.verify(() -> LyricEditor.deselectAndOpenOn(score, line, 0));
        }
    }

    /**
     * A note that hosts a paired grace note carries no lyric of its own — the pair's lyric
     * lives on the grace note — so the command opens the editor on the grace note beside it
     * rather than on the selected host. This matches the double-click and Return gestures.
     */
    @Test
    void testActionPerformedOnGraceHostOpensEditorOnTheGraceNote() {
        var line = detachedLine();
        var grace = ElementType.GRACE_QUAVER.newInstance();
        grace.setGlissando();
        line.addElement(grace);
        var host = ElementType.CROTCHET.newInstance();
        line.addElement(host);
        when(mockEnv().coordinator().getSingleSelectedElement()).thenReturn(host);

        try (var lyricEditorMock = mockStatic(LyricEditor.class)) {
            action.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "edit-lyric"));
            var score = mockEnv().score();
            lyricEditorMock.verify(() -> LyricEditor.deselectAndOpenOn(score, line, 0));
        }
    }

    // T26

    @Test
    void testCarriesDisableWhenEditingTextFlag() {
        assertThat(EditLyricAction.FLAGS).contains(UIAction.Flag.DISABLE_WHEN_EDITING_TEXT);
    }

    @Test
    void testDisabledWhenEditingText() {
        try (var uiUtilsMock = mockStatic(UIUtils.class)) {
            uiUtilsMock.when(() -> UIUtils.isEditingTextIn(any(Window.class))).thenReturn(true);
            assertThat(action.enableFromTextEditingState()).isFalse();
        }
    }

    @Test
    void testEnabledWhenNotEditingText() {
        try (var uiUtilsMock = mockStatic(UIUtils.class)) {
            uiUtilsMock.when(() -> UIUtils.isEditingTextIn(any(Window.class))).thenReturn(false);
            assertThat(action.enableFromTextEditingState()).isTrue();
        }
    }

    @Test
    @RequiresDisplay
    void testIsEditingTextInReturnsFalseForUnfocusedWindow() {
        // No mockStatic needed — isEditingTextIn takes a Window directly.
        // In a headless test environment the focused frame is null, so editing is never active.
        assertThat(UIUtils.isEditingTextIn(new JFrame())).isFalse();
    }
}
