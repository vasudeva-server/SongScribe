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
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;
import songscribe.dom.AccidentalChain.Restamp;
import songscribe.dom.ChainedAccidental.ChainType;
import songscribe.engraving.StaffPosition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static songscribe.dom.SongFactory.compactSong;

/**
 * {@link AccidentalChain#walkSong}'s and {@link AccidentalChain#walk}'s promise: the rule
 * stated on {@link ChainedAccidental} decides every note's chain state, and a single edit's walk
 * touches exactly the notes the policy says it must and none past its stopping point.
 *
 * <p>Every case names the staff position of the notes it follows as a bare integer — the walk
 * reads only staff positions and pitch classes, never letter names — chosen so its pitch class
 * lands on {@link PitchClass#F} unless a case says otherwise, to match the worked example on
 * {@link AccidentalChain}.
 */
class AccidentalChainTest extends UnitTest {

    /** A staff position at the pitch class {@link PitchClass#F}: the worked example's chain lives here. */
    private static final int F = 3;

    /** A staff position at the pitch class {@link PitchClass#E}, that intervenes without altering it under any key used here. */
    private static final int OTHER = 4;

    /** A staff position at the pitch class {@link PitchClass#B}, which {@link Key#ONE_FLAT} alters. */
    private static final int B = 0;

