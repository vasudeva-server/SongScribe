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

package songscribe.shape;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import module java.desktop;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;

/**
 * Characterization tests for {@link BezierBow}: pin {@code height}/{@code indent}/{@code lens}
 * against values computed independently (by hand, via the documented {@code bezier-bow.cc}
 * formulas) from the pre-refactor {@code LayoutEngine.slurHeightSs}/{@code slurIndentSs} and
 * {@code TieShape.build} (deleted in Phase 4; see plans/features/lilypond-ties.md Phase 4). These
 * guard against a silently mis-wired parameter — e.g. {@code ratio} and {@code heightLimitSs}
 * swapped at a call site — that would mis-shape every tie without failing compilation.
 */
class BezierBowTest extends UnitTest {

    private static final double TOLERANCE = 1e-9;

    /**
     * Mirrors {@code LayoutEngine.TIE_RATIO} (0.333). Hardcoded rather than referenced because
     * {@code BezierBowTest} lives in {@code songscribe.shape}, a different package from
     * {@code songscribe.layout.LayoutEngine}, and the constant is package-private there.
     */
    private static final double TIE_RATIO = 0.333;

    /** Mirrors {@code LayoutEngine.TIE_HEIGHT_LIMIT_SS} (1.1). See {@link #TIE_RATIO}. */
    private static final double TIE_HEIGHT_LIMIT_SS = 1.1;

    /**
     * Mirrors {@code LayoutEngine.TIE_SLUR_MAX_FRACTION} (1 / 3.1). See {@link #TIE_RATIO}; this
     * one is additionally {@code private} in {@code LayoutEngine}, so it is unreachable even from
     * the same package.
     */
    private static final double TIE_SLUR_MAX_FRACTION = 1.0 / 3.1;

    private static final double NARROW_WIDTH_SS = 2.0;
    private static final double MEDIUM_WIDTH_SS = 6.0;
    private static final double WIDE_WIDTH_SS = 10.0;

    // Hand-computed (Python, double precision) from the documented formulas:
    //   f01(x)     = 2/pi * atan(pi*x/2)
    //   height(w)  = f01(w * TIE_RATIO / TIE_HEIGHT_LIMIT_SS) * TIE_HEIGHT_LIMIT_SS
    //   indent(w)  = 2*TIE_HEIGHT_LIMIT_SS - q*q*TIE_SLUR_MAX_FRACTION / (w + q),
    //                q = 2*TIE_HEIGHT_LIMIT_SS / TIE_SLUR_MAX_FRACTION
    private static final double EXPECTED_NARROW_HEIGHT_SS = 0.532432724816226;
    private static final double EXPECTED_MEDIUM_HEIGHT_SS = 0.8639262721938931;
    private static final double EXPECTED_WIDE_HEIGHT_SS = 0.9548494191381434;

    private static final double EXPECTED_NARROW_INDENT_SS = 0.49886621315192703;
    private static final double EXPECTED_MEDIUM_INDENT_SS = 1.0296411856474257;
    private static final double EXPECTED_WIDE_INDENT_SS = 1.3079667063020213;

    @Nested
    class Height {

        @Test
        void testNarrowWidthMatchesHandComputedValue() {
            assertThat(BezierBow.height(NARROW_WIDTH_SS, TIE_RATIO, TIE_HEIGHT_LIMIT_SS))
                .isCloseTo(EXPECTED_NARROW_HEIGHT_SS, within(TOLERANCE));
        }

        @Test
        void testMediumWidthMatchesHandComputedValue() {
            assertThat(BezierBow.height(MEDIUM_WIDTH_SS, TIE_RATIO, TIE_HEIGHT_LIMIT_SS))
                .isCloseTo(EXPECTED_MEDIUM_HEIGHT_SS, within(TOLERANCE));
        }

        @Test
        void testWideWidthMatchesHandComputedValue() {
            assertThat(BezierBow.height(WIDE_WIDTH_SS, TIE_RATIO, TIE_HEIGHT_LIMIT_SS))
                .isCloseTo(EXPECTED_WIDE_HEIGHT_SS, within(TOLERANCE));
        }

        @Test
        void testRatioAndHeightLimitSwapDoesNotMatchHandComputedValue() {
            // Guards the exact failure mode this test class exists to catch: if a call site ever
            // swaps ratio and heightLimitSs, height() must diverge from the known-good value.
            assertThat(BezierBow.height(MEDIUM_WIDTH_SS, TIE_HEIGHT_LIMIT_SS, TIE_RATIO))
                .describedAs("swapped ratio/heightLimitSs must not coincidentally match the correct output")
                .isNotCloseTo(EXPECTED_MEDIUM_HEIGHT_SS, within(TOLERANCE));
        }
    }

