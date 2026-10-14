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
package songscribe.io;

import org.slf4j.Logger;
import org.xml.sax.SAXException;

import songscribe.dom.Key;

/**
 * The three values a legacy {@code .mssw} {@code <keytype>} tag can name, and how the tag combines
 * with its sibling {@code <keys>} count to make a {@link Key}.
 *
 * <p>These names are file-format vocabulary, not domain vocabulary: renaming a domain constant
 * must not stop old files loading.
 */
enum LegacyKeyType {
    NONE(0),
    FLATS(-1),
    SHARPS(1),
    ;

    private final int sign;

    LegacyKeyType(int sign) {
        this.sign = sign;
    }

    static LegacyKeyType forKey(Key key) {
        if (key == Key.NO_ACCIDENTALS) {
            return NONE;
        }

        return key.isFlatKey() ? FLATS : SHARPS;
    }

    static LegacyKeyType parse(Logger log, String str) throws SAXException {
        try {
            return valueOf(str);
        } catch (IllegalArgumentException e) {
            throw DocumentValidation.corrupt(log, "Corrupt document: unknown key type: '{}'", str);
        }
    }

    /**
     * A named accidental type with a count of 0, or {@link #NONE} with a count, means
     * {@link Key#NO_ACCIDENTALS}, so such files keep loading. A negative or too-large count
     * fails the load. A negative count is rejected here because {@link Key#ofFifths} would read
     * it as a valid key in the opposite direction.
     */
    Key keyFor(Logger log, int accidentalCount) throws SAXException {
        if (accidentalCount < 0) {
            throw DocumentValidation.corrupt(
                log, "Corrupt document: negative accidental count: {}", accidentalCount);
        }

        try {
            return Key.ofFifths(sign * accidentalCount);
        } catch (IllegalArgumentException e) {
            throw DocumentValidation.corrupt(
                log,
                "Corrupt document: invalid key: {} with {} accidentals",
                this,
                accidentalCount);
        }
    }
}
