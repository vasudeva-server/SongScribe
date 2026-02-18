/*
 * This file is part of SongScribe.
 *
 * SongScribe is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * SongScribe is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with SongScribe.
 * If not, see <https://www.gnu.org/licenses/>.
 *
 * Copyright (C) 2024 Aparajita
 */

package songscribe.music;

import java.util.logging.Logger;

import org.jetbrains.annotations.NotNull;

import songscribe.ui.component.Score;
import songscribe.ui.layout.LayoutStylesheet;

/**
 * Calculates beam positioning and note lengthenings for proper visual rendering.
 *
 * This class encapsulates the mathematical calculations for determining how much each
 * note in a beamed group needs to be extended to align with the beam angle.
 */
public class BeamCalculator {

    private static final Logger LOG = Logger.getLogger(BeamCalculator.class.getName());

    private static final double SLOPE_SCALE = 0.6;
    private static final double MAX_DEVIATION_SS = 0.75;
    private static final double PITCH_SPAN_THRESHOLD = 8;

    // Gould/Ross section 4.2: minimum stem length is 3.5 staff spaces
    private static final double MIN_STEM_SS = 3.5;
    private static final double MIN_STEM_PX = MIN_STEM_SS * LayoutStylesheet.STAFF_SPACE;
    private static final double SLOPE_REDUCTION_FACTOR = 0.85;
    private static final int MAX_SLOPE_ITERATIONS = 20;

    private BeamCalculator() {
        // Utility class
    }

    /**
     * Calculates beam lengthenings for all notes in a beamed group.
     *
     * @param xIndex                Index of a note in the beamed group
     * @param line                  The line containing the notes
     * @param automaticStemDirection Whether to determine stem direction automatically
     */
    public static void calculateLengthenings(
        int xIndex,
        @NotNull Line line,
        boolean automaticStemDirection
    ) {
        // Determine start index, end index
        var interval = line.getBeamings().findInterval(xIndex);

        if (interval == null) {
            return;
        }

        var startIndex = interval.getStart();
        var endIndex = interval.getEnd();

        // Decide whether beaming should be up or down
        var sumY = 0;

        for (var i = startIndex; i <= endIndex; i++) {
            var note = line.getNote(i);

            if (automaticStemDirection) {
                sumY += note.getYPos();
            } else {
                sumY += note.isUpper() ? 1 : -1;
            }
        }

        // +1: upper
        // -1: lower
        var direction = (sumY >= 0) ? 1 : -1;
        var startY = line.getNote(startIndex).getYPos();
        var endY = line.getNote(endIndex).getYPos();
        var yDiff = (double) endY - startY;
        var startX = line.getNote(startIndex).getXPos();
        var endX = line.getNote(endIndex).getXPos();
        var xDiff = endX - startX;

        LOG.fine("=== BeamCalculator: startIndex=%d endIndex=%d yDiff=%.1f xDiff=%.1f direction=%d"
            .formatted(startIndex, endIndex, yDiff, (double) xDiff, direction));

        double angle;

        if (shouldForceHorizontal(line, startIndex, endIndex)) {
            LOG.fine("  Forced horizontal");
            angle = 0;
        } else {
            var rawSlope = (yDiff * Score.NOTE_Y_OFFSET) / xDiff;
            var slope = rawSlope * SLOPE_SCALE;
            var maxDevPx = MAX_DEVIATION_SS * LayoutStylesheet.STAFF_SPACE;
            var deviation = Math.abs(slope * xDiff);

            LOG.fine("  rawSlope=%.4f scaledSlope=%.4f deviation=%.1f maxDevPx=%.1f"
                .formatted(rawSlope, slope, deviation, maxDevPx));

            if (deviation > maxDevPx) {
                var clampedSlope = Math.signum(slope) * maxDevPx / xDiff;
                LOG.fine("  CLAMPED slope %.4f -> %.4f".formatted(slope, clampedSlope));
                slope = clampedSlope;
            }

            angle = Math.atan(slope);
            LOG.fine("  angle=%.4f rad (%.1f deg)".formatted(angle, Math.toDegrees(angle)));
        }

        computeLengthenings(line, startIndex, endIndex, angle, direction);

        // Gould/Ross 4.2: validate stems against minimum length.
        // Any negative lengthening means a stem shorter than the 3.5 ss default.
        var minLengthening = findMinLengthening(line, startIndex, endIndex);

        // Phase 1: Reduce slope iteratively until all stems are valid
        var iterations = 0;
        var epsilon = 1e-6;

        while (minLengthening < 0 && Math.abs(angle) > epsilon
            && iterations < MAX_SLOPE_ITERATIONS) {

            angle *= SLOPE_REDUCTION_FACTOR;

            if (Math.abs(angle) < epsilon) {
                angle = 0;
            }

            iterations++;
            computeLengthenings(line, startIndex, endIndex, angle, direction);
            minLengthening = findMinLengthening(line, startIndex, endIndex);
        }

        if (iterations > 0) {
            LOG.fine("  Slope reduced %d times, angle=%.4f rad (%.1f deg)"
                .formatted(iterations, angle, Math.toDegrees(angle)));
        }

        // Phase 2: If stems are still too short, shift the entire beam vertically
        if (minLengthening < 0) {
            var deficit = -minLengthening;
            LOG.fine("  Shifting beam vertically by %d px".formatted(deficit));

            for (var i = startIndex; i <= endIndex; i++) {
                var note = line.getNote(i);

                if (!note.getNoteType().isGraceNote()) {
                    note.properties.lengthening += deficit;
                }
            }
        }
    }

