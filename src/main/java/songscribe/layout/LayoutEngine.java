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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import songscribe.dom.Beam;
import songscribe.dom.Direction;
import songscribe.dom.Dots;
import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.NoteElement;
import songscribe.dom.Slide;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.dom.Tie;
import songscribe.engraving.BeamMetrics;
import songscribe.engraving.EngravingConstants;
import songscribe.engraving.Staff;
import songscribe.engraving.StaffPosition;
import songscribe.engraving.StemMetrics;
import songscribe.font.DocumentFontsHolder;
import songscribe.layout.ElementColumn.TieColumns;
import songscribe.layout.stacking.VerticalStackingCalculator;
import songscribe.shape.BezierBow;
import songscribe.util.LogUtils;

/**
 * Orchestrates the complete layout pipeline for a staff line.
 * <p>
 * The LayoutEngine coordinates all layout calculators to produce a final {@link LayoutResult}
 * containing positioned elements ready for rendering. The pipeline executes in this order:
 * <ol>
 *   <li>{@link ElementColumnBuilder} - Creates note columns from the line's notes</li>
 *   <li>{@link HorizontalSpacingCalculator} - Builds one {@link Spring} per adjacent column pair</li>
 *   <li>{@link LyricLift} - Stretches the springs' rests so syllables clear each other</li>
 *   <li>{@link SpringSpacer} - Solves the spring chain to fit the staff width, compressing gaps
 *       in proportion to their compliance; a line that cannot fit even fully compressed is placed
 *       on its collision floors and marked
 *       {@link LayoutResult#overflowsStaffWidth() overflowing} instead of failing</li>
 *   <li>{@link VerticalStackingCalculator} - Positions elements vertically (layer-by-layer)</li>
 * </ol>
 * <p>
 * Layout always produces a result. There is no failure mode: every line can be placed, because
 * the collision floors are always a placement, even when they do not fit.
 * <p>
 * Usage:
 * <pre>{@code
 * var engine = new LayoutEngine(lyricRenderMetrics, staffRightMarginSs);
 * var result = engine.layout(line, hasLeadingLyricContinuation, TempoMark.STACKED);
 * }</pre>
 */
public class LayoutEngine {

    private static final Logger LOG = LoggerFactory.getLogger(LayoutEngine.class);

    static final double CLEF_X_POSITION_SS = 0.625;  // 5px

    // Beam geometry constants (staff-space units unless noted)
    // Not the drawn beam thickness (that is BeamMetrics.BEAM_THICKNESS_SS) — only
    // the base the raster-thickening factor scales.
    static final double BEAM_DEPTH_SS = 0.4;

    // The raster-thickening factor's bounds: an angled beam's depth grows by 3.3% to 8.8%.
    private static final double MIN_BEAM_THICKENING_FACTOR = 1.033;
    private static final double MAX_BEAM_THICKENING_FACTOR = 1.088;

    private static final double MIN_STEM_SS = StemMetrics.STEM_LENGTH_SS;

    /** Decimal format for the staff-space values in this class's beam debug output. */
    private static final String STEM_LOG_FORMAT = "%.3f";

    // Tie geometry constants (LilyPond slur_shape port).
    //
    // Two LilyPond unit families are involved. "Position-space" tie-details are in
    // half-staff-spaces and convert to staff spaces by ×0.5 (proven by
    // Tie_configuration::get_transformed_bezier, which translates by
    // delta_y_ + staff_space·0.5·position_). Geometric gaps are already in staff spaces.

    /** slur_shape asymptotic max arc height h_inf (bezier-bow.cc slur_height); staff spaces. */
    static final double TIE_HEIGHT_LIMIT_SS = 1.1;
    /** slur_shape height-vs-width growth ratio r_0 (bezier-bow.cc slur_height); unitless. */
    static final double TIE_RATIO = 0.333;
    /** slur_shape control-point indent factor max_fraction = 1/3.1 (bezier-bow.cc slur_shape); unitless. */
    static final double TIE_SLUR_MAX_FRACTION = 1.0 / 3.1;

    /**
     * LilyPond Tie note-head-gap: pulls both endpoints toward the span center (an interval shrink,
     * {@code attachment_x_.widen(-gap)}), not a per-edge offset; staff spaces.
     */
    static final double NOTE_HEAD_GAP_SS = 0.2;
    /**
     * LilyPond Tie generate_configuration dot lift: extra outward push of both endpoints when the tie's
     * seat row coincides with the left note's displaced augmentation-dot row, so the tie clears the dot
     * and attaches higher on the noteheads ({@code delta_y_ += dir·0.25·staff_space}); staff spaces.
     */
    static final double TIE_DOT_ROW_NUDGE_SS = 0.25;

    /**
     * Slack allowed when deciding whether a line's closing barline stands flush with the end of the
     * staff, which is what stops a cross-line tie at its left edge rather than carrying it over;
     * staff spaces. Absorbs the rounding of the spring solve, nothing more — a closer that is not
     * snapped flush right falls short by far more than this.
     */
    private static final double FLUSH_RIGHT_CLOSER_TOLERANCE_SS = 0.01;

    /**
     * Distance a tie endpoint clears a staff line by, in the arc direction; staff spaces. Both the seat
     * of a tie whose note sits on a line and the outward push of a tie whose edge-seat would land on a
     * line are this amount (LilyPond Tie tip-staff-line-clearance, 0.45 half-spaces × 0.5).
     */
    public static final double STAFF_LINE_TIE_CLEARANCE_GAP_SS = 0.225;
    /**
     * Tie seat for a note on an <em>outer</em> staff line (the top or bottom line), in the arc
     * direction; staff spaces. LilyPond seats these a full 1.5 staff-positions out — the endpoint
     * lands in the open space beyond the staff and attaches centered outside the notehead, rather
     * than tucking into the adjacent space like an inner-line note (measured: F5/E4 endpoints at
     * ±1.5 staff-positions = 0.75 ss). Inner-line notes stay at
     * {@link #STAFF_LINE_TIE_CLEARANCE_GAP_SS}.
     */
    static final double TIE_OUTER_STAFF_LINE_SEAT_SS = 0.75;
    /** Trigger margin: an arc fitting below a line keeps its natural height while its outer edge (incl. stroke) stays this far below; staff spaces. */
    static final double TIE_OUTER_EDGE_LINE_CLEARANCE_SS = 0.125;
    /** Fixed ink height (bottom of the endpoint stroke cap to the top of the apex stroke) when a tie is heightened over a staff line; staff spaces. */
    static final double TIE_HEIGHTENED_INK_HEIGHT_SS = 0.88;

    /** Tie apex thickness measured in LilyPond, as a multiple of staff line thickness (includes the render stroke). */
    private static final double TIE_APEX_THICKNESS_RATIO = 5.0 / 3.0;
    /** Fraction of its control-point offset a symmetric cubic actually reaches at the apex (t=0.5), per side. */
    static final double TIE_APEX_CONTROL_REACH = 0.75;
    /** Round-pen outline width TieRenderer strokes around the filled lens (rounds the ends, adds to apex); staff spaces. */
    public static final double TIE_OUTLINE_THICKNESS_SS = 0.05;
    /**
     * Tie lens midpoint half-thickness (centerline → outer/inner control point). Solved so the rendered
     * apex thickness — the filled lens (each side reaching {@link #TIE_APEX_CONTROL_REACH} of this offset)
     * plus the {@link #TIE_OUTLINE_THICKNESS_SS} stroke — equals LilyPond's measured
     * {@link #TIE_APEX_THICKNESS_RATIO} × staff line thickness; staff spaces.
     */
    static final double TIE_MID_THICKNESS_SS =
        (TIE_APEX_THICKNESS_RATIO * EngravingConstants.LILYPOND_BASE_THICKNESS_SS - TIE_OUTLINE_THICKNESS_SS)
            / (2 * TIE_APEX_CONTROL_REACH);

