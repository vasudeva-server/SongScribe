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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.BeatChange;
import songscribe.dom.BeatChangeAttachment;
import songscribe.dom.Duration;
import songscribe.dom.ElementType;
import songscribe.dom.MetronomeAttachment;
import songscribe.dom.ScaleContext;
import songscribe.font.DocumentFonts;
import songscribe.util.GraphicUtils;

class BeatChangeAttachmentTest extends UnitTest {

    private static final double EPSILON = 1e-10;

    /**
     * A quarter-note → quarter-note beat change: both notes have a metronome glyph
     * so all three sub-regions have non-zero widths.
     */
    private static BeatChangeAttachment crotchetToCrotchet() {
        return new BeatChangeAttachment(new BeatChange(Duration.CROTCHET, Duration.CROTCHET));
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class Copy {

        @Test
        void testCopyReturnsDistinctInstanceWithNewOwnerAndPreservesBeatChange() {
            var beatChange = new BeatChange(Duration.QUAVER, Duration.QUAVER);
            var originalOwner = ElementType.CROTCHET.newInstance();
            var newOwner = ElementType.QUAVER.newInstance();
            var original = new BeatChangeAttachment(originalOwner, beatChange);

            var copy = original.copy(newOwner);

            assertThat(copy).isNotSameAs(original);
            assertThat(copy).isExactlyInstanceOf(BeatChangeAttachment.class);
            assertThat(copy.getOwnerElement()).isSameAs(newOwner);
            assertThat(((BeatChangeAttachment) copy).getBeatChange()).isSameAs(beatChange);
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class RegionStructure {

        @Test
        void testProducesThreeRegions() {
            var font = DocumentFonts.defaultFonts().getAttributionFont();
            var metrics = crotchetToCrotchet().computeContentMetrics(font);

            assertThat(metrics.regions()).hasSize(3);
        }

        @Test
        void testLeftNoteRegionStartsAtOrigin() {
            var font = DocumentFonts.defaultFonts().getAttributionFont();
            var metrics = crotchetToCrotchet().computeContentMetrics(font);
            var leftNote = metrics.regions().getFirst();

            assertThat(leftNote.xOffsetSs()).isEqualTo(0.0);
            assertThat(leftNote.yOffsetSs()).isEqualTo(0.0);
            assertThat(leftNote.heightSs()).isEqualTo(MetronomeAttachment.QUARTER_NOTE_HEIGHT_SS);
            assertThat(leftNote.widthSs()).isGreaterThan(0.0);
        }

        @Test
        void testEqualsRegionFollowsLeftNoteWithGap() {
            var font = DocumentFonts.defaultFonts().getAttributionFont();
            var metrics = crotchetToCrotchet().computeContentMetrics(font);
            var leftNote = metrics.regions().get(0);
            var equals = metrics.regions().get(1);

            assertThat(equals.xOffsetSs())
                .isEqualTo(leftNote.widthSs());
            assertThat(equals.widthSs()).isGreaterThan(0.0);
        }

        @Test
        void testRightNoteRegionFollowsEqualsWithGap() {
            var font = DocumentFonts.defaultFonts().getAttributionFont();
            var metrics = crotchetToCrotchet().computeContentMetrics(font);
            var equals = metrics.regions().get(1);
            var rightNote = metrics.regions().get(2);

            assertThat(rightNote.xOffsetSs())
                .isEqualTo(equals.xOffsetSs() + equals.widthSs());
            assertThat(rightNote.yOffsetSs()).isEqualTo(0.0);
            assertThat(rightNote.heightSs()).isEqualTo(MetronomeAttachment.QUARTER_NOTE_HEIGHT_SS);
            assertThat(rightNote.widthSs()).isGreaterThan(0.0);
        }

        @Test
        void testTotalWidthIsSumOfRegionsAndGaps() {
            var font = DocumentFonts.defaultFonts().getAttributionFont();
            var metrics = crotchetToCrotchet().computeContentMetrics(font);
            var regions = metrics.regions();

            var expectedWidth = regions.get(0).widthSs()
                + regions.get(1).widthSs()
                + regions.get(2).widthSs();

            assertThat(metrics.widthSs()).isCloseTo(expectedWidth, within(EPSILON));
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class EqualsDescender {

        @Test
        void testEqualsSignDescentExtendsBelow_QUARTER_NOTE_HEIGHT_SS() {
            var font = DocumentFonts.defaultFonts().getAttributionFont();
            var metrics = crotchetToCrotchet().computeContentMetrics(font);
            var equals = metrics.regions().get(1);

            var equalsDescentSs = ScaleContext.pxToSs(
                font.getLineMetrics("=", GraphicUtils.SCREEN_FRC).getDescent());
            var equalsBottom = equals.yOffsetSs() + equals.heightSs();

            assertThat(equalsBottom).isCloseTo(
                MetronomeAttachment.QUARTER_NOTE_HEIGHT_SS + equalsDescentSs,
                within(EPSILON));
        }

        @Test
        void testEqualsDescentIsPositive() {
            var font = DocumentFonts.defaultFonts().getAttributionFont();
            var metrics = crotchetToCrotchet().computeContentMetrics(font);
            var equals = metrics.regions().get(1);

            var equalsBottom = equals.yOffsetSs() + equals.heightSs();

            assertThat(equalsBottom).isGreaterThan(MetronomeAttachment.QUARTER_NOTE_HEIGHT_SS);
        }
    }
}
