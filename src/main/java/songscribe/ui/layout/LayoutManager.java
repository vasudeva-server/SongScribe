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

package songscribe.ui.layout;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.util.EnumMap;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import songscribe.music.Line;
import songscribe.music.Note;
import songscribe.music.NoteType;
import songscribe.ui.component.Score;
import songscribe.ui.menu.DebugState;
import songscribe.ui.message.LayoutChangeMessage;
import songscribe.util.GraphicUtils;
import songscribe.util.StringUtils;

/**
 * Manages the vertical layout of all sections in a music score.
 * <p>
 * This class coordinates two-pass rendering (measure then draw) and maintains
 * centralized tracking of all section bounds. It replaces the existing RenderStep
 * enum and renderMap in Score.
 * <p>
 * <b>Block Flow Layout (CSS-like):</b> Sections are laid out top-to-bottom with
 * margins between them. Adjacent margins collapse to the larger value (like CSS).
 * <p>
 * Lifecycle: Single instance created in Score constructor, invalidated when
 * composition changes.
 *
 * @deprecated Replaced by new ElementRenderer system (Phase 6). Scheduled for removal in Phase 8.
 */
@Deprecated
public class LayoutManager {

    // -------------------------------------------------------------------------
    // Block Flow Margin Constants (from LayoutStylesheet)
    // -------------------------------------------------------------------------

    // Margin from title bottom to attribution top
    private static final int ATTRIBUTION_TOP_MARGIN =
        LayoutStylesheet.px(LayoutStylesheet.TITLE_MARGIN_BOTTOM);

    // Margin from attribution bottom to score top
    private static final int SCORE_TOP_MARGIN =
        LayoutStylesheet.px(LayoutStylesheet.SCORE_MARGIN_TOP);

    // Margin from score bottom to lyrics block
    private static final int LYRICS_TOP_MARGIN =
        LayoutStylesheet.px(LayoutStylesheet.LYRICS_BLOCK_MARGIN_TOP);

    // Margin from primary lyrics to Bangla lyrics
    private static final int BANGLA_LYRICS_TOP_MARGIN =
        LayoutStylesheet.px(LayoutStylesheet.BANGLA_MARGIN_TOP);

    // Margin from Bangla lyrics to translation
    private static final int TRANSLATION_TOP_MARGIN =
        LayoutStylesheet.px(LayoutStylesheet.TRANSLATION_MARGIN_TOP);

    // Minimum margin above footnotes
    private static final int FOOTNOTES_MIN_TOP_MARGIN =
        LayoutStylesheet.px(LayoutStylesheet.FOOTNOTES_MIN_MARGIN_TOP);

    // -------------------------------------------------------------------------
    // Line-Level Margin Constants (from LayoutStylesheet)
    // -------------------------------------------------------------------------

    // Margin between notes/articulations and notations above
    public static final int ANNOTATION_MARGIN =
        LayoutStylesheet.px(LayoutStylesheet.ANNOTATION_REGION_MARGIN);

    // Margin between staff bottom and in-line lyrics
    public static final int LYRICS_MARGIN =
        LayoutStylesheet.px(LayoutStylesheet.LYRICS_ROW_MARGIN);

    // Minimum margin between adjacent rows (staff lines)
    public static final int INTER_ROW_MARGIN =
        LayoutStylesheet.px(LayoutStylesheet.LINE_MARGIN_BOTTOM);

    // -------------------------------------------------------------------------
    // Title/Attribution Constants
    // -------------------------------------------------------------------------

    // The maximum percentage of line width the title can occupy before being split into lines
    private static final double TITLE_MAX_WIDTH_PERCENTAGE = 0.75;

    // The padding between attribution and the right edge of the page
    private static final int ATTRIBUTION_RIGHT_PADDING = 20;

    // Translation header text
    private static final String TRANSLATION_HEADER_OFFICIAL =
        "Sri Chinmoy's translation:";
    private static final String TRANSLATION_HEADER_UNOFFICIAL =
        "Unofficial translation:";

    // -------------------------------------------------------------------------
    // Horizontal Layout Constants
    // -------------------------------------------------------------------------

    // The number of pixels reserved for a single accidental
    public static final int ACCIDENTAL_WIDTH = 7;

    // The offset from the left edge of a staff (line) to the first note on the staff
    private static int firstNoteX = 100;

    // The default right spacing between a note and the next note in a staff line.
    // This is called "padding" for historical reasons but is actually the horizontal
    // space (margin) after the note.
    private static final EnumMap<NoteType, Integer> NOTE_SPACING =
        new EnumMap<>(NoteType.class);

    static {
        NOTE_SPACING.put(NoteType.SEMIBREVE, 70);
        NOTE_SPACING.put(NoteType.MINIM, 50);
        NOTE_SPACING.put(NoteType.CROTCHET, 35);
        NOTE_SPACING.put(NoteType.QUAVER, 25);
        NOTE_SPACING.put(NoteType.SEMIQUAVER, 25);
        NOTE_SPACING.put(NoteType.DEMI_SEMIQUAVER, 25);
        NOTE_SPACING.put(NoteType.SEMIBREVE_REST, 70);
        NOTE_SPACING.put(NoteType.MINIM_REST, 50);
        NOTE_SPACING.put(NoteType.CROTCHET_REST, 35);
        NOTE_SPACING.put(NoteType.QUAVER_REST, 25);
        NOTE_SPACING.put(NoteType.SEMIQUAVER_REST, 25);
        NOTE_SPACING.put(NoteType.DEMI_SEMIQUAVER_REST, 25);
        NOTE_SPACING.put(NoteType.GRACE_QUAVER, 30);
        NOTE_SPACING.put(NoteType.GRACE_SEMIQUAVER, 50);
        NOTE_SPACING.put(NoteType.GLISSANDO, 0);
        NOTE_SPACING.put(NoteType.REPEAT_LEFT, 25);
        NOTE_SPACING.put(NoteType.REPEAT_RIGHT, 25);
        NOTE_SPACING.put(NoteType.REPEAT_LEFT_RIGHT, 25);
        NOTE_SPACING.put(NoteType.BREATH_MARK, 15);
        NOTE_SPACING.put(NoteType.SINGLE_BARLINE, 60);
        NOTE_SPACING.put(NoteType.DOUBLE_BARLINE, 60);
        NOTE_SPACING.put(NoteType.FINAL_DOUBLE_BARLINE, 60);
        NOTE_SPACING.put(NoteType.PASTE, 0);
    }

    // -------------------------------------------------------------------------
    // Instance Fields
    // -------------------------------------------------------------------------

    private final Score score;

    private final EnumMap<LayoutChangeMessage.Section, Rectangle> sectionBounds =
        new EnumMap<>(LayoutChangeMessage.Section.class);

    private boolean valid = false;

    // Cached uniform row height; -1 means not calculated
    private int cachedUniformRowHeight = -1;

    // Cached first line distance above top line; -1 means not calculated
    private int cachedFirstLineDistanceAbove = -1;

    // Cached effective tempo Y position for first line; Integer.MAX_VALUE means not calculated
    private int cachedFirstLineEffectiveTempoYPos = Integer.MAX_VALUE;

    // Cached title max ascent (including combining marks); -1 means not calculated
    private int cachedTitleMaxAscent = -1;

    // The complete layout result from the last measure() pass
    private @Nullable LayoutResult layoutResult = null;

    public LayoutManager(@NotNull Score score) {
        this.score = score;
    }

    // -------------------------------------------------------------------------
    // Invalidation
    // -------------------------------------------------------------------------

    /**
     * Marks the entire layout as invalid, requiring full recalculation.
     */
    public void invalidate() {
        valid = false;
        cachedUniformRowHeight = -1;
        cachedFirstLineDistanceAbove = -1;
        cachedFirstLineEffectiveTempoYPos = Integer.MAX_VALUE;
        cachedTitleMaxAscent = -1;
        sectionBounds.clear();
        layoutResult = null;
    }

