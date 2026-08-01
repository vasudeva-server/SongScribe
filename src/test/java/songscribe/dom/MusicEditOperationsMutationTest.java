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
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static songscribe.dom.StaffElementFactory.*;

import java.util.List;

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
import songscribe.message.mutation.DiminuendoAddition;
import songscribe.message.mutation.ElementField;
import songscribe.message.mutation.ElementModification;
import songscribe.message.mutation.Mutation;
import songscribe.message.mutation.RangeElementAddition;
import songscribe.message.mutation.TieAddition;
import songscribe.message.mutation.TieRemoval;
import songscribe.message.mutation.TupletAddition;
import songscribe.message.mutation.TupletRemoval;
import songscribe.message.notification.SongDidChangeNotification;
import songscribe.ui.MusicEditOperations;
import songscribe.ui.action.TupletAction;
import songscribe.ui.component.ScoreView;
import songscribe.ui.selection.ReflectionTestHelper;
import songscribe.ui.selection.SelectionCoordinator;
import songscribe.undo.MutationReplayer;

class MusicEditOperationsMutationTest extends UnitTest {

    /** Number of quavers spanned by the beam fixture in the stem-direction tests. */
    private static final int BEAM_GROUP_SIZE = 4;

    /** Number of crotchets spanned by the tie fixture in the stem-direction tests. */
    private static final int TIED_NOTE_COUNT = 3;

    /**
     * Elements eligible for a stem change in the three-element fixtures whose middle
     * element is stemless — [note, rest, note] and [note, whole note, note].
     */
    private static final int STEMMED_NOTE_COUNT = 2;

    /** Last element index of the three-element fixtures whose middle element is stemless. */
    private static final int STEMLESS_MIDDLE_FIXTURE_LAST_INDEX = 2;

    /** Last element index of the three-note fixture in the single-note extend test. */
    private static final int SINGLE_NOTE_EXTEND_END = 2;

    /** Last element index of the [grace, note, note] fixture. */
    private static final int GRACE_FIXTURE_LAST_INDEX = 2;

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

    /**
     * Returns the element indices the given mutations modified, so a test can assert which
     * elements were touched rather than merely how many.
     */
    private static List<Integer> modifiedIndices(List<Mutation> mutations) {
        return mutations.stream()
            .filter(ElementModification.class::isInstance)
            .map(mutation -> ((ElementModification) mutation).index())
            .toList();
    }

