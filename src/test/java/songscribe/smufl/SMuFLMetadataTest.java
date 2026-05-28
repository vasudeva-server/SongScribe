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
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;

/**
 * Direct-value tests for {@link SMuFLMetadata}.
 *
 * Expected bbox values are derived from bravura_metadata.json via BBox.fromSMuFL:
 *   noteheadBlack: bBoxSW=[0.0, -0.5], bBoxNE=[1.18, 0.5]
 *   → left=0.0, top=−0.5, right=1.18, bottom=0.5 (Y-down screen convention)
 *   → width=1.18, height=1.0
 *
 * These values are used as independent oracles, not derived from the production
 * accessors under test, so they can catch a wrong glyph mapping or parse regression.
 */
class SMuFLMetadataTest extends UnitTest {

    // -------------------------------------------------------------------------
    // Bravura noteheadBlack BBox constants (screen Y-down convention)
    //   Raw JSON: bBoxSW=[0.0, -0.5], bBoxNE=[1.18, 0.5]
    //   fromSMuFL: left=swX, top=-neY, right=neX, bottom=-swY
    // -------------------------------------------------------------------------
    private static final double NOTEHEAD_BLACK_EXPECTED_LEFT   = 0.0;
    private static final double NOTEHEAD_BLACK_EXPECTED_TOP    = -0.5;
    private static final double NOTEHEAD_BLACK_EXPECTED_RIGHT  = 1.18;
    private static final double NOTEHEAD_BLACK_EXPECTED_BOTTOM = 0.5;
    private static final double NOTEHEAD_BLACK_EXPECTED_WIDTH  = 1.18;
    private static final double NOTEHEAD_BLACK_EXPECTED_HEIGHT = 1.0;

    // Tolerance for floating-point comparisons
    private static final double TOLERANCE = 1e-9;

    // -------------------------------------------------------------------------
    // noteHeadWidthSs plausible-range bounds (staff spaces)
    //   A standard notehead is roughly 1.0–1.5 staff spaces wide in Bravura.
    //   The known JSON value is 1.18 ss, so these bounds are tight and justified.
    // -------------------------------------------------------------------------
    private static final double MIN_NOTEHEAD_WIDTH_SS  = 1.0;
    private static final double MAX_NOTEHEAD_WIDTH_SS  = 1.5;

    // -------------------------------------------------------------------------
    // noteHeadHeightSs plausible-range bounds (staff spaces)
    //   A standard notehead spans one space vertically (top to bottom = 1.0 ss).
    //   The known JSON value is 1.0 ss, so these bounds are tight and justified.
    // -------------------------------------------------------------------------
    private static final double MIN_NOTEHEAD_HEIGHT_SS = 0.8;
    private static final double MAX_NOTEHEAD_HEIGHT_SS = 1.2;

    // -------------------------------------------------------------------------
    // Row 23 — getBBox returns a BBox with correct concrete values for a known glyph
    //
    // This replaces the prior self-referential coverage (cross-package tests that
    // used requireBBox as their own oracle). Expected values are hard-coded from
    // the Bravura JSON, independent of any SMuFLMetadata accessor.
    // -------------------------------------------------------------------------

    @Test
    void testGetBBoxReturnsCorrectValuesForNoteheadBlack() {
        var bbox = SMuFLMetadata.getBBox(SMuFLGlyph.NOTEHEAD_BLACK);

        assertThat(bbox).isNotNull();

        if (bbox == null) {
            return;
        }

        assertThat(bbox.left()).isCloseTo(NOTEHEAD_BLACK_EXPECTED_LEFT, within(TOLERANCE));
        assertThat(bbox.top()).isCloseTo(NOTEHEAD_BLACK_EXPECTED_TOP, within(TOLERANCE));
        assertThat(bbox.right()).isCloseTo(NOTEHEAD_BLACK_EXPECTED_RIGHT, within(TOLERANCE));
        assertThat(bbox.bottom()).isCloseTo(NOTEHEAD_BLACK_EXPECTED_BOTTOM, within(TOLERANCE));
        assertThat(bbox.width()).isCloseTo(NOTEHEAD_BLACK_EXPECTED_WIDTH, within(TOLERANCE));
        assertThat(bbox.height()).isCloseTo(NOTEHEAD_BLACK_EXPECTED_HEIGHT, within(TOLERANCE));
    }

