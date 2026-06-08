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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;
import static songscribe.dom.StaffElementFactory.*;

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
import songscribe.layout.Ending;
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
import songscribe.message.notification.SongDidChangeNotification;
import songscribe.ui.MusicEditOperations;
import songscribe.ui.action.TupletAction;
import songscribe.ui.selection.ReflectionTestHelper;
import songscribe.ui.selection.SelectionCoordinator;

class MusicEditOperationsMutationTest extends UnitTest {

    private Song song;
    @Nullable private MockedStatic<MessageCenter> messageCenterMock;

    @BeforeEach
    void setUp() {
        // Construct before mocking so constructor-internal bus interactions
        // go to the real (unobserved) bus, not the mock.
        song = new Song();
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
     * Builds a Line with the given elements, attaches it to the song (which fires a
     * real LineInsertion notification on the unobserved bus), creates a coordinator and
     * operations wrapper, then starts mocking MessageCenter.
     *
     * <p>Range elements (beams, ties, etc.) that require no modification bracket may be added
     * to {@code env.line()} directly via {@code line.addRangeElement} after this call.
     */
    private Env setupEnv(StaffElement... elements) {
        var line = new Line(song);

        // addLine fires a real LineInsertion notification on the pre-mock bus.
        // Use withoutMutationTracking so the terminal invariant is not enforced
        // during setup — tests need to build lines with arbitrary terminal elements.
        song.withoutMutationTracking(() -> {
            for (var element : elements) {
                line.addElement(element);
            }

            song.addLine(line);
        });
        var coordinator = ReflectionTestHelper.createCoordinatorForLine(line);
        var ops = new MusicEditOperations(song, coordinator);
        messageCenterMock = mockStatic(MessageCenter.class);
        return new Env(coordinator, ops, line);
    }

    // -----------------------------------------------------------------------
    // Notification capture helper
    // -----------------------------------------------------------------------

    private SongDidChangeNotification captureSingleDidChange() {
        var mock = messageCenterMock;

        if (mock == null) {
            throw new IllegalStateException("messageCenterMock not set — call setupEnv() first");
        }

        var captor = ArgumentCaptor.forClass(Message.class);
        mock.verify(() -> MessageCenter.post(captor.capture()));
        var didChanges = captor.getAllValues().stream()
            .filter(m -> m instanceof SongDidChangeNotification)
            .map(m -> (SongDidChangeNotification) m)
            .toList();

        assertThat(didChanges)
            .as("expected exactly one SongDidChangeNotification, got: %s", didChanges)
            .hasSize(1);

        return didChanges.getFirst();
    }

    // -----------------------------------------------------------------------
    // Beaming
    // -----------------------------------------------------------------------

    @Test
    void testToggleBeamingAddEmitsBeamingAddition() {
        var env = setupEnv(quaver(), quaver(), quaver());
        ReflectionTestHelper.selectRange(env.coordinator(), 0, 2);
        env.operations().toggleBeaming();

        var notification = captureSingleDidChange();
        var mutations = notification.getMutations();
        assertThat(mutations).hasSize(1);
        assertThat(mutations.getFirst()).isInstanceOf(BeamingAddition.class);
        var addition = (BeamingAddition) mutations.getFirst();
        assertThat(addition.beam().getAnchorElementIndex()).isEqualTo(0);
        assertThat(addition.beam().getEndElementIndex()).isEqualTo(2);
        assertThat(addition.line()).isSameAs(env.line());
    }

    @Test
    void testToggleBeamingRemoveEmitsBeamingRemoval() {
        var env = setupEnv(quaver(), quaver(), quaver());
        env.line().getSong().withoutMutationTracking(
            () -> env.line().addBeaming(new Beam(env.line().getElement(0), env.line().getElement(2))));
        ReflectionTestHelper.selectRange(env.coordinator(), 0, 2);
        env.operations().toggleBeaming();

        var notification = captureSingleDidChange();
        var mutations = notification.getMutations();
        assertThat(mutations).hasSize(1);
        assertThat(mutations.getFirst()).isInstanceOf(BeamingRemoval.class);
        assertThat(((BeamingRemoval) mutations.getFirst()).line()).isSameAs(env.line());
    }

    // -----------------------------------------------------------------------
    // Tie
    // -----------------------------------------------------------------------

    @Test
    void testToggleTieAddEmitsTieAddition() {
        var env = setupEnv(crotchet(), crotchet());
        ReflectionTestHelper.selectRange(env.coordinator(), 0, 1);
        env.operations().toggleTie();

        var notification = captureSingleDidChange();
        var mutations = notification.getMutations();
        assertThat(mutations).hasSize(1);
        assertThat(mutations.getFirst()).isInstanceOf(TieAddition.class);
        var addition = (TieAddition) mutations.getFirst();
        assertThat(addition.tie().getAnchorElementIndex()).isEqualTo(0);
        assertThat(addition.tie().getEndElementIndex()).isEqualTo(1);
        assertThat(addition.line()).isSameAs(env.line());
    }

    @Test
    void testToggleTieRemoveEmitsTieRemoval() {
        var env = setupEnv(crotchet(), crotchet());
        var e0 = env.line().getElement(0);
        var e1 = env.line().getElement(1);
        env.line().getSong().withoutMutationTracking(
            () -> env.line().addRangeElement(new Tie(e0, e1)));
        ReflectionTestHelper.selectRange(env.coordinator(), 0, 1);
        env.operations().toggleTie();

        var notification = captureSingleDidChange();
        var mutations = notification.getMutations();
        assertThat(mutations).hasSize(1);
        assertThat(mutations.getFirst()).isInstanceOf(TieRemoval.class);
        assertThat(((TieRemoval) mutations.getFirst()).line()).isSameAs(env.line());
    }

    // -----------------------------------------------------------------------
    // Tuplet
    // -----------------------------------------------------------------------

    @Test
    void testToggleTupletAddEmitsTupletAddition() {
        var env = setupEnv(crotchet(), crotchet(), crotchet());
        ReflectionTestHelper.selectRange(env.coordinator(), 0, 2);
        env.operations().toggleTuplet(TupletAction.Tuplet.TRIPLET.getSize(), env.operations().canToggleTuplet());

        var notification = captureSingleDidChange();
        var mutations = notification.getMutations();
        assertThat(mutations).hasSize(1);
        assertThat(mutations.getFirst()).isInstanceOf(TupletAddition.class);
        var addition = (TupletAddition) mutations.getFirst();
        assertThat(addition.tuplet().getAnchorElementIndex()).isEqualTo(0);
        assertThat(addition.tuplet().getEndElementIndex()).isEqualTo(2);
        assertThat(addition.tuplet().getGrade()).isEqualTo(TupletAction.Tuplet.TRIPLET.getSize());
        assertThat(addition.line()).isSameAs(env.line());
    }

    @Test
    void testToggleTupletRemoveEmitsTupletRemoval() {
        var env = setupEnv(crotchet(), crotchet(), crotchet());
        song.withoutMutationTracking(() -> env.line().addTuplet(new Tuplet(
            env.line().getElement(0), env.line().getElement(2), TupletAction.Tuplet.TRIPLET.getSize())));
        ReflectionTestHelper.selectRange(env.coordinator(), 0, 2);
        env.operations().toggleTuplet(TupletAction.Tuplet.TRIPLET.getSize(), env.operations().canToggleTuplet());

        var notification = captureSingleDidChange();
        var mutations = notification.getMutations();
        assertThat(mutations).hasSize(1);
        assertThat(mutations.getFirst()).isInstanceOf(TupletRemoval.class);
        assertThat(((TupletRemoval) mutations.getFirst()).line()).isSameAs(env.line());
    }

    @Test
    void testToggleTupletGradeChangeEmitsRemovalThenAddition() {
        // With the full tuplet span selected, calling toggleTuplet with a
        // different grade must remove the existing span and add the new one
        // inside a single bracket — one notification, two mutations, undo replays
        // both atomically.
        var env = setupEnv(crotchet(), crotchet(), crotchet());
        song.withoutMutationTracking(() -> env.line().addTuplet(new Tuplet(
            env.line().getElement(0), env.line().getElement(2), TupletAction.Tuplet.TRIPLET.getSize())));
        ReflectionTestHelper.selectRange(env.coordinator(), 0, 2);
        env.operations().toggleTuplet(TupletAction.Tuplet.QUINTUPLET.getSize(), env.operations().canToggleTuplet());

        var notification = captureSingleDidChange();
        var mutations = notification.getMutations();
        assertThat(mutations).hasSize(2);
        assertThat(mutations.getFirst()).isInstanceOf(TupletRemoval.class);
        var removal = (TupletRemoval) mutations.getFirst();
        assertThat(removal.tuplet().getGrade()).isEqualTo(TupletAction.Tuplet.TRIPLET.getSize());
        assertThat(removal.line()).isSameAs(env.line());
        assertThat(mutations.get(1)).isInstanceOf(TupletAddition.class);
        var addition = (TupletAddition) mutations.get(1);
        assertThat(addition.tuplet().getAnchorElementIndex()).isEqualTo(0);
        assertThat(addition.tuplet().getEndElementIndex()).isEqualTo(2);
        assertThat(addition.tuplet().getGrade()).isEqualTo(TupletAction.Tuplet.QUINTUPLET.getSize());
        assertThat(addition.line()).isSameAs(env.line());
    }

    @Test
    void testToggleTupletMatchingGradeRemovesOnly() {
        // Clicking the same grade over an existing tuplet removes it (no add).
        var env = setupEnv(crotchet(), crotchet(), crotchet());
        song.withoutMutationTracking(() -> env.line().addTuplet(new Tuplet(
            env.line().getElement(0), env.line().getElement(2), TupletAction.Tuplet.TRIPLET.getSize())));
        ReflectionTestHelper.selectRange(env.coordinator(), 0, 2);
        env.operations().toggleTuplet(TupletAction.Tuplet.TRIPLET.getSize(), env.operations().canToggleTuplet());

        var notification = captureSingleDidChange();
        var mutations = notification.getMutations();
        assertThat(mutations).hasSize(1);
        assertThat(mutations.getFirst()).isInstanceOf(TupletRemoval.class);
    }

    @Test
    void testToggleTupletPartialCoverageInExistingTupletThrows() {
        // Partial-coverage selection inside an existing tuplet is a caller bug:
        // the UI disables this path, and toggleTuplet now throws IllegalStateException
        // rather than silently replacing the tuplet with a sub-range one.
        var env = setupEnv(crotchet(), crotchet(), crotchet());
        var originalTuplet = new Tuplet(
            env.line().getElement(0), env.line().getElement(2), TupletAction.Tuplet.TRIPLET.getSize());
        song.withoutMutationTracking(() -> env.line().addTuplet(originalTuplet));
        ReflectionTestHelper.selectRange(env.coordinator(), 0, 1);
        var info = env.operations().canToggleTuplet();

        assertThatThrownBy(() -> env.operations().toggleTuplet(TupletAction.Tuplet.QUINTUPLET.getSize(), info))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("sub-range");

        // The original tuplet object is still in place — the operation was a pure no-op.
        assertThat(env.line().findTupletAt(0)).isSameAs(originalTuplet);
    }

    @Test
    void testToggleTupletWithSizeZeroRemovesExistingTuplet() {
        // TupletAction.Tuplet.REMOVE.getSize() == 0; calling toggleTuplet with size 0
        // must remove the existing tuplet via the tupletSize == 0 branch rather than
        // the same-grade comparison path exercised by testToggleTupletMatchingGradeRemovesOnly.
        var env = setupEnv(crotchet(), crotchet(), crotchet());
        song.withoutMutationTracking(() -> env.line().addTuplet(new Tuplet(
            env.line().getElement(0), env.line().getElement(2), TupletAction.Tuplet.TRIPLET.getSize())));
        ReflectionTestHelper.selectRange(env.coordinator(), 0, 2);
        env.operations().toggleTuplet(TupletAction.Tuplet.REMOVE.getSize(), env.operations().canToggleTuplet());

        var notification = captureSingleDidChange();
        var mutations = notification.getMutations();
        assertThat(mutations).hasSize(1);
        var firstMutation = mutations.getFirst();
        assertThat(firstMutation).isInstanceOf(TupletRemoval.class);
        assertThat(((TupletRemoval) firstMutation).line()).isSameAs(env.line());
        assertThat(env.line().findTupletAt(0))
            .as("tuplet must be absent after removal via size-0 path")
            .isNull();
    }

    @Test
    void testToggleTupletWithSizeZeroAndNoExistingThrows() {
        // size == 0 with no existing tuplet at the selection is a caller bug:
        // toggleTuplet must throw IllegalStateException to prevent a silent no-op.
        var env = setupEnv(crotchet(), crotchet(), crotchet());
        ReflectionTestHelper.selectRange(env.coordinator(), 0, 2);
        var info = env.operations().canToggleTuplet();

        assertThatThrownBy(() -> env.operations().toggleTuplet(TupletAction.Tuplet.REMOVE.getSize(), info))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("toggleTuplet(0) requires an existing tuplet");
    }

    // -----------------------------------------------------------------------
    // Dynamics
    // -----------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testAddDynamicsEmitsOneAddition(boolean crescendo) {
        var env = setupEnv(crotchet(), crotchet());
        ReflectionTestHelper.selectRange(env.coordinator(), 0, 1);
        env.operations().addDynamicsToSelection(crescendo);

        var notification = captureSingleDidChange();
        var mutations = notification.getMutations();
        assertThat(mutations).hasSize(1);

        if (crescendo) {
            assertThat(mutations.getFirst()).isInstanceOf(CrescendoAddition.class);
        } else {
            assertThat(mutations.getFirst()).isInstanceOf(DiminuendoAddition.class);
        }
    }

