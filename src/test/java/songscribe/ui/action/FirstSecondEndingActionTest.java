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
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import songscribe.MainFrameMockTest;
import songscribe.dom.EndingValidationResult;
import songscribe.message.MessageCenter;
import songscribe.message.command.FirstSecondEndingCommand;

// Rows 40–42: validate(), getCachedResult(), and actionPerformed() for FirstSecondEndingAction
class FirstSecondEndingActionTest extends MainFrameMockTest {

    // Row 41: initial contract — getCachedResult() is null before validate() is called
    @Test
    void testGetCachedResultIsNullBeforeValidate() {
        var action = new FirstSecondEndingAction(mainFrame());
        // The @Nullable annotation on getCachedResult() promises null before first validate call.
        // A mutant removing the field initializer or returning a non-null default would fail this.
        assertThat(action.getCachedResult()).isNull();
    }

    // Row 40: validate() binds cachedResult and delegates the enabled state to result.isValid()
    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class Validate {

        @Test
        void testValidResultEnablesActionAndSetsCachedResult() {
            var validResult = EndingValidationResult.valid(
                EndingValidationResult.PrecedingAction.NONE, 0, 1
            );
            when(mockEnv().ctrl().canMakeFirstSecondEnding()).thenReturn(validResult);
            var action = new FirstSecondEndingAction(mainFrame());

            action.validate(mockEnv().ctrl());

            assertThat(action.isEnabled()).isTrue();
            // cachedResult must be the exact object returned by canMakeFirstSecondEnding()
            assertThat(action.getCachedResult()).isSameAs(validResult);
        }

        @Test
        void testInvalidResultDisablesAction() {
            when(mockEnv().ctrl().canMakeFirstSecondEnding())
                .thenReturn(EndingValidationResult.invalid());
            var action = new FirstSecondEndingAction(mainFrame());

            action.validate(mockEnv().ctrl());

            assertThat(action.isEnabled()).isFalse();
        }
    }

    // Row 42: actionPerformed() posts FirstSecondEndingCommand on the message bus.
    // The existing handler test (ScoreViewControllerCommandHandlerTest) injects cachedResult via
    // reflection and calls the handler directly — it never fires actionPerformed() itself.
    // This test exercises the action's own command-posting path.
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
        void testActionPerformedPostsFirstSecondEndingCommand() {
            var action = new FirstSecondEndingAction(mainFrame());
            var e = new ActionEvent(new JButton(), ActionEvent.ACTION_PERFORMED, "make-ending");

            action.actionPerformed(e);

            // A mutant that removes the MessageCenter.post call would fail this verification.
            messageMock.verify(
                () -> MessageCenter.post(any(FirstSecondEndingCommand.class))
            );
        }
    }
}
