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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.within;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.ElementType;
import songscribe.dom.StaffElement;
import songscribe.engraving.SMuFLConstants;

class HorizontalSpacingCalculatorTest extends UnitTest {

    private static final double TOLERANCE = 0.001;
    private static final int THREE_KEY_ACCIDENTALS = 3;
    private static final int SEVEN_KEY_ACCIDENTALS = 7;
    private static final double PREV_COLUMN_X_SS = 10.0;
    private static final double PLAIN_RIGHT_EXTENT_SS = 2.0;
    private static final double NEGATIVE_LEFT_EXTENT_SS = -0.5;
    // A target left extent negative enough that the glissando reservation, not the default
    // comfortable gap (DEFAULT_COLUMN_GAP_SS), becomes the binding spacing constraint.
    private static final double TARGET_LEFT_EXTENT_SS = -2.0;
    private static final double WIDE_SYLLABLE_WIDTH_SS = 8.0;
    // A narrow syllable, so a gap carrying it needs less room than one between two wide syllables.
    private static final double NARROW_SYLLABLE_WIDTH_SS = 2.0;
    // The gap a column reserves to the next syllable (lyric space width), as ElementColumnBuilder sets it.
    private static final double SYLLABLE_GAP_SS = 0.5;
    // Wider than any real accidental; lets tests exercise the accidental-present path without
    // needing SMuFL font metrics (which require NoteGeometry.initializeAccidentalWidths()).
    private static final double ACCIDENTAL_LEFT_EXTENT_SS = -0.625;
    private static final int TWO_LEDGERS_BELOW_SP = 8;
    private static final double GRACE_RIGHT_EXTENT_SS = 1.0;
    private static final double BEAM_RIGHT_EXTENT_SS = 2.0;
    // A deliberately wide accidental left extent, chosen so the geometric minimum spacing
    // exceeds the comfortable gap and therefore governs (shifting the note head).
    private static final double WIDE_ACCIDENTAL_LEFT_EXTENT_SS = -3.0;
    // A fall extra width small enough that, with the beam-internal default gap (2.5ss), the
    // comfortable spacing still has margin over the minimum gap (1.0ss) and so must not shift
    // the next note (refs #496).
    private static final double FALL_EXTRA_RIGHT_EXTENT_SS = 0.5;
    // A fall extra width wide enough that the minimum-gap floor exceeds the beam-internal
    // default gap, so the minimum-gap algorithm — not the fall's full width — governs the
    // shift (refs #496).
    private static final double WIDE_FALL_EXTRA_RIGHT_EXTENT_SS = 2.0;

    // Row 23 (strengthened): calculatePositions places first column at clef+keyAccidentals+firstNoteOffset
    // (pinned concrete value — not self-referential against calculateFirstElementXSs)
    @Test
    void testCalculatePositionsFirstColumnXSsEqualsClefPlusKeyAccidentalsPlusOffset() {
        var calculator = new HorizontalSpacingCalculator();
        var line = detachedLine(); // C major, 0 accidentals
        var element = ElementType.CROTCHET.newInstance();
        var column = new ElementColumn(
            element,
            Collections.emptyList(),
            0.0,
            SMuFLConstants.NOTE_HEAD_WIDTH_SS,
            0.0, 0.0, null, 0.0, false
        );
        var columns = List.of(column);

        calculator.calculatePositions(columns, line);

        // Concrete expected value: clef width + 0*keyAccidentalWidth + firstNoteOffset
        var expectedXSs = SMuFLConstants.G_CLEF_WIDTH_SS
            + line.getKeyAccidentalCount() * HorizontalSpacingCalculator.KEY_ACCIDENTAL_WIDTH_SS
            + HorizontalSpacingCalculator.FIRST_NOTE_OFFSET_SS;
        assertThat(column.getXSs()).isCloseTo(expectedXSs, within(TOLERANCE));
    }

    // #121: first note with an accidental must have its column's left edge (not its note head)
    // placed at FIRST_NOTE_OFFSET from the header, so the accidental doesn't crowd the key signature.
    @Test
    void testFirstNoteWithAccidentalHasColumnLeftEdgeAtOffset() {
        var calculator = new HorizontalSpacingCalculator();
        var line = detachedLine(); // C major, 0 accidentals
        var element = ElementType.CROTCHET.newInstance();
        element.setAccidental(StaffElement.Accidental.FLAT);
        var column = new ElementColumn(
            element,
            Collections.emptyList(),
            ACCIDENTAL_LEFT_EXTENT_SS,
            SMuFLConstants.NOTE_HEAD_WIDTH_SS,
            0.0, 0.0, null, 0.0, false
        );

        calculator.calculatePositions(List.of(column), line);

        var headerRightEdgeSs = HorizontalSpacingCalculator.calculateHeaderRightEdgeSs(line.getKeyAccidentalCount());
        var expectedLeftEdgeSs = headerRightEdgeSs + HorizontalSpacingCalculator.FIRST_NOTE_OFFSET_SS;
        assertThat(column.getLeftEdgeXSs()).isCloseTo(expectedLeftEdgeSs, within(TOLERANCE));
        // The note head sits one accidental-width right of the offset, so the accidental fills the gap.
        var expectedHeadXSs = expectedLeftEdgeSs + Math.abs(ACCIDENTAL_LEFT_EXTENT_SS);
        assertThat(column.getXSs()).isCloseTo(expectedHeadXSs, within(TOLERANCE));
    }

    // Row 24: calculateHeaderRightEdgeSs(n) = G_CLEF_WIDTH_SS + n * KEY_ACCIDENTAL_WIDTH_SS
    @Test
    void testCalculateHeaderRightEdgeSsWithZeroAccidentals() {
        assertThat(HorizontalSpacingCalculator.calculateHeaderRightEdgeSs(0))
            .isEqualTo(SMuFLConstants.G_CLEF_WIDTH_SS);
    }

    @Test
    void testCalculateHeaderRightEdgeSsWithThreeAccidentals() {
        var expected = SMuFLConstants.G_CLEF_WIDTH_SS + THREE_KEY_ACCIDENTALS * HorizontalSpacingCalculator.KEY_ACCIDENTAL_WIDTH_SS;
        assertThat(HorizontalSpacingCalculator.calculateHeaderRightEdgeSs(THREE_KEY_ACCIDENTALS)).isEqualTo(expected);
    }

    @Test
    void testCalculateHeaderRightEdgeSsWithSevenAccidentals() {
        var expected = SMuFLConstants.G_CLEF_WIDTH_SS + SEVEN_KEY_ACCIDENTALS * HorizontalSpacingCalculator.KEY_ACCIDENTAL_WIDTH_SS;
        assertThat(HorizontalSpacingCalculator.calculateHeaderRightEdgeSs(SEVEN_KEY_ACCIDENTALS)).isEqualTo(expected);
    }

    // Row 25: calculateNextColumnXSs for two plain columns (no accidentals, no lyrics)
    // = prevXSs + prevRightExtent + DEFAULT_COLUMN_GAP_SS
    @Test
    void testCalculateNextColumnXSsTwoPlainColumns() {
        var prevElement = ElementType.CROTCHET.newInstance();
        var currElement = ElementType.CROTCHET.newInstance();
        var prevColumn = new ElementColumn(
            prevElement,
            Collections.emptyList(),
            0.0, SMuFLConstants.NOTE_HEAD_WIDTH_SS,
            0.0, 0.0, null, 0.0, false
        );
        var currColumn = new ElementColumn(
            currElement,
            Collections.emptyList(),
            0.0, SMuFLConstants.NOTE_HEAD_WIDTH_SS,
            0.0, 0.0, null, 0.0, false
        );
        prevColumn.setXSs(PREV_COLUMN_X_SS);

        var expected = PREV_COLUMN_X_SS + SMuFLConstants.NOTE_HEAD_WIDTH_SS + HorizontalSpacingCalculator.DEFAULT_COLUMN_GAP_SS;
        assertThat(HorizontalSpacingCalculator.calculateNextColumnXSs(prevColumn, currColumn))
            .isEqualTo(expected);
    }

    // Row 26: default gap floor dominates over min gap when there are no lyrics
    @Test
    void testDefaultGapFloorDominatesWithoutLyrics() {
        var prevColumn = new ElementColumn(
            ElementType.CROTCHET.newInstance(), Collections.emptyList(),
            0.0, PLAIN_RIGHT_EXTENT_SS, 0.0, 0.0, null, 0.0, false
        );
        var currColumn = new ElementColumn(
            ElementType.CROTCHET.newInstance(), Collections.emptyList(),
            NEGATIVE_LEFT_EXTENT_SS, PLAIN_RIGHT_EXTENT_SS, 0.0, 0.0, null, 0.0, false
        );
        prevColumn.setXSs(PREV_COLUMN_X_SS);

        var result = HorizontalSpacingCalculator.calculateNextColumnXSs(prevColumn, currColumn);

        // Comfortable spacing measures to the note head, so the left extent does not widen it.
        var expectedWithDefault = PREV_COLUMN_X_SS + PLAIN_RIGHT_EXTENT_SS
            + HorizontalSpacingCalculator.DEFAULT_COLUMN_GAP_SS;
        // The minimum-spacing floor does include the full left extent.
        var wouldBeWithMin = PREV_COLUMN_X_SS + PLAIN_RIGHT_EXTENT_SS
            + HorizontalSpacingCalculator.MIN_COLUMN_GAP_SS + Math.abs(NEGATIVE_LEFT_EXTENT_SS);
        assertThat(result).isEqualTo(expectedWithDefault);
        assertThat(result).isGreaterThan(wouldBeWithMin);
    }

