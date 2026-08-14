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
import static songscribe.dom.StaffElementFactory.breathMark;
import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.dom.StaffElementFactory.graceQuaver;
import static songscribe.dom.StaffElementFactory.repeatLeft;
import static songscribe.dom.StaffElementFactory.repeatLeftRight;
import static songscribe.dom.StaffElementFactory.singleBarline;

import java.util.ArrayList;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import songscribe.UnitTest;

class LineQueryTest extends UnitTest {

    /** Line 0 of a fresh song, holding {@code elements} in the order given. */
    private static Line lineOf(StaffElement... elements) {
        var song = new Song();
        var line = song.getLine(0);

        song.withoutMutationTracking(() -> {
            for (var element : elements) {
                line.addElement(element);
            }
        });

        return line;
    }

    /** A key signature element, in whatever key — the pairing rules never read the key. */
    private static KeySignatureElement keySignature() {
        return new KeySignatureElement(Key.DEFAULT);
    }

    // -----------------------------------------------------------------------
    // effectiveEnd — pure query, must not mutate
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class EffectiveEnd {

        @Test
        void testExtendsPastATrailingBreathMark() {
            var line = lineOf(crotchet(), breathMark());

            assertThat(line.effectiveEnd(0))
                .as("a breath mark after end is attached to it, so the range must extend past it")
                .isEqualTo(1);
        }

        @Test
        void testLeavesANonBreathMarkSuccessorAlone() {
            var line = lineOf(crotchet(), crotchet());

            assertThat(line.effectiveEnd(0))
                .as("a plain successor is not attached to end, so the range must not grow")
                .isEqualTo(0);
        }

        @Test
        void testHandlesEndAtTheLastElement() {
            var line = lineOf(crotchet());

            assertThat(line.effectiveEnd(0))
                .as("there is no successor to inspect, so end must come back unchanged")
                .isEqualTo(0);
        }

        @Test
        void testExtendsPastAKeySignatureBehindTheBarlineAtEnd() {
            var line = lineOf(crotchet(), singleBarline(), keySignature(), crotchet());

            assertThat(line.effectiveEnd(1))
                .as("a key signature cannot outlive the barline it sits behind")
                .isEqualTo(2);
        }

        @Test
        void testExtendsPastAKeySignatureBehindTheRepeatAtEnd() {
            var line = lineOf(crotchet(), repeatLeft(), keySignature(), crotchet());

            assertThat(line.effectiveEnd(1))
                .as("a repeat hosts a key signature exactly as a barline does")
                .isEqualTo(2);
        }

        @Test
        void testLeavesABarlineWithNoKeySignatureAfterItAlone() {
            var line = lineOf(crotchet(), singleBarline(), crotchet());

            assertThat(line.effectiveEnd(1))
                .as("a barline with no key signature behind it is paired with nothing")
                .isEqualTo(1);
        }

        @Test
        void testMutatesNothing() {
            var note = crotchet();
            var breath = breathMark();
            var line = lineOf(note, breath);

            var countBefore = line.elementCount();

            line.effectiveEnd(0);

            assertThat(line.elementCount())
                .as("effectiveEnd is a pure query and must not add or remove elements")
                .isEqualTo(countBefore);
            assertThat(line.getElement(0)).isSameAs(note);
            assertThat(line.getElement(1)).isSameAs(breath);
        }
    }

    // -----------------------------------------------------------------------
    // effectiveBegin — pure query, must not mutate
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class EffectiveBegin {

        @Test
        void testExtendsBackOverAPairedGraceNote() {
            var grace = graceQuaver();
            grace.setGlissando();
            var line = lineOf(grace, crotchet());

            assertThat(line.effectiveBegin(1))
                .as("a paired grace note cannot outlive its host, so the range must reach back over it")
                .isEqualTo(0);
        }

        @Test
        void testExtendsBackOverTheBarlineAKeySignatureSitsBehind() {
            var line = lineOf(crotchet(), singleBarline(), keySignature(), crotchet());

            assertThat(line.effectiveBegin(2))
                .as("the pair goes whole, so deleting the key signature reaches back over its barline")
                .isEqualTo(1);
        }

        @Test
        void testExtendsBackOverTheRepeatAKeySignatureSitsBehind() {
            var line = lineOf(crotchet(), repeatLeftRight(), keySignature(), crotchet());

            assertThat(line.effectiveBegin(2))
                .as("a repeat hosts a key signature exactly as a barline does")
                .isEqualTo(1);
        }

        @Test
        void testLeavesAKeySignatureWithNoBarlineBeforeItAlone() {
            var line = lineOf(crotchet(), keySignature(), crotchet());

            assertThat(line.effectiveBegin(1))
                .as("nothing pairs backward from a key signature that sits behind no barline")
                .isEqualTo(1);
        }

        @Test
        void testLeavesAnUnpairedBeginAlone() {
            var line = lineOf(crotchet(), crotchet());

            assertThat(line.effectiveBegin(1))
                .as("a plain predecessor is not paired with begin, so the range must not grow")
                .isEqualTo(1);
        }

        @Test
        void testHandlesBeginAtTheFirstElement() {
            var line = lineOf(crotchet(), crotchet());

            assertThat(line.effectiveBegin(0))
                .as("there is no predecessor to inspect, so begin must come back unchanged")
                .isEqualTo(0);
        }

        @Test
        void testMutatesNothing() {
            var barline = singleBarline();
            var key = keySignature();
            var line = lineOf(crotchet(), barline, key, crotchet());

            var countBefore = line.elementCount();

            line.effectiveBegin(2);

            assertThat(line.elementCount())
                .as("effectiveBegin is a pure query and must not add or remove elements")
                .isEqualTo(countBefore);
            assertThat(line.getElement(1)).isSameAs(barline);
            assertThat(line.getElement(2)).isSameAs(key);
        }
    }

