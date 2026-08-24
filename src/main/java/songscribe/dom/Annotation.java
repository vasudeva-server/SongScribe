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
 * <p>Annotation text is expected to be non-blank. That is guaranteed by the UI, not by this
 * type: the annotation combo offers only non-blank items, and its {@code Other…} prompt
 * refuses a blank entry. This type does not enforce the expectation — it logs a warning if
 * it is given blank text and stores it anyway. A blank annotation read from a file is dropped
 * by the reader rather than attached, since it has nothing to draw.
 *
 * <p><strong>An annotation is a value.</strong> It is immutable and it compares by value, so a
 * caller holding one cannot be surprised by a change made elsewhere, and a controller can tell a
 * gathered annotation from the one already on the element. Whoever wants a different annotation
 * builds one; {@link AnnotationAttachment#setAnnotation} is how an element takes it.
 *
 * <p><strong>Where an annotation sits is not part of it.</strong> A hand-placed vertical offset
 * belongs to the attachment holding it, as {@link LineElement#getUserYOffsetSs()}, the same field
 * every other attachment uses. So editing an annotation cannot move it, and moving one cannot
 * change what it says.
 *
 * @param text      the text to display; expected to be non-blank
 * @param alignment how the text sits against the note it is attached to
 * @param placement whether the text sits above the staff or below it
 */
public record Annotation(String text, LeftCenterRight alignment, AboveBelow placement)
    implements Copyable<Annotation> {

    private static final Logger LOG = LoggerFactory.getLogger(Annotation.class);

    /** How an annotation sits against its note when nothing states an alignment. */
    public static final LeftCenterRight DEFAULT_ALIGNMENT = LeftCenterRight.LEFT;

    /** Which side of the staff an annotation sits on when nothing states a placement. */
    public static final AboveBelow DEFAULT_PLACEMENT = AboveBelow.ABOVE;

    /**
     * @log warn if {@code text} is blank; the blank text is stored anyway
     */
    public Annotation {
        warnIfBlank(text);
    }

    /**
     * An annotation at the defaults, which is what a reader builds before applying whatever the
     * document went on to state.
     *
     * @param text the text to display; expected to be non-blank
     */
    public Annotation(String text) {
        this(text, DEFAULT_ALIGNMENT, DEFAULT_PLACEMENT);
    }

    private static void warnIfBlank(String text) {
        if (text.isBlank()) {
            LOG.warn("Annotation text must not be blank");
        }
    }

    /**
     * {@inheritDoc}
     *
     * @return {@code this}. Every component is immutable, so an annotation holds no state for a
     *         copy to separate.
     */
    @Override
    public Annotation copy() {
        return this;
    }
}
