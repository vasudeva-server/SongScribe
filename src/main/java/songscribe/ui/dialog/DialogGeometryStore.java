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

import java.awt.Dimension;
import java.awt.Point;
import java.util.HashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import songscribe.prefs.Prefs;
import songscribe.prefs.PrefsKey;

/**
 * Where each dialog class's window geometry lives between openings.
 * <p>
 * A dialog instance serves one opening, so the geometry it closed with has to be held
 * outside it. This store holds it per dialog class for the life of the process, and backs
 * it with the {@link PrefsKey#DIALOG_GEOMETRY} preference so a class opened for the first
 * time in a session starts where the previous session left it.
 * <p>
 * EDT-only by contract, like the dialogs that use it.
 */
final class DialogGeometryStore {

    private static final String KEY_X = "x";
    private static final String KEY_Y = "y";
    private static final String KEY_WIDTH = "width";
    private static final String KEY_HEIGHT = "height";

    private static final Map<Class<?>, DialogGeometry> GEOMETRY_BY_CLASS = new HashMap<>();

    private DialogGeometryStore() {}

    /**
     * Returns the geometry {@code dialogClass} last closed with.
     *
     * @return the geometry saved for {@code dialogClass} in this process, else the one the
     *         preference holds for it, or {@code null} if neither has one
     */
    static @Nullable DialogGeometry load(Class<?> dialogClass) {
        var geometry = GEOMETRY_BY_CLASS.get(dialogClass);

        if (geometry == null) {
            geometry = loadFromPrefs(dialogClass);

            if (geometry != null) {
                GEOMETRY_BY_CLASS.put(dialogClass, geometry);
            }
        }

        return geometry;
    }

    /**
     * Records {@code geometry} as what {@code dialogClass} closed with.
     *
     * @effects a later {@link #load} for {@code dialogClass} returns {@code geometry}, in this
     *          process and in the next; writes the preference, which posts
     *          {@code PrefsDidChangeNotification}
     */
    static void save(Class<?> dialogClass, DialogGeometry geometry) {
        GEOMETRY_BY_CLASS.put(dialogClass, geometry);

        var location = geometry.location();
        var size = geometry.size();
        var valueMap = new HashMap<String, Object>();
        valueMap.put(KEY_X, location.x);
        valueMap.put(KEY_Y, location.y);

        if (size != null) {
            valueMap.put(KEY_WIDTH, size.width);
            valueMap.put(KEY_HEIGHT, size.height);
        }

        Prefs.putMap(PrefsKey.DIALOG_GEOMETRY, Map.of(dialogClass.getSimpleName(), valueMap));
    }

    private static @Nullable DialogGeometry loadFromPrefs(Class<?> dialogClass) {
        var allGeometry = Prefs.getMap(PrefsKey.DIALOG_GEOMETRY);
        var entry = allGeometry.get(dialogClass.getSimpleName());

        if (!(entry instanceof Map<?, ?> map)) {
            return null;
        }

        var rawX = map.get(KEY_X);
        var rawY = map.get(KEY_Y);

        if (!(rawX instanceof Number) || !(rawY instanceof Number)) {
            return null;
        }

        var location = new Point(((Number) rawX).intValue(), ((Number) rawY).intValue());
        Dimension size = null;

        var rawWidth = map.get(KEY_WIDTH);
        var rawHeight = map.get(KEY_HEIGHT);

        if (rawWidth instanceof Number && rawHeight instanceof Number) {
            size = new Dimension(((Number) rawWidth).intValue(), ((Number) rawHeight).intValue());
        }

        return new DialogGeometry(location, size);
    }
}
