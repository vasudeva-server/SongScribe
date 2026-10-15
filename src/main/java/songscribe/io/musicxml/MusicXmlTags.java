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
 * The MusicXML element names, attribute names and fixed values that the code reads or writes
 * by name rather than through the proxymusic object graph.
 */
final class MusicXmlTags {

    private MusicXmlTags() {}

    /**
     * The minimum version the reader accepts; older documents are rejected.
     */
    static final String VERSION_VALUE = "4.0";

    // MusicXML's positional coordinate unit is the tenth: 1 staff space = 10
    // tenths. Shared by both sides of the codec so the writer's px→tenths and the
    // reader's tenths→px conversions cannot silently disagree.
    static final int TENTHS_PER_STAFF_SPACE = 10;

    static final String SCORE_PARTWISE = "score-partwise";

    static final String NOTE        = "note";
    static final String DOT         = "dot";
    static final String ACCENT      = "accent";
    static final String STACCATO    = "staccato";
    static final String BREATH_MARK = "breath-mark";
    static final String FALLOFF     = "falloff";
    static final String NORMAL_DOT  = "normal-dot";
    static final String WAVY_LINE   = "wavy-line";

    static final String METRONOME          = "metronome";
    static final String PER_MINUTE         = "per-minute";
    static final String METRONOME_NOTE     = "metronome-note";
    static final String METRONOME_RELATION = "metronome-relation";

    static final String SOFTWARE = "software";
    static final String SUPPORTS = "supports";

    static final String CREDIT_TYPE  = "credit-type";
    static final String CREDIT_WORDS = "credit-words";

    static final String ATTR_VERSION    = "version";
    static final String ATTR_RELATIVE_X = "relative-x";
    static final String ATTR_FONT_SIZE  = "font-size";

    // Every SongScribe key is major, so this is the only <mode> value the writer emits and the
    // reader never reads one back.
    static final String MODE_MAJOR = "major";

    // The <metronome-relation> symbol between the two metric-modulation note groups; "equals" is
    // the only value the schema defines.
    static final String RELATION_EQUALS = "equals";

    static final String PART_ID = "P1";

    // The wedge @number is always "1" (only one hairpin is ever open at a time); the ending
    // @number is "1" for the first volta bracket and "2" for the second.
    static final String NUMBER_1 = "1";
    static final String NUMBER_2 = "2";

    // Write-forward: fixed, never read.
    static final String MUSIC_FONT_FAMILY = "Bravura";
    static final String MUSIC_FONT_SIZE   = "32";

    // Write-forward: fixed, never read.
    static final double SCALING_MILLIMETERS = 7.0;
    static final String SCALING_TENTHS      = "40";
    static final String PAGE_HEIGHT_TENTHS  = "1596";

    static final String SUPPORTS_ACCIDENTAL = "accidental";
    static final String SUPPORTS_BEAM       = "beam";
    static final String SUPPORTS_STEM       = "stem";

    static final String LYRIC_LANGUAGE_DEFAULT = "en";

    // The xml:lang of the bangla-lyrics <credit-words>: the Bangla ISO 639-1 code.
    static final String CREDIT_LANGUAGE_BANGLA = "bn";

    // Write-forward: ignored on read. %d is substituted with the current year.
    static final String COPYRIGHT = "Copyright © %d Sri Chinmoy Centre, Creative Commons BY-NC-ND 4.0";
}
