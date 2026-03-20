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
import static songscribe.ui.renderer.GraphicsState.Property.FONT;
import static songscribe.ui.renderer.GraphicsState.Property.TRANSFORM;

import module java.desktop;

import java.util.EnumMap;
import java.util.function.BiConsumer;

import org.jspecify.annotations.Nullable;

import songscribe.music.ElementType;
import songscribe.music.StaffElement;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;
import songscribe.ui.layout.LayoutConstants;
import songscribe.error.RuntimeError;
import songscribe.util.GraphicUtils;

/**
 * Renders notes (head, stem, flags, dots, accidentals, ledger lines).
 * <p>
 * Extracts rendering logic from FughettaRenderer for the new ElementRenderer system.
 * This renderer handles:
 * <ul>
 *   <li>Note heads (whole, half, filled)</li>
 *   <li>Stems (up or down based on note.isUpper())</li>
 *   <li>Flags (for non-beamed 8th, 16th, 32nd notes)</li>
 *   <li>Dots (for dotted notes)</li>
 *   <li>Accidentals (sharps, flats, naturals)</li>
 *   <li>Ledger lines (for notes above/below staff)</li>
 * </ul>
 */
public class NoteRenderer extends BaseElementRenderer<StaffElement> {

    // ==========================================================================
    // Constants
    // ==========================================================================

    // Note heads by type
    private static final EnumMap<ElementType, SMuFLGlyph> NOTE_HEAD = new EnumMap<>(ElementType.class);

    static {
        NOTE_HEAD.put(ElementType.SEMIBREVE, SMuFLGlyph.NOTEHEAD_WHOLE);
        NOTE_HEAD.put(ElementType.MINIM, SMuFLGlyph.NOTEHEAD_HALF);
        NOTE_HEAD.put(ElementType.CROTCHET, SMuFLGlyph.NOTEHEAD_BLACK);
        NOTE_HEAD.put(ElementType.QUAVER, SMuFLGlyph.NOTEHEAD_BLACK);
        NOTE_HEAD.put(ElementType.SEMIQUAVER, SMuFLGlyph.NOTEHEAD_BLACK);
        NOTE_HEAD.put(ElementType.DEMI_SEMIQUAVER, SMuFLGlyph.NOTEHEAD_BLACK);
        NOTE_HEAD.put(ElementType.GRACE_QUAVER, SMuFLGlyph.NOTEHEAD_BLACK);
    }

    private static final SMuFLMetadata METADATA = SMuFLMetadata.getInstance();

    // Half the beam thickness in ss, used to tuck beamed stems inside the beam
    // so they don't peek past the outer edge when the beam is angled.
    private static final double HALF_BEAM_THICKNESS_SS =
        METADATA.getEngravingDefaults().beamThickness() / 2.0;

    // Dot positioning (using SMuFL augmentation dot glyph), in staff-space units
    static final float FIRST_DOT_X_SS = 1.6375f; // 13.1px / 8 px/ss
    static final float DOT_SPACING_SS;

    static {
        var metadata = SMuFLMetadata.getInstance();
        var advanceWidth = metadata.getAdvanceWidth(SMuFLGlyph.AUGMENTATION_DOT);
        DOT_SPACING_SS = (advanceWidth != null) ? advanceWidth.floatValue() + 0.35f : 0.825f;
    }

    // Accidental glyph components indexed by Accidental.ordinal()
    private static final SMuFLGlyph[][] ACCIDENTAL_COMPONENTS = {
        {},                                                              // NONE
        {SMuFLGlyph.ACCIDENTAL_NATURAL},                                // NATURAL
        {SMuFLGlyph.ACCIDENTAL_FLAT},                                   // FLAT
        {SMuFLGlyph.ACCIDENTAL_SHARP},                                  // SHARP
        {SMuFLGlyph.ACCIDENTAL_NATURAL, SMuFLGlyph.ACCIDENTAL_NATURAL}, // DOUBLE_NATURAL
        {SMuFLGlyph.ACCIDENTAL_DOUBLE_FLAT},                            // DOUBLE_FLAT
        {SMuFLGlyph.ACCIDENTAL_DOUBLE_SHARP},                           // DOUBLE_SHARP
        {SMuFLGlyph.ACCIDENTAL_NATURAL, SMuFLGlyph.ACCIDENTAL_FLAT},    // NATURAL_FLAT
        {SMuFLGlyph.ACCIDENTAL_NATURAL, SMuFLGlyph.ACCIDENTAL_SHARP},   // NATURAL_SHARP
    };

