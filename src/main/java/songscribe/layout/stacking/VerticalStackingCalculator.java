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

import java.util.List;

import org.jspecify.annotations.Nullable;

import songscribe.dom.Attribution;
import songscribe.dom.LineElement;
import songscribe.font.DocumentFontsHolder;
import songscribe.dom.Line;
import songscribe.dom.AnnotationAttachment;
import songscribe.layout.ElementColumn;
import songscribe.layout.Ending;
import songscribe.dom.Hairpin;
import songscribe.layout.LayoutResult;
import songscribe.layout.SongLayoutMetricsBuilder;
import songscribe.layout.StaffExtents;
import songscribe.engraving.Staff;
import songscribe.dom.Trill;
import songscribe.dom.Tuplet;
import songscribe.layout.NoteGeometry;

/**
 * Orchestrates the vertical layout pipeline for a staff line.
 * <p>
 * Creates the three-layer {@link StaffExtents} model, delegates to specialized stackers
 * for each tier, then applies manual offsets. All calculations are in staff-space units.
 * Results are written directly to the {@link LayoutResult.Builder}.
 * <p>
 * Tier order:
 * <ol>
 *   <li><b>Note-attached layer</b> ({@link NoteAttachedStacker#stackInner()}): seeds note/tie
 *       bounds, then stacks the inner articulations (staccato, accent)</li>
 *   <li><b>Structural layer</b> ({@link StructuralStacker}) — imports the note-attached top
 *       extents, then stacks tuplet brackets, the outer scripts, and the remaining structural
 *       elements (dynamics hairpins, text dynamics, volta endings) in that order</li>
 *   <li><b>System layer</b> ({@link SystemStacker}): tempo, beat changes, annotations —
 *       imports the structural top extents</li>
 * </ol>
 * Each layer starts by importing the previous layer's top extents, ensuring higher layers
 * clear all lower-layer elements.
 * <p>
 * The outer scripts (fermata, trill) come from {@link NoteAttachedStacker} but stack into the
 * structural layer, between the tuplet brackets and the elements that follow them. This mirrors
 * LilyPond's {@code outside-staff-priority}: a tuplet bracket clears the priority-less scripts
 * (staccato, accent) but skips the priority ones, which then float outside the bracket
 * (tuplet-bracket.cc, {@code calc_position_and_height}). Their placement is therefore ordered
 * after the bracket's, so the bracket's reserved footprint pushes them clear.
 */
public class VerticalStackingCalculator {

    /**
     * Calculates vertical positions for all elements in the given columns.
     * Equivalent to {@code calculate(columns, line, builder, lineWidthSs, fonts, null)}.
     */
    public void calculate(
        List<ElementColumn> columns,
        Line line,
        LayoutResult.Builder builder,
        double lineWidthSs,
        DocumentFontsHolder fonts) {
        calculate(columns, line, builder, lineWidthSs, fonts, null);
    }

