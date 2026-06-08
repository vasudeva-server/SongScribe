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
