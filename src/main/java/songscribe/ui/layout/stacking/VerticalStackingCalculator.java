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

package songscribe.ui.layout.stacking;

import java.util.List;

import songscribe.music.Line;

import songscribe.ui.layout.AnnotationAttachment;
import songscribe.ui.layout.ElementColumn;
import songscribe.ui.layout.Hairpin;
import songscribe.ui.layout.Ending;
import songscribe.ui.layout.LayoutResult;
import songscribe.ui.layout.LayoutStylesheet;
import songscribe.ui.layout.ScaleContext;
import songscribe.ui.layout.StaffExtents;
import songscribe.ui.layout.Trill;

/**
 * Orchestrates the vertical layout pipeline for a staff line.
 * <p>
 * Creates the three-layer {@link StaffExtents} model, delegates to specialized stackers
 * for each tier, then applies manual offsets. All calculations are in staff-space units.
 * Results are written directly to the {@link LayoutResult.Builder}.
 * <p>
 * Tier order:
 * <ol>
 *   <li><b>Note-attached layer</b> ({@link NoteAttachedStacker}): seeds note/tie bounds,
 *       then stacks articulations, fermata, and trills</li>
 *   <li><b>Structural layer</b> ({@link StructuralStacker}): dynamics hairpins, text dynamics,
 *       volta endings, tuplets — imports the note-attached top extents</li>
 *   <li><b>System layer</b> ({@link SystemStacker}): tempo, beat changes, annotations —
 *       imports the structural top extents</li>
 * </ol>
 * Each layer starts by importing the previous layer's top extents, ensuring higher layers
 * clear all lower-layer elements.
 */
public class VerticalStackingCalculator {

    /**
     * Calculates vertical positions for all elements in the given columns.
     * <p>
     * Creates the three-layer StaffExtents model, seeds note bounding areas, then processes
     * each decoration tier in order. Results are written directly to the builder.
     *
     * @param columns     list of element columns with X positions already set
     * @param line        the line being laid out
     * @param builder     the LayoutResult builder to write decoration positions into
     * @param lineWidthSs total width of the staff line in staff-space units
     */
    public void calculate(
        List<ElementColumn> columns,
        Line line,
        LayoutResult.Builder builder,
        double lineWidthSs) {

        var noteAttachedExtents = new StaffExtents(lineWidthSs);
        var structuralExtents = new StaffExtents(lineWidthSs);
        var systemExtents = new StaffExtents(lineWidthSs);

        var context = new StackingContext(columns, line, builder);

        // Tiers 0-2: seed note/tie bounds and stack note-attached decorations
        new NoteAttachedStacker(context, noteAttachedExtents).stack();

        // Initialize structural layer from note-attached layer
        structuralExtents.copyTopFrom(noteAttachedExtents);

        // Tier 3: structural decorations (tuplets, hairpins, dynamics, endings)
        new StructuralStacker(context, structuralExtents).stack();

        // Tier 4: system-level stacking (tempo, beat changes, annotations)
        systemExtents.copyTopFrom(structuralExtents);
        new SystemStacker(context, systemExtents).stack();

        // Apply manual offsets post-layout (no collision re-run)
        applyManualOffsets(builder);

        // Calculate lyrics baseline and line height
        double lowestNoteBotSs = context.getLowestNoteBotSs();
        double lyricsBaselineYSs = lowestNoteBotSs + LayoutStylesheet.LYRICS_BASELINE_OFFSET_SS;
        boolean hasLyrics = false;

        for (var column : columns) {
            if (column.hasSyllable()) {
                hasLyrics = true;
                break;
            }
        }
        double lyricsHeightSs = hasLyrics ? LayoutStylesheet.LYRICS_HEIGHT_SS : 0.0;

        // Find the highest point reached across all layers (most negative Y)
        double maxAboveStaffSs = systemExtents.yGet(true, 0, lineWidthSs);

        double lineHeightSs = LayoutStylesheet.STAFF_HEIGHT_SS
            + Math.abs(maxAboveStaffSs)
            + lyricsHeightSs
            + LayoutStylesheet.INTER_LINE_MARGIN_SS;

        builder.setLineHeightSs(lineHeightSs);
        builder.setLyricBaselineYSs(hasLyrics ? lyricsBaselineYSs : 0);
    }


    // ---- Manual offset application ----

