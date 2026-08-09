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

package songscribe.dom;

import java.awt.Font;
import java.util.ArrayList;

import songscribe.util.GraphicUtils;

/**
 * The drawn content of a tempo marking — its text, its glyph width and its collision geometry —
 * stated once for the two things that depict a {@link Tempo}: the per-note
 * {@link TempoChangeAttachment} and the song-level {@link SongTempoMark} at the first line's
 * staff header.
 * <p>
 * {@link Tempo#shouldShowTempo()} is not a visibility flag. When it is false the metronome glyph
 * and the {@code "= N "} prefix are omitted and only the description is drawn; the mark vanishes
 * entirely only when the description is empty too, which shows up here as a
 * {@link MetronomeAttachment.ContentMetrics} of zero width.
 */
public final class TempoContent {

    private TempoContent() {
    }

    /**
     * Returns the text drawn to the right of the metronome glyph.
     */
    public static String text(Tempo tempo) {
        var text = new StringBuilder(25);

        if (tempo.shouldShowTempo()) {
            text.append("= ");
            text.append(tempo.getVisibleTempo());
            text.append(' ');
        }

        text.append(tempo.getTempoDescription());
        return text.toString();
    }

    /**
     * Returns the width in staff spaces of the metronome note glyph, or 0 when the tempo
     * renders as a description alone.
     */
    public static double glyphWidthSs(Tempo tempo) {
        if (!tempo.shouldShowTempo()) {
            return 0;
        }

        return MetronomeAttachment.noteWidthSs(tempo.getTempoType().getNote());
    }

    /**
     * Computes the overall content width and the per-sub-region collision geometry of the
     * tempo marking.
     *
     * @param tempo    the tempo being depicted
     * @param attrFont the attribution font, unscaled, in pixel units
     */
    public static MetronomeAttachment.ContentMetrics metrics(Tempo tempo, Font attrFont) {
        var regions = new ArrayList<CollisionRegion>(2);
        var glyphWidth = glyphWidthSs(tempo);

        if (glyphWidth > 0) {
            regions.add(new CollisionRegion(
                0, 0, glyphWidth, MetronomeAttachment.QUARTER_NOTE_HEIGHT_SS));
        }

        var textWidth = textWidthSs(text(tempo), attrFont);

        if (textWidth > 0) {
            // The glyph width is 0 when the tempo renders as a description alone, which is
            // exactly where the text starts in that case.
            var textXOffsetSs = glyphWidth;
            var textLm = attrFont.getLineMetrics("", GraphicUtils.SCREEN_FRC);
            var textAscentSs = ScaleContext.pxToSs(textLm.getAscent());
            var textDescentSs = ScaleContext.pxToSs(textLm.getDescent());
            var textYOffsetSs = MetronomeAttachment.QUARTER_NOTE_HEIGHT_SS - textAscentSs;
            var textHeightSs = textAscentSs + textDescentSs;
            regions.add(new CollisionRegion(
                textXOffsetSs, textYOffsetSs, textWidth, textHeightSs));
        }

        var widthSs = glyphWidth + textWidth;

        return new MetronomeAttachment.ContentMetrics(widthSs, regions);
    }

    private static double textWidthSs(String text, Font attrFont) {
        if (text.isEmpty()) {
            return 0;
        }

        return ScaleContext.textWidthSs(attrFont, text).value();
    }
}
