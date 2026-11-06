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
import javax.swing.JComponent;

import songscribe.dom.Ss;
import songscribe.ui.ViewScale;
import songscribe.util.GraphicUtils;

/**
 * Shows unsaved title or subtitle text as the page draws it, at its natural size: no zoom
 * applies. Sizes as {@link TitleText} does.
 */
public final class TitlePreview extends JComponent {

    /**
     * Lets a settings dialog show unsaved text without holding the document: both values travel
     * together, since supplying only one would still need a song for the other.
     */
    public record Content(String text, Ss wrapWidthSs) {}

    private final TitleText titleText;

    private Content content = new Content("", new Ss(0));

    public TitlePreview(TitleKind kind) {
        titleText = new TitleText(kind);
    }

    public void setContent(Content content) {
        this.content = content;
        revalidate();
        repaint();
    }

    private TitleText.Block block() {
        return new TitleText.Block(content.text(), content.wrapWidthSs(), getFont(), ViewScale.IDENTITY);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        var g2 = (Graphics2D) g;
        GraphicUtils.setRenderingHints(g2);
        titleText.draw(g2, block(), getWidth());
    }

    @Override
    public Dimension getPreferredSize() {
        return titleText.preferredSize(block());
    }
}
