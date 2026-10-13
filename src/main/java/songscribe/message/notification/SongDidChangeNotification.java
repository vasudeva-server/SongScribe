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
 * Carries the accumulated list of mutations from the current modification bracket.
 *
 * <p>The mutation list is never empty: a bracket that accumulated nothing posts no
 * notification at all, so a subscriber may read the first mutation without guarding. It
 * describes the whole edit — every state change the bracket made is in it, which is what
 * lets {@code UndoController} replay it as a unit; see {@code docs/mutations.md}. Beside it
 * travels the edit's report ({@link #getReport()}), so a subscriber learns what the song
 * changed on its own from the same notification that tells it the song changed.
 *
 * <p><strong>EDT only.</strong> The cached {@link #getLine()} result is read and written
 * without synchronization; subscribers must call it from the event-dispatch thread.
 * This matches MBassador's synchronous dispatch and the rest of the SongScribe UI.
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
     * Constructs a notification that takes ownership of an already-immutable
     * mutation list. The caller must not retain or mutate the list after
     * construction — {@code ModificationSession}, when it closes the outermost modification
     * bracket, uses this to avoid defensively copying the accumulated list a second time.
     *
     * @param report what the song changed on its own during this edit; see {@link #getReport()}
     * @param opName the declared op-name for this edit, or {@code null} to let
     *               {@code UndoController} derive a type-based fallback label
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
     * The changes the song made on its own during this edit, for the user to be told about.
     *
     * @return the edit's report: empty when the song made no change on its own, and for an
     *         undo; for a redo, the report of the edit the redo applies again
     */
    public ChangeReport getReport() {
        return report;
    }

    /**
     * The name this edit's initiator declared for it.
     *
     * @return the declared op-name, or {@code null} when the edit declared none, in which
     *         case the type-based fallback label applies
     */
    @Nullable
    public String getOpName() {
        return opName;
    }

    /**
     * The one line this edit targeted, when it targeted exactly one. Song-scoped mutations
     * are ignored. The result is lazily cached.
     *
     * @return the line shared by every line-scoped mutation in the list, or {@code null}
     *         when there are none or they target different lines — see
     *         {@link #touchesLine} for the latter case
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

    /**
     * Returns whether any line-scoped mutation in this notification targets {@code line}.
     *
     * <p>This is the question {@link #getLine()} cannot answer for an edit that spans lines: there
     * being no <em>single</em> target line, it reports none at all, which reads as "no line was
     * touched" to a subscriber that only cares about one of them.
     *
     * @return {@code true} when at least one line-scoped mutation targets {@code line}
     */
    public boolean touchesLine(Line line) {
        for (var mutation : mutations) {
            if (mutation instanceof LineScopedMutation lineMutation && lineMutation.getLine() == line) {
                return true;
            }
        }

        return false;
    }

    /**
     * Whether this edit made a change of a given kind.
     *
     * @return {@code true} when the mutation list holds at least one instance of
     *         {@code type}
     */
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
