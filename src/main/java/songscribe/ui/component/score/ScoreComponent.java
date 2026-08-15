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
import javax.swing.JComponent;

import org.jspecify.annotations.Nullable;

import songscribe.dom.Song;
import songscribe.dom.Ss;
import songscribe.dom.ViewPx;
import songscribe.error.RuntimeError;
import songscribe.ui.ViewScale;
import songscribe.ui.component.ScoreView;
import songscribe.util.GraphicUtils;

/**
 * Abstract base class for score rendering components.
 * <p>
 * Provides common functionality for components that render parts of a music score:
 * <ul>
 *   <li>Antialiasing setup in {@link #paintComponent(Graphics)}</li>
 *   <li>Template method pattern via {@link #render(Graphics2D)}</li>
 *   <li>Margin system integration via layout constants</li>
 * </ul>
 * <p>
 * Subclasses must implement {@link #render(Graphics2D)} to perform their specific rendering.
 */
public abstract class ScoreComponent extends JComponent {

    /** Reference to the song model. */
    @Nullable
    protected Song song;

    /**
     * The owning {@link ScoreView}, or {@code null} when this component is detached
     * (dialog previews, exporters). Read on demand through {@link #getViewScale()} so
     * a rebuilt tree never renders at a stale zoom.
     */
    @Nullable
    protected ScoreView scoreView;

    /** Margin values (top, right, bottom, left) in pixels. */
    protected int marginTop = 0;
    protected int marginRight = 0;
    protected int marginBottom = 0;
    protected int marginLeft = 0;

    /** X position for content (used for union width centering). */
    private float contentXPx = -1;

    /**
     * Creates a new ScoreComponent.
     */
    protected ScoreComponent() {
        setOpaque(false);
    }

    /**
     * Sets the song model for this component.
     *
     * @param song The song to render
     */
    public void setSong(Song song) {
        this.song = song;
        revalidate();
        repaint();
    }

    /**
     * Sets the owning {@link ScoreView}. On-score components are given one; detached
     * previews are not, so they fall back to {@link ViewScale#IDENTITY}.
     *
     * @param scoreView the owning score view
     */
    public void setScoreView(ScoreView scoreView) {
        this.scoreView = scoreView;
    }

    /**
     * Returns the owning {@link ScoreView}, or {@code null} when detached.
     */
    public @Nullable ScoreView getScoreView() {
        return scoreView;
    }

    /**
     * Returns this component's {@link ViewScale}: the owning view's when attached,
     * otherwise the shared read-only {@link ViewScale#IDENTITY} (natural size).
     */
    protected ViewScale getViewScale() {
        return scoreView != null ? scoreView.getViewScale() : ViewScale.IDENTITY;
    }

    /** Converts a staff-space distance to view pixels through this component's zoom. */
    protected ViewPx toViewPx(Ss ss) {
        return getViewScale().toViewPx(ss);
    }

    /**
     * Returns the song model.
     */
    public Song getSong() {
        if (song == null) {
            throw RuntimeError.exit("song not initialized");
        }

        return song;
    }

    /**
     * Sets uniform margin on all sides.
     *
     * @param margin Margin in pixels
     */
    public void setMargin(int margin) {
        marginTop = margin;
        marginRight = margin;
        marginBottom = margin;
        marginLeft = margin;
    }

    /**
     * Sets CSS-style margins.
     *
     * @param top    Top margin in pixels
     * @param right  Right margin in pixels
     * @param bottom Bottom margin in pixels
     * @param left   Left margin in pixels
     */
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
     * Sets the X position for content rendering.
     * <p>
     * Used by TextPanel to achieve union width centering across
     * all text components.
     *
     * @param contentX X position, or -1 to center based on own width
     */
    public void setContentX(float contentX) {
        contentXPx = contentX;
    }

    /**
     * Returns the X position for content rendering.
     */
    public float getContentX() {
        return contentXPx;
    }

    /**
     * Resolves the X position for content rendering.
     * <p>
     * Returns the value set by {@link #setContentX} if non-negative,
     * otherwise computes a centered X based on the given text width.
     *
     * @param text text to measure for centering
     * @param g2   graphics context with font already set
     * @return X position in pixels
     */
    protected float resolveContentX(String text, Graphics2D g2) {
        if (contentXPx >= 0) {
            return contentXPx;
        }

        var textWidth = GraphicUtils.getTextBlockWidth(text, g2);
        // The measured text width comes from a zoom-scaled font, so center it against the
        // view-scaled (zoomed) line width, not the document-pixel width.
        var lineWidthPx = toViewPx(new Ss(getSong().getLineWidthSs())).roundedPx();
        return (float) ((lineWidthPx - textWidth) / 2);
    }

    /**
     * Paints this component with antialiasing enabled.
     * <p>
     * Sets up Graphics2D with antialiasing hints, then delegates to
     * {@link #render(Graphics2D)} for actual rendering.
     *
     * @param g The graphics context
     */
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        var g2 = initGraphics(g);
        render(g2);
    }

    /**
     * Initializes a Graphics2D context with common settings.
     * <p>
     * Sets up antialiasing and other rendering hints for high-quality output.
     *
     * @param g The graphics context to initialize
     * @return The initialized Graphics2D
     */
    protected Graphics2D initGraphics(Graphics g) {
        var g2 = (Graphics2D) g;
        GraphicUtils.setRenderingHints(g2);
        return g2;
    }

    /**
     * Template method for subclass rendering.
     * <p>
     * Subclasses implement this method to perform their specific rendering.
     * The graphics context is already set up with antialiasing.
     *
     * @param g2 The Graphics2D context with antialiasing enabled
     */
    protected abstract void render(Graphics2D g2);

    /**
     * Returns {@code base} scaled to the current zoom. Score text is drawn in absolute
     * pixel coordinates with no zoom transform on the {@code Graphics2D}, so the zoom
     * must be baked into the font; this is the shared entry point score text components
     * use for both painting and measuring.
     */
    protected Font zoomedFont(Font base) {
        return getViewScale().zoomedFont(base);
    }

    /**
     * Returns the maximum size for this component.
     * <p>
     * Returns the preferred size to prevent BoxLayout from expanding
     * components beyond their natural size.
     */
    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }

    /**
     * Opens the editor for what this component displays, answering whether one opened.
     * <p>
     * Called by {@code ScoreInputHandler} when a left double-click outside playback
     * lands on this component. The default answers false, so a component that displays
     * nothing editable needs no override and the click falls through to the score
     * view's normal handling.
     * <p>
     * Takes no click point: a component's bounds are its hit area. Should a component
     * ever need to distinguish where on itself it was clicked, the conversion from the
     * dispatching {@code ScoreView}'s coordinates into component coordinates belongs at
     * the dispatch site, so that no override can get the coordinate space wrong.
     */
    public boolean openEditor() {
        return false;
    }

}
