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

package songscribe.util;

import javax.swing.JOptionPane;

import org.jetbrains.annotations.Nullable;

/**
 * Handles fatal application errors caused by violated invariants
 * (e.g. a critical object is null). Logs the error, warns the user,
 * and exits the application.
 */
public final class FatalError {

    /**
     * Logs the message, shows an error dialog to the user, and exits.
     *
     * @param message Description of the violated invariant
     */
    public static void exit(String message) {
        Log.error("Fatal: " + message);

        JOptionPane.showOptionDialog(
            null,
            "Sorry, but a fatal error has occurred and the application must quit.",
            "Fatal Error",
            JOptionPane.DEFAULT_OPTION,
            JOptionPane.ERROR_MESSAGE,
            null,
            new String[]{"Quit"},
            "Quit"
        );

        System.exit(-1);
    }

    /**
     * If the object is null, logs the message, warns the user, and exits.
     *
     * @param object  The object to check
     * @param message Description of the violated invariant
     */
    public static void exitIfNull(@Nullable Object object, String message) {
        if (object == null) {
            exit(message);
        }
    }

    private FatalError() {}
}