    private final LyricRenderMetrics lyricRenderMetrics;
    private final double staffRightMarginSs;
    private final DocumentFontsHolder fonts;

    // Calculators
    private final ElementColumnBuilder columnBuilder;
    private final VerticalStackingCalculator verticalCalculator;

    /**
     * Creates a new LayoutEngine.
     *
     * @param lyricRenderMetrics Song-wide lyric render metrics (font + glyph widths)
     * @param staffRightMarginSs Right margin of the staff in staff-space units
     */
    public LayoutEngine(
        LyricRenderMetrics lyricRenderMetrics,
        double staffRightMarginSs,
        DocumentFontsHolder fonts) {
        this.lyricRenderMetrics = lyricRenderMetrics;
        this.staffRightMarginSs = staffRightMarginSs;
        this.fonts = fonts;

        // Initialize calculators
        columnBuilder = new ElementColumnBuilder(lyricRenderMetrics);
        // The song's staff width, not a line's content width, which varies with column count:
        // every line's extents then clamp alike.
        verticalCalculator = new VerticalStackingCalculator(staffRightMarginSs, fonts);
    }

    /**
     * Whether the song's tempo mark is stacked into the first line's layout.
     * <p>
     * {@link #OMITTED} is for callers that lay a line out for its coordinates rather than to
     * paint it, where the inert header mark has nothing to contribute. The attribution is stacked
     * either way.
     */
    public enum TempoMark { STACKED, OMITTED }

    /**
     * Executes the complete layout pipeline for a line and produces a {@link LayoutResult} ready
     * for rendering.
     * <p>
     * The line's position in its song decides the position-dependent work: on the last line the
     * final double barline (if present) is pinned flush with the right edge of the line, and on
     * the first line the song's attribution — and, when {@code tempoMark} is
     * {@link TempoMark#STACKED}, its tempo mark — are typeset from the song and stacked above the
     * staff. Neither needs pre-measurement; both are typeset on every layout pass.
     *
     * @param line                        the line to lay out; must be in its song, since its
     *                                    position is read from {@link Line#isFirst()} and
     *                                    {@link Line#isLast()}
     * @param hasLeadingLyricContinuation true when the previous line's layout ended with an
     *                                    active melisma extender, which then continues from
     *                                    x = 0 on this line until the first syllable or rest
     *                                    that breaks it. A fact about the previous line's
     *                                    layout, not about this line, so it stays a parameter
     * @param tempoMark                   whether to stack the song's tempo mark on the first
     *                                    line; has no effect on any other line
     * @return the positioned elements; never null, since every line can be placed
     * @throws IllegalStateException if {@code line} is not in a song
     */
    public LayoutResult layout(Line line, boolean hasLeadingLyricContinuation, TempoMark tempoMark) {
        // Every pass below asks each beamed note for its stem direction, and each answer is its
        // beam's; holding the directions computes each beam's once for the whole layout.
        try (var _ = Beam.holdDirections(line.spanRegistry().findSpans(Beam.class))) {
            return layoutHoldingBeamDirections(line, hasLeadingLyricContinuation, tempoMark);
        }
    }

    /** The passes of {@link #layout}, run while every beam on {@code line} holds its direction. */
    private LayoutResult layoutHoldingBeamDirections(
        Line line, boolean hasLeadingLyricContinuation, TempoMark tempoMark) {
        // Step 1: Build note columns
        var columns = columnBuilder.buildColumns(line);

        // Steps 2-3b: Place the columns horizontally. An empty line has no chain to solve and
        // falls straight through to the shared passes below, which all resolve to no-ops or
        // natural zeros on empty columns. It deliberately gets no special-cased result: the
        // extents below feed inter-line spacing, so a line that invents content extents it does
        // not have spaces itself as if it held that content.
        var fitsStaffWidth = placeColumnsHorizontally(columns, line, line.isLast());

        // Step 3c: The notator's horizontal nudges land on the solved columns, before anything
        // below reads a column's X.
        applyHorizontalNudges(columns);

        // Step 4: The header (clef and key signature) depends on the line's key alone, so the
        // builder opens with it.
        var builder = LayoutResult.builder(createStaffHeader(line));
        builder.setOverflowsStaffWidth(!fitsStaffWidth);

        // Step 5: Calculate beam layouts for beamed note groups
        calculateBeams(line, columns, builder);

        // Step 5b: Calculate stem layouts for unbeamed notes
        calculateUnbeamedStems(columns, builder);

        // Step 6: Calculate tie geometry for every tie and tie half the line owns
        calculateTies(line, columns, builder);

        // Step 6b: Calculate slide geometry for every note carrying a glissando or a fall
        calculateSlides(line, columns, builder);

        // Step 7: Calculate vertical positions (requires stem layouts from steps 5/5b)
        verticalCalculator.calculate(columns, line, builder, tempoMark);

        // Step 7b: Compute lyric box and connector geometry, for the same script step 1 built the
        // columns for — what makes their cached syllable widths reusable there.
        buildLyricLayout(
            columns, line.getSong().getActiveScript(), builder, hasLeadingLyricContinuation);

        // Step 8: Build final LayoutResult
        return buildLayoutResult(columns, line, builder);
    }

    private void buildLyricLayout(
        List<ElementColumn> columns,
        Song.Script activeScript,
        LayoutResultBuilder builder,
        boolean hasLeadingLyricContinuation) {

        var lyricResult = LyricLayoutBuilder.build(
            columns,
            activeScript,
            lyricRenderMetrics,
            hasLeadingLyricContinuation,
            staffRightMarginSs);

        for (var entry : lyricResult.boxes().entrySet()) {
            for (var box : entry.getValue()) {
                builder.addLyricBox(entry.getKey(), box);
            }
        }

        for (var entry : lyricResult.placeholders().entrySet()) {
            builder.addLyricPlaceholder(entry.getKey(), entry.getValue());
        }

        for (var connector : lyricResult.connectors()) {
            builder.addLyricConnector(connector);
        }

        builder.setHasTrailingLyricContinuation(lyricResult.hasTrailingContinuation());
    }

