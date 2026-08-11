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
 * <p>{@code Measure.getNoteOrBackupOrForward()} and {@code Notations.getTiedOrSlurOrTuplet()}
 * are heterogeneous {@code List<Object>}s. Walking them with {@code instanceof} is possible —
 * Audiveris itself does it — but it should be the exception: a pass that can reach its target
 * through a handle recorded here must do so instead.
 *
 * <p>What {@code BuildIndex} is <em>not</em>: it collects output handles, it does not carry
 * input state forward the way {@code NoteWriteContext} did in the streaming writer. Nothing
 * reads a value from it that an earlier element wrote in order to decide what to emit next —
 * every read here is a later pass revisiting a node an earlier pass already finished.
 *
 * @param notes a {@link StaffElement} to the {@link Note} built for it. Must be an
 *     {@link IdentityHashMap}, which the constructor enforces. A plain {@code HashMap} gives
 *     identity semantics today only because {@code StaffElement} defines no
 *     {@code equals}/{@code hashCode} — deliberately absent so identity-based hash collections
 *     and layout caches keep working. If value equality is ever added there, a {@code HashMap}
 *     would collide two equivalent rests and silently attach a span to the wrong note, which
 *     would compile, pass every test, and corrupt the saved file.
 */
record BuildIndex(Map<StaffElement, Note> notes) {

    BuildIndex {
        if (!(notes instanceof IdentityHashMap<StaffElement, Note>)) {
            throw new IllegalArgumentException("BuildIndex.notes must be an IdentityHashMap");
        }
    }
}
