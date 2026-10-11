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
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * The changes the song made on its own during one edit or one load, in the order they were
 * recorded, for the user to be told about.
 *
 * <p>A finished report: a {@code ChangeReport} never changes after {@link Builder#build()}
 * returns it, and its entries hold line indices rather than model objects, so both of its
 * holders — the notification that carries it and the view that shows it — can keep it without
 * copying. Only a {@link Builder} records. Whoever owns the edit or the load creates the
 * builder, records into it, and builds the report when the edit or the load is over. Neither
 * type knows anything of modification brackets, undo replay or suspended tracking; deciding
 * whether a change is recorded at all is the owner's job.
 *
 * @param changes the entries, in the order recorded
 * @invariant {@link #changes()} holds every recorded entry exactly once, in the order recorded
 * @invariant {@link #changes()} is unmodifiable and never changes
 */
public record ChangeReport(List<ReportedChange> changes) {

    /** The report with no entries: an edit or a load that changed nothing on its own. */
    public static final ChangeReport EMPTY = new ChangeReport(List.of());

    /**
     * Creates a report holding {@code changes}, which later changes to {@code changes} do not
     * reach.
     *
     * @param changes the entries, in the order recorded
     */
    public ChangeReport {
        changes = List.copyOf(changes);
    }

    /**
     * Returns whether the report holds no entry.
     *
     * @return {@code true} when {@link #changes()} is empty
     */
    public boolean isEmpty() {
        return changes.isEmpty();
    }

    /**
     * The collector of one edit's or one load's report: the only place a change is recorded.
     *
     * <p>A builder belongs to one song, the song its entries name. A recorder hands it each
     * change with the {@link Line} objects the change was made on ({@link RecordedChange}), and
     * {@link #build()} resolves those lines to indices only then. Resolution waits for
     * {@code build()} because a report describes the song as the edit or load left it: a line
     * added or removed in front of a recorded line later in the same edit or load moves that
     * line's index, and resolving any earlier would make the entry name the wrong line.
     *
     * <p>EDT-only, like the model it reports on; no locking.
     */
    public static final class Builder {

        private final Song song;
        private final List<RecordedChange> changes = new ArrayList<>();

        /**
         * Creates a builder with no entries for a report on {@code song}.
         *
         * @param song the song whose lines every recorded entry names
         */
        public Builder(Song song) {
            this.song = song;
        }

        /**
         * Appends {@code change} to the report being built.
         *
         * @param change the change to record; its lines, when it has any, are lines of this
         *               builder's song
         * @effects {@code change} becomes the last entry of the report {@link #build()} returns;
         *     an entry equal to one already recorded is appended again, not merged
         */
        public void record(RecordedChange change) {
            changes.add(change);
        }

        /**
         * Returns whether the builder holds no entry.
         *
         * @return {@code true} when {@link #build()} would return an empty report
         */
        public boolean isEmpty() {
            return changes.isEmpty();
        }

        /**
         * Returns the report of the entries recorded so far, each entry's lines resolved to
         * their indices in the song as it stands when this method runs.
         *
         * <p>Every line an entry names is still a line of the song when the report is built:
         * removing a line takes the spans on it with it unreported, so nothing records a change
         * on a line that the same edit or load later removes.
         *
         * @return a report holding one {@link ReportedChange} per recorded entry, in the order
         *     recorded, which entries recorded into this builder afterwards do not reach
         * @invariant an entry recorded with no lines has no {@link LineRange}
         */
        public ChangeReport build() {
            var reported = new ArrayList<ReportedChange>(changes.size());

            for (var change : changes) {
                reported.add(new ReportedChange(change.headingKey(), change.description(), resolve(change.where())));
            }

            return new ChangeReport(reported);
        }

        /**
         * Returns {@code where} as line indices in the song as it stands now.
         *
         * @param where the lines to resolve, or {@code null} for a change with no place
         * @return the range of {@code where}'s lines, or {@code null} when {@code where} is
         */
        private @Nullable LineRange resolve(@Nullable ChangeLines where) {
            if (where == null) {
                return null;
            }

            return new LineRange(indexOf(where.first()), indexOf(where.last()));
        }

        private int indexOf(Line line) {
            return Objects.requireNonNull(song.indexOfLine(line), "a recorded change names a line the song no longer holds");
        }
    }
}
