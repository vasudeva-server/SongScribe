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

package songscribe.midi;

import java.util.stream.Stream;

import javax.sound.midi.ShortMessage;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;
import songscribe.dom.Key;
import songscribe.dom.NoteElement;
import songscribe.dom.Song;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.SongFactory.compactSong;

/**
 * A tie joins two written notes into one sounding note: a tied note starts no new note when its
 * incoming tie arrives, within a line or across a line break.
 */
class MidiTieTest extends UnitTest {

    private static final int PITCH = 3;
    private static final int FIRST_LINE = 0;

    private static final int PIANO = 0;
    private static final int FULL_PERCENT = 100;

    /** Every written note sounds for its full length, at the written tempo, without repeats. */
    private static final PlaybackSettings PLAIN_PLAYBACK = new PlaybackSettings(PIANO, FULL_PERCENT, FULL_PERCENT, false);

    /** What two untied notes sound as. */
    private static final int TWO_NOTES = 2;

    /** What a tied pair sounds as. */
    private static final int ONE_NOTE = 1;

    /**
     * A song and how many notes it sounds.
     *
     * @param description the case, as the test's display name
     * @param lines       the song's lines in compact notation
     * @param tieAcross   whether the first line's last note is tied into the second line
     * @param soundedNotes how many note-ons the sequence holds
     */
    private record TieCase(String description, String[] lines, boolean tieAcross, int soundedNotes) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("tieCases")
    void testATiedPairSoundsAsOneNote(TieCase testCase) throws Exception {
        var song = compactSong(Key.NO_ACCIDENTALS, testCase.lines());

        if (testCase.tieAcross()) {
            tieLastNoteOfFirstLine(song);
        }

        var sequence = new MidiSequenceBuilder(song, PLAIN_PLAYBACK).buildFullSequence();
        var noteOns = 0;

        for (var track : sequence.getTracks()) {
            for (var i = 0; i < track.size(); i++) {
                if (track.get(i).getMessage() instanceof ShortMessage message
                        && message.getCommand() == ShortMessage.NOTE_ON && message.getData2() > 0) {
                    noteOns++;
                }
            }
        }

        assertThat(noteOns).isEqualTo(testCase.soundedNotes());
    }

    static Stream<TieCase> tieCases() {
        return Stream.of(
            new TieCase("two untied notes sound twice",
                new String[] {PITCH + " " + PITCH}, false, TWO_NOTES),
            new TieCase("a tied pair sounds once",
                new String[] {PITCH + " ~ " + PITCH}, false, ONE_NOTE),
            new TieCase("a tied pair across a barline sounds once",
                new String[] {PITCH + " ~ | " + PITCH}, false, ONE_NOTE),
            new TieCase("a tied pair across a line break sounds once",
                new String[] {"" + PITCH, "" + PITCH}, true, ONE_NOTE)
        );
    }

    /** The compact grammar ties only within a line, so a tie across the break is added here. */
    private static void tieLastNoteOfFirstLine(Song song) {
        var line = song.getLine(FIRST_LINE);
        var anchor = (NoteElement) line.getElement(line.elementCount() - 1);

        song.withoutMutationTracking(() -> line.addTie(anchor));
    }
}
