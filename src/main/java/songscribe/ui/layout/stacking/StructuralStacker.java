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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import songscribe.music.DynamicsSpan;
import songscribe.music.Line;
import songscribe.music.Span;
import songscribe.music.SpanSet;
import songscribe.music.StaffElement;
import songscribe.music.TupletSpan;
import songscribe.ui.layout.CollisionRegion;
import songscribe.ui.layout.Crescendo;
import songscribe.ui.layout.Diminuendo;
import songscribe.ui.layout.DynamicAttachment;
import songscribe.ui.layout.ElementColumn;
import songscribe.ui.layout.Ending;
import songscribe.ui.layout.Hairpin;
import songscribe.ui.layout.LayoutResult;
import songscribe.smufl.Engraving;
import songscribe.ui.layout.RangeElement;
import songscribe.ui.layout.StaffExtents;
import songscribe.ui.layout.Tuplet;
import songscribe.ui.renderer.LineThickness;

import org.jspecify.annotations.Nullable;

import static songscribe.ui.layout.stacking.StackingUtils.stackAbove;
import static songscribe.ui.layout.stacking.StackingUtils.stackAboveWithRegions;

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
     * Margin from reference point to tuplet bracket
     */
    public static final double TUPLET_MARGIN_SS = 0.625;  // 5px
    private final StackingContext context;
    private final StaffExtents structuralExtents;

    public StructuralStacker(StackingContext context, StaffExtents structuralExtents) {
        this.context = context;
        this.structuralExtents = structuralExtents;
    }

    /**
     * Stacks all structural-tier decorations in order: tuplets, hairpins,
     * text dynamics, endings.
     */
    public void stack() {
        var columns = context.getColumns();
        var line = context.getLine();
        var columnsByElement = context.getColumnsByElement();
        var builder = context.getBuilder();

        // Tier 3a: Tuplet brackets
        stackTuplets(line, columnsByElement, builder);

        // Tier 3b: Hairpins (crescendo/diminuendo)
        stackHairpins(line, columnsByElement, builder);

        // Tier 3c: Text dynamics (DynamicAttachment on notes)
        for (var column : columns) {
            stackTextDynamics(column, builder);
        }

        // Tier 3d: Volta brackets (endings)
        stackEndings(line, columnsByElement, builder);
    }

    /**
     * Stacks all tuplet brackets for the line.
     * <p>
     * Processes {@link Tuplet} range elements first, then bridges legacy
     * {@link TupletSpan} data not already covered
     * by range elements.
     */
    private void stackTuplets(
        Line line,
        Map<StaffElement, ElementColumn> columnsByElement,
        LayoutResult.Builder builder) {

        // Process new Tuplet range elements
        var existingTuplets = line.findRangeElements(Tuplet.class);

        for (var tuplet : existingTuplets) {
            stackSpanElement(tuplet, TUPLET_MARGIN_SS,
                columnsByElement, builder);
        }

        // Bridge legacy tuplet spans
        bridgeLegacyTupletSpans(line, existingTuplets, columnsByElement, builder);
    }

    /**
     * Bridges legacy {@link TupletSpan} data to temporary
     * {@link Tuplet} range elements and stacks them.
     */
    private void bridgeLegacyTupletSpans(
        Line line,
        List<? extends Tuplet> existingTuplets,
        Map<StaffElement, ElementColumn> columnsByElement,
        LayoutResult.Builder builder) {

        for (var iter = line.getTuplets().listIterator(); iter.hasNext(); ) {
            var span = iter.next();
            var resolved = resolveSpan(line, span, existingTuplets, columnsByElement);

            if (resolved == null) {
                continue;
            }

            var startNote = resolved.startNote();
            var bridged = new Tuplet(startNote, resolved.endNote(), span.getGrade());

            // Compute actual visual bracket bounds
            var noteheadRightXSs = resolved.startColumn().getXSs() + Engraving.NOTE_HEAD_WIDTH_SS;
            var endNoteheadRightXSs = resolved.endColumn().getXSs() + Engraving.NOTE_HEAD_WIDTH_SS;
            var isUpper = startNote.isUpper();
            var stemSs = LineThickness.getInstance().stemSs();
            var leftXSs = noteheadRightXSs - (isUpper ? stemSs : Engraving.NOTE_HEAD_WIDTH_SS) - Tuplet.ARM_EXTENSION_SS;
            var rightXSs = endNoteheadRightXSs + Tuplet.ARM_EXTENSION_SS;

            var staffPosition = startNote.getStaffPosition();
            var widthSs = rightXSs - leftXSs;
            var contentHeightSs = bridged.getContentHeightSs();

            var ySs = stackAbove(structuralExtents, bridged, leftXSs, widthSs,
                contentHeightSs, TUPLET_MARGIN_SS,
                staffPosition, builder);

            // Write SpanLayout with actual bracket bounds for renderer access
            builder.putSpanLayout(span,
                new LayoutResult.SpanLayout(leftXSs, rightXSs, ySs, contentHeightSs));
        }
    }

    /**
     * Stacks all hairpins (crescendo/diminuendo) for the line.
     * <p>
     * Processes {@link Crescendo} and {@link Diminuendo} range elements first,
     * then bridges legacy {@link DynamicsSpan} data not
     * already covered by range elements.
     */
    private void stackHairpins(
        Line line,
        Map<StaffElement, ElementColumn> columnsByElement,
        LayoutResult.Builder builder) {

        // Process new Crescendo range elements
        var existingCrescendos = line.findRangeElements(Crescendo.class);

        for (var crescendo : existingCrescendos) {
            stackSpanElement(crescendo, HAIRPIN_MARGIN_SS,
                columnsByElement, builder);
        }

        // Process new Diminuendo range elements
        var existingDiminuendos = line.findRangeElements(Diminuendo.class);

        for (var diminuendo : existingDiminuendos) {
            stackSpanElement(diminuendo, HAIRPIN_MARGIN_SS,
                columnsByElement, builder);
        }

        // Bridge legacy crescendo spans
        bridgeLegacyHairpinSpans(line, line.getCrescendos(),
            existingCrescendos, Crescendo::new, columnsByElement, builder);

        // Bridge legacy diminuendo spans
        bridgeLegacyHairpinSpans(line, line.getDiminuendos(),
            existingDiminuendos, Diminuendo::new, columnsByElement, builder);
    }

    /**
     * Bridges legacy {@link DynamicsSpan} data to temporary
     * hairpin range elements and stacks them.
     */
    private void bridgeLegacyHairpinSpans(
        Line line,
        SpanSet<DynamicsSpan> spanSet,
        List<? extends RangeElement> existingRangeElements,
        BiFunction<? super StaffElement, ? super StaffElement, ? extends RangeElement> factory,
        Map<StaffElement, ElementColumn> columnsByElement,
        LayoutResult.Builder builder) {

        for (var iter = spanSet.listIterator(); iter.hasNext(); ) {
            var span = iter.next();
            var resolved = resolveSpan(line, span, existingRangeElements, columnsByElement);

            if (resolved == null) {
                continue;
            }

            // Bridge to temporary range element for dimension calculations
            var startNote = resolved.startNote();
            var anchorXSs = resolved.startColumn().getXSs();
            var endXSs = resolved.endColumn().getXSs();
            var bridged = factory.apply(startNote, resolved.endNote());

            var staffPosition = startNote.getStaffPosition();
            var widthSs = endXSs - anchorXSs + Engraving.NOTE_HEAD_WIDTH_SS;
            var ySs = stackAbove(structuralExtents, bridged, anchorXSs, widthSs,
                Hairpin.HAIRPIN_OPENING_HEIGHT_SS, HAIRPIN_MARGIN_SS,
                staffPosition, builder);

            // Write SpanLayout keyed by the legacy span for renderer access
            builder.putSpanLayout(span,
                new LayoutResult.SpanLayout(anchorXSs, endXSs,
                    ySs, Hairpin.HAIRPIN_OPENING_HEIGHT_SS));
        }
    }

    /**
     * Stacks text dynamics (DynamicAttachment) for the given column.
     * <p>
     * Positions text dynamics (pp, p, mp, mf, f, ff, sfz, fp) in the structural tier
     * using collision detection against previously placed elements.
     */
    private void stackTextDynamics(
        ElementColumn column,
        LayoutResult.Builder builder) {

        var note = column.getElement();
        var dynamic = note.findAttachment(DynamicAttachment.class);

        if (dynamic == null) {
            return;
        }

        var columnXSs = column.getXSs();
        var contentWidthSs = dynamic.getContentWidthSs();
        var centeredXSs = columnXSs + note.getType().getFullElementCenterXSs() - contentWidthSs / 2.0;
        var staffPosition = note.getStaffPosition();
        stackAbove(structuralExtents, dynamic, centeredXSs,
            contentWidthSs, dynamic.getContentHeightSs(),
            NoteAttachedStacker.NOTE_DECORATION_MARGIN_SS,
            staffPosition, builder);
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
        LayoutResult.Builder builder) {

        var lt = LineThickness.getInstance();

        for (var ending : line.findRangeElements(Ending.class)) {
            var anchor = ending.getAnchorElement();

            if (anchor == null || ending.getEndElement() == null) {
                continue;
            }

            // Compute bracket ranges (stored on the Ending for renderer use)
            var brackets = ending.computeBracketRanges(
                line,
                e -> {
                    var col = columnsByElement.get(e);

                    if (col == null) {
                        throw new IllegalStateException(
                            "No column for element");
                    }

                    return col.getXSs();
                },
                lt);

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
                    ending.computeCollisionRegions(bracket, xBaseSs));
            }

            // Overall width = from first bracket start to last bracket end
            var widthSs = brackets.getLast().x2Ss() - anchorXSs;

            var staffPosition = anchor.getStaffPosition();

            stackAboveWithRegions(structuralExtents, ending, allRegions,
                anchorXSs, widthSs,
                ENDING_MARGIN_SS,
                staffPosition, builder);
        }
    }

    /**
     * Stacks a span element (hairpin, tuplet) that requires both anchor and end notes.
     * <p>
     * Resolves anchor/end columns, computes span width via the range element,
     * and delegates to {@link StackingUtils#stackAbove}.
     */
    private void stackSpanElement(
        RangeElement element,
        double marginSs,
        Map<StaffElement, ElementColumn> columnsByElement,
        LayoutResult.Builder builder) {

        var anchor = element.getAnchorElement();
        var endNote = element.getEndElement();

        if (anchor == null || endNote == null) {
            return;
        }

        var anchorColumn = columnsByElement.get(anchor);
        var endColumn = columnsByElement.get(endNote);

        if (anchorColumn == null || endColumn == null) {
            return;
        }

        var staffPosition = anchor.getStaffPosition();
        var anchorXSs = anchorColumn.getXSs();
        var endXSs = endColumn.getXSs();
        var widthSs = element.getSpanWidthSs(anchorXSs, endXSs);

        stackAbove(structuralExtents, element, anchorXSs, widthSs,
            element.getContentHeightSs(), marginSs,
            staffPosition, builder);
    }

    private record ResolvedSpan(
        StaffElement startNote,
        StaffElement endNote,
        ElementColumn startColumn,
        ElementColumn endColumn
    ) {}

    @Nullable
    private ResolvedSpan resolveSpan(
        Line line,
        Span span,
        List<? extends RangeElement> existingRangeElements,
        Map<StaffElement, ElementColumn> columnsByElement) {

        var startNote = line.getElement(span.getStart());
        var endNote = line.getElement(span.getEnd());

        if (StackingUtils.isRangeCovered(startNote, endNote, existingRangeElements)) {
            return null;
        }

        var startColumn = columnsByElement.get(startNote);
        var endColumn = columnsByElement.get(endNote);

        if (startColumn == null || endColumn == null) {
            return null;
        }

        return new ResolvedSpan(startNote, endNote, startColumn, endColumn);
    }
}
