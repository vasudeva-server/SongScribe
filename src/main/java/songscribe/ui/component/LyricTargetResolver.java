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

import songscribe.dom.Line;
import songscribe.ui.action.EditLyricAction;

/**
 * Decides which staff element a lyric-editing gesture should open the editor on.
 * <p>
 * This is pure policy over a {@link Line} and an element index: it holds no state and
 * needs no editor instance. Every entry point to lyric editing — {@link EditLyricAction},
 * Return/Enter on a selection, and a double-click on an element — resolves its target
 * through {@link #resolveLyricTarget}, so all of them agree on what is editable and on
 * which element an ambiguous gesture lands.
 */
public final class LyricTargetResolver {

    private LyricTargetResolver() {
    }

    /**
     * Returns the index the editor should open on for a gesture aimed at {@code index},
     * or -1 when there is no such target. A grace-host pair's lyric lives on the grace
     * note, so a gesture on the host resolves to the grace note.
     */
    public static int resolveLyricTarget(Line line, int index) {
        var targetIndex = line.isHostOfPairedGraceNote(index) ? index - 1 : index;
        return isLyricTargetEligible(line, targetIndex) ? targetIndex : -1;
    }

    /**
     * Returns true when {@code index} is a structurally valid lyric target: a pitched note,
     * rest, or grace note that is NOT the host of a paired grace note. This is the single
     * source of truth for the host-block rule used by action enablement, gesture target
     * resolution, and editor navigation.
     */
    public static boolean isLyricTargetEligible(Line line, int index) {
        if (line.isHostOfPairedGraceNote(index)) {
            return false;
        }

        var type = line.getElement(index).getType();
        return type.isPitchedNote() || type.isRest() || type.isGraceNote();
    }

    /**
     * Returns the index Tab (and every other forward move) should carry the lyric editor to
     * from {@code currentIndex}, or -1 when the line has no eligible element left. Eligibility
     * is {@link #isLyricTargetEligible} plus the element accepting a lyric in {@code verse}.
     * The line's auto-maintained terminal barline is excluded via
     * {@link Line#effectiveElementCount()}.
     */
    public static int findNextEligibleIndex(Line line, int currentIndex, int verse) {
        var count = line.effectiveElementCount();

        for (var i = currentIndex + 1; i < count; i++) {
            if (isLyricTargetEligible(line, i) && line.getElement(i).isEligibleForLyric(verse)) {
                return i;
            }
        }

        return -1;
    }

    /**
     * Returns the index Shift-Tab should carry the lyric editor back to from
     * {@code currentIndex}, or -1 when there is no eligible element before it. Eligibility is
     * the same test {@link #findNextEligibleIndex} applies.
     */
    public static int findPreviousEligibleIndex(Line line, int currentIndex, int verse) {
        for (var i = currentIndex - 1; i >= 0; i--) {
            if (isLyricTargetEligible(line, i) && line.getElement(i).isEligibleForLyric(verse)) {
                return i;
            }
        }

        return -1;
    }
}
