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
import songscribe.ui.action.Actions;
import songscribe.ui.dialog.SongSettingsDialog;
import songscribe.util.GraphicsState;
import songscribe.util.StringUtils;

/**
 * Abstract base for title-style text components (title, subtitle).
 * <p>
 * Text that wraps past {@link #maxRenderedLines()} is clipped rather than shrunk, and the drawn
 * block takes the overflow colour. The component always sizes to exactly what it draws: its
 * width is the widest wrapped line, not the song's line width, and with empty
 * {@link #songText()} it collapses to {@code (0, 0)}. Centering on the page is the parent's
 * responsibility.
 */
public abstract class BaseTitleComponent extends ScoreComponent {

    /**
     * Lets a settings dialog show unsaved text without holding the document: both values travel
     * together, since supplying only one would still need a song for the other.
     */
    public record Preview(String text, Ss wrapWidthSs) {}

    /** {@code null} draws the song. */
    @Nullable
    private Preview preview;

    public void setPreview(@Nullable Preview preview) {
        this.preview = preview;
        revalidate();
        repaint();
    }

    protected abstract SongSettingsDialog.Section editorSection();

    /**
     * No hit testing: the component is exactly as wide as its text, so a click that reached it
     * is a click on the title. An empty title therefore has no hit area; this is deliberate,
     * since a phantom target would swallow clicks aimed at the staff below.
     * <p>
     * {@code SONG_SETTINGS_ACTION} is deliberately not null-guarded, so a startup-ordering
     * regression fails loudly instead of becoming a gesture that silently does nothing. The
     * playback condition is applied by {@link ScoreComponent} before this method is reached.
     */
    @Override
    protected boolean openEditor() {
        Actions.SONG_SETTINGS_ACTION.openAt(editorSection());
        return true;
    }

    private int lineWidthPx() {
        var currentPreview = preview;

        if (currentPreview != null) {
            return toViewPx(currentPreview.wrapWidthSs()).sizePx();
        }

        var theSong = song;

        if (theSong == null) {
            return 0;
        }

        return toViewPx(theSong.getLineWidthSs()).sizePx();
    }

    /**
     * @return empty string for none
     */
    protected abstract String songText();

    /**
     * @return at least 1
     */
    protected abstract int maxRenderedLines();

    protected int topGapPx() {
        return 0;
    }

    /**
     * Null when there is nothing to draw. Answerable without {@link FontMetrics}: a component
     * with neither song nor preview has no font, and {@code getFontMetrics(null)} throws.
     */
    private @Nullable String textToRenderOrNull() {
        var currentPreview = preview;
        String text;

        if (currentPreview != null) {
            text = currentPreview.text();
        } else if (song != null) {
            text = songText();
        } else {
            return null;
        }

        return text.isEmpty() ? null : text;
    }

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
     * Sizing and painting both go through this, so the component is exactly as wide as the text
     * it draws. {@code lines} is never empty: {@link #topInkPaddingPx} and
     * {@link #bottomInkPaddingPx} index it unguarded, which rests on
     * {@link #maxRenderedLines()} being at least 1.
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
     * Everything {@link #measureText} reads: equal keys must produce equal measurements.
     */
    private record MeasureKey(String text, Font font, int lineWidthPx) {}

    private record Memo(MeasureKey key, MeasuredText measured) {}

    /** Null before the first measurement. */
    @Nullable
    private Memo memo;

    /**
     * Memoized under {@link MeasureKey}, which holds every input that can vary.
     * <strong>No invalidation hook is needed or wanted:</strong> nothing here can go stale, so
     * do not add a listener to clear it.
     * <p>
     * {@link #maxRenderedLines()} is deliberately not in the key because it is fixed per
     * component class; a subclass whose cap varied would need it added.
     */
    private MeasuredText measureText(String text) {
        var font = zoomedFont(getFont());
        var lineWidthPx = lineWidthPx();
        var key = new MeasureKey(text, font, lineWidthPx);
        var cached = memo;

        if (cached != null && key.equals(cached.key())) {
            return cached.measured();
        }

        var metrics = TextMeasurement.fontMetrics(font);
        var wrapped = StringUtils.wrapText(text, metrics, lineWidthPx);
        var renderedCount = Math.min(wrapped.size(), maxRenderedLines());
        var lines = wrapped
            .subList(0, renderedCount)
            .stream()
            .map(line -> new MeasuredLine(line, TextMeasurement.visualBounds(line, font)))
            .toList();
        var measured = new MeasuredText(metrics, lines, renderedCount < wrapped.size());

        memo = new Memo(key, measured);

        return measured;
    }

    @Override
    protected void render(Graphics2D g2) {
        // A bare JComponent does not fill its background even when opaque, so do
        // it here. On the score the component is transparent (opaque == false);
        // the settings-dialog preview makes it opaque to paint the page color.
        if (isOpaque()) {
            try (var _ = GraphicsState.save(g2, GraphicsState.Property.COLOR)) {
                g2.setColor(getBackground());
                g2.fillRect(0, 0, getWidth(), getHeight());
            }
        }

        var text = textToRenderOrNull();

        if (text == null) {
            return;
        }

        try (var _ = GraphicsState.save(
            g2,
            GraphicsState.Property.FONT,
            GraphicsState.Property.COLOR
        )) {
            var measured = measureText(text);
            var metrics = measured.metrics();
            g2.setFont(metrics.getFont());

            // The overflow colour is the whole of the signal that the text runs past what
            // this component draws; there is no alert, and no selection colour on these
            // components to take precedence over it.
            g2.setColor(
                measured.clamped()
                    ? FlatLafProps.getColor(FlatLafKey.SCORE_OVERFLOW_COLOR)
                    : Color.BLACK
            );

            // Draw each line, offset by topGapPx so the gap appears above the text block.
            // The component is as wide as its widest line, so each line centers within the
            // component's own width rather than within the song's line width.
            var lineHeight = metrics.getHeight();
            var containerWidth = getWidth();
            var y = (float) (topGapPx() + measured.topInkPaddingPx() + metrics.getAscent());

            for (var line : measured.lines()) {
                g2.drawString(line.text(), line.centeredX(containerWidth), y);
                y += lineHeight;
            }
        }
    }

    @Override
    public Dimension getPreferredSize() {
        // Settled before measuring: with no text there is no font to build metrics from.
        var text = textToRenderOrNull();

        if (text == null) {
            return new Dimension(0, 0);
        }

        var measured = measureText(text);

        // Top gap only applies when there is text to display. The ink padding is what
        // keeps a face whose glyphs overshoot the nominal ascent/descent — a deep script
        // descender, say — from being clipped by the component's own bounds.
        var height = topGapPx()
            + measured.topInkPaddingPx()
            + measured.blockHeightPx()
            + measured.bottomInkPaddingPx();

        return new Dimension(measured.maxWidthPx(), height);
    }
}
