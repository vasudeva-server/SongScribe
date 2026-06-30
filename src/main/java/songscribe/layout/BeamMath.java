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

package songscribe.layout;

import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.StaffElement;

/**
 * Pure-static beam-geometry helpers shared by {@link LayoutEngine},
 * {@link songscribe.ui.renderer.BeamGroupRenderer}, and the MusicXML
 * writer/reader. All methods are keyed only on a {@link Line} and indices —
 * no layout state is required.
 *
 * <p>Centralising these avoids a second copy of the beam-count and
 * beam-level derivation rules, so the writer can produce per-note
 * {@code <beam>} values from the same source of truth that drives
 * rendering.
 */
public final class BeamMath {

    // Note types in decreasing beam count order (32nd → 16th → 8th).
    // Index 0 = 3 beams (DEMI_SEMIQUAVER), index 2 = 1 beam (QUAVER).
    private static final ElementType[] BEAM_LEVELS = {
        ElementType.DEMI_SEMIQUAVER,
        ElementType.SEMIQUAVER,
        ElementType.QUAVER,
    };

    /**
     * Number of distinct beam levels: level 0 = 8th, level 1 = 16th,
     * level 2 = 32nd. Equals {@code BEAM_LEVELS.length}.
     *
     * <p>Used by the MusicXML writer to size per-note beam-value arrays
     * without hardcoding the count.
     */
    public static final int LEVEL_COUNT = BEAM_LEVELS.length;

    private BeamMath() {}

    // -------------------------------------------------------------------------
    // Beam count
    // -------------------------------------------------------------------------

    /**
     * Returns the number of beams (flag levels) for the given note.
     * {@code QUAVER} = 1, {@code SEMIQUAVER} = 2, {@code DEMI_SEMIQUAVER} = 3.
     * Any other type (including rests and grace notes) returns 1.
     */
    public static int beamCount(StaffElement note) {
        return switch (note.getType()) {
            case SEMIQUAVER      -> 2;
            case DEMI_SEMIQUAVER -> 3;
            default              -> 1;
        };
    }

    // -------------------------------------------------------------------------
    // Stub direction
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} if the partial-beam stub at position {@code i}
     * within the beam group [{@code beamStart}, {@code beamEnd}] should be
     * drawn to the right; {@code false} if it should be drawn to the left
     * or if no stub is needed at this position.
     *
     * <p>A stub is needed at beam level L when neither the left nor the right
     * neighbour shares level L. The direction rule is:
     * <ul>
     *   <li>first element in group → right</li>
     *   <li>last element, or element immediately after a beam break → left</li>
     *   <li>element immediately before a beam break → right</li>
     *   <li>otherwise → toward the neighbour with more beams</li>
     * </ul>
     */
    public static boolean stubRight(Line line, int i, int beamStart, int beamEnd) {
        var myBeams    = beamCount(line.getElement(i));
        var leftBeams  = i > beamStart ? beamCount(line.getElement(i - 1)) : 0;
        var rightBeams = i < beamEnd   ? beamCount(line.getElement(i + 1)) : 0;

        var hasStub = false;

        for (var level = 2; level <= myBeams; level++) {
            if (leftBeams < level && rightBeams < level) {
                hasStub = true;
                break;
            }
        }

        if (!hasStub) {
            return false;
        }

        if (i == beamStart) {
            return true;                          // first element → stub right
        } else if (i == beamEnd || rightBeams < myBeams) {
            return false;                         // last element or after break → left
        } else if (leftBeams < myBeams) {
            return true;                          // element at a beam break → right
        } else {
            return rightBeams >= leftBeams;       // toward neighbour with more beams
        }
    }

    // -------------------------------------------------------------------------
    // Beam level (highest level spanned by the shortest note in a range)
    // -------------------------------------------------------------------------

    /**
     * Returns the beam level for the given note range, where level 0 = 8th,
     * level 1 = 16th, level 2 = 32nd. The level equals the shortest note in
     * [{@code beginIndex}, {@code endIndex}].
     */
    public static int beamLevel(Line line, int beginIndex, int endIndex) {
        var maxLevel = 0;

        for (var i = beginIndex; i <= endIndex; i++) {
            var noteType = line.getElement(i).getType();

            for (var j = 0; j < BEAM_LEVELS.length; j++) {
                if (noteType == BEAM_LEVELS[j]) {
                    var level = BEAM_LEVELS.length - 1 - j;
                    maxLevel = Math.max(maxLevel, level);
                    break;
                }
            }
        }

        return maxLevel;
    }

    // -------------------------------------------------------------------------
    // Note-type-in-level predicate
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} if the note at {@code noteIndex} is beamable at
     * the given {@code level} (0 = 8th, 1 = 16th, 2 = 32nd).
     *
     * <p>Grace notes are resolved by inspecting the nearest non-grace
     * neighbours: a grace note is in level L if both neighbours are in level L.
     */
    public static boolean noteTypeInLevel(Line line, int noteIndex, int level) {
        var type = line.getElement(noteIndex).getType();

        if (!type.isGraceNote()) {
            for (var i = 0; i < BEAM_LEVELS.length; i++) {
                if (BEAM_LEVELS[i] == type) {
                    return i <= (BEAM_LEVELS.length - 1 - level);
                }
            }

            return false;
        }

        // Grace notes: check surrounding non-grace notes.
        var begin = noteIndex - 1;
        var end   = noteIndex + 1;

        while (begin > 0 && line.getElement(begin).getType().isGraceNote()) {
            begin--;
        }

        while (end < line.elementCount() && line.getElement(end).getType().isGraceNote()) {
            end++;
        }

        return begin >= 0 && noteTypeInLevel(line, begin, level) &&
            end < line.elementCount() && noteTypeInLevel(line, end, level);
    }
}
