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

import songscribe.util.Copyable;

/**
 * The text {@link OtherValueDialog} opens on.
 *
 * <p>It exists because {@link String} is a JDK class and cannot be made {@link Copyable}, which
 * {@link DialogController}'s input bound requires. {@link FontChoice} is the same case for
 * {@link java.awt.Font}.
 *
 * @param text the value to show in the field when the prompt opens
 */
record OtherValue(String text) implements Copyable<OtherValue> {

    /**
     * {@inheritDoc}
     *
     * @return {@code this}; {@link String} is immutable, so there is nothing for a copy to separate
     */
    @Override
    public OtherValue copy() {
        return this;
    }
}
