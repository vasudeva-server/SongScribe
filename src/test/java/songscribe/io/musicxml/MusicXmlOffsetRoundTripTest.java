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

import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.DocumentScale;
import songscribe.dom.Song;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.dom.SongFactory.buildSong;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.X_OFFSET_PX;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.parse;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.roundTrip;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.writeToString;

/**
 * How a note's hand-placed horizontal nudge survives a write and a read, through the
 * ss &rarr; tenths &rarr; ss conversion {@code <relative-x>} carries it through.
 */
class MusicXmlOffsetRoundTripTest extends UnitTest {

    @Test
    void testAnElementsXOffsetSurvivesAWriteAndAReadThroughTenths() throws Exception {
        var xOffsetSs = DocumentScale.pxToSs(X_OFFSET_PX);
        var song = songWithXOffset(xOffsetSs);

        var restored = roundTrip(song).getLine(0).getElement(0);

        assertThat(restored.getUserXOffsetSs()).isEqualTo(xOffsetSs);
    }

    /**
     * A document without the {@code <supports element="note" attribute="relative-x">}
     * entry was written when {@code relative-x} carried the layout position, so the
     * reader must leave the offset at zero rather than take that position for a nudge.
     */
    @Test
    void testRelativeXIsIgnoredWhenTheDocumentDoesNotVouchForIt() throws Exception {
        var song = songWithXOffset(DocumentScale.pxToSs(X_OFFSET_PX));
        var xml = writeToString(song);

        var withoutDeclaration = NOTE_RELATIVE_X_SUPPORTS.matcher(xml).replaceAll("");
        assertThat(withoutDeclaration).isNotEqualTo(xml);

        var restored = parse(withoutDeclaration).getLine(0).getElement(0);

        assertThat(restored.getUserXOffsetSs()).isZero();
    }

    /** The emitted {@code <supports>} entry for note {@code relative-x}, whatever its attribute order. */
    private static final Pattern NOTE_RELATIVE_X_SUPPORTS = Pattern.compile(
        "<" + MusicXmlTags.SUPPORTS + "[^>]*" + MusicXmlTags.ATTR_RELATIVE_X + "[^>]*/>");

    /** A one-line song whose only note carries {@code xOffsetSs} as its hand-placed X nudge. */
    private static Song songWithXOffset(double xOffsetSs) {
        return buildSong(line -> {
            var note = crotchet();
            note.setUserXOffsetSs(xOffsetSs);
            line.addElement(note);
        });
    }
}
