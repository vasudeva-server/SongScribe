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
import songscribe.layout.ElementColumn;
import songscribe.layout.HorizontalSpacingCalculator;
import songscribe.smufl.Engraving;

class HorizontalSpacingCalculatorTest extends UnitTest {

    private static final double TOLERANCE = 0.001;
    private static final int THREE_KEY_ACCIDENTALS = 3;
    private static final int SEVEN_KEY_ACCIDENTALS = 7;
    private static final double PREV_COLUMN_X_SS = 10.0;
    private static final double PLAIN_RIGHT_EXTENT_SS = 2.0;
    private static final double NEGATIVE_LEFT_EXTENT_SS = -0.5;
    private static final double WIDE_SYLLABLE_WIDTH_SS = 8.0;
    // A narrow syllable, so a gap carrying it needs less room than one between two wide syllables.
    private static final double NARROW_SYLLABLE_WIDTH_SS = 2.0;
    // The gap a column reserves to the next syllable (lyric space width), as ElementColumnBuilder sets it.
    private static final double SYLLABLE_GAP_SS = 0.5;
    private static final double ACCIDENTAL_LEFT_EXTENT_SS = -0.625;
    private static final double GRACE_RIGHT_EXTENT_SS = 1.0;
    private static final double BEAM_RIGHT_EXTENT_SS = 2.0;
    // A deliberately wide accidental left extent, chosen so the geometric minimum spacing
    // exceeds the comfortable gap and therefore governs (shifting the note head).
    private static final double WIDE_ACCIDENTAL_LEFT_EXTENT_SS = -3.0;

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
            Engraving.NOTE_HEAD_WIDTH_SS,
            0.0, 0.0, null, 0.0, false
        );
        var columns = List.of(column);

        calculator.calculatePositions(columns, line);

        // Concrete expected value: clef width + 0*keyAccidentalWidth + firstNoteOffset
        var expectedXSs = Engraving.G_CLEF_WIDTH_SS
            + line.getKeyAccidentalCount() * HorizontalSpacingCalculator.KEY_ACCIDENTAL_WIDTH_SS
            + HorizontalSpacingCalculator.FIRST_NOTE_OFFSET_SS;
        assertThat(column.getXSs()).isCloseTo(expectedXSs, within(TOLERANCE));
    }

    // Row 24: calculateHeaderRightEdgeSs(n) = G_CLEF_WIDTH_SS + n * KEY_ACCIDENTAL_WIDTH_SS
    @Test
    void testCalculateHeaderRightEdgeSsWithZeroAccidentals() {
        assertThat(HorizontalSpacingCalculator.calculateHeaderRightEdgeSs(0))
            .isEqualTo(Engraving.G_CLEF_WIDTH_SS);
    }

    @Test
    void testCalculateHeaderRightEdgeSsWithThreeAccidentals() {
        var expected = Engraving.G_CLEF_WIDTH_SS + THREE_KEY_ACCIDENTALS * HorizontalSpacingCalculator.KEY_ACCIDENTAL_WIDTH_SS;
        assertThat(HorizontalSpacingCalculator.calculateHeaderRightEdgeSs(THREE_KEY_ACCIDENTALS)).isEqualTo(expected);
    }

    @Test
    void testCalculateHeaderRightEdgeSsWithSevenAccidentals() {
        var expected = Engraving.G_CLEF_WIDTH_SS + SEVEN_KEY_ACCIDENTALS * HorizontalSpacingCalculator.KEY_ACCIDENTAL_WIDTH_SS;
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
            0.0, Engraving.NOTE_HEAD_WIDTH_SS,
            0.0, 0.0, null, 0.0, false
        );
        var currColumn = new ElementColumn(
            currElement,
            Collections.emptyList(),
            0.0, Engraving.NOTE_HEAD_WIDTH_SS,
            0.0, 0.0, null, 0.0, false
        );
        prevColumn.setXSs(PREV_COLUMN_X_SS);

        var expected = PREV_COLUMN_X_SS + Engraving.NOTE_HEAD_WIDTH_SS + HorizontalSpacingCalculator.DEFAULT_COLUMN_GAP_SS;
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
            0.0, Engraving.NOTE_HEAD_WIDTH_SS, 0.0, 0.0, "do", WIDE_SYLLABLE_WIDTH_SS, false
        );
        var currColumn = new ElementColumn(
            ElementType.CROTCHET.newInstance(), Collections.emptyList(),
            0.0, Engraving.NOTE_HEAD_WIDTH_SS, 0.0, 0.0, "re", WIDE_SYLLABLE_WIDTH_SS, false
        );
        prevColumn.setXSs(PREV_COLUMN_X_SS);
        prevColumn.setMinGapToNextSyllableSs(SYLLABLE_GAP_SS);

        var result = HorizontalSpacingCalculator.calculateNextColumnXSs(prevColumn, currColumn);

        // lyricSpacing = prevHalfWidth + prev gap-to-next + currHalfWidth
        //              = WIDE/2 + SYLLABLE_GAP + WIDE/2 = WIDE + SYLLABLE_GAP
        var expectedLyricSpacing = WIDE_SYLLABLE_WIDTH_SS + SYLLABLE_GAP_SS;
        var expectedXSs = PREV_COLUMN_X_SS + expectedLyricSpacing;
        var wouldBeWithDefault = PREV_COLUMN_X_SS + Engraving.NOTE_HEAD_WIDTH_SS
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
            0.0, Engraving.NOTE_HEAD_WIDTH_SS, 0.0, 0.0, null, 0.0, false
        );
        var currColumn = new ElementColumn(
            ElementType.CROTCHET.newInstance(), Collections.emptyList(),
            0.0, Engraving.NOTE_HEAD_WIDTH_SS, 0.0, 0.0, "re", WIDE_SYLLABLE_WIDTH_SS, false
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
            ACCIDENTAL_LEFT_EXTENT_SS, Engraving.NOTE_HEAD_WIDTH_SS, 0.0, 0.0, null, 0.0, false
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
            0.0, Engraving.NOTE_HEAD_WIDTH_SS, 0.0, 0.0, null, 0.0, false
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
        glissandoElement.setGlissando(StaffElement.Glissando.Type.CONNECTED);

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

    // Row 31: calculatePositions with empty list returns without exception
    @Test
    void testCalculatePositionsEmptyListDoesNotThrow() {
        var calculator = new HorizontalSpacingCalculator();
        var line = detachedLine();
        assertThatCode(() -> calculator.calculatePositions(Collections.emptyList(), line))
            .doesNotThrowAnyException();
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
            0.0, Engraving.NOTE_HEAD_WIDTH_SS, 0.0, 0.0, null, 0.0, false
        );
        var accidentalElement = ElementType.CROTCHET.newInstance();
        accidentalElement.setAccidental(StaffElement.Accidental.SHARP);
        // Normal accidental: leftExtentSs = ACCIDENTAL_LEFT_EXTENT_SS (head still at origin)
        var hostAccidental = new ElementColumn(
            accidentalElement, Collections.emptyList(),
            ACCIDENTAL_LEFT_EXTENT_SS, Engraving.NOTE_HEAD_WIDTH_SS, 0.0, 0.0, null, 0.0, false
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
            0.0, Engraving.NOTE_HEAD_WIDTH_SS, 0.0, 0.0, null, 0.0, false
        );
        var wideElement = ElementType.CROTCHET.newInstance();
        wideElement.setAccidental(StaffElement.Accidental.SHARP);
        // Wide accidental: leftExtentSs = wideLeftExtentSs (head still at origin)
        var hostWide = new ElementColumn(
            wideElement, Collections.emptyList(),
            wideLeftExtentSs, Engraving.NOTE_HEAD_WIDTH_SS, 0.0, 0.0, null, 0.0, false
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
}
