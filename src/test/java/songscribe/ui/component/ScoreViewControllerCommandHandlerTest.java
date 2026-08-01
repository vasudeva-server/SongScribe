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

package songscribe.ui.component;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static songscribe.dom.StaffElementFactory.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.jspecify.annotations.Nullable;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import songscribe.UnitTest;
import songscribe.message.Message;
import songscribe.message.MessageCenter;
import songscribe.message.command.AddHairpinCommand;
import songscribe.message.command.AutoStemDirectionCommand;
import songscribe.message.command.FirstSecondEndingCommand;
import songscribe.message.command.FlipStemDirectionCommand;
import songscribe.message.command.ToggleBeamCommand;
import songscribe.message.command.ToggleBeamWithPreviousCommand;
import songscribe.message.command.ToggleTieCommand;
import songscribe.message.command.ToggleTupletCommand;
import songscribe.message.mutation.BeamingAddition;
import songscribe.message.mutation.CrescendoAddition;
import songscribe.message.mutation.DiminuendoAddition;
import songscribe.message.mutation.ElementField;
import songscribe.message.mutation.ElementModification;
import songscribe.message.mutation.RangeElementAddition;
import songscribe.message.mutation.TieAddition;
import songscribe.message.mutation.TupletAddition;
import songscribe.message.mutation.TupletRemoval;
import songscribe.message.notification.SongDidChangeNotification;
import songscribe.dom.Song;

import songscribe.dom.EndingValidationResult;
import songscribe.dom.Line;
import songscribe.ui.MusicEditOperations;
import songscribe.dom.StaffElement;
import songscribe.dom.Tuplet;
import songscribe.ui.action.Actions;
import songscribe.ui.action.FirstSecondEndingAction;
import songscribe.ui.action.TupletAction;
import songscribe.ui.clipboard.ClipboardManager;
import songscribe.ui.edit.EditModeManager;
import songscribe.ui.playback.PlaybackController;
import songscribe.dom.Ending;
import songscribe.ui.selection.ReflectionTestHelper;
import songscribe.ui.selection.SelectionCoordinator;
import songscribe.util.UIUtils;

/**
 * Verifies that each {@link ScoreViewController} command handler delegates
 * to {@link MusicEditOperations} such that exactly one
 * {@link SongDidChangeNotification} is emitted, and that the notification
 * carries mutations of the expected type. Field-level mutation assertions are
 * already covered by {@code MusicEditOperationsMutationTest} — this suite focuses
 * on the wiring between the coordinator and the operations layer.
 */
class ScoreViewControllerCommandHandlerTest extends UnitTest {

    /** Notes in the stem-direction fixtures, all of which are selected. */
    private static final int SELECTED_NOTE_COUNT = 3;

    private Song song;
    @Nullable private MockedStatic<MessageCenter> messageCenterMock;

    // Actions.MAKE_ENDING_ACTION is a public static field that stays null until
    // Actions.initialize() runs. Swapping in a mock keeps these tests from depending on
    // another test class having initialized Actions earlier in the same JVM; the original
    // is restored so the mock does not leak into tests that share the JVM.
    private @Nullable FirstSecondEndingAction originalMakeEndingAction;

    @BeforeEach
    void setUp() {
        song = new Song();
        originalMakeEndingAction = Actions.MAKE_ENDING_ACTION;
        Actions.MAKE_ENDING_ACTION = mock(FirstSecondEndingAction.class);
    }

    // MAKE_ENDING_ACTION lacks a @Nullable annotation but is null until Actions.initialize()
    // runs (and after resetForTest()), so restoring the saved original — null in a unit-test
    // JVM — requires suppressing NullAway.
    @SuppressWarnings("NullAway")
    @AfterEach
    void tearDown() {
        Actions.MAKE_ENDING_ACTION = originalMakeEndingAction;

        if (messageCenterMock != null) {
            messageCenterMock.close();
            messageCenterMock = null;
        }
    }

    // -----------------------------------------------------------------------
    // Setup helper
    // -----------------------------------------------------------------------

    private record Env(
        SelectionCoordinator coordinator,
        ScoreViewController scoreMessageCoordinator,
        Line line
    ) {}

