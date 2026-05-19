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

import songscribe.font.DocumentFontsHolder;
import songscribe.model.DynamicsSpan;
import songscribe.model.Line;

import songscribe.model.TupletSpan;
import songscribe.ui.layout.AnnotationAttachment;
import songscribe.ui.layout.ElementColumn;
import songscribe.ui.layout.Ending;
import songscribe.ui.layout.LayoutResult;
import songscribe.ui.layout.SongLayoutMetricsBuilder;
import songscribe.ui.layout.StaffExtents;
import songscribe.ui.layout.Trill;
import songscribe.ui.renderer.NoteRenderer;

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
        double lineWidthSs,
        DocumentFontsHolder fonts) {

        var noteAttachedExtents = new StaffExtents(lineWidthSs);
        var structuralExtents = new StaffExtents(lineWidthSs);
        var systemExtents = new StaffExtents(lineWidthSs);

        var context = new StackingContext(columns, line, builder);

        // Tiers 0-2: seed note/tie bounds and stack note-attached decorations
        new NoteAttachedStacker(context, noteAttachedExtents).stack();

        // Initialize structural layer from note-attached layer
        structuralExtents.copyTopFrom(noteAttachedExtents);

        // Must run after copyTopFrom(noteAttachedExtents) so note-attached stackers
        // (articulations/fermata/trills) ignore accidentals.
        seedAccidentalsIntoStructural(columns, structuralExtents);

        // Tier 3: structural decorations (tuplets, hairpins, dynamics, endings)
        new StructuralStacker(context, structuralExtents).stack();

        // Tier 4: system-level stacking (tempo, beat changes, annotations)
        systemExtents.copyTopFrom(structuralExtents);
        new SystemStacker(context, systemExtents, fonts).stack();

        // Apply manual offsets post-layout (no collision re-run)
        applyManualOffsets(builder);

        var lowestNoteBotSs = context.getLowestNoteBotSs();

        // Stacking coordinates put the middle staff line at y=0: staff top at
        // y=-STAFF_HALF_SS, staff bottom at y=+STAFF_HALF_SS. aboveStaffSs and
        // belowStaffSs are distances beyond the staff top/bottom respectively;
        // both subtract STAFF_HALF_SS from the signed extent.
        var topExtentSs = systemExtents.yGet(true, 0, lineWidthSs);
        var aboveStaffSs = Math.max(
            StaffExtents.MIN_ABOVE_STAFF_SS,
            -topExtentSs - StaffExtents.STAFF_HALF_SS);

        var botExtentSs = Math.max(
            Math.max(
                noteAttachedExtents.yGet(false, 0, lineWidthSs),
                structuralExtents.yGet(false, 0, lineWidthSs)),
            Math.max(
                systemExtents.yGet(false, 0, lineWidthSs),
                lowestNoteBotSs));
        var belowStaffSs = Math.max(
            StaffExtents.MIN_BELOW_STAFF_SS,
            botExtentSs - StaffExtents.STAFF_HALF_SS);

        // True extent of staff-element content below the staff bottom — distinct from the
        // sizing reservation above. Tracked in the context as elements seed their bounds
        // (notes, downward ties), defaults to staff bottom for an empty line.
        var belowContentSs = Math.max(
            0.0,
            context.getBotContentExtentSs() - StaffExtents.STAFF_HALF_SS);

        var lineHeightSs = StaffExtents.STAFF_HEIGHT_SS
            + aboveStaffSs
            + belowStaffSs
            + SongLayoutMetricsBuilder.INTER_LINE_MARGIN_SS;

        builder.setLineHeightSs(lineHeightSs);
        builder.setAboveStaffSs(aboveStaffSs);
        builder.setBelowContentSs(belowContentSs);
    }


    // ---- Accidental seeding ----

    /**
     * Seeds each note column's accidental bounding box into the structural extents layer.
     * <p>
     * Accidental bounds are relative to the notehead glyph origin ({@code column.getXSs()}).
     * Both top and bottom extents are reserved so structural elements clear the accidental
     * glyph in both directions (mirroring the symmetry of
     * {@link NoteAttachedStacker#seedNoteBounds()}).
     * <p>
     * Grace notes and notes without accidentals are skipped — they return {@code null}
     * from {@link NoteRenderer#getAccidentalBoundsSs}.
     *
     * @param columns          element columns with X positions already set
     * @param structuralExtents the structural-layer extents to seed into
     */
    static void seedAccidentalsIntoStructural(
        List<ElementColumn> columns,
        StaffExtents structuralExtents) {

        for (var column : columns) {
            var element = column.getElement();
            var bounds = NoteRenderer.getAccidentalBoundsSs(element);

            if (bounds == null) {
                continue;
            }

            // Translate from notehead-relative to absolute staff X.
            // bounds.leftSs() is negative (accidental is left of the notehead).
            var accXSs = column.getXSs() + bounds.leftSs();

            // bounds Y values are relative to the note center; shift to staff coordinates.
            var centerYSs = StaffExtents.spToSs(element.getStaffPosition());

            structuralExtents.ySet(true, accXSs, bounds.widthSs(), bounds.topSs() + centerYSs);
            structuralExtents.ySet(false, accXSs, bounds.widthSs(), bounds.botSs() + centerYSs);
        }
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
     *   <li>{@link AnnotationAttachment} / {@link songscribe.model.Annotation#getUserYOffsetSs()}:
     *       legacy annotation Y offset</li>
     *   <li>{@link DynamicsSpan}: hairpin shifts in staff-space units</li>
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

            var xOffsetSs = element.getUserXOffsetSs();
            var yOffsetSs = element.getUserYOffsetSs();

            // Element-specific additional offsets
            switch (element) {
                case Trill trill -> yOffsetSs += trill.getYPositionSs();
                case Ending ending -> yOffsetSs += ending.getYPositionSs();
                case AnnotationAttachment annAttach -> yOffsetSs += annAttach.getAnnotation().getUserYOffsetSs();
                default -> {
                }
            }

            if (xOffsetSs != 0 || yOffsetSs != 0) {
                builder.putDecorationLayout(element, new LayoutResult.DecorationLayout(
                    layout.xSs() + xOffsetSs,
                    layout.ySs() + yOffsetSs,
                    layout.widthSs(),
                    layout.heightSs(),
                    layout.marginSs(),
                    layout.regions()));
            }
        }
    }

    /**
     * Applies manual offsets to all {@link LayoutResult.SpanLayout} entries.
     * <p>
     * Handles {@link DynamicsSpan} shifts and
     * {@link TupletSpan} vertical position adjustments.
     */
    private void applySpanOffsets(LayoutResult.Builder builder) {
        var entries = List.copyOf(builder.getSpanLayoutEntries());

        for (var entry : entries) {
            var span = entry.getKey();
            var layout = entry.getValue();

            if (span instanceof DynamicsSpan dynSpan) {
                var x1ShiftSs = dynSpan.getX1ShiftSs();
                var x2ShiftSs = dynSpan.getX2ShiftSs();
                var yShiftSs = dynSpan.getYShiftSs();

                if (x1ShiftSs != 0 || x2ShiftSs != 0 || yShiftSs != 0) {
                    builder.putSpanLayout(span, new LayoutResult.SpanLayout(
                        layout.startXSs() + x1ShiftSs,
                        layout.endXSs() + x2ShiftSs,
                        layout.ySs() + yShiftSs,
                        layout.heightSs()));
                }
            } else if (span instanceof TupletSpan tupletSpan) {
                var yShiftSs = tupletSpan.getVerticalPositionSs();

                if (yShiftSs != 0) {
                    builder.putSpanLayout(span, new LayoutResult.SpanLayout(
                        layout.startXSs(),
                        layout.endXSs(),
                        layout.ySs() + yShiftSs,
                        layout.heightSs()));
                }
            }
        }
    }
}
