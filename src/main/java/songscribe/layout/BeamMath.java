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
import songscribe.engraving.LineThickness;
import songscribe.engraving.SMuFLConstants;
import songscribe.engraving.Staff;

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

    /**
     * Vertical gap, in staff spaces, a grace note's stem tip must keep below an upward beam
     * before the beam is pushed to the other side of the staff instead
     * ({@link #graceStemTouchesBeamAbove}).
     * <p>
     * It also absorbs the error in {@link #estimatedBeamBottomAboveYSs}, which places the beam
     * a natural stem above the group's highest head while the scorer routinely settles a few
     * tenths of a space lower. Erring low here only means a beam flips that would have been a
     * near miss; erring high means the grace note is buried, which is the bug this guards.
     */
    static final double GRACE_STEM_BEAM_CLEARANCE_SS = 0.5;

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
        }

        if (i == beamEnd || rightBeams < myBeams) {
            return false;                         // last element or after break → left
        }

        //noinspection SimplifiableIfStatement
        if (leftBeams < myBeams) {
            return true;                          // element at a beam break → right
        }

        return rightBeams >= leftBeams;       // toward neighbour with more beams
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

    // -------------------------------------------------------------------------
    // French beaming
    // -------------------------------------------------------------------------

    /**
     * Returns how many beam levels the stem at index {@code i} within the beam
     * group [{@code beamStart}, {@code beamEnd}] is shortened by under French
     * beaming — i.e. the number of beam translations between the outer beam and
     * the innermost beam that passes straight <em>through</em> this stem.
     *
     * <p>A stem is shortened only for beams that pass through it, so it still
     * terminates on a beam rather than in mid-air. Following LilyPond
     * ({@code Beam::set_stem_lengths}, which intersects each stem's left and
     * right beaming slices), the count is the number of beam levels shared with
     * <em>both</em> neighbours, less one for the level the stem must still reach.
     *
     * <p>So a stem returns 0 — and runs out to the outer beam as usual — exactly
     * when it shares no more than the outer beam with a neighbour: the first and
     * last stem of a group, every stem carrying a single beam, and every stem
     * whose <em>secondary</em> beam breaks. A break at a deeper level still
     * shortens: in a 16th/32nd/32nd/16th group the second stem is where the 32nd
     * sub-beam starts, yet it stops one level short, on the 16th beam. A stub is
     * not a level the stem must reach either — it sits nearer the notehead and is
     * covered on the way.
     *
     * <p>This is a drawing-time correction only: the logical stem tip stays at
     * the outer beam, which is what beam placement and the stacker read.
     */
    public static int frenchBeamShortening(Line line, int i, int beamStart, int beamEnd) {
        if (i <= beamStart || i >= beamEnd) {
            return 0;
        }

        // A note's beams always run 0..beamCount-1, so LilyPond's intersection of the
        // stem's left and right beaming slices is simply the smallest of the three
        // counts — the beams that reach this stem from both sides pass through it.
        var leftBeams = neighborBeamCount(line, i, -1);
        var ownBeams = beamCountInBeam(line, i);
        var rightBeams = neighborBeamCount(line, i, 1);
        var beamsThrough = Math.min(ownBeams, Math.min(leftBeams, rightBeams));

        return Math.max(0, beamsThrough - 1);
    }

    /**
     * Returns how many beams the element at {@code index} contributes to a beam it sits
     * under, or 0 if it takes no beam at all.
     *
     * <p>Unlike {@link #beamCount}, which reports the flag count a note type would carry
     * on its own, this reports 0 for anything unbeamable — a rest or a longer note inside
     * a beam's span stops a beam rather than carrying one across.
     */
    private static int beamCountInBeam(Line line, int index) {
        var element = line.getElement(index);
        return element.getType().isBeamable() ? beamCount(element) : 0;
    }

    /**
     * Returns {@link #beamCountInBeam} for the nearest element to one side of
     * {@code index} that takes part in beaming, stepping by {@code step} (-1 for the
     * left neighbour, 1 for the right); 0 if the line runs out first.
     *
     * <p>Grace notes are stepped over. One can be inserted between two beamed notes
     * without joining their beams, so the beam that decides this stem's length is the one
     * continuing to the note on the far side of it, not the grace note's own flag.
     */
    private static int neighborBeamCount(Line line, int index, int step) {
        var neighborIndex = line.nearestNonGraceIndex(index, step);

        return neighborIndex < 0 ? 0 : beamCountInBeam(line, neighborIndex);
    }

    // -------------------------------------------------------------------------
    // Grace notes inside a beam's span
    // -------------------------------------------------------------------------

    /**
     * Returns whether {@code element} takes part in the beaming of a group whose span it
     * falls inside.
     *
     * <p>A grace note can be inserted between two beamed notes, but it never joins their
     * beams: it keeps its own upward stem direction, its own short stem, and its own flag,
     * and it must not influence where the beam is placed. Every loop that walks a beam's
     * index range therefore has to step over it rather than treat it as a member.
     */
    public static boolean participatesInBeaming(StaffElement element) {
        return !element.getType().isGraceNote();
    }

    /**
     * Returns whether any grace note inside [{@code beamStart}, {@code beamEnd}] would have its
     * stem run into — or come within {@link #GRACE_STEM_BEAM_CLEARANCE_SS} of — a beam drawn above
     * the group, whose highest member sits at {@code topStaffPosition}.
     *
     * <p>A grace note keeps its own short, always-upward stem
     * ({@link NoteGeometry#effectiveDirection}) instead of running out to the beam, so an upward
     * beam over a grace note higher than its neighbours crosses the grace stem and swallows the
     * notehead. The stem tip is the grace note's topmost ink: its flag hangs back down from there.
     */
    public static boolean graceStemTouchesBeamAbove(
        Line line, int beamStart, int beamEnd, int topStaffPosition) {
        var beamBottomYSs = estimatedBeamBottomAboveYSs(line, beamStart, beamEnd, topStaffPosition);

        for (var i = beamStart; i <= beamEnd; i++) {
            var element = line.getElement(i);

            if (participatesInBeaming(element)) {
                continue;
            }

            var stemTopYSs =
                Staff.spToSs(element.getStaffPosition()) - SMuFLConstants.GRACE_NOTE_STEM_LENGTH_SS;

            if (stemTopYSs < beamBottomYSs + GRACE_STEM_BEAM_CLEARANCE_SS) {
                return true;
            }
        }

        return false;
    }

    /**
     * Estimates the Y of the lowest edge of the beam stack an upward beam would draw over the
     * group [{@code beamStart}, {@code beamEnd}], in layout staff spaces (Y down) from the middle
     * line. {@code topStaffPosition} is the staff position of the group's highest member.
     *
     * <p>An estimate is all that is available: the direction has to be settled before columns
     * exist, and {@link BeamScoring} cannot place the beam without their X positions. So the beam
     * is assumed to sit a natural stem above the highest head — its outer edge — with the stack of
     * {@link #beamLevel} beams hanging below it at the renderer's spacing.
     */
    private static double estimatedBeamBottomAboveYSs(
        Line line, int beamStart, int beamEnd, int topStaffPosition) {
        var beamStackCount = beamLevel(line, beamStart, beamEnd) + 1;

        return Staff.spToSs(topStaffPosition)
            - SMuFLConstants.STEM_LENGTH_SS
            + LineThickness.beamStackHeightSs(beamStackCount);
    }
}
