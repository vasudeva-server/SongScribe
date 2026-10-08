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
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;
import songscribe.message.mutation.ElementField;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.SongFactory.compactSong;

/**
 * {@link Song#applyChange}'s promise that every recorded edit leaves the song exactly as a
 * fresh {@link AccidentalChain#walkSong} would decide it, and that undoing an edit — by
 * recording its structural inverse — restores every note's prior state.
 *
 * <p>Each case records one edit through the same public route the UI edits through (an
 * {@code addElement}, {@code removeElement}, {@code setElement}, {@code modifyElement}, {@code setKey},
 * {@code addLine} or {@code removeLine} inside a modification bracket), which is what makes the
 * song run the chain walk in the first place; there is no separate call to trigger it.
 */
class SongAccidentalWalkTest extends UnitTest {

    private static final int F = 3;
    private static final int OTHER = 4;

    /** One note's decided state, independent of the note object's identity. */
    private record NoteSnapshot(int staffPosition, ChainedAccidental chained) {}

    private static List<NoteSnapshot> snapshot(Song song) {
        return song.getLines().stream()
            .flatMap(line -> line.getElements().stream())
            .filter(NoteElement.class::isInstance)
            .map(NoteElement.class::cast)
            .map(note -> new NoteSnapshot(note.getStaffPosition().value(), note.chainedAccidental()))
            .toList();
    }

    /**
     * Records {@code edit}, asserts the song already matches a fresh walk, then records
     * {@code undo} and asserts every note is back where {@code before} found it.
     */
    private static void assertEditWalksItselfAndUndoRestores(Song song, Runnable edit, Runnable undo) {
        var before = snapshot(song);

        song.withModification(edit);
        assertThat(AccidentalChain.walkSong(song))
            .as("the recorded edit already leaves every note as a fresh walk would decide it")
            .isEmpty();

        song.withModification(undo);
        assertThat(snapshot(song)).as("undoing the edit restores every note's prior state").isEqualTo(before);
    }

    /** A song, the edit under test and its inverse, built together so the edit can close over whatever the song needs it to. */
    private record Fixture(Song song, Runnable edit, Runnable undo) {}