    /**
     * Builds a Line with the given elements, attaches it to the song (which fires a
     * real LineInsertion notification on the unobserved bus), wires up real
     * {@link SelectionCoordinator} and {@link MusicEditOperations} instances, then
     * constructs a real {@link ScoreViewController} backed by mocked ScoreView /
     * EditModeManager / ClipboardManager dependencies. Finally, mocks
     * {@link MessageCenter} so subsequent posts can be captured.
     *
     * <p>The coordinator's constructor calls {@code MessageCenter.subscribe(this)} —
     * by the time the test invokes a handler, the bus is mocked, so the
     * {@code subscribe} call from construction is intercepted as a no-op. This is
     * desirable: the test invokes handlers directly, bypassing the bus.
     */
    private Env setupTest(StaffElement... elements) {
        var line = new Line(song);

        song.withoutMutationTracking(() -> {
            for (var element : elements) {
                line.addElement(element);
            }
        });

        // addLine fires a real LineInsertion notification on the pre-mock bus.
        song.addLine(line);
        var coordinator = ReflectionTestHelper.createCoordinatorForLine(line);
        var operations = new MusicEditOperations(song, coordinator);

        var score = mock(ScoreView.class);
        var clipboardManager = mock(ClipboardManager.class);

        messageCenterMock = mockStatic(MessageCenter.class);

        var scoreMessageCoordinator = new ScoreViewController(
            score,
            operations,
            coordinator,
            clipboardManager
        );

        return new Env(coordinator, scoreMessageCoordinator, line);
    }

    // -----------------------------------------------------------------------
    // Notification capture helper
    // -----------------------------------------------------------------------

    private SongDidChangeNotification captureSingleDidChange() {
        var mock = messageCenterMock;

        if (mock == null) {
            throw new IllegalStateException("messageCenterMock not set — call setupTest() first");
        }

        var captor = ArgumentCaptor.forClass(Message.class);
        mock.verify(() -> MessageCenter.post(captor.capture()), atLeastOnce());
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
    // Beam
    // -----------------------------------------------------------------------

    @Test
    void testHandleToggleBeamEmitsOneBeamingAddition() {
        var env = setupTest(quaver(), quaver(), quaver());
        ReflectionTestHelper.selectRange(env.coordinator(), 0, 2);

        env.scoreMessageCoordinator().handleToggleBeam(new ToggleBeamCommand());

        var notification = captureSingleDidChange();
        assertThat(notification.getMutations()).hasSize(1);
        assertThat(notification.getMutations().getFirst()).isInstanceOf(BeamingAddition.class);
    }

    // -----------------------------------------------------------------------
    // Tie
    // -----------------------------------------------------------------------

    @Test
    void testHandleToggleTieEmitsOneTieAddition() {
        var env = setupTest(crotchet(), crotchet());
        ReflectionTestHelper.selectRange(env.coordinator(), 0, 1);

        env.scoreMessageCoordinator().handleToggleTie(new ToggleTieCommand());

        var notification = captureSingleDidChange();
        assertThat(notification.getMutations()).hasSize(1);
        assertThat(notification.getMutations().getFirst()).isInstanceOf(TieAddition.class);
    }

    // -----------------------------------------------------------------------
    // Tuplet
    // -----------------------------------------------------------------------

    @Test
    void testHandleToggleTupletEmitsOneTupletAddition() {
        var env = setupTest(crotchet(), crotchet(), crotchet());
        ReflectionTestHelper.selectRange(env.coordinator(), 0, 2);

        env.scoreMessageCoordinator().handleToggleTuplet(tupletCommand(TupletAction.Tuplet.TRIPLET));

        var notification = captureSingleDidChange();
        assertThat(notification.getMutations()).hasSize(1);
        var addition = (TupletAddition) notification.getMutations().getFirst();
        assertThat(addition.tuplet().getGrade()).isEqualTo(TupletAction.Tuplet.TRIPLET.getSize());
    }

    @Test
    void testHandleToggleTupletEmitsRemovalAndAdditionForGradeChange() {
        // Existing triplet over [0..4] with the full span selected, handler invoked with
        // quintuplet — emits [TupletRemoval, TupletAddition] inside one modification bracket
        // so the grade change replays atomically under undo.
        // Five crotchets, so the incoming quintuplet's written value divides out exactly.
        var env = setupTest(crotchet(), crotchet(), crotchet(), crotchet(), crotchet());
        song.withoutMutationTracking(() -> env.line().addTuplet(Tuplet.withUnresolvedRatio(
            env.line().getElement(0), env.line().getElement(4), TupletAction.Tuplet.TRIPLET.getSize())));
        ReflectionTestHelper.selectRange(env.coordinator(), 0, 4);

        env.scoreMessageCoordinator().handleToggleTuplet(tupletCommand(TupletAction.Tuplet.QUINTUPLET));

        var notification = captureSingleDidChange();
        var mutations = notification.getMutations();
        assertThat(mutations).hasSize(2);
        assertThat(mutations.get(0)).isInstanceOf(TupletRemoval.class);
        assertThat(mutations.get(1)).isInstanceOf(TupletAddition.class);
        assertThat(((TupletAddition) mutations.get(1)).tuplet().getGrade()).isEqualTo(TupletAction.Tuplet.QUINTUPLET.getSize());
    }

    /**
     * Builds a {@link ToggleTupletCommand} backed by a mocked {@link TupletAction},
     * avoiding the singleton {@link MainFrame} touched by the
     * real {@code UIAction} constructor.
     */
    private static ToggleTupletCommand tupletCommand(TupletAction.Tuplet tuplet) {
        var action = mock(TupletAction.class);
        when(action.getTuplet()).thenReturn(tuplet);
        return new ToggleTupletCommand(action);
    }

    // -----------------------------------------------------------------------
    // Dynamics
    // -----------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testHandleAddDynamicsEmitsOneAddition(boolean crescendo) {
        var env = setupTest(crotchet(), crotchet());
        ReflectionTestHelper.selectRange(env.coordinator(), 0, 1);

        env.scoreMessageCoordinator().handleAddHairpin(new AddHairpinCommand(crescendo));

        var notification = captureSingleDidChange();
        assertThat(notification.getMutations()).hasSize(1);

        if (crescendo) {
            assertThat(notification.getMutations().getFirst()).isInstanceOf(CrescendoAddition.class);
        } else {
            assertThat(notification.getMutations().getFirst()).isInstanceOf(DiminuendoAddition.class);
        }
    }

