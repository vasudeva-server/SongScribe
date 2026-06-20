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

import module java.desktop;

import java.util.EnumMap;
import java.util.Map;
import java.util.WeakHashMap;

import org.jspecify.annotations.Nullable;

import songscribe.dom.ElementType;
import songscribe.dom.StaffElement;
import songscribe.layout.NoteGeometry;
import songscribe.smufl.Engraving;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;
import songscribe.util.GraphicUtils;

/**
 * Builds geometric {@link Area} shapes representing a note's visual footprint
 * (notehead, dots, accidentals, ledger lines, stem, flags) for glissando endpoint reservation.
 * <p>
 * Results are cached by note identity and invalidated when any area-affecting attribute changes.
 */
class NoteAreaBuilder {

    // ==========================================================================
    // Constants and Cached Shapes
    // ==========================================================================


    /** Minimum gap between the note area exit point and the glissando endpoint, in staff spaces. */
    static final double MIN_GAP_SS = 0.3;

    /** Cached glyph outline for filled (black) noteheads (quarter, eighth, etc.). */
    private static final Shape NOTEHEAD_BLACK_SHAPE;

    /** Cached glyph outline for half noteheads (minim). */
    private static final Shape NOTEHEAD_HALF_SHAPE;

    /** Cached glyph outline for whole noteheads. */
    private static final Shape NOTEHEAD_WHOLE_SHAPE;

    /** Cached glyph outline for grace noteheads. */
    private static final Shape NOTEHEAD_GRACE_SHAPE;

    /** Cached glyph outlines for flag glyphs at standard size. */
    private static final Map<SMuFLGlyph, Shape> FLAG_SHAPES;

    /** Cached glyph outline for the grace note flag (FLAG_8TH_UP at grace scale). */
    private static final Shape FLAG_8TH_UP_GRACE_SHAPE;

    /**
     * Cached accidental glyph outlines at the note origin, keyed by glyph.
     * Populated lazily because the set of accidental glyphs in use (components,
     * parentheses, small variants) is not known at static-init time. The cached
     * outlines are position-independent; each use translates them to the pen X.
     */
    private static final Map<SMuFLGlyph, Shape> ACCIDENTAL_SHAPES = new EnumMap<>(SMuFLGlyph.class);

    /** Grace-scale counterpart of {@link #ACCIDENTAL_SHAPES}. */
    private static final Map<SMuFLGlyph, Shape> ACCIDENTAL_GRACE_SHAPES = new EnumMap<>(SMuFLGlyph.class);

    static {
        var frc = GraphicUtils.SCREEN_FRC;
        var font = RenderingUtils.MUSIC_FONT;
        var graceFont = RenderingUtils.GRACE_NOTE_FONT;

        NOTEHEAD_BLACK_SHAPE = GraphicUtils.glyphOutline(font, frc, SMuFLGlyph.NOTEHEAD_BLACK);
        NOTEHEAD_HALF_SHAPE = GraphicUtils.glyphOutline(font, frc, SMuFLGlyph.NOTEHEAD_HALF);
        NOTEHEAD_WHOLE_SHAPE = GraphicUtils.glyphOutline(font, frc, SMuFLGlyph.NOTEHEAD_WHOLE);
        NOTEHEAD_GRACE_SHAPE = GraphicUtils.glyphOutline(graceFont, frc, SMuFLGlyph.NOTEHEAD_BLACK);

        var flagGlyphs = new SMuFLGlyph[]{
            SMuFLGlyph.FLAG_8TH_UP, SMuFLGlyph.FLAG_8TH_DOWN,
            SMuFLGlyph.FLAG_16TH_UP, SMuFLGlyph.FLAG_16TH_DOWN,
            SMuFLGlyph.FLAG_32ND_UP, SMuFLGlyph.FLAG_32ND_DOWN,
        };
        var flagShapes = new EnumMap<SMuFLGlyph, Shape>(SMuFLGlyph.class);

        for (var glyph : flagGlyphs) {
            flagShapes.put(glyph, GraphicUtils.glyphOutline(font, frc, glyph));
        }

        FLAG_SHAPES = flagShapes;
        FLAG_8TH_UP_GRACE_SHAPE = GraphicUtils.glyphOutline(graceFont, frc, SMuFLGlyph.FLAG_8TH_UP);
    }

