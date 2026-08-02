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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.awt.Component;
import java.util.ArrayList;

import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JRootPane;

import org.junit.jupiter.api.BeforeEach;
import org.mockito.MockedStatic;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.Strings;
import songscribe.message.MessageCenter;
import songscribe.message.command.PasteboardOpCommand;
import songscribe.dom.Song;
import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.layout.EndingLineFixture;
import songscribe.ui.action.ElementTypeAction;
import songscribe.ui.action.PasteboardAction;
import songscribe.ui.clipboard.ClipboardManager;
import songscribe.ui.component.MainFrame;
import songscribe.ui.component.ScoreView;
import songscribe.ui.component.ScoreViewController;

import songscribe.dom.Ending;
import songscribe.ui.selection.ReflectionTestHelper;
import songscribe.ui.selection.SelectionCoordinator;

/**
 * Integration tests for the {@link EndingConfirms} wiring in
 * {@link SelectionCoordinator} and {@link ScoreViewController}.
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
    private ElementTypeAction LEFT_REPEAT_ACTION;
    private ElementTypeAction RIGHT_REPEAT_ACTION;
    private ElementTypeAction LEFT_RIGHT_REPEAT_ACTION;
    private ElementTypeAction SINGLE_BARLINE_ACTION;

    @BeforeEach
    void createActions() {
        var mainFrame = mock(MainFrame.class);
        var rootPane = mock(JRootPane.class);
        when(rootPane.getInputMap(anyInt())).thenReturn(new InputMap());
        when(rootPane.getActionMap()).thenReturn(new ActionMap());
        when(mainFrame.getRootPane()).thenReturn(rootPane);
        LEFT_REPEAT_ACTION = ElementTypeAction.createLeftRepeatAction(mainFrame);
        RIGHT_REPEAT_ACTION = ElementTypeAction.createRightRepeatAction(mainFrame);
        LEFT_RIGHT_REPEAT_ACTION = ElementTypeAction.createLeftRightRepeatAction(mainFrame);
        SINGLE_BARLINE_ACTION = ElementTypeAction.createSingleBarlineAction(mainFrame);
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

        coordinator.setManagedActions(new ArrayList<>());

        return coordinator;
    }

    /**
     * Creates a {@link ScoreViewController} backed by a mock ScoreView.
     * Must be called AFTER {@link MessageCenter} is mocked so the coordinator's
     * {@code subscribe} call goes to the mock bus.
     */
    private ScoreViewController scoreCoordinator(Song song, SelectionCoordinator coordinator) {
        var score = mock(ScoreView.class);
        when(score.getSong()).thenReturn(song);
        when(score.isFocusOwner()).thenReturn(true);
        return new ScoreViewController(
            score,
            new MusicEditOperations(song, coordinator),
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
            any(), any(), any(), anyInt(), anyInt(), any(), any(), any(), any()
        )).thenReturn(0);
        return mock;
    }

    // -----------------------------------------------------------------------
    // Confirm-I, user declines — mutation is aborted
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
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
            assertThat(env.line().getSpans()).contains(env.ending());
        }

        @Test
        void testReplaceSplitWithSingleBarlineAbortsWhenUserDeclines() {
            // #306: the anchor no longer invalidates on a DOUBLE_BARLINE replacement (barlines
            // are now a valid anchor type). Use the split (Condition 2), which still invalidates
            // on a non-repeat replacement, to exercise the Invalidate confirm-dialog flow.
            var env = setupPrimaryLine();
            ReflectionTestHelper.selectNote(env.coordinator(), SPLIT_INDEX);
            env.coordinator().applyActionToSelection(SINGLE_BARLINE_ACTION, true, null);

            assertThat(env.line().getElement(SPLIT_INDEX).getType()).isEqualTo(ElementType.REPEAT_RIGHT);
            assertThat(env.line().getSpans()).contains(env.ending());
        }
    }

    // -----------------------------------------------------------------------
    // Confirm-I, user confirms — mutation proceeds, ending removed
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
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
            assertThat(env.line().getSpans()).doesNotContain(env.ending());
        }

        @Test
        void testReplaceSplitWithSingleBarlineProceedsWhenUserConfirms() {
            // #306: see testReplaceSplitWithSingleBarlineAbortsWhenUserDeclines — the split
            // (Condition 2) still invalidates on a non-repeat replacement.
            var env = setupPrimaryLine();

            try (var od = simulateYes()) {
                ReflectionTestHelper.selectNote(env.coordinator(), SPLIT_INDEX);
                env.coordinator().applyActionToSelection(SINGLE_BARLINE_ACTION, true, null);
            }

            assertThat(env.line().getElement(SPLIT_INDEX).getType()).isEqualTo(ElementType.SINGLE_BARLINE);
            assertThat(env.line().getSpans()).doesNotContain(env.ending());
        }
    }

    // -----------------------------------------------------------------------
    // Confirm-R, user declines — mutation is aborted, ending unchanged
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class ConfirmCompensationUserDeclines {

        @Test
        void testReplaceEndWithRepeatRightAbortsWhenUserDeclines() {
            // split=REPEAT_RIGHT; changing end to REPEAT_RIGHT requires split → REPEAT_LEFT_RIGHT
            var env = setupPrimaryLine();
            ReflectionTestHelper.selectNote(env.coordinator(), END_INDEX);
            env.coordinator().applyActionToSelection(RIGHT_REPEAT_ACTION, true, null);

            assertThat(env.line().getElement(SPLIT_INDEX).getType()).isEqualTo(ElementType.REPEAT_RIGHT);
            assertThat(env.line().getElement(END_INDEX).getType()).isEqualTo(ElementType.SINGLE_BARLINE);
            assertThat(env.line().getSpans()).contains(env.ending());
        }

        @Test
        void testReplaceSplitWithLeftRightRepeatAbortsWhenUserDeclines() {
            // split=REPEAT_RIGHT → REPEAT_LEFT_RIGHT requires end → REPEAT_RIGHT
            var env = setupPrimaryLine();
            ReflectionTestHelper.selectNote(env.coordinator(), SPLIT_INDEX);
            env.coordinator().applyActionToSelection(LEFT_RIGHT_REPEAT_ACTION, true, null);

            assertThat(env.line().getElement(SPLIT_INDEX).getType()).isEqualTo(ElementType.REPEAT_RIGHT);
            assertThat(env.line().getElement(END_INDEX).getType()).isEqualTo(ElementType.SINGLE_BARLINE);
            assertThat(env.line().getSpans()).contains(env.ending());
        }
    }

    // -----------------------------------------------------------------------
    // Confirm-R, user confirms — both changes applied, ending retained
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class ConfirmCompensationUserConfirms {

        @Test
        void testReplaceEndSingleBarlineWithRepeatLeftWhenSplitIsLeftRightRetainEnding() {
            // end(REPEAT_RIGHT) → REPEAT_LEFT with split=REPEAT_LEFT_RIGHT: split → REPEAT_RIGHT
            var env = setupSecondaryLine();

            try (var od = simulateYes()) {
                ReflectionTestHelper.selectNote(env.coordinator(), END_INDEX);
                env.coordinator().applyActionToSelection(LEFT_REPEAT_ACTION, true, null);
            }

            assertThat(env.line().getElement(SPLIT_INDEX).getType()).isEqualTo(ElementType.REPEAT_RIGHT);
            assertThat(env.line().getElement(END_INDEX).getType()).isEqualTo(ElementType.REPEAT_LEFT);
            assertThat(env.line().getSpans()).contains(env.ending());
        }

        @Test
        void testReplaceEndSingleBarlineWithRepeatRightWhenSplitIsRightRetainEnding() {
            // end(SINGLE_BARLINE) → REPEAT_RIGHT with split=REPEAT_RIGHT: split → REPEAT_LEFT_RIGHT
            var env = setupPrimaryLine();

            try (var od = simulateYes()) {
                ReflectionTestHelper.selectNote(env.coordinator(), END_INDEX);
                env.coordinator().applyActionToSelection(RIGHT_REPEAT_ACTION, true, null);
            }

            assertThat(env.line().getElement(SPLIT_INDEX).getType()).isEqualTo(ElementType.REPEAT_LEFT_RIGHT);
            assertThat(env.line().getElement(END_INDEX).getType()).isEqualTo(ElementType.REPEAT_RIGHT);
            assertThat(env.line().getSpans()).contains(env.ending());
        }

        @Test
        void testReplaceEndWithSingleBarlineWhenSplitIsLeftRightRetainEnding() {
            // end(REPEAT_RIGHT) → SINGLE_BARLINE with split=REPEAT_LEFT_RIGHT: split → REPEAT_RIGHT
            var env = setupSecondaryLine();

            try (var od = simulateYes()) {
                ReflectionTestHelper.selectNote(env.coordinator(), END_INDEX);
                env.coordinator().applyActionToSelection(SINGLE_BARLINE_ACTION, true, null);
            }

            assertThat(env.line().getElement(SPLIT_INDEX).getType()).isEqualTo(ElementType.REPEAT_RIGHT);
            assertThat(env.line().getElement(END_INDEX).getType()).isEqualTo(ElementType.SINGLE_BARLINE);
            assertThat(env.line().getSpans()).contains(env.ending());
        }

        @Test
        void testReplaceSplitLeftRightWithRightRepeatRetainEnding() {
            // split(REPEAT_LEFT_RIGHT) → REPEAT_RIGHT: end must become SINGLE_BARLINE
            var env = setupSecondaryLine();

            try (var od = simulateYes()) {
                ReflectionTestHelper.selectNote(env.coordinator(), SPLIT_INDEX);
                env.coordinator().applyActionToSelection(RIGHT_REPEAT_ACTION, true, null);
            }

            assertThat(env.line().getElement(SPLIT_INDEX).getType()).isEqualTo(ElementType.REPEAT_RIGHT);
            assertThat(env.line().getElement(END_INDEX).getType()).isEqualTo(ElementType.SINGLE_BARLINE);
            assertThat(env.line().getSpans()).contains(env.ending());
        }

        @Test
        void testReplaceSplitRightWithLeftRightRepeatRetainEnding() {
            // split(REPEAT_RIGHT) → REPEAT_LEFT_RIGHT: end must become REPEAT_RIGHT
            var env = setupPrimaryLine();

            try (var od = simulateYes()) {
                ReflectionTestHelper.selectNote(env.coordinator(), SPLIT_INDEX);
                env.coordinator().applyActionToSelection(LEFT_RIGHT_REPEAT_ACTION, true, null);
            }

            assertThat(env.line().getElement(SPLIT_INDEX).getType()).isEqualTo(ElementType.REPEAT_LEFT_RIGHT);
            assertThat(env.line().getElement(END_INDEX).getType()).isEqualTo(ElementType.REPEAT_RIGHT);
            assertThat(env.line().getSpans()).contains(env.ending());
        }
    }

    // -----------------------------------------------------------------------
    // Parent-forwarding assertion (decision 11A): verify that the Component
    // passed to the public confirm methods is forwarded to showOptionDialog.
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class ParentForwarding {

        @Test
        void testConfirmInvalidationForwardsParentToOptionDialog() {
            var parent = mock(Component.class);

            try (var od = mockStatic(OptionDialogs.class)) {
                od.when(() -> OptionDialogs.showOptionDialog(any(), any(), any(), anyInt(), anyInt(), any(), any(), any()))
                    .thenReturn(0);

                EndingConfirms.confirmInvalidation(parent);

                od.verify(() -> OptionDialogs.showOptionDialog(
                    eq(parent), any(), any(), anyInt(), anyInt(), any(), any(), any()
                ));
            }
        }

        @Test
        void testConfirmCompensateEndForwardsParentToOptionDialog() {
            var parent = mock(Component.class);
            var fixture = EndingLineFixture.primary();
            var ce = new Ending.EndingEffect.CompensateEnd(fixture.ending(), ElementType.REPEAT_RIGHT);

            try (var od = mockStatic(OptionDialogs.class)) {
                od.when(() -> OptionDialogs.showOptionDialog(any(), any(), any(), anyInt(), anyInt(), any(), any(), any()))
                    .thenReturn(0);

                EndingConfirms.confirmCompensateEnd(parent, ce);

                od.verify(() -> OptionDialogs.showOptionDialog(
                    eq(parent), any(), any(), anyInt(), anyInt(), any(), any(), any()
                ));
            }
        }

        @Test
        void testConfirmCompensateSplitForwardsParentToOptionDialog() {
            var parent = mock(Component.class);
            var fixture = EndingLineFixture.primary();
            var cs = new Ending.EndingEffect.CompensateSplit(fixture.ending(), ElementType.REPEAT_LEFT_RIGHT);

            try (var od = mockStatic(OptionDialogs.class)) {
                od.when(() -> OptionDialogs.showOptionDialog(any(), any(), any(), anyInt(), anyInt(), any(), any(), any(), any()))
                    .thenReturn(0);

                EndingConfirms.confirmCompensateSplit(parent, cs, ElementType.REPEAT_RIGHT);

                od.verify(() -> OptionDialogs.showOptionDialog(
                    eq(parent), any(), any(), anyInt(), anyInt(), any(), any(), any(), any()
                ));
            }
        }
    }

    // -----------------------------------------------------------------------
    // typeNameFor — all four ElementType branches
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class TypeNameFor {

        @Test
        void testRepeatRightReturnsRightRepeatName() {
            assertThat(EndingConfirms.typeNameFor(ElementType.REPEAT_RIGHT))
                .isEqualTo(Strings.get(Strings.ELEMENT_TYPE_NAME_RIGHT_REPEAT));
        }

        @Test
        void testRepeatLeftRightReturnsLeftRightRepeatName() {
            assertThat(EndingConfirms.typeNameFor(ElementType.REPEAT_LEFT_RIGHT))
                .isEqualTo(Strings.get(Strings.ELEMENT_TYPE_NAME_LEFT_RIGHT_REPEAT));
        }

        @Test
        void testRepeatLeftReturnsLeftRepeatName() {
            assertThat(EndingConfirms.typeNameFor(ElementType.REPEAT_LEFT))
                .isEqualTo(Strings.get(Strings.ELEMENT_TYPE_NAME_LEFT_REPEAT));
        }

        @Test
        void testDefaultElementTypeReturnsBarlineName() {
            // Any non-repeat type falls through to the default barline branch
            assertThat(EndingConfirms.typeNameFor(ElementType.SINGLE_BARLINE))
                .isEqualTo(Strings.get(Strings.ELEMENT_TYPE_NAME_BARLINE));
        }
    }

    // -----------------------------------------------------------------------
    // applyCompensatingChange — null targetEl guard
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class ApplyCompensatingChangeNullGuard {

        @Test
        void testNullEndElementSkipsSilently() {
            // An ending whose end element is null causes getEndElement() to return null,
            // so applyCompensatingEndChange must return without modifying the line.
            var fixture = EndingLineFixture.primary();
            var line = fixture.line();
            var elementCountBefore = line.elementCount();

            // Detach the end element from the ending so getEndElement() returns null.
            fixture.ending().setEndElement(null);

            var ce = new Ending.EndingEffect.CompensateEnd(fixture.ending(), ElementType.REPEAT_RIGHT);
            EndingConfirms.applyCompensatingEndChange(line, ce);

            // Line must be untouched — the null guard fired and returned early.
            assertThat(line.elementCount()).isEqualTo(elementCountBefore);
        }
    }
}
