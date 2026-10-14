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
package songscribe.dom;

import org.jspecify.annotations.Nullable;

import songscribe.Strings;

/**
 * The rule that no bar line stands first in the song, and the one place it is stated.
 *
 * <p>The song's terminal is exempt. A song with no other element holds only its terminal, and
 * that song is valid.
 *
 * <p>A removed bar line's key change moves onto the line: a bar line removed from the song start
 * takes the mid-line key change behind it with it, and that key becomes the own key of the line
 * ({@link Line#setKey}), so every element after the pair sounds in the key it did before.
 * {@link #keyBehind} answers which key moves.
 *
 * <p>An edit cannot put a bar line first, and a load or a paste removes one; a deletion can still
 * leave one, which the model accepts and the lint rule that reads {@link #leadingBar} flags.
 */
public final class SongStart {

    private SongStart() {
    }

    static boolean isSlot(Line line, int index) {
        if (index != 0) {
            return false;
        }

        var song = line.getSong();
        var lineIndex = song.indexOfLine(line);

        if (lineIndex == null) {
            return false;
        }

        var firstNonEmptyLineIndex = song.firstNonEmptyLineIndex();

        return firstNonEmptyLineIndex == null || firstNonEmptyLineIndex >= lineIndex;
    }

    /**
     * @return {@code null} when the song holds no element, when its first element is not a bar
     *     line, or when its first element is its terminal
     */
    public static @Nullable ElementLocation leadingBar(Song song) {
        var lineIndex = song.firstNonEmptyLineIndex();

        if (lineIndex == null) {
            return null;
        }

        var line = song.getLine(lineIndex);

        if (!line.getElement(0).getType().isBar() || line.isTerminalAt(0)) {
            return null;
        }

        return new ElementLocation(lineIndex, 0);
    }

    /**
     * @param removed as {@link StaffElementRun#effectiveRange} widened them
     * @return {@code null} when {@code removed} holds no key change, and the removal moves no key
     */
    public static @Nullable Key keyBehind(StaffElementRun run, StaffElementRun.EffectiveRange removed) {
        var range = removed.range();

        for (var index = range.end(); index >= range.begin(); index--) {
            if (run.getElement(index) instanceof KeyChangeElement keyChange) {
                return keyChange.getKey();
            }
        }

        return null;
    }

    public static RecordedChange removedEntry(Line line, @Nullable Key movedKey) {
        var reasonKey = movedKey == null
            ? Strings.REPORT_REASON_LEADING_BAR
            : Strings.REPORT_REASON_LEADING_BAR_KEY;

        return new RecordedChange(
            Strings.REPORT_LEADING_BARS_REMOVED,
            Strings.get(reasonKey),
            ChangeLines.on(line));
    }
}
