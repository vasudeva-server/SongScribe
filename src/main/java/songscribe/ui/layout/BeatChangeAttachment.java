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

import org.jspecify.annotations.Nullable;

import songscribe.music.BeatChange;
import songscribe.music.StaffElement;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;
import songscribe.ui.FlatLafKeys;
import songscribe.ui.FlatLafProps;

/**
 * Represents a beat change (metric modulation) attachment on a note.
 * <p>
 * Beat changes indicate tempo relationships between different note values
 * (e.g., ♩ = ♩. meaning "quarter note equals dotted quarter").
 * They are typically placed above the staff.
 */
public class BeatChangeAttachment extends Attachment {

    /** Scale factor for beat change note glyphs, matching the renderer's zoom factor. */
    private static final float NOTE_SCALE = FlatLafProps.get(FlatLafKeys.SCORE_TEMPO_NOTE_SCALE);

    /** Gap between note glyphs and the "=" sign, in staff-space units. */
    private static final float GLYPH_TEXT_GAP_SS =
        FlatLafProps.get(FlatLafKeys.SCORE_TEMPO_GLYPH_TEXT_GAP);

    /** Content height from the quarter note glyph bbox, scaled to beat change note size. */
    private static final double HEIGHT_SS =
        SMuFLMetadata.getInstance().requireBBox(SMuFLGlyph.MET_NOTE_QUARTER_UP).height()
            * NOTE_SCALE;

    /** The beat change data. */
    private BeatChange beatChange;

    /**
     * Creates a beat change attachment with the specified change.
     *
     * @param beatChange The beat change data
     */
    public BeatChangeAttachment(BeatChange beatChange) {
        this.beatChange = beatChange;
        setAlignment(Alignment.CENTER);
    }

    /**
     * Creates a beat change attachment attached to a note.
     *
     * @param parent     The parent note
     * @param beatChange The beat change data
     */
    public BeatChangeAttachment(@Nullable StaffElement parent, BeatChange beatChange) {
        this.beatChange = beatChange;
        setOwnerElement(parent);
        setAlignment(Alignment.CENTER);

        if (parent != null) {
            setOwnerElement(parent);
            setParentLine(parent.getParentLine());
        }
    }

    /**
     * Returns the beat change data.
     */
    public BeatChange getBeatChange() {
        return beatChange;
    }

    /**
     * Sets the beat change data.
     */
    public void setBeatChange(BeatChange beatChange) {
        this.beatChange = beatChange;
    }

    /**
     * Returns the tempo change multiplier.
     */
    public float getTempoChangeMultiplier() {
        return beatChange.getTempoChange();
    }

    /**
     * Computes the content width from the actual beat change glyphs and "=" sign.
     *
     * @param attrFontMetrics font metrics for the attribution font (used for the "=" sign)
     * @return width in staff-space units
     */
    public double computeContentWidthSs(FontMetrics attrFontMetrics) {
        var metadata = SMuFLMetadata.getInstance();
        var scale = ScaleContext.getInstance();

        double widthSs = TempoAttachment.noteWidthSs(beatChange.getFirstElement(), metadata);
        widthSs += GLYPH_TEXT_GAP_SS;
        widthSs += scale.fromPixels(attrFontMetrics.stringWidth("="));
        widthSs += GLYPH_TEXT_GAP_SS;
        widthSs += TempoAttachment.noteWidthSs(beatChange.getSecondElement(), metadata);

        return widthSs;
    }

    /**
     * Returns the content height in staff-space units.
     */
    public double getContentHeightSs() {
        return HEIGHT_SS;
    }

    @Override
    public double getContentWidthPx() {
        // Legacy pixel API — not used for layout (computeContentWidthSs is used instead)
        return 0;
    }

    @Override
    public double getContentHeightPx() {
        return ScaleContext.getInstance().toPixels(getContentHeightSs());
    }
}
