/*
 * SongScribe song notation program
 * Copyright (C) Sri Chinmoy Centres International
 *
 * This file is part of SongScribe.
 *
 * SongScribe is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * SongScribe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package songscribe.ui.action;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.awt.event.ActionEvent;

import javax.swing.JButton;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.UnitTest;
import songscribe.dom.Song;
import songscribe.dom.Tempo;
import songscribe.message.Message;
import songscribe.message.MessageCenter;
import songscribe.message.notification.SongDidChangeNotification;
import songscribe.ui.component.MainFrame;
import songscribe.ui.component.ScoreView;
import songscribe.undo.UndoController;

/**
 * Verifies the op-name threading contract added in Phase 1/2:
 *
 * <ul>
 *   <li>{@link Song#beginModification(String)} captures the batch op-name only at the
 *       outermost bracket, resolving {@code explicit != null ? explicit : pending}, and
 *       ships it via {@link SongDidChangeNotification#getOpName()} (the capture matrix).</li>
 *   <li>{@link UndoController#withPendingOpName} restores the prior {@code pendingOpName}
 *       whether the body returns or throws, so no dispatch leaks a stale name onto the next
 *       bracket (FM3). {@link UIAction#actionPerformed(ActionEvent)} is one of its three
 *       callers, and is exercised end to end here.</li>
 * </ul>
 */
class OpNameThreadingTest extends UnitTest {

    private static final String EXPLICIT_KEY = Strings.ACTION_EDIT_OP_ADD_NOTE;
    private static final String PENDING_KEY = Strings.ACTION_EDIT_OP_DELETE_NOTE;

    // ── Capture matrix: which name wins at the depth 0→1 transition ──

    @Nested
    class CaptureMatrix {

        // A song already carries Tempo's defaults, so a value-equal setTempo() call now
        // early-returns and records no mutation. Every test here needs a real tempo edit,
        // so it uses a tempo that actually differs from the default.
        private static final int NON_DEFAULT_BPM = Tempo.DEFAULT_BPM * 2;

        private Song song;
        private MockedStatic<MessageCenter> messageCenterMock;

        @BeforeEach
        void setUp() {
            // Construct the Song before mocking MessageCenter so its constructor's
            // bus subscription goes to the real bus and does not count as a post.
            song = new Song();
            messageCenterMock = mockStatic(MessageCenter.class);
        }

        @AfterEach
        void tearDown() {
            messageCenterMock.close();
        }

        /**
         * Runs {@code edit} (which must accumulate at least one mutation) and returns the
         * single {@link SongDidChangeNotification} posted when its outermost bracket closes.
         */
        private SongDidChangeNotification capturePostedBatch(Runnable edit) {
            edit.run();

            var captor = ArgumentCaptor.forClass(Message.class);
            messageCenterMock.verify(() -> MessageCenter.post(captor.capture()));

            assertThat(captor.getValue()).isInstanceOf(SongDidChangeNotification.class);
            return (SongDidChangeNotification) captor.getValue();
        }

        @Test
        void testExplicitLabelOnlyIsCaptured() {
            var notification = capturePostedBatch(
                () -> song.withModification(Strings.get(EXPLICIT_KEY), () -> song.setTempo(new Tempo(NON_DEFAULT_BPM, Tempo.DEFAULT_TYPE, Tempo.DEFAULT_DESCRIPTION, Tempo.DEFAULT_SHOW_TEMPO))));

            assertThat(notification.getOpName()).isEqualTo(Strings.get(EXPLICIT_KEY));
            assertThat(notification.getMutations()).isNotEmpty();
        }

        @Test
        void testPendingNameOnlyIsCaptured() {
            UndoController.setPendingOpName(Strings.get(PENDING_KEY));

            var notification = capturePostedBatch(
                () -> song.withModification(() -> song.setTempo(new Tempo(NON_DEFAULT_BPM, Tempo.DEFAULT_TYPE, Tempo.DEFAULT_DESCRIPTION, Tempo.DEFAULT_SHOW_TEMPO))));

            assertThat(notification.getOpName()).isEqualTo(Strings.get(PENDING_KEY));
        }

        @Test
        void testExplicitLabelWinsOverPendingName() {
            UndoController.setPendingOpName(Strings.get(PENDING_KEY));

            var notification = capturePostedBatch(
                () -> song.withModification(Strings.get(EXPLICIT_KEY), () -> song.setTempo(new Tempo(NON_DEFAULT_BPM, Tempo.DEFAULT_TYPE, Tempo.DEFAULT_DESCRIPTION, Tempo.DEFAULT_SHOW_TEMPO))));

            assertThat(notification.getOpName()).isEqualTo(Strings.get(EXPLICIT_KEY));
        }

