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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import songscribe.UnitTest;
import songscribe.model.Line;
import songscribe.model.StaffElement;
import songscribe.ui.component.LyricEditor;
import songscribe.ui.component.MainFrame;
import songscribe.util.UIUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class EditLyricActionTest extends UnitTest {

    private MockedStatic<MainFrame> mainFrameMock;
    private MockEnvHelper.MockEnv mockEnv;
    private EditLyricAction action;

    @BeforeEach
    void setUp() {
        mainFrameMock = mockStatic(MainFrame.class);
        mockEnv = MockEnvHelper.setupMockEnv(mainFrameMock);
        action = EditLyricAction.createAction();
    }

    @AfterEach
    void tearDown() {
        mainFrameMock.close();
    }

    // T27

    @Test
    void testActionPerformedOpensEditorForSelectedElement() {
        var mockElement = mock(StaffElement.class);
        var mockLine = mock(Line.class);
        when(mockElement.getLine()).thenReturn(mockLine);
        when(mockEnv.coordinator().getSingleSelectedElement()).thenReturn(mockElement);

        try (var lyricEditorMock = mockStatic(LyricEditor.class)) {
            action.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "edit-lyric"));
            var score = mockEnv.score();
            lyricEditorMock.verify(() -> LyricEditor.openOn(score, mockLine, mockElement));
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
    void testIsEditingTextInReturnsFalseForUnfocusedWindow() {
        // No mockStatic needed — isEditingTextIn takes a Window directly.
        // In a headless test environment the focused frame is null, so editing is never active.
        assertThat(UIUtils.isEditingTextIn(new JFrame())).isFalse();
    }
}
