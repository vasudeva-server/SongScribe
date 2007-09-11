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
package songscribe.ui.adjustment;

import java.awt.*;
import java.awt.event.*;

import songscribe.ui.component.Score;

public abstract class Adjustment extends MouseAdapter {

    protected final Score score;
    protected boolean enabled = false;
    protected boolean startedDrag = false;
    protected Point startPoint = null;
    protected final Point endPoint = new Point();
    protected final Point topLeftDragBounds = new Point();
    protected final Point bottomRightDragBounds = new Point();

    protected Adjustment(Score score) {
        this.score = score;
        score.addMouseListener(this);
        score.addMouseMotionListener(this);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public void mousePressed(MouseEvent e) {
        if (!enabled) {
            return;
        }

        startedDrag = true;
        startPoint = e.getPoint();
        startedDrag();

        if (startedDrag) {
            score.setDragDisabled(true);
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if (!enabled) {
            return;
        }

        if (startedDrag) {
            startedDrag = false;
            finishedDrag();
            score.setDragDisabled(false);
        }
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if (!enabled) {
            return;
        }

        if (startedDrag) {
            var x = e.getX();

            if (x < topLeftDragBounds.x) {
                x = topLeftDragBounds.x;
            } else if (x >= bottomRightDragBounds.x) {
                x = bottomRightDragBounds.x - 1;
            }

            var y = e.getY();

            if (y < topLeftDragBounds.y) {
                y = topLeftDragBounds.y;
            } else if (y >= bottomRightDragBounds.y) {
                y = bottomRightDragBounds.y - 1;
            }

            endPoint.setLocation(x, y);
            drag();
        }
    }

    protected abstract void startedDrag();

    protected abstract void drag();

    protected abstract void finishedDrag();

    public abstract void repaint(Graphics2D g2);
}
