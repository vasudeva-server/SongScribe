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
 * What an articulation draws. Sealed so that a renderer switching on it draws every kind.
 */
public sealed interface ArticulationInk {

    /**
     * For a glyph pair it is the above-staff glyph's box, which Bravura's glyphs share on both sides.
     */
    BBox boundsSs();

    record Glyphs(SMuFLGlyph above, SMuFLGlyph below) implements ArticulationInk {

        public SMuFLGlyph on(Direction side) {
            return side.isUp() ? above : below;
        }

        @Override
        public BBox boundsSs() {
            return SMuFLMetadata.bboxSs(above);
        }
    }

    /** In staff spaces with Y down. */
    record Outline(Shape shape) implements ArticulationInk {

        @Override
        public BBox boundsSs() {
            var bounds = shape.getBounds2D();

            return new BBox(bounds.getMinX(), bounds.getMinY(), bounds.getMaxX(), bounds.getMaxY());
        }
    }
}
