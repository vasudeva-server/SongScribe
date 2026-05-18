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
import java.util.List;

import org.jspecify.annotations.Nullable;

import songscribe.music.ElementType;
import songscribe.music.StaffElement;
import songscribe.smufl.BBox;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;
import songscribe.ui.FlatLafKeys;
import songscribe.ui.FlatLafProps;

/**
 * Abstract base class for metronome-style markings (tempo and beat change).
 * <p>
 * Provides SMuFL metronome glyph constants, the note-width calculation, and
 * the standard content-size implementation shared by {@link TempoChangeAttachment}
 * and {@link BeatChangeAttachment}.
 */
public abstract class MetronomeAttachment extends Attachment {

    /** Scale factor for metronome note glyphs relative to regular notes. */
    public static final float NOTE_SCALE = FlatLafProps.get(FlatLafKeys.SCORE_TEMPO_NOTE_SCALE);

    /** Bounding box of the quarter note metronome glyph (the tallest common tempo note). */
    private static final BBox QUARTER_NOTE_BBOX =
        SMuFLMetadata.requireBBox(SMuFLGlyph.MET_NOTE_QUARTER_UP);

    /** " = " in tempo markings and beat changes. */
    public static final String EQUALS_STR = " = ";

    /** Content height derived from the quarter note glyph, scaled to metronome note size. */
    public static final double QUARTER_NOTE_HEIGHT_SS = QUARTER_NOTE_BBOX.height() * NOTE_SCALE;

    /** Width and collision sub-regions computed together to avoid redundant computation. */
    public record ContentMetrics(double widthSs, List<CollisionRegion> regions) {}

    protected MetronomeAttachment(Alignment alignment) {
        setAlignment(alignment);
    }

    protected MetronomeAttachment(@Nullable StaffElement parent, Alignment alignment) {
        setOwnerElement(parent);
        setAlignment(alignment);

        if (parent != null) {
            setParentLine(parent.getParentLine());
        }
    }

    /**
     * Computes the content width and per-sub-region collision geometry for this marking.
     */
    public abstract ContentMetrics computeContentMetrics(Font attrFont);

    /**
     * Returns the SMuFL metronome glyph for the given element type, or null if unmapped.
     */
    public static @Nullable SMuFLGlyph metronomeGlyphFor(ElementType type) {
        return switch (type) {
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
     * Returns the advance width in staff spaces of one augmentation dot step (gap or dot itself).
     * The layout reserves two of these per dotted note: one gap before the dot, one for the dot.
     */
    public static double dotAdvanceWidthSs() {
        return SMuFLMetadata.requireAdvanceWidth(SMuFLGlyph.MET_AUGMENTATION_DOT) * NOTE_SCALE;
    }

    /**
     * Returns the advance width in staff spaces for the given note glyph plus any augmentation dot.
     * Returns 0 if the note type has no metronome glyph.
     */
    public static double noteWidthSs(StaffElement note) {
        var glyph = metronomeGlyphFor(note.getType());

        if (glyph == null) {
            return 0;
        }

        var widthSs = SMuFLMetadata.requireAdvanceWidth(glyph) * NOTE_SCALE;

        if (note.getDotCount() > 0) {
            widthSs += 2 * dotAdvanceWidthSs();
        }

        return widthSs;
    }

    /**
     * Returns the content height in staff-space units.
     */
    @Override
    public double getContentHeightSs() {
        return QUARTER_NOTE_HEIGHT_SS;
    }

    @Override
    public double getContentWidthPx() {
        return 0;
    }

    @Override
    public double getContentHeightPx() {
        return ScaleContext.getInstance().ssToPx(QUARTER_NOTE_HEIGHT_SS);
    }
}
