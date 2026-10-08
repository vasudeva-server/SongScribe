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
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;
import songscribe.dom.StaffElementRun.ElementRange;
import songscribe.message.mutation.ElementField;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.dom.StaffElementFactory.repeatRight;

/**
 * What an {@link ElementChange} promises across its own queries, for each of its four shapes:
 * {@link ElementChange#projectedIndexOf} and {@link ElementChange#projectedElements} describe one
 * projection.
 */
class ElementChangeTest extends UnitTest {

    /**
     * The fixture line, by position: a note, a note, a right repeat, and three more notes. The
     * second element and the repeat are the ones the projection cases change.
     */
    private static final int SECOND_NOTE = 1;
    private static final int REPEAT = 2;

    /**
     * One change to the fixture line, and what its projection must hold.
     *
     * @param description    the case, as the test's display name
     * @param change         builds the change against the line
     * @param removedIndices the line indices of the elements the change removes
     * @param arrivals       each position the change's projection holds a new element at,
     *                       mapped to that element; empty when the change brings none
     */
    private record ProjectionCase(
        String description,
        Function<Line, ElementChange> change,
        List<Integer> removedIndices,
        Map<Integer, StaffElement> arrivals
    ) {}

    /** The fixture line. */
    private static Line fixtureLine() {
        var line = detachedLine();
        line.addElement(crotchet());
        line.addElement(crotchet());
        line.addElement(repeatRight());
        line.addElement(crotchet());
        line.addElement(crotchet());
        line.addElement(crotchet());

        return line;
    }

    static Stream<ProjectionCase> projectionCases() {
        var inserted = crotchet();
        var replacement = crotchet();
        var deleted = new ElementRange(SECOND_NOTE, REPEAT);
        var firstPairReplacement = crotchet();
        var secondPairReplacement = crotchet();
        Map<Integer, StaffElement> twoPairs =
            Map.of(SECOND_NOTE, firstPairReplacement, REPEAT, secondPairReplacement);

        return Stream.of(
            new ProjectionCase("an insertion",
                line -> new ElementChange.Insertion(line, REPEAT, inserted),
                List.of(), Map.of(REPEAT, inserted)),
            new ProjectionCase("a replacement",
                line -> ElementChange.Replacement.single(line, REPEAT, replacement),
                List.of(REPEAT), Map.of(REPEAT, replacement)),
            new ProjectionCase("a replacement of two pairs",
                line -> new ElementChange.Replacement(line, new TreeMap<>(twoPairs)),
                List.of(SECOND_NOTE, REPEAT), twoPairs),
            new ProjectionCase("a deletion",
                line -> new ElementChange.Deletion(line, deleted),
                List.of(SECOND_NOTE, REPEAT), Map.of()),
            new ProjectionCase("a modification",
                line -> new ElementChange.Modification(line, SECOND_NOTE, Set.of(ElementField.LYRIC)),
                List.of(), Map.of())
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("projectionCases")
    void testTheProjectionAgreesWithItselfAboutEveryElement(ProjectionCase testCase) {
        var line = fixtureLine();
        var change = testCase.change().apply(line);
        var projected = change.projectedElements();
        var survivors = new ArrayList<StaffElement>();

        for (var index = 0; index < line.elementCount(); index++) {
            var element = line.getElement(index);
            var projectedIndex = change.projectedIndexOf(element);

            if (testCase.removedIndices().contains(index)) {
                assertThat(projectedIndex).as("the element at %d goes", index).isNull();
                continue;
            }

            assertThat(projectedIndex).as("the element at %d stays", index).isNotNull();
            assertThat(projected.get(projectedIndex)).isSameAs(element);
            survivors.add(element);
        }

        var arrivals = testCase.arrivals();

        arrivals.forEach((index, arriving) -> {
            assertThat(change.projectedIndexOf(arriving)).isEqualTo(index);
            assertThat(projected.get(index)).isSameAs(arriving);
        });

        assertThat(projected).hasSize(survivors.size() + arrivals.size());
    }

    /** The run the deletion rows of {@link #endpointCases} remove. */
    private static final ElementRange DELETED_RUN = new ElementRange(SECOND_NOTE, REPEAT);

    /**
     * One change to the fixture line, and where a span endpoint on one of its elements stands
     * afterwards.
     *
     * @param description the case, as the test's display name
     * @param change      builds the change against the line
     * @param endpoint    the line index of the element the span endpoint is on
     * @param expected    the endpoint's position in the projection, or {@code null} when the
     *                    change removes the endpoint
     */
    private record EndpointCase(
        String description,
        Function<Line, ElementChange> change,
        int endpoint,
        @Nullable Integer expected
    ) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("endpointCases")
    void testAnEndpointAnswersTheProjectedPositionOfItselfOrOfItsReplacement(EndpointCase testCase) {
        var line = fixtureLine();
        var change = testCase.change().apply(line);

        assertThat(change.projectedEndpointIndexOf(line.getElement(testCase.endpoint())))
            .isEqualTo(testCase.expected());
    }

    static Stream<EndpointCase> endpointCases() {
        return Stream.of(
            new EndpointCase("a replaced endpoint answers its replacement's position",
                line -> ElementChange.Replacement.single(line, REPEAT, crotchet()), REPEAT, REPEAT),
            new EndpointCase("an endpoint a replacement leaves in place answers its own position",
                line -> ElementChange.Replacement.single(line, REPEAT, crotchet()), SECOND_NOTE, SECOND_NOTE),
            new EndpointCase("a deleted endpoint answers null",
                line -> new ElementChange.Deletion(line, DELETED_RUN), SECOND_NOTE, null),
            new EndpointCase("an endpoint after a deletion answers its position less the deleted run",
                line -> new ElementChange.Deletion(line, DELETED_RUN), DELETED_RUN.end() + 1, DELETED_RUN.begin()),
            new EndpointCase("an endpoint at an insertion index is pushed one along",
                line -> new ElementChange.Insertion(line, REPEAT, crotchet()), REPEAT, REPEAT + 1),
            new EndpointCase("an endpoint on a modified element answers its own position",
                line -> new ElementChange.Modification(line, SECOND_NOTE, Set.of(ElementField.LYRIC)),
                SECOND_NOTE, SECOND_NOTE)
        );
    }

    @Test
    void testProjectionCasesCoverEveryShape() {
        var line = fixtureLine();

        assertThat(projectionCases().<Class<?>>map(testCase -> testCase.change().apply(line).getClass()).distinct())
            .containsExactlyInAnyOrder(ElementChange.class.getPermittedSubclasses());
    }
}
