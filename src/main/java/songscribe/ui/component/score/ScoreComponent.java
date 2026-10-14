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

import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.font.TextLayout;
import javax.swing.JComponent;

import org.jspecify.annotations.Nullable;

import songscribe.dom.Song;
import songscribe.dom.Ss;
import songscribe.dom.ViewPx;
import songscribe.error.RuntimeError;
import songscribe.font.TextMeasurement;
import songscribe.ui.ViewScale;
import songscribe.ui.component.ScoreView;
import songscribe.ui.playback.PlaybackController;
import songscribe.util.GraphicUtils;
import songscribe.util.UIUtils;

/**
 * Abstract base class for score rendering components. A detached component (dialog previews,
 * exporters) still receives events, but each hook reads a null {@code scoreView} and does
 * nothing.
 */
public abstract class ScoreComponent extends JComponent {

    private final MouseAdapter mouseAdapter = new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent e) {
            var view = scoreView;

            if (view == null || !UIUtils.isLeftClick(e)) {
                return;
            }

            clicked(e, view);
        }

        @Override
        public void mousePressed(MouseEvent e) {
            var view = scoreView;

            if (view == null || !UIUtils.isLeftClick(e)) {
                return;
            }

            view.takeFocus();
            pressed(e, view);
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            var view = scoreView;

            if (view == null) {
                return;
            }

            released(e, view);
        }

        @Override
        public void mouseEntered(MouseEvent e) {
            var view = scoreView;

            if (view == null) {
                return;
            }

            entered(e, view);
        }

        @Override
        public void mouseExited(MouseEvent e) {
            var view = scoreView;

            if (view == null) {
                return;
            }

            exited(e, view);
        }
    };

    @Nullable
    protected Song song;

    /**
     * {@code null} when this component is detached. Read on demand through
     * {@link #getViewScale()} so a rebuilt tree never renders at a stale zoom.
     */
    @Nullable
    protected ScoreView scoreView;

    protected int marginTop = 0;
    protected int marginRight = 0;
    protected int marginBottom = 0;
    protected int marginLeft = 0;

    /** Null when this component centers on its own width. */
    private @Nullable Float contentXPx = null;

    protected ScoreComponent() {
        setOpaque(false);
        addMouseListener(mouseAdapter);
    }

    public void setSong(Song song) {
        this.song = song;
        revalidate();
        repaint();
    }

    public void setScoreView(ScoreView scoreView) {
        this.scoreView = scoreView;
    }

    /** {@code null} when detached. */
    public @Nullable ScoreView getScoreView() {
        return scoreView;
    }

    protected ViewScale getViewScale() {
        return scoreView != null ? scoreView.getViewScale() : ViewScale.IDENTITY;
    }

    protected ViewPx toViewPx(Ss ss) {
        return getViewScale().toViewPx(ss);
    }

    public Song getSong() {
        if (song == null) {
            throw RuntimeError.exit("song not initialized");
        }

        return song;
    }

    public void setMargin(int margin) {
        marginTop = margin;
        marginRight = margin;
        marginBottom = margin;
        marginLeft = margin;
    }

    public void setMargin(int top, int right, int bottom, int left) {
        marginTop = top;
        marginRight = right;
        marginBottom = bottom;
        marginLeft = left;
    }

    public int getMarginTop() {
        return marginTop;
    }

    public void setMarginTop(int marginTop) {
        this.marginTop = marginTop;
    }

    public int getMarginRight() {
        return marginRight;
    }

    public void setMarginRight(int marginRight) {
        this.marginRight = marginRight;
    }

    public int getMarginBottom() {
        return marginBottom;
    }

    public void setMarginBottom(int marginBottom) {
        this.marginBottom = marginBottom;
    }

    public int getMarginLeft() {
        return marginLeft;
    }

    public void setMarginLeft(int marginLeft) {
        this.marginLeft = marginLeft;
    }

    /**
     * @param contentX null to center based on own width
     */
    public void setContentX(@Nullable Float contentX) {
        contentXPx = contentX;
    }

    /** Null when this component centers on its own width. */
    public @Nullable Float getContentX() {
        return contentXPx;
    }

    /**
     * @param g2 graphics context with font already set
     */
    protected float resolveContentX(String text, Graphics2D g2) {
        if (contentXPx != null) {
            return contentXPx;
        }

        var textWidth = textBlockWidth(text, g2);
        // The measured text width comes from a zoom-scaled font, so center it against the
        // view-scaled (zoomed) line width, not the document-pixel width.
        // A centering bound, not a size: rounding it to a whole pixel would throw away
        // half a pixel of centering, and drawString takes a float anyway.
        var lineWidthPx = toViewPx(getSong().getLineWidthSs()).value();
        return (float) ((lineWidthPx - textWidth) / 2);
    }

    /**
     * The widest line's <em>advance</em>, not its ink width: centering an ink width against a
     * pen origin displaces the text by the first glyph's left side bearing. Lives here, not in
     * {@link TextMeasurement}, because it measures in the zoom-scaled font already set on
     * {@code g2}, whereas {@link TextMeasurement} answers in zoom-free document scale.
     *
     * @param g2 the paint-time graphics, with the font to measure in already set
     */
    protected static double textBlockWidth(String text, Graphics2D g2) {
        var context = g2.getFontRenderContext();
        var font = g2.getFont();
        var maxWidth = 0d;

        for (var line : text.split("\n")) {
            if (!line.isEmpty()) {
                maxWidth = Math.max(maxWidth, new TextLayout(line, font, context).getAdvance());
            }
        }

        return maxWidth;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        var g2 = initGraphics(g);
        render(g2);
    }

    protected Graphics2D initGraphics(Graphics g) {
        var g2 = (Graphics2D) g;
        GraphicUtils.setRenderingHints(g2);
        return g2;
    }

    protected abstract void render(Graphics2D g2);

    /**
     * Score text is drawn with no zoom transform on the {@code Graphics2D}, so the zoom must be
     * baked into the font.
     */
    protected Font zoomedFont(Font base) {
        return getViewScale().zoomedFont(base);
    }

    /** Prevents BoxLayout from expanding components beyond their natural size. */
    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }

    /**
     * The default answers false, so the click falls through to the score view's normal handling.
     * Takes no click point: a component's bounds are its hit area; one that needs the point
     * overrides {@link #clicked} instead.
     */
    protected boolean openEditor() {
        return false;
    }

    /**
     * {@code view} is the owning view, so attachment is a precondition carried by the signature.
     * <p>
     * A double-click arrives as two clicks and the first takes the cancel-and-deselect path, so
     * the selection is cleared before an editor opens.
     */
    protected void clicked(MouseEvent e, ScoreView view) {
        // Playback state is read from the playback controller rather than from the
        // sequencer because the action layer's DISABLE_WHEN_PLAYING flag does not reach
        // a mouse handler.
        if (UIUtils.isLeftDoubleClick(e) && !PlaybackController.isPlaying() && openEditor()) {
            return;
        }

        view.cancelPlacementAndDeselect();
    }

    /**
     * {@code view} is the owning view, so attachment is a precondition carried by the signature.
     */
    protected void pressed(MouseEvent e, ScoreView view) {
    }

    /**
     * {@code view} as in {@link #pressed}. Unlike {@link #clicked} and {@link #pressed}, not
     * filtered by button: a release of any button reaches it.
     */
    protected void released(MouseEvent e, ScoreView view) {
    }

    /**
     * {@code view} as in {@link #pressed}. Not filtered by button.
     */
    protected void entered(MouseEvent e, ScoreView view) {
    }

    /**
     * {@code view} as in {@link #pressed}. Not filtered by button.
     */
    protected void exited(MouseEvent e, ScoreView view) {
    }

}
