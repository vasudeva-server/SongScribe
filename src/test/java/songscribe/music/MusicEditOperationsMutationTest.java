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
package songscribe.music;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;
import static songscribe.music.StaffElementFactory.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.jspecify.annotations.Nullable;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import songscribe.UnitTest;
import songscribe.message.Message;
import songscribe.message.MessageCenter;
import songscribe.message.mutation.BeamingAddition;
import songscribe.message.mutation.BeamingRemoval;
import songscribe.message.mutation.CrescendoAddition;
import songscribe.message.mutation.CrescendoRemoval;
import songscribe.message.mutation.DiminuendoAddition;
import songscribe.message.mutation.DiminuendoRemoval;
import songscribe.message.mutation.ElementField;
import songscribe.message.mutation.ElementInsertion;
import songscribe.message.mutation.ElementModification;
import songscribe.message.mutation.RangeElementAddition;
import songscribe.message.mutation.TieAddition;
import songscribe.message.mutation.TieRemoval;
import songscribe.message.mutation.TupletAddition;
import songscribe.message.mutation.TupletRemoval;
import songscribe.message.notification.CompositionDidChangeNotification;
import songscribe.ui.MusicEditOperations;
import songscribe.ui.action.TupletAction;
import songscribe.ui.layout.Ending;
import songscribe.ui.selection.ReflectionTestHelper;
import songscribe.ui.selection.SelectionCoordinator;

class MusicEditOperationsMutationTest extends UnitTest {

    private Composition composition;
    @Nullable private MockedStatic<MessageCenter> messageCenterMock;

    @BeforeEach
    void setUp() {
        // Construct before mocking so constructor-internal bus interactions
        // go to the real (unobserved) bus, not the mock.
        composition = new Composition();
    }

    @AfterEach
    void tearDown() {
        if (messageCenterMock != null) {
            messageCenterMock.close();
        }
    }

    // -----------------------------------------------------------------------
    // Setup helper
    // -----------------------------------------------------------------------

    private record Env(SelectionCoordinator coordinator, MusicEditOperations operations, Line line) {}

    /**
     * Builds a Line with the given elements, attaches it to the composition (which fires a
     * real LineInsertion notification on the unobserved bus), creates a coordinator and
     * operations wrapper, then starts mocking MessageCenter.
     *
     * <p>Span state (beams, ties, etc.) may be added to {@code env.line()} directly after
     * this call — {@link SpanSet#addSpan} bypasses {@code applyChange}
     * and does not require an open modification bracket.
     */
    private Env setup(StaffElement... elements) {
        var line = new Line();

        for (var element : elements) {
            line.addElement(element);
        }

        // addLine fires a real LineInsertion notification on the pre-mock bus.
        // Use withoutMutationTracking so the final-barline invariant is not enforced
        // during setup — tests need to build lines with arbitrary terminal elements.
        composition.withoutMutationTracking(() -> composition.addLine(line));
        var coordinator = ReflectionTestHelper.createCoordinatorForLine(line);
        var ops = new MusicEditOperations(composition, coordinator);
        messageCenterMock = mockStatic(MessageCenter.class);
        return new Env(coordinator, ops, line);
    }

    // -----------------------------------------------------------------------
    // Notification capture helper
    // -----------------------------------------------------------------------

    private CompositionDidChangeNotification captureSingleDidChange() {
        var mock = messageCenterMock;

        if (mock == null) {
            throw new IllegalStateException("messageCenterMock not set — call setup() first");
        }

        var captor = ArgumentCaptor.forClass(Message.class);
        mock.verify(() -> MessageCenter.post(captor.capture()));
        var didChanges = captor.getAllValues().stream()
            .filter(m -> m instanceof CompositionDidChangeNotification)
            .map(m -> (CompositionDidChangeNotification) m)
            .toList();

        assertThat(didChanges)
            .as("expected exactly one CompositionDidChangeNotification, got: %s", didChanges)
            .hasSize(1);

        return didChanges.get(0);
    }

    // -----------------------------------------------------------------------
    // Beaming
    // -----------------------------------------------------------------------

    @Test
    void testToggleBeamingAddEmitsBeamingAddition() {
        var env = setup(quaver(), quaver(), quaver());
        ReflectionTestHelper.selectRange(env.coordinator(), 0, 2);
        env.operations().toggleBeaming();

        var notification = captureSingleDidChange();
        var mutations = notification.getMutations();
        assertThat(mutations).hasSize(1);
        assertThat(mutations.get(0)).isInstanceOf(BeamingAddition.class);
        var addition = (BeamingAddition) mutations.get(0);
        assertThat(addition.span().start).isEqualTo(0);
        assertThat(addition.span().end).isEqualTo(2);
        assertThat(addition.line()).isSameAs(env.line());
    }

