package songscribe.layout;

import java.awt.geom.Rectangle2D;
import java.util.EnumMap;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;

import org.jspecify.annotations.Nullable;

import songscribe.dom.AccidentalBounds;
import songscribe.dom.StaffElement.Accidental;
import songscribe.error.RuntimeError;
import songscribe.dom.ElementType;
import songscribe.dom.StaffElement;
import songscribe.smufl.BBox;
import songscribe.smufl.Engraving;
import songscribe.smufl.GlyphAnchors;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;

/**
 * Geometry constants and stateless calculations for notes, noteheads, stems,
 * ledger lines, and accidentals. This class centralises layout-time geometry
 * so that layout algorithms do not need to import renderer classes.
 *
 * <p>{@link #initializeAccidentalWidths()} must be called before any accidental
 * query method.
 */
public final class NoteGeometry {

    // ==========================================================================
    // Stem Constants
    // ==========================================================================

    /** Stem width in staff-space units (LilyPond multiplier-derived). */
    public static final double STEM_WIDTH_SS = LineThickness.getInstance().stemSs();

    // ==========================================================================
    // Font Constants
    // ==========================================================================

    /** Bravura SMuFL music font size in staff-space units. */
    public static final float MUSIC_FONT_SIZE_SS = 4.0f;

    // ==========================================================================
    // Glissando Constants
    // ==========================================================================

    /**
     * Gap between the note column edge and the drawn slide line, in staff spaces. The connecting
     * glissando uses it on both endpoints to keep the drawn line clear of the note's ink.
     */
    public static final double GLISSANDO_DRAWN_GAP_SS = 0.4;

    /**
     * Gap between the host note's column edge and a fall's glyph, in staff spaces. Mirrors the
     * {@code padding} default LilyPond's {@code BendAfter} grob uses for falls and doits. Shared by
     * {@code SlideRenderer} (the drawn gap) and {@code ElementColumnBuilder} (the layout reservation
     * for a fall).
     */
    public static final double FALL_GAP_SS = 0.5;

    private static final double GLISSANDO_MIN_LENGTH_SS = 1.0;

    /** Minimum horizontal distance between note origins required for a glissando, in staff spaces. */
    public static final double MIN_GLISSANDO_RESERVATION_SS =
        GLISSANDO_MIN_LENGTH_SS + 2 * GLISSANDO_DRAWN_GAP_SS;

    /**
     * Stem anchor point for small black noteheads (stem-up, south-east corner).
     * Used for grace notes which use pre-sized small glyphs.
     */
    public static final GlyphAnchors.Anchor STEM_UP_SE_BLACK_SMALL = new GlyphAnchors.Anchor(
        Engraving.NOTEHEAD_BLACK_STEM_UP_SE.x() * ElementType.GRACE_NOTE_SCALE,
        Engraving.NOTEHEAD_BLACK_STEM_UP_SE.y() * ElementType.GRACE_NOTE_SCALE
    );

    // ==========================================================================
    // Augmentation Dot Geometry
    // ==========================================================================

    // Augmentation dot Y shift (in ss) when the note sits on a staff line —
    // moves the dot into the space above the line.
    public static final double DOT_ON_LINE_Y_SHIFT_SS = -0.5;

    // Center-to-center spacing between consecutive augmentation dots (in ss): one dot glyph
    // plus a one-dot-width gap, following LilyPond's stack-by-one-dot-width convention.
    public static final double DOT_SPACING_SS = Engraving.AUGMENTATION_DOT_WIDTH_SS * 2;

    /**
     * Returns the X position (in ss, from the note glyph origin) of the first augmentation dot,
     * following LilyPond's pad-by-one-dot-width convention: the dot sits one augmentation-dot width
     * to the right of the note's right-most obstruction. That obstruction is the notehead, unless an
     * unbeamed up-stem flag extends further right.
     */
    public static double firstDotXSs(ElementType noteType, boolean beamed, StaffElement.Direction direction) {
        var obstructionRightSs = SMuFLMetadata.requireBBox(noteType.requireSMuFLGlyph()).right();

        if (direction.isUp()) {
            var flagBBox = flagBBoxLocalSs(noteType, StaffElement.Direction.UP, beamed);

            if (flagBBox != null) {
                obstructionRightSs = Math.max(obstructionRightSs, flagBBox.getMaxX());
            }
        }

        return obstructionRightSs + Engraving.AUGMENTATION_DOT_WIDTH_SS;
    }

