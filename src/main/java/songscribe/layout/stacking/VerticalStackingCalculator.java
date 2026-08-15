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

import songscribe.dom.AnnotationAttachment;
import songscribe.dom.Attribution;
import songscribe.dom.Ending;
import songscribe.dom.Hairpin;
import songscribe.dom.Line;
import songscribe.dom.LineElement;
import songscribe.dom.SongTempoMark;
import songscribe.dom.Trill;
import songscribe.dom.Tuplet;
import songscribe.engraving.Staff;
import songscribe.font.DocumentFontsHolder;
import songscribe.layout.ElementColumn;
import songscribe.layout.LayoutResult;
import songscribe.layout.LayoutResultBuilder;
import songscribe.layout.NoteGeometry;
import songscribe.layout.StaffExtents;

/**
 * Orchestrates the vertical layout pipeline for a staff line.
 * <p>
 * Creates the three-layer {@link StaffExtents} model, delegates to specialized stackers
 * for each tier, then applies manual offsets. All calculations are in staff-space units.
 * Results are written directly to the {@link LayoutResultBuilder}.
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
     * Equivalent to {@code calculate(columns, line, builder, lineWidthSs, fonts, null, null)}.
     */
    public void calculate(
        List<ElementColumn> columns,
        Line line,
        LayoutResultBuilder builder,
        double lineWidthSs,
        DocumentFontsHolder fonts) {
        calculate(columns, line, builder, lineWidthSs, fonts, null, null);
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
     * @param tempoMark    the song's tempo mark, or null if none to stack; non-null implies this
     *                     is the first line of the song
     * @param attribution  the attribution block element, or null if none to stack;
     *                     non-null implies this is the first line of the song
     */
    public void calculate(
        List<ElementColumn> columns,
        Line line,
        LayoutResultBuilder builder,
        double lineWidthSs,
        DocumentFontsHolder fonts,
        @Nullable SongTempoMark tempoMark,
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

        // Tier 4: system-level stacking (the song's tempo mark, tempo changes, beat changes,
        // annotations)
        systemExtents.copyTopFrom(structuralExtents);
        new SystemStacker(context, systemExtents, fonts, tempoMark).stack();

        // Tier 5 (first line only): stack the attribution block above the right-edge columns
        if (attribution != null) {
            stackAttribution(attribution, systemExtents, lineWidthSs, builder);
        }

        // Apply manual offsets post-layout (no collision re-run)
        applyManualOffsets(builder);

        var contentAboveStaffSs = calculateContentAboveStaffSs(
            systemExtents, lineWidthSs, attribution, builder);

        // True extent of content below the staff bottom, unfloored. Only the note-attached
        // layer ever places anything below the staff — the structural and system layers
        // (hairpins, dynamics, endings, tempo, annotations) always stack above it — so the
        // note-attached content extent is the whole story. It tracks true ink (notehead and
        // stem bottoms, downward ties, the outermost below-staff articulation) rather than
        // reserved footprints, which is what lyrics must clear.
        var contentBelowStaffSs = Math.max(
            0.0,
            context.getBotContentExtentSs() - Staff.STAFF_HALF_SS);

        builder.setContentAboveStaffSs(contentAboveStaffSs);
        builder.setContentBelowStaffSs(contentBelowStaffSs);
    }

    /**
     * Measures how far content reaches above the staff top, in staff-space units.
     * <p>
     * Stacking coordinates put the middle staff line at y=0, so the staff top is at
     * y=-{@link Staff#STAFF_HALF_SS} and the signed extent has that subtracted from it.
     * The result is the line's true content reach, unfloored — the inter-line gap is owned
     * by the layout manager that positions the lines, so a floor here would only inflate
     * the gap (refs #591).
     * <p>
     * An upward (negative) user Y offset can raise the attribution above the stacked
     * extents, which only track its natural position. The attribution's final layout is
     * therefore measured directly so the band grows to match and the first staff drops.
     * Callers must have applied manual offsets already: the layout read here is the final
     * painted position and <em>already includes</em> the offset, so adding it again would
     * reserve twice the shift.
     *
     * @param systemExtents the system-tier extents to measure
     * @param lineWidthSs   total width of the staff line in staff-space units
     * @param attribution   the attribution block element, or null if none was stacked
     * @param builder       the builder holding the already-offset decoration layouts
     * @return the extent of content above the staff top, never negative
     */
    private static double calculateContentAboveStaffSs(
        StaffExtents systemExtents,
        double lineWidthSs,
        @Nullable Attribution attribution,
        LayoutResultBuilder builder) {

        var topExtentSs = systemExtents.yGet(true, 0, lineWidthSs);
        var contentAboveStaffSs = Math.max(0.0, -topExtentSs - Staff.STAFF_HALF_SS);

        if (attribution == null || attribution.getUserYOffsetSs() >= 0) {
            return contentAboveStaffSs;
        }

        var finalLayout = builder.getDecorationLayout(attribution);

        if (finalLayout == null) {
            return contentAboveStaffSs;
        }

        var aboveFromAttribution = -finalLayout.ySs() - Staff.STAFF_HALF_SS;

        return Math.max(contentAboveStaffSs, aboveFromAttribution);
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
        LayoutResultBuilder builder) {

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
    private void applyManualOffsets(LayoutResultBuilder builder) {
        applyDecorationOffsets(builder);
    }

    /**
     * Applies manual offsets to all {@link LayoutResult.DecorationLayout} entries.
     */
    void applyDecorationOffsets(LayoutResultBuilder builder) {
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
                builder.putDecorationLayout(element, layout.shiftedBy(
                    xOffsetSs + x1ExtraSs, yOffsetSs, x2ExtraSs - x1ExtraSs));
            }
        }
    }

}
