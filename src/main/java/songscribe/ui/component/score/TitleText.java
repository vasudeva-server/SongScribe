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

package songscribe.ui.component.score;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.util.List;

import org.jspecify.annotations.Nullable;

import songscribe.dom.Ss;
import songscribe.font.TextMeasurement;
import songscribe.ui.FlatLafKey;
import songscribe.ui.FlatLafProps;
import songscribe.ui.ViewScale;
import songscribe.util.GraphicsState;
import songscribe.util.StringUtils;

/**
 * Wraps, measures and draws one title-style text block for a component that sizes to exactly
 * what it draws: its width is the widest wrapped line, not the wrap width, and empty text has
 * size {@code (0, 0)}. Centering the block is the parent's responsibility.
 * <p>
 * The block that {@link TitleKind#maxRenderedLines()} clips takes the overflow color, the same
 * as that of a staff line holding more than the staff can show, because both mean the page
 * shows less than the document holds.
 */
final class TitleText {

    /**
     * Everything one block is drawn from. {@code font} is the unzoomed font; the block applies
     * {@code scale} to it and to {@code wrapWidthSs}.
     */
    record Block(String text, Ss wrapWidthSs, Font font, ViewScale scale) {}

    /**
     * {@code ink} is null for a blank line.
     */
    private record MeasuredLine(String text, @Nullable Rectangle2D ink) {

        /**
         * Rounded up: a fraction of a pixel left unreserved is a fraction clipped.
         */
        int widthPx() {
            return ink == null ? 0 : (int) Math.ceil(ink.getWidth());
        }

        /**
         * Centers the ink, not the advance box, and pulls a negative left bearing back inside the
         * component. Fractional, so a component sized to {@link #widthPx()} contains its widest
         * line exactly.
         */
        float centeredX(int containerWidth) {
            if (ink == null) {
                return 0;
            }

            return (float) ((containerWidth - ink.getWidth()) / 2 - ink.getX());
        }
    }

    /**
     * {@code lines} is never empty: {@link #topInkPaddingPx} and {@link #bottomInkPaddingPx}
     * index it unguarded, which rests on {@link TitleKind#maxRenderedLines()} being at least 1.
     */
    private record MeasuredText(FontMetrics metrics, List<MeasuredLine> lines, boolean clamped) {

        int maxWidthPx() {
            return lines.stream().mapToInt(MeasuredLine::widthPx).max().orElse(0);
        }

        int blockHeightPx() {
            return TextMeasurement.textBlockHeight(metrics, lines.size());
        }

        int topInkPaddingPx() {
            return TextMeasurement.extraInkAbovePx(lines.getFirst().ink(), metrics.getAscent());
        }

        int bottomInkPaddingPx() {
            return TextMeasurement.extraInkBelowPx(lines.getLast().ink(), metrics.getDescent());
        }
    }

    /**
     * Everything {@link #measure} reads: equal keys must produce equal measurements.
     */
    private record MeasureKey(String text, Font font, int lineWidthPx) {}

    private record Memo(MeasureKey key, MeasuredText measured) {}

    private final TitleKind kind;

    /** Null before the first measurement. */
    @Nullable
    private Memo memo;

    TitleText(TitleKind kind) {
        this.kind = kind;
    }

    /**
     * The ink padding keeps a face whose glyphs overshoot the nominal ascent and descent — a deep
     * script descender, say — from being clipped by the component's own bounds.
     */
    Dimension preferredSize(Block block) {
        // Settled before measuring: an empty block may have no font to build metrics from.
        if (block.text().isEmpty()) {
            return new Dimension(0, 0);
        }

        var measured = measure(block);
        var height = kind.topGapPx(block.scale())
            + measured.topInkPaddingPx()
            + measured.blockHeightPx()
            + measured.bottomInkPaddingPx();

        return new Dimension(measured.maxWidthPx(), height);
    }

    /**
     * @param containerWidth the component's width; each line centers within it, not within the
     *                       wrap width, because the component is as wide as its widest line
     */
    void draw(Graphics2D g2, Block block, int containerWidth) {
        if (block.text().isEmpty()) {
            return;
        }

        try (var _ = GraphicsState.save(
            g2,
            GraphicsState.Property.FONT,
            GraphicsState.Property.COLOR
        )) {
            var measured = measure(block);
            var metrics = measured.metrics();
            g2.setFont(metrics.getFont());

            // The overflow color is the whole of the signal that the text runs past what this
            // block draws; there is no alert, and no selection color on a title to take
            // precedence over it.
            g2.setColor(
                measured.clamped()
                    ? FlatLafProps.getColor(FlatLafKey.SCORE_OVERFLOW_COLOR)
                    : Color.BLACK
            );

            var lineHeight = metrics.getHeight();
            var y = (float) (kind.topGapPx(block.scale()) + measured.topInkPaddingPx() + metrics.getAscent());

            for (var line : measured.lines()) {
                g2.drawString(line.text(), line.centeredX(containerWidth), y);
                y += lineHeight;
            }
        }
    }

    /**
     * Memoized under {@link MeasureKey}, which holds every input that can vary.
     * <strong>No invalidation hook is needed or wanted:</strong> nothing here can go stale, so
     * do not add a listener to clear it.
     * <p>
     * {@link TitleKind#maxRenderedLines()} is deliberately not in the key because it is fixed for
     * the kind this instance draws.
     */
    private MeasuredText measure(Block block) {
        var scale = block.scale();
        var font = scale.zoomedFont(block.font());
        var lineWidthPx = scale.toViewPx(block.wrapWidthSs()).sizePx();
        var key = new MeasureKey(block.text(), font, lineWidthPx);
        var cached = memo;

        if (cached != null && key.equals(cached.key())) {
            return cached.measured();
        }

        var metrics = TextMeasurement.fontMetrics(font);
        var wrapped = StringUtils.wrapText(block.text(), metrics, lineWidthPx);
        var renderedCount = Math.min(wrapped.size(), kind.maxRenderedLines());
        var lines = wrapped
            .subList(0, renderedCount)
            .stream()
            .map(line -> new MeasuredLine(line, TextMeasurement.visualBounds(line, font)))
            .toList();
        var measured = new MeasuredText(metrics, lines, renderedCount < wrapped.size());

        memo = new Memo(key, measured);

        return measured;
    }
}