    @Test
    void testRemoveDynamicsEmitsRemovalPerSpan() {
        // One crescendo at [0..1] and one diminuendo at [2..3], selection covers all four notes.
        var env = setupEnv(crotchet(), crotchet(), crotchet(), crotchet());
        var line = env.line();
        song.withoutMutationTracking(() -> {
            line.addRangeElement(new Crescendo(line.getElement(0), line.getElement(1)));
            line.addRangeElement(new Diminuendo(line.getElement(2), line.getElement(3)));
        });
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

        assertThat(crescendoRemovals).as("exactly one crescendo removal emitted").hasSize(1);
        assertThat(diminuendoRemovals).as("exactly one diminuendo removal emitted").hasSize(1);
        assertThat(mutations).as("total mutations equals one crescendo removal plus one diminuendo removal").hasSize(2);
    }

    @Test
    void testCanRemoveDynamicsReturnsFalseWhenNoElementSelection() {
        // The coordinator has an active line but no element selection (selectionBegin == -1),
        // so hasElementSelection() is false and canRemoveDynamicsFromSelection() must return false.
        var env = setupEnv(crotchet(), crotchet());
        // No selectRange call — coordinator has a registered line but no element selection.
        assertThat(env.operations().canRemoveDynamicsFromSelection())
            .as("canRemoveDynamicsFromSelection() with no element selection must return false")
            .isFalse();
    }

