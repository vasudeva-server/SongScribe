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
import songscribe.dom.Span;
import songscribe.dom.StaffElement;
import songscribe.dom.StaffElement.Direction;
import songscribe.dom.Tuplet;
import songscribe.engraving.StemMetrics;
import songscribe.layout.ElementColumn;
import songscribe.layout.EndingBracketGeometry;
import songscribe.layout.HairpinEndpoints;
import songscribe.layout.LayoutResult;
import songscribe.layout.LayoutResultBuilder;
import songscribe.layout.NoteGeometry;
import songscribe.layout.StaffExtents;

import static songscribe.layout.stacking.StackingUtils.stackAbove;
import static songscribe.layout.stacking.StackingUtils.stackAboveWithRegions;

/**
 * Stacks structural-tier decorations (tier 3): tuplets, hairpins, text dynamics,
 * and endings (volta brackets).
 * <p>
 * Operates on the structural {@link StaffExtents} layer, which starts as a copy
 * of the note-attached layer's top extents. All calculations are in staff-space units.
 */
public class StructuralStacker {

    /**
     * Vertical margin between hairpins and elements below during stacking.
     */
    public static final double HAIRPIN_MARGIN_SS = 1.0;  // 8px
    /**
     * Margin from reference point to ending bracket
     */
    public static final double ENDING_MARGIN_SS = 1.0;  // 8px
    /**
     * Tuplet-bracket padding: the gap from the worst note tip to the bracket <em>line</em>, matching
     * LilyPond's {@code TupletBracket.padding} (measured tip → line in {@code calc_position_and_height},
     * {@code *offset += padding * dir}). The vertical arms hang {@link Tuplet#BRACKET_ARM_HEIGHT_SS}
     * down from the line into this gap; they do not push the line up.
     */
    public static final double TUPLET_BRACKET_PADDING_SS = 1.1;

    /**
     * LilyPond {@code TupletBracket.staff-padding}: the outer endpoints floor to the staff-line
     * centerline widened by this before the slope rise is taken (tuplet-bracket.cc, {@code staff.widen}).
     */
    public static final double TUPLET_STAFF_PADDING_SS = 0.25;

    /**
     * The margin the tuplet placement needs: it places the reserved box's <em>bottom</em> (the arm
     * bottom) this far above the tip, whereas LilyPond's padding is to the line. The line sits
     * {@link Tuplet#BRACKET_ARM_HEIGHT_SS} above the arm bottom, so subtracting the arm height lands
     * the line exactly {@link #TUPLET_BRACKET_PADDING_SS} above the tip.
     */
    public static final double TUPLET_ARM_MARGIN_SS =
        TUPLET_BRACKET_PADDING_SS - Tuplet.BRACKET_ARM_HEIGHT_SS;

    /**
     * The margin a number-only tuplet needs. LilyPond draws no bracket but still positions the number
     * from the bracket line: with no knee, {@code Tuplet_number::calc_y_offset} returns the bracket's
     * {@code positions} midpoint, so the number's ink <em>center</em> — not its bottom — lands
     * {@link #TUPLET_BRACKET_PADDING_SS} above the tip. Placement reserves the box bottom, which for a
     * number-only tuplet is the ink bottom, half an ink height below that center (issue #653).
     */
    public static final double TUPLET_NUMBER_MARGIN_SS =
        TUPLET_BRACKET_PADDING_SS - Tuplet.bracketLineOffsetSs();

    /**
     * How far below a dynamics group's shared reference line a text dynamic's glyph baseline sits,
     * from LilyPond's {@code DynamicText.Y-offset} ({@code scale-by-font-size -0.6}, commented
     * "center on an 'm'"). Placing the baseline here puts the glyph's x-height center on the line,
     * which is stable across glyphs whose ascenders and descenders differ.
     * <p>
     * Not to be confused with {@link NoteAttachedStacker#DYNAMIC_PADDING_SS}, which happens to
     * share this value but comes from {@code DynamicLineSpanner.padding} and means something else
     * entirely. Do not collapse the two.
     */
    public static final double DYNAMIC_TEXT_BASELINE_OFFSET_SS = 0.6;

    private final StackingContext context;
    private final StaffExtents structuralExtents;

