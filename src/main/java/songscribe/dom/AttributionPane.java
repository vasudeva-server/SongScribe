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
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import songscribe.font.FontKey;
import songscribe.util.GraphicUtils;
import songscribe.util.GraphicsState;

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
    // Line spacing
    // -------------------------------------------------------------------------

    /**
     * Leading between consecutive lines, in staff spaces: the fixed vertical gap
     * between the descender of one line and the ascender of the next. Lines are
     * boxed to the rendered height of {@link #LINE_BOX_REFERENCE}, separated only
     * by this gap. No leading is added before the first line or after the last.
     */
    private static final double LEADING_SS = 0.5;

    /**
     * Additional vertical gap, in staff spaces, inserted above the first
     * sub-attribution line — at the transition from attribution to
     * sub-attribution lines — on top of the normal {@link #LEADING_SS} leading.
     */
    private static final double SUB_ATTRIBUTION_GAP_SS = 0.5;

    /**
     * Reference glyphs whose rendered ink defines the uniform line-box height.
     * The cap height of {@code T} contributes the ascent and the descender of
     * {@code y} the descent, so every line is boxed to the font's full vertical
     * extent regardless of which characters it contains.
     * <p>
     * Package-private so the measurement test can reference the same string
     * rather than duplicating the literal.
     */
    static final String LINE_BOX_REFERENCE = "Ty";

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
     * Holds the fully-laid-out attribution lines together with the fonts used to
     * produce them and the resulting content size. The per-line {@link LineLayout}
     * captures everything {@link #render} needs, so rendering is a straight walk
     * over precomputed data with no re-measurement.
     * Re-computed whenever the Song, override lines, or fonts change.
     */
    private record MeasuredCache(
        List<LineLayout> layouts,
        Font attributionFont,
        Font subAttributionFont,
        int contentWidthPx,
        int contentHeightPx
    ) {}

    /**
     * A single attribution line, fully positioned by {@link #measure}. Holds the
     * text and its role font, the ink bounds ({@code null} for empty text), and
     * the baseline's vertical offset in pixels from the top of the content block
     * (i.e. from {@code marginTop}). {@link #render} draws each line directly from
     * these values; {@link #measure} derives the content size from them. Keeping
     * the layout in one place means the measure and render passes cannot drift.
     */
    private record LineLayout(String text, Font font, @Nullable Rectangle inkBounds, int baselineOffsetPx) {}

    /**
     * The rendered vertical extent of {@link #LINE_BOX_REFERENCE} in a given font.
     * {@code ascentPx} is the ink distance above the baseline; {@code heightPx}
     * is the full ink height (ascent + descent). Recomputed per measure pass.
     */
    private record LineBox(int ascentPx, int heightPx) {}

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
        var layouts = cache.layouts();

        if (layouts.isEmpty()) {
            return;
        }

        try (var ignored = GraphicsState.save(
            g2,
            GraphicsState.Property.FONT,
            GraphicsState.Property.COLOR
        )) {
            g2.setColor(Color.BLACK);

            for (var layout : layouts) {
                var bounds = layout.inkBounds();

                if (bounds == null) {
                    continue;
                }

                g2.setFont(layout.font());
                var drawY = (float) (yPx + marginTop + layout.baselineOffsetPx());
                // Center the ink rectangle, then shift back by bounds.x so a negative
                // left bearing (e.g. the "W" in "Words") does not overhang the box.
                var drawX = (float) (xPx + (widthPx - bounds.width) / 2.0 - bounds.x);
                g2.drawString(layout.text(), drawX, drawY);
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
        var attributionBox = measureLineBox(attributionFont);
        var subAttributionBox = measureLineBox(subAttributionFont);

        var leadingPx = ScaleContext.ssToRoundedPx(LEADING_SS);
        var subAttributionGapPx = ScaleContext.ssToRoundedPx(SUB_ATTRIBUTION_GAP_SS);

        // The extra gap sits above the first sub-attribution line, but only when an
        // attribution line precedes it; a sub-attribution line at index 0 has nothing
        // to separate from and gets no gap.
        var firstSubAttributionIndex = firstSubAttributionIndex(lines);

        var layouts = new ArrayList<LineLayout>(lines.size());
        var maxWidth = 0;
        var offsetPx = 0;

        for (var i = 0; i < lines.size(); i++) {
            var line = lines.get(i);
            var isAttribution = line.font() == FontKey.ATTRIBUTION;
            var font = isAttribution ? attributionFont : subAttributionFont;
            var lineBox = isAttribution ? attributionBox : subAttributionBox;

            if (i > 0 && i == firstSubAttributionIndex) {
                offsetPx += subAttributionGapPx;
            }

            var bounds = GraphicUtils.inkBounds(line.text(), font);

            if (bounds != null) {
                maxWidth = Math.max(maxWidth, bounds.width);
            }

            layouts.add(new LineLayout(line.text(), font, bounds, offsetPx + lineBox.ascentPx()));
            offsetPx += lineBox.heightPx();

            if (i < lines.size() - 1) {
                offsetPx += leadingPx;
            }
        }

        var totalHeight = marginTop + offsetPx + marginBottom;
        cachedMeasure = new MeasuredCache(
            List.copyOf(layouts), attributionFont, subAttributionFont, maxWidth, totalHeight);
        return cachedMeasure;
    }

    /**
     * Returns the index of the first sub-attribution line, or {@code -1} if there
     * are none. Used to place the extra gap above the attribution → sub-attribution
     * transition exactly once.
     */
    private static int firstSubAttributionIndex(List<AttributionLine> lines) {
        for (var i = 0; i < lines.size(); i++) {
            if (lines.get(i).font() != FontKey.ATTRIBUTION) {
                return i;
            }
        }

        return -1;
    }

    /**
     * Measures the rendered line box for {@code font}: the pixel bounds of
     * {@link #LINE_BOX_REFERENCE} rendered as vectors under
     * {@link GraphicUtils#SCREEN_FRC}. The bounds' {@code y} is the ink top
     * relative to the baseline (negative), so its negation is the ascent and the
     * bounds' {@code height} is the full vertical extent used to box every line.
     */
    private static LineBox measureLineBox(Font font) {
        var bounds = font.createGlyphVector(GraphicUtils.SCREEN_FRC, LINE_BOX_REFERENCE)
            .getPixelBounds(GraphicUtils.SCREEN_FRC, 0, 0);
        return new LineBox(-bounds.y, bounds.height);
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
