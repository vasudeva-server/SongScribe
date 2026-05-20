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

class LineGraceNotePairingTest extends UnitTest {

    /** Builds a line: [grace+CONNECTED glissando, host crotchet]. Index 0 is a paired grace. */
    private Line pairedGraceLine() {
        var line = detachedLine();
        var grace = ElementType.GRACE_QUAVER.newInstance();
        grace.setGlissando(StaffElement.Glissando.Type.CONNECTED);
        line.addElement(grace);
        line.addElement(ElementType.CROTCHET.newInstance());
        return line;
    }

    /** Builds a line: [grace (no glissando), crotchet]. Index 0 is an unpaired grace. */
    private Line unpairedGraceLine() {
        var line = detachedLine();
        line.addElement(ElementType.GRACE_QUAVER.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        return line;
    }

    @Test
    void testPairedGraceHostAtIndex1ReturnsTrue() {
        var line = pairedGraceLine();
        assertThat(line.isHostOfPairedGraceNote(1)).isTrue();
    }

    @Test
    void testPairedGraceItselfAtIndex0ReturnsFalse() {
        var line = pairedGraceLine();
        assertThat(line.isHostOfPairedGraceNote(0)).isFalse();
    }

    @Test
    void testUnpairedGraceHostAtIndex1ReturnsFalse() {
        var line = unpairedGraceLine();
        assertThat(line.isHostOfPairedGraceNote(1)).isFalse();
    }

    @Test
    void testNonGraceAtPrecedingIndexReturnsFalse() {
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        assertThat(line.isHostOfPairedGraceNote(1)).isFalse();
    }

    @Test
    void testIndexZeroReturnsFalse() {
        var line = pairedGraceLine();
        assertThat(line.isHostOfPairedGraceNote(0)).isFalse();
    }
}
