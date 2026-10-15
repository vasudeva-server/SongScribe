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

/** The {@code <miscellaneous-field name>} values that {@link HeaderBuilder} writes. */
enum MiscellaneousField implements XmlToken {
    COMPOSITION_DATE("composition-date"),
    LYRICS_DATE("lyrics-date"),
    COMPOSITION_PLACE("composition-place"),
    LYRICS_SOURCE("lyrics-source"),
    UNOFFICIAL_TRANSLATION("unofficial-translation"),
    SUB_ATTRIBUTION_FONT("sub-attribution-font"),
    SUB_ATTRIBUTION_FONT_SIZE("sub-attribution-font-size"),
    ROW_HEIGHT_ADJUSTMENT("row-height-adjustment"),
    DEFAULT_REST_LENGTH("default-rest-length");

    private final String token;

    MiscellaneousField(String token) {
        this.token = token;
    }

    @Override
    public String token() {
        return token;
    }
}