    private record WalkCase(String description, Supplier<Fixture> arrange) {
        @Override
        public String toString() {
            return description;
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("walkCases")
    void testEditWalksItselfAndUndoRestores(WalkCase testCase) {
        var fixture = testCase.arrange().get();

        assertEditWalksItselfAndUndoRestores(fixture.song(), fixture.edit(), fixture.undo());
    }

    static Stream<WalkCase> walkCases() {
        return Stream.of(
            new WalkCase("a note insertion", SongAccidentalWalkTest::noteInsertionCase),
            new WalkCase("a range deletion", SongAccidentalWalkTest::rangeDeletionCase),
            new WalkCase("a note adopting the chain's accidental", SongAccidentalWalkTest::accidentalAdoptionCase),
            new WalkCase("a note moved to another staff position", SongAccidentalWalkTest::noteMoveCase),
            new WalkCase("a key change insertion", SongAccidentalWalkTest::keyChangeInsertionCase),
            new WalkCase("a line deletion", SongAccidentalWalkTest::lineDeletionCase),
            new WalkCase("a line's own key changed", SongAccidentalWalkTest::lineKeyChangeCase),
            new WalkCase("a line with its own key removed", SongAccidentalWalkTest::lineRemovalWithOwnKeyCase),
            new WalkCase("a line inserted with its own key", SongAccidentalWalkTest::lineInsertionWithOwnKeyCase),
            new WalkCase("a note replaced by a key change", SongAccidentalWalkTest::elementReplacementCase),
            new WalkCase("a key change's key altered in place", SongAccidentalWalkTest::keyChangeAlteredCase),
            new WalkCase("a line holding a key change removed", SongAccidentalWalkTest::lineRemovalWithKeyChangeCase),
            new WalkCase("a key change deleted on its own", SongAccidentalWalkTest::keyChangeDeletionCase),
            new WalkCase("an empty line inserted with no key of its own", SongAccidentalWalkTest::emptyLineInsertionCase));
    }

    private static Fixture noteInsertionCase() {
        var song = compactSong(Key.NO_ACCIDENTALS, F + "f " + F);
        var line = song.getLine(0);
        var inserted = StaffElementFactory.note(OTHER);

        return new Fixture(song,
            () -> line.addElement(1, inserted),
            () -> line.removeElement(line.getElementIndex(inserted)));
    }

    private static Fixture rangeDeletionCase() {
        var song = compactSong(Key.NO_ACCIDENTALS, F + "f " + F + " " + F);
        var line = song.getLine(0);
        var removed = (NoteElement) line.getElement(1);

        return new Fixture(song,
            () -> line.removeElement(1),
            () -> line.addElement(1, removed));
    }

    private static Fixture accidentalAdoptionCase() {
        var song = compactSong(Key.NO_ACCIDENTALS, F + "f " + F);
        var line = song.getLine(0);
        var note = (NoteElement) line.getElement(1);
        var beforeState = note.clone();

        return new Fixture(song,
            () -> line.modifyElement(1, ElementField.ACCIDENTAL, note::adopt),
            () -> line.modifyElement(1, ElementField.ACCIDENTAL, () -> note.copyStateFrom(beforeState)));
    }

    /** The note's accidental is the chain's, so moving it off the chained pitch changes what it sounds. */
    private static Fixture noteMoveCase() {
        var song = compactSong(Key.NO_ACCIDENTALS, F + "f " + F);
        var line = song.getLine(0);

        return new Fixture(song,
            () -> LineEdits.move(line, 1, OTHER),
            () -> LineEdits.move(line, 1, F));
    }

    private static Fixture keyChangeInsertionCase() {
        var song = compactSong(Key.NO_ACCIDENTALS, F + "f | " + F);
        var line = song.getLine(0);
        var keyChange = StaffElementFactory.keyChange(Key.ONE_SHARP);

        return new Fixture(song,
            () -> line.addElement(2, keyChange),
            () -> line.removeElement(line.getElementIndex(keyChange)));
    }

    /** The edit removes only the key change, so it routes through {@link ChainEdit#removed}'s rekey clause. */
    private static Fixture keyChangeDeletionCase() {
        var song = compactSong(Key.NO_ACCIDENTALS, F + "f | K:ONE_SHARP " + OTHER + " " + F);
        var line = song.getLine(0);
        var keyChange = line.getElement(2);

        return new Fixture(song,
            () -> line.removeElement(2),
            () -> line.addElement(2, keyChange));
    }

    /** The inserted line is empty and has no key, so it inherits the key running into it and moves none. */
    private static Fixture emptyLineInsertionCase() {
        var song = compactSong(Key.NO_ACCIDENTALS, F + "f " + F, "" + F);
        var insertedLine = new Line(song);

        return new Fixture(song,
            () -> song.addLine(1, insertedLine),
            () -> song.removeLine(1));
    }

    private static Fixture lineDeletionCase() {
        var song = compactSong(Key.NO_ACCIDENTALS, F + "f " + F, "" + F);
        var removedLine = song.getLine(1);

        return new Fixture(song,
            () -> song.removeLine(1),
            () -> song.addLine(1, removedLine));
    }

    /** The edit is a {@link Line#setKey} on an already-loaded line, which routes through {@link ChainEdit#lineRekeyed}. */
    private static Fixture lineKeyChangeCase() {
        var song = compactSong(Key.NO_ACCIDENTALS, F + "f " + F, "" + F);
        var secondLine = song.getLine(1);

        return new Fixture(song,
            () -> secondLine.setKey(Key.ONE_SHARP),
            () -> secondLine.setKey(null));
    }

    /** The removed line carries its own key, so {@link ChainEdit#of} maps the edit to a removal that reaches the next key change. */
    private static Fixture lineRemovalWithOwnKeyCase() {
        var song = SongFactory.buildSong(
            line -> SongFactory.addCompactElements(line, F + "f " + F),
            line -> {
                line.setKey(Key.ONE_SHARP);
                SongFactory.addCompactElements(line, "" + F);
            });
        var removedLine = song.getLine(1);

        return new Fixture(song,
            () -> song.removeLine(1),
            () -> song.addLine(1, removedLine));
    }

    /** The inserted line carries its own key, so {@link ChainEdit#of} maps the edit to an insertion that reaches the next key change. */
    private static Fixture lineInsertionWithOwnKeyCase() {
        var song = compactSong(Key.NO_ACCIDENTALS, F + "f " + F);
        var insertedLine = new Line(song);

        song.withoutMutationTracking(() -> {
            insertedLine.setKey(Key.ONE_SHARP);
            SongFactory.addCompactElements(insertedLine, "" + F);
        });

        return new Fixture(song,
            () -> song.addLine(1, insertedLine),
            () -> song.removeLine(1));
    }

    /** The edit replaces a note with a key change, so it routes through {@link ChainEdit#replaced}. */
    private static Fixture elementReplacementCase() {
        var song = compactSong(Key.NO_ACCIDENTALS, F + "f | " + OTHER + " " + F);
        var line = song.getLine(0);
        var replaced = (NoteElement) line.getElement(2);
        var keyChange = StaffElementFactory.keyChange(Key.ONE_SHARP);

        return new Fixture(song,
            () -> line.setElement(2, keyChange),
            () -> line.setElement(2, replaced));
    }

    /**
     * The edit changes a key change's key through {@link Line#modifyElement}, so it routes through
     * {@link ChainEdit#modified}'s rekey clause; the note past the span is decided only because
     * the edit reaches the next key change.
     */
    private static Fixture keyChangeAlteredCase() {
        var song = compactSong(Key.NO_ACCIDENTALS, F + "f | K:ONE_SHARP " + OTHER + " " + F);
        var line = song.getLine(0);
        var keyChange = (KeyChangeElement) line.getElement(2);

        return new Fixture(song,
            () -> line.modifyElement(2, ElementField.KEY, () -> keyChange.setKey(Key.ONE_FLAT)),
            () -> line.modifyElement(2, ElementField.KEY, () -> keyChange.setKey(Key.ONE_SHARP)));
    }

    /**
     * The removed line holds a mid-line key change but no key of its own, so
     * {@link ChainEdit#of} maps the edit to a removal that reaches the next key change; the line
     * behind it inherits a new
     * key, and its note past the span is decided only because the edit reaches the next key
     * change.
     */
    private static Fixture lineRemovalWithKeyChangeCase() {
        var song = compactSong(Key.NO_ACCIDENTALS, F + "f", OTHER + " | K:ONE_SHARP " + OTHER, OTHER + " " + F);
        var removedLine = song.getLine(1);

        return new Fixture(song,
            () -> song.removeLine(1),
            () -> song.addLine(1, removedLine));
    }
}
