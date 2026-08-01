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

import static songscribe.util.GraphicsState.Property.COLOR;

import module java.desktop;

import org.jspecify.annotations.Nullable;

import songscribe.dom.Line;
import songscribe.dom.StaffElement;
import songscribe.engraving.LineThickness;
import songscribe.hit.HitTarget;
import songscribe.layout.LayoutResult;
import songscribe.layout.NoteGeometry;
import songscribe.layout.SlideGeometry;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;
import songscribe.util.GraphicUtils;
import songscribe.util.GraphicsState;

/**
 * Draws the two {@link StaffElement.Slide} subtypes attached to a note: a connecting
 * {@link StaffElement.Glissando} (a filled rectangle between two notes) and a trailing
 * {@link StaffElement.Fall} (the {@code brassFallLipShort} glyph hanging off the note's right).
 * <p>
 * The geometry itself is computed at layout time by {@link SlideGeometry} and stored on the line's
 * {@link LayoutResult} as a {@link LayoutResult.SlideLayout}, so this class only reads it and
 * converts from layout space (Y relative to the staff midline) to component space (Y relative to
 * the line component's top edge) as it draws.
 * <p>
 * The preview renders are the exception: they draw a slide for a note that does not own one yet, so
 * the layout has nothing stored for them and they compute their geometry through
 * {@link SlideGeometry} directly.
 * <p>
 * Slide data is stored on the source note via {@link StaffElement#getSlide()}.
 */
public final class SlideRenderer {

    // ==========================================================================
    // Constants
    // ==========================================================================

    /**
     * Layout space puts the staff midline at Y = 0, so a note context built for it takes 0 as its
     * middle-line Y.
     */
    private static final double LAYOUT_MIDLINE_Y_SS = 0.0;

    /** No displacement: the note sits where the layout put it. */
    private static final double NO_SHIFT_SS = 0.0;

    // ==========================================================================
    // Singleton
    // ==========================================================================

    private static final SlideRenderer INSTANCE = new SlideRenderer();

    private SlideRenderer() {
    }

    /**
     * Returns the singleton instance.
     */
    public static SlideRenderer getInstance() {
        return INSTANCE;
    }

    // ==========================================================================
    // Rendering
    // ==========================================================================

    /**
     * Renders slides for all notes in a line.
     *
     * @param g2    Graphics context
     * @param line  The line containing notes
     * @param invariants   Line invariants
     * @param frame Element frame (line-level)
     */
    public void renderSlidesFromLine(
        Graphics2D g2,
        Line line,
        LineInvariants invariants,
        ElementFrame frame
    ) {
        for (var i = 0; i < line.effectiveElementCount(); i++) {
            var note = line.getElement(i);

            if (note.getSlide() != null) {
                renderSlide(g2, line, note, i, invariants, frame);
            }
        }
    }

    /**
     * Renders the slide attached to a specific note: a trailing {@link StaffElement.Fall} glyph or a
     * connecting {@link StaffElement.Glissando} line.
     *
     * @param g2        Graphics context
     * @param line      The line containing the note
     * @param note      The note with the slide
     * @param noteIndex Index of the note in the line
     * @param invariants       Line invariants
     * @param frame     Element frame (line-level)
     */
    public void renderSlide(
        Graphics2D g2,
        Line line,
        StaffElement note,
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

        // A fall is a standalone trailing glyph with no target note, so it never looks at the
        // following element — which would crash on a non-renderable type such as a barline at
        // the line end.
        if (slide instanceof StaffElement.Fall) {
            var fallBoundsSs = fallBoundsSs(note, noteIndex, layoutResult, frame);

            if (fallBoundsSs != null) {
                drawFallGlyph(g2, fallBoundsSs, middleLineYSs, color);
            }

            return;
        }

        // The layout already withholds geometry for a glissando between two notes at one pitch,
        // but the grace-note insert preview below recomputes endpoints rather than reading the
        // layout, so the check is needed here to cover that branch too.
        if (line.isSamePitchAsFollower(noteIndex)) {
            return;
        }

        var endpoints = glissandoEndpointsSs(note, noteIndex, line, layoutResult, frame);

        if (endpoints != null) {
            drawGlissando(g2, endpoints, middleLineYSs, color);
        }
    }

    /**
     * Determines the color for a slide based on playback and selection state.
     * <p>
     * Delegates to {@link LineInvariants#getElementColor(int)} for the common edit-mode /
     * playback / selection checks on the owner note, and only when the note contributes no
     * color of its own does the slide's own selection state show through.
     *
     * @param note      the note the slide hangs off, which is what a click on the slide selects
     * @param noteIndex that note's index on the line
     */
    Color determineSlideColor(
        StaffElement note,
        int noteIndex,
        LineInvariants invariants
    ) {
        var color = invariants.getElementColor(noteIndex);

        if (color != Color.BLACK) {
            return color;
        }

        return invariants.colorFor(new HitTarget.Slide(note), noteIndex);
    }

