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

import module java.desktop;

import java.awt.Point;
import java.awt.Rectangle;

import static org.assertj.core.api.Assertions.assertThat;
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
import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.message.MessageCenter;
import songscribe.ui.adjustment.HorizontalAdjustment.HorizontalAdjustmentType;
import songscribe.ui.component.ScoreView;

/**
 * Tests for {@link HorizontalAdjustment#startedDrag()} bounds computation and
 * drag snap-to-end skip.
 *
 * <p>The {@code SnapToEndSkipped} block verifies that the terminal-node guard in
 * {@link HorizontalAdjustment#drag()} correctly reflects model properties on
 * both valid terminal types.
 *
 * <p>The {@code StartedDrag} block verifies the bounds calculation for the
 * {@code SINGLE_NOTE} drag type: the three edge cases are
 * <ul>
 *   <li>No {@code AdjustRect} contains the start point → {@code startedDrag} cleared</li>
 *   <li>Middle note → left = prevNote.x + handleWidth; right = nextNote.x − handleWidth</li>
 *   <li>First note (xIndex=0) → left = {@code FIRST_NOTE_LEFT_MARGIN_PX} + handleWidth</li>
 *   <li>Last note → right = {@code lineWidthPx}</li>
 * </ul>
 */
class HorizontalAdjustmentTest extends UnitTest {

    /**
     * Line-width stub value returned by {@link Song#getLineWidthPx()} in mocked setups.
     * 800 px is comfortably beyond any note x-offset used in these tests.
     */
    private static final int LINE_WIDTH_PX = 800;

    /**
     * Hard-coded left-margin for the first note in a line ({@code xIndex == 0}).
     * Mirrors the literal {@code 20} in {@link HorizontalAdjustment#startedDrag()}.
     */
    private static final int FIRST_NOTE_LEFT_MARGIN_PX = 20;

    private static final int HANDLE = HorizontalAdjustment.HANDLE_SIZE_PX;

    private MockedStatic<MessageCenter> messageCenterMock;
    private Song song;
    private Line line;
    private ScoreView scoreView;
    private HorizontalAdjustment ha;

    @BeforeEach
    void setUp() {
        song = new Song();
        line = song.getLine(0);
        messageCenterMock = mockStatic(MessageCenter.class);
        scoreView = mock(ScoreView.class);
        ha = new HorizontalAdjustment(scoreView);
    }

    @AfterEach
    void tearDown() {
        messageCenterMock.close();
    }

    // -----------------------------------------------------------------------
    // Snap-to-end skipped for both terminal types (rows 21-22)
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
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

