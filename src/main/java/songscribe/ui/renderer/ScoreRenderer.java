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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;

import org.jetbrains.annotations.NotNull;

import songscribe.music.Composition;
import songscribe.music.Line;
import songscribe.ui.component.Score;
import songscribe.ui.layout.LayoutManager;
import songscribe.ui.layout.LayoutResult;
import songscribe.ui.layout.LayoutStylesheet;
import songscribe.util.GraphicUtils;
import songscribe.util.MyFontUtils;
import songscribe.util.StringUtils;

/**
 * Orchestrates full score rendering using the new ElementRenderer system.
 * <p>
 * This class replaces the monolithic {@link Renderer#drawScore} method with a
 * modular approach that delegates to specialized renderers for each element type.
 * <p>
 * Rendering sections in order:
 * <ol>
 *   <li>Title — centered, potentially wrapped</li>
 *   <li>Attribution — right-aligned composer credit</li>
 *   <li>Composition — staff lines with notes, beams, ties, etc.</li>
 *   <li>Under Lyrics — lyrics, Bangla lyrics, translation</li>
 *   <li>Footnotes — centered footnote text</li>
 * </ol>
 * <p>
 * Each line of the composition is rendered using LineComponent's rendering logic,
 * which uses the RendererRegistry to dispatch to appropriate ElementRenderers.
 */
public class ScoreRenderer {

    // ==========================================================================
    // Constants
    // ==========================================================================

    /** Maximum percentage of line width the title can occupy before wrapping. */
    private static final double TITLE_MAX_WIDTH_PERCENTAGE = 0.75;

    /** Padding between attribution and right edge. */
    private static final int ATTRIBUTION_RIGHT_PADDING = 20;

    /** Vertical spacing for Bangla lyrics (2 staff lines). */
    private static final int BANGLA_LYRICS_TOP_MARGIN = 2 * Score.STAFF_LINE_Y_OFFSET;

    /** Vertical spacing for translation block (2 staff lines). */
    private static final int TRANSLATION_TOP_MARGIN = 2 * Score.STAFF_LINE_Y_OFFSET;

    /** Translation header for official translations. */
    private static final String TRANSLATION_HEADER_OFFICIAL = "Sri Chinmoy's translation:";

    /** Translation header for unofficial translations. */
    private static final String TRANSLATION_HEADER_UNOFFICIAL = "Unofficial translation:";

    /** Stroke for staff lines. */
    private static final BasicStroke STAFF_LINE_STROKE = new BasicStroke(1f);

    // ==========================================================================
    // Instance State
    // ==========================================================================

    /** The render context providing composition and layout data. */
    private final RenderContext context;

    /** Cached title max ascent for consistent positioning. */
    private int titleMaxAscent = -1;

    /** Tracks bottom Y of rendered sections. */
    private float currentY = 0;

    // ==========================================================================
    // Constructor
    // ==========================================================================

    /**
     * Creates a ScoreRenderer for the given context.
     *
     * @param context The render context (typically a Score instance)
     */
    public ScoreRenderer(@NotNull RenderContext context) {
        this.context = context;
    }

    // ==========================================================================
    // Public Rendering API
    // ==========================================================================

    /**
     * Renders the complete score.
     * <p>
     * This is the main entry point that orchestrates all rendering. It handles:
     * <ul>
     *   <li>Setting rendering hints for quality</li>
     *   <li>Applying scale transform for exports</li>
     *   <li>Applying scroll offset for non-edit mode (exports)</li>
     *   <li>Delegating to section-specific rendering methods</li>
     * </ul>
     *
     * @param g2          Graphics context for drawing
     * @param editMode    Whether to render edit-mode elements (selection, playing note)
     * @param scale       Scale factor (1.0 = normal, &gt; 1.0 for high-DPI exports)
     */
    public void render(
        @NotNull Graphics2D g2,
        boolean editMode,
        double scale
    ) {
        // Ensure layout is calculated
        var layoutManager = context.getLayoutManager();
        var layout = layoutManager.getLayoutResult();

        if (layout == null) {
            layoutManager.measure(g2);
            layout = layoutManager.getLayoutResult();
        }

        render(g2, layout, editMode, scale);
    }

