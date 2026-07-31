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

package songscribe.ui.clipboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.function.BiFunction;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Annotation;
import songscribe.dom.AnnotationAttachment;
import songscribe.dom.Beam;
import songscribe.dom.Crescendo;
import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.RangeElement;
import songscribe.dom.StaffElement;
import songscribe.dom.Tempo;
import songscribe.dom.TempoChangeAttachment;
import songscribe.dom.Tie;
import songscribe.dom.Trill;
import songscribe.dom.Tuplet;
import songscribe.layout.Ending;

class FragmentTest extends UnitTest {

    private static StaffElement crotchet() {
        return ElementType.CROTCHET.newInstance();
    }

    private static StaffElement pairedGraceNote() {
        var grace = ElementType.GRACE_QUAVER.newInstance();
        grace.setGlissando();
        return grace;
    }

    // -----------------------------------------------------------------------
    // Span copy and re-anchor round trip
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class SpanRoundTrip {

        // Builds a 4-note line and a span fully contained within [0, 3], captures it,
        // and asserts the captured span is anchored to the clones (not the originals).
        // Then instantiates the captured fragment again and asserts the spans re-anchor
        // to a *fresh* set of clones each time.
        private void assertSpanSurvivesCopyAndReanchors(
            BiFunction<StaffElement, StaffElement, RangeElement> spanFactory
        ) {
            var line = detachedLine();
            var noteA = crotchet();
            var noteB = crotchet();
            var noteC = crotchet();
            var noteD = crotchet();
            line.addElement(noteA);
            line.addElement(noteB);
            line.addElement(noteC);
            line.addElement(noteD);

            var span = spanFactory.apply(noteB, noteC);
            line.addRangeElement(span);

            var fragment = Fragment.capture(line, 0, 3);

            assertThat(fragment.elements()).hasSize(4);
            assertThat(fragment.spans()).hasSize(1);
            var capturedSpan = fragment.spans().getFirst();

            // Anchored to the pasted clones, not the originals.
            assertThat(capturedSpan.getAnchorElement()).isSameAs(fragment.elements().get(1));
            assertThat(capturedSpan.getEndElement()).isSameAs(fragment.elements().get(2));
            assertThat(capturedSpan.getAnchorElement()).isNotSameAs(noteB);
            assertThat(capturedSpan.getEndElement()).isNotSameAs(noteC);

            // Re-instantiating produces an independent set of clones, re-anchored again.
            var instantiated = fragment.instantiate();

            assertThat(instantiated.spans()).hasSize(1);
            var instantiatedSpan = instantiated.spans().getFirst();

            assertThat(instantiatedSpan.getAnchorElement()).isSameAs(instantiated.elements().get(1));
            assertThat(instantiatedSpan.getEndElement()).isSameAs(instantiated.elements().get(2));
            assertThat(instantiatedSpan.getAnchorElement()).isNotSameAs(capturedSpan.getAnchorElement());
            assertThat(instantiatedSpan.getEndElement()).isNotSameAs(capturedSpan.getEndElement());
        }

        @Test
        void testTieSurvivesCopyAndReanchorsToClones() {
            assertSpanSurvivesCopyAndReanchors(Tie::new);
        }

        @Test
        void testBeamSurvivesCopyAndReanchorsToClones() {
            assertSpanSurvivesCopyAndReanchors(Beam::new);
        }

        @Test
        void testTupletSurvivesCopyAndReanchorsToClones() {
            assertSpanSurvivesCopyAndReanchors((anchor, end) -> new Tuplet(anchor, end, 3));
        }

        @Test
        void testHairpinSurvivesCopyAndReanchorsToClones() {
            assertSpanSurvivesCopyAndReanchors(Crescendo::new);
        }

        @Test
        void testTrillSurvivesCopyAndReanchorsToClones() {
            assertSpanSurvivesCopyAndReanchors(Trill::new);
        }

        @Test
        void testEndingSurvivesCopyAndReanchorsToClones() {
            assertSpanSurvivesCopyAndReanchors(Ending::new);
        }
    }

