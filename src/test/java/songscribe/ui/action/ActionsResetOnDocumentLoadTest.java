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

import songscribe.UnitTest;
import songscribe.message.MessageCenter;
import songscribe.message.notification.DocumentDidLoadNotification;
import songscribe.music.Composition;
import songscribe.ui.Mode;
import songscribe.ui.component.MainFrame;
import songscribe.ui.component.Score;
import songscribe.ui.selection.SelectionCoordinator;

/**
 * Verifies that loading a document resets all toolbar action state via
 * {@link Actions.ResetHandler}, which subscribes to
 * {@link DocumentDidLoadNotification} and invokes the private
 * {@code resetToDefaults()}.
 *
 * <p>{@code resetToDefaults()} calls {@code MODE_ACTION_GROUP.select(...)} and
 * {@code DURATION_ACTION_GROUP.select(...)}, which fire {@code perform()} on
 * the selected action; that path ends up in {@code UIAction.requireScore()}.
 * {@code UIAction} caches {@code MainFrame.getInstance()} in a final field at
 * construction, so whichever frame was in scope the first time {@code Actions}
 * was loaded is the frame these calls go through. Rather than fight that
 * cache, this test wires the real {@code MainFrame} singleton to a mock
 * {@code Score} — if some earlier test cached a Mockito mock frame, that mock
 * already has {@code requireScore} stubbed by {@link MockEnvHelper}; if the
 * cached frame is the real singleton, the explicit {@code setScore} call here
 * supplies the score it needs.
 */
class ActionsResetOnDocumentLoadTest extends UnitTest {

    @BeforeEach
    void setUp() {
        var mockScore = mock(Score.class);
        var mockCoordinator = mock(SelectionCoordinator.class);
        when(mockScore.getSelectionCoordinator()).thenReturn(mockCoordinator);
        when(mockScore.getMode()).thenReturn(Mode.EDIT);
        when(mockCoordinator.getSelection()).thenReturn(null);

        MainFrame.getInstance().setScore(mockScore);
    }

    @Test
    void testDocumentDidLoadResetsAllActionStateToDefaults() {
        // Put every group and standalone toggle into a non-default state.
        Actions.MODE_ACTION_GROUP.setSelected(Actions.SELECT_MODE_ACTION, true);
        Actions.DURATION_ACTION_GROUP.setSelected(Actions.HALF_NOTE_ACTION, true);
        Actions.ACCIDENTAL_ACTION_GROUP.setSelected(Actions.SHARP_ACTION, true);
        Actions.DOT_ACTION_GROUP.setSelected(Actions.DOT_ACTION, true);
        Actions.ARTICULATION_ACTION_GROUP.setSelected(Actions.STACCATO_ACTION, true);
        Actions.NON_DURATION_ACTION_GROUP.setSelected(Actions.BREATH_MARK_ACTION, true);
        Actions.DYNAMIC_MARKING_ACTION_GROUP.setSelected(Actions.DYNAMIC_F_ACTION, true);
        Actions.FERMATA_ACTION.setSelected(true);
        Actions.ACCENT_ACTION.setSelected(true);
        Actions.REST_ACTION.setSelected(true);
        Actions.ACCIDENTAL_IN_PARENS_ACTION.setSelected(true);

        MessageCenter.post(new DocumentDidLoadNotification(new Composition()));

        assertAll(
            () -> assertThat(Actions.MODE_ACTION_GROUP.getSelected())
                .as("mode reset to EDIT").isSameAs(Actions.EDIT_MODE_ACTION),
            () -> assertThat(Actions.DURATION_ACTION_GROUP.getSelected())
                .as("duration reset to QUARTER_NOTE").isSameAs(Actions.QUARTER_NOTE_ACTION),
            () -> assertThat(Actions.ACCIDENTAL_ACTION_GROUP.getSelected())
                .as("accidental group cleared").isNull(),
            () -> assertThat(Actions.ARTICULATION_ACTION_GROUP.getSelected())
                .as("articulation group cleared").isNull(),
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