    // -----------------------------------------------------------------------
    // effectiveRange — both directions at once, and what a deletion cannot leave behind
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class EffectiveRangeQuery {

        @Test
        void testARangeCoveringOnlyTheKeySignatureWidensBackward() {
            var line = lineOf(singleBarline(), keySignature(), crotchet());

            assertThat(line.effectiveRange(1, 1))
                .as("selecting only the key signature must still carry its barline")
                .isEqualTo(new Line.EffectiveRange(0, 1));
        }

        @Test
        void testAKeySignatureLastOnTheLineWidensBackwardOnly() {
            var line = lineOf(crotchet(), singleBarline(), keySignature());

            assertThat(line.effectiveRange(2, 2))
                .as("there is nothing after the key signature, so only the backward pair applies")
                .isEqualTo(new Line.EffectiveRange(1, 2));
        }

        @Test
        void testARangeStartingOnOneKeySignatureAndEndingOnAnothersBarlineWidensBothWays() {
            var line = lineOf(
                singleBarline(), keySignature(), crotchet(), singleBarline(), keySignature(), crotchet());

            assertThat(line.effectiveRange(1, 3))
                .as("both pairs straddle the range, so it widens at both ends")
                .isEqualTo(new Line.EffectiveRange(0, 4));
        }

        @Test
        void testNoDeletionCanLeaveAKeySignatureAtIndexZero() {
            var line = lineOf(
                singleBarline(), keySignature(), crotchet(), singleBarline(), keySignature(), crotchet());
            var elementCount = line.elementCount();

            for (var from = 0; from < elementCount; from++) {
                for (var to = from; to < elementCount; to++) {
                    var range = line.effectiveRange(from, to);
                    var survivors = new ArrayList<StaffElement>();

                    for (var i = 0; i < elementCount; i++) {
                        if (i < range.begin() || i > range.end()) {
                            survivors.add(line.getElement(i));
                        }
                    }

                    assertThat(survivors.isEmpty()
                        || survivors.getFirst().getType() != ElementType.KEY_SIGNATURE)
                        .as("deleting [%d, %d] must not leave a key signature at index 0", from, to)
                        .isTrue();
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // keyPairDeletion — what a confirmation prompt has to name
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class KeyPairDeletionQuery {

        /** A line and a raw range that together produce one {@link Line.KeyPairDeletion} answer. */
        private record PairingCase(Line line, int from, int to) {}

        /**
         * The case for each answer. A switch expression over the enum, so a new answer breaks
         * this method until someone writes the range that produces it.
         */
        private PairingCase caseFor(Line.KeyPairDeletion answer) {
            return switch (answer) {
                case NONE -> new PairingCase(lineOf(crotchet(), singleBarline(), crotchet()), 0, 1);

                case KEY_SIGNATURE_AFTER -> new PairingCase(
                    lineOf(crotchet(), singleBarline(), keySignature(), crotchet()), 0, 1);

                case BARLINE_BEFORE -> new PairingCase(
                    lineOf(crotchet(), singleBarline(), keySignature(), crotchet()), 2, 3);

                case BOTH -> new PairingCase(
                    lineOf(singleBarline(), keySignature(), crotchet(), singleBarline(), keySignature(), crotchet()),
                    1,
                    3);
            };
        }

        @ParameterizedTest
        @EnumSource(Line.KeyPairDeletion.class)
        void testEachAnswerIsReportedForTheRangeThatProducesIt(Line.KeyPairDeletion answer) {
            var pairingCase = caseFor(answer);

            assertThat(pairingCase.line().keyPairDeletion(pairingCase.from(), pairingCase.to()))
                .as("the range built for %s must be reported as %s", answer, answer)
                .isEqualTo(answer);
        }

        @ParameterizedTest
        @EnumSource(Line.KeyPairDeletion.class)
        void testANonNoneAnswerMeansTheEffectiveRangeIsWiderThanTheRawOne(Line.KeyPairDeletion answer) {
            var pairingCase = caseFor(answer);
            var line = pairingCase.line();
            var range = line.effectiveRange(pairingCase.from(), pairingCase.to());
            var widened = range.begin() < pairingCase.from() || range.end() > pairingCase.to();

            assertThat(widened)
                .as("%s must agree with the widening effectiveRange performs", answer)
                .isEqualTo(answer != Line.KeyPairDeletion.NONE);
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
