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
    // The gap a column reserves to the next syllable (lyric space width), as ElementColumnBuilder sets it.
    private static final double SYLLABLE_GAP_SS = 0.5;
    private static final double ACCIDENTAL_LEFT_EXTENT_SS = -0.625;
    private static final double GRACE_RIGHT_EXTENT_SS = 1.0;
    private static final double BEAM_RIGHT_EXTENT_SS = 2.0;

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

        var absLeft = Math.abs(NEGATIVE_LEFT_EXTENT_SS);
        var expectedWithDefault = PREV_COLUMN_X_SS + PLAIN_RIGHT_EXTENT_SS
            + HorizontalSpacingCalculator.DEFAULT_COLUMN_GAP_SS + absLeft;
        var wouldBeWithMin = PREV_COLUMN_X_SS + PLAIN_RIGHT_EXTENT_SS
            + HorizontalSpacingCalculator.MIN_COLUMN_GAP_SS + absLeft;
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

    // Row 28: accidental clearance is satisfied by default gap (no extra push needed)
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

        // Default gap already provides clearance — no extra push applied
        var expectedXSs = PREV_COLUMN_X_SS + PLAIN_RIGHT_EXTENT_SS
            + HorizontalSpacingCalculator.DEFAULT_COLUMN_GAP_SS + Math.abs(ACCIDENTAL_LEFT_EXTENT_SS);
        assertThat(result).isEqualTo(expectedXSs);
        // Invariant: accidental left edge >= prevRightEdge + ACCIDENTAL_CLEARANCE
        var prevRightEdge = PREV_COLUMN_X_SS + PLAIN_RIGHT_EXTENT_SS;
        var accidentalLeft = result + ACCIDENTAL_LEFT_EXTENT_SS;
        assertThat(accidentalLeft).isGreaterThanOrEqualTo(
            prevRightEdge + HorizontalSpacingCalculator.ACCIDENTAL_CLEARANCE_SS
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

    // Row 30: glissando spacing is enforced within beam group (tight gap < MIN_GLISSANDO_RESERVATION_SS)
    // In a beam group the tight internal gap (1.5ss) < MIN_GLISSANDO_RESERVATION_SS (1.6ss),
    // so a glissando on the prev column forces extra spacing.
    @Test
    void testGlissandoSpacingEnforcedInBeamGroup() {
        var calculator = new HorizontalSpacingCalculator();
        var line = detachedLine();

        var glissandoElement = ElementType.CROTCHET.newInstance();
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
            ElementType.CROTCHET.newInstance(), Collections.emptyList(),
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

    // Row 32 (without lyrics): beam-group gets tight internal spacing < DEFAULT_COLUMN_GAP_SS
    @Test
    void testBeamGroupTightSpacingWithoutLyrics() {
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

        var tightSpacing = BEAM_RIGHT_EXTENT_SS + HorizontalSpacingCalculator.BEAM_GROUP_MIN_INTERNAL_GAP_SS;
        assertThat(col2.getXSs() - col1.getXSs()).isCloseTo(tightSpacing, within(TOLERANCE));
        // Tight beam gap is smaller than the default note-to-note gap
        assertThat(col2.getXSs() - col1.getXSs())
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
}
