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

import org.jspecify.annotations.Nullable;

import songscribe.font.DocumentFontsHolder;
import songscribe.dom.AnnotationAttachment;
import songscribe.dom.BeatChangeAttachment;
import songscribe.layout.HorizontalSpacingCalculator;
import songscribe.layout.LayoutResultBuilder;
import songscribe.layout.ElementColumn;
import songscribe.dom.MetronomeAttachment;
import songscribe.layout.MetronomeContent;
import songscribe.dom.ScaleContext;
import songscribe.engraving.SMuFLConstants;
import songscribe.layout.StaffExtents;
import songscribe.dom.SongTempoMark;
import songscribe.dom.TempoChangeAttachment;

import static songscribe.layout.stacking.StackingUtils.stackAbove;
import static songscribe.layout.stacking.StackingUtils.stackAboveWithRegions;

/**
 * Stacks system-tier decorations (tier 4): tempo markings, beat changes,
 * and annotations.
 * <p>
 * Operates on the system {@link StaffExtents} layer, which starts as a copy
 * of the structural layer's top extents. All calculations are in staff-space units.
 */
public class SystemStacker {

    /**
     * Margin from reference point to tempo marking
     */
    public static final double TEMPO_MARGIN_SS = 1.0;  // 4px
    /**
     * Margin from reference point to beat change
     */
    public static final double BEAT_CHANGE_MARGIN_SS = 1.0;  // 8px
    /**
     * Margin from reference point to annotation
     */
    public static final double ANNOTATION_MARGIN_SS = 1.0;  // 8px
    private final StackingContext context;
    private final StaffExtents systemExtents;
    private final DocumentFontsHolder fonts;
    private final @Nullable SongTempoMark tempoMark;

    public SystemStacker(
        StackingContext context,
        StaffExtents systemExtents,
        DocumentFontsHolder fonts,
        @Nullable SongTempoMark tempoMark) {
        this.context = context;
        this.systemExtents = systemExtents;
        this.fonts = fonts;
        this.tempoMark = tempoMark;
    }

    /**
     * Stacks all system-tier decorations in order: the song's own tempo mark, then per-column
     * tempo changes, beat changes and annotations.
     */
    public void stack() {
        var columns = context.getColumns();
        var builder = context.getBuilder();

        stackTempoMark(builder);

        for (var column : columns) {
            stackTempo(column, builder);
            stackBeatChange(column, builder);
            stackAnnotations(column, builder);
        }
    }

    /**
     * Stacks the song's tempo mark at the right edge of the first line's staff header.
     * <p>
     * Deliberately stacked before the column loop: everything a note owns is already in
     * {@code systemExtents}, so ledger lines and high notes push the mark up, while a beat change
     * or annotation on the first column stacks above it — the same relationship
     * {@link #stackTempo} already has with {@link #stackBeatChange} within a column.
     * <p>
     * The mark reserves no horizontal space; it begins one notehead width right of the header's
     * right edge and overhangs the music to its right.
     */
    private void stackTempoMark(LayoutResultBuilder builder) {
        if (tempoMark == null) {
            return;
        }

        var line = context.getLine();
        var content = MetronomeContent.forTempo(line.getSong().getTempo(), fonts.getAnnotationFont());

        // showTempo false with an empty description leaves nothing to draw.
        if (content.widthSs() <= 0) {
            return;
        }

        stackAboveWithRegions(
            systemExtents,
            tempoMark,
            content.regions(),
            HorizontalSpacingCalculator.calculateHeaderRightEdgeSs(line) + SMuFLConstants.NOTE_HEAD_WIDTH_SS,
            content.widthSs(),
            TEMPO_MARGIN_SS,
            StackingUtils.TOP_STAFF_LINE_POSITION,
            builder,
            content);
    }

    /**
     * Stacks tempo marking for the given column.
     */
    private void stackTempo(
        ElementColumn column,
        LayoutResultBuilder builder) {

        var note = column.getElement();
        var tempo = note.findAttachment(TempoChangeAttachment.class);

        if (tempo == null) {
            return;
        }

        stackMetronomeAttachment(tempo, column, TEMPO_MARGIN_SS, builder);
    }

    /**
     * Stacks beat change for the given column.
     */
    private void stackBeatChange(
        ElementColumn column,
        LayoutResultBuilder builder) {

        var note = column.getElement();
        var beatChange = note.findAttachment(BeatChangeAttachment.class);

        if (beatChange == null) {
            return;
        }

        stackMetronomeAttachment(beatChange, column, BEAT_CHANGE_MARGIN_SS, builder);
    }

    /**
     * Stacks the annotation attachment for the given column, if present.
     */
    private void stackAnnotations(
        ElementColumn column,
        LayoutResultBuilder builder) {

        var note = column.getElement();
        var annotation = note.findAttachment(AnnotationAttachment.class);

        if (annotation == null) {
            return;
        }

        var columnXSs = column.getXSs();
        var staffPosition = note.getStaffPosition();
        var annotationFont = fonts.getAnnotationFont();
        var widthSs = annotation.computeContentWidthSs(annotationFont);
        var heightSs = ScaleContext.textHeightSs(annotationFont).value();

        // xAlignment is 0.0 (left), 0.5 (center), or 1.0 (right). The text is
        // anchored to the matching point on the notehead: left edge → left,
        // center → center, right edge → right.
        double xAlignment = annotation.getAnnotation().getXAlignment();

        var elementType = note.getType();
        var anchorWidthSs = elementType.getElementWidthSs();

        var xSs = columnXSs + xAlignment * (anchorWidthSs - widthSs);

        stackAbove(systemExtents, annotation, xSs,
            widthSs, heightSs,
            ANNOTATION_MARGIN_SS,
            staffPosition, builder);
    }

    private void stackMetronomeAttachment(
        MetronomeAttachment attachment,
        ElementColumn column,
        double marginSs,
        LayoutResultBuilder builder) {

        var xSs = column.getXSs();
        var staffPosition = column.getElement().getStaffPosition();
        var font = fonts.getAnnotationFont();
        var content = switch (attachment) {
            case BeatChangeAttachment beatChangeAttachment ->
                MetronomeContent.forBeatChange(beatChangeAttachment.getBeatChange(), font);
            case TempoChangeAttachment tempoChangeAttachment ->
                MetronomeContent.forTempo(tempoChangeAttachment.getTempo(), font);
        };

        // showTempo false with an empty description leaves nothing to draw. Stacking it anyway
        // would place it from an empty region list, leaving its Y at Double.MAX_VALUE.
        if (content.widthSs() <= 0) {
            return;
        }

        stackAboveWithRegions(systemExtents, attachment, content.regions(), xSs,
            content.widthSs(), marginSs, staffPosition, builder, content);
    }
}
