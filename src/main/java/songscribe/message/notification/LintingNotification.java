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
 * The notification that a song's lint inputs changed.
 *
 * <p>Its constructor lints the song ({@code song.getLinter().lint()}), so the song's report is
 * current before the notification exists. Every subscriber therefore reads the current report,
 * whatever its priority: no handler can run before the lint, because there is no notification to
 * deliver until the lint has run.
 *
 * <p>Its three subtypes are the three moments a song's lint inputs change:
 * {@link SongDidChangeNotification} (an edit, undo or redo), {@link DocumentDidLoadNotification}
 * (a load) and {@link ActiveScriptDidChangeNotification} (a script change).
 *
 * <p>The bus delivers each subtype to a handler whose parameter type is
 * {@code LintingNotification}, because it delivers a message to the handlers of its supertypes
 * unless a handler sets {@code rejectSubtypes}. A subscriber that reacts the same way to all three
 * moments has one handler of this type.
 *
 * <p>EDT only, as the song and its linter are.
 */
public abstract class LintingNotification extends Message {

    private final Song song;

    /**
     * Lints {@code song}, then holds it as the song whose lint inputs changed.
     *
     * @param song the song whose lint inputs changed
     * @effects replaces {@code song}'s lint report
     */
    protected LintingNotification(Song song) {
        this.song = song;
        song.getLinter().lint();
    }

    /**
     * The song whose lint inputs changed.
     *
     * @return the song; its lint report is already current
     */
    public Song getSong() {
        return song;
    }
}
