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
 * Abstract base class for score rendering components.
 * <p>
 * Provides common functionality for components that render parts of a music score:
 * <ul>
 *   <li>Antialiasing setup in {@link #paintComponent(Graphics)}</li>
 *   <li>Template method pattern via {@link #render(Graphics2D)}</li>
 *   <li>Margin system integration via layout constants</li>
 *   <li>Mouse dispatch via the {@link #clicked}, {@link #pressed}, {@link #released},
 *       {@link #entered} and {@link #exited} hooks</li>
 * </ul>
 * <p>
 * Subclasses must implement {@link #render(Graphics2D)} to perform their specific rendering.
 * <p>
 * A component registers its mouse listener once, in its constructor, so AWT always
 * delivers a click straight to the component under the pointer and nothing has to search
 * the containment tree by bounds. A detached component (dialog previews, exporters) still
 * receives events, but each hook reads a null {@code scoreView} and does nothing.
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
        addMouseListener(mouseAdapter);
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
     * Sets the owning {@link ScoreView}, attaching this component to it. On-score
     * components are given one; detached previews are not, so they fall back to
     * {@link ViewScale#IDENTITY}.
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

        var textWidth = TextMeasurement.textBlockWidth(text, g2);
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
     * Called by {@link #clicked} on a left double-click outside playback. The default
     * answers false, so a component that displays nothing editable needs no override and
     * the click falls through to the score view's normal handling.
     * <p>
     * Takes no click point: a component's bounds are its hit area. A component that needs
     * to distinguish where on itself it was clicked overrides {@link #clicked} instead,
     * which receives the event in this component's own coordinate space.
     *
     * @return {@code true} if an editor opened, {@code false} if this component has
     *         nothing to edit
     * @effects opens an editor, which for most components is a modal dialog
     */
    protected boolean openEditor() {
        return false;
    }

    /**
     * Reacts to a left-button click on this component.
     * <p>
     * {@code view} is the owning view rather than a read of the {@code scoreView} field,
     * so that attachment is a precondition carried by the signature: no override can be
     * reached while this component is detached, and none has to test for it.
     * <p>
     * The default opens this component's editor on a left double-click outside playback,
     * and otherwise cancels pending input and clears the selection. A double-click
     * arrives as two clicks, and the first of them takes the cancel-and-deselect path in
     * full, so a double-click always clears the selection before an editor opens.
     * <p>
     * A component overrides this rather than {@link #openEditor()} when it needs the
     * click point, or when it wants a different order of gestures.
     *
     * @param e    the click, in this component's coordinate space
     * @param view the score view this component is attached to
     * @effects by default, opens an editor or clears the selection
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
     * Reacts to a left-button press on this component, after the score view has already
     * been given focus.
     * <p>
     * {@code view} is the owning view rather than a read of the {@code scoreView} field,
     * so that attachment is a precondition carried by the signature. Does nothing by
     * default.
     *
     * @param e    the press, in this component's coordinate space
     * @param view the score view this component is attached to
     */
    protected void pressed(MouseEvent e, ScoreView view) {
        // Nothing by default.
    }

    /**
     * Reacts to a mouse release on this component.
     * <p>
     * {@code view} is the owning view rather than a read of the {@code scoreView} field,
     * so that attachment is a precondition carried by the signature. Unlike
     * {@link #clicked} and {@link #pressed}, this is not filtered by button: a release of
     * any button reaches it. Does nothing by default.
     *
     * @param e    the release, in this component's coordinate space
     * @param view the score view this component is attached to
     */
    protected void released(MouseEvent e, ScoreView view) {
        // Nothing by default.
    }

    /**
     * Reacts to the pointer entering this component.
     * <p>
     * {@code view} is the owning view rather than a read of the {@code scoreView} field,
     * so that attachment is a precondition carried by the signature. Unlike
     * {@link #clicked} and {@link #pressed}, this is not filtered by button: an enter
     * carries no button at all. Does nothing by default.
     *
     * @param e    the enter, in this component's coordinate space
     * @param view the score view this component is attached to
     */
    protected void entered(MouseEvent e, ScoreView view) {
        // Nothing by default.
    }

    /**
     * Reacts to the pointer leaving this component.
     * <p>
     * {@code view} is the owning view rather than a read of the {@code scoreView} field,
     * so that attachment is a precondition carried by the signature. Unlike
     * {@link #clicked} and {@link #pressed}, this is not filtered by button: an exit
     * carries no button at all. Does nothing by default.
     *
     * @param e    the exit, in this component's coordinate space
     * @param view the score view this component is attached to
     */
    protected void exited(MouseEvent e, ScoreView view) {
        // Nothing by default.
    }

}
