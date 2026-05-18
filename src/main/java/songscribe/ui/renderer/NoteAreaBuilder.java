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

import songscribe.model.ElementType;
import songscribe.model.StaffElement;
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

    /** Height of the sharp accidental glyph, used as the bounding-box height for all accidentals. */
    private static final double ACCIDENTAL_HEIGHT_SS;

    /** Cached glyph outlines for flag glyphs at standard size. */
    private static final Map<SMuFLGlyph, Shape> FLAG_SHAPES;

    /** Cached glyph outline for the grace note flag (FLAG_8TH_UP at grace scale). */
    private static final Shape FLAG_8TH_UP_GRACE_SHAPE;

    static {
        var frc = GraphicUtils.SCREEN_FRC;
        var font = BaseElementRenderer.MUSIC_FONT;
        var graceFont = BaseElementRenderer.GRACE_NOTE_FONT;

        NOTEHEAD_BLACK_SHAPE = GraphicUtils.glyphOutline(font, frc, SMuFLGlyph.NOTEHEAD_BLACK);
        NOTEHEAD_HALF_SHAPE = GraphicUtils.glyphOutline(font, frc, SMuFLGlyph.NOTEHEAD_HALF);
        NOTEHEAD_WHOLE_SHAPE = GraphicUtils.glyphOutline(font, frc, SMuFLGlyph.NOTEHEAD_WHOLE);
        NOTEHEAD_GRACE_SHAPE = GraphicUtils.glyphOutline(graceFont, frc, SMuFLGlyph.NOTEHEAD_BLACK);

        var accidentalBBox = SMuFLMetadata.getBBox(SMuFLGlyph.ACCIDENTAL_SHARP);
        ACCIDENTAL_HEIGHT_SS = accidentalBBox != null ? accidentalBBox.height() : 2.5;

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

        var offsetX = NoteRenderer.getNoteheadXOffsetSs(noteType, upper);

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
        var dotBBox = SMuFLMetadata.getBBox(SMuFLGlyph.AUGMENTATION_DOT);

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
    private void addAccidentalToArea(Area area, StaffElement note) {
        if (note.getAccidental() == null) {
            return;
        }

        var accWidth = NoteRenderer.getAccidentalWidthSs(note);

        if (accWidth <= 0) {
            return;
        }

        // Accidental X position mirrors NoteRenderer: -(padding + width)
        // For grace notes, accWidth already reflects the small glyph size.
        double xSs = -NoteRenderer.ACCIDENTAL_PADDING_SS - accWidth;

        // Use the tallest accidental bbox height as a reasonable approximation.
        // The actual accidental may be shorter, but this gives a safe bounding area.
        var heightSs = ACCIDENTAL_HEIGHT_SS;
        var halfHeightSs = heightSs / 2.0;

        area.add(new Area(new Rectangle2D.Double(
            xSs, -halfHeightSs,
            accWidth, heightSs
        )));
    }

    /**
     * Adds ledger line rectangles to the area.
     * Mirrors the positioning logic in {@link NoteRenderer#renderLedgerLines}.
     */
    private void addLedgerLinesToArea(Area area, StaffElement note) {
        var extensionSs = NoteRenderer.getLedgerLineOverhangSs(note);

        if (extensionSs == 0.0) {
            return;
        }

        var ledgerWidthSs = NoteRenderer.getLedgerLineWidthSs(note, extensionSs);
        var centerXSs = NoteRenderer.getLedgerLineCenterXSs(note);
        var thicknessSs = Engraving.LEDGER_LINE_THICKNESS_SS;
        var halfThicknessSs = thicknessSs / 2.0;

        BaseElementRenderer.forEachLedgerLineYSs(note.getStaffPosition(), y ->
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
                stemLeftXSs, stemTipYSs, NoteRenderer.STEM_WIDTH_SS, geom.lengthSs())));
        } else {
            area.add(new Area(new Rectangle2D.Double(
                stemLeftXSs, geom.anchorYSs(), NoteRenderer.STEM_WIDTH_SS, geom.lengthSs())));
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
