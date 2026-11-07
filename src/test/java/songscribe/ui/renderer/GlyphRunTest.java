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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;
import songscribe.smufl.SMuFLGlyph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * How many glyphs a {@link GlyphRun} holds for a span, and where a run stands: the count is the
 * number of whole glyphs of the tile's scaled size that fit, never fewer than one, and a span a
 * rounding error short of a whole number holds that number. A run starting at a span begins at its
 * left edge, and a run centered on a span overflows it equally on both sides.
 */
class GlyphRunTest extends UnitTest {

    private static final ScaledGlyph TILE = ScaledGlyph.atDesignSize(SMuFLGlyph.WIGGLE_TRILL_FASTER);

    // Two scaled advances must not also hold exactly two design advances, so that a count taken
    // from the design advance gives a number other than two.
    private static final double NON_DESIGN_SCALE = 2.0;

    private static final ScaledGlyph SCALED_TILE = new ScaledGlyph(SMuFLGlyph.WIGGLE_TRILL_FASTER, NON_DESIGN_SCALE);

    private static final double SPAN_LEFT_XSS = 4.0;

    private static final double BASELINE_YSS = 1.5;

    private static final double TOLERANCE_SS = 0.0001;

    /**
     * One span, and how many glyphs of one tile fit it.
     *
     * @param description   the case, which doubles as the parameterized display name
     * @param tile          the glyph tiled along the span
     * @param span          the stretch the run is fitted to
     * @param expectedCount how many whole glyphs fit, at least one
     */
    private record CountCase(String description, ScaledGlyph tile, GlyphRun.Span span, int expectedCount) {

        @Override
        public String toString() {
            return description;
        }
    }

    private static CountCase inAdvances(
        String description,
        ScaledGlyph tile,
        double spanWidthInAdvances,
        int expectedCount
    ) {
        var span = new GlyphRun.Span(SPAN_LEFT_XSS, SPAN_LEFT_XSS + spanWidthInAdvances * tile.advanceSs());

        return new CountCase(description, tile, span, expectedCount);
    }

    static Stream<CountCase> countCases() {
        // The span starts at zero so that its width is exactly the edge it is built from: an
        // offset edge would round the one-ulp shortfall away.
        var roundingErrorShortOfTwo = new GlyphRun.Span(0, Math.nextDown(2 * TILE.advanceSs()));

        return Stream.of(
            inAdvances("a span narrower than one glyph holds the minimum of one", TILE, 0.5, 1),
            inAdvances("a span exactly one glyph wide holds one", TILE, 1.0, 1),
            inAdvances("a span of one glyph and a half holds one, the half is left over", TILE, 1.5, 1),
            inAdvances("a span exactly two glyphs wide holds two", TILE, 2.0, 2),
            inAdvances("a span of three glyphs and a half holds three", TILE, 3.5, 3),
            new CountCase("a span a rounding error short of two glyphs holds two", TILE, roundingErrorShortOfTwo, 2),
            inAdvances("a scaled tile holds the whole scaled glyphs that fit", SCALED_TILE, 2.0, 2)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("countCases")
    void testRunHoldsTheWholeGlyphsThatFit(CountCase testCase) {
        var tile = testCase.tile();
        var span = testCase.span();

        assertThat(GlyphRun.startingAt(tile, span, BASELINE_YSS).count()).isEqualTo(testCase.expectedCount());
        assertThat(GlyphRun.centeredOn(tile, span, BASELINE_YSS).count()).isEqualTo(testCase.expectedCount());
    }

    @Test
    void testRunStartingAtASpanBeginsAtItsLeftEdge() {
        var advanceSs = TILE.advanceSs();
        var span = new GlyphRun.Span(SPAN_LEFT_XSS, SPAN_LEFT_XSS + advanceSs + advanceSs / 2);
        var run = GlyphRun.startingAt(TILE, span, BASELINE_YSS);

        assertThat(run.startXSs()).isEqualTo(SPAN_LEFT_XSS);
        assertThat(run.baselineYSs()).isEqualTo(BASELINE_YSS);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("countCases")
    void testRunCenteredOnASpanOverflowsOrLeavesTheSameOnBothSides(CountCase testCase) {
        var tile = testCase.tile();
        var span = testCase.span();
        var run = GlyphRun.centeredOn(tile, span, BASELINE_YSS);

        var runRightXSs = run.startXSs() + run.count() * tile.advanceSs();
        var leftOfSpanXSs = span.leftXSs() - run.startXSs();
        var rightOfSpanXSs = runRightXSs - span.rightXSs();

        assertThat(leftOfSpanXSs).isCloseTo(rightOfSpanXSs, within(TOLERANCE_SS));
        assertThat(run.baselineYSs()).isEqualTo(BASELINE_YSS);
    }
}
