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

import static songscribe.ui.renderer.GraphicsState.Property.*;

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
import songscribe.smufl.StaffSpaces;
import songscribe.ui.layout.LayoutStylesheet;
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
    // Constants from FughettaRenderer
    // ==========================================================================

    private static final float NOTE_FONT_SIZE = BaseElementRenderer.NOTE_FONT_SIZE;

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

    // Stem dimensions from SMuFL metadata, rounded to integer pixels for crisp rendering
    public static final double STEM_WIDTH = Math.max(1.0,
        Math.round(StaffSpaces.toPixels(
            SMuFLMetadata.getInstance().getEngravingDefaults().stemThickness())));
    private static final double STEM_LENGTH = StaffSpaces.toPixels(3.5);

    // Half the beam thickness in pixels, used to tuck beamed stems inside the beam
    // so they don't peek past the outer edge when the beam is angled.
    private static final double HALF_BEAM_THICKNESS = StaffSpaces.toPixels(
        SMuFLMetadata.getInstance().getEngravingDefaults().beamThickness()) / 2.0;

    // Cached anchor data for notehead glyphs (in pixels, Y-down screen convention)
    private static final GlyphAnchors.Anchor STEM_UP_SE_BLACK;
    private static final GlyphAnchors.Anchor STEM_DOWN_NW_BLACK;
    private static final GlyphAnchors.Anchor STEM_UP_SE_HALF;
    private static final GlyphAnchors.Anchor STEM_DOWN_NW_HALF;

    static {
        var metadata = SMuFLMetadata.getInstance();
        var blackAnchors = metadata.getAnchors(SMuFLGlyph.NOTEHEAD_BLACK);
        var halfAnchors = metadata.getAnchors(SMuFLGlyph.NOTEHEAD_HALF);

        assert blackAnchors != null && blackAnchors.stemUpSE() != null && blackAnchors.stemDownNW() != null;
        assert halfAnchors != null && halfAnchors.stemUpSE() != null && halfAnchors.stemDownNW() != null;

        STEM_UP_SE_BLACK = blackAnchors.stemUpSE();
        STEM_DOWN_NW_BLACK = blackAnchors.stemDownNW();
        STEM_UP_SE_HALF = halfAnchors.stemUpSE();
        STEM_DOWN_NW_HALF = halfAnchors.stemDownNW();
    }

    // Dot positioning (using SMuFL augmentation dot glyph)
    private static final float FIRST_DOT_X = 13.1f;
    private static final float DOT_SPACING;

    static {
        var metadata = SMuFLMetadata.getInstance();
        var advanceWidth = metadata.getAdvanceWidth(SMuFLGlyph.AUGMENTATION_DOT);
        DOT_SPACING = (advanceWidth != null) ? (float) StaffSpaces.toPixels(advanceWidth) + 2.8f : 6.6f;
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
    private static final float ACCIDENTAL_PADDING = 2.7f;
    private static final float SPACE_BETWEEN_TWO_ACCIDENTALS = 1.3f;
    private static final float GRACE_ACCIDENTAL_RESIZE_FACTOR = 0.65f;

    // Kerning adjustments for parenthesized accidentals (in pixels).
    // Positive = more space, negative = less space.
    private static final EnumMap<SMuFLGlyph, Float> PAREN_LEFT_KERNING = new EnumMap<>(SMuFLGlyph.class);
    private static final EnumMap<SMuFLGlyph, Float> PAREN_RIGHT_KERNING = new EnumMap<>(SMuFLGlyph.class);

    static {
        // Kerning between left parenthesis and following accidental glyph
        PAREN_LEFT_KERNING.put(SMuFLGlyph.ACCIDENTAL_FLAT, 1f);
        PAREN_LEFT_KERNING.put(SMuFLGlyph.ACCIDENTAL_NATURAL, 1f);
        PAREN_LEFT_KERNING.put(SMuFLGlyph.ACCIDENTAL_SHARP, 1f);
        PAREN_LEFT_KERNING.put(SMuFLGlyph.ACCIDENTAL_DOUBLE_FLAT, 1f);

        // Kerning between accidental glyph and following right parenthesis
        PAREN_RIGHT_KERNING.put(SMuFLGlyph.ACCIDENTAL_FLAT, -1f);
        PAREN_RIGHT_KERNING.put(SMuFLGlyph.ACCIDENTAL_NATURAL, 1f);
        PAREN_RIGHT_KERNING.put(SMuFLGlyph.ACCIDENTAL_SHARP, 1f);
        PAREN_RIGHT_KERNING.put(SMuFLGlyph.ACCIDENTAL_DOUBLE_FLAT, -1f);
    }

    // Cached accidental widths (computed on first use)
    private static float[] baseAccidentalWidths = null;
    private static float[] baseAccidentalParenthesisWidths = null;
    private static float beginParenthesisWidth = 0.0f;
    private static float endParenthesisWidth = 0.0f;

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
            var layoutResult = ctx.getLayoutResult();
            var noteX = (layoutResult != null) ? layoutResult.getNoteX(element) : element.getXPos();
            var noteY = calculateNoteY(element.getYPos(), ctx.getMiddleLineY());

            // Snap X to device pixel so glyph and stem rendering share
            // the same pixel-aligned origin (prevents rounding disagreements).
            g2.translate(GraphicUtils.snapXToDevicePixel(g2, noteX), noteY);
            g2.setFont(ctx.getMusicFont());

            var isBeamed = isNoteBeamed(element, ctx);
            renderNoteHead(g2, element, isBeamed, ctx);
            renderLedgerLines(g2, element);
            renderAccidental(g2, element, ctx);
        }
    }

    /**
     * Renders a note with the simple method signature for backward compatibility.
     */
    public void render(
        @NotNull Graphics2D g2,
        @NotNull Note note,
        int middleLineY
    ) {
        // Create a minimal context for backward compatibility
        var noteType = note.getNoteType();

        if (noteType.isRest() || noteType.isBarLine() || noteType.isRepeat()) {
            // Delegate to appropriate renderer
            try (var ignored = GraphicsState.save(g2, TRANSFORM)) {
                var noteX = note.getXPos();
                var noteY = calculateNoteY(note.getYPos(), middleLineY);

                g2.translate(noteX, noteY);

                if (noteType.isRest()) {
                    renderRestGlyph(g2, noteType);
                } else {
                    renderBarLineOrRepeat(g2, noteType);
                }
            }
            return;
        }

        if (noteType == NoteType.BREATH_MARK) {
            var breathY = middleLineY - StaffSpaces.toPixels(2.5);
            drawBravuraGlyph(g2, SMuFLGlyph.BREATH_MARK_COMMA, note.getXPos(), Math.round(breathY), true);
            return;
        }

        if (noteType.isGraceNote()) {
            GraceNoteRenderer.getInstance().render(g2, note, middleLineY);
            return;
        }

        // Standard note rendering
        // Note: Don't set color here - respect the color set by the caller
        // (e.g., blue for edit notes, black for composition notes)
        try (var ignored = GraphicsState.save(g2, TRANSFORM, FONT)) {
            var noteX = note.getXPos();
            var noteY = calculateNoteY(note.getYPos(), middleLineY);

            g2.translate(noteX, noteY);
            g2.setFont(BaseElementRenderer.MUSIC_FONT);

            renderNoteHeadSimple(g2, note, false);
            renderLedgerLines(g2, note);
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
        var layoutResult = ctx.getLayoutResult();
        var noteX = (layoutResult != null) ? layoutResult.getNoteX(element) : element.getXPos();

        // Place half a staff space above the top staff line
        var breathY = ctx.getMiddleLineY() - StaffSpaces.toPixels(2.5);

        drawBravuraGlyph(
            g2,
            SMuFLGlyph.BREATH_MARK_COMMA,
            GraphicUtils.snapXToDevicePixel(g2, noteX),
            Math.round(breathY),
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
        float noteHeadXPos = 0f;

        if (noteType.isNoteWithStem() && !note.isUpper()) {
            noteHeadXPos -= (float) (STEM_WIDTH / 2);
        }

        try (var ignored = GraphicsState.save(g2, FONT)) {
            g2.setFont(BRAVURA_FONT);
            g2.drawString(glyph.asString(), noteHeadXPos, 0f);
        }

        // Draw stem (always for notes with stems - beamed notes need stems to connect to beams)
        renderStem(g2, note, note.isUpper(), beamed, noteType);

        // Draw flags only for unbeamed notes (beamed notes get beams instead of flags)
        if (!beamed) {
            renderFlags(g2, note, note.isUpper(), noteType);
        }

        // Draw dots
        renderDots(g2, note, beamed, note.isUpper());
    }

    private void renderNoteHeadSimple(
        @NotNull Graphics2D g2,
        @NotNull Note note,
        boolean beamed
    ) {
        var noteType = note.getNoteType();
        var glyph = NOTE_HEAD.get(noteType);

        if (glyph == null) {
            return;
        }

        // Adjust x position for lower stem notes
        float noteHeadXPos = 0f;

        if (noteType.isNoteWithStem() && !note.isUpper()) {
            noteHeadXPos -= (float) (STEM_WIDTH / 2);
        }

        try (var ignored = GraphicsState.save(g2, FONT)) {
            g2.setFont(BRAVURA_FONT);
            g2.drawString(glyph.asString(), noteHeadXPos, 0f);
        }

        // Draw stem (always for notes with stems - beamed notes need stems to connect to beams)
        renderStem(g2, note, note.isUpper(), beamed, noteType);

        // Draw flags only for unbeamed notes (beamed notes get beams instead of flags)
        if (!beamed) {
            renderFlags(g2, note, note.isUpper(), noteType);
        }

        // Draw dots
        renderDots(g2, note, beamed, note.isUpper());
    }

    // ==========================================================================
    // Stem Rendering
    // ==========================================================================

    private void renderStem(
        @NotNull Graphics2D g2,
        @NotNull Note note,
        boolean upper,
        boolean beamed,
        @NotNull NoteType noteType
    ) {
        if (!noteType.isNoteWithStem()) {
            return;
        }

        boolean isMinim = noteType == NoteType.MINIM;

        // Get anchor positions in pixels from the notehead's SMuFL anchor data
        GlyphAnchors.Anchor anchor;

        if (upper) {
            anchor = isMinim ? STEM_UP_SE_HALF : STEM_UP_SE_BLACK;
        } else {
            anchor = isMinim ? STEM_DOWN_NW_HALF : STEM_DOWN_NW_BLACK;
        }

        double anchorX = StaffSpaces.toPixels(anchor.x());
        double anchorY = StaffSpaces.toPixels(anchor.y());

        // Calculate stem left edge from the anchor point.
        // stemUpSE marks where the stem's RIGHT edge meets the notehead,
        // stemDownNW marks where the stem's LEFT edge meets the notehead
        // (but the down-stem notehead is shifted left by STEM_WIDTH/2, so we
        // compensate here to keep the stem aligned with the shifted notehead).
        // stemUpSE marks the stem's right edge; stemDownNW marks the left edge
        // (but down-stem noteheads are shifted left by STEM_WIDTH/2, so we compensate).
        double stemLeftRaw = upper ? anchorX - STEM_WIDTH : anchorX - STEM_WIDTH / 2;

        // Snap to device pixel boundary for crisp rendering.
        // We must work in absolute (device) coordinates because the graphics context
        // has been translated to the note's position — rounding in local coordinates
        // won't align to actual screen pixels.
        double stemLeftX = GraphicUtils.snapXToDevicePixel(g2, stemLeftRaw);
        double stemCenterX = stemLeftX + STEM_WIDTH / 2;

        double stemLength = STEM_LENGTH + note.properties.lengthening;

        // For beamed notes, shorten the rendered stem by half the (thickened) beam
        // so it tucks inside the beam rather than peeking past the outer edge
        // when the beam is angled. stem.y2 retains the full length for beam positioning.
        double drawLength = beamed
            ? Math.max(0, stemLength - HALF_BEAM_THICKNESS - note.properties.beamThickening / 2.0)
            : stemLength;

        if (upper) {
            double stemTopY = anchorY - stemLength;

            // Store logical stem line for BeamGroupRenderer (center X, actual Y endpoints)
            note.properties.stem.setLine(stemCenterX, anchorY, stemCenterX, stemTopY);

            // Draw filled rectangle for crisp rendering
            g2.fill(new Rectangle2D.Double(
                stemLeftX, anchorY - drawLength, STEM_WIDTH, drawLength));
        } else {
            double stemBottomY = anchorY + stemLength;

            note.properties.stem.setLine(stemCenterX, anchorY, stemCenterX, stemBottomY);

            g2.fill(new Rectangle2D.Double(
                stemLeftX, anchorY, STEM_WIDTH, drawLength));
        }
    }

    // ==========================================================================
    // Flag Rendering
    // ==========================================================================

    private void renderFlags(
        @NotNull Graphics2D g2,
        @NotNull Note note,
        boolean upper,
        @NotNull NoteType noteType
    ) {
        if (!noteType.isBeamable()) {
            return;
        }

        var flagGlyph = getFlagGlyph(noteType, upper);

        if (flagGlyph == null) {
            return;
        }

        // Position flag at the stem tip. SMuFL flag glyphs have their origin
        // at the left edge of the stem, so use stem center minus half width.
        var stem = note.properties.stem;
        float flagX = (float) (stem.getX1() - STEM_WIDTH / 2);
        float flagY = (float) stem.getY2();

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

            // Adjust for notes on lines (move dot off the line)
            if ((note.getYPos() % 2) == 0) {
                g2.translate(0, -NOTE_FONT_SIZE / 8);
            }

            // Adjust for wider note heads
            if (noteType == NoteType.SEMIBREVE) {
                g2.translate(3.5, 0);
            }

            if (noteType == NoteType.MINIM) {
                g2.translate(1.4, 0);
            }

            // Adjust for flags on unbeamed notes
            if (noteType.isBeamable() && !beamed && upper) {
                g2.translate((noteType == NoteType.QUAVER) ? 5 : 8, 0);
            }

            // Draw augmentation dots using SMuFL glyph
            try (var ignored2 = GraphicsState.save(g2, FONT)) {
                g2.setFont(BRAVURA_FONT);
                var dotX = FIRST_DOT_X;
                for (int i = 0; i < note.getDotCount(); i++) {
                    g2.drawString(SMuFLGlyph.AUGMENTATION_DOT.asString(), dotX, 0f);
                    dotX += DOT_SPACING;
                }
            }
        }
    }

    // ==========================================================================
    // Ledger Line Rendering
    // ==========================================================================

    private void renderLedgerLines(@NotNull Graphics2D g2, @NotNull Note note) {
        var noteType = note.getNoteType();

        if (Math.abs(note.getYPos()) <= 5 || !noteType.drawStaveLongitude()) {
            return;
        }

        try (var ignored = GraphicsState.save(g2, STROKE)) {
            g2.setStroke(LINE_STROKE);

            int yPos = note.getYPos();

            // Adjust to start from a line position
            int i = yPos;

            if ((yPos % 2) != 0) {
                i += (yPos > 0) ? -1 : 1;
            }

            int step = (yPos > 0) ? -2 : 2;

            while (Math.abs(i) > 5) {
                float y = ((i - yPos) * NOTE_FONT_SIZE) / 8;
                float x2 = Note.HOT_SPOT.x + 8f;

                if (noteType == NoteType.SEMIBREVE) {
                    x2 += 3.4f;
                } else if (noteType == NoteType.MINIM) {
                    x2 += 0.7f;
                }

                g2.draw(new Line2D.Float(Note.HOT_SPOT.x - 8, y, x2, y));
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

            float accidentalWidth = getAccidentalWidth(note);
            float x = (-ACCIDENTAL_PADDING - accidentalWidth) * resizeFactor;

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
                x += SPACE_BETWEEN_TWO_ACCIDENTALS;
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
        return x + (advanceWidth != null ? (float) StaffSpaces.toPixels(advanceWidth) : 0f);
    }

    /**
     * Returns the total parenthesis kerning adjustment for an accidental.
     * This is the sum of left-paren kerning (based on first component)
     * and right-paren kerning (based on last component).
     */
    private static float parenthesizedAccidentalKerning(int accidentalOrdinal) {
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

    private int calculateNoteY(int yPos, int middleLineY) {
        return middleLineY + (int) (yPos * LayoutStylesheet.NOTE_Y_OFFSET);
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
     * This must be called once before using getAccidentalWidth() or getAccidentalComponentWidth().
     */
    public static void initializeAccidentalWidths(@NotNull Graphics2D g2) {
        if (baseAccidentalWidths != null) {
            return;
        }

        var metadata = SMuFLMetadata.getInstance();
        baseAccidentalWidths = new float[ACCIDENTAL_COMPONENTS.length];

        for (var i = 0; i < ACCIDENTAL_COMPONENTS.length; i++) {
            var components = ACCIDENTAL_COMPONENTS[i];
            float width = 0f;

            for (var c = 0; c < components.length; c++) {
                if (c > 0) {
                    width += SPACE_BETWEEN_TWO_ACCIDENTALS;
                }

                var aw = metadata.getAdvanceWidth(components[c]);
                width += (aw != null) ? (float) StaffSpaces.toPixels(aw) : 0f;
            }

            baseAccidentalWidths[i] = width;
        }

        // Calculate parenthesis widths
        var parensLeftWidth = metadata.getAdvanceWidth(SMuFLGlyph.ACCIDENTAL_PARENS_LEFT);
        var parensRightWidth = metadata.getAdvanceWidth(SMuFLGlyph.ACCIDENTAL_PARENS_RIGHT);
        beginParenthesisWidth = (parensLeftWidth != null) ? (float) StaffSpaces.toPixels(parensLeftWidth) : 0f;
        endParenthesisWidth = (parensRightWidth != null) ? (float) StaffSpaces.toPixels(parensRightWidth) : 0f;

        // Parenthesized width = parens left + accidental components + parens right
        baseAccidentalParenthesisWidths = new float[ACCIDENTAL_COMPONENTS.length];

        for (var i = 0; i < baseAccidentalParenthesisWidths.length; i++) {
            baseAccidentalParenthesisWidths[i] =
                baseAccidentalWidths[i] + beginParenthesisWidth + endParenthesisWidth;

            baseAccidentalParenthesisWidths[i] += parenthesizedAccidentalKerning(i);
        }
    }

    /**
     * Returns the width of the accidental for a note.
     */
    public static float getAccidentalWidth(@NotNull Note note) {
        return note.isAccidentalInParentheses()
            ? baseAccidentalParenthesisWidths[note.getAccidental().ordinal()]
            : baseAccidentalWidths[note.getAccidental().ordinal()];
    }

    /**
     * Returns the width of a specific accidental component.
     */
    public static float getAccidentalComponentWidth(@NotNull Note note, int component) {
        if (baseAccidentalWidths == null) {
            getAccidentalWidth(note);
        }

        return baseAccidentalWidths[note.getAccidental().getComponent(component) + 1];
    }

    // ==========================================================================
    // Stub implementations for bar/rest delegation
    // ==========================================================================

    private void renderRestGlyph(@NotNull Graphics2D g2, @NotNull NoteType noteType) {
        try (var ignored = GraphicsState.save(g2, FONT)) {
            g2.setFont(BRAVURA_FONT);
            var glyph = RestRenderer.getRestGlyph(noteType);

            if (glyph != null) {
                g2.drawString(glyph.asString(), 0f, 0f);
            }
        }
    }

    private void renderBarLineOrRepeat(@NotNull Graphics2D g2, @NotNull NoteType noteType) {
        BarRenderer.renderBarLineOrRepeat(g2, noteType);
    }
}
