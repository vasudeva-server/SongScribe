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

import songscribe.dom.Accidental;

/**
 * Bidirectional lookup between SongScribe {@link Accidental} values and
 * their MusicXML {@code <accidental>} glyph token.
 *
 * <p>Every {@link Accidental} has a MusicXML token and every token here maps back
 * to an {@link Accidental}, so the two directions are exact inverses.
 */
public final class AccidentalMapping {

    public static final String ACCIDENTAL_NATURAL       = "natural";
    public static final String ACCIDENTAL_FLAT          = "flat";
    public static final String ACCIDENTAL_SHARP         = "sharp";
    public static final String ACCIDENTAL_FLAT_FLAT     = "flat-flat";
    public static final String ACCIDENTAL_DOUBLE_SHARP  = "double-sharp";

    private static final Map<String, Accidental> REVERSE_MAP = Map.of(
        ACCIDENTAL_NATURAL,       Accidental.NATURAL,
        ACCIDENTAL_FLAT,          Accidental.FLAT,
        ACCIDENTAL_SHARP,         Accidental.SHARP,
        ACCIDENTAL_FLAT_FLAT,     Accidental.DOUBLE_FLAT,
        ACCIDENTAL_DOUBLE_SHARP,  Accidental.DOUBLE_SHARP
    );

    private AccidentalMapping() {}

    public static String forAccidental(Accidental accidental) {
        return switch (accidental) {
            case NATURAL -> ACCIDENTAL_NATURAL;
            case FLAT -> ACCIDENTAL_FLAT;
            case SHARP -> ACCIDENTAL_SHARP;
            case DOUBLE_FLAT -> ACCIDENTAL_FLAT_FLAT;
            case DOUBLE_SHARP -> ACCIDENTAL_DOUBLE_SHARP;
        };
    }

    /**
     * Returns {@code null} if the token is not recognised.
     */
    @Nullable
    public static Accidental forToken(String token) {
        return REVERSE_MAP.get(token);
    }
}