    // Small accidental glyph components for grace notes (pre-sized, no scaling needed).
    // Uses small variants where available; falls back to regular glyphs for compound accidentals.
    private static final SMuFLGlyph[][] ACCIDENTAL_COMPONENTS_SMALL = {
        {},                                                                        // NONE
        {SMuFLGlyph.ACCIDENTAL_NATURAL_SMALL},                                    // NATURAL
        {SMuFLGlyph.ACCIDENTAL_FLAT_SMALL},                                       // FLAT
        {SMuFLGlyph.ACCIDENTAL_SHARP_SMALL},                                      // SHARP
        {SMuFLGlyph.ACCIDENTAL_NATURAL_SMALL, SMuFLGlyph.ACCIDENTAL_NATURAL_SMALL}, // DOUBLE_NATURAL
        {SMuFLGlyph.ACCIDENTAL_DOUBLE_FLAT},                                      // DOUBLE_FLAT
        {SMuFLGlyph.ACCIDENTAL_DOUBLE_SHARP},                                     // DOUBLE_SHARP
        {SMuFLGlyph.ACCIDENTAL_NATURAL_SMALL, SMuFLGlyph.ACCIDENTAL_FLAT_SMALL},  // NATURAL_FLAT
        {SMuFLGlyph.ACCIDENTAL_NATURAL_SMALL, SMuFLGlyph.ACCIDENTAL_SHARP_SMALL}, // NATURAL_SHARP
    };
    static final float ACCIDENTAL_PADDING_SS = 0.3375f; // 2.7px / 8 px/ss
    private static final float SPACE_BETWEEN_TWO_ACCIDENTALS_SS = 0.1625f; // 1.3px / 8 px/ss

    // Kerning adjustments for parenthesized accidentals (in staff-space units).
    // Positive = more space, negative = less space.
    private static final EnumMap<SMuFLGlyph, Float> PAREN_LEFT_KERNING = new EnumMap<>(SMuFLGlyph.class);
    private static final EnumMap<SMuFLGlyph, Float> PAREN_RIGHT_KERNING = new EnumMap<>(SMuFLGlyph.class);

    static {
        // Kerning between left parenthesis and following accidental glyph
        PAREN_LEFT_KERNING.put(SMuFLGlyph.ACCIDENTAL_FLAT, 0.125f);
        PAREN_LEFT_KERNING.put(SMuFLGlyph.ACCIDENTAL_NATURAL, 0.125f);
        PAREN_LEFT_KERNING.put(SMuFLGlyph.ACCIDENTAL_SHARP, 0.125f);
        PAREN_LEFT_KERNING.put(SMuFLGlyph.ACCIDENTAL_DOUBLE_FLAT, 0.125f);

        // Kerning between accidental glyph and following right parenthesis
        PAREN_RIGHT_KERNING.put(SMuFLGlyph.ACCIDENTAL_FLAT, -0.125f);
        PAREN_RIGHT_KERNING.put(SMuFLGlyph.ACCIDENTAL_NATURAL, 0.125f);
        PAREN_RIGHT_KERNING.put(SMuFLGlyph.ACCIDENTAL_SHARP, 0.125f);
        PAREN_RIGHT_KERNING.put(SMuFLGlyph.ACCIDENTAL_DOUBLE_FLAT, -0.125f);
    }

    // Cached accidental widths (computed on first use)
    private static float @Nullable [] baseAccidentalWidthsSs = null;
    private static float @Nullable [] baseAccidentalParenthesisWidthsSs = null;
    private static float @Nullable [] smallAccidentalWidthsSs = null;
    private static float beginParenthesisWidthSs = 0.0f;
    private static float endParenthesisWidthSs = 0.0f;

    // Singleton instance
    private static final NoteRenderer INSTANCE = new NoteRenderer();

    /**
     * Private constructor - use {@link #getInstance()}.
     */
    private NoteRenderer() {
    }

    /**
     * Returns the singleton instance.
     */
    public static NoteRenderer getInstance() {
        return INSTANCE;
    }

