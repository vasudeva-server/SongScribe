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
import songscribe.dom.Trill;
import songscribe.dom.Tuplet;

/**
 * Per-note span markers resolved once per line during the span precompute pass
 * in {@link MusicXmlWriter}.
 *
 * <p>Threaded into {@link NoteWriteContext} so {@code writeNote} and
 * {@code writeNotations} can do O(1) lookups without re-walking span lists.
 *
 * <p>Beam values are pre-computed per level. {@code beamLevelValues[i]} holds
 * the text content for {@code <beam number="i+1">}; an empty string means that
 * level is not active for this note. An empty array ({@code length == 0}) means
 * the note is not part of any beam group (or a degenerate single-note group that
 * produces no {@code <beam>} output).
 *
 * <p>For tuplets and trills the span reference is non-null for all notes in
 * the group (anchor, interior, and end). The anchor/end booleans identify the
 * group boundaries; interior notes have neither flag set.
 *
 * <p>For ties the flags are independent: both {@code tieStartsHere} and
 * {@code tieStopsHere} may be true on the same note when it is the end of
 * one tie and the anchor of the next in a chain.
 */
record NoteSpanMarkers(
    String[] beamLevelValues,
    boolean tieStartsHere,
    boolean tieStopsHere,
    @Nullable Tuplet tuplet,
    boolean isTupletAnchor,
    boolean isTupletEnd,
    @Nullable Trill trill,
    boolean isTrillAnchor,
    boolean isTrillEnd
) {}
