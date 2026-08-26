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
import javax.swing.text.JTextComponent;

import org.jspecify.annotations.Nullable;

import songscribe.util.StringUtils;
import songscribe.util.UIUtils;

// Disambiguates from javax.print.attribute.AttributeSet (also in java.desktop)

public final class InputUtils {

    /** Sentinel passed as {@code maxChars} to impose no length limit. */
    public static final int NO_MAX_CHARS = Integer.MAX_VALUE;

    /** Which shape of number a numeric filter admits. */
    public enum NumericFormat {
        /** Digits only. */
        INTEGER("\\d+"),
        /** Digits and the decimal point. */
        DECIMAL("[\\d\\.]+");

        private final String regex;

        NumericFormat(String regex) {
            this.regex = regex;
        }

        String regex() {
            return regex;
        }
    }

    private InputUtils() {}

    public static void addInputFilter(JComponent component, String regex) {
        addInputFilter(component, regex, NO_MAX_CHARS);
    }

    public static void addInputFilter(
        JComponent component,
        String regex,
        int maxChars
    ) {
        if (component instanceof JSpinner spinner) {
            var editor = spinner.getEditor();
            var textField = ((JSpinner.DefaultEditor) editor).getTextField();
            var formatter = new RegexFormatter(regex);
            textField.setFormatterFactory(
                new DefaultFormatterFactory(formatter)
            );
            return;
        }

        var document = documentOf(component);

        if (document != null) {
            document.setDocumentFilter(new CustomDocumentFilter(regex, maxChars));
        }
    }

    public static void addNumericFilter(JComponent component) {
        addNumericFilter(component, NumericFormat.INTEGER);
    }

    public static void addNumericFilter(
        JComponent component,
        NumericFormat format
    ) {
        addNumericFilter(component, format, NO_MAX_CHARS);
    }

    public static void addNumericFilter(
        JComponent component,
        NumericFormat format,
        int maxChars
    ) {
        addInputFilter(component, format.regex(), maxChars);
    }

    public static void addDecimalFilter(JTextField field) {
        addDecimalFilter(field, NO_MAX_CHARS);
    }

    public static void addDecimalFilter(JTextField field, int maxChars) {
        var document = (AbstractDocument) field.getDocument();
        document.setDocumentFilter(new DecimalDocumentFilter(maxChars));
    }

    /**
     * Holds {@code field} to {@code maxLines} lines, refusing a break the user enters past the
     * limit and folding a surplus break within pasted text into a space; see
     * {@link MaxLinesDocumentFilter}.
     *
     * @param field    the field to filter
     * @param maxLines the number of lines the field may hold; at least 1
     */
    public static void addMaxLinesFilter(JTextComponent field, int maxLines) {
        ((AbstractDocument) field.getDocument()).setDocumentFilter(new MaxLinesDocumentFilter(maxLines));
    }

    /**
     * @return {@code component}'s document, or {@code null} when it holds no text of its own, as a
     *     {@link JSpinner} does not
     */
    private static @Nullable AbstractDocument documentOf(JComponent component) {
        if (component instanceof JTextComponent textComponent) {
            return (AbstractDocument) textComponent.getDocument();
        }

        return null;
    }

    /**
     * An edit a filter has been asked to judge: the document's {@code [offset, offset + length)}
     * giving way to {@code text}.
     *
     * @param offset where the replaced stretch starts
     * @param length how many characters it covers; zero for a plain insertion
     * @param text   what goes in their place
     * @param attrs  the attributes to insert it under, or {@code null} to insert unattributed,
     *               which is what a programmatic {@code setText} passes
     */
    public record ProposedEdit(int offset, int length, String text, @Nullable AttributeSet attrs) {

        /**
         * @return {@code doc}'s text with this edit applied — what the document would hold were
         *     the edit let through
         */
        String prospectiveTextIn(Document doc) throws BadLocationException {
            var current = doc.getText(0, doc.getLength());
            return current.substring(0, offset) + text + current.substring(offset + length);
        }

        /** @return whether {@code doc} is still within {@code maxChars} once this edit lands */
        boolean fitsIn(Document doc, int maxChars) {
            return doc.getLength() - length + text.length() <= maxChars;
        }

        /** @return this edit with {@code replacement} going in instead of {@link #text()} */
        ProposedEdit withText(String replacement) {
            return new ProposedEdit(offset, length, replacement, attrs);
        }
    }

    /**
     * Base for this class's document filters. Each subclass says what to do with a proposed
     * edit; the two overrides that reach it, and the input-method case neither of them judges,
     * are the same for all of them and live here.
     */
    public abstract static class EditFilter extends DocumentFilter {

        @Override
        public void insertString(
            FilterBypass fb,
            int offset,
            @Nullable String string,
            AttributeSet attr
        ) throws BadLocationException {
            // An insertion is a replacement of nothing, so one path serves both.
            replace(fb, offset, 0, string, attr);
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
            // about to compose over. The removal must still go through, and no filter judges a
            // removal — taking text out cannot break a rule about what may be in.
            if (text == null) {
                super.replace(fb, offset, length, null, attrs);
                return;
            }

            applyEdit(fb, new ProposedEdit(offset, length, text, attrs));
        }

        /**
         * Carries {@code edit} out, alters it, or refuses it — beeping when it does either of
         * the last two, which is the signal every filter here gives.
         */
        protected abstract void applyEdit(FilterBypass fb, ProposedEdit edit) throws BadLocationException;

