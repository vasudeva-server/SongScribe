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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SMuFLMetadataTest {

    private final SMuFLMetadata metadata = SMuFLMetadata.getInstance();

    // --- Singleton loading ---

    @Test
    void testSingletonLoads() {
        assertNotNull(metadata);
        assertSame(metadata, SMuFLMetadata.getInstance());
    }

    // --- Engraving defaults ---

    @Test
    void testEngravingDefaultsLoaded() {
        var defaults = metadata.getEngravingDefaults();
        assertNotNull(defaults);
        assertEquals(0.13, defaults.staffLineThickness(), 1e-9);
        assertEquals(0.12, defaults.stemThickness(), 1e-9);
        assertEquals(0.5, defaults.beamThickness(), 1e-9);
        assertEquals(0.25, defaults.beamSpacing(), 1e-9);
        assertEquals(0.16, defaults.thinBarlineThickness(), 1e-9);
        assertEquals(0.5, defaults.thickBarlineThickness(), 1e-9);
        assertEquals(0.16, defaults.hairpinThickness(), 1e-9);
    }

    // --- Bounding boxes ---

    @Test
    void testBBoxNoteheadBlack() {
        var bbox = metadata.getBBox(SMuFLGlyph.NOTEHEAD_BLACK);
        assertNotNull(bbox);
        // SMuFL metadata: bBoxSW=[0.0, -0.5], bBoxNE=[1.18, 0.5]
        // After Y-flip: left=0.0, top=-0.5, right=1.18, bottom=0.5
        assertEquals(0.0, bbox.left(), 1e-9);
        assertEquals(-0.5, bbox.top(), 1e-9);
        assertEquals(1.18, bbox.right(), 1e-9);
        assertEquals(0.5, bbox.bottom(), 1e-9);
    }

    @Test
    void testBBoxWidthAndHeight() {
        var bbox = metadata.getBBox(SMuFLGlyph.NOTEHEAD_BLACK);
        assertNotNull(bbox);
        assertEquals(1.18, bbox.width(), 1e-9);
        assertEquals(1.0, bbox.height(), 1e-9);
    }

    @Test
    void testBBoxGClef() {
        var bbox = metadata.getBBox(SMuFLGlyph.G_CLEF);
        assertNotNull(bbox);
        // SMuFL metadata: bBoxNE=[2.684, 4.392], bBoxSW=[0.0, -2.632]
        // After Y-flip: left=0.0, top=-4.392, right=2.684, bottom=2.632
        assertEquals(0.0, bbox.left(), 1e-9);
        assertEquals(-4.392, bbox.top(), 1e-9);
        assertEquals(2.684, bbox.right(), 1e-9);
        assertEquals(2.632, bbox.bottom(), 1e-9);
    }

    @Test
    void testBBoxYFlipFromSMuFL() {
        // Verify the Y-flip factory method directly
        var bbox = BBox.fromSMuFL(0.0, -0.5, 1.18, 0.5);
        assertEquals(0.0, bbox.left(), 1e-9);
        assertEquals(-0.5, bbox.top(), 1e-9);
        assertEquals(1.18, bbox.right(), 1e-9);
        assertEquals(0.5, bbox.bottom(), 1e-9);
    }

    // --- Anchors ---

    @Test
    void testAnchorsNoteheadBlack() {
        var anchors = metadata.getAnchors(SMuFLGlyph.NOTEHEAD_BLACK);
        assertNotNull(anchors);

        // stemUpSE: [1.18, 0.168] -> Y-flipped: (1.18, -0.168)
        assertNotNull(anchors.stemUpSE());
        assertEquals(1.18, anchors.stemUpSE().x(), 1e-9);
        assertEquals(-0.168, anchors.stemUpSE().y(), 1e-9);

        // stemDownNW: [0.0, -0.168] -> Y-flipped: (0.0, 0.168)
        assertNotNull(anchors.stemDownNW());
        assertEquals(0.0, anchors.stemDownNW().x(), 1e-9);
        assertEquals(0.168, anchors.stemDownNW().y(), 1e-9);

        // cutOutNW: [0.208, 0.3] -> Y-flipped: (0.208, -0.3)
        assertNotNull(anchors.cutOutNW());
        assertEquals(0.208, anchors.cutOutNW().x(), 1e-9);
        assertEquals(-0.3, anchors.cutOutNW().y(), 1e-9);

        // cutOutSE: [0.94, -0.296] -> Y-flipped: (0.94, 0.296)
        assertNotNull(anchors.cutOutSE());
        assertEquals(0.94, anchors.cutOutSE().x(), 1e-9);
        assertEquals(0.296, anchors.cutOutSE().y(), 1e-9);
    }

    @Test
    void testAnchorsNullForGlyphWithoutAnchors() {
        // restQuarter should not have stem anchors
        var anchors = metadata.getAnchors(SMuFLGlyph.REST_QUARTER);
        assertNull(anchors);
    }

    @Test
    void testAnchorYFlipFromSMuFL() {
        var anchor = GlyphAnchors.Anchor.fromSMuFL(1.18, 0.168);
        assertEquals(1.18, anchor.x(), 1e-9);
        assertEquals(-0.168, anchor.y(), 1e-9);
    }

    // --- Advance widths ---

    @Test
    void testAdvanceWidthNoteheadBlack() {
        var width = metadata.getAdvanceWidth(SMuFLGlyph.NOTEHEAD_BLACK);
        assertNotNull(width);
        assertEquals(1.18, width, 1e-9);
    }

    @Test
    void testAdvanceWidthGClef() {
        var width = metadata.getAdvanceWidth(SMuFLGlyph.G_CLEF);
        assertNotNull(width);
        assertEquals(2.684, width, 1e-9);
    }

    // --- Staff space conversion ---

    @Test
    void testStaffSpacesToPixels() {
        assertEquals(8.0, StaffSpaces.toPixels(1.0), 1e-9);
        assertEquals(16.0, StaffSpaces.toPixels(2.0), 1e-9);
        assertEquals(4.0, StaffSpaces.toPixels(0.5), 1e-9);
    }

    @Test
    void testPixelsToStaffSpaces() {
        assertEquals(1.0, StaffSpaces.fromPixels(8.0), 1e-9);
        assertEquals(2.0, StaffSpaces.fromPixels(16.0), 1e-9);
        assertEquals(0.5, StaffSpaces.fromPixels(4.0), 1e-9);
    }

    @Test
    void testRoundTripConversion() {
        double original = 3.75;
        assertEquals(original, StaffSpaces.fromPixels(StaffSpaces.toPixels(original)), 1e-9);
    }

    // --- SMuFLGlyph enum ---

    @Test
    void testGlyphSmuflName() {
        assertEquals("noteheadBlack", SMuFLGlyph.NOTEHEAD_BLACK.smuflName());
        assertEquals("gClef", SMuFLGlyph.G_CLEF.smuflName());
        assertEquals("augmentationDot", SMuFLGlyph.AUGMENTATION_DOT.smuflName());
    }

    @Test
    void testGlyphCodepoint() {
        assertEquals('\uE0A4', SMuFLGlyph.NOTEHEAD_BLACK.codepoint());
        assertEquals('\uE050', SMuFLGlyph.G_CLEF.codepoint());
    }

    @Test
    void testGlyphAsString() {
        assertEquals("\uE0A4", SMuFLGlyph.NOTEHEAD_BLACK.asString());
    }

    // --- Coverage: all enum glyphs have metadata ---

    @Test
    void testAllGlyphsHaveAdvanceWidths() {
        for (var glyph : SMuFLGlyph.values()) {
            assertNotNull(metadata.getAdvanceWidth(glyph),
                    "Missing advance width for " + glyph.smuflName());
        }
    }

    @Test
    void testAllGlyphsHaveBBoxes() {
        for (var glyph : SMuFLGlyph.values()) {
            assertNotNull(metadata.getBBox(glyph),
                    "Missing bounding box for " + glyph.smuflName());
        }
    }
}
