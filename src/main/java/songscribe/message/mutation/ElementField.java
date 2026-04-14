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

    /** The element's trill flag was toggled. Emitted by {@code toggleTrill}. */
    TRILL,

    /** The element's force-syllable flag was toggled. Emitted by {@code toggleLyricsUnderRests}. */
    FORCE_SYLLABLE,

    /** The element's stem direction (upper/lower) was changed. Emitted by {@code flipStemDirection}. */
    UPPER,

    /** The element's stem-direction-auto flag was changed. Emitted by {@code flipStemDirection}. */
    STEM_DIRECTION_AUTO,

    /** The element's fermata flag was toggled. Emitted by {@code FermataAction}. */
    FERMATA,

    /** The element's accidental was changed. Emitted by {@code AccidentalAction}. */
    ACCIDENTAL,

    /** The element's accidental-in-parentheses flag was toggled. Emitted by {@code AccidentalInParensAction}. */
    ACCIDENTAL_IN_PARENS,

    /**
     * The element's dot count was changed. Emitted by {@code DotAction}.
     * <p>
     * Duration-affecting: {@code Line.modifyElement} removes any containing
     * tuplet when this field is present. New duration-affecting fields must
     * update that guard.
     */
    DOT_COUNT,

    /** The element's articulation list was changed. Emitted by articulation actions. */
    ARTICULATION,

    /** The element's dynamic attachment was changed. Emitted by {@code DynamicMarkingAction}. */
    DYNAMIC_ATTACHMENT,
}
