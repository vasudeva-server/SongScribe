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

package songscribe.ui.clipboard;

import org.jspecify.annotations.Nullable;

import songscribe.message.MessageCenter;
import songscribe.message.notification.ClipboardDidChangeNotification;

/**
 * Manages clipboard state for copy/paste operations.
 *
 * <p>This is app-global: it is constructed once ({@code ScoreView.java:251}) and
 * {@code ScoreView} is never recreated, only its {@code song} swapped, so it is
 * not cleared on document close. A clone's {@code line} back-reference keeps
 * that one {@code Song} reachable until the next copy replaces it — bounded,
 * harmless, and pre-existing.
 */
public final class ClipboardManager {

    // The last-captured fragment, or null if the clipboard is empty
    private @Nullable Fragment fragment;

    // -------------------------------------------------------------------------
    // Fragment accessors
    // -------------------------------------------------------------------------

    /**
     * Returns the current fragment, or {@code null} if the clipboard is empty.
     */
    public @Nullable Fragment getFragment() {
        return fragment;
    }

    /**
     * Returns the number of elements in the current fragment, or 0 if the
     * clipboard is empty.
     */
    public int getSize() {
        return fragment == null ? 0 : fragment.elements().size();
    }

    /**
     * Returns whether the clipboard is empty.
     */
    public boolean isEmpty() {
        return fragment == null;
    }

    // -------------------------------------------------------------------------
    // Fragment mutators
    // -------------------------------------------------------------------------

    /**
     * Replaces the current fragment with {@code fragment}.
     *
     * @param fragment The newly captured fragment
     */
    public void setFragment(Fragment fragment) {
        this.fragment = fragment;
        MessageCenter.post(new ClipboardDidChangeNotification());
    }

    /**
     * Clears the clipboard.
     */
    public void clear() {
        fragment = null;
        MessageCenter.post(new ClipboardDidChangeNotification());
    }
}
