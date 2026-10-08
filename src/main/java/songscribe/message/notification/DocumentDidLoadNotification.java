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

import songscribe.dom.ChangeReport;
import songscribe.dom.Song;
import songscribe.message.Message;

/**
 * Posted when a document is loaded (File &gt; Open, File &gt; New).
 * Subscribers that need to perform a full reset in response to a new document
 * should handle this notification via a dedicated {@code @Handler} method.
 */
public class DocumentDidLoadNotification extends Message {

    /** Whether the load left the song as its file holds it. */
    public enum LoadChange {
        /** The song is what the file holds, or a new document no file holds yet. */
        UNCHANGED,

        /** The load changed the song, so it differs from its file until it is saved. */
        CHANGED;

        /**
         * Returns the load change a load with {@code report} made.
         *
         * @param report what the load changed in the document
         * @return {@link #CHANGED} when {@code report} holds an entry, otherwise
         *     {@link #UNCHANGED}
         */
        public static LoadChange of(ChangeReport report) {
            return report.isEmpty() ? UNCHANGED : CHANGED;
        }
    }

    private final Song song;
    private final LoadChange loadChange;

    public DocumentDidLoadNotification(Song song, LoadChange loadChange) {
        this.song = song;
        this.loadChange = loadChange;
    }

    public Song getSong() {
        return song;
    }

    /**
     * Returns whether the load changed the song.
     *
     * @return {@link LoadChange#CHANGED} when the song differs from the file it was read from
     */
    public LoadChange getLoadChange() {
        return loadChange;
    }
}
