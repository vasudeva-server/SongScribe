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

package songscribe.smufl;

/**
 * Engraving defaults from SMuFL metadata, all values in staff spaces.
 * These replace hardcoded stroke/thickness constants throughout the renderers.
 */
public record EngravingDefaults(
        double staffLineThickness,
        double stemThickness,
        double beamThickness,
        double beamSpacing,
        double barlineSeparation,
        double thinBarlineThickness,
        double thickBarlineThickness,
        double repeatBarlineDotSeparation,
        double repeatEndingLineThickness,
        double legerLineThickness,
        double legerLineExtension,
        double slurEndpointThickness,
        double slurMidpointThickness,
        double tieEndpointThickness,
        double tieMidpointThickness,
        double hairpinThickness,
        double tupletBracketThickness
) {}
