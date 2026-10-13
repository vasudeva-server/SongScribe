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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import songscribe.dom.StaffElementRun.ElementRange;

/**
 * Removes every bar line that stands first in a freshly parsed song.
 *
 * <p>No bar line stands first in the song — the rule {@link SongStart} states, which every edit
 * that writes an element keeps. A file can still hold one: a song saved after a deletion left a
 * bar line first, which the model accepts and the lint rule flags, or a file written with a left
 * repeat at the start of its first measure. The pass removes each such bar line and reports the
 * line it was on. A key change behind the bar line goes with it, and the key it set becomes the
 * line's own key first, so the song sounds as the file wrote it. Nothing else is moved or
 * repaired into a place the file did not say.
 */
final class LeadingBarLoadPass {

    private static final Logger LOG = LoggerFactory.getLogger(LeadingBarLoadPass.class);

    private LeadingBarLoadPass() {
    }

    /**
     * Removes each bar line that stands first in {@code song}, until its first element is not a
     * bar line or is its terminal, and records what it removed.
     *
     * @param song   the song to settle, immediately after parsing and before it is installed
     * @param report the report the pass records into
     * @invariant afterwards {@link SongStart#leadingBar} answers {@code null} for {@code song}
     * @invariant the song's terminal is never removed
     * @invariant every element left on the song's lines sounds in the key it sounded in before
     *     the pass
     * @effects each removal takes the bar line's effective range
     *     ({@link StaffElementRun#effectiveRange}) through {@link Line#removeRange}, so a key
     *     change that sits behind the bar line goes with it, as a deletion of the bar line would
     *     take it. When it does, the pass first sets that key change's key as the line's own key
     *     ({@link Line#setKey}), so where several bar lines stand first the line ends in the key
     *     of the last key change removed. The line leaves off in the key it left off in before,
     *     so no other line's key moves and no key change is stranded. The removal runs the span
     *     sweep: a span with an endpoint on a removed element, such as an ending that starts on
     *     the bar line, is removed with it, and that removal is not recorded to {@code report},
     *     because the load runs with mutation tracking suspended and the sweep's report is silent
     *     then. Records to {@code report} one {@link SongStart#removedEntry} per bar line removed,
     *     naming the line it was on and whether the removal moved a key, in the order of removal
     * @log one {@code INFO} line stating how many bar lines were removed, when any were
     */
    static void run(Song song, ChangeReport.Builder report) {
        var removedCount = 0;
        var leadingBar = SongStart.leadingBar(song);

        while (leadingBar != null) {
            var line = song.getLine(leadingBar.lineIndex());
            var removed = line.effectiveRange(ElementRange.single(leadingBar.elementIndex()));
            var movedKey = SongStart.keyBehind(line, removed);

            // Set before the removal, so the elements behind the pair never run in any key but
            // the one they ran in under the key change.
            if (movedKey != null) {
                line.setKey(movedKey);
            }

            line.removeRange(removed.range());
            report.record(SongStart.removedEntry(line, movedKey));
            removedCount++;
            leadingBar = SongStart.leadingBar(song);
        }

        if (removedCount > 0) {
            LOG.info("Leading bar load pass: {} removed", removedCount);
        }
    }
}
