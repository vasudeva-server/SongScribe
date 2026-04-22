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

import java.awt.FontMetrics;
import java.util.ArrayList;

import org.jspecify.annotations.Nullable;

import songscribe.music.StaffElement;
import songscribe.music.Tempo;

import songscribe.smufl.SMuFLMetadata;

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

    public Tempo getTempo() {
        return tempo;
    }

    public void setTempo(Tempo tempo) {
        this.tempo = tempo;
    }

    public ContentMetrics computeContentMetrics(FontMetrics attrFontMetrics) {
        var regions = new ArrayList<CollisionRegion>(2);
        double glyphWidth = glyphWidthSs();

        if (glyphWidth > 0) {
            regions.add(new CollisionRegion(0, 0, glyphWidth, QUARTER_NOTE_HEIGHT_SS));
        }

        double textWidth = textWidthSs(tempoText(), attrFontMetrics);

        if (textWidth > 0) {
            double textXOffsetSs = glyphWidth > 0 ? glyphWidth + EQUALS_GAP_SS : 0;
            var scale = ScaleContext.getInstance();
            double textAscentSs = scale.fromPixels(attrFontMetrics.getAscent());
            double textDescentSs = scale.fromPixels(attrFontMetrics.getDescent());
            double textYOffsetSs = QUARTER_NOTE_HEIGHT_SS - textAscentSs;
            double textHeightSs = textAscentSs + textDescentSs;
            regions.add(new CollisionRegion(
                textXOffsetSs, textYOffsetSs, textWidth, textHeightSs));
        }

        double gap = glyphWidth > 0 ? EQUALS_GAP_SS : 0;
        double widthSs = glyphWidth + gap + textWidth;

        return new ContentMetrics(widthSs, regions);
    }

    private double glyphWidthSs() {
        if (!tempo.shouldShowTempo()) {
            return 0;
        }

        return noteWidthSs(tempo.getTempoType().getNote(), SMuFLMetadata.getInstance());
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

    private double textWidthSs(String text, FontMetrics attrFontMetrics) {
        if (text.isEmpty()) {
            return 0;
        }

        return ScaleContext.getInstance().fromPixels(
            attrFontMetrics.stringWidth(text));
    }

}
