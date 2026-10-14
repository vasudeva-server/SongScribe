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

package songscribe.ui;

import java.awt.Component;

import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.layout.AccidentalFitGate;

/**
 * Tells the user why the fit gate refused an edit.
 */
public final class FitRefusals {

    private FitRefusals() {
    }

    /** @param parent {@code null} for the default */
    public static boolean accepts(
        AccidentalFitGate.Verdict verdict, @Nullable Component parent, Runnable tellEditedLineFull) {

        switch (verdict) {
            case FITS -> {
                return true;
            }
            case EDITED_LINE_FULL -> tellEditedLineFull.run();
            case OTHER_LINE_FULL -> OptionDialogs.showErrorMessage(
                parent, Strings.ALERT_TITLE_LINE_TOO_FULL, Strings.ERROR_LINE_FULL_OTHER_LINE);
        }

        return false;
    }
}
