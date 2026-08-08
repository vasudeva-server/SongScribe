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

import module java.desktop;

import java.util.ArrayList;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import songscribe.MainFrameMockTest;
import songscribe.ui.action.Actions;

/**
 * Guards against a double-add of the separator or a barline item: {@code BarlineMenu} loops
 * {@code Actions.BARLINE_ACTIONS} and inserts a separator immediately before the final-double
 * entry, which is a real risk if that entry is ever moved or duplicated in the array.
 */
class BarlineMenuTest extends MainFrameMockTest {

    private BarlineMenu barlineMenu;

    @BeforeEach
    void setUp() {
        barlineMenu = new BarlineMenu();
    }

    @Test
    void testMenuHoldsOneItemPerBarlineActionAndOneSeparator() {
        var components = barlineMenu.getMenuComponents();

        var barlineItems = new ArrayList<JRadioButtonMenuItem>();
        var separatorCount = 0;

        for (var component : components) {
            if (component instanceof JRadioButtonMenuItem item) {
                barlineItems.add(item);
            } else if (component instanceof JPopupMenu.Separator) {
                separatorCount++;
            }
        }

        assertThat(barlineItems).hasSize(Actions.BARLINE_ACTIONS.length);
        assertThat(separatorCount).isEqualTo(1);
    }

    @Test
    void testFinalDoubleBarlineItemIsLastAndPrecededBySeparator() {
        var components = barlineMenu.getMenuComponents();

        assertThat(components).hasSizeGreaterThanOrEqualTo(2);

        var lastItem = components[components.length - 1];
        var itemBeforeLast = components[components.length - 2];

        assertThat(lastItem).isInstanceOf(JRadioButtonMenuItem.class);
        assertThat(((JRadioButtonMenuItem) lastItem).getAction())
            .isEqualTo(Actions.FINAL_DOUBLE_BARLINE_ACTION);
        assertThat(itemBeforeLast).isInstanceOf(JPopupMenu.Separator.class);
    }
}
