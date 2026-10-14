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

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;

import org.jspecify.annotations.Nullable;

import songscribe.dom.Line;
import songscribe.dom.NoteElement;
import songscribe.dom.Slide;
import songscribe.engraving.EngravingConstants;
import songscribe.hit.HitTarget;
import songscribe.layout.Beaming;
import songscribe.layout.LayoutResult;
import songscribe.layout.NoteGeometry;
import songscribe.layout.SlideGeometry;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;
import songscribe.util.GraphicUtils;
import songscribe.util.GraphicsState;

import static songscribe.util.GraphicsState.Property.COLOR;

/**
 * Draws the two {@link Slide} kinds attached to a note. Geometry is computed at layout time by
 * {@link SlideGeometry}; only the preview renders, which draw a slide for a note that does not own
 * one yet, compute it through {@link SlideGeometry} directly.
 */
public final class SlideRenderer {

    // ==========================================================================
    // Constants
    // ==========================================================================

    private static final double LAYOUT_MIDLINE_Y_SS = 0.0;

    private static final double NO_SHIFT_SS = 0.0;

    /** The glissando stroke width as a multiple of the LilyPond base thickness. */
    private static final double GLISSANDO_MULTIPLIER = 1.75;

    private static final double GLISSANDO_SS = EngravingConstants.LILYPOND_BASE_THICKNESS_SS * GLISSANDO_MULTIPLIER;

    // ==========================================================================
    // Singleton
    // ==========================================================================

    private static final SlideRenderer INSTANCE = new SlideRenderer();

    private SlideRenderer() {
    }

    public static SlideRenderer getInstance() {
        return INSTANCE;
    }

    // ==========================================================================
    // Rendering
    // ==========================================================================

    public void renderSlidesFromLine(
        Graphics2D g2,
        LineInvariants invariants,
        ElementFrame frame
    ) {
        var line = invariants.requireCurrentLine();

        for (var i = 0; i < line.effectiveElementCount(); i++) {
            if (line.getElement(i) instanceof NoteElement note && note.getSlide() != null) {
                renderSlide(g2, line, note, i, invariants, frame);
            }
        }
    }

    public void renderSlide(
        Graphics2D g2,
        Line line,
        NoteElement note,
        int noteIndex,
        LineInvariants invariants,
        ElementFrame frame
    ) {
        var slide = note.getSlide();

        if (slide == null) {
            return;
        }

        var layoutResult = invariants.getLayoutResult();
        var middleLineYSs = invariants.getMiddleLineYSs();
        var color = determineSlideColor(note, noteIndex, invariants);

        switch (slide) {
            // A fall is a standalone trailing glyph with no target note, so it never looks at
            // the following element — which would crash on a non-renderable type such as a
            // barline at the line end.
            case FALL -> {
                var fallBoundsSs = fallBoundsSs(note, noteIndex, layoutResult, frame);

                if (fallBoundsSs != null) {
                    drawFallGlyph(g2, fallBoundsSs, middleLineYSs, color);
                }
            }

            case GLISSANDO -> {
                // The layout already withholds geometry for a glissando between two notes at
                // one pitch, but the grace-note insert preview below recomputes endpoints
                // rather than reading the layout, so the check is needed here to cover that
                // branch too.
                if (!line.isSamePitchAsFollower(noteIndex)) {
                    var endpoints = glissandoEndpointsSs(note, noteIndex, line, layoutResult, frame);

                    if (endpoints != null) {
                        drawGlissando(g2, endpoints, middleLineYSs, color);
                    }
                }
            }
        }
    }

