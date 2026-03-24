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

package songscribe.ui.component;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.music.ElementType;
import songscribe.music.Line;
import songscribe.music.StaffElement;

class ScoreMessageCoordinatorTest extends UnitTest {

    @Nested
    class DeleteNote {

        @Test
        void testDeleteNoteRemovesOneElement() {
            var line = lineWith(crotchet(), crotchet(), crotchet());
            var removed = ScoreMessageCoordinator.deleteNote(1, line);

            assertThat(removed).isEqualTo(1);
            assertThat(line.elementCount()).isEqualTo(2);
        }

        @Test
        void testDeleteNoteRemovesPrecedingPairedGraceNote() {
            var line = lineWith(crotchet(), pairedGraceNote(), crotchet());
            var removed = ScoreMessageCoordinator.deleteNote(2, line);

            assertThat(removed).isEqualTo(2);
            assertThat(line.elementCount()).isEqualTo(1);
        }

        @Test
        void testDeleteNoteDoesNotRemoveUnpairedGraceNote() {
            var graceNote = ElementType.GRACE_QUAVER.newInstance();
            var line = lineWith(crotchet(), graceNote, crotchet());
            var removed = ScoreMessageCoordinator.deleteNote(2, line);

            assertThat(removed).isEqualTo(1);
            assertThat(line.elementCount()).isEqualTo(2);
        }

        @Test
        void testDeleteNoteRemovesGlissandoFromPreviousNote() {
            var prev = crotchet();
            prev.setGlissando(StaffElement.Glissando.Type.SLIDE_OUT);
            var line = lineWith(prev, crotchet());
            ScoreMessageCoordinator.deleteNote(1, line);

            assertThat(prev.getGlissando()).isNull();
        }

        @Test
        void testDeleteNoteShiftsSubsequentElementsWhenGraceNoteRemoved() {
            // [A, G(paired), B, C] — deleting B also removes G.
            // C should shift left to close the gap from G's position.
            var noteA = crotchet();
            noteA.setXPosSs(0);
            var grace = pairedGraceNote();
            grace.setXPosSs(8);
            var noteB = crotchet();
            noteB.setXPosSs(10);
            var noteC = crotchet();
            noteC.setXPosSs(20);
            var line = lineWith(noteA, grace, noteB, noteC);

            ScoreMessageCoordinator.deleteNote(2, line);

            // firstDeletedIndex is 1 (grace), so shift = grace.x - noteC.x = 8 - 20 = -12
            // noteC.x + (-12) = 20 - 12 = 8
            assertThat(noteC.getXPosSs()).isEqualTo(8);
        }

        @Test
        void testSelectionLoopSkipsGraceNoteIndex() {
            // Simulates the handleDelete loop: [A, G(paired), B, C]
            // Selection covers indices 1..3 (G, B, C).
            // Without the fix, deleting B at index 2 also removes G at index 1,
            // then the loop tries index 1 which is now A — wrong element.
            var noteA = crotchet();
            var line = lineWith(noteA, pairedGraceNote(), crotchet(), crotchet());

            var selectionBegin = 1;
            var selectionEnd = 3;

            for (var i = selectionEnd; i >= selectionBegin; i--) {
                var removedCount = ScoreMessageCoordinator.deleteNote(i, line);
                i -= (removedCount - 1);
            }

            // Only noteA should remain
            assertThat(line.elementCount()).isEqualTo(1);
            assertThat(line.getElement(0)).isSameAs(noteA);
        }

        @Test
        void testSelectionLoopWithGraceNoteAtSelectionStart() {
            // [A, G(paired), B] — selection covers indices 1..2 (G and B)
            // Deleting B (index 2) also removes G (index 1), completing the selection.
            var noteA = crotchet();
            var line = lineWith(noteA, pairedGraceNote(), crotchet());

            var selectionBegin = 1;
            var selectionEnd = 2;

            for (var i = selectionEnd; i >= selectionBegin; i--) {
                var removedCount = ScoreMessageCoordinator.deleteNote(i, line);
                i -= (removedCount - 1);
            }

            assertThat(line.elementCount()).isEqualTo(1);
            assertThat(line.getElement(0)).isSameAs(noteA);
        }

        @Test
        void testSelectionLoopWithGraceNoteBeforeSelection() {
            // [G(paired), A, B] — selection covers indices 1..2 (A and B).
            // Deleting A (index 1) removes G (index 0) as an orphan too.
            var line = lineWith(pairedGraceNote(), crotchet(), crotchet());

            var selectionBegin = 1;
            var selectionEnd = 2;

            for (var i = selectionEnd; i >= selectionBegin; i--) {
                var removedCount = ScoreMessageCoordinator.deleteNote(i, line);
                i -= (removedCount - 1);
            }

            assertThat(line.elementCount()).isEqualTo(0);
        }

        private StaffElement crotchet() {
            return ElementType.CROTCHET.newInstance();
        }

        private StaffElement pairedGraceNote() {
            var grace = ElementType.GRACE_QUAVER.newInstance();
            grace.setGlissando(StaffElement.Glissando.Type.CONNECTED);
            return grace;
        }

        private Line lineWith(StaffElement... elements) {
            var line = new Line();

            for (var element : elements) {
                line.addElement(element);
            }

            return line;
        }
    }
}
