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
import static org.assertj.core.api.Assertions.within;
import static songscribe.dom.StaffElementFactory.crotchet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Line;
import songscribe.engraving.SMuFLConstants;
import songscribe.engraving.Staff;
import songscribe.layout.HorizontalSpacingCalculator;
import songscribe.layout.LayoutResult;

/**
 * Unit tests for {@link InsertionMarkerOverlay#getInkBoundsSs()} — T13: the marker's height is
 * identical on every line (bounded by the compile-time staff-position constants) and its x comes
 * from {@link LayoutResult#calculateInsertionXSs} plus half a notehead width, widened by half the
 * marker's own thickness.
 */
class InsertionMarkerOverlayTest extends UnitTest {

    private static final double TOLERANCE = 1e-9;
    private static final int TARGET_INDEX = 0;

    private FakeOverlayHost host;
    private InsertionMarkerOverlay overlay;

    @BeforeEach
    void setUp() {
        host = new FakeOverlayHost();
        overlay = new InsertionMarkerOverlay(host);
    }

    /** Builds a real, empty, host-parented {@link LineComponent} ready for the marker to target. */
    private LineComponent parentedLine() {
        var lc = new LineComponent();
        host.add(lc);
        lc.setLine(detachedLine(), 0);
        lc.layoutResult = LayoutResult.builder().build();
        lc.layoutDirty = false;
        return lc;
    }

    /**
     * The expected x-center: for an empty line, {@code calculateInsertionXSs} takes the
     * empty-line branch and ignores both the index and the preview element's own extents, so this
     * matches the marker's computation regardless of which empty line or index is under test.
     */
    private static double expectedCenterXSs(Line domLine) {
        var baseXSs = HorizontalSpacingCalculator.calculateFirstElementXSs(domLine);
        return baseXSs + SMuFLConstants.NOTE_HEAD_WIDTH_SS / 2;
    }

    @Nested
    class NullTargetOrIndex {

        @Test
        void testNullLineProducesNullBounds() {
            overlay.setTarget(null, TARGET_INDEX);

            assertThat(overlay.getInkBoundsSs())
                .as("no target line -> no ink")
                .isNull();
        }

        @Test
        void testNegativeIndexProducesNullBounds() {
            var lc = parentedLine();
            overlay.setTarget(lc, -1);

            assertThat(overlay.getInkBoundsSs())
                .as("negative target index -> no ink")
                .isNull();
        }
    }

    @Nested
    class Geometry {

        @Test
        void testXIsInsertionXPlusHalfNoteheadWidthWidenedByHalfThickness() {
            var lc = parentedLine();
            overlay.setTarget(lc, TARGET_INDEX);

            var bounds = overlay.getInkBoundsSs();

            assertThat(bounds).as("expected non-null ink bounds").isNotNull();

            var domLine = lc.getLine();

            assertThat(domLine).as("test setup did not attach a line").isNotNull();

            var expectedCenterXSs = expectedCenterXSs(domLine);
            var expectedMinXSs = expectedCenterXSs - InsertionMarkerOverlay.INSERTION_POINT_THICKNESS_SS / 2;

            assertThat(bounds.getMinX())
                .as("left edge = insertion x + half notehead width - half thickness")
                .isCloseTo(expectedMinXSs, within(TOLERANCE));
            assertThat(bounds.getWidth())
                .as("width is exactly the marker's thickness constant")
                .isCloseTo(InsertionMarkerOverlay.INSERTION_POINT_THICKNESS_SS, within(TOLERANCE));
        }

        @Test
        void testHeightSpansMinToMaxStaffPosition() {
            var lc = parentedLine();
            overlay.setTarget(lc, TARGET_INDEX);

            var bounds = overlay.getInkBoundsSs();

            assertThat(bounds).as("expected non-null ink bounds").isNotNull();

            // song is null on this LineComponent, so getMiddleLineYSs() stays at its 0.0 default.
            var expectedTopYSs = Staff.spToSs(Staff.MIN_STAFF_POSITION_SP);
            var expectedBottomYSs = Staff.spToSs(Staff.MAX_STAFF_POSITION_SP);

            assertThat(bounds.getMinY())
                .as("top = middleLineYSs + spToSs(MIN_STAFF_POSITION_SP)")
                .isCloseTo(expectedTopYSs, within(TOLERANCE));
            assertThat(bounds.getHeight())
                .as("height spans MIN_STAFF_POSITION_SP to MAX_STAFF_POSITION_SP")
                .isCloseTo(expectedBottomYSs - expectedTopYSs, within(TOLERANCE));
        }

        /**
         * Height is a function of the compile-time staff-position constants alone, so it must be
         * identical across two lines with entirely different content.
         */
        @Test
        void testHeightIsIdenticalAcrossDifferentLines() {
            var firstLine = parentedLine();
            overlay.setTarget(firstLine, TARGET_INDEX);
            var firstBounds = overlay.getInkBoundsSs();

            var secondLine = parentedLine();
            var secondDomLine = secondLine.getLine();

            assertThat(secondDomLine).as("test setup did not attach a line").isNotNull();

            secondDomLine.addElement(crotchet());
            overlay.setTarget(secondLine, TARGET_INDEX + 1);
            var secondBounds = overlay.getInkBoundsSs();

            if (firstBounds == null || secondBounds == null) {
                throw new AssertionError("expected non-null ink bounds on both lines");
            }

            assertThat(secondBounds.getHeight())
                .as("marker height does not depend on line content")
                .isCloseTo(firstBounds.getHeight(), within(TOLERANCE));
        }
    }
}
