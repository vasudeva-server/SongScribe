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
import songscribe.layout.NoteGeometry;
import songscribe.dom.ScaleContext;
import songscribe.util.GraphicsState;

/**
 * Renders glissando lines connecting notes as filled rectangles.
 * <p>
 * Two types are supported: CONNECTED (note to note) and SLIDE_OUT (short
 * diagonal extension past the last note at 30 degrees).
 * <p>
 * The glissando endpoints are computed using an inward-search algorithm that pre-expands
 * the note area by the gap distance, then steps inward from the bounding box edge until
 * the first intersection with the expanded area. A gap is baked into the offset area so
 * the glissando never overlaps any note element.
 * <p>
 * Glissando data is stored on the source note via {@link StaffElement#getGlissando()}.
 */
public final class GlissandoRenderer {

    // ==========================================================================
    // Constants
    // ==========================================================================

    /**
     * Minimum rendered glissando length in staff spaces. Glissandos shorter than this are not drawn.
     * Lowered from 1.0: the endpoint search now measures the gap from the bare ink exit rather than
     * the pre-expanded area, yielding a slightly shorter drawn line, and 0.75 ss stays clearly
     * visible at tight spacing (notably a target carrying an accidental) (refs #443).
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
     * Minimum horizontal distance (in staff spaces) that must be reserved between two
     * note origins for a glissando to render. Equals the minimum glissando length plus
     * the gap on each side.
     */
    public static final double MIN_HORIZONTAL_RESERVATION_SS =
        MIN_RECT_LENGTH_SS + 2 * NoteAreaBuilder.MIN_GAP_SS;

    /** Glissando thickness in pixels. */
    private static final double RECT_THICKNESS_PX = 2.0;

    /** Hit-test tolerance in pixels (wider than visual thickness for easier clicking). */
    private static final double HIT_THICKNESS_PX = 8.0;

    // ==========================================================================
    // Singleton
    // ==========================================================================

    private static final GlissandoRenderer INSTANCE = new GlissandoRenderer();

    private final NoteAreaBuilder noteAreaBuilder = new NoteAreaBuilder();

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
    // Position Calculation
    // ==========================================================================

    /**
     * Returns the notehead center X for a note, in staff spaces.
     */
    private static double noteheadCenterXSs(
        StaffElement note,
        LayoutResult layoutResult
    ) {
        return layoutResult.getElementXSs(note) + NoteGeometry.getNoteheadRightEdgeSs(note) / 2.0;
    }

    // ==========================================================================
    // Endpoint Computation and Rendering
    // ==========================================================================