    // Row 27: lyric spacing dominates over default gap when syllables are wide
    @Test
    void testLyricSpacingDominatesWithWideSyllables() {
        var prevColumn = new ElementColumn(
            ElementType.CROTCHET.newInstance(), Collections.emptyList(),
            0.0, SMuFLConstants.NOTE_HEAD_WIDTH_SS, 0.0, 0.0, "do", WIDE_SYLLABLE_WIDTH_SS, false
        );
        var currColumn = new ElementColumn(
            ElementType.CROTCHET.newInstance(), Collections.emptyList(),
            0.0, SMuFLConstants.NOTE_HEAD_WIDTH_SS, 0.0, 0.0, "re", WIDE_SYLLABLE_WIDTH_SS, false
        );
        prevColumn.setXSs(PREV_COLUMN_X_SS);
        prevColumn.setMinGapToNextSyllableSs(SYLLABLE_GAP_SS);

        var result = HorizontalSpacingCalculator.calculateNextColumnXSs(prevColumn, currColumn);

        // lyricSpacing = prevHalfWidth + prev gap-to-next + currHalfWidth
        //              = WIDE/2 + SYLLABLE_GAP + WIDE/2 = WIDE + SYLLABLE_GAP
        var expectedLyricSpacing = WIDE_SYLLABLE_WIDTH_SS + SYLLABLE_GAP_SS;
        var expectedXSs = PREV_COLUMN_X_SS + expectedLyricSpacing;
        var wouldBeWithDefault = PREV_COLUMN_X_SS + SMuFLConstants.NOTE_HEAD_WIDTH_SS
            + HorizontalSpacingCalculator.DEFAULT_COLUMN_GAP_SS;
        assertThat(result).isEqualTo(expectedXSs);
        assertThat(result).isGreaterThan(wouldBeWithDefault);
    }

    // A syllable following a lyric-less element clears it by the previous column's reserved
    // space-width gap rather than an arbitrary minimum (#430 follow-up).
    @Test
    void testLyricSpacingUsesPrevColumnGapWhenPrevHasNoLyric() {
        var prevColumn = new ElementColumn(
            ElementType.CROTCHET.newInstance(), Collections.emptyList(),
            0.0, SMuFLConstants.NOTE_HEAD_WIDTH_SS, 0.0, 0.0, null, 0.0, false
        );
        var currColumn = new ElementColumn(
            ElementType.CROTCHET.newInstance(), Collections.emptyList(),
            0.0, SMuFLConstants.NOTE_HEAD_WIDTH_SS, 0.0, 0.0, "re", WIDE_SYLLABLE_WIDTH_SS, false
        );
        prevColumn.setXSs(PREV_COLUMN_X_SS);
        prevColumn.setMinGapToNextSyllableSs(SYLLABLE_GAP_SS);

        var result = HorizontalSpacingCalculator.calculateNextColumnXSs(prevColumn, currColumn);

        // prev has no syllable → prevHalfWidth = 0; gap = prev column's reserved space-width gap.
        var expectedLyricSpacing = SYLLABLE_GAP_SS + WIDE_SYLLABLE_WIDTH_SS / 2.0;
        assertThat(result).isEqualTo(PREV_COLUMN_X_SS + expectedLyricSpacing);
    }

    // Row 28: the comfortable gap is measured to the note head, so an accidental does not inflate
    // default spacing. The geometric minimum (full left extent) is still a hard floor.
    @Test
    void testAccidentalClearanceSatisfiedByDefaultGap() {
        var currElement = ElementType.CROTCHET.newInstance();
        currElement.setAccidental(StaffElement.Accidental.SHARP);
        var prevColumn = new ElementColumn(
            ElementType.CROTCHET.newInstance(), Collections.emptyList(),
            0.0, PLAIN_RIGHT_EXTENT_SS, 0.0, 0.0, null, 0.0, false
        );
        var currColumn = new ElementColumn(
            currElement, Collections.emptyList(),
            ACCIDENTAL_LEFT_EXTENT_SS, SMuFLConstants.NOTE_HEAD_WIDTH_SS, 0.0, 0.0, null, 0.0, false
        );
        prevColumn.setXSs(PREV_COLUMN_X_SS);

        var result = HorizontalSpacingCalculator.calculateNextColumnXSs(prevColumn, currColumn);

        // Comfortable spacing measures to the note head, not the accidental; the minimum uses the
        // full extent. Comfortable dominates here: prevRight + DEFAULT_GAP.
        var expectedXSs = PREV_COLUMN_X_SS + PLAIN_RIGHT_EXTENT_SS
            + HorizontalSpacingCalculator.DEFAULT_COLUMN_GAP_SS;
        assertThat(result).isEqualTo(expectedXSs);
        // Invariant: accidental left edge >= prevRightEdge + the minimum column gap
        var prevRightEdge = PREV_COLUMN_X_SS + PLAIN_RIGHT_EXTENT_SS;
        var accidentalLeft = result + ACCIDENTAL_LEFT_EXTENT_SS;
        assertThat(accidentalLeft).isGreaterThanOrEqualTo(
            prevRightEdge + HorizontalSpacingCalculator.MIN_COLUMN_GAP_SS
        );
    }

    // #418: a normal accidental that fits within the default gap must not shift the head.
    // The column with an accidental has the same xSs as one without — comfortable spacing
    // (measured to the note head) dominates over minimum spacing for both.
    @Test
    void testAccidentalWithinDefaultGapDoesNotShiftHead() {
        var prevColumn = new ElementColumn(
            ElementType.CROTCHET.newInstance(), Collections.emptyList(),
            0.0, PLAIN_RIGHT_EXTENT_SS, 0.0, 0.0, null, 0.0, false
        );
        // Column without accidental: leftExtent = 0.0
        var plainColumn = new ElementColumn(
            ElementType.CROTCHET.newInstance(), Collections.emptyList(),
            0.0, PLAIN_RIGHT_EXTENT_SS, 0.0, 0.0, null, 0.0, false
        );
        // Column with accidental: leftExtent = ACCIDENTAL_LEFT_EXTENT_SS
        var accidentalElement = ElementType.CROTCHET.newInstance();
        accidentalElement.setAccidental(StaffElement.Accidental.SHARP);
        var accidentalColumn = new ElementColumn(
            accidentalElement, Collections.emptyList(),
            ACCIDENTAL_LEFT_EXTENT_SS, PLAIN_RIGHT_EXTENT_SS, 0.0, 0.0, null, 0.0, false
        );
        prevColumn.setXSs(PREV_COLUMN_X_SS);

        var plainXSs = HorizontalSpacingCalculator.calculateNextColumnXSs(prevColumn, plainColumn);
        var accidentalXSs = HorizontalSpacingCalculator.calculateNextColumnXSs(prevColumn, accidentalColumn);

        // Both must land at the comfortable default-gap position (refs #418)
        assertThat(accidentalXSs)
            .as("accidental head x should match plain head x (no shift)")
            .isCloseTo(plainXSs, within(TOLERANCE));
    }

    // #418: a very wide accidental (minimum spacing dominates) shifts the head by exactly the
    // minimum-space overflow, and the accidental's left edge is MIN_COLUMN_GAP_SS clear
    // of the previous column's right edge.
    @Test
    void testWideAccidentalMinimumSpacingDominatesAndLeavesAccidentalClear() {
        // leftExtentSs wide enough that min spacing > comfortable spacing:
        // min = prevRight + MIN_GAP + 3.0 = 2.0 + 1.0 + 3.0 = 6.0
        // comfortable = prevRight + DEFAULT_GAP = 2.0 + 2.5 = 4.5
        // so minimum dominates.
        var wideLeftExtentSs = WIDE_ACCIDENTAL_LEFT_EXTENT_SS;
        var prevColumn = new ElementColumn(
            ElementType.CROTCHET.newInstance(), Collections.emptyList(),
            0.0, PLAIN_RIGHT_EXTENT_SS, 0.0, 0.0, null, 0.0, false
        );
        var wideColumn = new ElementColumn(
            ElementType.CROTCHET.newInstance(), Collections.emptyList(),
            wideLeftExtentSs, PLAIN_RIGHT_EXTENT_SS, 0.0, 0.0, null, 0.0, false
        );
        var plainColumn = new ElementColumn(
            ElementType.CROTCHET.newInstance(), Collections.emptyList(),
            0.0, PLAIN_RIGHT_EXTENT_SS, 0.0, 0.0, null, 0.0, false
        );
        prevColumn.setXSs(PREV_COLUMN_X_SS);

        var wideXSs = HorizontalSpacingCalculator.calculateNextColumnXSs(prevColumn, wideColumn);
        var plainXSs = HorizontalSpacingCalculator.calculateNextColumnXSs(prevColumn, plainColumn);

        var comfortableSpacingSs = PLAIN_RIGHT_EXTENT_SS + HorizontalSpacingCalculator.DEFAULT_COLUMN_GAP_SS;
        var minimumSpacingSs = PLAIN_RIGHT_EXTENT_SS + HorizontalSpacingCalculator.MIN_COLUMN_GAP_SS
            + Math.abs(wideLeftExtentSs);
        var expectedShiftSs = minimumSpacingSs - comfortableSpacingSs;

        // Head shifts by exactly the minimum-space overflow (refs #418)
        assertThat(wideXSs).isEqualTo(plainXSs + expectedShiftSs);
        // Accidental left edge is exactly MIN_COLUMN_GAP_SS clear of the previous right edge
        var prevRightEdgeSs = PREV_COLUMN_X_SS + PLAIN_RIGHT_EXTENT_SS;
        var accidentalLeftEdgeSs = wideXSs + wideLeftExtentSs;
        assertThat(accidentalLeftEdgeSs).isCloseTo(
            prevRightEdgeSs + HorizontalSpacingCalculator.MIN_COLUMN_GAP_SS,
            within(TOLERANCE)
        );
    }

    // #418 boundary: at the exact crossover where comfortable spacing equals minimum spacing,
    // the head still lands at the comfortable default-gap position and the accidental's left edge
    // is exactly MIN_COLUMN_GAP_SS clear of the previous column's right edge. Pins the
    // Math.max(comfortable, minimum) decision at its tipping point.
    @Test
    void testAccidentalAtComfortableMinimumBoundaryLeavesAccidentalExactlyClear() {
        // abs(leftExtent) = DEFAULT_GAP - MIN_GAP makes minimum spacing equal comfortable spacing.
        var boundaryLeftExtentSs = -(HorizontalSpacingCalculator.DEFAULT_COLUMN_GAP_SS
            - HorizontalSpacingCalculator.MIN_COLUMN_GAP_SS);
        var prevColumn = new ElementColumn(
            ElementType.CROTCHET.newInstance(), Collections.emptyList(),
            0.0, PLAIN_RIGHT_EXTENT_SS, 0.0, 0.0, null, 0.0, false
        );
        var boundaryColumn = new ElementColumn(
            ElementType.CROTCHET.newInstance(), Collections.emptyList(),
            boundaryLeftExtentSs, PLAIN_RIGHT_EXTENT_SS, 0.0, 0.0, null, 0.0, false
        );
        prevColumn.setXSs(PREV_COLUMN_X_SS);

        var result = HorizontalSpacingCalculator.calculateNextColumnXSs(prevColumn, boundaryColumn);

        // Comfortable and minimum coincide: head lands at the comfortable default-gap position.
        var expectedXSs = PREV_COLUMN_X_SS + PLAIN_RIGHT_EXTENT_SS
            + HorizontalSpacingCalculator.DEFAULT_COLUMN_GAP_SS;
        assertThat(result).isCloseTo(expectedXSs, within(TOLERANCE));
        // The accidental's left edge is exactly MIN_COLUMN_GAP_SS clear of the previous right edge.
        var prevRightEdgeSs = PREV_COLUMN_X_SS + PLAIN_RIGHT_EXTENT_SS;
        var accidentalLeftEdgeSs = result + boundaryLeftExtentSs;
        assertThat(accidentalLeftEdgeSs).isCloseTo(
            prevRightEdgeSs + HorizontalSpacingCalculator.MIN_COLUMN_GAP_SS,
            within(TOLERANCE)
        );
    }

