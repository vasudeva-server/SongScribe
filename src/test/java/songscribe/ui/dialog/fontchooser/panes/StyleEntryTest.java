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

package songscribe.ui.dialog.fontchooser.panes;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Font;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;

class StyleEntryTest extends UnitTest {

    // Two Font objects with the same family/style but different sizes share the
    // same PS name (e.g. "ArialMT"), because PSName encodes only family+style.
    private static final Font ARIAL_12 = new Font("Arial", Font.PLAIN, 12);
    private static final Font ARIAL_14 = new Font("Arial", Font.PLAIN, 14);
    private static final Font ARIAL_BOLD_12 = new Font("Arial", Font.BOLD, 12);

    @Test
    void testEqualsReturnsTrueForEntriesWithSamePSName() {
        // ARIAL_12 and ARIAL_14 share the same PS name (size is not part of PSName).
        assertThat(ARIAL_12.getPSName()).isEqualTo(ARIAL_14.getPSName());

        var entry1 = new StyleEntry(ARIAL_12);
        var entry2 = new StyleEntry(ARIAL_14);
        assertThat(entry1).isEqualTo(entry2);
    }

    @Test
    void testEqualsReturnsFalseForEntriesWithDifferentPSName() {
        var plainEntry = new StyleEntry(ARIAL_12);
        var boldEntry = new StyleEntry(ARIAL_BOLD_12);
        // Arial Plain and Arial Bold have different PS names.
        assertThat(plainEntry).isNotEqualTo(boldEntry);
    }

    @Test
    void testHashCodeIsConsistentWithEquals() {
        // ARIAL_12 and ARIAL_14 are equal by PSName (see testEqualsReturnsTrueForEntriesWithSamePSName).
        // The Java contract requires: if a.equals(b) then a.hashCode() == b.hashCode().
        var entry1 = new StyleEntry(ARIAL_12);
        var entry2 = new StyleEntry(ARIAL_14);
        assertThat(entry1).isEqualTo(entry2);
        assertThat(entry1.hashCode()).isEqualTo(entry2.hashCode());
    }
}
