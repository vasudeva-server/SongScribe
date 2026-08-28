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
 * SMuFL glyphs used by SongScribe, with their canonical SMuFL name and Unicode codepoint.
 * Codepoints are from the SMuFL 1.4 specification.
 */
public enum SMuFLGlyph {

    // Repeats (U+E040-U+E04F)
    REPEAT_DOTS("repeatDots", '\uE043'),

    // Clefs (U+E050-U+E07F)
    G_CLEF("gClef", '\uE050'),

    // Noteheads (U+E0A0-U+E0FF)
    NOTEHEAD_WHOLE("noteheadWhole", '\uE0A2'),
    NOTEHEAD_HALF("noteheadHalf", '\uE0A3'),
    NOTEHEAD_BLACK("noteheadBlack", '\uE0A4'),

    // Individual notes / augmentation (U+E1D0-U+E1EF)
    NOTE_WHOLE("noteWhole", '\uE1D2'),
    NOTE_HALF_UP("noteHalfUp", '\uE1D3'),
    NOTE_QUARTER_UP("noteQuarterUp", '\uE1D5'),
    NOTE_8TH_UP("note8thUp", '\uE1D7'),
    AUGMENTATION_DOT("augmentationDot", '\uE1E7'),

    // Flags (U+E240-U+E24F)
    FLAG_8TH_UP("flag8thUp", '\uE240'),
    FLAG_8TH_DOWN("flag8thDown", '\uE241'),
    FLAG_16TH_UP("flag16thUp", '\uE242'),
    FLAG_16TH_DOWN("flag16thDown", '\uE243'),
    FLAG_32ND_UP("flag32ndUp", '\uE244'),
    FLAG_32ND_DOWN("flag32ndDown", '\uE245'),

    // Accidentals (U+E260-U+E26F)
    ACCIDENTAL_FLAT("accidentalFlat", '\uE260'),
    ACCIDENTAL_NATURAL("accidentalNatural", '\uE261'),
    ACCIDENTAL_SHARP("accidentalSharp", '\uE262'),
    ACCIDENTAL_DOUBLE_SHARP("accidentalDoubleSharp", '\uE263'),
    ACCIDENTAL_DOUBLE_FLAT("accidentalDoubleFlat", '\uE264'),
    ACCIDENTAL_PARENS_LEFT("accidentalParensLeft", '\uE26A'),
    ACCIDENTAL_PARENS_RIGHT("accidentalParensRight", '\uE26B'),

    // Chord-symbol accidentals (U+ED60-U+ED7F)
    CSYM_ACCIDENTAL_FLAT("csymAccidentalFlat", '\uED60'),
    CSYM_ACCIDENTAL_SHARP("csymAccidentalSharp", '\uED62'),

    // Articulations (U+E4A0-U+E4AF)
    ARTIC_STACCATO_ABOVE("articStaccatoAbove", '\uE4A2'),
    ARTIC_STACCATO_BELOW("articStaccatoBelow", '\uE4A3'),

    // Holds and pauses (U+E4C0-U+E4CF)
    FERMATA_ABOVE("fermataAbove", '\uE4C0'),
    BREATH_MARK_COMMA("breathMarkComma", '\uE4CE'),

    // Rests (U+E4E0-U+E4EF)
    REST_WHOLE("restWhole", '\uE4E3'),
    REST_HALF("restHalf", '\uE4E4'),
    REST_QUARTER("restQuarter", '\uE4E5'),
    REST_8TH("rest8th", '\uE4E6'),
    REST_16TH("rest16th", '\uE4E7'),
    REST_32ND("rest32nd", '\uE4E8'),

    // Dynamics (U+E520-U+E52F)
    DYNAMIC_PIANO("dynamicPiano", '\uE520'),
    DYNAMIC_FORTE("dynamicForte", '\uE522'),
    DYNAMIC_PP("dynamicPP", '\uE52B'),
    DYNAMIC_MP("dynamicMP", '\uE52C'),
    DYNAMIC_MF("dynamicMF", '\uE52D'),
    DYNAMIC_FF("dynamicFF", '\uE52F'),

    // Common ornaments (U+E560-U+E56F)
    ORNAMENT_TRILL("ornamentTrill", '\uE566'),

    // Brass techniques (U+E5D0-U+E5EF)
    BRASS_FALL_LIP_SHORT("brassFallLipShort", '\uE5D7'),

    // Trills and tremolos (U+EAA0-U+EAAF)
    WIGGLE_TRILL_FASTER("wiggleTrillFaster", '\uEAA2'),

    // Metronome marks (U+ECA0-U+ECBF)
    MET_NOTE_WHOLE("metNoteWhole", '\uECA2'),
    MET_NOTE_HALF_UP("metNoteHalfUp", '\uECA3'),
    MET_NOTE_QUARTER_UP("metNoteQuarterUp", '\uECA5'),
    MET_NOTE_8TH_UP("metNote8thUp", '\uECA7'),
    MET_NOTE_16TH_UP("metNote16thUp", '\uECA9'),
    MET_NOTE_32ND_UP("metNote32ndUp", '\uECAB'),
    MET_AUGMENTATION_DOT("metAugmentationDot", '\uECB7');

    private final String smuflName;
    private final char codepoint;

    SMuFLGlyph(String smuflName, char codepoint) {
        this.smuflName = smuflName;
        this.codepoint = codepoint;
    }

    /** @return The canonical SMuFL name (camelCase), which is the key its metadata is under. */
    public String smuflName() {
        return smuflName;
    }

    /** @return The codepoint, in the Private Use Area every SMuFL glyph is assigned within. */
    public char codepoint() {
        return codepoint;
    }

    /**
     * The glyph as text to draw. Every glyph is one BMP codepoint, so this is always a
     * single character and needs no surrogate handling.
     *
     * @return a one-character string, which renders as this glyph in a SMuFL font and as
     *     nothing recognizable in any other
     */
    public String asString() {
        return String.valueOf(codepoint);
    }
}
