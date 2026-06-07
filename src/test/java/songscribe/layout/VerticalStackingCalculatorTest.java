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

import java.util.List;

import org.assertj.core.data.Offset;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Attribution;
import songscribe.font.DocumentFontsHolder;
import songscribe.layout.stacking.VerticalStackingCalculator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link VerticalStackingCalculator} — vertical layout pipeline.
 * <p>
 * All tests use an empty column list so the stacker tiers (NoteAttached, Structural,
 * System) have no elements to process; only the attribution path and the line-height
 * computation are driven.
 */
class VerticalStackingCalculatorTest extends UnitTest {

    private static final Offset<Double> EPSILON = within(1e-10);

    // Staff geometry constants for expected-value calculations
    private static final double STAFF_HEIGHT_SS = StaffExtents.STAFF_HEIGHT_SS;         // 4.0
    private static final double STAFF_HALF_SS = StaffExtents.STAFF_HALF_SS;             // 2.0
    private static final double MIN_ABOVE_STAFF_SS = StaffExtents.MIN_ABOVE_STAFF_SS;
    private static final double MIN_BELOW_STAFF_SS = StaffExtents.MIN_BELOW_STAFF_SS;
    private static final double INTER_LINE_MARGIN_SS = SongLayoutMetricsBuilder.INTER_LINE_MARGIN_SS;

    /** Asserts non-null and narrows the type so NullAway is satisfied on the caller. */
    @SuppressWarnings("NullAway")
    private static <T> T require(@Nullable T value, String description) {
        assertThat(value).describedAs(description).isNotNull();
        return value;
    }

    // -----------------------------------------------------------------------
    // Attribution stacking
    // -----------------------------------------------------------------------

    @Nested
    class StackAttribution {

        @Test
        void testAttributionWithNonZeroDimensionsReceivesDecorationLayout() {
            // stackAttribution should add a DecorationLayout keyed by the attribution
            // when its dimensions are non-zero.
            var attribution = new Attribution();
            var widthSs = 15.0;
            var heightSs = 3.0;
            attribution.setDimensionsSs(widthSs, heightSs);

            var staffRightSs = 100.0;
            var builder = LayoutResult.builder();

            new VerticalStackingCalculator().calculate(
                List.of(),
                detachedLine(),
                builder,
                staffRightSs,
                mock(DocumentFontsHolder.class),
                attribution);

            var layout = builder.getDecorationLayout(attribution);
            assertThat(layout)
                .describedAs("attribution with non-zero dimensions must be stacked")
                .isNotNull();
        }

        @Test
        void testAttributionIsRightAlignedToStaffRightEdge() {
            // stackAttribution right-aligns the attribution: xSs = staffRightSs - widthSs.
            var attribution = new Attribution();
            var widthSs = 15.0;
            var heightSs = 3.0;
            attribution.setDimensionsSs(widthSs, heightSs);

            var staffRightSs = 100.0;
            var builder = LayoutResult.builder();

            new VerticalStackingCalculator().calculate(
                List.of(),
                detachedLine(),
                builder,
                staffRightSs,
                mock(DocumentFontsHolder.class),
                attribution);

            var layout = require(builder.getDecorationLayout(attribution), "attribution layout");
            assertThat(layout.xSs())
                .describedAs("attribution x must be staffRightSs - widthSs")
                .isCloseTo(staffRightSs - widthSs, EPSILON);
        }

        @Test
        void testAttributionLayoutPreservesWidthAndHeight() {
            // The DecorationLayout must record the same width and height as the attribution.
            var attribution = new Attribution();
            var widthSs = 15.0;
            var heightSs = 3.0;
            attribution.setDimensionsSs(widthSs, heightSs);

            var builder = LayoutResult.builder();

            new VerticalStackingCalculator().calculate(
                List.of(),
                detachedLine(),
                builder,
                100.0,
                mock(DocumentFontsHolder.class),
                attribution);

            var layout = require(builder.getDecorationLayout(attribution), "attribution layout");
            assertThat(layout.widthSs()).isCloseTo(widthSs, EPSILON);
            assertThat(layout.heightSs()).isCloseTo(heightSs, EPSILON);
        }

