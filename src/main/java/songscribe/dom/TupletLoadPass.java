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
 * <p>A reader records each tuplet as a {@link PendingTuplet}. A file that stated a complete
 * ratio — MusicXML carrying {@code <normal-type>} — yields a pending tuplet whose M and V come
 * from the file. Every other file yields one carrying only the printed number, because the
 * ratio it stands for cannot be known mid-parse: the beat at the tuplet's anchor may still be
 * redefined by a tempo change in a later line.
 *
 * <p>This pass runs once the song is complete and is the one place a read tuplet becomes a
 * {@link Tuplet}. A stated ratio is trusted and checked only for self-consistency; an absent
 * one is derived from the beat in effect. Anything that fails is dropped — the bracket and the
 * number go, the notes stay — and nothing is ever repaired into something the file did not say.
 *
 * <h2>Why most existing files take the derived path</h2>
 *
 * <p>A MusicXML file written before SongScribe recorded {@code <normal-notes>} carries no M,
 * and the legacy {@code .mssw} format has no place to record a ratio at all. Every tuplet in
 * such a file has its ratio derived on load.
 *
 * <h2>Why it is a separate pass rather than reader work</h2>
 *
 * <p>Neither reader can judge a tuplet while it parses, since both meet their tuplets while
 * the song is still being assembled, before it holds the tempo changes that determine the
 * beat.
 */
public final class TupletLoadPass {

    private static final Logger LOG = LoggerFactory.getLogger(TupletLoadPass.class);

    private TupletLoadPass() {
    }

    /**
     * Adds to {@code song} every pending tuplet that can stand, and records what it did.
     *
     * <p>Each pending tuplet is judged leniently, under
     * {@link TupletValidator.Strictness#LENIENT}: the beat barrier and the structural boundary
     * constrain creation, not a document that already exists. A stated ratio is judged by
     * {@link TupletValidator#validateStated}, and an absent one takes the ratio the validator
     * derives. A tuplet that passes becomes a {@link Tuplet} with that ratio, the pending
     * tuplet's grade and its offset as {@link LineElement#getUserYOffsetSs()}, and is added to
     * its anchor's line. One whose span holds too few notes to carry a bracket, or which the
     * validator rejects, is dropped.
     *
     * <p>The whole pass runs with mutation tracking suspended, so it emits no mutations:
     * outside suspension the additions would land in the undo history and a freshly opened
     * song could be undone back to a song without its tuplets. The song's modified flag is
     * deliberately left alone — {@code ScoreView.setSong} clears it when the song is
     * installed, so the caller re-marks it afterwards from the report.
     *
     * @param song           the song to add the tuplets to, immediately after parsing and before
     *                       it is installed
     * @param pendingTuplets the tuplets the reader found in {@code song}. Each one's anchor and
     *                       end sit in the same line of {@code song}, the end at or after the
     *                       anchor, and no two overlap
     * @param report         the report the pass records into
     * @effects adds a {@link Tuplet} to its anchor's line for every pending tuplet not dropped;
     *     records to {@code report}, in document order, every pending tuplet the pass dropped
     *     and every one it added with a derived ratio; a pending tuplet added with its stated
     *     ratio is not recorded
     * @log INFO the dropped and derived counts, when either is not zero
     */
    public static void run(Song song, List<PendingTuplet> pendingTuplets, ChangeReport report) {
        var firstNewEntry = report.changes().size();

        song.withoutMutationTracking(() -> placeTuplets(song, pendingTuplets, report));

        var recorded = report.changes().subList(firstNewEntry, report.changes().size());
        var dropped = countUnder(recorded, Strings.DIALOG_REPORT_TUPLETS_REMOVED);
        var derived = countUnder(recorded, Strings.DIALOG_REPORT_TUPLETS_UPDATED);

        if (!recorded.isEmpty()) {
            LOG.info("Tuplet load pass: {} dropped, {} migrated", dropped, derived);
        }
    }

    private static long countUnder(List<ReportedChange> changes, String headingKey) {
        return changes.stream().filter(change -> change.headingKey().equals(headingKey)).count();
    }

    /**
     * One forward walk over the whole song, judging each pending tuplet against the beat the
     * walk carries. Adding a tuplet leaves the elements — and so every other pending tuplet's
     * indices — untouched, which is why the verdicts stay valid as they are applied.
     */
    private static void placeTuplets(Song song, List<PendingTuplet> pendingTuplets, ChangeReport report) {
        var startLineIndex = song.firstNonEmptyLineIndex();

        if (startLineIndex == null) {
            return;
        }

        var byAnchor = new IdentityHashMap<StaffElement, PendingTuplet>();

        for (var pending : pendingTuplets) {
            byAnchor.put(pending.anchor(), pending);
        }

        for (var verdict : TupletValidator.validateFrom(
            song, new ElementLocation(startLineIndex, 0), TupletValidator.Strictness.LENIENT,
            (line, index) -> pendingAnchoredAt(byAnchor, line, index))
        ) {
            place(song, verdict, report);
        }
    }

    /**
     * Adds one judged pending tuplet to its line, or drops it. Records a dropped tuplet, and a
     * tuplet added with a derived ratio.
     */
    private static void place(
        Song song, TupletValidator.Verdict<PendingTuplet> verdict, ChangeReport report
    ) {
        var pending = verdict.tuplet();
        var lineIndex = verdict.lineIndex();
        var line = song.getLine(lineIndex);
        var where = ElementLocationRange.onLine(lineIndex, verdict.span());
        var grade = pending.grade();
        var statedRatio = pending.statedRatio();
        var result = statedRatio == null
            ? verdict.result()
            : TupletValidator.validateStated(
                verdict.context(), grade, statedRatio, TupletValidator.Strictness.LENIENT);

        switch (result) {
            case TupletValidator.Result.Invalid(var reason) -> report.record(new ReportedChange(
                Strings.DIALOG_REPORT_TUPLETS_REMOVED, Strings.get(reason.messageKey()), where));

            case TupletValidator.Result.Valid(var ratio) -> {
                var tuplet = new Tuplet(pending.anchor(), pending.end(), grade, ratio);
                tuplet.setUserYOffsetSs(pending.yOffsetSs());
                tuplet.addTo(line);

                if (statedRatio == null) {
                    report.record(new ReportedChange(
                        Strings.DIALOG_REPORT_TUPLETS_UPDATED,
                        Strings.get(
                            Strings.DIALOG_REPORT_UPDATED_DETAIL, grade.number(), ratio.normalNotes()),
                        where));
                }
            }
        }
    }

    /** The pending tuplet anchored at the element at {@code index} in {@code line}, if any. */
    private static TupletValidator.@Nullable Anchored<PendingTuplet> pendingAnchoredAt(
        Map<StaffElement, PendingTuplet> byAnchor, Line line, int index
    ) {
        var pending = byAnchor.get(line.getElement(index));

        return pending == null
            ? null
            : new TupletValidator.Anchored<>(pending, line.getElementIndex(pending.end()), pending.grade());
    }

}
