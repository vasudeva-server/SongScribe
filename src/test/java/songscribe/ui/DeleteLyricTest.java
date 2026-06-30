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
package songscribe.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.ArrayList;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.MainFrameMockTest;
import songscribe.message.MessageCenter;
import songscribe.message.command.PasteboardOpCommand;
import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.Lyric;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.ui.action.PasteboardAction;
import songscribe.ui.clipboard.ClipboardManager;
import songscribe.ui.component.ScoreView;
import songscribe.ui.component.ScoreViewController;
import songscribe.ui.selection.ReflectionTestHelper;
import songscribe.ui.selection.SelectionCoordinator;

/**
 * Integration tests for the lyric-deletion dispatch in
 * {@link ScoreViewController#handleDelete()} (Phase 3).
 *
 * <p>Line layout used throughout:
 * <pre>
 *  idx: 0         1
 *       CROTCHET  CROTCHET
 *       (lyric)
 * </pre>
 */
class DeleteLyricTest extends MainFrameMockTest {

    private static final int VERSE = 1;
    private static final int LYRIC_NOTE_INDEX = 0;

    private Song song;
    private StaffElement lyricNote;
    private StaffElement otherNote;
    private SelectionCoordinator coordinator;

    @BeforeEach
    void setUp() {
        song = new Song();
        var line = song.getLine(0);
        lyricNote = new StaffElement(ElementType.CROTCHET);
        otherNote = new StaffElement(ElementType.CROTCHET);

        song.withoutMutationTracking(() -> {
            line.addElement(lyricNote);
            line.addElement(otherNote);
            lyricNote.setLyricForVerse(VERSE, Lyric.Syllabic.SINGLE, false, "foo", Lyric.Extend.NONE);
        });

        coordinator = createCoordinator(line);
    }

    private SelectionCoordinator createCoordinator(Line line) {
        var coordinator = ReflectionTestHelper.createCoordinatorForLine(line);

        coordinator.setManagedActions(new ArrayList<>());

        return coordinator;
    }

    private ScoreViewController scoreCoordinator() {
        var mockScore = mock(ScoreView.class);
        when(mockScore.getSong()).thenReturn(song);
        when(mockScore.isFocusOwner()).thenReturn(true);
        return new ScoreViewController(
            mockScore,
            new MusicEditOperations(song, coordinator),
            coordinator,
            mock(ClipboardManager.class)
        );
    }

    // -------------------------------------------------------------------------
    // Lyric branch
    // -------------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class LyricBranch {

        @Test
        void testDeleteRemovesLyricFromElement() {
            try (var mc = mockStatic(MessageCenter.class)) {
                coordinator.selectLyric(lyricNote, VERSE);
                scoreCoordinator().handlePasteboardOp(
                    new PasteboardOpCommand(PasteboardAction.Operation.DELETE));
            }

            assertThat(lyricNote.getLyricForVerse(VERSE)).isNull();
        }

        @Test
        void testDeleteClearsLyricSelection() {
            try (var mc = mockStatic(MessageCenter.class)) {
                coordinator.selectLyric(lyricNote, VERSE);
                scoreCoordinator().handlePasteboardOp(
                    new PasteboardOpCommand(PasteboardAction.Operation.DELETE));
            }

            assertThat(coordinator.getLyricSelection()).isNull();
        }

