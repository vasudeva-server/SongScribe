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
import songscribe.engraving.Staff;
import songscribe.font.DocumentFontsHolder;
import songscribe.layout.stacking.VerticalStackingCalculator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link VerticalStackingCalculator} — vertical layout pipeline.
 * <p>
 * All tests use an empty column list so the stacker tiers (NoteAttached, Structural,
 * System) have no elements to process; only the attribution path and the content-extent
 * computation are driven.
 */
class VerticalStackingCalculatorTest extends UnitTest {

    private static final Offset<Double> EPSILON = within(1e-10);
    private static final double LINE_WIDTH_SS = 100.0;

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
                .describedAs("attribution x must be staffRightSs - widthSs - ATTRIBUTION_RIGHT_MARGIN_SS")
                .isCloseTo(staffRightSs - widthSs - Attribution.ATTRIBUTION_RIGHT_MARGIN_SS, EPSILON);
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
                .isLessThan(-Staff.STAFF_HALF_SS);
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
    // Content-extent computation
    // -----------------------------------------------------------------------

    @Nested
    class ContentExtentComputation {

        @Test
        void testContentExtentsAreZeroForEmptyColumns() {
            // The calculator reports a line's *true* content reach, unfloored: with no elements
            // and no attribution nothing reaches past either staff edge, so both extents are 0.
            // The ledger-line floors live in LayoutResult's painted extents, not here —
            // re-introducing a floor at this stage would inflate every inter-line gap (refs #591).
            var builder = LayoutResult.builder();

            new VerticalStackingCalculator().calculate(
                List.of(),
                detachedLine(),
                builder,
                LINE_WIDTH_SS,
                mock(DocumentFontsHolder.class));

            var result = builder.build();
            assertThat(result.getContentAboveStaffSs())
                .describedAs("an empty line reaches nothing above its staff")
                .isCloseTo(0.0, EPSILON);
            assertThat(result.getContentBelowStaffSs())
                .describedAs("an empty line reaches nothing below its staff")
                .isCloseTo(0.0, EPSILON);
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
            var baseAboveSs = baseBuilder.build().getContentAboveStaffSs();

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

            var attrAboveSs = attrBuilder.build().getContentAboveStaffSs();
            assertThat(attrAboveSs)
                .describedAs("tall attribution must increase aboveStaffSs beyond baseline")
                .isGreaterThan(baseAboveSs);
        }

        @Test
        void testNegativeUserYOffsetGrowsAboveStaffSs() {
            // An upward (negative) user Y offset on the attribution must push aboveStaffSs
            // further than the same attribution with no offset.
            var lineWidth = 100.0;
            var upwardOffsetSs = -15.0;  // large upward shift

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
            var noOffsetAboveSs = noOffsetBuilder.build().getContentAboveStaffSs();

            // Same attribution shifted further up by a large negative Y offset
            var shiftedAttribution = new Attribution();
            shiftedAttribution.setDimensionsSs(15.0, 5.0);
            shiftedAttribution.setUserYOffsetSs(upwardOffsetSs);

            var shiftedBuilder = LayoutResult.builder();
            new VerticalStackingCalculator().calculate(
                List.of(),
                detachedLine(),
                shiftedBuilder,
                lineWidth,
                mock(DocumentFontsHolder.class),
                shiftedAttribution);
            var shiftedAboveSs = shiftedBuilder.build().getContentAboveStaffSs();

            // Exact, not merely greater: the offset is baked into the layout by
            // applyManualOffsets before the band is measured, so re-adding it would
            // silently reserve twice the shift and only an exact assertion catches that.
            assertThat(shiftedAboveSs - noOffsetAboveSs)
                .describedAs("upward Y offset must grow aboveStaffSs by exactly the shift")
                .isCloseTo(-upwardOffsetSs, EPSILON);
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
            var noOffsetAboveSs = noOffsetBuilder.build().getContentAboveStaffSs();

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
            var shiftedAboveSs = shiftedBuilder.build().getContentAboveStaffSs();

            assertThat(shiftedAboveSs)
                .describedAs("downward Y offset must not grow aboveStaffSs beyond the natural position")
                .isLessThanOrEqualTo(noOffsetAboveSs + EPSILON.value);
        }
    }

    // -----------------------------------------------------------------------
    // Empty-line attribution (first line with no musical columns) — refs #616
    // -----------------------------------------------------------------------

    @Nested
    class EmptyLineAttribution {

        private static final double ATTRIBUTION_WIDTH_SS = 15.0;
        private static final double ATTRIBUTION_HEIGHT_SS = 5.0;
        private static final double UPWARD_OFFSET_SS = -15.0;
        private static final double DOWNWARD_OFFSET_SS = 5.0;

