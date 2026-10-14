/*
    SongScribe song notation program
    Copyright (C) Sri Chinmoy Centres International

    This file is part of SongScribe.

    SongScribe is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 3 of the License, or
    (at your option) any later version.

    SongScribe is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package songscribe.message.notification;

import java.util.List;

import org.jspecify.annotations.Nullable;

import songscribe.dom.ChangeReport;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.message.mutation.LineScopedMutation;
import songscribe.message.mutation.Mutation;

/**
 * Posted when one or more mutations have been applied to the song.
 *
 * <p>The mutation list is never empty, so a subscriber may read the first mutation without
 * guarding, and it holds every state change the bracket made, so {@code UndoController} can
 * replay it as a unit. See {@code docs/mutations.md}.
 *
 * <p>EDT only: the cached {@link #getLine()} result is not synchronized.
 */
public class SongDidChangeNotification extends LintingNotification {

    private final List<Mutation> mutations;
    private final ChangeReport report;

    // Op-name declared by the initiator of this edit (Tier A/B), or null when the
    // edit declared no name — UndoController then derives a type-based fallback label.
    @Nullable
    private final String opName;

    // Lazy cache for getLine(). null is a valid result, so we need a separate flag.
    private boolean lineIsCached;
    @Nullable
    private Line cachedLine;

    /**
     * Takes ownership of an already-immutable mutation list; the caller must not retain or
     * mutate it afterwards.
     *
     * @param opName {@code null} lets {@code UndoController} derive a type-based fallback label
     */
    public SongDidChangeNotification(List<Mutation> mutations, Song song, ChangeReport report,
                                     @Nullable String opName) {
        super(song);
        this.mutations = mutations;
        this.report = report;
        this.opName = opName;
    }

    public List<Mutation> getMutations() {
        return mutations;
    }

    /**
     * @return the edit's report: empty for an undo; for a redo, the report of the edit the redo
     *         applies again
     */
    public ChangeReport getReport() {
        return report;
    }

    /** @return the declared op-name, or {@code null} when the edit declared none (the type-based label applies) */
    @Nullable
    public String getOpName() {
        return opName;
    }

    /**
     * @return the line shared by every line-scoped mutation in the list, or {@code null} when
     *         there are none or they target different lines — see {@link #touchesLine}
     */
    @Nullable
    public Line getLine() {
        if (lineIsCached) {
            return cachedLine;
        }

        Line result = null;

        for (var mutation : mutations) {
            if (mutation instanceof LineScopedMutation lineMutation) {
                var line = lineMutation.getLine();

                if (result == null) {
                    result = line;
                } else if (result != line) {
                    result = null;
                    break;
                }
            }
        }

        cachedLine = result;
        lineIsCached = true;
        return result;
    }

    public boolean touchesLine(Line line) {
        for (var mutation : mutations) {
            if (mutation instanceof LineScopedMutation lineMutation && lineMutation.getLine() == line) {
                return true;
            }
        }

        return false;
    }

    public boolean hasMutationOf(Class<? extends Mutation> type) {
        for (var mutation : mutations) {
            if (type.isInstance(mutation)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public String toString() {
        return super.toString() + "(mutations=" + mutations + ')';
    }
}
