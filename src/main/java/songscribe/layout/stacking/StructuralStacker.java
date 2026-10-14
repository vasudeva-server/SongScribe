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

package songscribe.layout.stacking;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

import org.jspecify.annotations.Nullable;

import songscribe.dom.ArticulationType;
import songscribe.dom.CollisionRegion;
import songscribe.dom.DynamicAttachment;
import songscribe.dom.Ending;
import songscribe.dom.Hairpin;
import songscribe.dom.IntrinsicHeight;
import songscribe.dom.Line;
import songscribe.dom.LineElement;
import songscribe.dom.NoteElement;
import songscribe.dom.Span;
import songscribe.dom.StaffElement;
import songscribe.dom.Direction;
import songscribe.dom.Tuplet;
import songscribe.engraving.StemMetrics;
import songscribe.engraving.TupletMetrics;
import songscribe.layout.Beaming;
import songscribe.layout.BracketRange;
import songscribe.layout.Chord;
import songscribe.layout.ElementColumn;
import songscribe.layout.ElementSeat;
import songscribe.layout.EndingBracketGeometry;
import songscribe.layout.Footprint;
import songscribe.layout.HairpinEndpoints;
import songscribe.layout.LayoutResult;
import songscribe.layout.LayoutResultBuilder;
import songscribe.layout.NoteGeometry;
import songscribe.layout.SpanEnd;
import songscribe.layout.StaffExtents;
import songscribe.layout.TupletGeometry;

/**
 * Stacks structural-tier decorations (tier 3): tuplets, hairpins, text dynamics,
 * and endings (volta brackets).
 */
public class StructuralStacker {

    public static final double HAIRPIN_MARGIN_SS = 1.0;
    public static final double ENDING_MARGIN_SS = 1.0;

    /**
     * LilyPond {@code TupletBracket.staff-padding}: outer endpoints floor to the staff-line
     * centerline widened by this.
     */
    public static final double TUPLET_STAFF_PADDING_SS = 0.25;

    /**
     * How far below a dynamics group's shared reference line a text dynamic's glyph baseline sits,
     * from LilyPond's {@code DynamicText.Y-offset}.
     * <p>
     * Not to be confused with {@link NoteAttachedStacker#DYNAMIC_PADDING_SS}, which happens to
     * share this value but means something else entirely. Do not collapse the two.
     */
    public static final double DYNAMIC_TEXT_BASELINE_OFFSET_SS = 0.6;

    // A text dynamic sits above the staff with DynamicLineSpanner's padding and staff-padding, and
    // looks for supports as far as the structural horizontal margin.
    private static final AlignedSide DYNAMIC_SIDE = new AlignedSide(Direction.UP,
        NoteAttachedStacker.DYNAMIC_PADDING_SS, NoteAttachedStacker.DYNAMIC_STAFF_PADDING_SS,
        StackingUtils.STRUCTURAL_HORIZONTAL_MARGIN_SS);

    private final StackingContext context;
    private final StackingLayer structuralLayer;

    public StructuralStacker(StackingContext context, StaffExtents structuralExtents) {
        this.context = context;
        structuralLayer =
            new StackingLayer(structuralExtents, context.getBuilder().decorationLayouts());
    }

    /** Split from {@link #stackRemaining} because the outside-priority scripts stack between the two. */
    public void stackTuplets() {
        stackTuplets(context.getLine(), context.getColumnsByElement(), context.getBuilder());
    }

    /** Hairpins and text dynamics are placed together, group by group, since a group shares one reference line. */
    public void stackRemaining() {
        var line = context.getLine();
        var columnsByElement = context.getColumnsByElement();
        var builder = context.getBuilder();

        // Tier 3b/3c: hairpins and text dynamics, in left-to-right group order
        for (var group : DynamicGrouper.group(line)) {
            stackDynamicsGroup(group, line, columnsByElement);
        }

        // Tier 3d: Volta brackets (endings)
        stackEndings(line, columnsByElement, builder);
    }