        /** Lets {@code edit} through unaltered. */
        protected final void accept(FilterBypass fb, ProposedEdit edit) throws BadLocationException {
            super.replace(fb, edit.offset(), edit.length(), edit.text(), edit.attrs());
        }
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

    public static final class DecimalDocumentFilter extends EditFilter {

        private static final Pattern DECIMAL_PATTERN = Pattern.compile("\\d*\\.?\\d*");

        private final int maxChars;

        private DecimalDocumentFilter(int maxChars) {
            this.maxChars = maxChars;
        }

        @Override
        protected void applyEdit(FilterBypass fb, ProposedEdit edit) throws BadLocationException {
            var doc = fb.getDocument();

            if (edit.fitsIn(doc, maxChars)
                && DECIMAL_PATTERN.matcher(edit.prospectiveTextIn(doc)).matches()) {
                accept(fb, edit);
            } else {
                UIUtils.beep();
            }
        }
    }

    public static final class CustomDocumentFilter extends EditFilter {

        private final Pattern pattern;
        private final int maxChars;

        private CustomDocumentFilter(String regex, int maxChars) {
            pattern = Pattern.compile(regex);
            this.maxChars = maxChars;
        }

        @Override
        protected void applyEdit(FilterBypass fb, ProposedEdit edit) throws BadLocationException {
            var text = edit.text();

            if ((text.isEmpty() || pattern.matcher(text).matches())
                && edit.fitsIn(fb.getDocument(), maxChars)) {
                accept(fb, edit);
            } else {
                UIUtils.beep();
            }
        }
    }

    /**
     * A filter that holds its document to a fixed number of lines.
     *
     * <p>An edit that would add a line past the limit is handled by what it carries. An insertion
     * of nothing but line breaks — pressing Enter — is refused outright, since it carries no text
     * a fold could preserve and a space in its place is a character nobody asked for. An insertion
     * carrying text goes in with the surplus breaks folded away, per
     * {@link StringUtils#foldSurplusLineBreaks(String, int)}, because refusing it would lose that
     * text. Either way the filter beeps to say the break was not taken — the same signal the
     * sibling filters give when they alter or refuse an edit.
     *
     * <p>Line endings are normalized on the way in, so the document only ever holds line feeds
     * and a beep means a break was dropped rather than merely rewritten.
     *
     * <p>A beep is all a filter may do. {@link AbstractDocument} holds its write lock across the
     * call, and a modal dialog would pump the event queue inside that lock; an alert belongs to an
     * {@link javax.swing.InputVerifier}, which runs outside the write.
     */
    public static final class MaxLinesDocumentFilter extends EditFilter {

        private final int maxLines;

        private MaxLinesDocumentFilter(int maxLines) {
            this.maxLines = maxLines;
        }

        @Override
        protected void applyEdit(FilterBypass fb, ProposedEdit edit) throws BadLocationException {
            var doc = fb.getDocument();

            // Fold the line endings of what is going in, not of the document, which holds none
            // because everything written into it comes through here. The comparison below then
            // answers "was a break dropped?" rather than "did anything change at all?" — so a
            // title pasted from a platform that ends its lines differently lands without a beep
            // claiming a break was refused.
            var normalized = edit.withText(StringUtils.normalizeLineEndings(edit.text()));
            var prospective = normalized.prospectiveTextIn(doc);
            var folded = StringUtils.foldSurplusLineBreaks(prospective, maxLines);

            if (folded.equals(prospective)) {
                accept(fb, normalized);
                return;
            }

            // Nothing to preserve, so nothing goes in: the fold exists to keep text a refusal
            // would lose, and an insertion of only breaks carries none.
            if (isOnlyLineBreaks(normalized.text())) {
                UIUtils.beep();
                return;
            }

            // Let the edit through where the user made it, so the caret ends up after what they
            // typed or pasted, and take the surplus break out as a second, separate edit. Doing
            // both at once means one replacement spanning to the end of the document, which
            // leaves the caret at the end rather than in the text.
            accept(fb, normalized);
            replaceDifference(fb, prospective, folded, normalized.attrs());
            UIUtils.beep();
        }

        /**
         * Rewrites the one stretch in which {@code folded} differs from {@code current}, which is
         * what the document holds.
         *
         * <p>The stretch is found by comparing rather than assumed to sit at the insertion: the
         * surplus break is not always one of the inserted characters, since typing a break ahead
         * of an existing one makes the existing one surplus.
         */
        private static void replaceDifference(
            FilterBypass fb,
            String current,
            String folded,
            @Nullable AttributeSet attrs
        ) throws BadLocationException {
            var prefix = 0;

            while (prefix < current.length()
                && prefix < folded.length()
                && current.charAt(prefix) == folded.charAt(prefix)) {
                prefix++;
            }

            var suffix = 0;

            while (suffix < current.length() - prefix
                && suffix < folded.length() - prefix
                && current.charAt(current.length() - 1 - suffix) == folded.charAt(folded.length() - 1 - suffix)) {
                suffix++;
            }

            fb.replace(
                prefix,
                current.length() - prefix - suffix,
                folded.substring(prefix, folded.length() - suffix),
                attrs
            );
        }

        /** @return whether {@code text} holds line breaks and nothing else */
        private static boolean isOnlyLineBreaks(String text) {
            return (
                !text.isEmpty() &&
                text.chars().allMatch(character -> character == '\n' || character == '\r')
            );
        }
    }
}
