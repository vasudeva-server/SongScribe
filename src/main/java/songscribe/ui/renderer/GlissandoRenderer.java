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

import static songscribe.ui.renderer.GraphicsState.Property.COLOR;
import static songscribe.ui.renderer.GraphicsState.Property.STROKE;
import static songscribe.ui.renderer.GraphicsState.Property.TRANSFORM;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.geom.*;
import java.util.EnumMap;
import java.util.Map;
import java.util.WeakHashMap;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import songscribe.music.Composition;
import songscribe.music.Line;
import songscribe.music.Note;
import songscribe.music.NoteType;

import songscribe.smufl.EngravingDefaults;

import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;
import songscribe.ui.layout2.LayoutConstants;
import songscribe.ui.layout2.LayoutResult;
import songscribe.ui.layout2.ScaleContext;

/**
 * Renders glissando lines connecting notes as filled rectangles.
 * <p>
 * Two types are supported: CONNECTED (note to note) and SLIDE_OUT (short
 * diagonal extension past the last note at 45 degrees).
 * <p>
 * The glissando endpoints are computed using an inward-search algorithm that pre-expands
 * the note area by the gap distance, then steps inward from the bounding box edge until
 * the first intersection with the expanded area. A gap is baked into the offset area so
 * the glissando never overlaps any note element.
 * <p>
 * Glissando data is stored on the source note via {@link Note#getGlissando()}.
 */
public class GlissandoRenderer {

    // ==========================================================================
    // Constants
    // ==========================================================================

    private static final SMuFLMetadata METADATA = SMuFLMetadata.getInstance();
    private static final EngravingDefaults ENGRAVING = METADATA.getEngravingDefaults();

    /**
     * Minimum gap between the note area exit point and the glissando endpoint, in staff spaces.
     */
    private static final double MIN_GAP_SS = 0.3;

    /** Minimum rendered glissando length in staff spaces. Glissandos shorter than this are not drawn. */
    private static final double MIN_RECT_LENGTH_SS = 1.0;

    /** Length of a slide-out glissando in staff spaces. */
    private static final double SLIDE_OUT_LENGTH_SS = 1.5;

    /**
     * Minimum horizontal distance (in staff spaces) that must be reserved between two
     * note origins for a glissando to render. Equals the minimum glissando length plus
     * the gap on each side.
     */
    public static final double MIN_HORIZONTAL_RESERVATION_SS =
        MIN_RECT_LENGTH_SS + 2 * MIN_GAP_SS;  // 1.0 + 0.6 = 1.6 ss

    /** Glissando thickness in staff spaces (2px). */
    private static final double RECT_THICKNESS_SS = ScaleContext.getInstance().fromPixels(2.0);

    // ==========================================================================
    // Note Area Constants and Cached Shapes
    // ==========================================================================

    /** Cached glyph outline for filled (black) noteheads (quarter, eighth, etc.). */
    private static final Shape NOTEHEAD_BLACK_SHAPE;

    /** Cached glyph outline for whole noteheads. */
    private static final Shape NOTEHEAD_WHOLE_SHAPE;

    /** Cached glyph outline for grace noteheads. */
    private static final Shape NOTEHEAD_GRACE_SHAPE;

    /** Cached glyph outlines for flag glyphs at standard size. */
    private static final Map<SMuFLGlyph, Shape> FLAG_SHAPES;

    /** Cached glyph outline for the grace note flag (FLAG_8TH_UP at grace scale). */
    private static final Shape FLAG_8TH_UP_GRACE_SHAPE;