    // -----------------------------------------------------------------------
    // Stem direction
    // -----------------------------------------------------------------------

    @Test
    void testHandleFlipStemDirectionEmitsOneNotificationWithModificationsPerNote() {
        var env = setupTest(crotchet(), crotchet(), crotchet());
        ReflectionTestHelper.selectRange(env.coordinator(), 0, 2);

        env.scoreMessageCoordinator().handleFlipStemDirection(new FlipStemDirectionCommand());

        var notification = captureSingleDidChange();
        assertThat(notification.getMutations()).hasSize(3);

        for (var mutation : notification.getMutations()) {
            assertThat(mutation).isInstanceOf(ElementModification.class);
            assertThat(((ElementModification) mutation).fields()).contains(ElementField.UPPER);
        }
    }

    @Test
    void testHandleAutoStemDirectionRestoresAutoOnEveryNote() {
        var env = setupTest(crotchet(), crotchet(), crotchet());

        // Elements default to auto, so pin them first. Otherwise the post-condition below
        // would already hold before the handler ran, and a handler that flipped instead of
        // restoring auto would still pass — FLIP records the same mutation field set.
        song.withoutMutationTracking(() -> {
            for (var index = 0; index < SELECTED_NOTE_COUNT; index++) {
                env.line().getElement(index).setStemDirectionAuto(false);
            }
        });

        ReflectionTestHelper.selectRange(env.coordinator(), 0, SELECTED_NOTE_COUNT - 1);

        env.scoreMessageCoordinator().handleAutoStemDirection(new AutoStemDirectionCommand());

        var notification = captureSingleDidChange();
        assertThat(notification.getMutations()).hasSize(SELECTED_NOTE_COUNT);

        for (var mutation : notification.getMutations()) {
            assertThat(mutation).isInstanceOf(ElementModification.class);
            assertThat(((ElementModification) mutation).fields()).contains(ElementField.STEM_DIRECTION_AUTO);
        }

        for (var index = 0; index < SELECTED_NOTE_COUNT; index++) {
            assertThat(env.line().getElement(index).isStemDirectionAuto())
                .as("note at index %d must have stemDirectionAuto restored to true", index)
                .isTrue();
        }
    }

    // -----------------------------------------------------------------------
    // First-second ending
    // -----------------------------------------------------------------------

