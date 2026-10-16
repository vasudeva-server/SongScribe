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

import org.jspecify.annotations.Nullable;

/**
 * The text of the leaf tag that a legacy SAX reader has open. A legacy document stores each
 * value as the text of a tag with no child tags, so a tag that another tag opens inside before
 * it closes yields no text.
 */
final class LeafTagText {

    @FunctionalInterface
    interface Handler<E extends Exception> {
        void accept(String tag, String text) throws E;
    }

    private @Nullable String openTag = null;

    private final StringBuilder text = new StringBuilder();

    void open(String tag) {
        openTag = tag;
        text.setLength(0);
    }

    void reset() {
        openTag = null;
        text.setLength(0);
    }

    void append(char[] ch, int start, int length) {
        if (openTag != null) {
            text.append(ch, start, length);
        }
    }

    /**
     * {@code handler} receives the text only when {@code qName} closes the open leaf tag.
     */
    <E extends Exception> void close(String qName, Handler<E> handler) throws E {
        var tag = openTag;

        if (tag != null && tag.equals(qName)) {
            handler.accept(tag, text.toString());
        }

        reset();
    }
}
