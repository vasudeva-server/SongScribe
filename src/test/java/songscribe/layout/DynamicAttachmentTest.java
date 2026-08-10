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
import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.dom.StaffElementFactory.quaver;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.DynamicAttachment;
import songscribe.dom.DynamicAttachment.DynamicType;
import songscribe.dom.ScaleContext;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;

class DynamicAttachmentTest extends UnitTest {

    // Non-default scale — distinct from ScaleContext.DEFAULT_PIXELS_PER_STAFF_SPACE (8.0)
    // so a forgotten tearDown surfaces immediately rather than silently passing.
    private static final double TEST_PPS = 12.5;

    // Tolerance for floating-point comparisons.
    private static final double DOUBLE_EPSILON = 1e-9;

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class Copy {

        @Test
        void testCopyReturnsDistinctInstanceWithNewOwnerAndPreservesType() {
            var originalOwner = crotchet();
            var newOwner = quaver();
            var original = new DynamicAttachment(originalOwner, DynamicType.FORTE);

            var copy = original.copy(newOwner);

            assertThat(copy).isNotSameAs(original);
            assertThat(copy).isExactlyInstanceOf(DynamicAttachment.class);
            assertThat(copy.getOwnerElement()).isSameAs(newOwner);
            assertThat(((DynamicAttachment) copy).getType()).isEqualTo(DynamicType.FORTE);
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class DynamicTypeFields {

        @Test
        void testForteHasCorrectGlyph() {
            assertThat(DynamicType.FORTE.getGlyph()).isEqualTo(SMuFLGlyph.DYNAMIC_FORTE);
        }

        @Test
        void testForteHasCorrectVelocityFraction() {
            assertThat(DynamicType.FORTE.getVelocityFraction()).isEqualTo(0.78);
        }

        @Test
        void testFortissimoHasCorrectGlyph() {
            assertThat(DynamicType.FORTISSIMO.getGlyph()).isEqualTo(SMuFLGlyph.DYNAMIC_FF);
        }

        @Test
        void testFortissimoHasCorrectVelocityFraction() {
            assertThat(DynamicType.FORTISSIMO.getVelocityFraction()).isEqualTo(1.00);
        }

        @Test
        void testMezzoForteHasCorrectGlyph() {
            assertThat(DynamicType.MEZZO_FORTE.getGlyph()).isEqualTo(SMuFLGlyph.DYNAMIC_MF);
        }

        @Test
        void testMezzoPianoHasCorrectGlyph() {
            assertThat(DynamicType.MEZZO_PIANO.getGlyph()).isEqualTo(SMuFLGlyph.DYNAMIC_MP);
        }

        @Test
        void testPianissimoHasCorrectGlyph() {
            assertThat(DynamicType.PIANISSIMO.getGlyph()).isEqualTo(SMuFLGlyph.DYNAMIC_PP);
        }

        @Test
        void testPianoHasCorrectGlyph() {
            assertThat(DynamicType.PIANO.getGlyph()).isEqualTo(SMuFLGlyph.DYNAMIC_PIANO);
        }

        @Test
        void testSymbolReturnsExpectedStringForAllTypes() {
            assertThat(DynamicType.PIANISSIMO.getSymbol()).isEqualTo("pp");
            assertThat(DynamicType.PIANO.getSymbol()).isEqualTo("p");
            assertThat(DynamicType.MEZZO_PIANO.getSymbol()).isEqualTo("mp");
            assertThat(DynamicType.MEZZO_FORTE.getSymbol()).isEqualTo("mf");
            assertThat(DynamicType.FORTE.getSymbol()).isEqualTo("f");
            assertThat(DynamicType.FORTISSIMO.getSymbol()).isEqualTo("ff");
        }

        // Every declared type must resolve to real font metrics, not merely name a glyph constant.
        // A type whose glyph is missing from the SMuFL metadata would draw nothing while still
        // reserving a footprint, which is exactly the invisible-but-space-taking mark that removing
        // sfz and fp was meant to rule out (refs #510).
        @Test
        void testEveryTypeResolvesToRealGlyphMetrics() {
            for (var type : DynamicType.values()) {
                var attachment = new DynamicAttachment(type);

                assertThat(attachment.getContentWidthSs())
                    .as("%s must have a drawable width", type)
                    .isGreaterThan(0.0);
                assertThat(attachment.getContentHeightSs())
                    .as("%s must have a drawable height", type)
                    .isGreaterThan(0.0);
            }
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class Dimensions {

        @BeforeEach
        void setUp() {
            ScaleContext.setPixelsPerStaffSpace(TEST_PPS);
        }

        @AfterEach
        void tearDown() {
            ScaleContext.setPixelsPerStaffSpace(ScaleContext.DEFAULT_PIXELS_PER_STAFF_SPACE);
        }

        @Test
        void testContentHeightPxEqualsHeightSsTimesScale() {
            var attachment = new DynamicAttachment(DynamicType.FORTE);
            var expectedPx = attachment.getContentHeightSs() * TEST_PPS;
            assertThat(attachment.getContentHeightPx()).isCloseTo(expectedPx, within(DOUBLE_EPSILON));
        }

        @Test
        void testContentHeightSsDelegatestoSmuflBBox() {
            var attachment = new DynamicAttachment(DynamicType.FORTE);
            var bbox = SMuFLMetadata.requireBBox(SMuFLGlyph.DYNAMIC_FORTE);
            assertThat(attachment.getContentHeightSs()).isEqualTo(bbox.height());
        }

        @Test
        void testContentWidthPxEqualsWidthSsTimesScale() {
            var attachment = new DynamicAttachment(DynamicType.FORTE);
            var expectedPx = attachment.getContentWidthSs() * TEST_PPS;
            assertThat(attachment.getContentWidthPx()).isCloseTo(expectedPx, within(DOUBLE_EPSILON));
        }

        @Test
        void testContentWidthSsDelegatesToSmuflBBox() {
            var attachment = new DynamicAttachment(DynamicType.FORTE);
            var bbox = SMuFLMetadata.requireBBox(SMuFLGlyph.DYNAMIC_FORTE);
            assertThat(attachment.getContentWidthSs()).isEqualTo(bbox.width());
        }

        // The shared dynamics baseline hangs a glyph by how far its ink drops below the text
        // baseline. Getting that wrong tilts a "p" or "f" off the line its hairpin sits on.
        @Test
        void testContentBottomSsDelegatesToSmuflBBox() {
            var attachment = new DynamicAttachment(DynamicType.FORTE);
            var bbox = SMuFLMetadata.requireBBox(SMuFLGlyph.DYNAMIC_FORTE);
            assertThat(attachment.getContentBottomSs()).isEqualTo(bbox.bottom());
        }

        // A neighbouring hairpin pads away from the glyph's *advance* box, not its ink box
        // (HairpinEndpoints.dynamicLeftTipOffsetSs). The two differ because the italic dynamics
        // paint outside the box the font declares for them, so the advance width and the left side
        // bearing are what carry that distinction. These are the only assertions in the suite that
        // pin them against the font directly: every hairpin-geometry test derives its expected
        // position by calling the same production helper it is checking, so a silent reversion to
        // the ink box would pass there and fail only here.
        @Test
        void testAdvanceWidthSsDelegatesToSmuflAdvanceWidth() {
            var attachment = new DynamicAttachment(DynamicType.FORTE);
            assertThat(attachment.getAdvanceWidthSs())
                .isEqualTo(SMuFLMetadata.requireAdvanceWidth(SMuFLGlyph.DYNAMIC_FORTE));
        }

        @Test
        void testLeftSideBearingSsDelegatesToSmuflBBoxLeftEdge() {
            var attachment = new DynamicAttachment(DynamicType.FORTE);
            var bbox = SMuFLMetadata.requireBBox(SMuFLGlyph.DYNAMIC_FORTE);
            assertThat(attachment.getLeftSideBearingSs()).isEqualTo(bbox.left());
        }

        // The whole point of the advance box is that it is not the ink box. If the font ever
        // stopped distinguishing them for this glyph, the two assertions above would still pass
        // while every hairpin next to an "f" silently moved.
        @Test
        void testForteInkOverhangsItsAdvanceBoxOnTheLeft() {
            var attachment = new DynamicAttachment(DynamicType.FORTE);

            assertThat(attachment.getLeftSideBearingSs())
                .as("forte's ink starts left of its glyph origin, which is what makes the "
                    + "advance box and the ink box differ")
                .isNegative();
            assertThat(attachment.getAdvanceWidthSs())
                .as("forte's advance box is narrower than the ink it paints")
                .isLessThan(attachment.getContentWidthSs());
        }
    }
}