    @Test
    void testCanRemoveDynamicsReturnsFalseWithNoHairpins() {
        // Selection contains only notes — no crescendo or diminuendo overlapping the range.
        var env = setupEnv(crotchet(), crotchet());
        ReflectionTestHelper.selectRange(env.coordinator(), 0, 1);
        assertThat(env.operations().canRemoveDynamicsFromSelection())
            .as("canRemoveDynamicsFromSelection() with no overlapping hairpins must return false")
            .isFalse();
    }

    @Test
    void testCanRemoveDynamicsReturnsTrueWhenCrescendoOverlapsSelection() {
        var env = setupEnv(crotchet(), crotchet(), crotchet());
        var line = env.line();
        song.withoutMutationTracking(
            () -> line.addRangeElement(new Crescendo(line.getElement(0), line.getElement(2))));
        ReflectionTestHelper.selectRange(env.coordinator(), 0, 2);
        assertThat(env.operations().canRemoveDynamicsFromSelection())
            .as("canRemoveDynamicsFromSelection() must return true when a crescendo overlaps selection")
            .isTrue();
    }

    @Test
    void testCanRemoveDynamicsReturnsTrueWhenDiminuendoOverlapsSelection() {
        var env = setupEnv(crotchet(), crotchet(), crotchet());
        var line = env.line();
        song.withoutMutationTracking(
            () -> line.addRangeElement(new Diminuendo(line.getElement(0), line.getElement(2))));
        ReflectionTestHelper.selectRange(env.coordinator(), 0, 2);
        assertThat(env.operations().canRemoveDynamicsFromSelection())
            .as("canRemoveDynamicsFromSelection() must return true when a diminuendo overlaps selection")
            .isTrue();
    }

