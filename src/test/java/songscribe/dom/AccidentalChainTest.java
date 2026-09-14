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
import java.util.Set;
import java.util.stream.Stream;

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
 * {@link AccidentalChain#walkSong}'s and {@link AccidentalChain#walk}'s promise: the policy
 * stated on {@link AccidentalChain} decides every note's chain state, and a single edit's walk
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

    // -------------------------------------------------------------------
    // walkSong: whole songs
    // -------------------------------------------------------------------

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
            F + "f " + F + " K:ONE_SHARP " + F);

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

    // -------------------------------------------------------------------
    // walk: a single edit, against the worked example
    // -------------------------------------------------------------------

    @Test
    void testPressingNaturalOnTheFifthNoteMakesItACancelAndTheSixthAnAnchor() {
        var song = workedExampleCase().song();
        var line = song.getLine(0);
        var fifth = (NoteElement) line.getElement(4);
        var sixth = (NoteElement) line.getElement(5);
        var originalSixth = sixth.chainedAccidental();

        fifth.setAccidentalState(new AccidentalState.Chosen(Accidental.NATURAL));
        var restamps = AccidentalChain.walk(new AccidentalScanView.Live(song), new ChainEdit(0, 4, 5, Set.of(), false));

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

        second.setAccidentalState(new AccidentalState.Chosen(Accidental.NATURAL));
        var restamps = AccidentalChain.walk(new AccidentalScanView.Live(song), new ChainEdit(0, 1, 2, Set.of(), false));

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

        seventh.setAccidentalState(new AccidentalState.Chosen(Accidental.FLAT));
        var restamps = AccidentalChain.walk(new AccidentalScanView.Live(song), new ChainEdit(0, 6, 7, Set.of(), false));

        assertRestamps(restamps, tuple(seventh, Accidental.FLAT, ChainType.IMPLICIT), tuple(ninth, Accidental.NATURAL, ChainType.CANCEL));
        assertThat(eighth.chainedAccidental())
            .as("the eighth note sits between the two changed notes but its own decision does not change")
            .isEqualTo(originalEighth);
    }

    @Test
    void testDeletingTheAnchorLeavesTheSurvivingCancelInKey() {
        var song = compactSong(Key.NO_ACCIDENTALS, F + "f " + OTHER + " " + F + "n");
        var line = song.getLine(0);
        var flat = (NoteElement) line.getElement(0);
        var natural = (NoteElement) line.getElement(2);

        song.withoutMutationTracking(() -> line.removeElement(0));
        var edit = ChainEdit.removed(0, 0, List.of(flat));
        var restamps = AccidentalChain.walk(new AccidentalScanView.Live(song), edit);

        assertRestamps(restamps, tuple(natural, Accidental.NATURAL, ChainType.IN_KEY));
    }

    @Test
    void testDeletingTheInterveningNoteCollapsesTheRepetitionToImplicit() {
        var song = compactSong(Key.NO_ACCIDENTALS, F + "f " + OTHER + " " + F);
        var line = song.getLine(0);
        var intervening = (NoteElement) line.getElement(1);
        var trailing = (NoteElement) line.getElement(2);

        song.withoutMutationTracking(() -> line.removeElement(1));
        var edit = ChainEdit.removed(0, 1, List.of(intervening));
        var restamps = AccidentalChain.walk(new AccidentalScanView.Live(song), edit);

        assertRestamps(restamps, tuple(trailing, Accidental.FLAT, ChainType.IMPLICIT));
    }

    @Test
    void testDeletingANoteReachesPastInterveningOtherPositionNotesToRedecideItsVacatedPosition() {
        var song = compactSong(Key.NO_ACCIDENTALS, F + "f " + OTHER + " " + OTHER + " " + OTHER + " " + F);
        var line = song.getLine(0);
        var leadingFlat = (NoteElement) line.getElement(0);
        var farNote = (NoteElement) line.getElement(4);

        song.withoutMutationTracking(() -> line.removeElement(0));
        var edit = ChainEdit.removed(0, 0, List.of(leadingFlat));
        var restamps = AccidentalChain.walk(new AccidentalScanView.Live(song), edit);

        assertRestamps(restamps, tuple(farNote, Accidental.FLAT, ChainType.ANCHOR));
    }

    @Test
    void testAnAdoptingNoteMovedToAnotherPositionTakesWhatIsInForceThere() {
        var song = compactSong(Key.NO_ACCIDENTALS, F + "f " + OTHER + "f " + F);
        var line = song.getLine(0);
        var moved = (NoteElement) line.getElement(1);
        var trailing = (NoteElement) line.getElement(2);
        var before = moved.clone();

        moved.setStaffPosition(new StaffPosition(F));
        moved.setAccidentalState(new AccidentalState.Adopting());
        var edit = ChainEdit.modified(0, 1, before, moved);
        var restamps = AccidentalChain.walk(new AccidentalScanView.Live(song), edit);

        assertRestamps(restamps, tuple(moved, Accidental.FLAT, ChainType.IMPLICIT), tuple(trailing, Accidental.FLAT, ChainType.IMPLICIT));
    }

    @Test
    void testInsertingAKeyChangeWalksOnToTheNextKeyChangeRestatingEveryNoteItAlters() {
        var song = compactSong(Key.NO_ACCIDENTALS, B + "f " + OTHER + " " + B + "n " + OTHER + " " + B + "n");
        var line = song.getLine(0);
        var leadingFlat = (NoteElement) line.getElement(0);
        var firstNatural = (NoteElement) line.getElement(2);
        var trailingNatural = (NoteElement) line.getElement(4);
        var firstOther = (NoteElement) line.getElement(1);
        var originalFirstOther = firstOther.chainedAccidental();

        var keyChange = StaffElementFactory.keyChange(Key.ONE_FLAT);
        song.withoutMutationTracking(() -> line.addElement(0, keyChange));
        var edit = ChainEdit.inserted(0, 0, List.of(keyChange));
        var restamps = AccidentalChain.walk(new AccidentalScanView.Live(song), edit);

        assertRestamps(restamps,
            tuple(leadingFlat, Accidental.FLAT, ChainType.IN_KEY),
            tuple(firstNatural, Accidental.NATURAL, ChainType.ANCHOR),
            tuple(trailingNatural, Accidental.NATURAL, ChainType.REPETITION));
        assertThat(firstOther.chainedAccidental())
            .as("a position the new key does not alter is undisturbed")
            .isEqualTo(originalFirstOther);
    }

    private static void assertRestamps(
        List<Restamp> restamps, org.assertj.core.groups.Tuple... expected) {

        assertThat(restamps)
            .extracting(Restamp::note, restamp -> restamp.state().accidental(), restamp -> restamp.state().type())
            .containsExactly(expected);
    }
}
