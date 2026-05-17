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

package songscribe.font;

import java.awt.Font;

/** Read interface for the six document-level font roles. */
@FunctionalInterface
public interface DocumentFontsHolder {

    Font getFont(FontKey key);

    default Font getTitleFont() {
        return getFont(FontKey.TITLE);
    }

    default Font getLyricsFont() {
        return getFont(FontKey.LYRICS);
    }

    default Font getAttributionFont() {
        return getFont(FontKey.ATTRIBUTION);
    }

    default Font getAnnotationFont() {
        return getFont(FontKey.ANNOTATION);
    }

    default Font getFootnoteFont() {
        return getFont(FontKey.FOOTNOTE);
    }

    default Font getBanglaFont() {
        return getFont(FontKey.BANGLA);
    }
}
