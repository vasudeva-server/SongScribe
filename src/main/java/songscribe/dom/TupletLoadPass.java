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

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import songscribe.Strings;

/**
 * Turns the tuplets a reader found into the song's tuplets, or drops them.
 *
 * <p>The one place a read {@link PendingTuplet} becomes a {@link Tuplet}. A stated ratio is
 * trusted and checked only for self-consistency; an absent one is derived from the beat in
 * effect. Anything that fails is dropped, and nothing is repaired into something the file did
 * not say.
 *
 * <p>It is a pass rather than reader work because the beat at a tuplet's anchor may still be
 * redefined by a tempo change later in the file, so neither reader can judge a tuplet while it
 * parses.
 */
final class TupletLoadPass {

    private static final Logger LOG = LoggerFactory.getLogger(TupletLoadPass.class);

    private TupletLoadPass() {
    }

    private record Counts(int dropped, int updated) {

        boolean isEmpty() {
            return dropped == 0 && updated == 0;
        }
    }

    /**
     * Adds to {@code song} every pending tuplet that can stand, and records what it did.
     *
     * <p>Each pending tuplet is judged leniently, under
     * {@link TupletValidator.Strictness#LENIENT}: the beat barrier and the structural boundary
     * constrain creation, not a document that already exists. A stated ratio is judged by
     * {@link Tuplet#spanningStated}, and an absent one is derived by {@link Tuplet#spanning}, each
     * at the beat in effect at the tuplet's anchor. A tuplet that passes takes the pending
     * tuplet's offset as its {@link LineElement#getUserYOffsetSs()} and is added to its anchor's
     * line. One that is refused is dropped.
     *
     * @param song           the song to add the tuplets to, immediately after parsing and before
     *                       it is installed
     * @param pendingTuplets the tuplets the reader found in {@code song}. Each one's anchor and
     *                       end sit in the same line of {@code song}, the end at or after the
     *                       anchor, and no two overlap
     * @param report         the report the pass records into
     * @effects adds a {@link Tuplet} to its anchor's line for every pending tuplet not dropped;
     *     records to {@code report}, in document order, every pending tuplet the pass dropped
     *     and every one it updated, that is, added with a derived ratio; a pending tuplet added
     *     with its stated ratio is not recorded
     */
    static void run(Song song, List<PendingTuplet> pendingTuplets, ChangeReport.Builder report) {
        var counts = placeTuplets(song, pendingTuplets, report);

        if (!counts.isEmpty()) {
            LOG.info("Tuplet load pass: {} dropped, {} updated", counts.dropped(), counts.updated());
        }
    }

    private static Counts placeTuplets(Song song, List<PendingTuplet> pendingTuplets, ChangeReport.Builder report) {
        var startLineIndex = song.firstNonEmptyLineIndex();

        if (startLineIndex == null) {
            return new Counts(0, 0);
        }

        var byAnchor = new IdentityHashMap<StaffElement, PendingTuplet>();

        for (var pending : pendingTuplets) {
            byAnchor.put(pending.anchor(), pending);
        }

        var dropped = 0;
        var updated = 0;

        for (var sited : TupletValidator.sitesFrom(
            song, new ElementLocation(startLineIndex, 0), (line, index) -> pendingAnchoredAt(byAnchor, line, index))
        ) {
            switch (place(sited, report)) {
                case DROPPED -> dropped++;
                case UPDATED -> updated++;
                case STATED -> {
                }
            }
        }

        return new Counts(dropped, updated);
    }

    private enum Placement {
        DROPPED,
        UPDATED,
        STATED
    }

    private static Placement place(TupletValidator.Sited<PendingTuplet> sited, ChangeReport.Builder report) {
        var pending = sited.tuplet();
        var site = sited.site();
        var where = ChangeLines.on(site.line());
        var grade = pending.grade();
        var statedRatio = pending.statedRatio();
        var spanning = statedRatio == null
            ? Tuplet.spanning(site, grade, TupletValidator.Strictness.LENIENT)
            : Tuplet.spanningStated(site, grade, statedRatio, TupletValidator.Strictness.LENIENT);

        return switch (spanning) {
            case Spanning.Refused<Tuplet> refused -> {
                report.record(refused.reportedAt(where));
                yield Placement.DROPPED;
            }

            case Spanning.Spanned(var tuplet, _) -> {
                tuplet.setUserYOffsetSs(pending.yOffsetSs());
                tuplet.addTo();

                if (statedRatio != null) {
                    yield Placement.STATED;
                }

                report.record(new RecordedChange(
                    Strings.REPORT_TUPLETS_UPDATED,
                    Strings.get(Strings.REPORT_UPDATED_DETAIL, grade.number(), tuplet.getRatio().normalNotes()),
                    where));
                yield Placement.UPDATED;
            }
        };
    }

    private static TupletValidator.@Nullable Anchored<PendingTuplet> pendingAnchoredAt(
        Map<StaffElement, PendingTuplet> byAnchor, Line line, int index
    ) {
        var pending = byAnchor.get(line.getElement(index));

        return pending == null
            ? null
            : new TupletValidator.Anchored<>(pending, line.getElementIndex(pending.end()));
    }
}