    public StructuralStacker(StackingContext context, StaffExtents structuralExtents) {
        this.context = context;
        this.structuralExtents = structuralExtents;
    }

    /**
     * Stacks the tuplet brackets (tier 3a).
     * <p>
     * Split from {@link #stackRemaining} because the outside-priority scripts (fermata, trill) stack
     * between the two: they float above the bracket, so it must reserve its footprint first. See
     * {@link VerticalStackingCalculator} for the full tier order.
     */
    public void stackTuplets() {
        stackTuplets(context.getLine(), context.getColumnsByElement(), context.getBuilder());
    }

    /**
     * Stacks the rest of the structural tier in order: hairpins and text dynamics, then endings.
     * <p>
     * Hairpins and text dynamics are placed together, group by group, rather than as two separate
     * sweeps: a hairpin and the dynamic beside it share one reference line (see
     * {@link DynamicGrouper}), which is impossible when the second sweep stacks outside whatever
     * the first one reserved.
     */
    public void stackRemaining() {
        var line = context.getLine();
        var columnsByElement = context.getColumnsByElement();
        var builder = context.getBuilder();

        // Tier 3b/3c: hairpins and text dynamics, in left-to-right group order
        for (var group : DynamicGrouper.group(line)) {
            stackDynamicsGroup(group, line, columnsByElement, builder);
        }

        // Tier 3d: Volta brackets (endings)
        stackEndings(line, columnsByElement, builder);
    }

    /**
     * Stacks all tuplet brackets for the line.
     */
    private void stackTuplets(
        Line line,
        Map<StaffElement, ElementColumn> columnsByElement,
        LayoutResultBuilder builder) {

        for (var tuplet : line.findSpans(Tuplet.class)) {
            var spanColumns = ElementColumn.resolveSpan(tuplet, columnsByElement);

            if (spanColumns == null) {
                continue;
            }

            var anchor = spanColumns.anchor();
            var endNote = spanColumns.end();
            var anchorColumn = spanColumns.anchorColumn();
            var endColumn = spanColumns.endColumn();

            var numberOnly = tuplet.isNumberOnly(line);
            var heightSs = numberOnly ? Tuplet.numberOnlyHeightSs() : Tuplet.bracketedHeightSs();
            var marginSs = numberOnly ? TUPLET_NUMBER_MARGIN_SS : TUPLET_ARM_MARGIN_SS;
            var anchorXSs = anchorColumn.getXSs();
            var widthSs = tuplet.getSpanWidthSs(anchorXSs, endColumn.getXSs());

            var nonRestColumns = collectNonRestSpannedColumns(line, tuplet, columnsByElement);
            var dySs = computeTupletSlopeDySs(nonRestColumns, anchorXSs, widthSs,
                this::rawObstacleTopSs);

            // The visual bracket arms extend past the outer note columns; the clearance folds each
            // outer obstacle in again at its arm's X so the extended, sloped arm — not just the line
            // over the note — stays clear. Compute the arm X positions the same way the renderer does
            // (shared Tuplet helpers) so reserved and drawn geometry match.
            var endXSs = anchorXSs + widthSs;
            var anchorStemUp = anchor.getDirection().isUp();
            var leftArmXSs = Tuplet.bracketLeftEdgeXSs(anchorXSs, anchorColumn.hasStem(),
                anchorStemUp, StemMetrics.THICKNESS_SS, anchorColumn.getNoteheadWidthSs());
            var rightArmXSs = Tuplet.bracketRightEdgeXSs(endXSs, endColumn.getNoteheadWidthSs());

            // Sloped clearance: find the left-endpoint ceiling that keeps the tilted bracket clear of
            // every non-rest tip. The clearance has already folded in every spanned tip along the
            // slope, so the box is placed against that ceiling verbatim — re-combining with the flat
            // note skyline (yGetExpanded) would re-flatten an ascending bracket up to its peak,
            // defeating the slope (issue #509). placeAndReserve's geometry inlined: the box bottom
            // (bracketed: the arm bottom; number-only: the number's ink bottom) sits marginSs above
            // the tip, so the box top is a further heightSs out. Either way the box top lands at
            // TUPLET_BRACKET_PADDING_SS plus half an ink height above the tip — the number's ink top.
            var leftYSs = computeTupletClearanceLeftYSs(
                nonRestColumns, this::scriptObstacles, dySs, anchorXSs, widthSs,
                leftArmXSs, rightArmXSs, this::rawObstacleTopSs);
            var finalLeftYSs = leftYSs - marginSs - heightSs;

            // Reserve the bracket's true sloped outer edge so later structural elements and line
            // sizing see the tilted silhouette rather than a flat approximation of it.
            structuralExtents.ySetSloped(true, anchorXSs, endXSs, finalLeftYSs, finalLeftYSs + dySs);

            // Record the sloped layout (dySs folded in), keeping the resolved left-end Y as ySs.
            builder.putDecorationLayout(tuplet,
                new LayoutResult.DecorationLayout.Plain(
                    anchorXSs, finalLeftYSs, dySs, widthSs, heightSs, marginSs));
        }
    }

