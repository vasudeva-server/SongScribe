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

import java.util.EnumSet;

/**
 * Identifies which fields changed on an element in an {@link ElementModification} mutation.
 * Populated incrementally as {@link ElementModification} emitters are added.
 */
public enum ElementField {
    /** The element's slide (fall, glissando, or slide-out) was set or cleared. */
    SLIDE,

    /**
     * The element's pitch (staff position and stem direction) was changed.
     * Emitted by {@code NoteDragHandler} when a note is pitch-dragged.
     */
    PITCH,

    /** The element's trill flag was toggled. Emitted by {@code toggleTrill}. */
    TRILL,

    /** The element's stem direction (upper/lower) was changed. Emitted by {@code modifyStemDirection}. */
    UPPER,

    /** The element's stem-direction-auto flag was changed. Emitted by {@code modifyStemDirection}. */
    STEM_DIRECTION_AUTO,

    /** The element's fermata flag was toggled. Emitted by {@code FermataAction}. */
    FERMATA,

    /** The element's accidental was changed. Emitted by {@code AccidentalAction}. */
    ACCIDENTAL,

    /** The element's accidental-in-parentheses flag was toggled. Emitted by {@code AccidentalInParensAction}. */
    ACCIDENTAL_IN_PARENS,

    /**
     * The element's dot count was changed. Emitted by {@code DotAction}.
     * Duration-affecting: see {@link #DURATION_AFFECTING}.
     */
    DOT_COUNT,

    /** The element's articulation list was changed. Emitted by articulation actions. */
    ARTICULATION,

    /** The element's dynamic attachment was changed. Emitted by {@code DynamicMarkingAction}. */
    DYNAMIC_ATTACHMENT,

    /** The element's tempo change was set or cleared. Emitted by {@code TempoChangeDialog}. */
    TEMPO_CHANGE,

    /** The element's beat change was set or cleared. Emitted by {@code BeatChangeDialog}. */
    BEAT_CHANGE,

    /** The element's annotation was set or cleared. Emitted by {@code AnnotationDialog}. */
    ANNOTATION,

    /** The syllable relation on one or more of the element's lyrics was changed. */
    LYRIC,

    /**
     * A mid-line key change's key was changed. Emitted by {@code KeyChangeDialogController}.
     *
     * <p>{@code Song.maintainKeyInvariant} re-derives what every following line inherits off the
     * resulting {@link ElementModification}, so this field is what keeps a key edit propagating
     * through undo and redo as well as forward.
     */
    KEY;

    /**
     * Fields that change an element's effective duration. {@code Line.modifyElement} removes any
     * containing tuplet when the modified field set intersects this set. Add new
     * duration-affecting fields here to extend the tuplet-immutability policy.
     */
    public static final EnumSet<ElementField> DURATION_AFFECTING = EnumSet.of(DOT_COUNT);
}