    /**
     * Renders a preview glissando line from the source note.
     * <p>
     * Used when the glissando tool is active and the mouse hovers over a zone.
     * No notehead is shown — only the glissando preview.
     *
     * @param g2          Graphics context (staff-space coordinate system)
     * @param sourceIndex Index of the source note in the line
     * @param line        The line containing the notes
     * @param invariants         Line invariants
     */
    public void renderPreviewGlissando(
        Graphics2D g2,
        int sourceIndex,
        Line line,
        LineInvariants invariants
    ) {
        if (sourceIndex < 0 || sourceIndex >= line.elementCount()) {
            return;
        }

        var layoutResult = invariants.getLayoutResult();
        var src = noteContextSs(line.getElement(sourceIndex), sourceIndex, line, layoutResult, NO_SHIFT_SS);
        var tgt = targetContextSs(sourceIndex, line, layoutResult, NO_SHIFT_SS);

        drawGlissandoIfDrawable(g2, src, tgt, invariants.getMiddleLineYSs());
    }

    /**
     * Renders a preview glissando from the note at {@code sourceIndex} to the hover preview
     * element, which sits at {@code previewElementXSs} instead of in the layout.
     * <p>
     * Used while the grace-note host insertion preview is showing: the host note exists only as
     * the ghost the user is positioning, so its geometry cannot be looked up the way
     * {@link #renderPreviewGlissando}'s target is. Everything else — the attach extents, the
     * end trimming, the minimum length — is the committed glissando's geometry.
     *
     * @param g2                Graphics context (staff-space coordinate system)
     * @param sourceIndex       Index of the source note in the line
     * @param line              The line containing the source note
     * @param previewElement    The hover preview element to connect to
     * @param previewElementXSs The preview element's origin X in the line's staff spaces
     * @param invariants        Line invariants
     */
    public void renderPreviewGlissandoToPreviewElement(
        Graphics2D g2,
        int sourceIndex,
        Line line,
        StaffElement previewElement,
        double previewElementXSs,
        LineInvariants invariants
    ) {
        if (sourceIndex < 0 || sourceIndex >= line.elementCount()) {
            return;
        }

        var src = noteContextSs(
            line.getElement(sourceIndex), sourceIndex, line,
            invariants.getLayoutResult(), NO_SHIFT_SS
        );

        // A preview element is not on the line, so no beam can span it.
        var tgt = SlideGeometry.noteContextAt(previewElement, previewElementXSs, false, LAYOUT_MIDLINE_Y_SS);

        drawGlissandoIfDrawable(g2, src, tgt, invariants.getMiddleLineYSs());
    }

    /**
     * Renders a preview fall glyph trailing the source note.
     * <p>
     * Used when the slide tool is active and the mouse hovers over the fall zone. No notehead is
     * shown and nothing is stored — only the glyph preview. A fall has no target note, so unlike
     * {@link #renderPreviewGlissando} this never resolves the following element.
     *
     * @param g2          Graphics context (staff-space coordinate system)
     * @param sourceIndex Index of the source note in the line
     * @param line        The line containing the note
     * @param invariants  Line invariants
     */
    public void renderPreviewFall(
        Graphics2D g2,
        int sourceIndex,
        Line line,
        LineInvariants invariants
    ) {
        if (sourceIndex < 0 || sourceIndex >= line.elementCount()) {
            return;
        }

        var src = noteContextSs(
            line.getElement(sourceIndex), sourceIndex, line,
            invariants.getLayoutResult(), NO_SHIFT_SS
        );

        drawFallGlyph(
            g2, SlideGeometry.computeFallBoundsSs(src), invariants.getMiddleLineYSs(), g2.getColor());
    }

    // ==========================================================================
    // Geometry Lookup
    // ==========================================================================

