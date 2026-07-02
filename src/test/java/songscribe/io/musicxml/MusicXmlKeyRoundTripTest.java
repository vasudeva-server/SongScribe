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

package songscribe.io.musicxml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

import songscribe.dom.ElementType;
import songscribe.dom.KeyType;
import songscribe.dom.Song;

/**
 * Round-trip tests for per-line key signatures. The writer emits the song
 * default at measure 1 and a key-only {@code <attributes>} at each later line
 * whose key differs from the running key; the reader restores the song default
 * from measure 1 and carries the running key forward across lines, applying a
 * later {@code <key>} to the line it opens.
 */
class MusicXmlKeyRoundTripTest extends MusicXmlRoundTripSupport {

    /** Sharps on the first mid-song key change. */
    private static final int MID_SONG_SHARP_COUNT = 3;

    /** Flats on the second mid-song key change (and the line that keeps it). */
    private static final int MID_SONG_FLAT_COUNT = 2;

    /** Line 1 (song default), two mid-song changes, then a line that keeps the last key. */
    private static final int EXPECTED_LINE_COUNT = 4;

    /** Sharps on a song whose global default key is itself non-default. */
    private static final int SONG_DEFAULT_SHARP_COUNT = 4;

    /**
     * Four lines: line 1 carries the song default, line 2 changes to sharps,
     * line 3 changes to flats, and line 4 keeps line 3's key (so the writer
     * emits no {@code <key>} for it and the reader must carry the running key
     * forward). Every line holds one note so it is a real, non-empty system.
     */
    private static Song buildKeyChangeSong() {
        return buildSong(
            line -> {
                line.setKeyType(Song.DEFAULT_KEY_TYPE);
                line.setKeyAccidentalCount(Song.DEFAULT_KEY_ACCIDENTAL_COUNT);
                line.addElement(ElementType.CROTCHET.newInstance());
            },
            line -> {
                line.setKeyType(KeyType.SHARPS);
                line.setKeyAccidentalCount(MID_SONG_SHARP_COUNT);
                line.addElement(ElementType.CROTCHET.newInstance());
            },
            line -> {
                line.setKeyType(KeyType.FLATS);
                line.setKeyAccidentalCount(MID_SONG_FLAT_COUNT);
                line.addElement(ElementType.CROTCHET.newInstance());
            },
            line -> {
                // Same key as line 3: the writer emits no <key> here, so the
                // reader can only reconstruct it by carrying the running key.
                line.setKeyType(KeyType.FLATS);
                line.setKeyAccidentalCount(MID_SONG_FLAT_COUNT);
                line.addElement(ElementType.CROTCHET.newInstance());
            }
        );
    }

    @Test
    void testMidSongKeyChangesRoundTrip() throws Exception {
        var song2 = roundTrip(buildKeyChangeSong());

        assertThat(song2.getLines())
            .as("line count after key-change round-trip")
            .hasSize(EXPECTED_LINE_COUNT);

        assertLineKey(song2, 0, Song.DEFAULT_KEY_TYPE, Song.DEFAULT_KEY_ACCIDENTAL_COUNT);
        assertLineKey(song2, 1, KeyType.SHARPS, MID_SONG_SHARP_COUNT);
        assertLineKey(song2, 2, KeyType.FLATS, MID_SONG_FLAT_COUNT);
        assertLineKey(song2, 3, KeyType.FLATS, MID_SONG_FLAT_COUNT);

        assertThat(song2.getDefaultKeyType())
            .as("song default key type")
            .isEqualTo(Song.DEFAULT_KEY_TYPE);
        assertThat(song2.getDefaultKeyAccidentalCount())
            .as("song default accidental count")
            .isEqualTo(Song.DEFAULT_KEY_ACCIDENTAL_COUNT);
    }

    /**
     * A single-line song whose global default key is non-default (sharps). The
     * writer emits it at measure 1 and the reader must decode the song-default
     * seed — not just later per-line changes — via the fifths mapping. Line 1's
     * own key matches the default so it survives materialization on reload.
     */
    private static Song buildNonDefaultSongKeySong() {
        var song = buildSong(line -> {
            line.setKeyType(KeyType.SHARPS);
            line.setKeyAccidentalCount(SONG_DEFAULT_SHARP_COUNT);
            line.addElement(ElementType.CROTCHET.newInstance());
        });

        song.withoutMutationTracking(() -> {
            song.setDefaultKeyType(KeyType.SHARPS);
            song.setDefaultKeyAccidentalCount(SONG_DEFAULT_SHARP_COUNT);
        });

        return song;
    }

    @Test
    void testNonDefaultSongKeyRoundTrips() throws Exception {
        var song2 = roundTrip(buildNonDefaultSongKeySong());

        assertThat(song2.getDefaultKeyType())
            .as("song default key type")
            .isEqualTo(KeyType.SHARPS);
        assertThat(song2.getDefaultKeyAccidentalCount())
            .as("song default accidental count")
            .isEqualTo(SONG_DEFAULT_SHARP_COUNT);

        assertLineKey(song2, 0, KeyType.SHARPS, SONG_DEFAULT_SHARP_COUNT);
    }

    @Test
    void testKeyChangeWriterOutputIsSchemaValid() throws Exception {
        var xml = writeToString(buildKeyChangeSong());
        var validator = new MusicXmlSchemaValidator();

        assertThatCode(() -> validator.validate(xml))
            .as("per-line key-change song must be schema-valid")
            .doesNotThrowAnyException();
    }

    private static void assertLineKey(Song song, int lineIndex, KeyType expectedType, int expectedCount) {
        var line = song.getLine(lineIndex);

        assertThat(line.getKeyType())
            .as("line %d key type", lineIndex)
            .isEqualTo(expectedType);
        assertThat(line.getKeyAccidentalCount())
            .as("line %d accidental count", lineIndex)
            .isEqualTo(expectedCount);
    }
}
