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

import module java.desktop;
// Disambiguates from org.w3c.dom.events.MouseEvent (java.xml module)
import java.awt.event.MouseEvent;

import org.jspecify.annotations.Nullable;

import songscribe.ui.component.ScoreView;

public abstract class Adjustment extends MouseAdapter {

    protected final ScoreView scoreView;
    protected boolean enabled = false;
    protected boolean startedDrag = false;
    @Nullable
    protected Point startPoint = null;
    protected final Point endPoint = new Point();
    protected final Point topLeftDragBounds = new Point();
    protected final Point bottomRightDragBounds = new Point();

    protected Adjustment(ScoreView scoreView) {
        this.scoreView = scoreView;
        scoreView.addMouseListener(this);
        scoreView.addMouseMotionListener(this);
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
        // Convert the view-pixel event point to document pixels once, at this input
        // boundary, so the subclasses' math against document-scale element positions
        // (getXOffsetPx, getLineWidthPx, ...) is zoom-independent.
        startPoint = scoreView.getViewScale().toDocumentPoint(e.getPoint());
        startedDrag();

        if (startedDrag) {
            scoreView.setDragDisabled(true);
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
            scoreView.setDragDisabled(false);
        }
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if (!enabled) {
            return;
        }

        if (startedDrag) {
            // Convert the view-pixel event point to document pixels so it shares the
            // coordinate space of the (document-scale) drag bounds and element positions.
            var documentPoint = scoreView.getViewScale().toDocumentPoint(e.getPoint());
            var x = documentPoint.x;

            if (x < topLeftDragBounds.x) {
                x = topLeftDragBounds.x;
            } else if (x >= bottomRightDragBounds.x) {
                x = bottomRightDragBounds.x - 1;
            }

            var y = documentPoint.y;

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
