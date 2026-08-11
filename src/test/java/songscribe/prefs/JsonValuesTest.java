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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import com.google.gson.JsonNull;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

/**
 * Exercises every JSON element kind {@link JsonValues#toJavaValue} maps: the three
 * primitive kinds (boolean, number, string), object, array, and the {@code null} case.
 * Each case asserts both the returned Java type and its value, since {@link Prefs}'s
 * typed getters depend on the exact type — a number stored as anything but
 * {@link Long} would break {@code getInt}/{@code getLong} for large values.
 */
class JsonValuesTest {

    @Test
    void testBooleanElementReturnsBooleanType() {
        // boolean JSON primitive → Java Boolean; the type matters because getBoolean
        // casts directly to Boolean — a non-Boolean stored value causes ClassCastException.
        var result = JsonValues.toJavaValue(JsonParser.parseString("true"));
        assertThat(result).isInstanceOf(Boolean.class).isEqualTo(Boolean.TRUE);
    }

    @Test
    void testNumberElementStoredAsLong() {
        // Numbers must be stored as Long, not Integer or Double.
        // getInt calls Number.intValue() and getLong calls Number.longValue() — both
        // require a numeric type; storing as Double would lose precision for large values.
        var result = JsonValues.toJavaValue(JsonParser.parseString("42"));
        assertThat(result).isInstanceOf(Long.class).isEqualTo(42L);
    }

    @Test
    void testStringElementReturnsString() {
        var result = JsonValues.toJavaValue(JsonParser.parseString("\"hello\""));
        assertThat(result).isInstanceOf(String.class).isEqualTo("hello");
    }

    @Test
    void testObjectElementReturnsMap() {
        // JSON objects are deserialized as Map<String,Object> so getMap() can
        // return them without additional conversion.
        var result = JsonValues.toJavaValue(JsonParser.parseString("{\"x\":1,\"y\":2}"));
        assertThat(result).isInstanceOf(Map.class);

        @SuppressWarnings("unchecked")
        var map = (Map<String, Object>) result;
        assertThat(map).containsKey("x").containsKey("y");
    }

    @Test
    void testArrayElementReturnsStringList() {
        // JSON arrays are deserialized as List<String> so getStringList() can return them.
        var result = JsonValues.toJavaValue(JsonParser.parseString("[\"a\",\"b\"]"));
        assertThat(result).isInstanceOf(List.class);

        @SuppressWarnings("unchecked")
        var list = (List<String>) result;
        assertThat(list).containsExactly("a", "b");
    }

    @Test
    void testNullJsonElementReturnsNull() {
        // JsonNull is returned for JSON null literals; toJavaValue returns null
        // so that the caller (loadStore/loadDefaults) can skip null entries.
        var result = JsonValues.toJavaValue(JsonNull.INSTANCE);
        assertThat(result).isNull();
    }
}
