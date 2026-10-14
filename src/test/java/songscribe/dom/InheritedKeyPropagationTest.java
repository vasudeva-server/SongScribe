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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;
import songscribe.dom.SongFactory.LineBuilder;
import songscribe.message.mutation.ElementField;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.SongFactory.buildSong;
import static songscribe.dom.SongFactory.notesAroundKeyChange;
import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.dom.StaffElementFactory.keyChange;
import static songscribe.dom.StaffElementFactory.singleBarline;

/**
 * What an edit that moves a key does to the keys the lines behind it run in: a line's own key
 * changing, a mid-line key change inserted, removed, replaced or edited in place, and a line
 * arriving at or leaving an index.
 *
 * <p>A line arriving or leaving moves no key of its own, and that is what makes it the dangerous
 * one: it changes which line each later line <em>follows</em>, so a line the edit never named ends
 * up running in a key nothing put it in. Nothing reports the miss — the line simply draws a
 * different header signature and re-spells every note under it — so what each case asserts is the
 * key <em>every</em> line runs in, not the one line the edit was about.
 *
 * <p>The forward walk that re-derives those keys stops at the first line establishing a key of its
 * own, which is sound only while that line keeps the same predecessor. An arriving line is the one
 * thing that breaks it, so the cases cover a line arriving both keyed and inheriting, arriving at
 * index 0, and leaving.
 */
class InheritedKeyPropagationTest extends UnitTest {

    /** The key line 0 establishes, and so the key the song is in until something moves it. */
    private static final Key DOCUMENT_KEY = Key.NO_ACCIDENTALS;

    /** The key the line under test establishes for itself, in the cases that give it one. */
    private static final Key OWN_KEY = Key.TWO_SHARPS;

    /** The key the line under test leaves off in, by way of a key change partway along it. */
    private static final Key MID_LINE_KEY = Key.THREE_FLATS;

    /** The key a line standing behind the one under test establishes for itself. */
    private static final Key FOLLOWER_KEY = Key.ONE_SHARP;

    private static final int FIRST_LINE = 0;
    private static final int SECOND_LINE = 1;
    private static final int THIRD_LINE = 2;

    /** Where {@link SongFactory#notesAroundKeyChange} puts its key change. */
    private static final int KEY_CHANGE_INDEX = 2;

    /**
     * One edit that moves a key, and the key every line is left running in.
     *
     * @param description  the case, which doubles as the parameterized display name
     * @param song         the song to edit, built fresh for the case
     * @param edit         the edit to make
     * @param expectedKeys the key each line runs in afterwards, in song order
     */
    private record PropagationCase(
        String description,
        Supplier<Song> song,
        Consumer<Song> edit,
        List<Key> expectedKeys) {

        @Override
        public String toString() {
            return description;
        }
    }

    /**
     * A song whose second line leaves off in {@link #MID_LINE_KEY}, ahead of {@code followers}.
     *
     * @param ownKey    the key the second line establishes for itself, or null to leave it
     *                  inheriting
     * @param followers the lines standing behind it, in song order
     */
    private static Song songWithKeyMoveOnTheSecondLine(@Nullable Key ownKey,
        LineBuilder... followers) {
        var builders = new ArrayList<LineBuilder>();

        builders.add(line -> {
            line.setKey(DOCUMENT_KEY);
            line.addElement(crotchet());
        });

        builders.add(line -> {
            if (ownKey != null) {
                line.setKey(ownKey);
            }

            notesAroundKeyChange(line, MID_LINE_KEY);
        });

        builders.addAll(List.of(followers));

        return buildSong(builders.toArray(new LineBuilder[0]));
    }

    /** A line that establishes no key of its own and holds a single note. */
    private static LineBuilder inheritingLine() {
        return line -> line.addElement(crotchet());
    }

    /** A line that establishes {@link #FOLLOWER_KEY} and holds a single note. */
    private static LineBuilder keyedLine() {
        return line -> {
            line.setKey(FOLLOWER_KEY);
            line.addElement(crotchet());
        };
    }

    /** Takes the line at {@code from} out of the song and puts it back in at {@code to}. */
    private static Consumer<Song> moveLine(int from, int to) {
        return song -> {
            var line = song.getLine(from);

            song.removeLine(from);
            song.addLine(to, line);
        };
    }

    private static List<Key> runningKeysOf(Song song) {
        var keys = new ArrayList<Key>();

        for (var lineIndex = 0; lineIndex < song.lineCount(); lineIndex++) {
            keys.add(song.getLine(lineIndex).getRunningKey());
        }

        return keys;
    }

