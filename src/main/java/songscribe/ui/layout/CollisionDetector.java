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

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import songscribe.music.Line;
import songscribe.ui.layout2.ScaleContext;

/**
 * Detects collisions between layout elements and calculates extent bounds.
 * <p>
 * Collision detection uses margin bounds (not content bounds) to ensure
 * proper spacing between elements.
 */
public final class CollisionDetector {

    private CollisionDetector() {
        // Prevent instantiation
    }

    /**
     * Checks if two elements collide based on their margin bounds.
     *
     * @param element1 First element
     * @param element2 Second element
     * @return true if the elements' margin bounds overlap
     */
    public static boolean checkCollision(
        LineElement element1,
        LineElement element2
    ) {
        var bounds1 = element1.getMarginBounds();
        var bounds2 = element2.getMarginBounds();

        return bounds1.intersects(bounds2);
    }

    /**
     * Finds all attachments on a line that collide with the attribution.
     *
     * @param line        The line to check
     * @param attribution The attribution element
     * @return List of attachments that collide with attribution
     */
    public static List<Attachment> findAttributionCollisions(
        Line line,
        Attribution attribution
    ) {
        var collisions = new ArrayList<Attachment>();

        // Check all notes for attachments (all are now positioned above the staff)
        for (var note : line.getElements()) {
            for (var attachment : note.getAttachments()) {
                if (checkCollision(attachment, attribution)) {
                    collisions.add(attachment);
                }
            }
        }

        return collisions;
    }

    /**
     * Calculates the vertical offset needed to avoid attribution collisions.
     * <p>
     * When attribution collides with above-staff attachments, the staff needs
     * to move down by this offset to avoid overlap.
     *
     * @param line        The line to check
     * @param attribution The attribution element
     * @return Vertical offset in pixels (0 if no collision)
     */
    public static double calculateAttributionOffset(
        Line line,
        Attribution attribution
    ) {
        var collisions = findAttributionCollisions(line, attribution);

        if (collisions.isEmpty()) {
            return 0;
        }

        // Find the lowest point of any colliding attachment
        double maxBottom = collisions.stream()
            .mapToDouble(a -> a.getMarginBounds().getMaxY())
            .max()
            .orElse(0);

        // Attribution should be below this point
        double attributionTop = attribution.getMarginBounds().getMinY();

        return Math.max(0, maxBottom - attributionTop);
    }

    /**
     * Calculates the maximum extent of notes above and below the staff.
     * <p>
     * This includes notes, their attachments, and range elements. The result
     * helps determine the LineComponent's preferred height.
     *
     * @param line         The line containing notes
     * @param staffMiddleY Y position of the staff middle line
     * @return Rectangle with: y=minY above staff, height=total extent
     */
    public static Rectangle2D calculateNoteExtent(
        Line line,
        double staffMiddleY
    ) {
        double minY = 0;  // Above staff (negative from middle)
        double maxY = 0;  // Below staff (positive from middle)

        // Check all notes
        for (var note : line.getElements()) {
            var noteBounds = note.getMarginBounds();
            double noteTop = noteBounds.getMinY() - staffMiddleY;
            double noteBottom = noteBounds.getMaxY() - staffMiddleY;

            minY = Math.min(minY, noteTop);
            maxY = Math.max(maxY, noteBottom);

            // Include attachments
            for (var attachment : note.getAttachments()) {
                var attBounds = attachment.getMarginBounds();
                double attTop = attBounds.getMinY() - staffMiddleY;
                double attBottom = attBounds.getMaxY() - staffMiddleY;

                minY = Math.min(minY, attTop);
                maxY = Math.max(maxY, attBottom);
            }

            // Include articulations
            for (var articulation : note.getArticulations()) {
                var artBounds = articulation.getMarginBounds();
                double artTop = artBounds.getMinY() - staffMiddleY;
                double artBottom = artBounds.getMaxY() - staffMiddleY;

                minY = Math.min(minY, artTop);
                maxY = Math.max(maxY, artBottom);
            }
        }

        // Include range elements
        for (var range : line.getRangeElements()) {
            var rangeBounds = range.getMarginBounds();
            double rangeTop = rangeBounds.getMinY() - staffMiddleY;
            double rangeBottom = rangeBounds.getMaxY() - staffMiddleY;

            minY = Math.min(minY, rangeTop);
            maxY = Math.max(maxY, rangeBottom);
        }

        // Return rectangle representing the extent
        // x and width are 0 since we only care about vertical extent
        return new Rectangle2D.Double(0, minY, 0, maxY - minY);
    }

    /**
     * Calculates the total vertical space needed for a line.
     * <p>
     * Includes staff height plus space above and below for notes, attachments,
     * and other elements.
     *
     * @param line         The line to measure
     * @param staffMiddleY Y position of the staff middle line
     * @return Total height in pixels
     */
    public static double calculateLineHeight(
        Line line,
        double staffMiddleY
    ) {
        // Start with staff height
        double height = ScaleContext.getInstance().toPixels(LayoutStylesheet.STAFF_HEIGHT_SS);

        // Calculate extent of all elements above and below staff
        var extent = calculateNoteExtent(line, staffMiddleY);

        // Add space above staff
        double spaceAbove = Math.abs(extent.getMinY());
        height += spaceAbove;

        // Add space below staff (accounting for staff position relative to middle)
        double spaceBelow = extent.getMaxY() - (ScaleContext.getInstance().toPixels(LayoutStylesheet.STAFF_HEIGHT_SS) / 2.0);
        height += Math.max(0, spaceBelow);

        return height;
    }

    /**
     * Checks if a point is within any element on the line.
     *
     * @param line The line to check
     * @param x    X coordinate
     * @param y    Y coordinate
     * @return The element containing the point, or null if none
     */
    public static @Nullable LineElement findElementAt(
        Line line,
        double x,
        double y
    ) {
        // Check notes first (most common hit target)
        for (var note : line.getElements()) {
            if (note.containsPoint(x, y)) {
                // Check children (attachments, articulations) first
                for (var attachment : note.getAttachments()) {
                    if (attachment.containsPoint(x, y)) {
                        return attachment;
                    }
                }

                for (var articulation : note.getArticulations()) {
                    if (articulation.containsPoint(x, y)) {
                        return articulation;
                    }
                }

                return note;
            }
        }

        // Check range elements
        for (var range : line.getRangeElements()) {
            if (range.containsPoint(x, y)) {
                return range;
            }
        }

        return null;
    }
}