    @Test
    void testRemoveDynamicsRemovesCrescendoWhoseAnchorIsBeforeSelectionBegin() {
        // A crescendo starting before the selection but ending inside the selection
        // must be included in getDynamicsFromSelection and removed.
        // Layout: [c0, c1, c2, c3]; crescendo spans [0..2], selection covers [2..3].
        // Overlap condition: anchor(0) <= selectionEnd(3) AND end(2) >= selectionBegin(2) → included.
        var env = setupEnv(crotchet(), crotchet(), crotchet(), crotchet());
        var line = env.line();
        var crescendo = new Crescendo(line.getElement(0), line.getElement(2));
        song.withoutMutationTracking(() -> line.addRangeElement(crescendo));
        ReflectionTestHelper.selectRange(env.coordinator(), 2, 3);
        env.operations().removeDynamicsFromSelection();

        var notification = captureSingleDidChange();
        var mutations = notification.getMutations();
        assertThat(mutations).as("one crescendo removal emitted for partial overlap").hasSize(1);
        assertThat(mutations.getFirst())
            .as("removal is a CrescendoRemoval")
            .isInstanceOf(CrescendoRemoval.class);
    }

    // -----------------------------------------------------------------------
    // canMakeFirstSecondEnding — null/no-element-selection (row 32)
    // -----------------------------------------------------------------------

    @Test
    void testCanMakeFirstSecondEndingReturnsFalseWhenNoElementSelection() {
        // Active line registered but no element range selected (hasElementSelection() == false).
        var env = setupEnv(crotchet(), crotchet());
        // No selectRange call — active line exists but selection is empty.
        assertThat(env.operations().canMakeFirstSecondEnding().isValid())
            .as("canMakeFirstSecondEnding() with no element selection must return invalid")
            .isFalse();
    }

    // -----------------------------------------------------------------------
    // canMakeFirstSecondEnding — validateEndingStructure failures
    // -----------------------------------------------------------------------

    @Test
    void testCanMakeFirstSecondEndingReturnsFalseWhenContentBelowMinimum() {
        // Three content elements total (crotchet + repeat_right + single_barline) which is
        // below MIN_CONTENT_ELEMENTS (4); validateEndingStructure must return -1.
        // Layout: [CROTCHET(0), REPEAT_RIGHT(1), SINGLE_BARLINE(2)], selection 0-2.
        var env = setupEnv(crotchet(), repeatRight(), singleBarline());
        ReflectionTestHelper.selectRange(env.coordinator(), 0, 2);
        assertThat(env.operations().canMakeFirstSecondEnding().isValid())
            .as("canMakeFirstSecondEnding() with fewer than MIN_CONTENT_ELEMENTS must return invalid")
            .isFalse();
    }

