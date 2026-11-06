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

import songscribe.dom.DocumentScale;
import songscribe.dom.Song;
import songscribe.error.RuntimeError;
import songscribe.ui.ViewScale;
import songscribe.ui.component.ScoreView;

/**
 * Container panel for under-lyrics text sections.
 */
public class TextPanel extends JPanel {

    private final UnderLyricsComponent underLyricsComponent;

    private final BanglaLyricsComponent banglaLyricsComponent;

    private final TranslationComponent translationComponent;

    private final ScoreView scoreView;

    public TextPanel(ScoreView scoreView) {
        this.scoreView = scoreView;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(false);

        underLyricsComponent = new UnderLyricsComponent(scoreView);
        underLyricsComponent.setAlignmentX(LEFT_ALIGNMENT);

        banglaLyricsComponent = new BanglaLyricsComponent(scoreView);
        banglaLyricsComponent.setAlignmentX(LEFT_ALIGNMENT);

        translationComponent = new TranslationComponent(scoreView);
        translationComponent.setAlignmentX(LEFT_ALIGNMENT);

        add(underLyricsComponent);
        add(banglaLyricsComponent);
        add(translationComponent);
    }

    /**
     * Call after the score view installs a new song.
     *
     * @effects every child measures the new song at the next layout
     */
    void songDidChange() {
        underLyricsComponent.invalidate();
        banglaLyricsComponent.invalidate();
        translationComponent.invalidate();
    }

    public UnderLyricsComponent getUnderLyricsComponent() {
        return underLyricsComponent;
    }

    public BanglaLyricsComponent getBanglaLyricsComponent() {
        return banglaLyricsComponent;
    }

    public TranslationComponent getTranslationComponent() {
        return translationComponent;
    }

    @Override
    protected void paintComponent(Graphics g) {
        var g2 = (Graphics2D) g;
        var unionWidth = calculateUnionWidth(g2);

        if (unionWidth > 0) {
            // The union width is measured with zoom-scaled fonts, so center against
            // the view-scaled (zoomed) line width.
            // A centering bound, not a size: rounding it to a whole pixel would throw away
            // half a pixel of centering, and setContentX takes a float anyway.
            var lineWidthPx = scoreView.getViewScale().toViewPx(scoreView.getSong().getLineWidthSs()).value();
            var contentX = (float) ((lineWidthPx - unionWidth) / 2);
            underLyricsComponent.setContentX(contentX);
            banglaLyricsComponent.setContentX(contentX);
            translationComponent.setContentX(contentX);
        } else {
            underLyricsComponent.setContentX(null);
            banglaLyricsComponent.setContentX(null);
            translationComponent.setContentX(null);
        }

        super.paintComponent(g);
    }

    private double calculateUnionWidth(Graphics2D g2) {
        double maxWidth = 0;

        maxWidth = Math.max(maxWidth, underLyricsComponent.getTextWidth(g2));
        maxWidth = Math.max(maxWidth, banglaLyricsComponent.getTextWidth(g2));
        maxWidth = Math.max(maxWidth, translationComponent.getTextWidth(g2));

        return maxWidth;
    }

    @Override
    public Dimension getPreferredSize() {
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