    /**
     * Applies manual user offsets to all decoration and span layouts post-layout.
     * <p>
     * Per spec, offsets are applied after all collision detection is complete.
     * The user takes responsibility for any resulting overlaps — no collision
     * re-run occurs.
     * <p>
     * Offset sources:
     * <ul>
     *   <li>{@link songscribe.ui.layout.LineElement#getUserXOffsetSs()} /
     *       {@link songscribe.ui.layout.LineElement#getUserYOffsetSs()}:
     *       base offsets for all decoration elements</li>
     *   <li>{@link Trill#getYPositionSs()}: additional Y offset for trills</li>
     *   <li>{@link Ending#getYPositionSs()}: additional Y offset for endings</li>
     *   <li>{@link Crescendo#getYShift()} etc.: pixel-based hairpin shifts (converted to ss)</li>
     *   <li>{@link AnnotationAttachment} / {@link songscribe.music.Annotation#getUserYOffsetSs()}:
     *       legacy annotation Y offset</li>
     *   <li>{@link songscribe.music.DynamicsInterval}: legacy hairpin shifts (already in ss)</li>
     * </ul>
     */
    private void applyManualOffsets(LayoutResult.Builder builder) {
        applyDecorationOffsets(builder);
        applySpanOffsets(builder);
    }

    /**
     * Applies manual offsets to all {@link LayoutResult.DecorationLayout} entries.
     */
    private void applyDecorationOffsets(LayoutResult.Builder builder) {
        // Collect entries to avoid ConcurrentModificationException during iteration
        var entries = List.copyOf(builder.getDecorationLayoutEntries());

        for (var entry : entries) {
            var element = entry.getKey();
            var layout = entry.getValue();

            double xOffsetSs = element.getUserXOffsetSs();
            double yOffsetSs = element.getUserYOffsetSs();
            double widthAdjustSs = 0;

            // Element-specific additional offsets
            if (element instanceof Trill trill) {
                yOffsetSs += trill.getYPositionSs();
            } else if (element instanceof Ending ending) {
                yOffsetSs += ending.getYPositionSs();
            } else if (element instanceof Hairpin hairpin) {
                var shifts = convertHairpinShifts(
                    hairpin.getX1Shift(), hairpin.getX2Shift(), hairpin.getYShift());
                xOffsetSs += shifts.x1Ss();
                widthAdjustSs = shifts.widthAdjustSs();
                yOffsetSs += shifts.ySs();
            } else if (element instanceof AnnotationAttachment annAttach) {
                yOffsetSs += annAttach.getAnnotation().getUserYOffsetSs();
            }

            if (xOffsetSs != 0 || yOffsetSs != 0 || widthAdjustSs != 0) {
                builder.putDecorationLayout(element, new LayoutResult.DecorationLayout(
                    layout.xSs() + xOffsetSs,
                    layout.ySs() + yOffsetSs,
                    layout.widthSs() + widthAdjustSs,
                    layout.heightSs(),
                    layout.marginSs(),
                    layout.regions()));
            }
        }
    }

    /**
     * Converts pixel-based hairpin shifts (x1, x2, y) to staff-space offsets.
     */
    private record HairpinShifts(double x1Ss, double widthAdjustSs, double ySs) {
    }

    private static HairpinShifts convertHairpinShifts(int x1Px, int x2Px, int yPx) {
        var sc = ScaleContext.getInstance();
        double x1Ss = sc.fromPixels(x1Px);
        double x2Ss = sc.fromPixels(x2Px);
        return new HairpinShifts(x1Ss, x2Ss - x1Ss, sc.fromPixels(yPx));
    }

    /**
     * Applies manual offsets to all {@link LayoutResult.SpanLayout} entries.
     * <p>
     * Handles {@link songscribe.music.DynamicsInterval} shifts and
     * {@link songscribe.music.TupletInterval} vertical position adjustments.
     */
    private void applySpanOffsets(LayoutResult.Builder builder) {
        var entries = List.copyOf(builder.getSpanLayoutEntries());

        for (var entry : entries) {
            var interval = entry.getKey();
            var layout = entry.getValue();

            if (interval instanceof songscribe.music.DynamicsInterval dynInterval) {
                double x1ShiftSs = dynInterval.getX1ShiftSs();
                double x2ShiftSs = dynInterval.getX2ShiftSs();
                double yShiftSs = dynInterval.getYShiftSs();

                if (x1ShiftSs != 0 || x2ShiftSs != 0 || yShiftSs != 0) {
                    builder.putSpanLayout(interval, new LayoutResult.SpanLayout(
                        layout.startXSs() + x1ShiftSs,
                        layout.endXSs() + x2ShiftSs,
                        layout.ySs() + yShiftSs,
                        layout.heightSs()));
                }
            } else if (interval instanceof songscribe.music.TupletInterval tupletInterval) {
                double yShiftSs = tupletInterval.getVerticalPositionSs();

                if (yShiftSs != 0) {
                    builder.putSpanLayout(interval, new LayoutResult.SpanLayout(
                        layout.startXSs(),
                        layout.endXSs(),
                        layout.ySs() + yShiftSs,
                        layout.heightSs()));
                }
            }
        }
    }
}
