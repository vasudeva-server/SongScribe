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
 * Base class for runtime error reporting. Logs the error and shows
 * an alert dialog to the user.
 */
public class RuntimeError {

    private static boolean suppressDialogs = false;

    /**
     * Suppress error dialogs. Intended for use in unit tests.
     */
    public static void setSuppressDialogs(boolean suppress) {
        suppressDialogs = suppress;
    }

    /**
     * If the object is null, logs the message, shows an error alert, and returns true.
     *
     * @param object      the object to check
     * @param alertTitle  the title for the error dialog
     * @param logMessage  message written to the log
     * @param userMessage message shown to the user in the dialog
     * @return true if the object was null (error was reported), false otherwise
     */
    public static boolean logNull(
        @Nullable Object object,
        String alertTitle,
        String logMessage,
        String userMessage
    ) {
        if (object != null) {
            return false;
        }

        Log.error(logMessage);

        if (!suppressDialogs) {
            JOptionPane.showOptionDialog(
                null,
                userMessage,
                alertTitle,
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.ERROR_MESSAGE,
                null,
                new String[]{"OK"},
                "OK"
            );
        }

        return true;
    }

    protected RuntimeError() {}
}
