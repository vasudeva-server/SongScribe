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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.music.Song;
import songscribe.music.ElementType;

class ClipboardManagerTest extends UnitTest {

    private ClipboardManager clipboardManager;

    @BeforeEach
    void setUp() {
        clipboardManager = new ClipboardManager();
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class AddElement {

        @Test
        void testDoubleBarlinePassedThrough() {
            var barline = ElementType.DOUBLE_BARLINE.newInstance();
            clipboardManager.addElement(barline);
            assertThat(clipboardManager.getElement(0)).isSameAs(barline);
        }

        @Test
        void testFinalDoubleBarlineNormalizedToDoubleBarline() {
            clipboardManager.addElement(ElementType.FINAL_DOUBLE_BARLINE.newInstance());
            assertThat(clipboardManager.getElement(0).getType()).isEqualTo(ElementType.DOUBLE_BARLINE);
        }

        @Test
        void testNotePassedThrough() {
            var note = ElementType.CROTCHET.newInstance();
            clipboardManager.addElement(note);
            assertThat(clipboardManager.getElement(0)).isSameAs(note);
        }
    }

    // Verifies that normalizing a copied final barline does not affect the
    // song's own invariant-owned final barline.
    @Test
    void testSongFinalBarlineUntouched() {
        var song = new Song();
        var lastLine = song.getLine(song.lineCount() - 1);
        var lastIdx = lastLine.elementCount() - 1;

        // Simulate what handleCopy does: clone then add to clipboard
        clipboardManager.addElement(lastLine.getElement(lastIdx).clone());

        assertThat(clipboardManager.getElement(0).getType())
            .isEqualTo(ElementType.DOUBLE_BARLINE);
        assertThat(lastLine.getElement(lastIdx).getType())
            .isEqualTo(ElementType.FINAL_DOUBLE_BARLINE);
    }

    // Parallels testSongFinalBarlineUntouched for a REPEAT_RIGHT terminal.
    // REPEAT_RIGHT is structurally valid as an interior element, so it passes through
    // the clipboard unchanged; the song's terminal remains in place.
    @Test
    void testSongRightRepeatTerminalUntouched() {
        var song = new Song();
        song.replaceTerminal(ElementType.REPEAT_RIGHT);
        var lastLine = song.getLine(song.lineCount() - 1);
        var lastIdx = lastLine.elementCount() - 1;

        clipboardManager.addElement(lastLine.getElement(lastIdx).clone());

        assertThat(clipboardManager.getElement(0).getType())
            .isEqualTo(ElementType.REPEAT_RIGHT);
        assertThat(lastLine.getElement(lastIdx).getType())
            .isEqualTo(ElementType.REPEAT_RIGHT);
    }
}
