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

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import songscribe.Strings;
import songscribe.UnitTest;

import static org.assertj.core.api.Assertions.assertThat;

class OtherValueComboBoxTest extends UnitTest {

    private static final String CHOICES_FILE = "test-choices";
    private static final String FIRST_CHOICE = "Andante";

    private static OtherValueComboBox combo(OtherValueComboBox.EmptyChoice emptyChoice) {
        return new OtherValueComboBox(
            new OtherValuePrompt("Title", "Label"),
            emptyChoice,
            List.of(CHOICES_FILE)
        );
    }

    @Test
    void testUnknownValueIsAddedAboveOtherAndSelected() {
        var combo = combo(OtherValueComboBox.EmptyChoice.WITHHELD);
        var unknownValue = "Poco a poco accelerando";

        combo.setValue(unknownValue);

        assertThat(combo.getValue()).isEqualTo(unknownValue);

        var lastIndex = combo.getItemCount() - 1;
        assertThat(combo.getItemAt(lastIndex)).isEqualTo(Strings.get(Strings.LABEL_OTHER));
        assertThat(combo.getItemAt(lastIndex - 1)).isEqualTo(unknownValue);
    }

    @ParameterizedTest
    @EnumSource(OtherValueComboBox.EmptyChoice.class)
    void testEmptyRowIsPresentOnlyWhenOffered(OtherValueComboBox.EmptyChoice emptyChoice) {
        var combo = combo(emptyChoice);

        if (emptyChoice == OtherValueComboBox.EmptyChoice.OFFERED) {
            assertThat(combo.getItemAt(0)).isEmpty();
            assertThat(combo.getValue()).isEmpty();
        } else {
            assertThat(combo.getItemAt(0)).isNotEmpty();
            assertThat(combo.getValue()).isNotEmpty();
        }
    }

    @ParameterizedTest
    @EnumSource(OtherValueComboBox.EmptyChoice.class)
    void testCommandLabelsAndExistingValuesAreInUse(OtherValueComboBox.EmptyChoice emptyChoice) {
        var combo = combo(emptyChoice);

        assertThat(combo.isValueInUse(Strings.get(Strings.LABEL_OTHER))).isTrue();
        assertThat(combo.isValueInUse(FIRST_CHOICE)).isTrue();
        assertThat(combo.isValueInUse("Poco a poco accelerando")).isFalse();
        assertThat(combo.isValueInUse(Strings.get(Strings.LABEL_NONE)))
            .isEqualTo(emptyChoice == OtherValueComboBox.EmptyChoice.OFFERED);
    }
}