    @Nested
    class Indent {

        @Test
        void testNarrowWidthMatchesHandComputedValue() {
            assertThat(BezierBow.indent(NARROW_WIDTH_SS, TIE_HEIGHT_LIMIT_SS, TIE_SLUR_MAX_FRACTION))
                .isCloseTo(EXPECTED_NARROW_INDENT_SS, within(TOLERANCE));
        }

        @Test
        void testMediumWidthMatchesHandComputedValue() {
            assertThat(BezierBow.indent(MEDIUM_WIDTH_SS, TIE_HEIGHT_LIMIT_SS, TIE_SLUR_MAX_FRACTION))
                .isCloseTo(EXPECTED_MEDIUM_INDENT_SS, within(TOLERANCE));
        }

        @Test
        void testWideWidthMatchesHandComputedValue() {
            assertThat(BezierBow.indent(WIDE_WIDTH_SS, TIE_HEIGHT_LIMIT_SS, TIE_SLUR_MAX_FRACTION))
                .isCloseTo(EXPECTED_WIDE_INDENT_SS, within(TOLERANCE));
        }
    }

    /**
     * {@code lens} is a pure geometric assembler (no LilyPond formula), ported verbatim from the
     * deleted {@code TieShape.build}: an outer cubic Bézier start → end, then a reversed inner
     * cubic Bézier end → start, closed. These tests pin that exact control-point wiring by walking
     * the resulting {@link Shape}'s {@link PathIterator}.
     */
    @Nested
    class Lens {

        private static final double START_X_SS = 0.0;
        private static final double START_Y_SS = 0.0;
        private static final double CP1_X_SS = 1.0;
        private static final double CP1_Y_SS = -2.0;
        private static final double CP2_X_SS = 3.0;
        private static final double CP2_Y_SS = -2.0;
        private static final double END_X_SS = 4.0;
        private static final double END_Y_SS = 0.0;
        private static final double INNER_CP1_X_SS = 1.0;
        private static final double INNER_CP1_Y_SS = -1.0;
        private static final double INNER_CP2_X_SS = 3.0;
        private static final double INNER_CP2_Y_SS = -1.0;

        private static final int COORDS_PER_CUBIC_SEGMENT = 6;
        private static final double COORD_TOLERANCE = 1e-9;

        @Test
        void testPathIsOuterCurveThenReversedInnerCurveThenClose() {
            var lens = BezierBow.lens(
                START_X_SS, START_Y_SS,
                CP1_X_SS, CP1_Y_SS,
                CP2_X_SS, CP2_Y_SS,
                END_X_SS, END_Y_SS,
                INNER_CP1_X_SS, INNER_CP1_Y_SS,
                INNER_CP2_X_SS, INNER_CP2_Y_SS);

            var iterator = lens.getPathIterator(null);
            var coords = new double[COORDS_PER_CUBIC_SEGMENT];

            assertThat(iterator.currentSegment(coords)).isEqualTo(PathIterator.SEG_MOVETO);
            assertThat(coords[0]).isCloseTo(START_X_SS, within(COORD_TOLERANCE));
            assertThat(coords[1]).isCloseTo(START_Y_SS, within(COORD_TOLERANCE));
            iterator.next();

            // Outer cubic: start -> end via (cp1, cp2).
            assertThat(iterator.currentSegment(coords)).isEqualTo(PathIterator.SEG_CUBICTO);
            assertThat(coords).containsExactly(
                new double[] {CP1_X_SS, CP1_Y_SS, CP2_X_SS, CP2_Y_SS, END_X_SS, END_Y_SS},
                within(COORD_TOLERANCE));
            iterator.next();

            // Inner cubic, reversed: end -> start via (innerCp2, innerCp1).
            assertThat(iterator.currentSegment(coords)).isEqualTo(PathIterator.SEG_CUBICTO);
            assertThat(coords).containsExactly(
                new double[] {
                    INNER_CP2_X_SS, INNER_CP2_Y_SS, INNER_CP1_X_SS, INNER_CP1_Y_SS, START_X_SS, START_Y_SS},
                within(COORD_TOLERANCE));
            iterator.next();

            assertThat(iterator.currentSegment(coords)).isEqualTo(PathIterator.SEG_CLOSE);
            iterator.next();

            assertThat(iterator.isDone()).isTrue();
        }
    }
}
