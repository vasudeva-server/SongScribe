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

package songscribe.dom;

import java.util.List;

import songscribe.engraving.BarStroke;
import songscribe.engraving.EngravingConstants;

/**
 * A barline or repeat sign, as the left-to-right sequence of strokes it is drawn from.
 *
 * <p>The strokes are laid out in the order given, starting at the element's own X origin,
 * with exactly one {@link BarStroke#SEPARATION_SS} between each adjacent pair.
 * The group's width and both of its volta bracket anchors are derived from that sequence,
 * so a bar's geometry is stated once, in its declaration, rather than restated by every
 * reader of it.
 *
 * <p>Repeat dots take part in neither volta anchor. A volta bracket opens on the group's last
 * drawn line — centered on it, or with its arm's right edge on that line's right edge where the
 * line is thick — and closes half a thin barline past the left edge of the first drawn line.
 * Dots are skipped at both ends because they belong to the repeated section rather than to the
 * bar line the bracket marks: a bracket closing on leading dots would hang back into the
 * preceding measure, and one opening past trailing dots would start inside the section it is
 * meant to bound.
 *
 * @param strokes the strokes, left to right; copied on construction, so the record is
 *                immutable whatever the caller does with the list afterwards
 * @invariant {@code strokes} is non-empty and holds at least one stroke that is not
 *            {@link BarStroke#DOTS}, so {@link #openingAnchorSs()} scanning back from the end
 *            and {@link #closingAnchorSs()} scanning forward from the start each reach a drawn
 *            line rather than running off the sequence
 * @throws IllegalArgumentException if {@code strokes} is empty or holds nothing but
 *         {@link BarStroke#DOTS}
 */
public record BarAppearance(List<BarStroke> strokes) implements ElementAppearance {

    public BarAppearance {
        strokes = List.copyOf(strokes);

        if (strokes.isEmpty()) {
            throw new IllegalArgumentException("strokes must not be empty");
        }

        if (strokes.stream().allMatch(stroke -> stroke == BarStroke.DOTS)) {
            throw new IllegalArgumentException(
                "strokes (%s) must hold at least one stroke that is not %s".formatted(
                    strokes, BarStroke.DOTS));
        }
    }

    /**
     * Returns the appearance of a bar drawn from the given strokes, left to right.
     *
     * @param strokes the strokes, left to right
     * @return the appearance, so that a declaration site reads
     *         {@code BarAppearance.of(DOTS, THIN, THICK)}
     */
    public static BarAppearance of(BarStroke... strokes) {
        return new BarAppearance(List.of(strokes));
    }

    /**
     * @return The total width of the group in staff spaces: every stroke's width, plus one
     *         {@link BarStroke#SEPARATION_SS} for each gap between adjacent
     *         strokes.
     */
    public double widthSs() {
        var totalSs = (strokes.size() - 1) * BarStroke.SEPARATION_SS;

        for (var stroke : strokes) {
            totalSs += stroke.widthSs();
        }

        return totalSs;
    }

    /**
     * The X offset in staff spaces, relative to the element's origin, at which a volta bracket
     * opening on this bar puts its left arm.
     *
     * <p>This is a path coordinate, not an ink edge: the arm is stroked centered on it, reaching
     * half a {@link EngravingConstants#VOLTA_BRACKET_SS} to either side. Trailing repeat dots are
     * skipped rather than opened past — one opening past them would start inside the section the
     * bracket is meant to bound.
     *
     * @return the center of the last drawn line, which the arm then straddles; or, when that line
     *         is {@link BarStroke#THICK}, half a bracket short of its right edge, which puts the
     *         arm's right edge on that edge so the bracket begins where the bar stops
     */
    public double openingAnchorSs() {
        var lastLineIndex = strokes.size() - 1;

        while (strokes.get(lastLineIndex) == BarStroke.DOTS) {
            lastLineIndex--;
        }

        var lastLine = strokes.get(lastLineIndex);
        var offsetSs = lastLineIndex * BarStroke.SEPARATION_SS;

        for (var i = 0; i < lastLineIndex; i++) {
            offsetSs += strokes.get(i).widthSs();
        }

        // We follow LilyPond's technique of aligning an opening bracket
        // on the trailing edge of a thick barline. The arm is stroked centered on this X, so it
        // is pulled back half a bracket to put its right edge on that edge rather than straddle
        // it. A thin line is narrow enough to align on its center, where no pull-back applies.
        var width = lastLine.widthSs();

        if (lastLine == BarStroke.THICK) {
            return offsetSs + width - EngravingConstants.VOLTA_BRACKET_SS / 2;
        }

        return offsetSs + width / 2;
    }

    /**
     * The X offset in staff spaces, relative to the element's origin, at which a volta bracket
     * closing on this bar ends its horizontal arm.
     *
     * <p>This is a path coordinate, not an ink edge: the arm ends here, and a closing stroke,
     * where the bracket has one, hangs from here. The two cases share the one anchor, so an open
     * bracket and a struck-through one stop at the same place. Leading repeat dots are skipped
     * rather than closed on — one closing on them would hang back into the preceding measure.
     *
     * <p>The advance is half a {@link BarStroke#THIN} whatever the line it is
     * measured from, following LilyPond: on a bar opening with a thick line the endpoint lands
     * that same distance in, well short of that line's center.
     *
     * @return half a thin barline past the left edge of the first drawn line
     */
    public double closingAnchorSs() {
        var offsetSs = 0.0;
        var index = 0;

        while (strokes.get(index) == BarStroke.DOTS) {
            offsetSs += strokes.get(index).widthSs() + BarStroke.SEPARATION_SS;
            index++;
        }

        return offsetSs + BarStroke.THIN.widthSs() / 2;
    }
}
