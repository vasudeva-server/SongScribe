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

package songscribe.ui.action;

import module java.desktop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import songscribe.MainFrameMockTest;
import songscribe.Strings;
import songscribe.dom.Hairpin;
import songscribe.message.MessageCenter;
import songscribe.message.command.AddHairpinCommand;
import songscribe.message.notification.MusicSelectionDidChangeNotification;
import songscribe.message.notification.RestModeDidChangeNotification;
import songscribe.ui.MusicEditOperations;

class HairpinActionTest extends MainFrameMockTest {

    /** Passes REQUIRES_SELECTION with more than one note, so the resolved state decides. */
    private static final int MULTIPLE_SELECTION_SIZE = 2;

    /** One note: passes REQUIRES_SELECTION, since a single-note extend is admissible. */
    private static final int SINGLE_SELECTION_SIZE = 1;

    /** Span the stubbed CAN_ADD / EXTEND resolutions report; never read by the action. */
    private static final int STUB_SPAN_END = 1;

    /**
     * Stubs {@code resolveHairpinAction(kind)} with a resolution in {@code state}. The span
     * is -1 for the states that carry none and [0, 1] otherwise, matching the record's
     * contract.
     */
    private void stubResolution(Hairpin.Kind kind, MusicEditOperations.HairpinActionState state) {
        var hasSpan = state == MusicEditOperations.HairpinActionState.CAN_ADD
            || state == MusicEditOperations.HairpinActionState.EXTEND;
        var spanBegin = hasSpan ? 0 : -1;
        var spanEnd = hasSpan ? STUB_SPAN_END : -1;

        when(mockEnv().ctrl().resolveHairpinAction(kind)).thenReturn(
            new MusicEditOperations.HairpinResolution(state, spanBegin, spanEnd));
    }

    /** A multi-note duration selection — the flag gate every resolved state is tested behind. */
    private void selectMultipleNotes() {
        when(mockEnv().score().getSelectionSize()).thenReturn(MULTIPLE_SELECTION_SIZE);
        when(mockEnv().coordinator().hasActiveSelection()).thenReturn(true);
        when(mockEnv().coordinator().selectionHasDurations()).thenReturn(true);
    }

    /**
     * Resolves each item's own state, then asserts the label and enabled flag both hairpin
     * actions end up with. The two states may differ — each menu item resolves separately.
     */
    private void assertBothActions(
        MusicEditOperations.HairpinActionState crescendoState,
        String crescendoLabel,
        boolean crescendoEnabled,
        MusicEditOperations.HairpinActionState diminuendoState,
        String diminuendoLabel,
        boolean diminuendoEnabled
    ) {
        stubResolution(Hairpin.Kind.CRESCENDO, crescendoState);
        stubResolution(Hairpin.Kind.DIMINUENDO, diminuendoState);
        var crescendo = HairpinAction.createCrescendoAction(mainFrame());
        var diminuendo = HairpinAction.createDiminuendoAction(mainFrame());

        crescendo.updateEnabledState();
        diminuendo.updateEnabledState();

        assertAll(
            () -> assertThat(crescendo.getValue(Action.NAME)).as("crescendo label").isEqualTo(crescendoLabel),
            () -> assertThat(crescendo.isEnabled()).as("crescendo enabled").isEqualTo(crescendoEnabled),
            () -> assertThat(diminuendo.getValue(Action.NAME)).as("diminuendo label").isEqualTo(diminuendoLabel),
            () -> assertThat(diminuendo.isEnabled()).as("diminuendo enabled").isEqualTo(diminuendoEnabled));
    }

    // Row 8: factory methods bind the kind field correctly

    @Test
    void testCreateCrescendoActionSetsCrescendoKind() {
        var action = HairpinAction.createCrescendoAction(mainFrame());
        assertThat(action.getKind()).isEqualTo(Hairpin.Kind.CRESCENDO);
    }

    @Test
    void testCreateDiminuendoActionSetsDiminuendoKind() {
        var action = HairpinAction.createDiminuendoAction(mainFrame());
        assertThat(action.getKind()).isEqualTo(Hairpin.Kind.DIMINUENDO);
    }

