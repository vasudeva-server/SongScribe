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

import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import songscribe.music.Line;
import songscribe.music.Note;
import songscribe.ui.component.Score;

/**
 * Facade for measurement operations used by layout and adjustment components.
 * <p>
 * This service provides clean access to measurement methods without requiring
 * direct access to BoundsCalculator or old Renderer. It delegates all operations
 * to BoundsCalculator.
 * <p>
 * Created as part of Phase 9.6 to enable removal of the legacy Renderer system.
 */
public class MeasurementService {

    private final Score score;
    private final BoundsCalculator boundsCalculator;
    private final FontBoundsProvider fontBoundsProvider;

    /**
     * Creates a new MeasurementService.
     *
     * @param score              The score component for layout context
     * @param fontBoundsProvider Provider for font-specific glyph bounds
     */
    public MeasurementService(
        @NotNull Score score,
        @NotNull FontBoundsProvider fontBoundsProvider
    ) {
        this.score = score;
        this.fontBoundsProvider = fontBoundsProvider;
        this.boundsCalculator = new BoundsCalculator(score, fontBoundsProvider);
    }

    /**
     * Returns the distance (pixels) from the top staff line to the highest element
     * in the specified line, including articulations.
     *
     * @param g2        Graphics context for font metrics
     * @param line      The musical line to measure
     * @param lineIndex The staff line index
     * @return Distance above top staff line in pixels, 0 if nothing extends above
     */
    public int getLineDistanceAboveTopLine(
        @NotNull Graphics2D g2,
        @NotNull Line line,
        int lineIndex
    ) {
        return boundsCalculator.getLineDistanceAboveTopLine(g2, line, lineIndex);
    }

    /**
     * Returns the distance (pixels) from the bottom staff line to the lowest element
     * in the specified line, including articulations.
     *
     * @param g2        Graphics context for font metrics
     * @param line      The musical line to measure
     * @param lineIndex The staff line index
     * @return Distance below bottom staff line in pixels, 0 if nothing extends below
     */
    public int getLineDistanceBelowBottomLine(
        @NotNull Graphics2D g2,
        @NotNull Line line,
        int lineIndex
    ) {
        return boundsCalculator.getLineDistanceBelowBottomLine(g2, line, lineIndex);
    }

    /**
     * Returns the effective Y position for the tempo marking on this line,
     * accounting for notes and articulations that extend above the staff.
     *
     * @param g2        Graphics context for font metrics
     * @param line      The musical line
     * @param lineIndex The staff line index
     * @return The effective Y position for the tempo marking (relative to middle line)
     */
    public int getEffectiveTempoChangeYPos(
        @NotNull Graphics2D g2,
        @NotNull Line line,
        int lineIndex
    ) {
        return boundsCalculator.getEffectiveTempoChangeYPos(g2, line, lineIndex);
    }

    /**
     * Returns the bounding box for a complete line including all elements.
     *
     * @param g2        Graphics context for font metrics
     * @param line      The musical line to measure
     * @param lineIndex The staff line index
     * @return Rectangle2D in absolute coordinates (page-relative)
     */
    @NotNull
    public Rectangle2D getLineBounds(
        @NotNull Graphics2D g2,
        @NotNull Line line,
        int lineIndex
    ) {
        return boundsCalculator.getLineBounds(g2, line, lineIndex);
    }

    /**
     * Returns the complete bounding box for a note including all visual elements.
     *
     * @param g2        Graphics context for font metrics
     * @param note      The note to measure
     * @param lineIndex The staff line index
     * @return Rectangle2D in absolute coordinates (page-relative)
     */
    @NotNull
    public Rectangle2D getNoteBounds(
        @NotNull Graphics2D g2,
        @NotNull Note note,
        int lineIndex
    ) {
        return boundsCalculator.getNoteBounds(g2, note, lineIndex);
    }

    /**
     * Returns the bounding box for the tempo marking including note and text.
     *
     * @param g2        Graphics context for font metrics
     * @param line      The musical line
     * @param lineIndex The staff line index
     * @return Rectangle2D in absolute coordinates, or null if no tempo
     */
    @Nullable
    public Rectangle2D getTempoBounds(
        @NotNull Graphics2D g2,
        @NotNull Line line,
        int lineIndex
    ) {
        return boundsCalculator.getTempoBounds(g2, line, lineIndex);
    }

    /**
     * Returns the Y position for an annotation.
     *
     * @param lineIndex The staff line index
     * @param note      The note with the annotation
     * @return Y coordinate in absolute coordinates
     */
    public int getAnnotationYPos(int lineIndex, @NotNull Note note) {
        return score.getNoteYPos(0, lineIndex) + note.getAnnotation().getYPos();
    }

    /**
     * Returns the X position for an annotation, accounting for alignment.
     *
     * @param g2   Graphics context for font metrics
     * @param note The note with the annotation
     * @return X coordinate in absolute coordinates
     */
    public double getAnnotationXPos(@NotNull Graphics2D g2, @NotNull Note note) {
        var annotation = note.getAnnotation();
        var xPos = note.getXPos() + (fontBoundsProvider.getCrotchetWidth() / 2);
        var font = score.getComposition().getAnnotationFont();

        if (annotation.getXAlignment() == Component.CENTER_ALIGNMENT) {
            xPos -= (double) g2.getFontMetrics(font).stringWidth(annotation.getAnnotation()) / 2;
        } else if (annotation.getXAlignment() == Component.RIGHT_ALIGNMENT) {
            xPos -= g2.getFontMetrics(font).stringWidth(annotation.getAnnotation());
        }

        return xPos;
    }
}
