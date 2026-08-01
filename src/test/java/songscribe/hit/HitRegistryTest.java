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

package songscribe.hit;

import java.awt.geom.Rectangle2D;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.ElementType;
import songscribe.dom.StaffElement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the resolution rules of {@link HitRegistry}: highest priority wins,
 * ties broken by smallest bounding-box area, hover queries restricted to the regions
 * that opted in, and unordered rectangle intersection.
 *
 * <p>Geometry is three concentric squares in layout space, so every region containing a
 * point also contains the smaller ones inside it:
 *
 * <pre>
 *   ┌──────────────────── outer ──────────────────┐
 *   │            ┌────── middle ──────┐           │
 *   │            │      ┌─ inner ─┐   │           │
 *   │            │      │    ✱    │   │  ✱ ✱      │
 *   │            │      └─────────┘   │           │
 *   │            └────────────────────┘           │
 *   └─────────────────────────────────────────────┘
 * </pre>
 */
class HitRegistryTest extends UnitTest {

    /** Outer square: origin at 0, so it contains every other fixture square. */
    private static final double OUTER_SIZE_SS = 10.0;

    /** Middle square, fully inside the outer one. */
    private static final double MIDDLE_ORIGIN_SS = 2.0;

    private static final double MIDDLE_SIZE_SS = 6.0;

    /** Inner square, fully inside the middle one. */
    private static final double INNER_ORIGIN_SS = 3.0;

    private static final double INNER_SIZE_SS = 2.0;

    /** A square far away from the other three, overlapping none of them. */
    private static final double FAR_ORIGIN_SS = 30.0;

    private static final double FAR_SIZE_SS = 2.0;

    /** Diagonal point inside all three concentric squares. */
    private static final double IN_ALL_SS = 4.0;

    /** Diagonal point inside the outer and middle squares only. */
    private static final double IN_MIDDLE_SS = 7.0;

    /** Diagonal point inside the outer square only. */
    private static final double IN_OUTER_SS = 9.0;

    /** Diagonal point outside every fixture square. */
    private static final double OUTSIDE_SS = 20.0;

    /** A small drag rectangle straddling the inner and outer squares. */
    private static final double DRAG_ORIGIN_SS = 4.0;

    private static final double DRAG_SIZE_SS = 2.0;

    private static final int FIRST_VERSE = 0;

    private final StaffElement note = new StaffElement(ElementType.CROTCHET);

    private final StaffElement otherNote = new StaffElement(ElementType.CROTCHET);

    private final HitTarget elementTarget = new HitTarget.Element(note);

    private final HitTarget otherElementTarget = new HitTarget.Element(otherNote);

    private final HitTarget lyricTarget = new HitTarget.Lyric(note, FIRST_VERSE);

    private final HitTarget staffLineTarget = new HitTarget.StaffLine();

    /** A square in layout space with its top-left corner on the diagonal. */
    private static Rectangle2D.Double squareSs(double originSs, double sizeSs) {
        return new Rectangle2D.Double(originSs, originSs, sizeSs, sizeSs);
    }

    private static Rectangle2D.Double outerSs() {
        return squareSs(0, OUTER_SIZE_SS);
    }

    private static Rectangle2D.Double middleSs() {
        return squareSs(MIDDLE_ORIGIN_SS, MIDDLE_SIZE_SS);
    }

    private static Rectangle2D.Double innerSs() {
        return squareSs(INNER_ORIGIN_SS, INNER_SIZE_SS);
    }

    @Nested
    class Priority {

        /**
         * Registers the three squares out of priority order — middle, inner, outer — so
         * that resolution has to both promote a better candidate and reject a worse one.
         */
        private HitRegistry concentricRegistry() {
            return HitRegistry.builder()
                .add(middleSs(), elementTarget, HitPriority.ELEMENT, false)
                .add(innerSs(), lyricTarget, HitPriority.LYRIC, false)
                .add(outerSs(), staffLineTarget, HitPriority.STAFF_LINE, false)
                .build();
        }

        @Test
        void testHitTestReturnsHighestPriorityRegionContainingPoint() {
            var registry = concentricRegistry();

            assertThat(registry.hitTest(IN_ALL_SS, IN_ALL_SS))
                .as("inside all three, LYRIC outranks ELEMENT and STAFF_LINE")
                .isEqualTo(lyricTarget);

            assertThat(registry.hitTest(IN_MIDDLE_SS, IN_MIDDLE_SS))
                .as("outside the lyric square, ELEMENT outranks STAFF_LINE")
                .isEqualTo(elementTarget);

            assertThat(registry.hitTest(IN_OUTER_SS, IN_OUTER_SS))
                .as("only the staff-line square contains this point")
                .isEqualTo(staffLineTarget);
        }

        @Test
        void testHitTestReturnsNullWhenNoRegionContainsPoint() {
            assertThat(concentricRegistry().hitTest(OUTSIDE_SS, OUTSIDE_SS)).isNull();
        }

