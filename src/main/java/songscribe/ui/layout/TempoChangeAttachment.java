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

package songscribe.ui.layout;

import java.awt.Font;
import java.util.ArrayList;

import org.jspecify.annotations.Nullable;

import songscribe.music.StaffElement;
import songscribe.music.Tempo;
import songscribe.util.GraphicUtils;

/**
 * Represents a tempo marking attachment on a note.
 * <p>
 * Tempo attachments display tempo changes (e.g., "♩ = 120" or "Allegro").
 * They are typically placed above the staff.
 */
public class TempoChangeAttachment extends MetronomeAttachment {

    private Tempo tempo;

    public TempoChangeAttachment(Tempo tempo) {
        super(Alignment.LEFT);
        this.tempo = tempo;
    }

    public TempoChangeAttachment(@Nullable StaffElement parent, Tempo tempo) {
        super(parent, Alignment.LEFT);
        this.tempo = tempo;
    }

    @Override
    public Attachment copy(StaffElement newOwner) {
        return new TempoChangeAttachment(newOwner, tempo);
    }

    public Tempo getTempo() {
        return tempo;
    }

    public void setTempo(Tempo tempo) {
        this.tempo = tempo;
    }

    @Override
    public ContentMetrics computeContentMetrics(Font attrFont) {
        var regions = new ArrayList<CollisionRegion>(2);
        var glyphWidth = glyphWidthSs();

        if (glyphWidth > 0) {
            regions.add(new CollisionRegion(0, 0, glyphWidth, QUARTER_NOTE_HEIGHT_SS));
        }

        var textWidth = textWidthSs(tempoText(), attrFont);

        if (textWidth > 0) {
            var textXOffsetSs = glyphWidth > 0 ? glyphWidth : 0;
            var textLm = attrFont.getLineMetrics("", GraphicUtils.SCREEN_FRC);
            var textAscentSs = ScaleContext.pxToSs(textLm.getAscent());
            var textDescentSs = ScaleContext.pxToSs(textLm.getDescent());
            var textYOffsetSs = QUARTER_NOTE_HEIGHT_SS - textAscentSs;
            var textHeightSs = textAscentSs + textDescentSs;
            regions.add(new CollisionRegion(
                textXOffsetSs, textYOffsetSs, textWidth, textHeightSs));
        }

        var widthSs = glyphWidth + textWidth;

        return new ContentMetrics(widthSs, regions);
    }

    private double glyphWidthSs() {
        if (!tempo.shouldShowTempo()) {
            return 0;
        }

        return noteWidthSs(tempo.getTempoType().getNote());
    }

    private String tempoText() {
        var text = new StringBuilder(25);

        if (tempo.shouldShowTempo()) {
            text.append("= ");
            text.append(tempo.getVisibleTempo());
            text.append(' ');
        }

        text.append(tempo.getTempoDescription());
        return text.toString();
    }

    private double textWidthSs(String text, Font attrFont) {
        if (text.isEmpty()) {
            return 0;
        }

        return ScaleContext.textWidthSs(attrFont, text);
    }

}