    // -----------------------------------------------------------------------
    // instantiate() called twice must yield fully independent results
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class RepeatedInstantiation {

        // Guards against a shared/cached IdentityHashMap: every existing test compares
        // an instantiate() result only against the original captured Fragment, which
        // would still pass if both instantiate() calls returned (or re-anchored onto)
        // the SAME clones. Comparing the two instantiations against EACH OTHER closes
        // that hole.
        @Test
        void testTwoInstantiationsOfTheSameFragmentAreIndependentOfEachOther() {
            var line = detachedLine();
            var noteA = crotchet();
            var noteB = crotchet();
            line.addElement(noteA);
            line.addElement(noteB);
            line.addRangeElement(new Tie(noteA, noteB));

            var fragment = Fragment.capture(line, 0, 1);

            var first = fragment.instantiate();
            var second = fragment.instantiate();

            assertThat(first.elements().get(0))
                .as("each instantiate() call must clone its own elements")
                .isNotSameAs(second.elements().get(0));
            assertThat(first.elements().get(1)).isNotSameAs(second.elements().get(1));

            assertThat(first.spans()).hasSize(1);
            assertThat(second.spans()).hasSize(1);
            var firstSpan = first.spans().getFirst();
            var secondSpan = second.spans().getFirst();

            assertThat(firstSpan)
                .as("each instantiate() call must produce its own span instance")
                .isNotSameAs(secondSpan);
            assertThat(firstSpan.getAnchorElement()).isNotSameAs(secondSpan.getAnchorElement());
            assertThat(firstSpan.getEndElement()).isNotSameAs(secondSpan.getEndElement());

            // Each instantiation's span must anchor to THAT instantiation's own elements,
            // never to the other instantiation's clones.
            assertThat(firstSpan.getAnchorElement()).isSameAs(first.elements().get(0));
            assertThat(firstSpan.getEndElement()).isSameAs(first.elements().get(1));
            assertThat(secondSpan.getAnchorElement()).isSameAs(second.elements().get(0));
            assertThat(secondSpan.getEndElement()).isSameAs(second.elements().get(1));
        }
    }

    // -----------------------------------------------------------------------
    // Multiple overlapping/nested spans in one capture
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class MultipleSpansInOneCapture {

        // [A, B, C, D] with Tie(A,B) and Beam(A,B) sharing the same pair of endpoints,
        // plus a Tuplet(A,D) nested around (and wider than) that pair — stresses the
        // capture-shared IdentityHashMap re-anchoring competing/nested spans onto the
        // SAME clone instances, not per-span copies. Every existing SpanRoundTrip test
        // captures exactly one span, so a bug where each span's endpoints get cloned
        // independently (breaking the shared-instance invariant) would still pass them.
        @Test
        void testCompetingAndNestedSpansShareTheSameReanchoredClones() {
            var line = detachedLine();
            var noteA = crotchet();
            var noteB = crotchet();
            var noteC = crotchet();
            var noteD = crotchet();
            line.addElement(noteA);
            line.addElement(noteB);
            line.addElement(noteC);
            line.addElement(noteD);

            var tie = new Tie(noteA, noteB);
            var beam = new Beam(noteA, noteB);
            var tuplet = new Tuplet(noteA, noteD, 3);
            line.addRangeElement(tie);
            line.addRangeElement(beam);
            line.addRangeElement(tuplet);

            var fragment = Fragment.capture(line, 0, 3);

            assertThat(fragment.spans()).hasSize(3);
            var clonedTie = fragment.spans().get(0);
            var clonedBeam = fragment.spans().get(1);
            var clonedTuplet = fragment.spans().get(2);

            assertThat(clonedTie.getAnchorElement()).isSameAs(fragment.elements().get(0));
            assertThat(clonedTie.getEndElement()).isSameAs(fragment.elements().get(1));

            assertThat(clonedBeam.getAnchorElement()).isSameAs(fragment.elements().get(0));
            assertThat(clonedBeam.getEndElement()).isSameAs(fragment.elements().get(1));

            assertThat(clonedTuplet.getAnchorElement()).isSameAs(fragment.elements().get(0));
            assertThat(clonedTuplet.getEndElement()).isSameAs(fragment.elements().get(3));

            // Tie, Beam, and Tuplet all anchor on noteA: they must share the SAME
            // clone instance, not each get their own independent copy of it.
            assertThat(clonedTie.getAnchorElement()).isSameAs(clonedBeam.getAnchorElement());
            assertThat(clonedTie.getAnchorElement()).isSameAs(clonedTuplet.getAnchorElement());
            // Tie and Beam also share the same end (noteB) clone.
            assertThat(clonedTie.getEndElement()).isSameAs(clonedBeam.getEndElement());
        }
    }

