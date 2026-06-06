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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import java.text.ParseException;

import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.text.DefaultFormatterFactory;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.util.UIUtils;

class InputUtilsTest extends UnitTest {

    // -------------------------------------------------------------------
    // Row 71: CustomDocumentFilter.insertString rejects non-matching text
    //         and emits a beep; document remains unchanged
    // -------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class CustomFilterInsertString {

        @Test
        void testInsertStringRejectsNonMatchingTextAndBeeps() throws Exception {
            var field = new JTextField();
            InputUtils.addInputFilter(field, "\\d+");

            try (var uiUtilsMock = mockStatic(UIUtils.class)) {
                // Attempt to insert a letter into a digits-only field
                field.getDocument().insertString(0, "a", null);

                uiUtilsMock.verify(UIUtils::beep);
                assertThat(field.getText()).isEmpty();
            }
        }

        // -------------------------------------------------------------------
        // Row 72: CustomDocumentFilter.insertString accepts matching text
        // -------------------------------------------------------------------

        @Test
        void testInsertStringAcceptsMatchingText() throws Exception {
            var field = new JTextField();
            InputUtils.addInputFilter(field, "\\d+");

            try (var uiUtilsMock = mockStatic(UIUtils.class)) {
                field.getDocument().insertString(0, "42", null);

                uiUtilsMock.verify(UIUtils::beep, never());
                assertThat(field.getText()).isEqualTo("42");
            }
        }
    }

    // -------------------------------------------------------------------
    // Row 73: CustomDocumentFilter.replace rejects non-matching replacement;
    //         accepts matching replacement
    // -------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class CustomFilterReplace {

        @Test
        void testReplaceRejectsNonMatchingText() throws Exception {
            var field = new JTextField("123");
            InputUtils.addInputFilter(field, "\\d+");

            try (var uiUtilsMock = mockStatic(UIUtils.class)) {
                // Drive CustomDocumentFilter.replace directly: swapping "123" → "abc"
                // is non-matching and must be rejected, leaving the document unchanged.
                var doc = (javax.swing.text.AbstractDocument) field.getDocument();
                doc.replace(0, doc.getLength(), "abc", null);

                uiUtilsMock.verify(UIUtils::beep);
                assertThat(field.getText()).isEqualTo("123");
            }
        }

        @Test
        void testReplaceAcceptsMatchingText() throws Exception {
            var field = new JTextField("123");
            InputUtils.addInputFilter(field, "\\d+");

            try (var uiUtilsMock = mockStatic(UIUtils.class)) {
                // Use AbstractDocument's replace to swap "123" → "456"
                var doc = (javax.swing.text.AbstractDocument) field.getDocument();
                doc.replace(0, doc.getLength(), "456", null);

                uiUtilsMock.verify(UIUtils::beep, never());
                assertThat(field.getText()).isEqualTo("456");
            }
        }
    }

    // -------------------------------------------------------------------
    // Row 74: DecimalDocumentFilter.insertString rejects text that would
    //         make the document content non-decimal
    // -------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class DecimalFilterInsertString {

        @Test
        void testInsertStringRejectsNonDecimalText() throws Exception {
            var field = new JTextField();
            InputUtils.addDecimalFilter(field);

            try (var uiUtilsMock = mockStatic(UIUtils.class)) {
                field.getDocument().insertString(0, "abc", null);

                uiUtilsMock.verify(UIUtils::beep);
                assertThat(field.getText()).isEmpty();
            }
        }

        // -------------------------------------------------------------------
        // Row 76 (partial-decimal acceptance): 1., 0.5 — tested here since
        // it shares the same code path as insertString
        // -------------------------------------------------------------------

        @Test
        void testInsertStringAcceptsPartialDecimalInProgress() throws Exception {
            var field = new JTextField();
            InputUtils.addDecimalFilter(field);

            try (var uiUtilsMock = mockStatic(UIUtils.class)) {
                // "1." is a valid partial decimal-in-progress
                field.getDocument().insertString(0, "1.", null);

                uiUtilsMock.verify(UIUtils::beep, never());
                assertThat(field.getText()).isEqualTo("1.");
            }
        }

        @Test
        void testInsertStringAcceptsFullDecimalValue() throws Exception {
            var field = new JTextField();
            InputUtils.addDecimalFilter(field);

            try (var uiUtilsMock = mockStatic(UIUtils.class)) {
                field.getDocument().insertString(0, "0.5", null);

                uiUtilsMock.verify(UIUtils::beep, never());
                assertThat(field.getText()).isEqualTo("0.5");
            }
        }

        @Test
        void testInsertStringRejectsSecondDecimalPoint() throws Exception {
            // Guards the single-dot constraint of DECIMAL_PATTERN (\d*\.?\d*): a value
            // introducing a second decimal point must be rejected on the prospective text.
            var field = new JTextField("1.");
            InputUtils.addDecimalFilter(field);

            try (var uiUtilsMock = mockStatic(UIUtils.class)) {
                // Prospective result "1.2.3" has two dots → must be rejected.
                field.getDocument().insertString(field.getDocument().getLength(), "2.3", null);

                uiUtilsMock.verify(UIUtils::beep);
                assertThat(field.getText()).isEqualTo("1.");
            }
        }
    }

    // -------------------------------------------------------------------
    // Row 75: DecimalDocumentFilter.replace rejects replacement that would
    //         make the document content non-decimal
    // -------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class DecimalFilterReplace {

        @Test
        void testReplaceRejectsNonDecimalReplacement() throws Exception {
            var field = new JTextField("1.5");
            InputUtils.addDecimalFilter(field);

            try (var uiUtilsMock = mockStatic(UIUtils.class)) {
                // Replace "1.5" with "abc" — prospective result "abc" is not decimal
                var doc = (javax.swing.text.AbstractDocument) field.getDocument();
                doc.replace(0, doc.getLength(), "abc", null);

                uiUtilsMock.verify(UIUtils::beep);
                assertThat(field.getText()).isEqualTo("1.5");
            }
        }

        @Test
        void testReplaceAcceptsDecimalReplacement() throws Exception {
            var field = new JTextField("1.5");
            InputUtils.addDecimalFilter(field);

            try (var uiUtilsMock = mockStatic(UIUtils.class)) {
                var doc = (javax.swing.text.AbstractDocument) field.getDocument();
                doc.replace(0, doc.getLength(), "3.14", null);

                uiUtilsMock.verify(UIUtils::beep, never());
                assertThat(field.getText()).isEqualTo("3.14");
            }
        }
    }

    // -------------------------------------------------------------------
    // Row 77: RegexFormatter.stringToValue throws ParseException when input
    //         does not match the pattern; returns value when it matches
    // -------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class RegexFormatterStringToValue {

        @Test
        void testStringToValueThrowsParseExceptionWhenInputDoesNotMatch() {
            var formatter = new InputUtils.RegexFormatter("\\d+");

            assertThatThrownBy(() -> formatter.stringToValue("abc"))
                .isInstanceOf(ParseException.class);
        }

        @Test
        void testStringToValueReturnsValueWhenInputMatches() throws Exception {
            var formatter = new InputUtils.RegexFormatter("\\d+");

            // RegexFormatter delegates to DefaultFormatter on match, which returns
            // the string itself when no value class is configured
            var result = formatter.stringToValue("42");

            assertThat(result).isEqualTo("42");
        }
    }

    // -------------------------------------------------------------------
    // Row 79: addNumericFilter(component) installs integer-only filter
    // -------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class AddNumericFilterIntegerOnly {

        @Test
        void testAddNumericFilterInstallsIntegerOnlyFilter() throws Exception {
            var field = new JTextField();
            InputUtils.addNumericFilter(field);

            try (var uiUtilsMock = mockStatic(UIUtils.class)) {
                // Digits accepted
                field.getDocument().insertString(0, "5", null);
                uiUtilsMock.verify(UIUtils::beep, never());
                assertThat(field.getText()).isEqualTo("5");

                // Decimal point rejected — integer-only filter
                field.getDocument().insertString(1, ".", null);
                uiUtilsMock.verify(UIUtils::beep);
                assertThat(field.getText()).isEqualTo("5");
            }
        }
    }

    // -------------------------------------------------------------------
    // Row 80: addNumericFilter(component, true) installs decimal-allowing filter
    // -------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class AddNumericFilterDecimal {

        @Test
        void testAddNumericFilterWithDecimalAllowsDecimalPoint() throws Exception {
            var field = new JTextField();
            InputUtils.addNumericFilter(field, true);

            try (var uiUtilsMock = mockStatic(UIUtils.class)) {
                // "3.14" should be accepted by the decimal-allowing filter
                field.getDocument().insertString(0, "3.14", null);
                uiUtilsMock.verify(UIUtils::beep, never());
                assertThat(field.getText()).isEqualTo("3.14");
            }
        }

        @Test
        void testAddNumericFilterWithDecimalRejectsLetters() throws Exception {
            var field = new JTextField();
            InputUtils.addNumericFilter(field, true);

            try (var uiUtilsMock = mockStatic(UIUtils.class)) {
                field.getDocument().insertString(0, "abc", null);
                uiUtilsMock.verify(UIUtils::beep);
                assertThat(field.getText()).isEmpty();
            }
        }
    }

    // -------------------------------------------------------------------
    // Row 81: addInputFilter on JSpinner installs RegexFormatter on the
    //         spinner's text field
    // -------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class AddInputFilterSpinner {

        @Test
        void testAddInputFilterInstallsRegexFormatterOnSpinnerTextField() throws Exception {
            var spinner = new JSpinner(new SpinnerNumberModel(1, 0, 100, 1));
            InputUtils.addInputFilter(spinner, "\\d+");

            var editor = (JSpinner.DefaultEditor) spinner.getEditor();
            var textField = editor.getTextField();
            var factory = (DefaultFormatterFactory) textField.getFormatterFactory();
            var formatter = (InputUtils.RegexFormatter) factory.getDefaultFormatter();

            // Valid input passes through the formatter without throwing.
            // The spinner's number model supplies an Integer value class, so
            // DefaultFormatter converts "42" → Integer(42).
            var result = formatter.stringToValue("42");
            assertThat(result).isEqualTo(42);

            // Invalid input throws ParseException
            assertThatThrownBy(() -> formatter.stringToValue("abc"))
                .isInstanceOf(ParseException.class);
        }
    }
}
