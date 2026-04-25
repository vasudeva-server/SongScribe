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
package songscribe.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.ArrayList;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import songscribe.UnitTest;
import songscribe.message.MessageCenter;
import songscribe.message.command.PasteboardOpCommand;
import songscribe.music.Song;
import songscribe.music.ElementType;
import songscribe.music.Line;
import songscribe.ui.layout.EndingLineFixture;
import songscribe.ui.action.ElementTypeAction;
import songscribe.ui.action.PasteboardAction;
import songscribe.ui.action.UIAction;
import songscribe.ui.clipboard.ClipboardManager;
import songscribe.ui.component.MainFrame;
import songscribe.ui.component.Score;
import songscribe.ui.component.ScoreMessageCoordinator;
import songscribe.ui.edit.EditModeManager;
import songscribe.ui.layout.Ending;
import songscribe.ui.selection.ReflectionTestHelper;
import songscribe.ui.selection.SelectionCoordinator;

/**
 * Integration tests for the {@link EndingConfirms} wiring in
 * {@link SelectionCoordinator} and {@link ScoreMessageCoordinator}.
 *
 * <p>Primary canonical line layout (split = {@code REPEAT_RIGHT}):
 * <pre>
 *  idx:  0             1        2        3             4        5        6
 *        SINGLE_BAR    CROTCHET CROTCHET REPEAT_RIGHT  CROTCHET CROTCHET SINGLE_BAR
 *        (anchor)                        (split)                          (end)
 * </pre>
 *
 * <p>Secondary canonical line layout (split = {@code REPEAT_LEFT_RIGHT}):
 * <pre>
 *  idx:  0            1        2        3                  4        5        6
 *        REPEAT_LEFT  CROTCHET CROTCHET REPEAT_LEFT_RIGHT  CROTCHET CROTCHET REPEAT_RIGHT
 *        (anchor)                       (split)                               (end)
 * </pre>
 */
class EndingConfirmsTest extends UnitTest {

    private static final int ANCHOR_INDEX = 0;
    private static final int SPLIT_INDEX = 3;
    private static final int END_INDEX = 6;

    // Actions used to trigger element replacements via SelectionCoordinator
    private static final ElementTypeAction DOUBLE_BARLINE_ACTION = ElementTypeAction.createDoubleBarlineAction();
    private static final ElementTypeAction LEFT_REPEAT_ACTION = ElementTypeAction.createLeftRepeatAction();
    private static final ElementTypeAction RIGHT_REPEAT_ACTION = ElementTypeAction.createRightRepeatAction();
    private static final ElementTypeAction LEFT_RIGHT_REPEAT_ACTION = ElementTypeAction.createLeftRightRepeatAction();
    private static final ElementTypeAction SINGLE_BARLINE_ACTION = ElementTypeAction.createSingleBarlineAction();

    @Nullable private MockedStatic<MainFrame> mainFrameMock;

    @BeforeEach
    void setUp() {
        mainFrameMock = mockStatic(MainFrame.class);
        mainFrameMock.when(MainFrame::getInstance).thenReturn(mock(MainFrame.class));
    }

    @AfterEach
    void tearDown() {
        var mock = mainFrameMock;

        if (mock != null) {
            mock.close();
            mainFrameMock = null;
        }
    }

    // -----------------------------------------------------------------------
    // Canonical line setup
    // -----------------------------------------------------------------------

    private record LineEnv(EndingLineFixture fixture, SelectionCoordinator coordinator) {
        Song song() { return fixture.song(); }
        Line line() { return fixture.line(); }
        Ending ending() { return fixture.ending(); }
    }

    /** Builds the primary canonical line (split = {@code REPEAT_RIGHT}). */
    private LineEnv setupPrimaryLine() {
        var fixture = EndingLineFixture.primary();
        return new LineEnv(fixture, coordinatorForLine(fixture.line()));
    }

    /** Builds the secondary canonical line (split = {@code REPEAT_LEFT_RIGHT}). */
    private LineEnv setupSecondaryLine() {
        var fixture = EndingLineFixture.secondary();
        return new LineEnv(fixture, coordinatorForLine(fixture.line()));
    }

    /**
     * Creates a {@link SelectionCoordinator} for an existing line with empty managed actions
     * injected. Without this injection, {@code saveActionStates()} triggers {@code collectActions()}
     * which attempts to initialize the {@code Actions} class — a full-UI operation.
     */
    private SelectionCoordinator coordinatorForLine(Line line) {
        var coordinator = ReflectionTestHelper.createCoordinatorForLine(line);

        try {
            var field = SelectionCoordinator.class.getDeclaredField("managedActions");
            field.setAccessible(true);
            field.set(coordinator, new ArrayList<UIAction>());
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Failed to inject managed actions", e);
        }

        return coordinator;
    }

