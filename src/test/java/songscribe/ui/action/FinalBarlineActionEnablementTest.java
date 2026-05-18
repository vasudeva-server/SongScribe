/*
 * SongScribe song notation program
 * Copyright (C) Sri Chinmoy Centres International
 *
 * This file is part of SongScribe.
 *
 * SongScribe is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * SongScribe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package songscribe.ui.action;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import module java.desktop;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import songscribe.UnitTest;
import songscribe.model.Song;
import songscribe.model.ElementType;
import songscribe.ui.OptionDialogs;
import songscribe.ui.component.MainFrame;

class FinalBarlineActionEnablementTest extends UnitTest {

    private MockedStatic<MainFrame> mainFrameMock;
    private Song song;

    @BeforeEach
    void setUp() {
        mainFrameMock = mockStatic(MainFrame.class);
        var env = MockEnvHelper.setupMockEnv(mainFrameMock);

        // PlaybackController's static initializer creates UIAction instances with
        // keyboard shortcuts that call mainFrame.getRootPane(), so we need a JRootPane mock.
        var mockRootPane = mock(JRootPane.class);
        when(mockRootPane.getInputMap(anyInt())).thenReturn(new InputMap());
        when(mockRootPane.getActionMap()).thenReturn(new ActionMap());
        when(env.frame().getRootPane()).thenReturn(mockRootPane);

        song = new Song();
        when(env.score().isInitialized()).thenReturn(true);
        when(env.score().getSong()).thenReturn(song);
    }

    @AfterEach
    void tearDown() {
        mainFrameMock.close();
    }

    @Test
    void testActionSelectedWhenTerminalIsFinalDoubleBarline() {
        var action = FinalTerminalAction.createFinalDoubleBarline();
        action.updateEnabledState();

        assertThat(action.isSelected()).isTrue();
    }

    @Test
    void testActionNotSelectedWhenTerminalIsRightRepeat() {
        song.replaceTerminal(ElementType.REPEAT_RIGHT);

        var action = FinalTerminalAction.createFinalDoubleBarline();
        action.updateEnabledState();

        assertThat(action.isSelected()).isFalse();
    }

    @Test
    void testActionSelectedAgainAfterReplaceBackToFinalBarline() {
        song.replaceTerminal(ElementType.REPEAT_RIGHT);
        song.replaceTerminal(ElementType.FINAL_DOUBLE_BARLINE);

        var action = FinalTerminalAction.createFinalDoubleBarline();
        action.updateEnabledState();

        assertThat(action.isSelected()).isTrue();
    }

    @Test
    void testActionReplacesTerminalDirectlyWithoutConfirm() {
        song.replaceTerminal(ElementType.REPEAT_RIGHT);

        try (var optionDialogsMock = mockStatic(OptionDialogs.class)) {
            var action = FinalTerminalAction.createFinalDoubleBarline();
            action.actionPerformed(new ActionEvent(action, ActionEvent.ACTION_PERFORMED, "final-double-barline"));

            optionDialogsMock.verifyNoInteractions();
        }

        assertThat(song.currentTerminalType()).isEqualTo(ElementType.FINAL_DOUBLE_BARLINE);
    }
}
