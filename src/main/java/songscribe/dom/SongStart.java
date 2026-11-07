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
 * The rule that no barline stands first in the song.
 *
 * <p>The song's terminal is exempt. A song with no other element holds only its terminal, and
 * that song is valid.
 *
 * <p>A removed barline's key change moves onto the line: a barline removed from the song start
 * takes the mid-line key change behind it with it, and that key becomes the own key of the line,
 * so every element after the pair sounds in the key it did before.
 *
 * <p>{@link StaffElementRun#leadingBars} answers which barlines the rule removes from a run, and
 * which key moves.
 *
 * <p>The rule holds when a load settles and when an edit's modification bracket closes
 * ({@link SongStartRule}); it does not hold between the mutations of an edit, where a deletion can
 * leave a barline first.
 */
public final class SongStart {

    private SongStart() {
    }

    static boolean isSongStartSlot(Line line, int index) {
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

        if (song.getLine(lineIndex).leadingBars() == null) {
            return null;
        }

        return new ElementLocation(lineIndex, 0);
    }

    /**
     * Removes each barline that stands first in {@code song}, until its first element is not a
     * barline or is its terminal, and records what it removed.
     *
     * @param song the song to settle, with an open bracket or a load session to report into
     * @return how many barlines it removed
     * @invariant afterwards {@link #leadingBar} answers {@code null} for {@code song}
     * @invariant the song's terminal is never removed
     * @invariant every element left on the song's lines sounds in the key it sounded in before
     *     the call
     * @effects a key change that sits behind a removed barline is removed with it, and its key
     *     becomes the line's own key; where several barlines stand first, the line's own key is
     *     that of the last key change removed. No other line's key moves and no key change is
     *     stranded
     * @effects a span with an endpoint on a removed element, such as an ending that starts on the
     *     barline, is removed with it
     * @effects records to the report one {@link #removedEntry} per line it removed barlines from,
     *     naming that line and whether the removal moved a key, in line order, each before the
     *     entries of the spans its removal takes
     */
    static int removeLeadingBars(Song song) {
        var removedCount = 0;

        // A line left empty passes the start of the song to the next non-empty line, whose own
        // leading barlines then stand first.
        for (var lineIndex = song.firstNonEmptyLineIndex(); lineIndex != null;
             lineIndex = song.firstNonEmptyLineIndex()) {

            var line = song.getLine(lineIndex);
            var leadingBars = line.leadingBars();

            if (leadingBars == null) {
                break;
            }

            var movedKey = leadingBars.movedKey();

            // Set before the removal, so the elements behind the pair never run in any key but
            // the one they ran in under the key change.
            if (movedKey != null) {
                line.setKey(movedKey);
            }

            song.report(removedEntry(line, movedKey));
            line.deleteRanges(leadingBars.ranges());
            removedCount += leadingBars.ranges().size();
        }

        return removedCount;
    }

    /**
     * The one entry for the barlines removed from the start of {@code line}, however many there
     * are.
     */
    public static RecordedChange removedEntry(Line line, @Nullable Key movedKey) {
        var reasonKey = movedKey == null
            ? Strings.REPORT_REASON_LEADING_BAR
            : Strings.REPORT_REASON_LEADING_BAR_KEY;

        return new RecordedChange(
            ChangeHeading.LEADING_BARS_REMOVED,
            Strings.get(reasonKey),
            ChangeLines.on(line));
    }
}
