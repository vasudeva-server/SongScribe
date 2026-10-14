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

/**
 * The changes the song made on its own during one edit or one load, in the order they were
 * recorded, for the user to be told about.
 *
 * <p>A finished report never changes after {@link Builder#build()} returns it, and its entries
 * hold line indices rather than model objects, so its holders can keep it without copying. The
 * owner of the edit or the load decides whether a change is recorded at all; neither type knows
 * of modification brackets, undo replay or suspended tracking.
 *
 * @param changes the entries, in the order recorded
 * @invariant {@link #changes()} holds every recorded entry exactly once, in the order recorded
 * @invariant {@link #changes()} is unmodifiable and never changes
 */
public record ChangeReport(List<ReportedChange> changes) {

    public static final ChangeReport EMPTY = new ChangeReport(List.of());

    public ChangeReport {
        changes = List.copyOf(changes);
    }

    public boolean isEmpty() {
        return changes.isEmpty();
    }

    /**
     * The collector of one edit's or one load's report: the only place a change is recorded.
     *
     * <p>A builder belongs to one song. {@link #build()} resolves recorded lines to indices only
     * then, because a line added or removed in front of a recorded line moves its index.
     *
     * <p>EDT-only, like the model it reports on; no locking.
     *
     * @invariant every line a recorded entry names is still a line of the song when
     *     {@link #build()} runs: no edit or load records a change on a line and then removes it
     */
    public static final class Builder {

        private final Song song;
        private final List<RecordedChange> changes = new ArrayList<>();

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

        public boolean isEmpty() {
            return changes.isEmpty();
        }

        /**
         * Returns the report of the entries recorded so far, each entry's lines resolved to
         * their indices in the song as it stands when this method runs, which the builder's
         * invariant guarantees exist.
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

        private @Nullable LineRange resolve(@Nullable ChangeLines where) {
            if (where == null) {
                return null;
            }

            var lines = song.getLines();

            return new LineRange(lines.indexOf(where.first()), lines.indexOf(where.last()));
        }
    }
}
