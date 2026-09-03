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

import songscribe.dom.AnnotationAttachment;
import songscribe.dom.Attribution;
import songscribe.dom.BeatChangeAttachment;
import songscribe.dom.CollisionRegion;
import songscribe.dom.MetronomeAttachment;
import songscribe.dom.StaffElement.Direction;
import songscribe.dom.TempoChangeAttachment;
import songscribe.font.DocumentFontsHolder;
import songscribe.layout.AttributionContent;
import songscribe.layout.ElementColumn;
import songscribe.layout.HorizontalSpacingCalculator;
import songscribe.layout.LayoutEngine.TempoMark;
import songscribe.layout.LayoutResultBuilder;
import songscribe.layout.MetronomeContent;
import songscribe.layout.StaffExtents;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;

import static songscribe.layout.stacking.StackingUtils.stackAboveWithRegions;

/**
 * Stacks system-tier decorations (tier 4): tempo markings, beat changes,
 * and annotations, and on the song's first line the two first-line decorations — the song's
 * tempo mark and its attribution block — which it typesets from the song and the document fonts.
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
    private final TempoMark tempoMark;
    private final double lineWidthSs;

    /**
     * @param tempoMark   whether the song's tempo mark is stacked when the line is the first
     * @param lineWidthSs total width of the staff line in staff-space units, which the
     *                    attribution block is right-aligned to
     */
    public SystemStacker(
        StackingContext context,
        StaffExtents systemExtents,
        DocumentFontsHolder fonts,
        TempoMark tempoMark,
        double lineWidthSs) {
        this.context = context;
        this.systemExtents = systemExtents;
        this.fonts = fonts;
        this.tempoMark = tempoMark;
        this.lineWidthSs = lineWidthSs;
    }

    /**
     * Stacks all system-tier decorations in order: the song's own tempo mark, then per-column
     * tempo changes, beat changes and annotations, then the attribution block topmost. The
     * tempo mark and the attribution are stacked only when the line is its song's first.
     */
    public void stack() {
        var columns = context.getColumns();
        var builder = context.getBuilder();
        var line = context.getLine();
        var isFirstLine = line.isFirst();

        if (isFirstLine && tempoMark == TempoMark.STACKED) {
            stackTempoMark(builder);
        }

        for (var column : columns) {
            stackTempo(column, builder);
            stackBeatChange(column, builder);
            stackAnnotations(column, builder);
        }

        if (isFirstLine) {
            stackAttribution(builder);
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
        var line = context.getLine();
        var song = line.getSong();
        var content = MetronomeContent.forTempo(song.getTempo(), fonts.getAnnotationFont());

        stackAboveWithRegions(
            systemExtents,
            song.getTempoMarkElement(),
            content.regions(),
            HorizontalSpacingCalculator.calculateHeaderRightEdgeSs(line)
                + SMuFLMetadata.bboxSs(SMuFLGlyph.NOTEHEAD_BLACK).widthSs(),
            content.widthSs(),
            TEMPO_MARGIN_SS,
            StackingUtils.TOP_STAFF_LINE_POSITION,
            builder,
            content);
    }

    /**
     * Stacks the attribution block above the right-edge columns of the first line.
     * <p>
     * The attribution is right-aligned to the staff right edge. It is stacked over its x-range,
     * anchored at the top staff line, so it nests as close to the staff as the system-layer
     * extents allow. Stacked after the column loop so that it clears every other system-tier
     * decoration.
     * <p>
     * Its size comes from the content, never from the model: the block is a solid rectangle of
     * text, so it reserves one collision region covering the whole of it. The resulting
     * layout is keyed by the song's {@link Attribution} element, which carries the user Y offset.
     */
    private void stackAttribution(LayoutResultBuilder builder) {
        var song = context.getLine().getSong();
        var content = AttributionContent.forSong(song, fonts);
        var widthSs = content.widthSs();
        var heightSs = content.heightSs();

        // Right-align with a small inset from the staff right edge
        var xSs = lineWidthSs - widthSs - Attribution.ATTRIBUTION_RIGHT_MARGIN_SS;

        stackAboveWithRegions(
            systemExtents,
            song.getAttributionElement(),
            List.of(new CollisionRegion(0, 0, widthSs, heightSs)),
            xSs,
            widthSs,
            Attribution.ATTRIBUTION_MARGIN_BOTTOM_SS,
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
        var heightSs = annotation.computeContentHeightSs(annotationFont);

        var elementType = note.getType();
        var anchorWidthSs = elementType.getElementWidthSs();

        // The text is anchored to the matching point on the notehead: its left edge to the
        // notehead's left edge, its center to the center, its right edge to the right edge.
        var freeWidthSs = anchorWidthSs - widthSs;

        var xSs = switch (annotation.getAnnotation().alignment()) {
            case LEFT -> columnXSs;
            case CENTER -> columnXSs + freeWidthSs / 2;
            case RIGHT -> columnXSs + freeWidthSs;
        };

        // An annotation's height is the annotation font's line height, so it cannot be an
        // IntrinsicHeight and does not go through stackAbove. This is the explicit-height door.
        StackingUtils.stackAtAnchor(Direction.UP, systemExtents, annotation, xSs,
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
