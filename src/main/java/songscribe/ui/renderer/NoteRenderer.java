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
import static songscribe.ui.renderer.GraphicsState.Property.STROKE;
import static songscribe.ui.renderer.GraphicsState.Property.TRANSFORM;

import java.awt.*;
import java.awt.geom.*;
import java.util.EnumMap;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import songscribe.music.Note;
import songscribe.music.NoteType;
import songscribe.smufl.GlyphAnchors;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;
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
public class NoteRenderer extends BaseElementRenderer<Note> {

    // ==========================================================================
    // Constants
    // ==========================================================================

    // Note heads by type
    private static final EnumMap<NoteType, SMuFLGlyph> NOTE_HEAD = new EnumMap<>(NoteType.class);

    static {
        NOTE_HEAD.put(NoteType.SEMIBREVE, SMuFLGlyph.NOTEHEAD_WHOLE);
        NOTE_HEAD.put(NoteType.MINIM, SMuFLGlyph.NOTEHEAD_HALF);
        NOTE_HEAD.put(NoteType.CROTCHET, SMuFLGlyph.NOTEHEAD_BLACK);
        NOTE_HEAD.put(NoteType.QUAVER, SMuFLGlyph.NOTEHEAD_BLACK);
        NOTE_HEAD.put(NoteType.SEMIQUAVER, SMuFLGlyph.NOTEHEAD_BLACK);
        NOTE_HEAD.put(NoteType.DEMI_SEMIQUAVER, SMuFLGlyph.NOTEHEAD_BLACK);
    }

    private static final double STEM_LENGTH_SS = 3.5; // ss

    // Half the beam thickness in ss, used to tuck beamed stems inside the beam
    // so they don't peek past the outer edge when the beam is angled.
    private static final double HALF_BEAM_THICKNESS_SS =
        SMuFLMetadata.getInstance().getEngravingDefaults().beamThickness() / 2.0;

    // Dot positioning (using SMuFL augmentation dot glyph), in staff-space units
    private static final float FIRST_DOT_X_SS = 1.6375f; // 13.1px / 8 px/ss
    private static final float DOT_SPACING_SS;

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
    private static final float ACCIDENTAL_PADDING_SS = 0.3375f; // 2.7px / 8 px/ss
    private static final float SPACE_BETWEEN_TWO_ACCIDENTALS_SS = 0.1625f; // 1.3px / 8 px/ss
    private static final float GRACE_ACCIDENTAL_RESIZE_FACTOR = 0.65f;

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
    private static float[] baseAccidentalWidthsSs = null;
    private static float[] baseAccidentalParenthesisWidthsSs = null;
    private static float beginParenthesisWidthSs = 0.0f;
    private static float endParenthesisWidthSs = 0.0f;

    private static final BasicStroke LINE_STROKE = (BasicStroke) BaseElementRenderer.LEDGER_LINE_STROKE;

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
    public static @NotNull NoteRenderer getInstance() {
        return INSTANCE;
    }

    /**
     * Returns the SMuFL glyph for a note type's head.
     */
    @Nullable
    public static SMuFLGlyph getNoteHeadGlyph(NoteType noteType) {
        return NOTE_HEAD.get(noteType);
    }