    // Row 9: actionPerformed posts AddHairpinCommand with the correct kind

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class ActionPerformed {

        private MockedStatic<MessageCenter> messageMock;

        @BeforeEach
        void setUpMessageCenter() {
            messageMock = mockStatic(MessageCenter.class);
        }

        @AfterEach
        void tearDownMessageCenter() {
            messageMock.close();
        }

        @Test
        void testActionPerformedPostsCrescendoCommand() {
            var action = HairpinAction.createCrescendoAction(mainFrame());
            var e = new ActionEvent(new JButton(), ActionEvent.ACTION_PERFORMED, "add-crescendo");

            action.actionPerformed(e);

            var captor = ArgumentCaptor.forClass(AddHairpinCommand.class);
            messageMock.verify(() -> MessageCenter.post(captor.capture()));
            assertThat(captor.getValue().kind()).isEqualTo(Hairpin.Kind.CRESCENDO);
        }

        @Test
        void testActionPerformedPostsDiminuendoCommand() {
            var action = HairpinAction.createDiminuendoAction(mainFrame());
            var e = new ActionEvent(new JButton(), ActionEvent.ACTION_PERFORMED, "add-diminuendo");

            action.actionPerformed(e);

            var captor = ArgumentCaptor.forClass(AddHairpinCommand.class);
            messageMock.verify(() -> MessageCenter.post(captor.capture()));
            // A mutant swapping the kind arg would make this CRESCENDO — this assertion catches it.
            assertThat(captor.getValue().kind()).isEqualTo(Hairpin.Kind.DIMINUENDO);
        }
    }

    // Row 10: critical enablement flag combinations for REQUIRES_SELECTION and
    //         DISABLE_WHEN_BAR_SELECTED

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class EnabledState {

        @AfterEach
        void resetRestAction() {
            Actions.REST_ACTION.setSelected(false);
        }

        @Test
        void testDisabledWhenNoSelection() {
            // REQUIRES_SELECTION: 0 notes → disabled
            when(mockEnv().score().getSelectionSize()).thenReturn(0);

            var action = HairpinAction.createCrescendoAction(mainFrame());
            action.updateEnabledState();

            assertThat(action.isEnabled()).as("no selection must disable the action").isFalse();
        }

        @Test
        void testDisabledWhenSingleNoteSelected() {
            // REQUIRES_SELECTION: 1 note passes the flag gate — single-note extend needs it
            // admissible — so the resolved state decides. INELIGIBLE → disabled.
            when(mockEnv().score().getSelectionSize()).thenReturn(SINGLE_SELECTION_SIZE);
            when(mockEnv().coordinator().hasActiveSelection()).thenReturn(true);
            when(mockEnv().coordinator().selectionHasDurations()).thenReturn(true);
            stubResolution(Hairpin.Kind.CRESCENDO, MusicEditOperations.HairpinActionState.INELIGIBLE);

            var action = HairpinAction.createCrescendoAction(mainFrame());
            action.updateEnabledState();

            assertThat(action.isEnabled())
                .as("a single selected note resolving to INELIGIBLE must disable the action")
                .isFalse();
        }

        @Test
        void testEnabledWhenMultipleNotesSelected() {
            // REQUIRES_SELECTION: 2 notes → check passes.
            // With an active selection: enableFromBarSelection returns true immediately,
            // and enableFromSelection (DISABLE_WHEN_BAR_SELECTED path) defers to selectionHasDurations.
            selectMultipleNotes();
            stubResolution(Hairpin.Kind.CRESCENDO, MusicEditOperations.HairpinActionState.CAN_ADD);

            var action = HairpinAction.createCrescendoAction(mainFrame());
            action.updateEnabledState();

            assertThat(action.isEnabled()).as("multiple selected notes must enable the action").isTrue();
        }

        @Test
        void testSelectionContainingRestsStaysEnabledWhenResolutionCanAdd() {
            // DISABLE_IN_REST_MODE is gone from this action: the resolution, not a flag,
            // decides whether a selection holding a rest may carry a hairpin.
            selectMultipleNotes();
            when(mockEnv().coordinator().selectionHasRests()).thenReturn(true);
            stubResolution(Hairpin.Kind.CRESCENDO, MusicEditOperations.HairpinActionState.CAN_ADD);

            var action = HairpinAction.createCrescendoAction(mainFrame());
            action.updateEnabledState();

            assertThat(action.isEnabled())
                .as("a rest in the selection must not disable the action — a hairpin may end on one")
                .isTrue();
        }

        @Test
        void testStaysEnabledWhileRestToolIsArmed() {
            // The other half of the dropped flag: the resolution decides here too. The armed
            // input mode governs the next inserted element, not what a hairpin drawn over an
            // existing selection may cover.
            selectMultipleNotes();
            Actions.REST_ACTION.setSelected(true);
            stubResolution(Hairpin.Kind.CRESCENDO, MusicEditOperations.HairpinActionState.CAN_ADD);

            var action = HairpinAction.createCrescendoAction(mainFrame());
            action.updateEnabledState();

            assertThat(action.isEnabled())
                .as("an armed rest tool must not disable the action")
                .isTrue();
        }
    }

