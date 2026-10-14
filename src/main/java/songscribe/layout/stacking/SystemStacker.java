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
import songscribe.dom.Direction;
import songscribe.dom.TempoChangeAttachment;
import songscribe.font.DocumentFontsHolder;
import songscribe.layout.AttributionContent;
import songscribe.layout.ElementColumn;
import songscribe.layout.ElementSeat;
import songscribe.layout.Footprint;
import songscribe.layout.HorizontalSpacingCalculator;
import songscribe.layout.LayoutEngine.TempoMark;
import songscribe.layout.LayoutResult;
import songscribe.layout.LayoutResultBuilder;
import songscribe.layout.MetronomeContent;
import songscribe.layout.StaffExtents;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;

/**
 * Stacks system-tier decorations (tier 4): tempo markings, beat changes,
 * and annotations, and on the song's first line the two first-line decorations — the song's
 * tempo mark and its attribution block — which it typesets from the song and the document fonts.
 */
public class SystemStacker {

    public static final double TEMPO_MARGIN_SS = 1.0;  // 4px
    public static final double BEAT_CHANGE_MARGIN_SS = 1.0;  // 8px
    public static final double ANNOTATION_MARGIN_SS = 1.0;  // 8px

    private final StackingContext context;
    private final StackingLayer systemLayer;
    private final DocumentFontsHolder fonts;
    private final double lineWidthSs;

    public SystemStacker(
        StackingContext context,
        StaffExtents systemExtents,
        DocumentFontsHolder fonts,
        double lineWidthSs) {
        this.context = context;
        systemLayer = new StackingLayer(systemExtents, context.getBuilder().decorationLayouts());
        this.fonts = fonts;
        this.lineWidthSs = lineWidthSs;
    }

    public void stack(TempoMark tempoMark) {
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
     * Deliberately stacked before the column loop, so a beat change or annotation on the first
     * column stacks above it. The mark reserves no horizontal space.
     */
    private void stackTempoMark(LayoutResultBuilder builder) {
        var line = context.getLine();
        var song = line.getSong();
        var content = MetronomeContent.forTempo(song.getTempo(), fonts.getAnnotationFont());

        var xSs = HorizontalSpacingCalculator.calculateHeaderRightEdgeSs(line)
            + SMuFLMetadata.bboxSs(SMuFLGlyph.NOTEHEAD_BLACK).widthSs();

        var placed = systemLayer.stackAboveWithRegions(
            content.regions(),
            new Footprint(xSs, content.widthSs()),
            TEMPO_MARGIN_SS,
            StackingUtils.TOP_STAFF_LINE_POSITION);

        builder.decorationLayouts().putTypeset(
            song.getTempoMarkElement(), LayoutResult.DecorationLayout.Typeset.of(placed, content));
    }

    /**
     * Stacked after the column loop so that it clears every other system-tier decoration. The
     * layout is keyed by the song's {@link Attribution} element, which carries the user Y offset.
     */
    private void stackAttribution(LayoutResultBuilder builder) {
        var song = context.getLine().getSong();
        var content = AttributionContent.forSong(song, fonts);
        var widthSs = content.widthSs();
        var heightSs = content.heightSs();

        // Right-align with a small inset from the staff right edge
        var xSs = lineWidthSs - widthSs - Attribution.ATTRIBUTION_RIGHT_MARGIN_SS;

        var placed = systemLayer.stackAboveWithRegions(
            List.of(new CollisionRegion(0, 0, widthSs, heightSs)),
            new Footprint(xSs, widthSs),
            Attribution.ATTRIBUTION_MARGIN_BOTTOM_SS,
            StackingUtils.TOP_STAFF_LINE_POSITION);

        builder.decorationLayouts().putTypeset(
            song.getAttributionElement(), LayoutResult.DecorationLayout.Typeset.of(placed, content));
    }

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

    private void stackAnnotations(
        ElementColumn column,
        LayoutResultBuilder builder) {

        var note = column.getElement();
        var annotation = note.findAttachment(AnnotationAttachment.class);

        if (annotation == null) {
            return;
        }

        var columnXSs = column.getXSs();
        var staffPosition = ElementSeat.seatSp(note).value();
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
        // IntrinsicHeight. This is the explicit-height door.
        systemLayer.stackAtAnchor(annotation, heightSs, new Footprint(xSs, widthSs),
            new StaffAnchor(Direction.UP, staffPosition, ANNOTATION_MARGIN_SS));
    }

    private void stackMetronomeAttachment(
        MetronomeAttachment attachment,
        ElementColumn column,
        double marginSs,
        LayoutResultBuilder builder) {

        var xSs = column.getXSs();
        var staffPosition = ElementSeat.seatSp(column.getElement()).value();
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

        var placed = systemLayer.stackAboveWithRegions(
            content.regions(), new Footprint(xSs, content.widthSs()), marginSs, staffPosition);

        builder.decorationLayouts().putTypeset(
            attachment, LayoutResult.DecorationLayout.Typeset.of(placed, content));
    }
}
