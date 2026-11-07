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
 * <p>A breath mark stands only immediately after a pitched note or a rest, the invariant
 * {@link StaffElementRun#canInsertRunAt} guards at every edit. The pass runs once the song is
 * complete, and moves or repairs nothing into a place the file did not say.
 */
final class BreathMarkLoadPass {

    private static final Logger LOG = LoggerFactory.getLogger(BreathMarkLoadPass.class);

    private BreathMarkLoadPass() {
    }

    /**
     * Removes every breath mark in {@code song} that does not follow a duration element, and
     * records what it removed.
     *
     * <p>Of a run of consecutive breath marks, at most the first stays: the second follows a
     * breath mark, never a duration element.
     *
     * @param song the song to settle, immediately after parsing and before it is installed
     * @invariant afterwards every breath mark in {@code song} stands immediately after a
     *     pitched note or a rest
     * @effects a tuplet that holds a removed mark is removed with it
     * @effects records to the load's report one entry per line it removed breath marks from,
     *     naming that line, in line order, each before the entries of the spans the line's
     *     removals take
     */
    static void run(Song song) {
        var removedCount = removeUnhostedBreathMarks(song);

        if (removedCount > 0) {
            LOG.info("Breath mark load pass: {} removed", removedCount);
        }
    }

    private static int removeUnhostedBreathMarks(Song song) {
        var removedCount = 0;

        for (var lineIndex = 0; lineIndex < song.lineCount(); lineIndex++) {
            var line = song.getLine(lineIndex);
            var lineReported = false;
            var i = 0;

            while (i < line.elementCount()) {
                if (line.getElement(i).getType().isBreathMark() && !line.isDurationAt(i - 1)) {
                    if (!lineReported) {
                        song.report(new RecordedChange(
                            ChangeHeading.BREATH_MARKS_REMOVED,
                            Strings.get(Strings.REPORT_REASON_BREATH_MARK_HOST),
                            ChangeLines.on(line)));
                        lineReported = true;
                    }

                    line.removeElement(i);
                    removedCount++;
                } else {
                    i++;
                }
            }
        }

        return removedCount;
    }
}
