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

import module java.desktop;

import java.util.List;

import org.jspecify.annotations.Nullable;

import songscribe.dom.Ss;
import songscribe.ui.action.Actions;
import songscribe.ui.dialog.SongSettingsDialog;
import songscribe.util.GraphicUtils;
import songscribe.util.GraphicsState;
import songscribe.util.StringUtils;

/**
 * Abstract base for title-style text components (title, subtitle).
 * <p>
 * Handles wrap and measurement. Subclasses supply the text via
 * {@link #songText()} and an optional leading gap via {@link #topGapPx()}.
 * <p>
 * When {@link #songText()} is non-empty the component stacks {@link #topGapPx()} rows of leading
 * gap above the wrapped text block, so its total height is the gap plus the block, and it
 * reports the width of the text it draws — the widest wrapped line — not the song's line
 * width. The line width remains the wrap constraint only. Centering the component on the
 * page is the parent's responsibility; within the component each wrapped line is centered
 * on the component's own width.
 * When {@link #songText()} is empty the component collapses to {@code (0, 0)}
 * and no gap is emitted — a subtitle with no text takes up no space.
 * <p>
 * The preview mechanism ({@link #setPreview}) lets settings dialogs render unsaved text
 * without mutating the song — and without holding one, since a {@link Preview} supplies both
 * the text and the width to wrap it at.
 */
public abstract class BaseTitleComponent extends ScoreComponent {

    /**
     * Text this component draws in place of the song's own, and the width to wrap it at.
     *
     * <p>The two travel together because they are the two things this component would
     * otherwise read out of the song, and a preview that supplied only one of them would
     * still need a song for the other. Supplying both is what lets a settings dialog show
     * unsaved text without holding the document.
     *
     * @param text        the text to draw; empty draws nothing and collapses the component
     * @param wrapWidthSs the width the text may occupy before it wraps, in staff spaces
     */
    public record Preview(String text, double wrapWidthSs) {}

    /**
     * What to draw instead of the song, or {@code null} to draw the song.
     */
    @Nullable
    private Preview preview;

    /**
     * Draws {@code preview} in place of the song's own text and line width, or restores
     * rendering from the song when {@code null}.
     *
     * <p>A component showing a preview needs no song and never consults one, so this is the
     * whole of what a detached preview has to be given.
     */
    public void setPreview(@Nullable Preview preview) {
        this.preview = preview;
        revalidate();
        repaint();
    }

    /**
     * The part of the song this component displays, which Song Settings opens to and
     * focuses. {@link TitleComponent} and {@link SubtitleComponent} share a tab but are
     * edited in different fields, so each names its own.
     */
    protected abstract SongSettingsDialog.Section editorSection();

    /**
     * Opens Song Settings at {@link #editorSection()}, with that section's field focused —
     * so a double-click lands the caret in the very text that was clicked.
     * <p>
     * No hit testing is needed: this component is exactly as wide as the text it draws,
     * so a click that reached it is a click on the title.
     * <p>
     * The same property means an empty title or subtitle has no hit area at all —
     * {@link #getPreferredSize} returns {@code (0, 0)}, so there is nothing to
     * double-click and the field is reached through the menu instead. Accepted
     * deliberately: giving empty text a phantom click target would mean inventing bounds
     * for something that draws nothing, and every score would carry an invisible strip
     * that swallows clicks aimed at the staff below it. The gesture reveals what is
     * already on the page; it is not the way to put something there.
     * <p>
     * The dialog comes from {@code SONG_SETTINGS_ACTION} so the gesture and the menu item
     * share the one cached instance. That field is populated by {@code Actions.initialize},
     * which has run before any score component is inside a visible {@code ScoreView}; it
     * is deliberately not null-guarded, so a startup-ordering regression fails loudly
     * rather than becoming a gesture that silently does nothing. The gesture's playback
     * condition is applied by the caller and is deliberately not re-derived here.
     */
    @Override
    public boolean openEditor() {
        if (song == null) {
            return false;
        }

        Actions.SONG_SETTINGS_ACTION.getDialog().show(editorSection());
        return true;
    }

    /**
     * The line width in pixels the text is allowed to occupy, used for wrapping only:
     * the preview's width when one is set, otherwise the document line width, scaled by this
     * component's zoom. On the score it tracks the view zoom; a detached preview (no
     * {@code ScoreView}) resolves to {@link songscribe.ui.ViewScale#IDENTITY} and so renders
     * at natural size.
     */
    private int lineWidthPx() {
        var currentPreview = preview;

        if (currentPreview != null) {
            return toViewPx(new Ss(currentPreview.wrapWidthSs())).roundedPx();
        }

        var theSong = song;

        if (theSong == null) {
            return 0;
        }

        return toViewPx(new Ss(theSong.getLineWidthSs())).roundedPx();
    }

