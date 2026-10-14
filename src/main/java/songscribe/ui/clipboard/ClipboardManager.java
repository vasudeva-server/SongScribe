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
 * <p>This is app-global and is not cleared on document close; a clone's {@code line}
 * back-reference keeps that one {@code Song} reachable until the next copy replaces it.
 */
public final class ClipboardManager {

    private @Nullable Fragment fragment;

    /** {@code null} if the clipboard is empty. */
    public @Nullable Fragment getFragment() {
        return fragment;
    }

    public int getSize() {
        return fragment == null ? 0 : fragment.elements().size();
    }

    public boolean isEmpty() {
        return fragment == null;
    }

    public void setFragment(Fragment fragment) {
        this.fragment = fragment;
        MessageCenter.post(new ClipboardDidChangeNotification());
    }

    public void clear() {
        fragment = null;
        MessageCenter.post(new ClipboardDidChangeNotification());
    }
}