    // -----------------------------------------------------------------------
    // Partially-overlapping spans are dropped
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class PartialOverlapDropped {

        @Test
        void testSpanWithAnchorInsideRangeAndEndOutsideIsDropped() {
            // [A, B, C, D, E] — Tie(B, E): anchor (B) is inside the captured range [0, 2],
            // but the end (E) is outside it. The span must not survive the copy.
            var line = detachedLine();
            var noteA = crotchet();
            var noteB = crotchet();
            var noteC = crotchet();
            var noteD = crotchet();
            var noteE = crotchet();
            line.addElement(noteA);
            line.addElement(noteB);
            line.addElement(noteC);
            line.addElement(noteD);
            line.addElement(noteE);
            line.addRangeElement(new Tie(noteB, noteE));

            var fragment = Fragment.capture(line, 0, 2);

            assertThat(fragment.elements()).hasSize(3);
            assertThat(fragment.spans()).isEmpty();
        }

        @Test
        void testSpanWithAnchorOutsideRangeAndEndInsideIsDropped() {
            // [A, B, C, D, E] — Tie(A, C): end (C) is inside the captured range [2, 4],
            // but the anchor (A) is outside it. The span must not survive the copy.
            var line = detachedLine();
            var noteA = crotchet();
            var noteB = crotchet();
            var noteC = crotchet();
            var noteD = crotchet();
            var noteE = crotchet();
            line.addElement(noteA);
            line.addElement(noteB);
            line.addElement(noteC);
            line.addElement(noteD);
            line.addElement(noteE);
            line.addRangeElement(new Tie(noteA, noteC));

            var fragment = Fragment.capture(line, 2, 4);

            assertThat(fragment.elements()).hasSize(3);
            assertThat(fragment.spans()).isEmpty();
        }

        @Test
        void testSpanMissingAnEndpointIsDroppedRatherThanCrashingTheCapture() {
            // A span whose anchor or end is null cannot be re-anchored onto a clone —
            // there is no original to look up in the clone map. Capture must skip it
            // instead of dereferencing the missing endpoint.
            var line = detachedLine();
            var noteA = crotchet();
            var noteB = crotchet();
            line.addElement(noteA);
            line.addElement(noteB);

            var tieMissingItsEnd = new Tie(noteA, noteB);
            var tieMissingItsAnchor = new Tie(noteA, noteB);
            line.addRangeElement(tieMissingItsEnd);
            line.addRangeElement(tieMissingItsAnchor);
            tieMissingItsEnd.setEndElement(null);
            tieMissingItsAnchor.setAnchorElement(null);

            var fragment = Fragment.capture(line, 0, 1);

            assertThat(fragment.elements()).hasSize(2);
            assertThat(fragment.spans())
                .as("a span missing either endpoint cannot be re-anchored and must be dropped")
                .isEmpty();
        }

        @Test
        void testEndingWithAnchorInsideRangeAndEndOutsideIsDropped() {
            // Same shape as the generic case above, but with an Ending — endings are
            // significant enough that the plan calls them out explicitly.
            var line = detachedLine();
            var noteA = crotchet();
            var noteB = crotchet();
            var noteC = crotchet();
            line.addElement(noteA);
            line.addElement(noteB);
            line.addElement(noteC);
            line.addRangeElement(new Ending(noteB, noteC));

            var fragment = Fragment.capture(line, 0, 1);

            assertThat(fragment.elements()).hasSize(2);
            assertThat(fragment.spans()).isEmpty();
        }
    }