    /**
     * Returns the SMuFL glyph for a note type's head.
     */
    @Nullable
    public static SMuFLGlyph getNoteHeadGlyph(ElementType noteType) {
        return NOTE_HEAD.get(noteType);
    }

    /**
     * Returns the note head character string for a note type (Bravura codepoint).
     */
    @Nullable
    public static String getNoteHeadChar(ElementType noteType) {
        var glyph = NOTE_HEAD.get(noteType);
        return glyph != null ? glyph.asString() : null;
    }

    // ==========================================================================
    // Rendering
    // ==========================================================================

    /**
     * Resolves the device-pixel-snapped X coordinate for a note, using the first
     * available source in priority order:
     * <ol>
     *   <li>Override X from context (insertion note preview)</li>
     *   <li>Layout result position (laid-out composition notes)</li>
     *   <li>Note's own {@code xPos} (fallback)</li>
     * </ol>
     */
    private static double resolveNoteXSs(
        Graphics2D g2,
        StaffElement note,
        ElementRenderContext ctx
    ) {
        double noteX;

        if (ctx.hasOverrideElementX()) {
            noteX = ctx.getOverrideElementXSs();
        } else {
            var layoutResult = ctx.getLayoutResult();
            noteX = (layoutResult != null) ? layoutResult.getElementXSs(note) : note.getXPosSs();
        }

        return GraphicUtils.snapXToDevicePixel(g2, noteX);
    }

    @Override
    protected void renderElement(
        StaffElement element,
        Graphics2D g2,
        ElementRenderContext ctx
    ) {
        var noteType = element.getType();

        // Delegate to specialized renderers for non-note types
        if (noteType.isRest()) {
            RestRenderer.getInstance().render(element, g2, ctx);
            return;
        }

        if (noteType.isBarLine() || noteType.isRepeat()) {
            BarRenderer.getInstance().render(element, g2, ctx);
            return;
        }

        if (noteType == ElementType.BREATH_MARK) {
            renderBreathMark(element, g2, ctx);
            return;
        }

        // Standard note rendering (including grace notes)
        // Note: Don't set color here - respect the color set by the caller
        // (e.g., blue for insertion notes, black for composition notes)
        try (var ignored = GraphicsState.save(g2, TRANSFORM, FONT)) {
            var noteX = resolveNoteXSs(g2, element, ctx);
            var noteY = noteStaffPositionToCoordinateSs(element.getStaffPosition(), ctx.getMiddleLineYSs());

            g2.translate(noteX, noteY);
            g2.setFont(ctx.getMusicFont());

            var isBeamed = isNoteBeamed(element, ctx);
            renderNoteHead(g2, element, isBeamed, ctx);
            renderLedgerLines(g2, element);
            renderAccidental(g2, element, ctx);
        }
    }

    /**
     * Renders with full render context.
     */
    public void render(
        Graphics2D g2,
        StaffElement note,
        ElementRenderContext ctx
    ) {
        render(note, g2, ctx);
    }

    // ==========================================================================
    // Breath Mark Rendering
    // ==========================================================================

    private void renderBreathMark(
        StaffElement element,
        Graphics2D g2,
        ElementRenderContext ctx
    ) {
        var noteX = resolveNoteXSs(g2, element, ctx);

        // Place half a staff space above the top staff line
        var breathY = ctx.getMiddleLineYSs() - 2.5;

        drawBravuraGlyph(
            g2,
            SMuFLGlyph.BREATH_MARK_COMMA,
            noteX,
            breathY,
            true
        );
    }

    // ==========================================================================
    // Note Head Rendering
    // ==========================================================================