    private void stackTuplets(
        Line line,
        Map<StaffElement, ElementColumn> columnsByElement,
        LayoutResultBuilder builder) {

        for (var tuplet : line.spanRegistry().findSpans(Tuplet.class)) {
            var spanColumns = ElementColumn.resolveSpan(tuplet, columnsByElement);

            if (spanColumns == null) {
                continue;
            }

            var anchorColumn = spanColumns.anchorColumn();
            var endColumn = spanColumns.endColumn();

            var style = TupletGeometry.Style.of(tuplet);
            var heightSs = style.heightSs();
            var marginSs = style.marginSs();
            var anchorXSs = anchorColumn.getXSs();
            var widthSs = TupletGeometry.getSpanWidthSs(anchorXSs, endColumn.getXSs());

            var nonRestColumns = collectNonRestSpannedColumns(line, tuplet, columnsByElement);
            var dySs = computeTupletSlopeDySs(nonRestColumns, widthSs, this::rawObstacleTopSs);

            // The visual bracket arms extend past the outer note columns; the clearance folds each
            // outer obstacle in again at its arm's X so the extended, sloped arm — not just the line
            // over the note — stays clear. Compute the arm X positions the same way the renderer does
            // (shared TupletGeometry helpers) so reserved and drawn geometry match.
            var endXSs = anchorXSs + widthSs;
            var leftArmXSs = TupletGeometry.bracketLeftEdgeXSs(tuplet, anchorXSs, anchorColumn);
            var rightArmXSs = TupletGeometry.bracketRightEdgeXSs(endXSs, endColumn);

            // Sloped clearance: find the left-endpoint ceiling that keeps the tilted bracket clear of
            // every non-rest tip. The clearance has already folded in every spanned tip along the
            // slope, so the box is placed against that ceiling verbatim — re-combining with the flat
            // note skyline (the expanded extents query) would re-flatten an ascending bracket up to
            // its peak, defeating the slope. The box geometry is inlined: the box bottom
            // (bracketed: the arm bottom; number-only: the number's ink bottom) sits marginSs above
            // the tip, so the box top is a further heightSs out. Either way the box top lands at
            // TupletMetrics.BRACKET_PADDING_SS plus half an ink height above the tip — the number's
            // ink top.
            var bracketLine = new BracketLine(anchorXSs, widthSs, dySs, leftArmXSs, rightArmXSs);
            var leftYSs = computeTupletClearanceLeftYSs(
                nonRestColumns, bracketLine, this::rawObstacleTopSs, this::scriptObstacles);
            var finalLeftYSs = leftYSs - marginSs - heightSs;

            // Reserve the bracket's true sloped outer edge so later structural elements and line
            // sizing see the tilted silhouette rather than a flat approximation of it.
            structuralLayer.extents().ySetSloped(
                Direction.UP, new Chord(anchorXSs, endXSs, finalLeftYSs, finalLeftYSs + dySs));

            builder.decorationLayouts().putTuplet(tuplet,
                new LayoutResult.DecorationLayout.Sloped(
                    anchorXSs, finalLeftYSs, dySs, widthSs, heightSs, marginSs));
        }
    }

