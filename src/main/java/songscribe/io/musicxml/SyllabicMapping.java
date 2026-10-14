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

import songscribe.dom.Lyric;

/**
 * Bidirectional lookup table between SongScribe {@link Lyric.Syllabic} /
 * {@link Lyric.Extend} values and their MusicXML {@code <syllabic>} text
 * content / {@code <extend type>} attribute tokens.
 *
 * <p>{@link Lyric.Extend#NONE} has <b>no</b> MusicXML token: it is the absence of an
 * {@code <extend>} child. {@link #forExtend} therefore rejects it, and {@link #forExtendToken}
 * never produces it.
 */
final class SyllabicMapping {

    static final String SYLLABIC_SINGLE = "single";
    static final String SYLLABIC_BEGIN  = "begin";
    static final String SYLLABIC_MIDDLE = "middle";
    static final String SYLLABIC_END    = "end";

    // NONE has no token: it represents the absence of an <extend> element.

    static final String EXTEND_START    = "start";
    static final String EXTEND_STOP     = "stop";
    static final String EXTEND_CONTINUE = "continue";

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

    // NONE has no entry: forExtend() rejects it explicitly rather than returning null.

    private static final Map<Lyric.Extend, String> EXTEND_FORWARD_MAP = Map.of(
        Lyric.Extend.START,    EXTEND_START,
        Lyric.Extend.STOP,     EXTEND_STOP,
        Lyric.Extend.CONTINUE, EXTEND_CONTINUE
    );

    // "start" has no entry: it is the fallback forExtendToken() returns for an absent token, so
    // it is folded into the default rather than duplicated here.

    private static final Map<String, Lyric.Extend> EXTEND_REVERSE_MAP = Map.of(
        EXTEND_STOP,     Lyric.Extend.STOP,
        EXTEND_CONTINUE, Lyric.Extend.CONTINUE
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

    static String forExtend(Lyric.Extend extend) {
        var token = EXTEND_FORWARD_MAP.get(extend);

        if (token == null) {
            throw new IllegalArgumentException(extend + " has no <extend type> token");
        }

        return token;
    }

    /**
     * An absent or unrecognized token defaults to {@link Lyric.Extend#START}, as for a legacy
     * self-closed {@code <extend/>}.
     */
    static Lyric.Extend forExtendToken(@Nullable String token) {
        if (token == null) {
            return Lyric.Extend.START;
        }

        var extend = EXTEND_REVERSE_MAP.get(token);
        return extend != null ? extend : Lyric.Extend.START;
    }
}
