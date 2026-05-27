package songscribe.layout;

import songscribe.smufl.Engraving;

/**
 * Resolved element thicknesses using LilyPond-informed multiplier ratios,
 * all in staff spaces.
 *
 * <p>LilyPond derives all element thicknesses from a single base staff-line
 * thickness using fixed multiplier ratios. The same ratios are used for both
 * screen and print output — screen pixel crispness is handled separately by
 * device-pixel snapping (the equivalent of LilyPond's {@code setstrokeadjust}).
 *
 * <p>At screen resolution (~72 dpi effective), raw SMuFL values collapse to the
 * same 1–2 device pixels, destroying the intended visual hierarchy. The multiplier
 * ratios preserve distinct visual weight across elements at any resolution.
 */
public record LineThickness(
        double staffLineSs,
        double stemSs,
        double ledgerLineSs,
        double thinBarlineSs,
        double thickBarlineSs,
        double hairpinSs,
        double voltaBracketSs,
        double tupletBracketSs
) {
    // LilyPond multipliers relative to base staff-line thickness
    static final double STEM_MULTIPLIER = 1.3;
    private static final double LEDGER_LINE_MULTIPLIER = 2.0;
    private static final double THIN_BARLINE_MULTIPLIER = 1.9;
    private static final double THICK_BARLINE_MULTIPLIER = 6.0;
    private static final double BARLINE_SEPARATION_MULTIPLIER = 3.0;
    private static final double HAIRPIN_MULTIPLIER = 1.0;
    static final double VOLTA_BRACKET_MULTIPLIER = 1.61;
    static final double TUPLET_BRACKET_MULTIPLIER = 1.61;

    /**
     * LilyPond base staff-line thickness in staff spaces. SMuFL Bravura uses 0.13,
     * but LilyPond uses 0.1 — applying LilyPond multipliers to a LilyPond base
     * produces the correct absolute thicknesses.
     */
    static final double LILYPOND_BASE_THICKNESS_SS = 0.1;

    private static final LineThickness INSTANCE;

    static {
        var base = LILYPOND_BASE_THICKNESS_SS;
        INSTANCE = new LineThickness(
                base,
                base * STEM_MULTIPLIER,
                base * LEDGER_LINE_MULTIPLIER,
                base * THIN_BARLINE_MULTIPLIER,
                base * THICK_BARLINE_MULTIPLIER,
                base * HAIRPIN_MULTIPLIER,
                base * VOLTA_BRACKET_MULTIPLIER,
                base * TUPLET_BRACKET_MULTIPLIER
        );
    }

    /**
     * Returns the barline separation (gap between adjacent barlines) in staff spaces.
     */
    public double barlineSeparationSs() {
        return staffLineSs * BARLINE_SEPARATION_MULTIPLIER;
    }

    /**
     * Returns the X offset of the thin barline center within a REPEAT_RIGHT barline,
     * relative to the element's layout X position.
     */
    public double repeatRightThinBarlineCenterXSs() {
        return Engraving.REPEAT_DOTS_ADVANCE_WIDTH_SS + barlineSeparationSs() + thinBarlineSs / 2;
    }

    /**
     * Returns the X offset just past the thick barline of a REPEAT_RIGHT barline,
     * relative to the element's layout X position.
     */
    public double repeatRightAfterThickXSs() {
        var sep = barlineSeparationSs();
        return Engraving.REPEAT_DOTS_ADVANCE_WIDTH_SS + sep + thinBarlineSs + sep + thickBarlineSs;
    }

    /**
     * Returns the singleton {@code LineThickness} with LilyPond multiplier-derived values.
     */
    public static LineThickness getInstance() {
        return INSTANCE;
    }
}
