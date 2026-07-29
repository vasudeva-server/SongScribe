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

import java.awt.Font;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.ElementType;
import songscribe.dom.Lyric;
import songscribe.dom.StaffElement;

class ElementColumnTest extends UnitTest {

    // Negative left extent models an accidental that pushes the column left of the head origin.
    private static final double LEFT_EXTENT = -1.5;
    private static final double RIGHT_EXTENT = 2.0;
    private static final double STEM_TOP = -3.0;
    private static final double STEM_BOTTOM = 4.0;
    private static final String SYLLABLE = "la";
    private static final double SYLLABLE_WIDTH = 1.25;
    private static final double X_POSITION = 10.0;

    // Concrete expected oracles, deliberately not recomputed from the formula under test.
    private static final double EXPECTED_WIDTH = 3.5;      // abs(-1.5) + 2.0
    private static final double EXPECTED_LEFT_EDGE = 8.5;  // 10.0 + (-1.5)
    private static final double EXPECTED_RIGHT_EDGE = 12.0; // 10.0 + 2.0

    private static final double POSITION_TOLERANCE_SS = 1e-9;

    // Staff positions (Sp, positive = below the middle line) far enough apart that a flag at one
    // hangs entirely clear of a notehead at the other.
    private static final int HIGH_STAFF_POSITION_SP = -4;   // top staff line
    private static final int MIDDLE_STAFF_POSITION_SP = -1; // space above the middle line
    private static final int LOW_STAFF_POSITION_SP = 2;     // fourth staff line

    private static final Font LYRICS_FONT = new Font(Font.MONOSPACED, Font.PLAIN, 12);
    private static final LyricRenderMetrics LYRIC_METRICS =
        new LyricRenderMetrics(LYRICS_FONT, LYRICS_FONT, 0.0, 0.0, 0.0);

    // A column carries the lyric it was built from, and reads its syllable text off that, so a
    // test that wants a syllable supplies the lyric it belongs to.
    private static final Lyric SYLLABLE_LYRIC = lyricWithText(SYLLABLE);
    private static final Lyric EMPTY_TEXT_LYRIC = lyricWithText("");

    private static Lyric lyricWithText(String text) {
        return new Lyric(Lyric.FIRST_VERSE, text, Lyric.Extend.NONE, Lyric.Syllabic.SINGLE, false);
    }

    private static StaffElement element(ElementType type) {
        return type.newInstance();
    }

    private static ElementColumn columnFor(StaffElement element) {
        return new ElementColumn(element, List.of(), 0, 0, 0, 0, null, 0, false);
    }

    /**
     * A column with real geometry — flag bounds, stem length, notehead width — as the layout builds
     * it, rather than the synthetic extents the other tests here pass in by hand. Detached, so it
     * carries no beam membership and keeps its flag.
     */
    private static ElementColumn builtColumn(StaffElement element) {
        return new ElementColumnBuilder(LYRIC_METRICS).buildDetachedColumn(element, Lyric.FIRST_VERSE);
    }

    private static ElementColumn builtColumn(
        ElementType type, int staffPositionSp, StaffElement.Direction direction) {

        var element = element(type);
        element.setStaffPosition(staffPositionSp);
        element.setDirection(direction);
        return builtColumn(element);
    }

    @Test
    void testConstructorStoresAllFields() {
        var note = element(ElementType.CROTCHET);
        var graceNote = element(ElementType.GRACE_QUAVER);

        var column = new ElementColumn(
            note, List.of(graceNote), LEFT_EXTENT, RIGHT_EXTENT,
            STEM_TOP, STEM_BOTTOM, SYLLABLE_LYRIC, SYLLABLE_WIDTH, true);

        assertThat(column.getElement()).isSameAs(note);
        assertThat(column.getGraceNotes()).containsExactly(graceNote);
        assertThat(column.getLeftExtentSs()).isEqualTo(LEFT_EXTENT);
        assertThat(column.getRightExtentSs()).isEqualTo(RIGHT_EXTENT);
        assertThat(column.getStemTopSs()).isEqualTo(STEM_TOP);
        assertThat(column.getStemBottomSs()).isEqualTo(STEM_BOTTOM);
        assertThat(column.getLyric()).isSameAs(SYLLABLE_LYRIC);
        assertThat(column.getSyllable())
            .as("the syllable text is read off the stored lyric, not held separately")
            .isEqualTo(SYLLABLE);
        assertThat(column.getSyllableWidthSs()).isEqualTo(SYLLABLE_WIDTH);
        assertThat(column.isBeamed()).isTrue();
    }

    @Test
    void testAColumnBuiltWithoutALyricReportsNone() {
        var column = new ElementColumn(
            element(ElementType.CROTCHET), List.of(), 0, 0, 0, 0, null, 0, false);

        assertThat(column.getLyric())
            .as("an element carrying no lyric in the verse being laid out yields a null lyric")
            .isNull();
        assertThat(column.getSyllable()).isNull();
    }

