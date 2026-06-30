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

import org.jspecify.annotations.Nullable;
import songscribe.dom.StaffElement;

/**
 * Bundles all per-note inputs for {@link MusicXmlWriter}'s {@code writeNote}
 * and {@code writeNotations} methods.
 *
 * <p>Grouping these inputs into a context record prevents parameter-list
 * growth as subsequent phases thread span markers and additional per-note
 * data through, keeping both call sites stable.
 */
record NoteWriteContext(
    StaffElement note,
    String typeToken,
    boolean nextIsBreathMark,
    StaffElement.@Nullable Glissando pendingStopGlissando,
    NoteSpanMarkers spanMarkers
) {}
