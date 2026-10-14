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

public enum ElementField {
    SLIDE,
    PITCH,
    TRILL,
    STEM,
    FERMATA,
    ACCIDENTAL,
    DOT_COUNT,
    ARTICULATION,
    DYNAMIC_ATTACHMENT,
    TEMPO_CHANGE,
    BEAT_CHANGE,
    ANNOTATION,
    LYRIC,

    /**
     * {@code KeyInheritance.maintainInvariant} re-derives what every following line inherits off the
     * resulting {@link ElementModification}, so a key edit propagates through undo and redo too.
     */
    KEY;

    /** A modification inside a tuplet's bracket that rewrites a field in this set removes the tuplet; see {@code Tuplet.outcomeFor}. */
    public static final EnumSet<ElementField> DURATION_AFFECTING = EnumSet.of(DOT_COUNT);
}
