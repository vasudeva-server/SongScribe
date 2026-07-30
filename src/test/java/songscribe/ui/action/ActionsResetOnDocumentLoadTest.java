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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import songscribe.RequiresDisplay;
import songscribe.UnitTest;
import songscribe.message.MessageCenter;
import songscribe.message.notification.DocumentDidLoadNotification;
import songscribe.dom.Song;
import songscribe.ui.Mode;
import songscribe.ui.ViewScale;
import songscribe.ui.component.MainFrame;
import songscribe.ui.component.ScoreView;
import songscribe.ui.selection.SelectionCoordinator;

/**
 * Verifies that loading a document resets all toolbar action state via
 * {@link Actions.ResetHandler}, which subscribes to
 * {@link DocumentDidLoadNotification} and invokes the private
 * {@code resetToDefaults()}.
 *
 * <p>{@code resetToDefaults()} calls {@code MODE_ACTION_GROUP.select(...)} and
 * {@code DURATION_ACTION_GROUP.select(...)}, which fire {@code perform()} on
 * the selected action; that path ends up in {@code UIAction.requireScoreView()}.
 * {@code UIAction} stores the injected {@code MainFrame} in a final field at
 * construction, so this test initializes {@code Actions} itself against the real
 * {@code MainFrame} singleton and wires that same singleton to a mock
 * {@code ScoreView} — making the setup self-contained rather than depending on
 * whichever frame some earlier test happened to load {@code Actions} with.
 */
@RequiresDisplay
class ActionsResetOnDocumentLoadTest extends UnitTest {

    @BeforeEach
    void setUp() {
        var mockScore = mock(ScoreView.class);
        var mockCoordinator = mock(SelectionCoordinator.class);
        when(mockScore.getSelectionCoordinator()).thenReturn(mockCoordinator);
        when(mockScore.getMode()).thenReturn(Mode.EDIT);
        when(mockCoordinator.getSelection()).thenReturn(null);
        // This test wires the mock onto the real MainFrame singleton (see class Javadoc),
        // which leaks past this test's lifetime; stub getViewScale() so later tests that
        // read MainFrame.getInstance().getScoreView().getViewScale() (ZoomController,
        // ScoreComponent) do not NPE on the leaked mock.
        when(mockScore.getViewScale()).thenReturn(new ViewScale());

        var mainFrame = MainFrame.getInstance();
        mainFrame.setScoreView(mockScore);
        // Initialize Actions against the real singleton frame wired above so the action
        // constants exist regardless of whether an earlier test class loaded Actions.
        Actions.initialize(mainFrame);
    }

    @Test
    void testDocumentDidLoadResetsAllActionStateToDefaults() {
        // Put every group and standalone toggle into a non-default state.
        Actions.MODE_ACTION_GROUP.setSelected(Actions.SELECT_MODE_ACTION, true);
        Actions.DURATION_ACTION_GROUP.setSelected(Actions.HALF_NOTE_ACTION, true);
        Actions.ACCIDENTAL_ACTION_GROUP.setSelected(Actions.SHARP_ACTION, true);
        Actions.DOT_ACTION_GROUP.setSelected(Actions.DOT_ACTION, true);
        Actions.STACCATO_ACTION.setSelected(true);
        Actions.NON_DURATION_ACTION_GROUP.setSelected(Actions.BREATH_MARK_ACTION, true);
        Actions.DYNAMIC_MARKING_ACTION_GROUP.setSelected(Actions.DYNAMIC_F_ACTION, true);
        Actions.FERMATA_ACTION.setSelected(true);
        Actions.ACCENT_ACTION.setSelected(true);
        Actions.REST_ACTION.setSelected(true);
        Actions.ACCIDENTAL_IN_PARENS_ACTION.setSelected(true);

        MessageCenter.post(new DocumentDidLoadNotification(new Song()));

        assertAll(
            () -> assertThat(Actions.MODE_ACTION_GROUP.getSelected())
                .as("mode reset to EDIT").isSameAs(Actions.EDIT_MODE_ACTION),
            () -> assertThat(Actions.DURATION_ACTION_GROUP.getSelected())
                .as("duration reset to QUARTER_NOTE").isSameAs(Actions.QUARTER_NOTE_ACTION),
            () -> assertThat(Actions.ACCIDENTAL_ACTION_GROUP.getSelected())
                .as("accidental group cleared").isNull(),
            () -> assertThat(Actions.STACCATO_ACTION.isSelected())
                .as("staccato off").isFalse(),
            () -> assertThat(Actions.DOT_ACTION_GROUP.getSelected())
                .as("dot group cleared").isNull(),
            () -> assertThat(Actions.NON_DURATION_ACTION_GROUP.getSelected())
                .as("non-duration group cleared").isNull(),
            () -> assertThat(Actions.DYNAMIC_MARKING_ACTION_GROUP.getSelected())
                .as("dynamic marking group cleared").isNull(),
            () -> assertThat(Actions.FERMATA_ACTION.isSelected())
                .as("fermata off").isFalse(),
            () -> assertThat(Actions.ACCENT_ACTION.isSelected())
                .as("accent off").isFalse(),
            () -> assertThat(Actions.REST_ACTION.isSelected())
                .as("rest off").isFalse(),
            () -> assertThat(Actions.ACCIDENTAL_IN_PARENS_ACTION.isSelected())
                .as("accidental-in-parens off").isFalse()
        );
    }
}
