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

package songscribe.dom;

import java.awt.Shape;

import songscribe.smufl.BBox;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;

/**
 * What an articulation draws: a pair of font glyphs, one for each side of the note, or one
 * outline drawn the same whichever side it sits on. Sealed so that a renderer switching on it
 * draws every kind there is, and so that no caller can ask a kind for a glyph it does not have.
 *
 * <p>Every kind measures itself, so the room an articulation reserves when stacked is derived
 * from the ink it draws rather than stated beside it.
 */
public sealed interface ArticulationInk {

    /**
     * Returns the box the ink occupies, in staff spaces, measured from the origin it is drawn
     * from. For a glyph pair it is the above-staff glyph's box: Bravura's staccato dot has the
     * same box on both sides.
     *
     * @return the ink's bounds
     */
    BBox boundsSs();

    /**
     * Ink drawn from the music font, with a glyph for each side of the note.
     *
     * @param above the glyph drawn above the note
     * @param below the glyph drawn below the note
     */
    record Glyphs(SMuFLGlyph above, SMuFLGlyph below) implements ArticulationInk {

        /**
         * Returns the glyph for one side of the note.
         *
         * @param side {@link Direction#UP} for above the note, {@link Direction#DOWN} for below
         * @return {@link #above()} or {@link #below()}
         */
        public SMuFLGlyph on(Direction side) {
            return side.isUp() ? above : below;
        }

        @Override
        public BBox boundsSs() {
            return SMuFLMetadata.bboxSs(above);
        }
    }

    /**
     * Ink drawn as a filled outline, in staff spaces with Y down, the same on either side of the
     * note.
     *
     * @param shape the outline, positioned relative to the origin it is drawn from
     */
    record Outline(Shape shape) implements ArticulationInk {

        @Override
        public BBox boundsSs() {
            var bounds = shape.getBounds2D();

            return new BBox(bounds.getMinX(), bounds.getMinY(), bounds.getMaxX(), bounds.getMaxY());
        }
    }
}
