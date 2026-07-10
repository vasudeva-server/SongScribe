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
import java.util.Map;

import songscribe.dom.Line;
import songscribe.dom.StaffElement;
import songscribe.dom.StaffElement.Direction;
import songscribe.dom.CollisionRegion;
import songscribe.dom.Crescendo;
import songscribe.dom.Diminuendo;
import songscribe.dom.DynamicAttachment;
import songscribe.layout.ElementColumn;
import songscribe.layout.Ending;
import songscribe.layout.LayoutResult;
import songscribe.dom.RangeElement;
import songscribe.layout.StaffExtents;
import songscribe.dom.Tuplet;
import songscribe.layout.NoteGeometry;

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
     */
    private void stackTuplets(
        Line line,
        Map<StaffElement, ElementColumn> columnsByElement,
        LayoutResult.Builder builder) {

        for (var tuplet : line.findRangeElements(Tuplet.class)) {
            var numberOnly = tuplet.isNumberOnly(line);
            var heightSs = numberOnly ? Tuplet.numberOnlyHeightSs() : Tuplet.bracketedHeightSs();
            stackSpanElement(tuplet, heightSs, TUPLET_MARGIN_SS, columnsByElement, builder);
        }
    }

    /**
     * Stacks all hairpins (crescendo/diminuendo) for the line.
     */
    private void stackHairpins(
        Line line,
        Map<StaffElement, ElementColumn> columnsByElement,
        LayoutResult.Builder builder) {

        for (var crescendo : line.findRangeElements(Crescendo.class)) {
            stackSpanElement(crescendo, crescendo.getContentHeightSs(), HAIRPIN_MARGIN_SS, columnsByElement, builder);
        }

        for (var diminuendo : line.findRangeElements(Diminuendo.class)) {
            stackSpanElement(diminuendo, diminuendo.getContentHeightSs(), HAIRPIN_MARGIN_SS, columnsByElement, builder);
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

        var contentWidthSs = dynamic.getContentWidthSs();
        var centeredXSs = column.getXSs()
            + NoteGeometry.getNoteheadCenterXSs(note) - contentWidthSs / 2.0;
        StackingUtils.placeAndReserveClamped(Direction.UP, structuralExtents, dynamic,
            centeredXSs, contentWidthSs, dynamic.getContentHeightSs(),
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
        LayoutResult.Builder builder) {

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
        double heightSs,
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
            heightSs, marginSs,
            staffPosition, builder);
    }

}
