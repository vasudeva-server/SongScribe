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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.ArrayList;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.ElementType;
import songscribe.dom.StaffElement;
import songscribe.dom.StaffElement.Accidental;
import songscribe.smufl.Engraving;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;

class NoteGeometryTest extends UnitTest {

    @BeforeAll
    static void initAccidentalWidths() {
        NoteGeometry.initializeAccidentalWidths();
    }

    private static StaffElement crotchetWithAccidental(Accidental accidental) {
        var note = ElementType.CROTCHET.newInstance();
        note.setAccidental(accidental);
        return note;
    }

    @SuppressWarnings("NullAway")
    private static <T> T require(@Nullable T value, String description) {
        assertThat(value).describedAs(description).isNotNull();
        return value;
    }

    // -----------------------------------------------------------------------
    // Row 22: getAccidentalWidthSs — dispatches small/base/parens; 0 for none
    // -----------------------------------------------------------------------

    @Nested
    class AccidentalWidth {

        @Test
        void testNoAccidentalReturnsZero() {
            var note = ElementType.CROTCHET.newInstance();
            assertThat(NoteGeometry.getAccidentalWidthSs(note)).isEqualTo(0f);
        }

        @Test
        void testSharpWidthMatchesSmuflAdvance() {
            final float expectedWidthSs = (float) SMuFLMetadata.getAdvanceWidthOrZero(SMuFLGlyph.ACCIDENTAL_SHARP);
            assertThat(NoteGeometry.getAccidentalWidthSs(crotchetWithAccidental(Accidental.SHARP)))
                .isEqualTo(expectedWidthSs);
        }

        @Test
        void testDoubleSharpWidthMatchesSmuflAdvance() {
            // DOUBLE_SHARP is a single-glyph accidental — width equals its advance width exactly
            final float expectedWidthSs = (float) SMuFLMetadata.getAdvanceWidthOrZero(SMuFLGlyph.ACCIDENTAL_DOUBLE_SHARP);
            assertThat(NoteGeometry.getAccidentalWidthSs(crotchetWithAccidental(Accidental.DOUBLE_SHARP)))
                .isEqualTo(expectedWidthSs);
        }

        @Test
        void testGraceNoteUsesSmallGlyphWidth() {
            final float expectedWidthSs = (float) SMuFLMetadata.getAdvanceWidthOrZero(SMuFLGlyph.ACCIDENTAL_SHARP_SMALL);
            var graceNote = ElementType.GRACE_QUAVER.newInstance();
            graceNote.setAccidental(Accidental.SHARP);
            assertThat(NoteGeometry.getAccidentalWidthSs(graceNote)).isEqualTo(expectedWidthSs);
        }

        @Test
        void testParenthesizedDoubleSharpAddsParenWidths() {
            // DOUBLE_SHARP has zero parenthesis kerning, so total = base + left-paren + right-paren
            final float baseWidthSs = (float) SMuFLMetadata.getAdvanceWidthOrZero(SMuFLGlyph.ACCIDENTAL_DOUBLE_SHARP);
            final float parenLeftWidthSs = (float) SMuFLMetadata.getAdvanceWidthOrZero(SMuFLGlyph.ACCIDENTAL_PARENS_LEFT);
            final float parenRightWidthSs = (float) SMuFLMetadata.getAdvanceWidthOrZero(SMuFLGlyph.ACCIDENTAL_PARENS_RIGHT);
            final float expectedWidthSs = baseWidthSs + parenLeftWidthSs + parenRightWidthSs;

            var note = crotchetWithAccidental(Accidental.DOUBLE_SHARP);
            note.setAccidentalInParentheses(true);
            assertThat(NoteGeometry.getAccidentalWidthSs(note)).isEqualTo(expectedWidthSs);
        }
    }

    // -----------------------------------------------------------------------
    // Row 23: getAccidentalBoundsSs — null / grace-null / exact table lookup
    // -----------------------------------------------------------------------

    @Nested
    class AccidentalBoundsExact {

        @Test
        void testNullForNoAccidental() {
            assertThat(NoteGeometry.getAccidentalBoundsSs(ElementType.CROTCHET.newInstance())).isNull();
        }

        @Test
        void testNullForGraceNote() {
            var graceNote = ElementType.GRACE_QUAVER.newInstance();
            graceNote.setAccidental(Accidental.SHARP);
            assertThat(NoteGeometry.getAccidentalBoundsSs(graceNote)).isNull();
        }

