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

import java.util.List;
import java.util.function.BiFunction;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.dom.StaffElementFactory.breathMark;
import static songscribe.dom.StaffElementFactory.finalDoubleBarline;
import static songscribe.dom.StaffElementFactory.graceQuaver;
import static songscribe.dom.StaffElementFactory.repeatRight;

import songscribe.UnitTest;
import songscribe.dom.Annotation;
import songscribe.dom.AnnotationAttachment;
import songscribe.dom.Beam;
import songscribe.dom.Crescendo;
import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.Lyric;
import songscribe.dom.Span;
import songscribe.dom.StaffElement;
import songscribe.dom.Tempo;
import songscribe.dom.TempoChangeAttachment;
import songscribe.dom.Tie;
import songscribe.dom.Trill;
import songscribe.dom.Tuplet;
import songscribe.dom.Ending;

class FragmentTest extends UnitTest {


    private static final int VERSE = Lyric.FIRST_VERSE;

    /** A triplet: three notes in the time of two of its written value. */
    private static final int TRIPLET_GRADE = 3;
    private static final int TRIPLET_NORMAL_NOTES = 2;
    private static final int NO_DOTS = 0;

    private static StaffElement pairedGraceNote() {
        var grace = graceQuaver();
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
            BiFunction<? super StaffElement, ? super StaffElement, ? extends Span> spanFactory
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
            line.addSpan(span);

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
            assertSpanSurvivesCopyAndReanchors((anchor, end) -> new Tuplet(anchor, end, TRIPLET_GRADE, TRIPLET_NORMAL_NOTES, ElementType.CROTCHET, NO_DOTS));
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
            line.addSpan(new Tie(noteA, noteB));

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
            var tuplet = new Tuplet(noteA, noteD, TRIPLET_GRADE, TRIPLET_NORMAL_NOTES, ElementType.CROTCHET, NO_DOTS);
            line.addSpan(tie);
            line.addSpan(beam);
            line.addSpan(tuplet);

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
            line.addSpan(new Tie(noteB, noteE));

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
            line.addSpan(new Tie(noteA, noteC));

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
            line.addSpan(tieMissingItsEnd);
            line.addSpan(tieMissingItsAnchor);
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
            line.addSpan(new Ending(noteB, noteC));

            var fragment = Fragment.capture(line, 0, 1);

            assertThat(fragment.elements()).hasSize(2);
            assertThat(fragment.spans()).isEmpty();
        }
    }

    // -----------------------------------------------------------------------
    // A tie is dropped when the range holds only one of its two notes —
    // the same rule every other span type follows. The copy still succeeds
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class TieClippedByRange {

        @Test
        void testCaptureRangeContainingBothEndpointsOfATieCopiesIt() {
            var line = detachedLine();
            var noteA = crotchet();
            var noteB = crotchet();
            line.addElement(noteA);
            line.addElement(noteB);
            line.addSpan(new Tie(noteA, noteB));

            var fragment = Fragment.capture(line, 0, 1);

            assertThat(fragment.spans()).hasSize(1);
        }

        @Test
        void testCaptureRangeClippingATieCopiesTheSelectedElementsAndUpdatesTheClipboard() {
            // [A, B, C] — Tie(B, C): only B is inside the captured range [0, 1]. A tie is a
            // relationship between two specific notes, so with one of them outside there is
            // nothing to attach the far end to and the tie is dropped — exactly as every
            // other span type is dropped in this shape. The copy itself must still succeed:
            // the user selected A and B, so they get A and B.
            var line = detachedLine();
            var noteA = crotchet();
            var noteB = crotchet();
            var noteC = crotchet();
            line.addElement(noteA);
            line.addElement(noteB);
            line.addElement(noteC);
            line.addSpan(new Tie(noteB, noteC));

            var baseline = Fragment.capture(lineWith(ElementType.CROTCHET), 0, 0);
            var clipboardManager = new ClipboardManager();
            clipboardManager.setFragment(baseline);

            // The same call shape ScoreViewController.handleCopy makes.
            var fragment = Fragment.capture(line, 0, 1);
            clipboardManager.setFragment(fragment);

            assertThat(fragment.elements()).hasSize(2);
            assertThat(fragment.spans()).as("a clipped tie is dropped, not copied").isEmpty();
            assertThat(clipboardManager.getFragment())
                .as("clipping a tie must not stop the copy from reaching the clipboard")
                .isSameAs(fragment);
        }

        @Test
        void testCaptureRangeContainingNeitherEndpointOfATieDropsIt() {
            // [A, B, C, D] — Tie(A, D): capturing [1, 2] excludes both endpoints.
            var line = detachedLine();
            var noteA = crotchet();
            var noteB = crotchet();
            var noteC = crotchet();
            var noteD = crotchet();
            line.addElement(noteA);
            line.addElement(noteB);
            line.addElement(noteC);
            line.addElement(noteD);
            line.addSpan(new Tie(noteA, noteD));

            var fragment = Fragment.capture(line, 1, 2);

            assertThat(fragment.elements()).hasSize(2);
            assertThat(fragment.spans()).isEmpty();
        }
    }

    // A paste landing at a line boundary needs no Fragment-level special case, so nothing
    // about it is tested here. It reaches a pre-existing cross-line tie through the ordinary
    // insertion rule, and the whole path — reconciliation declining to judge the tie, then
    // the insertion sweep dropping it — is driven through a real paste by
    // ScoreViewControllerTest.TryInsertFragment
    // .testPastingAtTheEndOfALineRemovesTheCrossLineTieItLandsInside.

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
            var breath = breathMark();
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
            line.addSpan(new Tie(noteA, grace));

            var fragment = Fragment.capture(line, 0, 1);

            assertThat(fragment.elements()).hasSize(1);
            assertThat(fragment.elements().getFirst().getType()).isEqualTo(ElementType.CROTCHET);
            assertThat(fragment.spans()).isEmpty();
        }

        @Test
        void testFinalDoubleBarlineIsNormalizedToDoubleBarlineWithoutMutatingTheOriginal() {
            var line = detachedLine();
            var noteA = crotchet();
            var finalBarline = finalDoubleBarline();
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
            var repeatRight = repeatRight();
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
    // Lyric chains that would reach outside the captured run (#708)
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class LyricChains {

        // "A-mi": one hyphenated word spread over two notes.
        private Line hyphenatedWord() {
            var line = detachedLine();
            var first = crotchet();
            var second = crotchet();
            first.lyrics.add(new Lyric(VERSE, "A", Lyric.Extend.NONE, Lyric.Syllabic.BEGIN, false));
            second.lyrics.add(new Lyric(VERSE, "mi", Lyric.Extend.NONE, Lyric.Syllabic.END, false));
            line.addElement(first);
            line.addElement(second);
            return line;
        }

        private Lyric capturedLyric(Fragment fragment, int index) {
            var lyric = fragment.elements().get(index).getLyricForVerse(VERSE);
            assertThat(lyric).isNotNull();
            return lyric;
        }

        @Test
        void testCapturingTheFirstSyllableAloneEndsItsWordWithoutTouchingTheSourceLine() {
            // The issue: pasted before some other BEGIN syllable, a captured BEGIN draws a
            // hyphen joining the two, connecting a word the user never copied.
            var line = hyphenatedWord();

            var fragment = Fragment.capture(line, 0, 0);

            assertThat(capturedLyric(fragment, 0).syllabic()).isEqualTo(Lyric.Syllabic.SINGLE);
            assertThat(capturedLyric(fragment, 0).text()).isEqualTo("A");

            var sourceLyric = line.getElement(0).getLyricForVerse(VERSE);
            assertThat(sourceLyric).isNotNull();
            assertThat(sourceLyric.syllabic())
                .as("the repair belongs to the clones — copying must not edit the song")
                .isEqualTo(Lyric.Syllabic.BEGIN);
        }

        @Test
        void testCapturingTheSecondSyllableAloneDropsItsWordContinuation() {
            var line = hyphenatedWord();

            var fragment = Fragment.capture(line, 1, 1);

            assertThat(capturedLyric(fragment, 0).syllabic()).isEqualTo(Lyric.Syllabic.SINGLE);
            assertThat(capturedLyric(fragment, 0).text()).isEqualTo("mi");
        }

        @Test
        void testCapturingTheWholeWordKeepsTheHyphenBetweenItsSyllables() {
            var line = hyphenatedWord();

            var fragment = Fragment.capture(line, 0, 1);

            assertThat(capturedLyric(fragment, 0).syllabic()).isEqualTo(Lyric.Syllabic.BEGIN);
            assertThat(capturedLyric(fragment, 1).syllabic()).isEqualTo(Lyric.Syllabic.END);
        }

        @Test
        void testCapturingAHostWithoutItsGraceNoteDropsTheOrphanMelismaCarrier() {
            // [G(paired, "la" START), host(STOP carrier), C] captured from the host onward:
            // the host's carrier sustains a melisma whose START stayed behind.
            var line = detachedLine();
            var grace = pairedGraceNote();
            var host = crotchet();
            var noteC = crotchet();
            grace.lyrics.add(new Lyric(VERSE, "la", Lyric.Extend.START, Lyric.Syllabic.SINGLE, false));
            host.lyrics.add(new Lyric(VERSE, "", Lyric.Extend.STOP, null, false));
            line.addElement(grace);
            line.addElement(host);
            line.addElement(noteC);

            var fragment = Fragment.capture(line, 1, 2);

            assertThat(fragment.elements()).hasSize(2);
            assertThat(fragment.elements().getFirst().getLyricForVerse(VERSE))
                .as("an extender with no START left in the run must not be pasted")
                .isNull();

            var sourceHostLyric = line.getElement(1).getLyricForVerse(VERSE);
            assertThat(sourceHostLyric).isNotNull();
            assertThat(sourceHostLyric.extend())
                .as("the source line's own melisma is intact")
                .isEqualTo(Lyric.Extend.STOP);
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

            assertThat(pastedAttachment).as("the pasted element lost its annotation").isNotNull();

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
            var note = crotchet();
            var tempoAttachment = new TempoChangeAttachment(note, new Tempo());
            tempoAttachment.setUserYOffsetSs(5.0);
            note.addAttachment(tempoAttachment);
            line.addElement(crotchet());
            line.addElement(note);

            var fragment = Fragment.capture(line, 1, 1);
            var pasted = fragment.instantiate();

            var pastedAttachment = pasted.elements().getFirst().findAttachment(TempoChangeAttachment.class);

            assertThat(pastedAttachment).as("the pasted element lost its tempo change").isNotNull();

            pastedAttachment.setUserYOffsetSs(pastedAttachment.getUserYOffsetSs() + 10.0);

            assertThat(tempoAttachment.getUserYOffsetSs()).isEqualTo(5.0);
        }

        /**
         * Copying used to strip the tempo change off the very first element of the line, because
         * that one mirrored the song's own tempo and pasting it elsewhere would have planted a
         * tempo change the user never wrote. The song now owns its tempo outright, so every
         * attachment is an ordinary tempo change and copying keeps all of them — including one
         * on the element the copy starts at.
         */
        @Test
        void testATempoChangeOnTheFirstCapturedElementIsKept() {
            var line = detachedLine();
            var note = crotchet();
            var bpm = 152;
            note.addAttachment(new TempoChangeAttachment(
                note,
                new Tempo(bpm, Tempo.DEFAULT_TYPE, Tempo.DEFAULT_DESCRIPTION, Tempo.DEFAULT_SHOW_TEMPO)));
            line.addElement(note);
            line.addElement(crotchet());

            var pasted = Fragment.capture(line, 0, 1).instantiate();
            var pastedAttachment =
                pasted.elements().getFirst().findAttachment(TempoChangeAttachment.class);

            assertThat(pastedAttachment)
                .as("a tempo change on the line's first element must survive the copy")
                .isNotNull();
            assertThat(pastedAttachment.getTempo().getVisibleTempo())
                .as("the copied tempo change must carry the tempo it was written with")
                .isEqualTo(bpm);
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
            line.addElement(breathMark());

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
