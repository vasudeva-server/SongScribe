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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;
import songscribe.dom.Hairpin;
import songscribe.dom.Line;
import songscribe.dom.StaffElementRun.ElementRange;
import songscribe.ui.selection.ElementSelection;
import songscribe.ui.selection.RangeQueries;
import songscribe.ui.selection.RangeQueries.HairpinResolution;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.LineSpans.SPAN_ADJACENCY_REACH;
import static songscribe.dom.SongFactory.lineOfQuavers;
import static songscribe.dom.SpanFactory.hairpinOver;
import static songscribe.dom.StaffElementFactory.crotchetRest;
import static songscribe.dom.StaffElementFactory.quaver;

/**
 * What the hairpin menu resolves to for a selection. A crescendo flush with the selection is
 * extended by the crescendo item, while the diminuendo item adds its own hairpin beside it, and a
 * selection resolves so wherever it stands on the line. A selection the hairpin rule refuses for
 * its own range is ineligible; one it refuses because another hairpin is in the way, or that a
 * same-kind hairpin already covers, is blocked.
 */
class MusicEditOperationsTest extends UnitTest {

    /** The width of every hairpin and selection in this class's fixtures: two notes. */
    private static final int NOTES_PER_HAIRPIN = 2;

    /** A selection that begins the line, where nothing comes before it for a hairpin to reach into. */
    private static final ElementRange AT_LINE_START = new ElementRange(0, NOTES_PER_HAIRPIN - 1);

    /** A selection with room for a hairpin on either side, clear of both ends of the line. */
    private static final ElementRange MID_LINE = new ElementRange(
        NOTES_PER_HAIRPIN + SPAN_ADJACENCY_REACH, 2 * NOTES_PER_HAIRPIN + SPAN_ADJACENCY_REACH - 1);

    private static final int LINE_LENGTH = MID_LINE.end() + SPAN_ADJACENCY_REACH + NOTES_PER_HAIRPIN;

    /**
     * One selection with a crescendo already on the line, within reach of it.
     *
     * @param description the case, as the test's display name
     * @param selection   the selected elements
     * @param crescendo   the crescendo already on the line
     */
    private record NeighborCase(String description, ElementRange selection, ElementRange crescendo) {}

    /** The crescendo that begins within reach of {@code selection}'s end. */
    private static ElementRange flushAfter(ElementRange selection) {
        var begin = selection.end() + SPAN_ADJACENCY_REACH;

        return new ElementRange(begin, begin + NOTES_PER_HAIRPIN - 1);
    }

    /** The crescendo that ends within reach of {@code selection}'s start. */
    private static ElementRange flushBefore(ElementRange selection) {
        var end = selection.begin() - SPAN_ADJACENCY_REACH;

        return new ElementRange(end - NOTES_PER_HAIRPIN + 1, end);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("neighborCases")
    void testACrescendoFlushWithTheSelectionExtendsWhileADiminuendoAddsOnTheSameLine(NeighborCase testCase) {
        var line = lineOfQuavers(LINE_LENGTH);
        hairpinOver(line, Hairpin.Kind.CRESCENDO, testCase.crescendo()).addTo();

        assertThat(resolve(line, testCase.selection(), Hairpin.Kind.CRESCENDO))
            .isInstanceOf(HairpinResolution.Extend.class);
        assertThat(resolve(line, testCase.selection(), Hairpin.Kind.DIMINUENDO))
            .isInstanceOf(HairpinResolution.Add.class);
    }

    static Stream<NeighborCase> neighborCases() {
        return Stream.of(
            new NeighborCase("a crescendo flush after a selection at the line start",
                AT_LINE_START, flushAfter(AT_LINE_START)),
            new NeighborCase("a crescendo flush after a selection mid-line",
                MID_LINE, flushAfter(MID_LINE)),
            new NeighborCase("a crescendo flush before a selection mid-line",
                MID_LINE, flushBefore(MID_LINE))
        );
    }

    @ParameterizedTest
    @EnumSource(Hairpin.Kind.class)
    void testWithNoHairpinNearbyEveryItemAddsOverTheSelection(Hairpin.Kind kind) {
        var line = lineOfQuavers(LINE_LENGTH);

        assertThat(resolve(line, AT_LINE_START, kind)).isInstanceOf(HairpinResolution.Add.class);
    }

    /** A crescendo that a diminuendo over {@link #OVERLAPPING} shares two elements with. */
    private static final ElementRange OVERLAPPED = new ElementRange(0, 2);

    private static final ElementRange OVERLAPPING = new ElementRange(1, 3);

    /** A crescendo that holds the whole of {@link #INSIDE}. */
    private static final ElementRange COVERING = new ElementRange(0, 3);

    private static final ElementRange INSIDE = new ElementRange(1, 2);

    /**
     * One selection, and the kind of resolution an item gets for it.
     *
     * @param description the case, as the test's display name
     * @param line        builds the line, with any crescendo already on it
     * @param selection   the selected elements
     * @param kind        the kind of the item asked
     * @param expected    the kind of resolution the item gets
     */
    private record SelectionCase(
        String description,
        Supplier<Line> line,
        ElementRange selection,
        Hairpin.Kind kind,
        Class<? extends HairpinResolution> expected
    ) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("selectionCases")
    void testARefusalOfTheRangeIsIneligibleAndAnotherHairpinInTheWayIsBlocked(SelectionCase testCase) {
        var line = testCase.line().get();

        assertThat(resolve(line, testCase.selection(), testCase.kind())).isInstanceOf(testCase.expected());
    }

    static Stream<SelectionCase> selectionCases() {
        return Stream.of(
            new SelectionCase("one note and the rest after it is ineligible",
                () -> lineOf(quaver(), crotchetRest()), new ElementRange(0, 1), Hairpin.Kind.CRESCENDO,
                HairpinResolution.Ineligible.class),
            new SelectionCase("a diminuendo sharing two elements with a crescendo is blocked",
                () -> lineWithCrescendo(OVERLAPPED, OVERLAPPING), OVERLAPPING, Hairpin.Kind.DIMINUENDO,
                HairpinResolution.Blocked.class),
            new SelectionCase("a selection a crescendo already covers leaves nothing to extend",
                () -> lineWithCrescendo(COVERING, COVERING), INSIDE, Hairpin.Kind.CRESCENDO,
                HairpinResolution.Blocked.class)
        );
    }

    private static HairpinResolution resolve(Line line, ElementRange selection, Hairpin.Kind kind) {
        return RangeQueries.resolveHairpin(new ElementSelection(line, selection), kind);
    }

    /** A line of quavers long enough for {@code extent}, holding a crescendo over {@code crescendo}. */
    private static Line lineWithCrescendo(ElementRange crescendo, ElementRange extent) {
        var line = lineOfQuavers(extent.end() + 1);
        hairpinOver(line, Hairpin.Kind.CRESCENDO, crescendo).addTo();
        return line;
    }
}