    @Test
    void testToggleBeamingRemoveEmitsBeamingRemoval() {
        var env = setup(quaver(), quaver(), quaver());
        env.line().getBeamings().addSpan(new BeamSpan(0, 2));
        ReflectionTestHelper.selectRange(env.coordinator(), 0, 2);
        env.operations().toggleBeaming();

        var notification = captureSingleDidChange();
        var mutations = notification.getMutations();
        assertThat(mutations).hasSize(1);
        assertThat(mutations.get(0)).isInstanceOf(BeamingRemoval.class);
        assertThat(((BeamingRemoval) mutations.get(0)).line()).isSameAs(env.line());
    }

    // -----------------------------------------------------------------------
    // Tie
    // -----------------------------------------------------------------------

    @Test
    void testToggleTieAddEmitsTieAddition() {
        var env = setup(crotchet(), crotchet());
        ReflectionTestHelper.selectRange(env.coordinator(), 0, 1);
        env.operations().toggleTie();

        var notification = captureSingleDidChange();
        var mutations = notification.getMutations();
        assertThat(mutations).hasSize(1);
        assertThat(mutations.get(0)).isInstanceOf(TieAddition.class);
        var addition = (TieAddition) mutations.get(0);
        assertThat(addition.span().start).isEqualTo(0);
        assertThat(addition.span().end).isEqualTo(1);
        assertThat(addition.line()).isSameAs(env.line());
    }

    @Test
    void testToggleTieRemoveEmitsTieRemoval() {
        var env = setup(crotchet(), crotchet());
        env.line().getTies().addSpan(new TieSpan(0, 1));
        ReflectionTestHelper.selectRange(env.coordinator(), 0, 1);
        env.operations().toggleTie();

        var notification = captureSingleDidChange();
        var mutations = notification.getMutations();
        assertThat(mutations).hasSize(1);
        assertThat(mutations.get(0)).isInstanceOf(TieRemoval.class);
        assertThat(((TieRemoval) mutations.get(0)).line()).isSameAs(env.line());
    }

    // -----------------------------------------------------------------------
    // Tuplet
    // -----------------------------------------------------------------------

    @Test
    void testToggleTupletAddEmitsTupletAddition() {
        var env = setup(crotchet(), crotchet(), crotchet());
        ReflectionTestHelper.selectRange(env.coordinator(), 0, 2);
        env.operations().toggleTuplet(TupletAction.Tuplet.TRIPLET.getSize(), env.operations().canToggleTuplet());

        var notification = captureSingleDidChange();
        var mutations = notification.getMutations();
        assertThat(mutations).hasSize(1);
        assertThat(mutations.get(0)).isInstanceOf(TupletAddition.class);
        var addition = (TupletAddition) mutations.get(0);
        assertThat(addition.span().start).isEqualTo(0);
        assertThat(addition.span().end).isEqualTo(2);
        assertThat(addition.span().getGrade()).isEqualTo(TupletAction.Tuplet.TRIPLET.getSize());
        assertThat(addition.line()).isSameAs(env.line());
    }

    @Test
    void testToggleTupletRemoveEmitsTupletRemoval() {
        var env = setup(crotchet(), crotchet(), crotchet());
        env.line().getTuplets().addSpan(new TupletSpan(0, 2, TupletAction.Tuplet.TRIPLET.getSize()));
        ReflectionTestHelper.selectRange(env.coordinator(), 0, 2);
        env.operations().toggleTuplet(TupletAction.Tuplet.TRIPLET.getSize(), env.operations().canToggleTuplet());

        var notification = captureSingleDidChange();
        var mutations = notification.getMutations();
        assertThat(mutations).hasSize(1);
        assertThat(mutations.get(0)).isInstanceOf(TupletRemoval.class);
        assertThat(((TupletRemoval) mutations.get(0)).line()).isSameAs(env.line());
    }

