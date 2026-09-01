/*
    SongScribe song notation program
    Copyright (C) Sri Chinmoy Centres International

    This file is part of SongScribe.

    SongScribe is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 3 of the License, or
    (at your option) any later version.

    SongScribe is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package songscribe.engraving;

/**
 * Beam thickness and stacking geometry, in staff spaces.
 */
public final class BeamMetrics {
    /**
     * Beam thickness in staff spaces, from LilyPond's {@code beam-thickness}
     * (define-grobs.scm). Bravura's SMuFL {@code engravingDefaults} recommend 0.5,
     * but beams are drawn by SongScribe rather than taken from the font, so
     * following LilyPond here matches its engraving — the same trade-off already
     * made for {@link EngravingConstants#LILYPOND_BASE_THICKNESS_SS} over Bravura's 0.13
     * staff line.
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
        (2 * STAFF_SPACE_SS + EngravingConstants.STAFF_LINE_THICKNESS_SS - BEAM_THICKNESS_SS) / 2;

    /**
     * Diameter of the rounded corner applied to a drawn beam, in staff spaces.
     * LilyPond's {@code blot-diameter} is 0.4 pt (scm/paper.scm) against a default
     * staff space of 5 pt, giving 0.08 staff spaces. {@code Lookup::beam} insets
     * the beam polygon by half this and strokes it with a round pen of this width,
     * so the overall beam extent is unchanged and only the corners are rounded.
     */
    public static final double BEAM_BLOT_DIAMETER_SS = 0.08;

    private BeamMetrics() {}

    /**
     * Center-to-center distance between adjacent beams in a stack that has been
     * thickened by {@code thickeningSs}, in staff spaces.
     *
     * <p>Beams grow downward from their center line, so thickening widens the gap
     * between stacked beams by the same amount it widens each beam. Both the
     * renderer that draws the inner beams and the one that stops a French-beamed
     * stem on one must step by this exact distance, or the stem tip lands off the
     * beam it is supposed to touch — hence the single definition here.
     *
     * @param thickeningSs how much each beam has been thickened, in staff spaces
     * @return the center-to-center distance between adjacent beams in a stack
     *     thickened by {@code thickeningSs}, in staff spaces
     */
    public static double beamTranslationSs(double thickeningSs) {
        return BEAM_TRANSLATION_SS + thickeningSs;
    }

    /**
     * Total vertical extent of a stack of {@code beamCount} unthickened beams, in staff spaces,
     * measured from the outer edge of the first beam to the inner edge of the last — the whole
     * depth the stack occupies.
     *
     * <p>{@code beamCount} is a count, not a level: a lone eighth-note beam is 1 and occupies
     * exactly {@link #BEAM_THICKNESS_SS}. Every further beam adds one
     * {@link #BEAM_TRANSLATION_SS} step, since the stack grows by center-to-center distance
     * rather than by thickness. LilyPond calls this quantity {@code height_of_beams}.
     *
     * @param beamCount how many beams stand in the stack, at least 1
     * @return the extent in staff spaces
     */
    public static double beamStackHeightSs(int beamCount) {
        return beamStackHeightSs(beamCount, 0);
    }

    /**
     * Total vertical extent of a stack of {@code beamCount} beams each thickened by
     * {@code thickeningSs}, in staff spaces, measured from the outer edge of the first beam to
     * the inner edge of the last.
     *
     * <p>Thickening widens each beam and, by {@link #beamTranslationSs}, the step between them,
     * so it enters the total twice over: once for the depth of the last beam and once per step
     * before it. This method and the hit region it is computed for are one definition of the
     * stack's depth; the renderer reaches the same total by stepping {@link #beamTranslationSs}
     * once per beam rather than calling this method, so the two share that per-beam distance
     * rather than a shared call site.
     *
     * @param beamCount    how many beams stand in the stack, at least 1
     * @param thickeningSs how much each beam has been thickened by, in staff spaces
     * @return the extent in staff spaces
     */
    public static double beamStackHeightSs(int beamCount, double thickeningSs) {
        return BEAM_THICKNESS_SS + thickeningSs
            + (beamCount - 1) * beamTranslationSs(thickeningSs);
    }
}
