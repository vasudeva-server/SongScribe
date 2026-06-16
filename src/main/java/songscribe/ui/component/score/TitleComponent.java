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

/**
 * Component that renders the song title.
 * <p>
 * The title is centered horizontally and may wrap to multiple lines
 * if it exceeds the line width. Uses the song's title font.
 */
public class TitleComponent extends BaseTitleComponent {

    @Override
    protected String songText() {
        return getSong().getNumberedTitle();
    }
}
