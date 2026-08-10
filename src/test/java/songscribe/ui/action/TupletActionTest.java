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
import static songscribe.dom.StaffElementFactory.crotchet;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import module java.desktop;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import songscribe.MainFrameMockTest;
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

    /** Every creatable grade, for spans whose validity is not what a test is about. */
    private static final Set<Integer> ALL_GRADES = Arrays.stream(TupletAction.Tuplet.values())
        .map(TupletAction.Tuplet::getSize)
        .collect(Collectors.toUnmodifiableSet());

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
    void testFullCoverageOfQuintupletChecksQuintupletAndEnablesTheValidGrades() {
        fireAll(new TupletToggleInfo(
            true, ALL_GRADES, makeTuplet(TupletAction.Tuplet.QUINTUPLET.getSize()), true));

        for (var action : addActions()) {
            assertThat(action.isEnabled()).isTrue();
        }

        assertThat(isSelected(quintupletAction)).isTrue();
        assertThat(isSelected(tripletAction)).isFalse();
    }

    @Test
    void testFullCoverageOfTripletChecksTripletAndEnablesOthers() {
        fireAll(new TupletToggleInfo(
            true, ALL_GRADES, makeTuplet(TupletAction.Tuplet.TRIPLET.getSize()), true));

        for (var action : addActions()) {
            assertThat(action.isEnabled()).isTrue();
        }

        assertThat(isSelected(tripletAction)).isTrue();
        assertThat(isSelected(quintupletAction)).isFalse();
    }

    @Test
    void testNotUniformDisablesEverything() {
        fireAll(new TupletToggleInfo(false, null, false));

        for (var action : addActions()) {
            assertThat(action.isEnabled()).isFalse();
        }
    }

    @Test
    void testPartialCoverageOfTripletDisablesAllAddActions() {
        fireAll(new TupletToggleInfo(false, makeTuplet(TupletAction.Tuplet.TRIPLET.getSize()), false));

        for (var action : addActions()) {
            assertThat(action.isEnabled()).isFalse();
        }
    }

    @Test
    void testUniformNoTupletEnablesValidGrades() {
        fireAll(new TupletToggleInfo(true, ALL_GRADES, null, false));

        for (var action : addActions()) {
            assertThat(action.isEnabled()).isTrue();
            assertThat(isSelected(action)).isFalse();
        }
    }

    // Row 45: songDidChange delegates to the same handleChange path
    @Test
    void testSongDidChangeUpdatesEnabledState() {
        when(mockEnv().ctrl().canToggleTuplet())
            .thenReturn(new TupletToggleInfo(true, ALL_GRADES, null, false));
        tripletAction.songDidChange(new SongDidChangeNotification(List.of(), mock(Song.class)));
        assertThat(tripletAction.isEnabled()).isTrue();
    }

    // Row 45: documentDidLoad delegates to the same handleChange path
    @Test
    void testDocumentDidLoadUpdatesEnabledState() {
        when(mockEnv().ctrl().canToggleTuplet())
            .thenReturn(new TupletToggleInfo(true, ALL_GRADES, null, false));
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

    /**
     * Every other test here passes either all grades or none, and those two cases cannot
     * tell a per-grade check apart from no check at all: replacing the enabled state with a
     * blanket "the selection can carry some tuplet" would pass all of them. This one names a
     * mixed set — the shape ordinary music actually produces — so only a per-grade check
     * satisfies it. If it broke, the menu would offer the user grades that cannot be applied
     * and clicking one would fail the validator's own precondition.
     */
    @Test
    void testOnlyTheGradesInValidGradesAreEnabled() {
        var validGrades = Set.of(
            TupletAction.Tuplet.TRIPLET.getSize(), TupletAction.Tuplet.SEXTUPLET.getSize());

        fireAll(new TupletToggleInfo(true, validGrades, null, false));

        for (var action : addActions()) {
            var grade = action.getTuplet().getSize();

            assertThat(action.isEnabled())
                .as("grade %d must be enabled only when the span could become it", grade)
                .isEqualTo(validGrades.contains(grade));
        }
    }

    /** Fires {@code musicSelectionDidChange} on all six actions with the given tuplet state. */
    private void fireAll(TupletToggleInfo info) {
        when(mockEnv().ctrl().canToggleTuplet()).thenReturn(info);
        var notification = new MusicSelectionDidChangeNotification(mockEnv().score());

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

    /** Reads the action's {@code Action.SELECTED_KEY}, which the radio menu items mirror. */
    private static boolean isSelected(TupletAction action) {
        return Boolean.TRUE.equals(action.getValue(Action.SELECTED_KEY));
    }

    private static Tuplet makeTuplet(int grade) {
        var anchor = crotchet();
        var end = crotchet();
        return Tuplet.withUnresolvedRatio(anchor, end, grade);
    }
}