    private void renderNoteHead(
        Graphics2D g2,
        StaffElement note,
        boolean beamed,
        ElementRenderContext ctx
    ) {
        var noteType = note.getType();
        var glyph = NOTE_HEAD.get(noteType);

        if (glyph == null) {
            return;
        }

        // Grace notes always have stem up
        boolean upper = noteType.isGraceNote() || note.isUpper();

        // Note: Don't set color here - respect the color set by the caller

        // Adjust x position for lower stem notes
        float noteHeadXPosSs = getNoteheadXOffsetSs(noteType, upper);

        try (var ignored = GraphicsState.save(g2, FONT)) {
            g2.setFont(noteType.isGraceNote() ? BRAVURA_FONT_GRACE : BRAVURA_FONT);
            g2.drawString(glyph.asString(), noteHeadXPosSs, 0f);
        }

        // Draw stem (always for notes with stems - beamed notes need stems to connect to beams)
        var stemTip = renderStem(g2, note, upper, beamed, noteType, ctx);

        // Draw flags only for unbeamed notes (beamed notes get beams instead of flags)
        if (!beamed) {
            renderFlags(g2, note, upper, noteType, stemTip);
        }

        // Draw dots (grace notes don't have dots)
        if (!noteType.isGraceNote()) {
            renderDots(g2, note, beamed, upper);
        }
    }

    // ==========================================================================
    // Stem Rendering
    // ==========================================================================

    // Returns the flag attachment point (x = stem left edge, y = stem tip), or null if no stem.
    private Point2D.@Nullable Double renderStem(
        Graphics2D g2,
        StaffElement note,
        boolean upper,
        boolean beamed,
        ElementType noteType,
        ElementRenderContext ctx
    ) {
        if (!noteType.isNoteWithStem()) {
            return null;
        }

        var geom = LayoutConstants.computeBaseStemGeometry(noteType, upper);

        // Snap stem left edge to device pixel boundary for crisp rendering.
        // We must work in absolute (device) coordinates because the graphics context
        // has been translated to the note's position — rounding in local coordinates
        // won't align to actual screen pixels.
        //
        // For up-stems, snap the RIGHT edge so the stem never protrudes past
        // the notehead. For down-stems, snap the LEFT edge directly.
        double stemLeftX;

        if (upper) {
            double stemRightX = GraphicUtils.snapXToDevicePixel(g2, geom.stemLeftXSs() + LayoutConstants.STEM_WIDTH_SS);
            stemLeftX = stemRightX - LayoutConstants.STEM_WIDTH_SS;
        } else {
            stemLeftX = GraphicUtils.snapXToDevicePixel(g2, geom.stemLeftXSs());
        }

        var layoutResult = ctx.getLayoutResult();
        var stemLayout = (layoutResult != null) ? layoutResult.getStemLayout(note) : null;
        double lengtheningSs = (stemLayout != null) ? stemLayout.lengtheningSs() : 0.0;

        double beamThickeningSs = 0.0;

        if (beamed && layoutResult != null) {
            var line = ctx.getCurrentLine();

            if (line != null) {
                var interval = line.getBeamings().findInterval(line.getElementIndex(note));

                if (interval != null) {
                    var beamLayout = layoutResult.getBeamLayout(interval);

                    if (beamLayout != null) {
                        beamThickeningSs = beamLayout.thickeningSs();
                    }
                }
            }
        }

        // Stem length is measured from notehead center (y=0), not from the anchor.
        // The anchor only determines where the stem visually attaches to the notehead.
        var stemLength = geom.lengthSs() + lengtheningSs;
        var anchorY = geom.anchorYSs();

        // For beamed notes, shorten the rendered stem by half the (thickened) beam
        // so it tucks inside the beam rather than peeking past the outer edge
        // when the beam is angled. The logical stem tip retains the full length
        // for beam positioning.
        var beamInsetSs = beamed
            ? HALF_BEAM_THICKNESS_SS + beamThickeningSs / 2.0
            : 0.0;

        if (upper) {
            var stemTipY = -stemLength;

            // Snap drawn edges to device pixels for crisp rendering
            var drawTop = GraphicUtils.snapYToDevicePixel(g2, -(stemLength - beamInsetSs));
            var drawBottom = GraphicUtils.snapYToDevicePixel(g2, anchorY);

            g2.fill(new Rectangle2D.Double(
                stemLeftX, drawTop, LayoutConstants.STEM_WIDTH_SS, drawBottom - drawTop));

            return new Point2D.Double(stemLeftX, stemTipY);
        } else {
            var stemTipY = stemLength;

            // Snap drawn edges to device pixels for crisp rendering
            var drawTop = GraphicUtils.snapYToDevicePixel(g2, anchorY);
            var drawBottom = GraphicUtils.snapYToDevicePixel(g2, stemLength - beamInsetSs);

            g2.fill(new Rectangle2D.Double(
                stemLeftX, drawTop, LayoutConstants.STEM_WIDTH_SS, drawBottom - drawTop));

            return new Point2D.Double(stemLeftX, stemTipY);
        }
    }

