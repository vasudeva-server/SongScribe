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

package songscribe.dom;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.util.List;

import org.jspecify.annotations.Nullable;

import songscribe.font.FontKey;
import songscribe.util.GraphicsState;
import songscribe.util.MyFontUtils;

/**
 * Bare measure-and-render surface for the song attribution block.
 * <p>
 * Pure rendering: no Swing, no layout manager, no font fields. All fonts are
 * passed as parameters so the caller (layout engine or dialog preview) drives
 * the measurement contract.
 *
 * <h3>Lifecycle: measure → cache → render</h3>
 * <pre>
 *  Invalidation points
 *  ───────────────────
 *  setSong(song)              → clears cachedMeasure   [Song metadata change]
 *  setOverrideLines(lines)    → clears cachedMeasure   [override lines change]
 *  fonts change (passed as params) → cache is keyed on font identity; mismatched
 *                                    fonts cause a fresh measure on next call
 *
 *  Measure / cache
 *  ───────────────
 *  getContentWidthPx(aFont, saFont)  → builds lines via formatter if cachedMeasure is null
 *                                       or fonts differ; stores MeasuredCache
 *  getContentHeightPx(aFont, saFont) → same cache; sums line heights + margins
 *
 *  Render
 *  ──────
 *  render(g2, xPx, yPx, widthPx, aFont, saFont) → reuses cachedMeasure (same fonts)
 *                                                   or triggers a fresh measure
 * </pre>
 */
public class AttributionPane {

    // -------------------------------------------------------------------------
    // Margins (pixels)
    // -------------------------------------------------------------------------

    private int marginTop = 0;
    private int marginBottom = 0;

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    @Nullable
    private Song song;

    /**
     * Directly injected lines for rendering, bypassing the Song model.
     * Used by the dialog preview to show uncommitted UI state without triggering
     * mutation notifications on the live Song.
     */
    @Nullable
    private List<AttributionLine> overrideLines;

    /**
     * Cached measurement result. Null means a fresh measure is needed.
     * Invalidated by {@link #setSong(Song)} and {@link #setOverrideLines(List)}.
     * Also invalidated when the fonts differ from those stored in the cache.
     */
    @Nullable
    private MeasuredCache cachedMeasure;

    // -------------------------------------------------------------------------
    // Cached measurement record
    // -------------------------------------------------------------------------

    /**
     * Holds a fully-measured set of attribution lines together with the fonts
     * and per-line metrics used to produce the measurement.
     * Re-computed whenever the Song, override lines, or fonts change.
     */
    private record MeasuredCache(
        List<AttributionLine> lines,
        Font attributionFont,
        Font subAttributionFont,
        List<FontMetrics> lineMetrics,
        int contentWidthPx,
        int contentHeightPx
    ) {}

    // -------------------------------------------------------------------------
    // Mutators (each invalidates the measure cache)
    // -------------------------------------------------------------------------

    /**
     * Clears the cached measurement so the next measure/render recomputes the
     * lines. Call this when a Song field the formatter reads — metadata or the
     * translation flag derived from {@code translatedLyrics} — changes without
     * the Song reference itself changing.
     */
    public void invalidateCache() {
        cachedMeasure = null;
    }

    /** Sets the song model; clears the measure cache. */
    public void setSong(Song song) {
        invalidateCache();
        this.song = song;
    }

    /**
     * Directly injects precomputed lines for rendering, bypassing the Song model.
     * Pass {@code null} to clear the override and fall back to the formatter.
     *
     * @param lines the lines to render; {@code null} clears the override
     */
    public void setOverrideLines(@Nullable List<AttributionLine> lines) {
        invalidateCache();
        overrideLines = lines;
    }

    public int getMarginTop() {
        return marginTop;
    }

    public void setMarginTop(int marginTop) {
        invalidateCache();
        this.marginTop = marginTop;
    }

    public int getMarginBottom() {
        return marginBottom;
    }

    public void setMarginBottom(int marginBottom) {
        invalidateCache();
        this.marginBottom = marginBottom;
    }

    // -------------------------------------------------------------------------
    // Measurement API
    // -------------------------------------------------------------------------

    /**
     * Returns the natural content width: the maximum rendered line width in pixels,
     * measured at the given fonts. No staff-width forcing.
     *
     * @param attributionFont    font for {@link FontKey#ATTRIBUTION} lines
     * @param subAttributionFont font for {@link FontKey#SUB_ATTRIBUTION} lines
     * @return max line width in pixels, or 0 if there are no lines
     */
    public int getContentWidthPx(Font attributionFont, Font subAttributionFont) {
        return measure(attributionFont, subAttributionFont).contentWidthPx();
    }

