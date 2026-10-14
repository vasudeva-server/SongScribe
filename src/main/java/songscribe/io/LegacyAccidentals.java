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
package songscribe.io;

import java.util.Map;

import org.jspecify.annotations.Nullable;

import songscribe.dom.Accidental;

/**
 * Lookups for the retired {@code NATURAL_FLAT}, {@code NATURAL_SHARP}, and
 * {@code DOUBLE_NATURAL} {@link Accidental} values, keyed by their legacy
 * serialized forms.
 */
public final class LegacyAccidentals {

    public static final String ACCIDENTAL_NATURAL_FLAT  = "natural-flat";
    public static final String ACCIDENTAL_NATURAL_SHARP = "natural-sharp";

    private static final Map<String, Accidental> LEGACY_TOKEN_MAP = Map.of(
        ACCIDENTAL_NATURAL_FLAT,  Accidental.FLAT,
        ACCIDENTAL_NATURAL_SHARP, Accidental.SHARP
    );

    private static final Map<String, Accidental> LEGACY_NAME_MAP = Map.of(
        "NATURAL_FLAT",  Accidental.FLAT,
        "NATURAL_SHARP", Accidental.SHARP,
        "DOUBLE_NATURAL", Accidental.NATURAL
    );

    private LegacyAccidentals() {}

    /**
     * Returns {@code null} if {@code token} is not a retired token.
     */
    @Nullable
    public static Accidental forLegacyToken(String token) {
        return LEGACY_TOKEN_MAP.get(token);
    }

    /**
     * Underscore-less alias variants are absent; {@code StaffElementIO} owns the alias rule.
     */
    public static Map<String, Accidental> legacyNames() {
        return LEGACY_NAME_MAP;
    }
}