    /**
     * Computes the position of each augmentation dot for a note and passes
     * (dotX, yOffset) to the consumer. Both values are in staff spaces,
     * relative to the note's glyph origin.
     */
    public static void forEachDotPosition(
        StaffElement note, boolean beamed, StaffElement.Direction direction,
        BiConsumer<? super Double, ? super Double> consumer
    ) {
        if (note.getDotCount() == 0) {
            return;
        }

        var noteType = note.getType();

        // Dots shift up when note is on a line (even staff position)
        var yOffset = (note.getStaffPosition() % 2 == 0) ? DOT_ON_LINE_Y_SHIFT_SS : 0.0;

        var dotX = firstDotXSs(noteType, beamed, direction);

        for (var i = 0; i < note.getDotCount(); i++) {
            consumer.accept(dotX, yOffset);
            dotX += DOT_SPACING_SS;
        }
    }

    /**
     * Returns the right-most extent (in ss, from the note glyph origin) of a note's augmentation
     * dots, or {@code baseRightSs} when the note has no dots. Derived from the same dot positions
     * {@link #forEachDotPosition} feeds the renderer, so the space layout reserves matches the ink
     * actually painted.
     */
    public static double dotsRightExtentSs(
        StaffElement note, boolean beamed, StaffElement.Direction direction, double baseRightSs
    ) {
        if (note.getDotCount() == 0) {
            return baseRightSs;
        }

        var dotRightSs = SMuFLMetadata.requireBBox(SMuFLGlyph.AUGMENTATION_DOT).right();
        var maxRightSs = new double[]{baseRightSs};

        forEachDotPosition(note, beamed, direction, (dotX, yOffset) ->
            maxRightSs[0] = Math.max(maxRightSs[0], dotX + dotRightSs));

        return maxRightSs[0];
    }

    // ==========================================================================
    // Stem Geometry
    // ==========================================================================

    /**
     * Resolves the effective stem direction for a note. Grace notes always stem up,
     * regardless of the note's own stored direction; every other note uses its own direction.
     */
    public static StaffElement.Direction effectiveDirection(StaffElement note) {
        return note.getType().isGraceNote() ? StaffElement.Direction.UP : note.getDirection();
    }

    /**
     * Computes the base stem geometry for a note type and direction.
     * This is the shared anchor selection and positioning logic used by both
     * {@code NoteRenderer} (for drawing) and {@code GlissandoRenderer} (for area building).
     *
     * @param noteType  The note type (determines anchor and stem length)
     * @param direction UP for stem-up, DOWN for stem-down
     * @return The base stem geometry
     */
    public static StemGeometry computeBaseStemGeometry(ElementType noteType, StaffElement.Direction direction) {
        var isMinim = noteType == ElementType.MINIM;
        var isGrace = noteType.isGraceNote();

        GlyphAnchors.Anchor anchor;

        if (isGrace) {
            anchor = STEM_UP_SE_BLACK_SMALL;
        } else if (direction.isUp()) {
            anchor = isMinim ? Engraving.NOTEHEAD_HALF_STEM_UP_SE : Engraving.NOTEHEAD_BLACK_STEM_UP_SE;
        } else {
            anchor = isMinim ? Engraving.NOTEHEAD_HALF_STEM_DOWN_NW : Engraving.NOTEHEAD_BLACK_STEM_DOWN_NW;
        }

        var anchorX = anchor.x();

        // Stem left edge: for up-stems, the anchor marks the RIGHT edge of the stem;
        // for down-stems, the anchor marks the LEFT edge but the notehead is shifted
        // left by STEM_WIDTH_SS/2, so we compensate.
        var stemLeftX = anchorX - (direction.isUp() ? STEM_WIDTH_SS : STEM_WIDTH_SS / 2);
        var stemLength = isGrace ? Engraving.GRACE_NOTE_STEM_LENGTH_SS : Engraving.STEM_LENGTH_SS;

        return new StemGeometry(stemLeftX, anchor.y(), stemLength);
    }

    /**
     * Base stem geometry computed from SMuFL anchor data, before any rendering-specific
     * adjustments (device-pixel snapping, beam lengthening, etc.).
     *
     * @param stemLeftXSs Left edge of the stem in staff spaces (relative to notehead origin)
     * @param anchorYSs   Y position where the stem meets the notehead
     * @param lengthSs    Stem length in staff spaces (without beam lengthening)
     */
    public record StemGeometry(double stemLeftXSs, double anchorYSs, double lengthSs) {

        /**
         * Returns the Y position of the stem tip (the end away from the notehead).
         *
         * @param direction UP for stem-up (tip above notehead), DOWN for stem-down
         * @return stem tip Y in staff spaces
         */
        public double stemTipYSs(StaffElement.Direction direction) {
            return direction.isUp() ? anchorYSs - lengthSs : anchorYSs + lengthSs;
        }
    }

