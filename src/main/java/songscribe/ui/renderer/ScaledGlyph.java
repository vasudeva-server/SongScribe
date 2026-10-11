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

package songscribe.ui.renderer;

import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;

/**
 * A SMuFL glyph drawn at a uniform scale of its design size, in both X and Y.
 *
 * @param glyph the glyph
 * @param scale the factor applied to the glyph's design size; 1 draws it at design size
 */
record ScaledGlyph(SMuFLGlyph glyph, double scale) {

    private static final double DESIGN_SCALE = 1d;

    static ScaledGlyph atDesignSize(SMuFLGlyph glyph) {
        return new ScaledGlyph(glyph, DESIGN_SCALE);
    }

    /**
     * The distance in staff spaces from one glyph's pen origin to the next in a tiled run, after
     * scaling.
     */
    double advanceSs() {
        return SMuFLMetadata.advanceWidthSs(glyph) * scale;
    }

    /**
     * The Y of the glyph's ink top relative to its baseline, in staff spaces, after scaling.
     * Negative when the ink rises above the baseline.
     */
    double inkTopYSs() {
        return SMuFLMetadata.bboxSs(glyph).topSs() * scale;
    }
}
