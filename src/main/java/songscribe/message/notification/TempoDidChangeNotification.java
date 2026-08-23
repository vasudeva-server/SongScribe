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

import songscribe.dom.Tempo;
import songscribe.message.Message;

/**
 * Announces that the song's tempo is to become {@code tempo}.
 *
 * <p>The tempo is carried whole. A sender that means to change one of its values reads the
 * song's current tempo, changes that value, and sends the result.
 */
public class TempoDidChangeNotification extends Message {

    private final Tempo tempo;

    /**
     * @param tempo the tempo the song is to take, which must be a copy detached from the song's
     *              own instance. The receiver copies its values onto the live instance, so a
     *              sender that passed the live instance would be asking it to copy from itself.
     */
    public TempoDidChangeNotification(Tempo tempo) {
        this.tempo = tempo;
    }

    /**
     * @return the tempo the song is to take
     */
    public Tempo getTempo() {
        return tempo;
    }
}
