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
import java.util.EnumMap;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import songscribe.music.Composition;
import songscribe.ui.component.Score;
import songscribe.ui.message.LayoutChangeMessage;

/**
 * Manages the vertical layout of all sections in a music score.
 * <p>
 * This class coordinates two-pass rendering (measure then draw) and maintains
 * centralized tracking of all section bounds. It replaces the existing RenderStep
 * enum and renderMap in Score.
 * <p>
 * Lifecycle: Single instance created in Score constructor, invalidated when
 * composition changes.
 */
public class LayoutManager {

    // Margin from title bottom to attribution top (2 staff lines)
    private static final int ATTRIBUTION_TOP_MARGIN = 2 * Score.STAFF_LINE_Y_OFFSET;

    // Margin from attribution bottom to score top (2 staff lines)
    private static final int SCORE_TOP_MARGIN = 2 * Score.STAFF_LINE_Y_OFFSET;

    // Margin from score bottom to lyrics block (5 staff lines)
    private static final int LYRICS_TOP_MARGIN = 5 * Score.STAFF_LINE_Y_OFFSET;

    // Margin from lyrics to Bangla lyrics (2 staff lines)
    private static final int BANGLA_LYRICS_TOP_MARGIN = 2 * Score.STAFF_LINE_Y_OFFSET;

    // Margin from Bangla lyrics to translation (2 staff lines)
    private static final int TRANSLATION_TOP_MARGIN = 2 * Score.STAFF_LINE_Y_OFFSET;

    // Minimum margin above footnotes (5 staff lines)
    private static final int FOOTNOTES_MIN_TOP_MARGIN = 5 * Score.STAFF_LINE_Y_OFFSET;

    private final Score score;

    private final EnumMap<LayoutChangeMessage.Section, Rectangle> sectionBounds =
        new EnumMap<>(LayoutChangeMessage.Section.class);

    private boolean valid = false;

    // Cached uniform row height; -1 means not calculated
    private int cachedUniformRowHeight = -1;

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
        sectionBounds.clear();
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

        var renderer = score.getRenderer();
        var composition = score.getComposition();
        var currentY = 0;

        // Title section
        var titleBounds = renderer.measureTitle(g2d);
        titleBounds.y = 0;  // Title starts at top of page
        currentY = titleBounds.y + titleBounds.height;
        setSectionBounds(LayoutChangeMessage.Section.TITLE, titleBounds);

        // Attribution section
        var attributionBounds = renderer.measureAttribution(g2d);

        if (attributionBounds.height > 0) {
            currentY += ATTRIBUTION_TOP_MARGIN;
        }

        attributionBounds.y = currentY;
        currentY = attributionBounds.y + attributionBounds.height;
        setSectionBounds(LayoutChangeMessage.Section.ATTRIBUTION, attributionBounds);

        // Score section
        var scoreBounds = renderer.measureScore(g2d);

        if (scoreBounds.height > 0) {
            currentY += SCORE_TOP_MARGIN;
        }

        scoreBounds.y = currentY;
        currentY = scoreBounds.y + scoreBounds.height;
        setSectionBounds(LayoutChangeMessage.Section.SCORE, scoreBounds);

        // Lyrics section
        var lyricsBounds = renderer.measureLyrics(g2d);

        if (lyricsBounds.height > 0) {
            currentY += LYRICS_TOP_MARGIN;
        }

        lyricsBounds.y = currentY;
        currentY = lyricsBounds.y + lyricsBounds.height;
        setSectionBounds(LayoutChangeMessage.Section.LYRICS, lyricsBounds);

        // Bangla lyrics section
        var banglaBounds = renderer.measureBanglaLyrics(g2d);

        if (banglaBounds.height > 0 && lyricsBounds.height > 0) {
            currentY += BANGLA_LYRICS_TOP_MARGIN;
        }

        banglaBounds.y = currentY;
        currentY = banglaBounds.y + banglaBounds.height;
        setSectionBounds(LayoutChangeMessage.Section.BANGLA_LYRICS, banglaBounds);

        // Translation section
        var translationBounds = renderer.measureTranslation(g2d);

        if (translationBounds.height > 0) {
            // Always add margin if there's content above (lyrics or Bangla)
            if (lyricsBounds.height > 0 || banglaBounds.height > 0) {
                currentY += TRANSLATION_TOP_MARGIN;
            }
        }

        translationBounds.y = currentY;
        currentY = translationBounds.y + translationBounds.height;
        setSectionBounds(LayoutChangeMessage.Section.TRANSLATION, translationBounds);

        // Footnotes section (bottom-anchored)
        var footnotesBounds = renderer.measureFootnotes(g2d);
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

        valid = true;
    }

    /**
     * Signals that layout calculation is complete.
     */
    public void finalizeLayout() {
        valid = true;
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

        cachedUniformRowHeight = score.getRenderer().calculateUniformRowHeight();
        return cachedUniformRowHeight;
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
            var tempoStartY = getMiddleLineY() +
                composition.getLine(0).getTempoChangeYPos() -
                (Score.STAFF_LINE_Y_OFFSET * Score.STAFF_LINE_COUNT);

            if (composition.getAttribution().isEmpty()) {
                return tempoStartY;
            }

            return Math.min(tempoStartY, composition.getAttributionStartY());
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
}
