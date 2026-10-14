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

import java.awt.geom.Rectangle2D;

import org.jspecify.annotations.Nullable;

import songscribe.dom.NoteElement;
import songscribe.dom.Slide;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;

/**
 * Layout-time geometry for the two {@link Slide} kinds attached to a note: a connecting
 * {@link Slide#GLISSANDO} and a trailing {@link Slide#FALL}. Stateless arithmetic in staff spaces.
 */
public final class SlideGeometry {

    // ==========================================================================
    // Constants
    // ==========================================================================

    public static final double MIN_RECT_LENGTH_SS = 0.75;

    private SlideGeometry() {
    }

    // ==========================================================================
    // Records
    // ==========================================================================

    public record NoteContext(
        NoteElement note,
        double cySs,
        double columnRightXSs,
        double glissLeftXSs,
        double glissRightXSs
    ) {

        public NoteContext shiftedX(double shiftSs) {
            return new NoteContext(
                note,
                cySs,
                columnRightXSs + shiftSs,
                glissLeftXSs + shiftSs,
                glissRightXSs + shiftSs
            );
        }
    }

    public record Endpoints(
        double startXSs, double startYSs, double endXSs, double endYSs,
        double angle, double length
    ) {}

    // ==========================================================================
    // Note Context
    // ==========================================================================

    /**
     * @param middleLineYSs pass 0 for layout space
     */
    public static NoteContext noteContextAt(
        NoteElement note, double elementXSs, Beaming beaming, double middleLineYSs
    ) {
        var cy = NoteGeometry.noteStaffPositionToCoordinateSs(note.getStaffPosition().value(), middleLineYSs);

        // Compute the stem-free attach extent once and widen it to the stem-full extent for the
        // fall anchor, rather than recomputing the notehead/dots/accidental geometry twice.
        var attachExtent = NoteColumnGeometry.glissandoAttachExtentSs(note, beaming);
        var columnRightXSs = elementXSs + NoteColumnGeometry.extentSs(note, attachExtent).rightSs();
        var glissLeftXSs = elementXSs + attachExtent.leftSs();
        var glissRightXSs = elementXSs + attachExtent.rightSs();

        return new NoteContext(note, cy, columnRightXSs, glissLeftXSs, glissRightXSs);
    }

    // ==========================================================================
    // Endpoint Computation
    // ==========================================================================

    /**
     * Null if the glissando is too short to render.
     *
     * @param tgt null when there is no following note to connect to
     */
    public static @Nullable Endpoints computeEndpoints(
        NoteContext src, @Nullable NoteContext tgt
    ) {
        // A connecting glissando requires a target note; without one there is nothing to draw.
        if (tgt == null) {
            return null;
        }

        // Attachment points: stem-free extents, each at its own notehead-center Y.
        var attachStartX = src.glissRightXSs();
        var attachStartY = src.cySs();
        var attachEndX = tgt.glissLeftXSs();
        var attachEndY = tgt.cySs();

        var dx = attachEndX - attachStartX;
        var dy = attachEndY - attachStartY;
        var attachLength = Math.hypot(dx, dy);

        // Trim a constant gap off each end along the line direction; both gaps together must
        // leave drawable length.
        var gap = NoteGeometry.GLISSANDO_DRAWN_GAP_SS;

        if (attachLength <= 2 * gap) {
            return null;
        }

        var unitX = dx / attachLength;
        var unitY = dy / attachLength;

        var startX = attachStartX + unitX * gap;
        var startY = attachStartY + unitY * gap;
        var endX = attachEndX - unitX * gap;
        var endY = attachEndY - unitY * gap;

        var length = attachLength - 2 * gap;

        if (length < MIN_RECT_LENGTH_SS) {
            return null;
        }

        var angle = Math.atan2(dy, dx);

        return new Endpoints(startX, startY, endX, endY, angle, length);
    }

    public static Rectangle2D.Double computeFallBoundsSs(NoteContext src) {
        var glyphXSs = src.columnRightXSs() + NoteGeometry.FALL_GAP_SS;
        var glyphYSs = src.cySs();

        // The glyph bbox is in staff spaces with the renderer's Y-down convention, relative to the
        // glyph origin (the drawString baseline anchor), so translating it by the draw point gives
        // the axis-aligned drawn rect directly.
        var bbox = SMuFLMetadata.bboxSs(SMuFLGlyph.BRASS_FALL_LIP_SHORT);

        return new Rectangle2D.Double(
            glyphXSs + bbox.leftSs(),
            glyphYSs + bbox.topSs(),
            bbox.widthSs(),
            bbox.heightSs()
        );
    }
}
