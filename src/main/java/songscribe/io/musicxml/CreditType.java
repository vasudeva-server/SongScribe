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

import songscribe.font.FontKey;

/** The {@code <credit-type>} values that {@link HeaderBuilder} writes, each with the font its words use. */
enum CreditType implements XmlToken {
    TITLE("title", FontKey.TITLE),
    SUBTITLE("subtitle", FontKey.SUBTITLE),
    COMPOSER("composer", FontKey.ATTRIBUTION),
    LYRICIST("lyricist", FontKey.ATTRIBUTION),
    ARRANGER("arranger", FontKey.ATTRIBUTION),
    COMPOSITION_DATE("composition date", FontKey.ATTRIBUTION),
    LYRICS_DATE("lyrics date", FontKey.ATTRIBUTION),
    RIGHTS("rights", FontKey.ATTRIBUTION),
    PLACE("place", FontKey.ATTRIBUTION),
    UNDERLYRICS("underlyrics", FontKey.LYRICS),
    BANGLA_LYRICS("bangla-lyrics", FontKey.BANGLA),
    TRANSLATION("translation", FontKey.LYRICS),
    FOOTNOTES("footnotes", FontKey.FOOTNOTE);

    private final String token;

    final FontKey fontKey;

    CreditType(String token, FontKey fontKey) {
        this.token = token;
        this.fontKey = fontKey;
    }

    @Override
    public String token() {
        return token;
    }
}