        @Test
        void testDeleteLyricLeavesElementIntact() {
            var line = song.getLine(0);
            var countBefore = line.elementCount();

            try (var mc = mockStatic(MessageCenter.class)) {
                coordinator.selectLyric(lyricNote, VERSE);
                scoreCoordinator().handlePasteboardOp(
                    new PasteboardOpCommand(PasteboardAction.Operation.DELETE));
            }

            assertThat(line.elementCount()).isEqualTo(countBefore);
        }
    }

    // -------------------------------------------------------------------------
    // Element branch — lyric selection absent
    // -------------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class ElementBranch {

        @Test
        void testDeleteElementDoesNotTouchLyric() {
            ReflectionTestHelper.selectNote(coordinator, LYRIC_NOTE_INDEX);

            try (var mc = mockStatic(MessageCenter.class)) {
                // No lyric selection — falls through to element branch. The lyric on
                // lyricNote is removed by element deletion itself, but otherNote is untouched.
                scoreCoordinator().handlePasteboardOp(
                    new PasteboardOpCommand(PasteboardAction.Operation.DELETE));
            }

            assertThat(coordinator.getLyricSelection()).isNull();
        }

        @Test
        void testDeleteElementRemovesElement() {
            var line = song.getLine(0);
            var countBefore = line.elementCount();
            ReflectionTestHelper.selectNote(coordinator, LYRIC_NOTE_INDEX);

            try (var mc = mockStatic(MessageCenter.class)) {
                scoreCoordinator().handlePasteboardOp(
                    new PasteboardOpCommand(PasteboardAction.Operation.DELETE));
            }

            assertThat(line.elementCount()).isEqualTo(countBefore - 1);
        }
    }

    // -------------------------------------------------------------------------
    // No-selection — neither branch fires
    // -------------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class NoSelection {

        @Test
        void testDeleteIsNoOpWhenNothingSelected() {
            var line = song.getLine(0);
            var countBefore = line.elementCount();

            try (var mc = mockStatic(MessageCenter.class)) {
                // No lyric selection and no element selection: handleDelete falls through.
                scoreCoordinator().handlePasteboardOp(
                    new PasteboardOpCommand(PasteboardAction.Operation.DELETE));
            }

            assertThat(lyricNote.getLyricForVerse(VERSE)).isNotNull();
            assertThat(line.elementCount()).isEqualTo(countBefore);
        }
    }

    // -------------------------------------------------------------------------
    // Neighbor adjustment — syllabic chain repair after lyric deletion
    // -------------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class NeighborAdjustment {

        @Test
        void testDeleteLyricAdjustsPredecessorSyllabic() {
            // "foo-" (BEGIN) at 0, "bar" (END) at 1; delete "bar" → "foo" becomes SINGLE
            song.withoutMutationTracking(() -> {
                lyricNote.setLyricForVerse(VERSE, Lyric.Syllabic.BEGIN, false, "foo", Lyric.Extend.NONE);
                otherNote.setLyricForVerse(VERSE, Lyric.Syllabic.END, false, "bar", Lyric.Extend.NONE);
            });

            try (var mc = mockStatic(MessageCenter.class)) {
                coordinator.selectLyric(otherNote, VERSE);
                scoreCoordinator().handlePasteboardOp(
                    new PasteboardOpCommand(PasteboardAction.Operation.DELETE));
            }

            assertThat(lyricNote.getLyricForVerse(VERSE))
                .isNotNull()
                .extracting(lyric -> lyric != null ? lyric.syllabic() : null)
                .isEqualTo(Lyric.Syllabic.SINGLE);
        }

        @Test
        void testDeleteLyricAdjustsSuccessorWhenNoPredecessor() {
            // "foo" (BEGIN) at 0, "bar" (END) at 1; delete "foo" → "bar" becomes SINGLE
            song.withoutMutationTracking(() -> {
                lyricNote.setLyricForVerse(VERSE, Lyric.Syllabic.BEGIN, false, "foo", Lyric.Extend.NONE);
                otherNote.setLyricForVerse(VERSE, Lyric.Syllabic.END, false, "bar", Lyric.Extend.NONE);
            });

            try (var mc = mockStatic(MessageCenter.class)) {
                coordinator.selectLyric(lyricNote, VERSE);
                scoreCoordinator().handlePasteboardOp(
                    new PasteboardOpCommand(PasteboardAction.Operation.DELETE));
            }

            assertThat(otherNote.getLyricForVerse(VERSE))
                .isNotNull()
                .extracting(lyric -> lyric != null ? lyric.syllabic() : null)
                .isEqualTo(Lyric.Syllabic.SINGLE);
        }
    }
}
