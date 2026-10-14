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

package songscribe.message.notification;

import org.jspecify.annotations.Nullable;

import songscribe.lint.LintMark;
import songscribe.message.Message;

/**
 * The notification that the lint mark selected in the Messages panel changed.
 */
public class LintMarkSelectionDidChangeNotification extends Message {

    private final @Nullable LintMark mark;

    /** {@code mark} is {@code null} when no lint row is selected. */
    public LintMarkSelectionDidChangeNotification(@Nullable LintMark mark) {
        this.mark = mark;
    }

    /** @return the selected lint row's mark, or {@code null} when no lint row is selected */
    public @Nullable LintMark getMark() {
        return mark;
    }
}
