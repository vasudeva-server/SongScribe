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

package songscribe.ui.renderer;

import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;
import songscribe.smufl.SMuFLGlyph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * How many glyphs a {@link GlyphRun} holds for a span, and where a centered run stands: the count
 * is the number of whole glyphs that fit, never fewer than one, and a run centered on a span
 * overflows it equally on both sides.
 */
class GlyphRunTest extends UnitTest {

    private static final ScaledGlyph TILE = ScaledGlyph.atDesignSize(SMuFLGlyph.WIGGLE_TRILL_FASTER);

    private static final double SPAN_LEFT_XSS = 4.0;

    private static final double BASELINE_YSS = 1.5;

    private static final double TOLERANCE_SS = 0.0001;

    /**
     * One span, and how many glyphs fit it.
     *
     * @param description         the case, which doubles as the parameterized display name
     * @param spanWidthInAdvances the span's width, in multiples of the tile's advance
     * @param expectedCount       how many whole glyphs fit, at least one
     */
    private record CountCase(String description, double spanWidthInAdvances, int expectedCount) {

        @Override
        public String toString() {
            return description;
        }

        GlyphRun.Span span() {
            return new GlyphRun.Span(SPAN_LEFT_XSS, SPAN_LEFT_XSS + spanWidthInAdvances * TILE.advanceSs());
        }
    }

    static Stream<CountCase> countCases() {
        return Stream.of(
            new CountCase("a span narrower than one glyph holds the minimum of one", 0.5, 1),
            new CountCase("a span exactly one glyph wide holds one", 1.0, 1),
            new CountCase("a span of one glyph and a half holds one, the half is left over", 1.5, 1),
            new CountCase("a span exactly two glyphs wide holds two", 2.0, 2),
            new CountCase("a span of three glyphs and a half holds three", 3.5, 3)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("countCases")
    void testRunHoldsTheWholeGlyphsThatFit(CountCase testCase) {
        var span = testCase.span();

        assertThat(GlyphRun.startingAt(TILE, span, BASELINE_YSS).count()).isEqualTo(testCase.expectedCount());
        assertThat(GlyphRun.centeredOn(TILE, span, BASELINE_YSS).count()).isEqualTo(testCase.expectedCount());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("countCases")
    void testRunStartingAtASpanBeginsAtItsLeftEdge(CountCase testCase) {
        var run = GlyphRun.startingAt(TILE, testCase.span(), BASELINE_YSS);

        assertThat(run.startXSs()).isEqualTo(SPAN_LEFT_XSS);
        assertThat(run.baselineYSs()).isEqualTo(BASELINE_YSS);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("countCases")
    void testRunCenteredOnASpanOverflowsOrLeavesTheSameOnBothSides(CountCase testCase) {
        var span = testCase.span();
        var run = GlyphRun.centeredOn(TILE, span, BASELINE_YSS);

        var runRightXSs = run.startXSs() + run.count() * TILE.advanceSs();
        var leftOfSpanXSs = span.leftXSs() - run.startXSs();
        var rightOfSpanXSs = runRightXSs - span.rightXSs();

        assertThat(leftOfSpanXSs).isCloseTo(rightOfSpanXSs, within(TOLERANCE_SS));
        assertThat(run.baselineYSs()).isEqualTo(BASELINE_YSS);
    }
}
