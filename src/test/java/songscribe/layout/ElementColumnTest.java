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

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.ElementType;
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
    private static final double CUSTOM_MIN_GAP = 0.75;

    // Concrete expected oracles, deliberately not recomputed from the formula under test.
    private static final double EXPECTED_WIDTH = 3.5;      // abs(-1.5) + 2.0
    private static final double EXPECTED_LEFT_EDGE = 8.5;  // 10.0 + (-1.5)
    private static final double EXPECTED_RIGHT_EDGE = 12.0; // 10.0 + 2.0

    private static StaffElement element(ElementType type) {
        return type.newInstance();
    }

    private static ElementColumn columnFor(StaffElement element) {
        return new ElementColumn(element, List.of(), 0, 0, 0, 0, null, 0, false);
    }

    @Test
    void testConstructorStoresAllFields() {
        var note = element(ElementType.CROTCHET);
        var graceNote = element(ElementType.GRACE_QUAVER);

        var column = new ElementColumn(
            note, List.of(graceNote), LEFT_EXTENT, RIGHT_EXTENT,
            STEM_TOP, STEM_BOTTOM, SYLLABLE, SYLLABLE_WIDTH, true);

        assertThat(column.getElement()).isSameAs(note);
        assertThat(column.getGraceNotes()).containsExactly(graceNote);
        assertThat(column.getLeftExtentSs()).isEqualTo(LEFT_EXTENT);
        assertThat(column.getRightExtentSs()).isEqualTo(RIGHT_EXTENT);
        assertThat(column.getStemTopSs()).isEqualTo(STEM_TOP);
        assertThat(column.getStemBottomSs()).isEqualTo(STEM_BOTTOM);
        assertThat(column.getSyllable()).isEqualTo(SYLLABLE);
        assertThat(column.getSyllableWidthSs()).isEqualTo(SYLLABLE_WIDTH);
        assertThat(column.isBeamed()).isTrue();
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
            element(ElementType.CROTCHET), List.of(), 0, 0, 0, 0, "", 0, false);

        assertThat(column.hasSyllable()).isFalse();
    }

    @Test
    void testHasSyllableTrueWhenNonEmpty() {
        var column = new ElementColumn(
            element(ElementType.CROTCHET), List.of(), 0, 0, 0, 0, SYLLABLE, SYLLABLE_WIDTH, false);

        assertThat(column.hasSyllable()).isTrue();
    }

    @Test
    void testMinGapToNextSyllableSetterRoundTrips() {
        var column = columnFor(element(ElementType.CROTCHET));

        column.setMinGapToNextSyllableSs(CUSTOM_MIN_GAP);

        assertThat(column.getMinGapToNextSyllableSs()).isEqualTo(CUSTOM_MIN_GAP);
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
        glissandoNote.setGlissando(StaffElement.Glissando.Type.CONNECTED);

        assertThat(columnFor(glissandoNote).hasGlissando()).isTrue();
        assertThat(columnFor(element(ElementType.CROTCHET)).hasGlissando()).isFalse();
    }
}