    /**
     * Invalidates this section and all sections below it.
     *
     * @param section The section from which to start invalidation
     */
    public void invalidateFromSection(@NotNull LayoutChangeMessage.Section section) {
        valid = false;

        // Invalidate row height cache if score section is affected
        if (section.ordinal() <= LayoutChangeMessage.Section.SCORE.ordinal()) {
            cachedUniformRowHeight = -1;
        }

        // Invalidate this section and all sections below it in the enum order
        boolean shouldInvalidate = false;

        for (LayoutChangeMessage.Section s : LayoutChangeMessage.Section.values()) {
            if (s == section) {
                shouldInvalidate = true;
            }

            if (shouldInvalidate) {
                sectionBounds.remove(s);
            }
        }
    }

    /**
     * Returns whether the layout is currently valid.
     */
    public boolean isValid() {
        return valid;
    }

    /**
     * Returns the layout result from the last measure() pass.
     * <p>
     * May be null if measure() hasn't been called yet or if the layout is invalid.
     */
    public @Nullable LayoutResult getLayoutResult() {
        return layoutResult;
    }

    // -------------------------------------------------------------------------
    // Measurement (Pass 1)
    // -------------------------------------------------------------------------

    /**
     * Performs the complete measurement pass, calculating bounds for all sections.
     * <p>
     * This method calls each renderer.measure*() method in sequence and calculates
     * the Y position for each section based on the configured margins.
     *
     * @param g2d The graphics context for measurement
     */
    public void measure(@NotNull Graphics2D g2d) {
        if (valid) {
            return;
        }

        var measurementService = score.getMeasurementService();
        var composition = score.getComposition();
        var currentY = 0;

        // Title section
        var titleBounds = measureTitle(g2d);
        titleBounds.y = 0;  // Title starts at top of page
        currentY = titleBounds.y + titleBounds.height;
        setSectionBounds(LayoutChangeMessage.Section.TITLE, titleBounds);

        // Attribution section
        var attributionBounds = measureAttribution(g2d);

        if (attributionBounds.height > 0) {
            currentY += ATTRIBUTION_TOP_MARGIN;
        }

        attributionBounds.y = currentY;
        currentY = attributionBounds.y + attributionBounds.height;
        setSectionBounds(LayoutChangeMessage.Section.ATTRIBUTION, attributionBounds);

        // Score section
        var scoreBounds = measureScore(g2d);

        // Calculate first line content extent and cache it
        var firstLine = composition.getLine(0);
        var distanceAboveFirst = measurementService.getLineDistanceAboveTopLine(g2d, firstLine, 0);
        cachedFirstLineDistanceAbove = distanceAboveFirst;

        // Also cache the effective tempo Y position for first line
        cachedFirstLineEffectiveTempoYPos = measurementService.getEffectiveTempoChangeYPos(g2d, firstLine, 0);

        // Cache the uniform row height (requires Graphics2D for articulation bounds)
        cachedUniformRowHeight = calculateUniformRowHeight(g2d);

        var staffHeight = 4 * Score.STAFF_LINE_Y_OFFSET; // 32 pixels

        // Add margin between attribution and score's topmost element
        if (attributionBounds.height > 0) {
            currentY += SCORE_TOP_MARGIN;
        }

        // Calculate where the middle line should be positioned
        // We want the topmost element to be at currentY
        // Topmost element is at: middleLineY - (staffHeight / 2) - distanceAboveFirst
        // So: middleLineY = currentY + (staffHeight / 2) + distanceAboveFirst
        var targetMiddleLineY = currentY + (staffHeight / 2) + distanceAboveFirst;

        // Update topPadding to achieve this middleLineY
        // middleLineY = topPadding + ((STAFF_LINES_ABOVE + 3) * STAFF_LINE_Y_OFFSET)
        var requiredTopPadding = targetMiddleLineY - ((Score.STAFF_LINES_ABOVE + 3) * Score.STAFF_LINE_Y_OFFSET);
        composition.setTopPadding(requiredTopPadding, false);

        // Now calculate score bounds based on the updated middle line position
        scoreBounds.y = getMiddleLineY() - (staffHeight / 2) - distanceAboveFirst;
        currentY = scoreBounds.y + scoreBounds.height;
        setSectionBounds(LayoutChangeMessage.Section.SCORE, scoreBounds);

        // Lyrics section
        var lyricsBounds = measureLyrics(g2d);

        if (lyricsBounds.height > 0) {
            currentY += LYRICS_TOP_MARGIN;
        }

        lyricsBounds.y = currentY;
        currentY = lyricsBounds.y + lyricsBounds.height;
        setSectionBounds(LayoutChangeMessage.Section.LYRICS, lyricsBounds);

        // Bangla lyrics section
        var banglaBounds = measureBanglaLyrics(g2d);

        if (banglaBounds.height > 0 && lyricsBounds.height > 0) {
            currentY += BANGLA_LYRICS_TOP_MARGIN;
        }

        banglaBounds.y = currentY;
        currentY = banglaBounds.y + banglaBounds.height;
        setSectionBounds(LayoutChangeMessage.Section.BANGLA_LYRICS, banglaBounds);

        // Translation section
        var translationBounds = measureTranslation(g2d);

        if (translationBounds.height > 0) {
            // Always add margin if there's content above (lyrics or Bangla)
            if (lyricsBounds.height > 0 || banglaBounds.height > 0) {
                currentY += TRANSLATION_TOP_MARGIN;
            }
        }

        translationBounds.y = currentY;
        currentY = translationBounds.y + translationBounds.height;
        setSectionBounds(LayoutChangeMessage.Section.TRANSLATION, translationBounds);

        // Adjust x positions for lyrics sections (all centered based on union width)
        adjustLyricsSectionPositions(
            lyricsBounds,
            banglaBounds,
            translationBounds
        );

        // Footnotes section (bottom-anchored)
        var footnotesBounds = measureFootnotes(g2d);
        var pageHeight = score.getPreferredSize().height;

        if (footnotesBounds.height > 0) {
            // Bottom-anchor the footnotes
            var footnotesY = pageHeight - footnotesBounds.height;

            // Check minimum margin above footnotes
            if ((footnotesY - currentY) >= FOOTNOTES_MIN_TOP_MARGIN) {
                footnotesBounds.y = footnotesY;
            } else {
                // Not enough space - don't draw footnotes
                footnotesBounds = new Rectangle(0, 0, 0, 0);
            }
        }

        setSectionBounds(LayoutChangeMessage.Section.FOOTNOTES, footnotesBounds);

        // Update Score's cached layout values before creating line layouts
        // This ensures bounds calculations use the correct middleLineY and rowHeight
        score.updateLayoutFromManager();

        // Create line layouts and build complete LayoutResult
        var lineLayouts = createLineLayouts(g2d);
        layoutResult = buildLayoutResult(g2d, lineLayouts);

        valid = true;
    }

    /**
     * Signals that layout calculation is complete.
     */
    public void finalizeLayout() {
        valid = true;
    }

    /**
     * Adjusts the x positions of lyrics sections to match rendering.
     * <p>
     * All three sections (lyrics, Bangla, translation) are centered based on
     * the maximum width across all three, matching how drawUnderLyrics renders them.
     */
    private void adjustLyricsSectionPositions(
        @NotNull Rectangle lyricsBounds,
        @NotNull Rectangle banglaBounds,
        @NotNull Rectangle translationBounds
    ) {
        // Find the maximum width across all three sections
        var maxWidth = Math.max(
            lyricsBounds.width,
            Math.max(banglaBounds.width, translationBounds.width)
        );

        // If no content, nothing to adjust
        if (maxWidth == 0) {
            return;
        }

        // Calculate centered x position based on union width
        var composition = score.getComposition();
        var lineWidth = composition.getLineWidth();
        var centeredX = (lineWidth - maxWidth) / 2;

        // Update x position for all three sections
        if (lyricsBounds.height > 0) {
            lyricsBounds.x = centeredX;
            lyricsBounds.width = maxWidth;
        }

        if (banglaBounds.height > 0) {
            banglaBounds.x = centeredX;
            banglaBounds.width = maxWidth;
        }

        if (translationBounds.height > 0) {
            translationBounds.x = centeredX;
            translationBounds.width = maxWidth;
        }
    }

    // -------------------------------------------------------------------------
    // Section Measurement Methods (Phase 5)
    // -------------------------------------------------------------------------