    // ==========================================================================
    // Accidental Constants
    // ==========================================================================

    /** Horizontal gap between notehead origin and the right edge of an accidental, in staff spaces. */
    public static final float ACCIDENTAL_PADDING_SS = 0.3375f; // 2.7px / 8 px/ss

    /**
     * Grace-note counterpart of {@link #ACCIDENTAL_PADDING_SS}, scaled by
     * {@link ElementType#GRACE_NOTE_SCALE} so the gap stays proportional to the smaller grace
     * notehead instead of looking oversized.
     */
    public static final float GRACE_ACCIDENTAL_PADDING_SS =
        ElementType.GRACE_NOTE_SCALE * ACCIDENTAL_PADDING_SS;

    static final float SPACE_BETWEEN_TWO_ACCIDENTALS_SS = 0.1625f; // 1.3px / 8 px/ss

    /** Accidental glyph components indexed by {@code Accidental.ordinal()}. */
    private static final SMuFLGlyph[][] ACCIDENTAL_COMPONENTS = {
        {SMuFLGlyph.ACCIDENTAL_NATURAL},                                // NATURAL
        {SMuFLGlyph.ACCIDENTAL_FLAT},                                   // FLAT
        {SMuFLGlyph.ACCIDENTAL_SHARP},                                  // SHARP
        {SMuFLGlyph.ACCIDENTAL_NATURAL, SMuFLGlyph.ACCIDENTAL_NATURAL}, // DOUBLE_NATURAL
        {SMuFLGlyph.ACCIDENTAL_DOUBLE_FLAT},                            // DOUBLE_FLAT
        {SMuFLGlyph.ACCIDENTAL_DOUBLE_SHARP},                           // DOUBLE_SHARP
        {SMuFLGlyph.ACCIDENTAL_NATURAL, SMuFLGlyph.ACCIDENTAL_FLAT},    // NATURAL_FLAT
        {SMuFLGlyph.ACCIDENTAL_NATURAL, SMuFLGlyph.ACCIDENTAL_SHARP},   // NATURAL_SHARP
    };

    /**
     * Returns the glyph components for an accidental. Grace notes use these same regular glyphs;
     * the grace size comes from drawing them with a scaled-down font ({@link ElementType#GRACE_NOTE_SCALE}),
     * mirroring how grace noteheads are rendered. There is no separate small-glyph table: Bravura's
     * "small" accidentals are a distinct private-use set with their own (non-proportional) metrics,
     * and have no double-flat/double-sharp variant, so they cannot scale uniformly with the regular
     * glyphs.
     */
    public static SMuFLGlyph[] getAccidentalComponents(Accidental accidental) {
        return ACCIDENTAL_COMPONENTS[accidental.ordinal()];
    }

    // Kerning adjustments for parenthesized accidentals (in staff-space units).
    // Positive = more space, negative = less space.
    private static final EnumMap<SMuFLGlyph, Float> PAREN_LEFT_KERNING = new EnumMap<>(SMuFLGlyph.class);
    private static final EnumMap<SMuFLGlyph, Float> PAREN_RIGHT_KERNING = new EnumMap<>(SMuFLGlyph.class);

    static {
        PAREN_LEFT_KERNING.put(SMuFLGlyph.ACCIDENTAL_FLAT, 0.125f);
        PAREN_LEFT_KERNING.put(SMuFLGlyph.ACCIDENTAL_NATURAL, 0.125f);
        PAREN_LEFT_KERNING.put(SMuFLGlyph.ACCIDENTAL_SHARP, 0.125f);
        PAREN_LEFT_KERNING.put(SMuFLGlyph.ACCIDENTAL_DOUBLE_FLAT, 0.125f);

        PAREN_RIGHT_KERNING.put(SMuFLGlyph.ACCIDENTAL_FLAT, -0.125f);
        PAREN_RIGHT_KERNING.put(SMuFLGlyph.ACCIDENTAL_NATURAL, 0.125f);
        PAREN_RIGHT_KERNING.put(SMuFLGlyph.ACCIDENTAL_SHARP, 0.125f);
        PAREN_RIGHT_KERNING.put(SMuFLGlyph.ACCIDENTAL_DOUBLE_FLAT, -0.125f);
    }

    // Cached accidental widths (computed on first use via initializeAccidentalWidths())
    private static float @Nullable [] baseAccidentalWidthsSs = null;
    private static float @Nullable [] baseAccidentalParenthesisWidthsSs = null;