    // Row 29: grace note to host note uses GRACE_NOTE_GAP_SS (tighter than DEFAULT_COLUMN_GAP_SS)
    @Test
    void testGraceNoteToHostNoteUsesGraceNoteGap() {
        var prevColumn = new ElementColumn(
            ElementType.GRACE_QUAVER.newInstance(), Collections.emptyList(),
            0.0, GRACE_RIGHT_EXTENT_SS, 0.0, 0.0, null, 0.0, false
        );
        var currColumn = new ElementColumn(
            ElementType.CROTCHET.newInstance(), Collections.emptyList(),
            0.0, SMuFLConstants.NOTE_HEAD_WIDTH_SS, 0.0, 0.0, null, 0.0, false
        );
        prevColumn.setXSs(PREV_COLUMN_X_SS);

        var result = HorizontalSpacingCalculator.calculateNextColumnXSs(prevColumn, currColumn);

        var expected = PREV_COLUMN_X_SS + GRACE_RIGHT_EXTENT_SS + HorizontalSpacingCalculator.GRACE_NOTE_GAP_SS;
        assertThat(result).isEqualTo(expected);
        // Grace note gap is tighter than the default gap
        assertThat(HorizontalSpacingCalculator.GRACE_NOTE_GAP_SS)
            .isLessThan(HorizontalSpacingCalculator.DEFAULT_COLUMN_GAP_SS);
    }

    // Row 30: glissando spacing is enforced within beam group (tight gap < MIN_GLISSANDO_RESERVATION_SS).
    // Two beamed semiquavers (both shorter than an eighth) take the tight internal gap (1.5ss),
    // which is < MIN_GLISSANDO_RESERVATION_SS (1.6ss), so a glissando on the prev column forces
    // extra spacing.
    @Test
    void testGlissandoSpacingEnforcedInBeamGroup() {
        var calculator = new HorizontalSpacingCalculator();
        var line = detachedLine();

        var glissandoElement = ElementType.SEMIQUAVER.newInstance();
        glissandoElement.setGlissando();

        var col0 = new ElementColumn(
            ElementType.CROTCHET.newInstance(), Collections.emptyList(),
            0.0, PLAIN_RIGHT_EXTENT_SS, 0.0, 0.0, null, 0.0, false
        );
        var col1 = new ElementColumn(
            glissandoElement, Collections.emptyList(),
            0.0, BEAM_RIGHT_EXTENT_SS, 0.0, 0.0, null, 0.0, true
        );
        var col2 = new ElementColumn(
            ElementType.SEMIQUAVER.newInstance(), Collections.emptyList(),
            0.0, BEAM_RIGHT_EXTENT_SS, 0.0, 0.0, null, 0.0, true
        );

        calculator.calculatePositions(List.of(col0, col1, col2), line);

        // Beam tight gap = 1.5ss; MIN_GLISSANDO_RESERVATION = 1.6ss → extra 0.1ss added
        var expectedSpacing = BEAM_RIGHT_EXTENT_SS + NoteGeometry.MIN_GLISSANDO_RESERVATION_SS;
        assertThat(col2.getXSs() - col1.getXSs()).isCloseTo(expectedSpacing, within(TOLERANCE));
        assertThat(col2.getXSs() - col1.getXSs())
            .isGreaterThan(BEAM_RIGHT_EXTENT_SS + HorizontalSpacingCalculator.BEAM_GROUP_MIN_INTERNAL_GAP_SS);
    }

    // Glissando + ledger note: the reservation uses the column's own right extent, not the wider
    // ledger base extent (ledger lines are reference marks, not ink the glissando must avoid).
    @Test
    void testGlissandoOnLedgerNote_reservesAgainstColumnRightExtentOnly() {
        var calculator = new HorizontalSpacingCalculator();
        var line = detachedLine();

        var ledgerElement = ElementType.CROTCHET.newInstance();
        ledgerElement.setGlissando();
        ledgerElement.setStaffPosition(TWO_LEDGERS_BELOW_SP);

        var ledgerBaseExtent = NoteGeometry.getLedgerLineBaseExtentSs(ledgerElement);
        var ledgerRightSs = ledgerBaseExtent.rightSs();

        // Column right extent is narrower than the ledger base right so the test can distinguish
        // whether the old ledger-inflated path or the new column-only path governs.
        var columnRightExtentSs = ledgerRightSs - 1.0;

        var col0 = new ElementColumn(
            ElementType.CROTCHET.newInstance(), Collections.emptyList(),
            0.0, PLAIN_RIGHT_EXTENT_SS, 0.0, 0.0, null, 0.0, false
        );
        // rightExtentExcludingAugmentation = 0 keeps the default-spacing floor at
        // DEFAULT_COLUMN_GAP_SS (2.5), below the glissando-required spacing; that makes the
        // glissando reservation the binding constraint rather than the comfortable default.
        var col1 = new ElementColumn(
            ledgerElement, Collections.emptyList(),
            0.0, columnRightExtentSs, 0.0, 0.0, 0.0, null, 0.0, false
        );
        // Negative target left extent so the glissando-required spacing exceeds the default floor.
        var col2 = new ElementColumn(
            ElementType.CROTCHET.newInstance(), Collections.emptyList(),
            TARGET_LEFT_EXTENT_SS, PLAIN_RIGHT_EXTENT_SS, 0.0, 0.0, null, 0.0, false
        );

        calculator.calculatePositions(List.of(col0, col1, col2), line);

        // prevGlissRight = col1.rightExtent = columnRightExtentSs (ledger lines excluded).
        // gap = spacing + col2.leftExtent - columnRightExtentSs; must satisfy gap >= MIN_GLISSANDO_RESERVATION_SS,
        // i.e. spacing >= columnRightExtentSs - col2.leftExtent + MIN_GLISSANDO_RESERVATION_SS.
        var columnMinSpacing =
            columnRightExtentSs - TARGET_LEFT_EXTENT_SS + NoteGeometry.MIN_GLISSANDO_RESERVATION_SS;
        var ledgerInflatedMinSpacing =
            ledgerRightSs - TARGET_LEFT_EXTENT_SS + NoteGeometry.MIN_GLISSANDO_RESERVATION_SS;
        var actualSpacing = col2.getXSs() - col1.getXSs();

        assertThat(actualSpacing)
            .as("glissando on a ledger note must reserve against the column right extent")
            .isGreaterThanOrEqualTo(columnMinSpacing - TOLERANCE);
        assertThat(actualSpacing)
            .as("glissando on a ledger note must NOT inflate spacing by the ledger base extent")
            .isLessThan(ledgerInflatedMinSpacing);
    }

    // Glissando into a ledger note: the reservation uses the column's own left extent, not the
    // wider ledger base extent (ledger lines are reference marks, not ink the glissando must avoid).
    @Test
    void testGlissandoIntoLedgerNote_reservesAgainstColumnLeftExtentOnly() {
        var calculator = new HorizontalSpacingCalculator();
        var line = detachedLine();

        var glissandoElement = ElementType.CROTCHET.newInstance();
        glissandoElement.setGlissando();

        var ledgerElement = ElementType.CROTCHET.newInstance();
        ledgerElement.setStaffPosition(TWO_LEDGERS_BELOW_SP);

        var ledgerBaseExtent = NoteGeometry.getLedgerLineBaseExtentSs(ledgerElement);
        var ledgerLeftSs = ledgerBaseExtent.leftSs();  // negative: extends left of notehead

        // Column left extent is less negative than the ledger base left so the test can distinguish
        // whether the old ledger-inflated path or the new column-only path governs.
        var columnLeftExtentSs = ledgerLeftSs + 1.0;

        var col0 = new ElementColumn(
            ElementType.CROTCHET.newInstance(), Collections.emptyList(),
            0.0, PLAIN_RIGHT_EXTENT_SS, 0.0, 0.0, null, 0.0, false
        );
        // rightExtentExcludingAugmentation = 0 keeps the default-spacing floor at
        // DEFAULT_COLUMN_GAP_SS (2.5), below the ledger-inflated minimum; that makes
        // the glissando rule the binding constraint under the old ledger-inclusive path.
        var col1 = new ElementColumn(
            glissandoElement, Collections.emptyList(),
            0.0, PLAIN_RIGHT_EXTENT_SS, 0.0, 0.0, 0.0, null, 0.0, false
        );
        var col2 = new ElementColumn(
            ledgerElement, Collections.emptyList(),
            columnLeftExtentSs, PLAIN_RIGHT_EXTENT_SS, 0.0, 0.0, null, 0.0, false
        );

        calculator.calculatePositions(List.of(col0, col1, col2), line);

        // currGlissLeft = col2.leftExtent = columnLeftExtentSs (ledger lines excluded).
        // gap = spacing + columnLeftExtentSs - PLAIN_RIGHT_EXTENT_SS; must satisfy gap >= MIN_GLISSANDO_RESERVATION_SS.
        var columnMinSpacing = PLAIN_RIGHT_EXTENT_SS - columnLeftExtentSs + NoteGeometry.MIN_GLISSANDO_RESERVATION_SS;
        var ledgerInflatedMinSpacing = PLAIN_RIGHT_EXTENT_SS - ledgerLeftSs + NoteGeometry.MIN_GLISSANDO_RESERVATION_SS;
        var actualSpacing = col2.getXSs() - col1.getXSs();

        assertThat(actualSpacing)
            .as("glissando into a ledger note must reserve against the column left extent")
            .isGreaterThanOrEqualTo(columnMinSpacing - TOLERANCE);
        assertThat(actualSpacing)
            .as("glissando into a ledger note must NOT inflate spacing by the ledger base extent")
            .isLessThan(ledgerInflatedMinSpacing);
    }

