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
import songscribe.dom.Spanning;
import songscribe.dom.StaffElement;
import songscribe.dom.StaffElementRun.ElementRange;
import songscribe.dom.Trill;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.StaffElementFactory.breathMark;
import static songscribe.dom.StaffElementFactory.crotchetRest;
import static songscribe.dom.StaffElementFactory.quaver;
import static songscribe.dom.StaffElementFactory.singleBarline;

/**
 * The range {@link RangeQueries#trillOver} asks {@link Trill#spanning} about: the selection
 * trimmed to its first and last pitched notes.
 */
class RangeQueriesTest extends UnitTest {

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
            new TrillSelectionCase("a rest before the first note and a bar line after the last",
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
