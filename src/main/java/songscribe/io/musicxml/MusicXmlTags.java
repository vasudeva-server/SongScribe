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

    /**
     * Supported and emitted MusicXML schema version. Also the minimum the
     * reader accepts: documents at this version or later are read
     * (forward-compatible); older ones are rejected.
     */
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

    // Element names — lyric subtree. ATTR_NUMBER (verse number) and ATTR_TYPE
    // (extend type) are shared, general-purpose attribute constants declared
    // below; they are not redeclared here.
    static final String LYRIC       = "lyric";
    static final String SYLLABIC    = "syllabic";
    static final String LYRIC_TEXT  = "text";
    static final String EXTEND      = "extend";

    // Element names — span subtree (beam, tie, tuplet, hairpin, trill, ending).
    static final String BEAM           = "beam";
    static final String TIE            = "tie";
    static final String TIED           = "tied";
    static final String TIME_MOD       = "time-modification";
    static final String ACTUAL_NOTES   = "actual-notes";
    static final String NORMAL_NOTES   = "normal-notes";
    static final String NORMAL_TYPE    = "normal-type";
    static final String NORMAL_DOT     = "normal-dot";
    static final String TUPLET         = "tuplet";
    static final String DIRECTION      = "direction";
    static final String DIRECTION_TYPE = "direction-type";
    static final String WEDGE          = "wedge";
    static final String ORNAMENTS      = "ornaments";
    static final String TRILL_MARK     = "trill-mark";
    static final String WAVY_LINE      = "wavy-line";
    static final String ENDING         = "ending";

    // Element names — tempo / metronome direction subtree.
    static final String METRONOME          = "metronome";
    static final String BEAT_UNIT          = "beat-unit";
    static final String BEAT_UNIT_DOT      = "beat-unit-dot";
    static final String PER_MINUTE         = "per-minute";
    static final String WORDS              = "words";
    static final String SOUND              = "sound";
    static final String METRONOME_NOTE     = "metronome-note";
    static final String METRONOME_TYPE     = "metronome-type";
    static final String METRONOME_DOT      = "metronome-dot";
    static final String METRONOME_RELATION = "metronome-relation";

    // Element names — head / identification subtree.
    static final String MOVEMENT_TITLE  = "movement-title";
    static final String MOVEMENT_NUMBER = "movement-number";
    static final String IDENTIFICATION  = "identification";
    static final String CREATOR         = "creator";
    static final String RIGHTS          = "rights";
    static final String ENCODING        = "encoding";
    static final String SOFTWARE        = "software";
    static final String ENCODING_DATE   = "encoding-date";
    static final String SUPPORTS        = "supports";

    // Element names — defaults subtree.
    static final String DEFAULTS       = "defaults";
    static final String SCALING        = "scaling";
    static final String MILLIMETERS    = "millimeters";
    static final String TENTHS         = "tenths";
    static final String PAGE_LAYOUT    = "page-layout";
    static final String PAGE_HEIGHT    = "page-height";
    static final String PAGE_WIDTH     = "page-width";
    static final String STAFF_LAYOUT   = "staff-layout";
    static final String STAFF_DISTANCE = "staff-distance";
    static final String MUSIC_FONT     = "music-font";
    static final String WORD_FONT      = "word-font";
    static final String LYRIC_FONT     = "lyric-font";
    static final String LYRIC_LANGUAGE = "lyric-language";

    // Element names — credit subtree.
    static final String CREDIT       = "credit";
    static final String CREDIT_TYPE  = "credit-type";
    static final String CREDIT_WORDS = "credit-words";

    // Element names — miscellaneous subtree.
    static final String MISCELLANEOUS       = "miscellaneous";
    static final String MISCELLANEOUS_FIELD = "miscellaneous-field";

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
    static final String ATTR_ORIENTATION = "orientation";
    static final String ATTR_LINE_SHAPE  = "line-shape";
    static final String ATTR_LINE_TYPE   = "line-type";
    static final String ATTR_LINE_LENGTH = "line-length";

    // Attribute names — tempo / metronome direction subtree.
    static final String ATTR_TEMPO        = "tempo";
    static final String ATTR_PRINT_OBJECT = "print-object";

    // Attribute names — head / defaults / credit / miscellaneous.
    static final String ATTR_NAME        = "name";
    static final String ATTR_FONT_FAMILY = "font-family";
    static final String ATTR_FONT_SIZE   = "font-size";
    static final String ATTR_FONT_WEIGHT = "font-weight";
    static final String ATTR_FONT_STYLE  = "font-style";
    static final String ATTR_JUSTIFY     = "justify";
    static final String ATTR_HALIGN      = "halign";
    static final String ATTR_XML_LANG    = "xml:lang";
    static final String ATTR_PAGE        = "page";
    static final String ATTR_PLACEMENT   = "placement";
    static final String ATTR_ELEMENT     = "element";

    // Element content values — tempo / metronome direction subtree. The
    // <metronome-relation> symbol between the two metric-modulation note groups;
    // "equals" is the only value the schema currently defines.
    static final String RELATION_EQUALS = "equals";

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

    // Attribute values — <tied orientation> (write-forward tie curve direction).
    static final String ORIENTATION_OVER  = "over";
    static final String ORIENTATION_UNDER = "under";

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

    // Attribute values — fixed document fonts (write-forward).
    static final String MUSIC_FONT_FAMILY = "Bravura";
    static final String MUSIC_FONT_SIZE   = "32";

    // Attribute values — font weight/style (creator/credit/word fonts).
    static final String WEIGHT_BOLD   = "bold";
    static final String WEIGHT_NORMAL = "normal";
    static final String STYLE_ITALIC  = "italic";
    static final String STYLE_NORMAL  = "normal";

    // Attribute values — scaling / page layout (write-forward, fixed).
    static final double SCALING_MILLIMETERS = 7.0;
    static final String SCALING_TENTHS      = "40";
    static final String PAGE_HEIGHT_TENTHS  = "1596";

    // Attribute values — text alignment (credit / direction words).
    static final String JUSTIFY_CENTER = "center";
    static final String HALIGN_LEFT    = "left";
    static final String HALIGN_CENTER  = "center";
    static final String HALIGN_RIGHT   = "right";

    // Attribute values — direction placement (credit / annotation).
    static final String PLACEMENT_ABOVE = "above";
    static final String PLACEMENT_BELOW = "below";

    // Attribute values — <supports> element tokens (write-forward, always emitted).
    static final String SUPPORTS_ACCIDENTAL = "accidental";
    static final String SUPPORTS_BEAM       = "beam";
    static final String SUPPORTS_STEM       = "stem";

    // Attribute values — <lyric-language xml:lang="…">.
    static final String LYRIC_LANGUAGE_DEFAULT = "en";

    // Attribute value — xml:lang on the bangla-lyrics <credit-words> (Bangla ISO 639-1 code).
    static final String CREDIT_LANGUAGE_BANGLA = "bn";

    // Element content values — <creator type="…">.
    static final String CREATOR_COMPOSER = "composer";
    static final String CREATOR_LYRICIST = "lyricist";
    static final String CREATOR_ARRANGER = "arranger";

    // Element content values — <credit-type>.
    static final String CREDIT_TITLE            = "title";
    static final String CREDIT_SUBTITLE         = "subtitle";
    static final String CREDIT_COMPOSER         = "composer";
    static final String CREDIT_LYRICIST         = "lyricist";
    static final String CREDIT_ARRANGER         = "arranger";
    static final String CREDIT_COMPOSITION_DATE = "composition date";
    static final String CREDIT_LYRICS_DATE      = "lyrics date";
    static final String CREDIT_RIGHTS           = "rights";
    static final String CREDIT_PLACE            = "place";
    static final String CREDIT_UNDERLYRICS      = "underlyrics";
    static final String CREDIT_BANGLA_LYRICS    = "bangla-lyrics";
    static final String CREDIT_TRANSLATION      = "translation";
    static final String CREDIT_FOOTNOTES        = "footnotes";

    // Attribute values — <miscellaneous-field name="…">.
    static final String MISC_COMPOSITION_DATE          = "composition-date";
    static final String MISC_LYRICS_DATE               = "lyrics-date";
    static final String MISC_COMPOSITION_PLACE         = "composition-place";
    static final String MISC_LYRICS_SOURCE             = "lyrics-source";
    static final String MISC_UNOFFICIAL_TRANSLATION    = "unofficial-translation";
    static final String MISC_SUB_ATTRIBUTION_FONT      = "sub-attribution-font";
    static final String MISC_SUB_ATTRIBUTION_FONT_SIZE = "sub-attribution-font-size";
    static final String MISC_ROW_HEIGHT_ADJUSTMENT     = "row-height-adjustment";
    static final String MISC_DEFAULT_REST_LENGTH       = "default-rest-length";

    // Rights/copyright format string (write-forward, ignored on read).
    // %d is substituted with the current year.
    static final String COPYRIGHT = "Copyright © %d Sri Chinmoy Centre, Creative Commons BY-NC-ND 4.0";
}
