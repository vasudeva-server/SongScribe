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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import songscribe.MainFrameMockTest;
import songscribe.message.MessageCenter;
import songscribe.message.command.ToggleBeamCommand;
import songscribe.message.command.ToggleTieCommand;
import songscribe.message.notification.DocumentDidLoadNotification;
import songscribe.message.notification.MusicSelectionDidChangeNotification;
import songscribe.message.notification.SongDidChangeNotification;
import songscribe.dom.Song;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class ToggleNotationActionTest extends MainFrameMockTest {

    // REQUIRES_MULTIPLE_SELECTION: size > 1 lets updateEnabledState() return true.
    private static final int MULTIPLE_SELECTION_SIZE = 2;

    @BeforeEach
    void setUp() {
        // Satisfy REQUIRES_MULTIPLE_SELECTION so updateEnabledState() can return true,
        // putting canToggle in charge of the final enabled state.
        when(mockEnv().score().getSelectionSize()).thenReturn(MULTIPLE_SELECTION_SIZE);
    }

    // Rows 28 & 29: handleChange — enabled iff updateEnabledState() passes AND canToggle predicate is true

    @Nested
    class HandleChange {

        // Row 28: beam action
        @Test
        void testBeamActionEnabledWhenCanToggleBeamingIsTrue() {
            when(mockEnv().ctrl().canToggleBeaming()).thenReturn(true);
            var action = ToggleNotationAction.createBeamAction(mainFrame());
            action.musicSelectionDidChange(
                new MusicSelectionDidChangeNotification(mockEnv().score()));
            assertThat(action.isEnabled()).isTrue();
        }

        @Test
        void testBeamActionDisabledWhenCanToggleBeamingIsFalse() {
            when(mockEnv().ctrl().canToggleBeaming()).thenReturn(false);
            var action = ToggleNotationAction.createBeamAction(mainFrame());
            action.musicSelectionDidChange(
                new MusicSelectionDidChangeNotification(mockEnv().score()));
            assertThat(action.isEnabled()).isFalse();
        }

        // Row 29: tie action
        @Test
        void testTieActionEnabledWhenCanToggleTieIsTrue() {
            when(mockEnv().ctrl().canToggleTie()).thenReturn(true);
            var action = ToggleNotationAction.createTieAction(mainFrame());
            action.musicSelectionDidChange(
                new MusicSelectionDidChangeNotification(mockEnv().score()));
            assertThat(action.isEnabled()).isTrue();
        }

        @Test
        void testTieActionDisabledWhenCanToggleTieIsFalse() {
            when(mockEnv().ctrl().canToggleTie()).thenReturn(false);
            var action = ToggleNotationAction.createTieAction(mainFrame());
            action.musicSelectionDidChange(
                new MusicSelectionDidChangeNotification(mockEnv().score()));
            assertThat(action.isEnabled()).isFalse();
        }
    }

    // Rows 30 & 31: actionPerformed — dispatches the correct command on the message bus

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

        // Row 30: beam action dispatches ToggleBeamCommand
        @Test
        void testBeamActionPerformedPostsToggleBeamCommand() {
            var action = ToggleNotationAction.createBeamAction(mainFrame());
            action.actionPerformed(
                new ActionEvent(new JButton(), ActionEvent.ACTION_PERFORMED, "toggle-beam"));
            var captor = ArgumentCaptor.forClass(ToggleBeamCommand.class);
            messageMock.verify(() -> MessageCenter.post(captor.capture()));
            assertThat(captor.getValue()).isInstanceOf(ToggleBeamCommand.class);
        }

        // Row 31: tie action dispatches ToggleTieCommand
        @Test
        void testTieActionPerformedPostsToggleTieCommand() {
            var action = ToggleNotationAction.createTieAction(mainFrame());
            action.actionPerformed(
                new ActionEvent(new JButton(), ActionEvent.ACTION_PERFORMED, "toggle-tie"));
            var captor = ArgumentCaptor.forClass(ToggleTieCommand.class);
            messageMock.verify(() -> MessageCenter.post(captor.capture()));
            assertThat(captor.getValue()).isInstanceOf(ToggleTieCommand.class);
        }
    }

    // Row 33: handleChange is re-evaluated on songDidChange and documentDidLoad notifications

    @Nested
    class NotificationHandlers {

        // songDidChange triggers handleChange: canToggleBeaming governs the final enabled state
        @Test
        void testSongDidChangeReEvaluatesBeamingPredicate() {
            when(mockEnv().ctrl().canToggleBeaming()).thenReturn(true);
            var action = ToggleNotationAction.createBeamAction(mainFrame());
            action.setEnabled(false);

            action.songDidChange(new SongDidChangeNotification(List.of(), new Song()));

            assertThat(action.isEnabled()).isTrue();
        }

        @Test
        void testSongDidChangeDisablesBeamWhenCannotToggle() {
            when(mockEnv().ctrl().canToggleBeaming()).thenReturn(false);
            var action = ToggleNotationAction.createBeamAction(mainFrame());
            action.setEnabled(true);

            action.songDidChange(new SongDidChangeNotification(List.of(), new Song()));

            assertThat(action.isEnabled()).isFalse();
        }

        // documentDidLoad triggers handleChange: canToggleTie governs the final enabled state
        @Test
        void testDocumentDidLoadReEvaluatesTiePredicate() {
            when(mockEnv().ctrl().canToggleTie()).thenReturn(true);
            var action = ToggleNotationAction.createTieAction(mainFrame());
            action.setEnabled(false);

            action.documentDidLoad(new DocumentDidLoadNotification(new Song()));

            assertThat(action.isEnabled()).isTrue();
        }

        @Test
        void testDocumentDidLoadDisablesTieWhenCannotToggle() {
            when(mockEnv().ctrl().canToggleTie()).thenReturn(false);
            var action = ToggleNotationAction.createTieAction(mainFrame());
            action.setEnabled(true);

            action.documentDidLoad(new DocumentDidLoadNotification(new Song()));

            assertThat(action.isEnabled()).isFalse();
        }
    }
}
