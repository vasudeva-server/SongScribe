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

import songscribe.message.Message;
import songscribe.message.mutation.LineScopedMutation;
import songscribe.message.mutation.Mutation;
import songscribe.music.Song;
import songscribe.music.Line;

/**
 * Posted when one or more mutations have been applied to the song.
 * Carries the accumulated list of mutations from the current modification bracket.
 *
 * <p><strong>EDT only.</strong> The cached {@link #getLine()} result is read and written
 * without synchronization; subscribers must call it from the event-dispatch thread.
 * This matches MBassador's synchronous dispatch and the rest of the SongScribe UI.
 */
public class SongDidChangeNotification extends Message {

    private final List<Mutation> mutations;
    private final Song song;

    // Lazy cache for getLine(). null is a valid result, so we need a separate flag.
    private boolean lineIsCached;
    @Nullable
    private Line cachedLine;

    /**
     * Constructs a notification that takes ownership of an already-immutable
     * mutation list. The caller must not retain or mutate the list after
     * construction — {@code Song.endModification} uses this to avoid
     * defensively copying the accumulated list a second time.
     */
    public SongDidChangeNotification(List<Mutation> mutations, Song song) {
        this.mutations = mutations;
        this.song = song;
    }

    public List<Mutation> getMutations() {
        return mutations;
    }

    public Song getSong() {
        return song;
    }

    /**
     * Returns the single line targeted by all line-scoped mutations in the list,
     * or {@code null} if no line-scoped mutations exist or they target different lines.
     * Song-scoped mutations are ignored. Result is lazily cached.
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
     * Returns {@code true} if the mutation list contains at least one instance
     * of the given mutation subclass.
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
