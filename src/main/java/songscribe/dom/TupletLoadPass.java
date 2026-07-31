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
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Completes or drops every tuplet in a freshly parsed song.
 *
 * <p>A reader produces a tuplet in one of two states. A file that stated a complete ratio
 * — MusicXML carrying {@code <normal-type>} — yields a resolved tuplet whose M and V come
 * from the file. Every other file yields an unresolved tuplet carrying only the printed
 * number, because the ratio it stands for cannot be known mid-parse: the beat at the
 * tuplet's anchor may still be redefined by a tempo change in a later line.
 *
 * <p>This pass runs once the song is complete and settles both states. A stated ratio is
 * trusted and checked only for self-consistency; an absent one is derived from the beat in
 * effect. Anything that fails is dropped — the bracket and the number go, the notes stay —
 * and nothing is ever repaired into something the file did not say.
 *
 * <h2>Why every existing file migrates</h2>
 *
 * <p>Until the change that introduced this pass, the MusicXML reader discarded
 * {@code <normal-notes>} outright, so <em>no</em> file written by any earlier version of
 * this program carries an M. Every song containing a tuplet is therefore migrated on its
 * first load under the new format — not just the legacy {@code .mssw} ones, whose format
 * never had a place to record a ratio at all.
 *
 * <h2>Why it is a separate pass rather than reader work</h2>
 *
 * <p>It lives in {@code songscribe.dom} because {@link Tuplet#resolveRatio} is
 * package-private: a half-built tuplet may only be completed here. It cannot run inside
 * {@code RangeSpanResolver.resolveTuplet} or {@code LineIO.createTupletsFromPending}
 * either, since both run mid-parse, before the song holds the tempo changes that determine
 * the beat.
 */
public final class TupletLoadPass {

    private static final Logger LOG = LoggerFactory.getLogger(TupletLoadPass.class);

    /** Returned by {@link #firstPopulatedLineIndex} for a song holding no elements at all. */
    private static final int NO_POPULATED_LINE = -1;

    private TupletLoadPass() {
    }

    /**
     * One thing the pass did to one tuplet. Every index is 0-based, as the document stores
     * it; a UI reporting them to the user converts to 1-based itself.
     */
    public sealed interface Change {

        /** The index of the line holding the tuplet. */
        int lineIndex();

        /** The index of the tuplet's anchor element within that line. */
        int beginIndex();

        /** The index of the tuplet's last element within that line, inclusive. */
        int endIndex();

        /** The printed tuplet number. */
        int grade();

        /**
         * A tuplet the pass removed. The reason is the validator's, and is null only when
         * the verdict carried none — a span the walk rejected without naming a cause.
         */
        record Removed(int lineIndex, int beginIndex, int endIndex, int grade,
                       TupletValidator.@Nullable Reason reason) implements Change {}

        /** A tuplet whose ratio the pass derived and recorded for the first time. */
        record Updated(int lineIndex, int beginIndex, int endIndex, int grade,
                       int normalNotes) implements Change {}
    }

    /**
     * Everything the pass did, in document order. A tuplet that was both unresolved and
     * invalid appears once, as {@link Change.Removed} — it is dropped, not migrated.
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

        /** How many tuplets the pass removed. */
        public int dropped() {
            return (int) changes.stream().filter(Change.Removed.class::isInstance).count();
        }

        /** How many tuplets the pass completed from the convention. */
        public int migrated() {
            return (int) changes.stream().filter(Change.Updated.class::isInstance).count();
        }

        public boolean isEmpty() {
            return changes.isEmpty();
        }
    }

    /**
     * Resolves or drops every tuplet in {@code song}, and reports what it did.
     *
     * <p>The whole pass runs with mutation tracking suspended, so it emits no mutations:
     * outside suspension the removals would land in the undo history and a freshly opened
     * song could be undone back to its invalid state. The song's modified flag is
     * deliberately left alone — {@code ScoreView.setSong} clears it when the song is
     * installed, so the caller re-marks it afterwards from the returned report.
     *
     * @param song the song to settle, immediately after parsing and before it is installed
     * @return every tuplet the pass removed or completed, in document order
     */
    public static Report run(Song song) {
        var report = new Report[1];

        song.withoutMutationTracking(() -> report[0] = settleTuplets(song));

        var settled = report[0];

        if (!settled.isEmpty()) {
            LOG.info("Tuplet load pass: {} dropped, {} migrated", settled.dropped(), settled.migrated());
        }

        return settled;
    }

    /**
     * One forward walk over the whole song, judging each tuplet against the beat the walk
     * carries. Removing a tuplet leaves the elements — and so every other tuplet's indices
     * — untouched, which is why the verdicts stay valid as the walk's results are applied.
     */
    private static Report settleTuplets(Song song) {
        var changes = new ArrayList<Change>();
        var startLineIndex = firstPopulatedLineIndex(song);

        if (startLineIndex == NO_POPULATED_LINE) {
            return Report.empty();
        }

        for (var verdict : TupletValidator.validateFrom(
            song, startLineIndex, 0, TupletValidator.Strictness.LENIENT)
        ) {
            var tuplet = verdict.tuplet();
            var lineIndex = verdict.lineIndex();
            var line = song.getLine(lineIndex);
            var statedNoteValue = tuplet.getNoteValue();
            var result = verdict.result();

            if (statedNoteValue != null) {
                result = validateStated(line, tuplet, statedNoteValue, result);
            }

            var derivedNoteValue = result.noteValue();

            // Read the span now: removeTuplet detaches the tuplet from the elements its
            // indices are derived from, so afterwards there is nothing left to report.
            var beginIndex = tuplet.getAnchorElementIndex();
            var endIndex = tuplet.getEndElementIndex();
            var grade = tuplet.getGrade();

            if (!result.valid() || derivedNoteValue == null) {
                line.removeTuplet(tuplet);
                changes.add(new Change.Removed(lineIndex, beginIndex, endIndex, grade, result.reason()));
            } else if (statedNoteValue == null) {
                tuplet.resolveRatio(result.normalNotes(), derivedNoteValue, result.noteValueDots());
                changes.add(new Change.Updated(lineIndex, beginIndex, endIndex, grade, result.normalNotes()));
            }
        }

        return new Report(changes);
    }

    /**
     * The first line holding any element, or {@link #NO_POPULATED_LINE} when the song holds
     * none. The walk has to start somewhere that exists — resolving the beat reads the
     * starting element — and starting at the first populated line loses nothing: an empty
     * line can hold neither a tuplet nor a beat-defining event.
     */
    private static int firstPopulatedLineIndex(Song song) {
        for (var lineIndex = 0; lineIndex < song.lineCount(); lineIndex++) {
            if (song.getLine(lineIndex).elementCount() > 0) {
                return lineIndex;
            }
        }

        return NO_POPULATED_LINE;
    }

    /**
     * Judges a tuplet whose file stated its own ratio, applying only the constraints that
     * hold regardless of the beat. A span whose ends the line does not hold is malformed
     * and out of scope for this pass, so it keeps the walk's own verdict rather than being
     * indexed past the end of the line.
     */
    private static TupletValidator.Result validateStated(
        Line line, Tuplet tuplet, ElementType statedNoteValue, TupletValidator.Result walkResult
    ) {
        var beginIndex = tuplet.getAnchorElementIndex();
        var endIndex = tuplet.getEndElementIndex();

        if (beginIndex < 0 || endIndex < beginIndex || endIndex >= line.elementCount()) {
            return walkResult;
        }

        return TupletValidator.validateStated(
            line, beginIndex, endIndex, tuplet.getGrade(),
            tuplet.getNormalNotes(), statedNoteValue, tuplet.getNoteValueDots());
    }
}