    /**
     * Calculates vertical positions for all elements in the given columns.
     * <p>
     * Creates the three-layer StaffExtents model, seeds note bounding areas, then processes
     * each decoration tier in order. Results are written directly to the builder.
     * <p>
     * On the first line, if {@code attribution} is non-null with non-zero dimensions, it is
     * stacked as the topmost tier (after {@link SystemStacker}) over its right-edge columns.
     * The resulting {@link LayoutResult.DecorationLayout} is keyed by the attribution element
     * so that {@link #applyManualOffsets} can apply the user Y offset. An upward (negative)
     * user Y offset is accounted for when computing {@code aboveStaffSs}, growing the above-staff
     * band and dropping the first staff via
     * {@link songscribe.ui.component.score.LineComponent#calculateMiddleLineYSs()}.
     *
     * @param columns      list of element columns with X positions already set
     * @param line         the line being laid out
     * @param builder      the LayoutResult builder to write decoration positions into
     * @param lineWidthSs  total width of the staff line in staff-space units
     * @param fonts        font holder for measuring elements
     * @param attribution  the attribution block element, or null if none to stack;
     *                     non-null implies this is the first line of the song
     */
    public void calculate(
        List<ElementColumn> columns,
        Line line,
        LayoutResult.Builder builder,
        double lineWidthSs,
        DocumentFontsHolder fonts,
        @Nullable Attribution attribution) {

        var noteAttachedExtents = new StaffExtents(lineWidthSs);
        var structuralExtents = new StaffExtents(lineWidthSs);
        var systemExtents = new StaffExtents(lineWidthSs);

        var context = new StackingContext(columns, line, builder);

        // Tiers 0-1: seed note/tie bounds and stack the inner, priority-less scripts
        var noteAttachedStacker = new NoteAttachedStacker(context, noteAttachedExtents);
        noteAttachedStacker.stackInner();

        // Initialize structural layer from note-attached layer
        structuralExtents.copyTopFrom(noteAttachedExtents);

        var structuralStacker = new StructuralStacker(context, structuralExtents);

        // Tier 3a: tuplet brackets. The bracket derives its own ceiling from the note tips and the
        // inner scripts, which the pass above has therefore already placed. It reserves that
        // footprint here so the outer scripts below stack clear of it.
        structuralStacker.stackTuplets();

        // Tier 2: the outside-priority scripts (fermata, trill). They stack into the structural
        // layer rather than their own so that a tuplet bracket, already reserved above, pushes them
        // outside it instead of colliding with them (issue #520).
        noteAttachedStacker.stackOuterScripts(structuralExtents);

        // Must run after every script has been stacked so they all ignore accidentals, and before
        // the remaining structural elements, which do clear them.
        seedAccidentalsIntoStructural(columns, structuralExtents);

        // Tiers 3b-d: the rest of the structural decorations (hairpins, dynamics, endings)
        structuralStacker.stackRemaining();

        // Tier 4: system-level stacking (tempo, beat changes, annotations)
        systemExtents.copyTopFrom(structuralExtents);
        new SystemStacker(context, systemExtents, fonts).stack();

        // Tier 5 (first line only): stack the attribution block above the right-edge columns
        if (attribution != null) {
            stackAttribution(attribution, systemExtents, lineWidthSs, builder);
        }

        // Apply manual offsets post-layout (no collision re-run)
        applyManualOffsets(builder);

        var lowestNoteBotSs = context.getLowestNoteBotSs();

        // Stacking coordinates put the middle staff line at y=0: staff top at
        // y=-STAFF_HALF_SS, staff bottom at y=+STAFF_HALF_SS. aboveStaffSs and
        // belowStaffSs are distances beyond the staff top/bottom respectively;
        // both subtract STAFF_HALF_SS from the signed extent.
        var topExtentSs = systemExtents.yGet(true, 0, lineWidthSs);
        var aboveStaffSs = Math.max(
            Staff.MIN_ABOVE_STAFF_SS,
            -topExtentSs - Staff.STAFF_HALF_SS);

        // An upward user Y offset on the attribution can raise it above the naturally
        // stacked position. Grow aboveStaffSs to accommodate so the first staff drops.
        if (attribution != null) {
            var userYOffsetSs = attribution.getUserYOffsetSs();

            if (userYOffsetSs < 0) {
                var naturalLayout = builder.getDecorationLayout(attribution);

                if (naturalLayout != null) {
                    var shiftedTopSs = naturalLayout.ySs() + userYOffsetSs;
                    var aboveFromAttribution = -shiftedTopSs - Staff.STAFF_HALF_SS;
                    aboveStaffSs = Math.max(aboveStaffSs, aboveFromAttribution);
                }
            }
        }

        var botExtentSs = Math.max(
            Math.max(
                noteAttachedExtents.yGet(false, 0, lineWidthSs),
                structuralExtents.yGet(false, 0, lineWidthSs)),
            Math.max(
                systemExtents.yGet(false, 0, lineWidthSs),
                lowestNoteBotSs));
        var belowStaffSs = Math.max(
            Staff.MIN_BELOW_STAFF_SS,
            botExtentSs - Staff.STAFF_HALF_SS);

        // True extent of staff-element content below the staff bottom — distinct from the
        // sizing reservation above. Tracked in the context as elements seed their bounds
        // (notes, downward ties), defaults to staff bottom for an empty line.
        var belowContentSs = Math.max(
            0.0,
            context.getBotContentExtentSs() - Staff.STAFF_HALF_SS);

        var lineHeightSs = Staff.STAFF_HEIGHT_SS
            + aboveStaffSs
            + belowStaffSs
            + SongLayoutMetricsBuilder.INTER_LINE_MARGIN_SS;

        builder.setLineHeightSs(lineHeightSs);
        builder.setAboveStaffSs(aboveStaffSs);
        builder.setBelowContentSs(belowContentSs);
    }