    /**
     * Returns the song text for this component.
     * <p>
     * Subclasses return the appropriate field from the song (e.g. numbered title,
     * subtitle). Called each time the component is painted or measured.
     *
     * @return the text to display, or empty string for none
     */
    protected abstract String songText();

    /**
     * Returns the top gap in pixels to prepend before the text block.
     * <p>
     * Default is {@code 0}. Override to insert spacing above this component
     * (e.g. the subtitle gap between title and subtitle).
     *
     * @return top gap in pixels
     */
    protected int topGapPx() {
        return 0;
    }

    /**
     * The text to draw, or null when there is nothing to draw.
     * <p>
     * Deliberately answerable without {@link FontMetrics}. A component that has neither a
     * song nor a preview has never been given a font either, and
     * {@code getFontMetrics(null)} throws, so {@link #getPreferredSize} has to settle this
     * <em>before</em> it measures.
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
     * One wrapped line together with the ink it paints.
     * <p>
     * {@code ink} is null when the line has no ink at all — a blank line — in which case
     * it neither contributes width nor needs positioning.
     */
    private record MeasuredLine(String text, @Nullable Rectangle2D ink) {

        /**
         * The width this line needs, rounded up because it is a size: leaving a fraction
         * of a pixel unreserved is leaving a fraction of a pixel clipped.
         */
        int widthPx() {
            return ink == null ? 0 : (int) Math.ceil(ink.getWidth());
        }

        /**
         * The x to draw at so this line's ink is centered in {@code containerWidth}.
         * <p>
         * Centering the ink rather than the advance box centers the box the reader
         * actually sees: the advance box excludes ink past the last glyph's advance and
         * includes the left side bearing. Subtracting the ink's own origin offset pulls a
         * negative left bearing — the overhang of a "W" — back inside the component
         * rather than letting it paint outside.
         * <p>
         * Fractional, and drawn at a fractional x, so that a component sized to
         * {@link #widthPx()} contains its widest line exactly. Rounding to a whole pixel
         * here would put up to half a pixel of ink back outside the bounds the width was
         * chosen to guarantee.
         */
        float centeredX(int containerWidth) {
            if (ink == null) {
                return 0;
            }

            return (float) ((containerWidth - ink.getWidth()) / 2 - ink.getX());
        }
    }

    /**
     * The component's text measured once — the font's metrics and every wrapped line's
     * ink, all taken under {@link GraphicUtils#SCREEN_FRC}.
     * <p>
     * Sizing and painting both go through this, which is what makes "the component is
     * exactly as wide as the text it draws" true rather than nearly true. Measuring the
     * two independently let them wrap the text at different words, and the wider of the
     * two results would then be painted into bounds sized for the narrower one.
     * <p>
     * {@code lines} is never empty: {@link #measureText} is reached only for non-empty
     * text, and {@link StringUtils#wrapText} always yields at least one line.
     */
    private record MeasuredText(FontMetrics metrics, List<MeasuredLine> lines) {

        /** The width of the widest line, and so of the component. */
        int maxWidthPx() {
            return lines.stream().mapToInt(MeasuredLine::widthPx).max().orElse(0);
        }

        /** The wrapped lines' own height, before any allowance for overshooting ink. */
        int blockHeightPx() {
            return GraphicUtils.getTextBlockHeight(metrics, lines.size());
        }

        /** Room above the first baseline for ink that overshoots the nominal ascent. */
        int topInkPaddingPx() {
            return GraphicUtils.extraInkAbove(lines.getFirst().ink(), metrics.getAscent());
        }

        /** Room below the last baseline for ink that overshoots the nominal descent. */
        int bottomInkPaddingPx() {
            return GraphicUtils.extraInkBelow(lines.getLast().ink(), metrics.getDescent());
        }
    }

    /**
     * Wraps {@code text} to {@link #lineWidthPx()} and captures each resulting line's ink.
     * <p>
     * {@link #lineWidthPx()} is the width the text is allowed to occupy, not the
     * component's width; the component's width comes from
     * {@link MeasuredText#maxWidthPx()} on the lines produced here.
     */
    private MeasuredText measureText(String text) {
        var font = zoomedFont(getFont());
        var metrics = GraphicUtils.fontMetrics(font);
        var lines = StringUtils
            .wrapText(text, metrics, lineWidthPx())
            .stream()
            .map(line -> new MeasuredLine(line, GraphicUtils.visualBounds(line, font)))
            .toList();

        return new MeasuredText(metrics, lines);
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
            g2.setColor(Color.BLACK);

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
