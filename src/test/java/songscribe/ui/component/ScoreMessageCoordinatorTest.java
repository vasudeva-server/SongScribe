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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.message.mutation.BeamingAddition;
import songscribe.message.mutation.BeamingRemoval;
import songscribe.message.mutation.Mutation;
import songscribe.message.mutation.TieAddition;
import songscribe.message.mutation.TieRemoval;
import songscribe.message.mutation.TupletAddition;
import songscribe.message.mutation.TupletRemoval;
import songscribe.message.notification.SongDidChangeNotification;
import songscribe.music.BeamSpan;
import songscribe.music.Song;
import songscribe.music.ElementType;
import songscribe.music.Line;
import songscribe.music.Lyric;
import songscribe.music.StaffElement;
import songscribe.music.TieSpan;
import songscribe.music.TupletSpan;
import songscribe.ui.MusicEditOperations;
import songscribe.ui.clipboard.ClipboardManager;
import songscribe.ui.component.score.LineComponent;
import songscribe.ui.component.score.LinePanel;
import songscribe.ui.component.score.MainPanel;
import songscribe.ui.component.score.StaffPanel;
import songscribe.ui.edit.EditModeManager;
import songscribe.ui.selection.ReflectionTestHelper;
import songscribe.ui.selection.SelectionCoordinator;

