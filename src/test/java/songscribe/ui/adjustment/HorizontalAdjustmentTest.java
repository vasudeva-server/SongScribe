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
import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.message.MessageCenter;
import songscribe.ui.adjustment.HorizontalAdjustment.AdjustRect;
import songscribe.ui.adjustment.HorizontalAdjustment.HorizontalAdjustmentType;
import songscribe.ui.component.ScoreView;

/**
 * Tests for {@link HorizontalAdjustment#startedDrag()} bounds computation and
 * {@link HorizontalAdjustment#drag()} snap-to-end behavior.
 *
 * <p>The {@code SnapToEnd} block verifies that the terminal-node guard in
 * {@link HorizontalAdjustment#drag()} correctly skips or applies the snap
 * adjustment to {@code endPoint.x}.
 *
 * <p>The {@code StartedDrag} block verifies the bounds calculation for all
 * relevant drag types.
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
    // Snap-to-end behavior in drag() — rows 21-23
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class SnapToEnd {

        private Song mockSong;
        private Line mockLine;
        private StaffElement mockNote;
        private ElementType mockType;

        @BeforeEach
        void setUpMocks() {
            mockSong = mock(Song.class);
            mockLine = mock(Line.class);
            mockNote = mock(StaffElement.class);
            mockType = mock(ElementType.class);

            when(scoreView.getSong()).thenReturn(mockSong);
            when(mockSong.getLine(anyInt())).thenReturn(mockLine);
            when(mockLine.getElement(anyInt())).thenReturn(mockNote);
            when(mockNote.getType()).thenReturn(mockType);
            when(mockSong.getLineWidthPx()).thenReturn(LINE_WIDTH_PX);
            // revalidateRects → getAdjustRect → getLineComponent → returns null → early return
            when(scoreView.getLineComponent(anyInt())).thenReturn(null);
        }

        /**
         * Installs a draggingRect with a SINGLE_NOTE handle at a known position and
         * sets endPoint.x to the supplied value.
         */
        private void setUpDragState(int rectX, int endX) {
            ha.draggingRect = ha.new AdjustRect(
                0, 0, HorizontalAdjustmentType.SINGLE_NOTE,
                new Rectangle(rectX, 0, HANDLE, HANDLE)
            );
            ha.adjustRects.add(ha.draggingRect);
            ha.endPoint.setLocation(endX, 0);
        }

        /**
         * Row 21: when the note is not interactable (auto-maintained terminal,
         * {@code FINAL_DOUBLE_BARLINE}), {@code drag()} must NOT adjust
         * {@code endPoint.x}, even when the note type has {@code snapToEnd=true} and
         * the cursor is within {@code HorizontalAdjustment.END_SNAP_LIMIT} of the line end.
         */
        @Test
        void testFinalDoubleBarlineTerminalSkipsSnap() {
            when(mockSong.isInteractable(any(), any())).thenReturn(false);
            when(mockType.snapToEnd()).thenReturn(true);

            // endPoint.x within HorizontalAdjustment.END_SNAP_LIMIT of lineWidth — would snap if interactable
            var endX = LINE_WIDTH_PX - (HorizontalAdjustment.END_SNAP_LIMIT - 1);
            setUpDragState(endX, endX);

            ha.drag();

            assertThat(ha.endPoint.x)
                .as("endPoint.x must not change when note is not interactable")
                .isEqualTo(endX);
        }

        /**
         * Row 22: same guard applies to {@code REPEAT_RIGHT} terminal — {@code drag()}
         * must not adjust {@code endPoint.x} even though {@code snapToEnd=true}.
         */
        @Test
        void testRepeatRightTerminalSkipsSnap() {
            when(mockSong.isInteractable(any(), any())).thenReturn(false);
            when(mockType.snapToEnd()).thenReturn(true);

            var endX = LINE_WIDTH_PX - (HorizontalAdjustment.END_SNAP_LIMIT - 1);
            setUpDragState(endX, endX);

            ha.drag();

            assertThat(ha.endPoint.x)
                .as("endPoint.x must not change when REPEAT_RIGHT terminal is not interactable")
                .isEqualTo(endX);
        }

        /**
         * Row 23: for an interactable note with {@code snapToEnd=true} (e.g., a barline
         * that is not the auto-maintained terminal) positioned within {@code HorizontalAdjustment.END_SNAP_LIMIT}
         * of the line end, {@code drag()} must adjust {@code endPoint.x} to
         * {@code lineWidth − note.contentWidthPx}.
         */
        @Test
        void testInteractableSnapToEndNoteSetsEndPointX() {
            var contentWidthPx = 12.0;
            when(mockSong.isInteractable(any(), any())).thenReturn(true);
            when(mockType.snapToEnd()).thenReturn(true);
            when(mockNote.getContentWidthPx()).thenReturn(contentWidthPx);

            // endPoint.x within HorizontalAdjustment.END_SNAP_LIMIT of lineWidth
            var endX = LINE_WIDTH_PX - (HorizontalAdjustment.END_SNAP_LIMIT - 1);
            setUpDragState(endX, endX);

            ha.drag();

            var expectedSnapX = (int) (LINE_WIDTH_PX - contentWidthPx);
            assertThat(ha.endPoint.x)
                .as("endPoint.x must be snapped to lineWidth - contentWidthPx")
                .isEqualTo(expectedSnapX);
        }
    }

    // -----------------------------------------------------------------------
    // startedDrag() — rows 9-12 (SINGLE_NOTE bounds)
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

        // -------------------------------------------------------------------
        // TO_END_OF_LINE bounds — row 17
        // -------------------------------------------------------------------

        @SuppressWarnings("PackageVisibleInnerClass")
        @Nested
        class ToEndOfLineBounds {

            private Song mockSong;
            private Line mockLine;
            private StaffElement prevNote;
            private StaffElement lastNote;

            @BeforeEach
            void setUpMocks() {
                mockSong = mock(Song.class);
                mockLine = mock(Line.class);
                when(scoreView.getSong()).thenReturn(mockSong);
                when(mockSong.getLine(anyInt())).thenReturn(mockLine);
                when(mockSong.getLineWidthPx()).thenReturn(LINE_WIDTH_PX);

                prevNote = mock(StaffElement.class);
                lastNote = mock(StaffElement.class);
            }

            /**
             * Row 17: for a TO_END_OF_LINE drag at xIndex=2 (effectiveElementCount=4):
             * <ul>
             *   <li>left bound = prevNote(xIndex-1).x + rect.width</li>
             *   <li>right bound = (rect.x − rect.width + lineWidth) − lastNote.x</li>
             * </ul>
             */
            @Test
            void testToEndOfLineBoundsExact() {
                when(mockLine.effectiveElementCount()).thenReturn(4);
                when(mockLine.elementCount()).thenReturn(4);

                var prevNoteX = 150;
                var lastNoteX = 600;
                var rectX = 250;
                when(prevNote.getXOffsetPx()).thenReturn(prevNoteX);
                when(lastNote.getXOffsetPx()).thenReturn(lastNoteX);
                // xIndex-1 = 1
                when(mockLine.getElement(1)).thenReturn(prevNote);
                // last element = elementCount()-1 = 3
                when(mockLine.getElement(3)).thenReturn(lastNote);

                ha.adjustRects.add(
                    ha.new AdjustRect(0, 2, HorizontalAdjustmentType.TO_END_OF_LINE,
                        new Rectangle(rectX, 0, HANDLE, HANDLE))
                );
                ha.startedDrag = true;
                ha.startPoint = new Point(rectX + 4, 4);
                ha.startedDrag();

                assertThat(ha.topLeftDragBounds.x)
                    .as("left bound = prevNote.x + rect.width")
                    .isEqualTo(prevNoteX + HANDLE);
                assertThat(ha.bottomRightDragBounds.x)
                    .as("right bound = (rect.x - rect.width + lineWidth) - lastNote.x")
                    .isEqualTo((rectX - HANDLE + LINE_WIDTH_PX) - lastNoteX);
            }
        }

        // -------------------------------------------------------------------
        // STRETCH_NOTE_SPACING — row 18
        // -------------------------------------------------------------------

        @SuppressWarnings("PackageVisibleInnerClass")
        @Nested
        class StretchNoteSpacingBounds {

            private Song mockSong;
            private Line mockLine;

            @BeforeEach
            void setUpMocks() {
                mockSong = mock(Song.class);
                mockLine = mock(Line.class);
                when(scoreView.getSong()).thenReturn(mockSong);
                when(mockSong.getLine(anyInt())).thenReturn(mockLine);
                when(mockSong.getLineWidthPx()).thenReturn(LINE_WIDTH_PX);
            }

            /**
             * Row 18: after {@code startedDrag()} for STRETCH_NOTE_SPACING,
             * {@code stretchHelper} is populated with each note's xOffsetPx, and the
             * array is reallocated when the existing one is too small.
             */
            @Test
            void testStretchHelperPopulatedWithNoteXOffsets() {
                var count = 3;
                when(mockLine.effectiveElementCount()).thenReturn(count);

                var noteXs = new int[]{100, 250, 400};
                for (var i = 0; i < count; i++) {
                    var note = mock(StaffElement.class);
                    when(note.getXOffsetPx()).thenReturn(noteXs[i]);
                    when(mockLine.getElement(i)).thenReturn(note);
                }

                var rectX = 400;
                ha.adjustRects.add(
                    ha.new AdjustRect(0, count - 1, HorizontalAdjustmentType.STRETCH_NOTE_SPACING,
                        new Rectangle(rectX, 0, HANDLE, HANDLE))
                );
                ha.startedDrag = true;
                ha.startPoint = new Point(rectX + 4, 4);
                ha.startedDrag();

                assertThat(ha.stretchHelper)
                    .as("stretchHelper must not be null after startedDrag")
                    .isNotNull();
                var helper = ha.stretchHelper;
                if (helper == null) {
                    return;
                }
                assertThat(helper.length)
                    .as("stretchHelper length >= effectiveElementCount")
                    .isGreaterThanOrEqualTo(count);
                for (var i = 0; i < count; i++) {
                    assertThat(helper[i])
                        .as("stretchHelper[%d] must equal note xOffsetPx %d", i, noteXs[i])
                        .isEqualTo((float) noteXs[i]);
                }
            }

            /**
             * Row 18 (reallocation): when a pre-existing {@code stretchHelper} array is
             * smaller than {@code effectiveElementCount}, it is reallocated to fit.
             */
            @Test
            void testStretchHelperReallocatedWhenTooSmall() {
                // Pre-install a too-small array
                ha.stretchHelper = new float[1];

                var count = 3;
                when(mockLine.effectiveElementCount()).thenReturn(count);

                for (var i = 0; i < count; i++) {
                    var note = mock(StaffElement.class);
                    when(note.getXOffsetPx()).thenReturn((i + 1) * 100);
                    when(mockLine.getElement(i)).thenReturn(note);
                }

                var rectX = 300;
                ha.adjustRects.add(
                    ha.new AdjustRect(0, count - 1, HorizontalAdjustmentType.STRETCH_NOTE_SPACING,
                        new Rectangle(rectX, 0, HANDLE, HANDLE))
                );
                ha.startedDrag = true;
                ha.startPoint = new Point(rectX + 4, 4);
                ha.startedDrag();

                assertThat(ha.stretchHelper)
                    .as("stretchHelper must be reallocated when too small")
                    .isNotNull();
                var helper = ha.stretchHelper;
                if (helper == null) {
                    return;
                }
                assertThat(helper.length)
                    .as("reallocated stretchHelper must accommodate effectiveElementCount")
                    .isGreaterThanOrEqualTo(count);
            }
        }

    }
}