    /**
     * Returns the fall glyph's drawn rect in layout space, or null when the layout holds none for
     * this note.
     */
    private static Rectangle2D.@Nullable Double fallBoundsSs(
        StaffElement note, int noteIndex, LayoutResult layoutResult, ElementFrame frame
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
     * Returns the glissando's drawn endpoints in layout space, or null when it is too short to
     * draw or has no following note.
     * <p>
     * Normally these come straight from the layout. During the grace-note insert preview they are
     * recomputed instead: the preview displaces elements from its insertion index onward without
     * relaying the line out, and that index can fall between a glissando's two notes, which
     * stretches the line rather than translating it.
     */
    private static SlideGeometry.@Nullable Endpoints glissandoEndpointsSs(
        StaffElement note, int noteIndex, Line line, LayoutResult layoutResult, ElementFrame frame
    ) {
        if (!frame.hasPreviewShift()) {
            var slideLayout = layoutResult.getSlideLayout(note);
            return slideLayout != null ? slideLayout.glissando() : null;
        }

        return SlideGeometry.computeEndpoints(
            noteContextSs(note, noteIndex, line, layoutResult, previewShiftSs(noteIndex, frame)),
            targetContextSs(noteIndex, line, layoutResult, previewShiftSs(noteIndex + 1, frame))
        );
    }

    /** Returns the grace-note insert preview's displacement for the element at {@code index}. */
    private static double previewShiftSs(int index, ElementFrame frame) {
        return frame.hasPreviewShift() && index >= frame.previewShiftFromIndex()
            ? frame.previewShiftSs()
            : NO_SHIFT_SS;
    }

    /**
     * Builds the geometry context, in layout space, for the element at {@code elementIndex},
     * displaced by {@code shiftSs} from where the layout put it.
     */
    private static SlideGeometry.NoteContext noteContextSs(
        StaffElement element, int elementIndex, Line line, LayoutResult layoutResult, double shiftSs
    ) {
        return SlideGeometry.noteContextAt(
            element,
            layoutResult.getElementXSs(element) + shiftSs,
            line.findBeamAt(elementIndex) != null,
            LAYOUT_MIDLINE_Y_SS
        );
    }

    /**
     * Builds the target context for a connecting glissando — the element after
     * {@code sourceIndex} — or returns null when there is nothing to connect to.
     * <p>
     * A non-note follower (a barline at the line end, say) has no notehead geometry to attach to,
     * so it counts as no follower at all, the same rule the layout pass applies.
     */
    private static SlideGeometry.@Nullable NoteContext targetContextSs(
        int sourceIndex, Line line, LayoutResult layoutResult, double shiftSs
    ) {
        var targetIndex = sourceIndex + 1;

        if (targetIndex >= line.elementCount()) {
            return null;
        }

        var target = line.getElement(targetIndex);

        if (!target.getType().isNote()) {
            return null;
        }

        return noteContextSs(target, targetIndex, line, layoutResult, shiftSs);
    }

    // ==========================================================================
    // Drawing
    // ==========================================================================

    /**
     * Draws the glissando between {@code src} and {@code tgt}, or nothing when the two are too
     * close together to leave a drawable line. Used by the preview renders, which have no stored
     * geometry to read.
     */
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
     * Draws a rounded line between the glissando's endpoints.
     * <p>
     * The endpoints arrive in layout space (Y relative to the staff midline) and are drawn in
     * component space, so every Y crosses through
     * {@link RenderingUtils#layoutYToComponentYSs(double, double)}.
     *
     * @param g2            Graphics context (staff-space coordinate system)
     * @param endpoints     the drawn segment's endpoints in layout space
     * @param middleLineYSs the line's middle-staff-line Y in component space
     * @param color         Fill color for the glissando rectangle
     */
    private void drawGlissando(
        Graphics2D g2,
        SlideGeometry.Endpoints endpoints,
        double middleLineYSs,
        Color color
    ) {
        try (var ignored = GraphicsState.save(g2, COLOR)) {
            g2.setColor(color);
            GraphicUtils.drawRoundedLine(g2,
                endpoints.startXSs(),
                RenderingUtils.layoutYToComponentYSs(endpoints.startYSs(), middleLineYSs),
                endpoints.endXSs(),
                RenderingUtils.layoutYToComponentYSs(endpoints.endYSs(), middleLineYSs),
                LineThickness.GLISSANDO_SS);
        }
    }

    /**
     * Draws the {@code brassFallLipShort} glyph so that its ink lands on {@code boundsSs}, which
     * sits {@link NoteGeometry#FALL_GAP_SS} past the host note's column right edge at the note's
     * notehead-center Y.
     * <p>
     * {@code boundsSs} is the glyph's bounding box translated by the {@code drawString} anchor, so
     * subtracting the box's own offsets recovers that anchor exactly — the drawn glyph and the
     * rect the hit test uses cannot drift apart.
     *
     * @param g2            Graphics context (staff-space coordinate system)
     * @param boundsSs      the glyph's drawn rect in layout space
     * @param middleLineYSs the line's middle-staff-line Y in component space
     * @param color         the glyph color
     */
    private void drawFallGlyph(
        Graphics2D g2,
        Rectangle2D boundsSs,
        double middleLineYSs,
        Color color
    ) {
        var bbox = SMuFLMetadata.requireBBox(SMuFLGlyph.BRASS_FALL_LIP_SHORT);
        var glyphXSs = boundsSs.getX() - bbox.left();
        var glyphYSs = RenderingUtils.layoutYToComponentYSs(
            boundsSs.getY() - bbox.top(), middleLineYSs);

        try (var ignored = GraphicsState.save(g2, COLOR)) {
            g2.setColor(color);
            RenderingUtils.drawBravuraGlyph(g2, SMuFLGlyph.BRASS_FALL_LIP_SHORT, glyphXSs, glyphYSs, true);
        }
    }

}
