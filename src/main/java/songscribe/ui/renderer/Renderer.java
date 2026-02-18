/*
 * SongScribe song notation program
 * Copyright (C) Sri Chinmoy Centres International
 *
 * This file is part of SongScribe.
 *
 * SongScribe is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * SongScribe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package songscribe.ui.renderer;

import static songscribe.ui.component.Score.SELECTION_STROKE_COLOR;

import java.awt.*;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.jetbrains.annotations.NotNull;

import songscribe.data.DynamicsIntervalData;
import songscribe.data.IntervalSet;
import songscribe.data.SlurData;
import songscribe.data.TupletIntervalData;
import songscribe.music.BeatChange;
import songscribe.music.Composition;
import songscribe.music.DurationArticulation;
import songscribe.music.ForceArticulation;
import songscribe.music.GraceSemiQuaver;
import songscribe.music.KeyType;
import songscribe.music.Line;
import songscribe.music.Note;
import songscribe.music.NoteType;
import songscribe.music.Tempo;
import songscribe.ui.Constants;
import songscribe.ui.component.Score;
import songscribe.util.GraphicUtils;
import songscribe.util.MyFontUtils;
import songscribe.util.StringUtils;

/**
 * This class is responsible for rendering a score to a graphics context.
 * It works in conjunction with the Score (and Composition) classes to calculate
 * the positions of the various elements.
 */
public abstract class Renderer {

    protected static final double barLineSpace = 4.167d;
    protected static final BasicStroke heavyLineStroke = new BasicStroke(
        4f,
        BasicStroke.CAP_BUTT,
        BasicStroke.JOIN_MITER
    );
    protected static final BasicStroke thinLineStroke = new BasicStroke(
        1.5f,
        BasicStroke.CAP_BUTT,
        BasicStroke.JOIN_MITER
    );
    protected static final BasicStroke repeatHeavyStroke = new BasicStroke(
        4.167f,
        BasicStroke.CAP_BUTT,
        BasicStroke.JOIN_BEVEL
    );
    protected static final BasicStroke repeatThinStroke = new BasicStroke(
        1.5f,
        BasicStroke.CAP_BUTT,
        BasicStroke.JOIN_BEVEL
    );

    // The maximum percentage of line width the title can occupy before being split into lines
    protected static final double TITLE_MAX_WIDTH_PERCENTAGE = 0.75;

    // The font size of the notes using Fughetta
    // TODO: Figure out what this should be in Bravura
    protected static final float NOTE_FONT_SIZE = 32;

    protected static final double repeatThinCircleDiff =
        NOTE_FONT_SIZE / 6.918919d;

    protected static final double repeatThickThinDiff =
        NOTE_FONT_SIZE / 6.095238d;

    protected static final Ellipse2D.Float repeatCircle1 = new Ellipse2D.Float(
        0f,
        (NOTE_FONT_SIZE / 2f) - (NOTE_FONT_SIZE / 2.3703704f),
        NOTE_FONT_SIZE / 8f,
        NOTE_FONT_SIZE / 8f
    );

    protected static final Ellipse2D.Float repeatCircle2 = new Ellipse2D.Float(
        0f,
        (NOTE_FONT_SIZE / 2f) - (NOTE_FONT_SIZE / 1.4545455f),
        NOTE_FONT_SIZE / 8f,
        NOTE_FONT_SIZE / 8f
    );

    protected static final Line2D.Float verticalLine = new Line2D.Float(
        0,
        -NOTE_FONT_SIZE / 2f,
        0,
        NOTE_FONT_SIZE / 2f
    );

    protected static final Line2D.Float barLine = new Line2D.Float(
        Note.NORMAL_IMAGE_WIDTH,
        -NOTE_FONT_SIZE / 2f,
        Note.NORMAL_IMAGE_WIDTH,
        NOTE_FONT_SIZE / 2f
    );

    // The length of the Fughetta glissando character from left bearing to right bearing
    // TODO: This should be calculated from the actual character
    private static final double GLISSANDO_LENGTH = NOTE_FONT_SIZE / 2.6666667;

    // The length of an inner beam in device units
    protected static final double INNER_BEAM_LENGTH = 11d;

    // The offset of the inner beam from the outer beam in device units
    protected static final double INNER_BEAM_OFFSET = 6d;

    // The padding between attribution and the right edge of the page
    protected static final int ATTRIBUTION_RIGHT_PADDING = 20;

    // We add this to the Lato font height to get the line height
    protected static final int LYRICS_EXTRA_LEADING = 2;

    // Vertical spacing for Bangla lyrics (2 staff lines = 16 pixels)
    protected static final int BANGLA_LYRICS_TOP_MARGIN =
        2 * Score.STAFF_LINE_Y_OFFSET;

    // Vertical spacing for translation block (2 staff lines = 16 pixels)
    protected static final int TRANSLATION_TOP_MARGIN =
        2 * Score.STAFF_LINE_Y_OFFSET;

    // Translation header text
    protected static final String TRANSLATION_HEADER_OFFICIAL =
        "Sri Chinmoy's translation:";
    protected static final String TRANSLATION_HEADER_UNOFFICIAL =
        "Unofficial translation:";

    // Minimum vertical spacing above footnotes (5 staff lines = 40 pixels)
    protected static final int FOOTNOTES_MIN_TOP_MARGIN =
        5 * Score.STAFF_LINE_Y_OFFSET;

    // The stroke used to draw beams
    protected static final BasicStroke beamStroke = new BasicStroke(
        4.04f,
        BasicStroke.CAP_BUTT,
        BasicStroke.JOIN_MITER
    );

    // The stroke used to draw:
    //   - Staff lines
    //   - Crescendo/Diminuendo lines
    //   - Tuplet curves
    //   - Slurs
    //   - Ties
    //   - Sixteenth grace note beams
    protected static final BasicStroke lineStroke = new BasicStroke(
        1f,
        BasicStroke.CAP_BUTT,
        BasicStroke.JOIN_MITER
    );

    // The stroke used to draw single vertical bar lines
    protected static final BasicStroke barLineStroke = new BasicStroke(
        1.5f,
        BasicStroke.CAP_BUTT,
        BasicStroke.JOIN_MITER
    );

    // The stroke used to draw note stems
    protected static final BasicStroke stemStroke = new BasicStroke(
        1f,
        BasicStroke.CAP_BUTT,
        BasicStroke.JOIN_MITER
    );

    // The stroke used to draw tenuto lines
    protected static final BasicStroke tenutoStroke = new BasicStroke(
        1.5f,
        BasicStroke.CAP_BUTT,
        BasicStroke.JOIN_MITER
    );

    // The stroke used to draw dashes between syllables
    // TODO: Replace with a hyphen?
    private static final BasicStroke dashStroke = new BasicStroke(
        1f,
        BasicStroke.CAP_BUTT,
        BasicStroke.JOIN_MITER,
        10f,
        new float[]{3.937f, 5.9055f},
        0f
    );

    // The stroke used to draw hyphens between words
    // TODO: Replace with en dash?
    private static final BasicStroke longDashStroke = new BasicStroke(
        1f,
        BasicStroke.CAP_BUTT,
        BasicStroke.JOIN_MITER
    );
    private static final float LONG_DASH_WIDTH = 7f;

    private static final BasicStroke graceSemiQuaverStemStroke =
        new BasicStroke(0.6f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER);

    private static final BasicStroke graceSemiQuaverBeamStroke =
        new BasicStroke(2f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER);

