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

package songscribe.dom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;

/**
 * Tests for {@link Trill} — getContentWidth/HeightSs, getContentWidth/HeightPx,
 * and getSpanWidthSs.
 */
class TrillTest extends UnitTest {

    // Floating-point tolerance for comparisons.
    private static final double DOUBLE_EPSILON = 1e-9;

    // Non-default pixels-per-staff-space chosen to be distinct from the production
    // default (8.0) so an accidental use of the default surfaces in the right test.
    private static final double TEST_PPS = 10.0;

    private static Trill createMultiNoteTrill(StaffElement anchor, StaffElement end) {
        return new Trill(anchor, end);
    }

    // -------------------------------------------------------------------------
    // Row 16 — getContentWidthSs / getContentHeightSs match the SMuFL bbox
    //          for ORNAMENT_TRILL
    // -------------------------------------------------------------------------

    @Test
    void testGetContentHeightSsEqualsOramentTrillBboxHeight() {
        var anchor = new StaffElement(ElementType.CROTCHET);
        var trill = new Trill(anchor);
        var bbox = SMuFLMetadata.requireBBox(SMuFLGlyph.ORNAMENT_TRILL);

        assertThat(trill.getContentHeightSs())
            .isCloseTo(bbox.height(), within(DOUBLE_EPSILON));
    }

    @Test
    void testGetContentWidthSsEqualsOrnamentTrillBboxWidth() {
        var anchor = new StaffElement(ElementType.CROTCHET);
        var trill = new Trill(anchor);
        var bbox = SMuFLMetadata.requireBBox(SMuFLGlyph.ORNAMENT_TRILL);

        assertThat(trill.getContentWidthSs())
            .isCloseTo(bbox.width(), within(DOUBLE_EPSILON));
    }

    // -------------------------------------------------------------------------
    // Row 15 — getSpanWidthSs(): max(glyphWidth, endX−anchorX+glyphWidth)
    //          Both branches: clamp (span < glyph width) and geometry (span >= glyph width)
    // -------------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class GetSpanWidthSs {

        /**
         * When endX == anchorX the geometry term equals glyphWidth, so the two sides
         * of max() are equal. Use a negative span to force geometry strictly below
         * glyphWidth, confirming the clamp branch.
         */
        @Test
        void testGetSpanWidthSsClampedToGlyphWidthWhenGeometryIsTooNarrow() {
            var anchor = new StaffElement(ElementType.CROTCHET);
            var end = new StaffElement(ElementType.CROTCHET);
            var trill = createMultiNoteTrill(anchor, end);
            var bbox = SMuFLMetadata.requireBBox(SMuFLGlyph.ORNAMENT_TRILL);
            double glyphWidthSs = bbox.width();

            // anchorX == endX => geometry = glyphWidth (clamp and geometry are equal).
            // Use anchorX > endX to strictly exercise the clamp branch.
            double anchorXSs = 1.0;
            double endXSs = 0.0;
            double geometryWidthSs = endXSs - anchorXSs + glyphWidthSs;

            assertThat(geometryWidthSs)
                .as("precondition: geometry width must be less than glyphWidth for clamp branch")
                .isLessThan(glyphWidthSs);

            assertThat(trill.getSpanWidthSs(anchorXSs, endXSs))
                .isCloseTo(glyphWidthSs, within(DOUBLE_EPSILON));
        }

        /**
         * When endX - anchorX + glyphWidth exceeds glyphWidth (i.e. endX > anchorX),
         * the span must equal the geometry term.
         */
        @Test
        void testGetSpanWidthSsReturnsGeometryWhenLargerThanGlyphWidth() {
            var anchor = new StaffElement(ElementType.CROTCHET);
            var end = new StaffElement(ElementType.CROTCHET);
            var trill = createMultiNoteTrill(anchor, end);
            var bbox = SMuFLMetadata.requireBBox(SMuFLGlyph.ORNAMENT_TRILL);
            double glyphWidthSs = bbox.width();

            double anchorXSs = 0.0;
            double endXSs = 3.0;
            double expectedWidthSs = endXSs - anchorXSs + glyphWidthSs;

            assertThat(expectedWidthSs)
                .as("precondition: geometry width must exceed glyphWidth for geometry branch")
                .isGreaterThan(glyphWidthSs);

            assertThat(trill.getSpanWidthSs(anchorXSs, endXSs))
                .isCloseTo(expectedWidthSs, within(DOUBLE_EPSILON));
        }
    }

    // -------------------------------------------------------------------------
    // Row 17 — getContentWidthPx / getContentHeightPx delegate to ssToPx
    // -------------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class GetContentPx {

        @BeforeEach
        void setUp() {
            ScaleContext.setPixelsPerStaffSpace(TEST_PPS);
        }

        @AfterEach
        void tearDown() {
            ScaleContext.setPixelsPerStaffSpace(ScaleContext.DEFAULT_PIXELS_PER_STAFF_SPACE);
        }

        @Test
        void testGetContentHeightPxEqualsGlyphHeightTimesPps() {
            var anchor = new StaffElement(ElementType.CROTCHET);
            var trill = new Trill(anchor);
            var bbox = SMuFLMetadata.requireBBox(SMuFLGlyph.ORNAMENT_TRILL);
            double expectedPx = bbox.height() * TEST_PPS;

            assertThat(trill.getContentHeightPx())
                .isCloseTo(expectedPx, within(DOUBLE_EPSILON));
        }

        @Test
        void testGetContentWidthPxEqualsGlyphWidthTimesPps() {
            var anchor = new StaffElement(ElementType.CROTCHET);
            var trill = new Trill(anchor);
            var bbox = SMuFLMetadata.requireBBox(SMuFLGlyph.ORNAMENT_TRILL);
            double expectedPx = bbox.width() * TEST_PPS;

            assertThat(trill.getContentWidthPx())
                .isCloseTo(expectedPx, within(DOUBLE_EPSILON));
        }
    }
}