    static {
        var frc = new FontRenderContext(null, true, true);
        var font = BaseElementRenderer.BRAVURA_FONT;
        var graceFont = BaseElementRenderer.BRAVURA_FONT_GRACE;

        NOTEHEAD_BLACK_SHAPE = glyphOutline(font, frc, SMuFLGlyph.NOTEHEAD_BLACK);
        NOTEHEAD_WHOLE_SHAPE = glyphOutline(font, frc, SMuFLGlyph.NOTEHEAD_WHOLE);
        NOTEHEAD_GRACE_SHAPE = glyphOutline(graceFont, frc, SMuFLGlyph.NOTEHEAD_BLACK);

        var flagGlyphs = new SMuFLGlyph[]{
            SMuFLGlyph.FLAG_8TH_UP, SMuFLGlyph.FLAG_8TH_DOWN,
            SMuFLGlyph.FLAG_16TH_UP, SMuFLGlyph.FLAG_16TH_DOWN,
            SMuFLGlyph.FLAG_32ND_UP, SMuFLGlyph.FLAG_32ND_DOWN,
        };
        var flagShapes = new EnumMap<SMuFLGlyph, Shape>(SMuFLGlyph.class);

        for (var glyph : flagGlyphs) {
            flagShapes.put(glyph, glyphOutline(font, frc, glyph));
        }

        FLAG_SHAPES = flagShapes;
        FLAG_8TH_UP_GRACE_SHAPE = glyphOutline(graceFont, frc, SMuFLGlyph.FLAG_8TH_UP);
    }

    /**
     * Returns the glyph outline for the given SMuFL glyph, suitable for use as an Area component.
     */
    private static Shape glyphOutline(
        @NotNull Font font,
        @NotNull FontRenderContext frc,
        @NotNull SMuFLGlyph glyph
    ) {
        var glyphVector = font.createGlyphVector(frc, glyph.asString());
        return glyphVector.getOutline();
    }

    // ==========================================================================
    // Singleton
    // ==========================================================================

    private static final GlissandoRenderer INSTANCE = new GlissandoRenderer();

    /**
     * Cache key capturing the note attributes that affect the composite area shape.
     * When any attribute changes, the key won't match and the area is rebuilt.
     * <p>
     * {@code staffPosition} is only relevant when ledger lines are present
     * (it affects the area shape). For notes on the staff, it is normalized to 0
     * so that dragging through non-ledger positions doesn't cause unnecessary
     * cache misses.
     */
    private record AreaCacheKey(
        NoteType noteType, int ledgerLineCount, int staffPosition,
        int dotCount, Note.Accidental accidental, boolean upper, boolean beamed) {

        AreaCacheKey(@NotNull Note note, boolean beamed) {
            this(note.getNoteType(),
                note.getLedgerLineCount(),
                note.hasLedgerLines() ? note.getStaffPosition() : 0,
                note.getDotCount(),
                note.getAccidental(),
                note.isUpper(),
                beamed);
        }
    }

    private record AreaCacheEntry(
        AreaCacheKey key,
        Area offsetArea,
        Rectangle2D offsetBounds
    ) {}

    /** Self-validating area cache keyed by Note identity. Weak keys allow GC of removed notes. */
    private final WeakHashMap<Note, AreaCacheEntry> areaCache = new WeakHashMap<>();

    private GlissandoRenderer() {
    }