    static Stream<PropagationCase> propagationCases() {
        return Stream.of(
            new PropagationCase(
                "a keyed line arriving ahead of inheriting lines moves what they run in",
                () -> songWithKeyMoveOnTheSecondLine(OWN_KEY, inheritingLine(), inheritingLine()),
                moveLine(SECOND_LINE, SECOND_LINE),
                List.of(DOCUMENT_KEY, OWN_KEY, MID_LINE_KEY, MID_LINE_KEY)),
            new PropagationCase(
                "an inheriting line arriving ahead of inheriting lines moves what they run in",
                () -> songWithKeyMoveOnTheSecondLine(null, inheritingLine(), inheritingLine()),
                moveLine(SECOND_LINE, SECOND_LINE),
                List.of(DOCUMENT_KEY, DOCUMENT_KEY, MID_LINE_KEY, MID_LINE_KEY)),
            new PropagationCase(
                "a line leaving hands its place on to the next line and moves nothing past it",
                () -> songWithKeyMoveOnTheSecondLine(
                    OWN_KEY, inheritingLine(), keyedLine(), inheritingLine()),
                song -> song.removeLine(SECOND_LINE),
                List.of(DOCUMENT_KEY, DOCUMENT_KEY, FOLLOWER_KEY, FOLLOWER_KEY)),
            new PropagationCase(
                "a keyed line arriving at index 0 keeps its own key and displaces the line there",
                () -> songWithKeyMoveOnTheSecondLine(OWN_KEY, inheritingLine()),
                moveLine(SECOND_LINE, FIRST_LINE),
                List.of(OWN_KEY, DOCUMENT_KEY, DOCUMENT_KEY)),
            new PropagationCase(
                "a line's own key changing leaves the lines past its mid-line key change where they were",
                () -> songWithKeyMoveOnTheSecondLine(null, inheritingLine(), inheritingLine()),
                song -> song.withModification(() -> song.getLine(SECOND_LINE).setKey(OWN_KEY)),
                List.of(DOCUMENT_KEY, OWN_KEY, MID_LINE_KEY, MID_LINE_KEY)),
            new PropagationCase(
                "a mid-line key change removed hands the line's own key on to the lines inheriting "
                    + "past it, up to a line with its own key",
                () -> songWithKeyMoveOnTheSecondLine(
                    OWN_KEY, inheritingLine(), keyedLine(), inheritingLine()),
                song -> {
                    var line = song.getLine(SECOND_LINE);
                    song.withModification(
                        () -> line.deleteRange(line.effectiveRange(
                            StaffElementRun.ElementRange.single(KEY_CHANGE_INDEX))));
                },
                List.of(DOCUMENT_KEY, OWN_KEY, OWN_KEY, FOLLOWER_KEY, FOLLOWER_KEY)),
            new PropagationCase(
                "a mid-line key change inserted moves the lines inheriting past it",
                () -> songWithKeyMoveOnTheSecondLine(null, inheritingLine(), inheritingLine()),
                song -> {
                    var line = song.getLine(THIRD_LINE);
                    song.withModification(() -> {
                        line.addElement(singleBarline());
                        line.addElement(keyChange(OWN_KEY));
                        line.addElement(crotchet());
                    });
                },
                List.of(DOCUMENT_KEY, DOCUMENT_KEY, MID_LINE_KEY, OWN_KEY)),
            new PropagationCase(
                "a mid-line key change edited in place moves the lines inheriting past it",
                () -> songWithKeyMoveOnTheSecondLine(null, inheritingLine(), inheritingLine()),
                song -> {
                    var line = song.getLine(SECOND_LINE);
                    var keyChange = (KeyChangeElement) line.getElement(KEY_CHANGE_INDEX);
                    song.withModification(() -> line.modifyElement(
                        KEY_CHANGE_INDEX, ElementField.KEY, () -> keyChange.setKey(OWN_KEY)));
                },
                List.of(DOCUMENT_KEY, DOCUMENT_KEY, OWN_KEY, OWN_KEY)),
            new PropagationCase(
                "a mid-line key change removed on its own hands the line's own key on to the lines "
                    + "inheriting past it",
                () -> songWithKeyMoveOnTheSecondLine(OWN_KEY, inheritingLine(), inheritingLine()),
                song -> {
                    var line = song.getLine(SECOND_LINE);
                    song.withModification(() -> line.removeElement(KEY_CHANGE_INDEX));
                },
                List.of(DOCUMENT_KEY, OWN_KEY, OWN_KEY, OWN_KEY)),
            new PropagationCase(
                "a mid-line key change replaced by one establishing another key moves the lines "
                    + "inheriting past it",
                () -> songWithKeyMoveOnTheSecondLine(null, inheritingLine(), inheritingLine()),
                song -> {
                    var line = song.getLine(SECOND_LINE);
                    song.withModification(() -> line.setElement(KEY_CHANGE_INDEX, keyChange(OWN_KEY)));
                },
                List.of(DOCUMENT_KEY, DOCUMENT_KEY, OWN_KEY, OWN_KEY)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("propagationCases")
    void testAKeyMovingEditLeavesEveryLineInTheKeyTheLineBeforeItEndsIn(PropagationCase testCase) {
        var song = testCase.song().get();

        testCase.edit().accept(song);

        assertThat(runningKeysOf(song)).isEqualTo(testCase.expectedKeys());
        assertKeyPropagationInvariant(song);
    }
}
