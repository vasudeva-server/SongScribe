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

import songscribe.dom.StaffElement;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;

/**
 * Layout-time geometry for the two {@link StaffElement.Slide} subtypes attached to a note: a
 * connecting {@link StaffElement.Glissando} (a line between two notes) and a trailing
 * {@link StaffElement.Fall} (the {@code brassFallLipShort} glyph hanging off the note's right).
 * <p>
 * A connecting glissando attaches at the stem-free extents of the two notes — the leading note's
 * right edge and the trailing note's left edge (notehead, augmentation dots, and accidental, but
 * not the stem or flag) — each at its own notehead-center Y. The drawn line is a sub-segment of
 * that attach-to-attach ray, trimmed by {@link NoteGeometry#GLISSANDO_DRAWN_GAP_SS} along the line
 * direction at each end, so it stays collinear with the ray and clears the noteheads by a constant
 * distance regardless of slope.
 * <p>
 * A fall's glyph sits {@link NoteGeometry#FALL_GAP_SS} past the host note's column edge, at the
 * note's notehead-center Y.
 * <p>
 * Everything here is stateless arithmetic in staff spaces. Y is relative to whatever
 * {@code middleLineYSs} the caller passes into {@link #noteContextAt}: layout passes 0, which
 * yields the midline-relative layout space the renderer and the hit registry both work in.
 */
public final class SlideGeometry {

    // ==========================================================================
    // Constants
    // ==========================================================================

    /**
     * Minimum rendered glissando length in staff spaces. Glissandos shorter than this are not drawn.
     * 0.75 ss stays clearly visible at tight spacing (notably a target carrying an accidental)
     * (refs #443).
     */
    public static final double MIN_RECT_LENGTH_SS = 0.75;

    private SlideGeometry() {
    }

    // ==========================================================================
    // Records
    // ==========================================================================

    /**
     * Resolved geometry for a single note in layout space: notehead-center Y, the stem-full
     * column right edge (the fall anchor), and the stem-free glissando attachment extents.
     */
    public record NoteContext(
        StaffElement note,
        double cySs,
        double columnRightXSs,
        double glissLeftXSs,
        double glissRightXSs
    ) {

        /**
         * Returns a copy shifted right by {@code shiftSs} along X: the fall anchor and both
         * glissando attach extents are translated, leaving Y intact.
         */
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

    /**
     * Immutable record holding the computed slide endpoint positions in layout space,
     * plus the derived angle and length so callers need not recompute them.
     */
    public record Endpoints(
        double startXSs, double startYSs, double endXSs, double endYSs,
        double angle, double length
    ) {}

    // ==========================================================================
    // Note Context
    // ==========================================================================

    /**
     * Resolves the geometry context for a note at the given origin X: notehead-center Y, the
     * stem-full column right edge (fall anchor), and the stem-free glissando attach extents.
     *
     * @param note          the note whose geometry to resolve
     * @param elementXSs    the note column's origin X in the line's staff spaces
     * @param beamed        whether the note belongs to a beam group (affects dot placement)
     * @param middleLineYSs Y position of the middle staff line; pass 0 for layout space
     */
    public static NoteContext noteContextAt(
        StaffElement note, double elementXSs, boolean beamed, double middleLineYSs
    ) {
        var cy = NoteGeometry.noteStaffPositionToCoordinateSs(note.getStaffPosition(), middleLineYSs);

        // Compute the stem-free attach extent once and widen it to the stem-full extent for the
        // fall anchor, rather than recomputing the notehead/dots/accidental geometry twice.
        var attachExtent = NoteColumnGeometry.glissandoAttachExtentSs(note, beamed);
        var columnRightXSs = elementXSs + NoteColumnGeometry.extentSs(note, attachExtent).rightSs();
        var glissLeftXSs = elementXSs + attachExtent.leftSs();
        var glissRightXSs = elementXSs + attachExtent.rightSs();

        return new NoteContext(note, cy, columnRightXSs, glissLeftXSs, glissRightXSs);
    }

    // ==========================================================================
    // Endpoint Computation
    // ==========================================================================

    /**
     * Computes the glissando start/end positions in layout space (staff-space coordinates).
     * Returns null if the glissando is too short to render.
     * <p>
     * The attachment points are the stem-free column extents — the leading note's right
     * ({@link NoteContext#glissRightXSs}) and the trailing note's left
     * ({@link NoteContext#glissLeftXSs}) — each at its own notehead-center Y. The drawn line
     * is a sub-segment of the attach-to-attach ray, trimmed by
     * {@link NoteGeometry#GLISSANDO_DRAWN_GAP_SS} <em>along the line direction</em> at each
     * end. Because the gap is measured along the line, its horizontal component is
     * {@code gap·cosθ}, so the line clears the noteheads by a constant distance regardless of
     * slope. The glissando is rejected when the attach length leaves no room for both gaps
     * ({@code len ≤ 2·gap}) or when the drawn length falls below {@link #MIN_RECT_LENGTH_SS}.
     *
     * @param src Source note context
     * @param tgt Target note context, or null when there is no following note to connect to
     * @return the glissando endpoints, or null if the glissando cannot render
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

    /**
     * Returns the axis-aligned drawn rect of a fall's {@code brassFallLipShort} glyph, in the same
     * layout space as {@code src}. The glyph sits one {@link NoteGeometry#FALL_GAP_SS} past the host
     * note's column right edge, at the note's notehead-center Y.
     * <p>
     * Unlike a glissando, a fall has no reject case: it is a single glyph with no target note, so
     * this never returns null.
     *
     * @param src the host note's context
     * @return the glyph's drawn rect in staff spaces
     */
    public static Rectangle2D.Double computeFallBoundsSs(NoteContext src) {
        var glyphXSs = src.columnRightXSs() + NoteGeometry.FALL_GAP_SS;
        var glyphYSs = src.cySs();

        // The glyph bbox is in staff spaces with the renderer's Y-down convention, relative to the
        // glyph origin (the drawString baseline anchor), so translating it by the draw point gives
        // the axis-aligned drawn rect directly.
        var bbox = SMuFLMetadata.requireBBox(SMuFLGlyph.BRASS_FALL_LIP_SHORT);

        return new Rectangle2D.Double(
            glyphXSs + bbox.left(),
            glyphYSs + bbox.top(),
            bbox.width(),
            bbox.height()
        );
    }
}