    @Test
    void testCanMakeFirstSecondEndingReturnsFalseWhenMultipleRightRepeats() {
        // Two REPEAT_RIGHT elements in the selection; validateEndingStructure detects the
        // second right-repeat and returns -1 immediately.
        // Layout: [CROTCHET(0), REPEAT_RIGHT(1), CROTCHET(2), REPEAT_RIGHT(3), CROTCHET(4),
        //          SINGLE_BARLINE(5)], selection 0-5.
        var env = setupEnv(crotchet(), repeatRight(), crotchet(), repeatRight(), crotchet(), singleBarline());
        ReflectionTestHelper.selectRange(env.coordinator(), 0, 5);
        assertThat(env.operations().canMakeFirstSecondEnding().isValid())
            .as("canMakeFirstSecondEnding() with multiple right-repeats must return invalid")
            .isFalse();
    }

    @Test
    void testCanMakeFirstSecondEndingReturnsFalseWhenNoRightRepeatFound() {
        // Five content elements (4 crotchets + SINGLE_BARLINE) satisfies MIN_CONTENT_ELEMENTS,
        // but there is no REPEAT_RIGHT or REPEAT_LEFT_RIGHT in the selection;
        // rightRepeatIndex stays -1 and validateEndingStructure returns -1.
        // Layout: [CROTCHET(0), CROTCHET(1), CROTCHET(2), CROTCHET(3), SINGLE_BARLINE(4)],
        // selection 0-4.
        var env = setupEnv(crotchet(), crotchet(), crotchet(), crotchet(), singleBarline());
        ReflectionTestHelper.selectRange(env.coordinator(), 0, 4);
        assertThat(env.operations().canMakeFirstSecondEnding().isValid())
            .as("canMakeFirstSecondEnding() with no right-repeat in selection must return invalid")
            .isFalse();
    }

    // -----------------------------------------------------------------------
    // canMakeFirstSecondEnding — validateEndingStructure: region content failures
    // -----------------------------------------------------------------------

    @Test
    void testCanMakeFirstSecondEndingReturnsFalseWhenFirstEndingRegionHasBarline() {
        // First ending region (between selection start and the right-repeat) contains a
        // SINGLE_BARLINE; validateEndingRegionContent returns false → validateEndingStructure
        // returns -1 → canMakeFirstSecondEnding returns invalid.
        //
        // Layout: [CROTCHET(0), CROTCHET(1), SINGLE_BARLINE(2), CROTCHET(3),
        //          REPEAT_RIGHT(4), CROTCHET(5), CROTCHET(6), SINGLE_BARLINE(7)]
        // Selection 0–7. firstEndingStart=0 (CROTCHET), region=[0..3].
        // Index 2 is SINGLE_BARLINE → validateEndingRegionContent returns false.
        var env = setupEnv(
            crotchet(), crotchet(), singleBarline(), crotchet(),
            repeatRight(), crotchet(), crotchet(), singleBarline()
        );
        ReflectionTestHelper.selectRange(env.coordinator(), 0, 7);
        assertThat(env.operations().canMakeFirstSecondEnding().isValid())
            .as("canMakeFirstSecondEnding() must return invalid when first ending region contains a barline")
            .isFalse();
    }

    @Test
    void testCanMakeFirstSecondEndingReturnsFalseWhenFirstEndingRegionEmpty() {
        // First ending region is empty: selection starts with SINGLE_BARLINE, the very next
        // element is REPEAT_RIGHT, so firstEndingStart == rightRepeatIndex → validateEndingRegionContent
        // is called with from > to → hasContent stays false → returns false.
        //
        // Layout: [SINGLE_BARLINE(0), REPEAT_RIGHT(1), CROTCHET(2), CROTCHET(3),
        //          CROTCHET(4), CROTCHET(5), SINGLE_BARLINE(6)]
        // Selection 0–6. firstEndingStart=1 (adjusted past leading SINGLE_BARLINE),
        // rightRepeatIndex=1 → region=[1..0] (empty) → validateEndingRegionContent returns false.
        var env = setupEnv(
            singleBarline(), repeatRight(), crotchet(), crotchet(),
            crotchet(), crotchet(), singleBarline()
        );
        ReflectionTestHelper.selectRange(env.coordinator(), 0, 6);
        assertThat(env.operations().canMakeFirstSecondEnding().isValid())
            .as("canMakeFirstSecondEnding() must return invalid when first ending region has no content elements")
            .isFalse();
    }

