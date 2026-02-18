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

package songscribe.ui.renderer;

import java.awt.Graphics2D;

import org.jetbrains.annotations.NotNull;

import songscribe.ui.layout.LayoutStylesheet;
import songscribe.ui.layout.Staff;

/**
 * Renders the 5 staff lines.
 * <p>
 * The staff is the fundamental background element that provides the
 * visual grid for note positioning.
 */
public class StaffRenderer extends BaseElementRenderer<Staff> {

    @Override
    protected void renderElement(
        @NotNull Staff element,
        @NotNull Graphics2D g2,
        @NotNull ElementRenderContext ctx
    ) {
        g2.setColor(STAFF_LINE_COLOR);
        g2.setStroke(STAFF_LINE_STROKE);

        int middleLineY = ctx.getMiddleLineY();
        int staffWidth = (int) element.getWidth();

        // Draw 5 staff lines (indices 0-4, middle is 2)
        // Index 0 = top line (F5), Index 4 = bottom line (E4)
        for (int i = 0; i < LayoutStylesheet.STAFF_LINE_COUNT; i++) {
            int y = staffLineToY(i, middleLineY);
            g2.drawLine(0, y, staffWidth, y);
        }
    }
}