    // Cached accidental bounding boxes, indexed by Accidental.ordinal().
    private static @Nullable AccidentalBounds @Nullable [] baseAccidentalBoundsSs = null;
    private static @Nullable AccidentalBounds @Nullable [] baseAccidentalParenthesisBoundsSs = null;

    // Cached bounding boxes of only the note-adjacent (rightmost) glyph, indexed by
    // Accidental.ordinal(). Used for ledger-line shortening, which checks only the glyph closest to
    // the notehead: the right paren when parenthesized, else the last component (e.g. the flat of a
    // natural-flat).
    private static @Nullable AccidentalBounds @Nullable [] closestAccidentalBoundsSs = null;
    private static @Nullable AccidentalBounds @Nullable [] closestAccidentalParenthesisBoundsSs = null;

    // Accidental-bbox combiner that keeps the most recently visited (rightmost) glyph, isolating the
    // note-adjacent glyph for ledger-line shortening. Contrast BBox::union, which keeps the full span.
    private static final BinaryOperator<BBox> KEEP_RIGHTMOST = (previous, rightmost) -> rightmost;

    private NoteGeometry() {}

    // ==========================================================================
    // Initialization
    // ==========================================================================

    /**
     * Initialises the cached accidental widths and bounding boxes from SMuFL metadata.
     * Must be called once before any accidental query method.
     */
    public static void initializeAccidentalWidths() {
        if (baseAccidentalWidthsSs != null) {
            return;
        }

        baseAccidentalWidthsSs = computeComponentWidths(ACCIDENTAL_COMPONENTS);

        var beginParenthesisWidthSs = (float) SMuFLMetadata.getAdvanceWidthOrZero(SMuFLGlyph.ACCIDENTAL_PARENS_LEFT);
        var endParenthesisWidthSs = (float) SMuFLMetadata.getAdvanceWidthOrZero(SMuFLGlyph.ACCIDENTAL_PARENS_RIGHT);

        baseAccidentalParenthesisWidthsSs = new float[ACCIDENTAL_COMPONENTS.length];

        for (var i = 0; i < baseAccidentalParenthesisWidthsSs.length; i++) {
            baseAccidentalParenthesisWidthsSs[i] =
                baseAccidentalWidthsSs[i] + beginParenthesisWidthSs + endParenthesisWidthSs;
            baseAccidentalParenthesisWidthsSs[i] += parenthesizedAccidentalKerningSs(i);
        }

        baseAccidentalBoundsSs = new AccidentalBounds[ACCIDENTAL_COMPONENTS.length];
        baseAccidentalParenthesisBoundsSs = new AccidentalBounds[ACCIDENTAL_COMPONENTS.length];
        closestAccidentalBoundsSs = new AccidentalBounds[ACCIDENTAL_COMPONENTS.length];
        closestAccidentalParenthesisBoundsSs = new AccidentalBounds[ACCIDENTAL_COMPONENTS.length];

        for (var i = 0; i < ACCIDENTAL_COMPONENTS.length; i++) {
            baseAccidentalBoundsSs[i] = computeAccidentalBounds(
                ACCIDENTAL_COMPONENTS[i], false, baseAccidentalWidthsSs[i], BBox::union);
            baseAccidentalParenthesisBoundsSs[i] = computeAccidentalBounds(
                ACCIDENTAL_COMPONENTS[i], true, baseAccidentalParenthesisWidthsSs[i], BBox::union);
            closestAccidentalBoundsSs[i] = computeAccidentalBounds(
                ACCIDENTAL_COMPONENTS[i], false, baseAccidentalWidthsSs[i], KEEP_RIGHTMOST);
            closestAccidentalParenthesisBoundsSs[i] = computeAccidentalBounds(
                ACCIDENTAL_COMPONENTS[i], true, baseAccidentalParenthesisWidthsSs[i], KEEP_RIGHTMOST);
        }
    }

    // ==========================================================================
    // Accidental Geometry
    // ==========================================================================

    /**
     * Returns the drawn width of the accidental for a note, in staff spaces. Grace notes use the
     * regular glyphs drawn at {@link ElementType#GRACE_NOTE_SCALE}, so their width is the regular
     * width scaled by that factor (this must agree with the scaled advance used in
     * {@link #walkAccidentalGlyphs}).
     */
    public static float getAccidentalWidthSs(StaffElement note) {
        var accidental = note.getAccidental();

        if (accidental == null) {
            return 0;
        }

        var ordinal = accidental.ordinal();
        var baseWidths = baseAccidentalWidthsSs;
        var parenWidths = baseAccidentalParenthesisWidthsSs;

        if (baseWidths == null || parenWidths == null) {
            throw RuntimeError.exit("getAccidentalWidthSs() called before initializeAccidentalWidths()");
        }

        var widthSs = note.isAccidentalInParentheses()
            ? parenWidths[ordinal]
            : baseWidths[ordinal];

        return note.getType().isGraceNote() ? ElementType.GRACE_NOTE_SCALE * widthSs : widthSs;
    }

