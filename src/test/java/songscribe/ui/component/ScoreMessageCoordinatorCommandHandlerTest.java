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
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static songscribe.music.StaffElementFactory.*;

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
import songscribe.message.command.AddDynamicsCommand;
import songscribe.message.command.FirstSecondEndingCommand;
import songscribe.message.command.FlipStemDirectionCommand;
import songscribe.message.command.RemoveDynamicsCommand;
import songscribe.message.command.ToggleBeamCommand;
import songscribe.message.command.ToggleTieCommand;
import songscribe.message.command.ToggleTrillCommand;
import songscribe.message.command.ToggleTupletCommand;
import songscribe.message.mutation.BeamingAddition;
import songscribe.message.mutation.CrescendoAddition;
import songscribe.message.mutation.DiminuendoAddition;
import songscribe.message.mutation.ElementField;
import songscribe.message.mutation.ElementInsertion;
import songscribe.message.mutation.ElementModification;
import songscribe.message.mutation.RangeElementAddition;
import songscribe.message.mutation.TieAddition;
import songscribe.message.mutation.TupletAddition;
import songscribe.message.mutation.TupletRemoval;
import songscribe.message.notification.SongDidChangeNotification;
import songscribe.music.Song;
import songscribe.music.DynamicsSpan;

import songscribe.music.EndingValidationResult;
import songscribe.music.Line;
import songscribe.ui.MusicEditOperations;
import songscribe.music.StaffElement;
import songscribe.music.TupletSpan;
import songscribe.ui.action.FirstSecondEndingAction;
import songscribe.ui.action.TupletAction;
import songscribe.ui.clipboard.ClipboardManager;
import songscribe.ui.edit.EditModeManager;
import songscribe.ui.layout.Ending;
import songscribe.ui.selection.ReflectionTestHelper;
import songscribe.ui.selection.SelectionCoordinator;

/**
 * Verifies that each {@link ScoreMessageCoordinator} command handler delegates
 * to {@link MusicEditOperations} such that exactly one
 * {@link SongDidChangeNotification} is emitted, and that the notification
 * carries mutations of the expected type. Field-level mutation assertions are
 * already covered by {@code MusicEditOperationsMutationTest} — this suite focuses
 * on the wiring between the coordinator and the operations layer.
 */
@SuppressWarnings("OverlyBroadThrowsClause")
class ScoreMessageCoordinatorCommandHandlerTest extends UnitTest {

    private Song song;
    @Nullable private MockedStatic<MessageCenter> messageCenterMock;

    @BeforeEach
    void setUp() {
        song = new Song();
    }

    @AfterEach
    void tearDown() {
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
        ScoreMessageCoordinator scoreMessageCoordinator,
        Line line
    ) {}

