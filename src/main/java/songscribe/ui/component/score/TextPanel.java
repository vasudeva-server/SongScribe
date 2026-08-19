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
import java.awt.Graphics;
import java.awt.Graphics2D;
import javax.swing.BoxLayout;
import javax.swing.JPanel;

import org.jspecify.annotations.Nullable;

import songscribe.dom.ScaleContext;
import songscribe.dom.Song;
import songscribe.error.RuntimeError;
import songscribe.ui.ViewScale;
import songscribe.ui.component.ScoreView;

/**
 * Container panel for under-lyrics text sections.
 * <p>
 * Contains:
 * <ul>
 *   <li>{@link UnderLyricsComponent} - main lyrics text</li>
 *   <li>{@link BanglaLyricsComponent} - Bengali lyrics</li>
 *   <li>{@link TranslationComponent} - translation with header</li>
 * </ul>
 * <p>
 * Uses vertical box layout with appropriate spacing between sections.
 */
public class TextPanel extends JPanel {

    /** Under-lyrics component. */
    private final UnderLyricsComponent underLyricsComponent;

    /** Bangla lyrics component. */
    private final BanglaLyricsComponent banglaLyricsComponent;

    /** Translation component. */
    private final TranslationComponent translationComponent;

    /** The song model. */
    @Nullable
    private Song song;

    /** The owning score view, or null when detached (tests). Supplies the view zoom. */
    @Nullable
    private ScoreView scoreView;

    /**
     * Creates a new TextPanel.
     */
    public TextPanel() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(false);

        underLyricsComponent = new UnderLyricsComponent();
        underLyricsComponent.setAlignmentX(LEFT_ALIGNMENT);

        banglaLyricsComponent = new BanglaLyricsComponent();
        banglaLyricsComponent.setAlignmentX(LEFT_ALIGNMENT);

        translationComponent = new TranslationComponent();
        translationComponent.setAlignmentX(LEFT_ALIGNMENT);

        add(underLyricsComponent);
        add(banglaLyricsComponent);
        add(translationComponent);
    }

    /**
     * Sets the song and updates all child components.
     *
     * @param song The song
     */
    public void setSong(Song song) {
        this.song = song;
        underLyricsComponent.setSong(song);
        banglaLyricsComponent.setSong(song);
        translationComponent.setSong(song);
        revalidate();
        repaint();
    }

    /**
     * Sets the owning {@link ScoreView} and fans it out to the once-created leaf
     * components so their text scales with the view zoom.
     */
    public void setScoreView(ScoreView scoreView) {
        this.scoreView = scoreView;
        underLyricsComponent.setScoreView(scoreView);
        banglaLyricsComponent.setScoreView(scoreView);
        translationComponent.setScoreView(scoreView);
    }

    /** The view zoom, or {@link ViewScale#IDENTITY} when detached. */
    private ViewScale viewScale() {
        return scoreView != null ? scoreView.getViewScale() : ViewScale.IDENTITY;
    }

    /**
     * Returns the song.
     */
    public Song getSong() {
        if (song == null) {
            throw RuntimeError.exit("song not initialized");
        }

        return song;
    }

    /**
     * Returns the under-lyrics component.
     */
    public UnderLyricsComponent getUnderLyricsComponent() {
        return underLyricsComponent;
    }

    /**
     * Returns the Bangla lyrics component.
     */
    public BanglaLyricsComponent getBanglaLyricsComponent() {
        return banglaLyricsComponent;
    }

    /**
     * Returns the translation component.
     */
    public TranslationComponent getTranslationComponent() {
        return translationComponent;
    }

    @Override
    protected void paintComponent(Graphics g) {
        // Calculate union width and set contentX on children before they paint
        if (song != null) {
            var g2 = (Graphics2D) g;
            var unionWidth = calculateUnionWidth(g2);

            if (unionWidth > 0) {
                // The union width is measured with zoom-scaled fonts, so center against
                // the view-scaled (zoomed) line width.
                var lineWidthPx =
                    (int) Math.round(ScaleContext.ssToPx(song.getLineWidthSs()) * viewScale().factor());
                var contentX = (float) Math.round(
                    (lineWidthPx - unionWidth) / 2
                );
                underLyricsComponent.setContentX(contentX);
                banglaLyricsComponent.setContentX(contentX);
                translationComponent.setContentX(contentX);
            } else {
                // Reset to independent centering
                underLyricsComponent.setContentX(-1);
                banglaLyricsComponent.setContentX(-1);
                translationComponent.setContentX(-1);
            }
        }

        super.paintComponent(g);
    }

    /**
     * Calculates the union width across all text sections.
     * <p>
     * This is the maximum width of lyrics, Bangla lyrics, and translation,
     * used to center all sections at the same X position.
     *
     * @param g2 Graphics context
     * @return Union width in pixels
     */
    private double calculateUnionWidth(Graphics2D g2) {
        double maxWidth = 0;

        maxWidth = Math.max(maxWidth, underLyricsComponent.getTextWidth(g2));
        maxWidth = Math.max(maxWidth, banglaLyricsComponent.getTextWidth(g2));
        maxWidth = Math.max(maxWidth, translationComponent.getTextWidth(g2));

        return maxWidth;
    }

    @Override
    public Dimension getPreferredSize() {
        if (song == null) {
            return new Dimension(0, 0);
        }

        var underLyricsSize = underLyricsComponent.getPreferredSize();
        var banglaLyricsSize = banglaLyricsComponent.getPreferredSize();
        var translationSize = translationComponent.getPreferredSize();

        var width = Math.max(
            underLyricsSize.width,
            Math.max(banglaLyricsSize.width, translationSize.width)
        );
        var height = underLyricsSize.height + banglaLyricsSize.height + translationSize.height;

        return new Dimension(width, height);
    }

    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }
}
