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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import songscribe.dom.Key;
import songscribe.dom.KeyType;
import songscribe.io.DocumentValidation;

/**
 * Bidirectional mapping between a SongScribe {@link Key} and its MusicXML
 * signed-{@code <fifths>} encoding. The writer encodes via {@link #toFifths}; the reader
 * decodes via {@link #toKey}. Owning both directions here keeps the sign convention — flats
 * negative, sharps positive, no accidentals zero — from drifting apart across the two files.
 *
 * <p>The two are exact inverses over the whole of {@link Key#allSignatures()}:
 * {@code toKey(toFifths(k))} is {@code k} for every valid key, and {@code toFifths(toKey(f))}
 * is {@code f} for every {@code f} {@link #toKey} accepts.
 *
 * <p>{@link #toFifths} is public because {@code songscribe.midi}'s MIDI key signature export
 * shares this sign convention rather than recomputing it; {@link #toKey} stays package-private,
 * since only the MusicXML reader decodes fifths.
 */
public final class KeySignatureMapping {

    private static final Logger LOG = LoggerFactory.getLogger(KeySignatureMapping.class);

    private KeySignatureMapping() {}

    /**
     * Encodes a key as signed fifths per the MusicXML convention: flats negative, sharps
     * positive, and {@link KeyType#NONE} zero.
     *
     * @param key the key to encode
     * @return its {@code <fifths>} value, in
     *         {@code -}{@value Key#MAX_ACCIDENTAL_COUNT}{@code ..}{@value Key#MAX_ACCIDENTAL_COUNT}
     */
    public static int toFifths(Key key) {
        if (key.keyType() == KeyType.FLATS) {
            return -key.accidentalCount();
        }

        // SHARPS → +count; NONE → 0 (its accidental count is always 0).
        return key.accidentalCount();
    }

    /**
     * Decodes signed fifths to the {@link Key} they encode.
     *
     * <p>Zero is C major: a {@link Key} states the accidental type and the count together, so a
     * count of zero has exactly one type it can carry.
     *
     * <p>A magnitude past {@value Key#MAX_ACCIDENTAL_COUNT} is rejected rather than clamped.
     * Clamping would load a document as a song silently in the wrong key, and the count reaches
     * {@link Key#altersPitchClass}, which is indexed per note during pitch resolution.
     *
     * @param fifths the MusicXML {@code <fifths>} value: flats negative, sharps positive, with a
     *               magnitude of at most {@value Key#MAX_ACCIDENTAL_COUNT}
     * @return the key that value encodes; never null
     * @throws SAXException if {@code |fifths|} exceeds {@value Key#MAX_ACCIDENTAL_COUNT}, which no
     *                      key signature this program can represent ever does
     */
    static Key toKey(int fifths) throws SAXException {
        if (Math.abs(fifths) > Key.MAX_ACCIDENTAL_COUNT) {
            throw DocumentValidation.corrupt(
                LOG, "Corrupt document: <fifths> out of range: {}, maximum magnitude {}",
                fifths, Key.MAX_ACCIDENTAL_COUNT);
        }

        if (fifths == 0) {
            return new Key(KeyType.NONE, 0);
        }

        return new Key(fifths > 0 ? KeyType.SHARPS : KeyType.FLATS, Math.abs(fifths));
    }
}
