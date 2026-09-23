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

import java.util.function.Consumer;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;
import songscribe.dom.BatchReplay.BatchRecorder;
import songscribe.engraving.StaffPosition;
import songscribe.message.mutation.ElementField;
import songscribe.message.mutation.ElementModification;
import songscribe.message.mutation.TieRemoval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static songscribe.dom.BatchReplay.redo;
import static songscribe.dom.BatchReplay.undo;
import static songscribe.dom.SongFactory.compactSong;
import static songscribe.dom.StaffElementFactory.graceQuaver;
import static songscribe.dom.StaffElementFactory.note;

/**
 * The tie rule on {@link NoteElement}: when the outermost modification bracket closes, a tie
 * stays only while its anchor and the element its walk reaches are notes at one staff position
 * sounding one alteration. It judges the lines the edit touched and the line before each, its
 * removals are undone with the edit that caused them, and it does not run in a replay, under
 * suspended tracking, or for an edit that throws.
 */
class TieRuleTest extends UnitTest {

    /** The staff position every tied note starts at. */
    private static final int TIED = 3;

    /** Another staff position, at a different pitch. */
    private static final int OTHER = 4;

    private static final int ANCHOR = 0;
    private static final int END = 1;
    private static final int FIRST_LINE = 0;
    private static final int SECOND_LINE = 1;

    /** A line with a tie from its first note to its second, and a note after them. */
    private static final String TIED_PAIR = TIED + " ~ " + TIED + " " + OTHER;

    /**
     * One edit to the line {@link #TIED_PAIR} builds, and what it leaves of the tie.
     *
     * @param description the edit, as the test's display name
     * @param edit        makes the edit, inside a modification bracket
     * @param endIndex    the index the tie from the first element ends on afterwards, or
     *                    {@code null} when the first element is no longer tied
     */
    private record EditCase(String description, Consumer<Line> edit, @Nullable Integer endIndex) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("editCases")
    void testATieStaysOnlyWhileItsNotesShareAPitch(EditCase testCase) {
        var song = compactSong(Key.NO_ACCIDENTALS, TIED_PAIR);
        var line = song.getLine(FIRST_LINE);

        song.withModification(() -> testCase.edit().accept(line));

        var first = (NoteElement) line.getElement(ANCHOR);
        var expectedEnd = testCase.endIndex();

        if (expectedEnd == null) {
            assertThat(first.isTiedToNext()).as("the tie is removed, not only broken").isFalse();
            return;
        }

        var tie = first.tie();

        assertThat(tie).isNotNull();
        assertThat(tie.end()).isSameAs(line.getElement(expectedEnd));
    }

    static Stream<EditCase> editCases() {
        return Stream.of(
            new EditCase("a sharp on the anchor removes it",
                line -> choose(line, ANCHOR, Accidental.SHARP), null),
            new EditCase("a sharp on the end removes it",
                line -> choose(line, END, Accidental.SHARP), null),
            new EditCase("moving one note to another pitch removes it",
                line -> move(line, END, OTHER), null),
            new EditCase("moving both notes to one new pitch keeps it",
                line -> {
                    move(line, ANCHOR, OTHER);
                    move(line, END, OTHER);
                },
                END),
            new EditCase("a same-pitch note inserted between them keeps it, ending on that note",
                line -> line.addElement(END, note(TIED)), END),
            new EditCase("a grace note inserted between them removes it",
                line -> line.addElement(END, grace(TIED)), null),
            new EditCase("a note at another pitch inserted between them removes it",
                line -> line.addElement(END, note(OTHER)), null),
            new EditCase("converting the anchor to a grace note leaves nothing tied",
                line -> line.setElement(ANCHOR,
                    StaffElement.convertedFrom(ElementType.GRACE_QUAVER, line.getElement(ANCHOR))),
                null)
        );
    }

    @Test
    void testOneUndoRestoresTheEditAndTheTieItRemoved() {
        var song = compactSong(Key.NO_ACCIDENTALS, TIED_PAIR);
        var line = song.getLine(FIRST_LINE);
        var anchor = (NoteElement) line.getElement(ANCHOR);
        var end = (NoteElement) line.getElement(END);
        var soundedBefore = end.soundingAccidental();
        var recorder = new BatchRecorder();

        try {
            song.withModification(() -> choose(line, END, Accidental.SHARP));

            assertThat(anchor.isTiedToNext()).as("the fixture reaches the case only if the edit removes the tie").isFalse();

            undo(song, recorder.batches().getFirst());

            assertThat(end.soundingAccidental()).isEqualTo(soundedBefore);
            assertThat(anchor.isTiedTo(end)).isTrue();
        } finally {
            recorder.dispose();
        }
    }