    /**
     * Computes lengthenings for all notes in a beamed group given the beam angle.
     * The first note is anchored (lengthening = 0), and all other notes are
     * computed relative to the beam line.
     *
     * @param line       The line containing the notes
     * @param startIndex Start index of the beamed group
     * @param endIndex   End index of the beamed group
     * @param angle      The angle of the beam line
     * @param direction  The beam direction (1 for up, -1 for down)
     */
    private static void computeLengthenings(
        @NotNull Line line,
        int startIndex,
        int endIndex,
        double angle,
        int direction
    ) {
        var firstNote = line.getNote(startIndex);
        firstNote.properties.lengthening = 0;
        firstNote.setUpper(direction == 1);

        var distance =
            (firstNote.getYPos() * Score.NOTE_Y_OFFSET) - (angle * firstNote.getXPos());

        LOG.fine("  anchor: note[%d] yPos=%d xPos=%d distance=%.1f"
            .formatted(startIndex, firstNote.getYPos(), firstNote.getXPos(), distance));

        for (var i = startIndex + 1; i <= endIndex; i++) {
            var note = line.getNote(i);
            calculateNoteLengthening(note, angle, distance, direction);
            LOG.fine("  note[%d] yPos=%d xPos=%d lengthening=%d"
                .formatted(i, note.getYPos(), note.getXPos(), note.properties.lengthening));
        }
    }

    /**
     * Finds the minimum lengthening across all non-grace notes in a beamed group.
     *
     * @param line       The line containing the notes
     * @param startIndex Start index of the beamed group
     * @param endIndex   End index of the beamed group
     * @return The minimum lengthening value
     */
    private static int findMinLengthening(
        @NotNull Line line,
        int startIndex,
        int endIndex
    ) {
        var min = Integer.MAX_VALUE;

        for (var i = startIndex; i <= endIndex; i++) {
            var note = line.getNote(i);

            if (!note.getNoteType().isGraceNote()) {
                min = Math.min(min, note.properties.lengthening);
            }
        }

        return min;
    }

    /**
     * Determines whether a beamed group should be forced to a horizontal beam.
     * This follows Gould/Ross engraving rules: force horizontal when the melodic
     * contour reverses direction, when the pitch span is too large, or when
     * exactly two notes share the same pitch.
     *
     * @param line       The line containing the notes
     * @param startIndex Start index of the beamed group
     * @param endIndex   End index of the beamed group
     * @return True if the beam should be horizontal
     */
    private static boolean shouldForceHorizontal(
        @NotNull Line line,
        int startIndex,
        int endIndex
    ) {
        var minY = Integer.MAX_VALUE;
        var maxY = Integer.MIN_VALUE;
        var prevY = line.getNote(startIndex).getYPos();
        var direction = 0;

        for (var i = startIndex; i <= endIndex; i++) {
            var y = line.getNote(i).getYPos();
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);

            if (i > startIndex) {
                var diff = y - prevY;

                if (diff != 0) {
                    var newDirection = (diff > 0) ? 1 : -1;

                    if (direction != 0 && newDirection != direction) {
                        return true;
                    }

                    direction = newDirection;
                }
            }

            prevY = y;
        }

        // Two notes at the same pitch
        if (startIndex == endIndex - 1 && maxY == minY) {
            return true;
        }

        // Pitch span exceeds threshold
        return Math.abs(maxY - minY) > PITCH_SPAN_THRESHOLD;
    }

    /**
     * Checks if a note position is good relative to the beam line.
     *
     * @param note      The note to check
     * @param angle     The angle of the beam line
     * @param distance  The distance of the beam line
     * @param direction The beam direction (1 for up, -1 for down)
     * @return True if the note position is acceptable
     */
    private static boolean isGoodNotePosition(
        @NotNull Note note,
        double angle,
        double distance,
        int direction
    ) {
        var xPos = note.getXPos();
        var yPos = note.getYPos();
        return (
            (Math.round((angle * xPos) + distance) * direction) <=
                (yPos * Score.NOTE_Y_OFFSET * direction)
        );
    }

    /**
     * Checks if a note could serve as an anchor point for the beam line.
     *
     * @param line       The line containing the notes
     * @param startIndex Start index of the beamed group
     * @param endIndex   End index of the beamed group
     * @param noteIndex  Index of the note to check
     * @param angle      The angle of the beam line
     * @param distance   The distance of the beam line
     * @param direction  The beam direction (1 for up, -1 for down)
     * @return True if this note can serve as an anchor
     */
    @SuppressWarnings("Convert2streamapi")
    private static boolean isGoodNote(
        Line line,
        int startIndex,
        int endIndex,
        int noteIndex,
        double angle,
        double distance,
        int direction
    ) {
        for (var left = noteIndex - 1; left >= startIndex; left--) {
            var leftNote = line.getNote(left);

            if (!isGoodNotePosition(leftNote, angle, distance, direction)) {
                return false;
            }
        }

        for (var right = noteIndex + 1; right <= endIndex; right++) {
            var rightNote = line.getNote(right);

            if (!isGoodNotePosition(rightNote, angle, distance, direction)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Calculates the lengthening value for a single note.
     * <p>
     * Lengthening represents how much the stem needs to be adjusted from its default
     * length to align with the beam line at the note's X position.
     *
     * @param note      The note to calculate lengthening for
     * @param angle     The angle of the beam line
     * @param distance  The distance of the beam line from origin
     * @param direction The beam direction (1 for up, -1 for down)
     */
    private static void calculateNoteLengthening(
        @NotNull Note note,
        double angle,
        double distance,
        int direction
    ) {
        note.setUpper(direction == 1);

        if (note.getNoteType().isGraceNote()) {
            note.properties.lengthening = 0;
        } else {
            var lengthening = direction * ((note.getYPos() * Score.NOTE_Y_OFFSET) -
                ((angle * note.getXPos()) + distance));
            note.properties.lengthening = (int) Math.round(lengthening);
        }
    }
}
