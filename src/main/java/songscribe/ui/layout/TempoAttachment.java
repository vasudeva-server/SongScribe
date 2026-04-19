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
import java.util.List;

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
public class TempoAttachment extends MetronomeAttachment {

    /** The tempo data. */
    private Tempo tempo;

    /**
     * Creates a tempo attachment with the specified tempo.
     *
     * @param tempo The tempo data
     */
    public TempoAttachment(Tempo tempo) {
        super(Alignment.LEFT);
        this.tempo = tempo;
    }

    /**
     * Creates a tempo attachment attached to a note.
     *
     * @param parent The parent note
     * @param tempo  The tempo data
     */
    public TempoAttachment(@Nullable StaffElement parent, Tempo tempo) {
        super(parent, Alignment.LEFT);
        this.tempo = tempo;
    }

    /**
     * Returns the tempo data.
     */
    public Tempo getTempo() {
        return tempo;
    }

    /**
     * Sets the tempo data.
     */
    public void setTempo(Tempo tempo) {
        this.tempo = tempo;
    }

    /**
     * Returns the visible tempo value (BPM).
     */
    public int getVisibleTempo() {
        return tempo.getVisibleTempo();
    }

    /**
     * Returns the tempo description text.
     */
    public @Nullable String getTempoDescription() {
        return tempo.getTempoDescription();
    }

    /**
     * Returns whether the tempo should be shown.
     */
    public boolean shouldShowTempo() {
        return tempo.shouldShowTempo();
    }

    /**
     * Content width and collision sub-regions computed together to avoid
     * redundant {@link #tempoText()} calls.
     */
    public record ContentMetrics(double widthSs, List<CollisionRegion> regions) {}

    /**
     * Computes the content width and collision sub-regions for this tempo marking.
     * <p>
     * Combines width and region computation into a single pass so that the
     * tempo text string is only built once.
     *
     * @param attrFontMetrics font metrics for the attribution font
     * @return content metrics (width and collision regions)
     */
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

    /**
     * Returns the width of the metronome note glyph in staff-space units,
     * or 0 if the tempo has no visible note.
     */
    private double glyphWidthSs() {
        if (!tempo.shouldShowTempo()) {
            return 0;
        }

        return noteWidthSs(tempo.getTempoType().getNote(), SMuFLMetadata.getInstance());
    }

    /**
     * Builds the tempo text string as drawn by the renderer (e.g. "= 120 Allegro").
     */
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

    /**
     * Returns the width of the tempo text portion in staff-space units,
     * or 0 if there is no text.
     */
    private double textWidthSs(String text, FontMetrics attrFontMetrics) {
        if (text.isEmpty()) {
            return 0;
        }

        return ScaleContext.getInstance().fromPixels(
            attrFontMetrics.stringWidth(text));
    }

}