    /**
     * Creates a {@link ScoreMessageCoordinator} backed by a mock Score.
     * Must be called AFTER {@link MessageCenter} is mocked so the coordinator's
     * {@code subscribe} call goes to the mock bus.
     */
    private ScoreMessageCoordinator scoreCoordinator(Song song, SelectionCoordinator coordinator) {
        var score = mock(Score.class);
        when(score.getSong()).thenReturn(song);
        when(score.isFocusOwner()).thenReturn(true);
        return new ScoreMessageCoordinator(
            score,
            new MusicEditOperations(song, coordinator),
            mock(EditModeManager.class),
            coordinator,
            mock(ClipboardManager.class)
        );
    }

    /**
     * Opens a {@link MockedStatic} for {@link OptionDialogs} that makes
     * {@code showOptionDialog} return 0 (the "Yes" button index).
     * The caller must close the returned mock (try-with-resources).
     */
    private MockedStatic<OptionDialogs> simulateYes() {
        var mock = mockStatic(OptionDialogs.class);
        mock.when(() -> OptionDialogs.showOptionDialog(
            any(), any(), any(), anyInt(), anyInt(), any(), any(), any()
        )).thenReturn(0);
        return mock;
    }

    // -----------------------------------------------------------------------
    // Confirm-I, user declines — mutation is aborted
    // -----------------------------------------------------------------------

    @Nested
    class ConfirmInvalidationUserDeclines {

        @Test
        void testDeleteAnchorAbortsWhenUserDeclines() {
            var env = setupPrimaryLine();

            try (var mc = mockStatic(MessageCenter.class)) {
                var scoreCoord = scoreCoordinator(env.song(), env.coordinator());
                ReflectionTestHelper.selectNote(env.coordinator(), ANCHOR_INDEX);
                scoreCoord.handlePasteboardOp(new PasteboardOpCommand(PasteboardAction.Operation.DELETE));
            }

            assertThat(env.line().elementCount()).isEqualTo(8);
            assertThat(env.line().getRangeElements()).contains(env.ending());
        }

        @Test
        void testReplaceAnchorWithDoubleBarlineAbortsWhenUserDeclines() {
            var env = setupPrimaryLine();
            ReflectionTestHelper.selectNote(env.coordinator(), ANCHOR_INDEX);
            env.coordinator().applyActionToSelection(DOUBLE_BARLINE_ACTION, true);

            assertThat(env.line().getElement(ANCHOR_INDEX).getType()).isEqualTo(ElementType.SINGLE_BARLINE);
            assertThat(env.line().getRangeElements()).contains(env.ending());
        }
    }

    // -----------------------------------------------------------------------
    // Confirm-I, user confirms — mutation proceeds, ending removed
    // -----------------------------------------------------------------------

    @Nested
    class ConfirmInvalidationUserConfirms {

        @Test
        void testDeleteAnchorProceedsWhenUserConfirms() {
            var env = setupPrimaryLine();

            try (var od = simulateYes();
                 var mc = mockStatic(MessageCenter.class)) {

                var scoreCoord = scoreCoordinator(env.song(), env.coordinator());
                ReflectionTestHelper.selectNote(env.coordinator(), ANCHOR_INDEX);
                scoreCoord.handlePasteboardOp(new PasteboardOpCommand(PasteboardAction.Operation.DELETE));
            }

            assertThat(env.line().elementCount()).isEqualTo(7);
            assertThat(env.line().getRangeElements()).doesNotContain(env.ending());
        }

        @Test
        void testReplaceAnchorWithDoubleBarlineProceedsWhenUserConfirms() {
            var env = setupPrimaryLine();

            try (var od = simulateYes()) {
                ReflectionTestHelper.selectNote(env.coordinator(), ANCHOR_INDEX);
                env.coordinator().applyActionToSelection(DOUBLE_BARLINE_ACTION, true);
            }

            assertThat(env.line().getElement(ANCHOR_INDEX).getType()).isEqualTo(ElementType.DOUBLE_BARLINE);
            assertThat(env.line().getRangeElements()).doesNotContain(env.ending());
        }
    }

    // -----------------------------------------------------------------------
    // Confirm-R, user declines — mutation is aborted, ending unchanged
    // -----------------------------------------------------------------------

    @Nested
    class ConfirmCompensationUserDeclines {

        @Test
        void testReplaceEndWithRepeatRightAbortsWhenUserDeclines() {
            // split=REPEAT_RIGHT; changing end to REPEAT_RIGHT requires split → REPEAT_LEFT_RIGHT
            var env = setupPrimaryLine();
            ReflectionTestHelper.selectNote(env.coordinator(), END_INDEX);
            env.coordinator().applyActionToSelection(RIGHT_REPEAT_ACTION, true);

            assertThat(env.line().getElement(SPLIT_INDEX).getType()).isEqualTo(ElementType.REPEAT_RIGHT);
            assertThat(env.line().getElement(END_INDEX).getType()).isEqualTo(ElementType.SINGLE_BARLINE);
            assertThat(env.line().getRangeElements()).contains(env.ending());
        }

