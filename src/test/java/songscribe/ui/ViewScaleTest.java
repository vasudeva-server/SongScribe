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
package songscribe.ui;

import java.util.function.BiFunction;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;
import songscribe.dom.DocPx;
import songscribe.dom.Ss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * {@link ViewScale}'s conversions across the view boundary: a distance converted to view
 * pixels and back names the same distance it started as, at every zoom the user can select.
 * Mouse-position arithmetic runs both directions of that trip on every click and drag.
 */
class ViewScaleTest extends UnitTest {

    /**
     * Distances the round trip is asserted over, in staff spaces or document pixels
     * according to the case. A zero, a distance below one unit, whole units and a
     * distance with a fractional part large enough to be lost by any rounding.
     */
    private static final double[] SAMPLE_DISTANCES = { 0, 0.1, 0.5, 1, 7.25, 123.75 };

    /**
     * Slack for the two floating-point multiplications the round trip performs. The
     * conversions promise the distance back, not the same bit pattern back.
     */
    private static final double MAX_ROUND_TRIP_ERROR = 1e-9;

    private record RoundTripCase(String description, BiFunction<ViewScale, Double, Double> roundTrip) {
        @Override
        public String toString() {
            return description;
        }
    }

    @ParameterizedTest(name = "{0} at {1}%")
    @MethodSource("roundTripsAtEveryZoomLevel")
    void testDistanceIsRecoveredFromViewPixels(RoundTripCase testCase, int zoomPercent) {
        var viewScale = ViewScale.IDENTITY.withZoomPercent(zoomPercent);

        for (var distance : SAMPLE_DISTANCES) {
            assertThat(testCase.roundTrip().apply(viewScale, distance))
                .as("%s of %s at %d%%", testCase.description(), distance, zoomPercent)
                .isCloseTo(distance, within(MAX_ROUND_TRIP_ERROR));
        }
    }

    static Stream<Arguments> roundTripsAtEveryZoomLevel() {
        var cases = Stream.of(
            new RoundTripCase(
                "staff spaces recovered",
                (viewScale, distance) -> viewScale.toSs(viewScale.toViewPx(new Ss(distance))).value()
            ),
            new RoundTripCase(
                "document pixels recovered",
                (viewScale, distance) -> viewScale.toDocPx(viewScale.toViewPx(new DocPx(distance))).value()
            )
        );

        return cases.flatMap(testCase ->
            ZoomController.ZOOM_LEVEL_PERCENTS.stream().map(zoomPercent -> Arguments.of(testCase, zoomPercent))
        );
    }
}