    @Test
    void testConstructorDefensivelyCopiesGraceNotes() {
        var graceNote = element(ElementType.GRACE_QUAVER);
        var source = new ArrayList<StaffElement>();
        source.add(graceNote);

        var column = new ElementColumn(
            element(ElementType.CROTCHET), source, 0, 0, 0, 0, null, 0, false);

        source.add(element(ElementType.GRACE_QUAVER));

        assertThat(column.getGraceNotes()).containsExactly(graceNote);
    }

    @Test
    void testGetWidthSsIsAbsoluteLeftPlusRightExtent() {
        var column = new ElementColumn(
            element(ElementType.CROTCHET), List.of(), LEFT_EXTENT, RIGHT_EXTENT,
            0, 0, null, 0, false);

        assertThat(column.getWidthSs()).isEqualTo(EXPECTED_WIDTH);
    }

    @Test
    void testEdgeXPositionsOffsetXSsByExtents() {
        var column = new ElementColumn(
            element(ElementType.CROTCHET), List.of(), LEFT_EXTENT, RIGHT_EXTENT,
            0, 0, null, 0, false);
        column.setXSs(X_POSITION);

        assertThat(column.getLeftEdgeXSs()).isEqualTo(EXPECTED_LEFT_EDGE);
        assertThat(column.getRightEdgeXSs()).isEqualTo(EXPECTED_RIGHT_EDGE);
    }

    @Test
    void testHasSyllableFalseWhenNull() {
        var column = new ElementColumn(
            element(ElementType.CROTCHET), List.of(), 0, 0, 0, 0, null, 0, false);

        assertThat(column.hasSyllable()).isFalse();
    }

    @Test
    void testHasSyllableFalseWhenEmptyString() {
        var column = new ElementColumn(
            element(ElementType.CROTCHET), List.of(), 0, 0, 0, 0, EMPTY_TEXT_LYRIC, 0, false);

        assertThat(column.hasSyllable()).isFalse();
    }

    @Test
    void testHasSyllableTrueWhenNonEmpty() {
        var column = new ElementColumn(
            element(ElementType.CROTCHET), List.of(), 0, 0, 0, 0, SYLLABLE_LYRIC, SYLLABLE_WIDTH, false);

        assertThat(column.hasSyllable()).isTrue();
    }

    @Test
    void testIsRestDelegatesToElementType() {
        assertThat(columnFor(element(ElementType.SEMIBREVE_REST)).isRest()).isTrue();
        assertThat(columnFor(element(ElementType.CROTCHET)).isRest()).isFalse();
    }

    @Test
    void testIsBarlineDelegatesToElementType() {
        assertThat(columnFor(element(ElementType.SINGLE_BARLINE)).isBarline()).isTrue();
        assertThat(columnFor(element(ElementType.CROTCHET)).isBarline()).isFalse();
    }

    @Test
    void testIsBeamedReflectsConstructorFlag() {
        var beamed = new ElementColumn(
            element(ElementType.QUAVER), List.of(), 0, 0, 0, 0, null, 0, true);
        var unbeamed = new ElementColumn(
            element(ElementType.QUAVER), List.of(), 0, 0, 0, 0, null, 0, false);

        assertThat(beamed.isBeamed()).isTrue();
        assertThat(unbeamed.isBeamed()).isFalse();
    }

    @Test
    void testHasGraceNotesReflectsGraceNoteList() {
        var withGrace = new ElementColumn(
            element(ElementType.CROTCHET), List.of(element(ElementType.GRACE_QUAVER)),
            0, 0, 0, 0, null, 0, false);

        assertThat(withGrace.hasGraceNotes()).isTrue();
        assertThat(columnFor(element(ElementType.CROTCHET)).hasGraceNotes()).isFalse();
    }

    @Test
    void testHasGlissandoDelegatesToElement() {
        var glissandoNote = element(ElementType.CROTCHET);
        glissandoNote.setGlissando();

        assertThat(columnFor(glissandoNote).hasGlissando()).isTrue();
        assertThat(columnFor(element(ElementType.CROTCHET)).hasGlissando()).isFalse();
    }

    // ==========================================================================
    // Left-facing band and the flag discount (refs #560)
    // ==========================================================================

    @Test
    void testLeftFacingBandIsTheNoteheadAloneForAnUpStem() {
        var upStem = builtColumn(ElementType.CROTCHET, LOW_STAFF_POSITION_SP, StaffElement.Direction.UP);
        var positionSs = upStem.getPositionSs();

        // The up-stem reaches well above the notehead, but it stands on the notehead's right edge,
        // so nothing approaching from the left can meet it.
        assertThat(upStem.getAbsoluteTopYSs()).isLessThan(positionSs - ElementColumnBuilder.HALF_NOTE_HEAD_SS);
        assertThat(upStem.getLeftFacingTopYSs())
            .isCloseTo(positionSs - ElementColumnBuilder.HALF_NOTE_HEAD_SS, within(POSITION_TOLERANCE_SS));
        assertThat(upStem.getLeftFacingBottomYSs())
            .isCloseTo(positionSs + ElementColumnBuilder.HALF_NOTE_HEAD_SS, within(POSITION_TOLERANCE_SS));
    }

