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

import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;

/**
 * One stroke a barline or repeat sign is built from: a thin line, a thick line, or a
 * repeat-dots pair.
 */
public enum BarStroke {
    THIN,
    THICK,
    DOTS;

    private static final double THIN_MULTIPLIER = 1.9;
    private static final double THICK_MULTIPLIER = 6.0;
    private static final double SEPARATION_MULTIPLIER = 3.0;

    public static final double THIN_SS = EngravingConstants.LILYPOND_BASE_THICKNESS_SS * THIN_MULTIPLIER;
    public static final double THICK_SS = EngravingConstants.LILYPOND_BASE_THICKNESS_SS * THICK_MULTIPLIER;

    public static final double SEPARATION_SS =
        EngravingConstants.LILYPOND_BASE_THICKNESS_SS * SEPARATION_MULTIPLIER;

    /**
     * Excludes the {@link #SEPARATION_SS} that stands between it and the stroke beside it.
     * Always positive.
     */
    public double widthSs() {
        return switch (this) {
            case THIN -> THIN_SS;
            case THICK -> THICK_SS;
            case DOTS -> SMuFLMetadata.advanceWidthSs(SMuFLGlyph.REPEAT_DOTS);
        };
    }
}
