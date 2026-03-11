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

package songscribe;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.ResourceBundle;
import java.util.Set;

import songscribe.UnitTest;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StringsTest extends UnitTest {

    // Template string: doubled apostrophe produces a literal apostrophe
    @Test
    void testApostropheEscapingInTemplateString() {
        assertThat(Strings.get(Strings.TEST_TEMPLATE_APOSTROPHE, "file.txt")).isEqualTo("Can't find file.txt");
    }

    // Template string: choice formatting for pluralization
    @Test
    void testChoiceTemplateFormatting() {
        assertThat(Strings.get(Strings.TEST_TEMPLATE_CHOICE, 0)).isEqualTo("no items");
        assertThat(Strings.get(Strings.TEST_TEMPLATE_CHOICE, 1)).isEqualTo("one item");
        assertThat(Strings.get(Strings.TEST_TEMPLATE_CHOICE, 5)).isEqualTo("5 items");
    }

    // Every key in the bundle must have a corresponding constant in Strings.class
    @Test
    void testEveryBundleKeyHasAConstant() throws Exception {
        var bundle = ResourceBundle.getBundle("songscribe.strings");

        var constantValues = new HashSet<String>();

        for (var field : Strings.class.getFields()) {
            if (isStringConstant(field)) {
                constantValues.add((String) field.get(null));
            }
        }

        for (var key : bundle.keySet()) {
            assertThat(constantValues)
                .as("Key \"%s\" in strings.properties has no matching constant in Strings.class", key)
                .contains(key);
        }
    }

    // Every constant in Strings.class must correspond to a key in the bundle
    @Test
    void testEveryConstantHasABundleKey() throws Exception {
        var bundle = ResourceBundle.getBundle("songscribe.strings");
        var bundleKeys = bundle.keySet();

        for (var field : Strings.class.getFields()) {
            if (!isStringConstant(field)) {
                continue;
            }

            var key = (String) field.get(null);
            assertThat(bundleKeys)
                .as("Constant %s = \"%s\" has no matching key in strings.properties", field.getName(), key)
                .contains(key);
        }
    }

    // Template string: simple positional substitution
    @Test
    void testSimpleTemplateSubstitution() {
        assertThat(Strings.get(Strings.TEST_TEMPLATE_SIMPLE, "world")).isEqualTo("Hello world");
    }

    private static boolean isStringConstant(Field field) {
        int modifiers = field.getModifiers();
        return Modifier.isPublic(modifiers)
            && Modifier.isStatic(modifiers)
            && Modifier.isFinal(modifiers)
            && field.getType() == String.class;
    }
}
