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
        grace.setGlissando();
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

    // -----------------------------------------------------------------------
    // isInsideGraceHostPair (Row 39)
    // -----------------------------------------------------------------------

    @Test
    void testIsInsideGraceHostPairAtPairedGraceIndexReturnsTrue() {
        // Index 0 is the paired grace note itself — clicking on it is inside the pair.
        var line = pairedGraceLine();
        assertThat(line.isInsideGraceHostPair(0)).isTrue();
    }

    @Test
    void testIsInsideGraceHostPairAtHostIndexReturnsTrue() {
        // Index 1 is the host — index-1 (the grace at 0) is a paired grace,
        // so inserting between the grace and the host is blocked.
        var line = pairedGraceLine();
        assertThat(line.isInsideGraceHostPair(1)).isTrue();
    }

    @Test
    void testIsInsideGraceHostPairAtIndexTwoReturnsFalse() {
        // Build: [grace+CONNECTED, host, crotchet]. Index 2 is past the pair.
        var line = pairedGraceLine();
        line.addElement(ElementType.CROTCHET.newInstance());
        assertThat(line.isInsideGraceHostPair(2)).isFalse();
    }

    @Test
    void testIsInsideGraceHostPairWithUnpairedGraceReturnsFalse() {
        // An unpaired grace (no CONNECTED glissando) does not block insertion.
        var line = unpairedGraceLine();
        assertThat(line.isInsideGraceHostPair(0)).isFalse();
        assertThat(line.isInsideGraceHostPair(1)).isFalse();
    }

    // -----------------------------------------------------------------------
    // isPairedGraceNote (Row 40)
    // -----------------------------------------------------------------------

    @Test
    void testIsPairedGraceNoteWithConnectedGlissandoReturnsTrue() {
        // Direct test: a grace note carrying a CONNECTED glissando is a paired grace.
        var line = pairedGraceLine();
        assertThat(line.isPairedGraceNote(0)).isTrue();
    }

    @Test
    void testIsPairedGraceNoteWithoutGlissandoReturnsFalse() {
        // A grace note without any glissando is not paired.
        var line = unpairedGraceLine();
        assertThat(line.isPairedGraceNote(0)).isFalse();
    }

    @Test
    void testIsPairedGraceNoteOnNonGraceReturnsFalse() {
        // A normal note (not a grace note) is never a paired grace.
        var line = pairedGraceLine();
        assertThat(line.isPairedGraceNote(1)).isFalse();
    }

    @Test
    void testIsPairedGraceNoteAtNegativeIndexReturnsFalse() {
        var line = pairedGraceLine();
        assertThat(line.isPairedGraceNote(-1)).isFalse();
    }

    @Test
    void testIsPairedGraceNoteAtOutOfBoundsIndexReturnsFalse() {
        var line = pairedGraceLine();
        assertThat(line.isPairedGraceNote(line.elementCount())).isFalse();
    }

    // -----------------------------------------------------------------------
    // precedingGraceNoteIndex (Row 41)
    // -----------------------------------------------------------------------

    @Test
    void testPrecedingGraceNoteIndexWhenPrecedingIsGraceReturnsIndex() {
        // Index 1 has a grace note at 0 immediately before it.
        var line = pairedGraceLine();
        assertThat(line.precedingGraceNoteIndex(1)).isEqualTo(0);
    }

    @Test
    void testPrecedingGraceNoteIndexWhenPrecedingIsNoteReturnsMinusOne() {
        // Build: [crotchet, crotchet]. Index 1 has a non-grace preceding it.
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET.newInstance());
        line.addElement(ElementType.CROTCHET.newInstance());
        assertThat(line.precedingGraceNoteIndex(1)).isEqualTo(-1);
    }

    @Test
    void testPrecedingGraceNoteIndexAtIndexZeroReturnsMinusOne() {
        // No element precedes index 0.
        var line = pairedGraceLine();
        assertThat(line.precedingGraceNoteIndex(0)).isEqualTo(-1);
    }
}
