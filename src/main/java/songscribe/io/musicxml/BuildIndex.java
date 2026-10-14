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
package songscribe.io.musicxml;

import java.util.IdentityHashMap;
import java.util.Map;

import org.audiveris.proxymusic.Note;

import songscribe.dom.StaffElement;

/**
 * Handles into the {@link org.audiveris.proxymusic.ScorePartwise} graph, recorded by the
 * builders as they emit nodes.
 *
 * @param notes must be an {@link IdentityHashMap}: {@code StaffElement} defines no
 *     {@code equals}/{@code hashCode}, and value equality would collide equivalent rests and
 *     attach a span to the wrong note.
 */
record BuildIndex(Map<StaffElement, Note> notes) {

    BuildIndex {
        if (!(notes instanceof IdentityHashMap<StaffElement, Note>)) {
            throw new IllegalArgumentException("BuildIndex.notes must be an IdentityHashMap");
        }
    }
}