    /**
     * Stacks the attribution block above the right-edge columns of the first line.
     * <p>
     * The attribution is right-aligned to the staff right edge. It uses
     * {@link StackingUtils#stackAbove} over its x-range, anchored at the top staff line,
     * so it nests as close to the staff as the system-layer extents allow.
     *
     * @param attribution  the attribution block element with dimensions already set
     * @param systemExtents the system-tier extents (attribution stacks above these)
     * @param staffRightSs  the right edge of the staff in staff-space units
     * @param builder       the layout builder to receive the resulting DecorationLayout
     */
    private static void stackAttribution(
        Attribution attribution,
        StaffExtents systemExtents,
        double staffRightSs,
        LayoutResult.Builder builder) {

        var widthSs = attribution.getContentWidthSs();
        var heightSs = attribution.getContentHeightSs();

        if (widthSs <= 0 || heightSs <= 0) {
            return;
        }

        // Right-align with a small inset from the staff right edge
        var xSs = staffRightSs - widthSs - Attribution.ATTRIBUTION_RIGHT_MARGIN_SS;

        StackingUtils.stackAbove(
            systemExtents,
            attribution,
            xSs,
            widthSs,
            heightSs,
            Attribution.ATTRIBUTION_MARGIN_BOTTOM_SS,
            StackingUtils.TOP_STAFF_LINE_POSITION,
            builder);
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
            var bounds = NoteGeometry.getAccidentalBoundsSs(element);

            if (bounds == null) {
                continue;
            }

            // Translate from notehead-relative to absolute staff X.
            // bounds.leftSs() is negative (accidental is left of the notehead).
            var accXSs = column.getXSs() + bounds.leftSs();

            // bounds Y values are relative to the note center; shift to staff coordinates.
            var centerYSs = Staff.spToSs(element.getStaffPosition());

            structuralExtents.ySet(true, accXSs, bounds.widthSs(), bounds.topSs() + centerYSs);
            structuralExtents.ySet(false, accXSs, bounds.widthSs(), bounds.botSs() + centerYSs);
        }
    }


    // ---- Manual offset application ----

    /**
     * Applies manual user offsets to all decoration layouts post-layout.
     * <p>
     * Per spec, offsets are applied after all collision detection is complete.
     * The user takes responsibility for any resulting overlaps — no collision
     * re-run occurs.
     * <p>
     * Offset sources:
     * <ul>
     *   <li>{@link LineElement#getUserXOffsetSs()} /
     *       {@link LineElement#getUserYOffsetSs()}:
     *       base offsets for all decoration elements</li>
     *   <li>{@link Trill#getYPositionSs()}: additional Y offset for trills</li>
     *   <li>{@link Ending#getYPositionSs()}: additional Y offset for endings</li>
     *   <li>{@link AnnotationAttachment} / {@link songscribe.dom.Annotation#getUserYOffsetSs()}:
     *       legacy annotation Y offset</li>
     *   <li>{@link Tuplet#getVerticalPositionSs()}: tuplet bracket Y offset</li>
     *   <li>{@link Hairpin}: crescendo / diminuendo shifts in staff-space units</li>
     * </ul>
     */
    private void applyManualOffsets(LayoutResult.Builder builder) {
        applyDecorationOffsets(builder);
    }

    /**
     * Applies manual offsets to all {@link LayoutResult.DecorationLayout} entries.
     */
    void applyDecorationOffsets(LayoutResult.Builder builder) {
        // Collect entries to avoid ConcurrentModificationException during iteration
        var entries = List.copyOf(builder.getDecorationLayoutEntries());

        for (var entry : entries) {
            var element = entry.getKey();
            var layout = entry.getValue();

            var xOffsetSs = element.getUserXOffsetSs();
            var yOffsetSs = element.getUserYOffsetSs();

            // Element-specific additional offsets
            var x1ExtraSs = 0.0;
            var x2ExtraSs = 0.0;

            switch (element) {
                case Trill trill -> yOffsetSs += trill.getYPositionSs();
                case Ending ending -> yOffsetSs += ending.getYPositionSs();
                case AnnotationAttachment annAttach -> yOffsetSs += annAttach.getAnnotation().getUserYOffsetSs();
                case Tuplet tuplet -> yOffsetSs += tuplet.getVerticalPositionSs();
                case Hairpin hairpin -> {
                    x1ExtraSs = hairpin.getX1ShiftSs();
                    x2ExtraSs = hairpin.getX2ShiftSs();
                    yOffsetSs += hairpin.getYShiftSs();
                }
                default -> {
                }
            }

            if (xOffsetSs != 0 || yOffsetSs != 0 || x1ExtraSs != 0 || x2ExtraSs != 0) {
                // For hairpins, x1ExtraSs shifts the left edge and x2ExtraSs shifts the right edge
                // independently. Translate that into (xSs + x1, widthSs + x2 - x1).
                builder.putDecorationLayout(element, new LayoutResult.DecorationLayout(
                    layout.xSs() + xOffsetSs + x1ExtraSs,
                    layout.ySs() + yOffsetSs,
                    layout.dySs(),
                    layout.widthSs() + x2ExtraSs - x1ExtraSs,
                    layout.heightSs(),
                    layout.marginSs(),
                    layout.regions()));
            }
        }
    }

}