    /**
     * Returns the pen start X for drawing (and measuring the clearance area of) a note's accidental,
     * in staff spaces relative to the notehead origin. The accidental's right edge sits one padding
     * left of the origin; for grace notes the padding scales with the glyphs
     * ({@link ElementType#GRACE_NOTE_SCALE}) so the gap stays proportional to the smaller notehead
     * instead of looking oversized.
     *
     * <p>The renderer and {@code NoteColumnGeometry} must share this so the drawn glyphs and the
     * glissando-clearance geometry cannot drift apart.
     */
    public static float getAccidentalStartXSs(StaffElement note) {
        var paddingSs = note.getType().isGraceNote()
            ? GRACE_ACCIDENTAL_PADDING_SS
            : ACCIDENTAL_PADDING_SS;

        return -paddingSs - getAccidentalWidthSs(note);
    }

    /**
     * Returns the font scale for drawing {@code note}'s glyphs: {@link ElementType#GRACE_NOTE_SCALE}
     * for grace notes, otherwise {@code 1f}. Pair with the grace-aware font from {@code RenderingUtils}
     * so the scale and the font always agree, and feed it to {@link #walkAccidentalGlyphs}.
     */
    public static float getGlyphScale(StaffElement note) {
        return note.getType().isGraceNote() ? ElementType.GRACE_NOTE_SCALE : 1f;
    }

    /**
     * Returns the bounding box of the accidental drawn for a note, in staff-space units.
     *
     * <p>Horizontal coordinates are relative to the notehead glyph origin (x = 0).
     * Vertical coordinates are relative to the note centre (y = 0), using Y-down convention.
     *
     * <p>Returns {@code null} when the note has no accidental, or when it is a grace note:
     * grace-note accidentals are deliberately not reserved into the structural stacking layer
     * (see {@code VerticalStackingCalculator.seedAccidentalsIntoStructural}), so this returns no
     * bounds for them rather than scaled bounds.
     */
    public static @Nullable AccidentalBounds getAccidentalBoundsSs(StaffElement note) {
        return accidentalBoundsFromTables(note, baseAccidentalBoundsSs, baseAccidentalParenthesisBoundsSs);
    }

    /**
     * Returns the bounding box of only the glyph nearest the notehead, in staff-space units, using
     * the same coordinate convention as {@link #getAccidentalBoundsSs}.
     *
     * <p>The accidental glyphs are laid out left to right with the notehead to the right, so the
     * nearest glyph is the rightmost one: the right parenthesis for a parenthesized accidental,
     * otherwise the last accidental component (e.g. the flat of a natural-flat). Ledger-line
     * shortening checks only this glyph rather than the full accidental span.
     *
     * <p>Returns {@code null} under the same conditions as {@link #getAccidentalBoundsSs}.
     */
    public static @Nullable AccidentalBounds getClosestAccidentalComponentBoundsSs(StaffElement note) {
        return accidentalBoundsFromTables(note, closestAccidentalBoundsSs, closestAccidentalParenthesisBoundsSs);
    }

    private static @Nullable AccidentalBounds accidentalBoundsFromTables(
        StaffElement note,
        @Nullable AccidentalBounds @Nullable [] baseTable,
        @Nullable AccidentalBounds @Nullable [] parenthesisTable
    ) {
        var accidental = note.getAccidental();

        if (accidental == null) {
            return null;
        }

        // Grace-note accidentals do not reserve structural stacking space (by design).
        if (note.getType().isGraceNote()) {
            return null;
        }

        var table = note.isAccidentalInParentheses() ? parenthesisTable : baseTable;

        if (table == null) {
            throw RuntimeError.exit("accidentalBoundsFromTables() called before initializeAccidentalWidths()");
        }

        return table[accidental.ordinal()];
    }

    // ==========================================================================
    // Notehead / Ledger Line Geometry
    // ==========================================================================

    /**
     * Horizontal extent of a ledger line, in staff spaces relative to the note origin
     * (X right-positive). {@code leftSs} and {@code rightSs} are the line's left and right edges.
     */
    public record LedgerExtentSs(double leftSs, double rightSs) {}

    /** Outermost staff-line position (top/bottom line); |position| beyond this needs ledger lines. */
    private static final int OUTERMOST_STAFF_LINE_POSITION = 5;

