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

package songscribe.layout.stacking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Line;
import songscribe.dom.StaffElement;
import songscribe.layout.ElementColumn;
import songscribe.layout.LayoutResult;
import songscribe.engraving.Staff;

class StackingContextTest extends UnitTest {

    // Tolerance for floating-point comparisons
    private static final double TOLERANCE = 1e-9;

    // Two distinct accumulation values that form a peak in the middle
    private static final double LOWER_VALUE_SS = Staff.STAFF_HALF_SS + 0.5;
    private static final double PEAK_VALUE_SS = Staff.STAFF_HALF_SS + 2.0;
    private static final double AFTER_PEAK_VALUE_SS = Staff.STAFF_HALF_SS + 1.0;

    // Accumulation values for lowestNoteBotSs (default = STAFF_HEIGHT_SS)
    private static final double LOWER_NOTE_BOT_SS = Staff.STAFF_HEIGHT_SS + 0.5;
    private static final double PEAK_NOTE_BOT_SS = Staff.STAFF_HEIGHT_SS + 2.0;
    private static final double AFTER_PEAK_NOTE_BOT_SS = Staff.STAFF_HEIGHT_SS + 1.0;

    private static ElementColumn mockColumn(StaffElement element) {
        var column = mock(ElementColumn.class);
        when(column.getElement()).thenReturn(element);
        return column;
    }

    private static StackingContext emptyContext() {
        return new StackingContext(
            List.of(),
            mock(Line.class),
            new LayoutResult.Builder());
    }

    // -------------------------------------------------------------------------
    // Row 11 — buildColumnMap: element→column mapping for each column
    // -------------------------------------------------------------------------

    @Test
    void testBuildColumnMapMapsEachElementToItsColumn() {
        var elementA = mock(StaffElement.class);
        var elementB = mock(StaffElement.class);
        var columnA = mockColumn(elementA);
        var columnB = mockColumn(elementB);

        var context = new StackingContext(
            List.of(columnA, columnB),
            mock(Line.class),
            new LayoutResult.Builder());

        var map = context.getColumnsByElement();

        assertThat(map.get(elementA)).isSameAs(columnA);
        assertThat(map.get(elementB)).isSameAs(columnB);
    }

    @Test
    void testBuildColumnMapContainsExactlyTheSuppliedColumns() {
        var elementA = mock(StaffElement.class);
        var elementB = mock(StaffElement.class);
        var columnA = mockColumn(elementA);
        var columnB = mockColumn(elementB);

        var context = new StackingContext(
            List.of(columnA, columnB),
            mock(Line.class),
            new LayoutResult.Builder());

        assertThat(context.getColumnsByElement()).hasSize(2);
    }

    // -------------------------------------------------------------------------
    // Row 12 — updateLowestNoteBotSs: retains MAX, not the last value
    // -------------------------------------------------------------------------

    @Test
    void testUpdateLowestNoteBotSsRetainsMaxAcrossAscendingThenDescendingFeed() {
        // Feed ascending then descending: max must be the peak, not the last value.
        var context = emptyContext();

        context.updateLowestNoteBotSs(LOWER_NOTE_BOT_SS);
        context.updateLowestNoteBotSs(PEAK_NOTE_BOT_SS);
        context.updateLowestNoteBotSs(AFTER_PEAK_NOTE_BOT_SS);

        assertThat(context.getLowestNoteBotSs())
            .isCloseTo(PEAK_NOTE_BOT_SS, within(TOLERANCE));
    }

    @Test
    void testUpdateLowestNoteBotSsIgnoresValuesBelowCurrent() {
        var context = emptyContext();

        context.updateLowestNoteBotSs(PEAK_NOTE_BOT_SS);
        context.updateLowestNoteBotSs(LOWER_NOTE_BOT_SS);

        assertThat(context.getLowestNoteBotSs())
            .isCloseTo(PEAK_NOTE_BOT_SS, within(TOLERANCE));
    }

    // -------------------------------------------------------------------------
    // Row 13 — updateBotContentExtentSs: retains MAX, not the last value
    // -------------------------------------------------------------------------

    @Test
    void testUpdateBotContentExtentSsRetainsMaxAcrossAscendingThenDescendingFeed() {
        var context = emptyContext();

        context.updateBotContentExtentSs(LOWER_VALUE_SS);
        context.updateBotContentExtentSs(PEAK_VALUE_SS);
        context.updateBotContentExtentSs(AFTER_PEAK_VALUE_SS);

        assertThat(context.getBotContentExtentSs())
            .isCloseTo(PEAK_VALUE_SS, within(TOLERANCE));
    }

    @Test
    void testUpdateBotContentExtentSsIgnoresValuesBelowCurrent() {
        var context = emptyContext();

        context.updateBotContentExtentSs(PEAK_VALUE_SS);
        context.updateBotContentExtentSs(LOWER_VALUE_SS);

        assertThat(context.getBotContentExtentSs())
            .isCloseTo(PEAK_VALUE_SS, within(TOLERANCE));
    }
}
