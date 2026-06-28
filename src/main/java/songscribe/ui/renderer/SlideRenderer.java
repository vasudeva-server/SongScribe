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
import songscribe.layout.LineThickness;
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
 * A connecting glissando's endpoints are placed at the trailing edge of the leading note's column
 * and the leading edge of the trailing note's column, each offset outward by
 * {@link NoteGeometry#GLISSANDO_DRAWN_GAP_SS}. Both endpoints sit at their own notehead-centre Y;
 * the drawn line's angle is derived from those two points. When the straight line intersects a
 * flag's bounding box, the relevant endpoint is pushed past the flag's far edge by the same gap.
 * <p>
 * A fall's glyph is drawn {@link NoteGeometry#FALL_GAP_SS} past the host note's column edge, at the
 * note's notehead-centre Y, and its drawn rect is cached on the {@link StaffElement.Fall} for
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
     * the host note's column right edge, at the note's notehead-centre Y, and returns the glyph's
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
     * Resolved geometry for a single note: notehead-centre Y, column edges in layout space,
     * optional flag bounding box in layout space, and note reference.
     */
    record NoteContext(
        StaffElement note,
        double cySs,
        double columnLeftXSs,
        double columnRightXSs,
        @Nullable Rectangle2D flagBBoxLayout
    ) {

        // Defensively copy the mutable flag bbox so the record owns its geometry and a
        // caller retaining the original reference cannot mutate it out from under us.
        NoteContext {
            if (flagBBoxLayout != null) {
                flagBBoxLayout = new Rectangle2D.Double(
                    flagBBoxLayout.getX(),
                    flagBBoxLayout.getY(),
                    flagBBoxLayout.getWidth(),
                    flagBBoxLayout.getHeight()
                );
            }
        }

        /**
         * Returns a copy shifted right by {@code shiftSs} along X: both column edges are
         * translated and the flag bbox's X is translated (when non-null), leaving Y intact.
         */
        NoteContext shiftedX(double shiftSs) {
            Rectangle2D shiftedFlag = null;

            if (flagBBoxLayout != null) {
                shiftedFlag = new Rectangle2D.Double(
                    flagBBoxLayout.getX() + shiftSs,
                    flagBBoxLayout.getY(),
                    flagBBoxLayout.getWidth(),
                    flagBBoxLayout.getHeight()
                );
            }

            return new NoteContext(
                note,
                cySs,
                columnLeftXSs + shiftSs,
                columnRightXSs + shiftSs,
                shiftedFlag
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
     * Resolves the geometry context for a note at the given index: notehead-centre Y,
     * column edges in layout space, and the optional flag bbox translated into layout space.
     */
    private static NoteContext resolveNoteContext(
        StaffElement note, int noteIndex, Line line,
        LayoutResult layoutResult, double middleLineYSs
    ) {
        var beamed = line.findBeamAt(noteIndex) != null;
        var elementXSs = layoutResult.getElementXSs(note);
        var cy = RenderingUtils.noteStaffPositionToCoordinateSs(note.getStaffPosition(), middleLineYSs);
        var extent = NoteColumnGeometry.extentSs(note, beamed);

        var columnLeftXSs = elementXSs + extent.leftSs();
        var columnRightXSs = elementXSs + extent.rightSs();

        // Translate the flag bbox from note-local space to layout space.
        Rectangle2D flagBBoxLayout = null;
        var flagBBoxLocal = extent.flagBBoxLocal();

        if (flagBBoxLocal != null) {
            flagBBoxLayout = new Rectangle2D.Double(
                flagBBoxLocal.getX() + elementXSs,
                flagBBoxLocal.getY() + cy,
                flagBBoxLocal.getWidth(),
                flagBBoxLocal.getHeight()
            );
        }

        return new NoteContext(note, cy, columnLeftXSs, columnRightXSs, flagBBoxLayout);
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
     * Returns null if the slide is too short to render (endpoints crossed or
     * length &lt; MIN_RECT_LENGTH_SS).
     * <p>
     * Both render() and the public endpoint methods delegate to this.
     * <p>
     * Conditional flag attachment logic (single-pass):
     * <pre>
     *   LEADING note (line departs from RIGHT edge):
     *     up-stem   flag is RIGHT → push startX to flagMaxX + gap  IFF line ∩ flagBBox
     *     down-stem flag is LEFT  → never near right edge           → ignore
     *
     *   TRAILING note (line arrives at LEFT edge):
     *     down-stem flag is LEFT  → push endX to flagMinX − gap  IFF line ∩ flagBBox
     *     up-stem   flag is RIGHT → never near left edge          → ignore
     * </pre>
     * Once an endpoint is pushed past the flag's far edge the line begins/ends clear of
     * the flag and cannot re-enter it, so no re-test is needed.
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

        // Base endpoints: column-edge ± gap at notehead-centre Y.
        var startX = src.columnRightXSs() + NoteGeometry.GLISSANDO_DRAWN_GAP_SS;
        var startY = src.cySs();
        var endX = tgt.columnLeftXSs() - NoteGeometry.GLISSANDO_DRAWN_GAP_SS;
        var endY = tgt.cySs();

        // Conditional flag push — leading note (up-stem only).
        // Grace notes always stem up and are treated the same as up-stem notes.
        var srcNote = src.note();

        if ((srcNote.isUpper() || srcNote.getType().isGraceNote()) && src.flagBBoxLayout() != null) {
            var flagBBox = src.flagBBoxLayout();

            if (flagBBox.intersectsLine(startX, startY, endX, endY)) {
                startX = flagBBox.getMaxX() + NoteGeometry.GLISSANDO_DRAWN_GAP_SS;
            }
        }

        // Conditional flag push — trailing note (down-stem only).
        if (!tgt.note().isUpper() && tgt.flagBBoxLayout() != null) {
            var flagBBox = tgt.flagBBoxLayout();

            if (flagBBox.intersectsLine(startX, startY, endX, endY)) {
                endX = flagBBox.getMinX() - NoteGeometry.GLISSANDO_DRAWN_GAP_SS;
            }
        }

        // Reject crossed endpoints with the cheap comparison before computing the length.
        if (endX <= startX) {
            return null;
        }

        var dx = endX - startX;
        var dy = endY - startY;
        var length = Math.sqrt(dx * dx + dy * dy);

        if (length < MIN_RECT_LENGTH_SS) {
            return null;
        }

        var angle = Math.atan2(dy, dx);

        return new Endpoints(startX, startY, endX, endY, angle, length);
    }

    /**
     * Renders a filled rectangle between two note columns.
     * <p>
     * For connecting glissandos, the shape spans from the source column's right edge
     * to the target column's left edge, each offset by {@link NoteGeometry#GLISSANDO_DRAWN_GAP_SS},
     * with conditional flag-bbox avoidance on either end.
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

        var thicknessSs = LineThickness.getInstance().glissandoSs();

        try (var ignored = GraphicsState.save(g2, COLOR)) {
            g2.setColor(color);
            GraphicUtils.drawRoundedLine(g2,
                endpoints.startXSs(), endpoints.startYSs(),
                endpoints.endXSs(), endpoints.endYSs(),
                thicknessSs);
        }
    }

}
