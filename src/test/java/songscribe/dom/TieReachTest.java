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

import java.util.EnumSet;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;
import songscribe.dom.StaffElementRun.ElementRange;
import songscribe.engraving.StaffPosition;
import songscribe.font.DocumentFonts;
import songscribe.message.mutation.ElementDeletion;
import songscribe.message.mutation.ElementField;
import songscribe.message.mutation.ElementInsertion;
import songscribe.message.mutation.ElementModification;
import songscribe.message.mutation.ElementReplacement;
import songscribe.message.mutation.FontChange;
import songscribe.message.mutation.LayoutChange;
import songscribe.message.mutation.LayoutField;
import songscribe.message.mutation.LineDeletion;
import songscribe.message.mutation.LineInsertion;
import songscribe.message.mutation.LineKeyChange;
import songscribe.message.mutation.LineLayoutChange;
import songscribe.message.mutation.LineLayoutField;
import songscribe.message.mutation.LyricsChange;
import songscribe.message.mutation.LyricsField;
import songscribe.message.mutation.MetadataChange;
import songscribe.message.mutation.MetadataField;
import songscribe.message.mutation.Mutation;
import songscribe.message.mutation.SpanAddition;
import songscribe.message.mutation.SpanRemoval;
import songscribe.message.mutation.TieAddition;
import songscribe.message.mutation.TieRemoval;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.SongFactory.compactSong;
import static songscribe.dom.SongFactory.noteAt;
import static songscribe.dom.SpanFactory.addWithoutTracking;
import static songscribe.dom.SpanFactory.beamOver;
import static songscribe.dom.SpanFactory.hairpinOver;
import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.dom.StaffElementFactory.quaver;
import static songscribe.util.SealedTypes.leavesOf;

/** Which neighboring lines an edit at a line's edge reaches ({@link TieReach#reach}). */
class TieReachTest extends UnitTest {

    private static final int PITCH = 3;
    private static final int OTHER_PITCH = 4;

    private static final int FIRST_LINE = 0;
    private static final int SECOND_LINE = 1;
    private static final int THIRD_LINE = 2;
    private static final int FIRST_ELEMENT = 0;
    private static final int SECOND_ELEMENT = 1;

    /**
     * One edit on a song, and the lines it reaches.
     *
     * @param description the edit, as the test's display name
     * @param lines       the song's lines in compact notation
     * @param mutation    lands the edit on the song, and returns the mutation that records it
     * @param reached     the indices of the lines {@link TieReach#reach} names, in any order
     */
    private record ReachCase(String description, String[] lines, Function<Song, Mutation> mutation, List<Integer> reached) {}

    /** A middle line whose first non-separator is its second element and whose last is its fourth. */
    private static final String[] SEPARATED_MIDDLE =
        {"" + PITCH, "| " + PITCH + " " + PITCH + " " + PITCH + " |", "" + PITCH};

    /** The indices, in {@link #SEPARATED_MIDDLE}'s middle line, of its last two non-separators. */
    private static final int PENULTIMATE_NOTE = 2;
    private static final int LAST_NOTE = 3;

    /** Four quavers that a beam can cover, then a line holding a note. */
    private static final String[] BEAMABLE_LINE_THEN_NOTE = {"Q Q Q Q", "" + PITCH};

    /** The indices, in {@link #BEAMABLE_LINE_THEN_NOTE}'s first line, of its last two quavers. */
    private static final int PENULTIMATE_QUAVER = 2;
    private static final int LAST_QUAVER = 3;

    /** The index, in {@link #SEPARATED_MIDDLE}'s middle line, of its closing barline. */
    private static final int CLOSING_BARLINE = 4;

    @ParameterizedTest(name = "{0}")
    @MethodSource("reachCases")
    void testALandedEditReachesTheLineBesideItOnlyAtALinesEdge(ReachCase testCase) {
        var song = compactSong(Key.NO_ACCIDENTALS, testCase.lines());

        assertThat(TieReach.reach(testCase.mutation().apply(song)))
            .containsExactlyInAnyOrderElementsOf(testCase.reached().stream().map(song::getLine).toList());
    }

