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

package songscribe.ui.layout;

import module java.desktop;

import songscribe.model.Line;

/**
 * Detects collisions between layout elements and calculates extent bounds.
 * <p>
 * Collision detection uses margin bounds (not content bounds) to ensure
 * proper spacing between elements.
 */
public final class CollisionDetector {

    /**
     * Padding applied to all collision regions (both sub-regions and full bounds)
     * during query and set phases, expanding each region on all sides.
     */
    public static final double COLLISION_PADDING_SS = 0.25;  // 2px

    private CollisionDetector() {
        // Prevent instantiation
    }

    /**
     * Calculates the maximum extent of notes above and below the staff, in staff-space.
     * <p>
     * This includes notes, their attachments, and range elements. The result
     * helps determine the LineComponent's preferred height.
     *
     * @param line           The line containing notes
     * @param staffMiddleYSs Y position of the staff middle line, in staff-space
     * @return Rectangle with: y=minY above staff, height=total extent (all in staff-space)
     */
    public static Rectangle2D calculateNoteExtent(
        Line line,
        double staffMiddleYSs
    ) {
        double minY = 0;  // Above staff (negative from middle)
        double maxY = 0;  // Below staff (positive from middle)

        // Check all notes
        for (var note : line.getElements()) {
            var noteBounds = note.getMarginBounds();
            var noteTop = noteBounds.getMinY() - staffMiddleYSs;
            var noteBottom = noteBounds.getMaxY() - staffMiddleYSs;

            minY = Math.min(minY, noteTop);
            maxY = Math.max(maxY, noteBottom);

            // Include attachments
            for (var attachment : note.getAttachments()) {
                var attBounds = attachment.getMarginBounds();
                var attTop = attBounds.getMinY() - staffMiddleYSs;
                var attBottom = attBounds.getMaxY() - staffMiddleYSs;

                minY = Math.min(minY, attTop);
                maxY = Math.max(maxY, attBottom);
            }

            // Include articulations
            for (var articulation : note.getArticulations()) {
                var artBounds = articulation.getMarginBounds();
                var artTop = artBounds.getMinY() - staffMiddleYSs;
                var artBottom = artBounds.getMaxY() - staffMiddleYSs;

                minY = Math.min(minY, artTop);
                maxY = Math.max(maxY, artBottom);
            }
        }

        // Include range elements
        for (var range : line.getRangeElements()) {
            var rangeBounds = range.getMarginBounds();
            var rangeTop = rangeBounds.getMinY() - staffMiddleYSs;
            var rangeBottom = rangeBounds.getMaxY() - staffMiddleYSs;

            minY = Math.min(minY, rangeTop);
            maxY = Math.max(maxY, rangeBottom);
        }

        // Return rectangle representing the extent
        // x and width are 0 since we only care about vertical extent
        return new Rectangle2D.Double(0, minY, 0, maxY - minY);
    }

}
