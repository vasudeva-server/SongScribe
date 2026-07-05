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
import org.slf4j.helpers.MessageFormatter;
import org.xml.sax.SAXException;

public final class DocumentValidation {

    private DocumentValidation() {}

    static SAXException corrupt(Logger log, String format, Object... args) {
        var message = MessageFormatter.arrayFormat(format, args).getMessage();
        log.error(message);
        return new SAXException(message);
    }

    static void requireIndexInRange(Logger log, int start, int end, int elementCount, String kind) throws SAXException {
        if (start < 0 || end >= elementCount || start > end) {
            throw corrupt(log, "Corrupt document: {} index out of range: {}, {}", kind, start, end);
        }
    }

    public static int parseIntOrThrow(Logger log, String tag, String raw) throws SAXException {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw corrupt(log, "Corrupt document: malformed {} value: '{}'", tag, raw);
        }
    }

    static float parseFloatOrThrow(Logger log, String tag, String raw) throws SAXException {
        try {
            return Float.parseFloat(raw);
        } catch (NumberFormatException e) {
            throw corrupt(log, "Corrupt document: malformed {} value: '{}'", tag, raw);
        }
    }

    public static double parseDoubleOrThrow(Logger log, String tag, String raw) throws SAXException {
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            throw corrupt(log, "Corrupt document: malformed {} value: '{}'", tag, raw);
        }
    }
}
