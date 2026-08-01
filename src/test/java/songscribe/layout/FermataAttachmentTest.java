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

package songscribe.layout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.ElementType;
import songscribe.dom.FermataAttachment;
import songscribe.dom.ScaleContext;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;

class FermataAttachmentTest extends UnitTest {

    // Non-default scale — distinct from ScaleContext.DEFAULT_PIXELS_PER_STAFF_SPACE (8.0)
    // so a forgotten tearDown surfaces immediately rather than silently passing.
    private static final double TEST_PPS = 12.5;

    // Tolerance for floating-point comparisons.
    private static final double DOUBLE_EPSILON = 1e-9;

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class ContentDimensionsPx {

        @BeforeEach
        void setUp() {
            ScaleContext.setPixelsPerStaffSpace(TEST_PPS);
        }

        @AfterEach
        void tearDown() {
            ScaleContext.setPixelsPerStaffSpace(ScaleContext.DEFAULT_PIXELS_PER_STAFF_SPACE);
        }

        @Test
        void testGetContentHeightPxEqualsHeightSsTimesScale() {
            var attachment = new FermataAttachment();
            var expectedPx = attachment.getContentHeightSs() * TEST_PPS;
            assertThat(attachment.getContentHeightPx()).isCloseTo(expectedPx, within(DOUBLE_EPSILON));
        }

        @Test
        void testGetContentWidthPxEqualsWidthSsTimesScale() {
            var attachment = new FermataAttachment();
            var expectedPx = attachment.getContentWidthSs() * TEST_PPS;
            assertThat(attachment.getContentWidthPx()).isCloseTo(expectedPx, within(DOUBLE_EPSILON));
        }
    }

    // -------------------------------------------------------------------------
    // Row 19 — getContentWidthSs/HeightSs return exact SMuFL bbox dimensions
    // -------------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class ContentDimensionsSs {

        @Test
        void testGetContentHeightSsMatchesFermataAboveBboxHeight() {
            var bbox = SMuFLMetadata.requireBBox(SMuFLGlyph.FERMATA_ABOVE);
            var attachment = new FermataAttachment();
            assertThat(attachment.getContentHeightSs()).isEqualTo(bbox.height());
        }

        @Test
        void testGetContentWidthSsMatchesFermataAboveBboxWidth() {
            var bbox = SMuFLMetadata.requireBBox(SMuFLGlyph.FERMATA_ABOVE);
            var attachment = new FermataAttachment();
            assertThat(attachment.getContentWidthSs()).isEqualTo(bbox.width());
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class Copy {

        @Test
        void testCopyReturnsDistinctInstanceWithNewOwner() {
            var originalOwner = ElementType.CROTCHET.newInstance();
            var newOwner = ElementType.QUAVER.newInstance();
            var original = new FermataAttachment(originalOwner);

            var copy = original.copy(newOwner);

            assertThat(copy).isNotSameAs(original);
            assertThat(copy).isExactlyInstanceOf(FermataAttachment.class);
            assertThat(copy.getOwnerElement()).isSameAs(newOwner);
        }
    }
}
