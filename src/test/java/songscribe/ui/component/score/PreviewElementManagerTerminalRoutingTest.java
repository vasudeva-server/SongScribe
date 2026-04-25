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

package songscribe.ui.component.score;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import songscribe.UnitTest;
import songscribe.message.MessageCenter;
import songscribe.music.Song;
import songscribe.music.ElementType;
import songscribe.music.Line;
import songscribe.ui.Control;
import songscribe.ui.Mode;
import songscribe.ui.component.Score;
import songscribe.ui.edit.EditModeManager;
import songscribe.ui.playback.PlaybackController;

/**
 * Tests Phase-3 routing: direct clicks on the auto-maintained terminal are routed
 * through {@link Song#replaceTerminal} instead of the normal insertion path,
 * and that {@code isPositionBlockedByTerminal} correctly lifts its block when the
 * active preview element can legally replace the terminal.
 */
class PreviewElementManagerTerminalRoutingTest extends UnitTest {

    private MockedStatic<MessageCenter> messageCenterMock;
    private MockedStatic<EditModeManager> editModeMgrMock;
    private MockedStatic<Score> scoreMock;
    private MockedStatic<PlaybackController> playbackMock;

    private LineComponent lc;
    private EditModeManager editModeManager;

    private Song song;
    private Line line;

    @BeforeEach
    void setUp() {
        // Build real model objects before mocking MessageCenter so their constructors
        // see the real bus.
        song = new Song();
        line = song.getLine(0);

        messageCenterMock = mockStatic(MessageCenter.class);
        editModeMgrMock = mockStatic(EditModeManager.class);
        scoreMock = mockStatic(Score.class);
        playbackMock = mockStatic(PlaybackController.class);

        lc = mock(LineComponent.class);
        var score = mock(Score.class);
        editModeManager = mock(EditModeManager.class);

        when(lc.isEditMode()).thenReturn(true);
        when(lc.getScore()).thenReturn(score);
        when(lc.getLine()).thenReturn(line);
        when(score.getControl()).thenReturn(Control.MOUSE);
        when(score.getMode()).thenReturn(Mode.EDIT);

        editModeMgrMock.when(EditModeManager::getInstance).thenReturn(editModeManager);
        when(editModeManager.hasPreviewElement()).thenReturn(true);
        when(editModeManager.elementWasModified(any(Line.class), anyInt())).thenReturn(false);

        playbackMock.when(PlaybackController::isPlaying).thenReturn(false);

        PreviewElementManager.currentPreviewLine = lc;
        PreviewElementManager.currentStaffPosition = 0;
    }

    @AfterEach
    void tearDown() {
        PreviewElementManager.currentPreviewLine = null;
        PreviewElementManager.currentXIndex = -1;
        PreviewElementManager.xPosSsMatchesElement = false;

        playbackMock.close();
        scoreMock.close();
        editModeMgrMock.close();
        messageCenterMock.close();
    }

    private int terminalIndex() {
        return line.elementCount() - 1;
    }

    // -----------------------------------------------------------------------
    // Terminal replacement routing
    // -----------------------------------------------------------------------

    @Nested
    class TerminalReplacement {

        /**
         * Direct click on the FINAL_DOUBLE_BARLINE terminal with REPEAT_RIGHT preview
         * must flip the terminal to REPEAT_RIGHT via replaceTerminal — not addElement
         * or insertElement. The element count must not change (replacement, not insertion).
         */
        @Test
        void testDirectClickReplacesTerminalWithRepeatRight() {
            when(editModeManager.getPreviewElement())
                .thenReturn(ElementType.REPEAT_RIGHT.newInstance());

            var termIdx = terminalIndex();
            PreviewElementManager.xPosSsMatchesElement = true;
            PreviewElementManager.currentXIndex = termIdx;
            var countBefore = line.elementCount();

            PreviewElementManager.handleClick(lc);

            assertThat(line.getElement(termIdx).getType())
                .as("terminal type after direct click with REPEAT_RIGHT preview")
                .isEqualTo(ElementType.REPEAT_RIGHT);
            assertThat(line.elementCount())
                .as("element count must not change — replacement, not insertion")
                .isEqualTo(countBefore);
        }

        /**
         * Direct click on the terminal with a non-terminal preview type (CROTCHET) must
         * be blocked. The terminal type and element count must remain unchanged.
         */
        @Test
        void testDirectClickWithNonTerminalTypeIsBlocked() {
            when(editModeManager.getPreviewElement())
                .thenReturn(ElementType.CROTCHET.newInstance());

            var termIdx = terminalIndex();
            PreviewElementManager.xPosSsMatchesElement = true;
            PreviewElementManager.currentXIndex = termIdx;
            var terminalTypeBefore = line.getElement(termIdx).getType();
            var countBefore = line.elementCount();

            PreviewElementManager.handleClick(lc);

            assertThat(line.getElement(termIdx).getType())
                .as("terminal type must not change when preview cannot replace it")
                .isEqualTo(terminalTypeBefore);
            assertThat(line.elementCount())
                .as("element count must not change")
                .isEqualTo(countBefore);
        }
    }

    // -----------------------------------------------------------------------
    // isPositionBlockedByTerminal: past-terminal append slot
    // -----------------------------------------------------------------------

    @Nested
    class PastTerminalAlwaysBlocked {

        /**
         * The append slot immediately past the terminal (xIndex == elementCount) is always
         * blocked, even when the preview is REPEAT_RIGHT and could otherwise replace the
         * terminal. Nothing may be appended after the terminal.
         */
        @Test
        void testAlwaysBlockedAtAppendSlotPastTerminal() {
            when(editModeManager.getPreviewElement())
                .thenReturn(ElementType.REPEAT_RIGHT.newInstance());

            // xIndex one past the last element
            PreviewElementManager.xPosSsMatchesElement = false;
            PreviewElementManager.currentXIndex = line.elementCount();
            var termIdx = terminalIndex();
            var terminalTypeBefore = line.getElement(termIdx).getType();
            var countBefore = line.elementCount();

            PreviewElementManager.handleClick(lc);

            assertThat(line.getElement(termIdx).getType())
                .as("terminal type must not change at past-terminal append slot")
                .isEqualTo(terminalTypeBefore);
            assertThat(line.elementCount())
                .as("element count must not change")
                .isEqualTo(countBefore);
        }
    }
}
