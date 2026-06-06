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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import javax.swing.JOptionPane;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import songscribe.UnitTest;
import songscribe.dom.Song;
import songscribe.ui.OptionDialogs;
import songscribe.ui.action.SaveAction;

/**
 * Unit tests for {@link MainFrame}'s save-dialog guard ({@link MainFrame#showSaveDialog}) and
 * save-routing ({@link MainFrame#save}).
 *
 * <p>Tests use {@code mock(MainFrame.class)} + {@code doCallRealMethod()} to exercise
 * the real method implementations without triggering the heavyweight JFrame constructor.
 * Fields ({@link MainFrame#scoreView}, {@link MainFrame#currentFile}) are set directly
 * from this package-sibling test class.
 */
class MainFrameTest extends UnitTest {

    private MainFrame frame;

    @BeforeEach
    void setUp() {
        frame = mock(MainFrame.class);
    }

    // -----------------------------------------------------------------------
    // showSaveDialog
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class ShowSaveDialog {

        /**
         * When {@code scoreView} is null (no document open), {@code showSaveDialog} returns
         * {@code true} immediately without showing any dialog.
         */
        @Test
        void testNullScoreViewReturnsTrueImmediately() {
            frame.scoreView = null;
            doCallRealMethod().when(frame).showSaveDialog();

            assertThat(frame.showSaveDialog())
                .as("null scoreView: no document open → allow proceed")
                .isTrue();
        }

        /**
         * When the document is clean ({@code isModified() == false}), {@code showSaveDialog}
         * returns {@code true} without prompting.
         */
        @Test
        void testCleanDocReturnsTrueWithoutDialog() {
            var mockScore = mock(ScoreView.class);
            var mockSong = mock(Song.class);

            when(mockScore.getSong()).thenReturn(mockSong);
            when(mockSong.isModified()).thenReturn(false);
            frame.scoreView = mockScore;

            doCallRealMethod().when(frame).showSaveDialog();

            try (var optionDialogsMock = mockStatic(OptionDialogs.class)) {
                assertThat(frame.showSaveDialog())
                    .as("clean document → allow proceed without dialog")
                    .isTrue();

                // No dialog should have been shown
                optionDialogsMock.verify(
                    () -> OptionDialogs.showOptionDialog(
                        any(), anyString(), anyString(), anyInt(), anyInt(),
                        any(), any(), any(), any()),
                    never()
                );
            }
        }

        /**
         * On a dirty document when the user selects "Save", {@code showSaveDialog} delegates
         * to {@code SaveAction.perform()} and returns its result (true on success).
         */
        @Test
        void testSaveAnswerDelegatesToSaveActionAndReturnsTrueOnSuccess() {
            var mockScore = mock(ScoreView.class);
            var mockSong = mock(Song.class);

            when(mockScore.getSong()).thenReturn(mockSong);
            when(mockSong.isModified()).thenReturn(true);
            frame.scoreView = mockScore;

            doCallRealMethod().when(frame).showSaveDialog();

            // saveIdx == 0, so return 0 from showOptionDialog to choose Save
            final int saveIdx = 0;

            try (var optionDialogsMock = mockStatic(OptionDialogs.class);
                 var saveActionMock = mockStatic(SaveAction.class)) {

                optionDialogsMock
                    .when(() -> OptionDialogs.showOptionDialog(
                        any(), anyString(), anyString(), anyInt(), anyInt(),
                        isNull(), any(), any(), any()))
                    .thenReturn(saveIdx);

                var mockSaveAction = mock(SaveAction.class);
                saveActionMock.when(() -> SaveAction.createAction(any())).thenReturn(mockSaveAction);
                when(mockSaveAction.perform(any())).thenReturn(true);

                assertThat(frame.showSaveDialog())
                    .as("Save answer → perform() returns true → showSaveDialog returns true")
                    .isTrue();

                verify(mockSaveAction).perform(frame);
            }
        }

        /**
         * On a dirty document when the user selects "Don't Save", {@code showSaveDialog} returns
         * {@code true} without writing anything.
         */
        @Test
        void testDontSaveAnswerReturnsTrueWithoutWriting() {
            var mockScore = mock(ScoreView.class);
            var mockSong = mock(Song.class);

            when(mockScore.getSong()).thenReturn(mockSong);
            when(mockSong.isModified()).thenReturn(true);
            frame.scoreView = mockScore;

            doCallRealMethod().when(frame).showSaveDialog();

            // dontSaveIdx == 1
            final int dontSaveIdx = 1;

            try (var optionDialogsMock = mockStatic(OptionDialogs.class);
                 var saveActionMock = mockStatic(SaveAction.class)) {

                optionDialogsMock
                    .when(() -> OptionDialogs.showOptionDialog(
                        any(), anyString(), anyString(), anyInt(), anyInt(),
                        isNull(), any(), any(), any()))
                    .thenReturn(dontSaveIdx);

                assertThat(frame.showSaveDialog())
                    .as("Don't Save answer → returns true without invoking SaveAction")
                    .isTrue();

                // SaveAction must NOT have been invoked
                saveActionMock.verify(
                    () -> SaveAction.createAction(any()),
                    never()
                );
            }
        }

        /**
         * When the user dismisses the dialog ({@code CLOSED_OPTION}), neither Save nor
         * Don't Save index was chosen, so {@code showSaveDialog} returns {@code false}.
         */
        @Test
        void testClosedOptionReturnsFalse() {
            var mockScore = mock(ScoreView.class);
            var mockSong = mock(Song.class);

            when(mockScore.getSong()).thenReturn(mockSong);
            when(mockSong.isModified()).thenReturn(true);
            frame.scoreView = mockScore;

            doCallRealMethod().when(frame).showSaveDialog();

            try (var optionDialogsMock = mockStatic(OptionDialogs.class)) {
                optionDialogsMock
                    .when(() -> OptionDialogs.showOptionDialog(
                        any(), anyString(), anyString(), anyInt(), anyInt(),
                        isNull(), any(), any(), any()))
                    .thenReturn(JOptionPane.CLOSED_OPTION);

                assertThat(frame.showSaveDialog())
                    .as("dialog dismissed (CLOSED_OPTION) → cancel → returns false")
                    .isFalse();
            }
        }
    }

