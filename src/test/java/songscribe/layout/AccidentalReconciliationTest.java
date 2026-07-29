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

package songscribe.layout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Crescendo;
import songscribe.dom.ElementType;
import songscribe.dom.KeyType;
import songscribe.dom.Line;
import songscribe.dom.RangeElement;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.dom.Tie;

/**
 * Tests for {@link AccidentalReconciliation}, the pure pre-mutation unit that decides which notes
 * must gain or lose an explicit accidental so an edit changes no pitch the user did not ask to
 * change.
 *
 * <p>Two fixtures from #676 carry most of the file, both on a line with no key signature so the
 * key fallback yields null for every pitch class and the arithmetic is visible:
 *
 * <pre>
 * Fixture A:   F   G   A   F      — both F sound natural
 * Fixture B:   F♯  G   A   F      — the last F inherits the sharp and sounds sharp
 * </pre>
 */
class AccidentalReconciliationTest extends UnitTest {

    // Staff position 0 is B4 and positions grow downwards, so each step down is one letter back.
    private static final int B_STAFF_POSITION = 0;
    private static final int A_STAFF_POSITION = 1;
    private static final int G_STAFF_POSITION = 2;
    private static final int F_STAFF_POSITION = 3;
    private static final int E_STAFF_POSITION = 4;

    private static final int FIRST_NOTE = 0;
    private static final int SECOND_NOTE = 1;
    private static final int THIRD_NOTE = 2;
    private static final int FOURTH_NOTE = 3;
    private static final int FIFTH_NOTE = 4;

    /** One flat in the signature is B♭, which is why the cross-key case below uses a B. */
    private static final int ONE_FLAT = 1;

    /** One sharp in the signature is F♯, which is why the sharp-key case below uses an F. */
    private static final int ONE_SHARP = 1;

    private static StaffElement note(int staffPosition, StaffElement.@Nullable Accidental accidental) {
        var element = ElementType.CROTCHET.newInstance();
        element.setStaffPosition(staffPosition);
        element.setAccidental(accidental);
        return element;
    }

    private static StaffElement note(int staffPosition) {
        return note(staffPosition, null);
    }

    private static Line lineOf(StaffElement... elements) {
        var line = detachedLine();

        for (var element : elements) {
            line.addElement(element);
        }

        return line;
    }

    /** F G A F, no key signature — both F sound natural. */
    private static Line fixtureA() {
        return lineOf(
            note(F_STAFF_POSITION), note(G_STAFF_POSITION), note(A_STAFF_POSITION), note(F_STAFF_POSITION));
    }

    /** F♯ G A F, no key signature — the last F inherits the sharp. */
    private static Line fixtureB() {
        return lineOf(
            note(F_STAFF_POSITION, StaffElement.Accidental.SHARP),
            note(G_STAFF_POSITION),
            note(A_STAFF_POSITION),
            note(F_STAFF_POSITION));
    }

    /**
     * A prior-accidental list that may contain nulls — {@code List.of} rejects them, and null is
     * meaningful here ("sounded unaltered in the source context").
     */
    @SafeVarargs
    private static List<StaffElement.@Nullable Accidental> priors(
        StaffElement.@Nullable Accidental... accidentals) {

        return Arrays.asList(accidentals);
    }

    /** A paste with no deletion: {@code inserted} lands at {@code insertIndex}. */
    private static AccidentalReconciliation.InsertionRegion paste(
        Line line,
        int insertIndex,
        List<StaffElement> inserted,
        List<StaffElement.@Nullable Accidental> priorAccidentals) {

        return new AccidentalReconciliation.InsertionRegion(
            line, insertIndex, null, inserted, priorAccidentals, List.of());
    }

    /**
     * A paste with no deletion, carrying the fragment's own range elements — the ties that came
     * with it and are not on the destination line yet.
     */
    private static AccidentalReconciliation.InsertionRegion pasteWithSpans(
        Line line,
        int insertIndex,
        List<StaffElement> inserted,
        List<StaffElement.@Nullable Accidental> priorAccidentals,
        List<RangeElement> insertedSpans) {

        return new AccidentalReconciliation.InsertionRegion(
            line, insertIndex, null, inserted, priorAccidentals, insertedSpans);
    }

    /** A paste-replace: {@code [begin, end]} goes away and {@code inserted} takes its place. */
    private static AccidentalReconciliation.InsertionRegion pasteReplace(
        Line line,
        int begin,
        int end,
        List<StaffElement> inserted,
        List<StaffElement.@Nullable Accidental> priorAccidentals) {

        return new AccidentalReconciliation.InsertionRegion(
            line, begin, new InsertionSpacingCalculator.DeletedRange(begin, end),
            inserted, priorAccidentals, List.of());
    }

    /** A fresh insert of one element — no source context, so the empty prior-accidental list. */
    private static AccidentalReconciliation.InsertionRegion insert(
        Line line, int insertIndex, StaffElement element) {

        return new AccidentalReconciliation.InsertionRegion(
            line, insertIndex, null, List.of(element), List.of(), List.of());
    }

    private static AccidentalReconciliation.AccidentalChange change(
        Line line, int index, StaffElement.@Nullable Accidental accidental) {

        return new AccidentalReconciliation.AccidentalChange(line.getElement(index), accidental);
    }

    private static List<AccidentalReconciliation.IntendedChange> toggle(
        int index, StaffElement.@Nullable Accidental accidental, int staffPosition) {

        return List.of(new AccidentalReconciliation.IntendedChange(index, accidental, staffPosition));
    }

