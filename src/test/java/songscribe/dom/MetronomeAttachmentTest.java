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

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;

/**
 * Tests for static utility methods on {@link MetronomeAttachment}.
 *
 * Row 22 — metronomeGlyphFor: 6 mapped note types + unmapped type returns null
 * Row 23 — dotAdvanceWidthSs: advance width × NOTE_SCALE
 * Row 24 — noteWidthSs: undotted, dotted, and unmapped (0)
 */
class MetronomeAttachmentTest extends UnitTest {

    // -------------------------------------------------------------------------
    // Row 22 — metronomeGlyphFor(ElementType): 6 notes + default null
    // -------------------------------------------------------------------------

    // Floating-point tolerance for double arithmetic involving float NOTE_SCALE.
    private static final double TOLERANCE = 1e-9;

    static Stream<Arguments> mappedNoteGlyphs() {
        return Stream.of(
            Arguments.of(ElementType.SEMIBREVE,       SMuFLGlyph.MET_NOTE_WHOLE),
            Arguments.of(ElementType.MINIM,            SMuFLGlyph.MET_NOTE_HALF_UP),
            Arguments.of(ElementType.CROTCHET,         SMuFLGlyph.MET_NOTE_QUARTER_UP),
            Arguments.of(ElementType.QUAVER,           SMuFLGlyph.MET_NOTE_8TH_UP),
            Arguments.of(ElementType.SEMIQUAVER,       SMuFLGlyph.MET_NOTE_16TH_UP),
            Arguments.of(ElementType.DEMI_SEMIQUAVER,  SMuFLGlyph.MET_NOTE_32ND_UP)
        );
    }

    @ParameterizedTest(name = "{0} → {1}")
    @MethodSource("mappedNoteGlyphs")
    void testMetronomeGlyphForReturnsMappedGlyph(ElementType type, SMuFLGlyph expectedGlyph) {
        assertThat(MetronomeAttachment.metronomeGlyphFor(type)).isEqualTo(expectedGlyph);
    }

    @Test
    void testMetronomeGlyphForReturnsNullForUnmappedType() {
        // SINGLE_BARLINE has no metronome glyph counterpart.
        assertThat(MetronomeAttachment.metronomeGlyphFor(ElementType.SINGLE_BARLINE)).isNull();
    }

    // -------------------------------------------------------------------------
    // Row 23 — dotAdvanceWidthSs: bbox advance × NOTE_SCALE
    // -------------------------------------------------------------------------

    @Test
    void testDotAdvanceWidthSsEqualsAugmentationDotAdvanceTimesNoteScale() {
        // Compute the expected value independently from the same two primitives:
        // the raw advance width from font metadata and the design scale constant.
        // This avoids calling dotAdvanceWidthSs() as its own oracle.
        var expectedDotAdvanceWidthSs =
            SMuFLMetadata.requireAdvanceWidth(SMuFLGlyph.MET_AUGMENTATION_DOT)
                * MetronomeAttachment.NOTE_SCALE;

        assertThat(MetronomeAttachment.dotAdvanceWidthSs())
            .isCloseTo(expectedDotAdvanceWidthSs, within(TOLERANCE));
    }

    // -------------------------------------------------------------------------
    // Row 24 — noteWidthSs: undotted, dotted, and unmapped → 0
    // -------------------------------------------------------------------------

    @Test
    void testNoteWidthSsForUndottedNoteEqualsGlyphAdvanceTimesNoteScale() {
        var undottedCrotchet = new StaffElement(ElementType.CROTCHET);
        // dotCount defaults to 0 — undotted

        var expectedWidthSs =
            SMuFLMetadata.requireAdvanceWidth(SMuFLGlyph.MET_NOTE_QUARTER_UP)
                * MetronomeAttachment.NOTE_SCALE;

        assertThat(MetronomeAttachment.noteWidthSs(undottedCrotchet))
            .isCloseTo(expectedWidthSs, within(TOLERANCE));
    }

    @Test
    void testNoteWidthSsForDottedNoteAddsDoubleDotAdvance() {
        var dottedCrotchet = new StaffElement(ElementType.CROTCHET);
        dottedCrotchet.setDotCount(1);

        var noteAdvanceSs =
            SMuFLMetadata.requireAdvanceWidth(SMuFLGlyph.MET_NOTE_QUARTER_UP)
                * MetronomeAttachment.NOTE_SCALE;
        var expectedWidthSs = noteAdvanceSs + 2 * MetronomeAttachment.dotAdvanceWidthSs();

        assertThat(MetronomeAttachment.noteWidthSs(dottedCrotchet))
            .isCloseTo(expectedWidthSs, within(TOLERANCE));
    }

    @Test
    void testNoteWidthSsIsZeroForUnmappedType() {
        // SINGLE_BARLINE has no metronome glyph, so width must be 0.
        var barline = new StaffElement(ElementType.SINGLE_BARLINE);

        assertThat(MetronomeAttachment.noteWidthSs(barline)).isEqualTo(0);
    }
}
