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
import songscribe.layout.LayoutResult;
import songscribe.engraving.LineThickness;
import songscribe.layout.NoteGeometry;
import songscribe.dom.ScaleContext;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;
import songscribe.util.GraphicUtils;
import songscribe.util.GraphicsState;

/**
 * Renders the two {@link StaffElement.Slide} subtypes attached to a note: a connecting
 * {@link StaffElement.Glissando} (a filled rectangle between two notes) and a trailing
 * {@link StaffElement.Fall} (the {@code brassFallLipShort} glyph hanging off the note's right).
 * <p>
 * A connecting glissando attaches at the stem-free extents of the two notes — the leading note's
 * right edge and the trailing note's left edge (notehead, augmentation dots, and accidental, but
 * not the stem or flag) — each at its own notehead-center Y. The drawn line is a sub-segment of
 * that attach-to-attach ray, trimmed by {@link NoteGeometry#GLISSANDO_DRAWN_GAP_SS} along the line
 * direction at each end, so it stays collinear with the ray and clears the noteheads by a constant
 * distance regardless of slope.
 * <p>
 * A fall's glyph is drawn {@link NoteGeometry#FALL_GAP_SS} past the host note's column edge, at the
 * note's notehead-center Y, and its drawn rect is cached on the {@link StaffElement.Fall} for
 * hit-testing.
 * <p>
 * Slide data is stored on the source note via {@link StaffElement#getSlide()}.
 */
public final class SlideRenderer {

    // ==========================================================================
    // Constants
    // ==========================================================================

    /**
     * Minimum rendered glissando length in staff spaces. Glissandos shorter than this are not drawn.
     * 0.75 ss stays clearly visible at tight spacing (notably a target carrying an accidental)
     * (refs #443).
     */
    private static final double MIN_RECT_LENGTH_SS = 0.75;

    /** Hit-test tolerance in pixels (wider than visual thickness for easier clicking). */
    private static final double HIT_THICKNESS_PX = 8.0;

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
        var src = resolveNoteContext(note, noteIndex, line, layoutResult, middleLineYSs);
        var isGlissando = slide instanceof StaffElement.Glissando;
        var color = determineSlideColor(noteIndex, isGlissando, invariants);

        var hasPreviewShift = frame.hasPreviewShift();
        var shiftFromIndex = frame.previewShiftFromIndex();
        var shiftSs = frame.previewShiftSs();

        // Apply preview shift so the source slide tracks its note during grace-note insert preview
        if (hasPreviewShift && noteIndex >= shiftFromIndex) {
            src = src.shiftedX(shiftSs);
        }

        // A fall is a standalone trailing glyph with no target note. Resolving the next element
        // would crash when it is a non-renderable type (e.g. a barline at the line end), so the
        // fall must render before any target resolution.
        if (slide instanceof StaffElement.Fall fall) {
            renderFall(g2, fall, src, color);
            return;
        }

        var tgt = resolveTargetContext(noteIndex, line, layoutResult, middleLineYSs);

        if (tgt != null && hasPreviewShift && noteIndex + 1 >= shiftFromIndex) {
            tgt = tgt.shiftedX(shiftSs);
        }

        // A connected glissando between notes at the same pitch is musically meaningless — hide it
        if (tgt != null && src.note().getPitch() == tgt.note().getPitch()) {
            return;
        }