    private record SongCase(String description, Song song, List<ChainType> expectedTypes) {
        @Override
        public String toString() {
            return description;
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("songCases")
    void testWalkSongStampsEveryNoteAsThePolicySays(SongCase testCase) {
        assertThat(typesOf(testCase.song())).containsExactlyElementsOf(testCase.expectedTypes());
    }

    static Stream<SongCase> songCases() {
        return Stream.of(
            workedExampleCase(),
            barlineRepeatAndLineStartInterveneCase(),
            restsAndBreathMarksAreTransparentCase(),
            tiedNotesCase(),
            graceNoteInterveningCase(),
            keyChangeEndsInForceCase(),
            ownLineKeyEndsInForceCase(),
            chainCrossesLineBreakCase());
    }

    /** {@code F Fb F G Fb F Fn E F} in C major — the worked example on {@link AccidentalChain}. */
    private static SongCase workedExampleCase() {
        var song = compactSong(Key.NO_ACCIDENTALS, F + " " + F + "f " + F + " " + OTHER + " " + F + " " + F + " " + F + "n " + OTHER + " " + F);

        return new SongCase("the worked example", song, List.of(
            ChainType.IN_KEY, ChainType.ANCHOR, ChainType.IMPLICIT, ChainType.IN_KEY, ChainType.REPETITION,
            ChainType.IMPLICIT, ChainType.CANCEL, ChainType.IN_KEY, ChainType.IN_KEY));
    }

    /** A barline, a repeat and the first element of a line each intervene the same way a note at another position does. */
    private static SongCase barlineRepeatAndLineStartInterveneCase() {
        var song = compactSong(Key.NO_ACCIDENTALS,
            F + "f | " + F + " :| " + F,
            "" + F);

        return new SongCase("a barline, a repeat and the first element of a line intervening", song,
            List.of(ChainType.ANCHOR, ChainType.REPETITION, ChainType.REPETITION, ChainType.REPETITION));
    }

    /** Rests and breath marks are skipped when deciding what intervenes. */
    private static SongCase restsAndBreathMarksAreTransparentCase() {
        var song = compactSong(Key.NO_ACCIDENTALS, F + "f R " + F + " B " + F);

        return new SongCase("rests and breath marks skipped", song,
            List.of(ChainType.ANCHOR, ChainType.IMPLICIT, ChainType.IMPLICIT));
    }

    /** A tie changes nothing: the note right after its partner still reads by position alone, tied across a barline or not. */
    private static SongCase tiedNotesCase() {
        var song = compactSong(Key.NO_ACCIDENTALS, F + "f ~ " + F + " | ~ " + F);

        return new SongCase("a tied note directly after its partner and one after a barline", song,
            List.of(ChainType.ANCHOR, ChainType.IMPLICIT, ChainType.REPETITION));
    }

    /** A grace note at another position intervenes exactly as an ordinary note there would. */
    private static SongCase graceNoteInterveningCase() {
        var song = compactSong(Key.NO_ACCIDENTALS, F + "f " + F + " g" + OTHER + " " + F);

        return new SongCase("a grace note at another position intervening", song,
            List.of(ChainType.ANCHOR, ChainType.IMPLICIT, ChainType.IN_KEY, ChainType.REPETITION));
    }

    /** A key change ends what was in force at every position, even one the new key does not alter. */
    private static SongCase keyChangeEndsInForceCase() {
        var song = compactSong(Key.NO_ACCIDENTALS,
            F + "f " + F + " | K:ONE_SHARP " + F);

        return new SongCase("a key change ending what is in force", song,
            List.of(ChainType.ANCHOR, ChainType.IMPLICIT, ChainType.IN_KEY));
    }

    /** A line with its own key ends what was in force at the line before it, exactly as a mid-line key change does. */
    private static SongCase ownLineKeyEndsInForceCase() {
        var song = SongFactory.buildSong(
            line -> {
                line.setKey(Key.NO_ACCIDENTALS);
                SongFactory.addCompactElements(line, F + "f " + F);
            },
            line -> {
                line.setKey(Key.ONE_SHARP);
                SongFactory.addCompactElements(line, "" + F);
            });

        return new SongCase("a line with its own key ending what is in force", song,
            List.of(ChainType.ANCHOR, ChainType.IMPLICIT, ChainType.IN_KEY));
    }

    /** A chain crosses a line break: nothing about the break resets what is in force. */
    private static SongCase chainCrossesLineBreakCase() {
        var song = compactSong(Key.NO_ACCIDENTALS, F + "f " + F, "" + F);

        return new SongCase("a chain crossing a line break", song,
            List.of(ChainType.ANCHOR, ChainType.IMPLICIT, ChainType.REPETITION));
    }

    /** Every note's decided type, in song order across all lines. */
    private static List<ChainType> typesOf(Song song) {
        return song.getLines().stream()
            .flatMap(line -> line.getElements().stream())
            .filter(NoteElement.class::isInstance)
            .map(NoteElement.class::cast)
            .map(note -> note.chainedAccidental().type())
            .toList();
    }

    @Test
    void testPressingNaturalOnTheFifthNoteMakesItACancelAndTheSixthAnAnchor() {
        var song = workedExampleCase().song();
        var line = song.getLine(0);
        var fifth = (NoteElement) line.getElement(4);
        var sixth = (NoteElement) line.getElement(5);
        var originalSixth = sixth.chainedAccidental();

        var restamps = AccidentalChain.walk(song, choose(song, new Cursor(0, 4), Accidental.NATURAL));

        assertRestamps(restamps, tuple(fifth, Accidental.NATURAL, ChainType.CANCEL), tuple(sixth, Accidental.FLAT, ChainType.ANCHOR));
        assertThat(sixth.chainedAccidental()).isEqualTo(originalSixth);

        var ninth = (NoteElement) line.getElement(8);
        assertThat(restamps).as("the walk stops once the span closes, so it never reaches the ninth note")
            .noneMatch(restamp -> restamp.note() == ninth);
    }

    @Test
    void testDeselectingTheSecondNotesFlatMakesItInKeyAndTheThirdAnAnchor() {
        var song = workedExampleCase().song();
        var line = song.getLine(0);
        var second = (NoteElement) line.getElement(1);
        var third = (NoteElement) line.getElement(2);

        var restamps = AccidentalChain.walk(song, choose(song, new Cursor(0, 1), Accidental.NATURAL));

        assertRestamps(restamps, tuple(second, Accidental.NATURAL, ChainType.IN_KEY), tuple(third, Accidental.FLAT, ChainType.ANCHOR));
    }

    @Test
    void testPressingFlatOnTheSeventhNoteMakesItImplicitAndTheNinthACancel() {
        var song = workedExampleCase().song();
        var line = song.getLine(0);
        var seventh = (NoteElement) line.getElement(6);
        var eighth = (NoteElement) line.getElement(7);
        var ninth = (NoteElement) line.getElement(8);
        var originalEighth = eighth.chainedAccidental();

        var restamps = AccidentalChain.walk(song, choose(song, new Cursor(0, 6), Accidental.FLAT));

        assertRestamps(restamps, tuple(seventh, Accidental.FLAT, ChainType.IMPLICIT), tuple(ninth, Accidental.NATURAL, ChainType.CANCEL));
        assertThat(eighth.chainedAccidental())
            .as("the eighth note sits between the two changed notes but its own decision does not change")
            .isEqualTo(originalEighth);
    }

    /** A restamp a case expects, its note named by where it stands once the edit has landed. */
    private record ExpectedRestamp(Cursor at, Accidental accidental, ChainType type) {}

    private record RemovalCase(String description, String notation, int removedIndex, List<ExpectedRestamp> expected) {
        @Override
        public String toString() {
            return description;
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("removalCases")
    void testRemovingANoteRedecidesTheNotesTheEditReaches(RemovalCase testCase) {
        var song = compactSong(Key.NO_ACCIDENTALS, testCase.notation());
        var line = song.getLine(0);
        var removed = line.getElement(testCase.removedIndex());

        song.withoutMutationTracking(() -> line.removeElement(testCase.removedIndex()));
        var edit = ChainEdit.removed(new Cursor(0, testCase.removedIndex()), List.of(removed));

        assertRestamps(AccidentalChain.walk(song, edit), expectedTuples(song, testCase.expected()));
    }

    static Stream<RemovalCase> removalCases() {
        return Stream.of(
            new RemovalCase("deleting the anchor leaves the surviving cancel in key",
                F + "f " + OTHER + " " + F + "n", 0,
                List.of(new ExpectedRestamp(new Cursor(0, 1), Accidental.NATURAL, ChainType.IN_KEY))),
            new RemovalCase("deleting the intervening note collapses the repetition to implicit",
                F + "f " + OTHER + " " + F, 1,
                List.of(new ExpectedRestamp(new Cursor(0, 1), Accidental.FLAT, ChainType.IMPLICIT))),
            new RemovalCase("deleting a note reaches past intervening notes at another position to redecide its vacated position",
                F + "f " + OTHER + " " + OTHER + " " + OTHER + " " + F, 0,
                List.of(new ExpectedRestamp(new Cursor(0, 3), Accidental.FLAT, ChainType.ANCHOR))));
    }

    /** A song, and an edit that lands in it untracked and answers the {@link ChainEdit} describing itself. */
    private record EditCase(
        String description, Supplier<Song> song, Function<Song, ChainEdit> edit, List<ExpectedRestamp> expected) {

        @Override
        public String toString() {
            return description;
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("editCases")
    void testAnEditRedecidesTheNotesItReaches(EditCase testCase) {
        var song = testCase.song().get();
        var edit = testCase.edit().apply(song);

        assertRestamps(AccidentalChain.walk(song, edit), expectedTuples(song, testCase.expected()));
    }

    static Stream<EditCase> editCases() {
        return Stream.of(
            new EditCase("a key change inside the edited range puts its key in force, and the walk goes on to the next key change",
                () -> compactSong(Key.NO_ACCIDENTALS, F + "f " + OTHER + " " + F),
                song -> insert(song, new Cursor(0, 1),
                    StaffElementFactory.note(OTHER), StaffElementFactory.singleBarline(),
                    StaffElementFactory.keyChange(Key.ONE_SHARP), StaffElementFactory.note(F, Accidental.NATURAL)),
                List.of(
                    new ExpectedRestamp(new Cursor(0, 1), Accidental.NATURAL, ChainType.IN_KEY),
                    new ExpectedRestamp(new Cursor(0, 4), Accidental.NATURAL, ChainType.ANCHOR),
                    new ExpectedRestamp(new Cursor(0, 6), Accidental.FLAT, ChainType.ANCHOR))),
            new EditCase("what is in force arriving at the edit stops at a key change before it",
                () -> compactSong(Key.NO_ACCIDENTALS, F + "f | K:ONE_SHARP " + OTHER + " " + F),
                song -> choose(song, new Cursor(0, 4), Accidental.FLAT),
                List.of(new ExpectedRestamp(new Cursor(0, 4), Accidental.FLAT, ChainType.ANCHOR))),
            new EditCase("what is in force arriving at the edit stops at the start of a line with its own key",
                () -> SongFactory.buildSong(
                    line -> {
                        line.setKey(Key.NO_ACCIDENTALS);
                        SongFactory.addCompactElements(line, F + "f");
                    },
                    line -> {
                        line.setKey(Key.ONE_SHARP);
                        SongFactory.addCompactElements(line, OTHER + " " + F);
                    }),
                song -> choose(song, new Cursor(1, 1), Accidental.FLAT),
                List.of(new ExpectedRestamp(new Cursor(1, 1), Accidental.FLAT, ChainType.ANCHOR))),
            new EditCase("the element before the edit skips rests and breath marks",
                () -> compactSong(Key.NO_ACCIDENTALS, F + "f " + OTHER + " R " + F + "s"),
                song -> choose(song, new Cursor(0, 3), Accidental.FLAT),
                List.of(new ExpectedRestamp(new Cursor(0, 3), Accidental.FLAT, ChainType.REPETITION))),
            new EditCase("the span's last element skips rests and breath marks",
                () -> compactSong(Key.NO_ACCIDENTALS, F + "f " + F + " R " + F),
                song -> insert(song, new Cursor(0, 2), StaffElementFactory.singleBarline()),
                List.of(new ExpectedRestamp(new Cursor(0, 4), Accidental.FLAT, ChainType.REPETITION))));
    }

    @Test
    void testAnAdoptingNoteMovedToAnotherPositionTakesWhatIsInForceThere() {
        var song = compactSong(Key.NO_ACCIDENTALS, F + "f " + OTHER + "f " + F);
        var line = song.getLine(0);
        var moved = (NoteElement) line.getElement(1);
        var trailing = (NoteElement) line.getElement(2);
        var before = moved.clone();

        moved.setStaffPosition(new StaffPosition(F));
        moved.adopt();
        var edit = ChainEdit.modified(new Cursor(0, 1), before, moved);
        var restamps = AccidentalChain.walk(song, edit);

        assertRestamps(restamps, tuple(moved, Accidental.FLAT, ChainType.IMPLICIT), tuple(trailing, Accidental.FLAT, ChainType.IMPLICIT));
    }

    @Test
    void testInsertingAKeyChangeWalksOnToTheNextKeyChangeRestatingEveryNoteItAlters() {
        var song = compactSong(Key.NO_ACCIDENTALS, "| " + B + "f " + OTHER + " " + B + "n " + OTHER + " " + B + "n");
        var line = song.getLine(0);
        var leadingFlat = (NoteElement) line.getElement(1);
        var firstNatural = (NoteElement) line.getElement(3);
        var trailingNatural = (NoteElement) line.getElement(5);
        var firstOther = (NoteElement) line.getElement(2);
        var originalFirstOther = firstOther.chainedAccidental();

        var restamps = AccidentalChain.walk(song, insert(song, new Cursor(0, 1), StaffElementFactory.keyChange(Key.ONE_FLAT)));

        assertRestamps(restamps,
            tuple(leadingFlat, Accidental.FLAT, ChainType.IN_KEY),
            tuple(firstNatural, Accidental.NATURAL, ChainType.ANCHOR),
            tuple(trailingNatural, Accidental.NATURAL, ChainType.REPETITION));
        assertThat(firstOther.chainedAccidental())
            .as("a position the new key does not alter is undisturbed")
            .isEqualTo(originalFirstOther);
    }

    /** Chooses {@code accidental} for the note at {@code at}, untracked, and answers the edit that describes it. */
    private static ChainEdit choose(Song song, Cursor at, Accidental accidental) {
        var note = (NoteElement) song.getLine(at.lineIndex()).getElement(at.index());
        var before = note.clone();

        note.choose(accidental);
        return ChainEdit.modified(at, before, note);
    }

    /** Inserts {@code elements} at {@code at}, untracked, and answers the edit that describes it. */
    private static ChainEdit insert(Song song, Cursor at, StaffElement... elements) {
        var line = song.getLine(at.lineIndex());

        song.withoutMutationTracking(() -> {
            for (var i = 0; i < elements.length; i++) {
                line.addElement(at.index() + i, elements[i]);
            }
        });

        return ChainEdit.inserted(at, List.of(elements));
    }

    /** Each expected restamp as the tuple {@link #assertRestamps} compares, its note read from {@code song} as it stands. */
    private static Tuple[] expectedTuples(Song song, List<ExpectedRestamp> expected) {
        return expected.stream()
            .map(restamp -> tuple(
                song.getLine(restamp.at().lineIndex()).getElement(restamp.at().index()),
                restamp.accidental(), restamp.type()))
            .toArray(Tuple[]::new);
    }

    private static void assertRestamps(
        List<Restamp> restamps, Tuple... expected) {

        assertThat(restamps)
            .extracting(Restamp::note, restamp -> restamp.state().sounding(), restamp -> restamp.state().type())
            .containsExactly(expected);
    }
}
