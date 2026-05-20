package songscribe.layout;

import java.util.EnumMap;
import java.util.function.BiConsumer;

import org.jspecify.annotations.Nullable;

import songscribe.dom.AccidentalBounds;
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

    /** SMuFL standard stem length in staff-space units. */
    public static final double STEM_LENGTH_SS = 3.5;

    /** Stem length for grace notes in staff-space units. */
    public static final double GRACE_NOTE_STEM_LENGTH_SS = 2.5;

    /** Stem width in staff-space units (LilyPond multiplier-derived). */
    public static final double STEM_WIDTH_SS = LineThickness.getInstance().stemSs();

    /** Scale factor applied to grace note glyphs. */
    public static final float GRACE_NOTE_SCALE = 0.75f;

    // ==========================================================================
    // Font Constants
    // ==========================================================================

    /** Bravura SMuFL music font size in staff-space units. */
    public static final float MUSIC_FONT_SIZE_SS = 4.0f;

    // ==========================================================================
    // Glissando Constants
    // ==========================================================================

    private static final double GLISSANDO_MIN_LENGTH_SS = 1.0;
    private static final double GLISSANDO_NOTEHEAD_GAP_SS = 0.3;

    /** Minimum horizontal distance between note origins required for a glissando, in staff spaces. */
    public static final double MIN_GLISSANDO_RESERVATION_SS =
        GLISSANDO_MIN_LENGTH_SS + 2 * GLISSANDO_NOTEHEAD_GAP_SS;

    /**
     * Stem anchor point for small black noteheads (stem-up, south-east corner).
     * Used for grace notes which use pre-sized small glyphs.
     */
    public static final GlyphAnchors.Anchor STEM_UP_SE_BLACK_SMALL = new GlyphAnchors.Anchor(
        Engraving.NOTEHEAD_BLACK_STEM_UP_SE.x() * GRACE_NOTE_SCALE,
        Engraving.NOTEHEAD_BLACK_STEM_UP_SE.y() * GRACE_NOTE_SCALE
    );

    // ==========================================================================
    // Notehead Fallback
    // ==========================================================================

    /** Fallback right-edge width (staff spaces) for noteheadBlack when SMuFL metadata is absent. */
    private static final double NOTEHEAD_BLACK_RIGHT_EDGE_SS = 1.18;

    // ==========================================================================
    // Accidental Constants
    // ==========================================================================

    /** Horizontal gap between notehead origin and the right edge of an accidental, in staff spaces. */
    public static final float ACCIDENTAL_PADDING_SS = 0.3375f; // 2.7px / 8 px/ss

    private static final float SPACE_BETWEEN_TWO_ACCIDENTALS_SS = 0.1625f; // 1.3px / 8 px/ss

    /** Accidental glyph components indexed by {@code Accidental.ordinal()}. */
    public static final SMuFLGlyph[][] ACCIDENTAL_COMPONENTS = {
        {SMuFLGlyph.ACCIDENTAL_NATURAL},                                // NATURAL
        {SMuFLGlyph.ACCIDENTAL_FLAT},                                   // FLAT
        {SMuFLGlyph.ACCIDENTAL_SHARP},                                  // SHARP
        {SMuFLGlyph.ACCIDENTAL_NATURAL, SMuFLGlyph.ACCIDENTAL_NATURAL}, // DOUBLE_NATURAL
        {SMuFLGlyph.ACCIDENTAL_DOUBLE_FLAT},                            // DOUBLE_FLAT
        {SMuFLGlyph.ACCIDENTAL_DOUBLE_SHARP},                           // DOUBLE_SHARP
        {SMuFLGlyph.ACCIDENTAL_NATURAL, SMuFLGlyph.ACCIDENTAL_FLAT},    // NATURAL_FLAT
        {SMuFLGlyph.ACCIDENTAL_NATURAL, SMuFLGlyph.ACCIDENTAL_SHARP},   // NATURAL_SHARP
    };

    /** Small accidental glyph components for grace notes (pre-sized, no scaling needed). */
    public static final SMuFLGlyph[][] ACCIDENTAL_COMPONENTS_SMALL = {
        {SMuFLGlyph.ACCIDENTAL_NATURAL_SMALL},                                           // NATURAL
        {SMuFLGlyph.ACCIDENTAL_FLAT_SMALL},                                              // FLAT
        {SMuFLGlyph.ACCIDENTAL_SHARP_SMALL},                                             // SHARP
        {SMuFLGlyph.ACCIDENTAL_NATURAL_SMALL, SMuFLGlyph.ACCIDENTAL_NATURAL_SMALL},      // DOUBLE_NATURAL
        {SMuFLGlyph.ACCIDENTAL_DOUBLE_FLAT},                                             // DOUBLE_FLAT
        {SMuFLGlyph.ACCIDENTAL_DOUBLE_SHARP},                                            // DOUBLE_SHARP
        {SMuFLGlyph.ACCIDENTAL_NATURAL_SMALL, SMuFLGlyph.ACCIDENTAL_FLAT_SMALL},         // NATURAL_FLAT
        {SMuFLGlyph.ACCIDENTAL_NATURAL_SMALL, SMuFLGlyph.ACCIDENTAL_SHARP_SMALL},        // NATURAL_SHARP
    };

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
    private static float @Nullable [] smallAccidentalWidthsSs = null;

    // Cached accidental bounding boxes, indexed by Accidental.ordinal().
    private static @Nullable AccidentalBounds @Nullable [] baseAccidentalBoundsSs = null;
    private static @Nullable AccidentalBounds @Nullable [] baseAccidentalParenthesisBoundsSs = null;

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
        smallAccidentalWidthsSs = computeComponentWidths(ACCIDENTAL_COMPONENTS_SMALL);

        var parensLeftWidth = SMuFLMetadata.getAdvanceWidth(SMuFLGlyph.ACCIDENTAL_PARENS_LEFT);
        var parensRightWidth = SMuFLMetadata.getAdvanceWidth(SMuFLGlyph.ACCIDENTAL_PARENS_RIGHT);
        var beginParenthesisWidthSs = (parensLeftWidth != null) ? parensLeftWidth.floatValue() : 0f;
        var endParenthesisWidthSs = (parensRightWidth != null) ? parensRightWidth.floatValue() : 0f;

        baseAccidentalParenthesisWidthsSs = new float[ACCIDENTAL_COMPONENTS.length];

        for (var i = 0; i < baseAccidentalParenthesisWidthsSs.length; i++) {
            baseAccidentalParenthesisWidthsSs[i] =
                baseAccidentalWidthsSs[i] + beginParenthesisWidthSs + endParenthesisWidthSs;
            baseAccidentalParenthesisWidthsSs[i] += parenthesizedAccidentalKerningSs(i);
        }

        baseAccidentalBoundsSs = new AccidentalBounds[ACCIDENTAL_COMPONENTS.length];
        baseAccidentalParenthesisBoundsSs = new AccidentalBounds[ACCIDENTAL_COMPONENTS.length];

        for (var i = 0; i < ACCIDENTAL_COMPONENTS.length; i++) {
            baseAccidentalBoundsSs[i] = computeAccidentalBounds(
                ACCIDENTAL_COMPONENTS[i], false, baseAccidentalWidthsSs[i]);
            baseAccidentalParenthesisBoundsSs[i] = computeAccidentalBounds(
                ACCIDENTAL_COMPONENTS[i], true, baseAccidentalParenthesisWidthsSs[i]);
        }
    }

    // ==========================================================================
    // Accidental Geometry
    // ==========================================================================

    /**
     * Returns the width of the accidental for a note.
     * Grace notes use pre-sized small accidental glyphs.
     */
    public static float getAccidentalWidthSs(StaffElement note) {
        var accidental = note.getAccidental();

        if (accidental == null) {
            return 0;
        }

        var ordinal = accidental.ordinal();

        if (note.getType().isGraceNote()) {
            var widths = smallAccidentalWidthsSs;

            if (widths == null) {
                throw RuntimeError.exit("getAccidentalWidthSs() called before initializeAccidentalWidths()");
            }

            return widths[ordinal];
        }

        var baseWidths = baseAccidentalWidthsSs;
        var parenWidths = baseAccidentalParenthesisWidthsSs;

        if (baseWidths == null || parenWidths == null) {
            throw RuntimeError.exit("getAccidentalWidthSs() called before initializeAccidentalWidths()");
        }

        return note.isAccidentalInParentheses()
            ? parenWidths[ordinal]
            : baseWidths[ordinal];
    }

    /**
     * Returns the bounding box of the accidental drawn for a note, in staff-space units.
     *
     * <p>Horizontal coordinates are relative to the notehead glyph origin (x = 0).
     * Vertical coordinates are relative to the note centre (y = 0), using Y-down convention.
     *
     * <p>Returns {@code null} when the note has no accidental or is a grace note
     * (grace-note accidentals use a different scale and are handled separately).
     */
    public static @Nullable AccidentalBounds getAccidentalBoundsSs(StaffElement note) {
        var accidental = note.getAccidental();

        if (accidental == null) {
            return null;
        }

        if (note.getType().isGraceNote()) {
            return null;
        }

        var table = note.isAccidentalInParentheses()
            ? baseAccidentalParenthesisBoundsSs
            : baseAccidentalBoundsSs;

        if (table == null) {
            throw RuntimeError.exit("getAccidentalBoundsSs() called before initializeAccidentalWidths()");
        }

        return table[accidental.ordinal()];
    }

    /** Returns the width of a specific accidental component. */
    public static float getAccidentalComponentWidthSs(StaffElement note, int component) {
        var widths = baseAccidentalWidthsSs;

        if (widths == null) {
            throw RuntimeError.exit("getAccidentalComponentWidthSs() called before initializeAccidentalWidths()");
        }

        var accidental = note.getAccidental();

        if (accidental == null) {
            return 0;
        }

        return widths[accidental.getComponent(component) + 1];
    }

    // ==========================================================================
    // Notehead / Ledger Line Geometry
    // ==========================================================================

    /** Returns the ledger line overhang in staff spaces, or 0 if the note needs no ledger lines. */
    public static double getLedgerLineOverhangSs(StaffElement note) {
        if (Math.abs(note.getStaffPosition()) <= 5 || !note.getType().drawStaveLongitude()) {
            return 0.0;
        }

        return Engraving.LEDGER_LINE_EXTENSION_SS;
    }

    /**
     * Returns the X offset of the notehead origin relative to the note's column X,
     * in staff spaces. Stem-down notes shift left by half a stem width.
     */
    public static float getNoteheadXOffsetSs(ElementType noteType, boolean upper) {
        if (noteType.isNoteWithStem() && !upper) {
            return (float) -(STEM_WIDTH_SS / 2);
        }

        return 0f;
    }

    /**
     * Returns the right edge of the notehead bounding box in staff spaces, relative to note X.
     *
     * <p>The value is read from bravura_metadata.json via SMuFLMetadata. For grace notes this
     * returns the noteheadBlackSmall bbox, which is already at the correct size.
     */
    public static double getNoteheadRightEdgeSs(StaffElement note) {
        var glyph = note.getType().getSMuFLGlyph();

        if (glyph != null) {
            var bbox = SMuFLMetadata.getBBox(glyph);

            if (bbox != null) {
                return bbox.right();
            }
        }

        return NOTEHEAD_BLACK_RIGHT_EDGE_SS;
    }

    // ==========================================================================
    // Helpers (also used by NoteRenderer for drawing)
    // ==========================================================================

    /**
     * Walks the glyph sequence that renders an accidental (optional left paren,
     * components, optional right paren), invoking {@code visitor} with each glyph
     * and its pen-position X. Centralises the advance-width and kerning bookkeeping
     * so the draw pass and bounds computation cannot drift apart.
     */
    public static void walkAccidentalGlyphs(
        SMuFLGlyph[] components,
        boolean parenthesized,
        float startX,
        BiConsumer<SMuFLGlyph, Float> visitor
    ) {
        var x = startX;

        if (parenthesized) {
            visitor.accept(SMuFLGlyph.ACCIDENTAL_PARENS_LEFT, x);
            x = advancePast(x, SMuFLGlyph.ACCIDENTAL_PARENS_LEFT);
            x += PAREN_LEFT_KERNING.getOrDefault(components[0], 0f);
        }

        for (var i = 0; i < components.length; i++) {
            if (i > 0) {
                x += SPACE_BETWEEN_TWO_ACCIDENTALS_SS;
            }

            visitor.accept(components[i], x);
            x = advancePast(x, components[i]);
        }

        if (parenthesized) {
            x += PAREN_RIGHT_KERNING.getOrDefault(components[components.length - 1], 0f);
            visitor.accept(SMuFLGlyph.ACCIDENTAL_PARENS_RIGHT, x);
        }
    }

    public static float advancePast(float x, SMuFLGlyph glyph) {
        var advance = SMuFLMetadata.getAdvanceWidth(glyph);
        return x + (advance != null ? advance.floatValue() : 0f);
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

    private static @Nullable AccidentalBounds computeAccidentalBounds(
        SMuFLGlyph[] components,
        boolean parenthesized,
        float totalWidthSs
    ) {
        var startX = -ACCIDENTAL_PADDING_SS - totalWidthSs;
        var accumulator = new BBox[]{null};

        walkAccidentalGlyphs(components, parenthesized, startX, (glyph, xSs) -> {
            var bbox = SMuFLMetadata.getBBox(glyph);

            if (bbox == null) {
                return;
            }

            var shifted = bbox.translateX(xSs);
            accumulator[0] = (accumulator[0] == null) ? shifted : accumulator[0].union(shifted);
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

                var aw = SMuFLMetadata.getAdvanceWidth(components[c]);
                width += (aw != null) ? aw.floatValue() : 0f;
            }

            widths[i] = width;
        }

        return widths;
    }
}