    // -----------------------------------------------------------------------
    // startedDrag() — rows 9-12
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class StartedDrag {

        /**
         * Row 9: when no {@code AdjustRect} contains the start point, {@code startedDrag()}
         * must clear the {@code startedDrag} flag so the drag is aborted.
         *
         * <p>The {@code adjustRects} list is populated with one rect, but the press
         * point is placed well outside it to confirm the miss-path is correctly handled.
         */
        @Test
        void testSetsStartedDragFalseWhenNoRectContainsStartPoint() {
            // One rect present at (100, 0, 8, 8); press is far outside it.
            ha.adjustRects.add(
                ha.new AdjustRect(0, 0, HorizontalAdjustmentType.SINGLE_NOTE,
                    new Rectangle(100, 0, HANDLE, HANDLE))
            );

            ha.startedDrag = true;
            ha.startPoint = new Point(500, 500);
            ha.startedDrag();

            assertThat(ha.startedDrag)
                .as("startedDrag must be cleared when no AdjustRect contains startPoint")
                .isFalse();
        }

        // -------------------------------------------------------------------
        // SINGLE_NOTE bounds — rows 10-12
        // -------------------------------------------------------------------

        @SuppressWarnings("PackageVisibleInnerClass")
        @Nested
        class SingleNoteBounds {

            private Song mockSong;
            private Line mockLine;
            private StaffElement prevNote;
            private StaffElement nextNote;

            @BeforeEach
            void setUpMockSong() {
                mockSong = mock(Song.class);
                mockLine = mock(Line.class);
                when(scoreView.getSong()).thenReturn(mockSong);
                when(mockSong.getLine(anyInt())).thenReturn(mockLine);
                when(mockLine.effectiveElementCount()).thenReturn(3);
                when(mockSong.getLineWidthPx()).thenReturn(LINE_WIDTH_PX);

                prevNote = mock(StaffElement.class);
                nextNote = mock(StaffElement.class);
            }

            /**
             * Row 10: for a note that has both a predecessor and a successor, the left
             * bound is {@code prevNote.x + handleWidth} and the right bound is
             * {@code nextNote.x − handleWidth}.
             */
            @Test
            void testMiddleNoteBoundsUsePrevAndNextNoteX() {
                // note at xIndex=1, prev at xIndex=0, next at xIndex=2
                when(prevNote.getXOffsetPx()).thenReturn(100);
                when(nextNote.getXOffsetPx()).thenReturn(300);
                when(mockLine.getElement(0)).thenReturn(prevNote);
                when(mockLine.getElement(2)).thenReturn(nextNote);

                var rectX = 201;
                ha.adjustRects.add(
                    ha.new AdjustRect(0, 1, HorizontalAdjustmentType.SINGLE_NOTE,
                        new Rectangle(rectX, 0, HANDLE, HANDLE))
                );
                ha.startedDrag = true;
                ha.startPoint = new Point(rectX + 4, 4);
                ha.startedDrag();

                assertThat(ha.topLeftDragBounds.x)
                    .as("left bound = prevNote.x + handleWidth")
                    .isEqualTo(100 + HANDLE);
                assertThat(ha.bottomRightDragBounds.x)
                    .as("right bound = nextNote.x - handleWidth")
                    .isEqualTo(300 - HANDLE);
            }

            /**
             * Row 11: for the first note (xIndex=0), the left bound uses the fixed
             * margin {@code FIRST_NOTE_LEFT_MARGIN_PX} rather than a predecessor x.
             * Right bound uses the next note as normal.
             */
            @Test
            void testFirstNoteBoundsUseFixedLeftMargin() {
                // note at xIndex=0, no predecessor; next at xIndex=1
                when(nextNote.getXOffsetPx()).thenReturn(200);
                when(mockLine.getElement(1)).thenReturn(nextNote);

                var rectX = 101;
                ha.adjustRects.add(
                    ha.new AdjustRect(0, 0, HorizontalAdjustmentType.SINGLE_NOTE,
                        new Rectangle(rectX, 0, HANDLE, HANDLE))
                );
                ha.startedDrag = true;
                ha.startPoint = new Point(rectX + 4, 4);
                ha.startedDrag();

                assertThat(ha.topLeftDragBounds.x)
                    .as("left bound = FIRST_NOTE_LEFT_MARGIN_PX + handleWidth when xIndex=0")
                    .isEqualTo(FIRST_NOTE_LEFT_MARGIN_PX + HANDLE);
                assertThat(ha.bottomRightDragBounds.x)
                    .as("right bound = nextNote.x - handleWidth")
                    .isEqualTo(200 - HANDLE);
            }

            /**
             * Row 12: for the last note (xIndex = effectiveElementCount−1), the right
             * bound equals {@code lineWidthPx} rather than a successor x-offset.
             * Left bound uses the predecessor as normal.
             */
            @Test
            void testLastNoteRightBoundIsLineWidth() {
                // note at xIndex=2 (last; effectiveElementCount=3), prev at xIndex=1
                when(prevNote.getXOffsetPx()).thenReturn(200);
                when(mockLine.getElement(1)).thenReturn(prevNote);

                var rectX = 301;
                ha.adjustRects.add(
                    ha.new AdjustRect(0, 2, HorizontalAdjustmentType.SINGLE_NOTE,
                        new Rectangle(rectX, 0, HANDLE, HANDLE))
                );
                ha.startedDrag = true;
                ha.startPoint = new Point(rectX + 4, 4);
                ha.startedDrag();

                assertThat(ha.topLeftDragBounds.x)
                    .as("left bound = prevNote.x + handleWidth")
                    .isEqualTo(200 + HANDLE);
                assertThat(ha.bottomRightDragBounds.x)
                    .as("right bound = lineWidthPx for last note")
                    .isEqualTo(LINE_WIDTH_PX);
            }
        }
    }
}
