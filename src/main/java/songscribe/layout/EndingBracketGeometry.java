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

import java.awt.Font;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import songscribe.dom.BarElement;
import songscribe.dom.CollisionRegion;
import songscribe.dom.Ending;
import songscribe.dom.Line;
import songscribe.dom.StaffElement;
import songscribe.engraving.EngravingConstants;
import songscribe.font.LocalFonts;
import songscribe.font.TextMeasurement;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;

/**
 * Layout geometry for {@link Ending} volta brackets. {@code Ending} is a DOM type and may not
 * depend on {@code songscribe.layout}, so its geometry lives here.
 */
public final class EndingBracketGeometry {

    public static final double VOLTA_TICK_HEIGHT_SS = 2.0;

    public static final double LABEL_X_INSET_SS = 0.9;

    public static final double LABEL_Y_OFFSET_SS = 0.5;

    private static final float LABEL_FONT_SCALE = 0.6f;

    private static final float MUSIC_FONT_SIZE_SS = 4.0f;

    public static final Font ENDING_FONT = LocalFonts.load(
        "emmentaler-16.otf", MUSIC_FONT_SIZE_SS * LABEL_FONT_SCALE);

    private static final Rectangle2D LABEL_1_BOUNDS_SS =
        TextMeasurement.requireVisualBounds("1.", ENDING_FONT);

    private static final Rectangle2D LABEL_2_BOUNDS_SS =
        TextMeasurement.requireVisualBounds("2.", ENDING_FONT);

    private EndingBracketGeometry() {
    }

    /**
     * Computes the two brackets of an ending: the first from the element the ending opens on to its
     * split, the second from the split to the ending's end.
     * <p>
     * The stacking pass places the ending from these brackets and records them in its
     * {@link LayoutResult.DecorationLayout.Volta}, where rendering reads them rather than deriving
     * positions of its own.
     * <p>
     * Each X is where the bracket's path runs, from the X rules below:
     * <ul>
     *   <li>At a bar — a barline or a repeat, the split included — a bracket opens on the bar's
     *       opening anchor and closes on its closing anchor, each measured from the bar's column X
     *       ({@link songscribe.dom.BarAppearance#openingAnchorSs},
     *       {@link songscribe.dom.BarAppearance#closingAnchorSs}).</li>
     *   <li>At a note or a rest, a bracket opens {@link AccidentalGeometry#ACCIDENTAL_PADDING_SS} left
     *       of its column's left edge ({@link ElementColumn#getLeftEdgeXSs}), and closes one
     *       advance of {@link SMuFLGlyph#AUGMENTATION_DOT} right of its column's right edge
     *       ({@link ElementColumn#getRightEdgeXSs}), whether or not the element is dotted.</li>
     * </ul>
     * The second bracket closes at the ending's end bar when it has one, and past its end element
     * otherwise.
     *
     * @param ending   the ending to compute brackets for
     * @param line     the line containing the ending
     * @param columnFn returns the {@link ElementColumn} of an element of {@code line}, from which
     *                 its X position and horizontal extents are read
     * @return the ending's two brackets
     * @invariant the first bracket always has a closing stroke; the second has one exactly when
     *            {@link Ending#isSecondBracketClosed()}
     */
    public static BracketRange.Pair computeBracketRanges(
        Ending ending,
        Line line,
        Function<? super StaffElement, ElementColumn> columnFn
    ) {
        // The ending owns which of its elements is the split; do not search for it here. A scan
        // that includes the anchor takes an anchor that is itself a repeat for the split.
        var split = ending.getSplitElement();

        var startElement = line.getElement(ending.getOpeningElementIndex());
        var endElement = line.getElement(ending.range().end());

        // The split lies strictly between anchor and end, so both brackets always exist.
        var firstBracket = firstBracketRange(startElement, split, columnFn);
        var secondBracket = secondBracketRange(ending, endElement, split, columnFn);

        return new BracketRange.Pair(firstBracket, secondBracket);
    }

    private static BracketRange firstBracketRange(
        StaffElement startElement,
        BarElement split,
        Function<? super StaffElement, ElementColumn> columnFn
    ) {
        var startColumn = columnFn.apply(startElement);
        double x1;

        // For barlines and repeats, the bar states the anchor its opening arm hangs from.
        // For notes/rests, anchor to the element's left extent.
        if (startElement instanceof BarElement startBar) {
            x1 = startColumn.getXSs() + startBar.bar().openingAnchorSs();
        }
        else {
            x1 = startColumn.getLeftEdgeXSs() - AccidentalGeometry.ACCIDENTAL_PADDING_SS;
        }

        var x2 = columnFn.apply(split).getXSs() + split.bar().closingAnchorSs();

        return new BracketRange(x1, x2, true);
    }

