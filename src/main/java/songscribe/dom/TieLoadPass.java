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

import org.jspecify.annotations.Nullable;

import songscribe.Strings;

/**
 * Turns the ties a reader found into the song's ties, or drops them.
 *
 * <p>A loaded tie stays by the same judgment as a tie an edit leaves ({@link Tie#judge}), and
 * nothing is repaired into something the file did not say.
 */
final class TieLoadPass {

    private TieLoadPass() {
    }

    /**
     * @param song the song being loaded, whose lines hold every anchor
     * @param ties the ties the reader found in {@code song}
     * @effects adds a tie from each pending tie's anchor that {@link Tie#canAnchor} accepts and
     *     that {@link Tie#judge} finds to hold; no other pending tie is on the song afterwards
     * @effects records to the load's report, in the order of {@code ties}, one entry for each
     *     pending tie dropped: under {@link ChangeHeading#TIES_REMOVED} with
     *     {@link Strings#REPORT_REASON_DAMAGED_WRONG_ELEMENT} on the anchor's line when
     *     {@link Tie#canAnchor} refuses its anchor, otherwise the
     *     {@link Tie.Judgment.Broken#entry() entry} {@link Tie#judge} gives
     */
    static void run(Song song, List<PendingTie> ties) {
        for (var pending : ties) {
            if (tieableAnchor(pending) instanceof NoteElement note) {
                note.requireParentLine().addTie(note);
            }
        }

        for (var pending : ties) {
            var line = pending.anchor().requireParentLine();

            if (!(tieableAnchor(pending) instanceof NoteElement note)) {
                song.report(new RecordedChange(
                    ChangeHeading.TIES_REMOVED,
                    Strings.get(Strings.REPORT_REASON_DAMAGED_WRONG_ELEMENT),
                    ChangeLines.on(line)));
            } else if (Tie.judge(line, line.getElementIndex(note)) instanceof Tie.Judgment.Broken broken) {
                song.report(broken.entry());
                line.removeTie(note);
            }
        }
    }

    /**
     * @return {@code null} when the anchor cannot carry a tie
     */
    private static @Nullable NoteElement tieableAnchor(PendingTie pending) {
        return pending.anchor() instanceof NoteElement note && Tie.canAnchor(note) ? note : null;
    }
}
