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
import static songscribe.util.GraphicsState.Property.TRANSFORM;

import module java.desktop;

import org.jspecify.annotations.Nullable;

import songscribe.dom.Line;
import songscribe.dom.StaffElement;
import songscribe.layout.LayoutResult;
import songscribe.layout.LineThickness;
import songscribe.dom.ScaleContext;
import songscribe.util.GraphicsState;

/**
 * Renders glissando lines connecting notes as filled rectangles.
 * <p>
 * Two types are supported: CONNECTED (note to note) and SLIDE_OUT (short
 * diagonal extension past the last note at 30 degrees).
 * <p>
 * Endpoints are placed at the trailing edge of the leading note's column and the leading
 * edge of the trailing note's column, each offset outward by {@code GLISSANDO_DRAWN_GAP_SS}.
 * Both endpoints sit at their own notehead-centre Y; the drawn line's angle is derived from
 * those two points. When the straight line intersects a flag's bounding box, the relevant
 * endpoint is pushed past the flag's far edge by the same gap.
 * <p>
 * Glissando data is stored on the source note via {@link StaffElement#getGlissando()}.
 */
public final class GlissandoRenderer {

    // ==========================================================================
    // Constants
    // ==========================================================================

    /**
     * Minimum rendered glissando length in staff spaces. Glissandos shorter than this are not drawn.
     * 0.75 ss stays clearly visible at tight spacing (notably a target carrying an accidental)
     * (refs #443).
     */
    private static final double MIN_RECT_LENGTH_SS = 0.75;

    /**
     * Length of a slide-out glissando in staff spaces. Lowered from 1.75 to match the shorter, more
     * consistent connecting-glissando lengths produced by the gap rework (refs #443).
     */
    private static final double SLIDE_OUT_LENGTH_SS = 1.0;

    /** Angle of a slide-out glissando below horizontal, in degrees. */
    private static final double SLIDE_OUT_ANGLE_DEG = 30.0;

    /**
     * Gap between the note column edge and the glissando endpoint, in staff spaces.
     * Keeps the drawn line clear of the note's ink on both ends.
     */
    static final double GLISSANDO_DRAWN_GAP_SS = 0.33;

    /** Hit-test tolerance in pixels (wider than visual thickness for easier clicking). */
    private static final double HIT_THICKNESS_PX = 8.0;

    // ==========================================================================
    // Singleton
    // ==========================================================================

    private static final GlissandoRenderer INSTANCE = new GlissandoRenderer();

    private GlissandoRenderer() {
    }

    /**
     * Returns the singleton instance.
     */
    public static GlissandoRenderer getInstance() {
        return INSTANCE;
    }

    // ==========================================================================
    // Rendering
    // ==========================================================================

    /**
     * Renders glissandos for all notes in a line.
     *
     * @param g2    Graphics context
     * @param line  The line containing notes
     * @param invariants   Line invariants
     * @param frame Element frame (line-level)
     */
    public void renderGlissandosFromLine(
        Graphics2D g2,
        Line line,
        LineInvariants invariants,
        ElementFrame frame
    ) {
        for (var i = 0; i < line.effectiveElementCount(); i++) {
            var note = line.getElement(i);

            if (note.getGlissando() != null) {
                renderGlissando(g2, line, note, i, invariants, frame);
            }
        }
    }

