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

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;

import javax.swing.GrayFilter;
import javax.swing.Icon;
import javax.swing.UIManager;
import javax.swing.border.Border;

import songscribe.error.RuntimeError;

/**
 * Typed access to custom {@code SongScribe.*} properties defined in
 * {@code FlatLaf.properties}. Keys are provided by the generated
 * {@link FlatLafKey} enum.
 *
 * <p>Use the typed getters ({@code getColor}, {@code getInt}, etc.) — one
 * exists for every type FlatLaf supports. A missing or wrongly-typed property
 * is always a fatal error — it indicates a broken installation.
 */
public final class FlatLafProps {

    private FlatLafProps() {}

    private static <T> T get(FlatLafKey key, Class<T> type) {
        var value = UIManager.get(key.key());

        if (value == null) {
            throw RuntimeError.missingResource("Missing required UI property: " + key.key());
        }

        if (!type.isInstance(value)) {
            throw RuntimeError.exit(
                "Wrong type for UI property " + key.key()
                + ": expected " + type.getSimpleName()
                + ", got " + value.getClass().getSimpleName()
            );
        }

        return type.cast(value);
    }

    public static boolean getBoolean(FlatLafKey key) {
        return get(key, Boolean.class);
    }

    public static int getInt(FlatLafKey key) {
        return get(key, Integer.class);
    }

    public static float getFloat(FlatLafKey key) {
        return get(key, Float.class);
    }

    public static String getString(FlatLafKey key) {
        return get(key, String.class);
    }

    public static Color getColor(FlatLafKey key) {
        return get(key, Color.class);
    }

    public static Font getFont(FlatLafKey key) {
        return get(key, Font.class);
    }

    public static Insets getInsets(FlatLafKey key) {
        return get(key, Insets.class);
    }

    public static Dimension getDimension(FlatLafKey key) {
        return get(key, Dimension.class);
    }

    public static Border getBorder(FlatLafKey key) {
        return get(key, Border.class);
    }

    public static Icon getIcon(FlatLafKey key) {
        return get(key, Icon.class);
    }

    public static GrayFilter getGrayFilter(FlatLafKey key) {
        return get(key, GrayFilter.class);
    }
}
