package songscribe.layout;

import java.util.EnumMap;
import java.util.function.BiConsumer;

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

    /** SMuFL standard stem length in staff-space units. */
    public static final double STEM_LENGTH_SS = 3.5;

    /** Stem length for grace notes in staff-space units. */
    public static final double GRACE_NOTE_STEM_LENGTH_SS = 2.5;

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

    private static final double GLISSANDO_MIN_LENGTH_SS = 1.0;

    /**
     * Layout-side gap reserved between a notehead and the glissando endpoint, per side. This is the
     * spacing-reservation counterpart of the renderer's drawn gap
     * ({@link songscribe.ui.renderer.NoteAreaBuilder#MIN_GAP_SS}); it is held slightly larger so the
     * reservation never under-shoots the gap the renderer actually draws.
     */
    private static final double GLISSANDO_NOTEHEAD_GAP_SS = 0.3;

    /**
     * Extra clearance reserved on each side of a glissando beyond the visible gap. The renderer
     * finds each endpoint by stepping inward in ~1px increments and stops one step <em>outside</em>
     * the note area, which shortens the drawn line by up to one step per side. Round-cap stroking of
     * the note area and glyph-outline slack add a little more. Without this, a tightly-spaced
     * glissando (notably one whose target carries an accidental) reserves just too little room and
     * falls below {@link #GLISSANDO_MIN_LENGTH_SS}, so it is not drawn (refs #443).
     */
    private static final double GLISSANDO_ENDPOINT_CLEARANCE_SS = 0.2;

    /** Minimum horizontal distance between note origins required for a glissando, in staff spaces. */
    public static final double MIN_GLISSANDO_RESERVATION_SS =
        GLISSANDO_MIN_LENGTH_SS + 2 * (GLISSANDO_NOTEHEAD_GAP_SS + GLISSANDO_ENDPOINT_CLEARANCE_SS);

    /**
     * Stem anchor point for small black noteheads (stem-up, south-east corner).
     * Used for grace notes which use pre-sized small glyphs.
     */
    public static final GlyphAnchors.Anchor STEM_UP_SE_BLACK_SMALL = new GlyphAnchors.Anchor(
        Engraving.NOTEHEAD_BLACK_STEM_UP_SE.x() * ElementType.GRACE_NOTE_SCALE,
        Engraving.NOTEHEAD_BLACK_STEM_UP_SE.y() * ElementType.GRACE_NOTE_SCALE
    );

    // ==========================================================================
    // Notehead Fallback
    // ==========================================================================

    /** Fallback right-edge width (staff spaces) for noteheadBlack when SMuFL metadata is absent. */
    static final double NOTEHEAD_BLACK_RIGHT_EDGE_SS = 1.18;

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
     * <p>The renderer and {@code NoteAreaBuilder} must share this so the drawn glyphs and the
     * glissando-clearance area cannot drift apart.
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
        var accidental = note.getAccidental();

        if (accidental == null) {
            return null;
        }

        // Grace-note accidentals do not reserve structural stacking space (by design).
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
            return SMuFLMetadata.requireBBox(glyph).right();
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

    private static @Nullable AccidentalBounds computeAccidentalBounds(
        SMuFLGlyph[] components,
        boolean parenthesized,
        float totalWidthSs
    ) {
        var startX = -ACCIDENTAL_PADDING_SS - totalWidthSs;
        var accumulator = new BBox[]{null};

        // Base (non-grace) bounds: glyphs are laid out at full size.
        walkAccidentalGlyphs(components, parenthesized, startX, 1f, (glyph, xSs) -> {
            var bbox = SMuFLMetadata.requireBBox(glyph);
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

                width += (float) SMuFLMetadata.getAdvanceWidthOrZero(components[c]);
            }

            widths[i] = width;
        }

        return widths;
    }
}
