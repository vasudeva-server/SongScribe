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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import songscribe.UnitTest;
import songscribe.dom.Beam;
import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.Lyric;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.dom.Tie;
import songscribe.dom.Tuplet;
import songscribe.font.DocumentFonts;
import songscribe.message.Message;
import songscribe.message.command.InsertLineCommand;
import songscribe.message.command.SelectLineCommand;
import songscribe.message.mutation.BeamingAddition;
import songscribe.message.mutation.BeamingRemoval;
import songscribe.message.mutation.FontChange;
import songscribe.message.mutation.LayoutChange;
import songscribe.message.mutation.LayoutField;
import songscribe.message.mutation.MetadataChange;
import songscribe.message.mutation.MetadataField;
import songscribe.message.mutation.Mutation;
import songscribe.message.mutation.TieAddition;
import songscribe.message.mutation.TieRemoval;
import songscribe.message.mutation.TupletAddition;
import songscribe.message.mutation.TupletRemoval;
import songscribe.message.notification.ModeDidChangeNotification;
import songscribe.message.notification.MusicSelectionDidChangeNotification;
import songscribe.message.notification.PrefsDidChangeNotification;
import songscribe.message.notification.SongDidChangeNotification;
import songscribe.message.notification.TextEditingDidChangeNotification;
import songscribe.prefs.PrefsKey;
import songscribe.ui.EndingConfirms;
import songscribe.ui.Mode;
import songscribe.ui.MusicEditOperations;
import songscribe.ui.action.Actions;
import songscribe.ui.action.ElementTypeAction;
import songscribe.ui.action.InsertLineAction;
import songscribe.ui.action.ModeAction;
import songscribe.ui.adjustment.HorizontalAdjustment;
import songscribe.ui.adjustment.VerticalAdjustment;
import songscribe.ui.clipboard.ClipboardManager;
import songscribe.ui.component.score.LineComponent;
import songscribe.ui.component.score.LinePanel;
import songscribe.ui.component.score.MainPanel;
import songscribe.ui.component.score.StaffPanel;
import songscribe.ui.edit.EditModeManager;
import songscribe.ui.selection.LineSelectionState;
import songscribe.ui.selection.ReflectionTestHelper;
import songscribe.ui.selection.SelectionCoordinator;
import songscribe.ui.selection.TupletToggleInfo;

class ScoreViewControllerTest extends UnitTest {

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

            var scoreMock = mock(ScoreView.class);
            when(scoreMock.isFocusOwner()).thenReturn(true);
            when(scoreMock.getSong()).thenReturn(song);
            when(scoreMock.canDeleteLine()).thenReturn(false);

            var controller = new ScoreViewController(
                scoreMock,
                mock(MusicEditOperations.class),
                selectionCoordinator,
                mock(ClipboardManager.class)
            );

            // handleDelete is package-private — no reflection needed
            controller.handleDelete();

