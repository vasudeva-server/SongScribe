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
import static songscribe.dom.SpanFactory.beamOver;
import static songscribe.dom.SpanFactory.ratioOf;
import static songscribe.dom.SpanFactory.tripletOver;
import static songscribe.dom.SpanFactory.TRIPLET_NORMAL_NOTES;
import static songscribe.dom.StaffElementFactory.quaver;

/**
 * The two facts the drawn bracket depends on: whether the tuplet's anchor draws an upward stem
 * ({@link Tuplet#anchorStemUp()}), and whether only the number is drawn, which needs the tuplet
 * beamed at both ends with that stem ({@link Tuplet#isNumberOnly()}).
 */
class TupletNumberOnlyTest extends UnitTest {

    private static final int FIRST_LINE = 0;
    private static final int NOTE_POSITION = 0;

    /** Six quavers, with the triplet over the middle three. */
    private static final int LINE_LENGTH = 6;

    private static final ElementRange TRIPLET = new ElementRange(1, 3);

    private static final Stem.Forced UP = new Stem.Forced(Direction.UP);
    private static final Stem.Forced DOWN = new Stem.Forced(Direction.DOWN);

    /**
     * A tuplet anchor and whether it draws an upward stem.
     *
     * @param description the anchor, as the test's display name
     * @param anchor      makes the anchor element
     * @param expected    whether the anchor draws an upward stem
     */
    private record AnchorCase(String description, Supplier<StaffElement> anchor, boolean expected) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("anchorCases")
    void testOnlyAStemmedNoteWithAnUpwardStemDrawsAnUpwardStem(AnchorCase testCase) {
        var tuplet = new Tuplet(testCase.anchor().get(), quaver(), TupletGrade.TRIPLET,
            ratioOf(TRIPLET_NORMAL_NOTES, NoteValue.EIGHTH));

        assertThat(tuplet.anchorStemUp()).isEqualTo(testCase.expected());
    }

    static Stream<AnchorCase> anchorCases() {
        return Stream.of(
            new AnchorCase("a note with a forced upward stem does",
                () -> quaver(NOTE_POSITION, UP), true),
            new AnchorCase("a note with a forced downward stem does not",
                () -> quaver(NOTE_POSITION, DOWN), false),
            new AnchorCase("a rest has no stem to draw",
                StaffElementFactory::crotchetRest, false)
        );
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
    void testOnlyTheNumberIsDrawnWhenBeamedAtBothEndsWithAnUpwardAnchorStem(NumberOnlyCase testCase) {
        var song = buildSong(line -> {
            for (var i = 0; i < LINE_LENGTH; i++) {
                line.addElement(i == TRIPLET.begin()
                    ? quaver(NOTE_POSITION, testCase.anchorStem())
                    : quaver(NOTE_POSITION, Stem.AUTO));
            }
        });
        var line = song.getLine(FIRST_LINE);
        var tuplet = song.withoutMutationTrackingResult(() -> {
            for (var range : testCase.beams()) {
                beamOver(line, range).addTo(line);
            }

            var built = tripletOver(line, TRIPLET, NoteValue.EIGHTH);
            built.addTo(line);

            return built;
        });

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
            new NumberOnlyCase("not beamed at all",
                UP, List.of(), false)
        );
    }
}