    // -----------------------------------------------------------------------
    // Boundary normalization
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class BoundaryNormalization {

        @Test
        void testTrailingBreathMarkIsIncludedInCapture() {
            // [A, breath] — capturing just A must extend to include the breath mark,
            // since it is positionally attached to A.
            var line = detachedLine();
            var noteA = crotchet();
            var breath = ElementType.BREATH_MARK.newInstance();
            line.addElement(noteA);
            line.addElement(breath);

            var fragment = Fragment.capture(line, 0, 0);

            assertThat(fragment.elements()).hasSize(2);
            assertThat(fragment.elements().get(1).getType()).isEqualTo(ElementType.BREATH_MARK);
        }

        @Test
        void testOrphanPairedGraceNoteIsDroppedFromTailAlongWithItsSpans() {
            // [A, G(paired)] — capturing [A, G] must drop the trailing orphan grace
            // note, since its host lies outside the captured range. A span anchored
            // to the grace note must be dropped along with it.
            var line = detachedLine();
            var noteA = crotchet();
            var grace = pairedGraceNote();
            line.addElement(noteA);
            line.addElement(grace);
            line.addRangeElement(new Tie(noteA, grace));

            var fragment = Fragment.capture(line, 0, 1);

            assertThat(fragment.elements()).hasSize(1);
            assertThat(fragment.elements().getFirst().getType()).isEqualTo(ElementType.CROTCHET);
            assertThat(fragment.spans()).isEmpty();
        }

        @Test
        void testFinalDoubleBarlineIsNormalizedToDoubleBarlineWithoutMutatingTheOriginal() {
            var line = detachedLine();
            var noteA = crotchet();
            var finalBarline = ElementType.FINAL_DOUBLE_BARLINE.newInstance();
            line.addElement(noteA);
            line.addElement(finalBarline);

            var fragment = Fragment.capture(line, 0, 1);

            assertThat(fragment.elements()).hasSize(2);
            assertThat(fragment.elements().get(1).getType()).isEqualTo(ElementType.DOUBLE_BARLINE);
            // The original line's terminal must be untouched.
            assertThat(line.getElement(1).getType()).isEqualTo(ElementType.FINAL_DOUBLE_BARLINE);
        }

        @Test
        void testRepeatRightTerminalPassesThroughCaptureUnmodified() {
            // Regression guard from the pre-refactor ClipboardManagerTest: only
            // FINAL_DOUBLE_BARLINE is normalized on capture. REPEAT_RIGHT (and every
            // other terminal) must pass through as an unmodified clone, and the
            // original line's element must be untouched.
            var line = detachedLine();
            var noteA = crotchet();
            var repeatRight = ElementType.REPEAT_RIGHT.newInstance();
            line.addElement(noteA);
            line.addElement(repeatRight);

            var fragment = Fragment.capture(line, 0, 1);

            assertThat(fragment.elements()).hasSize(2);
            assertThat(fragment.elements().get(1).getType()).isEqualTo(ElementType.REPEAT_RIGHT);
            assertThat(fragment.elements().get(1)).isNotSameAs(repeatRight);
            // The original line's terminal must be untouched.
            assertThat(line.getElement(1).getType()).isEqualTo(ElementType.REPEAT_RIGHT);
        }

        @Test
        void testCapturingOnlyAnOrphanPairedGraceNoteReturnsEmptyFragment() {
            // [G(paired), host] — capturing just the grace note (begin == end == 0,
            // excluding its host at index 1) makes the tail trim's effectiveEnd--
            // walk below begin, so the clone loop never runs at all: an entirely
            // empty Fragment, not a Fragment containing the dropped grace note.
            var line = detachedLine();
            var grace = pairedGraceNote();
            var host = crotchet();
            line.addElement(grace);
            line.addElement(host);

            var fragment = Fragment.capture(line, 0, 0);

            assertThat(fragment.elements()).as("copying exactly one grace note must be empty").isEmpty();
            assertThat(fragment.spans()).isEmpty();
        }

        @Test
        void testLeadingGraceNoteOrphanedFromHeadIsNotSpecialCased() {
            // [G(paired), host, C] — capturing [host, C] (begin == 1) excludes the
            // leading grace note at index 0 from the head, the mirror image of the
            // tail-trim case. capture() only special-cases the tail (effectiveEnd),
            // so this must NOT trim or otherwise alter the captured range: the host
            // is captured exactly like any other element.
            var line = detachedLine();
            var grace = pairedGraceNote();
            var host = crotchet();
            var noteC = crotchet();
            line.addElement(grace);
            line.addElement(host);
            line.addElement(noteC);

            var fragment = Fragment.capture(line, 1, 2);

            assertThat(fragment.elements()).hasSize(2);
            assertThat(fragment.elements().get(0).getType()).isEqualTo(ElementType.CROTCHET);
            assertThat(fragment.elements().get(0)).isNotSameAs(host);
            assertThat(fragment.elements().get(1).getType()).isEqualTo(ElementType.CROTCHET);
        }
    }

