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

package songscribe.ui.clipboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.ElementType;

class ClipboardManagerTest extends UnitTest {

    private ClipboardManager clipboardManager;

    @BeforeEach
    void setUp() {
        clipboardManager = new ClipboardManager();
    }

    @Test
    void testIsEmptyReturnsTrueForANewlyCreatedManager() {
        assertThat(clipboardManager.isEmpty()).isTrue();
        assertThat(clipboardManager.getFragment()).isNull();
        assertThat(clipboardManager.getSize()).isEqualTo(0);
    }

    @Test
    void testSetFragmentReplacesTheCurrentFragment() {
        var note = ElementType.CROTCHET.newInstance();
        var fragment = new Fragment(List.of(note), List.of());

        clipboardManager.setFragment(fragment);

        assertThat(clipboardManager.getFragment()).isSameAs(fragment);
        assertThat(clipboardManager.getSize()).isEqualTo(1);
        assertThat(clipboardManager.isEmpty()).isFalse();
    }

    @Test
    void testSetFragmentAgainReplacesThePreviousOne() {
        var first = new Fragment(List.of(ElementType.CROTCHET.newInstance()), List.of());
        var second = new Fragment(
            List.of(ElementType.QUAVER.newInstance(), ElementType.MINIM.newInstance()), List.of()
        );

        clipboardManager.setFragment(first);
        clipboardManager.setFragment(second);

        assertThat(clipboardManager.getFragment()).isSameAs(second);
        assertThat(clipboardManager.getSize()).isEqualTo(2);
    }

    @Test
    void testClearEmptiesTheClipboard() {
        clipboardManager.setFragment(new Fragment(List.of(ElementType.CROTCHET.newInstance()), List.of()));

        clipboardManager.clear();

        assertThat(clipboardManager.isEmpty()).isTrue();
        assertThat(clipboardManager.getFragment()).isNull();
        assertThat(clipboardManager.getSize()).isEqualTo(0);
    }
}
