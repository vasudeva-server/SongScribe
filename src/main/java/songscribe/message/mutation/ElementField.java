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

package songscribe.message.mutation;

/**
 * Identifies which fields changed on an element in an {@link ElementModification} mutation.
 * Populated incrementally as {@link ElementModification} emitters are added.
 */
public enum ElementField {
    /** The element's glissando zone was set or cleared. */
    GLISSANDO,

    /**
     * The element's pitch (staff position and stem direction) was changed.
     * Emitted by {@code NoteDragHandler} when a note is pitch-dragged.
     */
    PITCH,
}