        @Test
        void testAttributionIsStackedAboveStaff() {
            // The attribution must be above the staff top (y < -STAFF_HALF_SS in the
            // stacking coordinate system where middle staff line = 0).
            var attribution = new Attribution();
            attribution.setDimensionsSs(15.0, 3.0);

            var builder = LayoutResult.builder();

            new VerticalStackingCalculator().calculate(
                List.of(),
                detachedLine(),
                builder,
                100.0,
                mock(DocumentFontsHolder.class),
                attribution);

            var layout = require(builder.getDecorationLayout(attribution), "attribution layout");
            assertThat(layout.ySs())
                .describedAs("attribution top Y must be above the staff top")
                .isLessThan(-STAFF_HALF_SS);
        }

        @Test
        void testAttributionMarginIsPreservedInLayout() {
            // stackAbove writes the marginSs into DecorationLayout; must match
            // Attribution.ATTRIBUTION_MARGIN_BOTTOM_SS.
            var attribution = new Attribution();
            attribution.setDimensionsSs(15.0, 3.0);

            var builder = LayoutResult.builder();

            new VerticalStackingCalculator().calculate(
                List.of(),
                detachedLine(),
                builder,
                100.0,
                mock(DocumentFontsHolder.class),
                attribution);

            var layout = require(builder.getDecorationLayout(attribution), "attribution layout");
            assertThat(layout.marginSs())
                .isCloseTo(Attribution.ATTRIBUTION_MARGIN_BOTTOM_SS, EPSILON);
        }
    }

    // -----------------------------------------------------------------------
    // Line-height computation
    // -----------------------------------------------------------------------

    @Nested
    class LineHeightComputation {

        @Test
        void testLineHeightAtLeastMinimumForEmptyColumns() {
            // With no elements and no attribution, the computed lineHeightSs must be at
            // least STAFF_HEIGHT_SS + MIN_ABOVE_STAFF_SS + MIN_BELOW_STAFF_SS + INTER_LINE_MARGIN_SS.
            var expectedMinHeight = STAFF_HEIGHT_SS
                + MIN_ABOVE_STAFF_SS
                + MIN_BELOW_STAFF_SS
                + INTER_LINE_MARGIN_SS;

            var builder = LayoutResult.builder();

            new VerticalStackingCalculator().calculate(
                List.of(),
                detachedLine(),
                builder,
                100.0,
                mock(DocumentFontsHolder.class));

            var result = builder.build();
            assertThat(result.getLineHeightSs())
                .describedAs("line height must be at least the minimum")
                .isGreaterThanOrEqualTo(expectedMinHeight - EPSILON.value);
        }

        @Test
        void testAboveStaffSsAtLeastMinimumForEmptyColumns() {
            // With no elements, aboveStaffSs must be at least MIN_ABOVE_STAFF_SS.
            var builder = LayoutResult.builder();

            new VerticalStackingCalculator().calculate(
                List.of(),
                detachedLine(),
                builder,
                100.0,
                mock(DocumentFontsHolder.class));

            var result = builder.build();
            assertThat(result.getAboveStaffSs())
                .isGreaterThanOrEqualTo(MIN_ABOVE_STAFF_SS - EPSILON.value);
        }

