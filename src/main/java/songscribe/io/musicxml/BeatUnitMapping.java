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

import songscribe.dom.Duration;

/**
 * Bidirectional lookup table between SongScribe {@link Duration} values and
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
    // Dot counts used by this mapping (no note value needs more than one dot).
    // -------------------------------------------------------------------------

    private static final int NO_DOT = 0;
    private static final int ONE_DOT = 1;

    // -------------------------------------------------------------------------
    // Forward map: Duration → BeatUnitEntry (token + dot count)
    // -------------------------------------------------------------------------

    private static final Map<Duration, BeatUnitEntry> FORWARD_MAP = Map.of(
        Duration.SEMI_BREVE,      new BeatUnitEntry(NoteTypeMapping.TYPE_WHOLE, NO_DOT),
        Duration.MINIM,           new BeatUnitEntry(NoteTypeMapping.TYPE_HALF, NO_DOT),
        Duration.MINIM_DOTTED,    new BeatUnitEntry(NoteTypeMapping.TYPE_HALF, ONE_DOT),
        Duration.CROTCHET,        new BeatUnitEntry(NoteTypeMapping.TYPE_QUARTER, NO_DOT),
        Duration.CROTCHET_DOTTED, new BeatUnitEntry(NoteTypeMapping.TYPE_QUARTER, ONE_DOT),
        Duration.QUAVER,          new BeatUnitEntry(NoteTypeMapping.TYPE_EIGHTH, NO_DOT),
        Duration.QUAVER_DOTTED,   new BeatUnitEntry(NoteTypeMapping.TYPE_EIGHTH, ONE_DOT)
    );

    // -------------------------------------------------------------------------
    // Reverse map: (token, dot count) → Duration, derived from FORWARD_MAP so the
    // two directions cannot drift apart. A collision would silently drop an entry;
    // BeatUnitMappingTest guards FORWARD_MAP's injectivity to rule that out.
    // -------------------------------------------------------------------------

    private static final Map<BeatUnitKey, Duration> REVERSE_MAP = buildReverseMap();

    private BeatUnitMapping() {}

    private static Map<BeatUnitKey, Duration> buildReverseMap() {
        var reverse = new HashMap<BeatUnitKey, Duration>();

        for (var entry : FORWARD_MAP.entrySet()) {
            var beatUnit = entry.getValue();
            reverse.put(new BeatUnitKey(beatUnit.token(), beatUnit.dotCount()), entry.getKey());
        }

        return Map.copyOf(reverse);
    }

    // -------------------------------------------------------------------------
    // Package API
    // -------------------------------------------------------------------------

    /**
     * Returns the MusicXML beat-unit representation for the given
     * {@link Duration}, or {@code null} if the duration has no entry in the
     * forward map.
     */
    @Nullable
    static BeatUnitEntry forDuration(Duration duration) {
        return FORWARD_MAP.get(duration);
    }

    /**
     * Returns the {@link Duration} for the given MusicXML beat-unit token and
     * dot count, or {@code null} if the combination is not recognised.
     */
    @Nullable
    static Duration forBeatUnit(String token, int dotCount) {
        return REVERSE_MAP.get(new BeatUnitKey(token, dotCount));
    }

    // -------------------------------------------------------------------------
    // Value types
    // -------------------------------------------------------------------------

    /**
     * Carries the MusicXML beat-unit token and dot count for a {@link Duration}.
     */
    record BeatUnitEntry(String token, int dotCount) {}

    /**
     * Reverse-map key: a MusicXML beat-unit token paired with its dot count.
     */
    private record BeatUnitKey(String token, int dotCount) {}
}