        private static Attribution attribution(double heightSs, double userYOffsetSs) {
            var attribution = new Attribution();
            attribution.setDimensionsSs(ATTRIBUTION_WIDTH_SS, heightSs);
            attribution.setUserYOffsetSs(userYOffsetSs);
            return attribution;
        }

        /**
         * Stacks {@code attribution} over a line with no columns — the shared path an empty
         * line takes, with no empty-specific branch behind it (refs #630).
         */
        private static double stackOnEmptyLine(
            Attribution attribution, LayoutResult.Builder builder) {

            new VerticalStackingCalculator().calculate(
                List.of(),
                detachedLine(),
                builder,
                LINE_WIDTH_SS,
                mock(DocumentFontsHolder.class),
                attribution);

            return builder.build().getContentAboveStaffSs();
        }

        @Test
        void testUpwardOffsetIsBakedIntoTheDecorationLayout() {
            // The renderer paints DecorationLayout.ySs() verbatim, so a dragged attribution
            // only moves if the offset is applied here. Without it the band still grows,
            // leaving the attribution pinned in place under a widening gap.
            var noOffset = attribution(ATTRIBUTION_HEIGHT_SS, 0.0);
            var noOffsetBuilder = LayoutResult.builder();
            stackOnEmptyLine(noOffset, noOffsetBuilder);
            var naturalYSs = require(
                noOffsetBuilder.getDecorationLayout(noOffset), "natural layout").ySs();

            var shifted = attribution(ATTRIBUTION_HEIGHT_SS, UPWARD_OFFSET_SS);
            var shiftedBuilder = LayoutResult.builder();
            stackOnEmptyLine(shifted, shiftedBuilder);
            var shiftedYSs = require(
                shiftedBuilder.getDecorationLayout(shifted), "shifted layout").ySs();

            assertThat(shiftedYSs - naturalYSs)
                .describedAs("the painted attribution must move by exactly the user's drag")
                .isCloseTo(UPWARD_OFFSET_SS, EPSILON);
        }

        @Test
        void testUpwardOffsetGrowsAboveStaffSsByExactlyTheShift() {
            var noOffsetAboveSs = stackOnEmptyLine(
                attribution(ATTRIBUTION_HEIGHT_SS, 0.0), LayoutResult.builder());

            var shiftedAboveSs = stackOnEmptyLine(
                attribution(ATTRIBUTION_HEIGHT_SS, UPWARD_OFFSET_SS), LayoutResult.builder());

            assertThat(shiftedAboveSs - noOffsetAboveSs)
                .describedAs("band must grow by exactly the shift, not twice it")
                .isCloseTo(-UPWARD_OFFSET_SS, EPSILON);
        }

        @Test
        void testDownwardOffsetDoesNotGrowAboveStaffSs() {
            var noOffsetAboveSs = stackOnEmptyLine(
                attribution(ATTRIBUTION_HEIGHT_SS, 0.0), LayoutResult.builder());

            var shiftedAboveSs = stackOnEmptyLine(
                attribution(ATTRIBUTION_HEIGHT_SS, DOWNWARD_OFFSET_SS), LayoutResult.builder());

            assertThat(shiftedAboveSs)
                .describedAs("a downward drag must not reserve extra room above the staff")
                .isLessThanOrEqualTo(noOffsetAboveSs + EPSILON.value);
        }

        @Test
        void testTallerAttributionReservesMoreRoom() {
            var shortAboveSs = stackOnEmptyLine(
                attribution(ATTRIBUTION_HEIGHT_SS, 0.0), LayoutResult.builder());

            var tallAboveSs = stackOnEmptyLine(
                attribution(ATTRIBUTION_HEIGHT_SS * 2, 0.0), LayoutResult.builder());

            assertThat(tallAboveSs - shortAboveSs)
                .describedAs("the extra reserved room must match the extra height")
                .isCloseTo(ATTRIBUTION_HEIGHT_SS, EPSILON);
        }

        @Test
        void testZeroDimensionAttributionWithUpwardOffsetClampsToZero() {
            // A stored drag can outlive the measured dimensions (empty attribution text, or a
            // pane not yet measured). stackAttribution then writes no layout, so the null guard
            // is live and the clamp is the only thing keeping the band non-negative.
            var unmeasured = new Attribution();
            unmeasured.setUserYOffsetSs(UPWARD_OFFSET_SS);

            var aboveStaffSs = stackOnEmptyLine(unmeasured, LayoutResult.builder());

            assertThat(aboveStaffSs)
                .describedAs("an unmeasured attribution must reserve nothing, not a negative band")
                .isCloseTo(0.0, EPSILON);
        }
    }
}