    // Row 11: musicSelectionDidChange forwards the resolveHairpinAction() state to the
    // enabled state, gated by updateEnabledState() and a non-null ScoreViewController

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class MusicSelectionDidChange {

        // An empty selection fails REQUIRES_SELECTION, so updateEnabledState() returns false.
        private static final int NO_SELECTION_SIZE = 0;

        @BeforeEach
        void setUpMultipleSelection() {
            selectMultipleNotes();
        }

        @Test
        void testDisabledWhenResolutionIsIneligible() {
            // A resolution of INELIGIBLE is forwarded directly to setEnabled(false).
            stubResolution(Hairpin.Kind.CRESCENDO, MusicEditOperations.HairpinActionState.INELIGIBLE);
            var action = HairpinAction.createCrescendoAction(mainFrame());

            action.musicSelectionDidChange(new MusicSelectionDidChangeNotification(mockEnv().score()));

            assertThat(action.isEnabled())
                .as("an INELIGIBLE resolution must disable the action")
                .isFalse();
        }

        @Test
        void testEnabledWhenResolutionIsCanAdd() {
            // A resolution of CAN_ADD is forwarded directly to setEnabled(true).
            stubResolution(Hairpin.Kind.DIMINUENDO, MusicEditOperations.HairpinActionState.CAN_ADD);
            var action = HairpinAction.createDiminuendoAction(mainFrame());

            action.musicSelectionDidChange(new MusicSelectionDidChangeNotification(mockEnv().score()));

            assertThat(action.isEnabled())
                .as("a CAN_ADD resolution must enable the action")
                .isTrue();
        }

        @Test
        void testStaysDisabledWhenUpdateEnabledStateReturnsFalse() {
            // An empty selection fails REQUIRES_SELECTION, so updateEnabledState()
            // returns false and the resolved state must never override that to enabled.
            when(mockEnv().score().getSelectionSize()).thenReturn(NO_SELECTION_SIZE);
            stubResolution(Hairpin.Kind.CRESCENDO, MusicEditOperations.HairpinActionState.CAN_ADD);
            var action = HairpinAction.createCrescendoAction(mainFrame());

            action.musicSelectionDidChange(new MusicSelectionDidChangeNotification(mockEnv().score()));

            assertThat(action.isEnabled())
                .as("a false updateEnabledState() result must not be overridden by the resolved state")
                .isFalse();
        }

        @Test
        void testEnabledStateUnchangedWhenScoreViewControllerIsNull() {
            // A null controller short-circuits the handler entirely, leaving the action's
            // enabled state at whatever it was before the notification (false, from construction).
            when(mockEnv().score().getController()).thenReturn(null);
            stubResolution(Hairpin.Kind.CRESCENDO, MusicEditOperations.HairpinActionState.CAN_ADD);
            var action = HairpinAction.createCrescendoAction(mainFrame());

            action.musicSelectionDidChange(new MusicSelectionDidChangeNotification(mockEnv().score()));

            assertThat(action.isEnabled())
                .as("a null ScoreViewController must leave the action's enabled state untouched")
                .isFalse();
        }
    }