    /**
     * The bracket floats above the real tips and never shortens a stem to meet the line. A raw
     * ceiling with no margin baked in — the caller applies the single margin gap. With an empty tip
     * list (all-rest span) the ceiling collapses to {@link Double#POSITIVE_INFINITY}.
     * <p>
     * The vertical arms extend beyond the outer columns, so the anchor and end notehead tips are
     * folded a second time from the arm X positions. Scripts are folded only at their own center X,
     * never from the arm edge.
     *
     * @param nonRestColumns ordered spanned columns with rests already excluded
     */
    static double computeTupletClearanceLeftYSs(
        List<ElementColumn> nonRestColumns,
        BracketLine bracketLine,
        ToDoubleFunction<? super ElementColumn> obstacleTopSs,
        Function<? super ElementColumn, ? extends List<ScriptObstacle>> scriptObstacles) {

        var anchorXSs = bracketLine.anchorXSs();
        var slope = bracketLine.dySs() / bracketLine.widthSs();
        var leftYSs = Double.POSITIVE_INFINITY;
        var anchorTipTopYSs = 0.0;
        var endTipTopYSs = 0.0;

        for (var i = 0; i < nonRestColumns.size(); i++) {
            var column = nonRestColumns.get(i);

            // The notehead/stem/beam tip folds at the column's own X (its note-column bounds).
            var tipTopYSs = columnTipTopSs(column, obstacleTopSs);
            leftYSs = Math.min(leftYSs, tipTopYSs - slope * (column.getXSs() - anchorXSs));

            if (i == 0) {
                anchorTipTopYSs = tipTopYSs;
            }

            if (i == nonRestColumns.size() - 1) {
                endTipTopYSs = tipTopYSs;
            }

            // Each script folds at its own center X, mirroring LilyPond's script_x.center().
            for (var script : scriptObstacles.apply(column)) {
                leftYSs = Math.min(leftYSs, script.topYSs() - slope * (script.centerXSs() - anchorXSs));
            }
        }

        if (!nonRestColumns.isEmpty()) {
            leftYSs = Math.min(
                leftYSs, anchorTipTopYSs - slope * (bracketLine.leftArmXSs() - anchorXSs));
            leftYSs = Math.min(
                leftYSs, endTipTopYSs - slope * (bracketLine.rightArmXSs() - anchorXSs));
        }

        return leftYSs;
    }

    /**
     * @param widthSs anchor→end element run the slope is expressed over
     * @param dySs    bracket-line rise over {@code widthSs} (0 when flat/vetoed)
     */
    record BracketLine(
        double anchorXSs, double widthSs, double dySs, double leftArmXSs, double rightArmXSs) {
    }

    /**
     * The obstacle top a column presents to the bracket, staff-top-clamped, WITHOUT any articulation,
     * so a script is never projected from the arm edge. The staff-top ceiling is the staff-line
     * centerline widened by {@link #TUPLET_STAFF_PADDING_SS}.
     */
    private static double columnTipTopSs(
        ElementColumn column, ToDoubleFunction<? super ElementColumn> obstacleTopSs) {
        var staffTopCeilingYSs = StackingUtils.STAFF_TOP_Y_SS - TUPLET_STAFF_PADDING_SS;
        return Math.min(obstacleTopSs.applyAsDouble(column), staffTopCeilingYSs);
    }

    /** A beamed up-stem's natural stem tip sits below the beam; the beam's outer edge is the real ceiling. */
    double rawObstacleTopSs(ElementColumn column) {
        var element = column.getElement();
        var naturalTopYSs = column.getAbsoluteTopYSs();

        if (column.beaming() == Beaming.BEAMED
            && element instanceof NoteElement note
            && note.direction().isUp()) {
            return StackingUtils.resolvedStemTopYSs(context.getBuilder(), note, naturalTopYSs);
        }

        return naturalTopYSs;
    }

    /** Only staccato and accent count; the outer scripts (trill, fermata) float outside the bracket. */
    private List<ScriptObstacle> scriptObstacles(ElementColumn column) {
        var obstacles = new ArrayList<ScriptObstacle>();

        // Only a note carries scripts.
        if (!(column.getElement() instanceof NoteElement note)) {
            return obstacles;
        }

        var builder = context.getBuilder();

        for (var type : List.of(ArticulationType.STACCATO, ArticulationType.ACCENT)) {
            var articulation = note.findArticulation(type);

            if (articulation == null) {
                continue;
            }

            var layout = builder.decorationLayouts().getDecorationLayout(articulation);

            if (layout == null) {
                continue;
            }

            obstacles.add(new ScriptObstacle(layout.ySs(), layout.xSs() + layout.widthSs() / 2.0));
        }

        return obstacles;
    }

    record ScriptObstacle(double topYSs, double centerXSs) {}