    /**
     * Renders the complete score with explicit layout.
     * <p>
     * This method allows passing a pre-calculated layout for pure rendering
     * without side effects.
     *
     * @param g2          Graphics context for drawing
     * @param layout      Pre-calculated layout result
     * @param editMode    Whether to render edit-mode elements
     * @param scale       Scale factor
     */
    public void render(
        @NotNull Graphics2D g2,
        @NotNull LayoutResult layout,
        boolean editMode,
        double scale
    ) {
        var composition = context.getComposition();

        // Set rendering hints for quality
        GraphicUtils.setRenderingHints(g2);

        // Initialize accidental widths for rendering
        NoteRenderer.initializeAccidentalWidths(g2);

        // Apply scale transform if needed
        if (scale != 1d) {
            g2.scale(scale, scale);
        }

        // Apply scroll offset for non-edit mode (exports start at top of score)
        if (!editMode) {
            g2.translate(0, -context.getStartY());
        }

        // Set default color
        g2.setColor(Color.BLACK);

        // Reset tracking
        currentY = 0;

        // Render sections in order
        renderTitle(g2, composition, layout);
        renderAttribution(g2, composition, layout);
        renderComposition(g2, composition, layout, editMode);
        renderUnderLyrics(g2, composition, layout);
        renderFootnotes(g2, composition, layout);
    }

    /**
     * Initializes legacy FughettaRenderer accidental widths.
     * <p>
     * This is needed because LayoutManager and adjustment classes still use
     * FughettaRenderer for measurement calculations. Once those are migrated
     * to the new ElementRenderer system, this method can be removed.
     */

    // ==========================================================================
    // Section Rendering
    // ==========================================================================

