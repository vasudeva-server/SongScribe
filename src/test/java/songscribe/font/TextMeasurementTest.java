/*
 * SongScribe song notation program
 * Copyright (C) Sri Chinmoy Centres International
 *
 * This file is part of SongScribe.
 *
 * SongScribe is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * SongScribe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package songscribe.font;

import java.awt.FontMetrics;
import java.awt.geom.Rectangle2D;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import songscribe.UnitTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link TextMeasurement#extraInkAbove}, {@link TextMeasurement#extraInkBelow} and
 * {@link TextMeasurement#textBlockHeight}: the ink-overshoot and line-height formulas that
 * neither call into the graphics environment nor need a system font to exercise.
 */
class TextMeasurementTest extends UnitTest {

    private record OvershootCase(
        String description,
        BiFunction<@Nullable Rectangle2D, Integer, Integer> method,
        @Nullable Rectangle2D lineBounds,
        int limit,
        int expected
    ) {
        @Override
        public String toString() {
            return description;
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("overshootCases")
    void testInkOvershoot(OvershootCase testCase) {
        assertThat(testCase.method().apply(testCase.lineBounds(), testCase.limit()))
            .isEqualTo(testCase.expected());
    }

    static Stream<OvershootCase> overshootCases() {
        var ascent = 10;
        var descent = 8;

        return Stream.of(
            new OvershootCase(
                "extraInkAbove is 0 for a null (blank) line",
                TextMeasurement::extraInkAbove, null, ascent, 0
            ),
            new OvershootCase(
                "extraInkAbove is 0 when the ink stays within the ascent",
                TextMeasurement::extraInkAbove,
                new Rectangle2D.Double(0, -ascent, 5, ascent), ascent, 0
            ),
            new OvershootCase(
                "extraInkAbove is the ceiling of the ink past the ascent",
                TextMeasurement::extraInkAbove,
                new Rectangle2D.Double(0, -13.2, 5, 13.2), ascent, 4
            ),
            new OvershootCase(
                "extraInkBelow is 0 for a null (blank) line",
                TextMeasurement::extraInkBelow, null, descent, 0
            ),
            new OvershootCase(
                "extraInkBelow is 0 when the ink stays within the descent",
                TextMeasurement::extraInkBelow,
                new Rectangle2D.Double(0, 0, 5, descent), descent, 0
            ),
            new OvershootCase(
                "extraInkBelow is the ceiling of the ink past the descent",
                TextMeasurement::extraInkBelow,
                new Rectangle2D.Double(0, 0, 5, 11.4), descent, 4
            )
        );
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 5})
    void testTextBlockHeightAddsLeadingOnlyBetweenLinesNeverBelowTheLastDescender(int lineCount) {
        var ascent = 10;
        var descent = 3;
        var leading = 2;
        var metrics = mock(FontMetrics.class);
        when(metrics.getAscent()).thenReturn(ascent);
        when(metrics.getDescent()).thenReturn(descent);
        when(metrics.getLeading()).thenReturn(leading);

        var expected = lineCount * (ascent + descent) + (lineCount - 1) * leading;

        assertThat(TextMeasurement.textBlockHeight(metrics, lineCount)).isEqualTo(expected);
    }
}