    // -----------------------------------------------------------------------
    // Insert, delete and paste
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class Reconcile {

        @Test
        void testPastedSharpMaterializesANaturalOnTheFollowingNoteAtThatStaffPosition() {
            // Fixture A: without reconciliation the F at index 3 scans back, finds the pasted
            // sharp, and sounds sharp — one pasted note changing a note the user never touched.
            var line = fixtureA();
            var pastedSharpF = note(F_STAFF_POSITION, StaffElement.Accidental.SHARP);

            var accidentalChanges = AccidentalReconciliation.reconcile(
                paste(line, SECOND_NOTE, List.of(pastedSharpF), priors(StaffElement.Accidental.SHARP)));

            assertThat(accidentalChanges)
                .containsExactly(change(line, FOURTH_NOTE, StaffElement.Accidental.NATURAL));
        }

        @Test
        void testOnlyTheFirstFollowingNoteAtThatStaffPositionIsMaterialized() {
            // F G A F F — the second F resolves from the materialization on the first and needs
            // nothing of its own.
            var line = lineOf(
                note(F_STAFF_POSITION), note(G_STAFF_POSITION), note(A_STAFF_POSITION),
                note(F_STAFF_POSITION), note(F_STAFF_POSITION));
            var pastedSharpF = note(F_STAFF_POSITION, StaffElement.Accidental.SHARP);

            var accidentalChanges = AccidentalReconciliation.reconcile(
                paste(line, SECOND_NOTE, List.of(pastedSharpF), priors(StaffElement.Accidental.SHARP)));

            assertThat(accidentalChanges)
                .containsExactly(change(line, FOURTH_NOTE, StaffElement.Accidental.NATURAL));
            assertThat(line.getElement(FIFTH_NOTE).getAccidental()).isNull();
        }

        @Test
        void testAFollowingNoteWithItsOwnAccidentalIsNotMaterialized() {
            // The last F already says what it is, so the pasted sharp cannot reach it.
            var line = lineOf(
                note(F_STAFF_POSITION), note(G_STAFF_POSITION), note(A_STAFF_POSITION),
                note(F_STAFF_POSITION, StaffElement.Accidental.NATURAL));
            var pastedSharpF = note(F_STAFF_POSITION, StaffElement.Accidental.SHARP);

            var accidentalChanges = AccidentalReconciliation.reconcile(
                paste(line, SECOND_NOTE, List.of(pastedSharpF), priors(StaffElement.Accidental.SHARP)));

            assertThat(accidentalChanges).isEmpty();
        }

        @Test
        void testPasteReplacingTheSharpenedNoteMaterializesTheSharpOnTheInheritingNote() {
            // Fixture B, paste-replace over [0..1]: the sharp the last F inherited is being
            // deleted, so it has to be written onto the note that inherited it.
            var line = fixtureB();

            var accidentalChanges = AccidentalReconciliation.reconcile(pasteReplace(
                line, FIRST_NOTE, SECOND_NOTE,
                List.of(note(G_STAFF_POSITION), note(A_STAFF_POSITION)),
                priors(null, null)));

            assertThat(accidentalChanges)
                .containsExactly(change(line, FOURTH_NOTE, StaffElement.Accidental.SHARP));
        }

        @Test
        void testDeletingTheNoteThatCarriedTheAccidentalMaterializesItOnTheInheritingNote() {
            var line = fixtureB();

            var accidentalChanges = AccidentalReconciliation.reconcile(new AccidentalReconciliation.InsertionRegion(
                line, FIRST_NOTE, new InsertionSpacingCalculator.DeletedRange(FIRST_NOTE, FIRST_NOTE),
                List.of(), List.of(), List.of()));

            assertThat(accidentalChanges)
                .containsExactly(change(line, FOURTH_NOTE, StaffElement.Accidental.SHARP));
        }

        @Test
        void testPasteReplaceThatBothRemovesAndAddsAnAccidentalAtOneStaffPosition() {
            // The sharp goes away and a flat arrives at the same staff position: the last F
            // inherited neither, so it still has to be pinned to what it sounded like.
            var line = fixtureB();

            var accidentalChanges = AccidentalReconciliation.reconcile(pasteReplace(
                line, FIRST_NOTE, FIRST_NOTE,
                List.of(note(F_STAFF_POSITION, StaffElement.Accidental.FLAT)),
                priors(StaffElement.Accidental.FLAT)));

            assertThat(accidentalChanges)
                .containsExactly(change(line, FOURTH_NOTE, StaffElement.Accidental.SHARP));
        }

        @Test
        void testNullBeforeMaterializesANaturalWhenTheDestinationKeyAltersThePitch() {
            // The cross-key paste: the B sounded unaltered in its source, and one flat in the
            // destination signature alters it. You cannot write "nothing" and get a natural.
            var line = lineOf(note(G_STAFF_POSITION));
            line.setKeyType(KeyType.FLATS);
            line.setKeyAccidentalCount(ONE_FLAT);
            var pastedB = note(B_STAFF_POSITION);

            var accidentalChanges = AccidentalReconciliation.reconcile(
                paste(line, SECOND_NOTE, List.of(pastedB), priors((StaffElement.Accidental) null)));

            assertThat(accidentalChanges).containsExactly(
                new AccidentalReconciliation.AccidentalChange(pastedB, StaffElement.Accidental.NATURAL));
        }

        @Test
        void testNoMaterializationWhenNullAndNaturalMeetBecauseTheySoundAlike() {
            // Comparison is by sounding adjustment, never by enum identity.
            assertThat(StaffElement.getPitchAdjustment(StaffElement.Accidental.NATURAL)).isEqualTo(0);

            var line = lineOf(
                note(F_STAFF_POSITION, StaffElement.Accidental.NATURAL), note(G_STAFF_POSITION),
                note(A_STAFF_POSITION), note(F_STAFF_POSITION));

            // Replacing the explicit natural with a plain G leaves the last F inheriting nothing,
            // which sounds exactly like the natural it inherited before.
            var accidentalChanges = AccidentalReconciliation.reconcile(pasteReplace(
                line, FIRST_NOTE, FIRST_NOTE, List.of(note(G_STAFF_POSITION)),
                priors((StaffElement.Accidental) null)));

            assertThat(accidentalChanges).isEmpty();
        }

        @Test
        void testNoMaterializationWhenFlatAndNaturalFlatMeetBecauseTheySoundAlike() {
            assertThat(StaffElement.getPitchAdjustment(StaffElement.Accidental.NATURAL_FLAT))
                .isEqualTo(StaffElement.getPitchAdjustment(StaffElement.Accidental.FLAT));

            var line = lineOf(
                note(F_STAFF_POSITION, StaffElement.Accidental.FLAT), note(G_STAFF_POSITION),
                note(A_STAFF_POSITION), note(F_STAFF_POSITION));

            var accidentalChanges = AccidentalReconciliation.reconcile(pasteReplace(
                line, FIRST_NOTE, FIRST_NOTE,
                List.of(note(F_STAFF_POSITION, StaffElement.Accidental.NATURAL_FLAT)),
                priors(StaffElement.Accidental.NATURAL_FLAT)));

            assertThat(accidentalChanges).isEmpty();
        }

        @Test
        void testAFreshlyInsertedNoteIsNeverMaterializedAgainstItself() {
            // An empty prior-accidental list says the element has no source context: a note the
            // user is creating has no pitch it "had", so the invariant does not reach it. The
            // notes after it are still candidates, which the sharp it carries proves here.
            var line = fixtureA();
            var insertedSharpF = note(F_STAFF_POSITION, StaffElement.Accidental.SHARP);

            var accidentalChanges = AccidentalReconciliation.reconcile(insert(line, SECOND_NOTE, insertedSharpF));

            assertThat(accidentalChanges)
                .containsExactly(change(line, FOURTH_NOTE, StaffElement.Accidental.NATURAL));
        }
    }

