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

import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;
import songscribe.dom.StaffElementRun.ElementRange;
import songscribe.message.mutation.ElementDeletion;
import songscribe.message.mutation.ElementInsertion;
import songscribe.message.mutation.LineInsertion;
import songscribe.message.mutation.Mutation;
import songscribe.message.mutation.TieRemoval;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.SongFactory.compactSong;
import static songscribe.dom.StaffElementFactory.crotchet;

/**
 * Where a tie's derived end lies, and which lines a tie concerns: {@link NoteElement#tie()} and
 * {@link NoteElement#incomingTie()} walk over legal separators and across one line break,
 * {@link Line#ownedTies()} lists a tie once in each line that draws it, and
 * {@link Song#tieReach} names the neighboring lines an edit at a line's edge reaches.
 */
class TieWalkTest extends UnitTest {

    private static final int PITCH = 3;
    private static final int FIRST_LINE = 0;
    private static final int SECOND_LINE = 1;
    private static final int THIRD_LINE = 2;
    private static final int FIRST_ELEMENT = 0;
    private static final int SECOND_ELEMENT = 1;
    private static final int THIRD_ELEMENT = 2;

    /**
     * A song with its first element tied, and where the tie ends.
     *
     * @param description the case, as the test's display name
     * @param lines       the song's lines in compact notation, the first line's first element a
     *                    note that the case ties
     * @param end         where the tie's end stands, or {@code null} when the tie has no end
     */
    private record WalkCase(String description, String[] lines, @Nullable Cursor end) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("walkCases")
    void testATiesEndIsTheFirstElementPastTheSeparatorsWhenThatIsANote(WalkCase testCase) {
        var song = compactSong(Key.NO_ACCIDENTALS, testCase.lines());
        var firstLine = song.getLine(FIRST_LINE);
        var anchor = (NoteElement) firstLine.getElement(FIRST_ELEMENT);
        song.withoutMutationTracking(() -> firstLine.addTie(anchor));

        var tie = anchor.tie();
        var expected = testCase.end();

        if (expected == null) {
            assertThat(tie).isNull();
            return;
        }

        var end = (NoteElement) song.getLine(expected.lineIndex()).getElement(expected.index());

        assertThat(tie).isNotNull();
        assertThat(tie.end()).isSameAs(end);
        assertThat(end.incomingTie()).as("the end answers the same tie from its side").isEqualTo(tie);
    }

    static Stream<WalkCase> walkCases() {
        return Stream.of(
            new WalkCase("the next note",
                new String[] {PITCH + " " + PITCH}, new Cursor(FIRST_LINE, SECOND_ELEMENT)),
            new WalkCase("past a barline",
                new String[] {PITCH + " | " + PITCH}, new Cursor(FIRST_LINE, THIRD_ELEMENT)),
            new WalkCase("past a breath mark",
                new String[] {PITCH + " B " + PITCH}, new Cursor(FIRST_LINE, THIRD_ELEMENT)),
            new WalkCase("the first note of the next line",
                new String[] {"" + PITCH, "" + PITCH}, new Cursor(SECOND_LINE, FIRST_ELEMENT)),
            new WalkCase("past a barline that starts the next line",
                new String[] {"" + PITCH, "| " + PITCH}, new Cursor(SECOND_LINE, SECOND_ELEMENT)),
            new WalkCase("none when the walk reaches a rest",
                new String[] {PITCH + " R " + PITCH}, null),
            new WalkCase("none when the walk reaches a grace note",
                new String[] {PITCH + " g" + PITCH + " " + PITCH}, null),
            new WalkCase("none when the walk reaches a key change",
                new String[] {PITCH + " K:ONE_SHARP " + PITCH}, null),
            new WalkCase("none when the next line holds only separators",
                new String[] {"" + PITCH, "|", "" + PITCH}, null),
            new WalkCase("none when the song runs out",
                new String[] {"" + PITCH}, null)
        );
    }

