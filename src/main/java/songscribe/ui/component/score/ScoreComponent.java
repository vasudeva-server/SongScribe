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

import java.util.Objects;

import org.jspecify.annotations.Nullable;

import songscribe.music.Composition;
import songscribe.ui.menu.DebugState;

/**
 * Abstract base class for score rendering components.
 * <p>
 * Provides common functionality for components that render parts of a music score:
 * <ul>
 *   <li>Antialiasing setup in {@link #paintComponent(Graphics)}</li>
 *   <li>Template method pattern via {@link #render(Graphics2D)}</li>
 *   <li>Debug visualization hooks</li>
 *   <li>Margin system integration via {@link songscribe.ui.layout.LayoutStylesheet}</li>
 * </ul>
 * <p>
 * Subclasses must implement {@link #render(Graphics2D)} to perform their specific rendering.
 */
public abstract class ScoreComponent extends JComponent {

    /** Reference to the composition model. */
    @Nullable
    protected Composition composition;

    /** Margin values (top, right, bottom, left) in pixels. */
    protected int marginTop = 0;
    protected int marginRight = 0;
    protected int marginBottom = 0;
    protected int marginLeft = 0;

    /**
     * Creates a new ScoreComponent.
     */
    protected ScoreComponent() {
        setOpaque(false);
    }

    /**
     * Sets the composition model for this component.
     *
     * @param composition The composition to render
     */
    public void setComposition(Composition composition) {
        this.composition = composition;
        revalidate();
        repaint();
    }

    /**
     * Returns the composition model.
     */
    public Composition getComposition() {
        return Objects.requireNonNull(composition, "composition not initialized");
    }

    /**
     * Sets uniform margin on all sides.
     *
     * @param margin Margin in pixels
     */
    public void setMargin(int margin) {
        this.marginTop = margin;
        this.marginRight = margin;
        this.marginBottom = margin;
        this.marginLeft = margin;
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
        this.marginTop = top;
        this.marginRight = right;
        this.marginBottom = bottom;
        this.marginLeft = left;
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

        if (DebugState.isDebugEnabled()) {
            renderDebug(g2);
        }
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

    /**
     * Renders debug visualizations.
     * <p>
     * Called when debug mode is enabled. Default implementation draws
     * component bounds. Subclasses can override to add additional debug info.
     *
     * @param g2 The Graphics2D context
     */
    protected void renderDebug(Graphics2D g2) {
        // Draw component bounds
        g2.setColor(new Color(255, 0, 0, 64));
        g2.drawRect(0, 0, getWidth() - 1, getHeight() - 1);

        // Draw margin bounds
        g2.setColor(new Color(0, 255, 0, 64));
        g2.drawRect(
            marginLeft,
            marginTop,
            getWidth() - marginLeft - marginRight - 1,
            getHeight() - marginTop - marginBottom - 1
        );
    }
}