    // -----------------------------------------------------------------------
    // Annotation/attachment aliasing regression
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class AttachmentAliasing {

        // Regression: mutating a pasted element's AnnotationAttachment in place must never
        // affect the original element's annotation. Guards against a shallow-clone
        // reintroducing shared Annotation state between an original and its
        // clipboard-derived clones.
        @Test
        void testMutatingPastedAnnotationDoesNotAffectOriginal() {
            var line = detachedLine();
            var note = crotchet();
            var annotation = new Annotation("dolce");
            annotation.setUserYOffsetSs(5.0);
            note.addAttachment(new AnnotationAttachment(note, annotation));
            line.addElement(note);

            var fragment = Fragment.capture(line, 0, 0);
            var pasted = fragment.instantiate();

            var pastedAttachment = pasted.elements().getFirst().findAttachment(AnnotationAttachment.class);

            if (pastedAttachment == null) {
                throw new AssertionError("the pasted element lost its annotation");
            }

            var pastedAnnotation = pastedAttachment.getAnnotation();
            pastedAnnotation.setUserYOffsetSs(pastedAnnotation.getUserYOffsetSs() + 10.0);

            // The original element's annotation must be unaffected by the mutation above.
            assertThat(annotation.getUserYOffsetSs()).isEqualTo(5.0);
        }

        // Same shape for TempoChangeAttachment/Tempo: mutating the pasted attachment's
        // own userYOffsetSs in place must never affect the original attachment.
        @Test
        void testMutatingPastedTempoChangeAttachmentDoesNotAffectOriginal() {
            var line = detachedLine();
            // The tempo sits on the second element: a tempo on the song's first
            // element is the song's initial tempo, which capture deliberately drops.
            var note = crotchet();
            var tempoAttachment = new TempoChangeAttachment(note, new Tempo());
            tempoAttachment.setUserYOffsetSs(5.0);
            note.addAttachment(tempoAttachment);
            line.addElement(crotchet());
            line.addElement(note);

            var fragment = Fragment.capture(line, 1, 1);
            var pasted = fragment.instantiate();

            var pastedAttachment = pasted.elements().getFirst().findAttachment(TempoChangeAttachment.class);

            if (pastedAttachment == null) {
                throw new AssertionError("the pasted element lost its tempo change");
            }

            pastedAttachment.setUserYOffsetSs(pastedAttachment.getUserYOffsetSs() + 10.0);

            assertThat(tempoAttachment.getUserYOffsetSs()).isEqualTo(5.0);
        }
    }

    // -----------------------------------------------------------------------
    // The song's initial tempo is never copied
    // -----------------------------------------------------------------------

