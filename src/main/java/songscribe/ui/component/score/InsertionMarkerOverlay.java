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

import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;

import org.jspecify.annotations.Nullable;

import songscribe.dom.ElementType;
import songscribe.engraving.StaffPosition;
import songscribe.ui.component.ScoreView;
import songscribe.util.GraphicUtils;
import songscribe.util.GraphicsState;

/**
 * The insertion-point marker: a single rounded vertical line spanning every staff position a
 * note could occupy, shown at the line and index {@code InsertionPointMode} is tracking as the
 * placement target.
 * <p>
 * Exactly one instance exists for the lifetime of the owning {@link ScoreView}; it is retargeted
 * and shown or hidden by {@code InsertionPointMode}, never recreated.
 */
public final class InsertionMarkerOverlay extends LineOverlayComponent {

    private static final double INSERTION_POINT_THICKNESS_SS = 0.25;

    /**
     * Ranges from 0 to elementCount inclusive; null when nothing is tracked. Meaningless while
     * {@link #getTargetLine()} is null.
     */
    private @Nullable Integer targetIndex = null;

    public InsertionMarkerOverlay(OverlayHost host) {
        super(host);
    }

    /**
     * Safe to call every time the tracked insertion point changes, including when only the index
     * changes on the same line.
     */
    public void setTarget(LineComponent line, int index) {
        targetIndex = index;

        if (line == getTargetLine()) {
            inkDidChange();
        } else {
            setTargetLine(line);
        }
    }

    public void clearTarget() {
        targetIndex = null;
        setTargetLine(null);
    }

    @Override
    protected @Nullable Rectangle2D getInkBoundsSs() {
        var line = getTargetLine();

        if (line == null || targetIndex == null) {
            return null;
        }

        var domLine = line.getLine();
        var layoutResult = line.getLayoutResult();

        if (domLine == null || layoutResult == null) {
            return null;
        }

        // betweenElementsOnly=true: a placement never snaps onto an existing element's own
        // position, so mouseXSs is ignored.
        var previewElement = ElementType.CROTCHET.newInstance();
        // Center the marker on the head the insertion X was computed for, measured per type rather
        // than with the black-notehead constant.
        var xSs = layoutResult.calculateInsertionXSs(targetIndex, 0, previewElement, domLine, true)
            + previewElement.getGlyphWidthSs() / 2;

        var middleLineYSs = line.getMiddleLineYSs();
        var topYSs = middleLineYSs + StaffPosition.MIN.toSs();
        var bottomYSs = middleLineYSs + StaffPosition.MAX.toSs();
        var halfThicknessSs = INSERTION_POINT_THICKNESS_SS / 2;

        return new Rectangle2D.Double(
            xSs - halfThicknessSs, topYSs, INSERTION_POINT_THICKNESS_SS, bottomYSs - topYSs);
    }

    /**
     * Draws from the bounds this overlay was sized to, so the geometry can never disagree with
     * them; the cached rectangle is current here.
     */
    @Override
    protected void renderOverlay(Graphics2D g2) {
        var boundsSs = getLastInkBoundsSs();

        if (boundsSs == null) {
            return;
        }

        var xSs = boundsSs.getCenterX();
        var topYSs = boundsSs.getMinY();
        var bottomYSs = boundsSs.getMaxY();

        try (var _ = GraphicsState.save(g2, GraphicsState.Property.COLOR)) {
            g2.setColor(ScoreView.getPreviewElementColor());
            GraphicUtils.drawRoundedLine(g2, xSs, topYSs, xSs, bottomYSs, INSERTION_POINT_THICKNESS_SS);
        }
    }
}