    // -----------------------------------------------------------------------
    // In-place modification: accidental toggles and pitch shifts
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class ReconcileModification {

        @Test
        void testTogglingAnAccidentalOffMaterializesItOnTheInheritingNote() {
            var line = fixtureB();

            var accidentalChanges = AccidentalReconciliation.reconcileModification(
                line, toggle(FIRST_NOTE, null, F_STAFF_POSITION));

            assertThat(accidentalChanges)
                .containsExactly(change(line, FOURTH_NOTE, StaffElement.Accidental.SHARP));
        }

        @Test
        void testTogglingAnAccidentalOnMaterializesANaturalOnTheFollowingNote() {
            var line = fixtureA();

            var accidentalChanges = AccidentalReconciliation.reconcileModification(
                line, toggle(FIRST_NOTE, StaffElement.Accidental.FLAT, F_STAFF_POSITION));

            assertThat(accidentalChanges)
                .containsExactly(change(line, FOURTH_NOTE, StaffElement.Accidental.NATURAL));
        }

        @Test
        void testAStaffPositionVacatedByAPitchShiftMaterializesOnTheNoteThatInheritedFromIt() {
            // Dragging the sharpened F away to E takes its accidental with it.
            var line = fixtureB();

            var accidentalChanges = AccidentalReconciliation.reconcileModification(
                line, toggle(FIRST_NOTE, null, E_STAFF_POSITION));

            assertThat(accidentalChanges)
                .containsExactly(change(line, FOURTH_NOTE, StaffElement.Accidental.SHARP));
        }

        @Test
        void testTheChangedNoteItselfIsNeverInTheResult() {
            // The last F inherits the flat and the user clears its own accidental: it is supposed
            // to change, so it is never protected against its own edit.
            var line = lineOf(
                note(F_STAFF_POSITION, StaffElement.Accidental.FLAT), note(G_STAFF_POSITION),
                note(F_STAFF_POSITION, StaffElement.Accidental.NATURAL));

            var accidentalChanges = AccidentalReconciliation.reconcileModification(
                line, toggle(THIRD_NOTE, null, F_STAFF_POSITION));

            assertThat(accidentalChanges).isEmpty();
        }

        @Test
        void testNoChangesWhenNothingIsChanged() {
            assertThat(AccidentalReconciliation.reconcileModification(fixtureB(), List.of())).isEmpty();
        }
    }

