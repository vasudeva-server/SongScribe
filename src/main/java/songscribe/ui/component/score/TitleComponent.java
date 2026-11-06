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
import java.awt.Graphics2D;

import songscribe.ui.action.Actions;
import songscribe.ui.component.ScoreView;

/**
 * Draws the song's title or subtitle on the score, sized as {@link TitleText} does.
 * <p>
 * An empty title or subtitle therefore has no hit area, and a double-click where it would sit does
 * nothing; that is deliberate, because a placeholder target would put an invisible click-swallowing
 * strip above every score.
 */
public final class TitleComponent extends ScoreComponent {

    private final TitleKind kind;

    private final TitleText titleText;

    public TitleComponent(ScoreView scoreView, TitleKind kind) {
        super(scoreView);
        this.kind = kind;
        titleText = new TitleText(kind);
    }

    /**
     * No hit testing: the component is exactly as wide as its text, so a click that reached it
     * is a click on the title.
     * <p>
     * {@code SONG_SETTINGS_ACTION} is deliberately not null-guarded, so a startup-ordering
     * regression fails loudly instead of becoming a gesture that silently does nothing. The
     * playback condition is applied by {@link ScoreComponent} before this method is reached.
     */
    @Override
    protected boolean openEditor() {
        Actions.SONG_SETTINGS_ACTION.openAt(kind.editorSection());
        return true;
    }

    private TitleText.Block block() {
        var song = getSong();
        return new TitleText.Block(kind.songText(song), song.getLineWidthSs(), getFont(), getViewScale());
    }

    @Override
    protected void render(Graphics2D g2) {
        titleText.draw(g2, block(), getWidth());
    }

    @Override
    public Dimension getPreferredSize() {
        return titleText.preferredSize(block());
    }
}
