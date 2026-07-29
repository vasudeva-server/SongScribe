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

package songscribe.engraving;

import songscribe.smufl.GlyphAnchors;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;

/**
 * Single entry point for all SMuFL-sourced constants, all values in staff spaces.
 * Initialized once from {@link SMuFLMetadata} at class load time.
 */
public final class SMuFLConstants {

    // SMuFLConstants defaults
    // Beam thickness and spacing deliberately do NOT come from the font's
    // engravingDefaults — see LineThickness.BEAM_THICKNESS_SS / BEAM_TRANSLATION_SS,
    // which follow LilyPond instead.
    public static final double REPEAT_BARLINE_DOT_SEPARATION_SS;
    public static final double LEDGER_LINE_THICKNESS_SS;
    // LilyPond length-fraction default: a dimensionless multiplier on the notehead width that
    // sets how far each ledger line extends beyond the notehead bbox on each side.
    public static final double LEDGER_LINE_LENGTH_FRACTION = 0.25;
    public static final double TIE_MIDPOINT_THICKNESS_SS;

    // Glyph advance widths
    /**
     * The width of {@code noteheadBlack} specifically — <em>not</em> "the" notehead width. Use it
     * only for a quantity that really is tied to the black notehead, or as a generic spacing floor.
     *
     * <p>To ask how wide a particular element's head is, call
     * {@link songscribe.dom.ElementType#getElementWidthSs()} on its type instead: {@code
     * noteheadWhole} is wider than {@code noteheadBlack}, so a whole note measured with this
     * constant comes out about half a staff space short (refs #694).
     */
    public static final double NOTE_HEAD_WIDTH_SS;
    public static final double G_CLEF_WIDTH_SS;
    public static final double REPEAT_DOTS_ADVANCE_WIDTH_SS;
    public static final double AUGMENTATION_DOT_WIDTH_SS;

    // Glyph anchors
    public static final GlyphAnchors.Anchor NOTEHEAD_BLACK_STEM_UP_SE;
    public static final GlyphAnchors.Anchor NOTEHEAD_BLACK_STEM_DOWN_NW;
    public static final GlyphAnchors.Anchor NOTEHEAD_HALF_STEM_UP_SE;
    public static final GlyphAnchors.Anchor NOTEHEAD_HALF_STEM_DOWN_NW;

    // Stem lengths
    /** SMuFL standard stem length in staff-space units. */
    public static final double STEM_LENGTH_SS = 3.5;

    /** Stem length for grace notes in staff-space units. */
    public static final double GRACE_NOTE_STEM_LENGTH_SS = 2.5;

    static {
        var defaults = SMuFLMetadata.getEngravingDefaults();

        REPEAT_BARLINE_DOT_SEPARATION_SS = defaults.repeatBarlineDotSeparation();
        LEDGER_LINE_THICKNESS_SS = defaults.legerLineThickness();
        TIE_MIDPOINT_THICKNESS_SS = defaults.tieMidpointThickness();

        NOTE_HEAD_WIDTH_SS = SMuFLMetadata.noteHeadWidthSs();
        G_CLEF_WIDTH_SS = SMuFLMetadata.requireAdvanceWidth(SMuFLGlyph.G_CLEF);
        REPEAT_DOTS_ADVANCE_WIDTH_SS = SMuFLMetadata.requireAdvanceWidth(SMuFLGlyph.REPEAT_DOTS);
        AUGMENTATION_DOT_WIDTH_SS = SMuFLMetadata.requireAdvanceWidth(SMuFLGlyph.AUGMENTATION_DOT);

        var blackAnchors = SMuFLMetadata.requireAnchors(SMuFLGlyph.NOTEHEAD_BLACK);
        var halfAnchors = SMuFLMetadata.requireAnchors(SMuFLGlyph.NOTEHEAD_HALF);
        NOTEHEAD_BLACK_STEM_UP_SE = blackAnchors.requireStemUpSE();
        NOTEHEAD_BLACK_STEM_DOWN_NW = blackAnchors.requireStemDownNW();
        NOTEHEAD_HALF_STEM_UP_SE = halfAnchors.requireStemUpSE();
        NOTEHEAD_HALF_STEM_DOWN_NW = halfAnchors.requireStemDownNW();
    }

    private SMuFLConstants() {}
}
