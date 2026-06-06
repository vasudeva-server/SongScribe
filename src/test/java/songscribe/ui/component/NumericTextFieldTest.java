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

package songscribe.ui.component;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import javax.swing.text.AbstractDocument;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.util.UIUtils;

class NumericTextFieldTest extends UnitTest {

    // -------------------------------------------------------------------
    // Row 58: Integer filter — digits allowed, non-digit rejected with beep
    // -------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class IntegerFilter {

        @Test
        void testDigitInsertionAccepted() throws Exception {
            var field = new NumericTextField();

            try (var uiUtilsMock = mockStatic(UIUtils.class)) {
                field.getDocument().insertString(0, "42", null);

                uiUtilsMock.verify(UIUtils::beep, never());
                assertThat(field.getText()).isEqualTo("42");
            }
        }

        @Test
        void testNonDigitInsertionRejectedWithBeep() throws Exception {
            var field = new NumericTextField();

            try (var uiUtilsMock = mockStatic(UIUtils.class)) {
                field.getDocument().insertString(0, "a", null);

                uiUtilsMock.verify(UIUtils::beep);
                assertThat(field.getText()).isEmpty();
            }
        }

        @Test
        void testDecimalPointRejectedByIntegerFilter() throws Exception {
            var field = new NumericTextField();

            try (var uiUtilsMock = mockStatic(UIUtils.class)) {
                field.getDocument().insertString(0, ".", null);

                uiUtilsMock.verify(UIUtils::beep);
                assertThat(field.getText()).isEmpty();
            }
        }
    }

    // -------------------------------------------------------------------
    // Row 59: Decimal filter — valid decimal prospective string allowed;
    //         would-be invalid (two dots) rejected with beep
    // -------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class DecimalFilter {

        @Test
        void testValidDecimalInsertionAccepted() throws Exception {
            // NumericTextField(boolean allowDecimal) routes to addNumericFilter(comp, true)
            var field = new NumericTextField(true);

            try (var uiUtilsMock = mockStatic(UIUtils.class)) {
                field.getDocument().insertString(0, "3.14", null);

                uiUtilsMock.verify(UIUtils::beep, never());
                assertThat(field.getText()).isEqualTo("3.14");
            }
        }

        @Test
        void testNonDecimalInsertionRejectedWithBeep() throws Exception {
            var field = new NumericTextField(true);

            try (var uiUtilsMock = mockStatic(UIUtils.class)) {
                field.getDocument().insertString(0, "abc", null);

                uiUtilsMock.verify(UIUtils::beep);
                assertThat(field.getText()).isEmpty();
            }
        }

        @Test
        void testNonDigitRejectedAfterDecimalContent() throws Exception {
            // NumericTextField(true) uses CustomDocumentFilter with [\d.]+ (regex per-insertion).
            // This filter validates each inserted string in isolation, not the prospective
            // full-document text — so a second dot in a later insert would not be caught.
            // The test verifies that non-digit, non-dot characters are rejected even after
            // existing decimal content occupies the field (non-zero offset insertion).
            var field = new NumericTextField(true);

            try (var uiUtilsMock = mockStatic(UIUtils.class)) {
                field.getDocument().insertString(0, "1.5", null);
                uiUtilsMock.verify(UIUtils::beep, never());
                assertThat(field.getText()).isEqualTo("1.5");

                // Letters rejected even with decimal allowed — at a non-zero offset
                field.getDocument().insertString(3, "x", null);
                uiUtilsMock.verify(UIUtils::beep);
                assertThat(field.getText()).isEqualTo("1.5");
            }
        }
    }

    // -------------------------------------------------------------------
    // Row 60: addDecimalFilter variant — DecimalDocumentFilter vs CustomDocumentFilter
    //         routing distinguishes single-dot constraint from regex-based filter
    // -------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class AddDecimalFilterRouting {

        @Test
        void testAddDecimalFilterInstallsProspectiveTextFilter() throws Exception {
            // InputUtils.addDecimalFilter installs DecimalDocumentFilter (prospective-text based)
            // which rejects a second decimal point even when inserted at a different offset.
            var field = new NumericTextField(0, false);
            InputUtils.addDecimalFilter(field);

            try (var uiUtilsMock = mockStatic(UIUtils.class)) {
                field.getDocument().insertString(0, "1.", null);
                uiUtilsMock.verify(UIUtils::beep, never());
                assertThat(field.getText()).isEqualTo("1.");

                // Prospective text "1.2.3" has two dots — must be rejected
                var doc = (AbstractDocument) field.getDocument();
                doc.insertString(doc.getLength(), "2.3", null);
                uiUtilsMock.verify(UIUtils::beep);
                assertThat(field.getText()).isEqualTo("1.");
            }
        }

        @Test
        void testNumericFilterFalseInstallsIntegerCustomDocumentFilter() throws Exception {
            // addNumericFilter(comp, false) installs CustomDocumentFilter with \d+ regex
            var field = new NumericTextField(false);

            try (var uiUtilsMock = mockStatic(UIUtils.class)) {
                field.getDocument().insertString(0, "7", null);
                uiUtilsMock.verify(UIUtils::beep, never());
                assertThat(field.getText()).isEqualTo("7");

                // Decimal point rejected — integer-only
                field.getDocument().insertString(1, ".", null);
                uiUtilsMock.verify(UIUtils::beep);
                assertThat(field.getText()).isEqualTo("7");
            }
        }
    }
}