    private void verifyNoDidChange() {
        var mock = messageCenterMock;

        if (mock == null) {
            throw new IllegalStateException("messageCenterMock not set — call setupEnv() first");
        }

        mock.verify(() -> MessageCenter.post(any(SongDidChangeNotification.class)), never());
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

    @Test
    void testToggleBeamingExcludesTrailingGraceNote() {
        var env = setupEnv(quaver(), quaver(), graceQuaver());
        ReflectionTestHelper.selectRange(env.coordinator(), 0, 2);
        env.operations().toggleBeaming();

        var mutations = captureSingleDidChange().getMutations();
        assertThat(mutations).hasSize(1);
        var addition = (BeamingAddition) mutations.getFirst();
        assertThat(addition.beam().getAnchorElementIndex()).isEqualTo(0);
        assertThat(addition.beam().getEndElementIndex())
            .as("beam ends at the last non-grace note")
            .isEqualTo(1);
    }

    @Test
    void testToggleBeamingExcludesLeadingGraceNote() {
        var env = setupEnv(graceQuaver(), quaver(), quaver());
        ReflectionTestHelper.selectRange(env.coordinator(), 0, 2);
        env.operations().toggleBeaming();

        var mutations = captureSingleDidChange().getMutations();
        assertThat(mutations).hasSize(1);
        var addition = (BeamingAddition) mutations.getFirst();
        assertThat(addition.beam().getAnchorElementIndex())
            .as("beam starts at the first non-grace note")
            .isEqualTo(1);
        assertThat(addition.beam().getEndElementIndex()).isEqualTo(2);
    }

    @Test
    void testToggleTupletExcludesTrailingGraceNote() {
        // Three crotchets, so the triplet's written value divides out exactly.
        var env = setupEnv(crotchet(), crotchet(), crotchet(), graceQuaver());
        ReflectionTestHelper.selectRange(env.coordinator(), 0, 3);
        var ops = env.operations();
        ops.toggleTuplet(TupletAction.Tuplet.TRIPLET.getSize(), ops.canToggleTuplet());

        var mutations = captureSingleDidChange().getMutations();
        assertThat(mutations).hasSize(1);
        var addition = (TupletAddition) mutations.getFirst();
        assertThat(addition.tuplet().getAnchorElementIndex()).isEqualTo(0);
        assertThat(addition.tuplet().getEndElementIndex())
            .as("tuplet ends at the last non-grace note")
            .isEqualTo(2);
    }

    @Test
    void testToggleTupletExcludesLeadingGraceNote() {
        // Three crotchets, so the triplet's written value divides out exactly.
        var env = setupEnv(graceQuaver(), crotchet(), crotchet(), crotchet());
        ReflectionTestHelper.selectRange(env.coordinator(), 0, 3);
        var ops = env.operations();
        ops.toggleTuplet(TupletAction.Tuplet.TRIPLET.getSize(), ops.canToggleTuplet());

        var mutations = captureSingleDidChange().getMutations();
        assertThat(mutations).hasSize(1);
        var addition = (TupletAddition) mutations.getFirst();
        assertThat(addition.tuplet().getAnchorElementIndex())
            .as("tuplet starts at the first non-grace note")
            .isEqualTo(1);
        assertThat(addition.tuplet().getEndElementIndex()).isEqualTo(3);
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

    @Test
    void testToggleTieAcrossBarlineEmitsTieAdditionSpanningTheNotes() {
        // [CROTCHET(0), SINGLE_BARLINE(1), CROTCHET(2)] — selecting the barline along with
        // its neighboring notes ties the notes, with the barline inside the span (refs #527).
        var env = setupEnv(crotchet(), singleBarline(), crotchet());
        ReflectionTestHelper.selectRange(env.coordinator(), 0, 2);
        env.operations().toggleTie();

        var notification = captureSingleDidChange();
        var mutations = notification.getMutations();
        assertThat(mutations).hasSize(1);
        assertThat(mutations.getFirst()).isInstanceOf(TieAddition.class);
        var addition = (TieAddition) mutations.getFirst();
        assertThat(addition.tie().getAnchorElementIndex()).isEqualTo(0);
        assertThat(addition.tie().getEndElementIndex()).isEqualTo(2);
        assertThat(addition.line()).isSameAs(env.line());
    }

    @Test
    void testToggleTieAcrossBarlineRemoveEmitsTieRemoval() {
        // Untying is as common as tying: the removal branch must find the existing tie
        // when a barline sits inside its span, not only when the notes are adjacent.
        var env = setupEnv(crotchet(), singleBarline(), crotchet());
        var beginNote = env.line().getElement(0);
        var endNote = env.line().getElement(2);
        env.line().getSong().withoutMutationTracking(
            () -> env.line().addRangeElement(new Tie(beginNote, endNote)));
        ReflectionTestHelper.selectRange(env.coordinator(), 0, 2);
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
        song.withoutMutationTracking(() -> env.line().addTuplet(Tuplet.withUnresolvedRatio(
            env.line().getElement(0), env.line().getElement(2), TupletAction.Tuplet.TRIPLET.getSize())));
        ReflectionTestHelper.selectRange(env.coordinator(), 0, 2);
        // The Remove action, not a matching grade — REMOVE is the only way to delete a tuplet.
        env.operations().toggleTuplet(TupletAction.Tuplet.REMOVE.getSize(), env.operations().canToggleTuplet());

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
        // Five crotchets, so the incoming quintuplet's written value divides out exactly.
        var env = setupEnv(crotchet(), crotchet(), crotchet(), crotchet(), crotchet());
        song.withoutMutationTracking(() -> env.line().addTuplet(Tuplet.withUnresolvedRatio(
            env.line().getElement(0), env.line().getElement(4), TupletAction.Tuplet.TRIPLET.getSize())));
        ReflectionTestHelper.selectRange(env.coordinator(), 0, 4);
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
        assertThat(addition.tuplet().getEndElementIndex()).isEqualTo(4);
        assertThat(addition.tuplet().getGrade()).isEqualTo(TupletAction.Tuplet.QUINTUPLET.getSize());
        assertThat(addition.line()).isSameAs(env.line());
    }

    @Test
    void testToggleTupletMatchingGradeIsNoOp() {
        // The existing grade is shown checked, so re-picking it must confirm rather than
        // delete: a checked radio item that removes what it reports on is a trap. Remove
        // is the one way to delete a tuplet.
        var env = setupEnv(crotchet(), crotchet(), crotchet());
        var grade = TupletAction.Tuplet.TRIPLET.getSize();
        song.withoutMutationTracking(() -> env.line().addTuplet(Tuplet.withUnresolvedRatio(
            env.line().getElement(0), env.line().getElement(2), grade)));
        ReflectionTestHelper.selectRange(env.coordinator(), 0, 2);
        env.operations().toggleTuplet(grade, env.operations().canToggleTuplet());

        verifyNoDidChange();
        var tuplets = env.line().findRangeElements(Tuplet.class);
        assertThat(tuplets).as("the tuplet survives re-picking its own grade").hasSize(1);
        assertThat(tuplets.getFirst().getGrade()).isEqualTo(grade);
    }

    @Test
    void testToggleTupletPartialCoverageInExistingTupletThrows() {
        // Partial-coverage selection inside an existing tuplet is a caller bug:
        // the UI disables this path, and toggleTuplet now throws IllegalStateException
        // rather than silently replacing the tuplet with a sub-range one.
        var env = setupEnv(crotchet(), crotchet(), crotchet());
        var originalTuplet = Tuplet.withUnresolvedRatio(
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
        song.withoutMutationTracking(() -> env.line().addTuplet(Tuplet.withUnresolvedRatio(
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
        env.operations().addHairpinToSelection(crescendo);

        var notification = captureSingleDidChange();
        var mutations = notification.getMutations();
        assertThat(mutations).hasSize(1);

        if (crescendo) {
            assertThat(mutations.getFirst()).isInstanceOf(CrescendoAddition.class);
        } else {
            assertThat(mutations.getFirst()).isInstanceOf(DiminuendoAddition.class);
        }
    }

    // canAddDynamicsToSelection(), canRemoveDynamicsFromSelection() and
    // removeDynamicsFromSelection() were removed in Phase 8, superseded by
    // resolveHairpinAction() (covered in HairpinActionStateTest) and the
    // select-then-Delete removal path.

    // -----------------------------------------------------------------------
    // Hairpin execution — span, point-dynamic strip and undo
    // -----------------------------------------------------------------------

    /** The point dynamic the strip tests place on an element. */
    private static final DynamicAttachment.DynamicType POINT_DYNAMIC =
        DynamicAttachment.DynamicType.FORTE;

    /** The selection {@link #setupExtendEnv} is built for: the two notes past the crescendo. */
    private static final int EXTEND_SELECTION_BEGIN = 2;
    private static final int EXTEND_SELECTION_END = 3;

    /**
     * Four crotchets whose first two already carry a crescendo, with a point dynamic
     * on element 0. Selecting [2, 3] then extends that crescendo to [0, 3], so element
     * 0 lies inside the merged range but two notes outside the selection.
     */
    private Env setupExtendEnv() {
        var env = setupEnv(crotchet(), crotchet(), crotchet(), crotchet());
        var line = env.line();

        song.withoutMutationTracking(() -> {
            line.addCrescendo(new Crescendo(line.getElement(0), line.getElement(1)));
            var first = line.getElement(0);
            first.addAttachment(new DynamicAttachment(first, POINT_DYNAMIC));
        });

        return env;
    }

    private static List<Crescendo> crescendosOf(Line line) {
        return line.getRangeElements().stream()
            .filter(Crescendo.class::isInstance)
            .map(Crescendo.class::cast)
            .toList();
    }

    /** Replays {@code mutations} in reverse order under replay mode, exactly as undo does. */
    private void replayUndo(List<? extends Mutation> mutations) {
        var scoreView = mock(ScoreView.class);
        when(scoreView.getSong()).thenReturn(song);

        song.withModification(() -> song.withReplay(() -> {
            for (var i = mutations.size() - 1; i >= 0; i--) {
                MutationReplayer.applyUndo(scoreView, mutations.get(i));
            }
        }));
    }

    @Test
    void testAddHairpinStripsPointDynamicsAcrossMergedRangeNotJustSelection() {
        var env = setupExtendEnv();
        ReflectionTestHelper.selectRange(env.coordinator(), EXTEND_SELECTION_BEGIN, EXTEND_SELECTION_END);

        env.operations().addHairpinToSelection(true);

        assertThat(env.line().getElement(0).findAttachment(DynamicAttachment.class))
            .as("a point dynamic inside the merged hairpin range must be stripped "
                + "even though it sits outside the selection")
            .isNull();
    }

    @Test
    void testOneUndoRestoresBothTheHairpinAndTheStrippedPointDynamic() {
        var env = setupExtendEnv();
        var line = env.line();
        ReflectionTestHelper.selectRange(env.coordinator(), EXTEND_SELECTION_BEGIN, EXTEND_SELECTION_END);

        env.operations().addHairpinToSelection(true);

        // Captured before the replay, which posts a notification of its own.
        var mutations = captureSingleDidChange().getMutations();
        replayUndo(mutations);

        var restored = line.getElement(0).findAttachment(DynamicAttachment.class);

        assertThat(restored)
            .as("one undo must restore the stripped point dynamic — a raw "
                + "StaffElement.removeAttachment records no mutation and loses it forever")
            .isNotNull();

        var remaining = crescendosOf(line);
        assertThat(remaining).hasSize(1);

        var original = remaining.getFirst();
        assertAll(
            () -> assertThat(restored.getType()).isEqualTo(POINT_DYNAMIC),
            () -> assertThat(original.getAnchorElementIndex())
                .as("undo must restore the pre-extend crescendo anchor")
                .isEqualTo(0),
            () -> assertThat(original.getEndElementIndex())
                .as("undo must restore the pre-extend crescendo end")
                .isEqualTo(1));
    }

    @Test
    void testSingleNoteExtendNeverProducesAOneElementHairpin() {
        var env = setupEnv(crotchet(), crotchet(), crotchet());
        var line = env.line();
        song.withoutMutationTracking(() ->
            line.addCrescendo(new Crescendo(line.getElement(0), line.getElement(1))));

        // One note selected, adjacent to the existing crescendo → EXTEND_CRESCENDO.
        ReflectionTestHelper.selectNote(env.coordinator(), 2);
        env.operations().addHairpinToSelection(true);

        var additions = captureSingleDidChange().getMutations().stream()
            .filter(CrescendoAddition.class::isInstance)
            .map(CrescendoAddition.class::cast)
            .toList();
        assertThat(additions).hasSize(1);

        var added = additions.getFirst().crescendo();
        var resulting = crescendosOf(line);
        assertThat(resulting).hasSize(1);

        var survivor = resulting.getFirst();
        assertAll(
            () -> assertThat(added.getAnchorElementIndex())
                .as("the recorded addition must already carry the resolved span, "
                    + "not a degenerate one-element hairpin left for the merge to widen")
                .isNotEqualTo(added.getEndElementIndex()),
            () -> assertThat(survivor.getAnchorElementIndex()).isEqualTo(0),
            () -> assertThat(survivor.getEndElementIndex()).isEqualTo(SINGLE_NOTE_EXTEND_END));
    }

    @Test
    void testHairpinAnchoredAtGraceNoteUsesTheGraceIndexNotTheHost() {
        var env = setupEnv(graceQuaver(), crotchet(), crotchet());
        ReflectionTestHelper.selectRange(env.coordinator(), 0, GRACE_FIXTURE_LAST_INDEX);

        env.operations().addHairpinToSelection(true);

        var result = crescendosOf(env.line());
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getAnchorElementIndex())
            .as("the hairpin must anchor on the selected grace note, not on its host")
            .isEqualTo(0);
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
    void testCanMakeFirstSecondEndingReturnsNoneWhenPrecedingContentAndSelectionStartsWithNote() {
        // checkPrecedingElement: preceding element is content; selection starts with a note
        // (not a barline or left repeat) → #306: action is NONE (note-anchored, no barline
        // inserted), result is valid.
        //
        // Layout: [REPEAT_LEFT(0), CROTCHET(1), CROTCHET(2), CROTCHET(3),
        //          REPEAT_RIGHT(4), CROTCHET(5), CROTCHET(6), SINGLE_BARLINE(7)]
        // Selection 2–7. Preceding element at index 1 is CROTCHET (content).
        // selectionBeginType = CROTCHET → NONE action, anchored at the note.
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
            .as("preceding action must be NONE when selection starts with a note after content")
            .isEqualTo(EndingValidationResult.PrecedingAction.NONE);
        assertThat(result.getSpanStart())
            .as("span start must be the selection begin index")
            .isEqualTo(2);
        assertThat(result.getSpanEnd())
            .as("span end must be the selection end index")
            .isEqualTo(7);
    }

    @Test
    void testCanMakeFirstSecondEndingReturnsExtendSpanWhenPrecedingElementIsRepeatLeftRight() {
        // #306: a REPEAT_LEFT_RIGHT immediately before the selection anchors the 1st bracket
        // to that barline, just like SINGLE_BARLINE/REPEAT_LEFT — action is EXTEND_SPAN, not
        // NONE or invalid.
        //
        // Layout: [REPEAT_LEFT(0), CROTCHET(1), CROTCHET(2), REPEAT_LEFT_RIGHT(3), CROTCHET(4),
        //          CROTCHET(5), CROTCHET(6), REPEAT_RIGHT(7), CROTCHET(8), CROTCHET(9),
        //          SINGLE_BARLINE(10)]
        // Selection 4–10. Preceding element at index 3 is REPEAT_LEFT_RIGHT → EXTEND_SPAN;
        // spanStart extends back to 3. hasEnclosingRepeat scans back from 3 and finds
        // REPEAT_LEFT_RIGHT itself is a repeat, so the enclosing-repeat precondition is
        // satisfied without needing to reach index 0.
        var env = setupEnv(
            repeatLeft(), crotchet(), crotchet(), repeatLeftRight(), crotchet(),
            crotchet(), crotchet(), repeatRight(), crotchet(), crotchet(), singleBarline()
        );
        ReflectionTestHelper.selectRange(env.coordinator(), 4, 10);
        var result = env.operations().canMakeFirstSecondEnding();
        assertThat(result.isValid())
            .as("canMakeFirstSecondEnding() must be valid when preceding element is REPEAT_LEFT_RIGHT")
            .isTrue();
        assertThat(result.getPrecedingAction())
            .as("preceding action must be EXTEND_SPAN when preceding element is REPEAT_LEFT_RIGHT")
            .isEqualTo(EndingValidationResult.PrecedingAction.EXTEND_SPAN);
        assertThat(result.getSpanStart())
            .as("span start must be extended back to the preceding REPEAT_LEFT_RIGHT index")
            .isEqualTo(3);
        assertThat(result.getSpanEnd())
            .as("span end must remain the selection end index")
            .isEqualTo(10);
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

    @Test
    void testCanMakeFirstSecondEndingReturnsExtendSpanWhenPrecedingElementIsRepeatLeft() {
        // checkPrecedingElement: preceding element is a plain REPEAT_LEFT → action is
        // EXTEND_SPAN; the span start is extended backward to include that repeat sign.
        //
        // Layout: [REPEAT_LEFT(0), CROTCHET(1), REPEAT_RIGHT(2), CROTCHET(3),
        //          CROTCHET(4), SINGLE_BARLINE(5)]
        // Selection 1–5. Preceding element at index 0 is REPEAT_LEFT → EXTEND_SPAN;
        // spanStart extends back to 0.
        var env = setupEnv(
            repeatLeft(), crotchet(), repeatRight(), crotchet(), crotchet(), singleBarline()
        );
        ReflectionTestHelper.selectRange(env.coordinator(), 1, 5);
        var result = env.operations().canMakeFirstSecondEnding();
        assertThat(result.isValid())
            .as("canMakeFirstSecondEnding() must be valid when preceding element is a left repeat")
            .isTrue();
        assertThat(result.getPrecedingAction())
            .as("preceding action must be EXTEND_SPAN when preceding element is a left repeat")
            .isEqualTo(EndingValidationResult.PrecedingAction.EXTEND_SPAN);
        assertThat(result.getSpanStart())
            .as("span start must be extended back to the preceding REPEAT_LEFT index")
            .isZero();
        assertThat(result.getSpanEnd())
            .as("span end must remain the selection end index")
            .isEqualTo(5);
    }

    @Test
    void testCanMakeFirstSecondEndingSkipsGraceNotesToReachPrecedingBarline() {
        // Grace notes carry no duration and are skipped everywhere else in this
        // validation, so they must not hide the barline that anchors the 1st bracket.
        //
        // Layout: [REPEAT_LEFT(0), CROTCHET(1), SINGLE_BARLINE(2), GRACE_QUAVER(3),
        //          GRACE_QUAVER(4), CROTCHET(5), REPEAT_RIGHT(6), CROTCHET(7),
        //          CROTCHET(8), SINGLE_BARLINE(9)]
        // Selection 5–9. Skipping both grace notes reaches SINGLE_BARLINE at index 2
        // → EXTEND_SPAN with spanStart 2, so the grace notes fall inside the ending.
        var env = setupEnv(
            repeatLeft(), crotchet(), singleBarline(), graceQuaver(), graceQuaver(),
            crotchet(), repeatRight(), crotchet(), crotchet(), singleBarline()
        );
        ReflectionTestHelper.selectRange(env.coordinator(), 5, 9);
        var result = env.operations().canMakeFirstSecondEnding();
        assertThat(result.isValid())
            .as("grace notes before the selection must not make the ending invalid")
            .isTrue();
        assertThat(result.getPrecedingAction())
            .as("preceding action must be EXTEND_SPAN once the grace notes are skipped")
            .isEqualTo(EndingValidationResult.PrecedingAction.EXTEND_SPAN);
        assertThat(result.getSpanStart())
            .as("span start must be extended back past the grace notes to the barline")
            .isEqualTo(2);
        assertThat(result.getSpanEnd())
            .as("span end must remain the selection end index")
            .isEqualTo(9);
    }

    @Test
    void testCanMakeFirstSecondEndingSkipsGraceNoteToReachPrecedingNote() {
        // Same skipping rule, but the element behind the grace note is a note rather than
        // a barline, so the bracket anchors at the selection start with no extension.
        //
        // Layout: [REPEAT_LEFT(0), CROTCHET(1), GRACE_QUAVER(2), CROTCHET(3),
        //          REPEAT_RIGHT(4), CROTCHET(5), CROTCHET(6), SINGLE_BARLINE(7)]
        // Selection 3–7. Skipping the grace note reaches CROTCHET at index 1 → NONE.
        var env = setupEnv(
            repeatLeft(), crotchet(), graceQuaver(), crotchet(),
            repeatRight(), crotchet(), crotchet(), singleBarline()
        );
        ReflectionTestHelper.selectRange(env.coordinator(), 3, 7);
        var result = env.operations().canMakeFirstSecondEnding();
        assertThat(result.isValid())
            .as("a grace note before the selection must not make the ending invalid")
            .isTrue();
        assertThat(result.getPrecedingAction())
            .as("preceding action must be NONE when the element behind the grace note is a note")
            .isEqualTo(EndingValidationResult.PrecedingAction.NONE);
        assertThat(result.getSpanStart())
            .as("span start must be the selection begin index")
            .isEqualTo(3);
        assertThat(result.getSpanEnd())
            .as("span end must remain the selection end index")
            .isEqualTo(7);
    }

    @Test
    void testCanMakeFirstSecondEndingReturnsFalseWhenPrecedingElementIsRightRepeat() {
        // A REPEAT_RIGHT immediately before the selection closes the preceding section,
        // so the selection is not inside a repeated section and must be rejected.
        //
        // Layout: [REPEAT_LEFT(0), CROTCHET(1), CROTCHET(2), REPEAT_RIGHT(3),
        //          CROTCHET(4), CROTCHET(5), REPEAT_RIGHT(6), CROTCHET(7),
        //          CROTCHET(8), SINGLE_BARLINE(9)]
        // Selection 4–9. Preceding element at index 3 is REPEAT_RIGHT.
        // validateEndingStructure [4..9] passes (one REPEAT_RIGHT at 6, 4 content elements).
        // hasEnclosingRepeat starts its backward scan at index 3, sees the REPEAT_RIGHT
        // and returns invalid there — it never reaches the REPEAT_LEFT at index 0, and
        // checkPrecedingElement is never called. The rejection belongs to that scan, not
        // to the preceding-element check.
        var env = setupEnv(
            repeatLeft(), crotchet(), crotchet(), repeatRight(),
            crotchet(), crotchet(), repeatRight(), crotchet(), crotchet(), singleBarline()
        );
        ReflectionTestHelper.selectRange(env.coordinator(), 4, 9);
        assertThat(env.operations().canMakeFirstSecondEnding().isValid())
            .as("canMakeFirstSecondEnding() must return invalid when preceding element is a right-repeat")
            .isFalse();
    }

    // -----------------------------------------------------------------------
    // makeFirstSecondEnding — EXTEND_SPAN path (row 59)
    // -----------------------------------------------------------------------

    @Test
    void testMakeFirstSecondEndingWithExtendSpanEmitsOnlyRangeElementAdditionWithExtendedBounds() {
        // EXTEND_SPAN: the preceding element is already a barline that should be included
        // in the span. No new element is inserted; only one RangeElementAddition is emitted,
        // and its Ending spans from the extended start (the barline index) to the original end.
        //
        // Layout: [SINGLE_BARLINE(0), CROTCHET(1), CROTCHET(2), CROTCHET(3)]
        // Result: EXTEND_SPAN, spanStart=0, spanEnd=3 (barline already part of span).
        var env = setupEnv(singleBarline(), crotchet(), crotchet(), crotchet());
        ReflectionTestHelper.selectNote(env.coordinator(), 1);
        var result = EndingValidationResult.valid(EndingValidationResult.PrecedingAction.EXTEND_SPAN, 0, 3);
        env.operations().makeFirstSecondEnding(result);

        var notification = captureSingleDidChange();
        var mutations = notification.getMutations();
        assertThat(mutations).as("only one mutation emitted for EXTEND_SPAN path").hasSize(1);
        assertThat(mutations.getFirst())
            .as("mutation is a RangeElementAddition")
            .isInstanceOf(RangeElementAddition.class);

        var rangeAddition = (RangeElementAddition) mutations.getFirst();
        assertThat(rangeAddition.element()).as("added element is an Ending").isInstanceOf(Ending.class);
        var ending = (Ending) rangeAddition.element();
        assertThat(ending.getAnchorElementIndex())
            .as("Ending anchor is at the extended start (the barline index)")
            .isEqualTo(0);
        assertThat(ending.getEndElementIndex())
            .as("Ending end is at the original selection end")
            .isEqualTo(3);
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

    @Test
    void testFlipStemDirectionSkipsRestElements() {
        // [CROTCHET(0), CROTCHET_REST(1), CROTCHET(2)] — all three selected.
        // The rest at index 1 must not receive an ElementModification; only the
        // two notes at indices 0 and 2 are flipped.
        var env = setupEnv(crotchet(), crotchetRest(), crotchet());
        ReflectionTestHelper.selectRange(env.coordinator(), 0, STEMLESS_MIDDLE_FIXTURE_LAST_INDEX);
        env.operations().flipStemDirection();

        var notification = captureSingleDidChange();
        var mutations = notification.getMutations();

        assertThat(mutations)
            .as("exactly two modifications — one per note, none for the rest")
            .hasSize(STEMMED_NOTE_COUNT)
            .allSatisfy(mutation -> assertThat(mutation).isInstanceOf(ElementModification.class));

        // Counting the modifications is not enough: a guard that skipped the wrong element
        // would still produce two. Name the indices that must have been touched.
        assertThat(modifiedIndices(mutations))
            .as("the notes on either side of the rest, not the rest itself")
            .containsExactlyInAnyOrder(0, STEMLESS_MIDDLE_FIXTURE_LAST_INDEX);
    }

    @Test
    void testFlipStemDirectionSkipsWholeNotes() {
        // [CROTCHET(0), SEMIBREVE(1), CROTCHET(2)] — all three selected. A whole note
        // has no stem, so it must not receive an ElementModification.
        var env = setupEnv(crotchet(), semibreve(), crotchet());
        ReflectionTestHelper.selectRange(env.coordinator(), 0, STEMLESS_MIDDLE_FIXTURE_LAST_INDEX);
        env.operations().flipStemDirection();

        var notification = captureSingleDidChange();
        var mutations = notification.getMutations();

        assertThat(mutations)
            .as("exactly two modifications — one per stemmed note, none for the whole note")
            .hasSize(STEMMED_NOTE_COUNT)
            .allSatisfy(mutation -> assertThat(mutation).isInstanceOf(ElementModification.class));

        assertThat(modifiedIndices(mutations))
            .as("the crotchets on either side of the whole note, not the whole note itself")
            .containsExactlyInAnyOrder(0, STEMLESS_MIDDLE_FIXTURE_LAST_INDEX);
    }

    @Test
    void testFlipStemDirectionSkipsWholeNoteTiePartnersOutsideSelection() {
        // [CROTCHET(0), SEMIBREVE(1), CROTCHET(2)], tie spans [0..2]. Only index 0 is
        // selected, so indices 1 and 2 are pulled in as tie partners — but the whole
        // note at index 1 is stemless and must be skipped while the tie chain is still
        // walked through it to reach index 2.
        var env = setupEnv(crotchet(), semibreve(), crotchet());
        song.withoutMutationTracking(
            () -> env.line().addTie(
                new Tie(
                    env.line().getElement(0),
                    env.line().getElement(STEMLESS_MIDDLE_FIXTURE_LAST_INDEX)
                )
            )
        );
        ReflectionTestHelper.selectNote(env.coordinator(), 0);
        env.operations().flipStemDirection();

        var notification = captureSingleDidChange();
        var mutations = notification.getMutations();

        assertThat(mutations)
            .as("selected note and the far tie partner are flipped; the whole note between them is not")
            .hasSize(STEMMED_NOTE_COUNT)
            .allSatisfy(mutation -> assertThat(mutation).isInstanceOf(ElementModification.class));

        // The count alone cannot tell the two outcomes apart: skipping the far tie partner
        // and flipping the whole note instead also yields two modifications.
        assertThat(modifiedIndices(mutations))
            .as("the selected crotchet and the far tie partner, not the whole note between them")
            .containsExactlyInAnyOrder(0, STEMLESS_MIDDLE_FIXTURE_LAST_INDEX);
    }

    @Test
    void testFlipStemDirectionEmitsNoNotificationWhenSelectionIsAllStemless() {
        // Nothing in [SEMIBREVE(0), CROTCHET_REST(1)] carries a stem, so the flip has
        // nothing to change. Recording mutations anyway would mark the song modified and
        // push an undo entry that undoes nothing the user can see.
        var env = setupEnv(semibreve(), crotchetRest());
        ReflectionTestHelper.selectRange(env.coordinator(), 0, 1);
        env.operations().flipStemDirection();

        verifyNoDidChange();
    }

    @Test
    void testFlipStemDirectionDeduplicatesBeamGroupWhenPartiallySelected() {
        // Beam covers [0..3] (four eighth-notes). Selection covers only [0..1],
        // a strict subset of the beam group. flipStemDirection must flip the
        // entire beam group (indices 0–3) exactly once — not once per selected note.
        var env = setupEnv(quaver(), quaver(), quaver(), quaver());
        song.withoutMutationTracking(
            () -> env.line().addBeaming(new Beam(env.line().getElement(0), env.line().getElement(3)))
        );
        ReflectionTestHelper.selectRange(env.coordinator(), 0, 1);
        env.operations().flipStemDirection();

        var notification = captureSingleDidChange();
        var mutations = notification.getMutations();

        // All four beam members are flipped; each appears exactly once.
        assertThat(mutations)
            .as("all four beam members flipped exactly once despite partial selection")
            .hasSize(4);

        for (var mutation : mutations) {
            assertThat(mutation).isInstanceOf(ElementModification.class);
        }
    }

    @Test
    void testFlipStemDirectionAlsoFlipsTiePartnersOutsideSelection() {
        // [CROTCHET(0), CROTCHET(1), CROTCHET(2)], tie spans [0..2].
        // Selection covers only index 0. Tie partners at indices 1 and 2 fall
        // outside the selection but must also be flipped.
        var env = setupEnv(crotchet(), crotchet(), crotchet());
        song.withoutMutationTracking(
            () -> env.line().addTie(new Tie(env.line().getElement(0), env.line().getElement(2)))
        );
        ReflectionTestHelper.selectNote(env.coordinator(), 0);
        env.operations().flipStemDirection();

        var notification = captureSingleDidChange();
        var mutations = notification.getMutations();

        // Index 0 is in selection (directly flipped); indices 1 and 2 are tie
        // partners outside the selection (also flipped). Total: 3 modifications.
        assertThat(mutations)
            .as("selected note plus both tie partners outside selection are all flipped")
            .hasSize(3);

        for (var mutation : mutations) {
            assertThat(mutation).isInstanceOf(ElementModification.class);
        }
    }

    /**
     * Pins the stem direction of the given elements (auto off) without recording mutations.
     * Elements default to {@code stemDirectionAuto == true}, so without this an
     * {@code autoStemDirection()} assertion would hold before the call under test even ran.
     */
    private void forceManualStemDirection(Env env, int... indices) {
        song.withoutMutationTracking(() -> {
            for (var index : indices) {
                env.line().getElement(index).setStemDirectionAuto(false);
            }
        });
    }

    @Test
    void testAutoStemDirectionRestoresAutoOnSingleFlippedNote() {
        var env = setupEnv(crotchet());
        forceManualStemDirection(env, 0);
        ReflectionTestHelper.selectNote(env.coordinator(), 0);
        env.operations().autoStemDirection();

        var notification = captureSingleDidChange();
        var mutations = notification.getMutations();
        assertThat(mutations).hasSize(1);

        var mutation = (ElementModification) mutations.getFirst();
        assertThat(mutation.fields()).contains(ElementField.STEM_DIRECTION_AUTO);
        assertThat(env.line().getElement(0).isStemDirectionAuto())
            .as("note's stemDirectionAuto flag must transition false -> true")
            .isTrue();
    }

    @Test
    void testAutoStemDirectionEmitsNoNotificationWhenAlreadyAuto() {
        // Elements start out auto, so restoring auto changes nothing. Recording mutations
        // anyway would dirty the song and push an empty entry onto the undo stack.
        var env = setupEnv(crotchet(), crotchet(), crotchet());
        ReflectionTestHelper.selectRange(env.coordinator(), 0, TIED_NOTE_COUNT - 1);
        env.operations().autoStemDirection();

        verifyNoDidChange();
    }

    @Test
    void testAutoStemDirectionSkipsRestElements() {
        // [CROTCHET(0), CROTCHET_REST(1), CROTCHET(2)] — all three selected, all flipped.
        // The rest at index 1 must not receive an ElementModification.
        var env = setupEnv(crotchet(), crotchetRest(), crotchet());
        forceManualStemDirection(env, 0, 2);
        ReflectionTestHelper.selectRange(env.coordinator(), 0, TIED_NOTE_COUNT - 1);
        env.operations().autoStemDirection();

        var notification = captureSingleDidChange();
        var mutations = notification.getMutations();

        assertThat(mutations)
            .as("exactly two modifications — one per note, none for the rest")
            .hasSize(STEMMED_NOTE_COUNT);
    }

    @Test
    void testAutoStemDirectionAppliesToWholeBeamGroupWhenPartiallySelected() {
        // Beam covers [0..3] (four eighth-notes), all flipped. Selection covers only [0..1],
        // a strict subset of the beam group. autoStemDirection() must restore auto for the
        // entire beam group (indices 0-3), not just the selected notes.
        var env = setupEnv(quaver(), quaver(), quaver(), quaver());
        song.withoutMutationTracking(
            () -> env.line().addBeaming(
                new Beam(env.line().getElement(0), env.line().getElement(BEAM_GROUP_SIZE - 1))
            )
        );
        forceManualStemDirection(env, 0, 1, 2, 3);
        ReflectionTestHelper.selectRange(env.coordinator(), 0, 1);
        env.operations().autoStemDirection();

        var notification = captureSingleDidChange();
        var mutations = notification.getMutations();

        assertThat(mutations)
            .as("all four beam members updated exactly once despite partial selection")
            .hasSize(BEAM_GROUP_SIZE);

        for (var index = 0; index < BEAM_GROUP_SIZE; index++) {
            assertThat(env.line().getElement(index).isStemDirectionAuto())
                .as("beam member at index %d must have stemDirectionAuto restored to true", index)
                .isTrue();
        }
    }

    @Test
    void testAutoStemDirectionAlsoAppliesToTiePartnersOutsideSelection() {
        // [CROTCHET(0), CROTCHET(1), CROTCHET(2)] all flipped, tie spans [0..2].
        // Selection covers only index 0. Tie partners at indices 1 and 2 fall
        // outside the selection but must also have stemDirectionAuto restored.
        var env = setupEnv(crotchet(), crotchet(), crotchet());
        song.withoutMutationTracking(
            () -> env.line().addTie(
                new Tie(env.line().getElement(0), env.line().getElement(TIED_NOTE_COUNT - 1))
            )
        );
        forceManualStemDirection(env, 0, 1, 2);
        ReflectionTestHelper.selectNote(env.coordinator(), 0);
        env.operations().autoStemDirection();

        var notification = captureSingleDidChange();
        var mutations = notification.getMutations();

        // Index 0 is in selection (directly updated); indices 1 and 2 are tie
        // partners outside the selection (also updated). Total: 3 modifications.
        assertThat(mutations)
            .as("selected note plus both tie partners outside selection are all updated")
            .hasSize(TIED_NOTE_COUNT);

        for (var index = 0; index < TIED_NOTE_COUNT; index++) {
            assertThat(env.line().getElement(index).isStemDirectionAuto())
                .as("element at index %d must have stemDirectionAuto restored to true", index)
                .isTrue();
        }
    }

    // -----------------------------------------------------------------------
    // canChangeTempo — delegation
    // -----------------------------------------------------------------------

    @Test
    void testCanChangeTempoReturnsTrueWhenSingleNoteSelected() {
        // canChangeTempo() delegates to coordinator.canChangeTempo(), which returns true
        // when exactly one element is selected.
        var env = setupEnv(crotchet(), crotchet());
        ReflectionTestHelper.selectNote(env.coordinator(), 0);
        assertThat(env.operations().canChangeTempo())
            .as("canChangeTempo() must return true when a single note is selected")
            .isTrue();
    }

    @Test
    void testCanChangeTempoReturnsFalseWhenMultipleNotesSelected() {
        // coordinator.canChangeTempo() returns false when state is null or no single
        // element is selected. Use a coordinator with no active line to verify this path.
        var env = setupEnv(crotchet(), crotchet());
        // selectRange produces a multi-element selection; getSingleSelectedElement() → null
        ReflectionTestHelper.selectRange(env.coordinator(), 0, 1);
        assertThat(env.operations().canChangeTempo())
            .as("canChangeTempo() must return false when multiple notes are selected")
            .isFalse();
    }

    // -----------------------------------------------------------------------
    // First-second ending
    // -----------------------------------------------------------------------

    @Test
    void testMakeFirstSecondEndingWithNoteAnchorEmitsOnlyRangeElementAddition() {
        // #306: Four notes [n0..n3], selection begins on a note (no leading barline).
        // Result: NONE action at index 0, ending over [0..3]. No barline is inserted —
        // the Ending anchors directly at the note.
        var env = setupEnv(crotchet(), crotchet(), crotchet(), crotchet());
        ReflectionTestHelper.selectNote(env.coordinator(), 0);
        var result = EndingValidationResult.valid(
            EndingValidationResult.PrecedingAction.NONE, 0, 3);
        env.operations().makeFirstSecondEnding(result);

        var notification = captureSingleDidChange();
        var mutations = notification.getMutations();
        assertThat(mutations).hasSize(1);
        assertThat(mutations.getFirst()).isInstanceOf(RangeElementAddition.class);

        var rangeAddition = (RangeElementAddition) mutations.getFirst();
        assertThat(rangeAddition.element()).isInstanceOf(Ending.class);

        var ending = (Ending) rangeAddition.element();
        assertThat(ending.getAnchorElementIndex())
            .as("Ending anchor is the note at the span start, no barline inserted")
            .isEqualTo(0);
        assertThat(ending.getEndElementIndex())
            .as("Ending end is the original selection end")
            .isEqualTo(3);
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
     * Verifies that {@code canMakeFirstSecondEnding} is valid for a {@code REPEAT_LEFT_RIGHT}
     * split regardless of the outer end's element type. #306 removed the end-type gate that
     * used to require the second ending to close with {@code REPEAT_RIGHT} or
     * {@code REPEAT_LEFT_RIGHT} — any terminal (or content) end is now accepted.
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
            assertThat(env.operations().canMakeFirstSecondEnding().isValid())
                .as("REPEAT_RIGHT outer end closes the repeat section and is valid")
                .isTrue();
        }

        @Test
        void testRepeatLeftRightTerminalIsValid() {
            // REPEAT_LEFT_RIGHT closes the second ending and opens a new repeat — valid
            var env = buildEnv(repeatLeftRight());
            ReflectionTestHelper.selectRange(env.coordinator(), 1, 6);
            assertThat(env.operations().canMakeFirstSecondEnding().isValid())
                .as("REPEAT_LEFT_RIGHT outer end closes the repeat section and is valid")
                .isTrue();
        }

        @Test
        void testSingleBarlineTerminalIsValid() {
            // #306: the end-type gate is removed entirely — the outer end no longer needs
            // to close the REPEAT_LEFT_RIGHT split's repeat, so a SINGLE_BARLINE terminal
            // is now valid too.
            var env = buildEnv(singleBarline());
            ReflectionTestHelper.selectRange(env.coordinator(), 1, 6);
            assertThat(env.operations().canMakeFirstSecondEnding().isValid())
                .as("SINGLE_BARLINE outer end is valid after the #306 end-type gate removal")
                .isTrue();
        }

        @Test
        void testRepeatLeftTerminalIsValid() {
            // #306: same relaxation — REPEAT_LEFT no longer needs to close a repeat to be
            // a valid outer end.
            var env = buildEnv(repeatLeft());
            ReflectionTestHelper.selectRange(env.coordinator(), 1, 6);
            assertThat(env.operations().canMakeFirstSecondEnding().isValid())
                .as("REPEAT_LEFT outer end is valid after the #306 end-type gate removal")
                .isTrue();
        }

        @Test
        void testContentNoteOuterEndIsValid() {
            // #306: the removed end-type gate lets the outer end be a plain content note, not
            // just a barline/repeat. A content element AFTER the end note keeps the
            // auto-maintained-terminal extension from widening the selection onto a barline, so
            // validateEndingStructure actually sees a note as the end.
            //  idx: 0           1        2        3                 4        5(end)   6        7
            //       REPEAT_LEFT CROTCHET CROTCHET REPEAT_LEFT_RIGHT CROTCHET CROTCHET CROTCHET FINAL_DOUBLE_BARLINE
            var env = setupEnv(
                repeatLeft(), crotchet(), crotchet(), repeatLeftRight(),
                crotchet(), crotchet(), crotchet(), finalDoubleBarline()
            );
            ReflectionTestHelper.selectRange(env.coordinator(), 1, 5);

            var result = env.operations().canMakeFirstSecondEnding();

            assertThat(result.isValid())
                .as("a content note is a valid outer end after the #306 end-type gate removal")
                .isTrue();
            assertThat(env.line().getElement(result.getSpanEnd()).getType().isDuration())
                .as("the ending's outer end stays the content note, not widened to a barline")
                .isTrue();
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
     * Verifies the backward-scan rules in {@code hasEnclosingRepeat}: double
     * barlines and right repeats act as section delimiters, the scan continues
     * onto preceding lines for as long as it needs to, and reaching the beginning
     * of the song is valid regardless of which line the selection is on.
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
            assertThat(ops.canMakeFirstSecondEnding().isValid())
                .as("reaching the beginning of the song on line 0 must satisfy the enclosing-repeat rule")
                .isTrue();
        }

        // The very opening of the song: nothing precedes the selection on its line and
        // there is no preceding line either, so the backward scan runs out immediately.
        @Test
        void testSelectionAtSongOpeningIsValid() {
            // Populate the Song's initial line so the fixture sits at line index 0 with no
            // line before it. addElement keeps the constructor's FINAL_DOUBLE_BARLINE last,
            // so it lands at index 6 and stays outside the selection.
            //
            // idx: 0        1        2             3        4        5              6
            //      CROTCHET CROTCHET REPEAT_RIGHT  CROTCHET CROTCHET SINGLE_BARLINE FINAL_DOUBLE_BARLINE
            // Selection 0–5: the scan starts before index 0, finds no preceding line,
            // and reaches the beginning of the song → valid, span anchored at index 0
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

            ReflectionTestHelper.selectRange(coordinator, 0, 5);
            var result = ops.canMakeFirstSecondEnding();
            assertThat(result.isValid())
                .as("a selection starting at the song's very first element must be valid")
                .isTrue();
            assertThat(result.getPrecedingAction())
                .as("nothing precedes the selection, so no span adjustment is needed")
                .isEqualTo(EndingValidationResult.PrecedingAction.NONE);
            assertThat(result.getSpanStart())
                .as("span start must be the song's first element")
                .isZero();
            assertThat(result.getSpanEnd())
                .as("span end must remain the selection end index")
                .isEqualTo(5);
        }

        // Non-first line: reaching beginning of song without a left repeat is valid
        @Test
        void testNonFirstLineNoRepeatIsValid() {
            // Line 0: CROTCHET CROTCHET CROTCHET
            // Line 1: CROTCHET CROTCHET REPEAT_RIGHT  CROTCHET CROTCHET SINGLE_BARLINE
            //         idx: 0        1        2             3        4        5
            // Selection 0–5 on line 1. The scan crosses onto line 0, meets no delimiter
            // and reaches the beginning of the song, which is an implicit left repeat → valid
            //
            // Populate the Song's initial line rather than adding one, so no line
            // precedes the fixture. Its FINAL_DOUBLE_BARLINE has to go: once the line
            // is no longer the last, that terminal would act as a section delimiter.
            var line0 = song.getLine(0);
            song.withoutMutationTracking(() -> {
                line0.removeElement(0);
                line0.addElement(crotchet());
                line0.addElement(crotchet());
                line0.addElement(crotchet());
            });

            var env = setupEnv(
                crotchet(), crotchet(), repeatRight(), crotchet(), crotchet(), singleBarline()
            );
            ReflectionTestHelper.selectRange(env.coordinator(), 0, 5);
            var result = env.operations().canMakeFirstSecondEnding();
            assertThat(result.isValid())
                .as("the song start is an implicit left repeat even on a non-first line")
                .isTrue();
            assertThat(result.getPrecedingAction())
                .as("a selection at the head of its line needs no span adjustment")
                .isEqualTo(EndingValidationResult.PrecedingAction.NONE);
            assertThat(result.getSpanStart())
                .as("span start must stay at the head of the selection's own line")
                .isZero();
            assertThat(result.getSpanEnd())
                .as("span end must remain the selection end index")
                .isEqualTo(5);
        }

        // The scan keeps walking back past an intervening line to find the left repeat
        @Test
        void testRepeatLeftTwoLinesBackIsValid() {
            // Song line 0: FINAL_DOUBLE_BARLINE  (the Song constructor's initial line,
            //              left in place — the scan never reaches it here)
            // Song line 1: REPEAT_LEFT  CROTCHET  CROTCHET
            // Song line 2: CROTCHET CROTCHET
            // Song line 3: CROTCHET CROTCHET REPEAT_RIGHT  CROTCHET CROTCHET SINGLE_BARLINE
            //              idx: 0        1        2             3        4        5
            // Selection 0–5 on line 3. Scan crosses line 2, then finds REPEAT_LEFT on line 1
            var previousLine = new Line(song);
            var interveningLine = new Line(song);
            song.withoutMutationTracking(() -> {
                previousLine.addElement(repeatLeft());
                previousLine.addElement(crotchet());
                previousLine.addElement(crotchet());
                song.addLine(previousLine);
                interveningLine.addElement(crotchet());
                interveningLine.addElement(crotchet());
                song.addLine(interveningLine);
            });

            var env = setupEnv(
                crotchet(), crotchet(), repeatRight(), crotchet(), crotchet(), singleBarline()
            );
            ReflectionTestHelper.selectRange(env.coordinator(), 0, 5);
            var result = env.operations().canMakeFirstSecondEnding();
            assertThat(result.isValid())
                .as("scan must keep walking back past an intervening line to find the left repeat")
                .isTrue();
            assertThat(result.getPrecedingAction())
                .as("a selection at the head of its line needs no span adjustment")
                .isEqualTo(EndingValidationResult.PrecedingAction.NONE);
            assertThat(result.getSpanStart())
                .as("span start must stay at the head of the selection's own line")
                .isZero();
            assertThat(result.getSpanEnd())
                .as("span end must remain the selection end index")
                .isEqualTo(5);
        }

        // A delimiter two lines back still stops the scan before the left repeat
        @Test
        void testDoubleBarlineTwoLinesBackBlocksScan() {
            // Song line 0: FINAL_DOUBLE_BARLINE  (the Song constructor's initial line,
            //              left in place — the scan stops before it)
            // Song line 1: REPEAT_LEFT  CROTCHET  DOUBLE_BARLINE
            // Song line 2: CROTCHET CROTCHET
            // Song line 3: CROTCHET CROTCHET REPEAT_RIGHT  CROTCHET CROTCHET SINGLE_BARLINE
            //              idx: 0        1        2             3        4        5
            // Selection 0–5 on line 3. The scan meets DOUBLE_BARLINE on line 1 → invalid
            var previousLine = new Line(song);
            var interveningLine = new Line(song);
            song.withoutMutationTracking(() -> {
                previousLine.addElement(repeatLeft());
                previousLine.addElement(crotchet());
                previousLine.addElement(doubleBarline());
                song.addLine(previousLine);
                interveningLine.addElement(crotchet());
                interveningLine.addElement(crotchet());
                song.addLine(interveningLine);
            });

            var env = setupEnv(
                crotchet(), crotchet(), repeatRight(), crotchet(), crotchet(), singleBarline()
            );
            ReflectionTestHelper.selectRange(env.coordinator(), 0, 5);
            assertThat(env.operations().canMakeFirstSecondEnding().isValid())
                .as("a double barline two lines back must stop the scan short of the left repeat")
                .isFalse();
        }

        // A selection at the head of a line anchors its span there, never onto the
        // barline that ends the previous line (an ending never spans two lines).
        @Test
        void testSpanStartsAtLineHeadWhenPreviousLineEndsWithBarline() {
            // Song line 0: FINAL_DOUBLE_BARLINE  (the Song constructor's initial line,
            //              left in place — the scan stops at the REPEAT_LEFT before it)
            // Song line 1: REPEAT_LEFT  CROTCHET  CROTCHET  SINGLE_BARLINE
            // Song line 2: CROTCHET CROTCHET REPEAT_RIGHT  CROTCHET CROTCHET SINGLE_BARLINE
            //              idx: 0        1        2             3        4        5
            // Selection 0–5 on line 2 → the span must start at index 0 of line 2, never
            // at line 1's trailing SINGLE_BARLINE (index 3 of a different line)
            var previousLine = new Line(song);
            song.withoutMutationTracking(() -> {
                previousLine.addElement(repeatLeft());
                previousLine.addElement(crotchet());
                previousLine.addElement(crotchet());
                previousLine.addElement(singleBarline());
                song.addLine(previousLine);
            });

            var env = setupEnv(
                crotchet(), crotchet(), repeatRight(), crotchet(), crotchet(), singleBarline()
            );
            ReflectionTestHelper.selectRange(env.coordinator(), 0, 5);
            var result = env.operations().canMakeFirstSecondEnding();
            assertThat(result.isValid())
                .as("a selection at the head of a line whose predecessor ends in a barline is valid")
                .isTrue();
            assertThat(result.getPrecedingAction())
                .as("the span must not be extended onto the previous line's barline")
                .isEqualTo(EndingValidationResult.PrecedingAction.NONE);
            assertThat(result.getSpanStart())
                .as("span start must be the head of the selection's own line")
                .isZero();
            assertThat(result.getSpanEnd())
                .as("span end must remain the selection end index")
                .isEqualTo(5);
        }

        // Non-first line: REPEAT_LEFT on previous line crosses the line boundary → valid
        @Test
        void testRepeatLeftOnPreviousLineIsValid() {
            // Song line 0: FINAL_DOUBLE_BARLINE  (the Song constructor's initial line,
            //              left in place — the scan stops at the REPEAT_LEFT before it)
            // Song line 1: REPEAT_LEFT  CROTCHET  CROTCHET
            // Song line 2: CROTCHET CROTCHET REPEAT_RIGHT  CROTCHET CROTCHET SINGLE_BARLINE
            //              idx: 0        1        2             3        4        5
            // Selection 0–5 on line 2. Scan crosses to line 1, finds REPEAT_LEFT → valid
            var previousLine = new Line(song);
            song.withoutMutationTracking(() -> {
                previousLine.addElement(repeatLeft());
                previousLine.addElement(crotchet());
                previousLine.addElement(crotchet());
                song.addLine(previousLine);
            });

            var env = setupEnv(
                crotchet(), crotchet(), repeatRight(), crotchet(), crotchet(), singleBarline()
            );
            ReflectionTestHelper.selectRange(env.coordinator(), 0, 5);
            assertThat(env.operations().canMakeFirstSecondEnding().isValid())
                .as("a REPEAT_LEFT on the previous line must satisfy the enclosing-repeat rule")
                .isTrue();
        }
    }

}
