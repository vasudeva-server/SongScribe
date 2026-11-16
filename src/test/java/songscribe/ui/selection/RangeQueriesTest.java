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

package songscribe.ui.selection;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.Strings;
import songscribe.UnitTest;
import songscribe.dom.Key;
import songscribe.dom.Song;
import songscribe.dom.Spanning;
import songscribe.dom.StaffElement;
import songscribe.dom.StaffElementRun.ElementRange;
import songscribe.dom.Trill;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.SongFactory.compactSong;
import static songscribe.dom.SpanFactory.addWithoutTracking;
import static songscribe.dom.SpanFactory.beamOver;
import static songscribe.dom.StaffElementFactory.breathMark;
import static songscribe.dom.StaffElementFactory.crotchetRest;
import static songscribe.dom.StaffElementFactory.quaver;
import static songscribe.dom.StaffElementFactory.singleBarline;

/**
 * The range {@link RangeQueries#trillOver} asks {@link Trill#spanning} about: the selection
 * trimmed to its first and last pitched notes. And the selections
 * {@link RangeQueries#canToggleTie} accepts: those that name one tie that holds.
 */
class RangeQueriesTest extends UnitTest {

    private static final int PITCH = 3;
    private static final int OTHER_PITCH = 4;

    private static final int FIRST_LINE = 0;
    private static final int SECOND_LINE = 1;
    private static final int FIRST_ELEMENT = 0;
    private static final int SECOND_ELEMENT = 1;
    private static final int THIRD_ELEMENT = 2;

    private static final ElementRange FIRST_ONLY = new ElementRange(FIRST_ELEMENT, FIRST_ELEMENT);
    private static final ElementRange FIRST_TWO = new ElementRange(FIRST_ELEMENT, SECOND_ELEMENT);
    private static final ElementRange FIRST_THREE = new ElementRange(FIRST_ELEMENT, THIRD_ELEMENT);

    /** The elements of one line of a song that a test selects. */
    private record Selected(int line, ElementRange range) {}

    /**
     * A song, a selection in it, and whether the tie toggle accepts that selection.
     *
     * @param description the selection, as the test's display name
     * @param song        builds the song
     * @param selected    the selection
     * @param accepted    whether {@link RangeQueries#canToggleTie} accepts it
     */
    private record TieSelectionCase(String description, Supplier<Song> song, Selected selected, boolean accepted) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("tieSelectionCases")
    void testTheTieToggleAcceptsASelectionThatNamesOneTieThatHolds(TieSelectionCase testCase) {
        var song = testCase.song().get();
        var selected = testCase.selected();
        var selection = new ElementSelection(song.getLine(selected.line()), selected.range());

        assertThat(RangeQueries.canToggleTie(selection)).isEqualTo(testCase.accepted());
    }

    static Stream<TieSelectionCase> tieSelectionCases() {
        return Stream.of(
            new TieSelectionCase("two adjacent notes at one staff position",
                notated(PITCH + " " + PITCH), new Selected(FIRST_LINE, FIRST_TWO), true),
            new TieSelectionCase("two notes at one staff position on either side of a barline",
                notated(PITCH + " | " + PITCH), new Selected(FIRST_LINE, FIRST_THREE), true),
            new TieSelectionCase("a note and the barline after it",
                notated(PITCH + " | " + PITCH), new Selected(FIRST_LINE, FIRST_TWO), false),
            new TieSelectionCase("a note and the two barlines after it",
                notated(PITCH + " | | " + PITCH), new Selected(FIRST_LINE, FIRST_THREE), false),
            new TieSelectionCase("three notes at one staff position",
                notated(PITCH + " " + PITCH + " " + PITCH), new Selected(FIRST_LINE, FIRST_THREE), false),
            new TieSelectionCase("two notes at one staff position on either side of a key change",
                notated(PITCH + " K:" + Key.ONE_FLAT.name() + " " + PITCH), new Selected(FIRST_LINE, FIRST_THREE), false),
            new TieSelectionCase("two notes at different staff positions",
                notated(PITCH + " " + OTHER_PITCH), new Selected(FIRST_LINE, FIRST_TWO), false),
            new TieSelectionCase("two quavers that one beam holds",
                beamedOverFirstTwo("Q Q"), new Selected(FIRST_LINE, FIRST_TWO), false),
            new TieSelectionCase("two tied notes",
                notated(PITCH + " ~ " + PITCH), new Selected(FIRST_LINE, FIRST_TWO), true),
            new TieSelectionCase("a line's last note, with the same note starting the next line",
                notated("" + PITCH, "" + PITCH), new Selected(FIRST_LINE, FIRST_ONLY), true),
            new TieSelectionCase("a line's last note, with another staff position starting the next line",
                notated("" + PITCH, "" + OTHER_PITCH), new Selected(FIRST_LINE, FIRST_ONLY), false),
            new TieSelectionCase("a one-note line that the previous line cannot tie into, but that ties to the next",
                notated("" + OTHER_PITCH, "" + PITCH, "" + PITCH), new Selected(SECOND_LINE, FIRST_ONLY), true),
            new TieSelectionCase("a one-note line that neither neighboring line can tie to",
                notated("" + OTHER_PITCH, "" + PITCH, "" + OTHER_PITCH), new Selected(SECOND_LINE, FIRST_ONLY), false)
        );
    }

