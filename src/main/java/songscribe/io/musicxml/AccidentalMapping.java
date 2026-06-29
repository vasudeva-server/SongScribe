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
 * Bidirectional lookup table between SongScribe {@link Accidental} values and
 * their MusicXML {@code <accidental>} glyph token.
 *
 * <p>The sounding {@code <alter>} semitone is <b>not</b> kept here: it is derived
 * from {@link songscribe.dom.StaffElement#getPitchAdjustment} (the same source
 * playback uses) via {@link PitchSpelling}, so there is a single source of truth
 * for the alteration.
 *
 * <p>{@link Accidental#DOUBLE_NATURAL} is not supported by MusicXML and has no
 * mapping in either direction.
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
    public static final String ACCIDENTAL_NATURAL_FLAT  = "natural-flat";
    public static final String ACCIDENTAL_NATURAL_SHARP = "natural-sharp";

    // -------------------------------------------------------------------------
    // Forward map: Accidental → AccidentalEntry (glyph token)
    // DOUBLE_NATURAL is absent — it has no MusicXML representation.
    // -------------------------------------------------------------------------

    private static final Map<Accidental, AccidentalEntry> FORWARD_MAP = Map.of(
        Accidental.NATURAL,       new AccidentalEntry(ACCIDENTAL_NATURAL),
        Accidental.FLAT,          new AccidentalEntry(ACCIDENTAL_FLAT),
        Accidental.SHARP,         new AccidentalEntry(ACCIDENTAL_SHARP),
        Accidental.DOUBLE_FLAT,   new AccidentalEntry(ACCIDENTAL_FLAT_FLAT),
        Accidental.DOUBLE_SHARP,  new AccidentalEntry(ACCIDENTAL_DOUBLE_SHARP),
        Accidental.NATURAL_FLAT,  new AccidentalEntry(ACCIDENTAL_NATURAL_FLAT),
        Accidental.NATURAL_SHARP, new AccidentalEntry(ACCIDENTAL_NATURAL_SHARP)
    );

    // -------------------------------------------------------------------------
    // Reverse map: token → Accidental
    // -------------------------------------------------------------------------

    private static final Map<String, Accidental> REVERSE_MAP = Map.of(
        ACCIDENTAL_NATURAL,       Accidental.NATURAL,
        ACCIDENTAL_FLAT,          Accidental.FLAT,
        ACCIDENTAL_SHARP,         Accidental.SHARP,
        ACCIDENTAL_FLAT_FLAT,     Accidental.DOUBLE_FLAT,
        ACCIDENTAL_DOUBLE_SHARP,  Accidental.DOUBLE_SHARP,
        ACCIDENTAL_NATURAL_FLAT,  Accidental.NATURAL_FLAT,
        ACCIDENTAL_NATURAL_SHARP, Accidental.NATURAL_SHARP
    );

    private AccidentalMapping() {}

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Returns the MusicXML representation for the given {@link Accidental},
     * or {@code null} if the accidental has no entry in the forward map
     * (e.g. {@link Accidental#DOUBLE_NATURAL}).
     */
    @Nullable
    public static AccidentalEntry forAccidental(Accidental accidental) {
        return FORWARD_MAP.get(accidental);
    }

    /**
     * Returns the {@link Accidental} for the given MusicXML {@code <accidental>}
     * token, or {@code null} if the token is not recognised.
     */
    @Nullable
    public static Accidental forToken(String token) {
        return REVERSE_MAP.get(token);
    }

    // -------------------------------------------------------------------------
    // Value type
    // -------------------------------------------------------------------------

    /**
     * Carries the MusicXML {@code <accidental>} glyph token.
     */
    public record AccidentalEntry(
        String token
    ) {}
}
