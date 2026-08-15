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

import java.text.ParseException;
import java.util.regex.Pattern;
import javax.swing.JComponent;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultFormatter;
import javax.swing.text.DefaultFormatterFactory;
import javax.swing.text.Document;
import javax.swing.text.DocumentFilter;

import org.jspecify.annotations.Nullable;

import songscribe.util.UIUtils;

// Disambiguates from javax.print.attribute.AttributeSet (also in java.desktop)

public final class InputUtils {

    /** Sentinel passed as {@code maxChars} to impose no length limit. */
    public static final int NO_MAX_CHARS = Integer.MAX_VALUE;

    private InputUtils() {}

    public static void addInputFilter(JComponent component, String regex) {
        addInputFilter(component, regex, NO_MAX_CHARS);
    }

    public static void addInputFilter(
        JComponent component,
        String regex,
        int maxChars
    ) {
        AbstractDocument document = null;

        if (component instanceof JTextField textField) {
            document = (AbstractDocument) textField.getDocument();
        } else if (component instanceof JSpinner spinner) {
            var editor = spinner.getEditor();
            var textField = ((JSpinner.DefaultEditor) editor).getTextField();
            var formatter = new RegexFormatter(regex);
            textField.setFormatterFactory(
                new DefaultFormatterFactory(formatter)
            );
        }

        if (document != null) {
            document.setDocumentFilter(new CustomDocumentFilter(regex, maxChars));
        }
    }

    public static void addNumericFilter(JComponent component) {
        addNumericFilter(component, false);
    }

    public static void addNumericFilter(
        JComponent component,
        boolean allowDecimal
    ) {
        addNumericFilter(component, allowDecimal, NO_MAX_CHARS);
    }

    public static void addNumericFilter(
        JComponent component,
        boolean allowDecimal,
        int maxChars
    ) {
        addInputFilter(component, allowDecimal ? "[\\d\\.]+" : "\\d+", maxChars);
    }

    public static void addDecimalFilter(JTextField field) {
        addDecimalFilter(field, NO_MAX_CHARS);
    }

    public static void addDecimalFilter(JTextField field, int maxChars) {
        var document = (AbstractDocument) field.getDocument();
        document.setDocumentFilter(new DecimalDocumentFilter(maxChars));
    }

    public static class RegexFormatter extends DefaultFormatter {

        private final Pattern pattern;

        public RegexFormatter(String regex) {
            pattern = Pattern.compile(regex);
            setAllowsInvalid(false);
        }

        @Override
        public Object stringToValue(String string) throws ParseException {
            if (pattern.matcher(string).matches()) {
                return super.stringToValue(string);
            }

            throw new ParseException("Input does not match the pattern", 0);
        }
    }

    public static final class DecimalDocumentFilter extends DocumentFilter {

        private static final Pattern DECIMAL_PATTERN = Pattern.compile("\\d*\\.?\\d*");

        private final int maxChars;

        private DecimalDocumentFilter(int maxChars) {
            this.maxChars = maxChars;
        }

        @Override
        public void insertString(
            FilterBypass fb,
            int offset,
            @Nullable String string,
            AttributeSet attr
        ) throws BadLocationException {
            if (string == null) {
                return;
            }

            if (isProspectiveTextValid(fb.getDocument(), offset, 0, string)) {
                super.insertString(fb, offset, string, attr);
            } else {
                UIUtils.beep();
            }
        }

        @Override
        public void replace(
            FilterBypass fb,
            int offset,
            int length,
            @Nullable String text,
            AttributeSet attrs
        ) throws BadLocationException {
            // A null text is not a rejected edit: JTextComponent#replaceInputMethodText passes
            // it to mean "no text to insert," e.g. while clearing the selection a dead key is
            // about to compose over. The removal must still go through, so this skips the
            // validity check, which only judges text to insert.
            if (text == null) {
                super.replace(fb, offset, length, null, attrs);
                return;
            }

            if (isProspectiveTextValid(fb.getDocument(), offset, length, text)) {
                super.replace(fb, offset, length, text, attrs);
            } else {
                UIUtils.beep();
            }
        }

        private boolean isProspectiveTextValid(
            Document doc,
            int offset,
            int length,
            String text
        ) throws BadLocationException {
            var docLength = doc.getLength();

            if (docLength - length + text.length() > maxChars) {
                return false;
            }

            var current = doc.getText(0, docLength);
            var prospective = current.substring(0, offset) + text + current.substring(offset + length);
            return DECIMAL_PATTERN.matcher(prospective).matches();
        }
    }

    public static final class CustomDocumentFilter extends DocumentFilter {

        private final Pattern pattern;
        private final int maxChars;

        private CustomDocumentFilter(String regex, int maxChars) {
            pattern = Pattern.compile(regex);
            this.maxChars = maxChars;
        }

        @Override
        public void insertString(
            FilterBypass fb,
            int offset,
            @Nullable String string,
            AttributeSet attr
        ) throws BadLocationException {
            if (string == null) {
                return;
            }

            if (isProspectiveTextValid(fb.getDocument(), 0, string)) {
                super.insertString(fb, offset, string, attr);
            } else {
                UIUtils.beep();
            }
        }

        @Override
        public void replace(
            FilterBypass fb,
            int offset,
            int length,
            @Nullable String text,
            AttributeSet attrs
        ) throws BadLocationException {
            // A null text is not a rejected edit: JTextComponent#replaceInputMethodText passes
            // it to mean "no text to insert," e.g. while clearing the selection a dead key is
            // about to compose over. The removal must still go through, so this skips the
            // validity check, which only judges text to insert.
            if (text == null) {
                super.replace(fb, offset, length, null, attrs);
                return;
            }

            if (isProspectiveTextValid(fb.getDocument(), length, text)) {
                super.replace(fb, offset, length, text, attrs);
            } else {
                UIUtils.beep();
            }
        }

        private boolean isProspectiveTextValid(Document doc, int length, String text) {
            //noinspection SimplifiableIfStatement
            if (!text.isEmpty() && !pattern.matcher(text).matches()) {
                return false;
            }

            return doc.getLength() - length + text.length() <= maxChars;
        }
    }
}