    // -----------------------------------------------------------------------
    // Removal — the mirror rule
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class Dematerialization {

        /** F♭ G F♮ — the state a toggle-on leaves behind, ready for the toggle-off. */
        private Line flattenedFThenMaterializedNatural() {
            return lineOf(
                note(F_STAFF_POSITION, StaffElement.Accidental.FLAT), note(G_STAFF_POSITION),
                note(F_STAFF_POSITION, StaffElement.Accidental.NATURAL));
        }

        @Test
        void testTogglingTheAccidentalBackOffRemovesTheNaturalItMaterialized() {
            // Without this the reconciliation is add-only: the pitch would be right and the
            // notation stranded.
            var line = flattenedFThenMaterializedNatural();

            var accidentalChanges = AccidentalReconciliation.reconcileModification(
                line, toggle(FIRST_NOTE, null, F_STAFF_POSITION));

            assertThat(accidentalChanges).containsExactly(change(line, THIRD_NOTE, null));
        }

        @Test
        void testAParenthesizedAccidentalIsRemovedLikeAnyOther() {
            // Parentheses record that the notator wrote something they did not have to, which says
            // nothing about whether a later edit obviated it.
            var line = flattenedFThenMaterializedNatural();
            line.getElement(THIRD_NOTE).setAccidentalInParentheses(true);

            var accidentalChanges = AccidentalReconciliation.reconcileModification(
                line, toggle(FIRST_NOTE, null, F_STAFF_POSITION));

            assertThat(accidentalChanges).containsExactly(change(line, THIRD_NOTE, null));
        }

        @Test
        void testNoRemovalWhenTheEditDidNotMoveTheContextArrivingAtTheAccidental() {
            // The sharp lands on the G, so nothing about the context reaching the last F moved —
            // its natural stays, redundant or not.
            var line = lineOf(
                note(F_STAFF_POSITION), note(G_STAFF_POSITION),
                note(F_STAFF_POSITION, StaffElement.Accidental.NATURAL));

            var accidentalChanges = AccidentalReconciliation.reconcileModification(
                line, toggle(SECOND_NOTE, StaffElement.Accidental.SHARP, G_STAFF_POSITION));

            assertThat(accidentalChanges).isEmpty();
        }

        @Test
        void testAnAccidentalAlreadyRedundantBeforeTheEditIsNeverRemoved() {
            // F♭ G F♭ — the second flat is a restatement, already redundant when it was written.
            // "Already redundant" and "removable" are algebraically incompatible, which is what
            // makes removal safe to apply unattended.
            var line = lineOf(
                note(F_STAFF_POSITION, StaffElement.Accidental.FLAT), note(G_STAFF_POSITION),
                note(F_STAFF_POSITION, StaffElement.Accidental.FLAT));

            var accidentalChanges = AccidentalReconciliation.reconcileModification(
                line, toggle(FIRST_NOTE, null, F_STAFF_POSITION));

            assertThat(accidentalChanges).isEmpty();
            assertThat(line.getElement(THIRD_NOTE).getAccidental())
                .isEqualTo(StaffElement.Accidental.FLAT);
        }

        @Test
        void testASharpKeySignatureReassertingItselfRemovesTheSharpItMakesRedundant() {
            // The only case that pins the sharp side of the key fallback: the last F's own sharp
            // becomes redundant precisely because the key puts a sharp back in effect once the
            // natural cancelling it is gone. A fallback answering with a flat would leave it.
            var line = lineOf(
                note(F_STAFF_POSITION, StaffElement.Accidental.NATURAL), note(G_STAFF_POSITION),
                note(F_STAFF_POSITION, StaffElement.Accidental.SHARP));
            line.setKeyType(KeyType.SHARPS);
            line.setKeyAccidentalCount(ONE_SHARP);

            var accidentalChanges = AccidentalReconciliation.reconcileModification(
                line, toggle(FIRST_NOTE, null, F_STAFF_POSITION));

            assertThat(accidentalChanges).containsExactly(change(line, THIRD_NOTE, null));
        }

        @Test
        void testAPastedNoteCarryingAnExplicitAccidentalIsNeverDematerialized() {
            // The pasted F♯ is redundant the moment it lands, but a fragment carries semantic
            // content: it keeps the notation it arrived with.
            var line = lineOf(note(F_STAFF_POSITION, StaffElement.Accidental.SHARP), note(G_STAFF_POSITION));
            var pastedSharpF = note(F_STAFF_POSITION, StaffElement.Accidental.SHARP);

            var accidentalChanges = AccidentalReconciliation.reconcile(
                paste(line, THIRD_NOTE, List.of(pastedSharpF), priors(StaffElement.Accidental.SHARP)));

            assertThat(accidentalChanges).isEmpty();
        }
    }

