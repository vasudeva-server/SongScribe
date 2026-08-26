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

import org.jspecify.annotations.Nullable;

import songscribe.smufl.BBox;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;
/**
 * Abstract base class for metronome-style markings (tempo and beat change).
 * <p>
 * Provides the metronome note scale and the content-height overrides used by
 * TempoChangeAttachment and BeatChangeAttachment. The typesetting of a marking —
 * the glyph lookup, the advance sequence and the "=" separator — belongs to
 * {@code songscribe.layout.MetronomeContent}, which needs a resolved font that a
 * document-model object has no access to.
 */
public abstract sealed class MetronomeAttachment extends Attachment
    permits TempoChangeAttachment, BeatChangeAttachment {

    // Design constant; mirrors FlatLaf.properties: SongScribe.score.tempo.note.scale
    public static final float NOTE_SCALE = 0.8f;

    /** Bounding box of the quarter note metronome glyph (the tallest common tempo note). */
    private static final BBox QUARTER_NOTE_BBOX =
        SMuFLMetadata.requireBBox(SMuFLGlyph.MET_NOTE_QUARTER_UP);

    /** Content height derived from the quarter note glyph, scaled to metronome note size. */
    public static final double QUARTER_NOTE_HEIGHT_SS = QUARTER_NOTE_BBOX.height() * NOTE_SCALE;

    protected MetronomeAttachment(Alignment alignment) {
        setAlignment(alignment);
    }

    protected MetronomeAttachment(@Nullable StaffElement parent, Alignment alignment) {
        setOwnerElement(parent);
        setAlignment(alignment);
        // The line pointer is not set here. StaffElement.addAttachment — which every
        // caller reaches immediately — routes through LineElement.addChild, and that
        // owns it.
    }

    /**
     * Returns the content height in staff-space units.
     */
    @Override
    public double getContentHeightSs() {
        return QUARTER_NOTE_HEIGHT_SS;
    }

    /**
     * Always returns 0. A metronome marking's width depends on the resolved annotation font,
     * which a DOM object has no access to, so the real width lives in
     * {@code MetronomeContent.widthSs()} and reaches the layout through
     * {@code LayoutResult.DecorationLayout}.
     */
    @Override
    public double getContentWidthPx() {
        return 0;
    }

    @Override
    public double getContentHeightPx() {
        return DocumentScale.ssToPx(QUARTER_NOTE_HEIGHT_SS);
    }
}
