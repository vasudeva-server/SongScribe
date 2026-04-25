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

package songscribe.ui.adjustment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

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

/**
 * Tests that the drag snap-to-end skip in {@link HorizontalAdjustment#drag()} is
 * correctly gated on {@code Song.isInteractable}.
 *
 * <p>The condition in {@code drag()} is:
 * <pre>
 *   if (song.isInteractable(note, line) &amp;&amp; note.getType().snapToEnd() &amp;&amp; ...) {
 *       endPoint.x = ...;  // snap
 *   }
 * </pre>
 * Both valid terminal types ({@code FINAL_DOUBLE_BARLINE} and {@code REPEAT_RIGHT})
 * must satisfy {@code !isInteractable &amp;&amp; snapToEnd}, ensuring that snap is always
 * skipped for the terminal regardless of which type currently occupies the slot.
 */
class HorizontalAdjustmentTest extends UnitTest {

    private MockedStatic<MessageCenter> messageCenterMock;
    private Song song;
    private Line line;

    @BeforeEach
    void setUp() {
        song = new Song();
        line = song.getLine(0);
        messageCenterMock = mockStatic(MessageCenter.class);
    }

    @AfterEach
    void tearDown() {
        messageCenterMock.close();
    }

    // -----------------------------------------------------------------------
    // Snap-to-end skipped for both terminal types
    // -----------------------------------------------------------------------

    @Nested
    class SnapToEndSkipped {

        /**
         * The default {@code FINAL_DOUBLE_BARLINE} terminal satisfies both conditions
         * that make {@code drag()} skip the snap:
         * {@code !isInteractable} (position owned by layout) and {@code snapToEnd = true}.
         */
        @Test
        void testFinalDoubleBarlineTerminalSkipsSnap() {
            var termIdx = line.elementCount() - 1;
            var terminal = line.getElement(termIdx);

            assertThat(terminal.getType())
                .as("default terminal type")
                .isEqualTo(ElementType.FINAL_DOUBLE_BARLINE);
            assertThat(song.isInteractable(terminal, line))
                .as("terminal must not be interactable — snap condition is false")
                .isFalse();
            assertThat(terminal.getType().snapToEnd())
                .as("FINAL_DOUBLE_BARLINE snaps to end — condition would fire if interactable")
                .isTrue();
        }

        /**
         * Parallel case: after swapping to a {@code REPEAT_RIGHT} terminal, the same
         * two conditions still hold. The snap-to-end skip applies to both terminal types.
         */
        @Test
        void testRepeatRightTerminalSkipsSnap() {
            song.replaceTerminal(ElementType.REPEAT_RIGHT);

            var termIdx = line.elementCount() - 1;
            var terminal = line.getElement(termIdx);

            assertThat(terminal.getType())
                .as("terminal type after replaceTerminal")
                .isEqualTo(ElementType.REPEAT_RIGHT);
            assertThat(song.isInteractable(terminal, line))
                .as("REPEAT_RIGHT terminal must not be interactable — snap condition is false")
                .isFalse();
            assertThat(terminal.getType().snapToEnd())
                .as("REPEAT_RIGHT snaps to end — condition would fire if interactable")
                .isTrue();
        }
    }
}
