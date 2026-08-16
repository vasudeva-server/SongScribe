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
package songscribe.ui.dialog;

import songscribe.prefs.Prefs;
import songscribe.prefs.PrefsKey;
import songscribe.ui.component.MainFrame;

/**
 * The controller behind {@link DoNotShowMessage}. Holds the message text and the {@link PrefsKey}
 * the checkbox suppresses through, matching {@link DoNotShowMessage}'s own class contract on what
 * {@code true} means for that key.
 *
 * <p>Constructed per gesture, by whoever wants a suppressible message shown, and discarded with
 * the dialog.
 */
final class DoNotShowMessageController extends DialogController<String, Boolean> {

    private final String message;
    private final PrefsKey suppressionKey;

    DoNotShowMessageController(MainFrame mainFrame, String message, PrefsKey suppressionKey) {
        super(mainFrame);
        this.message = message;
        this.suppressionKey = suppressionKey;
    }

    @Override
    protected String read() {
        return message;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Only ever writes {@code true}. Leaving the box clear means the message keeps appearing,
     * which is what the preference already says, so there is nothing to write back — and writing
     * it anyway would post a change notification for a preference that did not change.
     */
    @Override
    protected void commit(Boolean doNotShowAgain) {
        if (doNotShowAgain) {
            Prefs.put(suppressionKey, true);
        }
    }
}