    /**
     * Returns the singleton instance.
     */
    public static @NotNull GlissandoRenderer getInstance() {
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
     * @param lineIndex     Index of the line in the composition
     * @param composition   The composition containing the line
     * @param layoutResult  Layout result for resolving note positions
     * @param middleLineYSs Y position of the middle staff line in staff spaces
     * @return X coordinate for glissando start in staff spaces
     */
    public static double getGlissandoX1Ss(
        int xIndex,
        @NotNull Note.Glissando glissando,
        int lineIndex,
        @NotNull Composition composition,
        @NotNull LayoutResult layoutResult,
        double middleLineYSs
    ) {
        var endpoints = INSTANCE.computeEndpointsForNote(
            xIndex, glissando, lineIndex, composition, layoutResult, middleLineYSs);

        if (endpoints != null) {
            return endpoints.startX;
        }

        // Fallback to notehead center if glissando cannot render
        var note = composition.getLine(lineIndex).getNote(xIndex);
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
     * @param lineIndex     Index of the line in the composition
     * @param composition   The composition containing the line
     * @param layoutResult  Layout result for resolving note positions
     * @param middleLineYSs Y position of the middle staff line in staff spaces
     * @return X coordinate for glissando end in staff spaces
     */
    public static double getGlissandoX2Ss(
        int xIndex,
        @NotNull Note.Glissando glissando,
        int lineIndex,
        @NotNull Composition composition,
        @NotNull LayoutResult layoutResult,
        double middleLineYSs
    ) {
        var endpoints = INSTANCE.computeEndpointsForNote(
            xIndex, glissando, lineIndex, composition, layoutResult, middleLineYSs);

        if (endpoints != null) {
            return endpoints.endX;
        }

        // Fallback to notehead center if glissando cannot render
        var line = composition.getLine(lineIndex);

        if (glissando.type == Note.Glissando.Type.CONNECTED) {
            return noteheadCenterXSs(line.getNote(xIndex + 1), layoutResult);
        }

        return noteheadCenterXSs(line.getNote(xIndex), layoutResult);
    }

    // ==========================================================================
    // Rendering
    // ==========================================================================

    /**
     * Renders glissandos for all notes in a line.
     *
     * @param g2   Graphics context
     * @param line The line containing notes
     * @param ctx  Render context
     */
    public void renderGlissandosFromLine(
        @NotNull Graphics2D g2,
        @NotNull Line line,
        @NotNull ElementRenderContext ctx
    ) {
        for (var i = 0; i < line.noteCount(); i++) {
            var note = line.getNote(i);

            //noinspection ObjectEquality
            if (note.getGlissando() != Note.NO_GLISSANDO) {
                renderGlissando(g2, line, note, i, ctx);
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
     * @param ctx       Render context
     */
    public void renderGlissando(
        @NotNull Graphics2D g2,
        @NotNull Line line,
        @NotNull Note note,
        int noteIndex,
        @NotNull ElementRenderContext ctx
    ) {
        var glissando = note.getGlissando();

        //noinspection ObjectEquality
        if (glissando == Note.NO_GLISSANDO) {
            return;
        }

        var layoutResult = ctx.getLayoutResult();
        var middleLineYSs = ctx.getMiddleLineYSs();
        var src = resolveNoteContext(note, noteIndex, line, layoutResult, middleLineYSs);
        var tgt = resolveTargetContext(glissando.type, noteIndex, line, layoutResult, middleLineYSs);

        // A connected glissando between notes at the same pitch is musically meaningless — hide it
        if (tgt != null && src.note().getPitch() == tgt.note().getPitch()) {
            return;
        }

        render(g2, src, tgt, glissando.x1Translate, glissando.type == Note.Glissando.Type.CONNECTED ? glissando.x2Translate : 0);
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
     * @param ctx         Render context
     */
    public void renderPreviewGlissando(
        @NotNull Graphics2D g2,
        int sourceIndex,
        @NotNull Note.Glissando.Type type,
        @NotNull Line line,
        @NotNull ElementRenderContext ctx
    ) {
        if (sourceIndex < 0 || sourceIndex >= line.noteCount()) {
            return;
        }

        var note = line.getNote(sourceIndex);
        var layoutResult = ctx.getLayoutResult();
        var middleLineYSs = ctx.getMiddleLineYSs();
        var src = resolveNoteContext(note, sourceIndex, line, layoutResult, middleLineYSs);
        var tgt = resolveTargetContext(type, sourceIndex, line, layoutResult, middleLineYSs);

        render(g2, src, tgt, 0, 0);
    }

    // ==========================================================================
    // Position Calculation
    // ==========================================================================

    /**
     * Returns the notehead center X for a note, in staff spaces.
     */
    private static double noteheadCenterXSs(
        @NotNull Note note,
        @NotNull LayoutResult layoutResult
    ) {
        return layoutResult.getNoteXSs(note) + NoteRenderer.getNoteheadRightEdgeSs(note) / 2.0;
    }

    /**
     * Computes the glissando endpoints for a note, building areas and
     * delegating to {@link #computeEndpoints}. Used by the public static
     * endpoint methods.
     *
     * @return The glissando endpoints, or null if the glissando cannot render
     */
    @Nullable
    private GlissandoRenderer.Endpoints computeEndpointsForNote(
        int xIndex,
        @NotNull Note.Glissando glissando,
        int lineIndex,
        @NotNull Composition composition,
        @NotNull LayoutResult layoutResult,
        double middleLineYSs
    ) {
        var line = composition.getLine(lineIndex);
        var note = line.getNote(xIndex);
        var src = resolveNoteContext(note, xIndex, line, layoutResult, middleLineYSs);
        var tgt = resolveTargetContext(glissando.type, xIndex, line, layoutResult, middleLineYSs);

        return computeEndpoints(src, tgt,
            glissando.x1Translate, glissando.type == Note.Glissando.Type.CONNECTED ? glissando.x2Translate : 0);
    }

    // ==========================================================================
    // Endpoint Computation and Rendering
    // ==========================================================================

    /**
     * Resolved geometry for a single note: center position, offset area, and note reference.
     */
    record NoteContext(
        @NotNull Note note,
        double cx, double cy,
        @NotNull Area offsetArea,
        @NotNull Rectangle2D offsetBounds
    ) {}

    /**
     * Immutable record holding the computed glissando endpoint positions in layout space.
     */
    record Endpoints(double startX, double startY, double endX, double endY, double angle) {}

    /**
     * Resolves the geometry context for a note at the given index: notehead center
     * position, staff-position Y coordinate, and composite area for gap calculation.
     */
    private NoteContext resolveNoteContext(
        @NotNull Note note, int noteIndex, @NotNull Line line,
        @NotNull LayoutResult layoutResult, double middleLineYSs
    ) {
        boolean beamed = line.getBeamings().findInterval(noteIndex) != null;
        var cx = noteheadCenterXSs(note, layoutResult);
        var cy = BaseElementRenderer.noteStaffPositionToCoordinateSs(note.getStaffPosition(), middleLineYSs);
        var entry = getOrBuildArea(note, beamed);

        return new NoteContext(note, cx, cy, entry.offsetArea(), entry.offsetBounds());
    }

    /**
     * Resolves the target note context for a CONNECTED glissando, or returns null
     * for SLIDE_OUT or if no next note exists.
     */
    @Nullable
    private NoteContext resolveTargetContext(
        @NotNull Note.Glissando.Type type, int sourceIndex, @NotNull Line line,
        @NotNull LayoutResult layoutResult, double middleLineYSs
    ) {
        if (type != Note.Glissando.Type.CONNECTED || sourceIndex + 1 >= line.noteCount()) {
            return null;
        }

        var nextNote = line.getNote(sourceIndex + 1);

        return resolveNoteContext(nextNote, sourceIndex + 1, line, layoutResult, middleLineYSs);
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
    @Nullable
    private static GlissandoRenderer.Endpoints computeEndpoints(
        @NotNull NoteContext src, @Nullable NoteContext tgt,
        double x1Translate, double x2Translate
    ) {
        boolean isSlideOut = (tgt == null);

        // Tangent direction in layout space
        double dx, dy;

        if (isSlideOut) {
            dx = 1.0;
            dy = 1.0;
        } else {
            dx = tgt.cx - src.cx;
            dy = tgt.cy - src.cy;
        }

        double len = Math.sqrt(dx * dx + dy * dy);

        if (!isSlideOut && len == 0) {
            return null;
        }

        double nx = dx / len;
        double ny = dy / len;

        // Areas are in local coordinates (origin at notehead glyph origin).
        // Local notehead center is at (noteheadRightEdge/2, 0).
        // Layout offset from local origin: (cx - localCenterX, cy).
        // Exit points found in local space are translated to layout space by adding the offset.

        // Source: find entry point on offset area in local space
        double localCx1 = NoteRenderer.getNoteheadRightEdgeSs(src.note) / 2.0;
        double offset1X = src.cx - localCx1;
        double offset1Y = src.cy;

        double stepSs = ScaleContext.getInstance().fromPixels(1.0);
        var entry1 = findNoteAreaEntryPoint(src.offsetArea, src.offsetBounds, localCx1, 0, nx, ny, stepSs);

        double effectiveX1Translate = Math.max(x1Translate, -MIN_GAP_SS);
        double startX = entry1.x + offset1X + nx * effectiveX1Translate;
        double startY = entry1.y + offset1Y + ny * effectiveX1Translate;

        double endX, endY;

        if (isSlideOut) {
            endX = startX + nx * SLIDE_OUT_LENGTH_SS;
            endY = startY + ny * SLIDE_OUT_LENGTH_SS;
        } else {
            // Target: find entry point on offset area in local space (reverse direction)
            double localCx2 = NoteRenderer.getNoteheadRightEdgeSs(tgt.note) / 2.0;
            double offset2X = tgt.cx - localCx2;
            double offset2Y = tgt.cy;

            var entry2 = findNoteAreaEntryPoint(tgt.offsetArea, tgt.offsetBounds, localCx2, 0, -nx, -ny, stepSs);

            double effectiveX2Translate = Math.max(x2Translate, -MIN_GAP_SS);
            endX = entry2.x + offset2X - nx * effectiveX2Translate;
            endY = entry2.y + offset2Y - ny * effectiveX2Translate;
        }

        // Compute glissando length
        dx = endX - startX;
        dy = endY - startY;
        double length = Math.sqrt(dx * dx + dy * dy);

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
        @NotNull Graphics2D g2,
        @NotNull NoteContext src, @Nullable NoteContext tgt,
        double x1Translate, double x2Translate
    ) {
        var endpoints = computeEndpoints(src, tgt, x1Translate, x2Translate);

        if (endpoints == null) {
            return;
        }

        double dx = endpoints.endX() - endpoints.startX();
        double dy = endpoints.endY() - endpoints.startY();
        double length = Math.sqrt(dx * dx + dy * dy);

        try (var ignored = GraphicsState.save(g2, TRANSFORM)) {
            g2.translate(endpoints.startX(), endpoints.startY());
            g2.rotate(endpoints.angle());
            g2.fill(new Rectangle2D.Double(
                0, -RECT_THICKNESS_SS / 2.0,
                length, RECT_THICKNESS_SS
            ));
        }
    }

    /**
     * Returns the cached entry if the note's visual attributes haven't changed,
     * or builds, caches, and returns a new one.
     */
    @NotNull AreaCacheEntry getOrBuildArea(@NotNull Note note, boolean beamed) {
        var key = new AreaCacheKey(note, beamed);

        var cached = areaCache.get(note);

        if (cached != null && cached.key.equals(key)) {
            return cached;
        }

        var offsetArea = createOffsetArea(buildNoteArea(note, beamed), (float) MIN_GAP_SS);
        var entry = new AreaCacheEntry(key, offsetArea, offsetArea.getBounds2D());
        areaCache.put(note, entry);

        return entry;
    }

    // ==========================================================================
    // Note Area Construction
    // ==========================================================================

    /**
     * Builds a geometric {@link Area} representing the visual footprint of a note,
     * including notehead, dots, accidentals, ledger lines, stem, and flags.
     * All coordinates are in staff spaces, relative to the notehead glyph origin.
     *
     * @param note   The note to build the area for
     * @param beamed Whether the note is part of a beam group (suppresses flags)
     * @return The composite area of all note components
     */
    Area buildNoteArea(@NotNull Note note, boolean beamed) {
        var area = new Area();
        var noteType = note.getNoteType();
        boolean upper = note.isUpper();

        // Grace notes always stem up
        if (noteType.isGraceNote()) {
            upper = true;
        }

        addNoteheadToArea(area, noteType, upper);
        addDotsToArea(area, note, beamed, upper);
        addAccidentalToArea(area, note);
        addLedgerLinesToArea(area, note);

        if (noteType.isNoteWithStem()) {
            var stemTip = addStemToArea(area, noteType, upper);

            if (!beamed) {
                addFlagsToArea(area, noteType, upper, stemTip);
            }
        }

        return area;
    }

    /**
     * Adds the notehead glyph outline to the area.
     * For stem-down notes, the notehead is shifted left by half the stem width
     * via {@link NoteRenderer#getNoteheadXOffsetSs}.
     */
    private void addNoteheadToArea(
        @NotNull Area area,
        @NotNull NoteType noteType,
        boolean upper
    ) {
        Shape shape;

        if (noteType.isGraceNote()) {
            shape = NOTEHEAD_GRACE_SHAPE;
        } else if (noteType == NoteType.SEMIBREVE) {
            shape = NOTEHEAD_WHOLE_SHAPE;
        } else {
            shape = NOTEHEAD_BLACK_SHAPE;
        }

        float offsetX = NoteRenderer.getNoteheadXOffsetSs(noteType, upper);

        if (offsetX != 0f) {
            shape = AffineTransform.getTranslateInstance(offsetX, 0).createTransformedShape(shape);
        }

        area.add(new Area(shape));
    }

    /**
     * Adds augmentation dot rectangles to the area.
     * Uses {@link NoteRenderer#forEachDotPosition} for shared positioning logic.
     */
    private void addDotsToArea(
        @NotNull Area area,
        @NotNull Note note,
        boolean beamed,
        boolean upper
    ) {
        var dotBBox = METADATA.getBBox(SMuFLGlyph.AUGMENTATION_DOT);

        if (dotBBox == null) {
            return;
        }

        NoteRenderer.forEachDotPosition(note, beamed, upper, (dotX, yOffset) ->
            area.add(new Area(new Rectangle2D.Double(
                dotX + dotBBox.left(),
                yOffset + dotBBox.top(),
                dotBBox.width(),
                dotBBox.height()
            ))));
    }

    /**
     * Adds an accidental bounding rectangle to the area.
     * Mirrors the positioning logic in {@link NoteRenderer#renderAccidental}.
     */
    private void addAccidentalToArea(@NotNull Area area, @NotNull Note note) {
        if (note.getAccidental() == Note.Accidental.NONE) {
            return;
        }

        float accWidth = NoteRenderer.getAccidentalWidthSs(note);

        if (accWidth <= 0) {
            return;
        }

        // Accidental X position mirrors NoteRenderer: -(padding + width)
        // For grace notes, accWidth already reflects the small glyph size.
        double x = -NoteRenderer.ACCIDENTAL_PADDING_SS - accWidth;

        // Use the tallest accidental bbox height as a reasonable approximation.
        // The actual accidental may be shorter, but this gives a safe bounding area.
        double height = estimateAccidentalHeightSs(note);
        double halfHeight = height / 2.0;

        area.add(new Area(new Rectangle2D.Double(
            x, -halfHeight,
            accWidth, height
        )));
    }

    /**
     * Estimates the vertical extent of a note's accidental in staff spaces,
     * using the tallest component glyph's bounding box.
     */
    private double estimateAccidentalHeightSs(@NotNull Note note) {
        // Use the sharp glyph bbox as a reasonable default height (~2.5 ss)
        var bbox = METADATA.getBBox(SMuFLGlyph.ACCIDENTAL_SHARP);

        return bbox != null ? bbox.height() : 2.5;
    }

    /**
     * Returns the ledger line overhang for a note, or 0 if the note has no ledger lines.
     * Delegates to {@link LayoutConstants#getLedgerLineOverhangSs(Note)}.
     */
    public static double getLedgerLineOverhangSs(@NotNull Note note) {
        return LayoutConstants.getLedgerLineOverhangSs(note);
    }

    /**
     * Adds ledger line rectangles to the area.
     * Mirrors the positioning logic in {@link NoteRenderer#renderLedgerLines}.
     */
    private void addLedgerLinesToArea(@NotNull Area area, @NotNull Note note) {
        double extensionSs = getLedgerLineOverhangSs(note);

        if (extensionSs == 0.0) {
            return;
        }

        double noteheadWidthSs = NoteRenderer.getNoteheadRightEdgeSs(note);
        double ledgerWidthSs = noteheadWidthSs + 2 * extensionSs;
        double centerXSs = noteheadWidthSs / 2.0;
        double thicknessSs = ENGRAVING.legerLineThickness();
        double halfThickness = thicknessSs / 2.0;

        BaseElementRenderer.forEachLedgerLineYSs(note.getStaffPosition(), y ->
            area.add(new Area(new Rectangle2D.Double(
                centerXSs - ledgerWidthSs / 2.0,
                y - halfThickness,
                ledgerWidthSs,
                thicknessSs
            ))));
    }

    /**
     * Adds a stem rectangle to the area and returns the stem tip point.
     * Uses {@link LayoutConstants#computeBaseStemGeometry} for shared anchor/positioning logic.
     *
     * @return The stem tip point (x = stem left edge, y = stem tip)
     */
    private Point2D.Double addStemToArea(
        @NotNull Area area,
        @NotNull NoteType noteType,
        boolean upper
    ) {
        var geom = LayoutConstants.computeBaseStemGeometry(noteType, upper);
        double stemTipY = geom.stemTipYSs(upper);

        if (upper) {
            area.add(new Area(new Rectangle2D.Double(
                geom.stemLeftXSs(), stemTipY, LayoutConstants.STEM_WIDTH_SS, geom.lengthSs())));
        } else {
            area.add(new Area(new Rectangle2D.Double(
                geom.stemLeftXSs(), geom.anchorYSs(), LayoutConstants.STEM_WIDTH_SS, geom.lengthSs())));
        }

        return new Point2D.Double(geom.stemLeftXSs(), stemTipY);
    }

    /**
     * Adds flag bounding rectangles to the area.
     * Mirrors the positioning logic in {@link NoteRenderer#renderFlags}.
     */
    private void addFlagsToArea(
        @NotNull Area area,
        @NotNull NoteType noteType,
        boolean upper,
        @NotNull Point2D.Double stemTip
    ) {
        var flagGlyph = noteType.getFlagGlyph(upper);

        if (flagGlyph == null) {
            return;
        }

        var flagShape = noteType.isGraceNote() ? FLAG_8TH_UP_GRACE_SHAPE : FLAG_SHAPES.get(flagGlyph);

        if (flagShape == null) {
            return;
        }

        area.add(new Area(AffineTransform.getTranslateInstance(stemTip.x, stemTip.y)
            .createTransformedShape(flagShape)));
    }

    // ==========================================================================
    // Offset Area and Inward-Search for Tangent-Area Intersection
    // ==========================================================================

    /**
     * Returns a new Area that is the union of the original shape's fill
     * and a stroke of width 2 × offsetSs around it, effectively expanding
     * the shape outward by offsetSs on all sides.
     */
    static @NotNull Area createOffsetArea(@NotNull Shape shape, float offsetSs) {
        var thickStroke = new BasicStroke(
            offsetSs * 2,
            BasicStroke.CAP_ROUND,
            BasicStroke.JOIN_ROUND,
            10.0f
        );
        var expanded = new Area(thickStroke.createStrokedShape(shape));
        expanded.add(new Area(shape));
        return expanded;
    }

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
    static @NotNull Point2D.Double findNoteAreaEntryPoint(
        @NotNull Area offsetArea,
        @NotNull Rectangle2D offsetBounds,
        double cx, double cy,
        double nx, double ny,
        double stepSs
    ) {
        // Zero-direction guard
        if (nx == 0 && ny == 0) {
            return new Point2D.Double(cx, cy);
        }

        // Precompute bounding-box half-dimensions of the rotated tip rectangle
        double halfStep = stepSs / 2.0;
        double halfThickness = RECT_THICKNESS_SS / 2.0;
        double halfW = halfStep * Math.abs(nx) + halfThickness * Math.abs(ny);
        double halfH = halfStep * Math.abs(ny) + halfThickness * Math.abs(nx);

        // Start at the point where the outward ray exits the bounding box
        double startT = computeFarBoundsT(offsetBounds, cx, cy, nx, ny);

        // Step inward; return the last non-intersecting position
        for (double t = startT; t >= 0; t -= stepSs) {
            double px = cx + nx * t;
            double py = cy + ny * t;
            var tipRect = new Rectangle2D.Double(px - halfW, py - halfH, halfW * 2, halfH * 2);

            if (offsetArea.intersects(tipRect)) {
                double endT = t + stepSs;
                return new Point2D.Double(cx + nx * endT, cy + ny * endT);
            }
        }

        // Fallback: center is outside the area
        return new Point2D.Double(cx, cy);
    }

    static double computeFarBoundsT(
        @NotNull Rectangle2D bounds,
        double cx, double cy,
        double nx, double ny
    ) {
        double tx = nx > 0 ? (bounds.getMaxX() - cx) / nx
                   : nx < 0 ? (bounds.getMinX() - cx) / nx
                   : Double.MAX_VALUE;

        double ty = ny > 0 ? (bounds.getMaxY() - cy) / ny
                   : ny < 0 ? (bounds.getMinY() - cy) / ny
                   : Double.MAX_VALUE;

        return Math.min(tx, ty);
    }

}
