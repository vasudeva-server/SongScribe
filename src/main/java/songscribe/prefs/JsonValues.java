/*
    SongScribe song notation program
    Copyright (C) Sri Chinmoy Centres International

    This file is part of SongScribe.

    SongScribe is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 3 of the License, or
    (at your option) any later version.

    SongScribe is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/
package songscribe.prefs;

import java.util.ArrayList;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import org.jspecify.annotations.Nullable;

/**
 * Coerces a parsed JSON element into the plain Java value {@link Prefs} stores it as, by the
 * element's JSON kind alone.
 */
public final class JsonValues {

    private static final Gson GSON = new Gson();

    private JsonValues() {
    }

    /**
     * A JSON number always becomes a {@link Long}, so a stored value round-trips through both
     * {@code Prefs.getInt} and {@code Prefs.getLong}.
     *
     * @param element no element of a JSON array is itself an object or an array
     * @return the Java value, or {@code null} only for {@link com.google.gson.JsonNull}
     */
    public static @Nullable Object toJavaValue(JsonElement element) {
        if (element.isJsonPrimitive()) {
            var primitive = element.getAsJsonPrimitive();

            if (primitive.isBoolean()) {
                return primitive.getAsBoolean();
            }

            if (primitive.isNumber()) {
                return primitive.getAsLong();
            }

            return primitive.getAsString();
        }

        if (element.isJsonObject()) {
            return GSON.fromJson(element, Map.class);
        }

        if (element.isJsonArray()) {
            var list = new ArrayList<String>();

            for (var item : element.getAsJsonArray()) {
                list.add(item.getAsString());
            }

            return list;
        }

        return null;
    }
}