    /**
     * A connecting glissando belongs to both its notes, so selecting either colors it. A fall has
     * no target note and never picks up the following element's selection.
     *
     * @param note the note the slide hangs off, which is what a click on the slide selects
     */
    Color determineSlideColor(
        NoteElement note,
        int noteIndex,
        LineInvariants invariants
    ) {
        var color = invariants.getElementColor(noteIndex);

        if (!LineInvariants.isDefaultColor(color)) {
            return color;
        }

        var slideColor = invariants.colorFor(new HitTarget.Slide(note));

        if (!LineInvariants.isDefaultColor(slideColor)) {
            return slideColor;
        }

        if (note.hasGlissando() && invariants.isElementRangeSelected(noteIndex + 1)) {
            return invariants.getSelectionColor();
        }

        return Color.BLACK;
    }

    /**
     * @param g2 Graphics context (staff-space coordinate system)
     */
    public void renderPreviewGlissando(
        Graphics2D g2,
        int sourceIndex,
        LineInvariants invariants
    ) {
        var line = invariants.requireCurrentLine();

        if (!line.hasIndex(sourceIndex)) {
            return;
        }

        if (!(line.getElement(sourceIndex) instanceof NoteElement source)) {
            return;
        }

        var layoutResult = invariants.getLayoutResult();
        var src = noteContextSs(source, sourceIndex, line, layoutResult, NO_SHIFT_SS);
        var tgt = targetContextSs(sourceIndex, line, layoutResult, NO_SHIFT_SS);

        drawGlissandoIfDrawable(g2, src, tgt, invariants.getMiddleLineYSs());
    }

    /**
     * The host note exists only as the ghost the user is positioning, so its geometry cannot be
     * looked up the way {@link #renderPreviewGlissando}'s target is. Everything else is the
     * committed glissando's geometry.
     *
     * @param g2                Graphics context (staff-space coordinate system)
     */
    public void renderPreviewGlissandoToPreviewElement(
        Graphics2D g2,
        int sourceIndex,
        Line line,
        NoteElement previewElement,
        double previewElementXSs,
        LineInvariants invariants
    ) {
        if (!line.hasIndex(sourceIndex)
            || !(line.getElement(sourceIndex) instanceof NoteElement source)) {
            return;
        }

        var src = noteContextSs(source, sourceIndex, line, invariants.getLayoutResult(), NO_SHIFT_SS);

        // A preview element is not on the line, so no beam can span it.
        var tgt = SlideGeometry.noteContextAt(
            previewElement, previewElementXSs, Beaming.UNBEAMED, LAYOUT_MIDLINE_Y_SS);

        drawGlissandoIfDrawable(g2, src, tgt, invariants.getMiddleLineYSs());
    }

    // ==========================================================================
    // Geometry Lookup
    // ==========================================================================

    /**
     * Null when the layout holds none for this note.
     */
    private static Rectangle2D.@Nullable Double fallBoundsSs(
        NoteElement note, int noteIndex, LayoutResult layoutResult, ElementFrame frame
    ) {
        var slideLayout = layoutResult.getSlideLayout(note);

        if (slideLayout == null) {
            return null;
        }

        var boundsSs = slideLayout.fallBounds();

        if (boundsSs == null) {
            return null;
        }

        var shiftSs = previewShiftSs(noteIndex, frame);

        if (shiftSs == NO_SHIFT_SS) {
            return boundsSs;
        }

        // The grace-note insert preview displaces the host note without relaying the line out,
        // so the fall travels with it.
        return new Rectangle2D.Double(
            boundsSs.x + shiftSs, boundsSs.y, boundsSs.width, boundsSs.height);
    }

    /**
     * Null when the glissando is too short to draw or has no following note.
     * <p>
     * During the grace-note insert preview the endpoints are recomputed, because the preview
     * displaces elements without relaying the line out, and its insertion index can fall between a
     * glissando's two notes.
     */
    private static SlideGeometry.@Nullable Endpoints glissandoEndpointsSs(
        NoteElement note, int noteIndex, Line line, LayoutResult layoutResult, ElementFrame frame
    ) {
        if (frame.previewShift() == null) {
            var slideLayout = layoutResult.getSlideLayout(note);
            return slideLayout != null ? slideLayout.glissando() : null;
        }

        return SlideGeometry.computeEndpoints(
            noteContextSs(note, noteIndex, line, layoutResult, previewShiftSs(noteIndex, frame)),
            targetContextSs(noteIndex, line, layoutResult, previewShiftSs(noteIndex + 1, frame))
        );
    }

