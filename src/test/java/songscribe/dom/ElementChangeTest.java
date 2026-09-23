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
import java.util.Set;
import java.util.function.Consumer;
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
import static songscribe.dom.StaffElementFactory.singleBarline;

/**
 * What an {@link ElementChange} promises across its own queries, for each of its four shapes:
 * {@link ElementChange#removesConfirmableSpan} predicts what the span sweep then does, and
 * {@link ElementChange#projectedIndexOf} and {@link ElementChange#projectedElements} describe one
 * projection.
 */
class ElementChangeTest extends UnitTest {

    /**
     * The fixture line, by position: a note, the note the ending anchors on, the repeat that
     * splits the ending, the note the ending ends on, and a note after the ending.
     */
    private static final int NOTE_OUTSIDE_ENDING = 0;
    private static final int ENDING_ANCHOR = 1;
    private static final int ENDING_SPLIT = 2;
    private static final int ENDING_END = 3;
    private static final int NOTE_AFTER_ENDING = 4;

    /**
     * One change to the fixture line, and whether it takes away the ending's repeat split.
     *
     * @param description the case, as the test's display name
     * @param change      builds the change against the line, still unapplied
     * @param apply       makes the same change to the line
     * @param splitGoes   whether the change leaves the ending without the split its invariant
     *                    requires, which is what costs the line the ending
     */
    private record ConfirmCase(
        String description, Function<Line, ElementChange> change, Consumer<Line> apply, boolean splitGoes
    ) {}

    /**
     * One change to the fixture line, and what its projection must hold.
     *
     * @param description    the case, as the test's display name
     * @param change         builds the change against the line
     * @param removedIndices the line indices of the elements the change removes
     * @param arriving       the element the change brings, or {@code null} when it brings none
     * @param arrivalIndex   the projected index {@code arriving} stands at
     */
    private record ProjectionCase(
        String description,
        Function<Line, ElementChange> change,
        List<Integer> removedIndices,
        @Nullable StaffElement arriving,
        int arrivalIndex
    ) {}

    /** The fixture line, with its ending. */
    private static Line fixtureLine() {
        var line = detachedLine();
        line.addElement(crotchet());
        line.addElement(crotchet());
        line.addElement(repeatRight());
        line.addElement(crotchet());
        line.addElement(crotchet());

        new Ending(line.getElement(ENDING_ANCHOR), line.getElement(ENDING_END)).addTo(line);

        return line;
    }

    private static boolean holdsAnEnding(Line line) {
        return line.spanRegistry().getSpans().stream().anyMatch(Ending.class::isInstance);
    }

    static Stream<ConfirmCase> confirmCases() {
        return Stream.of(
            confirmInsertion("an insertion after the ending", NOTE_AFTER_ENDING, crotchet(), false),
            confirmReplacement("a replacement of the split by a bar that closes no repeat",
                ENDING_SPLIT, singleBarline(), true),
            confirmReplacement("a replacement of a note outside the ending",
                NOTE_OUTSIDE_ENDING, crotchet(), false),
            confirmDeletion("a deletion of the split", ElementRange.single(ENDING_SPLIT), true),
            confirmDeletion("a deletion of a note outside the ending",
                ElementRange.single(NOTE_OUTSIDE_ENDING), false),
            new ConfirmCase("a modification of a note's lyric",
                line -> new ElementChange.Modification(line, ENDING_ANCHOR, Set.of(ElementField.LYRIC)),
                line -> line.modifyElement(ENDING_ANCHOR, ElementField.LYRIC, () -> { }),
                false)
        );
    }

    private static ConfirmCase confirmInsertion(
        String description, int index, StaffElement inserted, boolean splitGoes
    ) {
        return new ConfirmCase(description,
            line -> new ElementChange.Insertion(line, index, inserted),
            line -> line.addElement(index, inserted),
            splitGoes);
    }

    private static ConfirmCase confirmReplacement(
        String description, int index, StaffElement newElement, boolean splitGoes
    ) {
        return new ConfirmCase(description,
            line -> new ElementChange.Replacement(line, index, newElement),
            line -> line.setElement(index, newElement),
            splitGoes);
    }

    private static ConfirmCase confirmDeletion(String description, ElementRange range, boolean splitGoes) {
        return new ConfirmCase(description,
            line -> new ElementChange.Deletion(line, range),
            line -> line.removeRange(range),
            splitGoes);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("confirmCases")
    void testRemovesConfirmableSpanAgreesWithWhatTheSweepThenRemoves(ConfirmCase testCase) {
        var line = fixtureLine();

        var predicted = testCase.change().apply(line).removesConfirmableSpan();
        testCase.apply().accept(line);

        assertThat(predicted)
            .as("the prompt's prediction")
            .isEqualTo(testCase.splitGoes());
        assertThat(holdsAnEnding(line))
            .as("the ending standing after the change")
            .isEqualTo(!testCase.splitGoes());
    }

    @Test
    void testConfirmCasesCoverEveryShape() {
        var line = fixtureLine();

        assertThat(confirmCases().<Class<?>>map(testCase -> testCase.change().apply(line).getClass()).distinct())
            .containsExactlyInAnyOrder(ElementChange.class.getPermittedSubclasses());
    }

    static Stream<ProjectionCase> projectionCases() {
        var inserted = crotchet();
        var replacement = crotchet();
        var deleted = new ElementRange(ENDING_ANCHOR, ENDING_SPLIT);

        return Stream.of(
            new ProjectionCase("an insertion",
                line -> new ElementChange.Insertion(line, ENDING_SPLIT, inserted),
                List.of(), inserted, ENDING_SPLIT),
            new ProjectionCase("a replacement",
                line -> new ElementChange.Replacement(line, ENDING_SPLIT, replacement),
                List.of(ENDING_SPLIT), replacement, ENDING_SPLIT),
            new ProjectionCase("a deletion",
                line -> new ElementChange.Deletion(line, deleted),
                List.of(ENDING_ANCHOR, ENDING_SPLIT), null, 0),
            new ProjectionCase("a modification",
                line -> new ElementChange.Modification(line, ENDING_ANCHOR, Set.of(ElementField.LYRIC)),
                List.of(), null, 0)
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

        var arriving = testCase.arriving();
        var arrivingCount = arriving == null ? 0 : 1;

        if (arriving != null) {
            assertThat(change.projectedIndexOf(arriving)).isEqualTo(testCase.arrivalIndex());
            assertThat(projected.get(testCase.arrivalIndex())).isSameAs(arriving);
        }

        assertThat(projected).hasSize(survivors.size() + arrivingCount);
    }

    @Test
    void testProjectionCasesCoverEveryShape() {
        var line = fixtureLine();

        assertThat(projectionCases().<Class<?>>map(testCase -> testCase.change().apply(line).getClass()))
            .containsExactlyInAnyOrder(ElementChange.class.getPermittedSubclasses());
    }
}
