/*
 * SongScribe song notation program
 * Copyright (C) Sri Chinmoy Centres International
 *
 * This file is part of SongScribe.
 *
 * SongScribe is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * SongScribe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package songscribe.lint;

import java.util.List;

import songscribe.Strings;
import songscribe.dom.Song;
import songscribe.dom.SongStart;

/**
 * The check of {@link LintRule#LEADING_BAR}: no bar line stands first in the song, as
 * {@link SongStart#leadingBar} defines it.
 */
final class LeadingBarCheck implements LintCheck {

    /**
     * Finds the bar line that stands first in {@code song}, if one does.
     *
     * @param song the song to examine; read only
     * @return no violation when {@link SongStart#leadingBar} answers {@code null}; otherwise one
     *     violation with the heading {@link Strings#LINT_BAR_LEADING}, the description
     *     {@link Strings#REPORT_REASON_LEADING_BAR}, and a {@link LintMark.Flagged} mark on that
     *     bar line
     */
    @Override
    public List<Violation> check(Song song) {
        var location = SongStart.leadingBar(song);

        if (location == null) {
            return List.of();
        }

        var bar = song.getLine(location.lineIndex()).getElement(location.elementIndex());

        return List.of(new Violation(Strings.get(Strings.LINT_BAR_LEADING),
                                     Strings.get(Strings.REPORT_REASON_LEADING_BAR),
                                     new LintMark.Flagged(bar)));
    }
}