class ScoreMessageCoordinatorTest extends UnitTest {

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class DeleteLyric {

        @Test
        void testDeleteRemovesSelectedLyricAndClearsLyricSelection() {
            var song = new Song();
            var line = song.getLine(0);
            var element = ElementType.CROTCHET.newInstance();
            element.setLyricForVerse(1, Lyric.Syllabic.SINGLE, false, "om", Lyric.Extend.NONE);
            song.withoutMutationTracking(() -> line.addElement(element));

            var selectionCoordinator = ReflectionTestHelper.createCoordinatorForLine(line);
            selectionCoordinator.selectLyric(element, 1);
            assertThat(selectionCoordinator.getLyricSelection()).isEqualTo(
                new SelectionCoordinator.LyricSelection(element, 1));
            assertThat(line.getElementIndex(element)).isGreaterThanOrEqualTo(0);

            var scoreMock = mock(Score.class);
            when(scoreMock.isFocusOwner()).thenReturn(true);
            when(scoreMock.getSong()).thenReturn(song);
            when(scoreMock.canDeleteLine()).thenReturn(false);

            var coordinator = new ScoreMessageCoordinator(
                scoreMock,
                mock(MusicEditOperations.class),
                mock(EditModeManager.class),
                selectionCoordinator,
                mock(ClipboardManager.class)
            );

            invokeHandleDelete(coordinator);

            assertThat(element.getLyricForVerse(1)).isNull();
            assertThat(selectionCoordinator.hasLyricSelection()).isFalse();
            assertThat(selectionCoordinator.hasActiveSelection()).isFalse();
            verify(scoreMock).selectionChanged();
            verify(scoreMock).repaint();
        }

        private void invokeHandleDelete(ScoreMessageCoordinator coordinator) {
            try {
                var method = ScoreMessageCoordinator.class.getDeclaredMethod("handleDelete");
                method.setAccessible(true);
                method.invoke(coordinator);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("Failed to invoke handleDelete", e);
            }
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
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
            noteA.setXOffsetPx(0);
            var grace = pairedGraceNote();
            grace.setXOffsetPx(8);
            var noteB = crotchet();
            noteB.setXOffsetPx(10);
            var noteC = crotchet();
            noteC.setXOffsetPx(20);
            var line = lineWith(noteA, grace, noteB, noteC);

            ScoreMessageCoordinator.deleteNote(2, line);

            // firstDeletedIndex is 1 (grace), so shift = grace.x - noteC.x = 8 - 20 = -12
            // noteC.x + (-12) = 20 - 12 = 8
            assertThat(noteC.getXOffsetPx()).isEqualTo(8);
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

        @Test
        void testDeleteNoteBreaksSyllableRelationWhenDeletedNoteIsTerminus() {
            var predecessor = crotchet();
            var terminus = crotchet();
            predecessor.properties.lyrics.add(new Lyric(1, "do", Lyric.Extend.NONE, Lyric.Syllabic.BEGIN, false));
            terminus.properties.lyrics.add(new Lyric(1, "re", Lyric.Extend.NONE, Lyric.Syllabic.END, false));
            var line = lineWith(predecessor, terminus);

            ScoreMessageCoordinator.deleteNote(1, line);

            assertThat(predecessor.properties.lyrics.get(0).syllabic())
                .isEqualTo(Lyric.Syllabic.SINGLE);
        }

        @Test
        void testDeleteNotePreservesSyllableRelationWhenDeletedNoteIsChainMember() {
            var first = crotchet();
            var middle = crotchet();
            var last = crotchet();
            first.properties.lyrics.add(new Lyric(1, "do", Lyric.Extend.NONE, Lyric.Syllabic.BEGIN, false));
            middle.properties.lyrics.add(new Lyric(1, "re", Lyric.Extend.NONE, Lyric.Syllabic.MIDDLE, false));
            last.properties.lyrics.add(new Lyric(1, "mi", Lyric.Extend.NONE, Lyric.Syllabic.END, false));
            var line = lineWith(first, middle, last);

            ScoreMessageCoordinator.deleteNote(1, line);

            assertThat(first.properties.lyrics.get(0).syllabic())
                .isEqualTo(Lyric.Syllabic.BEGIN);
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
            var line = detachedLine();

            for (var element : elements) {
                line.addElement(element);
            }

            return line;
        }
    }

    /**
     * Regression: each span mutation type introduced by the ChangeType
     * migration must route through {@code hasLineLayoutMutation} and reach
     * {@code LineComponent.invalidateLayout()}. A mutation that does not
     * implement {@code LineScopedMutation} silently skips layout invalidation,
     * which is the main user-visible correctness risk of the migration.
     */
    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class LayoutInvalidation {

        private Line line;
        private LineComponent lineComponentMock;
        private ScoreMessageCoordinator coordinator;

        @BeforeEach
        void setUp() {
            line = detachedLine();
            lineComponentMock = mock(LineComponent.class);

            var linePanelMock = mock(LinePanel.class);
            when(linePanelMock.getLine()).thenReturn(line);
            when(linePanelMock.getLineComponent()).thenReturn(lineComponentMock);

            var staffPanelMock = mock(StaffPanel.class);
            when(staffPanelMock.getLinePanels()).thenReturn(List.of(linePanelMock));

            var mainPanelMock = mock(MainPanel.class);
            when(mainPanelMock.getStaffPanel()).thenReturn(staffPanelMock);

            var scoreMock = mock(Score.class);
            when(scoreMock.getMainPanel()).thenReturn(mainPanelMock);

            coordinator = new ScoreMessageCoordinator(
                scoreMock,
                mock(MusicEditOperations.class),
                mock(EditModeManager.class),
                mock(SelectionCoordinator.class),
                mock(ClipboardManager.class)
            );
        }

        private void fireNotification(Mutation mutation) {
            coordinator.songDidChange(
                new SongDidChangeNotification(List.of(mutation), new Song())
            );
        }

        @Test
        void testBeamingAdditionInvalidatesLayout() {
            fireNotification(new BeamingAddition(line, new BeamSpan(0, 2)));
            verify(lineComponentMock).invalidateLayout();
        }

        @Test
        void testBeamingRemovalInvalidatesLayout() {
            fireNotification(new BeamingRemoval(line, new BeamSpan(0, 2)));
            verify(lineComponentMock).invalidateLayout();
        }

        @Test
        void testTieAdditionInvalidatesLayout() {
            fireNotification(new TieAddition(line, new TieSpan(0, 1)));
            verify(lineComponentMock).invalidateLayout();
        }

        @Test
        void testTieRemovalInvalidatesLayout() {
            fireNotification(new TieRemoval(line, new TieSpan(0, 1)));
            verify(lineComponentMock).invalidateLayout();
        }

        @Test
        void testTupletAdditionInvalidatesLayout() {
            fireNotification(new TupletAddition(line, new TupletSpan(0, 2, 3)));
            verify(lineComponentMock).invalidateLayout();
        }

        @Test
        void testTupletRemovalInvalidatesLayout() {
            fireNotification(new TupletRemoval(line, new TupletSpan(0, 2, 3)));
            verify(lineComponentMock).invalidateLayout();
        }
    }
}
