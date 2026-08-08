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

import module java.desktop;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import songscribe.dom.Attribution;
import songscribe.dom.Beam;
import songscribe.dom.Clef;
import songscribe.dom.KeySignature;
import songscribe.font.DocumentFontsHolder;
import songscribe.dom.ElementType;
import songscribe.dom.KeyType;
import songscribe.engraving.LineThickness;
import songscribe.engraving.SMuFLConstants;
import songscribe.engraving.Staff;
import songscribe.dom.Line;
import songscribe.dom.StaffElement;
import songscribe.dom.Tie;
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
 * var result = engine.layout(line);
 * }</pre>
 */
public class LayoutEngine {

    private static final Logger LOG = LoggerFactory.getLogger(LayoutEngine.class);

    static final double CLEF_X_POSITION_SS = 0.625;  // 5px

    // Beam geometry constants (staff-space units unless noted)
    // Not the drawn beam thickness (that is LineThickness.BEAM_THICKNESS_SS) — only
    // the base the raster-thickening factor scales.
    static final double BEAM_DEPTH_SS = 0.4;
    private static final double MIN_STEM_SS = SMuFLConstants.STEM_LENGTH_SS;

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

    /** LilyPond default layout line-thickness (ly/paper-defaults-init.ly), i.e. staff line thickness; staff spaces. */
    private static final double TIE_LINE_THICKNESS_SS = 0.1;
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
        (TIE_APEX_THICKNESS_RATIO * TIE_LINE_THICKNESS_SS - TIE_OUTLINE_THICKNESS_SS)
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
        verticalCalculator = new VerticalStackingCalculator();
    }

    /**
     * Executes the complete layout pipeline for a line.
     * Equivalent to {@code layout(line, false, false)}.
     *
     * @param line The line to lay out
     * @return LayoutResult with all positioned elements
     */
    public LayoutResult layout(Line line) {
        return layout(line, false, false);
    }

    /**
     * Executes the complete layout pipeline for a line.
     * Equivalent to {@code layout(line, isLastLine, false)}.
     *
     * @param line       The line to lay out
     * @param isLastLine Whether this line is the last line of the song
     * @return LayoutResult with all positioned elements
     */
    public LayoutResult layout(Line line, boolean isLastLine) {
        return layout(line, isLastLine, false);
    }

    /**
     * Executes the complete layout pipeline for a line.
     * <p>
     * This is the main entry point for layout. It orchestrates all calculators
     * and produces a final LayoutResult ready for rendering.
     *
     * @param line                        The line to lay out
     * @param isLastLine                  Whether this line is the last line of the song.
     *                                    When true, the final double barline (if present) is
     *                                    pinned flush with the right edge of the line.
     * @param hasLeadingLyricContinuation True when the previous line ended with an active
     *                                    melisma extender that should continue from x = 0
     *                                    on this line until the first syllable or rest that
     *                                    breaks it.
     * @return LayoutResult with all positioned elements
     */
    public LayoutResult layout(Line line, boolean isLastLine, boolean hasLeadingLyricContinuation) {
        return layout(line, isLastLine, hasLeadingLyricContinuation, null);
    }

    /**
     * Executes the complete layout pipeline for a line, optionally stacking an attribution block.
     * <p>
     * On the first line, pass the song's {@link Attribution} element (with dimensions pre-set via
     * {@link Attribution#setDimensionsSs}) to trigger attribution stacking above the right-edge
     * columns. Passing a non-null {@code attribution} implies this is the first line.
     *
     * @param line                        The line to lay out
     * @param isLastLine                  Whether this line is the last line of the song
     * @param hasLeadingLyricContinuation True when the previous line's lyric extender continues
     * @param attribution                 The attribution block element, or null if not applicable
     * @return LayoutResult with all positioned elements
     */
    public LayoutResult layout(
        Line line,
        boolean isLastLine,
        boolean hasLeadingLyricContinuation,
        @Nullable Attribution attribution) {

        // Accidental widths are a layout input. Initialise them here (idempotent and cheap)
        // so layout never precedes initialisation, regardless of paint/layout ordering.
        NoteGeometry.initializeAccidentalWidths();

        // Step 0: Resolve auto stem directions before building columns.
        // ElementColumn bakes each note's stem-tip extent from its direction at
        // construction time (final fields), and downstream vertical stacking
        // (e.g. tuplet clearance) reads those extents. Freshly loaded notes carry
        // the DOWN default until direction is resolved, so resolving here — rather
        // than later in steps 5/5b — keeps the first layout after load consistent
        // with every subsequent layout (refs #554).
        resolveStemDirections(line);

        // Step 1: Build note columns
        var columns = columnBuilder.buildColumns(line);

        // Steps 2-3b: Place the columns horizontally. An empty line has no chain to solve and
        // falls straight through to the shared passes below, which all resolve to no-ops or
        // natural zeros on empty columns. It deliberately gets no special-cased result: the
        // extents below feed inter-line spacing, so a line that invents content extents it does
        // not have spaces itself as if it held that content (refs #630).
        var fitsStaffWidth = placeColumnsHorizontally(columns, line, isLastLine);

        var builder = LayoutResult.builder();
        builder.setOverflowsStaffWidth(!fitsStaffWidth);

        // Step 4: Create header elements (clef and key signature)
        createHeaderElements(line, builder);

        // Step 5: Calculate beam layouts for beamed note groups
        calculateBeams(line, columns, builder);

        // Step 5b: Calculate stem layouts for unbeamed notes
        calculateUnbeamedStems(line, columns, builder);

        // Step 6: Calculate tie geometry for all tie spans
        calculateTies(line, columns, builder);

        // Step 6b: Calculate slide geometry for every note carrying a glissando or a fall
        calculateSlides(line, columns, builder);

        // Step 7: Calculate vertical positions (requires stem layouts from steps 5/5b)
        // Use the song's staff width for consistent StaffExtents clamping,
        // not the content width which varies with column count.
        verticalCalculator.calculate(
            columns, line, builder, staffRightMarginSs, fonts, attribution);

        // Step 7b: Compute lyric box and connector geometry, for the same verse step 1 built the
        // columns for — what makes their cached syllable widths reusable there.
        buildLyricLayout(
            columns, line.getSong().getActiveVerse(), builder, hasLeadingLyricContinuation);

        // Step 8: Build final LayoutResult
        return buildLayoutResult(columns, line, builder);
    }

    private void buildLyricLayout(
        List<ElementColumn> columns,
        int activeVerse,
        LayoutResultBuilder builder,
        boolean hasLeadingLyricContinuation) {

        var lyricResult = LyricLayoutBuilder.build(
            columns,
            activeVerse,
            lyricRenderMetrics,
            hasLeadingLyricContinuation,
            staffRightMarginSs);

        for (var entry : lyricResult.boxes().entrySet()) {
            for (var box : entry.getValue()) {
                builder.addLyricBox(entry.getKey(), box);
            }
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
     * (refs #696) — the alternative was to abandon the layout, and the line then did not draw.
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
        // column before the terminal hosts a grace note carrying a syllable (refs #696).
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

        var flushRightXSs = ElementType.terminalFlushRightXSs(staffRightMarginSs, lastType);

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
     * Resolves auto stem directions for every note in the line and applies them to the
     * model, before columns are built. Manual overrides are left untouched.
     * <p>
     * Unbeamed notes default by staff position ({@link StaffElement#defaultDirection}); beamed
     * groups then override their auto members with the shared group direction. This must run
     * before {@code buildColumns} because {@link ElementColumn} bakes each note's stem-tip
     * extent from its direction at construction time (refs #554).
     */
    private void resolveStemDirections(Line line) {
        for (var i = 0; i < line.elementCount(); i++) {
            var element = line.getElement(i);

            if (element.getType().isNoteWithStem() && element.isStemDirectionAuto()) {
                element.setDirection(StaffElement.defaultDirection(element));
            }
        }

        var debugBeams = LogUtils.isTracingBeams(LOG);

        for (var beam : line.findSpans(Beam.class)) {
            var beamStart = beam.getAnchorElementIndex();
            var beamEnd = beam.getEndElementIndex();
            var direction = groupStemDirection(line, beamStart, beamEnd);

            if (debugBeams) {
                LOG.debug("stem dirs: group {}..{} -> {}", beamStart, beamEnd, direction);
            }

            for (var i = beamStart; i <= beamEnd; i++) {
                var element = line.getElement(i);

                if (!BeamMath.participatesInBeaming(element)) {
                    if (debugBeams) {
                        LOG.debug("  [{}] GRACE keeps its own direction {}", i, element.getDirection());
                    }

                    continue;
                }

                if (element.isStemDirectionAuto()) {
                    element.setDirection(direction);
                }
            }
        }
    }

    /**
     * Determines the shared stem direction for a beamed group from its pitch contour.
     * The first manual override in the group wins; otherwise the direction follows the
     * contour (staff position 0 = middle line; positive = below midpoint in Y-down, so
     * stems up). (min + max) is compared to 0 rather than dividing, to keep integer arithmetic.
     * <p>
     * Grace notes in the span are not members: neither their pitch nor a manual direction set on
     * them may decide which way the group's stems point. They do get one say, though — a grace
     * note whose stem would run into a beam placed above it sends the beam below instead.
     */
    private StaffElement.Direction groupStemDirection(Line line, int beamStart, int beamEnd) {
        var minStaffPos = Integer.MAX_VALUE;
        var maxStaffPos = Integer.MIN_VALUE;

        for (var i = beamStart; i <= beamEnd; i++) {
            var element = line.getElement(i);

            if (!BeamMath.participatesInBeaming(element)) {
                continue;
            }

            var pos = element.getStaffPosition();

            if (pos < minStaffPos) {
                minStaffPos = pos;
            }

            if (pos > maxStaffPos) {
                maxStaffPos = pos;
            }
        }

        // Scan for any manual override in the group; first one wins.
        for (var i = beamStart; i <= beamEnd; i++) {
            var element = line.getElement(i);

            if (BeamMath.participatesInBeaming(element) && !element.isStemDirectionAuto()) {
                return element.getDirection();
            }
        }

        if ((minStaffPos + maxStaffPos) <= 0) {
            return StaffElement.Direction.DOWN;
        }

        // The contour asks for an upward beam. A grace stem always points up, so an upward beam
        // is the only one it can be trapped under; send the beam below rather than bury it.
        if (BeamMath.graceStemTouchesBeamAbove(line, beamStart, beamEnd, minStaffPos)) {
            if (LogUtils.isTracingBeams(LOG)) {
                LOG.debug("  grace stem forces group {}..{} to beam below", beamStart, beamEnd);
            }

            return StaffElement.Direction.DOWN;
        }

        return StaffElement.Direction.UP;
    }

    /**
     * Calculates beam geometry for all beamed note groups in the line.
     * Populates {@code builder} with a {@link LayoutResult.BeamLayout} for each beam span.
     */
    private void calculateBeams(
        Line line,
        List<ElementColumn> columns,
        LayoutResultBuilder builder) {
        var beams = line.findSpans(Beam.class);
        var elementToColumn = elementToColumnMap(columns);

        for (var beam : beams) {
            var beamStart = beam.getAnchorElementIndex();
            var beamEnd = beam.getEndElementIndex();

            // Stem direction was already resolved and applied to the group by the
            // Step 0 pre-pass (resolveStemDirections); recover it here for the beam
            // geometry below.
            var stemDirection = groupStemDirection(line, beamStart, beamEnd);

            // Collect the group's stems in BeamScoring's scoring space (Y-up positive,
            // y=0 at the middle staff line, X measured from the first stem).  Elements
            // without a column are skipped, mirroring computeBeamElementGeometry.
            var spanSize = beamEnd - beamStart + 1;
            var stemInputs = new ArrayList<BeamScoring.StemInput>(spanSize);
            var firstColumnXSs = 0.0;
            var forcedStemCount = 0;
            var memberCount = 0;

            for (var i = beamStart; i <= beamEnd; i++) {
                var element = line.getElement(i);

                // A grace note inside the span is not one of the beam's stems: its pitch must
                // not tilt the beam, skew the concaveness test, or dilute forcedFraction.
                if (!BeamMath.participatesInBeaming(element)) {
                    continue;
                }

                memberCount++;

                // LilyPond Beam::forced_stem_count: a middle-line head is never "forced".
                if (element.getStaffPosition() != 0
                    && element.getDirection() != StaffElement.defaultDirection(element)) {
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
                    -Staff.spToSs(element.getStaffPosition()),
                    -element.getStaffPosition(),
                    BeamMath.beamCount(element)));

                if (LogUtils.isTracingBeams(LOG)) {
                    LOG.debug(
                        "  scoring input [{}] {} sp={} dir={} beams={}",
                        i,
                        element.getType(),
                        element.getStaffPosition(),
                        element.getDirection(),
                        BeamMath.beamCount(element));
                }
            }

            if (LogUtils.isTracingBeams(LOG)) {
                LOG.debug(
                    "  span={} beamMembers={} scoredStems={} forcedStemCount={}",
                    spanSize, memberCount, stemInputs.size(), forcedStemCount);
            }

            // Beam geometry in SongScribe layout space (Y-down positive).  startYSs is
            // the beam's outer edge at the first stem; slope is Y-down per X.
            var slope = 0.0;
            var startYSs = 0.0;

            if (!stemInputs.isEmpty()) {
                var dirSign = stemDirection.isUp() ? 1 : -1;
                var forcedFraction = (double) forcedStemCount / memberCount;

                if (LogUtils.isTracingBeams(LOG)) {
                    LOG.debug("--- beam elements {}..{} ---", beamStart, beamEnd);
                }

                var beamPosition = BeamScoring.solve(stemInputs, dirSign, forcedFraction);
                var leftYUpSs = beamPosition.leftYUpSs();
                var rightYUpSs = beamPosition.rightYUpSs();
                var xSpanSs = stemInputs.getLast().xSs();

                startYSs = -leftYUpSs - dirSign * LineThickness.BEAM_THICKNESS_SS / 2.0;

                if (xSpanSs != 0.0) {
                    slope = -(rightYUpSs - leftYUpSs) / xSpanSs;
                }
            }

            // Beam thickening: angled beams appear thinner due to raster aliasing.
            // Compensate by increasing BEAM_DEPTH proportionally to 1/cos(angle),
            // clamped to a 3.3–8.8% increase over the nominal beam depth.
            var angle = Math.atan(slope);
            var factor = Math.clamp(1.0 / Math.cos(angle), 1.033, 1.088);
            var thickeningSs = BEAM_DEPTH_SS * (factor - 1.0);

            // Build StemLayout for each element in the beam group and accumulate into a map
            // for the BeamLayout.  All Y values are in staff-space with Y-down positive.
            //   stemsUp:   topYSs = beamYSs (above notehead, smaller Y),  bottomYSs = elementAnchorYSs
            //   stemsDown: topYSs = elementAnchorYSs,                     bottomYSs = beamYSs (below notehead, larger Y)
            var stemLayouts = new HashMap<StaffElement, LayoutResult.StemLayout>();

            if (!stemInputs.isEmpty()) {
                for (var i = beamStart; i <= beamEnd; i++) {
                    // A grace note's stem stops at its own length instead of running to the
                    // beam, so it is left to calculateUnbeamedStems rather than measured here.
                    if (!BeamMath.participatesInBeaming(line.getElement(i))) {
                        continue;
                    }

                    var geometry = computeBeamElementGeometry(line, i, elementToColumn, stemDirection.isUp(), slope, firstColumnXSs, startYSs);

                    if (geometry == null) {
                        continue;
                    }

                    var element = geometry.element();
                    var elementYSs = geometry.elementYSs();
                    var beamYSs = geometry.beamYSs();
                    var stemLenSs = geometry.stemLenSs();

                    // The quanted beam position is authoritative: express the resulting
                    // stem length as a delta from the standard stem length so
                    // NoteRenderer.renderStem reproduces exactly this tip.
                    var lengtheningSs = Math.max(0.0, stemLenSs - MIN_STEM_SS);
                    var forcedShorteningSs = Math.max(0.0, MIN_STEM_SS - stemLenSs);

                    var topYSs = stemDirection.isUp() ? beamYSs : elementYSs;
                    var bottomYSs = stemDirection.isUp() ? elementYSs : beamYSs;

                    // Determine stub direction for partial-beam elements.
                    // Delegated to BeamMath so the writer can use the same rule.
                    var stubRight = BeamMath.stubRight(line, i, beamStart, beamEnd);

                    // How far back French beaming pulls the drawn stem. Resolved here rather
                    // than in NoteRenderer so the per-paint path stays a field read, matching
                    // stubRight above.
                    var frenchShorteningLevels = BeamMath.frenchBeamShortening(line, i, beamStart, beamEnd);

                    if (LogUtils.isTracingBeams(LOG)) {
                        LOG.debug(
                            "  stem [{}] {} sp={} dir={} elementY={} beamY={} stemLen={} "
                                + "(natural={}) lengthening={} forcedShort={} french={}",
                            i,
                            element.getType(),
                            element.getStaffPosition(),
                            element.getDirection(),
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
                            topYSs, bottomYSs, lengtheningSs, forcedShorteningSs, stubRight, frenchShorteningLevels));
                }
            }

            var beamLayout = new LayoutResult.BeamLayout(slope, startYSs, stemDirection.isUp(), thickeningSs, stemLayouts);
            builder.putBeamLayout(beam, beamLayout);
        }
    }

    @Nullable
    private BeamElementGeometry computeBeamElementGeometry(
        Line line, int i, Map<StaffElement, ElementColumn> elementToColumn,
        boolean stemsUp, double slope, double firstXSs, double startYSs) {
        var element = line.getElement(i);
        var column = elementToColumn.get(element);

        if (column == null) {
            return null;
        }

        var elementYSs = Staff.spToSs(element.getStaffPosition());
        var beamYSs = slope * (column.getXSs() - firstXSs) + startYSs;
        var stemLenSs = stemsUp ? (elementYSs - beamYSs) : (beamYSs - elementYSs);
        return new BeamElementGeometry(element, elementYSs, beamYSs, stemLenSs);
    }

    /**
     * Calculates stem geometry for all elements whose stems are not drawn to a beam.
     * Populates {@code builder} with a {@link LayoutResult.StemLayout} for each such element.
     * <p>
     * A grace note sitting inside a beam's span belongs here too. Its column is flagged
     * beamed — {@link Line#findBeamAt} answers by index range — but its stem stops at the
     * grace length rather than running to the beam, so {@code calculateBeams} leaves it to
     * this pass.
     */
    private void calculateUnbeamedStems(
        Line line,
        List<ElementColumn> columns,
        LayoutResultBuilder builder) {
        for (var col : columns) {
            var element = col.getElement();

            if ((col.isBeamed() && BeamMath.participatesInBeaming(element))
                || !element.getType().isNoteWithStem()) {
                continue;
            }

            // Stem direction was resolved by the Step 0 pre-pass (resolveStemDirections).
            var direction = element.getDirection();
            var isGraceNote = element.getType().isGraceNote();
            var elementYSs = Staff.spToSs(element.getStaffPosition());
            var stemLenSs = isGraceNote
                ? SMuFLConstants.GRACE_NOTE_STEM_LENGTH_SS
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
            var forcedShorteningSs =
                NoteGeometry.forcedShorteningSs(element.getStaffPosition(), direction, isGraceNote);
            var effectiveLenSs = stemLenSs + lengtheningSs - forcedShorteningSs;

            // Y increases downward: stem-up tip has smaller Y; stem-down tip has larger Y.
            var topYSs = direction.isUp() ? elementYSs - effectiveLenSs : elementYSs;
            var bottomYSs = direction.isUp() ? elementYSs : elementYSs + effectiveLenSs;

            builder.putStemLayout(
                element,
                new LayoutResult.StemLayout(topYSs, bottomYSs, lengtheningSs, forcedShorteningSs, false, 0));
        }
    }

    /**
     * Calculates tie geometry for all tie spans in the line.
     * <p>
     * Ports LilyPond's single-tie {@code slur_shape} curve (bezier-bow.cc) plus endpoint gaps
     * and staff-line avoidance (tie-formatting-problem.cc) to SongScribe's staff-space coordinate
     * system. Each tie produces a filled lens shape defined by an outer and an inner cubic Bézier
     * curve that share start/end points, creating natural tapering at the endpoints.
     * Populates {@code builder} with a {@link LayoutResult.TieLayout} for each tie span.
     * <p>
     * A tie whose two notes sit in different lines is laid out once per line, and each line draws
     * only the half it owns; {@link #tieLayout} carries the geometry of both cases.
     */
    private void calculateTies(
        Line line,
        List<ElementColumn> columns,
        LayoutResultBuilder builder) {

        var ties = line.findTies();

        if (ties.isEmpty()) {
            return;
        }

        var elementToColumn = elementToColumnMap(columns);

        // Resolved once for the line: both limits depend on the line's own edges, not on any
        // tie, so every half running off an edge stops in the same place.
        var openLimits = new OpenTieLimits(
            HorizontalSpacingCalculator.calculateHeaderRightEdgeSs(line),
            lineEndTieLimitXSs(line, elementToColumn));

        for (var tie : ties) {
            var tieColumns = ElementColumn.resolveTie(tie, line, elementToColumn);

            if (tieColumns == null) {
                continue;
            }

            var layout = switch (tieColumns) {
                case TieColumns.Whole whole -> {
                    var spanColumns = whole.columns();

                    yield tieLayout(
                        tie,
                        spanColumns.anchor(),
                        spanColumns.anchorColumn(),
                        spanColumns.endColumn(),
                        OpenSide.NONE,
                        openLimits);
                }

                // The half this line owns keeps the column of the note that is in it; the other
                // side gets no column, which is what makes it terminate at the line's edge.
                case TieColumns.Half half when half.openSide() == OpenSide.END -> tieLayout(
                    tie, half.attached(), half.attachedColumn(), null, OpenSide.END, openLimits);

                case TieColumns.Half half -> tieLayout(
                    tie, half.attached(), null, half.attachedColumn(), OpenSide.START, openLimits);
            };

            builder.putTieLayout(tie, layout);
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

        if (!lastType.isBarLine() && !lastType.isRepeat()) {
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
     * A half is described by the same twelve numbers as a whole tie, and {@code openSide} records
     * which of its ends is a staff edge rather than a notehead. The half is a complete arc in its
     * own right, so nothing downstream has to reconstruct the side that is missing.
     *
     * @param tie          the tie being laid out
     * @param seatNote     the note this line seats the arc against — a tie joins two same-pitch
     *                     notes, so either endpoint yields the same staff position
     * @param anchorColumn the anchor (left) note's column, or {@code null} when that note is in an
     *                     earlier line and this half enters from where the music starts
     * @param endColumn    the end (right) note's column, or {@code null} when that note is in a
     *                     later line and this half exits through the staff's right edge
     * @param openSide     which end of the arc terminates at a staff edge, if either
     * @param openLimits   where this line's edges stop an open end; the side that attaches to a
     *                     notehead ignores its half of this
     */
    private LayoutResult.TieLayout tieLayout(
        Tie tie,
        StaffElement seatNote,
        @Nullable ElementColumn anchorColumn,
        @Nullable ElementColumn endColumn,
        OpenSide openSide,
        OpenTieLimits openLimits) {

        // Tie arc sign from Tie.arcSign() (LilyPond's both-stem fallthrough tree, shared with
        // the skyline seeder and MusicXML export). Y increases downward, so +1 → arc bulges
        // downward (tie below), -1 → arc bulges upward (tie above).
        //
        // Both halves of a broken tie get the same sign for free, because arcSign() reads the two
        // notehead elements rather than this line's columns. LilyPond arrives at the same answer
        // the long way round, reaching across the break for the head a half does not have
        // (tie.cc get_default_dir: `if (!one_head) one_head = head (me->broken_neighbor (d), d)`).
        var arcSignSs = tie.arcSign();

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
        var notePositionSp = seatNote.getStaffPosition();
        var dotRowCoincides = anchorColumn != null
            && tieSeatRowHasDot(anchorColumn.getElement(), notePositionSp, arcSignSs);
        var seatSs = tieSeatSs(notePositionSp, arcSignSs, dotRowCoincides);
        var endpointYSs = Staff.spToSs(notePositionSp) + arcSignSs * seatSs;

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
        var centerAttach = seatSs > Staff.STAFF_POSITION_OFFSET_SS;
        var startXSs = anchorColumn == null
            ? openLimits.startXSs() + NOTE_HEAD_GAP_SS
            : tieEndpointXSs(
                anchorColumn.getXSs(), 1, centerAttach, anchorColumn.getElement().getType());
        var endXSs = endColumn == null
            ? openLimits.endXSs() - NOTE_HEAD_GAP_SS
            : tieEndpointXSs(
                endColumn.getXSs(), -1, centerAttach, endColumn.getElement().getType());

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
            endpointYSs, arcSignSs, BezierBow.height(tieWidthSs, TIE_RATIO, TIE_HEIGHT_LIMIT_SS));
        var shoulderYSs = endpointYSs + arcSignSs * heightSs;

        // Outer/inner lens taper: outer control points offset away from the notes by the midpoint
        // half-thickness, inner control points offset toward the notes.
        var outerCpYSs = shoulderYSs + arcSignSs * TIE_MID_THICKNESS_SS;
        var innerCpYSs = shoulderYSs - arcSignSs * TIE_MID_THICKNESS_SS;

        if (LogUtils.isTracingTies(LOG)) {
            // LilyPond position convention (positive = up) for direct ground-truth comparison:
            // LilyPond ss is positive-up, SongScribe ss is positive-down, so negate and ×2.
            LOG.debug(
                "tie notePos={} arcSign={} seatSs={} width={} endpointPos={} apexPos={} heightSs={}"
                    + " openSide={}",
                notePositionSp, arcSignSs, seatSs, tieWidthSs,
                -2 * endpointYSs, -2 * shoulderYSs, heightSs, openSide);
        }

        return new LayoutResult.TieLayout(
            startXSs, endpointYSs,
            endXSs, endpointYSs,
            cp1XSs, outerCpYSs,
            cp2XSs, outerCpYSs,
            cp1XSs, innerCpYSs,
            cp2XSs, innerCpYSs,
            openSide
        );
    }

    /**
     * Calculates slide geometry for every note in the line carrying a
     * {@link StaffElement.Glissando} or a {@link StaffElement.Fall}, and populates {@code builder}
     * with a {@link LayoutResult.SlideLayout} for each one that has drawable geometry.
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
            var note = line.getElement(i);
            var slide = note.getSlide();

            if (slide == null) {
                continue;
            }

            var src = slideNoteContext(note, i, line, elementToColumn);

            // A fall is a standalone trailing glyph with no target note, so it never resolves the
            // following element.
            if (slide instanceof StaffElement.Fall) {
                builder.putSlideLayout(note, LayoutResult.SlideLayout.ofFall(
                    SlideGeometry.computeFallBoundsSs(src)));
                continue;
            }

            // The slide tool refuses to create a glissando between two notes at one pitch and a
            // pitch move strips one that becomes so, leaving import as the only way one can get
            // here. Storing no geometry keeps hit-testing and the MusicXML writer agreeing with
            // the renderer, which draws nothing for it.
            if (line.isSamePitchAsFollower(i)) {
                continue;
            }

            var endpoints = SlideGeometry.computeEndpoints(
                src, slideTargetContext(i, line, elementToColumn));

            if (endpoints == null) {
                continue;
            }

            builder.putSlideLayout(note, LayoutResult.SlideLayout.ofGlissando(endpoints));
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
        StaffElement note,
        int noteIndex,
        Line line,
        Map<StaffElement, ElementColumn> elementToColumn) {

        var column = elementToColumn.get(note);
        var elementXSs = column != null ? column.getXSs() : 0;

        return SlideGeometry.noteContextAt(
            note, elementXSs, line.findBeamAt(noteIndex) != null, 0);
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

        var nextElement = line.getElement(sourceIndex + 1);

        if (!nextElement.getType().isNote()) {
            return null;
        }

        return slideNoteContext(nextElement, sourceIndex + 1, line, elementToColumn);
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
     * @param notePositionSp  the tied notes' shared staff position (half staff-spaces)
     * @param arcSignSs       +1 when the arc bulges downward, -1 upward
     * @param dotRowCoincides whether the seat row coincides with the left note's dot row
     */
    static double tieSeatSs(int notePositionSp, int arcSignSs, boolean dotRowCoincides) {
        if (dotRowCoincides) {
            return Staff.STAFF_POSITION_OFFSET_SS + TIE_DOT_ROW_NUDGE_SS;
        }

        if (StaffElement.isLinePosition(notePositionSp)) {
            // Outer staff lines seat outside the staff (centered outside the notehead); inner lines
            // tuck into the adjacent space.
            return isOuterRealStaffLine(notePositionSp)
                ? TIE_OUTER_STAFF_LINE_SEAT_SS
                : STAFF_LINE_TIE_CLEARANCE_GAP_SS;
        }

        // A space note seats at the head-box edge; push past the edge row when it is a staff line.
        var edgeRowSp = notePositionSp + arcSignSs;

        if (isRealStaffLinePosition(edgeRowSp)) {
            return Staff.STAFF_POSITION_OFFSET_SS + STAFF_LINE_TIE_CLEARANCE_GAP_SS;
        }

        return Staff.STAFF_POSITION_OFFSET_SS;
    }

    /**
     * Whether the given staff position is one of the five real staff lines — an even position within
     * the staff — as opposed to a line position on a ledger row above or below the staff.
     *
     * @param staffPositionSp a staff position, in half staff-spaces
     */
    private static boolean isRealStaffLinePosition(int staffPositionSp) {
        return StaffElement.isLinePosition(staffPositionSp)
            && Math.abs(Staff.spToSs(staffPositionSp)) <= Staff.STAFF_HALF_SS;
    }

    /**
     * Whether the given staff position is one of the two <em>outer</em> staff lines — the top or
     * bottom line, i.e. a line position exactly {@link Staff#STAFF_HALF_SS} from the middle line.
     *
     * @param staffPositionSp a staff position, in half staff-spaces
     */
    private static boolean isOuterRealStaffLine(int staffPositionSp) {
        return StaffElement.isLinePosition(staffPositionSp)
            && Math.abs(Staff.spToSs(staffPositionSp)) == Staff.STAFF_HALF_SS;
    }

    /**
     * Whether the tie's seat row coincides with the left note's augmentation-dot row — LilyPond's
     * {@code generate_configuration} dot check ({@code dot_positions_.find(pos)}). Only a dotted note
     * sitting on a staff line qualifies: its dot is displaced one half-space toward the top
     * ({@link NoteGeometry#DOT_ON_LINE_Y_SHIFT_SS}) into the space an upward arc seats in. A note in a
     * space keeps its dot on its own row — two half-spaces from the seat — so it never coincides, and an
     * on-line note whose tie arcs the other way seats opposite the (always up-displaced) dot.
     *
     * @param note           the left (anchor) note of the tie
     * @param notePositionSp the note's staff position (half staff-spaces)
     * @param arcSignSs      +1 when the arc bulges downward, -1 upward
     */
    static boolean tieSeatRowHasDot(StaffElement note, int notePositionSp, int arcSignSs) {
        if (note.getDotCount() == 0 || !StaffElement.isLinePosition(notePositionSp)) {
            return false;
        }

        // Dot row = note row displaced up (Y-down); seat row = note row + one half-space in the arc
        // direction (LilyPond position_ += dir). They meet only for an upward arc into the dot's space.
        var dotRowSp = notePositionSp + Staff.ssToSp(NoteGeometry.DOT_ON_LINE_Y_SHIFT_SS);
        var seatRowSp = notePositionSp + arcSignSs;

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
     * @param arcSignSs       +1 when the arc bulges downward, -1 upward
     * @param naturalHeightSs the unadjusted slur_shape arc height
     */
    static double tieLineAvoidedHeightSs(double baseYSs, int arcSignSs, double naturalHeightSs) {
        var halfStrokeSs = TIE_OUTLINE_THICKNESS_SS / 2;

        // The staff line nearest the natural apex centerline; only a real (in-staff) line constrains.
        var apexOutwardSs = TIE_APEX_CONTROL_REACH * naturalHeightSs;
        var apexMidYSs = baseYSs + arcSignSs * apexOutwardSs;
        var nearestLineYSs = (double) Math.round(apexMidYSs);

        if (Math.abs(nearestLineYSs) > Staff.STAFF_HALF_SS) {
            return naturalHeightSs;
        }

        // Distance from the baseline out to the line, positive in the arc direction.
        var lineDistSs = arcSignSs * (nearestLineYSs - baseYSs);

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
     * Creates the clef and key signature header elements and stores them in the builder.
     * <p>
     * The clef is placed at {@link #CLEF_X_POSITION_SS}. The key signature follows at
     * {@link HorizontalSpacingCalculator#calculateKeySignatureXSs()}, one clef width plus
     * LilyPond's clef-to-key-signature gap further right.
     */
    private static void createHeaderElements(Line line, LayoutResultBuilder builder) {
        var clef = new Clef();
        clef.setPosition(CLEF_X_POSITION_SS, 0);
        builder.setClef(clef);
        var rawKeyType = line.getKeyType();
        var keyType = rawKeyType != null ? rawKeyType : KeyType.NONE;
        var keySig = new KeySignature(keyType, line.getKeyAccidentalCount());
        keySig.setPosition(HorizontalSpacingCalculator.calculateKeySignatureXSs(), 0);
        builder.setKeySignature(keySig);
    }

    /**
     * Returns the number of beams (flag levels) for a note type.
     * QUAVER = 1, SEMIQUAVER = 2, DEMI_SEMIQUAVER = 3.
     *
     * <p>Delegates to {@link BeamMath#beamCount} — the single source of truth
     * shared by the writer and renderer.
     */
    static int beamCount(StaffElement note) {
        return BeamMath.beamCount(note);
    }

    /**
     * Returns the staff right margin used by this engine.
     */
    public double getStaffRightMarginSs() {
        return staffRightMarginSs;
    }

    private record BeamElementGeometry(
        StaffElement element,
        double elementYSs,
        double beamYSs,
        double stemLenSs) {}
}
