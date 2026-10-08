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

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.ToDoubleFunction;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;
import songscribe.dom.DynamicAttachment;
import songscribe.dom.Hairpin;
import songscribe.dom.Line;
import songscribe.dom.StaffElement;
import songscribe.dom.StaffElementRun.ElementRange;
import songscribe.engraving.HairpinMetrics;
import songscribe.layout.HairpinEndpoints.Endpoints;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static songscribe.dom.SongFactory.songOfQuavers;
import static songscribe.dom.SpanFactory.hairpinOver;

/**
 * Where {@link HairpinEndpoints#compute} puts the two tips of a wedge. Each tip takes the first
 * rule that applies, in this order: a text dynamic on the bound itself, a hairpin that meets this
 * one on that column, a text dynamic on the element outside the bound, and otherwise the
 * column's own edge. A wedge is at least {@link HairpinMetrics#MINIMUM_LENGTH_SS} long, except
 * that a right tip a dynamic placed is never lengthened: it moves only when it falls left of the
 * left tip, and then onto it.
 *
 * <p>Every column here has the same width and the same distance from the one before it, so a
 * tip's X says which rule placed it. An expectation compares the tip to the tip another
 * arrangement gives, or to the column, and never restates a padding formula.
 */
class HairpinEndpointsTest extends UnitTest {

    private static final int FIRST_LINE = 0;

    // The line: a hairpin that meets the wedge on its anchor, the element before the anchor, the
    // anchor, the end, the element after the end, and a hairpin that meets the wedge on its end.
    private static final int MEETING_BEFORE_INDEX = 0;
    private static final int BEFORE_ANCHOR_INDEX = 1;
    private static final int ANCHOR_INDEX = 2;
    private static final int END_INDEX = 3;
    private static final int AFTER_END_INDEX = 4;
    private static final int MEETING_AFTER_INDEX = 5;
    private static final int LINE_LENGTH = MEETING_AFTER_INDEX + 1;

    private static final double COLUMN_LEFT_EXTENT_SS = -0.5;

    /** A column pitch and width that leave the wedge longer than its minimum length. */
    private static final double WIDE_PITCH_SS = 2 * HairpinMetrics.MINIMUM_LENGTH_SS;
    private static final double WIDE_COLUMN_SS = HairpinMetrics.MINIMUM_LENGTH_SS;

    /** A column pitch and width that leave the wedge shorter than its minimum length. */
    private static final double SHORT_PITCH_SS = HairpinMetrics.MINIMUM_LENGTH_SS / 2;
    private static final double SHORT_COLUMN_SS = SHORT_PITCH_SS / 2;

    /**
     * A column pitch narrower than the clearance a dynamic keeps from a wedge's right tip, so a
     * dynamic on or just after the end puts that tip left of the anchor column.
     */
    private static final double CROWDED_PITCH_SS = HairpinMetrics.BOUND_PADDING_SS / 4;

    private static final double TOLERANCE_SS = 1.0e-9;

    /** What can sit beside the wedge and place one of its tips. */
    private enum Neighbor {
        DYNAMIC_ON_ANCHOR,
        HAIRPIN_ENDING_ON_ANCHOR,
        DYNAMIC_BEFORE_ANCHOR,
        DYNAMIC_ON_END,
        HAIRPIN_ANCHORED_ON_END,
        DYNAMIC_AFTER_END
    }

    /** The wedge, its line, and a stub column for every element of the line. */
    private record Fixture(Hairpin wedge, Line line, Map<StaffElement, ElementColumn> columns) {

        Endpoints endpoints() {
            var endpoints = HairpinEndpoints.compute(wedge, line, columns);

            assertThat(endpoints).isNotNull();

            return endpoints;
        }
    }

    private static Fixture fixtureOf(Set<Neighbor> present, double pitchSs, double columnSs) {
        var song = songOfQuavers(LINE_LENGTH);
        var line = song.getLine(FIRST_LINE);

        attachDynamic(present, Neighbor.DYNAMIC_ON_ANCHOR, line.getElement(ANCHOR_INDEX));
        attachDynamic(present, Neighbor.DYNAMIC_BEFORE_ANCHOR, line.getElement(BEFORE_ANCHOR_INDEX));
        attachDynamic(present, Neighbor.DYNAMIC_ON_END, line.getElement(END_INDEX));
        attachDynamic(present, Neighbor.DYNAMIC_AFTER_END, line.getElement(AFTER_END_INDEX));

        var wedge = song.withoutMutationTrackingResult(() -> {
            if (present.contains(Neighbor.HAIRPIN_ENDING_ON_ANCHOR)) {
                hairpinOver(line, Hairpin.Kind.DIMINUENDO, new ElementRange(MEETING_BEFORE_INDEX, ANCHOR_INDEX))
                    .addTo();
            }

            if (present.contains(Neighbor.HAIRPIN_ANCHORED_ON_END)) {
                hairpinOver(line, Hairpin.Kind.DIMINUENDO, new ElementRange(END_INDEX, MEETING_AFTER_INDEX))
                    .addTo();
            }

            var built = hairpinOver(line, Hairpin.Kind.CRESCENDO, new ElementRange(ANCHOR_INDEX, END_INDEX));
            built.addTo();

            return built;
        });

        var columns = new HashMap<StaffElement, ElementColumn>();

        for (var index = 0; index < LINE_LENGTH; index++) {
            var element = line.getElement(index);
            var extents = new ElementColumn.Extents(COLUMN_LEFT_EXTENT_SS, columnSs, columnSs, 0.0, 0.0);
            var column = new ElementColumn(element, extents, null, null);

            column.setXSs(index * pitchSs);
            columns.put(element, column);
        }

        return new Fixture(wedge, line, columns);
    }