    // ==========================================================================
    // Inner Types
    // ==========================================================================

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
        ElementType noteType, int ledgerLineCount, int staffPosition,
        int dotCount, StaffElement.@Nullable Accidental accidental, boolean upper, boolean beamed) {

        AreaCacheKey(StaffElement note, boolean beamed) {
            this(note.getType(),
                note.getLedgerLineCount(),
                note.hasLedgerLines() ? note.getStaffPosition() : 0,
                note.getDotCount(),
                note.getAccidental(),
                note.isUpper(),
                beamed);
        }
    }

    private record AreaCacheEntry(AreaCacheKey key, NoteArea noteArea) {}

    // ==========================================================================
    // Instance Fields
    // ==========================================================================

    /** Self-validating area cache keyed by note identity. Weak keys allow GC of removed notes. */
    private final WeakHashMap<StaffElement, AreaCacheEntry> areaCache = new WeakHashMap<>();

    // ==========================================================================
    // Public API
    // ==========================================================================

    /**
     * Returns the cached {@link NoteArea} if the note's visual attributes haven't changed,
     * or builds, caches, and returns a new one.
     */
    NoteArea getOrBuildArea(StaffElement note, boolean beamed) {
        var key = new AreaCacheKey(note, beamed);
        var cached = areaCache.get(note);

        if (cached != null && cached.key.equals(key)) {
            return cached.noteArea;
        }

        var offsetArea = createOffsetArea(buildNoteArea(note, beamed), (float) MIN_GAP_SS);
        var noteArea = new NoteArea(offsetArea, offsetArea.getBounds2D());
        areaCache.put(note, new AreaCacheEntry(key, noteArea));

        return noteArea;
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
    Area buildNoteArea(StaffElement note, boolean beamed) {
        var area = new Area();
        var noteType = note.getType();
        var upper = note.isUpper();

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
        Area area,
        ElementType noteType,
        boolean upper
    ) {
        Shape shape;

        if (noteType.isGraceNote()) {
            shape = NOTEHEAD_GRACE_SHAPE;
        } else if (noteType == ElementType.SEMIBREVE) {
            shape = NOTEHEAD_WHOLE_SHAPE;
        } else if (noteType == ElementType.MINIM) {
            shape = NOTEHEAD_HALF_SHAPE;
        } else {
            shape = NOTEHEAD_BLACK_SHAPE;
        }

        var offsetX = NoteGeometry.getNoteheadXOffsetSs(noteType, upper);

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
        Area area,
        StaffElement note,
        boolean beamed,
        boolean upper
    ) {
        var dotBBox = SMuFLMetadata.requireBBox(SMuFLGlyph.AUGMENTATION_DOT);

        NoteRenderer.forEachDotPosition(note, beamed, upper, (dotX, yOffset) ->
            area.add(new Area(new Rectangle2D.Double(
                dotX + dotBBox.left(),
                yOffset + dotBBox.top(),
                dotBBox.width(),
                dotBBox.height()
            ))));
    }

    /**
     * Adds the accidental glyph outlines to the area.
     * Mirrors the positioning logic in {@link NoteRenderer#renderAccidental}.
     * <p>
     * A rectangular bounding box would over-reserve in the corners yet still
     * under-reserve where the glyph ink is tallest (accidentals are not centered
     * on the note: a flat extends well above the note center and barely below it).
     * Adding the real glyph outlines makes the glissando clearance follow the ink.
     */
    private void addAccidentalToArea(Area area, StaffElement note) {
        var accidental = note.getAccidental();

        if (accidental == null) {
            return;
        }

        var accWidth = NoteGeometry.getAccidentalWidthSs(note);

        if (accWidth <= 0) {
            return;
        }

        var isGrace = note.getType().isGraceNote();
        var font = isGrace ? RenderingUtils.GRACE_NOTE_FONT : RenderingUtils.MUSIC_FONT;
        var components = NoteGeometry.getAccidentalComponents(accidental, isGrace);

        // Accidental X position mirrors NoteRenderer: -(padding + width).
        var startX = -NoteGeometry.ACCIDENTAL_PADDING_SS - accWidth;

        var cache = isGrace ? ACCIDENTAL_GRACE_SHAPES : ACCIDENTAL_SHAPES;

        NoteGeometry.walkAccidentalGlyphs(
            components,
            note.isAccidentalInParentheses(),
            startX,
            (glyph, xSs) -> {
                var outline = cache.computeIfAbsent(
                    glyph, g -> GraphicUtils.glyphOutline(font, GraphicUtils.SCREEN_FRC, g));
                area.add(new Area(
                    AffineTransform.getTranslateInstance(xSs, 0).createTransformedShape(outline)));
            });
    }

    /**
     * Adds ledger line rectangles to the area.
     * Mirrors the positioning logic in {@link NoteRenderer#renderLedgerLines}.
     */
    private void addLedgerLinesToArea(Area area, StaffElement note) {
        var extensionSs = NoteGeometry.getLedgerLineOverhangSs(note);

        if (extensionSs == 0.0) {
            return;
        }

        var ledgerWidthSs = NoteRenderer.getLedgerLineWidthSs(note, extensionSs);
        var centerXSs = NoteRenderer.getLedgerLineCenterXSs(note);
        var thicknessSs = Engraving.LEDGER_LINE_THICKNESS_SS;
        var halfThicknessSs = thicknessSs / 2.0;

        RenderingUtils.forEachLedgerLineYSs(note.getStaffPosition(), y ->
            area.add(new Area(new Rectangle2D.Double(
                centerXSs - ledgerWidthSs / 2.0,
                y - halfThicknessSs,
                ledgerWidthSs,
                thicknessSs
            ))));
    }

    /**
     * Adds a stem rectangle to the area and returns the stem tip point.
     * Uses {@link NoteRenderer#computeBaseStemGeometry} for shared anchor/positioning logic.
     *
     * @return The stem tip point (x = stem left edge, y = stem tip)
     */
    private Point2D.Double addStemToArea(
        Area area,
        ElementType noteType,
        boolean upper
    ) {
        var geom = NoteRenderer.computeBaseStemGeometry(noteType, upper);
        var stemLeftXSs = geom.stemLeftXSs();
        var stemTipYSs = geom.stemTipYSs(upper);

        if (upper) {
            area.add(new Area(new Rectangle2D.Double(
                stemLeftXSs, stemTipYSs, NoteGeometry.STEM_WIDTH_SS, geom.lengthSs())));
        } else {
            area.add(new Area(new Rectangle2D.Double(
                stemLeftXSs, geom.anchorYSs(), NoteGeometry.STEM_WIDTH_SS, geom.lengthSs())));
        }

        return new Point2D.Double(stemLeftXSs, stemTipYSs);
    }

    /**
     * Adds flag bounding rectangles to the area.
     * Mirrors the positioning logic in {@link NoteRenderer#renderFlags}.
     */
    private void addFlagsToArea(
        Area area,
        ElementType noteType,
        boolean upper,
        Point2D.Double stemTip
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
    // Offset Area Utility
    // ==========================================================================

    /**
     * Returns a new Area that is the union of the original shape's fill
     * and a stroke of width 2 × offsetSs around it, effectively expanding
     * the shape outward by offsetSs on all sides.
     */
    static Area createOffsetArea(Shape shape, float offsetSs) {
        var thickStroke = new BasicStroke(
            offsetSs * 2,
            BasicStroke.CAP_ROUND,
            BasicStroke.JOIN_ROUND
        );
        var expanded = new Area(thickStroke.createStrokedShape(shape));
        expanded.add(new Area(shape));
        return expanded;
    }

}
