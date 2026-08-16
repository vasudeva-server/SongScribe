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

import java.awt.Font;

import songscribe.util.Copyable;

/**
 * The font {@link FontDialog} opens on.
 *
 * <p>It exists because {@link Font} is a JDK class and cannot be made {@link Copyable}, which
 * {@link DialogController}'s input bound requires. Wrapping is the only way a type we do not own
 * crosses the dialog interface, and stating that here is what keeps the bound total rather than
 * total-with-exceptions.
 *
 * @param font the font to show as selected when the chooser opens
 */
public record FontChoice(Font font) implements Copyable<FontChoice> {

    /**
     * {@inheritDoc}
     *
     * @return {@code this}. {@link Font} is immutable, so there is nothing for a copy to separate —
     *         which is the fact this record's existence asserts.
     */
    @Override
    public FontChoice copy() {
        return this;
    }
}
