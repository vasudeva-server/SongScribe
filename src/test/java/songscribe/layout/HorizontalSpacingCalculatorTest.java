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

    // Row 29: grace note to host note uses GRACE_HOST_REST_SS (tighter than DEFAULT_COLUMN_GAP_SS)
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

        var expected = PREV_COLUMN_X_SS + GRACE_RIGHT_EXTENT_SS + HorizontalSpacingCalculator.GRACE_HOST_REST_SS;
        assertThat(result).isEqualTo(expected);
        // Grace note gap is tighter than the default gap
        assertThat(HorizontalSpacingCalculator.GRACE_HOST_REST_SS)
            .isLessThan(HorizontalSpacingCalculator.DEFAULT_COLUMN_GAP_SS);
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

        var comfortableSpacingSs = GRACE_RIGHT_EXTENT_SS + HorizontalSpacingCalculator.GRACE_HOST_REST_SS;
        var minimumSpacingSs = GRACE_RIGHT_EXTENT_SS + HorizontalSpacingCalculator.MIN_COLUMN_GAP_SS
            + Math.abs(wideLeftExtentSs);
        var expectedShiftSs = minimumSpacingSs - comfortableSpacingSs;

        // Head shifts by exactly the minimum-space overflow (refs #418)
        assertThat(wideXSs).isCloseTo(plainXSs + expectedShiftSs, within(TOLERANCE));
    }

    // #443 grace → host: when the grace note carries a connecting glissando and the host has an
    // accidental, the gap must widen so the glissando still reaches its minimum visible length.
    // The glissando ends at the host accidental's rendered left edge, which now equals the
    // layout left extent — the renderer and layout reserve share the single
    // NoteGeometry.ACCIDENTAL_PADDING_SS constant (refs the accidental-gap unification).
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

        var reservedGapSs = (hostXSs - PREV_COLUMN_X_SS) + ACCIDENTAL_LEFT_EXTENT_SS - GRACE_RIGHT_EXTENT_SS;

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
