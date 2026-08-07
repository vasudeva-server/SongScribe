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

package songscribe.dom;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;

/**
 * Tests for {@link Song#wouldOrphanLaterTempoChange}, which delegates to
 * {@link TempoResolver#removalWouldOrphanLaterTempoChange}. That query returns true exactly
 * when no element strictly before the given position carries a {@link TempoChangeAttachment}
 * and at least one element strictly after it does.
 */
class TempoOrphanRemovalTest extends UnitTest {

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static StaffElement note() {
        return ElementType.CROTCHET.newInstance();
    }

    private static void attachTempo(StaffElement element) {
        element.addAttachment(new TempoChangeAttachment(element, new Tempo()));
    }

    // -------------------------------------------------------------------------
    // True case
    // -------------------------------------------------------------------------

    @Test
    void testReturnsTrueWhenLaterElementCarriesTempoChange() {
        // First tempo change at line 0 element 0; a second tempo change later in the same
        // line. Removing the first would leave the second with nothing before it.
        var song = new Song();

        var firstTempoNote = note();
        var laterTempoNote = note();

        song.withoutMutationTracking(() -> {
            var line = song.getLine(0);
            line.addElement(firstTempoNote);
            line.addElement(laterTempoNote);
            attachTempo(firstTempoNote);
            attachTempo(laterTempoNote);
        });

        assertThat(song.wouldOrphanLaterTempoChange(firstTempoNote)).isTrue();
    }

    // -------------------------------------------------------------------------
    // False cases
    // -------------------------------------------------------------------------

    @Test
    void testReturnsFalseWhenOnlyTempoChangeInSong() {
        // Nothing later in the song to orphan.
        var song = new Song();
        var onlyTempoNote = note();
        var plainNote = note();

        song.withoutMutationTracking(() -> {
            var line = song.getLine(0);
            line.addElement(plainNote);
            line.addElement(onlyTempoNote);
            attachTempo(onlyTempoNote);
        });

        assertThat(song.wouldOrphanLaterTempoChange(onlyTempoNote)).isFalse();
    }

    @Test
    void testReturnsFalseWhenEarlierTempoChangeStillCoversLaterOne() {
        // earlierTempoNote < testedNote < laterTempoNote. Removing testedNote's tempo change
        // still leaves earlierTempoNote in place to cover laterTempoNote.
        var song = new Song();
        var earlierTempoNote = note();
        var testedNote = note();
        var laterTempoNote = note();

        song.withoutMutationTracking(() -> {
            var line = song.getLine(0);
            line.addElement(earlierTempoNote);
            line.addElement(testedNote);
            line.addElement(laterTempoNote);
            attachTempo(earlierTempoNote);
            attachTempo(testedNote);
            attachTempo(laterTempoNote);
        });

        assertThat(song.wouldOrphanLaterTempoChange(testedNote)).isFalse();
    }

    @Test
    void testReturnsFalseForLastOfSeveralTempoChanges() {
        // Nothing after the last tempo change to orphan.
        var song = new Song();
        var firstTempoNote = note();
        var middleTempoNote = note();
        var lastTempoNote = note();

        song.withoutMutationTracking(() -> {
            var line = song.getLine(0);
            line.addElement(firstTempoNote);
            line.addElement(middleTempoNote);
            line.addElement(lastTempoNote);
            attachTempo(firstTempoNote);
            attachTempo(middleTempoNote);
            attachTempo(lastTempoNote);
        });

        assertThat(song.wouldOrphanLaterTempoChange(lastTempoNote)).isFalse();
    }

    @Test
    void testReturnsFalseForElementWithNoTempoChangeAtAll() {
        var song = new Song();
        var plainNote = note();

        song.withoutMutationTracking(() -> {
            var line = song.getLine(0);
            line.addElement(plainNote);
        });

        assertThat(song.wouldOrphanLaterTempoChange(plainNote)).isFalse();
    }

    @Test
    void testReturnsFalseForElementWithNullParentLine() {
        // Never added to any line, so getParentLine() is null.
        var detachedNote = note();

        var song = new Song();

        assertThat(song.wouldOrphanLaterTempoChange(detachedNote)).isFalse();
    }

    @Test
    void testReturnsFalseForElementNotAmongItsLineElements() {
        // parentLine is set directly, bypassing Line.addElement, so getElementIndex() reports
        // -1: the element is "in" the line by parent pointer but not among its elements.
        var song = new Song();
        var strandedNote = note();

        song.withoutMutationTracking(() -> {
            var line = song.getLine(0);
            strandedNote.setParentLine(line);
        });

        assertThat(song.wouldOrphanLaterTempoChange(strandedNote)).isFalse();
    }

    // -------------------------------------------------------------------------
    // Self-exclusion
    // -------------------------------------------------------------------------

    @Test
    void testExcludesElementsOwnTempoChangeFromTheForwardScan() {
        // testedNote carries the only tempo change in the song, with several plain elements
        // after it. Its own attachment must not be counted as "one after" itself — the
        // off-by-one the forward scan's passedPosition flag exists to prevent.
        var song = new Song();
        var testedNote = note();
        var laterPlainNoteOne = note();
        var laterPlainNoteTwo = note();

        song.withoutMutationTracking(() -> {
            var line = song.getLine(0);
            line.addElement(testedNote);
            line.addElement(laterPlainNoteOne);
            line.addElement(laterPlainNoteTwo);
            attachTempo(testedNote);
        });

        assertThat(song.wouldOrphanLaterTempoChange(testedNote)).isFalse();
    }

    // -------------------------------------------------------------------------
    // Multi-line cases
    // -------------------------------------------------------------------------

    @Test
    void testReturnsFalseWhenEarlierTempoChangeIsOnAnEarlierLine() {
        // earlierTempoNote is on line 0; testedNote is on line 1. The earlier line's tempo
        // change still covers it.
        var song = new Song();
        var earlierTempoNote = note();
        var testedNote = note();

        song.withoutMutationTracking(() -> {
            var line0 = song.getLine(0);
            line0.addElement(earlierTempoNote);
            attachTempo(earlierTempoNote);

            var line1 = new Line(song);
            song.addLine(line1);
            line1.addElement(testedNote);
        });

        assertThat(song.wouldOrphanLaterTempoChange(testedNote)).isFalse();
    }

    @Test
    void testReturnsTrueWhenLaterTempoChangeIsOnALaterLine() {
        // testedNote is on line 0 with no earlier tempo change; laterTempoNote is on line 2.
        var song = new Song();
        var testedNote = note();
        var laterTempoNote = note();

        song.withoutMutationTracking(() -> {
            var line0 = song.getLine(0);
            line0.addElement(testedNote);
            attachTempo(testedNote);

            var line1 = new Line(song);
            song.addLine(line1);

            var line2 = new Line(song);
            song.addLine(line2);
            line2.addElement(laterTempoNote);
            attachTempo(laterTempoNote);
        });

        assertThat(song.wouldOrphanLaterTempoChange(testedNote)).isTrue();
    }
}