    /**
     * Renders the title section.
     */
    private void renderTitle(
        @NotNull Graphics2D g2,
        @NotNull Composition composition,
        @NotNull LayoutResult layout
    ) {
        var titleBounds = context.getLayoutManager().getTitleBounds();

        if (titleBounds == null || titleBounds.height == 0) {
            return;
        }

        var title = composition.getTitle();

        if (title.isEmpty()) {
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

        // Calculate max ascent including combining marks
        if (titleMaxAscent == -1) {
            var frc = g2.getFontRenderContext();
            var glyphVector = font.createGlyphVector(frc, "\u00c1"); // Á
            var bounds = glyphVector.getVisualBounds();
            titleMaxAscent = (int) Math.round(-bounds.getY());
        }

        var lineHeight = metrics.getHeight();

        // Wrap title if necessary
        var titleLines = StringUtils.wrapText(
            title,
            metrics,
            (int) (lineWidth * TITLE_MAX_WIDTH_PERCENTAGE)
        );

        // Draw from Y position in titleBounds
        var startY = titleBounds.y;

        for (var i = 0; i < titleLines.size(); i++) {
            var titleLine = titleLines.get(i);
            var titleWidth = metrics.stringWidth(titleLine);
            var x = ((float) lineWidth - titleWidth) / 2;
            var y = startY + titleMaxAscent + (i * lineHeight);
            drawString(g2, titleLine, x, y);
        }

        currentY = titleBounds.y + titleBounds.height;
    }

    /**
     * Renders the attribution section.
     */
    private void renderAttribution(
        @NotNull Graphics2D g2,
        @NotNull Composition composition,
        @NotNull LayoutResult layout
    ) {
        var attributionBounds = context.getLayoutManager().getAttributionBounds();

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
        var y = attributionBounds.y + g2.getFontMetrics().getAscent();

        drawTextBox(g2, attribution, x - ATTRIBUTION_RIGHT_PADDING, y, 0);

        currentY = attributionBounds.y + attributionBounds.height;
    }

    /**
     * Renders the composition (all staff lines with their content).
     */
    private void renderComposition(
        @NotNull Graphics2D g2,
        @NotNull Composition composition,
        @NotNull LayoutResult layout,
        boolean editMode
    ) {
        var font = composition.getLyricsFont();
        g2.setFont(font);

        var count = composition.lineCount();

        for (var i = 0; i < count; i++) {
            renderLine(g2, composition, layout, i, editMode);
        }
    }

    /**
     * Renders a single staff line with all its elements.
     * <p>
     * This method creates an ElementRenderContext and delegates to
     * the various ElementRenderers for modular rendering.
     */
    private void renderLine(
        @NotNull Graphics2D g2,
        @NotNull Composition composition,
        @NotNull LayoutResult layout,
        int lineIndex,
        boolean editMode
    ) {
        var line = composition.getLine(lineIndex);
        var lineLayout = layout.getLine(lineIndex).orElse(null);

        if (lineLayout == null) {
            return;
        }

        // Create render context for this line
        var ctx = new ElementRenderContext(composition);
        ctx.setCurrentLine(line);
        ctx.setLineIndex(lineIndex);
        ctx.setMiddleLineY(context.getNoteYPos(0, lineIndex));

        var isLastLine = (lineIndex == composition.lineCount() - 1);

        // Render in proper order (back to front)
        renderStaffLines(g2, composition, lineIndex);
        renderLineBeginning(g2, line, lineIndex, ctx);
        renderNotes(g2, line, ctx, editMode);
        renderGlissandos(g2, line, ctx);
        renderBeams(g2, line, ctx);
        renderTies(g2, line, ctx);
        renderTuplets(g2, line, ctx);
        renderKeyChanges(g2, composition, line, lineIndex, ctx);
        renderDynamics(g2, line, ctx);
        renderEndings(g2, line, lineIndex, ctx);
        renderAttachments(g2, line, ctx);
        renderLyrics(g2, line, ctx, isLastLine);
    }

    /**
     * Renders the 5 staff lines for a given line index.
     */
    private void renderStaffLines(
        @NotNull Graphics2D g2,
        @NotNull Composition composition,
        int lineIndex
    ) {
        g2.setColor(Color.BLACK);
        g2.setStroke(STAFF_LINE_STROKE);

        var lineWidth = composition.getLineWidth();
        var middleLineY = context.getNoteYPos(0, lineIndex);
        var yOffset = LayoutStylesheet.STAFF_LINE_Y_OFFSET;

        // Draw 5 lines: middle line is index 2
        for (var i = -2; i <= 2; i++) {
            var y = middleLineY + (i * yOffset);
            g2.drawLine(0, y, lineWidth, y);
        }
    }

    /**
     * Renders the line beginning (clef and key signature).
     */
    private void renderLineBeginning(
        @NotNull Graphics2D g2,
        @NotNull Line line,
        int lineIndex,
        @NotNull ElementRenderContext ctx
    ) {
        // Render treble clef
        ClefRenderer.getInstance().renderClef(g2, ctx);

        // Render key signature
        KeySignatureRenderer.getInstance().renderKeySignature(
            g2,
            line.getKeyType(),
            line.getKeyAccidentalCount(),
            context.getLeadingKeysPos(),
            ctx.getMiddleLineY(),
            ctx
        );
    }

    /**
     * Renders all notes in the line.
     */
    private void renderNotes(
        @NotNull Graphics2D g2,
        @NotNull Line line,
        @NotNull ElementRenderContext ctx,
        boolean editMode
    ) {
        var noteRenderer = NoteRenderer.getInstance();

        for (var i = 0; i < line.noteCount(); i++) {
            var note = line.getNote(i);

            // Apply color based on selection/playing state
            var color = getNoteColor(ctx.getLineIndex(), i, editMode);
            g2.setColor(color);

            noteRenderer.render(g2, note, ctx);
        }

        g2.setColor(Color.BLACK);
    }

    /**
     * Determines the color for rendering a note based on selection and playback state.
     */
    private Color getNoteColor(int lineIndex, int noteIndex, boolean editMode) {
        if (!editMode) {
            return Color.BLACK;
        }

        // Check if note is currently playing
        if (context.getPlayingLine() == lineIndex &&
            context.getPlayingNote() == noteIndex) {
            return Score.PLAYING_NOTE_COLOR;
        }

        // Check if note is selected
        if (context.isNoteSelected(noteIndex, lineIndex)) {
            return Score.SELECTION_STROKE_COLOR;
        }

        return Color.BLACK;
    }

    /**
     * Renders glissandos (wavy ornament lines) for all notes in the line.
     */
    private void renderGlissandos(
        @NotNull Graphics2D g2,
        @NotNull Line line,
        @NotNull ElementRenderContext ctx
    ) {
        GlissandoRenderer.getInstance().renderGlissandosFromLine(g2, line, ctx);
    }

    /**
     * Renders beam groups.
     */
    private void renderBeams(
        @NotNull Graphics2D g2,
        @NotNull Line line,
        @NotNull ElementRenderContext ctx
    ) {
        var beamRenderer = BeamGroupRenderer.getInstance();
        var beamings = line.getBeamings();

        for (var iter = beamings.listIterator(); iter.hasNext(); ) {
            var interval = iter.next();
            beamRenderer.renderBeams(g2, line, ctx, interval.getStart(), interval.getEnd());
        }
    }

    /**
     * Renders ties between notes.
     */
    private void renderTies(
        @NotNull Graphics2D g2,
        @NotNull Line line,
        @NotNull ElementRenderContext ctx
    ) {
        var tieRenderer = TieRenderer.getInstance();
        var ties = line.getTies();

        for (var iter = ties.listIterator(); iter.hasNext(); ) {
            var interval = iter.next();
            var startNote = line.getNote(interval.getStart());
            var endNote = line.getNote(interval.getEnd());
            tieRenderer.renderTie(g2, startNote, endNote, ctx);
        }
    }

    /**
     * Renders tuplet brackets.
     */
    private void renderTuplets(
        @NotNull Graphics2D g2,
        @NotNull Line line,
        @NotNull ElementRenderContext ctx
    ) {
        TupletRenderer.getInstance().renderTupletsFromLine(g2, line, ctx);
    }

    /**
     * Renders key signature changes at the end of a line.
     * <p>
     * When the next line has a different key signature, this renders
     * naturals (if needed) and the new key signature at the end of
     * the current line as a warning to the performer.
     */
    private void renderKeyChanges(
        @NotNull Graphics2D g2,
        @NotNull Composition composition,
        @NotNull Line line,
        int lineIndex,
        @NotNull ElementRenderContext ctx
    ) {
        // Only render if there's a next line
        if (lineIndex + 1 >= composition.lineCount()) {
            return;
        }

        var nextLine = composition.getLine(lineIndex + 1);

        // Delegate to KeySignatureRenderer
        KeySignatureRenderer.getInstance().renderKeyChange(
            g2,
            line,
            nextLine,
            composition.getLineWidth(),
            ctx
        );
    }

    /**
     * Renders crescendos and diminuendos.
     */
    private void renderDynamics(
        @NotNull Graphics2D g2,
        @NotNull Line line,
        @NotNull ElementRenderContext ctx
    ) {
        var dynamicsRenderer = DynamicsRenderer.getInstance();
        dynamicsRenderer.renderCrescendosFromLine(g2, line, ctx);
        dynamicsRenderer.renderDiminuendosFromLine(g2, line, ctx);
    }

    /**
     * Renders first/second endings.
     */
    private void renderEndings(
        @NotNull Graphics2D g2,
        @NotNull Line line,
        int lineIndex,
        @NotNull ElementRenderContext ctx
    ) {
        EndingRenderer.getInstance().renderEndings(g2, line, lineIndex, ctx);
    }

    /**
     * Renders note attachments (tempo, beat change, fermata, annotation, articulation, trill).
     */
    private void renderAttachments(
        @NotNull Graphics2D g2,
        @NotNull Line line,
        @NotNull ElementRenderContext ctx
    ) {
        var composition = ctx.getComposition();
        var tempoRenderer = TempoRenderer.getInstance();
        var beatChangeRenderer = BeatChangeRenderer.getInstance();
        var fermataRenderer = FermataRenderer.getInstance();
        var annotationRenderer = AnnotationRenderer.getInstance();
        var articulationRenderer = ArticulationRenderer.getInstance();

        for (var i = 0; i < line.noteCount(); i++) {
            var note = line.getNote(i);

            // Tempo marking
            if (note.getTempoChange() != null) {
                tempoRenderer.render(note, g2, ctx);
            } else if (ctx.getLineIndex() == 0 && i == 0) {
                // Initial tempo on first note of first line
                var initialTempo = composition.getTempo();

                if (initialTempo != null) {
                    tempoRenderer.renderInitialTempo(g2, note, initialTempo, ctx);
                }
            }

            // Beat change
            if (note.getBeatChange() != null) {
                beatChangeRenderer.render(note, g2, ctx);
            }

            // Fermata
            if (note.isFermata()) {
                fermataRenderer.render(note, g2, ctx);
            }

            // Annotation
            if (note.getAnnotation() != null) {
                annotationRenderer.render(note, g2, ctx);
            }

            // Articulations
            if (!note.getArticulations().isEmpty()) {
                articulationRenderer.render(note, g2, ctx);
            }
        }

        // Trills (may span multiple notes)
        TrillRenderer.getInstance().renderTrillsFromLine(g2, line, ctx);
    }

    /**
     * Renders per-note lyrics (syllables) under the staff.
     */
    private void renderLyrics(
        @NotNull Graphics2D g2,
        @NotNull Line line,
        @NotNull ElementRenderContext ctx,
        boolean isLastLine
    ) {
        LyricsRenderer.getInstance().renderLyrics(g2, line, ctx, isLastLine);
    }

    /**
     * Renders the under-lyrics section (lyrics, Bangla, translation).
     */
    private void renderUnderLyrics(
        @NotNull Graphics2D g2,
        @NotNull Composition composition,
        @NotNull LayoutResult layout
    ) {
        var lyrics = composition.getUnderLyrics();
        var banglaLyrics = composition.getBanglaLyrics();
        var translation = composition.getTranslatedLyrics();

        if (lyrics.isEmpty() && banglaLyrics.isEmpty() && translation.isEmpty()) {
            return;
        }

        // Calculate union width across all text blocks
        var unionWidth = calculateLyricsUnionWidth(g2, composition);

        // Center horizontally
        var lyricsX = (float) Math.round(
            (composition.getLineWidth() - unionWidth) / 2
        );

        // Start from under-lyrics Y position
        var yPos = (float) context.getUnderLyricsYPos();
        var hasDrawnContent = false;

        // Draw lyrics
        if (!lyrics.isEmpty()) {
            g2.setFont(composition.getLyricsFont());
            var baselineY = yPos + g2.getFontMetrics().getAscent();
            yPos = drawTextBox(g2, lyrics, lyricsX, baselineY, 0f);
            currentY = yPos;
            hasDrawnContent = true;
        }

        // Draw Bangla lyrics
        if (!banglaLyrics.isEmpty()) {
            g2.setFont(composition.getBanglaFont());

            if (hasDrawnContent) {
                yPos = currentY + BANGLA_LYRICS_TOP_MARGIN;
                yPos += g2.getFontMetrics().getAscent();
            } else {
                yPos += g2.getFontMetrics().getAscent();
            }

            yPos = drawTextBox(g2, banglaLyrics, lyricsX, yPos, 0f);
            currentY = yPos;
            hasDrawnContent = true;
        }

        // Draw translation
        if (!translation.isEmpty()) {
            if (hasDrawnContent) {
                yPos = currentY + TRANSLATION_TOP_MARGIN;
            }

            yPos = drawTranslationBlock(g2, composition, translation, lyricsX, yPos);
            currentY = yPos;
        }
    }

    /**
     * Calculates the maximum width across lyrics, Bangla, and translation.
     */
    private double calculateLyricsUnionWidth(
        @NotNull Graphics2D g2,
        @NotNull Composition composition
    ) {
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
            maxWidth = Math.max(maxWidth, GraphicUtils.getTextBlockWidth(banglaLyrics, g2));
        }

        if (!translation.isEmpty()) {
            var lyricsFont = composition.getLyricsFont();
            var headerFont = lyricsFont.deriveFont(Font.BOLD, lyricsFont.getSize2D());

            g2.setFont(headerFont);
            var headerText = composition.isUnofficialTranslation()
                ? TRANSLATION_HEADER_UNOFFICIAL
                : TRANSLATION_HEADER_OFFICIAL;
            maxWidth = Math.max(maxWidth, GraphicUtils.getTextBlockWidth(headerText, g2));

            g2.setFont(lyricsFont);
            maxWidth = Math.max(maxWidth, GraphicUtils.getTextBlockWidth(translation, g2));
        }

        return maxWidth;
    }

