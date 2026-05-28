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

    // Element names.
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

    // Attribute names.
    static final String ATTR_VERSION    = "version";
    static final String ATTR_NEW_SYSTEM = "new-system";
    static final String ATTR_LOCATION   = "location";
    static final String ATTR_DIRECTION  = "direction";
    static final String ATTR_NUMBER     = "number";
    static final String ATTR_ID         = "id";

    // Attribute values.
    static final String YES     = "yes";
    static final String PART_ID = "P1";
}