    // Row 31: calculatePositions with empty list returns without exception
    @Test
    void testCalculatePositionsEmptyListDoesNotThrow() {
        var calculator = new HorizontalSpacingCalculator();
        var line = detachedLine();
        assertThatCode(() -> calculator.calculatePositions(Collections.emptyList(), line))
            .doesNotThrowAnyException();
    }

    // calculatePositions: two plain non-beamed columns — second column gets the normal
    // column-to-column spacing (exercises the else branch of the beam-group dispatch).
    @Test
    void testCalculatePositionsTwoPlainColumnsUsesDefaultSpacing() {
        var calculator = new HorizontalSpacingCalculator();
        var line = detachedLine();

        var col0 = new ElementColumn(
            ElementType.CROTCHET.newInstance(), Collections.emptyList(),
            0.0, PLAIN_RIGHT_EXTENT_SS, 0.0, 0.0, null, 0.0, false
        );
        var col1 = new ElementColumn(
            ElementType.CROTCHET.newInstance(), Collections.emptyList(),
            0.0, PLAIN_RIGHT_EXTENT_SS, 0.0, 0.0, null, 0.0, false
        );

        calculator.calculatePositions(List.of(col0, col1), line);

        var expectedCol1XSs = col0.getXSs() + PLAIN_RIGHT_EXTENT_SS + HorizontalSpacingCalculator.DEFAULT_COLUMN_GAP_SS;
        assertThat(col1.getXSs()).isCloseTo(expectedCol1XSs, within(TOLERANCE));
    }

    // #418 (without lyrics): a beam group of eighth notes packs at the same default gap as
    // unbeamed notes.
    @Test
    void testBeamGroupEighthNotesUseDefaultGap() {
        var calculator = new HorizontalSpacingCalculator();
        var line = detachedLine();

        var col0 = new ElementColumn(
            ElementType.CROTCHET.newInstance(), Collections.emptyList(),
            0.0, PLAIN_RIGHT_EXTENT_SS, 0.0, 0.0, null, 0.0, false
        );
        var col1 = new ElementColumn(
            ElementType.QUAVER.newInstance(), Collections.emptyList(),
            0.0, BEAM_RIGHT_EXTENT_SS, 0.0, 0.0, null, 0.0, true
        );
        var col2 = new ElementColumn(
            ElementType.QUAVER.newInstance(), Collections.emptyList(),
            0.0, BEAM_RIGHT_EXTENT_SS, 0.0, 0.0, null, 0.0, true
        );

        calculator.calculatePositions(List.of(col0, col1, col2), line);

        var defaultSpacing = BEAM_RIGHT_EXTENT_SS + HorizontalSpacingCalculator.DEFAULT_COLUMN_GAP_SS;
        assertThat(col2.getXSs() - col1.getXSs()).isCloseTo(defaultSpacing, within(TOLERANCE));
    }

    // #418 (without lyrics): a beam group of notes shorter than an eighth (e.g. sixteenths)
    // keeps the tighter beam-internal gap, smaller than the default note-to-note gap.
    @Test
    void testBeamGroupShorterThanEighthUsesTightGap() {
        var calculator = new HorizontalSpacingCalculator();
        var line = detachedLine();

        var col0 = new ElementColumn(
            ElementType.CROTCHET.newInstance(), Collections.emptyList(),
            0.0, PLAIN_RIGHT_EXTENT_SS, 0.0, 0.0, null, 0.0, false
        );
        var col1 = new ElementColumn(
            ElementType.SEMIQUAVER.newInstance(), Collections.emptyList(),
            0.0, BEAM_RIGHT_EXTENT_SS, 0.0, 0.0, null, 0.0, true
        );
        var col2 = new ElementColumn(
            ElementType.SEMIQUAVER.newInstance(), Collections.emptyList(),
            0.0, BEAM_RIGHT_EXTENT_SS, 0.0, 0.0, null, 0.0, true
        );

        calculator.calculatePositions(List.of(col0, col1, col2), line);

        var tightSpacing = BEAM_RIGHT_EXTENT_SS + HorizontalSpacingCalculator.BEAM_GROUP_MIN_INTERNAL_GAP_SS;
        assertThat(col2.getXSs() - col1.getXSs()).isCloseTo(tightSpacing, within(TOLERANCE));
        // Tight beam gap is smaller than the default note-to-note gap
        assertThat(col2.getXSs() - col1.getXSs())
            .isLessThan(BEAM_RIGHT_EXTENT_SS + HorizontalSpacingCalculator.DEFAULT_COLUMN_GAP_SS);
    }

    // #418 (without lyrics): in a mixed beam group the LONGER note of each pair governs the gap.
    // Quaver → semiquaver uses the default gap (the quaver governs); semiquaver → semiquaver uses
    // the tight gap (both shorter than an eighth). Mirrors the issue example "quaver, semiquaver,
    // semiquaver — the space between the quaver and the semiquaver should be the normal space".
    @Test
    void testBeamGroupQuaverThenSemiquaversSpacesQuaverPairNormally() {
        var calculator = new HorizontalSpacingCalculator();
        var line = detachedLine();

        var col0 = new ElementColumn(
            ElementType.CROTCHET.newInstance(), Collections.emptyList(),
            0.0, PLAIN_RIGHT_EXTENT_SS, 0.0, 0.0, null, 0.0, false
        );
        var col1 = new ElementColumn(
            ElementType.QUAVER.newInstance(), Collections.emptyList(),
            0.0, BEAM_RIGHT_EXTENT_SS, 0.0, 0.0, null, 0.0, true
        );
        var col2 = new ElementColumn(
            ElementType.SEMIQUAVER.newInstance(), Collections.emptyList(),
            0.0, BEAM_RIGHT_EXTENT_SS, 0.0, 0.0, null, 0.0, true
        );
        var col3 = new ElementColumn(
            ElementType.SEMIQUAVER.newInstance(), Collections.emptyList(),
            0.0, BEAM_RIGHT_EXTENT_SS, 0.0, 0.0, null, 0.0, true
        );

        calculator.calculatePositions(List.of(col0, col1, col2, col3), line);

        // Quaver → semiquaver: the longer note (quaver) governs → default gap
        var defaultSpacing = BEAM_RIGHT_EXTENT_SS + HorizontalSpacingCalculator.DEFAULT_COLUMN_GAP_SS;
        assertThat(col2.getXSs() - col1.getXSs()).isCloseTo(defaultSpacing, within(TOLERANCE));
        // Semiquaver → semiquaver: both shorter than an eighth → tight gap
        var tightSpacing = BEAM_RIGHT_EXTENT_SS + HorizontalSpacingCalculator.BEAM_GROUP_MIN_INTERNAL_GAP_SS;
        assertThat(col3.getXSs() - col2.getXSs()).isCloseTo(tightSpacing, within(TOLERANCE));
    }

    // #418 (without lyrics): the LONGER note governs regardless of order — the quaver wins even
    // when it is the second note of the pair. Semiquaver → semiquaver uses the tight gap;
    // semiquaver → quaver uses the default gap. Mirrors the issue example "two semiquavers
    // followed by a quaver".
    @Test
    void testBeamGroupSemiquaversThenQuaverSpacesQuaverPairNormally() {
        var calculator = new HorizontalSpacingCalculator();
        var line = detachedLine();

        var col0 = new ElementColumn(
            ElementType.CROTCHET.newInstance(), Collections.emptyList(),
            0.0, PLAIN_RIGHT_EXTENT_SS, 0.0, 0.0, null, 0.0, false
        );
        var col1 = new ElementColumn(
            ElementType.SEMIQUAVER.newInstance(), Collections.emptyList(),
            0.0, BEAM_RIGHT_EXTENT_SS, 0.0, 0.0, null, 0.0, true
        );
        var col2 = new ElementColumn(
            ElementType.SEMIQUAVER.newInstance(), Collections.emptyList(),
            0.0, BEAM_RIGHT_EXTENT_SS, 0.0, 0.0, null, 0.0, true
        );
        var col3 = new ElementColumn(
            ElementType.QUAVER.newInstance(), Collections.emptyList(),
            0.0, BEAM_RIGHT_EXTENT_SS, 0.0, 0.0, null, 0.0, true
        );

        calculator.calculatePositions(List.of(col0, col1, col2, col3), line);

        // Semiquaver → semiquaver: both shorter than an eighth → tight gap
        var tightSpacing = BEAM_RIGHT_EXTENT_SS + HorizontalSpacingCalculator.BEAM_GROUP_MIN_INTERNAL_GAP_SS;
        assertThat(col2.getXSs() - col1.getXSs()).isCloseTo(tightSpacing, within(TOLERANCE));
        // Semiquaver → quaver: the longer note (quaver) governs → default gap
        var defaultSpacing = BEAM_RIGHT_EXTENT_SS + HorizontalSpacingCalculator.DEFAULT_COLUMN_GAP_SS;
        assertThat(col3.getXSs() - col2.getXSs()).isCloseTo(defaultSpacing, within(TOLERANCE));
    }

    // #496: a fall on a beamed note must not shift the next beamed note by the fall's full
    // width. The comfortable beam-internal gap is measured to the note head, excluding the
    // fall, the same way it already excludes augmentation dots for unbeamed notes — so a fall
    // that fits within the comfortable gap does not move the next note at all.
    @Test
    void testBeamGroupFallWithinDefaultGapDoesNotShiftNextNote() {
        var calculator = new HorizontalSpacingCalculator();

        var col0 = new ElementColumn(
            ElementType.CROTCHET.newInstance(), Collections.emptyList(),
            0.0, PLAIN_RIGHT_EXTENT_SS, 0.0, 0.0, null, 0.0, false
        );
        var noFallColumn = new ElementColumn(
            ElementType.QUAVER.newInstance(), Collections.emptyList(),
            0.0, BEAM_RIGHT_EXTENT_SS, 0.0, 0.0, null, 0.0, true
        );
        var noFallNext = new ElementColumn(
            ElementType.QUAVER.newInstance(), Collections.emptyList(),
            0.0, BEAM_RIGHT_EXTENT_SS, 0.0, 0.0, null, 0.0, true
        );
        calculator.calculatePositions(List.of(col0, noFallColumn, noFallNext), detachedLine());

        var fallElement = ElementType.QUAVER.newInstance();
        fallElement.setFall();
        // rightExtentSs includes the fall; rightExtentExcludingAugmentationSs does not.
        var fallColumn = new ElementColumn(
            fallElement, Collections.emptyList(),
            0.0, BEAM_RIGHT_EXTENT_SS + FALL_EXTRA_RIGHT_EXTENT_SS, BEAM_RIGHT_EXTENT_SS,
            0.0, 0.0, null, 0.0, true
        );
        var fallNext = new ElementColumn(
            ElementType.QUAVER.newInstance(), Collections.emptyList(),
            0.0, BEAM_RIGHT_EXTENT_SS, 0.0, 0.0, null, 0.0, true
        );
        calculator.calculatePositions(List.of(col0, fallColumn, fallNext), detachedLine());

        assertThat(fallNext.getXSs() - fallColumn.getXSs())
            .as("a fall that fits within the comfortable gap must not shift the next note")
            .isCloseTo(noFallNext.getXSs() - noFallColumn.getXSs(), within(TOLERANCE));
    }

