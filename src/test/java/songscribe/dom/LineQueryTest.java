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
    // effectiveDeleteEnd — pure query, must not mutate
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class EffectiveDeleteEnd {

        @Test
        void testExtendsPastATrailingBreathMark() {
            var song = new Song();
            var line = song.getLine(0);
            var note = ElementType.CROTCHET.newInstance();
            var breath = ElementType.BREATH_MARK.newInstance();
            song.withoutMutationTracking(() -> {
                line.addElement(note);
                line.addElement(breath);
            });

            assertThat(line.effectiveDeleteEnd(0))
                .as("a breath mark after end is attached to it, so the range must extend past it")
                .isEqualTo(1);
        }

        @Test
        void testLeavesANonBreathMarkSuccessorAlone() {
            var song = new Song();
            var line = song.getLine(0);
            var noteA = ElementType.CROTCHET.newInstance();
            var noteB = ElementType.CROTCHET.newInstance();
            song.withoutMutationTracking(() -> {
                line.addElement(noteA);
                line.addElement(noteB);
            });

            assertThat(line.effectiveDeleteEnd(0))
                .as("a plain successor is not attached to end, so the range must not grow")
                .isEqualTo(0);
        }

        @Test
        void testHandlesEndAtTheLastElement() {
            var song = new Song();
            var line = song.getLine(0);
            var note = ElementType.CROTCHET.newInstance();
            song.withoutMutationTracking(() -> line.addElement(note));

            assertThat(line.effectiveDeleteEnd(0))
                .as("there is no successor to inspect, so end must come back unchanged")
                .isEqualTo(0);
        }

        @Test
        void testMutatesNothing() {
            var song = new Song();
            var line = song.getLine(0);
            var note = ElementType.CROTCHET.newInstance();
            var breath = ElementType.BREATH_MARK.newInstance();
            song.withoutMutationTracking(() -> {
                line.addElement(note);
                line.addElement(breath);
            });

            var countBefore = line.elementCount();

            line.effectiveDeleteEnd(0);

            assertThat(line.elementCount())
                .as("effectiveDeleteEnd is a pure query and must not add or remove elements")
                .isEqualTo(countBefore);
            assertThat(line.getElement(0)).isSameAs(note);
            assertThat(line.getElement(1)).isSameAs(breath);
        }
    }

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
            song.withoutMutationTracking(() -> line.addSpan(trill));

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

    // -----------------------------------------------------------------------
    // isSamePitchAsFollower — the one definition of the span a glissando may not cover
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class IsSamePitchAsFollower {

        private static final int A_STAFF_POSITION = 2;
        private static final int ANOTHER_STAFF_POSITION = 5;

        /** Builds a line from the given element types, each at the paired staff position. */
        private static Line lineOf(ElementType[] types, int[] staffPositions) {
            var song = new Song();
            var line = song.getLine(0);

            song.withoutMutationTracking(() -> {
                for (var i = 0; i < types.length; i++) {
                    var element = types[i].newInstance();
                    element.setStaffPosition(staffPositions[i]);
                    line.addElement(element);
                }
            });

            return line;
        }

        @Test
        void testTwoNotesAtTheSameStaffPositionMatch() {
            var line = lineOf(
                new ElementType[] {ElementType.CROTCHET, ElementType.CROTCHET},
                new int[] {A_STAFF_POSITION, A_STAFF_POSITION});

            assertThat(line.isSamePitchAsFollower(0)).isTrue();
        }

        @Test
        void testTwoNotesAtDifferentStaffPositionsDoNot() {
            var line = lineOf(
                new ElementType[] {ElementType.CROTCHET, ElementType.CROTCHET},
                new int[] {A_STAFF_POSITION, ANOTHER_STAFF_POSITION});

            assertThat(line.isSamePitchAsFollower(0)).isFalse();
        }

        @Test
        void testSameStaffPositionButDifferentAccidentalDoesNotMatch() {
            // Pitch, not staff position, is the question — an accidental separates the two.
            var line = lineOf(
                new ElementType[] {ElementType.CROTCHET, ElementType.CROTCHET},
                new int[] {A_STAFF_POSITION, A_STAFF_POSITION});
            line.getElement(1).setAccidental(StaffElement.Accidental.SHARP);

            assertThat(line.isSamePitchAsFollower(0))
                .as("the sharp puts the two notes a semitone apart")
                .isFalse();
        }

        @Test
        void testAGraceNoteCountsAsANote() {
            var line = lineOf(
                new ElementType[] {ElementType.GRACE_QUAVER, ElementType.CROTCHET},
                new int[] {A_STAFF_POSITION, A_STAFF_POSITION});

            assertThat(line.isSamePitchAsFollower(0))
                .as("a grace note carries a pitch like any other note")
                .isTrue();
        }

        @Test
        void testANonNoteFollowerNeverMatches() {
            var line = lineOf(
                new ElementType[] {ElementType.CROTCHET, ElementType.BREATH_MARK},
                new int[] {A_STAFF_POSITION, A_STAFF_POSITION});

            assertThat(line.isSamePitchAsFollower(0))
                .as("a breath mark sounds no pitch, so there is nothing to match")
                .isFalse();
        }

        @Test
        void testTheLastElementHasNoFollower() {
            var line = lineOf(
                new ElementType[] {ElementType.CROTCHET, ElementType.CROTCHET},
                new int[] {A_STAFF_POSITION, A_STAFF_POSITION});

            assertThat(line.isSamePitchAsFollower(line.elementCount() - 1)).isFalse();
        }

        @Test
        void testANegativeIndexDoesNotMatch() {
            var line = lineOf(
                new ElementType[] {ElementType.CROTCHET, ElementType.CROTCHET},
                new int[] {A_STAFF_POSITION, A_STAFF_POSITION});

            assertThat(line.isSamePitchAsFollower(-1)).isFalse();
        }
    }
}
