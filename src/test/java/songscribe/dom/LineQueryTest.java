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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;

class LineQueryTest extends UnitTest {

    // -----------------------------------------------------------------------
    // getFirstBeatChange
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class GetFirstBeatChange {

        @Test
        void testGetFirstBeatChangeReturnsIndexOfFirstBeatChangeAttachment() {
            // A two-element line: only the second element (index 1) carries a BeatChange.
            // getFirstBeatChange() must return 1.
            var song = new Song();
            var line = song.getLine(0);
            var e0 = new StaffElement(ElementType.CROTCHET);
            var e1 = new StaffElement(ElementType.CROTCHET);
            song.withoutMutationTracking(() -> {
                line.addElement(e0);
                line.addElement(e1);
            });

            var beatChange = new BeatChange(Duration.QUAVER, Duration.QUAVER);
            e1.addAttachment(new BeatChangeAttachment(beatChange));

            var expected = line.getElementIndex(e1);
            assertThat(line.getFirstBeatChange())
                .as("getFirstBeatChange should return the index of the first BeatChangeAttachment element")
                .isEqualTo(expected);
        }

        @Test
        void testGetFirstBeatChangeReturnsMinusOneWhenNonePresent() {
            // No elements have a BeatChangeAttachment — must return -1.
            var song = new Song();
            var line = song.getLine(0);
            var e0 = new StaffElement(ElementType.CROTCHET);
            song.withoutMutationTracking(() -> line.addElement(e0));

            assertThat(line.getFirstBeatChange())
                .as("getFirstBeatChange should return -1 when no BeatChangeAttachment exists")
                .isEqualTo(-1);
        }
    }

    // -----------------------------------------------------------------------
    // getFirstTempoChange
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class GetFirstTempoChange {

        @Test
        void testGetFirstTempoChangeReturnsZeroOnLineZero() {
            // Line 0 with at least one element must always return 0, regardless of
            // whether any element carries a TempoChangeAttachment.
            var song = new Song();
            var line0 = song.getLine(0);
            var e0 = new StaffElement(ElementType.CROTCHET);
            song.withoutMutationTracking(() -> line0.addElement(e0));

            assertThat(line0.getFirstTempoChange())
                .as("getFirstTempoChange on line 0 must always return 0")
                .isEqualTo(0);
        }

        @Test
        void testGetFirstTempoChangeReturnsAttachmentIndexOnLaterLine() {
            // On a non-first line, getFirstTempoChange must scan for a TempoChangeAttachment
            // and return its index.  Add two elements; attach tempo to index 1.
            var song = new Song();
            // Add a second line so line 0 is not the last line, then add line 2 so
            // we have a genuine "later" line to test.
            var line1 = new Line(song);
            song.addLine(1, line1);

            var e0 = new StaffElement(ElementType.CROTCHET);
            var e1 = new StaffElement(ElementType.CROTCHET);
            song.withoutMutationTracking(() -> {
                line1.addElement(e0);
                line1.addElement(e1);
            });

            var tempo = song.getTempo();
            if (tempo == null) {
                throw new AssertionError("fresh Song should always have a non-null tempo");
            }
            e1.addAttachment(new TempoChangeAttachment(tempo));

            // e0 is at index 0, e1 is at index 1 on line1
            assertThat(line1.getFirstTempoChange())
                .as("getFirstTempoChange on a non-first line must return the index of the TempoChangeAttachment")
                .isEqualTo(1);
        }

        @Test
        void testGetFirstTempoChangeReturnsMinusOneWhenNoAttachmentOnLaterLine() {
            // On a non-first line with no TempoChangeAttachments, must return -1.
            var song = new Song();
            var line1 = new Line(song);
            song.addLine(1, line1);

            var e0 = new StaffElement(ElementType.CROTCHET);
            song.withoutMutationTracking(() -> line1.addElement(e0));

            assertThat(line1.getFirstTempoChange())
                .as("getFirstTempoChange on a non-first line with no tempo attachment must return -1")
                .isEqualTo(-1);
        }
    }

