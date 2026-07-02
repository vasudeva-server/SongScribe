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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;

/**
 * Tests for {@link ArticulationType} — MIDI duration override logic.
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
}
