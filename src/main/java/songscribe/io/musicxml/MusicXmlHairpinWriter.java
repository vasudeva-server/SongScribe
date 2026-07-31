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
import java.util.ArrayList;
import java.util.List;

import songscribe.dom.Hairpin;
import songscribe.io.XML;
import songscribe.io.musicxml.MusicXmlSpanIndex.IndexSpanMarkers;

final class MusicXmlHairpinWriter {

    private MusicXmlHairpinWriter() {}

    /**
     * Emits the start and stop wedges bound to the note at this element index.
     * Stop wedges (closing an open hairpin) precede start wedges so a hairpin's
     * stop and the next hairpin's start on the same note keep a natural order.
     */
    static void writeHairpinWedges(PrintWriter pw, IndexSpanMarkers markers) {
        for (var hairpin : markers.hairpinsEndingHere()) {
            writeStopWedge(pw, hairpin);
        }

        for (var hairpin : markers.hairpinsStartingHere()) {
            writeStartWedge(pw, hairpin);
        }
    }

    /**
     * Emits the start wedge for {@code hairpin}: {@code <wedge type="crescendo|
     * diminuendo" number="1">}, carrying {@code x1ShiftSs} as {@code relative-x}
     * and {@code yShiftSs} as {@code relative-y} (ss × 10 = tenths), each only
     * when non-zero.
     */
    private static void writeStartWedge(PrintWriter pw, Hairpin hairpin) {
        var wedgeType = WedgeTypeMapping.wedgeType(hairpin);

        if (wedgeType == null) {
            return;
        }

        var attrs = new ArrayList<String>();
        attrs.add(MusicXmlTags.ATTR_TYPE);
        attrs.add(wedgeType);
        attrs.add(MusicXmlTags.ATTR_NUMBER);
        attrs.add(MusicXmlTags.NUMBER_1);
        addShiftAttr(attrs, MusicXmlTags.ATTR_RELATIVE_X, hairpin.getX1ShiftSs());
        addShiftAttr(attrs, MusicXmlTags.ATTR_RELATIVE_Y, hairpin.getYShiftSs());

        writeWedgeDirection(pw, attrs);
    }

    /**
     * Emits the stop wedge for {@code hairpin}: {@code <wedge type="stop"
     * number="1">}, carrying {@code x2ShiftSs} as {@code relative-x}
     * (ss × 10 = tenths), only when non-zero.
     */
    private static void writeStopWedge(PrintWriter pw, Hairpin hairpin) {
        var attrs = new ArrayList<String>();
        attrs.add(MusicXmlTags.ATTR_TYPE);
        attrs.add(MusicXmlTags.TYPE_STOP);
        attrs.add(MusicXmlTags.ATTR_NUMBER);
        attrs.add(MusicXmlTags.NUMBER_1);
        addShiftAttr(attrs, MusicXmlTags.ATTR_RELATIVE_X, hairpin.getX2ShiftSs());

        writeWedgeDirection(pw, attrs);
    }

    /**
     * Wraps a {@code <wedge>} (built from {@code wedgeAttrs}, a flat alternating
     * key/value list) in its {@code <direction><direction-type>} envelope.
     */
    private static void writeWedgeDirection(PrintWriter pw, List<String> wedgeAttrs) {
        XML.writeBeginTag(pw, MusicXmlTags.DIRECTION);
        XML.indent();

        XML.writeBeginTag(pw, MusicXmlTags.DIRECTION_TYPE);
        XML.indent();

        XML.writeEmptyTag(pw, MusicXmlTags.WEDGE, wedgeAttrs.toArray(new String[0]));

        XML.dedent();
        XML.writeEndTag(pw, MusicXmlTags.DIRECTION_TYPE);

        XML.dedent();
        XML.writeEndTag(pw, MusicXmlTags.DIRECTION);
    }

    /**
     * Appends an optional position-shift attribute to {@code attrs} (a flat
     * alternating key/value list): when {@code shiftSs} is non-zero, adds
     * {@code attrName} and the ss→tenths-formatted value; otherwise does nothing.
     */
    private static void addShiftAttr(List<? super String> attrs, String attrName, double shiftSs) {
        if (shiftSs != 0) {
            attrs.add(attrName);
            attrs.add(MusicXmlUnits.formatSsAsTenths(shiftSs));
        }
    }
}
