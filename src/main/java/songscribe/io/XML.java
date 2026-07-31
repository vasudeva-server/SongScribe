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
package songscribe.io;

import java.io.PrintWriter;

public final class XML {

    // One nesting level is two spaces; shared by the legacy .mssw writers (which
    // set absolute widths) and any push/pop writer using indent()/dedent().
    private static final int INDENT_STEP = 2;
    private static final Indent indent = new Indent(INDENT_STEP);

    private XML() {}

    /**
     * Sets the indentation to an absolute width in spaces.
     *
     * <p>Used only by the legacy {@code .mssw} writers, which set an absolute
     * indent per element instead of nesting with {@link #indent()} /
     * {@link #dedent()}. The {@code .mssw} format is permanently supported for
     * migrating old files, so this method is not going away.
     */
    public static void setIndent(int newIndent) {
        indent.setIndent(newIndent);
    }

    /** Descends one nesting level. */
    public static void indent() {
        indent.indent();
    }

    /** Ascends one nesting level. */
    public static void dedent() {
        indent.dedent();
    }

    /** Resets the indentation to column zero, for the start of a fresh document. */
    public static void resetIndent() {
        indent.reset();
    }

    public static void printIndent(PrintWriter pw) {
        indent.print(pw);
    }

    public static void writeEmptyTag(PrintWriter pw, String tag) {
        printIndent(pw);
        pw.print('<');
        pw.print(tag);
        pw.println(" />");
    }

    /**
     * Write a self-closing tag with attributes. {@code attrs} is a flat alternating
     * key/value sequence: {@code "key1", "val1", "key2", "val2", ...}.
     * Attribute values are XML-escaped automatically.
     */
    public static void writeEmptyTag(PrintWriter pw, String tag, String... attrs) {
        printIndent(pw);
        pw.print('<');
        pw.print(tag);
        writeAttrs(pw, attrs);
        pw.println(" />");
    }

    public static void writeBeginTag(PrintWriter pw, String tag) {
        printIndent(pw);
        pw.print('<');
        pw.print(tag);
        pw.println('>');
    }

    /**
     * Write an opening tag with attributes. {@code attrs} is a flat alternating
     * key/value sequence: {@code "key1", "val1", "key2", "val2", ...}.
     * Attribute values are XML-escaped automatically.
     */
    public static void writeBeginTag(PrintWriter pw, String tag, String... attrs) {
        printIndent(pw);
        pw.print('<');
        pw.print(tag);
        writeAttrs(pw, attrs);
        pw.println('>');
    }

    /** Appends {@code key="escaped-value"} pairs to the current output line. */
    private static void writeAttrs(PrintWriter pw, String[] attrs) {
        for (var i = 0; i < attrs.length - 1; i += 2) {
            pw.print(' ');
            pw.print(attrs[i]);
            pw.print("=\"");
            pw.print(escapeXML(attrs[i + 1]));
            pw.print('"');
        }
    }

    public static void writeEndTag(PrintWriter pw, String tag) {
        printIndent(pw);
        pw.print("</");
        pw.print(tag);
        pw.println('>');
    }

    public static void writeValue(PrintWriter pw, String tag, String value) {
        printIndent(pw);
        pw.print('<');
        pw.print(tag);
        pw.print('>');
        pw.print(escapeXML(value));
        pw.print("</");
        pw.print(tag);
        pw.println('>');
    }

    /**
     * Write an element with both attributes and text content. {@code attrs} is a
     * flat alternating key/value sequence: {@code "key1", "val1", ...}. Attribute
     * values and the text content are XML-escaped automatically.
     */
    public static void writeValue(PrintWriter pw, String tag, String value, String... attrs) {
        printIndent(pw);
        pw.print('<');
        pw.print(tag);
        writeAttrs(pw, attrs);
        pw.print('>');
        pw.print(escapeXML(value));
        pw.print("</");
        pw.print(tag);
        pw.println('>');
    }

    /**
     * Replace special characters with XML escapes:
     * <pre>
     * &amp; <small>(ampersand)</small> is replaced by &amp;amp;
     * &lt; <small>(less than)</small> is replaced by &amp;lt;
     * &gt; <small>(greater than)</small> is replaced by &amp;gt;
     * &quot; <small>(double quote)</small> is replaced by &amp;quot;
     * </pre>
     *
     * @param string The string to be escaped.
     * @return The escaped string.
     */
    public static String escapeXML(String string) {
        var sb = new StringBuilder((int) (string.length() * 1.5));

        for (int i = 0, len = string.length(); i < len; i++) {
            var c = string.charAt(i);

            sb.append(
                switch (c) {
                    case '&' -> "&amp;";
                    case '<' -> "&lt;";
                    case '>' -> "&gt;";
                    case '"' -> "&quot;";
                    default -> c;
                }
            );
        }

        return sb.toString();
    }
}