    /**
     * Computes the tuplet bracket's left-endpoint ceiling ({@code leftYSs}) so the tilted bracket
     * line clears every non-rest column's actual top ({@code getAbsoluteTopYSs()}). Pure and
     * package-visible for independent unit testing.
     * <p>
     * The bracket floats above the real tips, exactly as LilyPond does — it never shortens a stem to
     * meet the line. Unbeamed forced stems are already shortened bracket-independently (see
     * {@link NoteGeometry#forcedShorteningSs}) and beamed stems already carry their quanted length,
     * so their reported tips are the final ones and the bracket sits low on them without any
     * trimming; a genuinely natural, full-length interior stem floats the bracket above it.
     * <p>
     * A flat box that clears only the tallest tip can still dip below an intermediate one once
     * tilted, so each tip is projected back to the left endpoint along the slope and the highest
     * (smallest Y) projection wins:
     * <pre>
     *   leftYSs = min over columns ( getAbsoluteTopYSs() − slope · (xᵢ − anchorXSs) )
     * </pre>
     * This is a raw ceiling with no margin baked in — the caller applies the single margin gap;
     * baking a margin in here too would double it.
     * <p>
     * The {@code min} picks the binding tip (up = smaller Y). The right endpoint is
     * {@code leftYSs + dySs}. With an empty tip list (all-rest span) there is nothing to clear, so
     * the ceiling collapses to {@link Double#POSITIVE_INFINITY} and the caller's extents floor wins.
     * <p>
     * <b>Arm reach.</b> The visible bracket does not stop at the outer note columns: its vertical
     * arms are drawn at {@code leftArmXSs}/{@code rightArmXSs}, which extend a notehead-plus-extension
     * beyond the anchor/end columns. On a sloped bracket the arm on the downhill side keeps
     * descending past its column, so a line that merely clears the note over its own X can still let
     * the extended arm dip into that same note (issue #509). LilyPond avoids this by fitting the
     * whole bracket to its visual X-bounds — the arm edges — rather than the note span
     * (tuplet-bracket.cc, {@code calc_position_and_height}). We mirror that by folding the anchor and
     * end <b>notehead tips</b> in a second time, projected from the arm X positions, so the extended
     * arms clear the noteheads by the same margin the interior gets. The arm-edge fold deliberately
     * omits articulation: LilyPond folds scripts only at their own center X, so scripts are cleared
     * by the interior loop (which folds each notehead tip at its column's X and each script at the
     * script's own center X), never projected from the arm edge — projecting a script from the
     * downhill arm X over-lifts the trailing arm.
     * <p>
     * <b>Script center X.</b> A script's clearance point is folded at its own center X, not the
     * column reference X, matching LilyPond's {@code script_x.center()}. The column reference X is the
     * notehead's left edge, so on a descending bracket projecting a script from there instead of its
     * center under-clears it — and drops the downhill arm — by {@code slope · (centerX − refX)}.
     *
     * @param nonRestColumns ordered spanned columns with rests already excluded
     * @param scriptObstacles the above-staff scripts (accent/staccato) each column presents, each with
     *                        its top edge and center X (empty when none)
     * @param dySs           bracket-line rise over {@code widthSs} (0 when flat/vetoed)
     * @param anchorXSs      X of the anchor (left) element column — the slope origin
     * @param widthSs        anchor→end element run the slope is expressed over
     * @param leftArmXSs     X of the left arm (visual bracket left edge)
     * @param rightArmXSs    X of the right arm (visual bracket right edge)
     * @return the left-endpoint ceiling {@code leftYSs} in layout-Y staff-space units
     */
    static double computeTupletClearanceLeftYSs(
        List<ElementColumn> nonRestColumns,
        Function<? super ElementColumn, ? extends List<ScriptObstacle>> scriptObstacles,
        double dySs,
        double anchorXSs,
        double widthSs,
        double leftArmXSs,
        double rightArmXSs,
        ToDoubleFunction<? super ElementColumn> obstacleTopSs) {

        var slope = dySs / widthSs;
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
            leftYSs = Math.min(leftYSs, anchorTipTopYSs - slope * (leftArmXSs - anchorXSs));
            leftYSs = Math.min(leftYSs, endTipTopYSs - slope * (rightArmXSs - anchorXSs));
        }

