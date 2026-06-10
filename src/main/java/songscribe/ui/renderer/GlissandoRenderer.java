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

import songscribe.dom.Song;
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

    /** Minimum rendered glissando length in staff spaces. Glissandos shorter than this are not drawn. */
    private static final double MIN_RECT_LENGTH_SS = 1.0;

    /** Length of a slide-out glissando in staff spaces. */
    private static final double SLIDE_OUT_LENGTH_SS = 1.75;

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
    // Public Position Methods (for HorizontalAdjustment)
    // ==========================================================================

    /**
     * Calculates the starting X position for a glissando (public static version).
     * <p>
     * Returns the actual glissando start X after area exit and gap computation,
     * not the notehead center. Used by HorizontalAdjustment to position UI handles
     * at the glissando endpoints.
     *
     * @param xIndex        Index of the source note in the line
     * @param glissando     The glissando data
     * @param lineIndex     Index of the line in the song
     * @param song   The song containing the line
     * @param layoutResult  Layout result for resolving note positions
     * @param middleLineYSs Y position of the middle staff line in staff spaces
     * @return X coordinate for glissando start in staff spaces
     */
    public static double getGlissandoX1Ss(
        int xIndex,
        StaffElement.Glissando glissando,
        int lineIndex,
        Song song,
        LayoutResult layoutResult,
        double middleLineYSs
    ) {
        var endpoints = INSTANCE.computeEndpointsForNote(
            xIndex, glissando, lineIndex, song, layoutResult, middleLineYSs);

        if (endpoints != null) {
            return endpoints.startXSs;
        }

        // Fallback to notehead center if glissando cannot render
        var note = song.getLine(lineIndex).getElement(xIndex);
        return noteheadCenterXSs(note, layoutResult);
    }

    /**
     * Calculates the ending X position for a glissando (public static version).
     * <p>
     * Returns the actual glissando end X after area exit and gap computation,
     * not the notehead center. Used by HorizontalAdjustment to position UI handles
     * at the glissando endpoints.
     *
     * @param xIndex        Index of the source note in the line
     * @param glissando     The glissando data
     * @param lineIndex     Index of the line in the song
     * @param song   The song containing the line
     * @param layoutResult  Layout result for resolving note positions
     * @param middleLineYSs Y position of the middle staff line in staff spaces
     * @return X coordinate for glissando end in staff spaces
     */
    public static double getGlissandoX2Ss(
        int xIndex,
        StaffElement.Glissando glissando,
        int lineIndex,
        Song song,
        LayoutResult layoutResult,
        double middleLineYSs
    ) {
        var endpoints = INSTANCE.computeEndpointsForNote(
            xIndex, glissando, lineIndex, song, layoutResult, middleLineYSs);

        if (endpoints != null) {
            return endpoints.endXSs;
        }

        // Fallback to notehead center if glissando cannot render
        var line = song.getLine(lineIndex);

        if (glissando.type == StaffElement.Glissando.Type.CONNECTED) {
            return noteheadCenterXSs(line.getElement(xIndex + 1), layoutResult);
        }

        return noteheadCenterXSs(line.getElement(xIndex), layoutResult);
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
                src = new NoteContext(src.note(), src.cxSs() + shiftSs, src.cySs(), src.offsetArea(), src.offsetBounds());
            }

            if (tgt != null && noteIndex + 1 >= shiftFromIndex) {
                tgt = new NoteContext(tgt.note(), tgt.cxSs() + shiftSs, tgt.cySs(), tgt.offsetArea(), tgt.offsetBounds());
            }
        }

        // A connected glissando between notes at the same pitch is musically meaningless — hide it
        if (tgt != null && src.note().getPitch() == tgt.note().getPitch()) {
            return;
        }

        var color = determineGlissandoColor(noteIndex, glissando.type, invariants);

        render(g2, src, tgt, glissando.x1Translate, glissando.type == StaffElement.Glissando.Type.CONNECTED ? glissando.x2Translate : 0, glissando, color);
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
        var color = invariants.getElementColor(noteIndex);

        if (color != Color.BLACK) {
            return color;
        }

        var selectionProvider = invariants.getSelectionProvider();

        if (selectionProvider == null) {
            return Color.BLACK;
        }

        var lineIndex = invariants.getLineIndex();

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

        render(g2, src, tgt, 0, 0, null, g2.getColor());
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

    /**
     * Computes the glissando endpoints for a note, building areas and
     * delegating to {@link #computeEndpoints}. Used by the public static
     * endpoint methods.
     *
     * @return The glissando endpoints, or null if the glissando cannot render
     */
    private GlissandoRenderer.@Nullable Endpoints computeEndpointsForNote(
        int xIndex,
        StaffElement.Glissando glissando,
        int lineIndex,
        Song song,
        LayoutResult layoutResult,
        double middleLineYSs
    ) {
        var line = song.getLine(lineIndex);
        var note = line.getElement(xIndex);
        var src = resolveNoteContext(note, xIndex, line, layoutResult, middleLineYSs);
        var tgt = resolveTargetContext(glissando.type, xIndex, line, layoutResult, middleLineYSs);

        return computeEndpoints(src, tgt,
            glissando.x1Translate, glissando.type == StaffElement.Glissando.Type.CONNECTED ? glissando.x2Translate : 0);
    }

    // ==========================================================================
    // Endpoint Computation and Rendering
    // ==========================================================================

    /**
     * Resolved geometry for a single note: center position, offset area, and note reference.
     */
    record NoteContext(
        StaffElement note,
        double cxSs, double cySs,
        Area offsetArea,
        Rectangle2D offsetBounds
    ) {}

    /**
     * Immutable record holding the computed glissando endpoint positions in layout space.
     */
    record Endpoints(double startXSs, double startYSs, double endXSs, double endYSs, double angle) {}

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

        return new NoteContext(note, cx, cy, entry.offsetArea(), entry.offsetBounds());
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
     * @param x1Translate  User drag offset for source endpoint
     * @param x2Translate  User drag offset for target endpoint
     * @return The glissando endpoints, or null if the glissando cannot render
     */
    static GlissandoRenderer.@Nullable Endpoints computeEndpoints(
        NoteContext src, @Nullable NoteContext tgt,
        double x1Translate, double x2Translate
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

        // Source: find entry point on offset area in local space
        var localCx1 = NoteGeometry.getNoteheadRightEdgeSs(src.note) / 2.0;
        var offset1X = src.cxSs - localCx1;
        var offset1Y = src.cySs;

        var stepSs = ScaleContext.pxToSs(1.0);
        var entry1 = findNoteAreaEntryPoint(src.offsetArea, src.offsetBounds, localCx1, 0, nx, ny, stepSs);

        var effectiveX1Translate = Math.max(x1Translate, -NoteAreaBuilder.MIN_GAP_SS);
        var startX = entry1.x + offset1X + nx * effectiveX1Translate;
        var startY = entry1.y + offset1Y + ny * effectiveX1Translate;

        double endX, endY;

        if (tgt == null) {
            endX = startX + nx * SLIDE_OUT_LENGTH_SS;
            endY = startY + ny * SLIDE_OUT_LENGTH_SS;
        } else {
            // Target: find entry point on offset area in local space (reverse direction)
            var localCx2 = NoteGeometry.getNoteheadRightEdgeSs(tgt.note) / 2.0;
            var offset2X = tgt.cxSs - localCx2;
            var offset2Y = tgt.cySs;

            var entry2 = findNoteAreaEntryPoint(tgt.offsetArea, tgt.offsetBounds, localCx2, 0, -nx, -ny, stepSs);

            var effectiveX2Translate = Math.max(x2Translate, -NoteAreaBuilder.MIN_GAP_SS);
            endX = entry2.x + offset2X - nx * effectiveX2Translate;
            endY = entry2.y + offset2Y - ny * effectiveX2Translate;
        }

        // Compute glissando length
        dx = endX - startX;
        dy = endY - startY;
        var length = Math.sqrt(dx * dx + dy * dy);

        // If the endpoints have crossed or the glissando is too short, skip rendering
        if ((dx * nx + dy * ny) < 0 || length < MIN_RECT_LENGTH_SS) {
            return null;
        }

        return new Endpoints(startX, startY, endX, endY, Math.atan2(ny, nx));
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
     * @param x1Translate  User drag offset for source endpoint
     * @param x2Translate  User drag offset for target endpoint
     */
    private void render(
        Graphics2D g2,
        NoteContext src, @Nullable NoteContext tgt,
        double x1Translate, double x2Translate,
        StaffElement.@Nullable Glissando glissando,
        Color color
    ) {
        var endpoints = computeEndpoints(src, tgt, x1Translate, x2Translate);

        if (endpoints == null) {
            return;
        }

        var dx = endpoints.endXSs() - endpoints.startXSs();
        var dy = endpoints.endYSs() - endpoints.startYSs();
        var length = Math.sqrt(dx * dx + dy * dy);

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
     * Finds the glissando endpoint on the boundary of an offset (pre-expanded) note area,
     * by stepping inward from the bounding box edge until the first intersection.
     * The gap is already baked into the offset area, so no additional gap is needed here.
     * <p>
     * The algorithm inverts the classic outward-sweep: instead of starting at the center
     * and sweeping out, it starts at the bounding box edge and steps inward ~1 px at a time:
     *
     * <pre>
     *   bbox edge         offset area boundary     notehead center
     *       |                    |                      |
     *       v                    v                      v
     *       * ← ← ← ← ← * ← ← * ← ← ← ← ← ← ← ← ← *
     *       ^             ^
     *       startT        endpoint (one step past first intersect)
     * </pre>
     *
     * @param offsetArea   The pre-expanded note area (includes gap)
     * @param offsetBounds Bounding box of the offset area
     * @param cx           Notehead center X in local space
     * @param cy           Notehead center Y in local space
     * @param nx           Normalized X component of the glissando direction
     * @param ny           Normalized Y component of the glissando direction
     * @param stepSs       Step size in staff spaces (typically 1px)
     * @return The endpoint just outside the offset area boundary
     */
    static Point2D.Double findNoteAreaEntryPoint(
        Area offsetArea,
        Rectangle2D offsetBounds,
        double cx, double cy,
        double nx, double ny,
        double stepSs
    ) {
        // Zero-direction guard
        if (nx == 0 && ny == 0) {
            return new Point2D.Double(cx, cy);
        }

        // Precompute bounding-box half-dimensions of the rotated tip rectangle
        var halfStep = stepSs / 2.0;
        var halfThickness = ScaleContext.pxToSs(RECT_THICKNESS_PX) / 2.0;
        var halfW = halfStep * Math.abs(nx) + halfThickness * Math.abs(ny);
        var halfH = halfStep * Math.abs(ny) + halfThickness * Math.abs(nx);

        // Start at the point where the outward ray exits the bounding box
        var startT = computeFarBoundsT(offsetBounds, cx, cy, nx, ny);

        // Step inward; return the last non-intersecting position
        for (var t = startT; t >= 0; t -= stepSs) {
            var px = cx + nx * t;
            var py = cy + ny * t;
            var tipRect = new Rectangle2D.Double(px - halfW, py - halfH, halfW * 2, halfH * 2);

            if (offsetArea.intersects(tipRect)) {
                var endT = t + stepSs;
                return new Point2D.Double(cx + nx * endT, cy + ny * endT);
            }
        }

        // Fallback: center is outside the area
        return new Point2D.Double(cx, cy);
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
