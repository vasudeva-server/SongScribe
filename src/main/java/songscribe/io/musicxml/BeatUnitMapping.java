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

import java.util.HashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import songscribe.dom.BeatUnit;

/**
 * Bidirectional lookup table between SongScribe {@link BeatUnit} values and
 * their MusicXML beat-unit representation: a type token ({@code <beat-unit>}
 * or {@code <metronome-type>}) plus a dot count ({@code <beat-unit-dot/>} or
 * {@code <metronome-dot/>}).
 *
 * <p>The token vocabulary is the same MusicXML {@code note-type-value} set used
 * for {@code <type>}, so the tokens come from {@link NoteTypeMapping} rather than
 * being redeclared here.
 *
 * <p>The same token/dot pair serves both the tempo {@code <metronome>} form
 * (beat-unit + per-minute) and the metric-modulation form
 * (two {@code <metronome-note>}s related by {@code <metronome-relation>}) —
 * see {@code MusicXmlWriter#writeTempoDirection} and
 * {@code MusicXmlWriter#writeMetricModulationDirection}.
 */
final class BeatUnitMapping {

    // -------------------------------------------------------------------------
    // Reverse map: (token, dot count) → BeatUnit, derived from the same
    // token/dot derivation entryFor uses, so the two directions cannot drift
    // apart.
    // -------------------------------------------------------------------------

    private static final Map<BeatUnitKey, BeatUnit> REVERSE_MAP = buildReverseMap();

    private BeatUnitMapping() {}

    private static Map<BeatUnitKey, BeatUnit> buildReverseMap() {
        var reverse = new HashMap<BeatUnitKey, BeatUnit>();

        for (var unit : BeatUnit.values()) {
            var entry = entryFor(unit);
            reverse.put(new BeatUnitKey(entry.token(), entry.dotCount()), unit);
        }

        return Map.copyOf(reverse);
    }

    // -------------------------------------------------------------------------
    // Package API
    // -------------------------------------------------------------------------

    /**
     * Returns the MusicXML beat-unit representation for the given {@link BeatUnit}.
     *
     * @param unit the beat unit being written
     * @return the token naming the unit's note value, with one dot when the unit is dotted
     *     and none otherwise
     */
    static BeatUnitEntry entryFor(BeatUnit unit) {
        return new BeatUnitEntry(
            NoteTypeMapping.typeToken(unit.noteValue()), unit.dotted() ? 1 : 0);
    }

    /**
     * Returns the {@link BeatUnit} for the given MusicXML beat-unit token and dot count.
     *
     * @param token    the {@code <beat-unit>} or {@code <metronome-type>} text, already trimmed
     * @param dotCount the number of {@code <beat-unit-dot/>} or {@code <metronome-dot/>} children
     * @return the unit {@link #entryFor} maps to that token and dot count, or {@code null} if
     *     no unit does
     */
    @Nullable
    static BeatUnit beatUnitFor(String token, int dotCount) {
        return REVERSE_MAP.get(new BeatUnitKey(token, dotCount));
    }

    // -------------------------------------------------------------------------
    // Value types
    // -------------------------------------------------------------------------

    /**
     * Carries the MusicXML beat-unit token and dot count for a {@link BeatUnit}.
     */
    record BeatUnitEntry(String token, int dotCount) {}

    /**
     * Reverse-map key: a MusicXML beat-unit token paired with its dot count.
     */
    private record BeatUnitKey(String token, int dotCount) {}
}