    // ==========================================================================
    // Flag Rendering
    // ==========================================================================

    private void renderFlags(
        Graphics2D g2,
        StaffElement note,
        boolean upper,
        ElementType noteType,
        Point2D.@Nullable Double stemTip
    ) {
        if (stemTip == null) {
            return;
        }

        var flagGlyph = noteType.getFlagGlyph(upper);

        if (flagGlyph == null) {
            return;
        }

        // Position flag at the stem tip. SMuFL flag glyphs have their origin
        // at the left edge of the stem, so stemTip.x is already the left edge.
        float flagX = (float) stemTip.x;
        float flagY = (float) stemTip.y;

        Font flagFont;

        if (noteType.isGraceNote()) {
            flagFont = BRAVURA_FONT_GRACE;
            // The scaled flag glyph's internal stem connection is 65% of the full stem width.
            // Shift right to visually center the flag on the actual stem.
            flagX += (float) (LayoutConstants.STEM_WIDTH_SS * (1 - LayoutConstants.GRACE_NOTE_SCALE) / 2);
        } else {
            flagFont = BRAVURA_FONT;
        }

        try (var ignored = GraphicsState.save(g2, FONT)) {
            g2.setFont(flagFont);
            g2.drawString(flagGlyph.asString(), flagX, flagY);
        }
    }

    // ==========================================================================
    // Dot Rendering
    // ==========================================================================

    private void renderDots(
        Graphics2D g2,
        StaffElement note,
        boolean beamed,
        boolean upper
    ) {
        if (note.getDotCount() == 0) {
            return;
        }

        try (var ignored = GraphicsState.save(g2, FONT)) {
            g2.setFont(BRAVURA_FONT);
            var dotStr = SMuFLGlyph.AUGMENTATION_DOT.asString();
            forEachDotPosition(note, beamed, upper, (dotX, yOffset) ->
                g2.drawString(dotStr, dotX.floatValue(), yOffset.floatValue()));
        }
    }

    /**
     * Computes the position of each augmentation dot for a note and passes
     * (dotX, yOffset) to the consumer. Both values are in staff spaces,
     * relative to the note's glyph origin.
     */
    static void forEachDotPosition(
        StaffElement note, boolean beamed, boolean upper,
        BiConsumer<Double, Double> consumer
    ) {
        if (note.getDotCount() == 0) {
            return;
        }

        var noteType = note.getType();

        // Dots shift up by 0.5 ss when note is on a line
        double yOffset = (note.getStaffPosition() % 2 == 0) ? -0.5 : 0.0;

        // X offset adjustments for wider noteheads and flags
        double xAdjust = 0.0;

        if (noteType == ElementType.SEMIBREVE) {
            xAdjust = 0.4375;
        } else if (noteType == ElementType.MINIM) {
            xAdjust = 0.175;
        } else if (noteType.isBeamable() && !beamed && upper) {
            xAdjust = (noteType == ElementType.QUAVER) ? 0.625 : 1.0;
        }

        double dotX = FIRST_DOT_X_SS + xAdjust;

        for (int i = 0; i < note.getDotCount(); i++) {
            consumer.accept(dotX, yOffset);
            dotX += DOT_SPACING_SS;
        }
    }

    // ==========================================================================
    // Ledger Line Rendering
    // ==========================================================================

    private void renderLedgerLines(Graphics2D g2, StaffElement note) {
        double extensionSs = LayoutConstants.getLedgerLineOverhangSs(note);

        if (extensionSs == 0.0) {
            return;
        }

        double noteheadWidthSs = getNoteheadRightEdgeSs(note);
        double ledgerWidthSs = noteheadWidthSs + 2 * extensionSs;
        double centerXSs = noteheadWidthSs / 2.0;

        forEachLedgerLineYSs(note.getStaffPosition(),
            y -> drawLedgerLine(g2, centerXSs, y, ledgerWidthSs));
    }

    // ==========================================================================
    // Accidental Rendering
    // ==========================================================================

