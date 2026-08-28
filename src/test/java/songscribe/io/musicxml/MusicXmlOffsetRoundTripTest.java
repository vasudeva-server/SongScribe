/*
 * SongScribe song notation program
 * Copyright (C) Sri Chinmoy Centres International
 *
 * This file is part of SongScribe.
 *
 * SongScribe is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * SongScribe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package songscribe.io.musicxml;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Song;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.dom.SongFactory.buildSong;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.X_OFFSET_PX;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.roundTrip;

/**
 * How a note's hand-placed horizontal offset survives a write and a read, through the
 * px &rarr; ss &rarr; tenths &rarr; ss &rarr; px conversion {@code <relative-x>} carries it
 * through.
 */
class MusicXmlOffsetRoundTripTest extends UnitTest {

    @Test
    void testAnElementsXOffsetSurvivesAWriteAndAReadThroughTenths() throws Exception {
        var song = songWithXOffset(X_OFFSET_PX);

        var restored = roundTrip(song).getLine(0).getElement(0);

        assertThat(restored.getXOffsetPx()).isEqualTo(X_OFFSET_PX);
    }

    /** A one-line song whose only note carries {@code xOffsetPx} as its hand-placed X offset. */
    private static Song songWithXOffset(int xOffsetPx) {
        return buildSong(line -> {
            var note = crotchet();
            note.setXOffsetPx(xOffsetPx);
            line.addElement(note);
        });
    }
}