    // The label/enabled table updateEnabledState() writes, one row per HairpinActionState.
    // The label matters as much as the flag: the flags alone cannot tell an add from an
    // extend, so a stale NAME would promise "Extend Crescendo" while adding a second one.

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class LabelAndEnabledPerState {

        @BeforeEach
        void setUpEligibleSelection() {
            selectMultipleNotes();
        }

        @Test
        void testIneligibleShowsAddLabelsDisabledOnBothActions() {
            assertBothActions(
                MusicEditOperations.HairpinActionState.INELIGIBLE,
                Strings.get(Strings.ACTION_HAIRPIN_CRESCENDO), false,
                MusicEditOperations.HairpinActionState.INELIGIBLE,
                Strings.get(Strings.ACTION_HAIRPIN_DIMINUENDO), false);
        }

        @Test
        void testBlockedShowsAddLabelsDisabledOnBothActions() {
            assertBothActions(
                MusicEditOperations.HairpinActionState.BLOCKED,
                Strings.get(Strings.ACTION_HAIRPIN_CRESCENDO), false,
                MusicEditOperations.HairpinActionState.BLOCKED,
                Strings.get(Strings.ACTION_HAIRPIN_DIMINUENDO), false);
        }

        @Test
        void testCanAddShowsAddLabelsEnabledOnBothActions() {
            assertBothActions(
                MusicEditOperations.HairpinActionState.CAN_ADD,
                Strings.get(Strings.ACTION_HAIRPIN_CRESCENDO), true,
                MusicEditOperations.HairpinActionState.CAN_ADD,
                Strings.get(Strings.ACTION_HAIRPIN_DIMINUENDO), true);
        }

        @Test
        void testCrescendoExtendWithDiminuendoBlockedRelabelsOnlyTheCrescendoAction() {
            assertBothActions(
                MusicEditOperations.HairpinActionState.EXTEND,
                Strings.get(Strings.ACTION_HAIRPIN_CRESCENDO_EXTEND), true,
                MusicEditOperations.HairpinActionState.BLOCKED,
                Strings.get(Strings.ACTION_HAIRPIN_DIMINUENDO), false);
        }

        @Test
        void testDiminuendoExtendWithCrescendoBlockedRelabelsOnlyTheDiminuendoAction() {
            assertBothActions(
                MusicEditOperations.HairpinActionState.BLOCKED,
                Strings.get(Strings.ACTION_HAIRPIN_CRESCENDO), false,
                MusicEditOperations.HairpinActionState.EXTEND,
                Strings.get(Strings.ACTION_HAIRPIN_DIMINUENDO_EXTEND), true);
        }

        @Test
        void testBackToBackSelectionEnablesExtendCrescendoAndAddDiminuendoTogether() {
            assertBothActions(
                MusicEditOperations.HairpinActionState.EXTEND,
                Strings.get(Strings.ACTION_HAIRPIN_CRESCENDO_EXTEND), true,
                MusicEditOperations.HairpinActionState.CAN_ADD,
                Strings.get(Strings.ACTION_HAIRPIN_DIMINUENDO), true);
        }
    }

    // Regression guard: updateEnabledState() is the single writer of the enabled flag,
    // so even a purely flag-driven trigger must land on the resolved state. Without the
    // override, super's flag-only setEnabled(true) would re-admit the one-element hairpin
    // a single INELIGIBLE note used to produce.

    @Test
    void testFlagOnlyTriggerLeavesIneligibleSingleNoteDisabledAndLabeledAdd() {
        when(mockEnv().score().getSelectionSize()).thenReturn(SINGLE_SELECTION_SIZE);
        when(mockEnv().coordinator().hasActiveSelection()).thenReturn(true);
        when(mockEnv().coordinator().selectionHasDurations()).thenReturn(true);
        stubResolution(Hairpin.Kind.CRESCENDO, MusicEditOperations.HairpinActionState.INELIGIBLE);

        var action = HairpinAction.createCrescendoAction(mainFrame());
        // Rest-mode change carries no selection information — it ends in the flag-only
        // setEnabled(enable) that the override must not be allowed to leave as the last word.
        action.restModeDidChange(new RestModeDidChangeNotification());

        assertAll(
            () -> assertThat(action.isEnabled())
                .as("a flag-only trigger must not enable an INELIGIBLE single-note selection")
                .isFalse(),
            () -> assertThat(action.getValue(Action.NAME))
                .as("the label must stay the add label")
                .isEqualTo(Strings.get(Strings.ACTION_HAIRPIN_CRESCENDO)));
    }
}
