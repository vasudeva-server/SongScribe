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
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.ArrayList;
import java.util.EnumMap;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.AccidentalBounds;
import songscribe.dom.ElementType;
import songscribe.dom.StaffElement;
import songscribe.dom.StaffElement.Accidental;
import songscribe.engraving.SMuFLConstants;
import songscribe.engraving.Staff;
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

    private static <T> T require(@Nullable T value, String description) {
        assertThat(value).describedAs(description).isNotNull();
        return value;
    }

    // -----------------------------------------------------------------------
    // Which glyph each accidental draws
    // -----------------------------------------------------------------------

    @Nested
    class AccidentalGlyph {

        @Test
        void testEachAccidentalDrawsItsOwnGlyph() {
            var expectedGlyphs = new EnumMap<Accidental, SMuFLGlyph>(Accidental.class);
            expectedGlyphs.put(Accidental.NATURAL, SMuFLGlyph.ACCIDENTAL_NATURAL);
            expectedGlyphs.put(Accidental.FLAT, SMuFLGlyph.ACCIDENTAL_FLAT);
            expectedGlyphs.put(Accidental.SHARP, SMuFLGlyph.ACCIDENTAL_SHARP);
            expectedGlyphs.put(Accidental.DOUBLE_FLAT, SMuFLGlyph.ACCIDENTAL_DOUBLE_FLAT);
            expectedGlyphs.put(Accidental.DOUBLE_SHARP, SMuFLGlyph.ACCIDENTAL_DOUBLE_SHARP);

            expectedGlyphs.forEach((accidental, expectedGlyph) ->
                assertThat(accidental.glyph())
                    .as("glyph drawn for %s", accidental)
                    .isEqualTo(expectedGlyph));
        }
    }

    // -----------------------------------------------------------------------
    // Row 22: getAccidentalWidthSs — base/parens, grace scaling; 0 for none
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
            var expectedWidthSs = (float) SMuFLMetadata.getAdvanceWidthOrZero(SMuFLGlyph.ACCIDENTAL_SHARP);
            assertThat(NoteGeometry.getAccidentalWidthSs(crotchetWithAccidental(Accidental.SHARP)))
                .isEqualTo(expectedWidthSs);
        }

        @Test
        void testDoubleSharpWidthMatchesSmuflAdvance() {
            // DOUBLE_SHARP is a single-glyph accidental — width equals its advance width exactly
            var expectedWidthSs = (float) SMuFLMetadata.getAdvanceWidthOrZero(SMuFLGlyph.ACCIDENTAL_DOUBLE_SHARP);
            assertThat(NoteGeometry.getAccidentalWidthSs(crotchetWithAccidental(Accidental.DOUBLE_SHARP)))
                .isEqualTo(expectedWidthSs);
        }

        @Test
        void testGraceNoteScalesRegularGlyphWidth() {
            // Grace accidentals draw the regular glyph with a scaled-down font, so the grace width is
            // the regular (non-grace) width of the same accidental scaled by GRACE_NOTE_SCALE — and
            // therefore strictly smaller, which also guards the scale factor against regressing to >= 1.
            var regularWidthSs = NoteGeometry.getAccidentalWidthSs(crotchetWithAccidental(Accidental.SHARP));

            var graceNote = ElementType.GRACE_QUAVER.newInstance();
            graceNote.setAccidental(Accidental.SHARP);
            var graceWidthSs = NoteGeometry.getAccidentalWidthSs(graceNote);

            assertThat(graceWidthSs)
                .as("grace width is the regular width scaled by GRACE_NOTE_SCALE")
                .isEqualTo(ElementType.GRACE_NOTE_SCALE * regularWidthSs);
            assertThat(graceWidthSs)
                .as("grace accidental is smaller than the regular accidental")
                .isLessThan(regularWidthSs);
        }

        @Test
        void testGraceNoteScalesParenthesizedWidth() {
            // The grace scale is applied after the parenthesis-vs-base width selection, so a
            // parenthesized grace accidental is the parenthesized regular width scaled.
            var regular = crotchetWithAccidental(Accidental.DOUBLE_SHARP);
            regular.setAccidentalInParentheses(true);
            var regularParenWidthSs = NoteGeometry.getAccidentalWidthSs(regular);

            var grace = ElementType.GRACE_QUAVER.newInstance();
            grace.setAccidental(Accidental.DOUBLE_SHARP);
            grace.setAccidentalInParentheses(true);

            assertThat(NoteGeometry.getAccidentalWidthSs(grace))
                .as("parenthesized grace width is the parenthesized regular width scaled")
                .isEqualTo(ElementType.GRACE_NOTE_SCALE * regularParenWidthSs);
        }

        @Test
        void testParenthesizedDoubleSharpAddsParenWidths() {
            // DOUBLE_SHARP has zero parenthesis kerning, so total = base + left-paren + right-paren
            var baseWidthSs = (float) SMuFLMetadata.getAdvanceWidthOrZero(SMuFLGlyph.ACCIDENTAL_DOUBLE_SHARP);
            var parenLeftWidthSs = (float) SMuFLMetadata.getAdvanceWidthOrZero(SMuFLGlyph.ACCIDENTAL_PARENS_LEFT);
            var parenRightWidthSs = (float) SMuFLMetadata.getAdvanceWidthOrZero(SMuFLGlyph.ACCIDENTAL_PARENS_RIGHT);
            var expectedWidthSs = baseWidthSs + parenLeftWidthSs + parenRightWidthSs;

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
            var sharpAdvanceWidthSs = (float) SMuFLMetadata.getAdvanceWidthOrZero(SMuFLGlyph.ACCIDENTAL_SHARP);
            var startXSs = -NoteGeometry.ACCIDENTAL_PADDING_SS - sharpAdvanceWidthSs;
            var sharpBBox = require(SMuFLMetadata.getBBox(SMuFLGlyph.ACCIDENTAL_SHARP), "ACCIDENTAL_SHARP bbox");

            var expectedLeftSs = sharpBBox.left() + startXSs;
            var expectedWidthSs = sharpBBox.width();
            var expectedTopSs = sharpBBox.top();
            var expectedBotSs = sharpBBox.bottom();

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
            var dsAdvanceWidthSs = (float) SMuFLMetadata.getAdvanceWidthOrZero(SMuFLGlyph.ACCIDENTAL_DOUBLE_SHARP);
            var startXSs = -NoteGeometry.ACCIDENTAL_PADDING_SS - dsAdvanceWidthSs;
            var dsBBox = require(SMuFLMetadata.getBBox(SMuFLGlyph.ACCIDENTAL_DOUBLE_SHARP), "ACCIDENTAL_DOUBLE_SHARP bbox");

            var expectedLeftSs = dsBBox.left() + startXSs;
            var expectedWidthSs = dsBBox.width();
            var expectedTopSs = dsBBox.top();
            var expectedBotSs = dsBBox.bottom();

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
    }

    // -----------------------------------------------------------------------
    // Row 24: noteNeedsLedgerLines — gating by staff position and note type
    // -----------------------------------------------------------------------

    @Nested
    class LedgerLineGating {

        /** |sp| = 5 is the highest in-staff position — no ledger line needed. */
        private static final int IN_STAFF_BOUNDARY_SP = 5;

        /** First out-of-staff position. */
        private static final int OUT_OF_STAFF_SP = 6;

        @Test
        void testInStaffBoundaryPositionReturnsFalse() {
            var note = ElementType.CROTCHET.newInstance();
            note.setStaffPosition(IN_STAFF_BOUNDARY_SP);
            assertThat(NoteGeometry.noteNeedsLedgerLines(note)).isFalse();
        }

        @Test
        void testInStaffNegativeBoundaryPositionReturnsFalse() {
            var note = ElementType.CROTCHET.newInstance();
            note.setStaffPosition(-IN_STAFF_BOUNDARY_SP);
            assertThat(NoteGeometry.noteNeedsLedgerLines(note)).isFalse();
        }

        @Test
        void testAboveStaffReturnsTrue() {
            var note = ElementType.CROTCHET.newInstance();
            note.setStaffPosition(OUT_OF_STAFF_SP);
            assertThat(NoteGeometry.noteNeedsLedgerLines(note)).isTrue();
        }

        @Test
        void testBelowStaffReturnsTrue() {
            var note = ElementType.CROTCHET.newInstance();
            note.setStaffPosition(-OUT_OF_STAFF_SP);
            assertThat(NoteGeometry.noteNeedsLedgerLines(note)).isTrue();
        }

        @Test
        void testBreathMarkReturnsFalseEvenOutOfStaff() {
            // BREATH_MARK.drawStaveLongitude() == false → always false regardless of position
            var note = ElementType.BREATH_MARK.newInstance();
            note.setStaffPosition(OUT_OF_STAFF_SP);
            assertThat(NoteGeometry.noteNeedsLedgerLines(note)).isFalse();
        }
    }

    // -----------------------------------------------------------------------
    // Row 24b: getLedgerLineBaseExtentSs — proportional width from notehead bbox
    // -----------------------------------------------------------------------

    @Nested
    class LedgerLineBaseExtent {

        private static final double TOLERANCE = 1e-9;

        /** Staff position where ledger lines are needed (|pos| > 5). */
        private static final int LEDGER_POSITION_SP = 6;

        /**
         * Asserts both of {@code actual}'s extensions beyond the notehead bbox are symmetric and each
         * equal LENGTH_FRACTION × notehead width. This checks the defining proportional property
         * rather than mirroring the production left/right assembly, so it catches a wrong multiplier
         * base — e.g. {@code lf × headRight} instead of {@code lf × width}, which diverge once the
         * notehead is X-offset (stem-down) — that a copied formula would silently reproduce.
         */
        private static void assertProportionalExtent(
            NoteGeometry.LedgerExtentSs actual, ElementType noteType, StaffElement.Direction direction
        ) {
            var bbox = SMuFLMetadata.requireBBox(noteType.requireSMuFLGlyph());
            var offset = NoteGeometry.getNoteheadXOffsetSs(noteType, direction);
            var headLeft = offset + bbox.left();
            var headRight = offset + bbox.right();
            var expectedExtension = SMuFLConstants.LEDGER_LINE_LENGTH_FRACTION * (headRight - headLeft);

            var leftExtension = headLeft - actual.leftSs();
            var rightExtension = actual.rightSs() - headRight;

            assertAll(
                () -> assertThat(leftExtension)
                    .as("left extension = LENGTH_FRACTION × notehead width")
                    .isCloseTo(expectedExtension, within(TOLERANCE)),
                () -> assertThat(rightExtension)
                    .as("right extension = LENGTH_FRACTION × notehead width")
                    .isCloseTo(expectedExtension, within(TOLERANCE)),
                () -> assertThat(leftExtension)
                    .as("extensions are symmetric")
                    .isCloseTo(rightExtension, within(TOLERANCE))
            );
        }

        @Test
        void testStemUpCrotchetExtentIsProportional() {
            var note = ElementType.CROTCHET.newInstance();
            note.setUpper(true);
            note.setStaffPosition(LEDGER_POSITION_SP);

            assertProportionalExtent(
                NoteGeometry.getLedgerLineBaseExtentSs(note), ElementType.CROTCHET, StaffElement.Direction.UP);
        }

        @Test
        void testStemDownCrotchetExtentMatchesStemUp() {
            // Noteheads are no longer shifted by stem direction, so the ledger extent is identical
            // for stem-down and stem-up.
            var note = ElementType.CROTCHET.newInstance();
            note.setUpper(false);
            note.setStaffPosition(LEDGER_POSITION_SP);

            var actual = NoteGeometry.getLedgerLineBaseExtentSs(note);
            assertProportionalExtent(actual, ElementType.CROTCHET, StaffElement.Direction.DOWN);

            var stemUpNote = ElementType.CROTCHET.newInstance();
            stemUpNote.setUpper(true);
            stemUpNote.setStaffPosition(LEDGER_POSITION_SP);
            assertThat(actual.leftSs())
                .isCloseTo(NoteGeometry.getLedgerLineBaseExtentSs(stemUpNote).leftSs(), within(TOLERANCE));
        }

        @Test
        void testGraceNoteExtentIsProportionalAndStemUp() {
            // Grace notes always treat upper=true; offset=0 since isGraceNote() forces stem-up logic
            var note = ElementType.GRACE_QUAVER.newInstance();
            note.setStaffPosition(LEDGER_POSITION_SP);

            assertProportionalExtent(
                NoteGeometry.getLedgerLineBaseExtentSs(note), ElementType.GRACE_QUAVER, StaffElement.Direction.UP);
        }
    }

    // -----------------------------------------------------------------------
    // Row 24c: getLedgerLineExtentSs — accidental shortening per ledger Y
    // -----------------------------------------------------------------------

    @Nested
    class LedgerLineExtentWithAccidental {

        private static final double TOLERANCE = 1e-9;

        /** One ledger line below the staff (Y-down): ledger sits above note center in Y-down. */
        private static final int ONE_LEDGER_BELOW_SP = 7;

        /** Staff position with 3 ledger lines; the farthest ledger Y exceeds the sharp bbox. */
        private static final int THREE_LEDGERS_BELOW_SP = 11;

        /**
         * The sharp accidental spans the note center vertically (typical bbox ≈ [-1.5, 1.5]).
         * spToSs(-1) = -0.5 is well within that range.
         */
        private static final int LEDGER_WITHIN_ACC_RANGE_SP = -1;

        /**
         * spToSs(-5) = -2.5 is beyond the typical sharp bbox top (~-1.5), placing the ledger
         * Y outside the accidental's vertical extent.
         */
        private static final int LEDGER_OUTSIDE_ACC_RANGE_SP = -5;

        private static AccidentalBounds requireAccBounds(StaffElement note) {
            var bounds = NoteGeometry.getAccidentalBoundsSs(note);
            assertThat(bounds).as("accidental bounds must be non-null for this test").isNotNull();
            return bounds;
        }

        // ---- test a: ledger Y in accidental range, midpoint right of base ledgerLeft ----

        @Test
        void testAccidentalInYRangeShortenedToMidpoint() {
            // stem-up crotchet, SHARP, ledger Y within accidental's vertical extent.
            // With Bravura metrics: accRight ≈ −ACCIDENTAL_PADDING_SS, headLeft = 0,
            // midpoint ≈ −ACCIDENTAL_PADDING_SS/2, which sits right of ledgerLeft
            // (≈ −lf·headRight), so the max returns midpoint (shortening applies).
            var note = ElementType.CROTCHET.newInstance();
            note.setUpper(true);
            note.setStaffPosition(ONE_LEDGER_BELOW_SP);
            note.setAccidental(Accidental.SHARP);

            var ledgerYSs = Staff.spToSs(LEDGER_WITHIN_ACC_RANGE_SP);
            var baseExtent = NoteGeometry.getLedgerLineBaseExtentSs(note);
            var accBounds = requireAccBounds(note);

            // Precondition: ledger Y is within the accidental's vertical range
            assertThat(ledgerYSs)
                .as("ledger Y must be within accidental's vertical extent for test a")
                .isBetween(accBounds.topSs(), accBounds.botSs());

            var accRight = accBounds.leftSs() + accBounds.widthSs();
            var noteType = ElementType.CROTCHET;
            var glyph = noteType.requireSMuFLGlyph();
            var bbox = SMuFLMetadata.requireBBox(glyph);
            var headLeft = NoteGeometry.getNoteheadXOffsetSs(noteType, StaffElement.Direction.UP) + bbox.left();
            var midpoint = (accRight + headLeft) / 2;

            // Precondition: midpoint is right of base ledgerLeft (shortening applies)
            assertThat(midpoint)
                .as("midpoint must be right of base ledgerLeft for test a")
                .isGreaterThan(baseExtent.leftSs());

            var expected = midpoint;
            var actual = NoteGeometry.getLedgerLineExtentSs(note, ledgerYSs);

            assertThat(actual.leftSs()).isCloseTo(expected, within(TOLERANCE));
            assertThat(actual.rightSs()).isCloseTo(baseExtent.rightSs(), within(TOLERANCE));
        }

        // ---- test b: ledger Y outside accidental's vertical extent → no shortening ----

        @Test
        void testAccidentalOutsideYRangeBaseExtentUnchanged() {
            // Three ledgers below staff; the farthest ledger Y (spToSs(-5) = -2.5 in Y-down)
            // falls above the typical sharp bbox top (≈ -1.5), placing it outside the accidental's
            // vertical extent → no shortening; result equals base extent.
            var note = ElementType.CROTCHET.newInstance();
            note.setUpper(true);
            note.setStaffPosition(THREE_LEDGERS_BELOW_SP);
            note.setAccidental(Accidental.SHARP);

            var ledgerYSs = Staff.spToSs(LEDGER_OUTSIDE_ACC_RANGE_SP);
            var baseExtent = NoteGeometry.getLedgerLineBaseExtentSs(note);
            var accBounds = requireAccBounds(note);

            // Precondition: ledger Y is above (Y-down: less than) the accidental's top edge, hence
            // outside its vertical range.
            assertThat(ledgerYSs)
                .as("ledger Y must be outside accidental's vertical extent for test b")
                .isLessThan(accBounds.topSs());

            var actual = NoteGeometry.getLedgerLineExtentSs(note, ledgerYSs);

            assertThat(actual.leftSs()).isCloseTo(baseExtent.leftSs(), within(TOLERANCE));
            assertThat(actual.rightSs()).isCloseTo(baseExtent.rightSs(), within(TOLERANCE));
        }

        // ---- test c: accRight < ledgerLeft yet shortening STILL applies (midpoint is the guard) ----

        @Test
        void testAccRightLeftOfBaseEdgeYetShorteningApplies() {
            // With Bravura metrics, accRight ≈ -ACCIDENTAL_PADDING_SS ≈ -0.34, which is LEFT of the
            // base ledgerLeft (≈ -0.25 × headRight ≈ -0.30). This demonstrates that accRight ≤ ledgerLeft
            // is NOT the no-shortening guard: what matters is (accRight + headLeft)/2 vs ledgerLeft.
            // Here midpoint ≈ accRight/2 ≈ -0.17, which is RIGHT of ledgerLeft (-0.30), so the
            // max returns midpoint and shortening DOES apply even though accRight < ledgerLeft.
            var note = ElementType.CROTCHET.newInstance();
            note.setUpper(true);
            note.setStaffPosition(ONE_LEDGER_BELOW_SP);
            note.setAccidental(Accidental.SHARP);

            var ledgerYSs = Staff.spToSs(LEDGER_WITHIN_ACC_RANGE_SP);
            var baseExtent = NoteGeometry.getLedgerLineBaseExtentSs(note);
            var accBounds = requireAccBounds(note);
            var accRight = accBounds.leftSs() + accBounds.widthSs();
            var noteType = ElementType.CROTCHET;
            var glyph = noteType.requireSMuFLGlyph();
            var bbox = SMuFLMetadata.requireBBox(glyph);
            var headLeft = NoteGeometry.getNoteheadXOffsetSs(noteType, StaffElement.Direction.UP) + bbox.left();
            var midpoint = (accRight + headLeft) / 2;

            // Show that accRight IS left of the base ledger left edge …
            assertThat(accRight).isLessThan(baseExtent.leftSs());
            // … yet midpoint is right of the base ledger left edge (the max picks midpoint)
            assertThat(midpoint).isGreaterThan(baseExtent.leftSs());

            var actual = NoteGeometry.getLedgerLineExtentSs(note, ledgerYSs);

            // Shortening applies: leftSs == midpoint, not base ledgerLeft
            assertThat(actual.leftSs()).isCloseTo(midpoint, within(TOLERANCE));
            assertThat(actual.leftSs()).isGreaterThan(baseExtent.leftSs());
        }

        // ---- plain accidental: the closest component is the whole accidental ----

        @Test
        void testPlainAccidentalClosestBoundsAreTheFullBounds() {
            // A flat draws a single glyph, so the component nearest the notehead is the accidental
            // itself: the closest-component bounds and the full bounds must coincide, and ledger
            // shortening measured from either is the same.
            var note = ElementType.CROTCHET.newInstance();
            note.setUpper(true);
            note.setStaffPosition(ONE_LEDGER_BELOW_SP);
            note.setAccidental(Accidental.FLAT);

            var fullBounds = require(NoteGeometry.getAccidentalBoundsSs(note), "full flat bounds");
            var closestBounds = require(
                NoteGeometry.getClosestAccidentalGlyphBoundsSs(note), "closest-component bounds");

            assertThat(closestBounds.leftSs()).isCloseTo(fullBounds.leftSs(), within(TOLERANCE));
            assertThat(closestBounds.widthSs()).isCloseTo(fullBounds.widthSs(), within(TOLERANCE));
            assertThat(closestBounds.topSs()).isCloseTo(fullBounds.topSs(), within(TOLERANCE));
            assertThat(closestBounds.botSs()).isCloseTo(fullBounds.botSs(), within(TOLERANCE));

            var ledgerYSs = Staff.spToSs(LEDGER_WITHIN_ACC_RANGE_SP);

            // Shortening only engages when the ledger Y sits within the flat's vertical extent.
            assertThat(ledgerYSs)
                .as("ledger Y must be within the flat's vertical extent")
                .isBetween(closestBounds.topSs(), closestBounds.botSs());

            var baseExtent = NoteGeometry.getLedgerLineBaseExtentSs(note);
            var accRight = closestBounds.leftSs() + closestBounds.widthSs();
            var noteType = ElementType.CROTCHET;
            var bbox = SMuFLMetadata.requireBBox(noteType.requireSMuFLGlyph());
            var headLeft = NoteGeometry.getNoteheadXOffsetSs(noteType, StaffElement.Direction.UP) + bbox.left();
            var expectedLeft = Math.max(baseExtent.leftSs(), (accRight + headLeft) / 2);

            var actual = NoteGeometry.getLedgerLineExtentSs(note, ledgerYSs);

            assertThat(actual.leftSs()).isCloseTo(expectedLeft, within(TOLERANCE));
            assertThat(actual.rightSs()).isCloseTo(baseExtent.rightSs(), within(TOLERANCE));
        }

        // ---- parenthesized accidental: only the right parenthesis matters ----

        @Test
        void testParenthesizedAccidentalChecksRightParenOnly() {
            // The right parenthesis is the rightmost glyph, so for a parenthesized accidental it is
            // the glyph nearest the notehead and the only one the ledger can collide with.
            var note = ElementType.CROTCHET.newInstance();
            note.setUpper(true);
            note.setStaffPosition(ONE_LEDGER_BELOW_SP);
            note.setAccidental(Accidental.SHARP);
            note.setAccidentalInParentheses(true);

            var fullBounds = require(NoteGeometry.getAccidentalBoundsSs(note), "full parenthesized bounds");
            var closestBounds = require(
                NoteGeometry.getClosestAccidentalGlyphBoundsSs(note), "right-paren bounds");

            var rightParenBBox = SMuFLMetadata.requireBBox(SMuFLGlyph.ACCIDENTAL_PARENS_RIGHT);

            // The closest glyph is the right paren: its span and vertical extent match the paren
            // glyph (translation leaves width/height unchanged), and it starts right of the full
            // span (which also covers the left paren and the sharp) while sharing its right edge.
            assertThat(closestBounds.widthSs())
                .isCloseTo(rightParenBBox.width(), within(TOLERANCE));
            assertThat(closestBounds.botSs() - closestBounds.topSs())
                .isCloseTo(rightParenBBox.height(), within(TOLERANCE));
            assertThat(closestBounds.leftSs()).isGreaterThan(fullBounds.leftSs());
            assertThat(closestBounds.leftSs() + closestBounds.widthSs())
                .isCloseTo(fullBounds.leftSs() + fullBounds.widthSs(), within(TOLERANCE));
        }

        // ---- no accidental: the public per-Y wrapper returns the base extent unchanged ----

        @Test
        void testNoAccidentalReturnsBaseExtent() {
            // A note with no accidental has no closest-component bounds, so getLedgerLineExtentSs must
            // return the base extent for any ledger Y (the accidentalBounds == null branch).
            var note = ElementType.CROTCHET.newInstance();
            note.setUpper(true);
            note.setStaffPosition(ONE_LEDGER_BELOW_SP);

            var baseExtent = NoteGeometry.getLedgerLineBaseExtentSs(note);
            var actual = NoteGeometry.getLedgerLineExtentSs(note, Staff.spToSs(LEDGER_WITHIN_ACC_RANGE_SP));

            assertThat(actual.leftSs()).isCloseTo(baseExtent.leftSs(), within(TOLERANCE));
            assertThat(actual.rightSs()).isCloseTo(baseExtent.rightSs(), within(TOLERANCE));
        }
    }

    // -----------------------------------------------------------------------
    // Row 24d: LedgerLineGeometry.extentAtSs — clamp logic over synthetic geometry
    // -----------------------------------------------------------------------

    @Nested
    class LedgerLineGeometryClamp {

        private static final double TOLERANCE = 1e-9;

        private static final double BASE_LEFT_SS = -0.3;
        private static final double BASE_RIGHT_SS = 1.5;
        private static final double HEAD_LEFT_SS = 0.0;

        private static final double ACC_TOP_SS = -1.0;
        private static final double ACC_BOT_SS = 1.0;
        private static final double Y_IN_RANGE_SS = 0.0;
        private static final double OUT_OF_RANGE_DELTA_SS = 0.1;

        // Accidental sitting just left of the notehead: its midpoint lands right of the base left
        // edge, so the clamp pulls the ledger in to that midpoint.
        private static final double NEAR_ACC_LEFT_SS = -0.2;
        private static final double NEAR_ACC_WIDTH_SS = 0.1;

        // Accidental reaching far left: its midpoint lands left of the base edge, so Math.max keeps
        // the (further-right) base edge and no shortening occurs.
        private static final double FAR_ACC_LEFT_SS = -1.0;
        private static final double FAR_ACC_WIDTH_SS = 0.3;

        private static final NoteGeometry.LedgerExtentSs BASE =
            new NoteGeometry.LedgerExtentSs(BASE_LEFT_SS, BASE_RIGHT_SS);

        private static NoteGeometry.LedgerLineGeometry geometry(@Nullable AccidentalBounds accidental) {
            return new NoteGeometry.LedgerLineGeometry(BASE, accidental, HEAD_LEFT_SS);
        }

        private static AccidentalBounds accidental(double leftSs, double widthSs) {
            return new AccidentalBounds(leftSs, widthSs, ACC_TOP_SS, ACC_BOT_SS);
        }

        private static double midpoint(double accLeftSs, double accWidthSs) {
            return (accLeftSs + accWidthSs + HEAD_LEFT_SS) / 2;
        }

        @Test
        void testNullAccidentalReturnsBase() {
            var extent = geometry(null).extentAtSs(Y_IN_RANGE_SS);

            assertThat(extent.leftSs()).isCloseTo(BASE_LEFT_SS, within(TOLERANCE));
            assertThat(extent.rightSs()).isCloseTo(BASE_RIGHT_SS, within(TOLERANCE));
        }

        @Test
        void testYInRangeShortensToMidpoint() {
            var geom = geometry(accidental(NEAR_ACC_LEFT_SS, NEAR_ACC_WIDTH_SS));
            var expectedMidpoint = midpoint(NEAR_ACC_LEFT_SS, NEAR_ACC_WIDTH_SS);

            // Precondition: the midpoint is right of the base edge, so shortening genuinely applies.
            assertThat(expectedMidpoint).isGreaterThan(BASE_LEFT_SS);

            var extent = geom.extentAtSs(Y_IN_RANGE_SS);

            assertThat(extent.leftSs()).isCloseTo(expectedMidpoint, within(TOLERANCE));
            assertThat(extent.rightSs()).isCloseTo(BASE_RIGHT_SS, within(TOLERANCE));
        }

        @Test
        void testMidpointLeftOfBaseKeepsBaseEdge() {
            // Math.max guard: when the accidental midpoint is LEFT of the base edge, the base edge
            // wins and the ledger is not shortened — even though the ledger Y is in the accidental's
            // vertical range. Dropping the max would wrongly move the edge left to the midpoint.
            var geom = geometry(accidental(FAR_ACC_LEFT_SS, FAR_ACC_WIDTH_SS));

            // Precondition: the midpoint is left of the base edge (the branch the max must guard).
            assertThat(midpoint(FAR_ACC_LEFT_SS, FAR_ACC_WIDTH_SS)).isLessThan(BASE_LEFT_SS);

            var extent = geom.extentAtSs(Y_IN_RANGE_SS);

            assertThat(extent.leftSs()).isCloseTo(BASE_LEFT_SS, within(TOLERANCE));
            assertThat(extent.rightSs()).isCloseTo(BASE_RIGHT_SS, within(TOLERANCE));
        }

        @Test
        void testYAtTopBoundaryIsInRange() {
            // y exactly at the top edge is inclusive, so shortening applies (guards < vs <=).
            var geom = geometry(accidental(NEAR_ACC_LEFT_SS, NEAR_ACC_WIDTH_SS));

            var extent = geom.extentAtSs(ACC_TOP_SS);

            assertThat(extent.leftSs())
                .isCloseTo(midpoint(NEAR_ACC_LEFT_SS, NEAR_ACC_WIDTH_SS), within(TOLERANCE));
        }

        @Test
        void testYAtBottomBoundaryIsInRange() {
            // y exactly at the bottom edge is inclusive (guards > vs >=).
            var geom = geometry(accidental(NEAR_ACC_LEFT_SS, NEAR_ACC_WIDTH_SS));

            var extent = geom.extentAtSs(ACC_BOT_SS);

            assertThat(extent.leftSs())
                .isCloseTo(midpoint(NEAR_ACC_LEFT_SS, NEAR_ACC_WIDTH_SS), within(TOLERANCE));
        }

        @Test
        void testYJustOutsideRangeReturnsBase() {
            // Just above the top edge → out of range → no shortening.
            var geom = geometry(accidental(NEAR_ACC_LEFT_SS, NEAR_ACC_WIDTH_SS));

            var extent = geom.extentAtSs(ACC_TOP_SS - OUT_OF_RANGE_DELTA_SS);

            assertThat(extent.leftSs()).isCloseTo(BASE_LEFT_SS, within(TOLERANCE));
        }
    }

    // -----------------------------------------------------------------------
    // Row 25: getNoteheadXOffsetSs — stem-down / stem-up / non-stemmed
    // -----------------------------------------------------------------------

    @Nested
    class NoteheadXOffset {

        @Test
        void testStemDownReturnsZero() {
            // Down-stem heads are no longer shifted: they align with up-stem heads at the column X.
            final var direction = StaffElement.Direction.DOWN;
            assertThat(NoteGeometry.getNoteheadXOffsetSs(ElementType.CROTCHET, direction)).isEqualTo(0f);
        }

        @Test
        void testStemUpReturnsZero() {
            final var direction = StaffElement.Direction.UP;
            assertThat(NoteGeometry.getNoteheadXOffsetSs(ElementType.CROTCHET, direction)).isEqualTo(0f);
        }

        @Test
        void testNonStemmedNoteReturnsZero() {
            // SEMIBREVE has no stem (isNoteWithStem() == false) → always 0
            assertThat(NoteGeometry.getNoteheadXOffsetSs(ElementType.SEMIBREVE, StaffElement.Direction.DOWN)).isEqualTo(0f);
        }
    }

    // -----------------------------------------------------------------------
    // effectiveDirection — grace notes always resolve UP regardless of stored direction
    // -----------------------------------------------------------------------

    @Nested
    class EffectiveDirection {

        @Test
        void testGraceNoteStoredDownStillResolvesUp() {
            var grace = ElementType.GRACE_QUAVER.newInstance();
            grace.setDirection(StaffElement.Direction.DOWN);

            assertThat(NoteGeometry.effectiveDirection(grace)).isEqualTo(StaffElement.Direction.UP);
        }

        @Test
        void testNonGraceNoteUsesOwnStoredDirection() {
            var note = ElementType.CROTCHET.newInstance();
            note.setDirection(StaffElement.Direction.DOWN);

            assertThat(NoteGeometry.effectiveDirection(note)).isEqualTo(StaffElement.Direction.DOWN);
        }
    }

    // -----------------------------------------------------------------------
    // forcedShorteningSs — Ross & Gourlay forced-direction stem shortening
    // -----------------------------------------------------------------------

    @Nested
    class ForcedShorteningSs {

        private static final double TOLERANCE = 1e-9;

        // Staff position at which the forced-shorten formula first hits MAX_FORCED_SHORTEN_SS:
        // FORCED_SHORTEN_PER_STEP_SS * (1 + |sp|) == MAX_FORCED_SHORTEN_SS => |sp| == 5.
        private static final int FLOOR_STAFF_POSITION = 5;

        @Test
        void testForcedUpStemAtMiddleLineShortensByOneStep() {
            // sp=0, direction UP: defaultDirection(sp<=0) is DOWN, so UP is forced.
            var shorteningSs = NoteGeometry.forcedShorteningSs(0, StaffElement.Direction.UP, false);

            assertThat(shorteningSs).isCloseTo(NoteGeometry.FORCED_SHORTEN_PER_STEP_SS, within(TOLERANCE));
        }

        @Test
        void testForcedUpStemShortensProgressivelyWithDistanceFromMiddleLine() {
            var nearSs = NoteGeometry.forcedShorteningSs(-1, StaffElement.Direction.UP, false);
            var farSs = NoteGeometry.forcedShorteningSs(-3, StaffElement.Direction.UP, false);

            assertThat(farSs).isGreaterThan(nearSs);
            assertThat(nearSs).isCloseTo(
                NoteGeometry.FORCED_SHORTEN_PER_STEP_SS * 2, within(TOLERANCE));
            assertThat(farSs).isCloseTo(
                NoteGeometry.FORCED_SHORTEN_PER_STEP_SS * 4, within(TOLERANCE));
        }

        @Test
        void testForcedUpStemCapsAtMaxShortenAtFloorStaffPosition() {
            var shorteningSs = NoteGeometry.forcedShorteningSs(
                -FLOOR_STAFF_POSITION, StaffElement.Direction.UP, false);

            assertThat(shorteningSs).isCloseTo(NoteGeometry.MAX_FORCED_SHORTEN_SS, within(TOLERANCE));
        }

        @Test
        void testForcedUpStemBeyondFloorStaffPositionStaysCapped() {
            var shorteningSs = NoteGeometry.forcedShorteningSs(
                -(FLOOR_STAFF_POSITION + 10), StaffElement.Direction.UP, false);

            assertThat(shorteningSs).isCloseTo(NoteGeometry.MAX_FORCED_SHORTEN_SS, within(TOLERANCE));
        }

        @Test
        void testNaturalUpStemBelowMiddleLineIsNotShortened() {
            // sp=3 > 0: defaultDirection is UP, so an UP stem here is natural, not forced.
            var shorteningSs = NoteGeometry.forcedShorteningSs(3, StaffElement.Direction.UP, false);

            assertThat(shorteningSs).isZero();
        }

        @Test
        void testForcedDownStemBelowMiddleLineShortensProgressively() {
            // sp=3 > 0: defaultDirection is UP, so a DOWN stem here is forced (symmetric case).
            var shorteningSs = NoteGeometry.forcedShorteningSs(3, StaffElement.Direction.DOWN, false);

            assertThat(shorteningSs).isCloseTo(
                NoteGeometry.FORCED_SHORTEN_PER_STEP_SS * 4, within(TOLERANCE));
        }

        @Test
        void testNaturalDownStemAboveMiddleLineIsNotShortened() {
            var shorteningSs = NoteGeometry.forcedShorteningSs(-3, StaffElement.Direction.DOWN, false);

            assertThat(shorteningSs).isZero();
        }

        @Test
        void testDownStemExactlyOnMiddleLineIsTreatedAsNatural() {
            // forcedDown requires sp > 0 strictly, so sp=0 is the resolved boundary: natural.
            var shorteningSs = NoteGeometry.forcedShorteningSs(0, StaffElement.Direction.DOWN, false);

            assertThat(shorteningSs).isZero();
        }

        @Test
        void testGraceNoteIsNeverShortenedRegardlessOfDirectionOrPosition() {
            var upSs = NoteGeometry.forcedShorteningSs(-FLOOR_STAFF_POSITION, StaffElement.Direction.UP, true);
            var downSs = NoteGeometry.forcedShorteningSs(FLOOR_STAFF_POSITION, StaffElement.Direction.DOWN, true);

            assertThat(upSs).isZero();
            assertThat(downSs).isZero();
        }
    }

    // -----------------------------------------------------------------------
    // Row 27: walkAccidentalGlyphs — advance and paren kerning
    // -----------------------------------------------------------------------

    // The kerning the production tables give a sharp on each side of a parenthesis, read
    // from those tables rather than restated here — a duplicated literal would go stale
    // the moment the spacing is retuned, and the failure would point at the geometry
    // instead of at the spacing. Mirrors the production lookup, which treats a glyph
    // missing from a table as no kerning.
    private static float sharpParenLeftKerningSs() {
        return NoteGeometry.PAREN_LEFT_KERNING.getOrDefault(SMuFLGlyph.ACCIDENTAL_SHARP, 0f);
    }

    private static float sharpParenRightKerningSs() {
        return NoteGeometry.PAREN_RIGHT_KERNING.getOrDefault(SMuFLGlyph.ACCIDENTAL_SHARP, 0f);
    }

    @Nested
    class WalkAccidentalGlyphs {

        @Test
        void testSingleComponentEmittedAtStartX() {
            final var startX = 2.0f;
            var glyphs = new ArrayList<SMuFLGlyph>();
            var positions = new ArrayList<Float>();

            NoteGeometry.walkAccidentalGlyphs(
                SMuFLGlyph.ACCIDENTAL_SHARP,
                false, startX, 1f,
                (g, x) -> { glyphs.add(g); positions.add(x); });

            assertAll(
                () -> assertThat(glyphs).containsExactly(SMuFLGlyph.ACCIDENTAL_SHARP),
                () -> assertThat(positions.getFirst()).isEqualTo(startX)
            );
        }

        @Test
        void testParenthesizedSharpEmitsThreeGlyphsWithKerning() {
            final var startX = 0.0f;
            var parenLeftAdvanceSs = (float) SMuFLMetadata.getAdvanceWidthOrZero(SMuFLGlyph.ACCIDENTAL_PARENS_LEFT);
            var sharpAdvanceSs = (float) SMuFLMetadata.getAdvanceWidthOrZero(SMuFLGlyph.ACCIDENTAL_SHARP);
            var sharpLeftKerningSs = sharpParenLeftKerningSs();
            var sharpRightKerningSs = sharpParenRightKerningSs();
            var glyphs = new ArrayList<SMuFLGlyph>();
            var positions = new ArrayList<Float>();

            NoteGeometry.walkAccidentalGlyphs(
                SMuFLGlyph.ACCIDENTAL_SHARP,
                true, startX, 1f,
                (g, x) -> { glyphs.add(g); positions.add(x); });

            var expectedSharpX = startX + parenLeftAdvanceSs + sharpLeftKerningSs;
            var expectedParenRightX = expectedSharpX + sharpAdvanceSs + sharpRightKerningSs;
            assertAll(
                () -> assertThat(glyphs).containsExactly(
                    SMuFLGlyph.ACCIDENTAL_PARENS_LEFT,
                    SMuFLGlyph.ACCIDENTAL_SHARP,
                    SMuFLGlyph.ACCIDENTAL_PARENS_RIGHT),
                () -> assertThat(positions.getFirst()).isEqualTo(startX),
                () -> assertThat(positions.get(1)).isEqualTo(expectedSharpX),
                () -> assertThat(positions.get(2)).isEqualTo(expectedParenRightX)
            );
        }

        @Test
        void testParenthesizedDoubleSharpHasZeroKerning() {
            // DOUBLE_SHARP absent from kerning maps → getOrDefault returns 0f on both sides
            final var startX = 0.0f;
            var parenLeftAdvanceSs = (float) SMuFLMetadata.getAdvanceWidthOrZero(SMuFLGlyph.ACCIDENTAL_PARENS_LEFT);
            var dsAdvanceSs = (float) SMuFLMetadata.getAdvanceWidthOrZero(SMuFLGlyph.ACCIDENTAL_DOUBLE_SHARP);
            var positions = new ArrayList<Float>();

            NoteGeometry.walkAccidentalGlyphs(
                SMuFLGlyph.ACCIDENTAL_DOUBLE_SHARP,
                true, startX, 1f,
                (g, x) -> positions.add(x));

            var expectedDsX = startX + parenLeftAdvanceSs;  // zero left kerning
            var expectedParenRightX = expectedDsX + dsAdvanceSs;  // zero right kerning
            assertAll(
                () -> assertThat(positions).hasSize(3),
                () -> assertThat(positions.get(1)).isEqualTo(expectedDsX),
                () -> assertThat(positions.get(2)).isEqualTo(expectedParenRightX)
            );
        }

        @Test
        void testScaleMultipliesAdvanceIntoRightParen() {
            // A scale != 1f must multiply the accidental's own advance as well as the paren advance
            // and kerning, so the right paren — the last glyph the walk emits — lands at the sum of
            // every scaled step (grace-note layout).
            final var startX = 0.0f;
            final var scale = ElementType.GRACE_NOTE_SCALE;
            var parenLeftAdvanceSs = (float) SMuFLMetadata.getAdvanceWidthOrZero(SMuFLGlyph.ACCIDENTAL_PARENS_LEFT);
            var sharpAdvanceSs = (float) SMuFLMetadata.getAdvanceWidthOrZero(SMuFLGlyph.ACCIDENTAL_SHARP);
            var sharpLeftKerningSs = sharpParenLeftKerningSs();
            var sharpRightKerningSs = sharpParenRightKerningSs();
            var positions = new ArrayList<Float>();

            NoteGeometry.walkAccidentalGlyphs(
                SMuFLGlyph.ACCIDENTAL_SHARP,
                true, startX, scale,
                (g, x) -> positions.add(x));

            // Accumulate in the same left-to-right order the production walk uses, so the float
            // comparison stays exact.
            var expectedSharpX = startX + scale * parenLeftAdvanceSs + scale * sharpLeftKerningSs;
            var expectedParenRightX = expectedSharpX + scale * sharpAdvanceSs + scale * sharpRightKerningSs;
            assertAll(
                () -> assertThat(positions).hasSize(3),
                () -> assertThat(positions.getFirst()).isEqualTo(startX),
                () -> assertThat(positions.get(2)).isEqualTo(expectedParenRightX)
            );
        }

        @Test
        void testScaleMultipliesParenAdvanceAndKerning() {
            // A scale != 1f must also multiply the parenthesis advance and the kerning around the
            // glyph, so the inner glyph lands at startX + scale*parenAdvance + scale*kerning.
            final var startX = 0.0f;
            final var scale = ElementType.GRACE_NOTE_SCALE;
            var parenLeftAdvanceSs = (float) SMuFLMetadata.getAdvanceWidthOrZero(SMuFLGlyph.ACCIDENTAL_PARENS_LEFT);
            var sharpLeftKerningSs = sharpParenLeftKerningSs();
            var positions = new ArrayList<Float>();

            NoteGeometry.walkAccidentalGlyphs(
                SMuFLGlyph.ACCIDENTAL_SHARP,
                true, startX, scale,
                (g, x) -> positions.add(x));

            var expectedSharpX = startX + scale * parenLeftAdvanceSs + scale * sharpLeftKerningSs;
            assertThat(positions.get(1)).isEqualTo(expectedSharpX);
        }
    }

    // -----------------------------------------------------------------------
    // getAccidentalStartXSs — accidental right edge sits one (grace-scaled) padding left of origin
    // -----------------------------------------------------------------------

    @Nested
    class AccidentalStartX {

        // Tolerance absorbing the float round-trip of -padding - width + width back to -padding.
        private static final float RIGHT_EDGE_TOLERANCE_SS = 1e-4f;

        @Test
        void testRegularAccidentalRightEdgeIsOnePaddingLeftOfOrigin() {
            var note = crotchetWithAccidental(Accidental.SHARP);
            var rightEdgeSs = NoteGeometry.getAccidentalStartXSs(note) + NoteGeometry.getAccidentalWidthSs(note);

            assertThat(rightEdgeSs)
                .as("regular accidental right edge sits one padding left of the notehead origin")
                .isCloseTo(-NoteGeometry.ACCIDENTAL_PADDING_SS, within(RIGHT_EDGE_TOLERANCE_SS));
        }

        @Test
        void testGraceAccidentalUsesScaledPadding() {
            var grace = ElementType.GRACE_QUAVER.newInstance();
            grace.setAccidental(Accidental.SHARP);
            var rightEdgeSs = NoteGeometry.getAccidentalStartXSs(grace) + NoteGeometry.getAccidentalWidthSs(grace);

            assertThat(rightEdgeSs)
                .as("grace accidental right edge sits one grace-scaled padding left of the origin")
                .isCloseTo(-NoteGeometry.GRACE_ACCIDENTAL_PADDING_SS, within(RIGHT_EDGE_TOLERANCE_SS));
            assertThat(NoteGeometry.GRACE_ACCIDENTAL_PADDING_SS)
                .as("grace padding is proportionally smaller than the regular padding")
                .isLessThan(NoteGeometry.ACCIDENTAL_PADDING_SS);
        }
    }
}
