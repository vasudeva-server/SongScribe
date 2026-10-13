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
 * <p>A <em>bar line</em> here is any element whose {@link ElementType#isBar()} is true: the
 * plain, double and final bar lines, and the three repeats. The <em>first element of the
 * song</em> is the first element of the first line that holds one. So the <b>song-start
 * slot</b> is index 0 of a line every line before which is empty: an element inserted there
 * becomes the first element of the song.
 *
 * <p>The song's terminal is exempt. A song with no other element holds only its terminal, and
 * that song is valid.
 *
 * <p><b>A removed bar line's key change moves onto the line.</b> A bar line and the mid-line key
 * change behind it are one pair, so a bar line removed from the song-start slot takes its key
 * change with it. The key that key change set is not lost: it becomes the own key of the line the
 * pair stood on ({@link Line#setKey}), so every element after the pair sounds in the key it
 * sounded in before. Where several pairs stand first, the line takes the key of the last one that
 * carries a key change, which is the key in effect after all of them. A pair with no key change
 * leaves the line's key alone. {@link #keyBehind} answers which key moves.
 *
 * <p>Three points keep the rule, and each asks this class:
 *
 * <ul>
 *   <li>An edit cannot put a bar line in the song-start slot. {@link StaffElementRun#canInsertRunAt}
 *       and {@link StaffElementRun#canReplaceElementAt} refuse it, through
 *       {@link StaffElementRun#isSongStartAt}; a paste drops the bar lines that start its
 *       fragment instead, moves the key of the last key change it drops onto the destination
 *       line, and reports the drop with {@link #removedEntry}.</li>
 *   <li>A load removes each bar line that stands first in the song, moving the key of a key
 *       change behind it onto the line, and reports each removal with {@link #removedEntry}.</li>
 *   <li>A deletion can still leave a bar line first — a deletion of the notes in front of it,
 *       for instance. The model accepts that state, and the lint rule that reads
 *       {@link #leadingBar} flags it for the user to fix.</li>
 * </ul>
 */
public final class SongStart {

    private SongStart() {
    }

    /**
     * Whether {@code index} of {@code line} is the song-start slot: an element inserted there
     * becomes the first element of the song.
     *
     * @param line  the line asked about
     * @param index an insertion index of {@code line}
     * @return {@code true} when {@code index} is 0, {@code line} is one of its song's lines, and
     *     every line before it holds no element; {@code false} for a line that is not one of its
     *     song's lines, since it has no place in the song
     */
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
     * The bar line that stands first in {@code song}, when one does.
     *
     * @param song the song to read; read only, never changed
     * @return the location of the song's first element when it is a bar line and not the
     *     song's terminal; {@code null} when the song holds no element, when its first element is
     *     not a bar line, or when its first element is its terminal
     * @invariant a non-null answer has element index 0, on the first line that holds an element
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
     * The key a removal of {@code removed} from the song-start slot moves onto the line: the key
     * of the last key change {@code removed} holds.
     *
     * @param run     the run {@code removed} was widened on: a song's line, or a fragment that
     *                has not landed on one
     * @param removed the pairs about to be removed from the start of {@code run}, as
     *                {@link StaffElementRun#effectiveRange} widened them
     * @return the key of the last key change in {@code removed}; {@code null} when it holds none,
     *     and the removal moves no key
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

    /**
     * The change-report entry for a removal from the song-start slot of {@code line}: one bar
     * line by a load, or every bar line that started a fragment by a paste. The load pass and a
     * paste both record it, so a removal reads the same whichever made it. Each removal records
     * exactly one entry.
     *
     * @param line     the line the bar lines were removed from
     * @param movedKey the key the removal moved onto {@code line} ({@link #keyBehind}), or
     *                 {@code null} when it moved none
     * @return an entry with the heading {@link Strings#REPORT_LEADING_BARS_REMOVED} and the place
     *     {@link ChangeLines#on ChangeLines.on(line)}; its description is
     *     {@link Strings#REPORT_REASON_LEADING_BAR} when {@code movedKey} is {@code null}, and
     *     {@link Strings#REPORT_REASON_LEADING_BAR_KEY} when it is not
     */
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
