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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import songscribe.util.Copyable;

/**
 * The changes the song made on its own during one edit or one load, in the order they were
 * recorded, for the user to be told about.
 *
 * <p>A plain collector: whoever owns the edit or the load creates one, records into it, and
 * hands it on. It knows nothing of modification brackets, undo replay or suspended tracking;
 * deciding whether a change is recorded at all is the owner's job.
 *
 * <p>EDT-only, like the model it reports on; no locking.
 *
 * @invariant {@link #changes()} holds every recorded entry exactly once, in the order recorded
 */
public final class ChangeReport implements Copyable<ChangeReport> {

    private final List<ReportedChange> changes = new ArrayList<>();

    /**
     * {@inheritDoc}
     *
     * <p>Copies the list of entries only: a {@link ReportedChange} is immutable, so the copy can
     * share them.
     *
     * @return a report holding the entries of this one, in the same order, to which later
     *     entries recorded here are not added
     */
    @Override
    public ChangeReport copy() {
        var copy = new ChangeReport();
        copy.changes.addAll(changes);
        return copy;
    }

    /**
     * Appends {@code change} to this report.
     *
     * @param change the change to record
     * @effects {@code change} becomes the last entry of {@link #changes()}; an entry equal to one
     *     already recorded is appended again, not merged
     */
    public void record(ReportedChange change) {
        changes.add(change);
    }

    /**
     * Returns whether nothing has been recorded.
     *
     * @return {@code true} when {@link #changes()} is empty
     */
    public boolean isEmpty() {
        return changes.isEmpty();
    }

    /**
     * Returns the recorded entries, in the order recorded.
     *
     * @return an unmodifiable view, which reflects entries recorded after it was returned
     */
    public List<ReportedChange> changes() {
        return Collections.unmodifiableList(changes);
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
}
