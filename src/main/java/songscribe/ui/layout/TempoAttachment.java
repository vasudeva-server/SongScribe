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

import org.jspecify.annotations.Nullable;

import songscribe.music.StaffElement;
import songscribe.music.Tempo;
import java.awt.FontMetrics;

import songscribe.smufl.BBox;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;
import songscribe.ui.FlatLafKeys;
import songscribe.ui.FlatLafProps;

/**
 * Represents a tempo marking attachment on a note.
 * <p>
 * Tempo attachments display tempo changes (e.g., "♩ = 120" or "Allegro").
 * They are typically placed above the staff.
 */
public class TempoAttachment extends Attachment {

    /** Scale factor for tempo note glyphs relative to regular notes. */
    public static final float NOTE_SCALE =
        FlatLafProps.get(FlatLafKeys.SCORE_TEMPO_NOTE_SCALE);

    /** Bounding box of the quarter note metronome glyph (the tallest common tempo note). */
    public static final BBox QUARTER_NOTE_BBOX =
        SMuFLMetadata.getInstance().requireBBox(SMuFLGlyph.MET_NOTE_QUARTER_UP);

    /** Gap between tempo note glyph and text, in staff-space units. */
    private static final float GLYPH_TEXT_GAP_SS =
        FlatLafProps.get(FlatLafKeys.SCORE_TEMPO_GLYPH_TEXT_GAP);

    /** Content height from the quarter note glyph bbox, scaled to tempo note size. */
    private static final double DEFAULT_HEIGHT_SS = QUARTER_NOTE_BBOX.height() * NOTE_SCALE;

    /** The tempo data. */
    private Tempo tempo;

    /**
     * Creates a tempo attachment with the specified tempo.
     *
     * @param tempo The tempo data
     */
    public TempoAttachment(Tempo tempo) {
        this.tempo = tempo;
        setAlignment(Alignment.LEFT);
    }

    /**
     * Creates a tempo attachment attached to a note.
     *
     * @param parent The parent note
     * @param tempo  The tempo data
     */
    public TempoAttachment(@Nullable StaffElement parent, Tempo tempo) {
        this.tempo = tempo;
        setOwnerElement(parent);
        setAlignment(Alignment.LEFT);

        if (parent != null) {
            setOwnerElement(parent);
            setParentLine(parent.getParentLine());
        }
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
     * Computes the content width from the actual tempo text and glyph metrics.
     *
     * @param attrFontMetrics font metrics for the attribution font (used for "= NNN" text)
     * @return width in staff-space units
     */
    public double computeContentWidthSs(FontMetrics attrFontMetrics) {
        double widthSs = 0;
        var metadata = SMuFLMetadata.getInstance();
        var scale = ScaleContext.getInstance();

        if (tempo.shouldShowTempo()) {
            double tempoNoteWidthSs = noteWidthSs(tempo.getTempoType().getNote(), metadata);

            if (tempoNoteWidthSs > 0) {
                widthSs += tempoNoteWidthSs;
                widthSs += GLYPH_TEXT_GAP_SS;
            }
        }

        // Build the same text string the renderer draws
        var text = new StringBuilder(25);

        if (tempo.shouldShowTempo()) {
            text.append("= ");
            text.append(tempo.getVisibleTempo());
            text.append(' ');
        }

        text.append(tempo.getTempoDescription());

        if (!text.isEmpty()) {
            widthSs += scale.fromPixels(attrFontMetrics.stringWidth(text.toString()));
        }

        return widthSs;
    }

    /**
     * Returns the SMuFL metronome glyph for the given note's type, or null if unmapped.
     */
    static @Nullable SMuFLGlyph metronomeGlyphFor(StaffElement note) {
        return switch (note.getType()) {
            case SEMIBREVE -> SMuFLGlyph.MET_NOTE_WHOLE;
            case MINIM -> SMuFLGlyph.MET_NOTE_HALF_UP;
            case CROTCHET -> SMuFLGlyph.MET_NOTE_QUARTER_UP;
            case QUAVER -> SMuFLGlyph.MET_NOTE_8TH_UP;
            case SEMIQUAVER -> SMuFLGlyph.MET_NOTE_16TH_UP;
            case DEMI_SEMIQUAVER -> SMuFLGlyph.MET_NOTE_32ND_UP;
            default -> null;
        };
    }

    /**
     * Returns the advance width in staff spaces for the given note glyph plus any augmentation dot.
     * Returns 0 if the note type has no metronome glyph.
     */
    static double noteWidthSs(StaffElement note, SMuFLMetadata metadata) {
        var glyph = metronomeGlyphFor(note);

        if (glyph == null) {
            return 0;
        }

        double widthSs = metadata.requireAdvanceWidth(glyph) * NOTE_SCALE;

        if (note.getDotCount() > 0) {
            widthSs += metadata.requireAdvanceWidth(SMuFLGlyph.MET_AUGMENTATION_DOT) * NOTE_SCALE;
        }

        return widthSs;
    }

    /**
     * Returns the content height in staff-space units.
     */
    public double getContentHeightSs() {
        return DEFAULT_HEIGHT_SS;
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