    /**
     * A detached line's Song mock reports it as line 0, so its first element is the song's
     * first element — the initial-tempo anchor — unless a test stubs indexOfLine otherwise.
     */
    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class InitialTempoDropped {

        @Test
        void testTempoOnTheSongsFirstElementIsNotCaptured() {
            var line = detachedLine();
            var noteA = crotchet();
            var noteB = crotchet();
            line.addElement(noteA);
            line.addElement(noteB);
            var initialTempo = new TempoChangeAttachment(noteA, new Tempo());
            noteA.addAttachment(initialTempo);

            var fragment = Fragment.capture(line, 0, 1);

            assertThat(fragment.elements()).hasSize(2);
            assertThat(fragment.elements().getFirst().findAttachment(TempoChangeAttachment.class))
                .as("the song's initial tempo must not travel with the copy")
                .isNull();
            // Capture must not disturb the source: the song keeps its initial tempo.
            assertThat(noteA.findAttachment(TempoChangeAttachment.class)).isSameAs(initialTempo);
        }

        @Test
        void testTempoChangeOnALaterElementOfTheCaptureIsKept() {
            // Both elements carry a tempo, so the strip has to discriminate: only the
            // anchor loses its tempo, and the real tempo change behind it survives.
            var line = detachedLine();
            var noteA = crotchet();
            var noteB = crotchet();
            line.addElement(noteA);
            line.addElement(noteB);
            noteA.addAttachment(new TempoChangeAttachment(noteA, new Tempo()));
            noteB.addAttachment(new TempoChangeAttachment(noteB, new Tempo()));

            var fragment = Fragment.capture(line, 0, 1);

            assertThat(fragment.elements().getFirst().findAttachment(TempoChangeAttachment.class))
                .as("the anchor's tempo is the song's and must be stripped")
                .isNull();
            assertThat(fragment.elements().get(1).findAttachment(TempoChangeAttachment.class))
                .as("a tempo change behind the anchor is real content and must survive")
                .isNotNull();
        }

        @Test
        void testTempoOnACaptureNotStartingAtTheSongsFirstElementIsKept() {
            var line = detachedLine();
            var noteA = crotchet();
            var noteB = crotchet();
            line.addElement(noteA);
            line.addElement(noteB);
            noteB.addAttachment(new TempoChangeAttachment(noteB, new Tempo()));

            var fragment = Fragment.capture(line, 1, 1);

            assertThat(fragment.elements().getFirst().findAttachment(TempoChangeAttachment.class))
                .as("a capture that skips the anchor strips nothing")
                .isNotNull();
        }

        @Test
        void testTempoOnTheFirstElementOfALaterLineIsKept() {
            // Index 0 of a line that is not the song's first line holds a genuine
            // tempo change, not the song's initial tempo.
            var song = minimalSongMock();
            var line = new Line(song);
            var note = crotchet();
            line.addElement(note);
            note.addAttachment(new TempoChangeAttachment(note, new Tempo()));
            when(song.indexOfLine(line)).thenReturn(1);

            var fragment = Fragment.capture(line, 0, 0);

            assertThat(fragment.elements().getFirst().findAttachment(TempoChangeAttachment.class))
                .as("index 0 of a later line is not the anchor, so its tempo is real content")
                .isNotNull();
        }
    }

    // -----------------------------------------------------------------------
    // Prior accidentals — what each captured note sounded like on its source line
    // -----------------------------------------------------------------------