        @Test
        void testNeitherNameYieldsNull() {
            var notification = capturePostedBatch(
                () -> song.withModification(() -> song.setTempo(new Tempo(NON_DEFAULT_BPM, Tempo.DEFAULT_TYPE, Tempo.DEFAULT_DESCRIPTION, Tempo.DEFAULT_SHOW_TEMPO))));

            assertThat(notification.getOpName()).isNull();
        }

        @Test
        void testOuterBracketLabelWinsOverNestedInnerLabel() {
            // The op-name is captured only at the depth 0→1 transition, so a label
            // supplied by a nested bracket must not override the outer one.
            var notification = capturePostedBatch(
                () -> song.withModification(
                    Strings.get(EXPLICIT_KEY),
                    () -> song.withModification(
                        Strings.get(PENDING_KEY),
                        () -> song.setTempo(new Tempo(NON_DEFAULT_BPM, Tempo.DEFAULT_TYPE, Tempo.DEFAULT_DESCRIPTION, Tempo.DEFAULT_SHOW_TEMPO)))));

            assertThat(notification.getOpName()).isEqualTo(Strings.get(EXPLICIT_KEY));
        }
    }

    // ── FM3: the shared bracket restores pendingOpName, however the body ends ──

    /**
     * The bracket every op-name-setting dispatch runs through — the {@code UIAction}
     * template, paste placement and the last-insertion keys. Tested here rather than
     * through one of them, so all three are covered by the same two cases.
     *
     * <p>The prior name is non-null in both, which is the case a {@code finally} that
     * <em>cleared</em> the slot instead of restoring it would still pass: nesting is the
     * whole reason the bracket saves a value rather than setting {@code null} on the way out.
     */
    @Nested
    class WithPendingOpName {

        @BeforeEach
        void setPriorName() {
            UndoController.setPendingOpName(Strings.get(PENDING_KEY));
        }

        @AfterEach
        void clearPendingName() {
            UndoController.setPendingOpName(null);
        }

        @Test
        void testTheNameIsSetForTheBodyAndTheOuterNameRestoredAfterIt() {
            var observed = UndoController.withPendingOpNameResult(
                Strings.get(EXPLICIT_KEY), UndoController::getPendingOpName);

            assertThat(observed)
                .as("a bracket opening inside the body captures the name the body was given")
                .isEqualTo(Strings.get(EXPLICIT_KEY));
            assertThat(UndoController.getPendingOpName())
                .as("the enclosing dispatch's name is handed back, not cleared")
                .isEqualTo(Strings.get(PENDING_KEY));
        }

        @Test
        void testAThrowingBodyStillRestoresTheOuterName() {
            assertThatThrownBy(() -> UndoController.withPendingOpName(
                Strings.get(EXPLICIT_KEY),
                () -> {
                    throw new IllegalStateException("boom");
                }))
                .isInstanceOf(IllegalStateException.class);

            assertThat(UndoController.getPendingOpName())
                .as("a thrown body must not leave its name for the next edit to adopt")
                .isEqualTo(Strings.get(PENDING_KEY));
        }
    }

    // ── FM3: the UIAction template restores pendingOpName on exception ──

    @Nested
    class FinallyRestore {

        private MainFrame mockFrame;
        private MockedStatic<MainFrame> mainFrameMock;

        @BeforeEach
        void setUp() {
            // The op-name slot lives on UndoController now, so the template never
            // dereferences the song; a mocked, initialized ScoreView is enough.
            var mockScore = mock(ScoreView.class);
            when(mockScore.isInitialized()).thenReturn(true);
            mockFrame = mock(MainFrame.class);
            when(mockFrame.getScoreView()).thenReturn(mockScore);
            mainFrameMock = mockStatic(MainFrame.class);
            mainFrameMock.when(MainFrame::getInstance).thenReturn(mockFrame);
        }

        @AfterEach
        void tearDown() {
            mainFrameMock.close();
        }

        @Test
        void testThrowingActionRestoresPriorPendingName() {
            // The action declares a name, so the template sets pendingOpName to it around
            // performAction. When performAction throws, the finally must restore the prior
            // value (null here) so the next bracket captures null, not the stale name.
            var throwingAction = new UIAction(mockFrame, "throwing", null) {
                @Override
                public @Nullable String getUndoOpName() {
                    return Strings.get(Strings.ACTION_EDIT_OP_ADD_NOTE);
                }

                @Override
                protected void performAction(ActionEvent e) {
                    throw new IllegalStateException("boom");
                }
            };

            var event = new ActionEvent(new JButton(), ActionEvent.ACTION_PERFORMED, "throwing");

            assertThatThrownBy(() -> throwingAction.actionPerformed(event))
                .isInstanceOf(IllegalStateException.class);

            assertThat(UndoController.getPendingOpName())
                .as("pendingOpName restored to prior null after the action threw")
                .isNull();
        }
    }
}