    /**
     * Draws the translation block with header.
     */
    private float drawTranslationBlock(
        @NotNull Graphics2D g2,
        @NotNull Composition composition,
        @NotNull String translation,
        float x,
        float y
    ) {
        var lyricsFont = composition.getLyricsFont();
        var headerFont = lyricsFont.deriveFont(Font.BOLD, lyricsFont.getSize2D());

        g2.setFont(headerFont);

        var headerText = composition.isUnofficialTranslation()
            ? TRANSLATION_HEADER_UNOFFICIAL
            : TRANSLATION_HEADER_OFFICIAL;

        var metrics = g2.getFontMetrics();
        var lineHeight = metrics.getHeight();

        // Convert to baseline
        y += metrics.getAscent();
        drawString(g2, headerText, x, y);
        y += lineHeight;

        // Margin below header
        y += lyricsFont.getSize2D() / 4f;

        // Draw translation text
        g2.setFont(lyricsFont);
        return drawTextBox(g2, translation, x, y, 0f);
    }

    /**
     * Renders the footnotes section.
     */
    private void renderFootnotes(
        @NotNull Graphics2D g2,
        @NotNull Composition composition,
        @NotNull LayoutResult layout
    ) {
        var footnotesBounds = context.getLayoutManager().getFootnotesBounds();

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

        // Center horizontally
        var actualWidth = Math.min(footnotesBounds.width, maxWidth);
        var x = (float) Math.round((composition.getLineWidth() - actualWidth) / 2);

        // Convert Y to baseline
        var y = footnotesBounds.y + metrics.getAscent();

        drawTextBox(g2, footnotes, x, y, 0f);
    }

    // ==========================================================================
    // Text Drawing Utilities
    // ==========================================================================

    /**
     * Draws a string at the specified position.
     */
    private void drawString(@NotNull Graphics2D g2, @NotNull String str, float x, float y) {
        g2.drawString(str, x, y);
    }

    /**
     * Draws a multi-line text box.
     *
     * @param g2    Graphics context
     * @param text  Text with newlines
     * @param x     X position
     * @param y     Y position (baseline of first line)
     * @param align Alignment (0 = left, 0.5 = center, 1 = right)
     * @return Y position after the last line
     */
    private float drawTextBox(
        @NotNull Graphics2D g2,
        @NotNull String text,
        float x,
        float y,
        float align
    ) {
        var metrics = g2.getFontMetrics();
        var lineHeight = metrics.getHeight();
        var lines = text.split("\n");

        for (var line : lines) {
            drawString(g2, line, x, y);
            y += lineHeight;
        }

        return y;
    }
}