    /**
     * Returns the note head character string for a note type (Bravura codepoint).
     */
    @Nullable
    public static String getNoteHeadChar(NoteType noteType) {
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
     *   <li>Override X from context (edit note preview)</li>
     *   <li>Layout result position (laid-out composition notes)</li>
     *   <li>Note's own {@code xPos} (fallback)</li>
     * </ol>
     */
    private static double resolveNoteXSs(
        @NotNull Graphics2D g2,
        @NotNull Note note,
        @NotNull ElementRenderContext ctx
    ) {
        double noteX;

        if (ctx.hasOverrideNoteX()) {
            noteX = ctx.getOverrideNoteXSs();
        } else {
            var layoutResult = ctx.getLayoutResult();
            noteX = (layoutResult != null) ? layoutResult.getNoteXSs(note) : note.getXPosSs();
        }

        return GraphicUtils.snapXToDevicePixel(g2, noteX);
    }

    @Override
    protected void renderElement(
        @NotNull Note element,
        @NotNull Graphics2D g2,
        @NotNull ElementRenderContext ctx
    ) {
        var noteType = element.getNoteType();

        // Delegate to specialized renderers for non-note types
        if (noteType.isRest()) {
            RestRenderer.getInstance().render(element, g2, ctx);
            return;
        }

        if (noteType.isBarLine() || noteType.isRepeat()) {
            BarRenderer.getInstance().render(element, g2, ctx);
            return;
        }

        if (noteType == NoteType.BREATH_MARK) {
            renderBreathMark(element, g2, ctx);
            return;
        }

        if (noteType.isGraceNote()) {
            GraceNoteRenderer.getInstance().render(element, g2, ctx);
            return;
        }

        // Standard note rendering
        // Note: Don't set color here - respect the color set by the caller
        // (e.g., blue for edit notes, black for composition notes)
        try (var ignored = GraphicsState.save(g2, TRANSFORM, FONT)) {
            var noteX = resolveNoteXSs(g2, element, ctx);
            var noteY = calculateNoteYSs(element.getStaffPosition(), ctx.getMiddleLineYSs());

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
        @NotNull Graphics2D g2,
        @NotNull Note note,
        @NotNull ElementRenderContext ctx
    ) {
        render(note, g2, ctx);
    }

    // ==========================================================================
    // Breath Mark Rendering
    // ==========================================================================

    private void renderBreathMark(
        @NotNull Note element,
        @NotNull Graphics2D g2,
        @NotNull ElementRenderContext ctx
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
        @NotNull Graphics2D g2,
        @NotNull Note note,
        boolean beamed,
        @NotNull ElementRenderContext ctx
    ) {
        var noteType = note.getNoteType();
        var glyph = NOTE_HEAD.get(noteType);

        if (glyph == null) {
            return;
        }

        // Note: Don't set color here - respect the color set by the caller

        // Adjust x position for lower stem notes
        float noteHeadXPosSs = 0f;

        if (noteType.isNoteWithStem() && !note.isUpper()) {
            noteHeadXPosSs -= (float) (STEM_WIDTH_SS / 2);
        }

        try (var ignored = GraphicsState.save(g2, FONT)) {
            g2.setFont(BRAVURA_FONT);
            g2.drawString(glyph.asString(), noteHeadXPosSs, 0f);
        }

        // Draw stem (always for notes with stems - beamed notes need stems to connect to beams)
        var stemTip = renderStem(g2, note, note.isUpper(), beamed, noteType, ctx);

        // Draw flags only for unbeamed notes (beamed notes get beams instead of flags)
        if (!beamed) {
            renderFlags(g2, note, note.isUpper(), noteType, stemTip);
        }

        // Draw dots
        renderDots(g2, note, beamed, note.isUpper());
    }

    // ==========================================================================
    // Stem Rendering
    // ==========================================================================

    // Returns the flag attachment point (x = stem left edge, y = stem tip), or null if no stem.
    @Nullable
    private Point2D.Double renderStem(
        @NotNull Graphics2D g2,
        @NotNull Note note,
        boolean upper,
        boolean beamed,
        @NotNull NoteType noteType,
        @NotNull ElementRenderContext ctx
    ) {
        if (!noteType.isNoteWithStem()) {
            return null;
        }

        boolean isMinim = noteType == NoteType.MINIM;

        // Get anchor positions in staff-space units from the notehead's SMuFL anchor data
        GlyphAnchors.Anchor anchor;

        if (upper) {
            anchor = isMinim ? STEM_UP_SE_HALF : STEM_UP_SE_BLACK;
        } else {
            anchor = isMinim ? STEM_DOWN_NW_HALF : STEM_DOWN_NW_BLACK;
        }

        double anchorX = anchor.x();
        double anchorY = anchor.y();

        // Calculate stem left edge from the anchor point.
        // stemUpSE marks where the stem's RIGHT edge meets the notehead,
        // stemDownNW marks where the stem's LEFT edge meets the notehead
        // (but the down-stem notehead is shifted left by STEM_WIDTH_SS/2, so we
        // compensate here to keep the stem aligned with the shifted notehead).
        double stemLeftRaw = upper ? anchorX - STEM_WIDTH_SS : anchorX - STEM_WIDTH_SS / 2;

        // Snap to device pixel boundary for crisp rendering.
        // We must work in absolute (device) coordinates because the graphics context
        // has been translated to the note's position — rounding in local coordinates
        // won't align to actual screen pixels.
        double stemLeftX = GraphicUtils.snapXToDevicePixel(g2, stemLeftRaw);
        double stemCenterX = stemLeftX + STEM_WIDTH_SS / 2;

        var layoutResult = ctx.getLayoutResult();
        var stemLayout = (layoutResult != null) ? layoutResult.getStemLayout(note) : null;
        double lengtheningSs = (stemLayout != null) ? stemLayout.lengtheningSs() : 0.0;

        double beamThickeningSs = 0.0;

        if (beamed && layoutResult != null) {
            var line = ctx.getCurrentLine();

            if (line != null) {
                var interval = line.getBeamings().findInterval(line.getNoteIndex(note));

                if (interval != null) {
                    var beamLayout = layoutResult.getBeamLayout(interval);

                    if (beamLayout != null) {
                        beamThickeningSs = beamLayout.thickeningSs();
                    }
                }
            }
        }

        double stemLength = STEM_LENGTH_SS + lengtheningSs;

        // For beamed notes, shorten the rendered stem by half the (thickened) beam
        // so it tucks inside the beam rather than peeking past the outer edge
        // when the beam is angled. stem.y2 retains the full length for beam positioning.
        double drawLength = beamed
            ? Math.max(0, stemLength - HALF_BEAM_THICKNESS_SS - beamThickeningSs / 2.0)
            : stemLength;

        if (upper) {
            double stemTopY = anchorY - stemLength;

            // Draw filled rectangle for crisp rendering
            g2.fill(new Rectangle2D.Double(
                stemLeftX, anchorY - drawLength, STEM_WIDTH_SS, drawLength));

            return new Point2D.Double(stemLeftX, stemTopY);
        } else {
            double stemBottomY = anchorY + stemLength;

            g2.fill(new Rectangle2D.Double(
                stemLeftX, anchorY, STEM_WIDTH_SS, drawLength));

            return new Point2D.Double(stemLeftX, stemBottomY);
        }
    }

    // ==========================================================================
    // Flag Rendering
    // ==========================================================================

    private void renderFlags(
        @NotNull Graphics2D g2,
        @NotNull Note note,
        boolean upper,
        @NotNull NoteType noteType,
        @Nullable Point2D.Double stemTip
    ) {
        if (!noteType.isBeamable() || stemTip == null) {
            return;
        }

        var flagGlyph = getFlagGlyph(noteType, upper);

        if (flagGlyph == null) {
            return;
        }

        // Position flag at the stem tip. SMuFL flag glyphs have their origin
        // at the left edge of the stem, so stemTip.x is already the left edge.
        float flagX = (float) stemTip.x;
        float flagY = (float) stemTip.y;

        try (var ignored = GraphicsState.save(g2, FONT)) {
            g2.setFont(BRAVURA_FONT);
            g2.drawString(flagGlyph.asString(), flagX, flagY);
        }
    }

    @Nullable
    private static SMuFLGlyph getFlagGlyph(@NotNull NoteType noteType, boolean upper) {
        return switch (noteType) {
            case QUAVER -> upper ? SMuFLGlyph.FLAG_8TH_UP : SMuFLGlyph.FLAG_8TH_DOWN;
            case SEMIQUAVER -> upper ? SMuFLGlyph.FLAG_16TH_UP : SMuFLGlyph.FLAG_16TH_DOWN;
            case DEMI_SEMIQUAVER -> upper ? SMuFLGlyph.FLAG_32ND_UP : SMuFLGlyph.FLAG_32ND_DOWN;
            default -> null;
        };
    }

    // ==========================================================================
    // Dot Rendering
    // ==========================================================================

    private void renderDots(
        @NotNull Graphics2D g2,
        @NotNull Note note,
        boolean beamed,
        boolean upper
    ) {
        if (note.getDotCount() == 0) {
            return;
        }

        try (var ignored = GraphicsState.save(g2, TRANSFORM)) {
            var noteType = note.getNoteType();

            // Adjust for notes on lines (move dot off the line by half a staff space)
            if ((note.getStaffPosition() % 2) == 0) {
                g2.translate(0, -0.5);
            }

            // Adjust for wider note heads
            if (noteType == NoteType.SEMIBREVE) {
                g2.translate(0.4375, 0); // 3.5px / 8 px/ss
            }

            if (noteType == NoteType.MINIM) {
                g2.translate(0.175, 0); // 1.4px / 8 px/ss
            }

            // Adjust for flags on unbeamed notes
            if (noteType.isBeamable() && !beamed && upper) {
                g2.translate((noteType == NoteType.QUAVER) ? 0.625 : 1.0, 0);
            }

            // Draw augmentation dots using SMuFL glyph
            try (var ignored2 = GraphicsState.save(g2, FONT)) {
                g2.setFont(BRAVURA_FONT);
                var dotX = FIRST_DOT_X_SS;
                for (int i = 0; i < note.getDotCount(); i++) {
                    g2.drawString(SMuFLGlyph.AUGMENTATION_DOT.asString(), dotX, 0f);
                    dotX += DOT_SPACING_SS;
                }
            }
        }
    }

    // ==========================================================================
    // Ledger Line Rendering
    // ==========================================================================

    // Ledger line extent around the notehead, in staff-space units.
    // Based on the approximate Bravura notehead center (~0.625 ss) ±1.0 ss extension.
    private static final double LEDGER_LINE_LEFT_SS = -0.375;  // (5 - 8) / 8 px→ss
    private static final double LEDGER_LINE_RIGHT_SS = 1.625;  // (5 + 8) / 8 px→ss

    private void renderLedgerLines(@NotNull Graphics2D g2, @NotNull Note note) {
        var noteType = note.getNoteType();

        if (Math.abs(note.getStaffPosition()) <= 5 || !noteType.drawStaveLongitude()) {
            return;
        }

        try (var ignored = GraphicsState.save(g2, STROKE)) {
            g2.setStroke(LINE_STROKE);

            int staffPosition = note.getStaffPosition();

            // Adjust to start from a line position
            int i = staffPosition;

            if ((staffPosition % 2) != 0) {
                i += (staffPosition > 0) ? -1 : 1;
            }

            int step = (staffPosition > 0) ? -2 : 2;

            while (Math.abs(i) > 5) {
                double y = (i - staffPosition) * 0.5; // each staff position = 0.5 ss
                double x2 = LEDGER_LINE_RIGHT_SS;

                if (noteType == NoteType.SEMIBREVE) {
                    x2 += 0.425; // 3.4px / 8 px/ss
                } else if (noteType == NoteType.MINIM) {
                    x2 += 0.0875; // 0.7px / 8 px/ss
                }

                g2.draw(new Line2D.Double(LEDGER_LINE_LEFT_SS, y, x2, y));
                i += step;
            }
        }
    }

    // ==========================================================================
    // Accidental Rendering
    // ==========================================================================

    private void renderAccidental(
        @NotNull Graphics2D g2,
        @NotNull Note note,
        @NotNull ElementRenderContext ctx
    ) {
        var accidental = note.getAccidental().ordinal();

        if (accidental == 0) {
            return;
        }

        try (var ignored = GraphicsState.save(g2, COLOR, FONT, TRANSFORM)) {
            float resizeFactor = 1f;

            if (note.getNoteType().isGraceNote()) {
                resizeFactor = GRACE_ACCIDENTAL_RESIZE_FACTOR;
            }

            g2.setFont(BRAVURA_FONT);

            float accidentalWidth = getAccidentalWidthSs(note);
            float x = (-ACCIDENTAL_PADDING_SS - accidentalWidth) * resizeFactor;

            if (resizeFactor != 1f) {
                g2.scale(resizeFactor, resizeFactor);
            }

            var components = ACCIDENTAL_COMPONENTS[accidental];

            if (note.isAccidentalInParentheses()) {
                x = drawGlyph(g2, SMuFLGlyph.ACCIDENTAL_PARENS_LEFT, x / resizeFactor);
                x += PAREN_LEFT_KERNING.getOrDefault(components[0], 0f);
            }

            x = renderAccidentalComponents(g2, accidental, x / resizeFactor);

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
        @NotNull Graphics2D g2,
        int accidental,
        float x
    ) {
        var components = ACCIDENTAL_COMPONENTS[accidental];

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
    private float drawGlyph(@NotNull Graphics2D g2, @NotNull SMuFLGlyph glyph, float x) {
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

    private double calculateNoteYSs(int staffPosition, double middleLineYSs) {
        return middleLineYSs + staffPosition * 0.5;
    }

    private boolean isNoteBeamed(@NotNull Note note, @NotNull ElementRenderContext ctx) {
        var line = ctx.getCurrentLine();

        if (line == null) {
            return false;
        }

        int noteIndex = line.getNoteIndex(note);
        return line.getBeamings().findInterval(noteIndex) != null &&
            note.getNoteType() != NoteType.GRACE_QUAVER;
    }

    // ==========================================================================
    // Accidental Width Calculation
    // ==========================================================================

    /**
     * Initializes the cached accidental widths from SMuFL metadata advance widths.
     * This must be called once before using getAccidentalWidthSs() or getAccidentalComponentWidthSs().
     */
    public static void initializeAccidentalWidths(@NotNull Graphics2D g2) {
        if (baseAccidentalWidthsSs != null) {
            return;
        }

        var metadata = SMuFLMetadata.getInstance();
        baseAccidentalWidthsSs = new float[ACCIDENTAL_COMPONENTS.length];

        for (var i = 0; i < ACCIDENTAL_COMPONENTS.length; i++) {
            var components = ACCIDENTAL_COMPONENTS[i];
            float width = 0f;

            for (var c = 0; c < components.length; c++) {
                if (c > 0) {
                    width += SPACE_BETWEEN_TWO_ACCIDENTALS_SS;
                }

                var aw = metadata.getAdvanceWidth(components[c]);
                width += (aw != null) ? aw.floatValue() : 0f;
            }

            baseAccidentalWidthsSs[i] = width;
        }

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

    /**
     * Returns the width of the accidental for a note.
     */
    public static float getAccidentalWidthSs(@NotNull Note note) {
        return note.isAccidentalInParentheses()
            ? baseAccidentalParenthesisWidthsSs[note.getAccidental().ordinal()]
            : baseAccidentalWidthsSs[note.getAccidental().ordinal()];
    }

    /**
     * Returns the width of a specific accidental component.
     */
    public static float getAccidentalComponentWidthSs(@NotNull Note note, int component) {
        if (baseAccidentalWidthsSs == null) {
            getAccidentalWidthSs(note);
        }

        return baseAccidentalWidthsSs[note.getAccidental().getComponent(component) + 1];
    }

}
