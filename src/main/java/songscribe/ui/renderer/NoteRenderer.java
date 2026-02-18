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
import songscribe.ui.layout.LayoutStylesheet;

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
    private static final EnumMap<NoteType, String> NOTE_HEAD = new EnumMap<>(NoteType.class);

    static {
        NOTE_HEAD.put(NoteType.SEMIBREVE, "\uf077");
        NOTE_HEAD.put(NoteType.MINIM, "\uf0cd");
        NOTE_HEAD.put(NoteType.CROTCHET, "\uf0cf");
        NOTE_HEAD.put(NoteType.QUAVER, "\uf0cf");
        NOTE_HEAD.put(NoteType.SEMIQUAVER, "\uf0cf");
        NOTE_HEAD.put(NoteType.DEMI_SEMIQUAVER, "\uf0cf");
    }

    // Stem positions and dimensions
    private static final double UPPER_CROTCHET_STEM_X = NOTE_FONT_SIZE / 3.6056337d;
    private static final double UPPER_MINIM_STEM_X = NOTE_FONT_SIZE / 3.1411042d;
    private static final Line2D.Float UPPER_STEM = new Line2D.Float(
        0f,
        -NOTE_FONT_SIZE / 32f,
        0f,
        -NOTE_FONT_SIZE / 1.1429f
    );
    private static final Line2D.Float LOWER_STEM = new Line2D.Float(
        0f,
        NOTE_FONT_SIZE / 60f,
        0f,
        NOTE_FONT_SIZE / 1.1429f
    );
    private static final double LOWER_STEM_CROTCHET_Y1_OFFSET = 0.6d;

    // Flag positions
    private static final double UPPER_FLAG_X = NOTE_FONT_SIZE / 3.6834533d;
    private static final double UPPER_FLAG_Y = -NOTE_FONT_SIZE / 1.6623377d;
    private static final double UPPER_FLAG_2_Y = -NOTE_FONT_SIZE / 1.1851852d;
    private static final double UPPER_FLAG_3_Y = -NOTE_FONT_SIZE / 0.9411765d;
    private static final double LOWER_FLAG_Y = NOTE_FONT_SIZE / 1.6d;
    private static final double LOWER_FLAG_2_Y = NOTE_FONT_SIZE / 1.1428572d;
    private static final double LOWER_FLAG_3_Y = NOTE_FONT_SIZE / 0.9078014d;
    private static final double FLAG_Y_LENGTH = 7;
    private static final float SEMIQUAVER_AND_DEMI_SEMIQUAVER_FLAG_COLLAPSE = 5f;

    // Dot dimensions
    private static final double DOT_WIDTH = NOTE_FONT_SIZE / 9.142858d;
    private static final Ellipse2D.Double[] NOTE_DOTS = new Ellipse2D.Double[]{
        new Ellipse2D.Double(13.1d, -DOT_WIDTH / 2, DOT_WIDTH, DOT_WIDTH),
        new Ellipse2D.Double(15.878d + DOT_WIDTH, -DOT_WIDTH / 2, DOT_WIDTH, DOT_WIDTH),
    };

    // Accidentals
    private static final String[] ACCIDENTALS = new String[]{
        "",           // NONE
        "\uf06e",     // NATURAL
        "\uf062",     // FLAT
        "\uf023",     // SHARP
        "\uf06e\uf06e", // DOUBLE_NATURAL
        "\uf0ba",     // DOUBLE_FLAT
        "\uf0dc",     // DOUBLE_SHARP
        "\uf06e\uf062", // NATURAL_FLAT
        "\uf06e\uf023", // NATURAL_SHARP
    };
    private static final String[] ACCIDENTAL_PARENTHESIS = new String[]{
        "",
        "\uf04e",
        "\uf041",
        "\uf061",
        "\uf06e\uf06e",
        "\uf08c",
        "\uf081",
        "\uf06e\uf062",
        "\uf06e\uf023",
    };
    private static final double MANUAL_PARENTHESIS_Y = NOTE_FONT_SIZE / 3.5068493d;
    private static final float ACCIDENTAL_PADDING = 2.7f;
    private static final float SPACE_BETWEEN_TWO_ACCIDENTALS = 1.3f;
    private static final float GRACE_ACCIDENTAL_RESIZE_FACTOR = 0.65f;

    // Cached accidental widths (computed on first use)
    private static float[] baseAccidentalWidths = null;
    private static float[] baseAccidentalParenthesisWidths = null;
    private static float beginParenthesisWidth = 0.0f;
    private static float endParenthesisWidth = 0.0f;

    // Strokes
    private static final BasicStroke STEM_STROKE_IMPL = new BasicStroke(
        1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER
    );
    private static final BasicStroke LINE_STROKE = new BasicStroke(
        1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER
    );

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
     * Returns the note head character for a note type.
     */
    @Nullable
    public static String getNoteHeadChar(NoteType noteType) {
        return NOTE_HEAD.get(noteType);
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

            g2.translate(noteX, noteY);
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
    // Note Head Rendering
    // ==========================================================================

    private void renderNoteHead(
        @NotNull Graphics2D g2,
        @NotNull Note note,
        boolean beamed,
        @NotNull ElementRenderContext ctx
    ) {
        var noteType = note.getNoteType();
        var headStr = NOTE_HEAD.get(noteType);

        if (headStr == null) {
            return;
        }

        // Note: Don't set color here - respect the color set by the caller

        // Adjust x position for lower stem notes
        float noteHeadXPos = 0f;

        if (noteType.isNoteWithStem() && !note.isUpper()) {
            noteHeadXPos -= STEM_STROKE_IMPL.getLineWidth() / 2;
        }

        g2.drawString(headStr, noteHeadXPos, 0f);

        // Draw stem (always for notes with stems - beamed notes need stems to connect to beams)
        renderStem(g2, note, note.isUpper(), false, beamed, noteType);

        // Draw flags only for unbeamed notes (beamed notes get beams instead of flags)
        if (!beamed) {
            renderFlags(g2, note.isUpper(), false, noteType);
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
        var headStr = NOTE_HEAD.get(noteType);

        if (headStr == null) {
            return;
        }

        // Adjust x position for lower stem notes
        float noteHeadXPos = 0f;

        if (noteType.isNoteWithStem() && !note.isUpper()) {
            noteHeadXPos -= STEM_STROKE_IMPL.getLineWidth() / 2;
        }

        g2.drawString(headStr, noteHeadXPos, 0f);

        // Draw stem (always for notes with stems - beamed notes need stems to connect to beams)
        renderStem(g2, note, note.isUpper(), false, beamed, noteType);

        // Draw flags only for unbeamed notes (beamed notes get beams instead of flags)
        if (!beamed) {
            renderFlags(g2, note.isUpper(), false, noteType);
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
        boolean isTempoNote,
        boolean beamed,
        @NotNull NoteType noteType
    ) {
        if (!noteType.isNoteWithStem()) {
            return;
        }

        double stemLengthOffset = 0d;
        double stemYOffset = 0d;

        if (isTempoNote) {
            stemLengthOffset = -2; // tempoStemShortening
        } else if (!beamed) {
            // Only apply flag-related stem length adjustments for unbeamed notes.
            // Beamed notes don't have flags, so they don't need the extra stem length.
            if (noteType == NoteType.SEMIQUAVER) {
                stemLengthOffset = FLAG_Y_LENGTH - SEMIQUAVER_AND_DEMI_SEMIQUAVER_FLAG_COLLAPSE;
            } else if (noteType == NoteType.DEMI_SEMIQUAVER) {
                stemLengthOffset = (2 * FLAG_Y_LENGTH) - SEMIQUAVER_AND_DEMI_SEMIQUAVER_FLAG_COLLAPSE;
            }
        }

        // Y offset adjustments apply to all notes
        if (noteType == NoteType.CROTCHET) {
            stemYOffset = LOWER_STEM_CROTCHET_Y1_OFFSET;
        } else if (noteType == NoteType.MINIM) {
            stemYOffset = 0.1d;
        }

        if (upper) {
            double stemX = (noteType == NoteType.MINIM) ? UPPER_MINIM_STEM_X : UPPER_CROTCHET_STEM_X;
            note.properties.stem.setLine(
                stemX,
                UPPER_STEM.getY1(),
                stemX,
                UPPER_STEM.getY2() - stemLengthOffset - note.properties.lengthening
            );
        } else {
            note.properties.stem.setLine(
                0d,
                LOWER_STEM.getY1() + stemYOffset,
                0d,
                LOWER_STEM.getY2() + stemLengthOffset - note.properties.lengthening
            );
        }

        try (var ignored = GraphicsState.save(g2, STROKE)) {
            g2.setStroke(STEM_STROKE_IMPL);
            g2.draw(note.properties.stem);
        }
    }

    // ==========================================================================
    // Flag Rendering
    // ==========================================================================

    private void renderFlags(
        @NotNull Graphics2D g2,
        boolean upper,
        boolean isTempoNote,
        @NotNull NoteType noteType
    ) {
        if (!noteType.isBeamable()) {
            return;
        }

        float offset = (noteType == NoteType.QUAVER)
            ? 0f
            : SEMIQUAVER_AND_DEMI_SEMIQUAVER_FLAG_COLLAPSE;

        if (upper) {
            if (isTempoNote) {
                g2.translate(0, 2); // tempoStemShortening
            }

            g2.drawString(MAIN_UPPER_FLAG, (float) UPPER_FLAG_X, (float) UPPER_FLAG_Y + offset);

            if (noteType != NoteType.QUAVER) {
                g2.drawString(SECOND_UPPER_FLAG, (float) UPPER_FLAG_X, (float) UPPER_FLAG_2_Y + offset);

                if (noteType != NoteType.SEMIQUAVER) {
                    g2.drawString(SECOND_UPPER_FLAG, (float) UPPER_FLAG_X, (float) UPPER_FLAG_3_Y + offset);
                }
            }

            if (isTempoNote) {
                g2.translate(0, -2);
            }
        } else {
            if (isTempoNote) {
                g2.translate(0, -2);
            }

            g2.drawString(MAIN_LOWER_FLAG, 0f, (float) LOWER_FLAG_Y - offset);

            if (noteType != NoteType.QUAVER) {
                g2.drawString(SECOND_LOWER_FLAG, 0f, (float) LOWER_FLAG_2_Y - offset);

                if (noteType != NoteType.SEMIQUAVER) {
                    g2.drawString(SECOND_LOWER_FLAG, 0f, (float) LOWER_FLAG_3_Y - offset);
                }
            }

            if (isTempoNote) {
                g2.translate(0, 2);
            }
        }
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

            for (int i = 0; i < note.getDotCount(); i++) {
                g2.fill(NOTE_DOTS[i]);
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

        try (var ignored = GraphicsState.save(g2, COLOR, FONT)) {
            float resizeFactor = 1f;

            if (note.getNoteType().isGraceNote()) {
                g2.setFont(ctx.getMusicGraceFont());
                resizeFactor = GRACE_ACCIDENTAL_RESIZE_FACTOR;
            }

            float accidentalWidth = getAccidentalWidth(note);

            if (!note.isAccidentalInParentheses() ||
                !ACCIDENTALS[accidental].equals(ACCIDENTAL_PARENTHESIS[accidental])) {
                renderSimpleAccidental(g2, note, -ACCIDENTAL_PADDING - accidentalWidth, resizeFactor);
            } else {
                float xPos = -ACCIDENTAL_PADDING - accidentalWidth;
                g2.drawString(BEGIN_PARENTHESIS, xPos * resizeFactor, (float) MANUAL_PARENTHESIS_Y * resizeFactor);

                // Calculate begin parenthesis width (approximate)
                float beginParenWidth = g2.getFontMetrics().stringWidth(BEGIN_PARENTHESIS);
                xPos += beginParenWidth;

                if (note.getAccidental().getComponent(1) == 1) {
                    xPos += 0.5f;
                }

                renderSimpleAccidental(g2, note, xPos, resizeFactor);

                float endParenWidth = g2.getFontMetrics().stringWidth(END_PARENTHESIS);
                g2.drawString(
                    END_PARENTHESIS,
                    (-ACCIDENTAL_PADDING - endParenWidth) * resizeFactor,
                    (float) MANUAL_PARENTHESIS_Y * resizeFactor
                );
            }

            if (note.getNoteType().isGraceNote()) {
                g2.setFont(ctx.getMusicFont());
            }
        }
    }

    private void renderSimpleAccidental(
        @NotNull Graphics2D g2,
        @NotNull Note note,
        float startX,
        float resizeFactor
    ) {
        var accidental = note.getAccidental().ordinal();
        float x = startX * resizeFactor;
        String str = note.isAccidentalInParentheses()
            ? ACCIDENTAL_PARENTHESIS[accidental]
            : ACCIDENTALS[accidental];

        if (str.length() == 1) {
            g2.drawString(str, x, 0f);
        } else {
            g2.drawString(str.substring(0, 1), x, 0f);

            float componentWidth = getAccidentalComponentWidth(note, 0);
            g2.drawString(
                str.substring(1),
                x + ((componentWidth + SPACE_BETWEEN_TWO_ACCIDENTALS) * resizeFactor),
                0f
            );
        }
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
     * Initializes the cached accidental widths using the given graphics context.
     * This must be called once before using getAccidentalWidth() or getAccidentalComponentWidth().
     * <p>
     * Migrated from FughettaRenderer.calculateAccidentalWidths().
     */
    public static void initializeAccidentalWidths(@NotNull Graphics2D g2) {
        if (baseAccidentalWidths != null) {
            return;
        }

        var metrics = g2.getFontMetrics(BaseElementRenderer.MUSIC_FONT);
        var accidentalEnums = Note.Accidental.values();
        baseAccidentalWidths = new float[ACCIDENTALS.length];

        // Calculate widths for single-character accidentals
        for (var i = 0; i < baseAccidentalWidths.length; i++) {
            baseAccidentalWidths[i] = (ACCIDENTALS[i].length() == 1)
                ? metrics.stringWidth(ACCIDENTALS[i])
                : 0f;
        }

        // Calculate widths for two-character accidentals (double-natural, natural-flat, natural-sharp)
        for (var i = 0; i < baseAccidentalWidths.length; i++) {
            if ((baseAccidentalWidths[i] == 0f) && (ACCIDENTALS[i].length() == 2)) {
                baseAccidentalWidths[i] =
                    baseAccidentalWidths[accidentalEnums[i].getComponent(0) + 1];
                baseAccidentalWidths[i] += SPACE_BETWEEN_TWO_ACCIDENTALS;
                baseAccidentalWidths[i] +=
                    baseAccidentalWidths[accidentalEnums[i].getComponent(1) + 1];
            }
        }

        // Calculate widths for parenthesized accidentals
        baseAccidentalParenthesisWidths = new float[ACCIDENTAL_PARENTHESIS.length];
        beginParenthesisWidth = metrics.stringWidth(BEGIN_PARENTHESIS);
        endParenthesisWidth = metrics.stringWidth(END_PARENTHESIS);

        for (var i = 0; i < baseAccidentalParenthesisWidths.length; i++) {
            baseAccidentalParenthesisWidths[i] = !ACCIDENTALS[i].equals(ACCIDENTAL_PARENTHESIS[i])
                ? metrics.stringWidth(ACCIDENTAL_PARENTHESIS[i])
                : (baseAccidentalWidths[i] + beginParenthesisWidth + endParenthesisWidth);
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
            g2.setFont(BaseElementRenderer.MUSIC_FONT);
            String glyph = RestRenderer.getRestGlyph(noteType);

            if (glyph != null) {
                g2.drawString(glyph, 0f, 0f);
            }
        }
    }

    private void renderBarLineOrRepeat(@NotNull Graphics2D g2, @NotNull NoteType noteType) {
        BarRenderer.renderBarLineOrRepeat(g2, noteType);
    }
}
