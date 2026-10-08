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
import songscribe.dom.StaffElementRun.ElementRange;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.SongFactory.buildSong;
import static songscribe.dom.SpanFactory.addWithoutTracking;
import static songscribe.dom.SpanFactory.beamOver;
import static songscribe.dom.SpanFactory.tripletOver;
import static songscribe.dom.StaffElementFactory.quaver;

/**
 * The two facts the drawn bracket depends on: whether the tuplet's anchor draws an upward stem
 * ({@link Tuplet#anchorStemUp()}), and whether only the number is drawn, which needs the tuplet
 * under one beam with that stem ({@link Tuplet#isNumberOnly()}).
 */
class TupletNumberOnlyTest extends UnitTest {

    private static final int FIRST_LINE = 0;
    private static final int NOTE_POSITION = 0;

    /** Six quavers, with the triplet over the middle three. */
    private static final int LINE_LENGTH = 6;

    private static final ElementRange TRIPLET = new ElementRange(1, 3);

    /** The three elements of the anchor case: the anchor, then two alike that end the triplet. */
    private static final ElementRange ANCHOR_RANGE = new ElementRange(0, 2);

    private static final Stem.Forced UP = new Stem.Forced(Direction.UP);
    private static final Stem.Forced DOWN = new Stem.Forced(Direction.DOWN);

    /**
     * A tuplet anchor and whether it draws an upward stem. The two elements after the anchor
     * are alike, and the triplet's share of the whole group is the value that makes the three a
     * triplet.
     *
     * @param description the anchor, as the test's display name
     * @param anchor      makes the anchor element
     * @param follower    makes each of the two elements after the anchor
     * @param share       the written value of one normal note of the triplet
     * @param expected    whether the anchor draws an upward stem
     */
    private record AnchorCase(
        String description,
        Supplier<StaffElement> anchor,
        Supplier<StaffElement> follower,
        NoteValue share,
        boolean expected
    ) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("anchorCases")
    void testOnlyAStemmedNoteWithAnUpwardStemDrawsAnUpwardStem(AnchorCase testCase) {
        var song = buildSong(line -> {
            line.addElement(testCase.anchor().get());
            line.addElement(testCase.follower().get());
            line.addElement(testCase.follower().get());
        });
        var line = song.getLine(FIRST_LINE);
        var tuplet = addWithoutTracking(song, () -> tripletOver(line, ANCHOR_RANGE, testCase.share()));

        assertThat(tuplet.anchorStemUp()).isEqualTo(testCase.expected());
    }

    static Stream<AnchorCase> anchorCases() {
        return Stream.of(
            new AnchorCase("a note with a forced upward stem does",
                () -> quaver(NOTE_POSITION, UP), StaffElementFactory::quaver, NoteValue.EIGHTH, true),
            new AnchorCase("a note with a forced downward stem does not",
                () -> quaver(NOTE_POSITION, DOWN), StaffElementFactory::quaver, NoteValue.EIGHTH, false),
            new AnchorCase("a rest has no stem to draw",
                StaffElementFactory::quaverRest, StaffElementFactory::quaver, NoteValue.EIGHTH, false),
            new AnchorCase("a semibreve has no stem to draw, whichever way it is forced",
                TupletNumberOnlyTest::semibreveForcedUp, StaffElementFactory::crotchet, NoteValue.HALF, false)
        );
    }

    private static NoteElement semibreveForcedUp() {
        var semibreve = StaffElementFactory.semibreve();
        semibreve.setStem(UP);

        return semibreve;
    }

    /**
     * A line of quavers holding a triplet over {@link #TRIPLET}, and the beams over it.
     *
     * @param description the arrangement, as the test's display name
     * @param anchorStem  the stem of the triplet's anchor
     * @param beams       the ranges the beams cover
     * @param expected    whether only the number is drawn
     */
    private record NumberOnlyCase(
        String description, Stem anchorStem, List<ElementRange> beams, boolean expected) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("numberOnlyCases")
    void testOnlyTheNumberIsDrawnWhenOneBeamCoversTheTupletWithAnUpwardAnchorStem(NumberOnlyCase testCase) {
        var song = buildSong(line -> {
            for (var i = 0; i < LINE_LENGTH; i++) {
                line.addElement(i == TRIPLET.begin()
                    ? quaver(NOTE_POSITION, testCase.anchorStem())
                    : quaver(NOTE_POSITION, Stem.AUTO));
            }
        });
        var line = song.getLine(FIRST_LINE);

        for (var range : testCase.beams()) {
            addWithoutTracking(song, () -> beamOver(line, range));
        }

        var tuplet = addWithoutTracking(song, () -> tripletOver(line, TRIPLET, NoteValue.EIGHTH));

        assertThat(tuplet.isNumberOnly()).isEqualTo(testCase.expected());
    }

    static Stream<NumberOnlyCase> numberOnlyCases() {
        return Stream.of(
            new NumberOnlyCase("beamed across the whole tuplet with an upward anchor stem",
                UP, List.of(TRIPLET), true),
            new NumberOnlyCase("beamed across the whole tuplet with a downward anchor stem",
                DOWN, List.of(TRIPLET), false),
            new NumberOnlyCase("beamed at the anchor but not at the end",
                UP, List.of(new ElementRange(TRIPLET.begin(), TRIPLET.end() - 1)), false),
            new NumberOnlyCase("beamed at the end but not at the anchor",
                UP, List.of(new ElementRange(TRIPLET.begin() + 1, TRIPLET.end())), false),
            new NumberOnlyCase("under two beams, one at each end, so the bracket is drawn",
                UP,
                List.of(
                    new ElementRange(TRIPLET.begin(), TRIPLET.begin() + 1),
                    new ElementRange(TRIPLET.end(), TRIPLET.end() + 1)),
                false),
            new NumberOnlyCase("not beamed at all",
                UP, List.of(), false)
        );
    }
}
