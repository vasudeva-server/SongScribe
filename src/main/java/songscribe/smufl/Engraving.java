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

package songscribe.smufl;

/**
 * Single entry point for all SMuFL-sourced constants, all values in staff spaces.
 * Initialized once from {@link SMuFLMetadata} at class load time.
 */
public final class Engraving {

    // Engraving defaults
    public static final double BEAM_THICKNESS_SS;
    public static final double BEAM_SPACING_SS;
    public static final double REPEAT_BARLINE_DOT_SEPARATION_SS;
    public static final double LEDGER_LINE_THICKNESS_SS;
    public static final double LEDGER_LINE_EXTENSION_SS;
    public static final double TIE_MIDPOINT_THICKNESS_SS;

    // Glyph advance widths
    public static final double NOTE_HEAD_WIDTH_SS;
    public static final double G_CLEF_WIDTH_SS;
    public static final double REPEAT_DOTS_ADVANCE_WIDTH_SS;

    // Glyph anchors
    public static final GlyphAnchors.Anchor NOTEHEAD_BLACK_STEM_UP_SE;
    public static final GlyphAnchors.Anchor NOTEHEAD_BLACK_STEM_DOWN_NW;
    public static final GlyphAnchors.Anchor NOTEHEAD_HALF_STEM_UP_SE;
    public static final GlyphAnchors.Anchor NOTEHEAD_HALF_STEM_DOWN_NW;

    static {
        var defaults = SMuFLMetadata.getEngravingDefaults();

        BEAM_THICKNESS_SS = defaults.beamThickness();
        BEAM_SPACING_SS = defaults.beamSpacing();
        REPEAT_BARLINE_DOT_SEPARATION_SS = defaults.repeatBarlineDotSeparation();
        LEDGER_LINE_THICKNESS_SS = defaults.legerLineThickness();
        LEDGER_LINE_EXTENSION_SS = defaults.legerLineExtension();
        TIE_MIDPOINT_THICKNESS_SS = defaults.tieMidpointThickness();

        NOTE_HEAD_WIDTH_SS = SMuFLMetadata.noteHeadWidthSs();
        G_CLEF_WIDTH_SS = SMuFLMetadata.requireAdvanceWidth(SMuFLGlyph.G_CLEF);
        REPEAT_DOTS_ADVANCE_WIDTH_SS = SMuFLMetadata.requireAdvanceWidth(SMuFLGlyph.REPEAT_DOTS);

        var blackAnchors = SMuFLMetadata.requireAnchors(SMuFLGlyph.NOTEHEAD_BLACK);
        var halfAnchors = SMuFLMetadata.requireAnchors(SMuFLGlyph.NOTEHEAD_HALF);
        NOTEHEAD_BLACK_STEM_UP_SE = blackAnchors.requireStemUpSE();
        NOTEHEAD_BLACK_STEM_DOWN_NW = blackAnchors.requireStemDownNW();
        NOTEHEAD_HALF_STEM_UP_SE = halfAnchors.requireStemUpSE();
        NOTEHEAD_HALF_STEM_DOWN_NW = halfAnchors.requireStemDownNW();
    }

    private Engraving() {}
}