    // #496: when a fall is wide enough to violate the minimum gap, the next beamed note is
    // shifted only as far as the minimum-gap algorithm requires — not by the fall's full width
    // added on top of the comfortable gap.
    @Test
    void testBeamGroupWideFallShiftsOnlyToMaintainMinimumGap() {
        var calculator = new HorizontalSpacingCalculator();

        var col0 = new ElementColumn(
            ElementType.CROTCHET.newInstance(), Collections.emptyList(),
            0.0, PLAIN_RIGHT_EXTENT_SS, 0.0, 0.0, null, 0.0, false
        );
        var fallElement = ElementType.QUAVER.newInstance();
        fallElement.setFall();
        var fallRightExtentSs = BEAM_RIGHT_EXTENT_SS + WIDE_FALL_EXTRA_RIGHT_EXTENT_SS;
        var fallColumn = new ElementColumn(
            fallElement, Collections.emptyList(),
            0.0, fallRightExtentSs, BEAM_RIGHT_EXTENT_SS, 0.0, 0.0, null, 0.0, true
        );
        var nextColumn = new ElementColumn(
            ElementType.QUAVER.newInstance(), Collections.emptyList(),
            0.0, BEAM_RIGHT_EXTENT_SS, 0.0, 0.0, null, 0.0, true
        );

        calculator.calculatePositions(List.of(col0, fallColumn, nextColumn), detachedLine());

        var minimumGapSpacing = fallRightExtentSs + HorizontalSpacingCalculator.MIN_COLUMN_GAP_SS;
        var actualSpacing = nextColumn.getXSs() - fallColumn.getXSs();

        assertThat(actualSpacing)
            .as("a wide fall must shift the next note only as far as the minimum-gap floor requires")
            .isCloseTo(minimumGapSpacing, within(TOLERANCE));
        var fallInflatedComfortableSpacing =
            fallRightExtentSs + HorizontalSpacingCalculator.DEFAULT_COLUMN_GAP_SS;
        assertThat(actualSpacing)
            .as("must not also add the comfortable beam gap on top of the fall's full width")
            .isLessThan(fallInflatedComfortableSpacing);
    }

    // #418: two adjacent beam groups must not be merged. A quaver group followed by a separate
    // semiquaver group: the first note of the second group is spaced from the previous group
    // with the normal note-to-note gap, not a (tight) beam-internal gap.
    @Test
    void testAdjacentBeamGroupsAreNotMerged() {
        var firstBeamGroupId = 0;
        var secondBeamGroupId = 1;
        var calculator = new HorizontalSpacingCalculator();
        var line = detachedLine();

        var col0 = new ElementColumn(
            ElementType.CROTCHET.newInstance(), Collections.emptyList(),
            0.0, PLAIN_RIGHT_EXTENT_SS, 0.0, 0.0, null, 0.0, false
        );
        // Group A: two beamed quavers
        var colA1 = new ElementColumn(
            ElementType.QUAVER.newInstance(), Collections.emptyList(),
            0.0, BEAM_RIGHT_EXTENT_SS, 0.0, 0.0, null, 0.0, true
        );
        colA1.setBeamGroupId(firstBeamGroupId);
        var colA2 = new ElementColumn(
            ElementType.QUAVER.newInstance(), Collections.emptyList(),
            0.0, BEAM_RIGHT_EXTENT_SS, 0.0, 0.0, null, 0.0, true
        );
        colA2.setBeamGroupId(firstBeamGroupId);
        // Group B: two beamed semiquavers, a separate beam group
        var colB1 = new ElementColumn(
            ElementType.SEMIQUAVER.newInstance(), Collections.emptyList(),
            0.0, BEAM_RIGHT_EXTENT_SS, 0.0, 0.0, null, 0.0, true
        );
        colB1.setBeamGroupId(secondBeamGroupId);
        var colB2 = new ElementColumn(
            ElementType.SEMIQUAVER.newInstance(), Collections.emptyList(),
            0.0, BEAM_RIGHT_EXTENT_SS, 0.0, 0.0, null, 0.0, true
        );
        colB2.setBeamGroupId(secondBeamGroupId);

        calculator.calculatePositions(List.of(col0, colA1, colA2, colB1, colB2), line);

        var defaultSpacing = BEAM_RIGHT_EXTENT_SS + HorizontalSpacingCalculator.DEFAULT_COLUMN_GAP_SS;
        var tightSpacing = BEAM_RIGHT_EXTENT_SS + HorizontalSpacingCalculator.BEAM_GROUP_MIN_INTERNAL_GAP_SS;

        // Within group A (both quavers): default gap
        assertThat(colA2.getXSs() - colA1.getXSs()).isCloseTo(defaultSpacing, within(TOLERANCE));
        // Boundary between the two groups: normal note-to-note spacing, NOT a tight beam gap
        assertThat(colB1.getXSs() - colA2.getXSs()).isCloseTo(defaultSpacing, within(TOLERANCE));
        // Within group B (both semiquavers): tight beam gap
        assertThat(colB2.getXSs() - colB1.getXSs()).isCloseTo(tightSpacing, within(TOLERANCE));
    }

    // #444: a beam group anchored at the very first column on a line must still get beam-internal
    // spacing. The main positioning loop starts at the second column, so a group starting at column
    // 0 would otherwise never be treated as a beam group and its notes would fall back to the
    // default note-to-note gap instead of the tight beam gap.
    @Test
    void testFirstColumnBeamGroupGetsBeamInternalSpacing() {
        var calculator = new HorizontalSpacingCalculator();
        var line = detachedLine();

        var col0 = new ElementColumn(
            ElementType.SEMIQUAVER.newInstance(), Collections.emptyList(),
            0.0, BEAM_RIGHT_EXTENT_SS, 0.0, 0.0, null, 0.0, true
        );
        var col1 = new ElementColumn(
            ElementType.SEMIQUAVER.newInstance(), Collections.emptyList(),
            0.0, BEAM_RIGHT_EXTENT_SS, 0.0, 0.0, null, 0.0, true
        );

        calculator.calculatePositions(List.of(col0, col1), line);

        var tightSpacing = BEAM_RIGHT_EXTENT_SS + HorizontalSpacingCalculator.BEAM_GROUP_MIN_INTERNAL_GAP_SS;
        assertThat(col1.getXSs() - col0.getXSs()).isCloseTo(tightSpacing, within(TOLERANCE));
        // Tight beam gap is smaller than the default note-to-note gap
        assertThat(col1.getXSs() - col0.getXSs())
            .isLessThan(BEAM_RIGHT_EXTENT_SS + HorizontalSpacingCalculator.DEFAULT_COLUMN_GAP_SS);
    }

    // #121: a first note that is both beamed and carries an accidental must also have its column's
    // left edge (not its note head) at FIRST_NOTE_OFFSET from the header. The first-column beam
    // path routes the adjusted firstXSs through handleBeamGroup, which re-sets the first column — so
    // a regression there (e.g. passing the unadjusted header offset) would crowd the key signature
    // even though the non-beamed path stays correct.
    @Test
    void testFirstBeamedNoteWithAccidentalHasColumnLeftEdgeAtOffset() {
        var calculator = new HorizontalSpacingCalculator();
        var line = detachedLine(); // C major, 0 accidentals

        var accidentalElement = ElementType.SEMIQUAVER.newInstance();
        accidentalElement.setAccidental(StaffElement.Accidental.FLAT);
        var col0 = new ElementColumn(
            accidentalElement, Collections.emptyList(),
            ACCIDENTAL_LEFT_EXTENT_SS, BEAM_RIGHT_EXTENT_SS, 0.0, 0.0, null, 0.0, true
        );
        var col1 = new ElementColumn(
            ElementType.SEMIQUAVER.newInstance(), Collections.emptyList(),
            0.0, BEAM_RIGHT_EXTENT_SS, 0.0, 0.0, null, 0.0, true
        );

        calculator.calculatePositions(List.of(col0, col1), line);

        var headerRightEdgeSs = HorizontalSpacingCalculator.calculateHeaderRightEdgeSs(line.getKeyAccidentalCount());
        var expectedLeftEdgeSs = headerRightEdgeSs + HorizontalSpacingCalculator.FIRST_NOTE_OFFSET_SS;
        assertThat(col0.getLeftEdgeXSs()).isCloseTo(expectedLeftEdgeSs, within(TOLERANCE));
    }

    // Row 32 (with lyrics): beam-group expands evenly when lyric spacing exceeds tight spacing
    @Test
    void testBeamGroupExpandsEvenlyForWideSyllables() {
        var calculator = new HorizontalSpacingCalculator();
        var line = detachedLine();

        var col0 = new ElementColumn(
            ElementType.CROTCHET.newInstance(), Collections.emptyList(),
            0.0, PLAIN_RIGHT_EXTENT_SS, 0.0, 0.0, null, 0.0, false
        );
        var col1 = new ElementColumn(
            ElementType.QUAVER.newInstance(), Collections.emptyList(),
            0.0, BEAM_RIGHT_EXTENT_SS, 0.0, 0.0, "do", WIDE_SYLLABLE_WIDTH_SS, true
        );
        var col2 = new ElementColumn(
            ElementType.QUAVER.newInstance(), Collections.emptyList(),
            0.0, BEAM_RIGHT_EXTENT_SS, 0.0, 0.0, "re", WIDE_SYLLABLE_WIDTH_SS, true
        );

        col1.setMinGapToNextSyllableSs(SYLLABLE_GAP_SS);

        calculator.calculatePositions(List.of(col0, col1, col2), line);

        // Lyric spacing = WIDE/2 + SYLLABLE_GAP + WIDE/2 = WIDE + SYLLABLE_GAP > tight beam spacing
        var lyricSpacing = WIDE_SYLLABLE_WIDTH_SS + SYLLABLE_GAP_SS;
        assertThat(col2.getXSs() - col1.getXSs()).isCloseTo(lyricSpacing, within(TOLERANCE));
        // Lyric expansion exceeds tight beam spacing
        assertThat(col2.getXSs() - col1.getXSs())
            .isGreaterThan(BEAM_RIGHT_EXTENT_SS + HorizontalSpacingCalculator.BEAM_GROUP_MIN_INTERNAL_GAP_SS);
    }