    /**
     * An edit on one line, and whether the tie from the first line's last note into the next
     * line survives it.
     *
     * @param description the edit, as the test's display name
     * @param lines       the song's lines in compact notation; the first line's last note is tied
     *                    to the second line's first
     * @param edit        makes the edit, inside a modification bracket
     * @param kept        whether the first line's last note is still tied afterwards
     */
    private record CrossLineCase(String description, String[] lines, Consumer<Song> edit, boolean kept) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("crossLineCases")
    void testAnEditOnTheNextLineIsJudgedForTheTieCrossingIntoIt(CrossLineCase testCase) {
        var song = compactSong(Key.NO_ACCIDENTALS, testCase.lines());
        var firstLine = song.getLine(FIRST_LINE);
        var anchor = (NoteElement) firstLine.getElement(firstLine.elementCount() - 1);

        // The compact grammar ties only within a line, so the tie across the break is added here.
        song.withoutMutationTracking(() -> firstLine.addTie(anchor));

        assertThat(anchor.isTiedTo((NoteElement) song.getLine(SECOND_LINE).getElement(ANCHOR)))
            .as("the fixture reaches the case only if the tie crosses into the second line")
            .isTrue();

        song.withModification(() -> testCase.edit().accept(song));

        assertThat(anchor.isTiedToNext()).isEqualTo(testCase.kept());
    }

    static Stream<CrossLineCase> crossLineCases() {
        return Stream.of(
            new CrossLineCase("moving the next line's first note to another pitch removes it",
                new String[] {"" + TIED, TIED + " " + OTHER},
                song -> move(song.getLine(SECOND_LINE), ANCHOR, OTHER), false),
            new CrossLineCase("deleting the line it ends in, bringing a note at another pitch, removes it",
                new String[] {"" + TIED, "" + TIED, OTHER + " " + TIED},
                song -> song.removeLine(SECOND_LINE), false),
            new CrossLineCase("deleting the line it ends in, bringing a note at its pitch, keeps it",
                new String[] {"" + TIED, "" + TIED, TIED + " " + OTHER},
                song -> song.removeLine(SECOND_LINE), true)
        );
    }

    @Test
    void testTheRuleTrustsATieUnderSuspendedTracking() {
        var song = compactSong(Key.NO_ACCIDENTALS, TIED_PAIR);
        var line = song.getLine(FIRST_LINE);
        var anchor = (NoteElement) line.getElement(ANCHOR);

        song.withoutMutationTracking(() -> move(line, END, OTHER));

        assertThat(anchor.isTiedToNext()).isTrue();
    }

    @Test
    void testTheRuleDoesNotRunWhileABatchIsReplayed() {
        var song = compactSong(Key.NO_ACCIDENTALS, TIED_PAIR);
        var line = song.getLine(FIRST_LINE);
        var anchor = (NoteElement) line.getElement(ANCHOR);
        var recorder = new BatchRecorder();

        try {
            song.withModification(() -> move(line, END, OTHER));
            var batch = recorder.batches().getFirst();

            assertThat(batch)
                .as("the fixture reaches the case only if the edit removes the tie")
                .anyMatch(TieRemoval.class::isInstance);

            undo(song, batch);
            redo(song, batch.stream().filter(ElementModification.class::isInstance).toList());

            assertThat(anchor.isTiedToNext())
                .as("the replay moves the note without the removal, and nothing removes the tie for it")
                .isTrue();
        } finally {
            recorder.dispose();
        }
    }

    @Test
    void testAnEditThatThrowsIsNotJudgedAndItsBatchIsStillDelivered() {
        var song = compactSong(Key.NO_ACCIDENTALS, TIED_PAIR);
        var line = song.getLine(FIRST_LINE);
        var anchor = (NoteElement) line.getElement(ANCHOR);
        var recorder = new BatchRecorder();

        try {
            assertThatThrownBy(() -> song.withModification(() -> {
                move(line, END, OTHER);
                throw new IllegalStateException("the edit stops part way");
            })).isInstanceOf(IllegalStateException.class);

            assertThat(anchor.isTiedToNext()).isTrue();
            assertThat(recorder.batches()).singleElement()
                .satisfies(batch -> assertThat(batch).noneMatch(TieRemoval.class::isInstance));
        } finally {
            recorder.dispose();
        }
    }

    private static void choose(Line line, int index, Accidental accidental) {
        line.modifyElement(index, ElementField.ACCIDENTAL, () -> ((NoteElement) line.getElement(index)).choose(accidental));
    }

    private static void move(Line line, int index, int staffPosition) {
        line.modifyElement(index, ElementField.PITCH,
            () -> ((NoteElement) line.getElement(index)).setStaffPosition(new StaffPosition(staffPosition)));
    }

    private static NoteElement grace(int staffPosition) {
        var grace = graceQuaver();
        grace.setStaffPosition(new StaffPosition(staffPosition));

        return grace;
    }
}
