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
package songscribe.layout;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;
import songscribe.dom.*;

class SpanInvalidationTest extends UnitTest {

    // -----------------------------------------------------------------------
    // Factory
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @FunctionalInterface
    interface SpanFactory {

        Span create(StaffElement anchor, StaffElement end);
    }

    static Stream<Arguments> spanFactories() {
        return Stream.of(
            Arguments.of("Tie",        (SpanFactory) Tie::new),
            Arguments.of("Trill",      (SpanFactory) Trill::new),
            Arguments.of("Tuplet",     (SpanFactory) (a, e) -> Tuplet.withUnresolvedRatio(a, e, 3)),
            Arguments.of("Crescendo",  (SpanFactory) Crescendo::new),
            Arguments.of("Diminuendo", (SpanFactory) Diminuendo::new),
            Arguments.of("Ending",     (SpanFactory) Ending::new)
        );
    }

    // -----------------------------------------------------------------------
    // Fixture
    // -----------------------------------------------------------------------

    /** Index of the span's anchor in every layout below. */
    private static final int ANCHOR_INDEX = 0;

    /** Number of quavers in the layout the interior tests need room in. */
    private static final int LAYOUT_WITH_INTERIOR_SIZE = 5;

    /** Index of the span's end in {@link #LAYOUT_WITH_INTERIOR_SIZE}'s layout. */
    private static final int INTERIOR_END_INDEX = LAYOUT_WITH_INTERIOR_SIZE - 1;

    /** Number of quavers in the layout whose span covers the first two of three notes. */
    private static final int LAYOUT_WITH_OUTSIDER_SIZE = 3;

    /** Index of the span's end in every layout but {@link #LAYOUT_WITH_INTERIOR_SIZE}'s. */
    private static final int END_INDEX = 1;

    /** Index of the note past the span in {@link #LAYOUT_WITH_OUTSIDER_SIZE}'s layout. */
    private static final int OUTSIDE_INDEX = 2;

    /** A line of {@code noteCount} plain quavers, unattached to a song. */
    private static Line quaverLine(int noteCount) {
        var types = new ElementType[noteCount];
        Arrays.fill(types, ElementType.QUAVER);

        return lineWith(types);
    }

    /**
     * The outcome {@code span} reports for deleting the contiguous run from {@code first}
     * through {@code last} of {@code line}, judged against the projected line.
     * <p>
     * Takes the endpoints as elements and resolves their positions here, so each test still
     * names what it deletes rather than counting indices.
     */
    private static SpanOutcome outcomeForDeleting(
        Span span, Line line, StaffElement first, StaffElement last
    ) {
        return span.outcomeFor(
            ElementChange.forDeletion(line, line.getElementIndex(first), line.getElementIndex(last)),
            line);
    }

    /** The outcome {@code span} reports for deleting {@code only} from {@code line}. */
    private static SpanOutcome outcomeForDeleting(Span span, Line line, StaffElement only) {
        return outcomeForDeleting(span, line, only, only);
    }

    // -----------------------------------------------------------------------
    // outcomeFor, deletion — all concrete subtypes
    //
    // A surviving span is asserted as "not Remove" rather than as Keep, because a hairpin
    // answers every deletion it survives with a Reshape carrying its projected endpoints.
    // -----------------------------------------------------------------------

    @ParameterizedTest(name = "{0}: anchor deleted → invalidated")
    @MethodSource("spanFactories")
    void testAnchorDeletedInvalidates(String name, SpanFactory factory) {
        var line = quaverLine(LAYOUT_WITH_OUTSIDER_SIZE);
        var anchor = line.getElement(ANCHOR_INDEX);
        var element = factory.create(anchor, line.getElement(END_INDEX));

        assertThat(outcomeForDeleting(element, line, anchor))
            .isSameAs(SpanOutcome.Simple.REMOVE);
    }

    @ParameterizedTest(name = "{0}: end deleted → invalidated")
    @MethodSource("spanFactories")
    void testEndDeletedInvalidates(String name, SpanFactory factory) {
        var line = quaverLine(LAYOUT_WITH_OUTSIDER_SIZE);
        var end = line.getElement(END_INDEX);
        var element = factory.create(line.getElement(ANCHOR_INDEX), end);

        assertThat(outcomeForDeleting(element, line, end))
            .isSameAs(SpanOutcome.Simple.REMOVE);
    }

    @ParameterizedTest(name = "{0}: both endpoints deleted → invalidated")
    @MethodSource("spanFactories")
    void testBothDeletedInvalidates(String name, SpanFactory factory) {
        var line = quaverLine(LAYOUT_WITH_OUTSIDER_SIZE);
        var anchor = line.getElement(ANCHOR_INDEX);
        var end = line.getElement(END_INDEX);
        var element = factory.create(anchor, end);

        assertThat(outcomeForDeleting(element, line, anchor, end))
            .isSameAs(SpanOutcome.Simple.REMOVE);
    }

    @ParameterizedTest(name = "{0}: only middle elements deleted → not invalidated")
    @MethodSource("spanFactories")
    void testMiddleDeletedNotInvalidates(String name, SpanFactory factory) {
        // Three interior notes with two of them deleted, so the one that survives leaves the
        // span content an ending demands as well as the two endpoints every span demands.
        var line = quaverLine(LAYOUT_WITH_INTERIOR_SIZE);
        var element = factory.create(line.getElement(ANCHOR_INDEX), line.getElement(INTERIOR_END_INDEX));
        var middle1 = line.getElement(ANCHOR_INDEX + 1);
        var middle2 = line.getElement(ANCHOR_INDEX + 2);

        assertThat(outcomeForDeleting(element, line, middle1, middle2))
            .isNotSameAs(SpanOutcome.Simple.REMOVE);
    }

    @ParameterizedTest(name = "{0}: unrelated element deleted → not invalidated")
    @MethodSource("spanFactories")
    void testExternalDeletedNotInvalidates(String name, SpanFactory factory) {
        var line = quaverLine(LAYOUT_WITH_OUTSIDER_SIZE);
        var element = factory.create(line.getElement(ANCHOR_INDEX), line.getElement(END_INDEX));

        assertThat(outcomeForDeleting(element, line, line.getElement(OUTSIDE_INDEX)))
            .isNotSameAs(SpanOutcome.Simple.REMOVE);
    }
}
