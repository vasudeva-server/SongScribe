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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The changes the song made on its own during one edit or one load, in the order they were
 * recorded, for the user to be told about.
 *
 * <p>A finished report: a {@code ChangeReport} never changes after {@link Builder#build()}
 * returns it, so every holder — the notification that carries it, the undo step that keeps it
 * for a redo, the view that shows it — can keep it without copying. Only a {@link Builder}
 * records. Whoever owns the edit or the load creates the builder, records into it, and builds
 * the report when the edit or the load is over. Neither type knows anything of modification
 * brackets, undo replay or suspended tracking; deciding whether a change is recorded at all is
 * the owner's job.
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
     * Returns the recorded entries grouped by {@link ReportedChange#headingKey}.
     *
     * @return a new map from each heading key to its entries in the order recorded; the keys are
     *     ordered by the first entry recorded under each, and the map is empty when the report is
     * @invariant the lists together hold exactly the entries of {@link #changes()}
     */
    public Map<String, List<ReportedChange>> byHeading() {
        var groups = new LinkedHashMap<String, List<ReportedChange>>();

        for (var change : changes) {
            groups.computeIfAbsent(change.headingKey(), key -> new ArrayList<>()).add(change);
        }

        return groups;
    }

    /**
     * The collector of one edit's or one load's report: the only place a change is recorded.
     *
     * <p>EDT-only, like the model it reports on; no locking.
     */
    public static final class Builder {

        private final List<ReportedChange> changes = new ArrayList<>();

        /** Creates a builder with no entries. */
        public Builder() {
        }

        /**
         * Creates a builder that starts from the entries of {@code seed}, so the report it
         * builds holds them first, then whatever is recorded into it.
         *
         * @param seed the report whose entries this builder starts from
         */
        public Builder(ChangeReport seed) {
            changes.addAll(seed.changes());
        }

        /**
         * Appends {@code change} to the report being built.
         *
         * @param change the change to record
         * @effects {@code change} becomes the last entry of the report {@link #build()} returns;
         *     an entry equal to one already recorded is appended again, not merged
         */
        public void record(ReportedChange change) {
            changes.add(change);
        }

        /**
         * Returns whether the builder holds no entry, from its seed or recorded into it.
         *
         * @return {@code true} when {@link #build()} would return an empty report
         */
        public boolean isEmpty() {
            return changes.isEmpty();
        }

        /**
         * Returns the report of the entries recorded so far.
         *
         * @return a report holding the entries in the order recorded, which entries recorded
         *     into this builder afterwards do not reach
         */
        public ChangeReport build() {
            return new ChangeReport(changes);
        }
    }
}