    @Test
    void testLeftFacingBandReachesTheStemTipForADownStem() {
        var downStem = builtColumn(ElementType.CROTCHET, HIGH_STAFF_POSITION_SP, StaffElement.Direction.DOWN);

        // A down-stem sits on the notehead's LEFT edge, so it does face left and the band must reach
        // past the notehead all the way to the stem tip.
        assertThat(downStem.getLeftFacingBottomYSs())
            .isGreaterThan(downStem.getPositionSs() + ElementColumnBuilder.HALF_NOTE_HEAD_SS);
    }

    @Test
    void testRightExtentFacingDropsAGraceFlagHangingClearOfTheNeighbour() {
        var grace = builtColumn(ElementType.GRACE_QUAVER, HIGH_STAFF_POSITION_SP, StaffElement.Direction.UP);
        var host = builtColumn(ElementType.CROTCHET, LOW_STAFF_POSITION_SP, StaffElement.Direction.UP);

        // Precondition: the flag really is what inflates this column's right extent.
        assertThat(grace.getFlagExtentSs()).isPositive();

        assertThat(grace.getRightExtentFacingSs(host.getLeftFacingTopYSs(), host.getLeftFacingBottomYSs()))
            .as("a flag four steps above the neighbour's notehead is ink nowhere near the gap")
            .isCloseTo(grace.getNoteheadWidthSs(), within(POSITION_TOLERANCE_SS));
    }

    @Test
    void testRightExtentFacingChargesAGraceFlagThatMeetsTheNeighbour() {
        var grace = builtColumn(ElementType.GRACE_QUAVER, LOW_STAFF_POSITION_SP, StaffElement.Direction.UP);
        var host = builtColumn(ElementType.CROTCHET, MIDDLE_STAFF_POSITION_SP, StaffElement.Direction.DOWN);

        assertThat(grace.getRightExtentFacingSs(host.getLeftFacingTopYSs(), host.getLeftFacingBottomYSs()))
            .as("a flag level with the neighbour's notehead is charged in full")
            .isCloseTo(grace.getRightExtentSs(), within(POSITION_TOLERANCE_SS));
    }

    @Test
    void testRightExtentFacingChargesTheFullExtentWhenAugmentationReachesPastTheFlag() {
        var dottedQuaver = element(ElementType.QUAVER);
        dottedQuaver.setDotCount(1);
        dottedQuaver.setStaffPosition(HIGH_STAFF_POSITION_SP);
        dottedQuaver.setDirection(StaffElement.Direction.UP);
        var dotted = builtColumn(dottedQuaver);
        var neighbour = builtColumn(ElementType.CROTCHET, LOW_STAFF_POSITION_SP, StaffElement.Direction.UP);

        // Preconditions: the flag clears the neighbour's band (so only the dot can hold the extent
        // out), and the dot really does reach past the flag.
        assertThat(builtColumn(ElementType.QUAVER, HIGH_STAFF_POSITION_SP, StaffElement.Direction.UP)
            .getRightExtentFacingSs(neighbour.getLeftFacingTopYSs(), neighbour.getLeftFacingBottomYSs()))
            .isLessThan(dotted.getRightExtentSs());
        assertThat(dotted.getRightExtentSs()).isGreaterThan(dotted.getRightExtentExcludingAugmentationSs());

        assertThat(dotted.getRightExtentFacingSs(
            neighbour.getLeftFacingTopYSs(), neighbour.getLeftFacingBottomYSs()))
            .as("a dot stacks to the right of the flag, so there is nothing to discount")
            .isCloseTo(dotted.getRightExtentSs(), within(POSITION_TOLERANCE_SS));
    }

    @Test
    void testRightExtentFacingChargesTheFullExtentWithoutAFlag() {
        var crotchet = builtColumn(ElementType.CROTCHET, HIGH_STAFF_POSITION_SP, StaffElement.Direction.UP);
        var neighbour = builtColumn(ElementType.CROTCHET, LOW_STAFF_POSITION_SP, StaffElement.Direction.UP);

        assertThat(crotchet.getFlagExtentSs()).isZero();
        assertThat(crotchet.getRightExtentFacingSs(
            neighbour.getLeftFacingTopYSs(), neighbour.getLeftFacingBottomYSs()))
            .isCloseTo(crotchet.getRightExtentSs(), within(POSITION_TOLERANCE_SS));
    }
}
