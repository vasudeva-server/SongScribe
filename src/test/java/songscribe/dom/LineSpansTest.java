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
import java.util.function.BiFunction;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;
import songscribe.dom.StaffElementRun.ElementRange;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.LineSpans.SPAN_ADJACENCY_REACH;
import static songscribe.dom.StaffElementFactory.quaver;

/**
 * What a span kind promises about the same-kind spans already on its line when it is added: a
 * kind absorbs whatever it overlaps, and a kind whose two touching spans are one gesture — a
 * hairpin — also absorbs a span flush against it, where a kind whose touching spans are two
 * deliberate groupings — a beam — leaves that span standing. Each row is one
 * {@link LineSpans.Absorb} value, reached through the kind that declares it.
 */
class LineSpansTest extends UnitTest {

    /**
     * One span kind and the absorb mode its displacement policy declares.
     *
     * @param description the case, as the test's display name
     * @param absorb      the mode the kind declares
     * @param kind        the kind's concrete class, which is what the merge is typed on
     * @param newSpan     builds a span of the kind over an anchor and an end element
     */
    private record MergeCase(
        String description,
        LineSpans.Absorb absorb,
        Class<? extends Span> kind,
        BiFunction<NoteElement, NoteElement, Span> newSpan
    ) {}

    private static final int LINE_LENGTH = 5;

    /** The span already on the line when the one under test is added. */
    private static final ElementRange EXISTING = new ElementRange(0, 2);

    /** A span sharing its first element with {@link #EXISTING}'s last. */
    private static final ElementRange OVERLAPPING = new ElementRange(EXISTING.end(), LINE_LENGTH - 1);

    /** A span beginning just past {@link #EXISTING}, within the reach that counts as touching. */
    private static final ElementRange ADJACENT =
        new ElementRange(EXISTING.end() + SPAN_ADJACENCY_REACH, LINE_LENGTH - 1);

    /** What {@link #EXISTING} and either other span become once merged. */
    private static final ElementRange MERGED = new ElementRange(EXISTING.begin(), LINE_LENGTH - 1);

    @ParameterizedTest(name = "{0}")
    @MethodSource("mergeCases")
    void testASpanOverlappingAnExistingOneOfItsKindAbsorbsItWhateverTheKindsReach(MergeCase testCase) {
        var line = lineOfQuavers();
        add(line, testCase, EXISTING);

        add(line, testCase, OVERLAPPING);

        assertThat(rangesOfKind(line, testCase))
            .as("the two spans became one over both their extents")
            .containsExactly(MERGED);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("mergeCases")
    void testASpanFlushAgainstAnExistingOneMergesOnlyForAKindThatAbsorbsAdjacentSpans(MergeCase testCase) {
        var line = lineOfQuavers();
        add(line, testCase, EXISTING);

        add(line, testCase, ADJACENT);

        var ranges = rangesOfKind(line, testCase);

        if (testCase.absorb() == LineSpans.Absorb.ADJACENT) {
            assertThat(ranges)
                .as("two touching spans of this kind describe one gesture")
                .containsExactly(MERGED);
        } else {
            assertThat(ranges)
                .as("two touching spans of this kind are two deliberate groupings")
                .containsExactly(EXISTING, ADJACENT);
        }
    }

    @Test
    void testCasesCoverEveryAbsorbMode() {
        assertThat(mergeCases().map(MergeCase::absorb))
            .containsExactlyInAnyOrder(LineSpans.Absorb.values());
    }

    static Stream<MergeCase> mergeCases() {
        return Stream.of(
            new MergeCase("a beam absorbs only what it overlaps",
                LineSpans.Absorb.OVERLAPPING, Beam.class, Beam::new),
            new MergeCase("a crescendo also absorbs what touches it",
                LineSpans.Absorb.ADJACENT, Crescendo.class, Crescendo::new)
        );
    }

    private static Line lineOfQuavers() {
        var line = detachedLine();

        for (var i = 0; i < LINE_LENGTH; i++) {
            line.addElement(quaver());
        }

        return line;
    }

    private static void add(Line line, MergeCase testCase, ElementRange range) {
        var anchor = (NoteElement) line.getElement(range.begin());
        var end = (NoteElement) line.getElement(range.end());
        testCase.newSpan().apply(anchor, end).addTo(line);
    }

    /** The extents of every span on {@code line} of the case's kind, in registry order. */
    private static List<ElementRange> rangesOfKind(Line line, MergeCase testCase) {
        return line.spanRegistry().getSpans().stream()
            .filter(testCase.kind()::isInstance)
            .map(span -> new ElementRange(span.requireAnchorElementIndex(), span.requireEndElementIndex()))
            .toList();
    }
}
