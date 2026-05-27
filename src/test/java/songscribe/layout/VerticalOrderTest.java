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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static songscribe.layout.VerticalOrder.*;

class VerticalOrderTest extends UnitTest {

    // -----------------------------------------------------------------------
    // Row 34: isAboveStaff / isBelowStaff relative to NOTE_STEM.order
    // -----------------------------------------------------------------------

    @Nested
    class AboveAndBelowStaff {

        @Test
        void testElementsAboveStaffAreAboveAndNotBelow() {
            assertAll(
                // all seven above-staff constants
                () -> assertThat(TEMPO.isAboveStaff()).isTrue(),
                () -> assertThat(TEMPO.isBelowStaff()).isFalse(),
                () -> assertThat(BEAT_CHANGE.isAboveStaff()).isTrue(),
                () -> assertThat(BEAT_CHANGE.isBelowStaff()).isFalse(),
                () -> assertThat(ENDINGS.isAboveStaff()).isTrue(),
                () -> assertThat(ENDINGS.isBelowStaff()).isFalse(),
                () -> assertThat(ANNOTATIONS_ABOVE.isAboveStaff()).isTrue(),
                () -> assertThat(ANNOTATIONS_ABOVE.isBelowStaff()).isFalse(),
                () -> assertThat(TRILL.isAboveStaff()).isTrue(),
                () -> assertThat(TRILL.isBelowStaff()).isFalse(),
                () -> assertThat(RANGE_ELEMENTS_ABOVE.isAboveStaff()).isTrue(),
                () -> assertThat(RANGE_ELEMENTS_ABOVE.isBelowStaff()).isFalse(),
                () -> assertThat(ARTICULATIONS_STEM_DOWN.isAboveStaff()).isTrue(),
                () -> assertThat(ARTICULATIONS_STEM_DOWN.isBelowStaff()).isFalse()
            );
        }

        @Test
        void testNoteStemIsNeitherAboveNorBelowStaff() {
            assertAll(
                () -> assertThat(NOTE_STEM.isAboveStaff()).isFalse(),
                () -> assertThat(NOTE_STEM.isBelowStaff()).isFalse()
            );
        }

        @Test
        void testElementsBelowStaffAreBelowAndNotAbove() {
            assertAll(
                // all five below-staff constants
                () -> assertThat(ARTICULATIONS_STEM_UP.isBelowStaff()).isTrue(),
                () -> assertThat(ARTICULATIONS_STEM_UP.isAboveStaff()).isFalse(),
                () -> assertThat(RANGE_ELEMENTS_BELOW.isBelowStaff()).isTrue(),
                () -> assertThat(RANGE_ELEMENTS_BELOW.isAboveStaff()).isFalse(),
                () -> assertThat(DYNAMICS.isBelowStaff()).isTrue(),
                () -> assertThat(DYNAMICS.isAboveStaff()).isFalse(),
                () -> assertThat(LYRICS.isBelowStaff()).isTrue(),
                () -> assertThat(LYRICS.isAboveStaff()).isFalse(),
                () -> assertThat(TRANSLATION.isBelowStaff()).isTrue(),
                () -> assertThat(TRANSLATION.isAboveStaff()).isFalse()
            );
        }
    }

    // -----------------------------------------------------------------------
    // Row 35: compareByOrder — <0 / >0 / 0 cases
    // -----------------------------------------------------------------------

    @Test
    void testCompareByOrderReturnsNegativeWhenThisIsHigherOnPage() {
        // TEMPO (order 1) is drawn above LYRICS (order 12): compare returns negative
        assertThat(TEMPO.compareByOrder(LYRICS)).isNegative();
    }

    @Test
    void testCompareByOrderReturnsPositiveWhenThisIsLowerOnPage() {
        // LYRICS (order 12) is drawn below TEMPO (order 1): compare returns positive
        assertThat(LYRICS.compareByOrder(TEMPO)).isPositive();
    }

    @Test
    void testCompareByOrderReturnsZeroForSameElement() {
        assertThat(TEMPO.compareByOrder(TEMPO)).isZero();
    }
}
