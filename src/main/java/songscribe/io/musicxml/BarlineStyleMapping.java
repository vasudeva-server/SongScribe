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

import java.util.Map;

import org.jspecify.annotations.Nullable;

import songscribe.dom.ElementType;

/**
 * Bidirectional lookup table between SongScribe barline/repeat {@link ElementType}s
 * and their MusicXML {@code <bar-style>}, {@code <repeat direction>}, and
 * {@code location} representations.
 *
 * <p>{@code REPEAT_LEFT_RIGHT} is intentionally absent from both maps — the
 * writer decomposes it into a straddling barline pair, and the reader
 * recomposes it from that pair, so the mapping is handled at a higher level.
 */
public final class BarlineStyleMapping {

    // -------------------------------------------------------------------------
    // MusicXML bar-style attribute values
    // -------------------------------------------------------------------------

    public static final String BAR_STYLE_REGULAR     = "regular";
    public static final String BAR_STYLE_LIGHT_LIGHT = "light-light";
    public static final String BAR_STYLE_LIGHT_HEAVY = "light-heavy";
    public static final String BAR_STYLE_HEAVY_LIGHT = "heavy-light";
    public static final String BAR_STYLE_NONE        = "none";

    // -------------------------------------------------------------------------
    // MusicXML repeat direction attribute values
    // -------------------------------------------------------------------------

    public static final String REPEAT_FORWARD  = "forward";
    public static final String REPEAT_BACKWARD = "backward";

    // -------------------------------------------------------------------------
    // MusicXML barline location attribute values
    // -------------------------------------------------------------------------

    public static final String LOCATION_LEFT  = "left";
    public static final String LOCATION_RIGHT = "right";

    // -------------------------------------------------------------------------
    // Forward-map entries (one per supported ElementType)
    // -------------------------------------------------------------------------

    private static final Map<ElementType, BarlineEntry> FORWARD_MAP = Map.of(
        ElementType.SINGLE_BARLINE,       new BarlineEntry(BAR_STYLE_REGULAR,     null,            LOCATION_RIGHT),
        ElementType.DOUBLE_BARLINE,       new BarlineEntry(BAR_STYLE_LIGHT_LIGHT, null,            LOCATION_RIGHT),
        ElementType.FINAL_DOUBLE_BARLINE, new BarlineEntry(BAR_STYLE_LIGHT_HEAVY, null,            LOCATION_RIGHT),
        ElementType.REPEAT_LEFT,          new BarlineEntry(BAR_STYLE_HEAVY_LIGHT, REPEAT_FORWARD,  LOCATION_LEFT),
        ElementType.REPEAT_RIGHT,         new BarlineEntry(BAR_STYLE_LIGHT_HEAVY, REPEAT_BACKWARD, LOCATION_RIGHT)
    );

    // -------------------------------------------------------------------------
    // Reverse map — bar-style alone is sufficient for all cases except the
    // light-heavy ambiguity, which is resolved by the presence/absence of a
    // repeat direction (REPEAT_RIGHT has one; FINAL_DOUBLE_BARLINE does not).
    // heavy-light always resolves to REPEAT_LEFT and needs no repeat direction,
    // so it is covered by REVERSE_MAP_NO_REPEAT alone.
    // -------------------------------------------------------------------------

    private static final Map<String, ElementType> REVERSE_MAP_NO_REPEAT = Map.of(
        BAR_STYLE_REGULAR,     ElementType.SINGLE_BARLINE,
        BAR_STYLE_LIGHT_LIGHT, ElementType.DOUBLE_BARLINE,
        BAR_STYLE_LIGHT_HEAVY, ElementType.FINAL_DOUBLE_BARLINE,
        BAR_STYLE_HEAVY_LIGHT, ElementType.REPEAT_LEFT
    );

    private static final Map<String, ElementType> REVERSE_MAP_WITH_REPEAT = Map.of(
        BAR_STYLE_LIGHT_HEAVY, ElementType.REPEAT_RIGHT
    );

    private BarlineStyleMapping() {}

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Returns the MusicXML representation for the given barline/repeat
     * {@link ElementType}, or {@code null} if the type has no entry in the
     * forward map (e.g. {@code REPEAT_LEFT_RIGHT}).
     */
    @Nullable
    public static BarlineEntry forElementType(ElementType type) {
        return FORWARD_MAP.get(type);
    }

    /**
     * Returns the {@link ElementType} for the given MusicXML {@code <bar-style>}
     * and optional {@code <repeat direction>}, or {@code null} if the combination
     * is not recognised.
     *
     * <p>When {@code repeatDirection} is non-null, the with-repeat map is
     * consulted first so that {@code light-heavy + backward} resolves to
     * {@code REPEAT_RIGHT} rather than {@code FINAL_DOUBLE_BARLINE}.
     */
    @Nullable
    public static ElementType forBarStyle(String barStyle, @Nullable String repeatDirection) {
        if (repeatDirection != null) {
            var type = REVERSE_MAP_WITH_REPEAT.get(barStyle);
            if (type != null) {
                return type;
            }
        }

        return REVERSE_MAP_NO_REPEAT.get(barStyle);
    }

    // -------------------------------------------------------------------------
    // Value type
    // -------------------------------------------------------------------------

    /**
     * Carries the MusicXML fields needed to emit a {@code <barline>} element:
     * the {@code <bar-style>} text, an optional {@code <repeat direction>}
     * attribute value, and the {@code location} attribute value.
     */
    public record BarlineEntry(
        String barStyle,
        @Nullable String repeatDirection,
        String location
    ) {}
}
