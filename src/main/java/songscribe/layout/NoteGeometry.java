package songscribe.layout;

import java.awt.geom.Rectangle2D;
import java.util.EnumMap;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;

import org.jspecify.annotations.Nullable;

import songscribe.dom.AccidentalBounds;
import songscribe.dom.ElementType;
import songscribe.dom.StaffElement;
import songscribe.dom.StaffElement.Accidental;
import songscribe.engraving.LineThickness;
import songscribe.engraving.SMuFLConstants;
import songscribe.engraving.Staff;
import songscribe.error.RuntimeError;
import songscribe.smufl.BBox;
import songscribe.smufl.Anchor;
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
    public static final double STEM_WIDTH_SS = LineThickness.STEM_SS;

    /**
     * Shortening applied per diatonic step of distance from the middle line to a stem forced into its
     * unnatural direction. LilyPond's {@code shortening_step} of 0.3333 half-spaces, halved to ss
     * (see {@code stem.cc:519-555}).
     */
    public static final double FORCED_SHORTEN_PER_STEP_SS = 1.0 / 6.0;

    /**
     * Maximum a forced stem may be shortened, in staff spaces. LilyPond's {@code 2·stem-shorten[0]}
     * of 2.0 half-spaces, halved to ss.
     */
    public static final double MAX_FORCED_SHORTEN_SS = 1.0;

    /**
     * Floor a forced stem may be shortened to, in staff spaces. The forced-shortening formula caps at
     * {@link #MAX_FORCED_SHORTEN_SS}, so a forced stem never falls below this; kept as a named
     * constant for defensive flooring at the renderer.
     */
    public static final double FORCED_STEM_FLOOR_SS = SMuFLConstants.STEM_LENGTH_SS - MAX_FORCED_SHORTEN_SS;

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
     * Stem anchor point for a grace note's stem-up south-east corner: the ordinary black
     * notehead's stem anchor, scaled by {@link ElementType#GRACE_NOTE_SCALE} because a grace
     * note is an ordinary black notehead drawn at a reduced font size.
     */
    public static final Anchor GRACE_STEM_UP_SE = new Anchor(
        SMuFLMetadata.stemAnchors(ElementType.GRACE_QUAVER.stemmedNotehead()).stemUpSE().xSs()
            * ElementType.GRACE_NOTE_SCALE,
        SMuFLMetadata.stemAnchors(ElementType.GRACE_QUAVER.stemmedNotehead()).stemUpSE().ySs()
            * ElementType.GRACE_NOTE_SCALE
    );

    // ==========================================================================
    // Augmentation Dot Geometry
    // ==========================================================================

    // Augmentation dot Y shift (in ss) when the note sits on a staff line —
    // moves the dot into the space above the line.
    public static final double DOT_ON_LINE_Y_SHIFT_SS = -0.5;

    // Center-to-center spacing between consecutive augmentation dots (in ss): one dot glyph
    // plus a one-dot-width gap, following LilyPond's stack-by-one-dot-width convention.
    public static final double DOT_SPACING_SS = SMuFLConstants.AUGMENTATION_DOT_WIDTH_SS * 2;

    /**
     * Returns the X position (in ss, from the note glyph origin) of the first augmentation dot,
     * following LilyPond's pad-by-one-dot-width convention: the dot sits one augmentation-dot width
     * to the right of the note's right-most obstruction. That obstruction is the notehead, unless an
     * unbeamed up-stem flag extends further right.
     */
    public static double firstDotXSs(ElementType noteType, boolean beamed, StaffElement.Direction direction) {
        var obstructionRightSs = SMuFLMetadata.bboxSs(noteType.notehead().glyph()).rightSs();

        if (direction.isUp()) {
            var flagBBox = flagBBoxLocalSs(noteType, StaffElement.Direction.UP, beamed);

            if (flagBBox != null) {
                obstructionRightSs = Math.max(obstructionRightSs, flagBBox.getMaxX());
            }
        }

        return obstructionRightSs + SMuFLConstants.AUGMENTATION_DOT_WIDTH_SS;
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
        var yOffset = StaffElement.isLinePosition(note.getStaffPosition()) ? DOT_ON_LINE_Y_SHIFT_SS : 0.0;

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

        var dotRightSs = SMuFLMetadata.bboxSs(SMuFLGlyph.AUGMENTATION_DOT).rightSs();
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
     * Returns the amount (≥ 0, ss) a stem is shortened because it points in its <em>forced</em>
     * (unnatural) direction, per Ross &amp; Gourlay (LilyPond {@code stem.cc:519-555}).
     *
     * <p>A stem is forced when its direction opposes the default: an up-stem at or above the middle
     * line ({@code staffPosition <= 0}) or a down-stem below it ({@code staffPosition > 0}). Because
     * an auto stem always equals the default direction, it is never forced, so this returns 0 for
     * every auto stem regardless of when direction is resolved. Grace stems are never shortened.
     *
     * <p>The shortening grows {@link #FORCED_SHORTEN_PER_STEP_SS} per diatonic step of distance from
     * the middle line and caps at {@link #MAX_FORCED_SHORTEN_SS}, reaching the cap (a
     * {@link #FORCED_STEM_FLOOR_SS} residual stem) one diatonic step beyond the top staff line.
     *
     * @param staffPosition integer diatonic steps from the middle line (positive = below)
     * @param direction     the note's stem direction
     * @param isGraceNote   whether the note is a grace note
     * @return the shortening in staff spaces (0 for natural, auto, and grace stems)
     */
    public static double forcedShorteningSs(
        int staffPosition, StaffElement.Direction direction, boolean isGraceNote) {
        if (isGraceNote) {
            return 0.0;
        }

        var forcedUp = direction.isUp() && staffPosition <= 0;
        var forcedDown = direction.isDown() && staffPosition > 0;

        if (!forcedUp && !forcedDown) {
            return 0.0;
        }

        var shortenSs = FORCED_SHORTEN_PER_STEP_SS * (1 + Math.abs(staffPosition));
        return Math.min(MAX_FORCED_SHORTEN_SS, shortenSs);
    }

    /**
     * Returns the stem length for a note {@code type}: the shortened grace stem for grace notes, the
     * standard stem otherwise. Single source of truth for the length shared by the drawn stem
     * ({@link #computeBaseStemGeometry}) and the column's baked vertical extent
     * ({@link ElementColumnBuilder#calculateStemTopSs}).
     */
    public static double stemLengthSs(ElementType noteType) {
        return noteType.isGraceNote()
            ? SMuFLConstants.GRACE_NOTE_STEM_LENGTH_SS
            : SMuFLConstants.STEM_LENGTH_SS;
    }

    /**
     * Computes the base stem geometry for a note type and direction.
     * This is the shared anchor selection and positioning logic used by both
     * {@code StaffElementRenderer} (for drawing) and {@code GlissandoRenderer} (for area building).
     *
     * @param noteType  The note type (determines anchor and stem length)
     * @param direction UP for stem-up, DOWN for stem-down
     * @return The base stem geometry
     */
    public static StemGeometry computeBaseStemGeometry(ElementType noteType, StaffElement.Direction direction) {
        var isGrace = noteType.isGraceNote();
        var anchor = isGrace ? GRACE_STEM_UP_SE : stemSideAnchor(noteType, direction);
        var anchorXSs = anchor.xSs();

        // Stem left edge: the anchor marks the stem's RIGHT edge for up-stems (back off a full
        // stem width) and its LEFT edge for down-stems (use the anchor directly). Both leave the
        // stem's outer edge flush with the notehead's outer edge, overlapping inward by its width.
        var stemLeftXSs = direction.isUp() ? anchorXSs - STEM_WIDTH_SS : anchorXSs;

        return new StemGeometry(stemLeftXSs, anchor.ySs(), stemLengthSs(noteType));
    }

    /**
     * The stem-side notehead anchor (SE for up-stems, NW for down-stems) per SMuFL's
     * per-notehead-shape metrics, for a non-grace note. Grace notes use a separate, smaller
     * anchor — see {@link #computeBaseStemGeometry}. Shared with
     * {@link songscribe.ui.renderer.RenderingUtils#stemCenterXOffsetSs} so the anchor lookup is
     * defined once.
     */
    public static Anchor stemSideAnchor(ElementType noteType, StaffElement.Direction direction) {
        var anchors = SMuFLMetadata.stemAnchors(noteType.stemmedNotehead());

        return direction.isUp() ? anchors.stemUpSE() : anchors.stemDownNW();
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

    /**
     * Horizontal gap between notehead origin and the right edge of an accidental, in staff spaces.
     * <p>
     * Single source of truth for the accidental-to-notehead gap: the renderer draws with it and the
     * layout reserves a column's left extent with it ({@code ElementColumnBuilder.calculateLeftExtentSs}),
     * so the reserved extent matches what is actually drawn.
     */
    public static final float ACCIDENTAL_PADDING_SS = 0.3375f; // 2.7px / 8 px/ss

    /**
     * Grace-note counterpart of {@link #ACCIDENTAL_PADDING_SS}, scaled by
     * {@link ElementType#GRACE_NOTE_SCALE} so the gap stays proportional to the smaller grace
     * notehead instead of looking oversized.
     */
    public static final float GRACE_ACCIDENTAL_PADDING_SS =
        ElementType.GRACE_NOTE_SCALE * ACCIDENTAL_PADDING_SS;

    // Kerning adjustments for parenthesized accidentals (in staff-space units).
    // Positive = more space, negative = less space.
    // Package-private so NoteGeometryTest reads the real values instead of duplicating them.
    static final EnumMap<SMuFLGlyph, Float> PAREN_LEFT_KERNING = new EnumMap<>(SMuFLGlyph.class);
    static final EnumMap<SMuFLGlyph, Float> PAREN_RIGHT_KERNING = new EnumMap<>(SMuFLGlyph.class);

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

    // Cached bounding boxes of only the note-adjacent (rightmost) glyph of a parenthesized
    // accidental — the right paren — indexed by Accidental.ordinal(). Used for ledger-line
    // shortening, which checks only the glyph closest to the notehead. Unparenthesized accidentals
    // draw a single glyph, so their closest-glyph bounds are baseAccidentalBoundsSs.
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

        var accidentals = Accidental.values();
        var count = accidentals.length;

        baseAccidentalWidthsSs = new float[count];

        for (var i = 0; i < count; i++) {
            baseAccidentalWidthsSs[i] = (float) SMuFLMetadata.advanceWidthSs(accidentals[i].glyph());
        }

        var beginParenthesisWidthSs = (float) SMuFLMetadata.advanceWidthSs(SMuFLGlyph.ACCIDENTAL_PARENS_LEFT);
        var endParenthesisWidthSs = (float) SMuFLMetadata.advanceWidthSs(SMuFLGlyph.ACCIDENTAL_PARENS_RIGHT);

        baseAccidentalParenthesisWidthsSs = new float[count];

        for (var i = 0; i < count; i++) {
            baseAccidentalParenthesisWidthsSs[i] =
                baseAccidentalWidthsSs[i] + beginParenthesisWidthSs + endParenthesisWidthSs;
            baseAccidentalParenthesisWidthsSs[i] += parenthesizedAccidentalKerningSs(accidentals[i].glyph());
        }

        baseAccidentalBoundsSs = new AccidentalBounds[count];
        baseAccidentalParenthesisBoundsSs = new AccidentalBounds[count];
        closestAccidentalParenthesisBoundsSs = new AccidentalBounds[count];

        for (var i = 0; i < count; i++) {
            var glyph = accidentals[i].glyph();

            baseAccidentalBoundsSs[i] = computeAccidentalBounds(
                glyph, false, baseAccidentalWidthsSs[i], BBox::union);
            baseAccidentalParenthesisBoundsSs[i] = computeAccidentalBounds(
                glyph, true, baseAccidentalParenthesisWidthsSs[i], BBox::union);
            closestAccidentalParenthesisBoundsSs[i] = computeAccidentalBounds(
                glyph, true, baseAccidentalParenthesisWidthsSs[i], KEEP_RIGHTMOST);
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
     * Vertical coordinates are relative to the note center (y = 0), using Y-down convention.
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
     * otherwise the accidental glyph itself. Ledger-line shortening checks only this glyph rather
     * than the full accidental span.
     *
     * <p>Returns {@code null} under the same conditions as {@link #getAccidentalBoundsSs}.
     */
    public static @Nullable AccidentalBounds getClosestAccidentalGlyphBoundsSs(StaffElement note) {
        return accidentalBoundsFromTables(note, baseAccidentalBoundsSs, closestAccidentalParenthesisBoundsSs);
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
     * Calculates the Y coordinate for a note given its staff position.
     * <p>
     * The staff position is relative to the middle line (B4), where:
     * <ul>
     *   <li>0 = B4 (middle line)</li>
     *   <li>Negative values = higher pitches (above middle line)</li>
     *   <li>Positive values = lower pitches (below middle line)</li>
     * </ul>
     * Each staff position is 0.5 ss (half a staff space).
     * <p>
     * Pass {@code middleLineYSs = 0} to get the midline-relative layout-space Y; pass the line's
     * actual middle-line Y to get component space.
     *
     * @param staffPosition The note's staff position relative to middle line
     * @param middleLineYSs Y position of middle staff line in staff spaces
     * @return Y coordinate for the note in staff spaces
     */
    public static double noteStaffPositionToCoordinateSs(int staffPosition, double middleLineYSs) {
        return middleLineYSs + Staff.spToSs(staffPosition);
    }

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
     * <p>Coordinates are note-relative, X right-positive and Y down-positive. A ledger line runs
     * from {@code ledgerLeft} to {@code ledgerRight}, overhanging the notehead by the ledger factor
     * times the head width ({@code headRight - headLeft}) on each side. Where an accidental spans
     * the ledger's y, {@link #extentAtSs} pulls {@code ledgerLeft} in to the clamp midpoint
     * {@code (accRight + headLeft) / 2}, which always lands right of {@code ledgerLeft}.
     *
     * <p>See {@code docs/layout-geometry.md} for the annotated diagram.
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
     * {@link SMuFLConstants#LEDGER_LINE_LENGTH_FRACTION} × notehead width (LilyPond's proportional rule).
     *
     * <p>Every note type passing {@link #noteNeedsLedgerLines} has a notehead, and
     * {@code bbox.left()} is {@code 0.0} for all noteheads, so no fallback or left-edge constant
     * is needed.
     */
    public static LedgerLineGeometry getLedgerLineGeometry(StaffElement note) {
        var noteType = note.getType();
        var bbox = SMuFLMetadata.bboxSs(noteType.notehead().glyph());
        var direction = effectiveDirection(note);
        var offsetSs = getNoteheadXOffsetSs(noteType, direction);
        var headLeftSs = offsetSs + bbox.leftSs();
        var headRightSs = offsetSs + bbox.rightSs();
        var widthSs = headRightSs - headLeftSs;
        var extensionSs = SMuFLConstants.LEDGER_LINE_LENGTH_FRACTION * widthSs;
        var baseExtentSs = new LedgerExtentSs(headLeftSs - extensionSs, headRightSs + extensionSs);

        return new LedgerLineGeometry(baseExtentSs, getClosestAccidentalGlyphBoundsSs(note), headLeftSs);
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
     * Returns the X offset of the notehead origin relative to the note's column X, in staff spaces.
     * Always zero: noteheads are drawn at the column X regardless of stem direction, so up-stem and
     * down-stem noteheads align horizontally. (Down-stem heads were formerly shifted left by half a
     * stem width; that shift only pushed the whole note off its reference and has been removed.)
     */
    @SuppressWarnings("SameReturnValue")
    public static float getNoteheadXOffsetSs(ElementType noteType, StaffElement.Direction direction) {
        return 0f;
    }

    /**
     * Returns the X of the notehead's center relative to the note's column X, in staff spaces.
     * <p>
     * Anything that must sit centered over the notehead — an articulation, a dynamic, a fermata —
     * measures from here, not from the column X: the column is as wide as the whole element, and an
     * accent or a dynamic is wider than the head itself.
     */
    public static double getNoteheadCenterXSs(StaffElement note) {
        var noteType = note.getType();
        return noteType.getElementCenterXSs() + getNoteheadXOffsetSs(noteType, note.getDirection());
    }

    /**
     * Returns the absolute X of the left edge of a {@code widthSs}-wide glyph centered over
     * {@code note}'s notehead — an articulation, a text dynamic, anything drawn over the head.
     * <p>
     * This is the one place the centering is expressed, so a dynamic and an articulation over the
     * same note can never drift apart: change the rule here and both move together.
     *
     * @param note      the note the glyph sits over
     * @param columnXSs the absolute X of the note's column
     * @param widthSs   the glyph's width in staff spaces
     * @return the glyph's absolute left edge in staff spaces
     */
    public static double centeredOverNoteheadXSs(StaffElement note, double columnXSs, double widthSs) {
        return columnXSs + getNoteheadCenterXSs(note) - widthSs / 2.0;
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
        var flagBBox = SMuFLMetadata.bboxSs(flagGlyph);

        if (noteType.isGraceNote()) {
            var scale = (double) ElementType.GRACE_NOTE_SCALE;
            var flagOriginX = getGraceFlagOriginXSs(stemLeftXSs);

            return new Rectangle2D.Double(
                flagOriginX + flagBBox.leftSs() * scale,
                stemTipYSs + flagBBox.topSs() * scale,
                flagBBox.widthSs() * scale,
                flagBBox.heightSs() * scale);
        }

        return new Rectangle2D.Double(
            stemLeftXSs + flagBBox.leftSs(),
            stemTipYSs + flagBBox.topSs(),
            flagBBox.widthSs(),
            flagBBox.heightSs());
    }

    // ==========================================================================
    // Helpers (also used by StaffElementRenderer for drawing)
    // ==========================================================================

    /**
     * Walks the glyph sequence that renders an accidental (optional left paren,
     * the accidental glyph, optional right paren), invoking {@code visitor} with each glyph
     * and its pen-position X. Centralises the advance-width and kerning bookkeeping
     * so the draw pass and bounds computation cannot drift apart.
     *
     * <p>{@code scale} multiplies every advance and paren kerning so the pen positions track
     * glyphs drawn at a scaled font size. Pass {@code 1f} for full-size accidentals and
     * {@link ElementType#GRACE_NOTE_SCALE} for grace-note accidentals.
     */
    public static void walkAccidentalGlyphs(
        SMuFLGlyph accidentalGlyph,
        boolean parenthesized,
        float startX,
        float scale,
        BiConsumer<? super SMuFLGlyph, ? super Float> visitor
    ) {
        var x = startX;

        if (parenthesized) {
            visitor.accept(SMuFLGlyph.ACCIDENTAL_PARENS_LEFT, x);
            x = advancePast(x, SMuFLGlyph.ACCIDENTAL_PARENS_LEFT, scale);
            x += scale * PAREN_LEFT_KERNING.getOrDefault(accidentalGlyph, 0f);
        }

        visitor.accept(accidentalGlyph, x);
        x = advancePast(x, accidentalGlyph, scale);

        if (parenthesized) {
            x += scale * PAREN_RIGHT_KERNING.getOrDefault(accidentalGlyph, 0f);
            visitor.accept(SMuFLGlyph.ACCIDENTAL_PARENS_RIGHT, x);
        }
    }

    private static float advancePast(float x, SMuFLGlyph glyph, float scale) {
        return x + scale * (float) SMuFLMetadata.advanceWidthSs(glyph);
    }

    // ==========================================================================
    // Private Helpers
    // ==========================================================================

    private static float parenthesizedAccidentalKerningSs(SMuFLGlyph accidentalGlyph) {
        return PAREN_LEFT_KERNING.getOrDefault(accidentalGlyph, 0f)
            + PAREN_RIGHT_KERNING.getOrDefault(accidentalGlyph, 0f);
    }

    /**
     * Folds the bounding boxes of a note's accidental glyphs into a single {@link AccidentalBounds},
     * combining successive (left-to-right) glyph boxes with {@code combine}.
     *
     * <p>{@code combine} selects what the result represents: {@link BBox#union} yields the full
     * accidental span, while {@link #KEEP_RIGHTMOST} yields only the glyph nearest the notehead —
     * the rightmost one, which is the right parenthesis of a parenthesized accidental — used for
     * ledger-line shortening (see {@link #getLedgerLineExtentSs}).
     */
    private static @Nullable AccidentalBounds computeAccidentalBounds(
        SMuFLGlyph accidentalGlyph,
        boolean parenthesized,
        float totalWidthSs,
        BinaryOperator<BBox> combine
    ) {
        var startX = -ACCIDENTAL_PADDING_SS - totalWidthSs;
        var accumulator = new BBox[]{null};

        // Base (non-grace) bounds: glyphs are laid out at full size.
        walkAccidentalGlyphs(accidentalGlyph, parenthesized, startX, 1f, (glyph, xSs) -> {
            var shifted = SMuFLMetadata.bboxSs(glyph).translateXSs(xSs);
            accumulator[0] = (accumulator[0] == null) ? shifted : combine.apply(accumulator[0], shifted);
        });

        var box = accumulator[0];

        if (box == null) {
            return null;
        }

        return new AccidentalBounds(box.leftSs(), box.widthSs(), box.topSs(), box.bottomSs());
    }
}
