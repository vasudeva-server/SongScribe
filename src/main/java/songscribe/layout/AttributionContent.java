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

package songscribe.layout;

import java.awt.Font;
import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import songscribe.dom.AttributionFormatter;
import songscribe.dom.AttributionLine;
import songscribe.dom.DocumentScale;
import songscribe.dom.Song;
import songscribe.dom.SongAttribution;
import songscribe.error.RuntimeError;
import songscribe.font.DocumentFontsHolder;
import songscribe.font.FontKey;
import songscribe.font.TextMeasurement;

/**
 * The positioned, measured content of the song's attribution block — the credit, date and place
 * lines drawn above the right end of the first line's staff. Lines are centered against
 * {@link #widthSs} at typeset time, so the renderer positions nothing.
 *
 * @param lines    the typeset lines, top to bottom, positioned relative to the block's top-left
 *                 corner
 * @param widthSs  the widest line's ink width, in staff spaces; never zero
 * @param heightSs the block's full height, in staff spaces: every line box, one leading per
 *                 inter-line gap, and the sub-attribution gap where the two roles meet
 */
public record AttributionContent(List<TextItem> lines, double widthSs, double heightSs)
    implements DecorationContent {

    private static final double LEADING_SS = 0.5;

    private static final double SUB_ATTRIBUTION_GAP_SS = 0.5;

    private static final String LINE_BOX_REFERENCE = "Ty";

    public AttributionContent {
        lines = List.copyOf(lines);
    }

    public static AttributionContent forSong(Song song, DocumentFontsHolder fonts) {
        return forLines(
            AttributionFormatter.lines(song.getMetadata().attribution(), song.showTranslation()),
            fonts.getAttributionFont(),
            fonts.getSubAttributionFont());
    }

    /**
     * @param lines             the lines to typeset, in order; never empty, no line blank, and
     *                           every credit line precedes every sub-attribution line
     * @param attributionFont    the font for {@link FontKey#ATTRIBUTION} lines, sized in document
     *                           pixels
     * @param subAttributionFont the font for {@link FontKey#SUB_ATTRIBUTION} lines, sized in
     *                           document pixels
     */
    public static AttributionContent forLines(
        List<AttributionLine> lines,
        Font attributionFont,
        Font subAttributionFont) {

        var attributionBox = lineBox(attributionFont);
        var subAttributionBox = lineBox(subAttributionFont);
        var scaledAttributionFont = DocumentScale.fontSizedInSs(attributionFont);
        var scaledSubAttributionFont = DocumentScale.fontSizedInSs(subAttributionFont);
        var firstSubAttributionIndex = firstSubAttributionIndex(lines);

        var measured = new ArrayList<MeasuredLine>(lines.size());
        var widthSs = 0.0;
        var offsetSs = 0.0;
        var lastIndex = lines.size() - 1;

        for (var i = 0; i <= lastIndex; i++) {
            var line = lines.get(i);
            var isAttribution = line.font() == FontKey.ATTRIBUTION;
            var font = isAttribution ? attributionFont : subAttributionFont;
            var lineBox = isAttribution ? attributionBox : subAttributionBox;

            if (firstSubAttributionIndex != null && i == firstSubAttributionIndex) {
                offsetSs += SUB_ATTRIBUTION_GAP_SS;
            }

            // The ink rather than the advance: centering measures ink so a negative left bearing
            // (the "W" in "Words") does not overhang the box.
            var inkPx = TextMeasurement.visualBounds(line.text(), font);

            if (inkPx == null) {
                throw RuntimeError.exit("attribution line has no ink: \"" + line.text() + "\"");
            }

            var inkWidthSs = DocumentScale.pxToSs(inkPx.getWidth());
            widthSs = Math.max(widthSs, inkWidthSs);
            measured.add(new MeasuredLine(
                line.text(),
                isAttribution ? scaledAttributionFont : scaledSubAttributionFont,
                inkWidthSs,
                DocumentScale.pxToSs(inkPx.getX()),
                offsetSs + lineBox.ascentSs()));

            offsetSs += lineBox.heightSs();

            if (i < lastIndex) {
                offsetSs += LEADING_SS;
            }
        }

        var items = new ArrayList<TextItem>(measured.size());

        for (var line : measured) {
            // Center the ink within the block, then shift back by its left bearing so the ink's
            // left edge — not the drawing origin — is what the centering places.
            items.add(new TextItem(
                line.text(),
                line.scaledFont(),
                (widthSs - line.inkWidthSs()) / 2.0 - line.inkXSs(),
                line.baselineOffsetSs()));
        }

        return new AttributionContent(items, widthSs, offsetSs);
    }

    private record MeasuredLine(
        String text,
        Font scaledFont,
        double inkWidthSs,
        double inkXSs,
        double baselineOffsetSs) {}

    private record LineBox(double ascentSs, double heightSs) {}

    /**
     * Fractional outline bounds, not device-pixel-snapped ones, so line spacing does not jump as
     * the zoom changes.
     */
    private static LineBox lineBox(Font font) {
        var boundsPx = TextMeasurement.requireVisualBounds(LINE_BOX_REFERENCE, font);

        return new LineBox(
            DocumentScale.pxToSs(TextMeasurement.inkHeight(boundsPx)),
            DocumentScale.pxToSs(boundsPx.getHeight()));
    }

    private static @Nullable Integer firstSubAttributionIndex(List<AttributionLine> lines) {
        for (var i = 0; i < lines.size(); i++) {
            if (lines.get(i).font() != FontKey.ATTRIBUTION) {
                return i;
            }
        }

        return null;
    }
}
