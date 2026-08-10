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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static songscribe.dom.StaffElementFactory.crotchet;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import songscribe.MainFrameMockTest;
import songscribe.dom.FermataAttachment;
import songscribe.message.MessageCenter;
import songscribe.message.notification.GraceModeStateDidChangeNotification;
import songscribe.ui.edit.GraceModeManager;
import songscribe.ui.selection.ElementSelection;

class FermataActionTest extends MainFrameMockTest {

    private FermataAction action;

    @BeforeEach
    void createAction() {
        action = FermataAction.createAction(mainFrame());
    }

    @Test
    void testApplyToNoteAppliesFermata() {
        var note = crotchet();
        action.applyToElement(note, true);
        assertThat(note.findAttachment(FermataAttachment.class)).isNotNull();
    }

    @Test
    void testApplyToNoteRemovesFermata() {
        var note = crotchet();
        note.addAttachment(new FermataAttachment(note));
        action.applyToElement(note, false);
        assertThat(note.findAttachment(FermataAttachment.class)).isNull();
    }

    @Test
    void testDoesNotMatchWhenFermataFalse() {
        var note = crotchet();
        assertThat(action.matchesElement(note)).isFalse();
    }

    @Test
    void testMatchesWhenFermataTrue() {
        var note = crotchet();
        note.addAttachment(new FermataAttachment(note));
        assertThat(action.matchesElement(note)).isTrue();
    }

    @Test
    void testApplyToNoteWithExistingFermataIsIdempotent() {
        var note = crotchet();
        action.applyToElement(note, true);
        action.applyToElement(note, true);
        assertThat(note.getAttachments())
            .filteredOn(a -> a instanceof FermataAttachment)
            .hasSize(1);
    }

    // G3: FermataAction picked up Flag.DISABLE_IN_GRACE_MODE (Phase 4) so that it
    // behaves like TrillAction and is disabled during grace-note entry.
    @Test
    void testDisabledWhileGraceModeIsActive() {
        // Give the action an otherwise-fully-enabled selection (single pitched note,
        // not inside a hairpin) so DISABLE_IN_GRACE_MODE is the only thing in play.
        final var selectedElementIndex = 0;
        var line = detachedLine();
        line.addElement(crotchet());
        var selection = new ElementSelection(line, selectedElementIndex, selectedElementIndex);
        var selectionSize = selectedElementIndex + 1;

        when(mockEnv().score().getSelectionSize()).thenReturn(selectionSize);
        when(mockEnv().coordinator().hasActiveSelection()).thenReturn(true);
        when(mockEnv().coordinator().isApplicableToSelection(any())).thenReturn(true);
        when(mockEnv().coordinator().getSelection()).thenReturn(selection);

        try (var graceModeMock = mockStatic(GraceModeManager.class)) {
            graceModeMock.when(GraceModeManager::isActive).thenReturn(false);
            action.graceModeStateDidChange(new GraceModeStateDidChangeNotification(false));
            assertThat(action.isEnabled())
                .as("sanity check: action must be enabled before grace mode activates")
                .isTrue();

            graceModeMock.when(GraceModeManager::isActive).thenReturn(true);
            action.graceModeStateDidChange(new GraceModeStateDidChangeNotification(true));
            assertThat(action.isEnabled()).isFalse();
        }
    }

    // Phase 7: FermataAction now carries Flag.REQUIRES_SINGLE_SELECTION (mirrors
    // TrillAction's G1 enablement tests), so it is enabled only with exactly one
    // pitched note selected.
    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class Enablement {

        @Test
        void testNoSelectionIsDisabled() {
            when(mockEnv().score().getSelectionSize()).thenReturn(0);

            action.updateEnabledState();

            assertThat(action.isEnabled()).isFalse();
        }

        @Test
        void testSinglePitchedNoteSelectionIsEnabled() {
            var line = detachedLine();
            line.addElement(crotchet());
            var selection = new ElementSelection(line, 0, 0);

            when(mockEnv().score().getSelectionSize()).thenReturn(1);
            when(mockEnv().coordinator().hasActiveSelection()).thenReturn(true);
            when(mockEnv().coordinator().isApplicableToSelection(any())).thenReturn(true);
            when(mockEnv().coordinator().getSelection()).thenReturn(selection);

            action.updateEnabledState();

            assertThat(action.isEnabled()).isTrue();
        }

        @Test
        void testMultiNoteSelectionIsDisabled() {
            // Give the action an otherwise-fully-enabled multi-note selection (applicable,
            // non-null) so REQUIRES_SINGLE_SELECTION's size gate is the only disabler. Without
            // stubbing isApplicableToSelection/getSelection, the action would be disabled by the
            // unstubbed Reflectable check and pass even if the single-selection flag were removed.
            final var multiNoteSelectionSize = 2;
            var line = detachedLine();
            line.addElement(crotchet());
            line.addElement(crotchet());
            var selection = new ElementSelection(line, 0, multiNoteSelectionSize - 1);

            when(mockEnv().score().getSelectionSize()).thenReturn(multiNoteSelectionSize);
            when(mockEnv().coordinator().hasActiveSelection()).thenReturn(true);
            when(mockEnv().coordinator().isApplicableToSelection(any())).thenReturn(true);
            when(mockEnv().coordinator().getSelection()).thenReturn(selection);

            action.updateEnabledState();

            assertThat(action.isEnabled())
                .as("REQUIRES_SINGLE_SELECTION must reject a 2-note selection even when otherwise applicable")
                .isFalse();
        }
    }

    // Phase 7: FermataAction overrides actionPerformed to drop the inherited
    // preview-element fallback (toggleOnKeyboardShortcut + applyToSelectionIfActive
    // only, no UpdatePreviewElementCommand branch).
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
        void testNoSelectionPostsNoUpdatePreviewElementCommand() {
            // coordinator.getSelection() returns null by default — no active selection.
            action.actionPerformed(
                new ActionEvent(new JButton(), ActionEvent.ACTION_PERFORMED, "fermata")
            );

            messageMock.verify(() -> MessageCenter.post(any()), never());
        }

        @Test
        void testKeyboardShortcutTogglesSelectedState() {
            assertThat(action.isSelected()).isFalse();

            action.actionPerformed(
                new ActionEvent(new JRootPane(), ActionEvent.ACTION_PERFORMED, "fermata")
            );

            assertThat(action.isSelected()).isTrue();
        }
    }
}
