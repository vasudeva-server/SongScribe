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

package songscribe.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import module java.desktop;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.jspecify.annotations.Nullable;

import org.assertj.swing.core.GenericTypeMatcher;
import org.assertj.swing.finder.JOptionPaneFinder;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.Strings;
import songscribe.ui.OptionDialogs;
import songscribe.ui.component.MainFrame;

/**
 * E2E tests for dialog behavior: return values and closed-option mapping.
 */
class DialogsTest extends E2ETest {

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class ConfirmDialog {

        @Test
        void testClickYesReturnsYesOption() throws InterruptedException {
            var result = new AtomicInteger();
            var latch = new CountDownLatch(1);

            SwingUtilities.invokeLater(() -> {
                result.set(OptionDialogs.showConfirmDialog(
                    MainFrame.getInstance(), Strings.CONFIRM_TITLE_SAVE_CHANGES, Strings.CONFIRM_SAVE_MODIFIED,
                    JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE
                ));
                latch.countDown();
            });

            JOptionPaneFinder.findOptionPane().using(robot).yesButton().click();
            latch.await();

            assertThat(result.get()).isEqualTo(JOptionPane.YES_OPTION);
        }

        @Test
        void testCloseWithYesNoOptionReturnsNoOption() throws InterruptedException {
            var result = new AtomicInteger();
            var latch = new CountDownLatch(1);

            SwingUtilities.invokeLater(() -> {
                result.set(OptionDialogs.showConfirmDialog(
                    MainFrame.getInstance(), Strings.CONFIRM_TITLE_SAVE_CHANGES, Strings.CONFIRM_SAVE_MODIFIED,
                    JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE
                ));
                latch.countDown();
            });

            JOptionPaneFinder.findOptionPane().using(robot).pressAndReleaseKeys(KeyEvent.VK_ESCAPE);
            latch.await();

            assertThat(result.get()).isEqualTo(JOptionPane.NO_OPTION);
        }

        @Test
        void testCloseWithYesNoCancelOptionReturnsCancelOption() throws InterruptedException {
            var result = new AtomicInteger();
            var latch = new CountDownLatch(1);

            SwingUtilities.invokeLater(() -> {
                result.set(OptionDialogs.showConfirmDialog(
                    MainFrame.getInstance(), Strings.CONFIRM_TITLE_SAVE_CHANGES, Strings.CONFIRM_SAVE_MODIFIED,
                    JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE
                ));
                latch.countDown();
            });

            JOptionPaneFinder.findOptionPane().using(robot).pressAndReleaseKeys(KeyEvent.VK_ESCAPE);
            latch.await();

            assertThat(result.get()).isEqualTo(JOptionPane.CANCEL_OPTION);
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class InputDialog {

        @Test
        void testReturnsTypedText() throws InterruptedException {
            var resultRef = new AtomicReference<@Nullable String>();
            var latch = new CountDownLatch(1);

            SwingUtilities.invokeLater(() -> {
                resultRef.set(OptionDialogs.showInputDialog(MainFrame.getInstance(), Strings.INPUT_TITLE_NUMBER_SONGS, Strings.INPUT_CONVERTER_ENTER_NUMBER));
                latch.countDown();
            });

            var optionPane = JOptionPaneFinder.findOptionPane().using(robot);
            optionPane.textBox().setText("hello");
            optionPane.okButton().click();
            latch.await();

            assertThat(resultRef.get()).isEqualTo("hello");
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class OptionDialog {

        @Test
        void testReturnsIndexOfClickedOption() throws InterruptedException {
            var result = new AtomicInteger();
            var latch = new CountDownLatch(1);
            var options = new String[]{"Option A", "Option B"};

            SwingUtilities.invokeLater(() -> {
                result.set(OptionDialogs.showOptionDialog(
                    MainFrame.getInstance(),
                    Strings.CONFIRM_TITLE_SAVE_CHANGES,
                    Strings.CONFIRM_TITLE_FIRST_SECOND_ENDING,
                    JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                    null, options, options[0]
                ));
                latch.countDown();
            });

            JOptionPaneFinder.findOptionPane().using(robot).button(new GenericTypeMatcher<>(JButton.class) {
                @Override
                protected boolean isMatching(JButton button) {
                    return button.getText().equals("Option B");
                }
            }).click();
            latch.await();

            assertThat(result.get()).isEqualTo(1);
        }
    }
}
