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
import songscribe.dom.NoteValue;

/**
 * Bidirectional lookup table between a SongScribe {@link NoteValue} and its MusicXML
 * {@code <type>} token, plus tick-duration computation.
 *
 * <p>A rest is distinguished from its note counterpart by a {@code <rest/>} child
 * within the {@code <note>} element, not by a separate token. A grace note is
 * distinguished by a {@code <grace>} child within the {@code <note>} element. Both
 * the rest and grace variants map to the same {@code <type>} token as the
 * corresponding pitched note, which is why the tables key on the value rather than
 * on the {@link ElementType}.
 *
 * <p>This class owns the undotted {@code <duration>} tick count of each value: the
 * {@code <type>} token and the tick count both key off the same {@link NoteValue},
 * so they share one class rather than two parallel tables. Dots lengthen that base
 * through {@link songscribe.dom.Dots#augment}.
 *
 * <p>Special-shape rules:
 * <ul>
 *   <li>Grace notes emit {@code <type>} but <b>no</b> {@code <duration>} —
 *       use {@link #hasDuration(ElementType)} to guard {@code <duration>} emission.</li>
 *   <li>Rests emit {@code <duration>} + {@code <type>} + {@code <rest/>}.</li>
 * </ul>
 *
 * <p>ASCII table — single {@code NoteValue → <type> token + base-tick factor}.
 * A rest and a grace note share the value of their pitched counterpart:
 * <pre>
 * NoteValue       &lt;type&gt;    base ticks (DIVISIONS = 13440)
 * ---------------------------------------------------------
 * WHOLE           whole     4 × DIVISIONS = 53760
 * HALF            half      2 × DIVISIONS = 26880
 * QUARTER         quarter   1 × DIVISIONS = 13440
 * EIGHTH          eighth    DIVISIONS / 2  =  6720
 * SIXTEENTH       16th      DIVISIONS / 4  =  3360
 * THIRTY_SECOND   32nd      DIVISIONS / 8  =  1680
 *
 * Dot augmentation: base × (2^(d+1) − 1) / 2^d   where d = dotCount
 *   0 dots → × 1      (base)
 *   1 dot  → × 3/2    (base + base/2)
 *   2 dots → × 7/4    (base + base/2 + base/4)
 *
 * Divisibility check — DIVISIONS must survive every tuplet ratio N in {2..7}
 * as well as every dot count, not just the plain base values above:
 *   smallest fraction: double-dotted 32nd = 1680 × 7/4 = 2940 ticks  ✓
 *   2940 must also stay an exact integer once scaled by M/N for every
 *   conventional tuplet — see the class-level DIVISIONS derivation below.
 * </pre>
 */
public final class NoteTypeMapping {

    // -------------------------------------------------------------------------
    // DIVISIONS: quarter-note tick resolution (global, not per-song, no
    // per-song prescan — a single compile-time constant).
    //
    // DIVISIONS must survive more than the plain note values: it must also
    // stay an exact integer once a tuplet scales a written duration by M/N,
    // for every ratio N in {2..7} the validator accepts (see the Shared
    // Reference of plans/604-invalid-tuplets.md for where M and N come from).
    //
    // Every written value is base × dotFactor, and every base is an integer
    // multiple of the 32nd note, so it suffices that the 32nd works. With
    // u = DIVISIONS / 8 (the 32nd-note tick count), the performed duration
    // u × f × M/N must be integral for f in {1, 3/2, 7/4} (the dot factors)
    // and N in {2..7}. M shares no guaranteed factor with N, so u × f must
    // be divisible by lcm(2..7) = 420 in each case:
    //
    //   f = 1      u        divisible by 420   ->  u divisible by 420   (2^2*3*5*7)
    //   f = 3/2    3u/2     divisible by 420   ->  u divisible by 280   (2^3*5*7)
    //   f = 7/4    7u/4     divisible by 420   ->  u divisible by 240   (2^4*3*5)
    //
    //   u = lcm(420, 280, 240) = 2^4*3*5*7 = 1680
    //   DIVISIONS = 8u                        = 13440   (2^7*3*5*7)
    //
    // Verification table (ticks, then ticks/N for N = 2..7):
    //
    //                     ticks    /2     /3     /4     /5     /6     /7
    // 32nd                 1680    840    560    420    336    280    240
    // dotted 32nd          2520   1260    840    630    504    420    360
    // double-dotted 32nd   2940   1470    980    735    588    490    420
    //
    // 3360 (= 480 × 7) is NOT enough: u = 420, so a double-dotted 32nd is
    // 735, which is odd, and 735 × 3/2 = 1102.5 inside a duplet. The
    // required factor depends on the dot count as well as the ratio, which
    // is why inspecting only the ratios present cannot find it. DIVISIONS = 480
    // has this defect independently of septuplets.
    //
    // A whole note at DIVISIONS = 13440 is 53760 ticks, far inside int range.
    // -------------------------------------------------------------------------

    public static final int DIVISIONS = 13440;

    // -------------------------------------------------------------------------
    // Base ticks per undotted note value (all exact divisors of DIVISIONS).
    // -------------------------------------------------------------------------