    /** Leading, trailing, and interior rests are all excluded. */
    static List<ElementColumn> collectNonRestSpannedColumns(
        Line line,
        Span<?> element,
        Map<StaffElement, ElementColumn> columnsByElement) {

        var columns = new ArrayList<ElementColumn>();
        var range = element.range();

        for (var i = range.begin(); i <= range.end(); i++) {
            var column = columnsByElement.get(line.getElement(i));

            if (column == null || column.isRest()) {
                continue;
            }

            columns.add(column);
        }

        return columns;
    }

    /**
     * Layout Y is up-negative while {@code getStaffPosition()} increases downward. The slope is kept
     * only when the tip rise and the staff rise agree in sign; a disagreement (or a flat staff or
     * contour) collapses it to 0. The result is expressed over {@code widthSs} so the clearance and
     * render stages share one run basis.
     *
     * @param nonRestColumns ordered spanned columns with rests already excluded
     */
    static double computeTupletSlopeDySs(
        List<ElementColumn> nonRestColumns,
        double widthSs,
        ToDoubleFunction<? super ElementColumn> obstacleTopSs) {

        // Defensive early-out: an all-rest span or a single non-rest column has no contour and a
        // zero tip run (which would divide to NaN). Such tuplets are also rejected on load.
        if (nonRestColumns.size() < 2) {
            return 0.0;
        }

        var leftEnd = nonRestColumns.getFirst();
        var rightEnd = nonRestColumns.getLast();

        // Step 1: contour slope = outer-tip rise spread over LilyPond's head/stem-edge run
        // (x0 = left endpoint's left edge, x1 = right endpoint's right edge). This run is wider
        // than the note-center span, so the slope is proportionally shallower than the raw tips.
        // Each outer endpoint floors to the staff-top ceiling first, mirroring LilyPond's
        // rv.unite(staff) / lv.unite(staff): an endpoint whose own tip sits inside the staff
        // contributes the staff edge, not its tip. Layout Y is up-negative, so "higher of tip /
        // staff edge" is Math.min. Shares columnTipTopSs's ceiling with the clearance path so the
        // two never drift apart.
        var leftTopYSs = columnTipTopSs(leftEnd, obstacleTopSs);
        var rightTopYSs = columnTipTopSs(rightEnd, obstacleTopSs);
        var tipRise = rightTopYSs - leftTopYSs;
        var edgeRunSs =
            boundEdgeXSs(rightEnd, SpanEnd.RIGHT) - boundEdgeXSs(leftEnd, SpanEnd.LEFT);
        var slope = tipRise / edgeRunSs;

        // Step 2: contour veto (chord-free). A valid ascending/descending contour has tipRise and
        // staffRise pointing the same way; if they disagree — or either is flat — force flat.
        var staffRise = ElementSeat.seatSp(rightEnd.getElement()).value()
            - ElementSeat.seatSp(leftEnd.getElement()).value();

        if (Math.signum(tipRise) != Math.signum((double) staffRise)) {
            slope = 0.0;
        }

        // Step 3: slope clamp.
        if (Math.abs(slope) > TupletMetrics.MAX_SLOPE_FACTOR) {
            slope = Math.copySign(TupletMetrics.MAX_SLOPE_FACTOR, slope);
        }

        return slope * widthSs;
    }

    /**
     * Mirrors LilyPond's {@code get_x_bound_item}: an up-stem is bounded by its stem edge, any other
     * column by its note-head outer edge; ledger lines, dots, accidentals and flags are excluded.
     *
     * @param column the outer non-rest column
     */
    private static double boundEdgeXSs(ElementColumn column, SpanEnd end) {
        var headLeftXSs = column.getXSs();
        var headRightXSs = headLeftXSs + column.getNoteheadWidthSs();

        // Right endpoint contributes its RIGHT edge. An up-stem's right edge coincides with the
        // head's right edge, so the stem-bound and head-bound cases both resolve to headRight.
        if (end == SpanEnd.RIGHT) {
            return headRightXSs;
        }

        // Left endpoint contributes its LEFT edge. An up-stem is bounded by the stem, whose left
        // edge sits one stem thickness inside the head's right edge; any other column (down-stem
        // or stemless) by the head's outer (left) edge.
        var isUpStem = column.getElement() instanceof NoteElement note
            && note.getType().isNoteWithStem()
            && note.direction().isUp();
        return isUpStem ? headRightXSs - StemMetrics.thicknessSs() : headLeftXSs;
    }