    @Test
    void testToggleTupletGradeChangeEmitsRemovalThenAddition() {
        // With the full tuplet span selected, calling toggleTuplet with a
        // different grade must remove the existing span and add the new one
        // inside a single bracket — one notification, two mutations, undo replays
        // both atomically.
        var env = setup(crotchet(), crotchet(), crotchet());
        env.line().getTuplets().addSpan(new TupletSpan(0, 2, TupletAction.Tuplet.TRIPLET.getSize()));
        ReflectionTestHelper.selectRange(env.coordinator(), 0, 2);
        env.operations().toggleTuplet(TupletAction.Tuplet.QUINTUPLET.getSize(), env.operations().canToggleTuplet());

        var notification = captureSingleDidChange();
        var mutations = notification.getMutations();
        assertThat(mutations).hasSize(2);
        assertThat(mutations.get(0)).isInstanceOf(TupletRemoval.class);
        var removal = (TupletRemoval) mutations.get(0);
        assertThat(removal.span().getGrade()).isEqualTo(TupletAction.Tuplet.TRIPLET.getSize());
        assertThat(removal.line()).isSameAs(env.line());
        assertThat(mutations.get(1)).isInstanceOf(TupletAddition.class);
        var addition = (TupletAddition) mutations.get(1);
        assertThat(addition.span().start).isEqualTo(0);
        assertThat(addition.span().end).isEqualTo(2);
        assertThat(addition.span().getGrade()).isEqualTo(TupletAction.Tuplet.QUINTUPLET.getSize());
        assertThat(addition.line()).isSameAs(env.line());
    }

    @Test
    void testToggleTupletMatchingGradeRemovesOnly() {
        // Clicking the same grade over an existing tuplet removes it (no add).
        var env = setup(crotchet(), crotchet(), crotchet());
        env.line().getTuplets().addSpan(new TupletSpan(0, 2, TupletAction.Tuplet.TRIPLET.getSize()));
        ReflectionTestHelper.selectRange(env.coordinator(), 0, 2);
        env.operations().toggleTuplet(TupletAction.Tuplet.TRIPLET.getSize(), env.operations().canToggleTuplet());

        var notification = captureSingleDidChange();
        var mutations = notification.getMutations();
        assertThat(mutations).hasSize(1);
        assertThat(mutations.get(0)).isInstanceOf(TupletRemoval.class);
    }

    @Test
    void testToggleTupletPartialCoverageInExistingTupletThrows() {
        // Partial-coverage selection inside an existing tuplet is a caller bug:
        // the UI disables this path, and toggleTuplet now throws IllegalStateException
        // rather than silently replacing the tuplet with a sub-range one.
        var env = setup(crotchet(), crotchet(), crotchet());
        var originalTuplet = new TupletSpan(0, 2, TupletAction.Tuplet.TRIPLET.getSize());
        env.line().getTuplets().addSpan(originalTuplet);
        ReflectionTestHelper.selectRange(env.coordinator(), 0, 1);
        var info = env.operations().canToggleTuplet();

        assertThatThrownBy(() -> env.operations().toggleTuplet(TupletAction.Tuplet.QUINTUPLET.getSize(), info))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("sub-range");

        // The original span object is still in place — the operation was a pure no-op.
        assertThat(env.line().getTuplets().findSpan(0)).isSameAs(originalTuplet);
    }

    // -----------------------------------------------------------------------
    // Dynamics
    // -----------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testAddDynamicsEmitsOneAddition(boolean crescendo) {
        var env = setup(crotchet(), crotchet());
        ReflectionTestHelper.selectRange(env.coordinator(), 0, 1);
        env.operations().addDynamicsToSelection(crescendo);

        var notification = captureSingleDidChange();
        var mutations = notification.getMutations();
        assertThat(mutations).hasSize(1);

