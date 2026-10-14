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
     * From LilyPond's {@code beam-thickness}, not the font's recommended 0.5, because beams
     * are drawn by SongScribe rather than taken from the font.
     */
    public static final double BEAM_THICKNESS_SS = 0.48;

    private static final double STAFF_SPACE_SS = 1.0;

    /**
     * From LilyPond's {@code Beam::get_beam_translation}, fewer-than-four-beams branch only:
     * SongScribe never stacks more than three beams.
     */
    public static final double BEAM_TRANSLATION_SS =
        (2 * STAFF_SPACE_SS + EngravingConstants.STAFF_LINE_THICKNESS_SS - BEAM_THICKNESS_SS) / 2;

    /**
     * From LilyPond's {@code blot-diameter}.
     */
    public static final double BEAM_BLOT_DIAMETER_SS = 0.08;

    private BeamMetrics() {}

    /**
     * The beam renderer and the French-beam stem renderer must step by this same distance, or
     * the stem tip lands off its beam.
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
     * @param beamCount at least 1
     */
    public static double beamStackHeightSs(int beamCount, double thickeningSs) {
        return BEAM_THICKNESS_SS + thickeningSs
            + (beamCount - 1) * beamTranslationSs(thickeningSs);
    }

    /**
     * An estimate: the beam's direction is settled before column positions exist.
     *
     * @param stackCount at least 1
     */
    public static double upwardStackBottomYSs(StaffPosition highestHead, int stackCount) {
        return highestHead.toSs() - StemMetrics.STEM_LENGTH_SS + beamStackHeightSs(stackCount);
    }
}
