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

package songscribe.ui.component.score;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import javax.swing.JComponent;
import javax.swing.JToolTip;
import javax.swing.SwingUtilities;

/**
 * Where the tooltip of a lint mark goes. The hover tooltip and the callout are both placed here,
 * so a mark's tooltip shows at the same place whichever of the two shows it.
 * <p>
 * The rule, in this order:
 * <ol>
 *   <li>The tooltip goes below the item, its top on the item's bottom edge, when it fits on the
 *       usable screen there. Otherwise it goes above the item, its bottom on the item's top
 *       edge. Either way, its left side is on the item's left edge.</li>
 *   <li>The tooltip is pushed onto the usable screen: the screen less what the system keeps at
 *       its edges, such as the menu bar and the Dock. This is the area the tooltip manager keeps
 *       a tooltip inside, so the manager never moves a tooltip placed here.</li>
 * </ol>
 * {@link #arrange} is the rule as arithmetic, with no Swing; {@link #place} reads the screen,
 * builds the tooltip and measures it.
 */
final class LintToolTipPlacement {

    private LintToolTipPlacement() {
    }

    /**
     * A lint tooltip and where it goes.
     *
     * @param toolTip                the tooltip, with its text set
     * @param locationOnScreenViewPx its top-left corner, in screen coordinates
     */
    record Placed(JToolTip toolTip, Point locationOnScreenViewPx) {

        /**
         * Returns the tooltip's top-left corner in {@code component}'s coordinates.
         *
         * @param component a component on the screen
         * @return the corner, a new point
         */
        Point locationIn(Component component) {
            var location = new Point(locationOnScreenViewPx);
            SwingUtilities.convertPointFromScreen(location, component);
            return location;
        }
    }

    /**
     * What {@link #arrange} places, all in screen coordinates.
     *
     * @param itemOnScreenViewPx the item the mark is drawn on
     * @param toolTipSizeViewPx  the tooltip's size
     * @param usableScreenViewPx the usable screen, which must be non-empty
     */
    record Request(Rectangle itemOnScreenViewPx, Dimension toolTipSizeViewPx, Rectangle usableScreenViewPx) { }

    /**
     * Returns the tooltip of a mark with {@code text}, placed at the mark's item by the rule in
     * the class comment.
     *
     * @param itemBoundsViewPx the item the mark is drawn on, in {@code component}'s view pixels
     * @param component        the component the item is drawn in, which owns the tooltip
     * @param text             the tooltip's text
     * @return the tooltip, and its top-left corner on the screen
     */
    static Placed place(Rectangle itemBoundsViewPx, JComponent component, String text) {
        var itemOrigin = itemBoundsViewPx.getLocation();
        SwingUtilities.convertPointToScreen(itemOrigin, component);
        var itemOnScreenViewPx = new Rectangle(itemOrigin, itemBoundsViewPx.getSize());

        var toolTip = component.createToolTip();
        toolTip.setTipText(text);

        var location = arrange(new Request(
            itemOnScreenViewPx, toolTip.getPreferredSize(), usableScreenViewPx(component)));

        return new Placed(toolTip, location);
    }

    /**
     * Places a tooltip at an item by the rule in the class comment.
     *
     * @param request the item, the tooltip's size and the usable screen
     * @return the tooltip's top-left corner, in screen coordinates
     * @invariant the tooltip goes below the item exactly when its bottom, placed there, is at or
     *            above the usable screen's bottom; a tooltip that fits exactly goes below
     * @invariant below the item, the tooltip's top is the item's bottom; above it, the tooltip's
     *            bottom is the item's top; in both, the tooltip's left side is the item's left
     *            side; all before the push onto the usable screen
     * @invariant a tooltip no wider than the usable screen lies within it horizontally, and one
     *            no taller lies within it vertically; on an axis where the tooltip is larger than
     *            the usable screen, its top or left side is the usable screen's
     * @invariant on an axis where the unpushed tooltip already lies within the usable screen, it
     *            is not moved
     */
    static Point arrange(Request request) {
        var item = request.itemOnScreenViewPx();
        var size = request.toolTipSizeViewPx();
        var usable = request.usableScreenViewPx();
        var itemBottomYViewPx = item.y + item.height;
        var usableBottomYViewPx = usable.y + usable.height;
        var fitsBelow = itemBottomYViewPx + size.height <= usableBottomYViewPx;
        var unpushedYViewPx = fitsBelow ? itemBottomYViewPx : item.y - size.height;

        var xViewPx = pushOnto(item.x, usable.x, usable.x + usable.width - size.width);
        var yViewPx = pushOnto(unpushedYViewPx, usable.y, usableBottomYViewPx - size.height);

        return new Point(xViewPx, yViewPx);
    }

    /**
     * Returns {@code positionViewPx} pushed into {@code [minViewPx, maxViewPx]}, or
     * {@code minViewPx} when the range is empty because the tooltip is larger than the screen.
     */
    private static int pushOnto(int positionViewPx, int minViewPx, int maxViewPx) {
        return Math.max(minViewPx, Math.min(positionViewPx, maxViewPx));
    }

    /**
     * Returns the usable part of the screen {@code component} is on: the screen less what the
     * system keeps at its edges.
     */
    private static Rectangle usableScreenViewPx(Component component) {
        var configuration = component.getGraphicsConfiguration();
        var screenViewPx = configuration.getBounds();
        var screenInsets = Toolkit.getDefaultToolkit().getScreenInsets(configuration);

        return new Rectangle(
            screenViewPx.x + screenInsets.left,
            screenViewPx.y + screenInsets.top,
            screenViewPx.width - screenInsets.left - screenInsets.right,
            screenViewPx.height - screenInsets.top - screenInsets.bottom);
    }
}
