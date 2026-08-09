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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import songscribe.MainFrameMockTest;
import songscribe.Strings;
import songscribe.dom.Song;
import songscribe.message.MessageCenter;
import songscribe.message.notification.DocumentDidLoadNotification;
import songscribe.message.notification.MusicSelectionDidChangeNotification;
import songscribe.message.notification.SongDidChangeNotification;
import songscribe.ui.action.Actions;
import songscribe.ui.action.TupletAction;
import songscribe.ui.selection.TupletToggleInfo;

/**
 * Unit tests for {@link TupletPopupButton} covering:
 * <ul>
 *   <li>where the button's direct click points — the lowest grade the selection could
 *       actually become, left alone when no grade applies. Every one of the button's three
 *       notification handlers exists only to keep that up to date, so each is driven here.</li>
 *   <li>{@code configureButtonFromAction}: overrides tooltip to fixed tuplet string
 *       regardless of the action's own tooltip</li>
 * </ul>
 */
class TupletPopupButtonTest extends MainFrameMockTest {

    /**
     * Two grades the stubbed selection is allowed to become, deliberately neither adjacent to
     * each other nor first in the action list, so a button that simply picks the first or the
     * last action cannot agree with the expected answer by accident.
     */
    private static final int LOWEST_OFFERED_GRADE = TupletAction.Tuplet.QUADRUPLET.getSize();
    private static final int HIGHER_OFFERED_GRADE = TupletAction.Tuplet.SEXTUPLET.getSize();

    private TupletPopupButton button;

    @BeforeEach
    void setUp() {
        // Establish a known baseline. Nothing has to be restored afterwards: MainFrameMockTest
        // calls Actions.initialize() before every test, which replaces these actions outright.
        for (var action : Actions.TOGGLE_TUPLET_ACTIONS) {
            action.setEnabled(false);
        }

        button = new TupletPopupButton();
    }

    @AfterEach
    void unsubscribeButton() {
        // The button subscribes itself to the message bus in its constructor.
        MessageCenter.unsubscribe(button);
    }

    // -----------------------------------------------------------------------
    // Enabled state derived from Actions.TOGGLE_TUPLET_ACTIONS
    // -----------------------------------------------------------------------

    @Test
    void testButtonIsDisabledWhenAllTupletActionsAreDisabled() {
        // All actions disabled in @BeforeEach
        assertThat(button.isEnabled()).isFalse();
    }

    // -----------------------------------------------------------------------
    // Where a direct click on the button lands. The three handlers below all feed the same
    // decision; they differ only in where they get the controller from, so each is driven
    // separately — a handler wired to the wrong call would otherwise go unnoticed.
    // -----------------------------------------------------------------------

    @Test
    void testMusicSelectionDidChangePointsTheClickAtTheLowestGradeOnOffer() {
        offerGrades(Set.of(HIGHER_OFFERED_GRADE, LOWEST_OFFERED_GRADE));

        button.musicSelectionDidChange(new MusicSelectionDidChangeNotification(mockEnv().score()));

        assertThat(button.getCurrentAction()).isEqualTo(actionForGrade(LOWEST_OFFERED_GRADE));
    }

    /**
     * An edit can change which grades are valid without moving the selection or the caret — the
     * answer depends on the notes and the beat, not only on where the selection sits.
     */
    @Test
    void testSongDidChangePointsTheClickAtTheLowestGradeOnOffer() {
        offerGrades(Set.of(HIGHER_OFFERED_GRADE, LOWEST_OFFERED_GRADE));

        button.songDidChange(new SongDidChangeNotification(List.of(), mock(Song.class)));

        assertThat(button.getCurrentAction()).isEqualTo(actionForGrade(LOWEST_OFFERED_GRADE));
    }

    @Test
    void testDocumentDidLoadPointsTheClickAtTheLowestGradeOnOffer() {
        offerGrades(Set.of(HIGHER_OFFERED_GRADE, LOWEST_OFFERED_GRADE));

        button.documentDidLoad(new DocumentDidLoadNotification(mock(Song.class)));

        assertThat(button.getCurrentAction()).isEqualTo(actionForGrade(LOWEST_OFFERED_GRADE));
    }

    /**
     * The whole button is disabled when the selection could become no grade at all, so there is
     * nothing to be gained by swapping the action underneath it — and a click that did land
     * would fail the edit operation's own precondition.
     */
    @Test
    void testTheDefaultActionIsLeftAloneWhenNoGradeIsOnOffer() {
        offerGrades(Set.of(HIGHER_OFFERED_GRADE, LOWEST_OFFERED_GRADE));
        button.musicSelectionDidChange(new MusicSelectionDidChangeNotification(mockEnv().score()));

        assertThat(button.getCurrentAction())
            .as("precondition")
            .isEqualTo(actionForGrade(LOWEST_OFFERED_GRADE));

        offerGrades(Set.of());
        button.musicSelectionDidChange(new MusicSelectionDidChangeNotification(mockEnv().score()));

        assertThat(button.getCurrentAction()).isEqualTo(actionForGrade(LOWEST_OFFERED_GRADE));
    }

    // -----------------------------------------------------------------------
    // Row 33: configureButtonFromAction — tooltip is always the fixed tuplet string
    // -----------------------------------------------------------------------

    @Test
    void testConfigureButtonFromActionUsesFixedTupletTooltip() {
        // configureButtonFromAction is called by setCurrentAction;
        // the action's own tooltip is irrelevant — the override must win.
        var action = Actions.TOGGLE_TUPLET_ACTIONS.getFirst();
        button.setCurrentAction(action);
        assertThat(button.getToolTipText()).isEqualTo(Strings.get(Strings.TOOLTIP_TUPLET));
    }

    // -----------------------------------------------------------------------
    // Fixture helpers
    // -----------------------------------------------------------------------

    /** Makes the mocked selection report exactly the given grades as creatable. */
    private void offerGrades(Set<Integer> grades) {
        when(mockEnv().ctrl().canToggleTuplet())
            .thenReturn(new TupletToggleInfo(!grades.isEmpty(), grades, null, false));
    }

    private static TupletAction actionForGrade(int grade) {
        return Actions.TOGGLE_TUPLET_ACTIONS.stream()
            .filter(action -> action.getTuplet().getSize() == grade)
            .findFirst()
            .orElseThrow(() -> new AssertionError("no tuplet action for grade " + grade));
    }
}
