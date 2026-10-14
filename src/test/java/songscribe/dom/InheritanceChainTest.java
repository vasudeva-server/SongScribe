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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;
import songscribe.message.mutation.ElementInsertion;
import songscribe.message.mutation.LineKeyChange;
import songscribe.message.mutation.Mutation;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.SongFactory.buildSong;
import static songscribe.dom.SongFactory.notesAroundKeyChange;
import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.dom.StaffElementFactory.keyChange;
import static songscribe.dom.StaffElementFactory.singleBarline;

/**
 * Which lines a key reaches: {@link Song#inheritanceFrom}, the one statement of the rule that a
 * key runs forward to the first line with a key of its own, and {@link Song#keyMoveReach}, which
 * a view reads to learn which lines to re-solve after a key move.
 *
 * <p>Every case runs against the same shape of song, so each answer can be told from its
 * neighbors: a first line in the document key, an inheriting line leaving off in another key by
 * way of a mid-line key change, a plain inheriting line, a line with a key of its own, and a last
 * line inheriting from it.
 */
class InheritanceChainTest extends UnitTest {

    /** The key line 0 establishes. */
    private static final Key DOCUMENT_KEY = Key.NO_ACCIDENTALS;

    /** The key the second line's mid-line key change leaves it in. */
    private static final Key MID_LINE_KEY = Key.THREE_FLATS;

    /** The key the fourth line establishes for itself. */
    private static final Key OWN_KEY = Key.ONE_SHARP;

    /** A key no line of the song is in, so an answer carrying it came from the caller. */
    private static final Key SUPPOSED_KEY = Key.TWO_SHARPS;

    private static final int FIRST_LINE = 0;
    private static final int SECOND_LINE = 1;
    private static final int THIRD_LINE = 2;
    private static final int KEYED_LINE = 3;
    private static final int LAST_LINE = 4;

    /**
     * One expected inheriting line of a chain.
     *
     * @param lineIndex    the line's index
     * @param inheritedKey the key it must be reported inheriting
     */
    private record Link(int lineIndex, Key inheritedKey) {}

    /**
     * The expected line that stops a chain.
     *
     * @param lineIndex    the line's index
     * @param inheritedKey the key it must be reported inheriting
     * @param ownKey       the key it must be reported establishing
     */
    private record StopLink(int lineIndex, Key inheritedKey, Key ownKey) {}

    /**
     * One chain query, and the chain it must answer.
     *
     * @param description        the case, which doubles as the parameterized display name
     * @param originIndex        the line the chain starts after
     * @param originEndKey       the key to suppose that line leaves off in
     * @param expectedInheriting the inheriting lines, in song order
     * @param expectedStop       the line that stops the chain, or null when none does
     */
    private record ChainCase(
        String description,
        int originIndex,
        Key originEndKey,
        List<Link> expectedInheriting,
        @Nullable StopLink expectedStop) {

        @Override
        public String toString() {
            return description;
        }
    }

    /**
     * One edit, and the lines its key move reaches.
     *
     * @param description the case, which doubles as the parameterized display name
     * @param edit        makes the edit on a fresh song and returns the mutation it recorded
     * @param expected    the indices of the lines reached, in song order, once the edit is made
     */
    private record ReachCase(String description, Function<Song, Mutation> edit, List<Integer> expected) {

        @Override
        public String toString() {
            return description;
        }
    }

    private Song chainSong;

    @BeforeAll
    void buildChainSong() {
        chainSong = fiveLineSong();
    }

    /** The song every case runs against; see the class Javadoc for its shape. */
    private static Song fiveLineSong() {
        return buildSong(
            line -> {
                line.setKey(DOCUMENT_KEY);
                line.addElement(crotchet());
            },
            line -> notesAroundKeyChange(line, MID_LINE_KEY),
            line -> line.addElement(crotchet()),
            line -> {
                line.setKey(OWN_KEY);
                line.addElement(crotchet());
            },
            line -> line.addElement(crotchet()));
    }

    static Stream<ChainCase> chainCases() {
        return Stream.of(
            new ChainCase(
                "carried through a mid-line key change to the first line with a key of its own",
                FIRST_LINE, SUPPOSED_KEY,
                List.of(new Link(SECOND_LINE, SUPPOSED_KEY), new Link(THIRD_LINE, MID_LINE_KEY)),
                new StopLink(KEYED_LINE, MID_LINE_KEY, OWN_KEY)),
            new ChainCase(
                "started from the supposed end key rather than the one the origin leaves off in",
                THIRD_LINE, SUPPOSED_KEY,
                List.of(),
                new StopLink(KEYED_LINE, SUPPOSED_KEY, OWN_KEY)),
            new ChainCase(
                "run to the end of the song when no later line has a key of its own",
                KEYED_LINE, OWN_KEY,
                List.of(new Link(LAST_LINE, OWN_KEY)),
                null),
            new ChainCase(
                "empty after the last line",
                LAST_LINE, OWN_KEY,
                List.of(),
                null));
    }

    static Stream<ReachCase> reachCases() {
        return Stream.of(
            new ReachCase(
                "a line's own key reaches the line before it, itself, and every line inheriting past "
                    + "it, whether or not that line's key moved",
                song -> {
                    var line = song.getLine(SECOND_LINE);
                    song.withModification(() -> line.setKey(SUPPOSED_KEY));
                    return new LineKeyChange(line, null, SUPPOSED_KEY);
                },
                List.of(FIRST_LINE, SECOND_LINE, THIRD_LINE)),
            new ReachCase(
                "a mid-line key change reaches its own line and the lines inheriting past it",
                song -> {
                    var line = song.getLine(SECOND_LINE);
                    var change = keyChange(SUPPOSED_KEY);
                    song.withModification(() -> {
                        line.addElement(singleBarline());
                        line.addElement(change);
                        line.addElement(crotchet());
                    });
                    return new ElementInsertion(line, line.getElementIndex(change), change);
                },
                List.of(SECOND_LINE, THIRD_LINE)),
            new ReachCase(
                "an edit moving no key reaches nothing",
                song -> {
                    var line = song.getLine(THIRD_LINE);
                    var note = crotchet();
                    song.withModification(() -> line.addElement(note));
                    return new ElementInsertion(line, line.getElementIndex(note), note);
                },
                List.of()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("chainCases")
    void testTheChainRunsFromTheLineAfterTheOriginThroughTheFirstLineWithAKeyOfItsOwn(ChainCase testCase) {
        var chain = chainSong.inheritanceFrom(
            chainSong.getLine(testCase.originIndex()), testCase.originEndKey());

        var expectedStop = testCase.expectedStop();
        var expected = new Song.InheritanceChain(
            testCase.expectedInheriting().stream()
                .map(link -> new Song.Inheritance(chainSong.getLine(link.lineIndex()), link.inheritedKey()))
                .toList(),
            (expectedStop == null)
                ? null
                : new Song.InheritanceChain.Stop(
                    chainSong.getLine(expectedStop.lineIndex()), expectedStop.inheritedKey(), expectedStop.ownKey()));

        assertThat(chain).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("reachCases")
    void testAKeyMoveReachesTheOriginAndEveryLineInheritingPastIt(ReachCase testCase) {
        var song = fiveLineSong();
        var mutation = testCase.edit().apply(song);

        assertThat(song.keyMoveReach(mutation))
            .containsExactlyElementsOf(testCase.expected().stream().map(song::getLine).toList());
    }
}
