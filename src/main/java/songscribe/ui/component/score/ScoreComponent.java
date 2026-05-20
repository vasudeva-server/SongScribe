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

import songscribe.error.RuntimeError;

import org.jspecify.annotations.Nullable;

import songscribe.dom.Song;
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
        return (float) ((getSong().getLineWidthPx() - textWidth) / 2);
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

        g2.setRenderingHint(
            RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON
        );
        g2.setRenderingHint(
            RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON
        );
        g2.setRenderingHint(
            RenderingHints.KEY_FRACTIONALMETRICS,
            RenderingHints.VALUE_FRACTIONALMETRICS_ON
        );
        g2.setRenderingHint(
            RenderingHints.KEY_STROKE_CONTROL,
            RenderingHints.VALUE_STROKE_NORMALIZE
        );

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
     * Returns the maximum size for this component.
     * <p>
     * Returns the preferred size to prevent BoxLayout from expanding
     * components beyond their natural size.
     */
    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }

}