    @Test
    void testHandleFirstSecondEndingEmitsOneNotificationWithEnding() {
        // The handler reads the cached EndingValidationResult from the singleton
        // FirstSecondEndingAction, which setUp() has replaced with a mock.
        // #306: PrecedingAction.NONE no longer inserts a barline — the ending anchors
        // directly to the note at the span start.
        var env = setupTest(crotchet(), crotchet(), crotchet(), crotchet());
        ReflectionTestHelper.selectNote(env.coordinator(), 0);

        var validResult = EndingValidationResult.valid(
            EndingValidationResult.PrecedingAction.NONE, 0, 3);
        setCachedEndingResult(validResult);

        env.scoreMessageCoordinator().handleFirstSecondEnding(new FirstSecondEndingCommand());

        var notification = captureSingleDidChange();
        assertThat(notification.getMutations()).hasSize(1);
        assertThat(notification.getMutations().getFirst()).isInstanceOf(RangeElementAddition.class);
        assertThat(((RangeElementAddition) notification.getMutations().getFirst()).element())
            .isInstanceOf(Ending.class);
    }

    // -----------------------------------------------------------------------
    // handleToggleBeam no-op — row 48
    // -----------------------------------------------------------------------

    @Test
    void testHandleToggleBeamIsNoOpWhenNoActiveSelection() {
        // Row 48: when no range is selected, the handler must return immediately without
        // posting any SongDidChangeNotification.
        //
        // Uses a mocked SelectionCoordinator that returns null for getRange() rather than the
        // real coordinator (which always has an active line).
        var coordinatorMock = mock(SelectionCoordinator.class);
        when(coordinatorMock.getRange()).thenReturn(null);

        messageCenterMock = mockStatic(MessageCenter.class);

        var controller = new ScoreViewController(
            mock(ScoreView.class),
            mock(MusicEditOperations.class),
            coordinatorMock,
            mock(ClipboardManager.class)
        );

        controller.handleToggleBeam(new ToggleBeamCommand());

        var mock = messageCenterMock;

        if (mock == null) {
            throw new IllegalStateException("messageCenterMock not set");
        }

        mock.verify(() -> MessageCenter.post(any(SongDidChangeNotification.class)), never());
    }

    // -----------------------------------------------------------------------
    // handleFirstSecondEnding no-op — row 57
    // -----------------------------------------------------------------------

    @Test
    void testHandleFirstSecondEndingIsNoOpWhenCachedResultIsNull() {
        // Row 57a: null cached result — handler must not emit any notification.
        var env = setupTest(crotchet(), crotchet());
        ReflectionTestHelper.selectNote(env.coordinator(), 0);

        setCachedEndingResult(null);

        env.scoreMessageCoordinator().handleFirstSecondEnding(new FirstSecondEndingCommand());

        var mock = messageCenterMock;

        if (mock == null) {
            throw new IllegalStateException("messageCenterMock not set");
        }

        mock.verify(() -> MessageCenter.post(any(SongDidChangeNotification.class)), never());
    }

    @Test
    void testHandleFirstSecondEndingIsNoOpWhenCachedResultIsInvalid() {
        // Row 57b: invalid cached result — handler must not emit any notification.
        var env = setupTest(crotchet(), crotchet());
        ReflectionTestHelper.selectNote(env.coordinator(), 0);

        setCachedEndingResult(EndingValidationResult.invalid());

        env.scoreMessageCoordinator().handleFirstSecondEnding(new FirstSecondEndingCommand());

        var mock = messageCenterMock;

        if (mock == null) {
            throw new IllegalStateException("messageCenterMock not set");
        }

        mock.verify(() -> MessageCenter.post(any(SongDidChangeNotification.class)), never());
    }

    private static void setCachedEndingResult(@Nullable EndingValidationResult value) {
        when(Actions.MAKE_ENDING_ACTION.getCachedResult()).thenReturn(value);
    }

    // -----------------------------------------------------------------------
    // handleToggleBeamWithPrevious
    // -----------------------------------------------------------------------

