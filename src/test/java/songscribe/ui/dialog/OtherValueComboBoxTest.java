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
import javax.swing.JList;
import javax.swing.plaf.basic.ComboPopup;

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

        combo.setSelectedItem(unknownValue);

        assertThat(combo.getSelectedItem()).isEqualTo(unknownValue);

        var lastIndex = combo.getItemCount() - 1;
        assertThat(combo.getItemAt(lastIndex)).isEqualTo(Strings.get(Strings.LABEL_OTHER));
        assertThat(combo.getItemAt(lastIndex - 1)).isEqualTo(unknownValue);
    }

    @Test
    void testSelectingEmptyStringSelectsNoneRowWithoutInsertingARow() {
        var combo = combo(OtherValueComboBox.EmptyChoice.OFFERED);
        var itemCountBefore = combo.getItemCount();

        combo.setSelectedItem("");

        assertThat(combo.getSelectedItem()).isEqualTo("");
        assertThat(combo.getItemCount()).isEqualTo(itemCountBefore);
    }

    @ParameterizedTest
    @EnumSource(OtherValueComboBox.EmptyChoice.class)
    void testEmptyRowIsPresentOnlyWhenOffered(OtherValueComboBox.EmptyChoice emptyChoice) {
        var combo = combo(emptyChoice);

        if (emptyChoice == OtherValueComboBox.EmptyChoice.OFFERED) {
            assertThat(combo.getItemAt(0)).isEmpty();
            assertThat(combo.getSelectedItem()).isEmpty();
        } else {
            assertThat(combo.getItemAt(0)).isNotEmpty();
            assertThat(combo.getSelectedItem()).isNotEmpty();
        }
    }

    /**
     * The list the drop-down shows, which is what every route the user takes selects into, and
     * what the Enter key reads the committed value from.
     */
    private static JList<Object> popupList(OtherValueComboBox combo) {
        return ((ComboPopup) combo.getUI().getAccessibleChild(combo, 0)).getList();
    }

    @Test
    void testBarredEmptyRowCannotBecomeThePopupSelection() {
        var combo = combo(OtherValueComboBox.EmptyChoice.OFFERED);
        var list = popupList(combo);
        combo.setSelectedItem(FIRST_CHOICE);

        combo.setEmptyChoiceSelectable(false);
        list.setSelectedIndex(OtherValueComboBox.EMPTY_INDEX);

        // Nothing the Enter key could commit is the barred row.
        assertThat(list.getSelectedIndex()).isNotEqualTo(OtherValueComboBox.EMPTY_INDEX);

        combo.setEmptyChoiceSelectable(true);
        list.setSelectedIndex(OtherValueComboBox.EMPTY_INDEX);

        assertThat(list.getSelectedIndex()).isEqualTo(OtherValueComboBox.EMPTY_INDEX);
    }

    @Test
    void testSelectingTheOtherRowOpensThePromptAndCommitsNoValue() {
        var combo = combo(OtherValueComboBox.EmptyChoice.WITHHELD);
        combo.setSelectedItem(FIRST_CHOICE);
        var otherLabel = combo.getItemAt(combo.getItemCount() - 1);

        // The route the Enter key takes, which does not pass through setSelectedIndex.
        combo.setSelectedItem(otherLabel);

        assertThat(combo.getSelectedItem()).isEqualTo(FIRST_CHOICE);
    }

    @Test
    void testBarredEmptyRowRefusesTheUserButNotACaller() {
        var combo = combo(OtherValueComboBox.EmptyChoice.OFFERED);
        combo.setSelectedItem(FIRST_CHOICE);

        combo.setEmptyChoiceSelectable(false);

        // The user's route, which every popup, arrow key and typeahead selection arrives at.
        combo.setSelectedIndex(OtherValueComboBox.EMPTY_INDEX);
        assertThat(combo.getSelectedItem()).isEqualTo(FIRST_CHOICE);

        // A caller's route, which barring does not govern.
        combo.setSelectedItem("");
        assertThat(combo.getSelectedItem()).isEmpty();

        combo.setSelectedItem(FIRST_CHOICE);
        combo.setEmptyChoiceSelectable(true);
        combo.setSelectedIndex(OtherValueComboBox.EMPTY_INDEX);
        assertThat(combo.getSelectedItem()).isEmpty();
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