    /**
     * Per-note ledger-line geometry: the y-invariant base extent plus the data needed to shorten any
     * individual ledger line for the nearest accidental. This is identical for all of a note's ledger
     * lines, so compute it once (via {@link #getLedgerLineGeometry}) and call {@link #extentAtSs} per
     * line rather than recomputing the head/accidental geometry for each.
     *
     * <p>Coordinate convention (note-relative, X right-positive, Y down-positive):
     *
     * <pre>
     *                       note origin (x = 0)
     *                            |
     *         headLeft           |          headRight
     *            |               |              |
     *    --------+---------------+--------------+--------   ← a ledger line (one of several),
     *    |       |          width = headRight - headLeft    drawn at note-relative y = yOffsetSs
     *    |       |                              |       |   (Y increases downward)
     * ledgerLeft |                              |  ledgerRight
     *    <-------->                             <-------->
     *    lf · width                             lf · width
     *
     *    accidental clamp midpoint = (accRight + headLeft) / 2
     *      lands between the accidental's right edge and headLeft, i.e. right of ledgerLeft;
     *      extentAtSs() pulls ledgerLeft in to this midpoint where an accidental spans the ledger's y.
     * </pre>
     */
    public record LedgerLineGeometry(
        LedgerExtentSs baseExtentSs,
        @Nullable AccidentalBounds closestAccidentalSs,
        double headLeftSs
    ) {
        /**
         * Extent of the ledger line at note-relative {@code yOffsetSs} (Y down-positive, note-center
         * origin, matching {@link NoteGeometry#getAccidentalBoundsSs}). Where the nearest accidental
         * spans this ledger's Y, pulls the left edge in to the midpoint between the accidental's right
         * edge and the notehead's left edge (LilyPond's per-head accidental shortening); otherwise
         * returns the base extent unchanged.
         */
        public LedgerExtentSs extentAtSs(double yOffsetSs) {
            // closestAccidentalSs and yOffsetSs share the note-center, Y-down origin, so the closed
            // vertical-range test needs no coordinate conversion. The max is the only guard (LilyPond
            // left_shorten = max(dist - ledgerLeft, 0)); there is no accRight > ledgerLeft precondition.
            if (closestAccidentalSs == null
                || yOffsetSs < closestAccidentalSs.topSs()
                || yOffsetSs > closestAccidentalSs.botSs()) {
                return baseExtentSs;
            }

            var accidentalRightSs = closestAccidentalSs.leftSs() + closestAccidentalSs.widthSs();
            var ledgerLeftSs = Math.max(baseExtentSs.leftSs(), (accidentalRightSs + headLeftSs) / 2);

            return new LedgerExtentSs(ledgerLeftSs, baseExtentSs.rightSs());
        }
    }

    /**
     * Returns whether a note requires ledger lines: notes within the staff
     * (|staffPosition| ≤ {@value #OUTERMOST_STAFF_LINE_POSITION}) or whose type does not draw staff
     * longitude get no ledger lines.
     */
    public static boolean noteNeedsLedgerLines(StaffElement note) {
        return Math.abs(note.getStaffPosition()) > OUTERMOST_STAFF_LINE_POSITION
            && note.getType().drawStaveLongitude();
    }

    /**
     * Returns the {@link LedgerLineGeometry} for {@code note}: its base ledger extent plus the data
     * needed to shorten individual ledger lines for the nearest accidental. Compute this once per
     * note and reuse it across the note's ledger lines.
     *
     * <p>Each side of the base extent runs beyond the notehead bbox by
     * {@link Engraving#LEDGER_LINE_LENGTH_FRACTION} × notehead width (LilyPond's proportional rule).
     *
     * <p>Every note type passing {@link #noteNeedsLedgerLines} yields a non-null glyph via
     * {@code requireSMuFLGlyph()}, and {@code bbox.left()} is {@code 0.0} for all noteheads, so no
     * fallback or left-edge constant is needed.
     */
    public static LedgerLineGeometry getLedgerLineGeometry(StaffElement note) {
        var noteType = note.getType();
        var bbox = SMuFLMetadata.requireBBox(noteType.requireSMuFLGlyph());
        var direction = effectiveDirection(note);
        var offsetSs = getNoteheadXOffsetSs(noteType, direction);
        var headLeftSs = offsetSs + bbox.left();
        var headRightSs = offsetSs + bbox.right();
        var widthSs = headRightSs - headLeftSs;
        var extensionSs = Engraving.LEDGER_LINE_LENGTH_FRACTION * widthSs;
        var baseExtentSs = new LedgerExtentSs(headLeftSs - extensionSs, headRightSs + extensionSs);

        return new LedgerLineGeometry(baseExtentSs, getClosestAccidentalComponentBoundsSs(note), headLeftSs);
    }

