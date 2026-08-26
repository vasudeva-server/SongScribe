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
 * <p>These names are file-format vocabulary, not domain vocabulary, which is why they live in the
 * reader. A {@code Key} is one signed number and has no accidental-type component to name; when
 * the two were the same enum, renaming a domain constant silently stopped old files loading, with
 * nothing in the build to catch it.
 *
 * <p>The song and each line both carry the pair, and both are read through here so the two cannot
 * judge the same file differently.
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

    /**
     * Returns the type whose name the {@code <keytype>} tag takes for the given key — the inverse
     * of {@link #keyFor}, for the writer that still emits this format.
     *
     * @param key the key being written
     * @return {@link #NONE} for {@link Key#NO_ACCIDENTALS}, otherwise the type matching its sign
     */
    static LegacyKeyType forKey(Key key) {
        if (key == Key.NO_ACCIDENTALS) {
            return NONE;
        }

        return key.isFlatKey() ? FLATS : SHARPS;
    }

    /**
     * Returns the type the given {@code <keytype>} tag text names.
     *
     * @param log the reader's logger, so the corruption is reported against the reading class
     * @param str the tag's text
     * @return the type it names
     * @throws SAXException if {@code str} is not one of the three names
     */
    static LegacyKeyType parse(Logger log, String str) throws SAXException {
        try {
            return valueOf(str);
        } catch (IllegalArgumentException e) {
            throw DocumentValidation.corrupt(log, "Corrupt document: unknown key type: '{}'", str);
        }
    }

    /**
     * Returns the key this type and its sibling {@code <keys>} count encode.
     *
     * <p>The two tags are an unconstrained pair, so combinations no key can have are
     * representable in a {@code .mssw} file and do occur: a named accidental type with a count of
     * 0, and — from a corrupt file — {@link #NONE} with a count. Both mean
     * {@link Key#NO_ACCIDENTALS}: it is what the pair always sounded and drew as, so a file
     * carrying one keeps loading rather than being refused by a migration path.
     *
     * <p>A count that is negative or too large to be a key is not normalized, because
     * neither has a reading that preserves what the file meant. Both are rejected — this is the
     * conversion of the tag pair into a domain type, so it either yields a {@link Key} or fails
     * the load.
     *
     * <p>The two rejections are separate conditions, not one range checked twice. A negative
     * count cannot reach {@link Key#ofFifths} as an error, because multiplying it by this type's
     * sign lands on a perfectly valid key in the opposite direction — "-3 flats" would load as
     * three sharps. That a count is a magnitude is a fact about this file format, so it is
     * checked here; the magnitude's limit is a fact about keys, so {@code ofFifths}
     * owns it.
     *
     * @param log             the reader's logger, so the corruption is reported against the
     *                        reading class
     * @param accidentalCount the sibling {@code <keys>} count
     * @return the key the pair encodes; never null
     * @throws SAXException if {@code accidentalCount} is negative, or exceeds
     *                      {@value Key#MAX_ACCIDENTAL_COUNT}
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