    private static Supplier<Song> notated(String... lines) {
        return () -> compactSong(Key.NO_ACCIDENTALS, lines);
    }

    /** A one-line song of {@code notation}, with a beam over its first two elements. */
    private static Supplier<Song> beamedOverFirstTwo(String notation) {
        return () -> {
            var song = compactSong(Key.NO_ACCIDENTALS, notation);
            var line = song.getLine(FIRST_LINE);

            addWithoutTracking(song, () -> beamOver(line, FIRST_TWO));

            return song;
        };
    }

    /**
     * A selected line, and what the trill toggle answers for it.
     *
     * @param description the selection, as the test's display name
     * @param elements    makes the line's elements, in order; the whole line is selected
     * @param trilled     the range the trill covers, or {@code null} when it is refused
     * @param reasonKey   the {@code Strings} key of the refusal, or {@code null} when it is not
     */
    private record TrillSelectionCase(
        String description,
        Supplier<List<StaffElement>> elements,
        @Nullable ElementRange trilled,
        @Nullable String reasonKey
    ) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("trillSelectionCases")
    void testATrillCoversTheSelectionFromItsFirstPitchedNoteToItsLast(TrillSelectionCase testCase) {
        var line = lineOf(testCase.elements().get().toArray(StaffElement[]::new));
        var selection = new ElementSelection(line, wholeRangeOf(line));
        var answer = RangeQueries.trillOver(selection);
        var trilled = testCase.trilled();

        if (trilled == null) {
            assertThat(answer).isInstanceOf(Spanning.Refused.class);
            assertThat(((Spanning.Refused<Trill>) answer).reasonKey()).isEqualTo(testCase.reasonKey());
            return;
        }

        assertThat(answer).isInstanceOf(Spanning.Spanned.class);

        var trill = ((Spanning.Spanned<Trill>) answer).span();

        assertThat(trill.getAnchorElement()).isSameAs(line.getElement(trilled.begin()));
        assertThat(trill.getEndElement()).isSameAs(line.getElement(trilled.end()));
    }

    static Stream<TrillSelectionCase> trillSelectionCases() {
        return Stream.of(
            new TrillSelectionCase("notes only",
                () -> List.of(quaver(), quaver(), quaver()), new ElementRange(0, 2), null),
            new TrillSelectionCase("a rest before the first note and a barline after the last",
                () -> List.of(crotchetRest(), quaver(), quaver(), singleBarline()), new ElementRange(1, 2), null),
            new TrillSelectionCase("a breath mark before a single note",
                () -> List.of(breathMark(), quaver()), new ElementRange(1, 1), null),
            new TrillSelectionCase("no pitched note",
                () -> List.of(crotchetRest(), crotchetRest()), null, Strings.REPORT_REASON_TRILL_ENDPOINT_UNUSABLE),
            new TrillSelectionCase("a rest between the first note and the last",
                () -> List.of(crotchetRest(), quaver(), crotchetRest(), quaver()),
                null, Strings.REPORT_REASON_TRILL_REST_OR_BREATH_MARK)
        );
    }
}
