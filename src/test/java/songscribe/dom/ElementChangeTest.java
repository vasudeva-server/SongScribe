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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;

/**
 * Unit tests for {@link ElementChange}'s projection — the two directions each change shape
 * maps between a line's positions and the positions it will have once the change lands.
 *
 * <p>The projection is a view rather than a copy, so both directions are arithmetic over
 * the pre-change line rather than a list that can be read off and checked by eye. That
 * arithmetic is what these tests pin: every span decides its fate from it, but each span
 * type only ever asks about the handful of positions it happens to care about, so an
 * off-by-one here would surface only wherever some span test happened to touch that index.
 */
class ElementChangeTest extends UnitTest {

    /** Positions in the fixture line, which holds {@link #ELEMENT_COUNT} distinct notes. */
    private static final int FIRST_INDEX = 0;
    private static final int SECOND_INDEX = 1;
    private static final int MIDDLE_INDEX = 2;
    private static final int FOURTH_INDEX = 3;
    private static final int LAST_INDEX = 4;
    private static final int ELEMENT_COUNT = 5;

    /** What {@link ElementChange#projectedIndexOf} reports for an element that will not be there. */
    private static final int ABSENT = -1;

    private Line line;

    /** The fixture's elements by their pre-change position, captured before any projection. */
    private List<StaffElement> elements;