    @Test
    void testHandleToggleBeamWithPreviousBeepsAndDoesNothingWhilePlaying() {
        var controller = new ScoreViewController(
            mock(ScoreView.class),
            mock(MusicEditOperations.class),
            mock(SelectionCoordinator.class),
            mock(ClipboardManager.class)
        );

        try (
            var playback = mockStatic(PlaybackController.class);
            var editModeManager = mockStatic(EditModeManager.class);
            var uiUtils = mockStatic(UIUtils.class)
        ) {
            playback.when(PlaybackController::isPlaying).thenReturn(true);

            controller.handleToggleBeamWithPrevious(new ToggleBeamWithPreviousCommand());

            uiUtils.verify(UIUtils::beep);
            editModeManager.verify(EditModeManager::getLastInsertion, never());
        }
    }

    @Test
    void testHandleToggleBeamWithPreviousBeepsWhenNoLastInsertion() {
        var controller = new ScoreViewController(
            mock(ScoreView.class),
            mock(MusicEditOperations.class),
            mock(SelectionCoordinator.class),
            mock(ClipboardManager.class)
        );

        try (
            var playback = mockStatic(PlaybackController.class);
            var editModeManager = mockStatic(EditModeManager.class);
            var uiUtils = mockStatic(UIUtils.class)
        ) {
            playback.when(PlaybackController::isPlaying).thenReturn(false);
            editModeManager.when(EditModeManager::getLastInsertion).thenReturn(null);

            controller.handleToggleBeamWithPrevious(new ToggleBeamWithPreviousCommand());

            uiUtils.verify(UIUtils::beep);
        }
    }

    /**
     * The handler hands the remembered target to the toggle and must not arm anything
     * itself. Arming belongs to the toggle's two modifying branches, because the handler
     * cannot know in advance whether the toggle will change the line: every refusing path
     * opens no modification bracket, so an arm made here would sit unclaimed until some
     * later unrelated edit adopted it as its own placement.
     */
    @Test
    void testHandleToggleBeamWithPreviousTogglesTheTargetWithoutArmingItself() {
        var line = new Line(new Song());
        var elementIndex = 2;
        var insertion = new EditModeManager.Insertion(line, elementIndex);
        var controller = new ScoreViewController(
            mock(ScoreView.class),
            mock(MusicEditOperations.class),
            mock(SelectionCoordinator.class),
            mock(ClipboardManager.class)
        );

        try (
            var playback = mockStatic(PlaybackController.class);
            var editModeManager = mockStatic(EditModeManager.class);
            var operations = mockStatic(MusicEditOperations.class);
            var uiUtils = mockStatic(UIUtils.class)
        ) {
            playback.when(PlaybackController::isPlaying).thenReturn(false);
            editModeManager.when(EditModeManager::getLastInsertion).thenReturn(insertion);
            operations.when(() -> MusicEditOperations.toggleBeamWithPredecessor(line, elementIndex))
                .thenReturn(true);

            controller.handleToggleBeamWithPrevious(new ToggleBeamWithPreviousCommand());

            operations.verify(() -> MusicEditOperations.toggleBeamWithPredecessor(line, elementIndex));
            editModeManager.verify(() -> EditModeManager.armInsertion(any(), anyInt()), never());
            uiUtils.verify(UIUtils::beep, never());
        }
    }

    /**
     * A toggle that refuses — the note before the target is a rest, a tie already joins the
     * pair, and so on — has to be audible. Without this the key would fail silently and the
     * user would have no way to tell the press registered at all.
     */
    @Test
    void testHandleToggleBeamWithPreviousBeepsWhenTheToggleRefuses() {
        var line = new Line(new Song());
        var elementIndex = 2;
        var insertion = new EditModeManager.Insertion(line, elementIndex);
        var controller = new ScoreViewController(
            mock(ScoreView.class),
            mock(MusicEditOperations.class),
            mock(SelectionCoordinator.class),
            mock(ClipboardManager.class)
        );

        try (
            var playback = mockStatic(PlaybackController.class);
            var editModeManager = mockStatic(EditModeManager.class);
            var operations = mockStatic(MusicEditOperations.class);
            var uiUtils = mockStatic(UIUtils.class)
        ) {
            playback.when(PlaybackController::isPlaying).thenReturn(false);
            editModeManager.when(EditModeManager::getLastInsertion).thenReturn(insertion);
            operations.when(() -> MusicEditOperations.toggleBeamWithPredecessor(line, elementIndex))
                .thenReturn(false);

            controller.handleToggleBeamWithPrevious(new ToggleBeamWithPreviousCommand());

            uiUtils.verify(UIUtils::beep);
        }
    }

}