    /**
     * A group of two or more is placed in two passes: every member's own demand first, then all
     * placed against the most outward. A member that reserved before its neighbours were measured
     * would push them off the shared line.
     */
    private void stackDynamicsGroup(
        DynamicGrouper.Group group,
        Line line,
        Map<StaffElement, ElementColumn> columnsByElement) {

        var members = group.members();

        if (members.size() == 1) {
            stackSoloMember(members.getFirst(), line, columnsByElement);
            return;
        }

        // Pass 1: resolve each member's footprint and the reference line it would demand alone,
        // reserving nothing.
        var placements = new ArrayList<DynamicPlacement<?>>(members.size());
        var groupReferenceYSs = Double.POSITIVE_INFINITY;

        for (var member : members) {
            var placement = resolvePlacement(member, line, columnsByElement);

            if (placement == null) {
                continue;
            }

            placements.add(placement);
            // All dynamics sit above the staff, so the most outward demand is the smallest Y.
            groupReferenceYSs = Math.min(groupReferenceYSs, placement.referenceYSs());
        }

        // Pass 2: place and reserve left to right, each member's inner edge derived from the
        // shared line by inverting the relation that pass 1 computes for its demand.
        for (var placement : placements) {
            var edge = new InnerEdge(Direction.UP,
                groupReferenceYSs + placement.innerEdgeOffsetSs(), placement.marginSs());

            structuralLayer.placeAtInnerEdge(
                placement.element(), Outline.flat(placement.footprint()), edge);
        }
    }

    private void stackSoloMember(
        DynamicGrouper.Member member,
        Line line,
        Map<StaffElement, ElementColumn> columnsByElement) {

        if (member instanceof DynamicGrouper.Member.OfHairpin hairpinMember) {
            stackHairpin(hairpinMember.hairpin(), line, columnsByElement);
        } else if (member instanceof DynamicGrouper.Member.OfDynamic dynamicMember) {
            var column = columnsByElement.get(line.getElement(dynamicMember.elementIndex()));

            if (column != null) {
                stackTextDynamics(column);
            }
        }
    }

    /**
     * Null when the member is a text dynamic whose element has no column. Reserves nothing.
     */
    private @Nullable DynamicPlacement<?> resolvePlacement(
        DynamicGrouper.Member member,
        Line line,
        Map<StaffElement, ElementColumn> columnsByElement) {

        if (member instanceof DynamicGrouper.Member.OfHairpin hairpinMember) {
            return resolveHairpinPlacement(hairpinMember.hairpin(), line, columnsByElement);
        }

        if (member instanceof DynamicGrouper.Member.OfDynamic dynamicMember) {
            var column = columnsByElement.get(line.getElement(dynamicMember.elementIndex()));

            if (column == null) {
                return null;
            }

            return resolveDynamicPlacement(dynamicMember.dynamic(), column);
        }

        return null;
    }

    /** The wedge is centered on the group's line, so its inner edge sits half its height below it. */
    private DynamicPlacement<Hairpin> resolveHairpinPlacement(
        Hairpin hairpin,
        Line line,
        Map<StaffElement, ElementColumn> columnsByElement) {

        var endpoints = HairpinEndpoints.compute(hairpin, line, columnsByElement);
        var footprint = hairpinFootprint(endpoints);
        var innerEdgeOffsetSs = hairpin.intrinsicHeightSs() / 2;
        var innerEdgeYSs =
            structuralLayer.anchoredInnerEdgeYSs(footprint, hairpinAnchor(endpoints));

        return new DynamicPlacement<>(hairpin, footprint, HAIRPIN_MARGIN_SS,
            innerEdgeOffsetSs, innerEdgeYSs - innerEdgeOffsetSs);
    }

    private static Footprint hairpinFootprint(HairpinEndpoints.Endpoints endpoints) {
        return new Footprint(endpoints.x1Ss(), endpoints.widthSs());
    }