    /**
     * Builds the springs, lifts their rests so syllables clear each other, anchors the chain, and
     * solves it against the staff width — all through the shared solve the insertion pre-check
     * also runs, so a line the pre-check accepts is one this can always place. The line rest
     * drives the base rests and the lift cap. Column X positions are written in place.
     * <p>
     * A line with no columns has nothing to solve and succeeds trivially — the solver anchors on
     * the first and last column, so it is the one pass an empty line cannot take.
     * <p>
     * A line the solver calls infeasible is still placed, on the struts the solver could not fit.
     * Those floors are the tightest legal spacing there is, so the placement is the closest the
     * line can come to fitting; what will not fit runs past the end of the staff, where the line
     * component's bounds clip it. Placing it is what lets the score show an over-full line at all
     * — the alternative was to abandon the layout, and the line then did not draw.
     *
     * @return true when the line fits the staff width; false when it overflowed and was placed on
     *         its collision floors
     */
    private boolean placeColumnsHorizontally(
        List<ElementColumn> columns, Line line, boolean isLastLine) {

        if (columns.isEmpty()) {
            return true;
        }

        var lineRestSs = line.getSong().getDefaultRestLengthSs();
        var solution = HorizontalSpacingCalculator.solveLine(columns, line, staffRightMarginSs);
        var fitsStaffWidth = !solution.isInfeasible();

        // Anchor the first column at the solved chain's origin, then lay the solved gaps out from it.
        columns.getFirst().setXSs(solution.firstXSs());

        // An infeasible solve carries no lengths — the struts it could not fit are the placement.
        var gapLengthsSs = fitsStaffWidth
            ? solution.result().gapLengthsSs()
            : strutLengthsSs(solution.springs());

        if (gapLengthsSs == null) {
            throw new IllegalStateException("solve succeeded but gapLengthsSs is null");
        }

        for (var i = 1; i < columns.size(); i++) {
            columns.get(i).setXSs(columns.get(i - 1).getXSs() + gapLengthsSs[i - 1]);
        }

        // Pin the terminal flush-right on the last line.
        // Layout is the sole writer of the terminal's x position.
        //
        // An overflowing line is skipped: the chain above already placed the terminal on its
        // collision floor out past the staff, which is as far left as it may legally sit, so there
        // is no room to snap into and pulling it back toward the margin would land it on top of the
        // content it follows. Skipping is not merely the same answer by another route — the snap
        // recomputes the floor without the pair's outer neighbours, which understates it when the
        // column before the terminal hosts a grace note carrying a syllable.
        if (isLastLine && fitsStaffWidth) {
            positionTerminalFlushRight(columns, lineRestSs);
        }

        return fitsStaffWidth;
    }

    /**
     * Returns every gap at its collision floor: the tightest placement of the chain that still
     * honours each pair's minimum spacing. Used for a line the solver cannot fit, where the floors
     * are all that is left to place it on.
     */
    private static double[] strutLengthsSs(List<Spring> springs) {
        var lengthsSs = new double[springs.size()];

        for (var i = 0; i < springs.size(); i++) {
            lengthsSs[i] = springs.get(i).strutSs();
        }

        return lengthsSs;
    }

    private void positionTerminalFlushRight(List<ElementColumn> columns, double lineRestSs) {
        // On the last line, the terminal is always the last element. Only the last
        // column is snapped flush-right — interior REPEAT_RIGHTs are left in place.
        var lastColumn = columns.getLast();
        var lastType = lastColumn.getElement().getType();

        if (!lastType.isValidSongTerminal()) {
            return;
        }

        var flushRightXSs = lastType.flushRightXSs(staffRightMarginSs);

        // Snapping to the margin moves the terminal independently of the solved chain, so on a
        // line whose content already reaches the margin it could land on top of — or left of —
        // the preceding column. Its strut is the collision floor the solver would have honoured.
        if (columns.size() > 1) {
            var prevColumn = columns.get(columns.size() - 2);
            var strutXSs = prevColumn.getXSs()
                + HorizontalSpacingCalculator.buildSpring(prevColumn, lastColumn, lineRestSs).strutSs();
            flushRightXSs = Math.max(flushRightXSs, strutXSs);
        }

        lastColumn.setXSs(flushRightXSs);
    }

    /**
     * Moves each column by its element's {@link StaffElement#getUserXOffsetSs() horizontal
     * nudge}, after the spacing solve and the terminal snap have placed the columns.
     * <p>
     * The nudge moves the column, not the element within it. A column is everything drawn with
     * its element — stem, flag, accidental, dots, syllable, the decorations stacked on it — and
     * every later pass and every reader of an element's X, {@code LayoutResult.getElementXSs}
     * included, reads the column's X, so moving the column is what keeps the note and what hangs
     * on it together. On the terms {@code VerticalStackingCalculator.applyManualOffsets} states
     * for decorations: the offset lands after spacing is solved, no collision pass re-runs, and
     * the notator owns any overlap that results — a nudged column may sit on its neighbour, or
     * past the staff margin.
     *
     * @param columns the line's columns, each with its solved X; a column whose element carries
     *                no nudge is left where the solve put it
     * @effects adds each element's nudge to its column's X, in place
     */
    private static void applyHorizontalNudges(List<ElementColumn> columns) {
        for (var column : columns) {
            var nudgeSs = column.getElement().getUserXOffsetSs();

            if (nudgeSs != 0) {
                column.setXSs(column.getXSs() + nudgeSs);
            }
        }
    }

    /**
     * Builds the final LayoutResult from calculated positions.
     * <p>
     * Populates the given builder with element columns and staff geometry,
     * then returns the built result. Vertical stacking results (decoration layouts,
     * line height, lyrics baseline) are already in the builder from the
     * {@link VerticalStackingCalculator#calculate} call.
     * <p>
     * The hit registry is the last thing layout computes, because it reads every other layout
     * map — columns, lyric boxes, decorations, slides — and so needs them all finished. It reads
     * them from a built result rather than from the builder, which keeps the geometry formulas
     * (the lyric row's midline offset above all) in the one place that already owns them. The
     * registry is then attached to that same result, which shares its maps rather than copying
     * them again — {@code build()} deep-copies every layout map, so calling it twice per line
     * would double that work on every layout.
     */
    private LayoutResult buildLayoutResult(
        List<ElementColumn> columns,
        Line line,
        LayoutResultBuilder builder) {

        // Add element columns
        for (var column : columns) {
            builder.putElementColumn(column.getElement(), column);
        }

        var geometry = builder.build();

        return geometry.withHitRegistry(
            HitRegionBuilder.build(line, geometry, lyricRenderMetrics));
    }

