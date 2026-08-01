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
import songscribe.layout.LayoutResult;

/**
 * Bundles all per-note inputs for {@link MusicXmlWriter}'s {@code writeNote}
 * and {@code writeNotations} methods.
 *
 * <p>Grouping these inputs into a context record prevents parameter-list
 * growth as subsequent phases thread span markers and additional per-note
 * data through, keeping both call sites stable.
 *
 * @param note                     the note being written
 * @param typeToken                the note's MusicXML {@code <type>} token
 * @param nextIsBreathMark         whether a breath mark follows, to be folded into this note's
 *                                 {@code <notations>}
 * @param pendingStopGlissandoNote the note owning the glissando that stops on this one, or null
 *                                 when no glissando stops here. Addressed by its owning note
 *                                 rather than by the {@code Glissando} object because the
 *                                 geometry is keyed by note.
 * @param spanMarkers              this note's span start/end markers
 * @param layoutResult             the owning line's layout, source of the emitted glissando
 *                                 coordinates, or null when no layout is available
 */
record NoteWriteContext(
    StaffElement note,
    String typeToken,
    boolean nextIsBreathMark,
    @Nullable StaffElement pendingStopGlissandoNote,
    NoteSpanMarkers spanMarkers,
    @Nullable LayoutResult layoutResult
) {}
