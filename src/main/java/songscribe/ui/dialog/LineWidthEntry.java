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
package songscribe.ui.dialog;

import songscribe.util.LengthUnit;

/**
 * What the Song Settings line-width field says, as a value: the text in it, the unit that text
 * is expressed in, and whether the user typed it or it is still what the field was populated
 * with.
 *
 * <p><strong>The text is raw and may be nonsense.</strong> Nothing is parsed on the way in —
 * an empty field, {@code "abc"} and a width past the page's limits all produce an entry, so
 * that gathering the controls is total and rejecting bad input stays
 * {@link songscribe.ui.dialog.backend.LineWidthRules#validate validation's} job rather than
 * the field's.
 *
 * <p><strong>Why {@link Edit} is carried rather than derived.</strong> The field is populated
 * with the stored width rounded to two decimal places of the display unit, so parsing that
 * text back does not return the width it came from — it quantizes it, by enough to push a line
 * that only just fits past the staff margin. A width the user never touched must therefore
 * commit as the stored value and not as a reading of its own display text. Once the user
 * types, the typed value <em>is</em> the intent and there is nothing left to preserve.
 *
 * @param text the field's current text, exactly as it stands, including empty
 * @param unit the unit {@code text} is expressed in — what the field's adjacent label says
 * @param edit whether {@code text} is the user's or the one the field was populated with
 */
public record LineWidthEntry(String text, LengthUnit unit, Edit edit) {

    /**
     * Whether the line-width field still holds the text it was populated with.
     *
     * <p>An enum rather than a boolean because it is what
     * {@link songscribe.ui.dialog.backend.LineWidthRules#resolveSs} switches on, and
     * {@code true} at that call site would name nothing.
     */
    public enum Edit {

        /** The field's text is untouched, so the stored width is what the user is asking for. */
        UNEDITED,

        /** The user typed the text, so the text is what the user is asking for. */
        EDITED,
    }
}