        @Test
        void testSharpBoundsExact() {
            final float sharpAdvanceWidthSs = (float) SMuFLMetadata.getAdvanceWidthOrZero(SMuFLGlyph.ACCIDENTAL_SHARP);
            final float startXSs = -NoteGeometry.ACCIDENTAL_PADDING_SS - sharpAdvanceWidthSs;
            var sharpBBox = require(SMuFLMetadata.getBBox(SMuFLGlyph.ACCIDENTAL_SHARP), "ACCIDENTAL_SHARP bbox");

            final double expectedLeftSs = sharpBBox.left() + startXSs;
            final double expectedWidthSs = sharpBBox.width();
            final double expectedTopSs = sharpBBox.top();
            final double expectedBotSs = sharpBBox.bottom();

            var bounds = require(
                NoteGeometry.getAccidentalBoundsSs(crotchetWithAccidental(Accidental.SHARP)),
                "sharp bounds");

            assertAll(
                () -> assertThat(bounds.leftSs()).isEqualTo(expectedLeftSs),
                () -> assertThat(bounds.widthSs()).isEqualTo(expectedWidthSs),
                () -> assertThat(bounds.topSs()).isEqualTo(expectedTopSs),
                () -> assertThat(bounds.botSs()).isEqualTo(expectedBotSs)
            );
        }

        @Test
        void testDoubleSharpBoundsExact() {
            final float dsAdvanceWidthSs = (float) SMuFLMetadata.getAdvanceWidthOrZero(SMuFLGlyph.ACCIDENTAL_DOUBLE_SHARP);
            final float startXSs = -NoteGeometry.ACCIDENTAL_PADDING_SS - dsAdvanceWidthSs;
            var dsBBox = require(SMuFLMetadata.getBBox(SMuFLGlyph.ACCIDENTAL_DOUBLE_SHARP), "ACCIDENTAL_DOUBLE_SHARP bbox");

            final double expectedLeftSs = dsBBox.left() + startXSs;
            final double expectedWidthSs = dsBBox.width();
            final double expectedTopSs = dsBBox.top();
            final double expectedBotSs = dsBBox.bottom();

            var bounds = require(
                NoteGeometry.getAccidentalBoundsSs(crotchetWithAccidental(Accidental.DOUBLE_SHARP)),
                "double-sharp bounds");

            assertAll(
                () -> assertThat(bounds.leftSs()).isEqualTo(expectedLeftSs),
                () -> assertThat(bounds.widthSs()).isEqualTo(expectedWidthSs),
                () -> assertThat(bounds.topSs()).isEqualTo(expectedTopSs),
                () -> assertThat(bounds.botSs()).isEqualTo(expectedBotSs)
            );
        }

        @Test
        void testNaturalFlatLeftEdgeIsExact() {
            // NATURAL_FLAT = {NATURAL, FLAT}: total width = naturalAdv + spacing + flatAdv
            final float naturalAdvSs = (float) SMuFLMetadata.getAdvanceWidthOrZero(SMuFLGlyph.ACCIDENTAL_NATURAL);
            final float flatAdvSs = (float) SMuFLMetadata.getAdvanceWidthOrZero(SMuFLGlyph.ACCIDENTAL_FLAT);
            final float totalWidthSs = naturalAdvSs + NoteGeometry.SPACE_BETWEEN_TWO_ACCIDENTALS_SS + flatAdvSs;
            final float startXSs = -NoteGeometry.ACCIDENTAL_PADDING_SS - totalWidthSs;
            var naturalBBox = require(SMuFLMetadata.getBBox(SMuFLGlyph.ACCIDENTAL_NATURAL), "ACCIDENTAL_NATURAL bbox");

            // NATURAL is the leftmost glyph → leftSs of union = naturalBBox.left() + startX
            final double expectedLeftSs = naturalBBox.left() + startXSs;

            var bounds = require(
                NoteGeometry.getAccidentalBoundsSs(crotchetWithAccidental(Accidental.NATURAL_FLAT)),
                "natural-flat bounds");

            assertThat(bounds.leftSs()).isEqualTo(expectedLeftSs);
        }
    }

    // -----------------------------------------------------------------------
    // Row 24: getLedgerLineOverhangSs — 0 in-staff / LEDGER_LINE_EXTENSION_SS out
    // -----------------------------------------------------------------------

    @Nested
    class LedgerLineOverhang {

        @Test
        void testInStaffBoundaryPositionReturnsZero() {
            // |sp| = 5 is the highest in-staff position → no ledger line needed
            final int boundaryPositionSp = 5;
            var note = ElementType.CROTCHET.newInstance();
            note.setStaffPosition(boundaryPositionSp);
            assertThat(NoteGeometry.getLedgerLineOverhangSs(note)).isEqualTo(0.0);
        }