    /**
     * Builds a Line with the given elements, attaches it to the song (which fires a
     * real LineInsertion notification on the unobserved bus), wires up real
     * {@link SelectionCoordinator} and {@link MusicEditOperations} instances, then
     * constructs a real {@link ScoreMessageCoordinator} backed by mocked Score /
     * EditModeManager / ClipboardManager dependencies. Finally, mocks
     * {@link MessageCenter} so subsequent posts can be captured.
     *
     * <p>The coordinator's constructor calls {@code MessageCenter.subscribe(this)} —
     * by the time the test invokes a handler, the bus is mocked, so the
     * {@code subscribe} call from construction is intercepted as a no-op. This is
     * desirable: the test invokes handlers directly, bypassing the bus.
     */
    private Env setup(StaffElement... elements) {
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

        var score = mock(Score.class);
        var editModeManager = mock(EditModeManager.class);
        var clipboardManager = mock(ClipboardManager.class);

        messageCenterMock = mockStatic(MessageCenter.class);

        var scoreMessageCoordinator = new ScoreMessageCoordinator(
            score,
            operations,
            editModeManager,
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
            throw new IllegalStateException("messageCenterMock not set — call setup() first");
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
        var env = setup(quaver(), quaver(), quaver());
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
        var env = setup(crotchet(), crotchet());
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
        var env = setup(crotchet(), crotchet(), crotchet());
        ReflectionTestHelper.selectRange(env.coordinator(), 0, 2);

        env.scoreMessageCoordinator().handleToggleTuplet(tupletCommand(TupletAction.Tuplet.TRIPLET));

        var notification = captureSingleDidChange();
        assertThat(notification.getMutations()).hasSize(1);
        var addition = (TupletAddition) notification.getMutations().getFirst();
        assertThat(addition.span().getGrade()).isEqualTo(TupletAction.Tuplet.TRIPLET.getSize());
    }

    @Test
    void testHandleToggleTupletEmitsRemovalAndAdditionForGradeChange() {
        // Existing triplet over [0..2] with the full span selected, handler invoked with
        // quintuplet — emits [TupletRemoval, TupletAddition] inside one modification bracket
        // so the grade change replays atomically under undo.
        var env = setup(crotchet(), crotchet(), crotchet());
        env.line().getTuplets().addSpan(new TupletSpan(0, 2, TupletAction.Tuplet.TRIPLET.getSize()));
        ReflectionTestHelper.selectRange(env.coordinator(), 0, 2);

        env.scoreMessageCoordinator().handleToggleTuplet(tupletCommand(TupletAction.Tuplet.QUINTUPLET));

        var notification = captureSingleDidChange();
        var mutations = notification.getMutations();
        assertThat(mutations).hasSize(2);
        assertThat(mutations.get(0)).isInstanceOf(TupletRemoval.class);
        assertThat(mutations.get(1)).isInstanceOf(TupletAddition.class);
        assertThat(((TupletAddition) mutations.get(1)).span().getGrade()).isEqualTo(TupletAction.Tuplet.QUINTUPLET.getSize());
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
        var env = setup(crotchet(), crotchet());
        ReflectionTestHelper.selectRange(env.coordinator(), 0, 1);

        env.scoreMessageCoordinator().handleAddDynamics(new AddDynamicsCommand(crescendo));

        var notification = captureSingleDidChange();
        assertThat(notification.getMutations()).hasSize(1);

        if (crescendo) {
            assertThat(notification.getMutations().getFirst()).isInstanceOf(CrescendoAddition.class);
        } else {
            assertThat(notification.getMutations().getFirst()).isInstanceOf(DiminuendoAddition.class);
        }
    }

    @Test
    void testHandleRemoveDynamicsEmitsRemovals() {
        // One crescendo, one diminuendo — the handler must coalesce both removals
        // into a single notification.
        var env = setup(crotchet(), crotchet(), crotchet(), crotchet());
        env.line().getCrescendos().addSpan(new DynamicsSpan(0, 1));
        env.line().getDiminuendos().addSpan(new DynamicsSpan(2, 3));
        ReflectionTestHelper.selectRange(env.coordinator(), 0, 3);

        env.scoreMessageCoordinator().handleRemoveDynamics(new RemoveDynamicsCommand());

        var notification = captureSingleDidChange();
        assertThat(notification.getMutations()).isNotEmpty();
    }

    // -----------------------------------------------------------------------
    // Trill
    // -----------------------------------------------------------------------

    @Test
    void testHandleToggleTrillEmitsOneNotificationWithModificationsPerNote() {
        var env = setup(crotchet(), crotchet(), crotchet());
        ReflectionTestHelper.selectRange(env.coordinator(), 0, 2);

        env.scoreMessageCoordinator().handleToggleTrill(new ToggleTrillCommand());

        var notification = captureSingleDidChange();
        assertThat(notification.getMutations()).hasSize(3);

        for (var mutation : notification.getMutations()) {
            assertThat(mutation).isInstanceOf(ElementModification.class);
            assertThat(((ElementModification) mutation).fields()).contains(ElementField.TRILL);
        }
    }

    // -----------------------------------------------------------------------
    // Stem direction
    // -----------------------------------------------------------------------

    @Test
    void testHandleFlipStemDirectionEmitsOneNotificationWithModificationsPerNote() {
        var env = setup(crotchet(), crotchet(), crotchet());
        ReflectionTestHelper.selectRange(env.coordinator(), 0, 2);

        env.scoreMessageCoordinator().handleFlipStemDirection(new FlipStemDirectionCommand());

        var notification = captureSingleDidChange();
        assertThat(notification.getMutations()).hasSize(3);

        for (var mutation : notification.getMutations()) {
            assertThat(mutation).isInstanceOf(ElementModification.class);
            assertThat(((ElementModification) mutation).fields()).contains(ElementField.UPPER);
        }
    }

    // -----------------------------------------------------------------------
    // First-second ending
    // -----------------------------------------------------------------------

    @Test
    void testHandleFirstSecondEndingEmitsOneNotificationWithBarlineAndEnding() throws Exception {
        // The handler reads the cached EndingValidationResult from the singleton
        // FirstSecondEndingAction. Inject a valid result via reflection, then restore
        // the prior value to avoid leaking state into other tests.
        var env = setup(crotchet(), crotchet(), crotchet(), crotchet());
        ReflectionTestHelper.selectNote(env.coordinator(), 0);

        var validResult = EndingValidationResult.valid(
            EndingValidationResult.PrecedingAction.INSERT_BARLINE, 0, 3);
        var previous = setCachedEndingResult(validResult);

        try {
            env.scoreMessageCoordinator().handleFirstSecondEnding(new FirstSecondEndingCommand());

            var notification = captureSingleDidChange();
            assertThat(notification.getMutations()).hasSize(2);
            assertThat(notification.getMutations().get(0)).isInstanceOf(ElementInsertion.class);
            assertThat(notification.getMutations().get(1)).isInstanceOf(RangeElementAddition.class);
            assertThat(((RangeElementAddition) notification.getMutations().get(1)).element())
                .isInstanceOf(Ending.class);
        } finally {
            setCachedEndingResult(previous);
        }
    }

    @Nullable
    private static EndingValidationResult setCachedEndingResult(@Nullable EndingValidationResult value)
        throws ReflectiveOperationException
    {
        var field = FirstSecondEndingAction.class.getDeclaredField("cachedResult");
        field.setAccessible(true);
        var previous = (EndingValidationResult) field.get(FirstSecondEndingAction.MAKE_ENDING_ACTION);
        field.set(FirstSecondEndingAction.MAKE_ENDING_ACTION, value);
        return previous;
    }

}
