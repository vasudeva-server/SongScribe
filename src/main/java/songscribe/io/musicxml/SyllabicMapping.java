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
 * <p>{@link Lyric.Extend#NONE} has <b>no</b> MusicXML token: it represents the
 * complete absence of an {@code <extend>} child, not a token value. {@link
 * #forExtend} therefore rejects it, and the reverse lookup ({@link
 * #forExtendToken}) never produces it — an absent or unrecognized {@code type}
 * attribute (including a legacy self-closed {@code <extend/>} with no
 * attribute at all) defaults to {@link Lyric.Extend#START}, mirroring the
 * legacy {@code StaffElementIO.parseExtendType}.
 *
 * <p>An unrecognized (or {@code null}) {@code <syllabic>} token defaults to
 * {@link Lyric.Syllabic#SINGLE} on read, mirroring the legacy writer's own
 * null-syllabic default ({@code case null -> "single"} in {@code
 * StaffElementIO}'s write loop). In practice a non-carrier {@link Lyric} can
 * never have a null syllabic — its compact constructor forbids it — so this
 * is a defensive fallback for hand-edited or otherwise malformed input, not a
 * path production code exercises.
 */
final class SyllabicMapping {

    // -------------------------------------------------------------------------
    // MusicXML <syllabic> token values
    // -------------------------------------------------------------------------

    static final String SYLLABIC_SINGLE = "single";
    static final String SYLLABIC_BEGIN  = "begin";
    static final String SYLLABIC_MIDDLE = "middle";
    static final String SYLLABIC_END    = "end";

    // -------------------------------------------------------------------------
    // MusicXML <extend type> token values. NONE is intentionally absent: it
    // represents the absence of an <extend> element, not a token.
    // -------------------------------------------------------------------------

    static final String EXTEND_START    = "start";
    static final String EXTEND_STOP     = "stop";
    static final String EXTEND_CONTINUE = "continue";

    // -------------------------------------------------------------------------
    // Forward map: Syllabic -> token
    // -------------------------------------------------------------------------

    private static final Map<Lyric.Syllabic, String> SYLLABIC_FORWARD_MAP = Map.of(
        Lyric.Syllabic.SINGLE, SYLLABIC_SINGLE,
        Lyric.Syllabic.BEGIN,  SYLLABIC_BEGIN,
        Lyric.Syllabic.MIDDLE, SYLLABIC_MIDDLE,
        Lyric.Syllabic.END,    SYLLABIC_END
    );

    // -------------------------------------------------------------------------
    // Reverse map: token -> Syllabic. An unrecognized token has no entry;
    // forSyllabicToken() falls back to SINGLE for anything missing.
    // -------------------------------------------------------------------------

    private static final Map<String, Lyric.Syllabic> SYLLABIC_REVERSE_MAP = Map.of(
        SYLLABIC_SINGLE, Lyric.Syllabic.SINGLE,
        SYLLABIC_BEGIN,  Lyric.Syllabic.BEGIN,
        SYLLABIC_MIDDLE, Lyric.Syllabic.MIDDLE,
        SYLLABIC_END,    Lyric.Syllabic.END
    );

    // -------------------------------------------------------------------------
    // Forward map: Extend -> token. NONE has no entry -- forExtend() rejects it
    // explicitly rather than silently returning null, mirroring
    // StaffElementIO.extendTypeAttr (which throws on NONE).
    // -------------------------------------------------------------------------

    private static final Map<Lyric.Extend, String> EXTEND_FORWARD_MAP = Map.of(
        Lyric.Extend.START,    EXTEND_START,
        Lyric.Extend.STOP,     EXTEND_STOP,
        Lyric.Extend.CONTINUE, EXTEND_CONTINUE
    );

    // -------------------------------------------------------------------------
    // Reverse map: token -> Extend. "start" is intentionally absent -- it is
    // also the fallback for an absent/unrecognized token, so forExtendToken()
    // folds it into the default rather than duplicating it here.
    // -------------------------------------------------------------------------

    private static final Map<String, Lyric.Extend> EXTEND_REVERSE_MAP = Map.of(
        EXTEND_STOP,     Lyric.Extend.STOP,
        EXTEND_CONTINUE, Lyric.Extend.CONTINUE
    );

    private SyllabicMapping() {}

    // -------------------------------------------------------------------------
    // Package API
    // -------------------------------------------------------------------------

    /**
     * Returns the MusicXML {@code <syllabic>} token for the given syllabic,
     * defaulting {@code null} to {@link #SYLLABIC_SINGLE} — matching the
     * legacy writer's null-syllabic default.
     */
    static String forSyllabic(Lyric.@Nullable Syllabic syllabic) {
        if (syllabic == null) {
            return SYLLABIC_SINGLE;
        }

        var token = SYLLABIC_FORWARD_MAP.get(syllabic);
        return token != null ? token : SYLLABIC_SINGLE;
    }

    /**
     * Returns the {@link Lyric.Syllabic} for the given MusicXML {@code
     * <syllabic>} token, defaulting an unrecognized (or {@code null}) token to
     * {@link Lyric.Syllabic#SINGLE} — matching the writer's own null-syllabic
     * default, so a malformed or hand-edited file degrades to the most
     * conservative syllabic rather than throwing.
     */
    static Lyric.Syllabic forSyllabicToken(@Nullable String token) {
        if (token == null) {
            return Lyric.Syllabic.SINGLE;
        }

        var syllabic = SYLLABIC_REVERSE_MAP.get(token);
        return syllabic != null ? syllabic : Lyric.Syllabic.SINGLE;
    }

    /**
     * Returns the MusicXML {@code <extend type>} token for the given extend
     * state.
     *
     * @throws IllegalArgumentException if {@code extend} is {@link
     *                                  Lyric.Extend#NONE} — NONE represents the
     *                                  absence of an {@code <extend>} element
     *                                  entirely, not a token value, mirroring
     *                                  {@code StaffElementIO.extendTypeAttr}.
     */
    static String forExtend(Lyric.Extend extend) {
        var token = EXTEND_FORWARD_MAP.get(extend);

        if (token == null) {
            throw new IllegalArgumentException(extend + " has no <extend type> token");
        }

        return token;
    }

    /**
     * Returns the {@link Lyric.Extend} for the given MusicXML {@code <extend
     * type>} attribute value, defaulting an absent or unrecognized token to
     * {@link Lyric.Extend#START} — mirroring {@code
     * StaffElementIO.parseExtendType}, including its handling of the legacy
     * self-closed {@code <extend/>} (no {@code type} attribute at all).
     */
    static Lyric.Extend forExtendToken(@Nullable String token) {
        if (token == null) {
            return Lyric.Extend.START;
        }

        var extend = EXTEND_REVERSE_MAP.get(token);
        return extend != null ? extend : Lyric.Extend.START;
    }
}
