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

package songscribe.ui.layout;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.music.KeyType;

class LayoutResultTest extends UnitTest {

    // T3a: Builder.setClef() round-trips through getClef()
    @Test
    void testBuilderClefRoundTrip() {
        var clef = new Clef();
        var result = LayoutResult.builder()
            .setClef(clef)
            .build();

        assertThat(result.getClef()).isSameAs(clef);
    }

    // T3b: Builder.setKeySignature() round-trips through getKeySignature()
    @Test
    void testBuilderKeySignatureRoundTrip() {
        var keySig = new KeySignature(KeyType.FLATS, 2);
        var result = LayoutResult.builder()
            .setKeySignature(keySig)
            .build();

        assertThat(result.getKeySignature()).isSameAs(keySig);
    }

    // T3c: Builder without setClef/setKeySignature returns null for both
    @Test
    void testBuilderDefaultsToNullHeaderElements() {
        var result = LayoutResult.builder().build();

        assertThat(result.getClef()).isNull();
        assertThat(result.getKeySignature()).isNull();
    }
}
