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
package songscribe.dom;

import org.audiveris.proxymusic.AboveBelow;
import org.audiveris.proxymusic.LeftCenterRight;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import songscribe.util.Copyable;

/**
 * A piece of text placed above or below a note — {@code dolce}, {@code cresc.}, {@code Fine}.
 *
 * <p>The text is expected to be non-blank; the UI guarantees it, and this type only logs a
 * warning and stores blank text anyway.
 *
 * <p>An annotation is an immutable value, compared by value. A hand-placed vertical offset
 * belongs to the attachment holding it ({@link LineElement#getUserYOffsetSs()}), not to the
 * annotation.
 */
public record Annotation(String text, LeftCenterRight alignment, AboveBelow placement)
    implements Copyable<Annotation> {

    private static final Logger LOG = LoggerFactory.getLogger(Annotation.class);

    public static final LeftCenterRight DEFAULT_ALIGNMENT = LeftCenterRight.LEFT;

    public static final AboveBelow DEFAULT_PLACEMENT = AboveBelow.ABOVE;

    public Annotation {
        warnIfBlank(text);
    }

    public Annotation(String text) {
        this(text, DEFAULT_ALIGNMENT, DEFAULT_PLACEMENT);
    }

    private static void warnIfBlank(String text) {
        if (text.isBlank()) {
            LOG.warn("Annotation text must not be blank");
        }
    }

    @Override
    public Annotation copy() {
        return this;
    }
}
