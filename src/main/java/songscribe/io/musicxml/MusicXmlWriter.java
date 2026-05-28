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
package songscribe.io.musicxml;

import java.io.PrintWriter;
import songscribe.dom.KeyType;
import songscribe.dom.Song;
import songscribe.io.XML;

public final class MusicXmlWriter {

    // Provisional value; final value justified in Phase 3.
    private static final int DIVISIONS = 480;

    private MusicXmlWriter() {}

    public static void writeSong(Song song, PrintWriter pw) {
        pw.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");

        XML.setIndent(0);
        XML.writeBeginTag(pw, "score-partwise", "version", "4.0");

        XML.setIndent(2);
        XML.writeBeginTag(pw, "part-list");

        // <score-part> and its child are emitted inline on one line.
        XML.setIndent(4);
        printIndent(pw, 4);
        pw.println("<score-part id=\"P1\"><part-name></part-name></score-part>");

        XML.setIndent(2);
        XML.writeEndTag(pw, "part-list");

        XML.writeBeginTag(pw, "part", "id", "P1");

        XML.setIndent(4);
        XML.writeBeginTag(pw, "measure", "number", "1");

        XML.setIndent(6);
        XML.writeBeginTag(pw, "attributes");

        XML.setIndent(8);
        XML.writeValue(pw, "divisions", Integer.toString(DIVISIONS));

        // <key> with inline child <fifths>
        // Encode as signed fifths: negative for flats (MusicXML convention).
        int fifths = song.getDefaultKeyType() == KeyType.FLATS
            ? -song.getDefaultKeyAccidentalCount()
            : song.getDefaultKeyAccidentalCount();
        printIndent(pw, 8);
        pw.println("<key><fifths>" + fifths + "</fifths></key>");

        // <time print-object="no"> with inline self-closing child <senza-misura/>
        printIndent(pw, 8);
        pw.println("<time print-object=\"no\"><senza-misura/></time>");

        // <clef> with inline children
        printIndent(pw, 8);
        pw.println("<clef><sign>G</sign><line>2</line></clef>");

        XML.setIndent(6);
        XML.writeEndTag(pw, "attributes");

        XML.setIndent(4);
        XML.writeEndTag(pw, "measure");

        XML.setIndent(2);
        XML.writeEndTag(pw, "part");

        XML.setIndent(0);
        XML.writeEndTag(pw, "score-partwise");
    }

    /** Print {@code count} spaces without a trailing newline. */
    private static void printIndent(PrintWriter pw, int count) {
        for (int i = 0; i < count; i++) {
            pw.print(' ');
        }
    }
}
