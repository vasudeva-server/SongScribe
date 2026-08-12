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

/**
 * The domain half of {@code SongSettingsDialog}: the document it edits, read and written on
 * its behalf.
 *
 * <p>Unlike {@link AttachmentBackEnd}, which is bound to one element for the life of its
 * dialog, this one is bound to <em>whatever document is open</em>. Song Settings is reached
 * from a cached menu action, so one dialog instance outlives many songs, and
 * {@link #read()} is answered afresh on every opening rather than once at binding time.
 *
 * @see DialogBackEnd for why the binding is done by whoever opens the dialog
 */
public interface SongSettingsBackEnd extends DialogBackEnd<SongSettingsOutput> {

    /**
     * Reads the document's current settings, for the dialog to show.
     *
     * <p>Called once per opening, before the window appears, and never while it is up. Reads
     * only: the document is left exactly as it was.
     *
     * @return the settings as the document now holds them, holding no live handle on it — the
     *         mutable parts ({@code Tempo}, the font holder) are copies the dialog is free to
     *         edit
     */
    SongSettingsInput read();
}
