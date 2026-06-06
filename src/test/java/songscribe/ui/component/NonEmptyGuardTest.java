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

package songscribe.ui.component;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import java.awt.Component;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTextField;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import songscribe.Strings;
import songscribe.UnitTest;
import songscribe.ui.OptionDialogs;

class NonEmptyGuardTest extends UnitTest {

    // Index of the "use default" button in the options array passed to showOptionDialog.
    // The array is { continueEditing, useDefault }, so useDefault is at index 1.
    private static final int USE_DEFAULT_INDEX = 1;

    // Shared parent component for dialog positioning — a no-op panel used in tests
    // to satisfy the @NonNull parent parameter without any Swing frame overhead.
    private static final JPanel PARENT = new JPanel();

    // -------------------------------------------------------------------
    // Helper — fire a FOCUS_LOST event directly on the field's focus
    // listeners (bypasses Swing EDT focus transfer).
    // -------------------------------------------------------------------

    private static void fireFocusLost(
        JTextField field,
        boolean temporary,
        @Nullable Component opposite
    ) {
        var event = new FocusEvent(field, FocusEvent.FOCUS_LOST, temporary, opposite);

        for (FocusListener listener : field.getFocusListeners()) {
            listener.focusLost(event);
        }
    }

    // -------------------------------------------------------------------
    // Row 61: validate() — non-blank text returns true, no dialog shown
    // -------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class ValidateNonBlank {