    // -----------------------------------------------------------------------
    // getFirstTrill
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class GetFirstTrill {

        @Test
        void testGetFirstTrillReturnsAnchorIndexOfFirstTrill() {
            // Add two elements; attach a trill to the second (index 1).
            // getFirstTrill() must return 1.
            var song = new Song();
            var line = song.getLine(0);
            var e0 = new StaffElement(ElementType.CROTCHET);
            var e1 = new StaffElement(ElementType.CROTCHET);
            song.withoutMutationTracking(() -> {
                line.addElement(e0);
                line.addElement(e1);
            });

            var trill = new Trill(e1);
            song.withoutMutationTracking(() -> line.addRangeElement(trill));

            assertThat(line.getFirstTrill())
                .as("getFirstTrill should return the anchor index of the earliest trill")
                .isEqualTo(line.getElementIndex(e1));
        }

        @Test
        void testGetFirstTrillReturnsMinusOneWhenNoTrillPresent() {
            // No trills on the line — must return -1.
            var song = new Song();
            var line = song.getLine(0);
            var e0 = new StaffElement(ElementType.CROTCHET);
            song.withoutMutationTracking(() -> line.addElement(e0));

            assertThat(line.getFirstTrill())
                .as("getFirstTrill should return -1 when no trills are present")
                .isEqualTo(-1);
        }
    }

    // -----------------------------------------------------------------------
    // firstPitchedElement
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class FirstPitchedElement {

        @Test
        void testSkipsLeadingGraceNoteAndReturnsHostNote() {
            // A grace note is an ornament, not the tempo anchor: firstPitchedElement
            // must skip it and return the following pitched host note.
            var song = new Song();
            var line = song.getLine(0);
            var graceNote = new StaffElement(ElementType.GRACE_QUAVER);
            var hostNote = new StaffElement(ElementType.CROTCHET);
            song.withoutMutationTracking(() -> {
                line.addElement(graceNote);
                line.addElement(hostNote);
            });

            assertThat(line.firstPitchedElement())
                .as("firstPitchedElement must skip a leading grace note and return the host note")
                .isSameAs(hostNote);
        }

        @Test
        void testSkipsLeadingRestAndReturnsPitchedNote() {
            var song = new Song();
            var line = song.getLine(0);
            var rest = new StaffElement(ElementType.SEMIBREVE_REST);
            var note = new StaffElement(ElementType.CROTCHET);
            song.withoutMutationTracking(() -> {
                line.addElement(rest);
                line.addElement(note);
            });

            assertThat(line.firstPitchedElement())
                .as("firstPitchedElement must skip a leading rest and return the pitched note")
                .isSameAs(note);
        }

        @Test
        void testReturnsNullWhenNoPitchedNotePresent() {
            // A line with only a grace note has no pitched note to anchor the tempo on.
            var song = new Song();
            var line = song.getLine(0);
            song.withoutMutationTracking(() -> line.addElement(new StaffElement(ElementType.GRACE_QUAVER)));

            assertThat(line.firstPitchedElement())
                .as("firstPitchedElement must return null when no pitched note is present")
                .isNull();
        }
    }

    // -----------------------------------------------------------------------
    // isAnnotation
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class IsAnnotation {

        @Test
        void testIsAnnotationReturnsTrueWhenElementHasAnnotationAttachment() {
            // A line where one element carries an AnnotationAttachment must return true.
            var song = new Song();
            var line = song.getLine(0);
            var e0 = new StaffElement(ElementType.CROTCHET);
            song.withoutMutationTracking(() -> line.addElement(e0));

            e0.addAttachment(new AnnotationAttachment("dolce"));

            assertThat(line.isAnnotation())
                .as("isAnnotation should return true when at least one element has an AnnotationAttachment")
                .isTrue();
        }

        @Test
        void testIsAnnotationReturnsFalseWhenNoElementHasAnnotationAttachment() {
            // No element has an AnnotationAttachment — must return false.
            var song = new Song();
            var line = song.getLine(0);
            var e0 = new StaffElement(ElementType.CROTCHET);
            song.withoutMutationTracking(() -> line.addElement(e0));

            assertThat(line.isAnnotation())
                .as("isAnnotation should return false when no element has an AnnotationAttachment")
                .isFalse();
        }
    }
}