        @Test
        void testInStaffNegativeBoundaryPositionReturnsZero() {
            final int boundaryPositionSp = -5;
            var note = ElementType.CROTCHET.newInstance();
            note.setStaffPosition(boundaryPositionSp);
            assertThat(NoteGeometry.getLedgerLineOverhangSs(note)).isEqualTo(0.0);
        }

        @Test
        void testAboveStaffReturnsLedgerExtension() {
            final int outOfStaffPositionSp = 6;
            var note = ElementType.CROTCHET.newInstance();
            note.setStaffPosition(outOfStaffPositionSp);
            assertThat(NoteGeometry.getLedgerLineOverhangSs(note)).isEqualTo(Engraving.LEDGER_LINE_EXTENSION_SS);
        }

        @Test
        void testBelowStaffReturnsLedgerExtension() {
            final int outOfStaffPositionSp = -6;
            var note = ElementType.CROTCHET.newInstance();
            note.setStaffPosition(outOfStaffPositionSp);
            assertThat(NoteGeometry.getLedgerLineOverhangSs(note)).isEqualTo(Engraving.LEDGER_LINE_EXTENSION_SS);
        }

        @Test
        void testBreathMarkReturnsZeroEvenOutOfStaff() {
            // BREATH_MARK.drawStaveLongitude() == false → always 0 regardless of position
            final int outOfStaffPositionSp = 6;
            var note = ElementType.BREATH_MARK.newInstance();
            note.setStaffPosition(outOfStaffPositionSp);
            assertThat(NoteGeometry.getLedgerLineOverhangSs(note)).isEqualTo(0.0);
        }
    }

    // -----------------------------------------------------------------------
    // Row 25: getNoteheadXOffsetSs — stem-down / stem-up / non-stemmed
    // -----------------------------------------------------------------------

    @Nested
    class NoteheadXOffset {

        @Test
        void testStemDownShiftsLeftByHalfStemWidth() {
            // upper=false means stem-down; offset = -(STEM_WIDTH_SS / 2)
            final boolean upper = false;
            final float expectedOffsetSs = (float) -(NoteGeometry.STEM_WIDTH_SS / 2);
            assertThat(NoteGeometry.getNoteheadXOffsetSs(ElementType.CROTCHET, upper))
                .isEqualTo(expectedOffsetSs);
        }

        @Test
        void testStemUpReturnsZero() {
            final boolean upper = true;
            assertThat(NoteGeometry.getNoteheadXOffsetSs(ElementType.CROTCHET, upper)).isEqualTo(0f);
        }

        @Test
        void testNonStemmedNoteReturnsZero() {
            // SEMIBREVE has no stem (isNoteWithStem() == false) → always 0
            assertThat(NoteGeometry.getNoteheadXOffsetSs(ElementType.SEMIBREVE, false)).isEqualTo(0f);
        }
    }

    // -----------------------------------------------------------------------
    // Row 26: getNoteheadRightEdgeSs — SMuFL bbox + fallback
    // -----------------------------------------------------------------------

    @Nested
    class NoteheadRightEdge {

        @Test
        void testCrotchetRightEdgeMatchesSmuflBbox() {
            var glyph = require(ElementType.CROTCHET.getSMuFLGlyph(), "CROTCHET SMuFL glyph");
            var bbox = require(SMuFLMetadata.getBBox(glyph), "NOTEHEAD_BLACK bbox");

            assertThat(NoteGeometry.getNoteheadRightEdgeSs(ElementType.CROTCHET.newInstance()))
                .isEqualTo(bbox.right());
        }

        @Test
        void testBarlineUsesFallbackRightEdge() {
            // SINGLE_BARLINE has no SMuFL glyph → production falls back to NOTEHEAD_BLACK_RIGHT_EDGE_SS
            final double expectedFallbackSs = NoteGeometry.NOTEHEAD_BLACK_RIGHT_EDGE_SS;

            assertThat(NoteGeometry.getNoteheadRightEdgeSs(ElementType.SINGLE_BARLINE.newInstance()))
                .isEqualTo(expectedFallbackSs);
        }
    }

    // -----------------------------------------------------------------------
    // Row 27: walkAccidentalGlyphs — advance, spacing, paren kerning
    // -----------------------------------------------------------------------

    @Nested
    class WalkAccidentalGlyphs {