    private static StaffAnchor hairpinAnchor(HairpinEndpoints.Endpoints endpoints) {
        var staffPosition = ElementSeat.seatSp(endpoints.spanColumns().anchor()).value();

        return new StaffAnchor(Direction.UP, staffPosition, HAIRPIN_MARGIN_SS);
    }

    /** The glyph's baseline sits {@link #DYNAMIC_TEXT_BASELINE_OFFSET_SS} below the group's line, not centered on it. */
    private DynamicPlacement<DynamicAttachment> resolveDynamicPlacement(
        DynamicAttachment dynamic, ElementColumn column) {

        var footprint = dynamicFootprint(column, dynamic);
        var innerEdgeOffsetSs = DYNAMIC_TEXT_BASELINE_OFFSET_SS + dynamic.getContentBottomSs();
        var innerEdgeYSs =
            structuralLayer.clampedInnerEdgeYSs(Outline.flat(footprint), DYNAMIC_SIDE);

        return new DynamicPlacement<>(dynamic, footprint, DYNAMIC_SIDE.paddingSs(),
            innerEdgeOffsetSs, innerEdgeYSs - innerEdgeOffsetSs);
    }

    private static Footprint dynamicFootprint(ElementColumn column, DynamicAttachment dynamic) {
        return new Footprint(HairpinEndpoints.dynamicLeftEdgeSs(column, dynamic),
            HairpinEndpoints.dynamicWidthSs(dynamic));
    }

    /**
     * {@code innerEdgeOffsetSs} is how far the member's inner edge sits below the group's reference
     * line. The member's height is always the element's own, never a component.
     */
    private record DynamicPlacement<E extends LineElement & IntrinsicHeight>(
        E element,
        Footprint footprint,
        double marginSs,
        double innerEdgeOffsetSs,
        double referenceYSs) {
    }

    /** The horizontal endpoints come from {@link HairpinEndpoints}, not from the bound columns' positions. */
    private void stackHairpin(
        Hairpin hairpin,
        Line line,
        Map<StaffElement, ElementColumn> columnsByElement) {

        var endpoints = HairpinEndpoints.compute(hairpin, line, columnsByElement);

        structuralLayer.stackAtAnchor(
            hairpin, hairpinFootprint(endpoints), hairpinAnchor(endpoints));
    }

    private void stackTextDynamics(ElementColumn column) {
        var note = column.getElement();
        var dynamic = note.findAttachment(DynamicAttachment.class);

        if (dynamic == null) {
            return;
        }

        structuralLayer.placeAndReserveClamped(
            dynamic, Outline.flat(dynamicFootprint(column, dynamic)), DYNAMIC_SIDE);
    }

    private void stackEndings(
        Line line,
        Map<StaffElement, ElementColumn> columnsByElement,
        LayoutResultBuilder builder) {

        for (var ending : line.spanRegistry().findSpans(Ending.class)) {
            var brackets = EndingBracketGeometry.computeBracketRanges(
                ending,
                line,
                element -> {
                    var column = columnsByElement.get(element);

                    if (column == null) {
                        throw new IllegalStateException("no column for " + element);
                    }

                    return column;
                });

            // The ending's anchor X is the first bracket's left edge, and every region is placed
            // from it.
            var anchorXSs = brackets.first().x1Ss();
            var regions = new ArrayList<CollisionRegion>();

            for (var slot : BracketRange.Bracket.values()) {
                var bracket = brackets.get(slot);
                regions.addAll(EndingBracketGeometry.computeCollisionRegions(
                    bracket, slot, bracket.x1Ss() - anchorXSs));
            }

            var widthSs = brackets.second().x2Ss() - anchorXSs;
            var staffPosition = ElementSeat.seatSp(ending.getAnchorElement()).value();

            var placed = structuralLayer.stackAboveWithRegions(
                regions, new Footprint(anchorXSs, widthSs), ENDING_MARGIN_SS, staffPosition);

            builder.decorationLayouts().putEnding(
                ending, LayoutResult.DecorationLayout.Volta.of(placed, brackets));
        }
    }

}