    /**
     * Renders a glissando for a specific note.
     *
     * @param g2        Graphics context
     * @param line      The line containing the note
     * @param note      The note with the glissando
     * @param noteIndex Index of the note in the line
     * @param invariants       Line invariants
     * @param frame     Element frame (line-level)
     */
    public void renderGlissando(
        Graphics2D g2,
        Line line,
        StaffElement note,
        int noteIndex,
        LineInvariants invariants,
        ElementFrame frame
    ) {
        var glissando = note.getGlissando();

        if (glissando == null) {
            return;
        }

        var layoutResult = invariants.getLayoutResult();
        var middleLineYSs = invariants.getMiddleLineYSs();
        var src = resolveNoteContext(note, noteIndex, line, layoutResult, middleLineYSs);
        var tgt = resolveTargetContext(glissando.type, noteIndex, line, layoutResult, middleLineYSs);

        // Apply preview shift so glissandos track their notes during grace-note insert preview
        if (frame.hasPreviewShift()) {
            var shiftFromIndex = frame.previewShiftFromIndex();
            var shiftSs = frame.previewShiftSs();

            if (noteIndex >= shiftFromIndex) {
                src = src.shiftedX(shiftSs);
            }

            if (tgt != null && noteIndex + 1 >= shiftFromIndex) {
                tgt = tgt.shiftedX(shiftSs);
            }
        }

        // A connected glissando between notes at the same pitch is musically meaningless — hide it
        if (tgt != null && src.note().getPitch() == tgt.note().getPitch()) {
            return;
        }

        var color = determineGlissandoColor(noteIndex, glissando.type, invariants);

        render(g2, src, tgt, glissando, color);
    }

