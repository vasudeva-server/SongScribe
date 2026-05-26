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

package songscribe.dom;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;

/**
 * Tests for {@link ArticulationType} — MIDI duration override logic and
 * drawing-order contract.
 */
class ArticulationTypeTest extends UnitTest {

    // Documented MIDI duration percentage for STACCATO articulation.
    private static final int STACCATO_MIDI_DURATION_PERCENT = 33;

    // Sentinel value meaning "no MIDI duration override" (maps to ACCENT's percent).
    private static final int NO_MIDI_DURATION_OVERRIDE = -1;

    // -------------------------------------------------------------------------
    // Row 8 — getMidiDurationPercent() returns the documented value per constant
    // -------------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class GetMidiDurationPercent {

        @Test
        void testAccentReturnsMinus1() {
            assertThat(ArticulationType.ACCENT.getMidiDurationPercent())
                .isEqualTo(NO_MIDI_DURATION_OVERRIDE);
        }

        @Test
        void testStaccatoReturns33() {
            assertThat(ArticulationType.STACCATO.getMidiDurationPercent())
                .isEqualTo(STACCATO_MIDI_DURATION_PERCENT);
        }
    }

    // -------------------------------------------------------------------------
    // Row 9 — hasMidiDurationOverride(): STACCATO true, ACCENT false
    // -------------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class HasMidiDurationOverride {

        @Test
        void testAccentReturnsFalse() {
            assertThat(ArticulationType.ACCENT.hasMidiDurationOverride()).isFalse();
        }

        @Test
        void testStaccatoReturnsTrue() {
            assertThat(ArticulationType.STACCATO.hasMidiDurationOverride()).isTrue();
        }
    }

    // -------------------------------------------------------------------------
    // Row 10 — getDrawingOrder(false) returns types in ascending enum order
    // -------------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class GetDrawingOrderStemDown {

        @Test
        void testDrawingOrderAscendsWithEnumOrdinal() {
            var order = ArticulationType.getDrawingOrder(false);

            // Each element must appear at or after the previous one in enum declaration order.
            for (var i = 0; i < order.length - 1; i++) {
                assertThat(order[i].ordinal())
                    .describedAs("drawing order index %d (%s) ordinal must be less than index %d (%s) ordinal",
                        i, order[i], i + 1, order[i + 1])
                    .isLessThan(order[i + 1].ordinal());
            }
        }

        @Test
        void testDrawingOrderContainsAllTypes() {
            var order = ArticulationType.getDrawingOrder(false);
            assertThat(order).containsExactlyInAnyOrder(ArticulationType.values());
        }
    }

    // -------------------------------------------------------------------------
    // Row 11 — getDrawingOrder(true) is the reverse of getDrawingOrder(false)
    // -------------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class GetDrawingOrderStemUp {

        @Test
        void testDrawingOrderStemUpIsReversalOfStemDown() {
            var stemDown = ArticulationType.getDrawingOrder(false);
            var stemUp = ArticulationType.getDrawingOrder(true);

            // Reverse stemDown and compare element-by-element with stemUp.
            var reversedStemDown = Arrays.copyOf(stemDown, stemDown.length);

            for (var i = 0; i < reversedStemDown.length; i++) {
                reversedStemDown[i] = stemDown[stemDown.length - 1 - i];
            }

            assertThat(stemUp).containsExactly(reversedStemDown);
        }
    }
}
