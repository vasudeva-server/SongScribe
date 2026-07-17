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

import org.junit.jupiter.api.Test;

import songscribe.MainFrameMockTest;

import static org.assertj.core.api.Assertions.assertThat;

class StickyUIActionTest extends MainFrameMockTest {

    /** Minimal concrete StickyUIAction for testing doActionPerformed. */
    private StickyUIAction makeAction() {
        return new StickyUIAction(mainFrame(), "Sticky", null, 0, "sticky-cmd", "tooltip", 0, 0) {
            @Override
            protected void performAction(ActionEvent e) {
                doActionPerformed(e);
            }
        };
    }

    private ActionEvent actionEventWithSource(Object source) {
        return new ActionEvent(source, ActionEvent.ACTION_PERFORMED, "sticky-cmd");
    }

    // Row 45: source is JRootPane + already selected → returns false (no toggle)

    @Test
    void testDoActionPerformedReturnsFalseWhenSourceIsRootPaneAndAlreadySelected() {
        var action = makeAction();
        action.setSelected(true);

        var rootPane = new JRootPane();
        var e = actionEventWithSource(rootPane);
        var result = action.doActionPerformed(e);

        assertThat(result).isFalse();
        assertThat(action.isSelected()).isTrue();
    }

    // Row 46: source is JRootPane + not selected → toggles to true, returns true

    @Test
    void testDoActionPerformedReturnsTrueAndTogglesWhenSourceIsRootPaneAndNotSelected() {
        var action = makeAction();
        action.setSelected(false);

        var rootPane = new JRootPane();
        var e = actionEventWithSource(rootPane);
        var result = action.doActionPerformed(e);

        assertThat(result).isTrue();
        assertThat(action.isSelected()).isTrue();
    }

    // Row 47: source is NOT JRootPane → always calls toggleOnKeyboardShortcut (no-op for non-root), returns true

    @Test
    void testDoActionPerformedReturnsTrueWhenSourceIsNotJRootPane() {
        var action = makeAction();
        action.setSelected(true);

        var button = new JButton();
        var e = actionEventWithSource(button);
        var result = action.doActionPerformed(e);

        assertThat(result).isTrue();
    }
}