    // -------------------------------------------------------------------------
    // Row 26 — noteHeadWidthSs returns notehead width within a tight plausible range
    //
    // Verifies that the width is the known Bravura value (1.18 ss) and lies within
    // the expected range for a standard notehead in a professional SMuFL font.
    // -------------------------------------------------------------------------

    @Test
    void testNoteHeadWidthSsIsPositiveAndPlausible() {
        var width = SMuFLMetadata.noteHeadWidthSs();

        assertThat(width).isCloseTo(NOTEHEAD_BLACK_EXPECTED_WIDTH, within(TOLERANCE));
        assertThat(width)
            .as("noteHeadWidthSs should be within plausible range for a standard notehead")
            .isBetween(MIN_NOTEHEAD_WIDTH_SS, MAX_NOTEHEAD_WIDTH_SS);
    }

    // -------------------------------------------------------------------------
    // Row 27 — noteHeadHeightSs returns notehead height within a tight plausible range
    //
    // Verifies that the height is the known Bravura value (1.0 ss) and lies within
    // the expected range for a standard notehead in a professional SMuFL font.
    // -------------------------------------------------------------------------

    @Test
    void testNoteHeadHeightSsIsPositiveAndPlausible() {
        var height = SMuFLMetadata.noteHeadHeightSs();

        assertThat(height).isCloseTo(NOTEHEAD_BLACK_EXPECTED_HEIGHT, within(TOLERANCE));
        assertThat(height)
            .as("noteHeadHeightSs should be within plausible range for a standard notehead")
            .isBetween(MIN_NOTEHEAD_HEIGHT_SS, MAX_NOTEHEAD_HEIGHT_SS);
    }

    // -------------------------------------------------------------------------
    // Row 28 — getAnchors returns populated GlyphAnchors for a known glyph
    //
    // noteheadBlack has anchors in Bravura metadata, including stemUpSE and stemDownNW.
    // We assert that the returned object is non-null and that the key anchors are
    // populated (not null), confirming that the anchors were correctly parsed.
    // -------------------------------------------------------------------------

    @Test
    void testGetAnchorsReturnsAnchorsForKnownGlyph() {
        var anchors = SMuFLMetadata.getAnchors(SMuFLGlyph.NOTEHEAD_BLACK);

        assertThat(anchors).isNotNull();

        if (anchors == null) {
            return;
        }

        // noteheadBlack has stemUpSE and stemDownNW in Bravura metadata
        assertThat(anchors.stemUpSE())
            .as("noteheadBlack must have a stemUpSE anchor in Bravura metadata")
            .isNotNull();
        assertThat(anchors.stemDownNW())
            .as("noteheadBlack must have a stemDownNW anchor in Bravura metadata")
            .isNotNull();
    }

    // -------------------------------------------------------------------------
    // Row 29 — getAnchors returns null for a glyph absent from anchors data
    //
    // G_CLEF ("gClef") is present in glyphBBoxes but absent from glyphsWithAnchors
    // in bravura_metadata.json, making this the correct choice for the "no anchors"
    // branch. This is a concrete, metadata-verified absent-anchors case.
    // -------------------------------------------------------------------------

    @Test
    void testGetAnchorsReturnsNullForGlyphWithNoAnchors() {
        // G_CLEF has a bbox but no anchor entries in Bravura metadata
        var anchors = SMuFLMetadata.getAnchors(SMuFLGlyph.G_CLEF);

        assertThat(anchors).isNull();
    }
}
