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

package songscribe.ui.component.score;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Where {@link LintToolTipPlacement#arrange} puts a tooltip: below the item when it fits, above
 * when it does not, its left side on the item's left edge, pushed onto the usable screen.
 */
class LintToolTipPlacementTest extends UnitTest {

    private static final Rectangle USABLE_SCREEN = new Rectangle(0, 0, 1000, 800);

    private static final int USABLE_SCREEN_RIGHT_X = USABLE_SCREEN.x + USABLE_SCREEN.width;

    private static final int USABLE_SCREEN_BOTTOM_Y = USABLE_SCREEN.y + USABLE_SCREEN.height;

    private static final Dimension TOOLTIP_SIZE = new Dimension(200, 50);

    /** A tooltip wider than the usable screen. */
    private static final Dimension WIDE_TOOLTIP_SIZE = new Dimension(USABLE_SCREEN.width * 2, TOOLTIP_SIZE.height);

    private static final int ITEM_WIDTH = 40;

    private static final int ITEM_HEIGHT = 20;

    /** The left side of an item well inside the usable screen. */
    private static final int INNER_ITEM_X = 400;

    /** The top of an item well inside the usable screen. */
    private static final int INNER_ITEM_Y = 100;

    /** The top of an item whose tooltip, placed below it, ends exactly at the screen's bottom. */
    private static final int FITS_EXACTLY_ITEM_Y = USABLE_SCREEN_BOTTOM_Y - TOOLTIP_SIZE.height - ITEM_HEIGHT;

    /** The left side of an item whose right side is on the usable screen's right edge. */
    private static final int RIGHT_EDGE_ITEM_X = USABLE_SCREEN_RIGHT_X - ITEM_WIDTH;

    /** The left side of an item that is partly off the usable screen's left edge. */
    private static final int OFF_LEFT_EDGE_ITEM_X = USABLE_SCREEN.x - ITEM_WIDTH / 2;

    /**
     * One item and tooltip on the usable screen, and where the tooltip goes.
     *
     * @param description      the case, which doubles as the parameterized display name
     * @param item             the item the mark is drawn on
     * @param toolTipSize      the tooltip's size
     * @param expectedLocation the tooltip's top-left corner
     */
    private record PlacementCase(String description, Rectangle item, Dimension toolTipSize, Point expectedLocation) {

        @Override
        public String toString() {
            return description;
        }
    }

    private static Rectangle itemAt(int x, int y) {
        return new Rectangle(x, y, ITEM_WIDTH, ITEM_HEIGHT);
    }

    static Stream<PlacementCase> placementCases() {
        return Stream.of(
            new PlacementCase(
                "below the item when it fits, its left side on the item's left edge",
                itemAt(INNER_ITEM_X, INNER_ITEM_Y),
                TOOLTIP_SIZE,
                new Point(INNER_ITEM_X, INNER_ITEM_Y + ITEM_HEIGHT)),
            new PlacementCase(
                "below the item when it fits exactly",
                itemAt(INNER_ITEM_X, FITS_EXACTLY_ITEM_Y),
                TOOLTIP_SIZE,
                new Point(INNER_ITEM_X, FITS_EXACTLY_ITEM_Y + ITEM_HEIGHT)),
            new PlacementCase(
                "above the item when it does not fit below, its left side on the item's left edge",
                itemAt(INNER_ITEM_X, FITS_EXACTLY_ITEM_Y + 1),
                TOOLTIP_SIZE,
                new Point(INNER_ITEM_X, FITS_EXACTLY_ITEM_Y + 1 - TOOLTIP_SIZE.height)),
            new PlacementCase(
                "pushed left off the right edge of the screen",
                itemAt(RIGHT_EDGE_ITEM_X, INNER_ITEM_Y),
                TOOLTIP_SIZE,
                new Point(USABLE_SCREEN_RIGHT_X - TOOLTIP_SIZE.width, INNER_ITEM_Y + ITEM_HEIGHT)),
            new PlacementCase(
                "pushed right off the left edge of the screen",
                itemAt(OFF_LEFT_EDGE_ITEM_X, INNER_ITEM_Y),
                TOOLTIP_SIZE,
                new Point(USABLE_SCREEN.x, INNER_ITEM_Y + ITEM_HEIGHT)),
            new PlacementCase(
                "wider than the screen, it starts at the screen's left edge",
                itemAt(INNER_ITEM_X, INNER_ITEM_Y),
                WIDE_TOOLTIP_SIZE,
                new Point(USABLE_SCREEN.x, INNER_ITEM_Y + ITEM_HEIGHT))
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("placementCases")
    void testTheToolTipIsPlacedByTheRule(PlacementCase testCase) {
        var location = LintToolTipPlacement.arrange(
            new LintToolTipPlacement.Request(testCase.item(), testCase.toolTipSize(), USABLE_SCREEN));

        assertThat(location).isEqualTo(testCase.expectedLocation());
    }
}