            assertThat(element.getLyricForVerse(1)).isNull();
            assertThat(selectionCoordinator.hasLyricSelection()).isFalse();
            assertThat(selectionCoordinator.hasActiveSelection()).isFalse();
            verify(scoreMock).selectionChanged();
            verify(scoreMock).repaint();
        }
    }

    // -----------------------------------------------------------------------
    // handleDelete — rows 16-20
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class HandleDelete {

        private static StaffElement crotchet() {
            return ElementType.CROTCHET.newInstance();
        }

        private static ScoreViewController buildController(
            Song song,
            SelectionCoordinator coordinator,
            ScoreView scoreMock
        ) {
            return new ScoreViewController(
                scoreMock,
                mock(MusicEditOperations.class),
                coordinator,
                mock(ClipboardManager.class)
            );
        }

        // Row 16: contiguous-range path
        @Test
        void testHandleDeleteContiguousRangeStripsGlissandoAndShiftsAndRemovesElements() {
            // Layout: [A(gliss), B, C, D] — select [B, C] (indices 1..2)
            // Expected: glissando removed from A; B and C deleted; D shifted;
            // A and D remain.
            var song = new Song();
            var line = song.getLine(0);
            var noteA = crotchet();
            noteA.setGlissando(StaffElement.Glissando.Type.SLIDE_OUT);
            noteA.setXOffsetPx(0);
            var noteB = crotchet();
            noteB.setXOffsetPx(10);
            var noteC = crotchet();
            noteC.setXOffsetPx(20);
            var noteD = crotchet();
            noteD.setXOffsetPx(30);

            song.withoutMutationTracking(() -> {
                line.addElement(noteA);
                line.addElement(noteB);
                line.addElement(noteC);
                line.addElement(noteD);
            });

            var scoreMock = mock(ScoreView.class);
            when(scoreMock.getSong()).thenReturn(song);
            var coordinator = ReflectionTestHelper.createCoordinatorForLine(line);
            ReflectionTestHelper.selectRange(coordinator, 1, 2);
            var controller = buildController(song, coordinator, scoreMock);

            controller.handleDelete();

            // Glissando stripped from the note before the selection
            assertThat(noteA.getGlissando()).isNull();
            // B and C are gone; A and D remain (plus terminal barline = 3 total)
            assertThat(line.elementCount()).isEqualTo(3);
            assertThat(line.getElement(0)).isSameAs(noteA);
            assertThat(line.getElement(1)).isSameAs(noteD);
            // D shifted left: shift = noteB.x - noteD.x = 10 - 30 = -20, so D.x = 30 - 20 = 10
            assertThat(noteD.getXOffsetPx()).isEqualTo(10);
        }

        // Row 17: paired-grace-note at selection start falls back to deleteSelection
        @Test
        void testHandleDeleteFallsBackToDeleteSelectionWhenPairedGraceNotePrecedesSelection() {
            // [A, G(paired), B, C] — select [B, C] (indices 2..3);
            // index 2 is the host of a paired grace note (G at index 1).
            // The fallback per-element loop must delete G+B and C, leaving only A.
            var song = new Song();
            var line = song.getLine(0);
            var noteA = crotchet();
            // Paired grace note: GRACE_QUAVER with CONNECTED glissando
            var grace = ElementType.GRACE_QUAVER.newInstance();
            grace.setGlissando(StaffElement.Glissando.Type.CONNECTED);
            var noteB = crotchet();
            var noteC = crotchet();

            song.withoutMutationTracking(() -> {
                line.addElement(noteA);
                line.addElement(grace);
                line.addElement(noteB);
                line.addElement(noteC);
            });

            var scoreMock = mock(ScoreView.class);
            when(scoreMock.getSong()).thenReturn(song);
            var coordinator = ReflectionTestHelper.createCoordinatorForLine(line);
            ReflectionTestHelper.selectRange(coordinator, 2, 3);
            var controller = buildController(song, coordinator, scoreMock);

            controller.handleDelete();

            // G (paired), B, and C all removed; A and terminal barline remain
            assertThat(line.elementCount()).isEqualTo(2);
            assertThat(line.getElement(0)).isSameAs(noteA);
        }

        // Row 18: glissando selection removes the glissando from source element
        @Test
        void testHandleDeleteGlissandoSelectionRemovesGlissandoFromElement() {
            var song = new Song();
            var line = song.getLine(0);
            var noteA = crotchet();
            noteA.setGlissando(StaffElement.Glissando.Type.SLIDE_OUT);
            var noteB = crotchet();

            song.withoutMutationTracking(() -> {
                line.addElement(noteA);
                line.addElement(noteB);
            });

            var scoreMock = mock(ScoreView.class);
            when(scoreMock.getSong()).thenReturn(song);
            var coordinator = ReflectionTestHelper.createCoordinatorForLine(line);
            ReflectionTestHelper.selectGlissando(coordinator, 0);
            var controller = buildController(song, coordinator, scoreMock);

            controller.handleDelete();

            assertThat(noteA.getGlissando()).isNull();
            // Only the glissando is removed; elements are unchanged (noteA, noteB, barline)
            assertThat(line.elementCount()).isEqualTo(3);
        }

        // Row 19: no element/glissando selection, canDeleteLine() true → removes line
        @Test
        void testHandleDeleteRemovesSelectedLineWhenCanDeleteLine() {
            var song = new Song();
            var line = song.getLine(0);
            // Add a second line so removing the first is legal (song needs >= 1 line)
            song.withoutMutationTracking(() -> song.addLine(1, new Line(song)));

            var scoreMock = mock(ScoreView.class);
            when(scoreMock.getSong()).thenReturn(song);
            when(scoreMock.canDeleteLine()).thenReturn(true);

            var coordinator = ReflectionTestHelper.createCoordinatorForLine(line);
            // No element or glissando selection — mark the line itself as selected so
            // getSelectedLine() returns 0 rather than -1.
            var state = coordinator.getActiveSelection();

            if (state != null) {
                state.setLineSelected(true);
            }

            var controller = buildController(song, coordinator, scoreMock);
            var lineCountBefore = song.lineCount();
            controller.handleDelete();

            assertThat(song.lineCount()).isEqualTo(lineCountBefore - 1);
        }

        // Row 20: confirmInvalidation() returns false → deletion is aborted
        @Test
        void testHandleDeleteAbortsWhenEndingInvalidationNotConfirmed() {
            // Mock the line to report that the selection invalidates an ending,
            // and mock EndingConfirms.confirmInvalidation() to return false.
            // Verify the song's withModification is never called (no deletion occurs).
            var noteA = crotchet();
            var noteB = crotchet();

            var songMock = mock(Song.class);
            var lineMock = mock(Line.class);
            var scoreMock = mock(ScoreView.class);
            when(scoreMock.getSong()).thenReturn(songMock);

            var coordinatorMock = mock(SelectionCoordinator.class);
            var stateMock = mock(LineSelectionState.class);
            when(coordinatorMock.getLyricSelection()).thenReturn(null);
            when(coordinatorMock.getActiveSelection()).thenReturn(stateMock);
            when(stateMock.hasElementSelection()).thenReturn(true);
            when(stateMock.getLine()).thenReturn(lineMock);
            when(stateMock.getSelectionBegin()).thenReturn(0);
            when(stateMock.getSelectionEnd()).thenReturn(1);
            when(lineMock.getElements(0, 1)).thenReturn(List.of(noteA, noteB));
            when(lineMock.hasEndingInvalidatedByDeletion(any())).thenReturn(true);

            var controller = new ScoreViewController(
                scoreMock,
                mock(MusicEditOperations.class),
                coordinatorMock,
                mock(ClipboardManager.class)
            );

            try (var endingConfirmsMock = mockStatic(EndingConfirms.class)) {
                endingConfirmsMock.when(EndingConfirms::confirmInvalidation).thenReturn(false);

                controller.handleDelete();

                // No removal should have happened — withModification never called
                verify(songMock, never()).withModification(any());
            }
        }
    }

    // -----------------------------------------------------------------------
    // handleCopy — row 21
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class HandleCopy {

        @Test
        void testHandleCopyCopiesSelectedElementRangeIntoClipboard() {
            // Row 21: handleCopy with an active element selection must copy exactly
            // the selected range into the ClipboardManager.
            var song = new Song();
            var line = song.getLine(0);
            var noteA = ElementType.CROTCHET.newInstance();
            var noteB = ElementType.CROTCHET.newInstance();
            var noteC = ElementType.CROTCHET.newInstance();
            song.withoutMutationTracking(() -> {
                line.addElement(noteA);
                line.addElement(noteB);
                line.addElement(noteC);
            });

            var coordinator = ReflectionTestHelper.createCoordinatorForLine(line);
            ReflectionTestHelper.selectRange(coordinator, 0, 1);  // select noteA and noteB

            var clipboardManager = new ClipboardManager();
            var scoreMock = mock(ScoreView.class);
            when(scoreMock.getSong()).thenReturn(song);

            var controller = new ScoreViewController(
                scoreMock,
                mock(MusicEditOperations.class),
                coordinator,
                clipboardManager
            );

            controller.handleCopy();

            // Two elements should have been copied (indices 0..1 inclusive)
            assertThat(clipboardManager.getSize()).isEqualTo(2);
            // Verify the copies are independent clones, not the originals
            assertThat(clipboardManager.getElement(0)).isNotSameAs(noteA);
            assertThat(clipboardManager.getElement(1)).isNotSameAs(noteB);
            // Verify the types match
            assertThat(clipboardManager.getElement(0).getType()).isEqualTo(noteA.getType());
            assertThat(clipboardManager.getElement(1).getType()).isEqualTo(noteB.getType());
        }

        @Test
        void testHandleCopyIsNoOpWhenNoActiveElementSelection() {
            var scoreMock = mock(ScoreView.class);
            var coordinatorMock = mock(SelectionCoordinator.class);
            when(coordinatorMock.getActiveSelection()).thenReturn(null);

            var clipboardManager = new ClipboardManager();
            var controller = new ScoreViewController(
                scoreMock,
                mock(MusicEditOperations.class),
                coordinatorMock,
                clipboardManager
            );

            controller.handleCopy();

            assertThat(clipboardManager.isEmpty()).isTrue();
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class DeleteNote {

        @Test
        void testDeleteNoteRemovesOneElement() {
            var line = lineWith(crotchet(), crotchet(), crotchet());
            var removed = ScoreViewController.deleteNote(1, line);

            assertThat(removed).isEqualTo(1);
            assertThat(line.elementCount()).isEqualTo(2);
        }

        @Test
        void testDeleteNoteRemovesPrecedingPairedGraceNote() {
            var line = lineWith(crotchet(), pairedGraceNote(), crotchet());
            var removed = ScoreViewController.deleteNote(2, line);

            assertThat(removed).isEqualTo(2);
            assertThat(line.elementCount()).isEqualTo(1);
        }

        @Test
        void testDeleteNoteDoesNotRemoveUnpairedGraceNote() {
            var graceNote = ElementType.GRACE_QUAVER.newInstance();
            var line = lineWith(crotchet(), graceNote, crotchet());
            var removed = ScoreViewController.deleteNote(2, line);

            assertThat(removed).isEqualTo(1);
            assertThat(line.elementCount()).isEqualTo(2);
        }

        @Test
        void testDeleteNoteRemovesGlissandoFromPreviousNote() {
            var prev = crotchet();
            prev.setGlissando(StaffElement.Glissando.Type.SLIDE_OUT);
            var line = lineWith(prev, crotchet());
            ScoreViewController.deleteNote(1, line);

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

            ScoreViewController.deleteNote(2, line);

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
                var removedCount = ScoreViewController.deleteNote(i, line);
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
                var removedCount = ScoreViewController.deleteNote(i, line);
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
                var removedCount = ScoreViewController.deleteNote(i, line);
                i -= (removedCount - 1);
            }

            assertThat(line.elementCount()).isEqualTo(0);
        }

        @Test
        void testDeleteNoteBreaksSyllableRelationWhenDeletedNoteIsTerminus() {
            var predecessor = crotchet();
            var terminus = crotchet();
            predecessor.lyrics.add(new Lyric(1, "do", Lyric.Extend.NONE, Lyric.Syllabic.BEGIN, false));
            terminus.lyrics.add(new Lyric(1, "re", Lyric.Extend.NONE, Lyric.Syllabic.END, false));
            var line = lineWith(predecessor, terminus);

            ScoreViewController.deleteNote(1, line);

            assertThat(predecessor.lyrics.getFirst().syllabic())
                .isEqualTo(Lyric.Syllabic.SINGLE);
        }

        @Test
        void testDeleteNotePreservesSyllableRelationWhenDeletedNoteIsChainMember() {
            var first = crotchet();
            var middle = crotchet();
            var last = crotchet();
            first.lyrics.add(new Lyric(1, "do", Lyric.Extend.NONE, Lyric.Syllabic.BEGIN, false));
            middle.lyrics.add(new Lyric(1, "re", Lyric.Extend.NONE, Lyric.Syllabic.MIDDLE, false));
            last.lyrics.add(new Lyric(1, "mi", Lyric.Extend.NONE, Lyric.Syllabic.END, false));
            var line = lineWith(first, middle, last);

            ScoreViewController.deleteNote(1, line);

            assertThat(first.lyrics.getFirst().syllabic())
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

    // -----------------------------------------------------------------------
    // handleSelectLine — rows 28 (active, covered elsewhere) & 29 (no-op)
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class SelectLine {

        @Test
        void testHandleSelectLineIsNoOpWhenNoActiveSelection() {
            var scoreMock = mock(ScoreView.class);
            var coordinatorMock = mock(SelectionCoordinator.class);
            when(coordinatorMock.getActiveSelection()).thenReturn(null);

            var controller = new ScoreViewController(
                scoreMock,
                mock(MusicEditOperations.class),
                coordinatorMock,
                mock(ClipboardManager.class)
            );

            controller.handleSelectLine(new SelectLineCommand());

            verify(scoreMock, never()).selectionChanged();
            verify(scoreMock, never()).repaint();
        }
    }

    // -----------------------------------------------------------------------
    // handleInsertLine — rows 30, 31, 32
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class InsertLine {

        private ScoreView scoreMock;
        private Song songMock;
        private SelectionCoordinator coordinatorMock;
        private ScoreViewController controller;

        @BeforeEach
        void setUp() {
            scoreMock = mock(ScoreView.class);
            songMock = mock(Song.class);
            coordinatorMock = mock(SelectionCoordinator.class);
            when(scoreMock.getSong()).thenReturn(songMock);

            controller = new ScoreViewController(
                scoreMock,
                mock(MusicEditOperations.class),
                coordinatorMock,
                mock(ClipboardManager.class)
            );
        }

        @Test
        void testHandleInsertLineInsertsAtSelectedLineIndexPlusShift() {
            // selectedLine == 1, shift == 1 → index = 1 + 1 = 2
            when(coordinatorMock.getSelectedLine()).thenReturn(1);

            controller.handleInsertLine(new InsertLineCommand(1));

            verify(songMock).addLine(eq(2), any(Line.class));
            verify(scoreMock).deselect();
        }

        @Test
        void testHandleInsertLineWithAddShiftInsertsAtEndRegardlessOfSelection() {
            // shift == ADD forces end-of-song insertion even without a line selected
            when(coordinatorMock.getSelectedLine()).thenReturn(-1);

            controller.handleInsertLine(new InsertLineCommand(InsertLineAction.ADD));

            verify(songMock).addLine(eq(InsertLineAction.ADD), any(Line.class));
            verify(scoreMock).deselect();
        }

        @Test
        void testHandleInsertLineShowsErrorWhenNoLineSelectedAndShiftIsNotAdd() {
            // No line selected and shift != ADD → error dialog (suppressed in tests),
            // addLine must NOT be called.
            when(coordinatorMock.getSelectedLine()).thenReturn(-1);

            controller.handleInsertLine(new InsertLineCommand(0));

            verify(songMock, never()).addLine(anyInt(), any(Line.class));
        }
    }

    // -----------------------------------------------------------------------
    // modeDidChange — rows 33, 34, 35
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class ModeDidChange {

        private ScoreView scoreMock;
        private SelectionCoordinator coordinatorMock;
        private ScoreViewController controller;

        @BeforeEach
        void setUp() {
            scoreMock = mock(ScoreView.class);
            coordinatorMock = mock(SelectionCoordinator.class);

            controller = new ScoreViewController(
                scoreMock,
                mock(MusicEditOperations.class),
                coordinatorMock,
                mock(ClipboardManager.class)
            );
        }

        private ModeDidChangeNotification notificationFor(Mode mode) {
            var action = mock(ModeAction.class);
            when(action.getMode()).thenReturn(mode);
            // Adjustment detection uses action.getActionCommand().startsWith("adjust-")
            var command = switch (mode) {
                case ADJUSTMENT -> "adjust-note-mode";
                case VERTICAL_ADJUSTMENT -> "adjust-vertical-mode";
                default -> mode.name().toLowerCase() + "-mode";
            };
            when(action.getActionCommand()).thenReturn(command);
            return new ModeDidChangeNotification(action);
        }

        @Test
        void testModeDidChangeClearsSelectionWhenModeIsNotSelect() {
            controller.modeDidChange(notificationFor(Mode.EDIT));

            verify(scoreMock).clearSelection();
        }

        @Test
        void testModeDidChangeSyncsPreviewElementOnEditEntry() {
            // When a duration action is selected, EDIT entry must call score.setPreviewElement.
            // Inject a mock ElementTypeAction into the DURATION_ACTION_GROUP's selected field
            // via reflection, bypassing the real UIAction constructor chain.
            var mockAction = mock(ElementTypeAction.class);
            when(mockAction.getType()).thenReturn(ElementType.CROTCHET);

            var mockPreviewElement = mock(StaffElement.class);

            try (MockedStatic<EditModeManager> emm = mockStatic(EditModeManager.class)) {
                emm.when(() -> EditModeManager.makePreviewElement(ElementType.CROTCHET))
                    .thenReturn(mockPreviewElement);

                setDurationGroupSelected(mockAction);
                try {
                    controller.modeDidChange(notificationFor(Mode.EDIT));
                } finally {
                    setDurationGroupSelected(null);
                }
            }

            verify(scoreMock).setPreviewElement(mockPreviewElement);
        }

        @Test
        void testModeDidChangeEnablesHorizontalAdjustmentForAdjustmentMode() {
            var ha = mock(HorizontalAdjustment.class);
            var va = mock(VerticalAdjustment.class);
            when(scoreMock.getHorizontalAdjustment()).thenReturn(ha);
            when(scoreMock.getVerticalAdjustment()).thenReturn(va);

            controller.modeDidChange(notificationFor(Mode.ADJUSTMENT));

            verify(ha).setEnabled(true);
            verify(va).setEnabled(false);
        }

        @Test
        void testModeDidChangeEnablesVerticalAdjustmentForVerticalAdjustmentMode() {
            var ha = mock(HorizontalAdjustment.class);
            var va = mock(VerticalAdjustment.class);
            when(scoreMock.getHorizontalAdjustment()).thenReturn(ha);
            when(scoreMock.getVerticalAdjustment()).thenReturn(va);

            controller.modeDidChange(notificationFor(Mode.VERTICAL_ADJUSTMENT));

            verify(ha).setEnabled(false);
            verify(va).setEnabled(true);
        }

        /**
         * Sets the {@code selected} field on {@link Actions#DURATION_ACTION_GROUP}
         * via reflection. Pass {@code null} to clear the selection.
         */
        @SuppressWarnings("SameParameterValue")
        private static void setDurationGroupSelected(@org.jspecify.annotations.Nullable ElementTypeAction value) {
            try {
                var field = Actions.DURATION_ACTION_GROUP.getClass().getSuperclass().getDeclaredField("selected");
                field.setAccessible(true);
                field.set(Actions.DURATION_ACTION_GROUP, value);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("Failed to set DURATION_ACTION_GROUP.selected", e);
            }
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
        private ScoreViewController coordinator;

        @BeforeEach
        void setUp() {
            line = detachedLine();
            line.addElement(ElementType.CROTCHET.newInstance());
            line.addElement(ElementType.CROTCHET.newInstance());
            lineComponentMock = mock(LineComponent.class);

            var linePanelMock = mock(LinePanel.class);
            when(linePanelMock.getLine()).thenReturn(line);
            when(linePanelMock.getLineComponent()).thenReturn(lineComponentMock);

            var staffPanelMock = mock(StaffPanel.class);
            when(staffPanelMock.getLinePanels()).thenReturn(List.of(linePanelMock));

            var mainPanelMock = mock(MainPanel.class);
            when(mainPanelMock.getStaffPanel()).thenReturn(staffPanelMock);

            var scoreMock = mock(ScoreView.class);
            when(scoreMock.getMainPanel()).thenReturn(mainPanelMock);

            coordinator = new ScoreViewController(
                scoreMock,
                mock(MusicEditOperations.class),
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
            fireNotification(new BeamingAddition(line, new Beam(line.getElement(0), line.getElement(1))));
            verify(lineComponentMock).invalidateLayout();
        }

        @Test
        void testBeamingRemovalInvalidatesLayout() {
            fireNotification(new BeamingRemoval(line, new Beam(line.getElement(0), line.getElement(1))));
            verify(lineComponentMock).invalidateLayout();
        }

        @Test
        void testTieAdditionInvalidatesLayout() {
            var tie = new Tie(line.getElement(0), line.getElement(1));
            fireNotification(new TieAddition(line, tie));
            verify(lineComponentMock).invalidateLayout();
        }

        @Test
        void testTieRemovalInvalidatesLayout() {
            var tie = new Tie(line.getElement(0), line.getElement(1));
            fireNotification(new TieRemoval(line, tie));
            verify(lineComponentMock).invalidateLayout();
        }

        @Test
        void testTupletAdditionInvalidatesLayout() {
            var tuplet = new Tuplet(line.getElement(0), line.getElement(1), 3);
            fireNotification(new TupletAddition(line, tuplet));
            verify(lineComponentMock).invalidateLayout();
        }

        @Test
        void testTupletRemovalInvalidatesLayout() {
            var tuplet = new Tuplet(line.getElement(0), line.getElement(1), 3);
            fireNotification(new TupletRemoval(line, tuplet));
            verify(lineComponentMock).invalidateLayout();
        }
    }

    // -----------------------------------------------------------------------
    // hasFullRelayoutMutation — row 37
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class FullRelayoutDetection {

        private ScoreView scoreMock;
        private ScoreViewController controller;

        @BeforeEach
        void setUp() {
            scoreMock = mock(ScoreView.class);

            // mainPanel must be non-null so songDidChange doesn't return early
            var mainPanelMock = mock(MainPanel.class);
            var staffPanelMock = mock(StaffPanel.class);
            when(mainPanelMock.getStaffPanel()).thenReturn(staffPanelMock);
            when(staffPanelMock.getLinePanels()).thenReturn(List.of());
            when(scoreMock.getMainPanel()).thenReturn(mainPanelMock);

            controller = new ScoreViewController(
                scoreMock,
                mock(MusicEditOperations.class),
                mock(SelectionCoordinator.class),
                mock(ClipboardManager.class)
            );
        }

        private void fire(Mutation mutation) {
            controller.songDidChange(new SongDidChangeNotification(List.of(mutation), new Song()));
        }

        @Test
        void testFontChangeTriggerViewChanged() {
            fire(new FontChange(new DocumentFonts(), new DocumentFonts()));
            verify(scoreMock).viewChanged();
        }

        @Test
        void testMetadataChangeTriggerViewChanged() {
            fire(new MetadataChange(MetadataField.TITLE, "old", "new"));
            verify(scoreMock).viewChanged();
        }

        @Test
        void testLayoutChangeTriggerViewChanged() {
            fire(new LayoutChange(LayoutField.LINE_WIDTH_SS, 40.0, 50.0));
            verify(scoreMock).viewChanged();
        }

        @Test
        void testLineScopedMutationDoesNotTriggerViewChanged() {
            // BeamingRemoval is a LineScopedMutation — not a full-relayout trigger.
            // The setUp wires scoreMock → mainPanel → staffPanel → empty linePanels,
            // so no line component is invalidated and viewChanged() must not be called.
            var line = detachedLine();
            line.addElement(ElementType.CROTCHET.newInstance());
            line.addElement(ElementType.CROTCHET.newInstance());

            fire(new BeamingRemoval(line, new Beam(line.getElement(0), line.getElement(1))));
            verify(scoreMock, never()).viewChanged();
        }
    }

    // -----------------------------------------------------------------------
    // songDidChange — target-line filtering and viewChanged (rows 38-40)
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class SongDidChangeHandling {

        private ScoreView scoreMock;
        private Line targetLine;
        private Line otherLine;
        private LineComponent targetLineComponentMock;
        private LineComponent otherLineComponentMock;
        private ScoreViewController controller;

        @BeforeEach
        void setUp() {
            scoreMock = mock(ScoreView.class);

            targetLine = detachedLine();
            targetLine.addElement(ElementType.CROTCHET.newInstance());
            targetLine.addElement(ElementType.CROTCHET.newInstance());

            otherLine = detachedLine();
            otherLine.addElement(ElementType.CROTCHET.newInstance());
            otherLine.addElement(ElementType.CROTCHET.newInstance());

            targetLineComponentMock = mock(LineComponent.class);
            otherLineComponentMock = mock(LineComponent.class);

            var targetPanel = mock(LinePanel.class);
            when(targetPanel.getLine()).thenReturn(targetLine);
            when(targetPanel.getLineComponent()).thenReturn(targetLineComponentMock);

            var otherPanel = mock(LinePanel.class);
            when(otherPanel.getLine()).thenReturn(otherLine);
            when(otherPanel.getLineComponent()).thenReturn(otherLineComponentMock);

            var staffPanelMock = mock(StaffPanel.class);
            when(staffPanelMock.getLinePanels()).thenReturn(List.of(targetPanel, otherPanel));

            var mainPanelMock = mock(MainPanel.class);
            when(mainPanelMock.getStaffPanel()).thenReturn(staffPanelMock);
            when(scoreMock.getMainPanel()).thenReturn(mainPanelMock);

            controller = new ScoreViewController(
                scoreMock,
                mock(MusicEditOperations.class),
                mock(SelectionCoordinator.class),
                mock(ClipboardManager.class)
            );
        }

        @Test
        void testSongDidChangeInvalidatesOnlyTargetLineWhenLineScopedMutationHasNonNullTarget() {
            // Row 38: line-scoped mutation with a specific target line must invalidate
            // only that line's component, not neighboring lines.
            var beam = new Beam(targetLine.getElement(0), targetLine.getElement(1));
            controller.songDidChange(
                new SongDidChangeNotification(List.of(new BeamingAddition(targetLine, beam)), new Song())
            );

            verify(targetLineComponentMock).invalidateLayout();
            verify(otherLineComponentMock, never()).invalidateLayout();
        }

        @Test
        void testSongDidChangeCallsViewChangedForFullRelayoutMutation() {
            // Row 39: a full-relayout mutation (MetadataChange) must trigger viewChanged()
            controller.songDidChange(
                new SongDidChangeNotification(
                    List.of(new MetadataChange(MetadataField.TITLE, "A", "B")),
                    new Song()
                )
            );

            verify(scoreMock).viewChanged();
        }

        @Test
        void testSongDidChangeRestartsRepaintDebounceTimer() {
            // Row 40: every call to songDidChange (with a non-null mainPanel) must
            // restart the debounce timer regardless of mutation type.
            controller.songDidChange(
                new SongDidChangeNotification(
                    List.of(new MetadataChange(MetadataField.TITLE, "A", "B")),
                    new Song()
                )
            );

            assertThat(controller.repaintDebounceTimer.isRunning()).isTrue();
        }
    }

    // -----------------------------------------------------------------------
    // canToggleTuplet / warmTupletCache — rows 41-42
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class TupletCaching {

        private MusicEditOperations operationsMock;
        private ScoreViewController controller;

        @BeforeEach
        void setUp() {
            operationsMock = mock(MusicEditOperations.class);

            controller = new ScoreViewController(
                mock(ScoreView.class),
                operationsMock,
                mock(SelectionCoordinator.class),
                mock(ClipboardManager.class)
            );
        }

        @Test
        void testCanToggleTupletDelegatesToOperationsWhenCacheIsNull() {
            // Row 41a: with no cache populated, canToggleTuplet() must call operations
            var expected = new TupletToggleInfo(true, null, false);
            when(operationsMock.canToggleTuplet()).thenReturn(expected);

            var result = controller.canToggleTuplet();

            assertThat(result).isSameAs(expected);
            verify(operationsMock).canToggleTuplet();
        }

        @Test
        void testCanToggleTupletReturnsCachedValueWithoutCallingOperations() {
            // Row 41b: once the cache is warm, canToggleTuplet() must not delegate
            var cached = new TupletToggleInfo(true, null, false);
            when(operationsMock.canToggleTuplet()).thenReturn(cached);

            // Warm the cache via musicSelectionDidChangeCacheTupletInfo
            controller.musicSelectionDidChangeCacheTupletInfo(
                mock(MusicSelectionDidChangeNotification.class)
            );

            // Now call canToggleTuplet() — operations should have been called
            // exactly once (during warmTupletCache), and not again here.
            controller.canToggleTuplet();
            verify(operationsMock).canToggleTuplet();
        }

        @Test
        void testTupletInfoCachePriorityExceedsHighPriority() {
            // Row 42: the cache handler must run before all HIGH_PRIORITY subscribers
            // so TupletAction reads a warm cache.
            assertThat(ScoreViewController.TUPLET_INFO_CACHE_PRIORITY)
                .isGreaterThan(Message.HIGH_PRIORITY);
        }
    }

    // -----------------------------------------------------------------------
    // prefsDidChange — row 43
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class PrefsDidChange {

        private ScoreView scoreMock;
        private ScoreViewController controller;

        @BeforeEach
        void setUp() {
            scoreMock = mock(ScoreView.class);

            controller = new ScoreViewController(
                scoreMock,
                mock(MusicEditOperations.class),
                mock(SelectionCoordinator.class),
                mock(ClipboardManager.class)
            );
        }

        @Test
        void testPrefsDidChangeLoopPlaybackCallsSyncPlaybackPrefs() {
            controller.prefsDidChange(new PrefsDidChangeNotification(PrefsKey.LOOP_PLAYBACK));
            verify(scoreMock).syncPlaybackPrefs();
        }

        @Test
        void testPrefsDidChangeLoopPlaybackDoesNotCallUpdatePageLayout() {
            controller.prefsDidChange(new PrefsDidChangeNotification(PrefsKey.LOOP_PLAYBACK));
            verify(scoreMock, never()).updatePageLayout(anyInt());
        }

        @Test
        void testPrefsDidChangePageSizeCallsUpdatePageLayoutWhenInitialized() {
            // Row 44: PAGE_SIZE fires updatePageLayout (but not syncPlaybackPrefs)
            // when score.isInitialized() returns true.
            var song = new Song();
            when(scoreMock.isInitialized()).thenReturn(true);
            when(scoreMock.getSong()).thenReturn(song);

            controller.prefsDidChange(new PrefsDidChangeNotification(PrefsKey.PAGE_SIZE));

            verify(scoreMock).updatePageLayout(anyInt());
            verify(scoreMock, never()).syncPlaybackPrefs();
        }

        @Test
        void testPrefsDidChangePageSizeIsNoOpWhenNotInitialized() {
            // Row 44 guard: PAGE_SIZE must not call updatePageLayout if score is not initialized.
            when(scoreMock.isInitialized()).thenReturn(false);

            controller.prefsDidChange(new PrefsDidChangeNotification(PrefsKey.PAGE_SIZE));

            verify(scoreMock, never()).updatePageLayout(anyInt());
        }

        @Test
        void testPrefsDidChangeAllCallsBothEffects() {
            // Row 45: PrefsKey.ALL triggers both syncPlaybackPrefs and updatePageLayout.
            var song = new Song();
            when(scoreMock.isInitialized()).thenReturn(true);
            when(scoreMock.getSong()).thenReturn(song);

            controller.prefsDidChange(new PrefsDidChangeNotification(PrefsKey.ALL));

            verify(scoreMock).syncPlaybackPrefs();
            verify(scoreMock).updatePageLayout(anyInt());
        }
    }

    // -----------------------------------------------------------------------
    // textEditingDidChange — row 46
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class TextEditingDidChange {

        private ScoreView scoreMock;
        private ScoreViewController controller;

        @BeforeEach
        void setUp() {
            scoreMock = mock(ScoreView.class);

            controller = new ScoreViewController(
                scoreMock,
                mock(MusicEditOperations.class),
                mock(SelectionCoordinator.class),
                mock(ClipboardManager.class)
            );
        }

        @Test
        void testTextEditingDidChangeDisablesKeyBindingsWhenEditing() {
            // Row 46a: editing=true → setKeyBindingsEnabled(false)
            controller.textEditingDidChange(new TextEditingDidChangeNotification(true));

            verify(scoreMock).setKeyBindingsEnabled(false);
        }

        @Test
        void testTextEditingDidChangeEnablesKeyBindingsWhenNotEditing() {
            // Row 46b: editing=false → setKeyBindingsEnabled(true)
            controller.textEditingDidChange(new TextEditingDidChangeNotification(false));

            verify(scoreMock).setKeyBindingsEnabled(true);
        }
    }

}