        if (crescendo) {
            assertThat(mutations.get(0)).isInstanceOf(CrescendoAddition.class);
        } else {
            assertThat(mutations.get(0)).isInstanceOf(DiminuendoAddition.class);
        }
    }

    @Test
    void testRemoveDynamicsEmitsRemovalPerSpan() {
        // One crescendo at [0..1] and one diminuendo at [2..3], selection covers all four notes.
        // getDynamicsSpansFromSelection iterates per index, so each span appears once per
        // covered index within the selection — the exact count depends on span range.
        var env = setup(crotchet(), crotchet(), crotchet(), crotchet());
        env.line().getCrescendos().addSpan(new DynamicsSpan(0, 1));
        env.line().getDiminuendos().addSpan(new DynamicsSpan(2, 3));
        ReflectionTestHelper.selectRange(env.coordinator(), 0, 3);
        env.operations().removeDynamicsFromSelection();

        var notification = captureSingleDidChange();
        var mutations = notification.getMutations();
        var crescendoRemovals = mutations.stream()
            .filter(m -> m instanceof CrescendoRemoval)
            .toList();
        var diminuendoRemovals = mutations.stream()
            .filter(m -> m instanceof DiminuendoRemoval)
            .toList();

        assertThat(crescendoRemovals).as("crescendo removals emitted").isNotEmpty();
        assertThat(diminuendoRemovals).as("diminuendo removals emitted").isNotEmpty();
        assertThat(mutations).hasSize(crescendoRemovals.size() + diminuendoRemovals.size());
    }

    // -----------------------------------------------------------------------
    // Trill
    // -----------------------------------------------------------------------

    @Test
    void testToggleTrillEmitsOneElementModificationPerNote() {
        var env = setup(crotchet(), crotchet(), crotchet());
        ReflectionTestHelper.selectRange(env.coordinator(), 0, 2);
        env.operations().toggleTrill();

        var notification = captureSingleDidChange();
        var mutations = notification.getMutations();
        assertThat(mutations).hasSize(3);

        for (var mutation : mutations) {
            assertThat(mutation).isInstanceOf(ElementModification.class);
            var mod = (ElementModification) mutation;
            assertThat(mod.fields()).contains(ElementField.TRILL);
            assertThat(mod.line()).isSameAs(env.line());
        }
    }

    // -----------------------------------------------------------------------
    // Lyrics under rests
    // -----------------------------------------------------------------------

    @Test
    void testToggleLyricsUnderRestsEmitsOneElementModification() {
        var env = setup(crotchetRest());
        ReflectionTestHelper.selectNote(env.coordinator(), 0);
        env.operations().toggleLyricsUnderRests();

        var notification = captureSingleDidChange();
        var mutations = notification.getMutations();
        assertThat(mutations).hasSize(1);
        assertThat(mutations.get(0)).isInstanceOf(ElementModification.class);
        var mod = (ElementModification) mutations.get(0);
        assertThat(mod.fields()).contains(ElementField.FORCE_SYLLABLE);
        assertThat(mod.line()).isSameAs(env.line());
    }

    // -----------------------------------------------------------------------
    // Stem direction
    // -----------------------------------------------------------------------

    @Test
    void testFlipStemDirectionEmitsElementModificationPerAffectedIndex() {
        var env = setup(crotchet(), crotchet(), crotchet());
        ReflectionTestHelper.selectRange(env.coordinator(), 0, 2);
        env.operations().flipStemDirection();

        var notification = captureSingleDidChange();
        var mutations = notification.getMutations();
        assertThat(mutations).hasSize(3);

        for (var mutation : mutations) {
            assertThat(mutation).isInstanceOf(ElementModification.class);
            var mod = (ElementModification) mutation;
            assertThat(mod.fields()).contains(ElementField.UPPER);
            assertThat(mod.fields()).contains(ElementField.STEM_DIRECTION_AUTO);
            assertThat(mod.line()).isSameAs(env.line());
        }
    }

    // -----------------------------------------------------------------------
    // First-second ending
    // -----------------------------------------------------------------------

    @Test
    void testMakeFirstSecondEndingEmitsElementInsertionAndRangeElementAddition() {
        // Four notes [n0..n3]. Result: INSERT_BARLINE at index 0, ending over [0..3].
        // makeFirstSecondEnding inserts the barline at start=0 (shifting indices to
        // start=1 and end=4), then adds an Ending spanning the adjusted bounds.
        var env = setup(crotchet(), crotchet(), crotchet(), crotchet());
        ReflectionTestHelper.selectNote(env.coordinator(), 0);
        var result = EndingValidationResult.valid(
            EndingValidationResult.PrecedingAction.INSERT_BARLINE, 0, 3);
        env.operations().makeFirstSecondEnding(result);

        var notification = captureSingleDidChange();
        var mutations = notification.getMutations();
        assertThat(mutations).hasSize(2);
        assertThat(mutations.get(0)).isInstanceOf(ElementInsertion.class);
        assertThat(mutations.get(1)).isInstanceOf(RangeElementAddition.class);

        var insertion = (ElementInsertion) mutations.get(0);
        assertThat(insertion.element().getType()).isEqualTo(ElementType.SINGLE_BARLINE);

        var rangeAddition = (RangeElementAddition) mutations.get(1);
        assertThat(rangeAddition.element()).isInstanceOf(Ending.class);
    }

    @Test
    void testMakeFirstSecondEndingWithExistingLeadingBarlineEmitsOnlyRangeElementAddition() {
        // Selection already starts with a single barline [barline, n1, n2, n3].
        // Result: NONE action at index 0, ending over [0..3].
        // No barline should be inserted — only the Ending range element is added.
        var env = setup(ElementType.SINGLE_BARLINE.newInstance(), crotchet(), crotchet(), crotchet());
        ReflectionTestHelper.selectNote(env.coordinator(), 0);
        var result = EndingValidationResult.valid(EndingValidationResult.PrecedingAction.NONE, 0, 3);
        env.operations().makeFirstSecondEnding(result);

        var notification = captureSingleDidChange();
        var mutations = notification.getMutations();
        assertThat(mutations).hasSize(1);
        assertThat(mutations.get(0)).isInstanceOf(RangeElementAddition.class);

        var rangeAddition = (RangeElementAddition) mutations.get(0);
        assertThat(rangeAddition.element()).isInstanceOf(Ending.class);
    }

    // -----------------------------------------------------------------------
    // canMakeFirstSecondEnding — REPEAT_LEFT_RIGHT split validation
    // -----------------------------------------------------------------------

    /**
     * Verifies that {@code canMakeFirstSecondEnding} applies the correct terminal constraint
     * when the split is a {@code REPEAT_LEFT_RIGHT}: the second ending must end with
     * {@code REPEAT_RIGHT} or {@code REPEAT_LEFT_RIGHT}.
     *
     * <p>Canonical line layout for all tests in this class:
     * <pre>
     *  idx:  0            1        2        3                  4        5        6
     *        REPEAT_LEFT  CROTCHET CROTCHET REPEAT_LEFT_RIGHT  CROTCHET CROTCHET [terminal]
     * </pre>
     * Selection covers indices 1–6. The REPEAT_LEFT at index 0 satisfies the preceding-element
     * and enclosing-repeat requirements.
     */
    @Nested
    class CanMakeFirstSecondEndingWithRepeatLeftRightSplit {

        private Env buildEnv(StaffElement terminal) {
            return setup(
                repeatLeft(), crotchet(), crotchet(), repeatLeftRight(), crotchet(), crotchet(), terminal
            );
        }

        @Test
        void testRepeatRightTerminalIsValid() {
            // REPEAT_RIGHT closes the second ending's repeat section — valid
            var env = buildEnv(repeatRight());
            ReflectionTestHelper.selectRange(env.coordinator(), 1, 6);
            assertThat(env.operations().canMakeFirstSecondEnding().isValid()).isTrue();
        }

        @Test
        void testRepeatLeftRightTerminalIsValid() {
            // REPEAT_LEFT_RIGHT closes the second ending and opens a new repeat — valid
            var env = buildEnv(repeatLeftRight());
            ReflectionTestHelper.selectRange(env.coordinator(), 1, 6);
            assertThat(env.operations().canMakeFirstSecondEnding().isValid()).isTrue();
        }

        @Test
        void testSingleBarlineTerminalIsInvalid() {
            // SINGLE_BARLINE does not close the second ending's repeat — invalid
            var env = buildEnv(singleBarline());
            ReflectionTestHelper.selectRange(env.coordinator(), 1, 6);
            assertThat(env.operations().canMakeFirstSecondEnding().isValid()).isFalse();
        }

        @Test
        void testRepeatLeftTerminalIsInvalid() {
            // REPEAT_LEFT does not close any repeat — invalid
            var env = buildEnv(repeatLeft());
            ReflectionTestHelper.selectRange(env.coordinator(), 1, 6);
            assertThat(env.operations().canMakeFirstSecondEnding().isValid()).isFalse();
        }
    }

    // -----------------------------------------------------------------------
    // hasEnclosingRepeat — backward-scan delimiter rules
    // -----------------------------------------------------------------------

    /**
     * Verifies the updated backward-scan rules in {@code hasEnclosingRepeat}:
     * double barlines act as section delimiters, reaching the beginning of a
     * non-first line is invalid, and reaching the beginning of the first line
     * is valid.
     */
    @Nested
    class HasEnclosingRepeatRules {

        // DOUBLE_BARLINE between REPEAT_LEFT and selection stops the scan
        @Test
        void testDoubleBarlineBlocksScan() {
            // idx: 0            1        2        3              4        5        6             7        8        9
            //      REPEAT_LEFT  CROTCHET CROTCHET DOUBLE_BARLINE CROTCHET CROTCHET REPEAT_RIGHT  CROTCHET CROTCHET SINGLE_BARLINE
            // Selection 4–9 hits DOUBLE_BARLINE before reaching REPEAT_LEFT → invalid
            var env = setup(
                repeatLeft(), crotchet(), crotchet(), doubleBarline(),
                crotchet(), crotchet(), repeatRight(), crotchet(), crotchet(), singleBarline()
            );
            ReflectionTestHelper.selectRange(env.coordinator(), 4, 9);
            assertThat(env.operations().canMakeFirstSecondEnding().isValid()).isFalse();
        }

        // FINAL_DOUBLE_BARLINE between REPEAT_LEFT and selection stops the scan
        @Test
        void testFinalDoubleBarlineBlocksScan() {
            // Same layout, FINAL_DOUBLE_BARLINE at idx 3 instead of DOUBLE_BARLINE
            var env = setup(
                repeatLeft(), crotchet(), crotchet(), finalDoubleBarline(),
                crotchet(), crotchet(), repeatRight(), crotchet(), crotchet(), singleBarline()
            );
            ReflectionTestHelper.selectRange(env.coordinator(), 4, 9);
            assertThat(env.operations().canMakeFirstSecondEnding().isValid()).isFalse();
        }

        // First line: reaching beginning of composition without a left repeat is valid
        @Test
        void testFirstLineNoRepeatIsValid() {
            // The Composition always starts with an initial line at index 0. Populate it
            // directly so the test line is at lineIndex 0, exercising the first-line exception.
            //
            // idx: 0        1        2             3        4        5
            //      CROTCHET CROTCHET REPEAT_RIGHT  CROTCHET CROTCHET SINGLE_BARLINE
            // Selection 1–5. Scan finds CROTCHET at idx 0 then reaches beginning; lineIndex==0 → valid
            var line = composition.getLine(0);
            composition.withoutMutationTracking(() -> {
                line.addElement(crotchet());
                line.addElement(crotchet());
                line.addElement(repeatRight());
                line.addElement(crotchet());
                line.addElement(crotchet());
                line.addElement(singleBarline());
            });
            var coordinator = ReflectionTestHelper.createCoordinatorForLine(line);
            var ops = new MusicEditOperations(composition, coordinator);
            messageCenterMock = mockStatic(MessageCenter.class);

            ReflectionTestHelper.selectRange(coordinator, 1, 5);
            assertThat(ops.canMakeFirstSecondEnding().isValid()).isTrue();
        }

        // Non-first line: reaching beginning of composition without a left repeat is invalid
        @Test
        void testNonFirstLineNoRepeatIsInvalid() {
            // Line 0: CROTCHET CROTCHET CROTCHET
            // Line 1: CROTCHET CROTCHET REPEAT_RIGHT  CROTCHET CROTCHET SINGLE_BARLINE
            //         idx: 0        1        2             3        4        5
            // Selection 0–5 on line 1. No repeat found; lineIndex==1 → invalid
            var line0 = new Line();
            line0.addElement(crotchet());
            line0.addElement(crotchet());
            line0.addElement(crotchet());
            composition.withoutMutationTracking(() -> composition.addLine(line0));

            var env = setup(
                crotchet(), crotchet(), repeatRight(), crotchet(), crotchet(), singleBarline()
            );
            ReflectionTestHelper.selectRange(env.coordinator(), 0, 5);
            assertThat(env.operations().canMakeFirstSecondEnding().isValid()).isFalse();
        }

        // Non-first line: REPEAT_LEFT on previous line crosses the line boundary → valid
        @Test
        void testRepeatLeftOnPreviousLineIsValid() {
            // Line 0: REPEAT_LEFT  CROTCHET  CROTCHET
            // Line 1: CROTCHET CROTCHET REPEAT_RIGHT  CROTCHET CROTCHET SINGLE_BARLINE
            //         idx: 0        1        2             3        4        5
            // Selection 0–5 on line 1. Scan crosses to line 0, finds REPEAT_LEFT → valid
            var line0 = new Line();
            line0.addElement(repeatLeft());
            line0.addElement(crotchet());
            line0.addElement(crotchet());
            composition.withoutMutationTracking(() -> composition.addLine(line0));

            var env = setup(
                crotchet(), crotchet(), repeatRight(), crotchet(), crotchet(), singleBarline()
            );
            ReflectionTestHelper.selectRange(env.coordinator(), 0, 5);
            assertThat(env.operations().canMakeFirstSecondEnding().isValid()).isTrue();
        }
    }

}