    private static void attachDynamic(Set<Neighbor> present, Neighbor neighbor, StaffElement element) {
        if (present.contains(neighbor)) {
            element.addAttachment(new DynamicAttachment(DynamicAttachment.DynamicType.FORTE));
        }
    }

    private static Endpoints endpointsWith(Set<Neighbor> present) {
        return fixtureOf(present, WIDE_PITCH_SS, WIDE_COLUMN_SS).endpoints();
    }

    /**
     * The three rules that can place one tip, in precedence order, and how to read the tip.
     *
     * @param description   the tip, as the test's display name
     * @param onBound       the dynamic on the bound itself
     * @param meeting       the hairpin that meets the wedge on the bound
     * @param outsideBound  the dynamic on the element outside the bound
     * @param tip           reads this tip from the endpoints
     * @param towardWedge   {@code 1} when moving the tip toward the wedge is moving it right,
     *                      {@code -1} when it is moving it left
     */
    private record TipRules(
        String description,
        Neighbor onBound,
        Neighbor meeting,
        Neighbor outsideBound,
        ToDoubleFunction<Endpoints> tip,
        int towardWedge
    ) {}

    static Stream<TipRules> tipRules() {
        return Stream.of(
            new TipRules("the left tip",
                Neighbor.DYNAMIC_ON_ANCHOR, Neighbor.HAIRPIN_ENDING_ON_ANCHOR, Neighbor.DYNAMIC_BEFORE_ANCHOR,
                Endpoints::x1Ss, 1),
            new TipRules("the right tip",
                Neighbor.DYNAMIC_ON_END, Neighbor.HAIRPIN_ANCHORED_ON_END, Neighbor.DYNAMIC_AFTER_END,
                Endpoints::x2Ss, -1)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("tipRules")
    void testEachRulePlacesTheTipWhereNoOtherRuleDoes(TipRules rules) {
        var tips = Stream.of(
                EnumSet.noneOf(Neighbor.class),
                EnumSet.of(rules.onBound()),
                EnumSet.of(rules.meeting()),
                EnumSet.of(rules.outsideBound()))
            .map(present -> rules.tip().applyAsDouble(endpointsWith(present)))
            .toList();

        assertThat(tips).doesNotHaveDuplicates();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("tipRules")
    void testARuleHidesEveryRuleBelowIt(TipRules rules) {
        var withAll = rules.tip().applyAsDouble(
            endpointsWith(EnumSet.of(rules.onBound(), rules.meeting(), rules.outsideBound())));
        var onBoundOnly = rules.tip().applyAsDouble(endpointsWith(EnumSet.of(rules.onBound())));
        var meetingAndOutside = rules.tip().applyAsDouble(
            endpointsWith(EnumSet.of(rules.meeting(), rules.outsideBound())));
        var meetingOnly = rules.tip().applyAsDouble(endpointsWith(EnumSet.of(rules.meeting())));

        assertThat(withAll).isCloseTo(onBoundOnly, within(TOLERANCE_SS));
        assertThat(meetingAndOutside).isCloseTo(meetingOnly, within(TOLERANCE_SS));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("tipRules")
    void testADynamicOnTheBoundOrAMeetingHairpinPullsTheTipIntoTheWedge(TipRules rules) {
        var bare = rules.tip().applyAsDouble(endpointsWith(EnumSet.noneOf(Neighbor.class)));
        var onBound = rules.tip().applyAsDouble(endpointsWith(EnumSet.of(rules.onBound())));
        var meeting = rules.tip().applyAsDouble(endpointsWith(EnumSet.of(rules.meeting())));

        assertThat(rules.towardWedge() * (onBound - bare)).isPositive();
        assertThat(rules.towardWedge() * (meeting - bare)).isPositive();
    }

    @Test
    void testABareWedgeRunsFromTheAnchorColumnToPastTheEndNotehead() {
        var endpoints = endpointsWith(EnumSet.noneOf(Neighbor.class));
        var columns = endpoints.spanColumns();

        assertThat(endpoints.x1Ss()).isCloseTo(columns.anchorColumn().getXSs(), within(TOLERANCE_SS));
        assertThat(endpoints.x2Ss()).isCloseTo(
            columns.endColumn().getXSs() + columns.endColumn().getNoteheadWidthSs(), within(TOLERANCE_SS));
    }

    @Test
    void testAWedgeShorterThanTheMinimumLengthIsExtendedToIt() {
        var endpoints = fixtureOf(EnumSet.noneOf(Neighbor.class), SHORT_PITCH_SS, SHORT_COLUMN_SS).endpoints();

        assertThat(endpoints.widthSs()).isCloseTo(HairpinMetrics.MINIMUM_LENGTH_SS, within(TOLERANCE_SS));
    }

    /** The dynamics that can place the right tip: on the end element, and just after it. */
    static Stream<Neighbor> rightTipDynamics() {
        return Stream.of(Neighbor.DYNAMIC_ON_END, Neighbor.DYNAMIC_AFTER_END);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rightTipDynamics")
    void testARightTipPlacedByADynamicIsNotExtendedAndCollapsesOntoTheLeftTip(Neighbor dynamic) {
        var endpoints = fixtureOf(EnumSet.of(dynamic), CROWDED_PITCH_SS, SHORT_COLUMN_SS).endpoints();

        assertThat(endpoints.widthSs())
            .as("the dynamic's clearance puts the right tip left of the left tip, and the wedge "
                + "collapses to nothing rather than being lengthened to the minimum")
            .isCloseTo(0.0, within(TOLERANCE_SS));
    }
}
