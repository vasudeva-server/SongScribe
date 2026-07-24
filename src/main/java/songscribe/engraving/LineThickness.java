package songscribe.engraving;

/**
 * Resolved element thicknesses and beam geometry using LilyPond-informed
 * multiplier ratios, all in staff spaces.
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
public final class LineThickness {
    // LilyPond multipliers relative to base staff-line thickness
    static final double STEM_MULTIPLIER = 1.3;
    private static final double LEDGER_LINE_MULTIPLIER = 2.0;
    private static final double THIN_BARLINE_MULTIPLIER = 1.9;
    private static final double THICK_BARLINE_MULTIPLIER = 6.0;
    private static final double BARLINE_SEPARATION_MULTIPLIER = 3.0;
    private static final double HAIRPIN_MULTIPLIER = 1.0;
    static final double VOLTA_BRACKET_MULTIPLIER = 1.61;
    static final double TUPLET_BRACKET_MULTIPLIER = 1.61;
    private static final double GLISSANDO_MULTIPLIER = 1.75;

    /**
     * LilyPond base staff-line thickness in staff spaces. SMuFL Bravura uses 0.13,
     * but LilyPond uses 0.1 — applying LilyPond multipliers to a LilyPond base
     * produces the correct absolute thicknesses.
     */
    static final double LILYPOND_BASE_THICKNESS_SS = 0.1;

    public static final double STAFF_LINE_SS = LILYPOND_BASE_THICKNESS_SS;
    public static final double STEM_SS = LILYPOND_BASE_THICKNESS_SS * STEM_MULTIPLIER;
    public static final double LEDGER_LINE_SS = LILYPOND_BASE_THICKNESS_SS * LEDGER_LINE_MULTIPLIER;
    public static final double THIN_BARLINE_SS = LILYPOND_BASE_THICKNESS_SS * THIN_BARLINE_MULTIPLIER;
    public static final double THICK_BARLINE_SS = LILYPOND_BASE_THICKNESS_SS * THICK_BARLINE_MULTIPLIER;
    public static final double HAIRPIN_SS = LILYPOND_BASE_THICKNESS_SS * HAIRPIN_MULTIPLIER;
    public static final double VOLTA_BRACKET_SS = LILYPOND_BASE_THICKNESS_SS * VOLTA_BRACKET_MULTIPLIER;
    public static final double TUPLET_BRACKET_SS = LILYPOND_BASE_THICKNESS_SS * TUPLET_BRACKET_MULTIPLIER;
    public static final double GLISSANDO_SS = LILYPOND_BASE_THICKNESS_SS * GLISSANDO_MULTIPLIER;

    /**
     * Beam thickness in staff spaces, from LilyPond's {@code beam-thickness}
     * (define-grobs.scm). Bravura's SMuFL {@code engravingDefaults} recommend 0.5,
     * but beams are drawn by SongScribe rather than taken from the font, so
     * following LilyPond here matches its engraving — the same trade-off already
     * made for {@link #LILYPOND_BASE_THICKNESS_SS} over Bravura's 0.13 staff line.
     */
    public static final double BEAM_THICKNESS_SS = 0.48;

    /**
     * A staff space, expressed in staff spaces. Named so that ported LilyPond
     * formulas below read the way the originals do, in which the staff space is an
     * explicit term rather than an anonymous factor.
     */
    private static final double STAFF_SPACE_SS = 1.0;

    /**
     * Center-to-center distance between adjacent beams in a stack, in staff
     * spaces. Ported from LilyPond's {@code Beam::get_beam_translation}
     * (beam.cc), whose fewer-than-four-beams branch is
     * {@code (2 * staffSpace + staffLine - beamThickness) / 2}. SongScribe never
     * stacks more than three beams (32nd notes), so the four-or-more branch is
     * unreachable and is not ported.
     *
     * <p>This is wider than beam thickness plus Bravura's {@code beamSpacing} of
     * 0.25: LilyPond leaves a larger gap between stacked beams.
     */
    public static final double BEAM_TRANSLATION_SS =
        (2 * STAFF_SPACE_SS + STAFF_LINE_SS - BEAM_THICKNESS_SS) / 2;

    /**
     * Center-to-center distance between adjacent beams in a stack that has been
     * thickened by {@code thickeningSs}, in staff spaces.
     *
     * <p>Beams grow downward from their center line, so thickening widens the gap
     * between stacked beams by the same amount it widens each beam. Both the
     * renderer that draws the inner beams and the one that stops a French-beamed
     * stem on one must step by this exact distance, or the stem tip lands off the
     * beam it is supposed to touch — hence the single definition here.
     */
    public static double beamTranslationSs(double thickeningSs) {
        return BEAM_TRANSLATION_SS + thickeningSs;
    }

    /**
     * Diameter of the rounded corner applied to a drawn beam, in staff spaces.
     * LilyPond's {@code blot-diameter} is 0.4 pt (scm/paper.scm) against a default
     * staff space of 5 pt, giving 0.08 staff spaces. {@code Lookup::beam} insets
     * the beam polygon by half this and strokes it with a round pen of this width,
     * so the overall beam extent is unchanged and only the corners are rounded.
     */
    public static final double BEAM_BLOT_DIAMETER_SS = 0.08;

    /**
     * The barline separation (gap between adjacent barlines) in staff spaces.
     */
    public static final double BARLINE_SEPARATION_SS = STAFF_LINE_SS * BARLINE_SEPARATION_MULTIPLIER;

    /**
     * The X offset of the thin barline center within a REPEAT_RIGHT barline,
     * relative to the element's layout X position.
     */
    public static final double REPEAT_RIGHT_THIN_BARLINE_CENTER_X_SS =
            SMuFLConstants.REPEAT_DOTS_ADVANCE_WIDTH_SS + BARLINE_SEPARATION_SS + THIN_BARLINE_SS / 2;

    /**
     * The X offset just past the thick barline of a REPEAT_RIGHT barline,
     * relative to the element's layout X position.
     */
    public static final double REPEAT_RIGHT_AFTER_THICK_X_SS = SMuFLConstants.REPEAT_DOTS_ADVANCE_WIDTH_SS
            + BARLINE_SEPARATION_SS + THIN_BARLINE_SS + BARLINE_SEPARATION_SS + THICK_BARLINE_SS;

    private LineThickness() {}
}
