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

import songscribe.music.Line;
import songscribe.ui.layout.AnnotationAttachment;
import songscribe.ui.layout.BeatChangeAttachment;
import songscribe.ui.layout.ElementColumn;
import songscribe.ui.layout.LayoutResult;
import songscribe.ui.layout.LayoutStylesheet;
import songscribe.ui.layout.StaffExtents;
import songscribe.ui.layout.TempoAttachment;

import static songscribe.ui.layout.stacking.StackingUtils.stackAbove;
import static songscribe.ui.layout.stacking.StackingUtils.stackAboveWithRegions;

/**
 * Stacks system-tier decorations (tier 4): tempo markings, beat changes,
 * and annotations.
 * <p>
 * Operates on the system {@link StaffExtents} layer, which starts as a copy
 * of the structural layer's top extents. All calculations are in staff-space units.
 */
public class SystemStacker {

    private final StackingContext context;
    private final StaffExtents systemExtents;

    public SystemStacker(StackingContext context, StaffExtents systemExtents) {
        this.context = context;
        this.systemExtents = systemExtents;
    }

    /**
     * Stacks all system-tier decorations in order: tempo, beat changes,
     * annotations.
     */
    public void stack() {
        var columns = context.getColumns();
        var line = context.getLine();
        var builder = context.getBuilder();

        for (var column : columns) {
            stackTempo(column, line, builder);
            stackBeatChange(column, line, builder);
            stackAnnotations(column, line, builder);
        }
    }

    /**
     * Stacks tempo marking for the given column.
     * <p>
     * Checks the new attachment hierarchy first; falls back to the legacy
     * {@code note.getTempoChange()} property and bridges it to a temporary
     * {@link TempoAttachment} so both paths write a {@link LayoutResult.DecorationLayout}.
     */
    private void stackTempo(
        ElementColumn column,
        Line line,
        LayoutResult.Builder builder) {

        var note = column.getElement();

        // Check new attachment hierarchy first
        var tempo = note.findAttachment(TempoAttachment.class);

        // Bridge legacy flag to a temporary TempoAttachment
        if (tempo == null && note.getTempoChange() != null) {
            tempo = new TempoAttachment(note, note.getTempoChange());
        }

        if (tempo == null) {
            return;
        }

        double xSs = column.getXSs();
        int staffPosition = note.getStaffPosition();
        var attrFontMetrics = line.getComposition().getAttributionFontMetrics();
        var metrics = tempo.computeContentMetrics(attrFontMetrics);

        stackAboveWithRegions(systemExtents, tempo, metrics.regions(), xSs,
            metrics.widthSs(), LayoutStylesheet.TEMPO_MARGIN_SS,
            staffPosition, builder);
    }

    /**
     * Stacks beat change for the given column.
     * <p>
     * Checks the new attachment hierarchy first; falls back to the legacy
     * {@code note.getBeatChange()} property and bridges it to a temporary
     * {@link BeatChangeAttachment} so both paths write a {@link LayoutResult.DecorationLayout}.
     */
    private void stackBeatChange(
        ElementColumn column,
        Line line,
        LayoutResult.Builder builder) {

        var note = column.getElement();

        // Check new attachment hierarchy first
        var beatChange = note.findAttachment(BeatChangeAttachment.class);

        // Bridge legacy flag to a temporary BeatChangeAttachment
        if (beatChange == null && note.getBeatChange() != null) {
            beatChange = new BeatChangeAttachment(note, note.getBeatChange());
        }

        if (beatChange == null) {
            return;
        }

        double xSs = column.getXSs();
        int staffPosition = note.getStaffPosition();
        double widthSs = beatChange.computeContentWidthSs(
            line.getComposition().getAttributionFontMetrics());
        stackAbove(systemExtents, beatChange, xSs,
            widthSs, beatChange.getContentHeightSs(),
            LayoutStylesheet.TEMPO_MARGIN_SS,
            staffPosition, builder);
    }

    /**
     * Stacks annotation for the given column.
     * <p>
     * Checks the new attachment hierarchy first; falls back to the legacy
     * {@code note.getAnnotation()} property and bridges it to a temporary
     * {@link AnnotationAttachment} so both paths write a {@link LayoutResult.DecorationLayout}.
     */
    private void stackAnnotations(
        ElementColumn column,
        Line line,
        LayoutResult.Builder builder) {

        var note = column.getElement();

        // Check new attachment hierarchy first
        var annotation = note.findAttachment(AnnotationAttachment.class);

        // Bridge legacy flag to a temporary AnnotationAttachment
        if (annotation == null && note.getAnnotation() != null) {
            annotation = new AnnotationAttachment(note, note.getAnnotation());
        }

        if (annotation == null) {
            return;
        }

        double xSs = column.getXSs();
        int staffPosition = note.getStaffPosition();
        double widthSs = annotation.computeContentWidthSs(
            line.getComposition().getAnnotationFontMetrics());
        stackAbove(systemExtents, annotation, xSs,
            widthSs, annotation.getContentHeightSs(),
            LayoutStylesheet.ANNOTATION_ABOVE_MARGIN_SS,
            staffPosition, builder);
    }
}
