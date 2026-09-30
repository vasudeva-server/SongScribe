/*
 * SongScribe song notation program
 * Copyright (C) Sri Chinmoy Centres International
 *
 * This file is part of SongScribe.
 *
 * SongScribe is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * SongScribe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package songscribe.ui;

import java.util.function.Supplier;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;
import songscribe.dom.Hairpin;
import songscribe.dom.Line;
import songscribe.dom.StaffElement;
import songscribe.dom.StaffElementRun.ElementRange;
import songscribe.ui.MusicEditOperations.HairpinActionState;
import songscribe.ui.MusicEditOperations.HairpinResolution;
import songscribe.ui.selection.Selection;
import songscribe.ui.selection.SelectionCoordinator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static songscribe.dom.LineSpans.SPAN_ADJACENCY_REACH;
import static songscribe.dom.StaffElementFactory.crotchetRest;
import static songscribe.dom.StaffElementFactory.quaver;

/**
 * What the hairpin menu resolves to for a selection. A selection at the start of a line, where
 * nothing comes before it for a hairpin to reach back into, resolves like any other. A selection
 * the hairpin rule refuses for its own range is ineligible; one it refuses because another
 * hairpin is in the way, or that a same-kind hairpin already covers, is blocked.
 */
class MusicEditOperationsTest extends UnitTest {

    /** A selection of two notes from the line's first element: the columns a new hairpin needs. */
    private static final ElementRange FROM_FIRST = new ElementRange(0, 1);

    /** A crescendo within reach of {@link #FROM_FIRST}'s end. */
    private static final ElementRange FLUSH_AFTER =
        new ElementRange(FROM_FIRST.end() + SPAN_ADJACENCY_REACH, FROM_FIRST.end() + SPAN_ADJACENCY_REACH + 1);

    private static final int LINE_LENGTH = FLUSH_AFTER.end() + 1;

    /**
     * One selection starting on the line's first element.
     *
     * @param description the case, as the test's display name
     * @param existing    the crescendo already on the line, or null for none
     * @param expected    what the crescendo item resolves to
     */
    private record FirstElementCase(
        String description,
        @Nullable ElementRange existing,
        HairpinResolution expected
    ) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("firstElementCases")
    void testASelectionFromTheFirstElementResolvesLikeAnyOther(FirstElementCase testCase) {
        var line = lineOfQuavers(LINE_LENGTH);
        var existing = testCase.existing();

        if (existing != null) {
            crescendoOver(line, existing).addTo(line);
        }

        assertThat(resolve(line, FROM_FIRST, Hairpin.Kind.CRESCENDO)).isEqualTo(testCase.expected());
    }

    static Stream<FirstElementCase> firstElementCases() {
        return Stream.of(
            new FirstElementCase("with no hairpin nearby, the item adds over the selection",
                null,
                new HairpinResolution(HairpinActionState.CAN_ADD, FROM_FIRST)),
            new FirstElementCase("with a crescendo flush after it, the item extends over both",
                FLUSH_AFTER,
                new HairpinResolution(
                    HairpinActionState.EXTEND, new ElementRange(FROM_FIRST.begin(), FLUSH_AFTER.end())))
        );
    }

    private static final HairpinResolution INELIGIBLE = new HairpinResolution(HairpinActionState.INELIGIBLE, null);
    private static final HairpinResolution BLOCKED = new HairpinResolution(HairpinActionState.BLOCKED, null);

    /** A crescendo that a diminuendo over {@link #OVERLAPPING} shares two elements with. */
    private static final ElementRange OVERLAPPED = new ElementRange(0, 2);

    private static final ElementRange OVERLAPPING = new ElementRange(1, 3);

    /** A crescendo that holds the whole of {@link #INSIDE}. */
    private static final ElementRange COVERING = new ElementRange(0, 3);

    private static final ElementRange INSIDE = new ElementRange(1, 2);

    /**
     * One selection, and what an item resolves to for it.
     *
     * @param description the case, as the test's display name
     * @param line        builds the line, with any crescendo already on it
     * @param selection   the selected elements
     * @param kind        the kind of the item asked
     * @param expected    what the item resolves to
     */
    private record SelectionCase(
        String description,
        Supplier<Line> line,
        ElementRange selection,
        Hairpin.Kind kind,
        HairpinResolution expected
    ) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("selectionCases")
    void testARefusalOfTheRangeIsIneligibleAndAnotherHairpinInTheWayIsBlocked(SelectionCase testCase) {
        var line = testCase.line().get();

        assertThat(resolve(line, testCase.selection(), testCase.kind())).isEqualTo(testCase.expected());
    }

    static Stream<SelectionCase> selectionCases() {
        return Stream.of(
            new SelectionCase("one note and the rest after it is ineligible",
                () -> lineOf(quaver(), crotchetRest()), new ElementRange(0, 1), Hairpin.Kind.CRESCENDO, INELIGIBLE),
            new SelectionCase("a diminuendo sharing two elements with a crescendo is blocked",
                () -> lineWithCrescendo(OVERLAPPED, OVERLAPPING), OVERLAPPING, Hairpin.Kind.DIMINUENDO, BLOCKED),
            new SelectionCase("a selection a crescendo already covers leaves nothing to extend",
                () -> lineWithCrescendo(COVERING, COVERING), INSIDE, Hairpin.Kind.CRESCENDO, BLOCKED)
        );
    }

    private static HairpinResolution resolve(Line line, ElementRange selection, Hairpin.Kind kind) {
        var coordinator = mock(SelectionCoordinator.class);
        when(coordinator.getRange()).thenReturn(new Selection.Range(line, selection, selection.begin()));
        var operations = new MusicEditOperations(line.getSong(), coordinator);

        return operations.resolveHairpinAction(kind);
    }

    /** A line of quavers long enough for {@code extent}, holding a crescendo over {@code crescendo}. */
    private static Line lineWithCrescendo(ElementRange crescendo, ElementRange extent) {
        var line = lineOfQuavers(extent.end() + 1);
        crescendoOver(line, crescendo).addTo(line);
        return line;
    }

    private static Line lineOf(StaffElement... elements) {
        var line = detachedLine();

        for (var element : elements) {
            line.addElement(element);
        }

        return line;
    }
}
