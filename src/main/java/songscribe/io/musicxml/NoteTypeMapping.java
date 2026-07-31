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
 * Bidirectional lookup table between SongScribe note/rest/grace {@link ElementType}s
 * and their MusicXML {@code <type>} token, plus tick-duration computation.
 *
 * <p>A rest is distinguished from its note counterpart by a {@code <rest/>} child
 * within the {@code <note>} element, not by a separate token. A grace note is
 * distinguished by a {@code <grace>} child within the {@code <note>} element. Both
 * the rest and grace variants map to the same {@code <type>} token as the
 * corresponding pitched note.
 *
 * <p>This class owns the {@code ticks(type, dotCount)} duration math: the
 * {@code <type>} token and the tick count both key off the same {@link ElementType},
 * so they share one class rather than two parallel tables.
 *
 * <p>Special-shape rules:
 * <ul>
 *   <li>Grace notes emit {@code <type>} but <b>no</b> {@code <duration>} —
 *       use {@link #hasDuration(ElementType)} to guard {@code <duration>} emission.</li>
 *   <li>Rests emit {@code <duration>} + {@code <type>} + {@code <rest/>}.</li>
 * </ul>
 *
 * <p>ASCII table — single {@code ElementType → <type> token + base-tick factor}:
 * <pre>
 * ElementType                   &lt;type&gt;    base ticks (DIVISIONS = 13440)
 * -------------------------------------------------------------------------
 * SEMIBREVE  / SEMIBREVE_REST   whole     4 × DIVISIONS = 53760
 * MINIM      / MINIM_REST       half      2 × DIVISIONS = 26880
 * CROTCHET   / CROTCHET_REST    quarter   1 × DIVISIONS = 13440
 * QUAVER     / QUAVER_REST      eighth    DIVISIONS / 2  =  6720
 * SEMIQUAVER / SEMIQUAVER_REST  16th      DIVISIONS / 4  =  3360
 * DEMI_SEMIQUAVER / *_REST      32nd      DIVISIONS / 8  =  1680
 * GRACE_QUAVER                  eighth    n/a (no &lt;duration&gt; emitted)
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
    // is why inspecting only the ratios present cannot find it — the old
    // DIVISIONS = 480 already had this defect independently of septuplets.
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
    // Forward map: ElementType → <type> token
    // Both a note and its *_REST counterpart map to the same token; GRACE_QUAVER
    // also maps to eighth (distinguished on read by a <grace> child, not the token).
    // -------------------------------------------------------------------------

    private static final Map<ElementType, String> FORWARD_MAP = Map.ofEntries(
        Map.entry(ElementType.SEMIBREVE,            TYPE_WHOLE),
        Map.entry(ElementType.MINIM,                TYPE_HALF),
        Map.entry(ElementType.CROTCHET,             TYPE_QUARTER),
        Map.entry(ElementType.QUAVER,               TYPE_EIGHTH),
        Map.entry(ElementType.SEMIQUAVER,           TYPE_16TH),
        Map.entry(ElementType.DEMI_SEMIQUAVER,      TYPE_32ND),
        Map.entry(ElementType.SEMIBREVE_REST,       TYPE_WHOLE),
        Map.entry(ElementType.MINIM_REST,           TYPE_HALF),
        Map.entry(ElementType.CROTCHET_REST,        TYPE_QUARTER),
        Map.entry(ElementType.QUAVER_REST,          TYPE_EIGHTH),
        Map.entry(ElementType.SEMIQUAVER_REST,      TYPE_16TH),
        Map.entry(ElementType.DEMI_SEMIQUAVER_REST, TYPE_32ND),
        Map.entry(ElementType.GRACE_QUAVER,         TYPE_EIGHTH)
    );

    // -------------------------------------------------------------------------
    // Reverse maps for reading: token → pitched note and token → rest.
    // GRACE_QUAVER is recovered by the presence of a <grace> child, not a
    // separate token, so it is handled explicitly in forTypeToken().
    // -------------------------------------------------------------------------

    private static final Map<String, ElementType> REVERSE_NOTE_MAP = Map.of(
        TYPE_WHOLE,   ElementType.SEMIBREVE,
        TYPE_HALF,    ElementType.MINIM,
        TYPE_QUARTER, ElementType.CROTCHET,
        TYPE_EIGHTH,  ElementType.QUAVER,
        TYPE_16TH,    ElementType.SEMIQUAVER,
        TYPE_32ND,    ElementType.DEMI_SEMIQUAVER
    );

    private static final Map<String, ElementType> REVERSE_REST_MAP = Map.of(
        TYPE_WHOLE,   ElementType.SEMIBREVE_REST,
        TYPE_HALF,    ElementType.MINIM_REST,
        TYPE_QUARTER, ElementType.CROTCHET_REST,
        TYPE_EIGHTH,  ElementType.QUAVER_REST,
        TYPE_16TH,    ElementType.SEMIQUAVER_REST,
        TYPE_32ND,    ElementType.DEMI_SEMIQUAVER_REST
    );

    // -------------------------------------------------------------------------
    // Base-tick map: ElementType → undotted tick count.
    // GRACE_QUAVER is intentionally absent — grace notes have no <duration>.
    // Note and its *_REST counterpart share the same base-tick value.
    // -------------------------------------------------------------------------

    private static final Map<ElementType, Integer> BASE_TICKS_MAP = Map.ofEntries(
        Map.entry(ElementType.SEMIBREVE,            WHOLE_TICKS),
        Map.entry(ElementType.MINIM,                HALF_TICKS),
        Map.entry(ElementType.CROTCHET,             QUARTER_TICKS),
        Map.entry(ElementType.QUAVER,               EIGHTH_TICKS),
        Map.entry(ElementType.SEMIQUAVER,           SIXTEENTH_TICKS),
        Map.entry(ElementType.DEMI_SEMIQUAVER,      THIRTY_SECOND_TICKS),
        Map.entry(ElementType.SEMIBREVE_REST,       WHOLE_TICKS),
        Map.entry(ElementType.MINIM_REST,           HALF_TICKS),
        Map.entry(ElementType.CROTCHET_REST,        QUARTER_TICKS),
        Map.entry(ElementType.QUAVER_REST,          EIGHTH_TICKS),
        Map.entry(ElementType.SEMIQUAVER_REST,      SIXTEENTH_TICKS),
        Map.entry(ElementType.DEMI_SEMIQUAVER_REST, THIRTY_SECOND_TICKS)
    );

    // Maximum supported dot count (double-dotted).
    static final int MAX_DOT_COUNT = 2;

    private NoteTypeMapping() {}

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Returns the MusicXML {@code <type>} token for the given {@link ElementType},
     * or {@code null} if the type has no entry in the forward map (e.g. a barline).
     */
    @Nullable
    public static String typeToken(ElementType type) {
        return FORWARD_MAP.get(type);
    }

    /**
     * Returns the {@link ElementType} for the given MusicXML {@code <type>} token
     * and note-shape flags, or {@code null} if the token is not recognised.
     *
     * @param typeToken the MusicXML {@code <type>} text content (e.g. {@code "quarter"})
     * @param isRest    {@code true} if a {@code <rest/>} child was present in the {@code <note>}
     * @param isGrace   {@code true} if a {@code <grace>} child was present in the {@code <note>}
     */
    @Nullable
    public static ElementType forTypeToken(String typeToken, boolean isRest, boolean isGrace) {
        if (isGrace) {
            return ElementType.GRACE_QUAVER;
        }

        if (isRest) {
            return REVERSE_REST_MAP.get(typeToken);
        }

        return REVERSE_NOTE_MAP.get(typeToken);
    }

    /**
     * Returns {@code true} if the given {@link ElementType} should emit a
     * {@code <duration>} element in MusicXML output.
     *
     * <p>Grace notes ({@link ElementType#GRACE_QUAVER}) carry no {@code <duration>}
     * because SongScribe assigns them zero playback time and never steals from the
     * host note. All other durational types (notes and rests) emit {@code <duration>}.
     */
    public static boolean hasDuration(ElementType type) {
        return type != ElementType.GRACE_QUAVER;
    }

    /**
     * Returns the {@code <duration>} tick count for the given {@link ElementType}
     * and dot count, as an exact integer.
     *
     * <p>The formula is {@code baseTicks × (2^(d+1) − 1) / 2^d} where {@code d}
     * is {@code dotCount} (0, 1, or 2). {@link #DIVISIONS} is chosen so that this
     * always produces an exact integer: the worst case is a double-dotted 32nd,
     * which yields {@code 1680 × 7 / 4 = 2940} ticks.
     *
     * <p>Do not call this for {@link ElementType#GRACE_QUAVER}; grace notes carry
     * no {@code <duration>}. Use {@link #hasDuration(ElementType)} to guard the call.
     *
     * @param type     the note/rest {@link ElementType} (not {@code GRACE_QUAVER})
     * @param dotCount number of augmentation dots (0, 1, or 2)
     * @return the exact integer tick count
     * @throws IllegalArgumentException if {@code type} has no tick mapping or
     *                                  {@code dotCount} is out of range
     * @throws ArithmeticException      if the result is not an exact integer
     *                                  (indicates {@link #DIVISIONS} is wrong)
     */
    public static int ticks(ElementType type, int dotCount) {
        if (dotCount < 0 || dotCount > MAX_DOT_COUNT) {
            throw new IllegalArgumentException("dotCount must be 0–" + MAX_DOT_COUNT + ", got " + dotCount);
        }

        var baseTicks = BASE_TICKS_MAP.get(type);

        if (baseTicks == null) {
            throw new IllegalArgumentException("No tick mapping for ElementType: " + type);
        }

        // Dot augmentation: base × (2^(d+1) − 1) / 2^d
        var numerator   = (1 << (dotCount + 1)) - 1;
        var denominator = 1 << dotCount;
        var product     = baseTicks * numerator;

        if (product % denominator != 0) {
            throw new ArithmeticException(
                "Tick count is not an integer for " + type + " dotCount=" + dotCount
                + " (DIVISIONS=" + DIVISIONS + " is wrong)"
            );
        }

        return product / denominator;
    }
}
