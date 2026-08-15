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

import songscribe.dom.Song;
import songscribe.message.Message;

/**
 * Posted when a document is loaded (File &gt; Open, File &gt; New).
 * Subscribers that need to perform a full reset in response to a new document
 * should handle this notification via a dedicated {@code @Handler} method.
 */
public class DocumentDidLoadNotification extends Message {

    private final Song song;

    public DocumentDidLoadNotification(Song song) {
        this.song = song;
    }

    public Song getSong() {
        return song;
    }
}