    // #445: within a beam group, a gap whose two syllables are wide must keep enough room for them
    // even when other gaps in the group need less. This mirrors the bug's third beam group —
    // [semiquaver, semiquaver, quaver] with wide lyrics on the two semiquavers — where distributing
    // the expansion evenly starved the narrow semiquaver-to-semiquaver gap and overlapped the lyrics.
    @Test
    void testBeamGroupGapHonorsItsOwnWideSyllablesNotJustEvenShare() {
        var calculator = new HorizontalSpacingCalculator();
        var line = detachedLine();

        var col0 = new ElementColumn(
            ElementType.CROTCHET.newInstance(), Collections.emptyList(),
            0.0, PLAIN_RIGHT_EXTENT_SS, 0.0, 0.0, null, 0.0, false
        );
        // Two semiquavers with wide lyrics: their shared gap takes the tight beam-internal gap
        // yet must hold both wide syllables.
        var col1 = new ElementColumn(
            ElementType.SEMIQUAVER.newInstance(), Collections.emptyList(),
            0.0, BEAM_RIGHT_EXTENT_SS, 0.0, 0.0, "c", WIDE_SYLLABLE_WIDTH_SS, true
        );
        var col2 = new ElementColumn(
            ElementType.SEMIQUAVER.newInstance(), Collections.emptyList(),
            0.0, BEAM_RIGHT_EXTENT_SS, 0.0, 0.0, "a", WIDE_SYLLABLE_WIDTH_SS, true
        );
        // Closing quaver with a narrow lyric: its gap needs less room than the semiquaver pair's.
        var col3 = new ElementColumn(
            ElementType.QUAVER.newInstance(), Collections.emptyList(),
            0.0, BEAM_RIGHT_EXTENT_SS, 0.0, 0.0, "b", NARROW_SYLLABLE_WIDTH_SS, true
        );

        col1.setMinGapToNextSyllableSs(SYLLABLE_GAP_SS);
        col2.setMinGapToNextSyllableSs(SYLLABLE_GAP_SS);

        calculator.calculatePositions(List.of(col0, col1, col2, col3), line);

        // Gap between the two wide semiquavers must clear both syllables: WIDE/2 + GAP + WIDE/2.
        var semiquaverPairLyricSpacingSs = WIDE_SYLLABLE_WIDTH_SS + SYLLABLE_GAP_SS;
        assertThat(col2.getXSs() - col1.getXSs())
            .as("wide semiquaver-to-semiquaver gap must not overlap its lyrics")
            .isGreaterThanOrEqualTo(semiquaverPairLyricSpacingSs - TOLERANCE);

        // The closing gap must clear its own (narrower) lyrics too: WIDE/2 + GAP + NARROW/2. Without
        // this, a regression that dumped all expansion into the first gap could starve this one.
        var gap2LyricSpacingSs = WIDE_SYLLABLE_WIDTH_SS / 2.0 + SYLLABLE_GAP_SS + NARROW_SYLLABLE_WIDTH_SS / 2.0;
        assertThat(col3.getXSs() - col2.getXSs())
            .as("semiquaver-to-quaver gap must not overlap its lyrics")
            .isGreaterThanOrEqualTo(gap2LyricSpacingSs - TOLERANCE);

        // Precondition (depends only on the test constants, so it is documented rather than
        // asserted): this scenario is discriminating because an even split of the total expansion
        // would place the semiquaver pair's gap at tightGap1 + totalExpansion/2 = 3.5 + 3.0 = 6.5 ss,
        // below its 8.5 ss lyric requirement — overlapping the syllables. The per-gap clamp prevents
        // that. tightGap1 = BEAM_RIGHT_EXTENT + BEAM_GROUP_MIN_INTERNAL_GAP; totalExpansion =
        // (8.5 + gap2 5.5) - (tightGap1 3.5 + tightGap2 4.5).
    }

    // #445: in a beam group that mixes a gap carrying lyrics with a gap carrying none, the
    // lyric-free gap still shares the group's even expansion. Its geometric minimum is counted in
    // the total lyric budget (the lyricSpacingSs > 0 fallback), so dropping that fallback would
    // starve the lyric-free gap back to its tight spacing instead of keeping the beam regular.
    @Test
    void testBeamGroupLyricFreeGapSharesEvenExpansion() {
        var calculator = new HorizontalSpacingCalculator();
        var line = detachedLine();

        var col0 = new ElementColumn(
            ElementType.CROTCHET.newInstance(), Collections.emptyList(),
            0.0, PLAIN_RIGHT_EXTENT_SS, 0.0, 0.0, null, 0.0, false
        );
        // Wide lyric on the first beamed note: its gap needs expansion beyond the tight beam gap.
        var col1 = new ElementColumn(
            ElementType.SEMIQUAVER.newInstance(), Collections.emptyList(),
            0.0, BEAM_RIGHT_EXTENT_SS, 0.0, 0.0, "la", WIDE_SYLLABLE_WIDTH_SS, true
        );
        // The remaining two beamed notes carry no lyric, so the gap between them is lyric-free.
        var col2 = new ElementColumn(
            ElementType.SEMIQUAVER.newInstance(), Collections.emptyList(),
            0.0, BEAM_RIGHT_EXTENT_SS, 0.0, 0.0, null, 0.0, true
        );
        var col3 = new ElementColumn(
            ElementType.SEMIQUAVER.newInstance(), Collections.emptyList(),
            0.0, BEAM_RIGHT_EXTENT_SS, 0.0, 0.0, null, 0.0, true
        );

        col1.setMinGapToNextSyllableSs(SYLLABLE_GAP_SS);

        calculator.calculatePositions(List.of(col0, col1, col2, col3), line);

        // All three notes are semiquavers, so every gap shares the same tight beam-internal spacing.
        var tightGapSs = BEAM_RIGHT_EXTENT_SS + HorizontalSpacingCalculator.BEAM_GROUP_MIN_INTERNAL_GAP_SS;
        // The lyric gap requires WIDE/2 + GAP; the lyric-free gap falls back to the geometric minimum.
        var lyricGapRequirementSs = WIDE_SYLLABLE_WIDTH_SS / 2.0 + SYLLABLE_GAP_SS;
        var lyricFreeGapRequirementSs =
            BEAM_RIGHT_EXTENT_SS + HorizontalSpacingCalculator.MIN_COLUMN_GAP_SS;
        var gapCount = 2;
        var totalExpansionSs =
            (lyricGapRequirementSs + lyricFreeGapRequirementSs) - (tightGapSs + tightGapSs);
        var expansionPerGapSs = totalExpansionSs / gapCount;
        var expectedLyricFreeGapSs = tightGapSs + expansionPerGapSs;

        // The lyric-free gap is widened by its even share of the expansion, not collapsed to the
        // tight spacing — only because its minimum was counted in the total budget.
        assertThat(col3.getXSs() - col2.getXSs())
            .as("lyric-free gap must share the beam group's even expansion")
            .isCloseTo(expectedLyricFreeGapSs, within(TOLERANCE));
        assertThat(col3.getXSs() - col2.getXSs())
            .as("lyric-free gap must be wider than the unexpanded tight gap")
            .isGreaterThan(tightGapSs + TOLERANCE);
    }

    // Row 33: single-column beam group receives normal (DEFAULT_COLUMN_GAP_SS) spacing
    @Test
    void testSingleColumnBeamGroupGetsNormalSpacing() {
        var calculator = new HorizontalSpacingCalculator();
        var line = detachedLine();

        var col0 = new ElementColumn(
            ElementType.CROTCHET.newInstance(), Collections.emptyList(),
            0.0, PLAIN_RIGHT_EXTENT_SS, 0.0, 0.0, null, 0.0, false
        );
        var col1 = new ElementColumn(
            ElementType.QUAVER.newInstance(), Collections.emptyList(),
            0.0, BEAM_RIGHT_EXTENT_SS, 0.0, 0.0, null, 0.0, true
        );

        calculator.calculatePositions(List.of(col0, col1), line);

        // handleBeamGroup with columnCount=1 falls back to calculateNextColumnXSs → default spacing
        var expectedSpacing = PLAIN_RIGHT_EXTENT_SS + HorizontalSpacingCalculator.DEFAULT_COLUMN_GAP_SS;
        assertThat(col1.getXSs() - col0.getXSs()).isCloseTo(expectedSpacing, within(TOLERANCE));
    }

    // #418 beam path: a beamed eighth note with a normal accidental must not shift its head — the
    // eighth-note beam gap (the default note-to-note gap) absorbs the accidental (comfortable
    // spacing is measured to the note head and dominates over minimum spacing for a normal-width
    // accidental).
    @Test
    void testBeamNoteWithNormalAccidentalDoesNotShiftHead() {
        var calculator = new HorizontalSpacingCalculator();
        var line = detachedLine();

        var accidentalElement = ElementType.QUAVER.newInstance();
        accidentalElement.setAccidental(StaffElement.Accidental.SHARP);

        var col0 = new ElementColumn(
            ElementType.CROTCHET.newInstance(), Collections.emptyList(),
            0.0, PLAIN_RIGHT_EXTENT_SS, 0.0, 0.0, null, 0.0, false
        );
        // First beam column: plain, no accidental
        var col1Plain = new ElementColumn(
            ElementType.QUAVER.newInstance(), Collections.emptyList(),
            0.0, BEAM_RIGHT_EXTENT_SS, 0.0, 0.0, null, 0.0, true
        );
        // Second beam column: plain, no accidental (reference)
        var col2Plain = new ElementColumn(
            ElementType.QUAVER.newInstance(), Collections.emptyList(),
            0.0, BEAM_RIGHT_EXTENT_SS, 0.0, 0.0, null, 0.0, true
        );
        // Second beam column: normal accidental (leftExtent negative, head still at origin)
        var col2Accidental = new ElementColumn(
            accidentalElement, Collections.emptyList(),
            ACCIDENTAL_LEFT_EXTENT_SS, BEAM_RIGHT_EXTENT_SS, 0.0, 0.0, null, 0.0, true
        );

        calculator.calculatePositions(List.of(col0, col1Plain, col2Plain), line);
        var plainXSs = col2Plain.getXSs();

        // Reset col0 and col1 positions (calculatePositions overwrites them)
        col0.setXSs(0.0);
        col1Plain.setXSs(0.0);
        calculator.calculatePositions(List.of(col0, col1Plain, col2Accidental), line);
        var accidentalXSs = col2Accidental.getXSs();

        // Normal accidental is absorbed by the beam gap — head must not shift (refs #418)
        assertThat(accidentalXSs).isCloseTo(plainXSs, within(TOLERANCE));
    }