        render(g2, src, tgt, (StaffElement.Glissando) slide, color);
    }

    /**
     * Renders a fall's {@code brassFallLipShort} glyph trailing the host note and caches the glyph's
     * drawn rect on the {@link StaffElement.Fall} for hit-testing.
     */
    private void renderFall(Graphics2D g2, StaffElement.Fall fall, NoteContext src, Color color) {
        fall.cachedHitBounds = drawFallGlyph(g2, src, color);
    }

    /**
     * Draws the {@code brassFallLipShort} glyph one {@link NoteGeometry#FALL_GAP_SS} past
     * the host note's column right edge, at the note's notehead-center Y, and returns the glyph's
     * axis-aligned drawn rect in layout space.
     */
    private Rectangle2D drawFallGlyph(Graphics2D g2, NoteContext src, Color color) {
        var glyphXSs = src.columnRightXSs() + NoteGeometry.FALL_GAP_SS;
        var glyphYSs = src.cySs();

        try (var ignored = GraphicsState.save(g2, COLOR)) {
            g2.setColor(color);
            RenderingUtils.drawBravuraGlyph(g2, SMuFLGlyph.BRASS_FALL_LIP_SHORT, glyphXSs, glyphYSs, true);
        }

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

    /**
     * Determines the color for a slide based on playback and selection state.
     * <p>
     * Delegates to {@link LineInvariants#getElementColor(int)} for the
     * common edit-mode / playback / selection checks, then adds
     * slide-specific selection rules on top (standalone slide selection,
     * implied target-note selection for a connecting glissando).
     *
     * @param isGlissando whether the slide is a {@link StaffElement.Glissando}; a glissando
     *               has a target note, so the implied target-note rule must only
     *               apply to it (otherwise selecting the element after a fall's host note would
     *               wrongly highlight the fall).
     */
    Color determineSlideColor(
        int noteIndex,
        boolean isGlissando,
        LineInvariants invariants
    ) {
        var lineIndex = invariants.getLineIndex();
        var color = invariants.getElementColor(noteIndex);

        if (color != Color.BLACK) {
            return color;
        }

        var selectionProvider = invariants.getSelectionProvider();

        if (selectionProvider == null) {
            return Color.BLACK;
        }

        // Standalone slide selection
        if (selectionProvider.isSlideSelected(noteIndex, lineIndex)) {
            return invariants.getSelectionColor();
        }

        // Implied by target note selection — only a connecting glissando has a target note.
        if (isGlissando && selectionProvider.isElementSelected(noteIndex + 1, lineIndex)) {
            return invariants.getSelectionColor();
        }

        return Color.BLACK;
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

        var note = line.getElement(sourceIndex);
        var layoutResult = invariants.getLayoutResult();
        var middleLineYSs = invariants.getMiddleLineYSs();
        var src = resolveNoteContext(note, sourceIndex, line, layoutResult, middleLineYSs);
        var tgt = resolveTargetContext(sourceIndex, line, layoutResult, middleLineYSs);

        render(g2, src, tgt, null, g2.getColor());
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

        var middleLineYSs = invariants.getMiddleLineYSs();
        var src = resolveNoteContext(
            line.getElement(sourceIndex), sourceIndex, line,
            invariants.getLayoutResult(), middleLineYSs
        );

        // A preview element is not on the line, so no beam can span it.
        var tgt = noteContextAt(previewElement, previewElementXSs, false, middleLineYSs);

        render(g2, src, tgt, null, g2.getColor());
    }

    /**
     * Renders a preview fall glyph trailing the source note.
     * <p>
     * Used when the slide tool is active and the mouse hovers over the fall zone. No notehead is
     * shown and nothing is cached — only the glyph preview. A fall has no target note, so unlike
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

        var note = line.getElement(sourceIndex);
        var src = resolveNoteContext(
            note, sourceIndex, line, invariants.getLayoutResult(), invariants.getMiddleLineYSs());

        drawFallGlyph(g2, src, g2.getColor());
    }

    /**
     * Hit-tests all slides in a line against a click point in staff-space coordinates.
     * Uses cached geometry from the most recent render pass: a fall is tested against its
     * axis-aligned glyph rect, a connecting glissando against its rotated drawn line.
     *
     * @param clickXSs click X in staff spaces
     * @param clickYSs click Y in staff spaces
     * @param line     the line to test
     * @return the note index of the hit slide's owner, or -1 if no hit
     */
    public int hitTestSlide(double clickXSs, double clickYSs, Line line) {
        var halfHitSs = ScaleContext.pxToSs(HIT_THICKNESS_PX) / 2.0;

        for (var i = 0; i < line.effectiveElementCount(); i++) {
            var slide = line.getElement(i).getSlide();

            if (slide instanceof StaffElement.Fall fall) {
                var bounds = fall.cachedHitBounds;

                if (bounds != null && bounds.contains(clickXSs, clickYSs)) {
                    return i;
                }

                continue;
            }

            if (!(slide instanceof StaffElement.Glissando glissando) || !glissando.hasCachedGeometry) {
                continue;
            }

            var dx = clickXSs - glissando.cachedStartX;
            var dy = clickYSs - glissando.cachedStartY;
            var localX = dx * glissando.cachedCos + dy * glissando.cachedSin;
            var localY = -dx * glissando.cachedSin + dy * glissando.cachedCos;

            if (localX >= 0 && localX <= glissando.cachedLength && Math.abs(localY) <= halfHitSs) {
                return i;
            }
        }

        return -1;
    }

    // ==========================================================================
    // Endpoint Computation and Rendering
    // ==========================================================================

    /**
     * Resolved geometry for a single note in layout space: notehead-center Y, the stem-full
     * column right edge (the fall anchor), and the stem-free glissando attachment extents.
     */
    record NoteContext(
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
        NoteContext shiftedX(double shiftSs) {
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
    record Endpoints(
        double startXSs, double startYSs, double endXSs, double endYSs,
        double angle, double length
    ) {}

    /**
     * Resolves the geometry context for a note at the given index: notehead-center Y, the
     * stem-full column right edge (fall anchor), and the stem-free glissando attach extents,
     * all translated into layout space.
     */
    static NoteContext resolveNoteContext(
        StaffElement note, int noteIndex, Line line,
        LayoutResult layoutResult, double middleLineYSs
    ) {
        return noteContextAt(
            note,
            layoutResult.getElementXSs(note),
            line.findBeamAt(noteIndex) != null,
            middleLineYSs
        );
    }

    /**
     * Resolves the geometry context for a note at an explicitly given origin X, for a note the
     * layout cannot be asked about because it is not on the line — the hover preview element.
     */
    static NoteContext noteContextAt(
        StaffElement note, double elementXSs, boolean beamed, double middleLineYSs
    ) {
        var cy = RenderingUtils.noteStaffPositionToCoordinateSs(note.getStaffPosition(), middleLineYSs);

        // Compute the stem-free attach extent once and widen it to the stem-full extent for the
        // fall anchor, rather than recomputing the notehead/dots/accidental geometry twice.
        var attachExtent = NoteColumnGeometry.glissandoAttachExtentSs(note, beamed);
        var columnRightXSs = elementXSs + NoteColumnGeometry.extentSs(note, attachExtent).rightSs();
        var glissLeftXSs = elementXSs + attachExtent.leftSs();
        var glissRightXSs = elementXSs + attachExtent.rightSs();

        return new NoteContext(note, cy, columnRightXSs, glissLeftXSs, glissRightXSs);
    }

    /**
     * Resolves the target note context for a connecting glissando, or returns null
     * if no next note exists.
     */
    @Nullable
    private NoteContext resolveTargetContext(
        int sourceIndex, Line line,
        LayoutResult layoutResult, double middleLineYSs
    ) {
        if (sourceIndex + 1 >= line.elementCount()) {
            return null;
        }

        var nextElement = line.getElement(sourceIndex + 1);

        return resolveNoteContext(nextElement, sourceIndex + 1, line, layoutResult, middleLineYSs);
    }

    /**
     * Computes the slide start/end positions in layout space (staff-space coordinates).
     * Returns null if the slide is too short to render.
     * <p>
     * Both render() and the public endpoint methods delegate to this.
     * <p>
     * The attachment points are the stem-free column extents — the leading note's right
     * ({@link NoteContext#glissRightXSs}) and the trailing note's left
     * ({@link NoteContext#glissLeftXSs}) — each at its own notehead-center Y. The drawn line
     * is a sub-segment of the attach-to-attach ray, trimmed by
     * {@link NoteGeometry#GLISSANDO_DRAWN_GAP_SS} <em>along the line direction</em> at each
     * end. Because the gap is measured along the line, its horizontal component is
     * {@code gap·cosθ}, so the line clears the noteheads by a constant distance regardless of
     * slope. The slide is rejected when the attach length leaves no room for both gaps
     * ({@code len ≤ 2·gap}) or when the drawn length falls below {@link #MIN_RECT_LENGTH_SS}.
     *
     * @param src Source note context
     * @param tgt Target note context, or null when there is no following note to connect to
     * @return the slide endpoints, or null if the slide cannot render
     */
    static SlideRenderer.@Nullable Endpoints computeEndpoints(
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
     * Renders a filled rectangle between two note columns.
     * <p>
     * For connecting glissandos, the shape spans the stem-free attach extents of the two notes
     * (source right, target left), trimmed by {@link NoteGeometry#GLISSANDO_DRAWN_GAP_SS} along the
     * line direction at each end. See {@link #computeEndpoints} for the endpoint geometry.
     *
     * @param g2           Graphics context (staff-space coordinate system)
     * @param src          Source note context
     * @param tgt          Target note context, or null when there is no following note
     * @param glissando    Glissando whose cached geometry is populated for
     *                     hit-testing; null for preview renders
     * @param color        Fill color for the glissando rectangle
     */
    private void render(
        Graphics2D g2,
        NoteContext src, @Nullable NoteContext tgt,
        StaffElement.@Nullable Glissando glissando,
        Color color
    ) {
        var endpoints = computeEndpoints(src, tgt);

        if (endpoints == null) {
            return;
        }

        var length = endpoints.length();

        if (glissando != null) {
            glissando.cachedStartX = endpoints.startXSs();
            glissando.cachedStartY = endpoints.startYSs();
            glissando.cachedAngle = endpoints.angle();
            glissando.cachedCos = Math.cos(endpoints.angle());
            glissando.cachedSin = Math.sin(endpoints.angle());
            glissando.cachedLength = length;
            glissando.hasCachedGeometry = true;
        }

        var thicknessSs = LineThickness.GLISSANDO_SS;

        try (var ignored = GraphicsState.save(g2, COLOR)) {
            g2.setColor(color);
            GraphicUtils.drawRoundedLine(g2,
                endpoints.startXSs(), endpoints.startYSs(),
                endpoints.endXSs(), endpoints.endYSs(),
                thicknessSs);
        }
    }

}