    /**
     * Returns the base horizontal extent of every ledger line for {@code note} (head geometry only —
     * no accidental shortening), in staff spaces relative to the note origin. Convenience wrapper over
     * {@link #getLedgerLineGeometry} for callers that need the base extent alone.
     */
    public static LedgerExtentSs getLedgerLineBaseExtentSs(StaffElement note) {
        return getLedgerLineGeometry(note).baseExtentSs();
    }

    /**
     * Returns the horizontal extent of the single ledger line at note-relative {@code yOffsetSs},
     * applying per-head accidental shortening. Convenience wrapper over {@link #getLedgerLineGeometry}
     * followed by {@link LedgerLineGeometry#extentAtSs}; callers drawing all of a note's ledger lines
     * should reuse one {@link LedgerLineGeometry} instead of calling this per line.
     */
    public static LedgerExtentSs getLedgerLineExtentSs(StaffElement note, double yOffsetSs) {
        return getLedgerLineGeometry(note).extentAtSs(yOffsetSs);
    }

    /**
     * Returns the X offset of the notehead origin relative to the note's column X,
     * in staff spaces. Stem-down notes shift left by half a stem width.
     */
    public static float getNoteheadXOffsetSs(ElementType noteType, StaffElement.Direction direction) {
        if (noteType.isNoteWithStem() && direction.isDown()) {
            return (float) -(STEM_WIDTH_SS / 2);
        }

        return 0f;
    }

    /**
     * Returns the X origin for a grace note's flag glyph, given the stem's left edge X
     * (both in staff spaces). SMuFL flag glyphs anchor at the stem's left edge, but the
     * scaled grace glyph's internal stem connection is only {@link ElementType#GRACE_NOTE_SCALE}
     * of the full stem width, so the flag is shifted right to stay centered on the actual stem.
     *
     * <p>Used by both flag rendering and glissando column-extent computation, which must agree
     * on where the grace flag sits.
     */
    public static double getGraceFlagOriginXSs(double stemLeftXSs) {
        return stemLeftXSs + STEM_WIDTH_SS * (1 - ElementType.GRACE_NOTE_SCALE) / 2;
    }

    /**
     * Returns the flag glyph's bounding box in staff spaces, in the note's local coordinate
     * space (relative to the notehead origin), or {@code null} when the note has no rendered
     * flag (beamed, stemless, or no flag glyph for the type).
     *
     * <p>This is the single source of truth for where the flag sits, so that spacing
     * reservation, augmentation-dot clearance, and column geometry never drift apart. The
     * returned origin matches the renderer's paint origin: non-grace flags anchor at the stem's
     * left edge, while grace flags use {@link #getGraceFlagOriginXSs} and are scaled by
     * {@link ElementType#GRACE_NOTE_SCALE}.
     */
    @Nullable
    public static Rectangle2D flagBBoxLocalSs(ElementType noteType, StaffElement.Direction direction, boolean beamed) {
        if (beamed || !noteType.isNoteWithStem()) {
            return null;
        }

        var flagGlyph = noteType.getFlagGlyph(direction);

        if (flagGlyph == null) {
            return null;
        }

        var stemGeom = computeBaseStemGeometry(noteType, direction);
        var stemLeftXSs = stemGeom.stemLeftXSs();
        var stemTipYSs = stemGeom.stemTipYSs(direction);
        var flagBBox = SMuFLMetadata.requireBBox(flagGlyph);

        if (noteType.isGraceNote()) {
            var scale = (double) ElementType.GRACE_NOTE_SCALE;
            var flagOriginX = getGraceFlagOriginXSs(stemLeftXSs);

            return new Rectangle2D.Double(
                flagOriginX + flagBBox.left() * scale,
                stemTipYSs + flagBBox.top() * scale,
                flagBBox.width() * scale,
                flagBBox.height() * scale);
        }

        return new Rectangle2D.Double(
            stemLeftXSs + flagBBox.left(),
            stemTipYSs + flagBBox.top(),
            flagBBox.width(),
            flagBBox.height());
    }

    /**
     * Returns the right edge of the notehead bounding box in staff spaces, relative to note X.
     *
     * <p>The value is read from bravura_metadata.json via SMuFLMetadata. For grace notes this
     * returns the noteheadBlackSmall bbox, which is already at the correct size.
     */
    public static double getNoteheadRightEdgeSs(StaffElement note) {
        return SMuFLMetadata.requireBBox(note.getType().requireSMuFLGlyph()).right();
    }

