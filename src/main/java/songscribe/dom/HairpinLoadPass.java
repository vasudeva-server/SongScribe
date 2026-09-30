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
import java.util.Locale;

import songscribe.Strings;

/**
 * Turns the hairpins a reader found into the song's hairpins, or drops them.
 *
 * <p>A reader records each hairpin as a {@link PendingHairpin}. This pass runs once the song is
 * complete and is the one place a read hairpin becomes a {@link Hairpin}: it asks
 * {@link Hairpin#spanning}, the same rule the editor and the paste ask, so a hairpin read from a
 * file is well formed by exactly the rule a hairpin made in the editor is. A file an older build
 * saved can hold a hairpin that rule refuses — one that ends on a rest, say. That hairpin is
 * dropped — the wedge goes, the elements stay — and nothing is repaired into something the file
 * did not say.
 */
public final class HairpinLoadPass {

    private HairpinLoadPass() {
    }

    /**
     * Adds to {@code song} every pending hairpin that is well formed, and records every one it
     * dropped.
     *
     * <p>Each pending hairpin is judged in order by {@link Hairpin#spanning} over the range from
     * its anchor's index to its end's index in the anchor's line. A hairpin {@code spanning}
     * returns takes the pending hairpin's shifts and offset as its
     * {@link Hairpin#getX1ShiftSs()}, {@link Hairpin#getX2ShiftSs()} and
     * {@link LineElement#getUserYOffsetSs()}, and is added to that line with {@link Span#addTo},
     * which merges it with each hairpin of its kind already there that it overlaps or touches.
     * One that {@code spanning} refuses is dropped. Adding a hairpin changes no element, so every
     * later pending hairpin's indices are the ones the reader built. Because the hairpins are
     * judged in order and {@code spanning} sees the hairpins already added, of two pending
     * hairpins of opposite kinds that share more than one element, the pass drops the later.
     *
     * <p>No reader records a hairpin whose anchor and end are on different lines: the MusicXML
     * reader drops a hairpin still open when its line ends, and the legacy format stores each
     * hairpin as a pair of indices into one line, which the legacy reader rejects unless the
     * first is at or before the second. So an anchor and an end on different lines is a reader's
     * bug, not file input, and the pass rejects it rather than reporting it.
     *
     * <p>The whole pass runs with mutation tracking suspended, so it emits no mutations:
     * outside suspension the additions would land in the undo history and a freshly opened
     * song could be undone back to a song without its hairpins. The song's modified flag is
     * left alone — {@code ScoreView.setSong} clears it when the song is installed, so the
     * caller re-marks it afterwards from the report.
     *
     * @param song            the song to add the hairpins to, immediately after parsing and
     *                        before it is installed
     * @param pendingHairpins the hairpins the reader found in {@code song}. Each one's anchor
     *                        sits in a line of {@code song}, and its end sits in the same line at
     *                        or after the anchor
     * @param report          the report the pass records into
     * @throws IllegalArgumentException if a pending hairpin's anchor sits in no line of
     *     {@code song}, or its end does not sit in the anchor's line at or after the anchor
     * @effects adds a {@link Hairpin} to its anchor's line for every pending hairpin not dropped;
     *     records to {@code report}, in the order of {@code pendingHairpins}, every pending
     *     hairpin the pass dropped, under {@link Strings#DIALOG_REPORT_HAIRPINS_REMOVED} with
     *     the reason {@link Hairpin#spanning} gave
     * @log WARN one line for each pending hairpin dropped, naming its line, its range and the
     *     reason
     */
    public static void run(Song song, List<PendingHairpin> pendingHairpins, ChangeReport report) {
        song.withoutMutationTracking(() -> {
            for (var pending : pendingHairpins) {
                SpanLoadPass.place(song, placement(pending), report);
            }
        });
    }

    private static SpanLoadPass.Placement<Hairpin> placement(PendingHairpin pending) {
        var kind = pending.kind();

        return new SpanLoadPass.Placement<>(
            pending.anchor(),
            pending.end(),
            (line, range) -> Hairpin.spanning(line, kind, range),
            hairpin -> {
                hairpin.setX1ShiftSs(pending.x1ShiftSs());
                hairpin.setX2ShiftSs(pending.x2ShiftSs());
                hairpin.setUserYOffsetSs(pending.yOffsetSs());
            },
            Strings.DIALOG_REPORT_HAIRPINS_REMOVED,
            kind.name().toLowerCase(Locale.ROOT));
    }
}
