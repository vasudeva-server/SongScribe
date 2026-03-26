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
package songscribe.ui;

import javax.swing.UIManager;

import songscribe.error.RuntimeError;

/**
 * Typed access to custom {@code SongScribe.*} properties defined in
 * {@code FlatLaf.properties}. Keys are provided by the generated
 * {@link FlatLafKeys} constants class.
 *
 * <p>Missing properties are a fatal error — they indicate a broken
 * installation (FlatLaf.properties incomplete or not loaded).
 */
public final class FlatLafProps {

    private FlatLafProps() {}

    /**
     * Returns a typed property value from UIManager.
     * The return type is inferred from the assignment target.
     * Throws RuntimeError.exit if the property is missing.
     */
    @SuppressWarnings("unchecked")
    public static <T> T get(String key) {
        var value = UIManager.get(key);

        if (value == null) {
            throw RuntimeError.exit("Missing required UI property: " + key);
        }

        return (T) value;
    }
}