    // ==========================================================================
    // Helpers (also used by NoteRenderer for drawing)
    // ==========================================================================

    /**
     * Walks the glyph sequence that renders an accidental (optional left paren,
     * components, optional right paren), invoking {@code visitor} with each glyph
     * and its pen-position X. Centralises the advance-width and kerning bookkeeping
     * so the draw pass and bounds computation cannot drift apart.
     *
     * <p>{@code scale} multiplies every advance, inter-component gap, and paren kerning so the
     * pen positions track glyphs drawn at a scaled font size. Pass {@code 1f} for full-size
     * accidentals and {@link ElementType#GRACE_NOTE_SCALE} for grace-note accidentals.
     */
    public static void walkAccidentalGlyphs(
        SMuFLGlyph[] components,
        boolean parenthesized,
        float startX,
        float scale,
        BiConsumer<SMuFLGlyph, Float> visitor
    ) {
        var x = startX;

        if (parenthesized) {
            visitor.accept(SMuFLGlyph.ACCIDENTAL_PARENS_LEFT, x);
            x = advancePast(x, SMuFLGlyph.ACCIDENTAL_PARENS_LEFT, scale);
            x += scale * PAREN_LEFT_KERNING.getOrDefault(components[0], 0f);
        }

        for (var i = 0; i < components.length; i++) {
            if (i > 0) {
                x += scale * SPACE_BETWEEN_TWO_ACCIDENTALS_SS;
            }

            visitor.accept(components[i], x);
            x = advancePast(x, components[i], scale);
        }

        if (parenthesized) {
            x += scale * PAREN_RIGHT_KERNING.getOrDefault(components[components.length - 1], 0f);
            visitor.accept(SMuFLGlyph.ACCIDENTAL_PARENS_RIGHT, x);
        }
    }

    private static float advancePast(float x, SMuFLGlyph glyph, float scale) {
        return x + scale * (float) SMuFLMetadata.getAdvanceWidthOrZero(glyph);
    }

    // ==========================================================================
    // Private Helpers
    // ==========================================================================

    private static float parenthesizedAccidentalKerningSs(int accidentalOrdinal) {
        var components = ACCIDENTAL_COMPONENTS[accidentalOrdinal];

        if (components.length == 0) {
            return 0f;
        }

        return PAREN_LEFT_KERNING.getOrDefault(components[0], 0f)
            + PAREN_RIGHT_KERNING.getOrDefault(components[components.length - 1], 0f);
    }

    /**
     * Folds the bounding boxes of a note's accidental glyphs into a single {@link AccidentalBounds},
     * combining successive (left-to-right) glyph boxes with {@code combine}.
     *
     * <p>{@code combine} selects what the result represents: {@link BBox#union} yields the full
     * accidental span, while {@link #KEEP_RIGHTMOST} yields only the glyph nearest the notehead —
     * the rightmost one (the right parenthesis when parenthesized, otherwise the last component such
     * as the flat of a natural-flat), used for ledger-line shortening (see {@link #getLedgerLineExtentSs}).
     */
    private static @Nullable AccidentalBounds computeAccidentalBounds(
        SMuFLGlyph[] components,
        boolean parenthesized,
        float totalWidthSs,
        BinaryOperator<BBox> combine
    ) {
        var startX = -ACCIDENTAL_PADDING_SS - totalWidthSs;
        var accumulator = new BBox[]{null};

        // Base (non-grace) bounds: glyphs are laid out at full size.
        walkAccidentalGlyphs(components, parenthesized, startX, 1f, (glyph, xSs) -> {
            var shifted = SMuFLMetadata.requireBBox(glyph).translateX(xSs);
            accumulator[0] = (accumulator[0] == null) ? shifted : combine.apply(accumulator[0], shifted);
        });

        var box = accumulator[0];

        if (box == null) {
            return null;
        }

        return new AccidentalBounds(box.left(), box.width(), box.top(), box.bottom());
    }

    private static float[] computeComponentWidths(SMuFLGlyph[][] componentTable) {
        var widths = new float[componentTable.length];

        for (var i = 0; i < componentTable.length; i++) {
            var components = componentTable[i];
            var width = 0f;

            for (var c = 0; c < components.length; c++) {
                if (c > 0) {
                    width += SPACE_BETWEEN_TWO_ACCIDENTALS_SS;
                }

                width += (float) SMuFLMetadata.getAdvanceWidthOrZero(components[c]);
            }

            widths[i] = width;
        }

        return widths;
    }
}
