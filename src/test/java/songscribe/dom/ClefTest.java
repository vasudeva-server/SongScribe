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
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;

/**
 * Tests for {@link Clef} — getContentWidthPx and getContentHeightPx delegate
 * to ScaleContext.ssToPx with G_CLEF bbox dimensions.
 */
class ClefTest extends UnitTest {

    // Pixels-per-staff-space used in all tests — chosen to be non-trivial and
    // distinct from the production default (8.0) so a forgotten reset surfaces
    // in the right test rather than silently passing.
    private static final double TEST_PPS = 12.5;

    // Tolerance for floating-point comparisons; exact arithmetic is expected
    // but representation noise warrants a tiny epsilon.
    private static final double DOUBLE_EPSILON = 1e-9;

    @BeforeEach
    void setUp() {
        ScaleContext.setPixelsPerStaffSpace(TEST_PPS);
    }

    @AfterEach
    void tearDown() {
        ScaleContext.setPixelsPerStaffSpace(ScaleContext.DEFAULT_PIXELS_PER_STAFF_SPACE);
    }

    // -------------------------------------------------------------------------
    // Row 54 — getContentWidthPx / getContentHeightPx route through ssToPx
    //          with the G_CLEF bbox dimensions
    // -------------------------------------------------------------------------

    @Test
    void testGetContentWidthPxEqualsGClefBboxWidthTimesPps() {
        var bbox = SMuFLMetadata.requireBBox(SMuFLGlyph.G_CLEF);
        var expectedPx = bbox.width() * TEST_PPS;

        assertThat(new Clef().getContentWidthPx())
            .isCloseTo(expectedPx, within(DOUBLE_EPSILON));
    }

    @Test
    void testGetContentHeightPxEqualsGClefBboxHeightTimesPps() {
        var bbox = SMuFLMetadata.requireBBox(SMuFLGlyph.G_CLEF);
        var expectedPx = bbox.height() * TEST_PPS;

        assertThat(new Clef().getContentHeightPx())
            .isCloseTo(expectedPx, within(DOUBLE_EPSILON));
    }
}