    /**
     * Calculates beam geometry for all beamed note groups in the line.
     * Populates {@code builder} with a {@link LayoutResult.BeamLayout} for each beam span.
     */
    private void calculateBeams(
        Line line,
        List<ElementColumn> columns,
        LayoutResultBuilder builder) {
        var beams = line.spanRegistry().findSpans(Beam.class);
        var elementToColumn = elementToColumnMap(columns);

        for (var beam : beams) {
            var members = beam.members();
            var memberCount = members.size();
            var stemDirection = beam.direction();

            // Collect the group's stems in BeamScoring's scoring space (Y-up positive,
            // y=0 at the middle staff line, X measured from the first stem).  Members
            // without a column are skipped, mirroring computeBeamElementGeometry. Only
            // members are stems here, so a grace note's pitch never tilts the beam, skews
            // the concaveness test, or dilutes forcedFraction.
            var stemInputs = new ArrayList<BeamScoring.StemInput>(memberCount);
            var firstColumnXSs = 0.0;
            var forcedStemCount = 0;

            for (var i = 0; i < memberCount; i++) {
                var element = members.get(i).note();

                // LilyPond Beam::forced_stem_count: a stem is forced when it points away from
                // its head's natural direction, whether the notator or the group turned it,
                // and a middle-line head is never "forced".
                if (element.getStaffPosition().value() != 0
                    && element.direction() != element.naturalDirection()) {
                    forcedStemCount++;
                }

                var column = elementToColumn.get(element);

                if (column == null) {
                    continue;
                }

                if (stemInputs.isEmpty()) {
                    firstColumnXSs = column.getXSs();
                }

                stemInputs.add(new BeamScoring.StemInput(
                    column.getXSs() - firstColumnXSs,
                    -element.getStaffPosition().toSs(),
                    -element.getStaffPosition().value(),
                    element.getType().beamCount()));

                if (LogUtils.isTracingBeams(LOG)) {
                    LOG.debug(
                        "  scoring input [{}] {} sp={} dir={} beams={}",
                        i,
                        element.getType(),
                        element.getStaffPosition(),
                        element.direction(),
                        element.getType().beamCount());
                }
            }

            if (LogUtils.isTracingBeams(LOG)) {
                LOG.debug(
                    "  beamMembers={} scoredStems={} forcedStemCount={}",
                    memberCount, stemInputs.size(), forcedStemCount);
            }

            // Beam geometry in SongScribe layout space (Y-down positive).  startYSs is
            // the beam's outer edge at the first stem; slope is Y-down per X.
            var slope = 0.0;
            var startYSs = 0.0;

            if (!stemInputs.isEmpty()) {
                var dirSign = stemDirection.isUp() ? 1 : -1;
                var forcedFraction = (double) forcedStemCount / memberCount;

                if (LogUtils.isTracingBeams(LOG)) {
                    LOG.debug(
                        "--- beam elements {}..{} ---",
                        beam.requireAnchorElementIndex(),
                        beam.requireEndElementIndex());
                }

                var beamPosition = BeamScoring.solve(stemInputs, dirSign, forcedFraction);
                var leftYUpSs = beamPosition.leftYUpSs();
                var rightYUpSs = beamPosition.rightYUpSs();
                var xSpanSs = stemInputs.getLast().xSs();

                startYSs = -leftYUpSs - dirSign * BeamMetrics.BEAM_THICKNESS_SS / 2.0;

                if (xSpanSs != 0.0) {
                    slope = -(rightYUpSs - leftYUpSs) / xSpanSs;
                }
            }

            // Beam thickening: angled beams appear thinner due to raster aliasing.
            // Compensate by increasing BEAM_DEPTH proportionally to 1/cos(angle),
            // clamped to the thickening factor's bounds.
            var angle = Math.atan(slope);
            var factor = Math.clamp(
                1.0 / Math.cos(angle), MIN_BEAM_THICKENING_FACTOR, MAX_BEAM_THICKENING_FACTOR);
            var thickeningSs = BEAM_DEPTH_SS * (factor - 1.0);
            var beamLine = new LayoutResult.BeamLine(firstColumnXSs, startYSs, slope);

            // Build StemLayout for each element in the beam group and accumulate into a map
            // for the BeamLayout.  All Y values are in staff-space with Y-down positive.
            //   stemsUp:   topYSs = beamYSs (above notehead, smaller Y),  bottomYSs = elementAnchorYSs
            //   stemsDown: topYSs = elementAnchorYSs,                     bottomYSs = beamYSs (below notehead, larger Y)
            var stemLayouts = new HashMap<StaffElement, LayoutResult.StemLayout>();

            if (!stemInputs.isEmpty()) {
                // Only members run to the beam: a grace note's stem stops at its own length, so
                // it is left to calculateUnbeamedStems rather than measured here.
                for (var i = 0; i < memberCount; i++) {
                    var member = members.get(i);
                    var element = member.note();
                    var geometry = computeBeamElementGeometry(element, elementToColumn, stemDirection, beamLine);

                    if (geometry == null) {
                        continue;
                    }

                    var elementYSs = geometry.elementYSs();
                    var beamYSs = geometry.beamYSs();
                    var stemLenSs = geometry.stemLenSs();

                    // The quanted beam position is authoritative: express the resulting
                    // stem length as a delta from the standard stem length so
                    // StaffElementRenderer.renderStem reproduces exactly this tip.
                    var lengtheningSs = Math.max(0.0, stemLenSs - MIN_STEM_SS);
                    var forcedShorteningSs = Math.max(0.0, MIN_STEM_SS - stemLenSs);

                    var topYSs = stemDirection.isUp() ? beamYSs : elementYSs;
                    var bottomYSs = stemDirection.isUp() ? elementYSs : beamYSs;

                    // How far back French beaming pulls the drawn stem. Resolved here rather
                    // than in StaffElementRenderer so the per-paint path stays a field read.
                    var frenchShorteningLevels = member.frenchShortening();

                    if (LogUtils.isTracingBeams(LOG)) {
                        LOG.debug(
                            "  stem [{}] {} sp={} dir={} elementY={} beamY={} stemLen={} "
                                + "(natural={}) lengthening={} forcedShort={} french={}",
                            i,
                            element.getType(),
                            element.getStaffPosition(),
                            element.direction(),
                            String.format(STEM_LOG_FORMAT, elementYSs),
                            String.format(STEM_LOG_FORMAT, beamYSs),
                            String.format(STEM_LOG_FORMAT, stemLenSs),
                            String.format(STEM_LOG_FORMAT, MIN_STEM_SS),
                            String.format(STEM_LOG_FORMAT, lengtheningSs),
                            String.format(STEM_LOG_FORMAT, forcedShorteningSs),
                            frenchShorteningLevels);
                    }

                    stemLayouts.put(
                        element,
                        new LayoutResult.StemLayout(
                            topYSs, bottomYSs, lengtheningSs, forcedShorteningSs, frenchShorteningLevels));
                }
            }

            var beamLayout = new LayoutResult.BeamLayout(beamLine, stemDirection, thickeningSs, stemLayouts);
            builder.putBeamLayout(beam, beamLayout);
        }
    }

    @Nullable
    private BeamElementGeometry computeBeamElementGeometry(
        NoteElement element, Map<StaffElement, ElementColumn> elementToColumn,
        Direction stemDirection, LayoutResult.BeamLine beamLine) {
        var column = elementToColumn.get(element);

        if (column == null) {
            return null;
        }

        var elementYSs = element.getStaffPosition().toSs();
        var beamYSs = beamLine.yAtSs(column.getXSs());
        var stemLenSs = stemDirection.isUp() ? (elementYSs - beamYSs) : (beamYSs - elementYSs);
        return new BeamElementGeometry(elementYSs, beamYSs, stemLenSs);
    }

    /**
     * Calculates stem geometry for all elements whose stems are not drawn to a beam.
     * Populates {@code builder} with a {@link LayoutResult.StemLayout} for each such element.
     * <p>
     * A grace note sitting inside a beam's span belongs here too: its column is never beamed
     * ({@link ElementColumnBuilder#buildColumn}), and its stem stops at the grace length rather
     * than running to the beam.
     */
    private void calculateUnbeamedStems(List<ElementColumn> columns, LayoutResultBuilder builder) {
        for (var col : columns) {
            if (!(col.getElement() instanceof NoteElement element)
                || col.beaming() == Beaming.BEAMED
                || !element.getType().isNoteWithStem()) {
                continue;
            }

            var direction = element.direction();
            var isGraceNote = element.getType().isGraceNote();
            var elementYSs = element.getStaffPosition().toSs();
            var stemLenSs = isGraceNote
                ? StemMetrics.GRACE_NOTE_STEM_LENGTH_SS
                : MIN_STEM_SS;

            // Extend the stem tip to reach the staff center (Y=0) when the stem points toward
            // center but the natural stem length falls short. The signed distance the tip must
            // travel toward center is elementYSs for an up-stem and -elementYSs for a down-stem;
            // a stem pointing away from center (only via a manual override) is never extended.
            var distanceTowardCenterSs = direction.isUp() ? elementYSs : -elementYSs;
            var lengtheningSs = isGraceNote
                ? 0.0
                : Math.max(0.0, distanceTowardCenterSs - MIN_STEM_SS);

            // Shorten a stem forced into its unnatural direction (Ross & Gourlay). Disjoint from
            // lengthening — a stem is lengthened, natural, or shortened, never two at once.
            var forcedShorteningSs = NoteGeometry.forcedShorteningSs(element);
            var effectiveLenSs = stemLenSs + lengtheningSs - forcedShorteningSs;

            // Y increases downward: stem-up tip has smaller Y; stem-down tip has larger Y.
            var topYSs = direction.isUp() ? elementYSs - effectiveLenSs : elementYSs;
            var bottomYSs = direction.isUp() ? elementYSs : elementYSs + effectiveLenSs;

            builder.putStemLayout(
                element,
                new LayoutResult.StemLayout(topYSs, bottomYSs, lengtheningSs, forcedShorteningSs, 0));
        }
    }