        @Test
        void testNonBlankTextReturnsTrue() {
            var field = new JTextField("Hello");
            var guard = new NonEmptyGuard(
                field, PARENT,
                Strings.ALERT_TITLE_SONG_SETTINGS,
                Strings.CONFIRM_SONG_EMPTY_TITLE
            );

            try (var optionMock = mockStatic(OptionDialogs.class)) {
                assertThat(guard.validate()).isTrue();

                // No dialog should have been shown
                optionMock.verify(
                    () -> OptionDialogs.showWarningMessage(any(), any(), any()),
                    never()
                );
                optionMock.verify(
                    () -> OptionDialogs.showOptionDialog(
                        any(), any(), any(), anyInt(), anyInt(), any(), any(), any()
                    ),
                    never()
                );
            }
        }
    }

    // -------------------------------------------------------------------
    // Row 62: validate() — blank text, no defaultValueKey → showWarningAndRefocus, returns false
    // -------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class ValidateBlankNoDefault {

        @Test
        void testBlankTextWithNoDefaultShowsWarningAndReturnsFalse() {
            var field = new JTextField("");
            var guard = new NonEmptyGuard(
                field, PARENT,
                Strings.ALERT_TITLE_SONG_SETTINGS,
                Strings.CONFIRM_SONG_EMPTY_TITLE
            );

            try (var optionMock = mockStatic(OptionDialogs.class)) {
                assertThat(guard.validate()).isFalse();

                optionMock.verify(
                    () -> OptionDialogs.showWarningMessage(
                        any(),
                        any(),
                        any()
                    )
                );
            }
        }
    }

    // -------------------------------------------------------------------
    // Row 63: validate() — blank text, defaultValueKey set, user chooses
    //         "use default" → fills field, returns true
    // -------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class ValidateBlankWithDefaultUserChoosesDefault {

        @Test
        void testUserChoosingDefaultFillsFieldAndReturnsTrue() {
            var field = new JTextField("");
            var guard = new NonEmptyGuard(
                field, PARENT,
                Strings.ALERT_TITLE_SONG_SETTINGS,
                Strings.CONFIRM_SONG_EMPTY_TITLE,
                Strings.DOCUMENT_UNTITLED,
                Strings.DIALOG_SONG_SETTINGS_USE_UNTITLED,
                Strings.DIALOG_SONG_SETTINGS_CONTINUE_EDITING
            );

            // Simulate the user pressing "use default" (index 1 in the options array)
            try (MockedStatic<OptionDialogs> optionMock = mockStatic(OptionDialogs.class)) {
                optionMock.when(
                    () -> OptionDialogs.showOptionDialog(
                        any(), any(), any(), anyInt(), anyInt(), any(), any(), any()
                    )
                ).thenReturn(USE_DEFAULT_INDEX);

                assertThat(guard.validate()).isTrue();
                assertThat(field.getText()).isEqualTo(Strings.get(Strings.DOCUMENT_UNTITLED));
            }
        }
    }

    // -------------------------------------------------------------------
    // Row 64: validate() — blank text, defaultValueKey set, user dismisses /
    //         chooses "continue editing" → refocuses, returns false
    // -------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class ValidateBlankWithDefaultUserDismisses {

        @Test
        void testUserDismissingDefaultDialogReturnsFalse() {
            var field = new JTextField("");
            var guard = new NonEmptyGuard(
                field, PARENT,
                Strings.ALERT_TITLE_SONG_SETTINGS,
                Strings.CONFIRM_SONG_EMPTY_TITLE,
                Strings.DOCUMENT_UNTITLED,
                Strings.DIALOG_SONG_SETTINGS_USE_UNTITLED,
                Strings.DIALOG_SONG_SETTINGS_CONTINUE_EDITING
            );

            // OptionDialogs is suppressed in UnitTest → returns CLOSED_OPTION (-1),
            // which is not USE_DEFAULT_INDEX, so the guard should return false
            // and leave the field blank.
            assertThat(guard.validate()).isFalse();
            assertThat(field.getText()).isEmpty();
        }
    }

    // -------------------------------------------------------------------
    // Row 65: install() — temporary focus-lost event → guard skipped
    // -------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class InstallTemporaryFocusLost {

        @Test
        void testTemporaryFocusLostSkipsGuard() {
            var field = new JTextField("");
            var guard = new NonEmptyGuard(
                field, PARENT,
                Strings.ALERT_TITLE_SONG_SETTINGS,
                Strings.CONFIRM_SONG_EMPTY_TITLE
            );

            try (var optionMock = mockStatic(OptionDialogs.class)) {
                // A temporary focus-lost (e.g., window losing focus) must not trigger validation
                fireFocusLost(field, true, null);

                optionMock.verify(
                    () -> OptionDialogs.showWarningMessage(any(), any(), any()),
                    never()
                );
            }
        }
    }

    // -------------------------------------------------------------------
    // Row 66: install() — focus-lost to exempt component → guard skipped
    // -------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class InstallFocusLostToExemptComponent {

        @Test
        void testFocusLostToExemptComponentSkipsGuard() {
            var field = new JTextField("");
            var guard = new NonEmptyGuard(
                field, PARENT,
                Strings.ALERT_TITLE_SONG_SETTINGS,
                Strings.CONFIRM_SONG_EMPTY_TITLE
            );

            var exemptButton = new JButton("OK");
            guard.addExemptComponent(exemptButton);

            try (var optionMock = mockStatic(OptionDialogs.class)) {
                // Focus moving to an exempt component (e.g., OK button) must not trigger validation
                fireFocusLost(field, false, exemptButton);

                optionMock.verify(
                    () -> OptionDialogs.showWarningMessage(any(), any(), any()),
                    never()
                );
            }
        }

        @Test
        void testFocusLostToNonExemptComponentTriggersGuard() {
            var field = new JTextField("");
            var guard = new NonEmptyGuard(
                field, PARENT,
                Strings.ALERT_TITLE_SONG_SETTINGS,
                Strings.CONFIRM_SONG_EMPTY_TITLE
            );

            var exemptButton = new JButton("OK");
            guard.addExemptComponent(exemptButton);
            var otherButton = new JButton("Other");

            try (var optionMock = mockStatic(OptionDialogs.class)) {
                // Focus moving to a non-exempt component must trigger validation
                fireFocusLost(field, false, otherButton);

                optionMock.verify(
                    () -> OptionDialogs.showWarningMessage(any(), any(), any())
                );
            }
        }
    }
}
