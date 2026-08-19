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
 * Whether a dialog blocks input to the rest of the application while it is up.
 * <p>
 * This says nothing about window ownership: every dialog is owned by the main frame,
 * because an unowned window carries no menu bar.
 */
public enum Modality {

    /** Blocks the rest of the application. */
    MODAL,

    /** Leaves the rest of the application usable while it is up. */
    MODELESS;

    /**
     * @return {@code true} for {@link #MODAL}, which is the form Swing's window
     *     constructors take modality in
     */
    public boolean isModal() {
        return this == MODAL;
    }
}
