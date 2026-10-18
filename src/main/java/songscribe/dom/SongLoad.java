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

/**
 * The one runner of a load: the whole of turning what a file reader builds into a song open for
 * editing, with the report of every change the load made to what the file said.
 *
 * <p>A reader builds the raw song and its pending marks and checks nothing. Every structural check
 * and repair is a load stage, so each holds for a song whichever reader built it.
 */
public final class SongLoad {

    /**
     * What a reader does in a load: build the raw song from its file.
     *
     * @param <R> what the reader hands back, carrying the pending marks
     * @param <X> a checked exception the reader throws on a file it cannot read
     * @param <Y> a second checked exception the reader throws on a file it cannot read
     */
    @FunctionalInterface
    public interface SongBuild<
        R extends SongBuild.Built, X extends Exception, Y extends Exception
    > {

        /**
         * @param song empty, and in a load
         */
        R build(Song song) throws X, Y;

        /**
         * What a reader's build hands back to the load.
         */
        interface Built {

            /**
             * @return each mark on a line of the song the build was given
             */
            PendingMarks marks();
        }
    }

    /**
     * A loaded song, open for editing, with the report of what the load changed and what the
     * reader handed back.
     */
    public record LoadedSong<R extends SongBuild.Built>(Song song, ChangeReport report, R built) {
    }

    private SongLoad() {
    }

    /**
     * @invariant the report holds every change that {@code build} and the load stages reported
     *     through {@link Song#report}, in the order reported
     * @invariant the song is open for editing: an edit to it records an undo entry and posts a
     *     {@link songscribe.message.notification.SongDidChangeNotification}
     * @invariant the load records nothing for undo, posts no notification and leaves the song's
     *     modified flag unset; the caller marks the song modified when the report is not empty
     * @throws X when {@code build} throws it
     * @throws Y when {@code build} throws it
     * @throws StrandedKeyChangeException when the song {@code build} built holds a key change
     *     that restates the key already in effect
     * @effects when {@code build} or a load stage throws, or the song is refused, the song is
     *     disposed and the exception propagates
     */
    public static <R extends SongBuild.Built, X extends Exception, Y extends Exception>
        LoadedSong<R> run(SongBuild<R, X, Y> build) throws X, Y, StrandedKeyChangeException {
        var song = Song.newInLoad();
        R built;
        ChangeReport report;

        try {
            built = build.build(song);
            new RawLoad(song, built.marks()).settle().stamp().place().repair();
        } catch (Throwable thrown) {
            song.dispose();
            throw thrown;
        } finally {
            report = song.endLoad();
        }

        return new LoadedSong<>(song, report, built);
    }
}