    /**
     * Calculates tie geometry for every tie and tie half the line owns — see
     * {@link Line#ownedTies}.
     * <p>
     * Ports LilyPond's single-tie {@code slur_shape} curve (bezier-bow.cc) plus endpoint gaps
     * and staff-line avoidance (tie-formatting-problem.cc) to SongScribe's staff-space coordinate
     * system. Each tie produces a filled lens shape defined by an outer and an inner cubic Bézier
     * curve that share start/end points, creating natural tapering at the endpoints.
     * Populates {@code builder} with a {@link LayoutResult.TieLayout} for each one.
     * <p>
     * A tie whose two notes sit in different lines is laid out once per line, and each line draws
     * only the half it owns; {@link #tieLayout} carries the geometry of both cases.
     */
    private void calculateTies(
        Line line,
        List<ElementColumn> columns,
        LayoutResultBuilder builder) {

        var ties = line.ownedTies();

        if (ties.isEmpty()) {
            return;
        }

        var elementToColumn = elementToColumnMap(columns);

        // Resolved once for the line: both limits depend on the line's own edges, not on any
        // tie, so every half running off an edge stops in the same place.
        var openLimits = new OpenTieLimits(
            HorizontalSpacingCalculator.calculateHeaderRightEdgeSs(line),
            lineEndTieLimitXSs(line, elementToColumn));

        for (var placement : ties) {
            var tieColumns = ElementColumn.resolveTie(placement, elementToColumn);

            if (tieColumns != null) {
                builder.addTieLayout(tieLayout(placement, tieColumns, openLimits));
            }
        }
    }

    /**
     * The X limits at which a tie half running off an edge of the line being laid out stops,
     * before the note-head gap is taken off each. Both are properties of the line, so every
     * half in it terminates in the same place.
     *
     * @param startXSs where a half entering from the left begins — the staff header's right edge
     * @param endXSs   where a half exiting to the right stops — see {@link #lineEndTieLimitXSs}
     */
    private record OpenTieLimits(double startXSs, double endXSs) {}

    /**
     * X at which a half exiting through this line's right edge stops: the left edge of the
     * line's closing barline or repeat when that closer stands flush with the end of the staff,
     * and the staff's right margin otherwise.
     * <p>
     * This is LilyPond's rule read exactly. `set_column_chord_outline` bounds the open end with
     * `Axis_group_interface::staff_extent (...)[-dir]` over the <em>break column's own</em>
     * elements — at a system end, its bar line — and `-dir` for the right bound is `iv[LEFT]`,
     * that bar line's left edge. The `iv.is_empty ()` fallback to the column's own X is what
     * makes a line ending in no barline run to the line's edge instead.
     * <p>
     * A tie's anchor is not the last element of its line whenever a legal separator follows it,
     * which is the ordinary case: the toggle offers a cross-line tie on the last <em>note</em>
     * of a line, past any barline or repeat closing it, and one may equally be inserted after
     * the anchor later. A terminal barline is laid out flush right, so it shares its pixels with
     * the end of the staff and an arc run past it would have nowhere to go; there the arc stops
     * at its left edge. A closer that sits short of the margin — the ordinary case, since only
     * the last line's terminal is snapped flush right — leaves open staff to the right of it, and
     * a half stopping at its left edge would end mid-staff. Such an arc carries over the barline
     * to the end of the staff, which is where the reader's eye leaves the line.
     * <p>
     * A breath mark is deliberately not included: it is ordinary music rather than part of the
     * closing column, and LilyPond does not bound a tie with one.
     */
    private double lineEndTieLimitXSs(Line line, Map<StaffElement, ElementColumn> elementToColumn) {
        var elementCount = line.elementCount();

        if (elementCount == 0) {
            return staffRightMarginSs;
        }

        var lastElement = line.getElement(elementCount - 1);
        var lastType = lastElement.getType();

        if (!lastType.isBar()) {
            return staffRightMarginSs;
        }

        var lastColumn = elementToColumn.get(lastElement);

        if (lastColumn == null
            || lastColumn.getRightEdgeXSs() < staffRightMarginSs - FLUSH_RIGHT_CLOSER_TOLERANCE_SS) {
            return staffRightMarginSs;
        }

        return lastColumn.getXSs();
    }