    // #418 beam path: a beamed note with a wide accidental (minimum spacing dominates) shifts by
    // exactly the minimum-space overflow, leaving the accidental MIN_COLUMN_GAP_SS clear of
    // the previous beamed note's right edge.
    @Test
    void testBeamNoteWithWideAccidentalShiftsByMinimumSpaceOverflow() {
        // wideLeftExtentSs = -3.0 → minimum = BEAM_RIGHT + MIN_GAP + 3.0 = 6.0
        //                            comfortable = BEAM_RIGHT + DEFAULT_GAP = 4.5
        //                            minimum dominates; shift = 6.0 - 4.5 = 1.5
        var wideLeftExtentSs = WIDE_ACCIDENTAL_LEFT_EXTENT_SS;
        var calculator = new HorizontalSpacingCalculator();
        var line = detachedLine();

        var wideElement = ElementType.QUAVER.newInstance();
        wideElement.setAccidental(StaffElement.Accidental.SHARP);

        var col0 = new ElementColumn(
            ElementType.CROTCHET.newInstance(), Collections.emptyList(),
            0.0, PLAIN_RIGHT_EXTENT_SS, 0.0, 0.0, null, 0.0, false
        );
        var col1 = new ElementColumn(
            ElementType.QUAVER.newInstance(), Collections.emptyList(),
            0.0, BEAM_RIGHT_EXTENT_SS, 0.0, 0.0, null, 0.0, true
        );
        // Wide accidental: leftExtentSs = wideLeftExtentSs (head still at origin)
        var col2Wide = new ElementColumn(
            wideElement, Collections.emptyList(),
            wideLeftExtentSs, BEAM_RIGHT_EXTENT_SS, 0.0, 0.0, null, 0.0, true
        );
        var col2Plain = new ElementColumn(
            ElementType.QUAVER.newInstance(), Collections.emptyList(),
            0.0, BEAM_RIGHT_EXTENT_SS, 0.0, 0.0, null, 0.0, true
        );

        calculator.calculatePositions(List.of(col0, col1, col2Plain), line);
        var plainXSs = col2Plain.getXSs();

        col0.setXSs(0.0);
        col1.setXSs(0.0);
        calculator.calculatePositions(List.of(col0, col1, col2Wide), line);
        var wideXSs = col2Wide.getXSs();

        var comfortableSpacingSs = BEAM_RIGHT_EXTENT_SS + HorizontalSpacingCalculator.DEFAULT_COLUMN_GAP_SS;
        var minimumSpacingSs = BEAM_RIGHT_EXTENT_SS + HorizontalSpacingCalculator.MIN_COLUMN_GAP_SS
            + Math.abs(wideLeftExtentSs);
        var expectedShiftSs = minimumSpacingSs - comfortableSpacingSs;

        // Head shifts by exactly the minimum-space overflow (refs #418)
        assertThat(wideXSs).isCloseTo(plainXSs + expectedShiftSs, within(TOLERANCE));
        // Accidental left edge is exactly MIN_COLUMN_GAP_SS clear of the previous beam column's right edge
        var col1RightEdgeSs = col1.getXSs() + BEAM_RIGHT_EXTENT_SS;
        var accidentalLeftEdgeSs = wideXSs + wideLeftExtentSs;
        assertThat(accidentalLeftEdgeSs).isCloseTo(
            col1RightEdgeSs + HorizontalSpacingCalculator.MIN_COLUMN_GAP_SS,
            within(TOLERANCE)
        );
    }

    // #418 grace → host: a host note with a normal accidental must not shift — the grace gap
    // absorbs the accidental (comfortable is measured to the head and dominates over minimum).
    @Test
    void testGraceToHostWithNormalAccidentalDoesNotShiftHead() {
        var prevColumn = new ElementColumn(
            ElementType.GRACE_QUAVER.newInstance(), Collections.emptyList(),
            0.0, GRACE_RIGHT_EXTENT_SS, 0.0, 0.0, null, 0.0, false
        );
        var hostPlain = new ElementColumn(
            ElementType.CROTCHET.newInstance(), Collections.emptyList(),
            0.0, SMuFLConstants.NOTE_HEAD_WIDTH_SS, 0.0, 0.0, null, 0.0, false
        );
        var accidentalElement = ElementType.CROTCHET.newInstance();
        accidentalElement.setAccidental(StaffElement.Accidental.SHARP);
        // Normal accidental: leftExtentSs = ACCIDENTAL_LEFT_EXTENT_SS (head still at origin)
        var hostAccidental = new ElementColumn(
            accidentalElement, Collections.emptyList(),
            ACCIDENTAL_LEFT_EXTENT_SS, SMuFLConstants.NOTE_HEAD_WIDTH_SS, 0.0, 0.0, null, 0.0, false
        );
        prevColumn.setXSs(PREV_COLUMN_X_SS);

        var plainXSs = HorizontalSpacingCalculator.calculateNextColumnXSs(prevColumn, hostPlain);
        var accidentalXSs = HorizontalSpacingCalculator.calculateNextColumnXSs(prevColumn, hostAccidental);

        // Normal accidental fits within the grace gap — host head must not shift (refs #418)
        assertThat(accidentalXSs).isCloseTo(plainXSs, within(TOLERANCE));
    }

    // #418 grace → host: a host note with a wide accidental (minimum spacing dominates) shifts by
    // exactly the minimum-space overflow.
    @Test
    void testGraceToHostWithWideAccidentalShiftsByMinimumSpaceOverflow() {
        // wideLeftExtentSs = -3.0 → minimum = GRACE_RIGHT + MIN_GAP + 3.0 = 5.0
        //                            comfortable = GRACE_RIGHT + GRACE_GAP = 3.0
        //                            minimum dominates; shift = 5.0 - 3.0 = 2.0
        var wideLeftExtentSs = WIDE_ACCIDENTAL_LEFT_EXTENT_SS;
        var prevColumn = new ElementColumn(
            ElementType.GRACE_QUAVER.newInstance(), Collections.emptyList(),
            0.0, GRACE_RIGHT_EXTENT_SS, 0.0, 0.0, null, 0.0, false
        );
        var hostPlain = new ElementColumn(
            ElementType.CROTCHET.newInstance(), Collections.emptyList(),
            0.0, SMuFLConstants.NOTE_HEAD_WIDTH_SS, 0.0, 0.0, null, 0.0, false
        );
        var wideElement = ElementType.CROTCHET.newInstance();
        wideElement.setAccidental(StaffElement.Accidental.SHARP);
        // Wide accidental: leftExtentSs = wideLeftExtentSs (head still at origin)
        var hostWide = new ElementColumn(
            wideElement, Collections.emptyList(),
            wideLeftExtentSs, SMuFLConstants.NOTE_HEAD_WIDTH_SS, 0.0, 0.0, null, 0.0, false
        );
        prevColumn.setXSs(PREV_COLUMN_X_SS);

        var plainXSs = HorizontalSpacingCalculator.calculateNextColumnXSs(prevColumn, hostPlain);
        var wideXSs = HorizontalSpacingCalculator.calculateNextColumnXSs(prevColumn, hostWide);

        var comfortableSpacingSs = GRACE_RIGHT_EXTENT_SS + HorizontalSpacingCalculator.GRACE_NOTE_GAP_SS;
        var minimumSpacingSs = GRACE_RIGHT_EXTENT_SS + HorizontalSpacingCalculator.MIN_COLUMN_GAP_SS
            + Math.abs(wideLeftExtentSs);
        var expectedShiftSs = minimumSpacingSs - comfortableSpacingSs;

        // Head shifts by exactly the minimum-space overflow (refs #418)
        assertThat(wideXSs).isCloseTo(plainXSs + expectedShiftSs, within(TOLERANCE));
    }

    // #443 grace → host: when the grace note carries a connecting glissando and the host has an
    // accidental, the gap must widen so the glissando still reaches its minimum visible length.
    // The glissando ends at the host accidental's rendered left edge, which sits
    // (ACCIDENTAL_PADDING_SS - ACCIDENTAL_GAP_SS) further left than the layout left extent.
    @Test
    void testGraceWithGlissandoToHostWithAccidentalReservesGlissandoSpacing() {
        var grace = ElementType.GRACE_QUAVER.newInstance();
        grace.setGlissando();
        var graceColumn = new ElementColumn(
            grace, Collections.emptyList(),
            0.0, GRACE_RIGHT_EXTENT_SS, 0.0, 0.0, null, 0.0, false
        );
        var host = ElementType.CROTCHET.newInstance();
        host.setAccidental(StaffElement.Accidental.SHARP);
        var hostColumn = new ElementColumn(
            host, Collections.emptyList(),
            ACCIDENTAL_LEFT_EXTENT_SS, SMuFLConstants.NOTE_HEAD_WIDTH_SS, 0.0, 0.0, null, 0.0, false
        );
        graceColumn.setXSs(PREV_COLUMN_X_SS);

        // Same grace/host pair but with no glissando, for comparison.
        var gracePlain = ElementType.GRACE_QUAVER.newInstance();
        var gracePlainColumn = new ElementColumn(
            gracePlain, Collections.emptyList(),
            0.0, GRACE_RIGHT_EXTENT_SS, 0.0, 0.0, null, 0.0, false
        );
        gracePlainColumn.setXSs(PREV_COLUMN_X_SS);

        var hostXSs = HorizontalSpacingCalculator.calculateNextColumnXSs(graceColumn, hostColumn);
        var hostNoGlissXSs = HorizontalSpacingCalculator.calculateNextColumnXSs(gracePlainColumn, hostColumn);

        var accidentalRenderedLeftSs = ACCIDENTAL_LEFT_EXTENT_SS
            - (NoteGeometry.ACCIDENTAL_PADDING_SS - ElementColumnBuilder.ACCIDENTAL_GAP_SS);
        var reservedGapSs = (hostXSs - PREV_COLUMN_X_SS) + accidentalRenderedLeftSs - GRACE_RIGHT_EXTENT_SS;

        // The reserved gap clears the minimum glissando reservation, measured to the rendered edge.
        assertThat(reservedGapSs).isGreaterThanOrEqualTo(NoteGeometry.MIN_GLISSANDO_RESERVATION_SS - TOLERANCE);
        // The glissando is what widens the gap — without it the host sits tighter.
        assertThat(hostXSs).isGreaterThan(hostNoGlissXSs);
    }