    // -----------------------------------------------------------------------
    // Barriers — barlines and repeats, both standing and newly inserted
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class Barriers {

        @Test
        void testABarlineBetweenTheEditAndTheCandidateStopsTheEffect() {
            // F♯ G | A F — the last F already resolved to the key across the barline, so removing
            // the sharp cannot change it.
            var line = lineOf(
                note(F_STAFF_POSITION, StaffElement.Accidental.SHARP), note(G_STAFF_POSITION),
                ElementType.SINGLE_BARLINE.newInstance(), note(A_STAFF_POSITION), note(F_STAFF_POSITION));

            var accidentalChanges = AccidentalReconciliation.reconcileModification(
                line, toggle(FIRST_NOTE, null, F_STAFF_POSITION));

            assertThat(accidentalChanges).isEmpty();
        }

        @Test
        void testInsertingABarlineMaterializesTheAccidentalItCancels() {
            // Fixture B with a barline inserted before the last F: the barline cancels the sharp
            // the F had been inheriting, so the sharp has to be written onto the F itself.
            var line = fixtureB();

            var accidentalChanges = AccidentalReconciliation.reconcile(
                insert(line, FOURTH_NOTE, ElementType.SINGLE_BARLINE.newInstance()));

            assertThat(accidentalChanges)
                .containsExactly(change(line, FOURTH_NOTE, StaffElement.Accidental.SHARP));
        }

        @Test
        void testInsertingARepeatMaterializesTheAccidentalItCancels() {
            var line = fixtureB();

            var accidentalChanges = AccidentalReconciliation.reconcile(
                insert(line, FOURTH_NOTE, ElementType.REPEAT_LEFT.newInstance()));

            assertThat(accidentalChanges)
                .containsExactly(change(line, FOURTH_NOTE, StaffElement.Accidental.SHARP));
        }

        @Test
        void testInsertingABreathMarkMaterializesNothing() {
            // Pins the barrier set to barlines and repeats rather than "any non-duration element":
            // a breath mark cancels nothing, so the last F goes on inheriting the sharp.
            var line = fixtureB();

            var accidentalChanges = AccidentalReconciliation.reconcile(
                insert(line, FOURTH_NOTE, ElementType.BREATH_MARK.newInstance()));

            assertThat(accidentalChanges).isEmpty();
        }

        // Only the engine is covered here. The call-site half — that inserting a barline reconciles
        // at all — runs through PreviewElementManager.materializeAndCalculateInsertion, which is
        // private and needs a live LineComponent and layout result, so it lives in
        // songscribe.e2e.AccidentalContextTest instead.
    }

    // -----------------------------------------------------------------------
    // Ties
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class TieExclusion {

        /**
         * Two bare A crotchets, tied when {@code tied}. Neither carries an accidental and there is
         * no key signature, so both sound A natural before any edit.
         */
        private Line twoAsInARow(boolean tied) {
            var first = note(A_STAFF_POSITION);
            var second = note(A_STAFF_POSITION);
            var line = lineOf(first, second);

            if (tied) {
                line.addRangeElement(new Tie(first, second));
            }

            return line;
        }

        /**
         * A tie asserts the two notes are one sounding pitch, so the second note is supposed to
         * follow the first — writing a natural on it would notate a pitch the tie forbids.
         */
        @Test
        void testFlatOnATiedNoteMaterializesNothingOnTheNoteItIsTiedTo() {
            var line = twoAsInARow(true);

            var accidentalChanges = AccidentalReconciliation.reconcileModification(
                line, toggle(FIRST_NOTE, StaffElement.Accidental.FLAT, A_STAFF_POSITION));

            assertThat(accidentalChanges).isEmpty();
        }

        /**
         * The control for the test above: with no tie the second note is an independent A natural,
         * so the flat arriving in front of it must be answered with a materialized natural.
         * Without this, a reconciliation that had simply stopped emitting would pass the tied case
         * for the wrong reason.
         */
        @Test
        void testFlatOnAnUntiedNoteMaterializesANaturalOnTheFollowingNote() {
            var line = twoAsInARow(false);

            var accidentalChanges = AccidentalReconciliation.reconcileModification(
                line, toggle(FIRST_NOTE, StaffElement.Accidental.FLAT, A_STAFF_POSITION));

            assertThat(accidentalChanges)
                .containsExactly(change(line, SECOND_NOTE, StaffElement.Accidental.NATURAL));
        }

        /**
         * A♭ ~ A♮ is contradictory notation, and that is the point: it is exactly what a version
         * without this exclusion would have written into a saved file, by materializing a natural
         * onto the tied note. Clearing the anchor's flat is the one edit that satisfies both
         * removal conditions on that natural — the context reaching it moves from flat to nothing,
         * and its own natural then sounds like that nothing — so this is the only shape in which
         * the exclusion is what stops the removal rather than the arithmetic.
         */
        @Test
        void testATiedNoteIsNotDematerializedEither() {
            var first = note(A_STAFF_POSITION, StaffElement.Accidental.FLAT);
            var second = note(A_STAFF_POSITION, StaffElement.Accidental.NATURAL);
            var line = lineOf(first, second);
            line.addRangeElement(new Tie(first, second));

            var accidentalChanges = AccidentalReconciliation.reconcileModification(
                line, toggle(FIRST_NOTE, null, A_STAFF_POSITION));

            assertThat(accidentalChanges).isEmpty();
        }

        /**
         * The case the deleted barrier escape existed for. The tied note used to need the scan to
         * climb back through the tie to see the flat; now it is simply never a candidate, so the
         * scan stopping dead at the barline costs nothing.
         */
        @Test
        void testATieCrossingABarlineStillNeedsNothingWrittenOnTheTiedNote() {
            var first = note(A_STAFF_POSITION, StaffElement.Accidental.FLAT);
            var second = note(A_STAFF_POSITION);
            var line = lineOf(first, ElementType.SINGLE_BARLINE.newInstance(), second);
            line.addRangeElement(new Tie(first, second));

            var accidentalChanges = AccidentalReconciliation.reconcileModification(
                line, toggle(FIRST_NOTE, null, A_STAFF_POSITION));

            assertThat(accidentalChanges).isEmpty();
        }

        /**
         * Deleting a tie's anchor leaves nothing for the tied note to follow, so it stops being
         * exempt and has to be pinned to the pitch it had — the tie is gone with the anchor.
         */
        @Test
        void testDeletingATiesAnchorPinsThePitchOnTheNoteThatWasTiedToIt() {
            var first = note(A_STAFF_POSITION, StaffElement.Accidental.FLAT);
            var second = note(A_STAFF_POSITION);
            var line = lineOf(first, second);
            line.addRangeElement(new Tie(first, second));

            var accidentalChanges = AccidentalReconciliation.reconcile(new AccidentalReconciliation.InsertionRegion(
                line, FIRST_NOTE, new InsertionSpacingCalculator.DeletedRange(FIRST_NOTE, FIRST_NOTE),
                List.of(), List.of(), List.of()));

            assertThat(accidentalChanges)
                .containsExactly(change(line, SECOND_NOTE, StaffElement.Accidental.FLAT));
        }

        /**
         * A fragment's own tie is not on the destination line yet, so the exclusion only sees it if
         * the region carries it. The fragment reads A♭ | A: the tie carries the flat over the
         * fragment's own barline, so the second A needs nothing written on it.
         */
        @Test
        void testATieArrivingWithAPastedFragmentExemptsTheNoteItEndsOn() {
            var line = lineOf(note(G_STAFF_POSITION));
            var pastedFlatA = note(A_STAFF_POSITION, StaffElement.Accidental.FLAT);
            var pastedTiedA = note(A_STAFF_POSITION);

            var accidentalChanges = AccidentalReconciliation.reconcile(pasteWithSpans(
                line, FIRST_NOTE,
                List.of(pastedFlatA, ElementType.SINGLE_BARLINE.newInstance(), pastedTiedA),
                priors(StaffElement.Accidental.FLAT, null, StaffElement.Accidental.FLAT),
                List.of(new Tie(pastedFlatA, pastedTiedA))));

            assertThat(accidentalChanges).isEmpty();
        }

        /**
         * The control for the test above: the same fragment spanned by a crescendo instead of a
         * tie. A non-tie span carries no accidental, so the second A still has a pitch of its own
         * to protect, and the fragment's barline cancels the flat it had been inheriting in its
         * source — the flat has to be written onto it.
         */
        @Test
        void testANonTieSpanArrivingWithAFragmentExemptsNothing() {
            var line = lineOf(note(G_STAFF_POSITION));
            var pastedFlatA = note(A_STAFF_POSITION, StaffElement.Accidental.FLAT);
            var pastedUntiedA = note(A_STAFF_POSITION);

            var accidentalChanges = AccidentalReconciliation.reconcile(pasteWithSpans(
                line, FIRST_NOTE,
                List.of(pastedFlatA, ElementType.SINGLE_BARLINE.newInstance(), pastedUntiedA),
                priors(StaffElement.Accidental.FLAT, null, StaffElement.Accidental.FLAT),
                List.of(new Crescendo(pastedFlatA, pastedUntiedA))));

            assertThat(accidentalChanges).containsExactly(
                new AccidentalReconciliation.AccidentalChange(
                    pastedUntiedA, StaffElement.Accidental.FLAT));
        }
    }

