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

import module java.desktop;

import org.jspecify.annotations.Nullable;

import songscribe.dom.ElementType;
import songscribe.engraving.SMuFLConstants;
import songscribe.engraving.Staff;
import songscribe.ui.component.ScoreView;
import songscribe.util.GraphicUtils;
import songscribe.util.GraphicsState;

/**
 * The paste-mode insertion-point marker: a single rounded vertical line spanning every staff
 * position a note could occupy, shown at the line and index {@code PasteModeManager} is
 * currently tracking as the placement target.
 * <p>
 * Exactly one instance exists for the lifetime of the owning {@link ScoreView}; it is retargeted
 * and shown or hidden by {@code PasteModeManager} rather than being recreated. Its height is
 * identical on every line — it is bounded by the compile-time constants
 * {@link Staff#MIN_STAFF_POSITION_SP} and {@link Staff#MAX_STAFF_POSITION_SP} — so only its x
 * position and the zoom-driven scale ever change.
 */
public final class InsertionMarkerOverlay extends LineOverlayComponent {

    /** Thickness of the paste-mode insertion-point marker, in staff spaces. */
    private static final double INSERTION_POINT_THICKNESS_SS = 0.25;

    /**
     * The tracked insertion index (0 to elementCount inclusive), or -1 when nothing is tracked.
     * Meaningless while {@link #getTargetLine()} is null.
     */
    private int targetIndex = -1;

    public InsertionMarkerOverlay(OverlayHost host) {
        super(host);
    }

    /**
     * Retargets this marker to {@code line} and {@code index}, or hides it when {@code line} is
     * null. Safe to call every time the tracked insertion point changes, including when only the
     * index changes on the same line — the base class only recomputes bounds when the line moves
     * or {@link #inkDidChange()} is called, and a same-line retarget needs the latter since the
     * line reference itself does not change.
     */
    public void setTarget(@Nullable LineComponent line, int index) {
        targetIndex = index;

        if (line == getTargetLine()) {
            inkDidChange();
        } else {
            setTargetLine(line);
        }
    }

    /**
     * The marker's extent: vertically every staff position a note could occupy (including
     * ledger-line room), horizontally the insertion x — a crotchet stands in for the extents
     * {@code calculateInsertionXSs} needs for after-last spacing, since the clipboard fragment's
     * actual shape doesn't matter here, only a plausible notehead width — plus half a notehead
     * width so the marker reads as "before the next element" rather than centered on the
     * notehead of whatever would be inserted, widened by half the marker's own thickness on each
     * side.
     */
    @Override
    protected @Nullable Rectangle2D getInkBoundsSs() {
        var line = getTargetLine();

        if (line == null || targetIndex < 0) {
            return null;
        }

        var domLine = line.getLine();
        var layoutResult = line.getLayoutResult();

        if (domLine == null || layoutResult == null) {
            return null;
        }

        // betweenElementsOnly=true: paste placement never snaps onto an existing element's own
        // position, so mouseXSs is ignored.
        var previewElement = ElementType.CROTCHET.newInstance();
        var xSs = layoutResult.calculateInsertionXSs(targetIndex, 0, previewElement, domLine, true)
            + SMuFLConstants.NOTE_HEAD_WIDTH_SS / 2;

        var middleLineYSs = line.getMiddleLineYSs();
        var topYSs = middleLineYSs + Staff.spToSs(Staff.MIN_STAFF_POSITION_SP);
        var bottomYSs = middleLineYSs + Staff.spToSs(Staff.MAX_STAFF_POSITION_SP);
        var halfThicknessSs = INSERTION_POINT_THICKNESS_SS / 2;

        return new Rectangle2D.Double(
            xSs - halfThicknessSs, topYSs, INSERTION_POINT_THICKNESS_SS, bottomYSs - topYSs);
    }

    /**
     * Draws the marker as a single rounded line, reusing {@link #getInkBoundsSs()} rather than
     * recomputing the insertion x independently, so the drawn geometry can never disagree with
     * the bounds it was sized to.
     */
    @Override
    protected void renderOverlay(Graphics2D g2) {
        var boundsSs = getInkBoundsSs();

        if (boundsSs == null) {
            return;
        }

        var xSs = boundsSs.getCenterX();
        var topYSs = boundsSs.getMinY();
        var bottomYSs = boundsSs.getMaxY();

        try (var ignored = GraphicsState.save(g2, GraphicsState.Property.COLOR)) {
            g2.setColor(ScoreView.getPreviewElementColor());
            GraphicUtils.drawRoundedLine(g2, xSs, topYSs, xSs, bottomYSs, INSERTION_POINT_THICKNESS_SS);
        }
    }
}
