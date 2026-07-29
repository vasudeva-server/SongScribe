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

import songscribe.dom.StaffElement.Accidental;

/**
 * Bidirectional lookup between SongScribe {@link Accidental} values and
 * their MusicXML {@code <accidental>} glyph token.
 *
 * <p>The sounding {@code <alter>} semitone is <b>not</b> kept here: it is derived
 * from {@link songscribe.dom.StaffElement#getPitchAdjustment} (the same source
 * playback uses) via {@link PitchSpelling}, so there is a single source of truth
 * for the alteration.
 *
 * <p>Every {@link Accidental} has a MusicXML token and every token here maps back
 * to an {@link Accidental}, so the two directions are exact inverses. Tokens for
 * retired accidentals that only appear in old files live in
 * {@link songscribe.io.LegacyAccidentals}, not here.
 */
public final class AccidentalMapping {

    // -------------------------------------------------------------------------
    // MusicXML <accidental> token values
    // -------------------------------------------------------------------------

    public static final String ACCIDENTAL_NATURAL       = "natural";
    public static final String ACCIDENTAL_FLAT          = "flat";
    public static final String ACCIDENTAL_SHARP         = "sharp";
    public static final String ACCIDENTAL_FLAT_FLAT     = "flat-flat";
    public static final String ACCIDENTAL_DOUBLE_SHARP  = "double-sharp";

    // -------------------------------------------------------------------------
    // Reverse map: token → Accidental
    // -------------------------------------------------------------------------

    private static final Map<String, Accidental> REVERSE_MAP = Map.of(
        ACCIDENTAL_NATURAL,       Accidental.NATURAL,
        ACCIDENTAL_FLAT,          Accidental.FLAT,
        ACCIDENTAL_SHARP,         Accidental.SHARP,
        ACCIDENTAL_FLAT_FLAT,     Accidental.DOUBLE_FLAT,
        ACCIDENTAL_DOUBLE_SHARP,  Accidental.DOUBLE_SHARP
    );

    private AccidentalMapping() {}

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Returns the MusicXML {@code <accidental>} token for the given
     * {@link Accidental}. Every accidental has one, so this never returns
     * {@code null}.
     *
     * <p>A switch rather than a map so that adding an {@link Accidental} without
     * a token here fails to compile, instead of failing when a user first saves
     * a score using the new accidental.
     */
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
     * Returns the {@link Accidental} for the given MusicXML {@code <accidental>}
     * token, or {@code null} if the token is not recognised.
     */
    @Nullable
    public static Accidental forToken(String token) {
        return REVERSE_MAP.get(token);
    }
}