    /**
     * Resolved geometry for a single note: center position, base ink area, offset area, and
     * note reference.
     */
    record NoteContext(
        StaffElement note,
        double cxSs, double cySs,
        Area area,
        Area offsetArea,
        Rectangle2D offsetBounds
    ) {

        /** Returns a copy shifted right by {@code shiftSs} along X, leaving geometry otherwise intact. */
        NoteContext shiftedX(double shiftSs) {
            return new NoteContext(note, cxSs + shiftSs, cySs, area, offsetArea, offsetBounds);
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
     * Resolves the geometry context for a note at the given index: notehead center
     * position, staff-position Y coordinate, and composite area for gap calculation.
     */
    private NoteContext resolveNoteContext(
        StaffElement note, int noteIndex, Line line,
        LayoutResult layoutResult, double middleLineYSs
    ) {
        var beamed = line.findBeamAt(noteIndex) != null;
        var cx = noteheadCenterXSs(note, layoutResult);
        var cy = RenderingUtils.noteStaffPositionToCoordinateSs(note.getStaffPosition(), middleLineYSs);
        var entry = noteAreaBuilder.getOrBuildArea(note, beamed);

        return new NoteContext(note, cx, cy, entry.area(), entry.offsetArea(), entry.offsetBounds());
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
     * length < MIN_RECT_LENGTH_SS).
     * <p>
     * Both render() and the public endpoint methods delegate to this.
     *
     * @param src          Source note context
     * @param tgt          Target note context, or null for SLIDE_OUT
     * @return The glissando endpoints, or null if the glissando cannot render
     */
    static GlissandoRenderer.@Nullable Endpoints computeEndpoints(
        NoteContext src, @Nullable NoteContext tgt
    ) {
        // Tangent direction in layout space
        double dx, dy;

        if (tgt == null) {
            dx = Math.cos(Math.toRadians(SLIDE_OUT_ANGLE_DEG));
            dy = Math.sin(Math.toRadians(SLIDE_OUT_ANGLE_DEG));
        } else {
            dx = tgt.cxSs - src.cxSs;
            dy = tgt.cySs - src.cySs;
        }

        var len = Math.sqrt(dx * dx + dy * dy);

        if (tgt != null && len == 0) {
            return null;
        }

        var nx = dx / len;
        var ny = dy / len;

        // Areas are in local coordinates (origin at notehead glyph origin).
        // Local notehead center is at (noteheadRightEdge/2, 0).
        // Layout offset from local origin: (cx - localCenterX, cy).
        // Exit points found in local space are translated to layout space by adding the offset.

        // Source: find the exit point on the note ink in local space
        var localCx1 = NoteGeometry.getNoteheadRightEdgeSs(src.note) / 2.0;
        var offset1X = src.cxSs - localCx1;
        var offset1Y = src.cySs;

        var stepSs = ScaleContext.pxToSs(1.0);
        var entry1 = findNoteAreaEntryPoint(
            src.area, src.offsetArea, src.offsetBounds, localCx1, 0, nx, ny, stepSs, NoteAreaBuilder.MIN_GAP_SS);

        var startX = entry1.x + offset1X;
        var startY = entry1.y + offset1Y;

        double endX, endY;

        if (tgt == null) {
            endX = startX + nx * SLIDE_OUT_LENGTH_SS;
            endY = startY + ny * SLIDE_OUT_LENGTH_SS;
        } else {
            // Target: find the exit point on the note ink in local space (reverse direction)
            var localCx2 = NoteGeometry.getNoteheadRightEdgeSs(tgt.note) / 2.0;
            var offset2X = tgt.cxSs - localCx2;
            var offset2Y = tgt.cySs;

            var entry2 = findNoteAreaEntryPoint(
                tgt.area, tgt.offsetArea, tgt.offsetBounds, localCx2, 0, -nx, -ny, stepSs, NoteAreaBuilder.MIN_GAP_SS);

            endX = entry2.x + offset2X;
            endY = entry2.y + offset2Y;
        }

        // Compute glissando length
        dx = endX - startX;
        dy = endY - startY;
        var length = Math.sqrt(dx * dx + dy * dy);

        // If the endpoints have crossed or the glissando is too short, skip rendering
        if ((dx * nx + dy * ny) < 0 || length < MIN_RECT_LENGTH_SS) {
            return null;
        }

        return new Endpoints(startX, startY, endX, endY, Math.atan2(ny, nx), length);
    }

    /**
     * Renders a filled rectangle between two note areas.
     * <p>
     * For CONNECTED glissandos, the shape spans from the source note area exit
     * to the target note area exit, with gaps on both sides. For SLIDE_OUT
     * glissandos, it extends from the source area exit at a fixed 45-degree
     * angle for {@link #MIN_RECT_LENGTH_SS}.
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
            var thicknessSs = ScaleContext.pxToSs(RECT_THICKNESS_PX);
            g2.fill(new RoundRectangle2D.Double(
                0, -thicknessSs / 2.0,
                length, thicknessSs,
                thicknessSs, thicknessSs
            ));
        }
    }

    // ==========================================================================
    // Offset Area and Inward-Search for Tangent-Area Intersection
    // ==========================================================================

    /**
     * Finds the glissando endpoint near a note, with a gap that stays visually consistent
     * across glissando angles while never crowding the stem or flags.
     * <p>
     * The ray starts at the notehead center and runs outward along the glissando direction.
     * The endpoint is computed in three steps:
     * <ol>
     *   <li><b>Exit the ink.</b> Step inward from the bounding-box edge until the centerline
     *       first enters the bare note ink — where the ray leaves the note.</li>
     *   <li><b>Back off along the ray.</b> Place the endpoint {@code gapSs} further out along
     *       the ray. Measuring the gap <em>along the line</em> (rather than perpendicular to the
     *       ink) keeps the visible gap constant for every angle: a ray from the center exits a
     *       convex notehead almost perpendicular, so along-line and perpendicular coincide there.</li>
     *   <li><b>Clamp.</b> If that endpoint still lies inside the offset area — i.e. within
     *       {@code gapSs} of any ink — step outward until it clears. This is a no-op for notehead
     *       exits and engages only where the ray grazes the stem or a flag obliquely, preventing
     *       the line from running too close to them at steep angles.</li>
     * </ol>
     *
     * <pre>
     *   bbox edge          ink boundary          notehead center
     *       |                    |                      |
     *       v                    v                      v
     *       * ← ← ← ← ← ← ← ← ← *  ← ← ← ← ← ← ← ← ← ← *
     *                    ^       ^
     *                  endpoint  crossing (endpoint = crossing + gap, then clamped)
     * </pre>
     *
     * @param area         The bare note ink area (notehead, stem, flags, …)
     * @param offsetArea   The note ink pre-expanded by the gap, used only for the clamp
     * @param offsetBounds Bounding box of the offset area (encloses the search range)
     * @param cx           Notehead center X in local space
     * @param cy           Notehead center Y in local space
     * @param nx           Normalized X component of the glissando direction
     * @param ny           Normalized Y component of the glissando direction
     * @param stepSs       Step size in staff spaces (typically 1px)
     * @param gapSs        Gap between the note ink and the endpoint, measured along the ray
     * @return The endpoint, or the center if the ray never meets the ink
     */
    static Point2D.Double findNoteAreaEntryPoint(
        Area area,
        Area offsetArea,
        Rectangle2D offsetBounds,
        double cx, double cy,
        double nx, double ny,
        double stepSs, double gapSs
    ) {
        // Zero-direction guard
        if (nx == 0 && ny == 0) {
            return new Point2D.Double(cx, cy);
        }

        // Start at the point where the outward ray exits the bounding box
        var startT = computeFarBoundsT(offsetBounds, cx, cy, nx, ny);

        // Step 1: find where the ray exits the bare note ink.
        double crossingT = -1;

        for (var t = startT; t >= 0; t -= stepSs) {
            if (area.contains(cx + nx * t, cy + ny * t)) {
                crossingT = t;
                break;
            }
        }

        // Fallback: the ray never meets the ink (center outside the area)
        if (crossingT < 0) {
            return new Point2D.Double(cx, cy);
        }

        // Step 2: back off a constant gap along the ray for a consistent visual gap.
        var endT = crossingT + gapSs;

        // Step 3: clamp so the endpoint never sits within the gap of any ink (stem/flag safety).
        // offsetArea.contains(p) is exactly "distance to ink <= gap"; the loop terminates once
        // the point leaves the offset area, which it must do before reaching the bounding box edge.
        while (endT < startT && offsetArea.contains(cx + nx * endT, cy + ny * endT)) {
            endT += stepSs;
        }

        return new Point2D.Double(cx + nx * endT, cy + ny * endT);
    }

    static double computeFarBoundsT(
        Rectangle2D bounds,
        double cx, double cy,
        double nx, double ny
    ) {
        double tx;

        if (nx > 0) {
            tx = (bounds.getMaxX() - cx) / nx;
        } else if (nx < 0) {
            tx = (bounds.getMinX() - cx) / nx;
        } else {
            tx = Double.MAX_VALUE;
        }

        double ty;

        if (ny > 0) {
            ty = (bounds.getMaxY() - cy) / ny;
        } else if (ny < 0) {
            ty = (bounds.getMinY() - cy) / ny;
        } else {
            ty = Double.MAX_VALUE;
        }

        return Math.min(tx, ty);
    }

}
