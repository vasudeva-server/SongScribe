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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.List;

import module java.desktop;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import songscribe.MainFrameMockTest;
import songscribe.dom.ElementType;
import songscribe.dom.Song;
import songscribe.dom.Tuplet;
import songscribe.message.MessageCenter;
import songscribe.message.command.ToggleTupletCommand;
import songscribe.message.notification.DocumentDidLoadNotification;
import songscribe.message.notification.MusicSelectionDidChangeNotification;
import songscribe.message.notification.SongDidChangeNotification;
import songscribe.ui.selection.TupletToggleInfo;

/**
 * Verifies the enable/disable rules in {@link TupletAction#musicSelectionDidChange}
 * for each row of the Phase 2 table in {@code plans/tuplet-action-enable-disable.md}.
 */
class TupletActionTest extends MainFrameMockTest {

    // > 1 so REQUIRES_MULTIPLE_SELECTION passes, letting updateEnabledState() return true.
    private static final int SELECTION_SIZE = 3;

    private TupletAction removeAction;
    private TupletAction dupletAction;
    private TupletAction tripletAction;
    private TupletAction quadrupletAction;
    private TupletAction quintupletAction;
    private TupletAction sextupletAction;
    private TupletAction septupletAction;

    @BeforeEach
    void setUp() {
        // PlaybackController's static initializer creates UIAction instances with keyboard
        // shortcuts that call mainFrame.getRootPane(), so we need a JRootPane mock.
        var mockRootPane = mock(JRootPane.class);
        when(mockRootPane.getInputMap(anyInt())).thenReturn(new InputMap());
        when(mockRootPane.getActionMap()).thenReturn(new ActionMap());
        when(mockEnv().frame().getRootPane()).thenReturn(mockRootPane);

        // Override defaults so that the general updateEnabledState() checks pass,
        // leaving the tuplet-specific logic in charge of the final enabled state.
        when(mockEnv().score().getSelectionSize()).thenReturn(SELECTION_SIZE);
        when(mockEnv().coordinator().hasActiveSelection()).thenReturn(true);
        when(mockEnv().coordinator().selectionHasDurations()).thenReturn(true);

        var mainFrame = mainFrame();
        removeAction = TupletAction.createRemoveAction(mainFrame);
        dupletAction = TupletAction.createDupletAction(mainFrame);
        tripletAction = TupletAction.createTripletAction(mainFrame);
        quadrupletAction = TupletAction.createQuadrupletAction(mainFrame);
        quintupletAction = TupletAction.createQuintupletAction(mainFrame);
        sextupletAction = TupletAction.createSextupletAction(mainFrame);
        septupletAction = TupletAction.createSeptupletAction(mainFrame);
    }

    // -----------------------------------------------------------------------
    // Tests
    // -----------------------------------------------------------------------

    @Test
    void testFullCoverageOfQuintupletDisablesQuintupletEnablesOthersAndRemove() {
        fireAll(new TupletToggleInfo(true, makeTuplet(TupletAction.Tuplet.QUINTUPLET.getSize()), true));

        assertThat(removeAction.isEnabled()).isTrue();
        assertThat(dupletAction.isEnabled()).isTrue();
        assertThat(tripletAction.isEnabled()).isTrue();
        assertThat(quadrupletAction.isEnabled()).isTrue();
        assertThat(quintupletAction.isEnabled()).isFalse();  // matching grade → no-op
        assertThat(sextupletAction.isEnabled()).isTrue();
        assertThat(septupletAction.isEnabled()).isTrue();
    }

    @Test
    void testFullCoverageOfTripletDisablesTripletEnablesOthersAndRemove() {
        fireAll(new TupletToggleInfo(true, makeTuplet(TupletAction.Tuplet.TRIPLET.getSize()), true));

        assertThat(removeAction.isEnabled()).isTrue();
        assertThat(dupletAction.isEnabled()).isTrue();
        assertThat(tripletAction.isEnabled()).isFalse();  // matching grade → no-op
        assertThat(quadrupletAction.isEnabled()).isTrue();
        assertThat(quintupletAction.isEnabled()).isTrue();
        assertThat(sextupletAction.isEnabled()).isTrue();
        assertThat(septupletAction.isEnabled()).isTrue();
    }

