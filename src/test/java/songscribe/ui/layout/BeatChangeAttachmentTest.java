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

package songscribe.ui.layout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.music.BeatChange;
import songscribe.music.Song;
import songscribe.music.Duration;
import songscribe.util.GraphicUtils;

class BeatChangeAttachmentTest extends UnitTest {

    private static final double EPSILON = 1e-10;
    private static Song song;

    @BeforeAll
    static void setUp() {
        song = new Song();
    }

    /**
     * A quarter-note → quarter-note beat change: both notes have a metronome glyph
     * so all three sub-regions have non-zero widths.
     */
    private static BeatChangeAttachment crotchetToCrotchet() {
        return new BeatChangeAttachment(new BeatChange(Duration.CROTCHET, Duration.CROTCHET));
    }

    @Nested
    class RegionStructure {

        @Test
        void testProducesThreeRegions() {
            var font = song.getAttributionFont();
            var metrics = crotchetToCrotchet().computeContentMetrics(font);

            assertThat(metrics.regions()).hasSize(3);
        }

        @Test
        void testLeftNoteRegionStartsAtOrigin() {
            var font = song.getAttributionFont();
            var metrics = crotchetToCrotchet().computeContentMetrics(font);
            var leftNote = metrics.regions().get(0);

            assertThat(leftNote.xOffsetSs()).isEqualTo(0.0);
            assertThat(leftNote.yOffsetSs()).isEqualTo(0.0);
            assertThat(leftNote.heightSs()).isEqualTo(MetronomeAttachment.QUARTER_NOTE_HEIGHT_SS);
            assertThat(leftNote.widthSs()).isGreaterThan(0.0);
        }

        @Test
        void testEqualsRegionFollowsLeftNoteWithGap() {
            var font = song.getAttributionFont();
            var metrics = crotchetToCrotchet().computeContentMetrics(font);
            var leftNote = metrics.regions().get(0);
            var equals = metrics.regions().get(1);

            assertThat(equals.xOffsetSs())
                .isEqualTo(leftNote.widthSs());
            assertThat(equals.widthSs()).isGreaterThan(0.0);
        }

        @Test
        void testRightNoteRegionFollowsEqualsWithGap() {
            var font = song.getAttributionFont();
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
            var font = song.getAttributionFont();
            var metrics = crotchetToCrotchet().computeContentMetrics(font);
            var regions = metrics.regions();

            double expectedWidth = regions.get(0).widthSs()
                + regions.get(1).widthSs()
                + regions.get(2).widthSs();

            assertThat(metrics.widthSs()).isCloseTo(expectedWidth, within(EPSILON));
        }
    }

    @Nested
    class EqualsDescender {

        @Test
        void testEqualsSignDescentExtendsBelow_QUARTER_NOTE_HEIGHT_SS() {
            var font = song.getAttributionFont();
            var metrics = crotchetToCrotchet().computeContentMetrics(font);
            var equals = metrics.regions().get(1);

            double equalsDescentSs = ScaleContext.getInstance().fromPixels(
                font.getLineMetrics("=", GraphicUtils.SCREEN_FRC).getDescent());
            double equalsBottom = equals.yOffsetSs() + equals.heightSs();

            assertThat(equalsBottom).isCloseTo(
                MetronomeAttachment.QUARTER_NOTE_HEIGHT_SS + equalsDescentSs,
                within(EPSILON));
        }

        @Test
        void testEqualsDescentIsPositive() {
            var font = song.getAttributionFont();
            var metrics = crotchetToCrotchet().computeContentMetrics(font);
            var equals = metrics.regions().get(1);

            double equalsBottom = equals.yOffsetSs() + equals.heightSs();

            assertThat(equalsBottom).isGreaterThan(MetronomeAttachment.QUARTER_NOTE_HEIGHT_SS);
        }
    }
}