    private static BracketRange secondBracketRange(
        Ending ending,
        StaffElement endElement,
        BarElement split,
        Function<? super StaffElement, ElementColumn> columnFn
    ) {
        var x1 = columnFn.apply(split).getXSs() + split.bar().openingAnchorSs();
        var endBar = ending.getEndBar();
        double x2;

        if (endBar == null) {
            x2 = columnFn.apply(endElement).getRightEdgeXSs()
                + SMuFLMetadata.advanceWidthSs(SMuFLGlyph.AUGMENTATION_DOT);
        }
        else {
            x2 = columnFn.apply(endBar).getXSs() + endBar.bar().closingAnchorSs();
        }

        return new BracketRange(x1, x2, ending.isSecondBracketClosed());
    }

    /**
     * Computes the collision sub-regions of one visual bracket, so that higher stacking layers can
     * nestle into the open space between its arm, its legs and its label.
     * <p>
     * Every region describes ink. The path is stroked {@link EngravingConstants#VOLTA_BRACKET_SS}
     * wide and centered on itself, so each stroke's ink reaches half that width past the path on
     * both sides, horizontally as well as vertically. X offsets are relative to the ending's anchor
     * X, which {@code xBaseSs} places this bracket's {@link BracketRange#x1Ss} from; Y offsets are
     * relative to the Y the top arm's path runs along, positive downward.
     * <p>
     * The regions, in this order:
     * <ol>
     *   <li>the top arm: from half a stroke left of {@link BracketRange#x1Ss} to half a stroke
     *       right of {@link BracketRange#x2Ss}, one stroke high — an open arm's capped end and a
     *       closed bracket's right leg reach the same X;</li>
     *   <li>the left leg: one stroke wide around {@link BracketRange#x1Ss}, from the arm's ink top
     *       down to {@value #VOLTA_TICK_HEIGHT_SS} staff spaces below the arm's path, where the
     *       leg's capped end stops;</li>
     *   <li>the right leg, only when {@link BracketRange#hasClosingStroke()}: the same, around
     *       {@link BracketRange#x2Ss};</li>
     *   <li>the label's ink box, drawn {@value #LABEL_X_INSET_SS} staff spaces right of the left
     *       leg's path and {@value #LABEL_Y_OFFSET_SS} below the arm's.</li>
     * </ol>
     *
     * @param bracket the bracket to decompose
     * @param slot    which of the ending's two brackets it is, which decides its label
     * @param xBaseSs offset of {@code bracket}'s {@link BracketRange#x1Ss} from the ending's anchor
     *                X
     * @return the bracket's regions: four with a closing stroke, three without
     */
    public static List<CollisionRegion> computeCollisionRegions(
        BracketRange bracket,
        BracketRange.Bracket slot,
        double xBaseSs
    ) {
        var bracketThicknessSs = EngravingConstants.VOLTA_BRACKET_SS;
        var halfThicknessSs = bracketThicknessSs / 2;

        // The stroke is centered on its path: the arm's ink reaches half a thickness above the
        // path's Y, and each leg's ink half a thickness either side of the leg's X.
        var inkTopSs = -halfThicknessSs;
        var leftLegInkXSs = xBaseSs - halfThicknessSs;
        var rightLegInkXSs = xBaseSs + bracket.x2Ss() - bracket.x1Ss() - halfThicknessSs;
        var regions = new ArrayList<CollisionRegion>(4);

        regions.add(new CollisionRegion(
            leftLegInkXSs, inkTopSs,
            rightLegInkXSs + bracketThicknessSs - leftLegInkXSs, bracketThicknessSs));

        // The legs hang from the arm. Their lower ends are the stroked path's caps, which
        // drawPath insets so that the ink stops on the endpoint rather than bulging past it.
        var legHeightSs = VOLTA_TICK_HEIGHT_SS + halfThicknessSs;

        regions.add(new CollisionRegion(
            leftLegInkXSs, inkTopSs, bracketThicknessSs, legHeightSs));

        if (bracket.hasClosingStroke()) {
            regions.add(new CollisionRegion(
                rightLegInkXSs, inkTopSs, bracketThicknessSs, legHeightSs));
        }

        // Label (e.g. "1." or "2."), drawn from its glyph origin, so its ink starts at the
        // bounds' own X past that origin.
        var labelBounds = labelBoundsSs(slot);
        regions.add(new CollisionRegion(
            xBaseSs + LABEL_X_INSET_SS + labelBounds.getX(), LABEL_Y_OFFSET_SS,
            labelBounds.getWidth(), TextMeasurement.inkHeight(labelBounds)));

        return regions;
    }

    static Rectangle2D labelBoundsSs(BracketRange.Bracket slot) {
        return switch (slot) {
            case FIRST -> LABEL_1_BOUNDS_SS;
            case SECOND -> LABEL_2_BOUNDS_SS;
        };
    }
}