    private void renderAccidental(
        Graphics2D g2,
        StaffElement note,
        ElementRenderContext ctx
    ) {
        var accidental = note.getAccidental().ordinal();

        if (accidental == 0) {
            return;
        }

        boolean isGrace = note.getType().isGraceNote();
        var components = isGrace
            ? ACCIDENTAL_COMPONENTS_SMALL[accidental]
            : ACCIDENTAL_COMPONENTS[accidental];

        try (var ignored = GraphicsState.save(g2, COLOR, FONT)) {
            g2.setFont(BRAVURA_FONT);

            float accidentalWidth = getAccidentalWidthSs(note);
            float x = -ACCIDENTAL_PADDING_SS - accidentalWidth;

            if (note.isAccidentalInParentheses()) {
                x = drawGlyph(g2, SMuFLGlyph.ACCIDENTAL_PARENS_LEFT, x);
                x += PAREN_LEFT_KERNING.getOrDefault(components[0], 0f);
            }

            x = renderAccidentalComponents(g2, components, x);

            if (note.isAccidentalInParentheses()) {
                x += PAREN_RIGHT_KERNING.getOrDefault(components[components.length - 1], 0f);
                drawGlyph(g2, SMuFLGlyph.ACCIDENTAL_PARENS_RIGHT, x);
            }
        }
    }

    /**
     * Draws accidental component glyphs at the given X position, advancing X by each glyph's width.
     * Returns the X position after the last glyph.
     */
    private float renderAccidentalComponents(
        Graphics2D g2,
        SMuFLGlyph[] components,
        float x
    ) {
        for (var i = 0; i < components.length; i++) {
            if (i > 0) {
                x += SPACE_BETWEEN_TWO_ACCIDENTALS_SS;
            }

            x = drawGlyph(g2, components[i], x);
        }

        return x;
    }

    /**
     * Draws a single SMuFL glyph at the given X position.
     * Returns the X position advanced by the glyph's advance width.
     */
    private float drawGlyph(Graphics2D g2, SMuFLGlyph glyph, float x) {
        g2.drawString(glyph.asString(), x, 0f);
        var advanceWidth = SMuFLMetadata.getInstance().getAdvanceWidth(glyph);
        return x + (advanceWidth != null ? advanceWidth.floatValue() : 0f);
    }

    /**
     * Returns the total parenthesis kerning adjustment for an accidental.
     * This is the sum of left-paren kerning (based on first component)
     * and right-paren kerning (based on last component).
     */
    private static float parenthesizedAccidentalKerningSs(int accidentalOrdinal) {
        var components = ACCIDENTAL_COMPONENTS[accidentalOrdinal];

        if (components.length == 0) {
            return 0f;
        }

        return PAREN_LEFT_KERNING.getOrDefault(components[0], 0f)
            + PAREN_RIGHT_KERNING.getOrDefault(components[components.length - 1], 0f);
    }

    // ==========================================================================
    // Utility Methods
    // ==========================================================================

    private boolean isNoteBeamed(StaffElement note, ElementRenderContext ctx) {
        var line = ctx.getCurrentLine();

        if (line == null) {
            return false;
        }

        int noteIndex = line.getElementIndex(note);
        return line.getBeamings().findInterval(noteIndex) != null &&
            note.getType() != ElementType.GRACE_QUAVER;
    }

    // ==========================================================================
    // Accidental Width Calculation
    // ==========================================================================

    /**
     * Initializes the cached accidental widths from SMuFL metadata advance widths.
     * This must be called once before using getAccidentalWidthSs() or getAccidentalComponentWidthSs().
     */
    public static void initializeAccidentalWidths(Graphics2D g2) {
        if (baseAccidentalWidthsSs != null) {
            return;
        }

        var metadata = SMuFLMetadata.getInstance();
        baseAccidentalWidthsSs = computeComponentWidths(metadata, ACCIDENTAL_COMPONENTS);
        smallAccidentalWidthsSs = computeComponentWidths(metadata, ACCIDENTAL_COMPONENTS_SMALL);

        // Calculate parenthesis widths (advance widths are already in ss)
        var parensLeftWidth = metadata.getAdvanceWidth(SMuFLGlyph.ACCIDENTAL_PARENS_LEFT);
        var parensRightWidth = metadata.getAdvanceWidth(SMuFLGlyph.ACCIDENTAL_PARENS_RIGHT);
        beginParenthesisWidthSs = (parensLeftWidth != null) ? parensLeftWidth.floatValue() : 0f;
        endParenthesisWidthSs = (parensRightWidth != null) ? parensRightWidth.floatValue() : 0f;

        // Parenthesized width = parens left + accidental components + parens right
        baseAccidentalParenthesisWidthsSs = new float[ACCIDENTAL_COMPONENTS.length];

        for (var i = 0; i < baseAccidentalParenthesisWidthsSs.length; i++) {
            baseAccidentalParenthesisWidthsSs[i] =
                baseAccidentalWidthsSs[i] + beginParenthesisWidthSs + endParenthesisWidthSs;

            baseAccidentalParenthesisWidthsSs[i] += parenthesizedAccidentalKerningSs(i);
        }
    }

