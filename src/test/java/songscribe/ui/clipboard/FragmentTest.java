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

import java.util.function.BiFunction;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Annotation;
import songscribe.dom.AnnotationAttachment;
import songscribe.dom.Beam;
import songscribe.dom.Crescendo;
import songscribe.dom.ElementType;
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
    }

    // -----------------------------------------------------------------------
    // Annotation/attachment aliasing regression
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class AttachmentAliasing {

        // Regression: mutating a pasted element's AnnotationAttachment the way
        // VerticalAdjustment.adjustAnnotation (:201-214) does must never affect the
        // original element's annotation. Guards against a shallow-clone reintroducing
        // shared Annotation state between an original and its clipboard-derived clones.
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
            assertThat(pastedAttachment).isNotNull();

            if (pastedAttachment == null) {
                return; // unreachable — NullAway flow narrowing
            }

            var pastedAnnotation = pastedAttachment.getAnnotation();
            pastedAnnotation.setUserYOffsetSs(pastedAnnotation.getUserYOffsetSs() + 10.0);

            // The original element's annotation must be unaffected by the mutation above.
            assertThat(annotation.getUserYOffsetSs()).isEqualTo(5.0);
        }

        // Same shape for TempoChangeAttachment/Tempo: mutating the pasted attachment's
        // own userYOffsetSs (the way VerticalAdjustment.adjustTempoChange does) must
        // never affect the original attachment.
        @Test
        void testMutatingPastedTempoChangeAttachmentDoesNotAffectOriginal() {
            var line = detachedLine();
            var note = crotchet();
            var tempoAttachment = new TempoChangeAttachment(note, new Tempo());
            tempoAttachment.setUserYOffsetSs(5.0);
            note.addAttachment(tempoAttachment);
            line.addElement(note);

            var fragment = Fragment.capture(line, 0, 0);
            var pasted = fragment.instantiate();

            var pastedAttachment = pasted.elements().getFirst().findAttachment(TempoChangeAttachment.class);
            assertThat(pastedAttachment).isNotNull();

            if (pastedAttachment == null) {
                return; // unreachable — NullAway flow narrowing
            }

            pastedAttachment.setUserYOffsetSs(pastedAttachment.getUserYOffsetSs() + 10.0);

            assertThat(tempoAttachment.getUserYOffsetSs()).isEqualTo(5.0);
        }
    }
}