    /**
     * Builds one tie's geometry: the whole arc when both of its notes are in the line being laid
     * out, or the half of it this line draws when they are not.
     * <p>
     * A half is described by the same twelve numbers as a whole tie: it is a complete arc in its
     * own right, so nothing downstream has to reconstruct the side that is missing.
     *
     * @param placement  the tie being laid out and its place in the line; the arc is seated
     *                   against the tie's anchor note, and a tie joins two same-pitch notes, so
     *                   either endpoint would yield the same staff position
     * @param columns    the columns of the tie's notes in this line: both for a whole tie, and
     *                   for a half the one note's, whose open edge terminates at the line's edge
     * @param openLimits where this line's edges stop an open end; the side that attaches to a
     *                   notehead ignores its half of this
     */
    private LayoutResult.TieLayout tieLayout(
        Line.TiePlacement placement,
        TieColumns columns,
        OpenTieLimits openLimits) {

        var tie = placement.tie();

        // Tie arc sign from Tie.arcSign() (LilyPond's both-stem fallthrough tree, shared with
        // the skyline seeder and MusicXML export). Y increases downward, so +1 → arc bulges
        // downward (tie below), -1 → arc bulges upward (tie above).
        //
        // Both halves of a broken tie get the same sign for free, because arcSign() reads the two
        // notehead elements rather than this line's columns. LilyPond arrives at the same answer
        // the long way round, reaching across the break for the head a half does not have
        // (tie.cc get_default_dir: `if (!one_head) one_head = head (me->broken_neighbor (d), d)`).
        var arcSign = tie.arcSign();

        // Seat the tie in the staff space adjacent to the note (in the arc direction), placed so
        // both the endpoints and the apex clear the staff lines bounding that space (LilyPond
        // tie-formatting-problem.cc generate_configuration). The vertical seat is computed first;
        // the horizontal attachment then follows from it, because LilyPond samples the chord
        // skyline at the seat height — the endpoint sits at the notehead's facing edge while the
        // seat is within the head box, and recedes to the notehead center once it drops below.
        // A tie joins two same-pitch notes, so both endpoints share one Y. The tie is positioned
        // purely note-relative and independent of any articulation, matching LilyPond, where the
        // tie and the note's scripts (staccato, accent) are placed independently.
        //
        // When the seat row lands on the left note's up-displaced augmentation dot, the whole tie
        // lifts a further quarter-space to clear it (the dot never moves) — LilyPond's dot-row case.
        // The dot belongs to the left note and sits under the arc's path, so the check applies only
        // where that note is in this line: a half entering from the left edge passes over no dot.
        var anchorNote = tie.anchor();
        var notePosition = anchorNote.getStaffPosition();
        var dotRowCoincides = placement.holdsAnchor()
            && tieSeatRowHasDot(anchorNote, notePosition, arcSign);
        var seatSs = tieSeatSs(notePosition, arcSign, dotRowCoincides);
        var endpointYSs = notePosition.toSs() + arcSign * seatSs;

        // Facing edge while the seat stays within the half-space-tall head box; notehead center
        // once it clears the box (LilyPond get_attachment's edge-vs-center step). dir = +1 for the
        // left endpoint (tie extends rightward), -1 for the right.
        //
        // An open end has no notehead to attach to and terminates at a line edge instead: the half
        // leaving the earlier line runs on to the right staff edge, and the half entering the later
        // line starts where the music does, past the clef and key signature.
        //
        // That is LilyPond's rule in tie-formatting-problem.cc set_column_chord_outline: when the
        // bound item is a line-break column rather than a note (`bounds[0]->break_status_dir()`),
        // the chord outline's limit is taken from
        // `Axis_group_interface::staff_extent (...)[-dir]` instead of from the union of the note-head
        // boxes, falling back to the break column's own X only where that extent is empty.
        //
        // Read that `staff_extent` carefully — it is not the staff symbol's extent, which would
        // span the whole system. `Axis_group_interface::staff_extent` (axis-group-interface.cc:244)
        // takes the *break column's own* elements, keeps those belonging to this staff, and returns
        // their combined X extent. At a system start those elements are the prefatory matter: the
        // clef and key signature. `-dir` then picks the far edge of that group — for the left bound
        // (`dir == LEFT`) it is `iv[RIGHT]`, the header's right edge.
        //
        // So the continuation half begins where the music does, past the header, and LilyPond's
        // rule and SongScribe's layout agree without either having to be bent. Taken instead to
        // X 0, the arc would be drawn straight across the clef, reading as a marking on the header
        // rather than as the continuation of a tie. The other end is bounded the same way — see
        // lineEndTieLimitXSs.
        //
        // An open end takes the same NOTE_HEAD_GAP_SS as an attached one. LilyPond applies it in
        // one unconditional step after both attachments are resolved
        // (`conf->attachment_x_.widen (-details_.x_gap_)`, tie-formatting-problem.cc:580), with no
        // test for whether an end is a notehead or a break column, so the arc stands off the
        // header and the barline by exactly what it stands off a notehead.
        var centerAttach = seatSs > Staff.HALF_SPACE_SS;
        var startXSs = switch (columns) {
            case TieColumns.Whole whole -> tieStartXSs(whole.columns().anchorColumn(), centerAttach);
            case TieColumns.Half half -> switch (half.openEdge()) {
                case START -> openLimits.startXSs() + NOTE_HEAD_GAP_SS;
                case END -> tieStartXSs(half.attachedColumn(), centerAttach);
            };
        };
        var endXSs = switch (columns) {
            case TieColumns.Whole whole -> tieEndXSs(whole.columns().endColumn(), centerAttach);
            case TieColumns.Half half -> switch (half.openEdge()) {
                case START -> tieEndXSs(half.attachedColumn(), centerAttach);
                case END -> openLimits.endXSs() - NOTE_HEAD_GAP_SS;
            };
        };

        var tieWidthSs = endXSs - startXSs;

        // LilyPond slur_shape: control points P1 = (indent, height), P2 = (width − indent, height).
        var indentSs = BezierBow.indent(tieWidthSs, TIE_HEIGHT_LIMIT_SS, TIE_SLUR_MAX_FRACTION);
        var cp1XSs = startXSs + indentSs;
        var cp2XSs = endXSs - indentSs;

        // Arc height from the endpoint baseline (the shared endpoint Y for a same-pitch tie),
        // adjusted so the arc body clears the nearest staff line.
        //
        // A half's width is its own — note to staff edge — never the notional whole tie's, and the
        // height law is a function of that width. So each half of a broken tie is flatter than the
        // unbroken tie would have been, and the two halves differ in height from each other
        // whenever they differ in width. They are two complete arcs rather than two pieces of one:
        // LilyPond builds each from `Real l = attachment_x_.length()` over its own system
        // (tie-configuration.cc get_untransformed_bezier), and slur_shape's control points
        // (0,0), (indent,h), (w−indent,h), (w,0) put both ends of every half back on the baseline —
        // the open end meets the staff edge flat, not mid-arc with a slope left hanging. Nothing in
        // tie-column.cc handles breaks at all; the halves are independent formatting problems.
        var heightSs = tieLineAvoidedHeightSs(
            endpointYSs, arcSign, BezierBow.height(tieWidthSs, TIE_RATIO, TIE_HEIGHT_LIMIT_SS));
        var shoulderYSs = endpointYSs + arcSign * heightSs;

        // Outer/inner lens taper: outer control points offset away from the notes by the midpoint
        // half-thickness, inner control points offset toward the notes.
        var outerCpYSs = shoulderYSs + arcSign * TIE_MID_THICKNESS_SS;
        var innerCpYSs = shoulderYSs - arcSign * TIE_MID_THICKNESS_SS;

        if (LogUtils.isTracingTies(LOG)) {
            // LilyPond position convention (positive = up) for direct ground-truth comparison:
            // LilyPond ss is positive-up, SongScribe ss is positive-down, so negate and ×2.
            LOG.debug(
                "tie notePos={} arcSign={} seatSs={} width={} endpointPos={} apexPos={} heightSs={}"
                    + " openEdge={}",
                notePosition, arcSign, seatSs, tieWidthSs,
                -2 * endpointYSs, -2 * shoulderYSs, heightSs, placement.openEdge());
        }

        return new LayoutResult.TieLayout(
            placement,
            startXSs, endpointYSs,
            endXSs, endpointYSs,
            cp1XSs, outerCpYSs,
            cp2XSs, outerCpYSs,
            cp1XSs, innerCpYSs,
            cp2XSs, innerCpYSs
        );
    }

    /** X of a tie's left end, attached to the anchor note's {@code column}. */
    private static double tieStartXSs(ElementColumn column, boolean centerAttach) {
        return tieEndpointXSs(column.getXSs(), 1, centerAttach, column.getElement().getType());
    }

    /** X of a tie's right end, attached to the end note's {@code column}. */
    private static double tieEndXSs(ElementColumn column, boolean centerAttach) {
        return tieEndpointXSs(column.getXSs(), -1, centerAttach, column.getElement().getType());
    }

