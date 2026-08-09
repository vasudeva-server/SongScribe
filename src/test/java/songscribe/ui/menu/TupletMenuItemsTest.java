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

package songscribe.ui.menu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import module java.desktop;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import songscribe.MainFrameMockTest;
import songscribe.dom.ElementType;
import songscribe.dom.Tuplet;
import songscribe.ui.action.Actions;
import songscribe.ui.action.TupletAction;
import songscribe.ui.selection.TupletToggleInfo;

/**
 * Covers the choices {@link TupletMenuItems#rebuild} makes about what the tuplet menu
 * offers. A grade the selection cannot become is left out rather than shown greyed, the
 * grade the selection already carries is always listed so something can be checked, and a
 * selection with nothing to offer gets a disabled placeholder instead of an empty menu.
 */
class TupletMenuItemsTest extends MainFrameMockTest {

    /** The grades an ordinary run of three quarter notes can become. */
    private static final Set<Integer> TRIPLET_AND_SEXTUPLET = Set.of(
        TupletAction.Tuplet.TRIPLET.getSize(), TupletAction.Tuplet.SEXTUPLET.getSize());

    /** The single disabled row shown when no grade is available. */
    private static final int PLACEHOLDER_ITEM_COUNT = 1;

    @AfterEach
    void restoreActionState() {
        // The tuplet actions are process-wide singletons whose enabled state this test
        // drives directly, so leave them enabled for whatever runs next.
        for (var action : Actions.TOGGLE_TUPLET_ACTIONS) {
            action.setEnabled(true);
        }
    }

    @Test
    void testOnlyEnabledGradesAreOffered() {
        setEnabledGrades(TRIPLET_AND_SEXTUPLET);
        stubExistingTuplet(null);

        var menu = new JPopupMenu();
        TupletMenuItems.rebuild(menu);

        assertThat(gradeItemLabels(menu))
            .as("a grade the span cannot become is left out, not shown disabled")
            .containsExactlyInAnyOrderElementsOf(labelsFor(TRIPLET_AND_SEXTUPLET));
    }

    /**
     * A selection that already is a tuplet must show that grade checked, so the user can see
     * what it is. That has to hold even when the span could not be turned into that grade
     * again — after a beat edit, say — because otherwise a selection the user can plainly see
     * is a triplet would show nothing checked at all.
     */
    @Test
    void testTheExistingGradeIsOfferedEvenWhenItIsNotCreatable() {
        var quintuplet = TupletAction.Tuplet.QUINTUPLET.getSize();
        setEnabledGrades(TRIPLET_AND_SEXTUPLET);
        stubExistingTuplet(tupletOfGrade(quintuplet));

        var menu = new JPopupMenu();
        TupletMenuItems.rebuild(menu);

        assertThat(gradeItemLabels(menu))
            .as("the grade the selection carries is listed alongside the creatable ones")
            .contains(labelFor(quintuplet));
    }

    @Test
    void testASelectionWithNoGradeAvailableGetsADisabledPlaceholder() {
        setEnabledGrades(Set.of());
        stubExistingTuplet(null);

        var menu = new JPopupMenu();
        TupletMenuItems.rebuild(menu);

        var items = menuItems(menu);

        assertThat(items)
            .as("the placeholder alone, and no grade rows at all")
            .hasSize(PLACEHOLDER_ITEM_COUNT);
        assertThat(items.getFirst().isEnabled())
            .as("the placeholder explains the emptiness; it must not be clickable")
            .isFalse();
    }

    @Test
    void testRebuildReplacesThePreviousItemsRatherThanAppending() {
        var menu = new JPopupMenu();
        setEnabledGrades(TRIPLET_AND_SEXTUPLET);
        stubExistingTuplet(null);

        TupletMenuItems.rebuild(menu);
        var firstCount = menu.getComponentCount();
        TupletMenuItems.rebuild(menu);

        assertThat(menu.getComponentCount())
            .as("reopening the menu must not stack a second copy of every grade")
            .isEqualTo(firstCount);
    }

    // -----------------------------------------------------------------------
    // Fixture helpers
    // -----------------------------------------------------------------------

    /** Enables exactly the given grades on the shared action singletons. */
    private static void setEnabledGrades(Set<Integer> grades) {
        for (var action : Actions.TOGGLE_TUPLET_ACTIONS) {
            action.setEnabled(grades.contains(action.getTuplet().getSize()));
        }
    }

    private void stubExistingTuplet(@Nullable Tuplet existing) {
        when(mockEnv().ctrl().canToggleTuplet())
            .thenReturn(new TupletToggleInfo(existing == null, Set.of(), existing, existing != null));
    }

    private static Tuplet tupletOfGrade(int grade) {
        return Tuplet.withUnresolvedRatio(
            ElementType.CROTCHET.newInstance(), ElementType.CROTCHET.newInstance(), grade);
    }

    /** The grade items only — the disabled placeholder is a plain item, not a grade. */
    private static List<String> gradeItemLabels(JPopupMenu menu) {
        return menuItems(menu).stream()
            .filter(JRadioButtonMenuItem.class::isInstance)
            .map(AbstractButton::getText)
            .toList();
    }

    private static List<JMenuItem> menuItems(JPopupMenu menu) {
        return Arrays.stream(menu.getComponents())
            .filter(JMenuItem.class::isInstance)
            .map(JMenuItem.class::cast)
            .toList();
    }

    private static List<String> labelsFor(Set<Integer> grades) {
        return Actions.TOGGLE_TUPLET_ACTIONS.stream()
            .filter(action -> grades.contains(action.getTuplet().getSize()))
            .map(action -> (String) action.getValue(Action.NAME))
            .toList();
    }

    private static String labelFor(int grade) {
        return Actions.TOGGLE_TUPLET_ACTIONS.stream()
            .filter(action -> action.getTuplet().getSize() == grade)
            .map(action -> (String) action.getValue(Action.NAME))
            .findFirst()
            .orElseThrow(() -> new AssertionError("no tuplet action for grade " + grade));
    }
}