    private static double previewShiftSs(int index, ElementFrame frame) {
        var previewShift = frame.previewShift();
        return previewShift != null && previewShift.displaces(index)
            ? previewShift.shiftSs()
            : NO_SHIFT_SS;
    }

    private static SlideGeometry.NoteContext noteContextSs(
        NoteElement element, int elementIndex, Line line, LayoutResult layoutResult, double shiftSs
    ) {
        return SlideGeometry.noteContextAt(
            element,
            layoutResult.getElementXSs(element) + shiftSs,
            Beaming.of(line.spanRegistry().findBeamJoining(elementIndex)),
            LAYOUT_MIDLINE_Y_SS
        );
    }

    /**
     * Null when there is nothing to connect to. A non-note follower counts as no follower, as in
     * the layout pass.
     */
    private static SlideGeometry.@Nullable NoteContext targetContextSs(
        int sourceIndex, Line line, LayoutResult layoutResult, double shiftSs
    ) {
        var targetIndex = sourceIndex + 1;

        if (targetIndex >= line.elementCount()) {
            return null;
        }

        if (!(line.getElement(targetIndex) instanceof NoteElement target)) {
            return null;
        }

        return noteContextSs(target, targetIndex, line, layoutResult, shiftSs);
    }

    // ==========================================================================
    // Drawing
    // ==========================================================================

    private void drawGlissandoIfDrawable(
        Graphics2D g2,
        SlideGeometry.NoteContext src,
        SlideGeometry.@Nullable NoteContext tgt,
        double middleLineYSs
    ) {
        var endpoints = SlideGeometry.computeEndpoints(src, tgt);

        if (endpoints != null) {
            drawGlissando(g2, endpoints, middleLineYSs, g2.getColor());
        }
    }

    /**
     * @param g2           Graphics context (staff-space coordinate system)
     * @param endpoints     the drawn segment's endpoints in layout space
     * @param middleLineYSs the line's middle-staff-line Y in component space
     */
    private void drawGlissando(
        Graphics2D g2,
        SlideGeometry.Endpoints endpoints,
        double middleLineYSs,
        Color color
    ) {
        try (var _ = GraphicsState.save(g2, COLOR)) {
            g2.setColor(color);
            GraphicUtils.drawRoundedLine(g2,
                endpoints.startXSs(),
                RenderingUtils.layoutYToComponentYSs(endpoints.startYSs(), middleLineYSs),
                endpoints.endXSs(),
                RenderingUtils.layoutYToComponentYSs(endpoints.endYSs(), middleLineYSs),
                GLISSANDO_SS);
        }
    }

    /**
     * The glyph's ink lands on {@code boundsSs}, so the drawn glyph and the rect the hit test uses
     * cannot drift apart.
     *
     * @param g2            Graphics context (staff-space coordinate system)
     * @param boundsSs      the glyph's drawn rect in layout space
     * @param middleLineYSs the line's middle-staff-line Y in component space
     */
    private void drawFallGlyph(
        Graphics2D g2,
        Rectangle2D boundsSs,
        double middleLineYSs,
        Color color
    ) {
        var bbox = SMuFLMetadata.bboxSs(SMuFLGlyph.BRASS_FALL_LIP_SHORT);
        var glyphXSs = boundsSs.getX() - bbox.leftSs();
        var glyphYSs = RenderingUtils.layoutYToComponentYSs(
            boundsSs.getY() - bbox.topSs(), middleLineYSs);

        try (var _ = GraphicsState.save(g2, COLOR)) {
            g2.setColor(color);
            RenderingUtils.drawBravuraGlyph(g2, SMuFLGlyph.BRASS_FALL_LIP_SHORT, glyphXSs, glyphYSs, true);
        }
    }

}
