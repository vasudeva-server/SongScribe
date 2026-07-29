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

import songscribe.dom.StaffElement.Accidental;

/**
 * Lookups for the retired {@code NATURAL_FLAT}, {@code NATURAL_SHARP}, and
 * {@code DOUBLE_NATURAL} {@link Accidental} values, keyed by their legacy
 * serialized forms. Lives in {@code io/} rather than {@code io/musicxml/}
 * because both the MusicXML reader and the legacy {@code .mssw} reader
 * consult it.
 *
 * <p>Unlike {@link songscribe.dom.BeatChange#fromLegacyName}, which throws on
 * an unrecognized legacy name, {@link #forLegacyToken} returns {@code null}
 * instead of throwing. A caller needs to distinguish "not a legacy token" from
 * "a legacy token that was converted" so it can flag that a conversion
 * happened; throwing would destroy that distinction.
 */
public final class LegacyAccidentals {

    // -------------------------------------------------------------------------
    // Retired MusicXML <accidental> token values
    // -------------------------------------------------------------------------

    public static final String ACCIDENTAL_NATURAL_FLAT  = "natural-flat";
    public static final String ACCIDENTAL_NATURAL_SHARP = "natural-sharp";

    // -------------------------------------------------------------------------
    // Legacy token → replacement Accidental
    // -------------------------------------------------------------------------

    private static final Map<String, Accidental> LEGACY_TOKEN_MAP = Map.of(
        ACCIDENTAL_NATURAL_FLAT,  Accidental.FLAT,
        ACCIDENTAL_NATURAL_SHARP, Accidental.SHARP
    );

    // -------------------------------------------------------------------------
    // Legacy enum name → replacement Accidental
    // -------------------------------------------------------------------------

    private static final Map<String, Accidental> LEGACY_NAME_MAP = Map.of(
        "NATURAL_FLAT",  Accidental.FLAT,
        "NATURAL_SHARP", Accidental.SHARP,
        "DOUBLE_NATURAL", Accidental.NATURAL
    );

    private LegacyAccidentals() {}

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Returns the replacement {@link Accidental} for a retired MusicXML
     * {@code <accidental>} token, or {@code null} if {@code token} is not a
     * retired token.
     */
    @Nullable
    public static Accidental forLegacyToken(String token) {
        return LEGACY_TOKEN_MAP.get(token);
    }

    /**
     * Returns every retired accidental's canonical serialized enum name mapped to
     * its replacement {@link Accidental}, for seeding a lookup table. Underscore-less
     * alias variants (e.g. {@code "NATURALFLAT"}) are absent — {@code StaffElementIO}
     * owns the alias rule and synthesizes them from these canonical names.
     */
    public static Map<String, Accidental> legacyNames() {
        return LEGACY_NAME_MAP;
    }
}