    /**
     * Calculates slide geometry for every note in the line carrying a {@link Slide}, and
     * populates {@code builder} with a {@link LayoutResult.SlideLayout} for each one that has
     * drawable geometry.
     * <p>
     * Everything is computed in layout space — X line-local, Y relative to the staff midline — by
     * building each note context with {@code middleLineYSs = 0}. A connecting glissando that is
     * too short to draw, or that spans two notes at the same pitch, yields no entry at all; a
     * fall always yields one.
     */
    private void calculateSlides(
        Line line,
        List<ElementColumn> columns,
        LayoutResultBuilder builder) {

        var elementToColumn = elementToColumnMap(columns);

        // The auto-maintained terminal barline can own no slide, so the effective count is enough.
        var elementCount = line.effectiveElementCount();

        for (var i = 0; i < elementCount; i++) {
            if (!(line.getElement(i) instanceof NoteElement note)) {
                continue;
            }

            var slide = note.getSlide();

            if (slide == null) {
                continue;
            }

            var src = slideNoteContext(note, i, line, elementToColumn);

            switch (slide) {
                // A fall is a standalone trailing glyph with no target note, so it never
                // resolves the following element.
                case FALL -> builder.putSlideLayout(note, LayoutResult.SlideLayout.ofFall(
                    SlideGeometry.computeFallBoundsSs(src)));

                case GLISSANDO -> {
                    // The slide tool refuses to create a glissando between two notes at one
                    // pitch and a pitch move strips one that becomes so, leaving import as the
                    // only way one can get here. Storing no geometry keeps hit-testing and the
                    // MusicXML writer agreeing with the renderer, which draws nothing for it.
                    if (line.isSamePitchAsFollower(i)) {
                        continue;
                    }

                    var endpoints = SlideGeometry.computeEndpoints(
                        src, slideTargetContext(i, line, elementToColumn));

                    if (endpoints != null) {
                        builder.putSlideLayout(note, LayoutResult.SlideLayout.ofGlissando(endpoints));
                    }
                }
            }
        }
    }

    /**
     * Indexes {@code columns} by the element each one lays out, so the passes that walk spans —
     * beams, ties, slides — can look a note's column up directly instead of scanning the list
     * once per element they touch.
     */
    private static Map<StaffElement, ElementColumn> elementToColumnMap(List<ElementColumn> columns) {
        var elementToColumn = new HashMap<StaffElement, ElementColumn>(columns.size() * 2);

        for (var column : columns) {
            elementToColumn.put(column.getElement(), column);
        }

        return elementToColumn;
    }

    /**
     * Builds the slide geometry context for the note at {@code noteIndex}, in layout space.
     */
    private static SlideGeometry.NoteContext slideNoteContext(
        NoteElement note,
        int noteIndex,
        Line line,
        Map<StaffElement, ElementColumn> elementToColumn) {

        var column = elementToColumn.get(note);
        var elementXSs = column != null ? column.getXSs() : 0;

        return SlideGeometry.noteContextAt(
            note, elementXSs, Beaming.of(line.spanRegistry().findBeamJoining(noteIndex)), 0);
    }

    /**
     * Builds the target context for a connecting glissando — the element after {@code sourceIndex} —
     * or returns null when there is nothing to connect to.
     * <p>
     * A non-note follower (a barline at the line end, say) has no notehead geometry to attach to, so
     * it is treated the same as no follower at all: {@link SlideGeometry#computeEndpoints} then
     * rejects the glissando instead of the column geometry failing on a glyph-less element type.
     */
    private static SlideGeometry.@Nullable NoteContext slideTargetContext(
        int sourceIndex,
        Line line,
        Map<StaffElement, ElementColumn> elementToColumn) {

        if (sourceIndex + 1 >= line.elementCount()) {
            return null;
        }

        if (!(line.getElement(sourceIndex + 1) instanceof NoteElement nextNote)) {
            return null;
        }

        return slideNoteContext(nextNote, sourceIndex + 1, line, elementToColumn);
    }

    /**
     * X of a tie endpoint, in staff spaces (LilyPond tie-formatting-problem.cc {@code get_attachment}).
     * <p>
     * LilyPond samples the chord's skyline outline at the endpoint's seat height, then note-head-gap
     * pulls the endpoint {@link #NOTE_HEAD_GAP_SS} toward the span center
     * ({@code attachment_x_.widen(-gap)}). That sample is a step: while the seat sits within the head
     * box the outline is the notehead's facing (span-side) edge; once the seat drops below the box the
     * outline has receded to the notehead center. The two endpoints are mirror images.
     *
     * @param noteLeftXSs  the notehead's left edge (its column X), in staff spaces
     * @param dir          +1 for the left endpoint (tie extends rightward), -1 for the right endpoint
     * @param centerAttach true when the seat has cleared the head box, so the endpoint attaches at the
     *                     notehead center rather than its facing edge
     * @param noteType     the type of the note this endpoint attaches to, for its own head width
     */
    static double tieEndpointXSs(
        double noteLeftXSs, int dir, boolean centerAttach, ElementType noteType) {
        var noteheadWidthSs = noteType.getElementWidthSs();
        var centerXSs = noteLeftXSs + noteheadWidthSs / 2;
        var facingEdgeXSs = centerXSs + dir * noteheadWidthSs / 2;
        var skylineXSs = centerAttach ? centerXSs : facingEdgeXSs;

        return skylineXSs + dir * NOTE_HEAD_GAP_SS;
    }

    /**
     * Distance from the tied notes' shared center to the tie endpoints, in the arc direction; staff
     * spaces. The tie is seated in the staff space adjacent to the note so its endpoints clear the
     * bounding staff lines (LilyPond tie-formatting-problem.cc {@code generate_configuration}).
     * <ul>
     *   <li>A note <b>on a line</b> seats {@link #STAFF_LINE_TIE_CLEARANCE_GAP_SS} into the adjacent
     *       space, clearing its own line; the far line bounding that space is cleared by the arc-height
     *       adjustment in {@link #tieLineAvoidedHeightSs}.</li>
     *   <li>A note <b>in a space</b> seats at the head-box edge, half a staff space out; if that edge
     *       row is itself a staff line the endpoints would land on it, so the tie is pushed
     *       {@link #STAFF_LINE_TIE_CLEARANCE_GAP_SS} further out to clear it.</li>
     *   <li>When {@code dotRowCoincides} — the seat row lands on the left note's displaced dot row
     *       ({@link #tieSeatRowHasDot}) — the tie seats at the head-box edge and lifts a further
     *       {@link #TIE_DOT_ROW_NUDGE_SS} to attach above the dot (LilyPond adds {@code dir·0.25} and
     *       disables y-tuning).</li>
     * </ul>
     * Placement is purely note-relative and independent of any articulation, matching LilyPond, where
     * the tie and the note's scripts are positioned independently.
     *
     * @param notePosition    the tied notes' shared staff position
     * @param arcSign       +1 when the arc bulges downward, -1 upward
     * @param dotRowCoincides whether the seat row coincides with the left note's dot row
     */
    static double tieSeatSs(StaffPosition notePosition, int arcSign, boolean dotRowCoincides) {
        if (dotRowCoincides) {
            return Staff.HALF_SPACE_SS + TIE_DOT_ROW_NUDGE_SS;
        }

        if (notePosition.isOnLine()) {
            // Outer staff lines seat outside the staff (centered outside the notehead); inner lines
            // tuck into the adjacent space.
            return isOuterRealStaffLine(notePosition)
                ? TIE_OUTER_STAFF_LINE_SEAT_SS
                : STAFF_LINE_TIE_CLEARANCE_GAP_SS;
        }

        // A space note seats at the head-box edge; push past the edge row when it is a staff line.
        // notePosition is off-line, so it is never MIN or MAX (both are on-line), and the
        // adjacent row stays on the grid.
        var edgeRow = new StaffPosition(notePosition.value() + arcSign);

        if (isRealStaffLinePosition(edgeRow)) {
            return Staff.HALF_SPACE_SS + STAFF_LINE_TIE_CLEARANCE_GAP_SS;
        }

        return Staff.HALF_SPACE_SS;
    }

