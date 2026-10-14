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
 * <p>The strokes are laid out in order from the element's own X origin, exactly one
 * {@link BarStroke#SEPARATION_SS} apart. The width and both volta anchors derive from that
 * sequence.
 *
 * <p>Repeat dots take part in neither volta anchor: they belong to the repeated section rather
 * than to the bar line the bracket marks.
 *
 * <p>Every bar is declared as a literal in this package — the constants of {@link ElementType}
 * and {@link #SINGLE_BARLINE} — through {@link #of}, and no other code builds one.
 *
 * @param strokes the strokes, left to right; copied on construction
 * @invariant {@code strokes} is non-empty and holds at least one stroke that is not
 *            {@link BarStroke#DOTS}, so {@link #openingAnchorSs()} scanning back from the end
 *            and {@link #closingAnchorSs()} scanning forward from the start each reach a drawn
 *            line rather than running off the sequence
 */
public record BarAppearance(List<BarStroke> strokes) implements ElementAppearance {

    /**
     * Named because a cautionary key signature draws it at the end of a line, and that drawing
     * and {@link ElementType#SINGLE_BARLINE} must not disagree about a single barline.
     */
    public static final BarAppearance SINGLE_BARLINE = of(BarStroke.THIN);

    public BarAppearance {
        strokes = List.copyOf(strokes);
    }

    static BarAppearance of(BarStroke... strokes) {
        return new BarAppearance(List.of(strokes));
    }

    public double widthSs() {
        var totalSs = (strokes.size() - 1) * BarStroke.SEPARATION_SS;

        for (var stroke : strokes) {
            totalSs += stroke.widthSs();
        }

        return totalSs;
    }

    /**
     * The X offset in staff spaces, relative to the element's origin, at which a volta bracket
     * opening on this bar puts its left arm. A path coordinate, not an ink edge: the arm is
     * stroked centered on it, half a {@link EngravingConstants#VOLTA_BRACKET_SS} to either side.
     *
     * @return the center of the last drawn line; when that line is {@link BarStroke#THICK}, half
     *         a bracket short of its right edge, so the arm's right edge lies on that edge
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
     * closing on this bar ends its horizontal arm. A path coordinate, not an ink edge.
     *
     * <p>The advance is half a {@link BarStroke#THIN} whatever the first line is, following
     * LilyPond.
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