    @BeforeEach
    void setUp() {
        line = detachedLine();

        for (var i = 0; i < ELEMENT_COUNT; i++) {
            line.addElement(new StaffElement(ElementType.CROTCHET));
        }

        elements = List.copyOf(line.getElements());
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class ForInsertion {

        @Test
        void testProjectedElementsPlacesTheInsertedElementAtItsIndex() {
            var inserted = new StaffElement(ElementType.QUAVER);

            assertThat(ElementChange.forInsertion(line, MIDDLE_INDEX, inserted).projectedElements())
                .as("the projection must read as the line will once the insertion lands")
                .containsExactly(
                    elements.get(FIRST_INDEX),
                    elements.get(SECOND_INDEX),
                    inserted,
                    elements.get(MIDDLE_INDEX),
                    elements.get(FOURTH_INDEX),
                    elements.get(LAST_INDEX));
        }

        @Test
        void testProjectedIndexOfShiftsOnlyFromTheInsertionPointOn() {
            var change = ElementChange.forInsertion(
                line, MIDDLE_INDEX, new StaffElement(ElementType.QUAVER));

            assertAll(
                () -> assertThat(change.projectedIndexOf(elements.get(SECOND_INDEX)))
                    .as("an element before the insertion point keeps its position")
                    .isEqualTo(SECOND_INDEX),
                () -> assertThat(change.projectedIndexOf(elements.get(MIDDLE_INDEX)))
                    .as("the element at the insertion point is pushed one along")
                    .isEqualTo(MIDDLE_INDEX + 1),
                () -> assertThat(change.projectedIndexOf(elements.get(LAST_INDEX)))
                    .as("and so is everything after it")
                    .isEqualTo(LAST_INDEX + 1));
        }

        @Test
        void testProjectedIndexOfFindsTheInsertedElementAtItsIndex() {
            var inserted = new StaffElement(ElementType.QUAVER);

            assertThat(ElementChange.forInsertion(line, MIDDLE_INDEX, inserted)
                    .projectedIndexOf(inserted))
                .as("the element being inserted is in the projection, at the insertion point")
                .isEqualTo(MIDDLE_INDEX);
        }

        @Test
        void testProjectedIndexOfReportsAnElementFromNoLineAsAbsent() {
            assertThat(ElementChange.forInsertion(
                    line, MIDDLE_INDEX, new StaffElement(ElementType.QUAVER))
                    .projectedIndexOf(new StaffElement(ElementType.CROTCHET)))
                .as("an element that is in no line cannot have a projected position")
                .isEqualTo(ABSENT);
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class ForReplacement {

        @Test
        void testProjectedElementsSwapsOnlyTheReplacedPosition() {
            var replacement = new StaffElement(ElementType.QUAVER);

            assertThat(ElementChange.forReplacement(line, MIDDLE_INDEX, replacement)
                    .projectedElements())
                .as("a replacement moves nothing; it swaps one position")
                .containsExactly(
                    elements.get(FIRST_INDEX),
                    elements.get(SECOND_INDEX),
                    replacement,
                    elements.get(FOURTH_INDEX),
                    elements.get(LAST_INDEX));
        }

        @Test
        void testTheReplacedElementIsAbsentAndTheReplacementTakesItsPlace() {
            var replacement = new StaffElement(ElementType.QUAVER);
            var change = ElementChange.forReplacement(line, MIDDLE_INDEX, replacement);

            assertAll(
                () -> assertThat(change.oldElement())
                    .as("the change must resolve the element it displaces from the line")
                    .isSameAs(elements.get(MIDDLE_INDEX)),
                () -> assertThat(change.projectedIndexOf(elements.get(MIDDLE_INDEX)))
                    .as("the replaced element will not be in the line at all")
                    .isEqualTo(ABSENT),
                () -> assertThat(change.projectedIndexOf(replacement))
                    .as("the replacement takes the position it was given")
                    .isEqualTo(MIDDLE_INDEX),
                () -> assertThat(change.projectedIndexOf(elements.get(LAST_INDEX)))
                    .as("nothing else moves")
                    .isEqualTo(LAST_INDEX));
        }

        @Test
        void testReplacingAnElementWithItselfLeavesItInPlace() {
            var unchanged = elements.get(MIDDLE_INDEX);

            // Line.setElement permits a self-replace, and reading it as "the old element is
            // gone" would report the surviving element as absent from its own line.
            assertThat(ElementChange.forReplacement(line, MIDDLE_INDEX, unchanged)
                    .projectedIndexOf(unchanged))
                .as("an element replaced by itself keeps its position")
                .isEqualTo(MIDDLE_INDEX);
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class ForDeletion {

        @Test
        void testProjectedElementsClosesUpTheDeletedRange() {
            assertThat(ElementChange.forDeletion(line, SECOND_INDEX, MIDDLE_INDEX)
                    .projectedElements())
                .as("the survivors close up in document order")
                .containsExactly(
                    elements.get(FIRST_INDEX),
                    elements.get(FOURTH_INDEX),
                    elements.get(LAST_INDEX));
        }

        @Test
        void testASingleElementIsTheSamePositionTwice() {
            assertThat(ElementChange.forDeletion(line, MIDDLE_INDEX, MIDDLE_INDEX)
                    .projectedElements())
                .as("a one-element deletion is a range of one, not a special case")
                .containsExactly(
                    elements.get(FIRST_INDEX),
                    elements.get(SECOND_INDEX),
                    elements.get(FOURTH_INDEX),
                    elements.get(LAST_INDEX));
        }

        @Test
        void testDeletingFromTheStartLeavesTheTail() {
            assertThat(ElementChange.forDeletion(line, FIRST_INDEX, SECOND_INDEX)
                    .projectedElements())
                .as("nothing precedes the deletion, so every survivor shifts")
                .containsExactly(
                    elements.get(MIDDLE_INDEX),
                    elements.get(FOURTH_INDEX),
                    elements.get(LAST_INDEX));
        }

        @Test
        void testDeletingToTheEndLeavesTheHead() {
            assertThat(ElementChange.forDeletion(line, FOURTH_INDEX, LAST_INDEX)
                    .projectedElements())
                .as("nothing follows the deletion, so no survivor shifts")
                .containsExactly(
                    elements.get(FIRST_INDEX),
                    elements.get(SECOND_INDEX),
                    elements.get(MIDDLE_INDEX));
        }

        @Test
        void testDeletingEverythingProjectsAnEmptyLine() {
            assertThat(ElementChange.forDeletion(line, FIRST_INDEX, LAST_INDEX).projectedElements())
                .as("deleting every element leaves nothing to project")
                .isEmpty();
        }

        @Test
        void testDeletedElementsIsTheRangeItself() {
            assertThat(ElementChange.forDeletion(line, SECOND_INDEX, MIDDLE_INDEX).deletedElements())
                .as("the elements leaving the line are exactly the range, in document order")
                .containsExactly(elements.get(SECOND_INDEX), elements.get(MIDDLE_INDEX));
        }

        @Test
        void testProjectedIndexOfSubtractsTheRangeOnlyFromPositionsAfterIt() {
            var change = ElementChange.forDeletion(line, SECOND_INDEX, MIDDLE_INDEX);

            assertAll(
                () -> assertThat(change.projectedIndexOf(elements.get(FIRST_INDEX)))
                    .as("an element before the range keeps its position")
                    .isEqualTo(FIRST_INDEX),
                () -> assertThat(change.projectedIndexOf(elements.get(SECOND_INDEX)))
                    .as("the first element of the range has no projected position")
                    .isEqualTo(ABSENT),
                () -> assertThat(change.projectedIndexOf(elements.get(MIDDLE_INDEX)))
                    .as("nor does the last one — the range is inclusive at both ends")
                    .isEqualTo(ABSENT),
                () -> assertThat(change.projectedIndexOf(elements.get(FOURTH_INDEX)))
                    .as("a survivor after the range moves down by its length")
                    .isEqualTo(SECOND_INDEX),
                () -> assertThat(change.projectedIndexOf(elements.get(LAST_INDEX)))
                    .as("as does every later one, by the same length")
                    .isEqualTo(MIDDLE_INDEX));
        }

        @Test
        void testProjectedIndexOfReportsAnElementFromNoLineAsAbsent() {
            assertThat(ElementChange.forDeletion(line, SECOND_INDEX, MIDDLE_INDEX)
                    .projectedIndexOf(new StaffElement(ElementType.CROTCHET)))
                .as("an element that is in no line cannot have a projected position")
                .isEqualTo(ABSENT);
        }

        @Test
        void testProjectedElementsRejectsAPositionPastItsEnd() {
            var projected = ElementChange.forDeletion(line, MIDDLE_INDEX, MIDDLE_INDEX)
                .projectedElements();

            // The line still holds an element at this position, so a view that did not check
            // would hand back one that is not in the projection at all.
            assertThatThrownBy(() -> projected.get(projected.size()))
                .as("a position past the projected end is out of bounds, not a live element")
                .isInstanceOf(IndexOutOfBoundsException.class);
        }
    }
}
