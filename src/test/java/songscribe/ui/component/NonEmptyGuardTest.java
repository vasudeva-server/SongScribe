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

import javax.swing.JPanel;
import javax.swing.JTextField;

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
    // Non-blank text → verify() passes, focus yields, no dialog shown
    // -------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class NonBlank {

        @Test
        void testNonBlankTextYieldsFocusWithoutDialog() {
            var field = new JTextField("Hello");
            var guard = new NonEmptyGuard(
                field, PARENT,
                Strings.ALERT_TITLE_SONG_SETTINGS,
                Strings.CONFIRM_SONG_EMPTY_TITLE
            );

            try (var optionMock = mockStatic(OptionDialogs.class)) {
                assertThat(guard.verify(field)).isTrue();
                assertThat(guard.shouldYieldFocus(field, field)).isTrue();

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
    // Blank text, no default → warning shown, focus not yielded
    // -------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class BlankNoDefault {

        @Test
        void testBlankTextWithNoDefaultShowsWarningAndKeepsFocus() {
            var field = new JTextField("");
            var guard = new NonEmptyGuard(
                field, PARENT,
                Strings.ALERT_TITLE_SONG_SETTINGS,
                Strings.CONFIRM_SONG_EMPTY_TITLE
            );

            try (var optionMock = mockStatic(OptionDialogs.class)) {
                assertThat(guard.verify(field)).isFalse();
                assertThat(guard.shouldYieldFocus(field, field)).isFalse();

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
    // Blank text, default set, user chooses "use default" → fills field, yields
    // -------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class BlankWithDefaultUserChoosesDefault {

        @Test
        void testUserChoosingDefaultFillsFieldAndYieldsFocus() {
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
            try (var optionMock = mockStatic(OptionDialogs.class)) {
                optionMock.when(
                    () -> OptionDialogs.showOptionDialog(
                        any(), any(), any(), anyInt(), anyInt(), any(), any(), any()
                    )
                ).thenReturn(USE_DEFAULT_INDEX);

                assertThat(guard.shouldYieldFocus(field, field)).isTrue();
                assertThat(field.getText()).isEqualTo(Strings.get(Strings.DOCUMENT_UNTITLED));
            }
        }
    }

    // -------------------------------------------------------------------
    // Blank text, default set, user dismisses / "continue editing" → keeps focus
    // -------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class BlankWithDefaultUserDismisses {

        @Test
        void testUserDismissingDefaultDialogKeepsFocus() {
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
            // which is not USE_DEFAULT_INDEX, so the guard should not yield focus
            // and should leave the field blank.
            assertThat(guard.shouldYieldFocus(field, field)).isFalse();
            assertThat(field.getText()).isEmpty();
        }
    }
}