    /**
     * Determines the color for a glissando based on playback and selection state.
     * <p>
     * Delegates to {@link LineInvariants#getElementColor(int)} for the
     * common edit-mode / playback / selection checks, then adds
     * glissando-specific selection rules on top (standalone glissando selection,
     * implied target-note selection for CONNECTED type).
     */
    Color determineGlissandoColor(
        int noteIndex,
        StaffElement.Glissando.Type type,
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

        // Standalone glissando selection
        if (selectionProvider.isGlissandoSelected(noteIndex, lineIndex)) {
            return invariants.getSelectionColor();
        }

        // Implied by target note selection (CONNECTED only)
        if (type == StaffElement.Glissando.Type.CONNECTED
            && selectionProvider.isElementSelected(noteIndex + 1, lineIndex)) {
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
     * @param type        Glissando type to preview (CONNECTED or SLIDE_OUT)
     * @param line        The line containing the notes
     * @param invariants         Line invariants
     */
    public void renderPreviewGlissando(
        Graphics2D g2,
        int sourceIndex,
        StaffElement.Glissando.Type type,
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
        var tgt = resolveTargetContext(type, sourceIndex, line, layoutResult, middleLineYSs);

        render(g2, src, tgt, null, g2.getColor());
    }

    /**
     * Hit-tests all glissandos in a line against a click point in staff-space coordinates.
     * Uses cached geometry from the most recent render pass.
     *
     * @param clickXSs click X in staff spaces
     * @param clickYSs click Y in staff spaces
     * @param line     the line to test
     * @return the note index of the hit glissando's owner, or -1 if no hit
     */
    public int hitTestGlissando(double clickXSs, double clickYSs, Line line) {
        var halfHitSs = ScaleContext.pxToSs(HIT_THICKNESS_PX) / 2.0;

        for (var i = 0; i < line.effectiveElementCount(); i++) {
            var glissando = line.getElement(i).getGlissando();

            if (glissando == null || !glissando.hasCachedGeometry) {
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
     * Immutable record holding the computed glissando endpoint positions in layout space,
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
     * Resolves the target note context for a CONNECTED glissando, or returns null
     * for SLIDE_OUT or if no next note exists.
     */
    @Nullable
    private NoteContext resolveTargetContext(
        StaffElement.Glissando.Type type, int sourceIndex, Line line,
        LayoutResult layoutResult, double middleLineYSs
    ) {
        if (type != StaffElement.Glissando.Type.CONNECTED || sourceIndex + 1 >= line.elementCount()) {
            return null;
        }

        var nextElement = line.getElement(sourceIndex + 1);

        return resolveNoteContext(nextElement, sourceIndex + 1, line, layoutResult, middleLineYSs);
    }

    /**
     * Computes the glissando start/end positions in layout space (staff-space coordinates).
     * Returns null if the glissando is too short to render (endpoints crossed or
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
     * @param tgt Target note context, or null for SLIDE_OUT
     * @return the glissando endpoints, or null if the glissando cannot render
     */
    static GlissandoRenderer.@Nullable Endpoints computeEndpoints(
        NoteContext src, @Nullable NoteContext tgt
    ) {
        // Base endpoints: column-edge ± gap at notehead-centre Y.
        var startX = src.columnRightXSs() + GLISSANDO_DRAWN_GAP_SS;
        var startY = src.cySs();

        // Slide-out direction (only meaningful when tgt == null); computed once and shared
        // between the leading-flag test and the final endpoint resolution below.
        var slideOutCos = tgt == null ? Math.cos(Math.toRadians(SLIDE_OUT_ANGLE_DEG)) : 0.0;
        var slideOutSin = tgt == null ? Math.sin(Math.toRadians(SLIDE_OUT_ANGLE_DEG)) : 0.0;

        double endX;
        double endY;

        if (tgt == null) {
            // SLIDE_OUT: direction fixed by angle; endpoint resolved after the flag check below.
            endX = Double.NaN;
            endY = Double.NaN;
        } else {
            endX = tgt.columnLeftXSs() - GLISSANDO_DRAWN_GAP_SS;
            endY = tgt.cySs();
        }

        // Conditional flag push — leading note (up-stem only).
        // Grace notes always stem up and are treated the same as up-stem notes.
        var srcNote = src.note();

        if ((srcNote.isUpper() || srcNote.getType().isGraceNote()) && src.flagBBoxLayout() != null) {
            var flagBBox = src.flagBBoxLayout();

            // For SLIDE_OUT, derive the nominal end point along the slide-out ray for the test.
            double testEndX;
            double testEndY;

            if (tgt == null) {
                testEndX = startX + slideOutCos * SLIDE_OUT_LENGTH_SS;
                testEndY = startY + slideOutSin * SLIDE_OUT_LENGTH_SS;
            } else {
                testEndX = endX;
                testEndY = endY;
            }

            if (flagBBox.intersectsLine(startX, startY, testEndX, testEndY)) {
                startX = flagBBox.getMaxX() + GLISSANDO_DRAWN_GAP_SS;
            }
        }

        // Conditional flag push — trailing note (down-stem only, CONNECTED only).
        if (tgt != null && !tgt.note().isUpper() && tgt.flagBBoxLayout() != null) {
            var flagBBox = tgt.flagBBoxLayout();

            if (flagBBox.intersectsLine(startX, startY, endX, endY)) {
                endX = flagBBox.getMinX() - GLISSANDO_DRAWN_GAP_SS;
            }
        }

        // Resolve SLIDE_OUT endpoint from the (flag-adjusted) start.
        if (tgt == null) {
            endX = startX + slideOutCos * SLIDE_OUT_LENGTH_SS;
            endY = startY + slideOutSin * SLIDE_OUT_LENGTH_SS;
        }

        // Reject crossed endpoints with the cheap comparison before computing the length.
        if (tgt != null && endX <= startX) {
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
     * For CONNECTED glissandos, the shape spans from the source column's right edge
     * to the target column's left edge, each offset by {@code GLISSANDO_DRAWN_GAP_SS},
     * with conditional flag-bbox avoidance on either end. For SLIDE_OUT glissandos,
     * it extends from the source column's right edge at {@link #SLIDE_OUT_ANGLE_DEG}
     * degrees for {@link #SLIDE_OUT_LENGTH_SS} staff spaces.
     *
     * @param g2           Graphics context (staff-space coordinate system)
     * @param src          Source note context
     * @param tgt          Target note context, or null for SLIDE_OUT
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

        try (var ignored = GraphicsState.save(g2, TRANSFORM, COLOR)) {
            g2.setColor(color);
            g2.translate(endpoints.startXSs(), endpoints.startYSs());
            g2.rotate(endpoints.angle());
            var thicknessSs = LineThickness.getInstance().glissandoSs();
            g2.fill(new RoundRectangle2D.Double(
                0, -thicknessSs / 2.0,
                length, thicknessSs,
                thicknessSs, thicknessSs
            ));
        }
    }

}