    static Stream<ReachCase> reachCases() {
        return Stream.of(
            new ReachCase("before the first non-separator reaches the previous line",
                SEPARATED_MIDDLE, song -> insertion(song, SECOND_LINE, FIRST_ELEMENT), List.of(FIRST_LINE)),
            new ReachCase("at the first non-separator reaches the previous line",
                SEPARATED_MIDDLE, song -> insertion(song, SECOND_LINE, SECOND_ELEMENT), List.of(FIRST_LINE)),
            new ReachCase("an insertion before the last non-separator reaches no line",
                SEPARATED_MIDDLE, song -> insertion(song, SECOND_LINE, LAST_NOTE), List.of()),
            new ReachCase("an insertion after the last non-separator reaches the next line",
                SEPARATED_MIDDLE, song -> insertion(song, SECOND_LINE, CLOSING_BARLINE), List.of(THIRD_LINE)),
            new ReachCase("a deletion of the line's opening reaches the previous line",
                SEPARATED_MIDDLE,
                song -> deletion(song, SECOND_LINE, new ElementRange(FIRST_ELEMENT, SECOND_ELEMENT)),
                List.of(FIRST_LINE)),
            new ReachCase("a deletion of the line's last notes reaches the next line",
                SEPARATED_MIDDLE,
                song -> deletion(song, SECOND_LINE, new ElementRange(PENULTIMATE_NOTE, LAST_NOTE)),
                List.of(THIRD_LINE)),
            new ReachCase("a line holding only separators reaches both neighbors",
                new String[] {"" + PITCH, "|", "" + PITCH},
                song -> insertion(song, SECOND_LINE, FIRST_ELEMENT), List.of(FIRST_LINE, THIRD_LINE)),
            new ReachCase("a replacement at the first non-separator reaches the previous line",
                SEPARATED_MIDDLE,
                song -> {
                    var line = song.getLine(SECOND_LINE);
                    var replaced = line.getElement(SECOND_ELEMENT);
                    var replacement = crotchet();

                    song.withModification(() -> line.setElement(SECOND_ELEMENT, replacement));

                    return new ElementReplacement(line, SECOND_ELEMENT, replaced, replacement);
                },
                List.of(FIRST_LINE)),
            new ReachCase("a pitch change at the last non-separator reaches the next line",
                SEPARATED_MIDDLE, song -> pitchChange(song, new Cursor(SECOND_LINE, LAST_NOTE)), List.of(THIRD_LINE)),
            new ReachCase("a tie addition on the last non-separator reaches the next line",
                SEPARATED_MIDDLE,
                song -> {
                    var line = song.getLine(SECOND_LINE);
                    var anchor = noteAt(song, new Cursor(SECOND_LINE, LAST_NOTE));

                    song.withModification(() -> line.addTie(anchor));

                    return new TieAddition(line, anchor);
                },
                List.of(THIRD_LINE)),
            new ReachCase("a tie removal on the first non-separator reaches the previous line",
                SEPARATED_MIDDLE,
                song -> {
                    var line = song.getLine(SECOND_LINE);
                    var anchor = noteAt(song, new Cursor(SECOND_LINE, SECOND_ELEMENT));

                    song.withModification(() -> line.addTie(anchor));
                    song.withModification(() -> line.removeTie(anchor));

                    return new TieRemoval(line, anchor);
                },
                List.of(FIRST_LINE)),
            new ReachCase("a beam added over the last notes of a line reaches the next line",
                BEAMABLE_LINE_THEN_NOTE,
                song -> {
                    var line = song.getLine(FIRST_LINE);
                    var beam = beamOver(line, lastTwoQuavers());

                    song.withModification(beam::addTo);

                    return new SpanAddition(line, beam);
                },
                List.of(SECOND_LINE)),
            new ReachCase("a beam removed from the last notes of a line reaches the next line",
                BEAMABLE_LINE_THEN_NOTE,
                song -> {
                    var line = song.getLine(FIRST_LINE);
                    var beam = addWithoutTracking(song, () -> beamOver(line, lastTwoQuavers()));

                    song.withModification(beam::removeFrom);

                    return new SpanRemoval(line, beam);
                },
                List.of(SECOND_LINE)),
            new ReachCase("a pitch change on an inner member of a beam that ends the line reaches the next line",
                BEAMABLE_LINE_THEN_NOTE,
                song -> {
                    var line = song.getLine(FIRST_LINE);

                    addWithoutTracking(song, () -> beamOver(line, new ElementRange(FIRST_ELEMENT, LAST_QUAVER)));

                    return pitchChange(song, new Cursor(FIRST_LINE, SECOND_ELEMENT));
                },
                List.of(SECOND_LINE))
        );
    }

    private static ElementRange lastTwoQuavers() {
        return new ElementRange(PENULTIMATE_QUAVER, LAST_QUAVER);
    }

    private static Mutation insertion(Song song, int lineIndex, int index) {
        var line = song.getLine(lineIndex);
        var inserted = crotchet();

        song.withModification(() -> line.addElement(index, inserted));

        return new ElementInsertion(line, index, inserted);
    }

    private static Mutation deletion(Song song, int lineIndex, ElementRange range) {
        var line = song.getLine(lineIndex);
        var deleted = List.copyOf(line.getElements().subList(range.begin(), range.end() + 1));

        song.withModification(() -> line.removeRange(range));

        return new ElementDeletion(line, range, deleted);
    }

    private static Mutation pitchChange(Song song, Cursor cursor) {
        var line = song.getLine(cursor.lineIndex());
        var note = noteAt(song, cursor);
        var before = StaffElement.convertedFrom(note.getType(), note);

        song.withModification(() -> note.setStaffPosition(new StaffPosition(OTHER_PITCH)));

        return new ElementModification(line, cursor.index(), EnumSet.of(ElementField.PITCH), before, note);
    }
}