    /**
     * Whether the given staff position is one of the five real staff lines — an even position within
     * the staff — as opposed to a line position on a ledger row above or below the staff.
     *
     * @param staffPosition a staff position
     */
    private static boolean isRealStaffLinePosition(StaffPosition staffPosition) {
        return staffPosition.isOnLine()
            && Math.abs(staffPosition.value()) <= StaffPosition.OUTERMOST_STAFF_LINE_SP;
    }

    /**
     * Whether the given staff position is one of the two <em>outer</em> staff lines — the top or
     * bottom line, i.e. a line position exactly {@link Staff#STAFF_HALF_SS} from the middle line.
     *
     * @param staffPosition a staff position
     */
    private static boolean isOuterRealStaffLine(StaffPosition staffPosition) {
        return staffPosition.isOnLine()
            && Math.abs(staffPosition.value()) == StaffPosition.OUTERMOST_STAFF_LINE_SP;
    }

    /**
     * Whether the tie's seat row coincides with the left note's augmentation-dot row — LilyPond's
     * {@code generate_configuration} dot check ({@code dot_positions_.find(pos)}). Only a dotted note
     * sitting on a staff line qualifies: its dot is displaced one half-space toward the top
     * ({@link NoteGeometry#DOT_ON_LINE_Y_SHIFT_SS}) into the space an upward arc seats in. A note in a
     * space keeps its dot on its own row — two half-spaces from the seat — so it never coincides, and an
     * on-line note whose tie arcs the other way seats opposite the (always up-displaced) dot.
     *
     * @param note         the left (anchor) note of the tie
     * @param notePosition the note's staff position
     * @param arcSign    +1 when the arc bulges downward, -1 upward
     */
    static boolean tieSeatRowHasDot(NoteElement note, StaffPosition notePosition, int arcSign) {
        if (note.dots() == Dots.NONE || !notePosition.isOnLine()) {
            return false;
        }

        // Dot row = note row displaced up (Y-down); seat row = note row + one half-space in the arc
        // direction (LilyPond position_ += dir). They meet only for an upward arc into the dot's space.
        var dotRowSp = notePosition.value() + Staff.ssToHalfSpaces(NoteGeometry.DOT_ON_LINE_Y_SHIFT_SS);
        var seatRowSp = notePosition.value() + arcSign;

        return dotRowSp == seatRowSp;
    }

    /**
     * Adjusts the tie arc height so its body clears the nearest staff line, mirroring LilyPond's
     * per-configuration staff-line avoidance. The endpoints are fixed, so only the height changes.
     * <p>
     * The arc stays on whichever side of the line its natural apex already favors, matching LilyPond. An
     * arc whose apex sits below the line stays under it, keeping its natural height unless its outer edge
     * (incl. render stroke) would come within {@link #TIE_OUTER_EDGE_LINE_CLEARANCE_SS} of the line — a
     * small edge-based trigger, so a tie resting quietly inside a staff space is left untouched. When the
     * trigger fires the arc is flattened just enough to restore that edge gap. An arc whose apex pokes
     * through the line is instead heightened up and over it — never squashed back under — so the stroked
     * arc stands a fixed {@link #TIE_HEIGHTENED_INK_HEIGHT_SS} tall (bottom of the endpoint stroke cap to
     * the top of the apex stroke), a stable height independent of tie width. Returns the natural height
     * when the arc already clears, or when no real staff line is near the apex. All Y arguments and the
     * result are in staff spaces.
     *
     * @param baseYSs         the endpoint baseline Y (midpoint of the two endpoints)
     * @param arcSign       +1 when the arc bulges downward, -1 upward
     * @param naturalHeightSs the unadjusted slur_shape arc height
     */
    static double tieLineAvoidedHeightSs(double baseYSs, int arcSign, double naturalHeightSs) {
        var halfStrokeSs = TIE_OUTLINE_THICKNESS_SS / 2;

        // The staff line nearest the natural apex centerline; only a real (in-staff) line constrains.
        var apexOutwardSs = TIE_APEX_CONTROL_REACH * naturalHeightSs;
        var apexMidYSs = baseYSs + arcSign * apexOutwardSs;
        var nearestLineYSs = (double) Math.round(apexMidYSs);

        if (Math.abs(nearestLineYSs) > Staff.STAFF_HALF_SS) {
            return naturalHeightSs;
        }

        // Distance from the baseline out to the line, positive in the arc direction.
        var lineDistSs = arcSign * (nearestLineYSs - baseYSs);

        // Natural apex sits below the line: keep the arc under it, flattening only when its outer edge
        // would intrude on the small edge trigger. If even a flat arc cannot clear below, fall through
        // and lift it over.
        if (apexOutwardSs <= lineDistSs) {
            if (tieOuterEdgeDistSs(naturalHeightSs, halfStrokeSs) <= lineDistSs - TIE_OUTER_EDGE_LINE_CLEARANCE_SS) {
                return naturalHeightSs;
            }

            var flattenedHeightSs =
                tieHeightForOuterEdgeDistSs(lineDistSs - TIE_OUTER_EDGE_LINE_CLEARANCE_SS, halfStrokeSs);

            if (flattenedHeightSs > 0) {
                return flattenedHeightSs;
            }
        }

        // Natural apex pokes through the line (or cannot clear below): heighten so the stroked arc stands
        // a fixed ink height tall — from the endpoint stroke cap (halfStroke below the anchor) to the top
        // of the apex stroke — a stable height independent of tie width; never shrink an already-taller
        // natural arc. The outer edge above the anchor must therefore reach the ink height less that cap.
        return Math.max(
            naturalHeightSs,
            tieHeightForOuterEdgeDistSs(TIE_HEIGHTENED_INK_HEIGHT_SS - halfStrokeSs, halfStrokeSs));
    }

    /** Outward distance from the baseline to the arc's outer edge (incl. stroke) at the apex; staff spaces. */
    private static double tieOuterEdgeDistSs(double heightSs, double halfStrokeSs) {
        return TIE_APEX_CONTROL_REACH * (heightSs + TIE_MID_THICKNESS_SS) + halfStrokeSs;
    }

    /** Inverse of {@link #tieOuterEdgeDistSs}: the arc height whose outer edge sits at the given distance. */
    private static double tieHeightForOuterEdgeDistSs(double outerEdgeDistSs, double halfStrokeSs) {
        return (outerEdgeDistSs - halfStrokeSs) / TIE_APEX_CONTROL_REACH - TIE_MID_THICKNESS_SS;
    }

    /**
     * Creates the header {@code line} opens with.
     * <p>
     * The clef is placed at {@link #CLEF_X_POSITION_SS}. The key signature follows at
     * {@link HorizontalSpacingCalculator#calculateKeySignatureXSs()}, one clef width plus
     * LilyPond's clef-to-key-signature gap further right, and draws the line's
     * {@linkplain Line#getRunningKey() running key}.
     *
     * @return the line's staff header
     */
    private static StaffHeader createStaffHeader(Line line) {
        return new StaffHeader(
            CLEF_X_POSITION_SS,
            HorizontalSpacingCalculator.calculateKeySignatureXSs(),
            line.getRunningKey());
    }

    /**
     * Returns the staff right margin used by this engine.
     */
    public double getStaffRightMarginSs() {
        return staffRightMarginSs;
    }

    private record BeamElementGeometry(
        double elementYSs,
        double beamYSs,
        double stemLenSs) {}
}
