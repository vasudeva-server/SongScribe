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

import songscribe.Strings;

/**
 * Drops every breath mark in a freshly parsed song that does not follow a duration element.
 *
 * <p>A breath mark stands only immediately after a pitched note or a rest — the invariant
 * {@link StaffElementRun#canInsertRunAt} guards at every edit. A file can still hold a breath
 * mark elsewhere: the legacy {@code .mssw} reader takes a flat element list as it stands, and
 * that list may put a breath mark after a barline or at the start of a line. The MusicXML
 * writer folds each breath mark into the {@code <note>} before it and the reader
 * appends it after that note again, so a {@code .musicxml} file cannot hold the state and the
 * pass finds nothing there.
 *
 * <p>The pass runs once the song is complete and removes each such breath mark, reporting the
 * line it was on so the user can be told. Nothing is moved or repaired into a place the file did
 * not say.
 */
final class BreathMarkLoadPass {

    private static final Logger LOG = LoggerFactory.getLogger(BreathMarkLoadPass.class);

    private BreathMarkLoadPass() {
    }

    /**
     * Removes every breath mark in {@code song} that does not follow a duration element, and
     * records what it removed.
     *
     * <p>Each breath mark is judged against the element in front of it once the pass has
     * settled everything before it, so a run of consecutive breath marks keeps at most its
     * first: the second follows a breath mark, never a duration element.
     *
     * @param song   the song to settle, immediately after parsing and before it is installed
     * @param report the report the pass records into
     * @invariant afterwards every breath mark in {@code song} stands immediately after a
     *     pitched note or a rest, the invariant {@link StaffElementRun#canInsertRunAt} states
     * @effects each removal goes through {@link Line#removeElement}, which runs the span sweep:
     *     a tuplet that holds the removed mark is removed with it, and that removal is not
     *     recorded to {@code report}, because the load runs with mutation tracking suspended
     *     and the sweep's report is silent then; records to {@code report}, in document order,
     *     each breath mark removed, with an entry that names the line the breath mark was on
     * @log one {@code INFO} line stating how many breath marks were removed, when any were
     */
    static void run(Song song, ChangeReport.Builder report) {
        var removedCount = removeUnhostedBreathMarks(song, report);

        if (removedCount > 0) {
            LOG.info("Breath mark load pass: {} removed", removedCount);
        }
    }

    /**
     * One forward walk over every line. Removing the element at {@code i} closes the line up
     * onto that index, so the walk stays at {@code i} to judge whatever moved into the slot
     * against the predecessor that is now settled. Each removal is recorded with an entry that
     * names the line the breath mark was on.
     */
    private static int removeUnhostedBreathMarks(Song song, ChangeReport.Builder report) {
        var removedCount = 0;

        for (var lineIndex = 0; lineIndex < song.lineCount(); lineIndex++) {
            var line = song.getLine(lineIndex);
            var i = 0;

            while (i < line.elementCount()) {
                if (line.getElement(i).getType().isBreathMark() && !line.isDurationAt(i - 1)) {
                    line.removeElement(i);
                    removedCount++;
                    report.record(new RecordedChange(
                        Strings.REPORT_BREATH_MARKS_REMOVED,
                        Strings.get(Strings.REPORT_REASON_BREATH_MARK_HOST),
                        ChangeLines.on(line)));
                } else {
                    i++;
                }
            }
        }

        return removedCount;
    }
}