    @Test
    void testCanMakeFirstSecondEndingReturnsFalseWhenSecondEndingRegionHasBarline() {
        // Second ending region (between right-repeat and terminal) contains a SINGLE_BARLINE;
        // validateEndingRegionContent returns false → validateEndingStructure returns -1 → invalid.
        //
        // Layout: [CROTCHET(0), CROTCHET(1), REPEAT_RIGHT(2), CROTCHET(3),
        //          SINGLE_BARLINE(4), CROTCHET(5), SINGLE_BARLINE(6)]
        // Selection 0–6. rightRepeatIndex=2, second region=[3..5].
        // Index 4 is SINGLE_BARLINE → validateEndingRegionContent returns false.
        var env = setupEnv(
            crotchet(), crotchet(), repeatRight(), crotchet(),
            singleBarline(), crotchet(), singleBarline()
        );
        ReflectionTestHelper.selectRange(env.coordinator(), 0, 6);
        assertThat(env.operations().canMakeFirstSecondEnding().isValid())
            .as("canMakeFirstSecondEnding() must return invalid when second ending region contains a barline")
            .isFalse();
    }

    // -----------------------------------------------------------------------
    // canMakeFirstSecondEnding — hasOverlap: existing ending in selection range
    // -----------------------------------------------------------------------

    @Test
    void testCanMakeFirstSecondEndingReturnsFalseWhenSelectionOverlapsExistingEnding() {
        // A line with an existing Ending span; the candidate selection overlaps it.
        // hasOverlap returns true → canMakeFirstSecondEnding returns invalid.
        //
        // Layout: [REPEAT_LEFT(0), CROTCHET(1), CROTCHET(2), REPEAT_RIGHT(3),
        //          CROTCHET(4), CROTCHET(5), SINGLE_BARLINE(6)]
        // An Ending is pre-added from element[1] to element[6].
        // Selection 1–6. Stage 1 passes (structural validation OK); stage 2 (hasOverlap) detects
        // the existing ending → returns invalid.
        var env = setupEnv(
            repeatLeft(), crotchet(), crotchet(), repeatRight(),
            crotchet(), crotchet(), singleBarline()
        );
        var line = env.line();
        // Add an existing Ending spanning the same range so hasOverlap fires.
        song.withoutMutationTracking(
            () -> line.addRangeElement(new Ending(line.getElement(1), line.getElement(6))));
        ReflectionTestHelper.selectRange(env.coordinator(), 1, 6);
        assertThat(env.operations().canMakeFirstSecondEnding().isValid())
            .as("canMakeFirstSecondEnding() must return invalid when selection overlaps an existing ending")
            .isFalse();
    }

    // -----------------------------------------------------------------------
    // canMakeFirstSecondEnding — checkPrecedingElement branches
    // -----------------------------------------------------------------------

    @Test
    void testCanMakeFirstSecondEndingReturnsNoneWhenPrecedingContentAndSelectionStartsWithBarline() {
        // checkPrecedingElement: preceding element is a content note; selection starts with
        // SINGLE_BARLINE → action is NONE (no barline insertion needed), result is valid.
        //
        // Layout: [REPEAT_LEFT(0), CROTCHET(1), SINGLE_BARLINE(2), CROTCHET(3),
        //          REPEAT_RIGHT(4), CROTCHET(5), CROTCHET(6), SINGLE_BARLINE(7)]
        // Selection 2–7. Preceding element at index 1 is CROTCHET (content).
        // selectionBeginType = SINGLE_BARLINE → NONE action.
        var env = setupEnv(
            repeatLeft(), crotchet(), singleBarline(), crotchet(),
            repeatRight(), crotchet(), crotchet(), singleBarline()
        );
        ReflectionTestHelper.selectRange(env.coordinator(), 2, 7);
        var result = env.operations().canMakeFirstSecondEnding();
        assertThat(result.isValid())
            .as("canMakeFirstSecondEnding() must be valid when preceding content + selection starts with barline")
            .isTrue();
        assertThat(result.getPrecedingAction())
            .as("preceding action must be NONE when selection already starts with a barline")
            .isEqualTo(EndingValidationResult.PrecedingAction.NONE);
        assertThat(result.getSpanStart())
            .as("span start must be the selection begin index")
            .isEqualTo(2);
        assertThat(result.getSpanEnd())
            .as("span end must be the selection end index")
            .isEqualTo(7);
    }