    /**
     * A fragment records, per element, the accidental that was in effect for it on the source
     * line, so a paste can preserve the pitch each note had there rather than the pitch the
     * destination's context would give it.
     */
    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class PriorAccidentals {

        // Staff position 0 is B4 and positions grow downwards, so each step down is one letter back.
        private static final int G_STAFF_POSITION = 2;
        private static final int F_STAFF_POSITION = 3;

        private static final int STALE_X_OFFSET_PX = 37;

        /** F♯ G F — the last F carries no accidental of its own and inherits the sharp. */
        private static Line sharpenedThenInheritingF() {
            var line = detachedLine();
            var sharpF = crotchet();
            sharpF.setStaffPosition(F_STAFF_POSITION);
            sharpF.setAccidental(StaffElement.Accidental.SHARP);
            var g = crotchet();
            g.setStaffPosition(G_STAFF_POSITION);
            var inheritingF = crotchet();
            inheritingF.setStaffPosition(F_STAFF_POSITION);
            line.addElement(sharpF);
            line.addElement(g);
            line.addElement(inheritingF);
            return line;
        }

        @Test
        void testCaptureResolvesAnInheritedAccidentalAgainstTheLiveOriginal() {
            // The last F sounds sharp on this line even though it carries nothing, and the
            // fragment has to say so. Resolving against the clone instead would silently skip the
            // backward scan and answer with the key signature alone.
            var line = sharpenedThenInheritingF();

            var fragment = Fragment.capture(line, 2, 2);

            assertThat(fragment.priorAccidentals()).containsExactly(StaffElement.Accidental.SHARP);
        }

        @Test
        void testCaptureRecordsOwnAccidentalsInheritedOnesAndNullForAnUnalteredNote() {
            var line = sharpenedThenInheritingF();

            var fragment = Fragment.capture(line, 0, 2);

            assertThat(fragment.priorAccidentals()).containsExactly(
                StaffElement.Accidental.SHARP, null, StaffElement.Accidental.SHARP);
        }

        @Test
        void testPriorAccidentalsStayAlignedWhenCaptureTrimsAnOrphanPairedGraceNote() {
            // [F♭, grace(paired)] — the tail trim drops the grace note, and the parallel list has
            // to shrink with it or every later entry describes the wrong element.
            var line = detachedLine();
            var flatF = crotchet();
            flatF.setStaffPosition(F_STAFF_POSITION);
            flatF.setAccidental(StaffElement.Accidental.FLAT);
            line.addElement(flatF);
            line.addElement(pairedGraceNote());

            var fragment = Fragment.capture(line, 0, 1);

            assertThat(fragment.elements()).hasSize(1);
            assertThat(fragment.priorAccidentals()).containsExactly(StaffElement.Accidental.FLAT);
        }

        @Test
        void testPriorAccidentalsStayAlignedWhenCaptureExtendsPastATrailingBreathMark() {
            // [F♯, breath] — capturing just the F extends to include the breath mark, which is not
            // a pitched note and so contributes a null entry.
            var line = detachedLine();
            var sharpF = crotchet();
            sharpF.setStaffPosition(F_STAFF_POSITION);
            sharpF.setAccidental(StaffElement.Accidental.SHARP);
            line.addElement(sharpF);
            line.addElement(ElementType.BREATH_MARK.newInstance());

            var fragment = Fragment.capture(line, 0, 0);

            assertThat(fragment.elements()).hasSize(2);
            assertThat(fragment.priorAccidentals()).containsExactly(StaffElement.Accidental.SHARP, null);
        }

        @Test
        void testInstantiateCarriesThePriorAccidentalsThroughAndZeroesEveryCloneXOffset() {
            // An xOffset is a layout correction made under one specific spring solve; pasted
            // elsewhere it recreates the collision it was made to fix.
            var line = sharpenedThenInheritingF();

            for (var i = 0; i < line.elementCount(); i++) {
                line.getElement(i).setXOffsetPx(STALE_X_OFFSET_PX);
            }

            var instantiated = Fragment.capture(line, 0, 2).instantiate();

            assertThat(instantiated.priorAccidentals()).containsExactly(
                StaffElement.Accidental.SHARP, null, StaffElement.Accidental.SHARP);
            assertThat(instantiated.elements()).allSatisfy(
                element -> assertThat(element.getXOffsetPx()).isEqualTo(0));
        }

        @Test
        void testConstructorRejectsAPriorAccidentalListThatIsNotParallelToTheElements() {
            var elements = List.of(crotchet(), crotchet());
            var priorAccidentals = List.of(StaffElement.Accidental.SHARP);

            assertThatIllegalArgumentException()
                .isThrownBy(() -> new Fragment(elements, priorAccidentals, List.of()))
                .withMessageContaining("must be parallel to elements");
        }
    }
}