        @Test
        void testSingleComponentEmittedAtStartX() {
            final float startX = 2.0f;
            var glyphs = new ArrayList<SMuFLGlyph>();
            var positions = new ArrayList<Float>();

            NoteGeometry.walkAccidentalGlyphs(
                new SMuFLGlyph[]{SMuFLGlyph.ACCIDENTAL_SHARP},
                false, startX,
                (g, x) -> { glyphs.add(g); positions.add(x); });

            assertAll(
                () -> assertThat(glyphs).containsExactly(SMuFLGlyph.ACCIDENTAL_SHARP),
                () -> assertThat(positions.get(0)).isEqualTo(startX)
            );
        }

        @Test
        void testTwoComponentsSpacedByGap() {
            final float startX = 0.0f;
            final float naturalAdvanceSs = (float) SMuFLMetadata.getAdvanceWidthOrZero(SMuFLGlyph.ACCIDENTAL_NATURAL);
            var positions = new ArrayList<Float>();

            NoteGeometry.walkAccidentalGlyphs(
                new SMuFLGlyph[]{SMuFLGlyph.ACCIDENTAL_NATURAL, SMuFLGlyph.ACCIDENTAL_FLAT},
                false, startX,
                (g, x) -> positions.add(x));

            final float expectedFlatX = startX + naturalAdvanceSs + NoteGeometry.SPACE_BETWEEN_TWO_ACCIDENTALS_SS;
            assertAll(
                () -> assertThat(positions).hasSize(2),
                () -> assertThat(positions.get(0)).isEqualTo(startX),
                () -> assertThat(positions.get(1)).isEqualTo(expectedFlatX)
            );
        }

        @Test
        void testParenthesizedSharpEmitsThreeGlyphsWithKerning() {
            final float startX = 0.0f;
            final float parenLeftAdvanceSs = (float) SMuFLMetadata.getAdvanceWidthOrZero(SMuFLGlyph.ACCIDENTAL_PARENS_LEFT);
            final float sharpAdvanceSs = (float) SMuFLMetadata.getAdvanceWidthOrZero(SMuFLGlyph.ACCIDENTAL_SHARP);
            // SHARP is in PAREN_LEFT_KERNING/PAREN_RIGHT_KERNING with value 0.125f each
            final float sharpLeftKerningSs = 0.125f;
            final float sharpRightKerningSs = 0.125f;
            var glyphs = new ArrayList<SMuFLGlyph>();
            var positions = new ArrayList<Float>();

            NoteGeometry.walkAccidentalGlyphs(
                new SMuFLGlyph[]{SMuFLGlyph.ACCIDENTAL_SHARP},
                true, startX,
                (g, x) -> { glyphs.add(g); positions.add(x); });

            final float expectedSharpX = startX + parenLeftAdvanceSs + sharpLeftKerningSs;
            final float expectedParenRightX = expectedSharpX + sharpAdvanceSs + sharpRightKerningSs;
            assertAll(
                () -> assertThat(glyphs).containsExactly(
                    SMuFLGlyph.ACCIDENTAL_PARENS_LEFT,
                    SMuFLGlyph.ACCIDENTAL_SHARP,
                    SMuFLGlyph.ACCIDENTAL_PARENS_RIGHT),
                () -> assertThat(positions.get(0)).isEqualTo(startX),
                () -> assertThat(positions.get(1)).isEqualTo(expectedSharpX),
                () -> assertThat(positions.get(2)).isEqualTo(expectedParenRightX)
            );
        }

        @Test
        void testParenthesizedDoubleSharpHasZeroKerning() {
            // DOUBLE_SHARP absent from kerning maps → getOrDefault returns 0f on both sides
            final float startX = 0.0f;
            final float parenLeftAdvanceSs = (float) SMuFLMetadata.getAdvanceWidthOrZero(SMuFLGlyph.ACCIDENTAL_PARENS_LEFT);
            final float dsAdvanceSs = (float) SMuFLMetadata.getAdvanceWidthOrZero(SMuFLGlyph.ACCIDENTAL_DOUBLE_SHARP);
            var positions = new ArrayList<Float>();

            NoteGeometry.walkAccidentalGlyphs(
                new SMuFLGlyph[]{SMuFLGlyph.ACCIDENTAL_DOUBLE_SHARP},
                true, startX,
                (g, x) -> positions.add(x));

            final float expectedDsX = startX + parenLeftAdvanceSs;  // zero left kerning
            final float expectedParenRightX = expectedDsX + dsAdvanceSs;  // zero right kerning
            assertAll(
                () -> assertThat(positions).hasSize(3),
                () -> assertThat(positions.get(1)).isEqualTo(expectedDsX),
                () -> assertThat(positions.get(2)).isEqualTo(expectedParenRightX)
            );
        }
    }
}
