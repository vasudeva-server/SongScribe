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

import org.jetbrains.annotations.NotNull;
import songscribe.ui.component.Score;

/**
 * Calculates beam positioning and note lengthenings for proper visual rendering.
 *
 * This class encapsulates the mathematical calculations for determining how much each
 * note in a beamed group needs to be extended to align with the beam angle.
 */
public class BeamCalculator {

    private static final double MAX_BEAM_ANGLE = 0.4;

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

        var angle = Math.atan((yDiff * Score.NOTE_Y_OFFSET) / xDiff);
        angle = Math.max(-MAX_BEAM_ANGLE, Math.min(angle, MAX_BEAM_ANGLE));

        var goodIndex = -1;

        for (var i = startIndex; i <= endIndex; i++) {
            var note = line.getNote(i);
            var xPos = note.getXPos();
            var yPos = note.getYPos();
            var distance = (yPos * Score.NOTE_Y_OFFSET) - (angle * xPos);

            if (
                isGoodNote(
                    line,
                    startIndex,
                    endIndex,
                    i,
                    angle,
                    distance,
                    direction
                )
            ) {
                goodIndex = i;
                break;
            }
        }

        var note = line.getNote(goodIndex);
        note.acceleration.lengthening = 0;
        note.setUpper(direction == 1);
        var distance =
            (note.getYPos() * Score.NOTE_Y_OFFSET) - (angle * note.getXPos());

        for (var left = goodIndex - 1; left >= startIndex; left--) {
            note = line.getNote(left);
            calculateNoteLengthening(note, angle, distance, direction);
        }

        for (var right = goodIndex + 1; right <= endIndex; right++) {
            note = line.getNote(right);
            calculateNoteLengthening(note, angle, distance, direction);
        }
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
            note.acceleration.lengthening = 0;
        } else {
            var lengthening = (note.getYPos() * Score.NOTE_Y_OFFSET) -
                ((angle * note.getXPos()) + distance);
            note.acceleration.lengthening = (int) Math.round(lengthening);
        }
    }
}