        @Test
        void testReplaceSplitWithLeftRightRepeatAbortsWhenUserDeclines() {
            // split=REPEAT_RIGHT → REPEAT_LEFT_RIGHT requires end → REPEAT_RIGHT
            var env = setupPrimaryLine();
            ReflectionTestHelper.selectNote(env.coordinator(), SPLIT_INDEX);
            env.coordinator().applyActionToSelection(LEFT_RIGHT_REPEAT_ACTION, true);

            assertThat(env.line().getElement(SPLIT_INDEX).getType()).isEqualTo(ElementType.REPEAT_RIGHT);
            assertThat(env.line().getElement(END_INDEX).getType()).isEqualTo(ElementType.SINGLE_BARLINE);
            assertThat(env.line().getRangeElements()).contains(env.ending());
        }
    }

    // -----------------------------------------------------------------------
    // Confirm-R, user confirms — both changes applied, ending retained
    // -----------------------------------------------------------------------

    @Nested
    class ConfirmCompensationUserConfirms {

        @Test
        void testReplaceEndSingleBarlineWithRepeatLeftWhenSplitIsLeftRightRetainEnding() {
            // end(REPEAT_RIGHT) → REPEAT_LEFT with split=REPEAT_LEFT_RIGHT: split → REPEAT_RIGHT
            var env = setupSecondaryLine();

            try (var od = simulateYes()) {
                ReflectionTestHelper.selectNote(env.coordinator(), END_INDEX);
                env.coordinator().applyActionToSelection(LEFT_REPEAT_ACTION, true);
            }

            assertThat(env.line().getElement(SPLIT_INDEX).getType()).isEqualTo(ElementType.REPEAT_RIGHT);
            assertThat(env.line().getElement(END_INDEX).getType()).isEqualTo(ElementType.REPEAT_LEFT);
            assertThat(env.line().getRangeElements()).contains(env.ending());
        }

        @Test
        void testReplaceEndSingleBarlineWithRepeatRightWhenSplitIsRightRetainEnding() {
            // end(SINGLE_BARLINE) → REPEAT_RIGHT with split=REPEAT_RIGHT: split → REPEAT_LEFT_RIGHT
            var env = setupPrimaryLine();

            try (var od = simulateYes()) {
                ReflectionTestHelper.selectNote(env.coordinator(), END_INDEX);
                env.coordinator().applyActionToSelection(RIGHT_REPEAT_ACTION, true);
            }

            assertThat(env.line().getElement(SPLIT_INDEX).getType()).isEqualTo(ElementType.REPEAT_LEFT_RIGHT);
            assertThat(env.line().getElement(END_INDEX).getType()).isEqualTo(ElementType.REPEAT_RIGHT);
            assertThat(env.line().getRangeElements()).contains(env.ending());
        }

        @Test
        void testReplaceEndWithSingleBarlineWhenSplitIsLeftRightRetainEnding() {
            // end(REPEAT_RIGHT) → SINGLE_BARLINE with split=REPEAT_LEFT_RIGHT: split → REPEAT_RIGHT
            var env = setupSecondaryLine();

            try (var od = simulateYes()) {
                ReflectionTestHelper.selectNote(env.coordinator(), END_INDEX);
                env.coordinator().applyActionToSelection(SINGLE_BARLINE_ACTION, true);
            }

            assertThat(env.line().getElement(SPLIT_INDEX).getType()).isEqualTo(ElementType.REPEAT_RIGHT);
            assertThat(env.line().getElement(END_INDEX).getType()).isEqualTo(ElementType.SINGLE_BARLINE);
            assertThat(env.line().getRangeElements()).contains(env.ending());
        }

        @Test
        void testReplaceSplitLeftRightWithRightRepeatRetainEnding() {
            // split(REPEAT_LEFT_RIGHT) → REPEAT_RIGHT: end must become SINGLE_BARLINE
            var env = setupSecondaryLine();

            try (var od = simulateYes()) {
                ReflectionTestHelper.selectNote(env.coordinator(), SPLIT_INDEX);
                env.coordinator().applyActionToSelection(RIGHT_REPEAT_ACTION, true);
            }

            assertThat(env.line().getElement(SPLIT_INDEX).getType()).isEqualTo(ElementType.REPEAT_RIGHT);
            assertThat(env.line().getElement(END_INDEX).getType()).isEqualTo(ElementType.SINGLE_BARLINE);
            assertThat(env.line().getRangeElements()).contains(env.ending());
        }

        @Test
        void testReplaceSplitRightWithLeftRightRepeatRetainEnding() {
            // split(REPEAT_RIGHT) → REPEAT_LEFT_RIGHT: end must become REPEAT_RIGHT
            var env = setupPrimaryLine();

            try (var od = simulateYes()) {
                ReflectionTestHelper.selectNote(env.coordinator(), SPLIT_INDEX);
                env.coordinator().applyActionToSelection(LEFT_RIGHT_REPEAT_ACTION, true);
            }

            assertThat(env.line().getElement(SPLIT_INDEX).getType()).isEqualTo(ElementType.REPEAT_LEFT_RIGHT);
            assertThat(env.line().getElement(END_INDEX).getType()).isEqualTo(ElementType.REPEAT_RIGHT);
            assertThat(env.line().getRangeElements()).contains(env.ending());
        }
    }
}