    @Test
    void testATieIsOwnedOnceByEachLineThatDrawsIt() {
        var song = compactSong(Key.NO_ACCIDENTALS, PITCH + " ~ " + PITCH + " " + PITCH, "" + PITCH);
        var firstLine = song.getLine(FIRST_LINE);
        var secondLine = song.getLine(SECOND_LINE);
        var crossing = (NoteElement) firstLine.getElement(THIRD_ELEMENT);
        song.withoutMutationTracking(() -> firstLine.addTie(crossing));

        var inLine = ((NoteElement) firstLine.getElement(FIRST_ELEMENT)).tie();
        var acrossTheBreak = crossing.tie();

        assertThat(firstLine.ownedTies()).containsExactly(inLine, acrossTheBreak);
        assertThat(secondLine.ownedTies()).containsExactly(acrossTheBreak);
    }

    /**
     * One mutation on a song of three lines, and the lines it reaches.
     *
     * @param description the mutation, as the test's display name
     * @param lines       the song's lines in compact notation
     * @param mutation    builds the mutation against the song
     * @param reached     the indices of the lines {@link Song#tieReach} names, in any order
     */
    private record ReachCase(String description, String[] lines, Function<Song, Mutation> mutation, List<Integer> reached) {}

    /** A middle line whose first non-separator is its second element and whose last is its fourth. */
    private static final String[] SEPARATED_MIDDLE =
        {"" + PITCH, "| " + PITCH + " " + PITCH + " " + PITCH + " |", "" + PITCH};

    /** The index, in {@link #SEPARATED_MIDDLE}'s middle line, of its last non-separator. */
    private static final int LAST_NOTE = 3;

    /** The index, in {@link #SEPARATED_MIDDLE}'s middle line, of its closing barline. */
    private static final int CLOSING_BARLINE = 4;

    @ParameterizedTest(name = "{0}")
    @MethodSource("reachCases")
    void testAnEditAtALinesEdgeReachesTheLineBesideIt(ReachCase testCase) {
        var song = compactSong(Key.NO_ACCIDENTALS, testCase.lines());

        assertThat(song.tieReach(testCase.mutation().apply(song)))
            .containsExactlyInAnyOrderElementsOf(testCase.reached().stream().map(song::getLine).toList());
    }

    static Stream<ReachCase> reachCases() {
        return Stream.of(
            new ReachCase("before the first non-separator reaches the previous line",
                SEPARATED_MIDDLE, song -> insertion(song, SECOND_LINE, FIRST_ELEMENT), List.of(FIRST_LINE)),
            new ReachCase("at the first non-separator reaches the previous line",
                SEPARATED_MIDDLE, song -> insertion(song, SECOND_LINE, SECOND_ELEMENT), List.of(FIRST_LINE)),
            new ReachCase("between the edges reaches no line",
                SEPARATED_MIDDLE, song -> insertion(song, SECOND_LINE, THIRD_ELEMENT), List.of()),
            new ReachCase("at the last non-separator reaches the next line",
                SEPARATED_MIDDLE, song -> insertion(song, SECOND_LINE, LAST_NOTE), List.of(THIRD_LINE)),
            new ReachCase("past the last non-separator reaches the next line",
                SEPARATED_MIDDLE, song -> insertion(song, SECOND_LINE, CLOSING_BARLINE), List.of(THIRD_LINE)),
            new ReachCase("a deletion is asked at its first index",
                SEPARATED_MIDDLE,
                song -> new ElementDeletion(song.getLine(SECOND_LINE),
                    new ElementRange(FIRST_ELEMENT, SECOND_ELEMENT), List.of()),
                List.of(FIRST_LINE)),
            new ReachCase("a line holding only separators reaches both neighbors",
                new String[] {"" + PITCH, "|", "" + PITCH},
                song -> insertion(song, SECOND_LINE, FIRST_ELEMENT), List.of(FIRST_LINE, THIRD_LINE)),
            new ReachCase("the first line has no previous line to reach",
                new String[] {PITCH + " " + PITCH + " " + PITCH, "" + PITCH},
                song -> insertion(song, FIRST_LINE, FIRST_ELEMENT), List.of()),
            new ReachCase("a tie removal whose anchor has left its line reaches no line",
                SEPARATED_MIDDLE, song -> new TieRemoval(song.getLine(SECOND_LINE), crotchet()), List.of()),
            new ReachCase("a line insertion names no position",
                SEPARATED_MIDDLE, song -> new LineInsertion(SECOND_LINE, song.getLine(SECOND_LINE)), List.of())
        );
    }

    private static Mutation insertion(Song song, int lineIndex, int index) {
        return new ElementInsertion(song.getLine(lineIndex), index, crotchet());
    }
}
