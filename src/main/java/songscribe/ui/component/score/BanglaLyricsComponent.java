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

import java.awt.Font;

import songscribe.dom.Ss;

/**
 * Component that renders Bengali (Bangla) lyrics.
 */
public class BanglaLyricsComponent extends LyricsComponent {

    private static final Ss BANGLA_LYRICS_TOP_MARGIN_SS = new Ss(2.0);

    /** Recomputed per layout so it tracks the current zoom. */
    @Override
    public int getMarginTop() {
        return toViewPx(BANGLA_LYRICS_TOP_MARGIN_SS).positionPx();
    }

    @Override
    protected String getLyrics() {
        return getSong().getBanglaLyrics();
    }

    @Override
    protected Font getLyricsFont() {
        return getFont();
    }
}
