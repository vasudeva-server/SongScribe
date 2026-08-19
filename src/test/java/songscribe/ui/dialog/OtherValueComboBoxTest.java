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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import songscribe.Strings;
import songscribe.UnitTest;

import static org.assertj.core.api.Assertions.assertThat;

class OtherValueComboBoxTest extends UnitTest {

    private static final String ANNOTATION_FILE = "annotations";

    private static OtherValueComboBox annotationCombo() {
        return new OtherValueComboBox(
            new OtherValuePrompt("Title", "Label"),
            OtherValueComboBox.EmptyChoice.WITHHELD,
            ANNOTATION_FILE
        );
    }

    @Test
    @DisplayName("text equal to the Other… label is an ordinary value, not the sentinel")
    void otherLabelAsValue() {
        var combo = annotationCombo();
        // A distinct instance, which interning of the literal would otherwise prevent: the
        // override recognises the sentinel by identity, so an equal instance must select normally
        // rather than open the prompt.
        var equalToSentinel = new String(Strings.get(Strings.LABEL_OTHER));

        combo.setSelectedItem(equalToSentinel);

        assertThat(combo.getValue()).isEqualTo(equalToSentinel);
    }

    @Test
    @DisplayName("a value the list does not hold is added above Other… and selected")
    void unknownValueIsAdded() {
        var combo = annotationCombo();
        var unknownValue = "Poco a poco accelerando";

        combo.setSelectedItem(unknownValue);

        assertThat(combo.getValue()).isEqualTo(unknownValue);
        var lastIndex = combo.getItemCount() - 1;
        assertThat(combo.getItemAt(lastIndex)).isEqualTo(Strings.get(Strings.LABEL_OTHER));
        assertThat(combo.getItemAt(lastIndex - 1)).isEqualTo(unknownValue);
    }
}
