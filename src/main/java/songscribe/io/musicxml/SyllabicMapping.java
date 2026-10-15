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

import org.audiveris.proxymusic.StartStopContinue;
import org.jspecify.annotations.Nullable;

import songscribe.dom.Lyric;

/**
 * Bidirectional lookup table between SongScribe {@link Lyric.Syllabic} /
 * {@link Lyric.Extend} values and their MusicXML {@code <syllabic>} text
 * content / {@code <extend type>} attribute values.
 *
 * <p>{@link Lyric.Extend#NONE} has <b>no</b> MusicXML value: it is the absence of an
 * {@code <extend>} child. {@link #forExtend} therefore rejects it, and {@link #forExtendType}
 * never produces it.
 */
final class SyllabicMapping {

    static final String SYLLABIC_SINGLE = "single";
    static final String SYLLABIC_BEGIN  = "begin";
    static final String SYLLABIC_MIDDLE = "middle";
    static final String SYLLABIC_END    = "end";


    private static final Map<Lyric.Syllabic, String> SYLLABIC_FORWARD_MAP = Map.of(
        Lyric.Syllabic.SINGLE, SYLLABIC_SINGLE,
        Lyric.Syllabic.BEGIN,  SYLLABIC_BEGIN,
        Lyric.Syllabic.MIDDLE, SYLLABIC_MIDDLE,
        Lyric.Syllabic.END,    SYLLABIC_END
    );

    // forSyllabicToken() falls back to SINGLE for a token with no entry.

    private static final Map<String, Lyric.Syllabic> SYLLABIC_REVERSE_MAP = Map.of(
        SYLLABIC_SINGLE, Lyric.Syllabic.SINGLE,
        SYLLABIC_BEGIN,  Lyric.Syllabic.BEGIN,
        SYLLABIC_MIDDLE, Lyric.Syllabic.MIDDLE,
        SYLLABIC_END,    Lyric.Syllabic.END
    );


    private SyllabicMapping() {}

    /**
     * A {@code null} syllabic defaults to {@link #SYLLABIC_SINGLE}.
     */
    static String forSyllabic(Lyric.@Nullable Syllabic syllabic) {
        if (syllabic == null) {
            return SYLLABIC_SINGLE;
        }

        var token = SYLLABIC_FORWARD_MAP.get(syllabic);
        return token != null ? token : SYLLABIC_SINGLE;
    }

    /**
     * An unrecognized (or {@code null}) token defaults to {@link Lyric.Syllabic#SINGLE}, so a
     * malformed file degrades rather than throwing.
     */
    static Lyric.Syllabic forSyllabicToken(@Nullable String token) {
        if (token == null) {
            return Lyric.Syllabic.SINGLE;
        }

        var syllabic = SYLLABIC_REVERSE_MAP.get(token);
        return syllabic != null ? syllabic : Lyric.Syllabic.SINGLE;
    }

    static StartStopContinue forExtend(Lyric.Extend extend) {
        return switch (extend) {
            case START -> StartStopContinue.START;
            case STOP -> StartStopContinue.STOP;
            case CONTINUE -> StartStopContinue.CONTINUE;
            case NONE -> throw new IllegalArgumentException(extend + " has no <extend type> token");
        };
    }

    /**
     * An absent type defaults to {@link Lyric.Extend#START}, as for a legacy self-closed
     * {@code <extend/>}.
     */
    static Lyric.Extend forExtendType(@Nullable StartStopContinue type) {
        if (type == null) {
            return Lyric.Extend.START;
        }

        return switch (type) {
            case START -> Lyric.Extend.START;
            case STOP -> Lyric.Extend.STOP;
            case CONTINUE -> Lyric.Extend.CONTINUE;
        };
    }
}
