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

import module java.desktop;
// Disambiguates from javax.print.attribute.AttributeSet (also in java.desktop)
import javax.swing.text.AttributeSet;

import java.text.ParseException;
import java.util.regex.Pattern;

public final class InputUtils {

    private InputUtils() {}

    public static void addInputFilter(JComponent component, String regex) {
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
            document.setDocumentFilter(new CustomDocumentFilter(regex));
        }
    }

    public static void addNumericFilter(JComponent component) {
        addNumericFilter(component, false);
    }

    public static void addNumericFilter(
        JComponent component,
        boolean allowDecimal
    ) {
        addInputFilter(component, allowDecimal ? "[\\d\\.]+" : "\\d+");
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

    public static final class CustomDocumentFilter extends DocumentFilter {

        private final Pattern pattern;

        private CustomDocumentFilter(String regex) {
            pattern = Pattern.compile(regex);
        }

        @Override
        public void insertString(
            FilterBypass fb,
            int offset,
            String string,
            AttributeSet attr
        ) throws BadLocationException {
            if (string != null) {
                if (string.isEmpty() || pattern.matcher(string).matches()) {
                    super.insertString(fb, offset, string, attr);
                } else {
                    Toolkit.getDefaultToolkit().beep();
                }
            }
        }

        @Override
        public void replace(
            FilterBypass fb,
            int offset,
            int length,
            String text,
            AttributeSet attrs
        ) throws BadLocationException {
            if (text != null) {
                if (text.isEmpty() || pattern.matcher(text).matches()) {
                    super.replace(fb, offset, length, text, attrs);
                } else {
                    Toolkit.getDefaultToolkit().beep();
                }
            }
        }
    }
}