    private static final BasicStroke graceSemiQuaverStrikeStroke =
        new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL);

    // The stroke used to draw the continuation of a syllable
    // TODO: Use GlyphVector.getOutline(<underscore>) to draw this
    private static final BasicStroke underScoreStroke = new BasicStroke(
        0.836f,
        BasicStroke.CAP_BUTT,
        BasicStroke.JOIN_MITER
    );

    // TODO: Replace with Bravura
    protected static final Font fughetta;

    // TODO: Replace with Bravura
    protected static final Font fughettaGrace;

    // TODO: This will go away when I rewrite the renderer
    protected static final double TEMPO_CHANGE_ZOOM_X = 0.8;
    protected static final double TEMPO_CHANGE_ZOOM_Y = 0.6;

    // The space between an accidental and the note head
    // TODO: Adjust this for Bravura
    protected static final float ACCIDENTAL_PADDING = 2.7f; //1.139f;

    // The space between two accidentals
    // TODO: This will probably go away with Bravura
    protected static final float SPACE_BETWEEN_TWO_ACCIDENTALS = 1.3f;

    // How much to scale the grace note accidentals
    protected static final float GRACE_ACCIDENTAL_RESIZE_FACTOR = 0.65f;

    // TODO: Replace with Bravura staccato combining mark 1D17C
    private static final Ellipse2D.Float staccatoEllipse = new Ellipse2D.Float(
        0f,
        0f,
        3.5f,
        3.5f
    );

    // Used to draw the number in a tuplet
    private static final Font tupletFont;

    // Used to draw the number in a first or second ending
    private static final Font firstSecondEndingFont;

    // In Fughetta
    // TODO: Replace with Bravura
    private static final String GLISSANDO = "\uf07e";
    private static final String TRILL = "\uf0d9";

    private static final NoteType[] BEAM_LEVELS = new NoteType[]{
        NoteType.DEMI_SEMIQUAVER,
        NoteType.SEMIQUAVER,
        NoteType.QUAVER,
    };

    // These arrays are the y positions of the accidentals in a key signature,
    // with the middle line of a staff being 0.
    protected static final int[][] KEY_SIGNATURE_Y_POSITIONS = new int[][]{
        new int[]{}, // None
        new int[]{0, -3, 1, -2, 2, -1, 3}, // Flats
        new int[]{-4, -1, -5, -2, 1, -3, 0}, // Sharps
    };

    // The width of a note head?
    // TODO: Use bravura_medata
    protected double crotchetWidth = 0.0;

    // TODO: Adjust when I switch to Bravura
    protected double beamX1Correction = 0.0;
    protected double beamX2Correction = 0.0;

    // The maximum visual ascent (including combining marks) of a title line.
    // It only needs to be calculated once.
    protected int titleMaxAscent = -1;

    protected Score score;
    protected Composition composition;

    static {
        // TODO: Replace with Bravura
        var fontName = "Fughetta";

        try {
            var font = Objects.requireNonNull(
                MyFontUtils.getLocalFont("Fughetta.ttf")
            );
            fughetta = font.deriveFont(NOTE_FONT_SIZE);
            fughettaGrace = font.deriveFont(
                NOTE_FONT_SIZE * GRACE_ACCIDENTAL_RESIZE_FACTOR
            );

            font = Objects.requireNonNull(
                MyFontUtils.getLocalFont("TupletNumbers.ttf")
            );
            tupletFont = font.deriveFont(13f);
            firstSecondEndingFont = tupletFont;
        } catch (Exception e) {
            throw new RuntimeException(
                "Cannot load a required font (" + fontName + ").",
                e
            );
        }
    }

    // The height of the rendered score, calculated during rendering
    private int height = 0;

    // TODO: This could potentially be replaced with height
    private float lyricsMaxY = 0;

    // This is used multiple times in the drawLyrics method, we calculate it once
    private int lyricsMaxDescent = 0;

    // We want the dash to be placed at the middle of the font's x height. This is calculated once.
    private float dashOffset = 0;

    // If true, strings are drawn using glyph vectors. If false, strings are drawn using drawString.
    // It is set to true when exporting to SVG.
    private static boolean convertTextToOutlines = false;

    protected Renderer(@NotNull Score score) {
        this.score = score;
        composition = score.getComposition();
    }

    public static void setConvertTextToOutlines(boolean convertTextToOutlines) {
        Renderer.convertTextToOutlines = convertTextToOutlines;
    }

    public void drawScore(
        Graphics2D g2,
        boolean drawEditingComponents,
        double scale
    ) {
        /*
          We draw each vertical section of the music sheet separately:
            - Title
            - Attribution
            - Tempo
            - Composition lines
            - Lyrics
            - Separator
            - Bangla lyrics
            - Separator
            - English translation
            - Footnotes
         */
        GraphicUtils.setRenderingHints(g2);
        composition = score.getComposition();

        // TODO: This can go away when I switch to BravuraRenderer
        FughettaRenderer.calculateAccidentalWidths(g2);

        if (scale != 1d) {
            g2.scale(scale, scale);
        }

        if (!drawEditingComponents) {
            g2.translate(0, -score.getStartY());
        }

        g2.setColor(Color.black);

        drawTitle(g2);
        drawAttribution(g2);
        drawComposition(g2, drawEditingComponents);
        drawUnderLyrics(g2);
        drawFootnotes(g2);

        // Height comes from LayoutManager
        height = score.getLayoutManager().getTotalHeight();
    }

    private void drawTitle(Graphics2D g2) {
        var titleBounds = score.getLayoutManager().getTitleBounds();

        if (titleBounds == null || titleBounds.height == 0) {
            lyricsMaxY = 0;
            return;
        }

        var title = composition.getTitle();

        if (title.isEmpty()) {
            lyricsMaxY = 0;
            return;
        }

        var font = composition.getTitleFont();
        g2.setFont(font);
        var metrics = g2.getFontMetrics();
        var number = composition.getNumber();

        if (!number.isEmpty()) {
            title = number + ". " + title;
        }

        var lineWidth = composition.getLineWidth();

        // It isn't enough to get the max ascent from the metrics, because combining marks can be higher.
        // We need to render the tallest possible letter + combining mark to get the real ascent.
        if (titleMaxAscent == -1) {
            var context = g2.getFontRenderContext();
            var glyphVector = font.createGlyphVector(context, "Á");
            var bounds = glyphVector.getVisualBounds();
            titleMaxAscent = (int) Math.round(-bounds.getY());
        }

        var lineHeight = metrics.getHeight();

        // Wrap the title if necessary to fit in the maximum width
        var titleLines = StringUtils.wrapText(
            title,
            metrics,
            (int) (composition.getLineWidth() * TITLE_MAX_WIDTH_PERCENTAGE)
        );

        // Draw from the Y position in titleBounds
        var startY = titleBounds.y;

        for (var i = 0; i < titleLines.size(); i++) {
            var titleLine = titleLines.get(i);
            var titleWidth = metrics.stringWidth(titleLine);
            var x = ((float) lineWidth - titleWidth) / 2;
            var y = startY + titleMaxAscent + (i * lineHeight);
            drawString(g2, titleLine, x, y);
        }

        // Track the bottom of the title for subsequent sections
        lyricsMaxY = titleBounds.y + titleBounds.height;
    }

    /**
     * Measures the title without drawing it.
     *
     * @param g2 Graphics context for font metrics
     * @return Rectangle with title bounds (x=0, y=0, width=max line width, height=total height)
     */
    @NotNull
    public Rectangle measureTitle(@NotNull Graphics2D g2) {
        var comp = score.getComposition();

        if (comp == null) {
            return new Rectangle(0, 0, 0, 0);
        }

        var title = comp.getTitle();

        if (title.isEmpty()) {
            return new Rectangle(0, 0, 0, 0);
        }

        var font = comp.getTitleFont();
        g2.setFont(font);
        var metrics = g2.getFontMetrics();
        var number = comp.getNumber();

        if (!number.isEmpty()) {
            title = number + ". " + title;
        }

        // Calculate titleMaxAscent if not already done
        if (titleMaxAscent == -1) {
            var context = g2.getFontRenderContext();
            var glyphVector = font.createGlyphVector(context, "Á");
            var bounds = glyphVector.getVisualBounds();
            titleMaxAscent = (int) Math.round(-bounds.getY());
        }

        var lineHeight = metrics.getHeight();

        // Wrap the title to fit in the maximum width
        var titleLines = StringUtils.wrapText(
            title,
            metrics,
            (int) (comp.getLineWidth() * TITLE_MAX_WIDTH_PERCENTAGE)
        );

        // Calculate max width of wrapped lines
        var maxWidth = 0;

        for (var titleLine : titleLines) {
            maxWidth = Math.max(maxWidth, metrics.stringWidth(titleLine));
        }

        // Height: titleMaxAscent + (lineCount - 1) * lineHeight + maxDescent
        var height = titleMaxAscent +
            ((titleLines.size() - 1) * lineHeight) +
            metrics.getMaxDescent();

        return new Rectangle(0, 0, maxWidth, height);
    }

    private void drawAttribution(Graphics2D g2) {
        var attributionBounds = score.getLayoutManager().getAttributionBounds();

        if (attributionBounds == null || attributionBounds.height == 0) {
            return;
        }

        var attribution = composition.getAttribution();

        if (attribution.isEmpty()) {
            return;
        }

        g2.setFont(composition.getAttributionFont());
        var maxWidth = GraphicUtils.getTextBlockWidth(attribution, g2);
        var x = (float) (composition.getLineWidth() - maxWidth);

        // Y position comes from LayoutManager bounds + ascent for baseline
        var y = attributionBounds.y + g2.getFontMetrics().getAscent();

        drawTextBox(g2, attribution, x - ATTRIBUTION_RIGHT_PADDING, y, 0);

        // Update lyricsMaxY to track the bottom of attribution
        lyricsMaxY = attributionBounds.y + attributionBounds.height;
    }

    /**
     * Measures the attribution text without drawing it.
     *
     * @param g2 Graphics context for font metrics
     * @return Rectangle with attribution bounds (x=0, y=0, width, height)
     */
    @NotNull
    public Rectangle measureAttribution(@NotNull Graphics2D g2) {
        var comp = score.getComposition();

        if (comp == null) {
            return new Rectangle(0, 0, 0, 0);
        }

        var attribution = comp.getAttribution();

        if (attribution.isEmpty()) {
            return new Rectangle(0, 0, 0, 0);
        }

        g2.setFont(comp.getAttributionFont());
        var metrics = g2.getFontMetrics();
        var width = (int) Math.ceil(
            GraphicUtils.getTextBlockWidth(attribution, g2)
        );

        // Use measureTextBox to calculate the height
        var textBounds = measureTextBox(metrics, attribution, 0);
        return new Rectangle(0, 0, width, textBounds.height);
    }

    /**
     * Measures the score section without drawing it.
     * <p>
     * Calculates the uniform row height based on the maximum required height
     * across all lines, then computes the total score section bounds.
     *
     * @param g2 Graphics context for font metrics
     * @return Rectangle with score bounds (x=0, y=0, width, height)
     */
    @NotNull
    public Rectangle measureScore(@NotNull Graphics2D g2) {
        var comp = score.getComposition();

        if (comp == null) {
            return new Rectangle(0, 0, 0, 0);
        }

        var lineCount = comp.lineCount();

        if (lineCount == 0) {
            return new Rectangle(0, 0, 0, 0);
        }

        // Calculate uniform row height based on max required height
        var uniformRowHeight = calculateUniformRowHeight();

        // Total height is uniform row height * number of lines
        var totalHeight = uniformRowHeight * lineCount;

        // Width is the line width from composition
        var width = comp.getLineWidth();

        return new Rectangle(0, 0, width, totalHeight);
    }

    /**
     * Calculates the uniform row height for inter-staff spacing.
     * <p>
     * This finds the maximum required height across all lines and ensures
     * all lines use the same spacing for cleaner visual alignment and
     * simpler hit-testing.
     *
     * @return The uniform row height in pixels
     */
    public int calculateUniformRowHeight() {
        // Ensure composition is set (may be called during initialization)
        if (composition == null) {
            composition = score.getComposition();
        }

        if (composition == null) {
            return getDefaultRowHeight();
        }

        var lineCount = composition.lineCount();

        if (lineCount == 0) {
            return getDefaultRowHeight();
        }

        // Find the maximum required height across all lines
        int maxRequiredHeight = 0;

        for (var i = 0; i < lineCount; i++) {
            var line = composition.getLine(i);
            var requiredHeight = line.getRequiredHeight();
            maxRequiredHeight = Math.max(maxRequiredHeight, requiredHeight);
        }

        // Apply composition-level row height adjustment
        var adjustedHeight = maxRequiredHeight +
            composition.getRowHeightAdjustment();

        // Ensure we don't go below the default row height
        return Math.max(adjustedHeight, getDefaultRowHeight());
    }

    /**
     * Returns the default row height for a standard staff without extra content.
     * <p>
     * This is the base height calculated as:
     * (STAFF_LINES_BELOW + STAFF_LINE_COUNT + STAFF_LINES_ABOVE + 1) * STAFF_LINE_Y_OFFSET
     * = (4 + 5 + 3 + 1) * 8 = 104 pixels
     *
     * @return The default row height in pixels
     */
    protected int getDefaultRowHeight() {
        return 13 * Score.STAFF_LINE_Y_OFFSET; // 104 pixels
    }

    /**
     * Measures the lyrics block without drawing it.
     *
     * @param g2 Graphics context for font metrics
     * @return Rectangle with width and height of the lyrics block, or empty if no lyrics
     */
    public Rectangle measureLyrics(@NotNull Graphics2D g2) {
        var comp = score.getComposition();

        if (comp == null) {
            return new Rectangle(0, 0, 0, 0);
        }

        var lyrics = comp.getUnderLyrics();

        if (lyrics.isEmpty()) {
            return new Rectangle(0, 0, 0, 0);
        }

        g2.setFont(comp.getLyricsFont());
        var metrics = g2.getFontMetrics();
        var width = (int) Math.ceil(GraphicUtils.getTextBlockWidth(lyrics, g2));
        var textBounds = measureTextBox(metrics, lyrics, 0);

        return new Rectangle(0, 0, width, textBounds.height);
    }

    /**
     * Measures the Bangla lyrics block without drawing it.
     *
     * @param g2 Graphics context for font metrics
     * @return Rectangle with width and height of the Bangla lyrics block, or empty if none
     */
    public Rectangle measureBanglaLyrics(@NotNull Graphics2D g2) {
        var comp = score.getComposition();

        if (comp == null) {
            return new Rectangle(0, 0, 0, 0);
        }

        var banglaLyrics = comp.getBanglaLyrics();

        if (banglaLyrics.isEmpty()) {
            return new Rectangle(0, 0, 0, 0);
        }

        g2.setFont(comp.getBanglaFont());
        var metrics = g2.getFontMetrics();
        var width = (int) Math.ceil(
            GraphicUtils.getTextBlockWidth(banglaLyrics, g2)
        );
        var textBounds = measureTextBox(metrics, banglaLyrics, 0);

        return new Rectangle(0, 0, width, textBounds.height);
    }

    /**
     * Measures the translation block without drawing it.
     * <p>
     * The measurement includes the header line height.
     *
     * @param g2 Graphics context for font metrics
     * @return Rectangle with width and height of the translation block including header
     */
    public Rectangle measureTranslation(@NotNull Graphics2D g2) {
        var comp = score.getComposition();

        if (comp == null) {
            return new Rectangle(0, 0, 0, 0);
        }

        var translation = comp.getTranslatedLyrics();

        if (translation.isEmpty()) {
            return new Rectangle(0, 0, 0, 0);
        }

        var lyricsFont = comp.getLyricsFont();

        // Measure header line
        var headerFont = lyricsFont.deriveFont(
            Font.BOLD,
            lyricsFont.getSize2D()
        );
        g2.setFont(headerFont);
        var headerMetrics = g2.getFontMetrics();
        var headerText = comp.isUnofficialTranslation()
            ? TRANSLATION_HEADER_UNOFFICIAL
            : TRANSLATION_HEADER_OFFICIAL;
        var headerWidth = headerMetrics.stringWidth(headerText);
        var headerHeight = headerMetrics.getHeight();

        // Measure translation text
        g2.setFont(lyricsFont);
        var translationMetrics = g2.getFontMetrics();
        var translationWidth = (int) Math.ceil(
            GraphicUtils.getTextBlockWidth(translation, g2)
        );
        var translationBounds = measureTextBox(translationMetrics, translation, 0);

        // Combine: max width, sum of heights (including margin below header)
        var totalWidth = Math.max(headerWidth, translationWidth);
        var headerMargin = (int) Math.ceil(lyricsFont.getSize2D() / 4f);
        var totalHeight = headerHeight + headerMargin + translationBounds.height;

        return new Rectangle(0, 0, totalWidth, totalHeight);
    }

    /**
     * Measures the footnotes block without drawing.
     * Footnotes are bottom-anchored with max width of 2/3 page width.
     *
     * @param g2 Graphics context for font metrics
     * @return Rectangle with width and height of the footnotes block
     */
    public Rectangle measureFootnotes(@NotNull Graphics2D g2) {
        var comp = score.getComposition();

        if (comp == null) {
            return new Rectangle(0, 0, 0, 0);
        }

        var footnotes = comp.getFootnotes();

        if (footnotes.isEmpty()) {
            return new Rectangle(0, 0, 0, 0);
        }

        g2.setFont(comp.getFootnoteFont());
        var metrics = g2.getFontMetrics();

        // Max width is 2/3 of line width
        var maxWidth = (comp.getLineWidth() * 2) / 3;

        // Measure the text block
        var bounds = measureTextBox(metrics, footnotes, 0f);

        // Clamp width to max width
        var actualWidth = Math.min(bounds.width, maxWidth);

        return new Rectangle(0, 0, actualWidth, bounds.height);
    }

    private void drawComposition(
        @NotNull Graphics2D g2,
        boolean drawEditingComponents
    ) {
        var font = composition.getLyricsFont();
        g2.setFont(font);

        if (lyricsMaxDescent == 0) {
            var lyricsMetrics = g2.getFontMetrics(font);
            lyricsMaxDescent = lyricsMetrics.getMaxDescent();
        }

        if (dashOffset == 0) {
            // We want the dash to be placed at the middle of the font's x height.
            // Subtract half of the line stroke width to center the dash.
            var halfDashHeight = (longDashStroke.getLineWidth() / 2);
            dashOffset = MyFontUtils.getXHeight(g2) - halfDashHeight;
        }

        var count = composition.lineCount();

        for (var i = 0; i < count; i++) {
            drawLine(g2, drawEditingComponents, i);
        }
    }

    private void drawLine(
        Graphics2D g2,
        boolean drawEditingComponents,
        int lineIndex
    ) {
        var isLastLine = lineIndex == (composition.lineCount() - 1);
        drawStaffLines(g2, lineIndex);

        // Draw treble clef and leading keys
        var line = composition.getLine(lineIndex);
        var maxY = drawLineBeginning(g2, line, lineIndex);

        if (lyricsMaxY == 0) {
            height = maxY;
        }

        drawNotes(g2, drawEditingComponents, line, lineIndex, isLastLine);
        drawSlurs(g2, lineIndex, line, drawEditingComponents);
        drawBeamsOnLine(g2, lineIndex, line, drawEditingComponents);
        drawTuplets(g2, lineIndex, line, drawEditingComponents);
        drawKeyChanges(g2, lineIndex, line);
        drawEndings(g2, lineIndex, line);
        drawCrescendos(g2, lineIndex, line, drawEditingComponents);
        drawDiminuendos(g2, lineIndex, line, drawEditingComponents);
    }

    private void drawNotes(
        @NotNull Graphics2D g2,
        boolean drawEditingComponents,
        @NotNull Line line,
        int lineIndex,
        boolean isLastLine
    ) {
        var oldColor = g2.getColor();
        var lyricsDrawnIndex = 0;

        for (var noteIndex = 0; noteIndex < line.noteCount(); noteIndex++) {
            var note = line.getNote(noteIndex);

            if (note.getTempoChange() != null) {
                drawTempoChange(
                    g2,
                    note.getTempoChange(),
                    lineIndex,
                    noteIndex
                );
            } else if ((lineIndex == 0) && (noteIndex == 0)) {
                drawTempoChange(
                    g2,
                    composition.getTempo(),
                    lineIndex,
                    noteIndex
                );
            }

            if (note.getBeatChange() != null) {
                drawBeatChange(g2, lineIndex, note);
            }

            var beamed =
                (line.getBeamings().findInterval(noteIndex) != null) &&
                    (note.getNoteType() != NoteType.GRACE_QUAVER);

            var color = getPaintColorForNoteInterval(
                lineIndex,
                noteIndex,
                noteIndex,
                true,
                drawEditingComponents
            );

            g2.setColor(color);

            lyricsDrawnIndex = paintNoteAndDecorations(
                g2,
                line,
                lineIndex,
                isLastLine,
                note,
                noteIndex,
                beamed,
                lyricsDrawnIndex
            );
        }

        g2.setColor(oldColor);
    }

    private Color getPaintColorForNoteInterval(
        int lineIndex,
        int beginIndex,
        int endIndex,
        boolean isNote,
        boolean drawEditComponents
    ) {
        if (!drawEditComponents) {
            return Color.black;
        }

        if (isNote) {
            var playingLine = score.getPlayingLine();
            var playingNote = score.getPlayingNote();

            if (
                (playingLine == lineIndex) &&
                    (playingNote >= beginIndex) &&
                    (playingNote <= endIndex)
            ) {
                return Score.PLAYING_NOTE_COLOR;
            }
        }

        return (
            score.isNoteSelected(beginIndex, lineIndex) &&
                score.isNoteSelected(endIndex, lineIndex)
        )
            ? SELECTION_STROKE_COLOR
            : Color.black;
    }

    private void drawDynamicMarks(
        @NotNull Graphics2D g2,
        int lineIndex,
        Line line,
        @NotNull IntervalSet dynamics,
        boolean isCrescendo,
        boolean drawEditingComponents
    ) {
        var oldColor = g2.getColor();
        g2.setStroke(lineStroke);

        for (var iter = dynamics.listIterator(); iter.hasNext(); ) {
            var interval = iter.next();
            var startNote = line.getNote(interval.getStart());
            var endNote = line.getNote(interval.getEnd());
            var x1 =
                startNote.getXPos() + DynamicsIntervalData.getX1Shift(interval);
            var yShift = DynamicsIntervalData.getYShift(interval);
            var x2 =
                endNote.getXPos() +
                    (int) crotchetWidth +
                    DynamicsIntervalData.getX2Shift(interval);

            g2.setColor(
                getPaintColorForNoteInterval(
                    lineIndex,
                    interval.getStart(),
                    interval.getEnd(),
                    false,
                    drawEditingComponents
                )
            );
            g2.drawLine(
                x1,
                score.getNoteYPos(isCrescendo ? 6 : 5, lineIndex) + yShift,
                x2,
                score.getNoteYPos(isCrescendo ? 5 : 6, lineIndex) + yShift
            );
            g2.drawLine(
                x1,
                score.getNoteYPos(isCrescendo ? 6 : 7, lineIndex) + yShift,
                x2,
                score.getNoteYPos(isCrescendo ? 7 : 6, lineIndex) + yShift
            );
        }

        g2.setColor(oldColor);
    }

    private void drawDiminuendos(
        Graphics2D g2,
        int lineIndex,
        Line line,
        boolean drawEditingComponents
    ) {
        drawDynamicMarks(
            g2,
            lineIndex,
            line,
            line.getDiminuendos(),
            false,
            drawEditingComponents
        );
    }

    private void drawCrescendos(
        Graphics2D g2,
        int lineIndex,
        Line line,
        boolean drawEditingComponents
    ) {
        drawDynamicMarks(
            g2,
            lineIndex,
            line,
            line.getCrescendos(),
            true,
            drawEditingComponents
        );
    }

    protected abstract void drawEndings(
        Graphics2D g2,
        int lineIndex,
        Line line
    );

    private void drawKeyChanges(Graphics2D g2, int lineIndex, Line line) {
        if ((lineIndex + 1) >= composition.lineCount()) {
            return;
        }

        var nextLine = composition.getLine(lineIndex + 1);

        if (
            (nextLine.getKeyAccidentalCount() ==
                line.getKeyAccidentalCount()) &&
                (nextLine.getKeyType() == line.getKeyType())
        ) {
            return;
        }

        var newKeyTypes = new KeyType[2];
        var newAccidentalCounts = new int[2];
        var oldAccidentalCounts = new int[2];
        var isNaturals = new boolean[2];

        if (nextLine.getKeyType() == line.getKeyType()) {
            newKeyTypes[0] = nextLine.getKeyType();
            newAccidentalCounts[0] = nextLine.getKeyAccidentalCount();

            if (
                nextLine.getKeyAccidentalCount() > line.getKeyAccidentalCount()
            ) {
                //noinspection AssignmentToNull
                newKeyTypes[1] = null;
            } else {
                newKeyTypes[1] = line.getKeyType();
                newAccidentalCounts[1] = line.getKeyAccidentalCount() -
                    nextLine.getKeyAccidentalCount();
                oldAccidentalCounts[1] = nextLine.getKeyAccidentalCount();
                isNaturals[1] = true;
            }
        } else {
            newKeyTypes[0] = line.getKeyType();
            newAccidentalCounts[0] = line.getKeyAccidentalCount();
            isNaturals[0] = true;
            newKeyTypes[1] = nextLine.getKeyType();
            newAccidentalCounts[1] = nextLine.getKeyAccidentalCount();
        }

        drawKeySignatureChange(
            g2,
            lineIndex,
            newKeyTypes,
            newAccidentalCounts,
            oldAccidentalCounts,
            isNaturals
        );
    }

    private void drawTuplets(
        @NotNull Graphics2D g2,
        int lineIndex,
        @NotNull Line line,
        boolean drawEditingComponents
    ) {
        var oldColor = g2.getColor();

        for (var iter = line.getTuplets().listIterator(); iter.hasNext(); ) {
            var interval = iter.next();
            var odd =
                (((interval.getEnd() - interval.getStart()) + 1) % 2) == 1;
            var firstNote = line.getNote(interval.getStart());
            var upper = firstNote.isUpper() ? -1 : 0;
            var lx = firstNote.getXPos() + (int) crotchetWidth;
            var ly =
                (score.getNoteYPos(firstNote.getYPos(), lineIndex) -
                    Note.HOT_SPOT.y) +
                    (upper * firstNote.acceleration.lengthening);
            ly -= 5;

            int cx;

            if (odd) {
                var centerNote = line.getNote(
                    ((interval.getEnd() - interval.getStart()) / 2) +
                        interval.getStart()
                );
                cx = centerNote.getXPos() + (int) crotchetWidth;
            } else {
                var cn1 = line.getNote(
                    ((interval.getEnd() - interval.getStart()) / 2) +
                        interval.getStart()
                );
                var cn2 = line.getNote(
                    ((interval.getEnd() - interval.getStart()) / 2) +
                        interval.getStart() +
                        1
                );
                cx = ((cn2.getXPos() - cn1.getXPos()) / 2) +
                    cn1.getXPos() +
                    (int) crotchetWidth;
            }

            var lastNote = line.getNote(interval.getEnd());
            var rx = lastNote.getXPos() + (int) crotchetWidth;
            var ry =
                (score.getNoteYPos(lastNote.getYPos(), lineIndex) -
                    Note.HOT_SPOT.y) +
                    (upper * lastNote.acceleration.lengthening);
            ry -= 5;

            if (!firstNote.isUpper()) {
                lx -= (int) crotchetWidth / 2;
                ly += Note.HOT_SPOT.y - 3;
                cx -= (int) crotchetWidth / 2;
                rx -= (int) crotchetWidth / 2;
                ry += Note.HOT_SPOT.y - 3;
            }

            if (TupletIntervalData.isVerticalAdjusted(interval)) {
                ly += TupletIntervalData.getVerticalPosition(interval);
                ry += TupletIntervalData.getVerticalPosition(interval);
            }

            g2.setColor(
                getPaintColorForNoteInterval(
                    lineIndex,
                    interval.getStart(),
                    interval.getEnd(),
                    false,
                    drawEditingComponents
                )
            );
            g2.setStroke(lineStroke);
            var tc = new TupletCalc(lx, ly, rx, ry);

            g2.draw(
                new QuadCurve2D.Float(
                    lx,
                    ly,
                    ((float) (cx - lx) / 4) + lx,
                    tc.getRate(((cx - lx) / 4) + lx) - 10,
                    cx - 7,
                    tc.getRate(cx - 7) - 8
                )
            );
            g2.draw(
                new QuadCurve2D.Float(
                    cx + 7,
                    tc.getRate(cx + 7) - 8,
                    (((float) (rx - cx) * 3) / 4) + cx,
                    tc.getRate((((rx - cx) * 3) / 4) + cx) - 10,
                    rx,
                    ry
                )
            );
            g2.setFont(tupletFont);
            drawString(
                g2,
                Integer.toString(TupletIntervalData.getGrade(interval)),
                cx - 3,
                tc.getRate(cx - 3) - 5
            );
        }

        g2.setColor(oldColor);
    }

    private void drawBeamsOnLine(
        Graphics2D g2,
        int lineIndex,
        @NotNull Line line,
        boolean drawEditingComponents
    ) {
        for (var li = line.getBeamings().listIterator(); li.hasNext(); ) {
            var interval = li.next();
            Line2D.Double beamLine;
            var firstNote = line.getNote(interval.getStart());
            var lastNote = line.getNote(interval.getEnd());

            // TODO: maybe i should just pass the first/last note to drawBeams, that should be
            //  sufficient
            if (firstNote.isUpper()) {
                beamLine = new Line2D.Double(
                    firstNote.getXPos() + crotchetWidth,
                    score.getNoteYPos(firstNote.getYPos(), lineIndex) -
                        Note.HOT_SPOT.y -
                        firstNote.acceleration.lengthening,
                    lastNote.getXPos() + crotchetWidth,
                    score.getNoteYPos(lastNote.getYPos(), lineIndex) -
                        Note.HOT_SPOT.y -
                        lastNote.acceleration.lengthening
                );
            } else {
                beamLine = new Line2D.Double(
                    firstNote.getXPos(),
                    (score.getNoteYPos(firstNote.getYPos(), lineIndex) +
                        Note.HOT_SPOT.y) -
                        firstNote.acceleration.lengthening,
                    lastNote.getXPos() + 1,
                    (score.getNoteYPos(lastNote.getYPos(), lineIndex) +
                        Note.HOT_SPOT.y) -
                        lastNote.acceleration.lengthening
                );
            }

            g2.setStroke(beamStroke);
            beamLine.setLine(
                beamLine.x1 - 10,
                beamLine.y1 +
                    ((10 * (beamLine.y1 - beamLine.y2)) /
                        (beamLine.x2 - beamLine.x1)),
                beamLine.x2 + 10,
                beamLine.y2 -
                    ((10 * (beamLine.y1 - beamLine.y2)) /
                        (beamLine.x2 - beamLine.x1))
            );
            drawBeams(
                g2,
                BEAM_LEVELS.length - 1,
                line,
                lineIndex,
                interval.getStart(),
                interval.getEnd(),
                drawEditingComponents
            );
        }
    }

    private void drawSlurs(
        @NotNull Graphics2D g2,
        int lineIndex,
        @NotNull Line line,
        boolean drawEditingComponents
    ) {
        var oldColor = g2.getColor();

        for (var li = line.getSlurs().listIterator(); li.hasNext(); ) {
            var interval = li.next();
            var firstNote = line.getNote(interval.getStart());
            var lastNote = line.getNote(interval.getEnd());
            SlurData slurData;

            if (interval.getData() == null) {
                slurData = getDefaultSlurData(firstNote, lastNote);
                interval.setData(slurData.toString());
            } else {
                slurData = new SlurData(interval.getData());
            }

            g2.setColor(
                getPaintColorForNoteInterval(
                    lineIndex,
                    interval.getStart(),
                    interval.getEnd(),
                    false,
                    drawEditingComponents
                )
            );
            g2.setStroke(lineStroke);

            var tie = new GeneralPath(Path2D.WIND_NON_ZERO, 2);
            var xPos1 = firstNote.getXPos() + slurData.getXPos1();
            var xPos2 = lastNote.getXPos() + slurData.getXPos2();
            var yPos1 =
                score.getNoteYPos(firstNote.getYPos(), lineIndex) +
                    slurData.getYPos1();
            var yPos2 =
                score.getNoteYPos(lastNote.getYPos(), lineIndex) +
                    slurData.getYPos2();
            var ctrlY =
                ((score.getNoteYPos(firstNote.getYPos(), lineIndex) +
                    score.getNoteYPos(lastNote.getYPos(), lineIndex)) /
                    2) +
                    slurData.getCtrlY();
            var gap = xPos2 - xPos1;
            tie.moveTo(xPos1, yPos1);
            tie.quadTo(xPos1 + ((float) gap / 2), ctrlY, xPos1 + gap, yPos2);
            tie.quadTo(xPos1 + ((float) gap / 2), ctrlY + 2, xPos1, yPos1);
            tie.closePath();
            g2.draw(tie);
            g2.fill(tie);
        }

        g2.setColor(oldColor);
    }

    @NotNull
    private SlurData getDefaultSlurData(
        @NotNull Note firstNote,
        Note lastNote
    ) {
        var slurUpper = firstNote.isUpper();
        var xPos1 = getHalfNoteWidthForTie(firstNote) + 2;
        var xPos2 = getHalfNoteWidthForTie(lastNote) - 3;

        if (
            (firstNote.isUpper() != lastNote.isUpper()) && firstNote.isUpper()
        ) {
            slurUpper = false;
            xPos1 += 7;
            xPos2 -= 5;
        }

        var yPos = Math.round(
            slurUpper ? (Score.NOTE_Y_OFFSET + 2) : (-Score.NOTE_Y_OFFSET - 2)
        );
        var ctrlY = slurUpper ? 16 : -18;
        return new SlurData(xPos1, xPos2, yPos, yPos, ctrlY);
    }

    private void drawTrill(
        Graphics2D g2,
        int lineIndex,
        Line line,
        int noteIndex,
        @NotNull Note note
    ) {
        if (
            note.isTrill() &&
                ((noteIndex == 0) || !line.getNote(noteIndex - 1).isTrill())
        ) {
            var trillEnd = noteIndex + 1;

            while (
                (trillEnd < line.noteCount()) &&
                    line.getNote(trillEnd).isTrill()
            ) {
                trillEnd++;
            }

            trillEnd--;
            var x = note.getXPos();
            var y = score.getNoteYPos(0, lineIndex) + line.getTrillYPos();
            g2.setFont(fughetta);
            drawString(g2, TRILL, x, y);

            if (noteIndex < trillEnd) {
                drawGlissando(
                    g2,
                    x + 18,
                    y - 3,
                    (int) Math.round(
                        line.getNote(trillEnd).getXPos() + crotchetWidth
                    ),
                    y - 3
                );
            }
        }
    }

    private void drawAnnotation(
        Graphics2D g2,
        int lineIndex,
        Boolean lastLine,
        @NotNull Note note
    ) {
        if (note.getAnnotation() != null) {
            g2.setFont(composition.getAnnotationFont());
            var y = getAnnotationYPos(lineIndex, note);
            drawString(
                g2,
                note.getAnnotation().getAnnotation(),
                (float) getAnnotationXPos(g2, note),
                y
            );

            if (lastLine && (lyricsMaxY == 0)) {
                y += g2.getFontMetrics().getMaxDescent();

                if (y > height) {
                    height = y;
                }
            }
        }
    }

    private int drawLyrics(
        Graphics2D g2,
        int lineIndex,
        Boolean lastLine,
        @NotNull Line line,
        int lyricsDrawn,
        int noteIndex,
        Note note
    ) {
        var drawnIndex = lyricsDrawn;
        var lyricsY = score.getNoteYPos(0, lineIndex) + line.getLyricsYPos();

        if (lastLine && (lyricsMaxY == 0)) {
            height = lyricsY + lyricsMaxDescent;
        }

        var font = composition.getLyricsFont();
        g2.setFont(font);
        var metrics = g2.getFontMetrics(font);
        var syllableWidth = 0;
        var syllable = note.acceleration.syllable;
        var dashY = lyricsY - dashOffset;

        if ((syllable != null) && !syllable.equals(Constants.UNDERSCORE)) {
            syllableWidth = metrics.stringWidth(syllable);
            var lyricsX =
                ((note.getXPos() + Note.HOT_SPOT.x) - (syllableWidth / 2)) +
                    note.getSyllableMovement();

            if (!syllable.isEmpty()) {
                drawString(g2, syllable, lyricsX, lyricsY);
            }

            if (
                (noteIndex == 0) &&
                    (line.beginRelation == Note.SyllableRelation.ONE_DASH)
            ) {
                g2.setStroke(longDashStroke);
                g2.draw(
                    new Line2D.Float(
                        lyricsX - LONG_DASH_WIDTH - 10,
                        dashY,
                        lyricsX - 10,
                        dashY
                    )
                );
            }
        }

        if (
            (drawnIndex <= noteIndex) &&
                ((note.acceleration.syllableRelation != Note.SyllableRelation.NO) ||
                    ((noteIndex == 0) &&
                        (line.beginRelation == Note.SyllableRelation.EXTENDER)))
        ) {
            var relation = (note.acceleration.syllableRelation !=
                Note.SyllableRelation.NO)
                ? note.acceleration.syllableRelation
                : line.beginRelation;
            int c;

            if (
                (relation == Note.SyllableRelation.DASH) ||
                    (relation == Note.SyllableRelation.ONE_DASH)
            ) {
                for (
                    c = noteIndex + 1;
                    (c < line.noteCount()) &&
                        (line
                            .getNote(c)
                            .acceleration.syllable.equals(
                                Constants.UNDERSCORE
                            ) ||
                            line.getNote(c).acceleration.syllable.isEmpty());
                ) {
                    c++;
                }
            } else {
                for (
                    c = noteIndex;
                    (c < line.noteCount()) &&
                        ((line.getNote(c).acceleration.syllableRelation ==
                            relation) ||
                            line.getNote(c).acceleration.syllable.isEmpty());
                ) {
                    c++;
                }
            }

            drawnIndex = c;

            var startX = ((noteIndex == 0) &&
                (line.beginRelation == Note.SyllableRelation.EXTENDER))
                ? (note.getXPos() - 10)
                : (note.getXPos() +
                Note.HOT_SPOT.x +
                (syllableWidth / 2) +
                note.getSyllableMovement() +
                2);
            int endX;

            if (
                c == line.noteCount()
                /* || c==line.noteCount()-1 && l+1<composition.lineCount() && composition
        .getLine(l+1).beginRelation!=Note.SyllableRelation.NO*/
            ) {
                endX = (relation == Note.SyllableRelation.ONE_DASH)
                    ? (startX + (int) (LONG_DASH_WIDTH * 2f))
                    : composition.getLineWidth();
            } else {
                if (relation == Note.SyllableRelation.EXTENDER) {
                    endX = line.getNote(c).getXPos() + 12;
                } else if (
                    (relation == Note.SyllableRelation.ONE_DASH) &&
                        line.getNote(c).acceleration.syllable.isEmpty()
                ) {
                    endX = startX + (int) (LONG_DASH_WIDTH * 2f);
                } else {
                    endX = (((line.getNote(c).getXPos() + Note.HOT_SPOT.x) -
                        (g2
                            .getFontMetrics()
                            .stringWidth(
                                line.getNote(c).acceleration.syllable
                            ) /
                            2)) +
                        line.getNote(c).getSyllableMovement()) -
                        2;
                }
            }

            if (relation == Note.SyllableRelation.DASH) {
                g2.setStroke(dashStroke);
                var dashPhase =
                    dashStroke.getDashArray()[0] + dashStroke.getDashArray()[1];
                var length = Math.round(
                    ((float) Math.floor(
                        (endX - startX - dashStroke.getDashArray()[1]) /
                            dashPhase
                    ) *
                        dashPhase) +
                        dashStroke.getDashArray()[0]
                );
                var gap = (endX - startX - length) / 2;
                drawWithEmptySyllablesExclusion(
                    g2,
                    startX + gap,
                    (int) dashY,
                    endX - gap,
                    (int) dashY,
                    line,
                    noteIndex,
                    c + 1
                );
            } else if (relation == Note.SyllableRelation.EXTENDER) {
                g2.setStroke(underScoreStroke);
                drawWithEmptySyllablesExclusion(
                    g2,
                    startX,
                    lyricsY,
                    endX,
                    lyricsY,
                    line,
                    noteIndex,
                    c + 1
                );
            } else if (relation == Note.SyllableRelation.ONE_DASH) {
                g2.setStroke(longDashStroke);
                note.acceleration.longDashPosition = ((endX - startX) / 2f) +
                    startX;
                var centerX = (note.getSyllableRelationMovement() == 0)
                    ? note.acceleration.longDashPosition
                    : (note.getXPos() + note.getSyllableRelationMovement());
                g2.draw(
                    new Line2D.Float(
                        centerX - (LONG_DASH_WIDTH / 2f),
                        dashY,
                        centerX + (LONG_DASH_WIDTH / 2f),
                        dashY
                    )
                );
            }
        }

        return drawnIndex;
    }

    private void drawTie(
        Graphics2D g2,
        int lineIndex,
        @NotNull Line line,
        int noteIndex,
        Note note
    ) {
        var tieVal = line.getTies().findInterval(noteIndex);

        if ((tieVal != null) && (noteIndex != tieVal.getEnd())) {
            var tieUpper = note.isUpper();
            var xPos = note.getXPos() + getHalfNoteWidthForTie(note) + 2;
            var gap =
                (line.getNote(noteIndex + 1).getXPos() +
                    getHalfNoteWidthForTie(line.getNote(noteIndex + 1))) -
                    xPos -
                    3;

            if (
                (note.isUpper() != line.getNote(noteIndex + 1).isUpper()) &&
                    note.isUpper()
            ) {
                tieUpper = false;
                xPos += 7;
                gap -= 5;
            }

            var yPos =
                score.getNoteYPos(note.getYPos(), lineIndex) +
                    (tieUpper
                        ? ((Score.STAFF_LINE_Y_OFFSET / 2) + 2)
                        : ((-Score.STAFF_LINE_Y_OFFSET / 2) - 2));
            g2.setStroke(lineStroke);
            var tie = new GeneralPath(Path2D.WIND_NON_ZERO, 2);
            tie.moveTo(xPos, yPos);
            tie.quadTo(
                xPos + ((float) gap / 2),
                yPos + (tieUpper ? 6 : -6),
                xPos + gap,
                yPos
            );
            tie.quadTo(
                xPos + ((float) gap / 2),
                yPos + (tieUpper ? 8 : -8),
                xPos,
                yPos
            );
            tie.closePath();
            g2.draw(tie);
            g2.fill(tie);
        }
    }

    private void drawStaffLines(@NotNull Graphics2D g2, int lineIndex) {
        var color = g2.getColor();
        g2.setColor(
            (lineIndex != score.getSelectedLine())
                ? Color.black
                : SELECTION_STROKE_COLOR
        );
        g2.setStroke(lineStroke);

        // Don't antialias horizontal staff lines, they are always on pixel boundaries
        var antialias = g2.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g2.setRenderingHint(
            RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_OFF
        );

        // Draw the horizontal lines
        for (var i = -2; i <= 2; i++) {
            g2.drawLine(
                0,
                score.getNoteYPos(i * 2, lineIndex),
                composition.getLineWidth(),
                score.getNoteYPos(i * 2, lineIndex)
            );
        }

        // Draw the vertical line at the beginning of the staff, which is a little thicker than the horizontal lines
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, antialias);
        g2.setStroke(barLineStroke);
        g2.drawLine(
            1,
            score.getNoteYPos(-4, lineIndex),
            1,
            score.getNoteYPos(4, lineIndex)
        );

        g2.setColor(color);
    }

    protected static void drawBarLine(@NotNull Graphics2D g2, NoteType type) {
        var at = g2.getTransform();

        if (type == NoteType.DOUBLE_BARLINE) {
            g2.setStroke(thinLineStroke);
            g2.draw(barLine);
            g2.translate(-barLineSpace - thinLineStroke.getLineWidth(), 0);
        } else if (type == NoteType.FINAL_DOUBLE_BARLINE) {
            g2.setStroke(heavyLineStroke);
            g2.translate(-heavyLineStroke.getLineWidth() / 2f, 0);
            g2.draw(barLine);
            g2.translate(
                -barLineSpace -
                    (heavyLineStroke.getLineWidth() / 2f) -
                    (thinLineStroke.getLineWidth() / 2f),
                0
            );
        }

        g2.setStroke(thinLineStroke);
        g2.draw(barLine);

        g2.setTransform(at);
    }

    protected static void drawRepeat(
        @NotNull Graphics2D g2,
        double thickStart,
        double direction,
        boolean drawThick
    ) {
        var at = g2.getTransform();
        g2.translate(thickStart, 0);

        // align repeat bottom with staff line
        var offset = lineStroke.getLineWidth() / 2d;
        var line = new Line2D.Double(
            verticalLine.x1,
            verticalLine.y1,
            verticalLine.x2,
            verticalLine.y2 + offset
        );

        if (drawThick) {
            g2.setStroke(repeatHeavyStroke);
            g2.draw(line);
        }

        g2.setStroke(repeatThinStroke);
        g2.translate(repeatThickThinDiff * direction, 0);
        g2.draw(line);

        g2.translate(
            (repeatThinCircleDiff * direction) - (repeatCircle1.width / 2),
            0
        );
        g2.fill(repeatCircle1);
        g2.fill(repeatCircle2);
        g2.setTransform(at);
    }

    private void drawUnderLyrics(Graphics2D g2) {
        var lyrics = composition.getUnderLyrics();
        var banglaLyrics = composition.getBanglaLyrics();
        var translation = composition.getTranslatedLyrics();

        if (lyrics.isEmpty() && banglaLyrics.isEmpty() && translation.isEmpty()) {
            return;
        }

        // Calculate union width across all three text blocks
        var unionWidth = calculateLyricsUnionWidth(g2);

        // Center the union box horizontally on the page
        var lyricsX = (float) Math.round(
            (composition.getLineWidth() - unionWidth) / 2
        );

        // Start position for lyrics block
        var currentY = (float) score.getUnderLyricsYPos();
        var hasDrawnContent = false;

        // Draw lyrics block (if present)
        if (!lyrics.isEmpty()) {
            g2.setFont(composition.getLyricsFont());
            currentY = drawTextBox(g2, lyrics, lyricsX, currentY, 0f);
            lyricsMaxY = currentY;
            hasDrawnContent = true;
        }

        // Draw Bangla lyrics block (if present)
        if (!banglaLyrics.isEmpty()) {
            g2.setFont(composition.getBanglaFont());

            // Add margin only if there was content above
            if (hasDrawnContent) {
                currentY = lyricsMaxY + BANGLA_LYRICS_TOP_MARGIN;
                // Convert from bottom position to baseline by adding ascent
                currentY += g2.getFontMetrics().getAscent();
            }

            currentY = drawTextBox(g2, banglaLyrics, lyricsX, currentY, 0f);
            lyricsMaxY = currentY;
            hasDrawnContent = true;
        }

        // Draw translation block (if present)
        if (!translation.isEmpty()) {
            // Add margin only if there was content above
            if (hasDrawnContent) {
                currentY = lyricsMaxY + TRANSLATION_TOP_MARGIN;
            }

            currentY = drawTranslationBlock(g2, translation, lyricsX, currentY);
            lyricsMaxY = currentY;
        }
    }

    /**
     * Draws the translation block with a header line.
     *
     * @param g2          Graphics context
     * @param translation The translation text
     * @param x           X position (left-aligned)
     * @param y           Y position (bottom of previous content + margin)
     * @return Y position after the translation block (bottom)
     */
    private float drawTranslationBlock(
        Graphics2D g2,
        String translation,
        float x,
        float y
    ) {
        var lyricsFont = composition.getLyricsFont();

        // Draw header line in bold
        var headerFont = lyricsFont.deriveFont(
            Font.BOLD,
            lyricsFont.getSize2D()
        );
        g2.setFont(headerFont);

        var headerText = composition.isUnofficialTranslation()
            ? TRANSLATION_HEADER_UNOFFICIAL
            : TRANSLATION_HEADER_OFFICIAL;

        var metrics = g2.getFontMetrics();
        var lineHeight = metrics.getHeight();

        // y comes in as the bottom of previous content + margin
        // Convert to baseline by adding ascent (text draws from baseline)
        y += metrics.getAscent();
        drawString(g2, headerText, x, y);
        y += lineHeight;

        // Add margin below header (1/4 of font size)
        y += lyricsFont.getSize2D() / 4f;

        // Draw translation text in regular lyrics font
        g2.setFont(lyricsFont);
        return drawTextBox(g2, translation, x, y, 0f);
    }

    /**
     * Calculates the union width across lyrics, Bangla lyrics, and translation.
     * This width is used to center all three blocks horizontally.
     */
    private double calculateLyricsUnionWidth(Graphics2D g2) {
        var maxWidth = 0d;
        var lyrics = composition.getUnderLyrics();
        var banglaLyrics = composition.getBanglaLyrics();
        var translation = composition.getTranslatedLyrics();

        if (!lyrics.isEmpty()) {
            g2.setFont(composition.getLyricsFont());
            maxWidth = GraphicUtils.getTextBlockWidth(lyrics, g2);
        }

        if (!banglaLyrics.isEmpty()) {
            g2.setFont(composition.getBanglaFont());
            maxWidth = Math.max(
                maxWidth,
                GraphicUtils.getTextBlockWidth(banglaLyrics, g2)
            );
        }

        if (!translation.isEmpty()) {
            // Include header width in translation measurement
            var lyricsFont = composition.getLyricsFont();
            var headerFont = lyricsFont.deriveFont(
                Font.BOLD,
                lyricsFont.getSize2D()
            );

            g2.setFont(headerFont);
            var headerText = composition.isUnofficialTranslation()
                ? TRANSLATION_HEADER_UNOFFICIAL
                : TRANSLATION_HEADER_OFFICIAL;
            maxWidth = Math.max(
                maxWidth,
                GraphicUtils.getTextBlockWidth(headerText, g2)
            );

            g2.setFont(lyricsFont);
            maxWidth = Math.max(
                maxWidth,
                GraphicUtils.getTextBlockWidth(translation, g2)
            );
        }

        return maxWidth;
    }

    // Note: drawBanglaLyrics() is no longer needed as a separate method
    // Bangla lyrics are now drawn inline within drawUnderLyrics()

    private void drawFootnotes(Graphics2D g2) {
        var footnotesBounds = score.getLayoutManager().getFootnotesBounds();

        // LayoutManager returns empty bounds if footnotes shouldn't be drawn
        if (footnotesBounds == null || footnotesBounds.height == 0) {
            return;
        }

        var footnotes = composition.getFootnotes();

        if (footnotes.isEmpty()) {
            return;
        }

        g2.setFont(composition.getFootnoteFont());
        var metrics = g2.getFontMetrics();

        // Max width is 2/3 of line width
        var maxWidth = (composition.getLineWidth() * 2) / 3;

        // Center horizontally with max 2/3 page width
        var actualWidth = Math.min(footnotesBounds.width, maxWidth);
        var x = (float) Math.round(
            (composition.getLineWidth() - actualWidth) / 2
        );

        // Convert Y position from top-of-box to baseline
        var y = footnotesBounds.y + metrics.getAscent();

        // Draw the footnotes
        drawTextBox(g2, footnotes, x, y, 0f);
    }

    private static void drawWithEmptySyllablesExclusion(
        Graphics2D g2,
        int x1,
        int y1,
        int x2,
        int y2,
        @NotNull Line line,
        int startIndex,
        int endIndex
    ) {
        var end = Math.min(line.noteCount(), endIndex);
        var emptySyllables = IntStream.range(startIndex, end)
            .filter(i -> line.getNote(i).acceleration.syllable.isEmpty())
            .boxed()
            .collect(Collectors.toCollection(ArrayList::new));

        if (emptySyllables.isEmpty()) {
            g2.drawLine(x1, y1, x2, y2);
        } else {
            var intervalSet = new IntervalSet();
            intervalSet.addInterval(startIndex, end);

            for (var i : emptySyllables) {
                intervalSet.removeInterval(i, i + 1);
            }

            for (
                var intervalListIterator = intervalSet.listIterator();
                intervalListIterator.hasNext();
            ) {
                var interval = intervalListIterator.next();
                var drawX1 = (interval.getStart() == startIndex)
                    ? x1
                    : line.getNote(interval.getStart()).getXPos();
                var drawX2 = (interval.getEnd() == end)
                    ? x2
                    : (line.getNote(interval.getEnd() - 1).getXPos() + 12);
                g2.drawLine(drawX1, y1, drawX2, y2);
            }
        }
    }

    public int getAnnotationYPos(int l, @NotNull Note note) {
        return score.getNoteYPos(0, l) + note.getAnnotation().getYPos();
    }

    public double getAnnotationXPos(Graphics2D g2, @NotNull Note note) {
        var a = note.getAnnotation();
        var xPos = note.getXPos() + (crotchetWidth / 2);
        var font = composition.getAnnotationFont();

        if (a.getXAlignment() == Component.CENTER_ALIGNMENT) {
            xPos -=
                (double) g2.getFontMetrics(font).stringWidth(a.getAnnotation()) / 2;
        } else if (a.getXAlignment() == Component.RIGHT_ALIGNMENT) {
            xPos -= g2.getFontMetrics(font).stringWidth(a.getAnnotation());
        }

        return xPos;
    }

    private int getHalfNoteWidthForTie(@NotNull Note note) {
        if (
            (note.getNoteType() == NoteType.SEMIBREVE) ||
                (note.getNoteType() == NoteType.MINIM)
        ) {
            return note.getRealUpNoteRect().width / 2;
        }

        return (int) Math.round(crotchetWidth / 2);
    }

    private static boolean isNoteTypeInLevel(
        @NotNull Line line,
        int noteIndex,
        int level
    ) {
        var type = line.getNote(noteIndex).getNoteType();

        if (!type.isGraceNote()) {
            for (var i = 0; i < BEAM_LEVELS.length; i++) {
                if (BEAM_LEVELS[i] == type) {
                    return i <= level;
                }
            }

            return false;
        }

        var begin = noteIndex - 1;
        var end = noteIndex + 1;

        while ((begin > 0) && line.getNote(begin).getNoteType().isGraceNote()) {
            begin--;
        }

        while (
            (end < line.noteCount()) &&
                line.getNote(end).getNoteType().isGraceNote()
        ) {
            end++;
        }

        return (
            (begin >= 0) &&
                isNoteTypeInLevel(line, begin, level) &&
                (end < line.noteCount()) &&
                isNoteTypeInLevel(line, end, level)
        );
    }

    private void drawBeams(
        Graphics2D g2,
        int level,
        Line line,
        int lineIndex,
        int beginIndex,
        int endIndex,
        boolean drawEditingComponents
    ) {
        var outerNotes = new Point(beginIndex, endIndex);
        doDrawBeams(
            g2,
            level,
            line,
            lineIndex,
            outerNotes,
            beginIndex,
            endIndex,
            beginIndex,
            endIndex,
            false,
            0,
            drawEditingComponents
        );
    }

    private void doDrawBeams(
        Graphics2D g2,
        int level,
        Line line,
        int lineIndex,
        Point outerNotes,
        int beginIndex,
        int endIndex,
        int prevBeginIndex,
        int prevEndIndex,
        boolean isPrevLeftOriented,
        int recursionLevel,
        boolean drawEditingComponents
    ) {
        if (level == -1) {
            return;
        }

        var beginNote = line.getNote(beginIndex);
        var isUpper = beginNote.isUpper();
        var leftOriented = false;

        // Half beam
        if (beginIndex == endIndex) {
            if (beginNote.getNoteType().isGraceNote()) {
                return;
            }

            // NOTE: left oriented actually means the beam is attached to the right hand note stem
            leftOriented = (prevBeginIndex == prevEndIndex)
                ? isPrevLeftOriented
                : ((beginIndex == prevBeginIndex) ==
                beginNote.isInvertFractionBeamOrientation());

            int begin, end;

            if (leftOriented) {
                begin = outerNotes.x;
                end = endIndex;
            } else {
                begin = beginIndex;
                end = outerNotes.y;
            }

            var type = leftOriented
                ? BeamType.ATTACH_RIGHT
                : BeamType.ATTACH_LEFT;

            drawBeam(
                g2,
                line,
                lineIndex,
                begin,
                end,
                isUpper,
                type,
                recursionLevel,
                drawEditingComponents
            );
        }
        // Top beam
        else {
            drawBeam(
                g2,
                line,
                lineIndex,
                beginIndex,
                endIndex,
                isUpper,
                BeamType.FULL,
                recursionLevel,
                drawEditingComponents
            );
        }

        // Sub-beams
        var beamLevel = level - 1;
        var startSubBeam = -1;

        for (var i = beginIndex; i <= (endIndex + 1); i++) {
            if ((i <= endIndex) && isNoteTypeInLevel(line, i, beamLevel)) {
                if (startSubBeam == -1) {
                    startSubBeam = i;
                }
            } else if (startSubBeam != -1) {
                doDrawBeams(
                    g2,
                    beamLevel,
                    line,
                    lineIndex,
                    outerNotes,
                    startSubBeam,
                    i - 1,
                    beginIndex,
                    endIndex,
                    leftOriented,
                    recursionLevel + 1,
                    drawEditingComponents
                );
                startSubBeam = -1;
            }
        }
    }

    private void drawBeam(
        @NotNull Graphics2D g2,
        @NotNull Line line,
        int lineIndex,
        int beginIndex,
        int endIndex,
        boolean isUpper,
        BeamType type,
        int recursionLevel,
        boolean drawEditingComponents
    ) {
        /*
            We draw beams using a Path2D. Because of rendering inaccuracies with antialiasing,
            we first stroke the path with the stem stroke so that the antialiasing at the edge
            will match the stem antialiasing, then we fill the path.

            TODO: This may no longer be necessary once offscreen buffers are removed.
        */
        var oldColor = g2.getColor();
        var beginNote = line.getNote(beginIndex);
        var endNote = line.getNote(endIndex);
        var firstStem = beginNote.acceleration.stem;
        var lastStem = endNote.acceleration.stem;

        // Reduce the height top and bottom by half the stem stroke width since we stroke the path
        var halfStemWidth = stemStroke.getLineWidth() / 2;
        var halfBeamWidth = (beamStroke.getLineWidth() / 2) - halfStemWidth;

        var noteX = beginNote.getXPos();
        var firstX = firstStem.x1 + noteX;

        var yOffset = INNER_BEAM_OFFSET * recursionLevel * (isUpper ? 1 : -1);
        var noteY = score.getNoteYPos(beginNote.getYPos(), lineIndex) + yOffset;
        var firstY =
            (noteY + firstStem.y1) - beginNote.acceleration.lengthening;

        if (isUpper) {
            firstY += -Note.HOT_SPOT.y + halfBeamWidth;
        } else {
            firstY += Note.HOT_SPOT.y - halfBeamWidth;
        }

        // Bottom left
        var beam = new Path2D.Double(Path2D.WIND_NON_ZERO, 4);
        beam.moveTo(firstX, firstY + halfBeamWidth);
        // Top left
        beam.lineTo(firstX, firstY - halfBeamWidth);

        noteX = endNote.getXPos();
        noteY = score.getNoteYPos(endNote.getYPos(), lineIndex) + yOffset;
        var lastX = lastStem.x1 + noteX;
        var lastY = (noteY + lastStem.y1) - endNote.acceleration.lengthening;

        if (isUpper) {
            lastY += -Note.HOT_SPOT.y + halfBeamWidth;
        } else {
            lastY += Note.HOT_SPOT.y - halfBeamWidth;
        }

        // Top right
        beam.lineTo(lastX, lastY - halfBeamWidth);
        // Bottom right
        beam.lineTo(lastX, lastY + halfBeamWidth);
        beam.closePath();

        Shape oldClip = null;
        Rectangle2D clip = null;

        if (type != BeamType.FULL) {
            // If drawing an inner beam, clip the beam to the inner beam length,
            // leaving slop on the stem side to account for inaccuracies when printing.
            clip = beam.getBounds2D();
            var clipSlop = 2d;
            double x1;

            if (type == BeamType.ATTACH_LEFT) {
                x1 = firstX - clipSlop;
            } else { // type == BeamType.ATTACH_RIGHT
                x1 = lastX - INNER_BEAM_LENGTH;
            }

            clip.setRect(
                x1,
                clip.getMinY() - clipSlop,
                INNER_BEAM_LENGTH + clipSlop,
                clip.getHeight() + (clipSlop * 2)
            );
            oldClip = g2.getClip();
            g2.setClip(clip);
        }

        g2.setColor(
            getPaintColorForNoteInterval(
                lineIndex,
                beginIndex,
                endIndex,
                false,
                drawEditingComponents
            )
        );
        var stroke = g2.getStroke();
        g2.setStroke(stemStroke);
        g2.draw(beam);
        g2.fill(beam);
        g2.setStroke(stroke);

        if (clip != null) {
            g2.setClip(oldClip);
        }

        g2.setColor(oldColor);
    }

    public void drawGlissando(
        Graphics2D g2,
        int xIndex,
        Note.Glissando glissando,
        int lineIndex
    ) {
        var line = composition.getLine(lineIndex);
        var x1 = getGlissandoX1Pos(xIndex, glissando, lineIndex);
        var x2 = getGlissandoX2Pos(xIndex, glissando, lineIndex);
        drawGlissando(
            g2,
            x1,
            score.getNoteYPos(line.getNote(xIndex).getYPos(), lineIndex),
            x2,
            score.getNoteYPos(glissando.pitch, lineIndex)
        );

        g2.setStroke(lineStroke);
        //drawing the stave-longitude
        // TODO: check with Tanima
        //        if (Math.abs(glissando.pitch) > 5 && (xIndex+1==line.noteCount() ||
        //                Math.abs(line.getNote(xIndex+1).getYPos())<Math.abs(glissando.pitch))) {
        //            for (int i = glissando.pitch + (glissando.pitch % 2 == 0 ? 0 : glissando.pitch
        //            > 0 ? -1 : 1); Math.abs(i) > 5; i += glissando.pitch > 0 ? -2 : 2)
        //                g2.drawLine(x2-5, ms.getNoteYPos(i, l),
        //                        x2+5, ms.getNoteYPos(i, l));
        //        }
    }

    public int getGlissandoX1Pos(
        int xIndex,
        @NotNull Note.Glissando glissando,
        int lineIndex
    ) {
        var line = composition.getLine(lineIndex);
        var note = line.getNote(xIndex);
        var x1 = note.getXPos() + 15 + glissando.x1Translate;
        var noteType = note.getNoteType();

        if (noteType == NoteType.SEMIBREVE) {
            x1 += 3;
        } else if (noteType.isGraceNote()) {
            x1 -= 3;

            if (noteType == NoteType.GRACE_SEMIQUAVER) {
                x1 += ((GraceSemiQuaver) note).getX2DiffPos();
            }
        }

        x1 += note.getDotCount() * 6;
        return x1;
    }

    public int getGlissandoX2Pos(
        int xIndex,
        @NotNull Note.Glissando glissando,
        int lineIndex
    ) {
        var line = composition.getLine(lineIndex);
        float x2 = -glissando.x2Translate;

        if ((xIndex + 1) < line.noteCount()) {
            x2 += line.getNote(xIndex + 1).getXPos() - 3;
            var accNum = line.getNote(xIndex + 1).getAccidental().ordinal();

            if (accNum > 0) {
                x2 -=
                    FughettaRenderer.getAccidentalWidth(line.getNote(xIndex + 1));
                x2 -= 1.6F;
            }
        } else {
            x2 += line.getNote(xIndex).getXPos() + 45;
        }
        return Math.round(x2);
    }

    protected int getFermataYPos(@NotNull Note note) {
        if (note.isUpper() && (note.getYPos() < 2)) {
            return note.getYPos() - 11;
        }
        if (!note.isUpper() && (note.getYPos() < -4)) {
            return note.getYPos() - 5;
        }
        return -9;
    }

    private void drawGlissando(
        @NotNull Graphics2D g2,
        int x1,
        int y1,
        int x2,
        int y2
    ) {
        var l = Math.sqrt(
            (Math.abs(x1 - x2) * Math.abs(x1 - x2)) +
                (Math.abs(y1 - y2) * Math.abs(y1 - y2))
        );
        var m = (int) Math.round(l / GLISSANDO_LENGTH);
        m = Math.max(2, m); // minimum two glissando parts
        g2.setFont(fughetta);
        var at = g2.getTransform();
        g2.translate(x1, y1 + 2.25d);
        g2.rotate(Math.atan((double) (y2 - y1) / (double) (x2 - x1)));
        var scale = l / GLISSANDO_LENGTH / m;
        g2.scale(scale, 1d);

        for (var i = 0; i < m; i++) {
            drawString(
                g2,
                GLISSANDO,
                (int) Math.round(i * GLISSANDO_LENGTH),
                0
            );
        }

        g2.setTransform(at);
    }

    // TODO: Rewrite to use Bravura for the note
    private void drawTempoChange(
        Graphics2D g2,
        @NotNull Tempo tempo,
        int lineIndex,
        int noteIndex
    ) {
        var line = composition.getLine(lineIndex);
        var note = line.getNote(noteIndex);
        var yPos =
            score.getMiddleLineY() +
                line.getTempoChangeYPos() +
                (lineIndex * score.getRowHeight());
        var tempoBuilder = new StringBuilder(25);
        var tempoTypeNote = tempo.getTempoType().getNote();

        if (tempo.shouldShowTempo()) {
            drawTempoChangeNote(g2, tempoTypeNote, note.getXPos(), yPos);
            tempoBuilder.append("= ");
            tempoBuilder.append(tempo.getVisibleTempo());
            tempoBuilder.append(' ');
        }

        tempoBuilder.append(tempo.getTempoDescription());
        g2.setFont(composition.getAttributionFont());
        var xPos = (double) note.getXPos();

        if (tempo.shouldShowTempo()) {
            var extraOffset = 0;

            if (
                (tempoTypeNote.getDotCount() == 1) ||
                    (tempoTypeNote.getNoteType() == NoteType.QUAVER)
            ) {
                extraOffset = 6;
            }

            xPos += crotchetWidth + 5 + extraOffset;
        }

        drawString(g2, tempoBuilder.toString(), (float) xPos, (float) yPos);
    }

    private void drawBeatChange(
        Graphics2D g2,
        int lineIndex,
        @NotNull Note note
    ) {
        var beatChange = note.getBeatChange();
        var yPos =
            score.getNoteYPos(0, lineIndex) +
                composition.getLine(lineIndex).getBeatChangeYPos();
        drawBeatChange(g2, beatChange, note.getXPos(), yPos);
    }

    public void drawBeatChange(
        Graphics2D g2,
        @NotNull BeatChange beatChange,
        int xPos,
        int yPos
    ) {
        drawTempoChangeNote(g2, beatChange.getFirstNote(), xPos, yPos);
        g2.setFont(composition.getAttributionFont());
        var eqXPos = xPos + crotchetWidth + 7;
        drawString(g2, "=", (float) eqXPos, yPos);
        drawTempoChangeNote(
            g2,
            beatChange.getSecondNote(),
            (int) Math.round(eqXPos + 12),
            yPos
        );
    }

    protected void drawEnding(
        Graphics2D g2,
        @NotNull Line line,
        int lineIndex,
        double x1,
        double x2,
        int number
    ) {
        var y =
            score.getNoteYPos(0, lineIndex) + line.getFirstSecondEndingYPos();
        var fontHeight = firstSecondEndingFont.getSize() + 2;

        var bracket = new Path2D.Double();
        bracket.moveTo(x1, y);
        bracket.lineTo(x1, y - fontHeight);
        bracket.lineTo(x2, y - fontHeight);

        if (number == 1) {
            bracket.lineTo(x2, y);
        }

        g2.setStroke(stemStroke);
        g2.draw(bracket);

        g2.setFont(firstSecondEndingFont);
        drawString(g2, Integer.toString(number), (float) x1 + 4, y - 3);
    }

    protected void drawArticulation(
        Graphics2D g2,
        @NotNull Note note,
        int lineIndex
    ) {
        // todo exact y2 for all articulations
        // draw the accent
        var xPos = note.getXPos();

        if (note.getForceArticulation() == ForceArticulation.ACCENT) {
            var x2 = xPos + (int) crotchetWidth + 2;
            var y = getY(note, lineIndex);

            // g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints
            // .VALUE_ANTIALIAS_ON);
            g2.drawLine(xPos, y - 3, x2, y);
            g2.drawLine(xPos, y + 3, x2, y);
        }

        var dir = note.isUpper() ? 1 : -1;
        var durY = score.getNoteYPos(
            note.getYPos() + (dir * 2) + (dir * (1 - (note.getYPos() % 2))),
            lineIndex
        );

        if (note.getDurationArticulation() == DurationArticulation.STACCATO) {
            var at = g2.getTransform();
            g2.translate((xPos + getHalfNoteWidthForTie(note)) - 2, durY - 2);
            g2.fill(staccatoEllipse);
            g2.setTransform(at);
        } else if (
            note.getDurationArticulation() == DurationArticulation.TENUTO
        ) {
            g2.setStroke(tenutoStroke);
            var width = ((note.getNoteType() == NoteType.SEMIBREVE) ||
                (note.getNoteType() == NoteType.MINIM))
                ? note.getRealUpNoteRect().width
                : crotchetWidth;
            g2.draw(new Line2D.Double(xPos, durY, xPos + width, durY));
        }
    }

    private int getY(@NotNull Note note, int lineIndex) {
        int y;

        if (note.isUpper()) {
            if (note.getYPos() <= 3) {
                y = score.getNoteYPos(6, lineIndex);
            } else {
                y = score.getNoteYPos(note.getYPos() + 3, lineIndex);
            }
        } else {
            if (note.getYPos() >= -3) {
                y = score.getNoteYPos(-6, lineIndex);
            } else {
                y = score.getNoteYPos(note.getYPos() - 3, lineIndex);
            }
        }
        return y;
    }

    protected void drawString(Graphics2D g2, String str, float x, float y) {
        if (convertTextToOutlines) {
            var glyphVector = g2
                .getFont()
                .createGlyphVector(g2.getFontRenderContext(), str);
            g2.translate(x, y);
            g2.fill(glyphVector.getOutline());
            g2.translate(-x, -y);
        } else {
            g2.drawString(str, x, y);
        }
    }

    private float drawTextBox(
        @NotNull Graphics2D g2,
        @NotNull String str,
        float x,
        float y,
        float leadingAdjustment
    ) {
        var metrics = g2.getFontMetrics();
        var lineHeight = metrics.getHeight() + leadingAdjustment;
        var lines = str.trim().split("\n");

        for (var line : lines) {
            drawString(g2, line, x, y);
            y += lineHeight;
        }

        // Back y up to the bottom of the last line
        return y - metrics.getMaxAscent();
    }

    /**
     * Measures a text box without drawing it.
     *
     * @param metrics           Font metrics to use for measurement
     * @param str               The text to measure (may contain newlines)
     * @param leadingAdjustment Extra spacing to add between lines
     * @return Rectangle with width = max line width, height = total text block height
     */
    @NotNull
    protected Rectangle measureTextBox(
        @NotNull FontMetrics metrics,
        @NotNull String str,
        float leadingAdjustment
    ) {
        var lineHeight = metrics.getHeight() + leadingAdjustment;
        var lines = str.trim().split("\n");
        var maxWidth = 0;

        for (var line : lines) {
            maxWidth = Math.max(maxWidth, metrics.stringWidth(line));
        }

        var height = Math.round(lineHeight * lines.length);
        return new Rectangle(0, 0, maxWidth, height);
    }

    protected void drawGraceSemiQuaverBeam(
        @NotNull Graphics2D g2,
        @NotNull Note note,
        int lineIndex
    ) {
        // g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        var dir = note.isUpper() ? -1 : 1;
        var x1 = note.getXPos() + (note.isUpper() ? 8 : 2);
        var x2 = x1 + ((GraceSemiQuaver) note).getX2DiffPos();
        var y1Pos = ((GraceSemiQuaver) note).getY0Pos();
        var y2Pos = note.getYPos();

        g2.setStroke(graceSemiQuaverStemStroke);
        var yHead1 = score.getNoteYPos(y1Pos, lineIndex);
        var lengthening1 = Math.max((dir * (y2Pos - y1Pos)) - 2, 0);
        var yUpper1 = score.getNoteYPos(
            y1Pos + (dir * (4 + lengthening1)),
            lineIndex
        );
        g2.drawLine(x1, yHead1, x1, yUpper1 + dir);

        var lengthening2 = Math.max((dir * (y1Pos - y2Pos)) - 2, 0);
        var yUpper2 = score.getNoteYPos(
            y2Pos + (dir * (4 + lengthening2)),
            lineIndex
        );
        var yHead2 = score.getNoteYPos(y2Pos, lineIndex);
        g2.drawLine(x2, yHead2, x2, yUpper2 + dir);

        // draw the beams
        g2.setStroke(graceSemiQuaverBeamStroke);
        g2.setClip(x1, 0, x2 - x1, Integer.MAX_VALUE);
        g2.drawLine(x1, yUpper1, x2, yUpper2);
        yUpper1 -= dir * 3;
        yUpper2 -= dir * 3;
        g2.drawLine(x1, yUpper1, x2, yUpper2);
        g2.setClip(null);

        // draw the grace strike
        g2.setStroke(graceSemiQuaverStrikeStroke);
        yUpper1 += (int) ((-3 * dir * Score.NOTE_Y_OFFSET) + (dir * 5));
        yUpper2 += (int) ((dir * Score.NOTE_Y_OFFSET) + (dir * 4));
        g2.drawLine(x1 - 5, yUpper1, x2 - 3, yUpper2);

        // draw steve-longitudes
        g2.setStroke(lineStroke);
        drawStaveLongitude(g2, y1Pos, lineIndex, x1 - 8, x1 + 3);
        drawStaveLongitude(g2, y2Pos, lineIndex, x2 - 8, x2 + 3);
    }

    protected void drawStaveLongitude(
        Graphics g2,
        int yPos,
        int lineIndex,
        int x1Pos,
        int x2Pos
    ) {
        if (Math.abs(yPos) > 5) {
            var i = yPos;

            if ((yPos % 2) != 0) {
                i += (yPos > 0) ? -1 : 1;
            }

            for (var step = (yPos > 0) ? -2 : 2; Math.abs(i) > 5; i += step) {
                var y1 = score.getNoteYPos(i, lineIndex);
                g2.drawLine(x1Pos, y1, x2Pos, y1);
            }
        }
    }

    public abstract void paintNote(
        Graphics2D g2,
        Note note,
        int lineIndex,
        boolean beamed
    );

    public int paintNoteAndDecorations(
        Graphics2D g2,
        Line line,
        int lineIndex,
        boolean isLastLine,
        Note note,
        int noteIndex,
        boolean beamed,
        int lyricsDrawnIndex
    ) {
        var drawnIndex = lyricsDrawnIndex;
        paintNote(g2, note, lineIndex, beamed);
        drawTie(g2, lineIndex, line, noteIndex, note);
        drawnIndex = drawLyrics(
            g2,
            lineIndex,
            isLastLine,
            line,
            drawnIndex,
            noteIndex,
            note
        );
        drawAnnotation(g2, lineIndex, isLastLine, note);
        drawTrill(g2, lineIndex, line, noteIndex, note);
        return drawnIndex;
    }

    public int getHeight() {
        return height;
    }

    protected abstract int drawLineBeginning(
        Graphics2D g2,
        Line line,
        int lineIndex
    );

    protected abstract void drawKeySignatureChange(
        Graphics2D g2,
        int l,
        KeyType[] keyTypes,
        int[] keys,
        int[] froms,
        boolean[] isNatural
    );

    protected abstract void drawTempoChangeNote(
        Graphics2D g2,
        Note tempoNote,
        int x,
        int y
    );

    protected enum BeamType {
        FULL,
        ATTACH_LEFT,
        ATTACH_RIGHT,
    }

    private record TupletCalc(int lx, int ly, int rx, int ry) {
        float getRate(int x) {
            return (((float) (ry - ly) * (x - lx)) / (rx - lx)) + ly;
        }
    }
}