    // #418 regular → grace: a grace note carrying its own accidental (after a regular note)
    // uses the default path (prev is not a grace note). The comfortable gap is measured to the
    // note head, so the head lands at the same xSs as a plain grace note.
    @Test
    void testRegularToGraceWithAccidentalDoesNotShiftHead() {
        var prevColumn = new ElementColumn(
            ElementType.CROTCHET.newInstance(), Collections.emptyList(),
            0.0, PLAIN_RIGHT_EXTENT_SS, 0.0, 0.0, null, 0.0, false
        );
        var gracePlain = new ElementColumn(
            ElementType.GRACE_QUAVER.newInstance(), Collections.emptyList(),
            0.0, GRACE_RIGHT_EXTENT_SS, 0.0, 0.0, null, 0.0, false
        );
        var accidentalElement = ElementType.GRACE_QUAVER.newInstance();
        accidentalElement.setAccidental(StaffElement.Accidental.SHARP);
        // Grace note with normal accidental: leftExtentSs = ACCIDENTAL_LEFT_EXTENT_SS
        // (the head itself is still at the glyph origin)
        var graceAccidental = new ElementColumn(
            accidentalElement, Collections.emptyList(),
            ACCIDENTAL_LEFT_EXTENT_SS, GRACE_RIGHT_EXTENT_SS, 0.0, 0.0, null, 0.0, false
        );
        prevColumn.setXSs(PREV_COLUMN_X_SS);

        var plainXSs = HorizontalSpacingCalculator.calculateNextColumnXSs(prevColumn, gracePlain);
        var accidentalXSs = HorizontalSpacingCalculator.calculateNextColumnXSs(prevColumn, graceAccidental);

        // Accidental on grace note is absorbed by the default gap — head must not shift (refs #418)
        assertThat(accidentalXSs).isCloseTo(plainXSs, within(TOLERANCE));
    }

    // #441: a dotted note where the dot fits within the default gap must not shift the next
    // note head. Comfortable spacing (notehead only, excluding augmentation) dominates over minimum
    // spacing (which includes augmentation), so a dotted-prev column produces the same next-head
    // position as a plain-prev column.
    @Test
    void testDotWithinDefaultGapDoesNotShiftNextHead() {
        // prevColumn without dots: both extents equal PLAIN_RIGHT_EXTENT_SS
        var plainPrevColumn = new ElementColumn(
            ElementType.CROTCHET.newInstance(), Collections.emptyList(),
            0.0, PLAIN_RIGHT_EXTENT_SS, 0.0, 0.0, null, 0.0, false
        );
        // prevColumn with 1 dot: rightExtentSs includes the dot's footprint (one dot-spacing step,
        // the same per-dot stride the renderer uses); rightExtentExcludingAugmentationSs does not
        var dotRightExtentSs = PLAIN_RIGHT_EXTENT_SS + NoteGeometry.DOT_SPACING_SS;
        var dottedPrevColumn = new ElementColumn(
            ElementType.CROTCHET.newInstance(), Collections.emptyList(),
            0.0, dotRightExtentSs, PLAIN_RIGHT_EXTENT_SS, 0.0, 0.0, null, 0.0, false
        );
        var currColumn = new ElementColumn(
            ElementType.CROTCHET.newInstance(), Collections.emptyList(),
            0.0, PLAIN_RIGHT_EXTENT_SS, 0.0, 0.0, null, 0.0, false
        );
        plainPrevColumn.setXSs(PREV_COLUMN_X_SS);
        dottedPrevColumn.setXSs(PREV_COLUMN_X_SS);

        var plainXSs = HorizontalSpacingCalculator.calculateNextColumnXSs(plainPrevColumn, currColumn);
        var dottedXSs = HorizontalSpacingCalculator.calculateNextColumnXSs(dottedPrevColumn, currColumn);

        // Dot absorbed by default gap — next head must not shift (refs #441)
        assertThat(dottedXSs)
            .as("dot within default gap must not shift next head")
            .isCloseTo(plainXSs, within(TOLERANCE));
    }

    // #441: when the dot right edge is far enough right that minimum spacing (dot + MIN_GAP)
    // exceeds comfortable spacing (notehead + DEFAULT_GAP), the next note shifts by exactly
    // the overflow, and the dot right edge is MIN_COLUMN_GAP_SS clear of the next element.
    @Test
    void testWideDotMinimumSpacingDominatesAndShiftsNextHead() {
        // Wide dot right extent chosen so minimum spacing overflows comfortable by MIN_COLUMN_GAP_SS:
        //   comfortable = PLAIN_RIGHT_EXTENT + DEFAULT_GAP
        //   minimum     = wideDot + MIN_GAP = (PLAIN_RIGHT_EXTENT + DEFAULT_GAP) + MIN_GAP
        var wideDotRightExtentSs = PLAIN_RIGHT_EXTENT_SS + HorizontalSpacingCalculator.DEFAULT_COLUMN_GAP_SS;
        var prevColumn = new ElementColumn(
            ElementType.CROTCHET.newInstance(), Collections.emptyList(),
            0.0, wideDotRightExtentSs, PLAIN_RIGHT_EXTENT_SS, 0.0, 0.0, null, 0.0, false
        );
        var plainPrevColumn = new ElementColumn(
            ElementType.CROTCHET.newInstance(), Collections.emptyList(),
            0.0, PLAIN_RIGHT_EXTENT_SS, 0.0, 0.0, null, 0.0, false
        );
        var currColumn = new ElementColumn(
            ElementType.CROTCHET.newInstance(), Collections.emptyList(),
            0.0, PLAIN_RIGHT_EXTENT_SS, 0.0, 0.0, null, 0.0, false
        );
        prevColumn.setXSs(PREV_COLUMN_X_SS);
        plainPrevColumn.setXSs(PREV_COLUMN_X_SS);

        var dottedXSs = HorizontalSpacingCalculator.calculateNextColumnXSs(prevColumn, currColumn);
        var plainXSs = HorizontalSpacingCalculator.calculateNextColumnXSs(plainPrevColumn, currColumn);

        var comfortableSpacingSs = PLAIN_RIGHT_EXTENT_SS + HorizontalSpacingCalculator.DEFAULT_COLUMN_GAP_SS;
        var minimumSpacingSs = wideDotRightExtentSs + HorizontalSpacingCalculator.MIN_COLUMN_GAP_SS;

        // Next head shifts by exactly the minimum-space overflow (refs #441)
        assertThat(dottedXSs).isCloseTo(
            plainXSs + (minimumSpacingSs - comfortableSpacingSs),
            within(TOLERANCE)
        );
        // Dot right edge is MIN_COLUMN_GAP_SS clear of the next element's left edge
        var dotRightEdgeSs = PREV_COLUMN_X_SS + wideDotRightExtentSs;
        var nextLeftEdgeSs = dottedXSs; // leftExtentSs = 0.0
        assertThat(nextLeftEdgeSs).isCloseTo(
            dotRightEdgeSs + HorizontalSpacingCalculator.MIN_COLUMN_GAP_SS,
            within(TOLERANCE)
        );
    }

    // #441 boundary: at the exact crossover where minimum spacing (dots included) equals comfortable
    // spacing (dots excluded), the next head still lands at the comfortable default-gap position and
    // the dot's right edge is exactly MIN_COLUMN_GAP_SS clear of it. Pins the comfortable-vs-minimum
    // tipping point on the dot side, mirroring the accidental boundary test.
    @Test
    void testDotAtComfortableMinimumBoundaryLeavesDotExactlyClear() {
        // dotWidth = DEFAULT_GAP - MIN_GAP makes minimum spacing exactly equal comfortable spacing.
        var dotWidthSs = HorizontalSpacingCalculator.DEFAULT_COLUMN_GAP_SS
            - HorizontalSpacingCalculator.MIN_COLUMN_GAP_SS;
        var dotRightExtentSs = PLAIN_RIGHT_EXTENT_SS + dotWidthSs;
        var prevColumn = new ElementColumn(
            ElementType.CROTCHET.newInstance(), Collections.emptyList(),
            0.0, dotRightExtentSs, PLAIN_RIGHT_EXTENT_SS, 0.0, 0.0, null, 0.0, false
        );
        var currColumn = new ElementColumn(
            ElementType.CROTCHET.newInstance(), Collections.emptyList(),
            0.0, PLAIN_RIGHT_EXTENT_SS, 0.0, 0.0, null, 0.0, false
        );
        prevColumn.setXSs(PREV_COLUMN_X_SS);

        var result = HorizontalSpacingCalculator.calculateNextColumnXSs(prevColumn, currColumn);

        // Comfortable and minimum coincide: next head lands at the comfortable default-gap position.
        var expectedXSs = PREV_COLUMN_X_SS + PLAIN_RIGHT_EXTENT_SS
            + HorizontalSpacingCalculator.DEFAULT_COLUMN_GAP_SS;
        assertThat(result).isCloseTo(expectedXSs, within(TOLERANCE));
        // The dot's right edge is exactly MIN_COLUMN_GAP_SS clear of the next column's left edge.
        var dotRightEdgeSs = PREV_COLUMN_X_SS + dotRightExtentSs;
        var nextLeftEdgeSs = result; // currColumn leftExtentSs = 0.0
        assertThat(nextLeftEdgeSs).isCloseTo(
            dotRightEdgeSs + HorizontalSpacingCalculator.MIN_COLUMN_GAP_SS,
            within(TOLERANCE)
        );
    }
}
