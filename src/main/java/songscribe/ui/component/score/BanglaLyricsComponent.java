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
 * <p>
 * Displays multi-line Bangla text centered horizontally below the main lyrics.
 * Uses the song's Bangla font with appropriate spacing.
 */
public class BanglaLyricsComponent extends LyricsComponent {

    /** Vertical spacing for Bangla lyrics (2 staff lines). */
    private static final double BANGLA_LYRICS_TOP_MARGIN_SS = 2.0;

    /**
     * The top margin in view pixels, recomputed per layout so it tracks the current zoom.
     */
    @Override
    public int getMarginTop() {
        return toViewPx(new Ss(BANGLA_LYRICS_TOP_MARGIN_SS)).roundedPx();
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
