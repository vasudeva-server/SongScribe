/*
 * SongScribe song notation program
 * Copyright (C) Sri Chinmoy Centres International
 *
 * This file is part of SongScribe.
 *
 * SongScribe is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * SongScribe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package songscribe.io.musicxml;

/**
 * The MusicXML vocabulary shared by {@link MusicXmlReader} and
 * {@link MusicXmlWriter}: element names, attribute names, and the attribute
 * values both sides depend on. Centralised so a change on one side cannot
 * silently diverge from the other.
 *
 * <p>Bar-style, repeat-direction, and barline-location <em>values</em> live in
 * {@link BarlineStyleMapping}, which owns the barline mapping.
 */
final class MusicXmlTags {

    private MusicXmlTags() {}

    /** Supported and emitted MusicXML schema version. */
    static final String VERSION_VALUE = "4.0";

    // MusicXML's positional coordinate unit is the tenth: 1 staff space = 10
    // tenths. Shared by both sides of the codec so the writer's px→tenths and the
    // reader's tenths→px conversions cannot silently disagree.
    static final int TENTHS_PER_STAFF_SPACE = 10;

    // Element names — document structure.
    static final String SCORE_PARTWISE = "score-partwise";
    static final String PART_LIST      = "part-list";
    static final String SCORE_PART     = "score-part";
    static final String PART           = "part";
    static final String MEASURE        = "measure";
    static final String ATTRIBUTES     = "attributes";
    static final String KEY            = "key";
    static final String FIFTHS         = "fifths";
    static final String PRINT          = "print";
    static final String BARLINE        = "barline";
    static final String BAR_STYLE      = "bar-style";
    static final String REPEAT         = "repeat";

    // Element names — note subtree.
    static final String NOTE          = "note";
    static final String PITCH         = "pitch";
    static final String STEP          = "step";
    static final String ALTER         = "alter";
    static final String OCTAVE        = "octave";
    static final String REST          = "rest";
    static final String GRACE         = "grace";
    static final String DURATION      = "duration";
    static final String NOTE_TYPE     = "type";
    static final String DOT           = "dot";
    static final String ACCIDENTAL    = "accidental";
    static final String STEM          = "stem";
    static final String NOTATIONS     = "notations";
    static final String ARTICULATIONS = "articulations";
    static final String ACCENT        = "accent";
    static final String STACCATO      = "staccato";
    static final String FERMATA       = "fermata";
    static final String DYNAMICS      = "dynamics";
    static final String BREATH_MARK   = "breath-mark";
    static final String SLIDE         = "slide";
    static final String FALLOFF       = "falloff";

    // Element names — span subtree (beam, tie, tuplet, hairpin, trill, ending).
    static final String BEAM           = "beam";
    static final String TIE            = "tie";
    static final String TIED           = "tied";
    static final String TIME_MOD       = "time-modification";
    static final String ACTUAL_NOTES   = "actual-notes";
    static final String NORMAL_NOTES   = "normal-notes";
    static final String TUPLET         = "tuplet";
    static final String DIRECTION      = "direction";
    static final String DIRECTION_TYPE = "direction-type";
    static final String WEDGE          = "wedge";
    static final String ORNAMENTS      = "ornaments";
    static final String TRILL_MARK     = "trill-mark";
    static final String WAVY_LINE      = "wavy-line";
    static final String ENDING         = "ending";

    // Attribute names — document structure.
    static final String ATTR_VERSION    = "version";
    static final String ATTR_NEW_SYSTEM = "new-system";
    static final String ATTR_LOCATION   = "location";
    static final String ATTR_DIRECTION  = "direction";
    static final String ATTR_NUMBER     = "number";
    static final String ATTR_ID         = "id";

    // Attribute names — note subtree.
    static final String ATTR_SLASH       = "slash";
    static final String ATTR_CAUTIONARY  = "cautionary";
    static final String ATTR_PARENTHESES = "parentheses";
    static final String ATTR_DEFAULT_X   = "default-x";
    static final String ATTR_DEFAULT_Y   = "default-y";
    static final String ATTR_RELATIVE_X  = "relative-x";
    static final String ATTR_RELATIVE_Y  = "relative-y";
    static final String ATTR_TYPE        = "type";
    static final String ATTR_LINE_SHAPE  = "line-shape";
    static final String ATTR_LINE_TYPE   = "line-type";
    static final String ATTR_LINE_LENGTH = "line-length";

    // Attribute values — document structure.
    static final String YES     = "yes";
    static final String PART_ID = "P1";

    // Attribute values — note subtree.
    static final String NO            = "no";
    static final String STEM_UP       = "up";
    static final String STEM_DOWN     = "down";
    static final String SLIDE_START   = "start";
    static final String SLIDE_STOP    = "stop";
    static final String LINE_STRAIGHT = "straight";
    static final String LINE_SOLID    = "solid";

    // Attribute values — beam type (text content of <beam>).
    static final String BEAM_BEGIN        = "begin";
    static final String BEAM_CONTINUE     = "continue";
    static final String BEAM_END          = "end";
    static final String BEAM_FORWARD_HOOK = "forward hook";
    static final String BEAM_BACKWARD_HOOK = "backward hook";

    // Attribute values — generic span type (used by tie, tied, wavy-line, ending @type).
    static final String TYPE_START = "start";
    static final String TYPE_STOP  = "stop";

    // Attribute values — wedge type (@type on <wedge>).
    static final String WEDGE_CRESCENDO  = "crescendo";
    static final String WEDGE_DIMINUENDO = "diminuendo";

    // Attribute values — ending type (@type on <ending>).
    static final String ENDING_DISCONTINUE = "discontinue";

    // Attribute values — span numbering.
    // The wedge @number is always "1" (only one hairpin is ever open at a time);
    // ending @number is "1" for the first volta bracket and "2" for the second.
    static final String NUMBER_1 = "1";
    static final String NUMBER_2 = "2";
}