        return leftYSs;
    }

    /**
     * The obstacle top a column presents to the bracket, staff-top-clamped, WITHOUT any
     * articulation. Used both by the interior tip fold and the arm-reach fold so a script is never
     * projected from the arm edge — LilyPond folds scripts only at their own center X
     * (tuplet-bracket.cc, avoid-scripts). The raw top comes from {@code obstacleTopSs} so a beamed
     * up-stem column can report its beam edge rather than its tucked-in stem tip (issue #556).
     * <p>
     * Staff-top clamp: a tip below the top staff line raises to the staff-padding-widened staff top
     * (never lower), so the bracket stays at least the margin above the staff. The ceiling is the
     * staff-line centerline widened by {@link #TUPLET_STAFF_PADDING_SS}, matching LilyPond's
     * {@code staff.widen(0.25)} and the slope path's floor in {@link #computeTupletSlopeDySs} — not
     * the bare centerline, which would let the bracket sit 0.25ss too low against within-staff notes.
     */
    private static double columnTipTopSs(
        ElementColumn column, ToDoubleFunction<? super ElementColumn> obstacleTopSs) {
        var staffTopCeilingYSs = StackingUtils.STAFF_TOP_Y_SS - TUPLET_STAFF_PADDING_SS;
        return Math.min(obstacleTopSs.applyAsDouble(column), staffTopCeilingYSs);
    }

    /**
     * The raw (unclamped) obstacle top a column presents to the always-above tuplet: for a beamed
     * up-stem column the beam's outer (top) edge, otherwise the column's natural stem/head tip.
     * <p>
     * A beamed up-stem's rendered stem is shortened so it tucks inside the beam and never protrudes
     * past the beam's outer edge, so the column's {@link ElementColumn#getAbsoluteTopYSs()} — the
     * natural stem tip — sits <em>below</em> the beam the tuplet must actually clear. Measuring to it
     * lets the number/bracket drop onto the beam (issue #556); the beam's outer edge is the real
     * ceiling. The beam layout's {@link LayoutResult.StemLayout#topYSs()} is that outer edge for an
     * up-stem beam, in the same staff-Y space as {@code getAbsoluteTopYSs()}. Every other column
     * (unbeamed, or a down-stem beam whose beam sits below the noteheads) keeps its natural tip.
     */
    double rawObstacleTopSs(ElementColumn column) {
        var element = column.getElement();
        var naturalTopYSs = column.getAbsoluteTopYSs();

        if (column.isBeamed() && element.getDirection().isUp()) {
            return StackingUtils.resolvedStemTopYSs(context.getBuilder(), element, naturalTopYSs);
        }

        return naturalTopYSs;
    }

    /**
     * The above-staff scripts (staccato/accent) a column presents to the bracket, each as a
     * {@link ScriptObstacle} carrying its top edge and center X. Empty when the note has none, so the
     * column is then governed solely by its own tip.
     * <p>
     * Only staccato and accent count: these are the close, priority-less scripts LilyPond's tuplet
     * bracket avoids (tuplet-bracket.cc, "avoid-scripts"). Their placed positions are read straight
     * from the note-attached tier's {@link LayoutResult.DecorationLayout}s — written into the shared
     * builder before this tier runs — and the script's outer (top) edge is the ceiling the bracket
     * clears, folded at the script's center X. Noteheads, ties, accidentals, and the outer scripts
     * trill/fermata (which float outside the bracket) are ignored.
     */
    private List<ScriptObstacle> scriptObstacles(ElementColumn column) {
        var note = column.getElement();
        var builder = context.getBuilder();
        var obstacles = new ArrayList<ScriptObstacle>();

        for (var type : List.of(ArticulationType.STACCATO, ArticulationType.ACCENT)) {
            var articulation = note.findArticulation(type);

            if (articulation == null) {
                continue;
            }

            var layout = builder.getDecorationLayout(articulation);

            if (layout == null) {
                continue;
            }

            obstacles.add(new ScriptObstacle(layout.ySs(), layout.xSs() + layout.widthSs() / 2.0));
        }

        return obstacles;
    }

    /**
     * A script's clearance point for a tuplet bracket: its outer (top) edge Y and the center X at
     * which LilyPond folds it into the bracket's point set (tuplet-bracket.cc, {@code script_x.center()}).
     */
    record ScriptObstacle(double topYSs, double centerXSs) {}

    /**
     * Collects the spanned {@link ElementColumn}s of a span in reading order, skipping
     * rest columns. Leading, trailing, and interior rests are all excluded so the resulting list
     * holds only pitched tips — the outer entries are the non-rest end columns (mirroring
     * LilyPond's {@code get_bounds}).
     */
    static List<ElementColumn> collectNonRestSpannedColumns(
        Line line,
        Span element,
        Map<StaffElement, ElementColumn> columnsByElement) {

        var columns = new ArrayList<ElementColumn>();
        var startIndex = element.getAnchorElementIndex();
        var endIndex = element.getEndElementIndex();

        for (var i = startIndex; i <= endIndex; i++) {
            var column = columnsByElement.get(line.getElement(i));

            if (column == null || column.isRest()) {
                continue;
            }

            columns.add(column);
        }

        return columns;
    }

    /**
     * Computes the tuplet bracket's slope rise ({@code dySs}) from the note contour, following
     * LilyPond's raw-rise → contour-veto → slope-clamp pipeline. Pure and package-visible for
     * independent unit testing.
     * <p>
     * Sign convention: layout Y is up-negative while {@code getStaffPosition()} increases downward,
     * so a higher right-end note gives BOTH a negative tipRise (smaller top Y) AND a negative
     * staffRise (smaller staff position) — the two agree in sign. The veto keeps the slope only when
     * they do; a disagreement (or a flat staff/contour) collapses the slope to 0.
     * <p>
     * The slope is the rise between the outer endpoints' <em>staff-floored</em> tops (each floored to
     * the staff-top centerline widened by {@link #TUPLET_STAFF_PADDING_SS}, mirroring LilyPond's
     * {@code unite(staff)} — an endpoint inside the staff contributes the staff edge, not its tip)
     * spread over LilyPond's head/stem-edge run ({@code x0…x1}, see {@link #boundEdgeXSs}), then
     * re-expressed as a rise over {@code widthSs} so the clearance and render stages share one run
     * basis. Because the edge run is wider than the note-center span, the drawn slope is
     * proportionally shallower than the raw tip contour — matching {@code tuplet-bracket.cc}. The
     * residual gap vs LilyPond is the different horizontal spacing engine, not a bug.
     *
     * @param nonRestColumns ordered spanned columns with rests already excluded
     * @param anchorXSs      X of the anchor (left) element column — the run origin shared with the
     *                       clearance and render stages
     * @param widthSs        anchor→end element run the returned rise is expressed over
     * @return the slope rise {@code dySs} = slope × {@code widthSs}; 0.0 when flat or vetoed
     */
    static double computeTupletSlopeDySs(
        List<ElementColumn> nonRestColumns,
        double anchorXSs,
        double widthSs,
        ToDoubleFunction<? super ElementColumn> obstacleTopSs) {

        // Defensive early-out: an all-rest span or a single non-rest column has no contour and a
        // zero tip run (which would divide to NaN). Such tuplets are also rejected on load (#518).
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
        var edgeRunSs = boundEdgeXSs(rightEnd, true) - boundEdgeXSs(leftEnd, false);
        var slope = tipRise / edgeRunSs;

        // Step 2: contour veto (chord-free). A valid ascending/descending contour has tipRise and
        // staffRise pointing the same way; if they disagree — or either is flat — force flat.
        var staffRise = rightEnd.getElement().getStaffPosition() - leftEnd.getElement().getStaffPosition();

        if (Math.signum(tipRise) != Math.signum((double) staffRise)) {
            slope = 0.0;
        }

        // Step 3: slope clamp.
        if (Math.abs(slope) > Tuplet.MAX_SLOPE_FACTOR) {
            slope = Math.copySign(Tuplet.MAX_SLOPE_FACTOR, slope);
        }

        return slope * widthSs;
    }

    /**
     * Returns the bracket bound edge X for an outer endpoint column, mirroring LilyPond's
     * {@code get_x_bound_item}: a note whose stem points toward the (always-UP) bracket — an
     * up-stem — is bounded by its stem edge; any other column by its note-head outer edge. The
     * bound is head/stem only, so ledger lines, dots, accidentals, and flags are excluded
     * ({@code note-column.cc}). This is {@code TupletRenderer}'s arm-edge geometry without the
     * visual {@code ARM_EXTENSION_SS} overhang, which is not a bound edge.
     *
     * @param column       the outer non-rest column
     * @param rightEndpoint true for the right endpoint (contributes {@code x1}, its RIGHT edge);
     *                      false for the left endpoint (contributes {@code x0}, its LEFT edge)
     * @return the bound edge X in ss
     */
    private static double boundEdgeXSs(ElementColumn column, boolean rightEndpoint) {
        var headLeftXSs = column.getXSs();
        var headRightXSs = headLeftXSs + column.getNoteheadWidthSs();

        // Right endpoint contributes its RIGHT edge. An up-stem's right edge coincides with the
        // head's right edge, so the stem-bound and head-bound cases both resolve to headRight.
        if (rightEndpoint) {
            return headRightXSs;
        }

        // Left endpoint contributes its LEFT edge. An up-stem is bounded by the stem, whose left
        // edge sits one stem thickness inside the head's right edge; any other column (down-stem
        // or stemless) by the head's outer (left) edge.
        var element = column.getElement();
        var isUpStem = element.getType().isNoteWithStem() && element.getDirection().isUp();
        return isUpStem ? headRightXSs - StemMetrics.THICKNESS_SS : headLeftXSs;
    }

    /**
     * Places one {@link DynamicGrouper.Group}.
     * <p>
     * A group of one has no baseline to share, so it goes through the same single-element path it
     * always did. A group of two or more is placed in two passes: every member's own demand is
     * computed first, then all of them are placed against the most outward of those demands. The
     * split is the whole point — a member that reserved its footprint before its neighbours had
     * been measured would push those neighbours off the shared line, which is exactly the defect
     * this replaces.
     */
    private void stackDynamicsGroup(
        DynamicGrouper.Group group,
        Line line,
        Map<StaffElement, ElementColumn> columnsByElement,
        LayoutResultBuilder builder) {

        var members = group.members();

        if (members.size() == 1) {
            stackSoloMember(members.getFirst(), line, columnsByElement, builder);
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
        // shared line by inverting the relation pass 1 used to compute its demand.
        for (var placement : placements) {
            var widthSs = placement.widthSs();

            StackingUtils.placeAtInnerEdge(Direction.UP, structuralExtents, placement.element(),
                placement.xSs(), widthSs, placement.heightSs(),
                groupReferenceYSs + placement.innerEdgeOffsetSs(),
                StaffExtents.Profile.flat(widthSs), placement.marginSs(), builder);
        }
    }

    /**
     * Places a group of one, which shares its line with nothing and so needs neither of the two
     * passes: a lone hairpin stacks at its anchored ceiling and a lone text dynamic at its clamped
     * support, exactly as each did before grouping existed.
     */
    private void stackSoloMember(
        DynamicGrouper.Member member,
        Line line,
        Map<StaffElement, ElementColumn> columnsByElement,
        LayoutResultBuilder builder) {

        if (member instanceof DynamicGrouper.Member.OfHairpin hairpinMember) {
            stackHairpin(hairpinMember.hairpin(), line, columnsByElement, builder);
        } else if (member instanceof DynamicGrouper.Member.OfDynamic dynamicMember) {
            var column = columnsByElement.get(line.getElement(dynamicMember.elementIndex()));

            if (column != null) {
                stackTextDynamics(column, builder);
            }
        }
    }

    /**
     * Resolves a group member's footprint and the reference line it demands on its own, or
     * {@code null} when the member has no geometry in this line (an endpoint that resolves to no
     * column). Reserves nothing.
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

    /**
     * A hairpin's placement. LilyPond centers the wedge's full Y-extent on the group's line
     * ({@code Hairpin.self-alignment-Y} is {@code CENTER}), so its inner edge — the bottom, above
     * the staff — sits half its height below the line.
     */
    private @Nullable DynamicPlacement<Hairpin> resolveHairpinPlacement(
        Hairpin hairpin,
        Line line,
        Map<StaffElement, ElementColumn> columnsByElement) {

        var endpoints = HairpinEndpoints.compute(hairpin, line, columnsByElement);

        if (endpoints == null) {
            return null;
        }

        var xSs = endpoints.x1Ss();
        var widthSs = endpoints.widthSs();
        var innerEdgeOffsetSs = hairpin.intrinsicHeightSs() / 2;
        var innerEdgeYSs = StackingUtils.anchoredInnerEdgeYSs(Direction.UP, structuralExtents,
            xSs, widthSs, HAIRPIN_MARGIN_SS, endpoints.spanColumns().anchor().getStaffPosition());

        return new DynamicPlacement<>(hairpin, xSs, widthSs, HAIRPIN_MARGIN_SS,
            innerEdgeOffsetSs, innerEdgeYSs - innerEdgeOffsetSs);
    }

    /**
     * A text dynamic's placement. Unlike a hairpin, the glyph is not centered on the group's line:
     * LilyPond drops its baseline {@link #DYNAMIC_TEXT_BASELINE_OFFSET_SS} below it, so the
     * x-height center rather than the bounding-box center lands on the line. Its inner edge — the
     * glyph's bottom — is therefore that drop plus however far the ink descends past the baseline.
     */
    private DynamicPlacement<DynamicAttachment> resolveDynamicPlacement(
        DynamicAttachment dynamic, ElementColumn column) {

        var xSs = HairpinEndpoints.dynamicLeftEdgeSs(column, dynamic);
        var widthSs = HairpinEndpoints.dynamicWidthSs(dynamic);
        var innerEdgeOffsetSs = DYNAMIC_TEXT_BASELINE_OFFSET_SS + dynamic.getContentBottomSs();
        var innerEdgeYSs = StackingUtils.clampedInnerEdgeYSs(Direction.UP, structuralExtents,
            xSs, StaffExtents.Profile.flat(widthSs),
            NoteAttachedStacker.DYNAMIC_PADDING_SS, NoteAttachedStacker.DYNAMIC_STAFF_PADDING_SS,
            StackingUtils.STRUCTURAL_HORIZONTAL_MARGIN_SS);

        return new DynamicPlacement<>(dynamic, xSs, widthSs,
            NoteAttachedStacker.DYNAMIC_PADDING_SS, innerEdgeOffsetSs,
            innerEdgeYSs - innerEdgeOffsetSs);
    }

    /**
     * One group member resolved for placement.
     * <p>
     * {@code innerEdgeOffsetSs} is what makes the two member kinds interchangeable in the passes:
     * it is how far the member's inner edge sits below the group's reference line, so pass 1 reads
     * {@code referenceYSs = innerEdgeYSs - innerEdgeOffsetSs} and pass 2 inverts it. Half the
     * height for a centered hairpin, the baseline drop plus the glyph's descent for a text dynamic.
     *
     * @param <E>          the member's element type, which must own its height so the placement
     *                     cannot be built with one the element disagrees with
     * @param referenceYSs the reference line this member would demand on its own
     */
    private record DynamicPlacement<E extends LineElement & IntrinsicHeight>(
        E element,
        double xSs, double widthSs, double marginSs,
        double innerEdgeOffsetSs, double referenceYSs) {

        /**
         * The height this member reserves, which is always its own. Derived rather than
         * stored so a placement cannot carry a height its element disagrees with.
         *
         * @return the member's intrinsic height, in staff spaces
         */
        double heightSs() {
            return element.intrinsicHeightSs();
        }
    }

    /**
     * Stacks one hairpin. Its horizontal endpoints come from {@link HairpinEndpoints}, not from the
     * anchor and end column positions the way a tuplet bracket's do: a hairpin's tips are pulled
     * back from adjacent text dynamics and neighbouring hairpins, so they are not a plain function
     * of where its two bound columns sit.
     */
    private void stackHairpin(
        Hairpin hairpin,
        Line line,
        Map<StaffElement, ElementColumn> columnsByElement,
        LayoutResultBuilder builder) {

        var endpoints = HairpinEndpoints.compute(hairpin, line, columnsByElement);

        if (endpoints == null) {
            return;
        }

        stackAbove(structuralExtents, hairpin,
            endpoints.x1Ss(), endpoints.widthSs(), HAIRPIN_MARGIN_SS,
            endpoints.spanColumns().anchor().getStaffPosition(), builder);
    }


    /**
     * Stacks text dynamics (DynamicAttachment) for the given column.
     * <p>
     * Positions text dynamics (pp, p, mp, mf, f, ff) in the structural tier
     * using collision detection against previously placed elements.
     */
    private void stackTextDynamics(
        ElementColumn column,
        LayoutResultBuilder builder) {

        var note = column.getElement();
        var dynamic = note.findAttachment(DynamicAttachment.class);

        if (dynamic == null) {
            return;
        }

        var contentWidthSs = HairpinEndpoints.dynamicWidthSs(dynamic);
        var centeredXSs = HairpinEndpoints.dynamicLeftEdgeSs(column, dynamic);
        StackingUtils.placeAndReserveClamped(Direction.UP, structuralExtents, dynamic,
            centeredXSs, contentWidthSs,
            StaffExtents.Profiles.flat(contentWidthSs),
            NoteAttachedStacker.DYNAMIC_PADDING_SS, NoteAttachedStacker.DYNAMIC_STAFF_PADDING_SS,
            StackingUtils.STRUCTURAL_HORIZONTAL_MARGIN_SS, builder);
    }

    /**
     * Stacks all endings (volta brackets) for the line.
     * <p>
     * Each ending computes its bracket ranges (first and second brackets split
     * at the REPEAT_RIGHT barline), then collision regions are created for each
     * bracket and stacked together as one element.
     */
    private void stackEndings(
        Line line,
        Map<StaffElement, ElementColumn> columnsByElement,
        LayoutResultBuilder builder) {

        for (var ending : line.findSpans(Ending.class)) {
            var anchor = ending.getAnchorElement();

            // Compute bracket ranges (stored on the Ending for renderer use)
            var brackets = EndingBracketGeometry.computeBracketRanges(
                ending,
                line,
                e -> {
                    var col = columnsByElement.get(e);

                    if (col == null) {
                        throw new IllegalStateException(
                            "No column for element");
                    }

                    return col;
                });

            if (brackets.isEmpty()) {
                continue;
            }

            // Element anchor X = first bracket's left edge
            var anchorXSs = brackets.getFirst().x1Ss();

            // Combine collision regions from all brackets
            var allRegions = new ArrayList<CollisionRegion>();

            for (var bracket : brackets) {
                var xBaseSs = bracket.x1Ss() - anchorXSs;
                allRegions.addAll(
                    EndingBracketGeometry.computeCollisionRegions(bracket, xBaseSs));
            }

            // Overall width = from first bracket start to last bracket end
            var widthSs = brackets.getLast().x2Ss() - anchorXSs;

            var staffPosition = anchor.getStaffPosition();

            stackAboveWithRegions(structuralExtents, ending, allRegions,
                anchorXSs, widthSs,
                ENDING_MARGIN_SS,
                staffPosition, builder, null);
        }
    }

}