    @Test
    void testCanMakeFirstSecondEndingReturnsInsertBarlineWhenPrecedingContentAndSelectionStartsWithNote() {
        // checkPrecedingElement: preceding element is content; selection starts with a note
        // (not a barline or left repeat) → action is INSERT_BARLINE, result is valid.
        //
        // Layout: [REPEAT_LEFT(0), CROTCHET(1), CROTCHET(2), CROTCHET(3),
        //          REPEAT_RIGHT(4), CROTCHET(5), CROTCHET(6), SINGLE_BARLINE(7)]
        // Selection 2–7. Preceding element at index 1 is CROTCHET (content).
        // selectionBeginType = CROTCHET → INSERT_BARLINE action.
        var env = setupEnv(
            repeatLeft(), crotchet(), crotchet(), crotchet(),
            repeatRight(), crotchet(), crotchet(), singleBarline()
        );
        ReflectionTestHelper.selectRange(env.coordinator(), 2, 7);
        var result = env.operations().canMakeFirstSecondEnding();
        assertThat(result.isValid())
            .as("canMakeFirstSecondEnding() must be valid when preceding content + selection starts with note")
            .isTrue();
        assertThat(result.getPrecedingAction())
            .as("preceding action must be INSERT_BARLINE when selection starts with a note after content")
            .isEqualTo(EndingValidationResult.PrecedingAction.INSERT_BARLINE);
        assertThat(result.getSpanStart())
            .as("span start must be the selection begin index")
            .isEqualTo(2);
        assertThat(result.getSpanEnd())
            .as("span end must be the selection end index")
            .isEqualTo(7);
    }

    @Test
    void testCanMakeFirstSecondEndingReturnsExtendSpanWhenPrecedingElementIsBarline() {
        // checkPrecedingElement: preceding element is SINGLE_BARLINE → action is EXTEND_SPAN;
        // the span start is extended backward to include the preceding barline.
        //
        // Layout: [REPEAT_LEFT(0), CROTCHET(1), SINGLE_BARLINE(2), CROTCHET(3),
        //          REPEAT_RIGHT(4), CROTCHET(5), CROTCHET(6), SINGLE_BARLINE(7)]
        // Selection 3–7. Preceding element at index 2 is SINGLE_BARLINE → EXTEND_SPAN;
        // spanStart extends back to 2.
        var env = setupEnv(
            repeatLeft(), crotchet(), singleBarline(), crotchet(),
            repeatRight(), crotchet(), crotchet(), singleBarline()
        );
        ReflectionTestHelper.selectRange(env.coordinator(), 3, 7);
        var result = env.operations().canMakeFirstSecondEnding();
        assertThat(result.isValid())
            .as("canMakeFirstSecondEnding() must be valid when preceding element is a barline")
            .isTrue();
        assertThat(result.getPrecedingAction())
            .as("preceding action must be EXTEND_SPAN when preceding element is a barline")
            .isEqualTo(EndingValidationResult.PrecedingAction.EXTEND_SPAN);
        assertThat(result.getSpanStart())
            .as("span start must be extended back to the preceding barline index")
            .isEqualTo(2);
        assertThat(result.getSpanEnd())
            .as("span end must remain the selection end index")
            .isEqualTo(7);
    }

    // -----------------------------------------------------------------------
    // Trill
    // -----------------------------------------------------------------------

    @Test
    void testToggleTrillEmitsRangeElementAddition() {
        var env = setupEnv(crotchet(), crotchet(), crotchet());
        ReflectionTestHelper.selectRange(env.coordinator(), 0, 2);
        env.operations().toggleTrill();

        var notification = captureSingleDidChange();
        var mutations = notification.getMutations();
        assertThat(mutations).hasSize(1);
        assertThat(mutations.getFirst()).isInstanceOf(RangeElementAddition.class);
        assertThat(((RangeElementAddition) mutations.getFirst()).line()).isSameAs(env.line());
    }

    @Test
    void testToggleTrillOffResultsInNoTrill() {
        var env = setupEnv(crotchet(), crotchet(), crotchet());
        ReflectionTestHelper.selectRange(env.coordinator(), 0, 2);
        // Add trill, then remove it
        env.operations().toggleTrill();
        env.operations().toggleTrill();

        // After two toggles the line should have no trill range elements
        assertThat(env.line().findRangeElements(Trill.class))
            .as("trill removed after second toggle").isEmpty();
    }

    // -----------------------------------------------------------------------
    // Stem direction
    // -----------------------------------------------------------------------

