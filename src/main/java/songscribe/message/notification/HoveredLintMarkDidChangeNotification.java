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

package songscribe.message.notification;

import org.jspecify.annotations.Nullable;

import songscribe.lint.LintMark;
import songscribe.message.Message;

/**
 * Posted each time the pointer moves onto a lint mark in the score, from one mark to another, or
 * off every mark. Handler name convention: {@code hoveredLintMarkDidChange}.
 */
public class HoveredLintMarkDidChangeNotification extends Message {

    private final @Nullable LintMark mark;

    /**
     * @param mark the mark now under the pointer, or {@code null} when the pointer is on none
     */
    public HoveredLintMarkDidChangeNotification(@Nullable LintMark mark) {
        this.mark = mark;
    }

    /**
     * Returns the mark under the pointer, or null when the pointer is on none.
     */
    public @Nullable LintMark getMark() {
        return mark;
    }
}