    private static final int WHOLE_TICKS         = 4 * DIVISIONS; // 53760
    private static final int HALF_TICKS          = 2 * DIVISIONS; // 26880
    private static final int QUARTER_TICKS       = DIVISIONS;     // 13440
    private static final int EIGHTH_TICKS        = DIVISIONS / 2; //  6720
    private static final int SIXTEENTH_TICKS     = DIVISIONS / 4; //  3360
    private static final int THIRTY_SECOND_TICKS = DIVISIONS / 8; //  1680

    // -------------------------------------------------------------------------
    // MusicXML <type> token values
    // -------------------------------------------------------------------------

    public static final String TYPE_WHOLE   = "whole";
    public static final String TYPE_HALF    = "half";
    public static final String TYPE_QUARTER = "quarter";
    public static final String TYPE_EIGHTH  = "eighth";
    public static final String TYPE_16TH    = "16th";
    public static final String TYPE_32ND    = "32nd";

    // -------------------------------------------------------------------------
    // Reverse map for reading: token → NoteValue, which noteValueForTypeToken() answers
    // from. forTypeToken() turns the value
    // into the note or rest ElementType from the <rest/> flag, and recovers
    // GRACE_QUAVER from the <grace> flag. The forward direction is an exhaustive
    // switch in typeToken(), so it needs no map and no null branch.
    // -------------------------------------------------------------------------

    private static final Map<String, NoteValue> REVERSE_MAP = Map.of(
        TYPE_WHOLE,   NoteValue.WHOLE,
        TYPE_HALF,    NoteValue.HALF,
        TYPE_QUARTER, NoteValue.QUARTER,
        TYPE_EIGHTH,  NoteValue.EIGHTH,
        TYPE_16TH,    NoteValue.SIXTEENTH,
        TYPE_32ND,    NoteValue.THIRTY_SECOND
    );

    private NoteTypeMapping() {}

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Returns the MusicXML {@code <type>} token for the given {@link NoteValue}.
     *
     * @param value the written note value
     * @return the {@code note-type-value} token MusicXML spells that value with
     */
    public static String typeToken(NoteValue value) {
        return switch (value) {
            case WHOLE -> TYPE_WHOLE;
            case HALF -> TYPE_HALF;
            case QUARTER -> TYPE_QUARTER;
            case EIGHTH -> TYPE_EIGHTH;
            case SIXTEENTH -> TYPE_16TH;
            case THIRTY_SECOND -> TYPE_32ND;
        };
    }

    /**
     * The undotted {@code <duration>} tick count of {@code value}.
     *
     * @return the undotted tick count
     */
    static int baseTicks(NoteValue value) {
        return switch (value) {
            case WHOLE -> WHOLE_TICKS;
            case HALF -> HALF_TICKS;
            case QUARTER -> QUARTER_TICKS;
            case EIGHTH -> EIGHTH_TICKS;
            case SIXTEENTH -> SIXTEENTH_TICKS;
            case THIRTY_SECOND -> THIRTY_SECOND_TICKS;
        };
    }

    /**
     * Returns the {@link ElementType} for the given MusicXML {@code <type>} token
     * and note-shape flags, or {@code null} if the token is not recognised.
     *
     * @param typeToken the MusicXML {@code <type>} text content (e.g. {@code "quarter"}),
     *                  already trimmed
     * @param isRest    {@code true} if a {@code <rest/>} child was present in the {@code <note>}
     * @param isGrace   {@code true} if a {@code <grace>} child was present in the {@code <note>};
     *                  when {@code true} the token is not consulted, since the one grace type
     *                  is the answer whatever the token says
     * @return the grace type when {@code isGrace}; otherwise the rest or the note of the value
     *     the token names, or {@code null} if the token names no value
     */
    @Nullable
    public static ElementType forTypeToken(String typeToken, boolean isRest, boolean isGrace) {
        if (isGrace) {
            return ElementType.GRACE_QUAVER;
        }

        var value = noteValueForTypeToken(typeToken);

        if (value == null) {
            return null;
        }

        return isRest ? value.rest() : value.note();
    }

    /**
     * Returns the {@link NoteValue} a MusicXML {@code <type>}-valued token names, such as the text
     * of a {@code <type>} or a {@code <normal-type>}.
     *
     * @param typeToken the token's text content (e.g. {@code "quarter"}), already trimmed
     * @return the value the token names, or {@code null} if it names none
     */
    public static @Nullable NoteValue noteValueForTypeToken(String typeToken) {
        return REVERSE_MAP.get(typeToken);
    }

    /**
     * Returns {@code true} if the given {@link ElementType} emits a {@code <duration>}
     * element in MusicXML output: a pitched note or a rest does; a grace note does not,
     * because SongScribe assigns it zero playback time and never steals from the host
     * note. A type that is none of those has no duration at all.
     *
     * @param type the element type being written
     * @return {@code true} for a pitched note or a rest; {@code false} for every other type
     */
    public static boolean hasDuration(ElementType type) {
        return type.isDuration();
    }

}