    @Test
    void testFlipStemDirectionEmitsElementModificationPerAffectedIndex() {
        var env = setupEnv(crotchet(), crotchet(), crotchet());
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
        var env = setupEnv(crotchet(), crotchet(), crotchet(), crotchet());
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
        var env = setupEnv(ElementType.SINGLE_BARLINE.newInstance(), crotchet(), crotchet(), crotchet());
        ReflectionTestHelper.selectNote(env.coordinator(), 0);
        var result = EndingValidationResult.valid(EndingValidationResult.PrecedingAction.NONE, 0, 3);
        env.operations().makeFirstSecondEnding(result);

        var notification = captureSingleDidChange();
        var mutations = notification.getMutations();
        assertThat(mutations).hasSize(1);
        assertThat(mutations.getFirst()).isInstanceOf(RangeElementAddition.class);

        var rangeAddition = (RangeElementAddition) mutations.getFirst();
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
    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class CanMakeFirstSecondEndingWithRepeatLeftRightSplit {

        private Env buildEnv(StaffElement terminal) {
            return setupEnv(
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
    // canMakeFirstSecondEnding — auto-maintained terminal extension
    // -----------------------------------------------------------------------

    /**
     * Verifies that {@code canMakeFirstSecondEnding} is enabled when the selection ends
     * just before the song's auto-maintained terminal, which is not selectable
     * by the user.
     *
     * <p>Canonical line layout:
     * <pre>
     *  idx:  0            1        2        3             4        5        6
     *        REPEAT_LEFT  CROTCHET CROTCHET REPEAT_RIGHT  CROTCHET CROTCHET FINAL_DOUBLE_BARLINE
     * </pre>
     * Index 6 is the auto-maintained terminal — not interactable. The user selects 1–5.
     */
    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class CanMakeFirstSecondEndingAtSongEnd {

        @Test
        void testSelectionEndingBeforeAutoMaintainedTerminalIsValid() {
            var env = setupEnv(
                repeatLeft(), crotchet(), crotchet(), repeatRight(), crotchet(), crotchet(),
                finalDoubleBarline()
            );
            // Index 6 (FINAL_DOUBLE_BARLINE) is the auto-maintained terminal and is not
            // selectable; the user's selection covers only indices 1–5.
            ReflectionTestHelper.selectRange(env.coordinator(), 1, 5);
            assertThat(env.operations().canMakeFirstSecondEnding().isValid()).isTrue();
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
    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class HasEnclosingRepeatRules {

        // DOUBLE_BARLINE between REPEAT_LEFT and selection stops the scan
        @Test
        void testDoubleBarlineBlocksScan() {
            // idx: 0            1        2        3              4        5        6             7        8        9
            //      REPEAT_LEFT  CROTCHET CROTCHET DOUBLE_BARLINE CROTCHET CROTCHET REPEAT_RIGHT  CROTCHET CROTCHET SINGLE_BARLINE
            // Selection 4–9 hits DOUBLE_BARLINE before reaching REPEAT_LEFT → invalid
            var env = setupEnv(
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
            var env = setupEnv(
                repeatLeft(), crotchet(), crotchet(), finalDoubleBarline(),
                crotchet(), crotchet(), repeatRight(), crotchet(), crotchet(), singleBarline()
            );
            ReflectionTestHelper.selectRange(env.coordinator(), 4, 9);
            assertThat(env.operations().canMakeFirstSecondEnding().isValid()).isFalse();
        }

        // First line: reaching beginning of song without a left repeat is valid
        @Test
        void testFirstLineNoRepeatIsValid() {
            // The Song always starts with an initial line at index 0. Populate it
            // directly so the test line is at lineIndex 0, exercising the first-line exception.
            //
            // idx: 0        1        2             3        4        5
            //      CROTCHET CROTCHET REPEAT_RIGHT  CROTCHET CROTCHET SINGLE_BARLINE
            // Selection 1–5. Scan finds CROTCHET at idx 0 then reaches beginning; lineIndex==0 → valid
            var line = song.getLine(0);
            song.withoutMutationTracking(() -> {
                line.addElement(crotchet());
                line.addElement(crotchet());
                line.addElement(repeatRight());
                line.addElement(crotchet());
                line.addElement(crotchet());
                line.addElement(singleBarline());
            });
            var coordinator = ReflectionTestHelper.createCoordinatorForLine(line);
            var ops = new MusicEditOperations(song, coordinator);
            messageCenterMock = mockStatic(MessageCenter.class);

            ReflectionTestHelper.selectRange(coordinator, 1, 5);
            assertThat(ops.canMakeFirstSecondEnding().isValid()).isTrue();
        }

        // Non-first line: reaching beginning of song without a left repeat is invalid
        @Test
        void testNonFirstLineNoRepeatIsInvalid() {
            // Line 0: CROTCHET CROTCHET CROTCHET
            // Line 1: CROTCHET CROTCHET REPEAT_RIGHT  CROTCHET CROTCHET SINGLE_BARLINE
            //         idx: 0        1        2             3        4        5
            // Selection 0–5 on line 1. No repeat found; lineIndex==1 → invalid
            var line0 = new Line(song);
            song.withoutMutationTracking(() -> {
                line0.addElement(crotchet());
                line0.addElement(crotchet());
                line0.addElement(crotchet());
                song.addLine(line0);
            });

            var env = setupEnv(
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
            var line0 = new Line(song);
            song.withoutMutationTracking(() -> {
                line0.addElement(repeatLeft());
                line0.addElement(crotchet());
                line0.addElement(crotchet());
                song.addLine(line0);
            });

            var env = setupEnv(
                crotchet(), crotchet(), repeatRight(), crotchet(), crotchet(), singleBarline()
            );
            ReflectionTestHelper.selectRange(env.coordinator(), 0, 5);
            assertThat(env.operations().canMakeFirstSecondEnding().isValid()).isTrue();
        }
    }

}
