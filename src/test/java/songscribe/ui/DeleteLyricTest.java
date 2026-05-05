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

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import songscribe.UnitTest;
import songscribe.message.MessageCenter;
import songscribe.message.command.PasteboardOpCommand;
import songscribe.music.ElementType;
import songscribe.music.Line;
import songscribe.music.Lyric;
import songscribe.music.Song;
import songscribe.music.StaffElement;
import songscribe.ui.action.PasteboardAction;
import songscribe.ui.action.UIAction;
import songscribe.ui.clipboard.ClipboardManager;
import songscribe.ui.component.MainFrame;
import songscribe.ui.component.Score;
import songscribe.ui.component.ScoreMessageCoordinator;
import songscribe.ui.edit.EditModeManager;
import songscribe.ui.selection.ReflectionTestHelper;
import songscribe.ui.selection.SelectionCoordinator;

/**
 * Integration tests for the lyric-deletion dispatch in
 * {@link ScoreMessageCoordinator#handleDelete()} (Phase 3).
 *
 * <p>Line layout used throughout:
 * <pre>
 *  idx: 0         1
 *       CROTCHET  CROTCHET
 *       (lyric)
 * </pre>
 */
class DeleteLyricTest extends UnitTest {

    private static final int VERSE = 1;
    private static final int LYRIC_NOTE_INDEX = 0;

    @Nullable private MockedStatic<MainFrame> mainFrameMock;

    private Song song;
    private StaffElement lyricNote;
    private StaffElement otherNote;
    private SelectionCoordinator coordinator;

    @BeforeEach
    void setUp() {
        mainFrameMock = mockStatic(MainFrame.class);
        mainFrameMock.when(MainFrame::getInstance).thenReturn(mock(MainFrame.class));

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

    @AfterEach
    void tearDown() {
        var mock = mainFrameMock;

        if (mock != null) {
            mock.close();
            mainFrameMock = null;
        }
    }

    private SelectionCoordinator createCoordinator(Line line) {
        var coordinator = ReflectionTestHelper.createCoordinatorForLine(line);

        try {
            var field = SelectionCoordinator.class.getDeclaredField("managedActions");
            field.setAccessible(true);
            field.set(coordinator, new ArrayList<UIAction>());
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Failed to inject managed actions", e);
        }

        return coordinator;
    }

    private ScoreMessageCoordinator scoreCoordinator() {
        var mockScore = mock(Score.class);
        when(mockScore.getSong()).thenReturn(song);
        when(mockScore.isFocusOwner()).thenReturn(true);
        return new ScoreMessageCoordinator(
            mockScore,
            new MusicEditOperations(song, coordinator),
            mock(EditModeManager.class),
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
                .extracting(Lyric::syllabic)
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
                .extracting(Lyric::syllabic)
                .isEqualTo(Lyric.Syllabic.SINGLE);
        }
    }
}
