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

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import songscribe.dom.StaffElementRun.ElementRange;

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
     * One thing the pass did to one tuplet. Every index is 0-based, as the document stores
     * it; a UI reporting them to the user converts to 1-based itself.
     */
    public sealed interface Change {

        /** The index of the line holding the tuplet. */
        int lineIndex();

        /** The tuplet's elements within that line, from its anchor to its last element. */
        ElementRange span();

        /** The printed tuplet number. */
        TupletGrade grade();

        /**
         * A tuplet the pass dropped. The reason is the validator's, except
         * {@link TupletValidator.Reason#TOO_FEW_NOTES}: a span holding too few notes to carry
         * a bracket is a rule of the pass, judged before the validator runs.
         */
        record Removed(int lineIndex, ElementRange span, TupletGrade grade,
                       TupletValidator.Reason reason) implements Change {}

        /** A tuplet whose ratio the file did not state and the pass derived. */
        record Updated(int lineIndex, ElementRange span, TupletGrade grade,
                       int normalNotes) implements Change {}
    }

    /**
     * Everything the pass did, in document order. A tuplet that states no ratio and is invalid
     * appears once, as {@link Change.Removed} — it is dropped, not migrated.
     */
    public record Report(List<Change> changes) {

        // Defensive copy — the record's contract is immutability, and the pass builds the
        // list incrementally before handing it over.
        public Report {
            changes = List.copyOf(changes);
        }

        public static Report empty() {
            return new Report(List.of());
        }

        // The counts are derived on demand rather than stored. Caching them would mean a
        // second component on the record that a caller could construct out of step with the
        // list it summarizes, and the list is short and read a handful of times per load.

        /** How many tuplets the pass dropped. */
        public int dropped() {
            return count(Change.Removed.class);
        }

        /** How many tuplets the pass derived a ratio for. */
        public int migrated() {
            return count(Change.Updated.class);
        }

        private int count(Class<? extends Change> kind) {
            return (int) changes.stream().filter(kind::isInstance).count();
        }

        public boolean isEmpty() {
            return changes.isEmpty();
        }
    }

    /**
     * Adds to {@code song} every pending tuplet that can stand, and reports what it did.
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
     * installed, so the caller re-marks it afterwards from the returned report.
     *
     * @param song           the song to add the tuplets to, immediately after parsing and before
     *                       it is installed
     * @param pendingTuplets the tuplets the reader found in {@code song}. Each one's anchor and
     *                       end sit in the same line of {@code song}, the end at or after the
     *                       anchor, and no two overlap
     * @return every pending tuplet the pass dropped, and every one it added with a derived
     *     ratio, in document order; a pending tuplet added with its stated ratio is not reported
     * @effects adds a {@link Tuplet} to its anchor's line for every pending tuplet not dropped
     * @log INFO the dropped and derived counts, when the report is not empty
     */
    public static Report run(Song song, List<PendingTuplet> pendingTuplets) {
        var report = new Report[1];

        song.withoutMutationTracking(() -> report[0] = placeTuplets(song, pendingTuplets));

        var placed = report[0];

        if (!placed.isEmpty()) {
            LOG.info("Tuplet load pass: {} dropped, {} migrated", placed.dropped(), placed.migrated());
        }

        return placed;
    }

    /**
     * One forward walk over the whole song, judging each pending tuplet against the beat the
     * walk carries. Adding a tuplet leaves the elements — and so every other pending tuplet's
     * indices — untouched, which is why the verdicts stay valid as they are applied.
     */
    private static Report placeTuplets(Song song, List<PendingTuplet> pendingTuplets) {
        var startLineIndex = firstPopulatedLineIndex(song);

        if (startLineIndex == null) {
            return Report.empty();
        }

        var byAnchor = new IdentityHashMap<StaffElement, PendingTuplet>();

        for (var pending : pendingTuplets) {
            byAnchor.put(pending.anchor(), pending);
        }

        var changes = new ArrayList<Change>();

        for (var verdict : TupletValidator.validateFrom(
            song, new ElementLocation(startLineIndex, 0), TupletValidator.Strictness.LENIENT,
            (line, index) -> pendingAnchoredAt(byAnchor, line, index))
        ) {
            var change = place(song, verdict);

            if (change != null) {
                changes.add(change);
            }
        }

        return new Report(changes);
    }

    /**
     * Adds one judged pending tuplet to its line, or drops it.
     *
     * @return what to report, or {@code null} when the tuplet was added with the ratio its file
     *     stated
     */
    private static @Nullable Change place(Song song, TupletValidator.Verdict<PendingTuplet> verdict) {
        var pending = verdict.tuplet();
        var lineIndex = verdict.lineIndex();
        var line = song.getLine(lineIndex);
        var span = verdict.span();
        var grade = pending.grade();

        if (!Tuplet.holdsEnoughNotes(line, span)) {
            return new Change.Removed(lineIndex, span, grade, TupletValidator.Reason.TOO_FEW_NOTES);
        }

        var statedRatio = pending.statedRatio();
        var result = statedRatio == null
            ? verdict.result()
            : TupletValidator.validateStated(
                verdict.context(), grade, statedRatio, TupletValidator.Strictness.LENIENT);

        return switch (result) {
            case TupletValidator.Result.Invalid(var reason) ->
                new Change.Removed(lineIndex, span, grade, reason);

            case TupletValidator.Result.Valid(var ratio) -> {
                var tuplet = new Tuplet(pending.anchor(), pending.end(), grade, ratio);
                tuplet.setUserYOffsetSs(pending.yOffsetSs());
                tuplet.addTo(line);

                yield statedRatio == null
                    ? new Change.Updated(lineIndex, span, grade, ratio.normalNotes())
                    : null;
            }
        };
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

    /**
     * The first line holding any element, or {@code null} when the song holds none. The walk
     * has to start somewhere that exists — resolving the beat reads the starting element — and
     * starting at the first populated line loses nothing: an empty line can hold neither a
     * tuplet nor a beat-defining event.
     */
    private static @Nullable Integer firstPopulatedLineIndex(Song song) {
        for (var lineIndex = 0; lineIndex < song.lineCount(); lineIndex++) {
            if (song.getLine(lineIndex).elementCount() > 0) {
                return lineIndex;
            }
        }

        return null;
    }
}