        @Test
        void testHitTestBreaksPriorityTieBySmallestArea() {
            // The case the tiebreak exists for: a small region wholly inside a large one
            // at the same priority. Built both ways round, because insertion order must
            // carry no meaning.
            var smallLast = HitRegistry.builder()
                .add(outerSs(), elementTarget, HitPriority.ARTICULATION, false)
                .add(innerSs(), otherElementTarget, HitPriority.ARTICULATION, false)
                .build();

            var smallFirst = HitRegistry.builder()
                .add(innerSs(), otherElementTarget, HitPriority.ARTICULATION, false)
                .add(outerSs(), elementTarget, HitPriority.ARTICULATION, false)
                .build();

            assertThat(smallLast.hitTest(IN_ALL_SS, IN_ALL_SS)).isEqualTo(otherElementTarget);
            assertThat(smallFirst.hitTest(IN_ALL_SS, IN_ALL_SS)).isEqualTo(otherElementTarget);
        }
    }

    @Nested
    class Hover {

        @Test
        void testHitTestHoverIgnoresRegionsThatAreNotHoverTestable() {
            // The higher-priority region is the one that opted out, so the two queries
            // must disagree.
            var registry = HitRegistry.builder()
                .add(outerSs(), lyricTarget, HitPriority.LYRIC, false)
                .add(innerSs(), elementTarget, HitPriority.ELEMENT, true)
                .build();

            assertThat(registry.hitTest(IN_ALL_SS, IN_ALL_SS))
                .as("the full query sees both regions")
                .isEqualTo(lyricTarget);

            assertThat(registry.hitTestHover(IN_ALL_SS, IN_ALL_SS))
                .as("the hover query never sees the LYRIC region")
                .isEqualTo(elementTarget);

            assertThat(registry.hitTestHover(IN_OUTER_SS, IN_OUTER_SS))
                .as("only a non-hover-testable region contains this point")
                .isNull();
        }

        @Test
        void testHitTestHoverAppliesPriorityAndAreaRulesAmongHoverTestableRegions() {
            var registry = HitRegistry.builder()
                .add(outerSs(), staffLineTarget, HitPriority.STAFF_LINE, true)
                .add(middleSs(), elementTarget, HitPriority.LYRIC, true)
                .add(innerSs(), lyricTarget, HitPriority.LYRIC, true)
                .build();

            assertThat(registry.hitTestHover(IN_ALL_SS, IN_ALL_SS))
                .as("LYRIC beats STAFF_LINE, then the smaller LYRIC box wins the tie")
                .isEqualTo(lyricTarget);

            assertThat(registry.hitTestHover(IN_OUTER_SS, IN_OUTER_SS))
                .as("outside both LYRIC boxes, the STAFF_LINE region is the only hit")
                .isEqualTo(staffLineTarget);
        }
    }

    @Nested
    class Intersecting {

        private HitRegistry scatteredRegistry() {
            return HitRegistry.builder()
                .add(outerSs(), staffLineTarget, HitPriority.STAFF_LINE, false)
                .add(innerSs(), lyricTarget, HitPriority.LYRIC, true)
                .add(squareSs(FAR_ORIGIN_SS, FAR_SIZE_SS), elementTarget, HitPriority.ELEMENT, false)
                .build();
        }

        @Test
        void testIntersectingReturnsEveryOverlappingTargetAndOmitsTheRest() {
            var overlapping = scatteredRegistry()
                .intersecting(squareSs(DRAG_ORIGIN_SS, DRAG_SIZE_SS));

            // The contract is unordered, so membership is all that may be asserted.
            assertThat(overlapping).containsExactlyInAnyOrder(staffLineTarget, lyricTarget);
        }

        @Test
        void testIntersectingReturnsEmptyWhenRectangleOverlapsNothing() {
            var overlapping = scatteredRegistry()
                .intersecting(squareSs(OUTSIDE_SS, FAR_SIZE_SS));

            assertThat(overlapping).isEmpty();
        }
    }

    @Nested
    class Empty {

        @Test
        void testEmptyRegistryHasNoRegionsAndHitsNothing() {
            var registry = HitRegistry.EMPTY;

            assertThat(registry.regions()).isEmpty();
            assertThat(registry.hitTest(IN_ALL_SS, IN_ALL_SS)).isNull();
            assertThat(registry.hitTestHover(IN_ALL_SS, IN_ALL_SS)).isNull();
            assertThat(registry.intersecting(outerSs())).isEmpty();
        }

        @Test
        void testBuilderWithNoRegionsBuildsTheEmptyRegistry() {
            assertThat(HitRegistry.builder().build()).isSameAs(HitRegistry.EMPTY);
        }
    }

    @Test
    void testRegionsExposesWhatWasRegisteredAndIsUnmodifiable() {
        var registry = HitRegistry.builder()
            .add(innerSs(), lyricTarget, HitPriority.LYRIC, true)
            .build();

        assertThat(registry.regions())
            .containsExactly(new HitRegion(innerSs(), lyricTarget, HitPriority.LYRIC, true));
        assertThat(registry.regions()).isUnmodifiable();
    }
}
