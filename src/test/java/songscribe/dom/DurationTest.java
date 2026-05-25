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

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;

/**
 * Tests for {@link Duration} — getNote() identity, dotted/non-dotted variants,
 * and ElementType mapping.
 */
class DurationTest extends UnitTest {

    // The expected dotCount for dotted constants
    private static final int DOTTED_DOT_COUNT = 1;

    // The expected staffPosition for dotted constants
    private static final int DOTTED_STAFF_POSITION = 1;

    // The expected dotCount for non-dotted constants
    private static final int NON_DOTTED_DOT_COUNT = 0;

    // -----------------------------------------------------------------------
    // Row 1: getNote() returns a clone — two calls yield distinct instances
    // -----------------------------------------------------------------------

    @Test
    void testGetNoteReturnsCloneNotSharedInstance() {
        var first = Duration.CROTCHET.getNote();
        var second = Duration.CROTCHET.getNote();

        assertThat(first).isNotSameAs(second);
    }

    // -----------------------------------------------------------------------
    // Row 2: Dotted variants have dotCount == 1 and staffPosition == 1
    // -----------------------------------------------------------------------

    @Test
    void testMimDottedHasDotCountOneAndStaffPositionOne() {
        var note = Duration.MINIM_DOTTED.getNote();

        assertThat(note.getDotCount()).isEqualTo(DOTTED_DOT_COUNT);
        assertThat(note.getStaffPosition()).isEqualTo(DOTTED_STAFF_POSITION);
    }

    @Test
    void testCrotchetDottedHasDotCountOneAndStaffPositionOne() {
        var note = Duration.CROTCHET_DOTTED.getNote();

        assertThat(note.getDotCount()).isEqualTo(DOTTED_DOT_COUNT);
        assertThat(note.getStaffPosition()).isEqualTo(DOTTED_STAFF_POSITION);
    }

    @Test
    void testQuaverDottedHasDotCountOneAndStaffPositionOne() {
        var note = Duration.QUAVER_DOTTED.getNote();

        assertThat(note.getDotCount()).isEqualTo(DOTTED_DOT_COUNT);
        assertThat(note.getStaffPosition()).isEqualTo(DOTTED_STAFF_POSITION);
    }

    // -----------------------------------------------------------------------
    // Row 3: Non-dotted variants have dotCount == 0
    // -----------------------------------------------------------------------

    @Test
    void testSemiBraveHasZeroDotCount() {
        assertThat(Duration.SEMI_BREVE.getNote().getDotCount()).isEqualTo(NON_DOTTED_DOT_COUNT);
    }

    @Test
    void testMimHasZeroDotCount() {
        assertThat(Duration.MINIM.getNote().getDotCount()).isEqualTo(NON_DOTTED_DOT_COUNT);
    }

    @Test
    void testCrotchetHasZeroDotCount() {
        assertThat(Duration.CROTCHET.getNote().getDotCount()).isEqualTo(NON_DOTTED_DOT_COUNT);
    }

    @Test
    void testQuaverHasZeroDotCount() {
        assertThat(Duration.QUAVER.getNote().getDotCount()).isEqualTo(NON_DOTTED_DOT_COUNT);
    }

    // -----------------------------------------------------------------------
    // Row 4: Each constant's note has the expected ElementType
    // -----------------------------------------------------------------------

    @Test
    void testSemiBraveNoteTypeIsSemibreve() {
        assertThat(Duration.SEMI_BREVE.getNote().getType()).isEqualTo(ElementType.SEMIBREVE);
    }

    @Test
    void testMimDottedNoteTypeIsMinim() {
        assertThat(Duration.MINIM_DOTTED.getNote().getType()).isEqualTo(ElementType.MINIM);
    }

    @Test
    void testMimNoteTypeIsMinim() {
        assertThat(Duration.MINIM.getNote().getType()).isEqualTo(ElementType.MINIM);
    }

    @Test
    void testCrotchetDottedNoteTypeIsCrotchet() {
        assertThat(Duration.CROTCHET_DOTTED.getNote().getType()).isEqualTo(ElementType.CROTCHET);
    }

    @Test
    void testCrotchetNoteTypeIsCrotchet() {
        assertThat(Duration.CROTCHET.getNote().getType()).isEqualTo(ElementType.CROTCHET);
    }

    @Test
    void testQuaverDottedNoteTypeIsQuaver() {
        assertThat(Duration.QUAVER_DOTTED.getNote().getType()).isEqualTo(ElementType.QUAVER);
    }

    @Test
    void testQuaverNoteTypeIsQuaver() {
        assertThat(Duration.QUAVER.getNote().getType()).isEqualTo(ElementType.QUAVER);
    }
}
