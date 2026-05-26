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

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;

/**
 * Tests default values and constant relationships for {@link Annotation}.
 */
class AnnotationTest extends UnitTest {

    // -------------------------------------------------------------------------
    // Row 7 — ABOVE/BELOW constants: ABOVE < 0, BELOW > 0, BELOW > |ABOVE|
    //
    // ABOVE = (int) ssToPx(-2.0): -2 ss places the annotation above the staff
    // centre, so the px value should be negative in the coordinate system where
    // positive Y is down.
    // BELOW = (int) ssToPx(4.0): 4 ss is twice the magnitude of ABOVE, placing
    // text further from the staff when appearing below.
    // -------------------------------------------------------------------------

    @Test
    void testAboveIsNegative() {
        assertThat(Annotation.ABOVE).isLessThan(0);
    }

    @Test
    void testBelowIsPositive() {
        assertThat(Annotation.BELOW).isGreaterThan(0);
    }

    @Test
    void testBelowMagnitudeExceedsAboveMagnitude() {
        // BELOW derives from ssToPx(4.0) and ABOVE from ssToPx(-2.0), so
        // |BELOW| should be strictly greater than |ABOVE|.
        assertThat(Annotation.BELOW).isGreaterThan(-Annotation.ABOVE);
    }

    // -------------------------------------------------------------------------
    // Row 9 — yPosPx defaults to ABOVE
    // -------------------------------------------------------------------------

    @Test
    void testYPosPxDefaultsToAbove() {
        var annotation = new Annotation("test");
        assertThat(annotation.getYPosPx()).isEqualTo(Annotation.ABOVE);
    }

}
