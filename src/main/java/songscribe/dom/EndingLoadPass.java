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

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import songscribe.Strings;
import songscribe.dom.StaffElementRun.ElementRange;

/**
 * Turns the endings a reader found into the song's endings, or drops them.
 *
 * <p>A reader records each ending as a {@link PendingEnding}. This pass runs once the song is
 * complete and is the one place a read ending becomes an {@link Ending}: it asks
 * {@link Ending#spanning}, the same rule the editor asks, so an ending read from a file is well
 * formed by exactly the rule an ending made in the editor is. A range that rule refuses is
 * dropped — the bracket goes, the elements stay — and nothing is repaired into something the
 * file did not say.
 */
public final class EndingLoadPass {

    private static final Logger LOG = LoggerFactory.getLogger(EndingLoadPass.class);

    private EndingLoadPass() {
    }

    /**
     * Adds to {@code song} every pending ending that is well formed, and records every one it
     * dropped.
     *
     * <p>Each pending ending is judged in order by {@link Ending#spanning} over the range from
     * its anchor's index to its end's index in the anchor's line. An ending {@code spanning}
     * returns takes the pending ending's offset as its {@link LineElement#getUserYOffsetSs()}
     * and is added to that line. One that {@code spanning} refuses is dropped. Adding an ending
     * changes no element, so every later pending ending's indices are the ones the reader built.
     * Because the endings are judged in order and {@code spanning} sees the endings already
     * added, the pass drops the later of two pending endings that overlap or sit back to back.
     *
     * <p>The whole pass runs with mutation tracking suspended, so it emits no mutations:
     * outside suspension the additions would land in the undo history and a freshly opened
     * song could be undone back to a song without its endings. The song's modified flag is
     * left alone — {@code ScoreView.setSong} clears it when the song is installed, so the
     * caller re-marks it afterwards from the report.
     *
     * @param song           the song to add the endings to, immediately after parsing and before
     *                       it is installed
     * @param pendingEndings the endings the reader found in {@code song}. Each one's anchor sits
     *                       in a line of {@code song}, and its end sits in the same line at or
     *                       after the anchor
     * @param report         the report the pass records into
     * @throws IllegalArgumentException if a pending ending's anchor sits in no line of
     *     {@code song}, or its end does not sit in the anchor's line at or after the anchor
     * @effects adds an {@link Ending} to its anchor's line for every pending ending not dropped;
     *     records to {@code report}, in the order of {@code pendingEndings}, every pending
     *     ending the pass dropped, with the reason {@link Ending#spanning} gave
     * @log WARN one line for each pending ending dropped, naming its line and range
     */
    public static void run(Song song, List<PendingEnding> pendingEndings, ChangeReport report) {
        song.withoutMutationTracking(() -> {
            for (var pending : pendingEndings) {
                place(song, pending, report);
            }
        });
    }

    /** Adds one pending ending to its anchor's line, or drops it and records the drop. */
    private static void place(Song song, PendingEnding pending, ChangeReport report) {
        var anchor = pending.anchor();
        var line = anchor.getParentLine();
        var lineIndex = line == null ? null : song.indexOfLine(line);

        if (line == null || lineIndex == null) {
            throw new IllegalArgumentException("pending ending's anchor is in no line of the song");
        }

        var span = new ElementRange(
            line.getElementIndex(anchor), line.getElementIndex(pending.end()));

        switch (Ending.spanning(line, span)) {
            case Ending.Spanning.Spanned(var ending) -> {
                ending.setUserYOffsetSs(pending.yOffsetSs());
                ending.addTo(line);
            }

            case Ending.Spanning.Refused(var reasonKey) -> {
                LOG.warn("Ending load pass: dropped an ill-formed ending over elements {}-{} of line {}",
                    span.begin(), span.end(), lineIndex);

                report.record(new ReportedChange(
                    Strings.DIALOG_REPORT_ENDINGS_REMOVED,
                    Strings.get(reasonKey),
                    TupletLoadPass.anchorToEnd(lineIndex, span)));
            }
        }
    }
}