    // -----------------------------------------------------------------------
    // Restatement scan and propagation suppression (#681)
    // -----------------------------------------------------------------------

    /**
     * The worked example of the #681 plan, in D♭ major — five flats, so F is unaltered by the key
     * and every flat on an F is explicit:
     *
     * <pre>
     * line 1:  F♭  G   F♭
     * line 2:  F♭  A   F   F♮
     * </pre>
     *
     * Toggling the flat off {@code 1:0} finds {@code 1:2} and {@code 2:0} as restatements. The
     * point of the fixture is {@code 2:0}: it is not redundant on its own line, because accidental
     * context resets at the line boundary, which is exactly why no arithmetic can identify it.
     */
    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class Restatements {

        /** D♭ major: B E A D G are flattened, so an F needs an explicit flat to sound flat. */
        private static final int FIVE_FLATS = 5;

        private Line firstLine = detachedLine();
        private Line secondLine = detachedLine();
        private Song song = minimalSongMock();

        @BeforeEach
        void buildWorkedExample() {
            song = minimalSongMock();
            firstLine = flatKeyLine(
                note(F_STAFF_POSITION, StaffElement.Accidental.FLAT),
                note(G_STAFF_POSITION),
                note(F_STAFF_POSITION, StaffElement.Accidental.FLAT));
            secondLine = flatKeyLine(
                note(F_STAFF_POSITION, StaffElement.Accidental.FLAT),
                note(A_STAFF_POSITION),
                note(F_STAFF_POSITION),
                note(F_STAFF_POSITION, StaffElement.Accidental.NATURAL));

            registerLines(song, firstLine, secondLine);
        }

        private static Line flatKeyLine(StaffElement... elements) {
            var line = lineOf(elements);
            line.setKeyType(KeyType.FLATS);
            line.setKeyAccidentalCount(FIVE_FLATS);
            return line;
        }

        /** Teaches the song mock the line list the scan walks. */
        private static void registerLines(Song song, Line... lines) {
            when(song.lineCount()).thenReturn(lines.length);

            for (var index = 0; index < lines.length; index++) {
                when(song.getLine(index)).thenReturn(lines[index]);
                when(song.indexOfLine(lines[index])).thenReturn(index);
            }
        }

        private List<AccidentalReconciliation.Restatement> scanFromFirstFlat() {
            return AccidentalReconciliation.findRestatements(
                song, firstLine, FIRST_NOTE, F_STAFF_POSITION, StaffElement.Accidental.FLAT,
                Set.of(firstLine.getElement(FIRST_NOTE)));
        }

        /** The removal the user accepted: both restatements, with the F position suppressed. */
        private AccidentalReconciliation.RestatementRemoval acceptedRemoval() {
            return new AccidentalReconciliation.RestatementRemoval(
                Set.of(firstLine.getElement(THIRD_NOTE), secondLine.getElement(FIRST_NOTE)),
                Set.of(F_STAFF_POSITION));
        }

        @Test
        void testScanCollectsMatchingAccidentalsAcrossLines() {
            assertThat(scanFromFirstFlat()).containsExactly(
                new AccidentalReconciliation.Restatement(firstLine, firstLine.getElement(THIRD_NOTE)),
                new AccidentalReconciliation.Restatement(secondLine, secondLine.getElement(FIRST_NOTE)));
        }

        @Test
        void testScanStopsAtAnExplicitCancellation() {
            // A natural on the F at 1:2 cancels the flat, so nothing past it restates anything —
            // not even the flat still sitting at 2:0.
            firstLine.getElement(THIRD_NOTE).setAccidental(StaffElement.Accidental.NATURAL);

            assertThat(scanFromFirstFlat()).isEmpty();
        }

        @Test
        void testScanIgnoresAnAccidentalAtAnotherStaffPosition() {
            // Move 1:2's flat to the G line. A flat somewhere else says nothing about the F flat
            // being removed, so only 2:0 is left to offer.
            firstLine.getElement(THIRD_NOTE).setStaffPosition(G_STAFF_POSITION);

            assertThat(scanFromFirstFlat()).containsExactly(new AccidentalReconciliation.Restatement(
                secondLine, secondLine.getElement(FIRST_NOTE)));
        }

        @Test
        void testScanKeepsWhatItFoundBeforeStoppingAtADifferentAdjustment() {
            // Clear 2:3's natural so the scan runs past it, then put a sharp and a flat at the F
            // position after it. The sharp is a fresh decision, so the scan stops there — but the
            // two restatements it had already collected still come back, and the flat past the
            // sharp does not.
            secondLine.getElement(FOURTH_NOTE).setAccidental(null);
            secondLine.addElement(note(F_STAFF_POSITION, StaffElement.Accidental.SHARP));
            secondLine.addElement(note(F_STAFF_POSITION, StaffElement.Accidental.FLAT));

            assertThat(scanFromFirstFlat()).containsExactly(
                new AccidentalReconciliation.Restatement(firstLine, firstLine.getElement(THIRD_NOTE)),
                new AccidentalReconciliation.Restatement(secondLine, secondLine.getElement(FIRST_NOTE)));
        }

        @Test
        void testScanSkipsTheNotesTheEditItselfRemoves() {
            // A range delete taking both flats on line 1 must not offer the second one back: it is
            // going away regardless, and it must not stand in for a cancellation either.
            var excluded = Set.of(firstLine.getElement(FIRST_NOTE), firstLine.getElement(THIRD_NOTE));

            assertThat(AccidentalReconciliation.findRestatements(
                song, firstLine, FIRST_NOTE, F_STAFF_POSITION, StaffElement.Accidental.FLAT, excluded))
                .containsExactly(new AccidentalReconciliation.Restatement(
                    secondLine, secondLine.getElement(FIRST_NOTE)));
        }

        @Test
        void testAnExcludedCancellationCannotStopTheScan() {
            // 1:2 now cancels the flat instead of restating it — but the same edit deletes it, so
            // it will not be there to cancel anything and 2:0 must still be offered.
            firstLine.getElement(THIRD_NOTE).setAccidental(StaffElement.Accidental.NATURAL);

            var excluded = Set.of(firstLine.getElement(FIRST_NOTE), firstLine.getElement(THIRD_NOTE));

            assertThat(AccidentalReconciliation.findRestatements(
                song, firstLine, FIRST_NOTE, F_STAFF_POSITION, StaffElement.Accidental.FLAT, excluded))
                .containsExactly(new AccidentalReconciliation.Restatement(
                    secondLine, secondLine.getElement(FIRST_NOTE)));
        }

        @Test
        void testYesClearsTheRestatementOnTheEditedLineAndSuppressesMaterialization() {
            // Give line 1 a fourth note, a bare F after the accepted restatement. Without
            // suppression that F — which the removed flat was covering — would be materialized to
            // a flat, handing the accidental straight back and defeating the removal.
            firstLine.addElement(note(F_STAFF_POSITION));

            var accidentalChanges = AccidentalReconciliation.reconcileModification(
                firstLine,
                toggle(FIRST_NOTE, null, F_STAFF_POSITION),
                acceptedRemoval());

            assertThat(accidentalChanges).containsExactly(change(firstLine, THIRD_NOTE, null));
        }

        @Test
        void testSuppressionAppliesOnlyAtTheRemovedAccidentalsStaffPosition() {
            // A note the user did not touch, at a staff position nothing was removed from, must
            // still be protected. Toggling the G sharp off moves the context at the G line, so the
            // bare G has to be pinned to the sharp it used to sound — while the bare F, inside the
            // consented region, is left to change.
            var line = lineOf(
                note(G_STAFF_POSITION, StaffElement.Accidental.SHARP),
                note(F_STAFF_POSITION, StaffElement.Accidental.FLAT),
                note(G_STAFF_POSITION),
                note(F_STAFF_POSITION));

            var accidentalChanges = AccidentalReconciliation.reconcileModification(
                line,
                toggle(FIRST_NOTE, null, G_STAFF_POSITION),
                new AccidentalReconciliation.RestatementRemoval(
                    Set.of(line.getElement(SECOND_NOTE)), Set.of(F_STAFF_POSITION)));

            assertThat(accidentalChanges).containsExactly(
                change(line, SECOND_NOTE, null),
                change(line, THIRD_NOTE, StaffElement.Accidental.SHARP));
        }

        @Test
        void testYesClearsTheRestatementOnALaterLineAndStillRemovesTheStrandedNatural() {
            // 2:0's flat goes because the user said so; 2:3's natural goes because the ordinary
            // mirror rule finds it redundant once the flat is gone. Removal runs at a suppressed
            // position — only materialization is suspended, which is what spares the bare 2:2.
            var accidentalChanges = AccidentalReconciliation.reconcileModification(
                secondLine, List.of(), acceptedRemoval());

            assertThat(accidentalChanges).containsExactly(
                change(secondLine, FIRST_NOTE, null),
                change(secondLine, FOURTH_NOTE, null));
        }

        @Test
        void testSuppressionEndsAtTheNextExplicitAccidentalAtThatStaffPosition() {
            // Past an explicit accidental at the suppressed position the consented pitch change is
            // over and the ordinary invariant applies again. Give line 2 a fifth note — a bare F
            // after the natural at 2:3 — and have the same edit change that natural to a flat.
            // 2:2 is still spared, because it is inside the consented region; 2:4 is not, because
            // 2:3's accidental ended it, so it has to be pinned to the natural it used to sound.
            secondLine.addElement(note(F_STAFF_POSITION));

            var accidentalChanges = AccidentalReconciliation.reconcileModification(
                secondLine,
                toggle(FOURTH_NOTE, StaffElement.Accidental.FLAT, F_STAFF_POSITION),
                acceptedRemoval());

            assertThat(accidentalChanges).containsExactly(
                change(secondLine, FIRST_NOTE, null),
                change(secondLine, FIFTH_NOTE, StaffElement.Accidental.NATURAL));
        }

        @Test
        void testNoLeavesEveryRestatementAlone() {
            // The same toggle with no removal accepted: the mirror rule alone reaches nothing on
            // line 1, because 1:2's flat was already redundant when it was written.
            assertThat(AccidentalReconciliation.reconcileModification(
                firstLine, toggle(FIRST_NOTE, null, F_STAFF_POSITION))).isEmpty();

            // ...and line 2 is not reconciled at all, since the edit does not touch it.
            assertThat(AccidentalReconciliation.reconcileModification(secondLine, List.of())).isEmpty();
        }

        /** Deleting {@code 1:0}, the note carrying the flat the whole example turns on. */
        private AccidentalReconciliation.InsertionRegion deleteTheFirstFlat() {
            return new AccidentalReconciliation.InsertionRegion(
                firstLine, FIRST_NOTE,
                new InsertionSpacingCalculator.DeletedRange(FIRST_NOTE, FIRST_NOTE),
                List.of(), List.of(), List.of());
        }

        @Test
        void testYesClearsTheRestatementAndSuppressesMaterializationForADeletion() {
            // The same consent, reached through the region-based reconciliation that the range
            // delete, the cut and the paste-replace all run — a different walk from the in-place
            // one above, because the deleted note leaves the projected sequence entirely.
            //
            // Line 1 gains a bare F after the accepted restatement: without suppression it would
            // be materialized to a flat and hand the accidental straight back.
            firstLine.addElement(note(F_STAFF_POSITION));

            assertThat(AccidentalReconciliation.reconcile(deleteTheFirstFlat(), acceptedRemoval()))
                .containsExactly(change(firstLine, THIRD_NOTE, null));
        }

        @Test
        void testTheSameDeletionWithNoConsentLeavesTheRestatementInPlace() {
            // Answering No must leave the deletion behaving exactly as it did before this feature:
            // 1:2's flat stays, and the bare F after it keeps sounding flat through that flat, so
            // nothing has to change at all.
            firstLine.addElement(note(F_STAFF_POSITION));

            assertThat(AccidentalReconciliation.reconcile(deleteTheFirstFlat())).isEmpty();
        }

        @Test
        void testAPasteReplaceCarriesTheConsentToo() {
            // The paste-replace variant: the deleted note is replaced by a G rather than simply
            // going away, so the sequence holds an inserted element the walk must resolve past
            // before it reaches the accepted restatement.
            firstLine.addElement(note(F_STAFF_POSITION));

            var accidentalChanges = AccidentalReconciliation.reconcile(
                pasteReplace(firstLine, FIRST_NOTE, FIRST_NOTE, List.of(note(G_STAFF_POSITION)), priors()),
                acceptedRemoval());

            assertThat(accidentalChanges).containsExactly(change(firstLine, THIRD_NOTE, null));
        }
    }

    // -----------------------------------------------------------------------
    // Construction contracts
    // -----------------------------------------------------------------------

    @Test
    void testInsertionRegionRejectsAPriorAccidentalListThatIsNeitherEmptyNorParallel() {
        var line = fixtureA();
        var inserted = List.of(note(F_STAFF_POSITION), note(G_STAFF_POSITION));

        assertThatIllegalArgumentException().isThrownBy(() -> new AccidentalReconciliation.InsertionRegion(
            line, FIRST_NOTE, null, inserted, priors(StaffElement.Accidental.SHARP), List.of()));
    }
}
