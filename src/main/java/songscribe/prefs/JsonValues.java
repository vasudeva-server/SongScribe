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
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonElement;

import org.jspecify.annotations.Nullable;

/**
 * Coerces a parsed JSON element into the plain Java value {@link Prefs} stores it as.
 * The mapping is fixed by the element's JSON kind, never by an expected type at the
 * call site.
 */
public final class JsonValues {

    private static final Gson GSON = new Gson();

    private JsonValues() {
    }

    /**
     * Converts {@code element} to the Java value {@link Prefs} holds in its store.
     *
     * <p>A JSON boolean becomes a {@link Boolean}; a JSON number becomes a {@link Long}
     * — always, never a {@code Double} or {@code Integer} — so a stored value round-trips
     * through both {@code Prefs.getInt} and {@code Prefs.getLong} without a cast failure; a
     * JSON string becomes a {@link String}. A JSON object becomes an unstructured
     * {@code Map<String, Object>} via Gson's default object mapping. A JSON array becomes a
     * {@code List<String>}, reading every element with {@link JsonElement#getAsString()}.
     *
     * <p>{@code null} is returned only for {@link com.google.gson.JsonNull}, and for
     * nothing else — every other {@link JsonElement} kind is covered above.
     *
     * @param element the parsed JSON element to convert
     * @return the corresponding Java value, or {@code null} if {@code element} is
     *     {@link com.google.gson.JsonNull}
     * @throws UnsupportedOperationException if {@code element} is a JSON array containing
     *     an element that is itself an object or array, since {@link JsonElement#getAsString()}
     *     does not support those
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