    /**
     * Measures the title without drawing it.
     * <p>
     * The title is centered horizontally and may wrap to multiple lines if it
     * exceeds {@link #TITLE_MAX_WIDTH_PERCENTAGE} of the line width.
     *
     * @param g2 Graphics context for font metrics
     * @return Rectangle with title bounds (x=centered, y=0, width=max line width, height=total height)
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
        // It isn't enough to get the max ascent from the metrics, because combining marks can be higher.
        // We need to render the tallest possible letter + combining mark to get the real ascent.
        if (cachedTitleMaxAscent == -1) {
            var context = g2.getFontRenderContext();
            var glyphVector = font.createGlyphVector(context, "Á");
            var bounds = glyphVector.getVisualBounds();
            cachedTitleMaxAscent = (int) Math.round(-bounds.getY());
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
        var height = cachedTitleMaxAscent +
            ((titleLines.size() - 1) * lineHeight) +
            metrics.getMaxDescent();

        // Center the title horizontally (matching drawTitle behavior)
        var lineWidth = comp.getLineWidth();
        var x = (lineWidth - maxWidth) / 2;

        return new Rectangle(x, 0, maxWidth, height);
    }

    /**
     * Measures the attribution text without drawing it.
     * <p>
     * Attribution is right-aligned with a fixed padding from the right edge.
     *
     * @param g2 Graphics context for font metrics
     * @return Rectangle with attribution bounds (x=right-aligned, y=0, width, height)
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

        // Right-align the attribution (matching drawAttribution behavior)
        var lineWidth = comp.getLineWidth();
        var x = lineWidth - width - ATTRIBUTION_RIGHT_PADDING;

        return new Rectangle(x, 0, width, textBounds.height);
    }

    /**
     * Measures a multi-line text block without drawing it.
     * <p>
     * This helper calculates the width and height of text that may contain newlines.
     *
     * @param metrics           Font metrics to use for measurement
     * @param str               The text to measure (may contain newlines)
     * @param leadingAdjustment Extra spacing to add between lines
     * @return Rectangle with width = max line width, height = total text block height
     */
    @NotNull
    private Rectangle measureTextBox(
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

    /**
     * Measures the lyrics block without drawing it.
     *
     * @param g2 Graphics context for font metrics
     * @return Rectangle with width and height of the lyrics block, or empty if no lyrics
     */
    @NotNull
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
    @NotNull
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
    @NotNull
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
     * <p>
     * Footnotes are bottom-anchored with max width of 2/3 page width, centered horizontally.
     *
     * @param g2 Graphics context for font metrics
     * @return Rectangle with width and height of the footnotes block
     */
    @NotNull
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

        // Center the footnotes horizontally (matching drawFootnotes behavior)
        var lineWidth = comp.getLineWidth();
        var x = (lineWidth - actualWidth) / 2;

        return new Rectangle(x, 0, actualWidth, bounds.height);
    }

