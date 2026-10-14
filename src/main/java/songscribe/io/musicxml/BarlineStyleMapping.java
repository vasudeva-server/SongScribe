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
 * <p>{@code REPEAT_LEFT_RIGHT} is absent from both maps: it is written as a straddling barline
 * pair and recomposed from it, at a higher level.
 */
public final class BarlineStyleMapping {

    public static final String BAR_STYLE_REGULAR     = "regular";
    public static final String BAR_STYLE_LIGHT_LIGHT = "light-light";
    public static final String BAR_STYLE_LIGHT_HEAVY = "light-heavy";
    public static final String BAR_STYLE_HEAVY_LIGHT = "heavy-light";
    public static final String BAR_STYLE_NONE        = "none";

    public static final String REPEAT_FORWARD  = "forward";
    public static final String REPEAT_BACKWARD = "backward";

    public static final String LOCATION_LEFT  = "left";
    public static final String LOCATION_RIGHT = "right";

    private static final Map<ElementType, BarlineEntry> FORWARD_MAP = Map.of(
        ElementType.SINGLE_BARLINE,       new BarlineEntry(BAR_STYLE_REGULAR,     null,            LOCATION_RIGHT),
        ElementType.DOUBLE_BARLINE,       new BarlineEntry(BAR_STYLE_LIGHT_LIGHT, null,            LOCATION_RIGHT),
        ElementType.FINAL_DOUBLE_BARLINE, new BarlineEntry(BAR_STYLE_LIGHT_HEAVY, null,            LOCATION_RIGHT),
        ElementType.REPEAT_LEFT,          new BarlineEntry(BAR_STYLE_HEAVY_LIGHT, REPEAT_FORWARD,  LOCATION_LEFT),
        ElementType.REPEAT_RIGHT,         new BarlineEntry(BAR_STYLE_LIGHT_HEAVY, REPEAT_BACKWARD, LOCATION_RIGHT)
    );

    // Bar-style alone identifies the element except for light-heavy, which a repeat direction
    // disambiguates (REPEAT_RIGHT has one; FINAL_DOUBLE_BARLINE does not). heavy-light always
    // resolves to REPEAT_LEFT, so REVERSE_MAP_NO_REPEAT covers it alone.

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

    /**
     * Returns {@code null} if the type has no entry (e.g. {@code REPEAT_LEFT_RIGHT}).
     */
    @Nullable
    public static BarlineEntry forElementType(ElementType type) {
        return FORWARD_MAP.get(type);
    }

    /**
     * Returns {@code null} if the combination is not recognised.
     *
     * <p>A non-null {@code repeatDirection} wins: {@code light-heavy + backward} is
     * {@code REPEAT_RIGHT}, not {@code FINAL_DOUBLE_BARLINE}.
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

    public record BarlineEntry(
        String barStyle,
        @Nullable String repeatDirection,
        String location
    ) {}
}
