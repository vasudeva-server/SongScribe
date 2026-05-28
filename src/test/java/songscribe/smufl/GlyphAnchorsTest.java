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

package songscribe.smufl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;

class GlyphAnchorsTest extends UnitTest {

    // anchor coordinate fixtures
    private static final double ANCHOR_X = 0.1328;
    private static final double ANCHOR_Y = -0.168;

    // fromSMuFL Y-flip fixture — SMuFL Y-up value
    private static final double SMUFL_X         = 0.25;
    private static final double SMUFL_Y         = 0.75;
    private static final double EXPECTED_FLIPPED_Y = -SMUFL_Y;

    private static final double TOLERANCE = 1e-9;

    // -------------------------------------------------------------------------
    // Row 13 — requireStemUpSE returns anchor when present
    // -------------------------------------------------------------------------

    @Test
    void testRequireStemUpSEReturnsAnchorWhenPresent() {
        var anchor = new GlyphAnchors.Anchor(ANCHOR_X, ANCHOR_Y);
        var anchors = new GlyphAnchors(anchor, null, null, null);

        var result = anchors.requireStemUpSE();

        assertThat(result.x()).isCloseTo(ANCHOR_X, within(TOLERANCE));
        assertThat(result.y()).isCloseTo(ANCHOR_Y, within(TOLERANCE));
    }

    // -------------------------------------------------------------------------
    // Row 14 — requireStemUpSE throws when stemUpSE is null
    // -------------------------------------------------------------------------

    @Test
    void testRequireStemUpSEThrowsWhenNull() {
        var anchors = new GlyphAnchors(null, null, null, null);

        assertThatThrownBy(anchors::requireStemUpSE)
            .isInstanceOf(AssertionError.class);
    }

    // -------------------------------------------------------------------------
    // Row 15 — requireStemDownNW returns anchor when present
    // -------------------------------------------------------------------------

    @Test
    void testRequireStemDownNWReturnsAnchorWhenPresent() {
        var anchor = new GlyphAnchors.Anchor(ANCHOR_X, ANCHOR_Y);
        var anchors = new GlyphAnchors(null, anchor, null, null);

        var result = anchors.requireStemDownNW();

        assertThat(result.x()).isCloseTo(ANCHOR_X, within(TOLERANCE));
        assertThat(result.y()).isCloseTo(ANCHOR_Y, within(TOLERANCE));
    }

    // -------------------------------------------------------------------------
    // Row 16 — requireStemDownNW throws when stemDownNW is null
    // -------------------------------------------------------------------------

    @Test
    void testRequireStemDownNWThrowsWhenNull() {
        var anchors = new GlyphAnchors(null, null, null, null);

        assertThatThrownBy(anchors::requireStemDownNW)
            .isInstanceOf(AssertionError.class);
    }

    // -------------------------------------------------------------------------
    // Row 17 — Anchor.fromSMuFL flips Y (y becomes −y)
    // -------------------------------------------------------------------------

    @Test
    void testAnchorFromSmuflFlipsY() {
        var anchor = GlyphAnchors.Anchor.fromSMuFL(SMUFL_X, SMUFL_Y);

        assertThat(anchor.x()).isCloseTo(SMUFL_X, within(TOLERANCE));
        assertThat(anchor.y()).isCloseTo(EXPECTED_FLIPPED_Y, within(TOLERANCE));
    }
}
