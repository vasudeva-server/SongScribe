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

package songscribe.ui.component;

import org.jspecify.annotations.Nullable;

import songscribe.dom.Line;
import songscribe.ui.action.EditLyricAction;

/**
 * Decides which staff element a lyric-editing gesture should open the editor on.
 *
 * <p>Every entry point to lyric editing resolves its target through
 * {@link #resolveLyricTarget}, so all agree on what is editable.
 */
public final class LyricTargetResolver {

    private LyricTargetResolver() {
    }

    /**
     * A grace-host pair's lyric lives on the grace note, so a gesture on the host resolves
     * to the grace note.
     *
     * @return {@code null} when the element the gesture resolves to can bear no syllable
     */
    public static @Nullable Integer resolveLyricTarget(Line line, int index) {
        var targetIndex = line.isPairedGraceNoteTarget(index) ? index - 1 : index;
        return line.canBearSyllableAt(targetIndex) ? targetIndex : null;
    }

    /** Null when the line has no eligible element left. */
    public static @Nullable Integer findNextEligibleIndex(Line line, int currentIndex) {
        var count = line.effectiveElementCount();

        for (var i = currentIndex + 1; i < count; i++) {
            if (line.canBearSyllableAt(i)) {
                return i;
            }
        }

        return null;
    }

    /** Null when there is no eligible element before {@code currentIndex}. */
    public static @Nullable Integer findPreviousEligibleIndex(Line line, int currentIndex) {
        for (var i = currentIndex - 1; i >= 0; i--) {
            if (line.canBearSyllableAt(i)) {
                return i;
            }
        }

        return null;
    }
}