        @Test
        void testAttributionGrowsAboveStaffSsWhenLargerThanMinimum() {
            // A tall attribution above a cleared staff must increase aboveStaffSs
            // beyond its no-attribution baseline.
            var lineWidth = 100.0;

            // First compute baseline without attribution
            var baseBuilder = LayoutResult.builder();
            new VerticalStackingCalculator().calculate(
                List.of(),
                detachedLine(),
                baseBuilder,
                lineWidth,
                mock(DocumentFontsHolder.class));
            var baseAboveSs = baseBuilder.build().getAboveStaffSs();

            // Now run with a tall attribution
            var tallAttribution = new Attribution();
            tallAttribution.setDimensionsSs(15.0, 20.0);  // 20ss — very tall

            var attrBuilder = LayoutResult.builder();
            new VerticalStackingCalculator().calculate(
                List.of(),
                detachedLine(),
                attrBuilder,
                lineWidth,
                mock(DocumentFontsHolder.class),
                tallAttribution);

            var attrAboveSs = attrBuilder.build().getAboveStaffSs();
            assertThat(attrAboveSs)
                .describedAs("tall attribution must increase aboveStaffSs beyond baseline")
                .isGreaterThan(baseAboveSs);
        }

        @Test
        void testNegativeUserYOffsetGrowsAboveStaffSs() {
            // An upward (negative) user Y offset on the attribution must push aboveStaffSs
            // further than the same attribution with no offset.
            var lineWidth = 100.0;

            // Attribution with no Y offset
            var noOffsetAttribution = new Attribution();
            noOffsetAttribution.setDimensionsSs(15.0, 5.0);

            var noOffsetBuilder = LayoutResult.builder();
            new VerticalStackingCalculator().calculate(
                List.of(),
                detachedLine(),
                noOffsetBuilder,
                lineWidth,
                mock(DocumentFontsHolder.class),
                noOffsetAttribution);
            var noOffsetAboveSs = noOffsetBuilder.build().getAboveStaffSs();

            // Same attribution shifted further up by a large negative Y offset
            var shiftedAttribution = new Attribution();
            shiftedAttribution.setDimensionsSs(15.0, 5.0);
            shiftedAttribution.setUserYOffsetSs(-15.0);  // large upward shift

            var shiftedBuilder = LayoutResult.builder();
            new VerticalStackingCalculator().calculate(
                List.of(),
                detachedLine(),
                shiftedBuilder,
                lineWidth,
                mock(DocumentFontsHolder.class),
                shiftedAttribution);
            var shiftedAboveSs = shiftedBuilder.build().getAboveStaffSs();

            assertThat(shiftedAboveSs)
                .describedAs("upward Y offset must grow aboveStaffSs")
                .isGreaterThan(noOffsetAboveSs);
        }

        @Test
        void testPositiveUserYOffsetDoesNotGrowAboveStaffSs() {
            // A downward (positive) user Y offset must not enlarge aboveStaffSs beyond
            // the naturally-stacked position — the branch that grows it only fires when
            // userYOffsetSs < 0.
            var lineWidth = 100.0;

            // Attribution with no Y offset
            var noOffsetAttribution = new Attribution();
            noOffsetAttribution.setDimensionsSs(15.0, 5.0);

            var noOffsetBuilder = LayoutResult.builder();
            new VerticalStackingCalculator().calculate(
                List.of(),
                detachedLine(),
                noOffsetBuilder,
                lineWidth,
                mock(DocumentFontsHolder.class),
                noOffsetAttribution);
            var noOffsetAboveSs = noOffsetBuilder.build().getAboveStaffSs();

            // Same attribution shifted down
            var shiftedAttribution = new Attribution();
            shiftedAttribution.setDimensionsSs(15.0, 5.0);
            shiftedAttribution.setUserYOffsetSs(5.0);  // downward — should not grow above

            var shiftedBuilder = LayoutResult.builder();
            new VerticalStackingCalculator().calculate(
                List.of(),
                detachedLine(),
                shiftedBuilder,
                lineWidth,
                mock(DocumentFontsHolder.class),
                shiftedAttribution);
            var shiftedAboveSs = shiftedBuilder.build().getAboveStaffSs();

            assertThat(shiftedAboveSs)
                .describedAs("downward Y offset must not grow aboveStaffSs beyond the natural position")
                .isLessThanOrEqualTo(noOffsetAboveSs + EPSILON.value);
        }
    }
}