    @Test
    void testNotUniformDisablesEverything() {
        fireAll(new TupletToggleInfo(false, null, false));

        assertThat(removeAction.isEnabled()).isFalse();

        for (var action : addActions()) {
            assertThat(action.isEnabled()).isFalse();
        }
    }

    @Test
    void testPartialCoverageOfTripletEnablesRemoveDisablesAllAddActions() {
        fireAll(new TupletToggleInfo(true, makeTuplet(TupletAction.Tuplet.TRIPLET.getSize()), false));

        assertThat(removeAction.isEnabled()).isTrue();

        for (var action : addActions()) {
            assertThat(action.isEnabled()).isFalse();
        }
    }

    @Test
    void testUniformNoTupletEnablesAddActionsDisablesRemove() {
        fireAll(new TupletToggleInfo(true, null, false));

        assertThat(removeAction.isEnabled()).isFalse();

        for (var action : addActions()) {
            assertThat(action.isEnabled()).isTrue();
        }
    }

    // Row 45: songDidChange delegates to the same handleChange path
    @Test
    void testSongDidChangeUpdatesEnabledState() {
        when(mockEnv().ctrl().canToggleTuplet()).thenReturn(new TupletToggleInfo(true, null, false));
        tripletAction.songDidChange(new SongDidChangeNotification(List.of(), mock(Song.class)));
        assertThat(tripletAction.isEnabled()).isTrue();
    }

    // Row 45: documentDidLoad delegates to the same handleChange path
    @Test
    void testDocumentDidLoadUpdatesEnabledState() {
        when(mockEnv().ctrl().canToggleTuplet()).thenReturn(new TupletToggleInfo(true, null, false));
        tripletAction.documentDidLoad(new DocumentDidLoadNotification(mock(Song.class)));
        assertThat(tripletAction.isEnabled()).isTrue();
    }

    // Row 46: actionPerformed posts ToggleTupletCommand with the correct tuplet
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
        void testActionPerformedPostsToggleTupletCommand() {
            var e = new ActionEvent(new JButton(), ActionEvent.ACTION_PERFORMED, "");
            tripletAction.actionPerformed(e);
            var captor = ArgumentCaptor.forClass(ToggleTupletCommand.class);
            messageMock.verify(() -> MessageCenter.post(captor.capture()));
            assertThat(captor.getValue().getAction().getTuplet())
                .isEqualTo(TupletAction.Tuplet.TRIPLET);
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** Fires {@code musicSelectionDidChange} on all seven actions with the given tuplet state. */
    private void fireAll(TupletToggleInfo info) {
        when(mockEnv().ctrl().canToggleTuplet()).thenReturn(info);
        var notification = new MusicSelectionDidChangeNotification(mockEnv().score());

        removeAction.musicSelectionDidChange(notification);
        dupletAction.musicSelectionDidChange(notification);
        tripletAction.musicSelectionDidChange(notification);
        quadrupletAction.musicSelectionDidChange(notification);
        quintupletAction.musicSelectionDidChange(notification);
        sextupletAction.musicSelectionDidChange(notification);
        septupletAction.musicSelectionDidChange(notification);
    }

    private List<TupletAction> addActions() {
        return List.of(
            dupletAction, tripletAction, quadrupletAction,
            quintupletAction, sextupletAction, septupletAction
        );
    }

    private static Tuplet makeTuplet(int grade) {
        var anchor = ElementType.CROTCHET.newInstance();
        var end = ElementType.CROTCHET.newInstance();
        return new Tuplet(anchor, end, grade);
    }
}
