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

package songscribe.message.command;

import songscribe.message.Message;

/**
 * This class is the superclass of any message that will modify
 * the music sheet's active note. This allows the ScoreView class
 * to listen for these messages and update the active note accordingly.
 */
public class UpdatePreviewElementCommand extends Message {

    /** How much of the preview element the command has to rebuild. */
    public enum Scope {
        /**
         * Re-apply the decoration toggles (dots, accidental, articulations) to the
         * existing preview element. Its type is unchanged.
         */
        DECORATIONS,

        /**
         * Recreate the preview element from the selected actions. Required whenever the
         * element's <em>type</em> may have changed — rest mode, or the duration/non-duration
         * selection — since decorating in place cannot turn a rest back into a note.
         */
        ELEMENT
    }

    private final Scope scope;

    public UpdatePreviewElementCommand(Scope scope) {
        this.scope = scope;
    }

    public Scope getScope() {
        return scope;
    }
}