    // -----------------------------------------------------------------------
    // save
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class Save {

        /**
         * When {@code currentFile} is null, {@link MainFrame#save} delegates to
         * {@code saveAsNewFile()} — verified by observing that a null {@code scoreView}
         * triggers {@code saveAsNewFile}'s first guard (returns false) rather than
         * {@code saveCurrentFile}'s guard (which requires a non-null currentFile).
         */
        @Test
        void testNullCurrentFileDelegatesToSaveAsNewFile() {
            frame.currentFile = null;
            frame.scoreView = null;

            doCallRealMethod().when(frame).save();

            // saveAsNewFile() returns false when scoreView==null (its first guard).
            assertThat(frame.save())
                .as("currentFile==null → delegates to saveAsNewFile → false (scoreView guard)")
                .isFalse();
        }

        /**
         * When {@code currentFile} is set, {@link MainFrame#save} delegates to
         * {@code saveCurrentFile()} — verified by observing that a null {@code scoreView}
         * triggers {@code saveCurrentFile}'s null guard (returns false).
         */
        @Test
        void testSetCurrentFileDelegatesToSaveCurrentFile() {
            frame.currentFile = mock(java.io.File.class);
            frame.scoreView = null;

            doCallRealMethod().when(frame).save();

            // saveCurrentFile() returns false when scoreView==null (its first guard).
            assertThat(frame.save())
                .as("currentFile!=null → delegates to saveCurrentFile → false (scoreView guard)")
                .isFalse();
        }
    }

}
