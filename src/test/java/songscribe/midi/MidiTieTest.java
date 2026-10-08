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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;
import songscribe.dom.Key;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.SongFactory.compactSong;
import static songscribe.midi.MidiSequences.PLAIN_PLAYBACK;
import static songscribe.midi.MidiSequences.noteOnPitches;
import static songscribe.midi.MidiSequences.playbackOf;

/**
 * A tie chain sounds as one note: a tied note starts no new note when its incoming tie arrives,
 * within a line or across a line break, and the chain releases once, where its last note
 * releases. Every note of the chain advances time by its own written duration.
 */
class MidiTieTest extends UnitTest {

    private static final int PITCH = 3;

    /** What two untied notes sound as. */
    private static final int TWO_NOTES = 2;

    /** What a tied pair sounds as. */
    private static final int ONE_NOTE = 1;

    /** The written notes of a tied pair, and of a chain with a middle note. */
    private static final int PAIR_NOTES = 2;
    private static final int CHAIN_NOTES = 3;

    /**
     * A song and how many notes it sounds.
     *
     * @param description  the case, as the test's display name
     * @param lines        the song's lines in compact notation
     * @param soundedNotes how many note-ons the sequence holds
     */
    private record TieCase(String description, String[] lines, int soundedNotes) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("tieCases")
    void testATiedPairSoundsAsOneNote(TieCase testCase) throws Exception {
        var song = compactSong(Key.NO_ACCIDENTALS, testCase.lines());

        var sequence = playbackOf(song, PLAIN_PLAYBACK);

        assertThat(noteOnPitches(sequence)).hasSize(testCase.soundedNotes());
    }

    static Stream<TieCase> tieCases() {
        return Stream.of(
            new TieCase("two untied notes sound twice",
                new String[] {PITCH + " " + PITCH}, TWO_NOTES),
            new TieCase("a tied pair sounds once",
                new String[] {PITCH + " ~ " + PITCH}, ONE_NOTE),
            new TieCase("a tied pair across a barline sounds once",
                new String[] {PITCH + " ~ | " + PITCH}, ONE_NOTE),
            new TieCase("a tied pair across a line break sounds once",
                new String[] {PITCH + " ~", "" + PITCH}, ONE_NOTE),
            new TieCase("a chain of three sounds once",
                new String[] {PITCH + " ~ " + PITCH + " ~ " + PITCH}, ONE_NOTE),
            new TieCase("a chain of three across a line break sounds once",
                new String[] {PITCH + " ~ " + PITCH + " ~", "" + PITCH}, ONE_NOTE)
        );
    }

    /**
     * A tie chain, and how many written notes it joins.
     *
     * @param description  the case, as the test's display name
     * @param lines        the song's lines in compact notation, holding only the chain
     * @param writtenNotes the written notes the chain joins
     */
    private record ChainCase(String description, String[] lines, int writtenNotes) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("chainCases")
    void testAChainReleasesOnceAtTheEndOfItsLastNote(ChainCase testCase) throws Exception {
        var lone = playbackOf(compactSong(Key.NO_ACCIDENTALS, "" + PITCH), PLAIN_PLAYBACK);
        var writtenTicks = lone.getTracks()[0].ticks();
        var loneReleaseTick = noteOffTicks(lone).getFirst();

        var chain = playbackOf(compactSong(Key.NO_ACCIDENTALS, testCase.lines()), PLAIN_PLAYBACK);

        assertThat(noteOnTicks(chain)).containsExactly(0L);
        assertThat(noteOffTicks(chain))
            .as("one release, where the last note of the chain releases, and not where the first one ends")
            .containsExactly((testCase.writtenNotes() - 1) * writtenTicks + loneReleaseTick);
        assertThat(chain.getTracks()[0].ticks())
            .as("every note of the chain advances time by its own written duration")
            .isEqualTo(testCase.writtenNotes() * writtenTicks);
    }

    static Stream<ChainCase> chainCases() {
        return Stream.of(
            new ChainCase("a tied pair", new String[] {PITCH + " ~ " + PITCH}, PAIR_NOTES),
            new ChainCase("a tied pair across a line break", new String[] {PITCH + " ~", "" + PITCH}, PAIR_NOTES),
            new ChainCase("a chain of three",
                new String[] {PITCH + " ~ " + PITCH + " ~ " + PITCH}, CHAIN_NOTES),
            new ChainCase("a chain of three across a line break",
                new String[] {PITCH + " ~ " + PITCH + " ~", "" + PITCH}, CHAIN_NOTES)
        );
    }

    /** The tick of every sounding note-on in the sequence's tracks, in track order. */
    private static List<Long> noteOnTicks(Sequence sequence) {
        return messageTicks(sequence, message ->
            message.getCommand() == ShortMessage.NOTE_ON && message.getData2() > 0);
    }

    /** The tick of every note-off in the sequence's tracks, in track order. */
    private static List<Long> noteOffTicks(Sequence sequence) {
        return messageTicks(sequence, message ->
            message.getCommand() == ShortMessage.NOTE_OFF
                || message.getCommand() == ShortMessage.NOTE_ON && message.getData2() == 0);
    }

    private static List<Long> messageTicks(Sequence sequence, Predicate<ShortMessage> matches) {
        var ticks = new ArrayList<Long>();

        for (var track : sequence.getTracks()) {
            for (var i = 0; i < track.size(); i++) {
                var event = track.get(i);

                if (event.getMessage() instanceof ShortMessage message && matches.test(message)) {
                    ticks.add(event.getTick());
                }
            }
        }

        return ticks;
    }
}
