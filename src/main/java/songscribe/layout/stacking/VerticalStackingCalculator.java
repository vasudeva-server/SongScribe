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
import songscribe.dom.CollisionRegion;
import songscribe.dom.Direction;
import songscribe.dom.Hairpin;
import songscribe.dom.Line;
import songscribe.dom.LineElement;
import songscribe.dom.NoteElement;
import songscribe.engraving.Staff;
import songscribe.engraving.StaffPosition;
import songscribe.font.DocumentFontsHolder;
import songscribe.layout.DecorationLayouts;
import songscribe.layout.ElementColumn;
import songscribe.layout.Footprint;
import songscribe.layout.LayoutEngine.TempoMark;
import songscribe.layout.LayoutResult;
import songscribe.layout.LayoutResultBuilder;
import songscribe.layout.NoteGeometry;
import songscribe.layout.StaffExtents;

/**
 * Orchestrates the vertical layout pipeline for a staff line.
 * <p>
 * The outer scripts (fermata, trill) come from {@link NoteAttachedStacker} but stack into the
 * structural layer after the tuplet brackets, mirroring LilyPond's {@code outside-staff-priority},
 * so the bracket's reserved footprint pushes them clear.
 */
public class VerticalStackingCalculator {

    private final double lineWidthSs;
    private final DocumentFontsHolder fonts;

    public VerticalStackingCalculator(double lineWidthSs, DocumentFontsHolder fonts) {
        this.lineWidthSs = lineWidthSs;
        this.fonts = fonts;
    }

    /**
     * @param columns list of element columns with X positions already set
     * @param line    must be in its song
     */
    public void calculate(
        List<ElementColumn> columns,
        Line line,
        LayoutResultBuilder builder,
        TempoMark tempoMark) {

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
        // outside it instead of colliding with them.
        noteAttachedStacker.stackOuterScripts(structuralExtents);

        // Must run after every script has been stacked so they all ignore accidentals, and before
        // the remaining structural elements, which do clear them.
        seedAccidentalsIntoStructural(columns, structuralExtents);

        // Tiers 3b-d: the rest of the structural decorations (hairpins, dynamics, endings)
        structuralStacker.stackRemaining();

        // Tiers 4-5: system-level stacking (the song's tempo mark, tempo changes, beat changes,
        // annotations), then, on the first line, the attribution block topmost
        systemExtents.copyTopFrom(structuralExtents);
        new SystemStacker(context, systemExtents, fonts, lineWidthSs).stack(tempoMark);

        // Apply manual offsets post-layout (no collision re-run)
        applyManualOffsets(builder);

        var contentAboveStaffSs = calculateContentAboveStaffSs(
            systemExtents, lineWidthSs, line, builder);

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
     * The result is the line's true content reach, unfloored: a floor here would only inflate the
     * inter-line gap, which the layout manager owns.
     * <p>
     * Callers must have applied manual offsets already: the attribution layout read here
     * <em>already includes</em> the user Y offset, so adding it again would reserve it twice.
     */
    private static double calculateContentAboveStaffSs(
        StaffExtents systemExtents,
        double lineWidthSs,
        Line line,
        LayoutResultBuilder builder) {

        var topExtentSs = systemExtents.yGet(Direction.UP, new Footprint(0, lineWidthSs));
        var contentAboveStaffSs = Math.max(0.0, -topExtentSs - Staff.STAFF_HALF_SS);

        if (!line.isFirst()) {
            return contentAboveStaffSs;
        }

        var attribution = line.getSong().getAttributionElement();

        if (attribution.getUserYOffsetSs() >= 0) {
            return contentAboveStaffSs;
        }

        var finalLayout = builder.decorationLayouts().requireDecorationLayout(attribution);
        var aboveFromAttribution = -finalLayout.ySs() - Staff.STAFF_HALF_SS;

        return Math.max(contentAboveStaffSs, aboveFromAttribution);
    }

    // ---- Accidental seeding ----

    static void seedAccidentalsIntoStructural(
        List<ElementColumn> columns,
        StaffExtents structuralExtents) {

        for (var column : columns) {
            if (!(column.getElement() instanceof NoteElement note)) {
                continue;
            }

            var bounds = NoteGeometry.getAccidentalBoundsSs(note);

            if (bounds == null) {
                continue;
            }

            // Translate from notehead-relative to absolute staff X.
            // bounds.leftSs() is negative (accidental is left of the notehead).
            var accXSs = column.getXSs() + bounds.leftSs();

            // bounds Y values are relative to the note center; shift to staff coordinates.
            var centerYSs = note.getStaffPosition().toSs();

            var accidentalFootprint = new Footprint(accXSs, bounds.widthSs());
            structuralExtents.ySet(Direction.UP, accidentalFootprint, bounds.topSs() + centerYSs);
            structuralExtents.ySet(
                Direction.DOWN, accidentalFootprint, bounds.bottomSs() + centerYSs);
        }
    }


    // ---- Manual offset application ----

    /**
     * Offsets are applied after all collision detection; no collision re-run occurs, so the user
     * owns any resulting overlap.
     */
    private void applyManualOffsets(LayoutResultBuilder builder) {
        builder.decorationLayouts().applyUserOffsets();
    }

}
