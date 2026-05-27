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

import java.awt.Font;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;

class SectionLayoutTest extends UnitTest {

    private static final double CONTENT_WIDTH = 120.0;
    private static final double CONTENT_HEIGHT = 42.0;
    private static final int BASELINE_Y = 30;
    private static final String FIRST_LINE = "first";
    private static final String SECOND_LINE = "second";
    private static final Font SAMPLE_FONT = new Font(Font.SERIF, Font.PLAIN, 12);

    private static ElementBoundsSs boundsWithHeight(double height) {
        return ElementBoundsSs.contentOnly(new Rectangle2D.Double(0, 0, CONTENT_WIDTH, height));
    }

    private static ElementBoundsSs sampleBounds() {
        return boundsWithHeight(CONTENT_HEIGHT);
    }

    @Test
    void testHasContentTrueWhenFirstLineNonEmpty() {
        var layout = new SectionLayout(sampleBounds(), List.of(FIRST_LINE, SECOND_LINE), SAMPLE_FONT, BASELINE_Y);

        assertThat(layout.hasContent()).isTrue();
    }

    @Test
    void testHasContentFalseWhenLinesEmpty() {
        var layout = new SectionLayout(sampleBounds(), List.of(), SAMPLE_FONT, BASELINE_Y);

        assertThat(layout.hasContent()).isFalse();
    }

    @Test
    void testHasContentFalseWhenFirstLineEmptyString() {
        var layout = new SectionLayout(sampleBounds(), List.of(""), SAMPLE_FONT, BASELINE_Y);

        assertThat(layout.hasContent()).isFalse();
    }

    @Test
    void testGetTextReturnsFirstLine() {
        var layout = new SectionLayout(sampleBounds(), List.of(FIRST_LINE, SECOND_LINE), SAMPLE_FONT, BASELINE_Y);

        assertThat(layout.getText()).isEqualTo(FIRST_LINE);
    }

    @Test
    void testGetTextReturnsEmptyStringWhenNoLines() {
        var layout = new SectionLayout(sampleBounds(), List.of(), SAMPLE_FONT, BASELINE_Y);

        assertThat(layout.getText()).isEmpty();
    }

    @Test
    void testGetHeightReturnsContentBoundsHeight() {
        var layout = new SectionLayout(sampleBounds(), List.of(FIRST_LINE), SAMPLE_FONT, BASELINE_Y);

        assertThat(layout.getHeight()).isEqualTo(CONTENT_HEIGHT);
    }

    @Test
    void testEmptyFactoryHasZeroSizeNoLinesAndNullFont() {
        var layout = SectionLayout.empty();

        assertThat(layout.bounds().getContentBounds().getWidth()).isZero();
        assertThat(layout.bounds().getContentBounds().getHeight()).isZero();
        assertThat(layout.getHeight()).isZero();
        assertThat(layout.lines()).isEmpty();
        assertThat(layout.font()).isNull();
        assertThat(layout.baselineY()).isZero();
        assertThat(layout.hasContent()).isFalse();
    }

    @Test
    void testStringConstructorWrapsTextInSingleElementList() {
        var layout = new SectionLayout(sampleBounds(), FIRST_LINE, SAMPLE_FONT, BASELINE_Y);

        assertThat(layout.lines()).containsExactly(FIRST_LINE);
        assertThat(layout.getText()).isEqualTo(FIRST_LINE);
    }

    @Test
    void testLinesIsDefensiveCopyOfConstructorArgument() {
        var source = new ArrayList<String>();
        source.add(FIRST_LINE);
        var layout = new SectionLayout(sampleBounds(), source, SAMPLE_FONT, BASELINE_Y);

        source.add(SECOND_LINE);

        assertThat(layout.lines()).containsExactly(FIRST_LINE);
    }
}
