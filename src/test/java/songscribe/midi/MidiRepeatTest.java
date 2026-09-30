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

import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;
import songscribe.dom.Key;
import songscribe.dom.Line;
import songscribe.dom.NoteElement;
import songscribe.dom.SongFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.StaffElementFactory.doubleBarline;
import static songscribe.dom.StaffElementFactory.note;
import static songscribe.dom.StaffElementFactory.repeatLeft;
import static songscribe.dom.StaffElementFactory.repeatLeftRight;
import static songscribe.dom.StaffElementFactory.repeatRight;
import static songscribe.midi.MidiSequences.noteOnPitches;

/**
 * {@code MidiSequenceBuilder.buildSequenceWithRepeats}: a {@code :|} or {@code :|:} replays the
 * section {@link songscribe.dom.Song#repeatSectionStart} names once, then plays on; a {@code :|}
 * with no open section plays on as an ordinary bar; a jump to an earlier line continues on that
 * line.
 */
class MidiRepeatTest extends UnitTest {

    private static final int FIRST_LINE = 0;

    /** Three staff positions, one per letter a case's notation names. */
    private static final int A_POSITION = 0;
    private static final int B_POSITION = 2;
    private static final int C_POSITION = 4;

    private static final int PIANO = 0;
    private static final int FULL_PERCENT = 100;

    /** Every written note sounds for its full length, at the written tempo, with repeats taken. */
    private static final PlaybackSettings REPEATED_PLAYBACK = new PlaybackSettings(PIANO, FULL_PERCENT, FULL_PERCENT, true);

    private int pitchA;
    private int pitchB;
    private int pitchC;

    @BeforeAll
    void computeLetterPitches() {
        var song = SongFactory.buildSong(line -> {
            line.setKey(Key.NO_ACCIDENTALS);
            line.addElement(note(A_POSITION));
            line.addElement(note(B_POSITION));
            line.addElement(note(C_POSITION));
        });
        var line = song.getLine(FIRST_LINE);

        pitchA = ((NoteElement) line.getElement(0)).getPitch();
        pitchB = ((NoteElement) line.getElement(1)).getPitch();
        pitchC = ((NoteElement) line.getElement(2)).getPitch();
    }

    /**
     * A song built from one {@link SongFactory.LineBuilder} per line, and the order its notes
     * must sound in.
     *
     * @param description   the case, as the test's display name
     * @param lines         the song's lines, in song order
     * @param expectedOrder the letters {@link #pitchA}, {@link #pitchB} and {@link #pitchC} name,
     *                      in the order their note-ons must appear
     */
    private record RepeatCase(String description, SongFactory.LineBuilder[] lines, String expectedOrder) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("repeatCases")
    void testPlaybackFollowsTheRepeatMarkers(RepeatCase testCase) throws Exception {
        var song = SongFactory.buildSong(testCase.lines());
        var sequence = new MidiSequenceBuilder(song, REPEATED_PLAYBACK).buildFullSequence();
        var order = new StringBuilder();

        noteOnPitches(sequence).forEach(pitch -> order.append(letterOf(pitch)));

        assertThat(order.toString()).isEqualTo(testCase.expectedOrder());
    }

    private String letterOf(int pitch) {
        if (pitch == pitchA) {
            return "A";
        }

        if (pitch == pitchB) {
            return "B";
        }

        if (pitch == pitchC) {
            return "C";
        }

        throw new AssertionError("a note-on at an unexpected pitch: " + pitch);
    }

    private static SongFactory.LineBuilder firstLine(Consumer<Line> populate) {
        return line -> {
            line.setKey(Key.NO_ACCIDENTALS);
            populate.accept(line);
        };
    }

    static Stream<RepeatCase> repeatCases() {
        return Stream.of(
            new RepeatCase("|: A :| B plays A A B",
                new SongFactory.LineBuilder[] {
                    firstLine(line -> {
                        line.addElement(repeatLeft());
                        line.addElement(note(A_POSITION));
                        line.addElement(repeatRight());
                        line.addElement(note(B_POSITION));
                    })
                },
                "AAB"),

            new RepeatCase("A :| B plays A A B",
                new SongFactory.LineBuilder[] {
                    firstLine(line -> {
                        line.addElement(note(A_POSITION));
                        line.addElement(repeatRight());
                        line.addElement(note(B_POSITION));
                    })
                },
                "AAB"),

            new RepeatCase("A :| B :| plays A A B, the second :| having no open section",
                new SongFactory.LineBuilder[] {
                    firstLine(line -> {
                        line.addElement(note(A_POSITION));
                        line.addElement(repeatRight());
                        line.addElement(note(B_POSITION));
                        line.addElement(repeatRight());
                    })
                },
                "AAB"),

            new RepeatCase("A || B :| plays A B, the || having closed the section before the :|",
                new SongFactory.LineBuilder[] {
                    firstLine(line -> {
                        line.addElement(note(A_POSITION));
                        line.addElement(doubleBarline());
                        line.addElement(note(B_POSITION));
                        line.addElement(repeatRight());
                    })
                },
                "AB"),

            new RepeatCase("|: A :|: B :| C plays A A B B C",
                new SongFactory.LineBuilder[] {
                    firstLine(line -> {
                        line.addElement(repeatLeft());
                        line.addElement(note(A_POSITION));
                        line.addElement(repeatLeftRight());
                        line.addElement(note(B_POSITION));
                        line.addElement(repeatRight());
                        line.addElement(note(C_POSITION));
                    })
                },
                "AABBC"),

            new RepeatCase("|: A on one line, B :| C on the next, plays A B A B C",
                new SongFactory.LineBuilder[] {
                    firstLine(line -> {
                        line.addElement(repeatLeft());
                        line.addElement(note(A_POSITION));
                    }),
                    line -> {
                        line.addElement(note(B_POSITION));
                        line.addElement(repeatRight());
                        line.addElement(note(C_POSITION));
                    }
                },
                "ABABC")
        );
    }
}