    private static float[] computeComponentWidths(
        SMuFLMetadata metadata,
        SMuFLGlyph[][] componentTable
    ) {
        var widths = new float[componentTable.length];

        for (var i = 0; i < componentTable.length; i++) {
            var components = componentTable[i];
            float width = 0f;

            for (var c = 0; c < components.length; c++) {
                if (c > 0) {
                    width += SPACE_BETWEEN_TWO_ACCIDENTALS_SS;
                }

                var aw = metadata.getAdvanceWidth(components[c]);
                width += (aw != null) ? aw.floatValue() : 0f;
            }

            widths[i] = width;
        }

        return widths;
    }

    /**
     * Returns the width of the accidental for a note.
     * Grace notes use pre-sized small accidental glyphs.
     */
    public static float getAccidentalWidthSs(StaffElement note) {
        var ordinal = note.getAccidental().ordinal();

        if (note.getType().isGraceNote()) {
            var widths = smallAccidentalWidthsSs;

            if (widths == null) {
                RuntimeError.exit("getAccidentalWidthSs() called before initializeAccidentalWidths()");
                throw new AssertionError("unreachable");
            }

            return widths[ordinal];
        }

        var baseWidths = baseAccidentalWidthsSs;
        var parenWidths = baseAccidentalParenthesisWidthsSs;

        if (baseWidths == null || parenWidths == null) {
            RuntimeError.exit("getAccidentalWidthSs() called before initializeAccidentalWidths()");
            throw new AssertionError("unreachable");
        }

        return note.isAccidentalInParentheses()
            ? parenWidths[ordinal]
            : baseWidths[ordinal];
    }

    /**
     * Returns the width of a specific accidental component.
     */
    public static float getAccidentalComponentWidthSs(StaffElement note, int component) {
        var widths = baseAccidentalWidthsSs;

        if (widths == null) {
            RuntimeError.exit("getAccidentalComponentWidthSs() called before initializeAccidentalWidths()");
            throw new AssertionError("unreachable");
        }

        return widths[note.getAccidental().getComponent(component) + 1];
    }

    /**
     * Returns the X offset applied to the notehead glyph for stem-down notes.
     * <p>
     * Stem-down notes shift the notehead left by half the stem width so the stem
     * aligns with the left edge of the notehead. This offset must be applied
     * consistently in both rendering and area construction (for glissando collision).
     *
     * @param noteType The note type
     * @param upper    Whether the stem points up
     * @return The X offset in staff spaces (negative for stem-down, 0 otherwise)
     */
    public static float getNoteheadXOffsetSs(ElementType noteType, boolean upper) {
        if (noteType.isNoteWithStem() && !upper) {
            return (float) -(LayoutConstants.STEM_WIDTH_SS / 2);
        }

        return 0f;
    }

    /**
     * Returns the right edge of the notehead bounding box in staff spaces, relative to note X.
     * <p>
     * The value is read from bravura_metadata.json via SMuFLMetadata. For grace notes this
     * returns the noteheadBlackSmall bbox, which is already at the correct size.
     *
     * @param note The note whose notehead right edge is needed
     * @return Right edge of the notehead in staff-space units (relative to note X)
     */
    public static double getNoteheadRightEdgeSs(StaffElement note) {
        var glyph = note.getType().getSMuFLGlyph();

        if (glyph != null) {
            var bbox = METADATA.getBBox(glyph);

            if (bbox != null) {
                return bbox.right();
            }
        }

        // Fallback: use a safe default (noteheadBlack right edge)
        return 1.18;
    }

}
