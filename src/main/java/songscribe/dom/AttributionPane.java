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
import java.awt.geom.Rectangle2D;
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
 *  Invalidation points:
 *  setSong(song)              → clears both cache slots [Song metadata change]
 *  setOverrideLines(lines)    → clears both cache slots [override lines change]
 *  fonts / zoom change (passed as params) → each slot is keyed on font identity and
 *                                    the zoom factor; a mismatch forces a fresh
 *                                    measure of that slot
 *
 *  Natural-scale and zoomed measurements are cached separately, so the layout pass
 *  (always natural) and the paint pass (at the view zoom) never evict each other.
 *
 *  Measure / cache:
 *  getContentWidthPx(aFont, saFont)  → builds lines via formatter if the natural slot
 *                                       is empty or its fonts differ; stores MeasuredCache
 *  getContentHeightPx(aFont, saFont) → same slot; sums line heights + margins
 *  Both always measure at NATURAL_ZOOM_FACTOR, so the size is zoom-invariant.
 *
 *  Render:
 *  render(g2, xPx, yPx, widthPx, aFont, saFont, zoomFactor) → reuses the matching slot
 *                                                   (same fonts + zoom) or re-measures
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
    static final double LEADING_SS = 0.5;

    /**
     * Additional vertical gap, in staff spaces, inserted above the first
     * sub-attribution line — at the transition from attribution to
     * sub-attribution lines — on top of the normal {@link #LEADING_SS} leading.
     */
    static final double SUB_ATTRIBUTION_GAP_SS = 0.5;

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

    /**
     * Zoom factor for a natural-scale (unzoomed) measurement. The public
     * measurement API always measures at natural scale so the returned size is
     * zoom-invariant; zoom is applied only when {@link #render} paints.
     * <p>
     * Public so callers that render outside any zoomed view — the settings dialog
     * preview, exporters — can name the constant rather than passing a bare literal.
     */
    public static final double NATURAL_ZOOM_FACTOR = 1.0;

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
     * Cached natural-scale measurement — the one the measurement API always asks
     * for. Null means a fresh measure is needed.
     * Invalidated by {@link #setSong(Song)} and {@link #setOverrideLines(List)}.
     * Also invalidated when the fonts differ from those stored in the cache.
     */
    @Nullable
    private MeasuredCache cachedNaturalMeasure;

    /**
     * Cached zoomed measurement, kept in its own slot so it cannot evict
     * {@link #cachedNaturalMeasure}. The layout pass measures at natural scale while
     * the very next paint measures at the view zoom with zoomed fonts, so a single
     * shared slot would have the two passes evict each other on every layout —
     * which happens per mouse-drag tick while dragging the attribution block.
     */
    @Nullable
    private MeasuredCache cachedZoomedMeasure;

    // -------------------------------------------------------------------------
    // Cached measurement record
    // -------------------------------------------------------------------------

    /**
     * Holds the fully-laid-out attribution lines together with the fonts used to
     * produce them and the resulting content size. The per-line {@link LineLayout}
     * captures everything {@link #render} needs, so rendering is a straight walk
     * over precomputed data with no re-measurement.
     * Re-computed whenever the Song, override lines, fonts, or zoom factor change.
     * <p>
     * {@code marginTopPx} is the zoom-scaled top margin; storing it here rather than
     * re-deriving it in {@link #render} keeps the measure and render passes from
     * drifting apart.
     * <p>
     * Package-private, like {@link #measure}, so the measurement test can assert on
     * the zoom-scaled layout directly instead of inferring it from paint calls.
     */
    record MeasuredCache(
        List<LineLayout> layouts,
        Font attributionFont,
        Font subAttributionFont,
        double zoomFactor,
        double marginTopPx,
        double contentWidthPx,
        double contentHeightPx
    ) {}

    /**
     * A single attribution line, fully positioned by {@link #measure}. Holds the
     * text and its role font, the ink bounds ({@code null} for empty text), and
     * the baseline's vertical offset in pixels from the top of the content block
     * (i.e. from {@code marginTop}). {@link #render} draws each line directly from
     * these values; {@link #measure} derives the content size from them. Keeping
     * the layout in one place means the measure and render passes cannot drift.
     */
    private record LineLayout(String text, Font font, @Nullable Rectangle2D inkBounds, double baselineOffsetPx) {}

    /**
     * The rendered vertical extent of {@link #LINE_BOX_REFERENCE} in a given font.
     * {@code ascentPx} is the ink distance above the baseline; {@code heightPx}
     * is the full ink height (ascent + descent). Recomputed per measure pass.
     */
    private record LineBox(double ascentPx, double heightPx) {}

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
        cachedNaturalMeasure = null;
        cachedZoomedMeasure = null;
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
        var cache = measure(attributionFont, subAttributionFont, NATURAL_ZOOM_FACTOR);
        return new DocPx(cache.contentWidthPx()).ceilPx();
    }

    /**
     * Returns the total content height: {@code marginTop + Σ line heights + marginBottom}.
     *
     * @param attributionFont    font for {@link FontKey#ATTRIBUTION} lines
     * @param subAttributionFont font for {@link FontKey#SUB_ATTRIBUTION} lines
     * @return total height in pixels
     */
    public int getContentHeightPx(Font attributionFont, Font subAttributionFont) {
        var cache = measure(attributionFont, subAttributionFont, NATURAL_ZOOM_FACTOR);
        return new DocPx(cache.contentHeightPx()).ceilPx();
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
        var cache = measure(attributionFont, subAttributionFont, NATURAL_ZOOM_FACTOR);
        return new Dimension(
            new DocPx(cache.contentWidthPx()).ceilPx(),
            new DocPx(cache.contentHeightPx()).ceilPx());
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
     * @param zoomFactor         view zoom factor; scales the staff-space leading, the
     *                           sub-attribution gap, and the margins so the block scales
     *                           uniformly with the (already zoomed) fonts. Pass
     *                           {@value #NATURAL_ZOOM_FACTOR} when unzoomed.
     */
    public void render(
        Graphics2D g2,
        double xPx,
        double yPx,
        double widthPx,
        Font attributionFont,
        Font subAttributionFont,
        double zoomFactor
    ) {
        var cache = measure(attributionFont, subAttributionFont, zoomFactor);
        var layouts = cache.layouts();

        if (layouts.isEmpty()) {
            return;
        }

        try (var _ = GraphicsState.save(
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
                var drawY = (float) (yPx + cache.marginTopPx() + layout.baselineOffsetPx());
                // Center the ink rectangle, then shift back by bounds.x so a negative
                // left bearing (e.g. the "W" in "Words") does not overhang the box.
                var drawX = (float) (xPx + (widthPx - bounds.getWidth()) / 2.0 - bounds.getX());
                g2.drawString(layout.text(), drawX, drawY);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Internal: measure → cache
    // -------------------------------------------------------------------------

    /**
     * Returns the cached measurement, or computes a fresh one if the matching cache
     * slot is null or its fonts or zoom factor differ from those requested.
     * <p>
     * Natural-scale and zoomed measurements live in separate slots so the layout
     * pass (always natural) and the paint pass (at the view zoom) cannot evict each
     * other. Package-private so the measurement test can assert on the zoom-scaled
     * layout without going through a mocked {@code Graphics2D}.
     *
     * @param attributionFont    font for {@link FontKey#ATTRIBUTION} lines
     * @param subAttributionFont font for {@link FontKey#SUB_ATTRIBUTION} lines
     * @param zoomFactor         scales the staff-space leading, sub-attribution gap,
     *                           and margins; pass {@value #NATURAL_ZOOM_FACTOR} to
     *                           measure at natural scale
     */
    MeasuredCache measure(Font attributionFont, Font subAttributionFont, double zoomFactor) {
        var isNaturalScale = zoomFactor == NATURAL_ZOOM_FACTOR;
        var cached = isNaturalScale ? cachedNaturalMeasure : cachedZoomedMeasure;

        if (cached != null
            && cached.zoomFactor() == zoomFactor
            && cached.attributionFont().equals(attributionFont)
            && cached.subAttributionFont().equals(subAttributionFont)) {
            return cached;
        }

        var lines = resolveLines();
        var attributionBox = measureLineBox(attributionFont);
        var subAttributionBox = measureLineBox(subAttributionFont);

        // Leading and the sub-attribution gap are fixed staff-space distances, so they
        // scale with the view zoom exactly like the (caller-zoomed) fonts do. Keeping
        // them fractional — never rounding to whole pixels — is what stops the line
        // spacing from jumping as the zoom factor sweeps across pixel boundaries.
        var leadingPx = ScaleContext.ssToPx(LEADING_SS) * zoomFactor;
        var subAttributionGapPx = ScaleContext.ssToPx(SUB_ATTRIBUTION_GAP_SS) * zoomFactor;

        // The extra gap sits above the first sub-attribution line, but only when an
        // attribution line precedes it; a sub-attribution line at index 0 has nothing
        // to separate from and gets no gap.
        var firstSubAttributionIndex = firstSubAttributionIndex(lines);

        var layouts = new ArrayList<LineLayout>(lines.size());
        var maxWidthPx = 0.0;
        var offsetPx = 0.0;

        for (var i = 0; i < lines.size(); i++) {
            var line = lines.get(i);
            var isAttribution = line.font() == FontKey.ATTRIBUTION;
            var font = isAttribution ? attributionFont : subAttributionFont;
            var lineBox = isAttribution ? attributionBox : subAttributionBox;

            if (i > 0 && i == firstSubAttributionIndex) {
                offsetPx += subAttributionGapPx;
            }

            var bounds = GraphicUtils.visualBounds(line.text(), font);

            if (bounds != null) {
                maxWidthPx = Math.max(maxWidthPx, bounds.getWidth());
            }

            layouts.add(new LineLayout(line.text(), font, bounds, offsetPx + lineBox.ascentPx()));
            offsetPx += lineBox.heightPx();

            if (i < lines.size() - 1) {
                offsetPx += leadingPx;
            }
        }

        // Margins scale with zoom for the same reason the leading does: they are fixed
        // distances around a block whose fonts the caller has already zoomed, so leaving
        // them at natural size would shrink them relative to the text as zoom grows.
        var marginTopPx = marginTop * zoomFactor;
        var totalHeightPx = marginTopPx + offsetPx + marginBottom * zoomFactor;
        var measured = new MeasuredCache(
            List.copyOf(layouts), attributionFont, subAttributionFont, zoomFactor,
            marginTopPx, maxWidthPx, totalHeightPx);

        if (isNaturalScale) {
            cachedNaturalMeasure = measured;
        } else {
            cachedZoomedMeasure = measured;
        }

        return measured;
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
     * Measures the rendered line box for {@code font}: the fractional visual (ink)
     * bounds of {@link #LINE_BOX_REFERENCE}, via
     * {@link GraphicUtils#visualBounds(String, Font)}. The bounds' {@code y} is the
     * ink top relative to the baseline (negative), so
     * {@link GraphicUtils#inkHeight(Rectangle2D)} yields the ascent, and the bounds'
     * {@code height} is the full vertical extent used to box every line.
     * <p>
     * The fractional (outline) bounds rather than device-pixel-snapped ones are what
     * let the line box scale linearly with the zoomed font, so the spacing does not
     * jump as the zoom factor changes.
     */
    private static LineBox measureLineBox(Font font) {
        // LINE_BOX_REFERENCE is a non-empty constant, so the bounds are never null.
        var bounds = GraphicUtils.visualBounds(LINE_BOX_REFERENCE, font);

        if (bounds == null) {
            return new LineBox(0, 0);
        }

        return new LineBox(GraphicUtils.inkHeight(bounds), bounds.getHeight());
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