    /**
     * Measures the score section without drawing it.
     * <p>
     * Calculates the total score height based on:
     * <ul>
     *   <li>First row: distance above top line + staff height</li>
     *   <li>Middle rows: (lineCount - 1) * uniformRowHeight for midline-to-midline spacing</li>
     *   <li>Last row: distance below bottom line</li>
     * </ul>
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

        // Staff height is 4 staff line units (5 lines with 4 gaps between them)
        int staffHeight = 4 * Score.STAFF_LINE_Y_OFFSET; // 32 pixels

        // First row's extent above top staff line
        var measurementService = score.getMeasurementService();
        var firstLine = comp.getLine(0);
        var distanceAboveFirst = measurementService.getLineDistanceAboveTopLine(g2, firstLine, 0);

        // Last row's extent below bottom staff line
        var lastLine = comp.getLine(lineCount - 1);
        var distanceBelowLast = measurementService.getLineDistanceBelowBottomLine(g2, lastLine, lineCount - 1);

        // Calculate uniform row height for midline-to-midline spacing
        var uniformRowHeight = calculateUniformRowHeight(g2);

        // Total height:
        // - distanceAboveFirst: space above first row's top staff line
        // - staffHeight: the first row's staff itself
        // - (lineCount - 1) * uniformRowHeight: midline-to-midline spacing for subsequent rows
        // - distanceBelowLast: space below last row's bottom staff line
        int totalHeight;

        if (lineCount == 1) {
            // Single row: just first row's content
            totalHeight = distanceAboveFirst + staffHeight + distanceBelowLast;
        } else {
            // Multiple rows: first row top to last row bottom
            totalHeight = distanceAboveFirst + staffHeight +
                ((lineCount - 1) * uniformRowHeight) +
                distanceBelowLast;
        }

        // Width is the line width from composition
        var width = comp.getLineWidth();

        return new Rectangle(0, 0, width, totalHeight);
    }

    /**
     * Calculates the uniform row height for inter-staff spacing.
     * <p>
     * This implements midline-to-midline spacing where the distance between
     * staff rows is consistent. First row is excluded from this calculation
     * since its height only affects its position, not the uniform spacing.
     * <p>
     * The uniform row height is: staffHeight + maxAbove + maxBelow + INTER_ROW_MARGIN
     * where maxAbove and maxBelow are the maximum extents across rows 1+ (for above)
     * and all rows (for below).
     *
     * @param g2 Graphics context for font metrics
     * @return The uniform row height in pixels
     */
    public int calculateUniformRowHeight(@NotNull Graphics2D g2) {
        var composition = score.getComposition();

        if (composition == null) {
            return getDefaultRowHeight();
        }

        var lineCount = composition.lineCount();

        if (lineCount == 0) {
            return getDefaultRowHeight();
        }

        // Staff height is 4 staff line units (5 lines with 4 gaps between them)
        int staffHeight = 4 * Score.STAFF_LINE_Y_OFFSET; // 32 pixels

        // Get measurement service for font-specific bounds calculations
        var measurementService = score.getMeasurementService();

        // Find maximum extent above top line (exclude first row - index 0)
        int maxAbove = 0;

        for (var i = 1; i < lineCount; i++) {
            var line = composition.getLine(i);
            maxAbove = Math.max(maxAbove, measurementService.getLineDistanceAboveTopLine(g2, line, i));
        }

        // Find maximum extent below bottom line (all rows, since row 0's below affects row 1)
        int maxBelow = 0;

        for (var i = 0; i < lineCount; i++) {
            var line = composition.getLine(i);
            maxBelow = Math.max(maxBelow, measurementService.getLineDistanceBelowBottomLine(g2, line, i));
        }

        // Calculate uniform row height
        int baseHeight = staffHeight + maxAbove + maxBelow + INTER_ROW_MARGIN;

        // Apply composition-level row height adjustment
        var adjustedHeight = baseHeight + composition.getRowHeightAdjustment();

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
    public int getDefaultRowHeight() {
        return 13 * Score.STAFF_LINE_Y_OFFSET; // 104 pixels
    }

    // -------------------------------------------------------------------------
    // Line Layout Methods (Phase 5.2)
    // -------------------------------------------------------------------------

    /**
     * Creates LineLayout objects for all lines in the composition.
     *
     * @param g2d Graphics context for font metrics
     * @return List of LineLayout objects
     */
    private List<LineLayout> createLineLayouts(@NotNull Graphics2D g2d) {
        var composition = score.getComposition();

        if (composition == null) {
            return List.of();
        }

        var lineCount = composition.lineCount();

        if (lineCount == 0) {
            return List.of();
        }

        var measurementService = score.getMeasurementService();
        var layouts = new java.util.ArrayList<LineLayout>(lineCount);

        for (var i = 0; i < lineCount; i++) {
            var line = composition.getLine(i);
            var lineLayout = createLineLayout(g2d, line, i, measurementService);
            layouts.add(lineLayout);
        }

        return layouts;
    }

    /**
     * Creates a LineLayout for a single line.
     *
     * @param g2d                Graphics context for font metrics
     * @param line               The musical line
     * @param lineIndex          Index of the line
     * @param measurementService The measurement service for bounds calculations
     * @return LineLayout for the line
     */
    private LineLayout createLineLayout(
        @NotNull Graphics2D g2d,
        @NotNull Line line,
        int lineIndex,
        @NotNull MeasurementService measurementService
    ) {
        // Calculate middle line Y for this line
        var middleLineY = getNoteYPos(0, lineIndex);

        // Get full line bounds from measurement service
        var lineBoundsRect = measurementService.getLineBounds(g2d, line, lineIndex);
        var lineBounds = ElementBounds.contentOnly(lineBoundsRect);

        // Create note layouts for all notes in this line
        var noteLayouts = createNoteLayouts(g2d, line, lineIndex, measurementService);

        // Create attachment layouts (tempo, beat change, annotations, trills, fermatas)
        var attachmentLayouts = createAttachmentLayouts(g2d, line, lineIndex, middleLineY, measurementService);

        // Create range layouts (ties, tuplets, endings, crescendo, diminuendo)
        var rangeLayouts = createRangeLayouts(g2d, line, lineIndex, middleLineY, measurementService);

        // Create lyrics layout
        var lyricsLayout = createLyricsLayout(g2d, line, lineIndex, middleLineY);

        // Debug output
        if (DebugState.isDebugEnabled()) {
            System.out.printf(
                "[Layout] Line %d: notes=%d, attachments=%d, ranges=%d, syllables=%d%n",
                lineIndex,
                noteLayouts.size(),
                attachmentLayouts.size(),
                rangeLayouts.size(),
                lyricsLayout.getSyllableCount()
            );
        }

        return new LineLayout(
            lineIndex,
            lineBounds,
            middleLineY,
            noteLayouts,
            attachmentLayouts,
            rangeLayouts,
            lyricsLayout
        );
    }

    /**
     * Creates NoteLayout objects for all notes in a line.
     *
     * @param g2d                Graphics context for font metrics
     * @param line               The musical line
     * @param lineIndex          Index of the line
     * @param measurementService The measurement service for bounds calculations
     * @return List of NoteLayout objects
     */
    private List<NoteLayout> createNoteLayouts(
        @NotNull Graphics2D g2d,
        @NotNull Line line,
        int lineIndex,
        @NotNull MeasurementService measurementService
    ) {
        var noteCount = line.noteCount();

        if (noteCount == 0) {
            return List.of();
        }

        var layouts = new java.util.ArrayList<NoteLayout>(noteCount);

        for (var i = 0; i < noteCount; i++) {
            var note = line.getNote(i);
            var noteLayout = createNoteLayout(g2d, note, i, lineIndex, measurementService);
            layouts.add(noteLayout);
        }

        return layouts;
    }

    /**
     * Creates a NoteLayout for a single note.
     *
     * @param g2d                Graphics context for font metrics
     * @param note               The note
     * @param noteIndex          Index of the note within its line
     * @param lineIndex          Index of the line
     * @param measurementService The measurement service for bounds calculations
     * @return NoteLayout for the note
     */
    private NoteLayout createNoteLayout(
        @NotNull Graphics2D g2d,
        @NotNull Note note,
        int noteIndex,
        int lineIndex,
        @NotNull MeasurementService measurementService
    ) {
        // Calculate final position
        var x = note.getXPos();
        var y = getNoteYPos(note.getYPos(), lineIndex);
        var position = new java.awt.Point(x, y);

        // Get bounds from measurement service
        var noteBoundsRect = measurementService.getNoteBounds(g2d, note, lineIndex);

        // Create NoteBounds hierarchy
        // For now, use the same bounds for all three levels
        // This can be refined later to separate head, stem, and articulation bounds
        var noteBounds = NoteBounds.headOnly(noteBoundsRect, note.isUpper());

        // Create ElementBounds with padding for hit testing
        var paddingPx = LayoutStylesheet.px(LayoutStylesheet.NOTE_PADDING);
        var elementBounds = ElementBounds.uniform(noteBoundsRect, paddingPx, 0);

        return new NoteLayout(noteIndex, position, noteBounds, elementBounds);
    }

    // -------------------------------------------------------------------------
    // Layout Creation Constants
    // -------------------------------------------------------------------------

    /** Default padding for most layout elements */
    private static final int DEFAULT_PADDING = 4;

    /** Padding for lyrics syllables */
    private static final int SYLLABLE_PADDING = 2;

    /** Default height for range elements */
    private static final int RANGE_HEIGHT = 15;

    /** Y offset for dynamics (crescendo/diminuendo) below staff */
    private static final int DYNAMICS_Y_OFFSET = 30;

    // -------------------------------------------------------------------------
    // Attachment Layout Creation
    // -------------------------------------------------------------------------

    /**
     * Creates AttachmentLayout objects for all attachments in a line.
     */
    private List<AttachmentLayout> createAttachmentLayouts(
        @NotNull Graphics2D g2d,
        @NotNull Line line,
        int lineIndex,
        int middleLineY,
        @NotNull MeasurementService measurementService
    ) {
        var attachments = new java.util.ArrayList<AttachmentLayout>();
        var noteCount = line.noteCount();

        if (noteCount == 0) {
            return List.of();
        }

        addTempoAttachment(g2d, line, lineIndex, measurementService, attachments);
        addBeatChangeAttachment(g2d, line, middleLineY, attachments);
        addNoteAttachments(g2d, line, lineIndex, middleLineY, noteCount, measurementService, attachments);

        return attachments;
    }

    private void addTempoAttachment(
        Graphics2D g2d,
        Line line,
        int lineIndex,
        MeasurementService measurementService,
        List<AttachmentLayout> attachments
    ) {
        var firstTempoIndex = line.getFirstTempoChange();

        if (firstTempoIndex < 0) {
            return;
        }

        var note = line.getNote(firstTempoIndex);
        var tempo = note.getTempoChange();

        if (tempo == null) {
            return;
        }

        var tempoBounds = measurementService.getTempoBounds(g2d, line, lineIndex);

        if (tempoBounds == null) {
            return;
        }

        attachments.add(createAttachment(
            AttachmentLayout.Type.TEMPO,
            firstTempoIndex,
            note.getXPos(),
            (int) tempoBounds.getMinY(),
            tempoBounds,
            tempo
        ));
    }

    private void addBeatChangeAttachment(
        Graphics2D g2d,
        Line line,
        int middleLineY,
        List<AttachmentLayout> attachments
    ) {
        var firstBeatChangeIndex = line.getFirstBeatChange();

        if (firstBeatChangeIndex < 0) {
            return;
        }

        var note = line.getNote(firstBeatChangeIndex);
        var beatChange = note.getBeatChange();

        if (beatChange == null) {
            return;
        }

        var beatChangeY = middleLineY + line.getBeatChangeYPos();
        var font = score.getComposition().getAttributionFont();
        var metrics = g2d.getFontMetrics(font);
        var bounds = createTextBounds(note.getXPos(), beatChangeY, 50, metrics);

        attachments.add(createAttachment(
            AttachmentLayout.Type.BEAT_CHANGE,
            firstBeatChangeIndex,
            note.getXPos(),
            beatChangeY,
            bounds,
            beatChange
        ));
    }

    private void addNoteAttachments(
        Graphics2D g2d,
        Line line,
        int lineIndex,
        int middleLineY,
        int noteCount,
        MeasurementService measurementService,
        List<AttachmentLayout> attachments
    ) {
        for (var i = 0; i < noteCount; i++) {
            var note = line.getNote(i);

            addAnnotationAttachment(g2d, note, i, lineIndex, measurementService, attachments);
            addTrillAttachment(line, note, i, noteCount, middleLineY, attachments);
            addFermataAttachment(note, i, lineIndex, attachments);
        }
    }

    private void addAnnotationAttachment(
        Graphics2D g2d,
        Note note,
        int noteIndex,
        int lineIndex,
        MeasurementService measurementService,
        List<AttachmentLayout> attachments
    ) {
        var annotation = note.getAnnotation();

        if (annotation == null) {
            return;
        }

        var annotationY = measurementService.getAnnotationYPos(lineIndex, note);
        var annotationX = (int) measurementService.getAnnotationXPos(g2d, note);
        var font = score.getComposition().getAnnotationFont();
        var metrics = g2d.getFontMetrics(font);
        var text = annotation.getAnnotation();
        var bounds = createTextBounds(annotationX, annotationY, metrics.stringWidth(text), metrics);
        var type = annotation.getYPos() < 0
            ? AttachmentLayout.Type.ANNOTATION_ABOVE
            : AttachmentLayout.Type.ANNOTATION_BELOW;

        attachments.add(createAttachment(type, noteIndex, annotationX, annotationY, bounds, annotation));
    }

    private void addTrillAttachment(
        Line line,
        Note note,
        int noteIndex,
        int noteCount,
        int middleLineY,
        List<AttachmentLayout> attachments
    ) {
        // Only process start of trill sequences
        if (!note.isTrill() || (noteIndex > 0 && line.getNote(noteIndex - 1).isTrill())) {
            return;
        }

        var trillEnd = findTrillEnd(line, noteIndex, noteCount);
        var trillY = middleLineY + line.getTrillYPos();
        var startX = note.getXPos();
        var endX = line.getNote(trillEnd).getXPos() + 18;
        var bounds = new Rectangle2D.Double(startX, trillY - 15, endX - startX, 20);

        attachments.add(createAttachment(
            AttachmentLayout.Type.TRILL,
            noteIndex,
            startX,
            trillY,
            bounds,
            null
        ));
    }

    private int findTrillEnd(Line line, int start, int noteCount) {
        var trillEnd = start;

        while (trillEnd + 1 < noteCount && line.getNote(trillEnd + 1).isTrill()) {
            trillEnd++;
        }

        return trillEnd;
    }

    private void addFermataAttachment(Note note, int noteIndex, int lineIndex, List<AttachmentLayout> attachments) {
        if (!note.isFermata()) {
            return;
        }

        var fermataYPos = calculateFermataYPos(note);
        var fermataY = getNoteYPos(fermataYPos, lineIndex) + 12;
        var fermataX = note.getXPos() - 5;
        var bounds = new Rectangle2D.Double(fermataX, fermataY - 15, 20, 15);

        attachments.add(createAttachment(
            AttachmentLayout.Type.FERMATA,
            noteIndex,
            fermataX,
            fermataY,
            bounds,
            null
        ));
    }

    private AttachmentLayout createAttachment(
        AttachmentLayout.Type type,
        int noteIndex,
        int x,
        int y,
        Rectangle2D bounds,
        Object data
    ) {
        var elementBounds = ElementBounds.uniform(bounds, DEFAULT_PADDING, 0);
        var position = new java.awt.Point(x, y);
        return new AttachmentLayout(type, noteIndex, position, elementBounds, data);
    }

    private Rectangle2D createTextBounds(int x, int baselineY, int width, FontMetrics metrics) {
        return new Rectangle2D.Double(x, baselineY - metrics.getAscent(), width, metrics.getHeight());
    }

    // -------------------------------------------------------------------------
    // Range Layout Creation
    // -------------------------------------------------------------------------

    /**
     * Creates RangeLayout objects for all range elements in a line.
     */
    private List<RangeLayout> createRangeLayouts(
        @NotNull Graphics2D g2d,
        @NotNull Line line,
        int lineIndex,
        int middleLineY,
        @NotNull MeasurementService measurementService
    ) {
        if (line.noteCount() == 0) {
            return List.of();
        }

        var ranges = new java.util.ArrayList<RangeLayout>();

        addCurveRanges(line, lineIndex, line.getTies(), RangeLayout.Type.TIE, ranges);
        addTupletRanges(line, lineIndex, ranges);
        addEndingRanges(line, middleLineY, ranges);
        addDynamicRanges(line, middleLineY, line.getCrescendos(), RangeLayout.Type.CRESCENDO, ranges);
        addDynamicRanges(line, middleLineY, line.getDiminuendos(), RangeLayout.Type.DIMINUENDO, ranges);

        return ranges;
    }

    private void addCurveRanges(
        Line line,
        int lineIndex,
        songscribe.data.IntervalSet intervals,
        RangeLayout.Type type,
        List<RangeLayout> ranges
    ) {
        for (var iter = intervals.listIterator(); iter.hasNext(); ) {
            var interval = iter.next();
            var startNote = line.getNote(interval.getStart());
            var endNote = line.getNote(interval.getEnd());
            var above = startNote.isUpper();
            var startX = startNote.getXPos();
            var endX = endNote.getXPos() + 15;
            var startY = getNoteYPos(startNote.getYPos(), lineIndex);
            var endY = getNoteYPos(endNote.getYPos(), lineIndex);

            Rectangle2D bounds;

            if (type == RangeLayout.Type.TIE) {
                var y = startY + (above ? -10 : 10);
                bounds = new Rectangle2D.Double(startX, above ? y - 10 : y, endX - startX, RANGE_HEIGHT);
            } else {
                var minY = Math.min(startY, endY) - (above ? 15 : 0);
                var maxY = Math.max(startY, endY) + (above ? 0 : 15);
                bounds = new Rectangle2D.Double(startX, minY, endX - startX, maxY - minY);
            }

            ranges.add(createRange(type, interval.getStart(), interval.getEnd(), above, bounds, null));
        }
    }

    private void addTupletRanges(Line line, int lineIndex, List<RangeLayout> ranges) {
        for (var iter = line.getTuplets().listIterator(); iter.hasNext(); ) {
            var interval = iter.next();
            var startNote = line.getNote(interval.getStart());
            var endNote = line.getNote(interval.getEnd());
            var above = startNote.isUpper();
            var startX = startNote.getXPos();
            var endX = endNote.getXPos() + 15;
            var startY = getNoteYPos(startNote.getYPos(), lineIndex);
            var endY = getNoteYPos(endNote.getYPos(), lineIndex);
            var extremeY = above ? Math.min(startY, endY) - 25 : Math.max(startY, endY) + 25;
            var bounds = new Rectangle2D.Double(
                startX, above ? extremeY : startY, endX - startX, Math.abs(extremeY - startY) + 10
            );

            ranges.add(createRange(
                RangeLayout.Type.TUPLET,
                interval.getStart(),
                interval.getEnd(),
                above,
                bounds,
                interval.getData()
            ));
        }
    }

    private void addEndingRanges(Line line, int middleLineY, List<RangeLayout> ranges) {
        for (var iter = line.getFirstSecondEndings().listIterator(); iter.hasNext(); ) {
            var interval = iter.next();
            var startNote = line.getNote(interval.getStart());
            var endNote = line.getNote(interval.getEnd());
            var endingY = middleLineY + line.getFirstSecondEndingYPos();
            var bounds = new Rectangle2D.Double(
                startNote.getXPos(), endingY, endNote.getXPos() + 30 - startNote.getXPos(), RANGE_HEIGHT
            );

            ranges.add(createRange(
                RangeLayout.Type.ENDING,
                interval.getStart(),
                interval.getEnd(),
                true,
                bounds,
                interval.getData()
            ));
        }
    }

    private void addDynamicRanges(
        Line line,
        int middleLineY,
        songscribe.data.IntervalSet intervals,
        RangeLayout.Type type,
        List<RangeLayout> ranges
    ) {
        for (var iter = intervals.listIterator(); iter.hasNext(); ) {
            var interval = iter.next();
            var startNote = line.getNote(interval.getStart());
            var endNote = line.getNote(interval.getEnd());
            var y = middleLineY + DYNAMICS_Y_OFFSET;
            var bounds = new Rectangle2D.Double(
                startNote.getXPos(), y - 5, endNote.getXPos() + 15 - startNote.getXPos(), RANGE_HEIGHT
            );

            ranges.add(createRange(type, interval.getStart(), interval.getEnd(), false, bounds, null));
        }
    }

    private RangeLayout createRange(
        RangeLayout.Type type,
        int startIndex,
        int endIndex,
        boolean above,
        Rectangle2D bounds,
        Object data
    ) {
        var elementBounds = ElementBounds.uniform(bounds, DEFAULT_PADDING, 0);
        return new RangeLayout(type, startIndex, endIndex, above, elementBounds, null, data);
    }

    // -------------------------------------------------------------------------
    // Lyrics Layout Creation
    // -------------------------------------------------------------------------

    /**
     * Creates a LyricsLayout for a line's staff lyrics.
     */
    private LyricsLayout createLyricsLayout(
        @NotNull Graphics2D g2d,
        @NotNull Line line,
        int lineIndex,
        int middleLineY
    ) {
        var noteCount = line.noteCount();

        if (noteCount == 0) {
            return LyricsLayout.empty();
        }

        var font = score.getComposition().getLyricsFont();
        var metrics = g2d.getFontMetrics(font);
        var lyricsY = middleLineY + line.getLyricsYPos();
        var syllables = new java.util.ArrayList<SyllableLayout>();
        Rectangle2D combinedBounds = null;

        for (var i = 0; i < noteCount; i++) {
            var note = line.getNote(i);
            var syllable = note.acceleration.syllable;

            if (syllable == null || syllable.isEmpty() || syllable.equals("_")) {
                continue;
            }

            var syllableWidth = metrics.stringWidth(syllable);
            var x = (note.getXPos() + Note.HOT_SPOT.x) - (syllableWidth / 2) + note.getSyllableMovement();
            var contentBounds = createTextBounds(x, lyricsY, syllableWidth, metrics);
            var elementBounds = ElementBounds.uniform(contentBounds, SYLLABLE_PADDING, 0);
            var hasMelisma = note.acceleration.syllableRelation == Note.SyllableRelation.EXTENDER;

            syllables.add(new SyllableLayout(i, x, syllable, elementBounds, hasMelisma));
            combinedBounds = combinedBounds == null ? contentBounds : combinedBounds.createUnion(contentBounds);
        }

        if (syllables.isEmpty()) {
            return LyricsLayout.empty();
        }

        return new LyricsLayout(lyricsY, syllables, ElementBounds.uniform(combinedBounds, SYLLABLE_PADDING, 0));
    }

    // -------------------------------------------------------------------------
    // Helper Methods
    // -------------------------------------------------------------------------

    /**
     * Calculates the Y position for a fermata relative to the note.
     */
    private int calculateFermataYPos(@NotNull Note note) {
        if (note.isUpper() && note.getYPos() < 2) {
            return note.getYPos() - 11;
        }

        if (!note.isUpper() && note.getYPos() < -4) {
            return note.getYPos() - 5;
        }

        return -9;
    }

    /**
     * Builds the complete LayoutResult from section bounds and line layouts.
     *
     * @param g2d         Graphics context for font metrics
     * @param lineLayouts The line layouts
     * @return Complete LayoutResult
     */
    private LayoutResult buildLayoutResult(
        @NotNull Graphics2D g2d,
        @NotNull List<LineLayout> lineLayouts
    ) {
        var composition = score.getComposition();

        // Build section layouts from bounds
        var titleLayout = buildSectionLayout(getTitleBounds(), composition.getTitleFont());
        var attributionLayout = buildSectionLayout(getAttributionBounds(), composition.getAttributionFont());
        var scoreLayout = buildSectionLayout(getScoreBounds(), null);
        var lyricsLayout = buildSectionLayout(getLyricsBounds(), composition.getLyricsFont());
        var banglaLyricsLayout = buildSectionLayout(getBanglaLyricsBounds(), composition.getBanglaFont());
        var translationLayout = buildSectionLayout(getTranslationBounds(), composition.getLyricsFont());
        var footnotesLayout = buildSectionLayout(getFootnotesBounds(), composition.getFootnoteFont());

        // Calculate total bounds
        var totalBounds = calculateTotalBounds();

        return new LayoutResult(
            titleLayout,
            attributionLayout,
            scoreLayout,
            lyricsLayout,
            banglaLyricsLayout,
            translationLayout,
            footnotesLayout,
            lineLayouts,
            totalBounds
        );
    }

    /**
     * Creates a SectionLayout from Rectangle bounds.
     */
    private SectionLayout buildSectionLayout(@Nullable Rectangle bounds, @Nullable java.awt.Font font) {
        if (bounds == null || bounds.height == 0) {
            return SectionLayout.empty();
        }

        var contentBounds = new Rectangle2D.Double(bounds.x, bounds.y, bounds.width, bounds.height);
        var elementBounds = ElementBounds.contentOnly(contentBounds);
        var baselineY = bounds.y;  // Simplified - actual baseline depends on font metrics

        return new SectionLayout(elementBounds, List.of(), font, baselineY);
    }

    /**
     * Calculates the total bounds of all content.
     */
    private Rectangle2D calculateTotalBounds() {
        var composition = score.getComposition();
        var width = composition.getLineWidth();
        var height = getTotalHeight();
        return new Rectangle2D.Double(0, 0, width, height);
    }

    // -------------------------------------------------------------------------
    // Section Bounds Accessors
    // -------------------------------------------------------------------------

    /**
     * Returns the bounds for the specified section, or null if not yet calculated.
     */
    @Nullable
    public Rectangle getBounds(@NotNull LayoutChangeMessage.Section section) {
        return sectionBounds.get(section);
    }

    @Nullable
    public Rectangle getTitleBounds() {
        return sectionBounds.get(LayoutChangeMessage.Section.TITLE);
    }

    @Nullable
    public Rectangle getAttributionBounds() {
        return sectionBounds.get(LayoutChangeMessage.Section.ATTRIBUTION);
    }

    @Nullable
    public Rectangle getScoreBounds() {
        return sectionBounds.get(LayoutChangeMessage.Section.SCORE);
    }

    @Nullable
    public Rectangle getLyricsBounds() {
        return sectionBounds.get(LayoutChangeMessage.Section.LYRICS);
    }

    @Nullable
    public Rectangle getBanglaLyricsBounds() {
        return sectionBounds.get(LayoutChangeMessage.Section.BANGLA_LYRICS);
    }

    @Nullable
    public Rectangle getTranslationBounds() {
        return sectionBounds.get(LayoutChangeMessage.Section.TRANSLATION);
    }

    @Nullable
    public Rectangle getFootnotesBounds() {
        return sectionBounds.get(LayoutChangeMessage.Section.FOOTNOTES);
    }

    // -------------------------------------------------------------------------
    // Coordinate Helpers
    // -------------------------------------------------------------------------

    /**
     * Returns the Y coordinate of the middle line (B) of the first staff.
     * <p>
     * The middle line is positioned at the score top plus the number of
     * staff lines above plus 3 (to center the B line in the staff) multiplied
     * by the staff line offset.
     */
    public int getMiddleLineY() {
        // Use composition top padding to stay consistent with Score.mouseMoved() coordinate system
        // which still uses composition.getTopPadding() for mouse coordinate calculations
        return score.getComposition().getTopPadding() +
            ((Score.STAFF_LINES_ABOVE + 3) * Score.STAFF_LINE_Y_OFFSET);
    }

    /**
     * Returns the Y offset for the first row based on attribution and content extent.
     * <p>
     * If attribution exists, ensures SCORE_TOP_MARGIN (2 staff lines = 16px) is maintained
     * between the attribution bottom and the first row's highest element.
     *
     * @return The Y offset for the first row from the attribution bottom
     */
    public int getFirstRowOffset() {
        var composition = score.getComposition();

        if (composition == null || composition.lineCount() == 0) {
            return 0;
        }

        // Use cached value from measure() pass
        var distanceAbove = cachedFirstLineDistanceAbove >= 0
            ? cachedFirstLineDistanceAbove
            : 0;

        // The offset needs to account for:
        // 1. The SCORE_TOP_MARGIN from attribution to the topmost element
        // 2. The distance from the topmost element to the top staff line
        // 3. The distance from top staff line to middle line (3 staff lines)
        return SCORE_TOP_MARGIN + distanceAbove + (3 * Score.STAFF_LINE_Y_OFFSET);
    }

    /**
     * Returns the uniform vertical spacing between staff lines.
     * <p>
     * This is the maximum required height across all lines, ensuring all
     * lines use the same spacing for cleaner visual alignment and simpler
     * hit-testing.
     */
    public int getRowHeight() {
        if (cachedUniformRowHeight >= 0) {
            return cachedUniformRowHeight;
        }

        // Fallback to default if not calculated during measure() pass
        // This shouldn't normally happen as measure() should be called first
        return getDefaultRowHeight();
    }

    /**
     * Calculates the Y position of a note given its pitch position and line index.
     *
     * @param yPos      The pitch position (vertical offset from middle line)
     * @param lineIndex The index of the staff line (0-based)
     * @return The Y coordinate in pixels
     */
    public int getNoteYPos(int yPos, int lineIndex) {
        return (int) (getMiddleLineY() +
            (yPos * Score.NOTE_Y_OFFSET) +
            (lineIndex * getRowHeight()));
    }

    /**
     * Returns the Y coordinate of the topmost content.
     * <p>
     * If the title is empty, returns the minimum of the tempo change position
     * and the attribution start Y. Otherwise returns 0.
     */
    public int getContentStartY() {
        var composition = score.getComposition();

        if (composition.getTitle().isEmpty()) {
            // Use cached value from measure() pass
            var effectiveTempoYPos = cachedFirstLineEffectiveTempoYPos != Integer.MAX_VALUE
                ? cachedFirstLineEffectiveTempoYPos
                : composition.getLine(0).getTempoChangeYPos();

            var tempoStartY = getMiddleLineY() +
                effectiveTempoYPos -
                (Score.STAFF_LINE_Y_OFFSET * Score.STAFF_LINE_COUNT);

            if (composition.getAttribution().isEmpty()) {
                return tempoStartY;
            }

            // Use layout-calculated attribution position if available
            var attributionBounds = getAttributionBounds();
            var attributionY = attributionBounds != null
                ? attributionBounds.y
                : composition.getAttributionStartY();
            return Math.min(tempoStartY, attributionY);
        }

        return 0;
    }

    /**
     * Returns the total height of the sheet.
     * <p>
     * This is the bottom of the last non-empty section (footnotes if present,
     * otherwise the last content section).
     */
    public int getTotalHeight() {
        // Check sections from bottom to top to find last non-empty one
        var footnotes = getFootnotesBounds();

        if (footnotes != null && footnotes.height > 0) {
            return footnotes.y + footnotes.height;
        }

        var translation = getTranslationBounds();

        if (translation != null && translation.height > 0) {
            return translation.y + translation.height;
        }

        var bangla = getBanglaLyricsBounds();

        if (bangla != null && bangla.height > 0) {
            return bangla.y + bangla.height;
        }

        var lyrics = getLyricsBounds();

        if (lyrics != null && lyrics.height > 0) {
            return lyrics.y + lyrics.height;
        }

        var scoreBounds = getScoreBounds();

        if (scoreBounds != null && scoreBounds.height > 0) {
            return scoreBounds.y + scoreBounds.height;
        }

        var attribution = getAttributionBounds();

        if (attribution != null && attribution.height > 0) {
            return attribution.y + attribution.height;
        }

        var title = getTitleBounds();

        if (title != null && title.height > 0) {
            return title.y + title.height;
        }

        return 0;
    }

    /**
     * Returns the Y position for under-lyrics (lyrics block below score).
     * <p>
     * This is the Y position of the lyrics section bounds.
     */
    public int getUnderLyricsYPos() {
        var lyricsBounds = getLyricsBounds();

        if (lyricsBounds != null) {
            return lyricsBounds.y;
        }

        // Fallback calculation
        return getMiddleLineY() + (score.getComposition().lineCount() * getRowHeight());
    }

    // -------------------------------------------------------------------------
    // Internal - Section bounds setters for use by measure methods
    // -------------------------------------------------------------------------

    protected void setSectionBounds(
        @NotNull LayoutChangeMessage.Section section,
        @NotNull Rectangle bounds
    ) {
        sectionBounds.put(section, bounds);
    }

    // -------------------------------------------------------------------------
    // Block Flow Layout (Phase 3.4)
    // -------------------------------------------------------------------------

    /**
     * Collapses two adjacent vertical margins (CSS-like margin collapsing).
     * <p>
     * In CSS block flow, when two vertical margins meet (bottom of one element and
     * top of the next), they collapse to the larger margin instead of summing.
     * This prevents excessive spacing between elements.
     * <p>
     * Example: If element A has margin-bottom=16px and element B has margin-top=24px,
     * the space between them is 24px (not 40px).
     *
     * @param marginBottom Bottom margin of the preceding element (in pixels)
     * @param marginTop    Top margin of the following element (in pixels)
     * @return The collapsed margin (the larger of the two)
     */
    public static int collapseMargins(int marginBottom, int marginTop) {
        return Math.max(marginBottom, marginTop);
    }

    /**
     * Collapses margins using MU (measure unit) values from LayoutStylesheet.
     *
     * @param marginBottomMU Bottom margin in measure units
     * @param marginTopMU    Top margin in measure units
     * @return The collapsed margin in pixels
     */
    public static int collapseMarginsFromMU(double marginBottomMU, double marginTopMU) {
        return LayoutStylesheet.px(Math.max(marginBottomMU, marginTopMU));
    }

    /**
     * Calculates the vertical spacing between two sections using margin collapsing.
     * <p>
     * If either section is empty (height=0), returns 0 (no spacing needed).
     * Otherwise, returns the collapsed margin.
     *
     * @param precedingHeight  Height of the preceding section (0 if empty)
     * @param precedingMargin  Bottom margin of the preceding section
     * @param followingHeight  Height of the following section (0 if empty)
     * @param followingMargin  Top margin of the following section
     * @return The spacing to add between sections
     */
    public static int calculateSectionSpacing(
        int precedingHeight,
        int precedingMargin,
        int followingHeight,
        int followingMargin
    ) {
        // No spacing if either section is empty
        if (precedingHeight == 0 || followingHeight == 0) {
            return 0;
        }

        return collapseMargins(precedingMargin, followingMargin);
    }

    /**
     * Calculates the Y position for a section given the preceding section's bounds
     * and the margins to apply.
     * <p>
     * This implements CSS-like block flow: sections stack vertically with collapsed
     * margins between them.
     *
     * @param precedingBottom  Bottom Y of the preceding section (0 if none)
     * @param precedingHeight  Height of the preceding section (0 if empty)
     * @param precedingMargin  Bottom margin of the preceding section (MU)
     * @param sectionHeight    Height of the new section (0 if empty)
     * @param sectionMargin    Top margin of the new section (MU)
     * @return The Y position for the new section
     */
    public static int calculateBlockFlowY(
        int precedingBottom,
        int precedingHeight,
        double precedingMargin,
        int sectionHeight,
        double sectionMargin
    ) {
        // If the new section is empty, position doesn't matter
        if (sectionHeight == 0) {
            return precedingBottom;
        }

        // If there's no preceding section, start at the top
        if (precedingHeight == 0) {
            return 0;
        }

        // Calculate collapsed margin
        var spacing = collapseMarginsFromMU(precedingMargin, sectionMargin);
        return precedingBottom + spacing;
    }

    // -------------------------------------------------------------------------
    // Horizontal Layout
    // -------------------------------------------------------------------------

    /**
     * Calculates the X position for a new note to be added after the last note in a line.
     * <p>
     * The position accounts for the previous note's type-based spacing, the new note's
     * accidental width, and the line's note distance change ratio.
     *
     * @param line The line to which the note will be added
     * @param note The note to be added (for accidental width calculation)
     * @return The X position for the new note
     */
    public static int calculateLastNoteXPos(@NotNull Line line, Note note) {
        if (line.noteCount() == 0) {
            return firstNoteX;
        }

        var lastNote = line.getNote(line.noteCount() - 1);

        return (
            lastNote.getXPos() +
            Math.round(
                (NOTE_SPACING.get(lastNote.getNoteType()) +
                    (note.getAccidental().getWidthFactor() * ACCIDENTAL_WIDTH) +
                    (note.isAccidentalInParentheses() ? 8 : 0)) *
                line.getNoteDistChangeRatio()
            )
        );
    }

    /**
     * Returns the default horizontal spacing for a note of the given type.
     * <p>
     * This is the base spacing before applying line-specific adjustments
     * (noteDistChangeRatio) or accidental widths.
     *
     * @param noteType The type of note
     * @return The spacing in pixels, or 0 if the note type is not found
     */
    public static int getNoteSpacing(@NotNull NoteType noteType) {
        return NOTE_SPACING.getOrDefault(noteType, 0);
    }

    /**
     * Returns the X position of the first note on a staff line.
     *
     * @return The first note X position in pixels
     */
    public static int getFirstNoteX() {
        return firstNoteX;
    }

    /**
     * Sets the X position of the first note on a staff line.
     * <p>
     * This is typically set based on the composition's configuration.
     *
     * @param x The first note X position in pixels
     */
    public static void setFirstNoteX(int x) {
        firstNoteX = x;
    }

    // -------------------------------------------------------------------------
    // Reference-Based Positioning (Phase 3.3)
    // -------------------------------------------------------------------------

    /**
     * Calculates the dynamic reference Y position for above-staff elements.
     * <p>
     * Returns the minimum Y coordinate that clears both the staff top and the
     * note's full extent (including stem and articulations). This implements
     * the DYNAMIC_ABOVE reference type.
     * <p>
     * Formula: min(staffTopY, note.getTop())
     * (Using min because Y increases downward, so min = highest point)
     *
     * @param middleLineY Y coordinate of the middle staff line
     * @param noteLayout  The note layout (may be null for staff-only reference)
     * @return Y coordinate that clears everything above
     */
    public double calculateDynamicReferenceAbove(int middleLineY, @Nullable NoteLayout noteLayout) {
        var staffTopY = middleLineY - (2 * LayoutStylesheet.STAFF_LINE_Y_OFFSET);

        if (noteLayout == null) {
            return staffTopY;
        }

        return Math.min(staffTopY, noteLayout.getTop());
    }

    /**
     * Calculates the dynamic reference Y position for below-staff elements.
     * <p>
     * Returns the maximum Y coordinate that clears both the staff bottom and the
     * note's full extent (including stem and articulations). This implements
     * the DYNAMIC_BELOW reference type.
     * <p>
     * Formula: max(staffBottomY, note.getBottom())
     * (Using max because Y increases downward, so max = lowest point)
     *
     * @param middleLineY Y coordinate of the middle staff line
     * @param noteLayout  The note layout (may be null for staff-only reference)
     * @return Y coordinate that clears everything below
     */
    public double calculateDynamicReferenceBelow(int middleLineY, @Nullable NoteLayout noteLayout) {
        var staffBottomY = middleLineY + (2 * LayoutStylesheet.STAFF_LINE_Y_OFFSET);

        if (noteLayout == null) {
            return staffBottomY;
        }

        return Math.max(staffBottomY, noteLayout.getBottom());
    }

    /**
     * Determines whether a range element should be placed above or below the notes.
     * <p>
     * Scans ALL notes in the range for stem direction. The rule is:
     * <ul>
     *   <li>If ANY note has stem down (isUpper=false) → place ABOVE</li>
     *   <li>If ALL notes have stem up (isUpper=true) → place BELOW</li>
     * </ul>
     * This ensures the range element is on the opposite side from any stems,
     * avoiding collisions.
     *
     * @param notes List of notes in the range
     * @return true if the element should be placed above, false for below
     */
    public boolean calculateRangePlacement(@NotNull List<Note> notes) {
        if (notes.isEmpty()) {
            return true; // Default to above for empty range
        }

        // If ANY note has stem down, place above
        for (var note : notes) {
            if (!note.isUpper()) {
                return true; // Stem down found, place above
            }
        }

        // All notes have stem up, place below
        return false;
    }

    /**
     * Determines range placement using note layouts instead of notes.
     * <p>
     * This overload uses the pre-computed stem direction from NoteLayout.
     *
     * @param noteLayouts List of note layouts in the range
     * @return true if the element should be placed above, false for below
     */
    public boolean calculateRangePlacementFromLayouts(@NotNull List<NoteLayout> noteLayouts) {
        if (noteLayouts.isEmpty()) {
            return true; // Default to above for empty range
        }

        // If ANY note has stem down, place above
        for (var layout : noteLayouts) {
            if (!layout.isStemUp()) {
                return true; // Stem down found, place above
            }
        }

        // All notes have stem up, place below
        return false;
    }

    /**
     * Calculates the maximum extent bounds across all notes in a range.
     * <p>
     * For above placement: finds the minimum Y (highest point) across all notes.
     * For below placement: finds the maximum Y (lowest point) across all notes.
     * This ensures the range element clears all notes in the range.
     *
     * @param noteLayouts List of note layouts in the range
     * @param above       True if calculating for above-staff placement
     * @param middleLineY Y coordinate of the middle staff line (for staff bounds)
     * @return Rectangle2D representing the range's extent bounds
     */
    public Rectangle2D calculateRangeMaxBounds(
        @NotNull List<NoteLayout> noteLayouts,
        boolean above,
        int middleLineY
    ) {
        if (noteLayouts.isEmpty()) {
            // Return staff bounds for empty range
            var staffTopY = middleLineY - (2 * LayoutStylesheet.STAFF_LINE_Y_OFFSET);
            var staffBottomY = middleLineY + (2 * LayoutStylesheet.STAFF_LINE_Y_OFFSET);
            return new Rectangle2D.Double(0, staffTopY, 0, staffBottomY - staffTopY);
        }

        // Initialize with first note's bounds
        var first = noteLayouts.getFirst();
        var minX = first.getBounds().getNoteHeadBounds().getX();
        var maxX = first.getBounds().getNoteHeadBounds().getMaxX();
        double extremeY;

        if (above) {
            // Find highest point (minimum Y)
            extremeY = first.getTop();

            for (var layout : noteLayouts) {
                extremeY = Math.min(extremeY, layout.getTop());
                minX = Math.min(minX, layout.getBounds().getNoteHeadBounds().getX());
                maxX = Math.max(maxX, layout.getBounds().getNoteHeadBounds().getMaxX());
            }

            // Also check against staff top
            var staffTopY = middleLineY - (2 * LayoutStylesheet.STAFF_LINE_Y_OFFSET);
            extremeY = Math.min(extremeY, staffTopY);

            return new Rectangle2D.Double(minX, extremeY, maxX - minX, 0);
        } else {
            // Find lowest point (maximum Y)
            extremeY = first.getBottom();

            for (var layout : noteLayouts) {
                extremeY = Math.max(extremeY, layout.getBottom());
                minX = Math.min(minX, layout.getBounds().getNoteHeadBounds().getX());
                maxX = Math.max(maxX, layout.getBounds().getNoteHeadBounds().getMaxX());
            }

            // Also check against staff bottom
            var staffBottomY = middleLineY + (2 * LayoutStylesheet.STAFF_LINE_Y_OFFSET);
            extremeY = Math.max(extremeY, staffBottomY);

            return new Rectangle2D.Double(minX, extremeY, maxX - minX, 0);
        }
    }

    /**
     * Calculates the Y position for an above-staff attachment at a specific note.
     * <p>
     * Uses DYNAMIC_ABOVE reference (clears both staff and note) plus the specified margin.
     *
     * @param middleLineY Y coordinate of the middle staff line
     * @param noteLayout  The note layout (may be null for staff-only reference)
     * @param marginMU    Margin in measure units (from LayoutStylesheet)
     * @return Y position for the bottom of the attachment
     */
    public int calculateAboveStaffAttachmentY(
        int middleLineY,
        @Nullable NoteLayout noteLayout,
        double marginMU
    ) {
        var referenceY = calculateDynamicReferenceAbove(middleLineY, noteLayout);
        return (int) (referenceY - LayoutStylesheet.px(marginMU));
    }

    /**
     * Calculates the Y position for a below-staff attachment at a specific note.
     * <p>
     * Uses DYNAMIC_BELOW reference (clears both staff and note) plus the specified margin.
     *
     * @param middleLineY Y coordinate of the middle staff line
     * @param noteLayout  The note layout (may be null for staff-only reference)
     * @param marginMU    Margin in measure units (from LayoutStylesheet)
     * @return Y position for the top of the attachment
     */
    public int calculateBelowStaffAttachmentY(
        int middleLineY,
        @Nullable NoteLayout noteLayout,
        double marginMU
    ) {
        var referenceY = calculateDynamicReferenceBelow(middleLineY, noteLayout);
        return (int) (referenceY + LayoutStylesheet.px(marginMU));
    }

    /**
     * Calculates the Y position for a range element (tie, tuplet, ending).
     * <p>
     * Uses RANGE_MAX_ABOVE or RANGE_MIN_BELOW reference (clears all notes in range)
     * plus the specified margin.
     *
     * @param noteLayouts List of note layouts in the range
     * @param above       True if placed above, false if below
     * @param middleLineY Y coordinate of the middle staff line
     * @param marginMU    Margin in measure units (from LayoutStylesheet)
     * @return Y position for the range element
     */
    public int calculateRangeElementY(
        @NotNull List<NoteLayout> noteLayouts,
        boolean above,
        int middleLineY,
        double marginMU
    ) {
        var rangeBounds = calculateRangeMaxBounds(noteLayouts, above, middleLineY);
        var margin = LayoutStylesheet.px(marginMU);

        if (above) {
            // Position above the range's highest point
            return (int) (rangeBounds.getY() - margin);
        } else {
            // Position below the range's lowest point
            return (int) (rangeBounds.getY() + margin);
        }
    }
}
