/*
    SongScribe song notation program
    Copyright (C) Sri Chinmoy Centres International

    This file is part of SongScribe.

    SongScribe is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 3 of the License, or
    (at your option) any later version.

    SongScribe is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package songscribe.io.musicxml;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Key;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.SongFactory.buildSong;
import static songscribe.dom.SongFactory.notesAroundKeyChange;
import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.roundTrip;

/**
 * Reading is the second place the rule that no key change restates the key in effect before it
 * is enforced, and the only one that can reach a document written before the rule existed.
 *
 * <p>A file can hold such a key change with nothing on screen to say so: it cancels no accidentals
 * and reserves no width, so it draws nothing, while still refusing the two insertion slots
 * flanking it and still being written out again on the next save. No edit can be relied on to
 * reach it, which is why the repair belongs to the load rather than to any one edit.
 *
 * <p>The songs here are built in memory and written out, because a fixture file on disk cannot
 * carry a stranded key change past the very load that would be under test.
 */
class MusicXmlStrandedKeyChangeTest extends UnitTest {

    /** The document key, which line 0 establishes. */
    private static final Key DOCUMENT_KEY = Key.NO_ACCIDENTALS;

    /** A key a line establishes for itself, and that a key change on it then restates. */
    private static final Key OWN_KEY = Key.TWO_SHARPS;

    /** A key a mid-line key change really changes to, and that a later key change then restates. */
    private static final Key CHANGED_KEY = Key.THREE_FLATS;

    /** What a line holding a note, a stranded pair and another note is left with. */
    private static final int NOTES_EITHER_SIDE_OF_THE_PAIR = 2;

    private static final int SECOND_LINE = 1;
    private static final int THIRD_LINE = 2;
    private static final int FOURTH_LINE = 3;

    @Test
    void testAKeyChangeRestatingItsOwnLinesKeyIsGoneWithItsBarlineAfterReading() throws Exception {
        var written = buildSong(
            line -> {
                line.setKey(DOCUMENT_KEY);
                line.addElement(crotchet());
            },
            line -> {
                line.setKey(OWN_KEY);
                notesAroundKeyChange(line, OWN_KEY);
            });

        var read = roundTrip(written);
        var repaired = read.getLine(SECOND_LINE);

        assertThat(repaired.lastKeyChangeKey())
            .as("the key change restated the key its own line establishes, so it drew nothing")
            .isNull();
        assertThat(repaired.effectiveElementCount())
            .as("the barline it stood behind went with it, leaving the notes either side")
            .isEqualTo(NOTES_EITHER_SIDE_OF_THE_PAIR);
        assertThat(read.isModified())
            .as("the repair belongs to reading, so it leaves nothing for the notator to save")
            .isFalse();
    }

    @Test
    void testNoLineHoldsAStrandedKeyChangeAfterAReadAndARealChangeSurvivesIt() throws Exception {
        var written = buildSong(
            line -> {
                line.setKey(DOCUMENT_KEY);
                line.addElement(crotchet());
            },
            line -> {
                line.setKey(OWN_KEY);
                notesAroundKeyChange(line, OWN_KEY);
            },
            line -> notesAroundKeyChange(line, CHANGED_KEY),
            line -> notesAroundKeyChange(line, CHANGED_KEY),
            line -> line.addElement(crotchet()));

        var read = roundTrip(written);

        for (var lineIndex = 0; lineIndex < read.lineCount(); lineIndex++) {
            var line = read.getLine(lineIndex);

            assertThat(line.redundantKeyChangeRanges(line.getRunningKey()))
                .as("line %d", lineIndex)
                .isEmpty();
        }

        assertThat(read.getLine(THIRD_LINE).lastKeyChangeKey())
            .as("this one changes the key the line inherited, so it survives untouched")
            .isEqualTo(CHANGED_KEY);
        assertThat(read.getLine(FOURTH_LINE).lastKeyChangeKey())
            .as("this one restates the key the line before it left off in")
            .isNull();
        assertKeyPropagationInvariant(read);
    }
}
