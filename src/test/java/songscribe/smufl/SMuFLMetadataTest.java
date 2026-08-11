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

import java.util.EnumMap;

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

    // -------------------------------------------------------------------------
    // requireBBox fail-loud — exits fatally when bbox is absent
    //
    // requireMapValue is the package-private helper requireBBox/requireAdvanceWidth
    // delegate to; called directly here with a caller-supplied empty map so the
    // test is independent of which glyphs happen to be in Bravura metadata.
    // The UnitTest base class installs RuntimeErrorTestHelper, which replaces
    // System.exit with a handler that throws AssertionError.
    // -------------------------------------------------------------------------

    @Test
    void testRequireBBoxExitsWhenBBoxAbsent() {
        var emptyMap = new EnumMap<SMuFLGlyph, BBox>(SMuFLGlyph.class);

        assertThatThrownBy(
            () -> SMuFLMetadata.requireMapValue(emptyMap, SMuFLGlyph.NOTEHEAD_BLACK, "bounding box"))
            .isInstanceOf(AssertionError.class);
    }

    // -------------------------------------------------------------------------
    // requireAdvanceWidth fail-loud — exits fatally when advance width is absent
    // -------------------------------------------------------------------------

    @Test
    void testRequireAdvanceWidthExitsWhenAdvanceWidthAbsent() {
        var emptyMap = new EnumMap<SMuFLGlyph, Double>(SMuFLGlyph.class);

        assertThatThrownBy(
            () -> SMuFLMetadata.requireMapValue(emptyMap, SMuFLGlyph.ORNAMENT_TRILL, "advance width"))
            .isInstanceOf(AssertionError.class);
    }

    // -------------------------------------------------------------------------
    // Happy-path regression: requireBBox returns correct value for ACCIDENTAL_SHARP
    //
    // Bravura JSON: bBoxSW=[0.0, -1.392], bBoxNE=[0.996, 1.4]
    // fromSMuFL → left=0.0, top=-1.4, right=0.996, bottom=1.392, height=2.792
    // -------------------------------------------------------------------------
    private static final double ACCIDENTAL_SHARP_EXPECTED_HEIGHT = 2.792;

    @Test
    void testRequireBBoxHeightForAccidentalSharpMatchesBravura() {
        var height = SMuFLMetadata.requireBBox(SMuFLGlyph.ACCIDENTAL_SHARP).height();

        assertThat(height).isCloseTo(ACCIDENTAL_SHARP_EXPECTED_HEIGHT, within(TOLERANCE));
    }

    // -------------------------------------------------------------------------
    // Happy-path regression: requireAdvanceWidth returns correct value for ORNAMENT_TRILL
    //
    // Bravura JSON: glyphAdvanceWidths.ornamentTrill = 2.084
    // This pins the value used by TrillRenderer.TRILL_ADVANCE_WIDTH_SS.
    // -------------------------------------------------------------------------
    private static final double ORNAMENT_TRILL_EXPECTED_ADVANCE_WIDTH = 2.084;

    @Test
    void testRequireAdvanceWidthForOrnamentTrillMatchesBravura() {
        var width = SMuFLMetadata.requireAdvanceWidth(SMuFLGlyph.ORNAMENT_TRILL);

        assertThat(width).isCloseTo(ORNAMENT_TRILL_EXPECTED_ADVANCE_WIDTH, within(TOLERANCE));
    }

    // -------------------------------------------------------------------------
    // Happy-path regression: requireAdvanceWidth returns correct value for AUGMENTATION_DOT
    //
    // Bravura JSON: glyphAdvanceWidths.augmentationDot = 0.4
    // Augmentation dots are spaced center-to-center by one dot glyph plus a one-dot-width gap:
    //   DOT_SPACING_SS = 2 * requireAdvanceWidth(AUGMENTATION_DOT) = 0.8
    // -------------------------------------------------------------------------
    private static final double AUGMENTATION_DOT_EXPECTED_ADVANCE_WIDTH = 0.4;
    private static final double DOT_SPACING_SS_EXPECTED = AUGMENTATION_DOT_EXPECTED_ADVANCE_WIDTH * 2;

    @Test
    void testRequireAdvanceWidthForAugmentationDotMatchesBravura() {
        var width = SMuFLMetadata.requireAdvanceWidth(SMuFLGlyph.AUGMENTATION_DOT);

        assertThat(width).isCloseTo(AUGMENTATION_DOT_EXPECTED_ADVANCE_WIDTH, within(TOLERANCE));
    }

    @Test
    void testDotSpacingSsIsTwoDotWidths() {
        var dotSpacingSs = SMuFLMetadata.requireAdvanceWidth(SMuFLGlyph.AUGMENTATION_DOT) * 2;

        assertThat(dotSpacingSs).isCloseTo(DOT_SPACING_SS_EXPECTED, within(TOLERANCE));
    }

    // -------------------------------------------------------------------------
    // Row 30 — requireAnchors throws when glyph is absent from anchors map
    //
    // Uses requireMapValue directly with an empty EnumMap so the test is independent
    // of which glyphs happen to have anchors in Bravura metadata. The UnitTest base
    // class installs RuntimeErrorTestHelper, which replaces System.exit with a handler
    // that throws AssertionError — the same mechanism used by requireBBox/requireAdvanceWidth
    // tests above.
    // -------------------------------------------------------------------------

    @Test
    void testRequireAnchorsThrowsForAbsentGlyph() {
        var emptyMap = new EnumMap<SMuFLGlyph, GlyphAnchors>(SMuFLGlyph.class);

        assertThatThrownBy(
            () -> SMuFLMetadata.requireMapValue(emptyMap, SMuFLGlyph.NOTEHEAD_BLACK, "anchors"))
            .isInstanceOf(AssertionError.class);
    }

    // -------------------------------------------------------------------------
    // Row 31 — getAdvanceWidth returns the correct width for a known glyph
    //
    // Bravura JSON: glyphAdvanceWidths.noteheadBlack = 1.18
    // The expected value is the same independent oracle used for the BBox width
    // constant above, confirming the advance-width and bbox width agree for this
    // standard notehead.
    // -------------------------------------------------------------------------

    @Test
    void testGetAdvanceWidthReturnsValueForKnownGlyph() {
        var width = SMuFLMetadata.getAdvanceWidth(SMuFLGlyph.NOTEHEAD_BLACK);

        assertThat(width)
            .as("getAdvanceWidth must return a non-null value for noteheadBlack")
            .isNotNull();

        assertThat(width).isCloseTo(NOTEHEAD_BLACK_EXPECTED_WIDTH, within(TOLERANCE));
    }

    // -------------------------------------------------------------------------
    // Row 32 — getAdvanceWidth returns null for a glyph absent from advance widths
    //
    // All current SMuFLGlyph enum values happen to be present in Bravura's
    // glyphAdvanceWidths section. To exercise the null-return branch without
    // depending on external data, we use the package-private getAdvanceWidth(Map, SMuFLGlyph)
    // overload with a caller-supplied empty map, mirroring the pattern used for requireBBox above.
    // -------------------------------------------------------------------------

    @Test
    void testGetAdvanceWidthReturnsNullForAbsentGlyph() {
        var emptyMap = new EnumMap<SMuFLGlyph, Double>(SMuFLGlyph.class);

        var width = SMuFLMetadata.getAdvanceWidth(emptyMap, SMuFLGlyph.NOTEHEAD_BLACK);

        assertThat(width)
            .as("getAdvanceWidth must return null when the glyph has no advance-width entry")
            .isNull();
    }

    // -------------------------------------------------------------------------
    // Row 33 — getAdvanceWidthOrZero returns 0.0 when glyph is absent from advance widths
    //
    // Uses the package-private getAdvanceWidthOrZero(Map, SMuFLGlyph) overload with an
    // empty map to exercise the zero-fallback branch, which cannot be reached via the
    // public API because all current Bravura enum values have advance widths.
    // -------------------------------------------------------------------------

    @Test
    void testGetAdvanceWidthOrZeroReturnsFallbackForAbsentGlyph() {
        var emptyMap = new EnumMap<SMuFLGlyph, Double>(SMuFLGlyph.class);

        var width = SMuFLMetadata.getAdvanceWidthOrZero(emptyMap, SMuFLGlyph.NOTEHEAD_BLACK);

        assertThat(width)
            .as("getAdvanceWidthOrZero must return 0.0 when the glyph has no advance-width entry")
            .isEqualTo(0.0);
    }

    // -------------------------------------------------------------------------
    // Row 35 — getEngravingDefaults returns SMuFLData with plausible non-zero values
    //
    // Expected values are hard-coded from bravura_metadata.json engravingDefaults,
    // independent of any SMuFLMetadata accessor. This pins the parse and confirms
    // that the SMuFLData record is populated with the correct Bravura values.
    //
    // Bravura JSON values:
    //   beamThickness              = 0.5
    //   beamSpacing                = 0.25
    //   repeatBarlineDotSeparation = 0.16
    //   legerLineThickness         = 0.16
    //   legerLineExtension         = 0.4
    //   tieMidpointThickness       = 0.22
    // -------------------------------------------------------------------------
    private static final double EXPECTED_BEAM_THICKNESS               = 0.5;
    private static final double EXPECTED_BEAM_SPACING                 = 0.25;
    private static final double EXPECTED_REPEAT_BARLINE_DOT_SEPARATION = 0.16;
    private static final double EXPECTED_LEGER_LINE_THICKNESS         = 0.16;
    private static final double EXPECTED_LEGER_LINE_EXTENSION         = 0.4;
    private static final double EXPECTED_TIE_MIDPOINT_THICKNESS       = 0.22;

    @Test
    void testEngravingDefaultsAreNonZero() {
        var defaults = SMuFLMetadata.getEngravingDefaults();

        assertThat(defaults).isNotNull();
        assertThat(defaults.beamThickness())
            .as("beamThickness must match Bravura JSON value")
            .isCloseTo(EXPECTED_BEAM_THICKNESS, within(TOLERANCE));
        assertThat(defaults.beamSpacing())
            .as("beamSpacing must match Bravura JSON value")
            .isCloseTo(EXPECTED_BEAM_SPACING, within(TOLERANCE));
        assertThat(defaults.repeatBarlineDotSeparation())
            .as("repeatBarlineDotSeparation must match Bravura JSON value")
            .isCloseTo(EXPECTED_REPEAT_BARLINE_DOT_SEPARATION, within(TOLERANCE));
        assertThat(defaults.legerLineThickness())
            .as("legerLineThickness must match Bravura JSON value")
            .isCloseTo(EXPECTED_LEGER_LINE_THICKNESS, within(TOLERANCE));
        assertThat(defaults.legerLineExtension())
            .as("legerLineExtension must match Bravura JSON value")
            .isCloseTo(EXPECTED_LEGER_LINE_EXTENSION, within(TOLERANCE));
        assertThat(defaults.tieMidpointThickness())
            .as("tieMidpointThickness must match Bravura JSON value")
            .isCloseTo(EXPECTED_TIE_MIDPOINT_THICKNESS, within(TOLERANCE));
    }
}