    /**
     * Returns the total content height: {@code marginTop + Σ line heights + marginBottom}.
     *
     * @param attributionFont    font for {@link FontKey#ATTRIBUTION} lines
     * @param subAttributionFont font for {@link FontKey#SUB_ATTRIBUTION} lines
     * @return total height in pixels
     */
    public int getContentHeightPx(Font attributionFont, Font subAttributionFont) {
        return measure(attributionFont, subAttributionFont).contentHeightPx();
    }

    /**
     * Returns the natural content size in pixels, measured once at the given
     * fonts. Prefer this over separate width/height calls when both dimensions
     * are needed in the same pass.
     *
     * @param attributionFont    font for {@link FontKey#ATTRIBUTION} lines
     * @param subAttributionFont font for {@link FontKey#SUB_ATTRIBUTION} lines
     * @return the content size in pixels
     */
    public Dimension getContentSizePx(Font attributionFont, Font subAttributionFont) {
        var cache = measure(attributionFont, subAttributionFont);
        return new Dimension(cache.contentWidthPx(), cache.contentHeightPx());
    }

    // -------------------------------------------------------------------------
    // Rendering API
    // -------------------------------------------------------------------------

    /**
     * Renders the attribution lines centered within {@code widthPx} at position
     * {@code (xPx, yPx)}.
     * <p>
     * Saves and restores font and color via {@link GraphicsState}. Lines are
     * horizontally centered; the top of the block is at {@code yPx + marginTop}.
     *
     * @param g2                 graphics context (antialiasing set by caller)
     * @param xPx                left edge of the available width in pixels
     * @param yPx                top of the attribution block in pixels
     * @param widthPx            available width for centering
     * @param attributionFont    font for {@link FontKey#ATTRIBUTION} lines
     * @param subAttributionFont font for {@link FontKey#SUB_ATTRIBUTION} lines
     */
    public void render(
        Graphics2D g2,
        double xPx,
        double yPx,
        double widthPx,
        Font attributionFont,
        Font subAttributionFont
    ) {
        var cache = measure(attributionFont, subAttributionFont);
        var lines = cache.lines();

        if (lines.isEmpty()) {
            return;
        }

        try (var ignored = GraphicsState.save(
            g2,
            GraphicsState.Property.FONT,
            GraphicsState.Property.COLOR
        )) {
            g2.setColor(Color.BLACK);
            var y = (float) (yPx + marginTop);

            for (var i = 0; i < lines.size(); i++) {
                var line = lines.get(i);
                var font = line.font() == FontKey.ATTRIBUTION ? attributionFont : subAttributionFont;
                g2.setFont(font);
                var metrics = cache.lineMetrics().get(i);
                y += metrics.getAscent();
                var textWidth = metrics.stringWidth(line.text());
                var x = (float) (xPx + (widthPx - textWidth) / 2.0);
                g2.drawString(line.text(), x, y);
                y += metrics.getDescent() + metrics.getLeading();
            }
        }
    }

    // -------------------------------------------------------------------------
    // Internal: measure → cache
    // -------------------------------------------------------------------------

    /**
     * Returns the cached measurement, or computes a fresh one if the cache is
     * null or the fonts differ from the cached fonts.
     */
    private MeasuredCache measure(Font attributionFont, Font subAttributionFont) {
        if (cachedMeasure != null
            && cachedMeasure.attributionFont().equals(attributionFont)
            && cachedMeasure.subAttributionFont().equals(subAttributionFont)) {
            return cachedMeasure;
        }

        var lines = resolveLines();
        var lineMetrics = lines.stream()
            .map(line -> {
                var font = line.font() == FontKey.ATTRIBUTION ? attributionFont : subAttributionFont;
                return MyFontUtils.getFontMetrics(font);
            })
            .toList();

        var maxWidth = 0;
        var totalHeight = marginTop;

        for (var i = 0; i < lines.size(); i++) {
            var metrics = lineMetrics.get(i);
            maxWidth = Math.max(maxWidth, metrics.stringWidth(lines.get(i).text()));
            totalHeight += metrics.getHeight();
        }

        totalHeight += marginBottom;
        cachedMeasure = new MeasuredCache(lines, attributionFont, subAttributionFont, lineMetrics, maxWidth, totalHeight);
        return cachedMeasure;
    }

    /**
     * Returns the lines to render: the override lines when set, otherwise lines
     * built from the formatter using the current Song's metadata.
     */
    private List<AttributionLine> resolveLines() {
        if (overrideLines != null) {
            return overrideLines;
        }

        if (song == null) {
            return List.of();
        }

        return AttributionFormatter.lines(song.getMetadata(), song.showTranslation());
    }
}
